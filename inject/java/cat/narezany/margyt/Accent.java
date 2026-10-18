package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bytedance.tux.icon.TuxIconView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TikTok's accent colour, made changeable.
 *
 * The app is built around one pink, `#FE2C55`: the like, the follow button, the
 * tab underline, the badges. Most of the places it is drawn hold it as a plain
 * constant in the bytecode -- `const v1, -0x1d3ab` before a `Paint.setColor` --
 * so the build rewrites each of those into a call here, and the colour becomes
 * whatever this returns.
 *
 * This sits in the drawing path of half the app, so it answers from a cached
 * int and never throws: the worst it can do when something is wrong is hand
 * back the pink the app came with.
 */
public final class Accent {

    private Accent() {}

    /**
     * TikTok's own pink, and always the colour a swap is measured from.
     *
     * It stays the reference even when the build baked something else into the
     * resources. Making the baked colour the reference instead is a mistake
     * that was made once and is worth writing down: the app then stops
     * recognising its own pink wherever the build did not reach -- which is
     * most of the bytecode -- and starts recognising whatever sits near the
     * baked colour by hue, which for a mint is the green of somebody being
     * online. Shades this build wrote are recognised by the list in Baked
     * instead, exactly, and sent wherever their original would go.
     */
    public static final int TIKTOK = 0xFFFE2C55;

    /** What this apk was built with: the colour before anyone chooses another. */
    public static final int BUILT_WITH = Baked.ACCENT;

    public static final String KEY = "accent";

    /**
     * Whether the accent is the phone's own, taken from its wallpaper.
     *
     * On unless a colour was ever picked by hand: somebody who chose a colour
     * before this existed keeps the one they chose.
     */
    public static final String KEY_MATERIAL = "accent_material";

    /** iso-style names are not needed here; the label is the colour itself. */
    /** Material tonal accents; Android 12+ contributes the wallpaper palette. */
    public static final int[] PALETTE={0xFF6750A4,0xFF0B57D0,0xFF006A6A,0xFF146C2E,0xFF8B5000,0xFFB3261E,0xFF984061};
    public static int[] palette(){return PALETTE.clone();}
    static int custom(){SharedPreferences p=prefs();return p==null?0:p.getInt("accent_custom",0);}
    static void setCustom(int colour){SharedPreferences p=prefs();if(p!=null)p.edit().putInt("accent_custom",colour).apply();set(colour);}

    /**
     * The accent the phone took from the wallpaper, or zero.
     *
     * Android 12 puts that palette into the framework's own resources, so this
     * is a read rather than a guess. Read straight from the resources and not
     * through the mod's own interception, which would hand back whatever the
     * accent already is.
     */
    public static int fromWallpaper() {
        Context context = Margy.context();
        if (context == null || android.os.Build.VERSION.SDK_INT < 31) return 0;
        try {
            return context.getResources().getColor(
                    android.R.color.system_accent1_400, context.getTheme());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static volatile int cached;

    static void reload() { cached = 0; forget(); }

    public static int colour() {
        int known = cached;
        if (known != 0) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return BUILT_WITH;  // too early to know; do not cache it
        int chosen = 0;
        try {
            if (follows(prefs)) chosen = fromWallpaper();
            if (chosen == 0) chosen = prefs.getInt(KEY, BUILT_WITH);
        } catch (Throwable ignored) {
        }
        if (chosen == 0) chosen = BUILT_WITH;
        cached = chosen;
        return chosen;
    }

    private static boolean follows(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_MATERIAL, !prefs.contains(KEY));
    }

    /** Whether the accent in use is the wallpaper's. False on a phone that has none. */
    public static boolean isMaterial() {
        try {
            SharedPreferences prefs = prefs();
            return prefs != null && follows(prefs) && fromWallpaper() != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setMaterial(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_MATERIAL, on).apply();
        cached = 0;
        forget();
        Themes.repaintVisible();
        refreshLikeViews();
    }

    /**
     * Where a rewritten constant lands.
     *
     * `const v1, -0x1d3ab` in TikTok's bytecode becomes a call to this, so it
     * is the accent as chosen -- and then whatever the plugins make of it.
     * `colour()` itself stays plain: it is what the mod paints its own screen
     * with, and a plugin recolouring the settings it is being configured from
     * would be a poor joke.
     */
    public static int accent() {
        return Plugins.colour(colour());
    }

    public static void set(int colour) {
        cached = colour == 0 ? BUILT_WITH : colour;
        // swap() keeps the last translated source colour. It must not survive
        // a palette change, or an already-visible like can stay in the old
        // colour until it happens to be painted through another source first.
        forget();
        SharedPreferences prefs = prefs();
        // a colour picked by hand is also the end of following the wallpaper
        if (prefs != null) {
            prefs.edit().putInt(KEY, cached).putBoolean(KEY_MATERIAL, false).apply();
        }
        Themes.repaintVisible();
        refreshLikeViews();
    }

    public static boolean isDefault() {
        return colour() == BUILT_WITH;
    }

    private static SharedPreferences prefs() {
        try {
            Context context = Margy.context();
            if (context == null) return null;
            return AccountAppearance.prefs(context);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------- where colours arrive

    /**
     * Any colour of TikTok's red family comes back on the chosen accent.
     *
     * Not the one value it used to be: the app draws its pink at a dozen
     * opacities and next to a family of neighbours, and swapping only the exact
     * brand colour left nine tenths of the red on screen. Palette says what
     * belongs to the family and where it moves to.
     */
    public static int swap(int colour) {
        // Paint.setColor is called on every frame of everything, so the answer
        // to the question just asked is kept: one long holds both halves, so a
        // reader either sees a whole pair or none of it, with no lock either way
        long known = memo;
        if ((int) (known >>> 32) == colour) return (int) known;

        java.util.concurrent.atomic.AtomicLongArray table=translations;
        int index=(colour^(colour>>>16))&255;long pair=table.get(index);
        int translated;
        if(colour!=0 && (int)(pair>>>32)==colour)translated=(int)pair;
        else {translated=translate(colour);table.set(index,((long)colour<<32)|(translated&0xFFFFFFFFL));}
        int out = Plugins.colour(translated);
        memo = ((long) colour << 32) | (out & 0xFFFFFFFFL);
        return out;
    }

    /**
     * Two ways in, and the second only when the first says nothing.
     *
     * TikTok's own family is recognised by hue, which reaches every shade of
     * it including the ones no build ever saw. What the build baked is not a
     * family at all -- it is a list -- and each entry knows the shade it was
     * made from, so it is sent wherever that shade would go now.
     */
    private static int translate(int colour) {
        int chosen = colour();
        int moved = Palette.map(colour, TIKTOK, chosen);
        if (moved != colour) return moved;

        int origin = originOf(colour);
        if (origin != 0) return Palette.map(origin, TIKTOK, chosen);

        // the cyan and the online green, only once the accent is a choice
        if (chosen != TIKTOK && Palette.secondary(colour)) return Palette.toAccent(colour, chosen);

        // last, and only for the colours TikTok repaints itself when its own
        // theme changes: the accent has had its say and did not want this one
        return Themes.recolour(colour, true);
    }

    /** The shade a baked colour was made from, or zero. Binary search. */
    private static int originOf(int colour) {
        int[] baked = Baked.BAKED;
        int low = 0, high = baked.length - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int here = baked[middle];
            if (here == colour) return Baked.FROM[middle];
            if (here < colour) low = middle + 1; else high = middle - 1;
        }
        return 0;
    }

    private static volatile long memo;
    private static volatile java.util.concurrent.atomic.AtomicLongArray translations=new java.util.concurrent.atomic.AtomicLongArray(256);

    /** Forget the one remembered answer: something it depended on has changed. */
    /**
     * Goes up whenever anything a colour depends on has changed. The walks
     * over the screen remember each view as they left it, and this is how
     * they learn that what they left is no longer right.
     */
    static volatile int epoch;

    static void forget() {
        epoch++;
        memo = 0;
        translations=new java.util.concurrent.atomic.AtomicLongArray(256);
        recolouredBitmaps.clear();
        recolouredVectors.clear();
        plainVectors.clear();
    }

    // ---------------------------------------------- where a colour is used

    /**
     * The other half of the accent.
     *
     * Reading a colour is not the only way to have one: it can be computed,
     * blended, or carried in from somewhere the mod never sees. But it has to
     * be applied to something before it is drawn, and there are only so many
     * ways to apply one. Every call site of these in TikTok's bytecode is
     * rewritten to come through here, which is how the accent reaches what the
     * resource table and the constants never could.
     */
    /**
     * A background written in a layout, which arrives already wrapped.
     *
     * `android:background="?attr/..."` is resolved by the framework before any
     * of the app's code sees it, and what comes back is a ColorDrawable rather
     * than a number -- so every rule that watches for colours looks straight
     * past the thing most screens are painted with. Unwrapped here, moved, and
     * handed back as a new drawable: the one that came out of the resources is
     * shared with everything else that asked for it.
     */
    public static Drawable getDrawable(TypedArray array, int index) {
        return moved(array.getDrawable(index));
    }

    public static void setBackgroundResource(View view, int id) {
        try {
            Drawable drawable = view.getContext().getDrawable(id);
            Drawable out = moved(drawable);
            if (out != drawable) {
                view.setBackground(out);
                return;
            }
        } catch (Throwable ignored) {
        }
        view.setBackgroundResource(id);
    }

    /** The same drawable in the chosen colours, or the one that came in. */
    private static Drawable moved(Drawable drawable) {
        if(drawable==null)return null;
        Owned known=owned.get(drawable);
        if(known!=null){if(known.accent==colour())return drawable;drawable=known.original;}
        Drawable out=moveDrawable(drawable);
        if(out!=drawable)owned.put(out,new Owned(drawable,colour()));
        return out;
    }
    private static Drawable moveDrawable(Drawable drawable) {
        if (drawable instanceof android.graphics.drawable.VectorDrawable)
            return movedVector(drawable);
        if (drawable instanceof android.graphics.drawable.BitmapDrawable)
            return movedBitmap((android.graphics.drawable.BitmapDrawable) drawable);
        if (drawable instanceof GradientDrawable) return movedShape((GradientDrawable) drawable);
        if (drawable instanceof android.graphics.drawable.LayerDrawable)
            return movedLayers((android.graphics.drawable.LayerDrawable) drawable);
        if (!(drawable instanceof ColorDrawable)) return drawable;
        int was = ((ColorDrawable) drawable).getColor();
        int now = sourced(was);
        return now == was ? drawable : new ColorDrawable(now);
    }

    /**
     * Whether a picture is TikTok's red and next to nothing else.
     *
     * That is what an icon to be moved looks like: one colour, and greys. A
     * drawing is not that -- the flame of a streak runs from red through
     * orange to yellow -- and moving only the part of it that happens to fall
     * in the red family gave a flame in colours nobody chose. So a picture is
     * moved only when nearly every pixel with any colour in it is the red.
     */
    private static boolean plainlyRed(int[] pixels) {
        int coloured = 0, red = 0;
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 48) continue;
            int r = (pixel >> 16) & 0xFF, g = (pixel >> 8) & 0xFF, b = pixel & 0xFF;
            int most = Math.max(r, Math.max(g, b)), least = Math.min(r, Math.min(g, b));
            if (most - least < 40) continue;   // a grey, a white, a black
            coloured++;
            if (Palette.captures(pixel, TIKTOK)) red++;
        }
        return coloured > 0 && red * 100 >= coloured * 88;
    }

    /** Pictures at least this many pixels are worth reading from disk (64x64). */
    private static final int DISK_MIN = 4096;

        private static final java.util.Map<Bitmap, Bitmap> recolouredBitmaps =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Bitmap, Bitmap>());

