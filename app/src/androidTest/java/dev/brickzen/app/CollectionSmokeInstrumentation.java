package dev.brickzen.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Device smoke test with no third-party runner. It exercises both real Activities and the
 * draw-loop handoff, then restores every preference entry it touched, including user records.
 * Run only on an unlocked, foreground device. A real pilot plays each wave and the final boss;
 * the wall-clock watchdog belongs only to this test, never to gameplay.
 */
public final class CollectionSmokeInstrumentation extends Instrumentation {
    private static final String[] PREFERENCE_FILES = {"brickzen", "star_drift", "portal_results"};
    private final Map<String, Map<String, Object>> originalPreferences = new LinkedHashMap<>();
    private final List<Activity> createdActivities = new CopyOnWriteArrayList<>();
    private final List<Activity> destroyedActivities = new CopyOnWriteArrayList<>();
    private final List<ActivityMonitor> monitors = new ArrayList<>();
    private final Bundle result = new Bundle();
    private File verificationDirectory;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void callActivityOnCreate(Activity activity, Bundle state) {
        createdActivities.add(activity);
        super.callActivityOnCreate(activity, state);
    }

    @Override public void callActivityOnDestroy(Activity activity) {
        try { super.callActivityOnDestroy(activity); }
        finally { destroyedActivities.add(activity); }
    }

