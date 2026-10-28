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
import dev.brickzen.blocks.BlocksActivity;
import dev.brickzen.blocks.BlocksEngine;
import dev.brickzen.tabletennis.TableTennisActivity;
import dev.brickzen.tabletennis.TableTennisEngine;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Native UI/physics smoke test; record fixtures are isolated and all preferences restored. */
public final class CollectionAdditionsInstrumentation extends Instrumentation {
    private final String[] preferenceNames={"brickzen","star_drift","portal_results","table_tennis","blocks"};
    private final Map<String,Map<String,Object>> backup=new LinkedHashMap<>();
    private final List<Activity> activities=new CopyOnWriteArrayList<>(),destroyed=new CopyOnWriteArrayList<>();
    private final List<ActivityMonitor> monitors=new ArrayList<>();
    private File output;
    private CollectionActivity hall;
    private final Bundle result=new Bundle();

    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void callActivityOnCreate(Activity activity,Bundle state){activities.add(activity);super.callActivityOnCreate(activity,state);}
    @Override public void callActivityOnDestroy(Activity activity){try{super.callActivityOnDestroy(activity);}finally{destroyed.add(activity);}}
    @Override public void onStart(){
        Throwable failure=null;
        try{
            for(String name:preferenceNames){Map<String,Object> values=new HashMap<>();
                for(Map.Entry<String,?> entry:prefs(name).getAll().entrySet())
                    values.put(entry.getKey(),entry.getValue() instanceof Set?new HashSet<>((Set<?>)entry.getValue()):entry.getValue());
                backup.put(name,values);
            }
            output=new File(getTargetContext().getExternalFilesDir(null),"verification-2.3.0");
            check(output.isDirectory()||output.mkdirs(),"Screenshot directory");
            ActivityMonitor opening=monitor(CollectionActivity.class);
            try(ParcelFileDescriptor command=getUiAutomation().executeShellCommand("am start --display 0 -n dev.brickzen.app/.CollectionActivity");FileInputStream in=new FileInputStream(command.getFileDescriptor())){
                byte[] bytes=new byte[1024];while(in.read(bytes)!=-1){}
            }
            hall=(CollectionActivity)waitForMonitorWithTimeout(opening,8000);check(hall!=null,"Collection opens");focus(hall);
            screenshot(hall.findViewById(android.R.id.content),"collection.png");
            check(onMain(()->find(hall.findViewById(android.R.id.content),"进入3D乒乓球")!=null
                    &&find(hall.findViewById(android.R.id.content),"进入俄罗斯方块")!=null),"Both new collection cards present");
            onMain(()->{for(String description:new String[]{"进入碰界打砖块","进入星隙太空生存","进入3D乒乓球","进入俄罗斯方块"}){
                View card=find(hall.findViewById(android.R.id.content),description);
                check(card!=null&&card.getHeight()>=180*hall.getResources().getDisplayMetrics().density,"Visible full-height card: "+description);
            }return null;});
            tableTennis();blocks();
        }catch(Throwable problem){failure=problem;}
        finally{
            try{onMain(()->{for(int i=activities.size()-1;i>=0;i--){Activity a=activities.get(i);if(!a.isFinishing()&&!a.isDestroyed())a.finish();}return null;});
                await("Test activities destroyed",8000,()->destroyed.containsAll(activities));
            }catch(Throwable cleanup){if(failure==null)failure=cleanup;}
            try{restore();result.putBoolean("preferences_restored",true);}catch(Throwable restoreFailure){if(failure==null)failure=restoreFailure;}
            for(ActivityMonitor m:monitors)removeMonitor(m);
        }
        result.putString("stream","\n"+(failure==null?"PASS":"FAIL")+": table tennis + blocks native UI/physics; preferences_restored="
                +result.getBoolean("preferences_restored")+"\n"+(failure==null?"":Log.getStackTraceString(failure)));
        finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }

