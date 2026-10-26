package dev.brickzen.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
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
 * Run only on an unlocked, foreground device; the challenge uses 30 seconds of real gameplay.
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
        screenshot(brickActivity.game, "brick-before.png");

        ActivityMonitor monitor = starMonitor();
        FrozenLevel frozen = triggerMystery(brickActivity);
        io.github.jeremyjia.stardrift.MainActivity star = waitForStar(monitor);
        verifyPortalStart(star, frozen);
        screenshot((View) field(star, "game"), "star-challenge.png");
        io.github.jeremyjia.stardrift.GameView starView = (io.github.jeremyjia.stardrift.GameView) field(star, "game");
        io.github.jeremyjia.stardrift.GameEngine starEngine = (io.github.jeremyjia.stardrift.GameEngine) field(starView, "game");
        check(onMain(() -> starEngine.challengeMode && starEngine.runDuration == 30), "Portal launches a 30-second challenge");

        long deadline = SystemClock.elapsedRealtime() + 45000;
        long nextProgress = 0;
        boolean won = false;
        int upgradesSelected = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            Integer state = onMain(() -> {
                if (starEngine.phase == io.github.jeremyjia.stardrift.GameEngine.Phase.UPGRADE) {
                    starEngine.chooseUpgrade(0);
                    starView.invalidate();
                    return 1;
                }
                if (starEngine.phase == io.github.jeremyjia.stardrift.GameEngine.Phase.WON) return 2;
                if (starEngine.phase == io.github.jeremyjia.stardrift.GameEngine.Phase.LOST) return 3;
                return 0;
            });
            if (state == 1) upgradesSelected++;
            if (state == 2) { won = true; break; }
            check(state != 3, "Stationary pilot unexpectedly died during the short introductory challenge");
            if (SystemClock.elapsedRealtime() >= nextProgress) {
                String status = onMain(() -> "Star challenge running: " + Math.round(starEngine.time)
                        + "/30 s, level=" + starEngine.level + ", hp=" + Math.round(starEngine.hp));
                progress(status); nextProgress = SystemClock.elapsedRealtime() + 5000;
            }
            SystemClock.sleep(100);
        }
        check(won, "Real draw-loop challenge did not finish within 45 seconds");
        check(onMain(() -> !starEngine.bossSpawned && starEngine.time == 30), "Challenge completed without a boss at exactly 30 seconds");
        result.putInt("upgrades_selected", upgradesSelected);
        result.putFloat("challenge_seconds", onMain(() -> starEngine.time));
        screenshot(starView, "star-complete.png");
        int expectedReward = onMain(() -> Math.min(3000, Math.round(starEngine.score * .5f) + 500));
        onMain(() -> { tapGameButton(starView, "finishBonus"); return null; });
        awaitBrickResult(frozen.portalId);
        onMain(() -> {
            frozen.verifyPhysicalState(brickActivity.game.engine);
            GameEngine engine = brickActivity.game.engine;
            check(engine.score == frozen.score + expectedReward, "Displayed reward returned to the original level");
            check(!engine.portalActive && !engine.portalPending && engine.state == GameEngine.State.PAUSED, "Returned level remains paused");
            check(engine.wideUntil >= engine.time + 14 && engine.charge == 1, "Survival awards WIDE and full charge");
            Intent duplicate = new Intent().putExtra("portal_id", frozen.portalId).putExtra("bonus_score", expectedReward)
                    .putExtra("survived", true).putExtra("cancelled", false);
            brickActivity.onActivityResult(MainActivity.STAR_PORTAL_REQUEST, Activity.RESULT_OK, duplicate);
            check(engine.score == frozen.score + expectedReward, "Duplicate activity result cannot award twice");
            return null;
        });
        screenshot(brickActivity.game, "brick-return.png");
        result.putBoolean("success_round_trip", true);
        result.putInt("awarded_score", expectedReward);
        result.putBoolean("duplicate_reward_rejected", true);
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
            frozen.verifyPhysicalState(engine);
            check(engine.score == frozen.score && !engine.portalActive && engine.state == GameEngine.State.PAUSED,
                    "Cancellation keeps original score and paused level");
            check(engine.charge == frozen.charge && engine.wideUntil == frozen.wideUntil, "Cancellation grants no power-up");
            return null;
        });
        result.putBoolean("cancel_round_trip", true);
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

    private static void tapGameButton(View view, String action) throws Exception {
        for (Object button : (List<?>) field(view, "buttons")) {
            if (!action.equals(field(button, "action"))) continue;
            RectF bounds = (RectF) field(button, "bounds");
            float scale = view.getWidth() / 400f;
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                    bounds.centerX() * scale, bounds.centerY() * scale, 0);
            MotionEvent up = MotionEvent.obtain(now, now + 25, MotionEvent.ACTION_UP,
                    bounds.centerX() * scale, bounds.centerY() * scale, 0);
            try { view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); }
            finally { down.recycle(); up.recycle(); }
            return;
        }
        throw new AssertionError("Expected visible game button: " + action);
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
        }
    }
}
