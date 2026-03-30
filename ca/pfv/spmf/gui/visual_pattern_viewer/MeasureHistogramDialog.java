package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
 * A dialog window that displays a histogram showing the distribution of values
 * for a selected measure across all patterns.
 * 
 * @author Philippe Fournier-Viger 2025
 */
public class MeasureHistogramDialog extends JDialog {

	/** Serial UID */
	private static final long serialVersionUID = 1L;

	/** The panel that draws the histogram */
	private HistogramPanel histogramPanel;

	/** Combo box for selecting the measure */
	private JComboBox<String> measureComboBox;

	/** Spinner for selecting the number of bins */
	private JSpinner binSpinner;

	/** The patterns panel containing the data */
	private PatternsPanel patternsPanel;

	/** Map of measure names to their values for all patterns */
	private Map<String, List<Double>> measureValuesMap;

	/** Current number of bins */
	private int numberOfBins = 10;

	/** Minimum value for current measure */
	private double currentMin;

	/** Maximum value for current measure */
	private double currentMax;

	/**
	 * Constructor
	 * 
	 * @param parent          the parent frame
	 * @param patternsPanel   the patterns panel containing measure data
	 * @param measureValuesMap map of measure names to their list of values
	 */
	public MeasureHistogramDialog(JFrame parent, PatternsPanel patternsPanel,
			Map<String, List<Double>> measureValuesMap) {
		super(parent, "Measure Distribution Histogram", false);
		this.patternsPanel = patternsPanel;
		this.measureValuesMap = measureValuesMap;

		initializeUI();
		pack();
		setLocationRelativeTo(parent);
		setMinimumSize(new Dimension(500, 400));
	}

