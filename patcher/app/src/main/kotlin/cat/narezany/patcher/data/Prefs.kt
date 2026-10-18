package cat.narezany.patcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the person has chosen, kept between runs and watched by the screens. */
class Prefs private constructor(private val store: SharedPreferences) {

    data class Now(
        val language: String = SYSTEM,
        val theme: String = SYSTEM,
        val dynamic: Boolean = true,
        val notify: Boolean = true,
        val keepOriginal: Boolean = true,
        val beta: Boolean = false,
        val telegramToken: String = "",
        val telegramName: String = "",
        val supporter: Boolean = false,
    )

    private val flow = MutableStateFlow(read())
    val now: StateFlow<Now> get() = flow

    private fun read() = Now(
        language = store.getString("language", SYSTEM) ?: SYSTEM,
        theme = store.getString("theme", SYSTEM) ?: SYSTEM,
        dynamic = store.getBoolean("dynamic", true),
        notify = store.getBoolean("notify", true),
        keepOriginal = store.getBoolean("keep_original", true),
        beta = store.getBoolean("beta", false),
        telegramToken = store.getString("telegram_token", "") ?: "",
        telegramName = store.getString("telegram_name", "") ?: "",
        supporter = store.getBoolean("supporter", false),
    )

    fun set(change: (Now) -> Now) {
        val next = change(flow.value)
        store.edit()
            .putString("language", next.language)
            .putString("theme", next.theme)
            .putBoolean("dynamic", next.dynamic)
            .putBoolean("notify", next.notify)
            .putBoolean("keep_original", next.keepOriginal)
            .putBoolean("beta", next.beta)
            .putString("telegram_token", next.telegramToken)
            .putString("telegram_name", next.telegramName)
            .putBoolean("supporter", next.supporter)
            .apply()
        flow.value = next
    }

    /** Which versions a notification has already been shown for, so each is shown once. */
    fun told(key: String): Boolean = store.getBoolean("told_$key", false)
    fun tell(key: String) = store.edit().putBoolean("told_$key", true).apply()

    companion object {
        const val SYSTEM = "system"

        @Volatile private var one: Prefs? = null

        fun of(context: Context): Prefs = one ?: synchronized(this) {
            one ?: Prefs(context.applicationContext.getSharedPreferences("patcher", Context.MODE_PRIVATE))
                .also { one = it }
        }
    }
}
