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

/** Shared front door; each game owns its records and settings. */
public final class CollectionActivity extends Activity {
    private int background, ink, muted, accent, panel;
    private boolean dark;
    private final TextView[] records = new TextView[6];

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
        TextView title=label("SIX GAMES",29,ink,false);title.setTypeface(Typeface.create("sans-serif-medium",0));
        heading.addView(title,new LinearLayout.LayoutParams(0,dp(63),1));
        TextView badge=label("6 款游戏",11,accent,true);badge.setPadding(dp(13),dp(7),dp(13),dp(7));badge.setBackground(shape(panel,50));
        heading.addView(badge);content.addView(heading);
        TextView sub=label("随手开一局，换个好心情。",15,muted,false);content.addView(sub);space(content,23);

        View brick=gameCard(0,"碰界","打砖块 · 连破与奇遇",panel,ink,muted,accent,
                "进入碰界打砖块",MainActivity.class);
        View star=gameCard(1,"星隙","太空生存 · 最终 Boss",0xff102934,0xffedf7f2,0xffafc4c6,0xffa9e9d2,
                "进入星隙太空生存",io.github.jeremyjia.stardrift.MainActivity.class);
        View tennis=gameCard(2,"弧线","3D 乒乓球 · 指尖对决",0xff193948,0xfff1f6ed,0xffb3c6cd,0xffe6d8a5,
                "进入3D乒乓球",dev.brickzen.tabletennis.TableTennisActivity.class);
        View blocks=gameCard(3,"叠界","俄罗斯方块 · 消行与连击",dark?0xff343044:0xffebe5f2,
                dark?0xfff0e9f6:0xff463456,dark?0xffc3b2d1:0xff857092,dark?0xffdac2ee:0xff73558d,
                "进入俄罗斯方块",dev.brickzen.blocks.BlocksActivity.class);
        View hill=gameCard(4,"野径","登山赛车 · 油门与平衡",dark?0xff3b3830:0xffeee7d5,
                dark?0xfff2eddf:0xff4c4430,dark?0xffc5bda6:0xff887957,dark?0xffe6cf8d:0xff8c692e,
                "进入登山赛车",dev.brickzen.hillclimb.HillClimbActivity.class);
        View range=gameCard(5,"准星","狙击打靶 · 屏息与命中",0xff263b39,0xffeef2df,0xffb0c1ad,0xffd5df9b,
                "进入狙击打靶",dev.brickzen.range.RangeActivity.class);
        gameRow(content,brick,star);space(content,13);gameRow(content,tennis,blocks);space(content,13);
        gameRow(content,hill,range);space(content,20);

