package cat.narezany.tokkiyou;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.ss.android.ugc.aweme.im.common.model.StickerBase;
import com.ss.android.ugc.aweme.im.common.model.StickerImage;
import com.ss.android.ugc.aweme.im.common.model.StickerItem;
import com.ss.android.ugc.aweme.im.streak.api.IStreakService;
import com.ss.android.ugc.aweme.im.streak.api.StreakData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeping a streak alive by sending the sticker you picked.
 *
 * This is the one thing in the mod that acts on its own rather than answering
 * a question, and it is the only one that sends anything. It is off unless
 * switched on, it needs a sticker to have been chosen, and it will send at
 * most one sticker per conversation per day.
 *
 * When it sends is TikTok's own judgement rather than a guess about clocks:
 * the app puts a status on every streak and the mod reads it. A grey flame is
 * a streak still waiting for today, and that is the whole condition.
 *
 * Two halves, and both are found the same way -- by real names, at runtime.
 *
 * Which streaks are fading: `StreakData` is TikTok's own class and its fields
 * say everything -- `convId`, `activeBefore`, `endAt`. Reading them is not the
 * problem; getting hold of one is. Nothing in the app ever reads those fields,
 * so there is nothing there to listen to.
 *
 * What the app does do, all the time, is ask its own `IStreakService` about a
 * conversation -- and that name and its signatures are real. So the mod
 * listens to the questions rather than the answers: every conversation the app
 * asks about is remembered, along with the service that was asked, and from
 * then on the mod can ask about it itself.
 *
 * How to send: `IMStickerApi` and `getImStickerMessageService()` are real
 * names, and the service has exactly one method taking thirteen arguments.
 * Everything that method needs is read out of its own signature -- the source
 * it wants is an enum with a real constant on it, and the options object is a
 * class with a one-argument constructor. So there is not one obfuscated name
 * written down here, and a release that renames them all changes nothing.
 */
public final class Streaks {

    private Streaks() {}

    public static final String KEY_ON = "streak_auto";
    public static final String KEY_STICKER = "streak_sticker";
    public static final String KEY_MODE = "streak_mode";
    public static final String KEY_TEXT = "streak_text";

    /** What to send: a sticker, or a message. */
    public static final String BY_STICKER = "sticker";
    public static final String BY_TEXT = "text";

    public static final String DEFAULT_TEXT = "Это авто серия!";

    /**
     * The status TikTok gives a streak whose flame has gone grey.
     *
     * Its own enum, with its own names: ACTIVE is a streak already kept today,
     * SECONDARY_ACTIVE is one still waiting, EXPIRED is one that is gone. The
     * grey flame is the middle one, and that is the only one worth sending to.
     */
    private static final String GREY = "SECONDARY_ACTIVE";

    /** The day a streak gets its flame. Before it there is nothing on screen to keep. */
    private static final int SHOWN_FROM = 3;

    private static final long EVERY = 15 * 60 * 1000L;

    /** A conversation that is still grey after this many sends is left alone until tomorrow. */
    private static final int MAX_ATTEMPTS = 3;
    /** How long to give a send before looking at the flame again, and sending once more if it is still grey. */
    private static final long RECHECK = 4 * 60 * 1000L;
    /** How long to watch the flame right after a send. */
    private static final long VERIFY = 15000L;
    private static final long DAY = 24 * 60 * 60 * 1000L;

    /** The constant on TikTok's own enum that says who sent this and why. */
    private static final String SOURCE = "AUTO_CONSECUTIVE_SA_STICKERS";

    /** Streaks the app has looked at, newest last. */
    private static final Map<String, StreakData> known =
            new LinkedHashMap<String, StreakData>();

    /** Stickers the app has drawn, so there is something to choose from. */
    private static final Map<String, StickerItem> seenStickers =
            new LinkedHashMap<String, StickerItem>();
    private static final Map<String, StickerItem> favourites = new LinkedHashMap<String, StickerItem>();

    private static volatile boolean started;
    private static Handler timer;
    private static Context timerContext;
    private static final Runnable timedRound = new Runnable(){public void run(){
        Context c=timerContext;if(c!=null)round(c);rescheduleForeground();
    }};
    static synchronized void rescheduleForeground(){
        if(timer==null || timerContext==null)return;
        timer.removeCallbacks(timedRound);
        if(isEnabled())timer.postDelayed(timedRound,StreakSchedule.delay(System.currentTimeMillis(),EVERY));
    }

    // -------------------------------------------------------- the switches

    public static boolean isEnabled() {
        return flag(KEY_ON, false);
    }

