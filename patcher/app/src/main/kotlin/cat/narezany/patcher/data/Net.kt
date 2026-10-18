package cat.narezany.patcher.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** The little of the network the app needs: a page of text, and a file with its progress. */
object Net {

    private const val AGENT = "TokkiYouPatcher"

    class Cancelled : IOException("cancelled")

    private fun open(address: String, from: Long = 0L): HttpURLConnection {
        val link = URL(address).openConnection() as HttpURLConnection
        link.connectTimeout = 15_000
        link.readTimeout = 30_000
        link.instanceFollowRedirects = true
        link.setRequestProperty("User-Agent", AGENT)
        if (from > 0) link.setRequestProperty("Range", "bytes=$from-")
        return link
    }

    /** The body of a page, or null when there was no answering it. */
    fun text(address: String, post: String? = null, bearer: String? = null): String? {
        var link: HttpURLConnection? = null
        return try {
            link = open(address)
            if (bearer != null) link.setRequestProperty("Authorization", "Bearer $bearer")
            if (post != null) {
                link.requestMethod = "POST"
                link.doOutput = true
                link.setRequestProperty("Content-Type", "application/json")
                link.outputStream.use { it.write(post.toByteArray()) }
            }
            if (link.responseCode !in 200..299) null
            else link.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (failed: Exception) {
            null
        } finally {
            link?.disconnect()
        }
    }

    /**
     * Fetch [address] into [into], going on from where an earlier try stopped.
     *
     * The part that has arrived is kept beside the file under another name
     * until all of it is there, so a file with the right name is a whole one.
     */
    fun file(
        address: String, into: File, expected: Long,
        cancelled: () -> Boolean, seen: (done: Long, of: Long) -> Unit,
    ) {
        val part = File(into.path + ".part")
        into.parentFile?.mkdirs()
        var have = if (part.exists()) part.length() else 0L
        if (expected > 0 && have > expected) {
            part.delete()
            have = 0
        }
        var tries = 0
        // how long the whole file is: told beforehand, or by the server
        var target = expected
        while (true) {
            if (target > 0 && have == target) break
            var link: HttpURLConnection? = null
            try {
                link = open(address, have)
                val code = link.responseCode
                if (code == 416 && have > 0) {
                    // asked for what is past the end: it was all here already
                    break
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                if (code == 200 && have > 0) {
                    // a server that does not go on from the middle starts over
                    part.delete()
                    have = 0
                }
                val length = link.contentLengthLong
                if (target <= 0 && length > 0) target = have + length
                val whole = target
                RandomAccessFile(part, "rw").use { out ->
                    out.seek(have)
                    val block = ByteArray(1 shl 16)
                    var told = have
                    link.inputStream.use { input ->
                        while (true) {
                            if (cancelled()) throw Cancelled()
                            val got = input.read(block)
                            if (got < 0) break
                            out.write(block, 0, got)
                            have += got
                            if (have - told >= 256 * 1024) {
                                told = have
                                seen(have, whole)
                            }
                        }
                    }
                }
                seen(have, whole)
                // an answer that ends early is a connection that dropped
                if (target <= 0 || have >= target) break
                throw IOException("cut short")
            } catch (stopped: Cancelled) {
                throw stopped
            } catch (failed: IOException) {
                // a download of hundreds of megabytes over a phone's network
                // is interrupted more often than not; it is taken up again
                if (++tries > 6) throw failed
                Thread.sleep(1500L * tries)
            } finally {
                link?.disconnect()
            }
        }
        if (target > 0 && have != target) throw IOException("the file is ${have} bytes, not $target")
        if (!part.renameTo(into)) throw IOException("could not keep the file")
    }
}
