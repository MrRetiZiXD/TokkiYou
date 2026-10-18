package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;

/** Publication date in captions, scoped to native video renderers for 47.2.41. */
public final class Dates {
    static void reloadSettings(){on=null;DateOverlay.update();}

    private Dates() {}

    public static final String KEY = "always_date";

    private static volatile Boolean on;
    private static String selectedAid;
    private static String selectedDate;
    /** Full publication data of the post that is on screen, for the date sheet. */
    static volatile DateInfo.Data selectedInfo;
    private static final java.util.LinkedHashMap<String,java.lang.ref.WeakReference<android.view.View>> authorRows=new java.util.LinkedHashMap<>();
    /** Bind the actual author/date slot, including cells where description and author are siblings. */
    public static void bindAuthor(Object holder){
        Object params=NativeRead.item(holder);Object post=NativeRead.get(params,"getAweme");String aid=NativeRead.aid(post);Object content=NativeRead.get(holder,"getContentView");
        if(aid.isEmpty() || !(content instanceof android.view.View))return;
        android.view.View view=(android.view.View)content;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            view.setTag(0x7e0d0334,aid);view.setTag(DateInfo.CELL_TAG,DateInfo.Data.of(post));authorRows.put(aid,new java.lang.ref.WeakReference<>(view));while(authorRows.size()>32)authorRows.remove(authorRows.keySet().iterator().next());
            String publication=label(NativeRead.number(NativeRead.get(post,"getCreateTime")),System.currentTimeMillis()/1000L);
            DateOverlay.register(view,publication);
            if(aid.equals(selectedAid) && view.isShown())DateOverlay.bind(view,publication);
        });
    }

    public static boolean isEnabled() {
        Boolean known = on;
        if (known != null) return known.booleanValue();
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean value = prefs.getBoolean(KEY, false);
        on = Boolean.valueOf(value);
        return value;
    }

    public static void setEnabled(boolean enabled) {
        on = Boolean.valueOf(enabled);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->DateOverlay.update());
    }

    /** Scoped by the patcher to caption renderers, not arbitrary model reads. */
    public static String getDesc(Object post) {
        if (post==null) return null;
        String original=null;
        try {
            Object value=post.getClass().getMethod("getDesc").invoke(post);
            original=value instanceof String ? (String)value : null;
            // The date has its own row next to the author, never inside a caption.
            return original;
        } catch (Throwable error) {Diary.note("date caption: "+error);return original;}
    }
    /** Called only when TikTok selects a visible feed cell, never during prefetch. */
    public static void bindPost(Object holder,Object post) {
        VideoActions.selected(holder,post);
        try {
            final android.view.View view=(android.view.View)holder.getClass().getMethod("getContentView").invoke(holder);
            long time=post==null?0:((Number)post.getClass().getMethod("getCreateTime").invoke(post)).longValue();
            final String date=label(time,System.currentTimeMillis()/1000L);
            String name=null;try{Object user=post.getClass().getMethod("getAuthor").invoke(post);if(user!=null)name=(String)user.getClass().getMethod("getNickname").invoke(user);}catch(Throwable ignored){}
            final String author=name;
            final String aid=NativeRead.aid(post);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                selectedAid=aid;selectedDate=date;selectedInfo=DateInfo.Data.of(post);java.lang.ref.WeakReference<android.view.View> saved=authorRows.get(aid);android.view.View row=saved==null?null:saved.get();
                DateOverlay.bind(row!=null && aid.equals(row.getTag(0x7e0d0334)) && row.isAttachedToWindow() && row.isShown()?row:view,date,author);
            });
        }catch(Throwable error){Diary.note("visible date: "+error);}
    }

    static String label(long seconds,long now) {
        if (seconds>100000000000L) seconds/=1000L;
        if (seconds<1000000000L || seconds>now+86400L) return null;
        return new java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault())
                .format(new java.util.Date(seconds*1000L));
    }

    private static SharedPreferences prefs() {
        Context context=Margy.context();
        return context==null ? null : context.getSharedPreferences(Margy.PREFS,Context.MODE_PRIVATE);
    }
}
