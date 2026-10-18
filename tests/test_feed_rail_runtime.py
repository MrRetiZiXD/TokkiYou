"""Exercise native visibility arbitration and per-account settings using production Java."""
import pathlib,shutil,subprocess,tempfile,unittest
from test_account_appearance_runtime import STUBS
from margyt import dexpatch
ROOT=pathlib.Path(__file__).resolve().parents[1]
class FeedRailTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_visibility_and_account_isolation(self):
  sources={k:STUBS[k] for k in ['android/content/Context.java','android/content/SharedPreferences.java']}
  sources.update({
   'cat/narezany/margyt/VideoActions.java':'package cat.narezany.margyt;class VideoActions{static void accountChanged(){} static void bindNative(Object c){} static void selected(Object c,Object p){}}',
   'cat/narezany/margyt/Repost.java':'package cat.narezany.margyt;class Repost{static final int TAG=2;static void reload(){} static void anchor(Object c){} static boolean visibility(android.view.View v,int value){return false;}static boolean replacing(android.view.View v){return false;}}',

   'android/view/View.java':'package android.view;public class View{public static int VISIBLE=0,INVISIBLE=4,GONE=8;int visibility;java.util.Map<Integer,Object> tags=new java.util.HashMap<>();public Object getTag(int k){return tags.get(k);}public void setTag(int k,Object v){tags.put(k,v);}public int writes;public boolean isAttachedToWindow(){return true;}public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;writes++;}public void post(Runnable r){r.run();}}',
   'cat/narezany/margyt/Margy.java':'package cat.narezany.margyt;class Margy{static android.content.Context context=new android.content.Context();static android.content.Context context(){return context;}}',
   'cat/narezany/margyt/AccountAppearance.java':'package cat.narezany.margyt;class AccountAppearance{static String uid="111";static android.content.SharedPreferences prefs(android.content.Context c){return c.getSharedPreferences(uid,0);}}',
   'cat/narezany/margyt/Diary.java':'package cat.narezany.margyt;class Diary{static void note(String s){}}',
   'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long uptimeMillis(){return 100000;}}',
   'cat/narezany/margyt/Md3.java':'package cat.narezany.margyt;class Md3{static final String KEY_ICONS="md3_icons";static boolean isOn(String key){return true;}static void rail(android.view.View v,int kind){}static void icons(android.view.View v,int kind){}}',
   'cat/narezany/margyt/FeedRail.java':(ROOT/'inject/java/cat/narezany/margyt/FeedRail.java').read_text(encoding='utf-8'),
   'cat/narezany/margyt/Test.java':'''package cat.narezany.margyt;import android.view.View;public class Test{
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[] a){
com.ss.android.ugc.aweme.feed.assem.digg.VideoDiggAssem component=new com.ss.android.ugc.aweme.feed.assem.digg.VideoDiggAssem();View v=component.getContentView();FeedRail.bindNative(component);check(v.getVisibility()==0,"default stays native");
FeedRail.setShown(0,false);check(v.getVisibility()==8,"live hide");FeedRail.setVisibility(v,0);check(v.getVisibility()==8,"native show cannot flicker hidden slot");FeedRail.setVisibility(v,4);FeedRail.setShown(0,true);check(v.getVisibility()==4,"restore latest native intent, not forced visible");
FeedRail.setVisibility(v,0);FeedRail.setShown(0,false);AccountAppearance.uid="222";FeedRail.reloadSettings();check(v.getVisibility()==0,"second account defaults");AccountAppearance.uid="111";FeedRail.reloadSettings();check(v.getVisibility()==8,"first account retained");
View unrelated=new View();FeedRail.setVisibility(unrelated,4);check(unrelated.getVisibility()==4,"unregistered comments/media unchanged");int writes=v.writes;FeedRail.bindNative(component);check(v.writes==writes,"repeat binding no redraw");FeedRail.bindNative(new Object());FeedRail.setVisibility(null,0);check(FeedRail.shown(-1),"unknown kind untouched");
for(int i=0;i<4;i++)FeedRail.setShown(i,false);com.ss.android.ugc.aweme.feed.assem.share.VideoShareAssem share=new com.ss.android.ugc.aweme.feed.assem.share.VideoShareAssem();FeedRail.bindNative(share);check(share.getContentView().getVisibility()==8,"newly created share hidden immediately");
}}'''
  })
  for name in ['com.ss.android.ugc.aweme.feed.assem.digg.VideoDiggAssem','com.ss.android.ugc.aweme.feed.assem.share.VideoShareAssem']:
   pkg,cl=name.rsplit('.',1);sources[name.replace('.','/')+'.java']='package '+pkg+';public class '+cl+'{android.view.View view=new android.view.View();public android.view.View getContentView(){return view;}}'
  with tempfile.TemporaryDirectory() as folder:
   files=[]
   __import__("android_doubles").with_mod(sources)
   for name,source in sources.items():
    f=pathlib.Path(folder)/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(source,encoding='utf-8');files.append(str(f))
   result=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',folder,*files],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
   result=subprocess.run(['java','-cp',folder,'cat.narezany.margyt.Test'],capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
 def test_all_four_hooks_and_native_setter(self):
  with tempfile.TemporaryDirectory() as folder:
   for i,owner in enumerate(dexpatch.RAIL_CLASSES):
    code='.class public '+owner+'\n.super Ljava/lang/Object;\n.method public final onViewCreated(Landroid/view/View;)V\n    .registers 20\n    return-void\n.end method\n'
    (pathlib.Path(folder)/(str(i)+'.smali')).write_text(code,encoding='utf-8')
   counts=dexpatch.rewrite_anchored(folder);self.assertEqual(4,counts['native right-area slots']);self.assertFalse(dexpatch.rewrite_anchored(folder))
   f=pathlib.Path(folder)/'visibility.smali';f.write_text('invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V\n',encoding='utf-8')
   dexpatch.rewrite_models(folder);self.assertIn('FeedRail;->setVisibility(Landroid/view/View;I)V',f.read_text(encoding='utf-8'))
