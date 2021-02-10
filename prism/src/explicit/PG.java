package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of a parity game (PG).
 */
public class PG
{

	/**
	 * Turn-based game (TG)
	 */
	protected TGSimple tg;
	/**
	 * List of priorities
	 */
	protected List<Integer> priorities;
	/**
	 * Map of priorities to states 
	 */
	protected Map<Integer, BitSet> priorityMap = new HashMap<>();

	/**
	 * Create a new parity game (PG).
	 */
	public PG(TG tg, List<Integer> priorities)
	{
		this.tg = (TGSimple) tg;
		this.priorities = priorities;
		for (int s = 0; s < priorities.size(); s++) {
			priorityMap.computeIfAbsent(priorities.get(s), priority -> new BitSet()).set(s);
		}
	}

	/**
	 * Empty constructor.
	 */
	public PG()
	{
	}

	public TG getTG()
	{
		return tg;
	}

	public void setTG(TG tg)
	{
		this.tg = (TGSimple) tg;
	}

	public List<Integer> getPriorities()
	{
		return priorities;
	}

	public void setPriorities(List<Integer> priorities)
	{
		this.priorities = priorities;
	}

	public Map<Integer, BitSet> getPriorityMap()
	{
		return priorityMap;
	}

	public void setPriorityMap(Map<Integer, BitSet> priorityMap)
	{
		this.priorityMap = priorityMap;
	}

	public PG subgame(BitSet states)
	{
		PG pg = new PG();
		pg.tg = (TGSimple) tg.subgame(states);
		pg.priorities = new ArrayList<>(priorities);
		pg.priorityMap = new HashMap<>(priorityMap);

		BitSet removing = (BitSet) pg.tg.getActiveStates().clone();
		removing.andNot(states);
		removing.stream().forEach(s -> {
			pg.priorities.set(s, -1);
			pg.priorityMap.computeIfPresent(pg.priorities.get(s), (k, v) -> {
				v.clear(s);
				return v.isEmpty() ? null : v;
			});
		});

		return pg;
	}

	public PG difference(BitSet states)
	{
		PG pg = new PG();
		pg.tg = (TGSimple) tg.difference(states);
		pg.priorities = new ArrayList<>(priorities);
		pg.priorityMap = new HashMap<>(priorityMap);

		states.stream().forEach(s -> {
			pg.priorities.set(s, -1);
			pg.priorityMap.computeIfPresent(pg.priorities.get(s), (k, v) -> {
				v.clear(s);
				return v.isEmpty() ? null : v;
			});
		});

		return pg;
	}

}
