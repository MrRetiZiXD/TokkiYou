package cat.narezany.tokkiyou;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.ss.android.ugc.aweme.profile.model.User;
import com.ss.android.ugc.profile.platform.base.data.UserProfileInfo;

/**
 * A mark after a name, wherever that name is written.
 *
 * There is no screen to patch for this. TikTok writes an account's name in a
 * dozen places -- the profile, every comment, the line under a video -- and
 * every one of them asks the same model the same question. So the answer
 * carries the mark: `getNickname()` comes back with one invisible character on
 * the end, and every one of those places puts it on screen without knowing.
 *
 * Then the text is caught on its way into a TextView and the character is
 * swapped for a picture. Nothing about the layout changes: an ImageSpan takes
 * the place of a character, so the name is measured and wrapped exactly as the
 * app intended.
 *
 * Which badge it is comes from `Badges`, which reads it out of the repository
 * rather than out of this file -- and the character itself says which one,
 * because by the time a view has the text there is nothing left to ask.
 */
public final class Badge {

    private Badge() {}

    /** The colour of the mod's own note, when a badge names none. */
    private static final int MINT = 0xFF8DD1B0;

    private static volatile Drawable note;
    private static volatile boolean noteTried;

    // ------------------------------------------------- where the name lands

    public static String getNickname(User user) {
        if (user == null) return null;
        String name = user.getNickname();
        // the id is asked for inside the guard: this is a landing place for
        // one of TikTok's own calls, and anything thrown here is thrown at
        // TikTok rather than at the mod
        try {
            return marked(name, user.getUid(), false);
        } catch (Throwable ignored) {
            return name;
        }
    }

    /**
     * The name read straight off the account, which is how TikTok Lite reads
     * it: Lite was built with the getter folded away, so there is no call to
     * stand in for and the read itself comes here. The full app still calls
     * the getter and is given back what was there.
     */
    public static String nickname(User user) {
        if (user == null) return null;
        String name = user.nickname;
        if (!Version.LITE) return name;
        try {
            return marked(name, user.getUid(), false);
        } catch (Throwable ignored) {
            return name;
        }
    }

    /** The same name, off the model a loaded profile uses instead. */
    public static String getNickname(UserProfileInfo user) {
        if (user == null) return null;
        String name = user.getNickname();
        try {
            return marked(name, user.getUid(), true);
        } catch (Throwable ignored) {
            return name;
        }
    }

    /**
     * What a name becomes: a word in front of it, and marks after it.
     *
     * Done once and not twice. A profile is built out of the model the feed
     * already had, so by the time the profile's own model is asked for the
     * name it is handing back a name this has already been through -- and
     * marking it again gave everybody two badges and two prefixes.
     */
    private static String marked(String name, String uid, boolean fromProfile) {
        if (name == null || name.length() == 0) return name;
        // with badges off and no plugin listening, a name is somebody else's
        // text and is handed back exactly as it came
        if (!Badges.isEnabled() && !Plugins.anyRunning()) return name;
        try {
            // Cleared first, always. Whatever marks are on the way in are
            // either ones the mod put there a moment ago -- in which case
            // adding them again is what gave everybody two badges -- or ones
            // somebody typed into their own name to wear a badge they were
            // never given. Neither survives; only what the account is owed is
            // put back.
            String own = Plugins.name(uid, strip(name));
            remember(uid, own, fromProfile);
            String marks = Badges.marksFor(uid) + Looks.mark(uid);
            if (marks.length() > 0) return own + '\u2009' + marks;
            return own;
        } catch (Throwable ignored) {
            return name;
        }
    }

    // ------------------------------------------------- a name in a conversation

    /**
     * The names in the inbox and at the head of a conversation.
     *
     * They are not read off `User` at all: a conversation has models of its
     * own, and a badge that showed under every video was missing from the one
     * place people talk to each other. The models differ and what they answer
     * to does not -- a name and an account id -- so they arrive as plain
     * objects and are asked by name.
     */
    public static String getDisplayName(Object contact) {
        return chatName(contact, "getDisplayName");
    }

    public static String getNickName(Object contact) {
        return chatName(contact, "getNickName");
    }

    private static String chatName(Object contact, String method) {
        if (contact == null) return null;
        String name = null;
        try {
            Object said = contact.getClass().getMethod(method).invoke(contact);
            name = said instanceof String ? (String) said : null;
            Object uid = contact.getClass().getMethod("getUid").invoke(contact);
            if (name == null || !(uid instanceof String)) return name;
            String id = (String) uid;
            return id.matches("[0-9]{1,24}") ? marked(name, id, false) : name;
        } catch (Throwable ignored) {
            // an account whose name could not be dressed still has a name
            return name;
        }
    }

    // ------------------------------------------- the name a profile just read

