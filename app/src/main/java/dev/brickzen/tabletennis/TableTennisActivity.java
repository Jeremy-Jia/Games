package dev.brickzen.tabletennis;

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

/** Only Android lifecycle, local preferences and foreground feedback live here. */
public final class TableTennisActivity extends Activity {
    public TableTennisView game;
    public SharedPreferences preferences;
    public EncounterSession encounter;
    private ToneGenerator tones;
    private Vibrator vibrator;
    private boolean foreground;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        preferences=getSharedPreferences("table_tennis",MODE_PRIVATE);
        encounter=EncounterSession.from(this,EncounterPlan.TENNIS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams a=getWindow().getAttributes();a.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(a);}
        if(Build.VERSION.SDK_INT>=29){getWindow().setNavigationBarContrastEnforced(false);getWindow().getDecorView().setForceDarkAllowed(false);}
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
        try{tones=new ToneGenerator(AudioManager.STREAM_MUSIC,30);}catch(RuntimeException ignored){}
        game=new TableTennisView(this,this);setContentView(game);hideBars();
    }
    private void hideBars(){
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.systemBars());}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override protected void onResume(){super.onResume();foreground=true;syncFocus();hideBars();}
    @Override protected void onPause(){foreground=false;syncFocus();if(game!=null)rememberRally(game.engine.bestRally);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);syncFocus();if(focus)hideBars();}
    private void syncFocus(){
        boolean active=foreground&&hasWindowFocus();
        if(game!=null){if(active)game.onHostResume();else game.onHostPause();}
        if(active)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(tones!=null)tones.stopTone();if(vibrator!=null)vibrator.cancel();}
    }
    @Override protected void onDestroy(){if(tones!=null){tones.release();tones=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(game==null||!game.handleBack())super.onBackPressed();}
    public void feedback(String event){
        if(!foreground||!hasWindowFocus())return;
        if(preferences.getBoolean("sound",true)&&tones!=null){
            int tone=event.equals("hit")?ToneGenerator.TONE_PROP_BEEP:event.equals("win")?ToneGenerator.TONE_PROP_ACK:event.equals("point_lost")?ToneGenerator.TONE_PROP_NACK:ToneGenerator.TONE_PROP_BEEP2;
            if(!event.equals("bounce"))tones.startTone(tone,event.equals("win")?160:35);
        }
        if(preferences.getBoolean("haptics",true)&&vibrator!=null&&vibrator.hasVibrator()&&!event.equals("bounce")){
            try{vibrator.vibrate(VibrationEffect.createOneShot(event.equals("hit")?12:24,event.equals("hit")?50:75));}catch(RuntimeException ignored){}
        }
    }
    public void recordMatch(boolean won,int rally){
        if(encounter!=null)return;
        preferences.edit().putInt("matches",preferences.getInt("matches",0)+1)
                .putInt("wins",preferences.getInt("wins",0)+(won?1:0))
                .putInt("best_streak",Math.max(rally,preferences.getInt("best_streak",0))).apply();
    }
    public void rememberRally(int rally){
        if(encounter!=null)return;
        if(rally>preferences.getInt("best_streak",0))preferences.edit().putInt("best_streak",rally).apply();
    }
}
