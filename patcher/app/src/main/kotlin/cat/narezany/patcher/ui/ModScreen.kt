package cat.narezany.patcher.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.narezany.patcher.data.Installed
import cat.narezany.patcher.data.Prefs
import cat.narezany.patcher.data.Standing
import cat.narezany.patcher.data.Versions
import cat.narezany.patcher.work.Engine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModScreen(id: String, back: () -> Unit) {
    val words = LocalWords.current
    val context = LocalContext.current
    val catalog by Shelf.catalog.collectAsStateWithLifecycle()
    val looked by Shelf.looked.collectAsStateWithLifecycle()
    val prefs by Prefs.of(context).now.collectAsStateWithLifecycle()
    val state by Engine.state.collectAsStateWithLifecycle()
    val mod = catalog?.mods?.firstOrNull { it.id == id }
    if (mod == null) {
        LaunchedEffect(Unit) { back() }
        return
    }
    val standing = remember(mod, looked, prefs.beta) { Standing.of(context, mod, prefs.beta) }
    val release = standing.latest
    var removing by remember { mutableStateOf(false) }

    // what was made has been installed: nothing more to keep of it
    val mine = (state as? Engine.State.Ready)?.takeIf { it.mod.id == mod.id }
    LaunchedEffect(looked, mine) {
        val have = (standing.installed as? Installed.Ours)?.version
        if (mine != null && have != null && Versions.compare(have, mine.release.version) >= 0) {
            Engine.done(context)
        }
    }

    fun install(apk: java.io.File) {
        when {
            standing.installed is Installed.Theirs -> removing = true
            !Launch.canInstall(context) -> Launch.allowInstalls(context)
            else -> Launch.install(context, apk)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mod.name) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = words.back)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // who it is and where it stands
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                ModIcon(mod.icon, 96.dp)
                Spacer(Modifier.height(16.dp))
                Text(mod.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(standingText(words, standing), style = MaterialTheme.typography.bodyLarge,
                    color = if (standing.outdated) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                if (release != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(release.version, MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer)
                        if (release.beta) Pill(words.betaTag, MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            val about = words.pick(mod.about)
            if (about.isNotEmpty()) {
                Text(about, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
            if (release == null) {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Roundel(Icons.Rounded.Schedule)
                        Spacer(Modifier.width(16.dp))
                        Text(words.noRelease, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (release != null) {
                // the work, whatever it is at this moment
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    val now = state
                    val kind = when (now) {
                        is Engine.State.Working -> if (now.mod.id == mod.id) "working" else "busy"
                        is Engine.State.Ready -> if (now.mod.id == mod.id) "ready" else "idle"
                        is Engine.State.Failed -> if (now.mod.id == mod.id) "failed" else "idle"
                        else -> "idle"
                    }
                    AnimatedContent(kind, label = "work") { shown ->
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            when (shown) {
                                "working" -> {
                                    val working = now as? Engine.State.Working
                                    if (working != null) {
                                        val stages = Engine.stages(working.release)
                                        val at = stages.indexOf(working.stage)
                                        for ((number, stage) in stages.withIndex()) {
                                            val done = number < at
                                            val current = stage == working.stage
                                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(32.dp).clip(CircleShape).background(
                                                    if (done || current) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceContainerHighest),
                                                    contentAlignment = Alignment.Center) {
                                                    when {
                                                        done -> Icon(Icons.Rounded.Check, contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(18.dp))
                                                        current -> CircularProgressIndicator(
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                                        else -> Text((number + 1).toString(),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                Spacer(Modifier.width(14.dp))
                                                Text(words.stage(stage), style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (done || current) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (current) {
                                                Column(Modifier.padding(start = 46.dp, bottom = 6.dp)) {
                                                    if (working.of > 0) {
                                                        LinearProgressIndicator(
                                                            progress = { (working.done.toFloat() / working.of).coerceIn(0f, 1f) },
                                                            modifier = Modifier.fillMaxWidth())
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(Formatter.formatShortFileSize(context, working.done) + " / " +
                                                            Formatter.formatShortFileSize(context, working.of),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    } else {
                                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(onClick = { Engine.cancel() }) { Text(words.cancel) }
                                    }
                                }
                                "ready" -> {
                                    val ready = now as? Engine.State.Ready
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Text(words.readyTitle, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(if (!Launch.canInstall(context)) words.allowInstall else words.readyText,
                                        style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { ready?.let { install(it.apk) } }) { Text(words.installNow) }
                                        TextButton(onClick = { Engine.cancel() }) { Text(words.later) }
                                    }
                                }
                                "failed" -> {
                                    val failed = now as? Engine.State.Failed
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(12.dp))
                                        Text(when (failed?.trouble) {
                                            Engine.Trouble.NETWORK -> words.troubleNetwork
                                            Engine.Trouble.WRONG_APK -> words.troubleWrongApk
                                            Engine.Trouble.BAD_PATCH -> words.troubleBadPatch
                                            Engine.Trouble.NO_ROOM -> words.troubleNoRoom
                                            else -> words.troubleOther
                                        }, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    if (failed != null && failed.detail.isNotEmpty()
                                            && failed.trouble != Engine.Trouble.WRONG_APK) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(failed.detail, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { Engine.cancel(); Engine.start(context, mod, release) }) {
                                        Text(words.retry)
                                    }
                                }
                                else -> {
                                    val installed = standing.installed
                                    val fresh = installed is Installed.Ours && !standing.outdated
                                    val label = when {
                                        installed is Installed.Ours && standing.outdated -> words.update
                                        fresh -> words.reinstall
                                        else -> words.install
                                    }
                                    if (fresh) {
                                        Button(onClick = { Launch.open(context, mod.packageName) },
                                            modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                            Text(words.open, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        FilledTonalButton(enabled = shown != "busy",
                                            onClick = { Engine.start(context, mod, release) },
                                            modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                            Text(label, style = MaterialTheme.typography.titleMedium)
                                        }
                                    } else {
                                        Button(enabled = shown != "busy",
                                            onClick = { Engine.start(context, mod, release) },
                                            modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                            Text(label, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Group {
                    if (!release.whole) {
                        row(Icons.Rounded.Android, words.original, release.baseName)
                        row(Icons.Rounded.Healing, words.patch, Formatter.formatShortFileSize(context, release.patchSize))
                    }
                    row(Icons.Rounded.Inventory2, words.finishedApk,
                        Formatter.formatShortFileSize(context, release.resultSize))
                }

                val notes = words.pick(release.notes)
                if (notes.isNotEmpty()) {
                    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(words.whatsNew, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // where the original is to come from
    val asking = (state as? Engine.State.Asking)?.takeIf { it.mod.id == mod.id }
    if (asking != null) {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
            if (chosen != null) Engine.pick(context, asking.mod, asking.release, chosen)
        }
        ModalBottomSheet(onDismissRequest = { Engine.cancel() }) {
            Column(Modifier.padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(words.needOriginalTitle(asking.release.baseName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(8.dp))
                Text(words.needOriginalText, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
                for (source in asking.release.sources) {
                    ListItem(
                        colors = clear,
                        headlineContent = {
                            Text(if (source.direct) words.downloadFrom(source.name) else words.openPage(source.name))
                        },
                        supportingContent = if (source.direct) {
                            { Text(Formatter.formatShortFileSize(context, asking.release.baseSize)) }
                        } else null,
                        leadingContent = {
                            Icon(if (source.direct) Icons.Rounded.CloudDownload
                            else Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        },
                        modifier = Modifier.clickableRow {
                            if (source.direct) Engine.fetch(context, asking.mod, asking.release, source)
                            else Launch.link(context, source.url)
                        },
                    )
                }
                ListItem(
                    colors = clear,
                    headlineContent = { Text(words.pickFile) },
                    supportingContent = { Text(words.pickFileNote) },
                    leadingContent = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                    modifier = Modifier.clickableRow {
                        picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
                    },
                )
            }
        }
    }

    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(words.removeOriginalTitle) },
            text = { Text(words.removeOriginalText) },
            confirmButton = {
                FilledTonalButton(onClick = { removing = false; Launch.remove(context, mod.packageName) }) {
                    Text(words.remove)
                }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(words.cancel) } },
        )
    }
}
