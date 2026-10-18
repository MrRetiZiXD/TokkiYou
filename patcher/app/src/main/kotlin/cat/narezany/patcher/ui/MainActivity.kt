package cat.narezany.patcher.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.narezany.patcher.Strings
import cat.narezany.patcher.data.Prefs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs.of(this)
        setContent {
            val now by prefs.now.collectAsStateWithLifecycle()
            PatcherTheme(now) {
                CompositionLocalProvider(LocalWords provides Strings.of(now.language)) {
                    // "" is the list, "settings" the settings, anything else a mod's id
                    var screen by rememberSaveable { mutableStateOf("") }
                    val ask = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()) { }
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    BackHandler(screen.isNotEmpty()) { screen = "" }
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            if (targetState.isNotEmpty()) {
                                (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut()
                            } else {
                                fadeIn() togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
                            }
                        },
                        label = "screen",
                    ) { shown ->
                        when (shown) {
                            "" -> HomeScreen(open = { screen = it }, settings = { screen = "settings" })
                            "settings" -> SettingsScreen(back = { screen = "" })
                            else -> ModScreen(id = shown, back = { screen = "" })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Shelf.refresh(this)
        // coming back from the installer or from removing an app: look again
        Shelf.looked.value += 1
    }
}
