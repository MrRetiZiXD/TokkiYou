package cat.narezany.tokkiyou;

import android.content.SharedPreferences;

import com.ss.android.ugc.aweme.IAccountUserService;
import com.ss.android.ugc.aweme.profile.model.User;
import com.ss.android.ugc.profile.platform.base.data.UserProfileInfo;

/**
 * Who is signed in, learned by listening rather than by asking.
 *
 * The id is worth having: a username changes and the id does not, so it is
 * what a profile link is built from and what anyone reporting a problem should
 * be able to copy out.
 *
 * Asking for it directly would mean reaching TikTok's account service, and the
 * only ways in are obfuscated methods on obfuscated classes -- names that will
 * not survive the next release. The service's own interface, though, is a real
 * name, and the app asks it who is signed in constantly. So the calls are
 * rewritten to come through here: the real answer is fetched, remembered, and
 * handed back untouched. Nothing is changed; the mod simply overhears it.
 *
 * Kept in the mod's own settings so the screen has something to show before
 * the app has asked anyone anything.
 */
public final class Account {

    private Account() {}

    public static final String KEY_ID = "account_id";
    public static final String KEY_SEC_ID = "account_sec_id";
    private static final String KEY_PROFILE_NAME = "profile_preview_name";
    private static final String KEY_PROFILE_HANDLE = "profile_preview_handle";
    private static final String KEY_PROFILE_AVATAR = "profile_preview_avatar";

    private static volatile String id;
    private static volatile String activeId;
    static String activeId(){return activeId;}
    public static String liveId(){return activeId;}
    private static volatile String secId;
    private static volatile String profileName;
    private static volatile String profileHandle;
    private static volatile String profileAvatar;
    private static String pendingUid;
    private static String pendingName;
    private static String pendingHandle;
    private static String currentUserUid;
    private static long currentUserCapturedAt;

    // ------------------------------------------------ where the calls land

    public static String getCurUserId(IAccountUserService service) {
        if (service == null) return null;
        String answer = service.getCurUserId();
        // asked on every cell the feed binds; the same answer as last time changes nothing
        if (answer != null && answer.equals(activeId)) return answer;
        activeId=numeric(answer) ? answer : null;
        if(activeId==null)AccountAppearance.select(null);
        remember(KEY_ID, answer);
        return answer;
    }

    /** How long what was learnt from the signed-in profile is taken as still true. */
    private static final long LOOK_AGAIN = 1000L;
    private static volatile String lookedUid;
    private static volatile long lookedAt;

    /** Digits and nothing else, one to twenty-four of them, without a pattern compiled to say so. */
    static boolean numeric(String uid) {
        if (uid == null || uid.length() == 0 || uid.length() > 24) return false;
        for (int i = 0; i < uid.length(); i++) {
            char one = uid.charAt(i);
            if (one < '0' || one > '9') return false;
        }
        return true;
    }

    /** Read the profile TikTok already knows for the signed-in account. */
    public static User getCurUser(IAccountUserService service) {
        if (service == null) return null;
        User user = service.getCurUser();
        if (user == null) {activeId=null;lookedUid=null;AccountAppearance.select(null);return null;}
        try {
            String uid = user.getUid();
            long now = android.os.SystemClock.uptimeMillis();
            // TikTok asks who is signed in many times a second, from the main
            // thread, and nothing below can have changed since the last time
            // it asked a moment ago. This is the answer it is waiting for.
            if (uid != null && uid.equals(lookedUid) && now - lookedAt < LOOK_AGAIN) return user;
            lookedUid = uid;
            lookedAt = now;
            activeId=numeric(uid) ? uid : null;
            remember(KEY_ID, uid);
            rememberProfile(user);
            BioProof.recover();
            if (uid != null && (!uid.equals(currentUserUid)
                    || now - currentUserCapturedAt >= 120000L)) {
                currentUserUid = uid;
                currentUserCapturedAt = now;
                rememberProfile(user);
                Avatars.rememberCurrent(user);
            }
        } catch (Throwable error) {
            Diary.note("current profile: " + error);
        }
        return user;
    }

    public static String getCurSecUserId(IAccountUserService service) {
        if (service == null) return null;
        String answer = service.getCurSecUserId();
        remember(KEY_SEC_ID, answer);
        return answer;
    }

    private static void remember(String key, String value) {
        if (value == null || value.length() == 0) return;
        boolean numeric = KEY_ID.equals(key);
        String known = numeric ? id : secId;
        if (value.equals(known)) {if(numeric)AccountAppearance.select(value);return;}
        if (numeric) {
            String previous = stored(KEY_ID);
            id = value;
            if (previous != null && !previous.equals(value)) clearProfile();
            AccountAppearance.select(value);
        } else {
            secId = value;
        }
        try {
            SharedPreferences prefs = prefs();
            if (prefs != null) prefs.edit().putString(key, value).apply();
        } catch (Throwable ignored) {
        }
        if (numeric) publishPending();
    }

    /** Remember real identity data only when the loaded model is this account. */
    public static void rememberProfile(User user) {
        if (user == null) return;
        try { rememberProfile(user, user.getUid(), user.getNickname()); }
        catch (Throwable ignored) { }
    }

