package dev.brickzen.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

/** Original sixteen-sample sound palette, reconstructed from the installed game. */
final class Audio {
    static final int SOUND_COUNT = 16;
    private final SoundPool pool;
    private final int[] hits, surprises;
    private final int armor, shatter, combo, paddle, power, clear, miss, pulse;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] streams = new int[64];
    private int streamIndex;
    private float impactGain = 0.42f;
    boolean enabled = true, closed;
    volatile int loadedCount, successfulStreams, successfulSurprises;
    int maxImpactBatch;

    Audio(Context context) {
        pool = new SoundPool.Builder().setMaxStreams(20)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) loadedCount++;
        });
        hits = new int[]{pool.load(context,R.raw.hit,1),pool.load(context,R.raw.hit2,1),
                pool.load(context,R.raw.hit3,1),pool.load(context,R.raw.hit4,1)};
        surprises = new int[]{pool.load(context,R.raw.lightning,1),pool.load(context,R.raw.nova,1),
                pool.load(context,R.raw.meteor,1),pool.load(context,R.raw.prism,1)};
        armor=pool.load(context,R.raw.armor,1);shatter=pool.load(context,R.raw.shatter,1);
        combo=pool.load(context,R.raw.combo,1);paddle=pool.load(context,R.raw.paddle,1);
        power=pool.load(context,R.raw.power,1);clear=pool.load(context,R.raw.clear,1);
        miss=pool.load(context,R.raw.miss,1);pulse=pool.load(context,R.raw.pulse,1);
    }

    boolean ready() { return loadedCount == SOUND_COUNT; }
    void beginFrame(int count) {
        maxImpactBatch=Math.max(maxImpactBatch,count);
        impactGain=0.42f/(float)Math.sqrt(Math.max(1,count));
    }
    private void playSample(int sample,float gain,float x,int priority,float rate) {
        if(!enabled||closed)return;
        float pan=GameEngine.clamp((x-500)/500,-1,1)*0.15f;
        int stream=pool.play(sample,(0.85f-pan)*gain,(0.85f+pan)*gain,priority,0,rate);
        if(stream!=0){streams[streamIndex]=stream;streamIndex=(streamIndex+1)%streams.length;successfulStreams++;}
    }
    void play(GameEngine.Event event) {
        switch(event.kind) {
            case HIT:
                float pitch=1+Math.min(7,event.combo/4)*0.018f;
                playSample(event.remainingHp>0?armor:hits[Math.max(0,event.combo-1)%4],impactGain,event.x,3,pitch);
                if(event.remainingHp==0&&event.maxHp>1)playSample(shatter,impactGain*0.65f,event.x,2,1);
                if(event.combo>=4&&event.combo%4==0)playSample(combo,impactGain*0.48f,event.x,2,pitch);
                break;
            case PADDLE:playSample(paddle,0.24f,event.x,1,1);break;
            case POWER:playSample(power,0.34f,event.x,4,1);break;
            case PULSE:playSample(pulse,0.32f,500,5,1);break;
            case CLEAR:playSample(clear,0.38f,500,7,1);break;
            case MISS:playSample(miss,0.24f,500,2,1);break;
            case CLEANUP:playSample(power,0.24f,500,4,1.12f);break;
            case SURPRISE:
                int before=successfulStreams;
                if(event.surprise!=null)playSample(surprises[event.surprise.type.ordinal()],0.38f,event.x,6,1);
                if(successfulStreams>before)successfulSurprises++;
                break;
            default:break;
        }
    }
    void preview() {
        if(!enabled||closed)return;
        for(int index=0;index<4;index++){
            final int note=index;
            handler.postDelayed(()->playSample(hits[note],0.4f,note*200+200,3,1),index*85L);
        }
        handler.postDelayed(()->playSample(armor,0.38f,500,3,1),360);
        handler.postDelayed(()->{playSample(shatter,0.3f,500,3,1);playSample(combo,0.22f,500,3,1);},470);
        for(int index=0;index<4;index++){
            final int note=index;
            handler.postDelayed(()->playSample(surprises[note],0.32f,500,5,1),650+index*210L);
        }
    }
    void stop() {
        handler.removeCallbacksAndMessages(null);
        if(closed)return;
        for(int i=0;i<streams.length;i++){if(streams[i]!=0)pool.stop(streams[i]);streams[i]=0;}
    }
    void close() { if(closed)return;stop();closed=true;pool.release(); }
}