    @Override public void onStart() {
        super.onStart();
        Throwable failure = null;
        try {
            backupPreferences();
            File external = getTargetContext().getExternalFilesDir(null);
            check(external != null, "Application external files directory unavailable");
            verificationDirectory = new File(external, "verification");
            check(verificationDirectory.isDirectory() || verificationDirectory.mkdirs(), "Cannot create verification directory");
            result.putString("screenshots", verificationDirectory.getAbsolutePath());
            progress("Preferences backed up; opening the original Brick Zen activity");
            testSuccessfulRoundTrip();
            progress("Success and duplicate-result checks passed; verifying cancellation");
            testCancelledRoundTrip();
            progress("Cancellation continues automatically; verifying defeated encounter also returns");
            testFailedRoundTrip();
        } catch (Throwable error) {
            failure = error;
            result.putString("failure", Log.getStackTraceString(error));
        } finally {
            try {
                finishTestActivities();
            } catch (Throwable finishFailure) {
                result.putString("finish_failure", Log.getStackTraceString(finishFailure));
                if (failure == null) failure = finishFailure;
            }
            try {
                restorePreferences();
                result.putBoolean("preferences_restored", true);
            } catch (Throwable restoreFailure) {
                result.putBoolean("preferences_restored", false);
                result.putString("restore_failure", Log.getStackTraceString(restoreFailure));
                if (failure == null) failure = restoreFailure;
            }
            for (ActivityMonitor monitor : monitors) removeMonitor(monitor);
        }
        boolean passed = failure == null;
        result.putString("result", passed ? "PASS" : "FAIL");
        result.putString("stream", "\n" + (passed ? "PASS" : "FAIL")
                + ": collection portal smoke test; preferences_restored="
                + result.getBoolean("preferences_restored") + "\n"
                + (failure == null ? "" : Log.getStackTraceString(failure)));
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private MainActivity brickActivity;

    private void testSuccessfulRoundTrip() throws Exception {
        // A user's unfinished portal is preserved in the backup, not consumed by this test run.
        SharedPreferences brickPrefs = getTargetContext().getSharedPreferences("brickzen", Context.MODE_PRIVATE);
        check(brickPrefs.edit().remove(MainActivity.PORTAL_SNAPSHOT).remove("portal_message").commit(), "Prepare isolated portal checkpoint");
        // HyperOS can reject an instrumented app's first background start. Enter through
        // the same exported launcher as adb/Launcher, then click the real collection card.
        ActivityMonitor hallMonitor = addMonitor(CollectionActivity.class.getName(), null, false);
        monitors.add(hallMonitor);
        try (ParcelFileDescriptor command = getUiAutomation().executeShellCommand(
                "am start --display 0 -n dev.brickzen.app/.CollectionActivity");
             FileInputStream input = new FileInputStream(command.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { /* Drain the shell result. */ }
        }
        Activity hall = waitForMonitorWithTimeout(hallMonitor, 8000);
        check(hall instanceof CollectionActivity, "Collection launcher opened");
        await("Collection foreground and layout", 8000, () -> onMain(() ->
                hall.hasWindowFocus() && hall.findViewById(android.R.id.content).getWidth() > 0));
        screenshot(hall.findViewById(android.R.id.content), "collection.png");
        ActivityMonitor brickMonitor = addMonitor(MainActivity.class.getName(), null, false);
        monitors.add(brickMonitor);
        onMain(() -> {
            View card = findDescription(hall.findViewById(android.R.id.content), "进入碰界打砖块");
            check(card != null && card.performClick(), "Real collection card opens Brick Zen");
            return null;
        });
        Activity launched = waitForMonitorWithTimeout(brickMonitor, 8000);
        check(launched instanceof MainActivity, "Brick Zen activity opened within eight seconds");
        brickActivity = (MainActivity) launched;
        waitForIdleSync();
        onMain(() -> { brickActivity.game.start(true); return null; });
        await("Brick Zen view layout", 5000, () -> onMain(() -> brickActivity.game.getWidth() > 0 && brickActivity.game.getHeight() > 0));
        onMain(() -> {
            check(brickActivity.game.engine.bricks.size() >= 170, "Dense board contains many small bricks");
            check(!brickActivity.game.engine.obstacles.isEmpty(), "Rebound obstacles are present");
            return null;
        });
        screenshot(brickActivity.game, "brick-before.png");

        ActivityMonitor monitor = starMonitor();
        FrozenLevel frozen = triggerMystery(brickActivity);
        io.github.jeremyjia.stardrift.MainActivity star = waitForStar(monitor);
        verifyPortalStart(star, frozen);
        screenshot((View) field(star, "game"), "star-challenge.png");
        io.github.jeremyjia.stardrift.GameView starView = (io.github.jeremyjia.stardrift.GameView) field(star, "game");
        io.github.jeremyjia.stardrift.GameEngine starEngine = (io.github.jeremyjia.stardrift.GameEngine) field(starView, "game");
        check(onMain(() -> starEngine.challengeMode && starEngine.getWaveCount() == 5 && !starEngine.bossSpawned), "Portal launches four combat waves followed by a boss");

        long deadline = SystemClock.elapsedRealtime() + 240000;
        long nextProgress = 0;
        int highestWave = 1;
        boolean sawBoss = false;
        boolean won = false;
        while (SystemClock.elapsedRealtime() < deadline) {
            Integer state = onMain(() -> {
                check(starEngine.phase!=io.github.jeremyjia.stardrift.GameEngine.Phase.UPGRADE,"Portal combat never opens an upgrade dialog");
                float angle=starEngine.time*1.3f;
                starEngine.setTarget(200+(float)Math.sin(angle)*140,starEngine.height*.55f+(float)Math.cos(angle)*180);
                if(starEngine.pulseCharge>=.999f && starEngine.enemies.size()>3)starEngine.pulse();
                if (starEngine.phase == io.github.jeremyjia.stardrift.GameEngine.Phase.WON) return 2;
                if (starEngine.phase == io.github.jeremyjia.stardrift.GameEngine.Phase.LOST) return 3;
                return 0;
            });
            if (state == 2) { won = true; break; }
            check(state != 3, "Moving pilot failed the reward encounter");
            int wave = onMain(starEngine::getWave);
            check(wave >= highestWave, "Combat waves never move backwards");
            highestWave = wave;
            if (onMain(() -> starEngine.bossSpawned) && !sawBoss) {
                check(onMain(() -> starEngine.isBossWave() && starEngine.getWave() == starEngine.getWaveCount()), "Boss appears only in the final wave");
                sawBoss = true;
                screenshot(starView, "star-boss.png");
            }
            if (SystemClock.elapsedRealtime() >= nextProgress) {
                String status = onMain(() -> "Star combat running: " + Math.round(starEngine.time)
                        + " s, wave="+starEngine.getWave()+"/"+starEngine.getWaveCount()
                        + ", objective="+starEngine.getWaveKills()+"/"+starEngine.getWaveTarget()
                        + ", level=" + starEngine.level + ", kills="+Math.round(starEngine.kills)+", hp=" + Math.round(starEngine.hp)
                        + ", bossHp=" + bossHp(starEngine));
                progress(status); nextProgress = SystemClock.elapsedRealtime() + 5000;
            }
            SystemClock.sleep(100);
        }
        check(won, "Real combat did not defeat the final boss before the test watchdog");
        check(sawBoss && onMain(() -> starEngine.bossSpawned && starEngine.bossDefeated), "Victory follows a visible final boss defeated through normal combat");
        check(onMain(()->starEngine.level>1),"Automatic upgrades occurred without test input");
        result.putFloat("challenge_seconds", onMain(() -> starEngine.time));
        result.putBoolean("final_boss_defeated", true);
        screenshot(starView, "star-complete.png");
        int expectedReward = onMain(() -> Math.min(3000, Math.round(starEngine.score * .5f) + 500));
        // No finish/claim/resume tap: production code must return and continue by itself.
        awaitBrickResult(frozen.portalId);
        onMain(() -> {
            GameEngine checkpoint=GameEngine.fromSnapshot(brickActivity.game.prefs.getString(MainActivity.PORTAL_SNAPSHOT,null));
            check(checkpoint!=null,"Completion checkpoint exists");frozen.verifyPhysicalState(checkpoint);
            check(checkpoint.score==frozen.score+expectedReward,"Computed reward was persisted before play continued");
            GameEngine engine = brickActivity.game.engine;
            check(!engine.portalActive && !engine.portalPending && engine.state == GameEngine.State.PLAYING, "Returned level continues without a resume dialog");
            check(engine.wideUntil >= frozen.time + 14 && engine.charge == 1, "Survival awards WIDE and full charge");
            int scoreBeforeReplay=engine.score;
            Intent duplicate = new Intent().putExtra("portal_id", frozen.portalId).putExtra("bonus_score", expectedReward)
                    .putExtra("survived", true).putExtra("cancelled", false);
            brickActivity.onActivityResult(MainActivity.STAR_PORTAL_REQUEST, Activity.RESULT_OK, duplicate);
            check(engine.score == scoreBeforeReplay, "Duplicate activity result cannot award twice");
            return null;
        });
        await("Ball moves after automatic return",2000,()->onMain(()->brickActivity.game.engine.time>frozen.time+.1f));
        screenshot(brickActivity.game, "brick-return.png");
        result.putBoolean("success_round_trip", true);
        result.putInt("awarded_score", expectedReward);
        result.putBoolean("duplicate_reward_rejected", true);
    }

    private static int bossHp(io.github.jeremyjia.stardrift.GameEngine engine) {
        for (io.github.jeremyjia.stardrift.GameEngine.Enemy enemy : engine.enemies)
            if (enemy.type == 3) return Math.round(enemy.hp);
        return 0;
    }

    private void testCancelledRoundTrip() throws Exception {
        onMain(() -> { brickActivity.game.start(true); return null; });
        ActivityMonitor monitor = starMonitor();
        FrozenLevel frozen = triggerMystery(brickActivity);
        io.github.jeremyjia.stardrift.MainActivity star = waitForStar(monitor);
        verifyPortalStart(star, frozen);
        onMain(() -> { star.finishBonus(0, false, true); return null; });
        awaitBrickResult(frozen.portalId);
        onMain(() -> {
            GameEngine engine = brickActivity.game.engine;
            GameEngine checkpoint=GameEngine.fromSnapshot(brickActivity.game.prefs.getString(MainActivity.PORTAL_SNAPSHOT,null));
            check(checkpoint!=null,"Cancelled checkpoint exists");frozen.verifyPhysicalState(checkpoint);
            check(checkpoint.score == frozen.score && !engine.portalActive && engine.state == GameEngine.State.PLAYING,
                    "Cancellation keeps original score and resumes automatically");
            check(engine.charge == frozen.charge && engine.wideUntil == frozen.wideUntil, "Cancellation grants no power-up");
            return null;
        });
        result.putBoolean("cancel_round_trip", true);
    }

    private void testFailedRoundTrip() throws Exception {
        onMain(()->{brickActivity.game.start(true);return null;});
        ActivityMonitor monitor=starMonitor();FrozenLevel frozen=triggerMystery(brickActivity);
        io.github.jeremyjia.stardrift.MainActivity star=waitForStar(monitor);
        View view=(View)field(star,"game");
        io.github.jeremyjia.stardrift.GameEngine engine=(io.github.jeremyjia.stardrift.GameEngine)field(view,"game");
        onMain(()->{
            engine.hp=1;engine.invulnerable=0;
            io.github.jeremyjia.stardrift.GameEngine.Shot shot=new io.github.jeremyjia.stardrift.GameEngine.Shot();
            shot.x=engine.shipX;shot.y=engine.shipY;shot.hostile=true;shot.damage=20;shot.life=2;shot.r=4;engine.shots.add(shot);
            engine.update(.016f);check(engine.phase==io.github.jeremyjia.stardrift.GameEngine.Phase.LOST,"Damage ends encounter");view.invalidate();return null;
        });
        awaitBrickResult(frozen.portalId);
        onMain(()->{check(brickActivity.game.engine.state==GameEngine.State.PLAYING,"Defeat also returns without a confirmation");return null;});
        result.putBoolean("defeat_auto_return",true);
    }

    private FrozenLevel triggerMystery(MainActivity activity) throws Exception {
        return onMain(() -> {
            GameEngine engine = activity.game.engine;
            engine.launch();
            GameEngine.Brick mystery = null;
            for (GameEngine.Brick brick : engine.bricks) if (brick.mystery) { mystery = brick; break; }
            check(mystery != null, "Current level contains a mystery brick");
            engine.hit(mystery, false);
            check(engine.portalPending && engine.portalActive && engine.state == GameEngine.State.PAUSED, "Mystery hit creates frozen portal");
            FrozenLevel frozen = new FrozenLevel(engine);
            // Production onDraw consumes portalPending and posts MainActivity.openStarPortal.
            activity.game.invalidate();
            return frozen;
        });
    }

    private void verifyPortalStart(io.github.jeremyjia.stardrift.MainActivity star, FrozenLevel frozen) throws Exception {
        check(star.getIntent().getBooleanExtra("bonus_mode", false)
                && frozen.portalId.equals(star.getIntent().getStringExtra("portal_id")), "Portal identity delivered to bonus Activity");
        onMain(() -> {
            GameEngine engine = brickActivity.game.engine;
            frozen.verifyPhysicalState(engine);
            check(engine.portalActive && engine.state == GameEngine.State.PAUSED, "Original level remains frozen while star Activity runs");
            String snapshot = brickActivity.game.prefs.getString(MainActivity.PORTAL_SNAPSHOT, null);
            GameEngine restored = GameEngine.fromSnapshot(snapshot);
            check(restored != null && restored.portalActive && frozen.portalId.equals(restored.portalId), "Checkpoint saved before portal launch");
            frozen.verifyPhysicalState(restored);
            return null;
        });
    }

    private ActivityMonitor starMonitor() {
        ActivityMonitor monitor = addMonitor("io.github.jeremyjia.stardrift.MainActivity", null, false);
        monitors.add(monitor);
        return monitor;
    }

    private io.github.jeremyjia.stardrift.MainActivity waitForStar(ActivityMonitor monitor) throws Exception {
        Activity activity = waitForMonitorWithTimeout(monitor, 8000);
        check(activity instanceof io.github.jeremyjia.stardrift.MainActivity, "Production draw loop opened Star Drift automatically");
        await("Star Drift focused and laid out", 8000, () -> onMain(() -> {
            View view = (View) field(activity, "game");
            return activity.hasWindowFocus() && view.getWidth() > 0 && view.getHeight() > 0;
        }));
        return (io.github.jeremyjia.stardrift.MainActivity) activity;
    }

    private void awaitBrickResult(String portalId) throws Exception {
        await("Brick Zen result and foreground restoration", 8000, () -> onMain(() ->
                brickActivity.hasWindowFocus() && !brickActivity.game.engine.portalActive
                        && portalId.equals(brickActivity.game.engine.portalId)));
    }

    private void backupPreferences() {
        for (String name : PREFERENCE_FILES) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<String, ?> entry : getTargetContext().getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Set) value = new HashSet<>((Set<?>) value);
                check(value instanceof String || value instanceof Boolean || value instanceof Integer
                        || value instanceof Long || value instanceof Float || value instanceof Set, "Supported preference value type");
                copy.put(entry.getKey(), value);
            }
            originalPreferences.put(name, copy);
            result.putInt("original_keys_" + name, copy.size());
        }
        Object level = originalPreferences.get("brickzen").get("zen_level");
        Object best = originalPreferences.get("brickzen").get("best_zen");
        result.putString("original_zen_level", String.valueOf(level));
        result.putString("original_best_zen", String.valueOf(best));
    }

    @SuppressWarnings("unchecked")
    private void restorePreferences() {
        for (Map.Entry<String, Map<String, Object>> file : originalPreferences.entrySet()) {
            SharedPreferences preferences = getTargetContext().getSharedPreferences(file.getKey(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit().clear();
            for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                String key = entry.getKey(); Object value = entry.getValue();
                if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof Set) editor.putStringSet(key, new HashSet<>((Set<String>) value));
            }
            check(editor.commit(), "Restore preferences: " + file.getKey());
            check(preferences.getAll().equals(file.getValue()), "Every original preference entry restored: " + file.getKey());
        }
        check(originalPreferences.size() == PREFERENCE_FILES.length, "All three preference backups restored");
    }

    private void finishTestActivities() throws Exception {
        onMain(() -> {
            for (int i = createdActivities.size() - 1; i >= 0; i--) {
                Activity activity = createdActivities.get(i);
                if (!activity.isFinishing() && !activity.isDestroyed()) activity.finish();
            }
            return null;
        });
        await("All test Activities destroyed before preference restoration", 8000,
                () -> destroyedActivities.containsAll(createdActivities));
        waitForIdleSync();
    }

    private void screenshot(View view, String name) throws Exception {
        Bitmap bitmap = onMain(() -> {
            check(view.getWidth() > 0 && view.getHeight() > 0, "Screenshot view is laid out");
            Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(image));
            return image;
        });
        try (FileOutputStream output = new FileOutputStream(new File(verificationDirectory, name))) {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "PNG screenshot encoded");
        } finally { bitmap.recycle(); }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private static View findDescription(View root, String description) {
        if (description.equals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findDescription(group.getChildAt(i), description);
                if (match != null) return match;
            }
        }
        return null;
    }

    private <T> T onMain(Callable<T> action) throws Exception {
        AtomicReference<T> answer = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try { answer.set(action.call()); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Main-thread test action failed", failure.get());
        return answer.get();
    }

    private void await(String description, long timeoutMs, Callable<Boolean> condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.call()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out: " + description);
    }

    private void progress(String text) {
        Bundle status = new Bundle(); status.putString("stream", text + "\n"); sendStatus(1, status);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FrozenLevel {
        final String portalId;
        final int level, lives, score, destroyed;
        final float time, charge, wideUntil;
        final float[][] balls;
        final float[][] obstacles;
        final int[] brickHp;
        FrozenLevel(GameEngine engine) {
            portalId = engine.portalId; level = engine.level; lives = engine.lives; score = engine.score;
            destroyed = engine.destroyed; time = engine.time; charge = engine.charge; wideUntil = engine.wideUntil;
            balls = new float[engine.balls.size()][4];
            for (int i = 0; i < balls.length; i++) {
                GameEngine.Ball ball = engine.balls.get(i);
                balls[i] = new float[]{ball.x, ball.y, ball.vx, ball.vy};
            }
            brickHp = new int[engine.bricks.size()];
            for (int i = 0; i < brickHp.length; i++) brickHp[i] = engine.bricks.get(i).hp;
            obstacles = new float[engine.obstacles.size()][6];
            for (int i = 0; i < obstacles.length; i++) {
                GameEngine.Brick gate = engine.obstacles.get(i);
                obstacles[i] = new float[]{gate.x, gate.y, gate.w, gate.h, gate.hp, gate.hitAt};
            }
        }
        void verifyPhysicalState(GameEngine engine) {
            check(level == engine.level && lives == engine.lives && time == engine.time && destroyed == engine.destroyed,
                    "Original level, lives, clock and destroyed count unchanged");
            check(balls.length == engine.balls.size() && brickHp.length == engine.bricks.size(), "Original entity counts unchanged");
            for (int i = 0; i < balls.length; i++) {
                GameEngine.Ball ball = engine.balls.get(i);
                check(ball.x == balls[i][0] && ball.y == balls[i][1] && ball.vx == balls[i][2] && ball.vy == balls[i][3],
                        "Original ball position and velocity unchanged");
            }
            for (int i = 0; i < brickHp.length; i++) check(engine.bricks.get(i).hp == brickHp[i], "Original brick HP unchanged");
            check(engine.obstacles.size() == obstacles.length, "Rebound obstacle count preserved");
            for (int i = 0; i < obstacles.length; i++) {
                GameEngine.Brick gate = engine.obstacles.get(i);
                float[] saved = obstacles[i];
                check(gate.x == saved[0] && gate.y == saved[1] && gate.w == saved[2]
                        && gate.h == saved[3] && gate.hp == saved[4] && gate.hitAt == saved[5],
                        "Rebound obstacle geometry and feedback preserved");
            }
        }
    }
}
