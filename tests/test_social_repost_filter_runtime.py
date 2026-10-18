import pathlib,shutil,subprocess,tempfile,unittest
from test_archive_features_runtime import method
ROOT=pathlib.Path(__file__).resolve().parents[1]
class RepostFilterTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_social_card_kept_but_explicit_author_block_honored(self):
  sources={'cat/narezany/tokkiyou/NativeRead.java':(ROOT/'inject/java/cat/narezany/tokkiyou/NativeRead.java').read_text(encoding='utf8'),
   'cat/narezany/tokkiyou/Feed.java':'package cat.narezany.tokkiyou;class Feed{'+method('Feed.java','static boolean socialRepost(')+method('Feed.java','static boolean suggestion(')+'}',
   'cat/narezany/tokkiyou/Filter.java':'package cat.narezany.tokkiyou;import java.util.*;class Filter{static Map<Integer,String> types=new HashMap<>();static Map<String,String> authors=new HashMap<>();static Map<Integer,String> cards(){return types;}static Map<String,String> items(){return authors;}'+method('FeedBlacklist.java','static boolean hides(')+'}',
   'cat/narezany/tokkiyou/Test.java':"""package cat.narezany.tokkiyou;public class Test{public static class User{public String getUid(){return "1";}}public static class Post{boolean social;public int getRecommendCardType(){return 5;}public Object getUpvoteInfo(){return social?new Object():null;}public User getAuthor(){return new User();}}public static void main(String[] args){Filter.types.put(5,"card");Post p=new Post();if(!Filter.hides(p)||!Feed.suggestion(p))throw new AssertionError("normal card retained");p.social=true;if(Filter.hides(p)||Feed.suggestion(p))throw new AssertionError("friend repost removed by card type");Filter.authors.put("1","blocked");if(!Filter.hides(p))throw new AssertionError("explicit author block ignored");}}"""}
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory)
   __import__("android_doubles").with_mod(sources)
   for name,text in sources.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8')
   out=subprocess.run(['javac','-source','8','-target','8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True);self.assertEqual(0,out.returncode,out.stderr)
   out=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.Test'],capture_output=True,text=True);self.assertEqual(0,out.returncode,out.stderr)
