package cat.narezany.tokkiyou;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * A theme of your own, over the one TikTok is wearing.
 *
 * The rule this follows is the one the app already follows: a colour is
 * repainted here only where TikTok would repaint it when you switch between
 * light and dark. `tokkiyou/nightly.py` works out what that set is by reading
 * the style table -- a theme colour is an attribute that two styles give two
 * different values -- and the build writes it into `Nightly`. Anything not on
 * that list is left exactly as it was: the brand pink, a photo, the white of
 * an icon over a video.
 *
 * What a colour becomes keeps its place rather than its value. A colour is
 * measured by how far it sits from its own theme's background -- black is 0 in
 * the dark theme, white is 1, and the greys of cards and dividers sit between
 * -- and it is put the same distance from the chosen background towards the
 * chosen text. A card a shade above black stays a shade above whatever the
 * background becomes, and a divider stays as faint as it was.
 *
 * Off unless switched on, and the accent is not part of it: a colour the
 * accent has already claimed never reaches here.
 */
public final class Themes {

    private Themes() {}

    public static final String KEY_ON = "theme_on";
    public static final String KEY_MATERIAL = "theme_material";
    public static final String KEY_TEXT = "theme_text";
    public static final String KEY_BACKGROUND = "theme_background";
    public static final String KEY_STRENGTH = "theme_strength";

    /** What the mod falls back to: TikTok's own dark, near enough. */
    private static final int TEXT = 0xFFFFFFFF;
    private static final int BACKGROUND = 0xFF121212;

    // ---------------------------------------------------------- the switches

    public static boolean isEnabled() {
        return current().on;
    }

