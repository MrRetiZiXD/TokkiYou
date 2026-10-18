package cat.narezany.tokkiyou;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.RemoteViews;

/**
 * A widget for the home screen: the fat tilcayo, and nothing else.
 *
 * The same photograph that five taps on the mod's name bring up, kept where
 * that keeps it, so a phone that has seen the cat once shows it at once and
 * one that has not fetches it the first time the widget is put down.
 *
 * The layout is a resource the build adds, one ImageView, and its number is
 * written here as the build writes it: there is no generated `R` in a mod.
 */
public final class CatWidget extends AppWidgetProvider {

    /** The layout the build adds: package 0x31, its first and only entry. */
    static final int LAYOUT = 0x31010000;
    /** A widget's picture goes to the launcher through a pipe that is not wide. */
    private static final int LARGEST = 600;

    @Override
    public void onUpdate(final Context context, final AppWidgetManager manager, final int[] ids) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                RemoteViews views = new RemoteViews(context.getPackageName(), LAYOUT);
                Bitmap cat = cat(context);
                if (cat != null) views.setImageViewBitmap(android.R.id.background, cat);
                Intent open = context.getPackageManager()
                        .getLaunchIntentForPackage(context.getPackageName());
                if (open != null) {
                    views.setOnClickPendingIntent(android.R.id.background,
                            PendingIntent.getActivity(context, 0, open,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                }
                manager.updateAppWidget(ids, views);
            } catch (Throwable error) {
                Diary.note("widget: " + error);
            } finally {
                pending.finish();
            }
        }, "tokkiyou-widget").start();
    }

    private static Bitmap cat(Context context) {
        byte[] raw = Cats.fat(context);
        if (raw == null) return null;
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, size);
        int longest = Math.max(size.outWidth, size.outHeight);
        BitmapFactory.Options how = new BitmapFactory.Options();
        how.inSampleSize = 1;
        while (longest / how.inSampleSize > LARGEST * 2) how.inSampleSize *= 2;
        Bitmap decoded = BitmapFactory.decodeByteArray(raw, 0, raw.length, how);
        if (decoded == null) return null;
        longest = Math.max(decoded.getWidth(), decoded.getHeight());
        if (longest <= LARGEST) return decoded;
        float scale = LARGEST / (float) longest;
        return Bitmap.createScaledBitmap(decoded, Math.round(decoded.getWidth() * scale),
                Math.round(decoded.getHeight() * scale), true);
    }
}
