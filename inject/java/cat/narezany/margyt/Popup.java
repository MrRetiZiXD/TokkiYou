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
import android.widget.TextView;

/**
 * The mod's own dialog, in TikTok's language.
 *
 * TikTok has a good one -- rounded, centred, the screen dimmed behind it --
 * and it is behind an obfuscated name, which is the one thing this build will
 * not anchor on for something as small as a message: a renamed class would be
 * a crash where a sentence should have been.
 *
 * So it is drawn here instead, out of the same numbers. `Skin` measured the
 * card colour, the text colour and the corner radius off TikTok's own settings
 * screen, in whichever theme it was in, and the dialog is built from those --
 * which is why it matches without a single value being copied by eye.
 */
public final class Popup {

    private Popup() {}

    public static void show(Context context, String message) {
        show(context, null, message);
    }

    public static void show(Context context, String title, String message) {
        show(context, title, message, null);
    }

    /**
     * The same window with one picture under the words, drawn large.
     *
     * A badge is a few pixels beside a name and this is the one place it can
     * be looked at properly, so it is drawn at a size worth looking at rather
     * than at the size it sits at.
     */
    public static void show(Context context, String title, String message,
                            String button, final android.graphics.drawable.Drawable picture) {
        try {
            Skin skin = Skin.remembered(context);
            Dialog dialog = new SheetDialog(context, true);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            View card = card(context, skin, title, message, button, dialog);
            if (picture != null && card instanceof LinearLayout) {
                android.widget.ImageView view = new android.widget.ImageView(context);
                view.setImageDrawable(picture);
                view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                        dp(context, 96), dp(context, 96));
                where.gravity = Gravity.CENTER_HORIZONTAL;
                where.topMargin = dp(context, 16);
                LinearLayout holder = (LinearLayout) card;
                holder.addView(view, Math.max(0, holder.getChildCount() - 1), where);
            }

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 320),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.86f));
                params.gravity = Gravity.CENTER;
                params.y = 0;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup: " + error);
        }
    }

    public static void show(Context context, String title, String message,
                            String button, String[][] pictures, String[] captions) {
        try {
            Skin skin = Skin.remembered(context);
            Dialog dialog = new SheetDialog(context, true);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            View card = card(context, skin, title, message, button, dialog);
            View gallery = gallery(context, skin, pictures, captions);
            if (gallery != null && card instanceof LinearLayout) {
                LinearLayout holder = (LinearLayout) card;
                LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                where.topMargin = dp(context, 14);
                holder.addView(gallery, Math.max(0, holder.getChildCount() - 1), where);
            }

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 360),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f));
                params.gravity = Gravity.CENTER;
                params.y = 0;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup: " + error);
        }
    }

    /**
     * Several rows, each a picture with a title and a line under it.
     *
     * Used for the badges an account holds: one tap on a badge opens all of
     * them, not just the one that was touched. The rows scroll inside a
     * height cap, so an account with many badges never pushes the button off
     * the screen.
     */
    public static void showList(Context context, String title, String button,
                                android.graphics.drawable.Drawable[] pictures,
                                String[] titles, String[] texts) {
        try {
            Skin skin = Skin.remembered(context);
            Dialog dialog = new SheetDialog(context, true);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            View card = card(context, skin, title, null, button, dialog);
            if (card instanceof LinearLayout) {
                LinearLayout rows = new LinearLayout(context);
                rows.setOrientation(LinearLayout.VERTICAL);
                for (int i = 0; i < titles.length; i++) {
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(context, 8), 0, dp(context, 8));

                    android.widget.ImageView image = new android.widget.ImageView(context);
                    if (pictures != null && i < pictures.length && pictures[i] != null)
                        image.setImageDrawable(pictures[i]);
                    image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    row.addView(image, new LinearLayout.LayoutParams(
                            dp(context, 40), dp(context, 40)));

                    LinearLayout words = new LinearLayout(context);
                    words.setOrientation(LinearLayout.VERTICAL);
                    TextView name = new TextView(context);
                    name.setText(titles[i]);
                    name.setTextColor(skin.text);
                    name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    name.setTypeface(Typeface.DEFAULT_BOLD);
                    words.addView(name);
                    if (texts != null && i < texts.length && texts[i] != null
                            && texts[i].length() > 0) {
                        TextView line = new TextView(context);
                        line.setText(texts[i]);
                        line.setTextColor(skin.muted());
                        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        words.addView(line);
                    }
                    LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    beside.leftMargin = dp(context, 12);
                    row.addView(words, beside);
                    rows.addView(row);
                }

                final int cap = (int) (context.getResources().getDisplayMetrics()
                        .heightPixels * 0.5f);
                android.widget.ScrollView scroller = new android.widget.ScrollView(context) {
                    @Override
                    protected void onMeasure(int widthSpec, int heightSpec) {
                        super.onMeasure(widthSpec,
                                View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
                    }
                };
                scroller.setVerticalScrollBarEnabled(false);
                scroller.addView(rows);
                LinearLayout holder = (LinearLayout) card;
                LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                where.topMargin = dp(context, 4);
                holder.addView(scroller, Math.max(0, holder.getChildCount() - 1), where);
            }

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 340),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9f));
                params.gravity = Gravity.CENTER;
                params.y = 0;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup list: " + error);
        }
    }

    /**
     * Pictures side by side, one screenful at a time, each with its caption.
     *
     * A row inside a scroller rather than a pager: a pager is a library this
     * build does not have and would be a dependency for four photographs. The
     * page width is measured from the card, so a swipe lands on the next one
     * whatever the phone is.
     */
    private static View gallery(final Context context, Skin skin,
                                String[][] pictures, String[] captions) {
        if (pictures == null || pictures.length == 0) return null;

        final int width = Math.min(dp(context, 360),
                (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f))
                - dp(context, 48);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < pictures.length; i++) {
            LinearLayout page = new LinearLayout(context);
            page.setOrientation(LinearLayout.VERTICAL);

            android.graphics.Bitmap shot = decode(pictures[i]);
            if (shot != null) {
                android.widget.ImageView view = new android.widget.ImageView(context);
                view.setImageBitmap(round(context, shot, dp(context, 12)));
                view.setAdjustViewBounds(true);
                view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                page.addView(view, new LinearLayout.LayoutParams(
                        width, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            if (captions != null && i < captions.length) {
                TextView caption = new TextView(context);
                caption.setText(captions[i]);
                caption.setTextColor(skin.muted());
                caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                caption.setGravity(Gravity.CENTER);
                caption.setPadding(0, dp(context, 8), 0, 0);
                page.addView(caption, new LinearLayout.LayoutParams(
                        width, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(
                    width, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) beside.leftMargin = dp(context, 16);
            row.addView(page, beside);
        }

        android.widget.HorizontalScrollView scroller =
                new android.widget.HorizontalScrollView(context) {
                    @Override
                    public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                        // the card is not scrollable, so nothing else wants these
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return super.onInterceptTouchEvent(event);
                    }
                };
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(row);

        TextView hint = new TextView(context);
        hint.setText("‹ " + pictures.length + " ›");
        hint.setTextColor(skin.muted());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(context, 6), 0, 0);

        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.addView(hint);
        return holder;
    }

    /** The pieces the build split it into, put back together once. */
    private static android.graphics.Bitmap decode(String[] pieces) {
        if (pieces == null || pieces.length == 0) return null;
        android.graphics.Bitmap known = decoded.get(pieces);
        if (known != null) return known;
        try {
            StringBuilder whole = new StringBuilder();
            for (String piece : pieces) whole.append(piece);
            byte[] raw = android.util.Base64.decode(whole.toString(),
                    android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap =
                    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bitmap != null) decoded.put(pieces, bitmap);
            return bitmap;
        } catch (Throwable error) {
            Diary.note("popup picture: " + error);
            return null;
        }
    }

    private static final java.util.Map<String[], android.graphics.Bitmap> decoded =
            new java.util.HashMap<String[], android.graphics.Bitmap>();

    /** The same picture with its corners taken off. */
    private static android.graphics.Bitmap round(Context context,
                                                 android.graphics.Bitmap source,
                                                 float radius) {
        try {
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                    source.getWidth(), source.getHeight(),
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(out);
            android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.RectF bounds = new android.graphics.RectF(
                    0, 0, source.getWidth(), source.getHeight());
            canvas.drawRoundRect(bounds, radius * 2, radius * 2, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(source, 0, 0, paint);
            return out;
        } catch (Throwable ignored) {
            return source;
        }
    }

    /**
     * The same card, with something to do once it is gone.
     *
     * Dismissing counts: a refusal that only ran its `then` on the button
     * would leave whoever tapped outside the card sitting on a screen they
     * were just told they cannot use.
     */
    public static void told(Context context, String title, String message,
                            String button, final Runnable then) {
        show(context, title, message, button, then);
    }

    public static void show(Context context, String title, String message, String button) {
        show(context, title, message, button, (Runnable) null);
    }

    private static void show(Context context, String title, String message,
                             String button, final Runnable then) {
        try {
            Skin skin = Skin.remembered(context);
            Dialog dialog = new SheetDialog(context, true);

            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                // the card is the rounded thing, so the window itself is not
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            dialog.setContentView(card(context, skin, title, message, button, dialog));
            dialog.setCanceledOnTouchOutside(true);
            if (then != null) {
                dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(android.content.DialogInterface which) {
                        then.run();
                    }
                });
            }
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 320),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.86f));
                params.gravity = Gravity.CENTER;
                params.y = 0;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup: " + error);
            if (then != null) then.run();
        }
    }

    /**
     * A line of text to be edited, in the same card as everything else.
     *
     * Used where a setting is a word rather than a switch. Empty is allowed
     * and means the same as whatever the setting's own default is -- nothing
     * here decides that.
     */
    public static void write(Context context, String title, String current,
                             String button, final Written onDone) {
        try {
            Skin skin = Skin.remembered(context);
            final Dialog dialog = new SheetDialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(skin.card);
            background.setCornerRadius(Math.max(skin.radius, dp(context, 28)));
            card.setBackground(background);

            TextView head = new TextView(context);
            head.setText(title);
            head.setTextColor(skin.text);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setGravity(Gravity.CENTER);
            head.setPadding(0, 0, 0, dp(context, 12));
            card.addView(head);

            final android.widget.EditText field = new android.widget.EditText(context);
            field.setText(current == null ? "" : current);
            field.setTextColor(skin.text);
            field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            field.setSingleLine(true);
            field.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
            GradientDrawable box = new GradientDrawable();
            box.setColor(skin.page);
            box.setCornerRadius(dp(context, 14));
            box.setStroke(dp(context, 1), Accent.colour());
            field.setBackground(box);
            field.setSelection(field.getText().length());
            card.addView(field);

            TextView go = new TextView(context);
            go.setText(button);
            go.setTextColor(onAccent());
            go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            go.setTypeface(Typeface.DEFAULT_BOLD);
            go.setGravity(Gravity.CENTER);
            go.setPadding(0, dp(context, 12), 0, dp(context, 12));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Accent.colour());
            pill.setCornerRadius(dp(context, 20));
            go.setBackground(pill);
            LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            below.topMargin = dp(context, 12);
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        onDone.wrote(field.getText().toString());
                    } catch (Throwable ignored) {
                    }
                    dialog.dismiss();
                }
            });
            card.addView(go, below);

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 320),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.86f));
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.y = dp(context, 12);
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup: " + error);
        }
    }

    public interface Written {
        void wrote(String text);
    }

    /**
     * The card that proves an account: a code to copy and a button to press.
     *
     * It stays open while it is checked. Closing it to put the code in a bio
     * and finding the way back is a step nobody should have to remember, and
     * TikTok takes a moment to show a changed bio, so being able to press
     * again is the whole point.
     */
    public interface Reply {
        /**
         * Whether it worked, what went wrong, the code to show now, and
         * whether the server could not find the page on its own.
         */
        void said(boolean ok, String trouble, String code, boolean askName);
    }

    public interface Checking {
        void check(String name, Reply reply);
    }

    /** Asked when somebody wants a different code, rather than this one. */
    public interface Fresh {
        void want(Reply reply);
    }

    public static void prove(Context context, String code, final Checking checking,
                             final Fresh fresh) {
        try {
            Skin skin = Skin.remembered(context);
            final Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 24), dp(context, 24), dp(context, 24),
                    dp(context, 16));
            GradientDrawable background = new GradientDrawable();
            background.setColor(skin.card);
            background.setCornerRadius(Math.max(skin.radius, dp(context, 16)));
            card.setBackground(background);

            TextView head = new TextView(context);
            head.setText(Text.PROVE);
            head.setTextColor(skin.text);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setGravity(Gravity.CENTER);
            card.addView(head);

            TextView how = new TextView(context);
            how.setText(Text.PROVE_HOW);
            how.setTextColor(skin.muted());
            how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            how.setPadding(0, dp(context, 10), 0, dp(context, 14));
            card.addView(how);

            final TextView shown = new TextView(context);
            shown.setText(code);
            shown.setTextColor(skin.text);
            shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            shown.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            shown.setGravity(Gravity.CENTER);
            shown.setPadding(dp(context, 12), dp(context, 12), dp(context, 12),
                    dp(context, 12));
            GradientDrawable box = new GradientDrawable();
            box.setColor(skin.page);
            box.setCornerRadius(dp(context, 12));
            box.setStroke(dp(context, 1), Accent.colour());
            shown.setBackground(box);
            shown.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copy(v.getContext(), shown.getText().toString());
                    Screen.say(Text.PROVE_COPY);
                }
            });
            card.addView(shown, wide(context, 0));

            final android.widget.EditText named = new android.widget.EditText(context);
            named.setHint(Text.PROVE_NAME);
            named.setTextColor(skin.text);
            named.setHintTextColor(skin.muted());
            named.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            named.setSingleLine(true);
            named.setPadding(dp(context, 12), dp(context, 10), dp(context, 12),
                    dp(context, 10));
            GradientDrawable line = new GradientDrawable();
            line.setColor(skin.page);
            line.setCornerRadius(dp(context, 10));
            line.setStroke(dp(context, 1), skin.muted());
            named.setBackground(line);
            // only for the accounts whose page the server cannot reach from
            // the id: everybody else never sees a box to type in
            named.setVisibility(View.GONE);
            card.addView(named, wide(context, 10));

            final TextView again = new TextView(context);
            again.setText(Text.PROVE_FRESH);
            again.setTextColor(Accent.colour());
            again.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            again.setGravity(Gravity.CENTER);
            again.setPadding(dp(context, 8), dp(context, 10), dp(context, 8),
                    dp(context, 2));
            card.addView(again, wide(context, 0));

            final TextView tap = new TextView(context);
            tap.setText(Text.PROVE_TAP);
            tap.setTextColor(skin.muted());
            tap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tap.setGravity(Gravity.CENTER);
            tap.setPadding(0, dp(context, 8), 0, 0);
            card.addView(tap);

            final TextView go = new TextView(context);
            go.setText(Text.PROVE_CHECK);
            go.setTextColor(onAccent());
            go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            go.setTypeface(Typeface.DEFAULT_BOLD);
            go.setGravity(Gravity.CENTER);
            go.setPadding(0, dp(context, 12), 0, dp(context, 12));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Accent.colour());
            pill.setCornerRadius(dp(context, 12));
            go.setBackground(pill);
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!go.isEnabled()) return;
                    go.setEnabled(false);
                    go.setAlpha(0.6f);
                    tap.setText(Text.PROVE_CHECKING);
                    checking.check(named.getText().toString(), new Reply() {
                        @Override
                        public void said(boolean ok, String trouble, String fresh,
                                         boolean askName) {
                            if (ok) {
                                close(dialog);
                                return;
                            }
                            if (fresh != null && fresh.length() > 0) shown.setText(fresh);
                            if (askName) named.setVisibility(View.VISIBLE);
                            tap.setText(trouble);
                            go.setEnabled(true);
                            go.setAlpha(1f);
                        }
                    });
                }
            });
            card.addView(go, wide(context, 14));

            again.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (fresh == null) return;
                    again.setEnabled(false);
                    again.setAlpha(0.6f);
                    fresh.want(new Reply() {
                        @Override
                        public void said(boolean ok, String trouble, String made,
                                         boolean askName) {
                            if (made != null && made.length() > 0) shown.setText(made);
                            tap.setText(ok ? Text.PROVE_FRESH_DONE : trouble);
                            again.setEnabled(true);
                            again.setAlpha(1f);
                        }
                    });
                }
            });

            TextView hidden = new TextView(context);
            hidden.setText(Text.PROVE_HIDDEN);
            hidden.setTextColor(skin.muted());
            hidden.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hidden.setPadding(0, dp(context, 12), 0, 0);
            card.addView(hidden);

            TextView why = new TextView(context);
            why.setText(Text.PROVE_WHY);
            why.setTextColor(skin.muted());
            why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            why.setGravity(Gravity.CENTER);
            why.setPadding(0, dp(context, 12), 0, 0);
            card.addView(why);

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 340),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9f));
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup: " + error);
        }
    }

    private static LinearLayout.LayoutParams wide(Context context, int above) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, above);
        return params;
    }

    private static void copy(Context context, String text) {
        try {
            android.content.ClipboardManager board =
                    (android.content.ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (board != null) {
                board.setPrimaryClip(android.content.ClipData.newPlainText("TokkiYou", text));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * A question with two answers, and a box that can be ticked.
     *
     * The same card as `show`, with a second button drawn quietly beside the
     * accent one -- refusing should not look like the harder thing to do.
     */
    public static void ask(Context context, String title, String message,
                           String yes, final Runnable onYes,
                           String no, final Runnable onNo,
                           String tick, final Ticked onTick) {
        ask(context,title,message,yes,onYes,no,onNo,tick,onTick,false,true);
    }

    public static void askUpdate(Context context,String title,String message,
                                 String yes,Runnable onYes,String no,Runnable onNo,
                                 String tick,Ticked onTick) {
        ask(context,title,message,yes,onYes,no,onNo,tick,onTick,true,true);
    }

    public static void askUpdate(Context context,String title,String message,
                                 String yes,Runnable onYes,String no,Runnable onNo,
                                 String tick,Ticked onTick,boolean mandatory) {
        ask(context,title,message,yes,onYes,mandatory?null:no,onNo,
                mandatory?null:tick,onTick,true,!mandatory);
    }

    private static void ask(Context context,String title,String message,
                            String yes,final Runnable onYes,String no,final Runnable onNo,
                            String tick,final Ticked onTick,final boolean pinned,
                            final boolean cancelable) {
        try {
            Skin skin = Skin.remembered(context);
            final Dialog dialog = new SheetDialog(context);
            dialog.setCancelable(cancelable);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            LinearLayout card = pinned ? new LinearLayout(context) {
                @Override protected void onMeasure(int width,int height) {
                    int cap=Math.round(getResources().getDisplayMetrics().heightPixels*0.78f);
                    int available=View.MeasureSpec.getMode(height)==View.MeasureSpec.UNSPECIFIED
                            ? cap : Math.min(cap,View.MeasureSpec.getSize(height));
                    super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.AT_MOST));
                }
            } : new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(skin.card);
            background.setCornerRadius(Math.max(skin.radius, dp(context, 28)));
            card.setBackground(background);

            if (title != null && title.length() > 0) {
                TextView head = new TextView(context);
                head.setText(title);
                head.setTextColor(skin.text);
                head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                head.setTypeface(Typeface.DEFAULT_BOLD);
                head.setGravity(Gravity.CENTER);
                head.setPadding(0, 0, 0, dp(context, 8));
                card.addView(head);
            }

            TextView body = new TextView(context);
            body.setText(message);
            body.setTextColor(skin.text);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            body.setGravity(pinned ? Gravity.START : Gravity.CENTER);
            if (pinned) {
                android.widget.ScrollView notes=new android.widget.ScrollView(context);
                notes.setFillViewport(false);
                notes.setVerticalScrollBarEnabled(true);
                body.setTextIsSelectable(true);
                notes.addView(body,new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
                // Only notes can shrink and scroll; the footer remains a sibling.
                card.addView(notes,new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            } else card.addView(body);

            if (tick != null) {
                final M3Switch box = new M3Switch(context);
                box.colours(Accent.colour(), skin.muted(), skin.card);
                box.setChecked(false);
                box.setOnChanged(new M3Switch.OnChanged() {
                    @Override
                    public void onChanged(boolean on) {
                        if (onTick != null) onTick.ticked(on);
                    }
                });

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(context, 18), 0, 0);
                TextView label = new TextView(context);
                label.setText(tick);
                label.setTextColor(skin.muted());
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                row.addView(label, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(box);
                card.addView(row);
            }

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            place.topMargin = dp(context, 12);
            card.addView(buttons, place);

            if (no != null) {
                TextView refuse = new TextView(context);
                refuse.setMinHeight(dp(context,48));
                refuse.setText(no);
                refuse.setTextColor(skin.muted());
                refuse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                refuse.setGravity(Gravity.CENTER);
                refuse.setPadding(0, dp(context, 12), 0, dp(context, 12));
                refuse.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        close(dialog);
                        if (onNo != null) onNo.run();
                    }
                });
                buttons.addView(refuse, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }

            TextView accept = new TextView(context);
            accept.setMinHeight(dp(context,48));
            accept.setText(yes);
            accept.setTextColor(onAccent());
            accept.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            accept.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            accept.setGravity(Gravity.CENTER);
            accept.setPadding(0, dp(context, 12), 0, dp(context, 12));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Accent.colour());
            pill.setCornerRadius(dp(context, 24));
            accept.setBackground(pill);
            accept.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    close(dialog);
                    if (onYes != null) onYes.run();
                }
            });
            LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
            grow.leftMargin = no == null ? 0 : dp(context, 10);
            buttons.addView(accept, grow);

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 340),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.88f));
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.y = dp(context, 12);
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("ask: " + error);
        }
    }

    /** What a ticked box says. */
    public interface Ticked {
        void ticked(boolean on);
    }

    private static void close(Dialog dialog) {
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Throwable ignored) {
        }
    }

    private static View card(Context context, Skin skin, String title, String message,
                             String button, final Dialog dialog) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 12));

        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(Math.max(skin.radius, dp(context, 28)));
        card.setBackground(background);

        if (title != null && title.length() > 0) {
            TextView head = new TextView(context);
            head.setText(title);
            head.setTextColor(skin.text);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setGravity(Gravity.CENTER);
            head.setPadding(0, 0, 0, dp(context, 8));
            card.addView(head);
        }

        if (message != null) {
            TextView body = new TextView(context);
            body.setText(message);
            body.setTextColor(skin.text);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            body.setGravity(Gravity.CENTER);
            // long update notes used to push the button off the bottom of the
            // screen, where nothing could be done about them
            body.setMaxHeight((int) (context.getResources()
                    .getDisplayMetrics().heightPixels * 0.5f));
            body.setMovementMethod(new android.text.method.ScrollingMovementMethod());
            card.addView(body);
        }

        TextView close = new TextView(context);
        close.setText(button == null || button.length() == 0 ? Text.CLOSE : button);
        close.setTextColor(onAccent());
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        close.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(context, 12), 0, dp(context, 12));

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Accent.colour());
        pill.setCornerRadius(dp(context, 24));
        close.setBackground(pill);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 12);
        card.addView(close, params);
        return card;
    }

    /** Dark letters on a light accent, white on a dark one. */
    private static int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
