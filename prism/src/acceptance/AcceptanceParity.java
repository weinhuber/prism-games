package acceptance;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import jdd.JDDVars;
import prism.PrismException;
import prism.PrismNotSupportedException;

/**
 * A parity acceptance condition. The acceptance condition is accepting if 
 * "the minimal/maximal priority visited infinitely often is even/odd".
 */
public class AcceptanceParity implements AcceptanceOmega
{
	/** The objective i.e. min or max */
	private Objective objective;
	/** The parity i.e. even or odd */
	private Parity parity;
	/** Number of priorities */
	private int numPriorities;
	/** Acceptance sets, i.e., state indices for each priority */
	private List<BitSet> accSets;

	/** Constructor */
	public AcceptanceParity(Objective objective, Parity parity, int numPriorities)
	{
		this.objective = objective;
		this.parity = parity;
		this.numPriorities = numPriorities;
		accSets = new ArrayList<>(numPriorities);
		for (int p = 0; p < numPriorities; p++) {
			accSets.add(new BitSet());
		}
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

	/** Get the number of priorities */
	public int getNumPriorities()
	{
		return numPriorities;
	}
	
	/** Get the {@code p}th acceptance set,
	 * i.e., the states with priority {@code p} */
	public BitSet getAcceptanceSet(int p)
	{
		return accSets.get(p);
	}
	
	/** Set the {@code p}th acceptance set,
	 * i.e., the states with priority {@code p} */
	public void setAcceptanceSet(int p, BitSet b)
	{
		accSets.set(p, b);
	}
	
	/** Make a copy of the acceptance condition. */
	public AcceptanceParity clone()
	{
		AcceptanceParity accPar = new AcceptanceParity(objective, parity, numPriorities);
		for (int p = 0; p < numPriorities; p++) {
			accPar.setAcceptanceSet(p, (BitSet) getAcceptanceSet(p).clone());
		}
		return accPar;
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
		for (int p = 0; p < numPriorities; p++) {
			accSets.set(p, lifter.lift(accSets.get(p)));
		}
	}

	@Override
	public AcceptanceBuchiDD toAcceptanceDD(JDDVars ddRowVars)
	{
		return null;
	}

	@Override
	public AcceptanceGeneric toAcceptanceGeneric()
	{
		if (numPriorities == 0) {
			return new AcceptanceGeneric(emptyIsAccepting());
		}
		AcceptanceGeneric acceptanceGeneric = null;
		if (objective == Objective.MIN) {
			acceptanceGeneric = createPriority(0);
			for (int i = 1; i < numPriorities; i++) {
				acceptanceGeneric = isAccepting(i) ? new AcceptanceGeneric(AcceptanceGeneric.ElementType.AND, acceptanceGeneric, createPriority(i))
						: new AcceptanceGeneric(AcceptanceGeneric.ElementType.OR, acceptanceGeneric, createPriority(i));
			}
		} else {
			acceptanceGeneric = createPriority(numPriorities - 1);
			for (int i = numPriorities - 2; i >= 0; i--) {
				acceptanceGeneric = isAccepting(i) ? new AcceptanceGeneric(AcceptanceGeneric.ElementType.AND, acceptanceGeneric, createPriority(i))
						: new AcceptanceGeneric(AcceptanceGeneric.ElementType.OR, acceptanceGeneric, createPriority(i));
			}
		}
		return acceptanceGeneric;
	}

	/** Get a list of priorities for states 0<=s<n */
	public List<Integer> getPriorities(int n) {
		List<Integer> priorities = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			priorities.add(-1);
			for (int p = 0; p < numPriorities; p++) {
				if (accSets.get(p).get(i)) {
					priorities.set(i, p);
					continue;
				}
			}
		}
		return priorities;
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

	/** Get a list of the acceptance sets containing state {@code stateIndex} */
	private List<Integer> getAcceptanceSetIndices(int stateIndex)
	{
		List<Integer> list = new ArrayList<>();
		for (int p = 0; p < numPriorities; p++) {
			if (accSets.get(p).get(stateIndex)) {
				list.add(p);
			}
		}
		return list;
	}
	
	@Override
	public String getSignatureForState(int stateIndex)
	{
		List<Integer> list = getAcceptanceSetIndices(stateIndex);
		if (list.isEmpty()) {
			return " ";
		} else if (list.size() == 1) {
			return list.get(0).toString();
		} else {
			return new HashSet<>(list).toString();
		}
	}

	@Override
	public String getSignatureForStateHOA(int stateIndex)
	{
		List<Integer> list = getAcceptanceSetIndices(stateIndex);
		if (list.isEmpty()) {
			return "";
		}
		return new HashSet<>(list).toString();
	}

	/** Returns a textual representation of this acceptance condition. */
	@Override
	public String toString()
	{
		String result = "";
		result += "Objective: " + objective.toString() + "\n";
		result += "Parity: " + parity.toString() + "\n";
		result += "Number of priorities: " + numPriorities;
		result += "Acceptance sets:";
		for (int p = 0; p < numPriorities; p++) {
			result += " " + p + ":" + accSets.get(p);
		}
		return result;
	}

	@Override
	public String getSizeStatistics()
	{
		return "Number of priorities: " + numPriorities;
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
		out.println("acc-name: parity " + objective.toString() + " " + parity.toString() + " " + numPriorities);
		out.print("Acceptance: " + numPriorities + " ");
		if (numPriorities == 0) {
			out.println(emptyIsAccepting() ? "t" : "f");
			return;
		}

		if (objective == Objective.MIN) {
			out.print(createPriorityString(0));
			for (int i = 1; i < numPriorities; i++) {
				String parens = i != numPriorities - 1 ? "(" : "";
				out.print(isAccepting(i) ? " & " + parens + createPriorityString(i) : " | " + parens + createPriorityString(i));
			}
		} else {
			out.print(createPriorityString(numPriorities - 1));
			for (int i = numPriorities - 2; i >= 0; i--) {
				String parens = i != 0 ? "(" : "";
				out.print(isAccepting(i) ? " & " + parens + createPriorityString(i) : " | " + parens + createPriorityString(i));
			}
		}
		for (int i = 0; i < numPriorities - 2; i++) {
			out.print(")");
		}
		out.println();
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

	// Utility functions
	
	public static void replaceMissingPriorities(List<Integer> priorities, Objective objective) {
		// Add a dummy priority if some are missing
		if (priorities.contains(-1)) {
			if (objective == AcceptanceParity.Objective.MIN) {
				int maxPriority = Collections.max(priorities);
				for (int s = 0; s < priorities.size(); s++) {
					if (priorities.get(s) == -1) {
						priorities.set(s, maxPriority + 1);
					}
				}
			} else {
				for (int s = 0; s < priorities.size(); s++) {
					if (priorities.get(s) == -1) {
						priorities.set(s, 0);
					} else {
						priorities.set(s, priorities.get(s) + 1);
					}
				}
			}
		}
	}

	public static void convertPrioritiesToEven(List<Integer> priorities, Parity parity) {
		if (parity == AcceptanceParity.Parity.ODD) {
			for (int s = 0; s < priorities.size(); s++) {
				priorities.set(s, priorities.get(s) + 1);
			}
			shiftPiorities(priorities);
		}
	}

	public static void convertPrioritiesToMax(List<Integer> priorities, Objective objective) {
		if (objective == AcceptanceParity.Objective.MIN) {
			int d = Collections.max(priorities);
			if (d % 2 == 1) {
				d++;
			}
			for (int s = 0; s < priorities.size(); s++) {
				priorities.set(s, d - priorities.get(s));
			}
			shiftPiorities(priorities);
		}
	}

	public static void shiftPiorities(List<Integer> priorities) {
		// Shift so min is 0 or 1
		int minPriority = Collections.min(priorities);
		int shift = minPriority % 2 == 0 ? -minPriority : 1 - minPriority;
		for (int s = 0; s < priorities.size(); s++) {
			priorities.set(s, priorities.get(s) + shift);
		}
	}

}
