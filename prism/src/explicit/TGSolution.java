package explicit;

/**
 * Solution to a 2-player turn-based game (TG).
 */
public class TGSolution
{

	/**
	 * Winning pair for player 1 (1-indexed). 
	 */
	protected WinningPair w1 = new WinningPair();
	/**
	 * Winning pair for player 2 (1-indexed). 
	 */
	protected WinningPair w2 = new WinningPair();

	/**
	 * Get the winning pair.
	 * @param player player (1-indexed)
	 */
	public WinningPair get(int player)
	{
		if (player == 1) {
			return w1;
		} else {
			return w2;
		}
	}

	/**
	 * Set the winning region for the player (1-indexed).
	 * @param player player (1-indexed)
	 * @param pair Winning pair
	 */
	public void set(int player, WinningPair pair)
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
		String s = "TGSolution:\n";
		s += "Player 1:\n";
		s += "Region: " + w1.getRegion() + "\n";
		s += "Strategy: " + w1.getStrategy() + "\n";
		s += "Player 2:\n";
		s += "Region: " + w2.getRegion() + "\n";
		s += "Strategy: " + w2.getStrategy();
		return s;
	}

}