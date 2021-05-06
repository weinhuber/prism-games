package explicit;

import java.util.BitSet;

/**
 * Representation of a reachability game (RG).
 */
public class RG
{

	/**
	 * Turn-based game (TG)
	 */
	protected TGSimple tg;
	/**
	 * Target set
	 */
	protected BitSet target;

	/**
	 * Create a new reachability game (RG).
	 */
	public RG(TG tg, BitSet target)
	{
		this.tg = (TGSimple) tg;
		this.target = target;
	}

	/** 
	 * Get the turn-based game (TG). 
	 */
	public TG getTG()
	{
		return tg;
	}

	/** 
	 * Set the turn-based game (TG).
	 * @param tg TG
	 */
	public void setTG(TG tg)
	{
		this.tg = (TGSimple) tg;
	}

	/** 
	 * Get the target set.
	 */
	public BitSet getTarget()
	{
		return target;
	}

	/** 
	 * Set the target set. 
	 * @param target target set
	 */
	public void setTarget(BitSet target)
	{
		this.target = target;
	}

}
