package dev.brickzen.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import org.json.JSONObject;

/** Original Brick Zen activity, now hosted inside the offline game collection. */
public final class MainActivity extends Activity {
    static final int STAR_PORTAL_REQUEST=7701;
    static final String PORTAL_SNAPSHOT="portal_snapshot";
    Button[] appearanceControls;
    GameView game;
    SeekBar heightControl;
    Context settingsContext;
    AlertDialog settingsDialog;
    private boolean portalLaunching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        game=new GameView(this,false);
        restorePortalSnapshot();
        setContentView(game);
        applySystemBars();
        if(Build.VERSION.SDK_INT>=29)getWindow().getDecorView().setForceDarkAllowed(false);
    }

    void applySystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(game.appearance.dark?1792:10000);
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)
            getWindow().getInsetsController().setSystemBarsAppearance(game.appearance.dark?0:24,24);
        getWindow().setStatusBarColor(game.appearance.background);
        getWindow().setNavigationBarColor(game.appearance.background);
        getWindow().setBackgroundDrawable(new ColorDrawable(game.appearance.background));
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if(game==null)return;
        game.refreshAppearance(configuration);applySystemBars();
        if(settingsDialog!=null&&settingsDialog.isShowing()){settingsDialog.dismiss();settings();}
    }

    void refreshRate(boolean playing) {
        WindowManager.LayoutParams attributes=getWindow().getAttributes();
        attributes.preferredDisplayModeId=0;attributes.preferredRefreshRate=0;
        if(playing){
            Display display=getWindowManager().getDefaultDisplay();
            Display.Mode current=display.getMode(),best=null;
            for(Display.Mode mode:display.getSupportedModes())
                if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight()
                        &&mode.getRefreshRate()<=120.5f&&(best==null||mode.getRefreshRate()>best.getRefreshRate()))best=mode;
            attributes.preferredDisplayModeId=(best==null?current:best).getModeId();
        }
        getWindow().setAttributes(attributes);
        if(playing)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    int tint(int color){return game.appearance.color(color);}
    TextView label(String text,int size,int color,boolean bold){
        TextView view=new TextView(settingsContext);view.setText(text);view.setTextSize(size);view.setTextColor(tint(color));
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;
    }
    void space(LinearLayout container,int height){container.addView(new View(settingsContext),new LinearLayout.LayoutParams(1,dp(height)));}
    void roundedButton(Button button){
        GradientDrawable background=new GradientDrawable();background.setColor(tint(-1446430));background.setCornerRadius(dp(12));
        button.setBackground(background);button.setElevation(0);
    }
    void tintSwitch(Switch toggle){
        int[][] states={{android.R.attr.state_checked},{}};
        toggle.setThumbTintList(new ColorStateList(states,new int[]{tint(-14128551),tint(-1775393)}));
        toggle.setTrackTintList(new ColorStateList(states,new int[]{tint(-6241105),tint(-4471364)}));
    }
    private Button settingButton(String text){
        Button button=new Button(settingsContext);button.setText(text);button.setAllCaps(false);
        button.setTextSize(14);button.setTextColor(tint(-14128551));roundedButton(button);return button;
    }
    private LinearLayout.LayoutParams weighted(int height,int margin){
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,height<0?height:dp(height),1);params.leftMargin=dp(margin);return params;
    }

    void settings() {
        if(settingsDialog!=null&&settingsDialog.isShowing())return;
        settingsContext=new ContextThemeWrapper(this,game.appearance.dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);
        LinearLayout content=new LinearLayout(settingsContext);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24),dp(10),dp(24),dp(12));
        content.addView(label("外观",17,-15188170,true));space(content,10);
        LinearLayout choices=new LinearLayout(settingsContext);appearanceControls=new Button[3];
        String[] names={"跟随系统","浅色","深色"};
        for(int index=0;index<names.length;index++){
            final int mode=index;
            Button button=settingButton(names[index]);appearanceControls[index]=button;button.setTextSize(13);
            button.setPadding(dp(3),0,dp(3),0);boolean selected=game.appearanceMode==index;
            if(selected){GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(12));bg.setColor(tint(-14128551));button.setBackground(bg);}
            button.setTextColor(selected?game.appearance.primaryText:tint(-15188170));button.setContentDescription("外观："+names[index]);
            choices.addView(button,weighted(42,index==0?0:7));
            button.setOnClickListener(v->{game.setAppearance(mode);settingsDialog.dismiss();settings();});
        }
        content.addView(choices);space(content,20);content.addView(label("挡板高度",17,-15188170,true));space(content,7);
        TextView heightLabel=label("距底部 "+Math.round(game.engine.paddleLift*100)+"%",14,-14128551,false);content.addView(heightLabel);
        heightControl=new SeekBar(settingsContext);heightControl.setMax(24);heightControl.setProgress(Math.round(game.engine.paddleLift*100)-12);
        heightControl.setContentDescription("挡板高度");heightControl.setProgressTintList(ColorStateList.valueOf(tint(-14128551)));
        heightControl.setThumbTintList(ColorStateList.valueOf(tint(-14128551)));content.addView(heightControl,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout heightLabels=new LinearLayout(settingsContext);TextView low=label("较低",12,-8481911,false),high=label("较高",12,-8481911,false);
        high.setGravity(android.view.Gravity.END);heightLabels.addView(low,weighted(-2,0));heightLabels.addView(high,weighted(-2,0));content.addView(heightLabels);
        space(content,10);LinearLayout presets=new LinearLayout(settingsContext);String[] heights={"偏低","适中","偏高"};int[] values={0,10,24};
        for(int index=0;index<heights.length;index++){final int value=values[index];Button button=settingButton(heights[index]);presets.addView(button,weighted(42,index==0?0:8));button.setOnClickListener(v->heightControl.setProgress(value));}
        content.addView(presets);space(content,10);content.addView(label("可调范围 12%～36%，下方留出手指操作空间。",12,-8481911,false));space(content,20);
        Switch soundToggle=new Switch(settingsContext);soundToggle.setText("游戏音效");soundToggle.setTextColor(tint(-15188170));soundToggle.setTextSize(16);soundToggle.setChecked(game.sound);tintSwitch(soundToggle);
        content.addView(soundToggle,new LinearLayout.LayoutParams(-1,dp(46)));
        Switch hapticToggle=new Switch(settingsContext);hapticToggle.setText("震动反馈");hapticToggle.setTextColor(tint(-15188170));hapticToggle.setTextSize(16);
        hapticToggle.setChecked(game.haptic);hapticToggle.setEnabled(game.haptics.available());tintSwitch(hapticToggle);content.addView(hapticToggle,new LinearLayout.LayoutParams(-1,dp(46)));
        content.addView(label("碰砖、接球和道具分别有短震动反馈。",12,-8481911,false));space(content,8);
        LinearLayout previews=new LinearLayout(settingsContext);Button audioPreview=settingButton("试听音效"),hapticPreview=settingButton("试一下震动");
        previews.addView(audioPreview,weighted(46,0));previews.addView(hapticPreview,weighted(46,8));hapticPreview.setEnabled(game.haptics.available());content.addView(previews);
        heightControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar seekBar){}public void onStopTrackingTouch(SeekBar seekBar){}
            public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){int percent=progress+12;game.engine.setPaddleLift(percent/100f);heightLabel.setText("距底部 "+percent+"%");game.save();game.invalidate();}
        });
        soundToggle.setOnCheckedChangeListener((button,checked)->{game.sound=checked;game.audio.enabled=checked;if(!checked)game.audio.stop();game.save();});
        hapticToggle.setOnCheckedChangeListener((button,checked)->{game.haptic=checked;game.save();if(checked)game.haptics.test();else game.haptics.stop();});
        audioPreview.setOnClickListener(v->{soundToggle.setChecked(true);game.audio.preview();});
        hapticPreview.setOnClickListener(v->{hapticToggle.setChecked(true);game.haptics.test();});
        ScrollView scroll=new ScrollView(settingsContext);scroll.setFillViewport(false);scroll.addView(content);
        settingsDialog=new AlertDialog.Builder(settingsContext).setTitle("设置").setView(scroll).setPositiveButton("完成",null)
                .setNegativeButton("恢复默认",(dialog,which)->{game.engine.setPaddleLift(.22f);game.sound=true;game.audio.enabled=true;game.haptic=true;game.setAppearance(0);}).create();
        settingsDialog.setOnDismissListener(dialog->{game.save();game.invalidate();});settingsDialog.show();
        GradientDrawable background=new GradientDrawable();background.setColor(game.appearance.background);background.setCornerRadius(dp(24));
        if(settingsDialog.getWindow()!=null)settingsDialog.getWindow().setBackgroundDrawable(background);
        settingsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(tint(-14128551));settingsDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(tint(-14128551));
    }

    private void restorePortalSnapshot(){
        String snapshot=game.prefs.getString(PORTAL_SNAPSHOT,null);if(snapshot==null)return;
        try{
            GameEngine restored=GameEngine.fromSnapshot(snapshot);if(restored==null)throw new IllegalArgumentException("Empty snapshot");
            game.engine=restored;game.engine.pause();game.engine.portalPending=false;game.scene=GameView.Scene.GAME;
            game.toast=game.prefs.getString("portal_message","已恢复上次航程，点击继续");game.toastLife=5;
        }catch(RuntimeException error){game.prefs.edit().remove(PORTAL_SNAPSHOT).remove("portal_message").commit();}
    }

    boolean checkpointPortal(){
        if(game==null||game.scene!=GameView.Scene.GAME)return true;
        if(!game.engine.portalActive&&!game.prefs.contains(PORTAL_SNAPSHOT))return true;
        String snapshot=game.engine.snapshot();
        return snapshot!=null&&game.prefs.edit().putString(PORTAL_SNAPSHOT,snapshot).commit();
    }

    void clearPortalCheckpoint(){game.prefs.edit().remove(PORTAL_SNAPSHOT).remove("portal_message").commit();}

    public void openStarPortal(){
        if(portalLaunching||game==null||!game.engine.portalActive)return;
        game.engine.portalPending=false;game.engine.pause();game.paddlePointer=-1;game.pressed.clear();
        if(!checkpointPortal()){game.toast="保存暂未完成，请稍后再试";game.toastLife=5;game.invalidate();return;}
        portalLaunching=true;game.audio.stop();game.haptics.stop();
        Intent intent=new Intent(this,io.github.jeremyjia.stardrift.MainActivity.class)
                .putExtra("bonus_mode",true).putExtra("portal_id",game.engine.portalId);
        try{startActivityForResult(intent,STAR_PORTAL_REQUEST);}
        catch(RuntimeException error){portalLaunching=false;game.toast="星隙暂时无法启动，可继续或返回碰界";game.toastLife=5;game.invalidate();}
    }

    void cancelStarPortal(){
        if(game.engine.portalActive)applyPortalResult(game.engine.portalId,0,false,true);
    }

    private boolean applyPortalResult(String id,int score,boolean survived,boolean cancelled){
        if(game==null||!game.engine.portalActive||id==null||!id.equals(game.engine.portalId))return false;
        String before=game.engine.snapshot();if(before==null)return false;int previous=game.engine.score;
        game.engine.completePortal(Math.max(0,Math.min(3000,score)),survived,cancelled);
        game.scene=GameView.Scene.GAME;game.engine.pause();game.engine.portalPending=false;
        String message=cancelled?"已返回碰界，原局已保留":"星隙奖励 +"+(game.engine.score-previous)+" 分 · 点击继续";
        String completed=game.engine.snapshot();
        if(completed==null||!game.prefs.edit().putString(PORTAL_SNAPSHOT,completed).putString("portal_message",message).commit()){
            game.engine=GameEngine.fromSnapshot(before);game.toast="奖励正在等待保存，请重新进入本局";game.toastLife=5;game.invalidate();return false;
        }
        getSharedPreferences("portal_results",MODE_PRIVATE).edit().remove(id).commit();
        game.toast=message;game.toastLife=6;game.save();game.refresh();game.invalidate();game.announceForAccessibility(message);
        return true;
    }

    private void consumeSavedPortalResult(){
        if(game==null||!game.engine.portalActive||game.engine.portalId==null)return;
        String json=getSharedPreferences("portal_results",MODE_PRIVATE).getString(game.engine.portalId,null);if(json==null)return;
        try{JSONObject result=new JSONObject(json);applyPortalResult(result.optString("portal_id"),result.optInt("bonus_score"),result.optBoolean("survived"),result.optBoolean("cancelled"));}
        catch(Exception ignored){game.toast="奖励记录暂无法读取，原局仍已保存";game.toastLife=5;}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode!=STAR_PORTAL_REQUEST)return;
        portalLaunching=false;consumeSavedPortalResult();
        if(game==null||!game.engine.portalActive)return;
        if(data!=null){
            applyPortalResult(data.getStringExtra("portal_id"),data.getIntExtra("bonus_score",0),
                    data.getBooleanExtra("survived",false),resultCode!=RESULT_OK||data.getBooleanExtra("cancelled",false));
        }else if(resultCode!=RESULT_OK)cancelStarPortal();
    }

    @Override protected void onSaveInstanceState(Bundle state){checkpointPortal();super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();portalLaunching=false;consumeSavedPortalResult();if(game!=null)game.wake();}
    @Override protected void onPause(){if(game!=null){game.sleep();checkpointPortal();}super.onPause();}
    @Override protected void onDestroy(){if(game!=null)game.close();super.onDestroy();}
    @Override public void onBackPressed(){if(game!=null&&game.back())return;finish();}
}
