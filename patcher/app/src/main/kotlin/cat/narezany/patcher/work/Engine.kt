package cat.narezany.patcher.work

import android.content.Context
import android.net.Uri
import cat.narezany.patcher.core.Patch
import cat.narezany.patcher.data.Mod
import cat.narezany.patcher.data.Net
import cat.narezany.patcher.data.Prefs
import cat.narezany.patcher.data.Release
import cat.narezany.patcher.data.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Making a mod's apk, one at a time, from the moment the person asks to the
 * apk lying ready to install.
 *
 * It lives outside any screen: the work takes minutes and a screen does not
 * last that long. Screens watch [state]; a service keeps the app alive and
 * shows the same progress in a notification while there is any.
 */
object Engine {

    enum class Stage { ORIGINAL, CHECKING, PATCH, MAKING, APK }

    /** The steps a release goes through, in order: a patched one has four, one handed out whole has one. */
    fun stages(release: Release): List<Stage> =
        if (release.whole) listOf(Stage.APK) else listOf(Stage.ORIGINAL, Stage.CHECKING, Stage.PATCH, Stage.MAKING)

    /** Why it stopped, for the screen to put into the person's language. */
    enum class Trouble { NETWORK, WRONG_APK, BAD_PATCH, NO_ROOM, OTHER }

    sealed class State {
        data object Idle : State()

        /** TikTok's own apk is not here yet, and the person is asked where from. */
        data class Asking(val mod: Mod, val release: Release) : State()

        data class Working(val mod: Mod, val release: Release, val stage: Stage,
                           val done: Long, val of: Long) : State()

        data class Ready(val mod: Mod, val release: Release, val apk: File) : State()

        data class Failed(val mod: Mod, val release: Release, val trouble: Trouble,
                          val detail: String) : State()
    }

    private val flow = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> get() = flow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var stop = false

