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

package prism;

import java.util.ArrayList;
import java.util.List;

import parser.ast.Coalition;

/**
 * Information about the players in a game model and,
 * optionally, how they are split into coalitions.
 */
public class PlayerInfo
{
	/**
	 * Names of players. These are optional and can be "" if undefined,
	 * but all normally defined names should be unique.
	 * The size of this list defines the number of players in the game.
	 * NB: take care that most indexing of players is from 1 not 0,
	 * but this list is 0-indexed. 
	 */
	protected List<String> playerNames;
	
	/**
	 * Optionally, a mapping from player indices to 1 or 2,
	 * as induced by a coalition of players (those in the coalition
	 * are mapped to 1, those who are not are mapped to 2).
	 * The mapped index of the player with original index {@code p}
	 * is stored as {@code coalitionPlayerMap[p]}.
	 * Since players are indexed from 1, this array is of size
	 * numPlayers + 1, and the first elements is unused.
	 */
	protected int[] coalitionPlayerMap;
	
	// Constructors
	
	/**
	 * Constructor: empty
	 */
	public PlayerInfo()
	{
		playerNames = new ArrayList<String>();
		coalitionPlayerMap = null;
	}

	/**
	 * Copy constructor
	 */
	public PlayerInfo(PlayerInfo gameInfo)
	{
		setPlayerNames(gameInfo.playerNames);
		setCoalitionPlayerMap(gameInfo.coalitionPlayerMap);
	}
	
	// Mutators
	
	/**
	 * Add a player, specifying it's name.
	 * Names are optional and can be null or "" if undefined,
	 * but all normally defined names should be unique.
	 */
	public void addPlayer(String playerName)
	{
		playerNames.add(playerName);
	}

	/**
	 * Set the names of all players, provided as a list.
	 * Names are optional and can be null or "" if undefined,
	 * but all normally defined names should be unique.
	 */
	public void setPlayerNames(List<String> playerNames)
	{
		this.playerNames = new ArrayList<String>(playerNames.size());
		for (String name : playerNames) {
			// Always store non-named as ""
			this.playerNames.add(name == null ? "" : name);
		}
	}

	/**
	 * Set a coalition of players for the model
	 * (which effectively makes it a 2-player model,
	 * with player 1 representing the coalition and 2 the rest).
	 * Pass null to remove any coalition info from the model.
	 * @param coalition Coalition info object
	 */
	public void setCoalition(Coalition coalition) throws PrismException
	{
		// Clear info if coalition is null
		if (coalition == null) {
			coalitionPlayerMap = null;
			return;
		}
		// Construct mapping
		int numPlayers = getNumPlayers();
		coalitionPlayerMap = new int[numPlayers + 1];
		coalitionPlayerMap[0] = -1;
		for (int p = 1; p <= numPlayers; p++) {
			boolean inCoalition = coalition.isPlayerIndexInCoalition(p, playerNames);
			// In coalition => player 1; not in coalition (or undefined) => player 2
			coalitionPlayerMap[p] = inCoalition ? 1 : 2;
		}
	}

	/**
	 * Directly set the optionally stored mapping from player indices to 1 or 2,
	 * as induced by a coalition of players (those in the coalition
	 * are mapped to 1, those who are not are mapped to 2).
	 * The mapped index of the player with original index {@code p}
	 * is stored as {@code coalitionPlayerMap[p]}.
	 * Since players are indexed from 1, this array is of size
	 * numPlayers + 1, and the first elements is unused.
	 * A null array can be passed to remove storage of the info.
	 */
	private void setCoalitionPlayerMap(int[] coalitionPlayerMap)
	{
		this.coalitionPlayerMap = coalitionPlayerMap == null ? null : coalitionPlayerMap.clone();
	}
	
	// Accessors
	
	/**
	 * Get the number of players in the game.
	 */
	public int getNumPlayers()
	{
		return playerNames.size();
	}
	
	/**
	 * Get the name of player {@code i}. Returns "" if unnamed.
	 * @param i Index of player (1-indexed)
	 */
	public String getPlayerName(int i)
	{
		return playerNames.get(i - 1);
	}
	
	/**
	 * Get the list of player names. A name is "" if the player is unnamed.
	 */
	public List<String> getPlayerNames()
	{
		return playerNames;
	}
	
	/**
	 * Get the index of the player currently representing player {@code origPlayer},
	 * after applying any mapping that exists due to the presence of a coalition.
	 */
	public int getPlayer(int origPlayer)
	{
		if (coalitionPlayerMap == null) {
			// No coalition: just return index
			return origPlayer;
		} else {
			// Coalition defined: look up if player 1 or 2
			// (note: undefined players are mapped to player 2)
			return origPlayer == -1 ? 2 : coalitionPlayerMap[origPlayer];
		}
	}
}
