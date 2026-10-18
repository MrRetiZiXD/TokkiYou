package cat.narezany.patcher

import android.app.Application
import cat.narezany.patcher.work.News
import cat.narezany.patcher.work.Notify

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val words = Strings.of(this)
        Notify.channels(this, words.workChannel, words.newsChannel)
        News.schedule(this)
    }
}
