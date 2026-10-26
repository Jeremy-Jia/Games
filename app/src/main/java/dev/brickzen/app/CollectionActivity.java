package dev.brickzen.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** The shared front door. Both games remain independent and use their existing saves. */
public final class CollectionActivity extends Activity {
    private int background, ink, muted, accent, panel;
    private boolean dark;
    private TextView brickProgress, starProgress;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences old = getSharedPreferences("brickzen", MODE_PRIVATE);
        Appearance colors = Appearance.resolve(old.getInt("appearance",0),
                (getResources().getConfiguration().uiMode & 48)==32);
        dark=colors.dark;background=colors.background;
        ink=dark?0xffe8efea:0xff18362b;muted=dark?0xffa3b2a8:0xff7a887d;
        accent=dark?0xffa7d7be:0xff286a59;panel=dark?0xff23352d:0xffe8eddf;
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if(Build.VERSION.SDK_INT>=29)getWindow().getDecorView().setForceDarkAllowed(false);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(background);scroll.setFillViewport(true);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24),dp(30),dp(24),dp(28));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
        if(Build.VERSION.SDK_INT>=30 && getWindow().getInsetsController()!=null)
            getWindow().getInsetsController().setSystemBarsAppearance(dark?0:24,24);
        if(Build.VERSION.SDK_INT>=30)scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            content.setPadding(dp(24)+safe.left,dp(20)+safe.top,dp(24)+safe.right,dp(24)+safe.bottom);return insets;
        });

        TextView eyebrow=label("A LITTLE ARCADE",10,accent,true);eyebrow.setLetterSpacing(.20f);content.addView(eyebrow);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("碰界",36,ink,false);title.setTypeface(Typeface.create("sans-serif-medium",0));
        heading.addView(title,new LinearLayout.LayoutParams(0,dp(63),1));
        TextView badge=label("游戏集",11,accent,true);badge.setPadding(dp(13),dp(7),dp(13),dp(7));badge.setBackground(shape(panel,50));
        heading.addView(badge);content.addView(heading);
        TextView sub=label("不止一种好玩。",16,muted,false);content.addView(sub);space(content,23);

        LinearLayout brickCard=card(panel);brickCard.setContentDescription("进入碰界打砖块");
        brickCard.addView(new GameArt(this,false),new LinearLayout.LayoutParams(-1,dp(137)));
        LinearLayout brickText=cardText();
        brickText.addView(label("01   碰界",23,ink,true));
        TextView desc=label("几何砖块，安静也上头。",12,muted,false);gap(desc,7);brickText.addView(desc);
        brickProgress=label("",10,accent,true);gap(brickProgress,16);brickText.addView(brickProgress);
        brickCard.addView(brickText);content.addView(brickCard);
        brickCard.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        space(content,16);

        LinearLayout starCard=card(0xff0d202c);starCard.setContentDescription("进入星隙太空生存");
        starCard.addView(new GameArt(this,true),new LinearLayout.LayoutParams(-1,dp(130)));
        LinearLayout starText=cardText();starText.addView(label("02   星隙",23,0xfff0f7f3,true));
        TextView starDesc=label("一根手指，穿过三分钟的星海。",12,0xff9eafb5,false);gap(starDesc,7);starText.addView(starDesc);
        starProgress=label("",10,0xffaaf0d6,true);gap(starProgress,16);starText.addView(starProgress);
        starCard.addView(starText);content.addView(starCard);
        starCard.setOnClickListener(v->startActivity(new Intent(this,io.github.jeremyjia.stardrift.MainActivity.class)));
        space(content,20);

        LinearLayout portal=card(dark?0xff302d3d:0xffece8f3);portal.setPadding(dp(19),dp(17),dp(19),dp(17));
        portal.addView(label("✦  砖块里，藏着另一片星空",13,dark?0xffded0f0:0xff6d587d,true));
        TextView portalDesc=label("击中神秘砖块，开启 30 秒奇遇，再带奖励回来。",11,dark?0xffb6a8c4:0xff877592,false);gap(portalDesc,7);portal.addView(portalDesc);
        portal.setContentDescription("了解神秘砖块和跨游戏奇遇");
        portal.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("偶遇另一种好玩")
                .setMessage("神秘砖会随机藏在砖阵内部，避开最先接触的一排。击中它，会自动切入 30 秒星隙挑战。\n\n三波进攻越来越密，升级自动装配。结束后自动发放奖励、返回碰界并继续打球；存活到结束还会获得宽板和满格能量。\n\n原来的砖块、小球、生命和分数都会保留。随时可主动暂停或提前结束奇遇。")
                .setPositiveButton("去发现吧",null).show());
        content.addView(portal);space(content,24);
        TextView footer=label("完全离线  ·  无广告  ·  为短暂的空闲而做",10,muted,false);footer.setGravity(Gravity.CENTER);content.addView(footer);
        space(content,8);TextView version=label("GAMES / 02",9,muted,false);version.setGravity(Gravity.CENTER);version.setLetterSpacing(.18f);content.addView(version);
    }

    @Override protected void onResume(){super.onResume();
        if(brickProgress==null)return;
        SharedPreferences brick=getSharedPreferences("brickzen",MODE_PRIVATE),star=getSharedPreferences("star_drift",MODE_PRIVATE);
        brickProgress.setText(String.format(Locale.CHINA,"继续第 %d 关    ·    最佳 %,d     ↗",brick.getInt("zen_level",1),brick.getInt("best_zen",0)));
        starProgress.setText(String.format(Locale.CHINA,"单手生存    ·    最佳 %,d     ↗",star.getInt("best_score",0)));
    }
    private LinearLayout card(int color){LinearLayout v=new LinearLayout(this);v.setOrientation(1);v.setBackground(shape(color,24));v.setClipToOutline(true);v.setClickable(true);v.setFocusable(true);return v;}
    private LinearLayout cardText(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);v.setPadding(dp(22),0,dp(22),dp(23));return v;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String s,int size,int color,boolean strong){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",0));return t;}
    private void gap(View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);v.setLayoutParams(p);}
    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

    private final class GameArt extends View {
        private final boolean space;
        private final Paint paint=new Paint(3);private final RectF r=new RectF();private final Path path=new Path();
        GameArt(Context context,boolean space){super(context);this.space=space;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override public void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/340f,getHeight()/137f);
            if(space){
                for(int i=0;i<36;i++){paint.setColor(0x558ebcbf);c.drawCircle((i*67+29)%340,(i*29+19)%135,i%4==0?1.3f:.6f,paint);}
                paint.setColor(0xff183b48);c.drawCircle(251,50,60,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0xff3c5b65);r.set(167,21,335,81);c.save();c.rotate(-25,251,50);c.drawOval(r,paint);c.restore();paint.setStyle(Paint.Style.FILL);
                c.save();c.translate(140,71);c.rotate(30);paint.setColor(0x339ce7ca);c.drawCircle(0,0,33,paint);path.reset();path.moveTo(0,-31);path.lineTo(19,22);path.lineTo(0,12);path.lineTo(-19,22);path.close();paint.setColor(0xffeaf8f2);c.drawPath(path,paint);path.reset();path.moveTo(0,-17);path.lineTo(6,8);path.lineTo(-6,8);path.close();paint.setColor(0xff285361);c.drawPath(path,paint);paint.setColor(0xff9df1cf);path.reset();path.moveTo(-5,21);path.lineTo(0,42);path.lineTo(5,21);path.close();c.drawPath(path,paint);c.restore();
            } else {
                int[] colors={0xff7fba99,0xffa5c6a3,0xffc8d4a4,0xffe5d5b5};
                for(int row=0;row<3;row++)for(int col=0;col<6;col++){float x=43+col*43,y=15+row*24;paint.setColor(colors[(row+col/3)%4]);r.set(x,y,x+36,y+17);c.drawRoundRect(r,5,5,paint);}
                paint.setColor(0xff9a85bb);r.set(172,63,208,80);c.drawRoundRect(r,5,5,paint);paint.setColor(0xfff4eafa);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);c.drawCircle(190,71.5f,5,paint);paint.setStyle(Paint.Style.FILL);
                paint.setColor(dark?0xffa7d7be:0xff3a7867);r.set(114,117,205,123);c.drawRoundRect(r,3,3,paint);paint.setColor(dark?0xfff0f5ef:0xffffffff);c.drawCircle(214,100,5.5f,paint);paint.setColor(0x66ffffff);c.drawCircle(209,107,3,paint);paint.setColor(0x33ffffff);c.drawCircle(205,112,2,paint);
            }
            c.restore();
        }
    }
}
