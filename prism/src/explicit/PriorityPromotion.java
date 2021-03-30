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
	public PriorityPromotion(PrismComponent parent, PG pg)
	{
		super(parent, pg);
	}

	@Override
	public BitSet solve()
	{
		PG paritygame = pg;
		WinningRegions W = new WinningRegions();

		while (!paritygame.tg.getActiveStates().isEmpty()) {
			Pair<Integer, BitSet> dominion = searchDominion(paritygame);
			W.get(dominion.getKey()).or(dominion.getValue());
			paritygame = paritygame.difference(dominion.getValue());
		}

		return W.w1;
	}

	private Pair<Integer, BitSet> searchDominion(PG pg)
	{
		Map<Integer, Integer> r = new HashMap<>();
		pg.tg.getActiveStates().stream().forEach(s -> {
			r.put(s, -1); // -1 is our bottom
		});
		int p = Collections.max(pg.priorities);

		while (true) {
			int alpha = p % 2 == 0 ? 1 : 2;
            int alphaBar = alpha == 1 ? 2 : 1;

			int finalP = p;

			BitSet Subgame = (BitSet) pg.tg.getActiveStates().clone();
			r.entrySet().stream()
			.filter(entry -> entry.getValue() > finalP)
			.map(Map.Entry::getKey)
			.forEach(s -> Subgame.set(s, false));

			BitSet A = pg.priorityMap.getOrDefault(p, new BitSet());
			A.and(Subgame);
			r.entrySet().stream()
			.filter(entry -> entry.getValue() == finalP)
			.map(Map.Entry::getKey)
			.forEach(s -> A.set(s, true));

			BitSet Z = pg.tg.subgame(Subgame).attractor(alpha, A, parent);
			BitSet finalZ = Z;

			BitSet Open = Z.stream()
					.filter(s -> pg.tg.getPlayer(s) == alpha && !pg.tg.someSuccessorsInSet(s, finalZ))
					.collect(BitSet::new, BitSet::set,BitSet::or);

			BitSet Esc = Z.stream()
					.filter(s -> pg.tg.getPlayer(s) == alphaBar)
					.flatMap(s -> pg.tg.getSuccessors(s).stream())
					.filter(s -> !finalZ.get(s))
					.collect(BitSet::new, BitSet::set, BitSet::or);

			BitSet EscAndSubgame = (BitSet) Esc.clone();
			EscAndSubgame.and(Subgame);

			if (!Open.isEmpty() || !EscAndSubgame.isEmpty()) {
				int finalP1 = p;
				Z.stream().forEach(s -> r.put(s, finalP1));

				p = Subgame.stream()
						.filter(s -> !finalZ.get(s))
						.map(s -> pg.priorities.get(s))
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
				Z = pg.tg.attractor(alpha, Z, parent);
				return new Pair<>(alpha, Z);
			}
		}
	}

}
