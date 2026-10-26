import io.github.jeremyjia.stardrift.GameEngine;

/** Headless tuning probe; intentionally separate from correctness assertions. */
public final class BalanceProbe {
    public static void main(String[] args) {
        if(args.length>0 && args[0].equals("challenge")){
            for(int seed=1;seed<=16;seed++)challenge(seed);
            return;
        }
        for (boolean moving : new boolean[] {false, true}) {
            System.out.println(moving ? "CIRCLE + PULSE + AUTO UPGRADE" : "STATIONARY + AUTO UPGRADE, NO PULSE");
            for (int seed = 1; seed <= 8; seed++) run(seed, moving, true);
        }
        for (boolean moving : new boolean[] {false, true}) {
            System.out.println(moving ? "CIRCLE + PULSE + FIRST OFFER" : "STATIONARY + FIRST OFFER, NO PULSE");
            for (int seed = 1; seed <= 8; seed++) run(seed, moving, false);
        }
    }
    private static void challenge(int seed){
        GameEngine g=new GameEngine(null);g.setBounds(400,890);g.startChallenge(seed,30);
        float firstUpgrade=-1;int maxEnemies=0;
        for(int frame=0;frame<60*31 && g.phase==GameEngine.Phase.PLAYING;frame++){
            float angle=g.time*1.3f;g.setTarget(200+(float)Math.sin(angle)*140,g.height*.55f+(float)Math.cos(angle)*180);
            if(g.pulseCharge>=.999f && g.enemies.size()>3)g.pulse();
            g.update(1f/60);if(firstUpgrade<0 && g.level>1)firstUpgrade=g.time;maxEnemies=Math.max(maxEnemies,g.enemies.size());
        }
        System.out.printf("seed=%d firstUpgrade=%.1fs phase=%s end=%.1fs hp=%.0f level=%d kills=%.0f maxEnemy=%d%n",seed,firstUpgrade,g.phase,g.time,g.hp,g.level,g.kills,maxEnemies);
    }
    private static void run(int seed, boolean moving, boolean optimalUpgrades) {
        GameEngine g = new GameEngine(null); g.setBounds(400, 890); g.start(seed);
        float firstUpgrade = -1, maxGemAge = 0, maxBossHp = 0;
        int maxEnemies = 0, maxGems = 0, maxEdgeEnemies = 0;
        int frames = 0;
        while (g.time < 240 && frames++ < 60 * 300 && g.phase != GameEngine.Phase.WON && g.phase != GameEngine.Phase.LOST) {
            if (g.phase == GameEngine.Phase.UPGRADE) {
                if (firstUpgrade < 0) firstUpgrade = g.time;
                g.chooseUpgrade(optimalUpgrades ? choose(g) : 0);
            }
            if (moving) {
                float angle = g.time * 0.7f;
                g.setTarget(200 + (float) Math.sin(angle) * 140, 465 + (float) Math.cos(angle) * 245);
                if (g.pulseCharge > .999f && !g.enemies.isEmpty()) g.pulse();
            }
            g.update(1f / 60);
            maxEnemies = Math.max(maxEnemies, g.enemies.size()); maxGems = Math.max(maxGems, g.gems.size());
            int edgeEnemies = 0;
            for (GameEngine.Gem gem : g.gems) maxGemAge = Math.max(maxGemAge, gem.age);
            for (GameEngine.Enemy e : g.enemies) {
                if (e.type == 3) maxBossHp = Math.max(maxBossHp, e.hp);
                if (e.x <= 0 || e.x >= g.width || e.y >= g.height - 97) edgeEnemies++;
            }
            maxEdgeEnemies = Math.max(maxEdgeEnemies, edgeEnemies);
        }
        float bossHp = -1; for (GameEngine.Enemy e : g.enemies) if (e.type == 3) bossHp = e.hp;
        System.out.printf("seed=%d firstUpgrade=%.1fs end=%.1fs phase=%s hp=%.1f lv=%d kills=%.0f boss=%s/%.0f maxEnemy=%d maxGem=%d maxGemAge=%.1f maxEdge=%d%n",
            seed, firstUpgrade, g.time, g.phase, g.hp, g.level, g.kills, g.bossDefeated, bossHp, maxEnemies, maxGems, maxGemAge, maxEdgeEnemies);
    }
    private static int choose(GameEngine g) {
        float best = -999; int selected = 0;
        for (int i = 0; i < g.choices.size(); i++) {
            String id = g.choices.get(i).id; float score;
            switch (id) {
                case "multi": score = 6; break;
                case "rapid": score = 5; break;
                case "power": score = 5; break;
                case "pierce": score = 4; break;
                case "repair": score = g.hp / g.maxHp < .55f ? 9 : 1; break;
                case "regen": score = g.hp < g.maxHp ? 3.8f : 1.5f; break;
                case "magnet": score = 2; break;
                case "pulse": score = 3; break;
                default: score = 0;
            }
            score -= g.getRank(id) * .4f;
            if (score > best) { best = score; selected = i; }
        }
        return selected;
    }
}
