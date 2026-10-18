"""Execute actual account hooks, migration and the one set of appearance settings every account shares."""
import pathlib,shutil,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS={
'cat/narezany/tokkiyou/FeedRail.java':'package cat.narezany.tokkiyou;class FeedRail{static void reloadSettings(){}}',
'android/content/SharedPreferences.java':'''package android.content;public interface SharedPreferences {java.util.Map<String,?> getAll();String getString(String k,String d);boolean getBoolean(String k,boolean d);int getInt(String k,int d);boolean contains(String k);Editor edit();interface Editor {Editor putString(String k,String v);Editor putBoolean(String k,boolean v);Editor putInt(String k,int v);Editor putLong(String k,long v);Editor putFloat(String k,float v);Editor remove(String k);void apply();}}''',
'android/content/Context.java':'''package android.content;public class Context {public static int MODE_PRIVATE=0;public java.io.File files;public java.util.Map<String,Memory> stores=new java.util.HashMap<>();public java.io.File getFilesDir(){return files;}public SharedPreferences getSharedPreferences(String n,int m){Memory p=stores.get(n);if(p==null){p=new Memory();stores.put(n,p);}return p;}
 public static class Memory implements SharedPreferences {java.util.Map<String,Object> values=new java.util.HashMap<>();public java.util.Map<String,?> getAll(){return new java.util.HashMap<>(values);}public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public boolean contains(String k){return values.containsKey(k);}public Editor edit(){return new Editor(){java.util.Map<String,Object> writes=new java.util.HashMap<>();public Editor putString(String k,String v){writes.put(k,v);return this;}public Editor putBoolean(String k,boolean v){writes.put(k,v);return this;}public Editor putInt(String k,int v){writes.put(k,v);return this;}public Editor putLong(String k,long v){writes.put(k,v);return this;}public Editor putFloat(String k,float v){writes.put(k,v);return this;}public Editor remove(String k){writes.put(k,null);return this;}public void apply(){for(java.util.Map.Entry<String,Object> e:writes.entrySet())if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}};}}}''',
'android/os/Handler.java':'package android.os;public class Handler {public Handler(Looper l){}public static boolean queued;public static java.util.List<Runnable> jobs=new java.util.ArrayList<>();public void post(Runnable r){if(queued)jobs.add(r);else r.run();}}',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return null;}}',
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long uptimeMillis(){return 100000;}}',
'android/app/Activity.java':'package android.app;public class Activity {}',
'cat/narezany/tokkiyou/SettingsActivity.java':'package cat.narezany.tokkiyou;class SettingsActivity extends android.app.Activity {void accountAppearanceChanged(){}}',
'cat/narezany/tokkiyou/Screen.java':'package cat.narezany.tokkiyou;class Screen {static android.app.Activity now(){return null;}}',
'cat/narezany/tokkiyou/TokkiYou.java':'package cat.narezany.tokkiyou;class TokkiYou {static String PREFS="mod";static android.content.Context ctx;static android.content.Context context(){return ctx;}}',
'cat/narezany/tokkiyou/AppearanceColors.java':'package cat.narezany.tokkiyou;class AppearanceColors {static void reload(){}}',
'cat/narezany/tokkiyou/Fonts.java':'package cat.narezany.tokkiyou;class Fonts {static void reload(){}}',
'cat/narezany/tokkiyou/Themes.java':'package cat.narezany.tokkiyou;class Themes {static void forget(){}static void repaintVisible(){}}',
'cat/narezany/tokkiyou/Accent.java':'package cat.narezany.tokkiyou;class Accent {static void refreshLikeViews(){}}',
'cat/narezany/tokkiyou/LikeColors.java':'package cat.narezany.tokkiyou;class LikeColors {static void reload(){}}',
'cat/narezany/tokkiyou/Badge.java':'package cat.narezany.tokkiyou;class Badge {static void refreshProfiles(){}}',
'cat/narezany/tokkiyou/Plugins.java':'package cat.narezany.tokkiyou;class Plugins {static String uid;static void accountChanged(String value){uid=value;}}',
'cat/narezany/tokkiyou/Diary.java':'package cat.narezany.tokkiyou;class Diary {static java.util.List<String> said=new java.util.ArrayList<>();static void note(String value){if(value.startsWith("settings: one set"))said.add(value);else throw new AssertionError(value);}}',
'cat/narezany/tokkiyou/BioProof.java':'package cat.narezany.tokkiyou;class BioProof {static void recover(){}}',
'cat/narezany/tokkiyou/Avatars.java':'package cat.narezany.tokkiyou;class Avatars {static void rememberCurrent(com.ss.android.ugc.aweme.profile.model.User u){}}',
'com/ss/android/ugc/aweme/profile/model/User.java':'package com.ss.android.ugc.aweme.profile.model;public class User {public String uid,name,handle;public User(String u,String n,String h){uid=u;name=n;handle=h;}public String getUid(){return uid;}public String getNickname(){return name;}public String getUniqueId(){return handle;}}',
'com/ss/android/ugc/profile/platform/base/data/UserProfileInfo.java':'package com.ss.android.ugc.profile.platform.base.data;public class UserProfileInfo extends com.ss.android.ugc.aweme.profile.model.User {public UserProfileInfo(String u,String n,String h){super(u,n,h);}}',
'com/ss/android/ugc/aweme/IAccountUserService.java':'package com.ss.android.ugc.aweme;public interface IAccountUserService {String getCurUserId();String getCurSecUserId();com.ss.android.ugc.aweme.profile.model.User getCurUser();}',
}
STUBS['cat/narezany/tokkiyou/FeedRail.java']='package cat.narezany.tokkiyou;class FeedRail{static void reloadSettings(){}}'
STUBS['cat/narezany/tokkiyou/Version.java']='package cat.narezany.tokkiyou;class Version{static final boolean LITE=false,TEST=false;static final String MOD="0";}'
STUBS['cat/narezany/tokkiyou/FeedBlacklist.java']='package cat.narezany.tokkiyou;class FeedBlacklist{static void reload(){}}'
STUBS['cat/narezany/tokkiyou/PlayerTools.java']='package cat.narezany.tokkiyou;class PlayerTools{static void accountChanged(){} static void cancelSmooth(){}}'
STUBS['cat/narezany/tokkiyou/VideoActions.java']='package cat.narezany.tokkiyou;class VideoActions{static void accountChanged(){} static void bindNative(Object c){} static void selected(Object c,Object p){}}'
STUBS['cat/narezany/tokkiyou/PinnedFriends.java']='package cat.narezany.tokkiyou;class PinnedFriends{static void reload(){}}'
STUBS['cat/narezany/tokkiyou/ProfileLayout.java']='package cat.narezany.tokkiyou;class ProfileLayout{static void reload(){}}'
STUBS['cat/narezany/tokkiyou/MessageTimes.java']='package cat.narezany.tokkiyou;class MessageTimes{static void reload(){}}'
STUBS['cat/narezany/tokkiyou/ModNotifications.java']='package cat.narezany.tokkiyou;class ModNotifications{static void reload(){}}'
STUBS['cat/narezany/tokkiyou/Repost.java']='package cat.narezany.tokkiyou;class Repost{static void reload(){} static void anchor(Object c){} static boolean visibility(Object v,int value){return false;}static boolean replacing(Object v){return false;}}'

