package cat.narezany.tokkiyou;
/** Shared geometry for one continuous pill -> eight-petal completion contour. */
final class DownloadMorph {
    static float mix(float a,float b,float t){return a+(b-a)*t;}
    static float roundedRay(float angle,float width,float height,float corner){
        return roundedDirection((float)Math.cos(angle),(float)Math.sin(angle),width,height,corner);
    }
    static float roundedDirection(float dx,float dy,float width,float height,float corner){
        float x=Math.abs(dx),y=Math.abs(dy);
        float hw=width/2,hh=height/2,r=Math.min(corner,Math.min(hw,hh));
        float ray=Math.min(hw/Math.max(x,0.00001f),hh/Math.max(y,0.00001f));
        if(ray*x<=hw-r || ray*y<=hh-r)return ray;
        float cx=hw-r,cy=hh-r,b=cx*x+cy*y;
        return b+(float)Math.sqrt(Math.max(0,b*b-cx*cx-cy*cy+r*r));
    }
    // Normalised radial silhouette traced from the supplied eight-petal PNG.
    private static final float[] FLOWER={0.9076482f,0.9133127f,0.9253145f,0.9426181f,0.9573000f,0.9702557f,0.9775118f,0.9793025f,0.9773890f,0.9710281f,0.9618292f,0.9466557f,0.9273509f,0.9090994f,0.8882439f,0.8765814f,0.8749839f,0.8866054f,0.9018374f,0.9201358f,0.9326994f,0.9435017f,0.9506057f,0.9532097f,0.9527942f,0.9495406f,0.9457078f,0.9377436f,0.9224823f,0.9038563f,0.8788343f,0.8618000f,0.8526303f,0.8588390f,0.8728246f,0.8943824f,0.9120311f,0.9320147f,0.9477793f,0.9567148f,0.9613319f,0.9636667f,0.9643513f,0.9620809f,0.9524021f,0.9397566f,0.9219615f,0.9083153f,0.8980865f,0.8988706f,0.9085552f,0.9266721f,0.9434139f,0.9620575f,0.9778512f,0.9878928f,0.9964129f,0.9999122f,0.9988238f,0.9918252f,0.9789865f,0.9647258f,0.9456785f,0.9322664f,0.9249985f,0.9299549f,0.9426415f,0.9613611f,0.9767160f,0.9898005f,0.9969513f,0.9981567f,0.9957224f,0.9890748f,0.9802446f,0.9652642f,0.9460764f,0.9286676f,0.9119024f,0.9054187f,0.9105975f,0.9245363f,0.9396161f,0.9562526f,0.9683012f,0.9791035f,0.9858857f,0.9885716f,0.9881444f,0.9836269f,0.9766751f,0.9640470f,0.9467494f,0.9282521f,0.9042952f,0.8881620f,0.8818246f,0.8927731f,0.9103283f,0.9333548f,0.9497689f,0.9649716f,0.9756334f,0.9812160f,0.9839429f,0.9821932f,0.9772836f,0.9674410f,0.9532506f,0.9378723f,0.9168295f,0.9001346f,0.8884604f,0.8899409f,0.9011645f,0.9198256f,0.9359705f,0.9523495f,0.9648604f,0.9717889f,0.9765580f,0.9796302f,0.9796009f,0.9752121f,0.9614664f,0.9458248f,0.9253730f,0.9128445f};
    static float flowerRay(float angle,float radius){
        double turn=(angle/(Math.PI*2))%1;if(turn<0)turn+=1;float index=(float)(turn*128);int i=(int)index;
        return radius*mix(FLOWER[i%128],FLOWER[(i+1)%128],index-i);
    }
    static float radius(float angle,float width,float height,float density,float phase){return mix(roundedRay(angle,width,height,18*density),flowerRay(angle,27*density),phase);}
    static int colour(int from,int to,float phase){int result=0;for(int shift=0;shift<=24;shift+=8)result|=Math.round(mix((from>>>shift)&255,(to>>>shift)&255,phase))<<shift;return result;}
}