    private void tableTennis() throws Exception{
        progress("Opening 3D table tennis through the real collection card");
        ActivityMonitor opening=monitor(TableTennisActivity.class);
        onMain(()->{check(find(hall.findViewById(android.R.id.content),"进入3D乒乓球").performClick(),"Tennis card click");return null;});
        TableTennisActivity activity=(TableTennisActivity)waitForMonitorWithTimeout(opening,8000);check(activity!=null,"Tennis Activity");focus(activity);
        screenshot(activity.game,"tennis-menu.png");
        onMain(()->{tapAction(activity.game,"start");return null;});
        TableTennisEngine engine=activity.game.engine;
        check(onMain(()->activity.game.screen.equals("play")&&!activity.game.paused),"Touch starts real tennis match");
        int initialMatches=prefs("table_tennis").getInt("matches",0);
        long deadline=SystemClock.elapsedRealtime()+18000;
        int maxRally=0;boolean high=false,far=false,near=false;
        while(SystemClock.elapsedRealtime()<deadline){
            float[] sample=onMain(()->{
                if(engine.ballVz>0){float eta=Math.max(0,(TableTennisEngine.STRIKE_Z-engine.ballZ)/engine.ballVz);engine.setPlayerTarget(engine.ballX+engine.ballVx*eta);}
                return new float[]{engine.rally,engine.ballY,engine.ballZ,engine.time};
            });
            maxRally=Math.max(maxRally,(int)sample[0]);high|=sample[1]>.25f;far|=sample[2]<-.5f;near|=sample[2]>.5f;
            if(maxRally>=6&&high&&far&&near)break;
            SystemClock.sleep(30);
        }
        check(maxRally>=4&&high&&far&&near,"Actual 3D rally crosses the net in both directions with vertical motion");
        progress("Tennis real rally: "+maxRally+" returns, both table halves, gravity and assisted paddle contact");
        await("Ball visible away from the paddles",3000,()->onMain(()->{
            if(engine.ballVz>0){float eta=Math.max(0,(TableTennisEngine.STRIKE_Z-engine.ballZ)/engine.ballVz);engine.setPlayerTarget(engine.ballX+engine.ballVx*eta);}
            return engine.phase==TableTennisEngine.Phase.PLAYING&&Math.abs(engine.ballZ)<.7f&&engine.ballY>.08f;
        }));
        screenshot(activity.game,"tennis-rally.png");
        Activity cover=coverWithHall(activity);float pausedClock=onMain(()->engine.time);SystemClock.sleep(350);
        check(onMain(()->activity.game.paused&&engine.time==pausedClock),"Tennis loses focus and freezes simulation");
        onMain(()->{cover.finish();return null;});focus(activity);
        check(onMain(()->activity.game.paused),"Focus return does not silently resume");
        onMain(()->{activity.game.resumeMatch();return null;});
        await("Tennis resumes",2000,()->onMain(()->engine.time>pausedClock+.1f));
        // A near-match-point fixture checks natural scoring and record persistence, not a full 11-point match.
        onMain(()->{engine.aiScore=10;engine.playerScore=0;return null;});
        deadline=SystemClock.elapsedRealtime()+16000;
        while(SystemClock.elapsedRealtime()<deadline&&onMain(()->engine.phase!=TableTennisEngine.Phase.MATCH_OVER)){
            onMain(()->{engine.setPlayerTarget(engine.ballX>=0?-.86f:.86f);return null;});SystemClock.sleep(30);
        }
        check(onMain(()->engine.phase==TableTennisEngine.Phase.MATCH_OVER&&engine.aiScore>=11),"A missed legal ball completes match point");
        await("Match recorded",2000,()->prefs("table_tennis").getInt("matches",0)==initialMatches+1);
        SystemClock.sleep(200);check(prefs("table_tennis").getInt("matches",0)==initialMatches+1,"Terminal record counted only once");
        screenshot(activity.game,"tennis-result.png");
        onMain(()->{activity.finish();return null;});focus(hall);result.putBoolean("table_tennis",true);
    }

