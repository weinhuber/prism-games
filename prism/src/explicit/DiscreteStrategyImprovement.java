package explicit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import prism.PrismComponent;

/**
 * Solve parity games using the discrete strategy improvement algorithm.
 */
public class DiscreteStrategyImprovement extends PGSolver
{

	private final Set<Integer> V0 = new HashSet<>();
	private final Set<Integer> V1 = new HashSet<>();
	private final Set<Integer> VPlus = new HashSet<>();
	private final Set<Integer> VMinus = new HashSet<>();

	/**
	 * Create a new parity game solver.
	 */
	public DiscreteStrategyImprovement(PrismComponent parent, TG tg, List<Integer> priorities)
	{
		super(parent, tg, priorities);
		for (int s = 0; s < tg.getNumStates(); s++) {
			if (tg.getPlayer(s) == 1) {
				V0.add(s);
			} else {
				V1.add(s);
			}
			if (priorities.get(s) % 2 == 0) {
				VPlus.add(s);
			} else {
				VMinus.add(s);
			}
		}
	}

	@Override
	public BitSet solve()
	{
		PlayProfile[] phi = null;
		int[] sigma = initialStrategy();
		int[] sigmaDash = null;

		while (!(Arrays.equals(sigma, sigmaDash))) {
			TGSimple Gsigma = tg.subgameByEdgeFilter(
					(u, v) -> (sigma[u] == v && V0.contains(u)) || (tg.isSuccessor(u, v) && V1.contains(u)));
			phi = valuation(Gsigma);
			
			// Copy sigma under sigma'
			sigmaDash = sigma.clone();

			// Optimize sigma locally according to phi
			for (int v : V0) {
				Map<Integer, PlayProfile> D = new HashMap<>();
				SuccessorsIterator successors = tg.getSuccessors(v);
				while (successors.hasNext()) {
					int vDash = successors.next();
					D.put(vDash, phi[vDash]);
				}
				
				PlayProfile max = Collections.max(D.values(), playProfileOrder);
				if (playProfileOrder.compare(phi[sigma[v]], max) < 0) {
					for (int newState : D.keySet()) {
						if (playProfileOrder.compare(phi[newState], max) == 0) {
							sigma[v] = newState;
							System.out.println(v);
							break;
						}
					}
				}
			}
		}

		BitSet w1 = new BitSet();
		for (int v = 0; v < tg.getNumStates(); v++) {
			if (VPlus.contains(phi[v].u)) {
				w1.set(v);
			}
		}
		return w1;
	}

	// Select an initial strategy for player 0.
	private int[] initialStrategy()
	{
		int[] sigma = new int[tg.getNumStates()];
		for (int v : V0) {
			sigma[v] = tg.getSuccessorsIterator(v).next();
		}
		return sigma;
	}

	private PlayProfile[] valuation(TGSimple H)
	{
		PlayProfile[] phi = new PlayProfile[tg.getNumStates()];

        for (int w : tg.getActiveStates().stream().boxed()
                .sorted(rewardOrder) // Ascending order with respect to reward order.
                .collect(Collectors.toList())) {

            if (phi[w] == null) {
                Set<Integer> L = reach(H.subgameByStateList(tg.getActiveStates().stream().boxed()
                        .filter(v -> relevanceOrder.compare(v, w) <= 0)
                        .collect(Collectors.toSet())), w);
                
                if (H.someSuccessorsMatch(w, L::contains)) {
					Set<Integer> R = reach(H, w);
					
					PlayProfile[] phiR = subvaluation(H.subgameByStateList(R), w);
					for (int r : R) {
						phi[r] = phiR[r];
					}
					
                    Set<Integer> X = new HashSet<>(R);
                    Set<Integer> Y = tg.getActiveStates().stream().boxed()
                            .filter(s -> !R.contains(s))
                            .collect(Collectors.toSet());
					for (int x : X) {
						for (int y : Y) {
							if (H.hasTransition(x, y)) {
								H.removeTransition(x, y);
							}
						}
					}
                }
            }
        }

        return phi;
    }
	
	private PlayProfile[] subvaluation(TGSimple K, int w)
	{
		PlayProfile[] phi = new PlayProfile[tg.getNumStates()];
		K.getActiveStates().stream().forEach(v -> {
			phi[v] = new PlayProfile();
			phi[v].u = w;
			phi[v].P = new HashSet<>();
		});

		for (int u : K.getActiveStates().stream().boxed()
				.filter(v -> relevanceOrder.compare(v, w) > 0)
				.sorted(relevanceOrder.reversed()) // Descending order with respect to <.
				.collect(Collectors.toList())) {

			if (VPlus.contains(u)) {
				Set<Integer> UBar = reach(K.subgameByStateFilter(s -> s != u), w);
				
				for (int v : K.getActiveStates().stream().boxed()
						.filter(node -> !UBar.contains(node))
						.collect(Collectors.toList())) {
					phi[v].P.add(u);
				}
				
				Set<Integer> X = new HashSet<>(UBar);
				X.add(u);
				Set<Integer> Y = tg.getActiveStates().stream().boxed()
						.filter(s -> !UBar.contains(s))
						.collect(Collectors.toSet());
				for (int x : X) {
					for (int y : Y) {
						if (K.hasTransition(x, y)) {
							K.removeTransition(x, y);
						}
					}
				}
			} else {
				Set<Integer> U = reach(K.subgameByStateFilter(s -> s != w), u);
				
				for (int v : U) {
					phi[v].P.add(u);
				}
				
				Set<Integer> X = new HashSet<>(U);
				X.remove(u);
				Set<Integer> Y = tg.getActiveStates().stream().boxed()
						.filter(s -> !U.contains(s))
						.collect(Collectors.toSet());
				for (int x : X) {
					for (int y : Y) {
						if (K.hasTransition(x, y)) {
							K.removeTransition(x, y);
						}
					}
				}
			}
		}

		if (VPlus.contains(w)) {
			int[] maximalDistances = maximalDistances(K, w);
			K.getActiveStates().stream().forEach(s -> {
				phi[s].e = maximalDistances[s];
			});
		} else {
			int[] minimalDistances = minimalDistances(K, w);
			K.getActiveStates().stream().forEach(s -> {
				phi[s].e = minimalDistances[s];
			});
		}

		return phi;
	}

