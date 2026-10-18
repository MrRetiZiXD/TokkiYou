"""Run archive/ranking and the real fetch loop against a native API fixture."""
import pathlib, shutil, subprocess, tempfile, unittest, json, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / 'inject/java/cat/narezany/tokkiyou'

class OfflineCommentsRuntime(unittest.TestCase):
    def test_archive_and_native_pagination(self):
        javac, java = shutil.which('javac'), shutil.which('java')
        if not javac or not java:
            self.skipTest('JDK unavailable')
        with tempfile.TemporaryDirectory() as directory:
            base = pathlib.Path(directory)
            package = base / 'cat/narezany/tokkiyou'
            package.mkdir(parents=True)
            shutil.copy2(JAVA / 'OfflineCommentArchive.java', package)
            shutil.copy2(JAVA / 'NativeRead.java', package)
            (package / 'NativeJson.java').write_text('package cat.narezany.tokkiyou;final class NativeJson {static String encode(Object value){return "";}}', encoding='utf-8')
            source = (JAVA / 'OfflineComments.java').read_text(encoding='utf-8')
            fetch = source[source.index(' private static List<OfflineCommentArchive.Row> fetch('):source.index(' public static void settings(')]
            fetch = fetch.replace('"com.ss.android.ugc.aweme.comment.commentlist.api.CommentApi$RealApi"', '"fake.Api$RealApi"').replace('"com.ss.android.ugc.aweme.comment.commentlist.api.CommentApi"', '"fake.Api"').replace('"X.01TL"', '"fake.Factory"').replace('"X.0Vgo"', '"fake.Task"')
            harness = '''package cat.narezany.tokkiyou;
import java.io.*;import java.util.*;import java.lang.reflect.*;import java.util.concurrent.*;import fake.SystemClock;
public class Harness {
 static class Prefs {boolean getBoolean(String key,boolean fallback){return true;}}
 static Prefs prefs(){return new Prefs();}static final String ENABLED="enabled";
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  check(OfflineCommentArchive.count(" 10 ")==10);check(OfflineCommentArchive.count("0")==-1);check(OfflineCommentArchive.count("1001")==-1);check(OfflineCommentArchive.count("abc")==-1);
  List<OfflineCommentArchive.Row> rows=fetch("123","456",0,60);
  check(rows.size()==60);check(rows.get(0).likes==9999);check(fake.Api.calls==2);check(fake.Api.lastCursor==50);
  OfflineCommentArchive.write(new File(args[0]),"123","Видео 🐱","video.mp4",60,rows);
  check(OfflineCommentArchive.read(new File(args[0])).contains("Видео 🐱"));
  List<OfflineCommentArchive.Row> duplicate=Arrays.asList(new OfflineCommentArchive.Row("x","A","a","quote \\\"\\n",5,1),new OfflineCommentArchive.Row("x","A","a","duplicate",100,1),new OfflineCommentArchive.Row("y","B","b","Привет",20,2));
  check(OfflineCommentArchive.popular(duplicate,10).size()==2);check(OfflineCommentArchive.popular(duplicate,10).get(0).id.equals("y"));
  fake.Api.fail=true;boolean failed=false;try{fetch("123","456",0,5);}catch(IOException expected){failed=true;}check(failed);
  System.out.println("OK");
 }
''' + fetch + '\n}'
            (package / 'Harness.java').write_text(harness, encoding='utf-8')
            fake = base / 'fake'
            fake.mkdir()
            types = ['String','long','int','String','int','Long','int','String','int','int','int','int','Object','java.util.List','int','String','String','String','boolean','String','long','int','String','int','int','int','Integer','Integer','Float','String','Long','String','Integer','Long','String','String']
            params = ','.join(f'{kind} p{i}' for i, kind in enumerate(types))
            (fake / 'Factory.java').write_text('package fake;public interface Factory {Object create(Class<?> type);}', encoding='utf-8')
            (fake / 'SystemClock.java').write_text('package fake;public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;}}', encoding='utf-8')
            (fake / 'Task.java').write_text('package fake;public class Task {final Object value;final Exception error;public Task(Object v,Exception e){value=v;error=e;}public boolean LJIILIIL(){return true;}public boolean LJIJJLI(long n,java.util.concurrent.TimeUnit unit){return true;}public Exception LJIIJ(){return error;}public Object LJIIJJI(){return value;}}', encoding='utf-8')
            api = '''package fake;import java.util.*;public class Api {
 public static int calls;public static long lastCursor;public static boolean fail;
 public static Factory LIZ=type->new RealApi(){public Task fetchCommentListV2(PARAMS){
  if(!p0.equals("123")||!p22.equals("456")||p2<1||p2>50||!p15.equals("offline_mode"))throw new AssertionError();
  calls++;lastCursor=p1;List<Comment> rows=new ArrayList<>();for(int i=0;i<p2;i++)rows.add(new Comment("c"+(p1+i),p1+i==55?9999:100-(p1+i)));return new Task(new Response(rows,p1+p2),fail?new Exception("network"):null);
 }};
 public interface RealApi {Task fetchCommentListV2(PARAMS);}
 public static class Response {final List<Comment> rows;final long cursor;Response(List<Comment> r,long c){rows=r;cursor=c;}public int getStatusCode(){return 0;}public List<Comment> getItems(){return rows;}public long getCursor(){return cursor;}}
 public static class Comment {final String id;final long likes;Comment(String i,long l){id=i;likes=l;}public String getCid(){return id;}public User getUser(){return new User();}public String getText(){return "Комментарий "+id;}public long getDiggCount(){return likes;}public int getCreateTime(){return 12345;}}
 public static class User {public String getNickname(){return "Автор";}public String getUniqueId(){return "author";}}
}'''.replace('PARAMS', params)
            (fake / 'Api.java').write_text(api, encoding='utf-8')
            result = subprocess.run([javac, '-encoding','UTF-8','-d',str(base),*[str(p) for p in base.rglob('*.java')]], capture_output=True, text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            archive = base / 'comments.zip'
            result = subprocess.run([java,'-cp',str(base),'cat.narezany.tokkiyou.Harness',str(archive)], capture_output=True, text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            with zipfile.ZipFile(archive) as z:
                self.assertEqual(set(z.namelist()),{'comments.json','comments.txt'})
                data=json.loads(z.read('comments.json'))
                self.assertEqual(data['video_id'],'123')
                self.assertEqual(data['title'],'Видео 🐱')
                self.assertEqual(len(data['comments']),60)
                self.assertEqual(data['comments'][0]['likes'],9999)
                self.assertEqual(len({r['id'] for r in data['comments']}),60)

if __name__ == '__main__':
    unittest.main()
