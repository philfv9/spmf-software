package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.io.IOException;

import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.ItemsetsPanel;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.ItemsetsReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.UncertainItemsetsReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.rules.RulesPanel;
import ca.pfv.spmf.gui.visual_pattern_viewer.rules.RulesReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.SequentialPatternsPanel;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.SequentialPatternsReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.StringGapPatternReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.StringNoGapPattenrReader;

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
 * A factory for Pattern pannels for the Visual Pattern Viewer
 * @author Philippe Fournier-Viger
 */
public class PatternPanelFactory {

	/**
	 * Private constructor
	 */
	private PatternPanelFactory() {
		
	}
	
	/**
	 * Create a panel to display patterns
	 * @param filePath the path to a pattern file
	 * @param type the type of patterns as a String
	 * @param mode the layout mode
	 * @return the panel or null if this type of files cannot be viewed
	 * @throws IOException if error reading  the pattern file.
	 */
	public static PatternsPanel getPatternPanel(String filePath, PatternType type, LayoutMode mode) throws IOException {
		if(PatternType.ASSOCIATION_RULES.equals(type)) {
			RulesReader reader = new RulesReader(filePath);
			return new RulesPanel(reader, mode);
		}else if(PatternType.STRING_SEQUENTIAL_PATTERNS.equals(type)) {
			SequentialPatternsReader reader = new StringGapPatternReader(filePath);
			return new SequentialPatternsPanel(reader, mode);
		}else if(PatternType.STRING_SEQUENTIAL_PATTERNS_NO_GAP.equals(type)) {
			SequentialPatternsReader reader = new StringNoGapPattenrReader(filePath);
			return new SequentialPatternsPanel(reader, mode);
		}else if(PatternType.SEQUENTIAL_PATTERNS.equals(type)) {
			SequentialPatternsReader reader = new SequentialPatternsReader(filePath);
			return new SequentialPatternsPanel(reader, mode);
		}else if(PatternType.ITEMSETS.equals(type)) {
			ItemsetsReader reader = new ItemsetsReader(filePath);
			return new ItemsetsPanel(reader, mode);
		}else if(PatternType.UNCERTAIN_ITEMSETS.equals(type)) {
			ItemsetsReader reader = new UncertainItemsetsReader(filePath);
			return new ItemsetsPanel(reader, mode);
		}
		return null;
	}
	


}
