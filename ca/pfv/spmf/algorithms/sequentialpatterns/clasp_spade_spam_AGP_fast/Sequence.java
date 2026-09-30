package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.util.ArrayList;
import java.util.List;

/*
 * Copyright Antonio Gomariz Peñalver 2013
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */
/**
 * Implementation of a sequence. A sequence is defined as a list of itemsets.
 *
 * @author agomariz
 */
public class Sequence {

	/** The list of itemsets that compose this sequence, in temporal order. */
	private final List<Itemset> itemsets = new ArrayList<Itemset>();

	/** Constructs an empty sequence. */
	public Sequence() {
	}

	/**
	 * Adds an itemset at the end of this sequence.
	 * 
	 * @param itemset the itemset to add
	 */
	public void addItemset(Itemset itemset) {
		itemsets.add(itemset);
	}

	/**
	 * Adds an item to the itemset at the given index.
	 * 
	 * @param indexItemset the index of the itemset
	 * @param itemId       the item to add
	 */
	public void addItem(int indexItemset, int itemId) {
		itemsets.get(indexItemset).addItem(itemId);
	}

	/**
	 * Removes and returns the itemset at the given index.
	 * 
	 * @param indexItemset the index of the itemset to remove
	 * @return the removed itemset
	 */
	public Itemset remove(int indexItemset) {
		return itemsets.remove(indexItemset);
	}

	/**
	 * Removes and returns the item at the given position.
	 * 
	 * @param indexItemset the index of the itemset
	 * @param indexItem    the index of the item within the itemset
	 * @return the removed item id
	 */
	public int removeItem(int indexItemset, int indexItem) {
		return itemsets.get(indexItemset).removeItem(indexItem);
	}

	/**
	 * Creates a deep copy of this sequence.
	 * 
	 * @return the cloned sequence
	 */
	public Sequence cloneSequence() {
		Sequence sequence = new Sequence();
		for (Itemset itemset : itemsets) {
			sequence.addItemset(itemset.cloneItemSet());
		}
		return sequence;
	}

	/**
	 * Returns the string representation of this sequence.
	 * 
	 * @return the string representation
	 */
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		for (Itemset itemset : itemsets) {
			r.append('{');
			r.append(itemset.toString());
			r.append('}');
		}
		return r.toString();
	}

	/**
	 * Returns the list of itemsets of this sequence.
	 * 
	 * @return the list of itemsets
	 */
	public List<Itemset> getItemsets() {
		return itemsets;
	}

	/**
	 * Returns the itemset at the given index.
	 * 
	 * @param index the index
	 * @return the itemset
	 */
	public Itemset get(int index) {
		return itemsets.get(index);
	}

	/**
	 * Returns the number of itemsets in this sequence.
	 * 
	 * @return the number of itemsets
	 */
	public int size() {
		return itemsets.size();
	}
}