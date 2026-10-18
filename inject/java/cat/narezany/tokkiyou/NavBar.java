package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * The bottom bar as one rounded capsule: icons in a row, and the tab you are
 * on opened out into a pill with its name beside the icon.
 *
 * TikTok's bar is not replaced. Its tabs stay where they are and go on doing
 * everything they do -- the double tap that refreshes the feed, the hold that
 * switches accounts, the hold the mod adds to the inbox. What changes is what
 * is seen: the bar draws this over itself, its own tabs are clipped down to
 * nothing, and the strip of colour it lay on is put away for as long as the
 * capsule is worn, so the capsule floats on whatever is behind the bar. And
 * because a tab that is open is wider than one that is not, each of
 * TikTok's tabs is moved and stretched, out of sight, to lie under the place
 * its icon is drawn, so a touch lands on the tab it looks like it lands on.
 *
 * Nothing here is known by a name of TikTok's. The bar is the row of equal,
 * clickable groups at the bottom of the screen with one of them selected; a
 * tab's name, its number and its dot are read off the views TikTok puts in
 * it, in whatever language they are in. The first tab is home, the last is
 * the profile, the one before it the inbox and the one in the middle with no
 * name is the button that makes a video. A tab that is none of those keeps
 * the icon TikTok gave it.
 */
final class NavBar {

    private NavBar() {}

    static final String KEY = "nav_capsule";
    /** Whether the bar stays in sight, dimmed like the rest, while the screen saver is on. */
    static final String KEY_KEEP = "nav_keep_dimmed";
    /** How long a touch where the bar was brings it back for. */
    private static final long PEEK = 5000L;

    static boolean isKept() {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(KEY_KEEP, false);
    }

