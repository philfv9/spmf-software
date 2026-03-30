package ca.pfv.spmf.gui.viewers.timesequencedbviewer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.ItemSimple;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Itemset;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Sequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.SequenceDatabase;
import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator.ItemSetProvider;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */
/**
 * Adapter class that allows SequenceDatabase to be used with
 * ItemCooccurrenceHeatmapGenerator. Each sequence is flattened into a single
 * set of distinct items so that co-occurrence is computed per sequence.
 * 
 * @author Philippe Fournier-Viger
 */
class SequenceDatabaseItemSetProvider implements ItemSetProvider {

	/** The sequence database */
	private final SequenceDatabase db;

	/**
	 * Constructor
	 * 
	 * @param db the sequence database
	 */
	public SequenceDatabaseItemSetProvider(SequenceDatabase db) {
		this.db = db;
	}

	/**
	 * Get the number of item sets (sequences) in the database
	 * 
	 * @return the number of sequences
	 */
	@Override
	public int getItemSetCount() {
		return db.size();
	}

	/**
	 * Get the item set (flattened distinct items) for a given sequence index.
	 * All items across all itemsets in the sequence are collected into a single
	 * list of distinct item IDs.
	 * 
	 * @param index the sequence index
	 * @return a list of distinct item IDs appearing in the sequence
	 */
	@Override
	public List<Integer> getItemSet(int index) {
		Sequence sequence = db.getSequences().get(index);
		Set<Integer> distinctItems = new HashSet<Integer>();
		for (Itemset itemset : sequence.getItemsets()) {
			for (ItemSimple item : itemset.getItems()) {
				distinctItems.add(item.getId());
			}
		}
		return new ArrayList<Integer>(distinctItems);
	}

	/**
	 * Get the name for an item. This database type does not support item names,
	 * so null is returned.
	 * 
	 * @param itemId the item ID
	 * @return null (no item names available)
	 */
	@Override
	public String getItemName(int itemId) {
		return null;
	}
}