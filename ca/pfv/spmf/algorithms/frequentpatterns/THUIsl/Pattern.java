package ca.pfv.spmf.algorithms.frequentpatterns.THUIsl;

/* This file is copyright (c) 2024 Srikumar Krishnamoorthy
 * 
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
 
/**
 * Represents a pattern with a prefix, utility, support, and associated transaction IDs.
 * 
 * Supports information gain and entropy values for evaluating pattern significance.
 */

import java.util.ArrayList;
import java.util.List;

public class Pattern implements Comparable<Pattern>{
	
	/** Pattern string composed of prefix items */
    final String prefix;

    /** Utility value of the pattern */
    final float utility;

    /** Support (number of occurrences) of the pattern */
    final int sup;

    /** Index for sorting patterns in order of insertion */
    final int idx;

    /** List of transaction IDs containing this pattern */
    List<Integer> tidList = new ArrayList<>();

    /** Entropy of the pattern */
    double entropy = 0;

    /** Information gain of the pattern */
    double ig = 0;

    /** Class label if the pattern is pure (entropy = 0), otherwise -1 */
    int pureClass = -1;
	
	/**
     * Constructs a Pattern from a prefix array and utility list.
     *
     * @param prefix array of integers representing the prefix
     * @param length length of the prefix
     * @param X utility list containing item info and elements
     * @param idx insertion index
     */
	public Pattern(int[] prefix, int length, UtilityList X, int idx) {
		StringBuilder buffer = new StringBuilder();
		for (int i=0;i<length; i++){
			buffer.append(prefix[i]);
			buffer.append(" ");
		}
		buffer.append(X.item);
		this.prefix = buffer.toString();
		this.idx = idx;
		
		this.utility = X.getUtils();
		this.sup = X.elements.size();
		
		for (Element e : X.elements){
			this.tidList.add(e.tid + 1);
		}
		//this.patternLength = length + 1;
		this.entropy = X.entropy;
		this.ig = X.ig;
		
		if (this.entropy==0){
			this.pureClass = X.pureClass;
		}
	}
	
	/**
     * Constructs a Pattern with a given string prefix and support.
     *
     * @param p pattern string
     * @param support support count
     */
	public Pattern(String p, int support){
		this.prefix = p;
		this.utility = 0;
		this.sup = support;
		this.tidList = null;
		this.idx = 0;
	}

	public String getPrefix(){
		return this.prefix;
	}

	public int compareTo(Pattern o) {
		if(o == this){
			return 0;
		}
		float compare = this.utility - o.utility;
		if(compare !=0){
			return (int) compare;
		}
		return this.hashCode() - o.hashCode();
	}

}