    /**
     * The one name worth putting back, and only for a moment.
     *
     * A profile writes its name again when it has finished loading, by a road
     * the mod does not stand on, and the mark goes with it. The first attempt
     * at fixing that remembered every name the mod had ever seen and put the
     * mark back on any text that matched one -- which marked the word in a
     * comment, in a bio, anywhere somebody wrote a name that happened to be
     * somebody's. That is not where a badge belongs.
     *
     * So what is kept is one name, the one a profile asked for in the last few
     * seconds, and nothing older. A badge still cannot appear anywhere except
     * where TikTok asked for a nickname -- it only survives the profile
     * finishing its work.
     */
    private static volatile String lastName;
    private static volatile String lastUid;
    private static volatile long lastAt;

    private static final long RECENT = 3000;

    /**
     * Only a profile's own model counts.
     *
     * A name read off `User` is read everywhere -- under every video, beside
     * every comment -- and remembering those is what put a badge on the word
     * somebody typed into a comment. `UserProfileInfo` is asked for a name by
     * one screen and one screen only.
     */
    private static void remember(String uid, String name, boolean fromProfile) {
        if (!fromProfile || uid == null || name == null || name.length() == 0) return;
        try {
            // a gradient is reason enough to remember whose profile this is,
            // badges or not
            if (Badges.marksFor(uid).length() == 0 && Looks.of(uid) == null) {
                return;
            }
            lastName = name;
            lastUid = uid;
            lastAt = android.os.SystemClock.uptimeMillis();
        } catch (Throwable ignored) {
        }
    }

    /** Put the mark back on the profile name that was read a moment ago. */
    public static void rewrite(View root) {
        String name = lastName;
        String uid = lastUid;
        if (root == null || name == null || uid == null) return;
        if (android.os.SystemClock.uptimeMillis() - lastAt > RECENT) return;
        try {
            seen = 0;
            walk(root, 0, name, uid);
        } catch (Throwable ignored) {
        }
    }

    /**
     * How deep to go, and how much to do at once.
     *
     * A profile or the inbox is a fragment inside a pager inside a list inside
     * a coordinator, and twenty levels is not unusual -- fourteen reached a
     * comment sheet and stopped well short of those.
     */
    private static final int DEEP = 40;
    private static final int BUDGET = 4000;

    private static boolean inAList(View view) {
        String name = view.getClass().getName();
        return name.contains("RecyclerView") || name.contains("ListView")
                || name.contains("ViewPager");
    }

    private static int seen;

    private static void walk(View view, int depth, String name, String uid) {
        if (view == null || depth > DEEP || ++seen > BUDGET) return;
        // A list is where the mod has no business rewriting text: a comment,
        // a message, a search result is somebody's words, and a word that
        // happens to be a name is still their word. A profile's own name is
        // not in a list -- it is in the header above one.
        if (inAList(view)) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence showing = text.getText();
            if (showing != null && name.contentEquals(showing)) {
                String out = marked(showing.toString(), uid, false);
                if (!out.equals(showing.toString())) setText(text, out);
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            int many = group.getChildCount();
            for (int i = 0; i < many; i++) walk(group.getChildAt(i), depth + 1, name, uid);
        }
    }

    /**
     * Anything being typed, cleared of the characters a badge is made of.
     *
     * A mark is invisible, so one that ends up in a box is one nobody can see
     * to delete: edit a name that has a badge on it and the mark comes with
     * it, and what gets saved is a name with somebody's badge inside it --
     * after which the mod adds its own and there are two. Every box is watched
     * as well as cleared, because text can arrive in one by being pasted.
     */
    private static CharSequence typed(TextView view, CharSequence text) {
        watch(view);
        if (text == null) return text;
        String cleaned = strip(text.toString());
        return cleaned.equals(text.toString()) ? text : cleaned;
    }

