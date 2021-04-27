package explicit;

import prism.PrismComponent;

/**
 * Solve parity games (PGs).
 */
public abstract class PGSolver extends TGSolver
{

	/**
	 * Create a new parity game solver.
	 */
	public PGSolver(PrismComponent parent)
	{
		super(parent);
	}

	/**
	 * Compute the solution.
	 * @param pg Parity game (PG)
	 */
	public abstract TGSolution solve(PG pg);

}
