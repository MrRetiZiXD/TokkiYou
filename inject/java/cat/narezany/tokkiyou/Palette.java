package cat.narezany.tokkiyou;

import android.graphics.Color;

/**
 * Which colours the accent takes over, and what it turns them into.
 *
 * The same arithmetic as `tokkiyou/palette.py`, which does this to the resource
 * table and the compiled XML while the apk is built. This one is for the
 * colours that are only known while the app is running -- whatever comes back
 * from `Resources.getColor`, from a `TypedArray`, or out of a constant the
 * bytecode patch redirected here.
 *
 * The two have to agree: a build bakes the resources with the python and the
 * app moves the rest with this, and a colour that came out mint in one and
 * something else in the other would show as a seam down the middle of a screen.
 *
 * TikTok is not built around a single pink. `#FE2C55` is the one in the brand
 * guide, but the app draws it at a dozen opacities and surrounds it with a
 * family -- the magentas of the gradients, the reds of Shop and LIVE. So the
 * rule is a zone around the reference hue, and the move is relative: whatever
 * step takes the reference to the accent is applied to every member, so the
 * reference lands exactly on the accent and a lighter member stays lighter.
 * Alpha is never touched.
 */
public final class Palette {

    private Palette() {}

    /** How far from the reference hue still counts as the family, in degrees. */
    public static final float HUE = 20f;

    /** Below these there is no hue worth moving: greys, near-black, near-white. */
    public static final float MIN_SATURATION = 0.35f;
    public static final float MIN_VALUE = 0.35f;

    public static boolean captures(int colour, int reference) {
        // the reference itself is always in the family, at any opacity and
        // whatever it is: the thresholds are there to keep unrelated greys from
        // being dragged along with the neighbours
        if ((colour & 0xFFFFFF) == (reference & 0xFFFFFF)) return true;

        float[] here = hsv(colour);
        // Native hot/pastel pink UI sits outside the older red-only hue zone.
        if ((reference & 0xFFFFFF)==0xFE2C55 && here[1]>=0.06f && here[2]>=0.25f
                && (here[0]>=300f || here[0]<=10f)) return true;
        if (here[1] < MIN_SATURATION || here[2] < MIN_VALUE) return false;
        float apart = Math.abs(here[0] - hsv(reference)[0]);
        if (apart > 180f) apart = 360f - apart;
        return apart <= HUE;
    }

    /**
     * Move `colour` by the step that takes `reference` to `accent`.
     *
     * Sits in the drawing path of half the app, so the common case -- the
     * accent still being the colour the apk was built with -- is one integer
     * comparison and out.
     */
    public static int map(int colour, int reference, int accent) {
        if (reference == accent) return colour;
        // already the accent: an accent that sits inside the family (a pink, a
        // red) would otherwise be moved again every time it passes through here
        if ((colour & 0xFFFFFF) == (accent & 0xFFFFFF)) return colour;
        try {
            if (!captures(colour, reference)) return colour;

            float[] here = hsv(colour);
            float[] from = hsv(reference);
            float[] to = hsv(accent);

            float hue = to[0] + (here[0] - from[0]);
            hue = ((hue % 360f) + 360f) % 360f;
            // a ratio rather than a difference: half as saturated as the pink
            // comes out half as saturated as the accent, whatever it is
            float saturation = from[1] == 0f ? to[1] : to[1] * (here[1] / from[1]);
            float value = from[2] == 0f ? to[2] : to[2] * (here[2] / from[2]);

            return Color.HSVToColor(Color.alpha(colour),
                    new float[]{hue, clamp(saturation), clamp(value)});
        } catch (Throwable ignored) {
            // nothing about a colour is worth taking an app down for
            return colour;
        }
    }

    /**
     * TikTok's other two hues: the cyan half of the brand and the green of
     * "online". Neither is in the red family, so neither moved with the accent,
     * which left a cyan plus, cyan tabs and a green presence dot on screen next
     * to an accent-coloured everything else.
     *
     * Measured from the app on screen: the story plus is #0FC1E5, the cyan of
     * selected tabs and links sits at hue 185-190, the presence dot is #1CD764.
     * The zones are narrow on purpose -- a green or a blue that merely
     * resembles these (success ticks, links) is somebody else's colour.
     */
    public static boolean secondary(int colour) {
        // Called for every colour the app reads, so: integers only, and the
        // greys, darks and pale tints are turned away before any hue is worked out.
        int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max < 128 || (max - min) * 100 < 60 * max) return false;
        float d = max - min, hue;
        if (max == r) hue = ((g - b) / d) % 6f;
        else if (max == g) hue = (b - r) / d + 2f;
        else hue = (r - g) / d + 4f;
        hue *= 60f;
        if (hue < 0f) hue += 360f;
        if (hue >= 175f && hue <= 205f) return true;  // cyan: s >= 0.60, v >= 0.50 above
        float saturation = d / max, value = max / 255f;
        return saturation >= 0.78f && value >= 0.72f && value <= 0.92f
                && hue >= 136f && hue <= 150f;
    }

    /** The accent in the place of a secondary colour; alpha is never touched. */
    public static int toAccent(int colour, int accent) {
        return (colour & 0xFF000000) | (accent & 0xFFFFFF);
    }

    private static float[] hsv(int colour) {
        float[] out = new float[3];
        Color.colorToHSV(colour, out);
        return out;
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}
