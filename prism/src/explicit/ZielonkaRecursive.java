package explicit;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import prism.PrismComponent;

/**
 * Solve parity games using the Zielonka recursive algorithm.
 */
public class ZielonkaRecursive extends PGSolver
{

	/**
	 * Create a new parity game solver.
	 */
	public ZielonkaRecursive(PrismComponent parent, TG tg, List<Integer> priorities)
	{
		super(parent, tg, priorities);
	}

	/**
	 * Copy constructor.
	 */
	public ZielonkaRecursive(ZielonkaRecursive zielonkaRecursive)
	{
		super(zielonkaRecursive);
	}

	@Override
	public BitSet solve()
	{
		return zielonka().w1;
	}

	private WinningRegions zielonka()
	{
		WinningRegions W = new WinningRegions();
		if (tg.getNumTransitions() == 0) {
			return W;
		}

		int d = Collections.max(priorities);
		BitSet U = priorityMap.get(d);
		int p = d % 2 == 0 ? 1 : 2;
		int j = p == 1 ? 2 : 1;
		BitSet A = tg.attractor(p, U, parent);
		WinningRegions W1 = difference(A).zielonka();

		if (W1.get(j).isEmpty()) {
			W1.get(p).or(A);
			W.set(p, W1.get(p));
			W.set(j, new BitSet());
		} else {
			BitSet B = tg.attractor(j, W1.get(j), parent);
			W1 = difference(B).zielonka();
			W.set(p, W1.get(p));
			W1.get(j).or(B);
			W.set(j, W1.get(j));
		}

		return W;
	}

	private ZielonkaRecursive difference(BitSet A)
	{
		ZielonkaRecursive copy = new ZielonkaRecursive(this);

		A.stream().forEach(s -> {
			((TGSimple) copy.tg).clearState(s);
			copy.priorities.set(s, -1);
			copy.priorityMap.computeIfPresent(copy.priorities.get(s), (k, v) -> {
				v.clear(s);
				return v.isEmpty() ? null : v;
			});
		});

		return copy;
	}

}
