package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * A small, deliberately boring settings backup.
 *
 * It is an allow-list, not a dump of SharedPreferences: account ids, badge
 * tokens, cached feeds, notification state and any future private key are not
 * something an export should ever learn about. Import is limited to a small
 * JSON document, previewed before it is applied, and writes no unknown keys.
 */
public final class Backup {

    private Backup() {}

    private static final int SCHEMA = 1;
    private static final int MOST_BYTES = 512 * 1024;
    private static final String[] SAFE = {
            Accent.KEY,"theme_plexus","theme_plexus_intensity",
            LikeColors.KEY,
            Margy.KEY_ENABLED, Margy.KEY_COUNTRY,
            Themes.KEY_ON, Themes.KEY_MATERIAL, Themes.KEY_TEXT,
            Themes.KEY_BACKGROUND, Themes.KEY_STRENGTH,
            Themes.KEY_TEXT + "_dark", Themes.KEY_TEXT + "_light",
            Themes.KEY_BACKGROUND + "_dark", Themes.KEY_BACKGROUND + "_light",
            Fonts.KEY, Fonts.KEY_EMOJI,
            "profile_gradient_width", "profile_gradient_stop", "profile_gradient_on", "profile_gradient_start", "profile_gradient_end",
            "profile_gradient_middle", "profile_gradient_three", "profile_gradient_direction",
            "profile_nickname_bold", "profile_nickname_glow", "profile_gradient_animated",
            "profile_gradient_speed", "profile_badge_offset_x", "profile_badge_offset_y",
            "profile_badge_size", "profile_crown_offset_x", "profile_crown_offset_y",
            "profile_badge_x", "profile_badge_y", "profile_badge_zoom",
            Feed.KEY, Feed.KEY_LIVE, Feed.KEY_PHOTOS,
            "rail_show_like","rail_show_comment","rail_show_fav","rail_show_share",
            "streak_send_minute","repost_btn","repost_pos","profile_layout","friends_pinned","message_times","notify_messages","notify_promos","notify_other","notify_no_prompts",
            "dl_quality","frame_quality","player_default_speed","player_seek_step","player_reverse_rate","feed_blocked_authors","feed_blocked_cards",Feed.KEY_STORIES,Feed.KEY_SUGGESTIONS,
            Sound.KEY, Seekbar.KEY, Dates.KEY,
            Dim.KEY_ON, Dim.KEY_HOW, 
            Download.KEY, Download.KEY_ALWAYS, Avatars.KEY, Stickers.KEY,
            Comments.KEY_COPY,
            Flags.KEY_VOICE, Flags.KEY_BANNER, Flags.KEY_SPEED_LOCK,
            Flags.KEY_COMMENT_REPOST, Flags.KEY_SELF_REPOST, Flags.KEY_NEW_PROFILE, AvatarShape.KEY,
            Screamer.KEY, Screamer.KEY_SOUND, DepGame.KEY_FLOAT,
            Streaks.KEY_ON, Streaks.KEY_MODE, Streaks.KEY_TEXT,
            Updater.KEY_REMIND
    };

    static String[] settingKeys(){return SAFE.clone();}
    public static final class Review {
        final android.os.Bundle values;
        final String error;

        Review(android.os.Bundle values, String error) {
            this.values = values;
            this.error = error;
        }

        public boolean fits() { return error == null; }
        public int count() { return values == null ? 0 : values.size(); }
        public String error() { return error; }
    }

    public static boolean write(Context context, Uri where) {
        if (context == null || where == null) return false;
        try {
            SharedPreferences prefs = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
            java.util.Map<String, ?> all = prefs.getAll();
            java.util.Map<String,?> appearance=AccountAppearance.prefs(context).getAll();
            org.json.JSONObject settings = new org.json.JSONObject();
            for (String key : SAFE) {
                Object value = AccountAppearance.appearanceKey(key)?appearance.get(key):all.get(key);
                if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                        || value instanceof Float || value instanceof String) {
                    settings.put(key, value);
                }
            }
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("schema", SCHEMA);
            root.put("settings", settings);
            byte[] raw = root.toString(2).getBytes("UTF-8");
            java.io.OutputStream out = context.getContentResolver().openOutputStream(where, "w");
            if (out == null) return false;
            try {
                out.write(raw);
                out.flush();
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable error) {
            Diary.note("backup export: " + error);
            return false;
        }
    }

