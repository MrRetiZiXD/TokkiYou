package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Places a compact opacity toggle directly below the native LIVE entrance. */
final class BurnEye {
    private BurnEye() {}
    /** The eye is the only way back from dimming: it may fade with its neighbours, never to invisibility. */
    static final float MIN_ALPHA = 0.6f;
    /** How long a doubtful "this is not the feed" verdict may stand before the eye really leaves. */
    static final long LEAVE_DELAY_MS = 900L;
    private static final java.util.Map<View,java.lang.ref.WeakReference<Slot>> slots=new java.util.WeakHashMap<View,java.lang.ref.WeakReference<Slot>>();
    private static final java.util.Map<View,java.lang.ref.WeakReference<View>> anchors=new java.util.WeakHashMap<View,java.lang.ref.WeakReference<View>>();
    private static final java.util.Map<View,Boolean> replacedAnchors=new java.util.WeakHashMap<>();
    /** How long a "this is (not) the LIVE entrance" verdict is trusted between frames. */
    private static final long VERDICT_NS=250000000L;
    /** The Inbox title sits in the header; the eye goes in the free gap between it and the search icon. */
    static final float INBOX_X=0.72f;
    /** Centre of the eye on video, search and profile-post pages, as a share of the screen width / height. */
    static final float VIDEO_X=0.218f,VIDEO_Y=0.0718f;
    private static final java.util.Map<Class<?>,Boolean> transientTypes=new java.util.HashMap<Class<?>,Boolean>();

    /** Separate overlay: LIVE keeps its view, id, layout and native click handler. */
    static View install(View rail) {
        if (!(rail instanceof ViewGroup) || !isFeedRail(rail, 0)) return null;
        return installFeed(rail);
    }
    static View installFeed(View rail){
        if(!(rail instanceof ViewGroup))return null;
        View root = rail.getRootView();
        if (!(root instanceof FrameLayout)) return null;
        java.lang.ref.WeakReference<Slot> cached=slots.get(root);Slot slot=cached==null?null:cached.get();
        if(slot!=null && slot.getParent()!=root)slot=null;
        if (!Dim.isConfigured()) { hide(root); return null; }
        java.lang.ref.WeakReference<View> held=anchors.get(root);View entrance=held==null?null:held.get();
        if(entrance==null || !entrance.isAttachedToWindow() || !namedLive(entrance) || transientControl(entrance)) {
            entrance=entrance(rail,rail,0);

        }
        if (entrance == null) { hide(root); return null; }
        if (slot == null) {
            slot = new Slot(entrance.getContext());
            ((FrameLayout) root).addView(slot, new FrameLayout.LayoutParams(1, 1));
            slots.put(root,new java.lang.ref.WeakReference<Slot>(slot));
        }
        anchors.put(root,new java.lang.ref.WeakReference<View>(entrance));
        slot.replaceAnchor(entrance);slot.videoFallback=false;slot.inbox=false;slot.friends=false;
        slot.stay();slot.active=true;slot.watch();slot.sync();slot.update();
        return slot;
    }

