package cat.narezany.tokkiyou;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/** Settings-only network: 28 points, 56 links maximum, vsync scheduling, capped at 30 updates/second to keep scrolling smooth. */
final class PlexusBackground extends View {
    private final PlexusField field=new PlexusField();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean running,enabled=true,moving;
    private long last;private float intensity=1;
    private final Runnable frame=()->{
        if(!canAnimate())return;
        long now=android.os.SystemClock.uptimeMillis();
        if(now-last>=33L){field.step((now-last)/1000f*intensity);last=now;invalidate();}
        postOnAnimation(this.frame);
    };
    PlexusBackground(Context context){super(context);density=getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setFocusable(false);}
    void intensity(int value){float next=Math.max(0,Math.min(100,value))/100f;if(next==intensity)return;intensity=next;restart();}
    void configure(boolean value){if(enabled==value)return;enabled=value;setVisibility(value?VISIBLE:GONE);restart();}
    void running(boolean value){running=value;if(!value)field.release();restart();}
    private boolean canAnimate(){return enabled && running && moving && intensity>0 && isAttachedToWindow() && getWindowVisibility()==VISIBLE;}
    private void restart(){removeCallbacks(frame);moving=Motion.enabled(getContext());last=android.os.SystemClock.uptimeMillis();invalidate();if(canAnimate())postOnAnimation(frame);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();restart();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);field.release();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);restart();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){field.resize(w,h,density);}
    void pointer(MotionEvent event){
        if(!enabled || !running)return;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)field.pick(event.getX(),event.getY(),80*density);
        else if(action==MotionEvent.ACTION_MOVE)field.drag(event.getX(),event.getY());
        else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)field.release();
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        if(!enabled)return;
        int tint=Accent.colour()&0xFFFFFF;
        float range=132*density,range2=range*range;
        paint.setStrokeWidth(density*0.8f);
        int links=0;
        for(int i=0;i<PlexusField.COUNT;i++){
            int neighbours=0;
            for(int k=i+1;k<PlexusField.COUNT && neighbours<2 && links<56;k++){
                float dx=field.x[i]-field.x[k],dy=field.y[i]-field.y[k],d=dx*dx+dy*dy;
                if(d>=range2)continue;
                paint.setColor((Math.round((28+40*intensity)*(1-d/range2))<<24)|tint);
                canvas.drawLine(field.x[i],field.y[i],field.x[k],field.y[k],paint);neighbours++;links++;
            }
            paint.setColor(0x60FFFFFF);canvas.drawCircle(field.x[i],field.y[i],1.4f*density,paint);
        }
    }
    /** Observe gestures without consuming native clicks or ScrollView movement. */
    static final class Host extends FrameLayout {
        PlexusBackground background;
        Host(Context context){super(context);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){if(background!=null)background.pointer(event);return super.dispatchTouchEvent(event);}
    }
}
