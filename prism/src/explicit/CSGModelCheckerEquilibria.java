//==============================================================================
//
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@cs.ox.ac.uk> (University of Oxford)
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

import java.math.BigDecimal;
import java.util.*;

import gurobi.GRBException;
import org.apache.commons.math3.util.Precision;

import explicit.rewards.CSGRewards;
import explicit.rewards.MDPRewards;
import parser.ast.Coalition;
import parser.ast.ExpressionTemporal;
import prism.Pair;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismLangException;
import prism.PrismSettings;
import prism.PrismUtils;
import strat.CSGStrategy;
import strat.CSGStrategy.CSGStrategyType;
import java.text.SimpleDateFormat;
import java.util.Date;


public class CSGModelCheckerEquilibria extends CSGModelChecker
{
	protected MDPModelChecker mdpmc;

	//{player i -> action j (index) -> [<[product of ids of actions], value for joint action]>]
	private HashMap<Integer, HashMap<Integer, ArrayList<Pair<BitSet, Double>>>> assertions;
	//[player i][action j (index)][<product of ids of actions, value for joint action>]
	private ArrayList<ArrayList<ArrayList<Pair<BitSet, Double>>>> products;
	//[id payoff player i]
	private ArrayList<Integer> payoffs;
	/** Gradient of the objective function (multi-player Nash) */
	private HashMap<Integer, HashMap<Integer, ArrayList<Pair<BitSet, Double>>>> gradient;
	//{action j -> [player i, action j (index)]}
	private HashMap<Integer, int[]> mapActionIndex;

	/** Supports for a normal form game (indexed by coalition) */
	private ArrayList<ArrayList<BitSet>> supports;
	/** Pure supports for a normal form game (indexed by coalition) */
	private ArrayList<BitSet> psupports;
	/** All (joint-action) supports for a given normal form game */
	private ArrayList<BitSet> allSupports;


	/** Exclusive to correlated equilibria */
	private ArrayList<ArrayList<HashMap<BitSet, Double>>> ceConstraints;
	private HashMap<BitSet, Integer> ceVarMap;

	/** Dominated actions */
	protected BitSet[] dominated;
	/** Dominating actions */
	protected BitSet[] dominating;
	/** Set with all the players */
	protected BitSet players;

	/** SMT solver for labelled polytopes */
	protected CSGLabeledPolytopes smtLabeleldPolytopes;
	/** SMT solver for support enumeration */
	protected CSGSupportEnumeration smtSupportEnumeration;
	/** Numerical solver for support enumeration */
	protected CSGSupportEnumeration nlpSupportEnumeration;
	/** Solver for correlated equilibria */
	protected CSGCorrelated ceSolver;
	/** Name of the SMT solver */
	protected CSGRobustCorrelatedZ3 epsilonSolver;
	/** Name of the SMT solver */
	protected String smtSolver;
	/** Whether to check for the assumption for equilibria model checking */
	protected boolean assumptionCheck = false;
	/** Types and criteria for equilibria */
	public static final int NASH = 1;
	public static final int CORR = 2;
	public static final int SWEQ = 3;
	public static final int FAIR = 4;

	/** Different status for SMT equilibria computation */
	public enum CSGResultStatus {
		SAT, UNKNOWN, UNSAT;
	}

	/**
	 * Create a new CSGModelCheckerEquilibria, inherit basic state from parent (unless null).
	 */
	public CSGModelCheckerEquilibria(PrismComponent parent) throws PrismException {
		super(parent);
		players = new BitSet();
		psupports = new ArrayList<BitSet>();
		supports = new ArrayList<ArrayList<BitSet>>();
		allSupports =  new ArrayList<BitSet>();
		mapActionIndex = new HashMap<Integer, int[]>();
		products = new ArrayList<ArrayList<ArrayList<Pair<BitSet, Double>>>>();
		assertions = new HashMap<Integer, HashMap<Integer, ArrayList<Pair<BitSet, Double>>>>();
		ceConstraints = new ArrayList<ArrayList<HashMap<BitSet, Double>>>();
		ceVarMap = new HashMap<BitSet, Integer>();
		gradient = new HashMap<Integer, HashMap<Integer, ArrayList<Pair<BitSet, Double>>>>();
		payoffs = new ArrayList<Integer>();
		mdpmc = new MDPModelChecker(parent);
		mdpmc.setVerbosity(0);
		mdpmc.setSilentPrecomputations(true);
		assumptionCheck = false;
		smtSolver = getSettings().getString(PrismSettings.PRISM_SMT_SOLVER);
		switch (smtSolver) {
			case "Z3":
				break;
			case "Yices":
				break;
			default:
				throw new PrismException("Unknown SMT solver \"" + smtSolver + "\"");
		}
	}


	/**
	 * Sets the solver according to the settings and the equilibrium type.
	 *
	 * @param eqType Correlated/Nash
	 * @return Name
	 * @throws PrismException
	 */
	public String setSolver(int eqType) throws PrismException {
		String name = null;
		switch (eqType) {
			case CORR:
//				switch (lpSolver) {
//					case "Z3":
				ceSolver = new CSGCorrelatedZ3(maxRows * maxCols, numCoalitions);
				mainLog.println("using regular z3...");

//				try {
//					ceSolver = new CSGCorrelatedRobustGurobi(maxRows * maxCols, numCoalitions);
//					mainLog.println("Using robust gurobi");
//
//				} catch (GRBException e) {
//					throw new RuntimeException(e);
//				}

//				ceSolver = new CSGRobustCorrelatedZ3(maxRows * maxCols, numCoalitions);
//				mainLog.println("Using robust Z3");
				name = ceSolver.getSolverName();
//						break;
//					default: throw new PrismException("Unsupported solver for correlated equilibria computation");
//				}
				break;
			default: {
				switch (smtSolver) {
					case "Z3":
						smtLabeleldPolytopes = new CSGLabeledPolytopesZ3Stack(maxRows, maxCols);
						name = smtLabeleldPolytopes.getSolverName();
						break;
					case "Yices":
						smtLabeleldPolytopes = new CSGLabeledPolytopesYicesStack();
						name = smtLabeleldPolytopes.getSolverName();
				}
			}
		}
		return name;
	}

	/**
	 * Compute and store information about coalitions (for a nonzero-sum problem):
	 *
	 * @param csg The CSG
	 * @param coalitions The list of coalitions
	 * @throws PrismException
	 */
	public void buildCoalitions(CSG<Double> csg, List<Coalition> coalitions) throws PrismException {
		if (coalitions == null || coalitions.isEmpty())
			throw new PrismException("Coalitions must not be empty");
		int c, p, all;
		all = 0;
		numPlayers = csg.getNumPlayers();
		numCoalitions = coalitions.size();
		coalitionIndexes = new BitSet[coalitions.size()];
		actionIndexes = new BitSet[coalitions.size()];
		Map<Integer, String> pmap = new HashMap<Integer, String>();
		for (p = 0; p < numPlayers; p++) {
			pmap.put(p + 1, csg.getPlayerName(p));
		}
		for (c = 0; c < coalitions.size(); c++) {
			coalitionIndexes[c] = new BitSet();
			actionIndexes[c] = new BitSet();
			for (p = 0; p < numPlayers; p++) {
				if (!coalitionIndexes[c].get(p)) {
					if (coalitions.get(c).isPlayerIndexInCoalition(p, pmap)) {
						coalitionIndexes[c].set(p);
						actionIndexes[c].or(csg.getIndexes()[p]);
						actionIndexes[c].set(csg.getIdleForPlayer(p));
					}
				}
				else {
					throw new PrismLangException("Repeated player in coalition " + coalitions.get(c));
				}
			}
			all += coalitionIndexes[c].cardinality();
		}
		if (all != numPlayers)
			throw new PrismLangException("All players must be in a coalition");
		players.clear();
		players.set(0, numCoalitions);
	}

	/*
	 * Builds and stores all supports as BitSets of action indexes.
	 */
	public void buildAllSupports() {
		BitSet support;
		for (int p = 0; p < numCoalitions; p++) {
			if (!dominating[p].isEmpty()) {
				supports.get(p).add(dominating[p]);
			}
			else {
				buildSupportsPlayer(new BitSet(), p, 0);
			}
		}
		for (BitSet s : supports.get(0)) {
			support = new BitSet();
			support.or(s);
			buildAllSupportsAux(support, 1);
		}
	}

	/**
	 * Auxiliary method for building the set of supports.
	 *
	 * @param supp Current support
	 * @param p Player index
	 */
	public void buildAllSupportsAux(BitSet supp, int p) {
		for (BitSet s : supports.get(p)) {
			BitSet curr = (BitSet) supp.clone();
			curr.or(s);
			if(p == numCoalitions - 1) {
				if (!allSupports.contains(curr))
					allSupports.add(curr);
			}
			else {
				buildAllSupportsAux(curr, p + 1);
			}
		}
	}

	/**
	 * Builds all supports for a specific player.
	 *
	 * @param supp Current support
	 * @param p Player index
	 * @param a Action index
	 */
	public void buildSupportsPlayer(BitSet supp, int p, int a) {
		BitSet gt0 = (BitSet) supp.clone();
		if (!dominated[p].get(strategies.get(p).get(a))) {
			gt0.set(strategies.get(p).get(a));
		}
		BitSet eq0 = (BitSet) supp.clone();
		if (a == strategies.get(p).size() - 1) {
			if (!eq0.isEmpty()) {
				supports.get(p).add(eq0);
			}
			if (!gt0.isEmpty()) {
				supports.get(p).add(gt0);
			}
		}
		else {
			buildSupportsPlayer(eq0, p, a + 1);
			buildSupportsPlayer(gt0, p, a + 1);
		}
	}

	/**
	 * Finds maximum and average number of actions for all coalitions.
	 *
	 * @param csg The CSG
	 */
	public void findMaxAvgAct(CSG<Double> csg) {
		String max = "(";
		String avg = "(";
		int c, p, n, s;
		maxRows = 0;
		maxCols = 0;
		avgNumActions = new double[numCoalitions];
		Arrays.fill(avgNumActions, 0.0);
		maxNumActions  = new int[numCoalitions];
		Arrays.fill(maxNumActions, 0);
		for (s = 0; s < csg.getNumStates(); s++) {
			for (c = 0; c < numCoalitions; c++) {
				n = 1;
				for (p = coalitionIndexes[c].nextSetBit(0); p >= 0; p = coalitionIndexes[c].nextSetBit(p + 1)) {
					n *= csg.getIndexesForPlayer(s, p).cardinality();
				}
				maxNumActions[c] = (maxNumActions[c] < n)? n : maxNumActions[c];
				avgNumActions[c] += n;
			}
		}
		for (c = 0; c < numCoalitions; c++) {
			avgNumActions[c] /= csg.getNumStates();
			max += (c < numCoalitions -1)? maxNumActions[c] + "," : maxNumActions[c] + ")";
			avg += (c < numCoalitions -1)? PrismUtils.formatDouble2dp(avgNumActions[c]) + "," : PrismUtils.formatDouble2dp(avgNumActions[c]) + ")";
		}
		mainLog.println("Max/avg (actions): " + max + "/" + avg);
	}

	/**
	 * Finds dominated actions for a specific player.
	 *
	 * @param p Player index
	 * @return
	 * @throws PrismException
	 */
	public BitSet findDominated(int p) throws PrismException {
		Pair<BitSet, Double> pair1, pair2;
		BitSet domi = new BitSet();
		boolean domb;
		if (assertions.get(p).keySet().size() == 1) {
			return domi;
		}
		else {
			for (int act1 : assertions.get(p).keySet()) {
				for (int act2 : assertions.get(p).keySet()) {
					if (act1 != act2) {
						domb = true;
						for (int prod = 0; prod < assertions.get(p).get(act1).size(); prod++) {
							pair1 = assertions.get(p).get(act1).get(prod);
							pair2 = assertions.get(p).get(act2).get(prod);
							if (pair1.first.equals(pair2.first)) {
								domb = domb && Double.compare(pair1.second, pair2.second) < 0;
							}
							else {
								throw new PrismException("Error when comparing indexes");
							}
						}
						if (domb) {
							domi.set(strategies.get(p).get(act1));
							break;
						}
					}
				}
			}
		}
		return domi;
	}

	/**
	 * Finds dominating actions for a specific player.
	 *
	 * @param p Player index
	 * @return
	 * @throws PrismException
	 */
	public BitSet findDominating(int p) throws PrismException {
		Pair<BitSet, Double> pair1, pair2;
		BitSet domi = new BitSet();
		boolean domb;
		if (assertions.get(p).keySet().size() == 1) {
			return domi;
		}
		else {
			for (int act1 : assertions.get(p).keySet()) {
				domb = true;
				for (int act2 : assertions.get(p).keySet()) {
					if (act1 != act2) {
						for (int prod = 0; prod < assertions.get(p).get(act1).size(); prod++) {
							pair1 = assertions.get(p).get(act1).get(prod);
							pair2 = assertions.get(p).get(act2).get(prod);
							if (pair1.first.equals(pair2.first)) {
								domb = domb && Double.compare(pair1.second, pair2.second) > 0;
							}
							else {
								throw new PrismException("Error when comparing indexes");
							}
						}
					}
				}
				if (domb) {
					domi.set(strategies.get(p).get(act1));
				}
			}
		}
		return domi;
	}

	/**
	 * Finds row and column indexes for the maximum entry in a matrix.
	 *
	 * @param a Matrix
	 * @return
	 */
	public int[] findMaxIndexes(double[][] a) {
		int result[] = new int[2];
		result[0] = 0;
		result[1] = 0;
		double value = Double.NEGATIVE_INFINITY;
		for(int r = 0; r < a.length; r++) {
			for(int c = 0; c < a[r].length; c++) {
				if(Double.compare(a[r][c], value) > 0) {
					value = a[r][c];
					result[0] = r;
					result[1] = c;
				}
			}
		}
		return result;
	}

	/**
	 * Checks whether all entries in the utility table are zero.
	 *
	 * @return
	 */
	public boolean checkAllZeroEntries() {
		boolean allzero = true;
		for (BitSet entry : utilities.keySet()) {
			for (int p = 0; p < numCoalitions; p++) {
				allzero = allzero && Double.compare(utilities.get(entry).get(p), 0.0) == 0;
				if (!allzero)
					break;
			}
			if (!allzero)
				break;
		}
		return allzero;
	}

