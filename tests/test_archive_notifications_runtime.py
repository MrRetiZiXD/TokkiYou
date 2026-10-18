"""Actual notification filtering and account isolation, without requesting permission."""
import pathlib,shutil,subprocess,tempfile,unittest
import test_account_appearance_runtime as base
ROOT=pathlib.Path(__file__).resolve().parents[1]
class ArchiveNotificationsTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_native_categories_and_explicit_permission_action(self):
  sources={k:base.STUBS[k] for k in ['android/content/Context.java','android/content/SharedPreferences.java']}
  sources['android/content/Context.java']=sources['android/content/Context.java'].replace('public static int MODE_PRIVATE=0;','public static int MODE_PRIVATE=0;public int opened;public String getPackageName(){return "host.tiktok";}public void startActivity(Intent i){opened++;}')
  sources.update({
 'android/app/Notification.java':'package android.app;public class Notification{public static String CATEGORY_MESSAGE="msg",CATEGORY_PROMO="promo";public String category;public Notification(String s){category=s;}}',
 'android/app/NotificationManager.java':'package android.app;public class NotificationManager{public int posted;public boolean deny;public void notify(int i,Notification n){if(deny)throw new SecurityException();posted++;}public void notify(String tag,int i,Notification n){notify(i,n);}}',
 'android/content/Intent.java':'package android.content;public class Intent{public Intent(String a){}public Intent(String a,android.net.Uri u){}public void putExtra(String k,String v){}}',
 'android/net/Uri.java':'package android.net;public class Uri{public static Uri parse(String s){return new Uri();}}',
 'android/os/Build.java':'package android.os;public class Build{public static class VERSION{public static int SDK_INT=33;}}',
 'cat/narezany/margyt/Margy.java':'package cat.narezany.margyt;class Margy{static android.content.Context c=new android.content.Context();static android.content.Context context(){return c;}}',
 'cat/narezany/margyt/AccountAppearance.java':'package cat.narezany.margyt;class AccountAppearance{static String account="111";static android.content.SharedPreferences prefs(android.content.Context c){return c.getSharedPreferences(account,0);}}',
 'cat/narezany/margyt/Diary.java':'package cat.narezany.margyt;class Diary{static void note(String s){}}',
 'cat/narezany/margyt/Text.java':'package cat.narezany.margyt;class Text{static String PLAYER_NOT_READY="";}',
 'cat/narezany/margyt/Screen.java':'package cat.narezany.margyt;class Screen{static void say(String s){}}',
 'cat/narezany/margyt/ModNotifications.java':(ROOT/'inject/java/cat/narezany/margyt/ModNotifications.java').read_text(encoding='utf-8'),
 'cat/narezany/margyt/Test.java':HARNESS
  })
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
   result=subprocess.run(['javac','-encoding','UTF-8','-source','8','-target','8','-d',str(work)]+[str(p) for p in work.rglob('*.java')],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.Test'],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
HARNESS=r'''package cat.narezany.margyt;import android.app.*;public class Test{static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[] args){NotificationManager m=new NotificationManager();Notification message=new Notification("msg"),promo=new Notification("promo"),unknown=new Notification(null);check(!ModNotifications.noPrompts(),"native prompt unchanged initially");ModNotifications.notify(m,1,message);ModNotifications.set(ModNotifications.MESSAGES,false);ModNotifications.notify(m,2,message);check(m.posted==1,"disabled messages blocked");check(ModNotifications.noPrompts(),"turning notifications off suppresses native repeated guide");ModNotifications.notify(m,"tag",3,promo);ModNotifications.notify(m,4,unknown);check(m.posted==3,"promo and unspecified native categories retained");ModNotifications.set(ModNotifications.OTHER,false);ModNotifications.notify(m,5,unknown);check(m.posted==3,"uncategorized obeys other toggle");check(Margy.c.opened==0,"setting toggles never open permission UI");AccountAppearance.account="222";ModNotifications.reload();ModNotifications.notify(m,6,message);check(m.posted==4&&!ModNotifications.noPrompts(),"second account defaults isolated");m.deny=true;ModNotifications.notify(m,7,message);check(m.posted==4,"denied system permission contained");ModNotifications.settings(Margy.c);check(Margy.c.opened==1,"only explicit action opens Android settings");AccountAppearance.account="111";ModNotifications.reload();check(!ModNotifications.option(0)&&!ModNotifications.option(2),"first account filters restored");}}
'''
