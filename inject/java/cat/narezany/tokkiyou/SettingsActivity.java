package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * TokkiYou's own screen, drawn in TikTok's settings language.
 *
 * Page, cards, title, grey section labels: the shapes are TikTok's, and so are
 * the colours -- not copied out of a screenshot but the ones the row measured
 * off the real settings screen and kept. Opened from the launcher with TikTok
 * never having been on screen, it falls back to plain black or white.
 *
 * Every view is built here in code. Adding a layout or a style would mean
 * adding resources, and adding resources means rewriting a 25 MB resource
 * table -- the one thing this build refuses to do.
 */
public class SettingsActivity extends Activity {
    void accountAppearanceChanged(){skin=Skin.remembered(this);rebuild();}

    private Skin skin;
    private FrameLayout root;
    private final java.util.Map<String,View> settingTargets=new java.util.HashMap<>();
    private LinearLayout column;
    private View restartBar;
    private RestartSymbol restartSymbol;
    private TextView restartWhy, restartAction;
    private GradientDrawable restartBackground;
    private boolean rebuildQueued;
    private boolean badgePlacementOpen,gradientFineOpen;
    private String disclosureKey,renderDisclosureKey;
    private boolean collapsing;
    private final java.util.Map<String,java.util.List<DisclosureBody>> disclosureBodies=new java.util.HashMap<>();
    private final java.util.Map<String,Chevron> disclosureArrows=new java.util.HashMap<>();
    private boolean revealAnimationRequested;
    private boolean revealAnimationUsed;
    private Runnable scrollRestore;
    private android.view.ViewTreeObserver.OnPreDrawListener scrollRestoreListener;
    private int requestedScrollY;
    private final Runnable delayedRebuild = new Runnable() {
        @Override
        public void run() {
            rebuildQueued = false;
            rebuild();
        }
    };

    /**
     * Whether a setting was changed in this process.
     *
     * Static, because the bar is about the process and not about the screen:
     * leaving the settings and coming back does not un-change what was changed,
     * and only a restart -- which is a new process, where this is false again --
     * does.
     */
    private static boolean pending;

    /** Where the mod, its people and its money live. */
    private static final String CHANNEL = "https://t.me/tokkiyou";
    private static final String FORUM = "https://t.me/tokkiyougroup";
    private static final String OWNER_TELEGRAM = "https://t.me/MrRetiZiXD";
    private static final String OWNER_TIKTOK =
            "https://www.tiktok.com/@mrretizixd?_r=1&_t=ZS-9AYHKwMoPEw";
    /** Where a receipt goes: the channel's own messages, not somebody's inbox. */
    private static final String CHANNEL_WRITE = "";
    private static final String GIT = "https://github.com/MrRetiZiXD/TokkiYou";
    private static final String DOCS_PLUGINS =
            "https://github.com/MrRetiZiXD/TokkiYou/blob/main/docs/plugins.md";
    private static final String YOOMONEY = "";

    private boolean countriesOpen;
    private boolean accentOpen;
    private Dot accentCustomSwatch;
    private boolean textOpen;
    private boolean backgroundOpen;
    private boolean themeColoursOpen;
    private boolean fontOpen;
    private boolean iconOpen;
    private ScrollView page;
    private boolean thanksOpen;
    private int settingsGroup;
    private boolean mineOpen;
    private java.util.List<Mine.Held> ordering;
    private boolean streakOpen;
    private boolean diaryOpen;
    /** Set once the badges have been rearranged by hand and not yet saved. */
    private boolean rearranged;
    /** The card the donation button brings the screen down to. */
    private View donateAnchor;
    private String searchQuery = "";
    private final java.util.Map<String, View> sectionTargets =
            new java.util.HashMap<String, View>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TokkiYou.attach(this);
        // drawn in what TikTok's own settings were last seen wearing, which is
        // the theme when one is on: this screen is opened from that one
        skin = Skin.remembered(this);
        Unsaved.look(this);
        dressTheWindow();
        // Test builds still check access; ordinary installs do not query the
        // account/badge service just by opening settings.
        if (Tester.on()) {
            Mine.ask(() -> {
                if (isFinishing() || isDestroyed()) return;
                rebuild();
                if (!Tester.allowed()) Tester.refuse(this);
            });
            if (Tester.asked() && !Tester.allowed()) Tester.refuse(this);
        }

