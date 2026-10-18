package cat.narezany.margyt;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Random;

/**
 * The Dep Calculator's money, which is not on the phone.
 *
 * The balance, the reels, the cards and the wheel are all on the server: there
 * is a badge at the end of this, and a badge that could be had by editing a
 * number in a file would not be worth having. So nothing here decides
 * anything. A turn is a question to the server, signed with the key a proved
 * account holds, and what comes back is shown. The reels turn on the screen
 * while the answer is on its way and stop where the server says they stopped.
 */
final class DepGame {

    private DepGame() {}

    static final String KEY_FLOAT = "dep_float";
    /** Dep coins: the calculator's own money, after the name of the thing. */
    static final String COIN = " " + Text.DEP_COIN;

    private static final Random SHOW = new Random();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // what the server last said; -1 until it has said anything
    static volatile long balance = -1, debt, price, mostOwed;
    static volatile boolean held;

    interface Heard {
        /** On the main thread. `said` is null when nothing readable came back. */
        void heard(int code, JSONObject said);
    }

    interface Done {
        /** `trouble` is null when the turn was played, and then the rest is how it came out. */
        void spun(long stake, long won, int times, String trouble);
    }

    /** Whether this account has proved itself, which is what playing takes. */
    static boolean verified() {
        try {
            return Account.id() != null && Mine.token().length() > 0;
        } catch (Throwable error) {
            return false;
        }
    }

    static void ask(final String action, final JSONObject extra, final Heard then) {
        final String uid = Account.id();
        final String token = uid == null ? "" : Mine.token();
        if (uid == null || token.length() == 0) {
            MAIN.post(() -> then.heard(403, null));
            return;
        }
        Net.away("dep: " + action, () -> {
            int code = 0;
            JSONObject out = null;
            try {
                JSONObject body = extra == null ? new JSONObject() : extra;
                body.put("uid", uid).put("token", token).put("do", action);
                Net.Said said = Net.talk(Badges.SERVER + "/dep", body.toString());
                code = said.code;
                if (said.body != null && said.body.length() > 0) out = new JSONObject(said.body);
                if (code == 403) Mine.asksForProof(said);
                if (out != null && out.has("balance")) read(out);
            } catch (Throwable error) {
                Diary.note("dep: " + error);
            }
            final int heard = code;
            final JSONObject answer = out;
            MAIN.post(() -> then.heard(heard, answer));
        });
    }

    private static void read(JSONObject said) {
        balance = said.optLong("balance", balance);
        debt = said.optLong("debt", debt);
        price = said.optLong("price", price);
        mostOwed = said.optLong("most_owed", mostOwed);
        boolean had = held;
        held = said.optBoolean("held", held);
        // bought just now: the list of badges the account wears is out of date
        if (held && !had) Mine.ask(null);
    }

    static String wallet() {
        if (balance < 0) return Text.DEP_BALANCE + ": …";
        return Text.DEP_BALANCE + ": " + balance + COIN
                + (debt > 0 ? "   ·   " + Text.DEP_DEBT + ": " + debt + COIN : "");
    }

    /** Why a turn was not played, in words. */
    static String trouble(int code, JSONObject said) {
        String error = said == null ? "" : said.optString("error", "");
        if (code == 0) return Text.DEP_OFFLINE;
        if (code == 403) return Text.DEP_PROVE;
        if (code == 429) return Text.DEP_SLOW;
        switch (error) {
            case "broke": return Text.DEP_BROKE;
            case "owed": return Text.DEP_OWED_MOST + " " + mostOwed + COIN;
            case "debt": return Text.DEP_PAY_FIRST;
            case "short": return Text.DEP_SHORT + " " + price + COIN;
            case "hand": return Text.DEP_HAND_OPEN;
            default: return Text.DEP_OFFLINE;
        }
    }

    /**
     * Stake on the reels. They turn on the screen from the moment of asking
     * and stop, left to right, once the server has said where.
     */
    static void spin(long wanted, final TextView[] reels, final Done done) {
        final int[] landed = {-1, -1, -1};
        final long[] result = new long[3];   // stake, won, times
        final String[] trouble = {null};
        final boolean[] answered = {false};
        JSONObject stake = new JSONObject();
        try {
            stake.put("stake", wanted);
        } catch (Throwable ignored) { }
        ask("spin", stake, (code, said) -> {
            JSONArray stopped = said == null ? null : said.optJSONArray("reels");
            if (code == 200 && stopped != null && stopped.length() == 3) {
                for (int i = 0; i < 3; i++) {
                    landed[i] = Math.max(0, Math.min(DepSums.REEL.length - 1, stopped.optInt(i)));
                }
                result[0] = said.optLong("stake");
                result[1] = said.optLong("won");
                result[2] = said.optLong("times");
            } else {
                trouble[0] = trouble(code, said);
            }
            answered[0] = true;
        });
        final int[] turn = {0};
        final int[] from = {0};
        MAIN.post(new Runnable() {
            @Override public void run() {
                turn[0]++;
                if (answered[0] && trouble[0] != null) {
                    done.spun(0, 0, 0, trouble[0]);
                    return;
                }
                if (answered[0] && from[0] == 0) from[0] = Math.max(turn[0], 8);
                boolean seen = reels[0].isAttachedToWindow();
                for (int i = 0; i < 3 && seen; i++) {
                    boolean stopped = from[0] > 0 && turn[0] >= from[0] + i * 5;
                    reels[i].setText(DepSums.REEL[stopped ? landed[i] : SHOW.nextInt(DepSums.REEL.length)]);
                }
                if (from[0] > 0 && (turn[0] >= from[0] + 10 || !seen)) {
                    done.spun(result[0], result[1], (int) result[2], null);
                    return;
                }
                // the answer arrives or the request gives up; this only keeps the reels moving
                if (turn[0] < 900) MAIN.postDelayed(this, 70);
            }
        });
    }
}
