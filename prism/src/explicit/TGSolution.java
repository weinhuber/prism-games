package explicit;

/**
 * Solution to a 2-player turn-based game (TG).
 */
public class TGSolution
{

	/**
	 * Winning pair for player 1 (1-indexed). 
	 */
	protected RegionStrategy w1 = new RegionStrategy();
	/**
	 * Winning pair for player 2 (1-indexed). 
	 */
	protected RegionStrategy w2 = new RegionStrategy();

	/**
	 * Get the winning pair.
	 * @param player player (1-indexed)
	 */
	public RegionStrategy get(int player)
	{
		if (player == 1) {
			return w1;
		} else {
			return w2;
		}
	}

	/**
	 * Set the winning pair for the player (1-indexed).
	 * @param player player (1-indexed)
	 * @param pair Winning pair
	 */
	public void set(int player, RegionStrategy pair)
	{
		if (player == 1) {
			w1 = pair;
		} else {
			w2 = pair;
		}
	}

	@Override
	public String toString()
	{
		// Add a new line before and after.
		String s = "\nTGSolution:\n";
		s += "Player 1:\n";
		s += "Region: " + w1.getRegion() + "\n";
		s += "Strategy: " + w1.getStrategy() + "\n";
		s += "Player 2:\n";
		s += "Region: " + w2.getRegion() + "\n";
		s += "Strategy: " + w2.getStrategy() + "\n";
		return s;
	}

}