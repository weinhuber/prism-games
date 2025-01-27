//==============================================================================
//	
//	Copyright (c) 2002-
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

package parser;

/**
 * Information required to evaluate an expression: two State objects,
 * representing current/next (unprimed/primed) values for variables.
 * These are basically arrays of Objects, indexed according to a model file. 
 * Optionally values for constants can also be supplied.
 */
public class EvaluateContextStateAndNextState extends EvaluateContext
{
	private Values constantValues;
	private Object[] varValues;
	private Object[] nextVarValues;

	public EvaluateContextStateAndNextState(State state, State nextState)
	{
		this.constantValues = null;
		this.varValues = state.varValues;
		this.nextVarValues = nextState.varValues;
	}

	public EvaluateContextStateAndNextState(Values constantValues, State state, State nextState)
	{
		this.constantValues = constantValues;
		this.varValues = state.varValues;
		this.nextVarValues = nextState.varValues;
	}

	public Object getConstantValue(String name)
	{
		if (constantValues == null)
			return null;
		int i = constantValues.getIndexOf(name);
		if (i == -1)
			return null;
		return constantValues.getValue(i);
	}

	public Object getVarValue(String name, int index)
	{
		// Use index to look up value
		if(index >= varValues.length)
			return null;
		return index == -1 ? null : varValues[index];
	}

	public Object getPrimedVarValue(String name, int index)
	{
		// Use index to look up value
		if(index >= nextVarValues.length)
			return null;
		return index == -1 ? null : nextVarValues[index];
	}
}
