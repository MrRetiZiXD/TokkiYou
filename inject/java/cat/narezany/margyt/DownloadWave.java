package cat.narezany.margyt;
/** Compact M3 Expressive dimensions, independent of a Compose runtime. */
final class DownloadWave {
    static float clamp(float value){return Math.max(0,Math.min(1,value));}
    static float amplitude(float progress){return progress<0?1:clamp((progress-0.1f)/0.08f)*clamp((0.95f-progress)/0.08f);}
    static float offset(float x,float density,float cycle,float amplitude){return (float)Math.sin((x/(40*density)-cycle)*Math.PI*2)*3*density*amplitude;}
    static float approach(float from,float to,float seconds){return DownloadMorph.mix(from,to,1-(float)Math.exp(-seconds/0.12f));}
    static float checkX(float u){return u<=0.5f?DownloadMorph.mix(-10,-3,u*2):DownloadMorph.mix(-3,11,(u-0.5f)*2);}
    static float checkY(float u){return u<=0.5f?DownloadMorph.mix(0,6,u*2):DownloadMorph.mix(6,-8,(u-0.5f)*2);}
}
