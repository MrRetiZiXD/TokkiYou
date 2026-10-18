package cat.narezany.patcher.data

import org.json.JSONObject

/**
 * Signing in with Telegram, which is how the project knows its supporters.
 *
 * The app never sees anybody's Telegram account. It asks the server for a
 * one-time code, opens the project's bot with it, and the person presses
 * Start there; the bot tells the server who pressed, and the server tells the
 * app a name, whether that person is in the supporters' group, and a token
 * to ask again with.
 */
object Telegram {
    private const val API = "https://tokkiyou.narez.xyz/api/patcher"

    class Begun(val code: String, val link: String)
    class Who(val token: String, val name: String, val supporter: Boolean)

    fun begin(): Begun? = try {
        val answer = JSONObject(Net.text("$API/login/start", post = "{}") ?: return null)
        Begun(answer.getString("code"), answer.getString("link"))
    } catch (broken: Exception) {
        null
    }

    /** Null while the person has not pressed Start yet. */
    fun check(code: String): Who? = try {
        val answer = JSONObject(Net.text("$API/login/check?code=$code") ?: return null)
        if (answer.optString("state") != "ok") null
        else Who(answer.getString("token"), answer.optString("name"), answer.optBoolean("supporter"))
    } catch (broken: Exception) {
        null
    }

    /** Whether the person is still a supporter; asked again now and then. */
    fun again(token: String): Who? = try {
        val answer = JSONObject(Net.text("$API/me", bearer = token) ?: return null)
        Who(token, answer.optString("name"), answer.optBoolean("supporter"))
    } catch (broken: Exception) {
        null
    }
}