    /** The profile screen uses a separate, loaded model after its first draw. */
    public static void rememberProfile(UserProfileInfo user) {
        if (user == null) return;
        if(user.getUid()!=null && user.getUid().equals(activeId) && currentUserUid!=null
                && currentUserUid.equals(activeId) && profileName!=null)return;
        try { rememberProfile(user, user.getUid(), user.getNickname()); }
        catch (Throwable ignored) { }
    }
    static String canonicalName(String uid,String fallback) {
        if(uid!=null && uid.equals(activeId) && profileName!=null && !profileName.isEmpty())return profileName;
        return fallback;
    }

    private static synchronized void rememberProfile(Object model, String uid, String name) {
        if (uid == null || uid.length() == 0 || name == null || name.length() == 0) return;
        String clean = withoutPrivateUse(name).trim();
        if (clean.length() == 0 || clean.length() > 80) return;
        String handle = invokeString(model, "getUniqueId");
        if (handle != null) {
            handle = handle.trim();
            if (handle.startsWith("@")) handle = handle.substring(1);
            if (!handle.matches("[A-Za-z0-9._]{1,40}")) handle = null;
        }
        String signedIn = id();
        if (signedIn == null) {
            pendingUid = uid;
            pendingName = clean;
            pendingHandle = handle;
            return;
        }
        if (!signedIn.equals(uid)) return;
        saveProfile(clean, handle, null);
    }

    private static synchronized void publishPending() {
        if (pendingUid == null || !pendingUid.equals(id) || pendingName == null) return;
        saveProfile(pendingName, pendingHandle, null);
        pendingUid = pendingName = pendingHandle = null;
    }

    private static void saveProfile(String name, String handle, String avatar) {
        boolean changed = false;
        if (name != null && !name.equals(profileName)) { profileName = name; changed = true; }
        if (handle != null && !handle.equals(profileHandle)) { profileHandle = handle; changed = true; }
        if (avatar != null && !avatar.equals(profileAvatar)) { profileAvatar = avatar; changed = true; }
        if (!changed) return;
        try {
            SharedPreferences p = prefs();
            if (p != null) {
                SharedPreferences.Editor edit = p.edit();
                if (name != null) edit.putString(KEY_PROFILE_NAME, name);
                if (handle != null) edit.putString(KEY_PROFILE_HANDLE, handle);
                if (avatar != null) edit.putString(KEY_PROFILE_AVATAR, avatar);
                edit.apply();
            }
        } catch (Throwable ignored) { }
    }

    private static void clearProfile() {
        profileName = profileHandle = profileAvatar = null;
        pendingUid = pendingName = pendingHandle = null;
        try {
            SharedPreferences p = prefs();
            if (p != null) p.edit().remove(KEY_PROFILE_NAME).remove(KEY_PROFILE_HANDLE)
                    .remove(KEY_PROFILE_AVATAR).apply();
        } catch (Throwable ignored) { }
    }

    /** TikTok's avatar URL, observed from its own profile model. */
    public static synchronized void rememberAvatar(String uid, String address) {
        if (uid == null || address == null || !uid.equals(id())) return;
        try {
            java.net.URI uri = new java.net.URI(address);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || !trustedAvatarHost(host)) return;
            saveProfile(null, null, uri.toString());
        } catch (Throwable ignored) { }
    }

    private static boolean trustedAvatarHost(String host) {
        String lower = host.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".tiktokcdn.com") || lower.equals("tiktokcdn.com")
                || lower.endsWith(".tiktokcdn-us.com")
                || lower.endsWith(".tiktokv.com") || lower.equals("tiktokv.com")
                || lower.endsWith(".byteoversea.com") || lower.endsWith(".ibytedtos.com");
    }

    private static String invokeString(Object object, String method) {
        try {
            Object value = object.getClass().getMethod(method).invoke(object);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) { return null; }
    }

    private static String withoutPrivateUse(String value) {
        StringBuilder clean = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c!='\u2063' && c!='\u2064' && !(c >= '\uE000' && c <= '\uF8FF')) clean.append(c);
        }
        return clean.toString();
    }

    public static String profileName() { return profileValue(KEY_PROFILE_NAME, profileName); }
    public static String profileHandle() { return profileValue(KEY_PROFILE_HANDLE, profileHandle); }
    public static String profileAvatar() { return profileValue(KEY_PROFILE_AVATAR, profileAvatar); }

    private static String profileValue(String key, String known) {
        if (known != null) return known;
        String saved = stored(key);
        if (KEY_PROFILE_NAME.equals(key)) profileName = saved;
        else if (KEY_PROFILE_HANDLE.equals(key)) profileHandle = saved;
        else profileAvatar = saved;
        return saved;
    }

    // -------------------------------------------------- what the screen asks

    /** The numeric id, or null if the app has not asked for it yet. */
    public static String id() {
        String known = id;
        if (known != null) return known;
        known = stored(KEY_ID);
        id = known;
        return known;
    }

    /** The long opaque id -- the one in a profile link. */
    public static String secId() {
        String known = secId;
        if (known != null) return known;
        return stored(KEY_SEC_ID);
    }

    private static String stored(String key) {
        try {
            SharedPreferences prefs = prefs();
            if (prefs == null) return null;
            String value = prefs.getString(key, null);
            return value == null || value.length() == 0 ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs() {
        android.content.Context context = TokkiYou.context();
        if (context == null) return null;
        return context.getSharedPreferences(TokkiYou.PREFS, android.content.Context.MODE_PRIVATE);
    }
}
