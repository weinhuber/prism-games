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

	public TG getTG()
	{
		return tg;
	}

	public void setTG(TG tg)
	{
		this.tg = (TGSimple) tg;
	}

	public BitSet getTarget()
	{
		return target;
	}

	public void setTarget(BitSet target)
	{
		this.target = target;
	}
	
}
