"""Run production URL selection, file detection, pin ordering and timestamp/speed bounds."""
import pathlib,shutil,subprocess,tempfile,unittest
import test_account_appearance_runtime as base
from tokkiyou import dexpatch
ROOT=pathlib.Path(__file__).resolve().parents[1]
def method(file,signature):
 text=(ROOT/'inject/java/cat/narezany/tokkiyou'/file).read_text(encoding='utf-8');a=text.index(signature);b=text.index('{',a);depth=1;i=b+1
 while depth:
  if text[i]=='{':depth+=1
  if text[i]=='}':depth-=1
  i+=1
 return text[a:i]
class ArchiveFeaturesTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_real_models_and_formats(self):
  sources={k:base.STUBS[k] for k in ['android/content/Context.java','android/content/SharedPreferences.java']}
  sources.update({'cat/narezany/tokkiyou/TokkiYou.java':'package cat.narezany.tokkiyou;class TokkiYou{static android.content.Context c=new android.content.Context();static android.content.Context context(){return c;}}',
 'cat/narezany/tokkiyou/AccountAppearance.java':'package cat.narezany.tokkiyou;class AccountAppearance{static String account="111";static android.content.SharedPreferences prefs(android.content.Context c){return c.getSharedPreferences(account,0);}}'})
  for name in ['NativeRead','DownloadQuality']:
   sources['cat/narezany/tokkiyou/'+name+'.java']=(ROOT/'inject/java/cat/narezany/tokkiyou'/(name+'.java')).read_text(encoding='utf-8')
  helpers='package cat.narezany.tokkiyou;import java.util.*;class Algorithms{static final int LIMIT=20;'+method('PinnedFriends.java','static List<String> parse(')+method('PinnedFriends.java','static List order(')+method('PlayerTools.java','static float percent(')+method('PlayerTools.java','static boolean validSpeed(')+method('MessageTimes.java','static long millis(')+'}'
  sources['cat/narezany/tokkiyou/Algorithms.java']=helpers
  sources['cat/narezany/tokkiyou/Test.java']=HARNESS
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
   result=subprocess.run(['javac','-encoding','UTF-8','-source','8','-target','8','-d',str(work)]+[str(p) for p in work.rglob('*.java')],capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.Test',str(work)],capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr)
 def test_verified_hooks_are_scoped_and_idempotent(self):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory)
   cases=[(dexpatch.PROFILE_CONTAINER,'public onViewCreated(Landroid/view/View;)V','native profile layout'),(dexpatch.REPOST_CLASS,'public final s4(Ljava/lang/Object;)V','native repost'),(dexpatch.AVATAR_CLASS,'public final s4(Ljava/lang/Object;)V','native repost avatar'),(dexpatch.PLAYER_CONTROLLER,'public final onResumePlay(Ljava/lang/String;)V','native player resume'),(dexpatch.MESSAGE_CLASSES[0],'public Bq(LX/0Qfl;)V','native message timestamps'),(dexpatch.MESSAGE_CLASSES[1],'public final s4(Ljava/lang/Object;)V','native message timestamps'),(dexpatch.PUSH_GUIDE,'public final LIZ()Z','native notification guide')]
   for i,(owner,signature,label) in enumerate(cases):
    body='    const/4 v0, 0x0\n    return v0' if 'LIZ()Z' in signature else '    return-void'
    (root/(str(i)+'.smali')).write_text('.class public '+owner+'\n.super Ljava/lang/Object;\n.method '+signature+'\n    .registers 8\n'+body+'\n.end method',encoding='utf-8')
   counts=dexpatch.rewrite_anchored(str(root))
   for owner,signature,label in cases:self.assertGreater(counts.get(label,0),0,label)
   self.assertEqual({},dexpatch.rewrite_anchored(str(root)))
   unrelated=root/'chat.smali';original='.class public Lexample/Chat;\n.method public onViewCreated(Landroid/view/View;)V\n.registers 2\nreturn-void\n.end method';unrelated.write_text(original)
   dexpatch.rewrite_anchored(str(root));self.assertEqual(original,unrelated.read_text())
