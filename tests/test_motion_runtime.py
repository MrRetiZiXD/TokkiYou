"""Verify real Motion touch feedback keeps scrolling/click dispatch and cancellation."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS={
'android/content/Context.java':'package android.content;public class Context {public Object getContentResolver(){return null;}}',
'android/os/Build.java':'package android.os;public class Build {public static class VERSION {public static int SDK_INT=35;}}',
'android/provider/Settings.java':'package android.provider;public class Settings {public static class Global {public static String ANIMATOR_DURATION_SCALE="scale";public static float getFloat(Object c,String n,float value){return value;}}}',
'android/animation/ValueAnimator.java':'package android.animation;public class ValueAnimator {public static boolean enabled=true;public static boolean areAnimatorsEnabled(){return enabled;}}',
'android/content/res/ColorStateList.java':'package android.content.res;public class ColorStateList {public static ColorStateList valueOf(int c){return new ColorStateList();}}',
'android/graphics/Color.java':'package android.graphics;public class Color {public static int TRANSPARENT=0,WHITE=-1;}',
'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable {}',
'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable;public class GradientDrawable extends Drawable {public void setColor(int c){}}',
'android/graphics/drawable/ColorDrawable.java':'package android.graphics.drawable;public class ColorDrawable extends Drawable {public ColorDrawable(int c){}}',
'android/graphics/drawable/RippleDrawable.java':'package android.graphics.drawable;public class RippleDrawable extends Drawable {public Drawable content;public RippleDrawable(Object a,Drawable b,Drawable c){content=b;}}',
'android/view/animation/DecelerateInterpolator.java':'package android.view.animation;public class DecelerateInterpolator {}',
'android/view/animation/PathInterpolator.java':'package android.view.animation;public class PathInterpolator {public PathInterpolator(float a,float b,float c,float d){}}',
'android/view/MotionEvent.java':'package android.view;public class MotionEvent {public static int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3;private int action;public MotionEvent(int action){this.action=action;}public int getActionMasked(){return action;}}',
'android/view/View.java':"""package android.view;public class View {
 public interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}
 public OnTouchListener listener;public boolean clickable=true;public float scaleX=1,scaleY=1,alpha=1,x,y,startX;
 public ViewPropertyAnimator animator=new ViewPropertyAnimator(this);
 public void setOnTouchListener(OnTouchListener listener){this.listener=listener;}public boolean isEnabled(){return true;}public boolean isClickable(){return clickable;}
 public android.content.Context getContext(){return new android.content.Context();}
 public static class Metrics {public float density=1;}public static class Resources {public Metrics getDisplayMetrics(){return new Metrics();}}
 public Resources getResources(){return new Resources();}public void setFocusable(boolean f){}public android.graphics.drawable.Drawable background;public void setBackground(android.graphics.drawable.Drawable b){background=b;}public android.graphics.drawable.Drawable getBackground(){return background;}
 public void setAlpha(float v){alpha=v;}public void setTranslationX(float v){x=v;startX=v;}public void setTranslationY(float v){y=v;}
 public int getHeight(){return 100;}public boolean isAttachedToWindow(){return true;}public void post(Runnable r){r.run();}public ViewPropertyAnimator animate(){return animator;}
 }""",
'android/view/ViewPropertyAnimator.java':"""package android.view;public class ViewPropertyAnimator {
 public Runnable end;private View view;public ViewPropertyAnimator(View view){this.view=view;}
 public ViewPropertyAnimator withEndAction(Runnable r){end=r;return this;}public void cancel(){end=null;}
 public ViewPropertyAnimator scaleX(float v){view.scaleX=v;return this;}public ViewPropertyAnimator scaleY(float v){view.scaleY=v;return this;}
 public ViewPropertyAnimator alpha(float v){view.alpha=v;return this;}public ViewPropertyAnimator translationX(float v){view.x=v;return this;}public ViewPropertyAnimator translationY(float v){view.y=v;return this;}
 public ViewPropertyAnimator setDuration(long v){return this;}public ViewPropertyAnimator setInterpolator(Object v){return this;}public void start(){}public void finish(){Runnable r=end;end=null;if(r!=null)r.run();}
 }""",
'cat/narezany/margyt/Accent.java':'package cat.narezany.margyt;class Accent {static int colour(){return -1;}}'
}
HARNESS="""package cat.narezany.margyt;import android.view.*;
class MotionHarness {static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}public static void main(String[] args){
 View button=new View();
 android.graphics.drawable.GradientDrawable accent=new android.graphics.drawable.GradientDrawable();button.setBackground(accent);Motion.press(button);
 check(((android.graphics.drawable.RippleDrawable)button.getBackground()).content==accent,"Press retains the original accent and shape");
 android.graphics.drawable.Drawable ripple=button.getBackground();Motion.press(button);check(button.getBackground()==ripple,"Repeated setup cannot stack ripple backgrounds");
 View empty=new View();Motion.press(empty);check(((android.graphics.drawable.RippleDrawable)empty.getBackground()).content!=null,"Empty background receives a valid ripple base");
 check(!button.listener.onTouch(button,new MotionEvent(0)),"Touch is not consumed: native click and scrolling remain available");
 check(button.scaleX<1,"Clickable press has feedback");button.listener.onTouch(button,new MotionEvent(3));check(button.scaleX==1 && button.scaleY==1,"Scroll cancellation restores dimensions");
 button.clickable=false;button.listener.onTouch(button,new MotionEvent(0));check(button.scaleX==1,"Non-clickable preview cannot get stuck compressed without an Up event");
 button.clickable=true;android.animation.ValueAnimator.enabled=false;button.listener.onTouch(button,new MotionEvent(0));check(button.scaleX==1,"Disabled motion has no compression");
 int[] changes={0};Motion.navigate(button,1,()->changes[0]++);check(changes[0]==1 && button.alpha==1,"Reduced motion changes section directly");
 android.animation.ValueAnimator.enabled=true;Motion.navigate(button,-1,()->changes[0]++);check(changes[0]==1,"Normal navigation waits for exit");
 button.animator.finish();check(changes[0]==2 && button.startX<0,"Back transition enters from the opposite direction");button.animator.finish();check(changes[0]==2,"Completion is not reused by later animations");
 }}"""
class MotionRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('java') and shutil.which('javac'),'JDK required')
 def test_touch_cancellation_and_navigation(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   sources['cat/narezany/margyt/Motion.java']=(ROOT/'inject/java/cat/narezany/margyt/Motion.java').read_text(encoding='utf-8')
   sources['cat/narezany/margyt/MotionHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,value in sources.items():
    target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(value,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.MotionHarness'],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)
