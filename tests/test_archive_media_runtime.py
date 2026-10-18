"""Exercise actual streamed writes: commit, storage denial, cancellation and failed publish."""
import pathlib,shutil,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class ArchiveMediaTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_streams_and_pending_cleanup(self):
  sources={
 'android/content/Context.java':'package android.content;public class Context{public ContentResolver resolver=new ContentResolver();public ContentResolver getContentResolver(){return resolver;}public int checkSelfPermission(String p){return -1;}}',
 'android/content/ContentValues.java':'package android.content;public class ContentValues{public void put(String k,String v){}public void put(String k,int v){}public void clear(){}}',
 'android/content/ContentResolver.java':'package android.content;public class ContentResolver{public int deleted,published;public boolean noOutput,failCommit;public java.io.ByteArrayOutputStream out;public android.net.Uri insert(android.net.Uri u,ContentValues v){return new android.net.Uri();}public java.io.OutputStream openOutputStream(android.net.Uri u){out=new java.io.ByteArrayOutputStream();return noOutput?null:out;}public int update(android.net.Uri u,ContentValues v,String s,String[] a){if(failCommit)return 0;published++;return 1;}public int delete(android.net.Uri u,String s,String[] a){deleted++;return 1;}}',
 'android/net/Uri.java':'package android.net;public class Uri{}',
 'android/os/Build.java':'package android.os;public class Build{public static class VERSION{public static int SDK_INT=29;}}',
 'android/os/Environment.java':'package android.os;public class Environment{public static String DIRECTORY_MUSIC="Music",DIRECTORY_MOVIES="Movies",DIRECTORY_PICTURES="Pictures";public static java.io.File getExternalStoragePublicDirectory(String p){return new java.io.File(p);}}',
 'android/provider/MediaStore.java':'package android.provider;public class MediaStore{public static String VOLUME_EXTERNAL_PRIMARY="external";public static class MediaColumns{public static String DISPLAY_NAME="name",MIME_TYPE="mime",IS_PENDING="pending",RELATIVE_PATH="path";}public static class Audio{public static class Media{public static android.net.Uri getContentUri(String s){return new android.net.Uri();}}}public static class Images{public static class Media{public static android.net.Uri getContentUri(String s){return new android.net.Uri();}}}public static class Video{public static class Media{public static android.net.Uri getContentUri(String s){return new android.net.Uri();}}}}',
 'android/Manifest.java':'package android;public class Manifest{public static class permission{public static String WRITE_EXTERNAL_STORAGE="storage";}}',
 'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager{public static int PERMISSION_GRANTED=0;}',
 'android/media/MediaScannerConnection.java':'package android.media;public class MediaScannerConnection{public static void scanFile(android.content.Context c,String[] p,String[] m,Object cb){}}',
 'cat/narezany/tokkiyou/Gallery.java':'package cat.narezany.tokkiyou;class Gallery{static String FOLDER="ttcuz";}',
 'cat/narezany/tokkiyou/DownloadTasks.java':'package cat.narezany.tokkiyou;class DownloadTasks{static class Control{boolean stop;public boolean cancelled(){return stop;}}}'
 }
  sources['cat/narezany/tokkiyou/MediaFiles.java']=(ROOT/'inject/java/cat/narezany/tokkiyou/MediaFiles.java').read_text(encoding='utf-8')
  sources['cat/narezany/tokkiyou/Test.java']=HARNESS
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
   result=subprocess.run(['javac','-encoding','UTF-8','-source','8','-target','8','-d',str(work)]+[str(p) for p in work.rglob('*.java')],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.Test',str(work)],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
HARNESS=r'''package cat.narezany.tokkiyou;import android.content.*;import java.io.*;
public class Test{static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[] args)throws Exception{File f=new File(args[0],"video");byte[] bytes=new byte[150000];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)i;try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}Context context=new Context();DownloadTasks.Control control=new DownloadTasks.Control();MediaFiles.save(context,f,"sample.mp4","video/mp4",control);check(context.resolver.published==1&&context.resolver.deleted==0&&java.util.Arrays.equals(bytes,context.resolver.out.toByteArray()),"exact streamed media committed");
context=new Context();context.resolver.noOutput=true;boolean failed=false;try{MediaFiles.save(context,f,"sample.mp4","video/mp4",control);}catch(IOException e){failed=true;}check(failed&&context.resolver.deleted==1&&context.resolver.published==0,"null output cleans pending row");
context=new Context();context.resolver.failCommit=true;failed=false;try{MediaFiles.save(context,f,"sample.mp4","video/mp4",control);}catch(IOException e){failed=true;}check(failed&&context.resolver.deleted==1&&context.resolver.published==0,"publish failure cleans pending row");
context=new Context();control.stop=true;failed=false;try{MediaFiles.save(context,f,"sample.mp4","video/mp4",control);}catch(IOException e){failed=true;}check(failed&&context.resolver.deleted==1&&context.resolver.published==0,"cancel never publishes partial media");android.os.Build.VERSION.SDK_INT=23;failed=false;try{MediaFiles.save(context,f,"sample.mp4","video/mp4",control);}catch(SecurityException e){failed=true;}check(failed,"legacy storage denial is not false success");
}}
'''