    private fun room(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    fun original(context: Context, release: Release) =
        File(room(context), "original/${release.baseSha256}.apk")

    private fun patch(context: Context, release: Release) =
        File(room(context), "patch/${release.patchSha256}.mpatch")

    fun made(context: Context, mod: Mod, release: Release) =
        File(room(context), "made/${mod.id}-${release.version}.apk")

    /** How much the app is keeping on the phone, in bytes. */
    fun kept(context: Context): Long =
        room(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clear(context: Context) {
        if (flow.value is State.Working) return
        room(context).listFiles()?.forEach { it.deleteRecursively() }
        flow.value = State.Idle
    }

    val busy: Boolean get() = flow.value is State.Working

    /** Begin: with the original already here it goes straight on, otherwise it asks. */
    fun start(context: Context, mod: Mod, release: Release) {
        if (busy) return
        val app = context.applicationContext
        val ready = made(app, mod, release)
        if (ready.exists() && ready.length() == release.resultSize) {
            flow.value = State.Ready(mod, release, ready)
            return
        }
        if (release.whole) whole(app, mod, release)
        else if (original(app, release).exists()) run(app, mod, release) { }
        else flow.value = State.Asking(mod, release)
    }

    /** The person chose a place the apk can be fetched from as it is. */
    fun fetch(context: Context, mod: Mod, release: Release, source: Source) {
        if (busy) return
        val app = context.applicationContext
        run(app, mod, release) {
            flow.value = State.Working(mod, release, Stage.ORIGINAL, 0, release.baseSize)
            val unchecked = File(original(app, release).path + ".new")
            unchecked.delete()
            Net.file(source.url, unchecked, 0L, { stop }) { done, _ ->
                flow.value = State.Working(mod, release, Stage.ORIGINAL, done, release.baseSize)
            }
            take(app, mod, release, unchecked)
        }
    }

    /** The person pointed at an apk they already have. */
    fun pick(context: Context, mod: Mod, release: Release, chosen: Uri) {
        if (busy) return
        val app = context.applicationContext
        run(app, mod, release) {
            flow.value = State.Working(mod, release, Stage.ORIGINAL, 0, release.baseSize)
            val unchecked = File(original(app, release).path + ".new")
            unchecked.parentFile?.mkdirs()
            val input = app.contentResolver.openInputStream(chosen)
                ?: throw Patch.Refused("the file could not be opened")
            input.use { from ->
                unchecked.outputStream().use { to ->
                    val block = ByteArray(1 shl 16)
                    var done = 0L
                    while (true) {
                        if (stop) throw Net.Cancelled()
                        val got = from.read(block)
                        if (got < 0) break
                        to.write(block, 0, got)
                        done += got
                        if (done and 0xFFFFF < got) {
                            flow.value = State.Working(mod, release, Stage.ORIGINAL, done, release.baseSize)
                        }
                    }
                }
            }
            take(app, mod, release, unchecked)
        }
    }

    /** An apk has arrived: it is kept only if it is the one the patch is for. */
    private fun take(context: Context, mod: Mod, release: Release, unchecked: File) {
        flow.value = State.Working(mod, release, Stage.CHECKING, 0, unchecked.length())
        val right = unchecked.length() == release.baseSize && Patch.sha256(unchecked, { stop }) { seen ->
            flow.value = State.Working(mod, release, Stage.CHECKING, seen, unchecked.length())
        } == release.baseSha256
        if (!right) {
            unchecked.delete()
            throw WrongApk()
        }
        val kept = original(context, release)
        kept.delete()
        if (!unchecked.renameTo(kept)) throw java.io.IOException("could not keep the apk")
    }

    private class WrongApk : java.io.IOException("not the apk the patch is for")

    /** A mod handed out as a finished apk: fetched, checked against its hash, and that is all. */
    private fun whole(context: Context, mod: Mod, release: Release) {
        stop = false
        flow.value = State.Working(mod, release, Stage.APK, 0, release.resultSize)
        WorkService.start(context)
        job = scope.launch {
            val out = made(context, mod, release)
            try {
                out.parentFile?.mkdirs()
                out.parentFile?.listFiles()?.forEach { if (it.name.startsWith(mod.id + "-")) it.delete() }
                Net.file(release.apkUrl, out, release.resultSize, { stop }) { done, of ->
                    flow.value = State.Working(mod, release, Stage.APK, done, of)
                }
                if (Patch.sha256(out, { stop }) != release.apkSha256) {
                    out.delete()
                    throw Patch.Refused("the apk did not arrive whole")
                }
                flow.value = State.Ready(mod, release, out)
            } catch (cancelled: Net.Cancelled) {
                flow.value = State.Idle
            } catch (cancelled: Patch.Cancelled) {
                flow.value = State.Idle
            } catch (refused: Patch.Refused) {
                flow.value = State.Failed(mod, release, Trouble.BAD_PATCH, refused.message ?: "")
            } catch (failed: java.io.IOException) {
                val full = room(context).usableSpace < 64L shl 20
                flow.value = State.Failed(mod, release, if (full) Trouble.NO_ROOM else Trouble.NETWORK,
                    failed.message ?: failed.toString())
            } catch (failed: Throwable) {
                flow.value = State.Failed(mod, release, Trouble.OTHER, failed.toString())
            }
        }
    }

    private fun run(context: Context, mod: Mod, release: Release, first: () -> Unit) {
        stop = false
        flow.value = State.Working(mod, release, Stage.ORIGINAL, 0, release.baseSize)
        WorkService.start(context)
        job = scope.launch {
            try {
                first()
                val base = original(context, release)

                val patch = patch(context, release)
                if (!patch.exists()) {
                    flow.value = State.Working(mod, release, Stage.PATCH, 0, release.patchSize)
                    Net.file(release.patchUrl, patch, release.patchSize, { stop }) { done, _ ->
                        flow.value = State.Working(mod, release, Stage.PATCH, done, release.patchSize)
                    }
                    if (Patch.sha256(patch, { stop }) != release.patchSha256) {
                        patch.delete()
                        throw Patch.Refused("the patch did not arrive whole")
                    }
                }

                flow.value = State.Working(mod, release, Stage.MAKING, 0, release.resultSize)
                val out = made(context, mod, release)
                out.parentFile?.mkdirs()
                // anything made for an earlier release is in the way now
                out.parentFile?.listFiles()?.forEach { it.delete() }
                Patch.apply(base, patch, out, { stop }) { done, of ->
                    flow.value = State.Working(mod, release, Stage.MAKING, done, of)
                }
                patch.delete()
                if (!Prefs.of(context).now.value.keepOriginal) base.delete()
                flow.value = State.Ready(mod, release, out)
            } catch (cancelled: Net.Cancelled) {
                flow.value = State.Idle
            } catch (cancelled: Patch.Cancelled) {
                flow.value = State.Idle
            } catch (wrong: WrongApk) {
                flow.value = State.Failed(mod, release, Trouble.WRONG_APK, "")
            } catch (refused: Patch.Refused) {
                patch(context, release).delete()
                flow.value = State.Failed(mod, release, Trouble.BAD_PATCH, refused.message ?: "")
            } catch (failed: java.io.IOException) {
                val full = room(context).usableSpace < 64L shl 20
                flow.value = State.Failed(mod, release, if (full) Trouble.NO_ROOM else Trouble.NETWORK,
                    failed.message ?: failed.toString())
            } catch (failed: Throwable) {
                flow.value = State.Failed(mod, release, Trouble.OTHER, failed.toString())
            }
        }
    }

    fun cancel() {
        stop = true
        if (flow.value is State.Asking || flow.value is State.Failed || flow.value is State.Ready) {
            flow.value = State.Idle
        }
    }

    /** The made apk is installed, or the person has done with it. */
    fun done(context: Context) {
        val now = flow.value
        if (now is State.Ready) now.apk.delete()
        if (now !is State.Working) flow.value = State.Idle
    }
}
