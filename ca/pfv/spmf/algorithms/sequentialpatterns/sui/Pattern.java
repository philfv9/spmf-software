package ca.pfv.spmf.algorithms.sequentialpatterns.sui;

import java.util.ArrayList;
import java.util.List;

/* This file is part of the SPMF DATA MINING SOFTWARE
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
 *
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Represents a sequential pattern as a list of itemsets.
 *
 * @author Philippe Fournier-Viger
 */
public class Pattern {

	/** Ordered list of itemsets that form this sequential pattern. */
	private final List<List<Integer>> itemsets = new ArrayList<>(4);

	/** True if this pattern was created by an i-extension of its prefix. */
	private boolean iExtension;

	/**
	 * Returns the list of itemsets of this pattern.
	 *
	 * @return the ordered list of itemsets
	 */
	public List<List<Integer>> getItemsets() {
		return itemsets;
	}

	/**
	 * Returns whether this pattern was produced by an i-extension.
	 *
	 * @return true if this pattern is an i-extension of its prefix
	 */
	public boolean isIExtension() {
		return iExtension;
	}

	/**
	 * Sets whether this pattern was produced by an i-extension.
	 *
	 * @param v true if this pattern is an i-extension of its prefix
	 */
	public void setIExtension(boolean v) {
		this.iExtension = v;
	}

	/**
	 * Returns the last item of the last itemset of this pattern.
	 *
	 * @return the last item value
	 */
	public int getLastItem() {
		List<Integer> last = itemsets.get(itemsets.size() - 1);
		return last.get(last.size() - 1);
	}

	/**
	 * Creates a new pattern by s-extending this pattern with a new item, adding a
	 * new single-item itemset at the end.
	 *
	 * @param newItem the item to append as a new itemset
	 * @return the s-extended pattern
	 */
	public Pattern sExtend(int newItem) {
		Pattern z = new Pattern();
		// Shallow copy: existing inner lists are never mutated after creation
		for (List<Integer> is : itemsets) {
			z.itemsets.add(is);
		}
		List<Integer> newItemset = new ArrayList<>(1);
		newItemset.add(newItem);
		z.itemsets.add(newItemset);
		z.iExtension = false;
		return z;
	}

	/**
	 * Creates a new pattern by i-extending this pattern with a new item, appending
	 * the item to the last itemset.
	 *
	 * @param newItem the item to append to the last itemset
	 * @return the i-extended pattern
	 */
	public Pattern iExtend(int newItem) {
		Pattern z = new Pattern();
		int size = itemsets.size();
		// Shallow copy all but the last itemset
		for (int i = 0; i < size - 1; i++) {
			z.itemsets.add(itemsets.get(i));
		}
		// Deep copy the last itemset and append newItem
		List<Integer> lastOld = itemsets.get(size - 1);
		List<Integer> lastNew = new ArrayList<>(lastOld.size() + 1);
		lastNew.addAll(lastOld);
		lastNew.add(newItem);
		z.itemsets.add(lastNew);
		z.iExtension = true;
		return z;
	}

	/**
	 * Appends the SPMF-format representation of this pattern to a StringBuilder.
	 *
	 * @param sb the StringBuilder to append to
	 */
	public void appendTo(StringBuilder sb) {
		for (int i = 0; i < itemsets.size(); i++) {
			List<Integer> itemset = itemsets.get(i);
			for (int j = 0; j < itemset.size(); j++) {
				if (i > 0 || j > 0)
					sb.append(' ');
				sb.append((int) itemset.get(j));
			}
			sb.append(" -1");
		}
	}

	/**
	 * Returns the SPMF-format string representation of this pattern.
	 *
	 * @return the string representation
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(32);
		appendTo(sb);
		return sb.toString();
	}
}