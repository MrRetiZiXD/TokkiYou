"""Native bio metadata must round-trip through release-renamed Gson methods."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class NativeJsonRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_standard_and_release_gson(self):
  for encode,decode in [('toJson','fromJson'),('LJIILL','LJI')]:
   with self.subTest(encode=encode),tempfile.TemporaryDirectory() as directory:
    work=pathlib.Path(directory)
    gson='''package com.google.gson;public class Gson {
      public static int made;public Gson(){made++;}
      public String ENCODE(Object value){return value==null?"null":(String)value;}
      public Object DECODE(String value,Class<?> type){return value.equals("null")?null:new com.ss.android.ugc.aweme.profile.model.SignatureExtraInfo[]{new com.ss.android.ugc.aweme.profile.model.SignatureExtraInfo(value)};}
      public Object unrelated(String value,java.lang.reflect.Type type){throw new AssertionError("wrong overload");}
      public String unrelated(Object value,java.lang.reflect.Type type){throw new AssertionError("wrong overload");}
     }'''.replace('ENCODE',encode).replace('DECODE',decode)
    native=(ROOT/'inject/java/cat/narezany/margyt/NativeBio.java').read_text(encoding='utf-8')
    native=native[:native.index('    static boolean available()')]+'}'
    sources={
     'cat/narezany/margyt/NativeJson.java':(ROOT/'inject/java/cat/narezany/margyt/NativeJson.java').read_text(encoding='utf-8'),
     'cat/narezany/margyt/NativeBio.java':native,
     'android/os/Handler.java':'package android.os;public class Handler{}',
     'android/os/Looper.java':'package android.os;public class Looper{}',
     'android/os/Message.java':'package android.os;public class Message{}',
     'com/google/gson/Gson.java':gson,
     'com/ss/android/ugc/aweme/profile/model/SignatureExtraInfo.java':'package com.ss.android.ugc.aweme.profile.model;public class SignatureExtraInfo {public final String data;public SignatureExtraInfo(String d){data=d;}}',
     'X/14EI.java':'''package X;public class 14EI {}''',
    }
    # Obfuscated class names beginning with digits are valid DEX but not Java.
    # Override only the service locator; execute actual extras()/decodeExtras().
    sources.pop('X/14EI.java')
    sources['cat/narezany/margyt/NativeBio.java']=native.replace('return Class.forName("X.14EI").getMethod("LJFF").invoke(null);','return new NativeJsonHarness.Service();')
    sources['cat/narezany/margyt/NativeJsonHarness.java']=r'''package cat.narezany.margyt;
      public class NativeJsonHarness {
       public static class Service {public User getCurUser(){return new User();}}
       public static class User {public String getUid(){return "123";}public String getSignatureExtra(){return "[{\"text\":\"@friend\",\"start\":2,\"end\":9}]";}}
       static void check(boolean b){if(!b)throw new AssertionError();}
       public static void main(String[] args)throws Exception {
        String expected=new User().getSignatureExtra();
        check(NativeBio.extras("123").equals(expected));
        java.util.List<?> back=NativeBio.decodeExtras(expected);
        check(back.size()==1 && ((com.ss.android.ugc.aweme.profile.model.SignatureExtraInfo)back.get(0)).data.equals(expected));
        check(NativeBio.decodeExtras("null").isEmpty());
        for(int i=0;i<100;i++)check(NativeBio.extras("123").equals(expected));
        check(com.google.gson.Gson.made==1);
        try{NativeBio.extras("456");throw new AssertionError();}catch(IllegalStateException expectedError){}
       }
      }'''
    __import__("android_doubles").with_mod(sources)
    for name,source in sources.items():
     file=work/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source,encoding='utf-8')
    result=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',directory]+[str(work/name) for name in sources],capture_output=True,text=True)
    self.assertEqual(0,result.returncode,result.stderr)
    result=subprocess.run(['java','-cp',directory,'cat.narezany.margyt.NativeJsonHarness'],capture_output=True,text=True)
    self.assertEqual(0,result.returncode,result.stderr)
