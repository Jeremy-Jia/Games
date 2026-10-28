package dev.brickzen.tabletennis;

import java.util.Random;

/** A small deterministic 3D rally simulation. Dimensions and velocities are metres/seconds. */
public final class TableTennisEngine {
    public enum Phase { SERVE, PLAYING, POINT, MATCH_OVER }
    public interface Listener { void event(String name); }
    public static final float HALF_WIDTH=.7625f, HALF_LENGTH=1.37f, NET_HEIGHT=.1525f;
    public static final float BALL_RADIUS=.025f, STRIKE_Z=1.43f, PADDLE_RADIUS=.255f;
    public Phase phase=Phase.SERVE;
    public int playerScore, aiScore, rally, bestRally, difficulty, lastPointWinner;
    public boolean encounterMode;
    public int encounterDifficulty=1;
    public long encounterPoints,encounterRally,encounterBestRally,encounterAiPoints;
    public float playerX, playerVelocity, aiX, ballX, ballY, ballZ, ballVx, ballVy, ballVz;
    public float playerPaddleY=.27f, aiPaddleY=.27f, time, pointTimer, hitFlash;
    public String reason="准备发球";
    public final float[] trailX=new float[22], trailY=new float[22], trailZ=new float[22];
    public int trailCount, trailHead;
    public int lastHitter=1;
    public boolean receivingBounce;
    private boolean serveOwnBouncePending;
    private float playerTarget, aiError, spin, serveTimer;
    private Random random=new Random(1);
    private final Listener listener;

