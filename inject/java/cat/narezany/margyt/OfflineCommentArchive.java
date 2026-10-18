package cat.narezany.margyt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Small, independently readable sidecar; never contains account credentials. */
final class OfflineCommentArchive {
 static final int MAX_COUNT=1000, MAX_BYTES=16*1024*1024;
 static final class Row {
  final String id,author,handle,text,nativeJson;final long likes,date;
  Row(String id,String author,String handle,String text,long likes,long date){this(id,author,handle,text,likes,date,"");}
  Row(String id,String author,String handle,String text,long likes,long date,String nativeJson){this.id=id;this.author=author;this.handle=handle;this.text=text;this.likes=Math.max(0,likes);this.date=date;this.nativeJson=nativeJson!=null&&nativeJson.length()<=8192?nativeJson:"";}
 }
 static int count(String value){try{int n=Integer.parseInt(value.trim());return n>=1&&n<=MAX_COUNT?n:-1;}catch(Exception e){return -1;}}
 static List<Row> popular(Collection<Row> rows,int limit){
  LinkedHashMap<String,Row> unique=new LinkedHashMap<String,Row>();
  for(Row row:rows)if(row!=null&&!row.id.isEmpty()&&!unique.containsKey(row.id))unique.put(row.id,row);
  ArrayList<Row> sorted=new ArrayList<Row>(unique.values());
  Collections.sort(sorted,(a,b)->Long.compare(b.likes,a.likes));
  return new ArrayList<Row>(sorted.subList(0,Math.min(Math.max(0,limit),sorted.size())));
 }
 static String quote(String value){
  StringBuilder out=new StringBuilder("\"");
  for(int i=0;i<Math.min(value.length(),8192);i++){char c=value.charAt(i);switch(c){case '\\':out.append("\\\\");break;case '"':out.append("\\\"");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<32)out.append(String.format(Locale.US,"\\u%04x",(int)c));else out.append(c);}}
  return out.append('"').toString();
 }
 static void write(File file,String aid,String title,String video,int requested,List<Row> rows)throws IOException {
  List<Row> selected=popular(rows,requested);
  StringBuilder json=new StringBuilder("{\"schema\":1,\"video_id\":").append(quote(aid)).append(",\"title\":").append(quote(title)).append(",\"video_file\":").append(quote(video)).append(",\"saved_at\":").append(System.currentTimeMillis()).append(",\"requested\":").append(requested).append(",\"order\":\"likes_desc_from_native_results\",\"comments\":[");
  StringBuilder plain=new StringBuilder(title).append("\nhttps://www.tiktok.com/video/").append(aid).append("\n\n");
  for(int i=0;i<selected.size();i++){
   Row row=selected.get(i);if(i>0)json.append(',');
   json.append("{\"id\":").append(quote(row.id)).append(",\"author\":").append(quote(row.author)).append(",\"handle\":").append(quote(row.handle)).append(",\"text\":").append(quote(row.text)).append(",\"likes\":").append(row.likes).append(",\"date\":").append(row.date);
   if(!row.nativeJson.isEmpty())json.append(",\"native_json\":").append(quote(row.nativeJson));
   json.append('}');
   plain.append(row.author).append(" · @").append(row.handle).append(" · ♥ ").append(row.likes).append('\n').append(row.text).append("\n\n");
  }
  json.append("]}");
  File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("archive directory");
  File temp=new File(parent,file.getName()+".tmp");
  try{
   try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(temp))){entry(zip,"comments.json",json.toString());entry(zip,"comments.txt",plain.toString());}
   // Same-directory replacement is atomic on Android/Linux and keeps the previous archive on failure.
   if(!temp.renameTo(file))throw new IOException("archive commit");
  }finally{if(temp.exists())temp.delete();}
 }
 private static void entry(ZipOutputStream zip,String name,String content)throws IOException{
  byte[] bytes=content.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("archive too large");
  zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
 }
 static String read(File file)throws IOException{
  try(ZipFile zip=new ZipFile(file)){ZipEntry entry=zip.getEntry("comments.json");if(entry==null||entry.getSize()>MAX_BYTES)throw new IOException("invalid archive");
   try(InputStream input=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("archive too large");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
   }
  }
 }
}
