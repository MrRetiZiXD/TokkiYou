package cat.narezany.patcher.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import cat.narezany.patcher.R
import cat.narezany.patcher.ui.MainActivity

/** The two kinds of notification the app shows: work going on, and news. */
object Notify {
    const val WORK = "work"
    const val NEWS = "news"
    const val WORKING = 1

    fun channels(context: Context, working: String, news: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WORK, working, NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(
            NotificationChannel(NEWS, news, NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun working(context: Context, title: String, done: Long, of: Long): Notification {
        val builder = Notification.Builder(context, WORK)
            .setSmallIcon(R.drawable.ic_patch)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(context))
        if (of > 0) builder.setProgress(1000, (done * 1000 / of).toInt().coerceIn(0, 1000), false)
        else builder.setProgress(0, 0, true)
        return builder.build()
    }

    fun news(context: Context, id: Int, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(id, Notification.Builder(context, NEWS)
            .setSmallIcon(R.drawable.ic_patch)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open(context))
            .build())
    }
}
