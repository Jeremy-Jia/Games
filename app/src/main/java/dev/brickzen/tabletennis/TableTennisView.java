package dev.brickzen.tabletennis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.util.ArrayList;

/** A perspective camera renders a 3D court, moving ball, paddles, mesh net and shadows. */
public final class TableTennisView extends View {
    private static final int BG=0xff08171d, INK=0xffedf7f0, MINT=0xffa5ebcd, MUTED=0xff8ea9a9, RED=0xffffa18d;
    private static final Typeface NORMAL=Typeface.create("sans-serif",0), MEDIUM=Typeface.create("sans-serif-medium",0), MONO=Typeface.create("monospace",0);
    public final TableTennisEngine engine;
    private final TableTennisActivity host;
    private final Paint paint=new Paint(3);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private final ArrayList<Button> buttons=new ArrayList<>();
    public String screen="menu";
    public boolean paused;
    private boolean suspended=true, recorded;
    private long lastFrame;
    private float scale=1,h=870,top=34,bottom=18,ambient,flash;
    private int difficulty=1, activePointer=-1;
    private String pressed="";
    private static final class Button{final String id;final RectF rect;Button(String id,float x,float y,float w,float h){this.id=id;rect=new RectF(x,y,x+w,y+h);}}
    public TableTennisView(Context context,TableTennisActivity host){
        super(context);this.host=host;
        difficulty=host.preferences.getInt("difficulty",1);
        engine=new TableTennisEngine(name->{host.feedback(name);if(name.equals("hit"))flash=.3f;});
        setFocusable(true);setContentDescription("弧线 3D 乒乓球，左右拖动球拍，滑动速度影响回球落点");
    }
    @Override protected void onSizeChanged(int w,int hh,int oldw,int oldh){scale=w/400f;h=hh/scale;readInsets();}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){readInsets();return super.onApplyWindowInsets(insets);}
    private void readInsets(){
        top=32;bottom=16;
        if(Build.VERSION.SDK_INT>=28&&getRootWindowInsets()!=null&&getRootWindowInsets().getDisplayCutout()!=null)
            top=Math.max(top,getRootWindowInsets().getDisplayCutout().getSafeInsetTop()/Math.max(.1f,scale)+9);
    }
    public void startMatch(int chosen){difficulty=Math.max(0,Math.min(2,chosen));host.preferences.edit().putInt("difficulty",difficulty).apply();engine.start(System.nanoTime(),difficulty);screen="play";paused=false;recorded=false;lastFrame=0;invalidate();}
    public void onHostPause(){suspended=true;lastFrame=0;activePointer=-1;pressed="";if(screen.equals("play"))paused=true;}
    public void onHostResume(){suspended=false;lastFrame=0;invalidate();}
    public void resumeMatch(){paused=false;lastFrame=0;invalidate();}
    public boolean handleBack(){if(screen.equals("play")){if(!paused){paused=true;}else{host.rememberRally(engine.bestRally);screen="menu";paused=false;}invalidate();return true;}return false;}
    @Override protected void onDraw(Canvas canvas){
        long now=System.nanoTime();float dt=lastFrame==0?0:Math.min(.05f,(now-lastFrame)/1e9f);lastFrame=now;
        if(!suspended){ambient+=dt;flash=Math.max(0,flash-dt);if(screen.equals("play")&&!paused){engine.update(dt);if(engine.phase==TableTennisEngine.Phase.MATCH_OVER&&!recorded){recorded=true;host.recordMatch(engine.matchWon(),engine.bestRally);}}}
        canvas.save();canvas.scale(scale,scale);buttons.clear();background(canvas);
        if(screen.equals("menu"))menu(canvas);else{court(canvas);hud(canvas);if(engine.phase==TableTennisEngine.Phase.MATCH_OVER)result(canvas);else if(paused)pause(canvas);}
        canvas.restore();if(!suspended)postInvalidateOnAnimation();
    }
    private void background(Canvas c){
        paint.setShader(new LinearGradient(0,0,400,h,new int[]{0xff102b31,BG,0xff071217},null,Shader.TileMode.CLAMP));c.drawRect(0,0,400,h,paint);paint.setShader(null);
        paint.setColor(0x144cdac5);for(int i=0;i<7;i++){float x=37+i*59;line(c,x,top+170,x-75,h-bottom-180,.5f,0x0d92c7bb);}
        circle(c,330,h*.26f,150,0x0619eac4);
    }
    private void menu(Canvas c){
        text(c,"CURVE / TABLE TENNIS",28,top+16,9,MINT,MONO);
        text(c,"弧线",26,top+63,42,INK,NORMAL);
        text(c,"一张球台，下一拍的可能。",28,top+91,12,MUTED,NORMAL);
        float oldY=engine.ballY,oldX=engine.ballX,oldZ=engine.ballZ;
        engine.ballX=(float)Math.sin(ambient*.9)*.45f;engine.ballY=.25f+(float)Math.abs(Math.sin(ambient*1.5))*.25f;engine.ballZ=(float)Math.sin(ambient*1.5)*.9f;
        court(c);engine.ballY=oldY;engine.ballX=oldX;engine.ballZ=oldZ;
        float y=Math.max(top+370,h-bottom-240);
        text(c,"选择你的对手",28,y,11,MUTED,NORMAL);
        String[] names={"轻松","标准","挑战"};for(int i=0;i<3;i++){float x=28+i*117;button(c,"difficulty"+i,x,y+15,110,39,names[i],difficulty==i);}
        button(c,"start",28,y+71,344,54,"上场挥拍   ↗",true);
        textCenter(c,"左右拖动球拍 · 自动接击 · 快滑改变落点",200,y+149,10,MUTED,NORMAL);
        textCenter(c,"11 分制，领先 2 分获胜",200,y+169,10,MUTED,NORMAL);
        textCenter(c,"最佳连续回击  "+host.preferences.getInt("best_streak",0)+"    ·    胜场  "+host.preferences.getInt("wins",0),200,y+196,10,MINT,MONO);
        button(c,"home",28,top+111,82,30,"‹ 游戏集",false);
    }
    private float[] project(float x,float y,float z){
        float dy=y-2.8f,dz=z-4.6f,depth=-.52f*dy-.854f*dz;
        float anchor=screen.equals("menu")?Math.min(h*.48f,top+270):h*.48f;
        return new float[]{200+x*900/depth,anchor-(.854f*dy-.52f*dz)*900/depth,900/depth};
    }
    private void worldLine(Canvas c,float x1,float y1,float z1,float x2,float y2,float z2,float width,int color){float[] a=project(x1,y1,z1),b=project(x2,y2,z2);line(c,a[0],a[1],b[0],b[1],width,color);}
    private void worldQuad(Canvas c,float y,float x0,float z0,float x1,float z1,int color){float[] a=project(x0,y,z0),b=project(x1,y,z0),d=project(x1,y,z1),e=project(x0,y,z1);quad(c,a,b,d,e,color);}
    private void quad(Canvas c,float[] a,float[] b,float[] d,float[] e,int color){path.reset();path.moveTo(a[0],a[1]);path.lineTo(b[0],b[1]);path.lineTo(d[0],d[1]);path.lineTo(e[0],e[1]);path.close();paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawPath(path,paint);}
    private void court(Canvas c){
        // Floor, table legs and apron are all projected with the same camera as the ball.
        worldQuad(c,-.76f,-1.6f,-2.1f,1.6f,2.1f,0xff10252a);
        for(int i=-3;i<=3;i++){worldLine(c,i*.45f,-.755f,-2.1f,i*.45f,-.755f,2.1f,.7f,0xff1e363a);worldLine(c,-1.6f,-.755f,i*.6f,1.6f,-.755f,i*.6f,.7f,0xff1e363a);}
        float[] shadow=project(0,-.74f,.25f);oval(c,shadow[0]-170,shadow[1]-60,340,110,0x55000000);
        for(int side:new int[]{-1,1})for(int end:new int[]{-1,1}){
            worldLine(c,side*.59f,-.035f,end*1.05f,side*.67f,-.75f,end*1.11f,6,0xff506062);
            worldLine(c,side*.59f,-.035f,end*1.05f,side*.67f,-.75f,end*1.11f,2,0xffa2b4ad);
        }
        float[] a=project(-.7625f,0,-1.37f),b=project(.7625f,0,-1.37f),d=project(.7625f,0,1.37f),e=project(-.7625f,0,1.37f);
        quad(c,e,d,project(.7625f,-.075f,1.37f),project(-.7625f,-.075f,1.37f),0xff47605b);
        quad(c,b,d,project(.7625f,-.075f,1.37f),project(.7625f,-.075f,-1.37f),0xff213d3d);
        quad(c,a,b,d,e,0xff236a67);
        worldQuad(c,.001f,-.73f,-1.337f,.73f,1.337f,0xff287570);
        worldLine(c,-.747f,.006f,-1.35f,.747f,.006f,-1.35f,1.5f,0xffd0e9d5);
        worldLine(c,-.747f,.006f,1.35f,.747f,.006f,1.35f,1.5f,0xffd0e9d5);
        worldLine(c,-.747f,.006f,-1.35f,-.747f,.006f,1.35f,1.5f,0xffd0e9d5);
        worldLine(c,.747f,.006f,-1.35f,.747f,.006f,1.35f,1.5f,0xffd0e9d5);
        worldLine(c,0,.008f,-1.35f,0,.008f,1.35f,.75f,0xffd0e9d5);
        textCenter(c,"C U R V E",200,(a[1]+e[1])*.5f+49,9,0x44d8fff1,MONO);
        paddle(c,engine.aiX,engine.aiPaddleY,-TableTennisEngine.STRIKE_Z,false);
        if(engine.ballZ<0)ball(c);
        // Net is a genuine vertical plane with transparent mesh, posts and a white top tape.
        float[] nl=project(-.81f,.1525f,0),nr=project(.81f,.1525f,0),nb=project(.81f,0,0),ne=project(-.81f,0,0);
        quad(c,nl,nr,nb,ne,0x993b5659);
        for(int i=0;i<=24;i++){float x=-.81f+i*.0675f;worldLine(c,x,0,0,x,.1525f,0,.5f,0x889ab8b1);}
        for(int i=0;i<=3;i++)worldLine(c,-.81f,i*.05f,0,.81f,i*.05f,0,.5f,0x889ab8b1);
        worldLine(c,-.84f,0,0,-.84f,.19f,0,3,0xffb3c9bf);worldLine(c,.84f,0,0,.84f,.19f,0,3,0xffb3c9bf);
        worldLine(c,-.84f,.156f,0,.84f,.156f,0,2.7f,0xffe3f3e3);
        if(engine.ballZ>=0)ball(c);
        paddle(c,engine.playerX,engine.playerPaddleY,TableTennisEngine.STRIKE_Z,true);
    }
    private void paddle(Canvas c,float x,float y,float z,boolean near){
        float[] p=project(x,y,z);float rr=.128f*p[2];
        float[] s=project(x,0,z);oval(c,s[0]-rr,s[1]-rr*.24f,rr*2,rr*.5f,0x33000000);
        c.save();c.rotate(near?-9:12,p[0],p[1]);
        round(c,p[0]-rr*.15f,p[1]+rr*.7f,rr*.3f,rr*.95f,rr*.1f,0xffd9ae7b);
        oval(c,p[0]-rr-2,p[1]-rr*1.1f-2,rr*2+4,rr*2.2f+4,0xffead5b4);
        oval(c,p[0]-rr,p[1]-rr*1.1f,rr*2,rr*2.2f,near?0xffc85f59:0xff283d4a);
        oval(c,p[0]-rr*.77f,p[1]-rr*.91f,rr*1.54f,rr*1.82f,near?0xffdc766a:0xff3d5964);
        line(c,p[0]-rr*.4f,p[1]+rr*.55f,p[0]+rr*.4f,p[1]+rr*.55f,.65f,near?0x70ffe6c9:0x60b5e4dc);
        c.restore();
        if(near&&flash>0){float v=1-flash/.3f;ring(c,p[0],p[1],rr+5+v*18,1,alpha(MINT,(int)(110*(1-v))));}
    }
    private void ball(Canvas c){
        float[] s=project(engine.ballX,0,engine.ballZ),p=project(engine.ballX,engine.ballY,engine.ballZ);
        if(Math.abs(engine.ballZ)<1.4&&Math.abs(engine.ballX)<.83)oval(c,s[0]-8,s[1]-2.5f,16,5,alpha(0xff052f2d,(int)(100/(1+engine.ballY*3))));
        if(screen.equals("play")&&engine.phase==TableTennisEngine.Phase.PLAYING)for(int j=engine.trailCount-1;j>=0;j-=3){int i=(engine.trailHead-1-j+engine.trailX.length)%engine.trailX.length;float[] t=project(engine.trailX[i],engine.trailY[i],engine.trailZ[i]);float progress=1-j/(float)engine.trailX.length;circle(c,t[0],t[1],Math.max(.8f,.025f*t[2]*progress),alpha(0xffffdeb1,(int)(progress*90)));}
        float r=Math.max(2,.025f*p[2]);circle(c,p[0],p[1],r+3,0x19ffe7bb);circle(c,p[0],p[1],r,0xffffd58b);circle(c,p[0]-r*.25f,p[1]-r*.32f,r*.55f,0xfffff8dc);
    }
    private void hud(Canvas c){
        text(c,"弧线  /  3D 乒乓球",25,top+18,11,MINT,MEDIUM);
        button(c,"pause",329,top-3,47,36,"Ⅱ",false);
        round(c,24,top+40,352,98,20,0xcb102b30);
        textCenter(c,"你",95,top+64,10,MUTED,NORMAL);textCenter(c,"对手",305,top+64,10,MUTED,NORMAL);
        textCenter(c,String.valueOf(engine.playerScore),95,top+112,39,INK,MEDIUM);textCenter(c,String.valueOf(engine.aiScore),305,top+112,39,INK,MEDIUM);
        textCenter(c,"11 分",200,top+82,11,MINT,MONO);textCenter(c,"领先 2 分",200,top+106,9,MUTED,NORMAL);
        float y=h-bottom-159;
        textCenter(c,engine.rally>=3?"RALLY  "+engine.rally:"找到下一拍的节奏",200,y,engine.rally>=3?20:13,engine.rally>=3?MINT:INK,engine.rally>=3?MONO:MEDIUM);
        textCenter(c,"左右拖动，球拍会跟手移动",200,y+28,11,MUTED,NORMAL);
        textCenter(c,"快滑打出变线  ·  自动接击",200,y+49,10,MUTED,NORMAL);
        float tx=200+engine.playerX/0.86f*141;
        round(c,48,y+77,304,3,1.5f,0xff294747);circle(c,tx,y+78.5f,7,MINT);
        if(engine.phase==TableTennisEngine.Phase.SERVE){
            textCenter(c,engine.playerServing()?"你的发球":"对手发球",200,h*.47f+190,15,MINT,MEDIUM);
        }else if(engine.phase==TableTennisEngine.Phase.POINT){
            round(c,82,h*.47f-20,236,80,22,0xe60e252a);
            textCenter(c,engine.lastPointWinner==1?"漂亮，这一分属于你":"下一分，接回来",200,h*.47f+12,19,engine.lastPointWinner==1?MINT:RED,MEDIUM);
            textCenter(c,engine.reason,200,h*.47f+38,10,MUTED,NORMAL);
        }
    }
    private void overlay(Canvas c){paint.setColor(0xb9061218);c.drawRect(0,0,400,h,paint);buttons.clear();}
    private void pause(Canvas c){
        overlay(c);float y=(h-350)/2;round(c,27,y,346,350,27,0xff142d33);
        textCenter(c,"PAUSE / 呼吸一下",200,y+44,10,MINT,MONO);textCenter(c,"下一拍，依然在这里",200,y+87,24,INK,NORMAL);
        button(c,"resume",49,y+116,302,50,"继续比赛",true);
        button(c,"sound",49,y+181,145,39,"声音 "+(host.preferences.getBoolean("sound",true)?"开":"关"),false);
        button(c,"haptics",206,y+181,145,39,"触感 "+(host.preferences.getBoolean("haptics",true)?"开":"关"),false);
        button(c,"menu",49,y+235,302,44,"返回球馆",false);
        button(c,"home",49,y+286,302,35,"返回游戏集",false);
    }
    private void result(Canvas c){
        overlay(c);float y=(h-353)/2;round(c,27,y,346,353,27,0xff142d33);
        textCenter(c,engine.matchWon()?"MATCH WON":"GOOD MATCH",200,y+40,10,MINT,MONO);
        textCenter(c,engine.matchWon()?"把这一场，打漂亮":"下一场，再回一拍",200,y+85,25,INK,NORMAL);
        textCenter(c,engine.playerScore+"  :  "+engine.aiScore,200,y+150,43,INK,MONO);
        textCenter(c,"最佳连续回击 "+engine.bestRally,200,y+183,11,MINT,NORMAL);
        button(c,"start",49,y+210,302,51,"再来一场   ↗",true);
        button(c,"menu",49,y+277,302,44,"返回球馆",false);
    }
    private void button(Canvas c,String id,float x,float y,float w,float hh,String label,boolean primary){
        boolean down=pressed.equals(id);round(c,x,y,w,hh,Math.min(18,hh/2),primary?(down?0xff8cd5b9:MINT):(down?0xff294b4d:0xff1b383e));
        textCenter(c,label,x+w/2,y+hh/2+4.5f,hh>44?15:12,primary?0xff102b2a:INK,MEDIUM);buttons.add(new Button(id,x,y,w,hh));
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        float x=event.getX()/scale,y=event.getY()/scale;int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){activePointer=event.getPointerId(0);pressed="";for(int i=buttons.size()-1;i>=0;i--)if(buttons.get(i).rect.contains(x,y)){pressed=buttons.get(i).id;break;}
            if(pressed.isEmpty()&&screen.equals("play")&&!paused){engine.setPlayerTarget((x-200)/160f*.86f);if(engine.phase==TableTennisEngine.Phase.SERVE)engine.serve();}invalidate();return true;}
        if(action==MotionEvent.ACTION_MOVE&&event.getPointerId(event.getActionIndex())==activePointer){if(pressed.isEmpty()&&screen.equals("play")&&!paused)engine.setPlayerTarget((x-200)/160f*.86f);return true;}
        if(action==MotionEvent.ACTION_UP){String click=pressed;pressed="";activePointer=-1;if(!click.isEmpty())for(Button b:buttons)if(b.id.equals(click)&&b.rect.contains(x,y)){performClick();activate(click);break;}invalidate();return true;}
        if(action==MotionEvent.ACTION_CANCEL){activePointer=-1;pressed="";return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void activate(String id){
        host.feedback("tap");
        if(id.startsWith("difficulty")){difficulty=Integer.parseInt(id.substring(10));return;}
        switch(id){case "start":startMatch(difficulty);break;case "pause":paused=true;break;case "resume":resumeMatch();break;case "menu":host.rememberRally(engine.bestRally);screen="menu";paused=false;break;case "home":host.finish();break;
            case "sound":case "haptics":host.preferences.edit().putBoolean(id,!host.preferences.getBoolean(id,true)).apply();break;default:break;}
    }
    private void text(Canvas c,String text,float x,float y,float size,int color,Typeface face){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(face);paint.setTextAlign(Paint.Align.LEFT);c.drawText(text,x,y,paint);}
    private void textCenter(Canvas c,String text,float x,float y,float size,int color,Typeface face){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(face);paint.setTextAlign(Paint.Align.CENTER);c.drawText(text,x,y,paint);}
    private void round(Canvas c,float x,float y,float w,float hh,float radius,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);rect.set(x,y,x+w,y+hh);c.drawRoundRect(rect,radius,radius,paint);}
    private void oval(Canvas c,float x,float y,float w,float hh,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);rect.set(x,y,x+w,y+hh);c.drawOval(rect,paint);}
    private void circle(Canvas c,float x,float y,float r,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawCircle(x,y,r,paint);}
    private void line(Canvas c,float x,float y,float xx,float yy,float width,int color){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);c.drawLine(x,y,xx,yy,paint);paint.setStyle(Paint.Style.FILL);}
    private void ring(Canvas c,float x,float y,float r,float width,int color){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);c.drawCircle(x,y,r,paint);paint.setStyle(Paint.Style.FILL);}
    private static int alpha(int color,int a){return (color&0xffffff)|(Math.max(0,Math.min(255,a))<<24);}
}
