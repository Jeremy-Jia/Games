package dev.brickzen.app;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.HashSet;

/** Pure Java regression tests for the recovered brick engine and its star-portal handoff. */
public final class BrickEngineTest {
    public static void main(String[] args) throws Exception {
        compactLayouts(); orbsAvoidGates(); obstacleBounceAndClear(); obstacleSnapshots(); mysteryPlacement(); physicalPortalPausesAfterBounce(); chainPortalStopsRemainingStrikes();
        checkpointRoundTrip(); rewardIsOnceOnly(); cancelledPortal(); readyPortalAutoLaunch(); lastBrickPortal(); invalidCheckpoints();
        System.out.println("PASS: mystery placement, hit/bounce/freeze, chain freeze, full checkpoint/random, idempotent reward/cancel, final-brick clear, invalid snapshots");
    }

    private static void compactLayouts() {
        int minimum=Integer.MAX_VALUE,maximum=0;
        for(int seed=0;seed<60;seed++)for(float height:new float[]{1280,2000,2250}) {
            GameEngine engine=new GameEngine(seed);engine.resize(height);engine.setPaddleLift(.36f);
            engine.start(1+seed,true,0);
            minimum=Math.min(minimum,engine.total);maximum=Math.max(maximum,engine.total);
            check(engine.total>=180 && engine.total<=252,"dense layouts retain at least 180 small bricks");
            float bottom=0;
            for(GameEngine.Brick brick:engine.bricks) {
                check(brick.w<=51 && brick.h<=30 && brick.h>=15,"compact tile sizes");
                check(brick.x>GameEngine.LEFT+GameEngine.RADIUS && brick.x+brick.w<GameEngine.RIGHT-GameEngine.RADIUS,"open side channels");
                bottom=Math.max(bottom,brick.y+brick.h);
            }
            check(engine.obstacles.size()>=1 && engine.obstacles.size()<=3,"one to three rebound gates");
            for(GameEngine.Brick gate:engine.obstacles) {
                check(gate.y-bottom>GameEngine.RADIUS*2,"passage below destructible formation");
                check(engine.paddleY()-(gate.y+gate.h)>GameEngine.RADIUS*3,"gate stays clear of the paddle");
                for(GameEngine.Brick other:engine.obstacles)if(other!=gate)
                    check(Math.abs(other.x-gate.x)>gate.w+GameEngine.RADIUS*2,"gates leave traversable gaps");
            }
            check(GameEngine.fromSnapshot(engine.snapshot())!=null,"new dense board snapshot remains valid");
        }
        System.out.println("Compact layouts: 180 samples, "+minimum+"–"+maximum+" destructible tiles, 1–3 open rebound gates");
    }

    private static void obstacleBounceAndClear() {
        for(boolean comet:new boolean[]{false,true}) {
            GameEngine engine=minimal(false);
            GameEngine.Brick gate=new GameEngine.Brick(-2,-1,400,700,104,28,1,GameEngine.Shape.CAPSULE,0);
            engine.obstacles.add(gate);engine.cometUntil=comet?10:0;
            GameEngine.Ball ball=engine.balls.get(0);ball.x=452;ball.y=741;ball.vx=0;ball.vy=-600;
            int score=engine.score;engine.tick(1f/120);
            check(ball.vy>0 && ball.y>gate.y+gate.h,"gate reflects ordinary and comet balls");
            check(gate.hp==1 && engine.destroyed==0 && engine.score==score,"rebound has no destruction, score or charge");
            engine.hit(gate,true);check(gate.hp==1 && engine.score==score,"power hits cannot destroy gates");
            for(GameEngine.Brick brick:engine.bricks)brick.mystery=false;
            for(GameEngine.Brick brick:engine.bricks)engine.hit(brick,true);
            check(engine.remaining()==0 && engine.state==GameEngine.State.CLEARED && gate.hp==1,"gates never prevent clearing");
        }
    }

    private static void orbsAvoidGates() throws Exception {
        java.lang.reflect.Method spawn=GameEngine.class.getDeclaredMethod("spawnOrb",boolean.class);spawn.setAccessible(true);
        for(int seed=0;seed<60;seed++)for(float height:new float[]{1280,2000,2250})for(float lift:new float[]{.12f,.22f,.36f}) {
            GameEngine engine=new GameEngine(seed);engine.resize(height);engine.setPaddleLift(lift);engine.start(seed+1,true,0);
            for(int generation=0;generation<4;generation++) {
                for(GameEngine.Orb orb:engine.orbs) {
                    for(GameEngine.Brick gate:engine.obstacles) {
                        float dx=orb.x-GameEngine.clamp(orb.x,gate.x,gate.x+gate.w);
                        float dy=orb.y-GameEngine.clamp(orb.y,gate.y,gate.y+gate.h);
                        check(Math.hypot(dx,dy)>=39.99f,"orb stays visibly clear of gates even with raised paddle");
                    }
                    for(GameEngine.Orb other:engine.orbs)if(other!=orb)
                        check(Math.hypot(orb.x-other.x,orb.y-other.y)>=71.99f,"power orbs do not cover each other");
                }
                engine.orbs.clear();
                // Simulate later refill after part of the formation has been cleared.
                for(GameEngine.Brick brick:engine.bricks)if(brick.row>=10-generation)brick.hp=0;
                spawn.invoke(engine,true);spawn.invoke(engine,false);
            }
        }
        System.out.println("Orb placement: 540 height/paddle/seed combinations, initial and refilled orbs clear of rebound gates");
    }

    private static void obstacleSnapshots() throws Exception {
        GameEngine engine=new GameEngine(83);engine.start(4,true,0);
        engine.obstacles.get(0).hitAt=.2f;
        GameEngine copy=GameEngine.fromSnapshot(engine.snapshot());
        check(copy!=null && copy.obstacles.size()==engine.obstacles.size()
                && copy.obstacles.get(0).x==engine.obstacles.get(0).x && copy.obstacles.get(0).hitAt==.2f,"gate geometry and feedback survive snapshots");
        // Emulate the default value Java supplies when an old V1 stream has no obstacles field.
        engine.obstacles=null;ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ObjectOutputStream output=new ObjectOutputStream(bytes)){output.writeObject(engine);}
        copy=GameEngine.fromSnapshot("BRICKZEN_PORTAL_V1:"+Base64.getEncoder().encodeToString(bytes.toByteArray()));
        check(copy!=null && copy.obstacles.isEmpty() && copy.total==engine.total,"legacy V1 checkpoint defaults to no gates without replacing its board");
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
        engine.obstacles.clear();
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
