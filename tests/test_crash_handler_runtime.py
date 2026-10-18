import pathlib,subprocess,tempfile,shutil,unittest
from test_archive_features_runtime import method
class CrashHandlerTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_reporting_failure_never_suppresses_native_handler(self):
  code='package cat.narezany.tokkiyou;import android.content.Context;import java.util.concurrent.atomic.AtomicBoolean;class CrashReports{static Context app;static boolean installed;static AtomicBoolean capturing=new AtomicBoolean();static boolean enabled(Context c){return true;}static void save(Throwable e){throw new RuntimeException("disk failed");}static void flush(){}'+method('CrashReports.java','static synchronized void start(')+'}'
  files={'cat/narezany/tokkiyou/CrashReports.java':code,'android/content/Context.java':'package android.content;public class Context{public Context getApplicationContext(){return this;}}','android/os/Process.java':'package android.os;public class Process{public static int myPid(){return 1;}public static void killProcess(int i){}}','cat/narezany/tokkiyou/Test.java':"""package cat.narezany.tokkiyou;public class Test{public static void main(String[] args){final int[] nativeCalls={0};Thread.setDefaultUncaughtExceptionHandler((t,e)->nativeCalls[0]++);CrashReports.start(new android.content.Context());Thread.UncaughtExceptionHandler h=Thread.getDefaultUncaughtExceptionHandler();h.uncaughtException(Thread.currentThread(),new RuntimeException());h.uncaughtException(Thread.currentThread(),new RuntimeException());if(nativeCalls[0]!=2)throw new AssertionError("native crash chain suppressed");}}"""}
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(files)
   for name,text in files.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8')
   out=subprocess.run(['javac','-source','8','-target','8','-d',directory]+[str(work/name) for name in files],capture_output=True,text=True);self.assertEqual(0,out.returncode,out.stderr)
   out=subprocess.run(['java','-cp',directory,'cat.narezany.tokkiyou.Test'],capture_output=True,text=True);self.assertEqual(0,out.returncode,out.stderr)
