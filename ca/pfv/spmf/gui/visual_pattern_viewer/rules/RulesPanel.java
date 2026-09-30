package ca.pfv.spmf.gui.visual_pattern_viewer.rules;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.gui.visual_pattern_viewer.LayoutMode;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternListPanel;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternsReader;

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
 */
/**
 * Panel displaying each association rule read by a {@link RulesReader} as a
 * {@link RulePanel}. Sorting, search, filters, pagination and export are
 * inherited from {@link PatternListPanel}.
 *
 * @author Philippe Fournier-Viger
 */
public class RulesPanel extends PatternListPanel<Rule, RulePanel> {

	/** serial UID **/
	private static final long serialVersionUID = 123982348L;

	/** The reader holding the rules */
	private final RulesReader rulesReader;

	/**
	 * Constructor
	 *
	 * @param reader a reader that has already parsed the file
	 * @param mode   the layout mode
	 * @throws IOException if reading the file fails
	 */
	public RulesPanel(PatternsReader reader, LayoutMode mode) throws IOException {
		super(reader, mode);
		this.rulesReader = (RulesReader) reader;
		initialize();
	}

	/**
	 * Sorts rules numerically by the given measure, then rebuilds the display.
	 *
	 * @param measure   key of the measure to sort by
	 * @param ascending true for ascending order; false for descending
	 */
	public void sortRulesByMeasure(String measure, boolean ascending) {
		sortPatterns(measure + (ascending ? " (asc)" : " (desc)"));
	}

	@Override
	protected List<Rule> getPatterns() {
		return rulesReader.getPatterns();
	}

	@Override
	protected RulePanel createPanel(Rule rule) {
		return new RulePanel(rule, minValues, maxValues);
	}

	@Override
	protected Map<String, String> getMeasures(Rule rule) {
		return rule.getMeasures();
	}

	@Override
	protected String getSearchableText(Rule rule) {
		return rule.toString();
	}

	@Override
	protected int getPatternSize(Rule rule) {
		return rule.getAntecedent().size() + rule.getConsequent().size();
	}

	@Override
	protected String getPatternTypeName() {
		return "rules";
	}
}