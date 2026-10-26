package dev.brickzen.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/* JADX INFO: loaded from: classes.dex */
final class GameView extends View {
    static final int BG = -526608;
    static final int[] COLORS;
    static final int GREEN = -14128551;
    static final int INK = -15188170;
    static final int MUTED = -8481911;
    static final int[][] PALETTES;
    static final int[] SPECIAL_COLORS = {-12017967, -1073073, -2000026, -6259755};
    static final String[] SPECIAL_NAMES = {"连锁闪电", "范围爆破", "流星雨", "彩虹分裂"};
    float accumulator;
    Appearance appearance;
    int appearanceMode;
    final Audio audio;
    boolean awake;
    int bestChallenge;
    int bestZen;
    final Typeface bold;
    int bottomInset;
    final Path brickPath;
    final HashMap<Integer, Shader> brickShaders;
    final LinkedHashMap<String, RectF> buttons;
    float clock;
    final Path effectPath;
    GameEngine engine;
    long frames;
    float h;
    boolean haptic;
    final Haptics haptics;
    final HashMap<String, String> labels;
    long lastFrame;
    final Typeface normal;
    int paddlePointer;
    final Paint paint;
    final ArrayList<Particle> particles;
    final ArrayList<Popup> popups;
    final SharedPreferences prefs;
    final HashMap<Integer, String> pressed;
    final GameEngine preview;
    final Random random;
    final RectF rect;
    float scale;
    Scene scene;
    float shake;
    boolean sound;
    final ArrayList<SpecialVisual> specials;
    String toast;
    float toastLife;
    int topInset;
    int zenLevel;

    enum Scene {
        HOME,
        GAME,
        HELP
    }

    static {
        int[] iArr = {-7947356, -6109732, -3885604, -1789817, -2635640, -7026502};
        COLORS = iArr;
        PALETTES = new int[][]{iArr, new int[]{-10967090, -1008779, -2574257, -6965156, -5601596, -10111581}, new int[]{-2915965, -1725079, -4354885, -7820856, -4147603, -8211295}, new int[]{-7239216, -10110776, -2781264, -7686945, -2904472, -8208997}};
    }

    static final class Particle {
        int color;
        float life;
        float max;
        float r;
        float rotation;
        float vx;
        float vy;
        float x;
        float y;

        Particle() {
        }
    }

    static final class Popup {
        int color;
        float life;
        String text;
        float x;
        float y;

        Popup() {
        }
    }

    static final class SpecialVisual {
        float age;
        final GameEngine.Burst burst;

        SpecialVisual(GameEngine.Burst burst) {
            this.burst = burst;
        }
    }

