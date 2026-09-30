package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.patterns.itemset_list_integers_without_support.Itemset;
/*
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Represents a sequential pattern as a list of itemsets with associated support information.
  * @author Philipe-Fournier-Viger
  * */
public class SequentialPattern implements Comparable<SequentialPattern> {

	/** List of itemsets that form this sequential pattern. */
	private final List<Itemset> itemsets;

	/** IDs of sequences that contain this pattern. */
	private List<Integer> sequencesIds;

	/** Flag indicating whether this pattern was found (used in some algorithms). */
	private boolean isFound = false;

	/** Additional support count to be added to the base support. */
	private int additionalSupport = 0;

	/**
	 * Sets the list of sequence IDs that contain this pattern.
	 * 
	 * @param sequencesIds list of sequence IDs
	 */
	public void setSequenceIDs(List<Integer> sequencesIds) {
		this.sequencesIds = sequencesIds;
	}

	/** Creates an empty sequential pattern. */
	public SequentialPattern() {
		itemsets = new ArrayList<Itemset>();
	}

	/**
	 * Returns the relative support of this pattern as a formatted string.
	 * 
	 * @param sequencecount number of sequences in the original database
	 * @return relative support formatted as a string
	 */
	public String getRelativeSupportFormated(int sequencecount) {
		double relSupport = ((double) sequencesIds.size()) / ((double) sequencecount);
		DecimalFormat format = new DecimalFormat();
		format.setMinimumFractionDigits(0);
		format.setMaximumFractionDigits(5);
		return format.format(relSupport);
	}

	/**
	 * Returns the absolute support of this pattern.
	 * 
	 * @return support count (integer ≥ 1)
	 */
	public int getAbsoluteSupport() {
		return sequencesIds.size();
	}

	/**
	 * Adds an itemset to this sequential pattern.
	 * 
	 * @param itemset itemset to add
	 */
	public void addItemset(Itemset itemset) {
		itemsets.add(itemset);
	}

	/**
	 * Returns a deep copy of this sequential pattern.
	 * 
	 * @return copied sequential pattern
	 */
	public SequentialPattern copy() {
		SequentialPattern clone = new SequentialPattern();
		for (Itemset it : itemsets) {
			clone.addItemset(it.cloneItemSet());
		}
		clone.additionalSupport = this.additionalSupport;
		clone.sequencesIds = new ArrayList<Integer>(this.sequencesIds);
		return clone;
	}

	/**
	 * Prints this sequential pattern to the standard output.
	 */
	public void print() {
		System.out.print(toString());
	}

	/**
	 * Returns a string representation of this sequential pattern (itemsets only).
	 * 
	 * @return string representation of the pattern
	 */
	public String toString() {
		StringBuilder r = new StringBuilder("");
		for (Itemset itemset : itemsets) {
			r.append('(');
			List<Integer> items = itemset.getItems();
			for (int i = 0; i < items.size(); i++) {
				r.append(items.get(i).toString());
				if (i < items.size() - 1) {
					r.append(' ');
				}
			}
			r.append(')');
		}
		return r.append("    ").toString();
	}

	/**
	 * Returns a string representation of this sequential pattern using braces for itemsets.
	 * 
	 * @return string representation of the itemsets
	 */
	public String itemsetsToString() {
		StringBuilder r = new StringBuilder("");
		for (Itemset itemset : itemsets) {
			r.append('{');
			for (Integer item : itemset.getItems()) {
				String string = item.toString();
				r.append(string);
				r.append(' ');
			}
			r.append('}');
		}
		return r.append("    ").toString();
	}

	/**
	 * Returns the list of itemsets in this pattern.
	 * 
	 * @return list of itemsets
	 */
	public List<Itemset> getItemsets() {
		return itemsets;
	}

	/**
	 * Returns the itemset at the given position.
	 * 
	 * @param index index of the itemset to retrieve
	 * @return itemset at the given index
	 */
	public Itemset get(int index) {
		return itemsets.get(index);
	}

	/**
	 * Returns the number of itemsets in this pattern.
	 * 
	 * @return number of itemsets
	 */
	public int size() {
		return itemsets.size();
	}

	/**
	 * Returns the list of sequence IDs that contain this pattern.
	 * 
	 * @return list of sequence IDs
	 */
	public List<Integer> getSequenceIDs() {
		return sequencesIds;
	}

	/**
	 * Compares this pattern to another pattern based on support and hash code.
	 * 
	 * @param o other sequential pattern to compare with
	 * @return a negative, zero, or positive integer as this pattern is less, equal, or greater
	 */
	@Override
	public int compareTo(SequentialPattern o) {
		if (o == this) {
			return 0;
		}
		int compare = this.getAbsoluteSupport() - o.getAbsoluteSupport();
		if (compare != 0) {
			return compare;
		}
		return this.hashCode() - o.hashCode();
	}

	/**
	 * Returns the current value of the {@code isFound} flag (parameter is ignored).
	 * 
	 * @param b unused flag value
	 * @return current value of {@code isFound}
	 */
	public boolean setIsFound(boolean b) {
		return isFound;
	}

	/**
	 * Returns whether this pattern is marked as found.
	 * 
	 * @return {@code true} if the pattern is marked as found
	 */
	public boolean isFound() {
		return isFound;
	}

	/**
	 * Adds an additional support count to this pattern.
	 * 
	 * @param additionalSupport additional support to add
	 */
	public void addAdditionalSupport(int additionalSupport) {
		this.additionalSupport += additionalSupport;
	}

}