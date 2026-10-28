package dev.brickzen.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import dev.brickzen.arcade.EncounterPlan;
import dev.brickzen.arcade.EncounterSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** All five production portal routes; deterministic goal fixtures, real progress and automatic returns. */
public final class CollectionSmokeInstrumentation extends Instrumentation {
    private static final String[] PREFS={"brickzen","star_drift","portal_results","table_tennis","blocks","hill_climb","target_range"};
    private static final String[] GAMES={"io.github.jeremyjia.stardrift.MainActivity","dev.brickzen.tabletennis.TableTennisActivity",
            "dev.brickzen.blocks.BlocksActivity","dev.brickzen.hillclimb.HillClimbActivity","dev.brickzen.range.RangeActivity"};
    private final Map<String,Map<String,Object>> backup=new LinkedHashMap<>();
    private final List<Activity> activities=new CopyOnWriteArrayList<>(),destroyed=new CopyOnWriteArrayList<>();
    private final List<ActivityMonitor> monitors=new ArrayList<>();
    private final Bundle result=new Bundle();
    private MainActivity brick;
    private Frozen savedCompletionCheckpoint;
    private String savedCompletionResult;
    private File output;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void callActivityOnCreate(Activity a,Bundle state){activities.add(a);super.callActivityOnCreate(a,state);}
    @Override public void callActivityOnDestroy(Activity a){try{super.callActivityOnDestroy(a);}finally{destroyed.add(a);}}
    @Override public void onStart(){
        Throwable failure=null;
        try{
            for(String name:PREFS){Map<String,Object> values=new HashMap<>();for(Map.Entry<String,?> e:prefs(name).getAll().entrySet())values.put(e.getKey(),e.getValue() instanceof Set?new HashSet<>((Set<?>)e.getValue()):e.getValue());backup.put(name,values);}
            output=new File(getTargetContext().getExternalFilesDir(null),"verification-2.5.0");check(output.isDirectory()||output.mkdirs(),"Output directory");
            prefs("brickzen").edit().remove(MainActivity.PORTAL_SNAPSHOT).remove("portal_message").commit();
            ActivityMonitor hallMonitor=monitor(CollectionActivity.class.getName());
            try(ParcelFileDescriptor cmd=getUiAutomation().executeShellCommand("am start --display 0 -n dev.brickzen.app/.CollectionActivity");FileInputStream in=new FileInputStream(cmd.getFileDescriptor())){byte[] bytes=new byte[1024];while(in.read(bytes)!=-1){}}
            Activity hall=waitForMonitorWithTimeout(hallMonitor,8000);check(hall!=null,"Hall launched");focus(hall);
            screenshot(hall.findViewById(android.R.id.content),"collection.png");
            ActivityMonitor brickMonitor=monitor(MainActivity.class.getName());onMain(()->{check(find(hall.findViewById(android.R.id.content),"进入碰界打砖块").performClick(),"Brick card opens");return null;});
            brick=(MainActivity)waitForMonitorWithTimeout(brickMonitor,8000);check(brick!=null,"Brick Activity");focus(brick);
            runGoal(new EncounterPlan(EncounterPlan.STAR,0,6,1,1));
            runGoal(new EncounterPlan(EncounterPlan.TENNIS,1,4,2,4));
            runGoal(new EncounterPlan(EncounterPlan.BLOCKS,1,3,2,7));
            runGoal(new EncounterPlan(EncounterPlan.HILL,0,22,2,1));
            runGoal(new EncounterPlan(EncounterPlan.RANGE,0,2,2,1));
            randomAndCancel();failedGoal();completedResultRecovery();
        }catch(Throwable problem){failure=problem;}
        finally{
            try{onMain(()->{for(int i=activities.size()-1;i>=0;i--){Activity a=activities.get(i);if(!a.isDestroyed()&&!a.isFinishing())a.finish();}return null;});await("Activities destroyed",8000,()->destroyed.containsAll(activities));}catch(Throwable cleanup){if(failure==null)failure=cleanup;}
            try{restore();result.putBoolean("preferences_restored",true);}catch(Throwable problem){if(failure==null)failure=problem;}
            for(ActivityMonitor m:monitors)removeMonitor(m);
        }
        result.putString("stream","\n"+(failure==null?"PASS":"FAIL")+": all five random-goal portal routes, random selection/cancel/failure, automatic continuation; preferences_restored="+result.getBoolean("preferences_restored")+"\n"+(failure==null?"":Log.getStackTraceString(failure)));
        finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void runGoal(EncounterPlan plan)throws Exception{
        onMain(()->{brick.game.start(true);return null;});
        ActivityMonitor opening=monitor(GAMES[plan.game]);Frozen frozen=trigger(plan);
        Activity child=waitForMonitorWithTimeout(opening,8000);check(child!=null,"Correct game route "+plan.game);focus(child);
        View view=(View)field(child,"game");EncounterSession session=(EncounterSession)field(child,"encounter");
        check(session!=null&&session.plan.target==plan.target&&session.plan.difficulty==plan.difficulty,"Goal and difficulty delivered");
        onMain(()->{frozen.verify(brick.game.engine);GameEngine saved=GameEngine.fromSnapshot(prefs("brickzen").getString(MainActivity.PORTAL_SNAPSHOT,null));check(saved!=null,"Checkpoint before child");frozen.verify(saved);return null;});
        screenshot(view,"goal-"+plan.game+".png");
        long deadline=SystemClock.elapsedRealtime()+45000,nextAction=0;
        while(SystemClock.elapsedRealtime()<deadline&&!onMain(session::isEnding)){
            final long now=SystemClock.elapsedRealtime();final boolean action=now>=nextAction;
            onMain(()->{drive(child,view,plan.game,action);return null;});
            if(action)nextAction=now+350;
            SystemClock.sleep(40);
        }
        check(onMain(session::isEnding)&&onMain(()->session.progress()>=plan.target),"Real gameplay reaches goal "+plan.game);
        screenshot(view,"complete-"+plan.game+".png");
        if(plan.game==EncounterPlan.RANGE){savedCompletionCheckpoint=frozen;savedCompletionResult=prefs("portal_results").getString(frozen.id,null);}
        awaitReturn(frozen.id);
        onMain(()->{
            GameEngine saved=GameEngine.fromSnapshot(prefs("brickzen").getString(MainActivity.PORTAL_SNAPSHOT,null));check(saved!=null,"Completed snapshot");frozen.verify(saved);
            check(saved.score==frozen.score+plan.reward(),"Exact reward persisted once");
            check(brick.game.engine.state==GameEngine.State.PLAYING,"Automatic running return");
            int before=brick.game.engine.score;
            brick.onActivityResult(MainActivity.STAR_PORTAL_REQUEST,Activity.RESULT_OK,new Intent().putExtra("portal_id",frozen.id).putExtra("bonus_score",plan.reward()).putExtra("survived",true)
                    .putExtra("encounter_game",plan.game).putExtra("encounter_metric",plan.metric).putExtra("encounter_target",plan.target).putExtra("encounter_difficulty",plan.difficulty));
            check(brick.game.engine.score==before,"Duplicate reward rejected");return null;
        });
        await("Ball continues moving",2000,()->onMain(()->brick.game.engine.time>frozen.time+.1f));
        screenshot(brick.game,"return-"+plan.game+".png");
        progress("PASS route "+plan.game+" "+plan.gameName()+": "+plan.objective()+", difficulty="+plan.difficulty+", real progress="+session.progress()+", automatic return and saved ball/obstacle state");
    }
    private void drive(Activity a,View view,int game,boolean action)throws Exception{
        if(game==EncounterPlan.STAR){io.github.jeremyjia.stardrift.GameEngine e=(io.github.jeremyjia.stardrift.GameEngine)field(view,"game");float angle=e.time*1.3f;e.setTarget(200+(float)Math.sin(angle)*140,e.height*.55f+(float)Math.cos(angle)*180);if(e.pulseCharge>=.999f&&e.enemies.size()>3)e.pulse();}
        else if(game==EncounterPlan.TENNIS){dev.brickzen.tabletennis.TableTennisEngine e=((dev.brickzen.tabletennis.TableTennisActivity)a).game.engine;if(e.ballVz>0)e.setPlayerTarget(e.ballX+e.ballVx*Math.max(0,(dev.brickzen.tabletennis.TableTennisEngine.STRIKE_Z-e.ballZ)/e.ballVz));}
        else if(game==EncounterPlan.BLOCKS&&action){dev.brickzen.blocks.BlocksView v=((dev.brickzen.blocks.BlocksActivity)a).game;if(v.engine.phase==dev.brickzen.blocks.BlocksEngine.Phase.PLAYING){int target=v.engine.lockedPieces%2==0?0:7;while(v.engine.x>target&&v.engine.move(-1)){}while(v.engine.x<target&&v.engine.move(1)){}v.performAction("drop");}}
        else if(game==EncounterPlan.HILL){((dev.brickzen.hillclimb.HillClimbActivity)a).game.engine.setControls(1,0);}
        else if(game==EncounterPlan.RANGE){dev.brickzen.range.RangeView v=((dev.brickzen.range.RangeActivity)a).game;dev.brickzen.range.RangeEngine e=v.engine;if(e.ammo==0&&e.pendingImpact<=0&&e.reloadRemaining<=0)v.performAction("reload");if(e.canFire()){e.setAim(e.aimForTargetX(),e.aimForTargetY());v.performAction("fire");}}
    }
    private void randomAndCancel()throws Exception{
        onMain(()->{brick.game.start(true);return null;});
        ArrayList<ActivityMonitor> all=new ArrayList<>();for(String name:GAMES)all.add(monitor(name));
        Frozen frozen=trigger(null);EncounterPlan expected=onMain(brick::currentPortalPlan);
        Activity child=waitForMonitorWithTimeout(all.get(expected.game),8000);check(child!=null,"Unforced random route");focus(child);
        EncounterSession session=(EncounterSession)field(child,"encounter");
        EncounterPlan regenerated=EncounterPlan.forPortal(frozen.id);
        check(session.plan.game==regenerated.game&&session.plan.target==regenerated.target&&session.plan.difficulty==regenerated.difficulty,"Random plan persisted, cannot reroll on resume");
        onMain(()->{Intent bad=new Intent().putExtra("portal_id",frozen.id).putExtra("bonus_score",3000).putExtra("survived",true).putExtra("encounter_game",(expected.game+1)%5)
                .putExtra("encounter_target",expected.target).putExtra("encounter_metric",expected.metric).putExtra("encounter_difficulty",expected.difficulty);
            brick.onActivityResult(MainActivity.STAR_PORTAL_REQUEST,Activity.RESULT_OK,bad);check(brick.game.engine.portalActive&&brick.game.engine.score==frozen.score,"Mismatched goal result rejected");session.cancel();return null;});
        awaitReturn(frozen.id);check(onMain(()->brick.game.engine.score==frozen.score&&brick.game.engine.state==GameEngine.State.PLAYING),"Cancel automatically continues with no reward");
        progress("PASS unforced random route "+expected.game+", stable plan, mismatched callback rejected and cancellation");
    }
    private void failedGoal()throws Exception{
        onMain(()->{brick.game.start(true);return null;});ActivityMonitor m=monitor(GAMES[0]);Frozen frozen=trigger(new EncounterPlan(0,0,100,1,1));
        Activity child=waitForMonitorWithTimeout(m,8000);check(child!=null,"Failure test Star");focus(child);
        View view=(View)field(child,"game");
        onMain(()->{io.github.jeremyjia.stardrift.GameEngine e=(io.github.jeremyjia.stardrift.GameEngine)field(view,"game");e.hp=1;e.invulnerable=0;
            io.github.jeremyjia.stardrift.GameEngine.Shot shot=new io.github.jeremyjia.stardrift.GameEngine.Shot();shot.x=e.shipX;shot.y=e.shipY;shot.r=4;shot.hostile=true;shot.damage=20;shot.life=2;e.shots.add(shot);return null;});
        awaitReturn(frozen.id);check(onMain(()->brick.game.engine.state==GameEngine.State.PLAYING),"Failure automatic return");progress("PASS failed encounter auto-return");
    }
    private Frozen trigger(EncounterPlan plan)throws Exception{return onMain(()->{
        GameEngine e=brick.game.engine;e.launch();GameEngine.Brick mystery=null;for(GameEngine.Brick b:e.bricks)if(b.mystery){mystery=b;break;}check(mystery!=null,"Mystery exists");e.hit(mystery,false);
        if(plan!=null){e.portalGame=plan.game;e.portalMetric=plan.metric;e.portalTarget=plan.target;e.portalDifficulty=plan.difficulty;e.portalSeed=plan.seed;}
        brick.currentPortalPlan();Frozen frozen=new Frozen(e);brick.game.invalidate();return frozen;
    });}
    private void completedResultRecovery()throws Exception{
        check(savedCompletionCheckpoint!=null&&savedCompletionResult!=null,"Real completed result captured");
        Frozen frozen=savedCompletionCheckpoint;
        onMain(()->{
            // Recreate the crash window: the parent has its pending checkpoint, the child result is durable.
            brick.game.engine=GameEngine.fromSnapshot(frozen.snapshot);brick.game.engine.portalPending=false;
            check(prefs("portal_results").edit().putString(frozen.id,savedCompletionResult).commit(),"Restore durable completion fixture");
            brick.openRandomPortal();return null;
        });
        awaitReturn(frozen.id);
        check(onMain(()->brick.game.engine.score==frozen.score+new EncounterPlan(frozen.game,frozen.metric,frozen.goal,frozen.tier,frozen.seed).reward()),"Existing successful result recovered without replay or cancellation overwrite");
        progress("PASS durable completed-result recovery through recreated child");
    }
    private void awaitReturn(String id)throws Exception{await("Automatic portrait return",10000,()->onMain(()->brick.hasWindowFocus()&&!brick.game.engine.portalActive&&id.equals(brick.game.engine.portalId)&&brick.game.getHeight()>brick.game.getWidth()));}
    private static final class Frozen{
        final String id,snapshot;final int score,level,lives,destroyed,game,metric,tier;final long goal,seed;final float time;final float[][] balls,gates;final int[] hp;
        Frozen(GameEngine e){id=e.portalId;snapshot=e.snapshot();score=e.score;level=e.level;lives=e.lives;destroyed=e.destroyed;time=e.time;game=e.portalGame;metric=e.portalMetric;tier=e.portalDifficulty;goal=e.portalTarget;seed=e.portalSeed;
            balls=new float[e.balls.size()][4];for(int i=0;i<balls.length;i++){GameEngine.Ball b=e.balls.get(i);balls[i]=new float[]{b.x,b.y,b.vx,b.vy};}
            gates=new float[e.obstacles.size()][4];for(int i=0;i<gates.length;i++){GameEngine.Brick b=e.obstacles.get(i);gates[i]=new float[]{b.x,b.y,b.w,b.h};}
            hp=new int[e.bricks.size()];for(int i=0;i<hp.length;i++)hp[i]=e.bricks.get(i).hp;
        }
        void verify(GameEngine e){check(e!=null&&e.time==time&&e.level==level&&e.lives==lives&&e.destroyed==destroyed,"Frozen level/time/lives");
            check(e.portalGame==game&&e.portalMetric==metric&&e.portalDifficulty==tier&&e.portalTarget==goal&&e.portalSeed==seed,"Goal survives checkpoint");
            check(e.balls.size()==balls.length&&e.obstacles.size()==gates.length&&e.bricks.size()==hp.length,"Frozen entity counts");
            for(int i=0;i<balls.length;i++){GameEngine.Ball b=e.balls.get(i);check(b.x==balls[i][0]&&b.y==balls[i][1]&&b.vx==balls[i][2]&&b.vy==balls[i][3],"Frozen ball position/velocity");}
            for(int i=0;i<gates.length;i++){GameEngine.Brick b=e.obstacles.get(i);check(b.x==gates[i][0]&&b.y==gates[i][1]&&b.w==gates[i][2]&&b.h==gates[i][3],"Frozen obstacle");}
            for(int i=0;i<hp.length;i++)check(e.bricks.get(i).hp==hp[i],"Frozen brick health");
        }
    }
    private SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,Context.MODE_PRIVATE);}
    @SuppressWarnings("unchecked")private void restore(){for(Map.Entry<String,Map<String,Object>> file:backup.entrySet()){SharedPreferences p=prefs(file.getKey());SharedPreferences.Editor edit=p.edit().clear();for(Map.Entry<String,Object> entry:file.getValue().entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}check(edit.commit()&&p.getAll().equals(file.getValue()),"Restore preferences "+file.getKey());}check(backup.size()==PREFS.length,"Complete backup");}
    private ActivityMonitor monitor(String name){ActivityMonitor m=addMonitor(name,null,false);monitors.add(m);return m;}
    private void focus(Activity a)throws Exception{await("Focused child",8000,()->onMain(()->a.hasWindowFocus()&&a.findViewById(android.R.id.content).getWidth()>0));waitForIdleSync();}
    private static View find(View root,String description){if(description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),description);if(found!=null)return found;}return null;}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void screenshot(View v,String name)throws Exception{Bitmap b=onMain(()->{Bitmap image=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));return image;});try(FileOutputStream stream=new FileOutputStream(new File(output,name))){check(b.compress(Bitmap.CompressFormat.PNG,100,stream),"Screenshot");}finally{b.recycle();}}
    private <T>T onMain(Callable<T> action)throws Exception{AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{result.set(action.call());}catch(Throwable problem){error.set(problem);}});if(error.get()!=null)throw new AssertionError("Main-thread action",error.get());return result.get();}
    private void await(String text,long timeout,Callable<Boolean> condition)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(condition.call())return;SystemClock.sleep(40);}throw new AssertionError("Timed out: "+text);}
    private void progress(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(1,b);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
