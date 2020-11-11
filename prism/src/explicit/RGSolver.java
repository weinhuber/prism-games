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
	 * Create a new reachability game solver.
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
