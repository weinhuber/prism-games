package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import prism.PrismComponent;

/**
 * Solve parity games.
 */
public abstract class PGSolver extends TGSolver
{

	/**
	 * List of priorities
	 */
	protected List<Integer> priorities;
	/**
	 * Map of priorities to states 
	 */
	protected Map<Integer, BitSet> priorityMap = new HashMap<>();

	/**
	 * Create a new parity game solver.
	 */
	public PGSolver(PrismComponent parent, TG tg, List<Integer> priorities)
	{
		super(parent, tg);
		this.priorities = priorities;
		for (int s = 0; s < priorities.size(); s++) {
			priorityMap.computeIfAbsent(priorities.get(s), priority -> new BitSet()).set(s);
		}
	}

	/**
	 * Copy constructor.
	 */
	public PGSolver(PGSolver pgSolver)
	{
		super(pgSolver);
		this.priorities = new ArrayList<>(pgSolver.priorities);
		this.priorityMap = new HashMap<>(pgSolver.priorityMap);
	}

}
