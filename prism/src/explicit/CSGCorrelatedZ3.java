//==============================================================================
//
//	Copyright (c) 2020-
//	Authors:
//	* Gabriel Santos <gabriel.santos@cs.ox.ac.uk> (University of Oxford)
//
//------------------------------------------------------------------------------
//
//	This file is part of PRISM.
//
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//==============================================================================

package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.microsoft.z3.*;

import com.microsoft.z3.Model;
import prism.Pair;

/**
 * Z3-based implementation for correlated equilibria computation
 *
 * @author Gabriel Santos
 */
public class CSGCorrelatedZ3 implements CSGCorrelated {

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

    /**
     * Creates a new CSGCorrelatedZ3 (without initialisation)
     */
    public CSGCorrelatedZ3() {
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
    public CSGCorrelatedZ3(int n_entries, int n_coalitions) {
        cfg = new HashMap<String, String>();
        cfg.put("model", "true");
        cfg.put("auto_config", "true");
        ctx = new Context(cfg);
        solver = ctx.mkOptimize();
        name = Version.getFullVersion();
        zero = ctx.mkInt(0);
        one = ctx.mkInt(1);
        vars = new RealExpr[n_entries * n_entries * n_entries + 1];
        payoffs = new ArithExpr[n_coalitions];
        this.n_coalitions = n_coalitions;

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

    public EquilibriumResult computeEpsilonCorrelatedEquilibrium(HashMap<BitSet, ArrayList<Double>> utilities,
                                                                 ArrayList<ArrayList<HashMap<BitSet, Double>>> ce_constraints,
                                                                 ArrayList<ArrayList<Integer>> strategies) {

        EquilibriumResult result = new EquilibriumResult();
        Distribution d = new Distribution();
        ArrayList<Double> payoffs_result = new ArrayList<Double>();
        ArrayList<Distribution> strategy_result = new ArrayList<>();
        int counter = 0;
        ArithExpr expr;
        solver.Push();


        // previously we had a map from joint actions to variables HashMap<BitSet, Integer>
        // now we need a tuple of joint action and trembling action to integer HashMap<Pair<BitSet, BitSet>, Integer>
        HashMap<Pair<BitSet, BitSet>, Integer> epsilonCeVarMap = new HashMap<>();


        System.out.println("all actions available in this game: " + strategies.toString());

        // for each player i
        for (int currentPlayerIndex = 0; currentPlayerIndex < n_coalitions; currentPlayerIndex++) {
            System.out.println("\ti ∈ N: " + currentPlayerIndex);

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
                System.out.println("\t\tc_" + currentPlayerIndex + " ∈ C_" + currentPlayerIndex + ": " + strategies.get(currentPlayerIndex).get(actionIndex));

                // e_i ∈ C_i
                for (int deviationIndex = 0; deviationIndex < strategies.get(currentPlayerIndex).size(); deviationIndex++) {
                    if (actionIndex == deviationIndex) {
                        continue;
                    }

                    System.out.println("\t\te_" + currentPlayerIndex + " ∈ C_" + currentPlayerIndex + ": " + strategies.get(currentPlayerIndex).get(deviationIndex));

                    expr = ctx.mkInt(0);

                    // c_{-i} ∈ C_{-i}
                    for (BitSet opponentAction : ce_constraints.get(currentPlayerIndex).get(actionIndex).keySet()) {
                        System.out.println("\t\t\tc_{-" + currentPlayerIndex + "} ∈ C_{-" + currentPlayerIndex + "}: " + opponentAction);

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

                            System.out.println("\t\t\t\tS: " + tremblePlayerSubset);

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

                                System.out.println("\t\t\t\t\te_S: "+ eS);

                                // η(c,e_S)
                                if (!epsilonCeVarMap.containsKey(new Pair<>(ciActionClone, eS))) {
                                    // if we discovered a new variable, add it to the list
                                    epsilonCeVarMap.put(new Pair<>(ciActionClone, eS), epsilonCeVarMap.size());
                                }

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

                                System.out.println("\t\t\t\t\t\t\tη(c,e_S): \t\t\t\t\tη("+ ciActionClone + "," + eS + ")");
                                System.out.println("\t\t\t\t\t\t\t(c_{-S}, e_S): \t\t\t\t" + ciAction);
                                System.out.println("\t\t\t\t\t\t\t(c_{-S ∪ {i}}, e_S ∪ {i}): \t" + eiAction);
                                System.out.println(ciAction + " " + utilities.get(ciAction).get(currentPlayerIndex));
                                System.out.println(eiAction + " " + utilities.get(eiAction).get(currentPlayerIndex));

                                expr = ctx.mkAdd(expr, ctx.mkMul(vars[epsilonCeVarMap.get(new Pair<>(ciActionClone, eS))],
                                        ctx.mkSub(ctx.mkReal(String.valueOf(utilities.get(ciAction).get(currentPlayerIndex))),
                                                ctx.mkReal(String.valueOf(utilities.get(eiAction).get(currentPlayerIndex))))));
                                System.out.println("------------------------------------------------------");
                                System.out.println(expr);
                                System.out.println("------------------------------------------------------");
                                System.out.println(epsilonCeVarMap);
                            }
                        }
                    }
                    System.out.println("Intermediate expr: -----------------------------------");
                    System.out.println(expr);
                    System.out.println("------------------------------------------------------");
                    solver.Add(ctx.mkGe(expr, zero));

                }
            }
        }

