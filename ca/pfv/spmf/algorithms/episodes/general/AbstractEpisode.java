package ca.pfv.spmf.algorithms.episodes.general;

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
 * Copyright Peng Yang, Philippe Fournier-Viger, 2019
 */
/**
 * Class representing an abstract episode for frequent episode mining
 * @author Philippe Fournier-Viger, Peng Yang et al.
 *
 */
public class AbstractEpisode {
	/** the events in the serial episode (each event is a non-empty eventset) */
	public List<int[]> events;
	/** The support of episode */
	public int support;

	/**
	 * Constructor
	 * @param support support of the episode
	 */
	public AbstractEpisode(int support) {
		this.events = new ArrayList<>();
		this.support = support;
	}
	
	/**
	 * Constructor
	 * @param events the events
	 * @param support the support of this episode
	 */
	public AbstractEpisode(List<int[]> events, int support) {
		this.events = events;
		this.support = support;
	}
	
	
	/**
	 * Get the support
	 * @return the support
	 */
	public int getSupport() {
		return support;
	}
	
	/**
	 * Increase the support of this episode by 1 
	 */
	public void increaseSupport() {
		this.support++;
	}
	
	/** Get the events
	 * 
	 * @return the events
	 */
	public List<int[]> getEvents() {
		return events;
	}
	
    
    /**
     * Get the size of this episode.
     * Warning: this scans the episode to calculate the size so it should not
     * be called too often.
     * @return the size.
     */
    public int size() {
    	return events.size();
    }
    
    /**
	 * Get the last item (only for 1-episode to call)
	 * 
	 * @return the last item
	 */
	public int getLastItem() {
		return events.get(0)[0];
	}

	/**
	 * Get a string representation of this episode.
	 * @return a string
	 */
	public String toString() {
		String returnString = "";
		int episodeLength = events.size();
		for (int i = 0; i < episodeLength - 1; i++) {
			for (int j = 0; j < events.get(i).length - 1; j++) {
				returnString = returnString + String.valueOf(events.get(i)[j]) + " ";
			}
			returnString = returnString + String.valueOf(events.get(i)[events.get(i).length - 1]);
			returnString = returnString + " -1 ";
		}
		for (int j = 0; j < events.get(episodeLength - 1).length - 1; j++) {
			returnString = returnString + String.valueOf(events.get(episodeLength - 1)[j]) + " ";
		}
		returnString = returnString
				+ String.valueOf(events.get(episodeLength - 1)[events.get(episodeLength - 1).length - 1]);
		returnString = returnString + " -1 #SUP: " + String.valueOf(this.support);
		return returnString;
	}
	
	public boolean equal(List<int[]> b) {
		int episodeLength = events.size();
		if (b.size() != episodeLength) {
			//System.out.println(b.size() + " size1 " + episodeLength);
			return false;
		}
		for (int i = 0; i < episodeLength; i++) {
			if (events.get(i).length != b.get(i).length) {
			//	System.out.println("size2");
				return false;
			}
			for (int j = 0; j < events.get(i).length; j++) {
				if(events.get(i)[j] != b.get(i)[j]) {
			//		System.out.println(i + j + "3");
					return false;
				}
			}
		}
		return true;
	}

	

}