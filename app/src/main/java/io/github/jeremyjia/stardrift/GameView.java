package io.github.jeremyjia.stardrift;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/** Code-drawn arcade presentation: no network, bitmap assets, or game runtime. */
public final class GameView extends View {
    public interface Host {
        boolean isSoundEnabled(); boolean isHapticsEnabled();
        void setSoundEnabled(boolean value); void setHapticsEnabled(boolean value);
        int bestScore(); int bestKills(); int runs();
        void recordRun(int score, int kills, float seconds, boolean won);
        void event(String name); void exitGame();
        boolean reduceMotion(); void setReduceMotion(boolean value);
        boolean isBonusRun(); void finishBonus(int score, boolean survived, boolean cancelled);
    }
    private static final int BG = Color.rgb(7, 17, 26), PANEL = Color.rgb(15, 31, 41);
    private static final int WHITE = Color.rgb(239, 246, 244), MUTED = Color.rgb(137, 161, 171);
    private static final int MINT = Color.rgb(171, 244, 216), BLUE = Color.rgb(140, 195, 237);
    private static final int RED = Color.rgb(251, 149, 134), GOLD = Color.rgb(239, 215, 165);
    private static final Typeface REG = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);
    private final Host host;
    private final GameEngine game;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final float[] stars = new float[330];
    private final Random visualRandom = new Random(160626);
    private float w = 400, h = 860, scale = 1, top = 28, bottom = 12;
    private float ambient, fxFlash, pulseWave, toastTime, shake, bonusFinishElapsed;
    private boolean bonusReturnQueued;
    private String toast = "", screen = "home", modal = "";
    private boolean suspended = true;
    private boolean paused, recorded, guideStartsRun, touchDriving;
    private long lastFrame;
    private float pointerX, pointerY, dragOffsetX, dragOffsetY;
    private int pointerId = -1;
    private String pressedButton;
    private Shader backdrop, homeHalo, shipHalo;

    private static final class Button {
        final String action; final RectF bounds;
        Button(String action, float x, float y, float width, float height) {
            this.action = action; this.bounds = new RectF(x,y,x+width,y+height);
        }
    }

    public GameView(Context context, Host host) {
        super(context);
        this.host = host;
        this.game = new GameEngine(this::gameEvent);
        if (host.isBonusRun()) {
            screen="play";game.startChallenge(System.nanoTime(),30);
            toast="神秘砖块已开启 · 存活 30 秒，带奖励回去";toastTime=5;
        }
        setFocusable(true); setClickable(true);
        setContentDescription("星隙，单手太空生存游戏。拖动驾驶，自动射击。顶部暂停，底部释放冲击波。");
        for (int i=0;i<stars.length;i+=3) {
            stars[i]=visualRandom.nextFloat(); stars[i+1]=visualRandom.nextFloat();
            stars[i+2]=visualRandom.nextFloat();
        }
        setOnApplyWindowInsetsListener((v,insets)->{
            float densityScale = Math.max(1, getWidth()) / 400f;
            int insetTop = insets.getSystemWindowInsetTop();
            int insetBottom = insets.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null)
                insetTop=Math.max(insetTop,insets.getDisplayCutout().getSafeInsetTop());
            top=Math.max(22, insetTop/densityScale+9);
            bottom=Math.max(14,insetBottom/densityScale+6);
            return insets;
        });
    }

    @Override protected void onSizeChanged(int width,int height,int oldw,int oldh) {
        scale=width/400f; w=400; h=height/scale;
        game.setBounds(w,h);
        backdrop=new LinearGradient(0,0,w,h,new int[]{Color.rgb(12,29,39),BG,Color.rgb(7,20,29)},null,Shader.TileMode.CLAMP);
        homeHalo=new RadialGradient(270,h*.39f,210,new int[]{0x223c9f96,0x0008171c},null,Shader.TileMode.CLAMP);
        shipHalo=new RadialGradient(0,0,28,new int[]{0x51aaf5da,0x00aaf5da},null,Shader.TileMode.CLAMP);
        requestApplyInsets();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth()==0) return;
        long now=SystemClock.elapsedRealtimeNanos();
        float dt=lastFrame==0?0:Math.min(.034f,(now-lastFrame)/1_000_000_000f);
        lastFrame=now;
        if (suspended) dt=0;
        ambient+=dt; fxFlash=Math.max(0,fxFlash-dt*2.8f);
        pulseWave=Math.max(0,pulseWave-dt); toastTime=Math.max(0,toastTime-dt);
        shake=Math.max(0,shake-dt*22);
        if (screen.equals("play") && !paused && modal.isEmpty() && !suspended) {
            game.update(dt);
            if ((game.phase==GameEngine.Phase.WON || game.phase==GameEngine.Phase.LOST) && !recorded) {
                host.recordRun(Math.round(game.score),Math.round(game.kills),game.time,game.phase==GameEngine.Phase.WON);
                recorded=true;
            }
            if(host.isBonusRun() && (game.phase==GameEngine.Phase.WON || game.phase==GameEngine.Phase.LOST)) {
                bonusFinishElapsed+=dt;
                if(bonusFinishElapsed>=.65f && !bonusReturnQueued) {
                    bonusReturnQueued=true;
                    post(()->{
                        host.finishBonus(bonusReward(),game.phase==GameEngine.Phase.WON,false);
                        // A failed storage write can retry automatically without a dead end.
                        postDelayed(()->{if(isAttachedToWindow()){bonusReturnQueued=false;bonusFinishElapsed=-1.4f;invalidate();}},2000);
                    });
                }
            }
        }
        buttons.clear();
        canvas.save(); canvas.scale(scale,scale);
        background(canvas);
        if (screen.equals("home")) home(canvas); else {
            canvas.save();
            if (shake>0 && !host.reduceMotion()) canvas.translate((visualRandom.nextFloat()-.5f)*shake,(visualRandom.nextFloat()-.5f)*shake);
            arena(canvas); canvas.restore(); hud(canvas);
            if (game.phase==GameEngine.Phase.UPGRADE && !paused) upgrade(canvas);
            else if (game.phase==GameEngine.Phase.LOST || game.phase==GameEngine.Phase.WON) {
                if(host.isBonusRun())bonusDeparture(canvas);else result(canvas);
            }
            else if (paused && modal.isEmpty()) pauseMenu(canvas);
        }
        if (modal.equals("settings")) settings(canvas);
        else if (modal.equals("guide")) guide(canvas);
        if (toastTime>0 && modal.isEmpty() && !paused && game.phase==GameEngine.Phase.PLAYING && screen.equals("play")) {
            float tw=Math.min(344,measure(toast,12,MEDIUM)+36);
            round(canvas,(w-tw)/2,h-bottom-155,tw,32,16,0xe31b353e);
            text(canvas,toast,w/2,h-bottom-134,12,MINT,MEDIUM,Paint.Align.CENTER);
        }
        canvas.restore();
        if (!suspended) postInvalidateOnAnimation();
    }

    private void background(Canvas c) {
        p.setShader(backdrop); p.setStyle(Paint.Style.FILL); p.setAlpha(255); c.drawRect(0,0,w,h,p); p.setShader(null);
        for (int i=0;i<stars.length;i+=3) {
            float speed=host.reduceMotion()?0:ambient*(screen.equals("play")?18+stars[i+2]*32:2+stars[i+2]*7);
            float sx=stars[i]*w,sy=(stars[i+1]*h+speed)%h;
            int a=(int)(35+stars[i+2]*91);
            circle(c,sx,sy,.45f+stars[i+2]*.75f,alpha(WHITE,a));
        }
        if(screen.equals("home")) {
            p.setShader(homeHalo);c.drawRect(0,0,w,h,p);p.setShader(null);
        }
    }

    private void home(Canvas c) {
        text(c,"‹  返回游戏集",26,top+22,11,MINT,MEDIUM,Paint.Align.LEFT);
        buttons.add(new Button("collection",18,top-3,135,41));
        circleButton(c,"settings",352,top+10,"settings",false);
        text(c,"星隙",24,top+100,61,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"A LITTLE SHIP. AN ENDLESS SKY.",28,top+130,8.8f,MUTED,MONO,Paint.Align.LEFT);
        float cy=Math.max(top+235,Math.min(h*.405f,h-390));
        drawHomePlanet(c,270,cy,89);
        float sy=cy+45+(host.reduceMotion()?0:(float)Math.sin(ambient*.8)*5);
        c.save();c.translate(162,sy);c.rotate(26);drawShip(c,0,0,2.4f,true,1);c.restore();
        // A quiet chart line makes the hero feel like a flight map.
        line(c,47,cy-40,102,cy-40,1,0x445d8c91);
        line(c,47,cy-40,47,cy+100,1,0x445d8c91);
        circle(c,47,cy+100,2.2f,MINT);
        text(c,"01",57,cy+104,10,MUTED,MONO,Paint.Align.LEFT);
        text(c,"DEEP SPACE",47,cy-53,7.5f,MUTED,MONO,Paint.Align.LEFT);
        float infoY=h-bottom-282;
        text(c,"把生路，飞成一道光。",28,infoY,22,WHITE,MEDIUM,Paint.Align.LEFT);
        text(c,"躲过星群，收集微光，在三分钟里走得更远。",28,infoY+29,11.5f,MUTED,REG,Paint.Align.LEFT);
        line(c,28,infoY+52,372,infoY+52,1,0x253e5962);
        text(c,"最高得分",28,infoY+79,10,MUTED,REG,Paint.Align.LEFT);
        text(c,String.format(Locale.US,"%,d",host.bestScore()),28,infoY+108,24,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"累计航行",189,infoY+79,10,MUTED,REG,Paint.Align.LEFT);
        text(c,String.format(Locale.US,"%02d",host.runs()),189,infoY+108,24,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"OFFLINE",370,infoY+106,8,MINT,MONO,Paint.Align.RIGHT);
        button(c,"start",26,h-bottom-143,348,61,"开始航行","arrow",true);
        button(c,"guide",26,h-bottom-70,348,42,"飞行指南",null,false);
        text(c,"单 手 驾 驶    /    自 动 开 火    /    3 分 钟",200,h-bottom-8,8,MUTED,REG,Paint.Align.CENTER);
    }

    private void drawHomePlanet(Canvas c,float x,float y,float r) {
        c.save(); c.translate(x,y); c.rotate(-24);
        oval(c,-r*1.5f,-r*.52f,r*1.5f,r*.52f,0x28597f83,false,1);
        circle(c,0,0,r,0xff152f38);
        oval(c,-r,-r,r,r,0x45648e93,false,1);
        c.save();path.reset();path.addCircle(0,0,r,Path.Direction.CW);c.clipPath(path);
        for(int j=0;j<9;j++) {
            float yy=-r+j*24;
            oval(c,-r*1.2f,yy-27,r*1.4f,yy+32,0x165fb2a8,false,10);
        }
        circle(c,r*.46f,-r*.36f,r*1.05f,0xff0a1d28);
        c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f);p.setColor(0x8893bfbd);rect.set(-r*1.5f,-r*.52f,r*1.5f,r*.52f);
        c.drawArc(rect,0,180,false,p);p.setStyle(Paint.Style.FILL);
        circle(c,-r*1.3f,r*.28f,3,MINT);
        c.restore();
    }

    private void arena(Canvas c) {
        // Edge guide remains understated, leaving the playfield uncluttered.
        line(c,22,top+100,378,top+100,.5f,0x193e6871);
        float waveAge=game.time%(game.challengeMode?game.runDuration/3:30);
        if(waveAge<2) {
            float a=Math.min(1,(2-waveAge)*1.5f);
            String label=game.challengeMode?(game.getWave()==1?"第一波 · 侦察群":game.getWave()==2?"第二波 · 侧翼夹击":"最终波 · 弹幕突围"):("WAVE 0"+game.getWave());
            text(c,label,200,top+153,16,alpha(MINT,(int)(a*200)),MEDIUM,Paint.Align.CENTER);
        }
        for(GameEngine.Gem g:game.gems) {
            float pulse=(float)Math.sin(ambient*3+g.age)*.7f;
            circle(c,g.x,g.y,7+pulse,0x163ee5b4);
            diamond(c,g.x,g.y,3.1f+pulse*.2f,MINT,false);
        }
        for(GameEngine.Enemy e:game.enemies) drawEnemy(c,e);
        for(GameEngine.Shot s:game.shots) {
            if(s.hostile) {
                circle(c,s.x,s.y,s.r+3,0x33ff998b);circle(c,s.x,s.y,s.r,RED);
                circle(c,s.x,s.y,Math.max(1,s.r*.4f),WHITE);
            } else {
                float mag=Math.max(1,(float)Math.hypot(s.vx,s.vy));
                line(c,s.x-s.vx/mag*12,s.y-s.vy/mag*12,s.x,s.y,3,0x30aef7e0);
                line(c,s.x-s.vx/mag*7,s.y-s.vy/mag*7,s.x,s.y,1.8f,MINT);
                circle(c,s.x,s.y,1.4f,WHITE);
            }
        }
        for(GameEngine.Particle a:game.particles) {
            float life=Math.max(0,Math.min(1,a.life/Math.max(.01f,a.maxLife)));
            if(a.ring) ring(c,a.x,a.y,a.size,1.5f,alpha(a.color,(int)(life*160)));
            else if(!host.reduceMotion() || life>.65f) circle(c,a.x,a.y,Math.max(.5f,a.size*life),alpha(a.color,(int)(life*210)));
        }
        if (pulseWave>0) {
            float rad=(1-pulseWave)*460;
            ring(c,game.shipX,game.shipY,rad,2,alpha(MINT,(int)(pulseWave*150)));
            ring(c,game.shipX,game.shipY,rad*.9f,.5f,alpha(WHITE,(int)(pulseWave*85)));
        }
        if(game.hp>0) {
            float vis=game.invulnerable>0?.45f+.55f*(float)Math.abs(Math.sin(ambient*19)):1;
            drawShip(c,game.shipX,game.shipY,1,true,vis);
            if(game.invulnerable>0) ring(c,game.shipX,game.shipY,18,1,alpha(MINT,(int)(100*vis)));
        }
        if(fxFlash>0 && !host.reduceMotion()) {
            p.setColor(alpha(RED,(int)(fxFlash*35)));c.drawRect(0,0,w,h,p);
        }
        if (touchDriving && !host.reduceMotion()) {
            ring(c,pointerX,pointerY,15,1,0x369acabb);
            circle(c,pointerX,pointerY,2,0x769acabb);
        }
    }

    private void drawShip(Canvas c,float x,float y,float size,boolean flame,float visibility) {
        c.save();c.translate(x,y);c.scale(size,size);
        p.setShader(shipHalo);p.setAlpha((int)(visibility*255));c.drawCircle(0,0,28,p);p.setShader(null);p.setAlpha(255);
        if(flame) {
            float len=13+(host.reduceMotion()?5:(float)Math.sin(ambient*26)*5);
            path.reset();path.moveTo(-3,9);path.lineTo(0,9+len);path.lineTo(3,9);path.close();
            fillPath(c,alpha(MINT,(int)(160*visibility)));
            line(c,0,11,0,16,2,alpha(WHITE,(int)(200*visibility)));
        }
        path.reset();path.moveTo(0,-15);path.lineTo(10,11);path.lineTo(0,6);path.lineTo(-10,11);path.close();
        fillPath(c,alpha(WHITE,(int)(visibility*255)));
        path.reset();path.moveTo(0,-8);path.lineTo(3.4f,4);path.lineTo(0,2);path.lineTo(-3.4f,4);path.close();fillPath(c,0xff284f58);
        line(c,0,-15,0,-9,1,alpha(MINT,(int)(visibility*255)));
        c.restore();
    }

    private void drawEnemy(Canvas c,GameEngine.Enemy e) {
        int color=e.type==2?GOLD:e.type==3?RED:e.type==1?RED:BLUE;
        if(e.age<.7f) {
            float progress=Math.max(0,e.age/.7f);
            ring(c,e.x,e.y,e.r+11*(1-progress),1,alpha(color,90));
            line(c,e.x-4,e.y,e.x+4,e.y,1,alpha(color,150));
            line(c,e.x,e.y-4,e.x,e.y+4,1,alpha(color,150));return;
        }
        c.save();c.translate(e.x,e.y);
        float angle=(float)Math.toDegrees(Math.atan2(game.shipY-e.y,game.shipX-e.x))+90;
        c.rotate(e.type==2?e.age*12:angle);
        if(e.type==3) {
            ring(c,0,0,e.r+7,1,0x49fb9586);
            polygon(c,0,0,e.r,6,-90,0xff422a34,true,1);
            polygon(c,0,0,e.r,6,-90,color,false,1.8f);
            diamond(c,0,0,e.r*.5f,e.hit>0?WHITE:color,false);
            for(int i=-1;i<=1;i++) {circle(c,i*e.r*.65f,e.r*.35f,3,WHITE);}
        } else if(e.type==2) {
            polygon(c,0,0,e.r,6,-30,0xff37332c,true,1);
            polygon(c,0,0,e.r,6,-30,e.hit>0?WHITE:color,false,1.4f);
            diamond(c,0,0,5,color,true);
        } else if(e.type==1) {
            if(e.chargeTimer<.5f)ring(c,0,0,e.r+5,1.3f,alpha(RED,170));
            path.reset();path.moveTo(0,-e.r);path.lineTo(e.r*.75f,e.r);path.lineTo(0,e.r*.45f);path.lineTo(-e.r*.75f,e.r);path.close();
            fillPath(c,e.hit>0?WHITE:0xff50313b);strokePath(c,color,1.3f);
            circle(c,0,1,2,color);
        } else {
            diamond(c,0,0,e.r,e.hit>0?WHITE:0xff294251,false);
            diamond(c,0,0,e.r,color,true);circle(c,0,0,2.4f,color);
        }
        c.restore();
        if(e.hp<e.maxHp && e.hp>0) {
            float len=e.type==3?e.r*2:20;
            round(c,e.x-len/2,e.y-e.r-9,len,2,1,0x303e6772);
            round(c,e.x-len/2,e.y-e.r-9,len*Math.max(0,e.hp/e.maxHp),2,1,color);
        }
    }

    private void hud(Canvas c) {
        round(c,18,top+2,364,84,18,0xe70b1923);
        text(c,"存活",32,top+24,9,MUTED,REG,Paint.Align.LEFT);
        text(c,clock(game.time),32,top+52,25,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"/ "+clock(game.runDuration),103,top+51,10,MUTED,MONO,Paint.Align.LEFT);
        text(c,String.format(Locale.US,"%,d",Math.round(game.score)),291,top+42,21,WHITE,MEDIUM,Paint.Align.RIGHT);
        text(c,"得分",291,top+60,9,MUTED,REG,Paint.Align.RIGHT);
        circleButton(c,"pause",349,top+34,"pause",false);
        float life=Math.max(0,Math.min(1,game.hp/Math.max(1,game.maxHp)));
        round(c,32,top+67,137,3,1.5f,0xff263d46);
        round(c,32,top+67,137*life,3,1.5f,life<.3f?RED:MINT);
        text(c,String.valueOf(Math.max(0,Math.round(game.hp))),180,top+72,9,life<.3f?RED:MUTED,MONO,Paint.Align.LEFT);
        float xp=Math.min(1,game.xp/Math.max(1,game.xpNext));
        text(c,"LV "+game.level,23,top+108,9,MINT,MONO,Paint.Align.LEFT);
        round(c,65,top+102,313,2,1,0xff1c343f);round(c,65,top+102,313*xp,2,1,MINT);
        if(game.combo>=3)text(c,game.combo+" 连破",377,top+128,10,GOLD,MEDIUM,Paint.Align.RIGHT);
        float bY=h-bottom-65;
        boolean ready=game.pulseCharge>=.999f;
        round(c,111,bY,178,51,25,ready?0xffa8eed6:0xe8142935);
        border(c,111,bY,178,51,25,ready?0x006f9a8c:0xff34505c,1);
        ring(c,137,bY+25,7,1.2f,ready?BG:MUTED);
        ring(c,137,bY+25,3,1,ready?BG:MUTED);
        text(c,ready?"释放冲击波":"充能 "+Math.round(game.pulseCharge*100)+"%",205,bY+31,13,ready?BG:MUTED,MEDIUM,Paint.Align.CENTER);
        buttons.add(new Button("pulse",111,bY,178,51));
        if(game.time<13 && game.phase==GameEngine.Phase.PLAYING) {
            text(c,"拖动任意位置驾驶 · 松手悬停",200,bY-25,10,MUTED,REG,Paint.Align.CENTER);
        } else {
            text(c,game.challengeMode?"WAVE "+game.getWave()+"/3 · 自动升级 · 结束自动返回":game.time>=150?"最终信号  /  消灭守望者":"击破 "+Math.round(game.kills)+"  ·  星尘会自动吸附",200,bY-22,9,MUTED,REG,Paint.Align.CENTER);
        }
        float progress=Math.min(1,game.time/game.runDuration);
        round(c,22,h-bottom-1,356,2,1,0xff19313b);round(c,22,h-bottom-1,356*progress,2,1,0xff49786f);
    }

    private void upgrade(Canvas c) {
        overlay(c);buttons.clear();
        float y=Math.max(top+35,(h-476)/2);
        text(c,"S I G N A L   F O U N D",200,y+7,9,MINT,MONO,Paint.Align.CENTER);
        text(c,"选择一次进化",200,y+47,28,WHITE,LIGHT,Paint.Align.CENTER);
        text(c,"战场已暂停 · 选择后继续航行",200,y+74,11,MUTED,REG,Paint.Align.CENTER);
        for(int i=0;i<game.choices.size()&&i<3;i++) {
            GameEngine.Upgrade u=game.choices.get(i);
            float yy=y+101+i*101;
            round(c,25,yy,350,87,18,PANEL);border(c,25,yy,350,87,18,i==0?0xff41665f:0xff2a424e,1);
            round(c,40,yy+22,43,43,13,0xff203e41);
            upgradeIcon(c,u.id,61.5f,yy+43.5f,MINT);
            text(c,u.title,98,yy+32,17,WHITE,MEDIUM,Paint.Align.LEFT);
            wrapped(c,u.description,98,yy+54,247,10.5f,MUTED,REG,15,2);
            text(c,u.tag,354,yy+25,8,MINT,REG,Paint.Align.RIGHT);
            buttons.add(new Button("upgrade:"+i,25,yy,350,87));
        }
        text(c,"LEVEL "+game.level+"  /  BUILD YOUR OWN WAY",200,y+432,8,MUTED,MONO,Paint.Align.CENTER);
    }

    private void pauseMenu(Canvas c) {
        overlay(c);buttons.clear();
        float y=(h-340)/2;
        text(c,"TAKE A BREATH",200,y+8,9,MINT,MONO,Paint.Align.CENTER);
        text(c,"航行已暂停",200,y+51,29,WHITE,LIGHT,Paint.Align.CENTER);
        text(c,"星空会等你回来。",200,y+80,12,MUTED,REG,Paint.Align.CENTER);
        button(c,"resume",40,y+114,320,57,"继续航行","arrow",true);
        button(c,"settings",40,y+185,320,47,"声音与反馈",null,false);
        button(c,"home",40,y+246,320,47,host.isBonusRun()?"结束奇遇，返回碰界":"结束本局，返回星港",null,false);
    }

    private void result(Canvas c) {
        overlay(c);buttons.clear();
        boolean won=game.phase==GameEngine.Phase.WON;
        float y=(h-461)/2;
        text(c,host.isBonusRun()?"A LITTLE DETOUR":won?"SECTOR COMPLETE":"SIGNAL LOST",200,y+8,9,won?MINT:RED,MONO,Paint.Align.CENTER);
        text(c,host.isBonusRun()?"把星光带回去":won?"穿过这片星海":"微光从未消失",200,y+51,29,WHITE,LIGHT,Paint.Align.CENTER);
        text(c,host.isBonusRun()?"碰界的砖块和小球，仍在原处等你。":won?"边境已经安静下来。下一次，再飞远一点。":"每一次航行，都会让下一次更从容。",200,y+82,11,MUTED,REG,Paint.Align.CENTER);
        round(c,28,y+109,344,180,24,PANEL);border(c,28,y+109,344,180,24,0xff2a424a,1);
        text(c,"本次得分",200,y+138,10,MUTED,REG,Paint.Align.CENTER);
        text(c,String.format(Locale.US,"%,d",Math.round(game.score)),200,y+191,46,WHITE,LIGHT,Paint.Align.CENTER);
        float[] xs={86,200,314};String[] labels={"存活","击破","等级"};
        String[] values={clock(game.time),""+Math.round(game.kills),""+game.level};
        for(int i=0;i<3;i++){text(c,values[i],xs[i],y+236,19,WHITE,MEDIUM,Paint.Align.CENTER);text(c,labels[i],xs[i],y+260,10,MUTED,REG,Paint.Align.CENTER);}
        text(c,host.isBonusRun()?"碰界奖励 +"+bonusReward()+(won?"  ·  宽板 + 满能量":""):Math.round(game.score)>=host.bestScore()?"个人最佳航迹":"最佳纪录  "+host.bestScore(),200,y+315,10,MINT,REG,Paint.Align.CENTER);
        button(c,host.isBonusRun()?"finishBonus":"restart",28,y+338,344,58,host.isBonusRun()?"领取奖励，返回碰界":"再次出航","arrow",true);
        if(!host.isBonusRun())button(c,"home",28,y+408,344,44,"返回星港",null,false);
    }

    private void bonusDeparture(Canvas c) {
        buttons.clear();
        float y=h*.43f;
        round(c,35,y,330,112,23,0xf0102832);
        border(c,35,y,330,112,23,0xff4e8a7a,1);
        text(c,game.phase==GameEngine.Phase.WON?"突围成功":"奇遇结束",200,y+44,26,WHITE,MEDIUM,Paint.Align.CENTER);
        text(c,"奖励 +"+bonusReward()+" · 自动返回碰界",200,y+77,12,MINT,REG,Paint.Align.CENTER);
        round(c,60,y+95,280*Math.min(1,bonusFinishElapsed/.65f),2,1,MINT);
    }

    private void settings(Canvas c) {
        overlay(c);buttons.clear();
        float y=(h-396)/2;
        round(c,22,y,356,396,27,PANEL);border(c,22,y,356,396,27,0xff304650,1);
        text(c,"让航行更合心意",43,y+49,24,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"PREFERENCES",43,y+73,8,MUTED,MONO,Paint.Align.LEFT);
        toggle(c,"sound",y+96,"声音","轻量电子音效",host.isSoundEnabled());
        toggle(c,"haptics",y+167,"触感","击中、受伤与冲击波反馈",host.isHapticsEnabled());
        toggle(c,"motion",y+238,"减少动态效果","减弱镜头震动与粒子",host.reduceMotion());
        button(c,"close",42,y+326,316,49,"完成",null,true);
    }

    private void toggle(Canvas c,String action,float y,String title,String subtitle,boolean on) {
        text(c,title,43,y+22,16,WHITE,MEDIUM,Paint.Align.LEFT);
        text(c,subtitle,43,y+44,10,MUTED,REG,Paint.Align.LEFT);
        round(c,307,y+10,47,26,13,on?MINT:0xff314750);
        circle(c,on?341:320,y+23,9.5f,on?0xff15352e:MUTED);
        buttons.add(new Button(action,34,y,332,60));
    }

    private void guide(Canvas c) {
        overlay(c);buttons.clear();float y=(h-481)/2;
        round(c,22,y,356,481,27,PANEL);border(c,22,y,356,481,27,0xff304650,1);
        text(c,"第一段航程",44,y+47,26,WHITE,LIGHT,Paint.Align.LEFT);
        text(c,"一根手指，就能穿过星海。",44,y+76,12,MUTED,REG,Paint.Align.LEFT);
        String[] titles={"拖动驾驶，自动开火","收集星尘，选择进化","蓄满能量，释放冲击波"};
        String[] bodies={"在战场任意位置按住拖动。飞船会跟随移动，松手悬停。", "消灭敌人后靠近绿色星尘。升级时战场暂停，挑一项强化。", "点击底部按钮清除附近弹幕。坚持三分钟，击败最终守望者。"};
        for(int i=0;i<3;i++) {
            float yy=y+111+i*88;
            ring(c,57,yy+18,14,1,0xff44665f);text(c,"0"+(i+1),57,yy+22,10,MINT,MONO,Paint.Align.CENTER);
            text(c,titles[i],85,yy+17,14,WHITE,MEDIUM,Paint.Align.LEFT);
            wrapped(c,bodies[i],85,yy+39,260,11,MUTED,REG,17,2);
        }
        button(c,"guideDone",42,y+392,316,54,guideStartsRun?"明白，起飞":"回到星港","arrow",true);
    }

    private void gameEvent(String name) {
        if(name.equals("auto_upgrade")){host.event("upgrade");toast="自动装配 · "+game.lastUpgrade;toastTime=1.5f;return;}
        host.event(name);
        if(name.equals("hit")){fxFlash=.8f;shake=5;}
        else if(name.equals("pulse")){pulseWave=1;shake=3;}
        else if(name.equals("upgrade")){touchDriving=false;game.releaseTarget();}
        else if(name.equals("kill") && game.combo>=3){shake=Math.min(2.4f,.7f+game.combo*.1f);}
    }

    private void startGame() {
        screen="play";modal="";paused=false;recorded=false;touchDriving=false;
        game.setBounds(w,h);game.start(System.nanoTime());lastFrame=0;
        fxFlash=0;shake=0;pulseWave=0;toastTime=0;announceForAccessibility("航行开始，拖动驾驶，自动射击");
    }

    private int bonusReward(){return Math.min(3000,Math.round(game.score*.5f)+(game.phase==GameEngine.Phase.WON?500:0));}

    private void action(String action) {
        if (action==null) return;
        if(!action.equals("pulse")) host.event("tap");
        if(action.startsWith("upgrade:")) {
            int i=Integer.parseInt(action.substring(8));
            if(i<game.choices.size()) {String title=game.choices.get(i).title;game.chooseUpgrade(i);toast=title+"  已装配";toastTime=2.3f;}
            lastFrame=0;return;
        }
        switch(action) {
            case "collection": host.exitGame();break;
            case "finishBonus": host.finishBonus(bonusReward(),game.phase==GameEngine.Phase.WON,false);break;
            case "start": if(host.runs()==0){guideStartsRun=true;modal="guide";}else startGame();break;
            case "restart": startGame();break;
            case "guide": guideStartsRun=false;modal="guide";break;
            case "guideDone": modal="";if(guideStartsRun)startGame();break;
            case "settings": modal="settings";break;
            case "close": modal="";break;
            case "sound": host.setSoundEnabled(!host.isSoundEnabled());if(host.isSoundEnabled())host.event("tap");break;
            case "haptics": host.setHapticsEnabled(!host.isHapticsEnabled());if(host.isHapticsEnabled())host.event("tap");break;
            case "motion": host.setReduceMotion(!host.reduceMotion());break;
            case "pause": if(game.phase==GameEngine.Phase.PLAYING){paused=true;game.releaseTarget();}break;
            case "resume": paused=false;lastFrame=0;break;
            case "home":
                if(host.isBonusRun()){host.finishBonus(0,false,true);break;}
                if(screen.equals("play")&&!recorded&&game.time>1)host.recordRun(Math.round(game.score),Math.round(game.kills),game.time,false);
                recorded=true;screen="home";paused=false;modal="";touchDriving=false;game.releaseTarget();break;
            case "pulse": if(!paused&&modal.isEmpty()&&game.phase==GameEngine.Phase.PLAYING){if(!game.pulse()){toast="冲击波正在充能";toastTime=1;}}break;
        }
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX()/scale,y=e.getY()/scale;
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerX=x;pointerY=y;pointerId=e.getPointerId(0);pressedButton=null;
                for(int i=buttons.size()-1;i>=0;i--)if(buttons.get(i).bounds.contains(x,y)){pressedButton=buttons.get(i).action;break;}
                if(pressedButton==null&&canDrive()) {
                    touchDriving=true;dragOffsetX=game.shipX-x;dragOffsetY=game.shipY-y;
                    game.setTarget(game.shipX,game.shipY);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                int index=e.findPointerIndex(pointerId);if(index<0)return true;
                pointerX=e.getX(index)/scale;pointerY=e.getY(index)/scale;
                if(touchDriving&&canDrive())game.setTarget(pointerX+dragOffsetX,pointerY+dragOffsetY);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if(e.getPointerId(e.getActionIndex())==pointerId) {
                    int replacement=e.getActionIndex()==0?1:0;
                    pointerId=e.getPointerId(replacement);pressedButton=null;
                    pointerX=e.getX(replacement)/scale;pointerY=e.getY(replacement)/scale;
                    dragOffsetX=game.shipX-pointerX;dragOffsetY=game.shipY-pointerY;
                    game.releaseTarget();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(pressedButton!=null) {
                    for(Button b:buttons)if(b.action.equals(pressedButton)&&b.bounds.contains(x,y)){action(pressedButton);break;}
                }
                touchDriving=false;pressedButton=null;pointerId=-1;game.releaseTarget();performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                touchDriving=false;pressedButton=null;pointerId=-1;game.releaseTarget();return true;
        }
        return true;
    }
    private boolean canDrive(){return screen.equals("play")&&modal.isEmpty()&&!paused&&game.phase==GameEngine.Phase.PLAYING;}
    @Override public boolean performClick(){super.performClick();return true;}
    public void onHostPause(){if(!suspended&&screen.equals("play")&&game.phase==GameEngine.Phase.PLAYING)paused=true;suspended=true;touchDriving=false;game.releaseTarget();lastFrame=0;}
    public void onHostResume(){suspended=false;lastFrame=0;invalidate();}
    public boolean handleBack(){
        if(!modal.isEmpty()){modal="";invalidate();return true;}
        if(screen.equals("play")) {
            if(game.phase==GameEngine.Phase.WON||game.phase==GameEngine.Phase.LOST){if(host.isBonusRun()){host.finishBonus(bonusReward(),game.phase==GameEngine.Phase.WON,false);return true;}screen="home";paused=false;}
            else paused=!paused;
            touchDriving=false;game.releaseTarget();lastFrame=0;invalidate();return true;
        }
        return false;
    }

    private void overlay(Canvas c){p.setColor(0xec07121b);p.setStyle(Paint.Style.FILL);c.drawRect(0,0,w,h,p);}
    private void button(Canvas c,String id,float x,float y,float ww,float hh,String label,String icon,boolean primary){
        boolean pressed=id.equals(pressedButton);
        round(c,x,y,ww,hh,17,primary?(pressed?0xff8bcbb6:MINT):(pressed?0xff243d46:0xff122630));
        if(!primary)border(c,x,y,ww,hh,17,0xff2a414b,1);
        text(c,label,x+ww/2,y+hh/2+5,primary?16:12,primary?BG:WHITE,MEDIUM,Paint.Align.CENTER);
        if(icon!=null){float ax=x+ww-29,ay=y+hh/2;line(c,ax-6,ay,ax+5,ay,1.3f,BG);line(c,ax+1,ay-4,ax+5,ay,1.3f,BG);line(c,ax+1,ay+4,ax+5,ay,1.3f,BG);}
        buttons.add(new Button(id,x,y,ww,hh));
    }
    private void circleButton(Canvas c,String action,float x,float y,String icon,boolean active){
        circle(c,x,y,19,0xff18303b);ring(c,x,y,19,.75f,0xff304650);
        if(icon.equals("pause")){round(c,x-5,y-6,3,12,1,WHITE);round(c,x+2,y-6,3,12,1,WHITE);}
        else {ring(c,x,y,6,1.2f,WHITE);circle(c,x,y,2,WHITE);for(int i=0;i<6;i++){double a=i*Math.PI/3;line(c,x+(float)Math.cos(a)*7,y+(float)Math.sin(a)*7,x+(float)Math.cos(a)*9,y+(float)Math.sin(a)*9,1.4f,WHITE);}}
        buttons.add(new Button(action,x-23,y-23,46,46));
    }
    private void upgradeIcon(Canvas c,String id,float x,float y,int color){
        int hash=Math.abs(id.hashCode())%4;
        if(hash==0){line(c,x-7,y+7,x+7,y-7,2,color);line(c,x-6,y-6,x+6,y+6,1,color);ring(c,x,y,12,1,0xff476c65);}
        else if(hash==1){polygon(c,x,y,12,6,-30,color,false,1.3f);circle(c,x,y,3,color);}
        else if(hash==2){for(int i=-1;i<=1;i++){line(c,x+i*7,y+8,x+i*7,y-7,1.5f,color);circle(c,x+i*7,y-8,2,color);}}
        else {ring(c,x,y,11,1.3f,color);line(c,x-5,y,x+5,y,1.5f,color);line(c,x,y-5,x,y+5,1.5f,color);}
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface face,Paint.Align align){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTypeface(face);p.setTextAlign(align);c.drawText(s,x,y,p);
    }
    private float measure(String s,float size,Typeface face){p.setTextSize(size);p.setTypeface(face);return p.measureText(s);}
    private void wrapped(Canvas c,String s,float x,float y,float width,float size,int color,Typeface face,float lineHeight,int maxLines){
        p.setTextSize(size);p.setTypeface(face);int from=0;
        for(int line=0;line<maxLines&&from<s.length();line++){
            int count=p.breakText(s,from,s.length(),true,width,null);if(count==0)break;
            String part=s.substring(from,from+count);from+=count;
            if(line==maxLines-1&&from<s.length())part=part.substring(0,Math.max(0,part.length()-1))+"…";
            text(c,part,x,y+line*lineHeight,size,color,face,Paint.Align.LEFT);
        }
    }
    private void round(Canvas c,float x,float y,float ww,float hh,float radius,int color){if(ww<=0||hh<=0)return;p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);rect.set(x,y,x+ww,y+hh);c.drawRoundRect(rect,radius,radius,p);}
    private void border(Canvas c,float x,float y,float ww,float hh,float radius,int color,float stroke){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);rect.set(x,y,x+ww,y+hh);c.drawRoundRect(rect,radius,radius,p);p.setStyle(Paint.Style.FILL);}
    private void circle(Canvas c,float x,float y,float r,int color){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,Math.max(0,r),p);}
    private void ring(Canvas c,float x,float y,float r,float stroke,int color){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);c.drawCircle(x,y,Math.max(0,r),p);p.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x,float y,float xx,float yy,float stroke,int color){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,p);p.setStyle(Paint.Style.FILL);}
    private void oval(Canvas c,float l,float t,float r,float b,int color,boolean fill,float width){p.setShader(null);p.setColor(color);p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(width);rect.set(l,t,r,b);c.drawOval(rect,p);p.setStyle(Paint.Style.FILL);}
    private void diamond(Canvas c,float x,float y,float r,int color,boolean stroke){path.reset();path.moveTo(x,y-r);path.lineTo(x+r,y);path.lineTo(x,y+r);path.lineTo(x-r,y);path.close();if(stroke)strokePath(c,color,1.2f);else fillPath(c,color);}
    private void polygon(Canvas c,float x,float y,float r,int n,float offset,int color,boolean fill,float width){path.reset();for(int i=0;i<n;i++){double a=(offset+i*360f/n)*Math.PI/180;float xx=x+(float)Math.cos(a)*r,yy=y+(float)Math.sin(a)*r;if(i==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);}path.close();if(fill)fillPath(c,color);else strokePath(c,color,width);}
    private void fillPath(Canvas c,int color){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);}
    private void strokePath(Canvas c,int color,float width){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    private static int alpha(int color,int a){return (Math.max(0,Math.min(255,a))<<24)|(color&0x00ffffff);}
    private static String clock(float secs){int n=Math.max(0,(int)secs);return String.format(Locale.US,"%02d:%02d",n/60,n%60);}
}
