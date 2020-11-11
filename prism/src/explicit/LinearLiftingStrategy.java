package explicit;

import prism.PrismComponent;

/**
 * Linear lifting strategy for the small progress measures algorithm.
 */
public class LinearLiftingStrategy extends LiftingStrategy
{

	/**
	 * How many states have failed to lift
	 */
	protected int numFailed = 0;
	/**
	 * Next state to lift
	 */
	protected int nextState = 0;

	/**
	 * Create a LinearLiftingStrategy.
	 */
	public LinearLiftingStrategy(PrismComponent parent, TG tg)
	{
		super(parent, tg);
	}

	@Override
	public void lifted(int s)
	{
		numFailed = 0;
	}

	@Override
	public int next()
	{
		if (numFailed == tg.getNumStates()) {
			return LiftingStrategy.NO_STATE;
		} else {
			numFailed = numFailed + 1;
			nextState = (nextState + 1) % tg.getNumStates();
			return nextState;
		}
	}

}
