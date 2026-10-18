package cat.narezany.tokkiyou;
import android.app.Dialog;import android.view.View;import android.view.ViewGroup;import android.widget.TextView;
/** One bounded pass when the native share sheet opens, without polling the feed. */
public final class DownloadChoice {
 
 public static void show(Dialog dialog){dialog.show();if(dialog.getClass().getName().startsWith("cat.narezany.tokkiyou.")||dialog.getWindow()==null)return;
  Object post=VideoActions.downloadPost(dialog.getContext());if(post==null)return;guard(dialog,post);View root=dialog.getWindow().getDecorView();root.post(()->bind(root,dialog,post,0,new int[]{512}));for(int delay:new int[]{150,500})root.postDelayed(()->{if(dialog.isShowing())bind(root,dialog,post,0,new int[]{512});},delay);
 }
 static boolean saveLabel(CharSequence value){if(value==null)return false;String text=value.toString().trim().toLowerCase(java.util.Locale.ROOT);return text.equals("скачать")||text.equals("download")||text.equals("завантажити")||text.equals("сохранить видео")||text.equals("save video")||text.equals("скачать видео")||text.equals("завантажити відео")||text.equals("зберегти відео")||text.equals("download video")||text.equals("скачать фото")||text.equals("сохранить фото")||text.equals("download photo")||text.equals("save photo")||text.equals("завантажити фото")||text.equals("зберегти фото");}
 private static void bind(View view,Dialog dialog,Object post,int depth,int[] budget){if(post==null||depth>20||budget[0]--<=0)return;
  CharSequence label=view instanceof TextView?((TextView)view).getText():view.getContentDescription();
  if(saveLabel(label)){View target=view;for(int i=0;i<4&&!target.hasOnClickListeners()&&target.getParent() instanceof View;i++)target=(View)target.getParent();
   if(target.hasOnClickListeners()&&cell(target)&&!offline(dialog)){final View button=target;target.setOnClickListener(v->{dialog.dismiss();VideoActions.chooseDownload(button.getContext(),post);});return;}
  }
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)bind(group.getChildAt(i),dialog,post,depth+1,budget);}
 }

 /**
  * Whether a view is one cell of the share sheet's row and not something wider.
  *
  * "Download" is also what the button says on the sheet that saves videos for
  * watching offline, and there the nearest thing with a click on it can be
  * the whole sheet: every tap on it then opened this menu, for the video
  * playing underneath. A cell of the share row is a fifth of the screen
  * across; anything approaching half of it is not one.
  */
 static boolean cell(View target){
  View root=target.getRootView();int across=root==null?0:root.getWidth();
  return across<=0||target.getWidth()<=0||target.getWidth()<across*0.45f;
 }
 private static final java.util.Map<Dialog,Boolean> offlineSheets=new java.util.WeakHashMap<>();
 /** Whether a dialog is the offline-videos sheet, decided once it has something in it. */
 private static boolean offline(Dialog dialog){
  synchronized(offlineSheets){Boolean known=offlineSheets.get(dialog);if(known!=null)return known;}
  android.view.Window window=dialog.getWindow();if(window==null)return false;
  int[] budget={512};boolean found=offlineIn(window.getDecorView(),0,budget);
  // an empty sheet says nothing yet: ask again when it has been filled
  if(found||budget[0]<500)synchronized(offlineSheets){offlineSheets.put(dialog,found);}
  return found;
 }
 static boolean offlineWords(CharSequence value){
  if(value==null||value.length()<6)return false;String text=value.toString().toLowerCase(java.util.Locale.ROOT);
  return text.contains("офлайн")||text.contains("оффлайн")||text.contains("offline");
 }
 private static boolean offlineIn(View view,int depth,int[] budget){
  if(view==null||depth>24||budget[0]--<=0)return false;
  if(view.getClass().getName().contains(".offlinemode."))return true;
  if(view instanceof TextView&&offlineWords(((TextView)view).getText()))return true;
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(offlineIn(group.getChildAt(i),depth+1,budget))return true;}
  return false;
 }

 private static final java.util.Map<android.view.Window,Boolean> guarded=new java.util.WeakHashMap<>();
 /**
  * The tap itself is taken at the window, before TikTok's button can see it: a tap that ends on the
  * "save" button is turned into a CANCEL for the stock code (so its click never fires and nothing is
  * saved) and opens the quality menu instead. A swipe that starts on the button still scrolls the row.
  * Rebinding the click (above) stays as a fallback for keyboard and accessibility clicks.
  */
 private static void guard(final Dialog dialog,final Object post){
  final android.view.Window window=dialog.getWindow();if(window==null)return;
  final android.view.Window.Callback base=window.getCallback();if(base==null)return;
  synchronized(guarded){if(guarded.containsKey(window))return;guarded.put(window,Boolean.TRUE);}
  final float[] down=new float[2];final long[] when={0L};final android.graphics.Rect[] zone={null};
  final int slop=android.view.ViewConfiguration.get(dialog.getContext()).getScaledTouchSlop();
  window.setCallback((android.view.Window.Callback)java.lang.reflect.Proxy.newProxyInstance(base.getClass().getClassLoader(),new Class<?>[]{android.view.Window.Callback.class},(proxy,method,args)->{
   if(method.getName().equals("dispatchTouchEvent")&&args!=null&&args.length==1&&args[0] instanceof android.view.MotionEvent){
    android.view.MotionEvent e=(android.view.MotionEvent)args[0];
    boolean take=false;
    try{
     int action=e.getActionMasked();
     if(action==android.view.MotionEvent.ACTION_DOWN){zone[0]=offline(dialog)?null:saveZone(window.getDecorView(),(int)e.getRawX(),(int)e.getRawY(),new int[]{512});down[0]=e.getRawX();down[1]=e.getRawY();when[0]=e.getEventTime();}
     else if(zone[0]!=null){
      if(action==android.view.MotionEvent.ACTION_CANCEL||action==android.view.MotionEvent.ACTION_POINTER_DOWN)zone[0]=null;
      else if(action==android.view.MotionEvent.ACTION_MOVE){if(Math.hypot(e.getRawX()-down[0],e.getRawY()-down[1])>slop)zone[0]=null;}
      else if(action==android.view.MotionEvent.ACTION_UP){
       boolean hit=zone[0].contains((int)e.getRawX(),(int)e.getRawY())&&e.getEventTime()-when[0]<600L;zone[0]=null;
       if(hit){android.view.MotionEvent cancel=android.view.MotionEvent.obtain(e);cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
        try{method.invoke(base,cancel);}finally{cancel.recycle();}
        final android.content.Context context=dialog.getContext();
        window.getDecorView().post(()->{try{dialog.dismiss();VideoActions.chooseDownload(context,post);}catch(Throwable error){Diary.note("download menu: "+error);}});
        take=true;
       }
      }
     }
    }catch(Throwable error){Diary.note("download guard: "+error);}
    if(take)return Boolean.TRUE;
   }
   try{return method.invoke(base,args);}catch(java.lang.reflect.InvocationTargetException x){throw x.getCause();}
  }));
 }
 /** Screen rectangle of the stock "save" button (the whole tappable cell), or null if the tap is elsewhere. */
 private static android.graphics.Rect saveZone(View view,int x,int y,int[] budget){
  if(view==null||budget[0]--<=0||view.getVisibility()!=View.VISIBLE)return null;
  CharSequence label=view instanceof TextView?((TextView)view).getText():view.getContentDescription();
  if(saveLabel(label)){View target=view;for(int i=0;i<4&&!(target.isClickable()||target.hasOnClickListeners())&&target.getParent() instanceof View;i++)target=(View)target.getParent();
   if(!cell(target))return null;
   int[] at=new int[2];target.getLocationOnScreen(at);android.graphics.Rect rect=new android.graphics.Rect(at[0],at[1],at[0]+target.getWidth(),at[1]+target.getHeight());
   if(rect.contains(x,y))return rect;
  }
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.graphics.Rect found=saveZone(group.getChildAt(i),x,y,budget);if(found!=null)return found;}}
  return null;
 }
}
