package explicit;

import java.util.BitSet;

import prism.PrismComponent;

/**
 * Solve reachability games (RGs).
 */
public class RGSolver extends TGSolver
{

	/**
	 * Create a new reachability game solver.
	 */
	public RGSolver(PrismComponent parent)
	{
		super(parent);
	}

	/**
	 * Compute the solution.
	 * 
	 * @param tg Turn-based game (TG)
	 * @param target Target set
	 */
	public TGSolution solve(RG rg)
	{
		TGSolution soln = new TGSolution();

		RegionStrategy rs = rg.getTG().attractor(1, rg.target, parent);
		soln.set(1, rs);
		soln.get(2).setRegion((BitSet) rs.getRegion().clone());
		soln.get(2).getRegion().flip(0, rg.getTG().getNumStates());

		// Complete the winning strategies
		rg.getTG().getActiveStates().stream().forEach(s -> {
			// Arbitrary choice for Player 1
			if (rg.getTG().getPlayer(s) == 1 && !rs.getStrategy().containsKey(s)) {
				rs.getStrategy().put(s, rg.getTG().getSuccessors(s).next());
			}

			// For Player 2, keep the token out of the attractor
			// Arbitrary choice if unable to.
			if (rg.getTG().getPlayer(s) == 2) {
				SuccessorsIterator successors = rg.getTG().getSuccessors(s);
				while (successors.hasNext()) {
					int succ = successors.next();
					if (!rs.getRegion().get(succ)) {
						soln.get(2).getStrategy().put(s, succ);
						break;
					}
				}

				if (!rs.getStrategy().containsKey(s)) {
					soln.get(2).getStrategy().put(s, rg.getTG().getSuccessors(s).next());
				}
			}
		});

		return soln;
	}

}
