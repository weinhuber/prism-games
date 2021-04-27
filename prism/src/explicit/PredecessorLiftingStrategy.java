package explicit;

import java.util.*;

import prism.PrismComponent;

/**
 * Predecessor lifting strategy for the small progress measures algorithm.
 */
public class PredecessorLiftingStrategy extends LiftingStrategy {

	/**
	 * Small progress measure
	 */
	protected final int[][] rho;
	/**
	 * Queued states
	 */
	protected final BitSet queued;
	/**
	 * Queue of states to attempt to lift
	 */
	protected Deque<Integer> queue = new LinkedList<>();

	/**
	 * Create a PredecessorLiftingStrategy.
	 */
	public PredecessorLiftingStrategy(PrismComponent parent, PG pg, int[][] rho) {
		super(parent, pg);
		this.rho = rho;
		this.queued = new BitSet(pg.getTG().getNumStates());
		for (int v = 0; v < pg.getTG().getNumStates(); v++) {
			if (rho[v] != null) {
				queued.set(v);
				queue.add(v);
			}
		}
	}

	@Override
	public void lifted(int s) {
		PredecessorRelation pre = pg.getTG().getPredecessorRelation(parent, true);
		
		for (int w : pre.getPre(s)) {
			if (!queued.get(w) && rho[w] != null) {
				queued.set(w);
				queue.push(w);
			}
		}
	}

	@Override
	public int next() {
		if (queue.isEmpty()) {
			return LiftingStrategy.NO_STATE;
		} else {
			int v = queue.remove();
			queued.set(v, false);
			return v;
		}
	}
	
}
