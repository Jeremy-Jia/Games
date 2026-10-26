package dev.brickzen.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

final class Haptics {
    private final AudioAttributes attributes=new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    private final Vibrator vibrator;
    private long last;
    Haptics(Context context) {
        if(Build.VERSION.SDK_INT>=31){
            VibratorManager manager=context.getSystemService(VibratorManager.class);
            vibrator=manager==null?null:manager.getDefaultVibrator();
        }else vibrator=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
    }
    boolean available(){return vibrator!=null&&vibrator.hasVibrator();}
    void test(){pulse(48,190);}
    private void pulse(long duration,int amplitude){
        if(!available())return;
        try{vibrator.vibrate(VibrationEffect.createOneShot(duration,vibrator.hasAmplitudeControl()?amplitude:VibrationEffect.DEFAULT_AMPLITUDE),attributes);}
        catch(RuntimeException ignored){}
    }
    void play(GameEngine.Kind kind){
        long now=SystemClock.uptimeMillis();if(now-last<45)return;
        switch(kind){
            case HIT:pulse(18,135);break;
            case PADDLE:pulse(11,95);break;
            case POWER:case CLEANUP:pulse(34,175);break;
            case PULSE:case CLEAR:pulse(45,190);break;
            case SURPRISE:pulse(56,210);break;
            default:return;
        }
        last=now;
    }
    void stop(){if(vibrator!=null)vibrator.cancel();}
}
