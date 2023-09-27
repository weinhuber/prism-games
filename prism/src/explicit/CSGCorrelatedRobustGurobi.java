//==============================================================================
//
//	Copyright (c) 2023-
//	Authors:
//	* Christoph Weinhuber <christoph.weinhuber@cs.ox.ac.uk> (University of Oxford)
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
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import prism.Pair;

import gurobi.*;

/**
 * Gurobi-based implementation for accepting correlated equilibria
 *
 * @author Christoph Weinhuber
 */
public class CSGCorrelatedRobustGurobi implements CSGCorrelated {

    private GRBVar[] vars;
    private final int n_coalitions;

    private GRBModel model;

    private String name = "Gurobi";


    /**
     * Creates a new CSGCorrelatedZ3
     *
     * @param n_entries    Number of entries in the utility table
     * @param n_coalitions Number of coalitions
     */

    static {
        try {
            System.loadLibrary("gurobi100");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native code library failed to load.\n" + e);
            System.exit(1);
        }
    }
    public CSGCorrelatedRobustGurobi(int n_entries, int n_coalitions) throws GRBException {
        this.n_coalitions = n_coalitions;
        GRBEnv env = new GRBEnv(true);
        env.set("NonConvex", "2");
        env.set("Quad", "1");
        env.start();
        GRBModel model = new GRBModel(env);
        GRBVar[] vars = new GRBVar[n_entries * n_entries * n_entries + 1];

        for (int i = 0; i < vars.length; i++) {
            // Add a continuous variable named "v" + i, with lower bound 0 and upper bound 1
            vars[i] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "v" + i);
        }

        model.update();

        this.model = model;
        this.vars = vars;

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


