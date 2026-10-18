package cat.narezany.margyt;
import android.view.View;
import java.util.Map;
import java.util.WeakHashMap;
/** Native right-area slots only. No frame callbacks, tree walks or removed views. */
public final class FeedRail {
    public static final String[] KEYS={"rail_show_like","rail_show_comment","rail_show_fav","rail_show_share"};
    private static final Map<View,State> slots=new WeakHashMap<View,State>();
    private static volatile boolean[] shown;
    private static volatile boolean zenMode;
    private static final class State {final int kind;int wanted;State(int k,int v){kind=k;wanted=v;}}
    private FeedRail(){}
    public static boolean shown(int kind){
        if(zenMode&&kind<3)return false;
        boolean[] current=shown;
        if(current==null){
            android.content.Context c=Margy.context();if(c==null)return true;
            current=new boolean[]{true,true,true,true};
            try{android.content.SharedPreferences p=AccountAppearance.prefs(c);for(int i=0;i<4;i++)current[i]=p.getBoolean(KEYS[i],true);}
            catch(Throwable error){Diary.note("feed rail settings: "+error);}
            shown=current;
        }
        return kind<0 || kind>=current.length || current[kind];
    }
    static void zenMode(boolean on){
        zenMode=on;
        reloadSettings();
    }
    public static void setShown(int kind,boolean value){
        if(kind<0 || kind>=4)return;
        android.content.Context c=Margy.context();if(c==null)return;
        AccountAppearance.prefs(c).edit().putBoolean(KEYS[kind],value).apply();reloadSettings();
    }
    static void reloadSettings(){
        shown=null;
        java.util.List<View> views;
        synchronized(slots){views=new java.util.ArrayList<View>(slots.keySet());}
        for(View v:views)if(v!=null)v.post(()->apply(v));
    }
    public static void bindNative(Object component){
        if(component==null)return;
        String name=component.getClass().getName();int kind;
        if(name.equals("com.ss.android.ugc.aweme.feed.assem.digg.VideoDiggAssem"))kind=0;
        else if(name.equals("com.ss.android.ugc.aweme.feed.assem.videocomment.VideoCommentAssem"))kind=1;
        else if(name.equals("com.ss.android.ugc.aweme.feed.favorite.VideoFavoriteAssem"))kind=2;
        else if(name.equals("com.ss.android.ugc.aweme.feed.assem.share.VideoShareAssem"))kind=3;
        else return;
        try{
            Object content=component.getClass().getMethod("getContentView").invoke(component);
            if(!(content instanceof View))return;
            View v=(View)content;
            synchronized(slots){if(!slots.containsKey(v))slots.put(v,new State(kind,v.getVisibility()));}
            v.setTag(KNOWN,Boolean.TRUE);
            apply(v);
            if(kind==3)VideoActions.bindNative(component);
            if(kind==2)Repost.anchor(component);
        }catch(Throwable error){Diary.note("feed rail binding: "+error);}
    }
    /** Preserve TikTok's latest intended state, including server-hidden actions. */
    /** On the few views the mod has anything to say about. */
    private static final int KNOWN=0x7e0c0399;
    public static void setVisibility(View view,int visibility){
        if(view==null)return;
        // Every show and hide in TikTok comes through here, thousands a second
        // while a list is filled, and all but a handful are none of the mod's
        // business. Those go straight on: two tags asked for, where there used
        // to be two locks taken and two tables searched for each.
        if(view.getTag(KNOWN)==null&&view.getTag(Repost.TAG)==null){view.setVisibility(visibility);return;}
        long probe=Probe.start();
        boolean taken=Repost.visibility(view,visibility);
        State state=null;
        if(!taken)synchronized(slots){state=slots.get(view);if(state!=null)state.wanted=visibility;}
        Probe.stop(Probe.RAIL,probe);
        if(taken)return;
        view.setVisibility(state!=null&&state.kind==2&&Repost.replacing(view)?View.INVISIBLE:state!=null&&!shown(state.kind)?View.GONE:visibility);
    }
    static void refreshFavorite(View view){if(view!=null)apply(view);}
    private static void apply(View view){
        State state;synchronized(slots){state=slots.get(view);}
        if(state==null)return;
        int next=state.kind==2&&Repost.replacing(view)?View.INVISIBLE:shown(state.kind)?state.wanted:View.GONE;
        if(view.getVisibility()!=next)view.setVisibility(next);
        Md3.rail(view,state.kind);
    }
    /** The icons of the buttons on screen, looked at before each frame: a tap changes one without anything being laid out. */
    private static View[] seen=new View[0];private static int[] kinds=new int[0];private static long listed;
    private static long idled;
    static void icons(){
        long now=android.os.SystemClock.uptimeMillis();
        // with the round icons off there is nothing to swap; it is looked at
        // twice a second all the same, so that turning them off puts TikTok's back
        if(!Md3.isOn(Md3.KEY_ICONS)){if(now-idled<500L)return;idled=now;}
        if(now-listed>500L){
            listed=now;
            synchronized(slots){
                java.util.List<View> views=new java.util.ArrayList<View>();java.util.List<Integer> which=new java.util.ArrayList<Integer>();
                for(Map.Entry<View,State> entry:slots.entrySet()){View v=entry.getKey();if(v!=null&&v.isAttachedToWindow()){views.add(v);which.add(entry.getValue().kind);}}
                seen=views.toArray(new View[0]);kinds=new int[which.size()];for(int i=0;i<kinds.length;i++)kinds[i]=which.get(i);
            }
        }
        View[] views=seen;int[] which=kinds;
        for(int i=0;i<views.length&&i<which.length;i++)Md3.icons(views[i],which[i]);
    }
    /** Every button the mod has been handed, for what dresses them to look at again. */
    static void restyle(){
        java.util.List<View> views;
        synchronized(slots){views=new java.util.ArrayList<View>(slots.keySet());}
        for(View v:views)if(v!=null&&v.isAttachedToWindow()){State state;synchronized(slots){state=slots.get(v);}if(state!=null)Md3.rail(v,state.kind);}
    }
}
