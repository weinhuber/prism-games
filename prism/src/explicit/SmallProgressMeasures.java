package explicit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import prism.PrismComponent;

/**
 * Solve parity games using the small progress measures algorithm.
 * We treat null as our T. 
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
	private int p;
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
		if (lessThan(v, w)) {
			return -1;
		}
		if (lessThan(w, v)) {
			return 1;
		}
		return 0;
	};

	/**
	 * Create a new parity game solver.
	 */
	public SmallProgressMeasures(PrismComponent parent, PG pg)
	{
		super(parent, pg);
		this.d = Collections.max(pg.priorities);
		this.p = d + (d % 2);
		this.rho = new int[tg.getNumStates()][d + 1];
		this.max = new int[d + 1];
		for (Map.Entry<Integer, BitSet> entry : pg.priorityMap.entrySet()) {
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
		// LiftingStrategy liftingStrategy = new LinearLiftingStrategy(parent, tg);
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
		prog = Collections.max(Arrays.asList(rho[v], prog), vectorComparator);

		return prog;
	}

	private int[] prog(int v, int w)
	{
		if (rho[w] == null) {
			return null;
		}
		int[] rhoV = rho[w].clone();
		int vPriority = pg.priorities.get(v);

		for (int i = (p - vPriority) + 1; i < rhoV.length; i++) {
			rhoV[i] = 0;
		}
		if ((p - vPriority) % 2 != 0) {
			rhoV = increment(rhoV, p - vPriority);
		}

		return rhoV;
	}

	private int[] increment(int[] prog, int i)
	{
		if (i == 1 && prog[1] == max[1]) {
			return null;
		}

		if (prog[i] + 1 > max[i]) {
			return increment(prog, i - 2);
		} else {
			prog[i] = prog[i] + 1;
		}

		return prog;
	}

	// lexicographic order
	private static boolean lessThan(int[] v, int[] w)
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
