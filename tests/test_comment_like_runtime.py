"""Comment heart counts stay in sync even in obfuscated, tagged cells."""
import pathlib, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

STUBS = {
    'android/content/Context.java': '''package android.content; public class Context {
      public static final int MODE_PRIVATE=0; public SharedPreferences getSharedPreferences(String n,int m){return null;}}
    ''',
    'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {
      Editor edit(); interface Editor {Editor putInt(String k,int v); Editor remove(String k); void apply();}}
    ''',
    'android/content/res/ColorStateList.java': '''package android.content.res; public class ColorStateList {
      public int colour; public ColorStateList(int c){colour=c;} public int getDefaultColor(){return colour;}}
    ''',
    'android/content/res/Resources.java': '''package android.content.res; public class Resources {
      public String getResourceEntryName(int id){return "obfuscated";}}
    ''',
    'android/view/ViewParent.java': '''package android.view; public interface ViewParent {}''',
    'android/view/ViewTreeObserver.java': '''package android.view; public class ViewTreeObserver {
      public interface OnPreDrawListener {boolean onPreDraw();} public boolean isAlive(){return true;}
      public void addOnPreDrawListener(OnPreDrawListener l){} public void removeOnPreDrawListener(OnPreDrawListener l){}
    }''',
    'android/os/SystemClock.java': '''package android.os; public class SystemClock {public static long uptimeMillis(){return 100;}}''',
    'android/view/View.java': '''package android.view; public class View {
      private ViewParent parent; private final java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
      public ViewParent getParent(){return parent;} public void parent(ViewParent p){parent=p;}
      public Object getTag(int k){return tags.get(k);} public void setTag(int k,Object v){tags.put(k,v);}
      public int id; public int getId(){return id;} public android.content.res.Resources getResources(){return new android.content.res.Resources(){public String getResourceEntryName(int i){return i==0x7f000007?"comment_like":"obfuscated";}};}
      public boolean isSelected(){return false;} public boolean isActivated(){return false;}
      public ViewTreeObserver getViewTreeObserver(){return new ViewTreeObserver();}
    }''',
    'android/view/ViewGroup.java': '''package android.view; public class ViewGroup extends View implements ViewParent {
      private final java.util.List<View> children=new java.util.ArrayList<>();
      public void addView(View v){children.add(v);v.parent(this);} public int getChildCount(){return children.size();}
      public View getChildAt(int n){return children.get(n);}}
    ''',
    'android/widget/TextView.java': '''package android.widget; public class TextView extends android.view.View {
      private CharSequence text; private int colour=0xff888888; private android.content.res.ColorStateList colors=new android.content.res.ColorStateList(colour);
      public TextView(CharSequence t){text=t;} public CharSequence getText(){return text;}
      public void setTextColor(int c){colour=c;colors=new android.content.res.ColorStateList(c);}
      public void setTextColor(android.content.res.ColorStateList c){colors=c;colour=c.getDefaultColor();}
      public int getCurrentTextColor(){return colour;} public android.content.res.ColorStateList getTextColors(){return colors;}
    }''',
    'android/widget/EditText.java': '''package android.widget; public class EditText extends TextView {public EditText(){super("");}}''',
    'cat/narezany/tokkiyou/TokkiYou.java': '''package cat.narezany.tokkiyou; class TokkiYou {static String PREFS="m";static android.content.Context context(){return null;}}''',
    'cat/narezany/tokkiyou/AccountAppearance.java': '''package cat.narezany.tokkiyou; class AccountAppearance {static android.content.SharedPreferences prefs(android.content.Context c){return null;}}''',
    'cat/narezany/tokkiyou/Accent.java': '''package cat.narezany.tokkiyou; class Accent {static final int TIKTOK=0xfffe2c55; static int colour(){return 0xff39b9c7;}static void forget(){}static void refreshLikeViews(){}}''',
    'cat/narezany/tokkiyou/Palette.java': '''package cat.narezany.tokkiyou; class Palette {static boolean captures(int a,int b){return (a&0xffffff)==(b&0xffffff);}static int map(int a,int b,int c){return captures(a,b)?(a&0xff000000)|(c&0xffffff):a;}}''',
}

HARNESS = r'''package cat.narezany.tokkiyou;
import android.view.*;import android.widget.*;
public class CommentLikeHarness {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a){
   ViewGroup cell=new ViewGroup();cell.setTag(0x5454434D,Boolean.TRUE);
   ViewGroup controls=new ViewGroup();cell.addView(controls);
   android.widget.ImageView heart=new android.widget.ImageView();heart.id=0x7f000007;controls.addView(heart);
   TextView count=new TextView("298");controls.addView(count);
   check(LikeColors.inComment(heart),"tagged native comment cell recognized without resource names");
   LikeColors.syncCount(heart,true);check(count.getCurrentTextColor()==Accent.colour(),"comment count follows liked accent");
   LikeColors.syncCount(heart,false);check(count.getCurrentTextColor()==0xff888888,"unliked count restores native color");
 }
}'''

class CommentLikeRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_tagged_comment_count(self):
        with tempfile.TemporaryDirectory() as directory:
            work=pathlib.Path(directory); sources=dict(STUBS)
            for name in ('LikeColors.java','NativeRead.java'):
                path=ROOT/'inject/java/cat/narezany/tokkiyou'/name
                if name=='NativeRead.java':
                    sources['cat/narezany/tokkiyou/NativeRead.java']='package cat.narezany.tokkiyou; class NativeRead {}'
                else:sources['cat/narezany/tokkiyou/'+name]=path.read_text(encoding='utf-8')
            sources['android/widget/ImageView.java']='package android.widget; public class ImageView extends android.view.View {}'
            sources['cat/narezany/tokkiyou/CommentLikeHarness.java']=HARNESS
            __import__("android_doubles").with_mod(sources)
            for name,content in sources.items():
                target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content,encoding='utf-8')
            built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
            self.assertEqual(0,built.returncode,built.stderr)
            run=subprocess.run(['java','-cp',str(work),'cat.narezany.tokkiyou.CommentLikeHarness'],capture_output=True,text=True)
            self.assertEqual(0,run.returncode,run.stderr)

