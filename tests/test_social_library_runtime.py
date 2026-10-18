"""Execute real pagination and account guards using native API-shaped doubles."""
import pathlib,subprocess,tempfile,shutil,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class SocialLibraryTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_pages_callbacks_account_and_stale_response(self):
  self.run_with(self.doubles())
 def doubles(self):
  code=(ROOT/'inject/java/cat/narezany/tokkiyou/SocialLibrary.java').read_text(encoding='utf8')
  for name,alias in [('X.1M7B','mock.Api'),('X.01K8','mock.Continuation'),('X.0Xou','mock.Scene'),('X.14EI','mock.AccountApi')]:code=code.replace(name,alias)
  files={'cat/narezany/tokkiyou/SocialLibrary.java':code,
   'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
   'android/os/Handler.java':'package android.os;public class Handler{public Handler(Looper l){}public void post(Runnable r){r.run();}public void postDelayed(Runnable r,long t){}}',
   'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long uptimeMillis(){return 100000;}}',
   'mock/Continuation.java':'package mock;public interface Continuation{Object getContext();void resumeWith(Object value);}',
   'mock/Scene.java':'package mock;public class Scene{public static final Scene TT_RELATION_PAGE=new Scene();public int getValue(){return 7;}}',
   'kotlin/coroutines/EmptyCoroutineContext.java':'package kotlin.coroutines;public class EmptyCoroutineContext{public static final EmptyCoroutineContext INSTANCE=new EmptyCoroutineContext();}',
   'mock/AccountApi.java':'package mock;public class AccountApi{public static AccountApi LJFF(){return new AccountApi();}public String getCurUserId(){return cat.narezany.tokkiyou.Account.uid;}public String getCurSecUserId(){return "sec-current";}}',
   'com/ss/android/ugc/aweme/relation/model/FriendsListResponse.java':"""package com.ss.android.ugc.aweme.relation.model;import java.util.*;public class FriendsListResponse{public boolean hasMore;public String nextPageToken;public List getUserList(){return Arrays.asList(nextPageToken);}public FriendsListResponse(boolean more,String token){hasMore=more;nextPageToken=token;}}""",
   'mock/Api.java':"""package mock;import com.ss.android.ugc.aweme.relation.model.FriendsListResponse;public class Api{public static final Api LIZIZ=new Api();public static int calls;public static boolean stale;public Object LIZIZ(int scene,int count,String sec,String page,Continuation cb){if(scene!=7||count!=30||!sec.equals("sec-current"))throw new AssertionError();calls++;if(stale)cat.narezany.tokkiyou.Account.uid="2";FriendsListResponse out=new FriendsListResponse(page.isEmpty(),page.isEmpty()?"next":"done");if(page.isEmpty())return out;cb.resumeWith(out);return new Object();}}""",
   'cat/narezany/tokkiyou/Account.java':'package cat.narezany.tokkiyou;public class Account{public static String uid="1";static String liveId(){return uid;}}',
   'cat/narezany/tokkiyou/PinnedFriends.java':'package cat.narezany.tokkiyou;import java.util.*;class PinnedFriends{static List all=new ArrayList();static void reload(){all.clear();}static void observe(List x){all.addAll(x);}}',
   'cat/narezany/tokkiyou/NativeStreaks.java':'package cat.narezany.tokkiyou;class NativeStreaks{static void prepare(){}static void friends(java.util.List<?> users,String uid){}}',
   'cat/narezany/tokkiyou/FavouriteStickers.java':'package cat.narezany.tokkiyou;class FavouriteStickers{static void load(Runnable r){}}',
   'cat/narezany/tokkiyou/Diary.java':'package cat.narezany.tokkiyou;class Diary{static void note(String s){}}',
   'cat/narezany/tokkiyou/Net.java':'package cat.narezany.tokkiyou;class Net{static void away(String s,Runnable r){r.run();}}',
   'cat/narezany/tokkiyou/NativeRead.java':(ROOT/'inject/java/cat/narezany/tokkiyou/NativeRead.java').read_text(encoding='utf8'),
   'cat/narezany/tokkiyou/Test.java':"""package cat.narezany.tokkiyou;public class Test{public static void main(String[] a)throws Exception{SocialLibrary.load("1",null);if(mock.Api.calls!=2||PinnedFriends.all.size()!=2)throw new AssertionError("both immediate and suspended pages");PinnedFriends.all.clear();mock.Api.stale=true;SocialLibrary.load("1",null);if(!PinnedFriends.all.isEmpty())throw new AssertionError("stale account accepted");System.out.println("pagination and account guards passed");}}"""}
  return files
 def run_with(self,files):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(files)
   for name,text in files.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8')
   out=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in files],capture_output=True,text=True);self.assertEqual(0,out.returncode,out.stderr)
   out=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.Test'],capture_output=True,text=True,timeout=30);self.assertEqual(0,out.returncode,out.stderr)
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_a_tiktok_that_renamed_the_empty_context_is_given_one(self):
  files=self.doubles()
  del files['kotlin/coroutines/EmptyCoroutineContext.java']
  files['kotlin/coroutines/CoroutineContext.java']='package kotlin.coroutines;public interface CoroutineContext{Object fold(Object initial,Object operation);Object get(Object key);CoroutineContext minusKey(Object key);CoroutineContext plus(CoroutineContext other);}'
  files['cat/narezany/tokkiyou/Test.java']="""package cat.narezany.tokkiyou;import kotlin.coroutines.CoroutineContext;public class Test{public static void main(String[] a)throws Exception{
CoroutineContext empty=(CoroutineContext)SocialLibrary.emptyContext();Object seed=new Object();
if(empty.fold(seed,null)!=seed||empty.get("key")!=null||empty.minusKey("key")!=empty)throw new AssertionError("an empty context holds nothing");
CoroutineContext other=(CoroutineContext)SocialLibrary.emptyContext();if(empty.plus(other)!=other||!empty.equals(empty)||empty.equals(other))throw new AssertionError("adding to nothing gives what was added");
if(!"EmptyCoroutineContext".equals(empty.toString()))throw new AssertionError("it says what it is");
SocialLibrary.load("1",null);if(mock.Api.calls!=2)throw new AssertionError("the friends are still read");}}"""
  self.run_with(files)
