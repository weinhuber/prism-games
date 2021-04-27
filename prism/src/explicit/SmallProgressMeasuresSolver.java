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
public class SmallProgressMeasuresSolver extends PGSolver
{

	/**
	 * Measure comparator for lexicographic order
	 */
	private static final Comparator<int[]> measureComparator = SmallProgressMeasuresSolver::compare;

	/**
	 * Create a new parity game solver.
	 */
	public SmallProgressMeasuresSolver(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	public TGSolution solve(PG pg)
	{
		// Convert PG to the min definition as SPM assumes this
		PG parityGame = new PG(pg);
		parityGame.convertMaxToMin();

		TGSolution soln = new TGSolution();
		soln.set(2, jurdzinksi(parityGame));
		soln.get(1).setRegion((BitSet) soln.get(2).getRegion().clone());
		soln.get(1).getRegion().flip(0, pg.getTG().getNumStates());
		return soln;
	}

	private WinningPair jurdzinksi(PG pg)
	{
		// Maximum priority
		int d = pg.maxPriority();
		// Number of states for a priority
		int[] max = new int[d + 1];
		for (Map.Entry<Integer, BitSet> entry : pg.getPriorityMap().entrySet()) {
			if (entry.getKey() % 2 != 0) {
				max[entry.getKey()] = entry.getValue().cardinality();
			}
		}
		// Progress measure
		int[][] rho = new int[pg.getTG().getNumStates()][d + 1];

		// Lifting strategies can be swapped here
//		LiftingStrategy liftingStrategy = new LinearLiftingStrategy(parent, pg);
		LiftingStrategy liftingStrategy = new PredecessorLiftingStrategy(parent, pg, rho);
		int v = liftingStrategy.next();

		while (v != LiftingStrategy.NO_STATE) {
			// (For benchmarking)
			if (Thread.currentThread().isInterrupted()) {
				return null;
			}
			
			int[] lift = lift(pg, rho, max, d, v);
			if (measureComparator.compare(rho[v], lift) < 0) {
				rho[v] = lift;
				liftingStrategy.lifted(v);
			}
			v = liftingStrategy.next();
		}

		// SPM solves for Player 2
		WinningPair pair = new WinningPair();
		for (int s = 0; s < rho.length; s++) {
			if (rho[s] == null) {
				pair.getRegion().set(s);
			}
		}
		return pair;
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
