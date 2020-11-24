//==============================================================================
//	
//	Copyright (c) 2016-
//	Authors:
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package acceptance;

import java.io.PrintStream;
import java.util.BitSet;

import prism.PrismException;
import prism.PrismNotSupportedException;
import jdd.JDDVars;

/**
 * A Parity acceptance condition. Is accepting if 
 * "the minimal/maximal priority visited infinitely often is even/odd"
 */
public class AcceptanceParity implements AcceptanceOmega
{
	/** The definition i.e. min or max */
	private Defn defn;
	/** The parity i.e. even or odd */
	private Parity parity;
	/** The priority */
	private int priority;

	/** Constructor */
	public AcceptanceParity(Defn defn, Parity parity, int priority)
	{
		this.defn = defn;
		this.parity = parity;
		this.priority = priority;
	}

	/** Get the definition */
	public Defn getDefn()
	{
		return defn;
	}

	/** Set the definition */
	public void setDefn(Defn defn)
	{
		this.defn = defn;
	}

	/** Get the parity */
	public Parity getParity()
	{
		return parity;
	}

	/** Set the parity */
	public void setParity(Parity parity)
	{
		this.parity = parity;
	}

	/** Get the priority */
	public int getPriority()
	{
		return priority;
	}

	/** Set the priority */
	public void setPriority(int priority)
	{
		this.priority = priority;
	}

	/** Make a copy of the acceptance condition. */
	public AcceptanceParity clone()
	{
		return new AcceptanceParity(defn, parity, priority);
	}

	@Override
	public boolean isBSCCAccepting(BitSet bscc_states)
	{
	}

	/**
	 * Get the Rabin acceptance condition that is the equivalent of this Parity condition.
	 */
	public AcceptanceRabin toRabin(int numStates)
	{
	}

	/**
	 * Get the Streett acceptance condition that is the equivalent of this Parity condition.
	 */
	public AcceptanceStreett toStreett(int numStates)
	{
	}

	/**
	 * Get a Rabin acceptance condition that is the complement of this condition, i.e.,
	 * any word that is accepted by this condition is rejected by the returned Rabin condition.
	 *
	 * @param numStates the number of states in the underlying model / automaton (needed for complementing BitSets)
	 * @return the complement Rabin acceptance condition
	 */
	public AcceptanceRabin complementToRabin(int numStates)
	{
	}

	/**
	 * Get a Streett acceptance condition that is the complement of this condition, i.e.,
	 * any word that is accepted by this condition is rejected by the returned Streett condition.
	 * <br>
	 * Relies on the fact that once the goal states have been reached, all subsequent states
	 * are goal states.
	 *
	 * @param numStates the number of states in the underlying model / automaton (needed for complementing BitSets)
	 * @return the complement Streett acceptance condition
	 */
	public AcceptanceStreett complementToStreett(int numStates)
	{
	}

	/** Complement this acceptance condition, return as AcceptanceGeneric. */
	public AcceptanceGeneric complementToGeneric()
	{
		return toAcceptanceGeneric().complementToGeneric();
	}

	@Override
	public AcceptanceOmega complement(int numStates, AcceptanceType... allowedAcceptance) throws PrismException
	{
		if (AcceptanceType.contains(allowedAcceptance, AcceptanceType.RABIN)) {
			return complementToRabin(numStates);
		} else if (AcceptanceType.contains(allowedAcceptance, AcceptanceType.STREETT)) {
			return complementToStreett(numStates);
		} else if (AcceptanceType.contains(allowedAcceptance, AcceptanceType.GENERIC)) {
			return complementToGeneric();
		}
		throw new PrismNotSupportedException("Can not complement " + getType() + " acceptance to a supported acceptance type");
	}

	@Override
	public void lift(LiftBitSet lifter)
	{
	}

	@Override
	public AcceptanceBuchiDD toAcceptanceDD(JDDVars ddRowVars)
	{
		return new AcceptanceBuchiDD(this, ddRowVars);
	}

	@Override
	public AcceptanceGeneric toAcceptanceGeneric()
	{
	}

	@Override
	public String getSignatureForState(int i)
	{
		return "";
	}

	@Override
	public String getSignatureForStateHOA(int stateIndex)
	{
		return "";
	}

	/** Returns a textual representation of this acceptance condition. */
	@Override
	public String toString()
	{
		return "";
	}

	@Override
	public String getSizeStatistics()
	{
		return "";
	}

	@Override
	public AcceptanceType getType()
	{
		return AcceptanceType.PARITY;
	}

	@Override
	@Deprecated
	public String getTypeAbbreviated()
	{
		return getType().getNameAbbreviated();
	}

	@Override
	@Deprecated
	public String getTypeName()
	{
		return getType().getName();
	}

	@Override
	public void outputHOAHeader(PrintStream out)
	{
		out.println("acc-name: parity " + defn.name().toLowerCase() + " " + parity.name().toLowerCase() + " " + priority);
		//		out.println("Acceptance: 1 Inf(0)");
	}

	public enum Defn {
		MIN, MAX;
	}

	public enum Parity {
		EVEN, ODD;
	}

}
