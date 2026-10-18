package cat.narezany.patcher.core

import com.jcraft.jzlib.Deflater
import com.jcraft.jzlib.JZlib
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.ZipFile

/**
 * Turning TikTok's own apk into a release of the mod.
 *
 * A patch is a recipe: for every stretch of the release apk, where its bytes
 * come from. Most are copied out of TikTok's apk as they are. Some the patch
 * carries itself. And the files the mod changed are made from TikTok's by a
 * binary difference, then deflated again by the one compressor the build
 * used, so that they come out as the very bytes the build wrote. What is made
 * is the release apk byte for byte, signature included, and is checked
 * against its hash before anybody is offered it.
 *
 * The format is written down in margyt/delta.py, which makes these.
 */
object Patch {

    const val FORMAT = 1
    private const val DEFLATER = "jzlib-1.1.3"

    private const val COPY = 0
    private const val LITERAL = 1
    private const val PATCHED = 2
    private const val DEFLATED = 8

    class Refused(message: String) : IOException(message)
    class Cancelled : IOException("cancelled")

    /** What a patch says about itself. */
    class About(
        val baseSha256: String,
        val baseSize: Long,
        val resultSha256: String,
        val resultSize: Long,
        val level: Int,
    )

    private sealed class Step {
        class Copy(val from: Long, val length: Long) : Step()
        class Carried(val length: Long) : Step()
        class Made(
            val index: Int, val from: Long, val packed: Long, val method: Int, val plain: Long,
            val toMethod: Int, val toPlain: Long, val toPacked: Long, val crc: Long,
        ) : Step()
    }

    fun about(patch: File): About = ZipFile(patch).use { about(it) }

    private fun about(patch: ZipFile): About {
        val entry = patch.getEntry("patch.json") ?: throw Refused("not a patch")
        val root = JSONObject(patch.getInputStream(entry).readBytes().toString(Charsets.UTF_8))
        if (root.getInt("format") != FORMAT) throw Refused("a patch this version cannot read")
        val deflate = root.getJSONObject("deflate")
        if (deflate.getString("with") != DEFLATER) throw Refused("a patch this version cannot read")
        val base = root.getJSONObject("base")
        val result = root.getJSONObject("result")
        return About(
            base.getString("sha256"), base.getLong("size"),
            result.getString("sha256"), result.getLong("size"),
            deflate.getInt("level"),
        )
    }

    /** The sha256 of a file, in hex. `seen` is told how far it has read. */
    fun sha256(file: File, cancelled: () -> Boolean = { false }, seen: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val block = ByteArray(1 shl 16)
        var read = 0L
        file.inputStream().use { input ->
            while (true) {
                val got = input.read(block)
                if (got < 0) break
                digest.update(block, 0, got)
                read += got
                if (read and 0xFFFFF < got) {
                    if (cancelled()) throw Cancelled()
                    seen(read)
                }
            }
        }
        return hex(digest.digest())
    }

