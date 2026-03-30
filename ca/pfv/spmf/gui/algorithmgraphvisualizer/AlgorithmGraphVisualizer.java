package ca.pfv.spmf.gui.algorithmgraphvisualizer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.gui.MainWindow;

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
 * Algorithm Graph Visualizer - Interactive visualization tool for SPMF
 * algorithms
 * 
 * This class provides a comprehensive graph-based visualization of algorithms
 * in the SPMF data mining library. It allows users to: - View algorithms as
 * nodes in a force-directed graph - Filter and search algorithms by various
 * criteria - Visualize relationships between algorithms based on input/output
 * compatibility - Explore algorithm details in an interactive panel - Navigate
 * large graphs using a minimap - Export visualizations as images
 * 
 * Architecture: - Main window (AlgorithmGraphVisualizer): Manages UI components
 * and coordination - GraphPanel: Handles graph rendering and layout algorithms
 * - AlgorithmDetailPanel: Displays detailed information about selected
 * algorithms - MinimapPanel: Provides overview navigation for large graphs -
 * AlgorithmNode: Data model for individual algorithm nodes
 * 
 * Copyright (c) 2025 Philippe Fournier-Viger
 * 
 * @author Philippe Fournier-Viger
 * @version 2.0 - Enhanced with resource management, detail panel, and minimap
 */
public class AlgorithmGraphVisualizer extends JFrame {

	private static final long serialVersionUID = 1L;

	// ==================== WINDOW DIMENSION CONSTANTS ====================
	/** Default width of the main window in pixels */
	private static final int WINDOW_WIDTH = 1400;

	/** Default height of the main window in pixels */
	private static final int WINDOW_HEIGHT = 900;

	/** Minimum allowed window width to ensure usability */
	private static final int MIN_WINDOW_WIDTH = 1000;

	/** Minimum allowed window height to ensure usability */
	private static final int MIN_WINDOW_HEIGHT = 700;

	/** Font used for section titles and headings */
	private static final Font TITLE_FONT = new Font("Tahoma", Font.BOLD, 16);

	/** Font used for monospaced detail text */
	private static final Font DETAIL_FONT = new Font("Monospaced", Font.PLAIN, 11);

	// ==================== UI COMPONENTS ====================

	// Split panes (stored for divider location setting after visibility)
	/** Split pane containing graph and minimap */
	private JSplitPane graphSplitPane;

	/** Split pane containing graph/minimap and detail panel */
	private JSplitPane mainSplitPane;

	/** Main panel that renders the algorithm graph */
	private GraphPanel graphPanel;

	/** Panel displaying detailed information about selected algorithm */
	private AlgorithmDetailPanel detailPanel;

	/** Minimap panel providing overview and navigation for large graphs */
	private MinimapPanel minimapPanel;

	// Control components
	/** Text field for searching/filtering algorithms by name */
	private JTextField searchField;

	/** Dropdown to filter algorithms by category */
	private JComboBox<String> categoryFilter;

	/** Dropdown to select graph layout algorithm */
	private JComboBox<String> layoutComboBox;

	/** Checkbox to toggle node label visibility */
	private JCheckBox showLabelsCheckBox;

	/** Checkbox to toggle connection line visibility */
	private JCheckBox showConnectionsCheckBox;

	/** Checkbox to enable clustering by input file types */
	private JCheckBox clusterByInputCheckBox;

	/** Checkbox to enable clustering by output file types */
	private JCheckBox clusterByOutputCheckBox;

	/** Checkbox to hide tool/utility algorithms from view */
	private JCheckBox excludeToolsCheckBox;

	/** Button to manually trigger graph refresh */
	private JButton refreshButton;

	/** Button to export current graph view as PNG image */
	private JButton exportButton;

	/** Status bar label showing current filter statistics */
	private JLabel statusLabel;

	// ==================== DATA STRUCTURES ====================
	/** Singleton instance of the algorithm manager from SPMF */
	private AlgorithmManager algorithmManager;

	/** Master list of all algorithm nodes in the graph */
	private List<AlgorithmNode> algorithmNodes;

	/** Map of category names to their corresponding algorithm nodes */
	private Map<String, List<AlgorithmNode>> categoryMap;

	// ==================== RESOURCE MANAGEMENT ====================
	/**
	 * List of all action listeners registered by this component. Tracked for proper
	 * cleanup during disposal.
	 */
	private List<ActionListener> registeredListeners;

	/**
	 * List of all mouse listeners registered by this component. Tracked for proper
	 * cleanup during disposal.
	 */
	private List<MouseAdapter> registeredMouseListeners;

	/**
	 * Flag indicating whether resources have been disposed. Prevents
	 * double-disposal and use-after-dispose bugs.
	 */
	private boolean disposed = false;

