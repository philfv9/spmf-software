package ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information.
 */
/**
 * Panel displaying each subgraph pattern read by a {@link SubgraphsReader} as
 * a {@link SubgraphPanel}. Sorting, search, filters, pagination and export are
 * inherited from {@link PatternListPanel}.
 *
 * @author Philippe Fournier-Viger
 */
public class SubgraphsPanel extends PatternListPanel<Subgraph, SubgraphPanel> {

	/** Serial UID. */
	private static final long serialVersionUID = 8812093471239847123L;

	/** The reader holding the subgraphs */
	private final SubgraphsReader subgraphsReader;

	/**
	 * Constructor
	 *
	 * @param reader a {@link SubgraphsReader} that has already parsed the file
	 * @param mode   the layout mode
	 * @throws IOException if reading the file fails
	 */
	public SubgraphsPanel(PatternsReader reader, LayoutMode mode) throws IOException {
		super(reader, mode);
		this.subgraphsReader = (SubgraphsReader) reader;
		initialize();
	}

	@Override
	protected List<Subgraph> getPatterns() {
		return subgraphsReader.getPatterns();
	}

	@Override
	protected SubgraphPanel createPanel(Subgraph subgraph) {
		return new SubgraphPanel(subgraph, minValues, maxValues);
	}

	@Override
	protected Map<String, String> getMeasures(Subgraph subgraph) {
		return subgraph.getMeasures();
	}

	/**
	 * Only vertex and edge labels are searched (not vertex ids or the ids of the
	 * graphs containing the pattern).
	 */
	@Override
	protected String getSearchableText(Subgraph subgraph) {
		return subgraph.toSearchableString();
	}

	/** The size of a subgraph is its number of vertices. */
	@Override
	protected int getPatternSize(Subgraph subgraph) {
		return subgraph.size();
	}

	@Override
	protected String getPatternTypeName() {
		return "subgraphs";
	}
}