	/**
	 * Finds the SWNE for when just one player has choices.
	 *
	 * @param mmap Index map
	 * @param strats Overall strategy
	 * @param eqstrat Strategy for the current state
	 * @param active Active player
	 * @return
	 */
	public double[][] findSWNEOnePlayer(List<Map<Integer, BitSet>> mmap, List<List<Map<BitSet, Double>>> strats, List<Map<BitSet, Double>> eqstrat, BitSet active) {
		BitSet support = null;
		double[][] result;
		double sumt, sumv, v;
		int p1, p2;
		result = new double[1][numCoalitions];
		p1 = active.nextSetBit(0);
		v = Double.NEGATIVE_INFINITY;
		sumv = Double.NEGATIVE_INFINITY;
		sumt = Double.NEGATIVE_INFINITY;
		for (BitSet entry : utilities.keySet()) {
			sumv = 0.0;
			for (p2 = 0; p2 < numCoalitions; p2++) {
				sumv += utilities.get(entry).get(p2); // computes sum of utilities
			}
			if (Double.compare(utilities.get(entry).get(p1), v) > 0) { // maximizes for player who has a choice
				support = entry;
				sumt = 0.0;
				v = utilities.get(entry).get(p1);
				for (p2 = 0; p2 < numCoalitions; p2++) {
					result[0][p2] = utilities.get(entry).get(p2);
					sumt += utilities.get(entry).get(p2); // sum of the utilities for the selected entry
				}
			}
			else if (Double.compare(utilities.get(entry).get(p1), v) == 0 && Double.compare(sumv, sumt) > 0) { // case utility for player is the same but sum is higher
				support = entry;
				sumt = 0.0;
				for (p2 = 0; p2 < numCoalitions; p2++) {
					result[0][p2] = utilities.get(entry).get(p2);
					sumt += utilities.get(entry).get(p2);
				}
			}
		}
		if (genStrat) {
			eqstrat = new ArrayList<Map<BitSet, Double>>();
			extractStrategyFromSupport(mmap, eqstrat, support);
			strats.add(eqstrat);
		}
		return result;
	}

	/**
	 * Extracts the SWNE for when there is only one support.
	 *
	 * @param mmap Index map
	 * @param strats Overall strategy
	 * @param eqstrat
	 * @return
	 */
	public double[][] findSWNEUniqueSupport(List<Map<Integer, BitSet>> mmap, List<List<Map<BitSet, Double>>> strats, List<Map<BitSet, Double>> eqstrat) {
		double[][] result;
		result = new double[1][numCoalitions];
		for (BitSet entry : allSupports) {
			for (int p = 0; p < numCoalitions; p++) {
				result[0][p] = utilities.get(entry).get(p);
			}
			if (genStrat) {
				eqstrat = new ArrayList<Map<BitSet, Double>>();
				extractStrategyFromSupport(mmap, eqstrat, entry);
				strats.add(eqstrat);
			}
		}
		return result;
	}

	/**
	 * Extracts the strategy for a given support.
	 *
	 * @param mmap Index map
	 * @param eqstrat Strategy for the current state
	 * @param support Support
	 */
	public void extractStrategyFromSupport(List<Map<Integer, BitSet>> mmap, List<Map<BitSet, Double>> eqstrat, BitSet support) {
		BitSet indx = new BitSet();
		int a, i, p;
		for (p = 0; p < numCoalitions; p++) {
			indx.clear();
			for (a = 0; a < strategies.get(p).size(); a++) {
				indx.set(strategies.get(p).get(a));
			}
			indx.and(support);
			i = indx.nextSetBit(0);
			eqstrat.add(p, new HashMap<BitSet, Double>());
			eqstrat.get(p).put(mmap.get(p).get(strategies.get(p).indexOf(i)), 1.0); // indexOf should be changed
		}
	}

	/**
	 * Extract the strategy for the case of an unique equilibrium.
	 *
	 * @param eq Equilibria
	 * @param mmap Index map
	 * @return
	 */
	public ArrayList<Map<BitSet, Double>> extractStrategyFromEquilibrium(EquilibriumResult eq, List<Map<Integer, BitSet>> mmap) {
		ArrayList<Map<BitSet, Double>> eqstrat = new ArrayList<Map<BitSet, Double>>();
		for (int p = 0; p < numCoalitions; p++) {
			eqstrat.add(p, new HashMap<BitSet, Double>());
			for (int t : eq.getStrategy().get(p).getSupport()) {
				eqstrat.get(p).put(mmap.get(p).get(t), eq.getStrategy().get(p).get(t));
			}
		}
		return eqstrat;
	}

	/**
	 * Build info needed for the utility table to solve a CSG state s.
	 *
	 * @param csg The CSG
	 * @param rewards List of rewards
	 * @param mmap Index map
	 * @param val Current values for each state
	 * @param s State index
	 * @param min Whether minimising/maximising
	 * @throws PrismException
	 */
	public void buildStepGame(CSG<Double> csg, List<CSGRewards<Double>> rewards, List<Map<Integer, BitSet>> mmap, double[][] val, int s, boolean min) throws PrismException {
		Map<BitSet, Integer> imap = new HashMap<BitSet, Integer>();
		BitSet jidx;
		BitSet indexes = new BitSet();
		BitSet tmp = new BitSet();
		String act;
		double v;
		int c, i, p, t;
		int[] joint;
		int[] idle = new int[numPlayers];
		ceVarMap.clear();
		actions.clear();
		psupports.clear();
		strategies.clear();
		utilities.clear();
		varIndex = 0;
		Arrays.fill(idle, -1);
		for (c = 0; c < numCoalitions; c++) {
			actions.add(c, new ArrayList<String>());
			psupports.add(c, new BitSet());
			strategies.add(c, new ArrayList<Integer>());
		}
		for (t = 0; t < csg.getNumChoices(s); t++) {
			jidx = new BitSet();
			joint = csg.getIndexes(s, t);
			indexes.clear();
			for (p = 0; p < numPlayers; p++) {
				if (joint[p] != -1)
					indexes.set(joint[p]);
				else
					indexes.set(csg.getIdleForPlayer(p));
			}
			for (c = 0; c < numCoalitions; c++) {
				v = 0.0;
				tmp.clear();
				tmp.or(actionIndexes[c]);
				tmp.and(indexes);
				if (tmp.cardinality() != coalitionIndexes[c].cardinality()) {
					throw new PrismException("Error in coalition");
				}
				else {
					if(!imap.keySet().contains(tmp)) {
						act = "";
						strategies.get(c).add(varIndex);
						psupports.get(c).set(varIndex);
				    	if (mmap != null)
				    		mmap.get(c).put(strategies.get(c).size() - 1, (BitSet) tmp.clone());
						for (i = tmp.nextSetBit(0); i >= 0; i = tmp.nextSetBit(i + 1)) {
							act += "[" + csg.getActions().get(i - 1) + "]";
						}
						actions.get(c).add(act);
						jidx.set(varIndex);
						imap.put((BitSet) tmp.clone(), varIndex);
						varIndex++;
					}
					else {
						jidx.set(imap.get(tmp));
					}
				}
			}
			utilities.put(jidx, new ArrayList<Double>());
			ceVarMap.put(jidx, utilities.keySet().size() - 1);
			for (c = 0; c < numCoalitions; c++) {
				v = 0.0;
				for (int d : csg.getChoice(s, t).getSupport()) {
					if (!Double.isNaN(val[c][d])) {
						v += csg.getChoice(s, t).get(d) * val[c][d];
					}
					else {
						mainLog.println("val[c][d]: " + val[c][d]);
						mainLog.println("\n## state " + s);
						mainLog.println("-- strategies " + strategies);
						mainLog.println("-- actions " + actions);
						mainLog.println("-- utilities " + utilities);
						throw new PrismException("Error in building game for state " + s);
					}
				}
				if (rewards != null) {
					if (rewards.get(c) != null)
						v += rewards.get(c).getTransitionReward(s, t);
				}
				v = Precision.round(v, 12, BigDecimal.ROUND_HALF_EVEN);
				utilities.get(jidx).add(c, (min)? -1.0 * v : v); // might have to add min (v, 1.0) due to assertions for probabilistic
			}
		}
		//System.out.println("-- imap " + imap);
		//if (s == csg.getFirstInitialState()) {
			//System.out.println("\n## state " + s);
			//System.out.println("-- strategies " + strategies);
			//System.out.println("-- actions " + actions);
			//System.out.println("-- utilities " + utilities);
			//System.out.println("-- mmap " + mmap);
		//}
	}

	/**
	 * Builds a bimatrix game (two-player case).
	 *
	 * @param csg The CSG
	 * @param r1 Rewards for the first coalition
	 * @param r2 Rewards for the second coalition
	 * @param mmap Index map
	 * @param nmap Reduced index map
	 * @param val Current values for each state
	 * @param s State index
	 * @param min Whether minimising/maximising
	 * @return
	 * @throws PrismException
	 */
	public ArrayList<ArrayList<ArrayList<Double>>> buildBimatrixGame(CSG<Double> csg, CSGRewards<Double> r1, CSGRewards<Double> r2, List<Map<Integer, BitSet>> mmap,  List<ArrayList<Integer>> nmap, double[][] val, int s, boolean min) throws PrismException {
		ArrayList<ArrayList<ArrayList<Double>>> bmgame = new ArrayList<ArrayList<ArrayList<Double>>>();
		ArrayList<CSGRewards<Double>> rewards = null;
		BitSet action = new BitSet();
		int col, p, row, irow, icol;
		if (numCoalitions > 2)
			throw new PrismLangException("Multiplayer game not supported by this method");
		if (r1 != null || r2 != null) {
			rewards = new ArrayList<>();
			rewards.add(0, r1);
			rewards.add(1, r2);
		}
		buildStepGame(csg, rewards, mmap, val, s, min);
		//System.out.println("-- utilities " + utilities);
		//System.out.println("-- strategies " + strategies);
		//System.out.println("-- mmap " + mmap);
		clear();
		computeAssertions();
		//System.out.println("-- assertions " + assertions);
		//System.out.println("-- gradient " + gradient);
		for (p = 0; p < numCoalitions; p++) {
			dominated[p] = findDominated(p);
			dominating[p] = findDominating(p);
			//System.out.println("-- dominated " + p + ": " + dominated[p]);
			//System.out.println("-- dominating " + p + ": " + dominating[p]);
		}
		buildAllSupports();
		//System.out.println("-- supports " + allSupports);
		for (p = 0; p < 2; p++) {
			bmgame.add(p, new ArrayList<ArrayList<Double>>());
			irow = 0;
			for (row = 0; row < strategies.get(0).size(); row++) {
				if (!dominated[0].get(strategies.get(0).get(row))) {
					bmgame.get(p).add(irow, new ArrayList<Double>());
					action.clear();
					action.set(strategies.get(0).get(row));
					icol = 0;
					for (col = 0; col < strategies.get(1).size(); col++) {
						if (!dominated[1].get(strategies.get(1).get(col))) {
							action.set(strategies.get(1).get(col));
							if (utilities.containsKey(action))
								bmgame.get(p).get(irow).add(icol, utilities.get(action).get(p));
							else
								throw new PrismException("Error in building bimatrix game");
							action.clear(strategies.get(1).get(col));
							if (p == 0 && irow == 0)
								nmap.get(1).add(icol, col);
							icol++;
						}
					}
					if (p == 0)
						nmap.get(0).add(irow, row);
					irow++;
				}
			}
		}
		//System.out.println("-- nmap " + nmap);
		return bmgame;
	}

	/**
	 * Clear various structures used in model checking.
	 *
	 */
	public void clear() {
		// Would be better to clear the internal arrays/maps
		supports.clear();
		allSupports.clear();
		products.clear();
		assertions.clear();
		ceConstraints.clear();
		gradient.clear();
		payoffs.clear();
		mapActionIndex.clear();
		for(int c = 0; c < numCoalitions; c++) {
			supports.add(c, new ArrayList<BitSet>());
			products.add(c, new ArrayList<ArrayList<Pair<BitSet, Double>>>());
			assertions.put(c, new HashMap<Integer, ArrayList<Pair<BitSet, Double>>>());
			ceConstraints.add(c, new ArrayList<HashMap<BitSet, Double>>());
			gradient.put(c, new HashMap<Integer, ArrayList<Pair<BitSet, Double>>>());
			for(int a = 0; a < strategies.get(c).size(); a++) {
				products.get(c).add(a, new ArrayList<Pair<BitSet, Double>>());
				mapActionIndex.put(strategies.get(c).get(a), new int[] {c, a});
			}
		}
		for(int c = 0; c < numCoalitions; c++) { // strategies allocated first
			payoffs.add(c, varIndex);
			varIndex++;
		}
	}

	/**
	 * Builds info used in multi-player equilibria (Nash and Correlated).
	 *
	 * @throws PrismException
	 */
	public void computeAssertions() throws PrismException {
		int c, q;
		BitSet ps;
		BitSet acts = new BitSet();
		for (c = 0; c < numCoalitions; c++) {
        	ps = (BitSet) players.clone();
        	ps.clear(c);
        	for (q = 0; q < strategies.get(c).size(); q++) {
        		acts.clear();
        		acts.set(strategies.get(c).get(q));
        		assertions.get(c).put(q, prodAction(acts, ps, q, c));
        	}
        }
		/*
		System.out.println("-- actions ");
		System.out.println(actions);
		System.out.println("-- strategies ");
		System.out.println(strategies);

		System.out.println("-- assertions ");
		for (c = 0; c < numCoalitions; c++) {
			System.out.println("--- player " + c);
        	for (q = 0; q < strategies.get(c).size(); q++)
        		System.out.println("---- action " + q + " " + assertions.get(c).get(q));
		}

		System.out.println("-- gradient ");
		System.out.println(gradient);
		System.out.println("-- map ");
		for (int i : map.keySet()) {
			System.out.println(i + "= " + Arrays.toString(map.get(i)));
		}
		*/
	}

