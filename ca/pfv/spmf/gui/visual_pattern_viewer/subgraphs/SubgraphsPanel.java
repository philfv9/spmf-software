package ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information.
 */
/**
 * Panel that parses a file of subgraph patterns in SPMF / gSpan format and
 * displays each pattern as a {@link SubgraphPanel}. Provides sorting by
 * measure, by size, or lexicographically, and supports text / measure-based
 * filtering as well as optional pagination.
 *
 * <p>Supports optional pagination: call {@link #setPageSize(int)} to limit the
 * number of patterns shown per page, or pass {@code Integer.MAX_VALUE} to show
 * all patterns at once.
 *
 * @author Philippe Fournier-Viger
 */
public class SubgraphsPanel extends PatternsPanel {

    /** Serial UID. */
    private static final long serialVersionUID = 8812093471239847123L;

    /** Pixel gap inserted between individual pattern panels. */
    private static final int GAP_BETWEEN_PATTERNS = 10;

    /** Reader that parsed the pattern file. */
    private SubgraphsReader reader;

    /** One panel per parsed subgraph pattern (kept in memory for fast paging). */
    private final List<SubgraphPanel> allPanels = new ArrayList<>();

    /**
     * Ordered list of indices into {@link #allPanels} that pass the current
     * search / filter. {@code null} means no filter is active (all panels
     * visible).
     */
    private List<Integer> visiblePanelIndices = null;

    /** Pre-computed minimum values for each numeric measure. */
    private final Map<String, Double> minValues;

    /** Pre-computed maximum values for each numeric measure. */
    private final Map<String, Double> maxValues;

