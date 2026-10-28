package dev.brickzen.range;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import dev.brickzen.arcade.EncounterPlan;
import dev.brickzen.arcade.EncounterSession;

public final class RangeActivity extends Activity {
    public RangeView game;
    public SharedPreferences preferences;
    public EncounterSession encounter;
    private ToneGenerator tones;
    private Vibrator vibrator;
    private boolean foreground;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);preferences=getSharedPreferences("target_range",MODE_PRIVATE);
        encounter=EncounterSession.from(this,EncounterPlan.RANGE);if(isFinishing())return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams a=getWindow().getAttributes();a.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(a);}
        if(Build.VERSION.SDK_INT>=29){getWindow().setNavigationBarContrastEnforced(false);getWindow().getDecorView().setForceDarkAllowed(false);}
        setVolumeControlStream(AudioManager.STREAM_MUSIC);vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
        try{tones=new ToneGenerator(AudioManager.STREAM_MUSIC,25);}catch(RuntimeException ignored){}
        game=new RangeView(this,this);setContentView(game);hideBars();
    }
    private void hideBars(){
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.systemBars());}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override protected void onResume(){super.onResume();foreground=true;sync();hideBars();}
    @Override protected void onPause(){foreground=false;sync();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);sync();if(focus)hideBars();}
    private void sync(){boolean active=foreground&&hasWindowFocus();if(game!=null){if(active)game.onHostResume();else game.onHostPause();}
        if(active)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(tones!=null)tones.stopTone();if(vibrator!=null)vibrator.cancel();}}
    @Override public void onBackPressed(){if(game==null||!game.handleBack())super.onBackPressed();}
    @Override protected void onDestroy(){if(tones!=null){tones.release();tones=null;}super.onDestroy();}
    public void record(int score,int accuracy){if(encounter!=null)return;preferences.edit().putInt("best_score",Math.max(score,preferences.getInt("best_score",0)))
            .putInt("best_accuracy",Math.max(accuracy,preferences.getInt("best_accuracy",0))).putInt("rounds",preferences.getInt("rounds",0)+1).apply();}
    public void feedback(String name){
        if(!foreground||!hasWindowFocus())return;
        if(preferences.getBoolean("sound",true)&&tones!=null){int tone=name.equals("fire")?ToneGenerator.TONE_PROP_BEEP:name.equals("bullseye")||name.equals("round_over")?ToneGenerator.TONE_PROP_ACK:name.equals("miss")?ToneGenerator.TONE_PROP_NACK:ToneGenerator.TONE_PROP_BEEP2;tones.startTone(tone,name.equals("fire")?50:90);}
        if(preferences.getBoolean("haptics",true)&&vibrator!=null&&vibrator.hasVibrator())try{vibrator.vibrate(VibrationEffect.createOneShot(name.equals("fire")?28:12,name.equals("fire")?100:50));}catch(RuntimeException ignored){}
    }
}
