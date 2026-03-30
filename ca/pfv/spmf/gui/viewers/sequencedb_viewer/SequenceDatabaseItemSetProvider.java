package ca.pfv.spmf.gui.viewers.sequencedb_viewer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator.ItemSetProvider;
import ca.pfv.spmf.input.sequence_database_array_integers.Sequence;
import ca.pfv.spmf.input.sequence_database_array_integers.SequenceDatabase;

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
 * Adapter class that allows SequenceDatabase to be used with ItemCooccurrenceHeatmapGenerator.
 * Each sequence is flattened into a set of unique items across all its itemsets
 * for the purpose of computing item co-occurrence.
 * @author Philippe Fournier-Viger
 */
class SequenceDatabaseItemSetProvider implements ItemSetProvider {

    private final SequenceDatabase db;

    public SequenceDatabaseItemSetProvider(SequenceDatabase db) {
        this.db = db;
    }

    @Override
    public int getItemSetCount() {
        return db.size();
    }

    @Override
    public List<Integer> getItemSet(int index) {
        Sequence sequence = db.getSequences().get(index);
        // Collect all unique items across all itemsets in the sequence
        Set<Integer> items = new HashSet<Integer>();
        for (Integer[] itemset : sequence.getItemsets()) {
            for (int item : itemset) {
                items.add(item);
            }
        }
        return new ArrayList<Integer>(items);
    }

    @Override
    public String getItemName(int itemId) {
        return db.getNameForItem(itemId);
    }
}