package explicit;

import java.util.Objects;
import java.util.Set;

/**
 * A representation of "play profiles" as appearing in the discrete strategy improvement algorithm.
 */
public class PlayProfile
{

	/**
	 * The most relevant node visited infinitely often.
	 */
	public int u;
	/**
	 * The set of nodes that are more relevant than u and visited before the first visit of u.
	 */
	public Set<Integer> P;
	/**
	 * The number of nodes visited before the first visit of u.
	 */
	public int e;

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		PlayProfile that = (PlayProfile) o;
		return u == that.u && e == that.e && P.equals(that.P);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(u, P, e);
	}

}
