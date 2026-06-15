package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import ca.pfv.spmf.gui.Main;

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
 * 
 * Do not remove copyright and license information
 */
/**
 * This class implements a GUI Window (a viewer) for viewing patterns found by
 * data mining algorithms.
 * <p>
 * A left sidebar contains summary statistics, a size distribution histogram,
 * a search panel, and a separate filter panel for numeric measure filtering.
 * Each measure slider is backed by a {@link MeasureDistributionBar} that shows
 * where values cluster across all visible patterns.
 * <p>
 * A pagination toolbar at the bottom allows the user to navigate through pages
 * of patterns or switch to "show all" mode.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class VisualPatternViewer extends JFrame {

    /** Serial UID */
    private static final long serialVersionUID = 3545413603350311234L;

    /** Window title */
    private static final String WINDOW_TITLE = "SPMF Visual Pattern Viewer ";

    /** Default filename suggested in the export dialog */
    static final String DEFAULT_EXPORT_FILENAME = "patterns_visualization.png";

    /** Preferred width for the left sidebar */
    static final int SIDEBAR_WIDTH = 400;

    /**
     * Datasets larger than this threshold automatically activate pagination.
     */
    private static final int LARGE_DATASET_THRESHOLD = 200;

    /** Default page size used when pagination is activated automatically. */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /** Path to the original pattern file */
    private final String filePath;

    /** Selected layout mode */
    LayoutMode mode = LayoutMode.GRID;

    /** Panel for showing patterns */
    private PatternsPanel patternsPanel;

    /** Left sidebar */
    private JPanel sidebarPanel;

    /** Panel for text search only */
    private JPanel searchPanel;

    /** Panel for numeric measure filtering */
    private JPanel filterPanel;

    /** Panel for summary statistics */
    private JPanel summaryPanel;

    /** Histogram panel for size distribution */
    private SizeDistributionHistogram histogramPanel;

    /** Label for displaying pattern count */
    private JLabel countLabel;

    /** Status bar at the bottom */
    private JPanel statusBar;

    /** Status bar label */
    private JLabel statusLabel;

    // --- Pagination toolbar ---
    private JPanel paginationBar;
    private JButton btnFirst;
    private JButton btnPrev;
    private JButton btnNext;
    private JButton btnLast;
    private JLabel  pageInfoLabel;
    private JComboBox<String> pageSizeCombo;

    /** Scroll pane that wraps the patterns panel */
    private JScrollPane patternScrollPane;

    /**
     * Shared "Clear All" button referenced by both search and filter panels.
     */
    private JButton clearAllButton;

    /**
     * All measure distribution bars, keyed by measure name. Updated whenever
     * the visible pattern set changes.
     */
    private final Map<String, MeasureDistributionBar> distributionBars =
            new LinkedHashMap<>();

	private JTextField searchField;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    /**
     * Constructor
     *
     * @param runAsStandaloneProgram if true, closing the window exits the JVM
     * @param filePath               path to the pattern file to visualize
     * @param patternType            type of pattern to display
     * @throws IOException if error reading the input file
     */
    public VisualPatternViewer(boolean runAsStandaloneProgram, String filePath,
            PatternType patternType) throws IOException {
        super(WINDOW_TITLE + Main.SPMF_VERSION);
        if (runAsStandaloneProgram) {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }

        this.filePath = filePath;

        patternsPanel = PatternPanelFactory.getPatternPanel(filePath, patternType, mode);

        // Auto-activate pagination for large datasets
        if (patternsPanel.getTotalPatternCount() > LARGE_DATASET_THRESHOLD) {
            patternsPanel.setPageSize(DEFAULT_PAGE_SIZE);
        }

        // Retrieve all measures and compute min/max
        Set<String> measures = patternsPanel.getAllMeasures();
        Map<String, Double> minMap = new LinkedHashMap<>();
        Map<String, Double> maxMap = new LinkedHashMap<>();
        for (String measure : measures) {
            Double minVal = patternsPanel.getMinForMeasureOriginal(measure);
            Double maxVal = patternsPanel.getMaxForMeasureOriginal(measure);
            if (minVal != null && maxVal != null) {
                minMap.put(measure, minVal);
                maxMap.put(measure, maxVal);
            }
        }

        // === Build the left sidebar ===
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        // Shared sliders and operator combos
        Map<String, MeasureDistributionBar> sliders     = new LinkedHashMap<>();
        Map<String, JComboBox<String>>      operatorCombos = new LinkedHashMap<>();

        // Search panel (text only)
        searchPanel = buildSearchPanel(minMap, maxMap, sliders, operatorCombos);
        sidebarPanel.add(wrapForSidebar(searchPanel));
        sidebarPanel.add(Box.createVerticalStrut(6));

        // Filter panel (distribution sliders)
        filterPanel = buildFilterPanel(minMap, maxMap, sliders, operatorCombos);
        sidebarPanel.add(wrapForSidebar(filterPanel));
        sidebarPanel.add(Box.createVerticalStrut(6));

        // Summary statistics
        summaryPanel = buildSummaryPanel(minMap, maxMap);
        sidebarPanel.add(wrapForSidebar(summaryPanel));
        sidebarPanel.add(Box.createVerticalStrut(6));

        // Size distribution histogram
        histogramPanel = new SizeDistributionHistogram();
        histogramPanel.updateData(patternsPanel);
        sidebarPanel.add(wrapForSidebar(histogramPanel));

        sidebarPanel.add(Box.createVerticalGlue());

        // Populate distribution bars with initial data
        updateDistributionBars();

        // === Pagination toolbar ===
        paginationBar = buildPaginationBar();

        // === Status bar ===
        statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        statusLabel = new JLabel(buildStatusText());
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(80, 80, 80));
        statusBar.add(statusLabel, BorderLayout.WEST);

        countLabel = new JLabel();
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        countLabel.setForeground(new Color(60, 60, 60));
        updateCountLabel();
        statusBar.add(countLabel, BorderLayout.EAST);

        // === Layout assembly ===
        getContentPane().setLayout(new BorderLayout());

        patternScrollPane = new JScrollPane((JPanel) patternsPanel);
        getContentPane().add(patternScrollPane, BorderLayout.CENTER);

        JScrollPane sidebarScroll = new JScrollPane(sidebarPanel);
        sidebarScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sidebarScroll.setPreferredSize(new Dimension(SIDEBAR_WIDTH + 30, 0));
        sidebarScroll.setMinimumSize(new Dimension(SIDEBAR_WIDTH + 30, 200));
        sidebarScroll.setBorder(null);
        getContentPane().add(sidebarScroll, BorderLayout.WEST);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(paginationBar, BorderLayout.NORTH);
        southPanel.add(statusBar, BorderLayout.SOUTH);
        getContentPane().add(southPanel, BorderLayout.SOUTH);

        // === Menu bar ===
        this.setJMenuBar(createMenuBar(this, patternsPanel, this.filePath));

        // === Window title ===
        String fileName = new java.io.File(filePath).getName();
        this.setTitle(WINDOW_TITLE + Main.SPMF_VERSION + " - " + fileName);

        configureFrameSize(this, patternsPanel);
        refreshPaginationBar();
    }

    // ---------------------------------------------------------------
    // Distribution bar update
    // ---------------------------------------------------------------

    /**
     * Recomputes and pushes fresh data into every {@link MeasureDistributionBar}.
     * Called once on startup and again after every filter / sort change.
     */
    private void updateDistributionBars() {
        if (distributionBars.isEmpty()) return;

        // Build a single measure-values map covering all visible patterns
        Map<String, List<Double>> measureValuesMap = new LinkedHashMap<>();
        for (String measure : distributionBars.keySet()) {
            measureValuesMap.put(measure, new ArrayList<>());
        }
        patternsPanel.populateMeasureValues(measureValuesMap);

        for (Map.Entry<String, MeasureDistributionBar> entry : distributionBars.entrySet()) {
            List<Double> values = measureValuesMap.get(entry.getKey());
            entry.getValue().updateDistribution(values);
        }
    }

    // ---------------------------------------------------------------
    // Export dialog
    // ---------------------------------------------------------------

    

    // ---------------------------------------------------------------
    // Pagination toolbar
    // ---------------------------------------------------------------

    private JPanel buildPaginationBar() {
        JPanel bar = new JPanel(new BorderLayout(4, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        bar.setBackground(new Color(245, 245, 245));

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navPanel.setOpaque(false);

        btnFirst = new JButton("|◀");
        btnFirst.setToolTipText("First page");
        btnFirst.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnFirst.setMargin(new Insets(2, 5, 2, 5));
        btnFirst.addActionListener(e -> navigateToPage(0));

        btnPrev = new JButton("◀");
        btnPrev.setToolTipText("Previous page");
        btnPrev.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnPrev.setMargin(new Insets(2, 5, 2, 5));
        btnPrev.addActionListener(e -> navigateToPage(patternsPanel.getCurrentPage() - 1));

        pageInfoLabel = new JLabel("", SwingConstants.CENTER);
        pageInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        pageInfoLabel.setPreferredSize(new Dimension(130, 22));

        btnNext = new JButton("▶");
        btnNext.setToolTipText("Next page");
        btnNext.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnNext.setMargin(new Insets(2, 5, 2, 5));
        btnNext.addActionListener(e -> navigateToPage(patternsPanel.getCurrentPage() + 1));

        btnLast = new JButton("▶|");
        btnLast.setToolTipText("Last page");
        btnLast.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnLast.setMargin(new Insets(2, 5, 2, 5));
        btnLast.addActionListener(e -> navigateToPage(patternsPanel.getTotalPages() - 1));

        navPanel.add(btnFirst); navPanel.add(btnPrev);
        navPanel.add(pageInfoLabel);
        navPanel.add(btnNext);  navPanel.add(btnLast);

        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        sizePanel.setOpaque(false);
        JLabel sizeLabel = new JLabel("Patterns per page:");
        sizeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        String[] opts = { "10", "25", "50", "100", "200", "500", "Show All" };
        pageSizeCombo = new JComboBox<>(opts);
        pageSizeCombo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        pageSizeCombo.setPreferredSize(new Dimension(90, 24));
        syncPageSizeCombo();

        pageSizeCombo.addActionListener(e -> {
            String sel = (String) pageSizeCombo.getSelectedItem();
            int newSize = "Show All".equals(sel) ? Integer.MAX_VALUE : Integer.parseInt(sel);
            patternsPanel.setPageSize(newSize);
            scrollPatternPanelToTop();
            refreshPaginationBar();
            updateStatusBar();
        });

        sizePanel.add(sizeLabel); sizePanel.add(pageSizeCombo);
        bar.add(navPanel, BorderLayout.WEST);
        bar.add(sizePanel, BorderLayout.EAST);
        return bar;
    }

    private void syncPageSizeCombo() {
        if (pageSizeCombo == null) return;
        int current = patternsPanel.getPageSize();
        String targetStr = (current == Integer.MAX_VALUE) ? "Show All" : String.valueOf(current);
        var listeners = pageSizeCombo.getActionListeners();
        for (var l : listeners) pageSizeCombo.removeActionListener(l);
        for (int i = 0; i < pageSizeCombo.getItemCount(); i++) {
            if (targetStr.equals(pageSizeCombo.getItemAt(i))) {
                pageSizeCombo.setSelectedIndex(i); break;
            }
        }
        for (var l : listeners) pageSizeCombo.addActionListener(l);
    }

    private void navigateToPage(int page) {
        patternsPanel.goToPage(page);
        scrollPatternPanelToTop();
        refreshPaginationBar();
        updateStatusBar();
    }

    void refreshPaginationBar() {
        if (paginationBar == null) return;
        boolean paginated = patternsPanel.isPaginated();
        int cp = patternsPanel.getCurrentPage();
        int tp = patternsPanel.getTotalPages();
        paginationBar.setVisible(true);
        btnFirst.setEnabled(paginated && cp > 0);
        btnPrev.setEnabled(paginated && cp > 0);
        btnNext.setEnabled(paginated && cp < tp - 1);
        btnLast.setEnabled(paginated && cp < tp - 1);
        pageInfoLabel.setText(paginated
                ? String.format("Page %d of %d", cp + 1, tp) : "All patterns");
        syncPageSizeCombo();
    }

    private void scrollPatternPanelToTop() {
        if (patternScrollPane != null) {
            SwingUtilities.invokeLater(() ->
                    patternScrollPane.getViewport()
                            .setViewPosition(new java.awt.Point(0, 0)));
        }
    }

    // ---------------------------------------------------------------
    // Sidebar wrapper
    // ---------------------------------------------------------------

    private static JPanel wrapForSidebar(JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(content, BorderLayout.CENTER);
        int h = content.getPreferredSize().height;
        wrapper.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, h));
        wrapper.setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, h));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return wrapper;
    }

    // ---------------------------------------------------------------
    // Search panel
    // ---------------------------------------------------------------

    /**
     * Builds the text-search panel.
     *
     * @param minMap         minimum values per measure
     * @param maxMap         maximum values per measure
     * @param sliders        shared distribution-bar sliders (populated by
     *                       {@link #buildFilterPanel})
     * @param operatorCombos shared operator combos (populated by
     *                       {@link #buildFilterPanel})
     * @return the assembled search panel
     */
    private JPanel buildSearchPanel(Map<String, Double> minMap, Map<String, Double> maxMap,
            Map<String, MeasureDistributionBar> sliders,
            Map<String, JComboBox<String>> operatorCombos) {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Search"));

        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel searchLabel = new JLabel(" Text: ");
        searchField = new JTextField();
        JButton searchButton = new JButton("Go");
        searchButton.setMargin(new Insets(2, 6, 2, 6));
        searchRow.add(searchLabel, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);
        panel.add(searchRow);
        panel.add(Box.createVerticalStrut(6));

        Runnable doSearch = () -> {
            applySearchAndFilter(searchField.getText().trim(),
                    sliders, operatorCombos, minMap, maxMap);
            clearAllButton.setEnabled(true);
        };
        searchField.addActionListener(e -> doSearch.run());
        searchButton.addActionListener(e -> doSearch.run());

        int h = panel.getPreferredSize().height + 10;
        panel.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, h));
        panel.setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, h));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return panel;
    }

    // ---------------------------------------------------------------
    // Filter panel
    // ---------------------------------------------------------------

    /**
     * Builds the numeric filter panel. Each measure gets a single row containing
     * the measure label, operator combo, and distribution slider all on one line.
     *
     * @param minMap         minimum values per measure
     * @param maxMap         maximum values per measure
     * @param sliders        map to populate with measure → distribution-bar slider
     * @param operatorCombos map to populate with measure → operator combo
     * @return the assembled filter panel
     */
    private JPanel buildFilterPanel(Map<String, Double> minMap, Map<String, Double> maxMap,
            Map<String, MeasureDistributionBar> sliders,
            Map<String, JComboBox<String>> operatorCombos) {

        JPanel outerPanel = new JPanel();
        outerPanel.setLayout(new BoxLayout(outerPanel, BoxLayout.Y_AXIS));
        outerPanel.setBorder(BorderFactory.createTitledBorder("Filter"));

        if (minMap.isEmpty()) {
            JLabel noMeasures = new JLabel("  No numeric measures available");
            noMeasures.setFont(new Font("SansSerif", Font.ITALIC, 10));
            noMeasures.setForeground(new Color(150, 150, 150));
            outerPanel.add(noMeasures);
            outerPanel.add(Box.createVerticalStrut(4));
        } else {
            for (String measure : minMap.keySet()) {
                double min = minMap.get(measure);
                double max = maxMap.get(measure);

                // --- Single row: [Label] [Operator Combo] [Distribution Slider] ---
                JPanel rowPanel = new JPanel(new BorderLayout(4, 0));
                rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

                // Left: Measure label + operator combo in a small fixed-width panel
                JPanel labelOpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                labelOpPanel.setPreferredSize(new Dimension(95, 42));
                JLabel label = new JLabel(measure + ":");
                label.setFont(new Font("SansSerif", Font.BOLD, 11));
                String[] ops = { "\u2265", "\u2264" };
                JComboBox<String> combo = new JComboBox<>(ops);
                combo.setPreferredSize(new Dimension(48, 22));
                combo.setFont(new Font("SansSerif", Font.PLAIN, 10));
                labelOpPanel.add(label);
                labelOpPanel.add(combo);
                rowPanel.add(labelOpPanel, BorderLayout.WEST);

                // Center/Right: Distribution bar (slider with histogram)
                MeasureDistributionBar distBar = new MeasureDistributionBar(min, max);

                // Tooltip: show the real value under the cursor
                distBar.addMouseMotionListener(
                        new java.awt.event.MouseMotionAdapter() {
                    @Override
                    public void mouseMoved(java.awt.event.MouseEvent e) {
                        double realValue = min
                                + (distBar.getValue() / 1000.0) * (max - min);
                        distBar.setToolTipText(String.format(
                                "Threshold: %.4f  (drag to adjust)", realValue));
                    }
                });

                rowPanel.add(distBar, BorderLayout.CENTER);

                outerPanel.add(rowPanel);
                // Small gap between measure rows
                outerPanel.add(Box.createVerticalStrut(3));

                // Register in shared maps and in distributionBars
                sliders.put(measure, distBar);
                operatorCombos.put(measure, combo);
                distributionBars.put(measure, distBar);
            }

            // --- Apply Filter button ---
            outerPanel.add(Box.createVerticalStrut(4));
            JPanel applyRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            applyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
//            JButton applyButton = new JButton("Apply Filter");
//            applyButton.setToolTipText(
//                    "Apply measure filters (combined with any active text search)");
//            applyButton.setMargin(new Insets(2, 8, 2, 8));
            
            

//            JPanel clearRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
//            clearRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            clearAllButton = new JButton("Clear filter(s)");
            clearAllButton.setEnabled(false);
            clearAllButton.setToolTipText("Clear both text search and all measure filters");

            clearAllButton.addActionListener(e -> {
                searchField.setText("");
                for (MeasureDistributionBar bar : sliders.values()) bar.setValue(0);
                for (JComboBox<String> c : operatorCombos.values()) c.setSelectedItem("\u2265");
                patternsPanel.clearSearchAndFilters();
                clearAllButton.setEnabled(false);
                scrollPatternPanelToTop();
                refreshPaginationBar();
                updateStatusBar();
            });

            
//            clearRow.add(clearAllButton);
//            applyRow.add(applyButton);
            applyRow.add(clearAllButton);
            outerPanel.add(applyRow);

            // Auto-apply when the user releases the slider thumb
            for (Map.Entry<String, MeasureDistributionBar> entry : sliders.entrySet()) {
                MeasureDistributionBar bar = entry.getValue();
                bar.addChangeListener(e -> {
                    if (!bar.getValueIsAdjusting()) {
                        boolean anyMoved = sliders.values().stream()
                                .anyMatch(s -> s.getValue() > 0);
                        if (anyMoved) {
                            applySearchAndFilter("", sliders, operatorCombos,
                                    minMap, maxMap);
                            if (clearAllButton != null) clearAllButton.setEnabled(true);
                        } else {
                            patternsPanel.clearSearchAndFilters();
                            if (clearAllButton != null) clearAllButton.setEnabled(false);
                            scrollPatternPanelToTop();
                            refreshPaginationBar();
                            updateStatusBar();
                        }
                    }
                });
            }

            // Auto-apply when operator changes (only if a slider is active)
            for (Map.Entry<String, JComboBox<String>> entry : operatorCombos.entrySet()) {
                JComboBox<String> combo = entry.getValue();
                combo.addActionListener(e -> {
                    boolean anyMoved = sliders.values().stream()
                            .anyMatch(s -> s.getValue() > 0);
                    if (anyMoved) {
                        applySearchAndFilter("", sliders, operatorCombos,
                                minMap, maxMap);
                        if (clearAllButton != null) clearAllButton.setEnabled(true);
                    }
                });
            }

//            // Apply Filter button
//            applyButton.addActionListener(e -> {
//                boolean anyMoved = sliders.values().stream()
//                        .anyMatch(s -> s.getValue() > 0);
//                if (anyMoved) {
//                    applySearchAndFilter("", sliders, operatorCombos, minMap, maxMap);
//                    if (clearAllButton != null) clearAllButton.setEnabled(true);
//                }
//            });
        }

        int filterH = outerPanel.getPreferredSize().height + 10;
        outerPanel.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, filterH));
        outerPanel.setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, filterH));
        outerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, filterH));
        return outerPanel;
    }

    // ---------------------------------------------------------------
    // Summary statistics panel
    // ---------------------------------------------------------------

    private JPanel buildSummaryPanel(Map<String, Double> minMap, Map<String, Double> maxMap) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Statistics"));

        int total = patternsPanel.getTotalPatternCount();
        JLabel totalLabel = new JLabel(String.format(" %d patterns total", total));
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        panel.add(totalLabel);
        panel.add(Box.createVerticalStrut(6));

        Set<String> measures = patternsPanel.getAllMeasures();
        if (!measures.isEmpty()) {
            JLabel mLabel = new JLabel(String.format(" %d measures", measures.size()));
            mLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            mLabel.setForeground(new Color(100, 100, 100));
            panel.add(mLabel);
            panel.add(Box.createVerticalStrut(6));
        }

        if (!minMap.isEmpty()) {
            for (Map.Entry<String, Double> entry : minMap.entrySet()) {
                String measure = entry.getKey();
                double min     = entry.getValue();
                double max     = maxMap.getOrDefault(measure, min);

                JPanel row = new JPanel(new BorderLayout(4, 0));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

                JLabel nameLabel = new JLabel("  " + measure + ":");
                nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                nameLabel.setForeground(new Color(70, 70, 70));
                row.add(nameLabel, BorderLayout.WEST);

                JLabel rangeLabel = new JLabel(
                        String.format("%.4f \u2013 %.4f  ", min, max));
                rangeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                rangeLabel.setForeground(new Color(46, 134, 193));
                rangeLabel.setHorizontalAlignment(JLabel.RIGHT);
                row.add(rangeLabel, BorderLayout.EAST);

                panel.add(row);
                panel.add(Box.createVerticalStrut(2));
            }
        } else {
            JLabel noM = new JLabel("  No numeric measures found");
            noM.setFont(new Font("SansSerif", Font.ITALIC, 10));
            noM.setForeground(new Color(150, 150, 150));
            panel.add(noM);
            panel.add(Box.createVerticalStrut(6));
        }

        panel.add(Box.createVerticalStrut(4));
        int finalH = Math.max(panel.getPreferredSize().height, 80);
        panel.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, finalH));
        panel.setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, finalH));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, finalH));
        return panel;
    }

    // ---------------------------------------------------------------
    // Size Distribution Histogram (sidebar)
    // ---------------------------------------------------------------

    

    // ---------------------------------------------------------------
    // Filter / status helpers
    // ---------------------------------------------------------------

    /**
     * Applies search text and numeric filters. Only sliders moved away from
     * position 0 contribute to the filter (position 0 = no filter for that
     * measure).
     */
    private void applySearchAndFilter(String text,
            Map<String, MeasureDistributionBar> sliders,
            Map<String, JComboBox<String>> operatorCombos,
            Map<String, Double> minMap, Map<String, Double> maxMap) {

        Map<String, Double> thresholds = new LinkedHashMap<>();
        Map<String, String> operators  = new LinkedHashMap<>();

        for (Map.Entry<String, MeasureDistributionBar> entry : sliders.entrySet()) {
            String measure = entry.getKey();
            MeasureDistributionBar bar = entry.getValue();
            int pos = bar.getValue();

            if (pos > 0) {
                double min = minMap.get(measure);
                double max = maxMap.get(measure);
                double val = pos / 1000.0 * (max - min) + min;
                thresholds.put(measure, val);
                operators.put(measure,
                        (String) operatorCombos.get(measure).getSelectedItem());
            }
        }

        patternsPanel.applySearchAndFilters(text, thresholds, operators);
        scrollPatternPanelToTop();
        refreshPaginationBar();
        updateStatusBar();
    }

    /** Updates the status bar, histogram, and distribution bars after any change. */
    private void updateStatusBar() {
        int total   = patternsPanel.getTotalPatternCount();
        int visible = patternsPanel.getNumberOfVisiblePatterns();
        updateCountLabel();
        statusLabel.setText(total != visible
                ? String.format("Showing %d of %d patterns (filtered)", visible, total)
                : buildStatusText());
        if (histogramPanel != null) histogramPanel.updateData(patternsPanel);
        updateDistributionBars();
    }

    private void updateCountLabel() {
        int total   = patternsPanel.getTotalPatternCount();
        int visible = patternsPanel.getNumberOfVisiblePatterns();
        countLabel.setText(total != visible
                ? visible + " / " + total + " patterns "
                : total + " patterns ");
    }

    private String buildStatusText() {
        int total = patternsPanel.getTotalPatternCount();
        Set<String> measures = patternsPanel.getAllMeasures();
        StringBuilder sb = new StringBuilder(" ").append(total).append(" patterns");
        if (!measures.isEmpty())
            sb.append(" \u00B7 ").append(measures.size()).append(" measures");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Menu bar
    // ---------------------------------------------------------------

    private static JMenuBar createMenuBar(JFrame parentFrame,
            PatternsPanel rulesPanel, String originalFilePath) {
        JMenuBar menuBar = new JMenuBar();

        JMenu menuView = new JMenu("View");

        JMenu menuSort = new JMenu("Sort by");
        ButtonGroup sortGroup = new ButtonGroup();
        for (String opt : rulesPanel.getListOfSortingOrders()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(opt);
            sortGroup.add(item);
            item.addActionListener(e -> {
                try {
                    rulesPanel.sortPatterns(opt);
                    rulesPanel.revalidate(); rulesPanel.repaint();
                    if (parentFrame instanceof VisualPatternViewer v) {
                        v.scrollPatternPanelToTop();
                        v.refreshPaginationBar();
                        v.updateStatusBar();
                    }
                } catch (Exception ex) {
                    showErrorDialog(parentFrame, "Sort failed: " + ex.getMessage());
                }
            });
            menuSort.add(item);
        }

        JRadioButtonMenuItem itemStats = new JRadioButtonMenuItem("Statistics");
        itemStats.setSelected(true);
        itemStats.addActionListener(e -> {
            if (parentFrame instanceof VisualPatternViewer v) {
                v.summaryPanel.setVisible(itemStats.isSelected());
                v.sidebarPanel.revalidate(); v.sidebarPanel.repaint();
            }
        });
        menuView.add(itemStats);

        JRadioButtonMenuItem itemSearch = new JRadioButtonMenuItem("Search Panel");
        itemSearch.setSelected(true);
        itemSearch.addActionListener(e -> {
            if (parentFrame instanceof VisualPatternViewer v) {
                v.searchPanel.setVisible(itemSearch.isSelected());
                v.sidebarPanel.revalidate(); v.sidebarPanel.repaint();
            }
        });
        menuView.add(itemSearch);

        JRadioButtonMenuItem itemFilter = new JRadioButtonMenuItem("Filter Panel");
        itemFilter.setSelected(true);
        itemFilter.addActionListener(e -> {
            if (parentFrame instanceof VisualPatternViewer v) {
                v.filterPanel.setVisible(itemFilter.isSelected());
                v.sidebarPanel.revalidate(); v.sidebarPanel.repaint();
            }
        });
        menuView.add(itemFilter);

        JRadioButtonMenuItem itemHist = new JRadioButtonMenuItem("Size Histogram");
        itemHist.setSelected(true);
        itemHist.addActionListener(e -> {
            if (parentFrame instanceof VisualPatternViewer v) {
                v.histogramPanel.setVisible(itemHist.isSelected());
                v.sidebarPanel.revalidate(); v.sidebarPanel.repaint();
            }
        });
        menuView.add(itemHist);

        JRadioButtonMenuItem itemSidebar = new JRadioButtonMenuItem("Sidebar");
        itemSidebar.setSelected(true);
        itemSidebar.addActionListener(e -> {
            if (parentFrame instanceof VisualPatternViewer v) {
                java.awt.Component sc = v.sidebarPanel.getParent();
                if (sc != null) {
                    java.awt.Component sp = sc.getParent();
                    if (sp instanceof JScrollPane jsp)
                        jsp.setVisible(itemSidebar.isSelected());
                    else sc.setVisible(itemSidebar.isSelected());
                    v.getContentPane().revalidate();
                    v.getContentPane().repaint();
                }
            }
        });
        menuView.add(itemSidebar);

        JRadioButtonMenuItem itemPag = new JRadioButtonMenuItem("Pagination Bar");
        itemPag.setSelected(true);
        itemPag.addActionListener(e -> {
            if (parentFrame instanceof VisualPatternViewer v) {
                v.paginationBar.setVisible(itemPag.isSelected());
                v.getContentPane().revalidate();
                v.getContentPane().repaint();
            }
        });
        menuView.add(itemPag);

        menuView.addSeparator();

        JMenu menuLayout = new JMenu("Layout");
        ButtonGroup lg = new ButtonGroup();
        LayoutMode cur = (parentFrame instanceof VisualPatternViewer vf)
                ? vf.mode : LayoutMode.GRID;
        for (LayoutMode lm : LayoutMode.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(lm.toString());
            lg.add(item);
            if (lm == cur) item.setSelected(true);
            item.addActionListener(e -> {
                if (parentFrame instanceof VisualPatternViewer v) v.mode = lm;
                rulesPanel.setLayoutModeWithCallback(lm,
                        () -> configureFrameSize(parentFrame, rulesPanel));
            });
            menuLayout.add(item);
        }
        menuView.add(menuLayout);

        JMenu menuTools = new JMenu("Tools");

        JMenuItem itemOpen = new JMenuItem("Open this file using the system text editor");
        itemOpen.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    File f = new File(originalFilePath);
                    if (f.exists() && f.canRead()) Desktop.getDesktop().open(f);
                    else JOptionPane.showMessageDialog(parentFrame,
                            "Cannot open file:\n" + originalFilePath,
                            "File Not Found", JOptionPane.ERROR_MESSAGE);
                } else JOptionPane.showMessageDialog(parentFrame,
                        "Desktop API not supported.",
                        "Not Supported", JOptionPane.ERROR_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parentFrame,
                        "Failed to open file:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        menuTools.add(itemOpen);

        JMenuItem itemExport = new JMenuItem("Export as PNG\u2026");
        itemExport.addActionListener(e ->
                new ExportDialog(parentFrame, rulesPanel).setVisible(true));
        menuTools.add(itemExport);

        menuTools.addSeparator();

        JMenuItem itemMeasureHist =
                new JMenuItem("View Measure Distribution Histogram");
        itemMeasureHist.addActionListener(e -> {
            try {
                Map<String, List<Double>> mv =
                        MeasureValuesExtractor.extractMeasureValues(rulesPanel);
                if (mv.values().stream().noneMatch(v -> !v.isEmpty())) {
                    JOptionPane.showMessageDialog(parentFrame,
                            "No numeric measure values found.",
                            "No Data", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                new MeasureHistogramDialog((JFrame) parentFrame,
                        rulesPanel, mv).setVisible(true);
            } catch (Exception ex) {
                showErrorDialog(parentFrame,
                        "Failed to create histogram: " + ex.getMessage());
            }
        });
        menuTools.add(itemMeasureHist);

        menuBar.add(menuView);
        menuBar.add(menuSort);
        menuBar.add(menuTools);
        return menuBar;
    }

    private static void configureFrameSize(JFrame frame,
            PatternsPanel patternsPanel) {
        frame.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = (int) (screen.width  * 0.9);
        int maxH = (int) (screen.height * 0.9);
        Dimension pref = frame.getSize();

        if (frame instanceof VisualPatternViewer v && v.sidebarPanel != null) {
            int sh = v.sidebarPanel.getPreferredSize().height;
            Insets ins = frame.getInsets();
            int mh = frame.getJMenuBar() != null
                    ? frame.getJMenuBar().getPreferredSize().height : 0;
            int sbh = v.statusBar    != null
                    ? v.statusBar.getPreferredSize().height    : 0;
            int ph  = v.paginationBar != null
                    ? v.paginationBar.getPreferredSize().height : 0;
            int min = sh + ins.top + ins.bottom + mh + sbh + ph;
            pref = new Dimension(pref.width, Math.max(pref.height, min));
        }

        frame.setSize(Math.min(pref.width, maxW), Math.min(pref.height, maxH));
        frame.setMinimumSize(new Dimension(700, 450));
        frame.setLocationRelativeTo(null);
    }

    private static void showErrorDialog(JFrame parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Error",
                JOptionPane.ERROR_MESSAGE);
    }
}