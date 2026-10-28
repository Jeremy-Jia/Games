package dev.brickzen.range;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** A sporting paper-target range, drawn in landscape with a movable telescopic sight. */
public final class RangeView extends View {
    private static final int BG=0xff0a1c23,WHITE=0xffedf3e7,MINT=0xffa6dfc5,GOLD=0xffe7cc99,MUTED=0xff9bb1ad,RED=0xffe7917e;
    private static final Typeface REG=Typeface.create("sans-serif",0),MED=Typeface.create("sans-serif-medium",0),MONO=Typeface.create("monospace",0);
    public final RangeEngine engine;
    public String screen="menu";
    private final RangeActivity host;
    private final Paint paint=new Paint(3);
    private final RectF rect=new RectF();
    private final Path path=new Path();
    private final ArrayList<Button> buttons=new ArrayList<>();
    private final Map<Integer,String> pointers=new HashMap<>();
    private boolean suspended=true,recorded,encounterStarted;
    private long lastFrame;
    private float scale=1,h=430,left=25,right=25,top=24,bottom=15,ambient,flash;
    private int aimPointer=-1;
    private float lastX,lastY;
    private static final class Button{final String id;final RectF rect;Button(String id,float x,float y,float w,float h){this.id=id;rect=new RectF(x,y,x+w,y+h);}}
    public RangeView(Context context,RangeActivity host){
        super(context);this.host=host;engine=new RangeEngine(name->{host.feedback(name);if(name.equals("fire"))flash=.22f;});
        setFocusable(true);setClickable(true);setContentDescription("准星，狙击打靶。拖动画面瞄准，右下射击；左下屏息、变倍和装填。仅有纸靶和运动靶。");
        setOnApplyWindowInsetsListener((view,insets)->{updateInsets();return insets;});
    }
    @Override protected void onSizeChanged(int w,int hh,int oldw,int oldh){scale=w/900f;h=hh/scale;updateInsets();requestApplyInsets();}
    private void updateInsets(){left=right=25;top=24;bottom=15;
        if(Build.VERSION.SDK_INT>=28&&getRootWindowInsets()!=null&&getRootWindowInsets().getDisplayCutout()!=null){
            android.view.DisplayCutout cut=getRootWindowInsets().getDisplayCutout();left=Math.max(left,cut.getSafeInsetLeft()/Math.max(.1f,scale)+10);right=Math.max(right,cut.getSafeInsetRight()/Math.max(.1f,scale)+10);top=Math.max(top,cut.getSafeInsetTop()/Math.max(.1f,scale)+8);
        }
    }
    public float getLogicalWidth(){return 900;}
    public float getLogicalHeight(){return h;}
    public RectF getActionBounds(String action){for(Button b:buttons)if(b.id.equals(action))return new RectF(b.rect);return null;}
    public void startRound(long seed){
        if(host.encounter==null)engine.start(seed);else engine.startEncounter(seed,host.encounter.plan.difficulty);
        screen="play";recorded=false;lastFrame=0;invalidate();
    }
    public boolean performAction(String action){boolean done=true;switch(action){
        case "start":case "restart":if(host.encounter!=null){host.encounter.cancel();break;}startRound(System.nanoTime());break;
        case "fire":done=engine.fire();break;case "reload":done=engine.reload();break;case "zoom":engine.changeZoom();break;
        case "breath":engine.setHoldingBreath(!engine.holdingBreath);break;case "pause":engine.pause();break;case "resume":engine.resume();lastFrame=0;break;
        case "menu":if(host.encounter!=null){host.encounter.cancel();break;}engine.pause();screen="menu";break;
        case "quit":if(host.encounter!=null)host.encounter.cancel();else host.finish();break;
        case "sound":host.preferences.edit().putBoolean("sound",!host.preferences.getBoolean("sound",true)).apply();break;
        case "haptics":host.preferences.edit().putBoolean("haptics",!host.preferences.getBoolean("haptics",true)).apply();break;
        default:done=false;
    }invalidate();return done;}
    public void onHostPause(){suspended=true;engine.pause();pointers.clear();aimPointer=-1;lastFrame=0;invalidate();}
    public void onHostResume(){suspended=false;lastFrame=0;
        if(host.encounter!=null&&!encounterStarted){encounterStarted=true;startRound(host.encounter.plan.seed);}
        invalidate();
    }
    public boolean handleBack(){if(host.encounter!=null){if(!host.encounter.isEnding()){
            if(engine.phase==RangeEngine.Phase.PLAYING)engine.pause();else host.encounter.cancel();
        }invalidate();return true;}
        if(screen.equals("menu"))return false;if(engine.phase==RangeEngine.Phase.PLAYING)engine.pause();else screen="menu";invalidate();return true;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);long now=System.nanoTime();float dt=lastFrame==0?0:Math.min(.05f,(now-lastFrame)/1e9f);lastFrame=now;
        if(!suspended){ambient+=dt;flash=Math.max(0,flash-dt);if(screen.equals("play")&&(host.encounter==null||!host.encounter.isEnding())){
            engine.update(dt);
            if(host.encounter!=null)host.encounter.observe(host.encounter.plan.metric==0?engine.encounterHits:engine.encounterScore,engine.phase==RangeEngine.Phase.OVER);
        }}
        if(host.encounter==null&&screen.equals("play")&&engine.phase==RangeEngine.Phase.OVER&&!recorded){recorded=true;host.record(engine.score,engine.accuracy());}
        canvas.save();canvas.scale(scale,scale);buttons.clear();landscape(canvas);drawTarget(canvas,450,h*.47f,130,false);
        if(screen.equals("menu"))menu(canvas);else{play(canvas);
            if(host.encounter!=null)host.encounter.draw(canvas,left-4,top+58,252);
            if(host.encounter!=null&&host.encounter.isEnding())buttons.clear();
            else if(engine.phase==RangeEngine.Phase.PAUSED)pause(canvas);else if(engine.phase==RangeEngine.Phase.OVER)result(canvas);
        }
        canvas.restore();if(!suspended)postInvalidateOnAnimation();
    }
    private void landscape(Canvas c){
        paint.setShader(new LinearGradient(0,0,0,h,new int[]{0xff203c48,0xff6f8d8b,0xff78918a},null,Shader.TileMode.CLAMP));c.drawRect(0,0,900,h,paint);paint.setShader(null);
        circle(c,718,h*.18f,29,0x44ecdab2);circle(c,718,h*.18f,16,0x66f6e6c4);
        mountain(c,h*.45f,40,.7f,0xff4f7175);mountain(c,h*.49f,25,1.3f,0xff365a60);mountain(c,h*.54f,18,2.2f,0xff294c51);
        paint.setShader(new LinearGradient(0,h*.47f,0,h,0xff4c6c58,0xff173a34,Shader.TileMode.CLAMP));c.drawRect(0,h*.49f,900,h,paint);paint.setShader(null);
        for(int i=0;i<28;i++){float x=(i*97)%940-20,y=h*.44f+(i%3)*5;tree(c,x,y,17+(i%5)*6,0xff294e49);}
        path.reset();path.moveTo(413,h*.5f);path.lineTo(490,h*.5f);path.lineTo(704,h);path.lineTo(188,h);path.close();paint.setColor(0xff7b856b);c.drawPath(path,paint);
        for(int i=0;i<10;i++){float y=h*.53f+(float)Math.pow(i/9f,1.6)*h*.47f;line(c,185+(h-y)*.8f,y,710-(h-y)*.8f,y,.6f,0x33485343);}
        for(int lane=-2;lane<=2;lane++)line(c,450+lane*25,h*.51f,450+lane*170,h,.8f,0x44516148);
        for(int i=0;i<9;i++){float x=80+i*95,y=h*.51f;line(c,x,y,x,y+30,2,0xff5d6a53);round(c,x-10,y-6,20,13,2,0xff9da285);circle(c,x,y,.8f,0xff575c46);}
        for(int i=0;i<40;i++){float x=(i*73)%900,y=h*.69f+(i%7)*h*.043f;line(c,x,y,x+2,y-4,.7f,0x334d7760);}
    }
    private void mountain(Canvas c,float base,float amplitude,float offset,int color){path.reset();path.moveTo(0,h);path.lineTo(0,base);for(int x=0;x<=920;x+=20)path.lineTo(x,base-(float)(Math.sin(x*.013+offset)*.45+Math.sin(x*.031+offset)*.55)*amplitude);path.lineTo(920,h);path.close();paint.setColor(color);c.drawPath(path,paint);}
    private void tree(Canvas c,float x,float y,float height,int color){path.reset();path.moveTo(x,y-height);path.lineTo(x-height*.34f,y);path.lineTo(x+height*.34f,y);path.close();paint.setColor(color);c.drawPath(path,paint);}
    private float lensRadius(){return Math.min(145,h*.31f);}
    private float factor(){return lensRadius()*engine.zoom()/1.05f;}
    private void drawTarget(Canvas c,float cx,float cy,float factor,boolean focused){
        float tx=cx+(engine.targetX-engine.sightX())*factor,ty=cy-(engine.targetY-engine.sightY())*factor;
        float radius=engine.targetRadius()*factor;
        round(c,tx-radius*.16f,ty+radius,Math.max(2,radius*.32f),radius*.8f,1,0xff574d3e);
        round(c,tx-radius*1.24f+2,ty-radius*1.24f+4,radius*2.48f,radius*2.48f,2,0x45000000);
        round(c,tx-radius*1.24f,ty-radius*1.24f,radius*2.48f,radius*2.48f,2,0xffe4dcc4);
        for(int i=10;i>=1;i--){float r=radius*i/10f;circle(c,tx,ty,r,i>=6?0xffe7e0cb:0xff263d3f);ring(c,tx,ty,r,.55f,i>=6?0xffa3a58f:0xff9ca99b);}
        circle(c,tx,ty,radius*.10f,0xffc66c5c);line(c,tx-radius*.05f,ty,tx+radius*.05f,ty,.5f,WHITE);line(c,tx,ty-radius*.05f,tx,ty+radius*.05f,.5f,WHITE);
        if(focused&&radius>25){text(c,"PRECISION / "+Math.round(engine.distance)+" M",tx,ty-radius*1.08f,Math.min(8,radius*.11f),0xff616c5e,MONO,Paint.Align.CENTER);
            for(int i=2;i<=8;i+=2)text(c,""+(11-i),tx+radius*i/10f+2,ty-2,Math.min(7,radius*.09f),i>=6?0xff727a68:0xffbec9b7,REG,Paint.Align.LEFT);
        }
        if(engine.feedbackTime>0){float px=tx+engine.lastImpactX*factor,py=ty-engine.lastImpactY*factor;circle(c,px,py,3.2f,0xff14282a);ring(c,px,py,7+(1-engine.feedbackTime)*14,1,alpha(engine.lastHit?GOLD:RED,(int)(engine.feedbackTime*190)));}
    }
    private void scope(Canvas c){
        if(engine.zoomIndex==0){reticle(c,450,h*.47f,75);return;}
        float cx=450,cy=h*.47f,r=lensRadius();
        paint.setColor(0x78041015);c.drawRect(0,0,900,h,paint);
        circle(c,cx,cy,r+8,0xff091b21);circle(c,cx,cy,r+4,0xff3a5656);
        c.save();path.reset();path.addCircle(cx,cy,r,Path.Direction.CW);c.clipPath(path);
        paint.setShader(new LinearGradient(0,cy-r,0,cy+r,0xff6e8c86,0xff4b684e,Shader.TileMode.CLAMP));c.drawRect(cx-r,cy-r,cx+r,cy+r,paint);paint.setShader(null);
        for(int i=0;i<12;i++){float yy=cy-r+i*r/5;line(c,cx-r,yy,cx+r,yy,.6f,0x16425b47);}
        drawTarget(c,cx,cy,factor(),true);reticle(c,cx,cy,r);c.restore();ring(c,cx,cy,r,1.3f,0xff8ca79a);ring(c,cx,cy,r-3,.6f,0x7783a294);
        if(flash>0)circle(c,cx,cy,r,alpha(0xffe9d9a7,(int)(flash*130)));
    }
    private void reticle(Canvas c,float cx,float cy,float r){
        line(c,cx-r,cy,cx-16,cy,.8f,0xff183630);line(c,cx+16,cy,cx+r,cy,.8f,0xff183630);line(c,cx,cy-r,cx,cy-16,.8f,0xff183630);line(c,cx,cy+16,cx,cy+r,.8f,0xff183630);
        for(int i=-4;i<=4;i++)if(i!=0){line(c,cx+i*r/7,cy-3,cx+i*r/7,cy+3,.7f,0xff183630);line(c,cx-3,cy+i*r/7,cx+3,cy+i*r/7,.7f,0xff183630);}
        ring(c,cx,cy,8,.7f,0xff214b3c);circle(c,cx,cy,1.4f,0xffd7866b);
    }
    private void menu(Canvas c){
        paint.setShader(new LinearGradient(0,0,620,0,0xf00b2128,0x000b2128,Shader.TileMode.CLAMP));c.drawRect(0,0,650,h,paint);paint.setShader(null);
        text(c,"STILL / PRECISION RANGE",left,top+14,9,MINT,MONO,Paint.Align.LEFT);
        text(c,"准星",left,top+74,47,WHITE,REG,Paint.Align.LEFT);
        text(c,"把呼吸，留在扣动之前。",left,top+104,15,MUTED,REG,Paint.Align.LEFT);
        text(c,"拖动画面瞄准  ·  屏息稳定准星",left,top+149,12,WHITE,REG,Paint.Align.LEFT);
        text(c,"10 发一局，远靶与运动靶逐步登场",left,top+174,11,MUTED,REG,Paint.Align.LEFT);
        text(c,"后半程留意风向，远靶适度抬高瞄点",left,top+198,10,MUTED,REG,Paint.Align.LEFT);
        text(c,"最佳 "+host.preferences.getInt("best_score",0)+"   ·   命中率 "+host.preferences.getInt("best_accuracy",0)+"%",left,h-bottom-94,11,GOLD,MONO,Paint.Align.LEFT);
        button(c,"start",left,h-bottom-73,270,52,"进入靶场   ↗",true);
        button(c,"quit",900-right-112,top-6,112,34,"‹ 游戏集",false);
        button(c,"sound",left+285,h-bottom-69,112,43,"声音 "+(host.preferences.getBoolean("sound",true)?"开":"关"),false);
        button(c,"haptics",left+411,h-bottom-69,112,43,"触感 "+(host.preferences.getBoolean("haptics",true)?"开":"关"),false);
        // The menu displays an atmospheric close-up without changing live game state.
        c.save();c.translate(640,h*.48f);circle(c,0,0,Math.min(110,h*.24f),0x338bd3b6);for(int i=9;i>=1;i--){circle(c,0,0,i*9,i>=6?0xffd1cfb4:0xff294642);ring(c,0,0,i*9,.7f,0xff8c9f8c);}circle(c,0,0,8,0xffd68b70);reticle(c,0,0,110);c.restore();
    }
    private void play(Canvas c){
        scope(c);
        round(c,left-4,top-8,214,57,15,0xe10d252a);text(c,"准星 / 靶场",left+9,top+11,10,MINT,MED,Paint.Align.LEFT);
        text(c,String.format(Locale.US,"%,d",engine.score),left+9,top+38,25,WHITE,MONO,Paint.Align.LEFT);
        text(c,"命中 "+engine.hits+" / "+engine.shotsFired,left+119,top+36,10,MUTED,REG,Paint.Align.LEFT);
        round(c,332,top-8,236,40,13,0xd510282a);
        text(c,Math.round(engine.distance)+" m  ·  "+(engine.movingTarget?"运动靶":"固定靶"),450,top+8,11,WHITE,MED,Paint.Align.CENTER);
        text(c,"风 "+(engine.wind<0?"←":"→")+String.format(Locale.US," %.1f",Math.abs(engine.wind)),450,top+23,8.5f,GOLD,MONO,Paint.Align.CENTER);
        text(c,host.encounter==null?String.format(Locale.US,"%02d / 10",engine.shotsFired):engine.encounterShots+" 发",900-right-137,top+13,18,WHITE,MONO,Paint.Align.RIGHT);
        text(c,"弹匣 "+engine.ammo+" / 5",900-right-137,top+33,10,MUTED,REG,Paint.Align.RIGHT);
        button(c,"pause",900-right-55,top-6,55,40,"Ⅱ",false);
        float y=h-bottom-62;
        button(c,"breath",left,y,128,49,engine.holdingBreath?"屏息中":"按住屏息",engine.holdingBreath);
        round(c,left+11,y+42,105,2,1,0xff2f5150);round(c,left+11,y+42,105*engine.stamina,2,1,MINT);
        button(c,"zoom",left+140,y,100,49,String.format(Locale.US,"%.0f× 变倍",engine.zoom()),false);
        button(c,"reload",left+252,y,112,49,engine.reloadRemaining>0?"装填中…":"装填弹匣",false);
        if(engine.reloadRemaining>0)round(c,left+262,y+43,92*(1-engine.reloadRemaining/1.25f),2,1,GOLD);
        button(c,"fire",900-right-136,y-4,136,57,engine.canFire()?"射 击":"准备中",true);
        if(engine.ammo==0&&engine.pendingImpact<=0&&engine.nextTargetDelay<=0)text(c,"弹匣已空，点击装填",900-right-68,y-12,10,GOLD,MED,Paint.Align.CENTER);
        else if(engine.feedbackTime>0)text(c,engine.lastHit?engine.lastRing+" 环  +"+engine.lastAward:"脱靶 · 调整后再来",450,h-bottom-78,13,engine.lastHit?GOLD:RED,MED,Paint.Align.CENTER);
        else if(engine.time<7)text(c,"拖动画面瞄准 · 屏息更稳 · 每发后自动换靶",450,h-bottom-78,10,MUTED,REG,Paint.Align.CENTER);
    }
    private void overlay(Canvas c){paint.setColor(0xd006171e);c.drawRect(0,0,900,h,paint);buttons.clear();}
    private void pause(Canvas c){overlay(c);float y=(h-288)/2;round(c,240,y,420,288,24,0xff163036);
        text(c,"TAKE A BREATH",450,y+35,9,MINT,MONO,Paint.Align.CENTER);text(c,"下一发，等你准备好",450,y+75,25,WHITE,REG,Paint.Align.CENTER);
        button(c,"resume",270,y+98,360,48,"继续瞄准",true);
        button(c,"sound",270,y+160,173,36,"声音 "+(host.preferences.getBoolean("sound",true)?"开":"关"),false);
        button(c,"haptics",457,y+160,173,36,"触感 "+(host.preferences.getBoolean("haptics",true)?"开":"关"),false);
        button(c,"menu",270,y+215,173,42,host.encounter==null?"返回靶场":"结束奇遇",false);button(c,"quit",457,y+215,173,42,host.encounter==null?"返回游戏集":"返回碰界",false);
    }
    private void result(Canvas c){overlay(c);float y=(h-304)/2;round(c,220,y,460,304,26,0xff163036);
        text(c,"TEN SHOTS / 一次专注",450,y+33,9,MINT,MONO,Paint.Align.CENTER);text(c,"把这一轮，打漂亮",450,y+75,26,WHITE,REG,Paint.Align.CENTER);
        text(c,""+engine.score,450,y+133,43,GOLD,MONO,Paint.Align.CENTER);
        text(c,"命中 "+engine.hits+" / 10   ·   命中率 "+engine.accuracy()+"%",450,y+169,13,MUTED,REG,Paint.Align.CENTER);
        button(c,"restart",250,y+197,400,48,"再来一轮   ↗",true);button(c,"quit",250,y+255,400,33,"返回游戏集",false);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(host.encounter!=null&&host.encounter.isEnding())return true;
        int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);float x=event.getX(index)/scale,y=event.getY(index)/scale;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            String found="";for(int i=buttons.size()-1;i>=0;i--)if(buttons.get(i).rect.contains(x,y)){found=buttons.get(i).id;break;}
            pointers.put(id,found);
            if(found.equals("breath"))engine.setHoldingBreath(true);else if(found.equals("fire"))performAction("fire");
            else if(found.isEmpty()&&screen.equals("play")&&engine.phase==RangeEngine.Phase.PLAYING&&aimPointer<0){aimPointer=id;lastX=x;lastY=y;}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE&&aimPointer>=0){int at=event.findPointerIndex(aimPointer);if(at>=0){float px=event.getX(at)/scale,py=event.getY(at)/scale;if(engine.phase==RangeEngine.Phase.PLAYING){float f=engine.zoomIndex==0?130:factor();engine.moveAim(-(px-lastX)/f,(py-lastY)/f);}lastX=px;lastY=py;}invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){String pressed=pointers.remove(id);if(id==aimPointer)aimPointer=-1;
            if("breath".equals(pressed))engine.setHoldingBreath(false);
            else if(pressed!=null&&!pressed.isEmpty()&&!pressed.equals("fire"))for(Button b:buttons)if(b.id.equals(pressed)&&b.rect.contains(x,y)){performClick();performAction(pressed);break;}
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){pointers.clear();aimPointer=-1;engine.setHoldingBreath(false);return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void button(Canvas c,String id,float x,float y,float w,float height,String label,boolean primary){round(c,x,y,w,height,Math.min(17,height/2),primary?MINT:0xe019383e);text(c,label,x+w/2,y+height/2+4.5f,height>44?13:11,primary?BG:WHITE,MED,Paint.Align.CENTER);buttons.add(new Button(id,x,y,w,height));}
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface font,Paint.Align align){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setTypeface(font);paint.setTextAlign(align);paint.setTextSize(size);paint.setColor(color);c.drawText(s,x,y,paint);}
    private void round(Canvas c,float x,float y,float w,float height,float radius,int color){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);rect.set(x,y,x+w,y+height);c.drawRoundRect(rect,radius,radius,paint);}
    private void circle(Canvas c,float x,float y,float radius,int color){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawCircle(x,y,radius,paint);}
    private void ring(Canvas c,float x,float y,float radius,float width,int color){paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);c.drawCircle(x,y,radius,paint);paint.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x,float y,float xx,float yy,float width,int color){paint.setShader(null);paint.setStrokeWidth(width);paint.setColor(color);c.drawLine(x,y,xx,yy,paint);}
    private static int alpha(int color,int a){return (color&0xffffff)|(Math.max(0,Math.min(255,a))<<24);}
}
