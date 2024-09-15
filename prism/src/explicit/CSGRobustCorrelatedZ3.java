package explicit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.microsoft.z3.*;

import com.microsoft.z3.Model;
import prism.Pair;

/**
 * Z3-based implementation for robust correlated equilibria computation
 *
 * @author Christoph Weinhuber
 */
public class CSGRobustCorrelatedZ3 implements CSGCorrelated {

    private RealExpr[] vars;
    private RealExpr[] payoff_vars;
    private RealExpr obj_var;
    private ArithExpr[] payoffs;

    private HashMap<String, String> cfg;
    private Context ctx;
    private Optimize solver;

    private IntExpr zero;
    private IntExpr one;

    private String name;
    private int n_coalitions;

    private int n_entries;

    /**
     * Creates a new CSGCorrelatedZ3 (without initialisation)
     */
    public CSGRobustCorrelatedZ3() {
        cfg = new HashMap<String, String>();
        cfg.put("model", "true");
        ctx = new Context(cfg);
        solver = ctx.mkOptimize();
        name = Version.getFullVersion();
    }

    /**
     * Creates a new CSGCorrelatedZ3
     *
     * @param n_entries    Number of entries in the utility table
     * @param n_coalitions Number of coalitions
     */
    public CSGRobustCorrelatedZ3(int n_entries, int n_coalitions) {
        cfg = new HashMap<String, String>();
        cfg.put("model", "true");
        cfg.put("auto_config", "true");
        ctx = new Context(cfg);
        solver = ctx.mkOptimize();
        name = Version.getFullVersion();
        zero = ctx.mkInt(0);
        one = ctx.mkInt(1);
        vars = new RealExpr[n_entries * n_entries * n_entries * n_entries+ 1];
        payoffs = new ArithExpr[n_coalitions];
        this.n_coalitions = n_coalitions;
        this.n_entries = n_entries;

        // Setup of variables p_\alpha
        // 0 ≤ p_\alpha ≤ 1
        for (int i = 0; i < vars.length; i++) {
            vars[i] = ctx.mkRealConst("v" + i);
            solver.Add(ctx.mkLe(vars[i], one));
            solver.Add(ctx.mkGe(vars[i], zero));
        }
        payoff_vars = new RealExpr[n_coalitions];
        obj_var = ctx.mkRealConst("ob");
    }

    public static ArrayList<ArrayList<Integer>> getTrembleActions(ArrayList<Integer> indices, ArrayList<ArrayList<Integer>> strategies) {

        // cloning indices and strategies
        indices = new ArrayList<>(indices);
        strategies = new ArrayList<>(strategies);

        if (indices.isEmpty()) {
            ArrayList<ArrayList<Integer>> emptyResult = new ArrayList<>();
            emptyResult.add(new ArrayList<>());
            return emptyResult;
        }

        Integer currentIndex = indices.remove(0);
        ArrayList<Integer> currentStrategy = strategies.get(currentIndex);

        ArrayList<ArrayList<Integer>> smallerResult = getTrembleActions(indices, strategies);

        ArrayList<ArrayList<Integer>> result = new ArrayList<>();
        for (Integer action : currentStrategy) {
            for (ArrayList<Integer> subList : smallerResult) {
                ArrayList<Integer> newList = new ArrayList<>(subList);
                newList.add(0, action); // Add at the beginning since we're recursively reducing the size.
                result.add(newList);
            }
        }
        return result;
    }

    private ArrayList<Integer> getValuesWithMatchingFirstBitSet(HashMap<Pair<BitSet, BitSet>, Integer> map, BitSet target) {
        ArrayList<Integer> result = new ArrayList<>();
        for (Pair<BitSet, BitSet> key : map.keySet()) {
            if (key.getKey().equals(target)) {
                result.add(map.get(key));
            }
        }
        return result;
    }

