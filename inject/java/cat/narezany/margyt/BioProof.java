package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proving an account without anybody copying a code anywhere.
 *
 * The proof itself is `Proof`'s and is unchanged: the server hands out a code,
 * the code goes into the profile's bio, the server reads the public page and
 * finds it. What this adds is the middle step done by the app -- TikTok's own
 * way of saving a bio, called with the code on the end, and called again
 * afterwards with the text that was there before.
 *
 * It came from ttcuz, where it proved an account to a different server. The
 * care it takes is the part worth keeping: the old bio is written to disk
 * before anything is changed, and only the exact text this put there is ever
 * replaced, so a bio somebody edited in the meantime is left alone.
 */
final class BioProof {

    private BioProof() {}

    /** Told how it went, on the main thread. */
    interface Done {
        void done(boolean proved, String said);
    }

    private static final AtomicBoolean busy = new AtomicBoolean();
    private static final String PREFIX = "bio_proof_";

    /** The server allows a code fifteen looks; this stays well inside that. */
    private static final int LOOKS = 9;
    private static final long BETWEEN = 6000L;

    private static long recoveredAt;

    /** Whether this TikTok has the bio-saving call this was written against. */
    static boolean available() {
        try {
            return Margy.context() != null && NativeBio.available();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SharedPreferences prefs() {
        return Margy.context().getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    static boolean pending(String uid) {
        return uid != null && prefs().contains(PREFIX + uid + "_original");
    }

    /** The bio with the code on the end, if TikTok would accept it. */
    static String temporary(String original, String code) {
        String out = original.length() == 0 ? code : original + "\n" + code;
        // TikTok takes 160 UTF-16 characters and at most four line breaks
        if (out.length() > 160 || out.replace("\n", "").length() < out.length() - 4) {
            throw new IllegalArgumentException(Text.PROVE_AUTO_NO_ROOM);
        }
        return out;
    }

    static void start(final Done done) {
        final String uid = Account.liveId();
        if (uid == null) {
            tell(done, false, Text.PROVE_NO_ACCOUNT);
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            tell(done, false, Text.PROVE_AUTO_BUSY);
            return;
        }
        Net.away("proof: by itself", new Runnable() {
            @Override
            public void run() {
                boolean proved = false;
                String said = Text.PROVE_AUTO_STOPPED;
                try {
                    if (Proof.proved() && !pending(uid)) {
                        proved = true;
                        said = Text.PROVE_OK;
                        return;
                    }
                    String key = PREFIX + uid;
                    if (!pending(uid)) {
                        String code = code();
                        if (code.length() == 0) {
                            said = Text.PROVE_AUTO_NO_CODE;
                            return;
                        }
                        String original = NativeBio.bio(uid);
                        String extra = NativeBio.extras(uid);
                        String next = temporary(original, code);
                        // on disk before the profile is touched: the old text
                        // has to outlive the app being killed half way
                        if (!prefs().edit().putString(key + "_original", original)
                                .putString(key + "_temporary", next)
                                .putString(key + "_extra", extra).commit()) {
                            said = Text.PROVE_AUTO_STOPPED;
                            return;
                        }
                    }
                    String original = prefs().getString(key + "_original", "");
                    String next = prefs().getString(key + "_temporary", "");
                    String current = NativeBio.bio(uid);
                    if (original.equals(current)) {
                        if (!NativeBio.save(uid, original, next,
                                prefs().getString(key + "_extra", "null"))) {
                            said = Text.PROVE_AUTO_NOT_SAVED;
                            return;
                        }
                    } else if (!next.equals(current)) {
                        // somebody edited the bio meanwhile; it is theirs
                        said = Text.PROVE_AUTO_CHANGED;
                        return;
                    }
                    prefs().edit().putBoolean(key + "_confirmed", true).commit();

                    for (int look = 0; look < LOOKS && !proved
                            && uid.equals(Account.liveId()); look++) {
                        // the public page lags the app by a few seconds
                        Thread.sleep(BETWEEN);
                        proved = check(Proof.wantsName() ? handle(uid) : "");
                    }
                    restore(uid);
                    said = proved ? Text.PROVE_OK : Text.PROVE_AUTO_NOT_FOUND;
                } catch (IllegalArgumentException refused) {
                    said = refused.getMessage();
                } catch (Throwable error) {
                    Diary.note("proof by itself: " + error);
                    try {
                        if (pending(uid)) restore(uid);
                    } catch (Throwable ignored) {
                    }
                } finally {
                    busy.set(false);
                    later(uid, 0);
                    Diary.note("proof by itself: " + (proved ? "proved" : said));
                    tell(done, proved, said);
                }
            }
        });
    }

    // ------------------------------------------------ Proof, from a worker

    private static String code() throws InterruptedException {
        String have = Proof.waiting();
        if (have.length() > 0) return have;
        final CountDownLatch answered = new CountDownLatch(1);
        Proof.want(new Proof.Then() {
            @Override
            public void then(boolean ok, String trouble) {
                answered.countDown();
            }
        });
        answered.await(25, TimeUnit.SECONDS);
        return Proof.waiting();
    }

    private static boolean check(String name) throws InterruptedException {
        final CountDownLatch answered = new CountDownLatch(1);
        final boolean[] ok = {false};
        Proof.check(name, new Proof.Then() {
            @Override
            public void then(boolean proved, String trouble) {
                ok[0] = proved;
                answered.countDown();
            }
        });
        answered.await(40, TimeUnit.SECONDS);
        return ok[0];
    }

    private static String handle(String uid) {
        try {
            String handle = NativeBio.handle(uid);
            return handle == null ? "" : handle;
        } catch (Throwable ignored) {
            return "";
        }
    }

    // ------------------------------------------------ putting the bio back

    /** A late answer from TikTok can leave the code in the bio; look again. */
    private static void later(final String uid, final int attempt) {
        if (attempt >= 4 || !pending(uid)) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!uid.equals(Account.liveId()) || !pending(uid)) return;
                recover();
                later(uid, attempt + 1);
            }
        }, 30000L);
    }

    /** Called when the account is seen again: finish a restore that did not. */
    static void recover() {
        final String uid = Account.liveId();
        try {
            if (uid == null || Margy.context() == null || !pending(uid)) return;
        } catch (Throwable ignored) {
            return;
        }
        synchronized (BioProof.class) {
            long now = System.currentTimeMillis();
            if (now - recoveredAt < 30000L || !busy.compareAndSet(false, true)) return;
            recoveredAt = now;
        }
        Net.away("proof: bio back", new Runnable() {
            @Override
            public void run() {
                try {
                    restore(uid);
                } catch (Throwable error) {
                    Diary.note("bio back: " + error);
                } finally {
                    busy.set(false);
                }
            }
        });
    }

    private static void restore(String uid) throws Exception {
        if (!uid.equals(Account.liveId())) return;
        String key = PREFIX + uid;
        String original = prefs().getString(key + "_original", null);
        String next = prefs().getString(key + "_temporary", null);
        if (original == null || next == null) return;
        String current = NativeBio.bio(uid);
        if (original.equals(current)) {
            if (prefs().getBoolean(key + "_confirmed", false)) clear(key);
            return;
        }
        // only ever the exact text this put there
        if (!next.equals(current)) return;
        if (NativeBio.save(uid, next, original, prefs().getString(key + "_extra", "null"))) {
            clear(key);
        }
    }

    private static void clear(String key) {
        prefs().edit().remove(key + "_original").remove(key + "_temporary")
                .remove(key + "_extra").remove(key + "_confirmed").commit();
    }

    private static void tell(final Done done, final boolean proved, final String said) {
        if (done == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                done.done(proved, said);
            }
        });
    }
}
