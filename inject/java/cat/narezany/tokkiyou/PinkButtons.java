package cat.narezany.tokkiyou;
import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
/**
 * The "Like" button under a comment in the activity list keeps TikTok's own pink heart: it is drawn
 * inside the button, not through the colour calls the mod redirects. While a screen is up this looks
 * for such buttons now and then and gives their heart the accent. Does nothing while the accent is
 * still TikTok's own colour.
 */
final class PinkButtons {
 private PinkButtons(){}
 private static final int TAG=0x50494E4B;
 static void watch(final Activity activity){
  try{
   final View decor=activity.getWindow().getDecorView();
   if(decor.getTag(TAG)!=null)return;
   decor.setTag(TAG,Boolean.TRUE);
   final long[] last={0L};
   // A screen that has not been laid out again has no button on it that was
   // not there the last time it was looked over. This used to read every
   // label on the screen three times a second for as long as the screen was
   // up, whether or not anything on it had moved.
   final boolean[] moved={true};
   decor.getViewTreeObserver().addOnGlobalLayoutListener(()->moved[0]=true);
   decor.getViewTreeObserver().addOnPreDrawListener(()->{
    if(!moved[0])return true;
    long now=android.os.SystemClock.uptimeMillis();
    if(now-last[0]>=350L){
     last[0]=now;moved[0]=false;
     long probe=Probe.start();
     try{int accent=Accent.colour();if(accent!=Accent.TIKTOK)sweep(decor,accent,new int[]{1500});}
     catch(Throwable error){Diary.note("pink buttons: "+error);}
     Probe.stop(Probe.PINK,probe);
    }
    return true;
   });
  }catch(Throwable error){Diary.note("pink buttons watch: "+error);}
 }
 private static boolean isLike(CharSequence text){
  if(text==null)return false;
  String word=text.toString().trim().toLowerCase(java.util.Locale.ROOT);
  return word.equals("лайк")||word.equals("вподобайка")||word.equals("like");
 }
 private static void sweep(View view,int accent,int[] budget){
  if(view==null||budget[0]--<=0||view.getVisibility()!=View.VISIBLE)return;
  if(view instanceof TextView&&isLike(((TextView)view).getText())){paint((TextView)view,accent);return;}
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)sweep(group.getChildAt(i),accent,budget);}
 }
 private static void paint(TextView label,int accent){
  for(Drawable icon:label.getCompoundDrawables())if(icon!=null)icon.mutate().setColorFilter(accent,PorterDuff.Mode.SRC_IN);
  if(!(label.getParent() instanceof ViewGroup))return;
  ViewGroup button=(ViewGroup)label.getParent();
  float limit=40f*label.getResources().getDisplayMetrics().density;
  for(int i=0;i<button.getChildCount();i++){
   View child=button.getChildAt(i);
   if(child==label||child.getWidth()>limit)continue;
   if(child instanceof com.bytedance.tux.icon.TuxIconView)((com.bytedance.tux.icon.TuxIconView)child).setTintColor(accent);
   else if(child instanceof ImageView)((ImageView)child).setColorFilter(accent,PorterDuff.Mode.SRC_IN);
  }
 }
}
