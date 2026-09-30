package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Table model: one row per rule; columns = antecedent, consequent, then one
 * column per measure.
 * 
 * @author Philippe Fournier-Viger
 */
public class RuleTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private List<ARule> rules = new ArrayList<>();
	private final Map<ARule, Integer> index = new HashMap<>();
	private List<String> measureNames = new ArrayList<>();

	public void setStructure(List<String> measureNames) {
		this.measureNames = new ArrayList<>(measureNames);
		fireTableStructureChanged();
	}

	public void setRules(List<ARule> rules) {
		this.rules = rules;
		index.clear();
		for (int i = 0; i < rules.size(); i++) {
			index.put(rules.get(i), i);
		}
		fireTableDataChanged();
	}

	public ARule getRule(int modelRow) {
		return rules.get(modelRow);
	}

	public int indexOf(ARule rule) {
		Integer i = index.get(rule);
		return i == null ? -1 : i;
	}

	/** @return the measure shown in a model column, or null for the item columns */
	public String getMeasureAt(int modelColumn) {
		return modelColumn < 2 ? null : measureNames.get(modelColumn - 2);
	}

	public String getColumnTooltip(int modelColumn) {
		if (modelColumn == 0) {
			return "The IF part of the rule. Click the header to sort.";
		}
		if (modelColumn == 1) {
			return "The THEN part of the rule. Click the header to sort.";
		}
		return MeasureUtils.describe(measureNames.get(modelColumn - 2)) + " Click the header to sort.";
	}

	@Override
	public int getRowCount() {
		return rules.size();
	}

	@Override
	public int getColumnCount() {
		return 2 + measureNames.size();
	}

	@Override
	public String getColumnName(int column) {
		if (column == 0) {
			return "IF (antecedent)";
		}
		if (column == 1) {
			return "THEN (consequent)";
		}
		return MeasureUtils.prettyName(measureNames.get(column - 2));
	}

	@Override
	public Class<?> getColumnClass(int column) {
		return column < 2 ? String.class : Double.class;
	}

	@Override
	public Object getValueAt(int row, int column) {
		ARule rule = rules.get(row);
		if (column == 0) {
			return rule.getAntecedentText();
		}
		if (column == 1) {
			return rule.getConsequentText();
		}
		double v = rule.getMeasure(measureNames.get(column - 2));
		return Double.isNaN(v) ? null : Double.valueOf(v);
	}
}
