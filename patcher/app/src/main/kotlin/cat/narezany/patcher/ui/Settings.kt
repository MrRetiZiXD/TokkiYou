package cat.narezany.patcher.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.narezany.patcher.BuildConfig
import cat.narezany.patcher.Strings
import cat.narezany.patcher.data.Prefs
import cat.narezany.patcher.data.Telegram
import cat.narezany.patcher.work.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(back: () -> Unit) {
    val words = LocalWords.current
    val context = LocalContext.current
    val store = Prefs.of(context)
    val prefs by store.now.collectAsStateWithLifecycle()
    val loading by Shelf.loading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf("") }
    var kept by remember { mutableStateOf(0L) }
    var signing by remember { mutableStateOf("") }   // "", "waiting", "unavailable"
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { kept = withContext(Dispatchers.IO) { Engine.kept(context) } }
    // whether the person is still a supporter is the server's to say, each time
    LaunchedEffect(prefs.telegramToken) {
        if (prefs.telegramToken.isNotEmpty()) {
            val who = withContext(Dispatchers.IO) { Telegram.again(prefs.telegramToken) }
            if (who != null) store.set {
                it.copy(telegramName = who.name.ifEmpty { it.telegramName }, supporter = who.supporter,
                    beta = it.beta && who.supporter)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(words.settings) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = words.back)
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).navigationBarsPadding()) {
            Group(words.look) {
                row(Icons.Rounded.Translate, words.language,
                    Strings.LANGUAGES.firstOrNull { it.first == prefs.language }?.second ?: words.languageSystem,
                    pressed = { choosing = "language" })
                row(Icons.Rounded.DarkMode, words.theme, when (prefs.theme) {
                    "light" -> words.themeLight
                    "dark" -> words.themeDark
                    else -> words.themeSystem
                }, pressed = { choosing = "theme" })
                row(Icons.Rounded.Palette, words.dynamicColour, words.dynamicColourNote,
                    pressed = { store.set { it.copy(dynamic = !it.dynamic) } }) {
                    Switch(checked = prefs.dynamic, onCheckedChange = { on -> store.set { it.copy(dynamic = on) } })
                }
            }

            Group(words.updates) {
                row(Icons.Rounded.NotificationsActive, words.notifications, words.notificationsNote,
                    pressed = { store.set { it.copy(notify = !it.notify) } }) {
                    Switch(checked = prefs.notify, onCheckedChange = { on -> store.set { it.copy(notify = on) } })
                }
                row(Icons.Rounded.Refresh, words.checkNow, if (checked && !loading) words.upToDate else null,
                    pressed = { checked = true; Shelf.refresh(context) }) {
                    if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }

            Group(words.storage) {
                row(Icons.Rounded.Inventory2, words.keepOriginal, words.keepOriginalNote,
                    pressed = { store.set { it.copy(keepOriginal = !it.keepOriginal) } }) {
                    Switch(checked = prefs.keepOriginal,
                        onCheckedChange = { on -> store.set { it.copy(keepOriginal = on) } })
                }
                row(Icons.Rounded.DeleteSweep, words.clearStorage,
                    words.clearStorageNote(Formatter.formatShortFileSize(context, kept)),
                    pressed = {
                        scope.launch {
                            kept = withContext(Dispatchers.IO) { Engine.clear(context); Engine.kept(context) }
                        }
                    })
            }

            Group(words.account) {
                if (prefs.telegramToken.isEmpty()) {
                    row(Icons.AutoMirrored.Rounded.Send, words.telegramSignIn, when (signing) {
                        "waiting" -> words.telegramWaiting
                        "unavailable" -> words.telegramUnavailable
                        else -> words.telegramSignInNote
                    }, pressed = {
                        if (signing != "waiting") {
                            signing = "waiting"
                            scope.launch {
                                val begun = withContext(Dispatchers.IO) { Telegram.begin() }
                                if (begun == null) {
                                    signing = "unavailable"
                                    return@launch
                                }
                                Launch.link(context, begun.link)
                                // the answer comes when the person presses Start in the bot
                                repeat(90) {
                                    delay(2000)
                                    val who = withContext(Dispatchers.IO) { Telegram.check(begun.code) }
                                    if (who != null) {
                                        store.set {
                                            it.copy(telegramToken = who.token, telegramName = who.name,
                                                supporter = who.supporter)
                                        }
                                        signing = ""
                                        return@launch
                                    }
                                }
                                signing = ""
                            }
                        }
                    }) {
                        if (signing == "waiting") CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                } else {
                    row(Icons.AutoMirrored.Rounded.Send, words.telegramSignedIn(prefs.telegramName),
                        if (prefs.supporter) words.supporterYes else words.supporterNo)
                    if (prefs.supporter) {
                        row(Icons.Rounded.Science, words.betaUpdates, words.betaUpdatesNote,
                            pressed = { store.set { it.copy(beta = !it.beta) }; Shelf.refresh(context) }) {
                            Switch(checked = prefs.beta, onCheckedChange = { on ->
                                store.set { it.copy(beta = on) }
                                Shelf.refresh(context)
                            })
                        }
                    }
                    row(Icons.AutoMirrored.Rounded.Logout, words.telegramSignOut, words.signOutNote, pressed = {
                        store.set { it.copy(telegramToken = "", telegramName = "", supporter = false, beta = false) }
                        Shelf.refresh(context)
                    })
                }
            }

            Group(words.about) {
                row(Icons.Rounded.Info, words.version(BuildConfig.VERSION_NAME), "@margymods",
                    pressed = { Launch.link(context, "https://t.me/margymods") })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (choosing == "language") {
        Choice(words.language,
            listOf(Prefs.SYSTEM to words.languageSystem) + Strings.LANGUAGES,
            prefs.language, close = { choosing = "" }) { picked -> store.set { it.copy(language = picked) } }
    }
    if (choosing == "theme") {
        Choice(words.theme,
            listOf(Prefs.SYSTEM to words.themeSystem, "light" to words.themeLight, "dark" to words.themeDark),
            prefs.theme, close = { choosing = "" }) { picked -> store.set { it.copy(theme = picked) } }
    }
}

@Composable
private fun Choice(title: String, options: List<Pair<String, String>>, chosen: String,
                   close: () -> Unit, pick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = {
            Column {
                for ((value, label) in options) {
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = value == chosen, onClick = { pick(value); close() })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == chosen, onClick = { pick(value); close() })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(LocalWords.current.cancel) } },
    )
}
