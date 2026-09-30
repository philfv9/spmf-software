package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * An association rule with its numeric interestingness measures, as used by
 * the AssociationRuleExplorer tool.
 *
 * @author Philippe Fournier-Viger
 */
public class ARule {

	private final List<String> antecedent;
	private final List<String> consequent;
	private final boolean antecedentNegated;
	private final boolean consequentNegated;
	/** measure name -> value (insertion order preserved) */
	private final Map<String, Double> measures;
	private final String antecedentText;
	private final String consequentText;

	public ARule(List<String> antecedent, boolean antecedentNegated, List<String> consequent,
			boolean consequentNegated, Map<String, Double> measures) {
		this.antecedent = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(antecedent)));
		this.consequent = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(consequent)));
		this.antecedentNegated = antecedentNegated;
		this.consequentNegated = consequentNegated;
		this.measures = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(measures)));
		this.antecedentText = (antecedentNegated ? "NOT " : "") + "{" + String.join(", ", antecedent) + "}";
		this.consequentText = (consequentNegated ? "NOT " : "") + "{" + String.join(", ", consequent) + "}";
	}

	public List<String> getAntecedent() {
		return antecedent;
	}

	public List<String> getConsequent() {
		return consequent;
	}

	public boolean isAntecedentNegated() {
		return antecedentNegated;
	}

	public boolean isConsequentNegated() {
		return consequentNegated;
	}

	public Map<String, Double> getMeasures() {
		return measures;
	}

	/** @return the value of a measure, or NaN if absent */
	public double getMeasure(String name) {
		Double v = measures.get(name);
		return v == null ? Double.NaN : v;
	}

	/** @return the value of a measure looked up case-insensitively, or NaN */
	public double getMeasureIgnoreCase(String name) {
		for (Map.Entry<String, Double> e : measures.entrySet()) {
			if (e.getKey().equalsIgnoreCase(name)) {
				return e.getValue();
			}
		}
		return Double.NaN;
	}

	public String getAntecedentText() {
		return antecedentText;
	}

	public String getConsequentText() {
		return consequentText;
	}

	/** @return all items of the rule (antecedent then consequent) */
	public List<String> getAllItems() {
		List<String> all = new ArrayList<>(antecedent);
		all.addAll(consequent);
		return all;
	}

	/** @return the number of items in the whole rule (antecedent + consequent) */
	public int getItemCount() {
		return antecedent.size() + consequent.size();
	}

	/**
	 * Return a copy of this rule with an additional measure inserted right after
	 * a given measure (or at the end if that measure does not exist).
	 */
	public ARule withMeasureAfter(String afterKey, String name, double value) {
		Map<String, Double> m = new LinkedHashMap<>();
		boolean inserted = false;
		for (Map.Entry<String, Double> e : measures.entrySet()) {
			if (e.getKey().equals(name)) {
				continue;
			}
			m.put(e.getKey(), e.getValue());
			if (e.getKey().equals(afterKey)) {
				m.put(name, value);
				inserted = true;
			}
		}
		if (!inserted) {
			m.put(name, value);
		}
		return new ARule(antecedent, antecedentNegated, consequent, consequentNegated, m);
	}

	/**
	 * Check if an item of the rule matches a search term (already lower case).
	 * Numeric item ids must match exactly ("1" does not match "12"); textual
	 * items match if they contain the term.
	 */
	public boolean containsItem(String lowerCaseTerm) {
		for (String item : antecedent) {
			if (itemMatches(item, lowerCaseTerm)) {
				return true;
			}
		}
		for (String item : consequent) {
			if (itemMatches(item, lowerCaseTerm)) {
				return true;
			}
		}
		return false;
	}

	static boolean itemMatches(String item, String lowerCaseTerm) {
		String lower = item.toLowerCase();
		if (lower.equals(lowerCaseTerm)) {
			return true;
		}
		if (isNumber(lower)) {
			return false;
		}
		return lower.contains(lowerCaseTerm);
	}

	private static boolean isNumber(String s) {
		if (s.isEmpty()) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/** @return the rule as one line in SPMF format */
	public String toSpmfLine() {
		StringBuilder sb = new StringBuilder();
		if (antecedentNegated) {
			sb.append("NOT ");
		}
		sb.append(String.join(" ", antecedent)).append(" ==> ");
		if (consequentNegated) {
			sb.append("NOT ");
		}
		sb.append(String.join(" ", consequent));
		for (Map.Entry<String, Double> m : measures.entrySet()) {
			sb.append(" #").append(m.getKey()).append(": ").append(MeasureUtils.formatForFile(m.getValue()));
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return antecedentText + " ==> " + consequentText;
	}
}
