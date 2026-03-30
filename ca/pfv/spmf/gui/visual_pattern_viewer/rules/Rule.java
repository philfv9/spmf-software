package ca.pfv.spmf.gui.visual_pattern_viewer.rules;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import ca.pfv.spmf.gui.visual_pattern_viewer.Pattern;

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
 */
/**
 * Represents a single rule, including its antecedent, consequent,
 * and its evaluation measures and corresponding values.
 * @author Philippe Fournier-Viger
 */
public class Rule implements Pattern {

	/**
	 * List of antecedent items in the rule.
	 */
	private final List<String> antecedent;

	/**
	 * List of consequent items in the rule.
	 */
	private final List<String> consequent;

	/**
	 * Map of measure names to their string values (e.g., support, confidence).
	 */
	private final Map<String, String> measures;
	
	/**
	 * Cached string representation
	 */
	private final String cachedToString;

	/**
	 * Constructs a new Rule instance.
	 *
	 * @param antecedent list of items on the left-hand side (antecedent)
	 * @param consequent list of items on the right-hand side (consequent)
	 * @param measures   mapping of measure names to measure values
	 * @throws NullPointerException if any argument is null
	 */
	public Rule(List<String> antecedent, List<String> consequent, Map<String, String> measures) {
		this.antecedent = antecedent;
		this.consequent = consequent;
		this.measures = measures;
		cachedToString = antecedent + " --> " + consequent;
	}

	/**
	 * @return the list of items in the antecedent
	 */
	public List<String> getAntecedent() {
		return antecedent;
	}

	/**
	 * @return the list of items in the consequent
	 */
	public List<String> getConsequent() {
		return consequent;
	}

	/**
	 * @return map of measure names to their values
	 */
	public Map<String, String> getMeasures() {
		return measures;
	}

	/**
	 * @return a human-readable representation of the rule
	 */
	@Override
	public String toString() {
		return cachedToString;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Rule))
			return false;
		Rule that = (Rule) o;
		return getAntecedent().equals(that.getAntecedent()) && getConsequent().equals(that.getConsequent())
				&& getMeasures().equals(that.getMeasures());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getAntecedent(), getConsequent(), getMeasures());
	}
}