	/**
	 *
	 *
	 * @param prod
	 * @param sp
	 * @param act
	 * @param p
	 * @return
	 * @throws PrismException
	 */
	public ArrayList<Pair<BitSet, Double>> prodAction(BitSet prod, BitSet sp, int act, int p) throws PrismException {
		prodAction(new Pair<BitSet, Double>(new BitSet(), 0.0), prod, sp, act, p);
		ArrayList<Pair<BitSet, Double>> sum = new ArrayList<Pair<BitSet, Double>>();
		for(int j = 0; j < products.get(p).get(act).size(); j++) {
			sum.add(products.get(p).get(act).get(j));
		}
		products.get(p).get(act).clear();
		return sum;
	}

	/**
	 *
	 *
	 * @param expr
	 * @param prod
	 * @param sp
	 * @param act
	 * @param p
	 * @throws PrismException
	 */
	public void prodAction(Pair<BitSet, Double> expr, BitSet prod, BitSet sp, int act, int p) throws PrismException {
		BitSet set;
		BitSet curr = (BitSet) sp.clone();
		Pair<BitSet, Double> nexpr;
		if(products.get(p) == null)
			products.add(p, new ArrayList<ArrayList<Pair<BitSet, Double>>>());
		else if(products.get(p).get(act) == null)
			products.get(p).add(act, new ArrayList<Pair<BitSet, Double>>());
		for(int cp = sp.nextSetBit(0); cp < sp.size() && cp != -1; cp = sp.nextSetBit(cp + 1)) {
			curr.clear(cp);
			for(int a = 0; a < actions.get(cp).size(); a++) {
				set = new BitSet();
				set.or(prod);
				set.set(strategies.get(cp).get(a));
				nexpr = new Pair<BitSet, Double>(new BitSet(), 0.0);
				nexpr.first.or(expr.first);
				nexpr.first.set(strategies.get(cp).get(a));
				prodAction(nexpr, set, curr, act, p);
				if(sp.cardinality() == 1 && set.cardinality() == numCoalitions) { // should have to check for set size?
					nexpr.first.or(expr.first);
					nexpr.first.set(strategies.get(cp).get(a));
					nexpr.second = utilities.get(set).get(p);
					products.get(p).get(act).add(nexpr);
					if(gradient.get(p).get(act) == null)
						gradient.get(p).put(act, new ArrayList<Pair<BitSet, Double>>());
					if(nexpr.second != 0.0)
						gradient.get(p).get(act).add(nexpr);
					BitSet der = new BitSet();
					for(int i = nexpr.first.nextSetBit(0); i >= 0; i = nexpr.first.nextSetBit(i + 1)) {
						der.or(nexpr.first);
						der.set(strategies.get(p).get(act));
						der.clear(i);
						if(gradient.get(mapActionIndex.get(i)[0]).get(mapActionIndex.get(i)[1]) == null)
							gradient.get(mapActionIndex.get(i)[0]).put(mapActionIndex.get(i)[1], new ArrayList<Pair<BitSet, Double>>());
						if(nexpr.second != 0.0)
							gradient.get(mapActionIndex.get(i)[0]).get(mapActionIndex.get(i)[1]).add(new Pair<BitSet, Double>((BitSet) der.clone(), nexpr.second));
						der.clear();
					}
				}
			}
		}
	}

	/**
	 *
	 *
	 * @param csg
	 * @param target
	 * @param n
	 * @return
	 */
	public double[][] computeBoundedReachProbs(CSG<Double> csg, BitSet target, int n) {
		double[][] sol = new double[n][csg.getNumStates()];
		double[] sol1 = new double[csg.getNumStates()];
		double[] sol2 = new double[csg.getNumStates()];
		double v, sum;
		int i, s, t;
		for (s = 0; s < csg.getNumStates(); s++) {
			sol2[s] = sol1[s] = target.get(s)? 1.0 : 0.0;
		}
		for (i = 0; i < n; i++) {
			for (s = 0; s < csg.getNumStates(); s++) {
				v = 0.0;
				for (t = 0; t < csg.getNumChoices(s); t++) {
					sum = 0.0;
					for (Iterator<Map.Entry<Integer, Double>> iter = csg.getTransitionsIterator(s, t); iter.hasNext(); ) {
						Map.Entry<Integer, Double> e = iter.next();
						sum += e.getValue() * sol2[e.getKey()];
					}
					v = (sum > v)? sum : v;
				}
				if (!target.get(s))
					sol1[s] = v;
			}
			sol2 =  Arrays.copyOf(sol1, sol1.length);
			sol[i] = sol1;
		}
		return sol;
	}

	/**
	 * Deal with two-player bounded equilibria.
	 *
	 * @param csg
	 * @param coalitions
	 * @param rewards
	 * @param exprs
	 * @param targets
	 * @param remain
	 * @param bounds
	 * @param eqType
	 * @param crit
	 * @param min
	 * @return
	 * @throws PrismException
	 */
	public ModelCheckerResult computeBoundedEquilibria(CSG<Double> csg, List<Coalition> coalitions, List<CSGRewards<Double>> rewards, List<ExpressionTemporal> exprs, BitSet[] targets, BitSet[] remain, int[] bounds, int eqType, int crit, boolean min) throws PrismException {
		if (genStrat) {
			throw new PrismException("Strategy synthesis for bounded properties is not supported yet.");
		}
		ModelCheckerResult res = new ModelCheckerResult();
		List<CSGRewards<Double>> newRewards = null;
		BitSet[] only = new BitSet[coalitions.size()];
		BitSet[] phi1 = new BitSet[3];
		BitSet cpy =  new BitSet();
		double[][] sol = new double[coalitions.size()][csg.getNumStates()];
		double[][] tmp = new double[coalitions.size()][csg.getNumStates()];
		double[][] val = new double[coalitions.size()][csg.getNumStates()];
		double[] eq;
		double[] r = new double[csg.getNumStates()];
		int i, j, n1, n2, k, s;
		boolean rew;
		long currentTime, timePrecomp;

		rew = rewards != null;

		buildCoalitions(csg, coalitions);
		findMaxRowsCols(csg);
		mainLog.println("Starting bounded equilibria computation (solver=" + setSolver(eqType) + ")...");
		dominated = new BitSet[numCoalitions];
		dominating = new BitSet[numCoalitions];

		// Case next
		if ((exprs.get(0).getOperator() == ExpressionTemporal.P_X) || (exprs.get(1).getOperator() == ExpressionTemporal.P_X)) {
			for (i = 0; i < 2; i++) {
				if (exprs.get(i).getOperator() == ExpressionTemporal.P_X) {
					for (s = 0; s < csg.getNumStates(); s++) {
						sol[i][s] = targets[i].get(s)? 1.0 : 0.0;
					}
				}
				else {
					sol[i] = mdpmc.computeBoundedUntilProbs(csg, remain[i], targets[i], bounds[i]-1, min).soln;
				}
			}
			for (s = 0; s < csg.getNumStates(); s++) {
				eq = stepEquilibriaTwoPlayer(csg, null, null, null, sol, s, eqType, crit, rew, min);
				tmp[0][s] = eq[1];
				tmp[1][s] = eq[2];
				r[s] = eq[1] + eq[2];
			}
			mainLog.println("\nCoalition results (initial state): (" + tmp[0][csg.getFirstInitialState()] + "," + tmp[1][csg.getFirstInitialState()] + ")");
			res.soln = r;
			res.numIters = 1;
			return res;
		}

		if (!rew) {
			for (i = 0; i < 2; i++) {
				phi1[i] = new BitSet();
				if (remain[i] == null)
					phi1[i].set(0, csg.getNumStates());
			else
				phi1[i].or(remain[i]);
			}
		}

		if (targets == null) {
			targets = new BitSet[coalitions.size()];
			for (i = 0; i < coalitions.size(); i++) { // Case for cumulative rewards
				targets[i] = new BitSet();
			}
		}

		for (i = 0; i < coalitions.size(); i++) {
			only[i] = new BitSet();
			only[i].or(targets[i]);
			for (j = 0; j < coalitions.size(); j++) {
				if (i != j)
					only[i].andNot(targets[j]);
			}
		}

		k = Math.abs(bounds[0] - bounds[1]);
		n1 = (bounds[0] > bounds[1])? k : 0;
		n2 = (bounds[1] > bounds[0])? k : 0;

		if (!rew) {
			phi1[2] = new BitSet();
			phi1[2].or(phi1[0]);
			phi1[2].and(phi1[1]); // intersection of phi1(1) and phi1(2)
			cpy.clear();
			cpy.or(phi1[0]);
			phi1[0].andNot(phi1[1]); // phi1(1) minus phi1(2)
			phi1[1].andNot(cpy); // phi1(2) minus phi1(1)
			cpy.clear();
		}
		else {
			newRewards = new ArrayList<>();
		}

		//System.out.println("for bounds[0]");
		//double[][] pre0 = computeBoundedReachProbs(csg, targets[0], bounds[0]);
		//System.out.println("for bounds[1]");
		//double[][] pre1 = computeBoundedReachProbs(csg, targets[1], bounds[1]);

		timePrecomp = System.currentTimeMillis();
		if (rew) {
			if (bounds[0] > bounds[1]) {
				if (exprs.get(0).getOperator() == ExpressionTemporal.R_C)
					val[0] = mdpmc.computeCumulativeRewards(csg, rewards.get(0), n1, min).soln;
				else
					val[0] = mdpmc.computeInstantaneousRewards(csg, rewards.get(0), n1, min).soln;
			}
			if (bounds[1] > bounds[0]) {
				if (exprs.get(1).getOperator() == ExpressionTemporal.R_C)
					val[1] = mdpmc.computeCumulativeRewards(csg, rewards.get(1), n2, min).soln;
				else
					val[1] = mdpmc.computeInstantaneousRewards(csg, rewards.get(1), n2, min).soln;
			}
		}
		timePrecomp = System.currentTimeMillis() - timePrecomp;

		while (true) {
			currentTime = System.currentTimeMillis();
			if (!rew) {
				if (n1 > 0) {
					if (remain[0] == null)
						val[0] = mdpmc.computeBoundedReachProbs(csg, targets[0], n1, min).soln;
					else
						val[0] = mdpmc.computeBoundedUntilProbs(csg, remain[0], targets[0], n1, min).soln;
				}
				if (n2 > 0) {
					if (remain[1] == null)
						val[1] = mdpmc.computeBoundedReachProbs(csg, targets[1], n2, min).soln;
					else
						val[1] = mdpmc.computeBoundedUntilProbs(csg, remain[1], targets[1], n2, min).soln;
				}
			}
			timePrecomp += System.currentTimeMillis() - currentTime;
			if (Math.min(n1, n2) > 0) {
				for (s = 0; s < csg.getNumStates(); s++) {
					if (rew) {
						newRewards.clear();
						for (i = 0; i < 2; i++) {
							newRewards.add(i, rewards.get(i));
							if (!(exprs.get(i).getOperator() == ExpressionTemporal.R_C))
								newRewards.set(i, null);
						}
						eq = stepEquilibriaTwoPlayer(csg, newRewards, null, null, sol, s, eqType, crit, rew, min);
						tmp[0][s] = eq[1];
						tmp[1][s] = eq[2];
					}
					else {
						if (targets[0].get(s) && targets[1].get(s)) {
							tmp[0][s] = 1.0;
							tmp[1][s] = 1.0;
						}
						else if (only[0].get(s)) {
							tmp[0][s] = 1.0;
							tmp[1][s] = val[1][s];
						}
						else if (only[1].get(s)) {
							tmp[0][s] = val[0][s];
							tmp[1][s] = 1.0;
						}
						else if(phi1[0].get(s)) {
							tmp[0][s] = val[0][s];
							tmp[1][s] = 0.0;
						}
						else if(phi1[1].get(s)) {
							tmp[0][s] = 0.0;
							tmp[1][s] = val[1][s];
						}
						else if(!phi1[2].get(s)) {
							tmp[0][s] = 0.0;
							tmp[1][s] = 0.0;
						}
						else {
							eq = stepEquilibriaTwoPlayer(csg, null, null, null, sol, s, eqType, crit, rew, min);
							tmp[0][s] = eq[1];
							tmp[1][s] = eq[2];
						}
					}
				}
				for (s = 0; s < csg.getNumStates(); s++) {
					sol[0][s] = tmp[0][s];
					sol[1][s] = tmp[1][s];
					r[s] = sol[0][s] + sol[1][s];
				}
				/*
				String sols;
				sols = "(";
				for (p = 0; p < numCoalitions; p++) {
					if (p < numCoalitions - 1)
						sols += sol[p][csg.getFirstInitialState()] + ",";
					else
						sols += sol[p][csg.getFirstInitialState()] + ")";
				}
				mainLog.println(k + ": " + sols);
				*/
			}
			else {
				for (s = 0; s < csg.getNumStates(); s++) {
					if (rew) {
						if (n1 == 0 && n2 == 0) {
							sol[0][s] = (exprs.get(0).getOperator() == ExpressionTemporal.R_C)? 0.0 : rewards.get(0).getStateReward(s);
							sol[1][s] = (exprs.get(1).getOperator() == ExpressionTemporal.R_C)? 0.0 : rewards.get(1).getStateReward(s);
						}
						else if (n1 == 0) {
							sol[0][s] = (exprs.get(0).getOperator() == ExpressionTemporal.R_C)? 0.0 : rewards.get(0).getStateReward(s);
							sol[1][s] = val[1][s];
						}
						else {
							sol[0][s] = val[0][s];
							sol[1][s] = (exprs.get(1).getOperator() == ExpressionTemporal.R_C)? 0.0 : rewards.get(1).getStateReward(s);
						}
					}
					else {
						if (n1 == 0 && n2 == 0) {
							sol[0][s] = targets[0].get(s)? 1.0 : 0.0;
							sol[1][s] = targets[1].get(s)? 1.0 : 0.0;
						}
						else if (n1 == 0) {
							sol[0][s] = targets[0].get(s)? 1.0 : 0.0;
							sol[1][s] = targets[1].get(s)? 1.0 : val[1][s];
						}
						else {
							sol[0][s] = targets[0].get(s)? 1.0 : val[0][s];
							sol[1][s] = targets[1].get(s)? 1.0 : 0.0;
						}
					}
				}
			}
			if (k == Math.max(bounds[0], bounds[1])) {
				break;
			}
			k++;
			n1 = Math.min(n1 + 1, bounds[0]);
			n2 = Math.min(n2 + 1, bounds[1]);
		}
		mainLog.println("\nPrecomputation took " + timePrecomp / 1000.0 + " seconds.");
		mainLog.println("Coalition results (initial state): (" + sol[0][csg.getFirstInitialState()] + "," + sol[1][csg.getFirstInitialState()] + ")");
		res.soln = r;
		res.numIters = k;
		return res;
	}

