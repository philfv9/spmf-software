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
package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;

import ca.pfv.spmf.gui.visual_pattern_viewer.LayoutMode;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternsPanel;

/**
 * Panel that parses a file of sequential patterns and displays each pattern as
 * a {@link SequentialPatternPanel}. Supports sorting by support and exporting
 * the view as PNG.
 * <p>
 * Supports optional pagination: call {@link #setPageSize(int)} to limit the
 * number of patterns shown per page, or pass {@code Integer.MAX_VALUE} to show
 * all patterns at once.
 *
 * @author Philippe Fournier-Viger
 */
public class SequentialPatternsPanel extends PatternsPanel {

	private static final long serialVersionUID = 1L;
	private static final int GAP_BETWEEN_PATTERNS = 10;

	/** Class to read the sequential patterns from a file */
	private final SequentialPatternsReader reader;

	/** All panels, each corresponding to a sequential pattern */
	private final List<SequentialPatternPanel> allPanels = new ArrayList<>();

	/**
	 * Ordered list of indices into {@link #allPanels} that pass the current
	 * search/filter. {@code null} means no filter is active (all panels visible).
	 */
	private List<Integer> visiblePanelIndices = null;

	/** Precomputed min values for measures */
	private final Map<String, Double> minValues;

	/** Precomputed max values for measures */
	private final Map<String, Double> maxValues;

	/**
	 * Constructs the panel and builds child panels.
	 *
	 * @param reader a SequentialPatternsReader instance
	 * @param mode   the layout mode
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
	 * Resolves the ordered list of indices that are currently visible (after
	 * filtering). If no filter is active this is simply 0 … allPanels.size()-1.
	 */
	private List<Integer> resolveVisibleIndices() {
		if (visiblePanelIndices != null) {
			return visiblePanelIndices;
		}
		List<Integer> all = new ArrayList<>(allPanels.size());
		for (int i = 0; i < allPanels.size(); i++) {
			all.add(i);
		}
		return all;
	}

	/**
	 * Clears and rebuilds the layout. Respects both the current filter state and
	 * pagination settings. Only the patterns that belong to the current page are
	 * added to the Swing hierarchy; the rest are not rendered, keeping the UI fast
	 * even with thousands of patterns.
	 */
	@Override
	public void rebuildPanels() {
		removeAll();

		List<Integer> visible = resolveVisibleIndices();
		int totalVisible = visible.size();
		int fromIdx;
		int toIdx;

		if (pageSize == Integer.MAX_VALUE || pageSize <= 0) {
			fromIdx = 0;
			toIdx = totalVisible;
		} else {
			int totalPages = Math.max(1, (int) Math.ceil((double) totalVisible / pageSize));
			if (currentPage >= totalPages) {
				currentPage = totalPages - 1;
			}
			fromIdx = currentPage * pageSize;
			toIdx = Math.min(fromIdx + pageSize, totalVisible);
		}

		List<SequentialPatternPanel> panelsToDisplay = new ArrayList<>();
		for (int i = fromIdx; i < toIdx; i++) {
			panelsToDisplay.add(allPanels.get(visible.get(i)));
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

		} else {
			int columns = 3;
			int rows = panelsToDisplay.isEmpty() ? 1
					: (int) Math.ceil((double) panelsToDisplay.size() / columns);
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
	 * applied dynamically and temporarily to the view only. Resets to page 0 after
	 * filtering so the user always sees the first page of results.
	 * </p>
	 *
	 * @param searchString      a string to search for (case-insensitive) in the
	 *                          string representation of each pattern; may be null
	 *                          or empty to skip text search
	 * @param measureThresholds a map from measure names to threshold values; may be
	 *                          null or empty to skip numeric filtering
	 * @param measureOperators  a map from measure names to operator strings, either
	 *                          "≥" or "≤"; if an operator is missing or
	 *                          unrecognized, "≥" is assumed
	 */
	@Override
	public void applySearchAndFilters(String searchString, Map<String, Double> measureThresholds,
	        Map<String, String> measureOperators) {

		List<Integer> matched = new ArrayList<>();

		for (int i = 0; i < allPanels.size(); i++) {
			SequentialPattern pattern = allPanels.get(i).getPattern();

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

			if (measureThresholds != null && !measureThresholds.isEmpty()) {
				boolean failed = false;
				for (Map.Entry<String, Double> entry : measureThresholds.entrySet()) {
					String measure = entry.getKey();
					Double threshold = entry.getValue();
					String rawValue = pattern.getMeasures().get(measure);
					if (rawValue == null) {
						failed = true;
						break;
					}
					double value;
					try {
						value = Double.parseDouble(rawValue);
					} catch (NumberFormatException e) {
						failed = true;
						break;
					}
					String operator = "\u2265";
					if (measureOperators != null && measureOperators.containsKey(measure)) {
						operator = measureOperators.get(measure);
					}
					if ("\u2264".equals(operator)) {
						if (value > threshold) {
							failed = true;
							break;
						}
					} else {
						if (value < threshold) {
							failed = true;
							break;
						}
					}
				}
				if (failed) {
					continue;
				}
			}

			matched.add(i);
		}

		visiblePanelIndices = matched;
		currentPage = 0;
		rebuildPanels();
	}

	/**
	 * Clears any active text search or measure-based filtering. The view is reset
	 * to show all original sequential patterns loaded from the file, starting at
	 * page 0.
	 */
	@Override
	public void clearSearchAndFilters() {
		visiblePanelIndices = null;
		currentPage = 0;
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
	 * Sort the patterns based on the user's choice and rebuild the view. Resets to
	 * page 0 after sorting so the user always sees the top of the newly ordered
	 * list.
	 *
	 * @param choice the sorting order as a String
	 */
	@Override
	public void sortPatterns(String choice) {
		reader.sortPatterns(choice);
		allPanels.clear();
		buildPanels();
		currentPage = 0;
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
	 * Get the number of visible patterns (after filtering, across ALL pages).
	 *
	 * @return the number of visible patterns
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
	 * Returns the sizes of all currently visible sequential patterns (across ALL
	 * pages, not just the current page). The size is the number of itemsets
	 * (events) in the sequence.
	 *
	 * @return a list of integers representing visible pattern sizes
	 */
	@Override
	public List<Integer> getVisiblePatternSizes() {
		List<Integer> sizes = new ArrayList<>();
		List<Integer> visible = resolveVisibleIndices();
		for (int idx : visible) {
			SequentialPattern pattern = allPanels.get(idx).getPattern();
			sizes.add(pattern.getItemsets().size());
		}
		return sizes;
	}
}