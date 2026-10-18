package cat.narezany.tokkiyou;

import android.content.SharedPreferences;

import com.bytedance.ies.abmock.SettingsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The switches TikTok ships turned off for you.
 *
 * Every feature in the app is behind a named flag, and the value of each is
 * decided per account on the server. Two people on the same version get
 * different apps: voice comments, the splash advertisement, features already
 * written and shipped and simply not switched on for every account.
 *
 * The flag is read through one class whose name is real,
 * `com.bytedance.ies.abmock.SettingsManager`, with one static per type -- no
 * receiver, so a rewritten read keeps the very shape it had. Those reads come
 * through here instead: a flag on the list below is
 * answered by the mod, and every other flag in the app -- there are tens of
 * thousands -- is passed straight back to TikTok untouched.
 *
 * Each override belongs to a group with a switch of its own, so this is a
 * menu rather than a patch: nothing here is forced on anybody.
 *
 * Every flag name below was checked against 46.9.42 rather than copied from a
 * list. One that a later release drops is simply never asked for.
 *
 * There is a second way in, and most flags come by it: an experiment reader
 * whose name is not real, with one method per type. Each of those methods asks
 * here first -- the build puts the question at the top of every one -- so a
 * flag is answered the same whoever reads it and however. That is what lets a
 * person switch on something TikTok has written and not yet given them, and
 * what the developer's list of every flag stands on.
 */
public final class Flags {

    private Flags() {}

    // the groups, each with a switch on the mod's screen
    public static final int ADS = 0;
    public static final int VOICE = 1;
    public static final int BANNER = 2;
    /** Not on the menu: on for everybody, with no switch. */
    public static final int ALWAYS = 3;

    public static final String KEY_VOICE = "flag_voice";
    public static final String KEY_BANNER = "flag_profile_banner";
    public static final String KEY_SPEED_LOCK = "flag_speed_lock";

    // Switched on here for whoever asks, left as TikTok decided otherwise.
    public static final String KEY_COMMENT_REPOST = "flag_comment_repost";
    public static final String KEY_SELF_REPOST = "flag_self_repost";
    public static final String KEY_TRANSLATE_COMMENTS = "flag_translate_comments";

    // The new profile. On unless switched off, and off means the old one even
    // for somebody TikTok has moved to the new: this is a preference.
    public static final String KEY_NEW_PROFILE = "flag_new_profile";

    private static final Set<String> ON_BY_DEFAULT = new HashSet<String>(Arrays.asList(
            KEY_VOICE, KEY_BANNER, KEY_SPEED_LOCK, KEY_NEW_PROFILE, KEY_TRANSLATE_COMMENTS));

    /** Where the developer's own answers are kept, one entry a flag. */
    static final String OVERRIDES = TokkiYou.PREFS + "_flags";

    /** TikTok's name for "swipe down while holding to keep the speed". */
    private static final String SPEED_LOCK = "long_press_speed_up_lock";

    private static final class Override {
        final int group;
        final Object value;

        Override(int group, Object value) {
            this.group = group;
            this.value = value;
        }
    }

    private static final Map<String, Override> GROUPED = new HashMap<String, Override>();

    private static void put(int group, Object value, String... keys) {
        for (String key : keys) GROUPED.put(key, new Override(group, value));
    }

    static {
        // A splash advertisement never becomes a post, so the feed filter never
        // sees it. This is the only place it can be turned off.
        put(ADS, Boolean.FALSE,
                "enable_normal_splash_ad", "enable_normal_splash_ad_ab", "splash_ad_enable",
                "enable_live_splash", "search_ad_refactor_enable", "search_enable_mix_adgap",
                "commerce_enable");

        put(VOICE, Integer.valueOf(1), "audio_comment_publish");
        put(BANNER,Integer.valueOf(1),"profile_bg_in_allow_list","profile_bg_enable_consumption_group");
    }

    // ------------------------------------------------------------ the switches

    public static boolean isOn(int group) {
        if (group == ADS) return Feed.isEnabled();
        if (group == ALWAYS) return true;
        return bool(keyOf(group));
    }

    private static String keyOf(int group) {
        switch (group) {
            case BANNER: return KEY_BANNER;
            default: return KEY_VOICE;
        }
    }

    public static boolean isOn(String key) {
        return bool(key);
    }

