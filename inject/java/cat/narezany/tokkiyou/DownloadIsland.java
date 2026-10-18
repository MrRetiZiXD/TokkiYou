package cat.narezany.tokkiyou;
import android.app.*;import android.view.*;import android.widget.*;
/** Expand the existing surface, retaining its window and progress wave. */
final class DownloadIsland {
    private final Activity owner;private final Dialog dialog;private final DownloadMark surface;
    private LinearLayout rows;private ScrollView list;private android.animation.ValueAnimator motion;
    private boolean expanded,disposed;private int compactHeight;private String keys="";
    private final java.util.Map<String,Row> rendered=new java.util.LinkedHashMap<>();
    private Runnable refresh;
    DownloadIsland(Activity owner,Dialog dialog,DownloadMark surface){this.owner=owner;this.dialog=dialog;this.surface=surface;refresh=()->{if(disposed||!expanded||!this.dialog.isShowing())return;update();this.surface.postDelayed(this.refresh,250L);};surface.setOnClickListener(v->toggle());}
    boolean expanded(){return expanded;}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    void toggle(){if(disposed)return;if(expanded){collapse(null);return;}if(motion!=null)motion.cancel();
        expanded=true;if(list!=null){animate(true,null);surface.removeCallbacks(refresh);surface.postDelayed(refresh,250L);return;}compactHeight=Math.max(dp(56),surface.getHeight());keys=null;rows=new LinearLayout(owner);rows.setClickable(true);rows.setOrientation(LinearLayout.VERTICAL);
        list=new ScrollView(owner);list.setFillViewport(false);list.addView(rows);list.setAlpha(0f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(220));p.topMargin=dp(12);surface.addView(list,p);
        update();animate(true,null);surface.removeCallbacks(refresh);surface.postDelayed(refresh,250L);
    }
    void collapse(Runnable after){if(!expanded){if(after!=null)after.run();return;}expanded=false;surface.removeCallbacks(refresh);animate(false,after);}
    private void animate(boolean opening,Runnable after){
        if(motion!=null){motion.cancel();motion=null;}
        Window window=dialog.getWindow();if(window==null)return;
        int fromW=window.getAttributes().width,fromH=window.getAttributes().height>0?window.getAttributes().height:surface.getHeight();
        int targetW=Math.min(dp(opening?320:260),owner.getResources().getDisplayMetrics().widthPixels-dp(32));
        int targetH=opening?Math.min(dp(284),owner.getResources().getDisplayMetrics().heightPixels/2):compactHeight;
        final float startAlpha=list==null?0:list.getAlpha();
        if(!Motion.enabled(owner)){resize(targetW,targetH,opening?1:0);end(opening,after);return;}
        motion=android.animation.ValueAnimator.ofFloat(0,1);motion.setDuration(340L);motion.setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f));
        motion.addUpdateListener(a->{if(disposed)return;float t=(Float)a.getAnimatedValue();resize(Math.round(DownloadMorph.mix(fromW,targetW,t)),Math.round(DownloadMorph.mix(fromH,targetH,t)),DownloadMorph.mix(startAlpha,opening?1:0,t));});
        motion.addListener(new android.animation.AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}@Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled&&!disposed)end(opening,after);}});motion.start();
    }
    private void resize(int width,int height,float alpha){Window window=dialog.getWindow();if(window==null)return;WindowManager.LayoutParams p=window.getAttributes();if(p.width!=width||p.height!=height){p.width=width;p.height=height;window.setAttributes(p);}if(list!=null){list.setAlpha(alpha);list.setTranslationY(dp(6)*(1-alpha));}}
    private void end(boolean opening,Runnable after){motion=null;if(!opening){if(list!=null)surface.removeView(list);list=null;rows=null;rendered.clear();}if(after!=null)after.run();else if(!opening)Screen.tasksCollapsed();}
    void dispose(){disposed=true;surface.removeCallbacks(refresh);if(motion!=null){motion.cancel();motion=null;}}
    private TextView text(String s,int size){TextView v=new TextView(owner);v.setText(s);v.setTextSize(size);v.setTextColor(0xfff2f0f5);v.setIncludeFontPadding(false);Fonts.apply(v);return v;}
    private static final class Row {TextView state,action;ProgressBar progress;}
    private void update(){if(rows==null)return;java.util.List<DownloadTasks.Task> tasks=DownloadTasks.snapshot();StringBuilder fingerprint=new StringBuilder();for(DownloadTasks.Task t:tasks)fingerprint.append(t.key).append((char)10);
        String next=fingerprint.toString();if(!next.equals(keys)){keys=next;rows.removeAllViews();rendered.clear();
            TextView close=text(Local.of("Задачи  ·  Свернуть"),13);close.setGravity(Gravity.CENTER);close.setMinHeight(dp(48));close.setTextColor(Accent.colour());close.setOnClickListener(v->collapse(null));rows.addView(close);
            if(tasks.isEmpty())rows.addView(text(Local.of("Нет сетевых задач"),13));
            for(DownloadTasks.Task t:tasks){Row row=new Row();LinearLayout block=new LinearLayout(owner);block.setOrientation(LinearLayout.VERTICAL);block.setPadding(0,dp(8),0,dp(8));
                TextView title=text(t.label,14);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);block.addView(title);
                TextView who=text(t.origin+" · "+t.owner,11);who.setAlpha(0.6f);who.setMaxLines(1);who.setEllipsize(android.text.TextUtils.TruncateAt.END);block.addView(who);
                row.progress=new ProgressBar(owner,null,android.R.attr.progressBarStyleHorizontal);row.progress.setMax(100);row.progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Accent.colour()));block.addView(row.progress,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(5)));
                LinearLayout bottom=new LinearLayout(owner);bottom.setGravity(Gravity.CENTER_VERTICAL);row.state=text("",12);bottom.addView(row.state,new LinearLayout.LayoutParams(0,dp(48),1));row.state.setGravity(Gravity.CENTER_VERTICAL);
                row.action=text("",12);row.action.setTextColor(Accent.colour());row.action.setMinHeight(dp(48));row.action.setGravity(Gravity.CENTER);row.action.setPadding(dp(12),0,0,0);bottom.addView(row.action);block.addView(bottom);rows.addView(block);rendered.put(t.key,row);
            }
        }
        for(DownloadTasks.Task t:tasks){Row row=rendered.get(t.key);if(row==null)continue;String state=t.state+(t.percent>=0&&t.state.equals(Local.of("Загрузка"))?" · "+t.percent+"%":"");if(!state.contentEquals(row.state.getText()))row.state.setText(state);if(row.progress.isIndeterminate()!=(t.percent<0))row.progress.setIndeterminate(t.percent<0);if(t.percent>=0)row.progress.setProgress(t.percent);row.action.setVisibility(t.cancel||t.retry?View.VISIBLE:View.GONE);String action=t.cancel?Local.of("Отменить"):Local.of("Повторить");if(!action.contentEquals(row.action.getText()))row.action.setText(action);row.action.setOnClickListener(v->{if(t.cancel)DownloadTasks.cancel(t.key);else DownloadTasks.retry(t.key);update();});}
    }
}
