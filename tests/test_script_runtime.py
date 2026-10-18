import unittest
import test_settings_safety_runtime as safety
method=safety.method
class ScriptRuntimeTest(unittest.TestCase):
 def test_actual_validation_and_compiled_colour_commands(self):
  body='\n'.join(method('ScriptPlugins.java',m) for m in [' static final class Program',' static void keys',' static String string',' static void depth',' static Program check',' public static String name',' public static int colour'])
  sources={
   'org/json/JSONObject.java':'''package org.json;import java.util.*;public class JSONObject {public static Map<String,JSONObject> inputs=new HashMap<>();public Map<String,Object> values=new LinkedHashMap<>();public JSONObject(){}public JSONObject(String raw){values=inputs.get(raw).values;}public JSONObject put(String k,Object v){values.put(k,v);return this;}public Object get(String k)throws Exception{if(!values.containsKey(k))throw new Exception("missing");return values.get(k);}public String getString(String k)throws Exception{Object o=get(k);if(!(o instanceof String))throw new Exception("string");return (String)o;}public JSONArray optJSONArray(String k){Object o=values.get(k);return o instanceof JSONArray?(JSONArray)o:null;}public boolean has(String k){return values.containsKey(k);}public Iterator<String> keys(){return values.keySet().iterator();}}''',
   'org/json/JSONArray.java':'''package org.json;import java.util.*;public class JSONArray {public java.util.List<Object> values=new ArrayList<>();public JSONArray put(Object v){values.add(v);return this;}public int length(){return values.size();}public JSONObject getJSONObject(int i)throws Exception{Object o=values.get(i);if(!(o instanceof JSONObject))throw new Exception("object");return (JSONObject)o;}}''',
   'android/util/Base64.java':'''package android.util;public class Base64{public static final int DEFAULT=0;public static byte[] decode(String s,int flags){return java.util.Base64.getDecoder().decode(s);}}''',
   'Local.java':'class Local{static String of(String russian){return russian;}}',
   'android/graphics/Color.java':'''package android.graphics;public class Color{public static int parseColor(String s){return (int)(0xFF000000L|Long.parseLong(s.substring(1),16));}}''',
   'Test.java':'''import org.json.*;import java.util.*;class PluginCatalog{static boolean id(String s){return s.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");}}public class Test{static Program[] active=new Program[0];'''+body+'''
static JSONObject sample(){return new JSONObject().put("schema",1).put("id","my.plugin").put("name","Plugin").put("version","1.0").put("author","Author");}
static Program verify(JSONObject j)throws Exception{JSONObject.inputs.put("source",j);return check("source");}
static void reject(JSONObject j)throws Exception{try{verify(j);}catch(Exception expected){return;}throw new AssertionError("unsafe source accepted");}
public static void main(String[] args)throws Exception{
 JSONObject good=sample().put("rows",new JSONArray().put(new JSONObject().put("title","Copy").put("action","copy").put("value","text"))).put("names",new JSONArray().put(new JSONObject().put("uid","123").put("value","Name"))).put("colours",new JSONArray().put(new JSONObject().put("from","#FE2C55").put("to","#6750A4")));
 active=new Program[]{verify(good)};if(!name("123","Old").equals("Name")||!name("9","Other").equals("Other"))throw new AssertionError();if(colour(0xFFFE2C55)!=0xFF6750A4||colour(0xFFFFFFFF)!=0xFFFFFFFF)throw new AssertionError();for(int i=0;i<1000000;i++)if(colour(0xFFFFFFFF)!=0xFFFFFFFF)throw new AssertionError();
 reject(sample().put("schema",true));reject(sample().put("schema","1"));reject(sample().put("eval","hidden"));reject(sample().put("name","a\\u202Eb"));reject(sample().put("id","../bad"));reject(sample().put("rows",new JSONArray().put(new JSONObject().put("title","exec").put("action","exec").put("value","sh"))));reject(sample().put("colours",new JSONArray().put(new JSONObject().put("from","red").put("to","#6750A4"))));
 try{depth("[[[[[[[[[0]]]]]]]]]");throw new AssertionError();}catch(Exception expected){}depth(new String(new char[]{34,91,91,91,91,91,91,91,91,91,91,34}));
 active=new Program[0];if(colour(55)!=55||!name("123","Original").equals("Original"))throw new AssertionError();
}}
'''}
  safety.SafetyRuntimeTest().run_java(sources)
