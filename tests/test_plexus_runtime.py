"""Execute the real Plexus draw loop, lifecycle, touch forwarding and bounds."""
import pathlib,subprocess,tempfile,shutil,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
SOURCES={
'android/content/Context.java':'package android.content;public class Context{}',
'android/content/res/Resources.java':'package android.content.res;public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}',
'android/util/DisplayMetrics.java':'package android.util;public class DisplayMetrics {public float density=1;}',
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long now=1000;public static long uptimeMillis(){return now;}}',
'android/graphics/Paint.java':'package android.graphics;public class Paint {public static final int ANTI_ALIAS_FLAG=1;public int colour;public Paint(int f){}public void setColor(int c){colour=c;}public void setStrokeWidth(float f){}}',
'android/graphics/Canvas.java':'package android.graphics;public class Canvas {public int dots,lines;public void drawLine(float a,float b,float c,float d,Paint p){lines++;}public void drawCircle(float a,float b,float r,Paint p){dots++;}}',
'android/view/MotionEvent.java':'package android.view;public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;public int action;public float x,y;public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}public int getActionMasked(){return action;}public float getX(){return x;}public float getY(){return y;}}',
'android/view/View.java':'''package android.view;public class View {public static final int VISIBLE=0,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_NO=2;public java.util.Map<Runnable,Long> queued=new java.util.HashMap<>();public boolean attached=true;public int visibility=0,window=0;public android.content.Context context;public View(android.content.Context c){context=c;}public android.content.Context getContext(){return context;}public android.content.res.Resources getResources(){return new android.content.res.Resources();}public void setImportantForAccessibility(int v){}public void setClickable(boolean v){}public void setFocusable(boolean v){}public void setVisibility(int v){visibility=v;}public boolean isAttachedToWindow(){return attached;}public int getWindowVisibility(){return window;}public void invalidate(){}public void postOnAnimation(Runnable r){queued.put(r,16L);}public void postDelayed(Runnable r,long ms){queued.put(r,ms);}public void removeCallbacks(Runnable r){queued.remove(r);}protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}protected void onWindowVisibilityChanged(int v){}protected void onSizeChanged(int a,int b,int c,int d){}protected void onDraw(android.graphics.Canvas c){}public boolean dispatchTouchEvent(MotionEvent e){return true;}}''',
'android/widget/FrameLayout.java':'package android.widget;public class FrameLayout extends android.view.View {public FrameLayout(android.content.Context c){super(c);}}',
'cat/narezany/margyt/Motion.java':'package cat.narezany.margyt;class Motion {static boolean on=true;static boolean enabled(android.content.Context c){return on;}}',
'cat/narezany/margyt/Accent.java':'package cat.narezany.margyt;class Accent {static int colour(){return 0xff6750a4;}}',
'cat/narezany/margyt/PlexusHarness.java':'''package cat.narezany.margyt;import android.view.MotionEvent;public class PlexusHarness {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception {
  PlexusField f=new PlexusField();f.resize(360,800,1);
  f.pick(f.x[0],f.y[0],5);int picked=f.grabbed;check(picked>=0,"pick nearby dot");f.drag(900,-10);check(f.x[picked]==360&&f.y[picked]==0,"drag clamps");
  f.step(100);check(f.x[picked]==360&&f.y[picked]==0,"grabbed dot stays under finger");f.release();
  for(int n=0;n<10000;n++)f.step(0.033f);
  for(int n=0;n<PlexusField.COUNT;n++)check(f.x[n]>=0&&f.x[n]<=360&&f.y[n]>=0&&f.y[n]<=800,"long-running bounds");
  PlexusBackground b=new PlexusBackground(new android.content.Context());b.onSizeChanged(360,800,0,0);b.running(true);
  check(b.queued.size()==1&&b.queued.values().iterator().next()==16,"one vsync callback");
  for(int n=0;n<108000;n++){Runnable next=b.queued.keySet().iterator().next();b.queued.clear();android.os.SystemClock.now+=17;next.run();check(b.queued.size()==1,"no callback multiplication");}
  android.graphics.Canvas c=new android.graphics.Canvas();b.onDraw(c);check(c.dots==28&&c.lines<=56,"bounded render work");
  b.running(false);check(b.queued.isEmpty(),"pause cancels frame");b.running(true);b.configure(false);check(b.queued.isEmpty(),"off cancels frame");
  b.configure(true);Motion.on=false;b.running(true);check(b.queued.isEmpty(),"reduced motion stays static");
  Motion.on=true;b.running(true);b.intensity(0);check(b.queued.isEmpty(),"zero intensity stops animation");b.intensity(100);check(b.queued.size()==1,"positive intensity resumes");b.window=8;b.onWindowVisibilityChanged(8);check(b.queued.isEmpty(),"hidden window stops");b.window=0;b.onWindowVisibilityChanged(0);check(b.queued.size()==1,"visible resumes exactly once");
  PlexusBackground.Host h=new PlexusBackground.Host(new android.content.Context());h.background=b;check(h.dispatchTouchEvent(new MotionEvent(0,120,120)),"native child result preserved");
  b.onDetachedFromWindow();check(b.queued.isEmpty(),"detach cancels frame");
 }
}'''
}
class PlexusRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_lifecycle_and_bounded_work(self):
  with tempfile.TemporaryDirectory() as d:
   work=pathlib.Path(d);sources=dict(SOURCES)
   for name in ('PlexusField','PlexusBackground'):
    sources['cat/narezany/margyt/'+name+'.java']=(ROOT/('inject/java/cat/narezany/margyt/'+name+'.java')).read_text(encoding='utf-8')
   __import__("android_doubles").with_mod(sources)
   for name,s in sources.items():
    p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
   run=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',d]+[str(work/n) for n in sources],capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
   run=subprocess.run(['java','-cp',d,'cat.narezany.margyt.PlexusHarness'],capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
