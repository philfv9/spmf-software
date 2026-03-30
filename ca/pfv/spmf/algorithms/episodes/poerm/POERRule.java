package ca.pfv.spmf.algorithms.episodes.poerm;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.episodes.emma.EpisodeEMMA;

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
* This is used to record a Partially-Ordered Episode Rule <br/>
* <br/>
*
* Paper: Mining Partially-Ordered Episode Rules in an Event Sequence
* @see POERMALL
* @see AlgoPOERM
*/
public class POERRule {
	/** the antiEpisode of a Partially-Ordered Episode Rule*/
	private List<Integer> antiEpisode;
	
	/** the conseEpisode of a Partially-Ordered Episode Rule*/
	private List<Integer> conseEpisode;
	
	/** the appear time interval of a Partially-Ordered Episode Rule*/
	private List<RuleInterval> intervals;
	
	/** the antiEpisode appear time of a Partially-Ordered Episode Rule*/
	private int antiCount;
	
	/** the confident of a Partially-Ordered Episode Rule*/
	private int confidence;
	
	/** Object to format double numbers in decimal format */
	private DecimalFormat formatter = new DecimalFormat("#.####");
	
	public POERRule(List<Integer> antiEpisode, 
			List<Integer> conseEpisode, List<RuleInterval> intervals, 
			int antiCount, int confident) {
		this.setAntiEpisode(antiEpisode);
		this.setConseEpisode(conseEpisode);
		this.setIntervals(intervals);
		this.setAntiCount(antiCount);
		this.setConfident(confident);
	}
	
	

	public int match(List<int[]> antecedent) {
		int i = 0;
		List<Integer> intersection = new ArrayList<Integer>();
		intersection.addAll(antiEpisode);
		List<Integer> otherList = new ArrayList<Integer>();
		for (int j = 0; j < antecedent.size(); ++j) {
			int[] nowItemSet = antecedent.get(j);
			for (int k = 0; k < nowItemSet.length; ++k) {
				otherList.add(nowItemSet[k]);
			}
		}
		intersection.retainAll(otherList);
		if (intersection.size() == this.antiEpisode.size()) {
			return this.antiEpisode.size();
		}else {
			return 0;
		}
	//	return 0;
	}
	
	@Override
	public String toString() {
		String episodeRule = "";
		List<Integer> antiEpisode = this.getAntiEpisode();
		List<Integer> conseEpisode = this.getConseEpisode();
		for (Integer anti : antiEpisode) {
			episodeRule += anti + " ";
		}
		episodeRule += "==> ";
		for (Integer conse : conseEpisode) {
			episodeRule += conse + " ";
		}
		return "rule: " + episodeRule + "#SUP: " + this.getRuleCount() + " #CONF: "
				+ formatter.format(this.getRuleCount() / (double) this.getAntiCount());
	}
	
	/**
     * Compare this pattern with another pattern
     * @param o another pattern
     * @return 0 if equal, -1 if smaller, 1 if larger (in terms of support).
     */
    public int compareTo(POERRule o) {
		if(o == this){
			return 0;
		}
		long compare =  this.antiCount - o.antiCount;
		if(compare > 0){
			return 1;
		}
		if(compare < 0){
			return -1;
		}
		return 0;
	}
    
	public List<Integer> getAntiEpisode() {
		return antiEpisode;
	}
	public void setAntiEpisode(List<Integer> antiEpisode) {
		this.antiEpisode = antiEpisode;
	}
	public List<Integer> getConseEpisode() {
		return conseEpisode;
	}
	public void setConseEpisode(List<Integer> conseEpisode) {
		this.conseEpisode = conseEpisode;
	}
	public List<RuleInterval> getIntervals() {
		return intervals;
	}
	public void setIntervals(List<RuleInterval> intervals) {
		this.intervals = intervals;
	}
	public int getRuleCount() {
		return confidence;
	}
	public void setConfident(int confident) {
		this.confidence = confident;
	}
	public int getAntiCount() {
		return antiCount;
	}
	public void setAntiCount(int antiCount) {
		this.antiCount = antiCount;
	}
}
