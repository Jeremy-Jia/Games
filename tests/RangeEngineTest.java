import dev.brickzen.range.RangeEngine;
import java.util.ArrayList;

public final class RangeEngineTest {
    public static void main(String[] args){
        hitMissAndRings();magazineAndReload();breathZoomPause();movingTargetsAndRound();determinismAndReset();encounterHasNoRoundCeiling();encounterScalingAndLargeCounters();
        System.out.println("PASS: range hit/miss/rings, flight time/moving target, magazine/reload, breath/zoom/pause, normal ten-shot round, unlimited encounter shots/difficulty and deterministic reset");
    }
    private static void step(RangeEngine e,float seconds){for(int i=0;i<(int)Math.ceil(seconds/.01f);i++)e.update(.01f);}
    private static void waitReady(RangeEngine e){int frames=0;while(e.phase==RangeEngine.Phase.PLAYING&&!e.canFire()&&frames++<1000){if(e.ammo==0&&e.pendingImpact<=0)e.reload();e.update(.01f);}check(frames<1000,"ready does not deadlock");}
    private static void aimedShot(RangeEngine e){waitReady(e);e.setHoldingBreath(true);e.setAim(e.aimForTargetX(),e.aimForTargetY());check(e.fire(),"normal shot accepted");}
    private static void hitMissAndRings(){
        ArrayList<String> events=new ArrayList<>();RangeEngine e=new RangeEngine(events::add);e.start(4);
        aimedShot(e);check(e.hits==0&&e.pendingImpact>0&&e.ammo==4,"flight precedes impact and consumes one round");
        step(e,.4f);check(e.hits==1&&e.lastRing==10&&e.score==100&&events.contains("bullseye"),"center impact scores a bullseye");
        waitReady(e);e.setAim(e.aimForTargetX()+e.targetRadius()*.55f,e.aimForTargetY());check(e.fire(),"off-center shot");step(e,.5f);
        check(e.hits==2&&e.lastRing>=4&&e.lastRing<=5&&e.lastAward<100,"outer ring scores fewer points");
        waitReady(e);int score=e.score;e.setAim(1.5f,1);check(e.fire(),"intentional miss fired");step(e,.5f);
        check(e.hits==2&&!e.lastHit&&e.score==score&&e.combo==0&&events.contains("miss"),"miss resets combo without phantom score");
    }
    private static void magazineAndReload(){
        RangeEngine e=new RangeEngine();e.start(6);for(int i=0;i<5;i++){aimedShot(e);step(e,1.5f);}
        check(e.ammo==0&&e.shotsFired==5&&!e.fire(),"empty magazine cannot fire");
        check(e.reload()&&!e.reload(),"reload starts once");step(e,.6f);check(e.ammo==0&&!e.canFire(),"cannot shoot while loading");
        step(e,.7f);check(e.ammo==5&&e.canFire(),"reload restores the magazine after delay");
    }
    private static void breathZoomPause(){
        RangeEngine e=new RangeEngine();e.start(2);e.time=.7f;float free=Math.abs(e.swayX());e.setHoldingBreath(true);
        check(Math.abs(e.swayX())<free*.2f,"screen sway reduces during breath hold");step(e,5.2f);check(!e.holdingBreath&&e.stamina<.1f,"stamina limits breath holding");
        step(e,2);check(e.stamina>.25f,"stamina recovers");
        int z=e.zoomIndex;e.changeZoom();check(e.zoomIndex!=z&&e.zoom()==6,"zoom cycles to a magnified view");e.changeZoom();check(e.zoom()==1,"zoom can return unscoped");
        e.setHoldingBreath(true);e.pause();float time=e.time,stamina=e.stamina;step(e,2);
        check(e.phase==RangeEngine.Phase.PAUSED&&e.time==time&&e.stamina==stamina&&!e.holdingBreath,"pause freezes time and releases breath");
        e.resume();e.update(.01f);check(e.time>time,"resume advances normally");
        time=e.time;e.update(Float.NaN);e.update(-2);e.update(Float.POSITIVE_INFINITY);check(e.time==time,"invalid step ignored");
        e.update(100);check(e.time-time<.051f,"frame hitch is bounded");
    }
    private static void movingTargetsAndRound(){
        RangeEngine e=new RangeEngine();e.start(17);float firstDistance=e.distance;int total=0;
        while(e.phase!=RangeEngine.Phase.OVER&&total++<11){aimedShot(e);step(e,1.5f);}
        check(e.phase==RangeEngine.Phase.OVER&&e.shotsFired==10&&e.hits==10,"ten normal impacts finish the round");
        check(e.movingTarget&&e.distance>firstDistance&&e.accuracy()==100&&e.score>=1000,"later moving targets and distance remain scoreable");
        float t=e.time;int score=e.score;e.update(.05f);check(!e.fire()&&!e.reload()&&e.time==t&&e.score==score,"terminal state is stable");
    }
    private static void determinismAndReset(){
        RangeEngine a=new RangeEngine(),b=new RangeEngine();a.start(91);b.start(91);
        for(int frame=0;frame<6000;frame++){
            if(a.canFire()){a.setAim(a.aimForTargetX(),a.aimForTargetY());b.setAim(b.aimForTargetX(),b.aimForTargetY());a.fire();b.fire();}
            if(a.ammo==0&&a.pendingImpact<=0){a.reload();b.reload();}
            a.update(1f/120);b.update(1f/120);
            check(a.targetX==b.targetX&&a.score==b.score&&a.phase==b.phase&&a.ammo==b.ammo,"deterministic trace");
        }
        a.start(92);check(a.phase==RangeEngine.Phase.PLAYING&&a.score==0&&a.shotsFired==0&&a.ammo==5&&a.stamina==1&&a.distance==100,"start resets all round state");
        a.setAim(Float.NaN,3);check(a.aimX==0,"nonfinite aim ignored");a.setAim(100,-100);check(a.aimX<=1.6f&&a.aimY>=-1.1f,"aim is bounded");
    }
    private static void encounterHasNoRoundCeiling(){
        RangeEngine e=new RangeEngine();e.startEncounter(23,4);
        for(int shot=0;shot<28;shot++){aimedShot(e);step(e,2);}
        check(e.phase==RangeEngine.Phase.PLAYING&&e.encounterMode&&e.shotsFired==28&&e.encounterShots==28,
                "encounter keeps presenting targets beyond the former ten-shot round");
        check(e.encounterHits==28&&e.encounterScore>=2800&&e.hits==28,"ordinary hit/flight/reload paths advance long encounter progress");
        e.start(23);for(int shot=0;shot<10;shot++){aimedShot(e);step(e,2);}
        check(!e.encounterMode&&e.phase==RangeEngine.Phase.OVER&&e.shotsFired==10,"normal start restores the ten-shot rule");
    }
    private static void encounterScalingAndLargeCounters(){
        RangeEngine easy=new RangeEngine(),hard=new RangeEngine(),extreme=new RangeEngine();
        easy.startEncounter(42,1);hard.startEncounter(42,80);extreme.startEncounter(42,Integer.MAX_VALUE);
        check(hard.encounterDifficulty==80&&hard.distance>easy.distance&&hard.targetRadius()<easy.targetRadius(),"difficulty scales beyond the former three preset tiers");
        check(extreme.distance>hard.distance&&extreme.targetRadius()<hard.targetRadius(),"difficulty is not clamped at an arbitrary challenge level");
        for(int shot=0;shot<12;shot++){aimedShot(extreme);step(extreme,3);}
        check(extreme.phase==RangeEngine.Phase.PLAYING&&extreme.encounterHits==12,"very high tiers still expose scoreable targets and legal aim range");
        hard.encounterShots=Integer.MAX_VALUE;hard.encounterHits=Integer.MAX_VALUE;hard.encounterScore=(long)Integer.MAX_VALUE+200;
        hard.shotsFired=hard.hits=hard.score=Integer.MAX_VALUE;
        aimedShot(hard);step(hard,2);
        check(hard.encounterShots>(long)Integer.MAX_VALUE&&hard.encounterHits>(long)Integer.MAX_VALUE
                &&hard.encounterScore>(long)Integer.MAX_VALUE+200,"encounter counters outgrow legacy integer score fields without overflow");
        check(hard.phase==RangeEngine.Phase.PLAYING&&hard.score==Integer.MAX_VALUE,"legacy HUD counters saturate without terminating a long goal");
    }
    private static void check(boolean passed,String message){if(!passed)throw new AssertionError(message);}
}
