"""Stand-ins for the parts of Android the runtime tests are compiled against."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
STUBS={
'android/content/Context.java':'package android.content;public class Context {public android.content.res.Resources getResources(){return new android.content.res.Resources();}}',
'android/content/res/Resources.java':'package android.content.res;public class Resources {public static class Metrics {public float density=1;}public Metrics getDisplayMetrics(){return new Metrics();}public int getIdentifier(String a,String b,String c){return 0;}public int getDimensionPixelSize(int a){return 0;}}',
'android/view/ViewParent.java':'package android.view;public interface ViewParent {ViewParent getParent();void requestDisallowInterceptTouchEvent(boolean yes);}',
'android/view/MotionEvent.java':'package android.view;public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action;float x,y;public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}public int getActionMasked(){return action;}public float getRawX(){return x;}public float getRawY(){return y;}}',
'android/view/Gravity.java':'package android.view;public class Gravity {public static final int BOTTOM=1,CENTER_HORIZONTAL=2,CENTER=3,CENTER_VERTICAL=4;}',
'android/view/View.java':"""package android.view;import java.util.*;public class View implements ViewParent {
public static final int VISIBLE=0;public ViewGroup parent;public boolean attached=true,clickable;public int visibility=0;public OnClickListener click;
public ArrayList<Runnable> pending=new ArrayList<Runnable>();public interface OnClickListener {void onClick(View v);}public View(android.content.Context c){}public View(){this(new android.content.Context());}
public ViewParent getParent(){return parent;}public void requestDisallowInterceptTouchEvent(boolean yes){}public android.content.Context getContext(){return new android.content.Context();}public android.content.res.Resources getResources(){return getContext().getResources();}
public View getRootView(){return parent==null?this:parent.getRootView();}public boolean postDelayed(Runnable r,long d){pending.add(r);return true;}public boolean removeCallbacks(Runnable r){return pending.remove(r);}public void tick(){ArrayList<Runnable> list=new ArrayList<Runnable>(pending);pending.clear();for(Runnable r:list)r.run();}
public void setOnClickListener(OnClickListener l){click=l;}public void setClickable(boolean c){clickable=c;}public void setAlpha(float v){}public void bringToFront(){}public void setElevation(float v){}public void setBackgroundDrawable(Object b){}public void invalidate(){}
public ViewPropertyAnimator animate(){return new ViewPropertyAnimator();}public int getWidth(){return 300;}public int getHeight(){return 50;}public boolean isAttachedToWindow(){return attached;}public int getWindowVisibility(){return visibility;}
public ViewGroup.LayoutParams params;public ViewGroup.LayoutParams getLayoutParams(){return params;}public void setLayoutParams(ViewGroup.LayoutParams p){params=p;}protected void onDraw(android.graphics.Canvas c){}
}""",
'android/view/ViewPropertyAnimator.java':'package android.view;public class ViewPropertyAnimator {Runnable end;public ViewPropertyAnimator alpha(float f){return this;}public ViewPropertyAnimator translationY(float f){return this;}public ViewPropertyAnimator setStartDelay(long d){return this;}public ViewPropertyAnimator setDuration(long d){return this;}public ViewPropertyAnimator withEndAction(Runnable r){end=r;return this;}public void cancel(){}public void start(){if(end!=null)end.run();}}',
'android/view/ViewGroup.java':'package android.view;import java.util.*;public class ViewGroup extends View {public ArrayList<View> children=new ArrayList<View>();public boolean intercepted;public static class LayoutParams {public static int WRAP_CONTENT=-2;public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}public ViewGroup(){super();}public ViewGroup(android.content.Context c){super(c);}public void addView(View v,LayoutParams p){children.add(v);v.parent=this;v.params=p;}public void removeView(View v){children.remove(v);v.parent=null;}public void requestDisallowInterceptTouchEvent(boolean yes){intercepted=yes;}public void setClipChildren(boolean b){}',
'android/widget/FrameLayout.java':'package android.widget;public class FrameLayout extends android.view.ViewGroup {public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public int bottomMargin;public LayoutParams(int w,int h,int gravity){super(w,h);}}}',
'android/widget/LinearLayout.java':'package android.widget;public class LinearLayout extends android.view.ViewGroup {public static int HORIZONTAL=0;public LinearLayout(android.content.Context c){super(c);}public void setOrientation(int o){}public void setGravity(int g){}public void setPadding(int a,int b,int c,int d){}public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public int leftMargin;public LayoutParams(int w,int h){super(w,h);}}}',
'android/widget/TextView.java':'package android.widget;public class TextView extends android.view.View {public String text;public TextView(android.content.Context c){super(c);}public void setText(String s){text=s;}public void setTextColor(int c){}public void setTextSize(float f){}public void setTypeface(Object o,int i){}public void setSingleLine(boolean b){}public void setHorizontallyScrolling(boolean b){}public void setGravity(int i){}public android.graphics.Paint getPaint(){return new android.graphics.Paint(0);}}',
'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable;public class GradientDrawable {public void setColor(int c){}public void setCornerRadius(float f){}}',
'android/graphics/Typeface.java':'package android.graphics;public class Typeface {public static int BOLD=1;}',
'android/graphics/Paint.java':'package android.graphics;public class Paint {public static int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public Paint(int f){}public void setColorFilter(Object o){}public float measureText(String s){return s.length()*7;}}',
'android/graphics/Bitmap.java':'package android.graphics;public class Bitmap {public int getWidth(){return 20;}public int getHeight(){return 26;}}',
'android/graphics/Canvas.java':'package android.graphics;public class Canvas {public void drawBitmap(Bitmap b,Rect r,RectF f,Paint p){}}',
'android/graphics/Rect.java':'package android.graphics;public class Rect {public void set(int a,int b,int c,int d){}}',
'android/graphics/RectF.java':'package android.graphics;public class RectF {public void set(float a,float b,float c,float d){}}',
'android/graphics/PorterDuff.java':'package android.graphics;public class PorterDuff {public enum Mode {SRC_IN}}',
'android/graphics/PorterDuffColorFilter.java':'package android.graphics;public class PorterDuffColorFilter {public PorterDuffColorFilter(int c,PorterDuff.Mode m){}}',
'android/view/animation/DecelerateInterpolator.java':'package android.view.animation;public class DecelerateInterpolator {}',
'android/animation/ValueAnimator.java':'package android.animation;public class ValueAnimator {int value;public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator v);}public static ValueAnimator ofInt(int a,int b){ValueAnimator v=new ValueAnimator();v.value=b;return v;}public void setDuration(long l){}public void setInterpolator(Object o){}public void addUpdateListener(AnimatorUpdateListener l){l.onAnimationUpdate(this);}public Object getAnimatedValue(){return value;}public void start(){}public void cancel(){}}',
'cat/narezany/tokkiyou/Accent.java':'package cat.narezany.tokkiyou;class Accent {static int colour(){return 0xffa4c9ff;}}',
'cat/narezany/tokkiyou/LockArt.java':'package cat.narezany.tokkiyou;class LockArt {static final int WIDTH=20,HEIGHT=26;static android.graphics.Bitmap get(boolean b){return null;}}'
}
STUBS['cat/narezany/tokkiyou/PlayerTools.java']='package cat.narezany.tokkiyou;class PlayerTools{static float rate=2;static int writes;static float speed(){return rate;}static boolean applyHoldRate(){writes++;return true;}}'
STUBS['android/view/ViewGroup.java']+='}'


def mod_doubles():
    """The two helpers nearly every class of the mod now leans on.

    `Names` is the real one, because what it decides is what the tests are
    about; `Probe` is the same constants with nothing behind them.
    """
    import re
    base = ROOT / 'inject/java/cat/narezany/tokkiyou'
    probe = (base / 'Probe.java').read_text(encoding='utf-8')
    constants = ' '.join(re.search(r'static final int (THEME.*?);', probe, re.S).group(1).split())
    return {
        'cat/narezany/tokkiyou/Probe.java': 'package cat.narezany.tokkiyou;final class Probe{static final int %s;'
            'static long start(){return 0L;}static void stop(int what,long started){}}' % constants,
        'cat/narezany/tokkiyou/Names.java': (base / 'Names.java').read_text(encoding='utf-8'),
        'android/util/SparseArray.java': 'package android.util;public class SparseArray<E>{private final java.util.HashMap<Integer,E> held=new java.util.HashMap<>();'
            'public E get(int key){return held.get(key);}public void put(int key,E value){held.put(key,value);}}',
    }


def with_mod(sources):
    """Add whichever of the helpers a set of sources uses and does not bring."""
    doubles = mod_doubles()
    everything = ' '.join(sources.values())
    if 'Probe.' in everything:
        sources.setdefault('cat/narezany/tokkiyou/Probe.java', doubles['cat/narezany/tokkiyou/Probe.java'])
    if 'Local.of(' in everything:
        # the words as they were written: the tests read the Russian
        sources.setdefault('cat/narezany/tokkiyou/Local.java',
                           'package cat.narezany.tokkiyou;final class Local{static String of(String russian){return russian;}}')
    if 'Names.' in everything:
        sources.setdefault('cat/narezany/tokkiyou/Names.java', doubles['cat/narezany/tokkiyou/Names.java'])
        sources.setdefault('android/util/SparseArray.java', doubles['android/util/SparseArray.java'])
    return sources