HARNESS=r'''package cat.narezany.tokkiyou;import java.util.*;import java.io.*;
public class Test{
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
public static class Url{List<String> list;Url(String...s){list=Arrays.asList(s);}public List getUrlList(){return list;}}
public static class Music{Url play,reuse;Music(Url p,Url r){play=p;reuse=r;}public Url getPlayUrl(){return play;}public Url getReuseAudioPlayUrl(){return reuse;}}
public static class Photo{public Url displayImageNoWatermark,ownerWatermarkImage,userWatermarkImage;Photo(Url a,Url b,Url c){displayImageNoWatermark=a;ownerWatermarkImage=b;userWatermarkImage=c;}}
public static class PhotoInfo{List images;PhotoInfo(Photo...p){images=Arrays.asList(p);}public List getImageList(){return images;}}
public static class Post{Music music,added;PhotoInfo photo;Post(Music m,Music a,PhotoInfo p){music=m;added=a;photo=p;}public Music getMusic(){return music;}public Music getAddedSoundMusicInfo(){return added;}public PhotoInfo getPhotoModeImageInfo(){return photo;}}
public static class Rate{int rate;boolean dash;int codec;Url url;Rate(int r,String u,boolean d,int c){rate=r;url=new Url(u);dash=d;codec=c;}public int getBitRate(){return rate;}public int getWidth(){return 0;}public int getHeight(){return 0;}public boolean isDash(){return dash;}public int isBytevc1(){return codec;}public Url getPlayAddr(){return url;}}
public static class Video{public List bitRateAudio=new ArrayList();List rates=new ArrayList();public List getRawBitRate(){return rates;}}
public static class Audio{Meta meta;Audio(int r,String a,String b){meta=new Meta(r,a,b);}public Meta getAudioMeta(){return meta;}}
public static class Meta{int rate;AudioUrl url;Meta(int r,String a,String b){rate=r;url=new AudioUrl(a,b);}public int getBitrate(){return rate;}public AudioUrl getUrlList(){return url;}}
public static class AudioUrl{String main,backup;AudioUrl(String a,String b){main=a;backup=b;}public String getMainUrl(){return main;}public String getBackupUrl(){return backup;}}
public static class User{String uid;User(String u){uid=u;}public String getUid(){return uid;}}
static File payload(String directory,int...bytes)throws Exception{File f=new File(directory,"payload");try(FileOutputStream out=new FileOutputStream(f)){for(int b:bytes)out.write(b);}return f;}
public static void main(String[] args)throws Exception{
 Video video=new Video();Url fallback=new Url("https://original");Rate low=new Rate(300,"https://low",false,0),high=new Rate(2000,"https://high",false,0);video.rates.add(low);video.rates.add(high);video.rates.add(new Rate(9999,"https://dash",true,0));video.rates.add(new Rate(8888,"https://unsupported",false,1));video.rates.add(new Rate(7777,"http://insecure",false,0));
 check(DownloadQuality.video(video,fallback)==fallback,"native quality identity");DownloadQuality.setMode(1);check(DownloadQuality.video(video,fallback)==high.url,"max usable progressive URL");DownloadQuality.setMode(2);check(DownloadQuality.video(video,fallback)==low.url,"lowest real bitrate");java.util.List<DownloadQuality.Choice> choices=DownloadQuality.choices(video,"https://best");check(choices.size()==3&&"Лучшее".equals(choices.get(0).label)&&choices.get(0).best,"quality chooser starts with the best published source");check(choices.get(1).bitrate>=choices.get(2).bitrate,"quality options sorted from highest to lowest");video.rates.clear();check(DownloadQuality.video(video,fallback)==fallback,"absent rendition fallback");
 AccountAppearance.account="222";check(DownloadQuality.mode()==0,"new account native quality");AccountAppearance.account="111";check(DownloadQuality.mode()==2,"owner quality restored");
 check(DownloadQuality.audio(video)==null,"do not invent audio URL");video.bitRateAudio.add(new Audio(128,"https://audio",""));video.bitRateAudio.add(new Audio(256,"http://bad","https://backup"));check("https://backup".equals(DownloadQuality.audio(video)),"published audio with secure fallback");
 Post photoPost=new Post(new Music(new Url("http://music-cdn/audio.m4a"),null),null,new PhotoInfo(new Photo(new Url("https://cdn/image-1.jpg"),null,null),new Photo(null,null,new Url("https://cdn/image-2.webp"))));check("https://music-cdn/audio.m4a".equals(DownloadQuality.audio(photoPost,null)),"photo/slideshow music works without a video model and upgrades HTTP URL securely");
 Post addedOnly=new Post(null,new Music(new Url(),new Url("https://cdn/added.m4a")),null);check("https://cdn/added.m4a".equals(DownloadQuality.audio(addedOnly,null)),"added soundtrack reuse URL fallback");
 java.util.List<DownloadQuality.Choice> photos=DownloadQuality.photos(photoPost,true);check(photos.size()==2&&"Фото 1".equals(photos.get(0).label)&&"https://cdn/image-1.jpg".equals(photos.get(0).url)&&"https://cdn/image-2.webp".equals(photos.get(1).url),"carousel choices use each image and fall back to the available watermark variant");
 check("m4a".equals(DownloadQuality.audioFormat(payload(args[0],0,0,0,24,102,116,121,112))[0]),"M4A never mislabeled MP3");check("mp4".equals(DownloadQuality.videoFormat(payload(args[0],0,0,0,24,102,116,121,112))[0]),"MP4 detection");check("aac".equals(DownloadQuality.audioFormat(payload(args[0],255,241,0,0))[0]),"ADTS");check("mp3".equals(DownloadQuality.audioFormat(payload(args[0],73,68,51,0))[0]),"actual MP3");check("ogg".equals(DownloadQuality.audioFormat(payload(args[0],79,103,103,83))[0]),"Ogg");boolean failed=false;try{DownloadQuality.videoFormat(payload(args[0],60,104,116,109,108,62));}catch(IOException e){failed=true;}check(failed,"HTML response rejected before gallery publish");
 List<String> pins=Algorithms.parse("3\n1\n3\ninvalid\n");check(pins.equals(Arrays.asList("3","1")),"UID pins validated and deduplicated");check(Algorithms.parse(new String(new char[5000])).isEmpty(),"oversize import rejected");
 User one=new User("1"),two=new User("2"),three=new User("3");List<User> nativeList=Arrays.asList(one,two,three);List ordered=Algorithms.order(nativeList,pins);check(ordered.get(0)==three&&ordered.get(1)==one&&ordered.get(2)==two,"stable pinned-first order without lost friends");check(Algorithms.order(nativeList,Collections.emptyList())==nativeList,"no pins preserves list identity");check(Algorithms.order(nativeList,Arrays.asList("999"))==nativeList,"missing pins do not fabricate members");
 check(Algorithms.validSpeed(.5f)&&Algorithms.validSpeed(3f)&&!Algorithms.validSpeed(Float.NaN)&&!Algorithms.validSpeed(Float.POSITIVE_INFINITY)&&!Algorithms.validSpeed(.49f),"speed boundaries");check(Algorithms.percent(15000,60000)==25f&&Algorithms.percent(-1,100)==0&&Algorithms.percent(999,100)==100&&Algorithms.percent(12,0)==0,"native seek percentage clamped");
 long now=System.currentTimeMillis()/1000;check(Algorithms.millis(now)==now*1000&&Algorithms.millis(now*1000)==now*1000&&Algorithms.millis(now*1000000)==now*1000,"real timestamp seconds/millis/micros");check(Algorithms.millis(0)==0&&Algorithms.millis(-1)==0&&Algorithms.millis(1)==0&&Algorithms.millis(now+172800)==0,"invalid timestamps not invented");
 }
}'''
