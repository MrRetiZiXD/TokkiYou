package cat.narezany.margyt.plugin;

import android.app.Activity;
import android.content.Context;

/**
 * What a TokkiYou plugin extends.
 *
 * Everything here does nothing by default, so a plugin overrides the one or two
 * hooks it cares about and ignores the rest. New hooks can be added to this
 * class without breaking a plugin that was written before they existed, which
 * is the whole reason it is a class rather than an interface.
 *
 * A plugin's code is loaded into TikTok's own process, with this mod's classes
 * as its parent, so it can call anything in `cat.narezany.margyt` and anything
 * the app itself can reach. There is no sandbox and there cannot be one: read
 * what you install.
 *
 * Every hook is called with whatever thread the app happens to be on -- the
 * activity ones on the main thread, `onStart` before the application's own
 * onCreate. Anything slow belongs on a thread of the plugin's own. A hook that
 * throws is caught, written to the diary and never called again in that
 * process; it does not take TikTok down.
 *
 * See docs/plugins.md.
 */
public abstract class MargyPlugin {

    /**
     * The mod's API version, raised whenever a hook changes shape.
     *
     * A plugin declares the oldest one it works with in its manifest, as
     * `min_api`, and the loader refuses it rather than calling a hook that no
     * longer means what the plugin thought.
     */
    public static final int API = 5;

    private PluginContext margyt;

    /** Set by the loader before anything else is called. */
    public final void attach(PluginContext context) {
        this.margyt = context;
    }

    /** The mod, from the plugin's side: settings, the diary, the app context. */
    public final PluginContext margyt() {
        return margyt;
    }

    // ------------------------------------------------------------ the hooks

    /**
     * The process has started and the mod is up.
     *
     * This runs inside the mod's start-up provider, before TikTok's own
     * Application.onCreate: early enough to get in front of most things, and
     * early enough that most of the app does not exist yet.
     */
    public void onStart(Context context) {}

    /** An activity was created. */
    public void onActivityCreated(Activity activity) {}

    /** An activity came to the front. Called for every screen, so keep it cheap. */
    public void onActivityResumed(Activity activity) {}

    /** An activity went away. */
    public void onActivityPaused(Activity activity) {}
    /** Return true when the plugin opened its own settings window. */
    public boolean onOpenSettings(Activity activity) { return false; }
    /** UI-thread events; settings values are already persisted. */
    public void onAccountChanged(String uid) {}
    public void onSettingsChanged(String key) {}

    /**
     * A colour is on its way to the screen.
     *
     * Every colour the mod redirects -- the constants in the bytecode, whatever
     * comes back from the framework -- passes through here after the accent has
     * had its say. Return `colour` to leave it alone. This sits in the drawing
     * path of half the app: no allocation, no lookups, no logging.
     */
    public int onColour(int colour) {
        return colour;
    }

    /**
     * An answer about where the phone is, on its way back to TikTok.
     *
     * `key` is one of `sim_country`, `network_country`, `sim_operator`,
     * `network_operator`, `sim_operator_name`, `network_operator_name`.
     * `value` is what the mod was about to answer -- the phone's own answer
     * when the region switch is off, the chosen country's when it is on.
     * Return `value` to leave it alone.
     */
    public String onRegion(String key, String value) {
        return value;
    }

    /** The plugin was switched off, or the app is going down. */
    // --------------------------------------------- what the app is doing

    /**
     * A screen came up, by the name of the class that draws it.
     *
     * The names are TikTok's own and most of them are renamed every release --
     * which is why the name is handed over rather than an enum of screens the
     * mod would have to keep in step. What is stable is the activity itself:
     * `com.ss.android.ugc.aweme.main.MainActivity` is the feed, the inbox and
     * the profile, and a fragment inside it is what actually changed.
     */
    public void onScreen(Activity activity, String name) {}

    /**
     * A page of the feed, before anything has drawn it.
     *
     * The list is TikTok's own and what is returned is what the app will use:
     * return it as it came to leave it alone, or a list with things left out.
     * Returning null is the same as leaving it alone.
     */
    public java.util.List onFeed(java.util.List posts) {
        return posts;
    }

    /**
     * A name about to be written somewhere, with the account it belongs to.
     *
     * Whatever is returned is what gets drawn. The mod's own badges are added
     * after this, so a plugin cannot take one away by returning a bare name.
     */
    public String onName(String uid, String name) {
        return name;
    }

    /** Text on its way into a view. Return it, or something else. */
    public CharSequence onText(CharSequence text) {
        return text;
    }

    public void onStop() {}
}
