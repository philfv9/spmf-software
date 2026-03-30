package ca.pfv.spmf.gui.viewers.sequencedb_cost_utility_viewer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequential_rules.husrm.SequenceDatabaseWithUtility;
import ca.pfv.spmf.algorithms.sequential_rules.husrm.SequenceWithUtility;
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
 * Adapter class that allows SequenceDatabaseWithUtility to be used with
 * ItemCooccurrenceHeatmapGenerator. Each sequence's items (across all itemsets)
 * are flattened into a single list of unique items for co-occurrence analysis.
 * 
 * @author Philippe Fournier-Viger
 */
class SequenceCostUtilityDatabaseItemSetProvider implements ItemSetProvider {

	/** The sequence database with utility */
	private final SequenceDatabaseWithUtility db;

	/**
	 * Constructor
	 * 
	 * @param db the sequence database with utility
	 */
	public SequenceCostUtilityDatabaseItemSetProvider(SequenceDatabaseWithUtility db) {
		this.db = db;
	}

	@Override
	public int getItemSetCount() {
		return db.size();
	}

	@Override
	public List<Integer> getItemSet(int index) {
		SequenceWithUtility sequence = db.getSequences().get(index);
		// Flatten all itemsets in the sequence into a single list of unique items
		Set<Integer> uniqueItems = new HashSet<Integer>();
		for (List<Integer> itemset : sequence.getItemsets()) {
			for (Integer item : itemset) {
				uniqueItems.add(item);
			}
		}
		return new ArrayList<Integer>(uniqueItems);
	}

	@Override
	public String getItemName(int itemId) {
		// SequenceDatabaseWithUtility does not have item name mapping
		return null;
	}
}