import pathlib,unittest,tempfile,subprocess,shutil
import sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tokkiyou import dexpatch
class NativeFlagsTests(unittest.TestCase):
 def test_windows_case_collision_suffix_is_patched(self):
  with tempfile.TemporaryDirectory() as folder:
   file=pathlib.Path(folder)/'09tK.1.smali'
   file.write_text('.class public final LX/09tK;\n.super Ljava/lang/Object;\n.method public final LIZJ(ILjava/lang/String;ZZ)Z\n    .registers 13\n    const/4 v0, 0x0\n    return v0\n.end method\n',encoding='utf-8')
   self.assertEqual({dexpatch.FLAG_LABEL:1},dexpatch.rewrite_flag_reader(folder))
   self.assertIn('invoke-static/range {p2 .. p2}, Lcat/narezany/tokkiyou/Flags;->forcedBoolean(Ljava/lang/String;)Ljava/lang/Boolean;',file.read_text())
 def test_a_reader_with_no_register_to_spare_stops_the_build(self):
  with tempfile.TemporaryDirectory() as folder:
   file=pathlib.Path(folder)/'09tK.smali'
   file.write_text('.class public final LX/09tK;\n.super Ljava/lang/Object;\n.method public final LJIILIIL(IJLjava/lang/String;Z)J\n    .registers 7\n    return-wide p2\n.end method\n',encoding='utf-8')
   with self.assertRaises(RuntimeError):dexpatch.rewrite_flag_reader(folder)
 def test_feed_heart_tint_uses_heart_hook_before_generic_accent(self):
  with tempfile.TemporaryDirectory() as folder:
   file=pathlib.Path(folder)/'0HCj.smali'
   file.write_text('.class public LX/0HCj;\n.super Ljava/lang/Object;\n.method public tint(Lcom/bytedance/tux/icon/TuxIconView;I)V\n .registers 3\n invoke-virtual {p1, p2}, Lcom/bytedance/tux/icon/TuxIconView;->setTintColor(I)V\n return-void\n.end method\n',encoding='utf-8')
   anchored=dexpatch.rewrite_anchored(folder)
   accent=dexpatch.rewrite_accent(folder)
   changed=file.read_text()
   self.assertEqual(1,anchored['TuxIconView->setTintColor (in the class marked LX/0HCj;)'])
   self.assertEqual(0,accent.get('TuxIconView->setTintColor(I)V',0))
   self.assertIn('Heart;->setTintColor(Lcom/bytedance/tux/icon/TuxIconView;I)V',changed)
   self.assertNotIn('Accent;->setTintColor',changed)
 @unittest.skipUnless(shutil.which('javac'),'JDK required')
 def test_the_table_of_answers_follows_the_switches(self):
  sources={
   'android/content/Context.java':'package android.content;import java.util.*;public class Context {public static int MODE_PRIVATE=0;public static Map<String,SharedPreferences> files=new HashMap<>();public SharedPreferences getSharedPreferences(String n,int m){return files.get(n);}}',
   'android/content/SharedPreferences.java':'package android.content;import java.util.*;public interface SharedPreferences {boolean getBoolean(String k,boolean d);Map<String,?> getAll();Editor edit();interface Editor {Editor putBoolean(String k,boolean v);Editor putInt(String k,int v);Editor putLong(String k,long v);Editor putFloat(String k,float v);Editor putString(String k,String v);Editor remove(String k);Editor clear();void apply();}}',
   'com/bytedance/ies/abmock/SettingsManager.java':'package com.bytedance.ies.abmock;public class SettingsManager {public static boolean LIZ(String k,boolean v){return v;}public static int LJ(String k,int v){return v;}public static long LJFF(String k,long v){return v;}public static String LJI(String k,String v){return v;}public static float LIZJ(String k,float v){return v;}public static double LIZIZ(String k,double v){return v;}}',
   'cat/narezany/tokkiyou/TokkiYou.java':'package cat.narezany.tokkiyou;class TokkiYou {static String PREFS="test";static android.content.Context context(){return new android.content.Context();}}',
   'cat/narezany/tokkiyou/Feed.java':'package cat.narezany.tokkiyou;class Feed {static boolean isEnabled(){return false;}}',
   'cat/narezany/tokkiyou/DevMode.java':'package cat.narezany.tokkiyou;class DevMode {static boolean on;static boolean isOn(){return on;}}',
   'cat/narezany/tokkiyou/Diary.java':'package cat.narezany.tokkiyou;class Diary {static void note(String line){}static void detail(String line){}}',
   'cat/narezany/tokkiyou/FlagGuard.java':'package cat.narezany.tokkiyou;class FlagGuard {static boolean allows(){return true;}static void again(){}}',
   'cat/narezany/tokkiyou/Flags.java':(ROOT/'inject/java/cat/narezany/tokkiyou/Flags.java').read_text(encoding='utf-8'),
   'cat/narezany/tokkiyou/FlagHarness.java':"""package cat.narezany.tokkiyou;import android.content.*;import java.util.*;public class FlagHarness {
    static class Prefs implements SharedPreferences,SharedPreferences.Editor {Map<String,Object> values=new HashMap<>();public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public Map<String,?> getAll(){return new HashMap<String,Object>(values);}public Editor edit(){return this;}public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public Editor putInt(String k,int v){values.put(k,v);return this;}public Editor putLong(String k,long v){values.put(k,v);return this;}public Editor putFloat(String k,float v){values.put(k,v);return this;}public Editor putString(String k,String v){values.put(k,v);return this;}public Editor remove(String k){values.remove(k);return this;}public Editor clear(){values.clear();return this;}public void apply(){}}
    static void check(boolean v,String what){if(!v)throw new AssertionError(what);}
    public static void main(String[]args){
     Context.files.put("test",new Prefs());Context.files.put(Flags.OVERRIDES,new Prefs());
     check(Flags.forcedInt("audio_comment_publish")==1,"voice comments are on until switched off");
     Flags.set(Flags.KEY_VOICE,false);check(Flags.forcedInt("audio_comment_publish")==null,"and left to TikTok when they are");
     check(Flags.forcedInt("unrelated")==null&&Flags.forcedBoolean("unrelated")==null,"a flag nobody named is TikTok's");
     check(Flags.forcedInt("long_press_speed_up_lock")==1,"the speed lock is answered both ways");
     Flags.set(Flags.KEY_SPEED_LOCK,false);check(Flags.forcedInt("long_press_speed_up_lock")==0,"off is an answer too");
     check(Flags.forcedInt("tt_comment_repost")==null&&Flags.forcedInt("enable_self_repost")==null,"what is only offered is off to begin with");
     Flags.set(Flags.KEY_COMMENT_REPOST,true);Flags.set(Flags.KEY_SELF_REPOST,true);
     check(Flags.forcedInt("tt_comment_repost")==1&&Flags.forcedInt("tt_comment_repost_interaction")==1&&Flags.forcedInt("tt_audio_comment_repost")==1&&Flags.forcedInt("enable_self_repost")==1,"and on when asked for");
     check(Flags.forcedInt("profile_left_align")==1,"the new profile is on for everybody");
     Flags.set(Flags.KEY_NEW_PROFILE,false);check(Flags.forcedInt("profile_left_align")==0,"and off is the old one");Flags.set(Flags.KEY_NEW_PROFILE,true);
     check(Flags.forcedBoolean("profile_left_align")==Boolean.TRUE&&Flags.forcedLong("profile_left_align")==1L&&Flags.forcedString("profile_left_align")==null,"a number is read as whatever the reader wanted");
     Flags.Known flag=new Flags.Known("some_flag","BOOLEAN",Boolean.FALSE),wide=new Flags.Known("a_double","DOUBLE",0d),lock=new Flags.Known("profile_left_align","INT",0);
     Flags.override(flag,Boolean.TRUE);Flags.override(wide,Flags.parse(wide,"1,5"));Flags.override(lock,Flags.parse(lock,"7"));
     check(Flags.forcedBoolean("some_flag")==null&&Flags.forcedInt("profile_left_align")==1,"a developer's answers wait for the developer switch");
     DevMode.on=true;Flags.forget();
     check(Flags.forcedBoolean("some_flag")==Boolean.TRUE&&Flags.forcedInt("some_flag")==1&&Flags.forcedDouble("a_double")==1.5&&Flags.forcedInt("profile_left_align")==7,"and beat a switch once it is on");
     Flags.override(flag,null);check(Flags.forcedBoolean("some_flag")==null&&Flags.overrides().size()==2,"one can be let go");
     Flags.clearOverrides();check(Flags.forcedInt("profile_left_align")==1&&Flags.overrides().isEmpty(),"or all of them");
     check(Flags.parse(flag,"yes")==null&&Flags.parse(lock,"1.5")==null&&Flags.parse(new Flags.Known("o","OBJECT",null),"x")==null,"what is not a value of the type is refused");
     check(Flags.flag("audio_comment_publish",5)==5&&Flags.flag("profile_left_align",0)==1,"the older reader is answered from the same table");
    }
   }"""
  }
  with tempfile.TemporaryDirectory() as folder:
   room=pathlib.Path(folder)
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():
    file=room/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',folder]+[str(room/name) for name in sources],capture_output=True,text=True);self.assertEqual(0,built.returncode,built.stderr)
   ran=subprocess.run(['java','-cp',folder,'cat.narezany.tokkiyou.FlagHarness'],capture_output=True,text=True);self.assertEqual(0,ran.returncode,ran.stderr)

