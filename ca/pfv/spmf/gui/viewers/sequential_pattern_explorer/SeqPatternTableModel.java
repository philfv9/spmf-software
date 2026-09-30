package ca.pfv.spmf.gui.viewers.sequential_pattern_explorer;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

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
 * Table model: fixed columns (Pattern, #Itemsets, #Items) followed by one
 * column per measure found in the file. Measure columns are typed as Long,
 * Double or String depending on their content so that sorting is numeric.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class SeqPatternTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	public static final int COL_PATTERN = 0;
	public static final int COL_ITEMSETS = 1;
	public static final int COL_ITEMS = 2;
	public static final int FIXED_COLUMNS = 3;

	private List<SeqPattern> patterns = new ArrayList<>();
	private List<String> measureNames = new ArrayList<>();
	private Class<?>[] measureClasses = new Class<?>[0];
	/** measureValues[row][measureIndex] : Long, Double, String or null */
	private Object[][] measureValues = new Object[0][0];

	public void setData(List<SeqPattern> patterns, List<String> measureNames) {
		this.patterns = patterns != null ? patterns : new ArrayList<>();
		this.measureNames = measureNames != null ? measureNames : new ArrayList<>();
		computeMeasureColumns();
		fireTableStructureChanged();
	}

	private void computeMeasureColumns() {
		int m = measureNames.size();
		int n = patterns.size();
		measureClasses = new Class<?>[m];
		measureValues = new Object[n][m];

		for (int j = 0; j < m; j++) {
			String name = measureNames.get(j);
			boolean allLong = true, allDouble = true;
			for (int i = 0; i < n; i++) {
				String v = patterns.get(i).getMeasure(name);
				if (v == null || v.isEmpty()) continue;
				if (allLong) {
					try { Long.parseLong(v); } catch (NumberFormatException e) { allLong = false; }
				}
				if (!allLong && allDouble) {
					try { Double.parseDouble(v); } catch (NumberFormatException e) { allDouble = false; }
				}
				if (!allLong && !allDouble) break;
			}
			Class<?> cls = allLong ? Long.class : (allDouble ? Double.class : String.class);
			measureClasses[j] = cls;
			for (int i = 0; i < n; i++) {
				String v = patterns.get(i).getMeasure(name);
				if (v == null || v.isEmpty()) { measureValues[i][j] = null; continue; }
				if (cls == Long.class) measureValues[i][j] = Long.valueOf(v);
				else if (cls == Double.class) measureValues[i][j] = Double.valueOf(v);
				else measureValues[i][j] = v;
			}
		}
	}

	public SeqPattern getPattern(int modelRow) { return patterns.get(modelRow); }

	public List<String> getMeasureNames() { return measureNames; }

	public boolean isMeasureNumeric(int measureIndex) {
		return measureIndex >= 0 && measureIndex < measureClasses.length
				&& measureClasses[measureIndex] != String.class;
	}

	/** @return the numeric value of the measure or null if absent / not numeric */
	public Double getMeasureNumber(int modelRow, int measureIndex) {
		Object v = measureValues[modelRow][measureIndex];
		return (v instanceof Number) ? ((Number) v).doubleValue() : null;
	}

	@Override public int getRowCount() { return patterns.size(); }

	@Override public int getColumnCount() { return FIXED_COLUMNS + measureNames.size(); }

	@Override
	public String getColumnName(int column) {
		switch (column) {
		case COL_PATTERN: return "Pattern";
		case COL_ITEMSETS: return "#Itemsets";
		case COL_ITEMS: return "#Items";
		default: return measureNames.get(column - FIXED_COLUMNS);
		}
	}

	@Override
	public Class<?> getColumnClass(int column) {
		switch (column) {
		case COL_PATTERN: return String.class;
		case COL_ITEMSETS:
		case COL_ITEMS: return Integer.class;
		default: return measureClasses[column - FIXED_COLUMNS];
		}
	}

	@Override
	public Object getValueAt(int row, int column) {
		SeqPattern p = patterns.get(row);
		switch (column) {
		case COL_PATTERN: return p.toDisplayString();
		case COL_ITEMSETS: return p.getItemsetCount();
		case COL_ITEMS: return p.getItemCount();
		default: return measureValues[row][column - FIXED_COLUMNS];
		}
	}

	@Override public boolean isCellEditable(int row, int column) { return false; }
}