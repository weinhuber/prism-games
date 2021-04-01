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
 * We treat null as our T element. 
 */
public class SmallProgressMeasures extends PGSolver
{

	/**
	 * Vector comparator for lexicographic order
	 */
	private static final Comparator<int[]> vectorComparator = (v, w) -> {
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
	public SmallProgressMeasures(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	public BitSet solve(PG pg)
	{
		// Convert PG to the min definition
		PG parityGame = new PG(pg);
		parityGame.convertPrioritiesToMin();

		WinningRegions W = new WinningRegions();
		W.w2 = jurdzinksi(parityGame);
		W.w1 = (BitSet) W.w2.clone();
		W.w1.flip(0, parityGame.getTG().getNumStates());
		return W.w1;
	}

	private BitSet jurdzinksi(PG pg)
	{
		// Maximum priority
		int d = Collections.max(pg.getPriorities());
		// Number of states for a priority
		int[] max = new int[d + 1];
		for (Map.Entry<Integer, BitSet> entry : pg.getPriorityMap().entrySet()) {
			max[entry.getKey()] = entry.getValue().cardinality();
		}
		// Progress measure
		int[][] rho = new int[pg.getTG().getNumStates()][d + 1];

		// LiftingStrategy liftingStrategy = new LinearLiftingStrategy(parent, pg.getTG());
		LiftingStrategy liftingStrategy = new PredecessorLiftingStrategy(parent, pg.getTG(), rho);
		int v = liftingStrategy.next();

		while (v != LiftingStrategy.NO_STATE) {
			int[] lift = lift(pg, rho, max, v);
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

	private static int[] lift(PG pg, int[][] rho, int[] max, int v)
	{
		List<int[]> progs = new ArrayList<>();
		int[] prog;

		pg.getTG().getSuccessors(v).stream().forEach(w -> {
			progs.add(prog(pg, rho, max, v, w));
		});
		if (pg.getTG().getPlayer(v) == 1) {
			prog = Collections.min(progs, vectorComparator);
		} else {
			prog = Collections.max(progs, vectorComparator);
		}

		return Collections.max(Arrays.asList(rho[v], prog), vectorComparator);
	}

	private static int[] prog(PG pg, int[][] rho, int[] max, int v, int w)
	{
		if (rho[w] == null) {
			return null;
		}
		int[] rhoV = rho[w].clone();
		int vPriority = pg.getPriorities().get(v);

		for (int i = vPriority + 1; i < rhoV.length; i++) {
			rhoV[i] = 0;
		}
		if (vPriority % 2 != 0) {
			rhoV = increment(rhoV, max, vPriority);
		}

		return rhoV;
	}

	private static int[] increment(int[] prog, int[] max, int i)
	{
		if (i == 1 && prog[1] == max[1]) {
			return null;
		}

		if (prog[i] + 1 > max[i]) {
			return increment(prog, max, i - 1);
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