	/**
	 * Deal with multi-player bounded equilibria (unfinished).
	 *
	 * @param csg
	 * @param coalitions
	 * @param rewards
	 * @param exprs
	 * @param targets
	 * @param remain
	 * @param bounds
	 * @param eqType
	 * @param crit
	 * @param min
	 * @return
	 * @throws PrismException
	 */
	public ModelCheckerResult computeMultiBoundedEquilibria(CSG<Double> csg, List<Coalition> coalitions, List<CSGRewards<Double>> rewards, List<ExpressionTemporal> exprs, BitSet[] targets, BitSet[] remain, int[] bounds, int eqType, int crit, boolean min) throws PrismException {
		mainLog.println("\n# Running bounded multi-player equilibria...\n");
		if (genStrat) {
			throw new PrismException("Strategy synthesis for bounded properties is not yet supported");
		}
		ModelCheckerResult res = new ModelCheckerResult();
		List<Map<Integer, BitSet>> mmap = null;
		double[][] sol;
		double[][] val;
		double[][] tmp;
		double[][] eq;
		double[] r;
		double[] sw;
		long timeTaken;
		boolean done, rew;
		int c, k, s, t = -1;

		sol = new double[coalitions.size()][csg.getNumStates()];
		val = new double[coalitions.size()][csg.getNumStates()];
		tmp = new double[coalitions.size()][csg.getNumStates()];
		r = new double[csg.getNumStates()];

		rew = rewards != null;

		buildCoalitions(csg, coalitions);
		dominated = new BitSet[numCoalitions];
		dominating = new BitSet[numCoalitions];
		findMaxAvgAct(csg);

		if (rew) {
			t = exprs.get(0).getOperator();
			for (int i = 1; i < exprs.size(); i++) {
				if (t != exprs.get(i).getOperator())
					throw new PrismException("Properties with mixed operators are not yet supported");
			}
			if (t == ExpressionTemporal.R_C) {
				for (c = 0; c < numCoalitions; c++) {
					for (s = 0; s < csg.getNumStates(); s++) {
						sol[c][s] = 0.0;
					}
				}
			}
			else {
				for (c = 0; c < numCoalitions; c++) {
					for (s = 0; s < csg.getNumStates(); s++) {
						sol[c][s] = ((min)? -1 * rewards.get(c).getStateReward(s) : rewards.get(c).getStateReward(s));
					}
				}
			}
		}
		else {
			for (s = 0; s < csg.getNumStates(); s++) {
				for (c = 0; c < numCoalitions; c++) {
					if (targets[c].get(s))
						sol[c][s] = 1.0;
				}
			}
		}

		for (c = 0; c < numCoalitions; c++) {
			Arrays.fill(tmp[c], 0.0);
			Arrays.fill(val[c], 0.0);
		}

		switch (eqType) {
			case CORR : {
//				int maxSize = 1;
//				for (c = 0; c < numCoalitions; c++) {
//					maxSize = maxSize * maxNumActions[c];
//				}
//				switch (lpSolver) {
//					case "Z3" :
//						ceSolver = new CSGCorrelatedZ3(maxSize, numCoalitions);
//						break;
//					default :
//						throw new PrismException("Solver not yet supported");
//				}
			}
			default : {
				smtSupportEnumeration = new CSGSupportEnumerationZ3(maxNumActions, numCoalitions);
			}
		}

		smtSupportEnumeration.setIndexes(strategies);
		smtSupportEnumeration.setNumPlayers(numCoalitions);
		smtSupportEnumeration.init();

		/*
		nlpSupportEnumeration = new CSGSupportEnumerationGurobi(maxNumActions, numCoalitions);
		nlpSupportEnumeration.setIndexes(strategies);
		nlpSupportEnumeration.setNumPlayers(numCoalitions);
		*/

		done = true;
		k = 0;
		timeTaken = System.currentTimeMillis();
		while (true) {
			for (s = 0; s < csg.getNumStates(); s++) {
				//System.out.println("\ns " + s);
				sw = null;
				switch (eqType) {
					case CORR : {
						if (rew) {
							if (t == ExpressionTemporal.R_C) {
								sw = stepCorrelatedEquilibria(csg, rewards, mmap, null, sol, s, min, crit);
							}
							else {
								sw = stepCorrelatedEquilibria(csg, null, mmap, null, sol, s, min, crit);
							}
						}
						break;
					}
					default : {
						if (rew) {
							if (t == ExpressionTemporal.R_C) {
								eq = stepEquilibria(csg, rewards, mmap, null, sol, s, min);
								addStateRewards(eq, rewards, s, min);
							}
							else {
								eq = stepEquilibria(csg, null, mmap, null, sol, s, min);
							}
						}
						else {
							eq = stepEquilibria(csg, null, mmap, null, sol, s, min);
						}
						sw = swne(eq, null, min);
					}
				}
				for (c = 0; c < numCoalitions; c++) {
					val[c][s] = sw[c + 1];
				}
			}
			for (s = 0; s < csg.getNumStates(); s++) {
				for (c = 0; c < numCoalitions; c++) {
					sol[c][s] = val[c][s];
				}
				r[s] = 0.0;
				for (c = 0; c < numCoalitions; c++) {
					r[s] += sol[c][s];
				}
			}
			for (c = 0; c < numCoalitions; c++) {
				done = done & PrismUtils.doublesAreClose(sol[c], tmp[c], termCritParam, termCrit == TermCrit.ABSOLUTE);
			}
			k++;
			if (done || k == bounds[0]) {
				break;
			}
			else if (!done && k == maxIters) {
				throw new PrismException("Could not converge after " + k + " iterations");
			}
			else {
				done = true;
				for (c = 0; c < numCoalitions; c++) {
					tmp[c] = Arrays.copyOf(sol[c], sol[c].length);
				}
			}
		}
		timeTaken = System.currentTimeMillis() - timeTaken;
		mainLog.println();
		for (c = 0; c < numCoalitions; c++) {
			mainLog.println("Result for coalition " + coalitions.get(c) + ": " + sol[c][csg.getFirstInitialState()] + " (value in the intial state).");
		}
		r = new double[csg.getNumStates()];
		for (s = 0; s < csg.getNumStates(); s++) {
			r[s] = 0.0;
			for (c = 0; c < numCoalitions; c++) {
				r[s] += sol[c][s];
			}
		}
		res.soln = r;
		res.numIters = k;
		res.timeTaken = timeTaken /  1000.0;
		return res;
	}

	/**
	 *
	 *
	 * @param sol
	 * @param eq
	 * @param s
	 * @return
	 */
	public boolean checkEquilibriumChange(double[][] sol, double[] eq, int s) {
		int p;
		boolean result = true;
		for (p = 0; p < numCoalitions; p++) {
			result = result && Double.compare(sol[p][s], eq[p + 1]) == 0;
			if (!result)
				return true;
		}
		return false;
	}

	/**
	 *
	 *
	 * @param games
	 * @param sp
	 * @param p
	 */
	public void buildSubGames(Set<BitSet> games, BitSet sp, int p) {
		BitSet prod = new BitSet();
		prod.set(p);
		games.add((BitSet) prod.clone());
		for(int cp = sp.nextSetBit(0); cp >= 0; cp = sp.nextSetBit(cp + 1)) {
			BitSet newprod = new BitSet();
			newprod.or(prod);
			newprod.set(cp);
			games.add(newprod);
		}
	}

	/**
	 *
	 *
	 * @param games
	 * @param sp
	 * @param p
	 */
	public void buildSubGames(Map<Integer, Set<BitSet>> games, BitSet sp, int p) {
		BitSet prod = new BitSet();
		prod.set(p);
		games.get(prod.cardinality()).add((BitSet) prod.clone());
		for(int cp = sp.nextSetBit(0); cp >= 0; cp = sp.nextSetBit(cp + 1)) {
			BitSet newprod = new BitSet();
			newprod.or(prod);
			newprod.set(cp);
			games.get(newprod.cardinality()).add(newprod);
		}
	}

