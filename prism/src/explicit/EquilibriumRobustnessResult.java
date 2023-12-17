package explicit;

import prism.Pair;

import java.util.BitSet;
import java.util.HashMap;

public class EquilibriumRobustnessResult {

    private double epsilon;
    private HashMap<Pair<BitSet, BitSet>, Double> trembles;

    public EquilibriumRobustnessResult(double epsilon, HashMap<Pair<BitSet, BitSet>, Double> trembles) {
        this.epsilon = epsilon;
        this.trembles = trembles;
    }


    public double getEpsilon() {
        return epsilon;
    }

    public HashMap<Pair<BitSet, BitSet>, Double> getTrembles() {
        return trembles;
    }

}