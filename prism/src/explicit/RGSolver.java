package explicit;

import java.util.BitSet;

import prism.PrismComponent;

/**
 * Solve reachability games.
 */
public class RGSolver extends TGSolver
{

	/**
	 * Create a new reachability game solver.
	 */
	public RGSolver(PrismComponent parent)
	{
		super(parent);
	}

	/**
	 * Compute the solution as player 1 (1-indexed).
	 * @param tg Turn-based game (TG)
	 * @param target Target set
	 */
	public BitSet solve(TG tg, BitSet target)
	{
		return tg.attractor(1, target, parent);
	}

}
