package cat.narezany.tokkiyou;
import android.view.View;import java.util.*;import java.lang.ref.WeakReference;
/** Adjust only native profile info padding, not names, badges, chat or video. */
public final class ProfileLayout {
 static final String KEY="profile_layout";private static final int WATCHED=0x7e0c0336;private static final Map<View,int[]> originals=new WeakHashMap<>();private static final Map<View,int[]> margins=new WeakHashMap<>();
 static int mode(){try{return Math.max(0,Math.min(2,AccountAppearance.prefs(TokkiYou.context()).getInt(KEY,0)));}catch(Throwable ignored){return 0;}}
 public static void bind(Object component,View view){if(component==null||view==null)return;String name=component.getClass().getName();if(!name.equals("com.ss.android.ugc.profile.platform.business.header.business.info.assemble.ProfileHeaderInfoComponent")&&!name.equals("com.ss.android.ugc.profile.platform.business.header.business.info.assemble.ProfileHeaderInfoComponentV2")&&!name.equals("com.ss.android.ugc.profile.platform.business.header.business.info.business.basicinfo.container.ProfileHeaderUserBasicInfoComponent"))return;synchronized(originals){if(!originals.containsKey(view))originals.put(view,new int[]{view.getPaddingLeft(),view.getPaddingTop(),view.getPaddingRight(),view.getPaddingBottom()});}if(!Boolean.TRUE.equals(view.getTag(WATCHED))){view.setTag(WATCHED,true);view.addOnLayoutChangeListener((v,l,t,rr,b,ol,ot,or,ob)->apply(v));}apply(view);}
 static boolean onScreen(View view,View root){
  if(view==null||!view.isShown()||!view.isAttachedToWindow()||view.getRootView()!=root)return false;
  View parent=view;for(int i=0;i<40&&parent!=null;i++){if(parent.getAlpha()<=0.01f)return false;Object next=parent.getParent();parent=next instanceof View?(View)next:null;}
  android.graphics.Rect r=new android.graphics.Rect(),window=new android.graphics.Rect();
  return view.getGlobalVisibleRect(r)&&root.getGlobalVisibleRect(window)&&r.right>window.left&&r.left<window.right&&r.bottom>window.top&&r.top<window.bottom&&r.width()>1&&r.height()>1;
 }
 static boolean visible(View root){synchronized(originals){for(View view:originals.keySet())if(onScreen(view,root))return true;}return false;}
 private static void apply(View view){int[] old;synchronized(originals){old=originals.get(view);}if(old==null)return;int mode=mode(),space=(int)(view.getResources().getDisplayMetrics().density*(mode==1?4:20)+.5f);int top=mode==0?old[1]:space,bottom=mode==0?old[3]:space;if(view.getPaddingTop()!=top||view.getPaddingBottom()!=bottom)view.setPadding(old[0],top,old[2],bottom);
  if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<Math.min(16,group.getChildCount());i++){View child=group.getChildAt(i);android.view.ViewGroup.LayoutParams params=child.getLayoutParams();if(!(params instanceof android.view.ViewGroup.MarginLayoutParams))continue;android.view.ViewGroup.MarginLayoutParams layout=(android.view.ViewGroup.MarginLayoutParams)params;int[] base;synchronized(margins){base=margins.get(child);if(base==null){base=new int[]{layout.topMargin,layout.bottomMargin};margins.put(child,base);}}int gap=(int)(child.getResources().getDisplayMetrics().density*(mode==1?2:12)+.5f);int t=mode==0?base[0]:mode==1?gap:Math.max(base[0],gap),b=mode==0?base[1]:mode==1?gap:Math.max(base[1],gap);if(layout.topMargin!=t||layout.bottomMargin!=b){layout.topMargin=t;layout.bottomMargin=b;child.setLayoutParams(layout);}}}}
 static void setMode(int mode){AccountAppearance.prefs(TokkiYou.context()).edit().putInt(KEY,Math.max(0,Math.min(2,mode))).apply();reload();}
 static void reload(){List<View> values;synchronized(originals){values=new ArrayList<>(originals.keySet());}for(View view:values)if(view!=null)view.post(()->apply(view));}
}
