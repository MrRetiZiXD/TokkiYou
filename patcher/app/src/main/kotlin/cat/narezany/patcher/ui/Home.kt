package cat.narezany.patcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.narezany.patcher.BuildConfig
import cat.narezany.patcher.Strings
import cat.narezany.patcher.data.Installed
import cat.narezany.patcher.data.Prefs
import cat.narezany.patcher.data.Standing
import cat.narezany.patcher.data.Versions
import cat.narezany.patcher.work.Engine

/** What to say under a mod's name. */
fun standingText(words: Strings, standing: Standing): String {
    val installed = standing.installed
    val latest = standing.latest
    return when {
        installed is Installed.Theirs -> words.originalInstalled
        installed is Installed.Nothing -> words.notInstalled
        installed is Installed.Ours && !installed.sure -> words.installedSome
        standing.outdated && latest != null -> words.updateTo(latest.version)
        installed is Installed.Ours && installed.version != null -> words.installed(installed.version)
        else -> words.installedOld
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(open: (String) -> Unit, settings: () -> Unit) {
    val words = LocalWords.current
    val context = LocalContext.current
    val catalog by Shelf.catalog.collectAsStateWithLifecycle()
    val loading by Shelf.loading.collectAsStateWithLifecycle()
    val failed by Shelf.failed.collectAsStateWithLifecycle()
    val looked by Shelf.looked.collectAsStateWithLifecycle()
    val prefs by Prefs.of(context).now.collectAsStateWithLifecycle()
    val selfUpdate by Shelf.selfUpdate.collectAsStateWithLifecycle()
    val state by Engine.state.collectAsStateWithLifecycle()
    val behaviour = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(behaviour.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Margy Patcher", fontWeight = FontWeight.SemiBold) },
                actions = {
                    FilledTonalIconButton(onClick = settings, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = words.settings)
                    }
                },
                scrollBehavior = behaviour,
            )
        },
    ) { inner ->
        val known = catalog
        val standings = remember(known, looked, prefs.beta) {
            known?.mods?.map { Standing.of(context, it, prefs.beta) } ?: emptyList()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading && known == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            val patcher = known?.patcher
            if (patcher != null && Versions.compare(patcher.version, BuildConfig.VERSION_NAME) > 0) {
                item {
                    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Roundel(Icons.Rounded.SystemUpdate,
                                    container = MaterialTheme.colorScheme.tertiary,
                                    content = MaterialTheme.colorScheme.onTertiary)
                                Spacer(Modifier.width(16.dp))
                                Text(words.patcherUpdateTitle(patcher.version),
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(words.pick(patcher.notes).ifEmpty { words.patcherUpdateText },
                                style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            val progress = selfUpdate
                            if (progress != null) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            } else {
                                Button(onClick = {
                                    if (Launch.canInstall(context)) Shelf.updateSelf(context, patcher)
                                    else Launch.allowInstalls(context)
                                }) { Text(words.update) }
                            }
                        }
                    }
                }
            }
            if (known == null && !loading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (failed) words.noNetwork else words.nothingYet,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { Shelf.refresh(context) }) { Text(words.retry) }
                    }
                }
            }
            if (known != null) {
                items(standings, key = { it.mod.id }) { standing ->
                    ModCard(standing, state, open = { open(standing.mod.id) })
                }
                item {
                    Card(
                        onClick = { Launch.link(context, known.channel) },
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Roundel(Icons.AutoMirrored.Rounded.Send,
                                container = MaterialTheme.colorScheme.secondary,
                                content = MaterialTheme.colorScheme.onSecondary, size = 48.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(words.channelTitle, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(words.channelText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModCard(standing: Standing, state: Engine.State, open: () -> Unit) {
    val words = LocalWords.current
    val context = LocalContext.current
    val mod = standing.mod
    val release = standing.latest
    Card(
        onClick = open,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    ModIcon(mod.icon, 64.dp)
                    if (standing.outdated) {
                        // the dot that says: this one can be updated
                        Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(mod.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(standingText(words, standing), style = MaterialTheme.typography.bodyLarge,
                        color = if (standing.outdated) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))

            val working = (state as? Engine.State.Working)?.takeIf { it.mod.id == mod.id }
            val ready = (state as? Engine.State.Ready)?.takeIf { it.mod.id == mod.id }
            if (working != null) {
                Text(words.stage(working.stage), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (working.of > 0) {
                    LinearProgressIndicator(progress = { (working.done.toFloat() / working.of).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (release != null) {
                        Pill(release.version, MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurfaceVariant)
                        if (release.beta) Pill(words.betaTag, MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                val installed = standing.installed
                when {
                    release == null -> FilledTonalButton(onClick = open, enabled = false) { Text(words.soon) }
                    ready != null -> Button(onClick = open) { Text(words.installNow) }
                    installed is Installed.Ours && !standing.outdated ->
                        FilledTonalButton(onClick = { Launch.open(context, mod.packageName) }) { Text(words.open) }
                    else -> Button(
                        enabled = state !is Engine.State.Working,
                        onClick = { Engine.start(context, mod, release); open() },
                    ) { Text(if (installed is Installed.Ours) words.update else words.install) }
                }
            }
        }
    }
}
