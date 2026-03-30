package ca.pfv.spmf.algorithms.episodes.standardepisoderules;

import java.text.DecimalFormat;
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
 * Copyright Yangming Chen, 2021
 */

/**
 * This class contains the algorithm for generating episode rules from rules found by EMMA or TKE
 * 
 * @see AlgoGenerateEpisodeRules
 * @author Yangming Chen
 */
public class EpisodeRule {
	
	/** the events in the rule antecedent */
	public List<int[]> antecedent;
	
	/** the events in the rule consequent */
	public List<int[]> consequent;
	
	/** the support of the rule */
	private int totalCount;

	/** the support of the rule antecedent (for calculating the confidence)*/
	private int antiCount;
	
	/** 
	 * Constructor
	 * @param antiEvents the antecedent
	 * @param conseEvents the consequent
	 * @param totalCount the support of the rule
	 * @param antiCount the support of the antecedent
	 */
	public EpisodeRule(List<int[]> antiEvents, List<int[]> conseEvents, int totalCount, int antiCount) {
		this.antecedent = antiEvents;
		this.consequent = conseEvents;
		
		this.totalCount = totalCount;
		this.antiCount = antiCount;
	}
	
	
	/**
	 * Get the support of this rule
	 * @return the support
	 */
	public int getTotalCount() {
		return totalCount;
	}
	
	/**
	 * Get the antecedent of this rule
	 * @return a list of event sets
	 */
	public List<int[]> getAntiEvents() {
		return antecedent;
	}
	
	/**
	 * Get the consequent of this rule
	 * @return a list of event sets
	 */
	public List<int[]> getConseEvents() {
		return consequent;
	}
	/**
	 * Get the support of the antecedent
	 * @return the support
	 */
	public int getAntiCount() {
		return antiCount;
	}
	

	/**
	 * Get a string representation of this episode rule
	 * 
	 * @return a string
	 */
	public String toString() {
		DecimalFormat formater = new DecimalFormat("#.###");
		StringBuilder buffer = new StringBuilder();
		
		// write Antecedent
		List<int[]> antecedent = getAntiEvents();
		for (int j = 0; j < antecedent.size(); j++) {
			int[] eventSet = antecedent.get(j);
			buffer.append('{');
			for (int i = 0; i < eventSet.length; i++) {
				buffer.append(eventSet[i]);
				if (i != eventSet.length - 1) {
					buffer.append(',');
				}else {
					buffer.append('}');
				}
			}
		}

		buffer.append(" ==> ");
		
		// write consequent
		List<int[]> consequent = getConseEvents();
		for (int j = 0; j < consequent.size(); j++) {
			int[] eventSet = consequent.get(j);
			buffer.append('{');
			for (int i = 0; i < eventSet.length; i++) {
				buffer.append(eventSet[i]);
				if (i != eventSet.length - 1) {
					buffer.append(',');
				}else {
					buffer.append('}');
				}
			}
		}

		// write the support an confidence
		buffer.append(" #SUP: ");
		buffer.append(this.getTotalCount());
		buffer.append(" #CONF: ");
		double confidence = this.getTotalCount() / (double) this.getAntiCount();
		buffer.append(formater.format(confidence));
		buffer.append(System.lineSeparator());
		return buffer.toString();
	}
}
