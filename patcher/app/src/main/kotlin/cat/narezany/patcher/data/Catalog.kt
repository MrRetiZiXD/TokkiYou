package cat.narezany.patcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Somewhere an apk can be had from. */
data class Source(val name: String, val url: String, val direct: Boolean)

/** One release of a mod: which apk it is made from, and the patch that makes it. */
data class Release(
    val version: String,
    val beta: Boolean,
    val baseName: String,
    val baseSha256: String,
    val baseSize: Long,
    val sources: List<Source>,
    val patchUrl: String,
    val patchSha256: String,
    val patchSize: Long,
    val resultSize: Long,
    val notes: Map<String, String>,
    /** For a mod handed out whole rather than as a patch: where its apk is, and its hash. */
    val apkUrl: String = "",
    val apkSha256: String = "",
) {
    val whole: Boolean get() = apkUrl.isNotEmpty()
}

data class Mod(
    val id: String,
    val name: String,
    val icon: String,
    val about: Map<String, String>,
    val packageName: String,
    val releases: List<Release>,
    /** Installed under a name of its own, beside the app it is a mod of: whatever has that name is the mod. */
    val ownPackage: Boolean = false,
) {
    /** The newest release this person may have: betas only if they asked for them. */
    fun latest(beta: Boolean): Release? =
        releases.filter { beta || !it.beta }.maxWithOrNull { a, b -> Versions.compare(a.version, b.version) }
}

data class PatcherRelease(val version: String, val url: String, val sha256: String, val size: Long,
                          val notes: Map<String, String>)

data class Catalog(val patcher: PatcherRelease?, val channel: String, val mods: List<Mod>) {

    companion object {
        /**
         * Read from the project's own git server, like the mod's version file:
         * a link there can be changed for everybody without anybody updating
         * anything.
         */
        const val SOURCE = "https://raw.githubusercontent.com/BottomSalamander/TokkiYou/main/patcher/catalog.json"
        private const val BETA = "https://tokkiyou.narez.xyz/api/patcher/beta"

        private fun kept(context: Context) = File(context.filesDir, "catalog.json")

        /**
         * Where the list is read from. A file named catalog.url in the app's
         * own folder on shared storage names another place: that is how a
         * release is tried out before the list everybody reads is changed.
         */
        private fun source(context: Context): String = try {
            File(context.getExternalFilesDir(null), "catalog.url").readText().trim().ifEmpty { SOURCE }
        } catch (none: Exception) {
            SOURCE
        }

        /** What was read last time, for a start without a network. */
        fun remembered(context: Context): Catalog? = try {
            parse(kept(context).readText(), null)
        } catch (missing: Exception) {
            null
        }

        fun fetch(context: Context): Catalog? {
            val text = Net.text(source(context)) ?: return null
            val prefs = Prefs.of(context).now.value
            // the early releases are for the people who support the project,
            // and the server is who knows whether this is one of them
            val early = if (prefs.beta && prefs.telegramToken.isNotEmpty()) {
                Net.text(BETA, bearer = prefs.telegramToken)
            } else null
            return try {
                val catalog = parse(text, early)
                kept(context).writeText(text)
                catalog
            } catch (broken: Exception) {
                null
            }
        }

        fun parse(text: String, early: String?): Catalog {
            val root = JSONObject(text)
            val extra = HashMap<String, JSONArray>()
            if (early != null) {
                try {
                    val mods = JSONObject(early).optJSONObject("mods")
                    if (mods != null) for (id in mods.keys()) extra[id] = mods.getJSONArray(id)
                } catch (ignored: Exception) {
                }
            }
            val patcher = root.optJSONObject("patcher")?.let {
                PatcherRelease(it.getString("version"), it.getString("url"), it.optString("sha256"),
                    it.optLong("size"), words(it.optJSONObject("notes")))
            }
            val mods = ArrayList<Mod>()
            val list = root.getJSONArray("mods")
            for (i in 0 until list.length()) {
                val mod = list.getJSONObject(i)
                val releases = ArrayList<Release>()
                val given = mod.getJSONArray("releases")
                for (j in 0 until given.length()) releases.add(release(given.getJSONObject(j)))
                // Kept apart from the patches, under a name the first version
                // of the app never reads: it would have choked on one of these.
                val whole = mod.optJSONArray("apks") ?: JSONArray()
                for (j in 0 until whole.length()) releases.add(apk(whole.getJSONObject(j)))
                extra[mod.getString("id")]?.let { more ->
                    for (j in 0 until more.length()) releases.add(release(more.getJSONObject(j)))
                }
                mods.add(Mod(mod.getString("id"), mod.getString("name"), mod.optString("icon"),
                    words(mod.optJSONObject("about")), mod.getString("package"), releases,
                    mod.optBoolean("own_package", false)))
            }
            return Catalog(patcher, root.optString("channel", "https://t.me/tokkiyou"), mods)
        }

        private fun apk(given: JSONObject): Release {
            val file = given.getJSONObject("apk")
            return Release(
                given.getString("version"), given.optBoolean("beta", false),
                "", "", 0L, emptyList(), "", "", 0L, file.getLong("size"),
                words(given.optJSONObject("notes")), file.getString("url"), file.getString("sha256"),
            )
        }

        private fun release(given: JSONObject): Release {
            val base = given.getJSONObject("base")
            val patch = given.getJSONObject("patch")
            val sources = ArrayList<Source>()
            val list = base.optJSONArray("sources") ?: JSONArray()
            for (i in 0 until list.length()) {
                val source = list.getJSONObject(i)
                sources.add(Source(source.getString("name"), source.getString("url"),
                    source.optBoolean("direct", false)))
            }
            return Release(
                given.getString("version"), given.optBoolean("beta", false),
                base.getString("name"), base.getString("sha256"), base.getLong("size"), sources,
                patch.getString("url"), patch.getString("sha256"), patch.getLong("size"),
                given.getJSONObject("result").getLong("size"),
                words(given.optJSONObject("notes")),
            )
        }

        private fun words(given: JSONObject?): Map<String, String> {
            val out = HashMap<String, String>()
            if (given != null) for (key in given.keys()) out[key] = given.optString(key)
            return out
        }
    }
}

object Versions {
    /** Dotted versions by their numbers: 0.9 is behind 0.15. */
    fun compare(a: String, b: String): Int {
        val left = a.split('.')
        val right = b.split('.')
        for (i in 0 until maxOf(left.size, right.size)) {
            val one = left.getOrNull(i)?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val two = right.getOrNull(i)?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (one != two) return one.compareTo(two)
        }
        return 0
    }
}
