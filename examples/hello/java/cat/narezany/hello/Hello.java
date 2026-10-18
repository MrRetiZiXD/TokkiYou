package cat.narezany.hello;

import android.content.Context;

import cat.narezany.margyt.plugin.MargyPlugin;

/**
 * The smallest plugin that does something you can see.
 *
 * It counts the starts of the app in settings of its own and writes the count
 * into the mod's diary, which is where a plugin's output goes: a TikTok
 * repacked from a release has no log anyone is watching.
 *
 * Build it with
 *
 *     python3 -m margyt.plugin examples/hello
 *
 * and install the .mtp through Settings and privacy -> MargyT -> Plugins.
 */
public final class Hello extends MargyPlugin {

    private static final String STARTS = "starts";

    @Override
    public void onStart(Context context) {
        int starts = margyt().prefs().getInt(STARTS, 0) + 1;
        margyt().prefs().edit().putInt(STARTS, starts).apply();
        if (margyt().getBoolean("log_starts", true)) {
            for (int i = 0; i < margyt().getInt("repeat", 1); i++)
                margyt().log(margyt().getInt("style", 0) == 0
                        ? "hello #" + starts
                        : "hello -- this is start number " + starts);
        }

        // Settings of the plugin's own (API 3): they appear in the mod's
        // settings under "Hello" while the plugin is on, and are read back
        // with getBoolean / getInt.
        margyt().addSettingsSection("Diary");
        margyt().addSettingsToggle("Log every start", "Writes a line on each launch",
                "log_starts", true);
        margyt().addSettingsChoice("Style", null, "style",
                new String[] {"Short", "Verbose"}, 0);
        margyt().addSettingsSlider("Repeat", "Lines per start", "repeat", 1, 5, 1);
    }

    /**
     * Every colour the mod redirects comes through here. This one hands each
     * one straight back, which is what a plugin that does not care about
     * colours should do -- and it is on the drawing path, so it does it
     * without allocating anything.
     */
    @Override
    public int onColour(int colour) {
        return colour;
    }
}