	/**
	 * Constructor for AlgorithmGraphVisualizer
	 * 
	 * Initializes the complete visualization system including: 1. Algorithm data
	 * loading from SPMF 2. UI component creation and layout 3. Event listener
	 * registration 4. Resource tracking for cleanup 5. Initial graph layout
	 * computation
	 * 
	 * @param runAsStandalone true if running as standalone application (enables
	 *                        EXIT_ON_CLOSE), false if embedded in another
	 *                        application (enables DISPOSE_ON_CLOSE)
	 */
	public AlgorithmGraphVisualizer(boolean runAsStandalone) {
		// Initialize resource tracking lists before any listener registration
		registeredListeners = new ArrayList<>();
		registeredMouseListeners = new ArrayList<>();

		// Load algorithm data from SPMF framework
		initializeAlgorithmManager();

		// Setup main window properties (size, icon, etc.)
		initializeFrame();

		// Load and organize algorithm data
		initializeData();

		// Create all UI components
		initializeComponents();

		// Arrange components in the window
		layoutComponents();
		
		// Added
		filterAlgorithms();

		// Wire up event handlers
		setupEventListeners();

		// Configure window close behavior based on execution mode
		if (runAsStandalone) {
			// Standalone: exit JVM when window closes
			this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		} else {
			// Embedded: only dispose this window
			this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		}

		// Add window listener for proper resource cleanup
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// Ensure all resources are properly released
				disposeResources();
			}
		});

		// Finalize window setup
		this.setTitle("SPMF Algorithm Graph Visualizer");
		this.setLocationRelativeTo(null); // Center on screen
		this.setVisible(true);

		// Apply initial layout and set divider locations AFTER window is visible
		SwingUtilities.invokeLater(() -> {
			// Now that components are sized, set divider locations
			if (graphSplitPane != null) {
				graphSplitPane.setDividerLocation(0.85); // Use proportional location
			}
			if (mainSplitPane != null) {
				mainSplitPane.setDividerLocation(0.70); // Use proportional location
			}

			// Apply initial graph layout
			applyLayout();

			// Initialize minimap after layout is complete
			if (minimapPanel != null) {
				minimapPanel.updateMinimap();
			}
		});
	}

	/**
	 * Initialize the algorithm manager singleton from SPMF framework
	 * 
	 * The AlgorithmManager provides access to all registered algorithms in SPMF,
	 * including their metadata, input/output types, and categories.
	 * 
	 * @throws RuntimeException if the AlgorithmManager cannot be initialized
	 *                          (typically indicates SPMF configuration issue)
	 */
	private void initializeAlgorithmManager() {
		try {
			algorithmManager = AlgorithmManager.getInstance();
		} catch (Exception e) {
			e.printStackTrace();
			// Fatal error - cannot proceed without algorithm data
			throw new RuntimeException(
					"Failed to initialize AlgorithmManager. " + "Ensure SPMF framework is properly configured.", e);
		}
	}

	/**
	 * Initialize the main JFrame window properties
	 * 
	 * Sets up: - Window dimensions and constraints - Application icon (if
	 * available) - Resizability options
	 */
	private void initializeFrame() {
		// Attempt to load application icon
		try {
			setIconImage(
					Toolkit.getDefaultToolkit().getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));
		} catch (Exception e) {
			// Icon loading is non-critical - continue without it
			System.err.println("Warning: Could not load application icon: " + e.getMessage());
		}

		// Set initial window size
		setSize(WINDOW_WIDTH, WINDOW_HEIGHT);

		// Allow window resizing
		setResizable(true);

		// Enforce minimum size to prevent UI elements from becoming unusable
		setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT));
	}

	/**
	 * Initialize data structures for storing algorithm information
	 * 
	 * Creates empty collections that will be populated by loadAlgorithms()
	 */
	private void initializeData() {
		// Master list of all algorithm nodes
		algorithmNodes = new ArrayList<>();

		// Category-based organization for hierarchical layouts
		categoryMap = new HashMap<>();

		// Populate data structures from SPMF
		loadAlgorithms();
	}

	/**
	 * Load algorithms from SPMF AlgorithmManager and create node representations
	 * 
	 * This method: 1. Retrieves the complete list of algorithms from SPMF 2.
	 * Identifies category headers vs. actual algorithms 3. Creates AlgorithmNode
	 * objects for each algorithm 4. Organizes nodes by category for quick filtering
	 * 
	 * SPMF's algorithm list format: - Category names appear as standalone strings -
	 * Algorithm names follow their category - DescriptionOfAlgorithm objects
	 * contain metadata
	 */
	private void loadAlgorithms() {
		// Get complete list including categories and algorithms
		// Parameters: (includeCategories=true, includeTools=true,
		// includeAlgorithms=true)
		List<String> algorithmList = algorithmManager.getListOfAlgorithmsAsString(true, true, true, true, true);

		// Track current category as we iterate through the list
		String currentCategory = "Uncategorized";

		for (String name : algorithmList) {
			// Attempt to get algorithm metadata
			DescriptionOfAlgorithm description = algorithmManager.getDescriptionOfAlgorithm(name);

			if (description == null) {
				// This entry is a category header, not an algorithm
				currentCategory = name;

				// Ensure category exists in map
				categoryMap.putIfAbsent(currentCategory, new ArrayList<>());
			} else {
				// This is an actual algorithm - create node representation
				AlgorithmNode node = new AlgorithmNode(name, description, currentCategory);

				// Add to master list
				algorithmNodes.add(node);

				// Add to category-specific list
				categoryMap.putIfAbsent(currentCategory, new ArrayList<>());
				categoryMap.get(currentCategory).add(node);
			}
		}

		System.out.println(
				"Loaded " + algorithmNodes.size() + " algorithms across " + categoryMap.size() + " categories");
	}

	/**
	 * Initialize all UI components
	 * 
	 * Creates instances of all interactive components but does not arrange them in
	 * the layout (that's done in layoutComponents())
	 */
	private void initializeComponents() {
		// ========== Main Visualization Panels ==========

		// Graph panel - renders the node-link diagram
		graphPanel = new GraphPanel();

		// Detail panel - shows information about selected algorithm
		detailPanel = new AlgorithmDetailPanel();

		// Minimap panel - provides overview and navigation
		minimapPanel = new MinimapPanel();

		// ========== Search and Filter Controls ==========

		// Search field for text-based filtering
		searchField = new JTextField(15);
		searchField.setToolTipText("Search algorithms by name (case-insensitive)");

		// Category filter dropdown
		categoryFilter = new JComboBox<>();
		categoryFilter.addItem("All Categories");
		// Populate with all available categories
		for (String category : categoryMap.keySet()) {
			categoryFilter.addItem(category);
		}
		categoryFilter.setToolTipText("Filter algorithms by category");

		// ========== Layout Algorithm Selector ==========

		layoutComboBox = new JComboBox<>(new String[] { "Force-Directed", // Physics-based spring layout
				"Circular", // Arrange in circle
				"Grid", // Uniform grid layout
				"Hierarchical by Category" // Grouped by category
		});
		layoutComboBox.setToolTipText("Select graph layout algorithm");

		// ========== Display Option Checkboxes ==========

		showLabelsCheckBox = new JCheckBox("Show Labels", true);
		showLabelsCheckBox.setToolTipText("Toggle algorithm name labels");

		showConnectionsCheckBox = new JCheckBox("Show Connections", true);
		showConnectionsCheckBox.setToolTipText("Toggle connection lines between related algorithms");

		clusterByInputCheckBox = new JCheckBox("Cluster by Input", true);
		clusterByInputCheckBox.setToolTipText("Group algorithms with same input types");

		clusterByOutputCheckBox = new JCheckBox("Cluster by Output", true);
		clusterByOutputCheckBox.setToolTipText("Group algorithms with same output types");

		excludeToolsCheckBox = new JCheckBox("Exclude Tools", true);
		excludeToolsCheckBox.setToolTipText("Hide tool/converter algorithms from view");


		// ========== Action Buttons ==========

		refreshButton = new JButton("Refresh Graph");
		refreshButton.setToolTipText("Reload algorithms and reset layout");

		exportButton = new JButton("Export as PNG");
		exportButton.setToolTipText("Save current graph view as image file");

		// ========== Status Display ==========

		statusLabel = new JLabel("Algorithms: " + algorithmNodes.size());
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		statusLabel.setToolTipText("Current filter status");
		
	}

	/**
	 * Arrange all UI components in the main window layout
	 * 
	 * Layout structure: ┌─────────────────────────────────────────┐ │ Control Panel
	 * (NORTH) │ ├─────────────────────┬───────────────────┤ │ │ │ │ Graph Panel │
	 * Detail Panel │ │ (with minimap) │ │ │ │ │
	 * ├─────────────────────┴───────────────────┤ │ Status Bar (SOUTH) │
	 * └─────────────────────────────────────────┘
	 */
	private void layoutComponents() {
		setLayout(new BorderLayout(5, 5));

		// ========== Top Control Panel ==========
		JPanel controlPanel = createControlPanel();
		add(controlPanel, BorderLayout.NORTH);

		// ========== Center: Graph + Detail Split ==========
		// Create vertical split pane for graph and minimap
		JSplitPane graphSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createGraphPanel(), // Top: main graph
				minimapPanel // Bottom: minimap
		);

		// DON'T set divider location here - wait until components are sized
		// graphSplitPane.setDividerLocation(GRAPH_DIVIDER_LOCATION); // REMOVED
		graphSplitPane.setResizeWeight(0.85); // Give 85% space to main graph

		// Set minimum sizes to prevent complete collapse
		graphPanel.setMinimumSize(new Dimension(400, 300));
		minimapPanel.setMinimumSize(new Dimension(200, 100));

		// Create horizontal split pane for graph/minimap and detail panel
		JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphSplitPane, // Left: graph + minimap
				detailPanel // Right: detail panel
		);

		// DON'T set divider location here either
		// mainSplitPane.setDividerLocation(MAIN_DIVIDER_LOCATION); // REMOVED
		mainSplitPane.setResizeWeight(0.7); // Give 70% space to graph

		// Set minimum sizes for detail panel
		detailPanel.setMinimumSize(new Dimension(250, 200));

		add(mainSplitPane, BorderLayout.CENTER);

		// ========== Bottom Status Bar ==========
		JPanel statusPanel = new JPanel(new BorderLayout());
		statusPanel.setBorder(BorderFactory.createEtchedBorder());
		statusPanel.add(statusLabel, BorderLayout.WEST);
		add(statusPanel, BorderLayout.SOUTH);

		// ========== Store references for later initialization ==========
		// We'll set divider locations after the window is visible
		this.graphSplitPane = graphSplitPane;
		this.mainSplitPane = mainSplitPane;
	}

	/**
	 * Create the scrollable graph panel container
	 * 
	 * The graph panel can grow very large (2000x2000), so we wrap it in a scroll
	 * pane to allow navigation of large graphs.
	 * 
	 * @return JScrollPane containing the graph panel
	 */
	private JScrollPane createGraphPanel() {
		// Set large preferred size to accommodate many nodes
		graphPanel.setPreferredSize(new Dimension(2000, 2000));

		// Wrap in scroll pane with always-visible scrollbars
		JScrollPane scrollPane = new JScrollPane(graphPanel);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

		return scrollPane;
	}

	/**
	 * Create the control panel with proper layout to prevent component overlap
	 * 
	 * Panel structure: Row 1: Title Row 2: Search, Category filter, Layout selector
	 * Row 3: Display option checkboxes Row 4: Action buttons
	 * 
	 * @return fully configured control panel
	 */
	private JPanel createControlPanel() {
		// Main container with vertical layout
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setBorder(BorderFactory.createTitledBorder("Controls"));

		// ========== Title Row ==========
		JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JLabel titleLabel = new JLabel("Algorithm Graph Visualizer");
		titleLabel.setFont(TITLE_FONT);
		titlePanel.add(titleLabel);
		mainPanel.add(titlePanel);

		// ========== Controls Row 1: Search and Filters ==========
		JPanel controlsRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		controlsRow1.add(new JLabel("Search:"));
		controlsRow1.add(searchField);
		controlsRow1.add(new JLabel("Category:"));
		controlsRow1.add(categoryFilter);
		controlsRow1.add(new JLabel("Layout:"));
		controlsRow1.add(layoutComboBox);
		mainPanel.add(controlsRow1);

		// ========== Controls Row 2: Display Options ==========
		JPanel controlsRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		controlsRow2.add(showLabelsCheckBox);
		controlsRow2.add(showConnectionsCheckBox);
		controlsRow2.add(clusterByInputCheckBox);
		controlsRow2.add(clusterByOutputCheckBox);
		controlsRow2.add(excludeToolsCheckBox);
		mainPanel.add(controlsRow2);

		// ========== Controls Row 3: Action Buttons ==========
		JPanel controlsRow3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		controlsRow3.add(refreshButton);
		controlsRow3.add(exportButton);
		mainPanel.add(controlsRow3);

		return mainPanel;
	}

	/**
	 * Setup event listeners for all interactive components
	 * 
	 * All listeners are tracked in registeredListeners and registeredMouseListeners
	 * for proper cleanup during disposal.
	 */
	private void setupEventListeners() {
		// ========== Search Field ==========
		// Trigger filtering when user presses Enter
		ActionListener searchListener = e -> filterAlgorithms();
		searchField.addActionListener(searchListener);
		registeredListeners.add(searchListener);

		// ========== Category Filter ==========
		// Trigger filtering when selection changes
		ActionListener categoryListener = e -> filterAlgorithms();
		categoryFilter.addActionListener(categoryListener);
		registeredListeners.add(categoryListener);

		// ========== Layout Selector ==========
		// Apply new layout when selection changes
		ActionListener layoutListener = e -> applyLayout();
		layoutComboBox.addActionListener(layoutListener);
		registeredListeners.add(layoutListener);

		// ========== Display Option Checkboxes ==========

		// Show/hide labels
		ActionListener labelsListener = e -> {
			graphPanel.setShowLabels(showLabelsCheckBox.isSelected());
			graphPanel.repaint();
		};
		showLabelsCheckBox.addActionListener(labelsListener);
		registeredListeners.add(labelsListener);

		// Show/hide connections
		ActionListener connectionsListener = e -> {
			graphPanel.setShowConnections(showConnectionsCheckBox.isSelected());
			graphPanel.repaint();
		};
		showConnectionsCheckBox.addActionListener(connectionsListener);
		registeredListeners.add(connectionsListener);

		// Cluster by input types - requires relayout
		ActionListener inputClusterListener = e -> {
			graphPanel.setClusterByInput(clusterByInputCheckBox.isSelected());
			applyLayout();
		};
		clusterByInputCheckBox.addActionListener(inputClusterListener);
		registeredListeners.add(inputClusterListener);

		// Cluster by output types - requires relayout
		ActionListener outputClusterListener = e -> {
			graphPanel.setClusterByOutput(clusterByOutputCheckBox.isSelected());
			applyLayout();
		};
		clusterByOutputCheckBox.addActionListener(outputClusterListener);
		registeredListeners.add(outputClusterListener);

		// Exclude tools - requires filtering
		ActionListener excludeToolsListener = e -> filterAlgorithms();
		excludeToolsCheckBox.addActionListener(excludeToolsListener);
		registeredListeners.add(excludeToolsListener);

		// ========== Action Buttons ==========

		// Refresh button - reload all data
		ActionListener refreshListener = e -> refreshGraph();
		refreshButton.addActionListener(refreshListener);
		registeredListeners.add(refreshListener);

		// Export button - save as PNG
		ActionListener exportListener = e -> exportAsPNG();
		exportButton.addActionListener(exportListener);
		registeredListeners.add(exportListener);
	}

	/**
	 * Export the current graph view as a PNG image file
	 * 
	 * This method: 1. Prompts user for save location 2. Stops any running
	 * animations 3. Renders the graph panel to a BufferedImage 4. Writes the image
	 * to disk 5. Displays success/error message
	 */
	private void exportAsPNG() {
		// ========== File Selection Dialog ==========
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export Graph as PNG");
		fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
		fileChooser.setSelectedFile(new File("algorithm_graph.png"));

		int userSelection = fileChooser.showSaveDialog(this);

		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fileChooser.getSelectedFile();

			// Ensure .png extension
			if (!fileToSave.getName().toLowerCase().endsWith(".png")) {
				fileToSave = new File(fileToSave.getAbsolutePath() + ".png");
			}

			try {
				// ========== Stop Animations ==========
				// Layout animation must be stopped to get a clean snapshot
				graphPanel.stopLayoutTimer();

				// ========== Create Image Buffer ==========
				int width = graphPanel.getWidth();
				int height = graphPanel.getHeight();

				// Validate dimensions
				if (width <= 0 || height <= 0) {
					throw new IOException("Invalid panel dimensions: " + width + "x" + height);
				}

				// Create ARGB image for transparency support
				BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2d = image.createGraphics();

				// ========== Render Panel to Image ==========
				// Enable high-quality rendering
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

				// Paint the panel onto the image
				graphPanel.paint(g2d);
				g2d.dispose();

				// ========== Write to File ==========
				ImageIO.write(image, "PNG", fileToSave);

				// ========== Success Notification ==========
				JOptionPane.showMessageDialog(this, "Graph exported successfully to:\n" + fileToSave.getAbsolutePath(),
						"Export Successful", JOptionPane.INFORMATION_MESSAGE);

				statusLabel.setText("Exported to: " + fileToSave.getName());

			} catch (IOException ex) {
				// ========== Error Handling ==========
				ex.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error exporting graph:\n" + ex.getMessage(), "Export Failed",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 * Filter algorithms based on current search text, category, and options
	 * 
	 * This method applies all active filters: - Search text (case-insensitive
	 * substring match) - Category selection - "Exclude Tools" option
	 * 
	 * Updates node visibility and refreshes the display.
	 */
	private void filterAlgorithms() {
		// ========== Get Filter Criteria ==========
		String searchText = searchField.getText().toLowerCase().trim();
		String selectedCategory = (String) categoryFilter.getSelectedItem();
		boolean excludeTools = excludeToolsCheckBox.isSelected();

		// ========== Apply Filters to Each Node ==========
		int visibleCount = 0;
		for (AlgorithmNode node : algorithmNodes) {
			boolean visible = true;

			// Category filter
			if (selectedCategory != null && !"All Categories".equals(selectedCategory)) {
				visible = node.getCategory().equals(selectedCategory);
			}

			// Search text filter (case-insensitive)
			if (visible && !searchText.isEmpty()) {
				visible = node.getName().toLowerCase().contains(searchText);
			}

			// Tool exclusion filter
			if (visible && excludeTools) {
				visible = !node.isTool();
			}

			// Update node visibility
			node.setVisible(visible);
			if (visible)
				visibleCount++;
		}

		// ========== Update UI ==========
		// Update status bar
		statusLabel.setText("Showing: " + visibleCount + " / " + algorithmNodes.size() + " algorithms");

		// Reapply layout with new visibility
		applyLayout();

		// Update minimap to reflect changes
		if (minimapPanel != null) {
			minimapPanel.updateMinimap();
		}
	}

	/**
	 * Apply the currently selected layout algorithm to the graph
	 * 
	 * Delegates to the appropriate layout method in GraphPanel based on the
	 * selected layout type.
	 */
	private void applyLayout() {
		String layout = (String) layoutComboBox.getSelectedItem();

		if (layout != null) {
			switch (layout) {
			case "Force-Directed":
				// Physics-based spring layout
				graphPanel.applyForceDirectedLayout();
				break;
			case "Circular":
				// Arrange nodes in a circle
				graphPanel.applyCircularLayout();
				break;
			case "Grid":
				// Uniform grid arrangement
				graphPanel.applyGridLayout();
				break;
			case "Hierarchical by Category":
				// Group nodes by category in rows
				graphPanel.applyHierarchicalLayout();
				break;
			}
		}

		// Refresh display
		graphPanel.repaint();

		// Update minimap to reflect new positions
		if (minimapPanel != null) {
			minimapPanel.updateMinimap();
		}
	}

	/**
	 * Refresh the entire graph by reloading algorithm data
	 * 
	 * This method: 1. Clears all existing data structures 2. Reloads algorithms
	 * from SPMF 3. Reapplies the current layout 4. Updates all UI components
	 */
	private void refreshGraph() {
		// ========== Clear Existing Data ==========
		algorithmNodes.clear();
		categoryMap.clear();

		// ========== Reload from SPMF ==========
		loadAlgorithms();

		// ========== Update Category Filter ==========
		// Remove old categories
		categoryFilter.removeAllItems();
		categoryFilter.addItem("All Categories");
		// Add new categories
		for (String category : categoryMap.keySet()) {
			categoryFilter.addItem(category);
		}

		// ========== Refresh Display ==========
		applyLayout();
		statusLabel.setText("Algorithms: " + algorithmNodes.size());

		// Update detail panel in case selected algorithm changed
		if (detailPanel != null) {
			detailPanel.clearDetails();
		}

		// Update minimap
		if (minimapPanel != null) {
			minimapPanel.updateMinimap();
		}
	}

	/**
	 * Properly dispose of all resources used by this component
	 * 
	 * This method ensures: - All timers are stopped - All listeners are removed -
	 * Child components are disposed - No memory leaks occur
	 * 
	 * Safe to call multiple times (idempotent).
	 */
	public void disposeResources() {
		// Prevent double-disposal
		if (disposed) {
			return;
		}

		System.out.println("Disposing AlgorithmGraphVisualizer resources...");

		// ========== Stop Animation Timers ==========
		if (graphPanel != null) {
			graphPanel.stopLayoutTimer();
			graphPanel.dispose();
		}

		// ========== Remove All Listeners ==========
		// Remove action listeners
		for (ActionListener listener : registeredListeners) {
			// Find and remove from components
			if (searchField != null)
				searchField.removeActionListener(listener);
			if (categoryFilter != null)
				categoryFilter.removeActionListener(listener);
			if (layoutComboBox != null)
				layoutComboBox.removeActionListener(listener);
			if (showLabelsCheckBox != null)
				showLabelsCheckBox.removeActionListener(listener);
			if (showConnectionsCheckBox != null)
				showConnectionsCheckBox.removeActionListener(listener);
			if (clusterByInputCheckBox != null)
				clusterByInputCheckBox.removeActionListener(listener);
			if (clusterByOutputCheckBox != null)
				clusterByOutputCheckBox.removeActionListener(listener);
			if (excludeToolsCheckBox != null)
				excludeToolsCheckBox.removeActionListener(listener);
			if (refreshButton != null)
				refreshButton.removeActionListener(listener);
			if (exportButton != null)
				exportButton.removeActionListener(listener);
		}
		registeredListeners.clear();

		// Remove mouse listeners (handled by GraphPanel)
		registeredMouseListeners.clear();

		// ========== Clear Data Structures ==========
		if (algorithmNodes != null) {
			algorithmNodes.clear();
			algorithmNodes = null;
		}

		if (categoryMap != null) {
			categoryMap.clear();
			categoryMap = null;
		}

		// ========== Dispose Child Components ==========
		if (detailPanel != null) {
			detailPanel.dispose();
			detailPanel = null;
		}

		if (minimapPanel != null) {
			minimapPanel.dispose();
			minimapPanel = null;
		}

		// ========== Mark as Disposed ==========
		disposed = true;

		System.out.println("AlgorithmGraphVisualizer resources disposed successfully");
	}

	/**
	 * Override dispose to ensure proper resource cleanup
	 */
	@Override
	public void dispose() {
		disposeResources();
		super.dispose();
	}

	// ============================================================================
	// INNER CLASS: AlgorithmNode
	// ============================================================================

	/**
	 * Data model representing a single algorithm node in the graph
	 * 
	 * Each node contains: - Algorithm metadata (name, description, category) -
	 * Visual properties (position, color, visibility) - Physics properties
	 * (velocity for force-directed layout) - UI state (highlighted, selected)
	 */
	class AlgorithmNode {
		// ========== Algorithm Metadata ==========
		/** Display name of the algorithm */
		private String name;

		/** Complete algorithm metadata from SPMF */
		private DescriptionOfAlgorithm description;

		/** Category this algorithm belongs to */
		private String category;

		// ========== Visual Properties ==========
		/** X coordinate in graph space (pixels) */
		private double x;

		/** Y coordinate in graph space (pixels) */
		private double y;

		/** X velocity component for force-directed layout (pixels/frame) */
		private double vx;

		/** Y velocity component for force-directed layout (pixels/frame) */
		private double vy;

		/** Whether this node should be displayed (based on filters) */
		private boolean visible;

		/** Whether this node is currently highlighted (similar algorithms) */
		private boolean highlighted;

		/** Color assigned to this node (based on category) */
		private Color color;

		/** Whether this algorithm is a tool/utility (vs. data mining algorithm) */
		private boolean isTool;

		/**
		 * Construct a new algorithm node
		 * 
		 * @param name        algorithm name
		 * @param description complete algorithm metadata
		 * @param category    category this algorithm belongs to
		 */
		public AlgorithmNode(String name, DescriptionOfAlgorithm description, String category) {
			this.name = name;
			this.description = description;
			this.category = category;

			// Initial state
			this.visible = true;
			this.highlighted = false;
			this.x = 0;
			this.y = 0;
			this.vx = 0;
			this.vy = 0;

			// Determine if this is a tool (affects filtering)
			this.isTool = determineIfTool(description);

			// Assign category-based color
			this.color = generateColorForCategory(category);
		}

		/**
		 * Determine if this algorithm is a tool/converter rather than a mining
		 * algorithm
		 * 
		 * Tools are identified by: - Algorithm type contains "tool" - Category contains
		 * "tool", "converter", or "viewer" - Name contains "converter" or "viewer"
		 * 
		 * @param description algorithm metadata
		 * @return true if this is a tool, false if it's a mining algorithm
		 */
		private boolean determineIfTool(DescriptionOfAlgorithm description) {
			if (description == null) {
				return false;
			}

			try {
				// Get algorithm type and category (safely handle nulls)
				String algoType = description.getAlgorithmType() != null
						? description.getAlgorithmType().toString().toLowerCase()
						: "";
				String algoCategory = description.getAlgorithmCategory() != null
						? description.getAlgorithmCategory().toLowerCase()
						: "";

				// Check various indicators of tool status
				return algoType.contains("tool") || algoCategory.contains("tool") || algoCategory.contains("converter")
						|| algoCategory.contains("viewer") || (name != null && name.toLowerCase().contains("converter"))
						|| (name != null && name.toLowerCase().contains("viewer"));
			} catch (Exception e) {
				// If any error occurs, assume it's not a tool
				return false;
			}
		}

		/**
		 * Generate a consistent color for a category using hash-based coloring
		 * 
		 * This ensures: - Same category always gets same color - Colors are
		 * well-distributed - Colors are visible (not too dark)
		 * 
		 * @param category category name
		 * @return semi-transparent color for this category
		 */
		private Color generateColorForCategory(String category) {
			if (category == null) {
				// Default gray for uncategorized
				return new Color(150, 150, 150, 180);
			}

			// Use hash code to generate consistent but varied colors
			int hash = category.hashCode();

			// Extract RGB components from hash
			int r = (hash & 0xFF0000) >> 16; // Red from high byte
			int g = (hash & 0x00FF00) >> 8; // Green from middle byte
			int b = (hash & 0x0000FF); // Blue from low byte

			// Ensure colors are not too dark (minimum brightness)
			r = Math.max(80, r);
			g = Math.max(80, g);
			b = Math.max(80, b);

			// Return with alpha transparency
			return new Color(r, g, b, 180);
		}

		// ========== Getters and Setters ==========

		public String getName() {
			return name;
		}

		public DescriptionOfAlgorithm getDescription() {
			return description;
		}

		public String getCategory() {
			return category;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public void setX(double x) {
			this.x = x;
		}

		public void setY(double y) {
			this.y = y;
		}

		public double getVx() {
			return vx;
		}

		public double getVy() {
			return vy;
		}

		public void setVx(double vx) {
			this.vx = vx;
		}

		public void setVy(double vy) {
			this.vy = vy;
		}

		public boolean isVisible() {
			return visible;
		}

		public void setVisible(boolean visible) {
			this.visible = visible;
		}

		public boolean isHighlighted() {
			return highlighted;
		}

		public void setHighlighted(boolean highlighted) {
			this.highlighted = highlighted;
		}

		public Color getColor() {
			return color;
		}

		public boolean isTool() {
			return isTool;
		}
	}

	// ============================================================================
	// INNER CLASS: GraphPanel
	// ============================================================================

	/**
	 * Panel responsible for rendering the algorithm graph
	 * 
	 * Handles: - Node rendering (circles with colors) - Connection rendering (lines
	 * between related algorithms) - Label rendering (algorithm names) - Layout
	 * algorithms (force-directed, circular, grid, hierarchical) - Mouse interaction
	 * (dragging nodes, highlighting) - Resource management (timers)
	 */
	class GraphPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		// ========== Visual Constants ==========
		/** Radius of algorithm node circles (pixels) */
		private static final int NODE_RADIUS = 8;

		/** Radius of highlight ring around selected nodes (pixels) */
		private static final int HIGHLIGHT_RADIUS = 12;

		// ========== Display Options ==========
		/** Whether to show algorithm name labels */
		private boolean showLabels = true;

		/** Whether to show connection lines between related algorithms */
		private boolean showConnections = true;

		/** Whether to cluster algorithms by input file types */
		private boolean clusterByInput = true;

		/** Whether to cluster algorithms by output file types */
		private boolean clusterByOutput = true;

		// ========== Interaction State ==========
		/** Currently selected node (for highlighting similar algorithms) */
		private AlgorithmNode selectedNode = null;

		/** Node currently being dragged by the user */
		private AlgorithmNode draggedNode = null;

		/** Last mouse position (for drag calculations) */
		private Point lastMousePoint = null;

		// ========== Layout Animation ==========
		/** Timer for animated force-directed layout */
		private Timer layoutTimer;

		// ========== Force-Directed Layout Physics Parameters ==========

		/**
		 * Repulsion force strength between nodes Higher values = nodes push each other
		 * away more strongly Formula: F = REPULSION / distance²
		 */
		private static final double REPULSION = 10000;

		/**
		 * Attraction force strength for connected nodes (spring constant) Higher values
		 * = connected nodes pull together more strongly Formula: F = ATTRACTION *
		 * displacement
		 */
		private static final double ATTRACTION = 0.003;

		/**
		 * Damping factor to slow down node movement (prevent oscillation) Range: 0.0
		 * (stop immediately) to 1.0 (no damping) Applied each frame: velocity =
		 * velocity * DAMPING
		 */
		private static final double DAMPING = 0.75;

		/**
		 * Minimum allowed distance between nodes (pixels) Prevents nodes from
		 * overlapping or getting too close
		 */
		private static final double MIN_DISTANCE = 80;

		/**
		 * Maximum velocity per frame (pixels) Prevents wild movements and helps
		 * convergence
		 */
		private static final double MAX_VELOCITY = 10;

		/**
		 * Ideal rest length for springs between connected nodes (pixels) Connected
		 * nodes will settle at approximately this distance
		 */
		private static final double SPRING_LENGTH = 150;

		// ========== Mouse Listeners ==========
		/** Mouse listener for tracking (stored for disposal) */
		private MouseAdapter mouseListener;

		/** Mouse motion listener for tracking (stored for disposal) */
		private MouseMotionAdapter mouseMotionListener;

		/**
		 * Constructor for GraphPanel
		 * 
		 * Initializes the panel and sets up mouse interaction
		 */
		public GraphPanel() {
			setBackground(Color.WHITE);
			setPreferredSize(new Dimension(2000, 2000));
			setupMouseListeners();
		}

		// ========== Display Option Setters ==========

		public void setShowLabels(boolean show) {
			this.showLabels = show;
		}

		public void setShowConnections(boolean show) {
			this.showConnections = show;
		}

		public void setClusterByInput(boolean cluster) {
			this.clusterByInput = cluster;
		}

		public void setClusterByOutput(boolean cluster) {
			this.clusterByOutput = cluster;
		}

		/**
		 * Stop the layout animation timer
		 * 
		 * Should be called before: - Exporting the graph - Disposing the panel -
		 * Switching to a non-animated layout
		 */
		public void stopLayoutTimer() {
			if (layoutTimer != null && layoutTimer.isRunning()) {
				layoutTimer.stop();
			}
		}

		/**
		 * Setup mouse interaction listeners
		 * 
		 * Handles: - Node selection (click) - Node dragging (click + drag) - Similar
		 * algorithm highlighting
		 */
		private void setupMouseListeners() {
			// ========== Mouse Click and Press ==========
			mouseListener = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					// Find node at click position
					selectedNode = findNodeAt(e.getPoint());
					draggedNode = selectedNode;
					lastMousePoint = e.getPoint();

					if (selectedNode != null) {
						// Highlight similar algorithms
						highlightSimilarAlgorithms(selectedNode);

						// Update detail panel with selected algorithm info
						if (detailPanel != null) {
							detailPanel.showAlgorithmDetails(selectedNode);
						}
					} else {
						// Clear highlights and detail panel
						clearHighlights();
						if (detailPanel != null) {
							detailPanel.clearDetails();
						}
					}

					repaint();

					// Update minimap to show selection
					if (minimapPanel != null) {
						minimapPanel.setSelectedNode(selectedNode);
						minimapPanel.repaint();
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					// Stop dragging
					draggedNode = null;
				}
			};
			addMouseListener(mouseListener);

			// Track for disposal
			registeredMouseListeners.add(mouseListener);

			// ========== Mouse Drag ==========
			mouseMotionListener = new MouseMotionAdapter() {
				@Override
				public void mouseDragged(MouseEvent e) {
					if (draggedNode != null) {
						// Update node position to follow mouse
						draggedNode.setX(e.getX());
						draggedNode.setY(e.getY());

						// Reset velocity to prevent drift
						draggedNode.setVx(0);
						draggedNode.setVy(0);

						repaint();

						// Update minimap to show new position
						if (minimapPanel != null) {
							minimapPanel.repaint();
						}
					}
				}
			};
			addMouseMotionListener(mouseMotionListener);
		}

		/**
		 * Find the algorithm node at a given screen position
		 * 
		 * Uses simple distance check against node centers. Could be optimized with
		 * spatial indexing for large graphs.
		 * 
		 * @param p screen position to check
		 * @return node at that position, or null if none found
		 */
		private AlgorithmNode findNodeAt(Point p) {
			// Iterate through all visible nodes
			for (AlgorithmNode node : algorithmNodes) {
				if (!node.isVisible())
					continue;

				// Calculate distance from point to node center
				double dist = Math.sqrt(Math.pow(p.x - node.getX(), 2) + Math.pow(p.y - node.getY(), 2));

				// Check if point is within node radius
				if (dist <= NODE_RADIUS) {
					return node;
				}
			}

			return null; // No node found
		}

		/**
		 * Highlight algorithms similar to the selected node
		 * 
		 * Similarity is based on: - Shared input file types (if clusterByInput enabled)
		 * - Shared output file types (if clusterByOutput enabled)
		 * 
		 * @param selected the node to find similar algorithms for
		 */
		private void highlightSimilarAlgorithms(AlgorithmNode selected) {
			// Clear any existing highlights
			clearHighlights();

			// Always highlight the selected node
			selected.setHighlighted(true);

			// Get selected algorithm metadata
			DescriptionOfAlgorithm selectedDesc = selected.getDescription();
			if (selectedDesc == null)
				return;

			// Check each other node for similarity
			for (AlgorithmNode node : algorithmNodes) {
				if (node == selected || !node.isVisible())
					continue;

				DescriptionOfAlgorithm nodeDesc = node.getDescription();
				if (nodeDesc == null)
					continue;

				// Check if input types match (based on clustering setting)
				boolean matchesInput = !clusterByInput
						|| arraysEqual(selectedDesc.getInputFileTypes(), nodeDesc.getInputFileTypes());

				// Check if output types match (based on clustering setting)
				boolean matchesOutput = !clusterByOutput
						|| arraysEqual(selectedDesc.getOutputFileTypes(), nodeDesc.getOutputFileTypes());

				// Highlight if both criteria match
				if (matchesInput && matchesOutput) {
					node.setHighlighted(true);
				}
			}
		}

		/**
		 * Clear all node highlights
		 */
		private void clearHighlights() {
			for (AlgorithmNode node : algorithmNodes) {
				node.setHighlighted(false);
			}
		}

		/**
		 * Compare two string arrays for equality (null-safe)
		 * 
		 * Used to compare input/output file type arrays.
		 * 
		 * @param a1 first array
		 * @param a2 second array
		 * @return true if arrays contain same elements in same order
		 */
		private boolean arraysEqual(String[] a1, String[] a2) {
			// Both null = equal
			if (a1 == null && a2 == null)
				return true;

			// One null = not equal
			if (a1 == null || a2 == null)
				return false;

			// Different lengths = not equal
			if (a1.length != a2.length)
				return false;

			// Compare each element
			for (int i = 0; i < a1.length; i++) {
				if (a1[i] == null && a2[i] == null)
					continue;
				if (a1[i] == null || a2[i] == null)
					return false;
				if (!a1[i].equals(a2[i]))
					return false;
			}

			return true;
		}

		/**
		 * Paint the graph panel
		 * 
		 * Rendering order: 1. Connection lines (background) 2. Algorithm nodes (middle)
		 * 3. Labels (foreground)
		 * 
		 * @param g graphics context
		 */
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			// Enable antialiasing for smooth rendering
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// Draw in layers (back to front)
			if (showConnections) {
				drawConnections(g2);
			}

			drawNodes(g2);

			if (showLabels) {
				drawLabels(g2);
			}
		}

		/**
		 * Draw connection lines between related algorithms
		 * 
		 * Connections are drawn between algorithms that share: - Input file types (if
		 * clusterByInput enabled) - Output file types (if clusterByOutput enabled)
		 * 
		 * Uses semi-transparent gray lines to avoid visual clutter.
		 * 
		 * @param g2 graphics context
		 */
		private void drawConnections(Graphics2D g2) {
			// Use semi-transparent gray for connection lines
			g2.setColor(new Color(200, 200, 200, 100));

			// Iterate through all pairs of nodes (avoid duplicates)
			for (int i = 0; i < algorithmNodes.size(); i++) {
				AlgorithmNode node1 = algorithmNodes.get(i);
				if (!node1.isVisible())
					continue;

				// Only check nodes after i to avoid duplicate pairs
				for (int j = i + 1; j < algorithmNodes.size(); j++) {
					AlgorithmNode node2 = algorithmNodes.get(j);
					if (!node2.isVisible())
						continue;

					// Draw line if nodes are connected
					if (areConnected(node1, node2)) {
						g2.draw(new Line2D.Double(node1.getX(), node1.getY(), node2.getX(), node2.getY()));
					}
				}
			}
		}

		/**
		 * Check if two nodes should be connected based on clustering settings
		 * 
		 * Connection criteria: - If clusterByInput: must have matching input types - If
		 * clusterByOutput: must have matching output types - Both conditions must be
		 * true (AND logic)
		 * 
		 * @param n1 first node
		 * @param n2 second node
		 * @return true if nodes should be connected
		 */
		private boolean areConnected(AlgorithmNode n1, AlgorithmNode n2) {
			DescriptionOfAlgorithm d1 = n1.getDescription();
			DescriptionOfAlgorithm d2 = n2.getDescription();

			if (d1 == null || d2 == null)
				return false;

			// Check input type matching
			boolean inputMatches = !clusterByInput || arraysEqual(d1.getInputFileTypes(), d2.getInputFileTypes());

			// Check output type matching
			boolean outputMatches = !clusterByOutput || arraysEqual(d1.getOutputFileTypes(), d2.getOutputFileTypes());

			// Must satisfy both criteria
			return inputMatches && outputMatches;
		}

		/**
		 * Draw all visible algorithm nodes
		 * 
		 * Each node is rendered as: 1. Yellow highlight circle (if highlighted) 2.
		 * Colored fill circle (category color) 3. Black border circle
		 * 
		 * @param g2 graphics context
		 */
		private void drawNodes(Graphics2D g2) {
			for (AlgorithmNode node : algorithmNodes) {
				if (!node.isVisible())
					continue;

				double x = node.getX();
				double y = node.getY();

				// Draw highlight if this node is selected/highlighted
				if (node.isHighlighted()) {
					g2.setColor(Color.YELLOW);
					g2.fill(new Ellipse2D.Double(x - HIGHLIGHT_RADIUS, y - HIGHLIGHT_RADIUS, HIGHLIGHT_RADIUS * 2,
							HIGHLIGHT_RADIUS * 2));
				}

				// Draw main node circle with category color
				g2.setColor(node.getColor());
				g2.fill(new Ellipse2D.Double(x - NODE_RADIUS, y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2));

				// Draw black border
				g2.setColor(Color.BLACK);
				g2.draw(new Ellipse2D.Double(x - NODE_RADIUS, y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2));
			}
		}

		/**
		 * Draw labels for all visible nodes
		 * 
		 * Labels are: - Truncated to 20 characters (with "..." if longer) - Centered
		 * below the node - Drawn on semi-transparent white background for readability
		 * 
		 * @param g2 graphics context
		 */
		private void drawLabels(Graphics2D g2) {
			g2.setFont(new Font("Arial", Font.PLAIN, 9));

			for (AlgorithmNode node : algorithmNodes) {
				if (!node.isVisible())
					continue;

				// Get and truncate label
				String label = node.getName();
				if (label != null && label.length() > 20) {
					label = label.substring(0, 17) + "...";
				}
				if (label == null)
					label = "";

				// Measure text dimensions
				Rectangle2D bounds = g2.getFontMetrics().getStringBounds(label, g2);

				// Calculate position (centered below node)
				double x = node.getX() - bounds.getWidth() / 2;
				double y = node.getY() + NODE_RADIUS + 12;

				// Draw semi-transparent white background
				g2.setColor(new Color(255, 255, 255, 200));
				g2.fill(new Rectangle2D.Double(x - 2, y - bounds.getHeight(), bounds.getWidth() + 4,
						bounds.getHeight() + 2));

				// Draw black text
				g2.setColor(Color.BLACK);
				g2.drawString(label, (float) x, (float) y);
			}
		}

		/**
		 * Apply force-directed layout algorithm
		 * 
		 * This implements a physics-based layout where: - All nodes repel each other
		 * (like electric charges) - Connected nodes attract each other (like springs) -
		 * A centering force prevents drift - Damping prevents oscillation - Progressive
		 * cooling helps convergence
		 * 
		 * The layout runs as an animation over 300 iterations (7.5 seconds at
		 * 25ms/frame).
		 */
		public void applyForceDirectedLayout() {
			// Stop any existing layout animation
			stopLayoutTimer();

			// Initialize nodes in a grid to prevent initial overlap
			initializePositionsGrid();

			// Create animation timer (40 FPS)
			layoutTimer = new Timer(25, new ActionListener() {
				/** Current iteration count */
				private int iterations = 0;

				/** Maximum iterations before stopping */
				private final int MAX_ITERATIONS = 300;

				@Override
				public void actionPerformed(ActionEvent e) {
					// Check for completion
					if (iterations++ > MAX_ITERATIONS) {
						layoutTimer.stop();
						System.out.println("Force-directed layout converged after " + iterations + " iterations");
						return;
					}

					// ========== Progressive Cooling ==========
					// Reduce forces over time to help convergence
					// At iteration 0: coolingFactor = 1.0
					// At iteration MAX_ITERATIONS: coolingFactor = 0.0
					double coolingFactor = 1.0 - (double) iterations / MAX_ITERATIONS;

					// ========== Calculate Forces ==========
					for (AlgorithmNode node : algorithmNodes) {
						if (!node.isVisible())
							continue;

						double fx = 0; // Total force in X direction
						double fy = 0; // Total force in Y direction

						// ========== Repulsion from Other Nodes ==========
						// All nodes push each other away (prevents overlap)
						for (AlgorithmNode other : algorithmNodes) {
							if (other == node || !other.isVisible())
								continue;

							// Vector from other to node
							double dx = node.getX() - other.getX();
							double dy = node.getY() - other.getY();

							// Distance squared (for force calculation)
							double distSquared = dx * dx + dy * dy;
							double dist = Math.sqrt(distSquared);

							// Enforce minimum distance to prevent division by zero
							if (dist < MIN_DISTANCE) {
								dist = MIN_DISTANCE;
								// Add random jitter to break perfect symmetry
								dx += (Math.random() - 0.5) * 20;
								dy += (Math.random() - 0.5) * 20;
								distSquared = dx * dx + dy * dy;
							}

							// Coulomb's law: F = k / r²
							double force = REPULSION / distSquared;

							// Add force components (normalized by distance)
							fx += (dx / dist) * force;
							fy += (dy / dist) * force;
						}

						// ========== Attraction to Connected Nodes ==========
						// Connected nodes pull each other together (spring model)
						for (AlgorithmNode other : algorithmNodes) {
							if (other == node || !other.isVisible())
								continue;

							if (areConnected(node, other)) {
								// Vector from node to other
								double dx = other.getX() - node.getX();
								double dy = other.getY() - node.getY();
								double dist = Math.sqrt(dx * dx + dy * dy);

								// Hooke's law: F = k * (x - x₀)
								// where x = current distance, x₀ = rest length
								double displacement = dist - SPRING_LENGTH;
								double force = ATTRACTION * displacement;

								if (dist > 0) {
									// Add force components (normalized)
									fx += (dx / dist) * force;
									fy += (dy / dist) * force;
								}
							}
						}

						// ========== Centering Force ==========
						// Pull all nodes toward center to prevent drift
						double centerX = getWidth() / 2.0;
						double centerY = getHeight() / 2.0;

						// Distance from center
						double distFromCenter = Math
								.sqrt(Math.pow(node.getX() - centerX, 2) + Math.pow(node.getY() - centerY, 2));

						// Stronger pull the further from center
						double centeringStrength = 0.002 * (1 + distFromCenter / 500);
						fx += (centerX - node.getX()) * centeringStrength;
						fy += (centerY - node.getY()) * centeringStrength;

						// ========== Update Velocity ==========
						// Apply forces with damping and cooling
						node.setVx((node.getVx() + fx * coolingFactor) * DAMPING);
						node.setVy((node.getVy() + fy * coolingFactor) * DAMPING);

						// ========== Velocity Limiting ==========
						// Prevent excessive speeds that could cause instability
						double velocity = Math.sqrt(node.getVx() * node.getVx() + node.getVy() * node.getVy());

						double maxVel = MAX_VELOCITY * coolingFactor;
						if (velocity > maxVel) {
							// Scale down velocity to maximum
							node.setVx(node.getVx() * maxVel / velocity);
							node.setVy(node.getVy() * maxVel / velocity);
						}
					}

					// ========== Update Positions ==========
					for (AlgorithmNode node : algorithmNodes) {
						if (!node.isVisible())
							continue;

						// Euler integration: position += velocity
						node.setX(node.getX() + node.getVx());
						node.setY(node.getY() + node.getVy());

						// ========== Boundary Enforcement ==========
						// Keep nodes within panel bounds with bounce
						int padding = 150;
						int maxWidth = getWidth() - padding;
						int maxHeight = getHeight() - padding;

						// Left/right boundaries
						if (node.getX() < padding) {
							node.setX(padding);
							node.setVx(Math.abs(node.getVx()) * 0.5); // Bounce
						} else if (node.getX() > maxWidth) {
							node.setX(maxWidth);
							node.setVx(-Math.abs(node.getVx()) * 0.5); // Bounce
						}

						// Top/bottom boundaries
						if (node.getY() < padding) {
							node.setY(padding);
							node.setVy(Math.abs(node.getVy()) * 0.5); // Bounce
						} else if (node.getY() > maxHeight) {
							node.setY(maxHeight);
							node.setVy(-Math.abs(node.getVy()) * 0.5); // Bounce
						}
					}

					// Update display
					repaint();

					// Update minimap every few frames (performance optimization)
					if (iterations % 5 == 0 && minimapPanel != null) {
						minimapPanel.updateMinimap();
					}
				}
			});

			// Start animation
			layoutTimer.start();
			System.out.println("Starting force-directed layout animation...");
		}

		/**
		 * Initialize node positions in a grid pattern
		 * 
		 * This prevents initial overlap and provides a good starting point for
		 * force-directed layout. Random offsets break perfect symmetry which helps the
		 * algorithm converge to a natural-looking layout.
		 */
		private void initializePositionsGrid() {
			// Collect visible nodes
			List<AlgorithmNode> visibleNodes = new ArrayList<>();
			for (AlgorithmNode node : algorithmNodes) {
				if (node.isVisible()) {
					visibleNodes.add(node);
				}
			}

			if (visibleNodes.isEmpty())
				return;

			// ========== Calculate Grid Dimensions ==========
			int nodeCount = visibleNodes.size();

			// Use wider grid (aspect ratio ~1.5:1)
			int cols = (int) Math.ceil(Math.sqrt(nodeCount * 1.5));
			int rows = (int) Math.ceil((double) nodeCount / cols);

			// ========== Calculate Available Space ==========
			int padding = 150;
			int availableWidth = getWidth() - 2 * padding;
			int availableHeight = getHeight() - 2 * padding;

			// Calculate cell size
			int cellWidth = availableWidth / (cols + 1);
			int cellHeight = availableHeight / (rows + 1);

			// Ensure minimum spacing (120 pixels)
			cellWidth = Math.max(cellWidth, 120);
			cellHeight = Math.max(cellHeight, 120);

			// ========== Position Nodes in Grid ==========
			for (int i = 0; i < visibleNodes.size(); i++) {
				AlgorithmNode node = visibleNodes.get(i);

				// Calculate grid position
				int row = i / cols;
				int col = i % cols;

				// Add randomness to break symmetry (important for force-directed)
				double randomOffsetX = (Math.random() - 0.5) * 60;
				double randomOffsetY = (Math.random() - 0.5) * 60;

				// Set position
				node.setX(padding + cellWidth * (col + 1) + randomOffsetX);
				node.setY(padding + cellHeight * (row + 1) + randomOffsetY);

				// Set small initial velocity for more natural movement
				node.setVx((Math.random() - 0.5) * 2);
				node.setVy((Math.random() - 0.5) * 2);
			}

			System.out.println("Initialized " + visibleNodes.size() + " nodes in " + rows + "x" + cols + " grid");
		}

		/**
		 * Apply circular layout algorithm
		 * 
		 * Arranges all visible nodes in a circle around the center of the panel. This
		 * layout is useful for: - Showing all nodes equally - Visualizing cyclic
		 * relationships - Creating symmetric visualizations
		 */
		public void applyCircularLayout() {
			// Stop any running animation
			stopLayoutTimer();

			// Collect visible nodes
			List<AlgorithmNode> visibleNodes = new ArrayList<>();
			for (AlgorithmNode node : algorithmNodes) {
				if (node.isVisible()) {
					visibleNodes.add(node);
					// Reset velocity
					node.setVx(0);
					node.setVy(0);
				}
			}

			if (visibleNodes.isEmpty())
				return;

			// ========== Calculate Circle Parameters ==========
			int centerX = getWidth() / 2;
			int centerY = getHeight() / 2;

			// Use 80% of smaller dimension for radius
			int radius = Math.min(centerX, centerY) - 150;

			// ========== Position Nodes on Circle ==========
			// Angle between consecutive nodes
			double angleStep = 2 * Math.PI / visibleNodes.size();

			for (int i = 0; i < visibleNodes.size(); i++) {
				AlgorithmNode node = visibleNodes.get(i);

				// Calculate angle for this node
				double angle = i * angleStep;

				// Convert polar to Cartesian coordinates
				node.setX(centerX + radius * Math.cos(angle));
				node.setY(centerY + radius * Math.sin(angle));
			}

			System.out.println("Applied circular layout to " + visibleNodes.size() + " nodes");

			// Update display
			repaint();
			if (minimapPanel != null) {
				minimapPanel.updateMinimap();
			}
		}

		/**
		 * Apply grid layout algorithm
		 * 
		 * Arranges nodes in a uniform grid pattern. This layout is useful for: -
		 * Maximizing use of screen space - Creating organized, predictable layouts -
		 * Presenting large numbers of nodes
		 */
		public void applyGridLayout() {
			// Stop any running animation
			stopLayoutTimer();

			// Collect visible nodes
			List<AlgorithmNode> visibleNodes = new ArrayList<>();
			for (AlgorithmNode node : algorithmNodes) {
				if (node.isVisible()) {
					visibleNodes.add(node);
					// Reset velocity
					node.setVx(0);
					node.setVy(0);
				}
			}

			if (visibleNodes.isEmpty())
				return;

			// ========== Calculate Grid Dimensions ==========
			// Try to make grid roughly square
			int cols = (int) Math.ceil(Math.sqrt(visibleNodes.size()));
			int rows = (int) Math.ceil((double) visibleNodes.size() / cols);

			// Calculate cell size
			int cellWidth = getWidth() / (cols + 1);
			int cellHeight = getHeight() / (rows + 1);

			// ========== Position Nodes in Grid ==========
			for (int i = 0; i < visibleNodes.size(); i++) {
				AlgorithmNode node = visibleNodes.get(i);

				// Calculate grid position
				int row = i / cols;
				int col = i % cols;

				// Set position (centered in cell)
				node.setX(cellWidth * (col + 1));
				node.setY(cellHeight * (row + 1));
			}

			System.out.println("Applied grid layout: " + rows + "x" + cols + " (" + visibleNodes.size() + " nodes)");

			// Update display
			repaint();
			if (minimapPanel != null) {
				minimapPanel.updateMinimap();
			}
		}

		/**
		 * Apply hierarchical layout by category
		 * 
		 * Groups nodes by category in horizontal rows. Each category gets its own row.
		 * 
		 * This layout is useful for: - Understanding category distribution - Finding
		 * algorithms in specific categories - Visualizing categorical organization
		 */
		public void applyHierarchicalLayout() {
			// Stop any running animation
			stopLayoutTimer();

			int y = 100; // Starting Y position
			int spacing = 80; // Vertical spacing between rows

			// ========== Process Each Category ==========
			for (String category : categoryMap.keySet()) {
				List<AlgorithmNode> categoryNodes = categoryMap.get(category);

				// Count visible nodes in this category
				int visibleCount = 0;
				for (AlgorithmNode node : categoryNodes) {
					if (node.isVisible()) {
						visibleCount++;
						// Reset velocity
						node.setVx(0);
						node.setVy(0);
					}
				}

				// Skip empty categories
				if (visibleCount == 0)
					continue;

				// ========== Calculate Horizontal Spacing ==========
				int x = 100; // Starting X position
				int availableWidth = getWidth() - 200;
				int xSpacing = Math.max(120, availableWidth / Math.max(1, visibleCount));

				// ========== Position Nodes in Row ==========
				for (AlgorithmNode node : categoryNodes) {
					if (!node.isVisible())
						continue;

					node.setX(x);
					node.setY(y);
					x += xSpacing;
				}

				// Move to next row
				y += spacing;
			}

			System.out.println("Applied hierarchical layout by category");

			// Update display
			repaint();
			if (minimapPanel != null) {
				minimapPanel.updateMinimap();
			}
		}

		/**
		 * Dispose resources used by this panel
		 */
		public void dispose() {
			// Stop any running timer
			stopLayoutTimer();

			// Remove mouse listeners
			if (mouseListener != null) {
				removeMouseListener(mouseListener);
				mouseListener = null;
			}

			if (mouseMotionListener != null) {
				removeMouseMotionListener(mouseMotionListener);
				mouseMotionListener = null;
			}

			System.out.println("GraphPanel resources disposed");
		}
	}

	// ============================================================================
	// INNER CLASS: AlgorithmDetailPanel
	// ============================================================================

	/**
	 * Panel displaying detailed information about a selected algorithm
	 * 
	 * Shows: - Algorithm name and category - Input/output file types - Algorithm
	 * type and description - Implementation details - Parameter information
	 */
	class AlgorithmDetailPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		/** Text area for displaying algorithm details */
		private JTextArea detailsArea;

		/** Scroll pane containing the text area */
		private JScrollPane scrollPane;

		/**
		 * Constructor for AlgorithmDetailPanel
		 */
		public AlgorithmDetailPanel() {
			setLayout(new BorderLayout());
			setBorder(BorderFactory.createTitledBorder("Algorithm Details"));

			// Create text area for details
			detailsArea = new JTextArea();
			detailsArea.setEditable(false);
			detailsArea.setFont(DETAIL_FONT);
			detailsArea.setLineWrap(true);
			detailsArea.setWrapStyleWord(true);

			// Wrap in scroll pane
			scrollPane = new JScrollPane(detailsArea);
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

			add(scrollPane, BorderLayout.CENTER);

			// Show initial message
			clearDetails();
		}

		/**
		 * Display details for a selected algorithm
		 * 
		 * @param node the algorithm node to show details for
		 */
		public void showAlgorithmDetails(AlgorithmNode node) {
			if (node == null) {
				clearDetails();
				return;
			}

			StringBuilder details = new StringBuilder();

			// ========== Basic Information ==========
			details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
			details.append(" ALGORITHM INFORMATION\n");
			details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

			details.append("Name:\n  ").append(node.getName()).append("\n\n");
			details.append("Category:\n  ").append(node.getCategory()).append("\n\n");
			details.append("Type:\n  ").append(node.isTool() ? "Tool/Utility" : "Data Mining Algorithm").append("\n\n");

			DescriptionOfAlgorithm desc = node.getDescription();
			if (desc != null) {
				// ========== Input/Output Types ==========
				details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
				details.append(" INPUT/OUTPUT SPECIFICATION\n");
				details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

				// Input file types
				details.append("Input File Types:\n");
				String[] inputTypes = desc.getInputFileTypes();
				if (inputTypes != null && inputTypes.length > 0) {
					for (String type : inputTypes) {
						details.append("  • ").append(type).append("\n");
					}
				} else {
					details.append("  (none specified)\n");
				}
				details.append("\n");

				// Output file types
				details.append("Output File Types:\n");
				String[] outputTypes = desc.getOutputFileTypes();
				if (outputTypes != null && outputTypes.length > 0) {
					for (String type : outputTypes) {
						details.append("  • ").append(type).append("\n");
					}
				} else {
					details.append("  (none specified)\n");
				}
				details.append("\n");

				// ========== Implementation Details ==========
				details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
				details.append(" IMPLEMENTATION\n");
				details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

				// Algorithm type
				if (desc.getAlgorithmType() != null) {
					details.append("Algorithm Type:\n  ").append(desc.getAlgorithmType()).append("\n\n");
				}

				// Implementation class
				if (desc.getImplementationAuthorNames() != null) {
					details.append("Implementation Author:\n  ").append(desc.getImplementationAuthorNames())
							.append("\n\n");
				}

				// ========== Description ==========
				details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
				details.append(" DESCRIPTION\n");
				details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

				// URL (if available)
				if (desc.getURLOfDocumentation() != null) {
					details.append("Documentation URL:\n  ").append(desc.getURLOfDocumentation()).append("\n\n");
				}

				// Algorithm category
				if (desc.getAlgorithmCategory() != null) {
					details.append("Category:\n  ").append(desc.getAlgorithmCategory()).append("\n\n");
				}
			}

			// ========== Visual Properties ==========
			details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
			details.append(" GRAPH PROPERTIES\n");
			details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

			details.append("Position:\n  X: ").append(String.format("%.1f", node.getX())).append(", Y: ")
					.append(String.format("%.1f", node.getY())).append("\n\n");

			details.append("Color (RGB):\n  ").append("R:").append(node.getColor().getRed()).append(" ").append("G:")
					.append(node.getColor().getGreen()).append(" ").append("B:").append(node.getColor().getBlue())
					.append("\n\n");

			// Set text
			detailsArea.setText(details.toString());
			detailsArea.setCaretPosition(0); // Scroll to top
		}

		/**
		 * Clear the detail panel
		 */
		public void clearDetails() {
			detailsArea.setText("\n\n\n    No algorithm selected.\n\n    Click on a node to view details.");
		}

		/**
		 * Dispose resources
		 */
		public void dispose() {
			// Nothing to dispose currently
		}
	}

	// ============================================================================
	// INNER CLASS: MinimapPanel
	// ============================================================================

	/**
	 * Minimap panel providing overview and navigation for large graphs
	 * 
	 * Features: - Shows entire graph at reduced scale - Displays current viewport
	 * rectangle - Highlights selected node - Allows click-to-navigate
	 */
	class MinimapPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		/** Scale factor for minimap (e.g., 0.1 = 10% of original size) */
		private static final double MINIMAP_SCALE = 0.1;

		/** Padding around minimap content */
		private static final int MINIMAP_PADDING = 10;

		/** Currently selected node (highlighted in minimap) */
		private AlgorithmNode selectedNode = null;

		/** Mouse listener for minimap clicks */
		private MouseAdapter minimapMouseListener;

		/**
		 * Constructor for MinimapPanel
		 */
		public MinimapPanel() {
			setBackground(new Color(240, 240, 240));
			setBorder(BorderFactory.createTitledBorder("Minimap"));
			setPreferredSize(new Dimension(200, 150));

			setupMinimapMouseListener();
		}

		/**
		 * Setup mouse listener for minimap navigation
		 */
		private void setupMinimapMouseListener() {
			minimapMouseListener = new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					// TODO: Implement click-to-navigate functionality
					// Would calculate which node was clicked in minimap
					// and scroll main view to that position
				}
			};
			addMouseListener(minimapMouseListener);
		}

		/**
		 * Set the currently selected node (for highlighting)
		 * 
		 * @param node the selected node
		 */
		public void setSelectedNode(AlgorithmNode node) {
			this.selectedNode = node;
		}

		/**
		 * Update the minimap (should be called after layout changes)
		 */
		public void updateMinimap() {
			repaint();
		}

		/**
		 * Paint the minimap
		 * 
		 * @param g graphics context
		 */
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// ========== Calculate Bounds ==========
			// Find the bounding box of all visible nodes
			double minX = Double.MAX_VALUE;
			double minY = Double.MAX_VALUE;
			double maxX = Double.MIN_VALUE;
			double maxY = Double.MIN_VALUE;

			int visibleCount = 0;
			for (AlgorithmNode node : algorithmNodes) {
				if (!node.isVisible())
					continue;

				minX = Math.min(minX, node.getX());
				minY = Math.min(minY, node.getY());
				maxX = Math.max(maxX, node.getX());
				maxY = Math.max(maxY, node.getY());
				visibleCount++;
			}

			// Handle empty graph
			if (visibleCount == 0) {
				g2.setColor(Color.GRAY);
				g2.drawString("No visible nodes", getWidth() / 2 - 40, getHeight() / 2);
				return;
			}

			// ========== Calculate Scale and Offset ==========
			double graphWidth = maxX - minX;
			double graphHeight = maxY - minY;

			int availableWidth = getWidth() - 2 * MINIMAP_PADDING;
			int availableHeight = getHeight() - 2 * MINIMAP_PADDING;

			// Calculate scale to fit graph in available space
			double scaleX = graphWidth > 0 ? availableWidth / graphWidth : 1.0;
			double scaleY = graphHeight > 0 ? availableHeight / graphHeight : 1.0;
			double scale = Math.min(scaleX, scaleY) * 0.9; // Use 90% to leave margin

			// Calculate offset to center the graph
			double offsetX = MINIMAP_PADDING - minX * scale + (availableWidth - graphWidth * scale) / 2;
			double offsetY = MINIMAP_PADDING - minY * scale + (availableHeight - graphHeight * scale) / 2;

			// ========== Draw Connections (if enabled) ==========
			if (graphPanel.showConnections) {
				g2.setColor(new Color(200, 200, 200, 150));

				for (int i = 0; i < algorithmNodes.size(); i++) {
					AlgorithmNode node1 = algorithmNodes.get(i);
					if (!node1.isVisible())
						continue;

					for (int j = i + 1; j < algorithmNodes.size(); j++) {
						AlgorithmNode node2 = algorithmNodes.get(j);
						if (!node2.isVisible())
							continue;

						if (graphPanel.areConnected(node1, node2)) {
							int x1 = (int) (node1.getX() * scale + offsetX);
							int y1 = (int) (node1.getY() * scale + offsetY);
							int x2 = (int) (node2.getX() * scale + offsetX);
							int y2 = (int) (node2.getY() * scale + offsetY);

							g2.drawLine(x1, y1, x2, y2);
						}
					}
				}
			}

			// ========== Draw Nodes ==========
			int minimapNodeRadius = 3; // Smaller nodes for minimap

			for (AlgorithmNode node : algorithmNodes) {
				if (!node.isVisible())
					continue;

				int x = (int) (node.getX() * scale + offsetX);
				int y = (int) (node.getY() * scale + offsetY);

				// Highlight selected node
				if (node == selectedNode) {
					g2.setColor(Color.YELLOW);
					g2.fillOval(x - 5, y - 5, 10, 10);
				}

				// Draw node
				g2.setColor(node.getColor());
				g2.fillOval(x - minimapNodeRadius, y - minimapNodeRadius, minimapNodeRadius * 2, minimapNodeRadius * 2);

				// Draw border
				g2.setColor(Color.BLACK);
				g2.drawOval(x - minimapNodeRadius, y - minimapNodeRadius, minimapNodeRadius * 2, minimapNodeRadius * 2);
			}

			// ========== Draw Viewport Rectangle ==========
			// TODO: Calculate and draw the visible area of the main graph
			// This would show which part of the graph is currently visible
		}

		/**
		 * Dispose resources
		 */
		public void dispose() {
			if (minimapMouseListener != null) {
				removeMouseListener(minimapMouseListener);
				minimapMouseListener = null;
			}
		}
	}

	// ============================================================================
	// MAIN METHOD
	// ============================================================================

	/**
	 * Main method for standalone execution
	 * 
	 * Creates and displays the AlgorithmGraphVisualizer window.
	 * 
	 * @param args command-line arguments (not used)
	 */
	public static void main(String[] args) {
		// Run on Event Dispatch Thread for thread safety
		SwingUtilities.invokeLater(() -> {
			new AlgorithmGraphVisualizer(true);
		});
	}
}