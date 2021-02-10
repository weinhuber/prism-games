package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
			int alpha = p % 2;

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

//			BitSet Open = Z.stream().filter(s -> pg.tg.getPlayer(s) == alpha && !pg.tg.someSuccessorsInSet(s,  finalZ);
//			}).collect(Collectors.toSet());
//
//			Set<Integer> Esc = Z.stream()
//					.filter(node -> G.nodes.get(node).player == 1 - alpha)
//					.map(node -> G.nodes.get(node).successors)
//					.flatMap(Set::stream)
//					.filter(node -> !finalZ.contains(node))
//					.collect(Collectors.toSet());
//
//			Set<Integer> t = new HashSet<>(Esc);
//			t.retainAll(Subgame);
//			if (!Open.isEmpty() || !t.isEmpty()) {
//				Z.stream().forEach(s -> r.put(s, p));
//				p = Subgame.stream()
//								.filter(s -> !finalZ.get(s))
//								.map(s -> pg.priorities.get(s))
//								.max().getAsInt();
//			} else if (!Esc.isEmpty()) {
//				p = Collections.min(r.entrySet().stream()
//						.filter(entry -> Esc.contains(entry.getKey()))
//						.map(Map.Entry::getValue)
//						.collect(Collectors.toSet()));
//
//				for (int node : Z) {
//					r.put(node, p);
//				}
//				int finalP1 = p;
//				for (int node : r.entrySet().stream()
//						.filter(entry -> entry.getValue() < finalP1)
//						.map(Map.Entry::getKey)
//						.collect(Collectors.toSet())) {
//					r.put(node, -1);
//				}
//			} else {
//				Z = pg.tg.attractor(alpha, Z, parent);
//				return new Pair<>(alpha, Z);
//			}
		}
	}

}
