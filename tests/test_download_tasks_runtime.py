"""Run actual task registry and HTTPS download cancellation using a local fake transport."""
import pathlib,tempfile,subprocess,shutil,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class DownloadTasksRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_registry_cancel_retry_and_atomic_output(self):
  with tempfile.TemporaryDirectory() as d:
   w=pathlib.Path(d);sources={}
   for n in ('DownloadTasks','Net'):sources['cat/narezany/tokkiyou/'+n+'.java']=(ROOT/('inject/java/cat/narezany/tokkiyou/'+n+'.java')).read_text(encoding='utf-8')
   sources['cat/narezany/tokkiyou/Diary.java']='package cat.narezany.tokkiyou;class Diary {static void note(String s){}}'
   sources['cat/narezany/tokkiyou/Account.java']='package cat.narezany.tokkiyou;class Account {static String uid="111",name="alice";static String liveId(){return uid;}static String profileHandle(){return name;}}'
   sources['cat/narezany/tokkiyou/TasksHarness.java']='''package cat.narezany.tokkiyou;import java.net.*;import java.io.*;public class TasksHarness {
    static byte[] payload=new byte[200000];static int advertised=payload.length;static int retries;
    static class Connection extends HttpURLConnection {Connection(URL u){super(u);}public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public int getContentLength(){return advertised;}public InputStream getInputStream(){return new ByteArrayInputStream(payload);}}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] a)throws Exception {
     URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
     File file=new File(a[0],"download.bin");java.nio.file.Files.write(file.toPath(),new byte[]{9,8});
     DownloadTasks.Control c=DownloadTasks.begin("mid","Video","Plugin",()->retries++);
     Account.uid="222";Account.name="bob";check(DownloadTasks.snapshot().get(0).owner.equals("@alice"),"owner stable across account switch");
     boolean ok=Net.download("https://test.invalid/file",file,(p,g,t)->{DownloadTasks.progress("mid",p);DownloadTasks.cancel("mid");},c);
     check(!ok,"mid-stream cancellation");check(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(file.toPath()),new byte[]{9,8}),"cancel preserves previous output");check(!new File(file+".part").exists(),"partial removed");DownloadTasks.finish("mid",false);
     check(DownloadTasks.snapshot().get(0).state.equals("Отменено")&&DownloadTasks.snapshot().get(0).retry,"cancel is retryable");DownloadTasks.retry("mid");DownloadTasks.retry("mid");check(retries==1,"retry dispatched once");
     file.delete();c=DownloadTasks.begin("good","Plugin","Store",null);ok=Net.download("https://test.invalid/file",file,null,c);DownloadTasks.finish("good",ok);check(ok&&file.length()==payload.length,"complete output published");
     c=DownloadTasks.begin("truncated","APK","Update",null);advertised=payload.length+1;check(!Net.download("https://test.invalid/file",new File(a[0],"broken.apk"),null,c),"truncated file rejected");DownloadTasks.finish("truncated",false);
     for(int n=0;n<40;n++){String key="task"+n;DownloadTasks.begin(key,key,"API",null);DownloadTasks.finish(key,true);}check(DownloadTasks.snapshot().size()<=16,"finished history bounded");
    }
   }'''
   __import__("android_doubles").with_mod(sources)
   for n,s in sources.items():p=w/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf-8')
   run=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',d]+[str(w/n) for n in sources],capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
   run=subprocess.run(['java','-cp',d,'cat.narezany.tokkiyou.TasksHarness',d],capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
