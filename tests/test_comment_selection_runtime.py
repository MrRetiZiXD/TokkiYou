"""Execute selection, native fallback and clipboard paths from production methods."""
import pathlib, shutil, unittest, subprocess
import test_settings_safety_runtime as safety
from tokkiyou import dexpatch
import tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]

class CommentSelectionTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_actual_selection_and_native_forwarding(self):
  methods='\n'.join(safety.method('Comments.java',m) for m in [
   '    public static void bindCell',
   '    private static void bindBody',
   '    public static boolean openNativeActions',
   '    private static boolean showCommentActions(Context',
   '    private static boolean nativeLongPress',
   '    public static List nativeItems',
   '    private static void copyComment',
   '    private static void selectComment'])
  sources={
   'cat/narezany/tokkiyou/NativeRead.java':(ROOT/'inject/java/cat/narezany/tokkiyou/NativeRead.java').read_text(encoding='utf-8'),
   'kotlin/jvm/functions/Function0.java':'package kotlin.jvm.functions;public interface Function0<R>{R invoke();}',
   'X/NativeAction.java':'package X;public class NativeAction{public String LIZIZ,LIZLLL;public Integer LIZJ;public kotlin.jvm.functions.Function0 LJ;}',
   'cat/narezany/tokkiyou/CommentSelection.java':(ROOT/'inject/java/cat/narezany/tokkiyou/CommentSelection.java').read_text(encoding='utf-8'),
   'android/content/Context.java':'''package android.content;public class Context {public static final String CLIPBOARD_SERVICE="clipboard";public ClipboardManager clipboard=new ClipboardManager();public Object getSystemService(String s){return clipboard;}public Resources getResources(){return new Resources();}public static class Resources{public Metrics getDisplayMetrics(){return new Metrics();}}public static class Metrics{public float density=1;}}''',
   'android/content/ClipData.java':'package android.content;public class ClipData{public String text;public static ClipData newPlainText(String label,String text){ClipData c=new ClipData();c.text=text;return c;}}',
   'android/content/ClipboardManager.java':'package android.content;public class ClipboardManager{public ClipData clip;public void setPrimaryClip(ClipData c){clip=c;}}',
   'android/view/View.java':'package android.view;public class View{public OnLongClickListener listener;public void setOnLongClickListener(OnLongClickListener l){listener=l;}public boolean attached=true;public android.content.Context context=new android.content.Context();public boolean isAttachedToWindow(){return attached;}public android.content.Context getContext(){return context;}public interface OnLongClickListener{boolean onLongClick(View v);}}',
   'android/view/ViewGroup.java':'package android.view;public class ViewGroup extends View{public int getChildCount(){return 0;}public View getChildAt(int i){return null;}}',
   'android/text/TextUtils.java':'package android.text;public class TextUtils{public static boolean isEmpty(String s){return s==null||s.isEmpty();}}',
   'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View{public int start=-1,end=-1,color;public String text;public TextView(android.content.Context c){context=c;}public void setText(String s){text=s;}public void setTextColor(int c){color=c;}public void setTextSize(float n){}public void setTextIsSelectable(boolean b){}public void setFocusable(boolean b){}public void setFocusableInTouchMode(boolean b){}public void setLineSpacing(float a,float b){}public void setPadding(int a,int b,int c,int d){}public CharSequence getText(){return text;}public int getSelectionStart(){return start;}public int getSelectionEnd(){return end;}}''',
   'cat/narezany/tokkiyou/Test.java':r'''package cat.narezany.tokkiyou;
import android.content.*;import android.view.*;import android.widget.*;import android.text.*;import java.util.*;import kotlin.jvm.functions.Function0;
class Diary{static void note(String s){}}
class Skin{int text=123;static Skin remembered(Context c){return new Skin();}}
class Fonts{static void apply(TextView v){}}
class Text{static String COMMENT_SELECT_COPY="select and copy",COMMENT_COPY="copy",COMMENT_SELECT="select",COMMENT_TIKTOK="native",COMMENT_COPIED="copied",COMMENT_COPY_SELECTED="selected",COMMENT_SELECT_HINT="hint",CLOSE="close";}
class Screen{static String message;static void say(String s){message=s;}}
class CommentText{static String from(Object c){return ((Test.Cell)c).body;}}
class ModDialog{interface Click{void call(Object dialog,int which);}static Click click;static boolean fail;static class Builder{Builder(Context c){}Builder setItems(CharSequence[] items,Click c){click=c;return this;}void show(){if(fail)throw new IllegalStateException();}}}
class Panel{static Panel last;TextView body;Runnable copy;boolean closed;static Panel with(Context c,Skin s,String title){last=new Panel();return last;}void view(TextView v){body=v;}void primaryKeepOpen(String t,Runnable r){copy=r;}void quiet(String s,Object r){}void show(){}void close(){closed=true;}}
public class Test{static boolean enabled=true;static boolean isCopyEnabled(){return enabled;}static void mark(View v){}private static final ThreadLocal<Boolean> nativeMenu=new ThreadLocal<Boolean>();
'''+methods.replace('X.0oi8','X.NativeAction')+r'''
public static class Cell{public View itemView=new View();String body="hello 😀\nsecond line";int calls;boolean fail;public void k8(){if(openNativeActions(this))throw new AssertionError("recursive mod menu");calls++;if(fail)throw new IllegalStateException();}}
public static class Body{String text;Body(String t){text=t;}public String getText(){return text;}}
public static class Fragment{public Body LLJJL;public Context context=new Context();boolean dismissed;Fragment(String t){LLJJL=new Body(t);}public Context getContext(){return context;}public void dismissAllowingStateLoss(){dismissed=true;}}
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
public static void main(String[] a){
 check(CommentSelection.part("abc",-1,2)==null,"negative selection");check(CommentSelection.part("abc",1,1)==null,"empty selection");check(CommentSelection.part("abc",0,4)==null,"invalid bounds");check("bc".equals(CommentSelection.part("abc",3,1)),"reversed selection");check("😀".equals(CommentSelection.part("a😀b",2,3)),"emoji cannot split");check(" \n".equals(CommentSelection.part("x \ny",1,3)),"whitespace preserved");

 Cell c=new Cell();check(!openNativeActions(c),"native menu must open directly");
 Fragment f=new Fragment(c.body);X.NativeAction stock=new X.NativeAction();stock.LIZIZ="copy";stock.LIZLLL="Copy";stock.LIZJ=42;
 List original=new ArrayList();original.add(stock);List actions=nativeItems(f,original);
 check(original.size()==1&&actions.size()==2&&actions.get(0)==stock,"native actions preserved, input immutable");
 X.NativeAction select=(X.NativeAction)actions.get(1);check(select.LIZJ==42,"native copy icon reused");
 check(nativeItems(f,actions)==actions,"no duplicate actions");select.LJ.invoke();check(f.dismissed,"close native sheet before selection");
 Panel p=Panel.last;check(p.body.color==123,"readable themed text");p.copy.run();check(!p.closed&&Screen.message.equals("hint"),"no selection cannot copy whole body");p.body.start=6;p.body.end=8;p.copy.run();check(p.closed&&f.context.clipboard.clip.text.equals("😀"),"explicit selected copy");
 bindCell(c);check(c.itemView.listener.onLongClick(c.itemView)&&c.calls==1,"native menu forwarded once");
 c.body="new recycled comment";bindCell(c);c.itemView.listener.onLongClick(c.itemView);check(c.calls==2,"recycled native actions");
 enabled=false;check(nativeItems(f,original)==original,"disabled leaves native list untouched");
 enabled=true;f.LLJJL=new Body("");check(nativeItems(f,original)==original,"image-only comment unchanged");

}}
'''}
  with tempfile.TemporaryDirectory() as folder:
   files=[]
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():
    file=pathlib.Path(folder)/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source,encoding='utf-8');files.append(str(file))
   result=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',folder,*files],capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',folder,'cat.narezany.tokkiyou.Test'],capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr)

 def test_hook_is_scoped_and_idempotent(self):
  code='''.class public Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;
.super Ljava/lang/Object;
.method public k8()V
    .registers 19
    invoke-static/range {v3 .. v17}, Lcom/ss/android/ugc/aweme/commentv2/commentlist/CommentActionMenuVM;->h43()V
    return-void
.end method
'''
  with tempfile.TemporaryDirectory() as folder:
   file=pathlib.Path(folder)/'Cell.smali';file.write_text(code,encoding='utf-8')
   counts=dexpatch.rewrite_anchored(folder)
   self.assertEqual(1,counts['native comment text actions'])
   self.assertIn(dexpatch.COMMENTS+'->openNativeActions(Ljava/lang/Object;)Z',dexpatch.rewrite_targets())
   self.assertIn('invoke-static/range {p0 .. p0}',file.read_text(encoding='utf-8'))
   self.assertFalse(dexpatch.rewrite_anchored(folder))
   file.write_text(code.replace('BaseCommentCell;','OtherCell;'),encoding='utf-8')
   self.assertFalse(dexpatch.rewrite_anchored(folder))

 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_language_is_initialized_before_new_labels(self):
  source=(ROOT/'inject/java/cat/narezany/tokkiyou/Text.java').read_text(encoding='utf-8')
  sources={
   'cat/narezany/tokkiyou/NativeRead.java':(ROOT/'inject/java/cat/narezany/tokkiyou/NativeRead.java').read_text(encoding='utf-8'),
   'kotlin/jvm/functions/Function0.java':'package kotlin.jvm.functions;public interface Function0<R>{R invoke();}',
   'X/NativeAction.java':'package X;public class NativeAction{public String LIZIZ,LIZLLL;public Integer LIZJ;public kotlin.jvm.functions.Function0 LJ;}','cat/narezany/tokkiyou/Text.java':source,
   'Test.java':'''public class Test{public static void main(String[] a)throws Exception{Class<?> type=Class.forName("cat.narezany.tokkiyou.Text");java.lang.reflect.Field field=type.getDeclaredField("COMMENT_COPY_SELECTED");field.setAccessible(true);String value=(String)field.get(null);String language=java.util.Locale.getDefault().getLanguage();if(!value.equals(language.equals("ru")?"Копировать выделенное":language.equals("uk")?"Копіювати виділене":"Copy selection"))throw new AssertionError(value);}}'''}
  with tempfile.TemporaryDirectory() as folder:
   files=[]
   for name,content in sources.items():
    file=pathlib.Path(folder)/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(content,encoding='utf-8');files.append(str(file))
   result=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',folder,*files],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   for language in ['ru','uk','en']:
    result=subprocess.run(['java','-Duser.language='+language,'-cp',folder,'Test'],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
