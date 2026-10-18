package cat.narezany.margyt;
import android.content.*;import android.net.Uri;import android.os.*;import android.provider.MediaStore;import java.io.*;
/** Stream into MediaStore; failed and cancelled writes leave no pending rows. */
final class MediaFiles {
 static String save(Context context,File source,String name,String mime,DownloadTasks.Control control)throws Exception{
  if(Build.VERSION.SDK_INT<29){
   if(context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Storage permission required");
   String root=mime.startsWith("audio")?Environment.DIRECTORY_MUSIC:mime.startsWith("image")?Environment.DIRECTORY_PICTURES:Environment.DIRECTORY_MOVIES;File dir=new File(Environment.getExternalStoragePublicDirectory(root),Gallery.FOLDER);dir.mkdirs();
   File target=new File(dir,name);boolean okay=false;
   try{try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(target)){copy(in,out,control);}okay=true;}finally{if(!okay)target.delete();}
   android.media.MediaScannerConnection.scanFile(context,new String[]{target.getAbsolutePath()},new String[]{mime},null);return root+"/"+Gallery.FOLDER;
  }
  final String where=(mime.startsWith("audio")?Environment.DIRECTORY_MUSIC:mime.startsWith("image")?Environment.DIRECTORY_PICTURES:Environment.DIRECTORY_MOVIES)+"/"+Gallery.FOLDER;
  ContentResolver resolver=context.getContentResolver();ContentValues values=new ContentValues();
  values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.IS_PENDING,1);
  values.put(MediaStore.MediaColumns.RELATIVE_PATH,(mime.startsWith("audio")?Environment.DIRECTORY_MUSIC:mime.startsWith("image")?Environment.DIRECTORY_PICTURES:Environment.DIRECTORY_MOVIES)+"/"+Gallery.FOLDER);
  Uri collection=mime.startsWith("audio")?MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY):mime.startsWith("image")?MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY):MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
  Uri row=resolver.insert(collection,values);if(row==null)throw new IOException("Cannot create media row");boolean okay=false;
  try{
   try(InputStream in=new FileInputStream(source);OutputStream out=resolver.openOutputStream(row)){if(out==null)throw new IOException("Cannot open media row");copy(in,out,control);}
   if(control!=null&&control.cancelled())throw new IOException("Cancelled before publish");
   values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);if(resolver.update(row,values,null,null)<1)throw new IOException("Cannot publish media row");okay=true;
  }finally{if(!okay)resolver.delete(row,null,null);}
  return where;
 }
 private static void copy(InputStream in,OutputStream out,DownloadTasks.Control control)throws IOException{
  byte[] buffer=new byte[65536];int count;
  while((count=in.read(buffer))!=-1){if(control!=null && control.cancelled())throw new IOException("Cancelled");out.write(buffer,0,count);}
  if(control!=null && control.cancelled())throw new IOException("Cancelled");out.flush();
 }
}
