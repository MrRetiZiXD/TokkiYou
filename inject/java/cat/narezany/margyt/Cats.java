package cat.narezany.margyt;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A cat, for anyone who taps the title enough times.
 *
 * The same cats Margy has: the list and the photographs are read out of that
 * repository rather than copied into this one, so a cat added there turns up
 * here too, with nobody rebuilding anything.
 *
 * The count is five, and they have to be in a row -- more than a second
 * between two taps and the count starts again. Somebody who taps a heading
 * twice by accident will never reach it; somebody who is trying will reach it
 * at once.
 */
public final class Cats {

    private Cats() {}

    private static final int NEEDED = 5;
    private static final long GAP = 1000;

    private static final String REPOSITORY = "https://margy.narez.xyz/cats/";
    private static final String LIST = REPOSITORY + "cats.json";

    /** One cat: a photograph, what it is called, and who brought it. */
    private static final class Cat {
        final String photo;
        final String name;
        final String from;

        Cat(String photo, String name, String from) {
            this.photo = photo;
            this.name = name;
            this.from = from;
        }
    }

    private static volatile List<Cat> known = new ArrayList<Cat>();
    private static int taps;
    private static long lastTap;

    // ------------------------------------------------------------- the taps

    /**
     * A tap on the heading. Squashes it a little, and on the fifth in a row
     * finds a cat.
     */
    public static void tapped(final View heading) {
        long now = android.os.SystemClock.uptimeMillis();
        taps = now - lastTap > GAP ? 1 : taps + 1;
        lastTap = now;

        try {
            heading.animate().scaleY(0.82f).scaleX(1.04f).setDuration(90)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            heading.animate().scaleY(1f).scaleX(1f).setDuration(160).start();
                        }
                    }).start();
        } catch (Throwable ignored) {
        }

        if (taps < NEEDED) return;
        taps = 0;
        show(heading.getContext());
    }

    // --------------------------------------------------------- the list

    private static void fetch(final Context context) {
        if (!known.isEmpty()) return;
        final File cache = new File(context.getFilesDir(), "margyt/cats.json");
        byte[] have = Net.read(cache);
        if (have != null && apply(have)) return;

        Net.away("cats", new Runnable() {
            @Override
            public void run() {
                byte[] raw = Net.bytes(LIST);
                if (raw == null) return;
                if (apply(raw)) Net.save(cache, raw);
            }
        });
    }

    private static boolean apply(byte[] json) {
        try {
            JSONArray list = new JSONArray(new String(json, "UTF-8"));
            List<Cat> built = new ArrayList<Cat>();
            String language = Locale.getDefault().getLanguage();
            for (int i = 0; i < list.length(); i++) {
                JSONObject one = list.optJSONObject(i);
                if (one == null) continue;
                String photo = one.optString("photo", "");
                if (photo.length() == 0) continue;
                String name = one.optString("name_" + language, "");
                if (name.length() == 0) name = one.optString("name", "");
                built.add(new Cat(photo, name, one.optString("from", "")));
            }
            if (built.isEmpty()) return false;
            known = built;
            return true;
        } catch (Throwable error) {
            Diary.note("cats: " + error);
            return false;
        }
    }

    // --------------------------------------------------------- the window

    private static void show(final Context context) {
        try {
            java.io.InputStream input = context.getAssets().open("margyt/secret-cat.gif");
            final Movie movie;
            try {
                movie = Movie.decodeStream(input);
            } finally {
                input.close();
            }
            if (movie == null) throw new IllegalStateException("secret-cat.gif could not be decoded");

            Skin skin = Skin.remembered(context);
            Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.7f);
            }

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));
            GradientDrawable background = new GradientDrawable();
            background.setColor(skin.card);
            background.setCornerRadius(Math.max(skin.radius, dp(context, 18)));
            card.setBackground(background);

            GifView cat = new GifView(context, movie);
            card.addView(cat, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 240)));

            TextView title = new TextView(context);
            title.setText("Это же секретная котость...");
            title.setTextColor(skin.text);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, dp(context, 12), 0, 0);
            card.addView(title);

            TextView credit = new TextView(context);
            credit.setText("Принёс @mrretizixd");
            credit.setTextColor(skin.muted());
            credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            credit.setGravity(Gravity.CENTER);
            credit.setPadding(0, dp(context, 2), 0, 0);
            card.addView(credit);

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 340),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.88f));
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("secret cat: " + error);
            Screen.say("Секретная котость не загрузилась");
        }
    }

    private static final class GifView extends View {
        private final Movie movie;
        private final long started = android.os.SystemClock.uptimeMillis();

        GifView(Context context, Movie movie) {
            super(context);
            this.movie = movie;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int duration = Math.max(1, movie.duration());
            movie.setTime((int) ((android.os.SystemClock.uptimeMillis() - started) % duration));
            float scale = Math.min(getWidth() / (float) movie.width(),
                    getHeight() / (float) movie.height());
            float left = (getWidth() - movie.width() * scale) / 2f;
            float top = (getHeight() - movie.height() * scale) / 2f;
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            movie.draw(canvas, 0, 0);
            canvas.restore();
            postInvalidateDelayed(40);
        }
    }

    /** The fat tilcayo himself, for the widget. Fetched if this phone has not met him. */
    static byte[] fat(Context context) {
        return picture(context, "tilcayo.jpg");
    }

    private static byte[] picture(Context context, String path) {
        File cache = new File(context.getFilesDir(), "margyt/cats/" + name(path));
        byte[] have = Net.read(cache);
        if (have != null) return have;
        byte[] raw = Net.bytes(REPOSITORY + path);
        if (raw != null) Net.save(cache, raw);
        return raw;
    }

    private static String name(String path) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '.' ? c : '_');
        }
        String cleaned = out.toString();
        // "." and ".." are not names, they are places
        return cleaned.replace("..", "__");
    }

    private static void draw(Context context, Cat cat, byte[] raw) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bitmap == null) return;
            Skin skin = Skin.remembered(context);

            Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.7f);
            }

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));
            GradientDrawable background = new GradientDrawable();
            background.setColor(skin.card);
            background.setCornerRadius(Math.max(skin.radius, dp(context, 18)));
            card.setBackground(background);

            ImageView photo = new ImageView(context);
            photo.setImageBitmap(bitmap);
            photo.setAdjustViewBounds(true);
            photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(photo, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            if (cat.name.length() > 0) {
                TextView name = new TextView(context);
                name.setText(cat.name);
                name.setTextColor(skin.text);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                name.setGravity(Gravity.CENTER);
                name.setPadding(0, dp(context, 12), 0, 0);
                card.addView(name);
            }

            if (cat.from.length() > 0) {
                TextView from = new TextView(context);
                from.setText(Text.CAT_FROM + " " + cat.from);
                from.setTextColor(skin.muted());
                from.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                from.setGravity(Gravity.CENTER);
                from.setPadding(0, dp(context, 2), 0, 0);
                card.addView(from);
            }

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 340),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.88f));
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("cats: " + error);
        }
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
