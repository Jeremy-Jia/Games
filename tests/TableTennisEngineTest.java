import dev.brickzen.tabletennis.TableTennisEngine;
import java.util.ArrayList;

public final class TableTennisEngineTest {
    public static void main(String[] args){
        legalServeAndRally();netFaultAndOut();doubleBounce();scoreAndDeuce();trackingAndReset();determinismAndBounds();
        System.out.println("PASS: table tennis 3D serve/table/net/paddle, rally/scoring/deuce, AI, bounded step and deterministic reset");
    }
    private static void legalServeAndRally(){
        ArrayList<String> events=new ArrayList<>();TableTennisEngine e=new TableTennisEngine(events::add);e.start(4,1);e.serve();
        int maxRally=0;boolean high=false;
        for(int i=0;i<60*30;i++){
            if(e.ballVz>0){float eta=Math.max(0,(TableTennisEngine.STRIKE_Z-e.ballZ)/e.ballVz);e.setPlayerTarget(e.ballX+e.ballVx*eta);}
            e.update(1f/120);maxRally=Math.max(maxRally,e.rally);high|=e.ballY>.25f;
            if(maxRally>=5)break;
        }
        check(events.contains("bounce")&&events.contains("hit")&&maxRally>=5&&high,"a legitimate serve clears net and supports repeated 3D return rallies");
        check(e.bestRally>=maxRally,"longest rally updated");
    }
    private static TableTennisEngine rally(){TableTennisEngine e=new TableTennisEngine(null);e.start(2,1);e.serve();return e;}
    private static void netFaultAndOut(){
        TableTennisEngine e=rally();e.ballZ=.02f;e.ballY=.08f;e.ballVz=-3;e.ballVy=0;e.ballVx=0;e.update(.02f);
        check(e.phase==TableTennisEngine.Phase.POINT&&e.aiScore==1&&e.playerScore==0,"low strike clips net and awards opponent");
        e=rally();e.ballX=1.59f;e.ballVx=2;e.ballY=.3f;e.receivingBounce=false;e.update(.02f);
        check(e.aiScore==1,"out without receiving bounce loses hitter's point");
        e=rally();e.ballX=1.59f;e.ballVx=2;e.ballY=.3f;e.receivingBounce=true;e.update(.02f);
        check(e.playerScore==1,"valid landing before opponent miss wins hitter's point");
    }
    private static void doubleBounce(){
        TableTennisEngine e=rally();
        // Consume a legal serve's own bounce first.
        for(int i=0;i<60;i++){e.update(1f/240);if(e.ballVy>0)break;}
        e.ballX=0;e.ballY=.026f;e.ballZ=-.6f;e.ballVx=0;e.ballVy=-1;e.ballVz=-.1f;e.receivingBounce=true;e.update(.01f);
        check(e.playerScore==1&&e.reason.equals("两次落台"),"second receiving-half bounce wins point for hitter");
    }
    private static void forceMiss(TableTennisEngine e,int lastHitter){
        e.phase=TableTennisEngine.Phase.PLAYING;e.lastHitter=lastHitter;e.receivingBounce=false;
        e.ballX=1.7f;e.ballY=.2f;e.ballZ=.5f;e.ballVx=e.ballVy=e.ballVz=0;e.update(.01f);
    }
    private static void scoreAndDeuce(){
        TableTennisEngine e=rally();e.playerScore=e.aiScore=10;forceMiss(e,-1);
        check(e.playerScore==11&&e.phase==TableTennisEngine.Phase.POINT,"11 to10 continues until lead bytwo");
        forceMiss(e,-1);check(e.playerScore==12&&e.phase==TableTennisEngine.Phase.MATCH_OVER&&e.matchWon(),"12 to10 wins");
        int score=e.playerScore;float t=e.time;e.update(.05f);e.serve();check(e.playerScore==score&&e.time==t,"terminal match cannot simulate or serve again");
        e.start(3,1);e.playerScore=9;e.aiScore=10;forceMiss(e,1);check(!e.matchWon()&&e.phase==TableTennisEngine.Phase.MATCH_OVER,"opponent can win11 to9");
        e.start(2,1);check(e.playerServing(),"first serve belongs to player");e.playerScore=2;check(!e.playerServing(),"serve alternates every2 points");e.playerScore=10;e.aiScore=10;check(e.playerServing(),"deuce first service");e.playerScore=11;check(!e.playerServing(),"deuce alternates each point");
    }
    private static void trackingAndReset(){
        TableTennisEngine e=rally();e.setPlayerTarget(Float.NaN);e.setPlayerTarget(999);e.update(.05f);
        check(e.playerX>0&&e.playerX<.86f,"paddle tracks target at finite bounded speed");
        float t=e.time;e.update(Float.NaN);e.update(-1);e.update(0);check(e.time==t,"invalid steps ignored");e.update(1000);check(e.time<=t+.051f,"long hitch capped");
        e.start(44,2);check(e.difficulty==2&&e.playerScore==0&&e.aiScore==0&&e.bestRally==0&&e.trailCount==0&&e.phase==TableTennisEngine.Phase.SERVE,"new match resets all transient state");
        e.start(2,99);check(e.difficulty==2,"difficulty bounded");
    }
    private static void determinismAndBounds(){
        TableTennisEngine a=new TableTennisEngine(null),b=new TableTennisEngine(null);a.start(182,1);b.start(182,1);
        for(int i=0;i<60*120;i++){
            float target=(float)Math.sin(i*.02)*.8f;a.setPlayerTarget(target);b.setPlayerTarget(target);a.update(1f/60);b.update(1f/60);
            check(a.ballX==b.ballX&&a.ballY==b.ballY&&a.ballZ==b.ballZ&&a.playerScore==b.playerScore&&a.aiScore==b.aiScore,"seeded deterministic replay");
            check(Float.isFinite(a.ballX)&&Float.isFinite(a.ballY)&&Float.isFinite(a.ballZ)&&Math.abs(a.playerX)<=.861f&&Math.abs(a.aiX)<=.861f,"finite geometry and bounded paddles");
            check(a.trailCount<=a.trailX.length&&a.trailHead<a.trailX.length,"fixed trail capacity");
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
