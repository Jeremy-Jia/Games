package dev.brickzen.range;

import java.util.Random;

/** Deterministic arcade range simulation. Aim coordinates and drop are fictional game units. */
public final class RangeEngine {
    public enum Phase { READY, PLAYING, PAUSED, OVER }
    public interface Listener { void event(String name); }
    public Phase phase=Phase.READY;
    public float time,aimX,aimY,targetX,targetY,distance=100,wind,stamina=1,recoil;
    public float reloadRemaining,shotCooldown,pendingImpact,nextTargetDelay,feedbackTime;
    public int score,hits,shotsFired,ammo=5,zoomIndex=1,combo,lastRing,lastAward;
    public boolean holdingBreath,movingTarget,lastHit;
    public boolean encounterMode;
    public int encounterDifficulty=1;
    public long encounterHits,encounterScore,encounterShots;
    public float lastImpactX,lastImpactY;
    private float targetBaseX,targetBaseY,motionPhase,impactX,impactY;
    private Random random=new Random(1);
    private final Listener listener;
    public RangeEngine(){this(null);}
    public RangeEngine(Listener listener){this.listener=listener;}
    public void start(long seed){
        encounterMode=false;encounterDifficulty=1;encounterHits=encounterScore=encounterShots=0;
        random=new Random(seed);phase=Phase.PLAYING;time=aimX=aimY=recoil=wind=0;
        score=hits=shotsFired=combo=lastRing=lastAward=0;ammo=5;zoomIndex=1;stamina=1;
        reloadRemaining=shotCooldown=pendingImpact=nextTargetDelay=feedbackTime=0;
        holdingBreath=lastHit=false;prepareTarget();event("start");
    }
    /** Goal encounters have no ten-shot ceiling; the shared session owns completion. */
    public void startEncounter(long seed,int difficulty){
        start(seed);encounterMode=true;encounterDifficulty=Math.max(1,difficulty);prepareTarget();
    }
    private float challengeScale(){return encounterMode?(float)Math.log1p((double)encounterDifficulty-1):0;}
    private float motionSpeed(){return .75f*(1+challengeScale()*.14f);}
    private float motionAmplitude(){return .13f*(1+challengeScale()*.10f);}
    private void prepareTarget(){
        feedbackTime=0;
        distance=encounterMode?100+35*challengeScale()+22*(float)Math.log1p(encounterShots)
                :100+shotsFired*25+(shotsFired>=8?25:0);
        wind=encounterShots<2&&encounterDifficulty==1?0:(random.nextFloat()-.5f)*(4+challengeScale()*1.5f);
        targetBaseX=encounterShots==0?0:(random.nextFloat()-.5f)*.8f;
        targetBaseY=encounterShots==0?0:(random.nextFloat()-.5f)*.32f;
        motionPhase=random.nextFloat()*6.283185f;movingTarget=encounterShots>=(encounterMode&&encounterDifficulty>1?2:4);
        updateTarget();aimX=targetX;aimY=targetY+.015f;holdingBreath=false;
    }
    private void updateTarget(){targetX=targetXAt(time);targetY=targetBaseY;}
    public void setAim(float x,float y){if(Float.isFinite(x)&&Float.isFinite(y)){
        float xBound=encounterMode?Math.max(1.6f,Math.abs(windOffset())+1.2f):1.6f;
        float yBound=encounterMode?Math.max(1.1f,dropOffset()+1):1.1f;
        aimX=clamp(x,-xBound,xBound);aimY=clamp(y,-1.1f,yBound);
    }}
    public void moveAim(float x,float y){setAim(aimX+x,aimY+y);}
    public void setHoldingBreath(boolean active){holdingBreath=active&&phase==Phase.PLAYING&&stamina>.04f;}
    public float swayX(){return (float)Math.sin(time*1.7f)*(.016f+(movingTarget?.004f:0))*(1+challengeScale()*.12f)*(holdingBreath?.12f:1);}
    public float swayY(){return (float)Math.cos(time*1.31f)*.012f*(1+challengeScale()*.12f)*(holdingBreath?.12f:1);}
    public float sightX(){return aimX+swayX();}
    public float sightY(){return aimY+swayY()+recoil*.12f;}
    public float windOffset(){return wind*.008f*(distance/100f);}
    public float dropOffset(){float d=distance/100f;return .012f*d*d;}
    public float targetRadius(){return .19f/((1+(distance-100)*.002f)*(1+challengeScale()*.16f));}
    public float flightSeconds(){return .16f+distance*.0006f;}
    public float targetXAt(float atTime){return targetBaseX+(movingTarget?(float)Math.sin(atTime*motionSpeed()+motionPhase)*motionAmplitude():0);}
    /** Useful for deterministic simulation and an optional practice coach, not an automatic shot. */
    public float aimForTargetX(){return targetXAt(time+flightSeconds())-windOffset()-swayX();}
    public float aimForTargetY(){return targetY+dropOffset()-swayY()-recoil*.12f;}
    public float zoom(){return zoomIndex==0?1:zoomIndex==1?3:6;}
    public void changeZoom(){if(phase==Phase.PLAYING){zoomIndex=(zoomIndex+1)%3;event("zoom");}}
    public boolean canFire(){return phase==Phase.PLAYING&&ammo>0&&reloadRemaining<=0&&shotCooldown<=0&&pendingImpact<=0&&nextTargetDelay<=0;}
    public boolean fire(){
        if(!canFire()){if(phase==Phase.PLAYING&&ammo==0)event("empty");return false;}
        impactX=sightX()+windOffset();impactY=sightY()-dropOffset();
        ammo--;if(shotsFired<Integer.MAX_VALUE)shotsFired++;if(encounterShots<Long.MAX_VALUE)encounterShots++;
        shotCooldown=.72f;pendingImpact=flightSeconds();recoil=1;event("fire");return true;
    }
    public boolean reload(){
        if(phase!=Phase.PLAYING||ammo==5||reloadRemaining>0||pendingImpact>0)return false;
        reloadRemaining=1.25f;holdingBreath=false;event("reload");return true;
    }
    public int accuracy(){return shotsFired==0?0:Math.round(hits*100f/shotsFired);}
    public void pause(){if(phase==Phase.PLAYING){phase=Phase.PAUSED;holdingBreath=false;}}
    public void resume(){if(phase==Phase.PAUSED)phase=Phase.PLAYING;}
    public void update(float dt){
        if(phase!=Phase.PLAYING||!Float.isFinite(dt)||dt<=0)return;dt=Math.min(.05f,dt);time+=dt;updateTarget();
        recoil=Math.max(0,recoil-dt*3.4f);shotCooldown=Math.max(0,shotCooldown-dt);feedbackTime=Math.max(0,feedbackTime-dt);
        if(holdingBreath){stamina=Math.max(0,stamina-dt*.20f);if(stamina<=.001f)holdingBreath=false;}
        else stamina=Math.min(1,stamina+dt*.16f);
        if(reloadRemaining>0){reloadRemaining=Math.max(0,reloadRemaining-dt);if(reloadRemaining==0){ammo=5;event("loaded");}}
        if(pendingImpact>0){pendingImpact=Math.max(0,pendingImpact-dt);if(pendingImpact==0)settleImpact();}
        else if(nextTargetDelay>0){nextTargetDelay=Math.max(0,nextTargetDelay-dt);if(nextTargetDelay==0){if(!encounterMode&&shotsFired>=10){phase=Phase.OVER;event("round_over");}else prepareTarget();}}
    }
    private void settleImpact(){
        lastImpactX=impactX-targetX;lastImpactY=impactY-targetY;
        float radius=(float)Math.sqrt(lastImpactX*lastImpactX+lastImpactY*lastImpactY)/targetRadius();
        lastHit=radius<=1;
        if(lastHit){lastRing=Math.min(10,Math.max(1,(int)Math.ceil((1-radius)*10)));
            if(hits<Integer.MAX_VALUE)hits++;if(encounterHits<Long.MAX_VALUE)encounterHits++;
            if(combo<Integer.MAX_VALUE)combo++;lastAward=lastRing*10+Math.min(4,combo-1)*5;
            score=(int)Math.min(Integer.MAX_VALUE,(long)score+lastAward);
            encounterScore=encounterScore>Long.MAX_VALUE-lastAward?Long.MAX_VALUE:encounterScore+lastAward;
            event(lastRing>=9?"bullseye":"hit");}
        else{lastRing=lastAward=combo=0;event("miss");}
        feedbackTime=.95f;nextTargetDelay=.9f;
    }
    private void event(String name){if(listener!=null)listener.event(name);}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
}
