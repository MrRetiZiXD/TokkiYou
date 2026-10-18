package cat.narezany.patcher.work

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import cat.narezany.patcher.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while a mod is being made, and shows how far it has got.
 * Without it the work stops the moment the person looks at another app.
 */
class WorkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val words = Strings.of(this)
        val first = Notify.working(this, words.working, 0, 0)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notify.WORKING, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notify.WORKING, first)
        }
        scope.launch {
            Engine.state.collect { state ->
                if (state !is Engine.State.Working) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        scope.launch {
            Engine.state.sample(700).collect { state ->
                if (state is Engine.State.Working) {
                    val manager = getSystemService(android.app.NotificationManager::class.java)
                    manager.notify(Notify.WORKING, Notify.working(
                        this@WorkService, "${state.mod.name}: ${words.stage(state.stage)}", state.done, state.of))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, WorkService::class.java))
            } catch (refused: Exception) {
                // started from the background on a phone that forbids it: the
                // work goes on all the same for as long as the app is looked at
            }
        }
    }
}
