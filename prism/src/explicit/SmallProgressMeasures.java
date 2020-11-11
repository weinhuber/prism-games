package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import prism.PrismComponent;

/**
 * Solve parity games using the small progress measures algorithm.
 */
public class SmallProgressMeasures extends PGSolver
{

	/**
	 * Maximum priority 
	 */
	private final int d;
	/**
	 * Even upper bound for the maximum priority d
	 */
	private final int p;
	/**
	 * Number of states for a priority
	 */
	private int[] max;
	/**
	 * Small progress measure
	 */
	private final int[][] rho;
	/**
	 * Vector comparator for lexicographic order
	 */
	private final Comparator<int[]> vectorComparator = (v, w) -> {
		if (v == w) {
			return 0;
		}
		if (lessThan(v, w)) {
			return -1;
		}
		return 1;
	};

	/**
	 * Create a new parity game solver.
	 */
	public SmallProgressMeasures(PrismComponent parent, TG tg, List<Integer> priorities)
	{
		super(parent, tg, priorities);
		this.d = Collections.max(priorities);
		this.p = d + (d % 2);
		this.rho = new int[tg.getNumStates()][d + 1];
		this.max = new int[d + 1];
		for (Map.Entry<Integer, BitSet> entry : priorityMap.entrySet()) {
			max[entry.getKey()] = entry.getValue().cardinality();
		}
	}

	@Override
	public BitSet solve()
	{
		WinningRegions W = new WinningRegions();
		W.w2 = jurdzinksi();
		W.w1 = (BitSet) W.w2.clone();
		W.w1.flip(0, tg.getNumStates());
		return W.w1;
	}

	private BitSet jurdzinksi()
	{
		//		LiftingStrategy liftingStrategy = new LinearLiftingStrategy(parent, tg);
		LiftingStrategy liftingStrategy = new PredecessorLiftingStrategy(parent, tg, rho);
		int v = liftingStrategy.next();

		while (v != LiftingStrategy.NO_STATE) {
			int[] lift = lift(v);
			if (vectorComparator.compare(rho[v], lift) < 0) {
				liftingStrategy.lifted(v);
				rho[v] = lift;
			}

			v = liftingStrategy.next();
		}

		// SPM solves for player 2
		BitSet w2 = new BitSet();
		for (int i = 0; i < rho.length; i++) {
			if (rho[i] == null) {
				w2.set(i);
			}
		}
		return w2;
	}

	private int[] lift(int v)
	{
		List<int[]> progs = new ArrayList<>();
		int[] prog;

		tg.getSuccessors(v).stream().forEach(w -> {
			progs.add(prog(v, w));
		});
		if (tg.getPlayer(v) == 1) {
			prog = Collections.min(progs, vectorComparator);
		} else {
			prog = Collections.max(progs, vectorComparator);
		}

		return prog;
	}

	private int[] prog(int v, int w)
	{
		int[] rhoW = rho[w];
		if (rhoW == null) {
			return null;
		}
		rhoW = rhoW.clone();
		int vPriority = priorities.get(v);

		if ((p - vPriority) % 2 == 0) { // player 1
			for (int i = (p - vPriority) + 1; i < rhoW.length; i += 2) {
				rhoW[i] = 0;
			}
			return rhoW;
		} else { // player 2
			for (int i = (p - vPriority); i >= 0; i -= 2) {
				if (rhoW[i] < max[i]) {
					rhoW[i] = rhoW[i] + 1;
					for (int j = i + 2; j < rhoW.length; j += 2) {
						rhoW[j] = 0;
					}
					return rhoW;
				}
			}
			return null;
		}
	}

	// lexicographic order
	private boolean lessThan(int[] v, int[] w)
	{
		if (v == null) {
			return false;
		}
		if (w == null) {
			return true;
		}
		for (int i = 0; i < v.length; i++) {
			if (v[i] == w[i]) {
				continue;
			}
			return v[i] < w[i];
		}
		return false;
	}

}
