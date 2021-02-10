package explicit;

import java.util.BitSet;
import java.util.Collections;

import prism.PrismComponent;

/**
 * Solve parity games using the Zielonka recursive algorithm.
 */
public class ZielonkaRecursive extends PGSolver
{

	/**
	 * Create a new parity game solver.
	 */
	public ZielonkaRecursive(PrismComponent parent, PG pg)
	{
		super(parent, pg);
	}

	@Override
	public BitSet solve()
	{
		return zielonka(pg).w1;
	}

	private WinningRegions zielonka(PG pg)
	{
		WinningRegions W = new WinningRegions();
		if (pg.tg.getActiveStates().isEmpty()) {
			return W;
		}

		int d = Collections.max(pg.priorities);
		BitSet U = pg.priorityMap.get(d);
		int p = d % 2 == 0 ? 1 : 2;
		int j = p == 1 ? 2 : 1;
		BitSet A = pg.tg.attractor(p, U, parent);
		WinningRegions W1 = zielonka(pg.difference(A));

		if (W1.get(j).isEmpty()) {
			W1.get(p).or(A);
			W.set(p, W1.get(p));
			W.set(j, new BitSet());
		} else {
			BitSet B = pg.tg.attractor(j, W1.get(j), parent);
			W1 = zielonka(pg.difference(B));
			W.set(p, W1.get(p));
			W1.get(j).or(B);
			W.set(j, W1.get(j));
		}

		return W;
	}

}
