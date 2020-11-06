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

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import prism.PlayerInfo;
import prism.PlayerInfoOwner;
import prism.PrismException;

/**
 * Simple explicit-state representation of a turn-based (multi-player) game (TG).
 */
public class TGSimple extends LTSSimple implements TG
{
	/**
	 * Which player owns each state
	 */
	protected StateOwnersSimple stateOwners;
	
	/**
	 * Player + coalition information
	 */
	protected PlayerInfo playerInfo;
	
	// Constructors

	/**
	 * Constructor: empty TG.
	 */
	public TGSimple()
	{
		super();
		stateOwners = new StateOwnersSimple();
		playerInfo = new PlayerInfo();
	}

	/**
	 * Constructor: new TG with fixed number of states.
	 */
	public TGSimple(int numStates)
	{
		super(numStates);
		stateOwners = new StateOwnersSimple(numStates);
		playerInfo = new PlayerInfo();
	}

	/**
	 * Copy constructor
	 */
	public TGSimple(TGSimple tg)
	{
		super(tg);
		stateOwners = new StateOwnersSimple(tg.stateOwners);
		playerInfo = new PlayerInfo(tg.playerInfo);
	}
	
	/**
	 * Construct a TG from an existing one and a state index permutation,
	 * i.e. in which state index i becomes index permut[i].
	 * Player and coalition info is also copied across.
	 */
	public TGSimple(TGSimple tg, int permut[])
	{
		super(tg, permut);
		stateOwners = new StateOwnersSimple(tg.stateOwners, permut);
		playerInfo = new PlayerInfo(tg.playerInfo);
	}

	// Mutators

	@Override
	public void clearState(int s)
	{
		super.clearState(s);
		stateOwners.clearState(s);
	}

	@Override
	public void addStates(int numToAdd)
	{
		super.addStates(numToAdd);
		// Assume all player 1
		for (int i = 0; i < numToAdd; i++) {
			stateOwners.addState(1);
		}
	}

	/**
	 * Add a new (player {@code p}) state and return its index.
	 * @param p Player who owns the new state (1-indexed)
	 */
	public int addState(int p)
	{
		super.addState();
		stateOwners.addState(p);
		return numStates - 1;
	}

	/**
	 * Set the player that owns state {@code s} to {@code p}.
	 * @param s State to be modified (0-indexed)
	 * @param p Player who owns the state (1-indexed)
	 */
	public void setPlayer(int s, int p)
	{
		stateOwners.setPlayer(s, p);
	}

	/**
	 * Copy the player info from another model
	 */
	public void copyPlayerInfo(PlayerInfoOwner model)
	{
		playerInfo = new PlayerInfo(model.getPlayerInfo());
	}
	
	// Accessors (for Model)
	
	@Override
	public void checkForDeadlocks(BitSet except) throws PrismException
	{
		for (int i = 0; i < numStates; i++) {
			if (trans.get(i).isEmpty() && (except == null || !except.get(i)))
				throw new PrismException("Game has a deadlock in state " + i + (statesList == null ? "" : ": " + statesList.get(i)));
		}
	}
	
	// Accessors (for PlayerInfoOwner)

	@Override
	public PlayerInfo getPlayerInfo()
	{
		return playerInfo;
	}
	
	// Accessors (for TG)
	
	@Override
	public int getPlayer(int s)
	{
		return playerInfo.getPlayer(stateOwners.getPlayer(s));
	}
}
