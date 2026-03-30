package ca.pfv.spmf.gui.viewers.graph_transition_viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
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
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Panel displaying sequential patterns in a sortable table. First column shows
 * the pattern sequence; subsequent columns show measures. Supports filtering
 * by selected node or edge, and highlighting. Right-click to copy patterns.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class PatternListPanel extends JPanel {

	/** The pattern table */
	private JTable patternTable;
	/** Table model for patterns */
	private PatternTableModel tableModel;
	/** Header label showing pattern count */
	private JLabel headerLabel;
	/** Current graph reference */
	private TransitionGraph currentGraph;
	/** Listener for pattern selection events */
	private PatternSelectionListener listener;
	/** Row sorter for table sorting */
	private TableRowSorter<PatternTableModel> rowSorter;

	/** Currently displayed patterns (may be filtered by item or edge) */
	private List<TransitionGraph.StoredPattern> currentPatterns;
	/** Base set of patterns (already filtered by pattern-level criteria) */
	private List<TransitionGraph.StoredPattern> basePatterns;
	/** Cached measure names from the base pattern set */
	private List<String> cachedMeasureNames;

	/** Right-click context menu */
	private JPopupMenu contextMenu;

	/**
	 * Listener interface for pattern selection events.
	 */
	public interface PatternSelectionListener {
		/**
		 * Called when a pattern is selected.
		 * @param pattern the selected pattern
		 */
		void onPatternSelected(TransitionGraph.StoredPattern pattern);

		/**
		 * Called when selection is cleared.
		 */
		void onPatternDeselected();
	}

	/**
	 * Constructor. Initializes the table and context menu.
	 */
	public PatternListPanel() {
		setLayout(new BorderLayout(0, 4));
		setBorder(new EmptyBorder(5, 5, 5, 5));

		headerLabel = new JLabel("Sequential Patterns");
		headerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
		headerLabel.setBorder(new EmptyBorder(2, 4, 4, 4));
		add(headerLabel, BorderLayout.NORTH);

		tableModel = new PatternTableModel();
		patternTable = new JTable(tableModel);
		patternTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		patternTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
		patternTable.setRowHeight(22);
		patternTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		patternTable.getTableHeader().setReorderingAllowed(false);
		patternTable.setFillsViewportHeight(true);

		rowSorter = new TableRowSorter<>(tableModel);
		patternTable.setRowSorter(rowSorter);

		patternTable.setDefaultRenderer(Object.class, new PatternCellRenderer());

		patternTable.getSelectionModel().addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) return;
			int viewRow = patternTable.getSelectedRow();
			if (viewRow >= 0) {
				int modelRow = patternTable.convertRowIndexToModel(viewRow);
				TransitionGraph.StoredPattern selected = tableModel.getPatternAt(modelRow);
				if (selected != null && listener != null) {
					listener.onPatternSelected(selected);
				}
			} else {
				if (listener != null) listener.onPatternDeselected();
			}
		});

		patternTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					patternTable.clearSelection();
					if (listener != null) listener.onPatternDeselected();
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				handlePopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				handlePopup(e);
			}

			private void handlePopup(MouseEvent e) {
				if (e.isPopupTrigger()) {
					int row = patternTable.rowAtPoint(e.getPoint());
					if (row >= 0) {
						patternTable.setRowSelectionInterval(row, row);
						showContextMenu(e.getX(), e.getY());
					}
				}
			}
		});

		// Create context menu
		contextMenu = new JPopupMenu();
		JMenuItem copyItem = new JMenuItem("Copy");
		copyItem.addActionListener(e -> copySelectedPatternToClipboard());
		contextMenu.add(copyItem);

		JMenuItem copyWithMeasuresItem = new JMenuItem("Copy with Measures");
		copyWithMeasuresItem.addActionListener(e -> copySelectedPatternWithMeasures());
		contextMenu.add(copyWithMeasuresItem);

		JScrollPane scrollPane = new JScrollPane(patternTable);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Shows the context menu at the specified location.
	 * @param x x coordinate
	 * @param y y coordinate
	 */
	private void showContextMenu(int x, int y) {
		contextMenu.show(patternTable, x, y);
	}

	/**
	 * Copies the selected pattern sequence to clipboard.
	 */
	private void copySelectedPatternToClipboard() {
		TransitionGraph.StoredPattern pattern = getSelectedPattern();
		if (pattern != null) {
			StringSelection selection = new StringSelection(pattern.getSequenceString());
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selection, null);
		}
	}

	/**
	 * Copies the selected pattern with all measures to clipboard.
	 */
	private void copySelectedPatternWithMeasures() {
		TransitionGraph.StoredPattern pattern = getSelectedPattern();
		if (pattern != null) {
			StringSelection selection = new StringSelection(pattern.getDisplayString());
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selection, null);
		}
	}

	/**
	 * Sets the listener for pattern selection events.
	 * @param listener the listener
	 */
	public void setPatternSelectionListener(PatternSelectionListener listener) {
		this.listener = listener;
	}

	/**
	 * Sets the patterns to display. Computes and caches measure column names.
	 * @param patterns the filtered pattern list
	 * @param graph    the full graph (for item-level sub-filtering)
	 */
	public void setPatterns(List<TransitionGraph.StoredPattern> patterns, TransitionGraph graph) {
		this.currentGraph = graph;
		this.basePatterns = patterns;

		// Compute measure names once from the full base set
		Set<String> measureNameSet = new LinkedHashSet<>();
		for (TransitionGraph.StoredPattern p : patterns) {
			measureNameSet.addAll(p.getMeasures().keySet());
		}
		this.cachedMeasureNames = new ArrayList<>(measureNameSet);

		showPatterns(patterns, "Patterns", true);
	}

	/**
	 * Filters patterns to those containing the given item.
	 * @param itemLabel the item label to filter by
	 */
	public void filterByItem(String itemLabel) {
		if (basePatterns == null) return;
		List<TransitionGraph.StoredPattern> filtered = new ArrayList<>();
		for (TransitionGraph.StoredPattern p : basePatterns) {
			if (p.containsItem(itemLabel)) filtered.add(p);
		}
		showPatterns(filtered, "Patterns with '" + itemLabel + "'", false);
	}

	/**
	 * Filters patterns to those containing the given edge.
	 * @param edgeKey the edge key (e.g. "A->B:SEQUENTIAL")
	 */
	public void filterByEdge(String edgeKey) {
		if (basePatterns == null) return;
		List<TransitionGraph.StoredPattern> filtered = new ArrayList<>();
		for (TransitionGraph.StoredPattern p : basePatterns) {
			if (p.getEdgeKeys().contains(edgeKey)) {
				filtered.add(p);
			}
		}

		// Build a readable label for the header
		String label = edgeKey;
		if (currentGraph != null) {
			TransitionGraph.GraphEdge edge = currentGraph.getEdges().get(edgeKey);
			if (edge != null) {
				String type = edge.getEdgeType() == TransitionGraph.EdgeType.CO_OCCURRENCE
						? "co-occ" : "seq";
				label = edge.getSourceItem() + " \u2192 " + edge.getTargetItem()
						+ " (" + type + ")";
			}
		}
		showPatterns(filtered, "Patterns with edge '" + label + "'", false);
	}

	/**
	 * Shows all base patterns (removes item/edge sub-filter).
	 */
	public void showAll() {
		if (basePatterns != null) {
			showPatterns(basePatterns, "Patterns", false);
		}
	}

	/**
	 * Updates the displayed patterns.
	 * @param patterns         the patterns to show
	 * @param title            the header title
	 * @param structureChanged true if column structure changed, false if only data
	 */
	private void showPatterns(List<TransitionGraph.StoredPattern> patterns, String title,
			boolean structureChanged) {
		this.currentPatterns = patterns;

		if (structureChanged) {
			tableModel.setData(patterns, cachedMeasureNames);
			rowSorter.setModel(tableModel);
			for (int col = 1; col < tableModel.getColumnCount(); col++) {
				rowSorter.setComparator(col, (Object o1, Object o2) -> compareMeasureValues(o1, o2));
			}
			adjustColumnWidths();
		} else {
			// Same columns, just different data — cheaper update
			tableModel.setDataOnly(patterns);
		}

		headerLabel.setText(title + " (" + patterns.size() + " of " + currentGraph.getTotalPatternCount() + ")");
	}

	/**
	 * Compares measure values for sorting, handling numeric and string values.
	 */
	private int compareMeasureValues(Object o1, Object o2) {
		if (o1 == null && o2 == null) return 0;
		if (o1 == null) return -1;
		if (o2 == null) return 1;
		String s1 = o1.toString(), s2 = o2.toString();
		Double d1 = tryParseDouble(s1), d2 = tryParseDouble(s2);
		if (d1 != null && d2 != null) return Double.compare(d1, d2);
		if (d1 != null) return -1;
		if (d2 != null) return 1;
		return s1.compareTo(s2);
	}

	/**
	 * Attempts to parse a string as a double.
	 * @param s the string to parse
	 * @return the parsed value, or null if not numeric
	 */
	private static Double tryParseDouble(String s) {
		if (s == null || s.isEmpty()) return null;
		try { return Double.parseDouble(s.trim()); }
		catch (NumberFormatException e) { return null; }
	}

	/**
	 * Adjusts column widths for optimal display.
	 */
	private void adjustColumnWidths() {
		if (tableModel.getColumnCount() == 0) return;

		int columnCount = tableModel.getColumnCount();

		// First column = Pattern column
		TableColumn patternCol = patternTable.getColumnModel().getColumn(0);
		patternCol.setMinWidth(150);
		patternCol.setPreferredWidth(300);

		// Other columns (measures)
		for (int i = 1; i < columnCount; i++) {
			TableColumn col = patternTable.getColumnModel().getColumn(i);
			col.setMinWidth(70);
			col.setPreferredWidth(100);
		}
	}

	/**
	 * Selects and scrolls to the specified pattern.
	 * @param pattern the pattern to select, or null to clear
	 */
	public void selectPattern(TransitionGraph.StoredPattern pattern) {
		if (pattern == null) { patternTable.clearSelection(); return; }
		for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
			if (tableModel.getPatternAt(modelRow) == pattern) {
				int viewRow = patternTable.convertRowIndexToView(modelRow);
				patternTable.setRowSelectionInterval(viewRow, viewRow);
				patternTable.scrollRectToVisible(patternTable.getCellRect(viewRow, 0, true));
				return;
			}
		}
	}

	/**
	 * Clears the current selection.
	 */
	public void clearSelection() {
		patternTable.clearSelection();
	}

	/**
	 * Returns the currently selected pattern.
	 * @return the selected pattern, or null if none
	 */
	public TransitionGraph.StoredPattern getSelectedPattern() {
		int viewRow = patternTable.getSelectedRow();
		if (viewRow < 0) return null;
		return tableModel.getPatternAt(patternTable.convertRowIndexToModel(viewRow));
	}

	// =========================================================================
	// Table model
	// =========================================================================

	/**
	 * Table model for displaying patterns with dynamic measure columns.
	 */
	private static class PatternTableModel extends AbstractTableModel {
		/** List of patterns */
		private List<TransitionGraph.StoredPattern> patterns = new ArrayList<>();
		/** List of measure column names */
		private List<String> measureNames = new ArrayList<>();

		/**
		 * Sets data and column structure. Fires structure change.
		 * @param patterns     the patterns
		 * @param measureNames the measure column names
		 */
		public void setData(List<TransitionGraph.StoredPattern> patterns, List<String> measureNames) {
			this.patterns = patterns;
			this.measureNames = measureNames;
			fireTableStructureChanged();
		}

		/**
		 * Sets only data rows, keeping columns. Fires data change.
		 * @param patterns the patterns
		 */
		public void setDataOnly(List<TransitionGraph.StoredPattern> patterns) {
			this.patterns = patterns;
			fireTableDataChanged();
		}

		/**
		 * Returns the pattern at the given row.
		 * @param row the row index
		 * @return the pattern, or null if invalid
		 */
		public TransitionGraph.StoredPattern getPatternAt(int row) {
			if (row < 0 || row >= patterns.size()) return null;
			return patterns.get(row);
		}

		@Override public int getRowCount() { return patterns.size(); }
		@Override public int getColumnCount() { return 1 + measureNames.size(); }

		@Override
		public String getColumnName(int column) {
			if (column == 0) return "Pattern";
			return measureNames.get(column - 1);
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnIndex == 0 ? String.class : Object.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			TransitionGraph.StoredPattern pattern = patterns.get(rowIndex);
			if (columnIndex == 0) return pattern.getSequenceString();
			String measureName = measureNames.get(columnIndex - 1);
			String value = pattern.getMeasures().get(measureName);
			return value != null ? value : "";
		}
	}

	// =========================================================================
	// Cell renderer
	// =========================================================================

	/**
	 * Custom cell renderer with alternating row colors and multi-item highlighting.
	 */
	private static class PatternCellRenderer extends DefaultTableCellRenderer {
		/** Color for patterns containing multi-item itemsets */
		private static final Color MULTI_ITEM_COLOR = new Color(0, 100, 0);
		/** Alternating row background color */
		private static final Color ALT_ROW_COLOR = new Color(245, 245, 250);

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
				boolean isSelected, boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (c instanceof JLabel) {
				JLabel label = (JLabel) c;
				if (column == 0) {
					label.setHorizontalAlignment(JLabel.LEFT);
				} else {
					String val = value != null ? value.toString().trim() : "";
					label.setHorizontalAlignment(
							isNumericString(val) ? JLabel.RIGHT : JLabel.LEFT);
				}
				if (!isSelected) {
					label.setBackground(row % 2 == 1 ? ALT_ROW_COLOR : Color.WHITE);
					if (column == 0) {
						int modelRow = table.convertRowIndexToModel(row);
						PatternTableModel model = (PatternTableModel) table.getModel();
						TransitionGraph.StoredPattern pattern = model.getPatternAt(modelRow);
						boolean hasMulti = pattern != null && pattern.hasMultiItemItemset();
						label.setForeground(hasMulti ? MULTI_ITEM_COLOR : Color.BLACK);
					} else {
						label.setForeground(Color.BLACK);
					}
				}
			}
			return c;
		}

		/**
		 * Fast check whether a string looks numeric.
		 * @param s the string to check
		 * @return true if numeric
		 */
		private static boolean isNumericString(String s) {
			if (s == null || s.isEmpty()) return false;
			int len = s.length();
			boolean hasDigit = false;
			boolean hasDot = false;
			for (int i = 0; i < len; i++) {
				char ch = s.charAt(i);
				if (ch >= '0' && ch <= '9') {
					hasDigit = true;
				} else if (ch == '.') {
					if (hasDot) return false;
					hasDot = true;
				} else if (ch == '-' || ch == '+') {
					if (i != 0) return false;
				} else if (ch == 'E' || ch == 'e') {
					if (i == 0 || i == len - 1) return false;
				} else {
					return false;
				}
			}
			return hasDigit;
		}
	}
}