    /**
     * Creates a panel, parses the given reader for subgraph patterns, and builds
     * child panels.
     *
     * @param reader a {@link SubgraphsReader} that has already parsed the file
     * @param mode   the layout mode (VERTICAL, HORIZONTAL, or GRID)
     * @throws IOException if reading the file fails
     */
    public SubgraphsPanel(PatternsReader reader, LayoutMode mode) throws IOException {
        super(mode);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.LIGHT_GRAY);
        this.reader    = (SubgraphsReader) reader;
        this.minValues = reader.getAllMinValues();
        this.maxValues = reader.getAllMaxValues();
        buildPanels();
        rebuildPanels();
    }

    /**
     * @return an ordered list of all available sorting orders
     */
    @Override
    public List<String> getListOfSortingOrders() {
        return reader.getListOfSortingOrders();
    }

    /**
     * Sorts the subgraph patterns according to the user's choice and refreshes the
     * display from page 0.
     *
     * @param choice the sorting order string (as returned by
     *               {@link #getListOfSortingOrders()})
     */
    @Override
    public void sortPatterns(String choice) {
        reader.sortPatterns(choice);
        allPanels.clear();
        buildPanels();
        currentPage = 0;
        rebuildPanels();
    }

    /**
     * @return the total number of subgraph patterns loaded from the file
     */
    @Override
    public int getTotalPatternCount() {
        return reader.getPatterns().size();
    }

    /**
     * @return the number of patterns currently visible (after filtering, across
     *         all pages)
     */
    @Override
    public int getNumberOfVisiblePatterns() {
        return (visiblePanelIndices == null) ? allPanels.size()
                : visiblePanelIndices.size();
    }

    /**
     * Applies a text search and measure-based filtering to the list of subgraphs
     * currently loaded in memory. Resets to page 0 after filtering so the user
     * always sees the first page of results.
     */
    @Override
    public void applySearchAndFilters(String searchString,
            Map<String, Double> measureThresholds,
            Map<String, String> measureOperators) {

        List<Integer> matched = new ArrayList<>();

        for (int i = 0; i < allPanels.size(); i++) {
            Subgraph subgraph = allPanels.get(i).getSubgraph();

            // --- Text filter (all tokens must be present) ---
            // IMPORTANT: Use toSearchableString() which contains ONLY vertex and edge labels,
            // not vertex IDs or containing graph IDs
            if (searchString != null && !searchString.isBlank()) {
                String patternStr = subgraph.toSearchableString().toLowerCase();
                String[] tokens   = searchString.toLowerCase().split("\\s+");
                boolean allMatch  = true;
                for (String token : tokens) {
                    if (!patternStr.contains(token)) {
                        allMatch = false;
                        break;
                    }
                }
                if (!allMatch) {
                    continue;
                }
            }

            // --- Measure filters ---
            if (measureThresholds != null && !measureThresholds.isEmpty()) {
                boolean failed = false;
                for (Map.Entry<String, Double> entry : measureThresholds.entrySet()) {
                    String name      = entry.getKey();
                    Double threshold = entry.getValue();
                    String rawValue  = subgraph.getMeasures().get(name);
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
                    String operator = "\u2265"; // default ≥
                    if (measureOperators != null && measureOperators.containsKey(name)) {
                        operator = measureOperators.get(name);
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
     * Populates the provided map with numeric measure values from all subgraph
     * patterns (used e.g. for building histograms in the viewer).
     *
     * @param measureValuesMap map of measure name → list to which values are
     *                         appended
     */
    @Override
    public void populateMeasureValues(Map<String, List<Double>> measureValuesMap) {
        for (Subgraph subgraph : reader.getPatterns()) {
            for (Map.Entry<String, String> entry : subgraph.getMeasures().entrySet()) {
                String measureName = entry.getKey();
                String valueStr    = entry.getValue();
                try {
                    double value = Double.parseDouble(valueStr);
                    List<Double> list = measureValuesMap.get(measureName);
                    if (list != null) {
                        list.add(value);
                    }
                } catch (NumberFormatException e) {
                    // skip non-numeric values
                }
            }
        }
    }

    /**
     * Returns the sizes (vertex counts) of all currently visible subgraph patterns
     * across ALL pages, not just the current page.
     *
     * @return list of vertex counts for each visible subgraph
     */
    @Override
    public List<Integer> getVisiblePatternSizes() {
        List<Integer> sizes   = new ArrayList<>();
        List<Integer> visible = resolveVisibleIndices();
        for (int idx : visible) {
            sizes.add(allPanels.get(idx).getSubgraph().size());
        }
        return sizes;
    }

    /**
     * Clears and rebuilds the layout for the current page. Only the panels that
     * belong to the current page are added to the Swing hierarchy.
     */
    @Override
    public void rebuildPanels() {
        removeAll();

        List<Integer> visible     = resolveVisibleIndices();
        int           totalVisible = visible.size();
        int           fromIdx;
        int           toIdx;

        if (pageSize == Integer.MAX_VALUE || pageSize <= 0) {
            fromIdx = 0;
            toIdx   = totalVisible;
        } else {
            int totalPages = Math.max(1, (int) Math.ceil((double) totalVisible / pageSize));
            if (currentPage >= totalPages) {
                currentPage = totalPages - 1;
            }
            fromIdx = currentPage * pageSize;
            toIdx   = Math.min(fromIdx + pageSize, totalVisible);
        }

        List<SubgraphPanel> panelsToDisplay = new ArrayList<>();
        for (int i = fromIdx; i < toIdx; i++) {
            panelsToDisplay.add(allPanels.get(visible.get(i)));
        }

        if (layoutMode == LayoutMode.VERTICAL) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            for (SubgraphPanel panel : panelsToDisplay) {
                panel.setAlignmentX(Component.CENTER_ALIGNMENT);
                add(Box.createVerticalStrut(GAP_BETWEEN_PATTERNS));
                add(panel);
            }
            add(Box.createVerticalGlue());
        } else if (layoutMode == LayoutMode.HORIZONTAL) {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            for (SubgraphPanel panel : panelsToDisplay) {
                panel.setAlignmentY(Component.CENTER_ALIGNMENT);
                add(Box.createHorizontalStrut(GAP_BETWEEN_PATTERNS));
                add(panel);
            }
            add(Box.createHorizontalGlue());
        } else {
            // GRID (default)
            int columns = 3;
            int rows    = panelsToDisplay.isEmpty() ? 1
                    : (int) Math.ceil((double) panelsToDisplay.size() / columns);
            setLayout(new java.awt.GridLayout(rows, columns,
                    GAP_BETWEEN_PATTERNS, GAP_BETWEEN_PATTERNS));
            for (SubgraphPanel panel : panelsToDisplay) {
                add(panel);
            }
        }

        revalidate();
        repaint();
    }

    /**
     * Resolves the ordered list of indices that are currently visible (after
     * filtering). If no filter is active this is simply {@code 0 …
     * allPanels.size()-1}.
     *
     * @return list of visible panel indices
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
     * Builds one {@link SubgraphPanel} for every parsed subgraph and stores them
     * all in {@link #allPanels}.
     */
    private void buildPanels() {
        for (Subgraph subgraph : reader.getPatterns()) {
            allPanels.add(new SubgraphPanel(subgraph, minValues, maxValues));
        }
    }

    /**
     * Returns the set of all measure names found across the loaded patterns.
     *
     * @return set of measure names
     */
    public Set<String> getAllMeasures() {
        return reader.getAllMeasures();
    }

    /**
     * Returns the minimum numeric value observed for the given measure across all
     * patterns (before any global scaling).
     *
     * @param measure the measure name
     * @return the minimum value, or {@code null} if the measure is not numeric or
     *         not found
     */
    public Double getMinForMeasureOriginal(String measure) {
        return reader.getMinForMeasureOriginal(measure);
    }

    /**
     * Returns the maximum numeric value observed for the given measure across all
     * patterns (before any global scaling).
     *
     * @param measure the measure name
     * @return the maximum value, or {@code null} if the measure is not numeric or
     *         not found
     */
    public Double getMaxForMeasureOriginal(String measure) {
        return reader.getMaxForMeasureOriginal(measure);
    }
}