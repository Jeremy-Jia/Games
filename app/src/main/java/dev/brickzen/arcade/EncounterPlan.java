package dev.brickzen.arcade;

import java.util.Random;
import java.util.UUID;

/** Immutable, Android-independent contract for one mystery-brick encounter. */
public final class EncounterPlan {
    public static final int STAR=0,TENNIS=1,BLOCKS=2,HILL=3,RANGE=4;
    public final int game,metric,difficulty;
    public final long target,seed;

    public EncounterPlan(int game,int metric,long target,int difficulty,long seed){
        if(game<STAR||game>RANGE||metric<0||metric>1||target<1||difficulty<1)
            throw new IllegalArgumentException("Invalid encounter contract");
        this.game=game;this.metric=metric;this.target=target;this.difficulty=difficulty;this.seed=seed;
    }
    public static EncounterPlan forPortal(String id){
        long seed;
        try{UUID uuid=UUID.fromString(id);seed=uuid.getMostSignificantBits()^Long.rotateLeft(uuid.getLeastSignificantBits(),23);}
        catch(RuntimeException invalid){seed=(id==null?0:id.hashCode())*0x9e3779b97f4a7c15L;}
        return generate(seed);
    }
    public static EncounterPlan generate(long seed){
        Random random=new Random(seed);int game=random.nextInt(5),metric=random.nextInt(2),tier=1;
        // Geometric tail: no list of maximum presets or target cap. Large tiers are progressively rarer.
        while(random.nextBoolean())tier=Math.incrementExact(tier);
        int base;
        switch(game){
            case STAR:base=metric==0?12+random.nextInt(19):600+random.nextInt(901);break;
            case TENNIS:base=metric==0?2+random.nextInt(4):4+random.nextInt(7);break;
            case BLOCKS:base=metric==0?1+random.nextInt(4):5+random.nextInt(8);break;
            case HILL:base=metric==0?60+random.nextInt(101):8+random.nextInt(13);break;
            default:base=metric==0?2+random.nextInt(4):200+random.nextInt(401);
        }
        long scale=(long)tier+(long)(tier-1)*(tier-1)/3;
        long target;
        try{target=Math.multiplyExact(base,scale);}catch(ArithmeticException numericOverflow){target=Long.MAX_VALUE;}
        return new EncounterPlan(game,metric,target,tier,random.nextLong());
    }
    public String gameName(){return new String[]{"星隙","弧线","叠界","野径","准星"}[game];}
    public String objective(){
        String n=Long.toString(target);
        switch(game){
            case STAR:return metric==0?"击破 "+n+" 架敌机":"获得 "+n+" 分";
            case TENNIS:return metric==0?"赢下 "+n+" 分":"连续回击 "+n+" 拍";
            case BLOCKS:return metric==0?"消除 "+n+" 行":"放置 "+n+" 块方块";
            case HILL:return metric==0?"行驶 "+n+" 米":"收集 "+n+" 枚金币";
            default:return metric==0?"命中 "+n+" 个靶":"获得 "+n+" 分";
        }
    }
    public boolean reached(long progress){return progress>=target;}
    public int reward(){return (int)Math.min(3000,400L+150L*difficulty);}
}
