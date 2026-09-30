package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.util.ArrayList;
import java.util.List;
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
 * Represents an item together with its projected database for use in PrefixSpan.
 * 
 * Copyright (c) 2008-2012 Philippe Fournier-Viger
 * 
 * @author Philipe-Fournier-Viger
 */
public class Pair {
	/** Item represented by this pair. */
	protected final int item;

	/** List of pseudo-sequences in the projection for this item. */
	private List<PseudoSequence> pseudoSequences = new ArrayList<PseudoSequence>();

	/**
	 * Creates a new pair for the given item.
	 * 
	 * @param item the item represented by this pair
	 */
	public Pair(Integer item) {
		this.item = item;
	}

	/**
	 * Compares this pair to another object for equality based on the item.
	 * 
	 * @param object the object to compare with
	 * @return {@code true} if the other object is a {@link Pair} with the same item
	 */
	public boolean equals(Object object) {
		Pair pair = (Pair) object;
		return pair.item == this.item;
	}

	/**
	 * Computes a hash code for this pair based on its item.
	 * 
	 * @return the hash code
	 */
	public int hashCode() {
		return (item + "").hashCode();
	}

	/**
	 * Returns the item represented by this pair.
	 * 
	 * @return the item
	 */
	public int getItem() {
		return item;
	}

	/**
	 * Returns the support of this item, i.e. the number of pseudo-sequences.
	 * 
	 * @return the support count
	 */
	public int getCount() {
		return pseudoSequences.size();
	}

	/**
	 * Returns the list of pseudo-sequences in the projection for this item.
	 * 
	 * @return list of pseudo-sequences
	 */
	public List<PseudoSequence> getPseudoSequences() {
		return pseudoSequences;
	}
}