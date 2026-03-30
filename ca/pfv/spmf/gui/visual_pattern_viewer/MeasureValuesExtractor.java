package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Utility class for extracting measure values from patterns
 * for use in histogram visualization.
 * 
 * @author Philippe Fournier-Viger 2025
 */
public class MeasureValuesExtractor {

	/**
	 * Extracts all numeric measure values from the patterns panel.
	 * Only measures that have valid min/max values (i.e., numeric measures) are included.
	 * 
	 * @param patternsPanel the patterns panel
	 * @return a map from measure name to list of double values (only numeric measures included)
	 */
	public static Map<String, List<Double>> extractMeasureValues(PatternsPanel patternsPanel) {
		Map<String, List<Double>> result = new LinkedHashMap<>();
		
		Set<String> measures = patternsPanel.getAllMeasures();
		
		// Only add measures that have valid min/max values (i.e., numeric measures)
		for (String measure : measures) {
			Double minVal = patternsPanel.getMinForMeasureOriginal(measure);
			Double maxVal = patternsPanel.getMaxForMeasureOriginal(measure);
			
			// Only include numeric measures (those with valid min and max values)
			if (minVal != null && maxVal != null) {
				result.put(measure, new ArrayList<>());
			}
		}
		
		// Populate the map with measure values from all patterns
		patternsPanel.populateMeasureValues(result);
		
		return result;
	}
}