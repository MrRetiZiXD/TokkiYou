"""Execute production repost placement logic without hiding native friend information."""
import pathlib,shutil,subprocess,tempfile,unittest
from test_archive_features_runtime import method
ROOT=pathlib.Path(__file__).resolve().parents[1]
class RepostRegressionTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_native_information_and_icon_restore(self):
  code=(ROOT/'inject/java/cat/narezany/tokkiyou/Repost.java').read_text(encoding='utf-8')
  state=code[code.index('private static final class State'):code.index(' static boolean enabled()')]
  harness='''package cat.narezany.tokkiyou;
import android.view.*;import java.lang.ref.WeakReference;import java.util.*;
class RepostHarness {
 static boolean active=true;static boolean enabled(){return active;}static int position(){return 0;}
 static Map<View,WeakReference<Object>> anchors=new HashMap<>();
 static int dp(View v,int n){return n;}static ViewGroup container(View a,View b){return null;}static void place(State s){}
 static class Button extends View {WeakReference<View> anchor;Button(Object c,State s){}void refresh(Object c){}}
 '''+state+method('Repost.java','private static void apply(')+'''
 static class Post {public String getAid(){return "123";}}
 static class Item {public Post getAweme(){return new Post();}}
 static class Component {public Object item=new Item();public View.OnClickListener LLLLILI=v->{};public android.widget.ImageView LLLJIL=new android.widget.ImageView();}
 static class VideoFavoriteAssem extends Component {}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  View nativeRow=new View(),favorite=new View();Component component=new Component();
  anchors.put(favorite,new WeakReference<Object>(new VideoFavoriteAssem()));
  State state=new State(component,nativeRow);Button button=new Button(null,state);state.button=new WeakReference<>(button);
  apply(state);check(nativeRow.getVisibility()==View.VISIBLE,"friend repost row must remain visible");
  check(button.getVisibility()==View.VISIBLE,"custom action available");check(component.LLLJIL.getVisibility()==View.INVISIBLE,"only duplicate icon hidden");
  active=false;apply(state);check(component.LLLJIL.getVisibility()==View.VISIBLE,"native icon restored");check(button.getVisibility()==View.GONE,"custom action disabled");
  active=true;state.wanted=View.GONE;apply(state);check(nativeRow.getVisibility()==View.GONE&&button.getVisibility()==View.VISIBLE,"custom repost independent of native informational row");
 }
}'''
  sources={'cat/narezany/tokkiyou/RepostHarness.java':harness,
  'android/view/View.java':'''package android.view;import java.util.*;public class View {public static final int VISIBLE=0,INVISIBLE=4,GONE=8;int visible;Map<Integer,Object> tags=new HashMap<>();public int getVisibility(){return visible;}public void setVisibility(int v){visible=v;}public Object getContext(){return null;}public boolean isShown(){return visible==VISIBLE;}public boolean isAttachedToWindow(){return true;}public Object getTag(int i){return tags.get(i);}public void setTag(int i,Object o){tags.put(i,o);}public interface OnClickListener{void onClick(View v);}}''',
  'android/view/ViewGroup.java':'package android.view;public class ViewGroup extends View {public static class LayoutParams{public LayoutParams(int w,int h){}}public void addView(View v,LayoutParams p){}public interface Listener{void run(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}public void addOnLayoutChangeListener(Listener l){}}',
  'android/view/Gravity.java':'package android.view;public class Gravity{public static final int TOP=1,LEFT=2;}',
  'android/widget/ImageView.java':'package android.widget;public class ImageView extends android.view.View{}',
  'android/widget/FrameLayout.java':'package android.widget;public class FrameLayout extends android.view.ViewGroup{public static class LayoutParams extends android.view.ViewGroup.LayoutParams{public int gravity;public LayoutParams(int w,int h){super(w,h);}}}',
  'cat/narezany/tokkiyou/Diary.java':'package cat.narezany.tokkiyou;class Diary{static void note(String s){}}',
  'cat/narezany/tokkiyou/FeedRail.java':'package cat.narezany.tokkiyou;class FeedRail{static void refreshFavorite(android.view.View v){}}'}
  sources['cat/narezany/tokkiyou/NativeRepostVisual.java']='package cat.narezany.tokkiyou;class NativeRepostVisual{static void remember(Object o){}}'
  native=(ROOT/'inject/java/cat/narezany/tokkiyou/NativeRead.java').read_text(encoding='utf-8')
  sources['cat/narezany/tokkiyou/NativeRead.java']=native.replace(method('NativeRead.java','static Object item('),'static Object item(Object c){return field(c,"item");}')
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
   result=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',directory]+[str(work/n) for n in sources],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',directory,'cat.narezany.tokkiyou.RepostHarness'],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
