"""Exercise the first-run crash-report question with the real CrashConsent class."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / 'inject/java/cat/narezany/tokkiyou'
PKG = 'package cat.narezany.tokkiyou; '
STUBS = {
    'android/animation/TimeInterpolator.java': 'package android.animation; public interface TimeInterpolator {}',
    'android/view/animation/LinearInterpolator.java':
        'package android.view.animation; public class LinearInterpolator implements android.animation.TimeInterpolator {}',
    'android/view/animation/OvershootInterpolator.java':
        'package android.view.animation; public class OvershootInterpolator implements android.animation.TimeInterpolator {public OvershootInterpolator(float t){}}',
    'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {
        boolean getBoolean(String k,boolean d); Editor edit();
        interface Editor {Editor putBoolean(String k,boolean v); void apply();} }''',
    'android/content/FakePrefs.java': '''package android.content; public class FakePrefs implements SharedPreferences {
        public boolean getBoolean(String k,boolean d){Boolean v=Context.store.get(k);return v==null?d:v;}
        public Editor edit(){return new Editor(){
            public Editor putBoolean(String k,boolean v){Context.store.put(k,v);return this;}
            public void apply(){}};} }''',
    'android/content/DialogInterface.java': '''package android.content; public interface DialogInterface {
        interface OnDismissListener {void onDismiss(DialogInterface d);} }''',
    'android/content/Context.java': '''package android.content; public class Context {
        public static final int MODE_PRIVATE=0;
        public static java.util.Map<String,Boolean> store=new java.util.HashMap<>();
        public SharedPreferences getSharedPreferences(String n,int m){return new FakePrefs();}
        public android.content.res.Resources getResources(){return new android.content.res.Resources();}
        public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
        public android.content.pm.ApplicationInfo getApplicationInfo(){return new android.content.pm.ApplicationInfo();} }''',
    'android/content/res/Resources.java': '''package android.content.res; public class Resources {
        public static class Metrics {public float density=2;}
        public Metrics getDisplayMetrics(){return new Metrics();} }''',
    'android/content/pm/ApplicationInfo.java': 'package android.content.pm; public class ApplicationInfo {}',
    'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {
        public static boolean fail;
        public android.graphics.drawable.Drawable getApplicationIcon(ApplicationInfo i){
            if(fail)throw new RuntimeException("no icon");return new android.graphics.drawable.Drawable();} }''',
    'android/graphics/Typeface.java': '''package android.graphics; public class Typeface {
        public static final int NORMAL=0; public static Typeface create(String n,int s){return new Typeface();} }''',
    'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable; public class Drawable {}',
    'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable; public class GradientDrawable extends Drawable {
        public static final int OVAL=1; public void setShape(int s){} public void setColor(int c){} public void setCornerRadius(float r){} }''',
    'android/view/Gravity.java': 'package android.view; public class Gravity {public static final int CENTER_HORIZONTAL=1,CENTER=17;}',
    'android/view/ViewPropertyAnimator.java': '''package android.view; public class ViewPropertyAnimator {
        private final View v; private Runnable end; ViewPropertyAnimator(View v){this.v=v;}
        public ViewPropertyAnimator alpha(float a){v.alpha=a;return this;}
        public ViewPropertyAnimator scaleX(float a){v.scaleX=a;return this;}
        public ViewPropertyAnimator scaleY(float a){v.scaleY=a;return this;}
        public ViewPropertyAnimator setDuration(long d){return this;}
        public ViewPropertyAnimator setStartDelay(long d){return this;}
        public ViewPropertyAnimator setInterpolator(android.animation.TimeInterpolator i){return this;}
        public ViewPropertyAnimator withEndAction(Runnable r){end=r;return this;}
        public void start(){if(end!=null)end.run();} }''',
    'android/view/View.java': '''package android.view; public class View {
        public static final int VISIBLE=0,INVISIBLE=4,GONE=8;
        public interface OnClickListener {void onClick(View v);}
        public static java.util.List<Object[]> queue=new java.util.ArrayList<>();
        public float alpha=1f,scaleX=1f,scaleY=1f; public int visibility=VISIBLE; public OnClickListener click;
        public View(android.content.Context c){}
        public void setAlpha(float a){alpha=a;} public float getAlpha(){return alpha;}
        public void setScaleX(float v){scaleX=v;} public void setScaleY(float v){scaleY=v;} public void setPivotX(float v){}
        public void setBackground(android.graphics.drawable.Drawable d){}
        public void setVisibility(int v){visibility=v;} public int getVisibility(){return visibility;}
        public void setClickable(boolean b){} public void setFocusable(boolean b){}
        public void setPadding(int a,int b,int c,int d){}
        public void setOnClickListener(OnClickListener l){click=l;}
        public void tap(){if(click!=null)click.onClick(this);}
        public boolean postDelayed(Runnable r,long d){queue.add(new Object[]{r,Long.valueOf(d)});return true;}
        public ViewPropertyAnimator animate(){return new ViewPropertyAnimator(this);} }''',
    'android/view/ViewGroup.java': '''package android.view; public class ViewGroup extends View {
        public static class LayoutParams {public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;
            public int width,height; public LayoutParams(int w,int h){width=w;height=h;} }
        public java.util.List<View> children=new java.util.ArrayList<>();
        public ViewGroup(android.content.Context c){super(c);}
        public void addView(View v){children.add(v);} public void addView(View v,LayoutParams p){children.add(v);} }''',
    'android/widget/LinearLayout.java': '''package android.widget; public class LinearLayout extends android.view.ViewGroup {
        public static final int VERTICAL=1;
        public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
            public int topMargin; public LayoutParams(int w,int h){super(w,h);} }
        public LinearLayout(android.content.Context c){super(c);}
        public void setOrientation(int o){} public void setGravity(int g){} }''',
    'android/widget/FrameLayout.java': '''package android.widget; public class FrameLayout extends android.view.ViewGroup {
        public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
            public LayoutParams(int w,int h){super(w,h);} public LayoutParams(int w,int h,int g){super(w,h);} }
        public FrameLayout(android.content.Context c){super(c);} }''',
    'android/widget/TextView.java': '''package android.widget; public class TextView extends android.view.View {
        public String text=""; public TextView(android.content.Context c){super(c);}
        public void setText(String t){text=t;} public void setTextColor(int i){} public void setTextSize(float n){}
        public void setTypeface(android.graphics.Typeface t){} public void setGravity(int g){}
        public void setLineSpacing(float a,float b){} public void setMinHeight(int h){} }''',
    'android/widget/ImageView.java': '''package android.widget; public class ImageView extends android.view.View {
        public enum ScaleType {FIT_CENTER} public android.graphics.drawable.Drawable picture;
        public ImageView(android.content.Context c){super(c);}
        public void setScaleType(ScaleType t){} public void setImageDrawable(android.graphics.drawable.Drawable d){picture=d;} }''',
    'android/app/Dialog.java': '''package android.app; public class Dialog implements android.content.DialogInterface {
        public boolean showing,cancelable=true,outside=true; public OnDismissListener listener;
        public boolean isShowing(){return showing;} public void setCancelable(boolean b){cancelable=b;}
        public void setCanceledOnTouchOutside(boolean b){outside=b;}
        public void setOnDismissListener(OnDismissListener l){listener=l;}
        public void dismiss(){showing=false;if(listener!=null)listener.onDismiss(this);} }''',
    'android/app/Activity.java': '''package android.app; public class Activity extends android.content.Context {
        public boolean finishing; public boolean isFinishing(){return finishing;} public boolean isDestroyed(){return false;} }''',
    'cat/narezany/tokkiyou/TokkiYou.java': PKG + 'class TokkiYou {static String PREFS="mod";}',
    'cat/narezany/tokkiyou/Skin.java': PKG + '''class Skin {int text=-1; int muted(){return 0x99FFFFFF;}
        static Skin remembered(android.content.Context c){return new Skin();} }''',
    'cat/narezany/tokkiyou/Accent.java': PKG + 'class Accent {static int colour(){return 0xFF3366FF;}}',
    'cat/narezany/tokkiyou/Motion.java': PKG + '''class Motion {static boolean on=true;
        static boolean enabled(android.content.Context c){return on;} static void touch(android.view.View v){} }''',
    'cat/narezany/tokkiyou/Expressive.java': PKG + '''class Expressive {
        static android.graphics.drawable.Drawable tile(android.content.Context c,int fill,int ripple,float top,float bottom,float pressed){
            return new android.graphics.drawable.Drawable();} }''',
    'cat/narezany/tokkiyou/SettingsGlyph.java': PKG + '''class SettingsGlyph {static int made;
        static android.graphics.drawable.Drawable make(String n,int c){made++;return new android.graphics.drawable.Drawable();} }''',
    'cat/narezany/tokkiyou/Diary.java': PKG + 'class Diary {static void note(String s){}}',
    'cat/narezany/tokkiyou/Text.java': PKG + '''class Text {
        static final String CONSENT_TITLE="title",CONSENT_BODY="body",CONSENT_NOTE="note",CONSENT_WAIT="wait %d",
            CONSENT_YES="yes",CONSENT_NO="no"; }''',
    'cat/narezany/tokkiyou/CrashReports.java': PKG + '''class CrashReports {
        static final String KEY="crash_reports",ASKED="crash_consent_v1"; static int sets;
        static void set(android.content.Context c,boolean on){sets++;
            c.getSharedPreferences("mod",0).edit().putBoolean(KEY,on).putBoolean(ASKED,true).apply();} }''',
    'cat/narezany/tokkiyou/Panel.java': PKG + '''class Panel {
        static Panel last; static int created; android.app.Dialog dialog=new android.app.Dialog(); android.view.View content;
        static Panel with(android.content.Context c,Skin s,String t){last=new Panel();created++;return last;}
        Panel view(android.view.View v){content=v;return this;}
        android.app.Dialog dialog(){return dialog;}
        void show(){dialog.showing=true;dialog.cancelable=true;dialog.outside=true;}
        void close(){dialog.dismiss();} }''',
}
HARNESS = PKG + '''
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
public class ConsentHarness {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static void advance(){if(View.queue.isEmpty())return;Object[] e=View.queue.remove(0);((Runnable)e[0]).run();}
    static int answers;
    static ViewGroup answerBox(){
        ViewGroup page=(ViewGroup)Panel.last.content;ViewGroup slot=(ViewGroup)page.children.get(4);
        return (ViewGroup)slot.children.get(1);}
    static String counter(){
        ViewGroup slot=(ViewGroup)((ViewGroup)Panel.last.content).children.get(4);
        ViewGroup wait=(ViewGroup)slot.children.get(0);return ((android.widget.TextView)wait.children.get(1)).text;}
    static void reset(){android.content.Context.store.clear();View.queue.clear();answers=0;}
    public static void main(String[] args){
        for(int run=0;run<2;run++){
            Motion.on=run==0;
            String label=Motion.on?"motion on: ":"motion off: ";
            Activity a=new Activity();
            // answer "no"
            reset();Panel.created=0;
            check(!CrashConsent.asked(a),label+"fresh install is unasked");
            CrashConsent.show(a,()->answers++);
            check(Panel.created==1&&Panel.last.dialog.showing,label+"window shown");
            check(!Panel.last.dialog.cancelable&&!Panel.last.dialog.outside,label+"no back, no outside tap");
            ViewGroup box=answerBox();
            check(box.visibility==View.INVISIBLE,label+"answers hidden at the start");
            check("wait 5".equals(counter()),label+"countdown starts at five");
            CrashConsent.show(a,()->answers+=100);check(Panel.created==1,label+"no second window while one is open");
            for(int i=0;i<4;i++)advance();
            check(box.visibility==View.INVISIBLE&&"wait 1".equals(counter()),label+"still reading after four seconds");
            advance();
            check(box.visibility==View.VISIBLE&&box.alpha==1f,label+"answers appear after five seconds");
            View no=box.children.get(1),yes=box.children.get(0);
            int setsBefore=CrashReports.sets;
            no.tap();no.tap();yes.tap();
            check(answers==1&&CrashReports.sets==setsBefore+1,label+"one answer counted once");
            check(android.content.Context.store.get("crash_reports")==Boolean.FALSE,label+"no keeps reports off");
            check(CrashConsent.asked(a)&&!Panel.last.dialog.showing,label+"asked and closed");
            // answer "yes"
            reset();CrashConsent.show(a,()->answers++);
            check(!CrashConsent.asked(a),label+"unasked again after reset");
            for(int i=0;i<5;i++)advance();
            answerBox().children.get(0).tap();
            check(android.content.Context.store.get("crash_reports")==Boolean.TRUE&&answers==1,label+"yes turns reports on");
            CrashConsent.show(a,()->answers+=100);check(answers==1&&!Panel.last.dialog.showing,label+"never asked twice");
            // switched on in settings before the window existed
            reset();android.content.Context.store.put("crash_reports",Boolean.TRUE);
            int made=Panel.created;CrashConsent.show(a,()->answers++);
            check(Panel.created==made&&CrashConsent.asked(a),label+"existing opt-in is respected");
            // paused mid-countdown decides nothing
            reset();CrashConsent.show(a,()->answers++);
            Panel first=Panel.last;advance();advance();
            CrashConsent.paused(a);
            check(!first.dialog.showing&&answers==0&&!CrashConsent.asked(a),label+"pause leaves it undecided");
            for(int i=0;i<6;i++)advance();
            check(((ViewGroup)((ViewGroup)first.content).children.get(4)).children.get(1).getVisibility()==View.INVISIBLE,label+"countdown stops with the window");
            CrashConsent.show(a,()->answers++);check(Panel.last!=first&&Panel.last.dialog.showing,label+"comes back next time");
            CrashConsent.paused(new Activity());check(Panel.last.dialog.showing,label+"other screens do not close it");
            // no icon available
            reset();android.content.pm.PackageManager.fail=true;int glyphs=SettingsGlyph.made;
            CrashConsent.paused(a);CrashConsent.show(a,()->{});
            check(SettingsGlyph.made==glyphs+1,label+"falls back to a drawn glyph");
            android.content.pm.PackageManager.fail=false;CrashConsent.paused(a);
        }
        System.out.println("Crash-report question scenarios passed");
    }
}
'''


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
class CrashConsentRuntimeTest(unittest.TestCase):
    def test_question_flow(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = dict(STUBS)
            sources['cat/narezany/tokkiyou/CrashConsent.java'] = (SRC / 'CrashConsent.java').read_text(encoding='utf-8')
            sources['cat/narezany/tokkiyou/ConsentHarness.java'] = HARNESS
            __import__("android_doubles").with_mod(sources)
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            compiled = subprocess.run(['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-d', str(work)] +
                                      [str(work / name) for name in sources], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            result = subprocess.run(['java', '-cp', str(work), 'cat.narezany.tokkiyou.ConsentHarness'],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('Crash-report question scenarios passed', result.stdout)

    def test_switch_in_settings_counts_as_asked(self):
        source = (SRC / 'CrashReports.java').read_text(encoding='utf-8')
        self.assertIn('putBoolean(KEY,on).putBoolean(ASKED,true)', source)

    def test_every_consent_string_has_three_languages(self):
        text = (SRC / 'Text.java').read_text(encoding='utf-8')
        for name in ('CONSENT_TITLE', 'CONSENT_BODY', 'CONSENT_NOTE', 'CONSENT_WAIT', 'CONSENT_YES', 'CONSENT_NO'):
            start = text.index('static final String ' + name)
            statement = text[start:text.index(';', start)]
            self.assertEqual(6, statement.count('"'), name + ': Russian, Ukrainian and English are all needed')
        self.assertIn('%d', text[text.index('CONSENT_WAIT'):text.index('CONSENT_YES')])
