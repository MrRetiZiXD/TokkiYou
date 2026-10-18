"""Native favorite flow wrapping and fresh streak reads on the actual Java classes."""
import pathlib,shutil,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS={
 'android/content/Context.java':'package android.content;public class Context{public static int MODE_PRIVATE=0;public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String n,int m){return null;}}',
 'android/content/SharedPreferences.java':'package android.content;public interface SharedPreferences{boolean getBoolean(String k,boolean d);String getString(String k,String d);long getLong(String k,long d);int getInt(String k,int d);Editor edit();interface Editor{Editor putBoolean(String k,boolean v);Editor putString(String k,String v);Editor putLong(String k,long v);Editor putInt(String k,int v);void apply();}}',
 'android/os/Looper.java':'package android.os;public class Looper{public static Looper myLooper(){return null;}public static Looper getMainLooper(){return null;}}',
 'android/os/Handler.java':'package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}public void postDelayed(Runnable r,long delay){}public void removeCallbacks(Runnable r){}}',
 'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long uptimeMillis(){return 100000;}}',
 'android/graphics/Bitmap.java':'package android.graphics;public class Bitmap{}',
 'android/graphics/BitmapFactory.java':'package android.graphics;public class BitmapFactory{public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize;}public static Bitmap decodeByteArray(byte[] a,int b,int c,Options o){return null;}}',
 'org/json/JSONArray.java':'package org.json;public class JSONArray{public JSONArray(){}public JSONArray(String s){}public JSONArray put(String s){return this;}public int length(){return 0;}public String optString(int i,String d){return d;}}',
 'cat/narezany/tokkiyou/StreakSchedule.java':'package cat.narezany.tokkiyou;class StreakSchedule{static boolean due(long n,int m){return true;}static int minute(){return -1;}static long delay(long n,long d){return d;}static boolean sameDay(long a,long b){return b>0&&a-b<86400000;}}',
 'cat/narezany/tokkiyou/Account.java':'package cat.narezany.tokkiyou;class Account{static String uid="123";static String liveId(){return uid;}}',
 'cat/narezany/tokkiyou/TokkiYou.java':'package cat.narezany.tokkiyou;class TokkiYou{static String PREFS="test";static android.content.Context context(){return null;}}',
 'cat/narezany/tokkiyou/StreakAlarm.java':'package cat.narezany.tokkiyou;class StreakAlarm{static boolean pending(android.content.Context c){return true;}static boolean online(android.content.Context c){return true;}static void schedule(android.content.Context c,boolean r){}static void channel(android.content.Context c){}}',
 'cat/narezany/tokkiyou/Screen.java':'package cat.narezany.tokkiyou;class Screen{static void say(String s){}}',
 'cat/narezany/tokkiyou/Text.java':'package cat.narezany.tokkiyou;class Text{static final String STREAK_ALL_NONE="a",STREAK_ALL_NO_STICKER="b",STREAK_ALL_NO_CHATS="c",STREAK_ALL_BUSY="d",STREAK_ALL_DONE="%1$d/%2$d";}',
 'cat/narezany/tokkiyou/Diary.java':'package cat.narezany.tokkiyou;class Diary{static void note(String s){}}',
 'cat/narezany/tokkiyou/Net.java':'package cat.narezany.tokkiyou;class Net{static void away(String s,Runnable r){r.run();}static byte[] bytes(String s){return null;}}',
}
HARNESS='''package cat.narezany.tokkiyou;
import java.util.*;import java.lang.reflect.*;import com.ss.android.ugc.aweme.im.common.model.*;import com.ss.android.ugc.aweme.im.streak.api.*;
public class StreakHarness{
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static class State{Object items;State(Object x){items=x;}public Object LIZ(){return items;}}
 public static class Flow{Object value;Flow(Object x){value=x;}public Object getValue(){return value;}}
 public static class Repo{Flow flow;Repo(Object x){flow=new Flow(x);}public Flow getFavouriteStickersFlow(){return flow;}}
 public static class Service implements IStreakService{boolean fresh,missing;StreakData data=new StreakData();
  public enum Status{ACTIVE,SECONDARY_ACTIVE,EXPIRED}public Status state;public Status readStatus(StreakData d){return state;}
  public StreakData LJIJJLI(String id,boolean f){fresh=f;return missing?null:data;}public StreakData J(String id,boolean f){throw new AssertionError("47.2.41 must use native method");}public boolean a0(String id){return true;}public boolean h0(String id,boolean b){return true;}public int w(String id){return 1;}public boolean X(String id){return true;}public boolean Y(String id){return true;}public Integer l0(String id){return 1;}public String O(String id){return "";}
 }
 public static void main(String[] args)throws Exception{
  FavouriteStickers.read();
  StickerItem item=new StickerItem();item.stickerBase=new StickerBase();item.stickerBase.id=9L;
  check(FavouriteStickers.readRepository(new Repo(new State(Arrays.asList(item,"invalid")))),"Native 0IJO flow unwraps favorites");
  check(Streaks.sticker("9")==item,"Full sticker model retained for native sender");
  check(!FavouriteStickers.readRepository(new Repo(new State(Arrays.asList(item)))),"Repeated flow values do not trigger endless rebuilds");
  check(!FavouriteStickers.readRepository(new Repo(null)),"Loading state does not fabricate favorites");
  check(!FavouriteStickers.readRepository(new Repo(new State("bad"))),"Malformed state ignored");
  StickerItem recent=new StickerItem();recent.stickerBase=new StickerBase();recent.stickerBase.id=10L;Streaks.stickerBase(recent);
  check(Streaks.offered().equals(Arrays.asList("9")),"Recent stickers never appear among favorites");
  check(FavouriteStickers.readRepository(new Repo(new State(Collections.emptyList()))),"Removing favorite updates list");
  check(Streaks.offered().isEmpty()&&Streaks.sticker("9")==null,"Removed favorites unavailable for automatic send");
  FavouriteStickers.readRepository(new Repo(new State(Arrays.asList(item))));
  Service service=new Service();Streaks.streakOf(service,"chat",false);
  Method ask=Streaks.class.getDeclaredMethod("ask",String.class);ask.setAccessible(true);
  check(ask.invoke(null,"chat")==service.data && service.fresh,"Auto check requests current native state");
  service.missing=true;check(ask.invoke(null,"chat")==null,"No fallback to stale cached grey streak");
  // A loading native state must retain the scheduled retry, without sending.
  TokkiYou.ctx=new android.content.Context();
  FavouriteStickers.read();Streaks.rememberFavourite(item);
  Method worker=Streaks.class.getDeclaredMethod("roundWorker",android.content.Context.class,boolean.class);worker.setAccessible(true);
  check((Boolean)worker.invoke(null,TokkiYou.ctx,false),"Unreadable streak schedules another attempt");
  service.missing=false;
  // two days of talking is not a streak yet: nothing to read, nothing to send, nothing to come back for
  service.data.streak=2;check(!(Boolean)worker.invoke(null,TokkiYou.ctx,false),"A conversation with no flame is left alone");
  service.data.streak=5;
  check((Boolean)worker.invoke(null,TokkiYou.ctx,false),"Unreadable status schedules another attempt");
  Field asked=Streaks.class.getDeclaredField("asked");asked.setAccessible(true);((Map<?,?>)asked.get(null)).clear();
  check((Boolean)worker.invoke(null,TokkiYou.ctx,false),"Loading conversations schedule another attempt even with initialized service");
  Streaks.clearStickers();
  check((Boolean)worker.invoke(null,TokkiYou.ctx,false),"Chosen sticker loading after process restart schedules retry");
  Streaks.rememberFavourite(item);service.state=Service.Status.ACTIVE;Streaks.discover(service,"chat");
  worker.invoke(null,TokkiYou.ctx,false);
  check(com.ss.android.ugc.aweme.im.sticker.api.IMStickerApi.sent==0,"Without selected time active flame does not send");
  Method manual=Streaks.class.getDeclaredMethod("remember",String.class);manual.setAccessible(true);manual.invoke(null,"chat");
  StreakSchedule.selected=0;worker.invoke(null,TokkiYou.ctx,false);
  check(com.ss.android.ugc.aweme.im.sticker.api.IMStickerApi.sent==1,"Selected time sends even after manual renewal today");
  worker.invoke(null,TokkiYou.ctx,false);
  check(com.ss.android.ugc.aweme.im.sticker.api.IMStickerApi.sent==1,"Active flame sends only once per day");
  service.state=Service.Status.EXPIRED;Streaks.discover(service,"expired");worker.invoke(null,TokkiYou.ctx,false);
  check(com.ss.android.ugc.aweme.im.sticker.api.IMStickerApi.sent==1,"Expired flame never receives scheduled message");
  ((Map<?,?>)asked.get(null)).clear();
  Account.uid="456";Streaks.hasStreak(service,"other");Method conversations=Streaks.class.getDeclaredMethod("conversations");conversations.setAccessible(true);
  List<?> ids=(List<?>)conversations.invoke(null);check(ids.size()==1 && ids.contains("other"),"Changing accounts discards previous conversations");
  StreakSchedule.selected=-1;Streaks.start(TokkiYou.ctx);Streaks.rescheduleForeground();
  check(android.os.Handler.lastDelay==900000L,"No-time cadence retained");
  Calendar clock=Calendar.getInstance();int minute=clock.get(Calendar.HOUR_OF_DAY)*60+clock.get(Calendar.MINUTE);
  if(minute<1439){StreakSchedule.selected=minute+1;int removed=android.os.Handler.removed;Streaks.rescheduleForeground();check(android.os.Handler.removed>removed&&android.os.Handler.lastDelay<=60000L,"Changing chosen time replaces old fifteen-minute foreground timer");}
 }
}'''
class StreakRepositoryRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_native_favorites_and_fresh_state(self):
  with tempfile.TemporaryDirectory() as folder:
   work=pathlib.Path(folder);sources=dict(STUBS)
   sources['android/content/Context.java']=sources['android/content/Context.java'].replace('return null;', '''return (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{if(m.getName().equals("getString"))return a[0].toString().contains("sticker")?"9":a[1];if(m.getName().equals("getBoolean"))return true;if(m.getName().equals("getLong"))return 0L;if(m.getName().equals("getInt"))return 0;return null;});''')
   sources['android/content/Context.java']=sources['android/content/Context.java'].replace('String n,int m)', 'String n,int mode)')
   sources['cat/narezany/tokkiyou/TokkiYou.java']=sources['cat/narezany/tokkiyou/TokkiYou.java'].replace('static android.content.Context context(){return null;}','static android.content.Context ctx;static android.content.Context context(){return ctx;}')
   sources['cat/narezany/tokkiyou/NativeStreaks.java']='package cat.narezany.tokkiyou;class NativeStreaks{static void prepare(){}}'
   sources['cat/narezany/tokkiyou/StreakSchedule.java']=sources['cat/narezany/tokkiyou/StreakSchedule.java'].replace('static int minute(){return -1;}','static int selected=-1;static int minute(){return selected;}')
   import test_streak_schedule_edges as schedule
   sources['cat/narezany/tokkiyou/StreakSchedule.java']=sources['cat/narezany/tokkiyou/StreakSchedule.java'].replace('static long delay(long n,long d){return d;}',schedule.method('StreakSchedule.java','static long delay(').replace('Calendar','java.util.Calendar'))
   sources['android/os/Handler.java']=sources['android/os/Handler.java'].replace('public Handler(Looper l){}','public static long lastDelay;public static int removed;public Handler(Looper l){}').replace('public void postDelayed(Runnable r,long delay){}','public void postDelayed(Runnable r,long delay){lastDelay=delay;}').replace('public void removeCallbacks(Runnable r){}','public void removeCallbacks(Runnable r){removed++;}')
   sources['android/content/Context.java']='''package android.content;public class Context{public static int MODE_PRIVATE=0;static java.util.Map<String,Object> data=new java.util.HashMap<>();public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String n,int mode){return (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{String name=m.getName();if(name.equals("edit"))return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(e,f,b)->{if(f.getName().equals("apply"))return null;data.put((String)b[0],b[1]);return e;});if(name.equals("getString")&&a[0].equals("streak_sticker"))return "9";if(name.equals("getBoolean"))return true;return data.containsKey(a[0])?data.get(a[0]):a[1];});}}'''
   sources['com/ss/android/ugc/aweme/im/sticker/api/IMStickerApi.java']='''package com.ss.android.ugc.aweme.im.sticker.api;public class IMStickerApi{public static int sent;public static Holder holder=new Holder();public static class Holder{public IMStickerApi instance(){return new IMStickerApi();}}public Object getImStickerMessageService(){return new Sender();}public enum Source{AUTO_CONSECUTIVE_SA_STICKERS}public static class Options{public Options(Object x){}}public static class Sender{public void send(android.content.Context c,Object a,Object b,Source s,com.ss.android.ugc.aweme.im.common.model.StickerItem item,Options opts,Object d,Object e,String id,Object f,Object g,Object h,boolean flag){sent++;}}}'''
   for name in ('Streaks','FavouriteStickers'):
    sources['cat/narezany/tokkiyou/'+name+'.java']=(ROOT/'inject/java/cat/narezany/tokkiyou'/f'{name}.java').read_text(encoding='utf-8')
   for name in ('StickerItem','StickerBase','StickerImage'):
    path='com/ss/android/ugc/aweme/im/common/model/'+name+'.java';sources[path]=(ROOT/'inject/stubs'/path).read_text(encoding='utf-8')
   for name in ('StreakData','IStreakService'):
    path='com/ss/android/ugc/aweme/im/streak/api/'+name+'.java';sources[path]=(ROOT/'inject/stubs'/path).read_text(encoding='utf-8')
   sources['cat/narezany/tokkiyou/StreakHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,s in sources.items():
    path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(s,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/n) for n in sources],capture_output=True,text=True);self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.StreakHarness'],capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