    /** Reads and validates a file without changing a single preference. */
    public static Review review(Context context, Uri source) {
        if (context == null || source == null) return new Review(null, "no file");
        try {
            java.io.InputStream in = context.getContentResolver().openInputStream(source);
            if (in == null) return new Review(null, "cannot read file");
            byte[] raw;
            try {
                raw = readSmall(in);
            } finally {
                in.close();
            }
            org.json.JSONObject root = new org.json.JSONObject(new String(raw, "UTF-8"));
            if (root.optInt("schema", -1) != SCHEMA) return new Review(null, "unsupported backup");
            org.json.JSONObject settings = root.optJSONObject("settings");
            if (settings == null) return new Review(null, "settings missing");

            android.os.Bundle clean = new android.os.Bundle();
            for (String key : SAFE) {
                if (!settings.has(key) || settings.isNull(key)) continue;
                Object value = settings.get(key);
                if(key.startsWith("rail_")||key.startsWith("notify_")||key.equals("repost_btn")||key.equals("message_times")||key.equals(Feed.KEY_STORIES)||key.equals(Feed.KEY_SUGGESTIONS)) {
                    if(!(value instanceof Boolean))return new Review(null,"invalid boolean: "+key);
                }
                if(key.equals("streak_send_minute")) {
                    if(!(value instanceof Number))return new Review(null,"invalid schedule");
                    double n=((Number)value).doubleValue();
                    if(Double.isNaN(n)||Double.isInfinite(n)||n!=Math.rint(n)||n < -1||n > 1439)return new Review(null,"invalid schedule");
                    clean.putInt(key,(int)n);continue;
                }
                if(key.equals("player_default_speed")||key.equals("player_reverse_rate")) {
                    if(!(value instanceof Number)||!PlayerTools.validSpeed(((Number)value).floatValue()))return new Review(null,"invalid speed: "+key);
                    clean.putFloat(key,((Number)value).floatValue());continue;
                }
                if(key.equals("dl_quality")||key.equals("frame_quality")||key.equals("player_seek_step")||key.equals("repost_pos")||key.equals("profile_layout")) {
                    if(!(value instanceof Number))return new Review(null,"invalid number: "+key);
                    double n=((Number)value).doubleValue();if(Double.isNaN(n)||Double.isInfinite(n)||n!=Math.rint(n)||n<0||n>1080)return new Review(null,"invalid number: "+key);
                    clean.putInt(key,(int)n);continue;
                }
                if(key.equals("friends_pinned")||key.startsWith("feed_blocked_")) {
                    if(!(value instanceof String)||((String)value).length()>(key.equals("friends_pinned")?4096:16384))return new Review(null,"invalid list: "+key);
                }
                if (value instanceof Boolean) clean.putBoolean(key, ((Boolean) value).booleanValue());
                else if (value instanceof Integer) clean.putInt(key, ((Integer) value).intValue());
                else if (value instanceof Long) clean.putLong(key, ((Long) value).longValue());
                else if (value instanceof Double) clean.putFloat(key, ((Double) value).floatValue());
                else if (value instanceof String) clean.putString(key, (String) value);
            }
            return new Review(clean, null);
        } catch (Throwable error) {
            Diary.note("backup import: " + error);
            return new Review(null, "invalid backup");
        }
    }

