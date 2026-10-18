package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Fading static feed controls, including slideshow controls.
 *
 * The buttons down the right, the caption, the record, the scrubbing bar: they
 * sit in the same place for hours and an OLED panel remembers them. Turning
 * them down does not make them unusable -- a tap works on a view at a tenth of
 * its brightness exactly as it works at full -- and it is the whole of what
 * stops the wear.
 *
 * The feed header identifies the screen. A guarded traversal then handles
 * overlays wherever they sit in the hierarchy. Video surfaces, large image
 * widgets and their ancestor containers are never faded or translated.
 */
public final class Dim {
    static void reloadSettings(){on=null;how=-1;refresh();}

    private Dim() {}

    public static final String KEY_ON = "dim_on";
    public static final String KEY_HOW = "dim_how";

    /** Per cent of the way to invisible. A third is enough to be worth doing. */
    public static final int DEFAULT = 35;

    private static volatile Boolean on;
    private static volatile boolean paused;
    private static volatile int how = -1;

    public static boolean isEnabled() {
        return isConfigured() && !paused;
    }

    public static boolean isConfigured() {
        Boolean known = on;
        if (known != null) return known.booleanValue();
        SharedPreferences prefs = prefs();
        // Do not cache a guess before the preferences are available.
        if (prefs == null) return false;
        boolean value = prefs.getBoolean(KEY_ON, false);
        on = Boolean.valueOf(value);
        return value;
    }

