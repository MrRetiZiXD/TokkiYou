package cat.narezany.margyt;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The mod's own window, for anything that needs more than a sentence.
 *
 * The stock dialog is the platform's: square-cornered, with buttons in a row
 * along the bottom and a title bar nobody chose. `Popup` already draws a card
 * in the screen's own colours for a message or a question; this is the same
 * card with room for a body, a block of content of the caller's own, and any
 * number of buttons stacked underneath, the main one filled and the rest quiet.
 *
 *     Panel.with(context, skin, "Title")
 *          .text("What it is about")
 *          .view(somethingDrawn)
 *          .primary("Do it", () -> ...)
 *          .quiet("Not now", null)
 *          .show();
 *
 * A button closes the window unless it is added with `keepOpen`.
 */
final class Panel {

    interface Click { void on(); }

    private final Context context;
    private final Skin skin;
    private final LinearLayout card;
    private final LinearLayout buttons;
    private final Dialog dialog;
    private TextView status;
    private TextView head;
    private Expressive.Group group;
    private boolean stayOpen;
    private boolean compact;

    private Panel(Context context, Skin skin, String title) {
        this.context = context;
        this.skin = skin;
        dialog = new SheetDialog(context);
        Window window = dialog.getWindow();
        if (window != null) {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.45f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        card = new LinearLayout(context) {
            @Override protected void onMeasure(int width,int height){
                int cap=(int)(getResources().getDisplayMetrics().heightPixels*0.78f);
                int available=View.MeasureSpec.getMode(height)==View.MeasureSpec.UNSPECIFIED ? cap : Math.min(cap,View.MeasureSpec.getSize(height));
                super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.AT_MOST));
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadii(new float[]{dp(32),dp(32),dp(32),dp(32),dp(28),dp(28),dp(28),dp(28)});
        background.setStroke(dp(1), (skin.text & 0xFFFFFF) | 0x18000000);
        card.setBackground(background);

        if (title != null && title.length() > 0) {
            TextView head = new TextView(context);
            head.setText(title);
            head.setTextColor(skin.text);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            head.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            head.setGravity(Gravity.START);
            head.setPadding(dp(4), dp(4), dp(4), dp(10));
            card.addView(head);
            this.head = head;
        }

        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.VERTICAL);
    }

    static Panel with(Context context, Skin skin, String title) {
        return new Panel(context, skin, title);
    }

    /** Tighter rows and padding, for menus that should not fill the screen. */
    Panel compact() {
        compact = true;
        card.setPadding(dp(14), dp(8), dp(14), dp(10));
        if (head != null) {
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            head.setPadding(dp(4), dp(2), dp(4), dp(6));
        }
        return this;
    }

    /** A paragraph. */
    Panel text(String words) {
        TextView body = new TextView(context);
        body.setText(words);
        body.setTextColor(skin.text);
        body.setAlpha(0.84f);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setLineSpacing(0, 1.12f);
        body.setGravity(Gravity.START);
        LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        where.bottomMargin = dp(6);
        card.addView(body, where);
        group = null;
        return this;
    }

    /** Anything drawn by the caller, full width. */
    Panel view(View content) {
        LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        where.topMargin = dp(6);
        where.bottomMargin = dp(6);
        card.addView(content, where);
        if (content != group) group = null;
        return this;
    }

    // ------------------------------------------------------------ menu rows

    /** A quiet second line under the title. */
    Panel subtitle(String words) {
        if (head == null || words == null || words.length() == 0) return this;
        head.setPadding(dp(4), dp(4), dp(4), dp(2));
        TextView line = new TextView(context);
        line.setText(words);
        line.setTextColor(skin.muted());
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        line.setPadding(dp(4), 0, dp(4), dp(10));
        card.addView(line, card.indexOfChild(head) + 1);
        return this;
    }

