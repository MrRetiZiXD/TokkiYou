"""The walk that is taken a little at a time: everything is reached, and no stretch runs long."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
 'android/os/SystemClock.java': 'package android.os;public class SystemClock{public static long now;public static long uptimeMillis(){return now;}}',
 'android/view/View.java': '''package android.view;public class View{public static java.util.ArrayDeque<Runnable> frames=new java.util.ArrayDeque<>();
  public boolean attached=true;public boolean isAttachedToWindow(){return attached;}public void postOnAnimation(Runnable r){frames.add(r);}}''',
 'android/view/ViewGroup.java': 'package android.view;public class ViewGroup extends View{public java.util.List<View> kids=new java.util.ArrayList<>();public int getChildCount(){return kids.size();}public View getChildAt(int i){return kids.get(i);}}',
 'cat/narezany/tokkiyou/Version.java': 'package cat.narezany.tokkiyou;final class Version{static final boolean TEST=false;}',
}
HARNESS = '''package cat.narezany.tokkiyou;import android.view.*;import android.os.SystemClock;import java.util.*;
public class SlicedHarness{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static ViewGroup tree(int wide,int deep,List<View> all){ViewGroup g=new ViewGroup();all.add(g);if(deep>0)for(int i=0;i<wide;i++)g.kids.add(tree(wide,deep-1,all));else for(int i=0;i<wide;i++){View v=new View();g.kids.add(v);all.add(v);}return g;}
 public static void main(String[] a){
  final List<View> all=new ArrayList<>();ViewGroup root=tree(6,3,all);              // 1555 views
  final Map<View,Integer> seen=new IdentityHashMap<>();final List<View> order=new ArrayList<>();
  // every view costs a tenth of a millisecond, so the whole screen is far more than one stretch
  final int[] ticks={0};
  Sliced.Visit slow=v->{Integer n=seen.get(v);seen.put(v,n==null?1:n+1);order.add(v);if(++ticks[0]%10==0)SystemClock.now++;};
  Sliced.walk(root,slow);
  check(!seen.isEmpty()&&seen.size()<all.size(),"the first stretch does some of it and not all: "+seen.size());
  int first=seen.size();check(first<=80,"a stretch stops when its time is up: "+first);
  check(order.get(0)==root&&order.get(1)==root.kids.get(0),"top of the screen first, in the order it is laid out");
  int frames=0;
  while(!View.frames.isEmpty()){Runnable next=View.frames.poll();long before=SystemClock.now;next.run();frames++;check(SystemClock.now-before<=4,"no stretch runs long");check(frames<1000,"it ends");}
  check(seen.size()==all.size(),"every view is reached: "+seen.size()+" of "+all.size());
  for(int n:seen.values())check(n==1,"and reached once");
  check(frames>10,"spread over many frames: "+frames);
  // asked again while under way: once more round when it is done, not two walks at once
  seen.clear();Sliced.walk(root,slow);Sliced.walk(root,slow);Sliced.walk(root,slow);
  while(!View.frames.isEmpty())View.frames.poll().run();
  for(View v:all)check(seen.get(v)==2,"asked three times while busy is two rounds, not three and not one");
  // a screen that has gone away is not walked to the end
  seen.clear();Sliced.walk(root,slow);root.attached=false;while(!View.frames.isEmpty())View.frames.poll().run();
  check(seen.size()<all.size(),"a screen that left is dropped");
  root.attached=true;seen.clear();Sliced.walk(root,slow);while(!View.frames.isEmpty())View.frames.poll().run();
  check(seen.size()==all.size(),"and can be walked again when it is back");
  System.out.println("sliced walk passed");
 }
}'''
class SlicedRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_everything_is_reached_a_little_at_a_time(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory); sources = dict(STUBS)
            for name in ('Sliced', 'Probe'):
                sources['cat/narezany/tokkiyou/%s.java' % name] = (ROOT / 'inject/java/cat/narezany/tokkiyou' / (name + '.java')).read_text(encoding='utf-8')
            sources['android/util/Log.java'] = 'package android.util;public class Log{public static int e(String t,String m){return 0;}}'
            sources['cat/narezany/tokkiyou/SlicedHarness.java'] = HARNESS
            for name, content in sources.items():
                target = work / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(content, encoding='utf-8')
            built = subprocess.run(['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-d', str(work)] + [str(work / name) for name in sources], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            run = subprocess.run(['java', '-cp', str(work), 'cat.narezany.tokkiyou.SlicedHarness'], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stderr)

if __name__ == '__main__':
    unittest.main()
