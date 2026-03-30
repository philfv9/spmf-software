package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
 * An abstract class for reading a file containing patterns, used by the Visual
 * Pattern Viewer
 * 
 * @author Philippe Fournier-Viger
 * @see VisualPatternViewer
 */
public abstract class PatternsReader {

	/** The set of all available measures **/
	protected Set<String> availableMeasures = new TreeSet<>();

	/** Available sorting orders **/
	protected final List<String> orders = new ArrayList<>();

	/**
	 * For each measure name, the minimum numeric value found across all itemsets.
	 */
	protected final Map<String, Double> minMeasureValuesOriginal = new HashMap<>();

	/**
	 * For each measure name, the maximum numeric value found across all itemsets.
	 */
	protected final Map<String, Double> maxMeasureValuesOriginal = new HashMap<>();

	/**
	 * For each measure name, the minimum numeric value found across all itemsets.
	 */
	protected final Map<String, Double> minMeasureValuesAdjusted = new HashMap<>();

	/**
	 * For each measure name, the maximum numeric value found across all itemsets.
	 */
	protected final Map<String, Double> maxMeasureValuesAdjusted = new HashMap<>();

	/**
	 * Sort the rules based on a sorting order selected by the user
	 * 
	 * @param sortingOrder the sorting order selected by the user
	 */
	public abstract void sortPatterns(String sortingOrder);

	/**
	 * @return an ordered list of all sorting orders offered
	 */
	public List<String> getListOfSortingOrders() {
		return orders;
	}

	/**
	 * Get the list of available measures
	 * 
	 * @return a Set
	 */
	public Set<String> getAllMeasures() {
		return availableMeasures;
	}
	
	

	/**
	 * Reads the SPMF file and parses sequences and support values.
	 * 
	 * @param filePath input file path
	 * @throws IOException if file I/O fails
	 */
	public void readFile(String filePath) throws IOException {
		readFileHelper(filePath);
		computeMinMaxForMeasures(availableMeasures);
		fixMinMaxForMeasures();
	}

	/**
	 * Fix the default min max values for measures
	 */
	private void fixMinMaxForMeasures() {
		// Keep the original values
		minMeasureValuesOriginal.putAll(minMeasureValuesAdjusted);
		maxMeasureValuesOriginal.putAll(maxMeasureValuesAdjusted);

		// Then adjust the values
		minMeasureValuesAdjusted.replace("BOND", 0d);
		minMeasureValuesAdjusted.replace("PVALUE", 0d);
		minMeasureValuesAdjusted.replace("LOSS", 0d);
		minMeasureValuesAdjusted.replace("FVL", 0d);
		minMeasureValuesAdjusted.replace("SUP", 0d);
		Double minUtil = minMeasureValuesAdjusted.get("UTIL");
		if (minUtil != null && minUtil > 0) {
			minMeasureValuesAdjusted.replace("UTIL", 0d);
		}
		minMeasureValuesAdjusted.replace("AUTIL", 0d);
		minMeasureValuesAdjusted.replace("CONF", 0d);
		maxMeasureValuesAdjusted.replace("CONF", 1d);
		minMeasureValuesAdjusted.replace("UCONF", 0d);
		maxMeasureValuesAdjusted.replace("UCONF", 1d);
		minMeasureValuesAdjusted.replace("RA", 0d);
		maxMeasureValuesAdjusted.replace("RA", 1d);
	}

	/**
	 * Get the adjusted minimum value for a measure
	 * 
	 * @return the ajusted minimum numeric value observed for the given measure (or
	 *         null if none)
	 */
	public Double getMinForMeasure(String measure) {
		return minMeasureValuesAdjusted.get(measure);
	}

	/**
	 * Get the adjusted maximum value for a measure
	 * 
	 * @return the adjusted maximum numeric value observed for the given measure (or
	 *         null if none)
	 */
	public Double getMaxForMeasure(String measure) {
		return maxMeasureValuesAdjusted.get(measure);
	}

	/**
	 * Get the original minimum value for a measure
	 * 
	 * @return the minimum numeric value observed for the given measure (or null if
	 *         none)
	 */
	public Double getMinForMeasureOriginal(String measure) {
		return minMeasureValuesOriginal.get(measure);
	}

	/**
	 * Get the original maximum value for a measure
	 * 
	 * @return the maximum numeric value observed for the given measure (or null if
	 *         none)
	 */
	public Double getMaxForMeasureOriginal(String measure) {
		return maxMeasureValuesOriginal.get(measure);
	}

	/**
	 * @return the full mapping of measure → min value
	 */
	public Map<String, Double> getAllMinValues() {
		return minMeasureValuesAdjusted;
	}

	/**
	 * @return the full mapping of measure → max value
	 */
	public Map<String, Double> getAllMaxValues() {
		return maxMeasureValuesAdjusted;
	}

	/**
	 * Iterates over all itemsets and, for each measure in 'availableMeasures',
	 * parses its value as a double (if possible) and updates min/max maps.
	 */
	abstract protected void computeMinMaxForMeasures(Set<String> availableMeasures);

	/**
	 * Reads the SPMF file and parses sequences and support values.
	 * 
	 * @param filePath input file path
	 * @throws IOException if file I/O fails
	 */
	abstract protected void readFileHelper(String filePath) throws IOException;

}