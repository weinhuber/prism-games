package explicit;

import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * (region, strategy) pair for a player in a turn-based game (TG).
 */
public class RegionStrategy
{

	/**
	 * The region.
	 */
	protected BitSet region = new BitSet();
	/**
	 * The strategy.
	 * TreeMap is used to print the strategy in the order of vertices.
	 */
	protected Map<Integer, Integer> strategy = new TreeMap<>();

	/**
	 * Get the region for the player.
	 */
	public BitSet getRegion()
	{
		return region;
	}

	/**
	 * Set the region for the player.
	 * @param region region
	 */
	public void setRegion(BitSet region)
	{
		this.region = region;
	}

	/**
	 * Get the strategy for the player.
	 */
	public Map<Integer, Integer> getStrategy()
	{
		return strategy;
	}

	/**
	 * Set the strategy for the player.
	 * @param strategy strategy
	 */
	public void setStrategy(Map<Integer, Integer> strategy)
	{
		this.strategy = strategy;
	}

}