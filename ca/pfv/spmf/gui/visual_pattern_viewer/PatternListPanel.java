package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.JComponent;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * A {@link PatternsPanel} that displays one card per pattern read by a
 * {@link PatternsReader}. Implements everything that is common to all pattern
 * types: creation and reuse of the cards, sorting, text search, measure
 * filters, pagination, statistics of the visible patterns. Subclasses only
 * describe their pattern type through a handful of small methods.
 * <p>
 * Subclass constructors must call {@link #initialize()} once their own fields
 * are set.
 *
 * @param <T> the pattern type (e.g. Rule)
 * @param <C> the component displaying one pattern (e.g. RulePanel)
 * @author Philippe Fournier-Viger
 */
public abstract class PatternListPanel<T, C extends JComponent> extends PatternsPanel {

	private static final long serialVersionUID = 4221039874112384L;

	/** The reader holding the patterns (sorted in place) */
	protected final PatternsReader reader;
	/** Min value of each numeric measure (adjusted, for bar scaling) */
	protected final Map<String, Double> minValues;
	/** Max value of each numeric measure (adjusted, for bar scaling) */
	protected final Map<String, Double> maxValues;

	/** Snapshot of the patterns in the current sort order */
	private final List<T> allPatterns = new ArrayList<>();
	/** One card per pattern, parallel to {@link #allPatterns} */
	private final List<C> allPanels = new ArrayList<>();
	/** Cards indexed by pattern identity, so sorting does not recreate them */
	private Map<T, C> panelCache = new IdentityHashMap<>();

	/** Indices into allPanels passing the current filter, or null if no filter */
	private List<Integer> visibleIndices = null;

	// Active filter (kept so it can be re-evaluated after sorting)
	private String[] searchTokens = new String[0];
	private Map<String, Double> thresholds = null;
	private Map<String, String> operators = null;

	/**
	 * @param reader a reader that has already parsed the file
	 * @param mode   the layout mode
	 */
	protected PatternListPanel(PatternsReader reader, LayoutMode mode) {
		super(mode);
		this.reader = Objects.requireNonNull(reader, "reader must not be null");
		this.minValues = reader.getAllMinValues();
		this.maxValues = reader.getAllMaxValues();
	}

	/**
	 * Builds the cards and lays out the first page. Must be called at the end of
	 * every subclass constructor.
	 */
	protected final void initialize() {
		buildPanels();
		rebuildPanels();
	}
	
	@Override
	protected boolean hasActiveFilter() {
		return searchTokens.length > 0 || (thresholds != null && !thresholds.isEmpty());
	}

	// ---------------------------------------------------------------
	// What a subclass must provide
	// ---------------------------------------------------------------

	/** @return the patterns of the reader, in their current order */
	protected abstract List<T> getPatterns();

	/** @return a new card displaying the given pattern */
	protected abstract C createPanel(T pattern);

	/** @return the measures of a pattern (name → raw value) */
	protected abstract Map<String, String> getMeasures(T pattern);

	/** @return the text in which the search tokens are looked for */
	protected abstract String getSearchableText(T pattern);

	/** @return the size of a pattern for the size histogram */
	protected abstract int getPatternSize(T pattern);

	/** @return plural name of the pattern type, e.g. "rules" (for messages) */
	protected abstract String getPatternTypeName();

	// ---------------------------------------------------------------
	// Cards
	// ---------------------------------------------------------------

	private void buildPanels() {
		Map<T, C> newCache = new IdentityHashMap<>();
		allPatterns.clear();
		allPanels.clear();
		for (T pattern : getPatterns()) {
			C panel = panelCache.get(pattern);
			if (panel == null) {
				panel = createPanel(pattern);
			}
			newCache.put(pattern, panel);
			allPatterns.add(pattern);
			allPanels.add(panel);
		}
		panelCache = newCache;
	}

	private List<Integer> resolveVisibleIndices() {
		if (visibleIndices != null) {
			return visibleIndices;
		}
		List<Integer> all = new ArrayList<>(allPanels.size());
		for (int i = 0; i < allPanels.size(); i++) {
			all.add(i);
		}
		return all;
	}

	@Override
	protected void rebuildPanels() {
		List<Integer> visible = resolveVisibleIndices();
		int[] range = computePageRange(visible.size());
		List<C> page = new ArrayList<>(range[1] - range[0]);
		for (int i = range[0]; i < range[1]; i++) {
			page.add(allPanels.get(visible.get(i)));
		}
		layoutPatternPanels(page);
	}

	@Override
	protected String getEmptyMessage() {
		String name = getPatternTypeName();
		return isFilterActive() ? "No " + name + " match the current search / filter" : "No " + name + " to display";
	}

	// ---------------------------------------------------------------
	// Sorting
	// ---------------------------------------------------------------

	@Override
	public List<String> getListOfSortingOrders() {
		return reader.getListOfSortingOrders();
	}

	@Override
	public void sortPatterns(String choice) {
		reader.sortPatterns(choice);
		buildPanels();
		if (isFilterActive()) {
			visibleIndices = computeMatchingIndices(); // indices refer to the new order
		}
		currentPage = 0;
		rebuildPanels();
	}

	// ---------------------------------------------------------------
	// Search & filter
	// ---------------------------------------------------------------

	private boolean isFilterActive() {
		return searchTokens.length > 0 || (thresholds != null && !thresholds.isEmpty());
	}

	@Override
	public void applySearchAndFilters(String searchString, Map<String, Double> measureThresholds,
			Map<String, String> measureOperators) {
		searchTokens = (searchString == null || searchString.isBlank()) ? new String[0]
				: searchString.trim().toLowerCase().split("\\s+");
		thresholds = measureThresholds == null ? null : new LinkedHashMap<>(measureThresholds);
		operators = measureOperators == null ? null : new LinkedHashMap<>(measureOperators);
		visibleIndices = computeMatchingIndices();
		currentPage = 0;
		rebuildPanels();
	}

	private List<Integer> computeMatchingIndices() {
		List<Integer> matched = new ArrayList<>();
		for (int i = 0; i < allPatterns.size(); i++) {
			if (matches(allPatterns.get(i))) {
				matched.add(i);
			}
		}
		return matched;
	}

	private boolean matches(T pattern) {
		if (searchTokens.length > 0) {
			String text = getSearchableText(pattern);
			text = text == null ? "" : text.toLowerCase();
			for (String token : searchTokens) {
				if (!text.contains(token)) {
					return false;
				}
			}
		}
		if (thresholds != null) {
			Map<String, String> measures = getMeasures(pattern);
			for (Map.Entry<String, Double> entry : thresholds.entrySet()) {
				String measure = entry.getKey();
				double threshold = entry.getValue();
				Double value = ViewerStyle.parseDouble(measures == null ? null : measures.get(measure));
				if (value == null) {
					return false;
				}
				String operator = operators == null ? null : operators.get(measure);
				if ("\u2264".equals(operator)) {
					if (value > threshold) {
						return false;
					}
				} else if (value < threshold) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void clearSearchAndFilters() {
		searchTokens = new String[0];
		thresholds = null;
		operators = null;
		visibleIndices = null;
		currentPage = 0;
		rebuildPanels();
	}

	// ---------------------------------------------------------------
	// Statistics
	// ---------------------------------------------------------------

	@Override
	public int getTotalPatternCount() {
		return allPatterns.size();
	}

	@Override
	public int getNumberOfVisiblePatterns() {
		return visibleIndices == null ? allPatterns.size() : visibleIndices.size();
	}

	@Override
	public Set<String> getAllMeasures() {
		return reader.getAllMeasures();
	}

	@Override
	public Double getMinForMeasureOriginal(String measure) {
		return reader.getMinForMeasureOriginal(measure);
	}

	@Override
	public Double getMaxForMeasureOriginal(String measure) {
		return reader.getMaxForMeasureOriginal(measure);
	}

	/**
	 * Populates the map with the measure values of all <em>visible</em> patterns
	 * (across all pages).
	 */
	@Override
	public void populateMeasureValues(Map<String, List<Double>> measureValuesMap) {
		for (int idx : resolveVisibleIndices()) {
			Map<String, String> measures = getMeasures(allPatterns.get(idx));
			if (measures == null) {
				continue;
			}
			for (Map.Entry<String, String> entry : measures.entrySet()) {
				List<Double> values = measureValuesMap.get(entry.getKey());
				if (values == null) {
					continue;
				}
				Double v = ViewerStyle.parseDouble(entry.getValue());
				if (v != null) {
					values.add(v);
				}
			}
		}
	}

	@Override
	public List<Integer> getVisiblePatternSizes() {
		List<Integer> sizes = new ArrayList<>();
		for (int idx : resolveVisibleIndices()) {
			sizes.add(getPatternSize(allPatterns.get(idx)));
		}
		return sizes;
	}
}