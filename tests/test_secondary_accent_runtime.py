"""The cyan, the online green and span colours follow the accent; both loading dots too."""
import pathlib, shutil, unittest
import test_settings_safety_runtime as safety
method = safety.method
ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / 'inject/java/cat/narezany/tokkiyou'

COLOR = '''package android.graphics;public class Color {
 public static int alpha(int c){return c>>>24;}
 public static void colorToHSV(int c,float[] o){float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f;
  float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,h=0;
  if(d!=0){if(max==r)h=((g-b)/d)%6;else if(max==g)h=(b-r)/d+2;else h=(r-g)/d+4;h*=60;if(h<0)h+=360;}
  o[0]=h;o[1]=max==0?0:d/max;o[2]=max;}
 public static int HSVToColor(int a,float[] h){float c=h[2]*h[1],x=c*(1-Math.abs((h[0]/60f)%2-1)),m=h[2]-c,r,g,b;int k=(int)(h[0]/60f)%6;
  if(k==0){r=c;g=x;b=0;}else if(k==1){r=x;g=c;b=0;}else if(k==2){r=0;g=c;b=x;}else if(k==3){r=0;g=x;b=c;}else if(k==4){r=x;g=0;b=c;}else{r=c;g=0;b=x;}
  return (a<<24)|(Math.round((r+m)*255)<<16)|(Math.round((g+m)*255)<<8)|Math.round((b+m)*255);}}'''


class SecondaryAccentTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_secondary_zones_measured_from_the_screens(self):
        palette = (JAVA / 'Palette.java').read_text(encoding='utf-8')
        sources = {
            'android/graphics/Color.java': COLOR,
            'cat/narezany/tokkiyou/Palette.java': palette,
            'Test.java': '''import cat.narezany.tokkiyou.Palette;
public class Test {
 static void yes(int c,String m){if(!Palette.secondary(c))throw new AssertionError(m);}
 static void no(int c,String m){if(Palette.secondary(c))throw new AssertionError(m);}
 public static void main(String[] a){
  yes(0xFF0FC1E5,"story plus");yes(0xFF20D5EC,"cyan");yes(0xFF25F4EE,"brand cyan");yes(0xFF00C5CD,"cyan 2");
  yes(0x330FC1E5,"cyan keeps working at low alpha (selected tab fill)");
  yes(0xFF1CD764,"online dot");yes(0xFF1BD864,"online dot, jpeg noise");
  no(0xFFFE2C55,"pink is the red family's, not this one");no(0xFFFE3B75,"hot pink");
  no(0xFF1E90FF,"plain blue");no(0xFF808080,"grey");no(0xFF000000,"black");no(0xFFFFFFFF,"white");
  no(0xFF146C2E,"dark green accent from the palette");no(0xFF006A6A,"dark teal accent from the palette");
  no(0xFF6750A4,"purple accent");no(0xFF0B57D0,"blue accent");
  if(Palette.toAccent(0x330FC1E5,0xFF6750A4)!=0x336750A4)throw new AssertionError("alpha kept");
  if(Palette.toAccent(0xFF1CD764,0xFF6750A4)!=0xFF6750A4)throw new AssertionError("opaque");
 }}'''}
        safety.SafetyRuntimeTest().run_java(sources)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_sourced_moves_cyan_and_green_only_for_a_chosen_accent(self):
        body = method('Accent.java', '    private static int sourced(')
        sources = {
            'android/graphics/Color.java': COLOR,
            'cat/narezany/tokkiyou/Palette.java': (JAVA / 'Palette.java').read_text(encoding='utf-8'),
            'Test.java': '''import cat.narezany.tokkiyou.Palette;
public class Test {
 static int TIKTOK=0xFFFE2C55,chosen=0xFF6750A4;
 static int colour(){return chosen;} static int accent(){return chosen;}
 static int swap(int c){return (c&0xFFFFFF)==0xFE2C55?(c&0xFF000000)|(chosen&0xFFFFFF):c;}
 static class Themes{static int recolour(int c,boolean b){return c;}}
 '''+body+'''
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a){
  check(sourced(0xFF0FC1E5)==chosen,"story plus -> accent");
  check(sourced(0xFF1CD764)==chosen,"online dot -> accent");
  check(sourced(0x2920D5EC)==0x296750A4,"cyan tab fill keeps alpha");
  check(sourced(0xFF25F4EE)==chosen,"loading dot 1 (cyan)");
  check(sourced(0xFFFE2C55)==chosen,"loading dot 2 (pink)");
  check(sourced(0xFF808080)==0xFF808080,"grey untouched");
  chosen=TIKTOK;
  check(sourced(0xFF0FC1E5)==0xFF0FC1E5,"nothing moves while the accent is TikTok's own");
  check(sourced(0xFF25F4EE)==TIKTOK,"the exact cyans behave as they did in 0.3.5");
 }}'''}
        safety.SafetyRuntimeTest().run_java(sources)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_span_colours_follow_the_accent_and_restore(self):
        spans = method('Accent.java', '    private static void paintSpans(')
        owned = method('Accent.java', '    private static final class OwnedText')
        sources = {
            'android/text/Spanned.java': 'package android.text;public interface Spanned extends CharSequence{<T> T[] getSpans(int s,int e,Class<T> k);int getSpanStart(Object o);int getSpanEnd(Object o);int getSpanFlags(Object o);}',
            'android/text/style/ForegroundColorSpan.java': 'package android.text.style;public class ForegroundColorSpan{int c;public ForegroundColorSpan(int c){this.c=c;}public int getForegroundColor(){return c;}}',
            'android/text/SpannableStringBuilder.java': '''package android.text;import java.util.*;
public class SpannableStringBuilder implements Spanned{String t;List<Object[]> sp=new ArrayList<>();
 public SpannableStringBuilder(CharSequence c){t=c.toString();if(c instanceof Spanned){Spanned s=(Spanned)c;for(Object o:s.getSpans(0,s.length(),Object.class))sp.add(new Object[]{o,s.getSpanStart(o),s.getSpanEnd(o),s.getSpanFlags(o)});}}
 public int length(){return t.length();}public char charAt(int i){return t.charAt(i);}public CharSequence subSequence(int a,int b){return t.substring(a,b);}public String toString(){return t;}
 public void removeSpan(Object o){sp.removeIf(x->x[0]==o);}
 public void setSpan(Object o,int s,int e,int f){sp.add(new Object[]{o,s,e,f});}
 @SuppressWarnings("unchecked")public <T> T[] getSpans(int s,int e,Class<T> k){List<Object> o=new ArrayList<>();for(Object[] x:sp)if(k.isInstance(x[0]))o.add(x[0]);return o.toArray((T[])java.lang.reflect.Array.newInstance(k,0));}
 int f(Object o,int i){for(Object[] x:sp)if(x[0]==o)return (Integer)x[i];return -1;}
 public int getSpanStart(Object o){return f(o,1);}public int getSpanEnd(Object o){return f(o,2);}public int getSpanFlags(Object o){return f(o,3);}}''',
            'android/widget/TextView.java': 'package android.widget;public class TextView{CharSequence text="";java.util.Map<Integer,Object> tags=new java.util.HashMap<>();public CharSequence getText(){return text;}public void setText(CharSequence c){text=new android.text.SpannableStringBuilder(c);}public Object getTag(int k){return tags.get(k);}public void setTag(int k,Object v){tags.put(k,v);}}',
            'android/widget/EditText.java': 'package android.widget;public class EditText extends TextView{}',
            'Test.java': '''import android.widget.*;import android.text.*;import android.text.style.ForegroundColorSpan;
public class Test {
 static int chosen=0xFF6750A4;static int sourced(int c){return (c&0xFFFFFF)==0xFE2C55?(c&0xFF000000)|(chosen&0xFFFFFF):c;}
 '''+owned+'''
 static final int SPAN_KEY=0x54545350;
 '''+spans+'''
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static int colourOf(TextView t){return ((Spanned)t.getText()).getSpans(0,t.getText().length(),ForegroundColorSpan.class)[0].getForegroundColor();}
 public static void main(String[] a){
  SpannableStringBuilder s=new SpannableStringBuilder("Уже есть аккаунт? Войти");s.setSpan(new ForegroundColorSpan(0xFFFE2C55),17,23,33);
  TextView t=new TextView();t.setText(s);
  paintSpans(t);check(colourOf(t)==chosen,"span moved onto the accent");
  check(t.getText().toString().equals("Уже есть аккаунт? Войти"),"text itself unchanged");
  Spanned out=(Spanned)t.getText();check(out.getSpanStart(out.getSpans(0,23,ForegroundColorSpan.class)[0])==17,"range kept");
  paintSpans(t);check(colourOf(t)==chosen,"second pass changes nothing");
  chosen=0xFF006A6A;paintSpans(t);check(colourOf(t)==chosen,"a new accent starts from the original, not the copy");
  chosen=0xFFFE2C55;paintSpans(t);check(colourOf(t)==0xFFFE2C55,"TikTok's own accent restores");
  EditText e=new EditText();e.setText(s);chosen=0xFF6750A4;paintSpans(e);check(e.getTag(SPAN_KEY)==null,"input fields are left alone");
 }}'''}
        safety.SafetyRuntimeTest().run_java(sources)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_native_loading_dots_keep_the_two_colour_animation(self):
        accent = (JAVA / 'Accent.java').read_text(encoding='utf-8')
        colours = (JAVA / 'AppearanceColors.java').read_text(encoding='utf-8')
        tint = method('Accent.java', '    static int loadingDotTint(')
        self.assertIn('AppearanceColors.loadingDots(view)?loadingDotTint(colour):sourced(colour)', accent)
        self.assertIn('return Palette.map(colour,TIKTOK,colour());', tint)
        self.assertIn('static boolean loadingDots(View view)', colours)
        self.assertIn('if (!animatedDots && view instanceof ImageView)', colours)
        self.assertIn('if (!animatedDots && name != null && !mediaName(name))', colours)
        sources = {
            'android/graphics/Color.java': COLOR,
            'cat/narezany/tokkiyou/Palette.java': (JAVA / 'Palette.java').read_text(encoding='utf-8'),
            'Test.java': '''import cat.narezany.tokkiyou.Palette;
public class Test {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a){int pink=0xFFFE2C55,cyan=0xFF25F4EE,accent=0xFF6750A4;
  check(Palette.map(pink,pink,accent)==accent,"pink animated dot follows the chosen accent");
  check(Palette.map(cyan,pink,accent)==cyan,"cyan animated dot remains native cyan");
  check(Palette.map(0x80FE2C55,pink,accent)==0x806750A4,"animated dot preserves alpha");
 }}'''}
        safety.SafetyRuntimeTest().run_java(sources)