    private void blocks() throws Exception{
        progress("Opening falling blocks through its collection card");
        ActivityMonitor opening=monitor(BlocksActivity.class);
        onMain(()->{check(find(hall.findViewById(android.R.id.content),"进入俄罗斯方块").performClick(),"Blocks card click");return null;});
        BlocksActivity activity=(BlocksActivity)waitForMonitorWithTimeout(opening,8000);check(activity!=null,"Blocks Activity");focus(activity);
        screenshot(activity.game,"blocks-menu.png");
        onMain(()->{tapAction(activity.game,"start");return null;});
        BlocksEngine engine=activity.game.engine;
        check(onMain(()->engine.phase==BlocksEngine.Phase.PLAYING),"Touch starts blocks");
        int held=onMain(()->engine.type);
        onMain(()->{tapAction(activity.game,"hold");return null;});
        check(onMain(()->engine.heldType==held&&!engine.holdAvailable),"Real hold button swaps and locks once");
        onMain(()->{tapAction(activity.game,"rotate");tapAction(activity.game,"left");tapAction(activity.game,"right");return null;});
        Activity cover=coverWithHall(activity);float pausedClock=onMain(()->engine.time);SystemClock.sleep(350);
        check(onMain(()->engine.phase==BlocksEngine.Phase.PAUSED&&engine.time==pausedClock),"Blocks pauses on real focus loss");
        onMain(()->{cover.finish();return null;});focus(activity);
        onMain(()->{tapAction(activity.game,"resume");return null;});
        await("Blocks clock resumes",2000,()->onMain(()->engine.time>pausedClock+.1f));
        // Prepared legal well: the real hard-drop action must lock an I and compact a completed row.
        onMain(()->{
            for(int[] row:engine.board)Arrays.fill(row,0);
            int[] heights={3,5,4,0,0,0,0,6,4,2};
            for(int x=0;x<10;x++)for(int y=20-heights[x];y<20;y++)engine.board[y][x]=1+(x+y)%7;
            engine.type=1;engine.rotation=0;engine.x=3;engine.y=0;
            activity.game.invalidate();return null;
        });
        screenshot(activity.game,"blocks-play.png");
        int beforeLines=onMain(()->engine.lines);
        onMain(()->{tapAction(activity.game,"drop");return null;});
        check(onMain(()->engine.lines==beforeLines+1&&engine.score>0&&engine.lockedPieces==1),"Hard-drop touch locks, scores and clears actual row");
        progress("Blocks real controls: hold, rotate, movement, pause/resume, hard-drop and line clear passed");
        screenshot(activity.game,"blocks-clear.png");
        // Top-out fixture drives the ordinary drop/lock path so high-score persistence is exercised.
        onMain(()->{for(int[] row:engine.board)Arrays.fill(row,2);tapAction(activity.game,"drop");return null;});
        check(onMain(()->engine.phase==BlocksEngine.Phase.OVER),"Top-out reaches ordinary game-over path");
        await("Blocks record saved",2000,()->prefs("blocks").getInt("best_score",0)>=engine.score);
        onMain(()->{activity.finish();return null;});focus(hall);result.putBoolean("blocks",true);
    }

    private Activity coverWithHall(Activity source)throws Exception{
        ActivityMonitor cover=monitor(CollectionActivity.class);onMain(()->{source.startActivity(new Intent(source,CollectionActivity.class));return null;});
        Activity activity=waitForMonitorWithTimeout(cover,8000);check(activity!=null,"Foreground cover");focus(activity);return activity;
    }
    private ActivityMonitor monitor(Class<?> type){ActivityMonitor m=addMonitor(type.getName(),null,false);monitors.add(m);return m;}
    private void focus(Activity activity)throws Exception{await("Focused "+activity.getClass().getSimpleName(),8000,()->onMain(()->activity.hasWindowFocus()&&activity.findViewById(android.R.id.content).getWidth()>0));waitForIdleSync();}
    private static View find(View root,String description){if(description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private static Object field(Object object,String...names)throws Exception{for(String name:names)try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(Arrays.toString(names));}
    private void tapAction(View view,String action)throws Exception{
        RectF hit=null;
        for(Object button:(List<?>)field(view,"buttons"))if(action.equals(field(button,"id","action"))){hit=(RectF)field(button,"rect","bounds");break;}
        check(hit!=null,"Visible action: "+action);
        float scale=view.getWidth()/400f;long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,hit.centerX()*scale,hit.centerY()*scale,0);
        MotionEvent up=MotionEvent.obtain(now,now+12,MotionEvent.ACTION_UP,hit.centerX()*scale,hit.centerY()*scale,0);
        try{check(view.dispatchTouchEvent(down),"Touch down "+action);check(view.dispatchTouchEvent(up),"Touch up "+action);}finally{down.recycle();up.recycle();}
    }
    private SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,Context.MODE_PRIVATE);}
    @SuppressWarnings("unchecked") private void restore(){
        for(Map.Entry<String,Map<String,Object>> file:backup.entrySet()){
            SharedPreferences p=prefs(file.getKey());SharedPreferences.Editor edit=p.edit().clear();
            for(Map.Entry<String,Object> entry:file.getValue().entrySet()){Object v=entry.getValue();String k=entry.getKey();
                if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
                else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));
            }
            check(edit.commit()&&p.getAll().equals(file.getValue()),"Restore every preference: "+file.getKey());
        }
        check(backup.size()==preferenceNames.length,"All preferences were backed up");
    }
    private void screenshot(View view,String filename)throws Exception{
        Bitmap bitmap=onMain(()->{Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));return b;});
        try(FileOutputStream stream=new FileOutputStream(new File(output,filename))){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream),"Save screenshot");}finally{bitmap.recycle();}
    }
    private <T>T onMain(Callable<T> callable)throws Exception{AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try{result.set(callable.call());}catch(Throwable problem){error.set(problem);}});if(error.get()!=null)throw new AssertionError("Main-thread action",error.get());return result.get();}
    private void await(String text,long timeout,Callable<Boolean> condition)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(condition.call())return;SystemClock.sleep(40);}throw new AssertionError("Timed out: "+text);}
    private void progress(String message){Bundle b=new Bundle();b.putString("stream",message+"\n");sendStatus(1,b);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
