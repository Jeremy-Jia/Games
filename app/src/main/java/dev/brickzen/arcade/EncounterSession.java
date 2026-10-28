package dev.brickzen.arcade;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;

/** Shared goal HUD and durable, idempotent automatic return for all five side games. */
public final class EncounterSession {
    public final EncounterPlan plan;
    public final String portalId;
    private final Activity host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Paint paint=new Paint(3);
    private final RectF rect=new RectF();
    private long progress;
    private boolean ending,returned,success,cancelled;
    private long returnAt;

    private EncounterSession(Activity host,EncounterPlan plan,String id){this.host=host;this.plan=plan;portalId=id;}
    /** Null preserves ordinary play and the old Star-only bonus contract. */
    public static EncounterSession from(Activity host,int expectedGame){
        Intent i=host.getIntent();
        if(!i.getBooleanExtra("bonus_mode",false)||!i.hasExtra("encounter_target"))return null;
        String id=i.getStringExtra("portal_id");
        try{
            EncounterPlan p=new EncounterPlan(i.getIntExtra("encounter_game",-1),i.getIntExtra("encounter_metric",-1),
                    i.getLongExtra("encounter_target",0),i.getIntExtra("encounter_difficulty",0),i.getLongExtra("encounter_seed",0));
            if(p.game!=expectedGame||id==null||id.isEmpty())throw new IllegalArgumentException("Mismatched encounter");
            EncounterSession session=new EncounterSession(host,p,id);
            String saved=host.getSharedPreferences("portal_results",Activity.MODE_PRIVATE).getString(id,null);
            if(saved!=null)try{
                JSONObject result=new JSONObject(saved);
                if(id.equals(result.optString("portal_id"))&&result.optInt("encounter_game",-1)==p.game
                        &&result.optInt("encounter_metric",-1)==p.metric&&result.optLong("encounter_target",0)==p.target
                        &&result.optInt("encounter_difficulty",0)==p.difficulty){
                    session.ending=session.returned=true;session.success=result.optBoolean("survived");session.cancelled=result.optBoolean("cancelled");
                    session.progress=session.success?p.target:0;
                    session.handler.post(()->{
                        host.setResult(Activity.RESULT_OK,new Intent().putExtra("portal_id",id).putExtra("bonus_score",result.optInt("bonus_score"))
                                .putExtra("survived",session.success).putExtra("cancelled",session.cancelled).putExtra("encounter_game",p.game)
                                .putExtra("encounter_metric",p.metric).putExtra("encounter_target",p.target).putExtra("encounter_difficulty",p.difficulty));host.finish();
                    });
                }
            }catch(org.json.JSONException invalidSaved){/* Keep the original goal for a fresh attempt. */}
            return session;
        }catch(RuntimeException invalid){host.setResult(Activity.RESULT_CANCELED);host.finish();return null;}
    }
    public boolean isEnding(){return ending;}
    public long progress(){return progress;}
    public void observe(long value,boolean failed){
        if(ending)return;
        progress=Math.max(progress,Math.max(0,value));
        if(plan.reached(progress))complete(true,false);else if(failed)complete(false,false);
    }
    public void cancel(){if(!ending)complete(false,true);}
    private void complete(boolean won,boolean cancel){
        ending=true;success=won;cancelled=cancel;returnAt=SystemClock.uptimeMillis()+650;
        persistAndReturn();
    }
    private void persistAndReturn(){
        if(returned)return;
        int reward=cancelled?0:success?plan.reward():(int)Math.min(300,plan.reward()*.2*Math.min(1,progress/(double)plan.target));
        try{
            JSONObject json=new JSONObject().put("portal_id",portalId).put("bonus_score",reward)
                    .put("survived",success).put("cancelled",cancelled).put("encounter_game",plan.game)
                    .put("encounter_target",plan.target).put("encounter_metric",plan.metric).put("encounter_difficulty",plan.difficulty);
            if(!host.getSharedPreferences("portal_results",Activity.MODE_PRIVATE).edit().putString(portalId,json.toString()).commit()){
                handler.postDelayed(this::persistAndReturn,1000);return;
            }
            returned=true;
            handler.postDelayed(()->{
                if(host.isDestroyed())return;
                host.setResult(Activity.RESULT_OK,new Intent().putExtra("portal_id",portalId).putExtra("bonus_score",reward)
                        .putExtra("survived",success).putExtra("cancelled",cancelled).putExtra("encounter_game",plan.game)
                        .putExtra("encounter_target",plan.target).putExtra("encounter_metric",plan.metric).putExtra("encounter_difficulty",plan.difficulty));
                host.finish();
            },Math.max(0,returnAt-SystemClock.uptimeMillis()));
        }catch(org.json.JSONException impossible){handler.postDelayed(this::persistAndReturn,1000);}
    }
    /** Caller supplies a free HUD rectangle in its own logical coordinates. */
    public void draw(Canvas c,float left,float top,float width){
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xf0102d32);rect.set(left,top,left+width,top+52);c.drawRoundRect(rect,12,12,paint);
        paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setTextAlign(Paint.Align.LEFT);paint.setTextSize(12);paint.setColor(0xffc1efd2);
        String title=ending?(cancelled?"已结束奇遇":success?"目标达成 · 自动返回":"奇遇结束 · 自动返回"):plan.objective();
        while(paint.measureText(title)>width-24&&paint.getTextSize()>8)paint.setTextSize(paint.getTextSize()-1);
        c.drawText(title,left+12,top+20,paint);
        paint.setTextSize(9);paint.setTypeface(Typeface.create("monospace",0));paint.setColor(0xffb6c5bc);
        String detail=ending?"返回碰界，继续打球":"难度 "+plan.difficulty+"  ·  "+Math.min(progress,plan.target)+" / "+plan.target;
        while(paint.measureText(detail)>width-24&&paint.getTextSize()>7)paint.setTextSize(paint.getTextSize()-1);
        c.drawText(detail,left+12,top+37,paint);
        paint.setColor(0xff314b47);rect.set(left+12,top+44,left+width-12,top+46);c.drawRoundRect(rect,1,1,paint);
        paint.setColor(0xffa9dfbd);rect.right=rect.left+(width-24)*(float)Math.min(1,progress/(double)plan.target);c.drawRoundRect(rect,1,1,paint);
    }
}