	// Backward depth first search.
	private Set<Integer> reach(TG G, int u)
	{
		Deque<Integer> stack = new LinkedList<>();
		stack.add(u);
		Set<Integer> visited = new HashSet<>();

		PredecessorRelation pre = G.getPredecessorRelation(parent, false);
		while (!stack.isEmpty()) {
			int s = stack.pop();
			if (!visited.contains(s)) {
				visited.add(s);

				for (int pred : pre.getPre(s)) {
					if (!visited.contains(pred)) {
						stack.addFirst(pred);
					}
				}
			}
		}

		return visited;
	}

	// Backward breadth first search.
	private int[] minimalDistances(TG G, int u)
	{
		Deque<Integer> queue = new LinkedList<>();
		queue.add(u);
		int[] distances = new int[tg.getNumStates()];
		Arrays.fill(distances, -1);
		distances[u] = 0;

		PredecessorRelation pre = G.getPredecessorRelation(parent, false);
		while (!queue.isEmpty()) {
			int s = queue.pop();
			for (int pred : pre.getPre(s)) {
				if (distances[pred] == -1) {
					distances[pred] = distances[s] + 1;
					queue.addLast(pred);
				}
			}
		}

		return distances;
	}

	// Backward search.
	private int[] maximalDistances(TG G, int u)
	{
		List<Integer> topologicalSort = topologicalSort(G);
		int[] distances = new int[tg.getNumStates()];
		Arrays.fill(distances, -1);
		distances[u] = 0;

		PredecessorRelation pre = G.getPredecessorRelation(parent, false);
		for (int s : topologicalSort) {
			for (int pred : pre.getPre(s)) {
				if (distances[pred] == -1) {
					distances[pred] = Math.max(distances[pred], distances[s] + 1);
				}
			}
		}

		return distances;
	}

	// Topological sort needed for determining longest paths in DAGs.
	private List<Integer> topologicalSort(TG G)
	{
		List<Integer> order = new ArrayList<>();
		Queue<Integer> roots = new LinkedList<>();
		Map<Integer, Integer> indegree = new HashMap<>();

		PredecessorRelation pre = G.getPredecessorRelation(parent, false);
		for (int s = 0; s < G.getNumStates(); s++) {
			int preSize = ((List<Integer>) pre.getPre(s)).size();
			indegree.put(s, preSize);
			if (preSize == 0) {
				roots.add(s);
			}
		}

		while (!roots.isEmpty()) {
			int s = roots.poll();
			order.add(s);
			
			SuccessorsIterator successors = G.getSuccessors(s);
			while (successors.hasNext()) {
				int succ = successors.next();
				indegree.put(succ, indegree.get(succ) - 1);
				if (indegree.get(succ) == 0) {
					roots.offer(succ);
				}
			}
		}

		return order;
	}

	// Higher colours indicate higher relevance.
	private Comparator<Integer> relevanceOrder = Comparator.comparingInt(u -> priorities.get(u));

	// Indicates the value of a vertex as seen from player 0.
	private Comparator<Integer> rewardOrder = (u, v) -> {
		if (rewardOrder(u, v))
			return -1;
		if (rewardOrder(v, u))
			return 1;
		return 0;
	};

	private boolean rewardOrder(int u, int v)
	{
		return (priorities.get(u) < priorities.get(v) && VPlus.contains(v)) || (priorities.get(v) < priorities.get(u) && !VPlus.contains(u));
	}

	// The reward order extended to an order on sets of vertices.
	private Comparator<Set<Integer>> rewardOrderSet = (P, Q) -> {
		if (rewardOrderSet(P, Q))
			return -1;
		if (rewardOrderSet(Q, P))
			return 1;
		return 0;
	};

	private boolean rewardOrderSet(Set<Integer> P, Set<Integer> Q)
	{
		return !P.equals(Q) && symDiff(Q, VMinus).contains(Collections.max(symDiff(P, Q), relevanceOrder));
	}

	// https://stackoverflow.com/a/8064726/7253478
	private static <T> Set<T> symDiff(final Set<? extends T> s1, final Set<? extends T> s2)
	{
		Set<T> symmetricDiff = new HashSet<>(s1);
		symmetricDiff.addAll(s2);
		Set<T> tmp = new HashSet<>(s1);
		tmp.retainAll(s2);
		symmetricDiff.removeAll(tmp);
		return symmetricDiff;
	}

	// One play profile is greater than another with respect to this ordering if the plays it describes are 'better' for player 0.
	private Comparator<PlayProfile> playProfileOrder = (p1, p2) -> {
		if (playProfileOrder(p1, p2))
			return -1;
		if (playProfileOrder(p2, p1))
			return 1;
		return 0;
	};

	private boolean playProfileOrder(PlayProfile p1, PlayProfile p2)
	{
		return rewardOrder.compare(p1.u, p2.u) < 0 || (p1.u == p2.u && rewardOrderSet.compare(p1.P, p2.P) < 0)
				|| (p1.u == p2.u && p1.P.equals(p2.P) && VMinus.contains(p2.u) && p1.e < p2.e)
				|| (p1.u == p2.u && p1.P.equals(p2.P) && VPlus.contains(p2.u) && p1.e > p2.e);
	}

}
