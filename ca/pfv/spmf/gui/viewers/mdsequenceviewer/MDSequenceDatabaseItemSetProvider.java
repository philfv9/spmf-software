package ca.pfv.spmf.gui.viewers.mdsequenceviewer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.ItemSimple;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Itemset;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequenceDatabase;
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
 * Adapter class that allows MDSequenceDatabase to be used with ItemCooccurrenceHeatmapGenerator.
 * Each MD-sequence is treated as a single item set containing all unique items
 * across all itemsets in that sequence.
 * @author Philippe Fournier-Viger
 */
class MDSequenceDatabaseItemSetProvider implements ItemSetProvider {
    
    /** The MD sequence database */
    private final MDSequenceDatabase db;
    
    /**
     * Constructor
     * @param db the MD sequence database
     */
    public MDSequenceDatabaseItemSetProvider(MDSequenceDatabase db) {
        this.db = db;
    }
    
    @Override
    public int getItemSetCount() {
        return db.size();
    }
    
    @Override
    public List<Integer> getItemSet(int index) {
        MDSequence mdsequence = db.getSequences().get(index);
        // Collect all unique items across all itemsets in this sequence
        Set<Integer> items = new HashSet<Integer>();
        for (Itemset itemset : mdsequence.getSequence().getItemsets()) {
            for (ItemSimple item : itemset.getItems()) {
                items.add(item.getId());
            }
        }
        return new ArrayList<Integer>(items);
    }
    
    @Override
    public String getItemName(int itemId) {
        // MD sequence databases do not have item name mappings
        return null;
    }
}