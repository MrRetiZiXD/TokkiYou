package cat.narezany.patcher.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cat.narezany.patcher.BuildConfig
import cat.narezany.patcher.Strings
import cat.narezany.patcher.data.Catalog
import cat.narezany.patcher.data.Prefs
import cat.narezany.patcher.data.Standing
import cat.narezany.patcher.data.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Looks at the list of mods a few times a day and says so when something the
 * person has installed has a newer version. Each version is announced once.
 */
class News(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val prefs = Prefs.of(context)
        if (!prefs.now.value.notify) return@withContext Result.success()
        val catalog = Catalog.fetch(context) ?: return@withContext Result.retry()
        tell(context, catalog)
        Result.success()
    }

    companion object {
        fun tell(context: Context, catalog: Catalog) {
            val prefs = Prefs.of(context)
            val words = Strings.of(context)
            catalog.mods.forEachIndexed { at, mod ->
                val standing = Standing.of(context, mod, prefs.now.value.beta, wait = true)
                val latest = standing.latest ?: return@forEachIndexed
                val key = "${mod.id}_${latest.version}"
                if (standing.outdated && !prefs.told(key)) {
                    prefs.tell(key)
                    Notify.news(context, 100 + at, words.newsModTitle(mod.name, latest.version),
                        words.pick(latest.notes).ifEmpty { words.newsModText })
                }
            }
            val patcher = catalog.patcher ?: return
            if (Versions.compare(patcher.version, BuildConfig.VERSION_NAME) > 0
                    && !prefs.told("patcher_${patcher.version}")) {
                prefs.tell("patcher_${patcher.version}")
                Notify.news(context, 99, words.newsPatcherTitle(patcher.version),
                    words.pick(patcher.notes).ifEmpty { words.patcherUpdateText })
            }
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<News>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("news", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
