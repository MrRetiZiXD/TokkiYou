"""Run the real "is a restart still owed" bookkeeping against preferences in memory."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
 'android/content/pm/ApplicationInfo.java': 'package android.content.pm;public class ApplicationInfo{public String dataDir="/nowhere";}',
 'android/content/SharedPreferences.java': 'package android.content;public interface SharedPreferences{java.util.Map<String,?> getAll();}',
 'android/content/Context.java': '''package android.content;public class Context{public static final int MODE_PRIVATE=0;
  public java.util.Map<String,Store> files=new java.util.HashMap<>();
  public static class Store implements SharedPreferences{public java.util.Map<String,Object> values=new java.util.HashMap<>();public java.util.Map<String,?> getAll(){return new java.util.HashMap<>(values);}}
  public android.content.pm.ApplicationInfo getApplicationInfo(){return new android.content.pm.ApplicationInfo();}
  public SharedPreferences getSharedPreferences(String name,int mode){Store s=files.get(name);if(s==null){s=new Store();files.put(name,s);}return s;}
  public Store of(String name){return (Store)getSharedPreferences(name,0);}}''',
 'cat/narezany/tokkiyou/TokkiYou.java': 'package cat.narezany.tokkiyou;class TokkiYou{static final String PREFS="tokkiyou";}',
 'cat/narezany/tokkiyou/Diary.java': 'package cat.narezany.tokkiyou;class Diary{static void note(String s){throw new AssertionError(s);}}',
 'cat/narezany/tokkiyou/AccountAppearance.java': 'package cat.narezany.tokkiyou;import android.content.*;class AccountAppearance{static SharedPreferences prefs(Context c){return c.getSharedPreferences("look",0);}static String scope(Context c){return "1";}}',
}
HARNESS = '''package cat.narezany.tokkiyou;import android.content.Context;
public class UnsavedHarness{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 public static void main(String[] a){
  Context c=new Context();java.util.Map<String,Object> main=c.of("tokkiyou").values,look=c.of("look").values;
  main.put("ads",true);main.put("visits",3);look.put("accent",7);
  Unsaved.look(c);
  // a switch that was written down: off, and on again
  Unsaved.look(c);main.put("ads",false);check(Unsaved.after(c),"a changed switch owes a restart");
  Unsaved.look(c);main.put("ads",true);check(!Unsaved.after(c),"put back, it owes nothing");
  // a switch never written before shows its default, and the first write is the opposite
  Unsaved.look(c);main.put("hdr",false);check(Unsaved.after(c),"first write of a switch");
  Unsaved.look(c);main.put("hdr",true);check(!Unsaved.after(c),"and back to what it showed");
  // two at once: both have to come back
  Unsaved.look(c);main.put("ads",false);check(Unsaved.after(c),"one");
  Unsaved.look(c);look.put("accent",9);check(Unsaved.after(c),"two");
  Unsaved.look(c);main.put("ads",true);check(Unsaved.after(c),"one back is not both");
  Unsaved.look(c);look.put("accent",7);check(!Unsaved.after(c),"both back");
  // what moves on its own between taps is not a setting
  main.put("visits",4);
  Unsaved.look(c);main.put("ads",false);check(Unsaved.after(c),"changed");
  Unsaved.look(c);main.put("ads",true);check(!Unsaved.after(c),"a counter that moved by itself does not keep the bar up");
  // a number that was never there has nothing to go back to but being gone
  Unsaved.look(c);main.put("fps",60);check(Unsaved.after(c),"new number");
  Unsaved.look(c);main.put("fps",30);check(Unsaved.after(c),"another number is not the start");
  Unsaved.look(c);main.remove("fps");check(!Unsaved.after(c),"gone again is the start");
  // a change kept somewhere else cannot be seen to be undone
  Unsaved.look(c);check(Unsaved.after(c),"nothing moved here, so it moved elsewhere");
  Unsaved.look(c);main.put("ads",false);Unsaved.after(c);
  Unsaved.look(c);main.put("ads",true);check(Unsaved.after(c),"and after that the bar stays");
  System.out.println("restart bookkeeping passed");
 }
}'''
class UnsavedRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_putting_things_back_takes_the_bar_away(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory); sources = dict(STUBS)
            sources['cat/narezany/tokkiyou/Unsaved.java'] = (ROOT / 'inject/java/cat/narezany/tokkiyou/Unsaved.java').read_text(encoding='utf-8')
            sources['cat/narezany/tokkiyou/UnsavedHarness.java'] = HARNESS
            __import__("android_doubles").with_mod(sources)
            for name, content in sources.items():
                target = work / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(content, encoding='utf-8')
            built = subprocess.run(['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-d', str(work)] + [str(work / name) for name in sources], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            run = subprocess.run(['java', '-cp', str(work), 'cat.narezany.tokkiyou.UnsavedHarness'], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stderr)

if __name__ == '__main__':
    unittest.main()
