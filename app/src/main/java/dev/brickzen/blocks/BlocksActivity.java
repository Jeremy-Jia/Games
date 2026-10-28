package dev.brickzen.blocks;

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

/** Owns audio, feedback, records and Android lifecycle; the board stays Android independent. */
public final class BlocksActivity extends Activity implements BlocksView.Host {
    public BlocksView game;
    private SharedPreferences preferences;
    private ToneGenerator sound;
    private Vibrator vibrator;
    private boolean foreground;
    private long lastSound;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences=getSharedPreferences("blocks",MODE_PRIVATE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        if(Build.VERSION.SDK_INT>=28){
            WindowManager.LayoutParams attributes=getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        if(Build.VERSION.SDK_INT>=31){VibratorManager manager=getSystemService(VibratorManager.class);vibrator=manager==null?null:manager.getDefaultVibrator();}
        else vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        try{sound=new ToneGenerator(AudioManager.STREAM_MUSIC,30);}catch(RuntimeException ignored){}
        game=new BlocksView(this,this);setContentView(game);hideBars();
    }
    private void hideBars(){
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.systemBars());
        }}else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override protected void onResume(){super.onResume();foreground=true;updateForeground();hideBars();}
    @Override protected void onPause(){foreground=false;updateForeground();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);updateForeground();if(focused)hideBars();}
    private void updateForeground(){
        if(foreground&&hasWindowFocus()){
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(game!=null)game.onHostResume();
        }else{
            if(game!=null)game.onHostPause();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if(sound!=null)sound.stopTone();if(vibrator!=null)vibrator.cancel();
        }
    }
    @Override protected void onDestroy(){if(sound!=null){sound.release();sound=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(game==null||!game.handleBack())super.onBackPressed();}
    @Override public int bestScore(){return preferences.getInt("best_score",0);}
    @Override public int bestLines(){return preferences.getInt("best_lines",0);}
    @Override public boolean soundEnabled(){return preferences.getBoolean("sound",true);}
    @Override public boolean hapticsEnabled(){return preferences.getBoolean("haptics",true);}
    @Override public void setSound(boolean enabled){preferences.edit().putBoolean("sound",enabled).apply();if(!enabled&&sound!=null)sound.stopTone();}
    @Override public void setHaptics(boolean enabled){preferences.edit().putBoolean("haptics",enabled).apply();}
    @Override public void started(){preferences.edit().putInt("runs",preferences.getInt("runs",0)+1).apply();}
    @Override public void record(int score,int lines){preferences.edit().putInt("best_score",Math.max(bestScore(),score)).putInt("best_lines",Math.max(bestLines(),lines)).apply();}
    @Override public void quit(){finish();}
    @Override public void feedback(String event){
        if(!foreground||!hasWindowFocus())return;
        boolean strong=event.equals("tetris")||event.equals("clear")||event.equals("over");
        if(hapticsEnabled()&&vibrator!=null&&!event.equals("move"))try{
            vibrator.vibrate(VibrationEffect.createOneShot(strong?28:9,strong?90:45));
        }catch(RuntimeException ignored){}
        long now=SystemClock.elapsedRealtime();
        if(soundEnabled()&&sound!=null&&now-lastSound>55&&!event.equals("move")&&!event.equals("lock")){
            lastSound=now;
            int tone=event.equals("over")?ToneGenerator.TONE_PROP_NACK:event.equals("clear")||event.equals("tetris")?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_BEEP;
            try{sound.startTone(tone,strong?90:30);}catch(RuntimeException ignored){}
        }
    }
}