    public static void setEnabled(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_ON, on).apply();
        forget();
    }

    public static boolean isMaterial() {
        return current().material;
    }

    public static void setMaterial(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_MATERIAL, on).apply();
        forget();
    }

    public static int text() {
        return current().text;
    }

    public static int background() {
        return current().background;
    }

    /**
     * How much of the chosen background colour to actually use, 0 to 100.
     *
     * A background is mostly not a colour, it is a darkness -- and a screen
     * painted in full green is a toy rather than a theme. So the colour picked
     * is one end of a line whose other end is the theme's own extreme, black
     * in the dark theme and white in the light one, and this says how far
     * along that line to sit. At 10 it is a black that is faintly green; at
     * 100 it is green.
     */
    public static int strength() {
        return current().strength;
    }

    public static void setStrength(int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        put(KEY_STRENGTH, percent);
    }

    /** The chosen colour as it will actually be used. */
    public static int backgroundInUse() {
        return current().ground;
    }

    /**
     * Whether this phone has a palette of its own to paint with.
     *
     * Android 12 is where the wallpaper's colours became something an app
     * can read. Below it there is nothing to take.
     */
    public static boolean available() {
        return fromSystem(true) != 0 && fromSystem(false) != 0;
    }

    /**
     * The chosen colour at the depth asked for.
     *
     * Not a mix with black, which is what this used to be: mixing a colour
     * with black by channel takes the colour away with it, so at a tenth of
     * the way the answer was black and nothing else. What is wanted is the
     * same colour, darker -- so the hue and the saturation are kept exactly
     * and only the brightness is moved. A tenth of the way is now a very dark
     * green rather than a black that used to be green.
     */
    private static int toned(int chosen, int percent, boolean dark) {
        try {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(chosen, hsv);
            float how = percent / 100.0f;
            if (dark) {
                // from nearly black up to the colour's own brightness
                hsv[2] = 0.04f + (hsv[2] - 0.04f) * how;
            } else {
                // and from nearly white down to it
                hsv[2] = 1.0f - (1.0f - hsv[2]) * how;
                hsv[1] = hsv[1] * how;
            }
            return 0xFF000000 | (android.graphics.Color.HSVToColor(hsv) & 0xFFFFFF);
        } catch (Throwable ignored) {
            return chosen;
        }
    }

    /**
     * What the settings say, read once and kept.
     *
     * This is asked on every colour the app draws -- `Paint.setColor` alone is
     * nine thousand call sites -- and the answer changes only when somebody
     * opens the mod's own screen and changes it. Reading the preferences that
     * often would put a lock on the drawing path for no reason at all.
     *
     * One object holds the whole answer, so a reader either sees the old
     * settings or the new ones and never half of each.
     */
    private static final class Settled {
        final boolean on;
        final boolean material;
        final boolean dark;
        final int text;
        final int background;
        final int strength;

        /** The background at the depth asked for: asked for on every colour drawn. */
        final int ground;

        Settled(boolean on, boolean material, boolean dark,
                int text, int background, int strength) {
            this.ground = toned(background, strength, dark);
            this.on = on;
            this.material = material;
            this.dark = dark;
            this.text = text;
            this.background = background;
            this.strength = strength;
        }
    }

    private static volatile Settled settled;

    private static Settled current() {
        Settled now = settled;
        return now == null || now.dark != isDark() ? read() : now;
    }

    private static Settled read() {
        boolean dark = isDark();
        // On unless switched off, and the phone's own palette unless another
        // was chosen -- but only where the phone has a palette to offer:
        // before Android 12 there is none, and a theme that is on by default
        // and paints nothing but its own fallback is worse than no theme.
        boolean on = flag(KEY_ON, available());
        boolean material = flag(KEY_MATERIAL, true);
        int chosenText = 0;
        int chosenBackground = 0;
        if (material) {
            chosenText = fromSystem(true);
            chosenBackground = fromSystem(false);
        }
        if (chosenText == 0) chosenText = number(modeKey(KEY_TEXT, dark),
                number(KEY_TEXT, dark ? TEXT : 0xFF161823));
        if (chosenBackground == 0) chosenBackground = number(modeKey(KEY_BACKGROUND, dark),
                number(KEY_BACKGROUND, dark ? BACKGROUND : 0xFFFFFFFF));

        int strength = number(KEY_STRENGTH, 100);
        if (strength < 0) strength = 0;
        if (strength > 100) strength = 100;

        Settled fresh = new Settled(on, material, dark,
                chosenText, chosenBackground, strength);
        // not kept until there is a context to have read the settings with:
        // an answer given too early would otherwise be the answer for good
        if (TokkiYou.context() != null) settled = fresh;
        return fresh;
    }

    public static void setText(int colour) {
        put(modeKey(KEY_TEXT, isDark()), colour);
    }

    public static void setBackground(int colour) {
        put(modeKey(KEY_BACKGROUND, isDark()), colour);
    }

    private static String modeKey(String key, boolean dark) {
        return key + (dark ? "_dark" : "_light");
    }

    public static void reset() {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putBoolean(KEY_ON, false).remove(KEY_MATERIAL)
                .remove(KEY_TEXT).remove(KEY_BACKGROUND)
                .remove(modeKey(KEY_TEXT, true)).remove(modeKey(KEY_TEXT, false))
                .remove(modeKey(KEY_BACKGROUND, true)).remove(modeKey(KEY_BACKGROUND, false))
                .remove(KEY_STRENGTH).apply();
        forget();
        AppearanceColors.reset();
    }

    public static void preset(int chosen) {
        boolean dark = isDark();
        int background = dark
                ? (chosen == 1 ? 0xFF0D1B2A : 0xFF161823)
                : (chosen == 1 ? 0xFFEAF3FA : 0xFFF8F8F8);
        int text = dark ? 0xFFFFFFFF : 0xFF161823;
        SharedPreferences p = prefs();
        if (p != null) p.edit().putBoolean(KEY_ON, true)
                .putBoolean(KEY_MATERIAL, false)
                .putInt(modeKey(KEY_BACKGROUND, dark), background)
                .putInt(modeKey(KEY_TEXT, dark), text)
                .putInt(KEY_STRENGTH, 100).apply();
        forget();
    }

    /**
     * The wallpaper's own colours, when the phone offers them.
     *
     * Android 12 puts the palette it took from the wallpaper into the
     * framework's own resources, so this is a read rather than a calculation.
     * Which end of the neutral ramp to take depends on which theme is on.
     */
    private static int fromSystem(boolean forText) {
        Context context = TokkiYou.context();
        if (context == null || Build.VERSION.SDK_INT < 31) return 0;
        try {
            boolean dark = isDark();
            int id = forText == dark
                    ? android.R.color.system_neutral1_50
                    : android.R.color.system_neutral1_900;
            return context.getColor(id);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ------------------------------------------------------------ the paint

    /**
     * A colour, if this is one of the ones the theme owns.
     *
     * Returns what it was given when theming is off, when the colour is not on
     * the list, or when there is nothing sensible to say -- so a caller can
     * hand anything to this and use the answer without asking.
     */
    /**
     * A colour, if this is one of the ones the theme owns.
     *
     * `fromTheme` says how the colour was come by. True when it was read as a
     * theme colour -- out of a theme attribute or a colour resource -- and any
     * of the theme's colours is fair game. False when it is a number handed
     * straight to a Paint, where only the colours that mean nothing else are
     * safe to touch: TikTok's dark theme writes in white, and so does the
     * caption over a video, and those two must not share a fate.
     */
    public static int recolour(int colour, boolean fromTheme) {
        Settled now = current();
        if (!now.on) return colour;
        if (!(fromTheme ? Nightly.owns(colour) : Nightly.unmistakable(colour))) {
            return colour;
        }

        // how far this colour is from its own theme's background: in the dark
        // theme the background is the black end, in the light theme the white
        float level = brightness(colour);
        if (!isDark()) level = 1.0f - level;

        int mixed = mix(now.ground, now.text, level);
        return (colour & 0xFF000000) | (mixed & 0xFFFFFF);
    }

    /** How bright a colour reads, 0 for black and 1 for white. */
    private static float brightness(int colour) {
        int red = (colour >> 16) & 0xFF;
        int green = (colour >> 8) & 0xFF;
        int blue = colour & 0xFF;
        return (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255.0f;
    }

    /** A step of the way from one colour to another, kept inside the ends. */
    private static int mix(int from, int to, float how) {
        if (how < 0f) how = 0f;
        if (how > 1f) how = 1f;
        int red = round(((from >> 16) & 0xFF), ((to >> 16) & 0xFF), how);
        int green = round(((from >> 8) & 0xFF), ((to >> 8) & 0xFF), how);
        int blue = round((from & 0xFF), (to & 0xFF), how);
        return (red << 16) | (green << 8) | blue;
    }

    private static int round(int from, int to, float how) {
        return (int) (from + (to - from) * how + 0.5f);
    }

    // ------------------------------------------------- the ones already drawn

    /**
     * Repaint what is already on screen.
     *
     * Every rule in the patcher rewrites TikTok's own bytecode, and a
     * background written in a layout is never touched by TikTok's bytecode at
     * all: the framework reads the attribute and builds the drawable inside
     * its own code, where nothing can be rewritten. That is most of the
     * comments panel, the inbox and a conversation -- which is exactly what
     * stayed TikTok's own colour while everything else moved.
     *
     * So those are dealt with from the other end, after they exist. The tree
     * is walked and anything wearing a colour the theme owns is repainted. It
     * is bounded: only flat colours, only colours on the list, and a cap on
     * how much of a tree is walked at once.
     */
    public static void repaint(View root) {
        repaint(root, BUDGET);
    }

    /**
     * A screen is walked in full when it appears and less of it afterwards.
     *
     * The layout listener fires while anything at all is moving, and a full
     * walk two or three times a second is work in the middle of a scroll. What
     * arrives later is a panel or a sheet, which is near the top of the tree.
     */
    public static void repaint(View root, int budget) {
        Settled now = settled;
        if (now == null) now = read();
        if (!now.on || root == null) return;
        long probe = Probe.start();
        try {
            seen = BUDGET - budget;
            walk(root, 0);
        } catch (Throwable ignored) {
        }
        Probe.stop(Probe.THEME, probe);
    }

    private static final ArrayList<WeakReference<View>> watchedRoots =
            new ArrayList<WeakReference<View>>();

    /** Refresh custom role colours in visible TikTok screens after a setting changes. */
    public static void repaintVisible() {
        // asked for after a setting has changed, whichever it was: nothing
        // remembered about any view is to be trusted
        Accent.epoch++;
        synchronized (watchedRoots) {
            for (int i = watchedRoots.size() - 1; i >= 0; i--) {
                View root = watchedRoots.get(i).get();
                if (root == null) {
                    watchedRoots.remove(i);
                    continue;
                }
                repaint(root);
                Fonts.applyTree(root);
                AppearanceColors.repaint(root);
                Badge.rewrite(root);
                Accent.repaintTree(root);
                root.invalidate();
            }
        }
    }

    /**
     * How deep to go, and how much to do at once.
     *
     * Fourteen was far too shallow and it showed: a comment sheet is a few
     * levels down and was reached, while a profile or the inbox -- a fragment
     * inside a pager inside a list inside a coordinator -- is twenty and more,
     * and the walk simply stopped before it got there. The budget is what
     * keeps this bounded now, rather than the depth: a screen is a few hundred
     * views, and anything claiming to be tens of thousands is not a screen.
     */
    private static final int DEEP = 40;
    private static final int BUDGET = 12000;

    private static int seen;

    /**
     * Repaint a drawable, whatever kind it turns out to be.
     *
     * A flat colour is the easy case and not the common one: a panel is
     * usually a shape with rounded corners, and a row is often a stack of
     * drawables with the colour somewhere inside it. Both are followed. A
     * shape is repainted where it stands, which is why the view is handed in
     * as well -- the same shape can be shared between views, and a new one is
     * made rather than the shared one changed.
     */
    private static void paint(android.graphics.drawable.Drawable drawable,
                              View view, int depth) {
        if (drawable == null || depth > 3) return;
        try {
            // a fade this made earlier is made again from what it was made of,
            // so a change of theme starts from TikTok's colours and not ours
            android.graphics.drawable.Drawable first = brushed.get(drawable);
            if (first != null) drawable = first;
            if (drawable instanceof android.graphics.drawable.ShapeDrawable) {
                brush((android.graphics.drawable.ShapeDrawable) drawable, view);
                return;
            }
            if (drawable instanceof android.graphics.drawable.ColorDrawable) {
                int was = ((android.graphics.drawable.ColorDrawable) drawable).getColor();
                int now = recolour(was, true);
                if (now == was) return;
                if (view != null) {
                    view.setBackground(new android.graphics.drawable.ColorDrawable(now));
                } else {
                    // inside a stack of drawables there is no view to hand a
                    // new one to, so this one is changed -- its own copy of
                    // it, since the same drawable is shared by every view
                    // that was inflated from the same file
                    ((android.graphics.drawable.ColorDrawable) drawable.mutate()).setColor(now);
                }
                return;
            }
            if (drawable instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable shape =
                        (android.graphics.drawable.GradientDrawable) drawable;
                android.content.res.ColorStateList held =
                        Build.VERSION.SDK_INT >= 24 ? shape.getColor() : null;
                if (held == null) {
                    fade(shape, view);
                    return;
                }
                int was = held.getDefaultColor();
                int now = recolour(was, true);
                if (now != was) {
                    android.graphics.drawable.Drawable copy = shape.mutate();
                    ((android.graphics.drawable.GradientDrawable) copy).setColor(now);
                    if (view != null) view.setBackground(copy);
                }
                return;
            }
            if (drawable instanceof android.graphics.drawable.LayerDrawable) {
                // a ripple is one of these too, with the colour under it
                android.graphics.drawable.LayerDrawable layers =
                        (android.graphics.drawable.LayerDrawable) drawable;
                for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                    paint(layers.getDrawable(i), null, depth + 1);
                }
                return;
            }
            if (drawable instanceof android.graphics.drawable.InsetDrawable) {
                paint(((android.graphics.drawable.InsetDrawable) drawable).getDrawable(),
                        null, depth + 1);
                return;
            }
            if (drawable instanceof android.graphics.drawable.DrawableContainer) {
                // one drawable per state -- pressed, selected, plain -- and
                // the plain one is what a row looks like nearly all the time
                android.graphics.drawable.Drawable.ConstantState state =
                        drawable.getConstantState();
                if (state instanceof android.graphics.drawable.DrawableContainer
                        .DrawableContainerState) {
                    android.graphics.drawable.Drawable[] each =
                            ((android.graphics.drawable.DrawableContainer
                                    .DrawableContainerState) state).getChildren();
                    if (each != null) {
                        for (android.graphics.drawable.Drawable one : each) {
                            paint(one, null, depth + 1);
                        }
                    }
                }
                return;
            }
            other(drawable);
            if (Probe.ON && view != null) unknown(drawable, view);
        } catch (Throwable ignored) {
        }
    }

    private static final java.util.Set<String> told = new java.util.HashSet<String>();

    /** A test build writes down each kind of background this could do nothing with. */
    private static void unknown(android.graphics.drawable.Drawable drawable, View view) {
        String what = drawable.getClass().getName() + " on " + view.getClass().getName()
                + " " + view.getWidth() + "x" + view.getHeight();
        if (told.size() < 80 && told.add(what)) android.util.Log.e("TokkiYou", "background: " + what);
    }

    /**
     * A fade from one colour to another, rather than one colour.
     *
     * The strip under a profile's banner is one: the picture at the top, the
     * page at the bottom, and between them a fade into the page's colour so
     * the picture has no edge. The page was repainted and the fade was not,
     * which left a band of plain black or plain white across a themed
     * profile. Each stop of it is a theme colour at some transparency -- the
     * clear end is the same colour with nothing of it showing -- so each is
     * moved as the solid colour would be and given its transparency back.
     */
    private static void fade(android.graphics.drawable.GradientDrawable shape, View view) {
        if (Build.VERSION.SDK_INT < 24) return;
        int[] stops = shape.getColors();
        if (Probe.ON && view != null) {
            String what = "fade " + view.getWidth() + "x" + view.getHeight() + " "
                    + (stops == null ? "no stops" : java.util.Arrays.toString(stops));
            if (told.size() < 80 && told.add(what)) {
                StringBuilder owned = new StringBuilder();
                if (stops != null) for (int stop : stops) {
                    owned.append(Integer.toHexString(stop)).append(Nightly.owns(stop | 0xFF000000) ? "+ " : "- ");
                }
                android.util.Log.e("TokkiYou", "background: " + what + " | " + owned);
            }
        }
        if (stops == null || stops.length == 0) return;
        int[] moved = null;
        for (int i = 0; i < stops.length; i++) {
            int solid = recolour(stops[i] | 0xFF000000, true);
            int now = (stops[i] & 0xFF000000) | (solid & 0x00FFFFFF);
            if (now == stops[i]) continue;
            if (moved == null) moved = stops.clone();
            moved[i] = now;
        }
        if (moved == null) return;
        android.graphics.drawable.Drawable copy = shape.mutate();
        ((android.graphics.drawable.GradientDrawable) copy).setColors(moved);
        if (view != null) {
            view.setBackground(copy);
            view.invalidate();
        }
    }

    /** The fades this has rebuilt, and the brushes they were rebuilt from. */
    private static final java.util.Map<android.graphics.drawable.Drawable,
            android.graphics.drawable.Drawable> brushed = java.util.Collections.synchronizedMap(
            new java.util.WeakHashMap<android.graphics.drawable.Drawable,
                    android.graphics.drawable.Drawable>());

    private static final int SAMPLES = 32;

    /** Set when a view could not be dealt with yet and must not be marked as seen. */
    private static boolean later;

    /**
     * A background painted with a brush rather than described as a shape.
     *
     * The fade under a profile's banner is one of these, and it is the hard
     * case: the colours are inside a shader, and a shader does not say what
     * it is made of. But it can be asked to paint. So it paints a square the
     * size of a thumbnail, once, and what it painted is read back: if every
     * row is one colour it is a fade from top to bottom, if every column is,
     * from side to side, and either way the colours along it are now known.
     * Those are moved to the theme and the fade is built again as a plain
     * gradient that says what it is.
     *
     * Only for a rectangle. A brush that fills a circle or a rounded corner
     * would come back as a rectangle, and is left alone.
     */
    private static void brush(android.graphics.drawable.ShapeDrawable shape, View view) {
        android.graphics.Paint paint = shape.getPaint();
        if (shape.getShaderFactory() == null && paint.getShader() == null) {
            // no shader: the brush is one colour, and says so
            int was = paint.getColor();
            int now = recolour(was, true);
            if (now != was) {
                paint.setColor(now);
                shape.invalidateSelf();
            }
            return;
        }
        if (view == null || Build.VERSION.SDK_INT < 24) return;
        android.graphics.drawable.shapes.Shape form = shape.getShape();
        if (form != null && form.getClass() != android.graphics.drawable.shapes.RectShape.class) {
            return;
        }
        // A brush is made for the size it will be painted at, and a view that
        // has not been laid out has no size yet: looked at again once it has.
        int wide = view.getWidth(), tall = view.getHeight();
        if (wide <= 0 || tall <= 0) {
            later = true;
            return;
        }
        if (shape.getBounds().isEmpty()) shape.setBounds(0, 0, wide, tall);
        android.graphics.Rect bounds = shape.getBounds();

        int[] seen = new int[SAMPLES * SAMPLES];
        android.graphics.Bitmap square = android.graphics.Bitmap.createBitmap(
                SAMPLES, SAMPLES, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            // painted at its own size and looked at small, so that a brush
            // which was not made to be resized is still the brush it is
            android.graphics.Canvas canvas = new android.graphics.Canvas(square);
            canvas.scale(SAMPLES / (float) bounds.width(), SAMPLES / (float) bounds.height());
            canvas.translate(-bounds.left, -bounds.top);
            shape.draw(canvas);
            square.getPixels(seen, 0, SAMPLES, 0, 0, SAMPLES, SAMPLES);
        } finally {
            square.recycle();
        }

        boolean down = true, across = true;
        for (int y = 0; y < SAMPLES && (down || across); y++) {
            for (int x = 0; x < SAMPLES; x++) {
                int here = seen[y * SAMPLES + x];
                if (!near(here, seen[y * SAMPLES])) down = false;
                if (!near(here, seen[x])) across = false;
            }
        }
        if (!down && !across) return;   // not a fade along one line: left as it is

        int[] stops = new int[SAMPLES];
        boolean moved = false;
        for (int i = 0; i < SAMPLES; i++) {
            int was = down ? seen[i * SAMPLES] : seen[i];
            // nothing of the colour shows where it is clear, and the reading
            // comes back as black: it is the colour next to it, unseen
            int solid = recolour(was | 0xFF000000, true);
            stops[i] = (was & 0xFF000000) | (solid & 0x00FFFFFF);
            // where it can be seen, that is: a fade of some colour that is
            // not the theme's is clear at one end too, and is not ours to redo
            if (stops[i] != was && (was >>> 24) > 40) moved = true;
        }
        if (!moved) return;
        android.graphics.drawable.GradientDrawable rebuilt =
                new android.graphics.drawable.GradientDrawable(down
                        ? android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                        : android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, stops);
        brushed.put(rebuilt, shape);
        view.setBackground(rebuilt);
    }

    /** The same colour, give or take what drawing it small does to it. */
    private static boolean near(int one, int two) {
        return Math.abs((one >>> 24) - (two >>> 24)) <= 6
                && Math.abs(((one >> 16) & 0xFF) - ((two >> 16) & 0xFF)) <= 6
                && Math.abs(((one >> 8) & 0xFF) - ((two >> 8) & 0xFF)) <= 6
                && Math.abs((one & 0xFF) - (two & 0xFF)) <= 6;
    }

    /** How a drawable the framework does not know says its colour, per class. */
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Object[]> colourOf =
            new java.util.concurrent.ConcurrentHashMap<Class<?>, Object[]>();
    private static final Object[] NONE = new Object[0];

    /**
     * A drawable from a library rather than from Android.
     *
     * A card's rounded rectangle and a Material shape are both classes of
     * their own, and both are a flat colour with corners: they say so through
     * a `getColor` or a `getFillColor` that hands back a colour list, and
     * take a new one the same way. Asked by name, since neither is a type
     * this code can see.
     */
    private static void other(android.graphics.drawable.Drawable drawable) throws Exception {
        Class<?> type = drawable.getClass();
        Object[] how = colourOf.get(type);
        if (how == null) {
            how = NONE;
            if (!type.getName().startsWith("android.graphics.")) {
                for (String[] pair : new String[][] {{"getColor", "setColor"},
                        {"getFillColor", "setFillColor"}}) {
                    try {
                        java.lang.reflect.Method get = type.getMethod(pair[0]);
                        if (get.getReturnType() != android.content.res.ColorStateList.class) {
                            continue;
                        }
                        java.lang.reflect.Method set = type.getMethod(pair[1],
                                android.content.res.ColorStateList.class);
                        get.setAccessible(true);
                        set.setAccessible(true);
                        how = new Object[] {get, set};
                        break;
                    } catch (NoSuchMethodException none) {
                        // not that one
                    }
                }
            }
            colourOf.put(type, how);
        }
        if (how.length == 0) return;
        Object held = ((java.lang.reflect.Method) how[0]).invoke(drawable);
        if (!(held instanceof android.content.res.ColorStateList)) return;
        int was = ((android.content.res.ColorStateList) held).getDefaultColor();
        int now = recolour(was, true);
        if (now != was) {
            ((java.lang.reflect.Method) how[1]).invoke(drawable,
                    android.content.res.ColorStateList.valueOf(now));
            drawable.invalidateSelf();
        }
    }

    // What a view was wearing the last time it was looked at, and under which
    // theme. A shape or a stack costs something to look inside, a screen has
    // thousands of views, and nearly all of them are exactly as they were a
    // moment ago -- so the ones that are, are passed over.
    private static final int WORE = 0x4D617270;   // "Marp"
    private static final int UNDER = 0x4D617271;

    private static void walk(View view, int depth) {
        if (view == null || depth > DEEP || ++seen > BUDGET) return;
        if (Version.TEST) ShareProbe.look(view);

        android.graphics.drawable.Drawable wears = view.getBackground();
        if (wears != null) {
            if (wears instanceof android.graphics.drawable.ColorDrawable) {
                // a flat colour is cheaper to ask than to remember
                paint(wears, view, 0);
            } else {
                Settled now = settled;
                if (view.getTag(WORE) != wears || view.getTag(UNDER) != now) {
                    later = false;
                    paint(wears, view, 0);
                    if (!later) {
                        view.setTag(WORE, view.getBackground());
                        view.setTag(UNDER, now);
                    }
                }
            }
        }

        // Backgrounds only. Text colours are already answered where the app
        // sets them, in their thousands, and repainting them here as well
        // collapsed two colours that were close into one: the labels under the
        // last row of the share sheet came out the colour of the sheet.

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            int many = group.getChildCount();
            for (int i = 0; i < many; i++) walk(group.getChildAt(i), depth + 1);
        }
    }

    /**
     * Watch a screen, so that panels opened inside it are repainted too.
     *
     * A comment sheet or a conversation is not a new screen as far as Android
     * is concerned -- it is more views inside the one that is already up -- so
     * waiting for the next screen would never repaint them. A layout listener
     * hears about them, and the work is held off until the layouts stop
     * arriving, because one of these fires many times a second while anything
     * is moving.
     */
    public static void watch(final android.app.Activity activity) {
        if (activity == null || activity.getClass().getName().startsWith("cat.narezany.tokkiyou.")) return;
        try {
            final View root = activity.getWindow().getDecorView();
            Fonts.applyTree(root);
            DateOverlay.resume(root);
            synchronized (watchedRoots) {
                boolean found = false;
                for (int i = watchedRoots.size() - 1; i >= 0; i--) {
                    View held = watchedRoots.get(i).get();
                    if (held == null) watchedRoots.remove(i);
                    else if (held == root) found = true;
                }
                if (!found) watchedRoots.add(new WeakReference<View>(root));
            }
            if (Boolean.TRUE.equals(root.getTag(WATCHING))) {
                repaint(root);
                Badge.rewrite(root);
                sweep(root);
                Dim.watch(activity);
                return;
            }
            root.setTag(WATCHING, Boolean.TRUE);
            root.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        private long updated;
                        private boolean owed;
                        private int shape=shape(root, 0);
                        private final Runnable later = new Runnable() {
                            @Override
                            public void run() {
                                owed = false;
                                if (root.isAttachedToWindow()) look();
                            }
                        };
                        @Override
                        public void onGlobalLayout() {
                            long now = android.os.SystemClock.uptimeMillis();
                            if (now - updated < QUIET) {
                                // Too soon after the last look -- but whatever
                                // this layout brought is on the screen now, and
                                // if nothing else moves nobody would ever come
                                // back for it. That was the comments left
                                // unpainted until the app was left and returned
                                // to: they arrived inside the quiet. So one
                                // look is owed for when the quiet ends.
                                if (!owed) {
                                    owed = true;
                                    root.postDelayed(later, QUIET - (now - updated) + 20);
                                }
                                return;
                            }
                            look();
                        }
                        private void look() {
                            // asked here as well as on the way to a colour:
                            // once every colour on screen is remembered,
                            // nothing else would notice the theme change
                            isDark();
                            long probe = Probe.start();
                            int next=shape(root, 0);
                            Probe.stop(Probe.DIM_PRINT, probe);
                            if(next==shape)return;shape=next;
                            updated = android.os.SystemClock.uptimeMillis();
                            // the whole screen, not the first part of it: what
                            // has not changed is passed over, so the whole of
                            // it costs less than the part used to
                            repaint(root);
                            Fonts.applyTree(root);
                            probe = Probe.start();
                            DateOverlay.resume(root);
                            Probe.stop(Probe.DATES, probe);
                            probe = Probe.start();
                            Badge.rewrite(root);
                            Probe.stop(Probe.BADGES, probe);
                            sweep(root);
                        }
                    });
            repaint(root);
            Badge.rewrite(root);
            sweep(root);
            Dim.watch(activity);
        } catch (Throwable error) {
            Diary.note("theme: " + error);
        }
    }

    /**
     * The accent and the role colours, over the whole screen but not all at
     * once: a few milliseconds before each frame until it is done. These are
     * the two walks that cost the most on a screen nobody has looked at yet,
     * and a screen nobody has looked at yet is one that is just coming up.
     */
    private static final Sliced.Visit ACCENT = new Sliced.Visit() {
        @Override
        public void view(View view) {
            Accent.look(view);
        }
    };
    private static final Sliced.Visit ROLES = new Sliced.Visit() {
        @Override
        public void view(View view) {
            AppearanceColors.look(view);
        }
    };

    /**
     * What is on a screen and how large, said as one number: enough to tell
     * that something new has appeared and wants painting.
     *
     * This used to be the fingerprint the screen saver takes, which also
     * says where everything has moved to and how faint it is, and asks two
     * tables about each view. Asking where a view has moved to is a call out
     * of Java, three of them a view and thousands of views; and a screen with
     * anything sliding or fading on it had a new fingerprint every time, so
     * the whole of it was painted again several times a second while a video
     * was being swiped. A view that has only moved needs no new paint. What
     * is hidden is not looked inside: it is looked at when it is shown,
     * which changes the number.
     */
    private static int shape(View view, int depth) {
        if (view == null || depth > 40) return 0;
        int visibility = view.getVisibility();
        int hash = System.identityHashCode(view) * 31 + visibility;
        if (visibility != View.VISIBLE) return hash;
        hash = 31 * hash + view.getWidth() * 17 + view.getHeight();
        hash = 31 * hash + (view.isSelected() ? 8 : 0) + (view.isActivated() ? 16 : 0);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            int many = group.getChildCount();
            hash = 31 * hash + many;
            for (int i = 0; i < many; i++) hash = 31 * hash + shape(group.getChildAt(i), depth + 1);
        }
        return hash;
    }

    private static void sweep(View root) {
        Sliced.walk(root, ACCENT, ROLES);
    }

    private static volatile long last;
    private static final long QUIET = 400;



    // setTag(int, ...) wants a key that looks like a resource id, and every key
    // the mod uses has to differ from every other one. This was the same
    // number SettingsRow marks a window with, so whichever ran first told the
    // other its work was already done -- and the TokkiYou row stopped appearing
    // in TikTok's settings at all.
    private static final int WATCHING = 0x4D61726A;  // "Marj"

    // ------------------------------------------------- which theme is on

    private static volatile int mode;  // 0 unknown, 1 dark, 2 light
    private static volatile long asked;

    /**
     * Whether the app is wearing its dark theme, asked at most once a second.
     *
     * Asked of the screen that is on top rather than of the application. TikTok
     * has a theme switch of its own, and a phone set to light with TikTok set
     * to dark leaves the application saying "light" while every pixel on screen
     * says otherwise. The mod then repainted the colours it owns for the wrong
     * theme and left everything else alone, which is how a dark screen ended up
     * half light.
     *
     * What is on the window is worth more than what is in a setting, so the
     * window's own background is read first, and the configuration is only the
     * answer when there is no window to look at.
     */
    public static boolean isDark() {
        long now = android.os.SystemClock.uptimeMillis();
        if (mode != 0 && now - asked < 1000) return mode == 1;
        Boolean seen = painted();
        // With no screen of TikTok's to look at, what was last seen on one
        // still stands. The phone's own setting is asked only when nothing
        // has been seen: TikTok can be light on a dark phone, and it used to
        // turn dark for a second each time the mod's settings were shut.
        boolean dark = seen != null ? seen.booleanValue() : mode != 0 ? mode == 1 : configured();
        int was = mode;
        mode = dark ? 1 : 2;
        asked = now;
        if (was != 0 && was != mode) {
            // the palette has a dark end and a light end, and every answer
            // remembered so far was worked out for the other one
            settled = null;
            Accent.forget();
        }
        return dark;
    }

    /** What the screen on top is painted with, if it says plainly. */
    private static Boolean painted() {
        try {
            android.app.Activity here = Watch.here();
            if (here == null) return null;
            // the mod's own screens are painted its own way, which says nothing about TikTok's
            if (here.getClass().getName().startsWith("cat.narezany.tokkiyou.")) return null;
            android.view.View decor = here.getWindow() == null
                    ? null : here.getWindow().getDecorView();
            android.graphics.drawable.Drawable ground =
                    decor == null ? null : decor.getBackground();
            if (ground instanceof android.graphics.drawable.ColorDrawable) {
                int colour = ((android.graphics.drawable.ColorDrawable) ground).getColor();
                if (android.graphics.Color.alpha(colour) == 255) {
                    return Boolean.valueOf(brightness(colour) < 0.5f);
                }
            }
            Configuration config = here.getResources().getConfiguration();
            return Boolean.valueOf((config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The application's own idea, which is the phone's unless TikTok says otherwise. */
    private static boolean configured() {
        try {
            Context context = TokkiYou.context();
            if (context != null) {
                Configuration config = context.getResources().getConfiguration();
                return (config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** Something the answers depend on has changed; nothing remembered stands. */
    static void forget() {
        mode = 0;
        settled = null;
        Accent.forget();
    }

    // ------------------------------------------------------------ the store

    private static boolean flag(String key, boolean fallback) {
        try {
            SharedPreferences prefs = prefs();
            return prefs == null ? fallback : prefs.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int number(String key, int fallback) {
        try {
            SharedPreferences prefs = prefs();
            return prefs == null ? fallback : prefs.getInt(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void put(String key, int value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(key, value).apply();
        forget();
    }

    private static SharedPreferences prefs() {
        Context context = TokkiYou.context();
        if (context == null) return null;
        return AccountAppearance.prefs(context);
    }
}
