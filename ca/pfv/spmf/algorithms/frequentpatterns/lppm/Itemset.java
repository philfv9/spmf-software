package ca.pfv.spmf.algorithms.frequentpatterns.lppm;

import java.util.List;

/*
 * Copyright (c) 2019 Peng Yang, Philippe Fournier-Viger et al.

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
 */
/**
 * This class represents an itemset as used by the LPPM algorithms.
 * 
 * @author Peng yang
 * @see AlgoLPPMBreadth1
 * @see AlgoLPPMBreadth2
 * @see AlgoLPPMDepth1
 * @see AlgoLPPMDepth2
 */
public class Itemset {
	/** the list of items in this itemset */
	private int[] items;

	/** the list of time intervals */
	private List<int[]> timeIntervals;

	/**
	 * Constructor of an itemset
	 * 
	 * @param items the items
	 * @param timeIntervals the time intervals
	 */
	Itemset(int[] items, List<int[]> timeIntervals) {
		this.items = items;
		this.timeIntervals = timeIntervals;
	}

	/**
	 * Constructor of an itemset containing a single item
	 * 
	 * @param item the item
	 */
	Itemset(int item, List<int[]> timeIntervals) {
		this.items = new int[] { item };
		this.timeIntervals = timeIntervals;
	}

	/**
	 * Get the time intervals for this itemset
	 * @return the time intervals
	 */
	public List<int[]> getTimeIntervals() {
		return timeIntervals;
	}

	/**
	 * Get the items contained in this itemset
	 * @return the items
	 */
	public int[] getItems() {
		return this.items;
	}

	@Override
	/**
	 * Get a string representation of this itemset
	 */
	public String toString() {
		String s = "";
		for (int n : items) {
			s = s + n + " ,";
		}
		s = s.substring(0, s.length() - 1);
		s += " : ";
		for (int[] interval : timeIntervals) {
			s = s + "[ " + interval[0] + " , " + interval[1] + " ] ";
		}
		return s;
	}

}
