package explicit;

import prism.PrismComponent;

/**
 * Lifting strategy for the small progress measures algorithm.
 */
public abstract class LiftingStrategy
{

	/**
	 * Indicator for there being no more states left to lift
	 */
	public static final int NO_STATE = -1;

	/**
	 * PrismComponent for obtaining the log
	 */
	protected final PrismComponent parent;
	/**
	 * Turn-based game (TG)
	 */
	protected final TG tg;

	/**
	 * Create a LiftingStrategy.
	 */
	public LiftingStrategy(PrismComponent parent, TG tg)
	{
		this.parent = parent;
		this.tg = tg;
	}

	/**
	 * Notify the lifting strategy a state has been successfully lifted.
	 * @param s Index of state (0-indexed)
	 */
	public abstract void lifted(int s);

	/**
	 * Compute the next state to attempt to lift.
	 */
	public abstract int next();

}
