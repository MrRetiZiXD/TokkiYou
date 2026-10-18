"""Run the actual visible-date row over caption truncation and cell changes."""
import pathlib,shutil,subprocess,tempfile,unittest
from test_burn_eye_runtime import STUBS as BASE
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS=dict(BASE)
STUBS['android/view/ViewTreeObserver.java']='package android.view;public class ViewTreeObserver {public OnPreDrawListener draw;public interface OnPreDrawListener {boolean onPreDraw();}public boolean isAlive(){return true;}public void addOnPreDrawListener(OnPreDrawListener l){draw=l;}public void removeOnPreDrawListener(OnPreDrawListener l){if(draw==l)draw=null;}}'
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public static final int VISIBLE=0;', 'public static final int VISIBLE=0,IMPORTANT_FOR_ACCESSIBILITY_NO=2;public void post(Runnable r){r.run();}')
STUBS['android/widget/TextView.java']=STUBS['android/widget/TextView.java'].replace('public void setTextColor(int c)', 'public void setShadowLayer(float a,float b,float c,int d){}public void setTextColor(int c)')
STUBS['cat/narezany/margyt/Dates.java']='package cat.narezany.margyt;class Dates {static boolean enabled=true;static boolean isEnabled(){return enabled;}}'
STUBS['cat/narezany/margyt/DateInfo.java']='package cat.narezany.margyt;class DateInfo{static void open(android.view.View r,android.view.View c){}}'
STUBS['cat/narezany/margyt/Fonts.java']='package cat.narezany.margyt;class Fonts {static void apply(android.widget.TextView v){}}'
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public void post(Runnable r)', 'public boolean focused=true;public boolean hasWindowFocus(){return focused;}public void post(Runnable r)')
STUBS['android/os/SystemClock.java']='package android.os;public class SystemClock{public static long now=10000;public static long uptimeMillis(){return now;}}'
STUBS['cat/narezany/margyt/Dim.java']='package cat.narezany.margyt;class Dim {static boolean panel;static boolean panelOpen(android.view.View root){return panel;}static boolean panelContent(android.view.View v){return false;}}'
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public class View implements ViewParent {','public class View implements ViewParent {public View nativeDate;public View findViewById(int id){return id==0x7f0a93a5?nativeDate:null;}').replace('public void setAlpha(float v){}','public void setAlpha(float v){alpha=v;}')
STUBS['android/widget/TextView.java']=STUBS['android/widget/TextView.java'].replace('public void setText(String s)', 'public void setText(CharSequence s){text=s==null?null:s.toString();}public void setText(String s)')
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public class View implements ViewParent {','public class View implements ViewParent {public boolean clipped;public boolean getGlobalVisibleRect(android.graphics.Rect r){r.left=left;r.top=top;r.right=left+width;r.bottom=top+height;return !clipped&&isShown()&&isAttachedToWindow();}public int pl,pt,pr,pb;public int getPaddingLeft(){return pl;}public int getPaddingTop(){return pt;}public int getPaddingRight(){return pr;}public int getPaddingBottom(){return pb;}public void setPadding(int a,int b,int c,int d){pl=a;pt=b;pr=c;pb=d;}')
STUBS['android/graphics/Rect.java']='package android.graphics;public class Rect{public int left,top,right,bottom;public int width(){return right-left;}public int height(){return bottom-top;}}'
STUBS['cat/narezany/margyt/Skin.java']='package cat.narezany.margyt;class Skin{int text=0xffffff;static Skin remembered(android.content.Context c){return new Skin();}}'
HARNESS='''package cat.narezany.margyt;import android.view.*;import android.widget.*;
public class DateHarness {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  FrameLayout root=new FrameLayout();root.width=400;root.height=900;
  TextView caption=new TextView(new android.content.Context());caption.width=300;caption.height=100;caption.left=16;caption.top=600;caption.setText("Short caption");root.addView(caption,new FrameLayout.LayoutParams(300,100));
  TextView name=new TextView(new android.content.Context());name.width=90;name.height=24;name.left=16;name.top=570;name.setText("Author");root.addView(name,new FrameLayout.LayoutParams(90,24));
  TextView nativeDate=new TextView(new android.content.Context());nativeDate.setText("Yesterday");nativeDate.alpha=.8f;root.addView(nativeDate,new FrameLayout.LayoutParams(80,24));root.nativeDate=nativeDate;
  DateOverlay.bind(caption,"01.01.2021","Author");TextView date=nativeDate;
  check(root.getChildCount()==3 && date.text.equals("01.01.2021") && date.alpha==.6f,"native date reused without an overlay");
  android.os.SystemClock.now+=300;Dim.panel=true;DateOverlay.update();check(date.visibility==8,"no date in comments");android.os.SystemClock.now+=300;Dim.panel=false;DateOverlay.update();check(date.visibility==0,"date returns after comments");
  Dates.enabled=false;DateOverlay.update();check(date.text.equals("Yesterday")&&date.alpha==.8f,"native content and alpha restored when disabled");Dates.enabled=true;DateOverlay.update();check(date.text.equals("01.01.2021"),"enabled without reload");
  caption.visibility=8;root.observer.draw.onPreDraw();check(date.visibility==0,"empty/hidden caption does not hide author date");root.visibility=8;DateOverlay.update();check(date.visibility==8,"hidden author container hides date");root.visibility=0;caption.visibility=0;
  DateOverlay.bind(caption,"02.01.2021");check(root.getChildCount()==3 && date.text.equals("02.01.2021"),"next post reuses native element");
  root.focused=false;DateOverlay.update();check(date.visibility==0,"recents/window focus loss cannot erase date");root.focused=true;
  root.observer=new ViewTreeObserver();DateOverlay.resume(root);check(root.observer.draw!=null && date.visibility==0,"resume reconnects replaced observer");
  TextView replacement=new TextView(new android.content.Context());replacement.setText("Native replacement");root.removeView(date);root.addView(replacement,new FrameLayout.LayoutParams(80,24));root.nativeDate=replacement;
  DateOverlay.resume(root);date=replacement;check(date.text.equals("02.01.2021") && date.alpha==.6f,"resume finds recreated native row");
  DateOverlay.bind(caption,null);check(root.getChildCount()==3 && date.text.equals("Native replacement"),"unknown timestamp restores native element");
  FrameLayout page=new FrameLayout();page.width=400;page.height=900;FrameLayout photo=new FrameLayout();photo.width=240;photo.height=40;photo.setTag(0x7e0d0334,"photo");page.addView(photo,new FrameLayout.LayoutParams(240,40));
  DateOverlay.register(photo,"03.01.2021");check(photo.getChildCount()==1,"photo without native slot receives one date");TextView photoDate=(TextView)photo.getChildAt(0);check(photoDate.text.equals("03.01.2021")&&photoDate.alpha==.6f,"real photo date, same opacity");int padding=photo.getPaddingBottom();DateOverlay.update();check(photo.getPaddingBottom()==padding&&photo.getChildCount()==1,"no repeated reserve or duplicate row");
  FrameLayout next=new FrameLayout();next.width=240;next.height=40;next.clipped=true;next.setTag(0x7e0d0334,"next");page.addView(next,new FrameLayout.LayoutParams(240,40));DateOverlay.register(next,"04.01.2021");check(photoDate.text.equals("03.01.2021")&&next.getChildCount()==0,"prefetch cannot select next date");photo.clipped=true;next.clipped=false;android.os.SystemClock.now+=300;DateOverlay.update();check(photo.getChildCount()==0&&photo.getPaddingBottom()==0&&next.getChildCount()==1,"visible post change restores previous row");check(((TextView)next.getChildAt(0)).text.equals("04.01.2021"),"new visible photo has its date");Dates.enabled=false;DateOverlay.update();check(next.getChildCount()==0&&next.getPaddingBottom()==0,"disabled restores native layout");
  System.out.println("visible-date lifecycle checks passed");
 }
}'''
class DateOverlayRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_visible_caption_date(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   sources['cat/narezany/margyt/DateOverlay.java']=(ROOT/'inject/java/cat/narezany/margyt/DateOverlay.java').read_text(encoding='utf-8')
   sources['cat/narezany/margyt/DateHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,content in sources.items():
    target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.DateHarness'],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)
