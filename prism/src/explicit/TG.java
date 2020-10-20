//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham)
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

package explicit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import explicit.graphviz.StateOwnerDecorator;
import prism.ModelType;
import prism.PlayerInfoOwner;
import prism.PrismException;
import prism.PrismLog;

/**
 * Interface for classes that provide (read) access to an explicit-state turn-based (multi-player) game (TG).
 */
public interface TG extends LTS, PlayerInfoOwner
{
	// Accessors (for Model) - default implementations
	
	@Override
	default ModelType getModelType()
	{
		return ModelType.TG;
	}

	@Override
	default void exportToDotFile(PrismLog out, Iterable<explicit.graphviz.Decorator> decorators)
	{
		// Copy any existing decorators
		List<explicit.graphviz.Decorator> decoratorsNew = new ArrayList<>();
		if (decorators != null) {
			for (explicit.graphviz.Decorator decorator : decorators) {
				decoratorsNew.add(decorator);
			}
		}
		// And add a new one that draws states according to player owner
		decoratorsNew.add(new StateOwnerDecorator(this::getPlayer));
		LTS.super.exportToDotFile(out, decoratorsNew);
	}
	
	@Override
	default void exportToPrismLanguage(final String filename) throws PrismException
	{
		throw new UnsupportedOperationException();
	}
	
	@Override
	default String infoString()
	{
		final int numStates = getNumStates();
		String s = "";
		s += numStates + " states (" + getNumInitialStates() + " initial)";
		s += ", " + getNumTransitions() + " transitions";
		return s;
	}

	@Override
	default String infoStringTable()
	{
		final int numStates = getNumStates();
		String s = "";
		s += "States:      " + numStates + " (" + getNumInitialStates() + " initial)\n";
		s += "Transitions: " + getNumTransitions() + "\n";
		return s;
	}

	// Accessors
	
	/**
	 * Get the player that owns state {@code s}.
	 * Returns the index of the player (1-indexed).
	 * @param s Index of state (0-indexed)
	 */
	public int getPlayer(int s);
	
	/**
	 * Get an iterator over the transitions from choice {@code i} of state {@code s}.
	 */
	public Iterator<Entry<Integer, Double>> getTransitionsIterator(int s, int i);
}
