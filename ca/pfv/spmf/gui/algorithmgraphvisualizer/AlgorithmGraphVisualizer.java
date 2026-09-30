package ca.pfv.spmf.gui.algorithmgraphvisualizer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.MainWindow;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger. Part of SPMF (GPL v3).
 * See http://www.philippe-fournier-viger.com/spmf for the full license header.
 */
/**
 * Interactive graph visualizer for the algorithms of SPMF.
 *
 * This frame owns the controls and wires the pieces together:
 * {@link AlgorithmGraphModel} (data + filtering), {@link GraphPanel} (rendering
 * and layout), {@link MinimapPanel} and {@link AlgorithmDetailPanel} (views).
 * Options and window geometry are remembered between sessions.
 *
 * @author Philippe Fournier-Viger
 * @version 3.1
 */
public class AlgorithmGraphVisualizer extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final String ALL_CATEGORIES = "All categories";
	private static final int EXPORT_MAX_DIMENSION = 4000;
	private static final int MENU_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

	// ---- preferences keys ----
	private static final String PREF_LAYOUT = "layout", PREF_LABELS = "showLabels", PREF_GROUPS = "showGroups",
			PREF_DIM = "dimUnrelated", PREF_BY_INPUT = "groupByInput", PREF_BY_OUTPUT = "groupByOutput",
			PREF_HIDE_TOOLS = "hideTools", PREF_BOUNDS = "windowBounds", PREF_MAIN_DIVIDER = "mainDivider",
			PREF_GRAPH_DIVIDER = "graphDivider";

	private final Preferences prefs = Preferences.userNodeForPackage(AlgorithmGraphVisualizer.class);

	// ---- data ----
	private final AlgorithmGraphModel model;

	// ---- views ----
	private GraphPanel graphPanel;
	private MinimapPanel minimapPanel;
	private AlgorithmDetailPanel detailPanel;
	private JSplitPane graphSplitPane, mainSplitPane;

	// ---- controls ----
	private JTextField searchField;
	private JComboBox<String> categoryFilter;
	private JComboBox<GraphPanel.LayoutType> layoutComboBox;
	private JCheckBox showLabelsCheckBox, showGroupsCheckBox, dimUnrelatedCheckBox, groupByInputCheckBox,
			groupByOutputCheckBox, hideToolsCheckBox;
	private JLabel statusLabel, zoomLabel;
	private Timer searchDebounce;

	// ---- actions (shared by toolbar and menu) ----
	private Action fitAction, relayoutAction, reloadAction, exportAction, unpinAction, closeAction, focusSearchAction;

	private boolean updatingControls;
	private boolean disposed;

	public AlgorithmGraphVisualizer(boolean runAsStandalone) {
		model = new AlgorithmGraphModel();

		initializeFrame();
		createActions();
		initializeComponents();
		setJMenuBar(createMenuBar());
		layoutComponents();
		setupEventListeners();

		setDefaultCloseOperation(runAsStandalone ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				savePreferences();
				disposeResources();
			}
		});

		setTitle("SPMF Algorithm Graph Visualizer");
		setVisible(true);

		// everything that needs real component sizes runs after the frame is shown
		SwingUtilities.invokeLater(() -> {
			graphSplitPane.setDividerLocation(prefs.getDouble(PREF_GRAPH_DIVIDER, 0.78));
			mainSplitPane.setDividerLocation(prefs.getDouble(PREF_MAIN_DIVIDER, 0.70));
			filterAlgorithms(true);
			graphPanel.requestFocusInWindow();
		});
	}

	// =====================================================================
	// Initialisation
	// =====================================================================

	private void initializeFrame() {
		try {
			setIconImage(Toolkit.getDefaultToolkit()
					.getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));
		} catch (Exception e) {
			// non-critical
		}
		setMinimumSize(new Dimension(900, 600));
		Rectangle b = parseBounds(prefs.get(PREF_BOUNDS, null));
		if (b != null && isOnScreen(b)) {
			setBounds(b);
		} else {
			setSize(1400, 900);
			setLocationRelativeTo(null);
		}
	}

	private static Rectangle parseBounds(String s) {
		if (s == null) {
			return null;
		}
		try {
			String[] p = s.split(",");
			return new Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
					Integer.parseInt(p[3]));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static boolean isOnScreen(Rectangle r) {
		Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
		return r.width > 200 && r.height > 200 && screen.intersects(r);
	}

	private void createActions() {
		fitAction = action("Fit to view", KeyStroke.getKeyStroke(KeyEvent.VK_0, MENU_MASK),
				"Fit the whole graph in the view (or double-click the background)", () -> graphPanel.zoomToFit());
		relayoutAction = action("Re-layout", KeyStroke.getKeyStroke(KeyEvent.VK_R, MENU_MASK),
				"Run the current layout again from scratch (unpins all nodes)", () -> applyLayout(true));
		reloadAction = action("Reload algorithms", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
				"Reload the list of algorithms from SPMF", this::reloadAlgorithms);
		exportAction = action("Export as PNG\u2026", KeyStroke.getKeyStroke(KeyEvent.VK_E, MENU_MASK),
				"Save the whole graph (independent of the current zoom) as a PNG image", this::exportAsPNG);
		unpinAction = action("Unpin all nodes", null, "Release nodes that were pinned by dragging",
				() -> graphPanel.unpinAll());
		closeAction = action("Close", KeyStroke.getKeyStroke(KeyEvent.VK_W, MENU_MASK), null, this::dispose);
		focusSearchAction = action("Search", KeyStroke.getKeyStroke(KeyEvent.VK_F, MENU_MASK), null, () -> {
			searchField.requestFocusInWindow();
			searchField.selectAll();
		});
	}

	private static Action action(String name, KeyStroke accelerator, String tooltip, Runnable r) {
		AbstractAction a = new AbstractAction(name) {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				r.run();
			}
		};
		if (accelerator != null) {
			a.putValue(Action.ACCELERATOR_KEY, accelerator);
		}
		if (tooltip != null) {
			a.putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		return a;
	}

	private void initializeComponents() {
		graphPanel = new GraphPanel(model);
		graphPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
		minimapPanel = new MinimapPanel(model.getNodes(), graphPanel);
		detailPanel = new AlgorithmDetailPanel();

		graphPanel.setGraphListener(new GraphPanel.GraphListener() {
			@Override
			public void nodeSelected(AlgorithmNode node) {
				if (node == null) {
					detailPanel.clearDetails();
				} else {
					detailPanel.showAlgorithmDetails(node, graphPanel.getClusterMates(node));
				}
				minimapPanel.repaint();
				updateStatus();
			}

			@Override
			public void graphChanged() {
				minimapPanel.repaint();
				updateZoomLabel();
			}

			@Override
			public void layoutStateChanged(boolean running) {
				updateZoomLabel();
			}
		});

		detailPanel.setDetailListener(new AlgorithmDetailPanel.DetailListener() {
			@Override
			public void algorithmChosen(String name) {
				AlgorithmNode n = model.findByName(name);
				if (n != null && n.isVisible()) {
					graphPanel.selectNode(n);
					graphPanel.revealNode(n);
				}
			}

			@Override
			public void revealRequested(AlgorithmNode node) {
				graphPanel.revealNode(node);
			}
		});

		searchField = new PlaceholderTextField("name, category, file type, author\u2026", 18);
		searchField.setToolTipText("Filter as you type. Several words = all must match. Esc clears.");

		categoryFilter = new JComboBox<>();
		categoryFilter.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		categoryFilter.setRenderer(new CategoryRenderer());
		populateCategoryFilter();

		layoutComboBox = new JComboBox<>(GraphPanel.LayoutType.values());
		layoutComboBox.setToolTipText("Layout algorithm");
		layoutComboBox.setSelectedItem(readLayoutPref());

		showLabelsCheckBox = check("Labels", PREF_LABELS, true, "Show algorithm names");
		showGroupsCheckBox = check("Groups", PREF_GROUPS, true,
				"Draw a blob around algorithms with the same input/output types");
		dimUnrelatedCheckBox = check("Focus", PREF_DIM, true,
				"When a node is selected, fade out algorithms that are not in its group");
		groupByInputCheckBox = check("Input", PREF_BY_INPUT, true,
				"Group algorithms that read the same input file types");
		groupByOutputCheckBox = check("Output", PREF_BY_OUTPUT, true,
				"Group algorithms that write the same output file types");
		hideToolsCheckBox = check("Hide tools", PREF_HIDE_TOOLS, true,
				"Hide converters, viewers, generators and other utilities");

		// push initial option values to the graph panel
		graphPanel.setShowLabels(showLabelsCheckBox.isSelected());
		graphPanel.setShowClusters(showGroupsCheckBox.isSelected());
		graphPanel.setDimUnrelated(dimUnrelatedCheckBox.isSelected());
		graphPanel.setClusterByInput(groupByInputCheckBox.isSelected());
		graphPanel.setClusterByOutput(groupByOutputCheckBox.isSelected());

		statusLabel = new JLabel(" ");
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		zoomLabel = new JLabel(" ");
		zoomLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		searchDebounce = new Timer(250, e -> filterAlgorithms(false));
		searchDebounce.setRepeats(false);
	}

	private JCheckBox check(String text, String prefKey, boolean def, String tooltip) {
		JCheckBox cb = new JCheckBox(text, prefs.getBoolean(prefKey, def));
		cb.setToolTipText(tooltip);
		return cb;
	}

	private GraphPanel.LayoutType readLayoutPref() {
		try {
			return GraphPanel.LayoutType.valueOf(prefs.get(PREF_LAYOUT, GraphPanel.LayoutType.FORCE_DIRECTED.name()));
		} catch (IllegalArgumentException e) {
			return GraphPanel.LayoutType.FORCE_DIRECTED;
		}
	}

	private void populateCategoryFilter() {
		updatingControls = true;
		try {
			Object previous = categoryFilter.getSelectedItem();
			categoryFilter.removeAllItems();
			categoryFilter.addItem(ALL_CATEGORIES);
			for (String c : model.getCategoryNames()) {
				categoryFilter.addItem(c);
			}
			if (previous != null && model.getCategories().containsKey(previous)) {
				categoryFilter.setSelectedItem(previous);
			} else {
				categoryFilter.setSelectedIndex(0);
			}
		} finally {
			updatingControls = false;
		}
	}

	/** Combo renderer with colour swatch and algorithm count (doubles as legend). */
	private final class CategoryRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			String cat = value == null ? null : value.toString();
			setIcon(new SwatchIcon(cat == null ? null : model.getCategoryColor(cat)));
			if (cat != null && model.getCategories().containsKey(cat)) {
				setText(cat + "  (" + model.getCategorySize(cat) + ")");
			}
			return this;
		}
	}

	private static final class SwatchIcon implements Icon {
		private static final int SIZE = 12;
		private final Color color;

		SwatchIcon(Color color) { this.color = color; }

		@Override
		public int getIconWidth() { return SIZE; }

		@Override
		public int getIconHeight() { return SIZE; }

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			if (color == null) {
				return;
			}
			g.setColor(color);
			g.fillRect(x, y, SIZE, SIZE);
			g.setColor(Color.DARK_GRAY);
			g.drawRect(x, y, SIZE - 1, SIZE - 1);
		}
	}

	/** JTextField that shows grey hint text while empty and unfocused. */
	private static final class PlaceholderTextField extends JTextField {
		private static final long serialVersionUID = 1L;
		private final String placeholder;

		PlaceholderTextField(String placeholder, int columns) {
			super(columns);
			this.placeholder = placeholder;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (getText().isEmpty() && !isFocusOwner()) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(Color.GRAY);
				g2.setFont(getFont().deriveFont(java.awt.Font.ITALIC));
				int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
				g2.drawString(placeholder, getInsets().left + 2, y);
				g2.dispose();
			}
		}
	}

	// =====================================================================
	// Menu bar and layout
	// =====================================================================

	private JMenuBar createMenuBar() {
		JMenuBar bar = new JMenuBar();

		JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		file.add(new JMenuItem(exportAction));
		file.add(new JMenuItem(reloadAction));
		file.addSeparator();
		file.add(new JMenuItem(closeAction));
		bar.add(file);

		JMenu view = new JMenu("View");
		view.setMnemonic(KeyEvent.VK_V);
		view.add(new JMenuItem(fitAction));
		view.add(new JMenuItem(relayoutAction));
		view.add(new JMenuItem(unpinAction));
		view.addSeparator();
		view.add(sharedCheckItem("Show labels", showLabelsCheckBox));
		view.add(sharedCheckItem("Show groups", showGroupsCheckBox));
		view.add(sharedCheckItem("Focus on selected group", dimUnrelatedCheckBox));
		view.addSeparator();
		view.add(sharedCheckItem("Group by same input", groupByInputCheckBox));
		view.add(sharedCheckItem("Group by same output", groupByOutputCheckBox));
		view.addSeparator();
		view.add(sharedCheckItem("Hide tools", hideToolsCheckBox));
		view.addSeparator();
		view.add(new JMenuItem(focusSearchAction));
		bar.add(view);

		JMenu help = new JMenu("Help");
		help.setMnemonic(KeyEvent.VK_H);
		help.add(new JMenuItem(action("Shortcuts\u2026", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), null,
				this::showShortcuts)));
		help.add(new JMenuItem(action("About\u2026", null, null, this::showAbout)));
		bar.add(help);
		return bar;
	}

	/** Menu item that shares its model with a toolbar checkbox so both stay in sync. */
	private static JCheckBoxMenuItem sharedCheckItem(String text, JCheckBox checkBox) {
		JCheckBoxMenuItem item = new JCheckBoxMenuItem(text);
		item.setModel(checkBox.getModel());
		return item;
	}

	private void layoutComponents() {
		setLayout(new BorderLayout(4, 4));
		add(createToolbar(), BorderLayout.NORTH);

		graphSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, graphPanel, minimapPanel);
		graphSplitPane.setResizeWeight(0.8);
		graphSplitPane.setOneTouchExpandable(true);
		graphPanel.setMinimumSize(new Dimension(300, 200));
		minimapPanel.setMinimumSize(new Dimension(150, 80));

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphSplitPane, detailPanel);
		mainSplitPane.setResizeWeight(0.7);
		mainSplitPane.setOneTouchExpandable(true);
		detailPanel.setMinimumSize(new Dimension(220, 200));
		add(mainSplitPane, BorderLayout.CENTER);

		JPanel status = new JPanel(new BorderLayout());
		status.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
				BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		status.add(statusLabel, BorderLayout.CENTER);
		status.add(zoomLabel, BorderLayout.EAST);
		add(status, BorderLayout.SOUTH);
	}

	private JPanel createToolbar() {
		JPanel bar = new JPanel();
		bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
		bar.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));

		JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		row1.add(new JLabel("Search:"));
		row1.add(searchField);
		row1.add(separator());
		row1.add(new JLabel("Category:"));
		row1.add(categoryFilter);
		row1.add(separator());
		row1.add(new JLabel("Layout:"));
		row1.add(layoutComboBox);
		row1.add(new JButton(relayoutAction));
		row1.add(new JButton(fitAction));

		JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		row2.add(new JLabel("Show:"));
		row2.add(showLabelsCheckBox);
		row2.add(showGroupsCheckBox);
		row2.add(dimUnrelatedCheckBox);
		row2.add(separator());
		row2.add(new JLabel("Group by same:"));
		row2.add(groupByInputCheckBox);
		row2.add(groupByOutputCheckBox);
		row2.add(separator());
		row2.add(hideToolsCheckBox);
		row2.add(separator());
		row2.add(new JButton(reloadAction));
		row2.add(new JButton(exportAction));

		bar.add(row1);
		bar.add(row2);
		return bar;
	}

	private static JSeparator separator() {
		JSeparator s = new JSeparator(SwingConstants.VERTICAL);
		s.setPreferredSize(new Dimension(2, 22));
		return s;
	}

	// =====================================================================
	// Events
	// =====================================================================

	private void setupEventListeners() {
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) { searchDebounce.restart(); }

			@Override
			public void removeUpdate(DocumentEvent e) { searchDebounce.restart(); }

			@Override
			public void changedUpdate(DocumentEvent e) { searchDebounce.restart(); }
		});
		searchField.addActionListener(e -> { // Enter = filter now
			searchDebounce.stop();
			filterAlgorithms(false);
		});
		searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSearch");
		searchField.getActionMap().put("clearSearch", action("clear", null, null, () -> {
			if (searchField.getText().isEmpty()) {
				graphPanel.requestFocusInWindow();
			} else {
				searchField.setText("");
			}
		}));

		categoryFilter.addActionListener(e -> filterAlgorithms(false));
		hideToolsCheckBox.addActionListener(e -> filterAlgorithms(false));
		layoutComboBox.addActionListener(e -> applyLayout(false));

		showLabelsCheckBox.addActionListener(e -> graphPanel.setShowLabels(showLabelsCheckBox.isSelected()));
		showGroupsCheckBox.addActionListener(e -> graphPanel.setShowClusters(showGroupsCheckBox.isSelected()));
		dimUnrelatedCheckBox
				.addActionListener(e -> graphPanel.setDimUnrelated(dimUnrelatedCheckBox.isSelected()));
		groupByInputCheckBox.addActionListener(e -> groupingChanged());
		groupByOutputCheckBox.addActionListener(e -> groupingChanged());

		// global accelerators that are not in a menu
		getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, MENU_MASK), "focusSearch");
		getRootPane().getActionMap().put("focusSearch", focusSearchAction);
	}

	private void groupingChanged() {
		graphPanel.setClusterByInput(groupByInputCheckBox.isSelected());
		graphPanel.setClusterByOutput(groupByOutputCheckBox.isSelected());
		if (layoutComboBox.getSelectedItem() == GraphPanel.LayoutType.FORCE_DIRECTED) {
			applyLayout(false); // groups drive the force layout → continue from current positions
		}
		graphPanel.refreshSelection();
		updateStatus();
	}

	// =====================================================================
	// Actions
	// =====================================================================

	private void filterAlgorithms(boolean resetLayout) {
		if (updatingControls || disposed) {
			return;
		}
		Object category = categoryFilter.getSelectedItem();
		String cat = category == null || ALL_CATEGORIES.equals(category) ? null : category.toString();
		model.applyFilter(new AlgorithmGraphModel.Filter(searchField.getText(), cat, hideToolsCheckBox.isSelected()));

		graphPanel.invalidateClusters();
		applyLayout(resetLayout);
		graphPanel.refreshSelection();
	}

	private void applyLayout(boolean reset) {
		if (disposed) {
			return;
		}
		GraphPanel.LayoutType type = (GraphPanel.LayoutType) layoutComboBox.getSelectedItem();
		graphPanel.applyLayout(type == null ? GraphPanel.LayoutType.FORCE_DIRECTED : type, reset);
		minimapPanel.repaint();
		updateStatus();
	}

	private void reloadAlgorithms() {
		graphPanel.stopLayoutTimer();
		graphPanel.clearSelection();
		model.reload();
		populateCategoryFilter();
		graphPanel.invalidateClusters();
		filterAlgorithms(true);
	}

	private void exportAsPNG() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export graph as PNG");
		chooser.setFileFilter(new FileNameExtensionFilter("PNG image (*.png)", "png"));
		chooser.setSelectedFile(new File("spmf_algorithm_graph.png"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".png")) {
			file = new File(file.getParentFile(), file.getName() + ".png");
		}
		if (file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Overwrite?",
				"Confirm overwrite", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		BufferedImage image = graphPanel.renderImage(EXPORT_MAX_DIMENSION);
		if (image == null) {
			JOptionPane.showMessageDialog(this, "Nothing to export: no algorithm is currently visible.", "Export",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		try {
			ImageIO.write(image, "png", file);
			statusLabel.setText("Exported " + image.getWidth() + "\u00d7" + image.getHeight() + " px to "
					+ file.getAbsolutePath());
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Could not write the image:\n" + ex.getMessage(), "Export failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void showShortcuts() {
		String html = "<html><body style='font-family:sans-serif'><table cellpadding=3>"
				+ "<tr><th align=left>Graph</th><th></th></tr>"
				+ "<tr><td>Mouse wheel</td><td>zoom around the cursor</td></tr>"
				+ "<tr><td>Drag background</td><td>pan</td></tr>"
				+ "<tr><td>Drag node</td><td>move it (pins it for the force layout)</td></tr>"
				+ "<tr><td>Click node / background</td><td>select / clear</td></tr>"
				+ "<tr><td>Double-click node / background</td><td>centre on it / fit to view</td></tr>"
				+ "<tr><td>Right-click</td><td>context menu</td></tr>"
				+ "<tr><td>+ / &minus; / 0</td><td>zoom in / out / fit (graph focused)</td></tr>"
				+ "<tr><td>Arrow keys</td><td>pan</td></tr>"
				+ "<tr><td>Esc</td><td>clear selection</td></tr>"
				+ "<tr><th align=left style='padding-top:8px'>Window</th><th></th></tr>"
				+ "<tr><td>Ctrl+F</td><td>search</td></tr>"
				+ "<tr><td>Ctrl+0 / Ctrl+R</td><td>fit / re-layout</td></tr>"
				+ "<tr><td>Ctrl+E / F5 / Ctrl+W</td><td>export / reload / close</td></tr>"
				+ "</table></body></html>";
		JOptionPane.showMessageDialog(this, html, "Shortcuts", JOptionPane.PLAIN_MESSAGE);
	}

	private void showAbout() {
		JOptionPane.showMessageDialog(this,
				"<html><b>SPMF Algorithm Graph Visualizer</b><br>" + model.getAlgorithmCount() + " algorithms in "
						+ model.getCategoryCount() + " categories.<br><br>"
						+ "Algorithms are grouped when they share the same input/output file types.<br>"
						+ "Part of SPMF \u2013 http://www.philippe-fournier-viger.com/spmf</html>",
				"About", JOptionPane.INFORMATION_MESSAGE);
	}

	// =====================================================================
	// Status bar
	// =====================================================================

	private void updateStatus() {
		if (disposed) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Showing ").append(model.getVisibleCount()).append(" of ").append(model.getAlgorithmCount())
				.append(" algorithms in ").append(model.getCategoryCount()).append(" categories");
		int groups = graphPanel.getClusterCount();
		if (groups > 0) {
			sb.append("  \u00b7  ").append(groups).append(" groups with identical input/output");
		}
		int pinned = graphPanel.getPinnedCount();
		if (pinned > 0) {
			sb.append("  \u00b7  ").append(pinned).append(" pinned");
		}
		AlgorithmNode sel = graphPanel.getSelectedNode();
		if (sel != null) {
			sb.append("  \u00b7  Selected: ").append(sel.getName());
		}
		statusLabel.setText(sb.toString());
		unpinAction.setEnabled(pinned > 0);
	}

	private void updateZoomLabel() {
		if (disposed) {
			return;
		}
		String text = "Zoom: " + Math.round(graphPanel.getScale() * 100) + "%";
		if (graphPanel.isLayoutRunning()) {
			text = "Layout running\u2026  \u00b7  " + text;
		}
		zoomLabel.setText(text);
	}

	// =====================================================================
	// Preferences & disposal
	// =====================================================================

	private void savePreferences() {
		try {
			Object layout = layoutComboBox.getSelectedItem();
			if (layout instanceof GraphPanel.LayoutType) {
				prefs.put(PREF_LAYOUT, ((GraphPanel.LayoutType) layout).name());
			}
			prefs.putBoolean(PREF_LABELS, showLabelsCheckBox.isSelected());
			prefs.putBoolean(PREF_GROUPS, showGroupsCheckBox.isSelected());
			prefs.putBoolean(PREF_DIM, dimUnrelatedCheckBox.isSelected());
			prefs.putBoolean(PREF_BY_INPUT, groupByInputCheckBox.isSelected());
			prefs.putBoolean(PREF_BY_OUTPUT, groupByOutputCheckBox.isSelected());
			prefs.putBoolean(PREF_HIDE_TOOLS, hideToolsCheckBox.isSelected());
			if ((getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
				Rectangle b = getBounds();
				prefs.put(PREF_BOUNDS, b.x + "," + b.y + "," + b.width + "," + b.height);
			}
			if (mainSplitPane.getWidth() > 0) {
				prefs.putDouble(PREF_MAIN_DIVIDER,
						mainSplitPane.getDividerLocation() / (double) mainSplitPane.getWidth());
			}
			if (graphSplitPane.getHeight() > 0) {
				prefs.putDouble(PREF_GRAPH_DIVIDER,
						graphSplitPane.getDividerLocation() / (double) graphSplitPane.getHeight());
			}
		} catch (RuntimeException e) {
			// preferences are best-effort
		}
	}

	/** Release timers and listeners. Safe to call more than once. */
	public void disposeResources() {
		if (disposed) {
			return;
		}
		disposed = true;
		if (searchDebounce != null) {
			searchDebounce.stop();
		}
		if (graphPanel != null) {
			graphPanel.dispose();
		}
		if (minimapPanel != null) {
			minimapPanel.dispose();
		}
		model.clear();
	}

	@Override
	public void dispose() {
		savePreferences();
		disposeResources();
		super.dispose();
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new AlgorithmGraphVisualizer(true));
	}
}