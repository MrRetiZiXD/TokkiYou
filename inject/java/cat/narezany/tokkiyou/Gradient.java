package cat.narezany.tokkiyou;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Your own gradient: what it is, and telling the server about it.
 *
 * Who may have one is the server's decision, not this file's. The mod is an
 * apk anybody can edit, so the badge is checked where editing the apk does
 * not help, and so is the wait between changes.
 */
public final class Gradient {

    /** How many colours a gradient may have, matching the server. */
    public static final int FEWEST = 2;
    public static final int MOST = 5;

    /** What is offered before anybody has chosen anything. */
    private static final int[] SUGGESTED = {0xFFFF6FA5, 0xFF7B61FF};

    private Gradient() {}

    public interface Said {
        void said(boolean ok, String trouble);
    }

    /** The colours the server currently has for this account. */
    public static int[] mine() {
        String uid = Account.id();
        int[] have = uid == null ? null : Looks.of(uid);
        return have == null ? null : have.clone();
    }

    /** What the picker starts from. */
    public static List<Integer> starting() {
        List<Integer> out = new ArrayList<Integer>();
        int[] have = mine();
        if (have != null) {
            for (int colour : have) out.add(colour);
        } else {
            for (int colour : SUGGESTED) out.add(colour);
        }
        return out;
    }

    public static void save(final List<Integer> colours, final Said then) {
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid == null || token.length() == 0) {
            if (then != null) then.said(false, Mine.PROVE);
            return;
        }
        Net.away("gradient: save", new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String trouble = "";
                try {
                    JSONArray list = new JSONArray();
                    for (Integer colour : colours) list.put(hex(colour));
                    Net.Said said = Net.talk(Badges.SERVER + "/gradient",
                            new JSONObject().put("uid", uid).put("token", token)
                                    .put("colours", list).toString());
                    if (said.ok()) {
                        ok = true;
                        Badges.refresh();
                    } else {
                        trouble = refusal(said);
                    }
                } catch (Throwable error) {
                    trouble = String.valueOf(error);
                    Diary.note("gradient: " + error);
                }
                answer(then, ok, trouble);
            }
        });
    }

    /** Stop having one. */
    public static void drop(final Said then) {
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid == null || token.length() == 0) {
            if (then != null) then.said(false, Mine.PROVE);
            return;
        }
        Net.away("gradient: drop", new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String trouble = Text.GRADIENT_REFUSED;
                try {
                    Net.Said said = Net.talk(Badges.SERVER + "/gradient",
                            new JSONObject().put("uid", uid).put("token", token)
                                    .toString());
                    ok = said.ok();
                    if (ok) Badges.refresh();
                    else trouble = refusal(said);
                } catch (Throwable error) {
                    Diary.note("gradient: " + error);
                }
                answer(then, ok, ok ? "" : trouble);
            }
        });
    }

    static String hex(int colour) {
        return String.format("%06X", colour & 0xFFFFFF);
    }

    private static String refusal(Net.Said said) {
        if (Mine.asksForProof(said)) return Mine.PROVE;
        if (said.code == 429) {
            try {
                return Text.gradientWait(Math.max(1,
                        new JSONObject(said.body).optInt("wait", 60)));
            } catch (Throwable ignored) {
                return Text.gradientWait(60);
            }
        }
        return Text.GRADIENT_REFUSED;
    }

    /** One colour from hue, saturation and value, which is what the picker holds. */
    public static int from(int hue, int saturation, int value) {
        return Color.HSVToColor(new float[]{hue % 360, saturation / 100f, value / 100f});
    }

    public static int[] hsv(int colour) {
        float[] out = new float[3];
        Color.colorToHSV(colour, out);
        return new int[]{(int) out[0], Math.round(out[1] * 100), Math.round(out[2] * 100)};
    }

    private static void answer(final Said then, final boolean ok, final String trouble) {
        if (then == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                then.said(ok, trouble);
            }
        });
    }
}