    /** Starts a new segmented group, with a small accent heading when one is given. */
    Panel section(String heading) {
        group = null;
        if (heading != null && heading.length() > 0) {
            TextView label = new TextView(context);
            label.setText(heading);
            label.setTextColor(Accent.colour());
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            // room for the whole line: a tight height let the next row cover half of it
            label.setIncludeFontPadding(true);
            label.setGravity(Gravity.BOTTOM);
            label.setMinHeight(dp(compact ? 28 : 34));
            label.setPadding(dp(8), dp(compact ? 8 : 12), dp(8), dp(6));
            card.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return this;
    }

    private Expressive.Group segments() {
        if (group == null) {
            group = new Expressive.Group(context, (skin.text & 0xFFFFFF) | 0x14000000,
                    (Accent.colour() & 0xFFFFFF) | 0x33000000);
            view(group);
        }
        return group;
    }

    /** A menu row: shaped icon, title, an optional second line and a trailing glyph. Closes the sheet, then acts. */
    Panel item(String icon, String title, String detail, Click then) {
        return row(icon, title, detail, "chevron_right", false, false, then);
    }

    /** Same, with another trailing glyph (for instance "download"). */
    Panel item(String icon, String title, String detail, String trailing, Click then) {
        return row(icon, title, detail, trailing, false, false, then);
    }

    /** The one row that matters most: filled with the accent, standing on its own above the group. */
    Panel hero(String icon, String title, String detail, String trailing, Click then) {
        group = null;
        return row(icon, title, detail, trailing, true, false, then);
    }

    /**
     * A row that shows a value: the value is the title, the label sits under it.
     * Tapping runs `then` and leaves the sheet where it is (for instance to copy).
     */
    Panel info(String icon, String value, String label, Click then) {
        stayOpen = true;
        try {
            return row(icon, value, label, null, false, false, then);
        } finally {
            stayOpen = false;
        }
    }

    /** A row for something destructive. */
    Panel danger(String icon, String title, String detail, Click then) {
        return row(icon, title, detail, null, false, true, then);
    }

    /** One of several choices: a ring, filled with the accent when this is the chosen one. */
    Panel option(String title, boolean chosen, boolean stay, Click then) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setMinimumHeight(dp(56));
        TextView mark = new TextView(context);
        mark.setGravity(Gravity.CENTER);
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mark.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        if (chosen) {
            int accent = Accent.colour();
            ring.setColor(accent);
            mark.setText("✓");
            mark.setTextColor(onAccent(accent));
        } else {
            ring.setColor(0);
            ring.setStroke(dp(2), skin.muted());
        }
        mark.setBackground(ring);
        LinearLayout.LayoutParams markWhere = new LinearLayout.LayoutParams(dp(24), dp(24));
        markWhere.rightMargin = dp(14);
        row.addView(mark, markWhere);
        TextView name = new TextView(context);
        name.setText(title);
        name.setTextColor(chosen ? Accent.colour() : skin.text);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setClickable(true);
        row.setFocusable(true);
        row.setSelected(chosen);
        row.setContentDescription(title);
        row.setOnClickListener(v -> {
            if (!stay) close();
            if (then != null) {
                try { then.on(); } catch (Throwable error) { Diary.note("panel: " + error); }
            }
        });
        Motion.touch(row);
        segments().addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    private Panel row(String icon, String title, String detail, String trailing,
                      boolean hero, boolean danger, final Click then) {
        final boolean stay = stayOpen;
        int accent = Accent.colour();
        int tone = danger ? 0xFFE5484D : accent;
        int ink = hero ? onAccent(accent) : danger ? tone : skin.text;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padY = compact ? (hero ? 8 : 6) : (hero ? 12 : 10);
        row.setPadding(dp(14), dp(padY), dp(16), dp(padY));
        row.setMinimumHeight(dp(compact ? (hero ? 56 : 48) : (hero ? 68 : 60)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(detail == null ? title : title + ", " + detail);

        Expressive.Badge badge = new Expressive.Badge(context, shapeFor(icon), SettingsGlyph.make(icon, hero ? ink : tone),
                compact ? (hero ? 38 : 32) : (hero ? 44 : 40),
                hero ? (ink & 0xFFFFFF) | 0x2E000000 : (tone & 0xFFFFFF) | 0x26000000,
                hero ? (ink & 0xFFFFFF) | 0x2E000000 : (tone & 0xFFFFFF) | 0x26000000,
                hero ? ink : tone, hero ? ink : tone);
        LinearLayout.LayoutParams badgeWhere = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeWhere.rightMargin = dp(compact ? 10 : 14);
        row.addView(badge, badgeWhere);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(context);
        name.setText(title);
        name.setTextColor(ink);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? (hero ? 17 : 15) : (hero ? 20 : 16));
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (detail != null && detail.length() > 0) {
            TextView small = new TextView(context);
            small.setText(detail);
            small.setTextColor(ink);
            small.setAlpha(hero ? 0.82f : 0.62f);
            small.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            small.setSingleLine(true);
            small.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(small, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (trailing != null) {
            android.widget.ImageView end = new android.widget.ImageView(context);
            end.setImageDrawable(SettingsGlyph.make(trailing, hero ? ink : (skin.text & 0xFFFFFF) | 0xAA000000));
            LinearLayout.LayoutParams endWhere = new LinearLayout.LayoutParams(dp(22), dp(22));
            endWhere.leftMargin = dp(8);
            row.addView(end, endWhere);
        }
        row.setOnClickListener(v -> {
            if (!stay) close();
            if (then != null) {
                try { then.on(); } catch (Throwable error) { Diary.note("panel: " + error); }
            }
        });
        LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (hero) {
            row.setBackground(Expressive.tile(context, accent, 0x33FFFFFF, dp(28), dp(28), dp(14)));
            where.topMargin = dp(compact ? 2 : 4);
            where.bottomMargin = dp(compact ? 6 : 8);
            card.addView(row, where);
        } else {
            segments().addView(row, where);
        }
        return this;
    }

    /** A shape per icon, so a menu is not a column of identical circles. */
    private static int shapeFor(String icon) {
        int[] kinds = {Expressive.COOKIE9, Expressive.CLOVER4, Expressive.SUNNY, Expressive.FLOWER6, Expressive.COOKIE7};
        return kinds[Math.abs(icon == null ? 0 : icon.hashCode()) % kinds.length];
    }

    /** A line to type in, drawn like the rest of the card. */
    android.widget.EditText field(String hint) {
        android.widget.EditText field = new android.widget.EditText(context);
        field.setHint(hint);
        field.setHintTextColor(skin.muted());
        field.setTextColor(skin.text);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable box = new GradientDrawable();
        box.setColor(skin.page);
        box.setCornerRadius(dp(16));
        box.setStroke(dp(1), skin.muted());
        field.setBackground(box);
        view(field);
        return field;
    }

    /** The main button: filled. */
    Panel primary(String label, Click then) { return button(label, then, true, false); }

    /** The main button, for an action that may be refused: it closes the window itself. */
    Panel primaryKeepOpen(String label, Click then) { return button(label, then, true, true); }

    /** Any other: no fill, no weight. */
    Panel quiet(String label, Click then) { return button(label, then, false, false); }

    /** A button that does its thing and leaves the window where it is. */
    Panel keepOpen(String label, Click then) { return button(label, then, false, true); }

    /** Stateful steps can update their labels without rebuilding the sheet. */
    TextView action(String label, boolean main) {
        button(label,null,main,true);
        return (TextView)buttons.getChildAt(buttons.getChildCount()-1);
    }

    private Panel button(String label, final Click then, boolean main, final boolean stay) {
        TextView b = new TextView(context);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setPadding(dp(12), dp(compact ? 8 : 10), dp(12), dp(compact ? 8 : 10));
        if (main) {
            int accent = Accent.colour();
            b.setTextColor(onAccent(accent));
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            // a full pill that squares off a little while it is held
            b.setBackground(Expressive.tile(context, accent, 0x33FFFFFF, dp(24), dp(24), dp(12)));
        } else {
            // a tonal pill, so that every sheet ends the same way
            b.setTextColor(skin.text);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setBackground(Expressive.tile(context, (skin.text & 0xFFFFFF) | 0x14000000,
                    (Accent.colour() & 0xFFFFFF) | 0x33000000, dp(24), dp(24), dp(12)));
            b.setFocusable(true);
        }
        Motion.touch(b);
        b.setMinHeight(dp(compact ? 40 : 48));
        b.setFocusable(true);
        b.setOnClickListener(v -> {
            if (!stay) close();
            if (then != null) {
                try { then.on(); } catch (Throwable error) { Diary.note("panel: " + error); }
            }
        });
        LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        where.topMargin = dp(compact ? 4 : 8);
        buttons.addView(b, where);
        return this;
    }

    void close() {
        try { if (dialog.isShowing()) dialog.dismiss(); } catch (Throwable ignored) { }
    }

    Dialog dialog() { return dialog; }

    void busy(boolean busy, String message) {
        if (status == null) {
            status = new TextView(context);
            status.setTextColor(skin.text); status.setTextSize(13);Fonts.apply(status);
            status.setPadding(0, dp(12), 0, dp(4));
            card.addView(status, Math.max(0, card.indexOfChild(buttons)));
        }
        status.setText(message == null ? "" : message);
        Motion.change(status);
        setInputsEnabled(card,!busy);
        status.setVisibility(message == null || message.length() == 0 ? View.GONE : View.VISIBLE);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        for (int i = 0; i < buttons.getChildCount(); i++) {
            View button = buttons.getChildAt(i); button.setEnabled(!busy); button.setAlpha(busy ? 0.45f : 1f);
        }
        dialog.setCanceledOnTouchOutside(!busy);
    }

    private static void setInputsEnabled(View view,boolean enabled){
        if(view instanceof android.widget.EditText || view.hasOnClickListeners())view.setEnabled(enabled);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)setInputsEnabled(((ViewGroup)view).getChildAt(i),enabled);
    }

    void show() {
        try {
            // Scroll just the content. Actions and async status stay within reach.
            LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
            while(card.getChildCount()>0){View child=card.getChildAt(0);card.removeViewAt(0);body.addView(child);}
            ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);
            card.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            card.addView(buttons);
            Fonts.applyTree(card);
            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(480),
                        context.getResources().getDisplayMetrics().widthPixels - dp(24));
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.y = dp(12);
                window.setAttributes(params);
            }

        } catch (Throwable error) {
            Diary.note("panel: " + error);
        }
    }