	/**
	 * Initializes the user interface components
	 */
	private void initializeUI() {
		setLayout(new BorderLayout());

		// === Control Panel (Top) ===
		JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

		// Measure selection combo box
		controlPanel.add(new JLabel("Measure:"));
		measureComboBox = new JComboBox<>();
		for (String measure : measureValuesMap.keySet()) {
			measureComboBox.addItem(measure);
		}
		measureComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateHistogram();
			}
		});
		controlPanel.add(measureComboBox);

		// Number of bins spinner
		controlPanel.add(new JLabel("   Bins:"));
		binSpinner = new JSpinner(new SpinnerNumberModel(10, 2, 100, 1));
		binSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				numberOfBins = (Integer) binSpinner.getValue();
				updateHistogram();
			}
		});
		controlPanel.add(binSpinner);

		// Refresh button
		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateHistogram();
			}
		});
		controlPanel.add(refreshButton);

		add(controlPanel, BorderLayout.NORTH);

		// === Histogram Panel (Center) ===
		histogramPanel = new HistogramPanel();
		histogramPanel.setPreferredSize(new Dimension(600, 400));
		add(histogramPanel, BorderLayout.CENTER);

		// === Statistics Panel (Bottom) ===
		JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		add(statsPanel, BorderLayout.SOUTH);

		// Initialize with first measure if available
		if (measureComboBox.getItemCount() > 0) {
			measureComboBox.setSelectedIndex(0);
			updateHistogram();
		}
	}

	/**
	 * Updates the histogram based on current measure selection and bin count
	 */
	private void updateHistogram() {
		String selectedMeasure = (String) measureComboBox.getSelectedItem();
		if (selectedMeasure == null) {
			return;
		}

		List<Double> values = measureValuesMap.get(selectedMeasure);
		if (values == null || values.isEmpty()) {
			return;
		}

		// Get min and max from the patterns panel (adjusted values)
		Double min = patternsPanel.getMinForMeasureOriginal(selectedMeasure);
		Double max = patternsPanel.getMaxForMeasureOriginal(selectedMeasure);

		if (min == null || max == null) {
			// Calculate from values if not available
			min = Double.MAX_VALUE;
			max = Double.MIN_VALUE;
			for (Double v : values) {
				if (v < min) min = v;
				if (v > max) max = v;
			}
		}

		currentMin = min;
		currentMax = max;

		// Calculate bin counts
		int[] binCounts = calculateBinCounts(values, min, max, numberOfBins);

		// Update the histogram panel
		histogramPanel.setData(binCounts, min, max, selectedMeasure, values.size());
		histogramPanel.repaint();
	}

	/**
	 * Calculates the count of values in each bin
	 * 
	 * @param values      the list of values
	 * @param min         minimum value
	 * @param max         maximum value
	 * @param numBins     number of bins
	 * @return array of counts for each bin
	 */
	private int[] calculateBinCounts(List<Double> values, double min, double max, int numBins) {
		int[] counts = new int[numBins];
		double binWidth = (max - min) / numBins;

		// Handle edge case where min == max
		if (binWidth == 0) {
			counts[0] = values.size();
			return counts;
		}

		for (Double value : values) {
			int binIndex = (int) ((value - min) / binWidth);
			// Handle edge case where value == max
			if (binIndex >= numBins) {
				binIndex = numBins - 1;
			}
			if (binIndex < 0) {
				binIndex = 0;
			}
			counts[binIndex]++;
		}

		return counts;
	}

	/**
	 * Inner class that draws the histogram
	 */
	private class HistogramPanel extends JPanel {

		/** Serial UID */
		private static final long serialVersionUID = 1L;

		/** Bin counts */
		private int[] binCounts;

		/** Minimum value */
		private double minValue;

		/** Maximum value */
		private double maxValue;

		/** Measure name */
		private String measureName;

		/** Total count of values */
		private int totalCount;

		/** Margin for labels */
		private static final int LEFT_MARGIN = 60;
		private static final int RIGHT_MARGIN = 20;
		private static final int TOP_MARGIN = 40;
		private static final int BOTTOM_MARGIN = 60;

		/** Bar color */
		private static final Color BAR_COLOR = new Color(70, 130, 180);
		private static final Color BAR_BORDER_COLOR = new Color(30, 80, 130);

		/**
		 * Constructor
		 */
		public HistogramPanel() {
			setBackground(Color.WHITE);
		}

		/**
		 * Sets the data for the histogram
		 * 
		 * @param binCounts   array of counts for each bin
		 * @param min         minimum value
		 * @param max         maximum value
		 * @param measureName name of the measure
		 * @param totalCount  total number of values
		 */
		public void setData(int[] binCounts, double min, double max, String measureName, int totalCount) {
			this.binCounts = binCounts;
			this.minValue = min;
			this.maxValue = max;
			this.measureName = measureName;
			this.totalCount = totalCount;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			if (binCounts == null || binCounts.length == 0) {
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int width = getWidth();
			int height = getHeight();
			int chartWidth = width - LEFT_MARGIN - RIGHT_MARGIN;
			int chartHeight = height - TOP_MARGIN - BOTTOM_MARGIN;

			// Find max count for scaling
			int maxCount = 0;
			for (int count : binCounts) {
				if (count > maxCount) {
					maxCount = count;
				}
			}

			// Draw title
			g2.setColor(Color.BLACK);
			g2.setFont(new Font("SansSerif", Font.BOLD, 14));
			FontMetrics fm = g2.getFontMetrics();
			String title = "Distribution of " + measureName + " (n=" + totalCount + ")";
			int titleWidth = fm.stringWidth(title);
			g2.drawString(title, (width - titleWidth) / 2, 25);

			// Draw axes
			g2.setColor(Color.BLACK);
			g2.drawLine(LEFT_MARGIN, TOP_MARGIN, LEFT_MARGIN, height - BOTTOM_MARGIN);
			g2.drawLine(LEFT_MARGIN, height - BOTTOM_MARGIN, width - RIGHT_MARGIN, height - BOTTOM_MARGIN);

			// Draw bars
			double binWidth = (double) chartWidth / binCounts.length;
			double valueRange = maxValue - minValue;
			double valueBinWidth = valueRange / binCounts.length;

			g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
			fm = g2.getFontMetrics();

			for (int i = 0; i < binCounts.length; i++) {
				int barHeight = (maxCount > 0) ? (int) ((double) binCounts[i] / maxCount * chartHeight) : 0;
				int x = LEFT_MARGIN + (int) (i * binWidth);
				int y = height - BOTTOM_MARGIN - barHeight;

				// Draw bar
				g2.setColor(BAR_COLOR);
				g2.fillRect(x + 1, y, (int) binWidth - 2, barHeight);
				g2.setColor(BAR_BORDER_COLOR);
				g2.drawRect(x + 1, y, (int) binWidth - 2, barHeight);

				// Draw count on top of bar if non-zero
				if (binCounts[i] > 0) {
					g2.setColor(Color.BLACK);
					String countStr = String.valueOf(binCounts[i]);
					int countWidth = fm.stringWidth(countStr);
					g2.drawString(countStr, x + (int) (binWidth / 2) - countWidth / 2, y - 3);
				}

				// Draw x-axis labels (bin ranges)
				if (i == 0 || i == binCounts.length - 1 || binCounts.length <= 10 || i % (binCounts.length / 5) == 0) {
					double binStart = minValue + i * valueBinWidth;
					String label = String.format("%.2f", binStart);
					int labelWidth = fm.stringWidth(label);
					g2.setColor(Color.BLACK);
					g2.drawString(label, x + (int) (binWidth / 2) - labelWidth / 2, height - BOTTOM_MARGIN + 15);
				}
			}

			// Draw last x-axis label (max value)
			String maxLabel = String.format("%.2f", maxValue);
			g2.drawString(maxLabel, width - RIGHT_MARGIN - fm.stringWidth(maxLabel), height - BOTTOM_MARGIN + 15);

			// Draw y-axis labels
			g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
			int numYLabels = 5;
			for (int i = 0; i <= numYLabels; i++) {
				int count = (int) ((double) i / numYLabels * maxCount);
				int y = height - BOTTOM_MARGIN - (int) ((double) i / numYLabels * chartHeight);
				String label = String.valueOf(count);
				int labelWidth = fm.stringWidth(label);
				g2.drawString(label, LEFT_MARGIN - labelWidth - 5, y + 4);
				g2.setColor(Color.LIGHT_GRAY);
				g2.drawLine(LEFT_MARGIN, y, width - RIGHT_MARGIN, y);
				g2.setColor(Color.BLACK);
			}

			// Draw axis labels
			g2.setFont(new Font("SansSerif", Font.BOLD, 12));
			g2.drawString("Count", 10, TOP_MARGIN + chartHeight / 2);
			String xLabel = measureName + " Value";
			int xLabelWidth = g2.getFontMetrics().stringWidth(xLabel);
			g2.drawString(xLabel, LEFT_MARGIN + (chartWidth - xLabelWidth) / 2, height - 10);

			g2.dispose();
		}
	}
}