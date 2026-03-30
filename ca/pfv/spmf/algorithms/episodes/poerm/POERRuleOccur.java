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
* This is used to record a Partially-Ordered Episode Rule Omitted the antiEpisode<br/>
* <br/>
*
* Paper: Mining Partially-Ordered Episode Rules in an Event Sequence
* @see POERMALL
* @see AlgoPOERM
*/

public class POERRuleOccur {
	/** the conseEpisode of a Partially-Ordered Episode Rule*/
	private List<Integer> episode;
	
	/** the appear time interval of a Partially-Ordered Episode Rule*/
	private List<RuleInterval> intervals;
	
	public POERRuleOccur(List<Integer> episode, List<RuleInterval> intervals) {
		this.setEpisode(episode);
		this.setIntervals(intervals);
	}
	
	public List<Integer> getEpisode() {
		return episode;
	}
	
	public void setEpisode(List<Integer> episode) {
		this.episode = episode;
	}
	
	public List<RuleInterval> getIntervals() {
		return intervals;
	}
	
	public void setIntervals(List<RuleInterval> intervals) {
		this.intervals = intervals;
	}
	
	public String toString() {
		return "episode: " + this.episode.toString() + " " + "intervals: " + this.intervals.toString();
	}
	
}