	/**
	 *
	 *
	 * @param csg
	 * @param coalitions
	 * @param rewards
	 * @param targets
	 * @param remain
	 * @param eqType
	 * @param crit
	 * @param min
	 * @return
	 * @throws PrismException
	 */
	public ModelCheckerResult computeReachEquilibria(CSG<Double> csg, List<Coalition> coalitions, List<CSGRewards<Double>> rewards, BitSet[] targets, BitSet[] remain, int eqType, int crit, boolean min) throws PrismException {
		// Initialization
		ModelCheckerResult[] coalitionResults = new ModelCheckerResult[coalitions.size()];
		ModelCheckerResult result = new ModelCheckerResult();
		List<List<List<Map<BitSet, Double>>>> localStrategies = null;
		List<List<Map<BitSet, Double>>> singleStrategies = null;
		List<Map<Integer, BitSet>> mapping = null;
		BitSet[] onlyTargets = new BitSet[targets.length];
		BitSet[] phi1 = new BitSet[3];
		BitSet known = new BitSet();
		BitSet temp = new BitSet();
		double[][] solution = new double[coalitions.size()][csg.getNumStates()];
		double[][] values = new double[coalitions.size()][csg.getNumStates()];
		double[][] temporary = new double[coalitions.size()][csg.getNumStates()];
		double[] equilibrium;
		double[] rewardsArray = new double[csg.getNumStates()];
		int i, j, k, p, s;
		boolean done, withRewards;
		long precomputationTime;

		// helper for epsilon calculation
		ArrayList<List<Map<Integer, BitSet>>> mappingHistory = new ArrayList<List<Map<Integer, BitSet>>>();
		ArrayList<ArrayList<ArrayList<Integer>>> strategyHistory = new ArrayList<ArrayList<ArrayList<Integer>>>();
		ArrayList<HashMap<BitSet, ArrayList<Double>>> utilitiesHistory = new ArrayList<HashMap<BitSet, ArrayList<Double>>>();
		ArrayList<ArrayList<ArrayList<HashMap<BitSet, Double>>>> ceConstraintsHistory = new ArrayList<ArrayList<ArrayList<HashMap<BitSet, Double>>>>();
		ArrayList<HashMap<BitSet, Integer>> ceVarMapHistory = new ArrayList<HashMap<BitSet, Integer>>();


		// Check if generating strategies is enabled
		if (genStrat) {
			mdpmc.setGenStrat(true);
			mapping = new ArrayList<Map<Integer, BitSet>>();
			singleStrategies = new ArrayList<List<Map<BitSet, Double>>>();
			localStrategies = new ArrayList<List<List<Map<BitSet, Double>>>>();
			for (i = 0; i < coalitions.size(); i++) {
				mapping.add(i, new HashMap<Integer, BitSet>());
				localStrategies.add(i, new ArrayList<List<Map<BitSet, Double>>>());
				localStrategies.get(i).add(0, new ArrayList<Map<BitSet, Double>>());
				for (j = 0; j < csg.getNumStates(); j++) {
					localStrategies.get(i).get(0).add(j, null);
				}
			}
		}

		// Check if rewards are provided
		withRewards = rewards != null;

		// Initialize onlyTargets and known BitSet arrays
		for (i = 0; i < targets.length; i++) {
			onlyTargets[i] = new BitSet();
			onlyTargets[i].or(targets[i]);
			for (j = 0; j < targets.length; j++) {
				if (i != j) {
					onlyTargets[i].andNot(targets[j]);
				}
			}
			known.or(targets[i]);
		}

		// Precomputation for different objective types
		if (!withRewards) {
			for (i = 0; i < 2; i++) {
				phi1[i] = new BitSet();
				if (remain[i] == null) {
					phi1[i].set(0, csg.getNumStates());
				} else {
					phi1[i].or(remain[i]);
				}
			}
			phi1[2] = new BitSet();
			phi1[2].or(phi1[0]);
			phi1[2].and(phi1[1]); // Intersection of phi1(1) and phi1(2)
			temp.clear();
			temp.or(phi1[0]);
			phi1[0].andNot(phi1[1]); // phi1(1) minus phi1(2)
			phi1[1].andNot(temp); // phi1(2) minus phi1(1)
			known.or(phi1[0]);
			known.or(phi1[1]);
			temp.clear();
			temp.set(0, csg.getNumStates());
			temp.andNot(phi1[2]);
			known.or(temp);
		}

		// Build coalitions
		buildCoalitions(csg, coalitions);
		dominated = new BitSet[numCoalitions];
		dominating = new BitSet[numCoalitions];
		mainLog.println();
		findMaxRowsCols(csg);

		mainLog.println("Starting equilibrium computation (solver=" + setSolver(eqType) + ")...");
		mainLog.println("Checking whether all objectives are reachable...");

		// Check assumption: All objectives are reachable from all states with probability 1
		if (assumptionCheck) {
			for (i = 0; i < targets.length; i++) {
				temp.clear();
				if (!withRewards) {
					if (remain[i] != null) {
						temp.or(remain[i]);
						temp.flip(0, csg.getNumStates());
						temp.andNot(targets[i]);
					}
				}
				temp.or(mdpmc.prob0((MDP) csg, null, targets[i], false, null));
				temp.or(targets[i]);
				if (mdpmc.prob1((MDP) csg, null, temp, true, null).cardinality() != csg.getNumStates()) {
					throw new PrismException("At least one of the objectives is not reachable with probability 1 from all states");
				}
			}
		}

		k = 0;
		if (withRewards) {
			// Precomputation for rewards
			precomputationTime = System.currentTimeMillis();
			for (i = 0; i < targets.length; i++) {
				coalitionResults[i] = mdpmc.computeReachRewards((MDP) csg, (MDPRewards) rewards.get(i), targets[i], min);
				values[i] = coalitionResults[i].soln;
			}
			precomputationTime = System.currentTimeMillis() - precomputationTime;
			for (s = 0; s < csg.getNumStates(); s++) {
				if (targets[0].get(s) && targets[1].get(s)) {
					solution[0][s] = 0.0;
					solution[1][s] = 0.0;
				} else if (onlyTargets[0].get(s)) {
					solution[0][s] = 0.0;
					solution[1][s] = values[1][s];
				} else if (onlyTargets[1].get(s)) {
					solution[0][s] = values[0][s];
					solution[1][s] = 0.0;
				}
			}
		} else {
			// Precomputation for probabilistic objectives
			precomputationTime = System.currentTimeMillis();
			for (i = 0; i < targets.length; i++) {
				if (remain[i] != null) {
					coalitionResults[i] = mdpmc.computeUntilProbs(csg, remain[i], targets[i], min);
				} else {
					coalitionResults[i] = mdpmc.computeReachProbs((MDP) csg, targets[i], min);
				}
				values[i] = coalitionResults[i].soln;
			}
			precomputationTime = System.currentTimeMillis() - precomputationTime;
			for (s = 0; s < csg.getNumStates(); s++) {
				if (targets[0].get(s) && targets[1].get(s)) {
					solution[0][s] = 1.0;
					solution[1][s] = 1.0;
				} else if (onlyTargets[0].get(s)) {
					solution[0][s] = 1.0;
					solution[1][s] = values[1][s];
				} else if (onlyTargets[1].get(s)) {
					solution[0][s] = values[0][s];
					solution[1][s] = 1.0;
				} else if (phi1[0].get(s)) {
					solution[0][s] = values[0][s];
					solution[1][s] = 0.0;
				} else if (phi1[1].get(s)) {
					solution[0][s] = 0.0;
					solution[1][s] = values[1][s];
				} else if (!phi1[2].get(s)) {
					solution[0][s] = 0.0;
					solution[1][s] = 0.0;
				}
			}
		}

		mainLog.println();
		done = true;
		dominated = new BitSet[numCoalitions];
		dominating = new BitSet[numCoalitions];

		while (true) {
			for (s = 0; s < csg.getNumStates(); s++) {
				if (!known.get(s)) {
					if (genStrat) {
						singleStrategies = new ArrayList<List<Map<BitSet, Double>>>();
						mapping.clear();
						for (p = 0; p < 2; p++) {
							mapping.add(p, new HashMap<Integer, BitSet>());
						}
					}
					equilibrium = stepEquilibriaTwoPlayer(csg, rewards, mapping, singleStrategies, solution, s, eqType, crit, withRewards, min);
					values[0][s] = equilibrium[1];
					values[1][s] = equilibrium[2];

					// Store generated strategies based on the equilibrium type
					if (genStrat) {
						List<Map<Integer, BitSet>> copyMapping = new ArrayList<Map<Integer, BitSet>>(mapping);
						ArrayList<ArrayList<Integer>> copyStrategy = new ArrayList<ArrayList<Integer>>(strategies);
						HashMap<BitSet, ArrayList<Double>> copyUtilities = new HashMap<>(utilities);
						ArrayList<ArrayList<HashMap<BitSet, Double>>> copyCEConstraints = new ArrayList<ArrayList<HashMap<BitSet, Double>>>(ceConstraints);
						HashMap<BitSet, Integer> copyCEVarMap = new HashMap<>(ceVarMap);

						while (mappingHistory.size() <= s) {
							mappingHistory.add(null);
							strategyHistory.add(null);
							utilitiesHistory.add(null);
							ceVarMapHistory.add(null);
							ceConstraintsHistory.add(null);
						}

						mappingHistory.set(s, copyMapping);
						strategyHistory.set(s, copyStrategy);
						utilitiesHistory.set(s, copyUtilities);
						ceVarMapHistory.set(s, copyCEVarMap);
						ceConstraintsHistory.set(s, copyCEConstraints);

						switch (eqType) {
							case CORR: {


								if (localStrategies.get(0).get(0).get(s) == null) {
									localStrategies.get(0).get(0).set(s, singleStrategies.get(0).get(0));
								} else if (!localStrategies.get(0).get(0).get(s).equals(singleStrategies.get(0).get(0)) && checkEquilibriumChange(solution, equilibrium, s)) {
									localStrategies.get(0).get(0).set(s, singleStrategies.get(0).get(0));
								}


								break;
							}
							default: {
								for (p = 0; p < coalitions.size(); p++) {
									if (localStrategies.get(p).get(0).get(s) == null) {
										localStrategies.get(p).get(0).set(s, singleStrategies.get(0).get(p));
									} else if (!localStrategies.get(0).get(0).get(s).equals(singleStrategies.get(0).get(p)) && checkEquilibriumChange(solution, equilibrium, s)) {
										localStrategies.get(p).get(0).set(s, singleStrategies.get(0).get(p));
									}
								}
							}
						}
					}
				}
			}

			for (s = 0; s < csg.getNumStates(); s++) {
				if (!known.get(s)) {
					solution[0][s] = values[0][s];
					solution[1][s] = values[1][s];
				}
				rewardsArray[s] = solution[0][s] + solution[1][s];
			}



			done = PrismUtils.doublesAreClose(solution[0], temporary[0], termCritParam, termCrit == TermCrit.ABSOLUTE);
			done = done & PrismUtils.doublesAreClose(solution[1], temporary[1], termCritParam, termCrit == TermCrit.ABSOLUTE);

			if (done) {
//				System.out.println(mapping);
//				System.out.println(utilities);
//				System.out.println(ceVarMap);

				// print strategies
//				System.out.println(strategies);

//				System.out.println(mappingHistory);
				// calculate epsilon for all states of local strategy


				Boolean computeRobustStrat = false;

				if (true && genStrat) {
					mainLog.println("Strategy synthesis complete. Starting epsilon calculation for all states...");

					SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
					Date currentDate = new Date();
					String dateString = formatter.format(currentDate);
					mainLog.println(dateString);

					ArrayList<Double> epsilonValues = new ArrayList<>(Collections.nCopies(localStrategies.get(0).get(0).size(), null));
					ArrayList<HashMap<BitSet, Double>> nonRobustStrategy = new ArrayList<>(Collections.nCopies(localStrategies.get(0).get(0).size(), null));
					ArrayList<ArrayList<Distribution<Double>>> robustStrategy = new ArrayList<>(Collections.nCopies(localStrategies.get(0).get(0).size(), null));
					ArrayList<HashMap<Pair<BitSet, BitSet>, Double>> trembles = new ArrayList<>(Collections.nCopies(localStrategies.get(0).get(0).size(), null));


					for (int state = 0; state < localStrategies.get(0).get(0).size(); state++) {
//					System.out.println("\tState " + state + ": " + csg.getStatesList().get(state));
						for (int player = 0; player < localStrategies.size(); player++) {
							// Iterate over each player within the coalition
							for (int iteration = 0; iteration < localStrategies.get(player).size(); iteration++) {
								Map<BitSet, Double> strategy = localStrategies.get(player).get(iteration).get(state);
								if (strategy != null) {
//								System.out.println("\t\tPlayer " + player + ", Iteration " + iteration + ":");

									HashMap<BitSet, Double> strategyToCheck = new HashMap<>();
									// Iterate over each action within the strategy
									for (Map.Entry<BitSet, Double> action : strategy.entrySet()) {
										BitSet bitSetKey = action.getKey();
										Double prop = action.getValue();
										List<Map<Integer, BitSet>> currentMapping = mappingHistory.get(state);
//									System.out.println("\t\t\tutilities: " + utilitiesHistory.get(state));
//									System.out.println("\t\t\tJoint Action " + bitSetKey + " : " + prop);
//									System.out.println("\t\t\tMapping: " + currentMapping);
//									System.out.println("\t\t\tStrategies0: " + strategyHistory.get(state).get(0));
//									System.out.println("\t\t\tStrategies1: " + strategyHistory.get(state).get(1));

										// what was previously strategies is now strategyHistory.get(state)


										BitSet jointAction = new BitSet();
										// mapping of bitset to joint action
										// player 0
										BitSet player0action = (BitSet) actionIndexes[0].clone();
										player0action.and(bitSetKey);


										try {
											int actionIndex = getKeyForBitSet(currentMapping.get(0), player0action);
											jointAction.set(strategyHistory.get(state).get(0).get(actionIndex));
										} catch (Exception e) {
											continue;
										}

										// player 1
										BitSet player1action = (BitSet) actionIndexes[1].clone();
										player1action.and(bitSetKey);

										try {
											int actionIndex = getKeyForBitSet(currentMapping.get(1), player1action);
											jointAction.set(strategyHistory.get(state).get(1).get(actionIndex));
										} catch (Exception e) {
											continue;
										}


										strategyToCheck.put(jointAction, prop);
									}

									try {
//									System.out.println("\t\t\tstrategy to check: " + strategyToCheck);
										if (strategyHistory.get(state).get(0).size() == 1 || strategyHistory.get(state).get(1).size() == 1 || strategyToCheck.isEmpty()) {
//										System.out.println("\t\t\tNo constraints");
//										epsilonValues.add(null);
											continue;
										}
										CSGCorrelatedRobustGurobi robustGurobi = new CSGCorrelatedRobustGurobi(utilitiesHistory.get(state).size(), coalitions.size());
										EquilibriumRobustnessResult robustnessResult = robustGurobi.computeRobustness(strategyToCheck, utilitiesHistory.get(state), ceConstraintsHistory.get(state), strategyHistory.get(state), ceVarMapHistory.get(state), eqType);
										epsilonValues.set(state, robustnessResult.getEpsilon());
										nonRobustStrategy.set(state, strategyToCheck);
										trembles.set(state, robustnessResult.getTrembles());

//									System.out.println("\t\t\tepsilon: " + epsilon);

										if (computeRobustStrat) {
											CSGRobustCorrelatedZ3 robustZ3 = new CSGRobustCorrelatedZ3(utilitiesHistory.get(state).size(), coalitions.size());
											EquilibriumResult robustStrat = robustZ3.computeEquilibrium(utilitiesHistory.get(state), ceConstraintsHistory.get(state), strategyHistory.get(state), ceVarMapHistory.get(state), eqType);

											robustStrategy.set(state, robustStrat.getStrategy());
										}


									} catch (GRBException e) {
										System.out.println(e.getMessage());
									}
								} else {
//								System.out.println("\t\t\tPlayer " + player + ", Iteration " + iteration + ": No strategy.");
								}
							}
						}
					}

					if (computeRobustStrat) {
						mainLog.println("Iterating through all states...");

						for (int stateIndex = 0; stateIndex < epsilonValues.size(); stateIndex++) {
							if (epsilonValues.get(stateIndex) == null) {
								continue;
							}
							mainLog.println("State " + stateIndex + ": " + csg.getStatesList().get(stateIndex));
							mainLog.println("\tEpsilon: " + epsilonValues.get(stateIndex));
							mainLog.println("\tNon-robust strategy: " + nonRobustStrategy.get(stateIndex));
							mainLog.println("\t\tTrembles the non-robust strategy is robust against:");
							HashMap<Pair<BitSet, BitSet>, Double> stateTremble = trembles.get(stateIndex);
							for (Pair<BitSet, BitSet> singleTremble : stateTremble.keySet()) {
								mainLog.println("\t\t\t" + singleTremble + ": " + stateTremble.get(singleTremble));
							}
							mainLog.println("\tRobust strategy: " + robustStrategy.get(stateIndex));

							mainLog.println("\tVarMap: " + ceVarMapHistory.get(stateIndex));
							mainLog.println("\tUtilities: " + utilitiesHistory.get(stateIndex));
//						System.out.println("\tMapping: " + mappingHistory.get(stateIndex));
//						System.out.println("\tStrategies: " + strategyHistory.get(stateIndex));
//						System.out.println("\tNon-Robust Action Player 0: " + csg.getAvailableActions(stateIndex));
//						System.out.println(csg.getIndexes()[stateIndex]);
							mainLog.println("\tOutcome 0: " + csg.getAction(stateIndex,0));
							mainLog.println("\tOutcome 1: " + csg.getAction(stateIndex,1));
							mainLog.println("\tOutcome 2: " + csg.getAction(stateIndex,2));
							mainLog.println("\tOutcome 3: " + csg.getAction(stateIndex,3));
							mainLog.println("\tAll Available Actions: " + csg.getAvailableActions(stateIndex));

						}
					} else {

						// Remove null values from the ArrayList
						epsilonValues.removeIf(value -> value == null);

						if (!epsilonValues.isEmpty()) {

							// Sort the ArrayList
							Collections.sort(epsilonValues);

							// Compute the necessary values
							double minEps = epsilonValues.get(0);
							double maxEps = epsilonValues.get(epsilonValues.size() - 1);
							double q1Eps = getMedian(epsilonValues.subList(0, epsilonValues.size() / 2));
							double medianEps = getMedian(epsilonValues);
							double q3Eps = getMedian(epsilonValues.subList((epsilonValues.size() + 1) / 2, epsilonValues.size()));


//						// HashMap to store frequencies
//						HashMap<Double, Integer> frequencyMap = new HashMap<>();
//
//						double maxEps = epsilonValues.get(0);
//						double minEps = epsilonValues.get(0);
//						int maxIndex = 0;
//						int minIndex = 0;
//
//						for (int eps_i = 1; eps_i < epsilonValues.size(); eps_i++) {
//
//							Double currentValue = epsilonValues.get(eps_i);
//
//							if (currentValue == null) {
//								continue;
//							}
//
//							frequencyMap.put(currentValue, frequencyMap.getOrDefault(currentValue, 0) + 1);
//
//							if (currentValue > maxEps) {
//								maxEps = currentValue;
//								maxIndex = eps_i;
//							}
//							if (currentValue < minEps) {
//								minEps = currentValue;
//								minIndex = eps_i;
//							}
//						}
//
//
//						mainLog.println("Max-epsilon-value: " + maxEps);
//						mainLog.println("Max-epsilon-index: " + maxIndex);
//						mainLog.println("Min-epsilon-value: " + minEps);
//						mainLog.println("Min-epsilon-index: " + minIndex);
//
//						// Print the frequency of each value
//						for (Map.Entry<Double, Integer> entry : frequencyMap.entrySet()) {
//							mainLog.println("Epsilon value " + entry.getKey() + " occurs " + entry.getValue() + " times");
//						}

							mainLog.println("Max-epsilon-value: " + maxEps);
							mainLog.println("Min-epsilon-value: " + minEps);
							mainLog.println("Q1-epsilon-value: " + q1Eps);
							mainLog.println("Q2-epsilon-value: " + medianEps);
							mainLog.println("Q3-epsilon-value: " + q3Eps);
						}
						else {
							mainLog.println("Epsilon map is empty");
						}

					}

				}

//
				// print strategies
//				System.out.println("Coalition " + coalitions.get(0) + ": " + localStrategies.get(0).get(0));


//				System.out.println("Calculating epsilon for final strategy");
//				System.out.println(tmpEpsilonResult.getStrategy().toString());
//				System.out.println(epsilonSolver.getEpsilonForEquilibriumResult(tmpEpsilonResult, utilities, ceConstraints, strategies, crit));
//
//				System.out.println("Coalition " + coalitions.get(0) + ": " + localStrategies.get(0).get(0));
//				System.out.println(utilities);
//				System.out.println(ceVarMap);
				break;
			}
			else if (!done && k == maxIters) {
				throw new PrismException("Could not converge after " + k + " iterations");
			} else {
				done = true;
				temporary[0] = Arrays.copyOf(solution[0], solution[0].length);
				temporary[1] = Arrays.copyOf(solution[1], solution[1].length);
			}

			k++;
		}

		mainLog.println("\nValue iteration converged after " + k + " iterations.");
		mainLog.println("\nPrecomputation took " + precomputationTime / 1000.0 + " seconds.");
		mainLog.println("Coalition results (initial state): (" + solution[0][csg.getFirstInitialState()] + "," + solution[1][csg.getFirstInitialState()] + ")");

		result.soln = rewardsArray;

		// Generate strategy data structures based on the equilibrium type and reward availability
		if (genStrat)  {
			switch (eqType) {
				case CORR: {
					if (withRewards)
						result.strat = new CSGStrategy(csg, localStrategies, coalitionResults, targets, CSGStrategyType.EQUILIBRIA_CE_R);
					else
						result.strat = new CSGStrategy(csg, localStrategies, coalitionResults, targets, CSGStrategyType.EQUILIBRIA_CE_P);
					break;
				}
				default: {
					if (withRewards)
						result.strat = new CSGStrategy(csg, localStrategies, coalitionResults, targets, CSGStrategyType.EQUILIBRIA_R);
					else
						result.strat = new CSGStrategy(csg, localStrategies, coalitionResults, targets, CSGStrategyType.EQUILIBRIA_P);
				}
			}
		}
		result.numIters = k;

//		// creating targeted action noise per state
//		List<Map<BitSet, Double>> noise = new ArrayList<>();
//
//		// introducing noise to first joint action pair in the first state
//		Map<BitSet, Double> map = new HashMap<>();
//		// specifying that the first two joint action pairs should be chosen with probability 0.4
//		// remaining probability mass is distributed uniformly among the remaining joint action pairs equally
//		Iterator<BitSet> iterator = localStrategies.get(0).get(0).get(0).keySet().iterator();
//		map.put(iterator.next(), 0.4);
//		map.put(iterator.next(), 0.19);
//		System.out.println("noise map: " + map);
//		// this map corresponds to the first state
//		noise.add(map);
//		injectTargetNoise(localStrategies, csg, noise, eqType, true);

//		injectStaticNoise(localStrategies, csg, List.of(0.0), eqType, true);
//		injectStaticNoise(localStrategies, csg, List.of(0.1), eqType, true);
//		injectStaticNoise(localStrategies, csg, List.of(1.0), eqType, true);

		return result;

	}

