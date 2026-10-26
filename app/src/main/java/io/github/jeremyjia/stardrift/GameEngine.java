package io.github.jeremyjia.stardrift;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Deterministic, Android-independent simulation. Coordinates are logical screen units. */
public final class GameEngine {
    public enum Phase { PLAYING, UPGRADE, WON, LOST }
    public interface Listener { void onEvent(String name); }

    public static final int MAX_ENEMIES = 54, MAX_SHOTS = 128, MAX_GEMS = 100, MAX_PARTICLES = 280;
    public static final float RUN_SECONDS = 180f, WARNING_SECONDS = 0.7f;
    public Phase phase = Phase.PLAYING;
    public float width = 400, height = 850, time, hp, maxHp, xp, xpNext, pulseCharge, kills, score;
    public int level, combo;
    public float shipX, shipY, shipRadius = 9, invulnerable, shield;
    public boolean bossSpawned, bossDefeated;
    public boolean challengeMode;
    public float runDuration = RUN_SECONDS;
    public long runSeed;
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Shot> shots = new ArrayList<>();
    public final List<Gem> gems = new ArrayList<>();
    public final List<Particle> particles = new ArrayList<>();
    public final List<Upgrade> choices = new ArrayList<>();
    public String lastUpgrade = "";

    public static final class Enemy {
        public float x, y, r, hp, maxHp, age, hit, angle;
        public int type, id;
        public float vx, vy, attackTimer, chargeTimer, wobble;
    }
    public static final class Shot {
        public float x, y, vx, vy, r;
        public boolean hostile;
        public float damage, life;
        public int pierce, lastEnemy = -1;
    }
    public static final class Gem {
        public float x, y, r, age;
        public float value, vx, vy;
    }
    public static final class Particle {
        public float x, y, vx, vy, life, maxLife, size;
        public int color;
        public boolean ring;
    }
    public static final class Upgrade {
        public String id, title, description, tag;
        public int tier;
        Upgrade(String id, String title, String description, String tag, int tier) {
            this.id = id; this.title = title; this.description = description; this.tag = tag; this.tier = tier;
        }
    }

    private final Listener listener;
    private Random random = new Random(1);
    private float targetX, targetY, fireTimer, spawnTimer, comboTimer;
    private float damage, fireRate, magnetRadius, pulseCooldown, moveSpeed, armor, regeneration;
    private boolean targeting;
    private int projectiles, piercing, nextEnemyId;
    private final int[] upgradeRanks = new int[8];

    public GameEngine(Listener listener) { this.listener = listener; start(1); }

    public void setBounds(float w, float h) {
        width = Math.max(300, w); height = Math.max(500, h);
        shipX = clamp(shipX, 18, width - 18);
        shipY = clamp(shipY, 140, height - 110);
        targetX = clamp(targetX, 18, width - 18);
        targetY = clamp(targetY, 140, height - 110);
    }

    public void start(long seed) {
        runSeed = seed; random = new Random(seed);
        challengeMode = false; runDuration = RUN_SECONDS;
        phase = Phase.PLAYING; time = 0; hp = maxHp = 100; shield = 0;
        level = 1; xp = 0; xpNext = 7; pulseCharge = 1;
        kills = score = 0; combo = 0; comboTimer = 0;
        shipX = width / 2; shipY = height * 0.68f;
        targetX = shipX; targetY = shipY; targeting = false;
        invulnerable = 1; fireTimer = 0.1f; spawnTimer = 0.75f;
        damage = 11; fireRate = 3.6f; magnetRadius = 100; pulseCooldown = 18;
        moveSpeed = 340; armor = regeneration = 0; projectiles = 1; piercing = 0;
        nextEnemyId = 1; bossSpawned = bossDefeated = false; lastUpgrade = "";
        for (int i = 0; i < upgradeRanks.length; i++) upgradeRanks[i] = 0;
        enemies.clear(); shots.clear(); gems.clear(); particles.clear(); choices.clear();
    }

    /** Short survival encounter, suitable for returning a reward to a host game. */
    public void startChallenge(long seed, float seconds) {
        start(seed);
        challengeMode = true;
        runDuration = Float.isFinite(seconds) ? clamp(seconds, 15, 90) : 30;
        spawnTimer = 0.4f;
    }

