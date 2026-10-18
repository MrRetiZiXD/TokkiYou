package cat.narezany.margyt;
import android.os.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;
/** Account-scoped, bounded reads through TikTok's signed-in friends API. */
final class SocialLibrary {
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
 private static String account;private static long last;private static boolean started;
 private static volatile boolean missing;
 static synchronized void start(){if(started)return;started=true;main.postDelayed(new Runnable(){public void run(){NativeStreaks.prepare();refresh(null);main.postDelayed(this,60000);}},3000);}
 static void refresh(Runnable ready){if(missing)return;String uid=Account.liveId();if(uid==null)return;
  synchronized(SocialLibrary.class){if(!uid.equals(account)){account=uid;last=0;PinnedFriends.reload();}if(last!=0&&android.os.SystemClock.uptimeMillis()-last<300000)return;if(!busy.compareAndSet(false,true))return;last=android.os.SystemClock.uptimeMillis();}
  main.post(()->{try{FavouriteStickers.load(ready);}catch(Throwable e){Diary.note("sticker library: "+e);}});
  Net.away("friends-library",()->{try{load(uid,ready);}catch(ClassNotFoundException|NoSuchMethodException|NoSuchFieldException e){
   // this TikTok does not have what is asked for, and will not have it a minute from now either
   missing=true;Diary.note("friends library is not in this TikTok: "+e);
  }catch(Throwable e){
   // Tried again when the five minutes are up, like a read that worked. It used to be tried
   // again on the next minute, and every try began by reloading the stickers on the main thread.
   Diary.note("friends library: "+e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage()));
  }finally{busy.set(false);}});
 }
 /**
  * The coroutine context that holds nothing, for a call made from outside any coroutine.
  * Kotlin's own is renamed from one TikTok to the next, so it is asked for by its name,
  * then by the name it has in 47.2, and made here when it is under neither.
  */
 static Object emptyContext()throws Exception{
  for(String name:new String[]{"kotlin.coroutines.EmptyCoroutineContext","X.0D4q"}){
   // a name of TikTok's own may be some other class in another release; Kotlin's is what it says
   try{Object found=Class.forName(name).getField("INSTANCE").get(null);if(found!=null&&(name.startsWith("kotlin.")||"EmptyCoroutineContext".equals(found.toString())))return found;}catch(Throwable ignored){}
  }
  Class<?> kind=Class.forName("kotlin.coroutines.CoroutineContext");
  return Proxy.newProxyInstance(kind.getClassLoader(),new Class<?>[]{kind},(proxy,m,args)->{
   String name=m.getName();
   if(name.equals("fold"))return args[0];if(name.equals("plus"))return args[0];if(name.equals("minusKey"))return proxy;
   if(name.equals("toString"))return "EmptyCoroutineContext";if(name.equals("hashCode"))return 0;if(name.equals("equals"))return proxy==args[0];
   return null;});
 }
 static void load(String uid,Runnable ready)throws Exception{
  Class<?> api=Class.forName("X.1M7B"),continuation=Class.forName("X.01K8");Object service=api.getField("LIZIZ").get(null);
  Object scene=Class.forName("X.0Xou").getField("TT_RELATION_PAGE").get(null);int source=((Number)scene.getClass().getMethod("getValue").invoke(scene)).intValue();
  Object context=emptyContext();
  Method request=api.getMethod("LIZIZ",int.class,int.class,String.class,String.class,continuation);
  Object accountService=Class.forName("X.14EI").getMethod("LJFF").invoke(null);
  if(!uid.equals(NativeRead.string(NativeRead.get(accountService,"getCurUserId"))))return;
  String sec=NativeRead.string(NativeRead.get(accountService,"getCurSecUserId")),page="";Set<String> tokens=new HashSet<>();
  if(sec.length()==0)throw new java.io.IOException("account service not ready");
  for(int count=0;count<20&&uid.equals(Account.liveId());count++){
   CountDownLatch latch=new CountDownLatch(1);Object[] answer=new Object[1];
   Object callback=Proxy.newProxyInstance(continuation.getClassLoader(),new Class<?>[]{continuation},(proxy,m,args)->{
    if(m.getName().equals("getContext"))return context;
    if(m.getName().equals("resumeWith")){answer[0]=args[0];latch.countDown();return null;}
    if(m.getName().equals("toString"))return "margyt-friends";if(m.getName().equals("hashCode"))return System.identityHashCode(proxy);if(m.getName().equals("equals"))return proxy==args[0];return null;});
   Object immediate=request.invoke(service,source,30,sec,page,callback);
   if(immediate!=null&&immediate.getClass().getName().equals("com.ss.android.ugc.aweme.relation.model.FriendsListResponse"))answer[0]=immediate;
   else if(!latch.await(20,TimeUnit.SECONDS))throw new java.io.IOException("friends timeout");
   Object response=answer[0];if(response==null||!response.getClass().getName().equals("com.ss.android.ugc.aweme.relation.model.FriendsListResponse"))throw new java.io.IOException("friends response unavailable");
   if(!uid.equals(Account.liveId()))return;Object users=NativeRead.get(response,"getUserList");if(users instanceof List){PinnedFriends.observe((List)users);NativeStreaks.friends((List)users,uid);}
   if(ready!=null)main.post(()->{if(uid.equals(Account.liveId()))ready.run();});
   if(!Boolean.TRUE.equals(NativeRead.field(response,"hasMore")))break;
   String next=NativeRead.string(NativeRead.field(response,"nextPageToken"));if(next.isEmpty()||!tokens.add(next))break;page=next;
  }
 }
}
