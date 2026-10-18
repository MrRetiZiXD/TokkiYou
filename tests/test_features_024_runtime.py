"""Run native heart tinting and feed filtering on JVM doubles."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS={
'com/bytedance/tux/icon/TuxIconView.java':'package com.bytedance.tux.icon;public class TuxIconView extends android.widget.ImageView {public int icon,tint;public void setIconRes(int i){icon=i;}public void setTintColor(int c){tint=c;}}',
'android/view/View.java':'package android.view;public class View {public Object tag;public void setTag(int k,Object v){tag=v;}public boolean isAttachedToWindow(){return true;}public void post(Runnable r){r.run();}}',
'cat/narezany/margyt/Account.java': 'package cat.narezany.margyt;class Account {static String live;static String activeId(){return live;}static String id(){return live;}}',
'android/content/Context.java': 'package android.content; public class Context {public static int MODE_PRIVATE=0; public SharedPreferences getSharedPreferences(String n,int m){return null;}}',
'android/content/SharedPreferences.java': 'package android.content; public interface SharedPreferences {boolean getBoolean(String k,boolean d);Editor edit();interface Editor {Editor putBoolean(String k,boolean v);void apply();}}',
'android/graphics/PorterDuff.java': 'package android.graphics; public class PorterDuff {public enum Mode {SRC_IN}}',
'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable; public class Drawable {public int colour; public Drawable mutate(){return this;}public void setColorFilter(int c,android.graphics.PorterDuff.Mode m){colour=c;}}',
'android/widget/ImageView.java': 'package android.widget; public class ImageView extends android.view.View {public int filter,writes;public float alpha=1;public android.graphics.drawable.Drawable image;public void setColorFilter(int c,android.graphics.PorterDuff.Mode m){filter=c;writes++;}public void setImageDrawable(android.graphics.drawable.Drawable d){image=d;}}',
'cat/narezany/margyt/LikeColors.java': 'package cat.narezany.margyt; class LikeColors {static int value=0xffa8c7fa;static int colour(){return value;}}',
'cat/narezany/margyt/Margy.java': 'package cat.narezany.margyt; class Margy {static String PREFS="mod";static android.content.Context context(){return null;}}',
'cat/narezany/margyt/Plugins.java': 'package cat.narezany.margyt; class Plugins {static java.util.List feed(java.util.List list){return list;}}',
'cat/narezany/margyt/Diary.java': 'package cat.narezany.margyt; class Diary {static void note(String s){}}',
'com/ss/android/ugc/aweme/feed/model/Aweme.java': 'package com.ss.android.ugc.aweme.feed.model; public class Aweme {public int reads,card;public Object reason,upvotes,preload;public int getRecommendCardType(){return card;}public Object getUpvoteReason(){return reason;}public Object getUpvoteInfo(){return upvotes;}public Object getUpvotePreload(){return preload;}public boolean ad;public Aweme(boolean ad){this.ad=ad;} public boolean isAd(){reads++;return ad;}public int getAwemeType(){return 0;}public long getLiveId(){return 0;}public String getAuthorUid(){return "author";}public Object getRoomFeedCellStruct(){return null;}public Object getPhotoModeImageInfo(){return null;}}',
'com/ss/android/ugc/aweme/feed/model/FeedItemList.java': 'package com.ss.android.ugc.aweme.feed.model; public class FeedItemList {public java.util.List items;public java.util.List getItems(){return items;}}',
}
STUBS['com/ss/android/ugc/aweme/search/pages/result/topsearch/core/model/SearchMixFeedList.java']=(ROOT/'inject/stubs/com/ss/android/ugc/aweme/search/pages/result/topsearch/core/model/SearchMixFeedList.java').read_text()
HARNESS=r"""package cat.narezany.margyt;
import android.graphics.drawable.Drawable;import android.widget.ImageView;
import com.ss.android.ugc.aweme.feed.model.*;import java.util.*;
class FeatureHarness {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  ImageView heart=new ImageView();Drawable drawable=new Drawable();Heart.setImageDrawable(heart,drawable);
  com.bytedance.tux.icon.TuxIconView tux=new com.bytedance.tux.icon.TuxIconView();
  Heart.setIconRes(tux,42);check(tux.icon==42 && tux.tint==LikeColors.value,"native Tux icon tinted");
  Heart.setTintColor(tux,0xffff0000);check(tux.tint==LikeColors.value && Boolean.TRUE.equals(tux.tag),"native random red overridden; effect protected");
  Heart.setIconRes(null,0);Heart.setTintColor(null,0);
  check(heart.image==drawable && heart.filter==LikeColors.value,"native drawable follows chosen colour");
  Heart.setImageDrawable(heart,drawable);check(heart.writes==1,"repeated drawable assignment does not reapply filter");
  check(Boolean.TRUE.equals(heart.tag) && heart.alpha==1,"effect tagged and alpha unchanged");
  LikeColors.value=0xffb5d69c;Heart.setImageDrawable(heart,new Drawable());
  Heart.refresh();check(tux.tint==LikeColors.value && tux.filter==LikeColors.value,"existing heart tracks new account/accent immediately");
  check(heart.filter==LikeColors.value,"new tap uses new colour");Heart.setImageDrawable(heart,null);check(heart.image==null,"drawable clears safely");
  FeedItemList page=new FeedItemList();Aweme a=new Aweme(false),ad=new Aweme(true),b=new Aweme(false);
  List original=new ArrayList(Arrays.asList(a,ad,b));page.items=original;
  List filtered=Feed.getItems(page);check(filtered.size()==2 && filtered.get(0)==a && filtered.get(1)==b,"ads removed in order");
  check(a.reads==1 && ad.reads==1 && b.reads==1,"one classification per post");check(original.size()==3,"host list not mutated");Feed.setEnabled(false);check(Feed.getItems(page)==original,"disabled preserves host list");
  Feed.setEnabled(true);page.items=new ArrayList(Arrays.asList(a,b));check(Feed.getItems(page)==page.items,"clean feed keeps identity");
  page.items=new ArrayList(Arrays.asList(ad));check(Feed.getItems(page).isEmpty(),"all-ad page becomes empty");
  Aweme card=new Aweme(false);card.card=3;check(Feed.suggestion(card),"ordinary suggestion removed");
  card.reason=new Object();check(!Feed.suggestion(card),"friend repost reason preserved");card.reason=null;
  card.upvotes=new Object();check(!Feed.suggestion(card),"friend upvote metadata preserved");card.upvotes=null;
  card.preload=new Object();check(!Feed.suggestion(card),"preloaded friend repost preserved");
  check(Feed.getItems(null)==null,"null safe");System.out.println("heart and ad runtime checks passed");
 }
}"""
STUBS['cat/narezany/margyt/Tags.java']='package cat.narezany.margyt;class Tags{static java.util.List<String> all(){return java.util.Collections.emptyList();}static boolean blocks(com.ss.android.ugc.aweme.feed.model.Aweme post){return false;}}'
STUBS['cat/narezany/margyt/NativeRead.java']='package cat.narezany.margyt;class NativeRead{static Object get(Object o,String s){try{return o.getClass().getMethod(s).invoke(o);}catch(Exception e){return null;}}static long number(Object o){return o instanceof Number?((Number)o).longValue():0;}}'
STUBS['cat/narezany/margyt/FeedBlacklist.java']='package cat.narezany.margyt;class FeedBlacklist{static java.util.Map items(){return java.util.Collections.emptyMap();}static java.util.Map cards(){return java.util.Collections.emptyMap();}static boolean hides(Object o){return false;}}'
class FeatureRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_heart_and_ads(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);sources=dict(STUBS)
   for name in ('Heart','Feed','NativeRead'):
    sources['cat/narezany/margyt/'+name+'.java']=(ROOT/('inject/java/cat/narezany/margyt/'+name+'.java')).read_text(encoding='utf-8')
   sources['cat/narezany/margyt/FeatureHarness.java']=HARNESS
   __import__("android_doubles").with_mod(sources)
   for name,content in sources.items():
    target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.narezany.margyt.FeatureHarness'],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)
