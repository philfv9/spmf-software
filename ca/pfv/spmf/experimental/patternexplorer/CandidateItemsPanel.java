package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
 * The drill-down panel: lists the items that can be added to the current
 * query, with the number of matching patterns containing each item, the
 * corresponding percentage and a statistic (avg/min/max/sum) of every numeric
 * measure over those patterns. Items can be required, excluded, or used as a
 * "only these items" restriction.
 * 
 * @author Philippe Fournier-Viger
 */
public class CandidateItemsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Statistics offered for the measure columns */
	private static final String[] STATISTICS = { "avg", "min", "max", "sum" };

	private final ExplorerController controller;
	private final PatternCollection collection;
	private final CandidateTableModel model;
	private final JTable table;
	private final TableRowSorter<CandidateTableModel> sorter;
	private final JTextField filterField;
	private final JLabel titleLabel;
	private final JComboBox<String> statisticCombo;
	private final JButton requireButton;
	private final JButton excludeButton;
	private final JButton withinButton;

	/** Index in STATISTICS of the statistic currently shown */
	private int statistic = 0;
	/** Largest count of the current candidates (for the bars) */
	private int maxCount = 1;

	/**
	 * Constructor
	 * 
	 * @param controller the controller
	 * @param collection the collection
	 */
	public CandidateItemsPanel(ExplorerController controller, PatternCollection collection) {
		super(new BorderLayout(0, 4));
		this.controller = controller;
		this.collection = collection;
		setBorder(ExplorerStyle.titledBorder("Drill down by item"));
		setPreferredSize(new Dimension(400, 400));
		setMinimumSize(new Dimension(260, 200));

		// ---- North: title, hint, filter ----
		JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.setOpaque(false);
		titleLabel = new JLabel(" ");
		titleLabel.setFont(ExplorerStyle.FONT_BOLD);
		titleLabel.setAlignmentX(LEFT_ALIGNMENT);
		JLabel hint = ExplorerStyle.mutedLabel(
				"<html>Items of the matching patterns. Double-click an item to keep only the patterns containing it.</html>");
		hint.setAlignmentX(LEFT_ALIGNMENT);
		JPanel filterRow = new JPanel(new BorderLayout(4, 0));
		filterRow.setOpaque(false);
		filterRow.setAlignmentX(LEFT_ALIGNMENT);
		filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		filterRow.add(new JLabel("Find item:"), BorderLayout.WEST);
		filterField = new JTextField();
		filterField.setToolTipText("Type to filter the list of items (press Enter to require the only matching item)");
		filterRow.add(filterField, BorderLayout.CENTER);
		JButton clearFilter = new JButton("\u00D7");
		ExplorerStyle.makeCompact(clearFilter);
		clearFilter.setToolTipText("Clear the item filter");
		clearFilter.addActionListener(e -> filterField.setText(""));
		filterRow.add(clearFilter, BorderLayout.EAST);
		north.add(titleLabel);
		north.add(hint);
		north.add(Box.createVerticalStrut(4));
		north.add(filterRow);
		add(north, BorderLayout.NORTH);

		// ---- Center: table ----
		model = new CandidateTableModel();
		table = new JTable(model);
		table.setFont(ExplorerStyle.FONT_NORMAL);
		table.setRowHeight(20);
		table.setFillsViewportHeight(true);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setTableHeader(new JTableHeader(table.getColumnModel()) {
			private static final long serialVersionUID = 1L;

			@Override
			public String getToolTipText(MouseEvent e) {
				int viewColumn = columnAtPoint(e.getPoint());
				if (viewColumn < 0) {
					return null;
				}
				return headerTooltip(table.convertColumnIndexToModel(viewColumn));
			}
		});
		table.getTableHeader().setReorderingAllowed(false);
		sorter = new TableRowSorter<>(model);
		table.setRowSorter(sorter);
		sorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
		configureColumns();

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
					requireSelected();
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
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "requireSelected");
		table.getActionMap().put("requireSelected", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				requireSelected();
			}
		});
		table.getSelectionModel().addListSelectionListener(e -> updateButtons());
		add(new JScrollPane(table), BorderLayout.CENTER);

		// ---- South: actions ----
		JPanel south = new JPanel();
		south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
		south.setOpaque(false);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.setOpaque(false);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		requireButton = new JButton("+ Require");
		ExplorerStyle.makeCompact(requireButton);
		requireButton.setForeground(ExplorerStyle.REQUIRED_FG);
		requireButton.setToolTipText("Keep only the patterns containing the selected item(s)");
		requireButton.addActionListener(e -> requireSelected());
		excludeButton = new JButton("\u2212 Exclude");
		ExplorerStyle.makeCompact(excludeButton);
		excludeButton.setForeground(ExplorerStyle.EXCLUDED_FG);
		excludeButton.setToolTipText("Remove the patterns containing the selected item(s)");
		excludeButton.addActionListener(e -> excludeSelected());
		withinButton = new JButton("\u2286 Only these");
		ExplorerStyle.makeCompact(withinButton);
		withinButton.setToolTipText(
				"<html>Keep only the patterns made exclusively of the selected items<br>(plus the items already required)</html>");
		withinButton.addActionListener(e -> restrictWithinSelected());
		buttons.add(requireButton);
		buttons.add(excludeButton);
		buttons.add(withinButton);

		JPanel statRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		statRow.setOpaque(false);
		statRow.setAlignmentX(LEFT_ALIGNMENT);
		statisticCombo = new JComboBox<>(STATISTICS);
		statisticCombo.setFont(ExplorerStyle.FONT_SMALL);
		statisticCombo.setToolTipText("Statistic shown in the measure columns");
		statisticCombo.addActionListener(e -> {
			statistic = statisticCombo.getSelectedIndex();
			structureChanged();
		});
		if (!collection.getNumericMeasureNames().isEmpty()) {
			statRow.add(ExplorerStyle.mutedLabel("Measure statistic:"));
			statRow.add(statisticCombo);
		}
		south.add(buttons);
		south.add(Box.createVerticalStrut(3));
		south.add(statRow);
		add(south, BorderLayout.SOUTH);

		// ---- Filter behaviour ----
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				applyFilter();
			}
		});
		filterField.addActionListener(e -> {
			if (table.getRowCount() == 1) {
				controller.requireItems(List.of(model.getRow(table.convertRowIndexToModel(0)).getItem()));
				filterField.setText("");
			}
		});
		updateButtons();
	}

	// ------------------------------------------------------------------
	// Update
	// ------------------------------------------------------------------

	/**
	 * Show the candidate items of a query result.
	 * 
	 * @param result the result
	 */
	public void update(QueryResult result) {
		List<CandidateItemStats> candidates = result.getCandidateItems();
		maxCount = candidates.isEmpty() ? 1 : candidates.get(0).getCount();
		model.setRows(candidates);
		titleLabel.setText(ExplorerFormat.count(candidates.size(), "candidate item")
				+ (result.getQuery().getRequiredItems().isEmpty() ? "" : " to add to the query"));
		if (sorter.getSortKeys().isEmpty()) {
			sorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
		}
		updateButtons();
	}

	private void structureChanged() {
		List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
		model.fireTableStructureChanged();
		configureColumns();
		if (keys != null && !keys.isEmpty() && keys.get(0).getColumn() < model.getColumnCount()) {
			sorter.setSortKeys(keys);
		}
	}

	private void configureColumns() {
		TableColumnModel columns = table.getColumnModel();
		columns.getColumn(0).setPreferredWidth(140);
		columns.getColumn(1).setPreferredWidth(110);
		columns.getColumn(1).setCellRenderer(new ExplorerRenderers.BarRenderer(() -> maxCount, ExplorerStyle.ACCENT));
		columns.getColumn(2).setPreferredWidth(60);
		columns.getColumn(2).setCellRenderer(new ExplorerRenderers.PercentRenderer());
		for (int c = 3; c < columns.getColumnCount(); c++) {
			columns.getColumn(c).setPreferredWidth(85);
			columns.getColumn(c).setCellRenderer(new ExplorerRenderers.NumberRenderer());
		}
	}

	private String headerTooltip(int modelColumn) {
		switch (modelColumn) {
		case 0:
			return "Item name";
		case 1:
			return "Number of matching patterns that contain the item";
		case 2:
			return "Percentage of the matching patterns that contain the item";
		default:
			String measure = collection.getNumericMeasureNames().get(modelColumn - 3);
			return STATISTICS[statistic] + " of " + measure + " over the matching patterns containing the item";
		}
	}

	private void applyFilter() {
		String text = filterField.getText().trim();
		if (text.isEmpty()) {
			sorter.setRowFilter(null);
		} else {
			sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 0));
		}
	}

	private void updateButtons() {
		boolean any = table.getSelectedRowCount() > 0;
		requireButton.setEnabled(any);
		excludeButton.setEnabled(any);
		withinButton.setEnabled(any);
	}

	// ------------------------------------------------------------------
	// Actions
	// ------------------------------------------------------------------

	private List<String> getSelectedItems() {
		List<String> items = new ArrayList<>();
		for (int viewRow : table.getSelectedRows()) {
			items.add(model.getRow(table.convertRowIndexToModel(viewRow)).getItem());
		}
		return items;
	}

	private void requireSelected() {
		List<String> items = getSelectedItems();
		if (!items.isEmpty()) {
			controller.requireItems(items);
		}
	}

	private void excludeSelected() {
		List<String> items = getSelectedItems();
		if (!items.isEmpty()) {
			controller.excludeItems(items);
		}
	}

	private void restrictWithinSelected() {
		List<String> items = getSelectedItems();
		if (!items.isEmpty()) {
			controller.restrictWithin(items);
		}
	}

	private void maybeShowPopup(MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}
		int row = table.rowAtPoint(e.getPoint());
		if (row >= 0 && !table.isRowSelected(row)) {
			table.setRowSelectionInterval(row, row);
		}
		if (table.getSelectedRowCount() == 0) {
			return;
		}
		JPopupMenu menu = new JPopupMenu();
		JMenuItem require = new JMenuItem("+ Require selected item(s)");
		require.addActionListener(a -> requireSelected());
		JMenuItem exclude = new JMenuItem("\u2212 Exclude selected item(s)");
		exclude.addActionListener(a -> excludeSelected());
		JMenuItem within = new JMenuItem("\u2286 Keep only patterns made of the selected items");
		within.addActionListener(a -> restrictWithinSelected());
		menu.add(require);
		menu.add(exclude);
		menu.addSeparator();
		menu.add(within);
		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	// ------------------------------------------------------------------
	// Table model
	// ------------------------------------------------------------------

	/** Table model over the candidate items */
	private final class CandidateTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private List<CandidateItemStats> rows = Collections.emptyList();

		void setRows(List<CandidateItemStats> rows) {
			this.rows = rows;
			fireTableDataChanged();
		}

		CandidateItemStats getRow(int row) {
			return rows.get(row);
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 3 + collection.getNumericMeasureNames().size();
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "Item";
			case 1:
				return "Patterns";
			case 2:
				return "%";
			default:
				return STATISTICS[statistic] + " " + collection.getNumericMeasureNames().get(column - 3);
			}
		}

		@Override
		public Class<?> getColumnClass(int column) {
			switch (column) {
			case 0:
				return String.class;
			case 1:
				return Integer.class;
			default:
				return Double.class;
			}
		}

		@Override
		public Object getValueAt(int row, int column) {
			CandidateItemStats stats = rows.get(row);
			switch (column) {
			case 0:
				return stats.getItem();
			case 1:
				return stats.getCount();
			case 2:
				return stats.getFraction();
			default:
				int k = column - 3;
				switch (statistic) {
				case 1:
					return stats.getMin(k);
				case 2:
					return stats.getMax(k);
				case 3:
					return stats.getSum(k);
				default:
					return stats.getMean(k);
				}
			}
		}
	}

	/** @return the table component (for focus management) */
	public Component getTable() {
		return table;
	}
}