    /**
     * Make the release apk at [out] from TikTok's apk at [base].
     *
     * [base] is taken to be the right apk: check its hash against [about]
     * first, it is a different question with a different answer for the
     * person. [written] is told how many bytes of [out] exist so far.
     */
    fun apply(
        base: File, patch: File, out: File,
        cancelled: () -> Boolean = { false },
        written: (done: Long, of: Long) -> Unit = { _, _ -> },
    ) {
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            ZipFile(patch).use { archive ->
                val about = about(archive)
                if (base.length() != about.baseSize) throw Refused("this is not the apk the patch is for")
                val steps = steps(archive)
                RandomAccessFile(base, "r").use { source ->
                    val channel = source.channel
                    val literal = XZInputStream(BufferedInputStream(
                        archive.getInputStream(archive.getEntry("literal.xz")), 1 shl 16))
                    val digest = MessageDigest.getInstance("SHA-256")
                    var done = 0L
                    var told = 0L

                    // The changed files are the work, and are made a few at a
                    // time ahead of where the writing has got to: far enough
                    // to keep every core busy, not so far that a phone has to
                    // hold a dozen of them in memory at once.
                    val ahead = ArrayDeque<Future<ByteArray>>()
                    val waiting = steps.filterIsInstance<Step.Made>().iterator()
                    var held = 0L
                    val budget = 96L shl 20
                    val weights = ArrayDeque<Long>()
                    fun top() {
                        while (waiting.hasNext() && (ahead.isEmpty() || (ahead.size < workers && held < budget))) {
                            val step = waiting.next()
                            val weight = step.plain + step.toPlain
                            held += weight
                            weights.add(weight)
                            ahead.add(pool.submit(Callable { made(archive, channel, step, about.level) }))
                        }
                    }

                    BufferedOutputStream(out.outputStream(), 1 shl 16).use { target ->
                        val block = ByteArray(1 shl 16)
                        fun put(bytes: ByteArray, from: Int, length: Int) {
                            target.write(bytes, from, length)
                            digest.update(bytes, from, length)
                            done += length
                            if (done - told >= 1 shl 20) {
                                told = done
                                if (cancelled()) throw Cancelled()
                                written(done, about.resultSize)
                            }
                        }
                        for (step in steps) {
                            when (step) {
                                is Step.Copy -> {
                                    var at = step.from
                                    var left = step.length
                                    val buffer = ByteBuffer.wrap(block)
                                    while (left > 0) {
                                        buffer.clear()
                                        buffer.limit(minOf(block.size.toLong(), left).toInt())
                                        val got = channel.read(buffer, at)
                                        if (got <= 0) throw Refused("the apk ends before the patch is done with it")
                                        put(block, 0, got)
                                        at += got
                                        left -= got
                                    }
                                }
                                is Step.Carried -> {
                                    var left = step.length
                                    while (left > 0) {
                                        val got = literal.read(block, 0, minOf(block.size.toLong(), left).toInt())
                                        if (got < 0) throw Refused("the patch is cut short")
                                        put(block, 0, got)
                                        left -= got
                                    }
                                }
                                is Step.Made -> {
                                    top()
                                    val bytes = try {
                                        ahead.remove().get()
                                    } catch (failed: java.util.concurrent.ExecutionException) {
                                        throw failed.cause ?: failed
                                    }
                                    held -= weights.remove()
                                    top()
                                    var at = 0
                                    while (at < bytes.size) {
                                        val piece = minOf(1 shl 20, bytes.size - at)
                                        put(bytes, at, piece)
                                        at += piece
                                    }
                                }
                            }
                        }
                    }
                    written(done, about.resultSize)
                    if (done != about.resultSize || hex(digest.digest()) != about.resultSha256) {
                        throw Refused("what was made is not the release")
                    }
                }
            }
        } catch (failed: Throwable) {
            out.delete()
            throw failed
        } finally {
            pool.shutdownNow()
        }
    }

    private fun steps(archive: ZipFile): List<Step> {
        val entry = archive.getEntry("ops.xz") ?: throw Refused("not a patch")
        val bytes = XZInputStream(archive.getInputStream(entry)).use { it.readBytes() }
        val recipe = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = recipe.int
        val out = ArrayList<Step>(count)
        repeat(count) {
            when (recipe.get().toInt()) {
                COPY -> out.add(Step.Copy(recipe.long, recipe.long))
                LITERAL -> out.add(Step.Carried(recipe.long))
                PATCHED -> out.add(Step.Made(
                    recipe.int, recipe.long, recipe.long, recipe.get().toInt(), recipe.long,
                    recipe.get().toInt(), recipe.long, recipe.long, recipe.int.toLong() and 0xFFFFFFFFL,
                ))
                else -> throw Refused("a patch this version cannot read")
            }
        }
        return out
    }

    /** One changed file, as the bytes the release apk holds for it. */
    private fun made(archive: ZipFile, source: FileChannel, step: Step.Made, level: Int): ByteArray {
        val packed = ByteArray(step.packed.toInt())
        val buffer = ByteBuffer.wrap(packed)
        var at = step.from
        while (buffer.hasRemaining()) {
            val got = source.read(buffer, at)
            if (got <= 0) throw Refused("the apk ends before the patch is done with it")
            at += got
        }
        val old = if (step.method == DEFLATED) inflate(packed, step.plain.toInt()) else packed

        val fresh = ByteArray(step.toPlain.toInt())
        fun stream(kind: String): InputStream {
            val entry = archive.getEntry("d/${step.index}.$kind.xz") ?: throw Refused("the patch is cut short")
            return XZInputStream(BufferedInputStream(archive.getInputStream(entry), 1 shl 15))
        }
        // bsdiff: add so many bytes of the difference to the old file's, then
        // take so many new ones as they are, then move about in the old file
        DataInputStream(BufferedInputStream(stream("c"), 1 shl 15)).use { control ->
            stream("d").use { difference ->
                stream("e").use { extra ->
                    val row = ByteArray(24)
                    val numbers = ByteBuffer.wrap(row).order(ByteOrder.LITTLE_ENDIAN)
                    var to = 0
                    var from = 0L
                    while (to < fresh.size) {
                        try {
                            control.readFully(row)
                        } catch (short: EOFException) {
                            throw Refused("the patch is cut short")
                        }
                        val add = numbers.getLong(0)
                        val take = numbers.getLong(8)
                        val seek = numbers.getLong(16)
                        if (add < 0 || take < 0 || to + add + take > fresh.size) throw Refused("the patch is damaged")
                        fill(difference, fresh, to, add.toInt())
                        val first = maxOf(0L, -from)
                        val last = minOf(add, old.size - from)
                        var i = first
                        while (i < last) {
                            fresh[to + i.toInt()] = (fresh[to + i.toInt()] + old[(from + i).toInt()]).toByte()
                            i++
                        }
                        to += add.toInt()
                        from += add
                        fill(extra, fresh, to, take.toInt())
                        to += take.toInt()
                        from += seek
                    }
                }
            }
        }
        val crc = CRC32()
        crc.update(fresh)
        if (crc.value != step.crc) throw Refused("a patched file does not check out")
        val raw = if (step.toMethod == DEFLATED) deflate(fresh, level) else fresh
        if (raw.size.toLong() != step.toPacked) throw Refused("a patched file does not check out")
        return raw
    }

    private fun fill(from: InputStream, into: ByteArray, at: Int, length: Int) {
        var done = 0
        while (done < length) {
            val got = from.read(into, at + done, length - done)
            if (got < 0) throw Refused("the patch is cut short")
            done += got
        }
    }

    private fun inflate(packed: ByteArray, size: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(packed)
            val out = ByteArray(size)
            var at = 0
            while (at < size) {
                val got = inflater.inflate(out, at, size - at)
                if (got == 0 && (inflater.finished() || inflater.needsInput())) break
                at += got
            }
            if (at != size) throw Refused("this is not the apk the patch is for")
            return out
        } finally {
            inflater.end()
        }
    }

    /**
     * A raw deflate stream, by JZlib and not by the phone's own zlib: the
     * build deflated with this same code, and only the same code promises the
     * same bytes. Keep in step with margyt/java/Deflate.java.
     */
    fun deflate(data: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level, true)
        val block = ByteArray(1 shl 16)
        val out = ByteArrayOutputStream(maxOf(64, data.size / 2))
        deflater.setInput(data, 0, data.size, false)
        while (true) {
            deflater.setOutput(block, 0, block.size)
            val answer = deflater.deflate(JZlib.Z_FINISH)
            out.write(block, 0, block.size - deflater.avail_out)
            if (answer == JZlib.Z_STREAM_END) break
            if (answer != JZlib.Z_OK && answer != JZlib.Z_BUF_ERROR) throw IOException("deflate answered $answer")
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            out.append(Character.forDigit((byte.toInt() shr 4) and 0xF, 16))
            out.append(Character.forDigit(byte.toInt() and 0xF, 16))
        }
        return out.toString()
    }
}