    GameView(Context context, boolean z) {
        super(context);
        GameEngine gameEngine = new GameEngine();
        this.engine = gameEngine;
        GameEngine gameEngine2 = new GameEngine();
        this.preview = gameEngine2;
        this.paint = new Paint(3);
        this.rect = new RectF();
        this.buttons = new LinkedHashMap<>();
        this.labels = new HashMap<>();
        this.pressed = new HashMap<>();
        this.particles = new ArrayList<>();
        this.popups = new ArrayList<>();
        this.specials = new ArrayList<>();
        this.random = new Random();
        this.brickShaders = new HashMap<>();
        this.brickPath = new Path();
        this.effectPath = new Path();
        this.scene = Scene.HOME;
        this.appearance = Appearance.resolve(0, false);
        this.scale = 1.0f;
        this.h = 2000.0f;
        this.toast = "";
        this.paddlePointer = -1;
        this.normal = Typeface.create("sans-serif", 0);
        this.bold = Typeface.create("sans-serif", 1);
        SharedPreferences sharedPreferences = context.getSharedPreferences(z ? "brickzen-verification" : "brickzen", 0);
        this.prefs = sharedPreferences;
        this.bestZen = sharedPreferences.getInt("best_zen", 0);
        this.bestChallenge = sharedPreferences.getInt("best_challenge", 0);
        this.zenLevel = sharedPreferences.getInt("zen_level", 1);
        this.haptic = sharedPreferences.getBoolean("haptic", true);
        this.sound = sharedPreferences.getBoolean("sound", true);
        this.appearanceMode = Appearance.normalize(sharedPreferences.getInt("appearance", 0));
        refreshAppearance();
        gameEngine.setPaddleLift(sharedPreferences.getFloat("paddle_lift", 0.22f));
        gameEngine2.start(1, true, 0);
        Audio audio = new Audio(context);
        this.audio = audio;
        audio.enabled = this.sound;
        this.haptics = new Haptics(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setImportantForAccessibility(1);
        setContentDescription(context.getString(R.string.app_name) + "，离线打砖块。无限模式不限掉球次数。滑动屏幕控制挡板，轻点发球。");
    }

    void refreshAppearance() {
        refreshAppearance(getResources().getConfiguration());
    }

    void refreshAppearance(Configuration configuration) {
        this.appearance = Appearance.resolve(this.appearanceMode, (configuration.uiMode & 48) == 32);
        invalidate();
    }

    void setAppearance(int i) {
        this.appearanceMode = Appearance.normalize(i);
        refreshAppearance();
        save();
        ((MainActivity) getContext()).applySystemBars();
    }

    @Override // android.view.View
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            this.topInset = insets.top;
            this.bottomInset = insets.bottom;
        } else {
            this.topInset = windowInsets.getSystemWindowInsetTop();
            this.bottomInset = windowInsets.getSystemWindowInsetBottom();
        }
        layoutWorld();
        return windowInsets;
    }

    @Override // android.view.View
    protected void onSizeChanged(int i, int i2, int i3, int i4) {
        layoutWorld();
    }

    void layoutWorld() {
        if (getWidth() == 0) {
            return;
        }
        this.scale = getWidth() / 1000.0f;
        float height = ((getHeight() - this.topInset) - this.bottomInset) / this.scale;
        this.h = height;
        this.engine.resize(height);
        invalidate();
    }

    void wake() {
        this.awake = true;
        this.lastFrame = 0L;
        invalidate();
        refresh();
    }

    void sleep() {
        this.awake = false;
        this.engine.pause();
        this.lastFrame = 0L;
        this.haptics.stop();
        this.audio.stop();
        save();
        refresh();
    }

    void close() {
        this.audio.close();
        this.haptics.stop();
    }

    void refresh() {
        ((MainActivity) getContext()).refreshRate(this.scene == Scene.GAME && this.engine.state == GameEngine.State.PLAYING);
    }

    void save() {
        if (this.scene == Scene.GAME) {
            if (this.engine.zen) {
                this.bestZen = Math.max(this.bestZen, this.engine.score);
            } else {
                this.bestChallenge = Math.max(this.bestChallenge, this.engine.score);
            }
        }
        this.prefs.edit().putInt("best_zen", this.bestZen).putInt("best_challenge", this.bestChallenge).putInt("zen_level", this.zenLevel).putBoolean("haptic", this.haptic).putBoolean("sound", this.sound).putFloat("paddle_lift", this.engine.paddleLift).putInt("appearance", this.appearanceMode).apply();
        ((MainActivity)getContext()).checkpointPortal();
    }

    boolean back() {
        if (this.scene == Scene.HELP) {
            this.scene = Scene.HOME;
            invalidate();
            return true;
        }
        if (this.scene != Scene.GAME) {
            return false;
        }
        if (this.engine.portalActive) {
            ((MainActivity)getContext()).cancelStarPortal();
        } else if (this.engine.state == GameEngine.State.CLEARED || this.engine.state == GameEngine.State.OVER) {
            action("home");
        } else if (this.engine.state == GameEngine.State.PAUSED) {
            this.engine.resume();
        } else {
            this.engine.pause();
        }
        refresh();
        invalidate();
        return true;
    }

    void start(boolean z) {
        ((MainActivity)getContext()).clearPortalCheckpoint();
        this.scene = Scene.GAME;
        this.engine.start(z ? this.zenLevel : 1, z, 0);
        this.particles.clear();
        this.popups.clear();
        this.specials.clear();
        this.toast = "";
        this.toastLife = 0.0f;
        this.accumulator = 0.0f;
        this.lastFrame = 0L;
        refresh();
        invalidate();
        announceForAccessibility("轻点发球，左右滑动控制挡板");
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        this.frames++;
        long jNanoTime = System.nanoTime();
        long j = this.lastFrame;
        float fMin = j == 0 ? 0.0f : Math.min(0.05f, (jNanoTime - j) / 1.0E9f);
        this.lastFrame = jNanoTime;
        if (this.awake) {
            this.clock += fMin;
            if (this.scene == Scene.GAME && this.engine.state != GameEngine.State.PAUSED) {
                this.accumulator += fMin;
                while (this.accumulator >= 0.004166667f) {
                    this.engine.tick(0.004166667f);
                    this.accumulator -= 0.004166667f;
                }
                effects(fMin);
                consume();
            }
            if (this.scene == Scene.GAME && this.engine.portalPending) {
                this.engine.portalPending = false;
                this.accumulator = 0;
                this.paddlePointer = -1;
                this.pressed.clear();
                post(() -> ((MainActivity) getContext()).openStarPortal());
            }
        }
        canvas.drawColor(this.appearance.background);
        canvas.save();
        canvas.translate(0.0f, this.topInset);
        float f = this.scale;
        canvas.scale(f, f);
        this.buttons.clear();
        this.labels.clear();
        if (this.scene == Scene.HOME) {
            home(canvas);
        } else if (this.scene == Scene.HELP) {
            help(canvas);
        } else {
            game(canvas);
        }
        canvas.restore();
        if (this.awake) {
            if (this.scene == Scene.HOME || (this.scene == Scene.GAME && this.engine.state == GameEngine.State.READY)) {
                postInvalidateDelayed(33L);
                return;
            }
            if (this.scene != Scene.GAME || this.engine.state == GameEngine.State.PAUSED) {
                return;
            }
            if (this.engine.state != GameEngine.State.PLAYING && this.particles.isEmpty() && this.popups.isEmpty() && this.specials.isEmpty()) {
                return;
            }
            postInvalidateOnAnimation();
        }
    }

    void color(int i) {
        this.paint.reset();
        this.paint.setAntiAlias(true);
        this.paint.setColor(this.appearance.color(i));
    }

    void box(Canvas canvas, float f, float f2, float f3, float f4, float f5, int i) {
        if (i == -1) {
            i = this.appearance.panel;
        }
        color(i);
        this.rect.set(f, f2, f3 + f, f4 + f2);
        canvas.drawRoundRect(this.rect, f5, f5, this.paint);
    }

    void outline(Canvas canvas, float f, float f2, float f3, float f4, float f5, int i, float f6) {
        color(i);
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(f6);
        this.rect.set(f, f2, f3 + f, f4 + f2);
        canvas.drawRoundRect(this.rect, f5, f5, this.paint);
    }

    void text(Canvas canvas, String str, float f, float f2, float f3, int i, boolean z) {
        color(i);
        this.paint.setStyle(Paint.Style.FILL);
        this.paint.setTextAlign(Paint.Align.LEFT);
        this.paint.setTypeface(z ? this.bold : this.normal);
        this.paint.setTextSize(f3);
        canvas.drawText(str, f, f2, this.paint);
    }

    void center(Canvas canvas, String str, float f, float f2, float f3, int i, boolean z) {
        color(i);
        this.paint.setTypeface(z ? this.bold : this.normal);
        this.paint.setTextSize(f3);
        this.paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(str, f, f2, this.paint);
    }

    void line(Canvas canvas, float f, float f2, float f3, float f4, int i, float f5) {
        color(i);
        this.paint.setStrokeWidth(f5);
        this.paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(f, f2, f3, f4, this.paint);
    }

    void circle(Canvas canvas, float f, float f2, float f3, int i) {
        color(i);
        canvas.drawCircle(f, f2, f3, this.paint);
    }

    void register(String str, String str2, float f, float f2, float f3, float f4) {
        this.buttons.put(str, new RectF(f, f2, f3 + f, f4 + f2));
        this.labels.put(str, str2);
    }

    void button(Canvas canvas, String str, String str2, float f, float f2, float f3, float f4, boolean z) {
        int i = z ? GREEN : -1315613;
        if (this.pressed.containsValue(str)) {
            i = z ? -14920891 : -2300970;
        }
        box(canvas, f, f2, f3, f4, 36.0f, i);
        center(canvas, str2, f + (f3 / 2.0f), (f4 / 2.0f) + f2 + 16.0f, z ? 46.0f : 37.0f, z ? this.appearance.primaryText : INK, true);
        register(str, str2, f, f2, f3, f4);
    }

    void mark(Canvas canvas, float f, float f2) {
        box(canvas, f, f2, 31.0f, 22.0f, 7.0f, GREEN);
        box(canvas, f + 38.0f, f2, 31.0f, 22.0f, 7.0f, -7619681);
        box(canvas, f, f2 + 30.0f, 31.0f, 22.0f, 7.0f, -7619681);
        box(canvas, f + 42.0f, f2 + 34.0f, 18.0f, 18.0f, 5.0f, -1789817);
    }

    void home(Canvas canvas) {
        GameView gameView = this;
        gameView.mark(canvas, 65.0f, 68.0f);
        gameView.text(canvas, gameView.getContext().getString(R.string.app_name), 157.0f, 116.0f, 56.0f, INK, true);
        gameView.text(canvas, "BRICK BREAKER", 65.0f, 194.0f, 25.0f, MUTED, true);
        gameView.button(canvas, "settings", "设置", 773.0f, 64.0f, 167.0f, 94.0f, false);
        gameView.text(canvas, "打砖块", 61.0f, 404.0f, 128.0f, INK, true);
        gameView.text(canvas, "反弹 · 连击 · 清场", 66.0f, 500.0f, 34.0f, MUTED, false);
        float f = gameView.h - 510.0f;
        float f2 = f - 590.0f;
        gameView.box(canvas, 58.0f, 590.0f, 884.0f, f2, 52.0f, -1183515);
        gameView.outline(canvas, 58.0f, 590.0f, 884.0f, f2, 52.0f, -1906982, 2.0f);
        Iterator<GameEngine.Orb> it = gameView.preview.orbs.iterator();
        float fMax = 1000.0f;
        while (it.hasNext()) {
            fMax = Math.max(fMax, it.next().y + 45.0f);
        }
        float fMin = Math.min(0.81f, (f2 - 230.0f) / (fMax - 250.0f));
        float f3 = 500.0f - (fMin * 500.0f);
        for (GameEngine.Brick brick : gameView.preview.bricks) {
            gameView.brick(canvas, f3 + (brick.x * fMin), ((brick.y - 250.0f) * fMin) + 642.0f, brick.w * fMin, brick.h * fMin, brick.shape, PALETTES[gameView.preview.palette][brick.color], brick.hp, brick.maxHp, 0.0f);
        }
        for (GameEngine.Orb orb : gameView.preview.orbs) {
            gameView.orb(canvas, f3 + (orb.x * fMin), ((orb.y - 250.0f) * fMin) + 642.0f, orb.type, fMin);
        }
        float fSin = (((float) Math.sin(((double) gameView.clock) * 0.75d)) * 155.0f) + 500.0f;
        float fSin2 = (((float) Math.sin(((double) gameView.clock) * 1.15d)) * 230.0f) + 500.0f;
        float fSin3 = 930.0f + (((float) ((Math.sin(((double) gameView.clock) * 1.65d) * 0.5d) + 0.5d)) * (f2 - 590.0f));
        int i = 6;
        while (i > 0) {
            float f4 = i;
            gameView.circle(canvas, fSin2 - (i * 8), fSin3 + (i * 12), 13.0f - (0.9f * f4), alpha(GREEN, (int) ((1.0f - (f4 / 7.0f)) * 55.0f)));
            i--;
            gameView = this;
        }
        circle(canvas, fSin2, fSin3, 16.0f, -1);
        circle(canvas, fSin2, fSin3, 9.0f, GREEN);
        box(canvas, fSin - 145.0f, f - 145.0f, 290.0f, 23.0f, 12.0f, GREEN);
        center(canvas, "左右滑动 · 轻点发球", 500.0f, f - 64.0f, 29.0f, -8217725, false);
        button(canvas, "zen", this.zenLevel > 1 ? "无限模式 · 第 " + this.zenLevel + " 关" : "无限模式", 60.0f, this.h - 455.0f, 880.0f, 145.0f, true);
        button(canvas, "challenge", "挑战模式", 60.0f, this.h - 285.0f, 425.0f, 112.0f, false);
        button(canvas, "help", "怎么玩", 515.0f, this.h - 285.0f, 425.0f, 112.0f, false);
        center(canvas, "无限续球  ·  完全离线  ·  没有广告", 500.0f, this.h - 91.0f, 28.0f, MUTED, false);
        if (this.bestZen > 0) {
            center(canvas, "无限模式最佳  " + this.bestZen, 500.0f, this.h - 40.0f, 25.0f, -6509409, false);
        }
    }

    void brick(Canvas canvas, float f, float f2, float f3, float f4, GameEngine.Shape shape, int i, int i2, int i3, float f5) {
        this.brickPath.reset();
        this.rect.set(0.0f, 0.0f, f3, f4);
        int i4 = AnonymousClass2.$SwitchMap$dev$brickzen$app$GameEngine$Shape[shape.ordinal()];
        if (i4 == 1) {
            float fMin = Math.min(13.0f, Math.min(f3, f4) * 0.26f);
            this.brickPath.addRoundRect(this.rect, fMin, fMin, Path.Direction.CW);
        } else if (i4 == 2) {
            this.brickPath.addRoundRect(this.rect, Math.min(f3, f4) / 2.0f, Math.min(f3, f4) / 2.0f, Path.Direction.CW);
        } else if (i4 == 3) {
            this.brickPath.addOval(this.rect, Path.Direction.CW);
        } else if (i4 == 4) {
            float f6 = f3 / 2.0f;
            this.brickPath.moveTo(f6, 0.0f);
            float f7 = f4 / 2.0f;
            this.brickPath.lineTo(f3, f7);
            this.brickPath.lineTo(f6, f4);
            this.brickPath.lineTo(0.0f, f7);
            this.brickPath.close();
        } else if (i4 == 5) {
            float f8 = 0.2f * f3;
            this.brickPath.moveTo(f8, 0.0f);
            float f9 = 0.8f * f3;
            this.brickPath.lineTo(f9, 0.0f);
            float f10 = f4 / 2.0f;
            this.brickPath.lineTo(f3, f10);
            this.brickPath.lineTo(f9, f4);
            this.brickPath.lineTo(f8, f4);
            this.brickPath.lineTo(0.0f, f10);
            this.brickPath.close();
        }
        canvas.save();
        canvas.translate(f, f2);
        canvas.save();
        canvas.translate(0.0f, 4.0f);
        color(alpha(INK, 14));
        canvas.drawPath(this.brickPath, this.paint);
        canvas.restore();
        Shader shader = this.brickShaders.get(Integer.valueOf(i));
        if (shader == null) {
            LinearGradient linearGradient = new LinearGradient(0.0f, 0.0f, 0.0f, 90.0f, blend(i, -1, 0.18f), i, Shader.TileMode.CLAMP);
            this.brickShaders.put(Integer.valueOf(i), linearGradient);
            shader = linearGradient;
        }
        color(i);
        this.paint.setShader(shader);
        canvas.drawPath(this.brickPath, this.paint);
        this.paint.setShader(null);
        if (f5 > 0.0f) {
            color(alpha(-1, (int) (Math.min(1.0f, f5) * 110.0f)));
            canvas.drawPath(this.brickPath, this.paint);
        }
        canvas.save();
        canvas.clipPath(this.brickPath);
        line(canvas, 5.0f, 8.0f, f3 - 5.0f, 8.0f, 1224736767, 2.0f);
        if (i2 < i3) {
            float f11 = f3 * 0.55f;
            float f12 = f4 * 0.37f;
            line(canvas, f3 * 0.4f, 0.0f, f11, f12, 1880637238, 2.0f);
            line(canvas, f11, f12, f3 * 0.42f, 0.62f * f4, 1880637238, 2.0f);
        }
        canvas.restore();
        if (i3 > 1) {
            for (int i5 = 0; i5 < i2; i5++) {
                circle(canvas, (f3 / 2.0f) + ((i5 - ((i2 - 1) / 2.0f)) * 12.0f), f4 / 2.0f, 3.2f, -352321537);
            }
        }
        canvas.restore();
    }

    void mysteryBrick(Canvas canvas, GameEngine.Brick brick) {
        int violet=appearance.dark?0xff8271b8:0xffc9b9e9;
        brick(canvas,brick.x,brick.y,brick.w,brick.h,brick.shape,violet,1,1,
                1-(engine.time-brick.hitAt)/0.14f);
        float cx=brick.x+brick.w/2,cy=brick.y+brick.h/2;
        float radius=Math.min(brick.w*.35f,brick.h*.36f);
        canvas.save();canvas.translate(cx,cy);canvas.rotate(-22);
        color(appearance.dark?0xffe6dbff:0xff776194);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
        rect.set(-radius*1.7f,-radius*.62f,radius*1.7f,radius*.62f);
        canvas.drawOval(rect,paint);canvas.restore();
        circle(canvas,cx,cy,radius*.69f,appearance.dark?0xff8271b8:0xffc9b9e9);
        center(canvas,"?",cx,cy+radius*.43f,radius*1.5f,appearance.dark?0xfffcf5ff:0xff65507c,true);
        float angle=clock*.7f;
        circle(canvas,cx+(float)Math.cos(angle)*radius*1.65f,cy+(float)Math.sin(angle)*radius*.55f,2.5f,0xfffaf4ff);
    }

    void orb(Canvas canvas, float f, float f2, GameEngine.Surprise surprise, float f3) {
        int i = SPECIAL_COLORS[surprise.ordinal()];
        float fSin = (float) Math.sin((this.clock * 3.0f) + surprise.ordinal());
        canvas.save();
        canvas.translate(f, f2);
        canvas.scale(f3, f3);
        circle(canvas, 0.0f, 0.0f, (fSin * 3.0f) + 46.0f, alpha(i, 16));
        circle(canvas, 0.0f, 0.0f, 38.0f, alpha(i, 30));
        circle(canvas, 0.0f, 3.0f, 31.0f, alpha(INK, 18));
        circle(canvas, 0.0f, 0.0f, 31.0f, i);
        color(-1426063361);
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(2.0f);
        canvas.drawCircle(0.0f, 0.0f, 27.0f, this.paint);
        double dOrdinal = (this.clock * 1.1f) + surprise.ordinal();
        circle(canvas, ((float) Math.cos(dOrdinal)) * 43.0f, ((float) Math.sin(dOrdinal)) * 43.0f, 4.0f, i);
        circle(canvas, (-((float) Math.cos(dOrdinal))) * 43.0f, (-((float) Math.sin(dOrdinal))) * 43.0f, 3.0f, -131851);
        int i2 = AnonymousClass2.$SwitchMap$dev$brickzen$app$GameEngine$Surprise[surprise.ordinal()];
        if (i2 == 1) {
            this.effectPath.reset();
            this.effectPath.moveTo(5.0f, -20.0f);
            this.effectPath.lineTo(-13.0f, 3.0f);
            this.effectPath.lineTo(-1.0f, 3.0f);
            this.effectPath.lineTo(-5.0f, 20.0f);
            this.effectPath.lineTo(14.0f, -5.0f);
            this.effectPath.lineTo(2.0f, -5.0f);
            this.effectPath.close();
            color(-1);
            canvas.drawPath(this.effectPath, this.paint);
        } else if (i2 == 2) {
            for (int i3 = 0; i3 < 8; i3++) {
                double d = (i3 * 3.1415927f) / 4.0f;
                line(canvas, ((float) Math.cos(d)) * 10.0f, 10.0f * ((float) Math.sin(d)), ((float) Math.cos(d)) * 20.0f, ((float) Math.sin(d)) * 20.0f, -1, 3.0f);
            }
            circle(canvas, 0.0f, 0.0f, 7.0f, -1);
        } else if (i2 == 3) {
            line(canvas, -17.0f, -18.0f, 8.0f, 7.0f, -1, 5.0f);
            line(canvas, -7.0f, -21.0f, 14.0f, 0.0f, -1275068417, 3.0f);
            circle(canvas, 7.0f, 8.0f, 10.0f, -1);
        } else if (i2 == 4) {
            this.effectPath.reset();
            this.effectPath.moveTo(0.0f, -20.0f);
            this.effectPath.lineTo(20.0f, 15.0f);
            this.effectPath.lineTo(-20.0f, 15.0f);
            this.effectPath.close();
            color(-1);
            canvas.drawPath(this.effectPath, this.paint);
            line(canvas, -8.0f, 8.0f, 8.0f, 8.0f, -6259755, 3.0f);
            line(canvas, 0.0f, -8.0f, 0.0f, 8.0f, -1790359, 3.0f);
        }
        canvas.restore();
    }

    /* JADX INFO: renamed from: dev.brickzen.app.GameView$2, reason: invalid class name */
    static /* synthetic */ class AnonymousClass2 {
        static final /* synthetic */ int[] $SwitchMap$dev$brickzen$app$GameEngine$Shape;
        static final /* synthetic */ int[] $SwitchMap$dev$brickzen$app$GameEngine$Surprise;

        static {
            int[] iArr = new int[GameEngine.Surprise.values().length];
            $SwitchMap$dev$brickzen$app$GameEngine$Surprise = iArr;
            try {
                iArr[GameEngine.Surprise.LIGHTNING.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Surprise[GameEngine.Surprise.NOVA.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Surprise[GameEngine.Surprise.METEOR.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Surprise[GameEngine.Surprise.PRISM.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            int[] iArr2 = new int[GameEngine.Shape.values().length];
            $SwitchMap$dev$brickzen$app$GameEngine$Shape = iArr2;
            try {
                iArr2[GameEngine.Shape.ROUND.ordinal()] = 1;
            } catch (NoSuchFieldError unused5) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Shape[GameEngine.Shape.CAPSULE.ordinal()] = 2;
            } catch (NoSuchFieldError unused6) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Shape[GameEngine.Shape.DISC.ordinal()] = 3;
            } catch (NoSuchFieldError unused7) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Shape[GameEngine.Shape.DIAMOND.ordinal()] = 4;
            } catch (NoSuchFieldError unused8) {
            }
            try {
                $SwitchMap$dev$brickzen$app$GameEngine$Shape[GameEngine.Shape.HEXAGON.ordinal()] = 5;
            } catch (NoSuchFieldError unused9) {
            }
        }
    }

    void ring(Canvas canvas, float f, float f2, float f3, int i, float f4) {
        color(i);
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(f4);
        canvas.drawCircle(f, f2, Math.max(0.0f, f3), this.paint);
    }

    void drawSpecials(Canvas canvas) {
        GameEngine.Strike strike;
        float f;
        int i;
        float f2;
        GameView gameView = this;
        Canvas canvas2 = canvas;
        canvas2.save();
        canvas2.clipRect(26.0f, 208.0f, 974.0f, gameView.engine.paddleY() + 50.0f);
        for (SpecialVisual specialVisual : gameView.specials) {
            GameEngine.Burst burst = specialVisual.burst;
            float f3 = specialVisual.age;
            float f4 = 1.0f;
            float f5 = 0.0f;
            float fMax = Math.max(0.0f, 1.0f - (f3 / burst.duration));
            int i2 = SPECIAL_COLORS[burst.type.ordinal()];
            if (burst.type == GameEngine.Surprise.LIGHTNING) {
                float f6 = burst.x;
                float f7 = burst.y;
                int i3 = 0;
                while (i3 < burst.strikes.size()) {
                    GameEngine.Strike strike2 = burst.strikes.get(i3);
                    float f8 = f3 - strike2.delay;
                    if (i3 % 3 == 0) {
                        f6 = burst.x;
                        f7 = burst.y;
                    }
                    if (f8 < f5 || f8 >= 0.65f) {
                        strike = strike2;
                        f = f5;
                        i = i3;
                    } else {
                        float f9 = f4 - (f8 / 0.65f);
                        gameView.effectPath.reset();
                        gameView.effectPath.moveTo(f6, f7);
                        float f10 = strike2.x - f6;
                        float f11 = strike2.y - f7;
                        f = f5;
                        i = i3;
                        float fMax2 = Math.max(1.0f, (float) Math.hypot(f10, f11));
                        int i4 = 1;
                        while (i4 <= 8) {
                            if (i4 == 8) {
                                f2 = f;
                            } else {
                                f2 = (i4 % 2 == 0 ? 1 : -1) * ((((i + i4) % 3) * 7) + 10);
                            }
                            float f12 = i4;
                            gameView.effectPath.lineTo(f6 + ((f10 * f12) / 8.0f) + ((f11 / fMax2) * f2), (((f12 * f11) / 8.0f) + f7) - ((f10 / fMax2) * f2));
                            i4++;
                            f6 = f6;
                        }
                        gameView.color(alpha(i2, (int) (65.0f * f9)));
                        gameView.paint.setStyle(Paint.Style.STROKE);
                        gameView.paint.setStrokeWidth(15.0f);
                        gameView.paint.setStrokeJoin(Paint.Join.ROUND);
                        canvas2.drawPath(gameView.effectPath, gameView.paint);
                        gameView.color(alpha(i2, (int) (f9 * 220.0f)));
                        gameView.paint.setStyle(Paint.Style.STROKE);
                        gameView.paint.setStrokeWidth(7.0f);
                        canvas2.drawPath(gameView.effectPath, gameView.paint);
                        gameView.color(alpha(-1, (int) (245.0f * f9)));
                        gameView.paint.setStyle(Paint.Style.STROKE);
                        gameView.paint.setStrokeWidth(3.5f);
                        canvas2.drawPath(gameView.effectPath, gameView.paint);
                        strike = strike2;
                        gameView.circle(canvas2, strike2.x, strike2.y, 24.0f, alpha(i2, (int) (f9 * 90.0f)));
                        ring(canvas, strike.x, strike.y, (f8 * 70.0f) + 12.0f, alpha(i2, (int) (140.0f * f9)), 2.5f);
                    }
                    f6 = strike.x;
                    f7 = strike.y;
                    i3 = i + 1;
                    gameView = this;
                    canvas2 = canvas;
                    f5 = f;
                    f4 = 1.0f;
                }
            } else {
                float f13 = 0.0f;
                if (burst.type == GameEngine.Surprise.NOVA) {
                    float fMin = Math.min(1.0f, f3 / 0.5f) * 340.0f;
                    circle(canvas, burst.x, burst.y, fMin, alpha(i2, (int) (13.0f * fMax)));
                    ring(canvas, burst.x, burst.y, fMin, alpha(i2, (int) (200.0f * fMax)), 8.0f);
                    ring(canvas, burst.x, burst.y, fMin * 0.8f, alpha(-1, (int) (230.0f * fMax)), 3.0f);
                    ring(canvas, burst.x, burst.y, fMin * 0.56f, alpha(i2, (int) (fMax * 90.0f)), 5.0f);
                    for (int i5 = 0; i5 < 16; i5++) {
                        double d = ((i5 * 3.1415927f) / 8.0f) + 0.13f;
                        line(canvas, burst.x + (((float) Math.cos(d)) * fMin * 0.86f), burst.y + (((float) Math.sin(d)) * fMin * 0.86f), burst.x + (((float) Math.cos(d)) * fMin * 1.08f), burst.y + (((float) Math.sin(d)) * fMin * 1.08f), alpha(i2, (int) (170.0f * fMax)), 3.0f);
                    }
                } else {
                    float f14 = 130.0f;
                    if (burst.type == GameEngine.Surprise.METEOR) {
                        for (GameEngine.Strike strike3 : burst.strikes) {
                            float f15 = f3 - strike3.delay;
                            if (f15 < -0.32f || f15 >= f13) {
                                f13 = 0.0f;
                                if (f15 >= 0.0f && f15 < 0.4f) {
                                    float f16 = 1.0f - (f15 / 0.4f);
                                    ring(canvas, strike3.x, strike3.y, (f15 * 130.0f) + 18.0f, alpha(i2, (int) (210.0f * f16)), 5.0f);
                                    circle(canvas, strike3.x, strike3.y, f16 * 24.0f, alpha(-12680, (int) (f16 * 150.0f)));
                                }
                            } else {
                                float f17 = (f15 / 0.32f) + 1.0f;
                                float f18 = strike3.x - 190.0f;
                                float f19 = f18 + ((strike3.x - f18) * f17);
                                float f20 = ((strike3.y - 211.0f) * f17) + 211.0f;
                                int i6 = 10;
                                while (i6 >= 0) {
                                    float fMax3 = Math.max(f13, f17 - (i6 * 0.018f));
                                    circle(canvas, ((strike3.x - f18) * fMax3) + f18, 211.0f + ((strike3.y - 211.0f) * fMax3), Math.max(2, 13 - i6), alpha(i6 < 4 ? -12680 : i2, 220 - (i6 * 17)));
                                    i6--;
                                    f13 = 0.0f;
                                }
                                circle(canvas, f19, f20, 8.0f, -1);
                                f13 = 0.0f;
                            }
                        }
                    } else {
                        float f21 = (f3 * 220.0f) + 45.0f;
                        int i7 = 0;
                        while (i7 < 12) {
                            int i8 = PALETTES[3][i7 % 6];
                            double d2 = ((i7 * 3.1415927f) / 6.0f) + (0.6f * f3);
                            float f22 = fMax;
                            line(canvas, burst.x + (((float) Math.cos(d2)) * f21 * 0.43f), burst.y + (((float) Math.sin(d2)) * f21 * 0.43f), burst.x + (((float) Math.cos(d2)) * f21), burst.y + (((float) Math.sin(d2)) * f21), alpha(i8, (int) (f22 * 220.0f)), 5.0f);
                            circle(canvas, burst.x + (((float) Math.cos(d2)) * f21), burst.y + (((float) Math.sin(d2)) * f21), f22 * 6.0f, alpha(i8, (int) (240.0f * f22)));
                            i7++;
                            f14 = f14;
                            fMax = f22;
                        }
                        ring(canvas, burst.x, burst.y, f21 * 0.67f, alpha(i2, (int) (fMax * f14)), 3.0f);
                    }
                }
            }
            gameView = this;
            canvas2 = canvas;
        }
        canvas.restore();
    }

    /* JADX WARN: Code duplicated, block: B:120:0x0524  */
    /* JADX WARN: Code duplicated, block: B:121:0x0540  */
    /* JADX WARN: Code duplicated, block: B:123:0x0548  */
    /* JADX WARN: Code duplicated, block: B:124:0x054b  */
    /* JADX WARN: Code duplicated, block: B:127:0x0553  */
    /* JADX WARN: Code duplicated, block: B:128:0x0556  */
    /* JADX WARN: Code duplicated, block: B:131:0x055d  */
    /* JADX WARN: Code duplicated, block: B:134:0x056a  */
    /* JADX WARN: Code duplicated, block: B:135:0x056d  */
    /* JADX WARN: Code duplicated, block: B:142:0x057f  */
    /* JADX WARN: Code duplicated, block: B:145:0x0586  */
    /* JADX WARN: Code duplicated, block: B:146:0x0588  */
    /* JADX WARN: Code duplicated, block: B:149:0x059b  */
    /* JADX WARN: Code duplicated, block: B:153:0x05a5  */
    /* JADX WARN: Code duplicated, block: B:156:0x05b8  */
    /* JADX WARN: Code duplicated, block: B:158:0x05d6  */
    /* JADX WARN: Code duplicated, block: B:160:0x05de  */
    /* JADX WARN: Code duplicated, block: B:161:0x05e1  */
    /* JADX WARN: Code duplicated, block: B:162:0x05e4  */
    /* JADX WARN: Code duplicated, block: B:165:0x0607  */
    /* JADX WARN: Code duplicated, block: B:169:0x061a  */
    /* JADX WARN: Code duplicated, block: B:178:0x064e  */
    /* JADX WARN: Code duplicated, block: B:209:? A[RETURN, SYNTHETIC] */
    void game(Canvas canvas) {
        int i;
        String string;
        StringBuilder sb;
        float f;
        String str;
        float f2;
        float f3;
        int i2;
        int i3;
        boolean z;
        boolean z2;
        String str2;
        String str3;
        int i4;
        int i5;
        text(canvas, this.engine.zen ? "无限模式" : "挑战模式", 60.0f, 61.0f, 31.0f, MUTED, true);
        text(canvas, String.format(Locale.ROOT, "%,d", Integer.valueOf(this.engine.score)), 58.0f, 151.0f, this.engine.score > 99999 ? 61.0f : 78.0f, INK, true);
        float fMeasureText = this.paint.measureText(String.format(Locale.ROOT, "%,d", Integer.valueOf(this.engine.score))) + 58.0f;
        if (this.engine.combo >= 3) {
            float fMax = Math.max(340.0f, fMeasureText + 24.0f);
            float fMin = Math.min(180.0f, 599.0f - fMax);
            if (fMin > 140.0f) {
                box(canvas, fMax, 93.0f, fMin, 57.0f, 22.0f, -1839654);
                center(canvas, this.engine.combo + " 连击", fMax + (fMin / 2.0f), 132.0f, 31.0f, GREEN, true);
            }
        }
        GameView gameView = this;
        gameView.box(canvas, 623.0f, 67.0f, 184.0f, 73.0f, 27.0f, -1446430);
        gameView.center(canvas, String.format(Locale.ROOT, "第 %02d 关", Integer.valueOf(gameView.engine.level)), 715.0f, 115.0f, 33.0f, INK, true);
        gameView.box(canvas, 839.0f, 58.0f, 106.0f, 103.0f, 35.0f, -1446430);
        gameView.line(canvas, 881.0f, 94.0f, 881.0f, 125.0f, GREEN, 7.0f);
        gameView.line(canvas, 902.0f, 94.0f, 902.0f, 125.0f, GREEN, 7.0f);
        gameView.register("pause", "暂停", 827.0f, 45.0f, 129.0f, 129.0f);
        gameView.box(canvas, 60.0f, 185.0f, 880.0f, 7.0f, 4.0f, -1578530);
        gameView.box(canvas, 60.0f, 185.0f, (gameView.engine.destroyed / (float)Math.max(1,gameView.engine.total)) * 880.0f, 7.0f, 4.0f, -7947356);
        if (!gameView.engine.zen) {
            int i6 = 0;
            while (i6 < 3) {
                gameView.circle(canvas, (i6 * 18) + 550, 43.0f, 5.0f, i6 < gameView.engine.lives ? -2319477 : -2301226);
                i6++;
            }
        }
        canvas.save();
        if (gameView.shake > 0.0f) {
            canvas.translate(((float) Math.sin(gameView.clock * 130.0f)) * gameView.shake, ((float) Math.cos(gameView.clock * 103.0f)) * gameView.shake * 0.5f);
        }
        gameView.line(canvas, 26.0f, 218.0f, 26.0f, gameView.engine.paddleY() - 85.0f, -1512481, 2.0f);
        gameView.line(canvas, 974.0f, 218.0f, 974.0f, gameView.engine.paddleY() - 85.0f, -1512481, 2.0f);
        for (GameEngine.Brick brick : gameView.engine.bricks) {
            if (brick.hp > 0) {
                if (brick.mystery) gameView.mysteryBrick(canvas, brick);
                else gameView.brick(canvas, brick.x, brick.y, brick.w, brick.h, brick.shape, PALETTES[gameView.engine.palette][brick.color], brick.hp, brick.maxHp, 1.0f - ((gameView.engine.time - brick.hitAt) / 0.14f));
            }
        }
        for (GameEngine.Orb orb : gameView.engine.orbs) {
            if (orb.active) {
                gameView.orb(canvas, orb.x, orb.y, orb.type, 1.0f);
            }
        }
        Canvas canvas2 = canvas;
        Iterator<GameEngine.Drop> it = gameView.engine.drops.iterator();
        while (it.hasNext()) {
            gameView.drawPower(canvas2, it.next());
        }
        drawSpecials(canvas);
        Iterator<GameEngine.Ball> it2 = gameView.engine.balls.iterator();
        int i7 = 0;
        while (true) {
            boolean zHasNext = it2.hasNext();
            i = GREEN;
            int i8 = 1;
            if (!zHasNext) {
                break;
            }
            GameEngine.Ball next = it2.next();
            if (gameView.engine.prism()) {
                i4 = PALETTES[3][i7 % 6];
                i5 = i7 + 1;
            } else {
                if (gameView.engine.comet()) {
                    i = -2120858;
                }
                i4 = i;
                i5 = i7;
            }
            int i9 = next.trailCount - 1;
            while (i9 >= 0) {
                int length = (((next.trailIndex - i8) - i9) + next.tx.length) % next.tx.length;
                float f4 = i9;
                gameView.circle(canvas2, next.tx[length], next.ty[length], (1.0f - (f4 / next.tx.length)) * 13.0f, alpha(i4, (int) ((1.0f - (f4 / next.tx.length)) * 100.0f)));
                i9--;
                i8 = 1;
                gameView = this;
                canvas2 = canvas;
            }
            circle(canvas, next.x, next.y, 23.0f, alpha(i4, 23));
            circle(canvas, next.x, next.y, 13.0f, i4);
            gameView = this;
            canvas2 = canvas;
            gameView.circle(canvas2, next.x - 3.0f, next.y - 4.0f, 4.0f, -1426063361);
            i7 = i5;
        }
        gameView.box(canvas, gameView.engine.paddleX - (gameView.engine.paddleWidth() / 2.0f), 7.0f + gameView.engine.paddleY(), gameView.engine.paddleWidth(), 21.0f, 11.0f, 338192985);
        gameView.box(canvas, gameView.engine.paddleX - (gameView.engine.paddleWidth() / 2.0f), gameView.engine.paddleY(), gameView.engine.paddleWidth(), 21.0f, 11.0f, GREEN);
        gameView.line(canvas, (gameView.engine.paddleX - (gameView.engine.paddleWidth() / 2.0f)) + 17.0f, gameView.engine.paddleY() + 5.0f, (gameView.engine.paddleX + (gameView.engine.paddleWidth() / 2.0f)) - 17.0f, gameView.engine.paddleY() + 5.0f, 1191182335, 2.0f);
        drawEffects(canvas);
        canvas.restore();
        if (gameView.engine.state == GameEngine.State.READY) {
            float fMax2 = 0.0f;
            for (GameEngine.Brick brick2 : gameView.engine.bricks) {
                if (brick2.hp > 0) {
                    fMax2 = Math.max(fMax2, brick2.y + brick2.h);
                }
            }
            for (GameEngine.Orb orb2 : gameView.engine.orbs) {
                if (orb2.active) {
                    fMax2 = Math.max(fMax2, orb2.y + 42.0f);
                }
            }
            float fPaddleY = gameView.engine.paddleY() - fMax2;
            float fMax3 = fMax2 + Math.max(42.0f, 0.48f * fPaddleY);
            if (fPaddleY > 125.0f) {
                gameView.center(canvas, "轻点发球", 500.0f, fMax3, fPaddleY < 250.0f ? 40.0f : 53.0f, INK, true);
            }
            if (fPaddleY > 150.0f) {
                gameView.center(canvas, gameView.engine.orbs.isEmpty() ? gameView.engine.zen ? "不限续球次数" : "左右滑动屏幕，接住小球。" : "击中奖励球，触发特效", 500.0f, fMax3 + (fPaddleY < 250.0f ? 45 : 64), fPaddleY < 250.0f ? 25.0f : 30.0f, MUTED, false);
            }
            if (fPaddleY > 350.0f) {
                for (int i10 = 0; i10 < 6; i10++) {
                    gameView.circle(canvas, (i10 * 5) + gameView.engine.paddleX + 12.0f, (gameView.engine.paddleY() - 60.0f) - (i10 * 28), 3.0f, alpha(GREEN, 95 - (i10 * 12)));
                }
            }
        }
        String activePower = "";
        if(engine.prism()) activePower = "彩虹分裂 " + (int)Math.ceil(engine.prismUntil-engine.time)+"s";
        else if(engine.comet()) activePower = "穿透 " + (int)Math.ceil(engine.cometUntil-engine.time)+"s";
        else if(engine.slow()) activePower = "慢速 " + (int)Math.ceil(engine.slowUntil-engine.time)+"s";
        else if(engine.time<engine.wideUntil) activePower = "加宽 " + (int)Math.ceil(engine.wideUntil-engine.time)+"s";
        String hint=engine.cleanup()?"剩余 "+engine.remaining()+" 块 · 瞄准辅助":
                engine.state==GameEngine.State.READY?"轻点发球 · 滑动接球":"在挡板下方左右滑动";
        if(toastLife>0)hint=toast;else if(!activePower.isEmpty())hint=activePower;
        text(canvas,hint,60,h-75,toastLife>0?31:27,(!activePower.isEmpty()||toastLife>0)?GREEN:MUTED,toastLife>0);
        boolean ready=engine.charge>=0.999f;
        box(canvas,632,h-141,310,110,38,ready?GREEN:-1446430);
        if(!ready)box(canvas,652,h-52,engine.charge*270,4,2,-7619681);
        String pulseLabel=ready?(engine.cleanup()?"清场脉冲":"释放脉冲"):"脉冲 "+(int)(engine.charge*100)+"%";
        center(canvas,pulseLabel,787,h-75,34,ready?appearance.primaryText:MUTED,true);
        register("pulse",ready?pulseLabel:"脉冲充能中",632,h-141,310,110);
        if(engine.state==GameEngine.State.PAUSED||engine.state==GameEngine.State.CLEARED||engine.state==GameEngine.State.OVER){
            overlay(canvas);
            if(engine.state==GameEngine.State.CLEARED)drawEffects(canvas);
        }
    }

    /* JADX WARN: Code duplicated, block: B:39:0x0104  */
    /* JADX WARN: Code duplicated, block: B:41:0x0108 A[DONT_INVERT] */
    /* JADX WARN: Code duplicated, block: B:42:0x010a  */
    /* JADX WARN: Code duplicated, block: B:43:0x010d  */
    /* JADX WARN: Code duplicated, block: B:45:0x0112  */
    /* JADX WARN: Code duplicated, block: B:47:0x0116 A[DONT_INVERT] */
    /* JADX WARN: Code duplicated, block: B:48:0x0118  */
    /* JADX WARN: Code duplicated, block: B:49:0x011b  */
    /* JADX WARN: Code duplicated, block: B:52:0x0131  */
    /* JADX WARN: Code duplicated, block: B:53:0x0164  */
    /* JADX WARN: Code duplicated, block: B:56:0x017f  */
    /* JADX WARN: Code duplicated, block: B:58:0x01aa A[DONT_INVERT] */
    /* JADX WARN: Code duplicated, block: B:59:0x01ac  */
    /* JADX WARN: Code duplicated, block: B:61:? A[RETURN, SYNTHETIC] */
    void overlay(Canvas canvas) {
        buttons.clear();labels.clear();
        box(canvas,0,0,1000,h,0,-403179792);
        boolean paused=engine.state==GameEngine.State.PAUSED;
        boolean cleared=engine.state==GameEngine.State.CLEARED;
        boolean portal=paused&&engine.portalActive;
        float panelHeight=paused?820:690;
        float y=(h-panelHeight)/2;
        box(canvas,73,y+12,854,panelHeight,57,186138422);
        box(canvas,73,y,854,panelHeight,57,-1);
        center(canvas,portal?"发现神秘航道":paused?"已暂停":cleared?"关卡完成":"游戏结束",500,y+119,portal?60:70,INK,true);
        String detail=portal?"原局已保存 · 进入星隙，带回额外奖励":
                paused?"第 "+engine.level+" 关 · "+engine.score+" 分":
                cleared?"第 "+engine.level+" 关已清空":
                "本轮 "+engine.score+" 分 · 最佳 "+Math.max(bestChallenge,engine.score);
        if(paused&&!portal&&toastLife>0&&toast.startsWith("星隙奖励"))detail=toast;
        center(canvas,detail,500,y+188,32,MUTED,false);
        button(canvas,portal?"portal":paused?"resume":cleared?"next":"again",
                portal?"进入星隙奖励关":paused?"继续游戏":cleared?"下一关":"再玩一次",
                131,y+252,738,131,true);
        if(paused){
            button(canvas,portal?"cancelPortal":"restart",portal?"暂不探索，继续碰界":"重新开始",131,y+409,738,105,false);
            button(canvas,"home","回到首页",131,y+539,738,105,false);
            button(canvas,"settings","游戏设置",131,y+682,738,86,false);
        }else button(canvas,"home","回到首页",131,y+413,738,112,false);
        if(cleared)center(canvas,"+ "+engine.level*100+"  清关奖励",500,y+610,29,-7559794,false);
        else if(!paused)center(canvas,"到达第 "+engine.level+" 关",500,y+610,29,-7559794,false);
    }

    void help(Canvas canvas) {
        text(canvas, "操作说明", 60.0f, 156.0f, 79.0f, INK, true);
        text(canvas, "滑动接球，轻点发球。", 64.0f, 225.0f, 37.0f, MUTED, false);
        String[][] strArr = {new String[]{"01", "滑动屏幕，移动挡板", "手指放在屏幕下方即可，不必按住挡板。"}, new String[]{"02", "击中奖励球，触发特效", "闪电连锁、范围爆破、流星雨、彩虹分裂。"}, new String[]{"03", "接道具，释放脉冲", "击砖蓄能；剩余 5 块时补满清场脉冲。"}};
        float f = 315.0f;
        for (int i = 0; i < 3; i++) {
            String[] strArr2 = strArr[i];
            box(canvas, 60.0f, f, 880.0f, 202.0f, 36.0f, -1249564);
            float f2 = f;
            circle(canvas, 128.0f, f2 + 70.0f, 32.0f, -2365484);
            center(canvas, strArr2[0], 128.0f, f2 + 81.0f, 28.0f, GREEN, true);
            text(canvas, strArr2[1], 185.0f, f2 + 83.0f, 41.0f, INK, true);
            text(canvas, strArr2[2], 94.0f, f2 + 147.0f, 28.0f, MUTED, false);
            f = f2 + 228.0f;
        }
        float f3 = f;
        String[] strArr3 = {"加宽挡板", "三球齐发", "放慢速度", "穿透砖块"};
        for (int i2 = 0; i2 < 4; i2++) {
            float f4 = ((i2 % 2) * 455) + 60;
            float f5 = f3 + ((i2 / 2) * 120);
            box(canvas, f4, f5, 425.0f, 97.0f, 30.0f, -1);
            drawPowerIcon(canvas, f4 + 52.0f, f5 + 48.0f, GameEngine.Power.values()[i2], GREEN);
            text(canvas, strArr3[i2], f4 + 100.0f, f5 + 61.0f, 32.0f, INK, true);
        }
        button(canvas, "settings", "外观、挡板与音效", 60.0f, Math.max(f3 + 270.0f, this.h - 375.0f), 880.0f, 96.0f, false);
        button(canvas, "help_back", "返回首页", 60.0f, this.h - 210.0f, 880.0f, 135.0f, true);
        center(canvas, "完全离线，没有账号，也没有广告。", 500.0f, this.h - 39.0f, 27.0f, MUTED, false);
    }

    void soundButton(Canvas canvas, float f, float f2, String str) {
        box(canvas, f, f2, 110.0f, 108.0f, 37.0f, -1446430);
        Path path = new Path();
        float f3 = f + 31.0f;
        float f4 = f2 + 44.0f;
        path.moveTo(f3, f4);
        float f5 = 44.0f + f;
        path.lineTo(f5, f4);
        float f6 = 59.0f + f;
        path.lineTo(f6, 31.0f + f2);
        path.lineTo(f6, 76.0f + f2);
        float f7 = 63.0f + f2;
        path.lineTo(f5, f7);
        path.lineTo(f3, f7);
        path.close();
        color(GREEN);
        canvas.drawPath(path, this.paint);
        if (this.sound) {
            color(GREEN);
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(5.0f);
            this.rect.set(53.0f + f, 36.0f + f2, 82.0f + f, 74.0f + f2);
            canvas.drawArc(this.rect, -65.0f, 130.0f, false, this.paint);
        } else {
            line(canvas, f + 70.0f, f2 + 41.0f, f + 86.0f, f2 + 67.0f, GREEN, 5.0f);
        }
        register(str, this.sound ? "关闭声音" : "开启声音", f, f2, 110.0f, 108.0f);
    }

    void drawPower(Canvas canvas, GameEngine.Drop drop) {
        int i = drop.power == GameEngine.Power.COMET ? -2517671 : GREEN;
        circle(canvas, drop.x, drop.y + 5.0f, 30.0f, 271084121);
        circle(canvas, drop.x, drop.y, 30.0f, this.appearance.panel);
        drawPowerIcon(canvas, drop.x, drop.y, drop.power, i);
    }

    void drawPowerIcon(Canvas canvas, float f, float f2, GameEngine.Power power, int i) {
        if (power == GameEngine.Power.MULTI) {
            float f3 = f2 + 7.0f;
            circle(canvas, f - 11.0f, f3, 6.0f, i);
            circle(canvas, f + 11.0f, f3, 6.0f, i);
            circle(canvas, f, f2 - 12.0f, 6.0f, i);
            return;
        }
        if (power == GameEngine.Power.WIDE) {
            float f4 = f - 17.0f;
            float f5 = f + 17.0f;
            line(canvas, f4, f2, f5, f2, i, 5.0f);
            float f6 = f - 10.0f;
            float f7 = f2 - 7.0f;
            line(canvas, f4, f2, f6, f7, i, 4.0f);
            float f8 = f2 + 7.0f;
            line(canvas, f4, f2, f6, f8, i, 4.0f);
            float f9 = f + 10.0f;
            line(canvas, f5, f2, f9, f7, i, 4.0f);
            line(canvas, f5, f2, f9, f8, i, 4.0f);
            return;
        }
        if (power == GameEngine.Power.SLOW) {
            color(i);
            this.paint.setStrokeWidth(4.0f);
            this.paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(f, f2, 17.0f, this.paint);
            line(canvas, f, f2, f, f2 - 9.0f, i, 4.0f);
            line(canvas, f, f2, f + 7.0f, f2 + 4.0f, i, 4.0f);
            return;
        }
        Path path = new Path();
        path.moveTo(f + 5.0f, f2 - 22.0f);
        float f10 = f2 + 3.0f;
        path.lineTo(f - 13.0f, f10);
        path.lineTo(f - 1.0f, f10);
        path.lineTo(f - 5.0f, 23.0f + f2);
        float f11 = f2 - 3.0f;
        path.lineTo(14.0f + f, f11);
        path.lineTo(f + 2.0f, f11);
        path.close();
        color(i);
        canvas.drawPath(path, this.paint);
    }

    void effects(float f) {
        this.shake = Math.max(0.0f, this.shake - (32.0f * f));
        this.toastLife = Math.max(0.0f, this.toastLife - f);
        for (int size = this.specials.size() - 1; size >= 0; size--) {
            SpecialVisual specialVisual = this.specials.get(size);
            specialVisual.age += f;
            if (specialVisual.age > specialVisual.burst.duration) {
                this.specials.remove(size);
            }
        }
        for (int size2 = this.particles.size() - 1; size2 >= 0; size2--) {
            Particle particle = this.particles.get(size2);
            particle.life -= f;
            if (particle.life <= 0.0f) {
                this.particles.remove(size2);
            } else {
                particle.x += particle.vx * f;
                particle.y += particle.vy * f;
                particle.vy += 240.0f * f;
                particle.rotation += 100.0f * f;
            }
        }
        for (int size3 = this.popups.size() - 1; size3 >= 0; size3--) {
            Popup popup = this.popups.get(size3);
            popup.life -= f;
            popup.y -= 50.0f * f;
            if (popup.life <= 0.0f) {
                this.popups.remove(size3);
            }
        }
    }

    void drawEffects(Canvas canvas) {
        for (Particle particle : this.particles) {
            canvas.save();
            canvas.rotate(particle.rotation, particle.x, particle.y);
            box(canvas, particle.x - particle.r, particle.y - particle.r, particle.r * 2.0f, particle.r * 2.0f, 2.0f, alpha(particle.color, (int) ((particle.life * 230.0f) / particle.max)));
            canvas.restore();
        }
        for (Popup popup : this.popups) {
            center(canvas, popup.text, popup.x, popup.y, 31.0f, alpha(popup.color, (int) (Math.min(1.0f, popup.life * 2.0f) * 255.0f)), true);
        }
    }

    void burst(float f, float f2, int i, int i2) {
        for (int i3 = 0; i3 < i && this.particles.size() < 480; i3++) {
            Particle particle = new Particle();
            particle.x = f;
            particle.y = f2;
            particle.vx = (this.random.nextFloat() - 0.5f) * 470.0f;
            particle.vy = (this.random.nextFloat() - 0.65f) * 490.0f;
            float fNextFloat = (this.random.nextFloat() * 0.6f) + 0.4f;
            particle.life = fNextFloat;
            particle.max = fNextFloat;
            particle.r = (this.random.nextFloat() * 6.0f) + 3.0f;
            particle.color = i2;
            particle.rotation = this.random.nextFloat() * 90.0f;
            this.particles.add(particle);
        }
    }

    void consume() {
        String str;
        Iterator<GameEngine.Event> it = this.engine.events.iterator();
        int i = 0;
        while (it.hasNext()) {
            if (it.next().kind == GameEngine.Kind.HIT) {
                i++;
            }
        }
        this.audio.beginFrame(i);
        for (GameEngine.Event event : this.engine.events) {
            if (event.kind == GameEngine.Kind.HIT) {
                burst(event.x, event.y, 10, PALETTES[this.engine.palette][event.color % 6]);
                this.shake = Math.max(this.shake, 2.5f);
                if (this.popups.size() < 35) {
                    Popup popup = new Popup();
                    popup.x = event.x;
                    popup.y = event.y;
                    popup.life = 0.75f;
                    popup.text = "+" + event.value;
                    popup.color = GREEN;
                    this.popups.add(popup);
                }
            }
            if (event.kind == GameEngine.Kind.CLEAR) {
                for (int i2 = 0; i2 < 8; i2++) {
                    burst((i2 * 110) + 130, this.h * 0.33f, 15, PALETTES[this.engine.palette][i2 % 6]);
                }
                this.zenLevel = this.engine.zen ? Math.max(this.zenLevel, this.engine.level + 1) : this.zenLevel;
                save();
                refresh();
            }
            if (event.kind == GameEngine.Kind.PULSE) {
                for (int i3 = 0; i3 < 7; i3++) {
                    burst((i3 * 130) + 100, this.engine.paddleY() - 100.0f, 8, PALETTES[this.engine.palette][i3 % 6]);
                }
                this.shake = 9.0f;
                this.toast = "脉冲已释放";
                this.toastLife = 1.6f;
            }
            if (event.kind == GameEngine.Kind.CLEANUP) {
                this.toast = "清场脉冲就绪";
                this.toastLife = 2.2f;
            }
            if (event.kind == GameEngine.Kind.SURPRISE) {
                this.specials.add(new SpecialVisual(event.surprise));
                int iOrdinal = event.surprise.type.ordinal();
                float f = event.x;
                float f2 = event.y;
                int[] iArr = SPECIAL_COLORS;
                burst(f, f2, 55, iArr[iOrdinal]);
                this.shake = Math.max(this.shake, iOrdinal == 1 ? 12.0f : 7.0f);
                this.toast = SPECIAL_NAMES[iOrdinal];
                this.toastLife = 1.45f;
                Popup popup2 = new Popup();
                popup2.x = event.x;
                popup2.y = event.y - 55.0f;
                popup2.life = 0.9f;
                popup2.text = "+50";
                popup2.color = iArr[iOrdinal];
                this.popups.add(popup2);
            }
            if (event.kind == GameEngine.Kind.POWER) {
                if (event.power == GameEngine.Power.WIDE) {
                    str = "挡板加宽";
                } else if (event.power == GameEngine.Power.MULTI) {
                    str = "三球齐发";
                } else {
                    str = event.power == GameEngine.Power.SLOW ? "慢速开启" : "穿透开启";
                }
                this.toast = str;
                this.toastLife = 1.7f;
                burst(event.x, event.y, 20, GREEN);
            }
            if (event.kind == GameEngine.Kind.MISS) {
                refresh();
                save();
            }
            this.audio.play(event);
            if (this.haptic) {
                this.haptics.play(event.kind);
            }
        }
        this.engine.events.clear();
    }

    void action(String str) {
        if (str == null) {
            return;
        }
        if (!str.equals("pulse") && !str.equals("sound") && !str.equals("haptic")) {
            this.paddlePointer = -1;
            this.pressed.clear();
        }
        str.hashCode();
        switch (str) {
            case "haptic":
                this.haptic = !this.haptic;
                save();
                if (this.haptic) {
                    this.haptics.test();
                    break;
                }
                break;
            case "portal":
                ((MainActivity)getContext()).openStarPortal();
                break;
            case "cancelPortal":
                ((MainActivity)getContext()).cancelStarPortal();
                break;
            case "resume":
                if(engine.portalActive){((MainActivity)getContext()).openStarPortal();break;}
                this.engine.resume();
                refresh();
                break;
            case "zen":
                start(true);
                break;
            case "help":
                this.scene = Scene.HELP;
                break;
            case "home":
                if(engine.portalActive)((MainActivity)getContext()).cancelStarPortal();
                save();
                ((MainActivity)getContext()).clearPortalCheckpoint();
                this.scene = Scene.HOME;
                this.particles.clear();
                this.popups.clear();
                this.specials.clear();
                refresh();
                break;
            case "next":
                ((MainActivity)getContext()).clearPortalCheckpoint();
                int i = this.engine.lives;
                GameEngine gameEngine = this.engine;
                gameEngine.start(gameEngine.level + 1, this.engine.zen, this.engine.score);
                if (!this.engine.zen) {
                    this.engine.lives = i;
                }
                this.particles.clear();
                this.popups.clear();
                this.specials.clear();
                refresh();
                break;
            case "again":
                start(false);
                break;
            case "pause":
                this.engine.pause();
                refresh();
                break;
            case "pulse":
                if (!this.engine.pulse()) {
                    this.toast = "脉冲充能中";
                    this.toastLife = 1.2f;
                }
                consume();
                break;
            case "sound":
                boolean z = !this.sound;
                this.sound = z;
                this.audio.enabled = z;
                if (!this.sound) {
                    this.audio.stop();
                }
                save();
                break;
            case "restart":
                save();
                ((MainActivity)getContext()).clearPortalCheckpoint();
                GameEngine gameEngine2 = this.engine;
                gameEngine2.start(gameEngine2.level, this.engine.zen, 0);
                this.particles.clear();
                this.popups.clear();
                this.specials.clear();
                refresh();
                break;
            case "help_back":
                this.scene = Scene.HOME;
                break;
            case "challenge":
                start(false);
                break;
            case "settings":
                this.engine.pause();
                refresh();
                ((MainActivity) getContext()).settings();
                break;
        }
        performClick();
        invalidate();
        sendAccessibilityEvent(2048);
    }

    String hit(float f, float f2) {
        for (Map.Entry<String, RectF> entry : this.buttons.entrySet()) {
            if (entry.getValue().contains(f, f2)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        float x = motionEvent.getX(motionEvent.getActionIndex()) / this.scale;
        float y = (motionEvent.getY(motionEvent.getActionIndex()) - this.topInset) / this.scale;
        int pointerId = motionEvent.getPointerId(motionEvent.getActionIndex());
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked != 0) {
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    int iFindPointerIndex = motionEvent.findPointerIndex(this.paddlePointer);
                    if (iFindPointerIndex >= 0) {
                        this.engine.move(motionEvent.getX(iFindPointerIndex) / this.scale);
                    }
                    return true;
                }
                if (actionMasked == 3) {
                    this.pressed.clear();
                    this.paddlePointer = -1;
                    invalidate();
                    return true;
                }
                if (actionMasked != 5) {
                    if (actionMasked != 6) {
                        return true;
                    }
                }
            }
            String strRemove = this.pressed.remove(Integer.valueOf(pointerId));
            if (strRemove != null && strRemove.equals(hit(x, y))) {
                action(strRemove);
            } else if (pointerId == this.paddlePointer) {
                this.engine.move(x);
                this.paddlePointer = -1;
                if (this.engine.state == GameEngine.State.READY) {
                    this.engine.launch();
                    refresh();
                }
            }
            invalidate();
            return true;
        }
        String strHit = hit(x, y);
        if (strHit != null) {
            this.pressed.put(Integer.valueOf(pointerId), strHit);
        } else if (this.scene == Scene.GAME && ((this.engine.state == GameEngine.State.PLAYING || this.engine.state == GameEngine.State.READY) && this.paddlePointer < 0)) {
            this.paddlePointer = pointerId;
            this.engine.move(x);
        }
        invalidate();
        return true;
    }

    @Override // android.view.View
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override // android.view.View, android.view.KeyEvent.Callback
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(scene==Scene.GAME){
            if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT){engine.move(engine.targetX-95);invalidate();return true;}
            if(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){engine.move(engine.targetX+95);invalidate();return true;}
            if(keyCode==KeyEvent.KEYCODE_SPACE){
                if(engine.portalActive)action("portal");
                else if(engine.state==GameEngine.State.READY)engine.launch();
                else if(engine.state==GameEngine.State.PAUSED)engine.resume();
                else engine.pause();
                refresh();invalidate();return true;
            }
        }
        return super.onKeyDown(keyCode,event);
    }

    @Override // android.view.View
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return new AccessibilityNodeProvider() { // from class: dev.brickzen.app.GameView.1
            ArrayList<String> keys() {
                return new ArrayList<>(GameView.this.buttons.keySet());
            }

            @Override // android.view.accessibility.AccessibilityNodeProvider
            public AccessibilityNodeInfo createAccessibilityNodeInfo(int i) {
                String str;
                RectF rectF;
                AccessibilityNodeInfo accessibilityNodeInfoObtain = AccessibilityNodeInfo.obtain();
                accessibilityNodeInfoObtain.setPackageName(GameView.this.getContext().getPackageName());
                accessibilityNodeInfoObtain.setEnabled(true);
                int i2 = 0;
                if (i == -1) {
                    accessibilityNodeInfoObtain.setSource(GameView.this);
                    accessibilityNodeInfoObtain.setClassName("android.view.ViewGroup");
                    accessibilityNodeInfoObtain.setContentDescription(GameView.this.getContentDescription());
                    accessibilityNodeInfoObtain.setVisibleToUser(GameView.this.isShown());
                    Rect rect = new Rect();
                    GameView.this.getGlobalVisibleRect(rect);
                    accessibilityNodeInfoObtain.setBoundsInScreen(rect);
                    while (i2 < GameView.this.buttons.size()) {
                        i2++;
                        accessibilityNodeInfoObtain.addChild(GameView.this, i2);
                    }
                    return accessibilityNodeInfoObtain;
                }
                ArrayList<String> arrayListKeys = keys();
                if (i < 1 || i > arrayListKeys.size() || (rectF = GameView.this.buttons.get((str = arrayListKeys.get(i - 1)))) == null) {
                    return null;
                }
                accessibilityNodeInfoObtain.setSource(GameView.this, i);
                accessibilityNodeInfoObtain.setParent(GameView.this);
                accessibilityNodeInfoObtain.setClassName("android.widget.Button");
                accessibilityNodeInfoObtain.setText(GameView.this.labels.get(str));
                accessibilityNodeInfoObtain.setContentDescription(GameView.this.labels.get(str));
                accessibilityNodeInfoObtain.setClickable(true);
                accessibilityNodeInfoObtain.setFocusable(true);
                accessibilityNodeInfoObtain.setVisibleToUser(true);
                Rect rect2 = new Rect(Math.round(rectF.left * GameView.this.scale), Math.round((rectF.top * GameView.this.scale) + GameView.this.topInset), Math.round(rectF.right * GameView.this.scale), Math.round((rectF.bottom * GameView.this.scale) + GameView.this.topInset));
                accessibilityNodeInfoObtain.setBoundsInParent(rect2);
                int[] iArr = new int[2];
                GameView.this.getLocationOnScreen(iArr);
                rect2.offset(iArr[0], iArr[1]);
                accessibilityNodeInfoObtain.setBoundsInScreen(rect2);
                accessibilityNodeInfoObtain.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                return accessibilityNodeInfoObtain;
            }

            @Override // android.view.accessibility.AccessibilityNodeProvider
            public boolean performAction(int i, int i2, Bundle bundle) {
                ArrayList<String> arrayListKeys = keys();
                if (i2 != 16 || i <= 0 || i > arrayListKeys.size()) {
                    return false;
                }
                GameView.this.action(arrayListKeys.get(i - 1));
                return true;
            }
        };
    }

    static int alpha(int i, int i2) {
        return (i & 16777215) | (Math.max(0, Math.min(255, i2)) << 24);
    }

    static int blend(int i, int i2, float f) {
        float f2 = 1.0f - f;
        return Color.rgb((int) ((Color.red(i) * f2) + (Color.red(i2) * f)), (int) ((Color.green(i) * f2) + (Color.green(i2) * f)), (int) ((Color.blue(i) * f2) + (Color.blue(i2) * f)));
    }
}