        System.out.println(solver);


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




        // TODO: remove this later
        // DEBUG
        for (Pair<BitSet, BitSet> keySet : epsilonCeVarMap.keySet()) {
            // excluding ({0,1}{x}) for all x from the solution
            if (keySet.first.get(0) && keySet.first.get(1)) {
                solver.Add(ctx.mkEq(vars[epsilonCeVarMap.get(keySet)], zero));
            }
            // excluding ({x,y}{0}) for all x,y from the solution
            // excluding ({x,y}{1}) for all x,y from the solution
            if (keySet.second.get(0)&& keySet.first.get(1)) {
                solver.Add(ctx.mkEq(vars[epsilonCeVarMap.get(keySet)], zero));
            }
        }


        int tmp = 30;
        while (solver.Check() == Status.SATISFIABLE) {
            Model model = solver.getModel();
            if (tmp == 0) {

                break;
            }
            tmp--;

            // Create a blocking clause to exclude the current model
            BoolExpr blockingClause = ctx.mkFalse();
            ArrayList<BoolExpr> usedVariables = new ArrayList<>();


            for (FuncDecl constant : model.getConstDecls()) {
                Expr constantValue = model.getConstInterp(constant);
                if (!constantValue.toString().equals("0")) {
                    System.out.println(constant.getName() + " = " + constantValue);
                    solver.Add(ctx.mkEq(constant.apply(), zero));
                }
                BoolExpr currentNotEqual = ctx.mkNot(ctx.mkEq(constant.apply(), constantValue));
                blockingClause = ctx.mkOr(blockingClause, currentNotEqual);
            }
//            BoolExpr conjunction = ctx.mkAnd(usedVariables.toArray(new BoolExpr[0]));
//            BoolExpr exclusionClause = ctx.mkNot(conjunction);

            // Add the blocking clause to the solver
            System.out.println("----------------------------------");
//            solver.Add(blockingClause);
//            solver.Add(exclusionClause);
//            solver.Add(ctx.mkEq(vars[0], zero));
//            solver.Add(ctx.mkEq(vars[1], zero));
        }

        System.out.println(epsilonCeVarMap);


//        System.out.println(solver);
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

