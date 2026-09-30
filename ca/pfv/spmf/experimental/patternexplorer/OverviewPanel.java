package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

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
 * The aggregated view of the current selection: a summary line, the
 * pattern-size chart, the measure histogram, and a table giving the
 * statistics of every numeric measure at the same time. All of these help the
 * user decide where to drill next.
 * 
 * @author Philippe Fournier-Viger
 */
public class OverviewPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final PatternCollection collection;
	private final JLabel summaryLabel;
	private final SizeDistributionChart sizeChart;
	private final MeasureDistributionChart measureChart;
	private final MeasureStatsModel statsModel;

	private QueryResult result;

	/**
	 * Constructor
	 * 
	 * @param controller the controller
	 * @param collection the collection
	 */
	public OverviewPanel(ExplorerController controller, PatternCollection collection) {
		super(new BorderLayout(0, 4));
		this.collection = collection;
		setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));

		summaryLabel = new JLabel(" ");
		summaryLabel.setFont(ExplorerStyle.FONT_TITLE);
		add(summaryLabel, BorderLayout.NORTH);

		JPanel charts = new JPanel(new GridLayout(1, 0, 6, 0));
		charts.setOpaque(false);
		sizeChart = new SizeDistributionChart(controller);
		measureChart = new MeasureDistributionChart(controller, collection);
		charts.add(sizeChart);
		charts.add(measureChart);

		// Statistics of all numeric measures
		statsModel = new MeasureStatsModel();
		JTable statsTable = new JTable(statsModel);
		statsTable.setFont(ExplorerStyle.FONT_NORMAL);
		statsTable.setRowHeight(20);
		statsTable.setFillsViewportHeight(true);
		statsTable.setShowGrid(false);
		statsTable.setIntercellSpacing(new Dimension(0, 0));
		statsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		statsTable.getTableHeader().setReorderingAllowed(false);
		statsTable.setAutoCreateRowSorter(true);
		statsTable.setToolTipText("Statistics of each numeric measure over the matching patterns. "
				+ "Double-click a row to show its histogram.");
		TableColumnModel cm = statsTable.getColumnModel();
		cm.getColumn(0).setPreferredWidth(90);
		for (int c = 1; c < cm.getColumnCount(); c++) {
			cm.getColumn(c).setPreferredWidth(70);
			cm.getColumn(c).setCellRenderer(new ExplorerRenderers.NumberRenderer());
		}
		statsTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					int row = statsTable.rowAtPoint(e.getPoint());
					if (row >= 0) {
						measureChart.selectMeasure(
								collection.getNumericMeasureNames().get(statsTable.convertRowIndexToModel(row)));
					}
				}
			}
		});
		JPanel statsPanel = new JPanel(new BorderLayout());
		statsPanel.setBorder(ExplorerStyle.titledBorder("Measures of the matching patterns"));
		if (collection.getNumericMeasureNames().isEmpty()) {
			JLabel none = ExplorerStyle.mutedLabel("No numeric measure in this file");
			statsPanel.add(none, BorderLayout.NORTH);
		} else {
			statsPanel.add(new JScrollPane(statsTable), BorderLayout.CENTER);
		}
		List<String> textual = textualMeasures();
		if (!textual.isEmpty()) {
			JLabel textLabel = ExplorerStyle.mutedLabel(
					"<html>Text attributes: " + ExplorerHtml.escape(String.join(", ", textual)) + "</html>");
			statsPanel.add(textLabel, BorderLayout.SOUTH);
		}
		charts.add(statsPanel);
		add(charts, BorderLayout.CENTER);
		setPreferredSize(new Dimension(800, 250));
		setMinimumSize(new Dimension(300, 120));
	}

	private List<String> textualMeasures() {
		List<String> list = new java.util.ArrayList<>();
		for (String m : collection.getMeasureNames()) {
			if (!collection.isNumericMeasure(m)) {
				list.add(m);
			}
		}
		return list;
	}

	/** @return the measure chart (to synchronize the selected measure) */
	public MeasureDistributionChart getMeasureChart() {
		return measureChart;
	}

	/**
	 * Update all aggregated views.
	 * 
	 * @param result the current result
	 */
	public void update(QueryResult result) {
		this.result = result;
		int total = collection.size();
		StringBuilder sb = new StringBuilder("<html>");
		if (result.size() == total) {
			sb.append("<b>").append(ExplorerFormat.integer(total)).append("</b> patterns");
		} else {
			sb.append("<b>").append(ExplorerFormat.integer(result.size())).append("</b> of ")
					.append(ExplorerFormat.integer(total)).append(" patterns (")
					.append(ExplorerFormat.percent(result.getFractionOfCollection())).append(")");
		}
		sb.append(" &nbsp;&middot;&nbsp; ").append(ExplorerFormat.count(result.getDistinctItemCount(), "distinct item"));
		if (!result.isEmpty()) {
			int min = result.getSizeDistribution().firstKey();
			int max = result.getSizeDistribution().lastKey();
			sb.append(" &nbsp;&middot;&nbsp; size ").append(min == max ? String.valueOf(min) : min + "\u2013" + max);
		}
		sb.append("</html>");
		summaryLabel.setText(sb.toString());
		sizeChart.update(result);
		measureChart.update(result);
		statsModel.fireTableDataChanged();
	}

	/** Table model for the statistics of the numeric measures */
	private final class MeasureStatsModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] columns = { "Measure", "Values", "Min", "Mean", "Max", "Sum" };

		@Override
		public int getRowCount() {
			return collection.getNumericMeasureNames().size();
		}

		@Override
		public int getColumnCount() {
			return columns.length;
		}

		@Override
		public String getColumnName(int column) {
			return columns[column];
		}

		@Override
		public Class<?> getColumnClass(int column) {
			if (column == 0) {
				return String.class;
			}
			return column == 1 ? Integer.class : Double.class;
		}

		@Override
		public Object getValueAt(int row, int column) {
			if (column == 0) {
				return collection.getNumericMeasureNames().get(row);
			}
			if (result == null) {
				return null;
			}
			MeasureStats stats = result.getMeasureStats(row);
			switch (column) {
			case 1:
				return stats.getCount();
			case 2:
				return stats.getMin();
			case 3:
				return stats.getMean();
			case 4:
				return stats.getMax();
			default:
				return stats.hasValues() ? stats.getSum() : Double.NaN;
			}
		}
	}
}