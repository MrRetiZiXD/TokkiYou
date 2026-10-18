package cat.narezany.tokkiyou;
/** Only the native double-tap heart host is routed here. */
public final class Heart {
    private Heart(){}
    private static final java.util.Map<android.view.View,Integer> views=new java.util.WeakHashMap<android.view.View,Integer>();
    private static void tint(android.view.View view) {
        int colour=LikeColors.colour();
        synchronized(views){Integer known=views.get(view);if(known!=null&&known.intValue()==colour)return;views.put(view,colour);}
        // A view-level filter also covers bitmap frames whose drawable filter is reset by the host.
        if(view instanceof android.widget.ImageView)((android.widget.ImageView)view).setColorFilter(colour,android.graphics.PorterDuff.Mode.SRC_IN);
    }
    static void refresh() {
        java.util.List<android.view.View> all;
        synchronized(views){all=new java.util.ArrayList<android.view.View>(views.keySet());}
        for(android.view.View view:all)if(view!=null && view.isAttachedToWindow())view.post(()->{
            if(view instanceof com.bytedance.tux.icon.TuxIconView)((com.bytedance.tux.icon.TuxIconView)view).setTintColor(LikeColors.colour());
            tint(view);
        });
    }
    public static void setIconRes(com.bytedance.tux.icon.TuxIconView view,int resource) {
        if(view==null)return;
        view.setTag(0x54544C48,Boolean.TRUE);
        view.setIconRes(resource);
        view.setTintColor(LikeColors.colour());
        tint(view);
    }
    public static void setTintColor(com.bytedance.tux.icon.TuxIconView view,int ignored) {
        if(view==null)return;
        view.setTag(0x54544C48,Boolean.TRUE);
        view.setTintColor(LikeColors.colour());
        tint(view);
    }
    public static void setImageDrawable(android.widget.ImageView view,android.graphics.drawable.Drawable image) {
        if(view==null)return;
        view.setTag(0x54544C48,Boolean.TRUE);
        view.setImageDrawable(image);
        tint(view);
    }
}