    /** Applies the exact preview the person already approved. */
    public static boolean apply(Context context, Review review) {
        if (context == null || review == null || !review.fits()) return false;
        try {
            SharedPreferences.Editor global = context.getSharedPreferences(
                    Margy.PREFS, Context.MODE_PRIVATE).edit();
            SharedPreferences.Editor appearance=AccountAppearance.prefs(context).edit();
            for (String key : review.values.keySet()) {
                SharedPreferences.Editor edit=AccountAppearance.appearanceKey(key)?appearance:global;
                Object value = review.values.get(key);
                if (value instanceof Boolean) edit.putBoolean(key, ((Boolean) value).booleanValue());
                else if (value instanceof Integer) edit.putInt(key, ((Integer) value).intValue());
                else if (value instanceof Long) edit.putLong(key, ((Long) value).longValue());
                else if (value instanceof Float) edit.putFloat(key, ((Float) value).floatValue());
                else if (value instanceof String) edit.putString(key, (String) value);
            }
            global.apply();appearance.apply();AppearanceColors.reload();
            FeedRail.reloadSettings();AvatarShape.reloadSettings();
            FeedBlacklist.reload();PlayerTools.accountChanged();PinnedFriends.reload();ProfileLayout.reload();MessageTimes.reload();ModNotifications.reload();Repost.reload();
            Themes.forget();
            Fonts.reload();
            LikeColors.reload();
            return true;
        } catch (Throwable error) {
            Diary.note("backup apply: " + error);
            return false;
        }
    }

    /** Clear one visibly named group, never a hidden preference or account data. */
    public static void reset(Context context, int group) {
        if (context == null) return;
        String[] keys;
        if (group == 1) keys = new String[] {
                Accent.KEY,"theme_plexus","theme_plexus_intensity", LikeColors.KEY, Themes.KEY_ON, Themes.KEY_MATERIAL,
                Themes.KEY_TEXT, Themes.KEY_BACKGROUND, Themes.KEY_STRENGTH,
                Themes.KEY_TEXT + "_dark", Themes.KEY_TEXT + "_light",
                Themes.KEY_BACKGROUND + "_dark", Themes.KEY_BACKGROUND + "_light",
                Fonts.KEY, Fonts.KEY_EMOJI,
                Flags.KEY_NEW_PROFILE, AvatarShape.KEY,
                "profile_layout"
        };
        else if (group == 2) keys = new String[] {
                Sound.KEY, Seekbar.KEY, Dates.KEY, Dim.KEY_ON, Dim.KEY_HOW,
                "message_times","friends_pinned","dl_quality","frame_quality","player_default_speed","player_seek_step","player_reverse_rate",
                Download.KEY, Download.KEY_ALWAYS, Avatars.KEY,
                Stickers.KEY, Comments.KEY_COPY, Streaks.KEY_ON, Streaks.KEY_MODE, Streaks.KEY_TEXT
        };
        else if (group == 3) keys = new String[] {
                Feed.KEY, Feed.KEY_LIVE, Feed.KEY_PHOTOS, Flags.KEY_VOICE, Flags.KEY_BANNER,
                Flags.KEY_SPEED_LOCK, Flags.KEY_COMMENT_REPOST,
                Flags.KEY_SELF_REPOST,
                "rail_show_like","rail_show_comment","rail_show_fav","rail_show_share",
            "streak_send_minute","repost_btn","repost_pos","notify_messages","notify_promos","notify_other","notify_no_prompts",
                "feed_blocked_authors","feed_blocked_cards",Feed.KEY_STORIES,Feed.KEY_SUGGESTIONS,
                Margy.KEY_ENABLED, Margy.KEY_COUNTRY, Updater.KEY_REMIND
        };
        else keys = SAFE;
        SharedPreferences.Editor edit = context.getSharedPreferences(
                Margy.PREFS, Context.MODE_PRIVATE).edit();
        SharedPreferences.Editor appearance=AccountAppearance.prefs(context).edit();
        for (String key : keys) (AccountAppearance.appearanceKey(key)?appearance:edit).remove(key);
        appearance.apply();AppearanceColors.reload();
        edit.apply();
        FeedRail.reloadSettings();AvatarShape.reloadSettings();
        FeedBlacklist.reload();PlayerTools.accountChanged();PinnedFriends.reload();ProfileLayout.reload();MessageTimes.reload();ModNotifications.reload();Repost.reload();
        Themes.forget();
        Fonts.reload();
        LikeColors.reload();
    }

    private static byte[] readSmall(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > MOST_BYTES) throw new java.io.IOException("backup too large");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
