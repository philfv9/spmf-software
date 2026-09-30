package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternType;
import ca.pfv.spmf.gui.visual_pattern_viewer.VisualPatternViewer;

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
 * The main window of the Interactive Pattern Explorer. The user progressively
 * builds a query (required / excluded items, size, measure ranges, text) and
 * every view is updated: the drill-down list of candidate items, the
 * aggregated charts, and the explicit list of matching patterns. Each
 * restriction is shown as a chip in the query bar and can be removed by
 * clicking its 'x'; the 'Clear all' button removes every restriction.
 * <p>
 * Layout:
 * 
 * <pre>
 * Current query: [+ milk x] [+ bread x]   [Clear all]   Search: [......][mode][Go]
 * ---------------------------+---------------------------------------------------
 * Drill down by item         | Overview: summary, size chart, measure histogram,
 * (candidate items table)    |           measure statistics
 *                            +---------------------------------------------------
 *                            | Matching patterns (sortable table)
 * ---------------------------+---------------------------------------------------
 * status bar
 * </pre>
 *
 * @author Philippe Fournier-Viger 2025
 */
public class PatternExplorerViewer extends JFrame implements ExplorerController {

	private static final long serialVersionUID = 1L;

	/** Window title */
	private static final String WINDOW_TITLE = "SPMF Interactive Pattern Explorer ";

	/** Search modes */
	private static final String MODE_TEXT = "Text contains";
	private static final String MODE_ITEMS = "Contains items";
	private static final String MODE_EXACT = "Exact pattern";

	/** Path to the pattern file */
	private final String filePath;
	/** Pattern type (may be null) */
	private final PatternType patternType;
	/** The indexed collection */
	private final PatternCollection collection;
	/** Query history (kept internally to hold the current query state) */
	private final ExplorationHistory history;
	/** Result of the current query */
	private QueryResult currentResult;

	// UI components
	private QueryChipsPanel chipsPanel;
	private CandidateItemsPanel candidatesPanel;
	private OverviewPanel overviewPanel;
	private PatternTablePanel tablePanel;
	private JTextField searchField;
	private JComboBox<String> searchModeCombo;
	private JLabel statusLabel;
	private JLabel countLabel;
	private JSplitPane mainSplit;
	private JSplitPane rightSplit;
	private JScrollPane candidatesScroll;

	// ---------------------------------------------------------------
	// Constructor
	// ---------------------------------------------------------------

	/**
	 * Constructor
	 *
	 * @param runAsStandaloneProgram if true, closing the window exits the JVM
	 * @param filePath               path to the pattern file to explore
	 * @param patternType            type of patterns (null = itemsets)
	 * @throws IOException if error reading the input file
	 */
	public PatternExplorerViewer(boolean runAsStandaloneProgram, String filePath, PatternType patternType)
			throws IOException {
		super(WINDOW_TITLE + Main.SPMF_VERSION);
		if (runAsStandaloneProgram) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		} else {
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		}
		this.filePath = filePath;
		this.patternType = patternType;

		// Load and index the patterns
		this.collection = PatternExplorerLoader.load(filePath, patternType);
		this.history = new ExplorationHistory(ExplorerQuery.UNRESTRICTED);

		buildUserInterface();
		setJMenuBar(createMenuBar());
		installKeyboardShortcuts();

		setTitle(WINDOW_TITLE + Main.SPMF_VERSION + " - " + new File(filePath).getName());
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		setSize(Math.min(1350, (int) (screen.width * 0.92)), Math.min(880, (int) (screen.height * 0.9)));
		setMinimumSize(new Dimension(800, 500));
		setLocationRelativeTo(null);

