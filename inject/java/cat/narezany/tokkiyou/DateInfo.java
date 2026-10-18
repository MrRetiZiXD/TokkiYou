package cat.narezany.tokkiyou;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The full story of a video's publication time, opened by tapping the date under the author.
 *
 * One tonal sheet in the mod's own Material 3 Expressive style: a cookie-shaped badge that turns
 * as the sheet opens, the date written out in words, the time down to the second, a pill saying
 * how long ago that was, and a segmented list of the exact values. Tapping a row copies it.
 */
final class DateInfo {
    private DateInfo() {}

    /** View tag under which a feed cell remembers the post it belongs to. */
    static final int CELL_TAG = 0x7e0d0335;

    /** Everything the sheet needs, copied out of the post so that no model object is kept alive. */
    static final class Data {
        final long seconds;
        final String aid, author, handle;

        Data(long seconds, String aid, String author, String handle) {
            this.seconds = seconds;
            this.aid = aid == null ? "" : aid;
            this.author = author == null ? "" : author;
            this.handle = handle == null ? "" : handle;
        }

        static Data of(Object post) {
            if (post == null) return null;
            long time = NativeRead.number(NativeRead.get(post, "getCreateTime"));
            if (time > 100000000000L) time /= 1000L;
            if (time < 1000000000L) return null;
            Object user = NativeRead.get(post, "getAuthor");
            return new Data(time, NativeRead.aid(post),
                    NativeRead.string(NativeRead.get(user, "getNickname")),
                    NativeRead.string(NativeRead.get(user, "getUniqueId")));
        }
    }

    /** Called from the date row. `cell` is the feed cell that row belongs to. */
    static void open(View row, View cell) {
        try {
            Object tag = cell == null ? null : cell.getTag(CELL_TAG);
            Data data = tag instanceof Data ? (Data) tag : Dates.selectedInfo;
            if (data == null || row == null) return;
            show(row.getContext(), data);
        } catch (Throwable error) {
            Diary.note("date info: " + error);
        }
    }

    // ------------------------------------------------------------------ words

    private static final String LANGUAGE = Locale.getDefault().getLanguage();
    private static final boolean RU = "ru".equals(LANGUAGE) || "be".equals(LANGUAGE);
    private static final boolean UK = "uk".equals(LANGUAGE);

    private static String pick(String russian, String ukrainian, String english) {
        return RU ? russian : UK ? ukrainian : english;
    }

    // ------------------------------------------------------------------ sheet

    static void show(final Context context, Data data) {
        final Locale locale = Locale.getDefault();
        final TimeZone zone = TimeZone.getDefault();
        final long millis = data.seconds * 1000L;
        final Date when = new Date(millis);
        final boolean h24 = android.text.format.DateFormat.is24HourFormat(context);

        final String date = format(best(locale, "dMMMMyyyy", "d MMMM yyyy"), when, zone, locale);
        final String weekday = capital(format("EEEE", when, zone, locale));
        final String clock = format(best(locale, h24 ? "Hms" : "hms", "HH:mm:ss"), when, zone, locale);
        final String numeric = format("dd.MM.yyyy HH:mm:ss", when, zone, locale);
        final String utc = format("dd.MM.yyyy HH:mm:ss", when, TimeZone.getTimeZone("UTC"), locale) + " UTC";
        final String offset = offset(zone.getOffset(millis));
        final String ago = ago(data.seconds, System.currentTimeMillis() / 1000L);

        Skin skin = Skin.remembered(context);
        final Panel panel = Panel.with(context, skin, null);
        panel.view(header(context, skin, date, weekday + ", " + clock, ago));

        panel.section(pick("Точное время", "Точний час", "Exact time"))
                .info("article", numeric, pick("Дата и время публикации", "Дата й час публікації", "Published"), copy(context, numeric))
                .info("schedule", clock, pick("Время до секунды", "Час до секунди", "Time to the second"), copy(context, clock))
                .info("timeline", weekday, pick("День недели", "День тижня", "Day of the week"), copy(context, weekday))
                .info("language", offset + " · " + zone.getID(), pick("Часовой пояс", "Часовий пояс", "Time zone"), copy(context, offset + " " + zone.getID()))
                .info("public", utc, pick("По Гринвичу", "За Грінвічем", "In UTC"), copy(context, utc));

        panel.section(pick("Технические данные", "Технічні дані", "Technical"))
                .info("update", String.valueOf(data.seconds), "Unix timestamp", copy(context, String.valueOf(data.seconds)));
        if (data.aid.length() > 0) {
            panel.info("fingerprint", data.aid, pick("ID видео", "ID відео", "Video ID"), copy(context, data.aid));
        }
        if (data.author.length() > 0 || data.handle.length() > 0) {
            String who = data.author.length() > 0 ? data.author : "@" + data.handle;
            String sub = pick("Автор", "Автор", "Author") + (data.handle.length() > 0 && data.author.length() > 0 ? " · @" + data.handle : "");
            panel.info("account_circle", who, sub, copy(context, who));
        }

        final String all = date + ", " + weekday + ", " + clock + "\n" + numeric + "\n" + offset + " " + zone.getID() + "\n"
                + utc + "\nUnix: " + data.seconds + (data.aid.length() > 0 ? "\nID: " + data.aid : "");
        panel.primary(pick("Скопировать всё", "Скопіювати все", "Copy everything"), () -> copy(context, all).on())
                .quiet(pick("Закрыть", "Закрити", "Close"), null)
                .show();
    }

