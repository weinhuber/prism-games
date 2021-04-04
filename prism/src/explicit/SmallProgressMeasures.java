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
	 * Measure comparator for lexicographic order
	 */
	private static final Comparator<int[]> measureComparator = SmallProgressMeasures::compare;

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
		// Convert PG to the min definition as SPM assumes this
		PG parityGame = new PG(pg);
		parityGame.convertMaxToMin();

		WinningRegions W = new WinningRegions();
		W.w2 = jurdzinksi(parityGame);
		W.w1 = (BitSet) W.w2.clone();
		W.w1.flip(0, pg.getTG().getNumStates());
		return W.w1;
	}

	private BitSet jurdzinksi(PG pg)
	{
		// Maximum priority
		int d = Collections.max(pg.getPriorities());
		// Number of states for a priority
		int[] max = new int[d + 1];
		for (Map.Entry<Integer, BitSet> entry : pg.getPriorityMap().entrySet()) {
			if (entry.getKey() % 2 != 0) {
				max[entry.getKey()] = entry.getValue().cardinality();
			}
		}
		// Progress measure
		int[][] rho = new int[pg.getTG().getNumStates()][d + 1];

		// LiftingStrategy liftingStrategy = new LinearLiftingStrategy(parent, pg.getTG());
		LiftingStrategy liftingStrategy = new PredecessorLiftingStrategy(parent, pg.getTG(), rho);
		int v = liftingStrategy.next();

		while (v != LiftingStrategy.NO_STATE) {
			int[] lift = lift(pg, rho, max, d, v);
			if (measureComparator.compare(rho[v], lift) < 0) {
				rho[v] = lift;
				liftingStrategy.lifted(v);
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

	private static int[] lift(PG pg, int[][] rho, int[] max, int d, int v)
	{
		List<int[]> progs = new ArrayList<>();
		int[] prog;

		pg.getTG().getSuccessors(v).stream().forEach(w -> {
			progs.add(prog(pg, rho, max, d, v, w));
		});
		if (pg.getTG().getPlayer(v) == 1) {
			prog = Collections.min(progs, measureComparator);
		} else {
			prog = Collections.max(progs, measureComparator);
		}

		return Collections.max(Arrays.asList(rho[v], prog), measureComparator);
	}

	private static int[] prog(PG pg, int[][] rho, int[] max, int d, int v, int w)
	{
		if (rho[w] == null) {
			return null;
		}

		int vPriority = pg.getPriorities().get(v);
		int[] measure = new int[d + 1];

		for (int i = 1; i <= vPriority; i += 2) {
			measure[i] = rho[w][i];
		}
		if (vPriority % 2 != 0) {
			measure = increase(measure, max, vPriority);
		}

		return measure;
	}

	private static int[] increase(int[] measure, int[] max, int p)
	{
		if (measure != null) {
			for (int i = p; i >= 0; i--) {
				if (measure[i] < max[i]) {
					measure[i] = measure[i] + 1;
					return measure;
				} else {
					measure[i] = 0;
				}
			}
		}

		return null;
	}

	// Lexicographic order
	private static int compare(int[] v, int[] w)
	{
		if (v == null) {
			if (w == null) {
				return 0;
			} else {
				return 1;
			}
		} else if (w == null) {
			return -1;
		}

		for (int i = 0; i < v.length; i++) {
			if (v[i] == w[i]) {
				continue;
			}
			return Integer.compare(v[i], w[i]);
		}

		return 0;
	}

}
