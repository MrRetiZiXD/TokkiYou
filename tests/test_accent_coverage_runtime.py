"""Execute accent shape/state handling and verify new typed smali hooks."""
import unittest
import test_settings_safety_runtime as safety
method=safety.method
from margyt import dexpatch
class AccentCoverageTest(unittest.TestCase):
 def test_shapes_and_state_lists(self):
  body=''.join(method('Accent.java',m) for m in ('    private static Drawable movedShape','    private static ColorStateList moved(ColorStateList','    private static ColorStateList moveColours','    private static Object call','    private static ColorStateList repaintedColours'))
  sources={
   'android/os/Build.java':'package android.os;public class Build {public static class VERSION {public static int SDK_INT=35;}}',
   'android/content/res/ColorStateList.java':'''package android.content.res;public class ColorStateList {public int[][] states;public int[] colours;public boolean inaccessible;public ColorStateList(int[][] s,int[] c){states=s;colours=c;}public static ColorStateList valueOf(int c){return new ColorStateList(new int[][]{{}},new int[]{c});}public int getDefaultColor(){return colours[colours.length-1];}public boolean isStateful(){return colours.length>1;}public int[] getColors(){if(inaccessible)throw new IllegalStateException();return colours;}public int[][] getStates(){if(inaccessible)throw new IllegalStateException();return states;}}''',
   'android/graphics/drawable/Drawable.java':'''package android.graphics.drawable;public class Drawable {public static abstract class ConstantState{public abstract Drawable newDrawable();}public Drawable mutate(){return this;}}''',
   'android/graphics/drawable/GradientDrawable.java':'''package android.graphics.drawable;import android.content.res.ColorStateList;public class GradientDrawable extends Drawable {public ColorStateList fill;public int[] stops;public int radius=24;public boolean copyable=true;public ColorStateList getColor(){return fill;}public int[] getColors(){return stops;}public void setColor(ColorStateList c){fill=c;stops=null;}public void setColors(int[] c){stops=c;fill=null;}public ConstantState getConstantState(){if(!copyable)return null;final GradientDrawable original=this;return new ConstantState(){public Drawable newDrawable(){GradientDrawable d=new GradientDrawable();d.radius=original.radius;d.fill=original.fill;d.stops=original.stops;return d;}};}}''',
   'Test.java':'''import android.graphics.drawable.*;import android.content.res.ColorStateList;
public class Test {
 static int target=0xFF006A6A,TIKTOK=0xFFFE2C55;static int colour(){return target;}static int sourced(int c){return (c&0xFFFFFF)==0xFE2C55?(c&0xFF000000)|(target&0xFFFFFF):c;}
 static class OwnedColours {final ColorStateList original;final int accent;OwnedColours(ColorStateList s,int c){original=s;accent=c;}}
 static java.util.Map<ColorStateList,OwnedColours> ownedColours=new java.util.WeakHashMap<>();
 static java.util.Map<ColorStateList,Integer> unchangedColours=new java.util.WeakHashMap<>();
 '''+body+'''
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  GradientDrawable shape=new GradientDrawable();shape.stops=new int[]{TIKTOK,0x66FE2C55,0xFF00FFFF};
  GradientDrawable mapped=(GradientDrawable)movedShape(shape);check(mapped!=shape && mapped.radius==24,"gradient copied with original geometry");check(mapped.stops[0]==target && mapped.stops[1]==0x66006A6A && mapped.stops[2]==0xFF00FFFF,"gradient maps accent only and preserves alpha");check(shape.stops[0]==TIKTOK,"shared source stays unchanged");
  shape.copyable=false;check(movedShape(shape)==shape,"uncopyable source never mutated");
  ColorStateList state=new ColorStateList(new int[][]{{-1},{}},new int[]{0x66FE2C55,TIKTOK});shape=new GradientDrawable();shape.fill=state;
  mapped=(GradientDrawable)movedShape(shape);check(mapped.fill.colours[0]==0x66006A6A && mapped.fill.states[0][0]==-1,"solid stateful fill retains disabled state");
  ColorStateList active=repaintedColours(state);check(repaintedColours(active)==active,"same accent reuses translated list");target=0xFF6750A4;ColorStateList changed=repaintedColours(active);check(changed.colours[1]==target,"account change remaps original colour");target=TIKTOK;check(repaintedColours(changed)==state,"default accent restores original list");
  ColorStateList neutral=ColorStateList.valueOf(0xFFFFFFFF);check(repaintedColours(neutral)==neutral && unchangedColours.containsKey(neutral),"unchanged colours cached without repeated reflection");
  target=0xFF006A6A;state.inaccessible=true;check(moved(state)==state,"inaccessible state list never flattened");
  android.os.Build.VERSION.SDK_INT=23;check(movedShape(shape)==shape,"unsupported getter remains guarded");
 }
}'''}
  safety.SafetyRuntimeTest().run_java(sources)
 def test_typed_setters_have_matching_hooks(self):
  calls=[x for x in dexpatch.COLOUR_SOURCES if x[1] in ('setStroke','setBackgroundTintList','setTintList') or (x[1] in ('setColor','setTextColor','setHintTextColor') and 'ColorStateList;' in x[2])]
  self.assertEqual(10,len(calls))
  rules=dexpatch.accent_rules()
  for owner,name,sig,replacement in calls:
   text='invoke-virtual {v0, v1}, '+owner+'->'+name+sig
   for _,pattern,target in rules:text=pattern.sub(target,text)
   self.assertIn('Accent;->'+name+replacement,text)

 def test_native_icon_tint_preserves_large_heart_and_neutral_icons(self):
  body=method('Accent.java','    public static void setTintColor(')
  sources={
   'Test.java': 'class TuxIconView {Object tag;int tint;Object getTag(int id){return tag;}void setTintColor(int c){tint=c;}}class Heart {static void setTintColor(TuxIconView v,int c){v.tint=123;}}class AppearanceColors {static boolean loadingDots(Object v){return false;}}public class Test {static int sourced(int c){return c==55?66:c;}static int loadingDotTint(int c){return sourced(c);}static java.util.Map<TuxIconView,Integer> tintSources=new java.util.WeakHashMap<>();static int sourceColour(int c){return c;}'+body+' public static void main(String[] a){TuxIconView v=new TuxIconView();setTintColor(v,55);if(v.tint!=66)throw new AssertionError();setTintColor(v,99);if(v.tint!=99)throw new AssertionError();v.tag=Boolean.TRUE;setTintColor(v,55);if(v.tint!=123)throw new AssertionError();setTintColor(null,55);}}'
  }
  safety.SafetyRuntimeTest().run_java(sources)