    public static void setEnabled(boolean enabled) {
        on = Boolean.valueOf(enabled);
        paused = false;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_ON, enabled).apply();
        BurnEye.configurationChanged();
        refresh();
    }

    /** The eye changes opacity only; the saved master switch remains intact. */
    static void toggleOpacity() {
        if (!isConfigured()) return;
        paused = !paused;
        refresh();
    }

    public static int strength() {
        int known = how;
        if (known >= 0) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return DEFAULT; // not remembered: the saved value is read once the context exists
        int value = prefs.getInt(KEY_HOW, DEFAULT);
        how = Math.max(0, Math.min(90, value));
        return how;
    }

    public static void setStrength(int percent) {
        how = Math.max(0, Math.min(90, percent));
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY_HOW, how).apply();
        refresh();
    }

    /** Compatibility only; old backups cannot re-enable removed pixel shifting. */
    public static boolean isShiftEnabled() { return false; }
    public static int shiftDp(){return 0;}
    public static void setShiftDp(int ignored){}
    public static void setShiftEnabled(boolean ignored){}

    // -------------------------------------------------------------- the work

    /**
     * Fade whatever is over the video on this screen.
     *
     * Feed navigation identifies photo posts as well as video posts; the
     * traversal protects media views independently of their parent containers.
     */
    public static void apply(Activity activity) {
        apply(activity, false);
    }

    /** Entering a screen or rendering newly laid-out UI must never be throttled. */
    public static void applyNow(Activity activity) {
        apply(activity, true);
    }

    private static void apply(Activity activity, boolean immediate) {
        if (activity == null || activity.getClass().getName().startsWith("cat.narezany.margyt.")) return;
        long now = android.os.SystemClock.uptimeMillis();
        View root = activity.getWindow().getDecorView();
        View previousRoot = lastRoot == null ? null : lastRoot.get();
        if (!immediate && previousRoot == root && now - last < QUIET) return;
        last = now;
        lastRoot = new java.lang.ref.WeakReference<View>(root);
        long probe=Probe.start();
        try {
            viewportWidth=root.getWidth();viewportHeight=root.getHeight();
            // No hierarchy work for a background window.
            if(!activity.hasWindowFocus()){if(!isEnabled())restore();return;}
            if(!isConfigured()){retryEye=false;BurnEye.hide(root);restore();return;}
            if(ProfileLayout.visible(root)){retryEye=false;BurnEye.hide(root);restore();return;}
            inspection=passInspection;inspection.clear();commentSources.clear();commentZone=Integer.MAX_VALUE;
            int protection=protectedFlags(root,0,new int[]{10000});
            // A permanent comment composer below a search video is not an open sheet.
            // Protect that row locally; only a real comment panel blocks the screen.
            panels.put(root,(protection&(64|128))!=0);
            if((protection&(32|64))!=0){retryEye=false;BurnEye.hide(root);restore();return;}
            // Losing focus (Home, recents, notification shade) is not disabling.
            // Keep the last applied frame; focus gain repairs before drawing.
            if (!activity.hasWindowFocus()) {
                if (!isEnabled()) restore();
                return;
            }
            viewportWidth=root.getWidth();viewportHeight=root.getHeight();
            String activityName=activity.getClass().getName();
            boolean main=activityName.equals("com.ss.android.ugc.aweme.main.MainActivity");
            // The screen is told by the selected bottom tab, not by header words and heights: those misfire per video.
            int tabs=main?selectedTabs(root,0):0;
            boolean inboxTab=(tabs&TAB_INBOX)!=0,profileTab=(tabs&TAB_PROFILE)!=0;
            boolean feedTab=main&&!inboxTab&&!profileTab&&(tabs&TAB_FEED)!=0;
            boolean friendsScreen=feedTab&&(tabs&TAB_FRIENDS)!=0;
            boolean homeScreen=feedTab&&!friendsScreen;
            boolean header=BurnEye.hasFeedHeader(root)||feedTab;
            // The feed has its own search icon: on the Home/Friends tabs this is the feed, never a search page,
            // so the eye can only be the one that replaces LIVE (or sits under the Friends header), not the floating fallback.
            boolean search=!header&&hasSearchHeader(root,0);
            View video=search||!header?surface(root,0):null;
            boolean videoPage=video!=null&&video.getWidth()>=viewportWidth*(search?.5f:.3f)&&video.getHeight()>=viewportHeight*(search?.2f:.1f);
            boolean detail=activityName.equals("com.ss.android.ugc.aweme.detail.ui.DetailActivity");
            // Any screen that shows a player or large post images (video, photo, carousel, search, profile post, live-photo) carries the eye.
            boolean photoPage=video==null&&!header&&(protection&1)!=0;
            boolean searchVideo=!inboxTab&&!profileTab&&(videoPage||photoPage)&&(search||(!header&&(!main||detail||BurnEye.videoBackHeader(root))));
            if((protection&128)!=0 && !searchVideo){retryEye=false;BurnEye.hide(root);restore();return;}
            if(searchVideo)panels.put(root,(protection&64)!=0);
            // Profile is a settled verdict: the eye leaves at once. Both tabs "selected" is a screen mid-transition.
            boolean feed=main&&!search&&!inboxTab&&!profileTab&&(header||(tabs&TAB_FEED)!=0);
            if(!feed&&!searchVideo){retryEye=false;BurnEye.hide(root);restore();return;}
            retryEye=true;
            if(searchVideo)BurnEye.installVideo(root);
            else if(friendsScreen || (header && !homeScreen && BurnEye.friendsHeader(root)))BurnEye.installFriends(root);
            else if(header)BurnEye.installFeed(root);
            else BurnEye.hide(root);
            if (!isEnabled()) {restore();return;}
            wanted=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<View,Boolean>());
            float alpha=1f-strength()/100f;
            topBar(root,alpha);bottomBar(root,alpha);
            View content=root.findViewById(android.R.id.content);
            fadeChrome(content==null ? root : content,alpha,0);
        } catch (Throwable error) {
            Diary.note("dim: " + error);
        } finally {inspection=null;passInspection.clear();commentSources.clear();finishPass();
            Probe.stop(Probe.DIM_PASS,probe);
        }
    }

    /** Memoize subtree protection once per pass rather than rescanning each ancestor. */
    private static final java.util.IdentityHashMap<View,Integer> passInspection=new java.util.IdentityHashMap<>();
    private static java.util.Map<View,Integer> inspection;
    private static final java.util.Map<Class<?>,String> typeNames=new java.util.HashMap<>();
    private static String typeName(View view){Class<?> type=view.getClass();String name=typeNames.get(type);if(name==null){name=type.getName().toLowerCase(java.util.Locale.ROOT);typeNames.put(type,name);}return name;}
    private static final class PanelIdentity {int id;boolean blocked;PanelIdentity(int id,boolean blocked){this.id=id;this.blocked=blocked;}}
    private static final java.util.Map<View,PanelIdentity> panelIdentity=new java.util.WeakHashMap<>();
    private static final java.util.Map<View,java.lang.ref.WeakReference<View>> commentBoundaries=new java.util.WeakHashMap<>();
    private static final java.util.Map<View,View> commentSources=new java.util.IdentityHashMap<>();
    /** Keep only a weak anchor; recycled containers stop being boundaries when it leaves. */
    private static View commentBoundary(View view) {
        java.lang.ref.WeakReference<View> held=commentBoundaries.get(view);
        View anchor=held==null?null:held.get();
        if(anchor!=null && anchor.isAttachedToWindow() && belongsTo(anchor,view) && blockedPanel(anchor))return anchor;
        if(held!=null)commentBoundaries.remove(view);
        return null;
    }
    /** The comment input itself: an EditText whose hint speaks of comments. */
    private static boolean commentField(View view) {
        if(!(view instanceof android.widget.TextView))return false;
        CharSequence hint=((android.widget.TextView)view).getHint();
        boolean edit=view instanceof android.widget.EditText;
        // Before it is focused the input is often a plain text line ("Add comment...") rather than an EditText.
        CharSequence shown=hint!=null&&hint.length()>0?hint:(edit?null:((android.widget.TextView)view).getText());
        String text=shown==null?"":shown.toString().trim().toLowerCase(java.util.Locale.ROOT);
        if(!(text.contains("коммент") || text.contains("comment") || text.contains("коментар")))return false;
        if(edit)return true;
        return text.length()<=48 && (text.startsWith("добав") || text.startsWith("add") || text.startsWith("додат")
                || text.startsWith("остав") || text.startsWith("leave") || text.startsWith("напис") || text.startsWith("write")
                || text.startsWith("скаж") || text.startsWith("say"));
    }
    /**
     * Everything at or below the top of an open comments sheet (or one input-bar row above the field)
     * belongs to the comments UI: the emoji row, attach/mention/voice buttons and "send" stay bright
     * even when TikTok keeps them in a different container from the input itself.
     */
    private static volatile int commentZone=Integer.MAX_VALUE;
    private static void markZone(View view,boolean field){
        try{
            if(viewportHeight<=0)return;
            int[] at=new int[2];view.getLocationOnScreen(at);
            float dp=view.getResources().getDisplayMetrics().density;
            int top=field?at[1]-Math.round(72*dp):at[1];
            // An implausibly high top is a mis-measured view, not a comments sheet.
            if(top<viewportHeight*0.2f)return;
            if(top<commentZone)commentZone=top;
        }catch(Throwable ignored){}
    }
    private static boolean inCommentZone(View view){
        if(commentZone==Integer.MAX_VALUE)return false;
        int[] at=new int[2];view.getLocationOnScreen(at);
        return at[1]>=commentZone;
    }
    /**
     * A bounded sheet is wide and not the whole screen. A tall sheet qualifies on its own;
     * the bar that carries a comment input (emoji row, attach/mention/voice buttons, send)
     * may be much shorter, so it qualifies as soon as it holds the input field.
     */
    private static boolean boundedSheet(View view,int flags) {
        if(!(view instanceof ViewGroup) || viewportWidth<=0 || viewportHeight<=0
                || view.getWidth()<viewportWidth*0.65f || view.getHeight()>=viewportHeight*0.95f)return false;
        return view.getHeight()>=viewportHeight*0.2f || ((flags&128)!=0 && view.getHeight()>0);
    }
    /** Cheap exclusion shared by layout observers; no classification or resource access. */
    private static boolean excludedTree(View view) {
        if(Boolean.TRUE.equals(view.getTag(0x5454434D)) || commentBoundary(view)!=null)return true;
        PanelIdentity known=panelIdentity.get(view);
        return known!=null && known.id==view.getId() && known.blocked;
    }
    private static int protectedFlags(View view,int depth,int[] budget){
        if(view==null || !view.isShown())return 0;
        Integer saved=inspection.get(view);if(saved!=null)return saved;
        if(depth>40 || budget[0]--<=0)return 15;
        View anchor=commentBoundary(view);
        if(anchor!=null || blockedPanel(view)) {
            // A boundary is the sheet drawn round a panel that was found on an earlier pass, and what
            // kind of panel it was is a fact about the panel, not about the sheet: asked of the sheet
            // alone, a conversation stopped being one from the second pass on, and the eye came back.
            int flags=4|(chatInput(view)||(anchor!=null&&chatInput(anchor))?32:0)|(actualCommentPanel(view)?64:0)|(commentField(view)?128:0);commentSources.put(view,anchor==null?view:anchor);inspection.put(view,flags);
            if((flags&128)!=0)markZone(view,true);
            else if(anchor!=null || ((flags&64)!=0 && !Boolean.TRUE.equals(view.getTag(0x5454434D))))markZone(view,false);
            return flags;
        }
        int flags=(isMedia(view)?1:0)|(isLikeEffect(view)?2:0)
            |(view instanceof SurfaceView || view instanceof TextureView?8:0)|(faded.containsKey(view)?16:0);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);flags|=protectedFlags(child,depth+1,budget);
                if(anchor==null)anchor=commentSources.get(child);
            }
        }
        if(anchor!=null){commentSources.put(view,anchor);
            if(boundedSheet(view,flags) && (flags&8)==0){commentBoundaries.put(view,new java.lang.ref.WeakReference<View>(anchor));flags=4|(flags&240);markZone(view,(flags&128)!=0);}
        }
        inspection.put(view,flags);return flags;
    }
    private static java.util.Set<View> wanted;
    private static final java.util.Map<View,Boolean> panels=new java.util.WeakHashMap<View,Boolean>();
    static boolean panelOpen(View root){Boolean value=panels.get(root);return value!=null?value:commentsPanel(root,0);}
    /** Keep existing alpha between passes; restore only controls leaving the scope. */
    private static void finishPass() {
        if(wanted==null)return;
        java.util.Iterator<java.util.Map.Entry<View,Dimmed>> iterator=faded.entrySet().iterator();
        while(iterator.hasNext()) {
            java.util.Map.Entry<View,Dimmed> entry=iterator.next();View view=entry.getKey();
            if(view!=null && wanted.contains(view))continue;
            Dimmed state=entry.getValue();iterator.remove();
            if(view!=null && Math.abs(view.getAlpha()-state.applied)<0.001f)view.setAlpha(state.original);
        }
        wanted=null;
    }
    private static String words(View view) {
        CharSequence text=view instanceof android.widget.TextView ? ((android.widget.TextView)view).getText() : view.getContentDescription();
        return text==null ? "" : text.toString().trim().toLowerCase(java.util.Locale.ROOT);
    }
    private static boolean selected(View view) {
        for(int i=0;view!=null && i<3;i++) {
            if(view.isSelected() || view.isActivated())return true;
            Object parent=view.getParent();view=parent instanceof View ? (View)parent : null;
        }
        return false;
    }
    /** Following/community are outside the requested recommendations/friends scope. */
    private static boolean selectedExcludedHeader(View view,int depth) {
        if(view==null || !view.isShown() || depth>40 || excludedTree(view))return false;
        String label=selected(view)?words(view):"";
        if(label.equals("подписки") || label.equals("following") || label.equals("підписки")
                || label.equals("сообщество") || label.equals("community") || label.equals("explore"))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(selectedExcludedHeader(((ViewGroup)view).getChildAt(i),depth+1))return true;
        return false;
    }
    private static final int TAB_FEED=1,TAB_INBOX=2,TAB_PROFILE=4,TAB_FRIENDS=8;
    /** Which bottom-bar tabs are selected on screen, found in one walk: a bitmask of TAB_*. */
    private static int selectedTabs(View view,int depth) {
        if(view==null || !view.isShown() || depth>30 || excludedTree(view))return 0;
        int found=0;
        CharSequence raw=view instanceof android.widget.TextView ? ((android.widget.TextView)view).getText() : view.getContentDescription();
        if(raw!=null && raw.length()>0 && raw.length()<=10 && selected(view)){
            String label=raw.toString().trim().toLowerCase(java.util.Locale.ROOT);
            if(label.equals("главная") || label.equals("home") || label.equals("головна"))found|=TAB_FEED;
            else if(label.equals("друзья") || label.equals("friends") || label.equals("друзі"))found|=TAB_FEED|TAB_FRIENDS;
            else if(label.equals("входящие") || label.equals("inbox") || label.equals("вхідні"))found|=TAB_INBOX;
            else if(label.equals("профиль") || label.equals("profile") || label.equals("профіль"))found|=TAB_PROFILE;
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount() && found!=(TAB_FEED|TAB_INBOX|TAB_PROFILE);i++)found|=selectedTabs(group.getChildAt(i),depth+1);}
        return found;
    }
    private static boolean hasSearchHeader(View view,int depth) {
        if(view==null || !view.isShown() || depth>30 || excludedTree(view))return false;
        if(commentField(view) || chatInput(view))return false;
        int[] at=new int[2];view.getLocationOnScreen(at);
        String type=typeName(view);
        if(at[1]+view.getHeight()/2f<=viewportHeight*0.18f
                && (view instanceof android.widget.EditText || type.contains("searchbar") || type.contains("searchinput") || type.contains("searchbox")))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(hasSearchHeader(((ViewGroup)view).getChildAt(i),depth+1))return true;
        return false;
    }
    private static boolean actualCommentPanel(View view){
        if(Boolean.TRUE.equals(view.getTag(0x5454434D)))return true;
        String name=typeName(view);return name.equals("x.0h10") || name.contains("commentlist") || name.contains("commentpanel") || name.contains("commentsheet");
    }
    private static boolean commentsPanel(View view,int depth){
        if(view==null || !view.isShown() || depth>40)return false;
        if(actualCommentPanel(view))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(commentsPanel(group.getChildAt(i),depth+1))return true;}
        return false;
    }
    private static boolean chatInput(View view){
        if(view instanceof android.widget.EditText){CharSequence hint=((android.widget.EditText)view).getHint();String text=hint==null?"":hint.toString().trim().toLowerCase(java.util.Locale.ROOT);if(text.contains("коммент") || text.contains("comment"))return false;return text.startsWith("написать") || text.startsWith("сообщение") || text.startsWith("message");}
        // The box a message is typed into is a class with a real name, and that is what a conversation is
        // known by now. It used to be known by the hint in that box, which is "написать" in one language
        // only, and by class names TikTok no longer uses -- so the eye sat on top of the chat's title and
        // the dimming turned the sticker panel into an empty one.
        // And it has to be on the screen: a conversation that was left may still be in the tree for a while.
        String name=typeName(view);
        if(name.contains(CHAT))return view.isAttachedToWindow() && view.getGlobalVisibleRect(new android.graphics.Rect());
        return name.contains("messagelist") || name.contains("chatinput") || name.contains("chatroom");
    }
    /** Where the views of a conversation live, in lower case as type names are kept here. */
    private static final String CHAT = ".im.sdk.chat.";

    private static boolean blockedPanel(View view) {
        if(Boolean.TRUE.equals(view.getTag(0x5454434D))||Boolean.TRUE.equals(view.getTag(0x54544349)))return true;
        if(view instanceof android.widget.TextView) {
            CharSequence hint=((android.widget.TextView)view).getHint();
            String label=(hint==null?words(view):hint.toString()).toLowerCase(java.util.Locale.ROOT);
            if(label.contains("комментар") || label.contains("comment") || label.contains("коментар") || label.startsWith("написать") || label.startsWith("сообщение") || label.startsWith("message"))return true;
        }
        PanelIdentity known=panelIdentity.get(view);int resource=view.getId();
        if(known!=null && known.id==resource)return known.blocked;
        String type=typeName(view);
        boolean blocked=type.equals("x.0h10") || type.contains("commentlist") || type.contains("commentpanel")
                || type.contains("commentsheet") || type.contains("commentinput")
                || type.contains("commenttext") || type.contains("commentcomposer") || type.contains("chatroom")
                || type.contains("messagelist") || type.contains("chatinput") || type.contains(CHAT);
        if(!blocked && resource>0)try{
            String id=Names.of(view);
            blocked=id.contains("comment") && (id.contains("panel") || id.contains("list") || id.contains("input") || id.contains("sheet") || id.contains("edit"));
        }catch(Throwable ignored){}
        panelIdentity.put(view,new PanelIdentity(resource,blocked));return blocked;
    }
    static boolean panelContent(View view){
        int depth=0;while(view!=null && depth++<40){if(blockedPanel(view))return true;Object parent=view.getParent();view=parent instanceof View?(View)parent:null;}return false;
    }
    private static boolean hasBlockedPanel(View view,int depth) {
        if(inspection!=null)return (protectedFlags(view,depth,new int[]{10000})&4)!=0;
        if(view==null || !view.isShown())return false;
        if(excludedTree(view))return true;
        if(depth>40)return true;
        if(blockedPanel(view))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(hasBlockedPanel(((ViewGroup)view).getChildAt(i),depth+1))return true;
        return false;
    }

    private static volatile long last;
    /** Kept for the apply() verdict bookkeeping only; the periodic tick no longer depends on it. */
    private static volatile boolean retryEye=true;
    private static volatile java.lang.ref.WeakReference<View> lastRoot;
    private static volatile int viewportWidth;
    private static volatile int viewportHeight;

    /**
     * Runs on every layout, so it has a pause of its own -- short, because a
     * swipe builds a whole new cell and nothing of the old one carries over.
     */
    private static final long QUIET = 120;

    private static final java.util.Map<View, Boolean> ticking =
            new java.util.WeakHashMap<View, Boolean>();

    /** Keep the effect active on a still video, where no layout event occurs. */
    public static void watch(Activity activity) {
        if (activity == null || activity.getClass().getName().startsWith("cat.narezany.margyt.")) return;
        View root = activity.getWindow().getDecorView();
        applyNow(activity);
        synchronized (ticking) {
            if (ticking.containsKey(root)) return;
            ticking.put(root, Boolean.TRUE);
        }
        final java.lang.ref.WeakReference<Activity> held =
                new java.lang.ref.WeakReference<Activity>(activity);
        // Coalesce layout events within a frame, then apply before that frame
        // can display a newly recycled post at its original brightness.
        final boolean[] dirty = {false};
        final int[] shape={layoutFingerprint(root,0)};
        final long[] printed={0L};
        final int[] misses={0};
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                dirty[0] = true;
            }
        });
        root.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if(!isConfigured()){dirty[0]=false;return true;}
                // A video lays something out on nearly every frame, and reading
                // the whole screen over each time was a millisecond or two of
                // every one of them. Twenty times a second is still sooner
                // than the eye catches a new video's buttons at full strength.
                long at=android.os.SystemClock.uptimeMillis();
                if (dirty[0] && at-printed[0]>=48L && root.hasWindowFocus()) {
                    Activity current = held.get();printed[0]=at;
                    dirty[0]=false;long probe=Probe.start();int next=layoutFingerprint(root,0);Probe.stop(Probe.DIM_PRINT,probe);
                    if(current!=null && root.isAttachedToWindow() && next!=shape[0]){shape[0]=next;applyNow(current);}
                }
                long probe=Probe.start();
                reconcile();
                Probe.stop(Probe.DIM_FRAME,probe);
                // Live-photo transitions may request another layout every frame.
                // Never suppress their draw while waiting for that future layout.
                return true;
            }
        });
        root.getViewTreeObserver().addOnWindowFocusChangeListener(
                new android.view.ViewTreeObserver.OnWindowFocusChangeListener() {
            @Override public void onWindowFocusChanged(boolean focused) {
                dirty[0] = true;
                Activity current = held.get();
                if (current != null && root.isAttachedToWindow()) applyNow(current);
            }
        });
        root.postDelayed(new Runnable() {
            @Override public void run() {
                Activity current = held.get();
                if (current == null || !root.isAttachedToWindow()) {
                    synchronized (ticking) { ticking.remove(root); }
                    return;
                }
                long delay=TICK;
                if(isConfigured() && current.hasWindowFocus()){
                    int next=layoutFingerprint(root,0);
                    boolean lost;
                    if(next!=shape[0]){shape[0]=next;applyNow(current);lost=eyeShouldBeBack(root);}
                    // A screen transition moves views without changing their size, so the fingerprint stays
                    // equal while the verdict may have been taken mid-slide (or while the profile was still
                    // closing). If the feed header / inbox is on screen and the eye is not, look again --
                    // whatever the last verdict was -- instead of staying off until the app is restarted.
                    else if(eyeShouldBeBack(root)){applyNow(current);lost=eyeShouldBeBack(root);}
                    else if(misses[0]<RECHECK_LIMIT && mediaEyeMissing(root)){applyNow(current);lost=mediaEyeMissing(root);}
                    else lost=false;
                    // Still no eye on a screen that must have one: keep looking, quickly, for a few seconds.
                    if(lost && misses[0]<RECHECK_LIMIT){misses[0]++;delay=RECHECK;}
                    else if(!lost)misses[0]=0;
                }
                // Also catches visibility/focus changes that have no layout.
                root.postDelayed(this, delay);
            }
        }, TICK);
    }

    // Primitive layout signature: no resource reads, text conversions or ancestor walks.
    static int layoutFingerprint(View view,int depth){
        if(view==null || depth>40)return 0;
        int hash=System.identityHashCode(view)*31+view.getWidth()*17+view.getHeight();
        hash=31*hash+view.getVisibility()+(view.isSelected()?8:0)+(view.isActivated()?16:0);
        hash=31*hash+(int)view.getTranslationX();hash=31*hash+(int)view.getTranslationY();
        hash=31*hash+(view.getAlpha()<=0.01f?1:0);
        if(excludedTree(view))return hash;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;hash=31*hash+group.getChildCount();
            for(int i=0;i<group.getChildCount();i++)hash=31*hash+layoutFingerprint(group.getChildAt(i),depth+1);}
        return hash;
    }
    private static final long TICK = 1000L;
    /** While the eye is missing from a screen that must have it, look again this often... */
    private static final long RECHECK = 300L;
    /** ...for this many looks in a row (about six seconds), then fall back to the ordinary tick. */
    private static final int RECHECK_LIMIT = 20;

    /**
     * True when this screen is the feed (or the inbox) -- not a profile -- yet carries no eye.
     * Independent of every earlier verdict: the profile verdict used to switch the retry off for good,
     * so coming back from a profile left the eye gone and anti-burn off until the app was restarted.
     */
    /** A screen with a player or large post image but no eye: look again shortly. */
    private static boolean mediaEyeMissing(View root){
        if(!BurnEye.missing(root) || ProfileLayout.visible(root) || Boolean.TRUE.equals(panels.get(root)))return false;
        int tabs=selectedTabs(root,0);
        if((tabs&(TAB_PROFILE|TAB_INBOX))!=0 || BurnEye.hasFeedHeader(root))return false;
        return surface(root,0)!=null;
    }
    private static boolean eyeShouldBeBack(View root){
        if(!BurnEye.missing(root) || ProfileLayout.visible(root))return false;
        int tabs=selectedTabs(root,0);
        if((tabs&(TAB_PROFILE|TAB_INBOX))!=0 || Boolean.TRUE.equals(panels.get(root)) || hasSearchHeader(root,0))return false;
        return BurnEye.hasFeedHeader(root);
    }

    /**
     * The video that is actually being watched.
     *
     * Not the first one in the tree, which is what this used to take: a feed
     * keeps the cells on either side of the one you are looking at, so the
     * first surface it found was as often the video above as the one on
     * screen -- and the cell it dimmed was that one. The one being watched is
     * the one covering the middle of the screen.
     */
    private static View surface(View root, int unused) {
        java.util.List<View> all = new java.util.ArrayList<View>();
        gather(root, all, 0, new int[] {10000});
        if (all.isEmpty()) return null;

        int[] rootOnScreen = new int[2];
        root.getLocationOnScreen(rootOnScreen);
        int middleX = rootOnScreen[0] + root.getWidth() / 2;
        int middleY = rootOnScreen[1] + root.getHeight() / 2;
        android.graphics.Rect where = new android.graphics.Rect();
        for (View one : all) {
            if (!one.getGlobalVisibleRect(where)) continue;
            if (where.contains(middleX, middleY)) return one;
        }
        // A partially visible neighbour in the recycler is not the video the
        // person is watching. With no player under the centre, leave the UI
        // alone rather than dimming a photo or the next feed cell.
        return null;
    }

    private static void gather(View view, java.util.List<View> out, int depth, int[] budget) {
        if (view == null || !view.isShown() || depth > 40 || budget[0]-- <= 0 || excludedTree(view)) return;
        if (view instanceof SurfaceView || view instanceof TextureView) {
            out.add(view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            gather(group.getChildAt(i), out, depth + 1, budget);
        }
    }

    /**
     * Everything in this video's own cell except the video.
     *
     * The cell is found by walking up from the video until the list the videos
     * are in -- that list is the edge, and above it are the other videos and
     * the screen itself, which are not ours. Inside the cell, anything that is
     * not the video and does not contain it is over the video by definition:
     * the buttons, the caption, the record, the scrubbing bar.
     *
     * This replaced "the siblings drawn after the video", which reached
     * whatever happened to be listed after it and missed whatever was not.
     */
    private static void over(View video, View root, float alpha) {
        View cell = video;
        java.util.List<View> spine = new java.util.ArrayList<View>();
        spine.add(cell);
        while (true) {
            Object parent = cell.getParent();
            if (!(parent instanceof ViewGroup)) break;
            ViewGroup group = (ViewGroup) parent;
            if (scrolls(group)) break;
            cell = group;
            spine.add(cell);
        }
        // Select the larger navigation overlays first. Descendants already
        // covered by one of these must not receive another alpha or offset.
        topBar(root, alpha);
        bottomBar(root, alpha);
        if (cell == video) return;
        inside(cell, spine, alpha, 0);
        // The feed cell contains the controls drawn over the clip.  The two
        // navigation rails live outside it, so they need their own, strictly
        // geometric lookup.  Neither lookup accepts a subtree containing a
        // player, which keeps the moving picture at its original brightness.
    }

    private static void inside(View view, java.util.List<View> spine,
                               float alpha, int depth) {
        if (view == null || depth > 12) return;
        if (!spine.contains(view)) {
            fadeChrome(view, alpha, depth);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            inside(group.getChildAt(i), spine, alpha, depth + 1);
        }
    }

    /** Whether this is the thing the videos are listed in. */
    private static boolean scrolls(View view) {
        String name = view.getClass().getName();
        return name.contains("RecyclerView") || name.contains("ViewPager")
                || name.contains("ListView");
    }

    /**
     * The rows at the top and bottom: tabs/search, and Home/Friends/Inbox.
     *
     * Looked for once and then remembered. TikTok builds it in a class called
     * `HomepageToolBar`, which is a real name and a dead end -- it is not a
     * view, it is a factory, and what it fills is an ordinary FrameLayout. So
     * it is found by its shape instead, and searching a whole screen for that
     * shape on every layout is what was making the app slow to start.
     */
    private static void topBar(View root, float alpha) {
        chrome(root, alpha, true);
    }

    private static void bottomBar(View root, float alpha) {
        chrome(root, alpha, false);
    }

    /**
     * Finds one navigation rail by its position and shape, not TikTok class
     * names.  The UI is recreated on each feed change, so the cached view is
     * weak and the search is retried after a small pause.
     */
    private static void chrome(View root, float alpha, boolean top) {
        java.lang.ref.WeakReference<View> remembered = top ? topRail : bottomRail;
        View known = remembered == null ? null : remembered.get();
        if (known != null && known.isAttachedToWindow() && known.isShown() && belongsTo(known, root)
                && (!top || BurnEye.isFeedRail(known, 0))) {
            if (top) fadeChrome(known, alpha, 0); else fadeBottom(known, alpha);
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        long lastLook = top ? lastTopSearch : lastBottomSearch;
        if (known==null && remembered==null && now - lastLook < SEARCH_AGAIN) return;
        if (top) lastTopSearch = now;
        else lastBottomSearch = now;
        try {
            View content = root.findViewById(android.R.id.content);
            ViewGroup group = content instanceof ViewGroup
                    ? (ViewGroup) content
                    : (root instanceof ViewGroup ? (ViewGroup) root : null);
            if (group == null) return;
            int tall = root.getHeight();
            int wide = root.getWidth();
            if (tall <= 0 || wide <= 0) {
                return;
            }
            int[] rootOnScreen = new int[2];
            root.getLocationOnScreen(rootOnScreen);
            View found = lookRail(group, rootOnScreen[1], tall, wide, top, 0);
            if (found != null) {
                if (top) topRail = new java.lang.ref.WeakReference<View>(found);
                else bottomRail = new java.lang.ref.WeakReference<View>(found);
                if (top) fadeChrome(found, alpha, 0); else fadeBottom(found, alpha);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fadeBottom(View rail, float alpha) {
        if (!(rail instanceof ViewGroup)) return;
        BottomChrome.protect(rail);
        // The whole rail's alpha would expose the preloaded next page beneath it.
        View ancestor=rail;
        for(int depth=0;depth<40&&ancestor!=null;depth++){
            Dimmed previous=faded.remove(ancestor);
            if(previous!=null&&Math.abs(ancestor.getAlpha()-previous.applied)<0.001f)ancestor.setAlpha(previous.original);
            Object parent=ancestor.getParent();ancestor=parent instanceof View?(View)parent:null;
        }
        ViewGroup group = (ViewGroup) rail;
        for (int i = 0; i < group.getChildCount(); i++)
            fadeChrome(group.getChildAt(i), alpha, 0);
    }

    private static volatile java.lang.ref.WeakReference<View> topRail;
    private static volatile java.lang.ref.WeakReference<View> bottomRail;
    private static volatile long lastTopSearch;
    private static volatile long lastBottomSearch;
    private static final long SEARCH_AGAIN = 800;

    /** Navigation is deliberately small, leaving the actual video untouched. */
    private static final float RAIL_HEIGHT = 0.18f;
    
    private static View lookRail(ViewGroup group, int rootTop, int tall, int wide,
                                 boolean top, int depth) {
        if (depth > 20) return null;
        int[] where = new int[2];
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;

            int height = child.getHeight();
            child.getLocationOnScreen(where);
            boolean thin = height > 0 && height < tall * RAIL_HEIGHT;
            boolean full = child.getWidth() > wide * 0.85f;
            boolean atEdge = top
                    ? where[1] >= rootTop && where[1] + height / 2f <= rootTop + tall * 0.14f
                    : where[1] + height / 2f >= rootTop + tall * 0.93f
                        && where[1] + height >= rootTop + tall * 0.96f;

            // Do not dim a broad-but-short video container (or a miniature
            // preloaded player).  This is the last guard before setAlpha.
            if (thin && full && atEdge && child instanceof ViewGroup
                    && ((ViewGroup) child).getChildCount() >= 2
                    && (!top || BurnEye.isFeedRail(child, 0))
                    && !hasPlayer(child, 0) && !hasMedia(child, 0, new int[] {10000})) return child;
            if (child instanceof ViewGroup) {
                View found = lookRail((ViewGroup) child, rootTop, tall, wide,
                        top, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasPlayer(View view, int depth) {
        return hasPlayer(view, depth, new int[] {10000});
    }

    private static boolean hasPlayer(View view, int depth, int[] budget) {
        if(inspection!=null)return (protectedFlags(view,depth,budget)&8)!=0;
        if (view == null) return false;
        // If inspection would take too long, leave this subtree untouched.
        if (depth > 40 || budget[0]-- <= 0) return true;
        if (view instanceof SurfaceView || view instanceof TextureView) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasPlayer(group.getChildAt(i), depth + 1, budget)) return true;
        }
        return false;
    }

    /** Large image widgets are post media, small images remain UI icons. */
    private static boolean isMedia(View view) {
        if (view instanceof SurfaceView || view instanceof TextureView) return true;
        String name = typeName(view);
        if (name.contains("surfaceview") || name.contains("textureview")
                || name.contains("rendersurface") || name.contains("playerhost")
                || name.contains("webview")) return true;
        boolean image = view instanceof ImageView || (!(view instanceof ViewGroup)
                && (name.contains("drawee") || name.contains("photoview")
                    || name.contains("zoomimage")));
        if (!image || viewportWidth <= 0 || viewportHeight <= 0) return false;
        float w = view.getWidth(), h = view.getHeight();
        // Wide landscape strips and narrow portrait photos both count. A
        // fixed minimum height would incorrectly fade very wide photos.
        return w >= viewportWidth * 0.45f || h >= viewportHeight * 0.25f
                || (h >= viewportHeight * 0.08f
                    && w * h >= (float) viewportWidth * viewportHeight * 0.035f);
    }

    private static boolean hasMedia(View view, int depth, int[] budget) {
        if(inspection!=null)return (protectedFlags(view,depth,budget)&1)!=0;
        if (view == null) return false;
        if (depth > 40 || budget[0]-- <= 0) return true;
        if (isMedia(view)) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            if (hasMedia(group.getChildAt(i), depth + 1, budget)) return true;
        return false;
    }

    /** Do not fade a broad media host while its photo is still loading. */
    private static boolean mediaSizedContainer(View view) {
        return view instanceof ViewGroup && viewportWidth > 0 && viewportHeight > 0
                && view.getWidth() >= viewportWidth * 0.45f
                && view.getHeight() >= viewportHeight * 0.12f;
    }

    /** Apply the effect to visible UI overlays, never to the video surface. */
    private static boolean isLikeEffect(View view) {
        String type=typeName(view);
        if (type.equals("x.0hcj") || type.contains("digganimation") || type.contains("likeanimation")) return true;
        return Boolean.TRUE.equals(view.getTag(0x54544C48));
    }
    private static boolean hasLikeEffect(View view,int depth) {
        if(inspection!=null)return (protectedFlags(view,depth,new int[]{10000})&2)!=0;
        if (view==null) return false;
        if (depth>40) return true;
        if (isLikeEffect(view)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(hasLikeEffect(group.getChildAt(i),depth+1))return true;
        }
        return false;
    }

    private static void fadeChrome(View view, float alpha, int depth) {
        if(BurnEye.owns(view))return;
        if (view != null && BottomChrome.isRail(view)) {
            if (view.isShown()) fadeBottom(view, alpha);
            return;
        }
        if (view == null || depth > 40 || !view.isShown() || covered(view)) return;
        if (isLikeEffect(view) || excludedTree(view) || blockedPanel(view)) return;
        if (isMedia(view)) return;
        if (inCommentZone(view)) return;
        // A whole UI overlay is one composited view. Fading that parent covers
        // TikTok's custom-drawn icons and touch controls as well as its labels;
        // restricting this to TextView used to dim only the words.
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            // A comments sheet may have obfuscated container classes. Its
            // marked rows/input identify the whole bounded sheet, including
            // unmarked author names, menus and reaction icons around them.
            if(hasBlockedPanel(group,0) && boundedSheet(group,inspection!=null?protectedFlags(group,depth,new int[]{10000}):0)
                    && !hasMedia(group,0,new int[]{10000}))return;
            if (!hasMedia(view, 0, new int[] {10000})
                    && !mediaSizedContainer(view) && !hasFadedDescendant(view, 0)
                    && !hasLikeEffect(view,0) && !hasBlockedPanel(view,0) && !BottomChrome.containsRail(view)) {
                fade(view, alpha);
            } else {
                for (int i = 0; i < group.getChildCount(); i++)
                    fadeChrome(group.getChildAt(i), alpha, depth + 1);
            }
            return;
        }
        fade(view, alpha);
    }

    /** Host property animations bypass the patched setAlpha call. Repair
     * tracked controls before drawing, without traversing the entire feed. */
    private static void reconcile() {
        BottomChrome.repair();
        if(faded.isEmpty())return;
        long now=android.os.SystemClock.uptimeMillis();float factor=isEnabled()?1f-strength()/100f:1f;
        java.util.Iterator<java.util.Map.Entry<View, Dimmed>> iterator = faded.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<View, Dimmed> entry = iterator.next();
            View view = entry.getKey();
            Dimmed state = entry.getValue();
            if (view == null || state == null) continue;
            // A recycled UI subtree may become a photo before the delayed
            // layout update. Remove our effect before the next frame.
            int children=view instanceof ViewGroup?((ViewGroup)view).getChildCount():0;int shape=childShape(view);
            boolean inspect=view.getWidth()!=state.width || view.getHeight()!=state.height || children!=state.children || shape!=state.shape
                    || view.isLayoutRequested() || now-state.checked>=1000L || Boolean.TRUE.equals(view.getTag(0x54544C48));
            if(inspect){state.width=view.getWidth();state.height=view.getHeight();state.children=children;state.shape=shape;state.checked=now;}
            if (BottomChrome.containsRail(view) || (inspect && (hasMedia(view, 0, new int[] {10000}) || hasLikeEffect(view,0) || hasBlockedPanel(view,0)))) {
                iterator.remove();
                if (Math.abs(view.getAlpha() - state.applied) < 0.001f)
                    view.setAlpha(state.original);
                continue;
            }
            float current = view.getAlpha();
            if (Math.abs(current - state.applied) > 0.001f) {
                state.original = current;
                state.applied = current * factor;
                view.setAlpha(state.applied);
            }
        }
    }

    private static void fade(View view, float alpha) {
        try {
            if (view == null || covered(view) || hasLikeEffect(view,0) || hasBlockedPanel(view,0)) return;
            float current=view.getAlpha();
            Returning returning=returns.remove(view);
            float restoredOriginal=Float.NaN;
            if(returning!=null){if(Math.abs(current-returning.last)<.001f)restoredOriginal=returning.original;returning.animator.cancel();}
            Dimmed previous=faded.get(view);
            float original=!Float.isNaN(restoredOriginal)?restoredOriginal:previous!=null && Math.abs(current-previous.applied)<0.001f ? previous.original : current;
            float applied = original * alpha;
            if(wanted!=null)wanted.add(view);
            if(previous==null)faded.put(view,new Dimmed(view,original,applied));
            else {previous.original=original;previous.applied=applied;}
            if (Math.abs(current - applied) > 0.001f) view.setAlpha(applied);
            flat(view, applied);
        } catch (Throwable ignored) {
        }
    }

    /** Views already told to be drawn flat, so each is told once. */
    private static final java.util.Map<View,Boolean> flattened=new java.util.WeakHashMap<>();

    /**
     * A dimmed view drawn straight onto the screen, each thing in it faint by
     * itself, in place of being drawn full into a picture of its own that is
     * then laid on faintly.
     *
     * The second is what Android does for a group that is not fully there,
     * so that things lying over one another inside it do not show through
     * each other. It is also a picture the size of the group made again for
     * every frame, and with the whole of TikTok's overlay dimmed that was
     * most of the screen, a hundred and twenty times a second: reports from
     * phones with the dimming on had their slow frames spent drawing, where
     * every other report had them spent waiting. Icons and words over a
     * video do not lie over one another enough for the difference to be seen.
     */
    private static void flat(View view, float applied) {
        if (android.os.Build.VERSION.SDK_INT < 24 || applied >= 0.999f || flattened.containsKey(view)) return;
        flattened.put(view, Boolean.TRUE);
        view.forceHasOverlappingRendering(false);
    }

    /** A subtree gets exactly one composite alpha. */
    private static boolean covered(View view) {
        View current = view;
        while (current != null) {
            if (wanted==null ? faded.containsKey(current) : wanted.contains(current)) return true;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static boolean hasFadedDescendant(View view, int depth) {
        if (!(view instanceof ViewGroup)) return false;
        if (depth > 40) return true;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if ((wanted==null ? faded.containsKey(child) : wanted.contains(child))
                    || (inspection!=null ? (protectedFlags(child,depth+1,new int[]{10000})&16)!=0 : hasFadedDescendant(child, depth + 1))) return true;
        }
        return false;
    }

    private static boolean belongsTo(View view, View root) {
        View current = view;
        while (current != null) {
            if (current == root) return true;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    /** One more look at the current screen, e.g. right after the eye left on a doubtful verdict. */
    static void retry(){
        Activity activity=Screen.now();
        if(activity!=null)applyNow(activity);
    }

    /** Apply setting changes immediately, even on a paused video. */
    private static void refresh() {
        if(!isEnabled())restore();
        last = 0;
        Activity activity = Screen.now();
        if (activity != null) applyNow(activity);
    }

    /**
     * How bright a view should end up when TikTok asks for a brightness.
     *
     * Multiplied rather than replaced: TikTok fades its overlay away when a
     * panel opens and back when it closes, and answering with a fixed number
     * would either keep a hidden overlay visible or leave it hidden. Half of
     * TikTok's own is half of ours.
     */
    public static float alphaFor(View view, float asked) {
        // The maps below are the main thread's. TikTok sets a brightness from
        // other threads now and then, on views not yet on screen, and those
        // are none of this function's business.
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return asked;
        try {
            Returning returning=returns.remove(view);if(returning!=null)returning.animator.cancel();
            Dimmed dimmed = faded.get(view);
            if (!isEnabled() || dimmed == null) return asked;
            dimmed.original = asked;
            dimmed.applied = asked * (1f - strength() / 100f);
            return dimmed.applied;
        } catch (Throwable ignored) {
            return asked;
        }
    }

    private static int childShape(View view){
        int hash=0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);hash=31*hash+child.getWidth()*17+child.getHeight();}}return hash;
    }
    /** The value TikTok had and the value the anti-burn pass applied. */
    private static final class Dimmed {
        float original;
        float applied;

        int width,height,children,shape;long checked;
        Dimmed(View view,float original, float applied) {
            width=view.getWidth();height=view.getHeight();children=view instanceof ViewGroup?((ViewGroup)view).getChildCount():0;checked=android.os.SystemClock.uptimeMillis();shape=childShape(view);
            this.original = original;
            this.applied = applied;
        }
    }

    private static final java.util.Map<View, Dimmed> faded =
            new java.util.WeakHashMap<View, Dimmed>();

    /**
     * Put back what the mod turned down, and only that.
     *
     * A view is restored only if it is still wearing the brightness the mod
     * gave it: if TikTok has since faded it away itself -- a panel opening, a
     * video pausing -- then that is TikTok's business and setting it back to
     * full would put a hidden overlay on screen.
     */
    private static void restore() {
        BottomChrome.restore();
        try {
            java.util.Map<View, Dimmed> originals =
                    new java.util.HashMap<View, Dimmed>(faded);
            faded.clear();
            for (java.util.Map.Entry<View, Dimmed> entry : originals.entrySet()) {
                View view = entry.getKey();
                if (view == null) continue;
                Dimmed dimmed = entry.getValue();
                if (dimmed != null
                        && Math.abs(view.getAlpha() - dimmed.applied) < 0.001f) {
                    restoreSmooth(view,dimmed.original);
                }
                faded.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private static final java.util.Map<View,Returning> returns=new java.util.WeakHashMap<>();
    private static final class Returning {
        final float original;float last;final android.animation.ValueAnimator animator;
        Returning(float from,float to){original=to;last=from;animator=android.animation.ValueAnimator.ofFloat(from,to);}
    }
    /** Animate only our alpha. Stop immediately if TikTok changes or detaches the view. */
    private static void restoreSmooth(View view,float original){
        if(!view.isAttachedToWindow() || !view.isShown()){view.setAlpha(original);return;}
        Returning state=new Returning(view.getAlpha(),original);returns.put(view,state);
        java.lang.ref.WeakReference<View> ref=new java.lang.ref.WeakReference<>(view);
        state.animator.setDuration(160L);
        state.animator.addUpdateListener(animation->{View target=ref.get();
            if(target==null || returns.get(target)!=state || Math.abs(target.getAlpha()-state.last)>.001f){animation.cancel();return;}
            if(!target.isAttachedToWindow() || !target.isShown()){target.setAlpha(state.original);animation.cancel();return;}
            state.last=(Float)animation.getAnimatedValue();target.setAlpha(state.last);
        });
        state.animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){View target=ref.get();if(target!=null && returns.get(target)==state)returns.remove(target);}});
        state.animator.start();
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }
}
