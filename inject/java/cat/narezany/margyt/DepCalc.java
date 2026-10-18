package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * A small Dep Calculator.
 *
 * The real one is somebody else's app and a great deal bigger: this is a nod
 * to it, written from nothing. It adds up, and whatever is on the display can
 * be staked -- on three reels, on a hand of twenty-one, or on the colour of a
 * number on a wheel. The money is made up, and it is kept and counted by the
 * server, because enough of it buys a badge: this screen only asks and shows.
 * There is a way to the real calculator at the bottom.
 */
final class DepCalc {

    private DepCalc() {}

    static final String SITE = "https://zenusus.sbs/dep";

    /** Everything one open calculator is in the middle of. */
    private static final class Table {
        Context context;
        Skin skin;
        float unit;
        TextView balance, display, said;
        TextView[] reels = new TextView[3];
        LinearLayout actions;
        double held;
        char pending;
        boolean fresh = true, busy;
    }

    static void show(final Activity activity, final Skin skin) {
        final Table t = new Table();
        t.context = activity;
        t.skin = skin;
        t.unit = activity.getResources().getDisplayMetrics().density;

        final LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);

        t.balance = line(t, 13, skin.muted(), Gravity.START);
        body.addView(t.balance);

        t.display = line(t, 40, skin.text, Gravity.END);
        t.display.setText("0");
        t.display.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.display.setSingleLine(true);
        t.display.setPadding(0, px(t, 4), px(t, 4), px(t, 8));
        body.addView(t.display);