    public TableTennisEngine(Listener listener){this.listener=listener;start(1,1);}
    public void start(long seed,int selectedDifficulty){
        random=new Random(seed);difficulty=Math.max(0,Math.min(2,selectedDifficulty));
        encounterMode=false;encounterDifficulty=1;encounterPoints=encounterAiPoints=encounterRally=encounterBestRally=0;
        playerScore=aiScore=rally=bestRally=lastPointWinner=0;
        playerX=playerTarget=aiX=playerVelocity=time=spin=hitFlash=0;
        playerPaddleY=aiPaddleY=.27f;reason="准备发球";prepareServe();
    }
    public void startEncounter(long seed,int tier){start(seed,1);encounterMode=true;encounterDifficulty=Math.max(1,tier);}
    public long encounterProgress(int metric){return metric==0?encounterPoints:encounterBestRally;}
    public void setPlayerTarget(float x){if(Float.isFinite(x))playerTarget=clamp(x,-.86f,.86f);}
    public boolean matchWon(){return phase==Phase.MATCH_OVER&&playerScore>aiScore;}
    public boolean playerServing(){
        if(encounterMode&&(encounterPoints>=20||encounterAiPoints>=20||encounterPoints+encounterAiPoints>=20))
            return ((encounterPoints^encounterAiPoints)&1L)==0;
        long sum=encounterMode?encounterPoints+encounterAiPoints:(long)playerScore+aiScore;
        return (sum>=20?(sum-20)%2:(sum/2)%2)==0;
    }
    private void prepareServe(){
        phase=Phase.SERVE;serveTimer=.85f;rally=0;encounterRally=0;trailCount=trailHead=0;
        lastHitter=playerServing()?1:-1;receivingBounce=false;serveOwnBouncePending=true;
        ballX=lastHitter==1?playerX:aiX;ballY=.32f;ballZ=lastHitter*1.12f;
        ballVx=ballVy=ballVz=0;spin=0;
    }
    public void serve(){
        if(phase!=Phase.SERVE)return;
        lastHitter=playerServing()?1:-1;
        ballX=lastHitter==1?playerX:aiX;ballY=.32f;ballZ=lastHitter*1.12f;
        float target=lastHitter==1?clamp(playerX*.45f,-.35f,.35f):(random.nextFloat()-.5f)*.65f;
        ballVx=(target-ballX)*1.3f;ballVy=-1.2f;ballVz=-lastHitter*3f;
        receivingBounce=false;serveOwnBouncePending=true;phase=Phase.PLAYING;reason="";event("serve");
    }
    public void update(float dt){
        if(!Float.isFinite(dt)||dt<=0)return;
        dt=Math.min(.05f,dt);
        if(phase==Phase.MATCH_OVER)return;
        int steps=Math.max(1,(int)Math.ceil(dt*180));float step=dt/steps;
        for(int i=0;i<steps;i++)tick(step);
    }
    private void tick(float dt){
        if(phase==Phase.MATCH_OVER)return;
        time+=dt;hitFlash=Math.max(0,hitFlash-dt);
        float oldPlayer=playerX;
        playerX=approach(playerX,playerTarget,4.1f*dt);playerVelocity=(playerX-oldPlayer)/dt;
        float aim=phase==Phase.PLAYING&&ballVz<0?ballX+ballVx*Math.max(0,(-STRIKE_Z-ballZ)/ballVz)+aiError:0;
        float aiSpeed=encounterMode?1.15f+.55f*(float)Math.log1p(encounterDifficulty):(difficulty==0?1.15f:difficulty==1?1.8f:2.7f);
        aiX=approach(aiX,clamp(aim,-.86f,.86f),aiSpeed*dt);
        playerPaddleY=approach(playerPaddleY,phase==Phase.PLAYING&&ballVz>0?clamp(ballY,.12f,.65f):.27f,3*dt);
        aiPaddleY=approach(aiPaddleY,phase==Phase.PLAYING&&ballVz<0?clamp(ballY,.12f,.65f):.27f,3*dt);
        if(phase==Phase.POINT){pointTimer-=dt;if(pointTimer<=0)prepareServe();return;}
        if(phase==Phase.SERVE){
            ballX=playerServing()?playerX:aiX;serveTimer-=dt;if(serveTimer<=0)serve();return;
        }
        float oldZ=ballZ;
        ballVy-=9.8f*dt;ballVx+=spin*dt*.09f;
        ballX+=ballVx*dt;ballY+=ballVy*dt;ballZ+=ballVz*dt;
        trailX[trailHead]=ballX;trailY[trailHead]=ballY;trailZ[trailHead]=ballZ;
        trailHead=(trailHead+1)%trailX.length;trailCount=Math.min(trailCount+1,trailX.length);
        if((oldZ>0&&ballZ<=0||oldZ<0&&ballZ>=0)&&ballY<=NET_HEIGHT+BALL_RADIUS&&Math.abs(ballX)<HALF_WIDTH+.03f){
            award(-lastHitter,"擦网失误");return;
        }
        if(ballVy<0&&ballY<=BALL_RADIUS&&Math.abs(ballX)<=HALF_WIDTH&&Math.abs(ballZ)<=HALF_LENGTH){
            int side=ballZ>=0?1:-1;
            if(serveOwnBouncePending){
                if(side!=lastHitter){award(-lastHitter,"发球未先落在己方台面");return;}
                serveOwnBouncePending=false;
            }else if(side==lastHitter){award(-lastHitter,"回球未过网");return;}
            else if(receivingBounce){award(lastHitter,"两次落台");return;}
            else receivingBounce=true;
            ballY=BALL_RADIUS;ballVy=Math.abs(ballVy)*.87f;ballVx*=.98f;spin*=.7f;event("bounce");
        }
        if(ballVz>0&&oldZ<STRIKE_Z&&ballZ>=STRIKE_Z&&lastHitter==-1&&receivingBounce
                &&Math.abs(ballX-playerX)<=PADDLE_RADIUS&&ballY>=-.1f&&ballY<.9f){
            returnBall(1);return;
        }
        if(ballVz<0&&oldZ>-STRIKE_Z&&ballZ<=-STRIKE_Z&&lastHitter==1&&receivingBounce
                &&Math.abs(ballX-aiX)<=PADDLE_RADIUS-(!encounterMode&&difficulty==0?.035f:0)&&ballY>=-.1f&&ballY<.9f){
            returnBall(-1);return;
        }
        if(ballY<-.45f||Math.abs(ballZ)>2f||Math.abs(ballX)>1.6f)award(receivingBounce?lastHitter:-lastHitter,receivingBounce?"对手未能接回":"回球出界");
    }
    private void returnBall(int hitter){
        ballZ=hitter*STRIKE_Z;ballY=Math.max(.08f,ballY);
        float landingX;
        if(hitter==1)landingX=clamp(playerX*.62f+playerVelocity*.16f+(random.nextFloat()-.5f)*.06f,-.60f,.60f);
        else landingX=clamp((random.nextFloat()-.5f)*(encounterMode?.65f+.15f*(float)Math.log1p(encounterDifficulty):difficulty==0?.65f:1.16f)-playerX*.16f,-.61f,.61f);
        float flight=.49f-Math.min(.075f,rally*.003f)-(encounterMode?.014f*(float)Math.log1p(encounterDifficulty):difficulty==2?.018f:0);
        flight=Math.max(.18f,flight);
        ballVx=(landingX-ballX)/flight;ballVz=(-hitter*.94f-ballZ)/flight;
        ballVy=(BALL_RADIUS-ballY+4.9f*flight*flight)/flight;
        spin=hitter==1?playerVelocity*.45f:(random.nextFloat()-.5f)*1.6f;
        lastHitter=hitter;receivingBounce=false;serveOwnBouncePending=false;
        if(rally<Integer.MAX_VALUE)rally++;bestRally=Math.max(bestRally,rally);
        if(encounterRally<Long.MAX_VALUE)encounterRally++;encounterBestRally=Math.max(encounterBestRally,encounterRally);
        hitFlash=.18f;event("hit");
        aiError=(random.nextFloat()-.5f)*(encounterMode?.27f/(float)Math.sqrt(encounterDifficulty):difficulty==0?.32f:difficulty==1?.16f:.055f);
    }
    private void award(int winner,String why){
        if(phase!=Phase.PLAYING)return;
        lastPointWinner=winner;reason=why;bestRally=Math.max(bestRally,rally);
        if(winner==1){if(playerScore<Integer.MAX_VALUE)playerScore++;if(encounterPoints<Long.MAX_VALUE)encounterPoints++;}
        else{if(aiScore<Integer.MAX_VALUE)aiScore++;if(encounterAiPoints<Long.MAX_VALUE)encounterAiPoints++;}
        ballVx=ballVy=ballVz=0;
        if(!encounterMode&&Math.max(playerScore,aiScore)>=11&&Math.abs(playerScore-aiScore)>=2){phase=Phase.MATCH_OVER;event(matchWon()?"win":"lose");}
        else {phase=Phase.POINT;pointTimer=1.1f;event(winner==1?"point_won":"point_lost");}
    }
    private void event(String name){if(listener!=null)listener.event(name);}
    private static float approach(float value,float target,float step){return value+clamp(target-value,-step,step);}
    public static float clamp(float value,float lo,float hi){return Math.max(lo,Math.min(hi,value));}
}
