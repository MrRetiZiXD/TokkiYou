package cat.narezany.margyt;
import java.util.List;
/** Select only URLs actually published in the current native model. */
public final class DownloadQuality {
 public static final String KEY="dl_quality";
 static int mode(){try{return Math.max(0,Math.min(3,AccountAppearance.prefs(Margy.context()).getInt(KEY,0)));}catch(Throwable ignored){return 0;}}
 static void setMode(int value){if(value<0 || value>3)return;AccountAppearance.prefs(Margy.context()).edit().putInt(KEY,value).apply();}
 static String first(Object url){
  Object list=NativeRead.get(url,"getUrlList");if(!(list instanceof List))list=NativeRead.field(url,"urlList");
  if(list instanceof List)for(Object value:(List)list)if(value instanceof String&&((String)value).startsWith("https://"))return (String)value;
  return null;
 }
 private static String musicAddress(Object url){
  if(url==null)return null;
  Object list=NativeRead.get(url,"getUrlList");if(!(list instanceof List))list=NativeRead.field(url,"urlList");
  if(list instanceof List)for(Object value:(List)list){String candidate=secureAddress(value);if(candidate!=null)return candidate;}
  String candidate=secureAddress(NativeRead.get(url,"getUri"));if(candidate!=null)return candidate;
  return secureAddress(NativeRead.field(url,"uri"));
 }
 private static String secureAddress(Object value){
  if(!(value instanceof String))return null;
  String address=((String)value).trim();
  if(address.regionMatches(true,0,"http://",0,7))address="https://"+address.substring(7);
  if(!address.regionMatches(true,0,"https://",0,8))return null;
  try{java.net.URI parsed=new java.net.URI(address);return parsed.getHost()!=null&&parsed.getUserInfo()==null?address:null;}catch(Throwable ignored){return null;}
 }
 static Object video(Object video,Object fallback){
  return video(video,fallback,mode());
 }
 static Object video(Object video,Object fallback,int mode){
  if(mode!=1 && mode!=2)return fallback;
  Object raw=NativeRead.get(video,"getRawBitRate");if(!(raw instanceof List))return fallback;
  Object chosen=null;long score=mode==2?Long.MAX_VALUE:Long.MIN_VALUE;int count=0;
  for(Object option:(List)raw){
   if(count++>=256)break;
   if(Boolean.TRUE.equals(NativeRead.get(option,"isDash")))continue;
   if(NativeRead.number(NativeRead.get(option,"isBytevc1"))!=0)continue;
   Object url=NativeRead.get(option,"getPlayAddr");if(first(url)==null)continue;
   long bitrate=NativeRead.number(NativeRead.get(option,"getBitRate"));if(bitrate<=0)continue;
   if(chosen==null || (mode==2?bitrate<score:bitrate>score)){chosen=url;score=bitrate;}
  }
  return chosen==null?fallback:chosen;
 }
 static final class Choice {final String label,url;final long bitrate;int height;boolean best,hevc;String key;java.util.List<String> alts;Choice(String l,String u,long b){label=l;url=u;bitrate=b;}
  /** The address to try first, then every other mirror TikTok published for the same file. */
  java.util.List<String> candidates(){java.util.ArrayList<String> out=new java.util.ArrayList<>();if(url!=null)out.add(url);if(alts!=null)for(String other:alts)if(other!=null&&!out.contains(other))out.add(other);return out;}
 }
 /** Every usable address of one URL model, https first-listed order kept, plain http upgraded. */
 static java.util.List<String> all(Object url){
  java.util.ArrayList<String> out=new java.util.ArrayList<>();if(url==null)return out;
  Object list=NativeRead.get(url,"getUrlList");if(!(list instanceof List))list=NativeRead.field(url,"urlList");
  if(list instanceof List){int n=0;for(Object value:(List)list){if(n++>=16)break;String address=secureAddress(value);if(address!=null&&!out.contains(address))out.add(address);}}
  return out;
 }
 /** Every size this post is published in: 1080p, 720p, 540p... Best first, then by height. */
 private static int heightFrom(Object option,Object addr){
  int h=(int)NativeRead.number(NativeRead.get(option,"getHeight"));int w=(int)NativeRead.number(NativeRead.get(option,"getWidth"));
  if(h<=0){h=(int)NativeRead.number(NativeRead.get(addr,"getHeight"));w=(int)NativeRead.number(NativeRead.get(addr,"getWidth"));}
  if(h>0)return w>0&&w<h?w:h;
  String gear=NativeRead.string(NativeRead.get(option,"getGearName"));
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?<![0-9])(2160|1440|1080|960|720|576|540|480|360|240)(?![0-9])").matcher(gear==null?"":gear);
  return m.find()?Integer.parseInt(m.group(1)):0;
 }
 static java.util.List<Choice> choices(Object video,Object bestAddress){
  java.util.ArrayList<Choice> result=new java.util.ArrayList<>();
  if(bestAddress==null)bestAddress=first(NativeRead.get(video,"getPlayAddr"));
  if(bestAddress!=null){Choice top=new Choice(Local.of("Лучшее"),(String)bestAddress,Long.MAX_VALUE);top.best=true;result.add(top);}
  java.util.ArrayList<Choice> found=new java.util.ArrayList<>();java.util.HashSet<String> seen=new java.util.HashSet<>();
  java.util.ArrayList<Object> sources=new java.util.ArrayList<>();
  for(String name:new String[]{"getRawBitRate","getBitRate","getBitrateInfo"}){Object list=NativeRead.get(video,name);if(list instanceof List)sources.add(list);}
  for(String name:new String[]{"rawBitRate","bitRate"}){Object list=NativeRead.field(video,name);if(list instanceof List&&!sources.contains(list))sources.add(list);}
  int count=0;
  for(Object source:sources)for(Object option:(List)source){
   if(count++>=512)break;
   if(Boolean.TRUE.equals(NativeRead.get(option,"isDash")))continue;
   boolean hevc=NativeRead.number(NativeRead.get(option,"isBytevc1"))!=0;
   Object addr=NativeRead.get(option,"getPlayAddr");String url=first(addr);if(url==null)continue;
   long rate=NativeRead.number(NativeRead.get(option,"getBitRate"));
   int real=heightFrom(option,addr),shown=real;
   if(hevc&&real<=0)continue;
   if(shown<=0&&rate>0)shown=Math.max(144,Math.min(4320,(int)Math.round(Math.sqrt(rate/1800.0))));
   if(shown<=0&&rate<=0)continue;
   String key=(real>0?"h"+real:"r"+rate)+(hevc?"x":"a");
   String label=shown>0?shown+"p":Math.round(rate/1000.0)+Local.of(" кбит/с");
   Choice quality=new Choice(hevc?label+" · HEVC":label,url,rate);quality.height=shown;quality.hevc=hevc;quality.alts=all(addr);
   Choice earlier=null;for(Choice f:found)if(key.equals(f.key)){earlier=f;break;}
   quality.key=key;
   if(earlier!=null){if(rate>earlier.bitrate){found.remove(earlier);found.add(quality);}continue;}
   if(seen.add(url))found.add(quality);
  }
  // тот же рост в h264 и HEVC: остаётся h264, он открывается везде
  java.util.ArrayList<Choice> clean=new java.util.ArrayList<>();
  for(Choice f:found){boolean shadowed=false;if(f.hevc)for(Choice g:found)if(!g.hevc&&g.height==f.height&&f.height>0){shadowed=true;break;}if(!shadowed)clean.add(f);}
  java.util.Collections.sort(clean,(a,b)->a.height!=b.height?Integer.compare(b.height,a.height):Long.compare(b.bitrate,a.bitrate));
  if(!result.isEmpty()&&!clean.isEmpty()){int tallest=0;for(Choice f:clean)tallest=Math.max(tallest,f.height);result.get(0).height=tallest;}
  for(Choice choice:clean){boolean duplicate=false;for(Choice existing:result)if(existing.url.equals(choice.url)){duplicate=true;break;}if(!duplicate)result.add(choice);}
  return result;
 }
 /** Resolve the post's soundtrack first, then use the video's native audio rendition.
  * TikTok photo posts and Live Photos can have a music model without a Video at all.
  */
 static String audio(Object post,Object video){
  String linked=musicAudio(NativeRead.get(post,"getMusic"));
  if(linked!=null)return linked;
  linked=musicAudio(NativeRead.get(post,"getAddedSoundMusicInfo"));
  if(linked!=null)return linked;
  return audio(video);
 }
 private static String musicAudio(Object music){
  if(music==null)return null;
  String address=musicAddress(NativeRead.get(music,"getPlayUrl"));
  if(address!=null)return address;
  return musicAddress(NativeRead.get(music,"getReuseAudioPlayUrl"));
 }
 static String audio(Object video){
  if(video==null)return null;
  Object list=NativeRead.field(video,"bitRateAudio");if(!(list instanceof List))return null;
  long score=-1;String chosen=null;int count=0;
  for(Object option:(List)list){
   if(count++>=128)break;
   Object meta=NativeRead.get(option,"getAudioMeta");Object url=NativeRead.get(meta,"getUrlList");
   String address=NativeRead.string(NativeRead.get(url,"getMainUrl"));
   if(!address.startsWith("https://"))address=NativeRead.string(NativeRead.get(url,"getBackupUrl"));
   if(!address.startsWith("https://"))continue;
   long rank=NativeRead.number(NativeRead.get(meta,"getBitrate"));
   if(chosen==null || rank>score){chosen=address;score=rank;}
  }
  return chosen;
 }
 static java.util.List<Choice> photos(Object post,boolean noWatermark){
  java.util.ArrayList<Choice> result=new java.util.ArrayList<>();
  Object info=NativeRead.get(post,"getPhotoModeImageInfo");
  Object images=NativeRead.get(info,"getImageList");
  if(!(images instanceof List))images=NativeRead.field(info,"imageList");
  if(!(images instanceof List))return result;
  int index=0;
  for(Object image:(List)images){
   if(index>=256)break;
   // the preferred copy first; the others are only tried when it will not download
   Object[] models={noWatermark?NativeRead.field(image,"displayImageNoWatermark"):null,NativeRead.field(image,"ownerWatermarkImage"),NativeRead.field(image,"userWatermarkImage")};
   java.util.ArrayList<String> urls=new java.util.ArrayList<>();
   for(Object model:models)if(model!=null)for(String address:all(model))if(!urls.contains(address))urls.add(address);
   if(urls.isEmpty()){index++;continue;}
   Choice photo=new Choice(Local.of("Фото ")+(index+1),urls.get(0),index);photo.alts=urls;result.add(photo);
   index++;
  }
  return result;
 }
 static String[] imageFormat(java.io.File file)throws java.io.IOException{
  byte[] b=new byte[16];int n;try(java.io.FileInputStream in=new java.io.FileInputStream(file)){n=in.read(b);}
  if(n>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return new String[]{"jpg","image/jpeg"};
  if(n>=8&&(b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')return new String[]{"png","image/png"};
  if(n>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return new String[]{"webp","image/webp"};
  if(n>=6&&b[0]=='G'&&b[1]=='I'&&b[2]=='F'&&b[3]=='8')return new String[]{"gif","image/gif"};
  if(n>=12&&b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p'){
   String brand=new String(b,8,4,"US-ASCII").toLowerCase(java.util.Locale.ROOT);
   if(brand.startsWith("hei"))return new String[]{"heic","image/heic"};
   if(brand.startsWith("avi"))return new String[]{"avif","image/avif"};
  }
  throw new java.io.IOException("Unsupported or invalid image payload");
 }
 static String[] videoFormat(java.io.File file)throws java.io.IOException{byte[] h=new byte[12];int n;try(java.io.FileInputStream in=new java.io.FileInputStream(file)){n=in.read(h);}if(n>=8&&h[4]=='f'&&h[5]=='t'&&h[6]=='y'&&h[7]=='p')return new String[]{"mp4","video/mp4"};if(n>=4&&(h[0]&255)==0x1a&&(h[1]&255)==0x45&&(h[2]&255)==0xdf&&(h[3]&255)==0xa3)return new String[]{"webm","video/webm"};throw new java.io.IOException("Unsupported or invalid video payload");}
 static String[] audioFormat(java.io.File file)throws java.io.IOException{
  byte[] bytes=new byte[16];int count;try(java.io.InputStream in=new java.io.FileInputStream(file)){count=in.read(bytes);}
  if(count<4)throw new java.io.IOException("Empty audio");
  if(count>=8 && bytes[4]=='f' && bytes[5]=='t' && bytes[6]=='y' && bytes[7]=='p')return new String[]{"m4a","audio/mp4"};
  if(bytes[0]=='O' && bytes[1]=='g' && bytes[2]=='g' && bytes[3]=='S')return new String[]{"ogg","audio/ogg"};
  if(bytes[0]=='I' && bytes[1]=='D' && bytes[2]=='3' || (bytes[0]&255)==255 && (bytes[1]&0xE0)==0xE0 && (bytes[1]&0x06)!=0)return new String[]{"mp3","audio/mpeg"};
  if((bytes[0]&255)==255 && (bytes[1]&0xF6)==0xF0)return new String[]{"aac","audio/aac"};
  if((bytes[0]&255)==0x1A && (bytes[1]&255)==0x45 && (bytes[2]&255)==0xDF && (bytes[3]&255)==0xA3)return new String[]{"webm","audio/webm"};
  if(count>=12 && bytes[0]=='R' && bytes[1]=='I' && bytes[2]=='F' && bytes[3]=='F' && bytes[8]=='W' && bytes[9]=='A' && bytes[10]=='V' && bytes[11]=='E')return new String[]{"wav","audio/wav"};
  throw new java.io.IOException("Unsupported audio container");
 }
}

