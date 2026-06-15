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
 * Supports optional pagination: call {@link #setPageSize(int)} to limit the
 * number of patterns shown per page, or pass {@code Integer.MAX_VALUE} to show
 * all patterns at once.
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
		// Reset to first page after sorting so the user sees the top results
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
	 * Resolves the ordered list of indices that are currently visible (after
	 * filtering). If no filter is active this is simply 0 … allPanels.size()-1.
	 */
	private List<Integer> resolveVisibleIndices() {
		if (visiblePanelIndices != null) {
			// Already filtered – return the stored ordered list
			return visiblePanelIndices;
		}
		// No filter: all panels in order
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

		// Full ordered list of visible indices (may be all of them)
		List<Integer> visible = resolveVisibleIndices();

		// --- Pagination: compute the window [fromIdx, toIdx) ---
		int totalVisible = visible.size();
		int fromIdx; // inclusive
		int toIdx;   // exclusive

		if (pageSize == Integer.MAX_VALUE || pageSize <= 0) {
			// Show-all mode
			fromIdx = 0;
			toIdx = totalVisible;
		} else {
			// Clamp currentPage just in case
			int totalPages = Math.max(1, (int) Math.ceil((double) totalVisible / pageSize));
			if (currentPage >= totalPages) {
				currentPage = totalPages - 1;
			}
			fromIdx = currentPage * pageSize;
			toIdx = Math.min(fromIdx + pageSize, totalVisible);
		}

		// Collect the panels for this page
		List<ItemsetPanel> panelsToDisplay = new ArrayList<>();
		for (int i = fromIdx; i < toIdx; i++) {
			panelsToDisplay.add(allPanels.get(visible.get(i)));
		}

		// --- Build layout ---
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
		} else {
			// GRID (default)
			int columns = 3;
			int rows = panelsToDisplay.isEmpty() ? 1
					: (int) Math.ceil((double) panelsToDisplay.size() / columns);
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
	 * currently loaded in memory. Resets to page 0 after filtering so the user
	 * always sees the first page of results.
	 */
	@Override
	public void applySearchAndFilters(String searchString, Map<String, Double> measureThresholds,
	        Map<String, String> measureOperators) {

	    List<Integer> matched = new ArrayList<>();

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
	                String rawValue = itemset.getMeasures().get(measure);
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
	                
	                // Apply the filter based on operator
	                if ("\u2264".equals(operator)) {
	                    // For ≤: exclude if value is GREATER than threshold
	                    if (value > threshold) {
	                        failed = true;
	                        break;
	                    }
	                } else {
	                    // For ≥: exclude if value is LESS than threshold
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
	 * Clears any active text search or measure-based filtering and returns to page
	 * 0.
	 */
	@Override
	public void clearSearchAndFilters() {
		visiblePanelIndices = null;
		currentPage = 0;
		rebuildPanels();
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
	 * Returns the sizes of all currently visible itemsets (across ALL pages, not
	 * just the current page). The size of an itemset is the number of items it
	 * contains.
	 *
	 * @return a list of integers, each representing the number of items in a
	 *         visible itemset
	 */
	@Override
	public List<Integer> getVisiblePatternSizes() {
		List<Integer> sizes = new ArrayList<>();
		List<Integer> visible = resolveVisibleIndices();
		for (int idx : visible) {
			sizes.add(allPanels.get(idx).getItemset().getItems().size());
		}
		return sizes;
	}
}