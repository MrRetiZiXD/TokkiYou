package cat.narezany.margyt;
import java.util.*;import java.lang.reflect.*;
import com.ss.android.ugc.aweme.im.streak.api.IStreakService;
/** Audited entry points in TikTok 47.2.41: singleton, login initializer, friend conversation IDs. */
final class NativeStreaks {
 private static String account;private static long last;
 static synchronized void prepare(){
  if(Account.liveId()==null)try{Object userService=Class.forName("X.14EI").getMethod("LJFF").invoke(null);if(userService instanceof com.ss.android.ugc.aweme.IAccountUserService)Account.getCurUserId((com.ss.android.ugc.aweme.IAccountUserService)userService);}catch(Throwable ignored){}
  String uid=Account.liveId();if(uid==null)return;
  long now=android.os.SystemClock.uptimeMillis();if(uid.equals(account)&&now-last<5000)return;account=uid;last=now;
  try{
   Object nativeService=Class.forName("X.06p9").getMethod("LIZ").invoke(null);
   if(!(nativeService instanceof IStreakService))return;
   IStreakService service=(IStreakService)nativeService;
   Streaks.discover(service,null);
   android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
   main.post(()->{if(!uid.equals(Account.liveId()))return;try{service.getClass().getMethod("LIZLLL").invoke(service);}catch(Throwable e){Diary.note("streak native init: "+e.getClass().getSimpleName());}});
   Object api=Class.forName("X.06bA").getMethod("LIZ").invoke(null);
   if(api!=null){Object contacts=api.getClass().getMethod("LJ").invoke(api);if(contacts!=null){Object list=contacts.getClass().getMethod("getAllFriends").invoke(contacts);if(list instanceof List)friends((List)list,uid);}}
  }catch(Throwable e){Diary.note("streak native discovery: "+e.getClass().getSimpleName());}
 }
 static void friends(List<?> users,String uid){
  if(!uid.equals(Account.liveId()))return;
  try{
   Object value=Class.forName("X.06p9").getMethod("LIZ").invoke(null);if(!(value instanceof IStreakService))return;
   IStreakService service=(IStreakService)value;Method convert=Class.forName("X.06WN").getMethod("LJ",String.class);
   for(Object user:users){if(!uid.equals(Account.liveId()))return;String friend=NativeRead.uid(user);if(!friend.matches("[0-9]{1,24}")||friend.equals(uid))continue;Object id=convert.invoke(null,friend);if(id instanceof String&&!((String)id).isEmpty())Streaks.discover(service,(String)id);}
  }catch(Throwable e){Diary.note("streak friend discovery: "+e.getClass().getSimpleName());}
 }
}
