package cat.narezany.margyt;
import android.content.Context;
import android.net.Uri;
import java.io.File;
/** A bounded local audio clip; never prepare media synchronously on the UI thread. */
final class TitleSound {
    private static File file(Context context){return AccountAppearance.file(context,"title-sound");}
    static boolean has(Context context){return file(context).isFile();}
    static boolean take(Context context,Uri uri) {
        File target=file(context),temporary=new File(target.getPath()+".tmp");
        android.media.MediaMetadataRetriever check=new android.media.MediaMetadataRetriever();
        try {
            java.io.InputStream input=context.getContentResolver().openInputStream(uri);
            if(input==null)return false;
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            try{byte[] block=new byte[16384];int n;while((n=input.read(block))!=-1){if(bytes.size()+n>5*1024*1024)return false;bytes.write(block,0,n);}}finally{input.close();}
            if(!Net.save(temporary,bytes.toByteArray()))return false;
            check.setDataSource(temporary.getPath());
            String value=check.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration=value==null?0:Long.parseLong(value);
            if(duration<=0 || duration>30000)return false;
            return temporary.renameTo(target);
        } catch(Throwable error){Diary.note("title sound import: "+error);return false;}
        finally{temporary.delete();try{check.release();}catch(Throwable ignored){}}
    }
    static boolean play(Context context) {
        File path=file(context);if(!path.isFile())return false;
        android.media.MediaPlayer player=new android.media.MediaPlayer();
        try{
            player.setDataSource(path.getPath());
            player.setOnCompletionListener(done->done.release());
            player.setOnErrorListener((done,what,extra)->{done.release();return true;});
            player.setOnPreparedListener(ready->ready.start());player.prepareAsync();return true;
        }catch(Throwable error){player.release();Diary.note("title sound: "+error);return false;}
    }
}
