"""Exercise the actual progress window over navigation and concurrent downloads."""
import pathlib,shutil,subprocess,tempfile,unittest
from android_doubles import STUBS as BASE
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS=dict(BASE)
STUBS.update({
'android/app/Activity.java':'package android.app;public class Activity extends android.content.Context {public boolean finishing;public boolean isFinishing(){return finishing;}}',
'android/app/Dialog.java':'''package android.app;public class Dialog {public Activity owner;public boolean showing;public android.view.View content;public android.view.Window window=new android.view.Window();public Dialog(Activity a){owner=a;}public android.view.Window getWindow(){return window;}public void requestWindowFeature(int n){}public void setContentView(android.view.View v){content=v;}public void setCanceledOnTouchOutside(boolean b){}public void show(){showing=true;}public boolean isShowing(){return showing;}public void dismiss(){showing=false;}}''',
'android/view/Window.java':'''package android.view;public class Window {public static int FEATURE_NO_TITLE=1;public WindowManager.LayoutParams params=new WindowManager.LayoutParams();public View decor=new View();public void setBackgroundDrawable(Object o){}public void setDimAmount(float v){}public void addFlags(int v){}public View getDecorView(){return decor;}public WindowManager.LayoutParams getAttributes(){return params;}public void setAttributes(WindowManager.LayoutParams p){params=p;}}''',
'android/view/WindowManager.java':'''package android.view;public class WindowManager {public static class LayoutParams {public static int FLAG_NOT_TOUCH_MODAL=1,FLAG_NOT_FOCUSABLE=2,FLAG_WATCH_OUTSIDE_TOUCH=4,FLAG_NOT_TOUCHABLE=8;public int width,height,gravity,y;}}''',
'android/os/Handler.java':'package android.os;public class Handler {public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}public boolean postDelayed(Runnable r,long d){return true;}}',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/graphics/Color.java':'package android.graphics;public class Color {public static int TRANSPARENT=0;}',
'android/graphics/Typeface.java':'package android.graphics;public class Typeface {public static int NORMAL=0;public static Typeface create(String name,int style){return new Typeface();}}',
'android/graphics/drawable/ColorDrawable.java':'package android.graphics.drawable;public class ColorDrawable {public ColorDrawable(int c){}}',
'android/util/TypedValue.java':'package android.util;public class TypedValue {public static int COMPLEX_UNIT_SP=2;}',
'cat/narezany/margyt/Accent.java':'package cat.narezany.margyt;class Accent {static int colour(){return 0xffa4c9ff;}}',
'cat/narezany/margyt/Diary.java':'package cat.narezany.margyt;class Diary {static void note(String s){throw new AssertionError(s);}}',
'cat/narezany/margyt/Popup.java':'package cat.narezany.margyt;class Popup {static void show(android.app.Activity a,String t,String m,String b){}}',
})
STUBS['android/content/res/Resources.java']=STUBS['android/content/res/Resources.java'].replace('public float density=1;','public float density=1;public int heightPixels=2000;')
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public static final int VISIBLE=0;', 'public static final int VISIBLE=0;public interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}public void setOnTouchListener(OnTouchListener l){}public void setBackground(Object o){}public void setPadding(int a,int b,int c,int d){}')

