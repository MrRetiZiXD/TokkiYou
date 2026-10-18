package cat.narezany.margyt;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native offline-cache success -> bounded background comment fetch -> video-ID ZIP. */
public final class OfflineComments {
 private static final String ENABLED="offline_comments_enabled",COUNT="offline_comments_count";
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(10000),r->{Thread t=new Thread(r,"margyt-offline-comments");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
 private static final Set<String> pending=Collections.synchronizedSet(new HashSet<String>());
 private static final ConcurrentHashMap<String,String> errors=new ConcurrentHashMap<String,String>();
 private static WeakReference<Object> active=new WeakReference<Object>(null);
 private static SharedPreferences prefs(){return Margy.context().getSharedPreferences(Margy.PREFS,0);}
 private static String account(){String uid=Account.liveId();return uid!=null&&uid.matches("[0-9]{1,24}")?uid:"local";}
 private static File archive(String account,String aid){return new File(new File(Margy.context().getFilesDir(),"margyt"),"offline-comments-"+account+"-"+aid+".zip");}
 public static void cached(Object post,String videoPath){
  try{
   if(Margy.context()==null||!prefs().getBoolean(ENABLED,false))return;
   String aid=NativeRead.aid(post);if(!aid.matches("[0-9]{1,24}")||videoPath==null||videoPath.isEmpty())return;
   String user=account(),key=user+":"+aid;
   if(!pending.add(key))return;
   int count=Math.max(1,Math.min(OfflineCommentArchive.MAX_COUNT,prefs().getInt(COUNT,10)));
   String description=NativeRead.string(NativeRead.get(post,"getDesc"));String title=description.substring(0,Math.min(512,description.length()));
   String author=NativeRead.uid(NativeRead.get(post,"getAuthor"));
   int type=(int)NativeRead.number(NativeRead.get(post,"getAwemeType"));
   try{WORK.execute(()->{
    try{
     if(!prefs().getBoolean(ENABLED,false)||!user.equals(account()))return;
     File file=archive(user,aid);
     if(file.isFile())try{JSONObject old=new JSONObject(OfflineCommentArchive.read(file));if(old.optInt("requested")==count&&System.currentTimeMillis()-old.optLong("saved_at")<24*60*60*1000L)return;}catch(Exception ignored){}
     List<OfflineCommentArchive.Row> rows=fetch(aid,author,type,count);
     if(!prefs().getBoolean(ENABLED,false)||!user.equals(account()))return;
     OfflineCommentArchive.write(file,aid,title,new File(videoPath).getName(),count,rows);
     prefs().edit().putString("offline_title_"+key,title).apply();errors.remove(key);
    }catch(Throwable e){if(errors.size()>256)errors.clear();errors.put(key,Local.of("Не удалось сохранить комментарии. Повторите загрузку при подключении к интернету."));Diary.note("offline comments: "+e.getClass().getSimpleName());}
    finally{pending.remove(key);}
   });}catch(RejectedExecutionException e){pending.remove(key);Diary.note("offline comments queue full");}
  }catch(Throwable e){Diary.note("offline comments capture: "+e.getClass().getSimpleName());}
 }
 private static List<OfflineCommentArchive.Row> fetch(String aid,String author,int type,int count)throws Exception{
  Class<?> apiType=Class.forName("com.ss.android.ugc.aweme.comment.commentlist.api.CommentApi$RealApi");
  Object factory=Class.forName("com.ss.android.ugc.aweme.comment.commentlist.api.CommentApi").getField("LIZ").get(null);
  Object api=Class.forName("X.01TL").getMethod("create",Class.class).invoke(factory,apiType);
  Method request=null;for(Method m:apiType.getMethods())if(m.getName().equals("fetchCommentListV2")&&m.getParameterTypes().length==36)request=m;
  if(request==null)throw new IOException("native comment API changed");
  LinkedHashMap<String,OfflineCommentArchive.Row> rows=new LinkedHashMap<String,OfflineCommentArchive.Row>();
  long cursor=0,deadline=SystemClock.elapsedRealtime()+120000;
  // Native default ranking supplies the popular/relevant pool; sort its candidates by likes.
  int candidateCount=Math.min(1000,Math.max(50,count));
  for(int page=0;page<40&&rows.size()<candidateCount;page++){
   if(!prefs().getBoolean(ENABLED,false))throw new IOException("disabled");
   if(SystemClock.elapsedRealtime()>=deadline)throw new IOException("comments deadline");
   Class<?>[] types=request.getParameterTypes();Object[] args=new Object[types.length];
   for(int i=0;i<types.length;i++){if(types[i]==int.class)args[i]=0;else if(types[i]==long.class)args[i]=0L;else if(types[i]==boolean.class)args[i]=false;}
   args[0]=aid;args[1]=cursor;args[2]=Math.min(50,candidateCount-rows.size());args[3]="";args[7]="";args[13]=Collections.emptyList();args[15]="offline_mode";args[22]=author;args[23]=type;args[24]=page+1;
   Object task=request.invoke(api,args);Class<?> taskType=Class.forName("X.0Vgo");
   Method completed=taskType.getMethod("LJIILIIL"),wait=taskType.getMethod("LJIJJLI",long.class,TimeUnit.class);
   long pageDeadline=Math.min(deadline,SystemClock.elapsedRealtime()+20000);
   while(!(Boolean)completed.invoke(task)){
    long left=pageDeadline-SystemClock.elapsedRealtime();if(left<=0)throw new IOException("comment request timeout");wait.invoke(task,left,TimeUnit.MILLISECONDS);
   }
   Object error=taskType.getMethod("LJIIJ").invoke(task);if(error!=null)throw new IOException("native comment request failed",error instanceof Throwable?(Throwable)error:null);
   Object response=taskType.getMethod("LJIIJJI").invoke(task);if(response==null||NativeRead.number(NativeRead.get(response,"getStatusCode"))!=0)throw new IOException("comment response rejected");
   Object items=NativeRead.get(response,"getItems");if(!(items instanceof List))throw new IOException("comment response missing items");
   List<?> list=(List<?>)items;int before=rows.size();
   for(Object comment:list){
    String id=NativeRead.string(NativeRead.get(comment,"getCid"));if(id.isEmpty()||rows.containsKey(id))continue;
    Object user=NativeRead.get(comment,"getUser");
    String snapshot="";try{snapshot=NativeJson.encode(comment);}catch(Exception ignored){}
    rows.put(id,new OfflineCommentArchive.Row(id,NativeRead.string(NativeRead.get(user,"getNickname")),NativeRead.string(NativeRead.get(user,"getUniqueId")),NativeRead.string(NativeRead.get(comment,"getText")),NativeRead.number(NativeRead.get(comment,"getDiggCount")),NativeRead.number(NativeRead.get(comment,"getCreateTime")),snapshot));
   }
   long next=NativeRead.number(NativeRead.get(response,"getCursor"));
   if(list.isEmpty()||next<=cursor||rows.size()==before)break;cursor=next;
  }
  return OfflineCommentArchive.popular(rows.values(),count);
 }
 public static void settings(Activity activity){
  try{
   LinearLayout body=new LinearLayout(activity);body.setOrientation(1);int pad=dp(activity,20);body.setPadding(pad,0,pad,0);
   Switch enabled=new Switch(activity);enabled.setText(Local.of("Сохранять комментарии"));enabled.setChecked(prefs().getBoolean(ENABLED,false));body.addView(enabled);
   EditText count=new EditText(activity);count.setInputType(InputType.TYPE_CLASS_NUMBER);count.setSingleLine(true);count.setHint(Local.of("Количество комментариев: 1–1000"));count.setText(String.valueOf(prefs().getInt(COUNT,10)));body.addView(count);
   LinearLayout presets=new LinearLayout(activity);for(int n:new int[]{5,10}){Button b=new Button(activity);b.setText(String.valueOf(n));b.setOnClickListener(v->count.setText(String.valueOf(n)));presets.addView(b,new LinearLayout.LayoutParams(0,dp(activity,44),1));}body.addView(presets);
   TextView warning=new TextView(activity);warning.setText(Local.of("Больше комментариев — дольше загрузка и больше места. Популярные в приоритете. Лайки сохраняются на момент загрузки. Применяется к следующим офлайн-загрузкам."));warning.setTextSize(13);body.addView(warning);
   AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(Local.of("Комментарии офлайн")).setView(body).setNegativeButton(Local.of("Отмена"),null).setPositiveButton(Local.of("Сохранить"),null).create();
   dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{int n=OfflineCommentArchive.count(count.getText().toString());if(n<1){count.setError(Local.of("Введите число от 1 до 1000"));return;}prefs().edit().putBoolean(ENABLED,enabled.isChecked()).putInt(COUNT,n).apply();dialog.dismiss();}));dialog.show();
  }catch(Throwable e){Diary.note("offline settings: "+e.getClass().getSimpleName());}
 }
 public static void content(Object component){active=new WeakReference<Object>(component);}
 public static void closed(Object component){if(active.get()==component)active.clear();}
 private static boolean hasNativeArchive(String aid){
  if(aid==null||!aid.matches("[0-9]{1,24}")||Margy.context()==null)return false;
  Object component=active.get();if(component==null)return false;
  Object pager=NativeRead.get(component,"Bc");
  return aid.equals(NativeRead.aid(NativeRead.get(pager,"LJII")))&&archive(account(),aid).isFile();
 }
 /** Return the native task type before Retrofit tries the network. */
 public static Object nativeTask(String aid){
  if(!hasNativeArchive(aid))return null;
  final String user=account();
  try{return Class.forName("X.0Vgo").getMethod("LIZ",Callable.class).invoke(null,(Callable<Object>)()->nativeResponse(user,aid));}
  catch(Throwable e){Diary.note("offline native task: "+e.getClass().getSimpleName());return null;}
 }
 /** Both current comment implementations consume the same native response. */
 public static Object nativeObservable(String aid){
  if(!hasNativeArchive(aid))return null;
  final String user=account();
  try{
   Class<?> type=Class.forName("X.19ZK");
   Object observable=type.getMethod("LJJJJIZL",Callable.class).invoke(null,(Callable<Object>)()->nativeResponse(user,aid));
   Object scheduler=Class.forName("X.19e5").getMethod("LIZ").invoke(null);
   return type.getMethod("LJLI",Class.forName("X.19do")).invoke(observable,scheduler);
  }catch(Throwable e){Diary.note("offline native observable: "+e.getClass().getSimpleName());return null;}
 }
 private static Object nativeResponse(String user,String aid)throws Exception{
  if(!user.equals(account()))throw new IOException("account changed");
  JSONObject saved=new JSONObject(OfflineCommentArchive.read(archive(user,aid)));
  JSONArray rows=saved.getJSONArray("comments"),comments=new JSONArray();
  for(int i=0;i<Math.min(rows.length(),OfflineCommentArchive.MAX_COUNT);i++){
   JSONObject row=rows.optJSONObject(i);if(row==null)continue;
   JSONObject comment=null;String snapshot=row.optString("native_json");
   if(!snapshot.isEmpty())try{comment=new JSONObject(snapshot);}catch(JSONException ignored){}
   if(comment==null){
    JSONObject author=new JSONObject().put("uid","").put("nickname",row.optString("author")).put("unique_id",row.optString("handle"));
    comment=new JSONObject().put("cid",row.optString("id")).put("text",row.optString("text"))
      .put("digg_count",row.optLong("likes")).put("create_time",row.optLong("date")).put("user",author);
   }
   comment.put("aweme_id",aid);comments.put(comment);
  }
  JSONObject response=new JSONObject().put("status_code",0).put("comments",comments)
    .put("cursor",comments.length()).put("has_more",false).put("total",comments.length());
  return NativeJson.decode(response.toString(),Class.forName("com.ss.android.ugc.aweme.comment.model.CommentItemList"));
 }
 public static View bottom(View nativeView){
  try{
   LinearLayout wrapper=new LinearLayout(nativeView.getContext());wrapper.setOrientation(1);wrapper.setLayoutParams(nativeView.getLayoutParams());
   wrapper.addView(nativeView,new LinearLayout.LayoutParams(-1,-2));
   TextView button=new TextView(nativeView.getContext());button.setText(Local.of("Сохранённые комментарии"));button.setTextSize(13);button.setTextColor(Skin.of(nativeView).text);button.setGravity(Gravity.CENTER);button.setPadding(0,dp(nativeView.getContext(),8),0,dp(nativeView.getContext(),8));
   button.setOnClickListener(v->{Activity activity=activity(v.getContext());if(activity==null)return;Object component=active.get();Object pager=NativeRead.get(component,"Bc");String aid=NativeRead.aid(NativeRead.get(pager,"LJII"));if(aid.matches("[0-9]{1,24}"))show(activity,account(),aid);else library(activity);});wrapper.addView(button,new LinearLayout.LayoutParams(-1,dp(nativeView.getContext(),40)));return wrapper;
  }catch(Throwable e){return nativeView;}
 }
 private static Activity activity(Context context){for(int i=0;i<10;i++){if(context instanceof Activity)return (Activity)context;if(!(context instanceof ContextWrapper))break;Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}return null;}
 private static int dp(Context context,int n){return (int)(n*context.getResources().getDisplayMetrics().density+.5f);}
 private static void message(Activity activity,String text){if(!activity.isFinishing())new AlertDialog.Builder(activity).setTitle(Local.of("Комментарии офлайн")).setMessage(text).setPositiveButton(Local.of("ОК"),null).show();}
 public static void library(Activity activity){
  String user=account();
  if(!MediaQueue.add(()->{
   File dir=new File(Margy.context().getFilesDir(),"margyt");String prefix="offline-comments-"+user+"-";
   File[] files=dir.listFiles((d,name)->name.startsWith(prefix)&&name.endsWith(".zip"));
   if(files==null)files=new File[0];Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
   String[] ids=new String[files.length],titles=new String[files.length];for(int i=0;i<files.length;i++){ids[i]=files[i].getName().substring(prefix.length(),files[i].getName().length()-4);String title=prefs().getString("offline_title_"+user+":"+ids[i],"");titles[i]=title.isEmpty()?Local.of("Видео ")+ids[i]:title.substring(0,Math.min(80,title.length()));}
   MAIN.post(()->{if(activity.isFinishing())return;if(ids.length==0){message(activity,Local.of("Сохранённых комментариев пока нет. Включите сохранение перед загрузкой офлайн-видео."));return;}new AlertDialog.Builder(activity).setTitle(Local.of("Сохранённые комментарии")).setItems(titles,(d,index)->show(activity,user,ids[index])).setNegativeButton(Local.of("Закрыть"),null).show();});
  }))message(activity,Local.of("Очередь занята. Повторите чуть позже."));
 }
 private static void show(Activity activity,String user,String aid){
  File file=archive(user,aid);String key=user+":"+aid;
  if(!file.isFile()){message(activity,pending.contains(key)?Local.of("Комментарии ещё загружаются."):errors.containsKey(key)?errors.get(key):Local.of("Для этого видео комментарии не сохранены. Включите сохранение перед его офлайн-загрузкой."));return;}
  if(!MediaQueue.add(()->{try{
   JSONObject data=new JSONObject(OfflineCommentArchive.read(file));JSONArray rows=data.getJSONArray("comments");
   MAIN.post(()->{
    if(activity.isFinishing())return;LinearLayout body=new LinearLayout(activity);body.setOrientation(1);int pad=dp(activity,16);body.setPadding(pad,0,pad,pad);
    TextView caption=new TextView(activity);caption.setText(Local.of("Сохранено: ")+rows.length()+Local.of(" · лайки на момент загрузки"));caption.setTextSize(12);body.addView(caption);
    if(rows.length()==0){TextView empty=new TextView(activity);empty.setText(Local.of("У видео нет доступных комментариев."));body.addView(empty);}
    ListView list=new ListView(activity);list.setDivider(null);list.setAdapter(new BaseAdapter(){
     public int getCount(){return rows.length();}public Object getItem(int index){return rows.optJSONObject(index);}public long getItemId(int index){return index;}
     public View getView(int index,View reused,ViewGroup parent){TextView text=reused instanceof TextView?(TextView)reused:new TextView(activity);JSONObject row=rows.optJSONObject(index);text.setText(row==null?"":row.optString("author")+" · @"+row.optString("handle")+" · ♥ "+row.optLong("likes")+"\n"+row.optString("text"));text.setTextSize(14);text.setPadding(0,pad,0,pad);return text;}
    });body.addView(list,new LinearLayout.LayoutParams(-1,Math.min(dp(activity,440),(int)(activity.getResources().getDisplayMetrics().heightPixels*.55f))));
    new AlertDialog.Builder(activity).setTitle(Local.of("Комментарии офлайн")).setView(body).setPositiveButton(Local.of("Закрыть"),null).setNeutralButton(Local.of("Поделиться ZIP"),(d,w)->share(activity,file)).show();
   });
  }catch(Exception e){MAIN.post(()->message(activity,Local.of("Не удалось прочитать архив комментариев.")));}}))message(activity,Local.of("Очередь занята. Повторите чуть позже."));
 }
 private static void share(Activity activity,File file){try{android.net.Uri uri=MargyProvider.share(activity,file);if(uri==null)return;Intent intent=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(Intent.createChooser(intent,Local.of("Архив комментариев")));}catch(Exception e){message(activity,Local.of("Не удалось открыть приложение для передачи ZIP."));}}
}