    private static Panel.Click copy(final Context context, final String text) {
        return () -> {
            try {
                ClipboardManager board = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (board != null) board.setPrimaryClip(ClipData.newPlainText("TokkiYou", text));
                Screen.say(Text.COPIED);
            } catch (Throwable error) {
                Diary.note("date copy: " + error);
            }
        };
    }

    // ----------------------------------------------------------------- header

    private static View header(Context context, Skin skin, String date, String line, String ago) {
        int accent = Accent.colour();
        float unit = context.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(4 * unit), Math.round(4 * unit), Math.round(4 * unit), Math.round(8 * unit));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Expressive.Badge badge = new Expressive.Badge(context, Expressive.COOKIE9,
                SettingsGlyph.make("schedule", accent), 72,
                (accent & 0xFFFFFF) | 0x33000000, accent, accent, onAccent(accent));
        LinearLayout.LayoutParams badgeWhere = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeWhere.rightMargin = Math.round(16 * unit);
        top.addView(badge, badgeWhere);
        badge.set(true, true);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView big = new TextView(context);
        big.setText(date);
        big.setTextColor(skin.text);
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        big.setLineSpacing(0, 0.96f);
        words.addView(big, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView small = new TextView(context);
        small.setText(line);
        small.setTextColor(accent);
        small.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams smallWhere = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        smallWhere.topMargin = Math.round(2 * unit);
        words.addView(small, smallWhere);
        top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pill = new TextView(context);
        pill.setText(ago);
        pill.setTextColor(accent);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pill.setPadding(Math.round(14 * unit), Math.round(7 * unit), Math.round(14 * unit), Math.round(7 * unit));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor((accent & 0xFFFFFF) | 0x26000000);
        shape.setCornerRadius(Math.round(20 * unit));
        pill.setBackground(shape);
        LinearLayout.LayoutParams pillWhere = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillWhere.topMargin = Math.round(14 * unit);
        box.addView(pill, pillWhere);
        return box;
    }

    private static int onAccent(int colour) {
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF111111 : 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------- formats

    private static String best(Locale locale, String skeleton, String fallback) {
        try {
            String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton);
            return pattern == null || pattern.length() == 0 ? fallback : pattern;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String format(String pattern, Date when, TimeZone zone, Locale locale) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
            format.setTimeZone(zone);
            return format.format(when);
        } catch (Throwable error) {
            return String.valueOf(when.getTime() / 1000L);
        }
    }

    private static String capital(String text) {
        if (text == null || text.length() == 0) return "";
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    private static String offset(int millis) {
        int minutes = Math.abs(millis) / 60000;
        return "UTC" + (millis < 0 ? "−" : "+") + two(minutes / 60) + ":" + two(minutes % 60);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    // -------------------------------------------------------------- how long

    private static final String[][] RU_UNITS = {
            {"год", "года", "лет"}, {"месяц", "месяца", "месяцев"}, {"день", "дня", "дней"},
            {"час", "часа", "часов"}, {"минуту", "минуты", "минут"}, {"секунду", "секунды", "секунд"}};
    private static final String[][] UK_UNITS = {
            {"рік", "роки", "років"}, {"місяць", "місяці", "місяців"}, {"день", "дні", "днів"},
            {"годину", "години", "годин"}, {"хвилину", "хвилини", "хвилин"}, {"секунду", "секунди", "секунд"}};
    private static final String[][] EN_UNITS = {
            {"year", "years", "years"}, {"month", "months", "months"}, {"day", "days", "days"},
            {"hour", "hours", "hours"}, {"minute", "minutes", "minutes"}, {"second", "seconds", "seconds"}};

    private static String unit(int index, long count) {
        String[][] units = RU ? RU_UNITS : UK ? UK_UNITS : EN_UNITS;
        long tail = count % 100, last = count % 10;
        int form;
        if (RU || UK) {
            form = last == 1 && tail != 11 ? 0 : last >= 2 && last <= 4 && (tail < 12 || tail > 14) ? 1 : 2;
        } else {
            form = count == 1 ? 0 : 1;
        }
        return count + " " + units[index][form];
    }

    /** "2 месяца 3 дня назад": the two largest units that are not zero. */
    static String ago(long published, long now) {
        if (now - published < 60) return pick("только что", "щойно", "just now");
        Calendar from = Calendar.getInstance();
        from.setTimeInMillis(published * 1000L);
        Calendar to = Calendar.getInstance();
        to.setTimeInMillis(now * 1000L);
        long[] parts = new long[6];
        int[] fields = {Calendar.YEAR, Calendar.MONTH};
        for (int i = 0; i < 2; i++) {
            int step = 0;
            while (true) {
                Calendar next = (Calendar) from.clone();
                next.add(fields[i], 1);
                if (next.after(to)) break;
                from = next;
                step++;
            }
            parts[i] = step;
        }
        long rest = Math.max(0L, (to.getTimeInMillis() - from.getTimeInMillis()) / 1000L);
        parts[2] = rest / 86400L;
        parts[3] = rest % 86400L / 3600L;
        parts[4] = rest % 3600L / 60L;
        parts[5] = rest % 60L;
        StringBuilder text = new StringBuilder();
        int used = 0;
        for (int i = 0; i < 6 && used < 2; i++) {
            if (parts[i] == 0) continue;
            if (text.length() > 0) text.append(' ');
            text.append(unit(i, parts[i]));
            used++;
        }
        return RU ? text + " назад" : UK ? text + " тому" : text + " ago";
    }
}
