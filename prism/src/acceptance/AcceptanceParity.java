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
	/** The objective i.e. min or max */
	private Objective objective;
	/** The parity i.e. even or odd */
	private Parity parity;
	/** The maximal priority */
	private int maxPriority;

	/** Constructor */
	public AcceptanceParity(Objective objective, Parity parity, int maxPriority)
	{
		this.objective = objective;
		this.parity = parity;
		this.maxPriority = maxPriority;
	}

	/** Get the objective */
	public Objective getObjective()
	{
		return objective;
	}

	/** Set the objective */
	public void setObjective(Objective objective)
	{
		this.objective = objective;
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

	/** Get the maximal priority */
	public int getMaxPriority()
	{
		return maxPriority;
	}

	/** Set the maximal priority */
	public void setPriority(int maxPriority)
	{
		this.maxPriority = maxPriority;
	}

	/** Make a copy of the acceptance condition. */
	public AcceptanceParity clone()
	{
		return new AcceptanceParity(objective, parity, maxPriority);
	}

	@Override
	public boolean isBSCCAccepting(BitSet bscc_states)
	{
		return false;
	}

	/** Complement this acceptance condition, return as AcceptanceGeneric. */
	public AcceptanceGeneric complementToGeneric()
	{
		return toAcceptanceGeneric().complementToGeneric();
	}

	@Override
	public AcceptanceOmega complement(int numStates, AcceptanceType... allowedAcceptance) throws PrismException
	{
		if (AcceptanceType.contains(allowedAcceptance, AcceptanceType.GENERIC)) {
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
		return null;
	}

	@Override
	public AcceptanceGeneric toAcceptanceGeneric()
	{
		if (maxPriority == 0) {
			return new AcceptanceGeneric(emptyIsAccepting());
		}
		AcceptanceGeneric acceptanceGeneric = null;
		if (objective == Objective.MIN) {
			acceptanceGeneric = createPriority(0);
			for (int i = 1; i < maxPriority; i++) {
				acceptanceGeneric = isAccepting(i) ? new AcceptanceGeneric(AcceptanceGeneric.ElementType.AND, acceptanceGeneric, createPriority(i))
						: new AcceptanceGeneric(AcceptanceGeneric.ElementType.OR, acceptanceGeneric, createPriority(i));
			}
		} else {
			acceptanceGeneric = createPriority(maxPriority - 1);
			for (int i = maxPriority - 2; i >= 0; i--) {
				acceptanceGeneric = isAccepting(i) ? new AcceptanceGeneric(AcceptanceGeneric.ElementType.AND, acceptanceGeneric, createPriority(i))
						: new AcceptanceGeneric(AcceptanceGeneric.ElementType.OR, acceptanceGeneric, createPriority(i));
			}
		}
		return acceptanceGeneric;
	}

	public boolean isAccepting(int priority)
	{
		return priority % 2 == 0 ? parity == Parity.EVEN : parity == Parity.ODD;
	}

	public boolean emptyIsAccepting()
	{
		return (objective == Objective.MIN && parity == Parity.EVEN) || (objective == Objective.MAX && parity == Parity.ODD);
	}

	private AcceptanceGeneric createPriority(int priority)
	{
		BitSet state = new BitSet();
		state.set(priority);
		return isAccepting(priority) ? new AcceptanceGeneric(AcceptanceGeneric.ElementType.INF, state)
				: new AcceptanceGeneric(AcceptanceGeneric.ElementType.FIN, state);
	}

	@Override
	public String getSignatureForState(int i)
	{
		return isAccepting(i) ? "!" : " ";
	}

	@Override
	public String getSignatureForStateHOA(int stateIndex)
	{
		return isAccepting(stateIndex) ? "{" + stateIndex + "}" : "";
	}

	/** Returns a textual representation of this acceptance condition. */
	@Override
	public String toString()
	{
		String result = "";
		result += "Objective: " + objective.toString();
		result += "Parity: " + parity.toString();
		result += "Maximal Priority: " + maxPriority;
		return result;
	}

	@Override
	public String getSizeStatistics()
	{
		return "Maximal Priority: " + maxPriority;
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
		out.println("acc-name: parity " + objective.toString() + " " + parity.toString() + " " + maxPriority);
		out.print("Acceptance: " + maxPriority + " ");
		if (maxPriority == 0) {
			out.println(emptyIsAccepting() ? "t" : "f");
			return;
		}

		if (objective == Objective.MIN) {
			out.print(createPriorityString(0));
			for (int i = 1; i < maxPriority; i++) {
				out.print(joinPriorityString(i));
			}
		} else {
			out.print(createPriorityString(maxPriority - 1));
			for (int i = maxPriority - 2; i >= 0; i--) {
				out.print(joinPriorityString(i));
			}
		}
		for (int i = 0; i < maxPriority - 2; i++) {
			out.print(")");
		}
		out.println();
	}

	private String joinPriorityString(int priority)
	{
		String parens = priority != maxPriority - 1 ? "(" : "";
		return isAccepting(priority) ? " & " + parens + createPriorityString(priority) : " | " + parens + createPriorityString(priority);
	}

	private String createPriorityString(int priority)
	{
		return isAccepting(priority) ? "Inf(" + priority + ")" : "Fin(" + priority + ")";
	}

	public enum Objective {
		MIN, MAX;

		public String toString()
		{
			return this == MIN ? "min" : "max";
		}

		public static Objective fromString(String str)
		{
			return str.equalsIgnoreCase("min") ? MIN : MAX;
		}
	}

	public enum Parity {
		EVEN, ODD;

		public String toString()
		{
			return this == EVEN ? "even" : "odd";
		}

		public static Parity fromString(String str)
		{
			return str.equalsIgnoreCase("even") ? EVEN : ODD;
		}
	}

}
