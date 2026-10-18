package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

/**
 * TikTok's own settings style, measured off the screen it is going on.
 *
 * The rows on that screen are drawn by Compose, out of colours and dimensions
 * that live in obfuscated Kotlin, and the resource table is no help either --
 * TikTok's colours are called `ag` and `ah` in there. Copying the numbers out
 * of a screenshot would make the row right once, for one release, on one phone.
 *
 * So the screen is asked instead. It is drawn into a bitmap the mod owns, and
 * the card colour, the text colour, the side margin and the corner radius are
 * read out of the pixels: whatever TikTok is drawing today, in whichever theme,
 * is what the row is built from. When that fails the fallback is plain -- black
 * or white and a sensible radius -- and the diary says which one was used.
 */
final class Skin {

    final int page;
    final int card;
    final int text;
    final int margin;   // px from the screen edge to the card
    final int radius;   // px
    final boolean measured;

    private Skin(int page, int card, int text, int margin, int radius, boolean measured) {
        this.page = page;
        this.card = card;
        this.text = text;
        this.margin = margin;
        this.radius = radius;
        this.measured = measured;
    }

    /** The colour of a section label or a caption on this screen. */
    int muted() {
        return (text & 0x00FFFFFF) | 0x99000000;
    }

    boolean dark() {
        return (0.299f * android.graphics.Color.red(page)
                + 0.587f * android.graphics.Color.green(page)
                + 0.114f * android.graphics.Color.blue(page)) / 255f < 0.5f;
    }

    /**
     * The style measured last time, for TokkiYou's own screen to be drawn in.
     *
     * That screen can be opened from the launcher without TikTok's settings
     * ever being on screen, so there is nothing to measure at the time -- what
     * the row measured is kept instead, and used again here.
     */
    static Skin remembered(Context context) {
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
            if (prefs.contains("skin_card")) {
                return new Skin(
                        prefs.getInt("skin_page", 0xFF000000),
                        prefs.getInt("skin_card", 0xFF1C1C1E),
                        prefs.getInt("skin_text", 0xFFFFFFFF),
                        prefs.getInt("skin_margin", Math.round(
                                16 * context.getResources().getDisplayMetrics().density)),
                        prefs.getInt("skin_radius", Math.round(
                                12 * context.getResources().getDisplayMetrics().density)),
                        true);
            }
        } catch (Throwable ignored) {
        }
        return fallback(context);
    }

    void remember(Context context) {
        if (!measured) return;
        try {
            context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit()
                    .putInt("skin_page", page)
                    .putInt("skin_card", card)
                    .putInt("skin_text", text)
                    .putInt("skin_margin", margin)
                    .putInt("skin_radius", radius)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    static Skin of(View screen) {
        try {
            Skin measured = measure(screen);
            if (measured != null) {
                measured.remember(screen.getContext());
                Diary.note(String.format(
                        "style read off the screen: card #%06X text #%06X margin %dpx radius %dpx",
                        measured.card & 0xFFFFFF, measured.text & 0xFFFFFF,
                        measured.margin, measured.radius));
                return measured;
            }
        } catch (Throwable error) {
            Diary.note("could not read the style: " + error);
        }
        return fallback(screen.getContext());
    }

    private static Skin fallback(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        float density = context.getResources().getDisplayMetrics().density;
        return new Skin(
                dark ? 0xFF000000 : 0xFFFFFFFF,
                dark ? 0xFF1C1C1E : 0xFFF5F5F5,
                dark ? 0xFFFFFFFF : 0xFF161823,
                Math.round(16 * density),
                Math.round(12 * density),
                false);
    }

    // ------------------------------------------------------------ measuring

    private static Skin measure(View screen) {
        int width = screen.getWidth();
        if (width <= 0) return null;
        float density = screen.getResources().getDisplayMetrics().density;
        int height = Math.min(screen.getHeight(), Math.round(520 * density));
        if (height <= 0) return null;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            screen.draw(new Canvas(bitmap));
            return read(bitmap, width, height, density);
        } finally {
            bitmap.recycle();
        }
    }

    private static Skin read(Bitmap bitmap, int width, int height, float density) {
        int page = commonest(bitmap, 1, height / 3, height - 1);
        if (Color.alpha(page) < 255) return null;

        // the first card: a run of a different colour down the middle of the
        // screen, long enough not to be a letter or a line
        int cardTop = -1, card = 0;
        int run = 0, minimum = Math.round(24 * density);
        for (int y = 0; y < height; y++) {
            int colour = bitmap.getPixel(width / 2, y);
            if (colour != page && Color.alpha(colour) == 255) {
                if (run == 0) {
                    card = colour;
                    cardTop = y;
                } else if (colour != card) {
                    run = 0;
                    continue;
                }
                if (++run >= minimum) break;
            } else {
                run = 0;
            }
        }
        if (run < minimum || cardTop < 0) return null;

        int inside = cardTop + Math.round(16 * density);
        if (inside >= height) return null;
        int margin = edge(bitmap, inside, width, card);
        if (margin < 0) return null;

        // the corner: how far down the card's left edge takes to reach the
        // margin it keeps for the rest of its height
        int radius = 0;
        for (int k = 0; k < Math.round(28 * density) && cardTop + k < height; k++) {
            int here = edge(bitmap, cardTop + k, width, card);
            if (here == margin) {
                radius = k;
                break;
            }
        }

        int text = contrast(bitmap, cardTop, Math.min(cardTop + Math.round(56 * density), height),
                margin, width - margin, card);
        return new Skin(page, card, text, margin, radius, true);
    }

    /** Where `colour` starts on row `y`, scanning in from the left. */
    private static int edge(Bitmap bitmap, int y, int width, int colour) {
        for (int x = 0; x < width / 2; x++) {
            if (bitmap.getPixel(x, y) == colour) return x;
        }
        return -1;
    }

    /** The colour on the card that stands out most: its text. */
    private static int contrast(Bitmap bitmap, int top, int bottom, int left, int right, int card) {
        float base = luminance(card);
        int best = card;
        float found = 0f;
        for (int y = top; y < bottom; y += 2) {
            for (int x = left; x < right; x += 2) {
                int colour = bitmap.getPixel(x, y);
                if (Color.alpha(colour) < 255) continue;
                float distance = Math.abs(luminance(colour) - base);
                if (distance > found) {
                    found = distance;
                    best = colour;
                }
            }
        }
        return found > 0.25f ? best : (base < 0.5f ? 0xFFFFFFFF : 0xFF161823);
    }

    /** The colour a column is mostly made of. */
    private static int commonest(Bitmap bitmap, int x, int from, int to) {
        int best = bitmap.getPixel(x, from), bestCount = 0;
        for (int y = from; y < to; y += 3) {
            int colour = bitmap.getPixel(x, y);
            int count = 0;
            for (int j = from; j < to; j += 3) {
                if (bitmap.getPixel(x, j) == colour) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                best = colour;
            }
            if (bestCount > (to - from) / 6) break;  // good enough, and quicker
        }
        return best;
    }

    private static float luminance(int colour) {
        return (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
    }
}
