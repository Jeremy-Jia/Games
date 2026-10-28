package dev.brickzen.blocks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Locale;
import dev.brickzen.arcade.EncounterSession;

/** Portrait arcade board. Everything, including the jewel pieces, is drawn at device resolution. */
public final class BlocksView extends View {
    public interface Host{
        int bestScore();int bestLines();boolean soundEnabled();boolean hapticsEnabled();
        void setSound(boolean value);void setHaptics(boolean value);void started();void record(int score,int lines);
        void feedback(String event);void quit();
        default EncounterSession encounter(){return null;}
    }
    private static final int BG=0xff081a21,PANEL=0xff132b32,WHITE=0xffeff7f0,MUTED=0xff8da7a9,MINT=0xffb1eed4,GOLD=0xffeed7a6;
    private static final int[] COLORS={0,0xff82c6d8,0xffe9d3a0,0xffbaa5da,0xff9bcba9,0xffe3a0a3,0xff91acdc,0xffe5b38e};
    private static final Typeface REG=Typeface.create("sans-serif",0),MED=Typeface.create("sans-serif-medium",0),LIGHT=Typeface.create("sans-serif-light",0),MONO=Typeface.create("monospace",0);
    public final BlocksEngine engine;
    private final Host host;
    private final EncounterSession encounter;
    private final Paint paint=new Paint(3);
    private final RectF rect=new RectF();
    private final Path path=new Path();
    private final ArrayList<Button> buttons=new ArrayList<>();
    private float scale=1,h=850,top=26,bottom=16,boardX,boardY,cell,controlsY,clock;
    private int insetTop,insetBottom;
    private long lastFrame;
    private boolean home=true,help,suspended=true,recorded,encounterStarted;
    private String pressed,repeatAction;
    private float downX,downY,lastTouchX,lastTouchY;
    private boolean boardGesture,gestureMoved;
    private static final class Button{
        final String action;final RectF bounds;
        Button(String action,float x,float y,float w,float h){this.action=action;bounds=new RectF(x,y,x+w,y+h);}
    }
    private final Runnable repeater=new Runnable(){@Override public void run(){
        if(repeatAction!=null&&!suspended&&engine.phase==BlocksEngine.Phase.PLAYING){performAction(repeatAction);postDelayed(this,75);}
    }};
    public BlocksView(Context context,Host host){
        super(context);this.host=host;encounter=host.encounter();engine=new BlocksEngine(host::feedback);
        setFocusable(true);setClickable(true);setContentDescription("叠界，俄罗斯方块。下方按钮左右移动、旋转、软降和直落；右侧可暂存方块。顶部暂停。");
        setOnApplyWindowInsetsListener((v,insets)->{
            insetTop=insets.getSystemWindowInsetTop();insetBottom=insets.getSystemWindowInsetBottom();
            if(android.os.Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null)insetTop=Math.max(insetTop,insets.getDisplayCutout().getSafeInsetTop());
            updateLayout();return insets;
        });
    }
    @Override protected void onSizeChanged(int width,int height,int oldw,int oldh){scale=width/400f;h=height/scale;updateLayout();requestApplyInsets();}
    private void updateLayout(){
        top=Math.max(24,insetTop/Math.max(.01f,scale)+8);bottom=Math.max(14,insetBottom/Math.max(.01f,scale)+6);
        controlsY=h-bottom-132;boardY=top+119+(encounter!=null?62:0);cell=Math.min(24,Math.max(10,(controlsY-boardY-20)/20));boardX=24;
    }
    public void startRun(long seed){
        cancelRepeats();home=false;help=false;recorded=false;
        if(encounter!=null){encounterStarted=true;engine.startEncounter(encounter.plan.seed,encounter.plan.difficulty);}else engine.start(seed);
        host.started();lastFrame=0;updateLayout();invalidate();
    }
    public boolean performAction(String action){
        if(encounter!=null&&encounter.isEnding())return false;
        if(encounter!=null&&(action.equals("home")||action.equals("quit"))){cancelRepeats();encounter.cancel();return true;}
        boolean result=true;
        switch(action){
            case "start":case "restart":startRun(System.nanoTime());break;
            case "left":result=engine.move(-1);break;
            case "right":result=engine.move(1);break;
            case "rotate":result=engine.rotate(1);break;
            case "soft":result=engine.softDrop();break;
            case "drop":result=engine.hardDrop();break;
            case "hold":result=engine.hold();break;
            case "pause":engine.pause();cancelRepeats();break;
            case "resume":engine.resume();lastFrame=0;break;
            case "home":engine.pause();home=true;help=false;cancelRepeats();break;
            case "quit":host.quit();break;
            case "help":help=true;break;
            case "closeHelp":help=false;break;
            case "sound":host.setSound(!host.soundEnabled());break;
            case "haptics":host.setHaptics(!host.hapticsEnabled());break;
            default:result=false;
        }
        invalidate();return result;
    }
    public RectF getActionBounds(String action){for(Button button:buttons)if(button.action.equals(action))return new RectF(button.bounds);return null;}
    public void onHostPause(){suspended=true;engine.pause();cancelRepeats();lastFrame=0;invalidate();}
    public void onHostResume(){suspended=false;lastFrame=0;if(encounter!=null&&!encounterStarted)startRun(encounter.plan.seed);invalidate();}
    public boolean handleBack(){if(help){help=false;invalidate();return true;}if(home)return false;if(engine.phase==BlocksEngine.Phase.PLAYING)performAction("pause");else performAction("home");return true;}
    private void cancelRepeats(){repeatAction=null;pressed=null;removeCallbacks(repeater);boardGesture=false;}
    @Override protected void onDetachedFromWindow(){cancelRepeats();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(getWidth()==0)return;
        long now=SystemClock.elapsedRealtimeNanos();float dt=lastFrame==0?0:Math.min(.04f,(now-lastFrame)/1_000_000_000f);lastFrame=now;
        if(suspended)dt=0;clock+=dt;
        if(!home&&!help&&!suspended&&(encounter==null||!encounter.isEnding()))engine.update(dt);
        if(encounter!=null&&encounterStarted&&!suspended){encounter.observe(engine.objectiveProgress(encounter.plan.metric),engine.phase==BlocksEngine.Phase.OVER);if(encounter.isEnding())cancelRepeats();}
        if(encounter==null&&!home&&engine.phase==BlocksEngine.Phase.OVER&&!recorded){host.record(engine.score,engine.lines);recorded=true;}
        buttons.clear();canvas.save();canvas.scale(scale,scale);
        paint.setShader(new LinearGradient(0,0,400,h,0xff143039,BG,Shader.TileMode.CLAMP));canvas.drawRect(0,0,400,h,paint);paint.setShader(null);
        if(home)drawHome(canvas);else{drawGame(canvas);if(engine.phase==BlocksEngine.Phase.PAUSED)drawPause(canvas);else if(encounter==null&&engine.phase==BlocksEngine.Phase.OVER)drawOver(canvas);}
        if(encounter!=null&&encounterStarted){encounter.draw(canvas,24,top+108,352);if(encounter.isEnding())buttons.clear();}
        if(help)drawHelp(canvas);
        canvas.restore();if(!suspended)postInvalidateOnAnimation();
    }
    private void drawHome(Canvas c){
        smallButton(c,"quit",24,top,38,38,"‹");
        text(c,"S T A C K   /   B L O C K S",76,top+24,9,MINT,MONO,Paint.Align.LEFT);
        text(c,"叠界",26,top+91,43,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"让每一块，都落在刚刚好的位置。",28,top+121,12,MUTED,REG,Paint.Align.LEFT);
        float artY=top+160,artH=Math.min(260,Math.max(100,h-top-bottom-420));
        round(c,24,artY,352,Math.max(130,artH),26,0xff10272e);border(c,24,artY,352,Math.max(130,artH),26,0xff25434a,1);
        float size=Math.min(26,(artH-32)/7),centerX=200-5*size;
        int[][] art={{6,6,0,0,0,0,0,0,0,0},{0,6,0,0,3,0,0,0,0,0},{0,6,0,3,3,3,0,0,0,0},{0,0,0,0,0,0,0,2,2,0},{4,4,0,0,0,0,0,2,2,0},{7,4,4,0,0,5,5,1,0,0},{7,7,7,0,5,5,0,1,0,0}};
        float baseY=artY+Math.max(130,artH)-25;
        for(int row=0;row<art.length;row++)for(int column=0;column<10;column++)if(art[row][column]!=0)
            jewel(c,centerX+column*size,baseY-(7-row)*size,size-2,art[row][column],false);
        for(int i=0;i<4;i++)jewel(c,centerX+(4+i)*size,artY+24+(float)Math.sin(clock*.8)*4,size-2,1,false);
        float statsY=artY+Math.max(130,artH)+32;
        text(c,"个人最佳",28,statsY,10,MUTED,REG,Paint.Align.LEFT);
        text(c,String.format(Locale.US,"%,d",host.bestScore()),28,statsY+33,28,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,host.bestLines()+" 行",372,statsY+30,17,MINT,MONO,Paint.Align.RIGHT);
        float startY=h-bottom-171;
        button(c,"start",24,startY,352,58,"开始堆叠",true);
        button(c,"help",24,startY+68,352,42,"玩法与手势",false);
        pill(c,"sound",24,h-bottom-38,169,32,"声音  "+(host.soundEnabled()?"开":"关"),host.soundEnabled());
        pill(c,"haptics",207,h-bottom-38,169,32,"触感  "+(host.hapticsEnabled()?"开":"关"),host.hapticsEnabled());
    }
    private void drawGame(Canvas c){
        smallButton(c,"home",24,top,36,36,"‹");text(c,"叠界",73,top+26,23,WHITE,MED,Paint.Align.LEFT);
        smallButton(c,"pause",336,top,40,38,"Ⅱ");
        text(c,"SCORE",24,top+67,9,MUTED,MONO,Paint.Align.LEFT);
        text(c,String.format(Locale.US,"%,d",engine.score),24,top+101,30,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"等级 "+engine.level,375,top+69,12,MINT,MED,Paint.Align.RIGHT);
        text(c,"消除 "+engine.lines+" 行",375,top+96,11,MUTED,REG,Paint.Align.RIGHT);
        float bw=cell*10,bh=cell*20;
        round(c,boardX-7,boardY-7,bw+14,bh+14,16,0xff07161d);border(c,boardX-7,boardY-7,bw+14,bh+14,16,0xff29464b,1);
        for(int row=0;row<20;row++)for(int column=0;column<10;column++){
            float xx=boardX+column*cell,yy=boardY+row*cell;
            round(c,xx+.8f,yy+.8f,cell-1.6f,cell-1.6f,2,0xff10252c);
            if(engine.board[row][column]!=0)jewel(c,xx+1,yy+1,cell-2,engine.board[row][column],false);
        }
        if(engine.phase!=BlocksEngine.Phase.OVER){
            int ghost=engine.ghostY();
            for(int[] block:BlocksEngine.shape(engine.type,engine.rotation)){
                int xx=engine.x+block[0],yy=ghost+block[1];
                if(yy>=0)jewel(c,boardX+xx*cell+1,boardY+yy*cell+1,cell-2,engine.type,true);
            }
            for(int[] block:engine.cells())if(block[1]>=0)
                jewel(c,boardX+block[0]*cell+1,boardY+block[1]*cell+1,cell-2,engine.type,false);
        }
        if(engine.clearFx>0){
            for(int row:engine.lastClearedRows)round(c,boardX,boardY+row*cell,bw,cell,2,alpha(MINT,(int)(engine.clearFx*110)));
        }
        float sideX=boardX+bw+21,sideW=400-sideX-24;
        sideCard(c,sideX,boardY,sideW,104,"暂存",engine.heldType,!engine.holdAvailable);
        buttons.add(new Button("hold",sideX,boardY,sideW,104));
        text(c,"NEXT",sideX+sideW/2,boardY+135,9,MUTED,MONO,Paint.Align.CENTER);
        int previews=Math.min(engine.next.size(),Math.max(1,Math.min(4,(int)((bh-190)/63)+1)));
        for(int i=0;i<previews;i++)preview(c,engine.next.get(i),sideX+sideW/2,boardY+166+i*63,Math.min(14,sideW/5),false);
        if(engine.combo>0&&engine.clearFx>0){text(c,(engine.combo+1)+" 连消",sideX+sideW/2,boardY+bh-9,10,GOLD,MED,Paint.Align.CENTER);}
        else if(engine.lines>0&&engine.lines%10>=7)text(c,"即将提速",sideX+sideW/2,boardY+bh-9,9,MUTED,REG,Paint.Align.CENTER);
        String[] actions={"left","rotate","right","soft"},glyphs={"←","↻","→","↓"},labels={"左移","旋转","右移","软降"};
        for(int i=0;i<4;i++){
            float xx=24+i*90;round(c,xx,controlsY,82,54,14,actions[i].equals(pressed)?0xff31544f:PANEL);
            border(c,xx,controlsY,82,54,14,0xff31504f,1);
            text(c,glyphs[i],xx+41,controlsY+26,23,MINT,REG,Paint.Align.CENTER);
            text(c,labels[i],xx+41,controlsY+44,8,MUTED,REG,Paint.Align.CENTER);
            buttons.add(new Button(actions[i],xx,controlsY,82,54));
        }
        button(c,"drop",24,controlsY+63,262,54,"直落  ↓↓",true);
        button(c,"hold",294,controlsY+63,82,54,"暂存",false);
        text(c,"← → 滑动移动  ·  轻点旋转",200,controlsY-11,8.5f,MUTED,REG,Paint.Align.CENTER);
    }
    private void drawPause(Canvas c){
        overlay(c);buttons.clear();float y=Math.max(top+40,(h-320)/2);
        text(c,"TAKE A BREATH",200,y,9,MINT,MONO,Paint.Align.CENTER);
        text(c,"让灵感歇一会",200,y+43,29,WHITE,LIGHT,Paint.Align.CENTER);
        text(c,"方块会等你，计时已经暂停。",200,y+74,11,MUTED,REG,Paint.Align.CENTER);
        button(c,"resume",40,y+106,320,56,"继续堆叠",true);
        if(encounter!=null)button(c,"home",40,y+180,320,48,"结束奇遇，返回碰界",false);
        else{button(c,"restart",40,y+173,320,48,"重新开始",false);button(c,"home",40,y+231,320,45,"返回叠界",false);}
    }
    private void drawOver(Canvas c){
        overlay(c);buttons.clear();float y=Math.max(top+30,(h-370)/2);
        text(c,"ONE MORE PIECE",200,y,9,GOLD,MONO,Paint.Align.CENTER);
        text(c,"留一点，再来一次",200,y+43,28,WHITE,LIGHT,Paint.Align.CENTER);
        round(c,40,y+72,320,139,23,PANEL);
        text(c,String.format(Locale.US,"%,d",engine.score),200,y+132,42,MINT,LIGHT,Paint.Align.CENTER);
        text(c,"消除 "+engine.lines+" 行  ·  等级 "+engine.level,200,y+166,12,MUTED,REG,Paint.Align.CENTER);
        text(c,engine.score>=host.bestScore()?"个人最佳":"最佳 "+host.bestScore(),200,y+192,10,GOLD,REG,Paint.Align.CENTER);
        button(c,"restart",40,y+235,320,57,"再来一局",true);
        button(c,"home",40,y+303,320,46,"返回叠界",false);
    }
    private void drawHelp(Canvas c){
        overlay(c);buttons.clear();float y=Math.max(top+15,(h-470)/2);
        round(c,24,y,352,454,25,PANEL);
        text(c,"方块，刚刚好",45,y+48,27,WHITE,LIGHT,Paint.Align.LEFT);
        String[] titles={"拼满一行，整行消除","手指轻动，落点可见","暂存一块，为下一步留白"};
        String[] bodies={"七种方块轮流出现。消除越多，等级越高，方块下落也会变快。连续消行可获得额外分数。","按钮左右移动、顺时针旋转、软降；按住移动按钮可连续操作。薄线轮廓是预计落点，点击“直落”立即落下。","点右侧暂存或下方按钮交换方块，每块只能暂存一次。棋盘上轻点旋转，横滑移动，下滑直落，上滑暂存。"};
        for(int i=0;i<3;i++){
            text(c,"0"+(i+1),45,y+94+i*94,10,MINT,MONO,Paint.Align.LEFT);
            text(c,titles[i],75,y+94+i*94,14,WHITE,MED,Paint.Align.LEFT);
            wrapped(c,bodies[i],45,y+120+i*94,309,10.5f,MUTED,16);
        }
        button(c,"closeHelp",44,y+383,312,47,"开始发现",true);
    }
    private void sideCard(Canvas c,float x,float y,float width,float height,String title,int type,boolean disabled){
        round(c,x,y,width,height,14,PANEL);border(c,x,y,width,height,14,disabled?0xff263f45:0xff45665f,1);
        text(c,title,x+width/2,y+21,10,disabled?MUTED:MINT,REG,Paint.Align.CENTER);
        if(type==0)text(c,"＋",x+width/2,y+64,25,0xff43615f,LIGHT,Paint.Align.CENTER);
        else preview(c,type,x+width/2,y+58,Math.min(14,width/5),disabled);
        if(disabled)text(c,"下块可用",x+width/2,y+91,7.5f,MUTED,REG,Paint.Align.CENTER);
    }
    private void preview(Canvas c,int type,float cx,float cy,float size,boolean disabled){
        int[][] blocks=BlocksEngine.shape(type,0);int minX=4,maxX=0,minY=4,maxY=0;
        for(int[] b:blocks){minX=Math.min(minX,b[0]);maxX=Math.max(maxX,b[0]);minY=Math.min(minY,b[1]);maxY=Math.max(maxY,b[1]);}
        for(int[] b:blocks)jewel(c,cx+(b[0]-(minX+maxX+1)/2f)*size,cy+(b[1]-(minY+maxY+1)/2f)*size,size-2,type,disabled);
    }
    private void jewel(Canvas c,float x,float y,float size,int type,boolean ghost){
        int color=COLORS[Math.max(1,Math.min(7,type))];
        if(ghost){round(c,x,y,size,size,Math.max(2,size*.15f),alpha(color,18));border(c,x,y,size,size,Math.max(2,size*.15f),alpha(color,125),1);return;}
        round(c,x,y,size,size,Math.max(2,size*.15f),color);
        round(c,x+2,y+2,Math.max(1,size-4),Math.max(1,size*.18f),1,0x46ffffff);
        paint.setColor(0x17000000);c.drawRect(x+2,y+size-3,x+size-2,y+size-1,paint);
    }
    private void button(Canvas c,String action,float x,float y,float w,float h,String title,boolean primary){
        round(c,x,y,w,h,16,primary?MINT:PANEL);if(!primary)border(c,x,y,w,h,16,0xff304e51,1);
        text(c,title,x+w/2,y+h/2+5,primary?15:13,primary?BG:WHITE,MED,Paint.Align.CENTER);buttons.add(new Button(action,x,y,w,h));
    }
    private void pill(Canvas c,String action,float x,float y,float w,float h,String title,boolean on){
        round(c,x,y,w,h,16,on?0xff213f3c:0xff10262f);text(c,title,x+w/2,y+h/2+4,10,on?MINT:MUTED,REG,Paint.Align.CENTER);buttons.add(new Button(action,x,y,w,h));
    }
    private void smallButton(Canvas c,String action,float x,float y,float w,float h,String glyph){round(c,x,y,w,h,13,PANEL);text(c,glyph,x+w/2,y+h/2+7,23,MINT,REG,Paint.Align.CENTER);buttons.add(new Button(action,x,y,w,h));}
    private void overlay(Canvas c){paint.setColor(0xed06161e);c.drawRect(0,0,400,h,paint);}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface font,Paint.Align align){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setTypeface(font);paint.setTextAlign(align);paint.setTextSize(size);paint.setColor(color);c.drawText(s,x,y,paint);}
    private void round(Canvas c,float x,float y,float w,float h,float radius,int color){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,radius,radius,paint);}
    private void border(Canvas c,float x,float y,float w,float h,float radius,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.FILL);}
    private void wrapped(Canvas c,String s,float x,float y,float width,float size,int color,float pitch){
        paint.setTypeface(REG);paint.setTextSize(size);StringBuilder line=new StringBuilder();
        for(int i=0;i<s.length();i++){String next=line.toString()+s.charAt(i);if(paint.measureText(next)>width&&line.length()>0){text(c,line.toString(),x,y,size,color,REG,Paint.Align.LEFT);y+=pitch;line.setLength(0);}line.append(s.charAt(i));}
        if(line.length()>0)text(c,line.toString(),x,y,size,color,REG,Paint.Align.LEFT);
    }
    private static int alpha(int color,int alpha){return(color&0x00ffffff)|(Math.max(0,Math.min(255,alpha))<<24);}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(encounter!=null&&encounter.isEnding()){cancelRepeats();return true;}
        if(suspended)return true;float px=event.getX()/scale,py=event.getY()/scale;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=lastTouchX=px;downY=lastTouchY=py;pressed=null;gestureMoved=false;
                for(int i=buttons.size()-1;i>=0;i--)if(buttons.get(i).bounds.contains(px,py)){pressed=buttons.get(i).action;break;}
                boardGesture=pressed==null&&!home&&!help&&engine.phase==BlocksEngine.Phase.PLAYING&&px>=boardX&&px<=boardX+10*cell&&py>=boardY&&py<=boardY+20*cell;
                if("left".equals(pressed)||"right".equals(pressed)||"soft".equals(pressed)){repeatAction=pressed;performAction(pressed);postDelayed(repeater,260);}
                invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(boardGesture){if(Math.abs(px-downX)>12||Math.abs(py-downY)>12)gestureMoved=true;while(px-lastTouchX>cell*.8f){engine.move(1);lastTouchX+=cell*.8f;}while(lastTouchX-px>cell*.8f){engine.move(-1);lastTouchX-=cell*.8f;}}
                return true;
            case MotionEvent.ACTION_UP:
                String action=pressed;boolean repeated=repeatAction!=null;boolean gesture=boardGesture;
                cancelRepeats();
                if(gesture){float dx=px-downX,dy=py-downY;if(dy>45&&Math.abs(dy)>Math.abs(dx))performAction("drop");else if(dy< -45&&Math.abs(dy)>Math.abs(dx))performAction("hold");else if(Math.abs(dx)<12&&Math.abs(dy)<12&&!gestureMoved)performAction("rotate");}
                else if(action!=null&&!repeated){for(Button button:buttons)if(button.action.equals(action)&&button.bounds.contains(px,py)){performAction(action);break;}}
                performClick();invalidate();return true;
            case MotionEvent.ACTION_CANCEL:cancelRepeats();invalidate();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
