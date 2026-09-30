package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.gui.visual_pattern_viewer.LayoutMode;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternListPanel;

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
 * Panel displaying each sequential pattern read by a
 * {@link SequentialPatternsReader} as a {@link SequentialPatternPanel}.
 * Sorting, search, filters, pagination and export are inherited from
 * {@link PatternListPanel}.
 *
 * @author Philippe Fournier-Viger
 */
public class SequentialPatternsPanel extends PatternListPanel<SequentialPattern, SequentialPatternPanel> {

	private static final long serialVersionUID = 1L;

	/** The reader holding the sequential patterns */
	private final SequentialPatternsReader sequenceReader;

	/**
	 * Constructor
	 *
	 * @param reader a reader that has already parsed the file
	 * @param mode   the layout mode
	 * @throws IOException if reading the file fails
	 */
	public SequentialPatternsPanel(SequentialPatternsReader reader, LayoutMode mode) throws IOException {
		super(reader, mode);
		this.sequenceReader = reader;
		initialize();
	}

	@Override
	protected List<SequentialPattern> getPatterns() {
		return sequenceReader.getPatterns();
	}

	@Override
	protected SequentialPatternPanel createPanel(SequentialPattern pattern) {
		return new SequentialPatternPanel(pattern, minValues, maxValues);
	}

	@Override
	protected Map<String, String> getMeasures(SequentialPattern pattern) {
		return pattern.getMeasures();
	}

	@Override
	protected String getSearchableText(SequentialPattern pattern) {
		return pattern.toString();
	}

	/** The size of a sequential pattern is its number of itemsets (events). */
	@Override
	protected int getPatternSize(SequentialPattern pattern) {
		return pattern.getItemsets().size();
	}

	@Override
	protected String getPatternTypeName() {
		return "sequential patterns";
	}
}