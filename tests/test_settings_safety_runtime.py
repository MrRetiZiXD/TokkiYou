"""Execute the actual nullable slider tint and native sticker sender methods."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
def method(file, marker):
 s=(ROOT/'inject/java/cat/narezany/margyt'/file).read_text(encoding='utf-8');start=s.index(marker);a=s.index('{',start);n=1;i=a+1
 while n:
  n += (s[i]=='{')-(s[i]=='}');i+=1
 return s[start:i]
class SafetyRuntimeTest(unittest.TestCase):
 def test_nullable_slider_drawables(self):
  # The Expressive slider builds its own track and handle, so a SeekBar whose
  # drawables are missing (some ROMs return null) can no longer crash tinting.
  slider=method('Expressive.java','    static void slider')
  self.assertNotIn('getProgressDrawable',slider);self.assertNotIn('getThumb',slider)
  self.assertIn('bar.setProgressDrawable(layers)',slider);self.assertIn('bar.setThumb(handle)',slider)
  settings=(ROOT/'inject/java/cat/narezany/margyt/SettingsActivity.java').read_text(encoding='utf-8')
  self.assertNotIn('tintSlider',settings);self.assertIn('Expressive.slider(bar',settings)
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_sticker_sender_only_populates_native_arguments(self):
  source=method('Streaks.java','    static Object sourceFor').replace('X.13GH','Source')+method('Streaks.java','    private static Object constant')
  send=method('Streaks.java','    private static boolean sendOnMain')
  sources={
  'android/content/Context.java':'package android.content;public class Context{public Context getApplicationContext(){return this;}}',
  'Test.java':'''import android.content.Context;import java.lang.reflect.*;
class Diary{static void note(String s){}}class StickerItem{}interface SourceContract{}enum Source implements SourceContract{AUTO_CONSECUTIVE_SA_STICKERS}class Options{}
public class Test{static String SOURCE="AUTO_CONSECUTIVE_SA_STICKERS";static Service service=new Service();static Object messageService(){return service;}static Object blank(Class<?> c){return false;}static Object maybe(Class<?> c){return new Options();}
public static class Service{Object[] args;public void send(Context c,String a,String b,SourceContract source,StickerItem sticker,Options options,String d,String e,String chat,String f,Object g,Object h,boolean flag){args=new Object[]{c,a,b,source,sticker,options,d,e,chat,f,g,h,flag};}}
'''+source+send+'''public static void main(String[] a){if(sourceFor(Object.class)!=null)throw new AssertionError("Unsupported source contract accepted");StickerItem item=new StickerItem();if(!sendOnMain(new Context(),item,"conversation"))throw new AssertionError("Native send failed");Object[] values=service.args;for(int i:new int[]{1,2,6,7,9,10,11})if(values[i]!=null)throw new AssertionError("Unrelated argument populated: "+i);if(values[4]!=item || !"conversation".equals(values[8]) || !Boolean.FALSE.equals(values[12]))throw new AssertionError("Sender payload differs from native call");}}'''}
  self.run_java(sources)
 def run_java(self,sources):
  with tempfile.TemporaryDirectory() as d:
   root=pathlib.Path(d);files=[]
   __import__("android_doubles").with_mod(sources)
   for name,content in sources.items():
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8');files.append(str(p))
   subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',d,*files],check=True,capture_output=True)
   subprocess.run(['java','-cp',d,'Test'],check=True,capture_output=True)

