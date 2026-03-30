package ca.pfv.spmf.gui.viewers;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import ca.pfv.spmf.gui.visuals.heatmap.HeatMapViewer;
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
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * A reusable utility class for generating item co-occurrence heatmaps.
 * This class can be used by different dataset viewers (transaction databases,
 * sequence databases, etc.) to display co-occurrence patterns.
 * 
 * @author Philippe Fournier-Viger, 2025
 */
public class ItemCooccurrenceHeatmapGenerator {

    /**
     * Interface for providing item sets to the heatmap generator.
     * Implement this interface to adapt different dataset types.
     */
    public interface ItemSetProvider {
        /**
         * Get the number of item sets (e.g., transactions, sequences)
         * @return the count
         */
        int getItemSetCount();
        
        /**
         * Get the items in a specific item set
         * @param index the index of the item set
         * @return list of item IDs
         */
        List<Integer> getItemSet(int index);
        
        /**
         * Get the display name for an item
         * @param itemId the item ID
         * @return the display name, or null if no custom name
         */
        String getItemName(int itemId);
    }

    /**
     * Result class containing the computed heatmap data
     */
    public static class HeatmapData {
        private final double[][] cooccurrenceMatrix;
        private final String[] itemNames;
        private final List<Integer> topItems;

        public HeatmapData(double[][] cooccurrenceMatrix, String[] itemNames, List<Integer> topItems) {
            this.cooccurrenceMatrix = cooccurrenceMatrix;
            this.itemNames = itemNames;
            this.topItems = topItems;
        }

        public double[][] getCooccurrenceMatrix() {
            return cooccurrenceMatrix;
        }

        public String[] getItemNames() {
            return itemNames;
        }

        public List<Integer> getTopItems() {
            return topItems;
        }
    }

