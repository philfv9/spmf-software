package ca.pfv.spmf.algorithms.episodes.minepiplus;

import java.util.ArrayList;
import java.util.List;
/*
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright Peng Yang  2019
 */

import ca.pfv.spmf.algorithms.episodes.general.AbstractEpisode;

/**
 * This class is an Episode (serial episode) in a complex sequence (where an
 * episode can contain multiple symbols for the same time point). This is used
 * by the MINEPIPlus algorithm
 * 
 * @see AlgoMINEPIPlus
 * @author Peng Yang
 */
public class EpisodeMP extends AbstractEpisode {


	/** Constructor */
	EpisodeMP() {
		super(0);
	}

	/**
	 * Constructor
	 * 
	 * @param events  the events of this episode
	 * @param support the support of this episode
	 */
	EpisodeMP(List<int[]> events, int support) {
		super(events,support);
	}
	
	
	public boolean equal(List<int[]> b) {
		return events.equals(b);
	}
	/**
	 * Perform an i-extension of this episode with an item
	 * 
	 * @param item    the item
	 * @param support the support
	 * @return the resulting episode
	 */
	public EpisodeMP iExtension(int item, int support) {
		int[] finalEventSet = this.events.get(events.size() - 1);
		int len = finalEventSet.length;
		int[] newEventSet = new int[len + 1];
		System.arraycopy(finalEventSet, 0, newEventSet, 0, len);
		newEventSet[len] = item;
		List<int[]> newEvents = new ArrayList<int[]>(events);
		// set the last eventSet to the new eventSet.
		newEvents.set(events.size() - 1, newEventSet);
		return new EpisodeMP(newEvents, support);
	}
	
	/**
	 * Create an s-extension of this episode
	 * @param item the item used to do the s-extension
	 * @param support the support
	 * @return a new episode that is the s-extension of this episode
	 */
	public EpisodeMP sExtension(int item, int support) {
		List<int[]> newEvents = new ArrayList<int[]>(events);
		newEvents.add(new int[] { item });
		return new EpisodeMP(newEvents, support);
	}

}
