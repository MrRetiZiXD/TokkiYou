package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.util.Map;
import java.util.Locale;
import java.util.WeakHashMap;

/** Explicit per-role colours for TikTok controls with identifiable view names. */
public final class AppearanceColors {
    private AppearanceColors() {}

    public static final String ACCENT = "accent";
    public static final String BACKGROUND = "background";
    public static final String SECONDARY_BACKGROUND = "secondary_background";
    public static final String SURFACE = "surface";
    public static final String TEXT = "text";
    public static final String SECONDARY_TEXT = "secondary_text";
    public static final String DIVIDER = "divider";
    public static final String SELECTED_TAB = "selected_tab";
    public static final String NAV_ICON = "nav_icon";
    public static final String CONTROL = "control";
    public static final String SWITCH = "switch";
    public static final String PROGRESS = "progress";
    public static final String LIKE = "like";
    public static final String COMMENT_LIKE = "comment_like";
    public static final String PROFILE = "profile";
    public static final String NICKNAME = "nickname";

    public static final String[] ROLES = {
            ACCENT, BACKGROUND, SECONDARY_BACKGROUND, SURFACE, TEXT, SECONDARY_TEXT,
            DIVIDER, SELECTED_TAB, NAV_ICON, CONTROL, SWITCH, PROGRESS,
            PROFILE, NICKNAME
    };

