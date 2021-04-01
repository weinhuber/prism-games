package explicit;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import prism.Pair;
import prism.PrismComponent;

/**
 * Solve parity games using the priority promotion algorithm.
 */
public class PriorityPromotion extends PGSolver
{

	/**
	 * Create a new parity game solver.
	 */
	public PriorityPromotion(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	public BitSet solve(PG pg)
	{
		PG parityGame = pg;
		WinningRegions W = new WinningRegions();

		while (!parityGame.getTG().getActiveStates().isEmpty()) {
			Pair<Integer, BitSet> dominion = searchDominion(parityGame);
			W.get(dominion.getKey()).or(dominion.getValue());
			parityGame = parityGame.difference(dominion.getValue());
		}

		return W.w1;
	}

	private Pair<Integer, BitSet> searchDominion(PG pg)
	{
		Map<Integer, Integer> r = new HashMap<>();
		pg.getTG().getActiveStates().stream().forEach(s -> {
			r.put(s, -1); // -1 is our bottom
		});
		int p = Collections.max(pg.getPriorities());

		while (true) {
			int alpha = p % 2 == 0 ? 1 : 2;
            int alphaBar = alpha == 1 ? 2 : 1;

			int finalP = p;

			BitSet Subgame = (BitSet) pg.getTG().getActiveStates().clone();
			r.entrySet().stream()
			.filter(entry -> entry.getValue() > finalP)
			.map(Map.Entry::getKey)
			.forEach(s -> Subgame.set(s, false));

			BitSet A = (BitSet) pg.getPriorityMap().getOrDefault(p, new BitSet()).clone();
			A.and(Subgame);
			r.entrySet().stream()
			.filter(entry -> entry.getValue() == finalP)
			.map(Map.Entry::getKey)
			.forEach(s -> A.set(s));

			BitSet Z = pg.getTG().subgame(Subgame).attractor(alpha, A, parent);
			BitSet finalZ = Z;

			BitSet Open = Z.stream()
					.filter(s -> pg.getTG().getPlayer(s) == alpha && !pg.getTG().someSuccessorsInSet(s, finalZ))
					.collect(BitSet::new, BitSet::set, BitSet::or);

			BitSet Esc = Z.stream()
					.filter(s -> pg.getTG().getPlayer(s) == alphaBar)
					.flatMap(s -> pg.getTG().getSuccessors(s).stream())
					.filter(s -> !finalZ.get(s))
					.collect(BitSet::new, BitSet::set, BitSet::or);

			BitSet EscAndSubgame = (BitSet) Esc.clone();
			EscAndSubgame.and(Subgame);

			if (!Open.isEmpty() || !EscAndSubgame.isEmpty()) {
				int finalP1 = p;
				Z.stream().forEach(s -> r.put(s, finalP1));

				p = Subgame.stream()
						.filter(s -> !finalZ.get(s))
						.map(s -> pg.getPriorities().get(s))
						.max().getAsInt();
			} else if (!Esc.isEmpty()) {
				p = r.entrySet().stream()
						.filter(entry -> Esc.get(entry.getKey()))
						.map(Map.Entry::getValue)
						.min(Integer::compare).get();
				int finalP1 = p;

				Z.stream().forEach(s -> r.put(s, finalP1));
				r.entrySet().stream()
				.filter(entry -> entry.getValue() < finalP1)
				.map(Map.Entry::getKey)
				.forEach(s -> r.put(s, -1));
			} else {
				Z = pg.getTG().attractor(alpha, Z, parent);
				return new Pair<>(alpha, Z);
			}
		}
	}

}
