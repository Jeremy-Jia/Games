package dev.brickzen.app;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.HashSet;

/** Pure Java regression tests for the recovered brick engine and its star-portal handoff. */
public final class BrickEngineTest {
    public static void main(String[] args) throws Exception {
        mysteryPlacement(); physicalPortalPausesAfterBounce(); chainPortalStopsRemainingStrikes();
        checkpointRoundTrip(); rewardIsOnceOnly(); cancelledPortal(); readyPortalAutoLaunch(); lastBrickPortal(); invalidCheckpoints();
        System.out.println("PASS: mystery placement, hit/bounce/freeze, chain freeze, full checkpoint/random, idempotent reward/cancel, final-brick clear, invalid snapshots");
    }

    private static void mysteryPlacement() {
        HashSet<Integer> rows = new HashSet<>(), columns = new HashSet<>();
        for (int seed = 0; seed < 30; seed++) {
            GameEngine engine = new GameEngine(seed);
            for (int level = 1; level <= 12; level++) {
                engine.start(level, true, 0);
                int count = 0, bottomRow = -1, mysteryRow = -1;
                for (GameEngine.Brick brick : engine.bricks) {
                    bottomRow = Math.max(bottomRow, brick.row);
                    if (brick.mystery) { count++; mysteryRow = brick.row; rows.add(brick.row); columns.add(Math.round(brick.x+brick.w/2)); }
                }
                check(!engine.bricks.isEmpty() && count == 1 && mysteryRow < bottomRow, "one mystery brick inside the formation, never the front row");
                check(!engine.portalPending && !engine.portalActive && engine.portalId.isEmpty(), "new-level portal reset");
            }
        }
        check(rows.size() >= 4 && columns.size() >= 24, "mystery locations vary across rows and columns");
        GameEngine a = new GameEngine(763), b = new GameEngine(763);
        a.start(21,true,0);b.start(21,true,0);
        for(int i=0;i<a.bricks.size();i++)check(a.bricks.get(i).mystery==b.bricks.get(i).mystery,"seeded placement is reproducible");
        System.out.println("Placement: 360 layouts, " + rows.size() + " rows / " + columns.size() + " horizontal positions, no front-row portal");
    }

    private static GameEngine minimal(boolean lastBrick) {
        GameEngine engine = new GameEngine(123); engine.start(1, false, 200);
        engine.bricks.clear(); engine.balls.clear(); engine.orbs.clear(); engine.bursts.clear(); engine.drops.clear(); engine.events.clear();
        GameEngine.Brick mystery = new GameEngine.Brick(0, 0, 400, 400, 100, 40, lastBrick ? 1 : 3);
        mystery.mystery = true; engine.bricks.add(mystery);
        if (!lastBrick) engine.bricks.add(new GameEngine.Brick(1, 0, 100, 300, 100, 40, 3));
        engine.total = engine.bricks.size(); engine.destroyed = 0; engine.state = GameEngine.State.PLAYING;
        engine.balls.add(new GameEngine.Ball(450, 456, 0, -600));
        return engine;
    }

    private static void physicalPortalPausesAfterBounce() {
        GameEngine engine = minimal(false);
        // Reverse ball iteration handles the colliding ball first. The pending lost ball must remain untouched.
        engine.balls.add(0, new GameEngine.Ball(20, engine.height + 200, 0, 500));
        engine.tick(1f / 120);
        GameEngine.Ball ball = engine.balls.get(1);
        check(engine.portalActive && engine.portalPending && !engine.portalId.isEmpty(), "first hit opens portal");
        check(engine.state == GameEngine.State.PAUSED && engine.beforePause == GameEngine.State.PLAYING, "portal pauses correct previous state");
        check(engine.bricks.get(0).hp == 2 && !engine.bricks.get(0).mystery && ball.vy > 0, "first damage and bounce precede portal");
        check(engine.balls.size() == 2 && engine.lives == 3, "no remaining ball-loss processing after trigger");
        float x = ball.x, y = ball.y, vx = ball.vx, vy = ball.vy, time = engine.time; int score = engine.score;
        engine.resume(); engine.move(800);
        for (int i = 0; i < 100; i++) engine.tick(1f / 60);
        check(engine.state == GameEngine.State.PAUSED && x == ball.x && y == ball.y && vx == ball.vx && vy == ball.vy
                && time == engine.time && score == engine.score, "pending portal cannot move physical state or resume");
        engine.hit(engine.bricks.get(1), true);
        check(engine.bricks.get(1).hp == 3, "extra hit cannot mutate frozen portal state");
    }

