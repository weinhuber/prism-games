package explicit;

import prism.PrismComponent;

/**
 * Solve parity games (PGs).
 */
public abstract class PGSolver extends TGSolver
{

	/**
	 * Parity game (PG)
	 */
	protected PG pg;

	/**
	 * Create a new parity game solver.
	 */
	public PGSolver(PrismComponent parent, PG pg)
	{
		super(parent, pg.tg);
		this.pg = pg;
	}

}