	private double getMedian(List<Double> values) {
		int size = values.size();
		if (size % 2 == 0) {
			return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
		} else {
			return values.get(size / 2);
		}
	}

	// function to inject state noise into a joint action
	// noise is a list of joint actions pairs and their respective noise
	// position of this map in the list corresponds to the state
	public void injectTargetNoise(List<List<List<Map<BitSet, Double>>>> localStrategies, CSG<Double> csg, List<Map<BitSet, Double>> noise, int eqType, boolean support) throws PrismException {
		System.out.println("injecting targeted noise into local strategy");

		// handling the case of correlated equilibrium because the mixed strategy is stored in player 0
		if (eqType != 2) {
			System.out.println("injecting static noise currently only supports CE");
			return;
		}

		// iterating over all noises to inject
		for (int i = 0; i < noise.size(); i++) {

			// if eyType CORRELATED_EQUILIBRIUM --> mixed strategy over joint actions is stored in player 0
			List<List<Map<BitSet, Double>>> player = localStrategies.get(0);

			// currently all iterations are stored in 0; However to make it bulletproof, we're accessing the "last" iteration
			List<Map<BitSet, Double>> iteration = player.get(player.size() - 1);

			// keep in mind that this state it the state number before the re-enumaration
			Map<BitSet, Double> state = iteration.get(i);

			// get available support actions
			List<BitSet> availableActions = new ArrayList<>();

			// if support is true, we do not introduce new joint actions which haven't been there
			if (support) {
				for (Map.Entry<BitSet, Double> entry : localStrategies.get(0).get(0).get(i).entrySet()) {
					availableActions.add(entry.getKey());
				}
			} else {
				availableActions = getStateJointActions(csg, i);
			}

			if (availableActions.get(0).isEmpty()) {
				System.out.println("Noise injection impossible since no actions are available at given state " + i);
				return;
			}

			Map<BitSet, Double> availableJointActions = new HashMap<>();

			double targetDistributionSum = 0.0;
			// iterate through all target noise values and update the placeholder values
			for (Map.Entry<BitSet, Double> actionMap : noise.get(i).entrySet()) {
				BitSet targetJointAction = actionMap.getKey();
				Double targetProp = actionMap.getValue();
				targetDistributionSum += targetProp;
				availableJointActions.put(targetJointAction, targetProp);
				availableActions.remove(targetJointAction);
			}

			// get number of joint actions that have not been assigned a target probability
			int remainingJointActions = availableActions.size();

			// distribute the remaining probability mass evenly among the remaining joint actions
			double remainingProbabilityMass = 1.0 - targetDistributionSum;

			if (remainingProbabilityMass < 0.0) {
				System.out.println("The sum of the target distribution is greater than 1.0");
				return;
			} else if (remainingProbabilityMass > 0.0) {
				// iterate through remaining joint actions and distribute the remaining prop mass evenly
				Double baseDistort = remainingProbabilityMass / remainingJointActions;
				for (BitSet entry : availableActions) {
					availableJointActions.put(entry, baseDistort);

				}
			}

			// sanity check
			if (targetDistributionSum > 1.0) {
				throw new PrismException("The sum of the target distribution is greater than 1.0");
			}

			double sum = 0.0;
			for (Double value : availableJointActions.values()) {
				sum += value;
			}
			double epsilon = 0.000000000001;
			if (sum > (1.0 + epsilon) || sum < (1.0 - epsilon)) {
				System.out.println("The sum of the values is: " + sum);
				throw new PrismException("The sum of the values is not 1.0 +- epsilon");
			} else {
				System.out.println("Sanity passed with sum: " + sum);
			}

			System.out.println("distorted joint actions: " + availableJointActions);

			// update the local strategy with the new distorted joint actions
			iteration.set(i, availableJointActions);
		}
	}

	public Integer getKeyForBitSet(Map<Integer, BitSet> map, BitSet target) {
		for (Map.Entry<Integer, BitSet> entry : map.entrySet()) {
			if (entry.getValue().equals(target)) {
				return entry.getKey();
			}
		}
		return null;
	}

	// function to inject static noise
	public void injectStaticNoise(List<List<List<Map<BitSet, Double>>>> localStrategies, CSG<Double> csg, List<Double> noise, int eqType, boolean support) throws PrismException {
		System.out.println("injecting static noise into local strategy");

		if (eqType != 2) {
			System.out.println("injecting static noise currently only supports CE");
			return;
		}

		// iterating over all noises to inject
		for (int i = 0; i < noise.size(); i++) {

			// if eyType CORRELATED_EQUILIBRIUM --> mixed strategy over joint actions is stored in player 0
			List<List<Map<BitSet, Double>>> player = localStrategies.get(0);

			// currently all iterations are stored in 0; However to make it bulletproof, we're accessing the "last" iteration
			List<Map<BitSet, Double>> iteration = player.get(player.size() - 1);

			// keep in mind that this state it the state number before the re-enumaration
			Map<BitSet, Double> state = iteration.get(i);

			// get the static noise factor for state i
			Double staticNoise = noise.get(i);

			// get available support actions
			List<BitSet> availableActions = new ArrayList<>();

			// if support is true, we do not introduce new joint actions which haven't been there
			if (support) {
				for (Map.Entry<BitSet, Double> entry : localStrategies.get(0).get(0).get(i).entrySet()) {
					availableActions.add(entry.getKey());
				}
			} else {
				availableActions = getStateJointActions(csg, i);
			}

			if (availableActions.get(0).isEmpty()) {
				System.out.println("Noise injection impossible since no actions are available at given state " + i);
				return;
			}

			double baseDistort = staticNoise / availableActions.size();

			Map<BitSet, Double> availableJointActions = new HashMap<>();

			// if support is false, we assign
			if (!support) {
				for (BitSet jointAction : availableActions) {
					availableJointActions.put(jointAction, baseDistort);
				}
			}



			// iterate through all joint actions
			for (Map.Entry<BitSet, Double> entry : state.entrySet()) {
				BitSet jointAction = entry.getKey();
				Double prop = entry.getValue();

				Double updatedValue = ((1.0 - staticNoise) * prop) + baseDistort;
				availableJointActions.put(jointAction, updatedValue);

				System.out.println("joint action: " + jointAction + " " + prop);
				System.out.println("\tdistorted prop: " + updatedValue);
			}

			System.out.println("distorted joint actions: " + availableJointActions);

			// sanity check
			double sum = 0.0;
			for (Double value : availableJointActions.values()) {
				sum += value;
			}
			double epsilon = 0.000000000001;
			if (sum > (1.0 + epsilon) || sum < (1.0 - epsilon)) {
				System.out.println("The sum of the values is: " + sum);
				throw new PrismException("The sum of the values is not 1.0 +- epsilon");
			} else {
				System.out.println("Sanity passed with sum: " + sum);
			}

			// update the local strategy with the new distorted joint actions
			iteration.set(i, availableJointActions);
		}
	}

	// helper function for getStateJointActions
	private List<BitSet> generateCombinations(List<BitSet> input) {
		List<BitSet> result = new ArrayList<>();
		generateCombinations(input, 0, new BitSet(), result);
		return result;
	}

