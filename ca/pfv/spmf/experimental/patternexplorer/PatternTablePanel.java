package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

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
 * The explicit list of the matching patterns: a sortable table whose columns
 * are generated from the measures found in the file (one column per measure,
 * numeric or textual). The table is virtualized by Swing (only visible rows
 * are rendered). Very large results are shown only on request. "Maximal" and
 * "Closed" columns can be computed on demand, relative to the matching
 * patterns only.
 * 
 * @author Philippe Fournier-Viger
 */
public class PatternTablePanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Above this number of matching patterns the table is shown on request */
	private static final int LARGE_RESULT_THRESHOLD = 50_000;

	private static final String CARD_TABLE = "table";
	private static final String CARD_LARGE = "large";

	private final ExplorerController controller;
	private final PatternCollection collection;
	private final PatternTableModel model;
	private final JTable table;
	private final TableRowSorter<PatternTableModel> sorter;
	private final JLabel titleLabel;
	private final JLabel largeLabel;
	private final JButton flagsButton;
	private final JComboBox<String> closedMeasureCombo;
	private final CardLayout cards;
	private final JPanel cardPanel;

	private QueryResult result;
	private boolean forceShow = false;
	/** Flags per row of the result (null = not computed) */
	private boolean[] maximalFlags;
	private boolean[] closedFlags;
	private int closedMeasureIdx = -1;

	/**
	 * Constructor
	 * 
	 * @param controller the controller
	 * @param collection the collection
	 */
	public PatternTablePanel(ExplorerController controller, PatternCollection collection) {
		super(new BorderLayout(0, 2));
		this.controller = controller;
		this.collection = collection;
		setBorder(ExplorerStyle.titledBorder("Matching patterns"));
		setMinimumSize(new Dimension(300, 120));

		// ---- Header row ----
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		titleLabel = new JLabel(" ");
		titleLabel.setFont(ExplorerStyle.FONT_BOLD);
		header.add(titleLabel, BorderLayout.WEST);

		JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		tools.setOpaque(false);
		closedMeasureCombo = new JComboBox<>(collection.getNumericMeasureNames().toArray(new String[0]));
		closedMeasureCombo.setFont(ExplorerStyle.FONT_SMALL);
		closedMeasureCombo.setToolTipText("Measure used to decide closedness (same value as a superset)");
		int supIdx = collection.getNumericMeasureIndex("SUP");
		if (supIdx >= 0) {
			closedMeasureCombo.setSelectedIndex(supIdx);
		}
		flagsButton = new JButton("Compute closed / maximal");
		ExplorerStyle.makeCompact(flagsButton);
		flagsButton.setToolTipText("<html>Add <b>Maximal</b> and <b>Closed</b> columns, computed relative to the "
				+ "matching patterns only<br>(a pattern is maximal if no matching pattern is a proper superset; "
				+ "closed if no proper superset has the same measure value)</html>");
		flagsButton.addActionListener(e -> computeFlags());
		JButton exportButton = new JButton("Export\u2026");
		ExplorerStyle.makeCompact(exportButton);
		exportButton.setToolTipText("Save the matching patterns (in the current order) to a text file in SPMF format");
		exportButton.addActionListener(e -> exportPatterns(this));
		if (!collection.getNumericMeasureNames().isEmpty()) {
			tools.add(ExplorerStyle.mutedLabel("closed by:"));
			tools.add(closedMeasureCombo);
		}
		tools.add(flagsButton);
		tools.add(exportButton);
		header.add(tools, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		// ---- Table ----
		model = new PatternTableModel();
		table = new JTable(model) {
			private static final long serialVersionUID = 1L;

			@Override
			public String getToolTipText(MouseEvent e) {
				int viewRow = rowAtPoint(e.getPoint());
				if (viewRow < 0 || result == null) {
					return null;
				}
				int row = convertRowIndexToModel(viewRow);
				return patternTooltip(result.getPatternIndex(row));
			}
		};
		table.setFont(ExplorerStyle.FONT_NORMAL);
		table.setRowHeight(20);
		table.setFillsViewportHeight(true);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setTableHeader(new JTableHeader(table.getColumnModel()) {
			private static final long serialVersionUID = 1L;

			@Override
			public String getToolTipText(MouseEvent e) {
				int viewColumn = columnAtPoint(e.getPoint());
				if (viewColumn < 0) {
					return null;
				}
				return model.headerTooltip(table.convertColumnIndexToModel(viewColumn));
			}
		});
		table.getTableHeader().setReorderingAllowed(false);
		sorter = new TableRowSorter<>(model);
		table.setRowSorter(sorter);
		configureColumns();

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
					showDetailsOfSelected();
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}
		});
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "showDetails");
		table.getActionMap().put("showDetails", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				showDetailsOfSelected();
			}
		});

		// ---- Cards: table or "large result" notice ----
		cards = new CardLayout();
		cardPanel = new JPanel(cards);
		cardPanel.setOpaque(false);
		cardPanel.add(new JScrollPane(table), CARD_TABLE);

		JPanel largePanel = new JPanel();
		largePanel.setLayout(new javax.swing.BoxLayout(largePanel, javax.swing.BoxLayout.Y_AXIS));
		largePanel.setOpaque(false);
		largeLabel = new JLabel(" ");
		largeLabel.setFont(ExplorerStyle.FONT_NORMAL);
		largeLabel.setAlignmentX(CENTER_ALIGNMENT);
		JLabel hint = ExplorerStyle.mutedLabel(
				"Use the drill-down list, the size chart or the measure histogram to narrow the selection, or:");
		hint.setAlignmentX(CENTER_ALIGNMENT);
		JButton showAnyway = new JButton("Show these patterns anyway");
		ExplorerStyle.makeCompact(showAnyway);
		showAnyway.setAlignmentX(CENTER_ALIGNMENT);
		showAnyway.addActionListener(e -> {
			forceShow = true;
			refreshCard();
		});
		largePanel.add(Box.createVerticalGlue());
		largePanel.add(largeLabel);
		largePanel.add(Box.createVerticalStrut(6));
		largePanel.add(hint);
		largePanel.add(Box.createVerticalStrut(8));
		largePanel.add(showAnyway);
		largePanel.add(Box.createVerticalGlue());
		cardPanel.add(largePanel, CARD_LARGE);
		add(cardPanel, BorderLayout.CENTER);
	}

	// ------------------------------------------------------------------
	// Update
	// ------------------------------------------------------------------

	/**
	 * Show the patterns of a result.
	 * 
	 * @param result the current result
	 */
	public void update(QueryResult result) {
		this.result = result;
		forceShow = false;
		boolean hadFlags = maximalFlags != null;
		maximalFlags = null;
		closedFlags = null;
		List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
		if (hadFlags) {
			model.fireTableStructureChanged();
			configureColumns();
		} else {
			model.fireTableDataChanged();
		}
		if (keys != null && !keys.isEmpty() && keys.get(0).getColumn() < model.getColumnCount()) {
			sorter.setSortKeys(keys);
		}
		titleLabel.setText("Matching patterns (" + ExplorerFormat.integer(result.size()) + ")");
		flagsButton.setEnabled(!result.isEmpty());
		refreshCard();
	}

	private void refreshCard() {
		if (result != null && result.size() > LARGE_RESULT_THRESHOLD && !forceShow) {
			largeLabel.setText("<html><b>" + ExplorerFormat.integer(result.size())
					+ " patterns</b> match the current query. The list is hidden to keep the tool responsive.</html>");
			cards.show(cardPanel, CARD_LARGE);
		} else {
			cards.show(cardPanel, CARD_TABLE);
		}
	}

	private void configureColumns() {
		TableColumnModel columns = table.getColumnModel();
		for (int c = 0; c < columns.getColumnCount(); c++) {
			Class<?> cls = model.getColumnClass(c);
			if (c == 0) {
				columns.getColumn(c).setPreferredWidth(320);
			} else if (c == 1) {
				columns.getColumn(c).setPreferredWidth(50);
				columns.getColumn(c).setCellRenderer(new ExplorerRenderers.NumberRenderer());
			} else if (cls == Double.class) {
				columns.getColumn(c).setPreferredWidth(90);
				columns.getColumn(c).setCellRenderer(new ExplorerRenderers.NumberRenderer());
			} else if (cls == Boolean.class) {
				columns.getColumn(c).setPreferredWidth(70);
			} else {
				columns.getColumn(c).setPreferredWidth(160);
			}
		}
	}

	private String patternTooltip(int patternIndex) {
		ExplorerPattern p = collection.getPattern(patternIndex);
		StringBuilder sb = new StringBuilder("<html><b>").append(ExplorerHtml.escape(p.toString())).append("</b>");
		sb.append("<br>").append(ExplorerFormat.count(p.size(), "item"));
		for (Map.Entry<String, String> e : p.getAttributes().entrySet()) {
			sb.append("<br>").append(ExplorerHtml.escape(e.getKey())).append(": ")
					.append(ExplorerHtml.escape(e.getValue()));
		}
		sb.append("<br><i>double-click for details, right-click for actions</i></html>");
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// Closed / maximal flags (on demand, on the current subset)
	// ------------------------------------------------------------------

	private void computeFlags() {
		if (result == null || result.isEmpty()) {
			return;
		}
		final int[] subset = result.getMatchIndices();
		final QueryResult forResult = result;
		String measure = (String) closedMeasureCombo.getSelectedItem();
		final int measureIdx = measure == null ? -1 : collection.getNumericMeasureIndex(measure);
		flagsButton.setEnabled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		new SwingWorker<boolean[][], Void>() {
			@Override
			protected boolean[][] doInBackground() {
				return SubsetRelations.computeFlags(collection, subset, measureIdx);
			}

			@Override
			protected void done() {
				setCursor(Cursor.getDefaultCursor());
				flagsButton.setEnabled(true);
				if (result != forResult) {
					return; // The query changed meanwhile
				}
				try {
					boolean[][] flags = get();
					maximalFlags = flags[0];
					closedFlags = flags[1];
					closedMeasureIdx = measureIdx;
					List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
					model.fireTableStructureChanged();
					configureColumns();
					if (keys != null && !keys.isEmpty()) {
						sorter.setSortKeys(keys);
					}
					int maximalCount = 0;
					int closedCount = 0;
					for (int i = 0; i < maximalFlags.length; i++) {
						if (maximalFlags[i]) {
							maximalCount++;
						}
						if (closedFlags[i]) {
							closedCount++;
						}
					}
					titleLabel.setText("Matching patterns (" + ExplorerFormat.integer(result.size()) + ")  \u2013  "
							+ ExplorerFormat.integer(maximalCount) + " maximal"
							+ (measureIdx >= 0 ? ", " + ExplorerFormat.integer(closedCount) + " closed" : ""));
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(PatternTablePanel.this,
							"Could not compute the flags: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	// ------------------------------------------------------------------
	// Actions
	// ------------------------------------------------------------------

	private List<Integer> getSelectedPatternIndices() {
		List<Integer> list = new ArrayList<>();
		if (result == null) {
			return list;
		}
		for (int viewRow : table.getSelectedRows()) {
			list.add(result.getPatternIndex(table.convertRowIndexToModel(viewRow)));
		}
		return list;
	}

	private void showDetailsOfSelected() {
		List<Integer> sel = getSelectedPatternIndices();
		if (!sel.isEmpty()) {
			controller.showPatternDetails(sel.get(0));
		}
	}

	private void copySelected() {
		List<Integer> sel = getSelectedPatternIndices();
		if (sel.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (int idx : sel) {
			sb.append(toSpmfLine(collection.getPattern(idx))).append(System.lineSeparator());
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
	}

	private static String toSpmfLine(ExplorerPattern p) {
		StringBuilder sb = new StringBuilder(p.toSpaceSeparatedString());
		for (Map.Entry<String, String> e : p.getAttributes().entrySet()) {
			sb.append(" #").append(e.getKey()).append(": ").append(e.getValue());
		}
		return sb.toString();
	}

	private void maybeShowPopup(MouseEvent e) {
		if (!e.isPopupTrigger() || result == null) {
			return;
		}
		int row = table.rowAtPoint(e.getPoint());
		if (row >= 0 && !table.isRowSelected(row)) {
			table.setRowSelectionInterval(row, row);
		}
		List<Integer> sel = getSelectedPatternIndices();
		if (sel.isEmpty()) {
			return;
		}
		ExplorerPattern first = collection.getPattern(sel.get(0));
		JPopupMenu menu = new JPopupMenu();
		JMenuItem details = new JMenuItem("Show details of " + abbreviate(first.toString()));
		details.setFont(ExplorerStyle.FONT_BOLD);
		details.addActionListener(a -> controller.showPatternDetails(sel.get(0)));
		JMenuItem supersets = new JMenuItem("+ Require these items (explore supersets)");
		supersets.addActionListener(a -> controller.requireItems(first.getItems()));
		JMenuItem subsets = new JMenuItem("\u2286 Keep only patterns made of these items (explore subsets)");
		subsets.addActionListener(a -> controller.restrictWithin(first.getItems()));
		JMenuItem exclude = new JMenuItem("\u2212 Exclude these items");
		exclude.addActionListener(a -> controller.excludeItems(first.getItems()));
		JMenuItem copy = new JMenuItem("Copy " + (sel.size() == 1 ? "pattern" : sel.size() + " patterns"));
		copy.addActionListener(a -> copySelected());
		menu.add(details);
		menu.addSeparator();
		menu.add(supersets);
		menu.add(subsets);
		menu.add(exclude);
		menu.addSeparator();
		menu.add(copy);
		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	private static String abbreviate(String s) {
		return s.length() > 40 ? s.substring(0, 37) + "\u2026}" : s;
	}

	/**
	 * Export the matching patterns (in the current sort order) to a text file.
	 * 
	 * @param parent parent component for dialogs
	 */
	public void exportPatterns(Component parent) {
		if (result == null || result.isEmpty()) {
			JOptionPane.showMessageDialog(parent, "There is no matching pattern to export.", "Export",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export matching patterns");
		chooser.setSelectedFile(new File("selected_patterns.txt"));
		if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writer.write("# Patterns exported by the SPMF Interactive Pattern Explorer");
			writer.newLine();
			writer.write("# Query: " + result.getQuery().toString());
			writer.newLine();
			int n = table.getRowCount();
			for (int viewRow = 0; viewRow < n; viewRow++) {
				int idx = result.getPatternIndex(table.convertRowIndexToModel(viewRow));
				writer.write(toSpmfLine(collection.getPattern(idx)));
				writer.newLine();
			}
			JOptionPane.showMessageDialog(parent,
					ExplorerFormat.count(n, "pattern") + " exported to:\n" + file.getAbsolutePath(), "Export",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(parent, "Export failed: " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/** @return the table component */
	public JTable getTable() {
		return table;
	}

	// ------------------------------------------------------------------
	// Table model
	// ------------------------------------------------------------------

	/** Dynamic table model: Pattern, Size, one column per measure, [Maximal, Closed] */
	private final class PatternTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;

		private static final int FIXED_COLUMNS = 2;

		private int measureColumns() {
			return collection.getMeasureNames().size();
		}

		private int flagColumns() {
			if (maximalFlags == null) {
				return 0;
			}
			return closedMeasureIdx >= 0 ? 2 : 1;
		}

		@Override
		public int getRowCount() {
			return result == null ? 0 : result.size();
		}

		@Override
		public int getColumnCount() {
			return FIXED_COLUMNS + measureColumns() + flagColumns();
		}

		@Override
		public String getColumnName(int column) {
			if (column == 0) {
				return "Pattern";
			}
			if (column == 1) {
				return "Size";
			}
			int m = column - FIXED_COLUMNS;
			if (m < measureColumns()) {
				return collection.getMeasureNames().get(m);
			}
			int f = m - measureColumns();
			return f == 0 ? "Maximal" : "Closed (" + collection.getNumericMeasureNames().get(closedMeasureIdx) + ")";
		}

		String headerTooltip(int column) {
			if (column == 0) {
				return "The items of the pattern (unordered set)";
			}
			if (column == 1) {
				return "Number of items";
			}
			int m = column - FIXED_COLUMNS;
			if (m < measureColumns()) {
				String name = collection.getMeasureNames().get(m);
				return collection.isNumericMeasure(name) ? "Numeric measure " + name + " (click to sort)"
						: "Attribute " + name;
			}
			int f = m - measureColumns();
			return f == 0 ? "True if no matching pattern is a proper superset of this pattern"
					: "True if no matching proper superset has the same value of the measure";
		}

		@Override
		public Class<?> getColumnClass(int column) {
			if (column == 0) {
				return String.class;
			}
			if (column == 1) {
				return Integer.class;
			}
			int m = column - FIXED_COLUMNS;
			if (m < measureColumns()) {
				return collection.isNumericMeasure(collection.getMeasureNames().get(m)) ? Double.class
						: String.class;
			}
			return Boolean.class;
		}

		@Override
		public Object getValueAt(int row, int column) {
			if (result == null) {
				return null;
			}
			int idx = result.getPatternIndex(row);
			if (column == 0) {
				return collection.getPattern(idx).toString();
			}
			if (column == 1) {
				return collection.getPatternSize(idx);
			}
			int m = column - FIXED_COLUMNS;
			if (m < measureColumns()) {
				String name = collection.getMeasureNames().get(m);
				int numericIdx = collection.getNumericMeasureIndex(name);
				if (numericIdx >= 0) {
					return collection.getNumericValue(numericIdx, idx);
				}
				String value = collection.getPattern(idx).getAttribute(name);
				return value == null ? "" : value;
			}
			int f = m - measureColumns();
			if (maximalFlags == null || row >= maximalFlags.length) {
				return Boolean.FALSE;
			}
			return f == 0 ? maximalFlags[row] : closedFlags[row];
		}
	}
}