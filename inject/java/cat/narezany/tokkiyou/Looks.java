package cat.narezany.tokkiyou;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The gradient on a supporter's name.
 *
 * It comes from the server with the badges, and is only ever shown for an
 * account that still holds the supporter badge: the server drops it from the
 * list the moment the badge goes.
 *
 * A name carries its gradient the same way it carries a badge, as one
 * invisible character. The view that draws a name never sees whose it is, so
 * the character is the answer: it stands for a place in this run's table.
 * Names are stripped of private-use characters before the mod adds its own,
 * so pasting somebody else's into your name does nothing.
 */
public final class Looks {

    /** As many gradients as can be on screen at once, by a wide margin. */
    static final int MOST = 64;

    private Looks() {}

    private static volatile Map<String, int[]> gradients = new HashMap<String, int[]>();

    /** index in this run's table -> uid */
    private static final String[] slots = new String[MOST];
    private static int taken;

    // ------------------------------------------------------------- the data

    static void learn(JSONObject root) {
        try {
            Map<String, int[]> painted = new HashMap<String, int[]>();
            JSONObject said = root.optJSONObject("gradients");
            if (said != null) {
                java.util.Iterator<String> keys = said.keys();
                while (keys.hasNext()) {
                    String uid = keys.next();
                    JSONArray list = said.optJSONArray(uid);
                    if (list == null || list.length() < 2) continue;
                    int[] colours = new int[list.length()];
                    boolean fine = true;
                    for (int i = 0; i < colours.length; i++) {
                        colours[i] = colour(list.optString(i, ""));
                        if (colours[i] == 0) fine = false;
                    }
                    if (fine) painted.put(uid, colours);
                }
            }
            gradients = painted;
        } catch (Throwable error) {
            Diary.note("looks: " + error);
        }
    }

    private static int colour(String hex) {
        try {
            if (hex == null || hex.length() != 6) return 0;
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static int[] of(String uid) {
        return uid == null ? null : gradients.get(uid);
    }

    // ------------------------------------------------------ the mark a name carries

    private static char first() {
        return Badges.looksFirst();
    }

    /** The character that stands for this account's gradient, if it has one. */
    public static synchronized String mark(String uid) {
        if (uid == null || !gradients.containsKey(uid)) return "";
        for (int i = 0; i < taken; i++) {
            if (uid.equals(slots[i])) return String.valueOf((char) (first() + i));
        }
        if (taken == MOST) return "";
        slots[taken] = uid;
        return String.valueOf((char) (first() + taken++));
    }

    public static boolean isMark(char c) {
        return c >= first() && c < first() + MOST;
    }

    public static int[] colours(char c) {
        int at = c - first();
        if (at < 0 || at >= taken) return null;
        int[] found = gradients.get(slots[at]);
        return found != null && found.length >= 2 ? found : null;
    }

    // ------------------------------------------------------------ the drawing

    /** A tag of its own, so nothing here is mistaken for another part's. */
    private static final int PAINTED = 0x4D61726C;

    public static void paint(TextView view, int[] colours) {
        if (view == null || colours == null || colours.length < 2) return;
        try {
            float width = view.getPaint().measureText(view.getText().toString());
            if (width <= 0) width = view.getWidth();
            if (width <= 0) return;
            view.getPaint().setShader(new LinearGradient(
                    0, 0, width, 0, colours, null, Shader.TileMode.CLAMP));
            view.setTag(PAINTED, Boolean.TRUE);
            view.invalidate();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Take a gradient off a view the mod painted earlier.
     *
     * Lists hand the same view to the next person down, so a gradient left on
     * one would follow somebody who never had it. Only views this painted are
     * touched, so TikTok's own shaders are left alone.
     */
    public static void clear(TextView view) {
        if (view == null) return;
        try {
            if (view.getTag(PAINTED) == null) return;
            view.getPaint().setShader(null);
            view.setTag(PAINTED, null);
        } catch (Throwable ignored) {
        }
    }
}
