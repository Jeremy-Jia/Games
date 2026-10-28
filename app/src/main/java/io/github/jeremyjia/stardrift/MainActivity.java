package io.github.jeremyjia.stardrift;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import org.json.JSONObject;
import dev.brickzen.arcade.EncounterPlan;
import dev.brickzen.arcade.EncounterSession;

/** Android host: the game and every visible screen live in GameView. */
public final class MainActivity extends Activity implements GameView.Host {
    private SharedPreferences preferences;
    private GameView game;
    private SoundFx sound;
    private Vibrator vibrator;
    private boolean foreground;
    private boolean bonusMode;
    private boolean bonusReturned;
    private String portalId;
    public EncounterSession encounter;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("star_drift", MODE_PRIVATE);
        encounter=EncounterSession.from(this,EncounterPlan.STAR);
        bonusMode = encounter!=null || getIntent().getBooleanExtra("bonus_mode", false);
        portalId = getIntent().getStringExtra("portal_id");
        if (portalId == null) portalId = "";
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);

        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = getSystemService(VibratorManager.class);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        sound = new SoundFx(this);
        sound.setEnabled(isSoundEnabled());
        game = new GameView(this, this);
        setContentView(game);
        hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        updateHostState();
        hideSystemBars();
    }

    @Override protected void onPause() {
        foreground = false;
        updateHostState();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        updateHostState();
        if (hasFocus) hideSystemBars();
    }

    private void updateHostState() {
        // Notification/quick-settings panels can steal focus without pausing the Activity.
        // Restoring rendering never clears GameView's user-visible pause state.
        if (foreground && hasWindowFocus()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (sound != null) sound.resume();
            if (game != null) game.onHostResume();
        } else {
            if (game != null) game.onHostPause();
            if (sound != null) sound.stop();
            if (vibrator != null) vibrator.cancel();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override protected void onDestroy() {
        if (sound != null) sound.release();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (game == null || !game.handleBack()) super.onBackPressed();
    }

    @Override public boolean isSoundEnabled() {
        return preferences.getBoolean("sound", true);
    }

    @Override public boolean isHapticsEnabled() {
        return preferences.getBoolean("haptics", true);
    }

    @Override public void setSoundEnabled(boolean enabled) {
        preferences.edit().putBoolean("sound", enabled).apply();
        if (sound != null) sound.setEnabled(enabled);
    }

    @Override public void setHapticsEnabled(boolean enabled) {
        preferences.edit().putBoolean("haptics", enabled).apply();
        if (!enabled && vibrator != null) vibrator.cancel();
    }

    @Override public boolean reduceMotion() {
        return preferences.getBoolean("reduce_motion", false);
    }

    @Override public void setReduceMotion(boolean enabled) {
        preferences.edit().putBoolean("reduce_motion", enabled).apply();
    }

    @Override public int bestScore() {
        return preferences.getInt("best_score", 0);
    }

    @Override public int bestKills() {
        return preferences.getInt("best_kills", 0);
    }

    @Override public int runs() {
        return preferences.getInt("runs", 0);
    }

    @Override public void recordRun(int score, int kills, float seconds, boolean won) {
        if(encounter!=null)return;
        if (bonusMode) {
            preferences.edit().putInt("bonus_runs", preferences.getInt("bonus_runs", 0) + 1).apply();
            return;
        }
        preferences.edit()
                .putInt("best_score", Math.max(bestScore(), score))
                .putInt("best_kills", Math.max(bestKills(), kills))
                .putInt("runs", runs() + 1)
                .putInt("wins", preferences.getInt("wins", 0) + (won ? 1 : 0))
                .putFloat("longest_seconds", Math.max(
                        preferences.getFloat("longest_seconds", 0f), seconds))
                .apply();
    }

    @Override public void event(String name) {
        if (!foreground || !hasWindowFocus()) return;
        if (sound != null) sound.play(name);
        if (!isHapticsEnabled() || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            switch (name) {
                case "hit": vibrator.vibrate(VibrationEffect.createOneShot(24, 110)); break;
                case "upgrade": vibrator.vibrate(VibrationEffect.createOneShot(22, 75)); break;
                case "pulse": vibrator.vibrate(VibrationEffect.createOneShot(36, 100)); break;
                case "win":
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 18, 45, 22, 55, 34},
                            new int[]{0, 60, 0, 80, 0, 110}, -1));
                    break;
                case "lose": vibrator.vibrate(VibrationEffect.createOneShot(50, 90)); break;
                case "tap":
                case "start": vibrator.vibrate(VibrationEffect.createOneShot(9, 40)); break;
                default: break;
            }
        } catch (RuntimeException ignored) {
            // Device-specific vibration support must never interrupt a run.
        }
    }

    @Override public void exitGame() {
        if(encounter!=null){encounter.cancel();return;}
        if (bonusMode) { finishBonus(0, false, true); return; }
        finish();
    }

    @Override public boolean isBonusRun() { return bonusMode; }
    @Override public EncounterSession encounterSession(){return encounter;}

    @Override public void finishBonus(int score, boolean survived, boolean cancelled) {
        if(encounter!=null){if(cancelled)encounter.cancel();return;}
        if (!bonusMode || bonusReturned) return;
        int reward = cancelled ? 0 : Math.max(0, Math.min(3000, score));
        try {
            JSONObject result = new JSONObject();
            result.put("portal_id", portalId);
            result.put("bonus_score", reward);
            result.put("survived", survived && !cancelled);
            result.put("cancelled", cancelled);
            if (!portalId.isEmpty() && !getSharedPreferences("portal_results", MODE_PRIVATE)
                    .edit().putString(portalId, result.toString()).commit()) {
                android.widget.Toast.makeText(this,"奖励暂未保存，请稍候",android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            bonusReturned = true;
            Intent data = new Intent().putExtra("portal_id", portalId)
                    .putExtra("bonus_score", reward).putExtra("survived", survived && !cancelled)
                    .putExtra("cancelled", cancelled);
            setResult(RESULT_OK, data);
            finish();
        } catch (org.json.JSONException error) {
            android.widget.Toast.makeText(this,"奖励暂未保存，请重试",android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
