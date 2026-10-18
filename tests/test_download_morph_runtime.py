"""Execute the actual flower contour, progress/check stroke and geometry endpoints."""
import pathlib,shutil,subprocess,tempfile,unittest
from test_screen_runtime import STUBS as BASE
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS=dict(BASE)
STUBS.pop('cat/narezany/margyt/DownloadMark.java')
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public static final int VISIBLE=0;', 'public static final int VISIBLE=0,INVISIBLE=4,IMPORTANT_FOR_ACCESSIBILITY_NO=2,ACCESSIBILITY_LIVE_REGION_POLITE=1;public void setVisibility(int v){}public float alpha=1;public int width=260,height=50;public void setWillNotDraw(boolean b){}public void setContentDescription(String s){}public void setAccessibilityLiveRegion(int n){}public void setImportantForAccessibility(int n){}public int getTop(){return 32;}').replace('public void setAlpha(float v){}','public void setAlpha(float v){alpha=v;}').replace('public int getWidth(){return 300;}public int getHeight(){return 50;}', 'public int getWidth(){return width;}public int getHeight(){return height;}')
STUBS['android/graphics/Paint.java']='''package android.graphics;public class Paint {public static int ANTI_ALIAS_FLAG=1;public enum Style {FILL,STROKE}public enum Cap {ROUND}public enum Join {ROUND}public int colour;public float thickness;public Paint(int f){}public void setColor(int c){colour=c;}public void setStyle(Style s){}public void setStrokeCap(Cap c){}public void setStrokeJoin(Join j){}public void setStrokeWidth(float n){thickness=n;}public float measureText(String s){return s.length()*7;}}'''
STUBS['android/graphics/Path.java']='''package android.graphics;public class Path {public java.util.List<float[]> points=new java.util.ArrayList<>();public void reset(){points.clear();}public void moveTo(float x,float y){points.add(new float[]{x,y});}public void lineTo(float x,float y){points.add(new float[]{x,y});}public void close(){}}'''
STUBS['android/graphics/Canvas.java']='''package android.graphics;public class Canvas {public java.util.List<java.util.List<float[]>> paths=new java.util.ArrayList<>();public java.util.List<Integer> colours=new java.util.ArrayList<>();public void drawPath(Path p,Paint paint){paths.add(new java.util.ArrayList<>(p.points));colours.add(paint.colour);}}'''
STUBS['android/os/SystemClock.java']='package android.os;public class SystemClock {public static long now=1000;public static long uptimeMillis(){return now;}}'
STUBS['cat/narezany/margyt/Motion.java']='package cat.narezany.margyt;class Motion {static boolean animate=true;static boolean enabled(android.content.Context c){return animate;}}'
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public static final int VISIBLE=0;', 'public static final int VISIBLE=0,INVISIBLE=4;')
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public void setWillNotDraw', 'public int invalidations;public boolean isShown(){return true;}public void postInvalidateOnAnimation(){invalidations++;}public void setWillNotDraw')
HARNESS='''package cat.narezany.margyt;import android.graphics.*;import android.widget.*;
public class MorphHarness {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}static void near(float a,float b,String s){check(Math.abs(a-b)<0.01,s);}
 public static void main(String[] args){
  DownloadMark surface=new DownloadMark(new android.content.Context(),0xff1c2c24);Canvas loading=new Canvas();surface.onDraw(loading);check(loading.paths.size()==1 && loading.paths.get(0).size()==512,"pill surface is a continuous vector contour");
  TextView label=new TextView(new android.content.Context());ProgressBar progress=new ProgressBar(new android.content.Context(),null,1);progress.height=4;surface.begin(260,label,progress);
  Canvas start=new Canvas();surface.onDraw(start);check(start.paths.size()==2 && progress.alpha==0,"native stroke handed to same surface without double drawing");
  java.util.List<float[]> initial=start.paths.get(1);near(initial.get(0)[1],initial.get(1)[1],"progress starts horizontal");near(initial.get(1)[1],initial.get(2)[1],"progress starts horizontal");
  surface.morph(1);surface.width=surface.height=56;Canvas done=new Canvas();surface.onDraw(done);check(label.alpha==0 && done.colours.get(0)==Accent.colour(),"completion uses accent and fades label");
  java.util.List<float[]> mark=done.paths.get(1);near(mark.get(0)[0],18,"check start");near(mark.get(64)[1],34,"check joint");near(mark.get(128)[0],39,"check end");
  int peaks=0;java.util.List<float[]> flower=done.paths.get(0);for(int i=0;i<flower.size();i++){float[] a=flower.get((i+flower.size()-1)%flower.size()),b=flower.get(i),c=flower.get((i+1)%flower.size());double ra=Math.hypot(a[0]-28,a[1]-28),rb=Math.hypot(b[0]-28,b[1]-28),rc=Math.hypot(c[0]-28,c[1]-28);if(rb>ra && rb>rc)peaks++;}check(peaks==8,"reference has eight broad petals");
  for(int f=0;f<=100;f++){float t=f/100f,w=DownloadMorph.mix(260,56,t),h=DownloadMorph.mix(50,56,t);for(int i=0;i<128;i++){float angle=(float)(i*Math.PI*2/128),ray=DownloadMorph.radius(angle,260,50,1,t);check(Float.isFinite(ray)&&ray>0,"all intermediate contours finite");check(Math.abs(Math.cos(angle)*ray)<=w/2+0.1 && Math.abs(Math.sin(angle)*ray)<=h/2+0.1,"contour fits window throughout transform");}}
  near(DownloadWave.offset(10,1,0,1),3,"Material wave amplitude 3 dp");near(DownloadWave.offset(50,1,0,1),3,"Material wavelength 40 dp");near(DownloadWave.amplitude(0.95f),0,"flatten near completion");near(DownloadWave.amplitude(0.5f),1,"wavy middle progress");
  DownloadMark wavy=new DownloadMark(new android.content.Context(),0xff1c2c24);ProgressBar waveMeter=new ProgressBar(new android.content.Context(),null,1);waveMeter.height=10;wavy.bind(waveMeter);wavy.progress(50);Canvas half=new Canvas();wavy.onDraw(half);check(half.paths.size()==3,"background, remaining track and active wave");java.util.List<float[]> before=half.paths.get(2);check(before.size()==129,"sampled wave");double min=1e9,max=-1e9;for(float[] point:before){min=Math.min(min,point[1]);max=Math.max(max,point[1]);}check(max-min>5.8,"visible 3 dp amplitude");
  wavy.begin(260,new TextView(new android.content.Context()),waveMeter);Canvas handoff=new Canvas();wavy.onDraw(handoff);java.util.List<float[]> after=handoff.paths.get(2);for(int i=0;i<129;i++){near(before.get(i)[0],after.get(i)[0],"handoff x continuity");near(before.get(i)[1],after.get(i)[1],"handoff y continuity");}
  wavy.width=wavy.height=56;wavy.morph(1);Canvas waveDone=new Canvas();wavy.onDraw(waveDone);near(waveDone.paths.get(1).get(64)[1],34,"wave morph check joint");
  Motion.animate=false;DownloadMark quiet=new DownloadMark(new android.content.Context(),0xff1c2c24);quiet.bind(waveMeter);quiet.progress(50);Canvas still=new Canvas();quiet.onDraw(still);java.util.List<float[]> flat=still.paths.get(2);for(float[] point:flat)near(point[1],flat.get(0)[1],"reduced motion flat track");check(quiet.invalidations==0,"no frame loop with animations disabled");
  check(DownloadMorph.colour(0xff24262b,0xffa4c9ff,0)==0xff24262b && DownloadMorph.colour(0xff24262b,0xffa4c9ff,1)==0xffa4c9ff,"colour endpoints exact");
 }
}'''
class DownloadMorphRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_surface_and_contour(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   for name in ('DownloadMark','DownloadMorph','DownloadWave'):sources['cat/narezany/margyt/'+name+'.java']=(ROOT/('inject/java/cat/narezany/margyt/'+name+'.java')).read_text(encoding='utf-8')
   sources['cat/narezany/margyt/MorphHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,content in sources.items():
    path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8')
   result=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/n) for n in sources],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.MorphHarness'],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
