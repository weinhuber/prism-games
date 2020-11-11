package explicit;

import java.util.BitSet;

import prism.PrismComponent;

/**
 * Solve reachability games.
 */
public class RGSolver extends TGSolver
{

	/**
	 * Target set
	 */
	protected final BitSet target;

	/**
	 * Create a new RGSolver for a turn-based game (TG) with a target set.
	 */
	public RGSolver(PrismComponent parent, TG tg, BitSet target)
	{
		super(parent, tg);
		this.target = target;
	}

	@Override
	public BitSet solve()
	{
		return tg.attractor(1, target, parent);
	}

}
