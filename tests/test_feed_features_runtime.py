"""Run comment-model extraction, caption dates and live seekbar switching."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
    'cat/narezany/margyt/DateOverlay.java':'package cat.narezany.margyt;class DateOverlay {static void update(){}static android.view.View bound;static void register(android.view.View v,String t){}static void bind(android.view.View v,String d,String n){bound=v;}static void bind(android.view.View v,String d){bound=v;}}',
    'android/content/ClipData.java': 'package android.content; public class ClipData {public String label,body;public static ClipData newPlainText(CharSequence label,CharSequence body){ClipData result=new ClipData();result.label=label.toString();result.body=body.toString();return result;}}',
    'android/content/Context.java': '''package android.content; public class Context {
        public static int MODE_PRIVATE=0; public SharedPreferences getSharedPreferences(String n,int m){return null;} }''',
    'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {
        boolean getBoolean(String k,boolean d); Editor edit();interface Editor {Editor putBoolean(String k,boolean v);void apply();} }''',
    'android/view/View.java': 'package android.view; public class View {public java.util.Map<Integer,Object> tags=new java.util.HashMap<>();public Object getTag(int key){return tags.get(key);}public void setTag(int key,Object value){tags.put(key,value);}public boolean isShown(){return true;}public boolean isAttachedToWindow(){return true;}public void post(Runnable r){r.run();}}',
    'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return null;}}',
    'android/os/Handler.java': 'package android.os;public class Handler {public Handler(Looper l){} public void post(Runnable r){r.run();}}',
    'cat/narezany/margyt/NativeRead.java':'package cat.narezany.margyt;class NativeRead{static Object get(Object o,String n){try{return o.getClass().getMethod(n).invoke(o);}catch(Exception e){return null;}}static long number(Object o){return 0;}}',
    'cat/narezany/margyt/Margy.java': '''package cat.narezany.margyt;class Margy {
        static String PREFS="mod";static android.content.Context context(){return null;} }''',
    'cat/narezany/margyt/Diary.java': 'package cat.narezany.margyt;class Diary {static void note(String s){}}',
    'com/ss/android/ugc/aweme/comment/model/Comment.java': '''package com.ss.android.ugc.aweme.comment.model;
        public class Comment {public String body;public Comment(String b){body=b;}public String getText(){return body;} }''',
    'com/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell.java': '''package com.ss.android.ugc.aweme.commentv2.commentlist.powercell;
        public class BaseCommentCell {public com.ss.android.ugc.aweme.comment.model.Comment comment;
        public BaseCommentCell(String b){comment=new com.ss.android.ugc.aweme.comment.model.Comment(b);}
        public com.ss.android.ugc.aweme.comment.model.Comment D6(){return comment;} }''',
}
STUBS['cat/narezany/margyt/DateInfo.java']='package cat.narezany.margyt;class DateInfo{static final int CELL_TAG=0x7e0d0335;static class Data{static Data of(Object p){return null;}}static void open(android.view.View r,android.view.View c){}}'
STUBS['cat/narezany/margyt/VideoActions.java']='package cat.narezany.margyt;class VideoActions{static void accountChanged(){} static void bindNative(Object c){} static void selected(Object c,Object p){}}'
STUBS['cat/narezany/margyt/NativeRead.java']='package cat.narezany.margyt;class NativeRead {static long number(Object o){return o instanceof Number?((Number)o).longValue():0;}static Object get(Object o,String name){try{return o.getClass().getMethod(name).invoke(o);}catch(Throwable e){return null;}}static Object item(Object o){return get(o,"getItem");}static String aid(Object post){Object v=get(post,"getAid");return v instanceof String?(String)v:"";}}'
HARNESS = '''package cat.narezany.margyt;
public class FeedHarness {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    public static class Bar extends android.view.View {public int type;public void setSeekBarShowType(int v){type=v;}}
    public static class Post {public String body;public long created;
        public Post(String b,long t){body=b;created=t;}public String getAid(){return body;}public String getDesc(){return body;}public long getCreateTime(){return created;} }
    public static class Item {Post post;public Item(Post p){post=p;}public Post getAweme(){return post;}}
    public static class Holder {android.view.View view=new android.view.View();Item item;public Holder(Post p){item=new Item(p);}public Item getItem(){return item;}public android.view.View getContentView(){return view;}}
    static class Listener {
        String nickname="Очень длинный ник автора вместо короткого ответа";
        com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell cell;
        Listener(String b){cell=new com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell(b);}
    }
    public static void main(String[] args) {
        Post dated=new Post("a",1609459200L);Holder author=new Holder(dated),description=new Holder(dated);Dates.bindAuthor(author);Dates.bindPost(description,dated);check(DateOverlay.bound==author.view,"selected date routes to native author slot rather than description");author.item=new Item(new Post("b",1609459300L));Dates.bindAuthor(author);Dates.bindPost(description,dated);check(DateOverlay.bound==description.view,"recycled author row cannot show a previous post date");
        Bar a=new Bar(),b=new Bar();Seekbar.setEnabled(false);
        Seekbar.setSeekBarShowType(a,3);Seekbar.setSeekBarShowType(b,100);
        check(a.type==3&&b.type==100,"Disabled preserves native seek modes");
        Seekbar.setEnabled(true);check(a.type==0&&b.type==0,"Existing bars switch immediately");
        Seekbar.setSeekBarShowType(a,4);check(a.type==0,"Later native hide cannot override enabled mode");
        Seekbar.setEnabled(false);check(a.type==4&&b.type==100,"Disable restores latest requested modes independently");
        String raw="@friend: ответ\\n  Вторая строка 🙂";
        android.content.ClipData clip=CommentClipboard.LIZ("Автор с длинным ником",raw,java.util.Collections.emptyList());
        check(raw.equals(clip.body),"Native copy preserves only comment body, including mentions and newlines");
        check(!clip.body.startsWith("Автор"),"No author prefix");
        check("".equals(CommentClipboard.LIZ("Автор",null,null).body),"Null body cannot fall back to author");
        Listener listener=new Listener("Да");check("Да".equals(CommentText.from(listener)),"Short reply is not a nickname");
        listener.cell.comment.body="👍";check("👍".equals(CommentText.from(listener)),"Recycled row and emoji");
        listener.cell.comment.body="  @someone\\nВторая строка  ";
        check(listener.cell.comment.body.equals(CommentText.from(listener)),"Preserve body punctuation and whitespace");
        listener.cell.comment.body="";check(CommentText.from(listener)==null,"Empty comment does not copy nickname");
        check(CommentText.from(new Object())==null,"No arbitrary row text fallback");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        Post post=new Post("Подпись",1609459200L);
        Dates.setEnabled(false);check("Подпись".equals(Dates.getDesc(post)),"Disabled leaves descriptions alone");
        Dates.setEnabled(true);String first=Dates.getDesc(post);
        check("Подпись".equals(first),"Date comes from this post timestamp");
        check(first.equals(Dates.getDesc(post))&&"Подпись".equals(post.body),"No model mutation or repeated date");
        check("".equals(Dates.getDesc(new Post("",1609459200L))),"Empty caption still has a date");
        check("Original".equals(Dates.getDesc(new Post("Original",0))),"Unknown timestamp has no invented date");
        check(Dates.label(Long.MAX_VALUE,1700000000L)==null,"Overflow timestamp rejected");
        System.out.println("Seek modes, comment identity and publication dates passed");
    }
}'''


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
class FeedFeaturesRuntimeTest(unittest.TestCase):
    def test_current_models_native_modes_and_date_safety(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = dict(STUBS)
            for name in ('Seekbar', 'Dates', 'CommentText', 'CommentClipboard'):
                sources['cat/narezany/margyt/'+name+'.java'] = (
                    ROOT / ('inject/java/cat/narezany/margyt/'+name+'.java')).read_text(encoding='utf-8')
            sources['cat/narezany/margyt/FeedHarness.java'] = HARNESS
            __import__("android_doubles").with_mod(sources)
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            result = subprocess.run(['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-d', str(work)] +
                                    [str(work/name) for name in sources], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', str(work), 'cat.narezany.margyt.FeedHarness'],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('Seek modes, comment identity and publication dates passed', result.stdout)
