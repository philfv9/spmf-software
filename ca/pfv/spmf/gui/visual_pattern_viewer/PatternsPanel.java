package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import ca.pfv.spmf.gui.visual_pattern_viewer.rules.RulePanel;

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
 * An abstract class implementing a Swing panel for displaying a list of
 * patterns, used by the Visual Pattern Viewer
 * 
 * @author Philippe Fournier-Viger
 * @see VisualPatternViewer
 */
public abstract class PatternsPanel extends JPanel {

	/**
	 * serial UID
	 */
	private static final long serialVersionUID = 263741498271800378L;

	/** The layout mode **/
	protected LayoutMode layoutMode = LayoutMode.GRID;

	/**
	 * Constructor the selected layout mode
	 * 
	 * @param mode the layout mode
	 */
	public PatternsPanel(LayoutMode mode) {
		layoutMode = mode;

	}

	/**
	 * Clears and recreates a {@link RulePanel} for each rule.
	 */
	protected abstract void rebuildPanels();

	/**
	 * @return an ordered list of all sorting orders that are offered
	 */
	public abstract List<String> getListOfSortingOrders();

	/**
	 * Renders the entire panel to a PNG file.
	 *
	 * @param filename output filename (PNG format)
	 * @throws IOException if writing the image fails
	 */
	public void exportAsPNG(String filename) throws IOException {
		Dimension size = getPreferredSize();
		BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		paintAll(g2);
		g2.dispose();
		ImageIO.write(img, "png", new File(filename));
	}

	/**
	 * Sort the rules based on the the user's choice
	 * 
	 * @param choice the choice as a String
	 */
	public abstract void sortPatterns(String choice);

	/**
	 * Get the pattern count
	 * 
	 * @return the pattern count
	 */
	public abstract int getTotalPatternCount();

	/**
	 * Set the layout mode
	 * 
	 * @param mode a layout mode
	 */
	public void setLayoutMode(LayoutMode mode) {
		this.layoutMode = mode;
		rebuildPanels();
	}

	/**
	 * Get the layout mode
	 * 
	 * @return a layout mode
	 */
	public LayoutMode getLayoutMode() {
		return layoutMode;
	}

	public void setLayoutModeWithCallback(LayoutMode mode, Runnable callback) {
		setLayoutMode(mode);
		if (callback != null) {
			callback.run();
		}
	}

	/**
	 * Get the original minimum numeric value observed for the given measure
	 * 
	 * @return the original minimum numeric value observed for the given measure (or
	 *         null if none)
	 */
	abstract public Double getMinForMeasureOriginal(String measure);

	/**
	 * Get the original maximum numeric value observed for the given measure
	 * 
	 * @return the original maximum numeric value observed for the given measure (or
	 *         null if none)
	 */
	abstract public Double getMaxForMeasureOriginal(String measure);

	/**
	 * Get the list of available measures
	 * 
	 * @return a Set
	 */
	abstract public Set<String> getAllMeasures();

	/**
	 * Filters and displays patterns matching the given constraints.
	 * 
	 * @param searchString      a substring to search in pattern string
	 *                          representation.
	 * @param measureThresholds a map from measure name to minimum threshold value.
	 * @param operators         a map from measure name to operator name.
	 */
	abstract public void applySearchAndFilters(String searchString, Map<String, Double> measureThresholds,
			Map<String, String> operators);

	/**
	 * Get the number of visible patterns
	 * 
	 * @return the number of visible patterns (after filtering)
	 */
	abstract public int getNumberOfVisiblePatterns();

	/**
	 * Clear the filters and show all patterns
	 */
	abstract public void clearSearchAndFilters();

	/**
	 * Populates the provided map with measure values from all patterns. Each
	 * measure name maps to a list of its numeric values across all patterns.
	 * 
	 * @param measureValuesMap the map to populate with measure values
	 */
	abstract public void populateMeasureValues(Map<String, List<Double>> measureValuesMap);

	/**
	 * Returns the sizes (number of elements/items) of all currently visible
	 * patterns. This is used by the size distribution histogram.
	 * <p>
	 * For itemsets, the size is the number of items. For rules, it could be the
	 * total number of items in antecedent + consequent. For sequential patterns, it
	 * could be the number of itemsets in the sequence.
	 * <p>
	 * Subclasses should return sizes only for patterns that are currently visible
	 * (i.e., after filtering has been applied).
	 * 
	 * @return a list of integers, each representing the size of a visible pattern
	 */
	public abstract List<Integer> getVisiblePatternSizes();
}