    private Pair<BitSet, BitSet> getPairFromValue(HashMap<Pair<BitSet, BitSet>, Integer> map, Integer value) {
        for (HashMap.Entry<Pair<BitSet, BitSet>, Integer> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Acceptable Correlated Equilibria Z3
    @Override
    public EquilibriumResult computeEquilibrium(HashMap<BitSet, ArrayList<Double>> utilities,
                                                ArrayList<ArrayList<HashMap<BitSet, Double>>> ce_constraints,
                                                ArrayList<ArrayList<Integer>> strategies, HashMap<BitSet, Integer> ce_var_map, int crit) {

//        System.out.println(utilities);
        EquilibriumResult result = new EquilibriumResult();
        Distribution<Double> d = new Distribution<>();
        ArrayList<Double> payoffs_result = new ArrayList<Double>();
        ArrayList<Distribution<Double>> strategy_result = new ArrayList<>();
        ArithExpr expr;
        solver.Push();


        // previously we had a map from joint actions to variables HashMap<BitSet, Integer>
        // now we need a tuple of joint action and trembling action to integer HashMap<Pair<BitSet, BitSet>, Integer>
        HashMap<Pair<BitSet, BitSet>, Integer> epsilonCeVarMap = new HashMap<>();


        // GABRIEL CODE SECTION #########################################################
        expr = ctx.mkInt(0);
        // Builds a payoff expression for each player, and one for the sum of all payoffs
        for (int i = 0; i < n_coalitions; i++) {
            payoffs[i] = zero;
        }
        // iterating over all joint actions
        for (BitSet e : utilities.keySet()) {
            // payoff of joint action
            double u = 0.0;
            for (int c = 0; c < n_coalitions; c++) {
                u += utilities.get(e).get(c);
                // we need the individual payoffs later for the lower priority objectives
                payoffs[c] = ctx.mkAdd(payoffs[c], ctx.mkMul(vars[ce_var_map.get(e)], ctx.mkReal(String.valueOf(utilities.get(e).get(c)))));
            }
            expr = ctx.mkAdd(expr, ctx.mkMul(vars[ce_var_map.get(e)], ctx.mkReal(String.valueOf(u))));
        }
        for (int c = 0; c < n_coalitions; c++) {
            payoff_vars[c] = ctx.mkRealConst("p" + c);
            solver.Add(ctx.mkEq(payoff_vars[c], payoffs[c]));
        }

        // In case of the fair variant (minimise the difference between the largest and smallest payoffs)
        // we first need to add auxiliary constraints
        // The default case is social-welfare, in which we just maximise the sum
        switch (crit) {
            case CSGModelCheckerEquilibria.FAIR : {
                RealExpr ph;
                RealExpr pl;
                ph = ctx.mkRealConst("ph"); // Highest payoff
                pl = ctx.mkRealConst("pl"); // Lowest payoff
                BoolExpr bh;
                BoolExpr bl;
                // Additional constraints for fair
                for (int i = 0; i < n_coalitions; i++) {
                    bh = ctx.mkTrue();
                    bl = ctx.mkTrue();
                    for (int j = 0; j < n_coalitions; j++) {
                        if (i != j) {
                            bh = ctx.mkAnd(bh, ctx.mkGe(payoffs[i], payoffs[j]));
                            bl = ctx.mkAnd(bl, ctx.mkLe(payoffs[i], payoffs[j]));
                        }
                    }
                    solver.Add(ctx.mkImplies(bh, ctx.mkEq(ph, payoffs[i])));
                    solver.Add(ctx.mkImplies(bl, ctx.mkEq(pl, payoffs[i])));
                }
                solver.MkMinimize(ctx.mkSub(ph, pl));
                break;
            }
            default : {
                // Primary objective is maximising the sum
                solver.Add(ctx.mkEq(obj_var, expr));
                solver.MkMaximize(expr);
            }
        }

        // Lower priority objectives of maximising the payoff of each player in decreasing order
        for (int c = 0; c < n_coalitions; c++) {
            solver.MkMaximize(payoffs[(c)]);
        }

        // GABRIEL CODE SECTION END ########################################################




//        System.out.println("all actions available in this game: " + strategies.toString());

        // for each player i
        for (int currentPlayerIndex = 0; currentPlayerIndex < n_coalitions; currentPlayerIndex++) {
//            System.out.println("\ti ∈ N: " + currentPlayerIndex);

            // list of other players (not including playerIndex)
            // N_{-i}
            ArrayList<Integer> playerList = IntStream.range(0, n_coalitions)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            playerList.remove(currentPlayerIndex);

            // list of all possible subsets (of other players trembling)
            // S ⊆ N_{-i}
            ArrayList<ArrayList<Integer>> tremblePlayerSubsets = getSubsets(playerList);

            // sanity check printing
//            System.out.println("\tS ⊆ N_{-" + currentPlayerIndex + "}: " + tremblePlayerSubsets);

            // c_i ∈ C_i
            for (int actionIndex = 0; actionIndex < strategies.get(currentPlayerIndex).size(); actionIndex++) {
//                System.out.println("\t\tc_" + currentPlayerIndex + " ∈ C_" + currentPlayerIndex + ": " + strategies.get(currentPlayerIndex).get(actionIndex));

                // e_i ∈ C_i
                for (int deviationIndex = 0; deviationIndex < strategies.get(currentPlayerIndex).size(); deviationIndex++) {
                    if (actionIndex == deviationIndex) {
                        continue;
                    }

//                    System.out.println("\t\te_" + currentPlayerIndex + " ∈ C_" + currentPlayerIndex + ": " + strategies.get(currentPlayerIndex).get(deviationIndex));

                    expr = ctx.mkInt(0);

                    // c_{-i} ∈ C_{-i}
                    for (BitSet opponentAction : ce_constraints.get(currentPlayerIndex).get(actionIndex).keySet()) {
//                        System.out.println("\t\t\tc_{-" + currentPlayerIndex + "} ∈ C_{-" + currentPlayerIndex + "}: " + opponentAction);

                        BitSet ciAction = new BitSet();
                        ciAction.set(strategies.get(currentPlayerIndex).get(actionIndex));

                        BitSet eiAction = new BitSet();
                        eiAction.set(strategies.get(currentPlayerIndex).get(deviationIndex));

                        ciAction.or(opponentAction);
                        eiAction.or(opponentAction);

                        BitSet ciActionClone = new BitSet();
                        ciActionClone.or(ciAction);

                        BitSet eiActionClone = new BitSet();
                        eiActionClone.or(eiAction);

                        // S ⊆ N_{-i}
                        for (ArrayList<Integer> tremblePlayerSubset : tremblePlayerSubsets) {

//                            System.out.println("\t\t\t\tS: " + tremblePlayerSubset);

                            // pre-computation which is needed later when computing bitset (c_{-S}, e_S)
                            BitSet tremblePlayersAvailableSet = getPlayersAvailableSet(strategies, tremblePlayerSubset);

                            // C_S
                            ArrayList<ArrayList<Integer>> trembleActions = getTrembleActions(tremblePlayerSubset, strategies);

                            // e_S ∈ C_S
                            for (ArrayList<Integer> trembleAction : trembleActions) {

                                // convert ArrayList<Integer> to Bitset
                                BitSet eS = new BitSet();
                                for (Integer index : trembleAction) {
                                    eS.set(index);
                                }

//                                System.out.println("\t\t\t\t\te_S: "+ eS);



                                // computing bitset (c_{-S}, e_S)
                                // step 1: removing all possible actions of players in S
                                ciAction.andNot(tremblePlayersAvailableSet);
                                // step 2: injecting vector e_S
                                ciAction.or(eS);

                                // computing bitset (c_{-S ∪ {i}}, e_S ∪ {i})
                                // step 1: removing all possible actions of players in S ∪ {i}
                                eiAction.andNot(tremblePlayersAvailableSet);
                                // step 2: inject eS with S = S
                                eiAction.or(eS);

                                if (ciAction.equals(ciActionClone)) {
//                                    continue;
                                }
                                // η(c,e_S)
                                if (!epsilonCeVarMap.containsKey(new Pair<>(ciActionClone, eS))) {
                                    // if we discovered a new variable, add it to the list
                                    epsilonCeVarMap.put(new Pair<>(ciActionClone, eS), epsilonCeVarMap.size());
                                }

//                                System.out.println("\t\t\t\t\t\t\tη(c,e_S): \t\t\t\t\tη("+ ciActionClone + "," + eS + ")");
//                                System.out.println("\t\t\t\t\t\t\t(c_{-S}, e_S): \t\t\t\t" + ciAction);
//                                System.out.println("\t\t\t\t\t\t\t(c_{-S ∪ {i}}, e_S ∪ {i}): \t" + eiAction);
//                                System.out.println(ciAction + " " + utilities.get(ciAction).get(currentPlayerIndex));
//                                System.out.println(eiAction + " " + utilities.get(eiAction).get(currentPlayerIndex));

                                expr = ctx.mkAdd(expr, ctx.mkMul(vars[epsilonCeVarMap.get(new Pair<>(ciActionClone, eS))],
                                        ctx.mkSub(ctx.mkReal(String.valueOf(utilities.get(ciAction).get(currentPlayerIndex))),
                                                ctx.mkReal(String.valueOf(utilities.get(eiAction).get(currentPlayerIndex))))));
//                                System.out.println("------------------------------------------------------");
//                                System.out.println(expr);
//                                System.out.println("------------------------------------------------------");
//                                System.out.println(epsilonCeVarMap);
                            }
                        }
                    }
//                    System.out.println("Intermediate expr: -----------------------------------");
//                    System.out.println(expr);
//                    System.out.println("------------------------------------------------------");
                    solver.Add(ctx.mkGe(expr, zero));

                }
            }
        }



        RealExpr epsilon = ctx.mkRealConst("epsilon");
        // currently not supported with Z3
//        solver.Add(ctx.mkGt(epsilon, zero));
//        solver.Add(ctx.mkGe(one, epsilon));
//        solver.MkMinimize(epsilon);

        // set epsilon to 10e-6
        solver.Add(ctx.mkEq(epsilon, ctx.mkReal(""+ 10e-6)));


        // constraint 2.3
        // for each joint outcome
        for (BitSet jointOutcome : utilities.keySet()) {
            // for each player
            for (int currentPlayerIndex = 0; currentPlayerIndex < n_coalitions; currentPlayerIndex++) {

                // computing all possible subsets of other players
                ArrayList<Integer> playerList = IntStream.range(0, n_coalitions)
                        .boxed()
                        .collect(Collectors.toCollection(ArrayList::new));
                playerList.remove(currentPlayerIndex);

                // list of all possible subsets (of other players trembling)
                // S ⊆ N_{-i}
                ArrayList<ArrayList<Integer>> tremblePlayerSubsets = getSubsets(playerList);

                // for each subset S
                for (ArrayList<Integer> tremblePlayerSubset: tremblePlayerSubsets) {

                    // computing C_S
                    ArrayList<ArrayList<Integer>> trembleActions = getTrembleActions(tremblePlayerSubset, strategies);

                    // for each action e_S ∈ C_S
                    for (ArrayList<Integer> trembleAction : trembleActions){

                        // convert ArrayList<Integer> to Bitset
                        BitSet eS = new BitSet();
                        for (Integer index : trembleAction) {
                            eS.set(index);
                        }

                        ArithExpr sum = ctx.mkInt(0);
                        StringBuilder stringSum = new StringBuilder();
                        // for all actions of player i
                        for (int actionIndex = 0; actionIndex < strategies.get(currentPlayerIndex).size(); actionIndex++) {
                            BitSet eSi = new BitSet();
                            eSi.set(strategies.get(currentPlayerIndex).get(actionIndex));

                            // computing e_S u {i}
                            eSi.or(eS);

                            if (!epsilonCeVarMap.containsKey(new Pair<>(jointOutcome, eSi))) {
                                // if we discovered a new variable, add it to the list
                                epsilonCeVarMap.put(new Pair<>(jointOutcome, eSi), epsilonCeVarMap.size());
                            }

                            sum = ctx.mkAdd(sum, vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eSi))]);
                            stringSum.append(" (").append(jointOutcome).append(", ").append(eSi).append(") +");

//                            System.out.println(epsilonCeVarMap);
//                            System.out.println(jointOutcome);
//                            System.out.println(eS);

                            // constraint 2.4
                            if (!epsilonCeVarMap.containsKey(new Pair<>(jointOutcome, eS))) {
                                // if we discovered a new variable, add it to the list
                                epsilonCeVarMap.put(new Pair<>(jointOutcome, eS), epsilonCeVarMap.size());
                            }

                            solver.Add(ctx.mkImplies(ctx.mkGt(vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eS))], zero),
                                    ctx.mkGt(vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eSi))], zero)));

