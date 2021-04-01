package explicit;

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
	 * Create a new TGSolver for a turn-based game (TG).
	 */
	public TGSolver(PrismComponent parent)
	{
		this.parent = parent;
	}

}
