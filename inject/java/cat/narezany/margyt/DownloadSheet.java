package cat.narezany.margyt;
import android.content.Context;import android.graphics.Typeface;import android.util.TypedValue;import android.view.Gravity;import android.view.View;import android.widget.LinearLayout;import android.widget.TextView;import java.util.List;
/**
 * Download chooser, kept short so it does not fill the screen.
 * A video post: the tallest size stands alone, filled with the accent and marked as the best; the other
 * sizes follow as one segmented group, the shortest marked as the worst -- both read from what this post
 * really offers. A photo post: one "all photos" button and a grid of numbered chips, one per photo.
 * The soundtrack is always the last row.
 */
final class DownloadSheet {
 interface Pick {void on(DownloadQuality.Choice choice);}
 private DownloadSheet(){}
 static void show(Context c,List<DownloadQuality.Choice> videos,List<DownloadQuality.Choice> photos,boolean nativeOnly,final Panel.Click nativeSave,final Pick video,final Pick photo,final Panel.Click audio,final Panel.Click allPhotos){
  final Skin skin=Skin.remembered(c);
  final boolean photoPost=photos!=null&&!photos.isEmpty();
  final Panel panel=Panel.with(c,skin,photoPost?Text.SHEET_POST_TITLE:Text.SHEET_PICK_QUALITY).compact();
  DownloadQuality.Choice best=null;java.util.ArrayList<DownloadQuality.Choice> rest=new java.util.ArrayList<>();
  if(videos!=null)for(DownloadQuality.Choice v:videos){if(v.best&&best==null)best=v;else rest.add(v);}
  // the "best" entry is the tallest size under another name: do not list that size twice
  java.util.ArrayList<DownloadQuality.Choice> list=new java.util.ArrayList<>();
  if(best!=null)list.add(best);
  for(DownloadQuality.Choice v:rest){boolean same=best!=null&&best.height>0&&v.height==best.height;if(!same)list.add(v);}
  if(photoPost){
   if(photos.size()==1){final DownloadQuality.Choice only=photos.get(0);panel.hero("image",Text.SAVE_IMAGE,null,"download",()->photo.on(only));}
   else{
    panel.hero("image",Text.allPhotos(photos.size()),null,"download",()->allPhotos.on());
    panel.view(chips(c,skin,panel,photos,photo));
   }
  }else if(!list.isEmpty()){
   for(int i=0;i<list.size();i++){
    final DownloadQuality.Choice item=list.get(i);
    String chip=list.size()<2?null:i==0?Text.SHEET_BEST:i==list.size()-1?Text.SHEET_WORST:null;
    String title=item.best?(item.height>0?item.height+"p":Local.of("Лучшее")):item.label;
    String icon=item.height>0&&item.height<720?"sd":(item.height<=0&&!item.best?"sd":"hd");
    if(i==0)panel.hero(icon,title,chip,"download",()->video.on(item));
    else panel.item(icon,title,chip,"download",()->video.on(item));
   }
  }else if(nativeOnly){
   panel.hero("hd",Text.DL_NATIVE,null,"download",()->nativeSave.on());
  }
  // no heading: the row says what it is, and a heading only costs another line of height
  panel.section(null);
  panel.item("music_note",Text.DL_AUDIO,null,"download",()->audio.on());
  panel.quiet(Text.CLOSE,null).show();
 }
 /** Numbered chips, six to a line, each saving one photo. */
 private static View chips(final Context c,Skin skin,final Panel panel,List<DownloadQuality.Choice> photos,final Pick pick){
  final int perLine=6;
  LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
  LinearLayout line=null;
  for(int i=0;i<photos.size();i++){
   if(i%perLine==0){
    line=new LinearLayout(c);line.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams where=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
    where.topMargin=i==0?0:dp(c,6);box.addView(line,where);
   }
   final DownloadQuality.Choice item=photos.get(i);
   TextView chip=new TextView(c);chip.setText(String.valueOf(i+1));chip.setGravity(Gravity.CENTER);
   chip.setTextColor(skin.text);chip.setTextSize(TypedValue.COMPLEX_UNIT_SP,15);
   chip.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
   chip.setBackground(Expressive.tile(c,(skin.text&0xFFFFFF)|0x14000000,(Accent.colour()&0xFFFFFF)|0x33000000,dp(c,16),dp(c,16),dp(c,8)));
   chip.setClickable(true);chip.setFocusable(true);chip.setContentDescription(item.label);
   Motion.touch(chip);
   chip.setOnClickListener(v->{panel.close();pick.on(item);});
   LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(c,44),1f);
   size.leftMargin=i%perLine==0?0:dp(c,6);line.addView(chip,size);
  }
  // fill the last line so its chips keep the width of the others
  int left=photos.size()%perLine;
  if(left!=0&&line!=null)for(int k=left;k<perLine;k++){
   View gap=new View(c);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(c,44),1f);size.leftMargin=dp(c,6);line.addView(gap,size);
  }
  return box;
 }
 private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
}
