import dev.brickzen.blocks.BlocksEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/** Pure Java mechanic checks; no Android or external test framework. */
public final class BlocksEngineTest {
    public static void main(String[] args){
        sevenBag();wallAndFloorKicks();holdAndDrop();lineClearAndScoring();lockDelayAndPause();topOut();invalidTime();deterministic();encounterLevelsAndCounters();
        System.out.println("PASS: blocks 7-bag, SRS wall/floor kicks, hold lockout, ghost/hard drop, line clears/combo/B2B, lock delay/pause, top-out, bounded clock, deterministic replay");
    }
    private static void sevenBag(){
        for(int seed=0;seed<100;seed++){
            BlocksEngine game=new BlocksEngine();game.start(seed);
            ArrayList<Integer> sequence=new ArrayList<>();sequence.add(game.type);sequence.addAll(game.next);
            for(int start=0;start<14;start+=7)check(new HashSet<>(sequence.subList(start,start+7)).size()==7,"each bag contains all seven shapes");
        }
        int[][] shape=BlocksEngine.shape(3,0);shape[0][0]=500;
        check(BlocksEngine.shape(3,0)[0][0]!=500,"shape API returns defensive coordinates");
    }
    private static BlocksEngine fresh(){BlocksEngine g=new BlocksEngine();g.start(82);return g;}
    private static void wallAndFloorKicks(){
        BlocksEngine g=fresh();g.type=1;g.rotation=1;g.x=-2;g.y=5;
        check(g.fits(g.type,g.rotation,g.x,g.y),"vertical I fits against left wall");
        check(g.rotate(-1)&&g.rotation==0&&g.x==0,"I piece uses two-column wall kick");
        g=fresh();g.type=3;g.rotation=1;g.x=-1;g.y=5;
        check(g.rotate(1)&&g.rotation==2&&g.x==0,"T wall kick moves away from wall");
        g=fresh();g.type=1;g.rotation=0;g.x=3;g.y=18;
        check(g.rotate(1)&&g.rotation==1&&g.y==16,"I floor kick lifts two rows");
        g=fresh();g.type=2;g.rotation=0;g.x=3;g.y=18;
        int beforeX=g.x,beforeY=g.y;check(g.rotate(1)&&g.x==beforeX&&g.y==beforeY,"O rotates without translation");
    }
    private static void holdAndDrop(){
        BlocksEngine g=fresh();int original=g.type,upcoming=g.next.get(0);
        check(g.hold()&&g.heldType==original&&g.type==upcoming,"first hold takes next piece");
        check(!g.hold(),"hold only once before lock");
        int ghost=g.ghostY(),startScore=g.score,startY=g.y;
        check(g.hardDrop()&&g.lockedPieces==1,"hard drop immediately locks");
        check(g.score==startScore+(ghost-startY)*2&&g.holdAvailable,"drop score and hold reset");
        int occupied=0;for(int[] row:g.board)for(int block:row)if(block!=0)occupied++;
        check(occupied==4,"one tetromino locks exactly four cells");
        check(g.hold()&&g.type==original,"later hold swaps with stored piece");
    }
    private static void lineClearAndScoring(){
        BlocksEngine g=fresh();
        for(int row=18;row<20;row++){Arrays.fill(g.board[row],1);g.board[row][4]=g.board[row][5]=0;}
        g.type=2;g.rotation=0;g.x=3;g.y=18;g.hardDrop();
        check(g.lines==2&&g.score==300&&g.lastClearedRows.length==2,"double clears once and scores");
        for(int[] row:g.board)for(int block:row)check(block==0,"double collapses into empty board");
        g=fresh();prepareTetris(g);g.hardDrop();
        check(g.lines==4&&g.score==800&&g.backToBack,"four-line clear scores tetris");
        prepareTetris(g);g.hardDrop();
        check(g.lines==8&&g.score==2050&&g.combo==1,"back-to-back tetris and combo bonus");
        prepareTetris(g);g.hardDrop();check(g.level==2&&g.fallInterval()<.8f,"ten lines advance level and gravity");
    }
    private static void prepareTetris(BlocksEngine g){
        for(int[] row:g.board)Arrays.fill(row,0);
        for(int row=16;row<20;row++){Arrays.fill(g.board[row],6);g.board[row][4]=0;}
        g.type=1;g.rotation=1;g.x=2;g.y=16;
    }
    private static void lockDelayAndPause(){
        BlocksEngine g=fresh();g.type=2;g.rotation=0;g.x=3;g.y=18;
        for(int i=0;i<9;i++)g.update(.05f);
        check(g.lockedPieces==0,"grounded piece has half-second placement window");
        g.pause();float time=g.time;g.update(.1f);check(g.time==time&&!g.hardDrop()&&!g.move(1),"pause freezes all actions");
        g.resume();g.update(.06f);check(g.lockedPieces==1,"placement window finishes after resume");
        g=fresh();g.type=2;g.rotation=0;g.x=3;g.y=18;
        for(int i=0;i<15;i++){g.update(.1f);check(g.move(i%2==0?1:-1),"ground movement allowed");}
        for(int i=0;i<4;i++)g.update(.1f);g.move(-1);g.update(.11f);
        check(g.lockedPieces==1,"lock resets are bounded to prevent infinite stalling");
    }
    private static void topOut(){
        BlocksEngine g=fresh();Arrays.fill(g.board[1],7);g.type=3;g.rotation=0;g.x=3;g.y=-1;
        check(g.ghostY()==-1,"blocked piece remains in spawn zone");g.hardDrop();
        check(g.phase==BlocksEngine.Phase.OVER,"locking above board tops out");
        for(int cell:g.board[0])check(cell==0,"top-out cannot partially write current piece");
        int score=g.score;check(!g.hold()&&!g.rotate(1)&&!g.hardDrop()&&g.score==score,"gameover is terminal");
        g.start(1);check(g.phase==BlocksEngine.Phase.PLAYING&&g.score==0&&g.lines==0&&g.heldType==0,"restart clears all run state");
    }
    private static void invalidTime(){
        BlocksEngine g=fresh();for(float dt:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,0})g.update(dt);
        check(g.time==0,"invalid clock increments ignored");g.update(100);check(g.time<=.1001f,"frame hitch capped");
    }
    private static void deterministic(){
        BlocksEngine a=fresh(),b=fresh();
        for(int frame=0;frame<1000;frame++){
            if(frame%7==0){a.move(frame%2==0?-1:1);b.move(frame%2==0?-1:1);}
            if(frame%13==0){a.rotate(1);b.rotate(1);}
            if(frame%47==0){a.hold();b.hold();}
            if(frame%53==0){a.hardDrop();b.hardDrop();}
            a.update(1f/60);b.update(1f/60);
        }
        check(a.score==b.score&&a.lines==b.lines&&a.type==b.type&&a.phase==b.phase&&a.next.equals(b.next),"seed and inputs reproduce run");
        for(int row=0;row<20;row++)check(Arrays.equals(a.board[row],b.board[row]),"deterministic board");
    }
    private static void encounterLevelsAndCounters(){
        BlocksEngine normal=new BlocksEngine(),encounter=new BlocksEngine();normal.start(42);encounter.startEncounter(42,8);
        check(encounter.level==8&&encounter.fallInterval()<normal.fallInterval(),"encounter difficulty is a starting level, not a three-tier preset");
        for(int frame=0;frame<8;frame++){normal.update(.05f);encounter.update(.05f);}
        check(encounter.y>normal.y,"higher encounter tier actually advances gravity faster");
        prepareTetris(encounter);encounter.hardDrop();prepareTetris(encounter);encounter.hardDrop();prepareTetris(encounter);encounter.hardDrop();
        check(encounter.level==9&&encounter.totalLines==12,"starting difficulty remains after earned levels");
        encounter.totalLines=3_000_000_000L;encounter.lines=Integer.MAX_VALUE;encounter.totalLockedPieces=3_000_000_000L;encounter.lockedPieces=Integer.MAX_VALUE;
        prepareTetris(encounter);encounter.hardDrop();
        check(encounter.objectiveProgress(0)==3_000_000_004L&&encounter.objectiveProgress(1)==3_000_000_001L,"objective counters continue beyond int display range");
        check(encounter.score>=0&&encounter.lines==Integer.MAX_VALUE,"large objective progress cannot wrap UI values negative");
        encounter.startEncounter(4,Integer.MAX_VALUE);encounter.combo=Integer.MAX_VALUE;prepareTetris(encounter);encounter.hardDrop();
        check(encounter.level==Integer.MAX_VALUE&&encounter.score==Integer.MAX_VALUE,"large tiers use saturating score and level arithmetic");
        encounter.start(42);check(encounter.level==1&&encounter.fallInterval()==.8f&&encounter.totalLines==0&&encounter.totalLockedPieces==0,"ordinary restart fully resets encounter state");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
