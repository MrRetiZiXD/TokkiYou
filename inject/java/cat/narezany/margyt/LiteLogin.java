package cat.narezany.margyt;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

/**
 * Signing in to TikTok Lite with Google, which the mod cannot offer: Google
 * answers only to the app as TikTok signed it. Rather than a button that
 * spins and gives up, the person is told, and told what works.
 */
public final class LiteLogin {

    private LiteLogin() {}

    /** The screen every sign-in with somebody else's account goes through. */
    private static final String SCREEN = "com.ss.android.ugc.aweme.mini_lobby.internal.LobbyInvisibleActivity";
    private static final String PROVIDER = "provider_id";

    /**
     * Called as a screen is being created, before it has read what it was
     * opened for. A sign-in with Google is renamed to one nobody provides,
     * which Lite answers the way it answers any provider it does not have:
     * it reports the sign-in failed and closes.
     */
    static void created(Activity screen) {
        try {
            if (!Version.LITE || screen == null || !SCREEN.equals(screen.getClass().getName())) return;
            Intent opened = screen.getIntent();
            if (opened == null) return;
            String provider = opened.getStringExtra(PROVIDER);
            android.util.Log.i("TokkiYou", "sign-in: asked for " + provider);
            // the one-tap offer comes up unasked, and is left to fail quietly
            if (!"google".equals(provider) && !"google_web".equals(provider)) return;
            if (opened.getIntExtra("action_type", 1) != 1) return;
            opened.putExtra(PROVIDER, "margyt_none");
            Toast.makeText(screen.getApplicationContext(), Text.LITE_GOOGLE, Toast.LENGTH_LONG).show();
        } catch (Throwable broken) {
            Diary.note("lite sign-in: " + broken);
        }
    }
}