        LinearLayout portal=card(dark?0xff302d3d:0xffece8f3);portal.setPadding(dp(19),dp(17),dp(19),dp(17));
        portal.addView(label("✦  砖块里，藏着五种奇遇",13,dark?0xffded0f0:0xff6d587d,true));
        TextView portalDesc=label("随机游戏、随机目标、随机难度，达标后自动回来。",11,dark?0xffb6a8c4:0xff877592,false);gap(portalDesc,7);portal.addView(portalDesc);
        portal.setContentDescription("了解神秘砖块和跨游戏奇遇");
        portal.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("偶遇另一种好玩")
                .setMessage("神秘砖随机藏在砖阵内部，避开最先接触的一排。击中后，会随机进入星隙、弧线、叠界、野径或准星。\n\n每次都会随机生成目标和难度，例如击破敌机、连续回球、消行、行驶或命中靶子。目标和难度没有固定档位上限，进入时会显示本次任务。没有倒计时强制结束；达标后自动发奖、返回并继续打球。\n\n原来的砖块、小球、生命和分数都会保留。中断重进仍保留同一个目标。失败或主动结束也会自动返回，提前结束不扣碰界生命。")
                .setPositiveButton("去发现吧",null).show());
        content.addView(portal);space(content,24);
        TextView footer=label("完全离线  ·  无广告  ·  为短暂的空闲而做",10,muted,false);footer.setGravity(Gravity.CENTER);content.addView(footer);
        space(content,8);TextView version=label("GAMES / 06",9,muted,false);version.setGravity(Gravity.CENTER);version.setLetterSpacing(.18f);content.addView(version);
    }

    @Override protected void onResume(){super.onResume();
        if(records[0]==null)return;
        SharedPreferences brick=getSharedPreferences("brickzen",MODE_PRIVATE),star=getSharedPreferences("star_drift",MODE_PRIVATE);
        SharedPreferences tennis=getSharedPreferences("table_tennis",MODE_PRIVATE),blocks=getSharedPreferences("blocks",MODE_PRIVATE);
        SharedPreferences hill=getSharedPreferences("hill_climb",MODE_PRIVATE),range=getSharedPreferences("target_range",MODE_PRIVATE);
        records[0].setText(String.format(Locale.CHINA,"第 %d 关 · 最佳 %,d",brick.getInt("zen_level",1),brick.getInt("best_zen",0)));
        records[1].setText(String.format(Locale.CHINA,"最佳 %,d · 单手生存",star.getInt("best_score",0)));
        records[2].setText(String.format(Locale.CHINA,"胜场 %d · 连续回球 %d",tennis.getInt("wins",0),tennis.getInt("best_streak",0)));
        records[3].setText(String.format(Locale.CHINA,"最佳 %,d · 消行 %d",blocks.getInt("best_score",0),blocks.getInt("best_lines",0)));
        records[4].setText(String.format(Locale.CHINA,"最远 %,d m · 金币 %d",hill.getInt("best_distance",0),hill.getInt("best_coins",0)));
        records[5].setText(String.format(Locale.CHINA,"最佳 %,d · 命中率 %d%%",range.getInt("best_score",0),range.getInt("best_accuracy",0)));
    }
    private View gameCard(int index,String title,String subtitle,int color,int textColor,int secondary,int highlight,
                          String description,Class<? extends Activity> target){
        LinearLayout card=card(color);card.setContentDescription(description);card.setMinimumHeight(dp(236));
        card.addView(new GameArt(this,index),new LinearLayout.LayoutParams(-1,dp(100)));
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(15),0,dp(12),dp(18));
        TextView number=label("0"+(index+1)+"  /  PLAY",8,highlight,true);number.setLetterSpacing(.15f);body.addView(number);
        TextView name=label(title,23,textColor,true);gap(name,5);body.addView(name);
        TextView desc=label(subtitle,10,secondary,false);gap(desc,5);desc.setMinHeight(dp(31));body.addView(desc);
        records[index]=label("",9,highlight,true);records[index].setMinHeight(dp(26));gap(records[index],9);body.addView(records[index]);
        card.addView(body);card.setOnClickListener(v->startActivity(new Intent(this,target)));return card;
    }
    private void gameRow(LinearLayout parent,View first,View second){
        LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);
        row.addView(first,new LinearLayout.LayoutParams(0,-2,1));
        View spacer=new View(this);row.addView(spacer,new LinearLayout.LayoutParams(dp(13),1));
        row.addView(second,new LinearLayout.LayoutParams(0,-2,1));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private LinearLayout card(int color){LinearLayout v=new LinearLayout(this);v.setOrientation(1);v.setBackground(shape(color,24));v.setClipToOutline(true);v.setClickable(true);v.setFocusable(true);return v;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String s,int size,int color,boolean strong){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",0));return t;}
    private void gap(View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);v.setLayoutParams(p);}
    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

    private final class GameArt extends View {
        private final int game;
        private final Paint paint=new Paint(3);private final RectF r=new RectF();private final Path path=new Path();
        GameArt(Context context,int game){super(context);this.game=game;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override public void onDraw(Canvas c){super.onDraw(c);c.save();float artScale=Math.min(getWidth()/340f,getHeight()/137f);
            c.translate((getWidth()-340*artScale)/2,(getHeight()-137*artScale)/2);c.scale(artScale,artScale);
            if(game==4){
                path.reset();path.moveTo(25,95);path.lineTo(86,33);path.lineTo(146,84);path.lineTo(205,18);path.lineTo(315,99);path.close();paint.setColor(dark?0xff595747:0xffd6c8a7);c.drawPath(path,paint);
                path.reset();path.moveTo(15,120);path.cubicTo(69,122,75,111,125,97);path.cubicTo(188,76,236,58,325,67);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(14);paint.setColor(dark?0xff786b46:0xffb1a06c);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
                c.save();c.translate(172,75);c.rotate(-18);paint.setColor(0xffe1bd77);r.set(-34,-21,33,1);c.drawRoundRect(r,5,5,paint);path.reset();path.moveTo(-24,-21);path.lineTo(-16,-41);path.lineTo(10,-41);path.lineTo(23,-21);path.close();c.drawPath(path,paint);
                paint.setColor(0xff344e51);r.set(-12,-36,8,-22);c.drawRoundRect(r,2,2,paint);
                for(int x:new int[]{-23,23}){paint.setColor(0xff273738);c.drawCircle(x,3,13,paint);paint.setColor(0xffb6b5a1);c.drawCircle(x,3,6,paint);}c.restore();
            } else if(game==5){
                for(int i=0;i<3;i++){paint.setColor(new int[]{0xff334d48,0xff41594b,0xff5c7053}[i]);path.reset();path.moveTo(15,123);path.lineTo(60+i*43,51-i*11);path.lineTo(160+i*30,85);path.lineTo(230,29+i*14);path.lineTo(326,117);path.close();c.drawPath(path,paint);}
                paint.setColor(0xffd7dfba);r.set(199,39,242,107);c.drawRoundRect(r,4,4,paint);paint.setColor(0xff788765);c.drawCircle(220,69,14,paint);paint.setColor(0xffe8d18d);c.drawCircle(220,69,6,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xffd5df9b);c.drawCircle(158,68,43,paint);c.drawLine(102,68,148,68,paint);c.drawLine(168,68,214,68,paint);c.drawLine(158,12,158,57,paint);c.drawLine(158,79,158,123,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(158,68,2,paint);
            } else if(game==2){
                paint.setColor(0xff102c3b);c.drawCircle(215,55,77,paint);
                path.reset();path.moveTo(105,26);path.lineTo(239,26);path.lineTo(293,112);path.lineTo(48,112);path.close();
                paint.setColor(0xff439b96);c.drawPath(path,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xffd4e6d8);c.drawPath(path,paint);
                c.drawLine(172,26,170,112,paint);paint.setColor(0xff254854);paint.setStrokeWidth(3);c.drawLine(81,69,263,69,paint);
                paint.setColor(0xffd9e7db);paint.setStrokeWidth(1);c.drawLine(81,54,263,54,paint);c.drawLine(81,54,81,69,paint);c.drawLine(263,54,263,69,paint);
                for(int i=0;i<23;i++)c.drawLine(82+i*8,54,82+i*8,69,paint);paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xffdcb0a3);c.save();c.rotate(-24,124,106);r.set(105,86,143,122);c.drawOval(r,paint);r.set(119,116,128,134);paint.setColor(0xffe5cc99);c.drawRoundRect(r,3,3,paint);c.restore();
                paint.setColor(0x22000000);c.drawOval(211,65,231,70,paint);paint.setColor(0xfffff5d4);c.drawCircle(221,43,8,paint);paint.setColor(0xffffffff);c.drawCircle(218,40,2,paint);
            } else if(game==3){
                int[][] tiles={{1,1,1,0,0,0,0,0},{0,1,0,0,1,1,0,0},{0,0,0,0,1,1,0,0},{0,0,1,0,0,0,0,0},{1,1,1,1,0,0,1,0}};
                int[] colors={0xffb69bcf,0xff86c5bf,0xffe7bf8c,0xffbdabd9,0xffb7d5a5};
                for(int row=0;row<tiles.length;row++)for(int col=0;col<8;col++)if(tiles[row][col]!=0){
                    float x=72+col*24,y=14+row*22;paint.setColor(colors[row]);r.set(x,y,x+21,y+19);c.drawRoundRect(r,3,3,paint);paint.setColor(0x40ffffff);r.set(x+3,y+2,x+18,y+4);c.drawRoundRect(r,1,1,paint);
                }
                paint.setColor(dark?0xff70627c:0xffc6b8d2);r.set(72,128,263,130);c.drawRoundRect(r,1,1,paint);
            } else if(game==1){
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
