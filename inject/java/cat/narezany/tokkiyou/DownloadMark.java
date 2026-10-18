package cat.narezany.tokkiyou;
/** One surface throughout loading and completion; no swapped background or glyph. */
final class DownloadMark extends android.widget.LinearLayout {
    private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path contour=new android.graphics.Path(),stroke=new android.graphics.Path();
    private static final float[] X=new float[512],Y=new float[512],FLOWER=new float[512];
    static{for(int i=0;i<512;i++){float angle=(float)(i*Math.PI*2/512-Math.PI/2);X[i]=(float)Math.cos(angle);Y[i]=(float)Math.sin(angle);FLOWER[i]=DownloadMorph.flowerRay(angle,27);}}
    private final float[] rays=new float[512];
    private final float density;
    private final int contrast;
    private float phase,startWidth,startHeight,lineY;
    private android.widget.TextView label;
    private android.widget.ProgressBar meter;
    private int percent=-1;
    private float displayed=1,amplitude,cycle,startProgress=1,startAmplitude,startCycle;
    private long lastFrame;
    private boolean initialised;
    private boolean completing;
    private float shapeWidth=-1,shapeHeight=-1,shapePhase=-1,shapeCx=-1,shapeCy=-1;
    DownloadMark(android.content.Context context,int colour){super(context);density=getResources().getDisplayMetrics().density;contrast=colour;setWillNotDraw(false);}
    void bind(android.widget.ProgressBar progress){meter=progress;progress.setVisibility(android.view.View.INVISIBLE);progress.setAlpha(0f);lastFrame=android.os.SystemClock.uptimeMillis();}
    void progress(int value){percent=value<0?-1:Math.min(100,value);if(!initialised){displayed=percent<0?0.35f:percent/100f;startProgress=displayed;amplitude=DownloadWave.amplitude(percent<0?-1:displayed);initialised=true;}invalidate();}
    void begin(int width,android.widget.TextView text,android.widget.ProgressBar progress){
        completing=true;startWidth=width;startHeight=Math.max(getHeight(),Math.round(48*density));
        startAmplitude=amplitude;startCycle=cycle;
        lineY=progress.getTop()+progress.getHeight()/2f-startHeight/2f;label=text;progress.setAlpha(0f);
        text.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(Local.of("Загрузка завершена"));setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);morph(0);
    }
    int initialHeight(){return Math.round(startHeight);}
    void morph(float value){phase=Math.max(0,Math.min(1,value));if(label!=null)label.setAlpha(Math.max(0,1-phase/0.28f));invalidate();}
    @Override protected void onDraw(android.graphics.Canvas canvas){
        float width=completing?startWidth:getWidth(),height=completing?startHeight:getHeight();if(width<=0 || height<=0)return;
        float t=completing?phase:0,cx=getWidth()/2f,cy=getHeight()/2f;
        if(width!=shapeWidth || height!=shapeHeight || t!=shapePhase || cx!=shapeCx || cy!=shapeCy){
        if(width!=shapeWidth || height!=shapeHeight)for(int i=0;i<512;i++)rays[i]=DownloadMorph.roundedDirection(X[i],Y[i],width,height,18*density);
        shapeWidth=width;shapeHeight=height;shapePhase=t;shapeCx=cx;shapeCy=cy;contour.reset();
        for(int i=0;i<512;i++){
            float r=DownloadMorph.mix(rays[i],FLOWER[i]*density,t);
            float x=cx+X[i]*r,y=cy+Y[i]*r;
            if(i==0)contour.moveTo(x,y);else contour.lineTo(x,y);
        }
        contour.close();}
        paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(DownloadMorph.colour(0xFF24262B,Accent.colour(),t));canvas.drawPath(contour,paint);
        if(!completing && meter==null)return;
        float left=-(width/2-16*density),right=-left;
        float base=lineY,p=startProgress,amp=startAmplitude,clock=startCycle;
        boolean moving=Motion.enabled(getContext());
        if(!completing){
            long now=android.os.SystemClock.uptimeMillis();float dt=Math.min(0.05f,Math.max(0,(now-lastFrame)/1000f));lastFrame=now;
            float target=percent<0?0.35f:percent/100f;
            displayed=moving?DownloadWave.approach(displayed,target,dt):target;
            amplitude=moving?DownloadWave.approach(amplitude,DownloadWave.amplitude(percent<0?-1:displayed),dt):0;
            cycle=moving?(now%1000)/1000f:0;clock=cycle;amp=amplitude;p=displayed;
            base=meter.getTop()+meter.getHeight()/2f-height/2f;
            if(percent<0 && moving)p=0.35f+0.25f*(float)(0.5-0.5*Math.cos(now*Math.PI/1000));
            startProgress=p;
        }
        float end=left+(right-left)*p;
        paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);paint.setStrokeWidth(4*density);
        float trackStart=Math.min(right,end+8*density);
        if(trackStart<right && t<0.2f){stroke.reset();stroke.moveTo(cx+trackStart,cy+base);stroke.lineTo(cx+right,cy+base);int alpha=Math.round(51*Math.max(0,1-t/0.2f));paint.setColor((Accent.colour()&0xFFFFFF)|(alpha<<24));canvas.drawPath(stroke,paint);}
        if(p<=0 && !completing){if(moving && isAttachedToWindow() && isShown())postInvalidateOnAnimation();return;}
        stroke.reset();for(int i=0;i<=128;i++){
            float u=i/128f,sourceX=DownloadMorph.mix(left,end,u),sourceY=base+DownloadWave.offset(sourceX-left,density,clock,amp)*(1-t)*(1-t);
            float x=cx+DownloadMorph.mix(sourceX,DownloadWave.checkX(u)*density,t),y=cy+DownloadMorph.mix(sourceY,DownloadWave.checkY(u)*density,t);
            if(i==0)stroke.moveTo(x,y);else stroke.lineTo(x,y);
        }
        paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        paint.setStrokeWidth(DownloadMorph.mix(4*density,2.5f*density,t));paint.setColor(DownloadMorph.colour(Accent.colour(),contrast,t));canvas.drawPath(stroke,paint);
        if(!completing && moving && isAttachedToWindow() && isShown())postInvalidateOnAnimation();
    }
}