//                            System.out.println("IMPL: " + jointOutcome + ", " + eS + " > 0 => " + jointOutcome + ", " + eSi + " > 0");

                        }

                        ArithExpr leftSide = ctx.mkMul(epsilon, vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eS))]);
                        ArithExpr rightSide = ctx.mkMul(ctx.mkSub(ctx.mkInt(1),epsilon), sum);

//                        System.out.println("SUM: epsilon * (" + jointOutcome + ", " + eS + ") >=  (1-epsilon) * " + stringSum);

                        solver.Add(ctx.mkGe(leftSide, rightSide));
                    }
                }
            }
        }


        // all variables should sum up to 1
        expr = ctx.mkInt(0);
        for (int c = 0; c < vars.length; c++) {
            expr = ctx.mkAdd(expr, vars[c]);
        }
        solver.Add(ctx.mkEq(expr, one));


        // set all unused variables to zero
        for (int i = epsilonCeVarMap.size(); i < vars.length; i++) {
            solver.Add(ctx.mkEq(vars[i], zero));
        }


//        solver.Add(ctx.mkEq(vars[3], zero));
//        solver.Add(ctx.mkEq(vars[6], zero));
//        solver.Add(ctx.mkEq(vars[0], zero));
//        solver.Add(ctx.mkEq(vars[1], zero));
//        solver.Add(ctx.mkEq(vars[2], zero));
//        solver.Add(ctx.mkEq(vars[14], zero));
//        solver.Add(ctx.mkGt(vars[0], zero));


        Status solverStatus = solver.Check();
