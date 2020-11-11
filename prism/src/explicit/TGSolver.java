package explicit;

import java.util.BitSet;

import prism.PrismComponent;

/**
 * Solve turn-based games (TGs).
 */
public abstract class TGSolver
{

	/**
	 * PrismComponent for obtaining the log
	 */
	protected final PrismComponent parent;
	/**
	 * Turn-based game (TG)
	 */
	protected final TG tg;

	/**
	 * Create a new TGSolver for a turn-based game (TG).
	 */
	public TGSolver(PrismComponent parent, TG tg)
	{
		this.parent = parent;
		this.tg = tg;
	}

	/**
	 * Copy constructor.
	 */
	public TGSolver(TGSolver tgSolver)
	{
		this.parent = tgSolver.parent;
		this.tg = new TGSimple((TGSimple) tgSolver.tg);
	}

	/**
	 * Compute the solution as player 1 (1-indexed).
	 */
	public abstract BitSet solve();

}
