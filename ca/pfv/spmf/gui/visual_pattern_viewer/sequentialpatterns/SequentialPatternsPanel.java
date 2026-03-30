package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

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
 * Panel that parses a file of sequential patterns and displays each pattern as
 * a {@link SequentialPatternPanel}. Supports sorting by support and exporting
 * the view as PNG.
 * @author Philippe Fournier-Viger
 */
public class SequentialPatternsPanel extends PatternsPanel {
	private static final long serialVersionUID = 1L;
	private static final int GAP_BETWEEN_PATTERNS = 10;

	/** Class to read the sequential patterns from a file */
	private final SequentialPatternsReader reader;

	/** All panels, each corresponding to a sequential pattern */
	private final List<SequentialPatternPanel> allPanels = new ArrayList<>();

	/** Filtered indices (optional), or null if no filter is applied */
	private Set<Integer> visiblePanelIndices = null;

	/** Precomputed min values for measures */
	private final Map<String, Double> minValues;
	
	/** Precomputed min ax values for measures */
	private final Map<String, Double> maxValues;

	/**
	 * Constructs the panel and builds child panels.
	 * 
	 * @param mode   the layout mode
	 * @param reader a SequentialPatternsReader instance
	 * @throws IOException if reading the file fails
	 */
	public SequentialPatternsPanel(SequentialPatternsReader reader, LayoutMode mode) throws IOException {
		super(mode);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Color.LIGHT_GRAY);
		this.reader = reader;
		this.minValues = reader.getAllMinValues();
		this.maxValues = reader.getAllMaxValues();
		buildPanels();
		rebuildPanels();
	}

	/**
	 * Builds all {@link SequentialPatternPanel}s once and stores them in memory.
	 */
	private void buildPanels() {
		List<SequentialPattern> patterns = reader.getPatterns();
		for (SequentialPattern pattern : patterns) {
			SequentialPatternPanel panel = new SequentialPatternPanel(pattern, minValues, maxValues);
			allPanels.add(panel);
		}
	}

	/**
	 * Clears and recreates layout by toggling visibility of prebuilt panels.
	 */
	@Override
	public void rebuildPanels() {
		removeAll();

		List<SequentialPatternPanel> panelsToDisplay = new ArrayList<>();
		for (int i = 0; i < allPanels.size(); i++) {
			if (visiblePanelIndices == null || visiblePanelIndices.contains(i)) {
				panelsToDisplay.add(allPanels.get(i));
			}
		}

		if (layoutMode == LayoutMode.VERTICAL) {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			for (SequentialPatternPanel panel : panelsToDisplay) {
				panel.setAlignmentX(Component.CENTER_ALIGNMENT);
				add(Box.createVerticalStrut(GAP_BETWEEN_PATTERNS));
				add(panel);
			}
			add(Box.createVerticalGlue());
		} else if (layoutMode == LayoutMode.HORIZONTAL) {
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			for (SequentialPatternPanel panel : panelsToDisplay) {
				panel.setAlignmentY(Component.CENTER_ALIGNMENT);
				add(Box.createHorizontalStrut(GAP_BETWEEN_PATTERNS));
				add(panel);
			}
			add(Box.createHorizontalGlue());
		} else if (layoutMode == LayoutMode.GRID) {
			int columns = 3; // You can make this dynamic later
			int rows = (int) Math.ceil((double) panelsToDisplay.size() / columns);
			setLayout(new java.awt.GridLayout(rows, columns, GAP_BETWEEN_PATTERNS, GAP_BETWEEN_PATTERNS));
			for (SequentialPatternPanel panel : panelsToDisplay) {
				add(panel);
			}
		}

		revalidate();
		repaint();
	}

	/**
	 * Applies a text search and measure-based filtering to the list of sequential
	 * patterns currently loaded in memory. Only patterns that match the search
	 * string (if provided) and satisfy all measure thresholds (with the specified
	 * operators) will be displayed.
	 * <p>
	 * The original list of patterns remains unchanged in memory; filtering is
	 * applied dynamically and temporarily to the view only.
	 * </p>
	 *
	 * @param searchString      a string to search for (case-insensitive) in the
	 *                          string representation of each pattern; may be null
	 *                          or empty to skip text search
	 * @param measureThresholds a map from measure names to threshold values;
	 *                          may be null or empty to skip numeric filtering
	 * @param measureOperators  a map from measure names to operator strings,
	 *                          either "≥" or "≤"; if an operator is missing or
	 *                          unrecognized, "≥" is assumed
	 */
	@Override
	public void applySearchAndFilters(String searchString, Map<String, Double> measureThresholds, Map<String, String> measureOperators) {
		visiblePanelIndices = new HashSet<>();
		for (int i = 0; i < allPanels.size(); i++) {
			SequentialPattern pattern = allPanels.get(i).getPattern();

			// Text filter with token matching
			if (searchString != null && !searchString.isBlank()) {
				String patternStr = pattern.toString().toLowerCase();
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
					Double value = Double.valueOf(pattern.getMeasures().get(measure));
					String operator = "≥";
					if (measureOperators != null && measureOperators.containsKey(measure)) {
						operator = measureOperators.get(measure);
					}
					if ("≤".equals(operator)) {
						// For "≤", fail if value is greater than threshold
						if (value == null || value > threshold) {
							failed = true;
							break;
						}
					} else {
						// Default or "≥": fail if value is less than threshold
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
	 * Clears any active text search or measure-based filtering. The view is reset
	 * to show all original sequential patterns loaded from the file.
	 */
	@Override
	public void clearSearchAndFilters() {
		visiblePanelIndices = null;
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
	 * Sort the patterns based on the user's choice and rebuild view.
	 * 
	 * @param choice the sorting order as a String
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
		return reader.getMinForMeasure(measure);
	}

	/**
	 * Get the maximum numeric value observed for the given measure
	 * 
	 * @return the maximum numeric value observed for the given measure (or null if
	 *         none)
	 */
	public Double getMaxForMeasureOriginal(String measure) {
		return reader.getMaxForMeasure(measure);
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
	 * Populates the provided map with measure values from all sequential patterns.
	 * Each measure name maps to a list of its numeric values across all patterns.
	 * 
	 * @param measureValuesMap the map to populate with measure values
	 */
	@Override
	public void populateMeasureValues(Map<String, List<Double>> measureValuesMap) {
		List<SequentialPattern> patterns = reader.getPatterns();
		for (SequentialPattern pattern : patterns) {
			Map<String, String> measures = pattern.getMeasures();
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
	 * Returns the sizes of all currently visible sequential patterns.
	 * The size is the number of itemsets (events) in the sequence.
	 * 
	 * @return a list of integers representing visible pattern sizes
	 */
	@Override
	public List<Integer> getVisiblePatternSizes() {
		List<Integer> sizes = new ArrayList<>();
		for (int i = 0; i < allPanels.size(); i++) {
			if (visiblePanelIndices == null || visiblePanelIndices.contains(i)) {
				SequentialPattern pattern = allPanels.get(i).getPattern();
				sizes.add(pattern.getItemsets().size());
			}
		}
		return sizes;
	}
	
}
