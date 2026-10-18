package cat.narezany.tokkiyou;
/** Small verified model accessors; cache misses, avoid repeated method scans. */
final class NativeRead {
 private static final java.util.Map<String,java.lang.reflect.Method> methods=new java.util.HashMap<>();
 private static final java.util.Set<String> absent=new java.util.HashSet<>();
 static Object get(Object object,String name){
  if(object==null)return null;
  String key=object.getClass().getName()+"#"+name;
  try{
   java.lang.reflect.Method m;
   synchronized(methods){if(absent.contains(key))return null;m=methods.get(key);if(m==null){try{m=object.getClass().getMethod(name);m.setAccessible(true);if(methods.size()<256)methods.put(key,m);}catch(NoSuchMethodException e){if(absent.size()<256)absent.add(key);return null;}}}
   return m.invoke(object);
  }catch(Throwable error){return null;}
 }
 static Object field(Object object,String name){try{return object.getClass().getField(name).get(object);}catch(Throwable ignored){return null;}}
 static long number(Object object){return object instanceof Number?((Number)object).longValue():0;}
 static String string(Object object){return object instanceof String?(String)object:"";}
 static String uid(Object user){return string(get(user,"getUid"));}
 static String aid(Object post){return string(get(post,"getAid"));}
 static Object item(Object component){
  // 47.2.41: ReusedAssem.LLJJJJLIIL -> 1B0Z.LL is VideoItemParams.
  Object payload=field(field(component,"LLJJJJLIIL"),"LL");if(payload!=null)return payload;
  try{Class<?> type=Class.forName("com.bytedance.assem.arch.reused.ReusedAssem");return Class.forName("X.0cP2").getMethod("LIZ",type).invoke(null,component);}catch(Throwable ignored){return null;}
 }
 static void floating(Object object,String name,float value)throws Exception{
  java.lang.reflect.Method method=object.getClass().getMethod(name,float.class);method.setAccessible(true);method.invoke(object,value);
 }
}
