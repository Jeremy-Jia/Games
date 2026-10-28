import dev.brickzen.hillclimb.HillClimbEngine;

public final class HillClimbEngineTest {
    public static void main(String[] args){terrain();driveAndBrake();fuelAndPickups();pauseCrashReset();determinism();encounterDifficultyAndCounters();
        System.out.println("PASS: hill driving continuous terrain, two-wheel suspension/traction, throttle/brake, pickups/fuel, crash/pause/reset, stable bounded deterministic steps");}
    private static void terrain(){for(int seed=0;seed<20;seed++){HillClimbEngine e=new HillClimbEngine();e.start(seed);
        for(float x=0;x<2000;x+=.13f){float height=e.terrainHeight(x);check(Float.isFinite(height)&&Math.abs(height-e.terrainHeight(x+.01f))<.04f,"terrain remains continuous");}
    }}
    private static void driveAndBrake(){HillClimbEngine e=new HillClimbEngine();e.start(1);e.setControls(1,0);
        boolean contact=false,air=false;for(int i=0;i<60*12&&e.phase==HillClimbEngine.Phase.PLAYING;i++){e.update(1f/60);contact|=e.groundedWheels>0;air|=e.airTime>.04f;}
        System.out.println("Drive probe: "+e.distance+" m, phase="+e.phase+", coins="+e.coins+", angle="+e.angle);
        check(contact&&e.distance>40&&e.fuel<100,"wheels contact, propel forward and consume fuel");
        HillClimbEngine flat=new HillClimbEngine();flat.start(2);for(int i=0;i<120;i++)flat.update(1f/60);check(Math.abs(flat.y-.59f)<.3f&&Math.abs(flat.angle)<.15f,"idle spring contact stable");
        flat.vx=5;flat.setControls(0,1);for(int i=0;i<60;i++)flat.update(1f/60);check(flat.vx<2,"brake removes forward speed");
        flat.y=flat.terrainHeight(flat.x)+5;flat.vy=1;flat.setControls(1,0);float angle=flat.angle;
        for(int i=0;i<20;i++)flat.update(1f/60);check(flat.airTime>0&&flat.angle>angle,"throttle changes pitch while airborne");
    }
    private static void fuelAndPickups(){HillClimbEngine e=new HillClimbEngine();e.start(3);e.fuel=20;
        HillClimbEngine.Pickup coin=e.pickups.get(0);e.x=coin.x;e.y=coin.y;e.update(.01f);check(e.coins==1,"collect world coin");e.update(.01f);check(e.coins==1,"coin collected once");
        for(int i=0;i<100;i++){e.x=18+i*8;e.y=e.terrainHeight(e.x)+.7f;e.angle=e.angularVelocity=e.vx=e.vy=0;e.update(.005f);}
        check(e.fuel>20&&e.pickups.size()<=40,"fuel pickup refills and rolling window stays bounded");
        e.start(1);e.fuel=0;for(int i=0;i<60*4;i++)e.update(1f/60);check(e.phase==HillClimbEngine.Phase.OVER&&e.reason.contains("燃油"),"dry stalled car ends naturally");
    }
    private static void pauseCrashReset(){HillClimbEngine e=new HillClimbEngine();e.start(1);e.pause();float t=e.time;e.update(1);check(e.time==t&&e.phase==HillClimbEngine.Phase.PAUSED,"pause freezes physics");e.resume();e.update(.01f);check(e.time>t,"resume advances");
        e.start(1);e.angle=(float)Math.PI;e.y=e.terrainHeight(e.x)+.35f;
        for(int i=0;i<60;i++)e.update(1f/120);check(e.phase==HillClimbEngine.Phase.OVER,"roof contact crashes");float terminal=e.time;e.update(.05f);check(e.time==terminal,"over is terminal");
        e.start(1);e.update(Float.NaN);e.update(-1);check(e.time==0,"reject invalid dt");e.update(10);check(e.time<.051f,"bound frame hitch");e.start(1);check(e.distance==0&&e.coins==0&&e.fuel==100&&e.phase==HillClimbEngine.Phase.PLAYING,"restart fully resets");
    }
    private static void determinism(){HillClimbEngine a=new HillClimbEngine(),b=new HillClimbEngine();a.start(99);b.start(99);
        for(int i=0;i<60*90;i++){float gas=i%180<140?1:0,brake=i%180>=140?1:0;a.setControls(gas,brake);b.setControls(gas,brake);a.update(1f/60);b.update(1f/60);
            check(a.x==b.x&&a.y==b.y&&a.angle==b.angle&&Float.isFinite(a.y)&&a.pickups.size()<=40,"replay finite, deterministic, bounded");}
    }
    private static void encounterDifficultyAndCounters(){
        HillClimbEngine normal=new HillClimbEngine(),hard=new HillClimbEngine();normal.start(9);hard.startEncounter(9,50);
        check(hard.difficulty==50&&Math.abs(hard.terrainHeight(150))>Math.abs(normal.terrainHeight(150)),"encounter terrain difficulty scales beyond fixed presets");
        for(int frame=0;frame<120;frame++){normal.update(1f/60);hard.update(1f/60);}
        check(hard.fuel<normal.fuel&&Float.isFinite(hard.y),"encounter tier affects fuel and remains physically stable");
        hard.startEncounter(7,Integer.MAX_VALUE);hard.update(.05f);
        check(Float.isFinite(hard.terrainHeight(700))&&Float.isFinite(hard.y)&&hard.phase==HillClimbEngine.Phase.PLAYING,"large numeric tier does not overflow terrain");
        hard.totalCoins=3_000_000_000L;hard.coins=Integer.MAX_VALUE;HillClimbEngine.Pickup coin=hard.pickups.get(0);hard.x=coin.x;hard.y=coin.y;hard.update(.001f);
        check(hard.objectiveProgress(1)==3_000_000_001L&&hard.coins==Integer.MAX_VALUE,"coin objective count can exceed int display range");
        hard.start(9);check(hard.difficulty==1&&hard.totalCoins==0&&hard.terrainHeight(150)==normal.terrainHeight(150),"ordinary restart restores original terrain and counters");
    }
    private static void check(boolean value,String description){if(!value)throw new AssertionError(description);}
}
