package cat.narezany.margyt;

/** Fixed storage and bounded work: no allocations while advancing or drawing. */
final class PlexusField {
    static final int COUNT=28;
    final float[] x=new float[COUNT],y=new float[COUNT],vx=new float[COUNT],vy=new float[COUNT];
    float width,height;int grabbed=-1;
    void resize(float w,float h,float density) {
        width=w;height=h;java.util.Random random=new java.util.Random(21721);
        for(int i=0;i<COUNT;i++){
            x[i]=random.nextFloat()*w;y[i]=random.nextFloat()*h;
            vx[i]=(random.nextFloat()-0.5f)*9*density;vy[i]=(random.nextFloat()-0.5f)*9*density;
        }
        grabbed=-1;
    }
    void step(float seconds) {
        float dt=Math.max(0,Math.min(0.05f,seconds));
        for(int i=0;i<COUNT;i++)if(i!=grabbed){
            x[i]+=vx[i]*dt;y[i]+=vy[i]*dt;
            if(x[i]<0){x[i]=0;vx[i]=Math.abs(vx[i]);}else if(x[i]>width){x[i]=width;vx[i]=-Math.abs(vx[i]);}
            if(y[i]<0){y[i]=0;vy[i]=Math.abs(vy[i]);}else if(y[i]>height){y[i]=height;vy[i]=-Math.abs(vy[i]);}
        }
    }
    void pick(float px,float py,float radius) {
        float best=radius*radius;grabbed=-1;
        for(int i=0;i<COUNT;i++){float dx=x[i]-px,dy=y[i]-py,d=dx*dx+dy*dy;if(d<best){best=d;grabbed=i;}}
    }
    void drag(float px,float py) {if(grabbed>=0){x[grabbed]=Math.max(0,Math.min(width,px));y[grabbed]=Math.max(0,Math.min(height,py));}}
    void release(){grabbed=-1;}
}
