package ca.pfv.spmf.gui.viewers.sequential_pattern_explorer;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import ca.pfv.spmf.gui.Main;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license.
 */

/**
 * Sequential Pattern Explorer. The user builds one simple query:
 *
 * <pre>
 *   Patterns that start with [A] and end with [B]
 *   and contain [C] [D] but do not contain [E]
 * </pre>
 *
 * Items are added to the query from the item table on the left (or from the
 * "what comes right after / before" panel on the right). The matching patterns
 * are shown in a sortable table with one column per measure. One button clears
 * the whole query.
 *
 * @author Philippe Fournier-Viger 2026
 */
public class SequentialPatternExplorer extends JFrame {

	private static final long serialVersionUID = 1L;

	/** An item with its pre-computed counts. */
	private static class ItemEntry {
		final String item;
		int startCount, endCount, containsCount;
		ItemEntry(String item) { this.item = item; }
	}

	// ---- look ----
	private static final Color ACCENT = new Color(0, 105, 200);
	private static final Color ACCENT_BAR = new Color(214, 231, 250);
	private static final Color STRIPE = new Color(246, 248, 251);
	private static final Color MUTED = new Color(115, 115, 115);
	private static final Color PLACEHOLDER = new Color(165, 165, 165);
	private static final Color LINE = new Color(215, 215, 215);
	private static final Font TEXT = new Font("SansSerif", Font.PLAIN, 13);
	private static final Font TEXT_BOLD = TEXT.deriveFont(Font.BOLD);
	private static final Font BIG = new Font("SansSerif", Font.PLAIN, 15);
	private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);
	private static final Font SECTION = new Font("SansSerif", Font.BOLD, 11);

	// chip colours: foreground / background
	private static final Color START_FG = ACCENT, START_BG = ACCENT_BAR;
	private static final Color END_FG = new Color(190, 80, 0), END_BG = new Color(253, 230, 210);
	private static final Color CONTAIN_FG = new Color(40, 40, 40), CONTAIN_BG = new Color(232, 232, 232);
	private static final Color EXCLUDE_FG = new Color(180, 30, 30), EXCLUDE_BG = new Color(252, 220, 220);

	private final boolean runAsStandaloneProgram;

	// ---- data ----
	private List<SeqPattern> allPatterns = new ArrayList<>();
	private List<String> measureNames = new ArrayList<>();
	private List<ItemEntry> allItems = new ArrayList<>();
	private final Map<String, ItemEntry> itemIndex = new HashMap<>();
	private int maxContainsCount = 1;

	// ---- the query ----
	private String startItem = null;
	private String endItem = null;
	private final Set<String> containItems = new LinkedHashSet<>();
	private final Set<String> excludeItems = new LinkedHashSet<>();
	private boolean suppressEvents = false;

	// ---- components ----
	private JLabel fileLabel, summaryLabel;
	private JButton exportButton;
	private CardLayout cards;
	private JPanel cardPanel;

	private JTextField searchField;
	private ItemTableModel itemModel;
	private JTable itemTable;
	private TableRowSorter<ItemTableModel> itemSorter;
	private JButton startWithButton, endWithButton, containButton, excludeButton;

	private JPanel queryRow1, queryRow2;
	private JButton clearQueryButton;

	private JLabel countLabel;
	private JPanel measurePart;
	private JComboBox<String> measureCombo;
	private JTextField measureMin, measureMax, itemsMin, itemsMax;

	private SeqPatternTableModel tableModel;
	private JTable table;
	private TableRowSorter<SeqPatternTableModel> sorter;
	private PatternRenderer patternRenderer;
	private CardLayout tableCards;
	private JPanel tableCardPanel;
	private JLabel noResultsLabel;

	private JComboBox<String> neighborDirCombo;
	private JLabel neighborTitle;
	private DefaultTableModel neighborModel;
	private JTable neighborTable;
	private JButton nContainButton, nExcludeButton, nExploreButton;

	private JLabel detailPattern, detailMeasures;
	private JButton copyButton;

	// =========================================================================
	// Constructors
	// =========================================================================

	public SequentialPatternExplorer() { this(false); }

	public SequentialPatternExplorer(boolean runAsStandaloneProgram) {
		this.runAsStandaloneProgram = runAsStandaloneProgram;
		setTitle(baseTitle());
		setDefaultCloseOperation(runAsStandaloneProgram ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
		setSize(1280, 780);
		setLocationRelativeTo(null);
		initComponents();
		installShortcuts();
	}

	public SequentialPatternExplorer(String filePath, boolean runAsStandaloneProgram) {
		this(runAsStandaloneProgram);
		loadFile(filePath);
	}

	public SequentialPatternExplorer(List<SeqPattern> patterns, boolean runAsStandaloneProgram) {
		this(runAsStandaloneProgram);
		setPatterns(patterns, null, "(in-memory patterns)");
	}

	private String baseTitle() {
		return (runAsStandaloneProgram ? "SPMF v" + Main.SPMF_VERSION : "SPMF") + " - Sequential Pattern Explorer";
	}

	// =========================================================================
	// UI
	// =========================================================================

	private void initComponents() {
		setLayout(new BorderLayout());
		setJMenuBar(createMenuBar());
		add(buildToolbar(), BorderLayout.NORTH);
		cards = new CardLayout();
		cardPanel = new JPanel(cards);
		cardPanel.add(buildEmptyPanel(), "empty");
		cardPanel.add(buildMainPanel(), "main");
		add(cardPanel, BorderLayout.CENTER);
		cards.show(cardPanel, "empty");
	}

	private void installShortcuts() {
		JComponent root = getRootPane();
		bind(root, "control F", () -> { searchField.requestFocusInWindow(); searchField.selectAll(); });
		bind(root, "control O", this::openFileDialog);
		bind(root, "control E", this::exportDisplayed);
	}

	private static void bind(JComponent c, String key, Runnable r) {
		c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
		c.getActionMap().put(key, new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override public void actionPerformed(ActionEvent e) { r.run(); }
		});
	}

	private JPanel buildToolbar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
		JButton open = new JButton("Open file\u2026");
		open.setToolTipText("Open a sequential pattern file in SPMF format (Ctrl+O)");
		open.addActionListener(e -> openFileDialog());
		left.add(open);
		fileLabel = new JLabel("No file loaded");
		fileLabel.setFont(TEXT_BOLD);
		left.add(fileLabel);
		summaryLabel = new JLabel("");
		summaryLabel.setFont(TEXT);
		summaryLabel.setForeground(MUTED);
		left.add(summaryLabel);
		bar.add(left, BorderLayout.CENTER);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
		exportButton = new JButton("Export displayed\u2026");
		exportButton.setToolTipText("Save the displayed patterns in SPMF format (Ctrl+E)");
		exportButton.addActionListener(e -> exportDisplayed());
		exportButton.setEnabled(false);
		right.add(exportButton);
		bar.add(right, BorderLayout.EAST);
		return bar;
	}

	private JPanel buildEmptyPanel() {
		JPanel p = new JPanel(new GridBagLayout());
		GridBagConstraints g = new GridBagConstraints();
		g.gridx = 0; g.insets = new Insets(4, 0, 4, 0);
		JLabel title = new JLabel("No patterns loaded");
		title.setFont(new Font("SansSerif", Font.BOLD, 20));
		title.setForeground(MUTED);
		g.gridy = 0; p.add(title, g);
		JLabel sub = new JLabel("Open a sequential pattern file in SPMF format (e.g. \"1 2 -1 3 -1 #SUP: 5\").");
		sub.setFont(TEXT);
		sub.setForeground(MUTED);
		g.gridy = 1; p.add(sub, g);
		JButton open = new JButton("Open a pattern file\u2026");
		open.setFont(BIG);
		open.addActionListener(e -> openFileDialog());
		g.gridy = 2; g.insets = new Insets(14, 0, 0, 0); p.add(open, g);
		return p;
	}

	private JSplitPane buildMainPanel() {
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildItemPanel(), buildResultPanel());
		split.setResizeWeight(0.0);
		split.setDividerLocation(320);
		split.setBorder(null);
		return split;
	}

	// ---------------------------------------------------------------- left: items

	private JPanel buildItemPanel() {
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBorder(new EmptyBorder(10, 10, 10, 6));

		JPanel head = new JPanel(new BorderLayout(0, 5));
		head.add(sectionLabel("ITEMS"), BorderLayout.NORTH);
		searchField = placeholderField("Search items\u2026  (Ctrl+F)");
		onChange(searchField, this::applyItemSearch);
		searchField.addActionListener(e -> {
			if (itemTable.getRowCount() > 0) {
				itemTable.setRowSelectionInterval(0, 0);
				itemTable.scrollRectToVisible(itemTable.getCellRect(0, 0, true));
			}
		});
		head.add(searchField, BorderLayout.CENTER);
		p.add(head, BorderLayout.NORTH);

		itemModel = new ItemTableModel();
		itemTable = new JTable(itemModel);
		itemTable.setFont(TEXT);
		itemTable.setRowHeight(24);
		itemTable.setShowGrid(false);
		itemTable.setIntercellSpacing(new Dimension(0, 0));
		itemTable.setFillsViewportHeight(true);
		itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		itemTable.getTableHeader().setFont(SMALL);
		itemTable.getTableHeader().setReorderingAllowed(false);
		itemTable.getTableHeader().setToolTipText(
				"<html>Number of patterns that <b>start</b> with, <b>end</b> with, or contain the item <b>anywhere</b>."
				+ "<br>Click a header to sort.</html>");
		itemTable.setToolTipText("Double-click: patterns must contain the item.  Right-click for more choices.");
		itemSorter = new TableRowSorter<>(itemModel);
		Comparator<String> byLabel = SequentialPatternExplorer::compareLabels;
		itemSorter.setComparator(ItemTableModel.COL_ITEM, byLabel);		itemSorter.setSortKeys(Collections.singletonList(
				new RowSorter.SortKey(ItemTableModel.COL_ANY, SortOrder.DESCENDING)));
		itemTable.setRowSorter(itemSorter);
		itemTable.setDefaultRenderer(String.class, new StripedRenderer(SwingConstants.LEFT));
		itemTable.setDefaultRenderer(Integer.class, new BarRenderer(false));
		itemTable.getColumnModel().getColumn(ItemTableModel.COL_ITEM).setPreferredWidth(110);
		itemTable.getColumnModel().getColumn(ItemTableModel.COL_START).setPreferredWidth(55);
		itemTable.getColumnModel().getColumn(ItemTableModel.COL_END).setPreferredWidth(55);
		itemTable.getColumnModel().getColumn(ItemTableModel.COL_ANY).setPreferredWidth(75);
		itemTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateItemButtons(); });
		itemTable.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) addContain(selectedItem());
			}
			@Override public void mousePressed(MouseEvent e) { popup(e); }
			@Override public void mouseReleased(MouseEvent e) { popup(e); }
			private void popup(MouseEvent e) {
				if (!e.isPopupTrigger()) return;
				int row = itemTable.rowAtPoint(e.getPoint());
				if (row >= 0) itemTable.setRowSelectionInterval(row, row);
				String it = selectedItem();
				if (it != null) itemPopup(it).show(itemTable, e.getX(), e.getY());
			}
		});
		JScrollPane sp = new JScrollPane(itemTable);
		sp.setBorder(BorderFactory.createLineBorder(LINE));
		sp.getViewport().setBackground(Color.WHITE);
		p.add(sp, BorderLayout.CENTER);

		// the 4 ways to add the selected item to the query
		JPanel foot = new JPanel(new BorderLayout(0, 4));
		foot.add(sectionLabel("ADD THE SELECTED ITEM TO THE QUERY"), BorderLayout.NORTH);
		JPanel grid = new JPanel(new GridLayout(2, 2, 4, 4));
		startWithButton = smallButton("Start with it", "Patterns must start with the selected item",
				() -> setStart(selectedItem()));
		endWithButton = smallButton("End with it", "Patterns must end with the selected item",
				() -> setEnd(selectedItem()));
		containButton = smallButton("Must contain it", "Patterns must contain the selected item (double-click does the same)",
				() -> addContain(selectedItem()));
		excludeButton = smallButton("Must not contain it", "Patterns must not contain the selected item",
				() -> addExclude(selectedItem()));
		grid.add(startWithButton);
		grid.add(endWithButton);
		grid.add(containButton);
		grid.add(excludeButton);
		foot.add(grid, BorderLayout.CENTER);
		p.add(foot, BorderLayout.SOUTH);
		updateItemButtons();
		return p;
	}

	private void updateItemButtons() {
		boolean has = selectedItem() != null;
		startWithButton.setEnabled(has);
		endWithButton.setEnabled(has);
		containButton.setEnabled(has);
		excludeButton.setEnabled(has);
	}

	private void applyItemSearch() {
		if (itemSorter == null) return;
		final String q = searchField.getText().trim().toLowerCase();
		itemSorter.setRowFilter(q.isEmpty() ? null : new RowFilter<ItemTableModel, Integer>() {
			@Override public boolean include(Entry<? extends ItemTableModel, ? extends Integer> e) {
				return itemModel.get(e.getIdentifier()).item.toLowerCase().contains(q);
			}
		});
	}

	private String selectedItem() {
		if (itemTable == null) return null;
		int r = itemTable.getSelectedRow();
		return r < 0 ? null : itemModel.get(itemTable.convertRowIndexToModel(r)).item;
	}

	/** Right-click menu offering the four ways to use an item in the query. */
	private JPopupMenu itemPopup(String item) {
		JPopupMenu m = new JPopupMenu();
		JMenuItem a = new JMenuItem("Patterns must start with \"" + item + "\"");
		a.addActionListener(e -> setStart(item));
		JMenuItem b = new JMenuItem("Patterns must end with \"" + item + "\"");
		b.addActionListener(e -> setEnd(item));
		JMenuItem c = new JMenuItem("Patterns must contain \"" + item + "\"");
		c.addActionListener(e -> addContain(item));
		JMenuItem d = new JMenuItem("Patterns must not contain \"" + item + "\"");
		d.addActionListener(e -> addExclude(item));
		m.add(a); m.add(b); m.addSeparator(); m.add(c); m.add(d);
		return m;
	}

	// --------------------------------------------------------------- right: query + results

	private JPanel buildResultPanel() {
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBorder(new EmptyBorder(10, 6, 10, 10));

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.add(buildQueryPanel(), BorderLayout.NORTH);
		top.add(buildFilterBar(), BorderLayout.SOUTH);
		p.add(top, BorderLayout.NORTH);

		JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildTableArea(), buildNeighborPanel());
		center.setResizeWeight(1.0);
		center.setBorder(null);
		center.setDividerLocation(660);
		p.add(center, BorderLayout.CENTER);

		p.add(buildDetailPanel(), BorderLayout.SOUTH);
		return p;
	}

	/** The query, shown as two readable lines, with one button to clear it. */
	private JPanel buildQueryPanel() {
		JPanel outer = new JPanel(new BorderLayout(10, 0));
		outer.setBackground(Color.WHITE);
		outer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
				new EmptyBorder(6, 8, 6, 8)));

		JPanel rows = new JPanel(new GridLayout(2, 1, 0, 4));
		rows.setOpaque(false);
		queryRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		queryRow1.setOpaque(false);
		queryRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		queryRow2.setOpaque(false);
		rows.add(queryRow1);
		rows.add(queryRow2);
		outer.add(rows, BorderLayout.CENTER);

		clearQueryButton = new JButton("Clear query");
		clearQueryButton.setFocusPainted(false);
		clearQueryButton.setToolTipText("Remove all conditions and filters and show all patterns");
		clearQueryButton.addActionListener(e -> clearQuery());
		JPanel east = new JPanel(new GridBagLayout());
		east.setOpaque(false);
		east.add(clearQueryButton);
		outer.add(east, BorderLayout.EAST);

		rebuildQueryBar();
		return outer;
	}

	private void rebuildQueryBar() {
		queryRow1.removeAll();
		queryRow2.removeAll();

		queryRow1.add(bigLabel("Patterns that"));
		queryRow1.add(singleSlot("start with", startItem, START_FG, START_BG,
				"Item the patterns must start with:", this::setStart));
		queryRow1.add(singleSlot("and end with", endItem, END_FG, END_BG,
				"Item the patterns must end with:", this::setEnd));

		queryRow2.add(multiSlot("and contain", containItems, CONTAIN_FG, CONTAIN_BG,
				"Item the patterns must contain:", this::addContain, this::removeContain));
		queryRow2.add(multiSlot("but do not contain", excludeItems, EXCLUDE_FG, EXCLUDE_BG,
				"Item the patterns must NOT contain:", this::addExclude, this::removeExclude));

		queryRow1.revalidate(); queryRow1.repaint();
		queryRow2.revalidate(); queryRow2.repaint();
	}

	private JComponent singleSlot(String label, String item, Color fg, Color bg, String prompt, Consumer<String> setter) {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setOpaque(false);
		p.add(bigLabel(label));
		if (item != null) {
			p.add(chip(item, fg, bg, () -> setter.accept(null)));
		} else {
			JLabel any = new JLabel("any item");
			any.setFont(BIG);
			any.setForeground(PLACEHOLDER);
			p.add(any);
			p.add(plusButton(prompt, setter));
		}
		return p;
	}

	private JComponent multiSlot(String label, Set<String> items, Color fg, Color bg, String prompt,
			Consumer<String> adder, Consumer<String> remover) {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setOpaque(false);
		p.add(bigLabel(label));
		if (items.isEmpty()) {
			JLabel none = new JLabel("\u2014");
			none.setFont(BIG);
			none.setForeground(PLACEHOLDER);
			p.add(none);
		}
		for (String it : items) p.add(chip(it, fg, bg, () -> remover.accept(it)));
		p.add(plusButton(prompt, adder));
		return p;
	}

	private JButton plusButton(String prompt, Consumer<String> action) {
		JButton b = new JButton("+");
		b.setFont(SMALL);
		b.setMargin(new Insets(0, 5, 0, 5));
		b.setFocusPainted(false);
		b.setToolTipText("Type an item name (or select one in the list on the left)");
		b.addActionListener(e -> promptItem(prompt, action));
		return b;
	}

	private void promptItem(String prompt, Consumer<String> action) {
		String s = JOptionPane.showInputDialog(this, prompt, "Add to the query", JOptionPane.PLAIN_MESSAGE);
		if (s == null) return;
		s = s.trim();
		if (s.isEmpty()) return;
		if (!itemIndex.containsKey(s)) {
			JOptionPane.showMessageDialog(this, "The item \"" + s + "\" does not appear in any pattern.",
					"Add to the query", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		action.accept(s);
	}

	private JComponent chip(String text, Color fg, Color bg, Runnable onRemove) {
		JPanel c = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		c.setBackground(bg);
		c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(bg.darker()),
				new EmptyBorder(1, 6, 1, 1)));
		JLabel l = new JLabel(text);
		l.setFont(TEXT_BOLD);
		l.setForeground(fg);
		c.add(l);
		JButton x = new JButton("\u2715");
		x.setFont(SMALL);
		x.setForeground(fg);
		x.setMargin(new Insets(0, 3, 0, 3));
		x.setBorderPainted(false);
		x.setContentAreaFilled(false);
		x.setFocusPainted(false);
		x.setToolTipText("Remove from the query");
		x.addActionListener(e -> onRemove.run());
		c.add(x);
		return c;
	}

	private JPanel buildFilterBar() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		bar.setBackground(STRIPE);
		bar.setBorder(BorderFactory.createLineBorder(LINE));

		countLabel = new JLabel();
		countLabel.setFont(TEXT_BOLD);
		bar.add(countLabel);
		bar.add(vsep());

		JLabel f = new JLabel("Also filter by:");
		f.setFont(TEXT);
		f.setForeground(MUTED);
		bar.add(f);

		measurePart = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		measurePart.setOpaque(false);
		measureCombo = new JComboBox<>();
		measureCombo.setFont(TEXT);
		measureCombo.addActionListener(e -> refresh());
		measureMin = numField("min");
		measureMax = numField("max");
		measurePart.add(measureCombo);
		measurePart.add(new JLabel("\u2265"));
		measurePart.add(measureMin);
		measurePart.add(new JLabel("\u2264"));
		measurePart.add(measureMax);
		bar.add(measurePart);

		JLabel it = new JLabel("   Number of items:");
		it.setFont(TEXT);
		bar.add(it);
		itemsMin = numField("min");
		itemsMax = numField("max");
		bar.add(itemsMin);
		bar.add(new JLabel("\u2013"));
		bar.add(itemsMax);
		return bar;
	}

	private JPanel buildTableArea() {
		tableModel = new SeqPatternTableModel();
		table = new JTable(tableModel);
		table.setFont(TEXT);
		table.setRowHeight(24);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		table.getTableHeader().setFont(TEXT_BOLD);
		table.getTableHeader().setReorderingAllowed(false);
		table.getTableHeader().setToolTipText("Click a header to sort");
		patternRenderer = new PatternRenderer();
		table.setDefaultRenderer(String.class, patternRenderer);
		NumberRenderer nr = new NumberRenderer();
		table.setDefaultRenderer(Integer.class, nr);
		table.setDefaultRenderer(Long.class, nr);
		table.setDefaultRenderer(Double.class, nr);
		installSorter();
		table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateDetails(); });
		table.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) { popup(e); }
			@Override public void mouseReleased(MouseEvent e) { popup(e); }
			private void popup(MouseEvent e) {
				if (!e.isPopupTrigger()) return;
				int row = table.rowAtPoint(e.getPoint());
				if (row >= 0) table.setRowSelectionInterval(row, row);
				createTablePopup().show(table, e.getX(), e.getY());
			}
		});
		JScrollPane sp = new JScrollPane(table);
		sp.setBorder(BorderFactory.createLineBorder(LINE));
		sp.getViewport().setBackground(Color.WHITE);

		noResultsLabel = new JLabel("", SwingConstants.CENTER);
		noResultsLabel.setFont(BIG);
		noResultsLabel.setForeground(MUTED);
		JPanel empty = new JPanel(new BorderLayout());
		empty.setBackground(Color.WHITE);
		empty.setBorder(BorderFactory.createLineBorder(LINE));
		empty.add(noResultsLabel, BorderLayout.CENTER);

		tableCards = new CardLayout();
		tableCardPanel = new JPanel(tableCards);
		tableCardPanel.add(sp, "table");
		tableCardPanel.add(empty, "empty");
		return tableCardPanel;
	}

	private JPanel buildNeighborPanel() {
		JPanel p = new JPanel(new BorderLayout(0, 5));
		p.setBorder(new EmptyBorder(0, 8, 0, 0));
		p.setMinimumSize(new Dimension(220, 0));
		p.setPreferredSize(new Dimension(290, 0));

		JPanel head = new JPanel(new BorderLayout(0, 4));
		neighborDirCombo = new JComboBox<>(new String[] { "RIGHT AFTER THE START ITEM", "RIGHT BEFORE THE END ITEM" });
		neighborDirCombo.setFont(SECTION);
		neighborDirCombo.setForeground(MUTED);
		neighborDirCombo.setToolTipText("Items of the itemset immediately after the start item / before the end item");
		neighborDirCombo.addActionListener(e -> { if (!suppressEvents) updateNeighbors(); });
		head.add(neighborDirCombo, BorderLayout.NORTH);
		neighborTitle = new JLabel(" ");
		neighborTitle.setFont(SMALL);
		neighborTitle.setForeground(MUTED);
		head.add(neighborTitle, BorderLayout.SOUTH);
		p.add(head, BorderLayout.NORTH);

		neighborModel = new DefaultTableModel(new Object[] { "Item", "Patterns", "%" }, 0) {
			private static final long serialVersionUID = 1L;
			@Override public boolean isCellEditable(int r, int c) { return false; }
			@Override public Class<?> getColumnClass(int c) { return c == 0 ? String.class : c == 1 ? Integer.class : Double.class; }
		};
		neighborTable = new JTable(neighborModel);
		neighborTable.setFont(TEXT);
		neighborTable.setRowHeight(22);
		neighborTable.setShowGrid(false);
		neighborTable.setIntercellSpacing(new Dimension(0, 0));
		neighborTable.setFillsViewportHeight(true);
		neighborTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		neighborTable.setAutoCreateRowSorter(true);
		neighborTable.getTableHeader().setFont(SMALL);
		neighborTable.getTableHeader().setReorderingAllowed(false);
		neighborTable.setDefaultRenderer(String.class, new StripedRenderer(SwingConstants.LEFT));
		neighborTable.setDefaultRenderer(Integer.class, new StripedRenderer(SwingConstants.RIGHT));
		neighborTable.setDefaultRenderer(Double.class, new BarRenderer(true));
		neighborTable.getColumnModel().getColumn(0).setPreferredWidth(100);
		neighborTable.getColumnModel().getColumn(1).setPreferredWidth(60);
		neighborTable.getColumnModel().getColumn(2).setPreferredWidth(90);
		neighborTable.setToolTipText("Double-click: patterns must contain the item.  Right-click for more choices.");
		neighborTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateNeighborButtons(); });
		neighborTable.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) addContain(selectedNeighbor());
			}
			@Override public void mousePressed(MouseEvent e) { popup(e); }
			@Override public void mouseReleased(MouseEvent e) { popup(e); }
			private void popup(MouseEvent e) {
				if (!e.isPopupTrigger()) return;
				int row = neighborTable.rowAtPoint(e.getPoint());
				if (row >= 0) neighborTable.setRowSelectionInterval(row, row);
				String it = selectedNeighbor();
				if (it != null) itemPopup(it).show(neighborTable, e.getX(), e.getY());
			}
		});
		JScrollPane sp = new JScrollPane(neighborTable);
		sp.setBorder(BorderFactory.createLineBorder(LINE));
		sp.getViewport().setBackground(Color.WHITE);
		p.add(sp, BorderLayout.CENTER);

		JPanel foot = new JPanel(new BorderLayout(0, 3));
		JPanel btns = new JPanel(new GridLayout(3, 1, 0, 3));
		nContainButton = smallButton("Must contain it", "Keep only the patterns that also contain this item",
				() -> addContain(selectedNeighbor()));
		nExcludeButton = smallButton("Must not contain it", "Remove the patterns that contain this item",
				() -> addExclude(selectedNeighbor()));
		nExploreButton = smallButton("Make it the start item", "Continue the exploration from this item",
				() -> {
					String it = selectedNeighbor();
					if (it == null) return;
					if (neighborDirCombo.getSelectedIndex() == 0) setStart(it); else setEnd(it);
				});
		btns.add(nContainButton);
		btns.add(nExcludeButton);
		btns.add(nExploreButton);
		foot.add(btns, BorderLayout.NORTH);
		JLabel hint = new JLabel("<html>% = share of the displayed patterns.</html>");
		hint.setFont(SMALL);
		hint.setForeground(MUTED);
		foot.add(hint, BorderLayout.SOUTH);
		p.add(foot, BorderLayout.SOUTH);
		updateNeighborButtons();
		return p;
	}

	private void updateNeighborButtons() {
		boolean has = neighborTable != null && neighborTable.getSelectedRow() >= 0;
		nContainButton.setEnabled(has);
		nExcludeButton.setEnabled(has);
		nExploreButton.setEnabled(has);
		boolean next = neighborDirCombo.getSelectedIndex() == 0;
		nExploreButton.setText(next ? "Make it the start item" : "Make it the end item");
		nExploreButton.setToolTipText(next
				? "Continue forward: this item replaces the current start item"
				: "Continue backward: this item replaces the current end item");
	}

	private String selectedNeighbor() {
		int r = neighborTable.getSelectedRow();
		return r < 0 ? null : (String) neighborModel.getValueAt(neighborTable.convertRowIndexToModel(r), 0);
	}

	private JPanel buildDetailPanel() {
		JPanel p = new JPanel(new BorderLayout(10, 0));
		p.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, LINE), new EmptyBorder(8, 4, 2, 4)));
		JPanel text = new JPanel(new BorderLayout(0, 3));
		text.setOpaque(false);
		detailPattern = new JLabel();
		detailPattern.setFont(BIG);
		detailMeasures = new JLabel();
		detailMeasures.setFont(TEXT);
		detailMeasures.setForeground(MUTED);
		text.add(detailPattern, BorderLayout.NORTH);
		text.add(detailMeasures, BorderLayout.SOUTH);
		p.add(text, BorderLayout.CENTER);
		copyButton = new JButton("Copy line");
		copyButton.setToolTipText("Copy this pattern in SPMF format to the clipboard");
		copyButton.setFocusPainted(false);
		copyButton.addActionListener(e -> { SeqPattern s = selectedPattern(); if (s != null) copy(s.getRawLine()); });
		p.add(copyButton, BorderLayout.EAST);
		updateDetails();
		return p;
	}

	private JMenuBar createMenuBar() {
		JMenuBar bar = new JMenuBar();
		JMenu file = new JMenu("File");
		JMenuItem open = new JMenuItem("Open pattern file\u2026");
		open.setAccelerator(KeyStroke.getKeyStroke("control O"));
		open.addActionListener(e -> openFileDialog());
		file.add(open);
		JMenuItem export = new JMenuItem("Export displayed patterns\u2026");
		export.setAccelerator(KeyStroke.getKeyStroke("control E"));
		export.addActionListener(e -> exportDisplayed());
		file.add(export);
		file.addSeparator();
		JMenuItem quit = new JMenuItem(runAsStandaloneProgram ? "Exit" : "Close");
		quit.addActionListener(e -> { if (runAsStandaloneProgram) System.exit(0); else dispose(); });
		file.add(quit);
		bar.add(file);
		JMenu help = new JMenu("Help");
		JMenuItem how = new JMenuItem("How to use\u2026");
		how.addActionListener(e -> showHelp());
		help.add(how);
		bar.add(help);
		return bar;
	}

	private JPopupMenu createTablePopup() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem copyP = new JMenuItem("Copy pattern");
		copyP.addActionListener(e -> { SeqPattern p = selectedPattern(); if (p != null) copy(p.toDisplayString()); });
		JMenuItem copyL = new JMenuItem("Copy line (SPMF format)");
		copyL.addActionListener(e -> { SeqPattern p = selectedPattern(); if (p != null) copy(p.getRawLine()); });
		JMenuItem first = new JMenuItem("Patterns must start with its first item");
		first.addActionListener(e -> {
			SeqPattern p = selectedPattern();
			if (p != null && !p.getFirstItemset().isEmpty()) setStart(p.getFirstItemset().get(0));
		});
		JMenuItem last = new JMenuItem("Patterns must end with its last item");
		last.addActionListener(e -> {
			SeqPattern p = selectedPattern();
			if (p != null && !p.getLastItemset().isEmpty()) {
				List<String> l = p.getLastItemset();
				setEnd(l.get(l.size() - 1));
			}
		});
		menu.add(copyP); menu.add(copyL); menu.addSeparator(); menu.add(first); menu.add(last);
		return menu;
	}

	// =========================================================================
	// Loading
	// =========================================================================

	private void openFileDialog() {
		JFileChooser ch = new JFileChooser();
		ch.setDialogTitle("Open a sequential pattern file (SPMF format)");
		if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) loadFile(ch.getSelectedFile().getAbsolutePath());
	}

	/** Load a file asynchronously. */
	public void loadFile(String filePath) {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		fileLabel.setText("Loading\u2026");
		new SwingWorker<SeqPatternFileReader.Result, Void>() {
			@Override protected SeqPatternFileReader.Result doInBackground() throws Exception {
				return SeqPatternFileReader.read(filePath);
			}
			@Override protected void done() {
				setCursor(Cursor.getDefaultCursor());
				try {
					SeqPatternFileReader.Result r = get();
					setPatterns(r.patterns, r.measureNames, new File(filePath).getName());
				} catch (Exception ex) {
					Throwable c = ex.getCause() != null ? ex.getCause() : ex;
					fileLabel.setText("No file loaded");
					JOptionPane.showMessageDialog(SequentialPatternExplorer.this,
							"Could not read the file:\n" + c.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	/** Set the patterns to explore. measureNames may be null (collected from the patterns). */
	public void setPatterns(List<SeqPattern> patterns, List<String> measureNames, String sourceName) {
		allPatterns = patterns != null ? patterns : new ArrayList<>();
		if (measureNames == null) {
			Set<String> s = new LinkedHashSet<>();
			for (SeqPattern p : allPatterns) s.addAll(p.getMeasures().keySet());
			measureNames = new ArrayList<>(s);
		}
		this.measureNames = measureNames;
		computeItems();

		suppressEvents = true;
		startItem = null;
		endItem = null;
		containItems.clear();
		excludeItems.clear();

		tableModel.setData(allPatterns, this.measureNames);
		installSorter();
		table.getColumnModel().getColumn(SeqPatternTableModel.COL_PATTERN).setPreferredWidth(460);
		for (int c = 1; c < table.getColumnCount(); c++) table.getColumnModel().getColumn(c).setPreferredWidth(70);

		measureCombo.removeAllItems();
		for (int i = 0; i < this.measureNames.size(); i++)
			if (tableModel.isMeasureNumeric(i)) measureCombo.addItem(this.measureNames.get(i));
		measurePart.setVisible(measureCombo.getItemCount() > 0);
		measureMin.setText(""); measureMax.setText(""); itemsMin.setText(""); itemsMax.setText("");
		searchField.setText("");
		itemModel.setRows(allItems);
		itemTable.clearSelection();
		applyItemSearch();
		suppressEvents = false;

		fileLabel.setText(sourceName);
		summaryLabel.setText(String.format("%,d patterns  \u00b7  %,d items", allPatterns.size(), allItems.size())
				+ (this.measureNames.isEmpty() ? "" : "  \u00b7  measures: " + String.join(", ", this.measureNames)));
		setTitle(baseTitle() + " - " + sourceName);
		exportButton.setEnabled(!allPatterns.isEmpty());

		queryChanged();
		cards.show(cardPanel, allPatterns.isEmpty() ? "empty" : "main");
	}

	private void computeItems() {
		itemIndex.clear();
		for (SeqPattern p : allPatterns) {
			for (String it : p.getDistinctItems()) itemIndex.computeIfAbsent(it, ItemEntry::new).containsCount++;
			for (String it : new HashSet<>(p.getFirstItemset())) itemIndex.get(it).startCount++;
			for (String it : new HashSet<>(p.getLastItemset())) itemIndex.get(it).endCount++;
		}
		allItems = new ArrayList<>(itemIndex.values());
		maxContainsCount = 1;
		for (ItemEntry ie : allItems) maxContainsCount = Math.max(maxContainsCount, ie.containsCount);
	}

	private void installSorter() {
		sorter = new TableRowSorter<>(tableModel);
		table.setRowSorter(sorter);
		// default: sort by the first numeric measure (usually SUP), descending
		for (int i = 0; i < measureNames.size(); i++) {
			if (tableModel.isMeasureNumeric(i)) {
				sorter.setSortKeys(Collections.singletonList(
						new RowSorter.SortKey(SeqPatternTableModel.FIXED_COLUMNS + i, SortOrder.DESCENDING)));
				break;
			}
		}
	}

	// =========================================================================
	// Query editing
	// =========================================================================

	private void setStart(String item) {
		if (item != null && !itemIndex.containsKey(item)) return;
		startItem = item;
		if (item != null) excludeItems.remove(item);
		queryChanged();
	}

	private void setEnd(String item) {
		if (item != null && !itemIndex.containsKey(item)) return;
		endItem = item;
		if (item != null) excludeItems.remove(item);
		queryChanged();
	}

	private void addContain(String item) {
		if (item == null || !itemIndex.containsKey(item)) return;
		containItems.add(item);
		excludeItems.remove(item);
		queryChanged();
	}

	private void removeContain(String item) {
		if (containItems.remove(item)) queryChanged();
	}

	private void addExclude(String item) {
		if (item == null || !itemIndex.containsKey(item)) return;
		excludeItems.add(item);
		containItems.remove(item);
		if (item.equals(startItem)) startItem = null;
		if (item.equals(endItem)) endItem = null;
		queryChanged();
	}

	private void removeExclude(String item) {
		if (excludeItems.remove(item)) queryChanged();
	}

	private void clearQuery() {
		startItem = null;
		endItem = null;
		containItems.clear();
		excludeItems.clear();
		suppressEvents = true;
		measureMin.setText(""); measureMax.setText(""); itemsMin.setText(""); itemsMax.setText("");
		suppressEvents = false;
		queryChanged();
	}

	private boolean queryIsEmpty() {
		return startItem == null && endItem == null && containItems.isEmpty() && excludeItems.isEmpty();
	}

	private boolean filtersAreEmpty() {
		return measureMin.getText().trim().isEmpty() && measureMax.getText().trim().isEmpty()
				&& itemsMin.getText().trim().isEmpty() && itemsMax.getText().trim().isEmpty();
	}

	private void queryChanged() {
		rebuildQueryBar();
		refresh();
	}

	/** Human readable description of the query, in HTML, or null if empty. */
	private String describeQuery() {
		List<String> parts = new ArrayList<>();
		if (startItem != null) parts.add("start with <b>" + esc(startItem) + "</b>");
		if (endItem != null) parts.add("end with <b>" + esc(endItem) + "</b>");
		if (!containItems.isEmpty()) parts.add("contain <b>" + esc(String.join(", ", containItems)) + "</b>");
		if (!excludeItems.isEmpty()) parts.add("do not contain <b>" + esc(String.join(", ", excludeItems)) + "</b>");
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	private static int compareLabels(String a, String b) {
		try { return Long.compare(Long.parseLong(a), Long.parseLong(b)); }
		catch (NumberFormatException e) { return a.compareTo(b); }
	}

	// =========================================================================
	// Filtering
	// =========================================================================

	private void refresh() {
		if (suppressEvents || sorter == null) return;
		final String start = startItem, end = endItem;
		final List<String> must = new ArrayList<>(containItems);
		final List<String> mustNot = new ArrayList<>(excludeItems);
		final int measureIdx = measureCombo.getSelectedItem() != null
				? measureNames.indexOf((String) measureCombo.getSelectedItem()) : -1;
		final Double mMin = bound(measureMin), mMax = bound(measureMax);
		final Double iMin = bound(itemsMin), iMax = bound(itemsMax);

		sorter.setRowFilter(new RowFilter<SeqPatternTableModel, Integer>() {
			@Override
			public boolean include(Entry<? extends SeqPatternTableModel, ? extends Integer> e) {
				int row = e.getIdentifier();
				SeqPattern p = tableModel.getPattern(row);
				if (start != null && !p.startsWith(start)) return false;
				if (end != null && !p.endsWith(end)) return false;
				for (String r : must) if (!p.contains(r)) return false;
				for (String r : mustNot) if (p.contains(r)) return false;
				int n = p.getItemCount();
				if (iMin != null && n < iMin) return false;
				if (iMax != null && n > iMax) return false;
				if (measureIdx >= 0 && (mMin != null || mMax != null)) {
					Double v = tableModel.getMeasureNumber(row, measureIdx);
					if (v == null) return false;
					if (mMin != null && v < mMin) return false;
					if (mMax != null && v > mMax) return false;
				}
				return true;
			}
		});

		patternRenderer.start = start;
		patternRenderer.end = end;
		patternRenderer.contained = new HashSet<>(must);

		clearQueryButton.setEnabled(!queryIsEmpty() || !filtersAreEmpty());

		int shown = table.getRowCount();
		countLabel.setText(String.format("%,d of %,d patterns", shown, allPatterns.size()));
		if (shown == 0 && !allPatterns.isEmpty()) {
			String q = describeQuery();
			String msg = q != null ? "No patterns " + q : "No patterns match the current filters";
			noResultsLabel.setText("<html><center>" + msg + ".<br><font size='-1' color='#999999'>"
					+ "Remove a condition (\u2715) or press <i>Clear query</i>.</font></center></html>");
			tableCards.show(tableCardPanel, "empty");
		} else {
			tableCards.show(tableCardPanel, "table");
		}
		updateNeighbors();
		updateDetails();
	}

	private void updateNeighbors() {
		neighborModel.setRowCount(0);
		boolean hasStart = startItem != null, hasEnd = endItem != null;
		if (!hasStart && !hasEnd) {
			neighborDirCombo.setEnabled(false);
			neighborTitle.setText("<html>Set a start or end item to see what comes right after or before it.</html>");
			updateNeighborButtons();
			return;
		}
		boolean old = suppressEvents;
		suppressEvents = true;
		if (hasStart && !hasEnd) neighborDirCombo.setSelectedIndex(0);
		else if (!hasStart && hasEnd) neighborDirCombo.setSelectedIndex(1);
		suppressEvents = old;
		neighborDirCombo.setEnabled(hasStart && hasEnd);

		boolean next = neighborDirCombo.getSelectedIndex() == 0;
		String anchor = next ? startItem : endItem;
		Map<String, Integer> counts = new HashMap<>();
		int n = table.getRowCount();
		for (int v = 0; v < n; v++) {
			List<List<String>> iss = tableModel.getPattern(table.convertRowIndexToModel(v)).getItemsets();
			if (iss.size() < 2) continue;
			List<String> is = next ? iss.get(1) : iss.get(iss.size() - 2);
			for (String it : is) counts.merge(it, 1, Integer::sum);
		}
		List<Map.Entry<String, Integer>> rows = new ArrayList<>(counts.entrySet());
		rows.sort((a, b) -> !a.getValue().equals(b.getValue()) ? Integer.compare(b.getValue(), a.getValue())
				: compareLabels(a.getKey(), b.getKey()));
		for (Map.Entry<String, Integer> e : rows)
			neighborModel.addRow(new Object[] { e.getKey(), e.getValue(), n == 0 ? 0.0 : 100.0 * e.getValue() / n });
		neighborTitle.setText("<html>" + (rows.isEmpty()
				? "Nothing comes right " + (next ? "after" : "before") + " <b>" + esc(anchor) + "</b> in the displayed patterns."
				: String.format("Right %s <b>%s</b>: %,d distinct items", next ? "after" : "before", esc(anchor), rows.size()))
				+ "</html>");
		updateNeighborButtons();
	}

	private Double bound(JTextField f) {
		String t = f.getText().trim();
		if (t.isEmpty()) { f.setBackground(Color.WHITE); return null; }
		try { Double d = Double.valueOf(t); f.setBackground(Color.WHITE); return d; }
		catch (NumberFormatException e) { f.setBackground(new Color(255, 215, 215)); return null; }
	}

	private SeqPattern selectedPattern() {
		int r = table.getSelectedRow();
		return r < 0 ? null : tableModel.getPattern(table.convertRowIndexToModel(r));
	}

	private void updateDetails() {
		SeqPattern p = selectedPattern();
		if (p == null) {
			detailPattern.setText("<html><font color='#737373'>Select a pattern in the table to see its details. "
					+ "Right-click a row for more actions.</font></html>");
			detailMeasures.setText(" ");
			copyButton.setEnabled(false);
			return;
		}
		detailPattern.setText(patternHtml(p, patternRenderer.start, patternRenderer.end, patternRenderer.contained));
		StringBuilder sb = new StringBuilder();
		sb.append(p.getItemsetCount()).append(" itemsets, ").append(p.getItemCount()).append(" items");
		for (Map.Entry<String, String> e : p.getMeasures().entrySet())
			sb.append("   \u00b7   ").append(e.getKey()).append(": ").append(e.getValue());
		detailMeasures.setText(sb.toString());
		copyButton.setEnabled(true);
	}

	// =========================================================================
	// Export / help
	// =========================================================================

	private void exportDisplayed() {
		if (table == null || table.getRowCount() == 0) {
			JOptionPane.showMessageDialog(this, "There are no patterns to export.", "Export", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JFileChooser ch = new JFileChooser();
		ch.setDialogTitle("Export the displayed patterns (SPMF format)");
		ch.setSelectedFile(new File("selected_patterns.txt"));
		if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(ch.getSelectedFile()), StandardCharsets.UTF_8))) {
			for (int v = 0; v < table.getRowCount(); v++) {
				w.write(tableModel.getPattern(table.convertRowIndexToModel(v)).getRawLine());
				w.newLine();
			}
			JOptionPane.showMessageDialog(this, String.format("%,d patterns saved to %s", table.getRowCount(),
					ch.getSelectedFile().getName()), "Export", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Export", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void copy(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	private void showHelp() {
		String msg = "<html><body style='width:460px;font-family:sans-serif;font-size:12px'>"
				+ "<b>1.</b> Open a sequential pattern file in SPMF format. Every <i>#NAME:</i> measure becomes a column.<br><br>"
				+ "<b>2.</b> The list on the left shows every item with the number of patterns that <i>start</i> with it, "
				+ "<i>end</i> with it, or contain it <i>anywhere</i>. Click a header to sort, Ctrl+F to search.<br><br>"
				+ "<b>3.</b> Build the query: select an item and press <i>Start with it</i>, <i>End with it</i>, "
				+ "<i>Must contain it</i> or <i>Must not contain it</i> (or right-click / double-click). "
				+ "The query is shown as a sentence above the table; press \u2715 on a condition to remove it, "
				+ "or <i>Clear query</i> to start over.<br><br>"
				+ "<b>4.</b> When a start (or end) item is set, the right panel shows what comes right after (or before) it "
				+ "in the displayed patterns. From there you can require, exclude, or continue the exploration from an item.<br><br>"
				+ "<b>5.</b> Optionally filter by a measure range or by the number of items in the grey bar.<br><br>"
				+ "<b>6.</b> <i>Export displayed</i> (Ctrl+E) saves exactly what is shown, in SPMF format.</body></html>";
		JOptionPane.showMessageDialog(this, msg, "How to use", JOptionPane.PLAIN_MESSAGE);
	}

	// =========================================================================
	// Small UI helpers
	// =========================================================================

	private static JLabel sectionLabel(String text) {
		JLabel l = new JLabel(text);
		l.setFont(SECTION);
		l.setForeground(MUTED);
		return l;
	}

	private static JLabel bigLabel(String text) {
		JLabel l = new JLabel(text);
		l.setFont(BIG);
		return l;
	}

	private static JLabel vsep() {
		JLabel l = new JLabel("|");
		l.setForeground(LINE);
		return l;
	}

	private static JButton smallButton(String text, String tooltip, Runnable action) {
		JButton b = new JButton(text);
		b.setFont(SMALL);
		b.setFocusPainted(false);
		b.setMargin(new Insets(2, 6, 2, 6));
		b.setToolTipText(tooltip);
		b.addActionListener(e -> action.run());
		return b;
	}

	private JTextField numField(String placeholder) {
		JTextField f = placeholderField(placeholder);
		f.setColumns(4);
		onChange(f, this::refresh);
		return f;
	}

	private static JTextField placeholderField(String placeholder) {
		JTextField f = new JTextField() {
			private static final long serialVersionUID = 1L;
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (getText().isEmpty()) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					g2.setColor(new Color(160, 160, 160));
					g2.setFont(getFont());
					FontMetrics fm = g2.getFontMetrics();
					g2.drawString(placeholder, getInsets().left + 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
					g2.dispose();
				}
			}
		};
		f.setFont(TEXT);
		return f;
	}

	private static void onChange(JTextField f, Runnable r) {
		f.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) { r.run(); }
			@Override public void removeUpdate(DocumentEvent e) { r.run(); }
			@Override public void changedUpdate(DocumentEvent e) { r.run(); }
		});
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * HTML rendering of a pattern: the start item (in the first itemset) in blue,
	 * the end item (in the last itemset) in orange, required items in bold.
	 */
	private static String patternHtml(SeqPattern p, String start, String end, Set<String> contained) {
		StringBuilder sb = new StringBuilder("<html>");
		List<List<String>> iss = p.getItemsets();
		int last = iss.size() - 1;
		for (int i = 0; i < iss.size(); i++) {
			if (i > 0) sb.append(" <font color='#A0A0A0'>\u2192</font> ");
			sb.append("<font color='#A0A0A0'>{</font>");
			List<String> is = iss.get(i);
			for (int j = 0; j < is.size(); j++) {
				if (j > 0) sb.append(", ");
				String it = is.get(j);
				if (i == 0 && it.equals(start)) sb.append("<b><font color='#0069C8'>").append(esc(it)).append("</font></b>");
				else if (i == last && it.equals(end)) sb.append("<b><font color='#BE5000'>").append(esc(it)).append("</font></b>");
				else if (contained != null && contained.contains(it)) sb.append("<b>").append(esc(it)).append("</b>");
				else sb.append(esc(it));
			}
			sb.append("<font color='#A0A0A0'>}</font>");
		}
		return sb.append("</html>").toString();
	}

	// =========================================================================
	// Models & renderers
	// =========================================================================

	/** Item table: Item | Start | End | Anywhere. */
	private static class ItemTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		static final int COL_ITEM = 0, COL_START = 1, COL_END = 2, COL_ANY = 3;
		private static final String[] NAMES = { "Item", "Start", "End", "Anywhere" };
		private List<ItemEntry> rows = new ArrayList<>();

		void setRows(List<ItemEntry> rows) { this.rows = rows; fireTableDataChanged(); }
		ItemEntry get(int modelRow) { return rows.get(modelRow); }

		@Override public int getRowCount() { return rows.size(); }
		@Override public int getColumnCount() { return NAMES.length; }
		@Override public String getColumnName(int c) { return NAMES[c]; }
		@Override public Class<?> getColumnClass(int c) { return c == COL_ITEM ? String.class : Integer.class; }
		@Override public boolean isCellEditable(int r, int c) { return false; }
		@Override public Object getValueAt(int r, int c) {
			ItemEntry e = rows.get(r);
			switch (c) {
			case COL_ITEM: return e.item;
			case COL_START: return e.startCount;
			case COL_END: return e.endCount;
			default: return e.containsCount;
			}
		}
	}

	/** Pattern column with highlighted items and striped background. */
	private class PatternRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		String start, end;
		Set<String> contained = new HashSet<>();

		@Override
		public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
			super.getTableCellRendererComponent(t, value, sel, focus, row, col);
			setBorder(new EmptyBorder(0, 6, 0, 6));
			if (t.convertColumnIndexToModel(col) == SeqPatternTableModel.COL_PATTERN)
				setText(patternHtml(tableModel.getPattern(t.convertRowIndexToModel(row)), start, end, contained));
			if (!sel) setBackground(row % 2 == 0 ? Color.WHITE : STRIPE);
			return this;
		}
	}

	/** Striped cells; numbers right-aligned with trailing zeros trimmed. */
	private static class StripedRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		StripedRenderer(int align) { setHorizontalAlignment(align); }

		@Override
		public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
			super.getTableCellRendererComponent(t, value, sel, focus, row, col);
			setBorder(new EmptyBorder(0, 6, 0, 8));
			if (!sel) setBackground(row % 2 == 0 ? Color.WHITE : STRIPE);
			return this;
		}

		@Override
		protected void setValue(Object v) {
			if (v instanceof Double) {
				double d = (Double) v;
				setText(d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.4f", d).replaceAll("0+$", ""));
			} else setText(v == null ? "" : v.toString());
		}
	}

	private static class NumberRenderer extends StripedRenderer {
		private static final long serialVersionUID = 1L;
		NumberRenderer() { super(SwingConstants.RIGHT); }
	}

	/**
	 * Numeric cell drawn as a light bar with the value on top. In percent mode
	 * the bar is value/100; otherwise it is value / (max count over all items).
	 */
	private class BarRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		private final boolean percent;
		private double fraction;
		private boolean selected;
		private int row;

		BarRenderer(boolean percent) {
			this.percent = percent;
			setHorizontalAlignment(SwingConstants.RIGHT);
			setOpaque(false);
		}

		@Override
		public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int r, int col) {
			super.getTableCellRendererComponent(t, value, sel, focus, r, col);
			setBorder(new EmptyBorder(0, 4, 0, 6));
			double v = value instanceof Number ? ((Number) value).doubleValue() : 0;
			if (percent) {
				fraction = v / 100.0;
				setText(v >= 10 ? String.format("%.0f%%", v) : String.format("%.1f%%", v));
			} else {
				fraction = v / maxContainsCount;
				setText(String.format("%,d", (long) v));
			}
			fraction = Math.max(0, Math.min(1, fraction));
			selected = sel;
			row = r;
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			g.setColor(selected ? UIManager.getColor("Table.selectionBackground") : (row % 2 == 0 ? Color.WHITE : STRIPE));
			g.fillRect(0, 0, getWidth(), getHeight());
			if (!selected && fraction > 0) {
				g.setColor(ACCENT_BAR);
				int w = (int) Math.round((getWidth() - 8) * fraction);
				g.fillRect(4, 3, Math.max(w, 2), getHeight() - 6);
			}
			super.paintComponent(g);
		}
	}

	// =========================================================================
	// Main
	// =========================================================================

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
		SwingUtilities.invokeLater(() -> {
			SequentialPatternExplorer v = args.length > 0
					? new SequentialPatternExplorer(args[0], true) : new SequentialPatternExplorer(true);
			v.setVisible(true);
		});
	}
}