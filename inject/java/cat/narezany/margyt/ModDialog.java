package cat.narezany.margyt;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Existing dialog actions rendered with the mod's own sheet and typography. */
final class ModDialog {
    static final class Builder {
        private final Context context;
        private final Skin skin;
        private CharSequence title, message, positive, negative;
        private View content;
        private CharSequence[] choices;
        private int selected = -1;
        private DialogInterface.OnClickListener onPositive, onNegative, onChoice;
        Builder(Context context) { this(context, Skin.remembered(context)); }
        Builder(Context context, Skin skin) { this.context = context; this.skin = skin; }
        Builder setTitle(CharSequence v) { title = v; return this; }
        Builder setMessage(CharSequence v) { message = v; return this; }
        Builder setView(View v) { content = v; return this; }
        Builder setPositiveButton(CharSequence v, DialogInterface.OnClickListener listener) {
            positive = v; onPositive = listener; return this;
        }
        Builder setPositiveButton(int v, DialogInterface.OnClickListener listener) {
            return setPositiveButton(context.getString(v), listener);
        }
        Builder setNegativeButton(CharSequence v, DialogInterface.OnClickListener listener) {
            negative = v; onNegative = listener; return this;
        }
        Builder setNegativeButton(int v, DialogInterface.OnClickListener listener) {
            return setNegativeButton(context.getString(v), listener);
        }
        Builder setItems(CharSequence[] v, DialogInterface.OnClickListener listener) {
            choices = v; onChoice = listener; return this;
        }
        Builder setSingleChoiceItems(CharSequence[] v, int index, DialogInterface.OnClickListener listener) {
            choices = v; selected = index; onChoice = listener; return this;
        }
        android.app.Dialog show() {
            Panel panel = Panel.with(context, skin, title == null ? null : title.toString());
            if (message != null) panel.text(message.toString());
            if (content != null) panel.view(content);
            if (choices != null) {
                // one segmented group of rows; a ring marks the chosen one, a plain list closes on tap
                panel.section(null);
                for (int i = 0; i < choices.length; i++) {
                    final int index = i;
                    panel.option(String.valueOf(choices[i]), selected == i, selected >= 0,
                            () -> { if (onChoice != null) onChoice.onClick(panel.dialog(), index); });
                }
            }
            if (positive != null) panel.primary(positive.toString(),
                    () -> { if (onPositive != null) onPositive.onClick(panel.dialog(), DialogInterface.BUTTON_POSITIVE); });
            if (negative != null) panel.quiet(negative.toString(),
                    () -> { if (onNegative != null) onNegative.onClick(panel.dialog(), DialogInterface.BUTTON_NEGATIVE); });
            panel.show();
            return panel.dialog();
        }
    }
}
