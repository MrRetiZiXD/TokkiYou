package cat.narezany.tokkiyou;
import android.content.Context;import android.graphics.Bitmap;import android.view.*;
/** Capture the player's surface, never a screenshot of the window. */
final class FrameCapture {
 static final String KEY="frame_quality";
 static int quality(){try{int v=AccountAppearance.prefs(TokkiYou.context()).getInt(KEY,720);return v==480||v==1080?v:720;}catch(Throwable ignored){return 720;}}
 private static volatile boolean busy;
 static void capture(View source,String aid){
  if(busy)return;
  View player=null;View cursor=source;
  for(int i=0;i<8 && cursor!=null;i++){
   player=find(cursor,0,new int[]{512});if(player!=null)break;
   cursor=cursor.getParent() instanceof View?(View)cursor.getParent():null;
  }
  if(player==null){Screen.say(Text.FRAME_NOT_AVAILABLE);return;}
  int cap=quality();
  cap=cap<=480?480:cap>=1080?1080:720;
  float scale=Math.min(1f,(float)cap/Math.max(player.getWidth(),player.getHeight()));
  int width=Math.max(1,Math.round(player.getWidth()*scale)),height=Math.max(1,Math.round(player.getHeight()*scale));
  android.content.Context context=source.getContext().getApplicationContext();busy=true;
  try{
   if(player instanceof TextureView){
    Bitmap bitmap=((TextureView)player).getBitmap(width,height);if(bitmap==null){bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);copyWindow(source,player,bitmap,aid);}else save(context,bitmap,aid);
   }else if(android.os.Build.VERSION.SDK_INT>=24){
    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
    try{copySurface((SurfaceView)player,source,bitmap,aid,0);}catch(Throwable error){bitmap.recycle();throw error;}
   }else throw new IllegalStateException("Surface capture needs Android 7");
  }catch(Throwable error){busy=false;Diary.note("frame capture: "+error);Screen.say(Text.FRAME_NOT_AVAILABLE);}
 }
 private static void copySurface(SurfaceView surface,View source,Bitmap bitmap,String aid,int attempt){
  PixelCopy.request(surface,bitmap,result->{
   if(result==PixelCopy.SUCCESS){save(source.getContext().getApplicationContext(),bitmap,aid);return;}
   if(attempt==0 && surface.isAttachedToWindow()){
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->copySurface(surface,source,bitmap,aid,1),120L);return;
   }
   if(android.os.Build.VERSION.SDK_INT>=26)copyWindow(source,surface,bitmap,aid);
   else{bitmap.recycle();busy=false;Screen.say(Text.FRAME_NOT_AVAILABLE);}
  },new android.os.Handler(android.os.Looper.getMainLooper()));
 }
 /** SurfaceCopy can miss protected/overlapped player layers; retry from the composed window. */
 private static void copyWindow(View source,View player,Bitmap bitmap,String aid){
  try{
   if(android.os.Build.VERSION.SDK_INT<26)throw new IllegalStateException("Window PixelCopy unavailable");
   View root=source.getRootView();if(root==null||!root.isAttachedToWindow())throw new IllegalStateException("Player window detached");
   int[] rootAt=new int[2],playerAt=new int[2];root.getLocationOnScreen(rootAt);player.getLocationOnScreen(playerAt);
   android.graphics.Rect crop=new android.graphics.Rect(playerAt[0]-rootAt[0],playerAt[1]-rootAt[1],playerAt[0]-rootAt[0]+player.getWidth(),playerAt[1]-rootAt[1]+player.getHeight());
   if(!crop.intersect(0,0,root.getWidth(),root.getHeight()))throw new IllegalStateException("Player outside window");
   android.view.Window window=null;Context context=source.getContext();while(context instanceof android.content.ContextWrapper){if(context instanceof android.app.Activity){window=((android.app.Activity)context).getWindow();break;}context=((android.content.ContextWrapper)context).getBaseContext();}
   if(window==null)throw new IllegalStateException("Player window unavailable");
   PixelCopy.request(window,crop,bitmap,result->{if(result==PixelCopy.SUCCESS)save(source.getContext().getApplicationContext(),bitmap,aid);else{bitmap.recycle();busy=false;Diary.note("frame window copy: "+result);Screen.say(Text.FRAME_NOT_AVAILABLE);}},new android.os.Handler(android.os.Looper.getMainLooper()));
  }catch(Throwable error){bitmap.recycle();busy=false;Diary.note("frame window fallback: "+error);Screen.say(Text.FRAME_NOT_AVAILABLE);}
 }
 private static View find(View v,int depth,int[] budget){
  if(v==null || !v.isShown() || depth>24 || budget[0]--<=0)return null;
  if((v instanceof TextureView || v instanceof SurfaceView) && v.getWidth()>80 && v.getHeight()>80)return v;
  if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),depth+1,budget);if(found!=null)return found;}}
  return null;
 }
 private static void save(android.content.Context c,Bitmap bitmap,String aid){
  String key="frame_"+java.util.UUID.randomUUID();Screen.progress(key,Text.SAVE_FRAME,-1);
  Net.away("frame",()->{
   boolean success=false;
   try(java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
    if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,out))throw new java.io.IOException("Frame compression failed");
    success=Gallery.save(c,out.toByteArray(),"frame_"+System.currentTimeMillis()+"_"+java.util.UUID.randomUUID()+".jpg","image/jpeg")!=null;
    if(!success)Screen.say(Text.MEDIA_FAILED);
   }catch(Throwable error){Diary.note("frame save: "+error);Screen.say(Text.MEDIA_FAILED);}
   finally{bitmap.recycle();busy=false;if(success)Screen.progressDone(key);else Screen.progressGone(key);}
  });
 }
}