    public static void setEnabled(boolean on) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_ON, on).apply();
        Context context = TokkiYou.context();
        if (context != null) StreakAlarm.schedule(context, false);
        if (on) {
            if (context != null) round(context);
        }
    }

    public static String mode() {
        SharedPreferences prefs = prefs();
        return prefs == null ? BY_STICKER : prefs.getString(KEY_MODE, BY_STICKER);
    }

    public static void setMode(String mode) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_MODE, mode).apply();
    }

    public static String text() {
        SharedPreferences prefs = prefs();
        return prefs == null ? DEFAULT_TEXT : prefs.getString(KEY_TEXT, DEFAULT_TEXT);
    }

    public static void setText(String value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit().putString(KEY_TEXT,
                    value == null || value.trim().length() == 0 ? DEFAULT_TEXT : value.trim())
                    .apply();
        }
    }

    public static String chosen() {
        SharedPreferences prefs = prefs();
        return prefs == null ? "" : prefs.getString(KEY_STICKER, "");
    }

    public static void choose(String id) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_STICKER, id).putString(KEY_MODE,BY_STICKER).apply();
    }

    private static boolean flag(String key, boolean fallback) {
        try {
            SharedPreferences prefs = prefs();
            return prefs == null ? fallback : prefs.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static SharedPreferences prefs() {
        Context context = TokkiYou.context();
        if (context == null) return null;
        return context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------ where the reads land

    /** The service itself, kept from whichever call went past most recently. */
    private static volatile IStreakService service;

    /** Conversations the app has asked about, which is the list to work from. */
    private static final Map<String, Long> asked = new LinkedHashMap<String, Long>();

    /** The app looking a streak up: both halves of the answer are worth having. */
    public static StreakData streakOf(IStreakService from, String conversation, boolean fresh) {
        StreakData data = readStreak(from,conversation,fresh);
        note(from, conversation);
        if (data != null) {
            synchronized (known) {
                known.put(conversation, data);
            }
        }
        return data;
    }

    /** The app asking whether a conversation has a streak. */
    public static boolean hasStreak(IStreakService from, String conversation) {
        note(from, conversation);
        return from != null && from.a0(conversation);
    }

    /** The app asking whether to show one. */
    public static boolean showsStreak(IStreakService from, String conversation, boolean flag) {
        note(from, conversation);
        try{if(from==null)return false;Method m;try{m=from.getClass().getMethod("LJJJI",String.class,boolean.class);}catch(NoSuchMethodException e){m=from.getClass().getMethod("h0",String.class,boolean.class);}m.setAccessible(true);return Boolean.TRUE.equals(m.invoke(from,conversation,flag));}catch(Throwable error){Diary.note("streak visibility: "+error);return false;}
    }

    // The rest are the same thing: questions the app asks about one
    // conversation. Nothing is done with the answers -- they are here so that
    // the conversation is known about at all.

    public static int streakCount(IStreakService from, String conversation) {
        note(from, conversation);
        return from == null ? 0 : from.w(conversation);
    }

    public static boolean asksAbout(IStreakService from, String conversation) {
        note(from, conversation);
        return from != null && from.X(conversation);
    }

    public static boolean asksAboutToo(IStreakService from, String conversation) {
        note(from, conversation);
        return from != null && from.Y(conversation);
    }

    public static Integer streakState(IStreakService from, String conversation) {
        note(from, conversation);
        return from == null ? null : from.l0(conversation);
    }

    public static String streakText(IStreakService from, String conversation) {
        note(from, conversation);
        return from == null ? null : from.O(conversation);
    }

    private static volatile boolean discoveryQueued;
    /** The service has just turned up: whatever was waiting for it can go now. */
    private static void kick() {
        if (!isEnabled() || discoveryQueued) return;
        discoveryQueued = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            discoveryQueued = false;
            Context c = TokkiYou.context();
            if (c != null) round(c);
        }, 1500L);
    }

    /** True once a round has everything it needs: an account and TikTok's own service. */
    static boolean ready() {
        NativeStreaks.prepare();
        return service != null && Account.liveId() != null;
    }

    /**
     * The same question, given a moment to become true.
     *
     * An alarm that started the process finds a TikTok that has not asked
     * anything yet, and the service arrives while the app boots. Waiting for it
     * here is what decides whether that round sends or is handed to the next
     * one; the caller sets the alarm again first, so a wait that runs out costs
     * a retry rather than the send.
     */
    static boolean ready(long waitMillis) {
        long end = System.currentTimeMillis() + waitMillis;
        while (!ready() && System.currentTimeMillis() < end) {
            try { Thread.sleep(250); } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); break; }
        }
        return ready();
    }

    /**
     * One round, on the calling thread, for the alarm.
     * Answers whether it is worth trying again soon.
     */
    static boolean runNow(Context context) {
        if (!isEnabled()) return false;
        if (!roundRunning.compareAndSet(false, true)) return true;
        try {
            return roundWorker(context, false);
        } finally {
            roundRunning.set(false);
        }
    }
    private static void note(IStreakService from, String conversation) {
        try {
            currentAccount();
            if (from != null) {boolean first=service==null;if(service==null || service.getClass()!=from.getClass())status=null;service = from;if(first)kick();}
            if (conversation == null || conversation.length() == 0) return;
            synchronized (asked) {
                if (asked.size() > 200) {
                    asked.remove(asked.keySet().iterator().next());
                }
                boolean fresh = !asked.containsKey(conversation);
                asked.put(conversation, Long.valueOf(System.currentTimeMillis()));
                if (fresh) {persistConversations();if(isEnabled()&&!discoveryQueued){discoveryQueued=true;new Handler(Looper.getMainLooper()).postDelayed(()->{discoveryQueued=false;Context c=TokkiYou.context();if(c!=null)round(c);},5000L);}}

            }
        } catch (Throwable ignored) {
        }
    }

    /** Every sticker the app touches, so the settings have something to offer. */
    public static StickerBase stickerBase(StickerItem sticker) {
        StickerBase base = sticker == null ? null : sticker.stickerBase;
        try {
            String id = idOf(base);
            if (id != null) {
                synchronized (seenStickers) {
                    if (seenStickers.size() >= 120) {
                        seenStickers.remove(seenStickers.keySet().iterator().next());
                    }
                    seenStickers.put(id, sticker);
                }
            }
        } catch (Throwable ignored) {
        }
        return base;
    }

    static void clearStickers(){synchronized(seenStickers){seenStickers.clear();favourites.clear();}}
    static boolean replaceFavourites(List<?> items){
        synchronized(seenStickers){
            Map<String,StickerItem> next=new LinkedHashMap<String,StickerItem>();
            for(Object value:items)if(value instanceof StickerItem){StickerItem item=(StickerItem)value;String id=idOf(item.stickerBase);if(id!=null)next.put(id,item);}
            boolean changed=!new ArrayList<String>(favourites.keySet()).equals(new ArrayList<String>(next.keySet()));
            favourites.clear();favourites.putAll(next);return changed;
        }
    }
    static void discover(IStreakService from,String conversation){note(from,conversation);}
    static boolean rememberFavourite(StickerItem item){
        if(item==null)return false;String id=idOf(item.stickerBase);if(id==null)return false;
        synchronized(seenStickers){boolean added=!favourites.containsKey(id);favourites.put(id,item);return added;}
    }
    private static String conversationAccount;
    private static synchronized void currentAccount(){
        String uid=Account.liveId();if(java.util.Objects.equals(uid,conversationAccount))return;
        conversationAccount=uid;service=null;status=null;synchronized(asked){asked.clear();}synchronized(known){known.clear();}
        if(uid!=null)restoreConversations();
    }
    private static String conversationKey(){return "streak_conversations_"+Account.liveId();}

    /** What to call a sticker: its own id, or failing that its picture's. */
    private static String idOf(StickerBase base) {
        if (base == null) return null;
        if (base.id != null) return String.valueOf(base.id);
        StickerImage image = base.image != null ? base.image : base.thumbnail;
        return image == null || image.uri == null ? null : image.uri;
    }

    /** What the settings screen offers: whatever has been seen, newest first. */
    public static List<String> offered() {
        FavouriteStickers.read();
        synchronized (seenStickers) {
            List<String> out = new ArrayList<String>(favourites.keySet());
            java.util.Collections.reverse(out);
            return out;
        }
    }

    public static StickerItem sticker(String id) {
        synchronized (seenStickers) {
            return favourites.get(id);
        }
    }

    /**
     * A small picture of a sticker, for the settings to show.
     *
     * Fetched once and kept in memory: this is a grid of a couple of dozen
     * thumbnails on one screen, not something worth a cache on disk.
     */
    public static android.graphics.Bitmap thumbnail(Context context, String id) {
        return thumbnail(context,id,null);
    }
    public static android.graphics.Bitmap thumbnail(Context context,String id,Runnable ready) {
        final String url=urlOf(id);if(url==null)return null;
        synchronized(thumbnails){
            android.graphics.Bitmap cached=thumbnails.get(id);if(cached!=null)return cached;
            if(ready!=null){List<Runnable> list=waiting.get(id);if(list==null){list=new ArrayList<Runnable>();waiting.put(id,list);}list.add(ready);}
            if(loading.contains(id))return null;
            Long failed=failures.get(id);
            if(failed!=null && System.currentTimeMillis()-failed<30000L){waiting.remove(id);return null;}
            loading.add(id);
        }
        final String key=id;
        thumbnailWorkers.execute(()->{
            android.graphics.Bitmap bitmap=null;
            try {byte[] raw=Net.bytes(url);if(raw!=null){
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>160)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;bitmap=android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);
            }}catch(Throwable ignored){}
            List<Runnable> callbacks;
            synchronized(thumbnails){loading.remove(key);callbacks=waiting.remove(key);
                if(bitmap!=null){if(thumbnails.size()>=60)thumbnails.remove(thumbnails.keySet().iterator().next());thumbnails.put(key,bitmap);failures.remove(key);}
                else {if(failures.size()>=120)failures.remove(failures.keySet().iterator().next());failures.put(key,System.currentTimeMillis());}
            }
            if(bitmap!=null && callbacks!=null)new Handler(Looper.getMainLooper()).post(()->{for(Runnable callback:callbacks)try{callback.run();}catch(Throwable e){Diary.note("sticker preview callback: "+e.getClass().getSimpleName());}});
        });return null;
    }
    private static final java.util.concurrent.ExecutorService thumbnailWorkers=java.util.concurrent.Executors.newFixedThreadPool(2,r->{
        Thread thread=new Thread(r,"tokkiyou-sticker-thumbnail");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;
    });
    private static final Map<String,android.graphics.Bitmap> thumbnails=new LinkedHashMap<String,android.graphics.Bitmap>();
    private static final java.util.Set<String> loading=new java.util.HashSet<String>();
    private static final Map<String,Long> failures=new LinkedHashMap<String,Long>();
    private static final Map<String,List<Runnable>> waiting=new LinkedHashMap<String,List<Runnable>>();

    private static String urlOf(String id) {
        try {
            StickerItem sticker = sticker(id);
            StickerBase base = sticker == null ? null : sticker.stickerBase;
            if (base == null) return null;
            // the thumbnail first: this is a grid of them, and the full-size
            // picture is a video on some stickers and a still on others
            StickerImage image = base.thumbnail != null ? base.thumbnail : base.image;
            if (image == null || image.urlList == null || image.urlList.isEmpty()) image=base.image;
            if (image == null || image.urlList == null || image.urlList.isEmpty()) return null;
            return String.valueOf(image.urlList.get(0));
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------ the round

    public static synchronized void start(Context context) {
        if (started) return;
        started = true;
        restoreConversations();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Context application = context.getApplicationContext();
        timer=handler;timerContext=application==null?context:application;
        try {
            // Made now rather than when the first one is needed: a channel that
            // does not exist yet takes the notification with it, and the only
            // notification this feature sends is the one that saves the streak.
            StreakAlarm.channel(application);
            // Put in again on every start, without asking whether one is already
            // there: a pending intent outlives the alarm it stood for -- a reboot
            // or a force stop cancels the alarm and leaves the intent behind --
            // so "something is already waiting" is not proof that anything will
            // ever fire. Replacing it costs one call and cannot lose a send.
            if (isEnabled()) StreakAlarm.schedule(application, false);
        } catch (Throwable ignored) { }
        rescheduleForeground();
        handler.postDelayed(()->round(timerContext),30000L);
    }

    /** One pass over what is known, sending where it is needed. */
    public static void round(final Context context) {
        if (!isEnabled()) return;
        if (!roundRunning.compareAndSet(false, true)) return;
        Net.away("streaks", () -> {
            try { if (roundWorker(context)) StreakAlarm.schedule(context, true); }
            catch (Throwable error) { Diary.note("streaks round: " + error); }
            finally { roundRunning.set(false); }
        });
    }

    private static final java.util.concurrent.atomic.AtomicBoolean roundRunning =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** Answers whether it is worth trying again soon (no service yet, or a send failed). */
    private static boolean roundWorker(final Context context) {
        return roundWorker(context, true);
    }

    private static boolean roundWorker(final Context context, boolean verifyImmediately) {
        final long deadline = verifyImmediately ? Long.MAX_VALUE
                : android.os.SystemClock.uptimeMillis() + 2000L;
        currentAccount();if(Account.liveId()==null)return true;NativeStreaks.prepare();if(!StreakSchedule.due(System.currentTimeMillis(),StreakSchedule.minute()))return false;
        if(!StreakAlarm.online(context)){Diary.note("streaks: no network -- waiting for it, nothing marked as sent");return true;}
        FavouriteStickers.read();
        final boolean byText = BY_TEXT.equals(mode());
        final StickerItem sticker = byText ? null : sticker(chosen());
        if (!byText && sticker == null) {
            if (chosen().length() == 0) {
                Diary.note("streaks: nothing to send -- no sticker chosen yet");
                return false;
            }
            Diary.note("streaks: chosen sticker is still loading -- retrying");
            new Handler(Looper.getMainLooper()).post(() -> FavouriteStickers.load(null));
            return true;
        }

        List<String> all = conversations();
        if (all.isEmpty()) {
            Diary.note("streaks: no conversation has been looked at yet");
            return true; // An initialized service can still be loading its conversations.
        }

        final String owner=Account.liveId();
        final List<String> due = new ArrayList<String>();
        int grey = 0, lit = 0, gone = 0, unknown = 0, already = 0, waiting = 0, none = 0;
        boolean again = false;
        long now = System.currentTimeMillis();
        for (String conversation : all) {
            if (android.os.SystemClock.uptimeMillis() >= deadline) return true;
            StreakData data = ask(conversation);
            if (data == null) {
                unknown++;
                again = true;
                continue;
            }
            if(data.convId!=null && !conversation.equals(data.convId)){unknown++;again=true;continue;}
            // TikTok counts days from the first message two people exchange, and
            // shows a flame from the third. Until then there is no streak to
            // keep -- and this used to write to every one of those, which is
            // everybody a person had spoken to in the last couple of days.
            if(data.streak<SHOWN_FROM){none++;continue;}
            String state = statusOf(data);
            if (GREY.equals(state)) {
                grey++;
                int made = automaticAttempts(conversation, now);
                if (made >= MAX_ATTEMPTS) {
                    already++;
                } else if (made > 0 && now - automaticLast(conversation) < RECHECK) {
                    waiting++;
                    again = true;
                } else {
                    if (made > 0) Diary.note("streaks: still grey after attempt " + made + ", sending again: " + conversation);
                    due.add(conversation);
                }
            } else if ("ACTIVE".equals(state)) {
                lit++;
                // A selected time means one daily send to every existing flame,
                // even while TikTok still calls it active. No-time mode remains grey-only.
                if (StreakSchedule.minute() >= 0 && automaticAttempts(conversation, now) == 0) due.add(conversation);
            } else if (state == null) {
                unknown++;
                again = true;
            } else {
                gone++;
            }
        }

        Diary.note("streaks: " + all.size() + " looked at -- " + grey + " grey, "
                + lit + " lit, " + gone + " over, " + none + " with no flame yet, "
                + unknown + " unreadable; "
                + already + " gave up for today, " + waiting + " waiting to be re-checked, "
                + due.size() + " to send");
        if (due.isEmpty()) return service == null || again;

        boolean failed = false;
        final List<String> submitted = new ArrayList<String>();
        for (String conversation : due) {
            if (android.os.SystemClock.uptimeMillis() >= deadline) return true;
            if(!owner.equals(Account.liveId()) || !isEnabled())return false;
            if (deliver(context, sticker, conversation)) {
                rememberAutomatic(conversation);
                submitted.add(conversation);
                Diary.note("streak request submitted; delivery unconfirmed: "
                        + conversation);
            } else {
                Diary.note("streak request failed: " + conversation);
                failed = true;
            }
        }
        // Broadcast receivers have a short execution deadline. Check delivery on
        // the next alarm instead of sleeping another fifteen seconds in goAsync.
        if (!submitted.isEmpty() && (!verifyImmediately || !verify(submitted))) failed = true;
        return failed || again;
    }

    /**
     * Watch the flame for a few seconds after sending.
     * True when every conversation stopped being grey; the rest are left for
     * the next check, which sends again if the flame is still grey by then.
     */
    private static boolean verify(List<String> sent) {
        List<String> left = new ArrayList<String>(sent);
        long end = System.currentTimeMillis() + VERIFY;
        while (!left.isEmpty() && System.currentTimeMillis() < end) {
            try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
            for (java.util.Iterator<String> it = left.iterator(); it.hasNext(); ) {
                String conversation = it.next();
                String state = statusOf(ask(conversation));
                if (state != null && !GREY.equals(state)) {
                    Diary.note("streak confirmed: " + conversation + " is now " + state);
                    it.remove();
                }
            }
        }
        if (!left.isEmpty()) Diary.note("streak not confirmed yet, " + left.size() + " still grey -- will look again");
        return left.isEmpty();
    }

    /**
     * What TikTok makes of a streak: its own word for it.
     *
     * The service has a method that turns a streak into a status, and the
     * status is an enum whose constants TikTok named itself. Neither the
     * method nor the enum keeps its name between releases, but the shape does:
     * it is the one method taking a streak and answering with an enum. So it
     * is found by that, and the answer is read as the name it carries.
     */
    private static String statusOf(StreakData data) {
        IStreakService from = service;
        if (from == null || data == null) return null;
        try {
            Method reader = status;
            if (reader == null) {
                for (Method method : from.getClass().getMethods()) {
                    Class<?>[] takes = method.getParameterTypes();
                    if (takes.length != 1) continue;
                    if (!takes[0].isInstance(data)) continue;
                    if (!method.getReturnType().isEnum()) continue;
                    method.setAccessible(true);
                    reader = method;
                    status = method;
                    break;
                }
            }
            if (reader == null) {
                Diary.note("streaks: no way to read a status on this release");
                return null;
            }
            Object value = reader.invoke(from, data);
            return value == null ? null : ((Enum<?>) value).name();
        } catch (Throwable error) {
            Diary.note("streaks: status -- " + error);
            return null;
        }
    }

    private static volatile Method status;

    /**
     * Send to every conversation the mod knows about, whatever its state.
     *
     * A button rather than a schedule: the point is to find out whether
     * sending works at all, so it skips every condition -- not grey, already
     * sent today, none of it -- and writes down what happened to each one.
     */
    public static void test(final Context context) {
        currentAccount();if(Account.liveId()==null)return;
        FavouriteStickers.read();
        final boolean byText = BY_TEXT.equals(mode());
        final StickerItem sticker = byText ? null : sticker(chosen());
        final String owner=Account.liveId();
        final List<String> all = conversations();
        Diary.note("streak test: " + all.size() + " conversation(s) known, sending "
                + (byText ? "text"
                          : "a sticker " + (sticker == null ? "NOT chosen" : "chosen"))
                + ", service " + (service == null ? "not seen yet" : "seen")
                + ", network " + (StreakAlarm.online(context) ? "up" : "DOWN"));
        describe();
        if (all.isEmpty()) return;
        if (!byText && sticker == null) return;

        Net.away("streak test", new Runnable() {
            @Override
            public void run() {
                for (String conversation : all) {
                    if(!owner.equals(Account.liveId()))return;
                    String state = statusOf(ask(conversation));
                    boolean sent = deliver(context, sticker, conversation);
                    Diary.note("streak test: " + conversation + " (" + state + ") -> "
                            + (sent ? "request submitted, delivery unconfirmed"
                                    : "request failed"));
                }
            }
        });
    }

    private static final java.util.concurrent.atomic.AtomicBoolean allRunning =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Send the chosen sticker, now, to everyone there is a streak with.
     *
     * Unlike the test button this looks at what TikTok says about each
     * conversation and leaves out the ones without a streak (no streak, or
     * one that is already over). Lit and grey flames both get the sticker.
     * It does not wait for the schedule and does not need the switch on.
     */
    public static void sendAll(final Context context) {
        currentAccount();
        if (Account.liveId() == null) { Screen.say(Text.STREAK_ALL_NONE); return; }
        FavouriteStickers.read();
        final StickerItem sticker = BY_TEXT.equals(mode()) ? null : sticker(chosen());
        if (sticker == null) { Screen.say(Text.STREAK_ALL_NO_STICKER); return; }
        final List<String> all = conversations();
        if (all.isEmpty()) { Screen.say(Text.STREAK_ALL_NO_CHATS); return; }
        if (!allRunning.compareAndSet(false, true)) { Screen.say(Text.STREAK_ALL_BUSY); return; }
        final String owner = Account.liveId();
        Net.away("streak all", new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> targets = new ArrayList<String>();
                    for (String conversation : all) {
                        StreakData data = ask(conversation);
                        if (data == null) continue;
                        if (data.convId != null && !conversation.equals(data.convId)) continue;
                        String state = statusOf(data);
                        if ("ACTIVE".equals(state) || GREY.equals(state)) targets.add(conversation);
                    }
                    Diary.note("streak all: " + targets.size() + " streak(s) among " + all.size() + " conversation(s)");
                    if (targets.isEmpty()) { Screen.say(Text.STREAK_ALL_NONE); return; }
                    int sent = 0;
                    for (String conversation : targets) {
                        if (!owner.equals(Account.liveId())) break;
                        if (send(context, sticker, conversation)) {
                            remember(conversation);
                            sent++;
                            Diary.note("streak all: request submitted, delivery unconfirmed: " + conversation);
                        } else {
                            Diary.note("streak all: request failed: " + conversation);
                        }
                        try { Thread.sleep(600L); } catch (InterruptedException e) { break; }
                    }
                    Screen.say(String.format(java.util.Locale.getDefault(), Text.STREAK_ALL_DONE, sent, targets.size()));
                } catch (Throwable error) {
                    Diary.note("streak all: " + error);
                } finally {
                    allRunning.set(false);
                }
            }
        });
    }

    /** Whichever way was chosen. */
    private static boolean deliver(Context context, StickerItem sticker,
                                   String conversation) {
        if (BY_TEXT.equals(mode())) return sendText(context, conversation, text());
        return send(context, sticker, conversation);
    }

    /**
     * Sending words rather than a sticker. Not done, and not guessed at.
     *
     * This used to look for a method by its shape -- something taking the
     * conversation and the words -- and call whatever matched. That is a
     * dangerous way to find anything: the methods on a messenger service are
     * not all senders, plenty of them take two strings, and one of the ones it
     * reached reported an account. A blind call can do anything the app can
     * do, and "it had the right shape" is not a reason to let one run.
     *
     * So nothing is called until the right method is known the way the sticker
     * sender is known: TikTok's own call to that one is in the apk and could be
     * read. Nothing in the apk sends plain text under any name, and the service
     * that would arrives in a module downloaded at runtime -- so this waits
     * rather than experiments on somebody's account.
     */
    private static boolean sendText(Context context, String conversation, String words) {
        Diary.note("streak text: not available in this build");
        return false;
    }

    /** Every conversation worth asking about, newest first. */
    private static List<String> conversations() {
        synchronized (asked) {
            List<String> out = new ArrayList<String>(asked.keySet());
            java.util.Collections.reverse(out);
            return out;
        }
    }

    private static void persistConversations() {
        SharedPreferences p = prefs();
        if (p == null) return;
        org.json.JSONArray saved = new org.json.JSONArray();
        for (String id : asked.keySet()) saved.put(id);
        p.edit().putString(conversationKey(), saved.toString()).apply();
    }

    private static void restoreConversations() {
        SharedPreferences p = prefs();
        if (p == null) return;
        try {
            org.json.JSONArray saved = new org.json.JSONArray(
                    p.getString(conversationKey(), "[]"));
            synchronized (asked) {
                for (int i = 0; i < saved.length() && i < 200; i++) {
                    String id = saved.optString(i, "");
                    if (id.length() > 0) asked.put(id, 0L);
                }
            }
        } catch (Throwable error) {
            Diary.note("streaks: saved conversations unreadable: " + error);
        }
    }

    /**
     * Ask the service about one conversation.
     *
     * Ask for the current native state. A missing answer does not fall back
     * to a remembered grey flame: that could send after the streak was kept.
     */
    private static StreakData readStreak(IStreakService from,String conversation,boolean fresh){
        if(from==null)return null;
        try{Method m;try{m=from.getClass().getMethod("LJIJJLI",String.class,boolean.class);}catch(NoSuchMethodException e){m=from.getClass().getMethod("J",String.class,boolean.class);}m.setAccessible(true);Object data=m.invoke(from,conversation,fresh);return data instanceof StreakData?(StreakData)data:null;}catch(Throwable error){Diary.note("streak read: "+error);return null;}
    }
    private static StreakData ask(String conversation) {
        IStreakService from = service;
        if (from != null) {
            try {
                StreakData fresh = readStreak(from,conversation,true);
                if (fresh != null) {
                    synchronized (known) {
                        known.put(conversation, fresh);
                    }
                    return fresh;
                }
            } catch (Throwable ignored) {
            }
        }
        return null; // A stale grey flame must never trigger an automatic send.
    }

    private static long lastAttempt(String conversation) {
        SharedPreferences prefs = prefs();
        if (prefs == null || conversation == null) return 0;
        return prefs.getLong("streak_attempt_at_" + Account.liveId()+"_"+conversation,0);
    }

    private static String automaticKey(String conversation){return "streak_schedule_"+Account.liveId()+"_"+StreakSchedule.minute()+"_"+conversation;}
    private static long automaticLast(String conversation){if(StreakSchedule.minute()<0)return lastAttempt(conversation);SharedPreferences p=prefs();return p==null?0:p.getLong(automaticKey(conversation)+"_at",0);}
    private static int automaticAttempts(String conversation,long now){if(StreakSchedule.minute()<0)return attemptsToday(conversation,now);SharedPreferences p=prefs();if(p==null)return MAX_ATTEMPTS;if(!StreakSchedule.sameDay(now,automaticLast(conversation)))return 0;return p.getInt(automaticKey(conversation)+"_count",1);}
    private static void rememberAutomatic(String conversation){int minute=StreakSchedule.minute();if(minute<0){remember(conversation);return;}SharedPreferences p=prefs();if(p==null)return;long now=System.currentTimeMillis();int count=automaticAttempts(conversation,now)+1;String key=automaticKey(conversation);p.edit().putLong(key+"_at",now).putInt(key+"_count",count).apply();}

    /** How many times today a send was submitted to this conversation. */
    private static int attemptsToday(String conversation, long now) {
        SharedPreferences prefs = prefs();
        if (prefs == null || conversation == null) return MAX_ATTEMPTS;
        if (!StreakSchedule.sameDay(now, lastAttempt(conversation))) return 0;
        return Math.max(1, prefs.getInt("streak_attempts_" + Account.liveId()+"_"+conversation, 1));
    }

    private static void remember(String conversation) {
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            long now = System.currentTimeMillis();
            int made = attemptsToday(conversation, now) + 1;
            prefs.edit().putLong("streak_attempt_at_" + Account.liveId()+"_"+conversation, now)
                    .putInt("streak_attempts_" + Account.liveId()+"_"+conversation, made).apply();
        }
    }

    // ------------------------------------------------------- the sending

    /**
     * Send one sticker into one conversation.
     *
     * Every part of the call is worked out from the method it is calling: the
     * service comes from a real name, the method is the only one on it taking
     * thirteen arguments, and each argument's type is read from that method.
     * The enum it wants is recognised by a constant TikTok named itself, and
     * the options object by having a constructor that takes one thing.
     */
    public static boolean send(Context context, StickerItem sticker, String conversation) {
        if (Looper.myLooper() == Looper.getMainLooper())
            return sendOnMain(context, sticker, conversation);
        java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<Boolean>(
                () -> sendOnMain(context, sticker, conversation));
        if (!new Handler(Looper.getMainLooper()).post(task)) return false;
        try {
            return task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception error) {
            task.cancel(false);
            Diary.note("streak send: main-thread dispatch did not complete: " + error);
            return false;
        }
    }

    private static boolean sendOnMain(Context context, StickerItem sticker, String conversation) {
        try {
            Object service = messageService();
            if (service == null) {
                Diary.note("streak send: no sticker service on this release");
                return false;
            }

            Method sender = null;
            for (Method method : service.getClass().getMethods()) {
                Class<?>[] signature = method.getParameterTypes();
                if (signature.length == 13 && method.getReturnType() == void.class
                        && Context.class.isAssignableFrom(signature[0])
                        && sourceFor(signature[3]) != null
                        && signature[4].isInstance(sticker)
                        && signature[8] == String.class
                        && signature[12] == boolean.class) {
                    if (sender != null) {
                        Diary.note("streak send: ambiguous sender on this release");
                        return false;
                    }
                    sender = method;
                }
            }
            if (sender == null) {
                Diary.note("streak send: no method taking thirteen arguments on "
                        + service.getClass().getName());
                return false;
            }

            // TikTok's own call, which is in the apk even though the service
            // that answers it is not:
            //
            //   LIZJ(context.getApplicationContext(), null, null,
            //        AUTO_CONSECUTIVE_SA_STICKERS, sticker, new X(null),
            //        null, null, conversationId, null, null, null, false)
            //
            // Null for everything not named. The first version of this built
            // an object for every argument it had not been told about, which
            // meant handing made-up protocol messages to a sender that wanted
            // none -- and that is what the exception was.
            Class<?>[] types = sender.getParameterTypes();
            if (sourceFor(types[3]) == null) {
                Diary.note("streak send: source enum absent on this release");
                return false;
            }
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i].isPrimitive()) args[i] = blank(types[i]);
                else if (i == 0) args[i] = context.getApplicationContext();
                else if (i == 3) args[i] = sourceFor(types[3]);
                else if (i == 4) args[i] = sticker;
                else if (i == 5) args[i] = maybe(types[5]);
                else if (i == 8) args[i] = conversation;
                else args[i] = null;
            }

            sender.setAccessible(true);
            sender.invoke(service, args);
            Diary.note("streak send: " + sender.getName()
                    + " invoked; TikTok has not confirmed delivery for " + conversation);
            return true;
        } catch (Throwable error) {
            Throwable why = error instanceof java.lang.reflect.InvocationTargetException
                    && error.getCause() != null ? error.getCause() : error;
            Diary.note("streak send failed: " + why);
            StackTraceElement[] where = why.getStackTrace();
            if (where != null && where.length > 0) Diary.note("   at " + where[0]);
            return false;
        }
    }

    /**
     * Write down what the sticker service can do. Temporary.
     *
     * The sender is picked by shape -- thirteen arguments, returns nothing --
     * and if that is the wrong method, or the right one wants something the
     * mod is not giving it, the only way to tell is to look at what is there.
     */
    public static void describe() {
        Object service = messageService();
        if (service == null) {
            Diary.note("streak service: not found");
            return;
        }
        Diary.note("streak service: " + service.getClass().getName());
        int shown = 0;
        for (Method method : service.getClass().getMethods()) {
            Class<?> owner = method.getDeclaringClass();
            if (owner == Object.class) continue;
            StringBuilder line = new StringBuilder(method.getName()).append('(');
            Class<?>[] takes = method.getParameterTypes();
            for (int i = 0; i < takes.length; i++) {
                if (i > 0) line.append(", ");
                line.append(takes[i].getSimpleName());
            }
            line.append(") -> ").append(method.getReturnType().getSimpleName());
            Diary.note("   " + line);
            if (++shown > 40) break;
        }
    }

    /** IMStickerApi's own singleton, then the service it names. */
    private static Object messageService() {
        try {
            Class<?> api = Class.forName("com.ss.android.ugc.aweme.im.sticker.api.IMStickerApi");
            Object instance = null;
            for (Field field : api.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object holder = field.get(null);
                if (holder == null) continue;
                for (Method method : holder.getClass().getMethods()) {
                    if (method.getParameterTypes().length == 0
                            && api.isAssignableFrom(method.getReturnType())) {
                        method.setAccessible(true);
                        instance = method.invoke(holder);
                        break;
                    }
                }
                if (instance != null) break;
            }
            if (instance == null) return null;

            Method service = instance.getClass().getMethod("getImStickerMessageService");
            service.setAccessible(true);
            return service.invoke(instance);
        } catch (Throwable error) {
            Diary.note("streak service: " + error);
            return null;
        }
    }

    /** 47.2.41 exposes the source as 12qb interface, implemented by 13GH. */
    static Object sourceFor(Class<?> contract) {
        if(!contract.isEnum()&&!contract.isInterface())return null;Object source=constant(contract,SOURCE);if(source!=null)return source;
        try{Class<?> nativeSource=Class.forName("X.13GH");source=constant(nativeSource,SOURCE);return source!=null&&contract.isInstance(source)?source:null;}catch(Throwable ignored){return null;}
    }
    /** A named constant on an enum; never guess another sending source. */
    private static Object constant(Class<?> type, String name) {
        try {
            return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Something harmless of the right shape, for the arguments not being set. */
    private static Object blank(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return Integer.valueOf(0);
        if (type == long.class) return Long.valueOf(0);
        return Integer.valueOf(0);
    }

    /** An options object, when the argument is a class that takes one thing. */
    private static Object maybe(Class<?> type) {
        try {
            for (Constructor<?> made : type.getDeclaredConstructors()) {
                if (made.getParameterTypes().length != 1) continue;
                if (made.getParameterTypes()[0].isPrimitive()) continue;
                made.setAccessible(true);
                return made.newInstance(new Object[]{null});
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
