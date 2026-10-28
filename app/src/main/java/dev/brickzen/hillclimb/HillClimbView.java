package dev.brickzen.hillclimb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Locale;

/** Landscape, resolution-independent hill course with parallax scenery and two-finger pedals. */
public final class HillClimbView extends View {
    public interface Host{
        int bestDistance();int bestCoins();boolean soundEnabled();boolean hapticsEnabled();
        void setSound(boolean enabled);void setHaptics(boolean enabled);void started();void record(int distance,int coins);void feedback(String event);void quit();
    }
    public final HillClimbEngine engine;
    public String screen="menu";
    private final Host host;
    private static final int INK=0xffeff5e9,MINT=0xffb7e0c0,GOLD=0xfff2d1a0,MUTED=0xffa6bcb1;
    private static final Typeface NORMAL=Typeface.create("sans-serif",0),MEDIUM=Typeface.create("sans-serif-medium",0),MONO=Typeface.create("monospace",0);
    private final Paint paint=new Paint(3);private final Path path=new Path();private final RectF rect=new RectF();
    private final ArrayList<Button> buttons=new ArrayList<>();private final SparseArray<String> fingers=new SparseArray<>();
    private float scale=1,h=440,left=28,right=872,top=20,bottom=16,ambient,cameraX,baseY;
    private long lastFrame;private boolean suspended=true,recorded;private String pressed="";
    private static final float WORLD_SCALE=35;
    private static final class Button{final String action;final RectF bounds;Button(String action,float x,float y,float w,float h){this.action=action;bounds=new RectF(x,y,x+w,y+h);}}
    public HillClimbView(Context context,Host host){
        super(context);this.host=host;engine=new HillClimbEngine(host::feedback);setFocusable(true);setClickable(true);
        setContentDescription("野径，登山赛车。按住右侧油门前进，左侧刹车减速。腾空时油门抬头、刹车压头。顶部暂停。");
        setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
            left=Math.max(28,insets.getDisplayCutout().getSafeInsetLeft()/Math.max(.01f,scale)+10);
            right=900-Math.max(28,insets.getDisplayCutout().getSafeInsetRight()/Math.max(.01f,scale)+10);
            top=Math.max(18,insets.getDisplayCutout().getSafeInsetTop()/Math.max(.01f,scale)+8);
        }bottom=Math.max(14,insets.getSystemWindowInsetBottom()/Math.max(.01f,scale)+6);return insets;});
    }
    public float getLogicalWidth(){return 900;}
    public RectF getActionBounds(String action){for(Button b:buttons)if(b.action.equals(action))return new RectF(b.bounds);return null;}
    public void startRun(long seed){engine.start(seed);host.started();recorded=false;screen="play";clearTouches();lastFrame=0;invalidate();}
    public boolean performAction(String action){
        switch(action){
            case "start":case "restart":startRun(System.nanoTime());break;
            case "pause":engine.pause();clearTouches();break;
            case "resume":engine.resume();lastFrame=0;break;
            case "menu":host.record((int)engine.distance,engine.coins);engine.pause();screen="menu";clearTouches();break;
            case "home":host.record((int)engine.distance,engine.coins);host.quit();break;
            case "sound":host.setSound(!host.soundEnabled());break;
            case "haptics":host.setHaptics(!host.hapticsEnabled());break;
            default:return false;
        }invalidate();return true;
    }
    public void onHostPause(){suspended=true;engine.pause();clearTouches();lastFrame=0;invalidate();}
    public void onHostResume(){suspended=false;lastFrame=0;invalidate();}
    public boolean handleBack(){if(screen.equals("menu"))return false;if(engine.phase==HillClimbEngine.Phase.PLAYING)performAction("pause");else performAction("menu");return true;}
    private void clearTouches(){fingers.clear();pressed="";engine.setControls(0,0);}
    @Override protected void onDetachedFromWindow(){clearTouches();super.onDetachedFromWindow();}
    @Override protected void onSizeChanged(int width,int height,int oldw,int oldh){scale=width/900f;h=height/scale;requestApplyInsets();}
    @Override protected void onDraw(Canvas canvas){
        long now=SystemClock.elapsedRealtimeNanos();float dt=lastFrame==0?0:Math.min(.05f,(now-lastFrame)/1e9f);lastFrame=now;
        if(!suspended){ambient+=dt;if(screen.equals("play")){engine.update(dt);if(engine.phase==HillClimbEngine.Phase.OVER&&!recorded){host.record((int)engine.distance,engine.coins);recorded=true;}}}
        canvas.save();canvas.scale(scale,scale);buttons.clear();world(canvas);
        if(screen.equals("menu"))menu(canvas);else{hud(canvas);if(engine.phase==HillClimbEngine.Phase.PAUSED)pause(canvas);else if(engine.phase==HillClimbEngine.Phase.OVER)result(canvas);}
        canvas.restore();if(!suspended)postInvalidateOnAnimation();
    }
    private void world(Canvas c){
        paint.setShader(new LinearGradient(0,0,0,h,new int[]{0xff0d2837,0xff48797b,0xffe4bd94},new float[]{0,.58f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,900,h,paint);paint.setShader(null);
        float sunX=690,sunY=h*.24f;paint.setShader(new RadialGradient(sunX,sunY,90,new int[]{0x66f6ca98,0x00f6ca98},null,Shader.TileMode.CLAMP));c.drawCircle(sunX,sunY,90,paint);paint.setShader(null);circle(c,sunX,sunY,31,0xfff2d0a4);
        mountain(c,h*.42f,43,.13f,0xff345963);mountain(c,h*.53f,37,.26f,0xff31545b);mountain(c,h*.61f,22,.45f,0xff3c6460);
        float carScreenX=screen.equals("menu")?650:Math.max(200,left+145);cameraX=engine.x-carScreenX/WORLD_SCALE;baseY=h*.64f+engine.terrainHeight(engine.x)*WORLD_SCALE;
        path.reset();path.moveTo(-20,h+20);for(float sx=-20;sx<=920;sx+=6)path.lineTo(sx,ground(sx));path.lineTo(920,h+20);path.close();paint.setColor(0xff243f38);c.drawPath(path,paint);
        path.reset();for(float sx=-20;sx<=920;sx+=5){float gy=ground(sx);if(sx==-20)path.moveTo(sx,gy);else path.lineTo(sx,gy);}stroke(c,0xffbdd19a,4.5f);stroke(c,0xffd5d7a7,1);
        for(int i=(int)Math.floor(cameraX/3);i<cameraX/3+14;i++){
            float sx=(i*3-cameraX)*WORLD_SCALE,gy=ground(sx);if(sx< -30||sx>930)continue;
            line(c,sx,gy+22,sx+12,gy+24,1,0x55728c6a);line(c,sx+25,gy+49,sx+43,gy+48,1,0x44647b61);
            if(i%3==0){line(c,sx,gy-1,sx-3,gy-9,1,0xff8eab79);line(c,sx,gy,sx+4,gy-12,1,0xff9ab783);}
        }
        if(screen.equals("play")){
            for(HillClimbEngine.Pickup pickup:engine.pickups)if(!pickup.taken){float px=(pickup.x-cameraX)*WORLD_SCALE,py=baseY-pickup.y*WORLD_SCALE;
                if(px< -30||px>930)continue;py+=(float)Math.sin(ambient*2+pickup.x)*2;
                circle(c,px,py,14,pickup.fuel?0x186ee2c0:0x22ffe1a0);
                if(pickup.fuel){round(c,px-9,py-10,18,20,4,0xffb6dca9);round(c,px-3,py-14,8,5,1,0xffd9ebbf);line(c,px-4,py,px+4,py,2,0xff375f4c);line(c,px,py-4,px,py+4,2,0xff375f4c);}
                else{circle(c,px,py,8,0xffedca8b);ring(c,px,py,5,1,0xffb68b54);line(c,px,py-3,px,py+3,1,0xffffebba);}
            }
            int marker=(int)(engine.x/100)+1;float mx=(marker*100-cameraX)*WORLD_SCALE;
            if(mx>0&&mx<920){float my=ground(mx);line(c,mx,my,mx,my-43,2,0xffc7d2a6);round(c,mx-19,my-56,38,17,4,0xff183e3a);text(c,marker*100+"m",mx,my-44,9,GOLD,MONO,Paint.Align.CENTER);}
        }
        float cx=(engine.x-cameraX)*WORLD_SCALE,cy=baseY-engine.y*WORLD_SCALE;
        if(engine.groundedWheels>0&&engine.vx>3&&screen.equals("play")){for(int i=0;i<7;i++){float life=(ambient*2.4f+i*.15f)%1;float dx=cx-40-life*45,dy=ground(dx)-3-life*11;circle(c,dx,dy,2+life*6,alpha(0xffd4d1a2,(int)((1-life)*60)));}}
        car(c,cx,cy);
    }
    private void mountain(Canvas c,float baseline,float height,float speed,int color){path.reset();path.moveTo(-30,h);
        for(float px=-30;px<=930;px+=12){float p=px*.009f+engine.x*speed*.018f;float y=baseline-(float)(Math.sin(p)*.45+Math.sin(p*2.3+1)*.3+Math.sin(p*.51+2)*.25)*height;path.lineTo(px,y);}
        path.lineTo(930,h);path.close();paint.setColor(color);c.drawPath(path,paint);
    }
    private float ground(float sx){return baseY-engine.terrainHeight(cameraX+sx/WORLD_SCALE)*WORLD_SCALE;}
    private void car(Canvas c,float cx,float cy){
        float s=WORLD_SCALE;c.save();c.translate(cx,cy);c.rotate(-(float)Math.toDegrees(engine.angle));
        for(int wheel=0;wheel<2;wheel++){
            float wx=(wheel==0?-.9f:.9f)*s,wy=.30f*s;line(c,wx,-.13f*s,wx,wy,3,0xff9cae9e);
            c.save();c.translate(wx,wy);c.rotate(-(engine.x/HillClimbEngine.WHEEL_RADIUS)*57.2958f);float r=HillClimbEngine.WHEEL_RADIUS*s;
            circle(c,0,0,r+2,0xff122b2b);circle(c,0,0,r-2,0xff223b3b);ring(c,0,0,r-4,1,0xff698177);
            for(int i=0;i<10;i++){float a=i*.628318f;line(c,(float)Math.cos(a)*(r-2),(float)Math.sin(a)*(r-2),(float)Math.cos(a)*(r+1),(float)Math.sin(a)*(r+1),1.5f,0xff41554b);}
            circle(c,0,0,5,0xffe0c98f);circle(c,0,0,2.2f,0xff638078);c.restore();
        }
        round(c,-1.25f*s,-.40f*s,2.55f*s,.61f*s,5,0xff7eb29a);round(c,-1.17f*s,-.38f*s,2.39f*s,.10f*s,2,0xffb6d2a4);
        path.reset();path.moveTo(-.53f*s,-.4f*s);path.lineTo(-.29f*s,-1.01f*s);path.lineTo(.50f*s,-1.01f*s);path.lineTo(.91f*s,-.4f*s);path.close();paint.setColor(0xffb9d6b1);c.drawPath(path,paint);
        path.reset();path.moveTo(-.40f*s,-.44f*s);path.lineTo(-.22f*s,-.90f*s);path.lineTo(.43f*s,-.90f*s);path.lineTo(.72f*s,-.44f*s);path.close();paint.setColor(0xff254a54);c.drawPath(path,paint);
        line(c,.13f*s,-.91f*s,.13f*s,-.44f*s,2,0xffc9debb);line(c,-.15f*s,-.79f*s,.04f*s,-.79f*s,1,0xffa0c8bf);
        circle(c,.40f*s,-.68f*s,.10f*s,0xffe1c29b);round(c,.25f*s,-.57f*s,.32f*s,.17f*s,3,0xffd1ac78);
        line(c,-.47f*s,-.1f*s,.77f*s,-.1f*s,1,0xff608d7a);round(c,.56f*s,-.2f*s,.17f*s,.06f*s,1,0xffdce3b7);
        round(c,1.14f*s,-.24f*s,.17f*s,.17f*s,2,0xfff5dea6);round(c,-1.28f*s,-.13f*s,.12f*s,.13f*s,1,0xffdd9e7b);
        round(c,-1.35f*s,.11f*s,.24f*s,.10f*s,2,0xffb3c2a5);round(c,1.13f*s,.10f*s,.25f*s,.10f*s,2,0xffb3c2a5);
        c.restore();
    }
    private void menu(Canvas c){
        paint.setShader(new LinearGradient(0,0,530,0,new int[]{0xeb102f34,0xb3102f34,0x00102f34},new float[]{0,.65f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,560,h,paint);paint.setShader(null);
        text(c,"WILD TRAIL / 登山赛车",left,top+22,10,MINT,MONO,Paint.Align.LEFT);
        text(c,"野径",left-2,top+81,48,INK,NORMAL,Paint.Align.LEFT);
        text(c,"越过山丘，把下一段风景留给自己。",left,top+115,14,0xffc1d0bd,NORMAL,Paint.Align.LEFT);
        text(c,"最远 "+host.bestDistance()+" m    ·    金币 "+host.bestCoins(),left,top+151,12,GOLD,MONO,Paint.Align.LEFT);
        float y=h-bottom-104;button(c,"start",left,y,282,52,"发动引擎   ↗",true);button(c,"home",left+295,y,112,52,"游戏集",false);
        text(c,"按住油门前进 · 刹车减速 · 腾空时调节车身",left,y+77,11,0xffb6cbb9,NORMAL,Paint.Align.LEFT);
        button(c,"sound",right-192,top,88,31,"声音 "+(host.soundEnabled()?"开":"关"),false);
        button(c,"haptics",right-96,top,96,31,"触感 "+(host.hapticsEnabled()?"开":"关"),false);
    }
    private void hud(Canvas c){
        round(c,left,top,127,62,16,0xd7102d32);text(c,((int)engine.distance)+" m",left+15,top+30,25,INK,MEDIUM,Paint.Align.LEFT);text(c,"最远 "+Math.max(host.bestDistance(),(int)engine.distance)+" m",left+15,top+49,9,MUTED,MONO,Paint.Align.LEFT);
        round(c,left+138,top,143,62,16,0xc9102d32);text(c,"FUEL",left+153,top+20,8,MUTED,MONO,Paint.Align.LEFT);text(c,Math.round(engine.fuel)+"%",left+263,top+21,10,engine.fuel<20?0xffffb49b:MINT,MONO,Paint.Align.RIGHT);
        round(c,left+153,top+32,113,5,2,0xff3c5550);round(c,left+153,top+32,113*engine.fuel/100,5,2,engine.fuel<20?0xffffb49b:MINT);
        text(c,"补给在前方，保持前进",left+153,top+50,8,MUTED,NORMAL,Paint.Align.LEFT);
        text(c,"◉  "+engine.coins,right-78,top+26,16,GOLD,MONO,Paint.Align.RIGHT);button(c,"pause",right-49,top,49,42,"Ⅱ",false);
        pedal(c,"brake",left,h-bottom-76,148,65,"BRAKE","刹车 / 压车头",0xffe9c4a3);
        pedal(c,"gas",right-148,h-bottom-76,148,65,"THROTTLE","油门 / 抬车头",MINT);
        if(engine.time<10)text(c,"左右双指也可同时控制，起跳后轻点调整平衡",450,h-bottom-17,10,0xffc8d5b7,NORMAL,Paint.Align.CENTER);
        else if(engine.airTime>.2f)text(c,"AIR  "+String.format(Locale.US,"%.1f",engine.airTime)+"s",450,h-bottom-40,15,GOLD,MONO,Paint.Align.CENTER);
        else text(c,((int)Math.abs(engine.vx*3.6f))+" km/h",450,h-bottom-34,13,0xffc8d5b7,MONO,Paint.Align.CENTER);
    }
    private void pedal(Canvas c,String id,float x,float y,float w,float height,String title,String sub,int color){boolean active=false;for(int i=0;i<fingers.size();i++)active|=id.equals(fingers.valueAt(i));
        round(c,x,y,w,height,18,active?0xee3e6252:0xb6102f32);border(c,x,y,w,height,18,active?color:0x667aab93,1.5f);
        text(c,title,x+w/2,y+29,15,color,MONO,Paint.Align.CENTER);text(c,sub,x+w/2,y+49,10,0xffbed1bc,NORMAL,Paint.Align.CENTER);buttons.add(new Button(id,x,y,w,height));
    }
    private void overlay(Canvas c){paint.setColor(0xde0b252b);c.drawRect(0,0,900,h,paint);buttons.clear();}
    private void pause(Canvas c){overlay(c);float y=Math.max(25,(h-280)/2);text(c,"TAKE IN THE VIEW",450,y+18,10,MINT,MONO,Paint.Align.CENTER);text(c,"路还很长，歇一会",450,y+60,31,INK,NORMAL,Paint.Align.CENTER);
        button(c,"resume",275,y+86,350,49,"继续前进",true);button(c,"restart",275,y+145,168,41,"重新出发",false);button(c,"menu",457,y+145,168,41,"返回车库",false);
        button(c,"sound",275,y+199,168,34,"声音 "+(host.soundEnabled()?"开":"关"),false);button(c,"haptics",457,y+199,168,34,"触感 "+(host.hapticsEnabled()?"开":"关"),false);
    }
    private void result(Canvas c){overlay(c);float y=Math.max(24,(h-300)/2);text(c,"A LITTLE FURTHER",450,y+14,10,GOLD,MONO,Paint.Align.CENTER);text(c,"风景，下次再往前一点",450,y+55,29,INK,NORMAL,Paint.Align.CENTER);
        text(c,((int)engine.distance)+" m",450,y+113,43,MINT,MEDIUM,Paint.Align.CENTER);text(c,"金币 "+engine.coins+"   ·   最远 "+host.bestDistance()+" m",450,y+145,12,GOLD,MONO,Paint.Align.CENTER);
        text(c,engine.reason,450,y+174,12,MUTED,NORMAL,Paint.Align.CENTER);button(c,"restart",275,y+197,350,49,"再越过一座山   ↗",true);button(c,"menu",275,y+256,350,37,"返回车库",false);
    }
    private void button(Canvas c,String action,float x,float y,float w,float height,String title,boolean primary){round(c,x,y,w,height,Math.min(16,height/2),primary?MINT:0xcc183e40);text(c,title,x+w/2,y+height/2+5,height>44?15:12,primary?0xff15382f:INK,MEDIUM,Paint.Align.CENTER);buttons.add(new Button(action,x,y,w,height));}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),index=event.getActionIndex();float px=event.getX(index)/scale,py=event.getY(index)/scale;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){String hit=hit(px,py);int id=event.getPointerId(index);fingers.put(id,hit);if(!hit.equals("gas")&&!hit.equals("brake"))pressed=hit;controls();invalidate();return true;}
        if(action==MotionEvent.ACTION_MOVE){for(int i=0;i<event.getPointerCount();i++){String old=fingers.get(event.getPointerId(i),"");if(old.equals("gas")||old.equals("brake")||old.isEmpty())fingers.put(event.getPointerId(i),hitPedal(event.getX(i)/scale,event.getY(i)/scale));}controls();invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){String old=fingers.get(event.getPointerId(index),"");fingers.remove(event.getPointerId(index));controls();if(!old.equals("gas")&&!old.equals("brake")&&!old.isEmpty()&&old.equals(hit(px,py))){performClick();performAction(old);}pressed="";invalidate();return true;}
        if(action==MotionEvent.ACTION_CANCEL){clearTouches();invalidate();return true;}return true;
    }
    private String hit(float x,float y){for(int i=buttons.size()-1;i>=0;i--)if(buttons.get(i).bounds.contains(x,y))return buttons.get(i).action;return "";}
    private String hitPedal(float x,float y){String action=hit(x,y);return action.equals("gas")||action.equals("brake")?action:"";}
    private void controls(){float gas=0,brake=0;for(int i=0;i<fingers.size();i++){gas=Math.max(gas,fingers.valueAt(i).equals("gas")?1:0);brake=Math.max(brake,fingers.valueAt(i).equals("brake")?1:0);}engine.setControls(gas,brake);}
    @Override public boolean performClick(){super.performClick();return true;}
    private static int alpha(int color,int value){return (color&0xffffff)|Math.max(0,Math.min(255,value))<<24;}
    private void text(Canvas c,String value,float x,float y,float size,int color,Typeface type,Paint.Align align){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setTypeface(type);paint.setTextAlign(align);paint.setTextSize(size);paint.setColor(color);c.drawText(value,x,y,paint);}
    private void round(Canvas c,float x,float y,float w,float height,float radius,int color){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);rect.set(x,y,x+w,y+height);c.drawRoundRect(rect,radius,radius,paint);}
    private void border(Canvas c,float x,float y,float w,float height,float radius,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);rect.set(x,y,x+w,y+height);c.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.FILL);}
    private void circle(Canvas c,float x,float y,float radius,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawCircle(x,y,radius,paint);}
    private void ring(Canvas c,float x,float y,float radius,float width,int color){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);c.drawCircle(x,y,radius,paint);paint.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x,float y,float xx,float yy,float width,int color){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);c.drawLine(x,y,xx,yy,paint);paint.setStyle(Paint.Style.FILL);}
    private void stroke(Canvas c,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
}