	// helper function for getStateJointActions
	private void generateCombinations(List<BitSet> input, int index, BitSet current, List<BitSet> result) {
		if (index == input.size()) {
			result.add((BitSet) current.clone());
			return;
		}

		BitSet bitSet = input.get(index);
		for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
			current.set(i);
			generateCombinations(input, index + 1, current, result);
			current.clear(i);
		}
	}

	// function which returns all possible (and enabled) joint actions for a given state
	public List<BitSet> getStateJointActions(CSG<Double> csg, int state) {
		List<BitSet> jointActions = new ArrayList<>();

		BitSet concurrentPlayers = csg.getConcurrentPlayers(state);

		for (int player = concurrentPlayers.nextSetBit(0); player >= 0; player = concurrentPlayers.nextSetBit(player+1)) {
			jointActions.add(csg.getIndexesForPlayer(state,player));
		}

		return generateCombinations(jointActions);
	}

	// helper function to print mixed strategy over joint actions per state
	public void printLocalStrategiesState(List<List<List<Map<BitSet, Double>>>> localStrategies, CSG<Double> csg) {
		for (int state = 0; state < localStrategies.get(0).get(0).size(); state++) {
			System.out.println("State " + state + ": " + csg.getStatesList().get(state));
			for (int player = 0; player < localStrategies.size(); player++) {
				// Iterate over each player within the coalition
				for (int iteration = 0; iteration < localStrategies.get(player).size(); iteration++) {
					Map<BitSet, Double> strategy = localStrategies.get(player).get(iteration).get(state);
					if (strategy != null) {
						System.out.println("\tPlayer " + player + ", Iteration " + iteration + ":");
						// Iterate over each action within the strategy
						for (Map.Entry<BitSet, Double> action : strategy.entrySet()) {

							BitSet bitSetKey = action.getKey();
							Double prop = action.getValue();

							// get joint action
							StringBuilder jointAction = new StringBuilder();
							for (int i = bitSetKey.nextSetBit(0); i >= 0; i = bitSetKey.nextSetBit(i+1)) {
								jointAction.append(csg.getActions().get(i-1)).append(" ");
							}
							System.out.println("\t\tJoint Action " + jointAction + " : " + prop);
						}
					} else {
						System.out.println("  Player " + player + ", Iteration " + iteration + ": No strategy.");
					}
				}
			}
		}
	}


	/**
	 * Selects the equilibrium that minimises the difference among the highest and lowest payoffs.
	 *
	 * @param eqs The set of equilibria
	 * @param strats Strategy to be updated
	 * @param min If minimising
	 * @return
	 */
	public double[] fair(double[][] eqs, List<List<Map<BitSet, Double>>> strats, boolean min) {
		List<Map<BitSet, Double>> dist = null;
		BitSet ifr = new BitSet();
		double[] eq = new double[numCoalitions+1];
		double[][] df = new double[eqs.length][2];
		double diff;
		int i, p;
		for (p = 0; p < numCoalitions; p++) { // gets first equilibrium
			eq[0] += eqs[0][p];
			eq[p+1] = eqs[0][p];
		}
		df[0][0] = Arrays.stream(eqs[0]).max().getAsDouble();
		df[0][1] = Arrays.stream(eqs[0]).min().getAsDouble();
		diff = df[0][0] - df[0][1];
		ifr.set(0);
		if (strats != null)
			dist = strats.get(0);
		for (i = 1; i < eqs.length; i++) {
			df[i][0] = Arrays.stream(eqs[i]).max().getAsDouble();
			df[i][1] = Arrays.stream(eqs[i]).min().getAsDouble();
			if (Double.compare(df[i][0]-df[i][1], diff) < 0) {
				diff = df[i][0]-df[i][1];
				ifr.clear();
				ifr.set(i);
			}
			else if (Double.compare(df[i][0]-df[i][1], diff) == 0) {
				ifr.set(i);
			}
		}
		if (ifr.cardinality() == 1) { //if there is one single equilibrium that minimises the difference, we're done
			i = ifr.nextSetBit(0);
			eq[0] = 0.0;
			for (p = 0; p < numCoalitions; p++) {
				eq[0] += eqs[i][p];
				eq[p+1] = eqs[i][p];
			}
			if(strats != null)
				dist = strats.get(i);
			final List<Map<BitSet, Double>> strat = dist;
			if (strats != null)
				strats.removeIf((List<Map<BitSet, Double>> e) -> !e.equals(strat));
			if (min) {
				for (i = 0; i < eq.length; i++)
					eq[i] = -1.0 * eq[i];
			}
			return eq;
		}
		else { // if not, we have to look at the sum
			double[][] neweqs = new double[ifr.cardinality()][numCoalitions];
			int j = 0;
			for (i = ifr.nextSetBit(0); i >=0; i = ifr.nextSetBit(i+1)) {
				for (p = 0; p < numCoalitions; p++) {
					neweqs[j][p] = eqs[i][p];
					if (strats != null)
						Collections.swap(strats, i, j);
				}
				j++;
			}
			return swne(neweqs, strats, min);
		}
	}

	/**
	 * Selects the equilibrium that maximises the sum of payoffs.
	 *
	 * @param eqs The set of equilibria.
	 * @param strats The strategy to be updated.
	 * @param min If minimising.
	 * @return
	 */
	public double[] swne(double[][] eqs, List<List<Map<BitSet, Double>>> strats, boolean min) {
		List<Map<BitSet, Double>> dist = null;
		BitSet isw = new BitSet();
		double[] eq = new double[numCoalitions+1];
		double sum;
		int p;
		eq[0] = 0.0;
		for (p = 0; p < numCoalitions; p++) { // gets first equilibrium
			eq[0] += eqs[0][p];
			eq[p+1] = eqs[0][p];
		}
		isw.set(0);
		if (strats != null) {
			dist = strats.get(0);
		}
		for (int i = 1; i < eqs.length; i++) { // if there are more than one
			sum = 0.0;
			for (p = 0; p < numCoalitions; p++) { // computes the sum
				sum += eqs[i][p];
			}
			if (Double.compare(sum, eq[0]) > 0) { // selects equilibrium if it has a higher sum
				eq[0] = 0.0;
				for (p = 0; p < numCoalitions; p++) {
					eq[0] += eqs[i][p];
					eq[p+1] = eqs[i][p];
				}
				isw.clear();
				isw.set(i);
				if(strats != null)
					dist = strats.get(i);
			}
			else if (Double.compare(sum, eq[0]) == 0) {
				isw.set(i);
			}
		}
		if (isw.cardinality() != 1) {
			int idx = findMaxEqIndexes(isw, eqs, eq);
			sum = 0.0;
			for (p = 0; p < numCoalitions; p++) { // computes the sum
				sum += eqs[idx][p];
				eq[p+1] = eqs[idx][p];
			}
			eq[0] = sum;
			if(strats != null)
				dist = strats.get(idx);
		}
		final List<Map<BitSet, Double>> strat = dist;
		if (strats != null)
			strats.removeIf((List<Map<BitSet, Double>> e) -> !e.equals(strat));
		if (min) {
			for (int i = 0; i < eq.length; i++)
				eq[i] = -1.0 * eq[i];
		}
		return eq;
	}

	/**
	 *
	 *
	 * @param indexes
	 * @param eqs
	 * @param eq
	 * @return
	 */
	public int findMaxEqIndexes(BitSet indexes, double[][] eqs, double eq[]) {
		int idx;
		BitSet tmp = new BitSet();
		BitSet[] maxindexes = new BitSet[numCoalitions];
		double max;
		for (int p = 0; p < numCoalitions; p++) {
			maxindexes[p] = new BitSet();
			max = eq[p+1];
			for (int i = indexes.nextSetBit(0); i >= 0; i = indexes.nextSetBit(i+1)) {
				if (Double.compare(eqs[i][p], max) > 0) {
					maxindexes[p].clear();
					maxindexes[p].set(i);
					max = eqs[i][p];
				}
				else if (Double.compare(eqs[i][p], max) == 0) {
					maxindexes[p].set(i);
				}
			}
		}
		if (maxindexes[0].cardinality() == 1) {
			idx =  maxindexes[0].nextSetBit(0);
			return idx;
		}
		else {
			tmp.or(maxindexes[0]);
			for (int p = 1; p < numCoalitions; p++) {
				tmp.and(maxindexes[p]);
				if (tmp.cardinality() == 1) {
					idx = tmp.nextSetBit(0);
					return idx;
				}
			}
			// if this part of the code is reached, all players get the same payoff for all equilibria in tmp
			idx = maxindexes[0].nextSetBit(0);
			return idx;
		}
	}

	/**
	 *
	 *
	 * @param eqs
	 * @param csgRewards1
	 * @param csgRewards2
	 * @param s
	 * @param min
	 */
	public void addStateRewards(double[][] eqs, CSGRewards<Double> csgRewards1, CSGRewards<Double> csgRewards2, int s, boolean min) {
		for (int e = 0; e < eqs.length; e++) {
			if (csgRewards1 != null)
				eqs[e][0] += ((min)? -1 * csgRewards1.getStateReward(s) : csgRewards1.getStateReward(s));
			if (csgRewards2 != null)
				eqs[e][1] += ((min)? -1 * csgRewards2.getStateReward(s) : csgRewards2.getStateReward(s));
		}
	}

	/**
	 *
	 *
	 * @param eqs
	 * @param rewards
	 * @param s
	 * @param min
	 */
	public void addStateRewards(double[][] eqs, List<CSGRewards<Double>> rewards, int s, boolean min) {
		int e, p;
		for (e = 0; e < eqs.length; e++) {
			for (p = 0; p < numCoalitions; p++) {
				if (rewards.get(p) != null)
					eqs[e][p] +=  ((min)? -1 * rewards.get(p).getStateReward(s) : rewards.get(p).getStateReward(s));
			}
		}
	}

	/**
	 *
	 *
	 * @param eqs
	 * @param rewards
	 * @param s
	 * @param min
	 */
	public void addStateRewards(double[] eqs, List<CSGRewards<Double>> rewards, int s, boolean min) {
		for (int p = 0; p < numCoalitions; p++) {
			if (rewards.get(p) != null)
				eqs[p+1] +=  ((min)? -1.0 * rewards.get(p).getStateReward(s) : rewards.get(p).getStateReward(s));
		}
	}

	/**
	 *
	 *
	 * @param csg
	 * @param rewards
	 * @param mmap
	 * @param strats
	 * @param val
	 * @param s
	 * @param min
	 * @param crit
	 * @return
	 * @throws PrismException
	 */
	public double[] stepCorrelatedEquilibria(CSG<Double> csg, List<CSGRewards<Double>> rewards, List<Map<Integer, BitSet>> mmap, List<List<Map<BitSet, Double>>> strats,
											 double[][] val, int s, boolean min, int crit) throws PrismException {
		EquilibriumResult result;
		ArrayList<Map<BitSet, Double>> eqstrat = null;
		BitSet idx = null, ps, tmp = null;
		double[] eqs = new double[numCoalitions+1];
		int c, i, q;
		buildStepGame(csg, rewards, mmap, val, s, min);
		clear();
		computeAssertions();
		for (c = 0; c < numCoalitions; c++) {
			for (q = 0; q < strategies.get(c).size(); q++) {
				ceConstraints.get(c).add(q, new HashMap<BitSet, Double>());
				for (Pair<BitSet, Double> e : assertions.get(c).get(q)) {
					ps = new BitSet();
					ps.or(e.getKey());
					ceConstraints.get(c).get(q).put(ps, e.second);
				}
			}
		}
		if (genStrat) {
			eqstrat = new ArrayList<Map<BitSet, Double>>();
			eqstrat.add(new HashMap<BitSet, Double>());
			tmp = new BitSet();
		}
		if (utilities.size() == 1) {
			for (BitSet e : utilities.keySet()) {
				for (c = 0; c < numCoalitions; c++) {
					eqs[0] += utilities.get(e).get(c);
					eqs[c+1] = utilities.get(e).get(c);
				}
				if (genStrat) {
					idx = new BitSet();
					for (c = 0; c < numCoalitions; c++) {
						tmp.clear();
						tmp.or(psupports.get(c));
						tmp.and(e);
						i = tmp.nextSetBit(0);
						idx.or(mmap.get(c).get(strategies.get(c).indexOf(i)));
					}
					eqstrat.get(0).put(idx, 1.0);
				}
			}
			if (genStrat) {
				strats.add(0, eqstrat);
			}
		}
		else {
			result = ceSolver.computeEquilibrium(utilities, ceConstraints, strategies, ceVarMap, crit);
			if (result.getStatus() == CSGResultStatus.SAT) {
				eqs[0] = 0.0;
				for (Double d : result.getPayoffVector()) {
					eqs[0] += d;
				}
				Arrays.fill(eqs, 0.0);
				for (c = 0; c < numCoalitions; c++) {
					eqs[c+1] = result.getPayoffVector().get(c);
				}
				for (BitSet e : ceVarMap.keySet()) {
					if (genStrat) {
						idx = new BitSet();
						for (c = 0; c < numCoalitions; c++) {
							tmp.clear();
							tmp.or(psupports.get(c));
							tmp.and(e);
							i = tmp.nextSetBit(0);
							idx.or(mmap.get(c).get(strategies.get(c).indexOf(i)));
						}
						if (Double.compare(result.getStrategy().get(0).get(ceVarMap.get(e)), 0.0) > 0)
							eqstrat.get(0).put(idx, result.getStrategy().get(0).get(ceVarMap.get(e)));
					}
				}
				if (genStrat) {
					strats.add(0, eqstrat);
				}
			}
			else {
				throw new PrismException(ceSolver.getSolverName() + " could not find an optimal solution for state " + s);
			}
		}
		if (rewards != null)
			addStateRewards(eqs, rewards, s, min);
		if (min) {
			for (i = 0; i < eqs.length; i++)
				eqs[i] = -1.0 * eqs[i];
		}
		return eqs;
	}

	/*
	public ArrayList<EquilibriumResult> stepParallelEquilibriaGurobi(HashSet<BitSet> supports) {
		ArrayList<EquilibriumResult> eqs = new ArrayList<EquilibriumResult>();
		List<Callable<EquilibriumResult>> tasks = new ArrayList<Callable<EquilibriumResult>>();
		//supportCount = 0;
		//System.out.println("Total supports: " + supports.size());
		for (final BitSet supp : supports) {
			Callable<EquilibriumResult> c = new Callable<EquilibriumResult>() {
				@Override
				public EquilibriumResult call() throws Exception {
					return stepEquilibriaGurobi(supp);
				}
			};
			tasks.add(c);
		}
		//ExecutorService exec = Executors.newCachedThreadPool();
		//ExecutorService exec = Executors.newFixedThreadPool(allSupports.size());
		ExecutorService exec = Executors.newFixedThreadPool(5);
		//ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		//ExecutorService exec = Executors.newSingleThreadExecutor();
		try {
			List<Future<EquilibriumResult>> results = exec.invokeAll(tasks);
			for (Future<EquilibriumResult> result : results) {
				if (result.get().getStatus() == CSGResultStatus.SAT) {
					eqs.add(result.get());
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			exec.shutdown();
		}
		return eqs;
	}

	public EquilibriumResult stepEquilibriaGurobi(BitSet supp) {
		CSGSupportEnumerationGurobi nlpSupportEnumeration;
		EquilibriumResult result = null;
		nlpSupportEnumeration = new CSGSupportEnumerationGurobi(maxNumActions, numCoalitions);
		nlpSupportEnumeration.setIndexes(strategies);
		nlpSupportEnumeration.setNumPlayers(numCoalitions);
		nlpSupportEnumeration.translateAssertions(assertions, mapActionIndex);
		result = nlpSupportEnumeration.computeEquilibria(supp, mapActionIndex);
		return result;
	}
	*/

	/**
	 *
	 *
	 * @param csg
	 * @param rewards
	 * @param mmap
	 * @param strats
	 * @param val
	 * @param s
	 * @param min
	 * @return
	 * @throws PrismException
	 */
	public double[][] stepEquilibria(CSG<Double> csg, List<CSGRewards<Double>> rewards, List<Map<Integer, BitSet>> mmap, List<List<Map<BitSet, Double>>> strats,
									 double[][] val, int s, boolean min) throws PrismException {
		EquilibriumResult eqsresult;
		EquilibriumResult eqsresultnlp;
		ArrayList<ArrayList<Double>> equilibria = new ArrayList<ArrayList<Double>>();
		ArrayList<Map<BitSet, Double>> eqstrat = null;
		BitSet active;
		long time;
		double[][] result;
		int n, p;

		buildStepGame(csg, rewards, mmap, val, s, min);
		active = csg.getConcurrentPlayers(s);

		time = System.currentTimeMillis();

		// Case when just one player has a choice
		if (active.cardinality() == 1) {
			return findSWNEOnePlayer(mmap, strats, eqstrat, active);
		}
		else {
			clear();
			computeAssertions();
			for (p = 0; p < numCoalitions; p++) {
				dominated[p] = findDominated(p);
				dominating[p] = findDominating(p);
			}
			buildAllSupports();
			if (checkAllZeroEntries()) {
				result = new double[1][numCoalitions];
				Arrays.fill(result[0], 0.0);
				if (genStrat) {
					eqstrat = new ArrayList<Map<BitSet, Double>>();
					extractStrategyFromSupport(mmap, eqstrat, (BitSet) utilities.keySet().toArray()[0]);
					strats.add(eqstrat);
				}
				return result;
			}
			if (utilities.size() == 1) {
				return findSWNEUniqueSupport(mmap, strats, eqstrat);
			}

			smtSupportEnumeration.translateAssertions(assertions, mapActionIndex);
			//nlpSupportEnumeration.translateAssertions(assertions, mapActionIndex);

			if (allSupports.size()== 1) {
				return findSWNEUniqueSupport(mmap, strats, eqstrat);
			}
			else {
				HashSet<BitSet> unknown = new HashSet<BitSet>();
				HashSet<BitSet> unsat = new HashSet<BitSet>();
				HashSet<BitSet> sat = new HashSet<BitSet>();

				//System.out.println(allSupports.size());
				for (BitSet supp : allSupports) {

					//System.out.println("\n" + supp);
					if (supp.cardinality() < numCoalitions) {
						mainLog.println("Support: " + supp);
						for (int k = 0; k < numCoalitions; k++) {
							mainLog.println("Player " + k);
							mainLog.println("Dominating: " + dominating[k]);
							mainLog.println("Dominated: " + dominated[k]);
							mainLog.println("Action indexes: " + actionIndexes[k]);
							mainLog.println("Strategies:" + strategies.get(k));
							mainLog.println("Supports:" + supports.get(k));
						}
						throw new PrismException("Problem with support");
					}

					eqsresult = smtSupportEnumeration.computeEquilibria(supp, mapActionIndex);
					//eqsresult = nlpSupportEnumeration.computeEquilibria(supp, mapActionIndex);


					if (eqsresult.getStatus() == CSGResultStatus.SAT) {
						sat.add(supp);

						//eqsresult = nlpSupportEnumeration.computeEquilibria(supp, mapActionIndex);

						equilibria.add(eqsresult.getPayoffVector());
						if (genStrat) {
							strats.add(extractStrategyFromEquilibrium(eqsresult, mmap));
						}
						//System.out.println(equilibria);

						/*
						if (eqsresult.getStatus() != eqsresultnlp.getStatus()) {
							System.out.println("SMT: " + eqsresult.getStatus());
							System.out.println("NLP: " + eqsresultnlp.getStatus());
							throw new PrismException("Solvers differ.");
						}
						*/
						//System.out.println("sat");
					}
					else if (eqsresult.getStatus() == CSGResultStatus.UNKNOWN) {
						unknown.add(supp);

						//eqsresult = nlpSupportEnumeration.computeEquilibria(supp, mapActionIndex);
						/*
						if (eqsresult.getStatus() == CSGResultStatus.SAT) {
							equilibria.add(eqsresult.getPayoffVector());
						}
						*/
						//System.out.println("unknown");
					}
					else if (eqsresult.getStatus() == CSGResultStatus.UNSAT) {
						unsat.add(supp);
						//System.out.println("unsat");
						/*
						if (eqsresult.getStatus() != eqsresultnlp.getStatus()) {
							System.out.println("SMT: " + eqsresult.getStatus());
							System.out.println("NLP: " + eqsresultnlp.getStatus());
							throw new PrismException("Solvers differ.");
						}
						*/
					}
				}

				if (sat.size() != 0) {
					/*
					for (EquilibriumResult eq : stepParallelEquilibriaGurobi(sat)) {
						equilibria.add(eq.getPayoffVector());
						if (genStrat) {
							strats.add(extractStrategyFromEquilibrium(eq, mmap));
						}
						//if (s == csg.getFirstInitialState()) {
						//	System.out.println("-- strat " + eq.getStrategy());
						//}
					}
					*/
				}
				//System.out.println("Sat supports: " + sat.size() + " " + (System.currentTimeMillis() - par)/1000.00 + " s");

				//par = System.currentTimeMillis();
				//System.out.println("Unknown supports: " + unknown.size());
				if (unknown.size() != 0) {
					/*
					for (EquilibriumResult eq : stepParallelEquilibriaGurobi(unknown)) {
						equilibria.add(eq.getPayoffVector());
						if (genStrat) {
							strats.add(extractStrategyFromEquilibrium(eq, mmap));
						}
						//if (s == csg.getFirstInitialState()) {
						//	System.out.println("-- strat " + eq.getStrategy());
						//}
					}
					*/
				}

				//System.out.println("Unknown supports: " + unknown.size() + " " + (System.currentTimeMillis() - par)/1000.00 + " s");
			}
			result = new double[equilibria.size()][numCoalitions];
			for (n = 0; n < equilibria.size(); n++) {
				for (p = 0; p < numCoalitions; p++) {
					result[n][p] = equilibria.get(n).get(p);
				}
			}
		}
		return result;
	}

	/**
	 * Returns the equilibrium (array of values) and updates strategies for the two-player case.
	 *
	 * @param csg
	 * @param rewards
	 * @param mmap
	 * @param strats
	 * @param val
	 * @param s
	 * @param eqType
	 * @param crit
	 * @param rew
	 * @param min
	 * @return
	 * @throws PrismException
	 */
	public double[] stepEquilibriaTwoPlayer(CSG<Double> csg, List<CSGRewards<Double>> rewards, List<Map<Integer, BitSet>> mmap, List<List<Map<BitSet, Double>>> strats,
			 								double[][] val, int s, int eqType, int crit, boolean rew, boolean min) throws PrismException {
		double[][] equilibria;
		double[] equilibrium;

		switch (eqType) {
			case CORR : {
				if (rew) {
					equilibrium = stepCorrelatedEquilibria(csg, rewards, mmap, strats, val, s, min, crit);
				}
				else
					equilibrium = stepCorrelatedEquilibria(csg, null, mmap, strats, val, s, min, crit);
				break;
			}
			default : {
				if (rew) {
					equilibria = stepNashEquilibria(csg, rewards.get(0), rewards.get(1), mmap, strats, val, s, min);
				}
				else {
					equilibria = stepNashEquilibria(csg, null, null, mmap, strats, val, s, min);
				}
				switch (crit) {
					case FAIR : {
						equilibrium = fair(equilibria, strats, min);
						break;
					}
					default : {
						equilibrium = swne(equilibria, strats, min);
					}
				}
			}
		}
		return equilibrium;
	}

	/**
	 * Computes Nash equilibria for a bimatrix game.
	 *
	 * @param csg
	 * @param csgRewards1
	 * @param csgRewards2
	 * @param mmap
	 * @param strats
	 * @param val
	 * @param s
	 * @param min
	 * @return
	 * @throws PrismException
	 */
	public double[][] stepNashEquilibria(CSG<Double> csg, CSGRewards<Double> csgRewards1, CSGRewards<Double> csgRewards2, List<Map<Integer, BitSet>> mmap,
									 List<List<Map<BitSet, Double>>> strats, double[][] val, int s, boolean min) throws PrismException {
		Map<BitSet, Double> d1 = null;
		Map<BitSet, Double> d2 = null;
		ArrayList<Map<BitSet, Double>> eqstrat;
		ArrayList<ArrayList<Integer>> nmap;
		ArrayList<ArrayList<ArrayList<Double>>> bmgame;
		double[][] val1s, val2s, result;
		double val1, val2, ent1, ent2;
		int[] mIndxs;
		int nrows, ncols, mrow, mcol;
		boolean equalA, equalB;

		mmap = new ArrayList<Map<Integer, BitSet>>();
		nmap = new ArrayList<ArrayList<Integer>>();
		for (int p = 0; p < 2; p++) {
			mmap.add(p, new HashMap<Integer, BitSet>());
			nmap.add(p, new ArrayList<Integer>());
		}
		bmgame = buildBimatrixGame(csg, csgRewards1, csgRewards2, mmap, nmap, val, s, min);
		nrows = bmgame.get(0).size();
		ncols = bmgame.get(0).get(0).size();
		val1s = new double[nrows][ncols];
		val2s = new double[nrows][ncols];

		/*
		// --- Uncomment to print matrices ---
		//if (s == csg.getFirstInitialState()) {
			System.out.println("\n-- matrices for state " + s + " " + csg.getStatesList().get(s));
			for (int p = 0; p < 2; p++) {
				System.out.println("-- player " + p);
				for (int r = 0; r < nrows; r++) {
					System.out.println("-- row " + r + " " + bmgame.get(p).get(r));
				}
			}
			System.out.println(actions);
			System.out.println(strategies);
			System.out.println(mmap);
			for (Map<Integer, BitSet> lmap : mmap) {
				for (int i : lmap.keySet()) {
					System.out.println("-- " + i);
					System.out.println(lmap.get(i));
					for (int j = lmap.get(i).nextSetBit(0); j >= 0; j = lmap.get(i).nextSetBit(j+1)) {
						System.out.print(csg.getActions().get(j-1) + " ");
					}
					System.out.println();
				}
			}
			System.out.println();
		//}
		*/

		if (nrows > 1 && ncols > 1) { // both players have choices
			equalA = true;
			equalB = true;
			ent1 = bmgame.get(0).get(0).get(0);
			ent2 = bmgame.get(1).get(0).get(0);
			for (int r = 0; r < nrows; r++) {
				for (int c = 0; c < ncols; c++) {
					val1 = bmgame.get(0).get(r).get(c);
					val2 = bmgame.get(1).get(r).get(c);
					equalA = equalA && Double.compare(ent1, val1) == 0;
					equalB = equalB && Double.compare(ent2, val2) == 0;
					val1s[r][c] = val1;
					val2s[r][c] = val2;
				}
			}
			if (!(equalA && equalB)) { // at least one has different entries
				if(equalA || equalB) { // if all entries of one of them are the same
					result = new double[1][2];
					if (equalA) {
						mIndxs = findMaxIndexes(val2s);
						mrow = mIndxs[0];
						mcol = mIndxs[1];
					}
					else {
						mIndxs = findMaxIndexes(val1s);
						mrow = mIndxs[0];
						mcol = mIndxs[1];
					}
					result[0][0] = val1s[mrow][mcol];
					result[0][1] = val2s[mrow][mcol];
					if (genStrat) {
						eqstrat = new ArrayList<Map<BitSet, Double>>();
						eqstrat.add(0, new HashMap<BitSet, Double>());
						eqstrat.get(0).put(mmap.get(0).get(nmap.get(0).get(mrow)), 1.0);
						eqstrat.add(1, new HashMap<BitSet, Double>());
						eqstrat.get(1).put(mmap.get(1).get(nmap.get(1).get(mcol)), 1.0);
						strats.add(0, eqstrat);
					}
					addStateRewards(result, csgRewards1, csgRewards2, s, min);
				}
				else { // both players have choices and matrices are not trivial, call solver
					smtLabeleldPolytopes.update(nrows, ncols, val1s, val2s);
					smtLabeleldPolytopes.computeEquilibria();
					smtLabeleldPolytopes.compPayoffs();
					result = new double[smtLabeleldPolytopes.getNeq()][2];
					for (int e = 0; e < smtLabeleldPolytopes.getNeq(); e++) {
						result[e][0] = smtLabeleldPolytopes.getP1p()[e];
						result[e][1] = smtLabeleldPolytopes.getP2p()[e];
						if (genStrat) {
							eqstrat = new ArrayList<Map<BitSet, Double>>();
							for (int p = 0; p < 2; p++) {
								eqstrat.add(p, new HashMap<BitSet, Double>());
								//System.out.println("-- strat from solver " + nash.getStrat().get(e).get(p).getSupport());
								for (int t : smtLabeleldPolytopes.getStrat().get(e).get(p).getSupport()) {
									eqstrat.get(p).put(mmap.get(p).get(nmap.get(p).get(t)), smtLabeleldPolytopes.getStrat().get(e).get(p).get(t));
								}
							}
							strats.add(e, eqstrat);
						}
					}
					addStateRewards(result, csgRewards1, csgRewards2, s, min);
				}
			}
			else { // all entries in both are the same
				result = new double[1][2];
				result[0][0] = ent1;
				result[0][1] = ent2;
				if (genStrat) {
					eqstrat = new ArrayList<Map<BitSet, Double>>();
					eqstrat.add(0, new HashMap<BitSet, Double>());
					eqstrat.get(0).put(mmap.get(0).get(nmap.get(0).get(0)), 1.0);
					eqstrat.add(1, new HashMap<BitSet, Double>());
					eqstrat.get(1).put(mmap.get(1).get(nmap.get(0).get(0)), 1.0);
					strats.add(0, eqstrat);
				}
				addStateRewards(result, csgRewards1, csgRewards2, s, min);
			}
		}
		else { // just one of the players has choices
			result = new double[1][2];
			double vt1, vt2, sumv, sumt;
			if(genStrat) {
				d1 = new HashMap<BitSet, Double>();
				d2 = new HashMap<BitSet, Double>();
			}
			val1 = Double.NEGATIVE_INFINITY;
			val2 = Double.NEGATIVE_INFINITY;
			sumv = Double.NEGATIVE_INFINITY;
			if (nrows > 1 && ncols == 1) {
				for (int r = 0; r < nrows; r++) {
					vt1 = bmgame.get(0).get(r).get(0);
					vt2 = bmgame.get(1).get(r).get(0);
					sumt = vt1 + vt2;
					if (Double.compare(vt1, val1) > 0 || (Double.compare(vt1, val1) == 0 && Double.compare(sumt, sumv) > 0)) {
						if(genStrat) {
							d1.clear();
							d1.put(mmap.get(0).get(nmap.get(0).get(r)), 1.0);
						}
						val2 = vt2;
						val1 = vt1;
						sumv = val1 + val2;
					}
				}
				if(genStrat)
					d2.put(mmap.get(1).get(nmap.get(1).get(0)), 1.0);
			}
			else if (nrows == 1 && ncols > 1) {
				for (int c = 0; c < ncols; c++) {
					vt1 = bmgame.get(0).get(0).get(c);
					vt2 = bmgame.get(1).get(0).get(c);
					sumt = vt1 + vt2;
					if (Double.compare(vt2, val2) > 0 || (Double.compare(vt2, val2) == 0 && Double.compare(sumt, sumv) > 0)) {
						if(genStrat) {
							d2.clear();
							d2.put(mmap.get(1).get(nmap.get(1).get(c)), 1.0);
						}
						val2 = vt2;
						val1 = vt1;
						sumv = val1 + val2;
					}
				}
				if(genStrat)
					d1.put(mmap.get(0).get(nmap.get(0).get(0)), 1.0);
			}
			else if (nrows == 1 && ncols == 1) {
				val1 = bmgame.get(0).get(0).get(0);
				val2 = bmgame.get(1).get(0).get(0);
				if(genStrat) {
					d1.put(mmap.get(0).get(nmap.get(0).get(0)), 1.0);
					d2.put(mmap.get(1).get(nmap.get(1).get(0)), 1.0);
				}
			}
			else {
				throw new PrismException("Error with matrix rank");
			}
			if (genStrat) {
				eqstrat = new ArrayList<Map<BitSet, Double>>();
				eqstrat.add(0, d1);
				eqstrat.add(1, d2);
				strats.add(0, eqstrat);
			}
			result[0][0] = val1;
			result[0][1] = val2;
			addStateRewards(result, csgRewards1, csgRewards2, s, min);
		}
		return result;
	}
}
