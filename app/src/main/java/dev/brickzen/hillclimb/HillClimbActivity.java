package dev.brickzen.hillclimb;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import dev.brickzen.arcade.EncounterPlan;
import dev.brickzen.arcade.EncounterSession;

/** Android lifecycle, local records and short foreground-only feedback. */
public final class HillClimbActivity extends Activity implements HillClimbView.Host {
    public HillClimbView game;
    public EncounterSession encounter;
    private SharedPreferences preferences;
    private ToneGenerator sound;
    private Vibrator vibrator;
    private boolean foreground;
    private long lastSound;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);preferences=getSharedPreferences("hill_climb",MODE_PRIVATE);encounter=EncounterSession.from(this,EncounterPlan.HILL);setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        if(Build.VERSION.SDK_INT>=29){getWindow().setNavigationBarContrastEnforced(false);getWindow().getDecorView().setForceDarkAllowed(false);}
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams params=getWindow().getAttributes();params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(params);}
        if(Build.VERSION.SDK_INT>=31){VibratorManager manager=getSystemService(VibratorManager.class);vibrator=manager==null?null:manager.getDefaultVibrator();}
        else vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        try{sound=new ToneGenerator(AudioManager.STREAM_MUSIC,25);}catch(RuntimeException ignored){}
        game=new HillClimbView(this,this);setContentView(game);hideBars();
    }
    private void hideBars(){if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
    @Override protected void onResume(){super.onResume();foreground=true;sync();hideBars();}
    @Override protected void onPause(){foreground=false;sync();if(game!=null)record((int)game.engine.distance,game.engine.coins);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);sync();if(focus)hideBars();}
    private void sync(){if(foreground&&hasWindowFocus()){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(game!=null)game.onHostResume();}
        else{if(game!=null)game.onHostPause();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(sound!=null)sound.stopTone();if(vibrator!=null)vibrator.cancel();}}
    @Override protected void onDestroy(){if(sound!=null){sound.release();sound=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(game==null||!game.handleBack())super.onBackPressed();}
    @Override public int bestDistance(){return preferences.getInt("best_distance",0);}
    @Override public int bestCoins(){return preferences.getInt("best_coins",0);}
    @Override public boolean soundEnabled(){return preferences.getBoolean("sound",true);}
    @Override public boolean hapticsEnabled(){return preferences.getBoolean("haptics",true);}
    @Override public void setSound(boolean value){preferences.edit().putBoolean("sound",value).apply();if(!value&&sound!=null)sound.stopTone();}
    @Override public void setHaptics(boolean value){preferences.edit().putBoolean("haptics",value).apply();}
    @Override public EncounterSession encounter(){return encounter;}
    @Override public void started(){if(encounter==null)preferences.edit().putInt("runs",preferences.getInt("runs",0)+1).apply();}
    @Override public void record(int distance,int coins){if(encounter==null)preferences.edit().putInt("best_distance",Math.max(bestDistance(),distance)).putInt("best_coins",Math.max(bestCoins(),coins)).apply();}
    @Override public void quit(){if(encounter!=null)encounter.cancel();else finish();}
    @Override public void feedback(String event){
        if(!foreground||!hasWindowFocus())return;boolean heavy=event.equals("land")||event.equals("over");long now=SystemClock.elapsedRealtime();
        if(soundEnabled()&&sound!=null&&now-lastSound>90){lastSound=now;try{sound.startTone(event.equals("over")?ToneGenerator.TONE_PROP_NACK:event.equals("fuel")?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_BEEP,event.equals("over")?100:28);}catch(RuntimeException ignored){}}
        if(hapticsEnabled()&&vibrator!=null&&vibrator.hasVibrator())try{vibrator.vibrate(VibrationEffect.createOneShot(heavy?26:8,heavy?75:35));}catch(RuntimeException ignored){}
    }
}
