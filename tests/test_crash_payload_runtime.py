"""A crash report is cut down to what the server takes, without lying about it."""
import pathlib,shutil,subprocess,tempfile,unittest
import test_account_appearance_runtime as base
ROOT=pathlib.Path(__file__).resolve().parents[1]
def method(file,signature):
 text=(ROOT/'inject/java/cat/narezany/tokkiyou'/file).read_text(encoding='utf-8');a=text.index(signature);b=text.index('{',a);depth=1;i=b+1
 while depth:
  if text[i]=='{':depth+=1
  if text[i]=='}':depth-=1
  i+=1
 return text[a:i]
class CrashPayloadTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_long_stack_is_trimmed_to_the_limit_the_server_accepts(self):
  sources=dict(base.STUBS)
  # Only this one method is exercised, so the class is reduced to the method
  # and the constant it cuts against. The json library is the one android.jar
  # provides, stood in for by the smallest thing that serialises the same way.
  sources['org/json/JSONObject.java']=JSON
  sources['cat/narezany/tokkiyou/Reports.java']=('package cat.narezany.tokkiyou;import org.json.*;class Reports{'
   +'private static final int PAYLOAD_LIMIT=16000;'
   # The method is private to the real class; here it is called from the harness.
   +method('CrashReports.java','private static String payload(').replace('private static String payload(','static String payload(')+'}')
  sources['cat/narezany/tokkiyou/Test.java']=HARNESS
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
   result=subprocess.run(['javac','-encoding','UTF-8','-source','8','-target','8','-d',str(work)]+[str(p) for p in work.rglob('*.java')],capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.Test'],capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr+result.stdout)
HARNESS=r'''package cat.narezany.tokkiyou;import org.json.*;import java.nio.charset.StandardCharsets;public class Test{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 public static void main(String[] args)throws Exception{
  String frame=" at com.ss.android.Screen.onDraw(Screen.java:42)";
  // A small report goes out exactly as it was built, envelope and all.
  StringBuilder small=new StringBuilder();for(int i=0;i<20;i++)small.append("java.lang.IllegalStateException: boom\n").append(frame).append('\n');
  JSONObject body=new JSONObject().put("id","aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").put("version","0.5.9").put("sdk",34).put("meta",new JSONObject().put("device",new JSONObject().put("model","Pixel 8")));
  String kept=Reports.payload(body,small.toString());
  check(kept.getBytes("UTF-8").length<16000,"a small report fits");
  JSONObject parsed=new JSONObject(kept);
  check(parsed.getString("stack").equals(small.toString()),"a small stack is kept whole");
  check(parsed.getJSONObject("meta").getJSONObject("device").getString("model").equals("Pixel 8"),"the envelope survives the cut");
  // A huge one is cut on line boundaries until it fits.
  StringBuilder huge=new StringBuilder();for(int i=0;i<400;i++)huge.append("java.lang.IllegalStateException: boom\n").append(frame).append('\n');
  String trimmed=Reports.payload(body,huge.toString());
  check(trimmed.getBytes("UTF-8").length<16000,"a huge report is cut to fit");
  String stack=new JSONObject(trimmed).getString("stack");
  check(stack.length()<huge.length(),"the stack really was shortened");
  check(huge.toString().startsWith(stack),"what is sent is a prefix, not a rewrite");
  for(String line:stack.split("\n",-1))check(line.isEmpty()||line.equals("java.lang.IllegalStateException: boom")||line.equals(frame),"a kept line is a whole line: <"+line+">");
 }
}
'''
# The parts of android's json the report builder uses, serialising the way the
# real one does: nested objects in insertion order, with no spaces.
JSON=r'''package org.json;import java.util.*;
public class JSONObject{
 private final LinkedHashMap<String,Object> values=new LinkedHashMap<>();
 public JSONObject(){}
 public JSONObject(String text){parse(text.trim());}
 public JSONObject put(String key,Object value){values.put(key,value);return this;}
 public String getString(String key){return (String)values.get(key);}
 public JSONObject getJSONObject(String key){return (JSONObject)values.get(key);}
 public String toString(){return write();}
 private void parse(String text){
  int i=text.indexOf('{')+1;
  while(i>0&&i<text.length()){
   int key=i+1,keyEnd=text.indexOf('"',key);String name=text.substring(key,keyEnd);i=text.indexOf(':',keyEnd)+1;
   while(i<text.length()&&text.charAt(i)==' ')i++;
   if(text.charAt(i)=='{'){int depth=0,start=i;for(;i<text.length();i++){char c=text.charAt(i);if(c=='{')depth++;else if(c=='}'){depth--;if(depth==0){i++;break;}}}values.put(name,new JSONObject(text.substring(start,i)));}
   else if(text.charAt(i)=='"'){int end=i+1;StringBuilder raw=new StringBuilder();while(end<text.length()&&text.charAt(end)!='"'){char c=text.charAt(end);if(c=='\\'){end++;char next=text.charAt(end);if(next=='n')raw.append('\n');else if(next=='r')raw.append('\r');else if(next=='t')raw.append('\t');else raw.append(next);}else raw.append(c);end++;}values.put(name,raw.toString());i=end+1;}
   else{int end=i;while(end<text.length()&&text.charAt(end)!=','&&text.charAt(end)!='}')end++;values.put(name,text.substring(i,end).trim());i=end;}
   int next=text.indexOf(',',i);if(next<0)break;i=text.indexOf('"',next);
  }
 }
 private String write(){
  StringBuilder out=new StringBuilder("{");boolean first=true;
  for(Map.Entry<String,Object> entry:values.entrySet()){
   if(!first)out.append(',');
   first=false;out.append('"').append(entry.getKey()).append("\":");
   Object value=entry.getValue();
   if(value instanceof JSONObject)out.append(value.toString());
   else out.append('"').append(value).append('"');
  }
  return out.append('}').toString();
 }
}
'''
