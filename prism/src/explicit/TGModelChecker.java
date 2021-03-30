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
import java.util.BitSet;
import java.util.List;

import acceptance.AcceptanceParity;
import acceptance.AcceptanceType;
import parser.ast.Coalition;
import parser.ast.Expression;
import parser.ast.ExpressionStrategy;
import parser.ast.ExpressionTemporal;
import parser.ast.ExpressionUnaryOp;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismNotSupportedException;

/**
 * Explicit-state model checker for turn-based games (TGs).
 */
public class TGModelChecker extends NonProbModelChecker
{
	/**
	 * Create a new TGModelChecker, inherit basic state from parent (unless null).
	 */
	public TGModelChecker(PrismComponent parent) throws PrismException
	{
		super(parent);
	}

	// Model checking functions

	@Override
	public StateValues checkExpression(Model model, Expression expr, BitSet statesOfInterest) throws PrismException
	{
		StateValues res;

		// <<>> or [[]] operator
		if (expr instanceof ExpressionStrategy) {
			return checkExpressionStrategy(model, (ExpressionStrategy) expr, statesOfInterest);
		}
		// Otherwise, use the superclass
		else {
			res = super.checkExpression(model, expr, statesOfInterest);
		}

		return res;
	}

	/**
	 * Model check a <<>> or [[]] operator expression and return the values for the statesOfInterest.
	 * * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionStrategy(Model model, ExpressionStrategy expr, BitSet statesOfInterest) throws PrismException
	{
		// Only support <<>> right now, not [[]]
		if (!expr.isThereExists())
			throw new PrismNotSupportedException("The " + expr.getOperatorString() + " operator is not yet supported");

		// Multiple (>2) coalitions not supported
		if (expr.getNumCoalitions() > 1) {
			throw new PrismNotSupportedException("The " + expr.getOperatorString() + " operator can only contain one coalition");
		}
		// Extract coalition info
		Coalition coalition = expr.getCoalition();

		// For now, just support a single path formula in parentheses for now
		List<Expression> exprs = expr.getOperands();
		if (exprs.size() > 1) {
			throw new PrismException("Cannot currently check strategy operators with lists of expressions");
		}
		Expression exprSub = exprs.get(0);
		if (!Expression.isParenth(exprSub)) {
			throw new PrismException("Cannot currently check this strategy formula");
		}
		exprSub = ((ExpressionUnaryOp) exprSub).getOperand();
		if (exprSub.isSimplePathFormula() && exprSub instanceof ExpressionTemporal && ((ExpressionTemporal) exprSub).getOperator() == ExpressionTemporal.P_F) {
			return checkReach(model, (ExpressionTemporal) exprSub, coalition, statesOfInterest);
		} else {
			return checkLTL(model, (ExpressionTemporal) exprSub, coalition, statesOfInterest);
		}
	}

	/**
	 * Model check a reachability temporal operator from, inside a <<>> 
	 */
	protected StateValues checkReach(Model model, ExpressionTemporal expr, Coalition coalition, BitSet statesOfInterest) throws PrismException
	{
		// Model check operands for all states
		BitSet target = checkExpression(model, expr.getOperand2(), null).getBitSet();

		// Compute/return the result
		BitSet result = computeReach((TG) model, target, coalition);

		return StateValues.createFromBitSet(result, model);
	}

	/**
	 * Model check an LTL formula from inside a <<>> 
	 */
	protected StateValues checkLTL(Model model, ExpressionTemporal expr, Coalition coalition, BitSet statesOfInterest) throws PrismException
	{
		// For LTL model checking routines
		LTLModelChecker mcLtl = new LTLModelChecker(this);

		// Build product of TG and automaton
		AcceptanceType[] allowedAcceptance = {
				AcceptanceType.PARITY
		};
		LTLModelChecker.LTLProduct<TG> product = mcLtl.constructProductTG(this, (TG) model, expr, statesOfInterest, allowedAcceptance);
		
		// Get list of priorities for all states in product
		AcceptanceParity accPar = (AcceptanceParity) product.getAcceptance();
		List<Integer> priorities = accPar.getPriorities(product.getProductModel().getNumStates());
		// Replace unknown priorities and convert all to max-even if needed
		AcceptanceParity.replaceMissingPriorities(priorities, accPar.getObjective());
		AcceptanceParity.convertPrioritiesToEven(priorities, accPar.getParity());
		AcceptanceParity.convertPrioritiesToMax(priorities, accPar.getObjective());
		//mainLog.println(priorities);
		
		// Solve parity objective on product
		TGModelChecker mcProduct = new TGModelChecker(this);
		mcProduct.inheritSettings(this);
		BitSet result = mcProduct.computeParity((TG) product.getProductModel(), priorities, coalition);
		
		return StateValues.createFromBitSet(result, model);
	}

	/**
	 * Compute reachability
	 * @param tg TG
	 * @param target Target states
	 * @param coalition Players trying to reach the target
	 */
	protected BitSet computeReach(TG tg, BitSet target, Coalition coalition) throws PrismException
	{
		// Temporarily make the model a 2-player TG (if not already) by setting coalition
		tg.setCoalition(coalition);
		BitSet res = computeReach(tg, target);
		tg.setCoalition(null);
		return res;
	}

	/**
	 * Compute 2-player reachability
	 * @param tg 2-player TG
	 * @param target Target states
	 */
	protected BitSet computeReach(TG tg, BitSet target) throws PrismException
	{
		List<Integer> priorities = new ArrayList<>();
		// Priorities for fig3_1
//				priorities.add(4);
//				priorities.add(3);
//				priorities.add(2);
//				priorities.add(1);
//				priorities.add(0);
//				priorities.add(1);
//				priorities.add(2);
//				priorities.add(3);
//				priorities.add(0);
		// Priorities for random1
		priorities.add(9);
		priorities.add(7);
		priorities.add(1);
		priorities.add(10);
		priorities.add(2);
		priorities.add(11);
		priorities.add(9);
		priorities.add(9);
		priorities.add(12);
		priorities.add(6);
		System.out.println("Parity " + computeParity(new PG(tg, priorities)));

		return new RGSolver(this, tg, target).solve();
	}

	/**
	 * Compute parity
	 * @param tg TG
	 * @param priorities State priorities
	 * @param coalition Players trying to reach the target
	 */
	protected BitSet computeParity(TG tg, List<Integer> priorities, Coalition coalition) throws PrismException
	{
		// Temporarily make the model a 2-player TG (if not already) by setting coalition
		tg.setCoalition(coalition);
		BitSet res = computeParity(new PG(tg, priorities));
		tg.setCoalition(null);
		return res;
	}

	/**
	 * Compute 2-player parity
	 * @param tg 2-player TG
	 * @param priorities List of priorities
	 */
	protected BitSet computeParity(PG pg) throws PrismException
	{
		// return new ZielonkaRecursive(this, pg).solve();
		return new SmallProgressMeasures(this, pg).solve();
		// return new PriorityPromotion(this, pg).solve();
	}
}
