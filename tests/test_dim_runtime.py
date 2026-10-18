"""Execute the real Dim.java on small Android doubles; no phone or SDK needed."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
    'android/graphics/Color.java':'package android.graphics;public class Color {public static int BLACK=0xff000000;}',
    'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable {public int alpha=255;public int getAlpha(){return alpha;}public void setAlpha(int v){alpha=v;}public Object getColorFilter(){return null;}public void clearColorFilter(){}}',
    'android/graphics/drawable/ColorDrawable.java':'package android.graphics.drawable;public class ColorDrawable extends Drawable {public int colour;public ColorDrawable(int c){colour=c;}public int getColor(){return colour;}public void setColor(int c){colour=c;}}',
    'android/graphics/drawable/LayerDrawable.java':'package android.graphics.drawable;public class LayerDrawable extends Drawable {public Drawable[] layers;public LayerDrawable(Drawable[] d){layers=d;}public void setAlpha(int v){for(Drawable d:layers)d.setAlpha(v);}public int getAlpha(){return layers[0].getAlpha();}}',
    'android/widget/TextView.java': 'package android.widget; public class TextView extends android.view.View {public CharSequence text;public CharSequence getText(){return text;}public CharSequence getHint(){return null;}}',
    'android/widget/EditText.java': 'package android.widget; public class EditText extends TextView {public CharSequence hint;public CharSequence getHint(){return hint;}}',
    'X/ABCD.java': 'package X;public class ABCD extends android.view.ViewGroup {}',
    'com/ss/android/ugc/aweme/detail/ui/DetailActivity.java':'package com.ss.android.ugc.aweme.detail.ui;public class DetailActivity extends android.app.Activity {}',
    'com/ss/android/ugc/aweme/main/MainActivity.java': 'package com.ss.android.ugc.aweme.main;public class MainActivity extends android.app.Activity {}',
    'android/content/Context.java': '''package android.content;
        public class Context { public static final int MODE_PRIVATE=0;
        public SharedPreferences getSharedPreferences(String n,int m){return null;} }''',
    'android/content/SharedPreferences.java': '''package android.content;
        public interface SharedPreferences {
        boolean getBoolean(String k,boolean d); int getInt(String k,int d);
        boolean contains(String k); Editor edit();
        interface Editor { Editor putBoolean(String k,boolean v);
        Editor putInt(String k,int v); void apply(); } }''',
    'android/content/res/Resources.java': '''package android.content.res;
        public class Resources { public static int reads;public String getResourceEntryName(int id){reads++;return "unknown";} public android.util.DisplayMetrics getDisplayMetrics(){
        return new android.util.DisplayMetrics();} }''',
    'android/util/DisplayMetrics.java': '''package android.util;
        public class DisplayMetrics { public float density=1; }''',
    'android/graphics/Rect.java': '''package android.graphics;
        public class Rect { public int left,top,right,bottom;
        public boolean contains(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;} }''',
    'android/os/Looper.java': '''package android.os;
        public class Looper { static final Looper MAIN=new Looper();
        public static Looper myLooper(){return MAIN;} public static Looper getMainLooper(){return MAIN;} }''',
    'android/os/SystemClock.java': '''package android.os;
        public class SystemClock { public static long now=123456L;
        public static long uptimeMillis(){return now;} }''',
    'android/R.java': '''package android;
        public class R { public static class id { public static int content=1; } }''',
    'android/view/View.java': '''package android.view;
        public class View {
        public int getId(){return 0;}public android.content.res.Resources getResources(){return new android.content.res.Resources();}
        public static final int VISIBLE=0; public ViewGroup parent;public android.graphics.drawable.Drawable background;public android.graphics.drawable.Drawable getBackground(){return background;}public void setBackgroundDrawable(android.graphics.drawable.Drawable d){background=d;}
        public int visibility=0; public java.util.List<Runnable> delayed=new java.util.ArrayList<>();
        public ViewTreeObserver observer=new ViewTreeObserver();
        public boolean selected=false,activated=false;public CharSequence description;public int alphaWrites=0;
        public boolean isSelected(){return selected;}public boolean isActivated(){return activated;}public CharSequence getContentDescription(){return description;}
        public float alpha=1,x=0,y=0; public int left=0,top=0,width=100,height=100;
        public float getAlpha(){return alpha;} public void setAlpha(float a){alpha=a;alphaWrites++;} public int flat; public void forceHasOverlappingRendering(boolean overlapping){if(!overlapping)flat++;}
        public float getTranslationX(){return x;} public void setTranslationX(float a){x=a;}
        public float getTranslationY(){return y;} public void setTranslationY(float a){y=a;}
        public java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
        public Object getTag(int key){return tags.get(key);} public void setTag(int key,Object value){tags.put(key,value);}
        public Object getParent(){return parent;} public int getVisibility(){return visibility;}
        public boolean isShown(){return visibility==VISIBLE&&(parent==null||parent.isShown());}
        public int getWidth(){return width;} public int getHeight(){return height;}
        public void getLocationOnScreen(int[] a){a[0]=left;a[1]=top;}
        public boolean getGlobalVisibleRect(android.graphics.Rect r){r.left=left;r.top=top;
        r.right=left+width;r.bottom=top+height;return true;}
        public boolean isAttachedToWindow(){return true;}
        public boolean layoutRequested=false;public boolean isLayoutRequested(){return layoutRequested;}
        public void postDelayed(Runnable r,long delay){delayed.add(r);}
        public void removeCallbacks(Runnable r){delayed.remove(r);}
        public ViewTreeObserver getViewTreeObserver(){return observer;}
        public View findViewById(int id){return this;}
        }''',
    'android/view/ViewGroup.java': '''package android.view;
        public class ViewGroup extends View {
        private java.util.List<View> children=new java.util.ArrayList<>();
        public void addView(View v){children.add(v);v.parent=this;}public void removeAllViews(){for(View v:children)v.parent=null;children.clear();}
        public static int childReads;public int getChildCount(){childReads++;return children.size();}
        public View getChildAt(int n){return children.get(n);} }''',
    'android/widget/ImageView.java': 'package android.widget; public class ImageView extends android.view.View {}',
    'android/view/ViewTreeObserver.java': '''package android.view;
        public class ViewTreeObserver {
        public interface OnGlobalLayoutListener { void onGlobalLayout(); }
        public interface OnPreDrawListener { boolean onPreDraw(); }
        public interface OnWindowFocusChangeListener { void onWindowFocusChanged(boolean focus); }
        public OnGlobalLayoutListener layout; public OnPreDrawListener draw;
        public OnWindowFocusChangeListener focus;
        public void addOnGlobalLayoutListener(OnGlobalLayoutListener v){layout=v;}
        public void addOnPreDrawListener(OnPreDrawListener v){draw=v;}
        public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener v){focus=v;}
        }''',
    'android/view/SurfaceView.java': 'package android.view; public class SurfaceView extends View {}',
    'android/view/TextureView.java': 'package android.view; public class TextureView extends View {}',
    'android/view/Window.java': '''package android.view;
        public class Window { public View decor; public View getDecorView(){return decor;} }''',
    'android/app/Activity.java': '''package android.app;
        public class Activity { public android.view.Window window=new android.view.Window();
        public android.view.Window getWindow(){return window;}
        public boolean focused=true; public boolean hasWindowFocus(){return focused;}
        public android.content.res.Resources getResources(){return new android.content.res.Resources();} }''',
    'cat/narezany/margyt/Margy.java': '''package cat.narezany.margyt;
        public class Margy { public static final String PREFS="test";
        public static android.content.Context context(){return null;} }''',
    'cat/narezany/margyt/Screen.java': '''package cat.narezany.margyt;
        public class Screen { public static android.app.Activity now(){return null;} }''',
    'cat/narezany/margyt/Diary.java': '''package cat.narezany.margyt;
        public class Diary { public static void note(String s){throw new AssertionError(s);} }''',
    'cat/narezany/margyt/BurnEye.java': '''package cat.narezany.margyt;
        class BurnEye { static void configurationChanged(){} static android.view.View eye;static boolean owns(android.view.View v){return v!=null && v==eye;}static boolean gone;static boolean missing(android.view.View v){return gone;}static int installs;static android.view.View install(android.view.View v){installs++;return eye;}static android.view.View installFeed(android.view.View v){return install(v);}static android.view.View installVideo(android.view.View v){return install(v);}static int inboxInstalls,hidden;static android.view.View installInbox(android.view.View v){inboxInstalls++;return install(v);}static void hide(android.view.View v){hidden++;} static void hideSoon(android.view.View v){}
        static boolean isFeedRail(android.view.View v,int depth){return true;}
        static boolean header=true;static boolean hasFeedHeader(android.view.View v){return header;} }''',
}

STUBS['cat/narezany/margyt/NavBar.java']='package cat.narezany.margyt;class NavBar{static boolean wears(android.view.View v){return false;}}'
STUBS['android/os/Build.java']='package android.os;public class Build{public static class VERSION{public static int SDK_INT=34;}}'
STUBS['cat/narezany/margyt/ProfileLayout.java']='package cat.narezany.margyt;class ProfileLayout {static boolean profile;static boolean visible(android.view.View root){return profile;}}'
STUBS['android/view/View.java']=STUBS['android/view/View.java'].replace('public boolean isAttachedToWindow(){return true;}', 'public boolean isAttachedToWindow(){return true;}public boolean hasWindowFocus(){return true;}')

from animation_doubles import STUBS as ANIMATION
STUBS.update(ANIMATION)
STUBS['cat/narezany/margyt/BurnEye.java']=STUBS['cat/narezany/margyt/BurnEye.java'].replace('static boolean header=true;', 'static boolean friendsHeader(android.view.View v){return false;}static boolean videoBackHeader(android.view.View v){return false;}static android.view.View installFriends(android.view.View v){return install(v);}static boolean header=true;')
HARNESS = '''package cat.narezany.margyt;
import android.view.*;
import java.lang.reflect.*;
public class DimHarness {
    static class PhotoViewPager extends ViewGroup {}
    static Method fade,restore,over;
    static void eq(float want,float got) {
        if(Math.abs(want-got)>0.0001f) throw new AssertionError(want+" != "+got);
    }
    static void fade(View v) throws Exception { fade.invoke(null,v,0.65f,0); }
    static void reset() throws Exception { restore.invoke(null); }
    public static void main(String[] args) throws Exception {
        fade=Dim.class.getDeclaredMethod("fadeChrome",View.class,float.class,int.class);
        restore=Dim.class.getDeclaredMethod("restore");
        over=Dim.class.getDeclaredMethod("over",View.class,View.class,float.class);
        fade.setAccessible(true);restore.setAccessible(true);over.setAccessible(true);
        // Start-up race: no context yet. The answer is "on" and the guess must not be remembered.
        Dim.reloadSettings();
        if(Dim.isConfigured())throw new AssertionError("Unknown settings must default off");
        Field remembered=Dim.class.getDeclaredField("on");remembered.setAccessible(true);
        if(remembered.get(null)!=null)throw new AssertionError("A guess made without a context must not be cached");
        if(Dim.strength()!=Dim.DEFAULT)throw new AssertionError("Default strength without a context");
        Dim.setEnabled(true);Dim.setStrength(35);
        // Parent then child: brightness and drift compose only once.
        ViewGroup parent=new ViewGroup();View child=new View();parent.addView(child);
        parent.alpha=0.8f;parent.x=7;parent.y=9;
        fade(parent);fade(parent);fade(child);
        eq(0.52f,parent.alpha);eq(1,child.alpha);eq(7,parent.x);eq(9,parent.y);
        eq(0,child.x);reset();eq(0.8f,parent.alpha);eq(7,parent.x);eq(9,parent.y);
        android.animation.ValueAnimator.manual=true;
        child.alpha=1;fade(child);reset();android.animation.ValueAnimator pending=android.animation.ValueAnimator.last;eq(.65f,child.alpha);pending.finish();eq(1,child.alpha);
        fade(child);reset();pending=android.animation.ValueAnimator.last;child.alpha=.2f;pending.finish();eq(.2f,child.alpha);
        child.alpha=1;fade(child);reset();pending=android.animation.ValueAnimator.last;fade(child);pending.finish();eq(.65f,child.alpha);android.animation.ValueAnimator.manual=false;reset();eq(1,child.alpha);
        parent.alpha=1;parent.x=0;parent.y=0;
        // Child discovered first, then parent: no compounded dimming/drift.
        fade(child);fade(parent);eq(1,parent.alpha);eq(0.65f,child.alpha);
        eq(0,parent.x);eq(0,child.x);reset();
        // Mixed container: actual player remains exactly at host alpha/position.
        ViewGroup mixed=new ViewGroup();SurfaceView video=new SurfaceView();
        View control=new View();mixed.addView(video);mixed.addView(control);
        fade(mixed);eq(1,mixed.alpha);eq(1,video.alpha);eq(0,video.x);
        eq(0.65f,control.alpha);eq(0,control.x);reset();
        // If TikTok animates one axis, restore the other independently.
        fade(child);child.x=50;reset();eq(50,child.x);eq(0,child.y);child.x=0;
        // Host alpha hook respects host's original alpha and restores it.
        fade(child);child.setAlpha(Dim.alphaFor(child,0.4f));
        eq(0.26f,child.alpha);reset();eq(0.4f,child.alpha);child.alpha=1;
        // The switch and the strength slider work again.
        fade(child);reset();eq(1,child.alpha);
        Dim.setEnabled(false);
        if(Dim.isEnabled()||Dim.isConfigured())throw new AssertionError("Switch turns protection off");
        Dim.setEnabled(true);
        if(!Dim.isEnabled()||!Dim.isConfigured())throw new AssertionError("Switch turns protection on");
        Dim.setStrength(50);if(Dim.strength()!=50)throw new AssertionError("Strength is kept");
        Dim.setStrength(35);if(Dim.strength()!=35)throw new AssertionError("Strength is kept");
        if(Dim.isShiftEnabled() || Dim.shiftDp()!=0)throw new AssertionError("Shift removed");
        Dim.setEnabled(true);
        // Rails can be nested much deeper than six levels. The caption must
        // not steal the bottom-navigation match. All controls, player excluded.
        ViewGroup root=new ViewGroup();root.width=1000;root.height=2000;
        ViewGroup nested=root;
        for(int i=0;i<9;i++){ViewGroup n=new ViewGroup();n.width=1000;n.height=2000;
            nested.addView(n);nested=n;}
        ViewGroup top=new ViewGroup();top.width=1000;top.height=100;top.top=70;
        top.addView(new View());top.addView(new View());nested.addView(top);
        ViewGroup caption=new ViewGroup();caption.width=1000;caption.height=100;caption.top=1730;
        caption.addView(new View());caption.addView(new View());nested.addView(caption);
        ViewGroup bottom=new ViewGroup();bottom.width=1000;bottom.height=100;bottom.top=1900;
        bottom.addView(new View());bottom.addView(new View());nested.addView(bottom);
        SurfaceView player=new SurfaceView();player.width=1000;player.height=1600;
        player.top=180;nested.addView(player);
        over.invoke(null,player,root,0.65f);
        eq(0.65f,top.alpha);eq(1,bottom.alpha);eq(0.65f,bottom.getChildAt(0).alpha);eq(0.65f,bottom.getChildAt(1).alpha);eq(0.65f,caption.alpha);
        eq(1,player.alpha);eq(0,player.x);
        if(!(bottom.background instanceof android.graphics.drawable.ColorDrawable))throw new AssertionError("Opaque backing covers adjacent preloaded video");
        reset();if(bottom.background!=null)throw new AssertionError("Native backing restored outside dim scope");
        android.widget.ImageView effect=new android.widget.ImageView();effect.setTag(0x54544C48,true);
        ViewGroup overlay=new ViewGroup();View sibling=new View();overlay.addView(effect);overlay.addView(sibling);
        fade(overlay);eq(1,effect.alpha);eq(1,overlay.alpha);eq(0.65f,sibling.alpha);reset();
        java.io.InputStream stream=DimHarness.class.getResourceAsStream("/X/ABCD.class");
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();int next;
        while((next=stream.read())!=-1)bytes.write(next);stream.close();byte[] code=bytes.toByteArray();
        byte[] from="X/ABCD".getBytes("UTF-8"),to="X/0HCj".getBytes("UTF-8");
        for(int i=0;i<=code.length-from.length;i++){boolean match=true;for(int j=0;j<from.length;j++)if(code[i+j]!=from[j])match=false;
            if(match)System.arraycopy(to,0,code,i,to.length);}
        class Loader extends ClassLoader {Class<?> load(byte[] data){return defineClass(null,data,0,data.length);}}
        ViewGroup nativeHeart=(ViewGroup)new Loader().load(code).newInstance();
        ViewGroup heartParent=new ViewGroup();heartParent.addView(nativeHeart);heartParent.addView(new View());
        fade(heartParent);eq(1,nativeHeart.alpha);eq(1,heartParent.alpha);reset();
        System.out.println("8 anti-burn runtime scenarios passed");
        // Apply to an actual photo-feed tree: inner pager, right-side actions,
        // captions, photo counter and a second photo all share the feed root.
        android.app.Activity activity=new com.ss.android.ugc.aweme.main.MainActivity();activity.window.decor=root;
        android.widget.ImageView photo=new android.widget.ImageView();
        photo.width=1000;photo.height=1300;photo.top=200;photo.alpha=0.9f;photo.x=8;photo.y=4;
        ViewGroup pager=new PhotoViewPager();pager.width=1000;pager.height=1600;
        pager.addView(photo);View dots=new View();pager.addView(dots);nested.addView(pager);
        android.widget.ImageView nextPhoto=new android.widget.ImageView();
        nextPhoto.width=1000;nextPhoto.height=1300;nextPhoto.top=200;
        pager.addView(nextPhoto);
        ViewGroup actions=new ViewGroup();actions.width=90;actions.height=500;
        android.widget.ImageView heart=new android.widget.ImageView();heart.width=48;heart.height=48;
        actions.addView(heart);nested.addView(actions);
        android.os.SystemClock.now+=1000;Dim.apply(activity);
        eq(0.9f,photo.alpha);eq(8,photo.x);eq(4,photo.y);eq(1,nextPhoto.alpha);
        eq(1,pager.alpha);eq(0,pager.x);eq(0.65f,actions.alpha);eq(0.65f,dots.alpha);
        eq(0.65f,top.alpha);eq(1,bottom.alpha);eq(0.65f,bottom.getChildAt(0).alpha);eq(0.65f,bottom.getChildAt(1).alpha);eq(0.65f,caption.alpha);
        eq(1,player.alpha);reset();
        // Unusual photo aspect ratios remain protected as well.
        android.widget.ImageView panorama=new android.widget.ImageView();
        panorama.width=1000;panorama.height=60;pager.addView(panorama);
        android.widget.ImageView portrait=new android.widget.ImageView();
        portrait.width=100;portrait.height=1000;pager.addView(portrait);
        android.os.SystemClock.now+=1000;Dim.apply(activity);
        eq(1,panorama.alpha);eq(0,panorama.x);eq(1,portrait.alpha);eq(0,portrait.x);reset();
        // Pre-draw repair catches a native property animator bypassing alphaFor.
        android.os.SystemClock.now+=1000;Dim.apply(activity);Dim.watch(activity);
        root.observer.draw.onPreDraw();
        actions.alpha=0.8f;root.observer.draw.onPreDraw();eq(0.52f,actions.alpha);
        int idleReads=android.content.res.Resources.reads;
        for(int frame=0;frame<60;frame++)root.observer.draw.onPreDraw();
        if(android.content.res.Resources.reads!=idleReads)throw new AssertionError("Stable frames must not rescan resource names");
        java.lang.reflect.Field passAt=Dim.class.getDeclaredField("last");passAt.setAccessible(true);long beforeStable=passAt.getLong(null);
        for(int frame=0;frame<60;frame++){android.os.SystemClock.now++;root.observer.layout.onGlobalLayout();root.observer.draw.onPreDraw();}
        if(passAt.getLong(null)!=beforeStable)throw new AssertionError("Unchanged layouts must not run full dim passes");
        root.observer.draw.onPreDraw();eq(0.52f,actions.alpha);reset();eq(0.8f,actions.alpha);
        actions.alpha=1;
        // An old icon resized/recycled into an image is protected before drawing.
        android.os.SystemClock.now+=1000;Dim.apply(activity);
        heart.width=1000;heart.height=1300;root.observer.draw.onPreDraw();
        eq(1,actions.alpha);eq(0,actions.x);eq(1,heart.alpha);reset();
        heart.width=48;heart.height=48;
        // New controls are protected before their FIRST frame, at the same
        // clock value as the previous pass: no delayed runnable is required.
        android.os.SystemClock.now+=1000;Dim.apply(activity);
        View late=new View();nested.addView(late);
        root.observer.layout.onGlobalLayout();root.observer.layout.onGlobalLayout();
        root.observer.draw.onPreDraw();eq(0.65f,late.alpha);
        reset();
        // Two roots updated in the same millisecond must both get processed.
        android.app.Activity second=new android.app.Activity();ViewGroup other=new ViewGroup();
        other.width=1000;other.height=2000;View otherButton=new View();other.addView(otherButton);
        second.window.decor=other;Dim.apply(second);eq(1,otherButton.alpha);reset();
        // Shift-only mode is preserved through pre-draw and focus restoration.
        Dim.setEnabled(true);android.os.SystemClock.now+=1000;Dim.apply(activity);
        float shiftedX=actions.x;root.observer.draw.onPreDraw();eq(shiftedX,actions.x);eq(0.65f,actions.alpha);
        activity.focused=false;android.os.SystemClock.now+=1000;Dim.apply(activity);
        eq(0.65f,actions.alpha);eq(0,actions.x);eq(0.9f,photo.alpha);reset();
        // Re-entering an already watched screen bypasses the throttle too.
        activity.focused=true;Dim.setEnabled(true);Dim.watch(activity);
        eq(0.65f,late.alpha);eq(0.9f,photo.alpha);reset();
        // Focus gain reapplies synchronously; focus loss preserves dimming.
        root.observer.focus.onWindowFocusChanged(true);eq(0.65f,late.alpha);
        activity.focused=false;root.observer.focus.onWindowFocusChanged(false);
        eq(0.65f,late.alpha);
        int pausedWrites=late.alphaWrites;
        for(int i=0;i<5;i++){android.os.SystemClock.now+=1000;Dim.apply(activity);root.observer.draw.onPreDraw();eq(0.65f,late.alpha);}
        if(late.alphaWrites!=pausedWrites || !Dim.isEnabled())throw new AssertionError("Background polling must not disable or flash dimming");
        activity.focused=true;root.observer.focus.onWindowFocusChanged(true);eq(0.65f,late.alpha);
        activity.focused=false;
        eq(0.65f,late.alpha);eq(0.9f,photo.alpha);eq(8,photo.x);reset();
        // An idle/live-photo pass must not reset or multiply the same controls.
        activity.focused=true;Dim.applyNow(activity);int writes=actions.alphaWrites;
        for(int i=0;i<10;i++){android.os.SystemClock.now+=1000;Dim.apply(activity);eq(0.65f,actions.alpha);}
        if(actions.alphaWrites!=writes)throw new AssertionError("Repeated passes flash alpha");
        root.layoutRequested=true;
        if(!root.observer.draw.onPreDraw())throw new AssertionError("Live-photo layout suppresses drawing");
        root.layoutRequested=false;
        // Comment sheets remain bright while the feed keeps its opacity.
        View commentRow=new View();commentRow.setTag(0x5454434D,true);nested.addView(commentRow);
        activity.focused=false;
        Dim.applyNow(activity);eq(0.65f,actions.alpha);eq(1,commentRow.alpha);eq(0.65f,top.alpha);
        activity.focused=true;
        commentRow.visibility=8;Dim.applyNow(activity);eq(0.65f,actions.alpha);
        android.widget.EditText commentInput=new android.widget.EditText();commentInput.hint="Добавить комментарий…";
        nested.addView(commentInput);Dim.applyNow(activity);eq(1,actions.alpha);eq(1,commentInput.alpha);
        commentInput.visibility=8;Dim.applyNow(activity);eq(0.65f,actions.alpha);
        ViewGroup sheet=new ViewGroup();sheet.width=1000;sheet.height=900;sheet.top=1000;
        android.widget.EditText sheetInput=new android.widget.EditText();sheetInput.hint="Добавить комментарий";
        View author=new View(),commentMenu=new View();sheet.addView(sheetInput);sheet.addView(author);sheet.addView(commentMenu);root.addView(sheet);
        Dim.applyNow(activity);eq(1,sheet.alpha);eq(1,author.alpha);eq(1,commentMenu.alpha);eq(1,actions.alpha);
        // Recycling hundreds of nested comment widgets must not alter the
        // feed fingerprint or force full theme/anti-burn passes while scrolling.
        for(int row=0;row<100;row++){ViewGroup cell=new ViewGroup();cell.setTag(0x5454434D,true);sheet.addView(cell);for(int n=0;n<12;n++)cell.addView(new View());}
        // the screen is read over at most every 48 ms: let that much pass, so
        // that what was just added is what the reading sees
        android.os.SystemClock.now+=100;
        Dim.applyNow(activity);root.observer.layout.onGlobalLayout();root.observer.draw.onPreDraw();
        int readStart=ViewGroup.childReads;Dim.layoutFingerprint(sheet,0);
        if(ViewGroup.childReads!=readStart)throw new AssertionError("A known comments sheet must be opaque to layout observers");
        long scrollPass=passAt.getLong(null);
        for(int frame=0;frame<60;frame++){
            ViewGroup cell=new ViewGroup();cell.setTag(0x5454434D,true);cell.addView(new View());sheet.addView(cell);
            android.os.SystemClock.now++;root.observer.layout.onGlobalLayout();root.observer.draw.onPreDraw();
        }
        if(passAt.getLong(null)!=scrollPass)throw new AssertionError("Recycled comments must not rerun full feed passes");
        eq(1,author.alpha);eq(1,commentMenu.alpha);
        sheet.visibility=8;Dim.applyNow(activity);eq(0.65f,actions.alpha);
        if(Dim.panelOpen(root))throw new AssertionError("Hidden comment views must not keep the panel open");
        sheet.removeAllViews();sheet.visibility=0;View reusedControl=new View();sheet.addView(reusedControl);
        Dim.applyNow(activity);eq(0.65f,reusedControl.alpha);
        if(Dim.panelOpen(root))throw new AssertionError("A recycled container loses its comment boundary when its anchor is removed");
        sheet.visibility=8;Dim.applyNow(activity);
        // A short composer bar (emoji row, attach/mention/voice buttons, send) around the comment input stays bright.
        ViewGroup composer=new ViewGroup();composer.width=1000;composer.height=150;composer.top=1800;
        android.widget.EditText composerInput=new android.widget.EditText();composerInput.hint="Добавить комментарий...";
        View emojiRow=new View(),sendButton=new View(),micButton=new View();
        composer.addView(emojiRow);composer.addView(composerInput);composer.addView(micButton);composer.addView(sendButton);root.addView(composer);
        Dim.applyNow(activity);eq(1,emojiRow.alpha);eq(1,sendButton.alpha);eq(1,micButton.alpha);eq(1,composerInput.alpha);eq(1,actions.alpha);
        composer.visibility=8;Dim.applyNow(activity);eq(0.65f,actions.alpha);
        // The idle input is often a plain text line, and its emoji row can sit outside the bar's own container.
        ViewGroup idleBar=new ViewGroup();idleBar.width=1000;idleBar.height=120;idleBar.top=1860;
        android.widget.TextView prompt=new android.widget.TextView();prompt.text="Добавить комментарий...";prompt.width=700;prompt.height=100;prompt.top=1870;
        View barSend=new View();barSend.top=1870;idleBar.addView(prompt);idleBar.addView(barSend);
        ViewGroup emojiStrip=new ViewGroup();emojiStrip.width=1000;emojiStrip.height=90;emojiStrip.top=1800;View emoji=new View();emoji.top=1800;emojiStrip.addView(emoji);
        View aboveZone=new View();aboveZone.top=900;
        root.addView(emojiStrip);root.addView(idleBar);root.addView(aboveZone);
        Dim.applyNow(activity);
        eq(1,barSend.alpha);eq(1,emojiStrip.alpha);eq(1,emoji.alpha);eq(1,prompt.alpha);eq(1,aboveZone.alpha);eq(1,actions.alpha);
        idleBar.visibility=8;Dim.applyNow(activity);eq(0.65f,emojiStrip.alpha);
        emojiStrip.visibility=8;aboveZone.visibility=8;Dim.applyNow(activity);
        android.widget.TextView nav=new android.widget.TextView();nav.text="Профиль";nav.selected=true;root.addView(nav);
        int hiddenBefore=BurnEye.hidden;Dim.applyNow(activity);eq(1,actions.alpha);
        if(BurnEye.hidden<=hiddenBefore)throw new AssertionError("Profile is a settled verdict: the eye leaves at once, not after a delay");
        nav.visibility=8;
        // Inbox: anti-burn works there and the eye is bound to the inbox header.
        android.widget.TextView inboxNav=new android.widget.TextView();inboxNav.text="Входящие";inboxNav.selected=true;root.addView(inboxNav);
        int inboxBefore=BurnEye.inboxInstalls;BurnEye.header=false;Dim.applyNow(activity);
        if(BurnEye.inboxInstalls!=inboxBefore)throw new AssertionError("Inbox must never install an eye");
        eq(1,actions.alpha);
        inboxNav.selected=false;Dim.applyNow(activity);eq(1,actions.alpha);
        inboxNav.visibility=8;BurnEye.header=true;
        // Friends can be recognised by the active bottom tab without For You text.
        BurnEye.header=false;android.widget.TextView friends=new android.widget.TextView();friends.text="Друзья";friends.selected=true;root.addView(friends);
        Dim.applyNow(activity);eq(0.65f,actions.alpha);friends.selected=false;Dim.applyNow(activity);eq(1,actions.alpha);
        BurnEye.header=true;android.widget.TextView following=new android.widget.TextView();following.text="Подписки";following.selected=true;root.addView(following);
        Dim.applyNow(activity);eq(0.65f,actions.alpha);following.selected=false;Dim.applyNow(activity);eq(0.65f,actions.alpha);following.visibility=8;reset();
        BurnEye.eye=new View();Dim.setEnabled(true);int beforeInstall=BurnEye.installs;Dim.applyNow(activity);eq(1,BurnEye.eye.alpha);
        if(BurnEye.installs!=beforeInstall+1)throw new AssertionError("Install eye only once on root; narrow rail must not hide it");
        int eyeInstalls=BurnEye.installs;Dim.setEnabled(true);Dim.applyNow(activity);eq(1,BurnEye.eye.alpha);
        if(!Dim.isConfigured() || !Dim.isEnabled())throw new AssertionError("Anti burn remains enabled regardless of legacy preference");
        Dim.toggleOpacity();if(!Dim.isConfigured() || Dim.isEnabled())throw new AssertionError("Eye tap pauses the fade; the saved switch stays on");
        Dim.toggleOpacity();Dim.applyNow(activity);if(!Dim.isConfigured() || !Dim.isEnabled())throw new AssertionError("Second tap resumes the fade");eq(1,BurnEye.eye.alpha);
        // The eye left on a verdict taken mid-transition and the layout did not change afterwards:
        // the one-second tick must look again instead of leaving anti-burn off until a restart.
        activity.focused=true;BurnEye.gone=true;int beforeTick=BurnEye.installs;
        for(Runnable tick:new java.util.ArrayList<Runnable>(root.delayed))tick.run();
        if(BurnEye.installs<=beforeTick)throw new AssertionError("A missing eye must be looked for again");
        BurnEye.gone=false;
        // Back from a profile: its verdict used to switch the retry off for good and the layout is the same
        // as before, so nothing else would ever bring the eye (and anti-burn) back.
        ProfileLayout.profile=true;Dim.applyNow(activity);
        BurnEye.gone=true;int whileProfile=BurnEye.installs;
        for(Runnable tick:new java.util.ArrayList<Runnable>(root.delayed))tick.run();
        if(BurnEye.installs!=whileProfile)throw new AssertionError("The eye must stay away while the profile is on screen");
        ProfileLayout.profile=false;int afterProfile=BurnEye.installs;
        for(Runnable tick:new java.util.ArrayList<Runnable>(root.delayed))tick.run();
        if(BurnEye.installs<=afterProfile)throw new AssertionError("The eye must come back after a profile, not stay off until restart");
        BurnEye.gone=false;
        BurnEye.eye=null;reset();
        // Backing ownership follows native background replacements and recycled rails.
        View standalone=new View();android.graphics.drawable.Drawable nativeBg=new android.graphics.drawable.Drawable();standalone.background=nativeBg;
        BottomChrome.protect(standalone);android.graphics.drawable.Drawable backed=standalone.background;BottomChrome.protect(standalone);
        if(standalone.background!=backed)throw new AssertionError("Backing must not be stacked every pass");
        backed.setAlpha(30);BottomChrome.repair();if(backed.getAlpha()!=255)throw new AssertionError("A native background fade must not expose preloaded content");if(nativeBg.getAlpha()!=30)throw new AssertionError("Native overlay opacity must be preserved above the opaque shield");
        ViewGroup navHost=new ViewGroup();navHost.addView(standalone);fade(navHost);eq(1,navHost.alpha);eq(1,standalone.alpha);
        if(!BottomChrome.containsRail(navHost))throw new AssertionError("Backing protects all its ancestor containers");
        android.graphics.drawable.Drawable changedBg=new android.graphics.drawable.Drawable();standalone.background=changedBg;BottomChrome.protect(standalone);BottomChrome.restore();
        if(standalone.background!=changedBg)throw new AssertionError("Latest native style must survive disable");
        BottomChrome.protect(standalone);View replacement=new View();BottomChrome.protect(replacement);
        if(standalone.background!=changedBg)throw new AssertionError("Previous rail backing must be restored");
        android.graphics.drawable.Drawable foreign=new android.graphics.drawable.Drawable();replacement.background=foreign;BottomChrome.restore();
        if(replacement.background!=foreign)throw new AssertionError("Native updates must not be overwritten on cleanup");
        // Search-video chrome is allowed, search-result preview cards are not.
        BurnEye.header=false;android.app.Activity search=new android.app.Activity();ViewGroup searchRoot=new ViewGroup();searchRoot.width=1000;searchRoot.height=2000;
        android.widget.EditText query=new android.widget.EditText();query.width=1000;query.height=100;query.top=60;
        SurfaceView searchPlayer=new SurfaceView();searchPlayer.width=1000;searchPlayer.height=1500;searchPlayer.top=200;
        View searchButton=new View();searchRoot.addView(query);searchRoot.addView(searchPlayer);searchRoot.addView(searchButton);search.window.decor=searchRoot;
        Dim.applyNow(search);eq(0.65f,query.alpha);eq(0.65f,searchButton.alpha);eq(1,searchPlayer.alpha);
        searchPlayer.height=200;searchPlayer.top=900;Dim.applyNow(search);eq(1,query.alpha);eq(1,searchButton.alpha);reset();
        ProfileLayout.profile=true;Dim.applyNow(activity);eq(1,actions.alpha);ProfileLayout.profile=false;
        android.widget.EditText message=new android.widget.EditText();message.hint="Написать...";root.addView(message);Dim.applyNow(activity);eq(1,actions.alpha);eq(1,message.alpha);message.visibility=8;reset();
        System.out.println("7 slideshow and refresh scenarios passed");
        System.out.println("First-frame entry, layout and focus scenarios passed");
        Dim.setEnabled(true);BurnEye.header=false;ProfileLayout.profile=false;
        com.ss.android.ugc.aweme.detail.ui.DetailActivity detail=new com.ss.android.ugc.aweme.detail.ui.DetailActivity();
        ViewGroup detailRoot=new ViewGroup();detailRoot.width=1000;detailRoot.height=2000;detail.window.decor=detailRoot;
        SurfaceView detailVideo=new SurfaceView();detailVideo.width=1000;detailVideo.height=900;detailVideo.top=500;
        View detailButton=new View();detailButton.top=800;
        android.widget.EditText permanentInput=new android.widget.EditText();permanentInput.hint="Добавить комментарий...";permanentInput.top=1850;permanentInput.height=100;
        detailRoot.addView(detailVideo);detailRoot.addView(detailButton);detailRoot.addView(permanentInput);
        Dim.applyNow(detail);eq(.65f,detailButton.alpha);eq(1,detailVideo.alpha);eq(1,permanentInput.alpha);
        if(Dim.panelOpen(detailRoot))throw new AssertionError("A permanent search-video composer is not an open comment sheet");
        System.out.println("Detail screen scenarios passed");
    }
}'''


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
class DimRuntimeTest(unittest.TestCase):
    def test_overlay_composition_restore_and_deep_navigation(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = dict(STUBS)
            sources['cat/narezany/margyt/Dim.java'] = (
                ROOT / 'inject/java/cat/narezany/margyt/Dim.java').read_text(encoding='utf-8')
            for name in ('BottomChrome',):
                sources['cat/narezany/margyt/' + name + '.java'] = (
                    ROOT / ('inject/java/cat/narezany/margyt/' + name + '.java')).read_text(encoding='utf-8')
            sources['cat/narezany/margyt/DimHarness.java'] = HARNESS
            __import__("android_doubles").with_mod(sources)
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            compiled = subprocess.run(['javac', '-source', '8', '-target', '8',
                                      '-encoding', 'UTF-8', '-d', str(work)] +
                                      [str(work / name) for name in sources],
                                      capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            result = subprocess.run(['java', '-cp', str(work), 'cat.narezany.margyt.DimHarness'],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('8 anti-burn runtime scenarios passed', result.stdout)
            self.assertIn('7 slideshow and refresh scenarios passed', result.stdout)
            self.assertIn('First-frame entry, layout and focus scenarios passed', result.stdout)
            self.assertIn('Detail screen scenarios passed', result.stdout)
