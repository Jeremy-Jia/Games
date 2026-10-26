import io.github.jeremyjia.stardrift.GameEngine;
import java.util.HashSet;

/** Run with assertions enabled; no Android runtime or dependencies. */
public final class GameEngineTest {
    public static void main(String[] args) {
        movementAndPause(); upgradeChoice(); entityBounds(); terminalStates(); deterministicRun(); bossAtEntityCap();
        challengeCompletion(); challengePauseAndDeath(); challengeBoundsAndReset();
        System.out.println("PASS: movement, upgrade pause/choice, entity bounds, terminal states, deterministic replay, boss at entity cap, timed challenge completion/pause/death/reset");
    }
    private static void movementAndPause() {
        GameEngine g = new GameEngine(null); g.start(77); g.setTarget(-200, 9000);
        for (int i = 0; i < 500; i++) g.update(1f / 60);
        check(g.shipX >= 18 && g.shipY <= g.height - 110, "ship bounds");
        g.phase = GameEngine.Phase.UPGRADE; float time = g.time, x = g.shipX, charge = g.pulseCharge;
        for (int i = 0; i < 100; i++) g.update(0.05f);
        check(g.time == time && g.shipX == x && g.pulseCharge == charge, "upgrade truly pauses");
        check(!g.pulse(), "cannot pulse during upgrade");
    }
    private static void upgradeChoice() {
        GameEngine g = new GameEngine(null); g.start(23);
        GameEngine.Gem gem = new GameEngine.Gem(); gem.x = g.shipX; gem.y = g.shipY; gem.value = 7; g.gems.add(gem);
        g.update(0.016f);
        check(g.phase == GameEngine.Phase.UPGRADE && g.level == 2 && g.choices.size() == 3, "upgrade triggered");
        HashSet<String> ids = new HashSet<>(); for (GameEngine.Upgrade u : g.choices) ids.add(u.id);
        check(ids.size() == 3, "nonduplicate offers");
        String selected = g.choices.get(0).id; g.chooseUpgrade(0);
        check(g.phase == GameEngine.Phase.PLAYING && g.getRank(selected) == 1 && g.choices.isEmpty(), "upgrade applied once");
        g.chooseUpgrade(0); check(g.getRank(selected) == 1, "stale choice cannot apply twice");
    }
    private static void entityBounds() {
        GameEngine g = new GameEngine(null); g.start(51); g.maxHp = g.hp = 100000;
        for (int frame = 0; frame < 60 * 220; frame++) {
            g.setTarget(200 + (float) Math.sin(frame * 0.012) * 160, 480 + (float) Math.cos(frame * 0.009) * 170);
            if (g.phase == GameEngine.Phase.UPGRADE) g.chooseUpgrade(frame % 3);
            if (frame % 1000 == 0) g.pulse(); g.update(1f / 60);
            check(g.enemies.size() <= GameEngine.MAX_ENEMIES && g.shots.size() <= GameEngine.MAX_SHOTS
                    && g.gems.size() <= GameEngine.MAX_GEMS && g.particles.size() <= GameEngine.MAX_PARTICLES, "bounded entities");
            check(Float.isFinite(g.shipX) && Float.isFinite(g.shipY), "finite positions");
        }
        check(g.bossSpawned, "boss spawned in long run");
    }
    private static void terminalStates() {
        GameEngine g = new GameEngine(null); g.start(5); g.time = 180; g.bossSpawned = g.bossDefeated = true;
        g.update(.016f); check(g.phase == GameEngine.Phase.WON, "win after final sector and boss");
        float endTime = g.time; g.update(.05f); check(g.time == endTime, "won is terminal");
        g.start(5); g.hp = 1; g.invulnerable = 0;
        GameEngine.Shot s = new GameEngine.Shot(); s.x = g.shipX; s.y = g.shipY; s.hostile = true; s.damage = 5; s.life = 5; s.r = 4;
        g.shots.add(s); g.update(.016f); check(g.phase == GameEngine.Phase.LOST && g.hp == 0, "damage ends run");
        endTime = g.time; g.update(.05f); check(g.time == endTime, "lost is terminal");
    }
    private static void deterministicRun() {
        GameEngine a = new GameEngine(null), b = new GameEngine(null); a.start(99); b.start(99);
        for (int i = 0; i < 3000; i++) {
            if (a.phase == GameEngine.Phase.UPGRADE) { a.chooseUpgrade(1); b.chooseUpgrade(1); }
            float x = 200 + (float) Math.sin(i * .02f) * 130;
            a.setTarget(x, 540); b.setTarget(x, 540); a.update(1f/60); b.update(1f/60);
        }
        check(a.hp == b.hp && a.score == b.score && a.time == b.time && a.enemies.size() == b.enemies.size(), "seeded deterministic simulation");
    }
    private static void bossAtEntityCap() {
        GameEngine g = new GameEngine(null); g.start(5); g.time = 150;
        for (int i = 0; i < GameEngine.MAX_ENEMIES; i++) {
            GameEngine.Enemy e = new GameEngine.Enemy(); e.x = 30; e.y = 150; e.hp = e.maxHp = 100;
            g.enemies.add(e);
        }
        g.update(.016f); int bosses = 0;
        for (GameEngine.Enemy e : g.enemies) if (e.type == 3) bosses++;
        check(g.bossSpawned && bosses == 1 && g.enemies.size() == GameEngine.MAX_ENEMIES, "boss cannot be skipped at cap");
    }
    private static void challengeCompletion() {
        final int[] wins = {0};
        GameEngine g = new GameEngine(name -> { if ("win".equals(name)) wins[0]++; });
        g.startChallenge(8, 30);
        for (int frame = 0; frame < 60 * 31 && g.phase != GameEngine.Phase.WON; frame++) {
            if (g.phase == GameEngine.Phase.UPGRADE) g.chooseUpgrade(0);
            g.update(1f / 60);
        }
        check(g.phase == GameEngine.Phase.WON && g.time == 30 && !g.bossSpawned, "challenge ends at duration without boss");
        check(g.level > 1 && g.kills > 0, "short challenge offers pickups and upgrade");
        float score = g.score; g.update(.05f);
        check(g.score == score && wins[0] == 1, "challenge reward emitted only once");
        g.startChallenge(9, 30); g.time = 29.99f;
        GameEngine.Gem gem = new GameEngine.Gem(); gem.x = g.shipX; gem.y = g.shipY; gem.value = g.xpNext; g.gems.add(gem);
        g.update(.016f);
        check(g.phase == GameEngine.Phase.WON && g.choices.isEmpty(), "last-frame level-up cannot block completion");
    }
    private static void challengePauseAndDeath() {
        GameEngine g = new GameEngine(null); g.startChallenge(8, 30); g.time = 12;
        g.phase = GameEngine.Phase.UPGRADE;
        for (int i = 0; i < 100; i++) g.update(.05f);
        check(g.time == 12 && g.phase == GameEngine.Phase.UPGRADE, "challenge pause freezes clock");
        g.startChallenge(8, 30); g.hp = 1; g.invulnerable = 0;
        GameEngine.Shot s = new GameEngine.Shot(); s.x = g.shipX; s.y = g.shipY; s.hostile = true; s.damage = 5; s.life = 5; s.r = 4;
        g.shots.add(s); g.update(.016f);
        check(g.phase == GameEngine.Phase.LOST && g.hp == 0, "challenge can be lost before timeout");
        float time = g.time; g.update(.05f); check(g.time == time, "failed challenge remains terminal");
    }
    private static void challengeBoundsAndReset() {
        GameEngine g = new GameEngine(null);
        g.startChallenge(1, -1); check(g.runDuration == 15, "challenge minimum duration");
        g.startChallenge(1, 1000); check(g.runDuration == 90, "challenge maximum duration");
        g.startChallenge(1, Float.NaN); check(g.runDuration == 30, "invalid challenge duration has finite default");
        g.start(1); check(!g.challengeMode && g.runDuration == 180, "ordinary run resets challenge state");
        g.time = 180; g.update(.016f);
        check(g.phase == GameEngine.Phase.PLAYING && g.bossSpawned, "ordinary run still requires boss defeat");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