    /**
     * Show a dialog to get the number of top items and generate the heatmap
     * 
     * @param parentComponent the parent component for dialogs
     * @param provider the item set provider
     * @param runAsStandalone whether to run the heatmap viewer as standalone
     * @return true if heatmap was displayed, false if cancelled or error
     */
    public static boolean showCooccurrenceHeatmapDialog(Component parentComponent, 
            ItemSetProvider provider, boolean runAsStandalone) {
        
        if (provider == null || provider.getItemSetCount() == 0) {
            JOptionPane.showMessageDialog(parentComponent, 
                    "Please load a database first.", "No Database",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // Ask the user for the number of top items to display
        String input = JOptionPane.showInputDialog(parentComponent,
                "Enter the number of most frequent items to include in the heatmap:",
                "Item Co-occurrence Heatmap", JOptionPane.QUESTION_MESSAGE);

        if (input == null || input.trim().isEmpty()) {
            return false; // User cancelled
        }

        int topK;
        try {
            topK = Integer.parseInt(input.trim());
            if (topK <= 0) {
                JOptionPane.showMessageDialog(parentComponent, 
                        "Please enter a positive number.", "Invalid Input",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parentComponent, 
                    "Please enter a valid integer.", "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Generate the heatmap data
        HeatmapData data = generateHeatmapData(provider, topK);
        
        if (data == null) {
            JOptionPane.showMessageDialog(parentComponent, 
                    "No items found in the database.", "No Data",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // Check if we have fewer items than requested
        if (data.getTopItems().size() < topK) {
            JOptionPane.showMessageDialog(parentComponent,
                    "Only " + data.getTopItems().size() + " items available. Showing all items.",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
        }

        // Create and display the heatmap
        new HeatMapViewer(
                runAsStandalone,
                data.getCooccurrenceMatrix(),
                data.getItemNames(),
                data.getItemNames(),
                true,  // drawColumnLabels
                true,  // drawRowLabels
                true,  // drawColorScale
                true   // drawColumnLabelsVertically
        );

        return true;
    }

    /**
     * Generate co-occurrence heatmap data for the top K most frequent items
     * 
     * @param provider the item set provider
     * @param topK the number of top items to include
     * @return the heatmap data, or null if no items found
     */
    public static HeatmapData generateHeatmapData(ItemSetProvider provider, int topK) {
        if (provider == null || provider.getItemSetCount() == 0) {
            return null;
        }

        // Calculate item frequencies
        Map<Integer, Integer> itemFrequencies = calculateItemFrequencies(provider);
        
        if (itemFrequencies.isEmpty()) {
            return null;
        }

        // Sort items by frequency (descending) and get top K
        List<Integer> topItems = getTopKItems(itemFrequencies, topK);
        
        if (topItems.isEmpty()) {
            return null;
        }

        int actualK = topItems.size();

        // Create a mapping from item ID to index in the matrix
        Map<Integer, Integer> itemToIndex = new HashMap<Integer, Integer>();
        for (int i = 0; i < topItems.size(); i++) {
            itemToIndex.put(topItems.get(i), i);
        }

        // Calculate co-occurrence matrix
        double[][] cooccurrenceMatrix = calculateCooccurrenceMatrix(provider, topItems, itemToIndex);

        // Create item names
        String[] itemNames = createItemNames(provider, topItems);

        return new HeatmapData(cooccurrenceMatrix, itemNames, topItems);
    }

    /**
     * Calculate the frequency of each item across all item sets
     * 
     * @param provider the item set provider
     * @return map of item ID to frequency count
     */
    public static Map<Integer, Integer> calculateItemFrequencies(ItemSetProvider provider) {
        Map<Integer, Integer> itemFrequencies = new HashMap<Integer, Integer>();
        
        for (int i = 0; i < provider.getItemSetCount(); i++) {
            List<Integer> itemSet = provider.getItemSet(i);
            for (Integer item : itemSet) {
                Integer count = itemFrequencies.get(item);
                if (count == null) {
                    itemFrequencies.put(item, 1);
                } else {
                    itemFrequencies.put(item, count + 1);
                }
            }
        }
        
        return itemFrequencies;
    }

    /**
     * Get the top K most frequent items
     * 
     * @param itemFrequencies map of item frequencies
     * @param topK the number of top items to return
     * @return list of top K item IDs sorted by frequency (descending)
     */
    public static List<Integer> getTopKItems(Map<Integer, Integer> itemFrequencies, int topK) {
        // Sort items by frequency (descending)
        List<Map.Entry<Integer, Integer>> sortedItems = 
                new ArrayList<Map.Entry<Integer, Integer>>(itemFrequencies.entrySet());
        
        Collections.sort(sortedItems, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> e1, Map.Entry<Integer, Integer> e2) {
                return e2.getValue().compareTo(e1.getValue());
            }
        });

        // Get the top K items
        int actualK = Math.min(topK, sortedItems.size());
        List<Integer> topItems = new ArrayList<Integer>();
        for (int i = 0; i < actualK; i++) {
            topItems.add(sortedItems.get(i).getKey());
        }
        
        return topItems;
    }

    /**
     * Calculate the co-occurrence matrix for the given items
     * 
     * @param provider the item set provider
     * @param topItems the list of items to include
     * @param itemToIndex mapping from item ID to matrix index
     * @return the co-occurrence matrix
     */
    public static double[][] calculateCooccurrenceMatrix(ItemSetProvider provider, 
            List<Integer> topItems, Map<Integer, Integer> itemToIndex) {
        
        int size = topItems.size();
        double[][] cooccurrenceMatrix = new double[size][size];

        for (int i = 0; i < provider.getItemSetCount(); i++) {
            List<Integer> itemSet = provider.getItemSet(i);
            
            // Get items in this item set that are in our top K
            List<Integer> relevantItems = new ArrayList<Integer>();
            for (Integer item : itemSet) {
                if (itemToIndex.containsKey(item)) {
                    relevantItems.add(item);
                }
            }

            // Count co-occurrences (including self-occurrences on diagonal)
            for (int j = 0; j < relevantItems.size(); j++) {
                int item1 = relevantItems.get(j);
                int idx1 = itemToIndex.get(item1);

                // Count self-occurrence (diagonal)
                cooccurrenceMatrix[idx1][idx1]++;

                // Count co-occurrences with other items
                for (int k = j + 1; k < relevantItems.size(); k++) {
                    int item2 = relevantItems.get(k);
                    int idx2 = itemToIndex.get(item2);

                    // Increment both symmetric positions
                    cooccurrenceMatrix[idx1][idx2]++;
                    cooccurrenceMatrix[idx2][idx1]++;
                }
            }
        }
        
        return cooccurrenceMatrix;
    }

    /**
     * Create display names for the items
     * 
     * @param provider the item set provider
     * @param topItems the list of items
     * @return array of display names
     */
    public static String[] createItemNames(ItemSetProvider provider, List<Integer> topItems) {
        String[] itemNames = new String[topItems.size()];
        
        for (int i = 0; i < topItems.size(); i++) {
            int itemId = topItems.get(i);
            String name = provider.getItemName(itemId);
            if (name != null) {
                itemNames[i] = name + " (" + itemId + ")";
            } else {
                itemNames[i] = "Item " + itemId;
            }
        }
        
        return itemNames;
    }
}