//        System.out.println(solverStatus);
        Model model = solver.getModel();
        if (solverStatus == Status.UNKNOWN) {
            System.out.println(solver.getReasonUnknown());
            if (model != null) {
                for (BoolExpr constraint : solver.getAssertions()) {
                    if (!model.eval(constraint, false).isTrue()) {
                        System.out.println("Unsatisfied constraint: " + constraint);
                    }
                }
            }
            result.setStatus(CSGModelCheckerEquilibria.CSGResultStatus.UNSAT);
        } else if (solver.Check() == Status.UNSATISFIABLE) {
            for (BoolExpr constraint : solver.getAssertions()) {
                if (!model.eval(constraint, false).isTrue()) {
                    System.out.println("Unsatisfied constraint: " + constraint);
                }
            }
            result.setStatus(CSGModelCheckerEquilibria.CSGResultStatus.UNSAT);
        } else {

//            double[] payoffs = new double[n_coalitions];
//            Arrays.fill(payoffs, 0.0);
//
//
//            // SATISFIABLE
//            int stratIndex = 0;
//            for (BitSet outcome: utilities.keySet()) {
//                ArrayList<Integer> varList = getValuesWithMatchingFirstBitSet(epsilonCeVarMap, outcome);
//
//                for (int i: varList){
//                    d.add(stratIndex, getDoubleValue(model, vars[i]));
//                }
//                stratIndex++;
//            }
//
//            for (int i = 0; i < vars.length; i++){
//                double value = getDoubleValue(model, vars[i]);
//
//
//                if (value > 0) {
//                    System.out.println(getPairFromValue(epsilonCeVarMap, i) + ": " + value);
//                }
//            }
//            // printing epsilon
//            System.out.println("epsilon: " + getDoubleValue(model, epsilon));

//            System.out.println("Found optimal solution!");
            HashMap<BitSet, Double> robustStrategy = new HashMap<>();

//            for (int i = 0; i < vars.length; i++) {
//                if (getDoubleValue(model, vars[i]) > 0) {
//                    System.out.println(getPairFromValue(epsilonCeVarMap, i) + " " + getDoubleValue(model, vars[i]));
//                }
//            }

            double[] strat_payoffs = new double[n_coalitions];
            Arrays.fill(strat_payoffs, 0.0);

            // iterating over all joint outcomes
            for (BitSet c: ce_var_map.keySet()) {
                // printing reward
//                System.out.println(c + " " + utilities.get(c));

                ArrayList<Integer> cVars = getValuesWithMatchingFirstBitSet(epsilonCeVarMap, c);

                double prop = 0.0;
                for (Integer cVar: cVars) {
                    prop += getDoubleValue(model, vars[cVar]);
                }
                d.add(ce_var_map.get(c), prop);
                robustStrategy.put(c, prop);
                for (int i = 0; i < n_coalitions; i++) {
                    strat_payoffs[i] += utilities.get(c).get(i) * prop;
                }
            }

            // typecast payoff to payoff_result
            for (double payoff : strat_payoffs) {
                payoffs_result.add(payoff);
            }


//            System.out.println("robust strategy: " + robustStrategy);
//            System.out.println("epsilon: " + getDoubleValue(model, epsilon));
//            System.out.println("total reward: "+ Arrays.stream(strat_payoffs).sum());
            result.setStatus(CSGModelCheckerEquilibria.CSGResultStatus.SAT);
            result.setPayoffVector(payoffs_result);
            strategy_result.add(d);
            result.setStrategy(strategy_result);
        }

        // we don't need this following part anymore since epsilon is an ordinal rating
        // manual iteration to find epsilon
