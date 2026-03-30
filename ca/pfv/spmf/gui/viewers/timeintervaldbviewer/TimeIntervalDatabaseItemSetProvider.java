package ca.pfv.spmf.gui.viewers.timeintervaldbviewer;

import java.util.ArrayList;
import java.util.List;

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
 * Adapter class that allows TimeIntervalDatabase to be used with ItemCooccurrenceHeatmapGenerator.
 * Each sequence's symbolic time intervals are converted to a list of item IDs (symbols).
 * 
 * @author Philippe Fournier-Viger
 */
class TimeIntervalDatabaseItemSetProvider implements ItemSetProvider {

    /** The time interval database */
    private final TimeIntervalDatabase db;

    /**
     * Constructor
     * 
     * @param db the time interval database
     */
    public TimeIntervalDatabaseItemSetProvider(TimeIntervalDatabase db) {
        this.db = db;
    }

    @Override
    public int getItemSetCount() {
        return db.size();
    }

    @Override
    public List<Integer> getItemSet(int index) {
        TimeIntervalSequence sequence = db.getSequences().get(index);
        List<Integer> items = new ArrayList<Integer>();
        for (SymbolicTimeInterval interval : sequence.getTimeIntervals()) {
            items.add(interval.symbol);
        }
        return items;
    }

    @Override
    public String getItemName(int itemId) {
        return db.getNameForItem(itemId);
    }
}