    private static void watch(TextView view) {
        if (Boolean.TRUE.equals(view.getTag(WATCHED))) return;
        try {
            view.setTag(WATCHED, Boolean.TRUE);
            view.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(android.text.Editable text) {
                    try {
                        for (int i = text.length() - 1; i >= 0; i--) {
                            if (Badges.isPrivate(text.charAt(i))) text.delete(i, i + 1);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // another key of its own: "Marh" + 1
    private static final int WATCHED = 0x4D61726B;

    /**
     * Every piece of text on its way into a TextView passes here.
     *
     * Which is a great many of them, so the common case is a type check and a
     * scan of a short string, and nothing else.
     */
    public static void setText(TextView view, CharSequence text) {
        long probe = Probe.start();
        Fonts.apply(view);
        if (view instanceof android.widget.EditText) {
            view.setText(typed(view, text));
            return;
        }
        CharSequence out = marked(view, text);
        Probe.stop(Probe.TEXT, probe);
        // asking for it to be kept spannable, because a TextView told to store
        // plain text copies the spans into an immutable SpannedString and the
        // tap has nothing left to find
        if (out != text) {
            view.setText(out, TextView.BufferType.SPANNABLE);
        } else {
            view.setText(out);
        }
    }

    public static void setText(TextView view, CharSequence text, TextView.BufferType type) {
        long probe = Probe.start();
        Fonts.apply(view);
        if (view instanceof android.widget.EditText) {
            view.setText(typed(view, text), type);
            return;
        }
        CharSequence out = marked(view, text);
        Probe.stop(Probe.TEXT, probe);
        view.setText(out, out != text ? TextView.BufferType.SPANNABLE : type);
    }

    private static CharSequence marked(TextView view, CharSequence text) {
        if (text == null) return text;

        Looks.clear(view);

        // Every label in the app comes through here, and nearly none of them
        // carries a mark. The marks all live in the private-use area, so one
        // comparison settles most characters before anything is asked.
        boolean any = false;
        for (int i = text.length() - 1; i >= 0 && !any; i--) {
            char c = text.charAt(i);
            if (c < '\uE000' || c > '\uF8FF') continue;
            any = Badges.isMark(c) || Looks.isMark(c);
        }
        if (!any) return text;

        try {
            // Anything that is text, not only a plain String. A profile writes
            // the name once while it loads and again when it is loaded, and
            // the second time it is rich text -- which is why the badge used
            // to appear on the way in and then vanish: the mark was still
            // there, invisible, and nothing turned it into a picture.
            //
            // Walked backwards so that dropping a mark cannot move the ones
            // not yet looked at.
            SpannableStringBuilder out = new SpannableStringBuilder(text);
            boolean drew = false;
            int[] gradient = null;
            for (int i = out.length() - 1; i >= 0; i--) {
                char c = out.charAt(i);
                if (Looks.isMark(c)) {
                    if (gradient == null) gradient = Looks.colours(c);
                    out.delete(i, i + 1);
                    continue;
                }
                if (!Badges.isMark(c)) continue;
                Badges.Badge badge = Badges.byMark(c);
                Drawable picture = badge == null ? null : picture(view, badge);
                if (picture == null) {
                    out.delete(i, i + 1);
                    continue;
                }
                out.setSpan(new ImageSpan(picture, ImageSpan.ALIGN_BOTTOM), i, i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new Tap(badge), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                drew = true;
            }
            if (gradient != null) Looks.paint(view, gradient);
            if (!drew) return tidy(out.toString());
            listen(view);
            return out;
        } catch (Throwable ignored) {
            // a name with a stray invisible character is bad; a name that
            // crashes the screen it is on is worse
            return strip(text.toString());
        }
    }

    /**
     * The name with nothing of the private-use area left in it.
     *
     * Not only the marks this run happens to use: the whole area. A name is
     * text somebody chose, and none of that text has any business being an
     * invisible character that the mod might one day draw as a picture.
     */
    private static String strip(String plain) {
        boolean any = false;
        for (int i = 0; i < plain.length() && !any; i++) {
            any = Badges.isPrivate(plain.charAt(i));
        }
        if (!any) return plain;

        StringBuilder out = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (!Badges.isPrivate(c)) out.append(c);
        }
        return tidy(out.toString());
    }

    /** No dangling separator, for a name whose marks all went away. */
    private static String tidy(String name) {
        return name.endsWith(" ") || name.endsWith(" ")
                ? name.substring(0, name.length() - 1) : name;
    }

    /** The badge at a size worth looking at, for the window it opens. */
    private static Drawable large(View view, Badges.Badge badge) {
        try {
            Bitmap bitmap = badge.image.length() == 0
                    ? null : Badges.picture(view.getContext(), badge.image);
            Drawable drawable;
            if (bitmap != null) {
                drawable = new BitmapDrawable(view.getResources(), bitmap);
                if (badge.colour != 0) {
                    drawable.setColorFilter(badge.colour, PorterDuff.Mode.SRC_IN);
                }
            } else {
                Drawable own = note();
                if (own == null) return null;
                drawable = own.getConstantState() == null
                        ? own : own.getConstantState().newDrawable().mutate();
                drawable.setColorFilter(badge.colour != 0 ? badge.colour : MINT,
                        PorterDuff.Mode.SRC_IN);
            }
            return drawable;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Sized to the text it sits in, so it matches whatever draws it. */
    private static Drawable picture(TextView view, Badges.Badge badge) {
        Bitmap bitmap = badge.image.length() == 0
                ? null : Badges.picture(view.getContext(), badge.image);

        Drawable drawable;
        if (bitmap != null) {
            drawable = new BitmapDrawable(bitmap);
            if (badge.colour != 0) drawable.setColorFilter(badge.colour, PorterDuff.Mode.SRC_IN);
        } else {
            Drawable own = note();
            if (own == null) return null;
            drawable = own.getConstantState() == null
                    ? own : own.getConstantState().newDrawable().mutate();
            drawable.setColorFilter(badge.colour != 0 ? badge.colour : MINT,
                    PorterDuff.Mode.SRC_IN);
        }

        int size = Math.round(view.getTextSize());
        if (size <= 0) size = Math.round(14 * view.getResources().getDisplayMetrics().density);

        // as wide as it is tall only if the drawing is. Trimming a badge to
        // its content leaves a picture of whatever shape that content is --
        // the crown makes the owner's taller than it is wide -- and forcing
        // that into a square stretches it.
        int wide = drawable.getIntrinsicWidth();
        int tall = drawable.getIntrinsicHeight();
        int across = wide > 0 && tall > 0 ? Math.round(size * (float) wide / tall) : size;
        drawable.setBounds(0, 0, Math.max(1, across), size);
        return drawable;
    }

    /** The note that ships with the mod, decoded once. */
    private static Drawable note() {
        Drawable known = note;
        if (known != null) return known;
        if (noteTried) return null;
        noteTried = true;
        try {
            byte[] png = Base64.decode(Emblem.PNG, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bitmap == null) return null;
            known = new BitmapDrawable(crop(bitmap));
            note = known;
            return known;
        } catch (Throwable error) {
            Diary.note("badge: " + error);
            return null;
        }
    }

    // ------------------------------------------------------------- the tap

    private static final class Tap extends ClickableSpan {
        private final Badges.Badge badge;

        Tap(Badges.Badge badge) {
            this.badge = badge;
        }

        @Override
        public void onClick(View widget) {
            if (Version.TEST || Version.LITE) android.util.Log.i("TokkiYou", "badge: tapped, in " + widget.getContext());
            String head = badge.title != null && badge.title.length() > 0
                    ? badge.title : "TokkiYou";
            Popup.show(widget.getContext(), head, badge.text, badge.button,
                    large(widget, badge));
        }

        @Override
        public void updateDrawState(android.text.TextPaint paint) {
            // no underline, no colour: the picture is the whole of it
        }
    }

    /**
     * Take the badge's taps, and only the badge's.
     *
     * A movement method is the usual way and it is the wrong one here. By the
     * time TextView consults it, View.onTouchEvent has already run and already
     * decided a click happened -- so tapping the emblem fired both the emblem
     * and whatever the name does. A touch listener runs before all of that: it
     * swallows the press and the release when they land on the emblem, so the
     * view never sees a click, and answers false everywhere else, so the name
     * goes on behaving exactly as it did.
     */
    private static void listen(TextView view) {
        if (Boolean.TRUE.equals(view.getTag(LISTENING))) return;
        try {
            view.setTag(LISTENING, Boolean.TRUE);
            view.setOnTouchListener(new Touch());
        } catch (Throwable ignored) {
        }
    }

    // setTag(int, ...) wants a key that looks like a resource id: "Marg" + 1
    private static final int LISTENING = 0x4D617268;

    private static final class Touch implements View.OnTouchListener {
        private boolean pressed;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                pressed = span(v, event) != null;
                if (Version.TEST || Version.LITE) android.util.Log.i("TokkiYou", "badge: a touch on a name, on the badge: " + pressed);
                return pressed;
            }
            if (!pressed) return false;
            if (action == MotionEvent.ACTION_UP) {
                pressed = false;
                ClickableSpan tapped = span(v, event);
                if (tapped != null) tapped.onClick(v);
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) pressed = false;
            return true;
        }

        /** The badge under the finger, or null. */
        private ClickableSpan span(View v, MotionEvent event) {
            try {
                TextView view = (TextView) v;
                CharSequence text = view.getText();
                // Spanned, not Spannable: what comes back out of a TextView is
                // read-only, and asking for the writable interface was why this
                // answered "not mine" to every tap it should have taken
                if (!(text instanceof Spanned)) return null;
                Layout layout = view.getLayout();
                if (layout == null) return null;

                int x = (int) event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
                int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
                int line = layout.getLineForVertical(y);
                // an offset is answered even for a miss well past the end of
                // the line, so the touch has to be inside the line as well
                if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null;

                int at = layout.getOffsetForHorizontal(line, x);
                ClickableSpan[] found = ((Spanned) text).getSpans(at, at, ClickableSpan.class);
                return found.length == 0 ? null : found[0];
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /**
     * Cut away what is fully transparent.
     *
     * An adaptive icon's foreground is drawn small inside a large square,
     * because the launcher masks and moves it. Beside a name none of that
     * applies and the empty margin is just a smaller emblem.
     */
    static Bitmap crop(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) < 8) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        if (right < left || bottom < top) return bitmap;
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
    }
}
