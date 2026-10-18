"""Execute the real filter against explicit ads, incomplete payloads and search cards."""
import pathlib, shutil, subprocess, tempfile, unittest
from test_settings_safety_runtime import method
ROOT=pathlib.Path(__file__).resolve().parents[1]

class AdFilterTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_filter_preserves_organic_posts_and_native_list(self):
  body='\n'.join(method('Feed.java',m) for m in ['    static boolean advert','    public static List withoutAds','    public static List searchItems'])
  sources={
   'cat/narezany/margyt/NativeRead.java':(ROOT/'inject/java/cat/narezany/margyt/NativeRead.java').read_text(encoding='utf-8'),
   'com/ss/android/ugc/aweme/feed/model/Aweme.java':'package com.ss.android.ugc.aweme.feed.model;public class Aweme{public boolean _isAd;public Object raw;public boolean isAd(){return _isAd&&raw!=null;}}',
   'com/ss/android/ugc/aweme/search/pages/result/topsearch/core/model/SearchMixFeedList.java':(ROOT/'inject/stubs/com/ss/android/ugc/aweme/search/pages/result/topsearch/core/model/SearchMixFeedList.java').read_text(),
   'cat/narezany/margyt/Diary.java':'package cat.narezany.margyt;class Diary{static java.util.List<String> said=new java.util.ArrayList<>();static void note(String line){said.add(line);}}',
   'cat/narezany/margyt/Test.java':'''package cat.narezany.margyt;
import java.util.*;import com.ss.android.ugc.aweme.feed.model.Aweme;
public class Test {static boolean enabled=true;static boolean isEnabled(){return enabled;}
'''+body+'''
public static class SearchCard{public Aweme post;public Object precise,ai;public Aweme getAweme(){return post;}public Object getPreciseAd(){return precise;}public Object getAiAdCard(){return ai;}}
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
public static void main(String[] a){
 Aweme organic=new Aweme();organic.raw=new Object();Aweme ad=new Aweme();ad._isAd=true;ad.raw=new Object();Aweme incomplete=new Aweme();incomplete._isAd=true;
 SearchCard nativeCard=new SearchCard();nativeCard.post=organic;SearchCard precise=new SearchCard();precise.precise=new Object();SearchCard ai=new SearchCard();ai.ai=new Object();SearchCard mixedAd=new SearchCard();mixedAd.post=ad;
 List page=Arrays.asList(organic,ad,incomplete,nativeCard,precise,ai,mixedAd,"other");List kept=withoutAds(page);
 check(kept.size()==3&&kept.get(0)==organic&&kept.get(1)==nativeCard&&kept.get(2).equals("other"),"keep organic and other card types, remove actual ads");check(page.size()==8,"input remains intact");
 check(withoutAds(kept)==kept,"unchanged list keeps identity");check(withoutAds(null)==null,"null list");enabled=false;check(withoutAds(page)==page,"disabled filter");enabled=true;
 com.ss.android.ugc.aweme.search.pages.result.topsearch.core.model.SearchMixFeedList search=new com.ss.android.ugc.aweme.search.pages.result.topsearch.core.model.SearchMixFeedList();search.mItems=page;check(searchItems(search).size()==3&&search.mItems==page,"search field read is filtered without model mutation");
 check(Diary.said.size()==1&&Diary.said.get(0).equals("search: a page of 8 from TikTok, 3 after the mod"),"what the mod left out of a search is written down: "+Diary.said);
 Diary.said.clear();search.mItems=kept;searchItems(search);check(Diary.said.isEmpty(),"a page left as it was is not written about");
 search.mItems=new ArrayList();searchItems(search);search.mItems=null;searchItems(search);
 check(Diary.said.size()==2&&Diary.said.get(0).equals("search: a page of 0 from TikTok")&&Diary.said.get(1).equals("search: a page of nothing at all"),"and so is a page TikTok sent empty: "+Diary.said);
}}
'''}
  with tempfile.TemporaryDirectory() as folder:
   files=[]
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():
    p=pathlib.Path(folder)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8');files.append(str(p))
   result=subprocess.run(['javac','-encoding','UTF-8','-d',folder,*files],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',folder,'cat.narezany.margyt.Test'],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