    public float getFireRate() { return fireRate; }
    public float getDamage() { return damage; }
    public int getProjectileCount() { return projectiles; }
    public float getMagnetRadius() { return magnetRadius; }
    public float getPulseCooldown() { return pulseCooldown; }
    public int getUpgradeCount() { return level - 1; }
    public int getRank(String id) { int index = upgradeIndex(id); return index < 0 ? 0 : upgradeRanks[index]; }

    public void setTarget(float x, float y) {
        targetX = clamp(x, 18, width - 18); targetY = clamp(y, 140, height - 110); targeting = true;
    }
    public void releaseTarget() { targeting = false; }

    public void update(float dt) {
        if (phase != Phase.PLAYING || !Float.isFinite(dt) || dt <= 0) return;
        // A frame hitch never advances the simulation by an unplayable amount.
        dt = Math.min(dt, 0.05f);
        time += dt; invulnerable = Math.max(0, invulnerable - dt);
        comboTimer -= dt; if (comboTimer <= 0) combo = 0;
        hp = Math.min(maxHp, hp + regeneration * dt);
        pulseCharge = Math.min(1, pulseCharge + dt / pulseCooldown);
        if (targeting) {
            float dx = targetX - shipX, dy = targetY - shipY, distance = length(dx, dy);
            if (distance > 0.01f) {
                float travel = Math.min(distance, moveSpeed * dt);
                shipX += dx / distance * travel; shipY += dy / distance * travel;
            }
        }
        spawnTimer -= dt;
        if (spawnTimer <= 0 && enemies.size() < MAX_ENEMIES) {
            spawnEnemy(-1);
            if (time > 75 && random.nextFloat() < 0.35f && enemies.size() < MAX_ENEMIES) spawnEnemy(-1);
            spawnTimer = Math.max(0.46f, 1.75f - time * 0.008f) * (bossSpawned && !bossDefeated ? 1.4f : 1);
            if (challengeMode) spawnTimer *= 0.72f;
        }
        if (!challengeMode && !bossSpawned && time >= 150) { spawnEnemy(3); bossSpawned = true; event("boss"); }
        updateEnemies(dt);
        if (phase != Phase.PLAYING) return;
        fireTimer -= dt;
        if (fireTimer <= 0) {
            Enemy target = nearestEnemy();
            if (target != null) { fireAt(target); fireTimer = 1 / fireRate; }
            else fireTimer = 0;
        }
        updateShots(dt);
        if (phase != Phase.PLAYING) return;
        updateGems(dt);
        updateParticles(dt);
        if (phase == Phase.PLAYING && time >= runDuration && (challengeMode || bossDefeated)) {
            if (challengeMode) time = runDuration;
            phase = Phase.WON; score += 2000 + hp * 10; event("win");
        }
    }

    private void spawnEnemy(int requestedType) {
        if (enemies.size() >= MAX_ENEMIES) {
            if (requestedType == 3) enemies.remove(0); // The final encounter must never be skipped at the cap.
            else return;
        }
        Enemy e = new Enemy();
        e.id = nextEnemyId++; e.type = requestedType;
        if (e.type < 0) {
            float roll = random.nextFloat();
            e.type = time > 42 && roll < 0.18f ? 2 : (time > 18 && roll < 0.43f ? 1 : 0);
        }
        e.r = e.type == 3 ? 31 : e.type == 2 ? 17 : e.type == 1 ? 11 : 10;
        e.maxHp = e.type == 3 ? 610 : e.type == 2 ? 56 + time * 0.2f : e.type == 1 ? 21 + time * 0.08f : 11 + time * 0.045f;
        e.hp = e.maxHp; e.attackTimer = 1.2f + random.nextFloat() * 1.5f;
        e.wobble = random.nextFloat() * 6.28f; e.chargeTimer = 1.4f;
        if (e.type == 3) { e.x = width / 2; e.y = 174; }
        else {
            for (int attempt = 0; attempt < 8; attempt++) {
                int side = random.nextInt(time < 12 ? 1 : 3);
                if (side == 0) { e.x = 24 + random.nextFloat() * (width - 48); e.y = 145; }
                else { e.x = side == 1 ? 14 : width - 14; e.y = 170 + random.nextFloat() * (height - 340); }
                if (distanceSq(e.x, e.y, shipX, shipY) > 110 * 110) break;
            }
        }
        enemies.add(e);
    }