    private static void chainPortalStopsRemainingStrikes() {
        GameEngine engine = minimal(false); engine.balls.get(0).x = 700; engine.balls.get(0).y = 1200;
        GameEngine.Burst burst = new GameEngine.Burst(GameEngine.Surprise.LIGHTNING, 400, 400);
        burst.strikes.add(new GameEngine.Strike(engine.bricks.get(0), 0));
        burst.strikes.add(new GameEngine.Strike(engine.bricks.get(1), 0));
        engine.bursts.add(burst); engine.tick(1f / 120);
        check(engine.portalActive && engine.bricks.get(0).hp == 0, "surprise chain triggers portal");
        check(engine.bricks.get(1).hp == 3 && !burst.strikes.get(1).done, "remaining chain survives paused handoff");
    }

    private static void checkpointRoundTrip() {
        GameEngine engine = minimal(false); engine.tick(1f / 120); engine.portalPending = false;
        GameEngine.Burst burst = new GameEngine.Burst(GameEngine.Surprise.METEOR, 230, 450);
        burst.age = .32f; burst.strikes.add(new GameEngine.Strike(engine.bricks.get(1), .5f));
        engine.bursts.add(burst);
        engine.orbs.add(new GameEngine.Orb(310, 780, GameEngine.Surprise.PRISM));
        engine.drops.add(new GameEngine.Drop(150, 1200, GameEngine.Power.SLOW));
        engine.events.add(new GameEngine.Event(GameEngine.Kind.SURPRISE, 230, 450, 50, 2, null, 4, 0, 0, burst));
        engine.wideUntil = 8; engine.slowUntil = 9; engine.cometUntil = 10; engine.prismUntil = 11;
        engine.combo = 7; engine.nextOrbAt = 6; engine.paddleVelocity = 27;
        String snapshot = engine.snapshot(); check(snapshot != null && snapshot.startsWith("BRICKZEN_PORTAL_V1:"), "versioned snapshot encoded");
        GameEngine copy = GameEngine.fromSnapshot(snapshot); check(copy != null, "snapshot decoded");
        check(copy.state == engine.state && copy.beforePause == engine.beforePause && copy.portalActive
                && !copy.portalPending && copy.portalId.equals(engine.portalId), "handoff identity and pause restored");
        check(copy.score == engine.score && copy.lives == engine.lives && copy.time == engine.time && copy.combo == engine.combo
                && copy.paddleVelocity == engine.paddleVelocity && copy.nextOrbAt == engine.nextOrbAt, "run scalars retained");
        check(copy.wideUntil == 8 && copy.slowUntil == 9 && copy.cometUntil == 10 && copy.prismUntil == 11, "power timers retained");
        check(copy.balls.size() == engine.balls.size() && copy.balls.get(0).x == engine.balls.get(0).x
                && copy.balls.get(0).y == engine.balls.get(0).y && copy.balls.get(0).vx == engine.balls.get(0).vx
                && copy.balls.get(0).vy == engine.balls.get(0).vy && copy.balls.get(0).trailCount == engine.balls.get(0).trailCount,
                "position velocity and trail retained");
        check(copy.bricks.get(0).hp == 2 && copy.bricks.get(1).hp == 3 && copy.bursts.get(0).age == .32f
                && copy.bursts.get(0).strikes.get(0).brick == copy.bricks.get(1), "brick state and shared chain references retained");
        check(copy.orbs.size() == 1 && copy.drops.size() == 1 && copy.events.size() == engine.events.size()
                && copy.events.get(copy.events.size() - 1).surprise == copy.bursts.get(0), "orbs drops event effects retained");
        check(copy.random.nextLong() == engine.random.nextLong(), "random generator resumes exactly");
    }

