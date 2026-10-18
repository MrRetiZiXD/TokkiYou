package cat.narezany.margyt;
import android.view.View;import android.view.ViewGroup;import android.widget.TextView;
/**
 * What a cell of the share sheet is made of, said to logcat by a test build.
 *
 * The last row of the sheet comes out with dark icons and no labels under a
 * theme, and nothing in the code says why. This writes down each view of the
 * download cell as it is at the moment the sheet opens -- its class, its
 * colour, its tint, its background -- so the cause can be read off a phone.
 */
final class ShareProbe {
 private ShareProbe(){}
 /** A test build says what the download cell of the share sheet is made of and what colour each part is. */
 private static int told;
 /** Called for every view the theme walks. The first few cells of the sheet's last row are written down. */
 static void look(View view){
  if(told>=3||!(view instanceof TextView))return;
  CharSequence said=((TextView)view).getText();if(said==null||said.length()<4||said.length()>14)return;
  String text=said.toString();
  if(!(text.equals("Жалоба")||text.equals("Скачать")||text.equals("Неинтересно")||text.equals("Report")||text.equals("Download")))return;
  told++;
  android.util.Log.e("TokkiYou","share cell: theme dark "+Themes.isDark()+" text "+Integer.toHexString(Themes.text())+" ground "+Integer.toHexString(Themes.backgroundInUse()));
  describe(view.getParent() instanceof View?(View)view.getParent():view,0);
 }
 private static String filterColour(android.graphics.ColorFilter filter){
  try{java.lang.reflect.Method colour=filter.getClass().getMethod("getColor");return " "+Integer.toHexString((Integer)colour.invoke(filter));}catch(Throwable hidden){return "";}
 }
 static void describe(View view,int depth){
  if(view==null||depth>4)return;
  StringBuilder out=new StringBuilder("share cell: ");for(int i=0;i<depth;i++)out.append("  ");
  out.append(view.getClass().getName()).append(" alpha ").append(view.getAlpha()).append(" dark ").append(Themes.isDark());
  if(view instanceof TextView){TextView text=(TextView)view;out.append(" text '").append(text.getText()).append("' colour ").append(Integer.toHexString(text.getCurrentTextColor()))
    .append(" owned ").append(Nightly.owns(text.getCurrentTextColor())).append(" was ").append(Accent.sourceOf(text)).append(" paint alpha ").append(text.getPaint().getAlpha());}
  if(view instanceof android.widget.ImageView){android.widget.ImageView image=(android.widget.ImageView)view;
   out.append(" drawable ").append(image.getDrawable()==null?"none":image.getDrawable().getClass().getName())
    .append(" tint ").append(image.getImageTintList()==null?"none":Integer.toHexString(image.getImageTintList().getDefaultColor()))
    .append(" filter ").append(image.getColorFilter()==null?"none":image.getColorFilter().getClass().getSimpleName()+filterColour(image.getColorFilter()))
    .append(" image alpha ").append(image.getImageAlpha());}
  android.graphics.drawable.Drawable back=view.getBackground();
  if(back!=null){out.append(" background ").append(back.getClass().getName());
   if(back instanceof android.graphics.drawable.ColorDrawable)out.append(' ').append(Integer.toHexString(((android.graphics.drawable.ColorDrawable)back).getColor()));
   if(back instanceof android.graphics.drawable.GradientDrawable&&android.os.Build.VERSION.SDK_INT>=24&&((android.graphics.drawable.GradientDrawable)back).getColor()!=null)out.append(' ').append(Integer.toHexString(((android.graphics.drawable.GradientDrawable)back).getColor().getDefaultColor()));}
  android.util.Log.e("TokkiYou",out.toString());
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)describe(group.getChildAt(i),depth+1);}
 }
}
