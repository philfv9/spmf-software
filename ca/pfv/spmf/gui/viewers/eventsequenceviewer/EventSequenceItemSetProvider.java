package ca.pfv.spmf.gui.viewers.eventsequenceviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Adapter class that allows EventSequence to be used with ItemCooccurrenceHeatmapGenerator.
 * Groups events by timestamp to form item sets for co-occurrence analysis.
 * @author Philippe Fournier-Viger
 */
class EventSequenceItemSetProvider implements ItemSetProvider {

    /** The list of item sets grouped by timestamp */
    private final List<List<Integer>> itemSets;
    /** The event sequence used for item name lookups */
    private final EventSequence es;

    /**
     * Constructor
     * @param es the event sequence
     */
    public EventSequenceItemSetProvider(EventSequence es) {
        this.es = es;
        this.itemSets = new ArrayList<List<Integer>>();

        // Group events by timestamp to form item sets
        Map<Long, List<Integer>> timestampToItems = new HashMap<Long, List<Integer>>();
        for (int i = 0; i < es.size(); i++) {
            Event event = es.get(i);
            long timestamp = event.getTimestamp();
            List<Integer> items = timestampToItems.get(timestamp);
            if (items == null) {
                items = new ArrayList<Integer>();
                timestampToItems.put(timestamp, items);
            }
            int item = event.getItem();
            if (!items.contains(item)) {
                items.add(item);
            }
        }

        // Collect all item sets
        for (List<Integer> items : timestampToItems.values()) {
            itemSets.add(items);
        }
    }

    @Override
    public int getItemSetCount() {
        return itemSets.size();
    }

    @Override
    public List<Integer> getItemSet(int index) {
        return itemSets.get(index);
    }

    @Override
    public String getItemName(int itemId) {
        return es.getNameForItem(itemId);
    }
}