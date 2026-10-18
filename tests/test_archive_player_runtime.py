"""Drive the actual native player bridge with a deterministic clock and recycled controllers."""
import pathlib,shutil,subprocess,tempfile,unittest
import test_account_appearance_runtime as base
ROOT=pathlib.Path(__file__).resolve().parents[1]
class ArchivePlayerTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_account_resume_seek_and_cancellation(self):
  sources={k:base.STUBS[k] for k in ['android/content/Context.java','android/content/SharedPreferences.java']}
  sources['android/content/SharedPreferences.java']=sources['android/content/SharedPreferences.java'].replace('boolean contains(String k);','float getFloat(String k,float d);boolean contains(String k);')
  sources['android/content/Context.java']=sources['android/content/Context.java'].replace('public boolean contains(String k){','public float getFloat(String k,float d){return values.containsKey(k)?((Number)values.get(k)).floatValue():d;}public boolean contains(String k){')
  sources.update({
 'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
 'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now;public static long uptimeMillis(){return now;}}',
 'android/os/Handler.java':'package android.os;public class Handler{public static java.util.List<Runnable> queue=new java.util.ArrayList<>();public Handler(Looper l){}public void post(Runnable r){r.run();}public void postDelayed(Runnable r,long d){queue.add(r);}public void removeCallbacks(Runnable r){queue.remove(r);}public static void tick(long now){SystemClock.now=now;java.util.List<Runnable> old=new java.util.ArrayList<>(queue);queue.clear();for(Runnable r:old)r.run();}}',
 'android/text/InputType.java':'package android.text;public class InputType{public static int TYPE_CLASS_NUMBER=2,TYPE_NUMBER_FLAG_DECIMAL=8192;}',
 'android/widget/EditText.java':'package android.widget;public class EditText{String text="";public void setText(String s){text=s;}public CharSequence getText(){return text;}public void setInputType(int i){}}',
 'cat/narezany/tokkiyou/TokkiYou.java':'package cat.narezany.tokkiyou;class TokkiYou{static android.content.Context c=new android.content.Context();static android.content.Context context(){return c;}}',
 'cat/narezany/tokkiyou/AccountAppearance.java':'package cat.narezany.tokkiyou;class AccountAppearance{static String account="111";static android.content.SharedPreferences prefs(android.content.Context c){return c.getSharedPreferences(account,0);}}',
 'cat/narezany/tokkiyou/Diary.java':'package cat.narezany.tokkiyou;class Diary{static void note(String s){}}',
 'cat/narezany/tokkiyou/Screen.java':'package cat.narezany.tokkiyou;class Screen{static int errors;static void say(String s){errors++;}}',
 'cat/narezany/tokkiyou/Text.java':'package cat.narezany.tokkiyou;class Text{static String SPEED_RANGE="",PLAYER_NOT_READY="",CUSTOM_SPEED="",PLAYER_SPEED="",APPLY="",CLOSE="";}',
 'cat/narezany/tokkiyou/Skin.java':'package cat.narezany.tokkiyou;class Skin{static Skin remembered(android.content.Context c){return new Skin();}}',
 'cat/narezany/tokkiyou/ModDialog.java':'package cat.narezany.tokkiyou;class ModDialog{interface Choice{void on(Object d,int i);}static class Builder{Builder(android.content.Context c){}Builder setItems(String[] s,Choice ch){return this;}void show(){}}}',
 'cat/narezany/tokkiyou/Panel.java':'package cat.narezany.tokkiyou;class Panel{static Panel with(android.content.Context c,Skin s,String t){return new Panel();}android.widget.EditText field(String s){return new android.widget.EditText();}Panel primaryKeepOpen(String s,Runnable r){return this;}Panel quiet(String s,Runnable r){return this;}void show(){}void close(){}}'
  })
  for name in ['NativeRead','PlayerTools']:sources['cat/narezany/tokkiyou/'+name+'.java']=(ROOT/'inject/java/cat/narezany/tokkiyou'/(name+'.java')).read_text(encoding='utf-8')
  sources['cat/narezany/tokkiyou/Test.java']=HARNESS
  with tempfile.TemporaryDirectory() as folder:
   root=pathlib.Path(folder)
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
   result=subprocess.run(['javac','-encoding','UTF-8','-source','8','-target','8','-d',str(root)]+[str(p) for p in root.rglob('*.java')],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',str(root),'cat.narezany.tokkiyou.Test'],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
HARNESS=r'''package cat.narezany.tokkiyou;import java.util.*;
public class Test{static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
public static class Post{String aid;Post(String s){aid=s;}public String getAid(){return aid;}}
public static class Manager{float speed=1;int speedWrites;long position=45000,duration=60000;List<Float> seeks=new ArrayList<>();public void LJJIJL(float v){speed=v;speedWrites++;}public long getDuration(){return duration;}public long getCurrentPosition(){return position;}public void seek(float progress){seeks.add(progress);}}
public static class Controller{Post post;Manager manager=new Manager();Controller(String s){post=new Post(s);}public Post LLJJJIL(){return post;}public Manager getPlayerManager(){return manager;}}
public static void main(String[] args){Controller a=new Controller("111");PlayerTools.resumed(a,"different");check(a.manager.speedWrites==0,"recycled stale resume ignored");PlayerTools.resumed(a,"111");check(a.manager.speed==1,"default native speed");PlayerTools.setSpeed(1.5f);check(a.manager.speed==1&&PlayerTools.speed()==1.5f,"hold setting leaves normal playback intact");PlayerTools.applyHoldRate();check(a.manager.speed==1.5f,"hold uses configured speed");a.manager.speed=2;PlayerTools.resumed(a,"111");check(a.manager.speed==2,"same-video resume preserves edge lock");Controller b=new Controller("222");PlayerTools.resumed(b,"222");check(b.manager.speed==1,"new video stays at native speed");
PlayerTools.seek(false,false);check(Math.abs(b.manager.seeks.get(0)-58.33333)<.001,"back ten seconds");PlayerTools.seek(true,false);check(Math.abs(b.manager.seeks.get(1)-91.66667)<.001,"forward ten seconds");b.manager.seeks.clear();PlayerTools.seek(false,true);check(b.manager.seeks.get(0)==75,"reverse starts at current time without jump");for(int i=1;i<=72;i++)android.os.Handler.tick(i*140);float previous=75;for(float value:b.manager.seeks){check(value<=previous&&previous-value<.25f,"monotonic bounded reverse steps");previous=value;}check(Math.abs(previous-58.33333)<.001&&android.os.Handler.queue.isEmpty(),"reverse stops exactly at target");
PlayerTools.seek(false,true);int count=b.manager.seeks.size();PlayerTools.cancelSmooth();android.os.Handler.tick(12000);check(b.manager.seeks.size()==count,"screen pause cancels seeks");PlayerTools.seek(false,true);Controller c=new Controller("333");PlayerTools.resumed(c,"333");count=b.manager.seeks.size();android.os.Handler.tick(14000);check(b.manager.seeks.size()==count,"new post cancels old player task");
AccountAppearance.account="second";PlayerTools.accountChanged();check(PlayerTools.speed()==2,"second account hold default");int errors=Screen.errors;PlayerTools.seek(true,false);check(Screen.errors==errors+1,"account switch cannot seek previous owner player");PlayerTools.resumed(c,"333");check(c.manager.speed==1,"second account new player reset");AccountAppearance.account="111";PlayerTools.accountChanged();check(PlayerTools.speed()==1.5f,"first account retains speed");
}}
'''