    private static volatile int[] colours;
    private static volatile boolean coloursForDark;
    private static final Map<View, ColorStateList[]> originalText =
            new WeakHashMap<View, ColorStateList[]>();
    private static final Map<View, Drawable[]> originalBackground =
            new WeakHashMap<View, Drawable[]>();
    private static final Map<ImageView, android.graphics.ColorFilter[]> originalImageFilter =
            new WeakHashMap<ImageView, android.graphics.ColorFilter[]>();
    private static final Map<ImageView, ColorStateList[]> originalImageTint =
            new WeakHashMap<ImageView, ColorStateList[]>();

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null
                : AccountAppearance.prefs(context);
    }

    private static int[] values() {
        int[] current = colours;
        boolean dark = Themes.isDark();
        if (current != null && coloursForDark == dark) return current;
        synchronized (AppearanceColors.class) {
            current = colours;
            if (current == null || coloursForDark != dark) {
                current = new int[ROLES.length];
                SharedPreferences p = prefs();
                if (p != null) for (int i = 0; i < ROLES.length; i++)
                    if (p.contains(key(ROLES[i], dark)))
                        current[i] = p.getInt(key(ROLES[i], dark), 0);
                colours = current;
                coloursForDark = dark;
            }
        }
        return current;
    }
    static void reload(){colours=null;}

    public static int get(String role) {
        if (LIKE.equals(role)) return LikeColors.colour();
        if (COMMENT_LIKE.equals(role)) return LikeColors.commentColour();
        int at = index(role);
        return at < 0 ? 0 : values()[at];
    }

    public static void set(String role, int colour) {
        int at = index(role);
        if (at < 0) return;
        if (LIKE.equals(role)) {
            LikeColors.set(colour);
            Themes.repaintVisible();
            return;
        }
        if (COMMENT_LIKE.equals(role)) {
            LikeColors.setComment(colour);
            Themes.repaintVisible();
            return;
        }
        int normalized = 0xFF000000 | (colour & 0xFFFFFF);
        SharedPreferences p = prefs();
        boolean dark = Themes.isDark();
        if (p != null) p.edit().putInt(key(role, dark), normalized).apply();
        int[] next = values().clone();
        next[at] = normalized;
        colours = next;
        Accent.forget();
        if (ACCENT.equals(role)) Accent.set(normalized);
        Themes.repaintVisible();
    }

    public static void reset() {
        SharedPreferences p = prefs();
        if (p != null) {
            SharedPreferences.Editor edit = p.edit();
            for (String role : ROLES) {
                edit.remove(key(role, true));
                edit.remove(key(role, false));
            }
            edit.apply();
        }
        colours = new int[ROLES.length];
        coloursForDark = Themes.isDark();
        Accent.set(Accent.BUILT_WITH);
        LikeColors.reset();
        Accent.forget();
        Themes.repaintVisible();
    }

    /** Returns a role override only when the target's resource name is clear. */
    public static int forView(View view, int fallback) {
        if (view == null) return fallback;
        String name = Names.of(view);
        if (name.length() == 0 || mediaName(name)) return fallback;

        String role = role(name, view);
        if (followControlName(name) && view instanceof ImageView) return fallback;
        if (countBadgeName(name) && view instanceof TextView) return fallback;
        if (LIKE.equals(role) && view instanceof TextView) return fallback;
        if (COMMENT_LIKE.equals(role) && !activeLikeColour(fallback)) return fallback;
        int chosen = get(role);
        if (chosen == 0 && accentElement(name, role)) chosen = Accent.colour();
        return chosen == 0 ? fallback : chosen;
    }

    static void rememberImageFilter(ImageView view) {
        if (view == null) return;
        int mode = Themes.isDark() ? 1 : 0;
        synchronized (originalImageFilter) {
            android.graphics.ColorFilter[] saved = originalImageFilter.get(view);
            if (saved == null) {
                saved = new android.graphics.ColorFilter[2];
                originalImageFilter.put(view, saved);
            }
            if (saved[mode] == null) saved[mode] = view.getColorFilter();
        }
    }

    static void rememberImageTint(ImageView view, ColorStateList colours) {
        if (view == null) return;
        int mode = Themes.isDark() ? 1 : 0;
        synchronized (originalImageTint) {
            ColorStateList[] saved = originalImageTint.get(view);
            if (saved == null) {
                saved = new ColorStateList[2];
                originalImageTint.put(view, saved);
            }
            saved[mode] = colours;
        }
    }

    /** Repaints currently visible, semantically named text controls for preview/reset. */
    static void repaint(View root) {
        int[] visited = {0};
        repaintText(root, 0, visited);
    }

    // The same bargain the accent's walk makes: a view is remembered as it
    // was left, and passed over while it is still that. Whether it is
    // selected is part of what it is here, because a tab's role depends on it.
    private static final int LOOKED = 0x4D617273;

    private static int wearing(View view) {
        int all = Accent.epoch * 31 + (view.isSelected() ? 1 : 0);
        all = 31 * all + System.identityHashCode(view.getBackground());
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            all = 31 * all + System.identityHashCode(text.getTextColors());
            all = 31 * all + System.identityHashCode(text.getText());
        }
        if (view instanceof ImageView) {
            all = 31 * all + System.identityHashCode(((ImageView) view).getColorFilter());
        }
        return all;
    }

    /** One view, looked at if it has changed since the last time: what a walk calls. */
    static void look(View view) {
        Object last = view.getTag(LOOKED);
        if (!(last instanceof Integer) || ((Integer) last).intValue() != wearing(view)) {
            repaintOne(view);
            view.setTag(LOOKED, Integer.valueOf(wearing(view)));
        }
    }

    private static void repaintText(View view, int depth, int[] visited) {
        if (view == null || depth > 40 || ++visited[0] > 12000) return;
        look(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++)
                repaintText(group.getChildAt(i), depth + 1, visited);
        }
    }

    private static void repaintOne(View view) {
        boolean animatedDots = loadingDots(view);
        // Search input text keeps TikTok's normal foreground; only its caret
        // and selection handles follow a changed accent.
        if (view instanceof android.widget.EditText) Accent.tintTextCursor((TextView) view);
        if (view instanceof TextView) {
            if (!animatedDots) accentCreatorLabel((TextView) view);
            String textName = resourceName(view);
            if (!animatedDots && textName != null && !mediaName(textName)) {
                String role = role(textName, view);
                if (role != null && (!LIKE.equals(role) || view instanceof ImageView)
                        && !(CONTROL.equals(role) && countBadgeName(textName))) {
                    TextView text = (TextView) view;
                    ColorStateList original;
                    int mode = Themes.isDark() ? 1 : 0;
                    synchronized (originalText) {
                        ColorStateList[] saved = originalText.get(view);
                        if (saved == null) {
                            saved = new ColorStateList[2];
                            originalText.put(view, saved);
                        }
                        original = saved[mode];
                        if (original == null) {
                            original = text.getTextColors();
                            saved[mode] = original;
                        }
                    }
                    int chosen = get(role);
                    if (COMMENT_LIKE.equals(role)
                            && !activeLikeColour(text.getCurrentTextColor())) chosen = 0;
                    if (chosen == 0 && accentElement(textName, role)) chosen = Accent.colour();
                    if (chosen != 0) text.setTextColor(chosen);
                    else if (!COMMENT_LIKE.equals(role) && original != null)
                        text.setTextColor(original);
                }
            }
        }
        String name = resourceName(view);
        if (!animatedDots && name != null && !mediaName(name)) {
            String role = role(name, view);
            if (role != null && (SURFACE.equals(role) || BACKGROUND.equals(role)
                    || SECONDARY_BACKGROUND.equals(role) || PROFILE.equals(role)
                    || CONTROL.equals(role))) {
                Drawable background = view.getBackground();
                if (background instanceof ColorDrawable || background instanceof GradientDrawable) {
                    Drawable original;
                        int mode = Themes.isDark() ? 1 : 0;
                    synchronized (originalBackground) {
                        Drawable[] saved = originalBackground.get(view);
                        if (saved == null) {
                            saved = new Drawable[2];
                            originalBackground.put(view, saved);
                        }
                        original = saved[mode];
                        if (original == null) {
                            original = copy(background, view);
                            saved[mode] = original;
                        }
                    }
                    int chosen = get(role);
                    if (chosen == 0 && accentElement(name, role)) chosen = Accent.colour();
                    Drawable painted = chosen == 0 ? original : withColour(background, chosen);
                    if (painted != null) view.setBackground(painted);
                }
            }
        }
        if (!animatedDots && view instanceof ImageView) {
            String imageName = resourceName(view);
            if (imageName != null && !mediaName(imageName)) {
                String role = role(imageName, view);
                if (role != null
                        && !followControlName(imageName)
                        && !countBadgeName(imageName)) {
                    ImageView image = (ImageView) view;
                    rememberImageFilter(image);
                    int chosen = get(role);
                    if (chosen == 0 && accentElement(imageName, role)) chosen = Accent.colour();
                    if (LIKE.equals(role) || COMMENT_LIKE.equals(role)) {
                        ColorStateList tint;
                        synchronized (originalImageTint) {
                            ColorStateList[] saved = originalImageTint.get(image);
                            tint = saved == null ? null : saved[Themes.isDark() ? 1 : 0];
                        }
                        if (tint != null) Accent.setImageTintList(image, tint);
                        else if (chosen != 0) image.setColorFilter(chosen,
                                android.graphics.PorterDuff.Mode.SRC_IN);
                    } else if (chosen != 0) image.setColorFilter(chosen,
                            android.graphics.PorterDuff.Mode.SRC_IN);
                    else {
                        android.graphics.ColorFilter original;
                        int mode = Themes.isDark() ? 1 : 0;
                        synchronized (originalImageFilter) {
                            android.graphics.ColorFilter[] saved = originalImageFilter.get(image);
                            original = saved == null ? null : saved[mode];
                        }
                        if (original == null) image.clearColorFilter();
                        else image.setColorFilter(original);
                    }
                }
            }
        }
    }

    private static String resourceName(View view) {
        String name = Names.of(view);
        return name.length() == 0 ? null : name;
    }

    /** Keep TikTok's native two-colour loading animation intact. */
    static boolean loadingDots(View view) {
        if (view == null) return false;
        try {
            String name = resourceName(view);
            if (name != null && has(name, "loading_dot", "loading_dots", "loader_dot",
                    "progress_dot")) return true;
            String type = Names.type(view);
            return has(type, "loadingdots", "loading_dots", "dotindicator", "dotsindicator");
        } catch (Throwable ignored) { return false; }
    }

    /** Counts stay grey until TikTok marks the comment as liked. */
    private static boolean activeLikeColour(int colour) {
        return Palette.captures(colour, Accent.TIKTOK)
                || (Accent.colour() != Accent.TIKTOK
                && Palette.captures(colour, Accent.colour()));
    }

    static void accentCreatorLabel(TextView view) {
        if (view == null) return;
        CharSequence content = view.getText();
        if (content == null) return;
        String plain = content.toString();
        int start = plain.indexOf("Автор");
        int length = 5;
        String token = "Автор";
        if (start < 0) {
            start = plain.indexOf("Author");
            length = 6;
            token = "Author";
        }
        if (start < 0 || plain.length() > 100) return;
        int end = start + length;
        String name = resourceName(view);
        boolean creatorLabel = plain.trim().equals(token)
                || (start >= 2 && plain.charAt(start - 1) == ' '
                && plain.charAt(start - 2) == '\u00b7')
                || (name != null && has(name, "author", "creator"));
        if (!creatorLabel) return;
        if (start > 0 && Character.isLetter(plain.charAt(start - 1))) return;
        if (end < plain.length() && Character.isLetter(plain.charAt(end))) return;
        if (content instanceof android.text.Spanned) {
            android.text.Spanned old = (android.text.Spanned) content;
            CreatorSpan[] spans = old.getSpans(start, end, CreatorSpan.class);
            if (spans.length == 1 && spans[0].getForegroundColor() == Accent.colour()
                    && old.getSpanStart(spans[0]) == start && old.getSpanEnd(spans[0]) == end)
                return;
        }
        SpannableStringBuilder styled = new SpannableStringBuilder(content);
        CreatorSpan[] old = styled.getSpans(0, styled.length(), CreatorSpan.class);
        for (CreatorSpan span : old) styled.removeSpan(span);
        styled.setSpan(new CreatorSpan(Accent.colour()), start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(styled);
    }

    private static final class CreatorSpan extends ForegroundColorSpan {
        CreatorSpan(int colour) { super(colour); }
    }

    private static Drawable copy(Drawable drawable, View view) {
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            return state == null ? null : state.newDrawable(view.getResources()).mutate();
        } catch (Throwable ignored) { return null; }
    }

    private static Drawable withColour(Drawable drawable, int colour) {
        try {
            Drawable copy = drawable.getConstantState() == null ? drawable.mutate()
                    : drawable.getConstantState().newDrawable().mutate();
            if (copy instanceof ColorDrawable) ((ColorDrawable) copy).setColor(colour);
            else if (copy instanceof GradientDrawable) ((GradientDrawable) copy).setColor(colour);
            else return null;
            return copy;
        } catch (Throwable ignored) { return null; }
    }

    private static boolean mediaName(String name) {
        // These are controls layered over media (the follow badge on an
        // avatar) or badges in the feed navigation. Give their semantic role
        // precedence over words such as "avatar" in the same resource name.
        if (interactiveAccentName(name)) return false;
        return has(name, "video", "photo", "avatar", "thumbnail", "cover",
                "live_photo", "livephoto", "sticker", "emoji", "player");
    }

    private static boolean interactiveAccentName(String name) {
        return followControlName(name) || countBadgeName(name)
                || storyAddName(name) || onlineStatusName(name);
    }

    private static boolean storyAddName(String name) {
        return has(name,"story_add","add_story","story_create")
                || (has(name,"story","avatar","profile") && has(name,"add","plus","create"));
    }

    private static boolean onlineStatusName(String name) {
        return has(name,"online_status","online_dot","presence_online","active_status","status_dot")
                || (has(name,"online","presence") && has(name,"dot","status","indicator"));
    }

    private static boolean followControlName(String name) {
        return has(name, "follow_add", "add_follow", "button_follow", "follow_button")
                || (has(name, "follow") && has(name, "button", "action"));
    }

    private static boolean countBadgeName(String name) {
        return has(name, "unread_badge", "unread_count", "inbox_unread", "message_count",
                "notice_count", "badge_count", "notification_dot", "tab_badge")
                || (has(name, "unread") && has(name, "badge", "count"));
    }

    private static String role(String name, View view) {
        if (has(name, "comment") && has(name, "like", "heart", "digg"))
            return COMMENT_LIKE;
        if (has(name, "like", "heart", "digg")) return LIKE;
        if (has(name, "follow", "follow_add", "button_follow", "add_follow"))
            return CONTROL;
        if (storyAddName(name) || onlineStatusName(name)) return CONTROL;
        if (has(name, "unread", "message_count", "notice_count", "badge_count",
                "notification_dot", "tab_badge")) return CONTROL;
        if (has(name, "error", "empty_state", "not_found", "failed", "retry"))
            return CONTROL;
        if (has(name, "friend", "inbox", "message_tab", "notification_tab")
                && has(name, "tab", "nav", "icon", "image")) return NAV_ICON;
        if (has(name, "nickname", "nick_name", "username", "user_name"))
            return NICKNAME;
        if (has(name, "profile") && has(name, "accent", "name", "header"))
            return PROFILE;
        if (has(name, "switch", "toggle", "compound_button")) return SWITCH;
        if (has(name, "progress", "seekbar", "slider")) return PROGRESS;
        if (has(name, "tab", "navigation", "nav_")) {
            if (has(name, "tab") && view.isSelected()) return SELECTED_TAB;
            if (has(name, "icon", "image", "nav_")) return NAV_ICON;
            if (view.isSelected()) return SELECTED_TAB;
        }
        if (has(name, "divider", "separator", "border", "stroke")) return DIVIDER;
        if (has(name, "secondary_background", "background_secondary", "sub_background"))
            return SECONDARY_BACKGROUND;
        if (has(name, "card", "surface", "panel")) return SURFACE;
        if (has(name, "background", "page_bg", "root_bg")) return BACKGROUND;
        if (has(name, "secondary_text", "subtitle", "description", "hint", "caption"))
            return SECONDARY_TEXT;
        if (has(name, "text", "title", "label")) return TEXT;
        if (has(name, "accent", "primary_action", "selected")) return CONTROL;
        return null;
    }

    private static boolean accentElement(String name, String role) {
        return CONTROL.equals(role) || SELECTED_TAB.equals(role)
                || (NAV_ICON.equals(role) && has(name, "friend", "inbox", "message",
                        "notification"));
    }

    private static boolean has(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static int index(String role) {
        for (int i = 0; i < ROLES.length; i++) if (ROLES[i].equals(role)) return i;
        return -1;
    }

    private static String key(String role, boolean dark) {
        return "appearance_color_" + role + (dark ? "_dark" : "_light");
    }

    public static String label(String role) {
        String language = Locale.getDefault().getLanguage();
        boolean russian = "ru".equals(language);
        boolean ukrainian = "uk".equals(language);
        if (ACCENT.equals(role)) return russian ? "Цвет акцента" : ukrainian ? "Колір акценту" : "Accent color";
        if (BACKGROUND.equals(role)) return russian ? "Основной фон" : ukrainian ? "Основне тло" : "Primary background";
        if (SECONDARY_BACKGROUND.equals(role)) return russian ? "Дополнительный фон" : ukrainian ? "Додаткове тло" : "Secondary background";
        if (SURFACE.equals(role)) return russian ? "Панели и карточки" : ukrainian ? "Панелі й картки" : "Surfaces and cards";
        if (TEXT.equals(role)) return russian ? "Основной текст" : ukrainian ? "Основний текст" : "Primary text";
        if (SECONDARY_TEXT.equals(role)) return russian ? "Вторичный текст" : ukrainian ? "Другорядний текст" : "Secondary text";
        if (DIVIDER.equals(role)) return russian ? "Разделители" : ukrainian ? "Розділювачі" : "Dividers";
        if (SELECTED_TAB.equals(role)) return russian ? "Выбранная вкладка" : ukrainian ? "Вибрана вкладка" : "Selected tab";
        if (NAV_ICON.equals(role)) return russian ? "Значки навигации" : ukrainian ? "Значки навігації" : "Navigation icons";
        if (CONTROL.equals(role)) return russian ? "Активные элементы" : ukrainian ? "Активні елементи" : "Active controls";
        if (SWITCH.equals(role)) return russian ? "Включённые переключатели" : ukrainian ? "Увімкнені перемикачі" : "Active switches";
        if (PROGRESS.equals(role)) return russian ? "Ползунки и прогресс" : ukrainian ? "Повзунки та прогрес" : "Sliders and progress";
        if (LIKE.equals(role)) return russian ? "Лайки в ленте" : ukrainian ? "Вподобання у стрічці" : "Feed likes";
        if (COMMENT_LIKE.equals(role)) return russian ? "Лайки комментариев" : ukrainian ? "Вподобання коментарів" : "Comment likes";
        if (PROFILE.equals(role)) return russian ? "Акцент профиля" : ukrainian ? "Акцент профілю" : "Profile accent";
        if (NICKNAME.equals(role)) return russian ? "Цвет ника" : ukrainian ? "Колір імені" : "Nickname color";
        return role;
    }
}
