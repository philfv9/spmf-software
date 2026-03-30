package ca.pfv.spmf.algorithms.episodes.maxfem;

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
 * @see AlgoMAXFEM
 */
public class EpisodeMaxEPM extends AbstractEpisode  implements Comparable<EpisodeMaxEPM> {
	
	int sumOfEvenItems;
	int sumOfOddItems;
	int realsize;

	/**
	 * Constructor
	 * @param support the support
	 */
	EpisodeMaxEPM(int support) {
		super(support);
		realsize = 0;
	}

	/**
	 * Constructor
	 * @param events the events
	 * @param support the support of this episode
	 */
	public EpisodeMaxEPM(List<int[]> events, int support) {
		super(events,support);
		
		for(int[] itemset : events) {
			for(int val : itemset) {
				if(val % 2 == 0) {
					sumOfEvenItems+=val;
				}else {
					sumOfOddItems+=val;
				}
			}
		}

		for(int[] itemset : events) {
			realsize+= itemset.length;
		}
	} 
	
    /**
     * Get the size of this episode.
     * Warning: this scans the episode to calculate the size so it should not
     * be called too often.
     * @return the size.
     */
    public int realSize() {
    	return realsize;
    }

	

//	/**
//	 * Create an i-extension of this episode
//	 * @param item the item used to do the i-extension
//	 * @param support the support
//	 * @return a new episode that is the i-extension
//	 */
//	public EpisodeMaxEPM iExtension(int item, int support) {
//		int[] finalEventSet = this.events.get(events.size() - 1);
//		int len = finalEventSet.length;
//		int[] newEventSet = new int[len + 1];
//		System.arraycopy(finalEventSet, 0, newEventSet, 0, len);
//		newEventSet[len] = item;
//		List<int[]> newEvents = new ArrayList<int[]>(events);
//		// set the last eventSet to the new eventSet.
//		newEvents.set(events.size() - 1, newEventSet);
//		return new EpisodeMaxEPM(newEvents, support);
//	}



	/**
	 * Create an s-extension of this episode
	 * @param flowingEpisodeName the following episode name (set of items)
	 * @param support the support
	 * @return a new episode that is the s-extension of this episode
	 */
	public EpisodeMaxEPM sExtension(int[] flowingEpisodeName, int support) {
		List<int[]> newEvents = new ArrayList<int[]>(events);
		newEvents.add(flowingEpisodeName);
		return new EpisodeMaxEPM(newEvents, support);
	}

	
	
	public int compareTo(EpisodeMaxEPM o) {
		if(o == this){
			return 0;
		}
		int compare = o.sumOfEvenItems + o.sumOfOddItems
				- this.sumOfEvenItems - this.sumOfOddItems;
		if(compare !=0){
			return compare;
		}

		return this.hashCode() - o.hashCode();
	}


}
