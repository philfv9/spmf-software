package ca.pfv.spmf.gui.visual_pattern_viewer.itemsets;

import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;

import ca.pfv.spmf.gui.visual_pattern_viewer.LayoutMode;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternsPanel;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternsReader;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Panel that parses a file of itemsets (including extended SPMF format with item
 * mappings) and displays each rule as a {@link ItemsetPanel}. Provides sorting
 * by measure or lexicographically, and the ability to export the entire view as
 * a PNG.
 * 
 * @author Philippe Fournier-Viger
 */
public class ItemsetsPanel extends PatternsPanel {
	/** serial UID **/
	private static final long serialVersionUID = 123982348l;

	/** Vertical gap between panels in pixels */
	private static final int GAP_BETWEEN_PATTERNS = 10;

	/** Class to read the rules from a file */
	ItemsetsReader reader = null;

	/** All panels, each corresponding to a pattern */
	private final List<ItemsetPanel> allPanels = new ArrayList<>();

	/** Filtered indices (optional), or null if no filter is applied */
	private Set<Integer> visiblePanelIndices = null;

	/** Precomputed min values for measures */
	private final Map<String, Double> minValues;

	/** Precomputed max values for measures */
	private final Map<String, Double> maxValues;

	/**
	 * Creates a panel, parses the given file for rules or transactions, and builds
	 * child panels. Supports both standard and extended SPMF formats.
	 *
	 * @param reader a reader that has already parsed the file
	 * @param mode   the layout mode
	 * @throws IOException if reading the file fails
	 */
	public ItemsetsPanel(PatternsReader reader, LayoutMode mode) throws IOException {
		super(mode);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Color.LIGHT_GRAY);
		this.reader = (ItemsetsReader) reader;
		this.minValues = reader.getAllMinValues();
		this.maxValues = reader.getAllMaxValues();
		buildPanels();
		rebuildPanels();
	}

	/**
	 * Sorts rules numerically by the given measure, then rebuilds the display.
	 *
	 * @param measure   key of the measure to sort by
	 * @param ascending true for ascending order; false for descending
	 */
	public void sortItemsetsByMeasure(String measure, boolean ascending) {
		reader.sortPatternsByMeasure(measure, ascending);
		allPanels.clear();
		buildPanels();
		rebuildPanels();
	}

	/**
	 * @return an ordered list of all sorting orders offered
	 */
	@Override
	public List<String> getListOfSortingOrders() {
		return reader.getListOfSortingOrders();
	}

	/**
	 * Sort the rules based on the the user's choice
	 * 
	 * @param choice the choice as a String
	 */
	@Override
	public void sortPatterns(String choice) {
		reader.sortPatterns(choice);
		allPanels.clear();
		buildPanels();
		rebuildPanels();
	}

	@Override
	public int getTotalPatternCount() {
		return reader.getPatterns().size();
	}

	/**
	 * Get the list of available measures
	 * 
	 * @return a Set
	 */
	public Set<String> getAllMeasures() {
		return reader.getAllMeasures();
	}

	/**
	 * Get the minimum numeric value observed for the given measure
	 * 
	 * @return the minimum numeric value observed for the given measure (or null if
	 *         none)
	 */
	public Double getMinForMeasureOriginal(String measure) {
		return reader.getMinForMeasureOriginal(measure);
	}

	/**
	 * Get the maximum numeric value observed for the given measure
	 * 
	 * @return the maximum numeric value observed for the given measure (or null if
	 *         none)
	 */
	public Double getMaxForMeasureOriginal(String measure) {
		return reader.getMaxForMeasureOriginal(measure);
	}

	/**
	 * Builds all {@link ItemsetPanel}s once and stores them in memory.
	 */
	private void buildPanels() {
		List<Itemset> patterns = reader.getPatterns();
		for (Itemset rule : patterns) {
			ItemsetPanel panel = new ItemsetPanel(rule, minValues, maxValues);
			allPanels.add(panel);
		}
	}

	/**
	 * Clears and rebuilds the layout. Visibility of panels is controlled by
	 * filtering state; only visible panels are re-added to the layout.
	 */
	@Override
	public void rebuildPanels() {
		removeAll();

		List<ItemsetPanel> panelsToDisplay = new ArrayList<>();
		for (int i = 0; i < allPanels.size(); i++) {
			if (visiblePanelIndices == null || visiblePanelIndices.contains(i)) {
				panelsToDisplay.add(allPanels.get(i));
			}
		}

		if (layoutMode == LayoutMode.VERTICAL) {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			for (ItemsetPanel panel : panelsToDisplay) {
				panel.setAlignmentX(Component.CENTER_ALIGNMENT);
				add(Box.createVerticalStrut(GAP_BETWEEN_PATTERNS));
				add(panel);
			}
			add(Box.createVerticalGlue());
		} else if (layoutMode == LayoutMode.HORIZONTAL) {
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			for (ItemsetPanel panel : panelsToDisplay) {
				panel.setAlignmentY(Component.CENTER_ALIGNMENT);
				add(Box.createHorizontalStrut(GAP_BETWEEN_PATTERNS));
				add(panel);
			}
			add(Box.createHorizontalGlue());
		} else if (layoutMode == LayoutMode.GRID) {
			int columns = 3;
			int rows = (int) Math.ceil((double) panelsToDisplay.size() / columns);
			setLayout(new java.awt.GridLayout(rows, columns, GAP_BETWEEN_PATTERNS, GAP_BETWEEN_PATTERNS));
			for (ItemsetPanel panel : panelsToDisplay) {
				add(panel);
			}
		}

		revalidate();
		repaint();
	}

	/**
	 * Applies a text search and measure-based filtering to the list of itemsets
	 * currently loaded in memory.
	 */
	@Override
	public void applySearchAndFilters(String searchString, Map<String, Double> measureThresholds,
			Map<String, String> measureOperators) {
		visiblePanelIndices = new HashSet<>();
		for (int i = 0; i < allPanels.size(); i++) {
			Itemset itemset = allPanels.get(i).getItemset();

			// Text filter with token matching
			if (searchString != null && !searchString.isBlank()) {
				String patternStr = itemset.toString().toLowerCase();
				String[] tokens = searchString.toLowerCase().split("\\s+");
				boolean allTokensMatch = true;
				for (String token : tokens) {
					if (!patternStr.contains(token)) {
						allTokensMatch = false;
						break;
					}
				}
				if (!allTokensMatch) {
					continue;
				}
			}

			// Measure filters with operator support
			if (measureThresholds != null && !measureThresholds.isEmpty()) {
				boolean failed = false;
				for (Map.Entry<String, Double> entry : measureThresholds.entrySet()) {
					String measure = entry.getKey();
					Double threshold = entry.getValue();
					Double value = Double.valueOf(itemset.getMeasures().get(measure));
					String operator = "\u2265";
					if (measureOperators != null && measureOperators.containsKey(measure)) {
						operator = measureOperators.get(measure);
					}
					if ("\u2264".equals(operator)) {
						if (value == null || value > threshold) {
							failed = true;
							break;
						}
					} else {
						if (value == null || value < threshold) {
							failed = true;
							break;
						}
					}
				}
				if (failed) {
					continue;
				}
			}

			visiblePanelIndices.add(i);
		}

		rebuildPanels();
	}

	/**
	 * Clears any active text search or measure-based filtering.
	 */
	@Override
	public void clearSearchAndFilters() {
		visiblePanelIndices = null;
		rebuildPanels();
	}

	/**
	 * Get the number of visible patterns
	 * 
	 * @return the number of visible patterns (after filtering)
	 */
	@Override
	public int getNumberOfVisiblePatterns() {
		if (visiblePanelIndices == null) {
			return allPanels.size();
		}
		return visiblePanelIndices.size();
	}

	/**
	 * Populates the provided map with measure values from all itemsets.
	 */
	@Override
	public void populateMeasureValues(Map<String, List<Double>> measureValuesMap) {
		List<Itemset> patterns = reader.getPatterns();
		for (Itemset itemset : patterns) {
			Map<String, String> measures = itemset.getMeasures();
			for (Map.Entry<String, String> entry : measures.entrySet()) {
				String measureName = entry.getKey();
				String valueStr = entry.getValue();
				try {
					double value = Double.parseDouble(valueStr);
					List<Double> valuesList = measureValuesMap.get(measureName);
					if (valuesList != null) {
						valuesList.add(value);
					}
				} catch (NumberFormatException e) {
					// Skip non-numeric values
				}
			}
		}
	}

	/**
	 * Returns the sizes of all currently visible itemsets. The size of an itemset is
	 * the number of items it contains.
	 * 
	 * @return a list of integers, each representing the number of items in a
	 *         visible itemset
	 */
	@Override
	public List<Integer> getVisiblePatternSizes() {
		List<Integer> sizes = new ArrayList<>();
		for (int i = 0; i < allPanels.size(); i++) {
			if (visiblePanelIndices == null || visiblePanelIndices.contains(i)) {
				sizes.add(allPanels.get(i).getItemset().getItems().size());
			}
		}
		return sizes;
	}
}