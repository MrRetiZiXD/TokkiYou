"""Run actual SheetDialog lifecycle against deterministic Android doubles."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
    'cat/narezany/margyt/Fonts.java': 'package cat.narezany.margyt; class Fonts {static void applyTree(android.view.View v){}}',
    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/app/Activity.java': '''package android.app;
        public class Activity extends android.content.Context {
        public boolean finishing; public boolean isFinishing(){return finishing;}
        public boolean isDestroyed(){return false;} }''',
    'android/R.java': 'package android; public class R { public static class id { public static int content=1; } }',
    'android/view/WindowManager.java': '''package android.view;
        public class WindowManager { public static class LayoutParams {
        public static int FLAG_NOT_TOUCHABLE=16; public float dimAmount=.45f; } }''',
    'android/view/Window.java': '''package android.view;
        public class Window { public int flags; public View view=new View();
        public WindowManager.LayoutParams attrs=new WindowManager.LayoutParams();
        public void setWindowAnimations(int n){} public void clearFlags(int n){flags&=~n;}
        public void addFlags(int n){flags|=n;} public View getDecorView(){return view;}
        public WindowManager.LayoutParams getAttributes(){return attrs;}
        public void setDimAmount(float f){attrs.dimAmount=f;} }''',
    'android/view/View.java': '''package android.view;
        public class View { public java.util.List<Runnable> posts=new java.util.ArrayList<>();
        public ViewPropertyAnimator animator=new ViewPropertyAnimator();
        public View findViewById(int n){return this;} public int getHeight(){return 200;}
        public void removeCallbacks(Runnable r){posts.remove(r);}
        public ViewPropertyAnimator animate(){return animator;} }''',
    'android/view/ViewPropertyAnimator.java': '''package android.view;
        public class ViewPropertyAnimator { public Runnable end; public int starts;
        public ViewPropertyAnimator withEndAction(Runnable r){end=r;return this;}
        public void cancel(){} public ViewPropertyAnimator translationY(float f){return this;}
        public ViewPropertyAnimator alpha(float f){return this;}
        public ViewPropertyAnimator setDuration(long n){return this;}
        public ViewPropertyAnimator setInterpolator(Object o){return this;}
        public void start(){starts++;} public void complete(){Runnable r=end;end=null;if(r!=null)r.run();} }''',
    'android/view/animation/PathInterpolator.java': '''package android.view.animation;
        public class PathInterpolator { public PathInterpolator(float a,float b,float c,float d){} }''',
    'android/animation/ValueAnimator.java': '''package android.animation;
        public class ValueAnimator { public interface Update {void tick(ValueAnimator a);}
        public float to; public Update update;
        public static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.to=b;return v;}
        public void cancel(){} public void setDuration(long n){}
        public void addUpdateListener(Update u){update=u;}
        public Object getAnimatedValue(){return Float.valueOf(to);} public void start(){update.tick(this);} }''',
    'android/app/Dialog.java': '''package android.app;
        public class Dialog { public int dismisses,cancels; public boolean showing;
        public android.view.Window window=new android.view.Window();
        // Android wraps the activity context. Cleanup must check the original owner.
        public Dialog(android.content.Context c){} public android.content.Context getContext(){return new android.content.Context();}
        public android.view.Window getWindow(){return window;} public boolean isShowing(){return showing;}
        protected void onStart(){} protected void onStop(){}
        public void show(){showing=true;onStart();}
        public void dismiss(){if(showing){showing=false;dismisses++;onStop();}}
        public void cancel(){cancels++;dismiss();} }''',
    'cat/narezany/margyt/Motion.java': '''package cat.narezany.margyt;
        class Motion {static boolean active=true;
        static boolean enabled(android.content.Context c){return active;}
        static Runnable rise(android.view.View v){Runnable r=()->v.animate().start();v.posts.add(r);return r;} }''',
}
STUBS['android/content/Context.java']=STUBS['android/content/Context.java'].replace('class Context {}','class Context {public android.content.res.Resources getResources(){return new android.content.res.Resources();}}')
STUBS['android/content/res/Resources.java']='package android.content.res;public class Resources {public static class Metrics {public float density=1;}public Metrics getDisplayMetrics(){return new Metrics();}}'
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public class View {', 'public class View {public View(){}public View(android.content.Context c){}public Object tag;public float y;public interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}public OnTouchListener touch;public interface OnClickListener {void onClick(View v);}public void setOnTouchListener(OnTouchListener t){touch=t;}public void setOnClickListener(OnClickListener c){}public void setTag(Object t){tag=t;}public View findViewWithTag(Object t){return t.equals(tag)?this:null;}public void setContentDescription(String s){}public void setClickable(boolean b){}public void setFocusable(boolean b){}public void setBackground(Object o){}public void setAlpha(float a){}public void setTranslationY(float value){y=value;}')
STUBS['android/view/ViewGroup.java']='package android.view;public class ViewGroup extends View {public java.util.List<View> children=new java.util.ArrayList<>();public int getChildCount(){return children.size();}public View getChildAt(int n){return children.get(n);}public void addView(View v,LayoutParams p){children.add(v);}public void addView(View v,int n,LayoutParams p){children.add(n,v);}public View findViewWithTag(Object t){View own=super.findViewWithTag(t);if(own!=null)return own;for(View v:children){View found=v.findViewWithTag(t);if(found!=null)return found;}return null;}public static class LayoutParams {public static int MATCH_PARENT=-1;public LayoutParams(int w,int h){}}}'
STUBS['android/widget/LinearLayout.java']='package android.widget;public class LinearLayout extends android.view.ViewGroup {public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public LayoutParams(int w,int h){super(w,h);}}}'
STUBS['android/widget/FrameLayout.java']='package android.widget;public class FrameLayout extends android.view.ViewGroup {public FrameLayout(android.content.Context c){}public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public LayoutParams(int w,int h,int g){super(w,h);}}}'
STUBS['android/view/Gravity.java']='package android.view;public class Gravity {public static int CENTER=1;}'
STUBS['android/view/MotionEvent.java']='package android.view;public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action;float y;long time;public MotionEvent(int a,float n,long t){action=a;y=n;time=t;}public int getActionMasked(){return action;}public float getRawY(){return y;}public long getEventTime(){return time;}}'
STUBS['android/graphics/drawable/GradientDrawable.java']='package android.graphics.drawable;public class GradientDrawable {public void setColor(int c){}public void setCornerRadius(int r){}}'
STUBS['cat/narezany/margyt/Skin.java']='package cat.narezany.margyt;class Skin {int text=-1;static Skin remembered(android.content.Context c){return new Skin();}}'
STUBS['android/app/Dialog.java']=STUBS['android/app/Dialog.java'].replace('public void show(){','public void setCanceledOnTouchOutside(boolean allow){}public void show(){')
STUBS['android/view/ViewPropertyAnimator.java']=STUBS['android/view/ViewPropertyAnimator.java'].replace('public Runnable end;', 'public float targetY;public Runnable end;').replace('translationY(float f){return this;}', 'translationY(float f){targetY=f;return this;}')
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public float y;', 'public float y;public float getTranslationY(){return y;}')
HARNESS = '''package cat.narezany.margyt;
public class SheetHarness {
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        SheetDialog normal=new SheetDialog(new android.content.Context());normal.show();
        check(normal.window.view.posts.size()==1,"Entrance scheduled");
        normal.dismiss();normal.dismiss();normal.cancel();
        check(normal.window.view.posts.isEmpty(),"Closing cancels queued entrance");
        check(normal.window.view.animator.starts==1,"Only one closing animation");
        check(normal.isShowing(),"Window retained until exit completes");
        check(normal.window.flags==16,"Closing window blocks duplicate taps");
        normal.window.view.animator.complete();
        check(!normal.isShowing()&&normal.dismisses==1&&normal.cancels==0,"Dismiss once");
        check(normal.window.attrs.dimAmount==.45f,"Backdrop restored for reuse");
        normal.show();check(normal.window.flags==0,"Reopened window accepts taps");
        normal.cancel();normal.window.view.animator.complete();
        check(normal.cancels==1&&normal.dismisses==2,"Cancel preserves platform semantics");
        SheetDialog outside=new SheetDialog(new android.content.Context());outside.show();outside.cancel();outside.cancel();
        outside.window.view.animator.complete();check(outside.cancels==1,"Outside and back cancel once");
        Motion.active=false;SheetDialog reduced=new SheetDialog(new android.content.Context());reduced.show();reduced.dismiss();
        check(!reduced.isShowing()&&reduced.window.view.animator.starts==0,"Disabled motion closes immediately");
        Motion.active=true;android.app.Activity owner=new android.app.Activity();SheetDialog destroyed=new SheetDialog(owner);
        destroyed.show();owner.finishing=true;destroyed.dismiss();
        check(!destroyed.isShowing()&&destroyed.window.view.posts.isEmpty(),"Activity cleanup has no delayed exit");
        android.app.Activity leaving=new android.app.Activity();SheetDialog interrupted=new SheetDialog(leaving);
        interrupted.show();interrupted.dismiss();leaving.finishing=true;interrupted.dismiss();
        interrupted.window.view.animator.complete();
        check(!interrupted.isShowing()&&interrupted.dismisses==1,"Activity interrupts pending exit exactly once");
        SheetDialog dragged=new SheetDialog(new android.content.Context());android.view.ViewGroup content=new android.view.ViewGroup();android.widget.LinearLayout card=new android.widget.LinearLayout();content.addView(card,new android.view.ViewGroup.LayoutParams(200,200));dragged.window.view=content;dragged.show();android.view.View handle=card.getChildAt(0);
        check(handle.touch.onTouch(handle,new android.view.MotionEvent(0,100,0)),"Grip captures drag only on handle");handle.touch.onTouch(handle,new android.view.MotionEvent(2,150,300));check(content.y==50,"Surface follows finger");handle.touch.onTouch(handle,new android.view.MotionEvent(1,150,310));check(dragged.isShowing()&&content.animator.targetY==0,"Short drag settles");
        dragged.setCanceledOnTouchOutside(false);check(!handle.touch.onTouch(handle,new android.view.MotionEvent(0,100,400)),"Busy/non-cancellable window blocks drag");dragged.setCanceledOnTouchOutside(true);
        handle.touch.onTouch(handle,new android.view.MotionEvent(0,100,500));handle.touch.onTouch(handle,new android.view.MotionEvent(2,190,800));handle.touch.onTouch(handle,new android.view.MotionEvent(1,190,810));content.animator.complete();check(!dragged.isShowing()&&dragged.cancels==1,"Long drag closes with cancel semantics");
        SheetDialog centered=new SheetDialog(new android.content.Context(),true);android.view.ViewGroup centerContent=new android.view.ViewGroup();android.widget.LinearLayout centerCard=new android.widget.LinearLayout();centerContent.addView(centerCard,new android.view.ViewGroup.LayoutParams(200,200));centered.window.view=centerContent;centered.show();
        check(centerCard.getChildCount()==0,"Centered message has no hidden sheet grip or extra top spacing");
        check(centerContent.y==0 && centerContent.posts.isEmpty(),"Centered message does not slide from the bottom");
        centered.dismiss();check(centerContent.animator.targetY==0,"Centered message fades out in place");centerContent.animator.complete();check(!centered.isShowing()&&centered.dismisses==1,"Centered message closes normally");
        System.out.println("Sheet lifecycle and interruption scenarios passed");
    }
}'''


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
class SheetRuntimeTest(unittest.TestCase):
    def test_close_cancel_reopen_and_activity_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = dict(STUBS)
            sources['cat/narezany/margyt/SheetDialog.java'] = (
                ROOT / 'inject/java/cat/narezany/margyt/SheetDialog.java').read_text(encoding='utf-8')
            sources['cat/narezany/margyt/SheetDrag.java']=(ROOT/'inject/java/cat/narezany/margyt/SheetDrag.java').read_text()
            sources['cat/narezany/margyt/SheetHarness.java'] = HARNESS
            __import__("android_doubles").with_mod(sources)
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            compiled = subprocess.run(['javac', '-source', '8', '-target', '8', '-d', str(work)] +
                                      [str(work / name) for name in sources], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            result = subprocess.run(['java', '-cp', str(work), 'cat.narezany.margyt.SheetHarness'],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('Sheet lifecycle and interruption scenarios passed', result.stdout)
