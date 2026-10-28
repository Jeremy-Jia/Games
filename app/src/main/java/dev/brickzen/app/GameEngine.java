package dev.brickzen.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/* JADX INFO: loaded from: classes.dex */
final class GameEngine implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String SNAPSHOT_PREFIX = "BRICKZEN_PORTAL_V1:";
    private static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;
    static final float LEFT = 26.0f;
    static final float RADIUS = 13.0f;
    static final float RIGHT = 974.0f;
    static final float TOP = 186.0f;
    static final float WIDTH = 1000.0f;
    final ArrayList<Ball> balls;
    State beforePause;
    final ArrayList<Brick> bricks;
    // Separate from destructible bricks: gates never enter score, power targeting or clear counts.
    ArrayList<Brick> obstacles;
    final ArrayList<Burst> bursts;
    float charge;
    boolean cleanupGranted;
    int combo;
    float cometUntil;
    final Contact contact;
    int destroyed;
    final ArrayList<Drop> drops;
    final ArrayList<Event> events;
    float height;
    float lastHit;
    int level;
    int lives;
    float nextOrbAt;
    final ArrayList<Orb> orbs;
    int orbsSpawned;
    float paddleLift;
    float paddleVelocity;
    float paddleX;
    int palette;
    int pattern;
    float prismUntil;
    final Random random;
    int score;
    float slowUntil;
    State state;
    int surpriseOffset;
    float targetX;
    float time;
    int total;
    float wideUntil;
    boolean zen;
    boolean portalPending;
    boolean portalActive;
    String portalId = "";
    int portalGame = -1, portalMetric, portalDifficulty;
    long portalTarget, portalSeed;
    private boolean portalTriggered;

    enum Kind {
        HIT,
        WALL,
        PADDLE,
        MISS,
        POWER,
        CLEAR,
        PULSE,
        CLEANUP,
        SURPRISE
    }

    enum Power {
        WIDE,
        MULTI,
        SLOW,
        COMET
    }

    enum Shape {
        ROUND,
        CAPSULE,
        DISC,
        DIAMOND,
        HEXAGON
    }

    enum State {
        READY,
        PLAYING,
        PAUSED,
        CLEARED,
        OVER
    }

    enum Surprise {
        LIGHTNING,
        NOVA,
        METEOR,
        PRISM
    }

    static final class Ball implements Serializable {
        private static final long serialVersionUID = 1L;
        int trailCount;
        int trailIndex;
        float vx;
        float vy;
        float x;
        float y;
        int previousBrick = -1;
        float lastContact = -10.0f;
        final float[] tx = new float[18];
        final float[] ty = new float[18];

        Ball(float f, float f2, float f3, float f4) {
            this.x = f;
            this.y = f2;
            this.vx = f3;
            this.vy = f4;
        }

        void trail() {
            float[] fArr = this.tx;
            int i = this.trailIndex;
            fArr[i] = this.x;
            this.ty[i] = this.y;
            this.trailIndex = (i + 1) % fArr.length;
            this.trailCount = Math.min(this.trailCount + 1, fArr.length);
        }
    }

    static final class Brick implements Serializable {
        private static final long serialVersionUID = 1L;
        boolean mystery;
        final int color;
        final float h;
        float hitAt;
        int hp;
        final int id;
        final int maxHp;
        final int row;
        final Shape shape;
        final float[] vertices;
        final float w;
        final float x;
        final float y;

        Brick(int i, int i2, float f, float f2, float f3, float f4, int i3) {
            this(i, i2, f, f2, f3, f4, i3, Shape.ROUND, i2 % 6);
        }

        Brick(int i, int i2, float f, float f2, float f3, float f4, int i3, Shape shape, int i4) {
            float[] fArr;
            this.hitAt = -10.0f;
            this.id = i;
            this.row = i2;
            this.x = f;
            this.y = f2;
            this.w = f3;
            this.h = f4;
            this.hp = i3;
            this.maxHp = i3;
            this.shape = shape;
            this.color = i4;
            if (shape == Shape.DIAMOND) {
                float f5 = f3 / 2.0f;
                float f6 = f4 / 2.0f;
                fArr = new float[]{f5, 0.0f, f3, f6, f5, f4, 0.0f, f6};
            } else if (shape == Shape.HEXAGON) {
                float f7 = 0.2f * f3;
                float f8 = 0.8f * f3;
                float f9 = f4 / 2.0f;
                fArr = new float[]{f7, 0.0f, f8, 0.0f, f3, f9, f8, f4, f7, f4, 0.0f, f9};
            } else {
                fArr = null;
            }
            this.vertices = fArr;
        }
    }

    static final class Contact implements Serializable {
        private static final long serialVersionUID = 1L;
        float depth;
        float nx;
        float ny;

        Contact() {
        }
    }

    static final class Orb implements Serializable {
        private static final long serialVersionUID = 1L;
        static final float RADIUS = 31.0f;
        boolean active = true;
        final Surprise type;
        final float x;
        final float y;

        Orb(float f, float f2, Surprise surprise) {
            this.x = f;
            this.y = f2;
            this.type = surprise;
        }
    }

    static final class Strike implements Serializable {
        private static final long serialVersionUID = 1L;
        final Brick brick;
        final float delay;
        boolean done;
        final float x;
        final float y;

        Strike(Brick brick, float f) {
            this.brick = brick;
            this.x = brick.x + (brick.w / 2.0f);
            this.y = brick.y + (brick.h / 2.0f);
            this.delay = f;
        }
    }

    static final class Burst implements Serializable {
        private static final long serialVersionUID = 1L;
        float age;
        final float duration;
        final ArrayList<Strike> strikes = new ArrayList<>();
        final Surprise type;
        final float x;
        final float y;

        Burst(Surprise surprise, float f, float f2) {
            this.type = surprise;
            this.x = f;
            this.y = f2;
            this.duration = (surprise == Surprise.METEOR || surprise == Surprise.LIGHTNING) ? 1.35f : 1.05f;
        }
    }

    static final class Drop implements Serializable {
        private static final long serialVersionUID = 1L;
        final Power power;
        final float x;
        float y;

        Drop(float f, float f2, Power power) {
            this.x = f;
            this.y = f2;
            this.power = power;
        }
    }

    static final class Event implements Serializable {
        private static final long serialVersionUID = 1L;
        final int color;
        final int combo;
        final Kind kind;
        final int maxHp;
        final Power power;
        final int remainingHp;
        final Burst surprise;
        final int value;
        final float x;
        final float y;

        Event(Kind kind, float f, float f2, int i, int i2, Power power, int i3, int i4, int i5) {
            this(kind, f, f2, i, i2, power, i3, i4, i5, null);
        }

        Event(Kind kind, float f, float f2, int i, int i2, Power power, int i3, int i4, int i5, Burst burst) {
            this.kind = kind;
            this.x = f;
            this.y = f2;
            this.value = i;
            this.color = i2;
            this.power = power;
            this.combo = i3;
            this.maxHp = i4;
            this.remainingHp = i5;
            this.surprise = burst;
        }
    }

    GameEngine() {
        this.balls = new ArrayList<>();
        this.bricks = new ArrayList<>();
        this.obstacles = new ArrayList<>();
        this.drops = new ArrayList<>();
        this.events = new ArrayList<>();
        this.orbs = new ArrayList<>();
        this.bursts = new ArrayList<>();
        this.contact = new Contact();
        this.state = State.READY;
        this.beforePause = State.READY;
        this.zen = true;
        this.level = 1;
        this.lives = 3;
        this.palette = -1;
        this.height = 2000.0f;
        this.paddleX = 500.0f;
        this.targetX = 500.0f;
        this.paddleLift = 0.22f;
        this.lastHit = -10.0f;
        this.random = new Random();
    }

    GameEngine(long j) {
        this.balls = new ArrayList<>();
        this.bricks = new ArrayList<>();
        this.obstacles = new ArrayList<>();
        this.drops = new ArrayList<>();
        this.events = new ArrayList<>();
        this.orbs = new ArrayList<>();
        this.bursts = new ArrayList<>();
        this.contact = new Contact();
        this.state = State.READY;
        this.beforePause = State.READY;
        this.zen = true;
        this.level = 1;
        this.lives = 3;
        this.palette = -1;
        this.height = 2000.0f;
        this.paddleX = 500.0f;
        this.targetX = 500.0f;
        this.paddleLift = 0.22f;
        this.lastHit = -10.0f;
        this.random = new Random(j);
    }

    float paddleY() {
        return this.height * (1.0f - this.paddleLift);
    }

    void setPaddleLift(float f) {
        this.paddleLift = clamp(f, 0.12f, 0.36f);
        if (this.state == State.READY) {
            attach();
        }
    }

    float paddleWidth() {
        return (this.zen ? 320 : 280) * (this.time < this.wideUntil ? 1.4f : 1.0f);
    }

    float speed() {
        return Math.min(1280, (this.level * 22) + 1010);
    }

    int remaining() {
        return this.total - this.destroyed;
    }

    boolean comet() {
        return this.time < this.cometUntil;
    }

    boolean slow() {
        return this.time < this.slowUntil;
    }

    boolean cleanup() {
        return remaining() > 0 && remaining() <= 5;
    }

    boolean prism() {
        return this.time < this.prismUntil;
    }

    void resize(float f) {
        this.height = Math.max(1280.0f, f);
        if (this.state == State.READY) {
            attach();
        }
    }

    void start(int i, boolean z, int i2) {
        this.portalPending = false;
        this.portalActive = false;
        this.portalTriggered = false;
        this.portalId = "";
        this.portalGame = -1; this.portalMetric = this.portalDifficulty = 0;
        this.portalTarget = this.portalSeed = 0;
        this.level = Math.max(1, i);
        this.zen = z;
        this.score = i2;
        this.lives = 3;
        this.combo = 0;
        this.time = 0.0f;
        this.lastHit = -10.0f;
        this.charge = 0.0f;
        this.prismUntil = 0.0f;
        this.cometUntil = 0.0f;
        this.slowUntil = 0.0f;
        this.wideUntil = 0.0f;
        this.cleanupGranted = false;
        this.targetX = 500.0f;
        this.paddleX = 500.0f;
        this.paddleVelocity = 0.0f;
        this.destroyed = 0;
        this.balls.clear();
        this.bricks.clear();
        this.obstacles.clear();
        this.drops.clear();
        this.events.clear();
        this.orbs.clear();
        this.bursts.clear();
        this.orbsSpawned = 0;
        generateLayout();
        selectMysteryBrick();
        this.total = this.bricks.size();
        this.state = State.READY;
        this.beforePause = State.READY;
        this.surpriseOffset = this.random.nextInt(3);
        spawnOrb(true);
        spawnOrb(false);
        this.nextOrbAt = 4.0f;
        this.balls.add(new Ball(this.paddleX, (paddleY() - RADIUS) - 5.0f, 0.0f, 0.0f));
    }

    private void selectMysteryBrick() {
        int frontRow = Integer.MIN_VALUE, backRow = Integer.MAX_VALUE;
        for (Brick brick : this.bricks) {
            brick.mystery = false;
            frontRow = Math.max(frontRow, brick.row);
            backRow = Math.min(backRow, brick.row);
        }
        // Sample the interior uniformly, leaving the first row reached by the ball ordinary.
        // A one-row layout can still contain a portal, rather than losing the feature entirely.
        Brick selected = null;
        int candidates = 0;
        for (Brick brick : this.bricks) {
            if (brick.hp > 0 && (backRow == frontRow || brick.row < frontRow)
                    && this.random.nextInt(++candidates) == 0) {
                selected = brick;
            }
        }
        if (selected != null) selected.mystery = true;
    }

    private void spawnOrb(boolean z) {
        float fNextFloat;
        float f;
        float fMax = 250.0f;
        for (Brick brick : this.bricks) {
            if (brick.hp > 0) {
                fMax = Math.max(fMax, brick.y + brick.h);
            }
        }
        float fMin = Math.min(fMax + 82.0f + (z ? 0 : 38), Math.min(paddleY() - 105.0f, (this.height * 0.64f) - 90.0f));
        if (z) {
            fNextFloat = (((paddleY() - RADIUS) - 5.0f) - fMin) * 0.2472f;
            f = 500.0f;
        } else {
            fNextFloat = this.random.nextFloat() * 170.0f;
            f = 180.0f;
        }
        float orbX = safeOrbX(clamp(fNextFloat + f, 110.0f, 860.0f), fMin);
        this.orbs.add(new Orb(orbX, fMin, this.orbsSpawned % 2 == 0 ? Surprise.LIGHTNING : Surprise.values()[((this.surpriseOffset + (this.orbsSpawned / 2)) % 3) + 1]));
        this.orbsSpawned++;
    }

    private float safeOrbX(float preferred, float y) {
        if (orbPositionOpen(preferred, y)) return preferred;
        // Search sideways first, preserving the safe height above the raised paddle.
        int first = Math.round((preferred - 110) / 32.5f);
        for (int offset = 1; offset <= 25; offset++) {
            float candidate = 110 + ((first + offset) % 25) * 32.5f;
            if (orbPositionOpen(candidate, y)) return candidate;
        }
        return preferred; // Unreachable with at most three gates and one other active orb.
    }

    private boolean orbPositionOpen(float x, float y) {
        for (Brick gate : this.obstacles) {
            float dx = x - clamp(x, gate.x, gate.x + gate.w);
            float dy = y - clamp(y, gate.y, gate.y + gate.h);
            if (dx * dx + dy * dy < 40 * 40) return false;
        }
        for (Orb orb : this.orbs) {
            if (orb.active && Math.hypot(x - orb.x, y - orb.y) < 72) return false;
        }
        return true;
    }

    /** Compact masonry with small passages and a few indestructible rebound gates. */
    private void generateLayout() {
        int nextPalette = this.random.nextInt(4);
        if (nextPalette == this.palette) nextPalette = (nextPalette + 1 + this.random.nextInt(3)) % 4;
        this.palette = nextPalette;
        this.pattern = this.random.nextInt(5);
        int columns = 16 + this.random.nextInt(3);
        int rows = 12 + this.random.nextInt(3);
        float top = 250;
        float bottom = Math.min(760, Math.min(this.height * .45f, paddleY() - 230));
        float pitchX = 884f / columns;
        float pitchY = (bottom - top) / rows;
        float tileWidth = pitchX - 5;
        float tileHeight = Math.min(30, pitchY - 5);
        int passageColumn = 3 + this.random.nextInt(columns - 6);
        int passageRow = 3 + this.random.nextInt(rows - 6);
        Shape[] shapes = {Shape.ROUND, Shape.ROUND, Shape.CAPSULE, Shape.HEXAGON, Shape.DIAMOND};
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                // Open a slim route into the formation without making the board sparse.
                boolean gap = this.pattern == 0 && row == passageRow && column % 5 == 2
                        || this.pattern == 1 && column == passageColumn && row % 3 != 0
                        || this.pattern == 2 && row > 1 && row < rows - 2 && (column + row) % 13 == 0
                        || this.pattern == 3 && row == passageRow && Math.abs(column - columns / 2) < 2
                        || this.pattern == 4 && row % 4 == 2 && (column == 3 || column == columns - 4);
                if (gap) continue;
                Shape shape = shapes[this.random.nextInt(shapes.length)];
                float x = 58 + column * pitchX + (pitchX - tileWidth) / 2;
                float y = top + row * pitchY + (pitchY - tileHeight) / 2;
                int hp = this.random.nextFloat() < Math.min(.26f, .08f + this.level * .006f) ? 2 : 1;
                this.bricks.add(new Brick(this.bricks.size(), row, x, y, tileWidth, tileHeight,
                        hp, shape, (column / 3 + row / 2 + this.random.nextInt(2)) % 6));
            }
        }
        int count = 1 + this.random.nextInt(3);
        for (int index = 0; index < count; index++) {
            float x = count == 1 ? 448 : count == 2 ? 238 + index * 420 : 198 + index * 250;
            float y = bottom + 69 + ((index + this.pattern) % 2) * 31;
            this.obstacles.add(new Brick(-2 - index, -1, x, y, 104, 28, 1, Shape.CAPSULE, 0));
        }
    }

    void move(float f) {
        this.targetX = clamp(f, (paddleWidth() / 2.0f) + LEFT, RIGHT - (paddleWidth() / 2.0f));
    }

    void attach() {
        for (Ball ball : this.balls) {
            ball.x = this.paddleX;
            ball.y = (paddleY() - RADIUS) - 5.0f;
        }
    }

    void launch() {
        if (this.state != State.READY) {
            return;
        }
        for (Ball ball : this.balls) {
            ball.vx = speed() * 0.24f;
            ball.vy = -((float) Math.sqrt((speed() * speed()) - (ball.vx * ball.vx)));
        }
        this.state = State.PLAYING;
    }

    void pause() {
        if (this.state == State.PLAYING || this.state == State.READY) {
            this.beforePause = this.state;
            this.state = State.PAUSED;
        }
    }

    void resume() {
        if (this.state == State.PAUSED && !this.portalActive) {
            this.state = this.beforePause;
        }
    }

    void tick(float f) {
        if (this.portalActive || !Float.isFinite(f) || f <= 0) return;
        GameEngine gameEngine = this;
        if (gameEngine.state == State.PLAYING || gameEngine.state == State.READY) {
            float f2 = gameEngine.paddleX;
            float fPaddleWidth = gameEngine.paddleWidth() / 2.0f;
            float f3 = gameEngine.targetX;
            float f4 = LEFT + fPaddleWidth;
            float f5 = RIGHT - fPaddleWidth;
            float fClamp = clamp(f3, f4, f5);
            gameEngine.targetX = fClamp;
            float f6 = gameEngine.paddleX;
            float fClamp2 = clamp(f6 + ((fClamp - f6) * Math.min(1.0f, 48.0f * f)), f4, f5);
            gameEngine.paddleX = fClamp2;
            gameEngine.paddleVelocity = clamp((fClamp2 - f2) / f, -1800.0f, 1800.0f);
            if (gameEngine.state == State.READY) {
                gameEngine.attach();
                updateBursts(f);
                return;
            }
            float f7 = gameEngine.time + f;
            gameEngine.time = f7;
            if (f7 - gameEngine.lastHit > 3.3f) {
                gameEngine.combo = 0;
            }
            float f8 = gameEngine.slow() ? 0.72f : 1.0f;
            for (int size = gameEngine.balls.size() - 1; size >= 0; size--) {
                Ball ball = gameEngine.balls.get(size);
                float f9 = ball.y;
                ball.x += ball.vx * f * f8;
                ball.y += ball.vy * f * f8;
                ball.trail();
                if (ball.x < 39.0f) {
                    ball.x = 39.0f;
                    ball.vx = Math.abs(ball.vx);
                    gameEngine.event(Kind.WALL, ball.x, ball.y, 0, 0, null);
                }
                if (ball.x > 961.0f) {
                    ball.x = 961.0f;
                    ball.vx = -Math.abs(ball.vx);
                    event(Kind.WALL, ball.x, ball.y, 0, 0, null);
                }
                if (ball.y < 199.0f) {
                    ball.y = 199.0f;
                    ball.vy = Math.abs(ball.vy);
                    gameEngine = this;
                    gameEngine.event(Kind.WALL, ball.x, ball.y, 0, 0, null);
                } else {
                    gameEngine = this;
                }
                if (ball.vy > 0.0f && f9 + RADIUS <= gameEngine.paddleY() + 12.0f && ball.y + RADIUS >= gameEngine.paddleY() && Math.abs(ball.x - gameEngine.paddleX) <= (gameEngine.paddleWidth() / 2.0f) + 8.45f) {
                    ball.y = (gameEngine.paddleY() - RADIUS) - 0.1f;
                    ball.vx = (gameEngine.speed() * ((float) Math.sin(clamp((ball.x - gameEngine.paddleX) / (gameEngine.paddleWidth() / 2.0f), -0.98f, 0.98f) * 1.04f))) + (gameEngine.paddleVelocity * 0.1f);
                    ball.vx = clamp(ball.vx, (-gameEngine.speed()) * 0.9f, gameEngine.speed() * 0.9f);
                    ball.vy = -((float) Math.sqrt((gameEngine.speed() * gameEngine.speed()) - (ball.vx * ball.vx)));
                    if (gameEngine.cleanup() && (gameEngine.zen || gameEngine.time - gameEngine.lastHit > 4.0f)) {
                        gameEngine.aimAtRemaining(ball);
                    }
                    gameEngine.event(Kind.PADDLE, ball.x, gameEngine.paddleY(), 0, 0, null);
                }
                for (Brick obstacle : gameEngine.obstacles) {
                    if (contact(obstacle, ball.x, ball.y, gameEngine.contact)) {
                        ball.x += gameEngine.contact.nx * (gameEngine.contact.depth + .2f);
                        ball.y += gameEngine.contact.ny * (gameEngine.contact.depth + .2f);
                        float approach = ball.vx * gameEngine.contact.nx + ball.vy * gameEngine.contact.ny;
                        if (approach < 0) {
                            ball.vx -= 2 * approach * gameEngine.contact.nx;
                            ball.vy -= 2 * approach * gameEngine.contact.ny;
                            gameEngine.normalize(ball);
                            obstacle.hitAt = gameEngine.time;
                            gameEngine.event(Kind.WALL, ball.x, ball.y, 0, 0, null);
                        }
                        break;
                    }
                }
                for (Brick brick : gameEngine.bricks) {
                    if (brick.hp != 0 && (ball.previousBrick != brick.id || gameEngine.time - ball.lastContact >= 0.11f)) {
                        if (contact(brick, ball.x, ball.y, gameEngine.contact)) {
                            ball.previousBrick = brick.id;
                            ball.lastContact = gameEngine.time;
                            if (!gameEngine.comet()) {
                                ball.x += gameEngine.contact.nx * (gameEngine.contact.depth + 0.2f);
                                ball.y += gameEngine.contact.ny * (gameEngine.contact.depth + 0.2f);
                                float f10 = (ball.vx * gameEngine.contact.nx) + (ball.vy * gameEngine.contact.ny);
                                if (f10 < 0.0f) {
                                    float f11 = f10 * 2.0f;
                                    ball.vx -= gameEngine.contact.nx * f11;
                                    ball.vy -= f11 * gameEngine.contact.ny;
                                }
                                gameEngine.normalize(ball);
                            }
                            gameEngine.hit(brick, gameEngine.comet());
                            if (gameEngine.portalActive) return;
                            if (gameEngine.state == State.CLEARED || !gameEngine.comet()) {
                                break;
                            }
                        } else {
                            continue;
                        }
                    }
                }
                if (gameEngine.state == State.PLAYING) {
                    for (Orb orb : gameEngine.orbs) {
                        if (orb.active) {
                            float f12 = ball.x - orb.x;
                            float f13 = ball.y - orb.y;
                            if ((f12 * f12) + (f13 * f13) < 1936.0f) {
                                gameEngine.trigger(orb, ball);
                            }
                        }
                    }
                }
                if (ball.y > gameEngine.height - 100.0f) {
                    gameEngine.balls.remove(size);
                }
            }
            if (gameEngine.state == State.CLEARED) {
                gameEngine.orbs.clear();
                gameEngine.bursts.clear();
                return;
            }
            gameEngine.orbs.removeIf(new Predicate() { // from class: dev.brickzen.app.GameEngine$$ExternalSyntheticLambda0
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return GameEngine.lambda$tick$0((GameEngine.Orb) obj);
                }
            });
            updateBursts(f);
            if (gameEngine.portalActive) return;
            if (gameEngine.state == State.CLEARED) {
                return;
            }
            if (gameEngine.orbs.size() < 2 && gameEngine.orbsSpawned < 8 && gameEngine.remaining() > 12 && gameEngine.time >= gameEngine.nextOrbAt) {
                gameEngine.spawnOrb(gameEngine.orbs.isEmpty());
                gameEngine.nextOrbAt = gameEngine.time + 4.0f;
            }
            for (int size2 = gameEngine.drops.size() - 1; size2 >= 0; size2--) {
                Drop drop = gameEngine.drops.get(size2);
                drop.y += 280.0f * f;
                if (drop.y >= gameEngine.paddleY() - 24.0f && drop.y <= gameEngine.paddleY() + 35.0f && Math.abs(drop.x - gameEngine.paddleX) < (gameEngine.paddleWidth() / 2.0f) + 22.0f) {
                    gameEngine.activate(drop.power);
                    gameEngine.drops.remove(size2);
                } else if (drop.y > gameEngine.height - 110.0f) {
                    gameEngine.drops.remove(size2);
                }
            }
            if (gameEngine.balls.isEmpty()) {
                gameEngine.combo = 0;
                if (!gameEngine.zen) {
                    gameEngine.lives--;
                }
                gameEngine.event(Kind.MISS, gameEngine.paddleX, gameEngine.paddleY(), 0, 0, null);
                if (!gameEngine.zen && gameEngine.lives <= 0) {
                    gameEngine.state = State.OVER;
                } else {
                    gameEngine.state = State.READY;
                    gameEngine.balls.add(new Ball(gameEngine.paddleX, (gameEngine.paddleY() - RADIUS) - 5.0f, 0.0f, 0.0f));
                }
            }
        }
    }

    static /* synthetic */ boolean lambda$tick$0(Orb orb) {
        return !orb.active;
    }

    private void trigger(final Orb orb, Ball ball) {
        if (orb.active) {
            int i = 0;
            orb.active = false;
            this.score += 50;
            this.lastHit = this.time;
            this.charge = Math.min(1.0f, this.charge + 0.12f);
            Burst burst = new Burst(orb.type, orb.x, orb.y);
            ArrayList<Brick> arrayList = new ArrayList();
            for (Brick brick : this.bricks) {
                if (brick.hp > 0) {
                    arrayList.add(brick);
                }
            }
            if (orb.type == Surprise.METEOR) {
                Collections.shuffle(arrayList, this.random);
            } else {
                arrayList.sort(Comparator.comparingDouble(new ToDoubleFunction() { // from class: dev.brickzen.app.GameEngine$$ExternalSyntheticLambda1
                    @Override // java.util.function.ToDoubleFunction
                    public final double applyAsDouble(Object obj) {
                        GameEngine.Orb orb2 = orb;
                        GameEngine.Brick brick2 = (GameEngine.Brick) obj;
                        return Math.hypot((brick2.x + (brick2.w / 2.0f)) - orb2.x, (brick2.y + (brick2.h / 2.0f)) - orb2.y);
                    }
                }));
            }
            int i2 = AnonymousClass1.$SwitchMap$dev$brickzen$app$GameEngine$Surprise[orb.type.ordinal()];
            if (i2 == 1) {
                while (i < Math.min(14, arrayList.size())) {
                    burst.strikes.add(new Strike((Brick) arrayList.get(i), i * 0.045f));
                    i++;
                }
            } else if (i2 == 2) {
                for (Brick brick2 : arrayList) {
                    float fHypot = (float) Math.hypot((brick2.x + (brick2.w / 2.0f)) - orb.x, (brick2.y + (brick2.h / 2.0f)) - orb.y);
                    if (fHypot <= 340.0f && burst.strikes.size() < 8) {
                        burst.strikes.add(new Strike(brick2, (fHypot / 340.0f) * 0.5f));
                    }
                }
            } else if (i2 == 3) {
                while (i < Math.min(5, arrayList.size())) {
                    burst.strikes.add(new Strike((Brick) arrayList.get(i), (i * 0.13f) + 0.25f));
                    i++;
                }
            } else if (i2 == 4) {
                split(ball);
                this.prismUntil = this.time + 9.0f;
            }
            this.bursts.add(burst);
            if (this.events.size() < 256) {
                this.events.add(new Event(Kind.SURPRISE, orb.x, orb.y, 50, orb.type.ordinal(), null, this.combo, 0, 0, burst));
            }
        }
    }

    private void updateBursts(float f) {
        for (int size = this.bursts.size() - 1; size >= 0; size--) {
            Burst burst = this.bursts.get(size);
            burst.age += f;
            for (Strike strike : burst.strikes) {
                if (!strike.done && burst.age >= strike.delay) {
                    strike.done = true;
                    hit(strike.brick, true);
                    if (this.portalActive) return;
                }
            }
            if (this.state == State.CLEARED) {
                this.orbs.clear();
                this.bursts.clear();
                return;
            } else {
                if (burst.age > burst.duration) {
                    this.bursts.remove(size);
                }
            }
        }
    }

    private void split(Ball ball) {
        float fAtan2 = (float) Math.atan2(ball.vy, ball.vx);
        float[] fArr = {-0.38f, 0.38f};
        for (int i = 0; i < 2; i++) {
            float f = fArr[i];
            if (this.balls.size() < 5) {
                double d = f + fAtan2;
                Ball ball2 = new Ball(ball.x, ball.y, speed() * ((float) Math.cos(d)), speed() * ((float) Math.sin(d)));
                normalize(ball2);
                this.balls.add(ball2);
            }
        }
    }

    private void aimAtRemaining(Ball ball) {
        Brick brick = null;
        float f = Float.MAX_VALUE;
        for (Brick brick2 : this.bricks) {
            if (brick2.hp > 0) {
                float f2 = (brick2.x + (brick2.w / 2.0f)) - ball.x;
                float f3 = (ball.y - brick2.y) - (brick2.h / 2.0f);
                if (f3 > 0.0f) {
                    float f4 = (f2 * f2) + (f3 * f3);
                    if (f4 < f) {
                        brick = brick2;
                        f = f4;
                    }
                }
            }
        }
        if (brick != null) {
            float f5 = (brick.x + (brick.w / 2.0f)) - ball.x;
            float f6 = (brick.y + (brick.h / 2.0f)) - ball.y;
            float fHypot = (float) Math.hypot(f5, f6);
            ball.vx = (f5 / fHypot) * speed();
            ball.vy = (f6 / fHypot) * speed();
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    static boolean contact(Brick brick, float f, float f2, Contact contact) {
        float f3;
        float f4 = f - brick.x;
        float f5 = f2 - brick.y;
        if (f4 < -13.0f) {
            return false;
        }
        float f6 = brick.w;
        float f7 = RADIUS;
        if (f4 > f6 + RADIUS || f5 < -13.0f || f5 > brick.h + RADIUS) {
            return false;
        }
        float f8 = 1.0f;
        int i = 1;
        if (brick.vertices != null) {
            float[] fArr = brick.vertices;
            float f9 = Float.MAX_VALUE;
            boolean z = true;
            float f10 = 0.0f;
            float f11 = 0.0f;
            float f12 = 0.0f;
            int i2 = 0;
            float f13 = -1.0f;
            while (i2 < fArr.length) {
                int i3 = i2 + 2;
                int length = i3 % fArr.length;
                float f14 = fArr[i2];
                float f15 = fArr[i2 + 1];
                float f16 = fArr[length] - f14;
                float f17 = fArr[length + i] - f15;
                int i4 = i;
                float f18 = (f16 * f16) + (f17 * f17);
                float f19 = f5 - f15;
                float f20 = f4 - f14;
                if ((f16 * f19) - (f17 * f20) < 0.0f) {
                    z = false;
                }
                float f21 = (f20 * f16) + (f19 * f17);
                float f22 = f7;
                float fClamp = clamp(f21 / f18, 0.0f, 1.0f);
                float f23 = f14 + (fClamp * f16);
                float f24 = f15 + (fClamp * f17);
                float f25 = f4 - f23;
                float f26 = f5 - f24;
                float f27 = (f25 * f25) + (f26 * f26);
                if (f27 < f9) {
                    float fSqrt = (float) Math.sqrt(f18);
                    f10 = f17 / fSqrt;
                    f13 = (-f16) / fSqrt;
                    f9 = f27;
                    f12 = f24;
                    f11 = f23;
                }
                i2 = i3;
                f7 = f22;
                i = i4;
            }
            float f28 = f7;
            boolean z2 = i != 0;
            if (!z && f9 > 169.0f) {
                return false;
            }
            float fSqrt2 = (float) Math.sqrt(f9);
            if (fSqrt2 < 0.001f) {
                contact.nx = f10;
                contact.ny = f13;
            } else {
                f8 = z ? -1.0f : 1.0f;
                contact.nx = ((f4 - f11) * f8) / fSqrt2;
                contact.ny = (f8 * (f5 - f12)) / fSqrt2;
            }
            if (!z) {
                fSqrt2 = -fSqrt2;
            }
            contact.depth = fSqrt2 + f28;
            return z2;
        }
        float fMin = brick.shape == Shape.ROUND ? Math.min(RADIUS, Math.min(brick.w, brick.h) * 0.26f) : Math.min(brick.w, brick.h) / 2.0f;
        float fClamp2 = f4 - clamp(f4, fMin, brick.w - fMin);
        float fClamp3 = f5 - clamp(f5, fMin, brick.h - fMin);
        float fHypot = (float) Math.hypot(fClamp2, fClamp3);
        float f29 = fMin + RADIUS;
        if (fHypot > f29) {
            return false;
        }
        if (fHypot > 0.001f) {
            contact.nx = fClamp2 / fHypot;
            contact.ny = fClamp3 / fHypot;
            contact.depth = f29 - fHypot;
        } else {
            float f30 = f4 - fMin;
            float f31 = (brick.w - fMin) - f4;
            float f32 = f5 - fMin;
            float f33 = (brick.h - fMin) - f5;
            float fMin2 = Math.min(Math.min(f30, f31), Math.min(f32, f33));
            if (fMin2 == f30) {
                f3 = -1.0f;
            } else {
                f3 = fMin2 == f31 ? 1.0f : 0.0f;
            }
            contact.nx = f3;
            if (fMin2 == f32) {
                f8 = -1.0f;
            } else if (fMin2 != f33) {
                f8 = 0.0f;
            }
            contact.ny = f8;
            if (contact.nx != 0.0f) {
                contact.ny = 0.0f;
            }
            contact.depth = f29 + fMin2;
        }
        return true;
    }

    private void normalize(Ball ball) {
        float fSqrt = (float) Math.sqrt((ball.vx * ball.vx) + (ball.vy * ball.vy));
        if (fSqrt < 0.1f) {
            ball.vx = 0.0f;
            ball.vy = -speed();
            return;
        }
        ball.vx = (ball.vx / fSqrt) * speed();
        ball.vy = (ball.vy / fSqrt) * speed();
        if (Math.abs(ball.vy) < speed() * 0.3f) {
            ball.vy = Math.copySign(speed() * 0.3f, ball.vy == 0.0f ? -1.0f : ball.vy);
            ball.vx = Math.copySign((float) Math.sqrt((speed() * speed()) - (ball.vy * ball.vy)), ball.vx);
        }
    }

    void hit(Brick brick, boolean z) {
        if (this.portalActive || brick.hp == 0 || this.obstacles.contains(brick)) {
            return;
        }
        boolean openPortal = brick.mystery && !this.portalTriggered;
        this.combo = Math.min(200, this.combo + 1);
        this.lastHit = this.time;
        brick.hp = z ? 0 : brick.hp - 1;
        brick.hitAt = this.time;
        int iMin = Math.min(60, this.combo * 2) + 10;
        if (brick.hp == 0) {
            this.destroyed++;
            iMin += brick.maxHp * 15;
        }
        int i = iMin;
        this.score += i;
        this.charge = Math.min(1.0f, this.charge + 0.058f);
        if (this.events.size() < 256) {
            this.events.add(new Event(Kind.HIT, brick.x + (brick.w / 2.0f), brick.y + (brick.h / 2.0f), i, brick.color, null, this.combo, brick.maxHp, brick.hp));
        }
        if (brick.hp == 0 && this.random.nextFloat() < 0.16f && this.drops.size() < 5) {
            this.drops.add(new Drop(brick.x + (brick.w / 2.0f), brick.y + (brick.h / 2.0f), Power.values()[this.random.nextInt(4)]));
        }
        if (cleanup() && !this.cleanupGranted) {
            this.cleanupGranted = true;
            this.charge = 1.0f;
            event(Kind.CLEANUP, 500.0f, paddleY() - 90.0f, remaining(), 0, null);
        }
        if (this.destroyed == this.total) {
            this.score += this.level * 100;
            this.state = State.CLEARED;
            this.drops.clear();
            event(Kind.CLEAR, 500.0f, 0.38f * this.height, this.level * 100, 0, null);
        }
        if (openPortal) {
            brick.mystery = false;
            this.portalTriggered = true;
            this.portalId = UUID.randomUUID().toString();
            this.portalPending = true;
            this.portalActive = true;
            this.beforePause = this.state;
            this.state = State.PAUSED;
        }
    }

    /* JADX INFO: renamed from: dev.brickzen.app.GameEngine$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$dev$brickzen$app$GameEngine$Power;
        static final /* synthetic */ int[] $SwitchMap$dev$brickzen$app$GameEngine$Surprise;

        static {
            int[] iArr = new int[Power.values().length];
            $SwitchMap$dev$brickzen$app$GameEngine$Power = iArr;
            try {
                iArr[Power.WIDE.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Power[Power.SLOW.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Power[Power.COMET.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Power[Power.MULTI.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            int[] iArr2 = new int[Surprise.values().length];
            $SwitchMap$dev$brickzen$app$GameEngine$Surprise = iArr2;
            try {
                iArr2[Surprise.LIGHTNING.ordinal()] = 1;
            } catch (NoSuchFieldError unused5) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Surprise[Surprise.NOVA.ordinal()] = 2;
            } catch (NoSuchFieldError unused6) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Surprise[Surprise.METEOR.ordinal()] = 3;
            } catch (NoSuchFieldError unused7) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Surprise[Surprise.PRISM.ordinal()] = 4;
            } catch (NoSuchFieldError unused8) {
            }
        }
    }

    void activate(Power power) {
        int i = AnonymousClass1.$SwitchMap$dev$brickzen$app$GameEngine$Power[power.ordinal()];
        if (i == 1) {
            this.wideUntil = this.time + 14.0f;
        } else if (i == 2) {
            this.slowUntil = this.time + 12.0f;
        } else if (i == 3) {
            this.cometUntil = this.time + 9.0f;
        } else if (i == 4 && !this.balls.isEmpty()) {
            split(this.balls.get(0));
        }
        event(Kind.POWER, this.paddleX, paddleY() - 75.0f, 0, 0, power);
    }

    boolean pulse() {
        if ((this.state != State.PLAYING && this.state != State.READY) || this.charge < 0.999f) {
            return false;
        }
        this.charge = 0.0f;
        ArrayList arrayList = new ArrayList();
        for (Brick brick : this.bricks) {
            if (brick.hp > 0) {
                arrayList.add(brick);
            }
        }
        Collections.shuffle(arrayList, this.random);
        event(Kind.PULSE, this.paddleX, paddleY(), 0, 0, null);
        for (int i = 0; i < Math.min(6, arrayList.size()); i++) {
            hit((Brick) arrayList.get(i), true);
            if (this.portalActive) return true;
        }
        if (this.state == State.CLEARED) {
            this.orbs.clear();
            this.bursts.clear();
        }
        return true;
    }

    /** Apply a checked host result once and continue the state interrupted by the portal. */
    void completePortal(int bonusScore, boolean survived, boolean cancelled) {
        if (!this.portalActive) return;
        this.portalPending = false;
        this.portalActive = false;
        if (!cancelled) {
            int bonus = Math.max(0, Math.min(3000, bonusScore));
            this.score = (int) Math.min(Integer.MAX_VALUE, (long) this.score + bonus);
            if (survived) {
                this.wideUntil = Math.max(this.wideUntil, this.time + 14.0f);
                this.charge = 1;
                event(Kind.POWER, this.paddleX, paddleY() - 75, bonus, 0, Power.WIDE);
            }
        }
        this.state = this.beforePause;
        if (this.state == State.READY) launch();
    }

    /** Versioned checkpoint for trusted application-private storage only. */
    String snapshot() {
        if (!validSnapshot(this)) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(this);
            }
            if (bytes.size() > MAX_SNAPSHOT_BYTES) return null;
            return SNAPSHOT_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    static GameEngine fromSnapshot(String value) {
        if (value == null || !value.startsWith(SNAPSHOT_PREFIX)
                || value.length() > SNAPSHOT_PREFIX.length() + ((MAX_SNAPSHOT_BYTES + 2) / 3) * 4) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(SNAPSHOT_PREFIX.length()));
            if (bytes.length == 0 || bytes.length > MAX_SNAPSHOT_BYTES) return null;
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
                @Override protected Class<?> resolveClass(ObjectStreamClass descriptor)
                        throws IOException, ClassNotFoundException {
                    String name = descriptor.getName();
                    if (!name.equals(GameEngine.class.getName())
                            && !name.startsWith(GameEngine.class.getName() + "$")
                            && !name.equals("java.util.ArrayList") && !name.equals("java.util.Random")
                            && !name.equals("java.lang.Enum") && !name.equals("[F")) {
                        throw new InvalidClassException("Unsupported checkpoint class", name);
                    }
                    return super.resolveClass(descriptor);
                }
                @Override protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
                    throw new InvalidClassException("Proxy objects are not checkpoints");
                }
            }) {
                Object decoded = input.readObject();
                if (!(decoded instanceof GameEngine) || input.read() != -1) return null;
                GameEngine engine = (GameEngine) decoded;
                // V1 checkpoints written before gates were introduced have no such field.
                if (engine.obstacles == null) engine.obstacles = new ArrayList<>();
                return validSnapshot(engine) ? engine : null;
            }
        } catch (IOException | ClassNotFoundException | RuntimeException error) {
            return null;
        }
    }

    private static boolean validSnapshot(GameEngine engine) {
        if(engine.portalTarget<0 || engine.portalTarget>0 && (engine.portalGame<0 || engine.portalGame>4
                || engine.portalMetric<0 || engine.portalMetric>1 || engine.portalDifficulty<1))return false;
        if (engine.state == null || engine.beforePause == null || engine.random == null || engine.contact == null
                || engine.portalId == null || engine.portalId.length() > 128
                || engine.balls == null || engine.balls.size() > 16
                || engine.bricks == null || engine.bricks.size() > 1024
                || engine.obstacles == null || engine.obstacles.size() > 3
                || engine.orbs == null || engine.orbs.size() > 32
                || engine.bursts == null || engine.bursts.size() > 64
                || engine.drops == null || engine.drops.size() > 64
                || engine.events == null || engine.events.size() > 512
                || engine.level < 1 || engine.lives < 0 || engine.total != engine.bricks.size()
                || engine.destroyed < 0 || engine.destroyed > engine.total
                || !finite(engine.height, engine.paddleX, engine.targetX, engine.time, engine.charge,
                    engine.paddleVelocity, engine.paddleLift, engine.lastHit, engine.nextOrbAt,
                    engine.wideUntil, engine.slowUntil, engine.cometUntil, engine.prismUntil)
                || engine.height < 1280 || engine.time < 0
                || (engine.portalPending && !engine.portalActive)
                || (engine.portalActive && (engine.state != State.PAUSED || engine.portalId.isEmpty() || !engine.portalTriggered))) return false;
        int destroyed = 0, mystery = 0;
        for (Brick brick : engine.bricks) {
            if (brick == null || brick.shape == null || brick.maxHp < 1 || brick.hp < 0 || brick.hp > brick.maxHp
                    || !finite(brick.x, brick.y, brick.w, brick.h, brick.hitAt) || brick.w <= 0 || brick.h <= 0
                    || (brick.vertices != null && (brick.vertices.length > 32 || !finite(brick.vertices)))) return false;
            if (brick.hp == 0) destroyed++;
            if (brick.mystery) mystery++;
        }
        if (destroyed != engine.destroyed || mystery > 1) return false;
        for (Brick obstacle : engine.obstacles) {
            if (obstacle == null || obstacle.shape != Shape.CAPSULE || obstacle.mystery
                    || obstacle.hp != 1 || obstacle.maxHp != 1 || obstacle.id > -2
                    || !finite(obstacle.x, obstacle.y, obstacle.w, obstacle.h, obstacle.hitAt)
                    || obstacle.w <= 0 || obstacle.h <= 0 || obstacle.x < LEFT
                    || obstacle.x + obstacle.w > RIGHT || obstacle.y < TOP
                    || obstacle.y + obstacle.h >= engine.height - 100) return false;
        }
        for (Ball ball : engine.balls) {
            if (ball == null || ball.tx == null || ball.ty == null || ball.tx.length != 18 || ball.ty.length != 18
                    || ball.trailIndex < 0 || ball.trailIndex >= 18 || ball.trailCount < 0 || ball.trailCount > 18
                    || !finite(ball.x, ball.y, ball.vx, ball.vy, ball.lastContact) || !finite(ball.tx) || !finite(ball.ty)) return false;
        }
        for (Orb orb : engine.orbs) if (orb == null || orb.type == null || !finite(orb.x, orb.y)) return false;
        for (Drop drop : engine.drops) if (drop == null || drop.power == null || !finite(drop.x, drop.y)) return false;
        for (Burst burst : engine.bursts) {
            if (burst == null || burst.type == null || burst.strikes == null || burst.strikes.size() > 1024
                    || !finite(burst.x, burst.y, burst.age, burst.duration)) return false;
            for (Strike strike : burst.strikes) {
                if (strike == null || !engine.bricks.contains(strike.brick) || !finite(strike.x, strike.y, strike.delay)) return false;
            }
        }
        for (Event event : engine.events) if (event == null || event.kind == null || !finite(event.x, event.y)) return false;
        return true;
    }

    private static boolean finite(float... values) {
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }

    private void event(Kind kind, float f, float f2, int i, int i2, Power power) {
        if (this.events.size() < 256) {
            this.events.add(new Event(kind, f, f2, i, i2, power, this.combo, 0, 0));
        }
    }

    static float clamp(float f, float f2, float f3) {
        return Math.max(f2, Math.min(f3, f));
    }
}