        ScrollView scroll = page = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setFillViewport(true);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, statusBar(), 0, dp(32));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root=new FrameLayout(this);
        root.setBackgroundColor(skin.page);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        rebuild();
        maybeAskForSupport();
        if(getIntent().getBooleanExtra("tokkiyou_tutorial",false))root.postDelayed(()->Tutorial.show(this),350L);
    }

    void tutorialStep(int step) {
        settingsGroup=step==1 ? 1 : step==2 ? 3 : 0;
        if(step==1)accentOpen=true;
        searchQuery="";
        rebuild();
        String section=step==1 ? Text.ACCENT : step==2 ? BURN : null;
        View target=section==null ? null : sectionTargets.get(section);
        if(target!=null)page.post(()->page.smoothScrollTo(0,Math.max(0,target.getTop()-dp(20))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Streaks.isEnabled()){StreakAlarm.schedule(this,false);Streaks.round(this);}
        if(root!=null && column!=null)rebuild();
    }

    @Override protected void onPause(){
        navigating=false;
        if(column!=null){column.animate().withEndAction(null).cancel();column.setAlpha(1f);column.setTranslationX(0f);}
        super.onPause();
    }

    private void dressTheWindow() {
        try {
            getWindow().setStatusBarColor(skin.page);
            getWindow().setNavigationBarColor(skin.page);
            if (!skin.dark()) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------------- the screen

    private void rebuild() {
        navigating=false;
        collapsing=false;
        column.animate().withEndAction(null).cancel(); column.setAlpha(1f); column.setTranslationX(0f);
        revealAnimationUsed = false;
        boolean animateReveal = revealAnimationRequested;
        revealAnimationRequested = false;
        revealAnimationUsed = !animateReveal;
        final int previousScroll = page == null ? 0 : page.getScrollY();
        sectionTargets.clear();settingTargets.clear();
        disclosureBodies.clear();disclosureArrows.clear();renderDisclosureKey=null;
        column.removeAllViews();
        column.addView(backArrow());
        if (settingsGroup == DONATE_PAGE) {
            column.addView(title(Text.DONATE_BANNER));
            donatePage();
            showRestartBar();
            restoreScroll(0);
            return;
        }
        if (settingsGroup == FLAGS_PAGE) {
            column.addView(title(Text.DEV_FLAGS));
            flagsPage();
            showRestartBar();
            restoreScroll(previousScroll);
            return;
        }
        column.addView(title(settingsGroup == 0 ? (Version.LITE ? "TokkiYou Lite" : "TokkiYou") : groupName(settingsGroup)));
        if(settingsGroup==0 || searchQuery.length()>0)column.addView(searchBox());
        if (settingsGroup != 0) column.addView(wrap(actionRow("info",Local.of("Подсказки"),null,this::showSectionHelp)));
        if (searchQuery.length() > 0) {
            searchResults();
            restoreScroll(previousScroll);
            return;
        }

        if (settingsGroup == 0) {
            View donations = donateBanner();
            if (donations != null) column.addView(donations);
            View update = updateBanner();
            if (update != null) column.addView(update);
            View updateBanner = updateBanner();
            if (updateBanner != null) column.addView(updateBanner);
            column.addView(groupNavigation());
            column.addView(wrap(actionRow("info",Local.of("Обучение"),null,() -> Tutorial.show(this))));
            column.addView(versions());
            showRestartBar();
            restoreScroll(0);
            return;
        }

        column.addView(section(Text.APPEARANCE));
        LinearLayout icons = card();
        icons.addView(iconHead());
        if (iconOpen) {
            icons.addView(line());
            icons.addView(appear(iconChoices()));
        }
        column.addView(wrap(icons));

        column.addView(section(Text.ACCENT));
        LinearLayout accent = card();
        accent.addView(accentHead());
        if (accentOpen) {
            if (Accent.fromWallpaper() != 0) {
                accent.addView(line());
                accent.addView(appear(wallpaperRow()));
            }
            accent.addView(line());
            accent.addView(appear(palette()));
        }
        column.addView(wrap(accent));

        LinearLayout loading = card();
        loading.addView(liveToggleRow("palette", Text.LOADING_SHAPES, Loading.isEnabled(),
                Loading::setEnabled));
        loading.addView(quiet(Text.LOADING_NOTE));
        loading.addView(line());
        loading.addView(toggleRow("image", Text.ROUND_CARDS, Corners.isEnabled(),
                Corners::setEnabled));
        loading.addView(line());
        loading.addView(actionRow("account_circle", Text.AVATAR_SHAPE,
                AvatarShape.nameOf(AvatarShape.chosen()), this::chooseAvatarShape));
        loading.addView(line());
        loading.addView(toggleRow("view_compact", Text.NEW_PROFILE, Flags.isOn(Flags.KEY_NEW_PROFILE),
                on -> Flags.set(Flags.KEY_NEW_PROFILE, on)));
        loading.addView(line());
        loading.addView(toggleRow("tune", Text.NAV_CAPSULE, NavBar.isEnabled(), NavBar::setEnabled));
        loading.addView(line());
        loading.addView(toggleRow("tune", Text.MD3_FEED, Md3.isOn(Md3.KEY_FEED), on -> Md3.set(Md3.KEY_FEED, on)));
        loading.addView(line());
        loading.addView(toggleRow("tune", Text.MD3_ICONS, Md3.isOn(Md3.KEY_ICONS), on -> Md3.set(Md3.KEY_ICONS, on)));
        loading.addView(line());
        loading.addView(toggleRow("tune", Text.MD3_PROFILE, Md3.isOn(Md3.KEY_PROFILE), on -> Md3.set(Md3.KEY_PROFILE, on)));
        loading.addView(line());
        loading.addView(toggleRow("tune", Text.MD3_INBOX, Md3.isOn(Md3.KEY_INBOX), on -> Md3.set(Md3.KEY_INBOX, on)));
        column.addView(wrap(loading));

        column.addView(section(Text.THEME));
        LinearLayout theme = card();
        if (Themes.available()) {
            // on, and wearing the phone's own colours: one switch for both,
            // because the other way to be on is to pick a preset below
            theme.addView(toggleRow("wallpaper", Text.THEME_MATERIAL,
                    Themes.isEnabled() && Themes.isMaterial(), on -> {
                        Themes.setMaterial(on);
                        Themes.setEnabled(on);
                        markChanged();
                        rebuildAfterSwitchAnimation();
                    }));
            theme.addView(quiet(Text.THEME_NOTE));
        } else {
            theme.addView(quiet(Text.THEME_OLD));
        }
        theme.addView(line());
        theme.addView(actionRow("palette", Text.THEME_PRESETS, Text.THEME_PRESETS_NOTE,
                this::chooseThemePreset));
        theme.addView(line());
        theme.addView(themeColoursHead());
        if (themeColoursOpen) {
            theme.addView(line());
            for (String role : AppearanceColors.ROLES) {
                theme.addView(appearanceColourRow(role));
                theme.addView(line());
            }
        }
        column.addView(wrap(theme));

        column.addView(section(Text.FONT));
        LinearLayout fonts = card();
        fonts.addView(fontHead());
        if (fontOpen) {
            fonts.addView(line());
            fonts.addView(appear(fontChoices()));
        }
        column.addView(wrap(fonts));

        column.addView(section(Text.REGION));
        LinearLayout head = card();
        head.addView(switchRow());
        column.addView(wrap(head));

        column.addView(section(Text.COUNTRY));
        LinearLayout countries = card();
        countries.addView(countryHead());
        if (countriesOpen) {
            countries.addView(line());
            for (String[] country : TokkiYou.COUNTRIES) {
                countries.addView(appear(countryRow(country)));
            }
        }
        column.addView(wrap(countries));

        column.addView(section(Text.FEED));
        LinearLayout feed = card();
        feed.addView(toggleRow("block", Text.HIDE_ADS, Feed.isEnabled(), Feed::setEnabled));
        feed.addView(line());
        feed.addView(toggleRow("visibility_off", Text.HIDE_LIVE, Feed.hides(Feed.KEY_LIVE),
                on -> Feed.setHides(Feed.KEY_LIVE, on)));
        feed.addView(line());
        feed.addView(toggleRow("image", Text.HIDE_PHOTOS, Feed.hides(Feed.KEY_PHOTOS),
                on -> Feed.setHides(Feed.KEY_PHOTOS, on)));
        feed.addView(line());
        feed.addView(liveToggleRow("visibility_off",Text.HIDE_STORIES,Feed.hides(Feed.KEY_STORIES),on->Feed.setHides(Feed.KEY_STORIES,on)));
        feed.addView(liveToggleRow("visibility_off",Text.HIDE_SUGGESTIONS,Feed.hides(Feed.KEY_SUGGESTIONS),on->Feed.setHides(Feed.KEY_SUGGESTIONS,on)));
        column.addView(wrap(feed));

        column.addView(section(Text.TAGS));
        LinearLayout tags = card();
        tags.addView(betaRow("tag", Text.TAGS, Tags.isEnabled(), Tags::setEnabled));
        java.util.List<String> blocked = Tags.all();
        if (blocked.isEmpty()) {
            tags.addView(caption(Text.TAGS_NONE));
        } else {
            for (final String tag : blocked) {
                tags.addView(line());
                tags.addView(actionRow("block", "#" + tag, null, () -> {
                    Tags.remove(tag);
                    rebuild();
                }));
            }
        }
        tags.addView(line());
        tags.addView(actionRow("tag", Text.TAGS_ADD, null, () -> Popup.write(
                this, Text.TAGS_ADD, "", Text.TAGS_ADD, said -> {
                    if (!Tags.add(said)) {
                        Toast.makeText(this, Text.TAGS_FULL, Toast.LENGTH_SHORT).show();
                    }
                    rebuild();
                })));
        tags.addView(caption(Text.TAGS_NOTE));
        column.addView(wrap(tags));

        column.addView(section(Text.RAIL));
        LinearLayout rail=card();
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        String[] railIcons={"favorite_border","chat","star","share"};
        for(int i=0;i<railNames.length;i++){
            final int kind=i;
            rail.addView(liveToggleRow(railIcons[i],railNames[i],FeedRail.shown(i),on->FeedRail.setShown(kind,on)));
        }
        column.addView(wrap(rail));

        column.addView(section(Text.VIDEO));
        LinearLayout video = card();
        video.addView(actionRow("speed",Text.PLAYER_SPEED,Float.toString(PlayerTools.speed())+"×",()->PlayerTools.choose(this,this::rebuild)));
        video.addView(toggleRow("lock", Text.SPEED_LOCK, Flags.isOn(Flags.KEY_SPEED_LOCK),
                on -> Flags.set(Flags.KEY_SPEED_LOCK, on)));
        video.addView(toggleRow("volume_up", Text.SOUND, Sound.isEnabled(), Sound::setEnabled));
        video.addView(line());
        video.addView(liveToggleRow("timeline", Text.SEEKBAR, Seekbar.isEnabled(),
                Seekbar::setEnabled));
        video.addView(line());
        video.addView(liveToggleRow("info", Text.ALWAYS_DATE, Dates.isEnabled(),
                Dates::setEnabled));
        video.addView(line());
        video.addView(toggleRow("hdr_off", Text.OWN_BRIGHTNESS, Brightness.isEnabled(), on -> {
            Brightness.setEnabled(on);
            markChanged();
        }));
        video.addView(line());
        video.addView(liveToggleRow("visibility_off", Text.ZEN_MODE,
                TokkiYou.option(TokkiYou.KEY_ZEN_MODE), on -> TokkiYou.setOption(TokkiYou.KEY_ZEN_MODE, on)));
        video.addView(liveToggleRow("link", Text.CLEAN_LINKS,
                TokkiYou.option(TokkiYou.KEY_CLEAN_LINKS), on -> TokkiYou.setOption(TokkiYou.KEY_CLEAN_LINKS, on)));
        video.addView(liveToggleRow("content_copy", Text.COPY_METADATA,
                TokkiYou.option(TokkiYou.KEY_COPY_METADATA), on -> TokkiYou.setOption(TokkiYou.KEY_COPY_METADATA, on)));
        video.addView(caption(Text.OWN_BRIGHTNESS_NOTE));
        column.addView(wrap(video));

        column.addView(section(Text.FPS));
        LinearLayout fps = card();
        fps.addView(quiet(Text.FPS_ABOUT));
        String rate = Rate.name();
        for (int i = 0; i < Rate.CHOICES.length; i++) {
            final String which = Rate.CHOICES[i];
            if (i > 0) fps.addView(line());
            fps.addView(pickRow(fpsName(which), which.equals(rate), null, () -> {
                Rate.choose(which);
                markChanged();
            }));
        }
        if (!Rate.reachable(this)) fps.addView(quiet(Text.FPS_UNSUPPORTED));
        column.addView(wrap(fps));

        column.addView(section(Text.COMMENTS));
        LinearLayout comments = card();
        comments.addView(liveToggleRow("article", Text.COMMENT_COPY_ON, Comments.isCopyEnabled(),
                Comments::setCopyEnabled));
        column.addView(wrap(comments));

        column.addView(section(Text.TIKTOK_FEATURES));
        LinearLayout features = card();
        features.addView(quiet(Text.TIKTOK_FEATURES_NOTE));
        features.addView(toggleRow("repeat", Text.COMMENT_REPOST, Flags.isOn(Flags.KEY_COMMENT_REPOST),
                on -> Flags.set(Flags.KEY_COMMENT_REPOST, on)));
        features.addView(line());
        features.addView(toggleRow("repeat", Text.SELF_REPOST, Flags.isOn(Flags.KEY_SELF_REPOST),
                on -> Flags.set(Flags.KEY_SELF_REPOST, on)));
        features.addView(line());
        features.addView(toggleRow("language", Text.TRANSLATE_COMMENTS, Flags.isOn(Flags.KEY_TRANSLATE_COMMENTS),
                on -> Flags.set(Flags.KEY_TRANSLATE_COMMENTS, on)));
        column.addView(wrap(features));

        column.addView(section(BURN));
        LinearLayout burn = card();
        burn.addView(liveToggleRow("eye_open", Text.DIM, Dim.isConfigured(), on -> {
            Dim.setEnabled(on);
            rebuildAfterSwitchAnimation();
        }));
        if (Dim.isConfigured()) {
            burn.addView(line());
            burn.addView(slider("contrast", Text.DIM_HOW, Dim.strength(), 90, 0, "%", value -> Dim.setStrength(value)));
            burn.addView(line());
            burn.addView(toggleRow("visibility_off", Text.KEEP_BAR, NavBar.isKept(), NavBar::setKept));
        }
        burn.addView(quiet(Local.of("Глаз в ленте и на видео включает и выключает затемнение на лету. Поле ввода комментария не затемняется. Нижняя панель при затемнении прячется и возвращается на несколько секунд, если нажать на её место.")));
        column.addView(wrap(burn));

        column.addView(section(Text.DOWNLOADS));
        LinearLayout downloads = card();
        downloads.addView(actionRow("image",Text.FRAME_QUALITY,Integer.toString(FrameCapture.quality()),()->{
            new ModDialog.Builder(this).setItems(new String[]{"480","720","1080"},(dialog,index)->{AccountAppearance.prefs(this).edit().putInt(FrameCapture.KEY,new int[]{480,720,1080}[index]).apply();rebuild();}).show();
        }));
        downloads.addView(toggleRow("image", Text.NO_WATERMARK, Download.isEnabled(),
                Download::setEnabled));
        downloads.addView(line());
        downloads.addView(toggleRow("download", Text.DOWNLOAD_ALWAYS, Download.isAlways(),
                Download::setAlways));
        downloads.addView(line());
        downloads.addView(toggleRow("place", Text.SAVE_AVATARS_ON, Avatars.isEnabled(),
                Avatars::setEnabled));
        downloads.addView(line());
        downloads.addView(toggleRow("star", Text.SAVE_STICKERS_ON, Stickers.isEnabled(),
                Stickers::setEnabled));
        column.addView(wrap(downloads));

        column.addView(section(Text.BACKUP));
        LinearLayout backup = card();
        backup.addView(actionRow("upload", Text.BACKUP_EXPORT, Text.BACKUP_EXPORT_NOTE,
                this::createBackup));
        backup.addView(line());
        backup.addView(actionRow("download", Text.BACKUP_IMPORT, Text.BACKUP_IMPORT_NOTE,
                this::pickBackup));
        backup.addView(line());
        backup.addView(actionRow("restore", Text.BACKUP_RESET, Text.BACKUP_RESET_NOTE,
                this::pickResetGroup));
        column.addView(wrap(backup));

        column.addView(section(Text.PLUGINS));
        LinearLayout plugins = card();
        plugins.addView(actionRow("add", Local.of("Добавить плагин"), null, this::showPluginAdd));
        List<Plugins.Info> installed = Plugins.list();
        List<ScriptPlugins.Program> commands=ScriptPlugins.list(this);
        if(installed.isEmpty()&&commands.isEmpty())plugins.addView(quiet(Local.of("Пока нет установленных плагинов")));
        for(ScriptPlugins.Program program:commands){
            boolean enabled=getSharedPreferences(ScriptPlugins.STORE,0).getBoolean("on:"+program.id,false);
            plugins.addView(actionRow("extension",program.name,enabled?Local.of("Включён"):Local.of("Выключен"),()->ScriptPlugins.details(this,program,this::rebuild)));
        }
        for(Plugins.Info info:installed)plugins.addView(pluginRow(info));
        column.addView(wrap(plugins));

        column.addView(section(Text.PINNED_FRIENDS));
        LinearLayout friends=card();friends.addView(actionRow("people",Text.PINNED_FRIENDS,null,()->PinnedFriends.show(this)));
        column.addView(wrap(friends));

        column.addView(section(Text.STREAKS));
        LinearLayout streaks = card();
        streaks.addView(betaRow("repeat", Text.STREAK_AUTO, Streaks.isEnabled(),
                Streaks::setEnabled));
        streaks.addView(line());
        // What the schedule is standing on, said plainly: an inexact alarm, a
        // battery saver or a muted notification channel each stop the send, and
        // none of them is visible anywhere else in the app.
        streaks.addView(actionRow("bug_report", Text.STREAK_HEALTH, StreakAlarm.status(this), () -> {
            StreakAlarm.permissions(this);
            Toast.makeText(this, StreakAlarm.status(this), Toast.LENGTH_LONG).show();
        }));
        streaks.addView(line());
        streaks.addView(actionRow("schedule", Local.of("Время отправки"),StreakSchedule.label(),()->StreakSchedule.choose(this,this::rebuild)));
        streaks.addView(stickerHead());
        if (streakOpen) {
            streaks.addView(line());
            streaks.addView(appear(stickerChoices()));
        }
        streaks.addView(line());
        streaks.addView(actionRow("play_circle", Text.STREAK_ALL, Text.STREAK_ALL_NOTE,
                () -> Popup.ask(this, Text.STREAK_ALL, Text.STREAK_ALL_ASK,
                        Text.STREAK_ALL_SEND, () -> Streaks.sendAll(this),
                        Text.CANCEL, null, null, null)));
        streaks.addView(line());
        streaks.addView(actionRow("play_circle", Text.STREAK_TEST, Text.STREAK_TEST_NOTE,
                () -> {
                    Streaks.test(this);
                    Toast.makeText(this, Text.STREAK_TEST_GOING, Toast.LENGTH_SHORT).show();
                }));
        column.addView(wrap(streaks));

        column.addView(section(Text.NOTIFICATIONS));
        LinearLayout notifications=card();
        notifications.addView(liveToggleRow("chat",Text.NOTIFY_MESSAGES,ModNotifications.option(0),on->{ModNotifications.set(ModNotifications.MESSAGES,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications",Text.NOTIFY_PROMOS,ModNotifications.option(1),on->{ModNotifications.set(ModNotifications.PROMOS,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications",Text.NOTIFY_OTHER,ModNotifications.option(2),on->{ModNotifications.set(ModNotifications.OTHER,on);rebuildAfterSwitchAnimation();}));
        notifications.addView(liveToggleRow("notifications_off",Text.NOTIFY_NO_PROMPTS,ModNotifications.noPrompts(),on->{AccountAppearance.prefs(this).edit().putBoolean(ModNotifications.NO_PROMPTS,on).apply();ModNotifications.reload();}));
        notifications.addView(actionRow("settings",Text.NOTIFY_SETTINGS,null,()->ModNotifications.settings(this)));
        column.addView(wrap(notifications));

        column.addView(section(Text.CHANNEL));
        column.addView(channelBanner());

        column.addView(section(Text.UPDATE));
        LinearLayout updates = card();
        updates.addView(actionRow("download", Text.UPDATE_CHECK,
                Updater.newer() ? Text.UPDATE_THERE_IS + " " + Updater.latest() : null,
                () -> Updater.check(this, true)));
        updates.addView(line());
        updates.addView(toggleRow("info", Text.UPDATE_REMIND, Updater.remind(this),
                on -> Updater.setRemind(this, on)));
        if (Updater.waiting(this)) {
            updates.addView(line());
            updates.addView(actionRow("extension", Text.UPDATE_INSTALL, null,
                    () -> Updater.install(this)));
        }
        column.addView(wrap(updates));

        column.addView(section(Text.LINKS));
        LinearLayout links = card();
        links.addView(linkRow("group", Text.FORUM, "@tokkiyougroup", FORUM));
        links.addView(line());
        links.addView(linkRow("extension", Text.SOURCE, "MrRetiZiXD/TokkiYou", GIT));
        column.addView(wrap(links));

        column.addView(section(Text.THANKS));
        LinearLayout support = card();
        support.addView(thanksHead());
        if (thanksOpen) {
            support.addView(line());
            View details = thanks();
            support.addView(appear(details));
        }
        donateAnchor = wrap(support);
        column.addView(donateAnchor);

        filterGroup();
        column.addView(versions());

        showRestartBar();
        restoreScroll(previousScroll);
    }

    /** A row that opens something, with a word or two beside its name saying what kind of thing it is. */
    private View chipRow(String picture, String title, String[] chips, final Runnable action) {
        LinearLayout row = row();
        row.addView(icon(picture));
        row.addView(label(title));
        LinearLayout marks = new LinearLayout(this);
        marks.setGravity(Gravity.CENTER_VERTICAL);
        for (String word : chips) {
            TextView chip = new TextView(this);
            chip.setText(word);
            chip.setTextColor(onAccent());
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setPadding(dp(8), dp(3), dp(8), dp(3));
            GradientDrawable back = new GradientDrawable();
            back.setColor(Accent.colour());
            back.setCornerRadius(dp(12));
            chip.setBackground(back);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            place.rightMargin = dp(6);
            marks.addView(chip, place);
        }
        row.addView(marks, grow());
        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(SettingsGlyph.make("chevron_right", skin.muted()));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        row.setOnClickListener(v -> action.run());
        return sized(row, 56);
    }

    private void restoreScroll(final int scrollY) {
        Fonts.applyTree(root);
        if (page == null) return;
        requestedScrollY = Math.max(0, scrollY);
        if (scrollRestore != null) page.removeCallbacks(scrollRestore);
        if (scrollRestoreListener != null && page.getViewTreeObserver().isAlive()) {
            page.getViewTreeObserver().removeOnPreDrawListener(scrollRestoreListener);
            scrollRestoreListener = null;
        }
        if (requestedScrollY == 0) {
            page.scrollTo(0, 0);
            scrollRestore = null;
            return;
        }
        // Restore before the next frame is drawn. A posted scroll runs after
        // the new list has briefly appeared at the top of the screen.
        scrollRestoreListener = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                page.getViewTreeObserver().removeOnPreDrawListener(this);
                scrollRestoreListener = null;
                page.scrollTo(0, requestedScrollY);
                return true;
            }
        };
        page.getViewTreeObserver().addOnPreDrawListener(scrollRestoreListener);
    }

    /**
     * The bar that says a restart is due, pinned to the foot of the screen.
     *
     * It sits in the root frame rather than in the column, so it stays put
     * while the page scrolls under it, and it is built again on every rebuild
     * because the accent it is painted with may be what just changed.
     */
    private void showRestartBar() {
        if (pending && restartBar == null) {
            restartBar = restartBar();
            FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            floating.setMargins(dp(12), 0, dp(12), dp(12) + navigationBar());
            root.addView(restartBar, floating);
            Motion.rise(restartBar);
        }
        if (pending && restartBar instanceof LinearLayout) {
            LinearLayout bar = (LinearLayout)restartBar;
            styleRestartBar(bar);
            restartSymbol.colour(Accent.colour());
            restartWhy.setTextColor(skin.text);
            restartAction.setTextColor(onAccent());
            restartBackground.setColor(Accent.colour());
        }
        // Keep an existing bar in place: rebuilding rows must not replay its entrance.
        if (!pending && restartBar != null) {
            restartBar.animate().withEndAction(null).cancel();
            root.removeView(restartBar);
            restartBar = null;
            restartSymbol=null;restartWhy=null;restartAction=null;restartBackground=null;
        }
        column.setPadding(0, statusBar(), 0,
                dp(32) + (pending ? dp(92) + navigationBar() : 0));
    }

    /** The restart bar floats: a rounded tonal container with the accent laid over it. */
    private void styleRestartBar(LinearLayout bar) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Expressive.mix(skin.card, Accent.colour(), 0.22f));
        shape.setCornerRadius(dp(28));
        bar.setBackground(shape);
        bar.setElevation(dp(6));
    }

    /** A setting changed: redraw, and from now on the bar is up. */
    /** Something that needs a restart was changed on another screen: the store, say. */
    static void restartOwed() {
        pending = true;
        Unsaved.other();
    }

    private void markChanged() {
        // not simply "yes": put everything back as it was and there is
        // nothing left for a restart to apply
        pending = Unsaved.after(this);
        showRestartBar();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        // what the settings were before this tap changed any of them
        if (event != null && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            Unsaved.look(this);
        }
        return super.dispatchTouchEvent(event);
    }

    /** A live preview based on the identity TikTok has loaded for this account. */

    // ------------------------------------------------- what TokkiYou brings along

    /** Counted in preferences: asked on the third visit and then never again. */
    private static final String VISITS = "settings_visits";
    private static final String ASKED = "settings_asked";
    private static final int ON_VISIT = 3;

    private java.util.List<Integer> gradientColours;
    private int gradientSlot;

    /**
     * The first thing on the screen, and the only thing here that asks for
     * something. Short, because a long one is an advertisement.
     */
    private View donateBanner() {
        if (!donationsConfigured()) return null;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Math.max(skin.radius, dp(18)));
        background.setColor(skin.card);
        card.setBackground(background);

        TextView head = new TextView(this);
        head.setText(Text.DONATE_BANNER);
        head.setTextColor(skin.text);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(head);

        TextView body = new TextView(this);
        body.setText(Text.DONATE_BANNER_TEXT);
        body.setTextColor(skin.text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);

        TextView how = new TextView(this);
        how.setText(Text.DONATE_BANNER_HOW);
        how.setTextColor(skin.muted());
        how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        how.setPadding(0, dp(8), 0, 0);
        card.addView(how);

        TextView go = new TextView(this);
        go.setText(Text.DONATE_BANNER_BUTTON);
        go.setTextColor(onAccent());
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        go.setGravity(Gravity.CENTER);
        go.setPadding(0, dp(11), 0, dp(11));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Accent.colour());
        pill.setCornerRadius(dp(14));
        go.setBackground(pill);
        go.setOnClickListener(v -> showDonations());

        TextView write = new TextView(this);
        write.setText(Text.DONATE_BANNER_WRITE);
        write.setTextColor(skin.text);
        write.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        write.setGravity(Gravity.CENTER);
        write.setPadding(0, dp(11), 0, dp(11));
        GradientDrawable quiet = new GradientDrawable();
        quiet.setColor(0x00000000);
        quiet.setCornerRadius(dp(14));
        quiet.setStroke(dp(1), skin.muted());
        write.setBackground(quiet);
        write.setOnClickListener(v -> open(CHANNEL_WRITE));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        below.topMargin = dp(14);

        LinearLayout.LayoutParams half =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams second =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        second.leftMargin = dp(8);
        buttons.addView(go, half);
        buttons.addView(write, second);
        card.addView(buttons, below);

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(14), dp(20), dp(2));
        holder.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private boolean donationsConfigured() {
        return CHANNEL_WRITE.length() > 0 || YOOMONEY.length() > 0;
    }

    private View updateBanner() {
        if (!Updater.newer()) return null;
        LinearLayout card = card();
        card.addView(actionRow("download", Text.UPDATE_THERE_IS + " " + Updater.latest(),
                Updater.forceUpdate() ? Text.UPDATE : Text.UPDATE_CHECK,
                () -> Updater.offer(this)));
        return wrap(card);
    }

    /**
     * Ask once, on the third time this screen is opened.
     *
     * Once, and only once: refusing is remembered for good, and so is having
     * been asked. Somebody who opens the settings twenty times should be left
     * alone after the first answer.
     */
    private void maybeAskForSupport() {
        try {
            if (!donationsConfigured()) return;
            android.content.SharedPreferences prefs =
                    getSharedPreferences(TokkiYou.PREFS, MODE_PRIVATE);
            if (prefs.getBoolean(ASKED, false)) return;

            int visits = prefs.getInt(VISITS, 0) + 1;
            prefs.edit().putInt(VISITS, visits).apply();
            if (visits < ON_VISIT) return;
            prefs.edit().putBoolean(ASKED, true).apply();

            column.post(() -> Popup.ask(this, Text.REMIND_TITLE, Text.REMIND_TEXT,
                    Text.REMIND_MORE, this::showDonations,
                    Text.REMIND_NEVER, null,
                    null, null));
        } catch (Throwable error) {
            Diary.note("settings: " + error);
        }
    }

    private void tookFree(boolean ok, String trouble) {
        if (needsProof(ok, trouble)) return;
        Screen.say(ok ? Text.FREE_BADGE_GOT : Text.MINE_TOO_OFTEN);
        rebuild();
    }

    /**
     * Whether this account has proved it is anybody's, and the way to do it.
     *
     * At the head of the badge list because nothing under it saves until this
     * is done, and because the reason is worth seeing once.
     */
    private View proveRow() {
        boolean done = Proof.proved();
        LinearLayout row = row();
        row.setPadding(dp(16), 0, dp(16), 0);
        row.addView(icon(done ? "verified_user" : "fingerprint"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(done ? Text.PROVE_DONE : Text.PROVE));
        text.addView(detail(done ? Text.PROVE_DONE_NOTE : Text.PROVE_NOTE));
        row.addView(text, grow());

        if (!done) row.setOnClickListener(v -> proveAccount());
        return sized(row, 64);
    }

    /**
     * Prove the account: by itself where TikTok lets the bio be saved from
     * here, and by hand -- the card with the code -- where it does not, or
     * where doing it by itself did not work.
     */
    private void proveAccount() {
        if (!BioProof.available()) {
            proveByHand();
            return;
        }
        Screen.say(Text.PROVE_AUTO_GOING);
        BioProof.start((proved, said) -> {
            if (isFinishing() || isDestroyed()) return;
            if (proved) {
                Screen.say(Text.PROVE_OK);
                ordering = null;
                Mine.ask(this::rebuild);
                rebuild();
                return;
            }
            Screen.say(said);
            proveByHand();
        });
    }

    /** Ask for a code, then show the card that takes the name. */
    private void proveByHand() {
        String already = Proof.waiting();
        if (already.length() > 0) {
            askToCheck(already);
            return;
        }
        Screen.say(Text.MINE_WAIT);
        Proof.want((ok, trouble) -> {
            if (!ok) {
                Screen.say(trouble);
                return;
            }
            askToCheck(Proof.waiting());
        });
    }

    /** A refusal that only means the account has not been proved yet. */
    private boolean needsProof(boolean ok, String trouble) {
        if (ok || !Mine.PROVE.equals(trouble)) return false;
        proveAccount();
        return true;
    }

    private void askToCheck(String code) {
        Popup.prove(this, code, (name, reply) -> Proof.check(name, (ok, trouble) -> {
            if (ok) {
                Screen.say(Text.PROVE_OK);
                ordering = null;
                Mine.ask(this::rebuild);
            }
            // the card stays open on a no, with the trouble under the code
            reply.said(ok, trouble, Proof.waiting(), Proof.wantsName());
            rebuild();
        }), reply -> Proof.fresh((ok, trouble) ->
                reply.said(ok, trouble, Proof.waiting(), false)));
    }

    private LinearLayout gradientCard() {
        LinearLayout card = card();
        if (gradientColours == null) gradientColours = Gradient.starting();
        if (gradientSlot >= gradientColours.size()) gradientSlot = 0;

        card.addView(gradientPreview());
        card.addView(gradientDots());

        int[] hsv = Gradient.hsv(gradientColours.get(gradientSlot));
        card.addView(slider(Text.GRADIENT_HUE, hsv[0], 359, value -> setSlot(value, -1, -1)));
        card.addView(slider(Text.GRADIENT_SAT, hsv[1], 100, value -> setSlot(-1, value, -1)));
        card.addView(slider(Text.GRADIENT_VALUE, hsv[2], 100, value -> setSlot(-1, -1, value)));

        card.addView(line());
        if (gradientColours.size() < Gradient.MOST) {
            card.addView(actionRow("palette", Text.GRADIENT_ADD, null, () -> {
                gradientColours.add(gradientColours.get(gradientColours.size() - 1));
                gradientSlot = gradientColours.size() - 1;
                rebuild();
            }));
        }
        if (gradientColours.size() > Gradient.FEWEST) {
            card.addView(actionRow("block", Text.GRADIENT_DROP_ONE, null, () -> {
                gradientColours.remove(gradientSlot);
                gradientSlot = 0;
                rebuild();
            }));
        }
        card.addView(line());
        card.addView(actionRow("star", Text.GRADIENT_SAVE, null, () -> Gradient.save(
                gradientColours, (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Popup.show(this, Text.GRADIENT,
                            ok ? Text.GRADIENT_SAVED : trouble, Text.TEST_CLOSE);
                })));
        card.addView(actionRow("visibility_off", Text.GRADIENT_OFF, null, () -> Gradient.drop(
                (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Popup.show(this, Text.GRADIENT,
                            ok ? Text.GRADIENT_SAVED : trouble, Text.TEST_CLOSE);
                })));
        card.addView(caption(Text.GRADIENT_NOTE));
        return card;
    }

    private void setSlot(int hue, int saturation, int value) {
        int[] hsv = Gradient.hsv(gradientColours.get(gradientSlot));
        if (hue >= 0) hsv[0] = hue;
        if (saturation >= 0) hsv[1] = saturation;
        if (value >= 0) hsv[2] = value;
        gradientColours.set(gradientSlot, Gradient.from(hsv[0], hsv[1], hsv[2]));
        rebuild();
    }

    private View gradientPreview() {
        int[] colours = new int[gradientColours.size()];
        for (int i = 0; i < colours.length; i++) colours[i] = gradientColours.get(i);
        View bar = new View(this);
        android.graphics.drawable.GradientDrawable paint =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        colours);
        paint.setCornerRadius(dp(10));
        bar.setBackground(paint);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(16), dp(14), dp(16), dp(6));
        holder.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));
        return holder;
    }

    private View gradientDots() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(dp(16), dp(6), dp(16), dp(10));
        for (int i = 0; i < gradientColours.size(); i++) {
            final int at = i;
            Dot dot = new Dot(this, gradientColours.get(i), i == gradientSlot);
            dot.setOnClickListener(v -> {
                gradientSlot = at;
                rebuild();
            });
            line.addView(dot, new LinearLayout.LayoutParams(dp(36), dp(36), 1f));
        }
        return line;
    }

    private static String fpsName(String which) {
        if (which == null || which.length() == 0) return Text.FPS_AUTO;
        return String.format(Text.FPS_LOCKED, which + " FPS");
    }

    private static String named(Badges.Badge badge) {
        if (badge.title != null && badge.title.length() > 0) return badge.title;
        return badge.text;
    }

    /** A card number is read off the screen by a person, so it is grouped. */
    private static String spaced(String digits) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    /** Open the thanks, on the shelf they live on, and take the screen down to them. */
    /** Not one of the shelves: the page the "donate" button opens, and nothing else does. */
    private static final int DONATE_PAGE = 99;

    private void showDonations() {
        if (!donationsConfigured()) return;
        settingsGroup = DONATE_PAGE;
        searchQuery = "";
        rebuild();
    }

    /** Where the configured support links are shown. */
    private void donatePage() {
        LinearLayout what = card();
        what.addView(quiet(Text.DONATE_BANNER_TEXT));
        column.addView(wrap(what));

        column.addView(section(Text.DONATE_STEP_PAY));
        LinearLayout pay = card();
        pay.addView(linkRow("star", Text.YOOMONEY, Text.YOOMONEY_NOTE, YOOMONEY));
        column.addView(wrap(pay));

        column.addView(section(Text.DONATE_STEP_ID));
        LinearLayout mine = card();
        mine.addView(idRow(Text.ACCOUNT_ID, Account.id()));
        mine.addView(quiet(Text.DONATE_ID_NOTE));
        column.addView(wrap(mine));

        column.addView(section(Text.DONATE_STEP_WRITE));
        LinearLayout write = card();
        write.addView(actionRow("chat", Text.DONATE_BANNER_WRITE, Text.DONATE_WRITE_NOTE,
                () -> open(CHANNEL_WRITE)));
        write.addView(quiet(Text.DONATE_AFTER));
        column.addView(wrap(write));
    }

    private void rebuildAfterSwitchAnimation() {
        if (rebuildQueued) column.removeCallbacks(delayedRebuild);
        else rebuildQueued = true;
        column.postDelayed(delayedRebuild, M3Switch.ANIMATION_DURATION_MS);
    }

    // ----------------------------------------------------------- the rows

    private View switchRow() {
        LinearLayout row = row();
        final View lead = icon("language", true, 40);
        if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(TokkiYou.isEnabled(), false);
        row.addView(lead);
        row.addView(label(Text.CHANGE_REGION), grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(TokkiYou.isEnabled());
        toggle.setOnChanged(checked -> {
            if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(checked, true);
            TokkiYou.setEnabled(checked);
            markChanged();
            rebuildAfterSwitchAnimation();
        });
        row.addView(toggle);

        row.setOnClickListener(v -> {
            toggle.performClick();
        });
        return sized(row, 52);
    }

    private View countryHead() {
        String[] current = TokkiYou.current();
        LinearLayout row = row();
        row.addView(flag(current[TokkiYou.ISO]));
        row.setAlpha(TokkiYou.isEnabled() ? 1f : 0.4f);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(current[TokkiYou.LABEL]));
        text.addView(detail(current[TokkiYou.CARRIER] + "  ·  " + current[TokkiYou.MCCMNC]
                + "  ·  " + current[TokkiYou.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        row.addView(disclosure(countriesOpen,"countriesOpen"));

        if (TokkiYou.isEnabled()) {
            row.setOnClickListener(v -> {
                disclosureKey = "countriesOpen";
                countriesOpen = !countriesOpen;
                rebuildAfterReveal(false);
            });
        }
        return sized(row, 60);
    }

    private View countryRow(final String[] country) {
        boolean selected = country[TokkiYou.ISO].equals(TokkiYou.iso());
        LinearLayout row = row();
        row.addView(flag(country[TokkiYou.ISO]));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(country[TokkiYou.LABEL]));
        text.addView(detail(country[TokkiYou.CARRIER] + "  ·  " + country[TokkiYou.MCCMNC]
                + "  ·  " + country[TokkiYou.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        if (selected) row.addView(new Check(this, Accent.colour()));

        row.setOnClickListener(v -> {
            TokkiYou.setIso(country[TokkiYou.ISO]);
            countriesOpen = false;
            markChanged();
            rebuild();
        });
        return sized(row, 60);
    }

    /**
     * A country's flag, spelled rather than drawn.
     *
     * The two letters of a country's code have twin characters in the regional
     * indicator block, and a pair of those is a flag -- so `ru` becomes the
     * Russian flag with no picture involved, in whichever emoji font is on.
     * Which is also why it is worth having: with an emoji pack chosen in the
     * settings above, these are that pack's flags.
     */
    private View flag(String iso) {
        TextView view = new TextView(this);
        StringBuilder out = new StringBuilder();
        String code = iso.toUpperCase(Locale.US);
        for (int i = 0; i < code.length() && i < 2; i++) {
            int letter = code.charAt(i) - 'A';
            if (letter < 0 || letter > 25) return spacer();
            out.appendCodePoint(0x1F1E6 + letter);
        }
        view.setText(out.toString());
        // always Twemoji, whatever is chosen for the app: a phone's own flag
        // emoji is often two letters in a box, and this one is always a flag
        android.graphics.Typeface flags = Fonts.twemoji(this);
        if (flags != null) view.setTypeface(flags);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        view.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams size =
                new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT);
        size.rightMargin = dp(10);
        view.setLayoutParams(size);
        return view;
    }

    private View spacer() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(34), 1));
        return view;
    }

    private View disclosure(boolean expanded, String key) {
        renderDisclosureKey=expanded ? key : null;
        Chevron arrow=new Chevron(this,skin.muted(),expanded,key.equals(disclosureKey));
        disclosureArrows.put(key,arrow);return arrow;
    }

    private View accentHead() {
        LinearLayout row = row();
        row.addView(icon("palette"));
        row.addView(label(Text.ACCENT_COLOUR), grow());
        row.addView(new Dot(this, Accent.colour(), false));

        row.addView(disclosure(accentOpen,"accentOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "accentOpen";
            accentOpen = !accentOpen;
            rebuildAfterReveal(accentOpen);
        });
        return sized(row, 52);
    }

    private View themeColoursHead() {
        LinearLayout row = row();
        row.addView(icon("tune"));
        row.addView(label(Text.THEME_COLOURS), grow());
        row.addView(disclosure(themeColoursOpen,"themeColoursOpen"));
        row.setContentDescription(Text.THEME_COLOURS);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            disclosureKey = "themeColoursOpen";
            themeColoursOpen = !themeColoursOpen;
            rebuildAfterReveal(themeColoursOpen);
        });
        return sized(row, 52);
    }

    // ------------------------------------------------------------ own badges

    private View mineHead() {
        LinearLayout row = row();
        row.addView(icon("favorite_border"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.MINE));
        text.addView(detail(Text.MINE_NOTE));
        row.addView(text, grow());

        row.addView(disclosure(mineOpen,"mineOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "mineOpen";
            mineOpen = !mineOpen;
            if (mineOpen) {
                ordering = null;
                Mine.ask(this::rebuild);
            }
            rebuildAfterReveal(mineOpen);
        });
        return sized(row, 60);
    }

    private View mineRows() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        // the list the server last sent wins over whatever was being
        // rearranged: the first answer on a cold start is empty, and without
        // this that empty answer is what stayed on screen
        if (Mine.tookFresh() && !rearranged) ordering = null;
        if (ordering == null) ordering = Mine.held();
        rows.addView(proveRow());
        if (ordering.isEmpty()) {
            rows.addView(quiet(Mine.everAsked() ? Text.MINE_NONE : Text.MINE_WAIT));
            return rows;
        }

        for (int i = 0; i < ordering.size(); i++) {
            final int at = i;
            final Mine.Held one = ordering.get(i);
            if (i > 0) rows.addView(line());

            LinearLayout row = row();
            row.setPadding(dp(12), 0, dp(12), 0);

            Badges.Badge badge = Badges.byId(one.id);
            row.addView(badgeDot(badge));
            // the short name, which is what a badge is called; the long line
            // is its description and belongs in the popup, not in a list
            row.addView(label(badge == null ? one.id : named(badge)), grow());

            // up and down rather than dragging: a row that can be dragged has
            // to fight the page it is on for the same gesture
            row.addView(mover(true, at > 0, () -> {
                java.util.Collections.swap(ordering, at, at - 1);
                rearranged = true;
                rebuild();
            }));
            row.addView(mover(false, at < ordering.size() - 1, () -> {
                java.util.Collections.swap(ordering, at, at + 1);
                rearranged = true;
                rebuild();
            }));

            M3Switch toggle = new M3Switch(this);
            toggle.colours(Accent.colour(), skin.muted(), skin.card);
            toggle.setChecked(one.shown, false);
            toggle.setOnChanged(checked -> {
                one.shown = checked;
                rearranged = true;
            });
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(52), dp(32));
            size.leftMargin = dp(10);
            row.addView(toggle, size);

            rows.addView(sized(row, 60));
        }

        rows.addView(button(Text.MINE_SAVE, () -> Mine.save(ordering,
                (ok, trouble) -> {
                    if (needsProof(ok, trouble)) return;
                    Screen.say(ok ? Text.MINE_SAVED : Text.MINE_TOO_OFTEN);
                    if (ok) {
                        rearranged = false;
                        ordering = null;
                    }
                    rebuild();
                })));
        return rows;
    }

    /** Something to press, rather than a row that happens to do something. */
    private View button(String title, final Runnable action) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(onAccent());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(15), 0, dp(15));
        // a full pill that squares off a little while it is held
        view.setBackground(Expressive.tile(this, Accent.colour(), 0x33FFFFFF, dp(28), dp(28), dp(12)));
        view.setOnClickListener(v -> action.run());

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(16), dp(12), dp(16), dp(16));
        holder.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private View mover(boolean up, boolean can, final Runnable action) {
        android.widget.FrameLayout target=new android.widget.FrameLayout(this);
        Chevron arrow=new Chevron(this,can?skin.text:skin.muted(),up,false);
        android.widget.FrameLayout.LayoutParams image=new android.widget.FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);
        target.addView(arrow,image);
        target.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        target.setContentDescription(up ? Local.of("Выше") : Local.of("Ниже"));
        target.setEnabled(can);
        if (can) {Motion.press(target);target.setOnClickListener(v -> action.run());}
        return target;
    }

    private View badgeDot(Badges.Badge badge) {
        android.widget.ImageView view = new android.widget.ImageView(this);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(26), dp(26));
        size.rightMargin = dp(12);
        view.setLayoutParams(size);
        if (badge == null) return view;
        android.graphics.Bitmap picture = badge.image.length() == 0
                ? null : Badges.picture(this, badge.image);
        if (picture != null) {
            view.setImageBitmap(picture);
        } else {
            // a badge that names no picture of its own is the mod's own note,
            // which is the same thing it is drawn as beside a name
            view.setImageBitmap(Badges.note());
        }
        if (badge.colour != 0) {
            view.setColorFilter(badge.colour, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        return view;
    }

    /** The one that is free until it is not. */
    private View freeBanner() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Math.max(skin.radius, dp(18)));
        background.setColor(blend(0xFF40E0D0, skin.card, 0.82f));
        card.setBackground(background);

        TextView head = new TextView(this);
        head.setText(Text.FREE_BADGE);
        head.setTextColor(skin.text);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);

        TextView body = new TextView(this);
        body.setText(Text.FREE_BADGE_TEXT);
        body.setTextColor(skin.text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);

        TextView take = new TextView(this);
        take.setText(Text.FREE_BADGE_TAKE);
        take.setTextColor(0xFF10221F);
        take.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        take.setTypeface(Typeface.DEFAULT_BOLD);
        take.setGravity(Gravity.CENTER);
        take.setPadding(0, dp(11), 0, dp(11));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF40E0D0);
        pill.setCornerRadius(dp(14));
        take.setBackground(pill);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        below.topMargin = dp(14);
        take.setOnClickListener(v -> {
            if (!Mine.everAsked()) {
                Mine.ask(() -> Mine.takeFree(this::tookFree));
                return;
            }
            Mine.takeFree(this::tookFree);
        });
        card.addView(take, below);

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(14), dp(20), dp(2));
        holder.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private static int blend(int colour, int into, float how) {
        int red = (int) (((colour >> 16) & 0xFF) * (1 - how) + ((into >> 16) & 0xFF) * how);
        int green = (int) (((colour >> 8) & 0xFF) * (1 - how) + ((into >> 8) & 0xFF) * how);
        int blue = (int) ((colour & 0xFF) * (1 - how) + (into & 0xFF) * how);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    // -------------------------------------------------------- the colours

    private View wallpaperRow() {
        final int colour = Accent.fromWallpaper();
        LinearLayout row = row();
        row.addView(icon("wallpaper"));
        row.addView(label(Text.ACCENT_WALLPAPER), grow());
        row.addView(new Dot(this, colour, Accent.isMaterial()));
        row.setOnClickListener(v -> {
            Accent.setMaterial(true);
            markChanged();
            rebuild();
        });
        return sized(row, 52);
    }

    /** The inline body expands with the disclosure arrow, as one group. */
    private View appear(View view) {
        boolean animated=!revealAnimationUsed && Motion.enabled(this);
        if(animated)revealAnimationUsed=true;
        DisclosureBody body=new DisclosureBody(this,view,animated);
        if(renderDisclosureKey!=null) {
            java.util.List<DisclosureBody> list=disclosureBodies.get(renderDisclosureKey);
            if(list==null){list=new java.util.ArrayList<>();disclosureBodies.put(renderDisclosureKey,list);}
            list.add(body);
        }
        return body;
    }

    private void rebuildAfterReveal(boolean expanded) {
        if(collapsing)return;
        java.util.List<DisclosureBody> bodies=disclosureBodies.get(disclosureKey);
        if(!expanded && bodies!=null && !bodies.isEmpty() && Motion.enabled(this)) {
            collapsing=true;
            Chevron arrow=disclosureArrows.get(disclosureKey);
            if(arrow!=null)arrow.animate().rotation(0f).setDuration(180L).start();
            final int[] remaining={bodies.size()};
            for(DisclosureBody body:new java.util.ArrayList<>(bodies))body.collapse(()->{
                if(--remaining[0]==0){collapsing=false;revealAnimationRequested=false;disclosureKey=null;rebuild();}
            });
            return;
        }
        revealAnimationRequested=expanded;rebuild();disclosureKey=null;
    }







    private static final int CREATE_BACKUP = 0x4D42; // "MB"
    private static final int PICK_BACKUP = 0x4D49;   // "MI"

    private void createBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "tokkiyou-settings.json");
            startActivityForResult(intent, CREATE_BACKUP);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void pickBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, PICK_BACKUP);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void reviewBackup(final Uri source) {
        Screen.progress(Text.BACKUP_READING, 0);
        Net.away("backup", () -> {
            final Backup.Review review = Backup.review(this, source);
            if (review.fits()) Screen.progressDone(); else Screen.progressGone();
            runOnUiThread(() -> {
                if (!review.fits()) {
                    Screen.say(Text.BACKUP_FAILED);
                    return;
                }
                new ModDialog.Builder(this, skin)
                        .setTitle(Text.BACKUP_IMPORT)
                        .setMessage(Text.backupPreview(review.count()))
                        .setNegativeButton(Text.CANCEL, null)
                        .setPositiveButton(Text.BACKUP_APPLY, (dialog, which) -> {
                            if (Backup.apply(this, review)) {
                                markChanged();
                                rebuild();
                                Screen.say(Text.BACKUP_DONE);
                            } else Screen.say(Text.BACKUP_FAILED);
                        })
                        .show();
            });
        });
    }

    private void pickResetGroup() {
        final String[] groups = {
                Text.RESET_APPEARANCE, Text.RESET_PLAYER, Text.RESET_FEED, Text.RESET_ALL
        };
        try {
            new ModDialog.Builder(this, skin)
                    .setTitle(Text.BACKUP_RESET)
                    .setMessage(Text.BACKUP_RESET_ASK)
                    .setItems(groups, (dialog, chosen) -> askToReset(chosen + 1))
                    .setNegativeButton(Text.CANCEL, null)
                    .show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void askToReset(final int group) {
        new ModDialog.Builder(this, skin)
                .setTitle(Text.BACKUP_RESET)
                .setMessage(Text.BACKUP_RESET_ASK)
                .setNegativeButton(Text.CANCEL, null)
                .setPositiveButton(Text.BACKUP_APPLY, (dialog, which) -> {
                    Backup.reset(this, group);
                    markChanged();
                    rebuild();
                })
                .show();
    }

    /** A value between nothing and something, set by dragging. */
    private interface Chosen {
        void at(int value);
    }

    private View slider(String title, int now, int most, final Chosen chosen) {
        return slider(title, now, most, 0, "", chosen);
    }

    /** Ползунок с живым числом: показывает (положение + shift) и единицу измерения. */
    private View slider(String title, int now, int most, final int shift, final String unit, final Chosen chosen) {
        return slider("tune", title, now, most, shift, unit, chosen);
    }

    /** The same, with its own icon in front so no row of the list is left bare. */
    private View slider(String picture, String title, int now, int most, final int shift, final String unit, final Chosen chosen) {
        return slider(picture,title,now,most,shift,unit,chosen,false);
    }
    private View liveSlider(String picture,String title,int now,int most,int shift,String unit,Chosen chosen){
        return slider(picture,title,now,most,shift,unit,chosen,true);
    }
    private View slider(String picture, String title, int now, int most, final int shift, final String unit, final Chosen chosen, final boolean live) {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(10), dp(20), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(picture));
        head.addView(label(title), new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView number = new TextView(this);
        number.setTextColor(accentInk());
        number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        number.setText((Math.min(now, most) + shift) + unit);
        head.addView(number);
        rows.addView(head);

        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(most);
        bar.setProgress(Math.min(now, most));
        Expressive.slider(bar, Accent.colour(), Expressive.mix(skin.card, skin.text, 0.2f));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seek, int value, boolean human) {
                number.setText((value + shift) + unit);
                if(live && human)chosen.at(value);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seek) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seek) {
                if(!live)chosen.at(seek.getProgress());
            }
        });
        LinearLayout.LayoutParams barSize = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barSize.leftMargin = dp(56);
        rows.addView(bar, barSize);
        return rows;
    }


    // ---------------------------------------------------------- the font

    private static final int PICK_FONT = 0x4D46;  // "MF"
    private static final int PICK_EMOJI = 0x4D45;  // "ME"
    private static final int PICK_PROFILE_BADGE = 0x5450; // "TP"




    private GradientDrawable colourPreview(int colour) {
        GradientDrawable preview = new GradientDrawable();
        preview.setColor(colour);
        preview.setCornerRadius(dp(12));
        preview.setStroke(dp(1), skin.muted());
        return preview;
    }

    private int contrastingText(int colour) {
        float luminance = (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
        return luminance > 0.55f ? Color.BLACK : Color.WHITE;
    }




    private View fontHead() {
        LinearLayout row = row();
        row.addView(icon("text_fields"));
        row.addView(label(Text.FONT), grow());

        TextView now = detail(fontName(Fonts.name()));
        now.setTextColor(skin.text);
        row.addView(now);

        row.addView(disclosure(fontOpen,"fontOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "fontOpen";
            fontOpen = !fontOpen;
            rebuildAfterReveal(fontOpen);
        });
        return sized(row, 52);
    }

    private static String fontName(String which) {
        if (Fonts.NUNITO.equals(which)) return Text.FONT_NUNITO;
        if (Fonts.SANS.equals(which)) return Text.FONT_SANS;
        if (Fonts.SANS_LIGHT.equals(which)) return Text.FONT_SANS_LIGHT;
        if (Fonts.SANS_CONDENSED.equals(which)) return Text.FONT_SANS_CONDENSED;
        if (Fonts.SERIF.equals(which)) return Text.FONT_SERIF;
        if (Fonts.MONOSPACE.equals(which)) return Text.FONT_MONOSPACE;
        if (Fonts.CURSIVE.equals(which)) return Text.FONT_CURSIVE;
        if (Fonts.FILE.equals(which)) return Text.FONT_FILE;
        return Text.FONT_SYSTEM;
    }

    private View fontChoices() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        String now = Fonts.name();
        for (int i = 0; i < Fonts.PRESETS.length; i++) {
            final String which = Fonts.PRESETS[i];
            if (i > 0) rows.addView(line());
            rows.addView(pickRow(fontName(which), which.equals(now),
                    Fonts.SYSTEM.equals(which) ? null : which, () -> {
                        Fonts.choose(which);
                        markChanged();
                        rebuild();
                    }));
        }
        rows.addView(line());
        rows.addView(actionRow("download", Text.FONT_PICK,
                Fonts.FILE.equals(now) ? Text.FONT_FILE : null, this::pickFont));

        rows.addView(line());
        rows.addView(section(Text.EMOJI));
        String emoji = Fonts.emoji();
        List<EmojiPackManager.Pack> packs = EmojiPackManager.packs(this);
        boolean firstPack = true;
        for (EmojiPackManager.Pack pack : packs) {
            final String which = pack.id;
            if (Fonts.EMOJI_FILE.equals(which)) continue;
            if (!firstPack) rows.addView(line());
            firstPack = false;
            rows.addView(pickRow(emojiName(pack), which.equals(emoji), null, () -> {
                Fonts.chooseEmoji(this, which);
                markChanged();
                rebuild();
            }));
        }
        rows.addView(line());
        rows.addView(actionRow("download", Text.EMOJI_FILE,
                Fonts.EMOJI_FILE.equals(emoji) ? Text.EMOJI_FILE : null, this::pickEmoji));
        TextView sample = new TextView(this);
        sample.setText(Text.EMOJI_PREVIEW);
        sample.setTextColor(skin.text);
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        sample.setGravity(Gravity.CENTER);
        sample.setPadding(dp(16), dp(12), dp(16), dp(12));
        android.graphics.Typeface emojiPreview = Fonts.preview(this, emoji);
        if (emojiPreview != null) sample.setTypeface(emojiPreview);
        rows.addView(sample, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        rows.addView(quiet(Text.EMOJI_NOTE));
        return rows;
    }

    private String emojiName(EmojiPackManager.Pack pack) {
        if (Fonts.SYSTEM.equals(pack.id)) return Text.EMOJI_SYSTEM;
        return pack.name;
    }

    private void pickEmoji() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_EMOJI);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * A row that is one of several answers to one question.
     *
     * `preview` is a typeface name to write the row in, so a font can be read
     * before it is chosen; null leaves the row in the app's own.
     */
    private View pickRow(String title, boolean chosen, String preview, final Runnable action) {
        LinearLayout row = row();
        row.setPadding(dp(12), 0, dp(12), 0);

        TextView name = label(title);
        if (preview != null) {
            try {
                name.setTypeface(android.graphics.Typeface.create(
                        preview, android.graphics.Typeface.NORMAL));
            } catch (Throwable ignored) {
            }
        }
        row.addView(name, grow());
        if (chosen) row.addView(new Check(this, Accent.colour()));
        row.setOnClickListener(v -> action.run());
        return sized(row, 52);
    }

    private void pickFont() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FONT);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------- the icon

    private View iconHead() {
        LinearLayout row = row();
        row.addView(icon("image"));
        row.addView(label(Text.ICON), grow());

        TextView now = detail(Launcher.nameOf(Launcher.chosen()));
        now.setTextColor(skin.text);
        row.addView(now);

        row.addView(disclosure(iconOpen,"iconOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "iconOpen";
            iconOpen = !iconOpen;
            rebuildAfterReveal(iconOpen);
        });
        return sized(row, 52);
    }

    private View iconChoices() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        String now = Launcher.chosen();
        String[] all = Launcher.all();
        for (int i = 0; i < all.length; i++) {
            final String which = all[i];
            if (i > 0) rows.addView(line());
            LinearLayout row = row();
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setGravity(Gravity.CENTER_VERTICAL);

            android.widget.ImageView shot = new android.widget.ImageView(this);
            android.graphics.Bitmap picture = Launcher.preview(which);
            if (picture != null) shot.setImageBitmap(picture);
            shot.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(32), dp(32));
            size.rightMargin = dp(14);
            row.addView(shot, size);

            row.addView(label(Launcher.nameOf(which)), grow());
            if (which.equals(now)) row.addView(new Check(this, Accent.colour()));
            row.setOnClickListener(v -> {
                Launcher.choose(this, which);
                rebuild();
            });
            rows.addView(sized(row, 56));
        }
        rows.addView(line());
        return rows;
    }


    /**
     * What a text colour and a background colour may be.
     *
     * Two ramps rather than one palette: what a background wants is a set of
     * near-blacks and near-whites, and what text wants is the other end. The
     * accent's own dots are bright colours and would be no use for either.
     */
    private static final int[] DARKS = {
            0xFF000000, 0xFF0B0B0F, 0xFF121212, 0xFF161823, 0xFF1B1B1B,
            0xFF0D1B2A, 0xFF12232E, 0xFF1A1423, 0xFF14261C, 0xFF241A1A,
    };

    private static final int[] LIGHTS = {
            0xFFFFFFFF, 0xFFF6F6F6, 0xFFEDEDED, 0xFFE8E4DA, 0xFFDCDCDC,
            0xFFCFD8DC, 0xFFB0B8C4, 0xFF8A8A8A, 0xFF5A5A5A, 0xFF2E2E2E,
    };


    private interface ColourPicked { void set(int colour); }

    private View appearanceColourRow(final String role) {
        final int selected = AppearanceColors.ACCENT.equals(role)
                ? Accent.colour() : AppearanceColors.get(role);
        LinearLayout row = row();
        row.setPadding(dp(12), 0, dp(12), 0);
        row.addView(label(AppearanceColors.label(role)), grow());
        row.addView(new Dot(this, selected == 0 ? skin.muted() : selected, false));
        TextView value = detail(selected == 0 ? "TikTok" : String.format(Locale.US,
                "#%06X", selected & 0xFFFFFF));
        value.setTextColor(skin.muted());
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = dp(10);
        row.addView(value, valueParams);
        row.setOnClickListener(v -> askColour(AppearanceColors.label(role),
                selected == 0 ? Accent.TIKTOK : selected, colour -> {
                    AppearanceColors.set(role, colour);
                    markChanged();
                    rebuild();
                }));
        return sized(row, 52);
    }

    private void askColour(String title, int initial, ColourPicked picked) {
        Panel panel = Panel.with(this,skin,title);
        EditText input = panel.field("#RRGGBB");
        input.setText(String.format(Locale.US, "#%06X", initial & 0xFFFFFF));
        panel.primaryKeepOpen(getString(android.R.string.ok), () -> {
                    try {
                        int colour = android.graphics.Color.parseColor(
                                input.getText().toString().trim());
                        picked.set(colour);
                        panel.close();
                    } catch (IllegalArgumentException error) {
                        input.setError(Local.of("Укажите цвет в формате #RRGGBB"));
                    }
                });
        panel.quiet(Text.CANCEL,null); panel.show();
    }

    /** Keep theme presets together in one menu row instead of three buttons. */
    private void chooseThemePreset() {
        final String[] choices = {Text.THEME_PRESET_DARK, Text.THEME_PRESET_BLUE,
                Text.THEME_RESET};
        new ModDialog.Builder(this, skin)
                .setTitle(Text.THEME_PRESETS)
                .setItems(choices, (dialog, index) -> {
                    
                    if (index == 0) Themes.preset(0);
                    else if (index == 1) Themes.preset(1);
                    else Themes.reset();
                    markChanged();
                    rebuild();
                })
                .setNegativeButton(Text.CANCEL, null)
                .show();
    }

    private void chooseAccentColour() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        final AccentColourPicker picker = new AccentColourPicker(this, Accent.colour());
        final View sample = new View(this);
        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(dp(56), dp(28));
        sampleParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(sample, sampleParams);
        content.addView(picker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(238)));
        picker.onColourChanged(colour -> sample.setBackgroundColor(colour));
        sample.setBackgroundColor(Accent.colour());
        android.widget.HorizontalScrollView extras=new android.widget.HorizontalScrollView(this);extras.setHorizontalScrollBarEnabled(false);LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(0,dp(8),0,dp(8));
        final java.util.List<Dot> extraDots=new java.util.ArrayList<>();for(int colour:new int[]{0xFFFFA726,0xFF44D65B,0xFFAA45EE,0xFFF343C9,0xFF405FE8,0xFF35B8E5,0xFFFA4654}){Dot dot=new Dot(this,colour,colour==Accent.colour());extraDots.add(dot);dot.setContentDescription(String.format(Locale.ROOT,"#%06X",colour&0xffffff));Motion.touch(dot);dot.setOnClickListener(v->{picker.setColour(colour);for(Dot other:extraDots)other.choose(other==dot,true);});LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(44),dp(44));size.rightMargin=dp(8);strip.addView(dot,size);}extras.addView(strip);content.addView(extras);
        new ModDialog.Builder(this, skin).setTitle(Text.ACCENT_PICKER)
                .setView(content).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Accent.setCustom(picker.colour());
                    if(accentCustomSwatch!=null)accentCustomSwatch.reveal(picker.colour());
                    markChanged();
                    if(rebuildQueued)column.removeCallbacks(delayedRebuild);rebuildQueued=true;column.postDelayed(delayedRebuild,260L);
                }).show();
    }

    private View palette() {
        android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setPadding(dp(16),dp(12),dp(16),dp(16));
        int[] colours=Accent.palette();final java.util.List<Dot> swatches=new java.util.ArrayList<>();
        for(int colour:colours){Dot dot=new Dot(this,colour,!Accent.isMaterial()&&colour==Accent.colour());swatches.add(dot);Motion.touch(dot);
            dot.setContentDescription(String.format(Locale.US,"#%06X",colour&0xFFFFFF));
            dot.setOnClickListener(v->{Accent.set(colour);for(Dot swatch:swatches)swatch.choose(swatch.fill.getColor()==colour,true);if(accentCustomSwatch!=null)accentCustomSwatch.choose(false,true);markChanged();rebuildAfterSwitchAnimation();});
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(48),dp(48));size.rightMargin=dp(8);strip.addView(dot,size);
        }
        int custom=Accent.custom();
        Dot picker=new Dot(this,custom==0?Accent.colour():custom,custom!=0 && !Accent.isMaterial() && custom==Accent.colour());
        if(custom==0)picker.fill.setShader(new android.graphics.SweepGradient(dp(24),dp(24),new int[]{0xFFE53935,0xFFFFB300,0xFF43A047,0xFF00ACC1,0xFF1E88E5,0xFF8E24AA,0xFFE53935},null));
        accentCustomSwatch=picker;Motion.touch(picker);picker.setContentDescription(Text.ACCENT_PICKER);picker.setOnClickListener(v->chooseAccentColour());
        strip.addView(picker,new LinearLayout.LayoutParams(dp(48),dp(48)));scroll.addView(strip);return scroll;
    }

    /**
     * A Material icon, tinted to whatever this screen turned out to be.
     *
     * Decoded once per name and kept: the settings screen is rebuilt on every
     * tap, and decoding a dozen pngs each time would be felt.
     */
    private View icon(String name) {
        return icon(name, false, 40);
    }

    /** The shapes the first icons of the main list get; the rest are picked by name. */
    private static final java.util.Map<String, Integer> SHAPES = new java.util.HashMap<String, Integer>();
    static {
        SHAPES.put("palette", Expressive.COOKIE9);
        SHAPES.put("group", Expressive.CLOVER4);
        SHAPES.put("play_circle", Expressive.SUNNY);
        SHAPES.put("tune", Expressive.FLOWER6);
        SHAPES.put("info", Expressive.PENTAGON);
        SHAPES.put("extension", Expressive.COOKIE7);
        SHAPES.put("chat", Expressive.BURST12);
    }

    private static int shapeFor(String name) {
        Integer fixed = SHAPES.get(name);
        return fixed != null ? fixed : (name.hashCode() & 0x7FFFFFFF) % Expressive.KINDS;
    }

    /**
     * An icon inside an expressive shape.
     *
     * A toggle's shape is quiet when it is off and fills with the accent, turning
     * by a lobe, when it is on; anything else is a tonal shape that stays put.
     */
    private View icon(String name, boolean toggle, int sizeDp) {
        android.graphics.drawable.Drawable vector = name.equals("search")
                ? new Expressive.Magnifier(0xFFFFFFFF) : SettingsGlyph.make(name, 0xFFFFFFFF);
        if (vector == null) {
            Bitmap picture = pictureFor(name);
            if (picture != null) vector = new android.graphics.drawable.BitmapDrawable(getResources(), picture);
        }
        int accent = Accent.colour();
        int tonal = accentTonal();
        int ink = accentInk();
        Expressive.Badge badge = toggle
                ? new Expressive.Badge(this, shapeFor(name), vector, sizeDp,
                        Expressive.mix(skin.card, skin.text, 0.14f), accent, skin.muted(), onAccent())
                : new Expressive.Badge(this, shapeFor(name), vector, sizeDp, tonal, tonal, ink, ink);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        size.rightMargin = dp(16);
        badge.setLayoutParams(size);
        return badge;
    }

    private Bitmap pictureFor(String name) {
        Bitmap bitmap = ICONS.get(name);
        if (bitmap == null) {
            try {
                String data = Icons.PNG.get(name);
                if (data != null) {
                    byte[] png = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                    bitmap = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
                    if (bitmap != null) ICONS.put(name, bitmap);
                }
            } catch (Throwable ignored) {
            }
        }
        return bitmap;
    }

    private static final java.util.Map<String, Bitmap> ICONS =
            new java.util.HashMap<String, Bitmap>();

    /** What is set by a setting: one line and a switch, wherever it lives. */
    private interface Setting {
        void set(boolean on);
    }

    private View toggleRow(String picture, String title, boolean on, final Setting setting) {
        return toggleRow(picture,title,on,setting,true);
    }

    private View liveToggleRow(String picture, String title, boolean on, final Setting setting) {
        return toggleRow(picture,title,on,setting,false);
    }

    private View toggleRow(String picture, String title, boolean on, final Setting setting,
                           final boolean requiresRestart) {
        LinearLayout row = row();
        final View lead = icon(picture, true, 40);
        if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(on, false);
        row.addView(lead);
        row.addView(label(title), grow());

        settingTargets.put(title,row);
        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setContentDescription(title);
        toggle.setChecked(on);
        toggle.setOnChanged(checked -> {
            if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(checked, true);
            setting.set(checked);
            if (requiresRestart) markChanged();
        });
        row.addView(toggle);

        row.setOnClickListener(v -> {
            toggle.performClick();
        });
        return sized(row, 48);
    }

    /**
     * An identifier, and a tap to copy it.
     *
     * The long one does not fit on a phone, so what is shown is the ends of it
     * and what is copied is all of it.
     */
    /**
     * A switch with a word beside it saying not to trust it yet.
     *
     * The only thing in the mod that acts on its own and the only one that
     * sends anything, so it says so on the row rather than in a note nobody
     * reads.
     */
    private View betaRow(String picture, String title, boolean on, final Setting setting) {
        LinearLayout row = row();
        final View lead = icon(picture, true, 40);
        if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(on, false);
        row.addView(lead);
        row.addView(label(title), grow());

        TextView beta = new TextView(this);
        beta.setText(Text.BETA);
        beta.setTextColor(onAccent());
        beta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        beta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        beta.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(Accent.colour());
        chip.setCornerRadius(dp(12));
        beta.setBackground(chip);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        place.leftMargin = dp(8);
        row.addView(beta, place);

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(on);
        toggle.setOnChanged(checked -> {
            if (lead instanceof Expressive.Badge) ((Expressive.Badge) lead).set(checked, true);
            setting.set(checked);
            markChanged();
        });
        LinearLayout.LayoutParams togglePlace = new LinearLayout.LayoutParams(dp(52), dp(32));
        togglePlace.leftMargin = dp(10);
        row.addView(toggle, togglePlace);
        return sized(row, 52);
    }

    private View stickerHead() {
        LinearLayout row = row();
        row.addView(icon("star"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.STREAK_STICKER));
        int many = Streaks.offered().size();
        text.addView(detail(many == 0 ? Text.STREAK_NOTHING : String.valueOf(many)));
        row.addView(text, grow());

        row.addView(disclosure(streakOpen,"streakOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "streakOpen";
            streakOpen = !streakOpen;
            rebuildAfterReveal(streakOpen);
        });
        return sized(row, 60);
    }

    /** The stickers the app has drawn so far, as something to point at. */
    private View stickerChoices() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(10), dp(16), dp(14));

        FavouriteStickers.load(()->{if(!isFinishing())rebuild();});
        java.util.List<String> ids = Streaks.offered();
        if (ids.isEmpty()) {
            rows.addView(quiet(Text.STREAK_NOTHING));
            return rows;
        }

        String chosen = Streaks.chosen();
        LinearLayout line = null;
        for (int i = 0; i < ids.size() && i < 120; i++) {
            if (i % 5 == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(0, dp(5), 0, dp(5));
                rows.addView(line);
            }
            final String id = ids.get(i);
            View one = stickerTile(id, id.equals(chosen));
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(52), dp(52), 1f);
            line.addView(one, size);
        }
        return rows;
    }

    private View stickerTile(final String id, boolean chosen) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        if (chosen) {
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(0x00000000);
            ring.setStroke(dp(2), Accent.colour());
            ring.setCornerRadius(dp(10));
            view.setBackground(ring);
        }
        final java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<ImageView>(view);
        Bitmap picture = Streaks.thumbnail(this, id,()->{
            ImageView tile=target.get();if(tile!=null && tile.isAttachedToWindow()){
                Bitmap loaded=Streaks.thumbnail(tile.getContext(),id);if(loaded!=null)tile.setImageBitmap(loaded);
            }
        });
        if (picture != null) view.setImageBitmap(picture);
        view.setOnClickListener(v -> {
            Streaks.choose(id);
            rebuild();
        });
        return view;
    }

    /** A row that does something at once, rather than setting anything. */
    private View actionRow(String picture, String title, String detail, final Runnable action) {
        LinearLayout row = row();
        row.addView(icon(picture));
        boolean value = detail != null && detail.length() <= 14;

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));
        if (detail != null && !value) text.addView(detail(detail));
        row.addView(text, grow());

        if (value) {
            TextView shown = detail(detail);
            shown.setGravity(Gravity.CENTER_VERTICAL);
            shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);shown.setPadding(dp(8), 0, dp(6), 0);
            row.addView(shown);
        }
        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(SettingsGlyph.make("chevron_right", skin.muted()));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));

        row.setOnClickListener(v -> action.run());
        return sized(row, detail == null || value ? 48 : 60);
    }

    /**
     * What this is and what it was built from, at the very bottom.
     *
     * Small and grey on purpose: nobody needs it until something has gone
     * wrong, and then it is the first thing anybody will ask for.
     */
    private View versions() {
        TextView view = new TextView(this);
        // the author and the licence live here as well: the licence asks that
        // a fork keep this line and the card that asks for support, rather
        // than moving the name into print nobody reads
        view.setText((Version.LITE ? "TokkiYou Lite " : "TokkiYou ") + Version.MOD
                + (Version.LITE ? " beta  ·  TikTok Lite " : "  ·  TikTok ") + Version.TIKTOK
                + "\n" + Text.MADE_BY);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(24), dp(20), dp(8));
        return view;
    }

    private View idRow(String title, final String value) {
        LinearLayout row = row();
        row.addView(icon("fingerprint"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));

        TextView shown = detail(value == null ? Text.ACCOUNT_UNKNOWN : shorten(value));
        if (value != null) shown.setTextColor(skin.text);
        text.addView(shown);
        row.addView(text, grow());

        if (value != null) {
            row.addView(away());
            row.setOnClickListener(v -> copy(title, value));
        }
        return sized(row, 60);
    }

    private static String shorten(String value) {
        if (value.length() <= 26) return value;
        return value.substring(0, 14) + "…" + value.substring(value.length() - 8);
    }

    // ----------------------------------------------------------- the plugins

    private void showPluginAdd(){
        Panel panel=Panel.with(this,skin,Local.of("Добавить плагин"));
        panel.primary(Local.of("Создать из кнопок"),()->ScriptPlugins.edit(this,ScriptPlugins.SAMPLE,this::rebuild));
        panel.quiet(Local.of("Вставить JSON"),()->ScriptPlugins.edit(this,"",this::rebuild));
        panel.quiet(Local.of("Установить файл .mtp"),this::pickPlugin);
        panel.quiet(Local.of("Магазин"),()->startActivity(new Intent(this,StoreActivity.class)));
        panel.quiet(Local.of("Документация"),()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(DOCS_PLUGINS))));
        panel.quiet(Text.CLOSE,null);panel.show();
    }

    private static final int PICK_PLUGIN = 0x4D50;  // "MP"

    /**
     * What plugins put into the settings: one block per switched-on plugin,
     * under its own name, in the same rows the mod's own settings use.
     */


    private View pluginSettingRow(final Plugins.Row item,
                                  final android.content.SharedPreferences store) {
        switch (item.kind) {
            case Plugins.Row.SECTION:
                return caption(item.title);
            case Plugins.Row.TOGGLE:
                return toggleRow("extension", item.title,
                        store.getBoolean(item.key, item.def != 0),
                        on -> {store.edit().putBoolean(item.key, on).apply();Plugins.settingsChanged(item.plugin,item.key);});
            case Plugins.Row.CHOICE: {
                final String[] options = item.options;
                if (options == null || options.length == 0) return null;
                int now = Math.max(0, Math.min(options.length - 1,
                        store.getInt(item.key, item.def)));
                return actionRow("tune", item.title, options[now], () ->
                        new ModDialog.Builder(this, skin)
                                .setTitle(item.title)
                                .setSingleChoiceItems(options,
                                        Math.max(0, Math.min(options.length - 1,
                                                store.getInt(item.key, item.def))),
                                        (dialog, which) -> {
                                            store.edit().putInt(item.key, which).apply();
                                            Plugins.settingsChanged(item.plugin,item.key);
                                            markChanged();
                                            dialog.dismiss();
                                            rebuild();
                                        })
                                .show());
            }
            case Plugins.Row.SLIDER: {
                int now = Math.max(item.min, Math.min(item.max,
                        store.getInt(item.key, item.def)));
                return slider(item.title, now - item.min, item.max - item.min, item.min, "",
                        value -> {
                            store.edit().putInt(item.key, value + item.min).apply();
                            Plugins.settingsChanged(item.plugin,item.key);
                            markChanged();
                        });
            }
            default:
                return actionRow("extension", item.title, item.detail, () -> {
                    try {
                        if (item.action != null) item.action.tapped();
                    } catch (Throwable error) {
                        Diary.note("plugin " + item.plugin + ": " + error);
                    }
                });
        }
    }

    private View installRow() {
        LinearLayout row = row();
        row.addView(icon("extension"));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.PLUGIN_INSTALL));
        text.addView(detail(Text.PLUGIN_INSTALL_NOTE));
        row.addView(text, grow());

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(Accent.colour());
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        plus.setPadding(dp(12), 0, 0, 0);
        row.addView(plus);

        row.setOnClickListener(v -> pickPlugin());
        return sized(row, 60);
    }

    /**
     * One installed plugin: what it says about itself, and a switch.
     *
     * Not `sized()` like the other rows -- a description is as tall as it is,
     * and a plugin whose author wrote two sentences should not have the second
     * one clipped.
     */
    private View pluginRow(final Plugins.Info info) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        Bitmap icon = info.icon();
        if (icon != null) {
            ImageView view = new ImageView(this);
            view.setImageBitmap(icon);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(view, size);
        } else {
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(new Dot(this, Accent.colour(), false), size);
        }

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(info.name + "  " + info.version));

        String by = info.author;
        if (!info.description.isEmpty()) by = by + "  ·  " + info.description;
        text.addView(detail(by));
        if (info.trouble != null) text.addView(detail(info.trouble));
        long stalled = Lag.cost(info.id);
        if (stalled >= 1000L) {
            TextView slow = detail(Local.of("Тормозит приложение: подвисаний за этот запуск") + " " + (stalled + 500L) / 1000L + " " + Local.of("с"));
            slow.setTextColor(0xFFE57373);
            text.addView(slow);
        }
        if(info.safety!=null&&!info.safety.isEmpty())text.addView(detail(Local.of("Удерживайте для действий")));
        row.addView(text, grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(Plugins.isEnabled(info.id));
        // a plugin that failed to start can still be switched off, and tried again
        toggle.setEnabled(true);
        toggle.setOnChanged(checked -> {
            activatePlugin(info,checked);
        });
        row.addView(toggle);

        row.setOnClickListener(v -> openPluginSettings(info));
        Motion.press(row);
        row.setOnLongClickListener(v -> {
            Panel actions=Panel.with(this,skin,info.name).compact();
            actions.primary(Local.of("Редактировать"),()->PluginEditorActivity.binary(this,info.id));
            actions.quiet(Local.of("Удалить с устройства"),()->askToRemove(info));
            if(AdminAccess.current())actions.quiet(Local.of("Управление магазином"),()->PluginAdmin.show(this));
            actions.quiet(Text.CLOSE,null);actions.show();
            return true;
        });
        return row;
    }

    private void activatePlugin(final Plugins.Info info,boolean enabled){
        // A plugin's hooks are in TikTok from the moment it starts, and stay there after it is told to
        // stop: on or off, it only takes hold with a restart. The plugins said so themselves, in a line
        // of text; this offers the same button every other such setting does.
        if(!enabled){Net.away("plugin stop",()->{Plugins.setEnabled(info.id,false);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});});return;}
        // on is on: what the plugin reaches for was shown when it was installed, and is one tap away
        Net.away("plugin start",()->{Plugins.setEnabled(info.id,true);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});});
    }

    private void openPluginSettings(final Plugins.Info info) {
        if(Plugins.isEnabled(info.id) && Plugins.openSettings(info.id,this))return;
        Panel panel=Panel.with(this,skin,info.name);
        if(info.safety!=null&&!info.safety.isEmpty())panel.text(info.safety);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        android.content.SharedPreferences store=getSharedPreferences("tokkiyou_plugin_"+info.id,MODE_PRIVATE);
        int count=0;
        if(Plugins.isEnabled(info.id)) for(Plugins.Row item:Plugins.rows()) {
            if(!info.id.equals(item.plugin))continue;
            try { View control=pluginSettingRow(item,store); if(control!=null){body.addView(control);count++;} }
            catch(Throwable error){Diary.note("plugin settings "+info.id+": "+error);}
        }
        if(count==0){
            if(!Plugins.isEnabled(info.id))panel.text(Local.of("Включите плагин, чтобы открыть его настройки."));
            else if(Plugins.isRunning(info.id))panel.text(Local.of("Плагин не предоставил настройки. Версия ")+info.version);
            else{
                String why=Plugins.problem(info.id);
                panel.text(why!=null?Local.of("Плагин включён, но не запущен: ")+why:Local.of("Плагин включён, но ещё не запущен в этом сеансе."));
                panel.primary(Local.of("Запустить заново"),()->Net.away("plugin revive",()->{
                    final boolean ok=Plugins.revive(info.id);
                    runOnUiThread(()->{if(isFinishing())return;rebuild();if(ok)openPluginSettings(info);});
                }));
            }
        }else panel.view(body);
        panel.quiet(Local.of("Назад"),null).show();
    }

    private void pickPlugin() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // .mtp is nobody's registered type, so the picker is shown everything
            intent.setType("*/*");
            startActivityForResult(intent, PICK_PLUGIN);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        Uri source = data.getData();
        if (source == null) return;

        if (request == CREATE_BACKUP) {
            Screen.progress(Text.BACKUP_WRITING, 0);
            Net.away("backup", () -> {
                boolean done = Backup.write(this, source);
                if (done) Screen.progressDone(); else Screen.progressGone();
                runOnUiThread(() -> Screen.say(done ? Text.BACKUP_DONE : Text.BACKUP_FAILED));
            });
            return;
        }
        if (request == PICK_BACKUP) {
            reviewBackup(source);
            return;
        }

        if (request == PICK_FONT || request == PICK_EMOJI) {
            if (Fonts.take(this, source, request == PICK_EMOJI)) markChanged();
            else Toast.makeText(this, Text.FONT_FAILED, Toast.LENGTH_LONG).show();
            rebuild();
            return;
        }
        if (request != PICK_PLUGIN) return;
        reviewPlugin(source);
    }
    private void reviewPlugin(final Uri source){
        Net.away("plugin inspection",()->{
            try {final PluginAudit.Report report=Plugins.inspect(this,source);
                runOnUiThread(()->{
                    if(isFinishing())return;
                    Panel panel=Panel.with(this,skin,Text.PLUGIN_REVIEW).text(report.summary());
                    panel.primary(Text.PLUGIN_INSTALL,()->Net.away("plugin install",()->{
                        try {Plugins.install(this,source,report.digest);runOnUiThread(()->{if(!isFinishing()){markChanged();rebuild();}});Screen.say(Text.PLUGIN_INSTALLED);}
                        catch(Throwable error){Screen.say(String.valueOf(error.getMessage()));}
                    }));
                    panel.quiet(Local.of("Назад"),null).show();
                });
            }catch(Throwable error){runOnUiThread(()->{if(!isFinishing())Panel.with(this,skin,Local.of("Плагин не принят")).text(String.valueOf(error.getMessage())).quiet(Local.of("Назад"),null).show();});}
        });
    }

    private void askToRemove(final Plugins.Info info) {
        try {
            new ModDialog.Builder(this, skin)
                    .setTitle(Text.PLUGIN_REMOVE_ASK)
                    .setMessage(info.name + "  " + info.version)
                    .setNegativeButton(Text.CANCEL, null)
                    .setPositiveButton(Text.PLUGIN_REMOVE, (dialog, which) -> {
                        Plugins.uninstall(this, info.id);
                        markChanged();
                        rebuild();
                    })
                    .show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------- the links

    private View linkRow(String picture, String title, String handle, final String url) {
        LinearLayout row = row();
        row.addView(icon(picture));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));
        text.addView(detail(handle));
        row.addView(text, grow());
        View arrow = away();
        if (url == null || url.length() == 0) {
            arrow.setVisibility(View.GONE);
        } else {
            row.setOnClickListener(v -> open(url));
        }
        row.addView(arrow);
        return sized(row, 60);
    }

    /** The channel card: the whole card is a button to the mod's Telegram channel. */
    private View channelBanner() {
        ChannelBanner banner = new ChannelBanner(this);
        banner.setOnClickListener(v -> open(ChannelBanner.URL));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(12), dp(2), dp(12), dp(10));
        box.addView(banner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private View thanksHead() {
        LinearLayout row = row();
        row.addView(icon("favorite_border"));
        row.addView(label(Text.THANKS), grow());

        row.addView(disclosure(thanksOpen,"thanksOpen"));

        row.setOnClickListener(v -> {
            disclosureKey = "thanksOpen";
            thanksOpen = !thanksOpen;
            rebuildAfterReveal(thanksOpen);
        });
        return sized(row, 52);
    }

    /** Who made this, and where support links will appear when configured. */
    private View thanks() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(6), 0, dp(10));

        rows.addView(quiet(Text.THANKS_NOTE));
        rows.addView(owner());
        rows.addView(line());

        TextView heading = new TextView(this);
        heading.setText(Text.DONATE);
        heading.setTextColor(skin.text);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(16), dp(14), dp(16), dp(2));
        rows.addView(heading);

        rows.addView(quiet(Text.DONATE_NOTE));

        rows.addView(linkRow("star", Text.YOOMONEY, Text.YOOMONEY_NOTE, YOOMONEY));
        return rows;
    }

    /** The one row with two places to go, so it asks which. */
    private View owner() {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label("@mrretizixd"));
        text.addView(detail(Text.THANKS_OWNER));
        row.addView(text, grow());
        row.addView(away());

        row.setOnClickListener(v -> {
            try {
                new ModDialog.Builder(this, skin)
                        .setTitle("@mrretizixd")
                        .setItems(new CharSequence[]{"Telegram", "TikTok"}, (dialog, which) ->
                                open(which == 0 ? OWNER_TELEGRAM : OWNER_TIKTOK))
                        .setNegativeButton(Text.CANCEL, null)
                        .show();
            } catch (Throwable error) {
                open(OWNER_TELEGRAM);
            }
        });
        return sized(row, 60);
    }

    private View person(String name, String what, final String url) {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(name));
        text.addView(detail(what));
        row.addView(text, grow());

        if (url != null) {
            row.addView(away());
            row.setOnClickListener(v -> open(url));
        }
        return sized(row, 60);
    }

    /** The mark on a row that leaves the app. */
    private View away() {
        Chevron arrow=new Chevron(this,skin.muted(),false,false);
        arrow.setRotation(-90f);
        return arrow;
    }

    /** A paragraph that is there to be read once and then ignored. */
    private TextView quiet(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setPadding(dp(16), dp(2), dp(16), dp(8));
        return view;
    }

    /** A card number is read off the screen by a person, so it is grouped. */


    private void open(String url) {
        // a tiktok.com link belongs to the app this is running inside, so it is
        // offered there first: without this the browser opens, recognises the
        // link and hands it straight back, which is two screens for nothing
        url = inApp(url);
        if (url.contains("tiktok.com") && openWith(url, getPackageName())) return;
        if (openWith(url, null)) return;
        Toast.makeText(this, Text.NO_BROWSER, Toast.LENGTH_SHORT).show();
    }

    /**
     * The spelling of a link the app answers to.
     *
     * TikTok claims `www.tiktok.com` and a dozen others in its manifest, and
     * does not claim the bare domain -- so a link written without the `www`
     * resolves to nothing in the app, falls through to the browser, and the
     * browser hands it straight back. One prefix is the whole difference.
     */
    private static String inApp(String url) {
        if (url.startsWith("https://tiktok.com/")) {
            return "https://www.tiktok.com/" + url.substring("https://tiktok.com/".length());
        }
        return url;
    }

    private boolean openWith(String url, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (packageName != null) intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void copy(String what, String text) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(what, text));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    /** Every shape as a picture of itself, four to a row. */
    private void chooseAvatarShape() {
        final Panel panel = Panel.with(this, skin, Text.AVATAR_SHAPE);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        final String now = AvatarShape.chosen();
        LinearLayout line = null;
        for (int i = 0; i < AvatarShape.ALL.length; i++) {
            final String shape = AvatarShape.ALL[i];
            if (i % 4 == 0) {
                line = new LinearLayout(this);
                grid.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout tile = Panel.tile(this, skin, AvatarShape.picture(shape, dp(36), 0xFFFFFFFF),
                    shape.equals(now) ? Accent.colour() : skin.text, AvatarShape.nameOf(shape),
                    shape.equals(now));
            tile.setOnClickListener(v -> {
                panel.close();
                if (shape.equals(now)) return;
                AvatarShape.choose(this, shape);
                // a picture already cut keeps its shape until it is cut again
                markChanged();
                rebuild();
            });
            Motion.press(tile);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            place.setMargins(dp(3), dp(3), dp(3), dp(3));
            line.addView(tile, place);
        }
        // the last row is not full: keep its tiles the width of the others
        for (int i = AvatarShape.ALL.length; i % 4 != 0; i++) {
            line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            ((LinearLayout.LayoutParams) line.getChildAt(line.getChildCount() - 1).getLayoutParams())
                    .setMargins(dp(3), dp(3), dp(3), dp(3));
        }
        panel.view(grid);
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    // ------------------------------------------------------ TikTok's flags

    private static final int FLAGS_PAGE = 98;
    /** How many rows a search shows. There are twenty thousand flags and a row is a view. */
    private static final int FLAG_ROWS = 60;

    private String flagQuery = "";
    private java.util.List<Flags.Known> flagList;
    private boolean flagsLoading;

    private void showFlags() {
        settingsGroup = FLAGS_PAGE;
        searchQuery = "";
        flagQuery = "";
        page.scrollTo(0, 0);
        rebuild();
    }

    /**
     * Every flag TikTok declares, to look up and to answer differently.
     *
     * Nothing is listed until something is typed, bar what has already been
     * changed: the list is twenty thousand names long, and the one somebody
     * came for is found by a piece of its name or not at all.
     */
    private void flagsPage() {
        LinearLayout warning = card();
        warning.addView(quiet(Text.FLAGS_WARNING));
        column.addView(wrap(warning));

        final java.util.Map<String, Object> mine = Flags.overrides();
        if (FlagGuard.heldBack() && !mine.isEmpty()) {
            LinearLayout held = card();
            held.addView(quiet(Text.FLAGS_HELD));
            held.addView(actionRow("restore", Text.FLAGS_AGAIN, null, () -> {
                FlagGuard.again();
                Flags.forget();
                restartOwed();
                rebuild();
            }));
            column.addView(wrap(held));
        }

        if (flagList == null) {
            LinearLayout wait = card();
            wait.addView(quiet(Text.FLAGS_LOADING));
            column.addView(wrap(wait));
            if (flagsLoading) return;
            flagsLoading = true;
            // the list is built by a class initialiser the size of a small
            // book, and that is not something to wait for with a finger down
            new Thread(() -> {
                final java.util.List<Flags.Known> all = Flags.catalogue();
                runOnUiThread(() -> {
                    flagList = all;
                    flagsLoading = false;
                    if (settingsGroup == FLAGS_PAGE && !isFinishing()) rebuild();
                });
            }, "tokkiyou-flags").start();
            return;
        }

        column.addView(flagSearch());

        java.util.List<Flags.Known> shown = new java.util.ArrayList<Flags.Known>();
        int found = 0;
        for (Flags.Known flag : flagList) {
            boolean wanted = flagQuery.length() == 0 ? mine.containsKey(flag.key)
                    : flag.key.toLowerCase(Locale.ROOT).contains(flagQuery);
            if (!wanted) continue;
            found++;
            if (shown.size() < FLAG_ROWS) shown.add(flag);
        }

        column.addView(section(flagQuery.length() == 0 ? Text.FLAGS_CHANGED
                : String.format(Locale.ROOT, Text.FLAGS_FOUND, found)));
        LinearLayout list = card();
        if (flagList.isEmpty()) list.addView(quiet(Text.FLAGS_NO_LIST));
        else if (shown.isEmpty()) list.addView(quiet(flagQuery.length() == 0
                ? String.format(Locale.ROOT, Text.FLAGS_NONE_CHANGED, flagList.size())
                : Text.NOTHING_FOUND));
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) list.addView(line());
            list.addView(flagRow(shown.get(i), mine.get(shown.get(i).key)));
        }
        if (found > shown.size()) {
            list.addView(quiet(String.format(Locale.ROOT, Text.FLAGS_MORE, shown.size())));
        }
        column.addView(wrap(list));

        if (!mine.isEmpty()) {
            column.addView(wrap(actionRow("restore", Text.FLAGS_RESET_ALL, null, () -> {
                Flags.clearOverrides();
                restartOwed();
                rebuild();
            })));
        }
    }

    private View flagSearch() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(dp(28));
        row.setBackground(background);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(flagQuery);
        input.setHint(Text.FLAGS_SEARCH);
        input.setHintTextColor(skin.muted());
        input.setTextColor(skin.text);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        final Runnable look = () -> {
            flagQuery = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            page.scrollTo(0, 0);
            rebuild();
        };
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            look.run();
            return true;
        });
        row.addView(input, grow());

        TextView go = label(flagQuery.length() == 0 ? Text.SEARCH : Text.SEARCH_CLEAR);
        go.setTextColor(accentInk());
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), 0, dp(16), 0);
        go.setBackground(Expressive.tile(this, accentTonal(), (Accent.colour() & 0xFFFFFF) | 0x33000000,
                dp(20), dp(20), dp(10)));
        go.setOnClickListener(v -> {
            if (flagQuery.length() > 0) input.setText("");
            look.run();
        });
        row.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(12), dp(4), dp(12), dp(8));
        box.addView(sized(row, 56));
        return box;
    }

    /** A flag: its name, its type, what TikTok says and, if it differs, what is said here. */
    private View flagRow(final Flags.Known flag, final Object mine) {
        LinearLayout row = row();
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(flag.key);
        name.setTypeface(Typeface.MONOSPACE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        words.addView(name);

        final Object own = flag.settable() ? Flags.own(flag) : null;
        final Object theirs = own != null ? own : flag.fallback;
        String says = flag.type.toLowerCase(Locale.ROOT) + "  ·  TikTok: " + flagValue(theirs);
        if (mine != null) says += "  ·  " + Text.FLAGS_HERE + ": " + flagValue(mine);
        TextView about = detail(says);
        about.setPadding(0, dp(4), 0, 0);
        if (mine != null) about.setTextColor(accentInk());
        words.addView(about);
        row.addView(words, grow());
        row.setOnClickListener(v -> editFlag(flag, theirs, mine));
        return sized(row, 56);
    }

    private static String flagValue(Object value) {
        if (value == null) return "—";
        String text = value instanceof Object[] ? java.util.Arrays.toString((Object[]) value)
                : String.valueOf(value);
        if (value instanceof String) text = '"' + text + '"';
        return text.length() > 48 ? text.substring(0, 48) + "…" : text;
    }

    private void editFlag(final Flags.Known flag, Object theirs, Object mine) {
        if (!flag.settable()) {
            Toast.makeText(this, Text.FLAGS_FIXED, Toast.LENGTH_SHORT).show();
            return;
        }
        final Panel panel = Panel.with(this, skin, flag.key);
        if (flag.type.equals("BOOLEAN")) {
            panel.option(Text.FLAGS_AS_TIKTOK + " (" + flagValue(theirs) + ")", mine == null, false,
                    () -> answerFlag(flag, null));
            panel.option(Text.FLAGS_ON, Boolean.TRUE.equals(mine), false,
                    () -> answerFlag(flag, Boolean.TRUE));
            panel.option(Text.FLAGS_OFF, Boolean.FALSE.equals(mine), false,
                    () -> answerFlag(flag, Boolean.FALSE));
            panel.quiet(Text.CLOSE, null);
            panel.show();
            return;
        }
        panel.text(flag.type.toLowerCase(Locale.ROOT) + "  ·  TikTok: " + flagValue(theirs));
        final EditText input = panel.field(String.valueOf(theirs));
        Object now = mine != null ? mine : theirs;
        if (now != null) input.setText(String.valueOf(now));
        if (!flag.type.equals("STRING")) {
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    | (flag.type.equals("FLOAT") || flag.type.equals("DOUBLE")
                            ? android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        }
        panel.primaryKeepOpen(Text.APPLY, () -> {
            Object value = Flags.parse(flag, input.getText().toString());
            if (value == null) {
                Screen.say(Text.FLAGS_BAD);
                return;
            }
            panel.close();
            answerFlag(flag, value);
        });
        if (mine != null) panel.quiet(Text.FLAGS_RESET, () -> answerFlag(flag, null));
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    private void answerFlag(Flags.Known flag, Object value) {
        Flags.override(flag, value);
        Diary.note("flag " + flag.key + " = " + (value == null ? "TikTok's own" : value));
        // nearly every flag is read once and remembered, so it takes a restart
        restartOwed();
        rebuild();
    }

    /** The whole report, monospaced, so columns line up and it reads like a log. */
    private View fullReportView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(12));
        TextView view = new TextView(this);
        view.setText(Diagnostics.fullReport());
        view.setTextColor(skin.muted());
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        view.setTextIsSelectable(true);
        box.addView(view);
        return box;
    }

    private View diaryHead() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        // The title gets the whole row; the two actions sit on a line of their own,
        // otherwise the long words squeeze the title down to one letter per line.
        LinearLayout row = row();
        row.addView(icon("article"));
        TextView title = label(Text.DIARY_TITLE);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, grow());
        row.addView(disclosure(diaryOpen,"diaryOpen"));
        row.setOnClickListener(v -> {
            disclosureKey = "diaryOpen";
            diaryOpen = !diaryOpen;
            rebuildAfterReveal(diaryOpen);
        });
        box.addView(sized(row, 52));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(16), 0, dp(16), dp(8));

        TextView copy = diaryAction(Text.COPY);
        copy.setOnClickListener(v -> copyDiary());
        actions.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = diaryAction(Text.CLEAR);
        clear.setOnClickListener(v -> {
            Diary.clear();
            Toast.makeText(this, Text.CLEARED, Toast.LENGTH_SHORT).show();
            rebuild();
        });
        LinearLayout.LayoutParams clearWhere = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearWhere.leftMargin = dp(8);
        actions.addView(clear, clearWhere);
        box.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private TextView diaryAction(String text) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextColor(Accent.colour());
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.setGravity(Gravity.CENTER);
        action.setSingleLine(true);
        action.setEllipsize(android.text.TextUtils.TruncateAt.END);
        action.setPadding(dp(12), dp(10), dp(12), dp(10));
        action.setMinHeight(dp(44));
        action.setClickable(true);
        action.setFocusable(true);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(22));
        pill.setColor((Accent.colour() & 0xFFFFFF) | 0x1F000000);
        action.setBackground(pill);
        Motion.press(action);
        return action;
    }

    private View diaryLines() {
        if (DevMode.isOn()) return fullReportView();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(12));
        for (String entry : Diary.lines()) {
            TextView view = new TextView(this);
            view.setText(entry);
            view.setTextColor(skin.muted());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            view.setPadding(0, dp(2), 0, 0);
            box.addView(view);
        }
        return box;
    }

    /**
     * Start TikTok over, so everything is drawn again in the new colour.
     *
     * The launcher's own intent, then out: what comes back is a fresh process
     * with nothing of the old one's colours cached in it.
     */
    private void restartTikTok() {
        try {
            android.content.Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(getPackageName());
            if (intent == null) return;
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            Runtime.getRuntime().exit(0);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void copyDiary() {
        try {
            StringBuilder out = new StringBuilder("TokkiYou\n");
            if (DevMode.isOn()) out.append(Diagnostics.fullReport());
            else for (String line : Diary.lines()) out.append(line).append('\n');
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("TokkiYou", out.toString()));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------- the parts

    private View backArrow() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(16), dp(4));
        // a round tonal button that turns into a rounded square under the finger
        View button = Expressive.roundButton(this, SettingsGlyph.make("arrow_forward", skin.text),
                skin.card, (Accent.colour() & 0xFFFFFF) | 0x33000000, skin.text, 48);
        ((ViewGroup) button).getChildAt(0).setScaleX(-1f);
        bar.addView(button);
        TextView name = new TextView(this);
        name.setText(settingsGroup == 0 ? "TikTok" : Local.of("Настройки"));
        name.setTextColor(skin.muted());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(dp(12), 0, 0, 0);
        bar.addView(name);
        bar.setContentDescription(settingsGroup == 0 ? "TikTok" : Local.of("Настройки"));
        bar.setOnClickListener(v -> goBack());
        return bar;
    }

    private View title(String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, settingsGroup == 0 ? 44 : 34);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(28), dp(8), dp(24), dp(16));
        view.setOnClickListener(v -> Cats.tapped(view));
        return view;
    }

    /**
     * Search is an explicit action rather than a TextWatcher rebuilding the
     * entire settings tree for every keystroke. The keyboard stays stable,
     * and the results are real shortcuts into matching setting groups.
     */
    private View searchBox() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(dp(28));
        row.setBackground(background);

        ImageView glass = new ImageView(this);
        glass.setImageDrawable(new Expressive.Magnifier(skin.muted()));
        LinearLayout.LayoutParams glassSize = new LinearLayout.LayoutParams(dp(24), dp(24));
        glassSize.rightMargin = dp(12);
        row.addView(glass, glassSize);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(searchQuery);
        input.setSelectAllOnFocus(false);
        input.setHint(Text.SEARCH_HINT);
        input.setHintTextColor(skin.muted());
        input.setTextColor(skin.text);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(input.getText().toString());
                return true;
            }
            return false;
        });
        row.addView(input, grow());

        TextView go = label(searchQuery.length() == 0 ? Text.SEARCH : Text.SEARCH_CLEAR);
        go.setTextColor(accentInk());
        go.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), 0, dp(16), 0);
        go.setBackground(Expressive.tile(this, accentTonal(), (Accent.colour() & 0xFFFFFF) | 0x33000000,
                dp(20), dp(20), dp(10)));
        go.setOnClickListener(v -> {
            if (searchQuery.length() > 0) runSearch("");
            else runSearch(input.getText().toString());
        });
        row.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(12), dp(4), dp(12), dp(8));
        box.addView(sized(row, 56));
        return box;
    }

    private void runSearch(String words) {
        searchQuery = words == null ? "" : words.trim().toLowerCase(Locale.ROOT);
        rebuild();
    }


    private void chooseSeekStep(){
        new ModDialog.Builder(this).setItems(new String[]{"5","10","15","30","60",Text.CUSTOM_SPEED},(dialog,index)->{
            if(index<5){AccountAppearance.prefs(this).edit().putInt(PlayerTools.STEP,new int[]{5,10,15,30,60}[index]).apply();rebuild();return;}
            Panel panel=Panel.with(this,skin,Text.SEEK_STEP);android.widget.EditText input=panel.field("1–60");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(Integer.toString(PlayerTools.step()));
            panel.primaryKeepOpen(Text.APPLY,()->{try{int value=Integer.parseInt(input.getText().toString());if(value<1 || value>60){Screen.say("1–60");return;}AccountAppearance.prefs(this).edit().putInt(PlayerTools.STEP,value).apply();panel.close();rebuild();}catch(Throwable ignored){Screen.say("1–60");}});panel.quiet(Text.CLOSE,null);panel.show();
        }).show();
    }
    private void showHiddenItems() {
        Panel panel=Panel.with(this,skin,Text.HIDDEN_ITEMS);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        String[] keys={Feed.KEY,Feed.KEY_LIVE,Feed.KEY_PHOTOS,Feed.KEY_STORIES,Feed.KEY_SUGGESTIONS};
        String[] labels={Text.HIDE_ADS,Text.HIDE_LIVE,Text.HIDE_PHOTOS,Text.HIDE_STORIES,Text.HIDE_SUGGESTIONS};
        int count=0;
        for(int i=0;i<keys.length;i++){
            final String key=keys[i];
            if(!(key.equals(Feed.KEY)?Feed.isEnabled():Feed.hides(key)))continue;
            count++;
            rows.addView(actionRow("visibility_off",labels[i],Text.HIDDEN_RESTORE,()->{
                if(key.equals(Feed.KEY))Feed.setEnabled(false);else Feed.setHides(key,false);
                panel.close();rebuild();Screen.say(Text.HIDDEN_NEXT_PAGE);
            }));
        }
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        for(int i=0;i<4;i++)if(!FeedRail.shown(i)){
            final int kind=i;count++;
            rows.addView(actionRow("visibility_off",railNames[i],Text.HIDDEN_RESTORE,()->{
                FeedRail.setShown(kind,true);panel.close();rebuild();
            }));
        }
        for(java.util.Map.Entry<Integer,String> card:FeedBlacklist.cards().entrySet()){
            final int type=card.getKey();count++;
            rows.addView(actionRow("group",card.getValue(),Text.HIDDEN_RESTORE,()->{FeedBlacklist.removeCard(type);panel.close();rebuild();}));
        }
        for(java.util.Map.Entry<String,String> author:FeedBlacklist.items().entrySet()){
            final String uid=author.getKey();count++;
            rows.addView(actionRow("group",author.getValue(),Text.HIDDEN_RESTORE,()->{FeedBlacklist.remove(uid);panel.close();rebuild();}));
        }
        if(count==0)rows.addView(caption(Text.HIDDEN_EMPTY));
        else {
            rows.addView(caption(Text.HIDDEN_NEXT_PAGE));
            panel.primary(Text.HIDDEN_RESTORE_ALL,()->{
                Feed.setEnabled(false);Feed.setHides(Feed.KEY_LIVE,false);Feed.setHides(Feed.KEY_PHOTOS,false);
                Feed.setHides(Feed.KEY_STORIES,false);Feed.setHides(Feed.KEY_SUGGESTIONS,false);FeedBlacklist.clear();
                for(int i=0;i<4;i++)FeedRail.setShown(i,true);
                rebuild();
            });
        }
        panel.view(rows);panel.quiet(Text.CLOSE,null);panel.show();
    }

    private void searchResults() {
        column.addView(section(Text.SEARCH_RESULTS));
        LinearLayout results = card();
        int found = 0;
        found+=searchControl(results,Text.FEED,Text.HIDE_ADS,Feed.isEnabled());
        found+=searchControl(results,Text.FEED,Text.HIDE_LIVE,Feed.hides(Feed.KEY_LIVE));
        found+=searchControl(results,Text.FEED,Text.HIDE_PHOTOS,Feed.hides(Feed.KEY_PHOTOS));
        found+=searchControl(results,Text.FEED,Text.HIDE_STORIES,Feed.hides(Feed.KEY_STORIES));
        found+=searchControl(results,Text.FEED,Text.HIDE_SUGGESTIONS,Feed.hides(Feed.KEY_SUGGESTIONS));
        found+=searchHit(results,Text.PINNED_FRIENDS,Text.PINNED_FRIENDS+" "+Text.MESSAGE_TIMES,()->{});
        found+=searchHit(results,Text.NOTIFICATIONS,Text.NOTIFY_MESSAGES+" "+Text.NOTIFY_PROMOS+" "+Text.NOTIFY_OTHER+" "+Text.NOTIFY_NO_PROMPTS+" "+Text.NOTIFY_SETTINGS,()->{});
        found+=searchHit(results,Text.VIDEO,Text.PLAYER_SPEED,()->{});
        found+=searchHit(results,Text.DOWNLOADS,Text.DL_QUALITY+" "+Text.FRAME_QUALITY,()->{});
        String[] railNames={Text.RAIL_LIKE,Text.RAIL_COMMENT,Text.RAIL_FAV,Text.RAIL_SHARE};
        for(int i=0;i<4;i++)found+=searchControl(results,Text.RAIL,railNames[i],FeedRail.shown(i));
        if(matchesSearch(Text.HIDDEN_ITEMS)){
            results.addView(actionRow("visibility_off",Text.HIDDEN_ITEMS,Text.HIDDEN_ITEMS_NOTE,this::showHiddenItems));found++;
        }
        found+=searchControl(results,Text.DOWNLOADS,Text.NO_WATERMARK,Download.isEnabled());
        found+=searchControl(results,Text.VIDEO,Text.ALWAYS_DATE,Dates.isEnabled());
        found+=searchControl(results,BURN,Text.DIM,Dim.isConfigured());
        found+=searchControl(results,Text.COMMENTS,Text.COMMENT_COPY_ON,Comments.isCopyEnabled());
        found+=searchControl(results,Text.VIDEO,Text.OWN_BRIGHTNESS,Brightness.isEnabled());
        found+=searchControl(results,Text.TIKTOK_FEATURES,Text.COMMENT_REPOST,Flags.isOn(Flags.KEY_COMMENT_REPOST));
        found+=searchControl(results,Text.TIKTOK_FEATURES,Text.SELF_REPOST,Flags.isOn(Flags.KEY_SELF_REPOST));
        found+=searchControl(results,Text.TIKTOK_FEATURES,Text.TRANSLATE_COMMENTS,Flags.isOn(Flags.KEY_TRANSLATE_COMMENTS));
        found+=searchControl(results,Text.APPEARANCE,Text.NEW_PROFILE,Flags.isOn(Flags.KEY_NEW_PROFILE));
        found+=searchControl(results,Text.APPEARANCE,Text.NAV_CAPSULE,NavBar.isEnabled());
        found+=searchControl(results,Text.APPEARANCE,Text.MD3_FEED,Md3.isOn(Md3.KEY_FEED));
        found+=searchControl(results,Text.APPEARANCE,Text.MD3_ICONS,Md3.isOn(Md3.KEY_ICONS));
        found+=searchControl(results,Text.APPEARANCE,Text.MD3_PROFILE,Md3.isOn(Md3.KEY_PROFILE));
        found+=searchControl(results,Text.APPEARANCE,Text.MD3_INBOX,Md3.isOn(Md3.KEY_INBOX));
        found+=searchHit(results,Text.APPEARANCE,Text.AVATAR_SHAPE,()->{});
        found+=searchHit(results,Text.FPS,Text.FPS,()->{});
        found+=searchHit(results,Text.TAGS,Text.TAGS+" "+Text.TAGS_ADD,()->{});
        if(found==0){
        found += searchHit(results, Text.REGION, Text.SEARCH_REGION,
                () -> { countriesOpen = true; });
        found += searchHit(results, Text.ACCENT, Text.SEARCH_ACCENT,
                () -> { accentOpen = true; });
        found += searchHit(results, Text.THEME, Text.SEARCH_THEME,
                () -> { themeColoursOpen = true; });
        found += searchHit(results, Text.FEED, Text.SEARCH_FEED, () -> { });
        found += searchHit(results, Text.VIDEO, Text.SEARCH_VIDEO, () -> { });
        found += searchHit(results, Text.COMMENTS, Text.SEARCH_COMMENTS, () -> { });
        found += searchHit(results, Text.DOWNLOADS, Text.SEARCH_DOWNLOADS, () -> { });
        found += searchHit(results, Text.BACKUP, Text.SEARCH_BACKUP, () -> { });
        found += searchHit(results, Text.FONT, Text.SEARCH_FONT,
                () -> { fontOpen = true; });
        }
        if (found == 0) results.addView(caption(Text.SEARCH_NONE));
        column.addView(wrap(results));
    }

    private int searchControl(LinearLayout into,String section,String title,boolean on){
        if(!matchesSearch(title))return 0;if(into.getChildCount()>0)into.addView(line());
        into.addView(actionRow("search",title,on?Local.of("Включено"):Local.of("Выключено"),()->{searchQuery="";settingsGroup=groupOf(section);rebuild();page.post(()->{View target=settingTargets.get(title);if(target==null)return;int offset=0;View cursor=target;while(cursor!=column){offset+=cursor.getTop();if(!(cursor.getParent() instanceof View))break;cursor=(View)cursor.getParent();}page.smoothScrollTo(0,Math.max(0,offset-dp(16)));Motion.change(target);});}));return 1;
    }
    private int searchHit(LinearLayout into, String title, String words, final Runnable open) {
        if (!matchesSearch(title + " " + words)) return 0;
        if (into.getChildCount() > 0) into.addView(line());
        into.addView(actionRow("search", title, currentValue(title), () -> {
            open.run();
            searchQuery = "";
            settingsGroup = groupOf(title);
            page.scrollTo(0, 0);
            rebuild();
            final View target = sectionTargets.get(title);
            if (target != null) page.post(() -> page.smoothScrollTo(0, target.getTop()));
        }));
        return 1;
    }

    private String currentValue(String title){
        if(title.equals(Text.ACCENT))return String.format(Locale.US,"#%06X",Accent.colour()&0xFFFFFF);
        if(title.equals(Text.THEME))return Themes.isEnabled()?Local.of("Включена"):Local.of("Выключена");
        if(title.equals(Text.FEED))return Feed.isEnabled()?Local.of("Реклама скрыта"):Local.of("Реклама разрешена");
        if(title.equals(Text.DOWNLOADS))return Download.isEnabled()?Local.of("Без водяного знака"):Local.of("Обычное сохранение");
        if(title.equals(Text.PROFILE_STYLE))return Account.profileHandle()==null?Local.of("Откройте свой профиль"):"@"+Account.profileHandle();
        return Local.of("Открыть настройки");
    }
    private boolean matchesSearch(String words) {
        return words.toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private View section(String text) {
        renderDisclosureKey=null;
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(accentInk());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.04f);
        view.setPadding(dp(28), dp(24), dp(16), dp(8));
        sectionTargets.put(text, view);
        return view;
    }

    private String groupName(int group) {
        switch (group) {
            case 1: return Local.of("Оформление");
            case 3: return Local.of("Лента и видео");
            case 4: return Local.of("Инструменты");
            case 5: return Local.of("О проекте");
            case 6: return Local.of("Плагины");
            case 7: return Local.of("Друзья и чаты");
            default: return "TokkiYou";
        }
    }

    private void showSectionHelp() {
        String[] notes = {"", Local.of("Акцент меняет цвет кнопок и вкладок. Готовые картинки могут сохранять свой цвет. Тема и шрифты настраиваются отдельно."),
                "",
                Local.of("Глаз меняет прозрачность кнопок поверх видео; «Защита экрана» теперь здесь же. Удержание «Отправить» открывает скачивание и перемотку. Удерживайте край видео, чтобы ускорить, и потяните вниз, чтобы закрепить скорость."),
                Local.of("Скачивание, резервные копии, диагностика и журнал. Сохраните копию перед экспериментами."),
                Local.of("Здесь находятся версия мода, обновления, журнал и участники."), Local.of("Магазин, установка и настройки плагинов. Плагины запускают дополнительный код; устанавливайте только те, которым доверяете."),
                Local.of("Закреплённые друзья, автопродление серий и уведомления мода.")};
        Panel.with(this,skin,Local.of("Подсказки")).text(notes[Math.max(0,Math.min(notes.length-1,settingsGroup))])
                .quiet(Local.of("Обучение"),() -> Tutorial.show(this)).primary(Local.of("Понятно"),null).show();
    }

    private View groupNavigation() {
        LinearLayout list = card();
        String[] icons = {"palette", "", "play_circle", "tune", "info", "extension", "chat"};
        String[] notes = {Local.of("Акцент, тема и шрифты"),
                "",
                Local.of("Просмотр, кнопки, защита экрана и регион"),
                Local.of("Скачивание, копии и диагностика"),
                Local.of("Версия, обновления и участники"), Local.of("Магазин и установленные"),
                Local.of("Друзья, серии и уведомления")};
        // Shelf order on screen: what people touch daily first, the project itself last.
        // Lite has the shelves that have something on them for it
        int[] shelf = Version.LITE ? new int[] {1, 3, 4, 5} : new int[] {1, 3, 7, 4, 6, 5};
        for (int n = 0; n < shelf.length; n++) {
            final int i = shelf[n];
            final int chosen = i;
            if (n > 0) list.addView(line());
            LinearLayout entry = row();
            entry.setPadding(dp(16),dp(14),dp(20),dp(14));
            entry.addView(icon(icons[i-1],false,52));
            LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
            TextView head = label(groupName(i)); head.setTextSize(19);
            head.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            words.addView(head);words.addView(detail(notes[i-1]));entry.setContentDescription(groupName(i)+". "+notes[i-1]);
            entry.addView(words,grow()); entry.addView(away());
            entry.setMinimumHeight(dp(80));Motion.touch(entry);
            entry.setOnClickListener(v -> {
                if(navigating)return;navigating=true;
                Motion.navigate(column,1f,()->{settingsGroup=chosen;page.scrollTo(0,0);rebuild();navigating=false;});
            });
            list.addView(entry);
        }
        return wrap(list);
    }

    /**
     * Which shelf a section lives on.
     * 1 look, 3 feed and video, 4 tools, 5 about, 6 plugins, 7 friends and chats,
     * Remaining settings are tools: downloads and backups.
     */
    private int groupOf(String section) {
        if (section.equals(Text.PLUGINS)) return 6;
        if (section.equals(Text.APPEARANCE) || section.equals(Text.ACCENT)
                || section.equals(Text.THEME) || section.equals(Text.FONT)) return 1;
        if (section.equals(Text.REGION) || section.equals(Text.COUNTRY)
                || section.equals(Text.FEED) || section.equals(Text.VIDEO)
                || section.equals(Text.RAIL) || section.equals(Text.COMMENTS)
                || section.equals(Text.TAGS) || section.equals(Text.FPS)
                || section.equals(Text.TIKTOK_FEATURES)
                || section.equals(BURN)) return 3;
        if (section.equals(Text.PINNED_FRIENDS) || section.equals(Text.STREAKS)
                || section.equals(Text.NOTIFICATIONS)) return 7;
        if (section.equals(Text.THANKS) || section.equals(Text.UPDATE) || section.equals(Text.CHANNEL)
                || section.equals(Text.LINKS)) return 5;
        return 4; // downloads and backup
    }

    private void filterGroup() {
        int current = 0;
        boolean offered = true;
        for (int i = 3; i < column.getChildCount(); i++) {
            View item = column.getChildAt(i);
            if (item instanceof TextView && sectionTargets.containsValue(item)) {
                String section = ((TextView) item).getText().toString();
                current = groupOf(section);
                offered = !Version.LITE || inLite(section);
            }
            item.setVisibility(current == settingsGroup && offered ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * The sections that do something in TikTok Lite. The rest hook classes
     * Lite does not have, and a switch that does nothing is worse than none.
     */
    private static boolean inLite(String section) {
        return section.equals(Text.REGION) || section.equals(Text.COUNTRY)
                || section.equals(Text.ACCENT) || section.equals(Text.THEME)
                || section.equals(Text.DOWNLOADS)
                || section.equals(Text.THANKS) || section.equals(Text.LINKS) || section.equals(Text.CHANNEL);
    }

    private static final String BURN = Local.of("Защита экрана");

    private boolean navigating;
    private void goBack() {
        if(navigating)return;
        if (searchQuery.length() > 0) { searchQuery = ""; rebuild(); }
        else if (settingsGroup != 0) {
            // the flags are reached from the tools, so that is where back leads
            final int to = settingsGroup == FLAGS_PAGE ? 4 : 0;
            navigating=true;Motion.navigate(column,-1f,()->{settingsGroup=to;rebuild();navigating=false;});
        }
        else finish();
    }

    @Override public void onBackPressed() { goBack(); }

    @Override protected void onDestroy() {
        if (column != null) { column.removeCallbacks(delayedRebuild); column.animate().withEndAction(null).cancel(); }
        if (page != null) {
            if (scrollRestore != null) page.removeCallbacks(scrollRestore);
            if (scrollRestoreListener != null && page.getViewTreeObserver().isAlive())
                page.getViewTreeObserver().removeOnPreDrawListener(scrollRestoreListener);
        }
        super.onDestroy();
    }

    private View caption(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(dp(16), dp(12), dp(16), dp(4));
        return view;
    }

    /**
     * A segmented list in Material 3 Expressive: every row is a tile of its own,
     * with large outer corners, small inner ones and a thin gap between them.
     */
    private LinearLayout card() {
        return new Expressive.Group(this, skin.card, (Accent.colour() & 0xFFFFFF) | 0x33000000);
    }

    /** A lone row is a one-tile list, so it looks like every other group. */
    private View wrap(View card) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, dp(12), dp(6));
        if (!(card instanceof Expressive.Group)) {
            LinearLayout single = card();
            single.addView(card);
            card = single;
        }
        box.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** The accent as text on the page: lighter on dark, darker on light. */
    private int accentInk() {
        return skin.dark() ? Expressive.mix(Accent.colour(), 0xFFFFFFFF, 0.45f)
                : Expressive.mix(Accent.colour(), 0xFF000000, 0.35f);
    }

    /** The accent laid thin over the card: the tonal container of M3. */
    private int accentTonal() {
        return Expressive.mix(skin.card, Accent.colour(), 0.24f);
    }

    private View line() {
        View spacer=new View(this);spacer.setVisibility(View.GONE);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,0));return spacer;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(4), dp(20), dp(4));
        row.setMinimumHeight(dp(56));
        Motion.press(row);
        return row;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setIncludeFontPadding(false);view.setPadding(0,0,dp(12),0);
        return view;
    }

    private TextView detail(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View sized(View view, int height) {
        if (height > 1) view.setMinimumHeight(dp(Math.max(56, height)));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height <= 1 ? dp(height) : ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /**
     * The bar itself: one line of why, and the button that does it.
     *
     * Painted in the card colour with a hairline above, so it reads as resting
     * on the page rather than floating over it, and padded underneath by
     * whatever the navigation bar takes -- otherwise the button sits under the
     * gesture pill.
     */
    private View restartBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        styleRestartBar(bar);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(20), dp(10), dp(12), dp(10));

        RestartSymbol symbol = new RestartSymbol(this, Accent.colour());
        LinearLayout.LayoutParams symbolSize = new LinearLayout.LayoutParams(dp(24),dp(24));
        symbolSize.rightMargin = dp(12);
        content.addView(symbol,symbolSize);

        TextView why = new TextView(this);
        why.setText(Text.RESTART_PENDING);
        why.setTextColor(skin.text);
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        content.addView(why, grow());

        TextView button = new TextView(this);
        button.setText(Text.RESTART);
        button.setTextColor(onAccent());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16),0,dp(16),0);button.setMinWidth(dp(132));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Accent.colour());
        pill.setCornerRadius(dp(24));
        button.setMinHeight(dp(48));
        button.setFocusable(true);
        button.setBackground(pill);
        button.setOnClickListener(v -> restartTikTok());
        LinearLayout.LayoutParams actionSize=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(48));actionSize.leftMargin=dp(12);content.addView(button,actionSize);
        why.setIncludeFontPadding(false);why.setMaxLines(2);why.setGravity(Gravity.CENTER_VERTICAL);
        restartSymbol=symbol;restartWhy=why;restartAction=button;restartBackground=pill;
        Fonts.applyTree(content);Motion.press(button);
        bar.addView(content);
        return bar;
    }

    /**
     * What to write on the accent: the palette holds a mint and a near-white
     * as well as the pink, and white letters on either of those are unreadable.
     */
    private int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        int brightness = (red * 299 + green * 587 + blue * 114) / 1000;
        return brightness > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    /**
     * How much of the bottom belongs to the system, keyboard excluded.
     *
     * The system window inset counts the keyboard as well, so a rebuild while
     * one was up -- adding a hashtag, say -- padded the restart bar by the
     * height of the keyboard and turned it into half a screen.
     */
    private int navigationBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets == null) return 0;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return insets.getInsets(
                        android.view.WindowInsets.Type.navigationBars()).bottom;
            }
            return insets.getStableInsetBottom();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int statusBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            int top = 0;
            if (insets != null) {
                top = android.os.Build.VERSION.SDK_INT >= 30
                        ? insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()).top
                        : insets.getStableInsetTop();
            }
            if (top > 0) return top;
        } catch (Throwable ignored) {
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ----------------------------------------------------- the small shapes

    private interface ColourChanged { void changed(int colour); }

    /** A full hue and saturation/value picker, with no colour-code entry. */
    private static final class AccentColourPicker extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv = new float[3];
        private final int[] rainbow = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, 0xFFFF00FF, Color.RED};
        private ColourChanged changed;

        AccentColourPicker(Context context, int initial) {
            super(context);
            Color.colorToHSV(initial, hsv);
            setContentDescription(Text.ACCENT_PICKER);
        }

        void onColourChanged(ColourChanged listener) { changed = listener; }

        int colour() { return Color.HSVToColor(hsv); }
        void setColour(int colour){Color.colorToHSV(colour,hsv);invalidate();if(changed!=null)changed.changed(colour);}

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(10);
            float left = pad, right = getWidth() - pad, top = pad;
            float bottom = getHeight() - dp(42);
            int pureHue = Color.HSVToColor(new float[] {hsv[0], 1f, 1f});
            paint.setShader(new LinearGradient(left, top, right, top,
                    Color.WHITE, pureHue, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setShader(new LinearGradient(left, top, left, bottom,
                    0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setShader(null);

            float markX = left + hsv[1] * (right - left);
            float markY = top + (1f - hsv[2]) * (bottom - top);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.BLACK);
            canvas.drawCircle(markX, markY, dp(8), paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(markX, markY, dp(6), paint);

            float hueTop = bottom + dp(12), hueBottom = getHeight() - dp(8);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(left, hueTop, right, hueTop, rainbow,
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(left, hueTop, right, hueBottom, dp(5), dp(5), paint);
            paint.setShader(null);
            float hueX = left + hsv[0] / 360f * (right - left);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(hueX, (hueTop + hueBottom) / 2f, dp(8), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_DOWN
                    && event.getAction() != android.view.MotionEvent.ACTION_MOVE
                    && event.getAction() != android.view.MotionEvent.ACTION_UP) return true;
            float pad = dp(10), left = pad, right = Math.max(left + 1, getWidth() - pad);
            float top = pad, bottom = getHeight() - dp(42);
            if (event.getY() <= bottom + dp(5)) {
                hsv[1] = Math.max(0f, Math.min(1f, (event.getX() - left) / (right - left)));
                hsv[2] = 1f - Math.max(0f, Math.min(1f,
                        (event.getY() - top) / Math.max(1f, bottom - top)));
            } else {
                hsv[0] = Math.max(0f, Math.min(1f,
                        (event.getX() - left) / (right - left))) * 360f;
            }
            invalidate();
            if (changed != null) changed.changed(colour());
            return true;
        }
    }

    /** A tick, drawn rather than typed: the glyph fonts have is never the one. */
    private static final class Check extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Check(Context context, int colour) {
            super(context);
            paint.setColor(colour);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int size = Math.round(22 * getResources().getDisplayMetrics().density);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float unit = getWidth() / 22f;
            paint.setStrokeWidth(unit * 2.2f);
            float y = getHeight() / 2f;
            canvas.drawLine(unit * 4, y + unit, unit * 9, y + unit * 5.5f, paint);
            canvas.drawLine(unit * 9, y + unit * 5.5f, unit * 18, y - unit * 5f, paint);
        }
    }

    /** Circle at rest; a rounded square and outline indicate the selected colour. */
    private static final class Dot extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        private final android.graphics.Path outline = new android.graphics.Path();
        private boolean chosen;
        private float selection;
        private android.animation.ValueAnimator animation;

        Dot(Context context, int colour, boolean chosen) {
            super(context);
            this.chosen=chosen; selection=chosen?1f:0f;
            fill.setColor(colour); ring.setColor(colour); ring.setStyle(Paint.Style.STROKE);
            setSelected(chosen); setFocusable(true);
        }
        private final Paint customInk=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float customProgress;
        private android.animation.ValueAnimator colourAnimation;
        void reveal(int colour){
            choose(true,true);ring.setColor(colour);
            if(!Motion.enabled(getContext())){fill.setShader(null);fill.setColor(colour);invalidate();return;}
            if(colourAnimation!=null)colourAnimation.cancel();customInk.setColor(colour);customProgress=0;
            colourAnimation=android.animation.ValueAnimator.ofFloat(0,1);colourAnimation.setDuration(220L);
            colourAnimation.addUpdateListener(a->{customProgress=(Float)a.getAnimatedValue();invalidate();});
            colourAnimation.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){fill.setShader(null);fill.setColor(colour);customProgress=0;colourAnimation=null;invalidate();}});
            colourAnimation.start();
        }
        void choose(boolean selected, boolean animate) {
            chosen=selected; setSelected(selected);
            if (animation!=null) {animation.cancel();animation=null;}
            if (!animate || !Motion.enabled(getContext())) {selection=chosen?1f:0f;invalidate();return;}
            animation=android.animation.ValueAnimator.ofFloat(selection,chosen?1f:0f);
            animation.setDuration(180L);
            animation.addUpdateListener(a -> {selection=(Float)a.getAnimatedValue();invalidate();});
            animation.start();
        }
        @Override protected void onMeasure(int width, int height) {
            int size=Math.round(26*getResources().getDisplayMetrics().density);
            setMeasuredDimension(resolveSize(size,width),resolveSize(size,height));
        }
        @Override protected void onDraw(Canvas canvas) {
            float unit=getResources().getDisplayMetrics().density;
            float x=getWidth()/2f,y=getHeight()/2f;
            float radius=Math.max(1f,Math.min(x,y)-6*unit);
            // chosen: the circle grows a little, scallops into a cookie and turns one lobe
            Expressive.shape(outline,Expressive.COOKIE9,x,y,radius+3*unit*selection,selection,selection*360f/Expressive.lobes(Expressive.COOKIE9));
            canvas.drawPath(outline,fill);
            if(customProgress>0){customInk.setAlpha(Math.round(255*customProgress));canvas.drawPath(outline,customInk);}
            if (selection>0f) {
                int shade=fill.getColor();
                boolean light=(Color.red(shade)*299+Color.green(shade)*587+Color.blue(shade)*114)/1000>150;
                ring.setStyle(Paint.Style.STROKE);ring.setStrokeCap(Paint.Cap.ROUND);ring.setStrokeWidth(2.4f*unit);
                ring.setColor(light ? 0xFF111111 : 0xFFFFFFFF);ring.setAlpha(Math.round(255*Math.min(1f,selection)));
                canvas.drawLine(x-5*unit,y+0.5f*unit,x-1.5f*unit,y+4*unit,ring);
                canvas.drawLine(x-1.5f*unit,y+4*unit,x+5.5f*unit,y-3.5f*unit,ring);
            }
        }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (hasOnClickListeners()) {info.setCheckable(true);info.setChecked(chosen);info.setClassName("android.widget.RadioButton");}
        }
        @Override protected void onDetachedFromWindow() {
            if (animation!=null) {animation.cancel();animation=null;}
            if(colourAnimation!=null){colourAnimation.cancel();colourAnimation=null;}
            selection=chosen?1f:0f;super.onDetachedFromWindow();
        }
    }
}
