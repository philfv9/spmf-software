package ca.pfv.spmf.gui.viewers.graph_transition_viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternType;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.SequentialPattern;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.SequentialPatternsReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.StringGapPatternReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.StringNoGapPattenrReader;

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
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Main JFrame window for the Sequential Pattern Transition Network viewer, providing filtering, layout, and export.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class TransitionGraphViewer extends JFrame {

	private static final long serialVersionUID = 1L;

	/** Whether this viewer was launched as a standalone application. */
	private final boolean runAsStandaloneProgram;

	/** The panel that renders the transition graph. */
	private TransitionGraphPanel graphPanel;

	/** The panel that displays the list of patterns. */
	private PatternListPanel patternListPanel;

	/** Full graph built from ALL patterns. Created once per file load. */
	private TransitionGraph fullGraph;

	// Filter controls
	private JSpinner minItemCountSpinner;
	private JSpinner maxItemCountSpinner;
	private JSpinner minItemsetCountSpinner;
	private JSpinner maxItemsetCountSpinner;
	private JTextField requiredItemsField;
	private JSpinner minNodePatternCountSpinner;
	private JSpinner maxNodePatternCountSpinner;
	private JSpinner minEdgeWeightSpinner;
	private JSpinner maxEdgeWeightSpinner;
	private JTextField nodeSearchField;

	// Display toggle checkboxes
	private JCheckBox showPatternCountBadgeCheckBox;
	private JCheckBox showSelfLoopsCheckBox;
	private JCheckBox showSequentialEdgesCheckBox;
	private JCheckBox showCoOccurrenceEdgesCheckBox;
	private JCheckBox showEdgeWeightsCheckBox;

	/** Status bar label. */
	private JLabel statusLabel;

	/** Number of iterations for the force-directed layout algorithm. */
	private int layoutIterations = 300;

	/** Repulsion strength for the force-directed layout. */
	private double repulsionStrength = 5000.0;

	/** Attraction strength for the force-directed layout. */
	private double attractionStrength = 0.01;

	/** Cached list of filtered nodes from the last filter application. */
	private List<TransitionGraph.GraphNode> cachedFilteredNodes;

	/** Cached list of filtered edges from the last filter application. */
	private List<TransitionGraph.GraphEdge> cachedFilteredEdges;

	/** Cached list of filtered patterns from the last filter application. */
	private List<TransitionGraph.StoredPattern> cachedFilteredPatterns;

	/** Maximum total item count across all patterns (cached). */
	private int cachedMaxItemCount = 0;

	/** Maximum itemset count across all patterns (cached). */
	private int cachedMaxItemsetCount = 0;

	/** Flag to prevent recursive selection event handling. */
	private boolean suppressSelectionEvents = false;

	/** Whether the loaded data contains multi-item itemsets. */
	private boolean hasMultiItemItemsets = false;

	/** Whether the loaded data contains co-occurrence edges. */
	private boolean hasCoOccurrenceEdges = false;

	/** The left control panel. */
	private JPanel leftPanel;

	/** Scroll pane wrapping the left control panel. */
	private JScrollPane filterScroll;

	/** The node legend panel shown in the left sidebar. */
	private NodeLegendPanel nodeLegendPanel;

	// =========================================================================
	// Constructors
	// =========================================================================

	public TransitionGraphViewer() { this(false); }

	public TransitionGraphViewer(boolean runAsStandaloneProgram) {
		this.runAsStandaloneProgram = runAsStandaloneProgram;

		if (runAsStandaloneProgram) {
			setTitle("SPMF v" + Main.SPMF_VERSION + " - Sequential Pattern Transition Network");
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		} else {
			setTitle("SPMF - Sequential Pattern Transition Network");
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		}

		setSize(1400, 850);
		setLocationRelativeTo(null);

		createFilterComponents();
		initComponents();
	}

	public TransitionGraphViewer(String filePath, PatternType patternType,
			boolean runAsStandaloneProgram) {
		this(runAsStandaloneProgram);
		loadFile(filePath, patternType);
	}

	public TransitionGraphViewer(List<SequentialPattern> patterns,
			boolean runAsStandaloneProgram) {
		this(runAsStandaloneProgram);
		loadPatterns(patterns);
	}

	// =========================================================================
	// Component creation
	// =========================================================================

	private void createFilterComponents() {
		minItemCountSpinner = createIntSpinner(0, 0, 10000,
				"Minimum total number of items across all itemsets.");
		maxItemCountSpinner = createIntSpinner(10000, 0, 10000,
				"Maximum total number of items across all itemsets.");
		minItemsetCountSpinner = createIntSpinner(0, 0, 10000,
				"Minimum number of itemsets (steps) in a pattern.");
		maxItemsetCountSpinner = createIntSpinner(10000, 0, 10000,
				"Maximum number of itemsets (steps) in a pattern.");

		requiredItemsField = new JTextField();
		requiredItemsField.setToolTipText(
				"Only show patterns containing all listed items, e.g.: 1,3,5");

		minNodePatternCountSpinner = createIntSpinner(1, 0, 100000,
				"Minimum number of patterns a node must appear in to be visible.");
		maxNodePatternCountSpinner = createIntSpinner(100000, 0, 100000,
				"Maximum number of patterns a node must appear in to be visible.");

		minEdgeWeightSpinner = new JSpinner(
				new SpinnerNumberModel(0.0, 0.0, 1000000.0, 1.0));
		minEdgeWeightSpinner.setToolTipText("Minimum edge weight to be visible.");
		maxEdgeWeightSpinner = new JSpinner(
				new SpinnerNumberModel(1000000.0, 0.0, 1000000.0, 1.0));
		maxEdgeWeightSpinner.setToolTipText("Maximum edge weight to be visible.");

		nodeSearchField = new JTextField();
		nodeSearchField.setToolTipText(
				"Type a node label and press Enter or click Find to locate it.");
		nodeSearchField.addActionListener(e -> searchAndFocusNode());

		showPatternCountBadgeCheckBox = new JCheckBox("Show pattern count badges", false);
		showPatternCountBadgeCheckBox.setToolTipText(
				"Show the number of matching patterns on each node.");
		showPatternCountBadgeCheckBox.addActionListener(
				e -> graphPanel.setShowPatternCountBadge(
						showPatternCountBadgeCheckBox.isSelected()));

		showSelfLoopsCheckBox = new JCheckBox("Show self-loops", true);
		showSelfLoopsCheckBox.setToolTipText("Show/hide self-loop edges.");
		showSelfLoopsCheckBox.addActionListener(
				e -> graphPanel.setShowSelfLoops(showSelfLoopsCheckBox.isSelected()));

		showSequentialEdgesCheckBox = new JCheckBox("Show sequential edges", true);
		showSequentialEdgesCheckBox.setToolTipText("Show/hide sequential transition edges.");
		showSequentialEdgesCheckBox.addActionListener(
				e -> graphPanel.setShowSequentialEdges(
						showSequentialEdgesCheckBox.isSelected()));

		showCoOccurrenceEdgesCheckBox = new JCheckBox("Show co-occurrence edges", true);
		showCoOccurrenceEdgesCheckBox.setToolTipText("Show/hide co-occurrence edges.");
		showCoOccurrenceEdgesCheckBox.addActionListener(
				e -> graphPanel.setShowCoOccurrenceEdges(
						showCoOccurrenceEdgesCheckBox.isSelected()));

		showEdgeWeightsCheckBox = new JCheckBox("Show edge weights", false);
		showEdgeWeightsCheckBox.setToolTipText("Show/hide weight labels on edges.");
		showEdgeWeightsCheckBox.addActionListener(
				e -> graphPanel.setShowEdgeWeights(showEdgeWeightsCheckBox.isSelected()));
	}

	private JSpinner createIntSpinner(int value, int min, int max, String tooltip) {
		JSpinner sp = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
		sp.setEditor(new JSpinner.NumberEditor(sp, "#"));
		if (tooltip != null) sp.setToolTipText(tooltip);
		return sp;
	}

	private void initComponents() {
		setLayout(new BorderLayout(0, 0));
		setJMenuBar(createMenuBar());

		leftPanel = buildLeftPanel();
		filterScroll = new JScrollPane(leftPanel);
		filterScroll.setPreferredSize(new Dimension(250, 0));
		filterScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		graphPanel = new TransitionGraphPanel();
		graphPanel.setNodeSelectionListener(new TransitionGraphPanel.NodeSelectionListener() {
			@Override public void onNodeSelected(String nodeLabel) {
				if (suppressSelectionEvents) return;
				suppressSelectionEvents = true;
				patternListPanel.filterByItem(nodeLabel);
				patternListPanel.clearSelection();
				graphPanel.clearPatternHighlight();
				suppressSelectionEvents = false;
			}
			@Override public void onNodeDeselected() {
				if (suppressSelectionEvents) return;
				suppressSelectionEvents = true;
				patternListPanel.showAll();
				patternListPanel.clearSelection();
				graphPanel.clearPatternHighlight();
				suppressSelectionEvents = false;
			}
		});

		graphPanel.setEdgeSelectionListener(new TransitionGraphPanel.EdgeSelectionListener() {
			@Override public void onEdgeSelected(String edgeKey) {
				if (suppressSelectionEvents) return;
				suppressSelectionEvents = true;
				patternListPanel.filterByEdge(edgeKey);
				patternListPanel.clearSelection();
				graphPanel.clearPatternHighlight();
				suppressSelectionEvents = false;
			}
			@Override public void onEdgeDeselected() {
				if (suppressSelectionEvents) return;
				suppressSelectionEvents = true;
				patternListPanel.showAll();
				patternListPanel.clearSelection();
				graphPanel.clearPatternHighlight();
				suppressSelectionEvents = false;
			}
		});

		JScrollPane graphScroll = new JScrollPane(graphPanel,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		graphScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		patternListPanel = new PatternListPanel();
		patternListPanel.setPreferredSize(new Dimension(380, 0));
		patternListPanel.setPatternSelectionListener(
				new PatternListPanel.PatternSelectionListener() {
					@Override public void onPatternSelected(TransitionGraph.StoredPattern pattern) {
						if (suppressSelectionEvents) return;
						suppressSelectionEvents = true;
						graphPanel.clearSelection();
						graphPanel.setPatternHighlight(pattern.getEdgeKeys(), pattern.getContainedItems());
						suppressSelectionEvents = false;
					}
					@Override public void onPatternDeselected() {
						if (suppressSelectionEvents) return;
						suppressSelectionEvents = true;
						graphPanel.clearPatternHighlight();
						suppressSelectionEvents = false;
					}
				});

		JSplitPane rightSplit = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, graphScroll, patternListPanel);
		rightSplit.setResizeWeight(0.65);
		rightSplit.setDividerLocation(700);

		JSplitPane mainSplit = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, filterScroll, rightSplit);
		mainSplit.setResizeWeight(0.0);
		mainSplit.setDividerLocation(250);

		add(mainSplit, BorderLayout.CENTER);

		JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statusBar.setBorder(new EmptyBorder(2, 5, 2, 5));
		statusLabel = new JLabel("Ready. Use File \u2192 Open to load a sequential patterns file.");
		statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		statusBar.add(statusLabel);
		add(statusBar, BorderLayout.SOUTH);
	}

	// =========================================================================
	// Left panel
	// =========================================================================

	private JPanel buildLeftPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(new EmptyBorder(6, 6, 6, 6));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0; gbc.insets = new Insets(0, 0, 4, 0);
		int row = 0;

		gbc.gridy = row++; panel.add(wrapTitled("Search", buildSearchContent()), gbc);
		gbc.gridy = row++; panel.add(wrapTitled("Filters", buildFiltersContent()), gbc);
		gbc.gridy = row++; panel.add(wrapTitled("Display Options", buildDisplayOptionsContent()), gbc);
		gbc.gridy = row++; panel.add(wrapTitled("Edge Legend", buildEdgeLegendContent()), gbc);

		// Node size legend panel (under Edge Legend)
		nodeLegendPanel = new NodeLegendPanel();
		JPanel nodeLegendWrapper = wrapTitled("Node Size Legend", nodeLegendPanel);
		gbc.gridy = row++;
		panel.add(nodeLegendWrapper, gbc);

		gbc.gridy = row; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
		panel.add(new JPanel(), gbc);
		return panel;
	}

	private JPanel wrapTitled(String title, JPanel content) {
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBorder(new TitledBorder(title));
		wrapper.add(content, BorderLayout.NORTH);
		return wrapper;
	}

	private JPanel buildSearchContent() {
		JPanel p = new JPanel(new GridBagLayout());
		GridBagConstraints g = gbc(0, 0, 2, 1.0);
		p.add(new JLabel("Node label:"), g);
		g.gridy = 1;
		JPanel row = new JPanel(new GridBagLayout());
		GridBagConstraints sg = gbc(0, 0, 1, 1.0);
		sg.insets = new Insets(0, 0, 0, 4);
		row.add(nodeSearchField, sg);
		JButton findBtn = new JButton("Find");
		findBtn.setToolTipText("Find and center on the node with this label.");
		findBtn.addActionListener(e -> searchAndFocusNode());
		sg.gridx = 1; sg.weightx = 0; sg.insets = new Insets(0, 0, 0, 0);
		row.add(findBtn, sg);
		p.add(row, g);
		return p;
	}

	private void searchAndFocusNode() {
		String text = nodeSearchField.getText().trim();
		if (text.isEmpty() || fullGraph == null) return;

		TransitionGraph.GraphNode node = findNodeByText(text);
		if (node == null) { statusLabel.setText("Node '" + text + "' not found."); return; }

		boolean visible = false;
		if (cachedFilteredNodes != null) {
			for (TransitionGraph.GraphNode fn : cachedFilteredNodes) {
				if (fn.getItemLabel().equals(node.getItemLabel())) { visible = true; break; }
			}
		}
		if (!visible) {
			statusLabel.setText("Node '" + node.getItemLabel() + "' found but hidden by current filters.");
			return;
		}

		graphPanel.setSelectedNode(node.getItemLabel());
		graphPanel.centerOnNode(node.getItemLabel());

		suppressSelectionEvents = true;
		patternListPanel.filterByItem(node.getItemLabel());
		patternListPanel.clearSelection();
		graphPanel.clearPatternHighlight();
		suppressSelectionEvents = false;

		statusLabel.setText("Centered on node: " + node.getItemLabel());
	}

	private TransitionGraph.GraphNode findNodeByText(String text) {
		TransitionGraph.GraphNode node = fullGraph.getNodes().get(text);
		if (node != null) return node;
		String lower = text.toLowerCase();
		TransitionGraph.GraphNode partial = null;
		for (TransitionGraph.GraphNode n : fullGraph.getNodes().values()) {
			String label = n.getItemLabel();
			if (label.equalsIgnoreCase(text)) return n;
			if (partial == null && label.toLowerCase().contains(lower)) partial = n;
		}
		return partial;
	}

	private JPanel buildFiltersContent() {
		JPanel p = new JPanel(new GridBagLayout());
		GridBagConstraints g = gbc(0, 0, 2, 1.0);
		int row = 0;

		if (hasMultiItemItemsets) {
			g.gridy = row++;
			JLabel hdr = new JLabel("Item count");
			hdr.setFont(hdr.getFont().deriveFont(Font.BOLD));
			p.add(hdr, g);
			row = addMinMaxRow(p, row, "Min:", minItemCountSpinner, "Max:", maxItemCountSpinner);
			g.gridy = row++; g.insets = new Insets(3, 0, 3, 0);
			p.add(new JSeparator(), g); g.insets = new Insets(1, 0, 1, 0);
		} else {
			minItemCountSpinner.setValue(0);
			maxItemCountSpinner.setValue(10000);
		}

		g.gridy = row++; g.gridwidth = 2;
		JLabel isHdr = new JLabel("Itemset count");
		isHdr.setFont(isHdr.getFont().deriveFont(Font.BOLD));
		p.add(isHdr, g);
		row = addMinMaxRow(p, row, "Min:", minItemsetCountSpinner, "Max:", maxItemsetCountSpinner);

		g.gridy = row++; g.gridwidth = 2; g.insets = new Insets(3, 0, 3, 0);
		p.add(new JSeparator(), g); g.insets = new Insets(1, 0, 1, 0);

		g.gridy = row++;
		p.add(new JLabel("Required items (comma-sep):"), g);
		g.gridy = row++;
		p.add(requiredItemsField, g);

		g.gridy = row++; g.insets = new Insets(3, 0, 3, 0);
		p.add(new JSeparator(), g); g.insets = new Insets(1, 0, 1, 0);

		// Node weight section with min and max
		g.gridy = row++; g.gridwidth = 2;
		JLabel nwHdr = new JLabel("Node weight");
		nwHdr.setFont(nwHdr.getFont().deriveFont(Font.BOLD));
		p.add(nwHdr, g);
		row = addMinMaxRow(p, row, "Min:", minNodePatternCountSpinner, "Max:", maxNodePatternCountSpinner);

		g.gridy = row++; g.gridwidth = 2; g.insets = new Insets(3, 0, 3, 0);
		p.add(new JSeparator(), g); g.insets = new Insets(1, 0, 1, 0);

		// Edge weight section with min and max
		g.gridy = row++; g.gridwidth = 2;
		JLabel ewHdr = new JLabel("Edge weight");
		ewHdr.setFont(ewHdr.getFont().deriveFont(Font.BOLD));
		p.add(ewHdr, g);
		row = addMinMaxRow(p, row, "Min:", minEdgeWeightSpinner, "Max:", maxEdgeWeightSpinner);

		g.gridy = row; g.gridwidth = 2; g.insets = new Insets(8, 0, 0, 0);

		JPanel btns = new JPanel(new GridBagLayout());
		GridBagConstraints bg = gbc(0, 0, 1, 1.0);
		bg.insets = new Insets(0, 0, 0, 4);
		JButton applyBtn = new JButton("Apply Filters");
		applyBtn.setToolTipText("Apply filters to show/hide patterns, nodes, and edges.");
		applyBtn.addActionListener(e -> applyFilters());
		btns.add(applyBtn, bg);

		JButton resetBtn = new JButton("Reset");
		resetBtn.setToolTipText("Reset all filters to default values.");
		resetBtn.addActionListener(e -> resetFilters());
		bg.gridx = 1; bg.insets = new Insets(0, 0, 0, 0);
		btns.add(resetBtn, bg);

		p.add(btns, g);
		return p;
	}

	private int addMinMaxRow(JPanel p, int row, String minLbl, JSpinner minSp,
			String maxLbl, JSpinner maxSp) {
		GridBagConstraints g = gbc(0, row, 1, 0);
		g.insets = new Insets(1, 0, 1, 4); p.add(new JLabel(minLbl), g);
		g.gridx = 1; g.weightx = 1.0; g.insets = new Insets(1, 0, 1, 0); p.add(minSp, g);
		row++;
		g.gridy = row; g.gridx = 0; g.weightx = 0; g.insets = new Insets(1, 0, 1, 4);
		p.add(new JLabel(maxLbl), g);
		g.gridx = 1; g.weightx = 1.0; g.insets = new Insets(1, 0, 1, 0); p.add(maxSp, g);
		return row + 1;
	}

	private int addLabelSpinnerRow(JPanel p, int row, String label, JSpinner sp) {
		GridBagConstraints g = gbc(0, row, 1, 0);
		g.insets = new Insets(1, 0, 1, 4); p.add(new JLabel(label), g);
		g.gridx = 1; g.weightx = 1.0; g.insets = new Insets(1, 0, 1, 0); p.add(sp, g);
		return row + 1;
	}

	private JPanel buildDisplayOptionsContent() {
		JPanel p = new JPanel(new GridBagLayout());
		GridBagConstraints g = gbc(0, 0, 1, 1.0);
		int row = 0;
		g.gridy = row++; p.add(showPatternCountBadgeCheckBox, g);
		g.gridy = row++; p.add(showEdgeWeightsCheckBox, g);
		g.gridy = row++; p.add(showSelfLoopsCheckBox, g);
		g.gridy = row++; p.add(showSequentialEdgesCheckBox, g);
		if (hasCoOccurrenceEdges) { g.gridy = row++; p.add(showCoOccurrenceEdgesCheckBox, g); }
		return p;
	}

	private JPanel buildEdgeLegendContent() {
		JPanel p = new JPanel(new GridBagLayout());
		GridBagConstraints g = gbc(0, 0, 1, 1.0);
		int row = 0;
		g.gridy = row++;
		p.add(legendLabel("\u2500\u2500\u2500\u25B6 Sequential transition", new Color(150, 150, 150)), g);
		if (hasCoOccurrenceEdges) {
			g.gridy = row++;
			p.add(legendLabel("- - - - Co-occurrence", new Color(100, 170, 100)), g);
		}
		g.gridy = row++; p.add(legendLabel("\u25CF Green edge = incoming", new Color(50, 180, 50)), g);
		g.gridy = row++; p.add(legendLabel("\u25CF Red edge = outgoing", new Color(220, 50, 50)), g);
		g.gridy = row++; p.add(legendLabel("\u25CF Orange = selected node/edge", new Color(255, 140, 0)), g);
		g.gridy = row++; p.add(legendLabel("\u25CF Purple = pattern highlight", new Color(180, 50, 180)), g);
		return p;
	}

	private JLabel legendLabel(String text, Color color) {
		JLabel l = new JLabel(text);
		l.setFont(new Font("SansSerif", Font.PLAIN, 10));
		l.setForeground(color);
		return l;
	}

	private static GridBagConstraints gbc(int x, int y, int width, double weightx) {
		GridBagConstraints g = new GridBagConstraints();
		g.gridx = x; g.gridy = y; g.gridwidth = width; g.weightx = weightx;
		g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;
		g.insets = new Insets(0, 0, 1, 0);
		return g;
	}

	private void rebuildLeftPanel() {
		leftPanel = buildLeftPanel();
		filterScroll.setViewportView(leftPanel);
		filterScroll.revalidate();
	}

	// =========================================================================
	// Node Legend Panel (displayed in sidebar)
	// =========================================================================

	/**
	 * A small panel that draws the node-size legend (large circle = max patterns,
	 * small circle = min patterns) in the left sidebar.
	 */
	private class NodeLegendPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		private static final int MIN_NODE_RADIUS = 12;
		private static final int MAX_NODE_RADIUS = 28; // slightly smaller for sidebar
		private static final Color NODE_COLOR = new Color(70, 130, 180);

		private int minPC = 0;
		private int maxPC = 1;

		NodeLegendPanel() {
			setOpaque(false);
		}

		void updateLegendData(int minPatternCount, int maxPatternCount) {
			this.minPC = minPatternCount;
			this.maxPC = Math.max(maxPatternCount, 1);
			revalidate();
			repaint();
		}

		@Override
		public Dimension getPreferredSize() {
			boolean single = (minPC == maxPC);
			int h = 10 + MAX_NODE_RADIUS * 2 + (single ? 0 : 8 + MIN_NODE_RADIUS * 2) + 10;
			return new Dimension(200, h);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Font lf = new Font("SansSerif", Font.PLAIN, 10);
			g2.setFont(lf);

			boolean single = (minPC == maxPC);
			int cx = 10 + MAX_NODE_RADIUS;
			int cy1 = 10 + MAX_NODE_RADIUS;

			// Draw large circle (max)
			g2.setColor(NODE_COLOR);
			g2.fill(new Ellipse2D.Double(cx - MAX_NODE_RADIUS, cy1 - MAX_NODE_RADIUS,
					2 * MAX_NODE_RADIUS, 2 * MAX_NODE_RADIUS));
			g2.setColor(NODE_COLOR.darker());
			g2.draw(new Ellipse2D.Double(cx - MAX_NODE_RADIUS, cy1 - MAX_NODE_RADIUS,
					2 * MAX_NODE_RADIUS, 2 * MAX_NODE_RADIUS));
			g2.setColor(Color.DARK_GRAY);
			g2.drawString(maxPC + " patterns", cx + MAX_NODE_RADIUS + 6,
					cy1 + g2.getFontMetrics().getAscent() / 2);

			if (!single) {
				int cy2 = cy1 + MAX_NODE_RADIUS + 8 + MIN_NODE_RADIUS;
				g2.setColor(NODE_COLOR);
				g2.fill(new Ellipse2D.Double(cx - MIN_NODE_RADIUS, cy2 - MIN_NODE_RADIUS,
						2 * MIN_NODE_RADIUS, 2 * MIN_NODE_RADIUS));
				g2.setColor(NODE_COLOR.darker());
				g2.draw(new Ellipse2D.Double(cx - MIN_NODE_RADIUS, cy2 - MIN_NODE_RADIUS,
						2 * MIN_NODE_RADIUS, 2 * MIN_NODE_RADIUS));
				g2.setColor(Color.DARK_GRAY);
				g2.drawString(minPC + " patterns", cx + MIN_NODE_RADIUS + 6,
						cy2 + g2.getFontMetrics().getAscent() / 2);
			}

			g2.dispose();
		}
	}

	/**
	 * Update the node legend panel with current min/max pattern counts from
	 * the displayed nodes.
	 */
	private void updateNodeLegend() {
		if (nodeLegendPanel == null) return;
		List<TransitionGraph.GraphNode> nodes = graphPanel.getDisplayNodes();
		if (nodes == null || nodes.isEmpty()) {
			nodeLegendPanel.updateLegendData(0, 1);
			return;
		}
		int maxPC = graphPanel.getCachedMaxNodePC();
		int minPC = maxPC;
		for (TransitionGraph.GraphNode n : nodes) {
			if (n.getPatternCount() < minPC) minPC = n.getPatternCount();
		}
		nodeLegendPanel.updateLegendData(minPC, maxPC);
	}

	// =========================================================================
	// Filters
	// =========================================================================

	private void resetFilters() {
		if (fullGraph != null) autoSuggestFilterValues();
		else {
			minItemCountSpinner.setValue(0); maxItemCountSpinner.setValue(10000);
			minItemsetCountSpinner.setValue(0); maxItemsetCountSpinner.setValue(10000);
			minNodePatternCountSpinner.setValue(1); maxNodePatternCountSpinner.setValue(100000);
			minEdgeWeightSpinner.setValue(0.0); maxEdgeWeightSpinner.setValue(1000000.0);
		}
		requiredItemsField.setText("");
		applyFilters();
	}

	private void autoSuggestFilterValues() {
		if (fullGraph == null) return;
		setSpinnerRange(minItemCountSpinner, 0, cachedMaxItemCount, 0);
		setSpinnerRange(maxItemCountSpinner, 0, cachedMaxItemCount, cachedMaxItemCount);
		setSpinnerRange(minItemsetCountSpinner, 0, cachedMaxItemsetCount, 0);
		setSpinnerRange(maxItemsetCountSpinner, 0, cachedMaxItemsetCount, cachedMaxItemsetCount);

		int maxPC = fullGraph.getMaxNodePatternCount();
		setSpinnerRange(minNodePatternCountSpinner, 0, Math.max(maxPC, 1), 1);
		setSpinnerRange(maxNodePatternCountSpinner, 0, Math.max(maxPC, 1), Math.max(maxPC, 1));

		double maxEW = fullGraph.getMaxEdgeWeight();
		minEdgeWeightSpinner.setModel(new SpinnerNumberModel(0.0, 0.0, Math.max(maxEW, 1.0), 1.0));
		maxEdgeWeightSpinner.setModel(new SpinnerNumberModel(Math.max(maxEW, 1.0), 0.0, Math.max(maxEW, 1.0), 1.0));
	}

	private void cachePatternStats() {
		cachedMaxItemCount = 0; cachedMaxItemsetCount = 0;
		if (fullGraph == null) return;
		for (TransitionGraph.StoredPattern p : fullGraph.getStoredPatterns()) {
			if (p.getTotalItemCount() > cachedMaxItemCount) cachedMaxItemCount = p.getTotalItemCount();
			if (p.getItemsetCount() > cachedMaxItemsetCount) cachedMaxItemsetCount = p.getItemsetCount();
		}
	}

	private void setSpinnerRange(JSpinner sp, int min, int max, int value) {
		sp.setModel(new SpinnerNumberModel(value, min, Math.max(max, 1), 1));
		sp.setEditor(new JSpinner.NumberEditor(sp, "#"));
	}

	private void applyFilters() {
		if (fullGraph == null) return;
		commitAllSpinners();
		applyFiltersInternal();
		computePatternCountsPerNode();
		graphPanel.setGraph(fullGraph, cachedFilteredNodes, cachedFilteredEdges);
		patternListPanel.setPatterns(cachedFilteredPatterns, fullGraph);
		updateNodeLegend();
		updateStatusBar();
	}

	private void applyFiltersInternal() {
		int minItems = intVal(minItemCountSpinner);
		int maxItems = intVal(maxItemCountSpinner);
		int minItemsets = intVal(minItemsetCountSpinner);
		int maxItemsets = intVal(maxItemsetCountSpinner);
		Set<String> reqItems = parseRequiredItems();

		// First pass: filter patterns by item count, itemset count, required items
		List<TransitionGraph.StoredPattern> preliminaryPatterns = fullGraph.getFilteredPatterns(
				minItems, maxItems, minItemsets, maxItemsets, reqItems);

		// Collect active items and edge keys from preliminary patterns
		Set<String> activeItems = new HashSet<>();
		Set<String> activeEdgeKeys = new HashSet<>();
		for (TransitionGraph.StoredPattern p : preliminaryPatterns) {
			activeItems.addAll(p.getContainedItems());
			activeEdgeKeys.addAll(p.getEdgeKeys());
		}

		fullGraph.setWeightMeasure(TransitionGraph.WeightMeasure.SUPPORT);
		fullGraph.recomputeWeights();

		int minPC = intVal(minNodePatternCountSpinner);
		int maxPC = intVal(maxNodePatternCountSpinner);
		double minEW = ((Number) minEdgeWeightSpinner.getValue()).doubleValue();
		double maxEW = ((Number) maxEdgeWeightSpinner.getValue()).doubleValue();

		// Filter nodes by pattern count range and active items
		cachedFilteredNodes = fullGraph.getFilteredNodes(minPC, maxPC, activeItems);

		// Build set of visible node labels
		Set<String> visibleLabels = new HashSet<>((int) (cachedFilteredNodes.size() * 1.4) + 1);
		for (TransitionGraph.GraphNode n : cachedFilteredNodes) visibleLabels.add(n.getItemLabel());

		// Filter edges by weight range, visible nodes, and active edge keys
		cachedFilteredEdges = fullGraph.getFilteredEdges(minEW, maxEW, visibleLabels, activeEdgeKeys);

		// Build set of visible edge keys
		Set<String> visibleEdgeKeys = new HashSet<>((int) (cachedFilteredEdges.size() * 1.4) + 1);
		for (TransitionGraph.GraphEdge e : cachedFilteredEdges) visibleEdgeKeys.add(e.getKey());

		// Second pass: remove patterns that have NO visible nodes or whose edges
		// are entirely hidden by the node/edge weight filters.
		// A pattern is kept only if ALL of its contained items are visible nodes
		// AND at least one of its edges is visible (or it has no edges, i.e. single-item pattern).
		cachedFilteredPatterns = new java.util.ArrayList<>();
		for (TransitionGraph.StoredPattern p : preliminaryPatterns) {
			// Check that all items in this pattern are among visible nodes
			boolean allNodesVisible = true;
			for (String item : p.getContainedItems()) {
				if (!visibleLabels.contains(item)) {
					allNodesVisible = false;
					break;
				}
			}
			if (!allNodesVisible) continue;

			// For patterns with edges, check that at least one edge is visible
			Set<String> patternEdgeKeys = p.getEdgeKeys();
			if (!patternEdgeKeys.isEmpty()) {
				boolean anyEdgeVisible = false;
				for (String ek : patternEdgeKeys) {
					if (visibleEdgeKeys.contains(ek)) {
						anyEdgeVisible = true;
						break;
					}
				}
				if (!anyEdgeVisible) continue;
			}

			cachedFilteredPatterns.add(p);
		}
	}

	private void computePatternCountsPerNode() {
		Map<String, Integer> counts = new HashMap<>();
		if (cachedFilteredPatterns != null) {
			for (TransitionGraph.StoredPattern p : cachedFilteredPatterns) {
				for (String item : p.getContainedItems()) {
					counts.merge(item, 1, Integer::sum);
				}
			}
		}
		graphPanel.setPatternCountsPerNode(counts);
	}

	// =========================================================================
	// Menu
	// =========================================================================

	private JMenuBar createMenuBar() {
		JMenuBar bar = new JMenuBar();

		JMenu file = new JMenu("File");
		JMenuItem open = new JMenuItem("Open File...");
		open.addActionListener(e -> openFileDialog());
		file.add(open);
		file.addSeparator();
		JMenuItem expPng = new JMenuItem("Export as PNG...");
		expPng.addActionListener(e -> exportPNG());
		file.add(expPng);
		JMenuItem expCsv = new JMenuItem("Save Graph Data (CSV)...");
		expCsv.addActionListener(e -> exportGraphData());
		file.add(expCsv);
		file.addSeparator();
		if (runAsStandaloneProgram) {
			JMenuItem exit = new JMenuItem("Exit"); exit.addActionListener(e -> System.exit(0)); file.add(exit);
		} else {
			JMenuItem close = new JMenuItem("Close"); close.addActionListener(e -> dispose()); file.add(close);
		}
		bar.add(file);

		JMenu view = new JMenu("View");
		JMenuItem resetZoom = new JMenuItem("Reset Zoom");
		resetZoom.addActionListener(e -> graphPanel.resetView());
		view.add(resetZoom);
		view.addSeparator();
		JMenuItem recomp = new JMenuItem("Recompute Layout");
		recomp.setToolTipText("Refine node positions using the force-directed algorithm.");
		recomp.addActionListener(e -> recomputeLayout(false));
		view.add(recomp);
		JMenuItem fresh = new JMenuItem("Recompute Layout (from scratch)");
		fresh.setToolTipText("Randomize all positions and compute a new layout.");
		fresh.addActionListener(e -> recomputeLayout(true));
		view.add(fresh);
		view.addSeparator();
		JMenuItem params = new JMenuItem("Layout Parameters...");
		params.addActionListener(e -> showLayoutParametersDialog());
		view.add(params);
		bar.add(view);

		// Help menu
		JMenu help = new JMenu("Help");
		JMenuItem howToUse = new JMenuItem("How to Use...");
		howToUse.addActionListener(e -> showHelpDialog());
		help.add(howToUse);
		bar.add(help);

		return bar;
	}

	// =========================================================================
	// Help dialog
	// =========================================================================

	/**
	 * Show a help dialog that explains basic instructions for using the viewer,
	 * including mouse controls and general usage tips.
	 */
	private void showHelpDialog() {
		JDialog dlg = new JDialog(this, "How to Use", true);
		dlg.setLocationRelativeTo(this);

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(new EmptyBorder(15, 15, 15, 15));

		JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
		textArea.setBackground(content.getBackground());

		StringBuilder sb = new StringBuilder();
		sb.append("=== Sequential Pattern Transition Network Viewer ===\n\n");

		sb.append("GETTING STARTED\n");
		sb.append("1. Use File \u2192 Open to load a sequential patterns file.\n");
		sb.append("2. The graph will be displayed with nodes (items) and edges (transitions).\n");
		sb.append("3. Use the left panel to filter patterns and customize the display.\n\n");

		sb.append("MOUSE CONTROLS\n");
		sb.append("\u2022 Left-click a node: Select the node and filter patterns containing it.\n");
		sb.append("\u2022 Left-click an edge: Select the edge and filter patterns containing it.\n");
		sb.append("\u2022 Left-click a pattern (right panel): Highlight the pattern's nodes and edges on the graph.\n");
		sb.append("\u2022 Left-click empty area: Clear the current selection.\n");
		sb.append("\u2022 Left-drag a node: Move the node to a new position.\n");
		sb.append("\u2022 Right-drag (or middle-drag): Pan the view.\n");
		sb.append("\u2022 Scroll wheel: Zoom in or out.\n");
		sb.append("\u2022 Right-click a pattern (right panel): Copy the pattern text.\n\n");

		sb.append("FILTERING\n");
		sb.append("\u2022 Use the Filters section to restrict which patterns, nodes, and edges are shown.\n");
		sb.append("\u2022 Click 'Apply Filters' to update the view after changing filter values.\n");
		sb.append("\u2022 Click 'Reset' to restore all filters to their defaults.\n\n");

		sb.append("DISPLAY OPTIONS\n");
		sb.append("\u2022 Toggle badges, edge weights, self-loops, and edge types using the checkboxes.\n\n");

		sb.append("LAYOUT\n");
		sb.append("\u2022 Use View \u2192 Recompute Layout to refine node positions.\n");
		sb.append("\u2022 Use View \u2192 Layout Parameters to adjust the force-directed algorithm settings.\n\n");

		sb.append("EXPORT\n");
		sb.append("\u2022 File \u2192 Export as PNG: Save the graph as a high-resolution image.\n");
		sb.append("\u2022 File \u2192 Save Graph Data (CSV): Export nodes, edges, and patterns as CSV.\n");

		textArea.setText(sb.toString());
		textArea.setCaretPosition(0);

		JScrollPane scroll = new JScrollPane(textArea);
		scroll.setPreferredSize(new Dimension(520, 420));
		content.add(scroll, BorderLayout.CENTER);

		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener(e -> dlg.dispose());
		btns.add(closeBtn);
		content.add(btns, BorderLayout.SOUTH);

		dlg.setContentPane(content);
		dlg.pack();
		dlg.setMinimumSize(new Dimension(400, 300));
		dlg.setVisible(true);
	}

	// =========================================================================
	// Dialogs
	// =========================================================================

	private void showLayoutParametersDialog() {
		JDialog dlg = new JDialog(this, "Layout Parameters", true);
		dlg.setLocationRelativeTo(this);
		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(new EmptyBorder(15, 15, 15, 15));

		JPanel fields = new JPanel(new GridBagLayout());
		GridBagConstraints g = new GridBagConstraints();
		g.insets = new Insets(4, 4, 4, 4); g.fill = GridBagConstraints.HORIZONTAL;

		JSpinner iterSp = new JSpinner(new SpinnerNumberModel(layoutIterations, 10, 5000, 50));
		iterSp.setEditor(new JSpinner.NumberEditor(iterSp, "#"));
		g.gridx = 0; g.gridy = 0; g.weightx = 0; fields.add(new JLabel("Iterations:"), g);
		g.gridx = 1; g.weightx = 1.0; fields.add(iterSp, g);

		JSpinner repSp = new JSpinner(new SpinnerNumberModel(repulsionStrength, 100.0, 100000.0, 500.0));
		g.gridx = 0; g.gridy = 1; g.weightx = 0; fields.add(new JLabel("Repulsion strength:"), g);
		g.gridx = 1; g.weightx = 1.0; fields.add(repSp, g);

		JSpinner attrSp = new JSpinner(new SpinnerNumberModel(attractionStrength, 0.001, 1.0, 0.005));
		attrSp.setEditor(new JSpinner.NumberEditor(attrSp, "0.000"));
		g.gridx = 0; g.gridy = 2; g.weightx = 0; fields.add(new JLabel("Attraction strength:"), g);
		g.gridx = 1; g.weightx = 1.0; fields.add(attrSp, g);

		content.add(fields, BorderLayout.CENTER);

		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		JButton ok = new JButton("OK");
		ok.addActionListener(e -> {
			layoutIterations = ((Number) iterSp.getValue()).intValue();
			repulsionStrength = ((Number) repSp.getValue()).doubleValue();
			attractionStrength = ((Number) attrSp.getValue()).doubleValue();
			dlg.dispose();
		});
		JButton cancel = new JButton("Cancel"); cancel.addActionListener(e -> dlg.dispose());
		btns.add(ok); btns.add(cancel);
		content.add(btns, BorderLayout.SOUTH);

		dlg.setContentPane(content); dlg.pack();
		dlg.setMinimumSize(dlg.getPreferredSize());
		dlg.setVisible(true);
	}

	// =========================================================================
	// File I/O
	// =========================================================================

	private void openFileDialog() {
		JFileChooser ch = new JFileChooser();
		ch.setDialogTitle("Open Sequential Patterns File");
		if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File file = ch.getSelectedFile();
		String[] fmts = {"Standard SPMF (with -1)", "String with gaps (spaces)", "String no gaps (no spaces)"};
		String sel = (String) JOptionPane.showInputDialog(this,
				"Select the pattern file format:", "Pattern Format",
				JOptionPane.QUESTION_MESSAGE, null, fmts, fmts[0]);
		if (sel == null) return;
		PatternType type;
		if (sel.equals(fmts[1])) type = PatternType.STRING_SEQUENTIAL_PATTERNS;
		else if (sel.equals(fmts[2])) type = PatternType.STRING_SEQUENTIAL_PATTERNS_NO_GAP;
		else type = PatternType.SEQUENTIAL_PATTERNS;
		loadFile(file.getAbsolutePath(), type);
	}

	public void loadFile(String filePath, PatternType patternType) {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		statusLabel.setText("Loading file...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			private Exception error;
			private boolean detectedMultiItem, detectedCoOccurrence;

			@Override protected Void doInBackground() {
				try {
					SequentialPatternsReader reader;
					switch (patternType) {
					case STRING_SEQUENTIAL_PATTERNS: reader = new StringGapPatternReader(filePath); break;
					case STRING_SEQUENTIAL_PATTERNS_NO_GAP: reader = new StringNoGapPattenrReader(filePath); break;
					default: reader = new SequentialPatternsReader(filePath); break;
					}
					fullGraph = new TransitionGraph(reader);
					detectedMultiItem = fullGraph.hasMultiItemItemsets();
					detectedCoOccurrence = fullGraph.hasCoOccurrenceEdges();
					cachePatternStats();
					applyFiltersInternal();
					runLayoutInternal(false);
				} catch (IOException ex) { error = ex; }
				return null;
			}

			@Override protected void done() {
				setCursor(Cursor.getDefaultCursor());
				if (error != null) {
					JOptionPane.showMessageDialog(TransitionGraphViewer.this,
							"Error loading file: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					statusLabel.setText("Error loading file.");
					return;
				}
				boolean rebuild = (hasMultiItemItemsets != detectedMultiItem)
						|| (hasCoOccurrenceEdges != detectedCoOccurrence);
				hasMultiItemItemsets = detectedMultiItem;
				hasCoOccurrenceEdges = detectedCoOccurrence;

				autoSuggestFilterValues();
				if (rebuild) rebuildLeftPanel();

				computePatternCountsPerNode();
				graphPanel.setGraph(fullGraph, cachedFilteredNodes, cachedFilteredEdges);
				patternListPanel.setPatterns(cachedFilteredPatterns, fullGraph);
				updateNodeLegend();
				updateStatusBar();

				String name = new File(filePath).getName();
				setTitle((runAsStandaloneProgram
						? "SPMF v" + Main.SPMF_VERSION + " - Transition Network: "
						: "SPMF - Transition Network: ") + name);
			}
		};
		worker.execute();
	}

	public void loadPatterns(List<SequentialPattern> patterns) {
		if (patterns == null || patterns.isEmpty()) { statusLabel.setText("No patterns provided."); return; }

		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		statusLabel.setText("Building graph from patterns...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			private boolean detectedMultiItem, detectedCoOccurrence;

			@Override protected Void doInBackground() {
				fullGraph = new TransitionGraph(patterns);
				detectedMultiItem = fullGraph.hasMultiItemItemsets();
				detectedCoOccurrence = fullGraph.hasCoOccurrenceEdges();
				cachePatternStats();
				applyFiltersInternal();
				runLayoutInternal(false);
				return null;
			}

			@Override protected void done() {
				setCursor(Cursor.getDefaultCursor());
				boolean rebuild = (hasMultiItemItemsets != detectedMultiItem)
						|| (hasCoOccurrenceEdges != detectedCoOccurrence);
				hasMultiItemItemsets = detectedMultiItem;
				hasCoOccurrenceEdges = detectedCoOccurrence;

				autoSuggestFilterValues();
				if (rebuild) rebuildLeftPanel();

				computePatternCountsPerNode();
				graphPanel.setGraph(fullGraph, cachedFilteredNodes, cachedFilteredEdges);
				patternListPanel.setPatterns(cachedFilteredPatterns, fullGraph);
				updateNodeLegend();
				updateStatusBar();

				setTitle((runAsStandaloneProgram
						? "SPMF v" + Main.SPMF_VERSION + " - Transition Network: "
						: "SPMF - Transition Network: ") + "(in-memory patterns)");
			}
		};
		worker.execute();
	}

	// =========================================================================
	// Layout
	// =========================================================================

	private void recomputeLayout(boolean fromScratch) {
		if (fullGraph == null || cachedFilteredNodes == null) return;
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		statusLabel.setText("Computing layout...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() { runLayoutInternal(fromScratch); return null; }
			@Override protected void done() {
				setCursor(Cursor.getDefaultCursor());
				graphPanel.recalculateAndRevalidate();
				updateStatusBar();
			}
		};
		worker.execute();
	}

	private void runLayoutInternal(boolean fromScratch) {
		if (cachedFilteredNodes == null || cachedFilteredEdges == null) return;
		ForceDirectedLayout layout = new ForceDirectedLayout();

		// Scale canvas size based on node count for better spacing
		int baseW = Math.max(graphPanel.getWidth(), 800);
		int baseH = Math.max(graphPanel.getHeight(), 600);
		int nodeCount = cachedFilteredNodes.size();
		if (nodeCount > 30) {
			double scaleFactor = 1.0 + (nodeCount - 30) * 0.02;
			scaleFactor = Math.min(scaleFactor, 4.0);
			baseW = (int) (baseW * scaleFactor);
			baseH = (int) (baseH * scaleFactor);
		}

		layout.setCanvasSize(baseW, baseH);
		layout.setRepulsionStrength(repulsionStrength);
		layout.setAttractionStrength(attractionStrength);
		if (fromScratch) layout.layoutFromScratch(cachedFilteredNodes, cachedFilteredEdges, layoutIterations);
		else layout.layout(cachedFilteredNodes, cachedFilteredEdges, layoutIterations);
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private Set<String> parseRequiredItems() {
		String text = requiredItemsField.getText().trim();
		if (text.isEmpty()) return null;
		Set<String> items = new HashSet<>();
		for (String s : text.split(",")) { String t = s.trim(); if (!t.isEmpty()) items.add(t); }
		return items.isEmpty() ? null : items;
	}

	private void commitAllSpinners() {
		commitSpinner(minItemCountSpinner); commitSpinner(maxItemCountSpinner);
		commitSpinner(minItemsetCountSpinner); commitSpinner(maxItemsetCountSpinner);
		commitSpinner(minNodePatternCountSpinner); commitSpinner(maxNodePatternCountSpinner);
		commitSpinner(minEdgeWeightSpinner); commitSpinner(maxEdgeWeightSpinner);
	}

	private void commitSpinner(JSpinner sp) { try { sp.commitEdit(); } catch (Exception ignored) {} }

	private int intVal(JSpinner sp) { return ((Number) sp.getValue()).intValue(); }

	private void updateStatusBar() {
		if (fullGraph == null) return;
		int seq = 0, co = 0;
		if (cachedFilteredEdges != null) {
			for (TransitionGraph.GraphEdge e : cachedFilteredEdges) {
				if (e.getEdgeType() == TransitionGraph.EdgeType.SEQUENTIAL) seq++; else co++;
			}
		}
		int nc = cachedFilteredNodes != null ? cachedFilteredNodes.size() : 0;
		int pc = cachedFilteredPatterns != null ? cachedFilteredPatterns.size() : 0;

		StringBuilder sb = new StringBuilder();
		sb.append("Nodes: ").append(nc);
		sb.append(" | Seq. edges: ").append(seq);
		if (hasCoOccurrenceEdges) sb.append(" | Co-occ. edges: ").append(co);
//		sb.append(" | Patterns: ").append(pc).append(" / ").append(fullGraph.getTotalPatternCount());
		sb.append(" | Max items: ").append(cachedMaxItemCount);
		sb.append(" | Max itemsets: ").append(cachedMaxItemsetCount);
		statusLabel.setText(sb.toString());
	}

	// =========================================================================
	// Export
	// =========================================================================

	private void exportPNG() {
		JFileChooser ch = new JFileChooser();
		ch.setDialogTitle("Export Graph as PNG");
		ch.setSelectedFile(new File("transition_graph.png"));
		if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		try {
			graphPanel.showExportDialog(this, ch.getSelectedFile());
			JOptionPane.showMessageDialog(this, "Exported to " + ch.getSelectedFile().getName(),
					"Export Successful", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			if ("CANCELLED".equals(ex.getMessage())) return;
			JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void exportGraphData() {
		if (fullGraph == null) { JOptionPane.showMessageDialog(this, "No graph to export.", "Info", JOptionPane.INFORMATION_MESSAGE); return; }
		JFileChooser ch = new JFileChooser();
		ch.setDialogTitle("Save Graph Data");
		ch.setSelectedFile(new File("transition_graph_data.csv"));
		if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		try {
			TransitionGraphExporter.exportToFile(fullGraph, cachedFilteredNodes,
					cachedFilteredEdges, cachedFilteredPatterns, ch.getSelectedFile());
			JOptionPane.showMessageDialog(this, "Saved to " + ch.getSelectedFile().getName(),
					"Save Successful", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	// =========================================================================
	// Main
	// =========================================================================

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			TransitionGraphViewer viewer = new TransitionGraphViewer(true);
			viewer.setVisible(true);
		});
	}
}