        LinearLayout slot = new LinearLayout(activity);
        slot.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            t.reels[i] = reel(activity, skin, t.unit, 72, 64, 30);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(px(t, 72), px(t, 64));
            place.setMargins(px(t, 4), 0, px(t, 4), px(t, 8));
            slot.addView(t.reels[i], place);
        }
        body.addView(slot);

        t.said = line(t, 13, skin.muted(), Gravity.CENTER);
        t.said.setText(Text.DEP_HINT);
        t.said.setPadding(0, 0, 0, px(t, 8));
        body.addView(t.said);

        t.actions = new LinearLayout(activity);
        body.addView(t.actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        games(t);

        String[][] rows = {{"7", "8", "9", "÷"}, {"4", "5", "6", "×"}, {"1", "2", "3", "−"},
                {"C", "0", ",", "+"}};
        for (String[] row : rows) {
            LinearLayout keys = new LinearLayout(activity);
            for (final String key : row) {
                boolean sign = !Character.isDigit(key.charAt(0)) && !key.equals(",");
                keys.addView(button(t, key, sign ? Accent.colour() : skin.text, skin.page, 22,
                        () -> typed(t, key)), cell(t, 1f, 52));
            }
            body.addView(keys);
        }
        LinearLayout last = new LinearLayout(activity);
        last.addView(button(t, "=", Accent.colour(), skin.page, 22, () -> typed(t, "=")), cell(t, 1f, 52));
        last.addView(button(t, Text.DEP_BORROW, skin.text, skin.page, 14, () -> bank(t, "borrow")), cell(t, 1.5f, 52));
        last.addView(button(t, Text.DEP_REPAY, skin.text, skin.page, 14, () -> bank(t, "repay")), cell(t, 1.5f, 52));
        body.addView(last);

        LinearLayout prize = new LinearLayout(activity);
        final TextView buy = button(t, Text.DEP_BUY, skin.text, skin.page, 14, () -> bank(t, "buy"));
        prize.addView(buy, cell(t, 1f, 48));
        body.addView(prize);
        money(t);

        // what the server has, before anything is staked
        DepGame.ask("state", null, (code, said) -> {
            if (!t.display.isAttachedToWindow()) return;
            if (code != 200) t.said.setText(DepGame.trouble(code, said));
            else if (said != null && said.has("daily")) t.said.setText(Text.DEP_DAILY + " +" + said.optLong("daily") + DepGame.COIN);
            buy.setText(DepGame.held ? Text.DEP_BOUGHT : Text.DEP_BUY + ": " + DepGame.price + DepGame.COIN);
            money(t);
            if (said != null && said.optJSONObject("hand") != null) open(t, said.optJSONObject("hand"));
        });

        Panel.with(activity, skin, Text.DEP_TITLE)
                .view(body)
                .primary(Text.DEP_FULL, () -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SITE)));
                    } catch (Throwable error) {
                        Screen.say(SITE);
                    }
                })
                .quiet(Text.CLOSE, null)
                .show();
    }

    // ------------------------------------------------------------ the keys

    private static void typed(Table t, String key) {
        if (t.busy) return;
        String now = t.display.getText().toString();
        if (key.length() == 1 && Character.isDigit(key.charAt(0))) {
            t.display.setText(t.fresh || now.equals("0") ? key : now.length() < 12 ? now + key : now);
            t.fresh = false;
        } else if (key.equals(",")) {
            if (t.fresh) { t.display.setText("0."); t.fresh = false; }
            else if (!now.contains(".")) t.display.setText(now + ".");
        } else if (key.equals("C")) {
            t.display.setText("0");
            t.held = 0;
            t.pending = 0;
            t.fresh = true;
        } else {
            double value = number(now);
            double result = t.pending == 0 ? value : DepSums.apply(t.held, t.pending, value);
            t.held = result;
            t.pending = key.equals("=") ? 0 : key.charAt(0);
            t.display.setText(DepSums.shown(result));
            t.fresh = true;
        }
    }

    /** What is on the display, as a stake: whole, and no more than there is. Nought when it cannot be one. */
    private static long stake(Table t) {
        long wanted = (long) Math.floor(Math.abs(number(t.display.getText().toString())));
        if (wanted <= 0 || wanted > 2000000000L) {
            t.said.setText(Text.DEP_HINT);
            return 0;
        }
        return wanted;
    }

    private static JSONObject staking(long stake) {
        JSONObject body = new JSONObject();
        try {
            body.put("stake", stake);
        } catch (Throwable ignored) { }
        return body;
    }

    /** The bank and the shop: borrow, repay, buy. */
    private static void bank(final Table t, final String what) {
        if (t.busy) return;
        t.busy = true;
        DepGame.ask(what, null, (code, said) -> {
            t.busy = false;
            if (!t.display.isAttachedToWindow()) return;
            if (code != 200) t.said.setText(DepGame.trouble(code, said));
            else if (what.equals("borrow")) t.said.setText(Text.DEP_LOAN);
            else if (what.equals("repay")) {
                long paid = said == null ? 0 : said.optLong("paid");
                t.said.setText(paid > 0 ? Text.DEP_REPAID + " " + paid + DepGame.COIN : Text.DEP_NO_DEBT);
            } else t.said.setText(DepGame.held ? Text.DEP_BOUGHT : Text.DEP_SHORT + " " + DepGame.price + DepGame.COIN);
            money(t);
        });
    }

    // ----------------------------------------------------------- the games

    /** The row of things to stake on, as it stands between games. */
    private static void games(final Table t) {
        t.actions.removeAllViews();
        t.actions.addView(button(t, Text.DEP_SPIN, onAccent(), Accent.colour(), 16, () -> slots(t)), cell(t, 1.2f, 48));
        t.actions.addView(button(t, "21", t.skin.text, t.skin.page, 16, () -> deal(t)), cell(t, 0.8f, 48));
        t.actions.addView(button(t, "●", 0xFFE5484D, t.skin.page, 18, () -> wheel(t, 1)), cell(t, 0.7f, 48));
        t.actions.addView(button(t, "●", t.skin.text, t.skin.page, 18, () -> wheel(t, 2)), cell(t, 0.7f, 48));
        t.actions.addView(button(t, "0", 0xFF30A46C, t.skin.page, 16, () -> wheel(t, 0)), cell(t, 0.7f, 48));
    }

    private static void slots(final Table t) {
        if (t.busy) return;
        long stake = stake(t);
        if (stake <= 0) return;
        t.busy = true;
        t.said.setText("…");
        DepGame.spin(stake, t.reels, (staked, won, times, trouble) -> {
            t.busy = false;
            if (!t.display.isAttachedToWindow()) return;
            if (trouble != null) {
                t.said.setText(trouble);
                money(t);
                return;
            }
            settled(t, staked, won, times == 0 ? "" : " (×" + times + ")");
        });
    }

    /** A number on the wheel: 1 for red, 2 for black, 0 for nought itself. */
    private static void wheel(final Table t, final int on) {
        if (t.busy) return;
        final long stake = stake(t);
        if (stake <= 0) return;
        t.busy = true;
        final int[] landed = {-1};
        final long[] result = new long[2];
        final String[] trouble = {null};
        JSONObject body = staking(stake);
        try {
            body.put("on", on == 0 ? "zero" : on == 1 ? "red" : "black");
        } catch (Throwable ignored) { }
        DepGame.ask("wheel", body, (code, said) -> {
            if (code == 200 && said != null && said.has("number")) {
                result[0] = said.optLong("stake");
                result[1] = said.optLong("won");
                landed[0] = said.optInt("number");
            } else {
                trouble[0] = DepGame.trouble(code, said);
                landed[0] = -2;
            }
        });
        final int[] turn = {0};
        t.reels[0].setText("");
        t.reels[2].setText("");
        t.said.setText("…");
        t.display.post(new Runnable() {
            @Override public void run() {
                if (!t.display.isAttachedToWindow()) { t.busy = false; return; }
                turn[0]++;
                if (landed[0] == -2) {
                    t.busy = false;
                    t.said.setText(trouble[0]);
                    money(t);
                    return;
                }
                boolean stops = landed[0] >= 0 && turn[0] >= 12;
                int shown = stops ? landed[0] : (int) (Math.random() * 37);
                t.reels[1].setText(Integer.toString(shown));
                t.reels[1].setTextColor(shown == 0 ? 0xFF30A46C : DepSums.red(shown) ? 0xFFE5484D : t.skin.text);
                if (!stops) {
                    if (turn[0] < 900) t.display.postDelayed(this, 70);
                    return;
                }
                t.busy = false;
                settled(t, result[0], result[1], "");
            }
        });
    }

    private static void deal(final Table t) {
        if (t.busy) return;
        long stake = stake(t);
        if (stake <= 0) return;
        t.busy = true;
        DepGame.ask("deal", staking(stake), (code, said) -> hand(t, code, said));
    }

    /** What came back from a deal, another card, or standing. */
    private static void hand(Table t, int code, JSONObject said) {
        t.busy = false;
        if (!t.display.isAttachedToWindow()) return;
        if (code != 200 || said == null) {
            t.said.setText(DepGame.trouble(code, said));
            money(t);
            return;
        }
        if (said.optBoolean("over")) {
            games(t);
            String cards = cards(said.optJSONArray("mine"), said.optInt("mine_score"),
                    said.optJSONArray("dealer"), said.optInt("dealer_score"));
            settled(t, said.optLong("stake"), said.optLong("won"), "");
            t.said.setText(cards + "\n" + t.said.getText());
            return;
        }
        JSONObject open = said.optJSONObject("hand");
        if (open != null) open(t, open);
        money(t);
    }

    /** A hand still being played: its cards, and the two things that can be done with it. */
    private static void open(final Table t, JSONObject hand) {
        t.said.setText(cards(hand.optJSONArray("mine"), hand.optInt("score"), hand.optJSONArray("dealer"), -1));
        t.actions.removeAllViews();
        t.actions.addView(button(t, Text.DEP_HIT, onAccent(), Accent.colour(), 16, () -> {
            if (t.busy) return;
            t.busy = true;
            DepGame.ask("hit", null, (code, said) -> hand(t, code, said));
        }), cell(t, 1f, 48));
        t.actions.addView(button(t, Text.DEP_STAND, t.skin.text, t.skin.page, 16, () -> {
            if (t.busy) return;
            t.busy = true;
            DepGame.ask("stand", null, (code, said) -> hand(t, code, said));
        }), cell(t, 1f, 48));
    }

    private static String cards(org.json.JSONArray mine, int score, org.json.JSONArray dealer, int theirs) {
        StringBuilder out = new StringBuilder(Text.DEP_YOU).append(": ");
        for (int i = 0; mine != null && i < mine.length(); i++) out.append(DepSums.card(mine.optInt(i))).append(' ');
        out.append("= ").append(score).append("\n").append(Text.DEP_DEALER).append(": ");
        for (int i = 0; dealer != null && i < dealer.length(); i++) out.append(DepSums.card(dealer.optInt(i))).append(' ');
        out.append(theirs < 0 ? "?" : "= " + theirs);
        return out.toString();
    }

    private static void settled(Table t, long stake, long won, String how) {
        t.said.setText(won == 0 ? Text.DEP_LOST + " " + stake + DepGame.COIN
                : won == stake ? Text.DEP_PUSH
                : Text.DEP_WON + " " + won + DepGame.COIN + how);
        t.display.setText(DepSums.shown(won));
        t.fresh = true;
        money(t);
    }

    // ------------------------------------------------------------- pieces

    private static void money(Table t) {
        t.balance.setText(DepGame.wallet());
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException wrong) {
            return 0;
        }
    }

    static int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static int px(Table t, float dp) {
        return Math.round(t.unit * dp);
    }

    private static TextView line(Table t, int sp, int colour, int gravity) {
        TextView view = new TextView(t.context);
        view.setTextColor(colour);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setGravity(gravity);
        return view;
    }

    static TextView reel(Context context, Skin skin, float unit, int width, int height, int sp) {
        TextView reel = new TextView(context);
        reel.setText(DepSums.REEL[DepSums.REEL.length - 1]);
        reel.setTextColor(skin.text);
        reel.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        reel.setTypeface(Typeface.DEFAULT_BOLD);
        reel.setGravity(Gravity.CENTER);
        GradientDrawable box = new GradientDrawable();
        box.setColor(skin.page);
        box.setCornerRadius(unit * 16);
        reel.setBackground(box);
        return reel;
    }

    private static LinearLayout.LayoutParams cell(Table t, float weight, int height) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0, px(t, height), weight);
        int gap = px(t, 3);
        place.setMargins(gap, gap, gap, gap);
        return place;
    }

    private static TextView button(Table t, String label, int ink, int fill, int sp, final Runnable then) {
        TextView button = new TextView(t.context);
        button.setText(label);
        button.setTextColor(ink);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(t.unit * 20);
        button.setBackground(shape);
        button.setOnClickListener(v -> then.run());
        Motion.press(button);
        return button;
    }
}