		refresh();
		SwingUtilities.invokeLater(() -> {
			mainSplit.setDividerLocation(0.32);
			rightSplit.setDividerLocation(0.42);
		});
	}

	// ---------------------------------------------------------------
	// User interface
	// ---------------------------------------------------------------

	private void buildUserInterface() {
		getContentPane().setLayout(new BorderLayout());

		// ---- Top: query chips (with "Clear all") and search on one row ----
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ExplorerStyle.TOOLBAR_BG);
		top.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ExplorerStyle.BORDER),
				BorderFactory.createEmptyBorder(2, 0, 2, 6)));

		// Query chips + "Clear all" button (left / center)
		chipsPanel = new QueryChipsPanel(this);
		top.add(chipsPanel, BorderLayout.CENTER);

		// Search box (right, same line)
		JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		searchPanel.setOpaque(false);
		JLabel searchLabel = new JLabel("Search:");
		searchLabel.setFont(ExplorerStyle.FONT_NORMAL);
		searchField = new JTextField(18);
		searchField.setFont(ExplorerStyle.FONT_NORMAL);
		searchField.setToolTipText("<html>Type item names separated by spaces and press Enter.<br>"
				+ "<b>Text contains</b>: patterns whose text contains all the words<br>"
				+ "<b>Contains items</b>: patterns containing all these items<br>"
				+ "<b>Exact pattern</b>: the pattern made exactly of these items<br>"
				+ "(Ctrl+F focuses this box)</html>");
		searchField.addActionListener(e -> runSearch());
		searchModeCombo = new JComboBox<>(new String[] { MODE_TEXT, MODE_ITEMS, MODE_EXACT });
		searchModeCombo.setFont(ExplorerStyle.FONT_SMALL);
		searchModeCombo.setToolTipText("How the search text is interpreted");
		JButton goButton = new JButton("Go");
		ExplorerStyle.makeCompact(goButton);
		goButton.addActionListener(e -> runSearch());
		searchPanel.add(searchLabel);
		searchPanel.add(searchField);
		searchPanel.add(searchModeCombo);
		searchPanel.add(goButton);
		top.add(searchPanel, BorderLayout.EAST);

		getContentPane().add(top, BorderLayout.NORTH);

		// ---- Center: candidates | (overview / table) ----
		candidatesPanel = new CandidateItemsPanel(this, collection);
		candidatesScroll = new JScrollPane(candidatesPanel);
		candidatesScroll.setBorder(null);
		candidatesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		overviewPanel = new OverviewPanel(this, collection);
		tablePanel = new PatternTablePanel(this, collection);

		rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, overviewPanel, tablePanel);
		rightSplit.setResizeWeight(0.4);
		rightSplit.setOneTouchExpandable(true);
		rightSplit.setBorder(null);

		mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, candidatesScroll, rightSplit);
		mainSplit.setResizeWeight(0.3);
		mainSplit.setOneTouchExpandable(true);
		mainSplit.setBorder(null);
		getContentPane().add(mainSplit, BorderLayout.CENTER);

		// ---- Bottom: status bar ----
		JPanel statusBar = new JPanel(new BorderLayout());
		statusBar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, ExplorerStyle.BORDER),
				BorderFactory.createEmptyBorder(3, 8, 3, 8)));
		statusLabel = new JLabel(" ");
		statusLabel.setFont(ExplorerStyle.FONT_SMALL);
		statusLabel.setForeground(new Color(80, 80, 80));
		countLabel = new JLabel(" ");
		countLabel.setFont(ExplorerStyle.FONT_SMALL_BOLD);
		countLabel.setForeground(new Color(60, 60, 60));
		statusBar.add(statusLabel, BorderLayout.WEST);
		statusBar.add(countLabel, BorderLayout.EAST);
		getContentPane().add(statusBar, BorderLayout.SOUTH);
	}

	private void installKeyboardShortcuts() {
		JComponent root = getRootPane();
		bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focusSearch", () -> {
			searchField.requestFocusInWindow();
			searchField.selectAll();
		});
	}

	private static void bind(JComponent component, KeyStroke key, String name, Runnable action) {
		component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
		component.getActionMap().put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	// ---------------------------------------------------------------
	// Menu bar
	// ---------------------------------------------------------------

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		// ---- File ----
		JMenu menuFile = new JMenu("File");
		JMenuItem itemOpen = new JMenuItem("Open another pattern file\u2026");
		itemOpen.addActionListener(e -> openAnotherFile());
		menuFile.add(itemOpen);
		JMenuItem itemExport = new JMenuItem("Export matching patterns\u2026");
		itemExport.addActionListener(e -> tablePanel.exportPatterns(this));
		menuFile.add(itemExport);
		menuFile.addSeparator();
		JMenuItem itemClose = new JMenuItem("Close");
		itemClose.addActionListener(e -> dispose());
		menuFile.add(itemClose);
		menuBar.add(menuFile);

		// ---- Query ----
		JMenu menuQuery = new JMenu("Query");
		JMenuItem itemClear = new JMenuItem("Clear all restrictions");
		itemClear.addActionListener(e -> clearAll());
		JMenuItem itemSearch = new JMenuItem("Search\u2026");
		itemSearch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
		itemSearch.addActionListener(e -> {
			searchField.requestFocusInWindow();
			searchField.selectAll();
		});
		menuQuery.add(itemClear);
		menuQuery.addSeparator();
		menuQuery.add(itemSearch);
		menuBar.add(menuQuery);

		// ---- View ----
		JMenu menuView = new JMenu("View");
		JCheckBoxMenuItem itemCandidates = new JCheckBoxMenuItem("Drill-down panel", true);
		itemCandidates.addActionListener(e -> {
			candidatesScroll.setVisible(itemCandidates.isSelected());
			mainSplit.resetToPreferredSizes();
			if (itemCandidates.isSelected()) {
				mainSplit.setDividerLocation(0.32);
			}
			getContentPane().revalidate();
			getContentPane().repaint();
		});
		JCheckBoxMenuItem itemOverview = new JCheckBoxMenuItem("Overview (charts and statistics)", true);
		itemOverview.addActionListener(e -> {
			overviewPanel.setVisible(itemOverview.isSelected());
			rightSplit.resetToPreferredSizes();
			if (itemOverview.isSelected()) {
				rightSplit.setDividerLocation(0.42);
			}
			getContentPane().revalidate();
			getContentPane().repaint();
		});
		JCheckBoxMenuItem itemTable = new JCheckBoxMenuItem("Pattern list", true);
		itemTable.addActionListener(e -> {
			tablePanel.setVisible(itemTable.isSelected());
			rightSplit.resetToPreferredSizes();
			getContentPane().revalidate();
			getContentPane().repaint();
		});
		menuView.add(itemCandidates);
		menuView.add(itemOverview);
		menuView.add(itemTable);
		menuBar.add(menuView);

		// ---- Tools ----
		JMenu menuTools = new JMenu("Tools");
		JMenuItem itemTextEditor = new JMenuItem("Open this file using the system text editor");
		itemTextEditor.addActionListener(e -> openInTextEditor());
		menuTools.add(itemTextEditor);
		JMenuItem itemVisualViewer = new JMenuItem("Open this file in the Visual Pattern Viewer");
		itemVisualViewer.addActionListener(e -> {
			try {
				PatternType type = patternType == null ? PatternType.ITEMSETS : patternType;
				new VisualPatternViewer(false, filePath, type).setVisible(true);
			} catch (Exception ex) {
				showError("Cannot open the Visual Pattern Viewer: " + ex.getMessage());
			}
		});
		menuTools.add(itemVisualViewer);
		menuBar.add(menuTools);

		// ---- Help ----
		JMenu menuHelp = new JMenu("Help");
		JMenuItem itemHowTo = new JMenuItem("How to use the explorer");
		itemHowTo.addActionListener(e -> showHelp());
		menuHelp.add(itemHowTo);
		menuBar.add(menuHelp);

		return menuBar;
	}

	// ---------------------------------------------------------------
	// Refresh
	// ---------------------------------------------------------------

	/**
	 * Evaluate the current query and update every view.
	 */
	private void refresh() {
		ExplorerQuery query = history.current();
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try {
			currentResult = collection.evaluate(query);
			chipsPanel.update(query);
			candidatesPanel.update(currentResult);
			overviewPanel.update(currentResult);
			tablePanel.update(currentResult);
			updateStatusBar();
		} finally {
			setCursor(Cursor.getDefaultCursor());
		}
	}

	private void updateStatusBar() {
		StringBuilder sb = new StringBuilder();
		sb.append(collection.getSourceDescription());
		sb.append("  \u00B7  ").append(ExplorerFormat.count(collection.size(), "pattern"));
		sb.append("  \u00B7  ").append(ExplorerFormat.count(collection.getItemCount(), "distinct item"));
		int measures = collection.getMeasureNames().size();
		if (measures > 0) {
			sb.append("  \u00B7  ").append(ExplorerFormat.count(measures, "measure"));
			int numeric = collection.getNumericMeasureNames().size();
			if (numeric != measures) {
				sb.append(" (").append(numeric).append(" numeric)");
			}
		}
		statusLabel.setText(sb.toString());
		if (currentResult.size() == collection.size()) {
			countLabel.setText(ExplorerFormat.integer(collection.size()) + " patterns ");
		} else {
			countLabel.setText(ExplorerFormat.integer(currentResult.size()) + " / "
					+ ExplorerFormat.integer(collection.size()) + " patterns ("
					+ ExplorerFormat.percent(currentResult.getFractionOfCollection()) + ") ");
		}
	}

	// ---------------------------------------------------------------
	// Clear all
	// ---------------------------------------------------------------

	/**
	 * Remove every restriction and clear the search box.
	 */
	private void clearAll() {
		searchField.setText("");
		applyQuery(ExplorerQuery.UNRESTRICTED);
	}

	// ---------------------------------------------------------------
	// Search box
	// ---------------------------------------------------------------

	private void runSearch() {
		String text = searchField.getText().trim();
		String mode = (String) searchModeCombo.getSelectedItem();
		if (MODE_TEXT.equals(mode)) {
			applyTextSearch(text);
			return;
		}
		if (text.isEmpty()) {
			return;
		}
		// Parse item names (separated by spaces or commas; braces are ignored)
		String cleaned = text.replace('{', ' ').replace('}', ' ').replace(',', ' ');
		Set<String> items = new LinkedHashSet<>();
		List<String> unknown = new ArrayList<>();
		for (String token : cleaned.split("\\s+")) {
			if (token.isEmpty()) {
				continue;
			}
			if (collection.getItemId(token) >= 0) {
				items.add(token);
			} else {
				unknown.add(token);
			}
		}
		if (!unknown.isEmpty()) {
			// Try a case-insensitive match before giving up
			List<String> stillUnknown = new ArrayList<>();
			for (String u : unknown) {
				String match = findItemIgnoreCase(u);
				if (match != null) {
					items.add(match);
				} else {
					stillUnknown.add(u);
				}
			}
			if (!stillUnknown.isEmpty()) {
				JOptionPane.showMessageDialog(this,
						"These items do not appear in the collection:\n" + String.join(", ", stillUnknown)
								+ (items.isEmpty() ? "" : "\n\nThe other items will be used."),
						"Unknown item(s)", JOptionPane.WARNING_MESSAGE);
				if (items.isEmpty()) {
					return;
				}
			}
		}
		if (MODE_ITEMS.equals(mode)) {
			requireItems(items);
		} else {
			// Exact pattern: require all items and forbid any other item
			ExplorerQuery q = getCurrentQuery().requireItems(items).restrictWithin(items);
			applyQuery(q);
		}
		searchField.selectAll();
	}

	private String findItemIgnoreCase(String name) {
		int n = collection.getItemCount();
		for (int i = 0; i < n; i++) {
			if (collection.getItemName(i).equalsIgnoreCase(name)) {
				return collection.getItemName(i);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------
	// ExplorerController
	// ---------------------------------------------------------------

	@Override
	public ExplorerQuery getCurrentQuery() {
		return history.current();
	}

	@Override
	public void applyQuery(ExplorerQuery query) {
		if (query == null) {
			return;
		}
		// When everything is cleared (e.g. via the "Clear all" button), also
		// clear the search box so that the UI is consistent
		if (query.isUnrestricted()) {
			searchField.setText("");
		}
		if (history.push(query)) {
			refresh();
		}
	}

	@Override
	public void requireItems(Collection<String> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		applyQuery(getCurrentQuery().requireItems(items));
	}

	@Override
	public void excludeItems(Collection<String> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		ExplorerQuery q = getCurrentQuery();
		for (String item : items) {
			q = q.excludeItem(item);
		}
		applyQuery(q);
	}

	@Override
	public void restrictWithin(Collection<String> items) {
		if (items == null || items.isEmpty()) {
			return;
		}
		ExplorerQuery current = getCurrentQuery();
		// The required items must remain allowed
		Set<String> allowed = new LinkedHashSet<>(current.getRequiredItems());
		allowed.addAll(items);
		applyQuery(current.restrictWithin(allowed));
	}

	@Override
	public void applyTextSearch(String text) {
		ExplorerQuery current = getCurrentQuery();
		if (text == null || text.isBlank()) {
			applyQuery(current.clearTextFilter());
		} else {
			applyQuery(current.withTextFilter(text));
		}
	}

	@Override
	public void toggleSize(int size) {
		ExplorerQuery current = getCurrentQuery();
		if (current.getMinSize() == size && current.getMaxSize() == size) {
			applyQuery(current.clearSizeRange());
		} else {
			applyQuery(current.withSizeRange(size, size));
		}
	}

	@Override
	public void setMeasureRange(String measure, double min, double max) {
		if (measure == null || !collection.isNumericMeasure(measure)) {
			return;
		}
		double lo = Math.min(min, max);
		double hi = Math.max(min, max);
		applyQuery(getCurrentQuery().withMeasureRange(measure, lo, hi));
	}

	@Override
	public void showPatternDetails(int patternIndex) {
		if (patternIndex < 0 || patternIndex >= collection.size()) {
			return;
		}
		new PatternDetailsDialog(this, this, collection, patternIndex, currentResult).setVisible(true);
	}

	// ---------------------------------------------------------------
	// Tools
	// ---------------------------------------------------------------

	private void openAnotherFile() {
		JFileChooser chooser = new JFileChooser(new File(filePath).getParentFile());
		chooser.setDialogTitle("Open a pattern file");
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		PatternType type = askPatternType(this);
		if (type == null && !confirmGeneric(this)) {
			return;
		}
		try {
			PatternExplorerViewer viewer = new PatternExplorerViewer(false,
					chooser.getSelectedFile().getAbsolutePath(), type);
			viewer.setVisible(true);
		} catch (Exception ex) {
			showError("Cannot open the file: " + ex.getMessage());
		}
	}

	private static final String TYPE_ITEMSETS = "Itemsets (frequent, high-utility, closed, ...)";
	private static final String TYPE_UNCERTAIN = "Uncertain itemsets";
	private static final String TYPE_GENERIC = "Other patterns (rules, sequences...) - items only";

	/**
	 * Ask the user for the type of the file.
	 * 
	 * @param parent parent component
	 * @return the pattern type, or null for the generic loader (also null if
	 *         cancelled: use {@link #confirmGeneric(Component)} to distinguish)
	 */
	private static PatternType askPatternType(Component parent) {
		String[] options = { TYPE_ITEMSETS, TYPE_UNCERTAIN, TYPE_GENERIC };
		Object choice = JOptionPane.showInputDialog(parent, "What kind of patterns does the file contain?",
				"Pattern type", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (TYPE_ITEMSETS.equals(choice)) {
			return PatternType.ITEMSETS;
		}
		if (TYPE_UNCERTAIN.equals(choice)) {
			return PatternType.UNCERTAIN_ITEMSETS;
		}
		lastChoiceWasGeneric = TYPE_GENERIC.equals(choice);
		return null;
	}

	private static boolean lastChoiceWasGeneric = false;

	private static boolean confirmGeneric(Component parent) {
		return lastChoiceWasGeneric;
	}

	private void openInTextEditor() {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				File f = new File(filePath);
				if (f.exists() && f.canRead()) {
					Desktop.getDesktop().open(f);
				} else {
					showError("Cannot open file:\n" + filePath);
				}
			} else {
				showError("Desktop API not supported on this system.");
			}
		} catch (IOException ex) {
			showError("Failed to open file:\n" + ex.getMessage());
		}
	}

	private void showHelp() {
		String text = "INTERACTIVE PATTERN EXPLORER\n\n"
				+ "The explorer lets you navigate a large collection of patterns without displaying\n"
				+ "all of them at once. You progressively build a query; every view is updated.\n\n"
				+ "DRILL DOWN BY ITEM (left)\n"
				+ "  Lists the items of the matching patterns with the number of patterns containing\n"
				+ "  each item and statistics of the measures. Double-click (or '+ Require') to keep\n"
				+ "  only patterns containing the item, '- Exclude' to remove them, 'Only these' to\n"
				+ "  keep patterns made exclusively of the selected items (their subsets).\n\n"
				+ "OVERVIEW (top right)\n"
				+ "  Click or drag on the size chart to restrict the number of items. Choose any\n"
				+ "  numeric measure in the histogram and drag to keep a range of values.\n"
				+ "  The table lists statistics of all numeric measures for the current selection.\n\n"
				+ "MATCHING PATTERNS (bottom right)\n"
				+ "  The actual patterns with one column per measure. Click a header to sort,\n"
				+ "  double-click a row for details (subsets, supersets, closed / maximal), and\n"
				+ "  right-click for actions. 'Compute closed / maximal' adds these flags relative\n"
				+ "  to the current selection.\n\n"
				+ "CURRENT QUERY (top)\n"
				+ "  Each restriction is a chip with an 'x' to remove it individually.\n"
				+ "  'Clear all' removes every restriction and returns to the whole collection.\n\n"
				+ "SEARCH (Ctrl+F)\n"
				+ "  'Text contains': patterns whose text contains all the words.\n"
				+ "  'Contains items': require the given items.  'Exact pattern': that pattern only.\n\n"
				+ "Itemsets are treated as unordered sets: the order of items in the file is ignored.";
		JTextArea area = new JTextArea(text, 28, 78);
		area.setEditable(false);
		area.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
		area.setCaretPosition(0);
		JOptionPane.showMessageDialog(this, new JScrollPane(area), "How to use the Interactive Pattern Explorer",
				JOptionPane.INFORMATION_MESSAGE);
	}

	private void showError(String message) {
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	// ---------------------------------------------------------------
	// Main
	// ---------------------------------------------------------------

	/**
	 * Run the explorer as a standalone program.
	 * 
	 * @param args [0] = path to the pattern file (optional, a chooser is shown
	 *             otherwise), [1] = pattern type name (optional, e.g. ITEMSETS,
	 *             UNCERTAIN_ITEMSETS, GENERIC)
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				String path;
				PatternType type;
				if (args.length > 0) {
					path = args[0];
					if (args.length > 1) {
						type = "GENERIC".equalsIgnoreCase(args[1]) ? null : PatternType.valueOf(args[1]);
					} else {
						type = PatternType.ITEMSETS;
					}
				} else {
					JFileChooser chooser = new JFileChooser();
					chooser.setDialogTitle("Open a pattern file");
					if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
						return;
					}
					path = chooser.getSelectedFile().getAbsolutePath();
					type = askPatternType(null);
					if (type == null && !confirmGeneric(null)) {
						return;
					}
				}
				new PatternExplorerViewer(true, path, type).setVisible(true);
			} catch (Exception ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(null, "Cannot open the file: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		});
	}
}