    public static void set(String key, boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, on).apply();
        forget();
    }

    private static boolean bool(String key) {
        try {
            SharedPreferences prefs = prefs();
            return prefs != null && prefs.getBoolean(key, ON_BY_DEFAULT.contains(key));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SharedPreferences prefs() {
        android.content.Context context = TokkiYou.context();
        if (context == null) return null;
        return context.getSharedPreferences(TokkiYou.PREFS, android.content.Context.MODE_PRIVATE);
    }

    /** The override for a flag, or null when the mod has nothing to say. */
    private static Object of(String key) {
        Object forced = forced(key);
        if (forced != null) return forced;
        Override override = GROUPED.get(key);
        if (override == null) return null;
        return isOn(override.group) ? override.value : null;
    }

    // ------------------------------------------ the experiment reader's side

    /**
     * Every answer the mod has for the experiment reader, worked out once.
     *
     * The reader is asked tens of thousands of times, some of them while a
     * frame is being drawn, so the question has to cost one look in a map
     * nobody is writing to. The map is built from the settings the first time
     * it is wanted and thrown away when a setting changes.
     */
    private static volatile Map<String, Object> table;
    /** The thread building the table: reading the settings can itself read a flag. */
    private static volatile Thread building;
    /** The thread asking what TikTok itself would say, for the developer's list. */
    private static volatile Thread asking;

    private static final Set<String> ANSWERED =
            Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    static void forget() {
        table = null;
        ANSWERED.clear();
    }

    private static Map<String, Object> table() {
        Thread me = Thread.currentThread();
        if (building == me) return null;
        building = me;
        try {
            SharedPreferences prefs = prefs();
            if (prefs == null) return null;  // too early to know; do not keep it
            Map<String, Object> made = new HashMap<String, Object>();
            Integer one = Integer.valueOf(1), none = Integer.valueOf(0);

            // Holding a video speeds it up for everybody; swiping down while
            // holding, to keep it sped up with the finger off, TikTok gives to
            // some accounts and not others. Here it is the person's to decide,
            // both ways: on gives it to those TikTok left out, and off takes it
            // from those TikTok gave it to and who keep setting it off by accident.
            made.put(SPEED_LOCK, on(prefs, KEY_SPEED_LOCK) ? one : none);
            if (on(prefs, KEY_VOICE)) made.put("audio_comment_publish", one);
            if (on(prefs, KEY_BANNER)) {
                made.put("profile_bg_in_allow_list", one);
                made.put("profile_bg_enable_consumption_group", one);
            }

            // The entry is offered under one flag to an account that reposts
            // the older way and under another to one that reposts the newer,
            // so both are answered; the third lets a voice comment be reposted.
            if (on(prefs, KEY_COMMENT_REPOST)) {
                made.put("tt_comment_repost", one);
                made.put("tt_comment_repost_interaction", one);
                made.put("tt_audio_comment_repost", one);
            }
            if (on(prefs, KEY_SELF_REPOST)) made.put("enable_self_repost", one);
            // A button in the head of the comments that turns the translation
            // of all of them on and off, where TikTok otherwise translates one
            // at a time. TikTok has it as an experiment with several arms, and
            // the second is the one that draws the button, for viewers and for
            // the author alike.
            //
            // Not together with "auto translation v2", which is another
            // experiment about the same thing: that one translates by itself
            // and takes no notice of the button, so with both on the button
            // was there and did nothing.
            if (on(prefs, KEY_TRANSLATE_COMMENTS)) {
                made.put("comment_auto_translation_enabled", one);
                made.put("comment_auto_translation_consumer_style", Integer.valueOf(2));
                made.put("comment_auto_translation_creator_style", Integer.valueOf(2));
                made.put("comment_auto_translation_display_button_under_dnt", one);
                made.put("tt_comment_auto_translation_v2", none);
            }

            // TikTok hides itself from Google's Circle to Search: while a finger
            // is on the navigation handle it marks the window secure. A secure
            // window is black in the list of recent apps and cannot be
            // screenshotted, and it is an experiment only some accounts are in,
            // which is why some people had it and most did not. Nobody gets it.
            made.put("circle_search_block", none);

            made.put("profile_left_align", on(prefs, KEY_NEW_PROFILE) ? one : none);

            // last, so that a developer's answer beats a switch
            if (DevMode.isOn()) {
                Map<String, Object> mine = overrides();
                if (!mine.isEmpty() && FlagGuard.allows()) made.putAll(mine);
            }
            table = made;
            return made;
        } catch (Throwable error) {
            return null;
        } finally {
            building = null;
        }
    }

    private static boolean on(SharedPreferences prefs, String key) {
        return prefs.getBoolean(key, ON_BY_DEFAULT.contains(key));
    }

    private static Object forced(String key) {
        try {
            Map<String, Object> known = table;
            if (known == null) {
                known = table();
                if (known == null) return null;
            }
            Object value = known.get(key);
            if (value == null || asking == Thread.currentThread()) return null;
            // once a flag: whether TikTok ever asked is the first thing to
            // know when a switch here seems to do nothing
            if (ANSWERED.add(key)) Diary.detail("flag " + key + " = " + value);
            return value;
        } catch (Throwable error) {
            return null;
        }
    }

    // What the build puts at the top of each of the reader's methods. Null
    // sends the method on to do what it always did. A value kept as one type
    // and read as another is turned into the one asked for rather than thrown
    // back: the same flag is read as a number in one place and as yes-or-no
    // in the next.

    public static Boolean forcedBoolean(String key) {
        Object value = forced(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return Boolean.valueOf(((Number) value).doubleValue() != 0);
        return null;
    }

    public static Integer forcedInt(String key) {
        Object value = forced(key);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return Integer.valueOf(((Number) value).intValue());
        if (value instanceof Boolean) return Integer.valueOf(((Boolean) value).booleanValue() ? 1 : 0);
        return null;
    }

    public static Long forcedLong(String key) {
        Object value = forced(key);
        if (value instanceof Number) return Long.valueOf(((Number) value).longValue());
        if (value instanceof Boolean) return Long.valueOf(((Boolean) value).booleanValue() ? 1L : 0L);
        return null;
    }

    public static Float forcedFloat(String key) {
        Object value = forced(key);
        if (value instanceof Number) return Float.valueOf(((Number) value).floatValue());
        return null;
    }

    public static Double forcedDouble(String key) {
        Object value = forced(key);
        if (value instanceof Number) return Double.valueOf(((Number) value).doubleValue());
        return null;
    }

    public static String forcedString(String key) {
        Object value = forced(key);
        return value instanceof String ? (String) value : null;
    }

    // ------------------------------------------------- the developer's list

    /** A flag as TikTok declares it: its name, its type, and what it is when nobody says. */
    static final class Known {
        final String key;
        /** BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, or one this cannot answer for. */
        final String type;
        final Object fallback;

        Known(String key, String type, Object fallback) {
            this.key = key;
            this.type = type;
            this.fallback = fallback;
        }

        boolean settable() {
            return type.equals("BOOLEAN") || type.equals("INT") || type.equals("LONG")
                    || type.equals("FLOAT") || type.equals("DOUBLE") || type.equals("STRING");
        }
    }

    // 47.2.41. Both names are made up by the build that shipped it, so both
    // are looked for rather than relied on, and a list that cannot be found
    // is an empty list.
    private static final String REGISTRY = "X.06y1";
    private static final String READER = "X.09tK";
    private static final int EVERYWHERE = 0x7c00;

    private static List<Known> catalogue;

    /** Every flag the app declares, by name. Slow the first time: it is twenty thousand. */
    static synchronized List<Known> catalogue() {
        if (catalogue != null) return catalogue;
        List<Known> out = new ArrayList<Known>();
        try {
            Class<?> registry = Class.forName(REGISTRY, true, Flags.class.getClassLoader());
            for (java.lang.reflect.Field field : registry.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || !Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object held = field.get(null);
                if (!(held instanceof Map)) continue;
                for (Object each : new ArrayList<Object>(((Map<?, ?>) held).entrySet())) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) each;
                    if (!(entry.getKey() instanceof String) || entry.getValue() == null) continue;
                    Object item = entry.getValue();
                    Object type = item.getClass().getField("type").get(item);
                    Object fallback = item.getClass().getField("defaultValue").get(item);
                    out.add(new Known((String) entry.getKey(), String.valueOf(type), fallback));
                }
            }
        } catch (Throwable error) {
            Diary.note("flags: no list, " + error);
        }
        Collections.sort(out, (a, b) -> a.key.compareTo(b.key));
        catalogue = out;
        return out;
    }

    /** What TikTok itself answers for a flag, the mod aside. Null when that cannot be asked. */
    static Object own(Known flag) {
        Thread before = asking;
        asking = Thread.currentThread();
        try {
            Class<?> type = Class.forName(READER, true, Flags.class.getClassLoader());
            Object reader = null;
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && method.getParameterTypes().length == 0 && method.getReturnType() == type) {
                    reader = method.invoke(null);
                    break;
                }
            }
            if (reader == null) return null;
            Object fallback = flag.fallback;
            switch (flag.type) {
                case "BOOLEAN":
                    return type.getMethod("LIZJ", int.class, String.class, boolean.class, boolean.class)
                            .invoke(reader, EVERYWHERE, flag.key, true,
                                    fallback instanceof Boolean && ((Boolean) fallback).booleanValue());
                case "INT":
                    return type.getMethod("LJIIJJI", int.class, int.class, String.class, boolean.class)
                            .invoke(reader, EVERYWHERE,
                                    fallback instanceof Number ? ((Number) fallback).intValue() : 0,
                                    flag.key, true);
                case "LONG":
                    return type.getMethod("LJIILIIL", int.class, long.class, String.class, boolean.class)
                            .invoke(reader, EVERYWHERE,
                                    fallback instanceof Number ? ((Number) fallback).longValue() : 0L,
                                    flag.key, true);
                case "FLOAT":
                    return type.getMethod("LJIIIIZZ", int.class, String.class, boolean.class, float.class)
                            .invoke(reader, EVERYWHERE, flag.key, true,
                                    fallback instanceof Number ? ((Number) fallback).floatValue() : 0f);
                case "DOUBLE":
                    return type.getMethod("LJFF", double.class, int.class, String.class, boolean.class)
                            .invoke(reader, fallback instanceof Number ? ((Number) fallback).doubleValue() : 0d,
                                    EVERYWHERE, flag.key, true);
                case "STRING":
                    return type.getMethod("LJIILLIIL", int.class, String.class, String.class, boolean.class)
                            .invoke(reader, EVERYWHERE, flag.key,
                                    fallback instanceof String ? (String) fallback : "", true);
                default:
                    return null;
            }
        } catch (Throwable error) {
            return null;
        } finally {
            asking = before;
        }
    }

    private static SharedPreferences overridePrefs() {
        android.content.Context context = TokkiYou.context();
        if (context == null) return null;
        return context.getSharedPreferences(OVERRIDES, android.content.Context.MODE_PRIVATE);
    }

    /** The developer's answers, by flag. A double is kept as text: the file has no type for it. */
    static Map<String, Object> overrides() {
        Map<String, Object> out = new HashMap<String, Object>();
        try {
            SharedPreferences prefs = overridePrefs();
            if (prefs == null) return out;
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();
                String key = entry.getKey();
                if (key.startsWith(DOUBLE)) {
                    out.put(key.substring(DOUBLE.length()), Double.valueOf(String.valueOf(value)));
                } else if (value != null) {
                    out.put(key, value);
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static final String DOUBLE = "double:";

    /** Answer a flag from now on, or with null stop answering it. */
    static void override(Known flag, Object value) {
        SharedPreferences prefs = overridePrefs();
        if (prefs == null) return;
        SharedPreferences.Editor edit = prefs.edit().remove(flag.key).remove(DOUBLE + flag.key);
        if (value instanceof Boolean) edit.putBoolean(flag.key, ((Boolean) value).booleanValue());
        else if (value instanceof Integer) edit.putInt(flag.key, ((Integer) value).intValue());
        else if (value instanceof Long) edit.putLong(flag.key, ((Long) value).longValue());
        else if (value instanceof Float) edit.putFloat(flag.key, ((Float) value).floatValue());
        else if (value instanceof Double) edit.putString(DOUBLE + flag.key, value.toString());
        else if (value instanceof String) edit.putString(flag.key, (String) value);
        edit.apply();
        FlagGuard.again();
        forget();
    }

    static void clearOverrides() {
        SharedPreferences prefs = overridePrefs();
        if (prefs != null) prefs.edit().clear().apply();
        forget();
    }

    /**
     * What somebody typed, as the type the flag is. Null when it is not one.
     */
    static Object parse(Known flag, String typed) {
        String text = typed == null ? "" : typed.trim();
        try {
            switch (flag.type) {
                case "BOOLEAN":
                    if (text.equalsIgnoreCase("true") || text.equals("1")) return Boolean.TRUE;
                    if (text.equalsIgnoreCase("false") || text.equals("0")) return Boolean.FALSE;
                    return null;
                case "INT": return Integer.valueOf(text);
                case "LONG": return Long.valueOf(text);
                case "FLOAT": return Float.valueOf(text.replace(',', '.'));
                case "DOUBLE": return Double.valueOf(text.replace(',', '.'));
                case "STRING": return typed == null ? "" : typed;
                default: return null;
            }
        } catch (NumberFormatException wrong) {
            return null;
        }
    }
    // ------------------------------------------------- where the reads land

    public static boolean flag(String key, boolean fallback) {
        Object value = of(key);
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        if (value instanceof Integer) return ((Integer) value).intValue() != 0;
        return SettingsManager.LIZ(key, fallback);
    }

    public static int flag(String key, int fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        if (value instanceof Boolean) return ((Boolean) value).booleanValue() ? 1 : 0;
        return SettingsManager.LJ(key, fallback);
    }

    public static long flag(String key, long fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        return SettingsManager.LJFF(key, fallback);
    }

    public static String flag(String key, String fallback) {
        Object value = of(key);
        if (value instanceof String) return (String) value;
        return SettingsManager.LJI(key, fallback);
    }

    public static float flag(String key, float fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        return SettingsManager.LIZJ(key, fallback);
    }

    public static double flag(String key, double fallback) {
        Object value = of(key);
        if (value instanceof Integer) return ((Integer) value).intValue();
        return SettingsManager.LIZIZ(key, fallback);
    }
}