STUBS['android/view/MotionEvent.java']=STUBS['android/view/MotionEvent.java'].replace('ACTION_DOWN=0','ACTION_OUTSIDE=4,ACTION_DOWN=0').replace('public int getActionMasked()', 'public int getAction(){return action;}public int getActionMasked()')
STUBS['android/view/Gravity.java']=STUBS['android/view/Gravity.java'].replace('BOTTOM=1','TOP=8,BOTTOM=1')
STUBS['android/widget/TextView.java']=STUBS['android/widget/TextView.java'].replace('public void setTextSize(float f)', 'public void setTextSize(int unit,float f){}public void setTextSize(float f)').replace('public void setTypeface(Object o,int i)', 'public void setTypeface(Object o){}public void setTypeface(Object o,int i)')
STUBS['android/view/ViewGroup.java']=STUBS['android/view/ViewGroup.java'].replace('WRAP_CONTENT=-2','MATCH_PARENT=-1,WRAP_CONTENT=-2')
STUBS['android/widget/LinearLayout.java']=STUBS['android/widget/LinearLayout.java'].replace('HORIZONTAL=0','VERTICAL=1,HORIZONTAL=0').replace('int leftMargin','int topMargin,leftMargin')
STUBS['android/widget/TextView.java']=STUBS['android/widget/TextView.java'].replace('public String text;', 'public String text;public void setEllipsize(android.text.TextUtils.TruncateAt t){}')
STUBS['android/view/Gravity.java']=STUBS['android/view/Gravity.java'].replace('BOTTOM=1','LEFT=8,BOTTOM=1')
STUBS['android/content/res/Resources.java']=STUBS['android/content/res/Resources.java'].replace('public float density=1;', 'public int widthPixels=400;public float density=1;')
STUBS.update({
'android/R.java':'package android;public class R {public static class attr {public static int progressBarStyleHorizontal=1;}public static class id {public static int background=1,progress=2;}}',
'android/text/TextUtils.java':'package android.text;public class TextUtils {public enum TruncateAt {END}}',
'android/content/res/ColorStateList.java':'package android.content.res;public class ColorStateList {public static ColorStateList valueOf(int c){return new ColorStateList();}}',
'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable {}',
'android/graphics/drawable/ClipDrawable.java':'package android.graphics.drawable;public class ClipDrawable extends Drawable {public static int HORIZONTAL=1;public ClipDrawable(Drawable d,int g,int o){}}',
'android/graphics/drawable/LayerDrawable.java':'package android.graphics.drawable;public class LayerDrawable extends Drawable {public LayerDrawable(Drawable[] d){}public void setId(int n,int id){}}',
'android/widget/ProgressBar.java':'package android.widget;public class ProgressBar extends android.view.View {public int value;public boolean indeterminate;public ProgressBar(android.content.Context c,Object a,int s){super(c);}public void setMax(int n){}public void setProgressDrawable(Object o){}public void setIndeterminateTintList(Object o){}public void setIndeterminate(boolean b){indeterminate=b;}public void setProgress(int n){value=n;}}',
'cat/narezany/margyt/Fonts.java':'package cat.narezany.margyt;class Fonts {static void apply(android.widget.TextView v){}}',
})
STUBS['android/graphics/drawable/GradientDrawable.java']=STUBS['android/graphics/drawable/GradientDrawable.java'].replace('class GradientDrawable {','class GradientDrawable extends Drawable {')
STUBS['cat/narezany/margyt/DownloadMark.java']='package cat.narezany.margyt;class DownloadMark extends android.widget.LinearLayout {public float phase;public boolean completing;DownloadMark(android.content.Context c,int colour){super(c);}void bind(android.widget.ProgressBar b){}void progress(int n){}void begin(int width,android.widget.TextView text,android.widget.ProgressBar bar){completing=true;}int initialHeight(){return 48;}void morph(float f){phase=f;}}'
STUBS['cat/narezany/margyt/Motion.java']='package cat.narezany.margyt;class Motion {static boolean enabled(android.content.Context c){return true;}}'
STUBS['android/os/Handler.java']=STUBS['android/os/Handler.java'].replace('public Handler(Looper l){}','public static java.util.List<Runnable> delayed=new java.util.ArrayList<>();public Handler(Looper l){}').replace('public boolean postDelayed(Runnable r,long d){return true;}','public boolean postDelayed(Runnable r,long d){delayed.add(r);return true;}')
STUBS['android/animation/ValueAnimator.java']=STUBS['android/animation/ValueAnimator.java'].replace('int value;', 'Object value;').replace('public static ValueAnimator ofInt', 'public static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.value=Float.valueOf(b);return v;}public static ValueAnimator ofInt')
STUBS['android/view/animation/PathInterpolator.java']='package android.view.animation;public class PathInterpolator {public PathInterpolator(float a,float b,float c,float d){}}'
STUBS['android/widget/TextView.java']=STUBS['android/widget/TextView.java'].replace('public String text;', 'public String text;public int gravity;public String description;public void setContentDescription(String s){description=s;}public String getContentDescription(){return description;}').replace('public void setGravity(int i){}','public void setGravity(int i){gravity=i;}')
STUBS['cat/narezany/margyt/DownloadIsland.java']='package cat.narezany.margyt;class DownloadIsland {boolean open;DownloadIsland(android.app.Activity a,android.app.Dialog d,DownloadMark m){}void toggle(){open=!open;}boolean expanded(){return open;}void collapse(Runnable after){open=false;if(after!=null)after.run();}void dispose(){}}'
STUBS['cat/narezany/margyt/DownloadTasks.java']='package cat.narezany.margyt;class DownloadTasks {static void observe(String k,String l,int p){}static void endObserved(String k,boolean success){}}'
STUBS['cat/narezany/margyt/PlayerTools.java']='package cat.narezany.margyt;class PlayerTools{static void accountChanged(){} static void cancelSmooth(){}}'
HARNESS='''package cat.narezany.margyt;import android.app.*;import android.widget.*;
public class ScreenHarness {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static Dialog bar()throws Exception {java.lang.reflect.Field f=Screen.class.getDeclaredField("bar");f.setAccessible(true);return (Dialog)f.get(null);}
 public static void main(String[] args)throws Exception {
  Activity first=new Activity(),second=new Activity();Screen.at(first);Screen.progress("apk","Update",12);
  Dialog old=bar();check(old!=null && old.showing && old.owner==first,"first owner");
  Screen.gone(first);check(!old.showing && bar()==null,"paused window dismissed");
  Screen.progress("apk","Update",45);check(bar()==null,"no stale window while between screens");
  Screen.at(second);Dialog next=bar();check(next!=old && next.owner==second && next.showing,"reattach on destination");
  check(((TextView)((android.widget.LinearLayout)next.content).children.get(0)).text.equals("Загрузка") && ((android.widget.ProgressBar)((android.widget.LinearLayout)next.content).children.get(1)).value==45,"latest progress retained");
  check(((TextView)((android.widget.LinearLayout)next.content).children.get(0)).gravity==android.view.Gravity.CENTER,"label centered");
  check(((TextView)((android.widget.LinearLayout)next.content).children.get(0)).description.equals("Update"),"operation retained for accessibility");
  Screen.progress("plugin","Plugin",60);check(((TextView)((android.widget.LinearLayout)bar().content).children.get(0)).description.contains("· 2"),"concurrent tasks tracked");
  Screen.progressGone("plugin");check(bar().showing && ((TextView)((android.widget.LinearLayout)bar().content).children.get(0)).text.equals("Загрузка"),"finishing plugin retains apk pill");
  Screen.gone(second);Screen.progressGone("apk");Screen.at(first);check(bar()==null,"completed task cannot revive on resume");
  Screen.progress("Legacy",-1);check(bar()!=null,"legacy plugin API retained");Screen.progressGone();check(bar()==null,"legacy completion");
  Screen.progress("apk","Update",98);old=bar();old.dismiss();Screen.progress("apk","Update",99);check(bar()!=old && bar().showing,"externally hidden window repaired");
  Screen.progressGone("apk");
  Screen.progress("success","Update",100);android.view.View before=bar().content;Screen.progressDone("success");Dialog completed=bar();check(completed.content==before,"same surface throughout completion; no content swap");check(completed.content instanceof DownloadMark && completed.window.params.width==56,"successful download morphs to vector mark");
  Screen.progress("next","Next",1);check(bar()!=completed,"new job replaces completion");for(Runnable callback:android.os.Handler.delayed)callback.run();check(bar()!=null && bar().showing,"old completion timer cannot dismiss new download");Screen.progressGone("next");
  System.out.println("navigation and concurrent download checks passed");
 }
}'''
class ScreenRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_navigation_and_downloads(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   sources['android/view/View.java']=sources['android/view/View.java'].replace('public void setPadding(int a,int b,int c,int d){}','public void setPadding(int a,int b,int c,int d){}public void setTranslationY(float y){}')
   sources['android/widget/TextView.java']=sources['android/widget/TextView.java'].replace('public String text;','public String text;public void setMaxLines(int n){}')
   for name in ('Screen','ProgressJobs','DownloadMorph'):
    sources['cat/narezany/margyt/'+name+'.java']=(ROOT/('inject/java/cat/narezany/margyt/'+name+'.java')).read_text(encoding='utf-8')
   sources['cat/narezany/margyt/ScreenHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,content in sources.items():
    target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.ScreenHarness'],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)







