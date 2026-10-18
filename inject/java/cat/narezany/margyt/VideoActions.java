package cat.narezany.margyt;
import android.content.Context;import android.view.View;import android.view.ViewGroup;import java.lang.ref.WeakReference;
/** Extra actions on a long press of the native Share slot. Native tap stays intact. */
public final class VideoActions {
 private static volatile Object selected;
 private static volatile WeakReference<View> selectedView=new WeakReference<View>(null);
 private static volatile boolean zenMode;
 private static ViewGroup zenRoot;
 private static final java.util.List<HiddenView> zenHidden=new java.util.ArrayList<HiddenView>();
 private static final class HiddenView {final View view;final int visibility;HiddenView(View v){view=v;visibility=v.getVisibility();}}
 static void accountChanged(){selected=null;selectedView=new WeakReference<View>(null);}
 static void selected(Object holder,Object post){
  Object content=NativeRead.get(holder,"getContentView");
  if(content instanceof View){selected=post;selectedView=new WeakReference<View>((View)content);Repost.reload();}
 }
 public static String currentId(){Object post=downloadPost();return post==null?null:NativeRead.aid(post);}
 public static long currentPublishedTime(){Object post=downloadPost();return post==null?0:NativeRead.number(NativeRead.get(post,"getCreateTime"));}
 public static void captureShare(Object component){Object content=NativeRead.get(component,"getContentView"),post=NativeRead.get(NativeRead.item(component),"getAweme");if(content instanceof View&&post!=null){selected=post;selectedView=new WeakReference<View>((View)content);}}
 static Object downloadPost(){View row=selectedView.get();return row!=null&&row.isShown()&&row.isAttachedToWindow()?selected:null;}
 /**
  * The post a dialog's "save" is about, or nothing when the dialog belongs to another screen.
  *
  * A photo in a comment opens on a screen of its own, with the video still alive underneath it, and that
  * screen's menu has a "save photo" too. Taking that one over offered the video behind instead of the photo.
  */
 static Object downloadPost(Context from){
  Object post=downloadPost();if(post==null)return null;
  View row=selectedView.get();android.app.Activity asked=activity(from),shown=row==null?null:activity(row.getContext());
  return asked!=null&&shown!=null&&asked!=shown?null:post;
 }
 private static android.app.Activity activity(Context context){
  for(int i=0;i<12&&context!=null;i++){
   if(context instanceof android.app.Activity)return (android.app.Activity)context;
   if(!(context instanceof android.content.ContextWrapper))return null;
   context=((android.content.ContextWrapper)context).getBaseContext();
  }
  return null;
 }
 public static void bindNative(Object component){
  Object content=NativeRead.get(component,"getContentView");
  if(content instanceof View){View root=(View)content;View.OnLongClickListener hold=v->{Object post=NativeRead.get(NativeRead.item(component),"getAweme");show(v,post);return true;};
   root.setOnLongClickListener(hold);
   for(int id:new int[]{0x7f0a76eb,0x7f0a76d4,0x7f0a5f35}){View target=root.findViewById(id);if(target!=null)target.setOnLongClickListener(hold);}
  }
 }
 private static void show(View anchor,final Object post){
  View row=selectedView.get();
  if(post==null || NativeRead.aid(post).length()==0){Screen.say(Text.VIDEO_NOT_READY);return;}
  final View selectedRow=row!=null&&row.isShown()&&row.getRootView()==anchor.getRootView()&&NativeRead.aid(post).equals(NativeRead.aid(selected))?row:anchor;
  Context c=anchor.getContext();Panel panel=Panel.with(c,Skin.remembered(c),Text.VIDEO_ACTIONS);
   panel.item("download",Text.ACT_DOWNLOAD,Text.ACT_DOWNLOAD_HINT,()->chooseDownload(c,post));
 panel.item("play_circle",Text.ACT_PLAYBACK,Text.ACT_PLAYBACK_HINT,()->playback(c));
  panel.item("image",Text.SAVE_FRAME,Text.ACT_FRAME_HINT,()->FrameCapture.capture(selectedRow,NativeRead.aid(post)));
  panel.item("visibility_off",Text.ACT_HIDE,Text.ACT_HIDE_HINT,()->{
   Panel more=Panel.with(c,Skin.remembered(c),Text.ACT_HIDE);
   if(NativeRead.number(NativeRead.get(post,"getRecommendCardType"))>0)more.item("block",Text.BLOCK_SUGGESTION,null,()->FeedBlacklist.addCard(post));
   more.danger("block",Text.BLOCK_AUTHOR,null,()->FeedBlacklist.addPost(post));more.quiet(Text.CLOSE,null).show();
  });
  if(Margy.option(Margy.KEY_ZEN_MODE))panel.item("visibility_off",Text.ZEN_MODE,
          zenMode?Text.ZEN_MODE_OFF:Text.ZEN_MODE_ON,()->toggleZen(anchor));
  if(Margy.option(Margy.KEY_CLEAN_LINKS))panel.item("link",Text.COPY_VIDEO_LINK,null,()->copyVideoLink(c,post));
  if(Margy.option(Margy.KEY_COPY_METADATA))panel.item("content_copy",Text.COPY_METADATA,null,()->copyMetadata(c,post));
  panel.quiet(Text.CLOSE,null);panel.show();
 }
 private static void toggleZen(View anchor){
  if(zenMode){
   disableZen();Screen.say(Text.ZEN_MODE_OFF);return;
  }
  ViewParentWalk:
  for(View current=anchor;current!=null;){
   if(current instanceof ViewGroup&&containsVideo((ViewGroup)current)){
    zenRoot=(ViewGroup)current;hideOverlays(zenRoot,anchor);break ViewParentWalk;
   }
   android.view.ViewParent parent=current.getParent();
   current=parent instanceof View?(View)parent:null;
  }
  zenMode=true;FeedRail.zenMode(true);Screen.say(Text.ZEN_MODE_ON);
 }
 static void disableZen(){
  for(HiddenView saved:zenHidden)if(saved.view!=null)saved.view.setVisibility(saved.visibility);
  zenHidden.clear();zenRoot=null;zenMode=false;FeedRail.zenMode(false);
 }
 private static boolean containsVideo(View view){
  if(view instanceof android.view.SurfaceView||view instanceof android.view.TextureView)return true;
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsVideo(group.getChildAt(i)))return true;}
  return false;
 }
 private static boolean contains(View tree,View target){
  if(tree==target)return true;
  if(tree instanceof ViewGroup){ViewGroup group=(ViewGroup)tree;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),target))return true;}
  return false;
 }
 private static void hideOverlays(ViewGroup root,View anchor){
  for(int i=0;i<root.getChildCount();i++){
   View child=root.getChildAt(i);
   if(child==anchor||contains(child,anchor)||containsVideo(child)){
    if(child instanceof ViewGroup)hideOverlays((ViewGroup)child,anchor);
   }else if(child.getVisibility()!=View.GONE){
    zenHidden.add(new HiddenView(child));child.setVisibility(View.GONE);
   }
  }
 }
 static String cleanVideoUrl(String value){
  if(value==null)return "";
  try{
   android.net.Uri original=android.net.Uri.parse(value);
   android.net.Uri.Builder clean=original.buildUpon().clearQuery();
   for(String key:original.getQueryParameterNames()){
    if("_t".equalsIgnoreCase(key)||"_r".equalsIgnoreCase(key))continue;
    for(String item:original.getQueryParameters(key))clean.appendQueryParameter(key,item);
   }
   return clean.build().toString();
  }catch(Throwable error){Diary.note("video link cleanup: "+error);return value;}
 }
 private static void copyVideoLink(Context context,Object post){
  String aid=NativeRead.aid(post);if(aid.length()==0){Screen.say(Text.VIDEO_NOT_READY);return;}
  Object shared=NativeRead.get(post,"getShareUrl");
  String url=NativeRead.string(shared);
  if(url.length()==0){Object author=NativeRead.get(post,"getAuthor");String handle=NativeRead.string(NativeRead.get(author,"getUniqueId"));
   url="https://www.tiktok.com/"+(handle.length()>0?"@"+handle+"/":"")+"video/"+aid;}
  if(Margy.option(Margy.KEY_CLEAN_LINKS))url=cleanVideoUrl(url);
  copy(context,"TokkiYou",url);
 }
 private static void copyMetadata(Context context,Object post){
  String aid=NativeRead.aid(post);Object author=NativeRead.get(post,"getAuthor"),music=NativeRead.get(post,"getMusic");
  String name=NativeRead.string(NativeRead.get(author,"getNickname"));
  String sound=NativeRead.string(NativeRead.get(music,"getTitle"));
  long seconds=NativeRead.number(NativeRead.get(post,"getCreateTime"));if(seconds>100000000000L)seconds/=1000L;
  String published=seconds>0?new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z",java.util.Locale.getDefault()).format(new java.util.Date(seconds*1000L)):"";
  StringBuilder out=new StringBuilder();
  appendMetadata(out,Text.VIDEO_ID,aid);appendMetadata(out,Text.VIDEO_AUTHOR,name);
  appendMetadata(out,Text.VIDEO_SOUND,sound);appendMetadata(out,Text.VIDEO_PUBLISHED,published);
  copy(context,"TokkiYou",out.toString().trim());
 }
 private static void appendMetadata(StringBuilder text,String label,String value){
  if(value==null||value.length()==0)return;if(text.length()>0)text.append('\n');text.append(label).append(": ").append(value);
 }
 private static void copy(Context context,String label,String value){
  try{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
   if(clipboard==null){Diary.note("video action: clipboard unavailable");return;}
   clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label,value));Screen.say(Text.COPIED);
  }catch(Throwable error){Diary.note("video action copy: "+error);}
 }
 static void playback(Context c){Panel panel=Panel.with(c,Skin.remembered(c),Text.ACT_PLAYBACK);
  panel.item("speed",Text.PLAYER_SPEED,null,()->PlayerTools.choose(c));
  panel.item("fast_rewind",Text.SEEK_BACK,PlayerTools.step()+Local.of(" с"),null,()->PlayerTools.seek(false,false));
  panel.item("fast_forward",Text.SEEK_FORWARD,PlayerTools.step()+Local.of(" с"),null,()->PlayerTools.seek(true,false));
  panel.item("timeline",Text.SEEK_SMOOTH,null,null,()->PlayerTools.seek(false,true));panel.quiet(Text.CLOSE,null).show();
 }
 static void chooseDownload(Context c,Object post){
  if(post==null){Screen.say(Text.VIDEO_NOT_READY);return;}
  java.util.List<DownloadQuality.Choice> photos=DownloadQuality.photos(post,Download.isEnabled());
  // A photo post carries a slideshow "video" whose address is not a file: offering it as the best
  // quality only ever ended in an error. Photos and the soundtrack are what can be saved.
  final boolean photoPost=!photos.isEmpty();
  Object video=photoPost?null:NativeRead.get(post,"getVideo");
  java.util.List<DownloadQuality.Choice> qualities=new java.util.ArrayList<>();
  if(!photoPost){
   Object base=NativeRead.get(video,Download.isEnabled()?"getDownloadNoWatermarkAddr":"getDownloadAddr");
   if(DownloadQuality.first(base)==null)base=NativeRead.get(video,"getPlayAddr");
   qualities=DownloadQuality.choices(video,DownloadQuality.first(base));
   if(!qualities.isEmpty()&&qualities.get(0).best)qualities.get(0).alts=DownloadQuality.all(base);
   try{Diary.note("download sizes offered: "+qualities.size());}catch(Throwable ignored){}
  }
  final java.util.List<DownloadQuality.Choice> every=photos;
  DownloadSheet.show(c,qualities,photos,video!=null&&qualities.isEmpty(),()->save(post,0),choice->saveAddress(post,choice),choice->savePhoto(post,choice),()->save(post,3),()->saveAllPhotos(post,every));
 }
 static void save(Object post,boolean forceAudio){
  save(post,forceAudio?3:0);
 }
 static void save(Object post,int mode){
  Object video=NativeRead.get(post,"getVideo");boolean audio=mode==3;
  if(post==null||(!audio&&video==null)){Screen.say(Text.VIDEO_NOT_READY);return;}
  java.util.List<String> urls=new java.util.ArrayList<>();
  if(audio){String one=DownloadQuality.audio(post,video);if(one!=null)urls.add(one);}
  else{
   Object base=NativeRead.get(video,Download.isEnabled()?"getDownloadNoWatermarkAddr":"getDownloadAddr");
   if(DownloadQuality.first(base)==null)base=NativeRead.get(video,"getPlayAddr");
   urls=DownloadQuality.all(DownloadQuality.video(video,base,mode));
  }
  if(urls.isEmpty()){Screen.say(audio?Text.AUDIO_NOT_AVAILABLE:Text.VIDEO_NOT_READY);return;}
  Context context=Margy.context();if(context!=null)transfer(context.getApplicationContext(),urls,audio?1:0,NativeRead.aid(post));
 }

 private static void savePhoto(Object post,DownloadQuality.Choice choice){
  if(post==null||choice==null||choice.candidates().isEmpty()){Screen.say(Text.PHOTO_NOT_AVAILABLE);return;}
  Context context=Margy.context();if(context!=null)transfer(context.getApplicationContext(),choice.candidates(),2,NativeRead.aid(post));
 }
 private static void saveAddress(Object post,DownloadQuality.Choice choice){
  if(post==null||choice==null||choice.candidates().isEmpty()){Screen.say(Text.VIDEO_NOT_READY);return;}
  Context context=Margy.context();if(context!=null)transfer(context.getApplicationContext(),choice.candidates(),0,NativeRead.aid(post));
 }
 /** One file from the first address that works; null when cancelled. Throws the last error otherwise. */
 private static String fetchOne(Context c,String key,java.util.List<String> addresses,int kind,String aid,java.io.File temporary,DownloadTasks.Control control,int index,int total,String suffix)throws Exception{
  final boolean audio=kind==1,image=kind==2;final int slot=index,count=Math.max(1,total);
  Throwable last=null;
  for(String address:addresses){
   if(control.cancelled())return null;
   try{
    temporary.delete();
    if(!Net.download(address,temporary,(percent,got,size)->{int overall=(slot*100+percent)/count;DownloadTasks.progress(key,overall);Screen.progress(key,Text.DOWNLOADING,overall);},control)){last=new java.io.IOException("Download failed or cancelled");continue;}
    String[] format=audio?DownloadQuality.audioFormat(temporary):image?DownloadQuality.imageFormat(temporary):DownloadQuality.videoFormat(temporary);
    String base=aid==null||aid.length()==0?"post":aid.replaceAll("[^A-Za-z0-9_-]","_");
    return MediaFiles.save(c,temporary,"tokkiyou_"+base+suffix+"_"+java.util.UUID.randomUUID()+"."+format[0],format[1],control);
   }catch(Throwable error){last=error;Diary.note("media source failed: "+error);}
  }
  if(last instanceof Exception)throw (Exception)last;
  throw new java.io.IOException(String.valueOf(last));
 }
 private static void saveAllPhotos(Object post,java.util.List<DownloadQuality.Choice> photos){
  if(post==null||photos==null||photos.isEmpty()){Screen.say(Text.PHOTO_NOT_AVAILABLE);return;}
  Context context=Margy.context();if(context==null)return;
  final Context c=context.getApplicationContext();final String aid=NativeRead.aid(post);
  final java.util.List<DownloadQuality.Choice> list=new java.util.ArrayList<>(photos);
  final String key="media_"+java.util.UUID.randomUUID();
  final DownloadTasks.Control control=DownloadTasks.begin(key,Text.allPhotos(list.size()),"margyt",()->saveAllPhotos(post,list));
  control.limit=320L*1024L*1024L;Screen.progress(key,Text.DOWNLOADING,0);
  boolean queued=MediaQueue.add(()->{
   int saved=0;String where=null;java.io.File temporary=new java.io.File(c.getCacheDir(),key+".media");
   try{
    for(int i=0;i<list.size()&&!control.cancelled();i++){
     try{String done=fetchOne(c,key,list.get(i).candidates(),2,aid,temporary,control,i,list.size(),"_"+(i+1));if(done!=null){saved++;where=done;}}
     catch(Throwable error){Diary.note("photo "+(i+1)+": "+error);}
     finally{temporary.delete();}
    }
   }finally{
    boolean success=saved>0;
    DownloadTasks.finish(key,success);
    if(success){if(saved<list.size()&&!control.cancelled())Screen.say(Text.photosSaved(saved,list.size()));Screen.progressDone(key,where);}
    else{Screen.progressGone(key);if(!control.cancelled())Screen.say(Text.MEDIA_FAILED);}
   }
  });
  if(!queued){DownloadTasks.finish(key,false);Screen.progressGone(key);Screen.say(Text.MEDIA_FAILED);}
 }
 private static void transfer(Context c,java.util.List<String> addresses,int kind,String aid){
  final boolean audio=kind==1,image=kind==2;
  final String key="media_"+java.util.UUID.randomUUID();
  final DownloadTasks.Control control=DownloadTasks.begin(key,audio?Text.SAVE_AUDIO:image?Text.SAVE_IMAGE:Text.SAVE_VIDEO,"margyt",()->transfer(c,addresses,kind,aid));
  control.limit=320L*1024L*1024L;Screen.progress(key,Text.DOWNLOADING,0);
  boolean queued=MediaQueue.add(()->{
   boolean success=false;String savedTo=null;java.io.File temporary=new java.io.File(c.getCacheDir(),key+".media");
   try{
    savedTo=fetchOne(c,key,addresses,kind,aid,temporary,control,0,1,"");success=savedTo!=null;
    if(!success&&!control.cancelled())throw new java.io.IOException("Download failed or cancelled");
   }catch(Throwable error){Diary.note("media save: "+error);if(!control.cancelled())Screen.say(Text.MEDIA_FAILED);}
   finally{temporary.delete();DownloadTasks.finish(key,success);if(success)Screen.progressDone(key,savedTo);else Screen.progressGone(key);}
  });
  if(!queued){DownloadTasks.finish(key,false);Screen.progressGone(key);Screen.say(Text.MEDIA_FAILED);}
 }
}