    // Acceptable Correlated Equilibria Gurobi
    public EquilibriumResult computeRobustCorrelatedEquilibrium(HashMap<BitSet, ArrayList<Double>> utilities,
                                                                ArrayList<ArrayList<HashMap<BitSet, Double>>> ce_constraints,
                                                                ArrayList<ArrayList<Integer>> strategies) throws GRBException {

        EquilibriumResult result = new EquilibriumResult();

        // previously we had a map from joint actions to variables HashMap<BitSet, Integer>
        // now we need a tuple of joint action and trembling action to integer HashMap<Pair<BitSet, BitSet>, Integer>
        HashMap<Pair<BitSet, BitSet>, Integer> epsilonCeVarMap = new HashMap<>();

        // list of variables for lower priority objectives (to guarantee that uniqueness of solution)
        ArrayList<GRBLinExpr> payoff_vars = new ArrayList<>();

        for (int i = 0; i < n_coalitions; i++) {
            payoff_vars.add(new GRBLinExpr());
        }


        System.out.println("all actions available in this game: " + strategies.toString());

        // 1 >= epsilon > 0
        GRBVar epsilon = model.addVar(Double.MIN_VALUE, 1.0, 0.0, GRB.CONTINUOUS, "epsilon");

//        GRBVar minDouble = model.addVar(0.000009, 1.0, 0.0, GRB.CONTINUOUS, "minDouble");
//        model.addConstr(minDouble, GRB.EQUAL, Double.MIN_VALUE, "minDoubleConstraint");
//        model.update();

//        GRBLinExpr minDoubleExpr = new GRBLinExpr();
//        minDoubleExpr.addTerm(1, minDouble);


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

                    GRBLinExpr expr = new GRBLinExpr();


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

                                System.out.println("\t\t\t\t\t\t\tη(c,e_S): \t\t\t\t\tη("+ ciActionClone + "," + eS + ")");
                                System.out.println("\t\t\t\t\t\t\t(c_{-S}, e_S): \t\t\t\t" + ciAction);
                                System.out.println("\t\t\t\t\t\t\t(c_{-S ∪ {i}}, e_S ∪ {i}): \t" + eiAction);
                                System.out.println(ciAction + " " + utilities.get(ciAction).get(currentPlayerIndex));
                                System.out.println(eiAction + " " + utilities.get(eiAction).get(currentPlayerIndex));

                                GRBVar currentVar = vars[epsilonCeVarMap.get(new Pair<>(ciActionClone, eS))];

                                double utilityDiff = utilities.get(ciAction).get(currentPlayerIndex)
                                        - utilities.get(eiAction).get(currentPlayerIndex);

                                GRBLinExpr tempExpr = new GRBLinExpr();
                                tempExpr.addTerm(utilityDiff, currentVar);

                                expr.add(tempExpr);

                                // lower priority objective: maximise payoff of player i
                                payoff_vars.get(currentPlayerIndex).addTerm(utilities.get(ciAction).get(currentPlayerIndex), currentVar);

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
                    model.addConstr(expr, GRB.GREATER_EQUAL, 0.0, "CEconstraint" + currentPlayerIndex + "_" + actionIndex + "_" + deviationIndex);
                    model.update();
                }
            }
        }



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
                        StringBuilder stringSum = new StringBuilder();

                        // ϵ * η(c,e_S)
                        GRBQuadExpr lhs = new GRBQuadExpr();
                        lhs.addTerm(1.0, epsilon, vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eS))]);

                        // (1-ϵ) * sum
                        GRBQuadExpr rhs = new GRBQuadExpr();

                        // helper expr containing (1-ϵ)
                        GRBLinExpr minusEps = new GRBLinExpr();
                        minusEps.addConstant(1.0);
                        minusEps.addTerm(-1.0, epsilon);

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

                            rhs.addTerm(1.0, vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eSi))]);
                            rhs.addTerm(-1.0, epsilon, vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eSi))]);

                            stringSum.append(" (").append(jointOutcome).append(", ").append(eSi).append(") +");

                            System.out.println("IMPL: " + jointOutcome + ", " + eS + " > 0 => " + jointOutcome + ", " + eSi + " > 0");

                            // Constraint 2.4: if η(c,e_S) > 0 then η(c,e_S u {i}) > 0

                            // introducing new binary variable y indicating if η(c,e_S) > 0
                            GRBVar y = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y");
                            // η(c,e_S) <= y
                            model.addConstr(vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eS))], GRB.LESS_EQUAL, y, "LHSconstraint(2.4)");

                            // η(c,e_S u {i}) >= 0.00001 * y
                            GRBLinExpr expr = new GRBLinExpr();
                            expr.addTerm(0.00001, y);
                            model.addConstr(vars[epsilonCeVarMap.get(new Pair<>(jointOutcome, eSi))], GRB.GREATER_EQUAL, expr, "RHSconstraint(2.4)");
                            model.update();
                        }


                        System.out.println("SUM: epsilon * (" + jointOutcome + ", " + eS + ") >=  (1-epsilon) * " + stringSum);


                        // Add the constraint to the model
                        model.addQConstr(lhs, GRB.GREATER_EQUAL, rhs, "Constraint(2.3)");
                        model.update();


                    }
                }
            }
        }


        GRBLinExpr expr = new GRBLinExpr();

        for (int c = 0; c < vars.length; c++) {
            expr.addTerm(1.0, vars[c]);
        }

        // Add constraint to model: sum of all variables should be 1
        model.addConstr(expr, GRB.EQUAL, 1.0, "sumTo1");
        model.update();

        // set all unused variables to zero
        for (int i = epsilonCeVarMap.size(); i < vars.length; i++) {
            model.addConstr(vars[i], GRB.EQUAL, 0.0, "setUnusedToZero" + i);
            model.update();
        }


        GRBLinExpr objectiveExpr = new GRBLinExpr();
        objectiveExpr.addTerm(1.0, epsilon);

        // high priority objective: Mimimise epsilon
        model.setObjective(objectiveExpr, GRB.MINIMIZE);
        model.update();

        // lower priority objectives

        // for every player
        for (int i = 0; i < n_coalitions; i++){
            GRBLinExpr lowPrioObjectiveExpr = new GRBLinExpr();

            // for ever joint outcome
            for (BitSet jointOutcome : utilities.keySet()){
                // get all variables with matching joint outcome
                ArrayList<Integer> jointOutcomeVars = getValuesWithMatchingFirstBitSet(epsilonCeVarMap, jointOutcome);

                for (int jointOutcomeVar: jointOutcomeVars){
                    lowPrioObjectiveExpr.addTerm(utilities.get(jointOutcome).get(i), vars[jointOutcomeVar]);
                }
            }
            model.setObjectiveN(lowPrioObjectiveExpr, i + 1, -1*(i+1), -1.0, 10e-6, 0, "lowerPriorityObjectiveCoalition" + i);
            model.update();
        }

        model.optimize();

        // helper print statement to see which variable corresponds to which joint outcome
        for (int i = 0; i < epsilonCeVarMap.size(); i++) {
            System.out.println(i + " " + getPairFromValue(epsilonCeVarMap, i));
        }

        int status = model.get(GRB.IntAttr.Status);

        if (status == GRB.Status.OPTIMAL) {
            System.out.println("Found optimal solution!");
            HashMap<BitSet, Double> robustStrategy = new HashMap<>();

            for (int i = 0; i < vars.length; i++) {
                System.out.println("Value of var[" + i + "]: " + vars[i].get(GRB.DoubleAttr.X));
            }

            double[] payoffs = new double[n_coalitions];
            Arrays.fill(payoffs, 0.0);

            // iterating over all joint outcomes
            for (BitSet c: utilities.keySet()) {
                // printing reward
                System.out.println(c + " " + utilities.get(c));

                ArrayList<Integer> cVars = getValuesWithMatchingFirstBitSet(epsilonCeVarMap, c);

                double prop = 0.0;
                for (Integer cVar: cVars) {
                    prop += vars[cVar].get(GRB.DoubleAttr.X);
                }
                robustStrategy.put(c, prop);
                for (int i = 0; i < n_coalitions; i++) {
                    payoffs[i] += utilities.get(c).get(i) * prop;
                }
            }

            System.out.println("robust strategy: " + robustStrategy);
            System.out.println("epsilon: " + epsilon.get(GRB.DoubleAttr.X));
            System.out.println("total reward: "+ Arrays.stream(payoffs).sum());
            for (int i = 0; i < n_coalitions; i++) {
                System.out.println("\t- coalition " + i + ": " + payoffs[i]);
            }


        } else if (status == GRB.Status.INFEASIBLE) {
            System.out.println("Model is infeasible");
            // To diagnose the infeasibilities, you might consider calculating an Irreducible Infeasible Set (IIS)
            model.computeIIS();
            model.write("model.ilp");
        } else {
            System.out.println("Optimization finished with status " + status);
        }

        // pretty print the model
        model.write("model.lp");

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


        try {
            return computeRobustCorrelatedEquilibrium(utilities, ce_constraints, strategies);
        } catch (GRBException e) {
            throw new RuntimeException(e);
        }


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
