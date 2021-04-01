package explicit;

import java.util.BitSet;

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
	 * Compute the solution as player 1 (1-indexed).
	 * @param pg Parity game (PG)
	 */
	public abstract BitSet solve(PG pg);

}
