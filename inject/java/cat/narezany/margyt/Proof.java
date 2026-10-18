package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Proving that an account is yours, once.
 *
 * TikTok will not tell anybody that somebody is who they say they are, so the
 * mod asks the one thing TikTok does say out loud: what a profile page holds.
 * The server gives out a short code, the person puts it in their bio, the
 * server reads the page and sees it. Nobody can write into somebody else's
 * bio, so nobody else gets the key.
 *
 * The key that comes back is kept on the phone and used for everything after
 * that: the order of badges, a gradient. Proving is not something
 * to do twice.
 *
 * It replaces handing the key to whoever asked first, which held up until
 * somebody wrote a loop and gave the free badge to fifty accounts that had
 * never run the mod.
 */
public final class Proof {

    private Proof() {}

    private static final String KEY_PROVED = "proved_uid";

    //: the code and the secret are kept on the phone as well as in memory:
    //: losing them to a restart means the code already pasted in a bio stops
    //: being the one this phone may spend
    private static final String KEY_CODE = "proof_code";
    private static final String KEY_HOLDER = "proof_holder";
    private static final String KEY_FOR = "proof_for";

    /** The code the server last gave, kept only while the screen is open. */
    private static volatile String code = "";

    /**
     * The half of the proof that never goes in a bio.
     *
     * A code sits where anybody can read it, so on its own it says nothing
     * about who is asking. The server hands this out with the code and will
     * only finish a proof for whoever gives it back.
     */
    private static volatile String holder = "";

    /** Set when the server could not find the page and wants the name. */
    private static volatile boolean needsName;

    public static boolean wantsName() {
        return needsName;
    }

    public interface Then {
        void then(boolean ok, String trouble);
    }

    // ------------------------------------------------------------ what is known

    /** Whether this account has proved itself on this phone. */
    public static boolean proved() {
        String uid = Account.id();
        if (uid == null || uid.length() == 0) return false;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        return uid.equals(prefs.getString(KEY_PROVED, ""))
                && Mine.token().length() > 0;
    }

    /** Remember what the server said when it was asked what this account is. */
    static void heard(boolean isProved) {
        if (isProved) return;
        // the server is the one that decides; a phone saying yes to itself
        // after the key was thrown away helps nobody
        forget();
    }

    /** A write came back asking to be proved, so the key is no good any more. */
    static void lost() {
        forget();
    }

    private static void forget() {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().remove(KEY_PROVED).apply();
    }

    public static String waiting() {
        if (code.length() == 0) remember();
        return code;
    }

    /** Take the code and the secret back off the phone, once. */
    private static synchronized void remember() {
        if (code.length() > 0) return;
        String uid = Account.id();
        SharedPreferences prefs = prefs();
        if (uid == null || prefs == null) return;
        if (!uid.equals(prefs.getString(KEY_FOR, ""))) return;
        code = prefs.getString(KEY_CODE, "");
        holder = prefs.getString(KEY_HOLDER, "");
    }

    private static void keep() {
        String uid = Account.id();
        SharedPreferences prefs = prefs();
        if (uid == null || prefs == null) return;
        prefs.edit().putString(KEY_FOR, uid).putString(KEY_CODE, code)
                .putString(KEY_HOLDER, holder).apply();
    }

    // ------------------------------------------------------------- the asking

    /** Ask for a code to put in the bio. Answers on the main thread. */
    public static void want(final Then then) {
        final String uid = Account.id();
        if (uid == null || uid.length() == 0) {
            answer(then, false, Text.PROVE_NO_ACCOUNT);
            return;
        }
        remember();
        Net.away("proof: code", new Runnable() {
            @Override
            public void run() {
                // the secret goes back with the asking, so this phone is
                // handed its own code again rather than a new one each time
                Net.Said said = Net.talk(Badges.SERVER + "/prove",
                        json("uid", uid, "holder", holder));
                if (!said.ok()) {
                    answer(then, false, trouble(said));
                    return;
                }
                try {
                    JSONObject told = new JSONObject(said.body);
                    code = told.optString("code", "");
                    String key = told.optString("holder", "");
                    if (key.length() > 0) holder = key;
                    keep();
                } catch (Throwable error) {
                    Diary.note("proof: " + error);
                }
                answer(then, code.length() > 0, code.length() > 0 ? "" : Text.PROVE_FAILED);
            }
        });
    }