HARNESS='''package cat.narezany.tokkiyou;import android.content.*;import com.ss.android.ugc.aweme.profile.model.User;
public class AppearanceHarness {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static class Service implements com.ss.android.ugc.aweme.IAccountUserService {User user;Service(User u){user=u;}public String getCurUserId(){return user==null?"":user.uid;}public String getCurSecUserId(){return "sec";}public User getCurUser(){return user;}}
 public static void main(String[] args)throws Exception {
  Context ctx=new Context();ctx.files=new java.io.File(args[0]);TokkiYou.ctx=ctx;
  SharedPreferences legacy=ctx.getSharedPreferences("mod",0);
  legacy.edit().putString("account_id","111").putInt("accent",123).putBoolean("profile_gradient_on",true).putString("font","file").putString("profile_sync_token","secret").apply();
  java.io.File old=new java.io.File(ctx.files,"tokkiyou/font");old.getParentFile().mkdirs();java.nio.file.Files.write(old.toPath(),new byte[]{1,2});
  Service a=new Service(new User("111","Lolipuzik1","lolipuzik1")),b=new Service(new User("222","Bob2","bob2"));
  Account.getCurUser(a);SharedPreferences one=AccountAppearance.prefs(ctx);
  check(one.getInt("accent",0)==123 && one.getBoolean("profile_gradient_on",false),"the one set starts from the old settings");
  check(Accent.colour()==123,"actual accent cache starts with them");
  check(!one.contains("profile_sync_token"),"verification secrets stay out of appearance storage");
  check(AccountAppearance.file(ctx,"font").isFile(),"font brought into the shared folder");
  Account.getCurUser(b);SharedPreferences two=AccountAppearance.prefs(ctx);
  check(two==one && two.getInt("accent",0)==123 && two.getBoolean("profile_gradient_on",false),"a second account has the same settings");
  check(AccountAppearance.file(ctx,"font").isFile(),"and the same font");
  check(Accent.colour()==123,"the accent does not change with the account");Accent.set(456);
  check("222".equals(Plugins.uid),"plugin account event");
  Account.getCurUser(a);check(AccountAppearance.prefs(ctx).getInt("accent",0)==456 && Accent.colour()==456,"what one account set, the other has");
  Account.rememberProfile(new com.ss.android.ugc.profile.platform.base.data.UserProfileInfo("111","Wrong1","lolipuzik1"));
  check("Lolipuzik1".equals(Account.canonicalName("111","Wrong1")),"secondary profile model cannot replace authoritative nickname");
  check("Other7".equals(Account.canonicalName("999","Other7")),"another profile unchanged");
  Account.getCurUser(new Service(null));check(AccountAppearance.SHARED.equals(AccountAppearance.scope(ctx)) && AccountAppearance.prefs(ctx).getInt("accent",0)==456,"signed out, the settings are still there");
  Account.getCurUser(b);check(AccountAppearance.prefs(ctx).getInt("accent",0)==456 && Accent.colour()==456,"and after signing in again");
  // settings that were kept an account: the one set is a copy of the last account's
  Context kept=new Context();kept.files=new java.io.File(args[0],"kept");
  kept.getSharedPreferences("mod",0).edit().putString("account_id","222").putInt("accent",1).apply();
  kept.getSharedPreferences("tokkiyou_appearance_111",0).edit().putBoolean("_initialized",true).putInt("accent",5).apply();
  kept.getSharedPreferences("tokkiyou_appearance_222",0).edit().putBoolean("_initialized",true).putInt("accent",777).putString("font","mine").apply();
  java.io.File theirs=new java.io.File(kept.files,"tokkiyou/accounts/222/font");theirs.getParentFile().mkdirs();java.nio.file.Files.write(theirs.toPath(),new byte[]{7});
  Diary.said.clear();SharedPreferences shared=AccountAppearance.prefs(kept);
  check(shared.getInt("accent",0)==777 && "mine".equals(shared.getString("font","")),"the last account's settings become everybody's");
  check(new java.io.File(kept.files,"tokkiyou/accounts/shared/font").isFile() && theirs.isFile(),"its files are copied, and left where they were");
  check(kept.getSharedPreferences("tokkiyou_appearance_222",0).getInt("accent",0)==777,"and its own set is left as it was");
  check(Diary.said.size()==1,"said once: "+Diary.said);
  AccountAppearance.prefs(kept);check(Diary.said.size()==1,"and not brought over twice");
  Context out=new Context();out.files=new java.io.File(args[0],"out");
  out.getSharedPreferences("tokkiyou_appearance_guest",0).edit().putBoolean("_initialized",true).putInt("accent",31).apply();
  check(AccountAppearance.prefs(out).getInt("accent",0)==31,"nobody signed in: what the signed-out set had");
  check(!AccountAppearance.appearanceKey("account_id") && !AccountAppearance.appearanceKey("profile_preview_name"),"identity stays outside appearance");
  android.os.Handler.queued=true;Plugins.uid=null;
  AccountAppearance.select("111");AccountAppearance.select("222");AccountAppearance.select("111");
  android.os.Handler.jobs.get(0).run();check(Plugins.uid==null,"stale account callback must be ignored");
  android.os.Handler.jobs.get(1).run();check(Plugins.uid==null,"intermediate account callback must be ignored");
  android.os.Handler.jobs.get(2).run();check("111".equals(Plugins.uid),"only final account dispatches UI/plugin event");
  System.out.println("two-account migration, identity and appearance checks passed");
 }
}'''
class AppearanceRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_two_accounts_and_logout(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   accent=(ROOT/'inject/java/cat/narezany/tokkiyou/Accent.java').read_text(encoding='utf-8')
   def method(signature):
    start=accent.index(signature);pos=accent.index('{',start);depth=1;end=pos+1
    while depth:
     if accent[end]=='{':depth+=1
     elif accent[end]=='}':depth-=1
     end+=1
    return accent[start:end]
   sources['cat/narezany/tokkiyou/Accent.java']='package cat.narezany.tokkiyou;import android.content.*;class Accent {static int cached;static final int BUILT_WITH=99;static final String KEY="accent",KEY_MATERIAL="accent_material";static int fromWallpaper(){return 0;}static void forget(){}static void refreshLikeViews(){}'+''.join(method(sig) for sig in ('static void reload()', 'public static int colour()', 'public static void set(', 'private static boolean follows(', 'private static SharedPreferences prefs()'))+'}'
   for name in ('Account','AccountAppearance'):
    sources['cat/narezany/tokkiyou/'+name+'.java']=(ROOT/('inject/java/cat/narezany/tokkiyou/'+name+'.java')).read_text(encoding='utf-8')
   sources['cat/narezany/tokkiyou/AppearanceHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,content in sources.items():
    target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.AppearanceHarness',str(work/'files')],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)
