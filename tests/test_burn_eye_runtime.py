"""Check the actual eye overlay preserves the native LIVE control and geometry."""
import pathlib, shutil, subprocess, tempfile, unittest
from android_doubles import STUBS as BASE
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS=dict(BASE)
STUBS['android/widget/ImageView.java']='package android.widget;public class ImageView extends android.view.View {}'
v=STUBS['android/view/View.java']
v=v.replace('public int visibility=0;', 'public int visibility=0,width=40,height=40,left,top;public boolean focusable;public CharSequence description;public static final int GONE=8,IMPORTANT_FOR_ACCESSIBILITY_YES=1;')
v=v.replace('public int getWidth(){return 300;}public int getHeight(){return 50;}', 'public int getWidth(){return width;}public int getHeight(){return height;}public void getLocationOnScreen(int[] p){p[0]=left;p[1]=top;}public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;}public boolean isShown(){return visibility==VISIBLE;}public boolean isClickable(){return clickable;}public CharSequence getContentDescription(){return description;}public void setContentDescription(CharSequence s){description=s;}public void setFocusable(boolean b){focusable=b;}public void setImportantForAccessibility(int i){}')
v=v.replace('public class View implements ViewParent {', 'public class View implements ViewParent {public Object ordinaryTag;public java.util.Map<Integer,Object> keyedTags=new java.util.HashMap<>();public Object getTag(){return ordinaryTag;}public Object getTag(int key){return keyedTags.get(key);}public void setTag(Object value){ordinaryTag=value;}public void setTag(int key,Object value){keyedTags.put(key,value);}')
v=v.replace('public void setImportantForAccessibility(int i){}','public int accessibility;public static final int IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;public int getImportantForAccessibility(){return accessibility;}public void setImportantForAccessibility(int i){accessibility=i;}')
STUBS['android/view/View.java']=v
STUBS['android/view/ViewTreeObserver.java']='package android.view;public class ViewTreeObserver {public OnPreDrawListener draw;public interface OnPreDrawListener{boolean onPreDraw();}public boolean isAlive(){return true;}public void addOnPreDrawListener(OnPreDrawListener v){draw=v;}public void removeOnPreDrawListener(OnPreDrawListener v){if(draw==v)draw=null;}}'
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public class View implements ViewParent {','public class View implements ViewParent {public float alpha=1;public float getAlpha(){return alpha;}public ViewTreeObserver observer=new ViewTreeObserver();public ViewTreeObserver getViewTreeObserver(){return observer;}').replace('public void setAlpha(float v){}','public void setAlpha(float v){alpha=v;}').replace('return visibility==VISIBLE;', 'return visibility==VISIBLE && (parent==null || parent.isShown());')
STUBS['android/widget/FrameLayout.java']='package android.widget;public class FrameLayout extends android.view.ViewGroup {public FrameLayout(){super();}public FrameLayout(android.content.Context c){super(c);}public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public int bottomMargin,leftMargin,topMargin,gravity;public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,int g){super(w,h);gravity=g;}}}'
STUBS['android/view/ViewGroup.java']=STUBS['android/view/ViewGroup.java'].replace('public void removeView(View v)', 'public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}public void removeView(View v)')
STUBS['android/widget/TextView.java']=STUBS['android/widget/TextView.java'].replace('public void setText(String s)', 'public CharSequence getText(){return text;}public void setText(String s)')
STUBS['android/graphics/Color.java']='package android.graphics;public class Color {public static int WHITE=-1;}'
STUBS['android/graphics/Path.java']='package android.graphics;public class Path {public void reset(){}public void moveTo(float x,float y){}public void lineTo(float x,float y){}public void close(){}public void quadTo(float a,float b,float c,float d){}public void cubicTo(float a,float b,float c,float d,float e,float f){}}'
STUBS['android/graphics/Rect.java']='package android.graphics;public class Rect {public int left,top,right,bottom;public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}public int width(){return right-left;}public int height(){return bottom-top;}}'
STUBS['android/graphics/PixelFormat.java']='package android.graphics;public class PixelFormat {public static final int TRANSLUCENT=-3;}'
STUBS['android/graphics/ColorFilter.java']='package android.graphics;public class ColorFilter {}'
STUBS['android/graphics/drawable/Drawable.java']='package android.graphics.drawable;public abstract class Drawable {android.graphics.Rect bounds=new android.graphics.Rect();public void setBounds(int l,int t,int r,int b){bounds.left=l;bounds.top=t;bounds.right=r;bounds.bottom=b;}public android.graphics.Rect getBounds(){return bounds;}public abstract void draw(android.graphics.Canvas c);public abstract void setAlpha(int a);public abstract void setColorFilter(android.graphics.ColorFilter f);public abstract int getOpacity();public void invalidateSelf(){}}'
STUBS['android/graphics/Paint.java']=STUBS['android/graphics/Paint.java'].replace('public void setColorFilter', 'public enum Style {STROKE,FILL}public enum Cap {ROUND}public enum Join {ROUND}public void setStrokeJoin(Join j){}public void setAlpha(int a){}public void setColor(int c){}public void setStyle(Style s){}public void setStrokeWidth(float v){}public void setStrokeCap(Cap c){}public void setColorFilter')
STUBS['android/graphics/Canvas.java']=STUBS['android/graphics/Canvas.java'].replace('public void drawBitmap', 'public static int bitmaps;public static Bitmap lastBitmap;public static Rect lastDst;public static int circles;public void drawBitmap(Bitmap b,Rect s,Rect d,Paint p){bitmaps++;lastBitmap=b;lastDst=d;}public int save(){return 0;}public void restoreToCount(int n){}public void translate(float x,float y){}public void scale(float x,float y){}public void drawPath(Path p,Paint q){}public void drawLine(float a,float b,float c,float d,Paint p){}public void drawCircle(float a,float b,float c,Paint p){circles++;}public void drawBitmap')
STUBS['android/view/Gravity.java']=STUBS['android/view/Gravity.java'].replace('BOTTOM=1', 'TOP=8,LEFT=16,BOTTOM=1')
STUBS['cat/narezany/margyt/Dim.java']='package cat.narezany.margyt;class Dim {static boolean configured=true;static boolean isConfigured(){return configured;}static int strength=35;static int strength(){return strength;}static void toggleOpacity(){enabled=!enabled;}static void retry(){retries++;}static int retries;static boolean enabled;static boolean isEnabled(){return enabled;}static void setEnabled(boolean v){enabled=v;}}'
STUBS['cat/narezany/margyt/Text.java']='package cat.narezany.margyt;class Text {static String DIM_EYE_ON="on",DIM_EYE_OFF="off";}'
STUBS['android/content/res/AssetManager.java']='package android.content.res;public class AssetManager {public java.io.InputStream open(String n)throws java.io.IOException{if(n.contains("missing"))throw new java.io.IOException(n);return new java.io.ByteArrayInputStream(n.getBytes());}}'
STUBS['android/graphics/BitmapFactory.java']='package android.graphics;public class BitmapFactory {public static int decoded;public static boolean fail;public static Bitmap decodeStream(java.io.InputStream in){decoded++;return fail?null:new Bitmap();}}'
STUBS['android/content/Context.java']=STUBS['android/content/Context.java'].replace('public class Context {','public class Context {public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}')
STUBS['cat/narezany/margyt/Diary.java']='package cat.narezany.margyt;class Diary {static void note(String s){}}'
from animation_doubles import STUBS as ANIMATION
STUBS.update(ANIMATION)
HARNESS="""package cat.narezany.margyt;import android.view.*;import android.widget.*;
public class EyeHarness {static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[] args){
FrameLayout root=new FrameLayout();root.width=400;root.height=900;
View live=new View();live.setTag(0x7f0a374f,"live");live.width=40;live.height=40;live.left=12;live.top=60;live.clickable=true;root.addView(live,new FrameLayout.LayoutParams(40,40));
TextView tab=new TextView(new android.content.Context());tab.setText("Рекомендации");tab.left=120;tab.top=60;root.addView(tab,new FrameLayout.LayoutParams(140,40));
View eye=BurnEye.install(root);check(eye!=null && eye!=live,"Eye replaces native artwork without replacing the layout node");
FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)eye.getLayoutParams();check(layout.leftMargin+22==live.left+20 && layout.topMargin+22==live.top+20,"Eye is centered in the LIVE slot, not beneath it");
check(live.alpha==0 && !live.clickable && live.accessibility==4,"LIVE is invisible, noninteractive and hidden from accessibility");
eye.click.onClick(eye);check(Dim.enabled,"Replacement activates anti-burn");
check(BurnEye.install(root)==eye && root.children.size()==3,"Layout updates never duplicate the eye");
live.left=22;live.top=80;root.observer.draw.onPreDraw();check(layout.leftMargin+22==42 && layout.topMargin+22==100,"Eye follows native layout on the same frame");
live.alpha=1;root.observer.draw.onPreDraw();check(live.alpha==0 && eye.visibility==0,"Native alpha reset cannot reveal LIVE or hide the eye");
BurnEye.hide(root);check(eye.visibility==8 && live.alpha==1 && live.clickable && live.accessibility==0,"Leaving scope restores native controls");
check(BurnEye.install(root)==eye,"Returning reuses one eye");
live.visibility=8;root.observer.draw.onPreDraw();check(eye.visibility==0,"Native LIVE animation cannot hide the replacement on an allowed feed");
live.visibility=0;root.observer.draw.onPreDraw();check(eye.visibility==0,"Native slot return restores eye without restart");
BurnEye.hide(root);live.setTag(0x7f0a374f,"dm_notice");check(BurnEye.install(root)==null,"Notifications cannot become an eye anchor");
live.setTag(0x7f0a374f,"live");check(BurnEye.install(root)==eye,"A valid LIVE slot can be rebound");
Dim.configured=false;check(BurnEye.install(root)==null && eye.visibility==8 && live.alpha==1,"Disabling configuration restores LIVE");
Dim.configured=true;android.animation.ValueAnimator.manual=true;BurnEye.install(root);BurnEye.hide(root);android.animation.ValueAnimator old=android.animation.ValueAnimator.last;
check(eye.visibility==0&&!eye.clickable,"Leaving eye fades without taking taps");root.observer.draw.onPreDraw();check(eye.visibility==0,"Pre-draw does not interrupt fade");BurnEye.install(root);old.finish();check(eye.visibility==0&&eye.clickable&&old.cancelled,"Reentry cancels stale disappearance");android.animation.ValueAnimator.manual=false;
TextView friends=new TextView(new android.content.Context());friends.setText("Друзья");friends.top=50;friends.height=40;root.addView(friends,new FrameLayout.LayoutParams(100,40));
eye=BurnEye.installFriends(root);layout=(FrameLayout.LayoutParams)eye.getLayoutParams();check(layout.leftMargin==4&&layout.topMargin==96,"Friends eye below the header");check(live.alpha==1,"Friends placement does not steal native icons");
View back=new View();back.description="Назад";back.left=4;back.top=40;back.width=40;back.height=40;root.addView(back,new FrameLayout.LayoutParams(40,40));
eye=BurnEye.installVideo(root);layout=(FrameLayout.LayoutParams)eye.getLayoutParams();check(layout.leftMargin==65&&layout.topMargin==38,"Detail eye at the fixed spot right of the back arrow");check(back.alpha==1&&back.description.equals("Назад"),"Back arrow remains native");

}}
"""
FAIL_HARNESS="""package cat.narezany.margyt;import android.view.*;import android.widget.*;
public class EyeFailHarness {public static void main(String[] a)throws Exception{
android.graphics.BitmapFactory.fail=true;FrameLayout root=new FrameLayout();root.width=400;root.height=900;
TextView title=new TextView(new android.content.Context());title.setText("Рекомендации");title.left=130;title.top=40;title.width=120;title.height=40;root.addView(title,new FrameLayout.LayoutParams(120,40));
View nativeLive=new View();nativeLive.setTag("live");nativeLive.top=40;nativeLive.left=4;root.addView(nativeLive,new FrameLayout.LayoutParams(40,40));View eye=BurnEye.install(root);if(eye==null)throw new AssertionError("eye");
java.lang.reflect.Method draw=eye.getClass().getDeclaredMethod("onDraw",android.graphics.Canvas.class);draw.setAccessible(true);
draw.invoke(eye,new android.graphics.Canvas());draw.invoke(eye,new android.graphics.Canvas());
if(android.graphics.Canvas.bitmaps!=0)throw new AssertionError("no PNG expected");
if(android.graphics.BitmapFactory.decoded!=2)throw new AssertionError("unreadable art must be tried once, not every frame: "+android.graphics.BitmapFactory.decoded);
}}
"""
class BurnEyeRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('java') and shutil.which('javac'),'JDK required')
 def test_eye_replaces_live_and_restores_on_navigation(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   sources['cat/narezany/margyt/BurnEye.java']=(ROOT/'inject/java/cat/narezany/margyt/BurnEye.java').read_text(encoding='utf-8')
   sources['cat/narezany/margyt/SettingsGlyph.java']=(ROOT/'inject/java/cat/narezany/margyt/SettingsGlyph.java').read_text(encoding='utf-8')
   sources['cat/narezany/margyt/EyeHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,value in sources.items():
    target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(value,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.EyeHarness'],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)
 @unittest.skipUnless(shutil.which('java') and shutil.which('javac'),'JDK required')
 def test_unreadable_art_falls_back_to_vector_eye(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   sources['cat/narezany/margyt/BurnEye.java']=(ROOT/'inject/java/cat/narezany/margyt/BurnEye.java').read_text(encoding='utf-8')
   sources['cat/narezany/margyt/SettingsGlyph.java']=(ROOT/'inject/java/cat/narezany/margyt/SettingsGlyph.java').read_text(encoding='utf-8')
   sources['cat/narezany/margyt/EyeFailHarness.java']=FAIL_HARNESS
   for name,value in sources.items():
    target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(value,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.EyeFailHarness'],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)