    /**
     * Have the server read this account's profile page and look for the code.
     *
     * Nothing is sent but the account id. TikTok's own share link says which
     * name an id belongs to, so the server finds the page itself rather than
     * making somebody type their own name into a box.
     */
    public static void check(final String name, final Then then) {
        final String uid = Account.id();
        if (uid == null || uid.length() == 0) {
            answer(then, false, Text.PROVE_NO_ACCOUNT);
            return;
        }
        final String asked = clean(name);
        remember();
        Net.away("proof: check", new Runnable() {
            @Override
            public void run() {
                Net.Said said = asked.length() > 0
                        ? Net.talk(Badges.SERVER + "/prove/check",
                                json("uid", uid, "holder", holder, "name", asked))
                        : Net.talk(Badges.SERVER + "/prove/check",
                                json("uid", uid, "holder", holder));
                if (!said.ok()) {
                    // whatever went wrong, the code on the card may no longer
                    // be the one the server is waiting for, so it is asked
                    // again before anything is said to anybody
                    mint(uid);
                    answer(then, false, trouble(said));
                    return;
                }
                boolean ok = false;
                try {
                    JSONObject told = new JSONObject(said.body);
                    String token = told.optString("token", "");
                    if (token.length() > 0) {
                        Mine.gotKey(uid, token);
                        SharedPreferences prefs = prefs();
                        if (prefs != null) {
                            prefs.edit().putString(KEY_PROVED, uid).apply();
                        }
                        Mine.read(told);
                        code = "";
                        holder = "";
                        needsName = false;
                        keep();
                        ok = true;
                    }
                } catch (Throwable error) {
                    Diary.note("proof: " + error);
                }
                answer(then, ok, ok ? "" : Text.PROVE_FAILED);
            }
        });
    }

    /**
     * Throw this code away and take another.
     *
     * For the code that got left in a bio somewhere, or the one somebody
     * showed in a screenshot. The one before it goes on working for a while,
     * so asking for a new code never breaks a bio that already has the old.
     */
    public static void fresh(final Then then) {
        final String uid = Account.id();
        if (uid == null || uid.length() == 0) {
            answer(then, false, Text.PROVE_NO_ACCOUNT);
            return;
        }
        Net.away("proof: fresh", new Runnable() {
            @Override
            public void run() {
                try {
                    Net.Said said = Net.talk(Badges.SERVER + "/prove",
                            new JSONObject().put("uid", uid).put("holder", holder)
                                    .put("fresh", true).toString());
                    if (!said.ok()) {
                        answer(then, false, trouble(said));
                        return;
                    }
                    JSONObject told = new JSONObject(said.body);
                    String made = told.optString("code", "");
                    String key = told.optString("holder", "");
                    if (made.length() > 0) code = made;
                    if (key.length() > 0) holder = key;
                    keep();
                    needsName = false;
                    answer(then, made.length() > 0, made.length() > 0 ? "" : Text.PROVE_FAILED);
                } catch (Throwable error) {
                    Diary.note("proof: " + error);
                    answer(then, false, Text.PROVE_FAILED);
                }
            }
        });
    }

    /** Ask what the code is now, in the thread that just failed a check. */
    private static void mint(String uid) {
        try {
            Net.Said said = Net.talk(Badges.SERVER + "/prove",
                    json("uid", uid, "holder", holder));
            if (!said.ok()) return;
            JSONObject told = new JSONObject(said.body);
            String fresh = told.optString("code", "");
            if (fresh.length() > 0) code = fresh;
            String key = told.optString("holder", "");
            if (key.length() > 0) holder = key;
            keep();
        } catch (Throwable error) {
            Diary.note("proof: " + error);
        }
    }

    /** An @name as the server wants it: no at, no link around it. */
    static String clean(String name) {
        if (name == null) return "";
        String out = name.trim();
        int slash = out.lastIndexOf('/');
        if (slash >= 0) out = out.substring(slash + 1);
        while (out.startsWith("@")) out = out.substring(1);
        int query = out.indexOf('?');
        if (query >= 0) out = out.substring(0, query);
        return out.trim();
    }

    /** What the server refused with, in words rather than in a number. */
    private static String trouble(Net.Said said) {
        String what = "";
        try {
            if (said.body.length() > 0) {
                what = new JSONObject(said.body).optString("error", "");
            }
        } catch (Throwable ignored) {
        }
        needsName = "tell me the name".equals(what);
        if (needsName) return Text.PROVE_NEED_NAME;
        if ("that is not the code's owner".equals(what)) return Text.PROVE_NOT_YOURS;
        if (said.code == 0) return Text.PROVE_NO_SERVER;
        if ("the code is not in that profile yet".equals(what)) return Text.PROVE_NOT_THERE;
        if ("that name belongs to another account".equals(what)) return Text.PROVE_OTHER_NAME;
        if ("tiktok did not answer".equals(what)) return Text.PROVE_NO_TIKTOK;
        if (said.code == 410) return Text.PROVE_STALE;
        if (said.code == 429) return Text.PROVE_TOO_OFTEN;
        return Text.PROVE_FAILED;
    }

    private static String json(String... pairs) {
        try {
            JSONObject out = new JSONObject();
            for (int i = 0; i + 1 < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
            return out.toString();
        } catch (Throwable error) {
            return "{}";
        }
    }

    private static void answer(final Then then, final boolean ok, final String trouble) {
        if (then == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                then.then(ok, trouble);
            }
        });
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        try {
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
