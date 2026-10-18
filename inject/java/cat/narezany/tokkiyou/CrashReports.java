package cat.narezany.tokkiyou;
import android.content.Context;import org.json.*;import java.io.*;import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Crashes, captured where they happen and carried back to the server.
 *
 * What a stack alone cannot say is whether the report is worth acting on:
 * "something threw" on one phone out of one is usually not the mod's fault,
 * and the same throw on every Android 14 device is. So a report carries the
 * thread it died on, how much memory was left, what the device was, and the
 * stack of every thread at the moment of death -- the other threads are often
 * where the actual trouble is when the crashing one is a renderer waiting on
 * a lock somebody else never released.
 *
 * Nothing here runs until the switch is on, and the switch is only turned on
 * by the person using the phone: CrashConsent asks once, and until it has been
 * answered nothing is written down and nothing is sent anywhere.
 */
final class CrashReports {
 static final String KEY="crash_reports",ASKED="crash_consent_v1";private static final AtomicBoolean capturing=new AtomicBoolean(),sending=new AtomicBoolean();
 /** The mod's own boot, so uptime is not the whole phone's. */
 private static final long started=System.currentTimeMillis();
 /**
  * The largest report that goes out: the server refuses anything over 16 KiB,
  * and the margin is there because http framing is not part of the count the
  * limit is meant for.
  */
 private static final int PAYLOAD_LIMIT=16000;
 /**
  * The largest a piece of the envelope may be. The server bounds these too,
  * and a field it refuses would cost the whole report rather than that field.
  */
 private static final int FIELD_LIMIT=120;
 private static boolean installed;private static Context app;
 static boolean enabled(Context c){return c.getSharedPreferences(TokkiYou.PREFS,0).getBoolean(KEY,false);}
 static synchronized void start(Context c){app=c.getApplicationContext();if(installed)return;installed=true;
  Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try{if(capturing.compareAndSet(false,true)&&enabled(app))save(error);}catch(Throwable ignored){}
   finally{if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}}});
  flush();
 }
 private static File directory(){return new File(app.getFilesDir(),"tokkiyou-crashes");}

 /**
  * The report as json, cut down until it fits what the server accepts.
  *
  * The stack is the part that can be made smaller without lying about
  * anything -- a thread dump and a device are fixed costs -- and what is sent
  * is exactly what is left after the cut, never a claim that more was kept.
  * The cut is made at a line break: half a frame reads as a frame in a class
  * that is not in the stack at all, which is worse than one frame less.
  */
 private static String payload(JSONObject body,String trace)throws Exception{
  JSONObject frame=body.put("stack",trace);
  String encoded=frame.toString();
  if(encoded.getBytes("UTF-8").length<=PAYLOAD_LIMIT)return encoded;
  String trimmed=trace;
  while(trimmed.length()>0){
   int cut=trimmed.lastIndexOf('\n');
   if(cut<0)break;
   trimmed=trimmed.substring(0,cut);
   encoded=body.put("stack",trimmed).toString();
   if(encoded.getBytes("UTF-8").length<=PAYLOAD_LIMIT)return encoded;
  }
  return body.put("stack","stack omitted: the report did not fit").toString();
 }

 static void save(Throwable error)throws Exception{
  File dir=directory();if(!dir.exists()&&!dir.mkdirs())return;File[] files=dir.listFiles();if(files!=null&&files.length>=5){java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));for(int i=0;i<=files.length-5;i++)files[i].delete();}
  Thread dying=Thread.currentThread();Throwable root=error;java.util.Set<Throwable> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
  // The cause at the bottom of it. This used to walk past the last cause into nothing,
  // so every report said "java.lang.Throwable" and no message, whatever had happened.
  for(int depth=0;root!=null&&root.getCause()!=null&&depth<5&&seen.add(root);depth++)root=root.getCause();
  final Throwable thrown=error;
  JSONObject body=new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("version",Version.MOD).put("sdk",android.os.Build.VERSION.SDK_INT).put("time",System.currentTimeMillis());
  StringBuilder trace=new StringBuilder();java.util.Set<Throwable> walked=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
  for(int depth=0;error!=null&&depth<5&&walked.add(error);depth++,error=error.getCause()){
   trace.append(error.getClass().getName());
   String detail=error.getMessage();if(detail!=null)trace.append(": ").append(detail.replace('\n',' '));
   trace.append('\n');StackTraceElement[] frames=error.getStackTrace();for(int i=0;i<Math.min(48,frames.length);i++)trace.append(" at ").append(frames[i]).append('\n');}
  JSONObject meta=new JSONObject().put("crash",new JSONObject().put("type",truncate(root==null?"java.lang.Throwable":root.getClass().getName())).put("message",truncate(root!=null&&root.getMessage()!=null?root.getMessage():"").replace('\n',' ')).put("thread",truncate(dying.getName())).put("threadId",dying.getId()).put("threadState",String.valueOf(dying.getState())).put("androidRelease",truncate(android.os.Build.VERSION.RELEASE)).put("fatal",true).put("site",truncate(site(root!=null?root:thrown)).replace('\n',' ')));
  JSONObject device=new JSONObject().put("manufacturer",truncate(android.os.Build.MANUFACTURER)).put("brand",truncate(android.os.Build.BRAND)).put("model",truncate(android.os.Build.MODEL)).put("device",truncate(android.os.Build.DEVICE)).put("hardware",truncate(android.os.Build.HARDWARE)).put("product",truncate(android.os.Build.PRODUCT)).put("board",truncate(android.os.Build.BOARD)).put("fingerprint",truncate(android.os.Build.FINGERPRINT)).put("abis",joins(android.os.Build.SUPPORTED_ABIS));
  JSONObject memory=new JSONObject().put("javaHeapMb",megabytes(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())).put("javaHeapMaxMb",megabytes(Runtime.getRuntime().maxMemory())).put("nativeHeapMb",megabytes(nativeHeap())).put("freeMb",megabytes(ram()[0])).put("totalMb",megabytes(ram()[1])).put("thresholdMb",megabytes(ram()[2])).put("lowMemory",lowMemory()).put("storageFreeMb",megabytes(freeStorage())).put("uptimeSeconds",(System.currentTimeMillis()-started)/1000L);
  meta.put("device",device);meta.put("memory",memory).put("mod",new JSONObject().put("version",Version.MOD).put("tiktok",Version.TIKTOK)).put("threads",threads());
  meta.put("thread",truncate(dying.getName())).put("handler",Thread.getDefaultUncaughtExceptionHandler()==null?"none":"default");
  body.put("meta",meta);
  String encoded=payload(body,trace.toString());
  try(FileOutputStream out=new FileOutputStream(new File(dir,body.getString("id")+".json"))){out.write(encoded.getBytes("UTF-8"));out.getFD().sync();}
 }

 /**
  * Where it was thrown, in one line, for whoever reads the list of reports.
  *
  * Taken from what was thrown. It used to be taken from the thread that
  * died, as it stood while this very handler was running on it -- so every
  * report gave the same place, the call that reads a thread's stack.
  */
 private static String site(Throwable error){
  try{StackTraceElement[] frames=error==null?null:error.getStackTrace();if(frames==null||frames.length==0)return "";
   // the first frame that is somebody's code rather than Android's own plumbing
   for(int i=0;i<frames.length;i++){String type=frames[i].getClassName();
    if(!type.startsWith("android.")&&!type.startsWith("java.")&&!type.startsWith("com.android.")&&!type.startsWith("dalvik.")&&!type.startsWith("libcore."))return frames[i].toString();}
   return frames[0].toString();}catch(Throwable ignored){return "";}
 }

 /** Every other thread's stack: a deadlock or a stuck pool shows up here first. */
 private static JSONArray threads(){
  JSONArray out=new JSONArray();
  try{
   java.util.Map<Thread,StackTraceElement[]> all=Thread.getAllStackTraces();
   int taken=0;
   for(java.util.Map.Entry<Thread,StackTraceElement[]> entry:all.entrySet()){
    if(taken>=12)break;taken++;
    Thread thread=entry.getKey();JSONObject row=new JSONObject().put("name",truncate(thread.getName())).put("state",String.valueOf(thread.getState())).put("daemon",thread.isDaemon());
    StringBuilder stack=new StringBuilder();StackTraceElement[] frames=entry.getValue();for(int i=0;i<Math.min(24,frames.length);i++){if(i>0)stack.append('\n');stack.append(frames[i]);}
    row.put("stack",truncate(stack.toString()));out.put(row);
   }
  }catch(Throwable ignored){}
  return out;
 }

 private static String truncate(String value){if(value==null||value.isEmpty())return "";return value.length()>FIELD_LIMIT?value.substring(0,FIELD_LIMIT):value;}
 private static String joins(String[] values){if(values==null)return "";StringBuilder out=new StringBuilder();for(int i=0;i<values.length;i++){if(i>0)out.append(',');out.append(values[i]);}return truncate(out.toString());}
 private static long megabytes(long bytes){return bytes/1048576L;}
 /**
  * What the phone had left: available, total, and the point at which Android
  * starts killing processes. A crash that happened with 40 MB free is a
  * different story from the same stack with 2 GB free.
  */
 private static long[] ram(){
  try{android.app.ActivityManager manager=manager();if(manager==null)return new long[3];
   android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(info);
   return new long[]{info.availMem,info.totalMem,info.threshold};}catch(Throwable ignored){return new long[3];}
 }
 /** How much memory the process has taken outside the java heap. */
 private static long nativeHeap(){try{return android.os.Debug.getNativeHeapAllocatedSize();}catch(Throwable ignored){return 0;}}
 private static android.app.ActivityManager manager(){try{return (android.app.ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);}catch(Throwable ignored){return null;}}
 private static boolean lowMemory(){try{android.app.ActivityManager manager=manager();return manager!=null&&manager.isLowRamDevice();}catch(Throwable ignored){return false;}}
 private static long freeStorage(){try{return app.getFilesDir().getUsableSpace();}catch(Throwable ignored){return 0;}}

 static void flush(){if(app==null||!enabled(app)||!sending.compareAndSet(false,true))return;
  Net.away("crash-reports",()->{try{File[] files=directory().listFiles();if(files==null)return;for(File file:files){if(!enabled(app))return;if(file.length()>PAYLOAD_LIMIT){file.delete();continue;}byte[] bytes=new byte[(int)file.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(bytes);}String answer=Net.post(Badges.SERVER+"/crash",new String(bytes,"UTF-8"));if(answer!=null&&new JSONObject(answer).optBoolean("ok"))file.delete();else break;}}catch(Throwable ignored){}finally{sending.set(false);}});
 }
 static void set(Context c,boolean on){c.getSharedPreferences(TokkiYou.PREFS,0).edit().putBoolean(KEY,on).putBoolean(ASKED,true).apply();if(on)flush();else{File[] files=directory().listFiles();if(files!=null)for(File f:files)f.delete();}}
}
