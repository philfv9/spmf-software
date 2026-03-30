package ca.pfv.spmf.algorithms.episodes.poerm;

import java.util.List;

/* This file is copyright (c) 2021  CHEN YANGMING, Philippe Fournier-Viger
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
*
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
*
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
* This is an auxiliary data structure of the "POERM and POERM-ALL" algorithm, 
* This is used to record each appearance of a event set <br/>
* <br/>
*
* Paper: Mining Partially-Ordered Episode Rules in an Event Sequence
* @see POERMALL
* @see AlgoPOERM
*/
public class EventSetAppear {
	/** the event set to be record */
	private List<Integer> eventSet;
	
	/** a list of the time interval of the eventSet appear */
	private List<Interval> intervals;
	public double key;
	public EventSetAppear(List<Integer> episode, List<Interval> intervals, double key) {
		this.setEventSet(episode);
		this.setIntervals(intervals);
		this.key = key;
	}
	
	public EventSetAppear(List<Integer> episode, List<Interval> intervals) {
		this.setEventSet(episode);
		this.setIntervals(intervals);
		//this.key = key;
	}
	
	public List<Integer> getEventSet() {
		return eventSet;
	}
	
	public void setEventSet(List<Integer> eventSet) {
		this.eventSet = eventSet;
	}
	
	public List<Interval> getIntervals() {
		return intervals;
	}
	
	public void setIntervals(List<Interval> intervals) {
		this.intervals = intervals;
	}
	
	public String toString() {
		return "eventSet: " + this.eventSet.toString() + " " + "intervals: " + this.intervals.toString();
	}
	
}
