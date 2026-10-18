package cat.narezany.tokkiyou;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.ImageView;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.*;

/** TikTok's live repost texture, with its verified APK asset as the initial fallback. */
final class NativeRepostVisual {
 private NativeRepostVisual(){}
 private static WeakReference<ImageView> latest=new WeakReference<>(null);
 private static Bitmap fallback;
 private static boolean fallbackRead;
 private static int revision;
 private static final Map<View,Boolean> previews=new WeakHashMap<>();
 static void remember(Object component){Object image=NativeRead.field(component,"LLLJL");if(image instanceof ImageView&&latest.get()!=image){latest=new WeakReference<>((ImageView)image);changed();}}
 static int generation(){return revision;}
 static void watch(View view){previews.put(view,Boolean.TRUE);}
 static void changed(){revision++;for(View view:previews.keySet())if(view!=null)view.invalidate();}
 static Drawable icon(Context c,Object component){
  Object value=component==null?latest.get():NativeRead.field(component,"LLLJL");
  if(value instanceof ImageView){ImageView source=(ImageView)value;Drawable original=source.getDrawable();
   if(original!=null){Drawable.ConstantState state=original.getConstantState();if(state!=null){Drawable copy=state.newDrawable(c.getResources()).mutate();copy.setState(original.getState());copy.setLevel(original.getLevel());return copy;}}
   int width=source.getWidth(),height=source.getHeight();
   if(width>0&&height>0&&width<=256&&height<=256)try{Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);source.draw(new Canvas(image));return new BitmapDrawable(c.getResources(),image);}catch(Throwable error){Diary.note("repost native texture: "+error);}
  }
  if(!fallbackRead){fallbackRead=true;try(java.io.InputStream stream=c.getAssets().open("images/icon_media_repost.png")){fallback=BitmapFactory.decodeStream(stream);}catch(Throwable error){Diary.note("repost asset unavailable: "+error);}}
  return fallback==null?null:new BitmapDrawable(c.getResources(),fallback);
 }
}
