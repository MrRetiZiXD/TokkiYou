import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
class RecolourCacheRuntime(unittest.TestCase):
    def test_hit_rgb_key_and_six_colour_lru(self):
        sources = {
            "android/content/Context.java": """package android.content;
import java.io.File; import android.content.pm.PackageManager;
public class Context { private final File root; public Context(File root){this.root=root;}
public File getFilesDir(){return root;} public String getPackageName(){return "test";}
public PackageManager getPackageManager(){return new PackageManager();}}
""",
            "android/content/pm/PackageManager.java": """package android.content.pm;
public class PackageManager {public static class PackageInfo {public long lastUpdateTime=1;}
public PackageInfo getPackageInfo(String name,int flags){return new PackageInfo();}}
""",
            "android/graphics/Bitmap.java": """package android.graphics;
import java.io.OutputStream;
public class Bitmap {public enum CompressFormat{PNG} public boolean compress(CompressFormat f,int q,OutputStream out){return false;}}
""",
            "android/graphics/BitmapFactory.java": """package android.graphics;
public class BitmapFactory {public static Bitmap decodeByteArray(byte[] b,int o,int n){return null;}}
""",
            "cat/narezany/tokkiyou/TokkiYou.java": """package cat.narezany.tokkiyou;
import android.content.Context; import java.io.File;
public class TokkiYou {static Context c;static Context context(){return c;} }
""",
            "cat/narezany/tokkiyou/RecolourCache.java": (
                ROOT / "inject/java/cat/narezany/tokkiyou/RecolourCache.java"
            ).read_text(encoding="utf-8"),
            "cat/narezany/tokkiyou/Test.java": """package cat.narezany.tokkiyou;
import java.io.File; import java.nio.charset.StandardCharsets;
public class Test {
 static void check(boolean yes){if(!yes)throw new AssertionError();}
 static void await(String key)throws Exception{for(int n=0;n<300&&RecolourCache.get(key)==null;n++)Thread.sleep(10);check(RecolourCache.get(key)!=null);}
 public static void main(String[] args)throws Exception{
  TokkiYou.c=new android.content.Context(new File(args[0]));
  String a=RecolourCache.likeKey("heart",0x12AABBCC);
  check(a.equals(RecolourCache.likeKey("heart",0xFFAABBCC)));
  check(!a.equals(RecolourCache.likeKey("heart",0xFFAABBBD)));
  String first="lottie|"+a;
  RecolourCache.put(first,"ready".getBytes(StandardCharsets.UTF_8));await(first);
  check(new String(RecolourCache.get(first),StandardCharsets.UTF_8).equals("ready"));
  for(int i=1;i<6;i++){String key="lottie|"+RecolourCache.likeKey("heart",i);RecolourCache.put(key,new byte[]{(byte)i});await(key);Thread.sleep(15);}
  check(RecolourCache.get(first)!=null);Thread.sleep(15);
  String seventh="lottie|"+RecolourCache.likeKey("heart",6);
  RecolourCache.put(seventh,new byte[]{6});await(seventh);
  File[] colours=new File(args[0],"tokkiyou/recolour-cache/like").listFiles(File::isDirectory);
  check(colours!=null&&colours.length==6);
  check(RecolourCache.get(first)!=null);
  check(RecolourCache.get("lottie|"+RecolourCache.likeKey("heart",1))==null);
  String png="png|aabbcc|"+RecolourCache.ALGORITHM_VERSION+"|frame";
  RecolourCache.put(png,new byte[]{42});await(png);
  check(new File(args[0],"tokkiyou/recolour-cache/like/aabbcc").listFiles().length==2);
  RecolourCache.clear();
  for(int i=0;i<300&&RecolourCache.get(png)!=null;i++)Thread.sleep(10);
  check(RecolourCache.get(png)==null);

 }
}
""",
        }
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            __import__("android_doubles").with_mod(sources)
            for path, content in sources.items():
                target = root / "src" / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding="utf-8")
            files = [str(path) for path in (root / "src").rglob("*.java")]
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(root / "classes"), *files], check=True)
            subprocess.run(["java", "-cp", str(root / "classes"),
                            "cat.narezany.tokkiyou.Test", str(root / "data")], check=True)
