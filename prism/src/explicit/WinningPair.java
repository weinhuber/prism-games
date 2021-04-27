package explicit;

import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Winning pair (region, strategy) for a player in a 2-player turn-based game (TG).
 */
public class WinningPair
{

	/**
	 * Winning region.
	 */
	protected BitSet region = new BitSet();
	/**
	 * Winning strategy.
	 */
	protected Map<Integer, Integer> strategy = new TreeMap<>();

	/**
	 * Get the winning region for the player.
	 */
	public BitSet getRegion()
	{
		return region;
	}

	/**
	 * Set the winning region for the player.
	 * @param region region
	 */
	public void setRegion(BitSet region)
	{
		this.region = region;
	}

	/**
	 * Get the winning strategy for the player.
	 */
	public Map<Integer, Integer> getStrategy()
	{
		return strategy;
	}

	/**
	 * Set the winning strategy for the player.
	 * @param strategy strategy
	 */
	public void setStrategy(Map<Integer, Integer> strategy)
	{
		this.strategy = strategy;
	}
	
}