    public EquilibriumResult computeEquilibrium(HashMap<BitSet, ArrayList<Double>> utilities,
                                                ArrayList<ArrayList<HashMap<BitSet, Double>>> ce_constraints,
                                                ArrayList<ArrayList<Integer>> strategies,
                                                HashMap<BitSet, Integer> ce_var_map, int type) {


            return computeEpsilonCorrelatedEquilibrium(utilities, ce_constraints, strategies);


//        EquilibriumResult result = new EquilibriumResult();
//        ArrayList<Double> payoffs_result = new ArrayList<Double>();
//        ArrayList<Distribution> strategy_result = new ArrayList<>();
//        Distribution d = new Distribution();
//        ArithExpr expr;
//        BitSet is, js;
//        double u;
//        int c, q, r;
//        is = new BitSet();
//        js = new BitSet();
//
//        solver.Push();
//        expr = ctx.mkInt(0);
//
//        // Builds a payoff expression for each player, and one for the sum of all payoffs
//        for (int i = 0; i < n_coalitions; i++) {
//            payoffs[i] = zero;
//        }
//        // iterating over all joint actions
//        for (BitSet e : utilities.keySet()) {
//            // payoff of joint action
//            u = 0.0;
//            for (c = 0; c < n_coalitions; c++) {
//                u += utilities.get(e).get(c);
//                // we need the individual payoffs later for the lower priority objectives
//                payoffs[c] = ctx.mkAdd(payoffs[c], ctx.mkMul(vars[ce_var_map.get(e)], ctx.mkReal(String.valueOf(utilities.get(e).get(c)))));
//            }
//            expr = ctx.mkAdd(expr, ctx.mkMul(vars[ce_var_map.get(e)], ctx.mkReal(String.valueOf(u))));
//        }
//        for (c = 0; c < n_coalitions; c++) {
//            payoff_vars[c] = ctx.mkRealConst("p" + c);
//            solver.Add(ctx.mkEq(payoff_vars[c], payoffs[c]));
//        }
//
////		System.out.println(s.toString());
//
//
//        // In case of the fair variant (minimise the difference between the largest and smallest payoffs)
//        // we first need to add auxiliary constraints
//        // The default case is social-welfare, in which we just maximise the sum
//        switch (type) {
//            case CSGModelCheckerEquilibria.FAIR: {
//                RealExpr ph;
//                RealExpr pl;
//                ph = ctx.mkRealConst("ph"); // Highest payoff
//                pl = ctx.mkRealConst("pl"); // Lowest payoff
//                BoolExpr bh;
//                BoolExpr bl;
//                // Additional constraints for fair
//                for (int i = 0; i < n_coalitions; i++) {
//                    bh = ctx.mkTrue();
//                    bl = ctx.mkTrue();
//                    for (int j = 0; j < n_coalitions; j++) {
//                        if (i != j) {
//                            bh = ctx.mkAnd(bh, ctx.mkGe(payoffs[i], payoffs[j]));
//                            bl = ctx.mkAnd(bl, ctx.mkLe(payoffs[i], payoffs[j]));
//                        }
//                    }
//                    solver.Add(ctx.mkImplies(bh, ctx.mkEq(ph, payoffs[i])));
//                    solver.Add(ctx.mkImplies(bl, ctx.mkEq(pl, payoffs[i])));
//                }
//                solver.MkMinimize(ctx.mkSub(ph, pl));
//                break;
//            }
//            default: {
//                // Primary objective is maximising the sum
//                solver.Add(ctx.mkEq(obj_var, expr));
//                solver.MkMaximize(expr);
//            }
//        }
//
//        // Lower priority objectives of maximising the payoff of each player in decreasing order
//        for (c = 0; c < n_coalitions; c++) {
//            solver.MkMaximize(payoffs[(c)]);
//        }
//
//        // Constraints for correlated equilibria
//        // for each action of coalition c
//        for (c = 0; c < n_coalitions; c++) {
//            // strategies contains for each coalition, the indices of all coalition actions in s
//            // for each action of coalition c
//            for (q = 0; q < strategies.get(c).size(); q++) {
//                expr = zero;
//                is.clear();
//                // storing current action
//                is.set(strategies.get(c).get(q));
//
//                // for each action of coalition c
//                for (r = 0; r < strategies.get(c).size(); r++) {
//                    // empty bitset
//                    js.clear();
//
//                    // for each possible payoff of coalition c after playing action q
//                    for (BitSet e : ce_constraints.get(c).get(q).keySet()) {
//                        // bitset "is" is now containing
//                        is.or(e);
//                        js.or(e);
//                        if (q != r) {
//                            js.set(strategies.get(c).get(r));
//                            expr = ctx.mkAdd(expr, ctx.mkMul(vars[ce_var_map.get(is)],
//                                    ctx.mkSub(ctx.mkReal(String.valueOf(utilities.get(is).get(c))),
//                                            ctx.mkReal(String.valueOf(utilities.get(js).get(c))))));
//                        }
//                        is.andNot(e);
//                        js.andNot(e);
//                    }
//                    if (q != r) {
//                        solver.Add(ctx.mkGe(expr, zero));
//                    }
//                }
//            }
//        }
//
//        // Sets unused variables to zero
//        for (c = utilities.size(); c < vars.length; c++) {
//            solver.Add(ctx.mkEq(vars[c], zero));
//        }
//
//        expr = ctx.mkInt(0);
//        for (c = 0; c < vars.length; c++) {
//            expr = ctx.mkAdd(expr, vars[c]);
//        }
//        solver.Add(ctx.mkEq(expr, one));
//
//
////		for (BoolExpr e : s.getAssertions()) {
////			System.out.println(e);
////		}
//
//
//        // If and optimal solution is found, set the values and strategies
//        if (solver.Check() == Status.SATISFIABLE) {
//            for (c = 0; c < vars.length; c++) {
//                d.add(c, getDoubleValue(solver.getModel(), vars[c]));
//            }
//            for (c = 0; c < payoff_vars.length; c++) {
//                payoffs_result.add(getDoubleValue(solver.getModel(), payoff_vars[c]));
//            }
//            result.setStatus(CSGModelCheckerEquilibria.CSGResultStatus.SAT);
//            result.setPayoffVector(payoffs_result);
//            strategy_result.add(d);
//            result.setStrategy(strategy_result);
//        } else {
//            result.setStatus(CSGModelCheckerEquilibria.CSGResultStatus.UNSAT);
//        }
//
//        solver.Pop();
//        return result;
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
