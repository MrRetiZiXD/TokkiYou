package cat.narezany.margyt;
import android.widget.ImageView;import android.graphics.*;import java.util.*;import java.lang.ref.WeakReference;
final class FriendAvatars {
 private static final Map<String,Bitmap> cache=new LinkedHashMap<>();private static final Map<String,List<WeakReference<ImageView>>> waiting=new HashMap<>();
 private static final java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"margyt-friend-avatar");t.setDaemon(true);return t;});
 static void bind(ImageView view,String url){if(url==null||!url.startsWith("https://"))return;view.setTag(url);
  synchronized(cache){Bitmap known=cache.get(url);if(known!=null){view.setImageBitmap(known);return;}List<WeakReference<ImageView>> list=waiting.get(url);if(list!=null){if(list.size()<64)list.add(new WeakReference<>(view));return;}list=new ArrayList<>();list.add(new WeakReference<>(view));waiting.put(url,list);}
  workers.execute(()->{Bitmap bitmap=null;try{byte[] bytes=Net.bytes(url);if(bytes!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>96)o.inSampleSize*=2;o.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}}catch(Throwable ignored){}
   List<WeakReference<ImageView>> targets;synchronized(cache){targets=waiting.remove(url);if(bitmap!=null){while(cache.size()>=64)cache.remove(cache.keySet().iterator().next());cache.put(url,bitmap);}}final Bitmap image=bitmap;
   if(image!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(targets!=null)for(WeakReference<ImageView> ref:targets){ImageView target=ref.get();if(target!=null&&url.equals(target.getTag()))target.setImageBitmap(image);}});
  });
 }
}
