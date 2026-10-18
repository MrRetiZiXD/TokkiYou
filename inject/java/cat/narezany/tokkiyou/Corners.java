package cat.narezany.tokkiyou;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * The corners of a video's card in a grid: a little round rather than square.
 *
 * A profile is a grid of covers edge to edge, each one a hard rectangle. The
 * cell that holds a cover is a class with a real name, `FixedRatioFrameLayout`,
 * and every one that is made passes through here as it is built -- so the
 * rounding is there from the first frame, not painted on a moment after.
 *
 * Nothing is drawn. The cell is given an outline with round corners and told
 * to clip to it, which the system does on the graphics card. The same class
 * holds other things than covers, and those are left square: a cell is only
 * rounded when what it holds is a cover.
 */
public final class Corners {

    private Corners() {}

    public static final String KEY = "round_cards";

    /** How round, in dp. Enough to see, not enough to eat into the picture. */
    private static final float RADIUS = 8f;

    private static volatile Boolean cached;

    static void reloadSettings() {
        cached = null;
    }

    public static boolean isEnabled() {
        if (Version.LITE) return false;
        Boolean known = cached;
        if (known != null) return known;
        try {
            android.content.Context context = TokkiYou.context();
            if (context == null) return true;  // on until there is somewhere to read from
            boolean on = context.getSharedPreferences(TokkiYou.PREFS,
                    android.content.Context.MODE_PRIVATE).getBoolean(KEY, true);
            cached = on;
            return on;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        try {
            android.content.Context context = TokkiYou.context();
            if (context == null) return;
            context.getSharedPreferences(TokkiYou.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    /** The id TikTok gives the picture in a card, looked up by its name once. */
    private static int cover;

    private static final ViewOutlineProvider ROUND = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int wide = view.getWidth();
            int tall = view.getHeight();
            float radius = 0f;
            try {
                if (isEnabled() && holdsCover(view)) {
                    radius = RADIUS * view.getResources().getDisplayMetrics().density;
                }
            } catch (Throwable ignored) {
            }
            outline.setRoundRect(0, 0, wide, tall, radius);
        }
    };

    private static boolean holdsCover(View cell) {
        if (cover == 0) {
            cover = cell.getResources().getIdentifier("cover", "id",
                    cell.getContext().getPackageName());
            if (cover == 0) cover = -1;
        }
        return cover > 0 && cell.findViewById(cover) != null;
    }

    /** The end of the cell's constructor. */
    public static void cell(View cell) {
        try {
            // one constructor may hand over to another, and both end here
            if (cell == null || cell.getOutlineProvider() == ROUND) return;
            cell.setOutlineProvider(ROUND);
            cell.setClipToOutline(true);
            // what it holds is added after it is built, and the outline is
            // asked for again whenever its size changes -- but not when a
            // child arrives, so the first layout is where it is settled
            cell.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int l, int t, int r, int b,
                                           int ol, int ot, int or, int ob) {
                    view.invalidateOutline();
                }
            });
        } catch (Throwable error) {
            Diary.note("corners: " + error);
        }
    }
}
