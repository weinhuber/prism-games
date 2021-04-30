package explicit;

import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

import prism.PrismComponent;

/**
 * Solve parity games using the Zielonka recursive algorithm.
 */
public class ZielonkaRecursiveSolver extends PGSolver
{

	/**
	 * Create a new parity game solver.
	 */
	public ZielonkaRecursiveSolver(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	public TGSolution solve(PG pg)
	{
		TGSolution soln = zielonka(pg);
		return soln;
	}

	private TGSolution zielonka(PG pg)
	{
		// (For benchmarking)
		if (Thread.currentThread().isInterrupted()) {
			return null;
		}

		TGSolution W = new TGSolution();
		if (pg.getTG().getActiveStates().isEmpty()) {
			return W;
		}

		int d = pg.maxPriority();
		BitSet U = pg.getPriorityMap().get(d);
		int i = d % 2 == 0 ? 1 : 2;
		int j = i == 1 ? 2 : 1;
		Map<Integer, Integer> tau = new TreeMap<>();
		// Arbitrary initial strategy for player p on U
		U.stream().forEach(s -> {
			if (pg.getTG().getPlayer(s) == i) {
				tau.put(s, pg.getTG().getSuccessors(s).next());
			}
		});
		WinningPair A = pg.getTG().attractor(i, U, parent);
		TGSolution W1 = zielonka(pg.difference(A.getRegion()));

		if (W1.get(j).getRegion().isEmpty()) {
			W1.get(i).getRegion().or(A.getRegion());
			W1.get(i).getStrategy().putAll(tau);
			W1.get(i).getStrategy().putAll(A.getStrategy());
			W.set(i, W1.get(i));
			W.set(j, new WinningPair());
		} else {
			WinningPair B = pg.getTG().attractor(j, W1.get(j).getRegion(), parent);
			TGSolution W2 = zielonka(pg.difference(B.getRegion()));
			W.set(i, W2.get(i));
			W2.get(j).getRegion().or(B.getRegion());
			W2.get(j).getStrategy().putAll(B.getStrategy());
			W2.get(j).getStrategy().putAll(W1.get(j).getStrategy());
			W.set(j, W2.get(j));
		}

		return W;
	}

}
