package cat.narezany.patcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import cat.narezany.patcher.data.Catalog
import cat.narezany.patcher.data.Net
import cat.narezany.patcher.data.PatcherRelease
import cat.narezany.patcher.work.News
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** The list of mods as the screens see it: what is known, and whether more is being asked for. */
object Shelf {
    val catalog = MutableStateFlow<Catalog?>(null)
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    /** Counts up whenever what is installed may have changed, so the screens look again. */
    val looked = MutableStateFlow(0)

    /** How far a download of the patcher itself has got, 0..1; null when there is none. */
    val selfUpdate = MutableStateFlow<Float?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // which version of a mod is installed may be learnt a moment after it was asked
        cat.narezany.patcher.data.ApkHashes.learnt = { looked.value += 1 }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        if (catalog.value == null) catalog.value = Catalog.remembered(app)
        if (loading.value) return
        loading.value = true
        scope.launch {
            val fresh = Catalog.fetch(app)
            if (fresh != null) {
                catalog.value = fresh
                failed.value = false
                // what was just learnt is not news to the person looking at it
                News.tell(app, fresh)
            } else {
                failed.value = true
            }
            loading.value = false
        }
    }

    fun updateSelf(context: Context, release: PatcherRelease) {
        if (selfUpdate.value != null) return
        val app = context.applicationContext
        selfUpdate.value = 0f
        scope.launch {
            try {
                val room = File(app.getExternalFilesDir(null) ?: app.filesDir, "made")
                val apk = File(room, "patcher-${release.version}.apk")
                if (!apk.exists()) {
                    Net.file(release.url, apk, release.size, { false }) { done, of ->
                        selfUpdate.value = if (of > 0) done.toFloat() / of else 0f
                    }
                }
                Launch.install(app, apk)
            } catch (failed: Exception) {
                // the banner comes back and can be pressed again
            }
            selfUpdate.value = null
        }
    }
}

/** Handing things to the system: an apk to install, an app to remove or open, a link. */
object Launch {

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun allowInstalls(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun remove(context: Context, packageName: String) {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun link(context: Context, address: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (nothing: Exception) {
            // a phone with no browser has nowhere to open it
        }
    }
}
