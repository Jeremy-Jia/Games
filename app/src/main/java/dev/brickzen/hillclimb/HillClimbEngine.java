package dev.brickzen.hillclimb;

import java.util.ArrayList;
import java.util.Random;

/** Deterministic rigid-body hill driving, in metres with positive Y pointing up. */
public final class HillClimbEngine {
    public enum Phase { READY, PLAYING, PAUSED, OVER }
    public interface Listener { void event(String event); }
    public static final float WHEEL_RADIUS=.34f, AXLE=.9f;
    public final ArrayList<Pickup> pickups=new ArrayList<>();
    public Phase phase=Phase.READY;
    public float x,y,vx,vy,angle,angularVelocity,fuel,distance,time,airTime,landingFlash;
    public int coins,groundedWheels;
    public int difficulty=1;
    public long totalCoins;
    public String reason="";
    public static final class Pickup {
        public final float x,y;
        public final boolean fuel;
        public boolean taken;
        Pickup(float x,float y,boolean fuel){this.x=x;this.y=y;this.fuel=fuel;}
    }
    private final Listener listener;
    private float throttle,brake,phaseA,phaseB,crashTimer,stalledTimer,challengeScale=1;
    private int nextPickup;

    public HillClimbEngine(){this(null);}
    public HillClimbEngine(Listener listener){this.listener=listener;start(1);phase=Phase.READY;}
    public void start(long seed){
        difficulty=1;challengeScale=1;
        Random random=new Random(seed);phaseA=random.nextFloat()*6.283185f;phaseB=random.nextFloat()*6.283185f;
        x=5;y=terrainHeight(x)+.70f;vx=vy=angle=angularVelocity=0;fuel=100;distance=time=airTime=landingFlash=0;
        coins=groundedWheels=nextPickup=0;totalCoins=0;throttle=brake=crashTimer=stalledTimer=0;reason="";pickups.clear();
        phase=Phase.PLAYING;fillPickups();
    }
    /** Smooth tier scaling without a fixed easy/normal/hard ceiling. Targets stay host-owned. */
    public void startEncounter(long seed,int tier){
        start(seed);difficulty=Math.max(1,tier);challengeScale=1+.16f*(float)Math.log(difficulty);
        y=terrainHeight(x)+.70f;pickups.clear();nextPickup=0;fillPickups();
    }
    public long objectiveProgress(int metric){return metric==0?(long)distance:Math.max(totalCoins,(long)coins);}
    public void setControls(float gas,float stopping){throttle=finiteClamp(gas,0,1);brake=finiteClamp(stopping,0,1);}
    public void pause(){if(phase==Phase.PLAYING){phase=Phase.PAUSED;setControls(0,0);}}
    public void resume(){if(phase==Phase.PAUSED)phase=Phase.PLAYING;}
    public float terrainHeight(float position){
        float u=Math.max(0,position-8),ramp=Math.min(1,u/32);ramp=ramp*ramp*(3-2*ramp);
        float growth=Math.min(1,Math.max(0,(position-90)/350));
        return challengeScale*ramp*((1.2f+growth*1.3f)*(float)Math.sin(u*.075f)
                +(.45f+growth*.65f)*(float)Math.sin(u*.19f+phaseA)
                +(.12f+growth*.2f)*(float)Math.sin(u*.41f+phaseB));
    }
    public float terrainSlope(float position){return (terrainHeight(position+.025f)-terrainHeight(position-.025f))/.05f;}
    public float wheelX(int wheel){return x+(wheel==0?-AXLE:AXLE)*(float)Math.cos(angle)+.30f*(float)Math.sin(angle);}
    public float wheelY(int wheel){return y+(wheel==0?-AXLE:AXLE)*(float)Math.sin(angle)-.30f*(float)Math.cos(angle);}
    public void update(float dt){
        if(phase!=Phase.PLAYING||!Float.isFinite(dt)||dt<=0)return;
        dt=Math.min(.05f,dt);int steps=Math.max(1,(int)Math.ceil(dt*240));float step=dt/steps;
        for(int i=0;i<steps&&phase==Phase.PLAYING;i++)tick(step);
    }
    private void tick(float dt){
        time+=dt;landingFlash=Math.max(0,landingFlash-dt);
        float fx=-vx*.20f,fy=-12,torque=-angularVelocity*.65f;
        int contacts=0;float slopeSum=0;
        for(int wheel=0;wheel<2;wheel++){
            float wx=wheelX(wheel),wy=wheelY(wheel),slope=terrainSlope(wx);
            float length=(float)Math.sqrt(1+slope*slope),nx=-slope/length,ny=1/length;
            float penetration=terrainHeight(wx)+WHEEL_RADIUS-wy;
            if(penetration>0){
                float rx=wx-x,ry=wy-y;
                float normalVelocity=(vx-angularVelocity*ry)*nx+(vy+angularVelocity*rx)*ny;
                float force=Math.max(0,Math.min(120,penetration*110-normalVelocity*8.7f));
                fx+=nx*force;fy+=ny*force;torque+=(rx*ny-ry*nx)*force;
                contacts++;slopeSum+=slope;
            }
        }
        float drive=fuel>0?throttle:0;
        if(contacts>0){
            float slope=slopeSum/contacts,normal=(float)Math.sqrt(1+slope*slope);
            float tangentVelocity=(vx+vy*slope)/normal;
            float traction=drive*9.4f-brake*clamp(tangentVelocity*4,-14,14);
            fx+=traction/normal;fy+=traction*slope/normal;
            torque+=drive*.55f;
            if(groundedWheels==0&&airTime>.30f){landingFlash=.35f;event("land");}
            airTime=0;
        }else{airTime+=dt;torque+=(throttle-brake)*2.3f;}
        groundedWheels=contacts;
        vx=clamp(vx+fx*dt,-7,22);vy=clamp(vy+fy*dt,-26,26);
        angularVelocity=clamp(angularVelocity+torque/1.2f*dt,-5,5);
        x=Math.max(1,x+vx*dt);y+=vy*dt;angle=wrap(angle+angularVelocity*dt);
        distance=Math.max(distance,Math.max(0,x-5));
        fuel=Math.max(0,fuel-dt*(.22f+drive*.85f)*(1+(challengeScale-1)*.3f));
        float headX=x+.15f*(float)Math.cos(angle)-.61f*(float)Math.sin(angle);
        float headY=y+.15f*(float)Math.sin(angle)+.61f*(float)Math.cos(angle);
        boolean roof= Math.abs(angle)>1.8f && headY<terrainHeight(headX)+.13f;
        crashTimer=roof?crashTimer+dt:Math.max(0,crashTimer-dt*2);
        if(crashTimer>.20f||y<terrainHeight(x)-2){finish("车身翻覆 · 下次用刹车稳住车头");return;}
        stalledTimer=fuel<=0&&Math.abs(vx)<.7f?stalledTimer+dt:0;
        if(stalledTimer>3){finish("燃油耗尽 · 留意沿途补给");return;}
        for(Pickup pickup:pickups)if(!pickup.taken&&Math.abs(pickup.x-x)<1.3f&&Math.abs(pickup.y-y)<1.6f){
            pickup.taken=true;if(pickup.fuel){fuel=Math.min(100,fuel+45);event("fuel");}else{totalCoins=Math.max(totalCoins,(long)coins);if(totalCoins<Long.MAX_VALUE)totalCoins++;coins=(int)Math.min(Integer.MAX_VALUE,totalCoins);event("coin");}
        }
        while(!pickups.isEmpty()&&pickups.get(0).x<x-24)pickups.remove(0);
        fillPickups();
    }
    private void fillPickups(){
        while(pickups.size()<40&&18+nextPickup*8<x+95){float px=18+nextPickup*8;boolean fuelPickup=nextPickup%15==12;
            pickups.add(new Pickup(px,terrainHeight(px)+1.15f,fuelPickup));nextPickup++;}
    }
    private void finish(String why){if(phase==Phase.OVER)return;phase=Phase.OVER;reason=why;setControls(0,0);event("over");}
    private void event(String event){if(listener!=null)listener.event(event);}
    private static float wrap(float radians){while(radians>Math.PI)radians-=Math.PI*2;while(radians< -Math.PI)radians+=Math.PI*2;return radians;}
    private static float finiteClamp(float x,float lo,float hi){return Float.isFinite(x)?clamp(x,lo,hi):0;}
    public static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
}