    static void setKept(boolean kept) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_KEEP, kept).apply();
    }

    private static final int HOME = 0, FRIENDS = 1, CREATE = 2, INBOX = 3, PROFILE = 4, OTHER = 5;
    private static final String[] FRIEND_WORDS = {"друзья", "friends", "друзі"};

    // Google's Material Symbols, the rounded set, as they are published: a square of 960 with its top at -960.
    private static final String HOME_ON = "M160-200v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23"
            + ".5 56.5T720-120H600q-17 0-28.5-11.5T560-160v-200q0-17-11.5-28.5T520-400h-80q-17 0-28.5 11.5T400-360v"
            + "200q0 17-11.5 28.5T360-120H240q-33 0-56.5-23.5T160-200Z";
    private static final String HOME_OFF = "M240-200h120v-200q0-17 11.5-28.5T400-440h160q17 0 28.5 11.5T600-400v200h120v-360L480-740 240-560v360"
            + "Zm-80 0v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23."
            + "5 56.5T720-120H560q-17 0-28.5-11.5T520-160v-200h-80v200q0 17-11.5 28.5T400-120H240q-33 0-56.5-23.5T1"
            + "60-200Zm320-270Z";
    private static final String FRIENDS_ON = "M40-272q0-34 17.5-62.5T104-378q62-31 126-46.5T360-440q66 0 130 15.5T616-378q29 15 46.5 43.5T680-272v"
            + "32q0 33-23.5 56.5T600-160H120q-33 0-56.5-23.5T40-240v-32Zm698 112q11-18 16.5-38.5T760-240v-40q0-44-2"
            + "4.5-84.5T666-434q51 6 96 20.5t84 35.5q36 20 55 44.5t19 53.5v40q0 33-23.5 56.5T840-160H738ZM360-480q-"
            + "66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47Zm400-160q0 66-47 113t-"
            + "113 47q-11 0-28-2.5t-28-5.5q27-32 41.5-71t14.5-81q0-42-14.5-81T544-792q14-5 28-6.5t28-1.5q66 0 113 4"
            + "7t47 113Z";
    private static final String FRIENDS_OFF = "M40-272q0-34 17.5-62.5T104-378q62-31 126-46.5T360-440q66 0 130 15.5T616-378q29 15 46.5 43.5T680-272v"
            + "32q0 33-23.5 56.5T600-160H120q-33 0-56.5-23.5T40-240v-32Zm800 112H738q11-18 16.5-38.5T760-240v-40q0-"
            + "44-24.5-84.5T666-434q51 6 96 20.5t84 35.5q36 20 55 44.5t19 53.5v40q0 33-23.5 56.5T840-160ZM360-480q-"
            + "66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47Zm400-160q0 66-47 113t-"
            + "113 47q-11 0-28-2.5t-28-5.5q27-32 41.5-71t14.5-81q0-42-14.5-81T544-792q14-5 28-6.5t28-1.5q66 0 113 4"
            + "7t47 113ZM120-240h480v-32q0-11-5.5-20T580-306q-54-27-109-40.5T360-360q-56 0-111 13.5T140-306q-9 5-14"
            + ".5 14t-5.5 20v32Zm240-320q33 0 56.5-23.5T440-640q0-33-23.5-56.5T360-720q-33 0-56.5 23.5T280-640q0 33"
            + " 23.5 56.5T360-560Zm0 320Zm0-400Z";
    private static final String INBOX_ON = "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5"
            + " 56.5T800-160H160Zm320-287q5 0 10.5-1.5T501-453l283-177q8-5 12-12.5t4-16.5q0-20-17-30t-35 1L480-520 "
            + "212-688q-18-11-35-.5T160-659q0 10 4 17.5t12 11.5l283 177q5 3 10.5 4.5T480-447Z";
    private static final String INBOX_OFF = "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5"
            + " 56.5T800-160H160Zm640-480L501-453q-5 3-10.5 4.5T480-447q-5 0-10.5-1.5T459-453L160-640v400h640v-400Z"
            + "M480-520l320-200H160l320 200ZM160-640v10-59 1-32 32-.5 58.5-10 400-400Z";
    private static final String PROFILE_ON = "M480-480q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM160-240v-32"
            + "q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v32q0 33"
            + "-23.5 56.5T720-160H240q-33 0-56.5-23.5T160-240Z";
    private static final String PROFILE_OFF = "M480-480q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM160-240v-32"
            + "q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v32q0 33"
            + "-23.5 56.5T720-160H240q-33 0-56.5-23.5T160-240Zm80 0h480v-32q0-11-5.5-20T700-306q-54-27-109-40.5T480"
            + "-360q-56 0-111 13.5T260-306q-9 5-14.5 14t-5.5 20v32Zm240-320q33 0 56.5-23.5T560-640q0-33-23.5-56.5T4"
            + "80-720q-33 0-56.5 23.5T400-640q0 33 23.5 56.5T480-560Zm0-80Zm0 400Z";
    /** The icon of each kind of tab, open and not. */
    private static final String[][] OUTLINES = {
            {HOME_OFF, HOME_ON}, {FRIENDS_OFF, FRIENDS_ON}, null, {INBOX_OFF, INBOX_ON}, {PROFILE_OFF, PROFILE_ON}, null};
    private static final Path[][] DRAWN = new Path[OUTLINES.length][2];

    /** How long a tab takes to open, in milliseconds. */
    private static final long OPENING = 350L;
    /** How often the colour under the bar is looked at again. */
    private static final long LOOK_AGAIN = 400L;
    private static final int TRIES = 20;
    private static final long BETWEEN = 600L;

    // -------------------------------------------------------------- the switch

    static boolean isEnabled() {
        if (Version.LITE) return false;
        SharedPreferences prefs = prefs();
        return prefs == null || prefs.getBoolean(KEY, true);
    }

    static void setEnabled(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, on).apply();
    }

    private static SharedPreferences prefs() {
        Context context = TokkiYou.context();
        if (context == null) return null;
        try {
            return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Whether this is a bar the capsule is on, and so one whose background is not to be put back. */
    static boolean wears(View view) {
        Capsule capsule = BARS.get(view);
        return capsule != null && capsule.worn;
    }

    // ----------------------------------------------------------- finding the bar

    private static final WeakHashMap<View, Capsule> BARS = new WeakHashMap<View, Capsule>();
    private static WeakReference<ViewTreeObserver> watched = new WeakReference<ViewTreeObserver>(null);
    private static WeakReference<View> screen = new WeakReference<View>(null);
    private static long looked;

    private static final ViewTreeObserver.OnPreDrawListener EVERY_FRAME = () -> {
        try {
            boolean on = isEnabled(), any = false;
            for (java.util.Map.Entry<View, Capsule> entry : BARS.entrySet()) {
                View bar = entry.getKey();
                if (bar == null || !bar.isAttachedToWindow()) continue;
                any = true;
                if (on) entry.getValue().settle((ViewGroup) bar);
                else entry.getValue().leave((ViewGroup) bar);
            }
            // TikTok builds the bar again now and then without the screen changing
            View root = screen.get();
            long now = SystemClock.uptimeMillis();
            if (on && !any && root != null && now - looked > 2000L) {
                looked = now;
                look(root, TRIES);
            }
        } catch (Throwable ignored) {
            // a bar taken apart in the middle of a frame is looked at again on the next
        }
        return true;
    };

    /** A screen came up. The bar is put together after it, so it is looked for more than once. */
    static void attach(Activity activity) {
        try {
            if (isEnabled()) look(activity.getWindow().getDecorView(), 0);
        } catch (Throwable error) {
            Diary.detail("nav bar: " + error);
        }
    }

    private static void look(final View root, final int attempt) {
        try {
            ViewGroup bar = find(root, root, 0);
            if (bar != null) {
                if (!BARS.containsKey(bar)) {
                    BARS.put(bar, new Capsule(bar.getResources().getDisplayMetrics().density));
                    Diary.detail("nav bar: " + bar.getChildCount() + " tabs");
                }
                screen = new WeakReference<View>(root);
                ViewTreeObserver next = root.getViewTreeObserver(), old = watched.get();
                if (next != old && next.isAlive()) {
                    if (old != null && old.isAlive()) old.removeOnPreDrawListener(EVERY_FRAME);
                    next.addOnPreDrawListener(EVERY_FRAME);
                    watched = new WeakReference<ViewTreeObserver>(next);
                }
                bar.invalidate();
            } else if (attempt < TRIES) {
                // No asking whether the window is up: on a cold start it is not
                // yet when the screen says it has come, and a wait posted to a
                // view is kept until it is.
                root.postDelayed(() -> look(root, attempt + 1), BETWEEN);
            }
        } catch (Throwable error) {
            Diary.detail("nav bar: " + error);
        }
    }

    private static ViewGroup find(View view, View root, int depth) {
        if (!(view instanceof ViewGroup) || depth > 30 || view.getVisibility() != View.VISIBLE) return null;
        ViewGroup group = (ViewGroup) view;
        if (isBar(group, root)) return group;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = find(group.getChildAt(i), root, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    /** A row of equal clickable groups across the bottom of the screen, one of them selected. */
    private static boolean isBar(ViewGroup group, View root) {
        int wide = group.getWidth(), tall = group.getHeight();
        if (tall <= 0 || wide < root.getWidth() * 0.9f || tall > root.getHeight() * 0.15f) return false;
        int[] at = new int[2], origin = new int[2];
        group.getLocationOnScreen(at);
        root.getLocationOnScreen(origin);
        if (at[1] - origin[1] < root.getHeight() * 0.8f) return false;
        int tabs = 0, named = 0, selected = 0, width = -1;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (!(child instanceof ViewGroup) || !child.isClickable()) return false;
            if (width < 0) width = child.getWidth();
            else if (Math.abs(child.getWidth() - width) > 2) return false;
            tabs++;
            if (name((ViewGroup) child) != null) named++;
            if (child.isSelected()) selected++;
        }
        return tabs >= 3 && tabs <= 6 && named >= 2 && selected == 1 && width > 0;
    }

    // ------------------------------------------------------- reading a tab

    /** What a tab is called: the first text in it that is not a number. */
    private static String name(ViewGroup tab) {
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            CharSequence text = ((TextView) child).getText();
            if (text == null || text.length() == 0 || text.length() > 24) continue;
            if (TabCount.of(text) == 0) return text.toString();
        }
        return null;
    }

    private static TextView nameView(ViewGroup tab) {
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            CharSequence text = ((TextView) child).getText();
            if (text != null && text.length() > 0 && TabCount.of(text) == 0) return (TextView) child;
        }
        return null;
    }

    /** The number on a tab, while it is one that is being shown. */
    private static String count(ViewGroup tab) {
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (!(child instanceof TextView) || child.getVisibility() != View.VISIBLE) continue;
            if (child.getAlpha() < 0.05f || child.getWidth() == 0) continue;
            CharSequence text = ((TextView) child).getText();
            if (TabCount.of(text) > 0) return text.toString().trim();
        }
        return null;
    }

    /** The small dot TikTok puts on a tab for something new that has no number. */
    private static boolean dot(ViewGroup tab, float unit) {
        for (int i = 1; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (!(child instanceof ImageView) || child.getVisibility() != View.VISIBLE) continue;
            if (child.getAlpha() < 0.05f) continue;
            if (child.getWidth() > 0 && child.getWidth() <= 16 * unit && child.getHeight() <= 16 * unit) return true;
        }
        return false;
    }

    private static int kind(ViewGroup tab, int index, int count) {
        String name = name(tab);
        if (name == null && count % 2 == 1 && index == count / 2) return CREATE;
        if (index == 0) return HOME;
        if (index == count - 1) return PROFILE;
        if (index == count - 2 && count >= 4) return INBOX;
        if (name != null) {
            String said = name.trim().toLowerCase(Locale.ROOT);
            for (String word : FRIEND_WORDS) if (word.equals(said)) return FRIENDS;
        }
        return OTHER;
    }

    private static volatile Bitmap portrait;
    private static volatile String portraitOf;
    private static String asked;
    private static long askedAt;

    /**
     * The signed-in person's avatar, small. TikTok's own address for it, which
     * the mod remembers from the profile; fetched once, away from the main
     * thread, and again when the address changes. Null until it is here.
     */
    private static Bitmap face(final View bar) {
        final String address = Account.profileAvatar();
        if (address == null || !address.startsWith("https://")) return null;
        if (address.equals(portraitOf)) return portrait;
        long now = SystemClock.uptimeMillis();
        if (address.equals(asked) && now - askedAt < 60000L) return portrait;
        asked = address;
        askedAt = now;
        Net.away("nav avatar", () -> {
            try {
                byte[] raw = Net.bytes(address);
                if (raw == null) return;
                android.graphics.BitmapFactory.Options how = new android.graphics.BitmapFactory.Options();
                how.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length, how);
                how.inSampleSize = 1;
                while (Math.max(how.outWidth, how.outHeight) / how.inSampleSize > 256) how.inSampleSize *= 2;
                how.inJustDecodeBounds = false;
                Bitmap decoded = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length, how);
                if (decoded == null) return;
                portrait = decoded;
                portraitOf = address;
                bar.postInvalidate();
            } catch (Throwable error) {
                Diary.detail("nav bar: avatar: " + error);
            }
        });
        return portrait;
    }

    private static Path outline(int kind, boolean open) {
        if (OUTLINES[kind] == null) return null;
        Path known = DRAWN[kind][open ? 1 : 0];
        if (known != null) return known;
        final Path path = new Path();
        NavPlan.trace(OUTLINES[kind][open ? 1 : 0], new NavPlan.Pen() {
            @Override public void move(float x, float y) { path.moveTo(x, y); }
            @Override public void line(float x, float y) { path.lineTo(x, y); }
            @Override public void curve(float x1, float y1, float x2, float y2, float x, float y) {
                path.cubicTo(x1, y1, x2, y2, x, y);
            }
            @Override public void close() { path.close(); }
        });
        DRAWN[kind][open ? 1 : 0] = path;
        return path;
    }

    // ----------------------------------------------------------- one tab, drawn

    private static final class Tab {
        ViewGroup view;
        int kind;
        String name;
        float nameWide;
        String count;
        boolean dot;
        /** How far open it is, nought to one, and where that is heading. */
        float open, from, to;
        long since;
        /** How faint the screen saver has made it. */
        float shown = 1f;
        float left, right;
    }

    // ----------------------------------------------------------- the capsule

    private static final class Capsule extends Drawable {
        private final float unit;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF box = new RectF(), body = new RectF();
        private final android.graphics.Rect NOTHING = new android.graphics.Rect(-2, -2, -1, -1);
        private final PathInterpolator ease = new PathInterpolator(0.2f, 0f, 0f, 1f);
        private Tab[] tabs = new Tab[0];
        private boolean worn;
        private int ground = Color.BLACK, accent;
        private Drawable sampledFrom;
        private long sampledAt;
        /** Until when the bar is back in sight after a touch where it was. */
        private long peekUntil;
        private boolean listening;
        /** How far out of sight the screen saver has put the bar, nought to one, and where that is heading. */
        private float away, awayFrom, awayTo;
        private long awaySince;
        /** The background TikTok gave the bar, kept while the capsule is worn. */
        private Drawable own;
        private boolean ownKept;
        private Bitmap face;
        private android.graphics.BitmapShader faceShader;
        private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Matrix faceFit = new android.graphics.Matrix();
        private Bitmap pixel;
        private Canvas pixelCanvas;
        private int wide, tall;

        Capsule(float unit) {
            this.unit = unit;
            line.setStyle(Paint.Style.STROKE);
            words.setTextSize(13f * unit);
            small.setTextSize(10.5f * unit);
            small.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            small.setTextAlign(Paint.Align.CENTER);
        }

        /** Before every frame: what TikTok's bar says now, and whether that is what is drawn. */
        private long readAt;
        private boolean animates = true;
        private static final long READ_EVERY = 250L;

        void settle(ViewGroup bar) {
            long now = SystemClock.uptimeMillis();
            boolean changed = gather(bar);
            boolean moving = false;
            boolean read = changed || now - readAt >= READ_EVERY;
            if (read) {
                readAt = now;
                animates = Motion.enabled(bar.getContext());
            }
            boolean animated = animates;
            for (Tab tab : tabs) {
                float target = tab.view.isSelected() ? 1f : 0f;
                if (target != tab.to) {
                    tab.from = tab.open;
                    tab.to = target;
                    tab.since = now;
                }
                if (tab.open != tab.to) {
                    float passed = animated && worn ? (now - tab.since) / (float) OPENING : 1f;
                    tab.open = passed >= 1f ? tab.to
                            : tab.from + (tab.to - tab.from) * ease.getInterpolation(Math.max(0f, passed));
                    changed = true;
                    if (tab.open != tab.to) moving = true;
                }
                // What a tab says is read four times a second rather than on
                // every frame: each reading walks the tab and makes a string or
                // two, and at 120 frames a second that was thousands of them a
                // second for words that change a few times a day.
                if (!read) {
                    float shown = Math.max(0f, Math.min(1f, tab.view.getAlpha()));
                    if (Math.abs(shown - tab.shown) > 0.004f) {
                        tab.shown = shown;
                        changed = true;
                    }
                    continue;
                }
                String name = name(tab.view);
                if (name == null && tab.kind != CREATE) {
                    CharSequence said = tab.view.getContentDescription();
                    if (said != null && said.length() > 0 && said.length() <= 24) name = said.toString();
                }
                if (name == null ? tab.name != null : !name.equals(tab.name)) {
                    tab.name = name;
                    TextView from = nameView(tab.view);
                    words.setTypeface(from != null && from.getTypeface() != null ? from.getTypeface()
                            : Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    tab.nameWide = name == null ? 0f : words.measureText(name);
                    changed = true;
                }
                String count = count(tab.view);
                if (count == null ? tab.count != null : !count.equals(tab.count)) {
                    tab.count = count;
                    changed = true;
                }
                boolean dot = count == null && dot(tab.view, unit);
                if (dot != tab.dot) {
                    tab.dot = dot;
                    changed = true;
                }
                float shown = Math.max(0f, Math.min(1f, tab.view.getAlpha()));
                if (Math.abs(shown - tab.shown) > 0.004f) {
                    tab.shown = shown;
                    changed = true;
                }
            }
            // With the screen saver's eye shut the bar is not dimmed like the
            // rest: it goes altogether, and cannot be touched where it was.
            boolean dimmed = false;
            for (Tab tab : tabs) if (tab.shown < 0.99f) dimmed = true;
            // ...unless it is kept, or the place it was has just been touched
            float out = dimmed && Dim.isEnabled() && !isKept() && now >= peekUntil ? 1f : 0f;
            if (!listening) {
                listening = true;
                bar.setOnTouchListener((touched, event) -> {
                    // its tabs are off the screen while it is away, so a touch
                    // where it was comes here: it brings the bar back for a while
                    if (awayTo < 1f || event.getActionMasked() != android.view.MotionEvent.ACTION_DOWN) return false;
                    peekUntil = SystemClock.uptimeMillis() + PEEK;
                    touched.invalidate();
                    touched.postDelayed(touched::invalidate, PEEK + 50L);
                    return true;
                });
            }
            if (out != awayTo) {
                awayFrom = away;
                awayTo = out;
                awaySince = now;
            }
            if (away != awayTo) {
                float passed = animated && worn ? (now - awaySince) / 200f : 1f;
                away = passed >= 1f ? awayTo : awayFrom + (awayTo - awayFrom) * Math.max(0f, passed);
                changed = true;
                if (away != awayTo) moving = true;
            }
            // whatever TikTok or the theme has painted behind the tabs since the last frame is put away
            Drawable painted = bar.getBackground();
            if (painted != null) {
                own = painted;
                ownKept = true;
                bar.setBackground(null);
                changed = true;
            }
            Bitmap picture = face(bar);
            if (picture != face) {
                face = picture;
                faceShader = picture == null ? null : new android.graphics.BitmapShader(picture,
                        android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                changed = true;
            }
            int under = under(bar, now), tint = Accent.colour();
            if (under != ground || tint != accent) {
                ground = under;
                accent = tint;
                changed = true;
            }
            if (bar.getWidth() != wide || bar.getHeight() != tall) {
                wide = bar.getWidth();
                tall = bar.getHeight();
                setBounds(0, 0, wide, tall);
                changed = true;
            }
            if (!worn) {
                bar.getOverlay().add(this);
                worn = true;
                changed = true;
            }
            if (changed) arrange(bar);
            place();
            if (changed || moving) bar.invalidate();
        }

        /** The mod's bar was switched off: TikTok's is given back as it was. */
        void leave(ViewGroup bar) {
            if (!worn) return;
            worn = false;
            bar.getOverlay().remove(this);
            for (Tab tab : tabs) release(tab.view);
            tabs = new Tab[0];
            if (listening) bar.setOnTouchListener(null);
            listening = false;
            if (ownKept && bar.getBackground() == null) bar.setBackground(own);
            own = null;
            ownKept = false;
            sampledFrom = null;
            bar.invalidate();
        }

        private void release(View tab) {
            tab.setTranslationX(0f);
            tab.setScaleX(1f);
            tab.setClipBounds(null);
        }

        /** The tabs TikTok has in the bar at the moment. True when they are not the ones known. */
        private boolean gather(ViewGroup bar) {
            int count = 0;
            boolean same = true;
            for (int i = 0; i < bar.getChildCount(); i++) {
                View child = bar.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || !(child instanceof ViewGroup)) continue;
                if (count >= tabs.length || tabs[count].view != child) same = false;
                count++;
            }
            if (same && count == tabs.length) return false;
            for (Tab old : tabs) release(old.view);
            Tab[] next = new Tab[count];
            int at = 0;
            for (int i = 0; i < bar.getChildCount(); i++) {
                View child = bar.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || !(child instanceof ViewGroup)) continue;
                Tab tab = new Tab();
                tab.view = (ViewGroup) child;
                tab.kind = kind(tab.view, at, count);
                tab.open = tab.from = tab.to = child.isSelected() ? 1f : 0f;
                next[at++] = tab;
            }
            tabs = next;
            return true;
        }

        /**
         * The colour the capsule's own colours are mixed from: whatever is
         * painted under the bar on the page now showing, so that a light
         * page has a light capsule and a dark one a dark, and the black strip
         * TikTok keeps under a video has a dark one whatever the theme.
         *
         * It used to be the colour TikTok gave the bar, and then the colour
         * of the bar's own ancestors; but the bar is not part of the page it
         * is seen over, and both were dark on a light profile.
         */
        private int under(View bar, long now) {
            if (now - sampledAt < LOOK_AGAIN) return ground;
            sampledAt = now;
            return Md3.under(bar) | 0xFF000000;
        }

        /** Where everything goes along the bar, for how far open each tab is now. */
        private void arrange(ViewGroup bar) {
            if (tabs.length == 0) return;
            float top = tabs[0].view.getTop(), bottom = tabs[0].view.getBottom();
            if (bottom <= top) {
                top = 0f;
                bottom = tall;
            }
            float room = Math.min(bottom - top - 6f * unit, 52f * unit);
            float middle = (top + bottom) / 2f;
            body.set(8f * unit, middle - room / 2f, wide - 8f * unit, middle + room / 2f);
            float inside = 4f * unit, left = body.left + inside, right = body.right - inside;
            int centre = tabs.length / 2;
            if (tabs.length % 2 == 1 && tabs[centre].kind == CREATE) {
                // The button in the middle stays in the middle. A name that
                // opens takes its room from the tabs on its own side only.
                float half = Math.min(32f * unit, (right - left) / tabs.length / 2f);
                tabs[centre].left = body.centerX() - half;
                tabs[centre].right = body.centerX() + half;
                share(0, centre, left, tabs[centre].left);
                share(centre + 1, tabs.length, tabs[centre].right, right);
            } else {
                share(0, tabs.length, left, right);
            }
        }

        /** The tabs from one to another, side by side between two places on the bar. */
        private void share(int from, int to, float left, float right) {
            float[] open = new float[to - from], extra = new float[to - from];
            for (int i = from; i < to; i++) {
                open[i - from] = tabs[i].open;
                extra[i - from] = tabs[i].name == null || tabs[i].kind == CREATE ? 0f
                        : tabs[i].nameWide + 8f * unit + badge(tabs[i]);
            }
            float[] edges = NavPlan.edges(right - left, open, extra, 36f * unit);
            for (int i = from; i < to; i++) {
                tabs[i].left = left + edges[i - from];
                tabs[i].right = left + edges[i - from + 1];
            }
        }

        /** Each of TikTok's tabs goes under the place its icon is drawn, so that is where it is touched. */
        private void place() {
            for (int i = 0; i < tabs.length; i++) {
                Tab tab = tabs[i];
                View view = tab.view;
                if (view.getWidth() <= 0) continue;
                // the margins of the bar belong to the tabs at its ends
                float left = i == 0 ? 0f : tab.left, right = i == tabs.length - 1 ? wide : tab.right;
                float shift = (left + right) / 2f - (view.getLeft() + view.getWidth() / 2f);
                // a bar that is out of sight has its tabs off the side of the screen, where no finger is
                if (awayTo >= 1f) shift += wide * 3f;
                float stretch = (right - left) / view.getWidth();
                if (Math.abs(view.getPivotX() - view.getWidth() / 2f) > 0.5f) view.setPivotX(view.getWidth() / 2f);
                if (Math.abs(view.getTranslationX() - shift) > 0.5f) view.setTranslationX(shift);
                if (Math.abs(view.getScaleX() - stretch) > 0.002f) view.setScaleX(stretch);
                // still there and still touched, and not drawn
                if (view.getClipBounds() == null) view.setClipBounds(NOTHING);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (tabs.length == 0 || body.isEmpty() || away >= 1f) return;
            int veil = away > 0f ? canvas.saveLayerAlpha(body.left - unit, body.top - 8f * unit, body.right + unit,
                    body.bottom + unit, Math.round(255f * (1f - away))) : -1;
            int ink = Md3.ink(ground);
            int shell = mix(ground, ink, 0.07f), edge = mix(ground, ink, 0.15f);
            int pill = mix(shell, accent | 0xFF000000, 0.30f);
            int lit = mix(accent | 0xFF000000, ink, 0.40f);
            int quiet = mix(shell, ink, 0.80f);

            float round = body.height() / 2f;
            fill.setColor(shell);
            canvas.drawRoundRect(body, round, round, fill);
            line.setStrokeWidth(unit);
            line.setColor(edge);
            box.set(body);
            box.inset(unit / 2f, unit / 2f);
            canvas.drawRoundRect(box, round, round, line);

            float middle = body.centerY(), icon = 24f * unit, gap = 6f * unit;
            for (Tab tab : tabs) {
                int saved = -1;
                if (tab.shown < 0.99f) {
                    saved = canvas.saveLayerAlpha(tab.left, body.top, tab.right, body.bottom,
                            Math.round(tab.shown * 255f));
                }
                float centre = (tab.left + tab.right) / 2f;
                if (tab.kind == CREATE) {
                    create(canvas, centre, middle, ink, lit);
                } else {
                    if (tab.open > 0.01f) {
                        float half = (tab.right - tab.left) / 2f * (0.6f + 0.4f * tab.open);
                        box.set(centre - half, body.top + 4f * unit, centre + half, body.bottom - 4f * unit);
                        fill.setColor(pill);
                        fill.setAlpha(Math.round(255f * Math.min(1f, tab.open * 1.5f)));
                        canvas.drawRoundRect(box, box.height() / 2f, box.height() / 2f, fill);
                    }
                    // a name too long for its tab is set a little smaller before it is cut short
                    float badge = badge(tab);
                    float room = Math.max(0f, tab.right - tab.left - 2f * 8f * unit - icon - gap - badge);
                    float shrink = tab.name == null || tab.nameWide <= room ? 1f : Math.max(0.82f, room / tab.nameWide);
                    float text = tab.name == null ? 0f : Math.min(tab.nameWide * shrink, room);
                    float content = icon + tab.open * (gap + text + badge);
                    float start = centre - content / 2f;
                    int colour = mix(quiet, lit, tab.open);
                    emblem(canvas, tab, start, middle - icon / 2f, icon, colour);
                    if (tab.name != null && tab.open > 0.05f && text > 0f) {
                        int clip = canvas.save();
                        canvas.clipRect(start + icon, body.top, Math.min(start + icon + gap + text, tab.right), body.bottom);
                        words.setTextSize(13f * unit * shrink);
                        words.setColor(colour);
                        words.setAlpha(Math.round(255f * tab.open));
                        Paint.FontMetrics metrics = words.getFontMetrics();
                        canvas.drawText(tab.name, start + icon + gap,
                                middle - (metrics.ascent + metrics.descent) / 2f, words);
                        words.setTextSize(13f * unit);
                        canvas.restoreToCount(clip);
                    }
                    if (tab.count != null) {
                        // on the corner of the icon while the tab is shut, and after its name once
                        // it is open, where it is not over the first letters of it
                        float high = 16f * unit;
                        float shutLeft = start + icon - 9f * unit, shutUp = Math.max(body.top + unit, middle - icon / 2f - 5f * unit);
                        float openLeft = start + icon + gap + text + 4f * unit, openUp = middle - high / 2f;
                        number(canvas, tab.count, shutLeft + (openLeft - shutLeft) * tab.open,
                                shutUp + (openUp - shutUp) * tab.open, mix(shell, pill, tab.open));
                    }
                    else if (tab.dot) spot(canvas, start + icon - 2f * unit, middle - icon / 2f + 3f * unit, shell);
                }
                if (saved >= 0) canvas.restoreToCount(saved);
            }
            if (veil >= 0) canvas.restoreToCount(veil);
        }

        /** The icon of a tab: one of the mod's, or TikTok's own for a tab the mod has none for. */
        private void emblem(Canvas canvas, Tab tab, float left, float top, float size, int colour) {
            Path path = outline(tab.kind, tab.open > 0.5f);
            int saved = canvas.save();
            if (tab.kind == PROFILE && faceShader != null) {
                // the person whose profile it is, in the shape their avatars are in
                float half = size * 0.56f;
                Path shape = AvatarShape.chosenUnit();
                canvas.translate(left + size / 2f, top + size / 2f);
                if (tab.open > 0.01f) {
                    canvas.save();
                    canvas.scale(half + 2f * unit * tab.open, half + 2f * unit * tab.open);
                    fill.setColor(colour);
                    canvas.drawPath(shape, fill);
                    canvas.restore();
                }
                canvas.scale(half, half);
                float side = Math.min(face.getWidth(), face.getHeight());
                faceFit.setTranslate(-face.getWidth() / 2f, -face.getHeight() / 2f);
                faceFit.postScale(2f / side, 2f / side);
                faceShader.setLocalMatrix(faceFit);
                facePaint.setShader(faceShader);
                canvas.drawPath(shape, facePaint);
            } else if (path != null) {
                canvas.translate(left, top);
                canvas.scale(size / 960f, size / 960f);
                canvas.translate(0f, 960f);
                fill.setColor(colour);
                canvas.drawPath(path, fill);
            } else if (tab.view.getChildCount() > 0) {
                View own = tab.view.getChildAt(0);
                if (own.getWidth() > 0 && own.getHeight() > 0) {
                    float scale = size * 1.25f / own.getHeight();
                    canvas.translate(left + size / 2f - own.getWidth() * scale / 2f,
                            top + size / 2f - own.getHeight() * scale / 2f);
                    canvas.scale(scale, scale);
                    try {
                        own.draw(canvas);
                    } catch (Throwable ignored) {
                        // a view that will not draw anywhere but its own place leaves a gap, not a crash
                    }
                }
            }
            canvas.restoreToCount(saved);
        }

        /** The button that makes a video: TikTok's two-coloured lozenge, in the colours of the theme. */
        private void create(Canvas canvas, float centre, float middle, int ink, int lit) {
            float half = 15f * unit, round = 11f * unit;
            fill.setColor(lit);
            box.set(centre - 24f * unit, middle - half, centre + 24f * unit, middle + half);
            canvas.drawRoundRect(box, round, round, fill);
            fill.setColor(ink);
            box.set(centre - 20f * unit, middle - half, centre + 20f * unit, middle + half);
            canvas.drawRoundRect(box, round, round, fill);
            line.setStrokeWidth(2.4f * unit);
            line.setColor(bright(ink) < 0.5f ? 0xFFFFFFFF : 0xFF161823);
            line.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(centre - 6.5f * unit, middle, centre + 6.5f * unit, middle, line);
            canvas.drawLine(centre, middle - 6.5f * unit, centre, middle + 6.5f * unit, line);
            line.setStrokeCap(Paint.Cap.BUTT);
        }

        /** The number of unread things, on the corner of an icon. */
        /** The room a tab's number takes beside its name: nothing for a tab with no number. */
        private float badge(Tab tab) {
            return tab.count == null ? 0f : Math.max(16f * unit, small.measureText(tab.count) + 8f * unit) + 4f * unit;
        }

        private void number(Canvas canvas, String count, float left, float up, int around) {
            float high = 16f * unit, across = Math.max(high, small.measureText(count) + 8f * unit);
            box.set(left - 1.5f * unit, up - 1.5f * unit, left + across + 1.5f * unit, up + high + 1.5f * unit);
            fill.setColor(around);
            canvas.drawRoundRect(box, box.height() / 2f, box.height() / 2f, fill);
            box.set(left, up, left + across, up + high);
            fill.setColor(accent | 0xFF000000);
            canvas.drawRoundRect(box, high / 2f, high / 2f, fill);
            small.setColor(bright(accent) < 0.6f ? 0xFFFFFFFF : 0xFF161823);
            Paint.FontMetrics metrics = small.getFontMetrics();
            canvas.drawText(count, box.centerX(), box.centerY() - (metrics.ascent + metrics.descent) / 2f, small);
        }

        private void spot(Canvas canvas, float x, float y, int around) {
            fill.setColor(around);
            canvas.drawCircle(x, y, 5.5f * unit, fill);
            fill.setColor(accent | 0xFF000000);
            canvas.drawCircle(x, y, 4f * unit, fill);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static float bright(int colour) {
        return (0.299f * Color.red(colour) + 0.587f * Color.green(colour) + 0.114f * Color.blue(colour)) / 255f;
    }

    static int mix(int from, int to, float how) {
        if (how <= 0f) return from;
        if (how >= 1f) return to;
        return Color.argb(255,
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * how),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * how),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * how));
    }
}
