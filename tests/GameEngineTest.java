import io.github.jeremyjia.stardrift.GameEngine;
import java.util.HashSet;
import java.lang.reflect.Field;

/** Run with assertions enabled; no Android runtime or dependencies. */
public final class GameEngineTest {
    public static void main(String[] args) throws Exception {
        movementAndPause(); upgradeChoice(); entityBounds(); terminalStates(); deterministicRun(); bossAtEntityCap();
        waveObjectives(); noTimeLimit(); challengePauseAndDeath(); challengeBoundsAndReset(); challengeAutoUpgrade();
        continuousEncounter();
        System.out.println("PASS: movement, upgrades, bounded entities, deterministic replay, objective waves, final boss, unlimited clock, auto-upgrade steering, terminal states");
    }
    private static void continuousEncounter() {
        GameEngine g=new GameEngine(null);g.startEncounter(48,12);g.hp=g.maxHp=100000;g.invulnerable=100000;
        for(int frame=0;frame<60*240&&g.encounterKills<300;frame++){
            shootVisibleEnemies(g);g.update(1f/60);
            check(g.phase==GameEngine.Phase.PLAYING&&!g.bossSpawned,"random objective mode never ends at a fixed boss or wave");
        }
        check(g.encounterKills>=300&&g.getWave()>5&&g.encounterScore>0,"encounters progress beyond both old finite enemy budgets");
        g.encounterKills=2147483655L;g.encounterScore=2147483655L;
        GameEngine.Enemy e=new GameEngine.Enemy();e.hp=e.maxHp=1;e.x=g.shipX;e.y=g.shipY-30;e.age=1;e.id=99999;g.enemies.add(e);
        shootVisibleEnemies(g);g.update(.016f);
        check(g.encounterKills>2147483655L&&g.encounterScore>2147483655L,"encounter counters keep integer precision beyond int/float counters");
        GameEngine easy=new GameEngine(null),hard=new GameEngine(null);easy.startEncounter(1,1);hard.startEncounter(1,100);
        for(int i=0;i<5;i++){easy.update(.05f);hard.update(.05f);}
        check(!easy.enemies.isEmpty()&&!hard.enemies.isEmpty()&&hard.enemies.get(0).maxHp>easy.enemies.get(0).maxHp,"random difficulty scales beyond fixed presets");
        g.startChallenge(2);check(!g.encounterMode&&g.getWaveCount()==5&&g.encounterKills==0,"legacy challenge reset remains finite");
        g.start(2);check(!g.encounterMode&&g.getWaveCount()==7,"normal run reset remains finite");
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
    }
    private static void terminalStates() {
        final int[] wins = {0};
        GameEngine g = new GameEngine(name -> {if (name.equals("win")) wins[0]++;});
        g.startChallenge(5); reachFinalBoss(g);
        check(g.phase == GameEngine.Phase.PLAYING && !g.bossDefeated, "reaching boss does not win");
        for (GameEngine.Enemy e : g.enemies) if (e.type == 3) e.age = GameEngine.WARNING_SECONDS;
        shootVisibleEnemies(g); g.update(.016f);
        check(g.phase == GameEngine.Phase.WON && g.bossDefeated, "defeating final boss wins");
        float endTime = g.time, score = g.score; g.update(.05f); g.pulse();
        check(g.time == endTime && score == g.score && wins[0] == 1, "win and reward emitted only once");
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
    private static void bossAtEntityCap() throws Exception {
        GameEngine g = new GameEngine(null); g.start(5);
        Field wave = GameEngine.class.getDeclaredField("waveIndex"); wave.setAccessible(true);
        wave.setInt(g, g.getWaveCount() - 1);
        for (int i = 0; i < GameEngine.MAX_ENEMIES; i++) {
            GameEngine.Enemy e = new GameEngine.Enemy(); e.x = 30; e.y = 150; e.hp = e.maxHp = 100;
            g.enemies.add(e);
        }
        g.update(.016f); int bosses = 0;
        for (GameEngine.Enemy e : g.enemies) if (e.type == 3) bosses++;
        check(g.bossSpawned && bosses == 1 && g.enemies.size() == GameEngine.MAX_ENEMIES, "boss cannot be skipped at cap");
    }
    private static void waveObjectives() {
        for (boolean challenge : new boolean[]{false, true}) {
            GameEngine g = new GameEngine(null); if (challenge) g.startChallenge(8); else g.start(8);
            check(g.getWave() == 1 && g.getWaveKills() == 0 && g.getWaveCount() == (challenge ? 5 : 7), "fresh objective state");
            reachFinalBoss(g);
            check(g.kills == (challenge ? 84 : 198), "every finite wave must be cleared before boss");
            check(g.getWave() == g.getWaveCount() && g.isBossWave() && g.bossSpawned, "boss is final wave only");
            int bosses = 0; for (GameEngine.Enemy e : g.enemies) if (e.type == 3) bosses++;
            check(bosses == 1 && g.enemies.size() == 1, "previous wave fully cleared, one final boss");
            check(g.getWaveTarget() == 1 && g.getWaveProgress() >= 0 && g.getWaveProgress() < 1, "boss progress tracks health");
            g.time = 86400; g.update(.016f);
            check(g.phase == GameEngine.Phase.PLAYING, "living boss cannot expire into victory");
        }
    }
    private static void reachFinalBoss(GameEngine g) {
        g.hp = g.maxHp = 100000; g.invulnerable = 100000;
        int lastWave = 1, previousKills = 0, previousTarget = g.getWaveTarget();
        for (int frame = 0; frame < 60 * 240 && !g.bossSpawned; frame++) {
            if (g.phase == GameEngine.Phase.UPGRADE) g.chooseUpgrade(0);
            if (!g.isBossWave()) shootVisibleEnemies(g);
            g.update(1f / 60);
            if (g.getWave() != lastWave) {
                check(g.getWave() == lastWave + 1, "one wave at a time");
                check((int)g.kills - previousKills == previousTarget, "advance follows exact elimination quota");
                previousKills = (int)g.kills; previousTarget = g.getWaveTarget(); lastWave = g.getWave();
            }
            check(g.phase != GameEngine.Phase.LOST && g.phase != GameEngine.Phase.WON, "only boss can finish successful run");
        }
        check(g.bossSpawned, "objective driver reaches final boss");
    }
    private static void shootVisibleEnemies(GameEngine g) {
        for (GameEngine.Enemy e : g.enemies) {
            if (e.age < GameEngine.WARNING_SECONDS || g.shots.size() >= GameEngine.MAX_SHOTS) continue;
            GameEngine.Shot s = new GameEngine.Shot(); s.x = e.x; s.y = e.y; s.damage = e.hp + 1; s.r = 5; s.life = .1f;
            g.shots.add(s);
        }
    }
    private static void noTimeLimit() {
        GameEngine g = new GameEngine(null);
        for (boolean challenge : new boolean[]{false, true}) {
            if (challenge) g.startChallenge(21); else g.start(21);
            for (float previousTime : new float[]{29.99f, 179.99f, 86400}) {
                g.time = previousTime; g.update(.016f);
                check(g.phase == GameEngine.Phase.PLAYING && g.getWave() == 1 && !g.bossSpawned, "clock cannot advance objectives or end run");
            }
        }
    }
    private static void challengePauseAndDeath() {
        GameEngine g = new GameEngine(null); g.startChallenge(8); g.time = 12;
        g.phase = GameEngine.Phase.UPGRADE;
        for (int i = 0; i < 100; i++) g.update(.05f);
        check(g.time == 12 && g.phase == GameEngine.Phase.UPGRADE, "challenge pause freezes clock");
        g.startChallenge(8); g.hp = 1; g.invulnerable = 0;
        GameEngine.Shot s = new GameEngine.Shot(); s.x = g.shipX; s.y = g.shipY; s.hostile = true; s.damage = 5; s.life = 5; s.r = 4;
        g.shots.add(s); g.update(.016f);
        check(g.phase == GameEngine.Phase.LOST && g.hp == 0, "challenge can be lost during combat");
        float time = g.time; g.update(.05f); check(g.time == time, "failed challenge remains terminal");
    }
    @SuppressWarnings("deprecation") private static void challengeBoundsAndReset() {
        GameEngine g = new GameEngine(null);
        g.startChallenge(1, -1); check(g.challengeMode && Float.isInfinite(g.runDuration), "legacy duration is ignored");
        g.start(1); check(!g.challengeMode && g.getWaveCount() == 7 && !g.bossSpawned && g.getWaveKills() == 0, "ordinary run resets challenge state");
    }
    private static void challengeAutoUpgrade(){
        GameEngine g=new GameEngine(null);g.startChallenge(71);
        GameEngine.Gem gem=new GameEngine.Gem();gem.x=g.shipX;gem.y=g.shipY;gem.value=g.xpNext;g.gems.add(gem);
        g.setTarget(330,g.shipY);g.update(.016f);
        check(g.phase==GameEngine.Phase.PLAYING && g.level==2 && g.choices.isEmpty() && !g.lastUpgrade.isEmpty(),"challenge equips upgrade automatically");
        float x=g.shipX;g.update(.016f);check(g.shipX>x,"auto upgrade does not release steering");
        g.time=9999;check(g.getWave()==1,"time cannot fake next wave");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