    // ------------------------------------------------------------- pieces

    /**
     * A tile with a picture and a name, for choosing one of several.
     * `selected` draws the accent outline; the picture is painted with `colour`.
     */
    static LinearLayout tile(Context context, Skin skin, android.graphics.Bitmap picture,
                             int colour, String name, boolean selected) {
        float unit = context.getResources().getDisplayMetrics().density;
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(Math.round(unit * 4), Math.round(unit * 8), Math.round(unit * 4), Math.round(unit * 6));
        tile.setSelected(selected);
        tile.setFocusable(true);
        tile.setContentDescription(name);
        GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(unit * 14);
        back.setColor(skin.page);
        back.setStroke(Math.max(1, Math.round(selected ? unit * 2 : unit)), selected ? Accent.colour() : skin.muted());
        tile.setBackground(back);

        android.widget.ImageView image = new android.widget.ImageView(context);
        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if (picture != null) image.setImageBitmap(picture);
        if (colour != 0) image.setColorFilter(colour, android.graphics.PorterDuff.Mode.SRC_IN);
        tile.addView(image, new LinearLayout.LayoutParams(Math.round(unit * 36), Math.round(unit * 36)));

        TextView label = new TextView(context);
        label.setText(name);
        label.setTextColor(skin.text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Math.round(unit * 5), 0, 0);
        tile.addView(label);
        return tile;
    }

    private static int onAccent(int colour) {
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