    private void updateEnemies(float dt) {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i); e.age += dt; e.hit = Math.max(0, e.hit - dt);
            float dx = shipX - e.x, dy = shipY - e.y, distance = Math.max(0.1f, length(dx, dy));
            e.angle = (float) Math.atan2(dy, dx);
            if (e.age < WARNING_SECONDS) continue;
            float speed;
            if (e.type == 3) {
                float desiredX = width / 2 + (float) Math.sin(e.age * 0.6f) * width * 0.28f;
                e.x += (desiredX - e.x) * dt * 1.4f;
                e.y += (174 + (float) Math.sin(e.age * 0.9f) * 25 - e.y) * dt;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    for (int b = 0; b < 9; b++) enemyShot(e, e.age * 0.3f + b * 6.283185f / 9, 92, 12);
                    enemyShot(e, e.angle, 150, 15);
                    e.attackTimer = e.hp < e.maxHp * 0.4f ? 1 : 1.4f;
                }
            } else if (e.type == 1) {
                e.chargeTimer -= dt;
                if (e.chargeTimer < 0) {
                    e.vx = dx / distance * 160; e.vy = dy / distance * 160; e.chargeTimer = 2.4f;
                }
                if (e.chargeTimer > 1.85f) { e.x += e.vx * dt; e.y += e.vy * dt; }
                else { e.x += dx / distance * dt * 24; e.y += dy / distance * dt * 24; }
            } else {
                speed = e.type == 2 ? 20 : 30 + Math.min(22, time * 0.12f);
                e.x += dx / distance * speed * dt; e.y += dy / distance * speed * dt;
                if (e.type == 2) {
                    e.attackTimer -= dt;
                    if (e.attackTimer <= 0) { enemyShot(e, e.angle, 98, 12); e.attackTimer = 2.6f; }
                }
            }
            e.x = clamp(e.x, -20, width + 20); e.y = clamp(e.y, 135, height - 96);
            if (distanceSq(e.x, e.y, shipX, shipY) < square(e.r + shipRadius)) {
                takeDamage(e.type == 3 ? 25 : e.type == 2 ? 19 : 14);
                if (phase == Phase.LOST) return;
                if (e.type != 3) {
                    e.x -= dx / distance * 25; e.y -= dy / distance * 25; damageEnemy(i, 7);
                }
            }
        }
    }

    private Enemy nearestEnemy() {
        Enemy best = null; float bestDistance = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.age < WARNING_SECONDS) continue;
            float d = distanceSq(e.x, e.y, shipX, shipY);
            if (d < bestDistance) { bestDistance = d; best = e; }
        }
        return best;
    }

    private void fireAt(Enemy target) {
        float aim = (float) Math.atan2(target.y - shipY, target.x - shipX);
        for (int i = 0; i < projectiles && shots.size() < MAX_SHOTS; i++) {
            float angle = aim + (i - (projectiles - 1) / 2f) * 0.11f;
            Shot s = new Shot(); s.x = shipX; s.y = shipY - 3; s.r = 3;
            s.vx = (float) Math.cos(angle) * 385; s.vy = (float) Math.sin(angle) * 385;
            s.damage = damage; s.life = 2.6f; s.pierce = piercing;
            shots.add(s);
        }
        event("shoot");
    }

    private void enemyShot(Enemy e, float angle, float speed, float damage) {
        if (shots.size() >= MAX_SHOTS) return;
        Shot s = new Shot(); s.x = e.x; s.y = e.y; s.r = 4; s.hostile = true;
        s.vx = (float) Math.cos(angle) * speed; s.vy = (float) Math.sin(angle) * speed;
        s.life = 8; s.damage = damage; shots.add(s);
    }

    private void updateShots(float dt) {
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i); s.life -= dt;
            s.x += s.vx * dt; s.y += s.vy * dt;
            boolean remove = s.life <= 0 || s.x < -30 || s.x > width + 30 || s.y < 110 || s.y > height - 75;
            if (!remove && s.hostile) {
                if (distanceSq(s.x, s.y, shipX, shipY) < square(s.r + shipRadius - 2)) {
                    remove = true; takeDamage(s.damage); if (phase == Phase.LOST) return;
                }
            } else if (!remove) {
                for (int n = enemies.size() - 1; n >= 0; n--) {
                    Enemy e = enemies.get(n);
                    if (e.age < WARNING_SECONDS || e.id == s.lastEnemy) continue;
                    if (distanceSq(s.x, s.y, e.x, e.y) < square(s.r + e.r)) {
                        s.lastEnemy = e.id; damageEnemy(n, s.damage);
                        if (s.pierce-- <= 0) remove = true;
                        break;
                    }
                }
            }
            if (remove) shots.remove(i);
        }
    }

    private void damageEnemy(int index, float amount) {
        Enemy e = enemies.get(index); e.hp -= amount; e.hit = 0.12f;
        if (e.hp > 0) return;
        enemies.remove(index); kills++; combo++; comboTimer = 3;
        score += (e.type == 3 ? 1500 : e.type == 2 ? 80 : 30) + Math.min(combo, 10) * 2;
        pulseCharge = Math.min(1, pulseCharge + 0.015f);
        int count = e.type == 3 ? 18 : e.type == 2 ? 4 : 1;
        for (int i = 0; i < count && gems.size() < MAX_GEMS; i++) {
            Gem g = new Gem(); g.x = e.x; g.y = e.y; g.r = e.type == 3 ? 4 : 3;
            g.value = e.type == 3 ? 3 : 2; g.vx = (random.nextFloat() - 0.5f) * 70; g.vy = (random.nextFloat() - 0.5f) * 70;
            gems.add(g);
        }
        if (e.type == 3) { bossDefeated = true; hp = Math.min(maxHp, hp + 30); event("boss_down"); }
        burst(e.x, e.y, e.type == 3 ? 35 : 12, 0xfff9b583, false);
        event("kill");
    }

    private void takeDamage(float damageTaken) {
        if (invulnerable > 0 || phase != Phase.PLAYING) return;
        float actual = damageTaken * (1 - armor);
        if (shield > 0) { float absorbed = Math.min(shield, actual); shield -= absorbed; actual -= absorbed; }
        hp = Math.max(0, hp - actual); invulnerable = 0.95f; combo = 0;
        burst(shipX, shipY, 16, 0xffff7f93, false); event("hit");
        if (hp <= 0) { phase = Phase.LOST; burst(shipX, shipY, 35, 0xffb3f3de, true); event("lose"); }
    }

    private void updateGems(float dt) {
        for (int i = gems.size() - 1; i >= 0; i--) {
            Gem g = gems.get(i); g.age += dt;
            float dx = shipX - g.x, dy = shipY - g.y, dist = length(dx, dy);
            // Old pickups slowly drift toward the pilot, preventing permanently missed levels.
            if (dist < magnetRadius || g.age > 11) {
                float speed = 120 + (magnetRadius - Math.min(dist, magnetRadius)) * 2;
                if (dist > 0.1f) { g.x += dx / dist * speed * dt; g.y += dy / dist * speed * dt; }
            } else { g.x += g.vx * dt; g.y += g.vy * dt; g.vx *= 1 - dt * 3; g.vy *= 1 - dt * 3; }
            if (dist < shipRadius + 8) {
                xp += g.value; score += g.value * 4; gems.remove(i);
                if (xp >= xpNext && !(challengeMode && time >= runDuration)) { prepareUpgrade(); return; }
            } else if (g.age > 32) gems.remove(i);
        }
    }

    private void prepareUpgrade() {
        xp -= xpNext; level++; xpNext = 7 + (level - 1) * 4;
        choices.clear(); List<Upgrade> pool = new ArrayList<>();
        pool.add(upgrade(0, "rapid", "流光引擎", "射速提升 25%", "射速"));
        pool.add(upgrade(1, "power", "高能弹芯", "弹丸伤害提升 30%", "火力"));
        if (projectiles < 4) pool.add(upgrade(2, "multi", "双子阵列", "每次多发射 1 枚弹丸", "弹幕"));
        pool.add(upgrade(3, "magnet", "引力之心", "拾取范围 +35%，修复 15 点护盾", "收集"));
        pool.add(upgrade(4, "repair", "星舰装甲", "生命上限 +20，立即恢复 40", "生存"));
        pool.add(upgrade(5, "pulse", "回声反应堆", "脉冲冷却缩短 20%，立即充满", "脉冲"));
        if (piercing < 3) pool.add(upgrade(6, "pierce", "穿星长矛", "弹丸额外穿透 1 个目标", "穿透"));
        if (regeneration < 2.5f) pool.add(upgrade(7, "regen", "自愈星尘", "每秒恢复 0.5 生命，减伤 +5%", "修复"));
        Collections.shuffle(pool, random);
        choices.addAll(pool.subList(0, Math.min(3, pool.size())));
        phase = Phase.UPGRADE; targeting = false; event("upgrade");
    }

    private Upgrade upgrade(int rankIndex, String id, String title, String description, String tag) {
        return new Upgrade(id, title, description, tag, upgradeRanks[rankIndex] + 1);
    }

    public void chooseUpgrade(int index) {
        if (phase != Phase.UPGRADE || index < 0 || index >= choices.size()) return;
        Upgrade selected = choices.get(index); int rankIndex = upgradeIndex(selected.id);
        if (rankIndex < 0) return;
        upgradeRanks[rankIndex]++; lastUpgrade = selected.title;
        switch (selected.id) {
            case "rapid": fireRate = Math.min(18, fireRate * 1.25f); break;
            case "power": damage *= 1.30f; break;
            case "multi": projectiles = Math.min(4, projectiles + 1); break;
            case "magnet": magnetRadius = Math.min(420, magnetRadius * 1.35f); shield = Math.min(60, shield + 15); break;
            case "repair": maxHp += 20; hp = Math.min(maxHp, hp + 40); break;
            case "pulse": pulseCooldown = Math.max(5, pulseCooldown * 0.80f); pulseCharge = 1; break;
            case "pierce": piercing++; break;
            case "regen": regeneration += 0.5f; armor = Math.min(0.4f, armor + 0.05f); break;
            default: break;
        }
        phase = Phase.PLAYING; invulnerable = Math.max(1.2f, invulnerable); choices.clear();
        burst(shipX, shipY, 15, 0xffa6f5da, true); event("select");
    }

    private static int upgradeIndex(String id) {
        switch (id) {
            case "rapid": return 0; case "power": return 1; case "multi": return 2; case "magnet": return 3;
            case "repair": return 4; case "pulse": return 5; case "pierce": return 6; case "regen": return 7;
            default: return -1;
        }
    }

    public boolean pulse() {
        if (phase != Phase.PLAYING || pulseCharge < 0.999f) return false;
        pulseCharge = 0; invulnerable = Math.max(invulnerable, 0.6f);
        float radius = 235;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (distanceSq(e.x, e.y, shipX, shipY) < radius * radius) damageEnemy(i, Math.max(65, damage * 4));
        }
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i);
            if (s.hostile && distanceSq(s.x, s.y, shipX, shipY) < radius * radius) shots.remove(i);
        }
        burst(shipX, shipY, 26, 0xffa6f5da, true); event("pulse"); return true;
    }

    private void burst(float x, float y, int count, int color, boolean ring) {
        if (ring && particles.size() < MAX_PARTICLES) {
            Particle p = new Particle(); p.x = x; p.y = y; p.life = p.maxLife = 0.65f;
            p.size = 15; p.vx = 350; p.ring = true; p.color = color; particles.add(p);
        }
        for (int i = 0; i < count && particles.size() < MAX_PARTICLES; i++) {
            float angle = random.nextFloat() * 6.283185f, speed = 25 + random.nextFloat() * 100;
            Particle p = new Particle(); p.x = x; p.y = y;
            p.vx = (float) Math.cos(angle) * speed; p.vy = (float) Math.sin(angle) * speed;
            p.maxLife = p.life = 0.25f + random.nextFloat() * 0.55f; p.size = 1 + random.nextFloat() * 2.4f;
            p.color = color; particles.add(p);
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i); p.life -= dt;
            if (p.life <= 0) { particles.remove(i); continue; }
            if (p.ring) p.size += p.vx * dt;
            else { p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 1 - dt * 1.3f; p.vy *= 1 - dt * 1.3f; }
        }
    }

    private void event(String name) { if (listener != null) listener.onEvent(name); }
    private static float clamp(float value, float low, float high) { return Math.max(low, Math.min(high, value)); }
    private static float length(float x, float y) { return (float) Math.sqrt(x * x + y * y); }
    private static float square(float value) { return value * value; }
    private static float distanceSq(float x, float y, float x2, float y2) { return square(x - x2) + square(y - y2); }
}
