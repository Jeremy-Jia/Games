package dev.brickzen.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Deterministic falling-block simulation. No Android or wall-clock dependency. */
public final class BlocksEngine {
    public enum Phase { READY, PLAYING, PAUSED, OVER }
    public interface Listener { void onEvent(String event); }
    public static final int COLUMNS = 10, ROWS = 20;
    public static final String[] NAMES = {"", "I", "O", "T", "S", "Z", "J", "L"};
    private static final int[][][][] SHAPES = new int[8][4][4][2];
    private static final int[][] K_A = {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}};
    private static final int[][] K_B = {{0,0},{1,0},{1,-1},{0,2},{1,2}};
    private static final int[][] K_C = {{0,0},{1,0},{1,1},{0,-2},{1,-2}};
    private static final int[][] K_D = {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}};
    private static final int[][] I_A = {{0,0},{-2,0},{1,0},{-2,-1},{1,2}};
    private static final int[][] I_B = {{0,0},{2,0},{-1,0},{2,1},{-1,-2}};
    private static final int[][] I_C = {{0,0},{-1,0},{2,0},{-1,2},{2,-1}};
    private static final int[][] I_D = {{0,0},{1,0},{-2,0},{1,-2},{-2,1}};
    static {
        String[] forms = {"", "....|####|....|....", ".##.|.##.|....|....", ".#.|###|...",
                ".##|##.|...", "##.|.##|...", "#..|###|...", "..#|###|..."};
        for (int type = 1; type <= 7; type++) {
            String[] rows = forms[type].split("\\|"); int n = 0;
            for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++)
                if (rows[y].charAt(x) == '#') SHAPES[type][0][n++] = new int[]{x,y};
            for (int rotation = 1; rotation < 4; rotation++) for (int cell = 0; cell < 4; cell++) {
                int[] old = SHAPES[type][rotation-1][cell];
                SHAPES[type][rotation][cell] = type == 2 ? new int[]{old[0],old[1]}
                        : new int[]{(type == 1 ? 3 : 2)-old[1],old[0]};
            }
        }
    }

    public final int[][] board = new int[ROWS][COLUMNS];
    public final List<Integer> next = new ArrayList<>();
    public Phase phase = Phase.READY;
    public int type, rotation, x, y, heldType, score, lines, level = 1, combo = -1, lockedPieces;
    public long totalLines, totalLockedPieces;
    public boolean holdAvailable = true, backToBack;
    public float time, clearFx;
    public int[] lastClearedRows = new int[0];
    private final Listener listener;
    private Random random = new Random(1);
    private float gravity, lockTimer;
    private int lockResets;
    private int startingLevel=1;

    public BlocksEngine() { this(null); }
    public BlocksEngine(Listener listener) { this.listener = listener; }

    public void start(long seed) {
        random = new Random(seed); next.clear();
        for (int[] row : board) java.util.Arrays.fill(row,0);
        score = lines = lockedPieces = heldType = 0; startingLevel=level=1; totalLines=totalLockedPieces=0; combo = -1;
        backToBack = false; holdAvailable = true; time = clearFx = 0;
        lastClearedRows = new int[0]; phase = Phase.PLAYING;
        fillQueue(); spawn(next.remove(0)); fillQueue(); event("start");
    }
    /** Encounter tiers are continuous positive levels, independent of the target size. */
    public void startEncounter(long seed,int difficulty){
        start(seed);startingLevel=level=Math.max(1,difficulty);
    }
    public long objectiveProgress(int metric){return metric==0?Math.max(totalLines,(long)lines):Math.max(totalLockedPieces,(long)lockedPieces);}
    private void fillQueue() {
        while (next.size() < 7) {
            ArrayList<Integer> bag = new ArrayList<>();
            for (int i = 1; i <= 7; i++) bag.add(i);
            Collections.shuffle(bag,random); next.addAll(bag);
        }
    }
    private void spawn(int piece) {
        type = piece; rotation = 0; x = 3; y = -1;
        gravity = lockTimer = 0; lockResets = 0;
        if (!fits(type,rotation,x,y)) finish();
    }
    public static int[][] shape(int type, int rotation) {
        if (type < 1 || type > 7) return new int[0][2];
        int[][] cells = SHAPES[type][rotation & 3], copy = new int[4][2];
        for (int i = 0; i < 4; i++) { copy[i][0] = cells[i][0]; copy[i][1] = cells[i][1]; }
        return copy;
    }
    public int[][] cells() {
        int[][] result = shape(type,rotation);
        for (int[] cell : result) { cell[0] += x; cell[1] += y; }
        return result;
    }
    public boolean fits(int piece,int turn,int px,int py) {
        if (piece < 1 || piece > 7) return false;
        for (int[] cell : SHAPES[piece][turn & 3]) {
            int xx=px+cell[0], yy=py+cell[1];
            if (xx < 0 || xx >= COLUMNS || yy >= ROWS || yy < -4 || yy >= 0 && board[yy][xx] != 0) return false;
        }
        return true;
    }
    public int ghostY() {
        if (type == 0) return y;
        int result=y;
        while (fits(type,rotation,x,result+1)) result++;
        return result;
    }
    public boolean move(int direction) {
        if (phase != Phase.PLAYING || direction == 0) return false;
        int step=direction < 0 ? -1 : 1;
        if (!fits(type,rotation,x+step,y)) return false;
        boolean grounded=!fits(type,rotation,x,y+1); x+=step; resetLock(grounded); event("move"); return true;
    }
    public boolean rotate(int direction) {
        if (phase != Phase.PLAYING || direction == 0) return false;
        int destination=(rotation+(direction > 0 ? 1 : 3)) & 3;
        int[][] kicks;
        if (type == 2) kicks=new int[][]{{0,0}};
        else if (type == 1) {
            int code=rotation*4+destination;
            kicks=(code==1||code==14)?I_A:(code==4||code==11)?I_B:(code==6||code==3)?I_C:I_D;
        } else {
            int code=rotation*4+destination;
            kicks=(code==1||code==9)?K_A:(code==4||code==6)?K_B:(code==11||code==3)?K_C:K_D;
        }
        boolean grounded=!fits(type,rotation,x,y+1);
        for (int[] kick : kicks) if (fits(type,destination,x+kick[0],y-kick[1])) {
            x+=kick[0]; y-=kick[1]; rotation=destination; resetLock(grounded); event("rotate"); return true;
        }
        return false;
    }
    private void resetLock(boolean grounded) {
        if (grounded && lockResets < 15) { lockTimer=0; lockResets++; }
    }
    public boolean softDrop() {
        if (phase != Phase.PLAYING || !fits(type,rotation,x,y+1)) return false;
        y++; addScore(1); gravity=0; lockTimer=0; return true;
    }
    public boolean hardDrop() {
        if (phase != Phase.PLAYING) return false;
        int landing=ghostY(); addScore((landing-y)*2L); y=landing; lock(); event("drop"); return true;
    }
    public boolean hold() {
        if (phase != Phase.PLAYING || !holdAvailable) return false;
        int previous=heldType; heldType=type;
        if (previous==0) { spawn(next.remove(0)); fillQueue(); } else spawn(previous);
        holdAvailable=false; event("hold"); return true;
    }
    public void pause() { if (phase==Phase.PLAYING) phase=Phase.PAUSED; }
    public void resume() { if (phase==Phase.PAUSED) phase=Phase.PLAYING; }
    public float fallInterval() { return Math.max(.065f, .8f*(float)Math.pow(.82,level-1)); }
    public void update(float dt) {
        if (phase!=Phase.PLAYING || !Float.isFinite(dt) || dt<=0) return;
        dt=Math.min(.1f,dt); time+=dt; clearFx=Math.max(0,clearFx-dt*2.3f); gravity+=dt;
        while (gravity>=fallInterval()) {
            gravity-=fallInterval();
            if (fits(type,rotation,x,y+1)) { y++; lockTimer=0; }
        }
        if (!fits(type,rotation,x,y+1)) {
            lockTimer+=dt;
            if (lockTimer>=.5f) lock();
        } else lockTimer=0;
    }
    private void lock() {
        int[][] cells=cells();
        for (int[] cell : cells) if (cell[1]<0) { finish(); return; }
        for (int[] cell : cells) board[cell[1]][cell[0]]=type;
        totalLines=Math.max(totalLines,(long)lines);totalLockedPieces=Math.max(totalLockedPieces,(long)lockedPieces);
        if(totalLockedPieces<Long.MAX_VALUE)totalLockedPieces++;lockedPieces=(int)Math.min(Integer.MAX_VALUE,totalLockedPieces);ArrayList<Integer> cleared=new ArrayList<>();
        for (int row=0;row<ROWS;row++) {
            boolean full=true;
            for (int column=0;column<COLUMNS;column++) if (board[row][column]==0) { full=false; break; }
            if (full) cleared.add(row);
        }
        int count=cleared.size();
        if (count>0) {
            lastClearedRows=new int[count];for(int i=0;i<count;i++)lastClearedRows[i]=cleared.get(i);
            for (int row : cleared) {
                for (int above=row;above>0;above--) System.arraycopy(board[above-1],0,board[above],0,COLUMNS);
                java.util.Arrays.fill(board[0],0);
            }
            int[] points={0,100,300,500,800};
            int base=points[Math.min(4,count)];
            if(count==4 && backToBack)base=base*3/2;
            if(combo<Integer.MAX_VALUE)combo++;long awarded=(long)base+Math.max(0L,(long)combo)*50;
            addScore(awarded>Integer.MAX_VALUE/(long)level?Integer.MAX_VALUE:awarded*level);
            backToBack=count==4;totalLines=totalLines>Long.MAX_VALUE-count?Long.MAX_VALUE:totalLines+count;
            lines=(int)Math.min(Integer.MAX_VALUE,totalLines);level=(int)Math.min(Integer.MAX_VALUE,(long)startingLevel+totalLines/10);clearFx=1;
            event(count==4?"tetris":"clear");
        } else { combo=-1; event("lock"); }
        holdAvailable=true; spawn(next.remove(0)); fillQueue();
    }
    private void finish() { if(phase!=Phase.OVER){phase=Phase.OVER;event("over");} }
    private void addScore(long amount){score=(int)Math.min(Integer.MAX_VALUE,(long)score+Math.max(0,amount));}
    private void event(String event) { if(listener!=null)listener.onEvent(event); }
}
