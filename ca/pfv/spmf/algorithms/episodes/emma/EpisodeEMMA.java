package ca.pfv.spmf.algorithms.episodes.emma;

import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.episodes.general.AbstractEpisode;
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
 * Copyright Peng Yang, Philippe Fournier-Viger, 2019
 */
/**
 * implement Class of Episode ( serial episode) in complex sequence it means
 * that the episode can contains multiple symbols for one time point
 *
 * @author Peng Yang
 * @see AlgoEMMA
 */
public class EpisodeEMMA extends AbstractEpisode  implements Comparable<EpisodeEMMA> {

	/**
	 * Constructor
	 * @param support the support
	 */
	EpisodeEMMA(int support) {
		super(support);
	}

	/**
	 * Constructor
	 * @param events the events
	 * @param support the support of this episode
	 */
	public EpisodeEMMA(List<int[]> events, int support) {
		super(events,support);
	} 

	

	/**
	 * Create an i-extension of this episode
	 * @param item the item used to do the i-extension
	 * @param support the support
	 * @return a new episode that is the i-extension
	 */
	public EpisodeEMMA iExtension(int item, int support) {
		int[] finalEventSet = this.events.get(events.size() - 1);
		int len = finalEventSet.length;
		int[] newEventSet = new int[len + 1];
		System.arraycopy(finalEventSet, 0, newEventSet, 0, len);
		newEventSet[len] = item;
		List<int[]> newEvents = new ArrayList<int[]>(events);
		// set the last eventSet to the new eventSet.
		newEvents.set(events.size() - 1, newEventSet);
		return new EpisodeEMMA(newEvents, support);
	}



	/**
	 * Create an s-extension of this episode
	 * @param fllowingEpisodeName the following episode name (set of items)
	 * @param support the support
	 * @return a new episode that is the s-extension of this episode
	 */
	public EpisodeEMMA sExtension(int[] fllowingEpisodeName, int support) {
		List<int[]> newEvents = new ArrayList<int[]>(events);
		newEvents.add(fllowingEpisodeName);
		return new EpisodeEMMA(newEvents, support);
	}

	
	
    /**
     * Compare this pattern with another pattern
     * @param o another pattern
     * @return 0 if equal, -1 if smaller, 1 if larger (in terms of support).
     */
    public int compareTo(EpisodeEMMA o) {
		if(o == this){
			return 0;
		}
		long compare =  this.support - o.support;
		if(compare > 0){
			return 1;
		}
		if(compare < 0){
			return -1;
		}
		return 0;
	}


}
