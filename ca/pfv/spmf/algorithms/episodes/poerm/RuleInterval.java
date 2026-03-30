package ca.pfv.spmf.algorithms.episodes.poerm;

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
* This is used to record the time appear interval of a Partially-Ordered Episode Rule<br/>
* <br/>
*
* Paper: Mining Partially-Ordered Episode Rules in an Event Sequence
* @see POERMALL
* @see AlgoPOERM
*/

public class RuleInterval {
	/** the antiEpisode's start time of a Partially-Ordered Episode Rule*/
	public int antiStart;
	
	/** the antiEpisode's end time of a Partially-Ordered Episode Rule*/
	public int antiEnd;
	
	/** the conseEpisode's start time of a Partially-Ordered Episode Rule*/
	public int start;
	
	/** the conseEpisode's end time of a Partially-Ordered Episode Rule*/
	public int end;
	
	public RuleInterval(int antiStart, int antiEnd, int start, int end) {
		this.antiStart = antiStart;
		this.antiEnd = antiEnd;
		this.start = start;
		this.end = end;
	}
	
	public Boolean equal(RuleInterval other) {
		if (this.antiStart == other.antiEnd && this.antiEnd == other.antiEnd && this.start == other.start && this.end == other.end) {
			return true;
		}
		return false;
	}
	
	public String toString() {
		return this.antiStart + " " + this.antiEnd + " " + this.start + " " + this.end;
	}
}
