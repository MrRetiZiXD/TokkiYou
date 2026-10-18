package cat.narezany.patcher.data

import android.content.Context
import android.content.pm.PackageManager
import cat.narezany.patcher.core.Patch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** What is on the phone under the name a mod installs as. */
sealed class Installed {
    /** Nothing. */
    data object Nothing : Installed()

    /**
     * The mod; which version, when that is known. Releases from before the
     * patcher do not say, and [sure] is false while it is still being found out.
     */
    data class Ours(val version: String?, val sure: Boolean = true) : Installed()

    /**
     * The app as its own maker signed it. Android will not put the mod over
     * it: it has to be removed first, and its accounts signed in to again.
     */
    data object Theirs : Installed()

    companion object {
        private const val VERSION = "cat.narezany.tokkiyou.version"

        /** [wait]: off the main thread, where reading a whole apk to know it is no trouble. */
        fun of(context: Context, mod: Mod, wait: Boolean = false): Installed {
            val manager = context.packageManager
            val info = try {
                manager.getApplicationInfo(mod.packageName, PackageManager.GET_META_DATA)
            } catch (missing: PackageManager.NameNotFoundException) {
                return Nothing
            }
            val said = info.metaData?.get(VERSION)?.toString()
            if (!said.isNullOrEmpty()) return Ours(said)
            if (mod.ownPackage) {
                // A mod that does not say which version it is, is known by
                // its file: the same bytes as a release are that release.
                val updated = try {
                    manager.getPackageInfo(mod.packageName, 0).lastUpdateTime
                } catch (gone: Exception) {
                    0L
                }
                val hash = ApkHashes.of(context, info.sourceDir, updated, wait) ?: return Ours(null, sure = false)
                return Ours(mod.releases.firstOrNull { it.whole && it.apkSha256 == hash }?.version)
            }
            // a release from before it said: known by being signed as this app is
            return if (manager.checkSignatures(context.packageName, mod.packageName)
                    == PackageManager.SIGNATURE_MATCH) Ours(null) else Theirs
        }
    }
}

/** The sha256 of installed apks, worked out once for each install and remembered. */
object ApkHashes {
    private val known = ConcurrentHashMap<String, String>()
    private val busy = ConcurrentHashMap.newKeySet<String>()
    private val worker = Executors.newSingleThreadExecutor()

    /** Told when a hash that was asked for has been worked out. */
    @Volatile var learnt: () -> Unit = {}

    fun of(context: Context, path: String, updated: Long, wait: Boolean): String? {
        val key = "$path|$updated"
        known[key]?.let { return it }
        val store = context.applicationContext.getSharedPreferences("hashes", Context.MODE_PRIVATE)
        store.getString(key, null)?.let {
            known[key] = it
            return it
        }
        val work = Runnable {
            try {
                val hash = Patch.sha256(File(path))
                known[key] = hash
                store.edit().putString(key, hash).apply()
            } catch (unreadable: Exception) {
                known[key] = ""
            } finally {
                busy.remove(key)
            }
        }
        if (wait) {
            work.run()
            return known[key]
        }
        if (busy.add(key)) worker.execute {
            work.run()
            learnt()
        }
        return null
    }
}

/** Where a mod stands for this person: what they have and what they could have. */
data class Standing(val mod: Mod, val installed: Installed, val latest: Release?) {
    val outdated: Boolean
        get() {
            val have = installed as? Installed.Ours ?: return false
            val newest = latest ?: return false
            if (!have.sure) return false
            return have.version == null || Versions.compare(newest.version, have.version) > 0
        }

    companion object {
        fun of(context: Context, mod: Mod, beta: Boolean, wait: Boolean = false) =
            Standing(mod, Installed.of(context, mod, wait), mod.latest(beta))
    }
}