    private static void rewardIsOnceOnly() {
        GameEngine engine = minimal(false); engine.tick(1f / 120);
        GameEngine.Ball ball = engine.balls.get(0); float x = ball.x, y = ball.y, vx = ball.vx, vy = ball.vy, time = engine.time;
        int oldScore = engine.score, lives = engine.lives, hp = engine.bricks.get(0).hp;
        engine.completePortal(9000, true, false);
        check(engine.score == oldScore + 3000 && engine.charge == 1 && engine.wideUntil >= time + 14, "capped reward and successful power-up");
        check(!engine.portalActive && !engine.portalPending && engine.state == GameEngine.State.PLAYING, "reward automatically resumes the interrupted game");
        check(engine.lives == lives && engine.bricks.get(0).hp == hp && ball.x == x && ball.y == y && ball.vx == vx
                && ball.vy == vy && engine.time == time, "reward preserves original run");
        engine.completePortal(3000, true, false); check(engine.score == oldScore + 3000, "reward idempotent");
        engine.tick(1f/120);check(engine.time>time && (ball.x!=x||ball.y!=y),"ball continues without a resume tap");
        engine.hit(engine.bricks.get(0), false); check(!engine.portalActive, "same brick cannot reopen portal");
    }

    private static void cancelledPortal() {
        GameEngine engine = minimal(false); engine.tick(1f / 120);
        int score = engine.score; float charge = engine.charge, wide = engine.wideUntil;
        engine.completePortal(3000, true, true);
        check(engine.score == score && engine.charge == charge && engine.wideUntil == wide && !engine.portalActive, "cancel has no reward or penalty");
        check(engine.state == GameEngine.State.PLAYING && engine.lives == 3, "cancel automatically returns to the run");
    }

    private static void lastBrickPortal() {
        GameEngine engine = minimal(true); engine.tick(1f / 120);
        check(engine.portalActive && engine.state == GameEngine.State.PAUSED && engine.beforePause == GameEngine.State.CLEARED
                && engine.remaining() == 0, "last mystery brick records clear before portal");
        GameEngine restored = GameEngine.fromSnapshot(engine.snapshot()); check(restored != null, "cleared portal can persist");
        restored.completePortal(100, true, false); restored.resume();
        check(restored.state == GameEngine.State.CLEARED, "returning from last-brick portal advances clear flow");
    }

    private static void readyPortalAutoLaunch(){
        GameEngine engine=minimal(false);engine.state=GameEngine.State.READY;engine.balls.get(0).vx=engine.balls.get(0).vy=0;
        engine.hit(engine.bricks.get(0),false);engine.completePortal(0,false,true);
        check(engine.state==GameEngine.State.PLAYING && engine.balls.get(0).vy<0,"portal from ready state launches without another tap");
    }

    private static void invalidCheckpoints() throws Exception {
        check(GameEngine.fromSnapshot(null) == null && GameEngine.fromSnapshot("") == null
                && GameEngine.fromSnapshot("BRICKZEN_PORTAL_V2:AAAA") == null
                && GameEngine.fromSnapshot("BRICKZEN_PORTAL_V1:invalid!") == null, "bad envelope rejected");
        check(GameEngine.fromSnapshot("BRICKZEN_PORTAL_V1:" + "A".repeat(1_400_000)) == null, "oversized snapshot rejected");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject("not an engine"); }
        check(GameEngine.fromSnapshot("BRICKZEN_PORTAL_V1:" + Base64.getEncoder().encodeToString(bytes.toByteArray())) == null, "wrong serialized type rejected");
        GameEngine engine = minimal(false); engine.balls.get(0).vx = Float.NaN;
        check(engine.snapshot() == null, "invalid physical state is not checkpointed");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
