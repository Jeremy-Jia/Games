import dev.brickzen.arcade.EncounterPlan;
import java.util.HashSet;

public final class EncounterPlanTest {
    public static void main(String[] args){
        int[][] counts=new int[5][2];int maxTier=0;HashSet<Long> targets=new HashSet<>();
        for(long seed=0;seed<60000;seed++){
            EncounterPlan p=EncounterPlan.generate(seed);counts[p.game][p.metric]++;maxTier=Math.max(maxTier,p.difficulty);targets.add(p.target);
            check(p.target>0&&p.difficulty>0&&!p.reached(p.target-1)&&p.reached(p.target),"positive exact goal boundary");
            check(p.objective().contains(Long.toString(p.target))&&p.reward()>0&&p.reward()<=3000,"visible goal and bounded reward");
            EncounterPlan q=EncounterPlan.generate(seed);check(p.game==q.game&&p.metric==q.metric&&p.target==q.target&&p.difficulty==q.difficulty&&p.seed==q.seed,"reproducible plan");
        }
        for(int[] game:counts)for(int count:game)check(count>4500&&count<7500,"all five games and both metrics sampled");
        check(maxTier>=12&&targets.size()>300,"difficulty has long tail and goals vary beyond preset tiers");
        EncounterPlan large=new EncounterPlan(EncounterPlan.TENNIS,0,5000000000L,1000,1);
        check(large.target>Integer.MAX_VALUE&&large.difficulty==1000&&!large.reached(4999999999L),"no old match/preset target ceiling");
        String id="127dfa20-7bf4-41aa-95c9-1f9ce6d0c99d";EncounterPlan p=EncounterPlan.forPortal(id),q=EncounterPlan.forPortal(id);
        check(p.target==q.target&&p.game==q.game&&p.seed==q.seed,"same portal identity cannot reroll objective");
        try{new EncounterPlan(5,0,1,1,0);throw new AssertionError("Invalid game accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: 60000 encounter plans, all five games/two metrics, geometric difficulty tail max="+maxTier+", exact long targets and stable portal identity");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