    /**
     * A picture whose pixels are TikTok's red, moved without asking its name.
     *
     * The names of TikTok's resources are stripped from the apk, so a rule that
     * waits for "like" in a name never fires. The pixels are still there: any
     * small picture with the red family in it is redrawn on the accent, once,
     * and the answer is kept until the accent changes.
     */
    private static Drawable movedBitmap(android.graphics.drawable.BitmapDrawable drawable) {
        int target = colour();
        if (target == TIKTOK) return drawable;
        try {
            Bitmap source = drawable.getBitmap();
            if (source == null || source.isRecycled()) return drawable;
            int width = source.getWidth(), height = source.getHeight();
            if (width * height > 160000) return drawable;
            Bitmap known = recolouredBitmaps.get(source);
            Context context = Margy.context();
            Resources resources = context == null ? null : context.getResources();
            if (known != null) return new android.graphics.drawable.BitmapDrawable(resources, known);
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);
            // Larger pictures are remembered across runs, keyed by their pixels.
            // "bmp2": what was kept under the old name includes pictures that
            // should never have been moved, and must not be handed back
            String diskKey = width * height >= DISK_MIN
                    ? "bmp2|" + Integer.toHexString(target) + "|"
                        + RecolourCache.hash(pixels, width, height) : null;
            if (diskKey != null) {
                byte[] saved = RecolourCache.get(diskKey);
                if (saved == RecolourCache.NONE) return drawable;
                Bitmap restored = saved == null ? null : RecolourCache.decode(saved);
                if (restored != null && restored.getWidth() == width
                        && restored.getHeight() == height) {
                    restored.setDensity(source.getDensity());
                    recolouredBitmaps.put(source, restored);
                    return new android.graphics.drawable.BitmapDrawable(resources, restored);
                }
            }
            boolean any = false;
            if (plainlyRed(pixels)) {
                for (int i = 0; i < pixels.length; i++) {
                    int pixel = pixels[i];
                    if ((pixel >>> 24) == 0) continue;
                    int now = Palette.map(pixel, TIKTOK, target);
                    if (now != pixel) { pixels[i] = now; any = true; }
                }
            }
            if (!any) {
                if (diskKey != null) RecolourCache.put(diskKey, null);
                return drawable;
            }
            Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            out.setPixels(pixels, 0, width, 0, 0, width, height);
            // the size it is drawn at comes from this, and a picture made here
            // is otherwise taken to be the screen's own density
            out.setDensity(source.getDensity());
            recolouredBitmaps.put(source, out);
            if (diskKey != null) {
                byte[] encoded = RecolourCache.png(out);
                if (encoded != null) RecolourCache.put(diskKey, encoded);
            }
            return new android.graphics.drawable.BitmapDrawable(resources, out);
        } catch (Throwable ignored) {
            return drawable;
        }
    }

        private static final java.util.Map<Object, Bitmap> recolouredVectors =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, Bitmap>());
    private static final java.util.Set<Object> plainVectors = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>()));

    /**
     * A vector with TikTok's red in it, drawn once and moved pixel by pixel.
     *
     * A vector's colours are parsed by the framework and never reach the mod,
     * and the build only rewrites them when it is given an accent of its own.
     * Drawn into a small bitmap, they are pixels like any others. Vectors with
     * no red in them are remembered, so asking again costs one lookup.
     */
    private static Drawable movedVector(Drawable drawable) {
        int target = colour();
        if (target == TIKTOK) return drawable;
        android.graphics.Rect bounds = null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null && plainVectors.contains(state)) return drawable;
            Context context = Margy.context();
            Resources resources = context == null ? null : context.getResources();
            if (state != null) {
                Bitmap known = recolouredVectors.get(state);
                if (known != null)
                    return new android.graphics.drawable.BitmapDrawable(resources, known);
            }
            int width = drawable.getIntrinsicWidth(), height = drawable.getIntrinsicHeight();
            if (width <= 0 || height <= 0 || width * height > 160000) return drawable;
            bounds = drawable.copyBounds();
            Bitmap drawn = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new android.graphics.Canvas(drawn));
            drawable.setBounds(bounds);
            bounds = null;
            int[] pixels = new int[width * height];
            drawn.getPixels(pixels, 0, width, 0, 0, width, height);
            String diskKey = width * height >= DISK_MIN
                    ? "vec2|" + Integer.toHexString(target) + "|"
                        + RecolourCache.hash(pixels, width, height) : null;
            if (diskKey != null) {
                byte[] saved = RecolourCache.get(diskKey);
                if (saved == RecolourCache.NONE) {
                    if (state != null) plainVectors.add(state);
                    return drawable;
                }
                Bitmap restored = saved == null ? null : RecolourCache.decode(saved);
                if (restored != null) {
                    if (state != null) recolouredVectors.put(state, restored);
                    return new android.graphics.drawable.BitmapDrawable(resources, restored);
                }
            }
            boolean any = false;
            boolean red = plainlyRed(pixels);
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                if ((pixel >>> 24) == 0) continue;
                int now = red ? Palette.map(pixel, TIKTOK, target) : pixel;
                if (now == pixel && Palette.secondary(pixel)) now = Palette.toAccent(pixel, target);
                if (now != pixel) { pixels[i] = now; any = true; }
            }
            if (!any) {
                if (state != null) plainVectors.add(state);
                if (diskKey != null) RecolourCache.put(diskKey, null);
                return drawable;
            }
            Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            out.setPixels(pixels, 0, width, 0, 0, width, height);
            if (state != null) recolouredVectors.put(state, out);
            if (diskKey != null) {
                byte[] encoded = RecolourCache.png(out);
                if (encoded != null) RecolourCache.put(diskKey, encoded);
            }
            return new android.graphics.drawable.BitmapDrawable(resources, out);
        } catch (Throwable ignored) {
            if (bounds != null) try { drawable.setBounds(bounds); } catch (Throwable ignoredToo) { }
            return drawable;
        }
    }

        /** Solid and gradient fills, copied without changing geometry or shared resources. */
    private static Drawable movedShape(GradientDrawable shape) {
        if (android.os.Build.VERSION.SDK_INT < 24) return shape;
        try {
            ColorStateList held = shape.getColor();
            ColorStateList fill = moved(held);
            int[] stops = shape.getColors();
            int[] mapped = stops == null ? null : stops.clone();
            boolean changed = fill != held;
            if (mapped != null) for (int i=0;i<mapped.length;i++) {
                mapped[i]=sourced(stops[i]);changed |= mapped[i]!=stops[i];
            }
            if (!changed) return shape;
            Drawable.ConstantState state = shape.getConstantState();
            // An uncopyable shared shape must remain untouched.
            if (state == null) return shape;
            Drawable copy = state.newDrawable().mutate();
            if (!(copy instanceof GradientDrawable)) return shape;
            if (mapped != null) ((GradientDrawable)copy).setColors(mapped);
            else ((GradientDrawable)copy).setColor(fill);
            return copy;
        } catch (Throwable ignored) {
            return shape;
        }
    }

    /** A stack of drawables, with the layers that hold the red replaced in a copy. */
    private static Drawable movedLayers(android.graphics.drawable.LayerDrawable layers) {
        try {
            int count = layers.getNumberOfLayers();
            Drawable[] now = new Drawable[count];
            boolean any = false;
            for (int i = 0; i < count; i++) {
                Drawable child = layers.getDrawable(i);
                now[i] = moved(child);
                any |= now[i] != child;
            }
            if (!any) return layers;
            Drawable.ConstantState state = layers.getConstantState();
            Drawable copy = state == null ? layers.mutate() : state.newDrawable().mutate();
            if (!(copy instanceof android.graphics.drawable.LayerDrawable)) return layers;
            android.graphics.drawable.LayerDrawable out = (android.graphics.drawable.LayerDrawable) copy;
            if (out.getNumberOfLayers() != count) return layers;
            for (int i = 0; i < count; i++)
                if (now[i] != layers.getDrawable(i)) out.setDrawable(i, now[i]);
            return out;
        } catch (Throwable ignored) {
            return layers;
        }
    }

    // ------------------------------------------- what is already on screen

    private static final class Owned {
        final Drawable original;
        final int accent;
        Owned(Drawable original, int accent) {
            this.original = original;
            this.accent = accent;
        }
    }

    /** Drawables this class put on a view, with the one each replaced. */
    private static final java.util.Map<Drawable, Owned> owned =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Drawable, Owned>());

    /**
     * Backgrounds and icons that were read by the framework, not by the app.
     *
     * A badge, the plus under an avatar or the middle of the tab bar is a
     * shape or a stack of them in a layout file: the framework builds it, and
     * none of the rewritten calls ever sees its colour. So the views are
     * visited after they exist, name-free, and whatever red a background or
     * an icon holds is moved onto the accent. What was replaced is kept, so
     * a change of accent starts from the original instead of from a copy,
     * and a drawable already moved for this accent is left alone.
     */
    public static void repaintTree(View root) {
        if (root == null) return;
        if (colour() == TIKTOK && owned.isEmpty() && ownedColours.isEmpty()) return;
        try {
            paintViews(root, 0, new int[] {0});
        } catch (Throwable ignored) { }
    }

    // What a view was wearing when it was last looked at: its drawables, its
    // colour lists and its text, by which objects they were. A screen is
    // thousands of views and this walk runs each time the layout changes;
    // measured on a phone it took ten milliseconds a time, a frame and more,
    // nearly all of it spent finding out again that nothing needed doing.
    private static final int LOOKED = 0x4D617272;

    private static int wearing(View view) {
        int all = epoch;
        all = 31 * all + System.identityHashCode(view.getBackground());
        all = 31 * all + System.identityHashCode(view.getBackgroundTintList());
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            all = 31 * all + System.identityHashCode(text.getTextColors());
            all = 31 * all + System.identityHashCode(text.getHintTextColors());
            all = 31 * all + System.identityHashCode(text.getLinkTextColors());
            all = 31 * all + System.identityHashCode(text.getText());
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            all = 31 * all + System.identityHashCode(image.getImageTintList());
            all = 31 * all + System.identityHashCode(image.getDrawable());
        }
        return all;
    }

    /** One view, looked at if it has changed since the last time: what a walk calls. */
    static void look(View view) {
        if (colour() == TIKTOK && owned.isEmpty() && ownedColours.isEmpty()) return;
        Object last = view.getTag(LOOKED);
        if (!(last instanceof Integer) || ((Integer) last).intValue() != wearing(view)) {
            paintView(view);
            view.setTag(LOOKED, Integer.valueOf(wearing(view)));
        }
    }

    private static void paintViews(View view, int depth, int[] seen) {
        if (view == null || depth > 40 || ++seen[0] > 12000) return;
        look(view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0, many = group.getChildCount(); i < many; i++)
                paintViews(group.getChildAt(i), depth + 1, seen);
        }
    }

    private static void paintView(View view) {
        Drawable background = repainted(view.getBackground(), false);
        if (background != null) view.setBackground(background);
        ColorStateList backgroundTint=view.getBackgroundTintList();
        ColorStateList newBackgroundTint=repaintedColours(backgroundTint);
        if (newBackgroundTint!=backgroundTint) view.setBackgroundTintList(newBackgroundTint);
        if (view instanceof TextView) {
            TextView text=(TextView)view;
            ColorStateList colours=text.getTextColors(), hints=text.getHintTextColors();
            ColorStateList newColours=repaintedColours(colours), newHints=repaintedColours(hints);
            if (newColours!=colours) text.setTextColor(newColours);
            if (newHints!=hints) text.setHintTextColor(newHints);
            ColorStateList links=text.getLinkTextColors(), newLinks=repaintedColours(links);
            if (newLinks!=links) text.setLinkTextColor(newLinks);
            paintSpans(text);
            accentReply(text);
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            ColorStateList tint=image.getImageTintList(), newTint=repaintedColours(tint);
            if (newTint!=tint) image.setImageTintList(newTint);
            Drawable icon = repainted(image.getDrawable(), true);
            if (icon != null) image.setImageDrawable(icon);
        }
    }

    /** A text the app coloured span by span, and the one this class put on screen instead. */
    private static final class OwnedText {
        final CharSequence original,shown;
        OwnedText(CharSequence original,CharSequence shown){this.original=original;this.shown=shown;}
    }
    private static final int SPAN_KEY = 0x54545350;  // "TTSP"

    /**
     * Colour set on a piece of text rather than on the view -- the "Войти" in
     * "have an account? Войти", the red tag after a search suggestion. The
     * view's own colours say nothing about these, so they are moved here, on a
     * copy, and the original kept so a change of accent starts from it.
     */
    private static void paintSpans(TextView text) {
        if (text instanceof android.widget.EditText) return;
        try {
            CharSequence content = text.getText();
            Object held = text.getTag(SPAN_KEY);
            CharSequence base = content;
            if (held instanceof OwnedText && ((OwnedText) held).shown == content)
                base = ((OwnedText) held).original;
            if (!(base instanceof android.text.Spanned)) return;
            android.text.Spanned spanned = (android.text.Spanned) base;
            android.text.style.ForegroundColorSpan[] spans =
                    spanned.getSpans(0, spanned.length(), android.text.style.ForegroundColorSpan.class);
            android.text.SpannableStringBuilder out = null;
            for (android.text.style.ForegroundColorSpan span : spans) {
                if (span.getClass() != android.text.style.ForegroundColorSpan.class) continue;
                int was = span.getForegroundColor(), now = sourced(was);
                if (now == was) continue;
                if (out == null) out = new android.text.SpannableStringBuilder(base);
                int start = spanned.getSpanStart(span), end = spanned.getSpanEnd(span);
                int flags = spanned.getSpanFlags(span);
                out.removeSpan(span);
                out.setSpan(new android.text.style.ForegroundColorSpan(now), start, end, flags);
            }
            if (out == null) {
                // nothing to move any more: put back what the app wrote
                if (base != content) { text.setTag(SPAN_KEY, null); text.setText(base); }
                return;
            }
            text.setTag(SPAN_KEY, new OwnedText(base, out));
            text.setText(out);
            // TextView keeps its own copy; remember the one it now holds
            CharSequence shown = text.getText();
            if (shown != out) text.setTag(SPAN_KEY, new OwnedText(base, shown));
        } catch (Throwable ignored) { }
    }

    private static final class OwnedColours {
        final ColorStateList original;final int accent;
        OwnedColours(ColorStateList original,int accent){this.original=original;this.accent=accent;}
    }
    private static final java.util.Map<ColorStateList,OwnedColours> ownedColours=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<ColorStateList,OwnedColours>());
    private static final java.util.Map<ColorStateList,Integer> unchangedColours=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<ColorStateList,Integer>());
    private static ColorStateList repaintedColours(ColorStateList current) {
        if (current==null) return null;
        int target=colour();
        Integer unchanged=unchangedColours.get(current);
        if (unchanged!=null && unchanged.intValue()==target) return current;
        OwnedColours mine=ownedColours.get(current);
        if (mine!=null && mine.accent==target) return current;
        ColorStateList base=mine==null?current:mine.original;
        ColorStateList mapped=target==TIKTOK?base:moved(base);
        if (mapped!=base) ownedColours.put(mapped,new OwnedColours(base,target));
        else unchangedColours.put(base,target);
        return mapped;
    }

    /** The drawable to put in place of this one, or null to leave things as they are. */
    private static Drawable repainted(Drawable current, boolean icon) {
        if (current == null) return null;
        int accent = colour();
        Owned mine = owned.get(current);
        Drawable base = current;
        if (mine != null) {
            if (mine.accent == accent) return null;
            base = mine.original;
        }
        if (accent == TIKTOK) return mine != null ? base : null;
        if (icon && base instanceof android.graphics.drawable.BitmapDrawable) {
            // pictures this small are icons; larger ones may be somebody's photo
            Bitmap picture = ((android.graphics.drawable.BitmapDrawable) base).getBitmap();
            if (picture == null || picture.getWidth() * picture.getHeight() > 20000)
                return mine != null ? base : null;
        }
        Drawable now = moved(base);
        if (now == base) return mine != null ? base : null;
        owned.put(now, new Owned(base, accent));
        return now;
    }

    public static ColorStateList getColorStateList(Resources resources, int id) {
        return moved(resources.getColorStateList(id));
    }

    public static ColorStateList getColorStateList(TypedArray array, int index) {
        return moved(array.getColorStateList(index));
    }

    /**
     * A colour per state, moved state by state.
     *
     * There is no way to read the states back before Android 10, and below
     * that the list is left exactly as it was -- one colour on an older phone
     * is a small thing next to a list rebuilt out of guesses.
     */
    private static ColorStateList moved(ColorStateList list) {
        if(list==null)return null;
        OwnedColours known=ownedColours.get(list);
        if(known!=null){if(known.accent==colour())return list;list=known.original;}
        ColorStateList out=moveColours(list);
        if(out!=list)ownedColours.put(out,new OwnedColours(list,colour()));
        return out;
    }
    private static ColorStateList moveColours(ColorStateList list) {
        if (list == null) return null;
        try {
            // getColors and getStates exist from Android 10 but are not in the
            // jar this is compiled against, so they are asked for by name; a
            // phone that does not have them keeps its one colour
            int[] colours = (int[]) call(list, "getColors");
            int[][] states = (int[][]) call(list, "getStates");
            if (colours == null || states == null || colours.length == 0
                    || states.length < colours.length) {
                if (list.isStateful()) return list;
                int was = list.getDefaultColor();
                int now = sourced(was);
                return now == was ? list : ColorStateList.valueOf(now);
            }

            int[] out = new int[colours.length];
            int[][] kept = new int[colours.length][];
            boolean any = false;
            for (int i = 0; i < colours.length; i++) {
                kept[i] = states[i];
                out[i] = sourced(colours[i]);
                any |= out[i] != colours[i];
            }
            return any ? new ColorStateList(kept, out) : list;
        } catch (Throwable ignored) {
            return list;
        }
    }

    private static Object call(Object on, String name) {
        try {
            java.lang.reflect.Method method = on.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(on);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void setStatusBarColor(android.view.Window window, int colour) {
        window.setStatusBarColor(sourced(colour));
    }

    public static void setNavigationBarColor(android.view.Window window, int colour) {
        window.setNavigationBarColor(sourced(colour));
    }

    public static void setHintTextColor(TextView view, int colour) {
        view.setHintTextColor(swap(colour));
    }

    public static void setTint(Drawable drawable, int colour) {
        drawable.setTint(sourced(colour));
    }

    public static void setTintList(Drawable drawable,ColorStateList colours) {
        drawable.setTintList(moved(colours));
    }
    public static void setBackgroundTintList(View view,ColorStateList colours) {
        view.setBackgroundTintList(moved(colours));
    }
    public static void setColor(GradientDrawable shape,ColorStateList colours) {
        shape.setColor(moved(colours));
    }
    public static void setStroke(GradientDrawable shape,int width,int colour) {
        shape.setStroke(width,sourced(colour));
    }
    public static void setStroke(GradientDrawable shape,int width,ColorStateList colours) {
        shape.setStroke(width,moved(colours));
    }
    public static void setTextColor(TextView view,ColorStateList colours) {
        ColorStateList mapped=moved(colours);
        if (mapped!=null) {
            int fallback=mapped.getDefaultColor();int custom=AppearanceColors.forView(view,fallback);
            if (custom!=fallback) mapped=ColorStateList.valueOf(custom);
        }
        view.setTextColor(mapped);AppearanceColors.accentCreatorLabel(view);tintTextCursor(view);
    }
    public static void setHintTextColor(TextView view,ColorStateList colours) {
        view.setHintTextColor(moved(colours));
    }

    public static void setColors(GradientDrawable shape, int[] colours) {
        if (colours == null) {
            shape.setColors(null);
            return;
        }
        int[] out = new int[colours.length];
        for (int i = 0; i < colours.length; i++) out[i] = sourced(colours[i]);
        shape.setColors(out);
    }

    // ----------------------------------------------------- pictures by number

    /**
     * A picture asked for by number, which a texture pack may answer instead.
     *
     * Every one of these hands back what TikTok would have got when no pack is
     * on or the pack has nothing at that path -- which is nearly every call,
     * so the check has to be, and is, a flag and a lookup.
     */
    public static Drawable getDrawable(Context context, int id) {
        return moved(context.getDrawable(id));
    }

    public static Drawable getDrawable(Resources resources, int id) {
        return moved(resources.getDrawable(id));
    }

    public static Drawable getDrawable(Resources resources, int id, Resources.Theme theme) {
        return moved(resources.getDrawable(id, theme));
    }

    public static void setImageResource(ImageView view, int id) {
        {
            Drawable out = null;
            if (colour() != TIKTOK) {
                try {
                    Drawable original = view.getContext().getDrawable(id);
                    Drawable changed = moved(original);
                    if (changed != original) out = changed;
                } catch (Throwable ignored) { }
            }
            if (out != null) view.setImageDrawable(out);
            else view.setImageResource(id);
        }
        try {
            String name = Names.of(view.getResources(), id);
            boolean filledHeart = isLikeAsset(name)
                    && (name.contains("after") || name.contains("fill")
                    || name.contains("selected") || name.contains("active"));
            if (filledHeart) {
                synchronized (likeResourceSources) { likeResourceSources.put(view, id); }
                view.setColorFilter(LikeColors.colour(), PorterDuff.Mode.SRC_IN);
                ownedLikeTint.put(view, Boolean.TRUE);
            } else {
                synchronized (likeResourceSources) { likeResourceSources.remove(view); }
                if (Boolean.TRUE.equals(ownedLikeTint.remove(view))) view.clearColorFilter();
            }
        } catch (Throwable ignored) { }
    }

    private static final java.util.Map<ImageView, Boolean> ownedLikeTint =
            new java.util.WeakHashMap<ImageView, Boolean>();
    private static final java.util.Map<TuxIconView, Integer> likeIconSources =
            new java.util.WeakHashMap<TuxIconView, Integer>();
    private static final java.util.Map<ImageView, FilterSource> likeFilterSources =
            new java.util.WeakHashMap<ImageView, FilterSource>();
    private static final java.util.Map<ImageView, Integer> likeResourceSources =
            new java.util.WeakHashMap<ImageView, Integer>();
    private static final java.util.Map<View, String> likeAnimations =
            new java.util.WeakHashMap<View, String>();

    private static final class FilterSource {
        final int colour;
        final PorterDuff.Mode mode;
        FilterSource(int colour, PorterDuff.Mode mode) {
            this.colour = colour;
            this.mode = mode;
        }
    }

    public static void setImageTintList(ImageView view, ColorStateList colours) {
        if (view == null) return;
        if (LikeColors.isLikeView(view))
            AppearanceColors.rememberImageTint(view, colours);
        ColorStateList mapped = moved(colours);
        if (mapped != null) {
            int fallback = mapped.getDefaultColor();
            int chosen = AppearanceColors.forView(view, fallback);
            if (chosen != fallback) mapped = ColorStateList.valueOf(chosen);
        }
        view.setImageTintList(mapped);
    }

    /**
     * A file read as a stream, which is how an animation arrives.
     *
     * The heart that fills in when a video is liked is not a picture at all --
     * it is a Lottie animation, a json file describing how shapes move. Which
     * makes it the one thing in a texture pack that can be edited in a text
     * editor, so a pack gets to answer for these as well.
     */
    public static java.io.InputStream openRawResource(Resources resources, int id) {
        return recolourResource(resources, id, resources.openRawResource(id));
    }

    public static java.io.InputStream openRawResource(Resources resources, int id,
                                                       android.util.TypedValue value) {
        return recolourResource(resources, id, resources.openRawResource(id, value));
    }

    private static InputStream recolourResource(Resources resources, int id,
                                                 InputStream source) {
        boolean like = false;
        boolean doubleTap = false;
        try {
            String name = Names.of(resources, id);
            like = isLikeAsset(name);
            doubleTap = name.contains("double_tap") || name.contains("doubletap");
        } catch (Throwable ignored) { }
        return recolourLottie(source, like, doubleTap);
    }

    private static final int LOTTIE_MAX_BYTES = 1024 * 1024;
    private static final Pattern LOTTIE_PINK =
            Pattern.compile("(?i)#(?:[0-9a-f]{2})?fe2c55");
    private static final Pattern LOTTIE_COLOUR = Pattern.compile(
            "\"(k|s|e)\"\\s*:\\s*\\[\\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)\\s*,"
            + "\\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)\\s*,"
            + "\\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)\\s*,"
            + "\\s*(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)\\s*\\]");

    /**
     * The filled-like heart is a Lottie JSON file, so none of the normal
     * Paint, tint, or ColorStateList paths see its hexadecimal fill. Rewrite
     * only TikTok's accent literal and leave all other animation colours
     * alone. Large or non-JSON streams are replayed untouched.
     */
    private static InputStream recolourLottie(InputStream source, boolean like) {
        return recolourLottie(source, like, false);
    }

    private static InputStream recolourLottie(InputStream source, boolean like,
                                             boolean doubleTap) {
        return recolourLottie(source, like, doubleTap, false);
    }

    private static InputStream recolourLottie(InputStream source, boolean like,
                                             boolean doubleTap, boolean gradientsMapped) {
        if (like && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return source;
        ByteArrayOutputStream held = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        try {
            for (int read; (read = source.read(block)) != -1;) {
                held.write(block, 0, read);
                if (held.size() > LOTTIE_MAX_BYTES) {
                    return new SequenceInputStream(
                            new ByteArrayInputStream(held.toByteArray()), source);
                }
            }
        } catch (Throwable ignored) {
            return new SequenceInputStream(new ByteArrayInputStream(held.toByteArray()), source);
        }

        byte[] data = held.toByteArray();
        int first = 0;
        while (first < data.length && Character.isWhitespace((char) data[first])) first++;
        if (first == data.length || (data[first] != '{' && data[first] != '[')) {
            return new ByteArrayInputStream(data);
        }

        String json = new String(data, StandardCharsets.UTF_8);
        Matcher matches = LOTTIE_PINK.matcher(json);
        StringBuffer out = new StringBuffer();
        while (matches.find()) {
            String was = matches.group();
            int original = 0xFF000000
                    | (int) Long.parseLong(was.substring(was.length() - 6), 16);
            int now = like && LikeColors.colour() != TIKTOK
                    ? Palette.map(original, TIKTOK, LikeColors.colour()) : swap(original);
            String hex = String.format(Locale.US, "%06X", now & 0xFFFFFF);
            String replacement = was.length() == 9
                    ? "#" + was.substring(1, 3) + hex : "#" + hex;
            matches.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matches.appendTail(out);
        json = out.toString();
        if (like && LikeColors.colour() != TIKTOK) {
            Matcher colours = LOTTIE_COLOUR.matcher(json);
            out = new StringBuffer();
            while (colours.find()) {
                float red = Float.parseFloat(colours.group(2));
                float green = Float.parseFloat(colours.group(3));
                float blue = Float.parseFloat(colours.group(4));
                int original = 0xFF000000 | (Math.round(red * 255) << 16)
                        | (Math.round(green * 255) << 8) | Math.round(blue * 255);
                if (!Palette.captures(original, TIKTOK)
                        && !(doubleTap && red > 0.97f && green > 0.97f && blue > 0.97f))
                    continue;
                int now = doubleTap && red > 0.97f && green > 0.97f && blue > 0.97f
                        ? LikeColors.colour()
                        : Palette.map(original, TIKTOK, LikeColors.colour());
                String replacement = "\"" + colours.group(1) + "\":[" + number((now >> 16) & 255)
                        + "," + number((now >> 8) & 255) + "," + number(now & 255)
                        + "," + colours.group(5) + "]";
                colours.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
            colours.appendTail(out);
            json = out.toString();
            if (!gradientsMapped) json = recolourGradientFills(json, LikeColors.colour());
        }
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gradient fills and strokes of a Lottie heart (`ty` is `gf` or `gs`).
     *
     * Their colours are not `"k":[r,g,b,a]` like a flat fill: they sit in
     * `g.k.k` as a flat list of stops, four numbers each (position, r, g, b),
     * optionally followed by opacity stops, and can be animated as keyframes.
     * Only the stops that belong to TikTok's red family move, by the same
     * step as everything else; positions and opacity stops stay as they are.
     * Anything unexpected leaves the animation exactly as it came.
     */
    private static String recolourGradientFills(String json, int target) {
        if (!json.contains("\"gf\"") && !json.contains("\"gs\"")) return json;
        try {
            Object root = new org.json.JSONTokener(json).nextValue();
            int[] changed = {0};
            walkGradients(root, target, changed);
            return changed[0] == 0 ? json : root.toString();
        } catch (Throwable ignored) {
            return json;
        }
    }

    private static void walkGradients(Object node, int target, int[] changed)
            throws org.json.JSONException {
        if (node instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                Object child = array.opt(i);
                if (child instanceof org.json.JSONObject || child instanceof org.json.JSONArray)
                    walkGradients(child, target, changed);
            }
        } else if (node instanceof org.json.JSONObject) {
            org.json.JSONObject object = (org.json.JSONObject) node;
            String type = object.optString("ty", "");
            if (type.equals("gf") || type.equals("gs")) {
                org.json.JSONObject stops = object.optJSONObject("g");
                if (stops != null)
                    recolourStopProperty(stops.optJSONObject("k"), stops.optInt("p", 0),
                            target, changed);
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                Object child = object.opt(keys.next());
                if (child instanceof org.json.JSONObject || child instanceof org.json.JSONArray)
                    walkGradients(child, target, changed);
            }
        }
    }

    /** `{"a":0,"k":[stops]}` or `{"a":1,"k":[{"s":[stops],"e":[stops]}, ...]}`. */
    private static void recolourStopProperty(org.json.JSONObject property, int stops,
                                             int target, int[] changed)
            throws org.json.JSONException {
        if (property == null) return;
        org.json.JSONArray values = property.optJSONArray("k");
        if (values == null || values.length() == 0) return;
        if (values.opt(0) instanceof org.json.JSONObject) {
            for (int i = 0; i < values.length(); i++) {
                org.json.JSONObject frame = values.optJSONObject(i);
                if (frame == null) continue;
                recolourStopList(frame.optJSONArray("s"), stops, target, changed);
                recolourStopList(frame.optJSONArray("e"), stops, target, changed);
            }
        } else {
            recolourStopList(values, stops, target, changed);
        }
    }

    private static void recolourStopList(org.json.JSONArray list, int stops, int target,
                                         int[] changed) throws org.json.JSONException {
        if (list == null) return;
        int end = stops > 0 ? Math.min(stops * 4, list.length()) : list.length() / 4 * 4;
        // normally 0..1; a few exporters write 0..255, which is told by size
        double top = 0;
        for (int i = 0; i + 3 < end; i += 4)
            for (int c = 1; c <= 3; c++) top = Math.max(top, list.optDouble(i + c, 0));
        double scale = top > 1.0001 ? 1.0 : 255.0;
        for (int i = 0; i + 3 < end; i += 4) {
            int original = 0xFF000000
                    | (clampChannel(list.optDouble(i + 1, 0) * (scale == 1.0 ? 1 : 255)) << 16)
                    | (clampChannel(list.optDouble(i + 2, 0) * (scale == 1.0 ? 1 : 255)) << 8)
                    | clampChannel(list.optDouble(i + 3, 0) * (scale == 1.0 ? 1 : 255));
            if (!Palette.captures(original, TIKTOK)) continue;
            int now = Palette.map(original, TIKTOK, target);
            if (now == original) continue;
            list.put(i + 1, ((now >> 16) & 255) / scale);
            list.put(i + 2, ((now >> 8) & 255) / scale);
            list.put(i + 3, (now & 255) / scale);
            changed[0]++;
        }
    }

    private static int clampChannel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static String number(int channel) {
        return String.format(Locale.US, "%.8f", channel / 255f);
    }

    public static java.io.InputStream open(android.content.res.AssetManager assets,
                                           String name) throws java.io.IOException {
        return finishAsset(assets.open(name), name);
    }

    public static java.io.InputStream open(android.content.res.AssetManager assets,
                                           String name, int accessMode)
            throws java.io.IOException {
        return finishAsset(assets.open(name, accessMode), name);
    }

    private static InputStream finishAsset(InputStream source, String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        boolean like = isLikeAsset(lower);
        if (lower.endsWith(".json") || lower.endsWith(".lottie"))
            return recolourLottie(source, like,
                    lower.contains("double_tap") || lower.contains("doubletap"));
        if (like && LikeColors.colour() != TIKTOK && lower.endsWith(".png"))
            return recolourLikePng(source);
        return source;
    }

    /**
     * TikTok 47.2.41 passes feed-heart filenames straight to its Lottie view;
     * neither Resources.openRawResource nor AssetManager.open is on that
     * call path. Recolour those small JSON files before Lottie's parser runs.
     */
    public static void setAnimation(Object animation, String name) {
        if (animation == null) return;
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (animation instanceof View && isFeedLikeAnimation(lower)) {
            ((View)animation).setTag(0x54544C48,Boolean.TRUE);
            synchronized (likeAnimations) { likeAnimations.put((View) animation, name); }
            rememberLikeName(name);
            try {
                // Already recoloured in the background (or on an earlier
                // load): nothing is parsed, decoded or encoded here.
                final Context context = ((View) animation).getContext();
                String json = likeAnimationReady(context, name);
                if (json == null) {
                    // Not ready for this colour yet (a custom colour just
                    // picked). Never build it here: show the last colour of
                    // the same heart right away and swap when it is ready.
                    scheduleLikeBuild(context, (View) animation, name);
                    // A previous accent would flash the wrong colour. Keep the
                    // original composition until the current one is ready.
                    json = shippedLikeJson(context, lower, TIKTOK);
                    if (json == null) {
                        setAnimationNormally(animation, name);
                        return;
                    }
                }
                java.lang.reflect.Method fromJson = animation.getClass().getMethod(
                        "setAnimationFromJson", String.class, String.class);
                String cacheKey = "margyt:" + lower + ":"
                        + Integer.toHexString(LikeColors.colour()) + ":"
                        + Integer.toHexString(json.hashCode());
                fromJson.invoke(animation, json, cacheKey);
                return;
            } catch (Throwable error) {
                Diary.note("feed like animation: " + error);
            }
        }
        setAnimationNormally(animation, name);
    }

    // ---------------------------------------------------------------------
    // Pre-recoloured like animations.
    //
    // Recolouring a heart means parsing its Lottie JSON, decoding every frame
    // PNG, repainting it, encoding it again and wrapping it in base64. Doing
    // that on the UI thread each time a video binds made the feed stutter.
    // Now each (animation, colour) pair is recoloured once, off the UI
    // thread, as soon as the colour is chosen or the app starts, and kept.
    // ---------------------------------------------------------------------

    private static final String LIKE_NAMES_KEY = "like_animation_names";
    private static final java.util.concurrent.ConcurrentHashMap<String, String> likeJson =
            new java.util.concurrent.ConcurrentHashMap<String, String>();
    private static final java.util.Set<String> likeNames =
            java.util.Collections.newSetFromMap(
                    new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private static final java.util.concurrent.ThreadPoolExecutor likeWorker =
            new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<Runnable>(),
                    new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable job) {
                    Thread thread = new Thread(job, "margyt-like-prewarm");
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private static volatile boolean likeNamesLoaded;
    private static final android.os.Handler likeHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static volatile Runnable pendingLikePrewarm;
    private static volatile long likeGeneration;
    private static final java.util.Set<String> buildingLike =
            java.util.Collections.newSetFromMap(
                    new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private static String likeKey(String lower) {
        return RecolourCache.likeKey(lower, LikeColors.colour());
    }

    /**
     * Only what is already made: memory, the apk, the disk. Cheap enough for
     * the UI thread; returns null when the colour still has to be built.
     */
    private static String likeAnimationReady(Context context, String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String key = likeKey(lower);
        String ready = likeJson.get(key);
        if (ready != null) return ready;
        ready = shippedLikeJson(context, lower, LikeColors.colour());
        // Disk IO belongs to likeWorker, never to the feed/UI thread.
        if (ready != null) likeJson.put(key, ready);
        return ready;
    }

    /** Build one heart off the UI thread, then put it on the view that asked. */
    private static void scheduleLikeBuild(final Context context, final View view,
                                          final String name) {
        final int colour = LikeColors.colour();
        final String key = (name == null ? "" : name.toLowerCase(Locale.ROOT))
                + ":" + Integer.toHexString(colour & 0xFFFFFF);
        final long generation = likeGeneration;
        final String jobKey = generation + ":" + key;
        if (!buildingLike.add(jobKey)) return;
        try {
            likeWorker.execute(new Runnable() {
                @Override public void run() {
                    try { if (generation == likeGeneration) likeAnimationJson(context, name); }
                    catch (Throwable error) { Diary.note("like build: " + error); }
                    finally { buildingLike.remove(jobKey); }
                    if (generation != likeGeneration || colour != LikeColors.colour()) return;
                    view.post(new Runnable() {
                        @Override public void run() {
                            if (generation != likeGeneration || colour != LikeColors.colour()) return;
                            java.util.Map<View, String> waiting;
                            synchronized (likeAnimations) {
                                waiting = new java.util.WeakHashMap<View, String>(likeAnimations);
                            }
                            for (java.util.Map.Entry<View, String> item : waiting.entrySet())
                                if (item.getKey() != null && item.getKey().isAttachedToWindow()
                                        && name.equals(item.getValue())) refreshPreparedAnimation(item.getKey(), name);
                        }
                    });
                }
            });
        } catch (Throwable ignored) { buildingLike.remove(jobKey); }
    }

    private static void refreshPreparedAnimation(View view, String name) {
        float progress = 0f; boolean playing = false;
        try {
            progress = ((Number)view.getClass().getMethod("getProgress").invoke(view)).floatValue();
            playing = Boolean.TRUE.equals(view.getClass().getMethod("isAnimating").invoke(view));
        } catch (Throwable ignored) { }
        setAnimation(view, name);
        try {
            view.getClass().getMethod("setProgress", float.class).invoke(view, progress);
            if (playing) view.getClass().getMethod("resumeAnimation").invoke(view);
        } catch (Throwable ignored) { }
    }

    /** The finished, recoloured JSON for the current colour; built if needed (worker thread). */
    private static String likeAnimationJson(Context context, String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        final long generation = likeGeneration;
        String key = likeKey(lower);
        String ready = likeJson.get(key);
        if (ready != null) return ready;
        // Recoloured while the apk was built (margyt/prerecolour.py): ready
        // on the very first launch, for TikTok's pink and the palette.
        ready = shippedLikeJson(context, lower, LikeColors.colour());
        if (ready != null) {
            likeJson.put(key, ready);
            return ready;
        }
        // Recoloured on an earlier run: read it back instead of redoing it.
        String diskKey = "lottie|" + key;
        byte[] saved = RecolourCache.get(diskKey);
        if (saved != null && saved != RecolourCache.NONE) {
            ready = new String(saved, StandardCharsets.UTF_8);
            if (generation != likeGeneration || !key.equals(likeKey(lower))) return null;
            likeJson.put(key, ready);
            return ready;
        }
        String built = buildLikeAnimationJson(context, name, lower);
        if (built != null && generation == likeGeneration && key.equals(likeKey(lower))) {
            likeJson.put(key, built);
            RecolourCache.put(diskKey, built.getBytes(StandardCharsets.UTF_8));
        }
        return built;
    }

    /** The animation recoloured at build time for the current colour, or null. */
    private static String shippedLikeJson(Context context, String lower, int colour) {
        InputStream in = null;
        try {
            String path = "margyt/rc/"
                    + String.format(Locale.US, "%06X", colour & 0xFFFFFF) + "/"
                    + RecolourCache.hash(lower.getBytes(StandardCharsets.UTF_8)).substring(0, 16)
                    + ".json";
            in = context.getAssets().open(path);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (int read; (read = in.read(block)) != -1;) bytes.write(block, 0, read);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable notShipped) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    private static final java.util.Map<String, org.json.JSONObject> likeTemplates =
            new java.util.HashMap<String, org.json.JSONObject>();

    private static Object copyJson(Object value) throws org.json.JSONException {
        if (value instanceof org.json.JSONObject) {
            org.json.JSONObject copy = new org.json.JSONObject();
            org.json.JSONObject source = (org.json.JSONObject) value;
            java.util.Iterator<String> keys = source.keys();
            while (keys.hasNext()) { String key = keys.next(); copy.put(key, copyJson(source.get(key))); }
            return copy;
        }
        if (value instanceof org.json.JSONArray) {
            org.json.JSONArray source = (org.json.JSONArray) value, copy = new org.json.JSONArray();
            for (int i = 0; i < source.length(); i++) copy.put(copyJson(source.get(i)));
            return copy;
        }
        return value;
    }

    private static String buildLikeAnimationJson(Context context, String name, String lower) {
        try {
            long generation = likeGeneration;
            org.json.JSONObject template;
            synchronized (likeTemplates) { template = likeTemplates.get(name); }
            if (template == null) {
                InputStream source = context.getAssets().open(name);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try {
                    byte[] block = new byte[8192];
                    for (int read; (read = source.read(block)) != -1;) {
                        if (bytes.size() + read > LOTTIE_MAX_BYTES) return null;
                        bytes.write(block, 0, read);
                    }
                } finally { source.close(); }
                String json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                if (json.startsWith("\uFEFF")) json = json.substring(1);
                template = new org.json.JSONObject(json);
                synchronized (likeTemplates) { if (generation != likeGeneration) return null; likeTemplates.put(name, template); }
            }
            org.json.JSONObject composition = (org.json.JSONObject) copyJson(template);
            if (LikeColors.colour() != TIKTOK) walkGradients(composition, LikeColors.colour(), new int[]{0});
            String embedded = embedFeedLikeImages(composition, context);
            InputStream coloured = recolourLottie(new ByteArrayInputStream(embedded.getBytes(StandardCharsets.UTF_8)),
                    true, lower.contains("double_tap") || lower.contains("doubletap"), true);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            try { byte[] block = new byte[8192]; for (int n; (n = coloured.read(block)) != -1;) result.write(block, 0, n); }
            finally { coloured.close(); }
            return new String(result.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable error) {
            Diary.note("feed like animation: " + error);
            return null;
        }
    }

    private static void rememberLikeName(String name) {
        if (name == null || !likeNames.add(name)) return;
        try {
            Context context = Margy.context();
            if (context == null) return;
            AccountAppearance.prefs(context).edit()
                    .putStringSet(LIKE_NAMES_KEY, new java.util.HashSet<String>(likeNames))
                    .apply();
        } catch (Throwable ignored) { }
    }

    private static void loadLikeNames() {
        if (likeNamesLoaded) return;
        likeNamesLoaded = true;
        try {
            Context context = Margy.context();
            if (context == null) { likeNamesLoaded = false; return; }
            java.util.Set<String> saved = AccountAppearance.prefs(context)
                    .getStringSet(LIKE_NAMES_KEY, null);
            if (saved != null) likeNames.addAll(saved);
        } catch (Throwable ignored) { }
    }

    /** Every like animation TikTok ships, so none waits to be seen once before it is prepared. */
    private static void discoverLikeNames(Context context) {
        try {
            String[] files = context.getAssets().list("");
            if (files == null) return;
            for (String file : files)
                if (file != null && isFeedLikeAnimation(file.toLowerCase(Locale.ROOT)))
                    likeNames.add(file);
        } catch (Throwable ignored) { }
    }

    /** Drop everything recoloured: a texture pack or the source art changed. */
    static void dropLikeCache() {
        likeGeneration++;
        Runnable pending = pendingLikePrewarm;
        if (pending != null) likeHandler.removeCallbacks(pending);
        synchronized (likeTemplates) { likeTemplates.clear(); }
        synchronized (likeFrames) { likeFrames.clear(); }
        likeJson.clear();
        likeWorker.getQueue().clear();
        buildingLike.clear();
    }

    /**
     * Recolour every known like animation for the current colour in the
     * background. {@code then} (may be null) runs on the worker once all are
     * ready, so views can be refreshed without any work on the UI thread.
     */
    public static void prewarmLikeAnimations(final Runnable then) {
        final Context context = Margy.context();
        if (context == null) { if (then != null) then.run(); return; }
        final int colour = LikeColors.colour();
        final long generation = ++likeGeneration;
        Runnable earlier = pendingLikePrewarm;
        if (earlier != null) likeHandler.removeCallbacks(earlier);
        likeWorker.getQueue().clear();
        buildingLike.clear();
        Runnable launch = new Runnable() { @Override public void run() {
        if (generation != likeGeneration || colour != LikeColors.colour()) return;
        likeWorker.execute(new Runnable() {
            @Override public void run() {
                try {
                    loadLikeNames();
                    discoverLikeNames(context);
                    // Stale colours are never asked for again; keep memory small.
                    String suffix = ":" + Integer.toHexString(colour & 0xFFFFFF)
                            + ":" + RecolourCache.ALGORITHM_VERSION;
                    for (String key : new java.util.ArrayList<String>(likeJson.keySet()))
                        if (!key.endsWith(suffix)) likeJson.remove(key);
                    for (String name : new java.util.ArrayList<String>(likeNames)) {
                        if (generation != likeGeneration || colour != LikeColors.colour()) break;
                        likeAnimationJson(context, name);
                    }
                } catch (Throwable error) {
                    Diary.note("like prewarm: " + error);
                }
                if (generation == likeGeneration && colour == LikeColors.colour() && then != null) then.run();
            }
        });
        }};
        pendingLikePrewarm = launch;
        likeHandler.postDelayed(launch, 300L);
    }

    private static boolean isFeedLikeAnimation(String name) {
        return name.endsWith(".json") && isLikeAsset(name);
    }

    /**
     * Lottie reads embedded PNGs from its composition, bypassing Android's
     * normal drawable tint hooks. Inline only TikTok's feed-heart images after
     * recolouring them, so the idle and double-tap hearts use the same accent.
     */
    private static String embedFeedLikeImages(org.json.JSONObject composition, Context context) {
        String json = composition.toString();
        try {
            org.json.JSONArray images = composition.optJSONArray("assets");
            if (images == null) return json;
            boolean changed = false;
            for (int i = 0; i < images.length(); i++) {
                org.json.JSONObject image = images.optJSONObject(i);
                if (image == null) continue;
                String file = image.optString("p", "");
                // Every picture inside a like composition belongs to the heart.
                // Frames are often named img_0.png, so the name proves nothing.
                if (!file.toLowerCase(Locale.ROOT).endsWith(".png")
                        || file.startsWith("data:")) continue;
                String path = image.optString("u", "") + file;
                InputStream source = null;
                InputStream tinted = null;
                try {
                    source = context.getAssets().open(path);
                    tinted = LikeColors.colour() == TIKTOK ? source : recolourLikePng(source);
                    if (tinted != source) {
                        try { source.close(); } catch (Throwable ignoredClose) { }
                        source = null;
                    }
                    ByteArrayOutputStream png = new ByteArrayOutputStream();
                    byte[] block = new byte[8192];
                    for (int read; (read = tinted.read(block)) != -1;) {
                        if (png.size() + read > 2 * 1024 * 1024) {
                            png.reset();
                            break;
                        }
                        png.write(block, 0, read);
                    }
                    if (png.size() == 0) continue;
                    String encoded = android.util.Base64.encodeToString(
                            png.toByteArray(), android.util.Base64.NO_WRAP);
                    image.put("u", "");
                    image.put("p", "data:image/png;base64," + encoded);
                    image.put("e", 1);
                    changed = true;
                } catch (Throwable ignored) {
                    // A missing image should not prevent the JSON animation
                    // from loading; close both streams below and keep it local.
                } finally {
                    if (tinted != null) try { tinted.close(); } catch (Throwable ignoredClose) { }
                    if (source != null) try { source.close(); } catch (Throwable ignoredClose) { }
                }
            }
            return changed ? composition.toString() : json;
        } catch (Throwable error) {
            Diary.note("feed like images: " + error);
            return json;
        }
    }

    private static void setAnimationNormally(Object animation, String name) {
        try {
            animation.getClass().getMethod("setAnimation", String.class)
                    .invoke(animation, name);
        } catch (Throwable error) {
            Diary.note("feed animation fallback: " + error);
        }
    }

    private static boolean isLikeAsset(String name) {
        if (name.contains("dislike")) return false;
        return name.contains("like") || name.contains("heart") || name.contains("digg")
                || name.contains("double_tap") || name.contains("doubletap");
    }

    private static final class LikeFrame {
        final int width, height;
        final int[] pixels;
        LikeFrame(int width, int height, int[] pixels) {
            this.width = width; this.height = height; this.pixels = pixels;
        }
    }
    private static final java.util.LinkedHashMap<String, LikeFrame> likeFrames =
            new java.util.LinkedHashMap<String, LikeFrame>(32, .75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, LikeFrame> entry) {
                    return size() > 48;
                }
            };

    /** Recolour the PNG assets used by TikTok's feed heart buttons. */
    private static InputStream recolourLikePng(InputStream source) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            for (int read; (read = source.read(block)) != -1;) {
                if (bytes.size() + read > 2 * 1024 * 1024) {
                    return new SequenceInputStream(
                            new ByteArrayInputStream(bytes.toByteArray()), source);
                }
                bytes.write(block, 0, read);
            }
            byte[] raw = bytes.toByteArray();
            long generation = likeGeneration;
            String sourceKey = RecolourCache.hash(raw);
            String diskKey = "png|" + Integer.toHexString(LikeColors.colour() & 0xFFFFFF)
                    + "|" + RecolourCache.ALGORITHM_VERSION + "|" + sourceKey;
            byte[] saved = RecolourCache.get(diskKey);
            if (saved != null && saved != RecolourCache.NONE)
                return new ByteArrayInputStream(saved);
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                return new ByteArrayInputStream(raw);
            LikeFrame frame;
            synchronized (likeFrames) { frame = likeFrames.get(sourceKey); }
            if (frame == null) {
                Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                if (bitmap == null) return new ByteArrayInputStream(raw);
                int[] original = new int[bitmap.getWidth() * bitmap.getHeight()];
                bitmap.getPixels(original, 0, bitmap.getWidth(), 0, 0,
                        bitmap.getWidth(), bitmap.getHeight());
                frame = new LikeFrame(bitmap.getWidth(), bitmap.getHeight(), original);
                bitmap.recycle();
                synchronized (likeFrames) { likeFrames.put(sourceKey, frame); }
            }
            int width = frame.width, height = frame.height;
            int[] pixels = frame.pixels.clone();
            int target = LikeColors.colour();
            java.util.HashMap<Integer, Integer> memo = new java.util.HashMap<Integer, Integer>();
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                if ((pixel >>> 24) == 0) continue;
                Integer known = memo.get(pixel);
                if (known == null) {
                    known = Palette.map(pixel, TIKTOK, target);
                    memo.put(pixel, known);
                }
                pixels[i] = known;
            }
            Bitmap recoloured = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            recoloured.setPixels(pixels, 0, width, 0, 0, width, height);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            recoloured.compress(Bitmap.CompressFormat.PNG, 100, encoded);
            recoloured.recycle();
            if (generation == likeGeneration && target == LikeColors.colour())
                RecolourCache.put(diskKey, encoded.toByteArray());
            return new ByteArrayInputStream(encoded.toByteArray());
        } catch (Throwable ignored) {
            return source;
        }
    }

    /**
     * A view being faded by TikTok itself.
     *
     * The overlay fades in and out constantly -- a video pauses, a panel opens
     * -- and every one of those set the brightness back to what TikTok wanted,
     * undoing the anti burn-in. So the two are combined rather than fighting:
     * whatever TikTok asks for is multiplied by how far down the setting says
     * that view should be.
     */
    public static void setAlpha(View view, float alpha) {
        long probe = Probe.start();
        float asked = Dim.alphaFor(view, alpha);
        Probe.stop(Probe.ALPHA, probe);
        view.setAlpha(asked);
    }

    public static void setColor(Paint paint, int colour) {
        long probe = Probe.start();
        int mapped = sourced(colour);
        Probe.stop(Probe.PAINT, probe);
        paint.setColor(mapped);
    }

    public static void setColor(GradientDrawable shape, int colour) {
        shape.setColor(sourced(colour));
    }

    public static void setColor(TuxIconView icon, int colour) {
        if (icon != null && (LikeColors.isLikeView(icon)
                || Palette.captures(colour, TIKTOK))) {
            synchronized (likeIconSources) { likeIconSources.put(icon, colour); }
        }
        paintIcon(icon, colour);
    }

    public static void setColorFilter(ImageView view, int colour) {
        AppearanceColors.rememberImageFilter(view);
        rememberLikeFilter(view, colour, null);
        paintColorFilter(view, colour, null);
    }

    public static void setColorFilter(ImageView view, int colour, PorterDuff.Mode mode) {
        AppearanceColors.rememberImageFilter(view);
        rememberLikeFilter(view, colour, mode);
        paintColorFilter(view, colour, mode);
    }

    private static void rememberLikeFilter(ImageView view, int colour, PorterDuff.Mode mode) {
        if (view == null || (!LikeColors.isLikeView(view)
                && !Palette.captures(colour, TIKTOK))) return;
        synchronized (likeFilterSources) {
            likeFilterSources.put(view, new FilterSource(colour, mode));
        }
    }

    private static void paintIcon(TuxIconView icon, int colour) {
        if (icon == null) return;
        int mapped = LikeColors.forView(icon, colour, swap(colour));
        icon.setColor(AppearanceColors.forView(icon, mapped));
        LikeColors.syncCount(icon, LikeColors.liked(colour));
    }

    private static void paintColorFilter(ImageView view, int colour, PorterDuff.Mode mode) {
        if (view == null) return;
        int mapped = LikeColors.forView(view, colour, swap(colour));
        int chosen = AppearanceColors.forView(view, mapped);
        if (mode == null) view.setColorFilter(chosen);
        else view.setColorFilter(chosen, mode);
        LikeColors.syncCount(view, LikeColors.liked(colour));
    }

    /** Repaint visible likes after their chosen accent changes. */
    public static void refreshLikeViews() {
        Heart.refresh();
        synchronized(textSources){for(java.util.Map.Entry<TextView,Integer> entry:new java.util.WeakHashMap<TextView,Integer>(textSources).entrySet()){
            TextView view=entry.getKey();Integer source=entry.getValue();if(view!=null && source!=null && view.isAttachedToWindow())view.post(()->setTextColor(view,source));}}
        synchronized(tintSources){for(java.util.Map.Entry<TuxIconView,Integer> entry:new java.util.WeakHashMap<TuxIconView,Integer>(tintSources).entrySet()){
            TuxIconView view=entry.getKey();Integer source=entry.getValue();if(view!=null && source!=null && view.isAttachedToWindow())view.post(()->setTintColor(view,source));}}
        final java.util.Map<TuxIconView, Integer> icons;
        final java.util.Map<ImageView, FilterSource> filters;
        final java.util.Map<ImageView, Integer> resources;
        final java.util.Map<View, String> animations;
        synchronized (likeIconSources) {
            icons = new java.util.WeakHashMap<TuxIconView, Integer>(likeIconSources);
        }
        synchronized (likeFilterSources) {
            filters = new java.util.WeakHashMap<ImageView, FilterSource>(likeFilterSources);
        }
        synchronized (likeResourceSources) {
            resources = new java.util.WeakHashMap<ImageView, Integer>(likeResourceSources);
        }
        synchronized (likeAnimations) {
            animations = new java.util.WeakHashMap<View, String>(likeAnimations);
        }
        for (final java.util.Map.Entry<TuxIconView, Integer> entry : icons.entrySet()) {
            final TuxIconView view = entry.getKey();
            final Integer source = entry.getValue();
            if (view != null && source != null) view.post(() -> paintIcon(view, source));
        }
        for (final java.util.Map.Entry<ImageView, FilterSource> entry : filters.entrySet()) {
            final ImageView view = entry.getKey();
            final FilterSource source = entry.getValue();
            if (view != null && source != null)
                view.post(() -> paintColorFilter(view, source.colour, source.mode));
        }
        for (final java.util.Map.Entry<ImageView, Integer> entry : resources.entrySet()) {
            final ImageView view = entry.getKey();
            final Integer source = entry.getValue();
            if (view != null && source != null) view.post(() -> setImageResource(view, source));
        }
        if (!animations.isEmpty() || !likeNames.isEmpty() || !likeNamesLoaded) {
            final java.util.Map<View, String> pending = animations;
            prewarmLikeAnimations(new Runnable() {
                @Override public void run() {
                    for (final java.util.Map.Entry<View, String> entry : pending.entrySet()) {
                        final View view = entry.getKey();
                        final String source = entry.getValue();
                        if (view != null && source != null && view.isAttachedToWindow())
                            view.post(() -> {
                                synchronized (likeAnimations) {
                                    if (!source.equals(likeAnimations.get(view))) return;
                                }
                                refreshPreparedAnimation(view, source);
                            });
                    }
                }
            });
        }
    }

    // Native in-app message banners inflate their reply label from a style.
    // Keep disabled/neutral replies untouched; the active action uses the exact accent.
    private static void accentReply(TextView view){
        if(view==null)return;
        CharSequence text=view.getText();
        if(text==null)return;
        String label=text.toString().trim();
        if(!label.equalsIgnoreCase("Ответить") && !label.equalsIgnoreCase("Reply")
                && !label.equalsIgnoreCase("Відповісти"))return;
        int current=view.getCurrentTextColor();Integer source;
        synchronized(textSources){source=textSources.get(view);}
        int original=source==null?current:source;
        if(!Palette.captures(original,TIKTOK))return;
        synchronized(textSources){textSources.put(view,original);}
        view.setTextColor((original&0xff000000)|(accent()&0xffffff));
    }

    /**
     * The colour a number stands for, where TikTok gives a colour by number.
     *
     * The number is nearly always an attribute of the theme -- "primary
     * text", "secondary icon" -- and only the theme knows what colour that is
     * today. It used to be read here as a colour resource, which it is not:
     * the read failed, the failure was written in the diary several times a
     * second, and the label or icon was left the colour its layout gave it,
     * which on a dark sheet is black on nearly black. TikTok's own reading is
     * the one done here now, and a plain colour resource is still tried
     * after it, for the few callers that do pass one.
     */
    static Integer themed(android.content.Context context, int number) {
        if (context == null || number == 0) return null;
        try {
            android.util.TypedValue value = new android.util.TypedValue();
            if (context.getTheme().resolveAttribute(number, value, true)) {
                if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                        && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                    return Integer.valueOf(value.data);
                }
                if (value.resourceId != 0) {
                    return Integer.valueOf(context.getResources().getColor(value.resourceId, context.getTheme()));
                }
                return null;
            }
        } catch (Throwable ignored) { }
        try {
            return Integer.valueOf(context.getResources().getColor(number, context.getTheme()));
        } catch (Throwable notAColour) {
            return null;
        }
    }

    /** What colour TikTok asked for on a view, before any of this changed it. A test build's question. */
    static String sourceOf(TextView view){
        Integer source;
        synchronized(textSources){source=textSources.get(view);}
        return source==null?"unknown":Integer.toHexString(source);
    }

    public static void setTextColorRes(TextView view,int resource){
        if(view==null||resource==0)return;
        // nothing it stands for: TikTok leaves the view as it is, and so does this
        Integer colour=themed(view.getContext(),resource);
        if(colour!=null)setTextColor(view,colour.intValue());
    }

    public static void setTextColor(TextView view, int colour) {
        synchronized(textSources){textSources.put(view,sourceColour(colour));}
        view.setTextColor(AppearanceColors.forView(view, sourced(colour)));
        AppearanceColors.accentCreatorLabel(view);
        accentReply(view);
        tintTextCursor(view);
    }

    public static void setBackgroundColor(View view, int colour) {
        int fallback = sourced(colour);
        int mapped = AppearanceColors.forView(view, fallback);
        Drawable background = view.getBackground();
        if (mapped != fallback && background instanceof GradientDrawable) {
            Drawable copy = background.getConstantState() == null ? background.mutate()
                    : background.getConstantState().newDrawable().mutate();
            if (copy instanceof GradientDrawable) {
                ((GradientDrawable) copy).setColor(mapped);
                view.setBackground(copy);
                return;
            }
        }
        if (mapped != fallback && background instanceof ColorDrawable) {
            ((ColorDrawable) background.mutate()).setColor(mapped);
            return;
        }
        view.setBackgroundColor(mapped);
    }

    static void tintTextCursor(TextView view) {
        if (!(view instanceof android.widget.EditText)) return;
        String[] getters = {"getTextCursorDrawable", "getTextSelectHandle",
                "getTextSelectHandleLeft", "getTextSelectHandleRight"};
        for (String getter : getters) {
            try {
                java.lang.reflect.Method method = TextView.class.getMethod(getter);
                Object result = method.invoke(view);
                if (result instanceof Drawable) {
                    Drawable drawable = ((Drawable) result).mutate();
                    drawable.setColorFilter(Accent.colour(), PorterDuff.Mode.SRC_IN);
                }
            } catch (Throwable ignored) { }
        }
    }

    public static ColorStateList valueOf(int colour) {
        ColorStateList original=ColorStateList.valueOf(sourceColour(colour));
        ColorStateList out=ColorStateList.valueOf(sourced(colour));
        if(out.getDefaultColor()!=original.getDefaultColor())ownedColours.put(out,new OwnedColours(original,colour()));
        return out;
    }

    public static void setTintColorRes(TuxIconView view,int resource){
        if(view==null)return;
        Integer colour=themed(view.getContext(),resource);
        if(colour!=null)setTintColor(view,colour.intValue());
    }
    /** Native 47.2.41 reaction controller: only the filled, selected dislike icon. */
    public static void dislike(Object controller){
        try{Object icon=controller.getClass().getField("LLJLLL").get(controller);
            if(icon instanceof TuxIconView)((TuxIconView)icon).setTintColor(accent());
        }catch(Throwable ignored){}
    }
    static int loadingDotTint(int colour){return Palette.map(colour,TIKTOK,colour());}
    private static final java.util.Map<TextView,Integer> textSources=new java.util.WeakHashMap<>();
    private static final java.util.Map<TuxIconView,Integer> tintSources=new java.util.WeakHashMap<>();
    private static int sourceColour(int value){return (value&0xffffff)==(colour()&0xffffff)?(value&0xff000000)|(TIKTOK&0xffffff):value;}
    public static void setTintColor(TuxIconView view,int colour){if(view==null)return;if(Boolean.TRUE.equals(view.getTag(0x54544C48)))Heart.setTintColor(view,colour);else {synchronized(tintSources){tintSources.put(view,sourceColour(colour));}view.setTintColor(AppearanceColors.loadingDots(view)?loadingDotTint(colour):sourced(colour));}}
    public static int getColor(Context context, int id) {
        return sourced(context.getColor(id));
    }

    public static int getColor(Resources resources, int id) {
        return sourced(resources.getColor(id));
    }

    public static int getColor(Resources resources, int id, Resources.Theme theme) {
        return sourced(resources.getColor(id, theme));
    }

    public static int getColor(TypedArray array, int index, int fallback) {
        return sourced(array.getColor(index, fallback));
    }

    /**
     * A colour that came out of the resources rather than out of arithmetic.
     *
     * This is where TikTok's theme colours actually enter the app -- a theme
     * attribute or a colour resource -- so a colour arriving here is known to
     * be the theme's and the whole list applies to it. The accent goes first;
     * it only ever claims its own family, and what it leaves is offered to the
     * theme.
     */
    private static int sourced(int colour) {
        int rgb=colour & 0xFFFFFF;
        // Native secondary accent colours; never used to transform media pixels.
        if(rgb==0x25F4EE||rgb==0x20D5EC||rgb==0x00C5CD)return (colour & 0xFF000000)|(accent() & 0xFFFFFF);
        // the rest of the cyan and the "online" green: a chosen accent takes them too
        if(colour()!=TIKTOK && Palette.secondary(colour))return Palette.toAccent(colour,accent());
        int moved = swap(colour);
        if (moved != colour) return moved;
        return Themes.recolour(colour, true);
    }
}
