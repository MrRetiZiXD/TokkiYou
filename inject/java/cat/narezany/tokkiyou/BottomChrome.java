package cat.narezany.tokkiyou;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import java.lang.ref.WeakReference;

/** Keep the navigation's backing opaque while its controls remain dimmed. */
final class BottomChrome {
    private BottomChrome() {}
    private static WeakReference<View> rail = new WeakReference<View>(null);
    private static Drawable original, backing;
    private static ColorDrawable shield;

    static boolean isRail(View view) { return rail.get() == view; }
    static boolean containsRail(View ancestor){
        View current=rail.get();for(int i=0;i<40&&current!=null;i++){
            if(current==ancestor)return true;Object parent=current.getParent();current=parent instanceof View?(View)parent:null;
        }return false;
    }
    static void repair(){View view=rail.get();if(view!=null&&view.isAttachedToWindow()&&view.isShown())protect(view);}

    static void protect(View view) {
        if (view == null) return;
        // the mod's own bar has put the backing away on purpose
        if (NavBar.wears(view)) return;
        if (rail.get() != view) { restore(); rail = new WeakReference<View>(view); }
        if (backing != null && view.getBackground() == backing) {if(shield!=null){if(shield.getAlpha()!=255)shield.setAlpha(255);if(shield.getColor()!=Color.BLACK)shield.setColor(Color.BLACK);if(shield.getColorFilter()!=null)shield.clearColorFilter();}return;}
        // Retain native styling above the backing, including future background changes.
        original = view.getBackground();
        shield=new ColorDrawable(Color.BLACK);
        backing = original == null ? shield
                : new LayerDrawable(new Drawable[] {shield, original});
        view.setBackgroundDrawable(backing);
    }

    static void restore() {
        View view = rail.get();
        if (view != null && view.getBackground() == backing) view.setBackgroundDrawable(original);
        rail = new WeakReference<View>(null);
        original = null; backing = null;shield=null;
    }
}