//        double epsilonValue = 1.0;
//        double decreaseFactor = 0.5;  // Example: halving epsilon in each iteration.
//
//        while (true) {
//            solver.Push();
//            solver.Add(ctx.mkEq(epsilon, ctx.mkReal(String.valueOf(epsilonValue))));
//
//            if (solver.Check() != Status.SATISFIABLE) {
//                System.out.println("Model became UNSAT with epsilon = " + epsilonValue);
//                break;  // Exit the loop if the model is not satisfiable.
//            }
//
//            // If still satisfiable, prepare for next iteration.
//            solver.Pop();
//
//            epsilonValue *= decreaseFactor;  // Decrease the value of epsilon.
//        }


//         printing var map
//        for (int i = 0; i < epsilonCeVarMap.size(); i++) {
//            System.out.println(i + " " + getPairFromValue(epsilonCeVarMap, i));
//        }

        solver.Pop();

        return result;
    }

    private BitSet getPlayersAvailableSet(ArrayList<ArrayList<Integer>> strategies, ArrayList<Integer> tremblePlayerSubset) {
        BitSet tremblePlayersAvailableSet = new BitSet();
        for (int tremblePlayerIndex : tremblePlayerSubset) {
            // get all possible actions
            ArrayList<Integer> tremblePlayersAvailableActions = strategies.get(tremblePlayerIndex);

            for (Integer index : tremblePlayersAvailableActions) {
                tremblePlayersAvailableSet.set(index);
            }
        }
        return tremblePlayersAvailableSet;
    }


    /**
     * Computes all possible subsets of a given list
     * (right now used to generate all possible subset of players)
     */
    public static <T> ArrayList<ArrayList<T>> getSubsets(ArrayList<T> originalList) {
        ArrayList<ArrayList<T>> subsets = new ArrayList<>();

        if (originalList.isEmpty()) {
            subsets.add(new ArrayList<>());
            return subsets;
        }

        T firstElement = originalList.get(0);
        ArrayList<T> remainingList = new ArrayList<>(originalList.subList(1, originalList.size()));

        for (ArrayList<T> subset : getSubsets(remainingList)) {
            // Add without the first element
            subsets.add(new ArrayList<>(subset));

            // Add with the first element
            ArrayList<T> newSubset = new ArrayList<>(subset);
            newSubset.add(0, firstElement);
            subsets.add(newSubset);
        }
        return subsets;
    }

    /**
     * Return a double value for a given expression (usually variable), converting from BigInt fractions
     *
     * @param model The SMT model
     * @param expr  The SMT expression
     * @return
     */
    public double getDoubleValue(Model model, Expr expr) {
        RatNum v1;
        AlgebraicNum v2;
        if (model.getConstInterp(expr) instanceof RatNum) {
            v1 = (RatNum) model.getConstInterp(expr);
            return (Double) (v1.getBigIntNumerator().doubleValue() / v1.getBigIntDenominator().doubleValue());
        } else if (model.getConstInterp(expr) instanceof AlgebraicNum) {
            v2 = (AlgebraicNum) model.getConstInterp(expr);
            v1 = v2.toUpper(12);
            return (Double) (v1.getBigIntNumerator().doubleValue() / v1.getBigIntDenominator().doubleValue());
        } else
            return Double.NaN;
    }


    public String getSolverName() {
        return name;
    }

    @Override
    public void clear() {

    }

    @Override
    public void printModel() {
        // TODO Auto-generated method stub

    }

}