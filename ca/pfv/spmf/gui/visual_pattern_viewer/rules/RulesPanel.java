package ca.pfv.spmf.gui.visual_pattern_viewer.rules;

import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.util.*;

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
 * Panel that parses a file of association rules (including extended SPMF format
 * with item mappings) and displays each rule as a {@link RulePanel}.
 * Provides sorting by measure or lexicographically, and the ability to export
 * the entire view as a PNG.
 * @author Philippe Fournier-Viger
 */
public class RulesPanel extends PatternsPanel {
	/** serial UID **/
	private static final long serialVersionUID = 123982348l;

	/** Vertical gap between panels in pixels */
	private static final int GAP_BETWEEN_PATTERNS = 10;

	/** Class to read the rules from a file */
	RulesReader reader = null;

	/** All panels, each corresponding to a rule */
	private final List<RulePanel> allPanels = new ArrayList<>();

	/** Filtered indices (optional), or null if no filter is applied */
	private Set<Integer> visiblePanelIndices = null;

	/** Precomputed min and max values for measures */
	private final Map<String, Double> minValues;
	private final Map<String, Double> maxValues;

	/**
	 * Creates a panel, parses the given file for rules or transactions, and builds
	 * child panels. Supports both standard and extended SPMF formats.
	 *
	 * @param file a text file in one of the following formats:
	 *             <ul>
	 *             <li>Standard: "item1 item2 ==> item3 #m1:v1 #m2:v2 ..."</li>
	 *             <li>Extended: first lines mapping "@ITEM=id=name", then same rule
	 *             lines</li>
	 *             </ul>
	 * @param mode the layout mode
	 * @throws IOException if reading the file fails
	 */
	public RulesPanel(PatternsReader reader, LayoutMode mode) throws IOException {
		super(mode);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Color.LIGHT_GRAY);
		this.reader = (RulesReader) reader;
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
	public void sortRulesByMeasure(String measure, boolean ascending) {
		reader.sortPatternsByMeasure(measure, ascending);
		allPanels.clear();
		buildPanels();
		rebuildPanels();
	}

	/**
	 * Clears and rebuilds the layout. Visibility of panels is controlled by
	 * filtering state; only visible panels are re-added to the layout.
	 */
	@Override
	public void rebuildPanels() {
		removeAll();

		List<RulePanel> panelsToDisplay = new ArrayList<>();
		for (int i = 0; i < allPanels.size(); i++) {
			if (visiblePanelIndices == null || visiblePanelIndices.contains(i)) {
				panelsToDisplay.add(allPanels.get(i));
			}
		}

		if (layoutMode == LayoutMode.VERTICAL) {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			for (RulePanel panel : panelsToDisplay) {
				panel.setAlignmentX(Component.CENTER_ALIGNMENT);
				add(Box.createVerticalStrut(GAP_BETWEEN_PATTERNS));
				add(panel);
			}
			add(Box.createVerticalGlue());
		} else if (layoutMode == LayoutMode.HORIZONTAL) {
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			for (RulePanel panel : panelsToDisplay) {
				panel.setAlignmentY(Component.CENTER_ALIGNMENT);
				add(Box.createHorizontalStrut(GAP_BETWEEN_PATTERNS));
				add(panel);
			}
			add(Box.createHorizontalGlue());
		} else if (layoutMode == LayoutMode.GRID) {
			int columns = 3;
			int rows = (int) Math.ceil((double) panelsToDisplay.size() / columns);
			setLayout(new java.awt.GridLayout(rows, columns, GAP_BETWEEN_PATTERNS, GAP_BETWEEN_PATTERNS));
			for (RulePanel panel : panelsToDisplay) {
				add(panel);
			}
		}

		revalidate();
		repaint();
	}

	/**
	 * @return an ordered list of all sorting orders that are offered
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
	 * @return the minimum numeric value observed for the given measure (or null if none)
	 */
	public Double getMinForMeasureOriginal(String measure) {
		return reader.getMinForMeasure(measure);
	}

	/**
	 * Get the maximum numeric value observed for the given measure
	 * 
	 * @return the maximum numeric value observed for the given measure (or null if none)
	 */
	public Double getMaxForMeasureOriginal(String measure) {
		return reader.getMaxForMeasure(measure);
	}

	/**
	 * Builds all {@link RulePanel}s once and stores them in memory.
	 */
	private void buildPanels() {
		List<Rule> patterns = reader.getPatterns();
		for (Rule rule : patterns) {
			RulePanel panel = new RulePanel(rule, minValues, maxValues);
			allPanels.add(panel);
		}
	}

	/**
	 * Applies a text search and measure-based filtering to the list of rules
	 * currently loaded in memory. Only rules that match the search string (if
	 * provided) and satisfy all measure thresholds (with the specified operators)
	 * will be displayed.
	 * <p>
	 * The original list of rules remains unchanged in memory; filtering is
	 * applied dynamically and temporarily to the view only.
	 * </p>
	 *
	 * @param searchString      a string to search for (case-insensitive) in the
	 *                          string representation of each rule; may be null
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
			Rule rule = allPanels.get(i).getRule();

			// Text filter
			// Text filter with token matching
			if (searchString != null && !searchString.isBlank()) {
				String patternStr = rule.toString().toLowerCase();
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
					Double value = Double.valueOf(rule.getMeasures().get(measure));
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
				if (failed) continue;
			}

			visiblePanelIndices.add(i);
		}

		rebuildPanels();
	}


	/**
	 * Clears any active text search or measure-based filtering. The view is reset
	 * to show all original rules loaded from the file.
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
		return visiblePanelIndices.size();
	}
	
	/**
	 * Populates the provided map with measure values from all rules.
	 * Each measure name maps to a list of its numeric values across all patterns.
	 * 
	 * @param measureValuesMap the map to populate with measure values
	 */
	@Override
	public void populateMeasureValues(Map<String, List<Double>> measureValuesMap) {
		List<Rule> patterns = reader.getPatterns();
		for (Rule rule : patterns) {
			Map<String, String> measures = rule.getMeasures();
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
	 * Returns the sizes of all currently visible rules.
	 * The size of a rule is the total number of items in
	 * the antecedent plus the consequent.
	 * 
	 * @return a list of integers representing visible rule sizes
	 */
	@Override
	public List<Integer> getVisiblePatternSizes() {
		List<Integer> sizes = new ArrayList<>();
		for (int i = 0; i < allPanels.size(); i++) {
			if (visiblePanelIndices == null || visiblePanelIndices.contains(i)) {
				Rule rule = allPanels.get(i).getRule();
				int size = rule.getAntecedent().size() + rule.getConsequent().size();
				sizes.add(size);
			}
		}
		return sizes;
	}
}
