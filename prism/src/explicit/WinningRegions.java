package explicit;

import java.util.BitSet;

/**
 * Winning regions for 2-player turn-based games (TGs).
 */
public class WinningRegions
{

	/**
	 * Winning region player 1 (1-indexed).
	 */
	protected BitSet w1 = new BitSet();
	/**
	 * Winning region player 2 (2-indexed).
	 */
	protected BitSet w2 = new BitSet();

	/**
	 * Get the winning region for the player (1-indexed).
	 * @param player player (1-indexed)
	 */
	public BitSet get(int player)
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
	 */
	public void set(int player, BitSet region)
	{
		if (player == 1) {
			w1 = region;
		} else {
			w2 = region;
		}
	}

}