    /** Inbox has no LIVE entrance and no feed tabs: the eye is bound to the screen's own title instead. */
    static View installInbox(View root){
        if(!(root instanceof FrameLayout))return null;
        if(!Dim.isConfigured()){hide(root);return null;}
        java.lang.ref.WeakReference<View> held=anchors.get(root);View title=held==null?null:held.get();
        if(title==null || !title.isAttachedToWindow() || !title.isShown() || !inboxTitle(title,root)){
            title=findInboxTitle(root,root,0);
            if(title==null){hide(root);return null;}
        }
        anchors.put(root,new java.lang.ref.WeakReference<View>(title));
        java.lang.ref.WeakReference<Slot> cached=slots.get(root);Slot slot=cached==null?null:cached.get();
        if(slot==null||slot.getParent()!=root){slot=new Slot(root.getContext());((FrameLayout)root).addView(slot,new FrameLayout.LayoutParams(1,1));slots.put(root,new java.lang.ref.WeakReference<Slot>(slot));}
        slot.inbox=true;slot.videoFallback=false;slot.anchor=new java.lang.ref.WeakReference<View>(title);
        slot.stay();slot.active=true;slot.watch();slot.sync();slot.update();return slot;
    }
    private static boolean inboxWord(CharSequence label){
        if(label==null || label.length()==0 || label.length()>12)return false;
        String text=label.toString().trim().toLowerCase(java.util.Locale.ROOT);
        return text.equals("входящие") || text.equals("inbox") || text.equals("вхідні");
    }
    private static boolean inboxTitle(View view,View root){
        if(!(view instanceof TextView) && view.getContentDescription()==null)return false;
        if(!inboxWord(view instanceof TextView?((TextView)view).getText():view.getContentDescription()))return false;
        int[] at=new int[2],origin=new int[2];view.getLocationOnScreen(at);root.getLocationOnScreen(origin);
        // The bottom bar carries the same word; only the header copy counts.
        return at[1]+view.getHeight()/2f<=origin[1]+root.getHeight()*0.18f;
    }
    private static View findInboxTitle(View view,View root,int depth){
        if(view==null || view instanceof Slot || depth>40 || !view.isShown())return null;
        if(inboxTitle(view,root))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){View found=findInboxTitle(group.getChildAt(i),root,depth+1);if(found!=null)return found;}}
        return null;
    }

    static View installVideo(View root){
        if(!(root instanceof FrameLayout)||!Dim.isConfigured())return null;
        java.lang.ref.WeakReference<Slot> cached=slots.get(root);Slot slot=cached==null?null:cached.get();
        if(slot==null||slot.getParent()!=root){slot=new Slot(root.getContext());((FrameLayout)root).addView(slot,new FrameLayout.LayoutParams(1,1));slots.put(root,new java.lang.ref.WeakReference<Slot>(slot));}
        slot.restoreAnchor();slot.videoFallback=true;slot.inbox=false;slot.friends=false;slot.anchor=new java.lang.ref.WeakReference<View>(null);slot.placement=new java.lang.ref.WeakReference<>(findHeader(root,root,false,0,new int[]{512}));slot.stay();slot.active=true;slot.watch();slot.sync();slot.update();return slot;
    }

    static boolean friendsHeader(View root){return findHeader(root,root,true,0,new int[]{512})!=null;}
    static boolean videoBackHeader(View root){return findHeader(root,root,false,0,new int[]{512})!=null;}
    static View installFriends(View root){
        View eye=installVideo(root);if(!(eye instanceof Slot))return eye;
        Slot slot=(Slot)eye;slot.friends=true;slot.placement=new java.lang.ref.WeakReference<>(findHeader(root,root,true,0,new int[]{512}));slot.sync();return slot;
    }
    private static View findHeader(View view,View root,boolean friends,int depth,int[] budget){
        if(view==null || view instanceof Slot || !view.isShown() || depth>24 || budget[0]--<=0)return null;
        CharSequence words=view instanceof TextView?((TextView)view).getText():view.getContentDescription();
        if(words!=null && words.length()<48){
            String label=words.toString().trim().toLowerCase(java.util.Locale.ROOT);
            boolean match=friends?(label.equals("друзья") || label.equals("friends") || label.equals("друзі")):(label.equals("назад") || label.equals("back") || label.equals("go back") || label.equals("повернутися"));
            if(match){int[] at=new int[2],start=new int[2];view.getLocationOnScreen(at);root.getLocationOnScreen(start);
                if(at[1]+view.getHeight()/2f<start[1]+root.getHeight()*.18f)return view;}
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findHeader(group.getChildAt(i),root,friends,depth+1,budget);if(found!=null)return found;}}
        return null;
    }

    static boolean owns(View view){return view instanceof Slot || replacedAnchors.containsKey(view);}
    /** The eye itself, as opposed to the button of TikTok's it stands in for. */
    static boolean eye(View view){return view instanceof Slot;}

    /** True when this screen has no visible eye (never installed, or it left). */
    static boolean missing(View root){
        java.lang.ref.WeakReference<Slot> held=slots.get(root);Slot slot=held==null?null:held.get();
        return slot==null || !slot.active || slot.getVisibility()!=View.VISIBLE;
    }

    static void hide(View root) {
        java.lang.ref.WeakReference<Slot> held=slots.get(root);Slot slot=held==null?null:held.get();
        if (slot != null) slot.fadeOut();
    }

    /**
     * For verdicts that flicker while the feed scrolls or a screen is mid-transition: the eye
     * stays for a moment and leaves only if nobody installs it again in the meantime.
     */
    static void hideSoon(View root) {
        java.lang.ref.WeakReference<Slot> held=slots.get(root);Slot slot=held==null?null:held.get();
        if (slot != null && slot.active) slot.leaveLater();
    }

    static void configurationChanged() {
        for (java.lang.ref.WeakReference<Slot> ref : slots.values()) {
            Slot slot = ref.get();
            if (slot == null) continue;
            if(!Dim.isConfigured()){slot.fadeOut();continue;}
            slot.sync();
            slot.update();
        }
    }

    /** A feed header has named tabs; never replace a profile's back button. */
    static boolean isFeedRail(View view, int depth) {
        if (view == null || !view.isShown() || depth > 40) return false;
        CharSequence words = view instanceof TextView ? ((TextView) view).getText()
                : view.getContentDescription();
        if (words != null && words.length() <= 14) {
            String text = words.toString().trim().toLowerCase(java.util.Locale.ROOT);
            if (text.equals("друзья") || text.equals("friends") || text.equals("друзі") || text.equals("рекомендации") || text.equals("для вас")
                    || text.equals("подписки") || text.equals("for you")
                    || text.equals("following") || text.equals("рекомендації")
                    || text.equals("підписки") || text.equals("для тебе")) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (isFeedRail(group.getChildAt(i), depth + 1)) return true;
        }
        return false;
    }

    /** Tabs may occupy only part of the width in a split header. */
    static boolean hasFeedHeader(View root) {
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        return headerWords(root, origin[1], root.getHeight(), 0);
    }

    private static boolean headerWords(View view, int top, int height, int depth) {
        if (view == null || !view.isShown() || depth > 40) return false;
        if (view instanceof TextView) {
            int[] point = new int[2];
            view.getLocationOnScreen(point);
            if (point[1] >= top && point[1] + view.getHeight() / 2f <= top + height * 0.18f
                    && isFeedRail(view, 0)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (headerWords(group.getChildAt(i), top, height, depth + 1)) return true;
        }
        return false;
    }

    // HomepageToolBar (TikTok 47.2.41) uses a keyed tag in its new
    // implementation and an ordinary tag in its legacy implementation.
    private static String toolbarTag(View view) {
        Object value=view.getTag(0x7f0a374f);
        if(!(value instanceof String))value=view.getTag();
        return value instanceof String?((String)value).toLowerCase(java.util.Locale.ROOT):"";
    }
    private static boolean transientType(View view){
        Class<?> type=view.getClass();Boolean known=transientTypes.get(type);
        if(known==null){
            String name=type.getName().toLowerCase(java.util.Locale.ROOT);
            known=Boolean.valueOf(name.contains("toast") || name.contains("notification") || name.contains("banner"));
            transientTypes.put(type,known);
        }
        return known.booleanValue();
    }
    private static boolean transientControl(View view) {
        for(int i=0;view!=null && i<40;i++) {
            String tag=toolbarTag(view);
            if(tag.equals("dm_notice") || tag.equals("notification"))return true;
            if(transientType(view))return true;
            CharSequence label=view.getContentDescription();
            if(label!=null && label.length()>0){
                String text=label.toString().toLowerCase(java.util.Locale.ROOT);
                if(text.contains("notification") || text.contains("уведомлен") || text.contains("уведомл"))return true;
            }
            Object parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return false;
    }
    private static boolean namedLive(View view){
        if(toolbarTag(view).equals("live"))return true;
        CharSequence label=view instanceof TextView?((TextView)view).getText():view.getContentDescription();
        if(label==null)return false;
        String text=label.toString().trim().toLowerCase(java.util.Locale.ROOT);
        return text.equals("live") || text.contains("live entrance") || text.contains("прямые эфиры");
    }
    private static View entrance(View view, View rail, int depth) {
        if (view instanceof Slot || depth > 40 || !view.isShown() || transientControl(view)) return null;
        int[] point = new int[2];
        int[] origin = new int[2];
        view.getLocationOnScreen(point);
        rail.getLocationOnScreen(origin);
        float dp = view.getResources().getDisplayMetrics().density;
        boolean compact = view.getWidth() >= 8 * dp && view.getWidth() <= 96 * dp
                && view.getHeight() >= 8 * dp && view.getHeight() <= 96 * dp;
        // Prefer a named LIVE control; never anchor to a transient friends banner.
        boolean left = point[0] >= origin[0] - dp
                && point[0] + view.getWidth() <= origin[0] + rail.getWidth() * 0.18f;
        boolean upper = point[1] >= origin[1] - dp
                && point[1] + view.getHeight() / 2f <= origin[1] + rail.getHeight() * 0.18f;
        // On a short rail the control naturally sits in the middle of the row.
        if (rail.getHeight() <= 100 * dp) upper = true;
        boolean live=namedLive(view);
        if (view != rail && compact && left && upper && live) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = entrance(group.getChildAt(i), rail, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** The two eyes the person drew: assets/margyt/burn_eye_*.png, read once and kept (a few kB each). */
    private static android.graphics.Bitmap openArt,closedArt;
    private static boolean artTried;
    private static synchronized void loadArt(Context context){
        if(artTried)return;
        artTried=true;
        openArt=readArt(context,"margyt/burn_eye_open.png");
        closedArt=readArt(context,"margyt/burn_eye_closed.png");
    }
    private static android.graphics.Bitmap readArt(Context context,String name){
        try(java.io.InputStream in=context.getAssets().open(name)){return android.graphics.BitmapFactory.decodeStream(in);}
        catch(Throwable error){Diary.note("burn eye art: "+error);return null;}
    }

    private static final class Slot extends View {
        private java.lang.ref.WeakReference<View> anchor=new java.lang.ref.WeakReference<>(null);
        private java.lang.ref.WeakReference<android.view.ViewTreeObserver> watched=new java.lang.ref.WeakReference<>(null);
        private final int[] at=new int[2],origin=new int[2];
        private boolean active,videoFallback,inbox,friends,leaving;
        private java.lang.ref.WeakReference<View> placement=new java.lang.ref.WeakReference<>(null);
        private android.animation.ValueAnimator transition;
        void fadeOut(){
            if(leaving)return;
            removeCallbacks(leave);active=false;restoreAnchor();
            if(getVisibility()!=View.VISIBLE){setVisibility(View.GONE);return;}
            leaving=true;setClickable(false);transition=android.animation.ValueAnimator.ofFloat(getAlpha(),0f);transition.setDuration(160L);
            transition.addUpdateListener(a->setAlpha((Float)a.getAnimatedValue()));
            transition.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(leaving){leaving=false;setVisibility(View.GONE);}transition=null;}});transition.start();
        }
        private float nativeAlpha=1f;
        private boolean nativeClickable,replaced;
        private int nativeAccessibility;
        void replaceAnchor(View next){
            if(anchor.get()==next && replaced)return;
            restoreAnchor();anchor=new java.lang.ref.WeakReference<View>(next);
            nativeAlpha=next.getAlpha();nativeClickable=next.isClickable();
            nativeAccessibility=next.getImportantForAccessibility();replaced=true;replacedAnchors.put(next,true);revet();
        }
        void restoreAnchor(){
            View view=anchor.get();
            if(replaced && view!=null){view.setAlpha(nativeAlpha);view.setClickable(nativeClickable);view.setImportantForAccessibility(nativeAccessibility);}
            if(view!=null)replacedAnchors.remove(view);replaced=false;
        }
        private int lastLeft=-1,lastTop=-1;
        // Verdict "this really is the LIVE entrance" is not re-derived on every frame.
        private java.lang.ref.WeakReference<View> vettedFor=new java.lang.ref.WeakReference<>(null);
        private long vettedAt;private boolean vettedOk;
        // After leaving, look at the screen once more: a verdict taken mid-transition must not stick.
        private final Runnable leave=()->{active=false;restoreAnchor();setVisibility(View.GONE);postDelayed(Dim::retry,50L);};
        private final Paint ink=new Paint(3); // anti-alias + bitmap filtering
        private final android.graphics.Rect dst=new android.graphics.Rect();
        private android.graphics.drawable.Drawable closedEye,openEye; // vector fallback if the PNGs cannot be read
        void stay(){removeCallbacks(leave);if(leaving){leaving=false;if(transition!=null)transition.cancel();transition=null;}setClickable(true);}
        void leaveLater(){removeCallbacks(leave);postDelayed(leave,LEAVE_DELAY_MS);}
        void revet(){vettedFor=new java.lang.ref.WeakReference<>(null);}
        private boolean vetted(View live){
            long now=System.nanoTime();
            if(vettedFor.get()!=live || now-vettedAt>VERDICT_NS){
                if(vettedFor.get()!=live)vettedFor=new java.lang.ref.WeakReference<>(live);
                vettedAt=now;vettedOk=namedLive(live) && !transientControl(live);
            }
            return vettedOk;
        }
        private final android.view.ViewTreeObserver.OnPreDrawListener visibility=()->{sync();return true;};
        void watch(){
            android.view.ViewTreeObserver next=getRootView().getViewTreeObserver(),old=watched.get();
            if(next==old)return;
            if(old!=null && old.isAlive())old.removeOnPreDrawListener(visibility);
            if(next.isAlive()){next.addOnPreDrawListener(visibility);watched=new java.lang.ref.WeakReference<>(next);}
        }
        private static float dimmed(){return Dim.isEnabled()?Math.max(1f-Dim.strength()/100f,MIN_ALPHA):1f;}
        /** Mirror the screen's own anchor before the same frame is drawn. No tree search. */
        void sync(){
            if(leaving)return;
            View root=getRootView();boolean show=false;float alpha=1f;
            View anchored=anchor.get();
            if(!active || !Dim.isConfigured()){
                // nothing to show
            }else if(inbox){
                show=anchored!=null && anchored.isAttachedToWindow() && anchored.isShown() && anchored.getRootView()==root
                        && root.getWindowVisibility()==View.VISIBLE;
                alpha=dimmed();
            }else{
                boolean present=anchored!=null && anchored.isAttachedToWindow() && anchored.getRootView()==root && vetted(anchored);
                if(anchored!=null)videoFallback=false;
                if(videoFallback){
                    show=root.isShown() && root.isAttachedToWindow() && root.getWindowVisibility()==View.VISIBLE;
                    alpha=dimmed();
                }else{
                    show=present && root.isShown() && root.getWindowVisibility()==View.VISIBLE;
                    if(show){
                        for(View view=anchored;view!=null && view!=root;){alpha*=view==anchored && replaced?nativeAlpha:view.getAlpha();Object parent=view.getParent();view=parent instanceof View?(View)parent:null;}
                        show=alpha>0f;
                        alpha=Math.max(alpha,MIN_ALPHA);
                    }
                }
            }
            int next=show?View.VISIBLE:View.GONE;
            if(show){
                root.getLocationOnScreen(origin);
                float dp=getResources().getDisplayMetrics().density;
                int size=Math.round((inbox?36:44)*dp);
                int left,top;
                if(inbox){
                    anchored.getLocationOnScreen(at);
                    left=Math.round(root.getWidth()*INBOX_X-size/2f);
                    top=Math.round(at[1]-origin[1]+anchored.getHeight()/2f-size/2f);
                }else if(videoFallback){
                    View reference=placement.get();
                    // Video / search / profile-post pages: one fixed spot (right of the back arrow), as a share of the screen,
                    // independent of density, status-bar height and of whether the back arrow could be found.
                    left=Math.round(root.getWidth()*VIDEO_X-size/2f);top=Math.round(root.getHeight()*VIDEO_Y-size/2f);
                    // Same row as the back arrow and the search icon: when the arrow is found, its centre line wins over the fixed share.
                    if(!friends && reference!=null && reference.isShown()){reference.getLocationOnScreen(at);top=Math.round(at[1]-origin[1]+reference.getHeight()/2f-size/2f);}
                    if(friends){
                        left=Math.round(4*dp);
                        int status=getResources().getIdentifier("status_bar_height","dimen","android");
                        int inset=status==0?Math.round(24*dp):getResources().getDimensionPixelSize(status);
                        top=inset+Math.round(64*dp);
                        if(reference!=null && reference.isShown()){reference.getLocationOnScreen(at);top=at[1]-origin[1]+reference.getHeight()+Math.round(6*dp);}
                    }
                    left=Math.max(0,Math.min(left,root.getWidth()-size));top=Math.max(0,Math.min(top,root.getHeight()-size));
                }else{
                    anchored.getLocationOnScreen(at);
                    left=Math.round(at[0]-origin[0]+anchored.getWidth()/2f-size/2f);top=Math.round(at[1]-origin[1]+anchored.getHeight()/2f-size/2f);
                    lastLeft=left;lastTop=top;
                }
                FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)getLayoutParams();
                if(params.width!=size || params.height!=size || params.leftMargin!=left || params.topMargin!=top){
                    params.width=size;params.height=size;params.leftMargin=left;params.topMargin=top;params.gravity=android.view.Gravity.TOP|android.view.Gravity.LEFT;setLayoutParams(params);
                }
                if(replaced && anchored!=null){
                    if(anchored.getAlpha()!=0f)anchored.setAlpha(0f);
                    if(anchored.isClickable())anchored.setClickable(false);
                    if(anchored.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)anchored.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                }
                if(Math.abs(getAlpha()-alpha)>.001f)setAlpha(alpha);
            }
            if(getVisibility()!=next)setVisibility(next);
        }

        Slot(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setOnClickListener(v -> {
                Dim.toggleOpacity();
                update();
            });
        }

        void update() {
            String label=Dim.isEnabled() ? Text.DIM_EYE_ON : Text.DIM_EYE_OFF;
            if(!label.contentEquals(getContentDescription()==null?"":getContentDescription())){
                setContentDescription(label);
            }
            invalidate(); // the picture follows the state even when the label is unchanged
        }

        /** The open / closed eye PNGs, drawn as they are: no disc, no tint behind them. */
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float dp = getResources().getDisplayMetrics().density;
            float box = Math.min(getWidth(), getHeight());
            if (box <= 0) return;
            int cx = getWidth() / 2, cy = getHeight() / 2;
            int half = Math.round(Math.min(26 * dp, box * 0.62f) / 2f);
            boolean dimming = Dim.isEnabled();
            loadArt(getContext());
            android.graphics.Bitmap art = dimming ? closedArt : openArt;
            if (art != null) {
                dst.set(cx - half, cy - half, cx + half, cy + half);
                canvas.drawBitmap(art, null, dst, ink);
                return;
            }
            if (closedEye == null) closedEye = SettingsGlyph.make("eye_closed", Color.WHITE);
            if (openEye == null) openEye = SettingsGlyph.make("eye_open", Color.WHITE);
            android.graphics.drawable.Drawable glyph = dimming ? closedEye : openEye;
            if (glyph == null) return;
            glyph.setBounds(cx - half, cy - half, cx + half, cy + half);
            glyph.draw(canvas);
        }
    }
}
