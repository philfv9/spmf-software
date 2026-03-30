package ca.pfv.spmf.algorithms.frequentpatterns.lppgrowth;

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
/**
 * This class represents some time intervals.
 * 
 * @author Peng yang
 * @see AlgoLPPGrowth
 */
public class TimeIntervals {
	/** List of time intervals */
	List<int[]> intervals = new ArrayList<>();

	/** start point of the current interval */
	int left = -1;

	/** constructor */
	TimeIntervals() {
	}

	/**
	 * Add a time interval
	 * 
	 * @param right the end point of the current interval
	 */
	void addTimeInterval(int right) {
		intervals.add(new int[] { left, right });
//        totalDuration += (right - left );
	}

	/**
	 * Check whether a timestamp is in the time-intervals
	 * 
	 * @param timestamp the timestamps
	 * @return true if yes, otherwise false
	 */
	boolean isInside(int timestamp) {
		for (int[] timeInterval : intervals) {
			if (timeInterval[0] <= timestamp && timeInterval[1] >= timestamp) {
				return true;
			}
		}
		return false;
	}

	/**
	 * get total duration of this time-interval
	 * 
	 * @return the total duration
	 */
	int getTotalDuration() {
		int totalDuration = 0;
		for (int[] timeInterval : intervals) {
			totalDuration += (timeInterval[1] - timeInterval[0]);
		}
		return totalDuration;
	}

}
