package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.ToolTipManager;

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
 * 
 * Do not remove copyright and license information
 */
/**
 * A dialog showing the distribution of the values of a measure as a histogram,
 * with mean / median markers, hover tooltips per bin, a statistics strip and
 * PNG export.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class MeasureHistogramDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private static final Color MEAN_COLOR = new Color(219, 68, 55);
	private static final Color MEDIAN_COLOR = new Color(15, 157, 88);

	private final PatternsPanel patternsPanel;
	private Map<String, List<Double>> measureValuesMap;

	private HistogramPanel histogramPanel;
	private JComboBox<String> measureComboBox;
	private JSpinner binSpinner;
	private JCheckBox markersCheckBox;
	private JLabel statsLabel;
	private boolean updatingCombo;

	/**
	 * Constructor
	 *
	 * @param parent           the parent frame
	 * @param patternsPanel    the patterns panel (used to refresh the values)
	 * @param measureValuesMap measure name → values
	 */
	public MeasureHistogramDialog(JFrame parent, PatternsPanel patternsPanel,
			Map<String, List<Double>> measureValuesMap) {
		super(parent, "Measure Distribution Histogram", false);
		this.patternsPanel = patternsPanel;
		this.measureValuesMap = measureValuesMap;
		initializeUI();
		pack();
		setMinimumSize(new Dimension(560, 420));
		setLocationRelativeTo(parent);
	}

	private void initializeUI() {
		setLayout(new BorderLayout());
		getContentPane().setBackground(ViewerStyle.CARD_BG);

		// --- Toolbar ---
		JPanel toolbar = new JPanel(new BorderLayout());
		toolbar.setBackground(ViewerStyle.CARD_BG);
		toolbar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ViewerStyle.CARD_BORDER),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));

		JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		leftTools.setOpaque(false);
		leftTools.add(toolbarLabel("Measure"));
		measureComboBox = new JComboBox<>();
		measureComboBox.setFont(ViewerStyle.measureValueFont());
		for (String measure : measureValuesMap.keySet()) {
			measureComboBox.addItem(measure);
		}
		measureComboBox.addActionListener(e -> {
			if (!updatingCombo) {
				updateHistogram();
			}
		});
		leftTools.add(measureComboBox);
		leftTools.add(Box(14));
		leftTools.add(toolbarLabel("Bins"));
		binSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));
		binSpinner.setFont(ViewerStyle.measureValueFont());
		binSpinner.setPreferredSize(new Dimension(64, binSpinner.getPreferredSize().height));
		binSpinner.addChangeListener(e -> updateHistogram());
		leftTools.add(binSpinner);
		leftTools.add(Box(14));
		markersCheckBox = new JCheckBox("Mean / median", true);
		markersCheckBox.setFont(ViewerStyle.measureValueFont());
		markersCheckBox.setOpaque(false);
		markersCheckBox.setFocusPainted(false);
		markersCheckBox.addActionListener(e -> histogramPanel.repaint());
		leftTools.add(markersCheckBox);

		JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		rightTools.setOpaque(false);
		JButton refreshButton = new JButton("Refresh");
		refreshButton.setToolTipText("Reload the values from the viewer (after filtering or sorting)");
		ViewerStyle.styleButton(refreshButton);
		refreshButton.addActionListener(e -> refreshData());
		JButton exportButton = new JButton("Save as PNG\u2026");
		ViewerStyle.styleButton(exportButton);
		exportButton.addActionListener(e -> exportAsPng());
		rightTools.add(refreshButton);
		rightTools.add(exportButton);

		toolbar.add(leftTools, BorderLayout.WEST);
		toolbar.add(rightTools, BorderLayout.EAST);
		add(toolbar, BorderLayout.NORTH);

		// --- Chart ---
		histogramPanel = new HistogramPanel();
		histogramPanel.setPreferredSize(new Dimension(680, 420));
		add(histogramPanel, BorderLayout.CENTER);

		// --- Statistics strip ---
		JPanel statsPanel = new JPanel(new BorderLayout());
		statsPanel.setBackground(ViewerStyle.SIDEBAR_BG);
		statsPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, ViewerStyle.CARD_BORDER),
				BorderFactory.createEmptyBorder(5, 12, 5, 12)));
		statsLabel = new JLabel(" ");
		statsLabel.setFont(ViewerStyle.measureValueFont());
		statsLabel.setForeground(ViewerStyle.TEXT_PRIMARY);
		statsPanel.add(statsLabel, BorderLayout.CENTER);
		add(statsPanel, BorderLayout.SOUTH);

		if (measureComboBox.getItemCount() > 0) {
			measureComboBox.setSelectedIndex(0);
		}
		updateHistogram();
	}

	private static JLabel toolbarLabel(String text) {
		JLabel l = new JLabel(text);
		l.setFont(ViewerStyle.measureNameFont());
		l.setForeground(ViewerStyle.TEXT_SECONDARY);
		return l;
	}

	private static java.awt.Component Box(int w) {
		return javax.swing.Box.createHorizontalStrut(w);
	}

	/**
	 * Recomputes the bins for the selected measure and repaints.
	 */
	private void updateHistogram() {
		String measure = (String) measureComboBox.getSelectedItem();
		List<Double> values = (measure == null) ? null : measureValuesMap.get(measure);
		DescriptiveStats stats = DescriptiveStats.of(values);
		if (stats == null) {
			histogramPanel.setData(null, 0, 0, measure, null);
			statsLabel.setText("No numeric values for this measure");
			histogramPanel.repaint();
			return;
		}
		int bins = (Integer) binSpinner.getValue();
		int[] counts;
		if (stats.max <= stats.min) {
			counts = new int[] { stats.count };
		} else {
			counts = calculateBinCounts(values, stats.min, stats.max, bins);
		}
		histogramPanel.setData(counts, stats.min, stats.max, measure, stats);
		statsLabel.setText("<html><b>n</b> = " + stats.count + " &nbsp;\u00B7&nbsp; <b>min</b> "
				+ ViewerStyle.formatNumber(stats.min) + " &nbsp;\u00B7&nbsp; <b>mean</b> "
				+ ViewerStyle.formatNumber(stats.mean) + " &nbsp;\u00B7&nbsp; <b>median</b> "
				+ ViewerStyle.formatNumber(stats.median) + " &nbsp;\u00B7&nbsp; <b>max</b> "
				+ ViewerStyle.formatNumber(stats.max) + " &nbsp;\u00B7&nbsp; <b>std dev</b> "
				+ ViewerStyle.formatNumber(stats.stdDev) + "</html>");
		histogramPanel.repaint();
	}

	/**
	 * Counts the values falling in each of numBins equal-width bins over
	 * [min, max]. The last bin is closed on the right.
	 */
	static int[] calculateBinCounts(List<Double> values, double min, double max, int numBins) {
		int[] counts = new int[Math.max(1, numBins)];
		double binWidth = (max - min) / counts.length;
		if (binWidth <= 0) {
			counts[0] = values.size();
			return counts;
		}
		for (Double value : values) {
			if (value == null || value.isNaN()) {
				continue;
			}
			int idx = (int) ((value - min) / binWidth);
			idx = Math.max(0, Math.min(counts.length - 1, idx));
			counts[idx]++;
		}
		return counts;
	}

	/**
	 * Reloads the values from the patterns panel (e.g. after the user filtered
	 * the patterns) and keeps the selected measure if it still exists.
	 */
	private void refreshData() {
		try {
			Map<String, List<Double>> fresh = MeasureValuesExtractor.extractMeasureValues(patternsPanel);
			if (fresh != null) {
				measureValuesMap = fresh;
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Could not reload the values:\n" + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		String selected = (String) measureComboBox.getSelectedItem();
		updatingCombo = true;
		try {
			measureComboBox.removeAllItems();
			for (String measure : measureValuesMap.keySet()) {
				measureComboBox.addItem(measure);
			}
			if (selected != null && measureValuesMap.containsKey(selected)) {
				measureComboBox.setSelectedItem(selected);
			} else if (measureComboBox.getItemCount() > 0) {
				measureComboBox.setSelectedIndex(0);
			}
		} finally {
			updatingCombo = false;
		}
		updateHistogram();
	}

	/**
	 * Saves the chart as a PNG image.
	 */
	private void exportAsPng() {
		String measure = (String) measureComboBox.getSelectedItem();
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save histogram as PNG");
		chooser.setSelectedFile(new File((measure == null ? "measure" : measure) + "_histogram.png"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".png")) {
			file = new File(file.getParentFile(), file.getName() + ".png");
		}
		try {
			int w = Math.max(1, histogramPanel.getWidth());
			int h = Math.max(1, histogramPanel.getHeight());
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = img.createGraphics();
			try {
				histogramPanel.printAll(g2);
			} finally {
				g2.dispose();
			}
			ImageIO.write(img, "png", file);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Could not save the image:\n" + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// ------------------------------------------------------------------
	// Chart
	// ------------------------------------------------------------------

	/**
	 * The panel drawing the histogram.
	 */
	private class HistogramPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		private static final int LEFT = 62;
		private static final int RIGHT = 20;
		private static final int TOP = 36;
		private static final int BOTTOM = 56;

		private int[] binCounts;
		private double minValue;
		private double maxValue;
		private String measureName;
		private DescriptiveStats stats;
		private int hoveredBin = -1;

		// Geometry of the last paint, used for hit testing
		private int chartLeft;
		private int chartTop;
		private int chartWidth;
		private int chartHeight;

		HistogramPanel() {
			setBackground(ViewerStyle.CARD_BG);
			setOpaque(true);
			ToolTipManager.sharedInstance().registerComponent(this);
			addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					int b = binAt(e.getX(), e.getY());
					if (b != hoveredBin) {
						hoveredBin = b;
						repaint();
					}
				}
			});
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(MouseEvent e) {
					if (hoveredBin != -1) {
						hoveredBin = -1;
						repaint();
					}
				}
			});
		}

		void setData(int[] binCounts, double min, double max, String measureName, DescriptiveStats stats) {
			this.binCounts = binCounts;
			this.minValue = min;
			this.maxValue = max;
			this.measureName = measureName;
			this.stats = stats;
			this.hoveredBin = -1;
		}

		private int binAt(int mx, int my) {
			if (binCounts == null || chartWidth <= 0) {
				return -1;
			}
			if (mx < chartLeft || mx >= chartLeft + chartWidth || my < chartTop || my > chartTop + chartHeight) {
				return -1;
			}
			int b = (int) ((mx - chartLeft) / ((double) chartWidth / binCounts.length));
			return Math.min(b, binCounts.length - 1);
		}

		@Override
		public String getToolTipText(MouseEvent e) {
			int b = binAt(e.getX(), e.getY());
			if (b < 0) {
				return null;
			}
			int n = binCounts.length;
			double bw = (maxValue - minValue) / n;
			double a = minValue + b * bw;
			double z = (b == n - 1) ? maxValue : a + bw;
			int total = stats == null ? 0 : stats.count;
			double pct = total > 0 ? 100.0 * binCounts[b] / total : 0;
			String range = (maxValue <= minValue) ? ViewerStyle.formatNumber(minValue)
					: "[" + ViewerStyle.formatNumber(a) + ", " + ViewerStyle.formatNumber(z)
							+ (b == n - 1 ? "]" : ")");
			return String.format("<html><b>%s</b><br>%d pattern%s (%.1f%%)</html>", range, binCounts[b],
					binCounts[b] != 1 ? "s" : "", pct);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				ViewerStyle.applyRenderingHints(g2);
				int W = getWidth();
				int H = getHeight();

				if (binCounts == null || binCounts.length == 0) {
					g2.setFont(ViewerStyle.baseFont().deriveFont(Font.ITALIC, 14f));
					g2.setColor(ViewerStyle.TEXT_SECONDARY);
					String msg = "No numeric values to display";
					FontMetrics fm = g2.getFontMetrics();
					g2.drawString(msg, (W - fm.stringWidth(msg)) / 2, H / 2);
					return;
				}

				chartLeft = LEFT;
				chartTop = TOP;
				chartWidth = W - LEFT - RIGHT;
				chartHeight = H - TOP - BOTTOM;
				if (chartWidth <= 10 || chartHeight <= 10) {
					return;
				}
				int n = binCounts.length;
				int maxCount = 0;
				for (int c : binCounts) {
					maxCount = Math.max(maxCount, c);
				}

				// Title
				g2.setFont(ViewerStyle.sectionTitleFont());
				g2.setColor(ViewerStyle.TEXT_PRIMARY);
				g2.drawString("Distribution of " + measureName, LEFT, 22);
				g2.setFont(ViewerStyle.smallFont());
				g2.setColor(ViewerStyle.TEXT_SECONDARY);
				String nText = (stats == null ? 0 : stats.count) + " values \u00B7 " + n + (n == 1 ? " bin" : " bins");
				g2.drawString(nText, W - RIGHT - g2.getFontMetrics().stringWidth(nText), 22);

				// Y scale (nice integer steps) + grid
				double step = Math.max(1, ViewerStyle.niceStep(Math.max(1, maxCount), 5));
				double yMax = Math.max(step, Math.ceil(maxCount / step) * step);
				FontMetrics sfm = g2.getFontMetrics(ViewerStyle.smallFont());
				g2.setFont(ViewerStyle.smallFont());
				for (double v = 0; v <= yMax + 1e-9; v += step) {
					int y = chartTop + chartHeight - (int) Math.round(v / yMax * chartHeight);
					if (v > 0) {
						g2.setColor(ViewerStyle.GRID_LINE);
						g2.drawLine(chartLeft, y, chartLeft + chartWidth, y);
					}
					String label = ViewerStyle.formatNumber(v);
					g2.setColor(ViewerStyle.TEXT_SECONDARY);
					g2.drawString(label, chartLeft - 8 - sfm.stringWidth(label), y + sfm.getAscent() / 2 - 1);
				}

				// Axes
				g2.setColor(ViewerStyle.AXIS);
				g2.drawLine(chartLeft, chartTop, chartLeft, chartTop + chartHeight);
				g2.drawLine(chartLeft, chartTop + chartHeight, chartLeft + chartWidth, chartTop + chartHeight);

				// Bars
				double binPx = (double) chartWidth / n;
				int gap = binPx > 8 ? 2 : (binPx > 3 ? 1 : 0);
				int baseY = chartTop + chartHeight;
				for (int i = 0; i < n; i++) {
					int x0 = chartLeft + (int) Math.round(i * binPx);
					int x1 = chartLeft + (int) Math.round((i + 1) * binPx);
					int bx = x0 + gap / 2;
					int bw = Math.max(1, x1 - x0 - gap);
					boolean hovered = i == hoveredBin;
					if (hovered) {
						g2.setColor(ViewerStyle.ACCENT_SOFT);
						g2.fillRect(x0, chartTop, Math.max(1, x1 - x0), chartHeight);
					}
					int barH = (int) Math.round(binCounts[i] / yMax * chartHeight);
					if (barH > 0) {
						int by = baseY - barH;
						g2.setColor(hovered ? ViewerStyle.ACCENT_DARK : ViewerStyle.ACCENT);
						int r = Math.min(4, bw / 2);
						if (barH > r && r > 0) {
							g2.fillRoundRect(bx, by, bw, barH, 2 * r, 2 * r);
							g2.fillRect(bx, by + r, bw, barH - r);
						} else {
							g2.fillRect(bx, by, bw, barH);
						}
						String cs = String.valueOf(binCounts[i]);
						int cw = sfm.stringWidth(cs);
						if ((cw <= bw + 2 || hovered) && by - 4 - sfm.getAscent() >= chartTop) {
							g2.setColor(ViewerStyle.TEXT_PRIMARY);
							g2.drawString(cs, bx + (bw - cw) / 2, by - 4);
						}
					}
				}

				// X labels at bin boundaries, spaced so they never overlap
				int labelY = baseY + 6 + sfm.getAscent();
				g2.setFont(ViewerStyle.smallFont());
				if (maxValue <= minValue) {
					String lab = ViewerStyle.formatNumber(minValue);
					g2.setColor(ViewerStyle.TEXT_SECONDARY);
					g2.drawString(lab, chartLeft + (chartWidth - sfm.stringWidth(lab)) / 2, labelY);
				} else {
					double vw = (maxValue - minValue) / n;
					int maxLabelW = 0;
					String[] labels = new String[n + 1];
					for (int i = 0; i <= n; i++) {
						labels[i] = ViewerStyle.formatNumber(i == n ? maxValue : minValue + i * vw);
						maxLabelW = Math.max(maxLabelW, sfm.stringWidth(labels[i]));
					}
					int every = Math.max(1, (int) Math.ceil((maxLabelW + 12) / binPx));
					int lastRight = Integer.MIN_VALUE;
					for (int i = 0; i <= n; i++) {
						if (i % every != 0 && i != n) {
							continue;
						}
						int x = chartLeft + (int) Math.round(i * binPx);
						int lw = sfm.stringWidth(labels[i]);
						int lx = Math.max(2, Math.min(W - lw - 2, x - lw / 2));
						if (lx < lastRight + 6) {
							continue;
						}
						g2.setColor(ViewerStyle.AXIS);
						g2.drawLine(x, baseY, x, baseY + 4);
						g2.setColor(ViewerStyle.TEXT_SECONDARY);
						g2.drawString(labels[i], lx, labelY);
						lastRight = lx + lw;
					}
				}

				// Mean / median markers
				if (markersCheckBox.isSelected() && stats != null && maxValue > minValue) {
					paintMarker(g2, stats.mean, MEAN_COLOR, "mean " + ViewerStyle.formatNumber(stats.mean), 0,
							new float[] { 6f, 4f });
					paintMarker(g2, stats.median, MEDIAN_COLOR, "median " + ViewerStyle.formatNumber(stats.median),
							1, new float[] { 2f, 3f });
				}

				// Axis titles
				g2.setFont(ViewerStyle.measureNameFont());
				g2.setColor(ViewerStyle.TEXT_SECONDARY);
				FontMetrics tfm = g2.getFontMetrics();
				String xTitle = measureName;
				g2.drawString(xTitle, chartLeft + (chartWidth - tfm.stringWidth(xTitle)) / 2, H - 10);
				String yTitle = "Number of patterns";
				AffineTransform old = g2.getTransform();
				g2.rotate(-Math.PI / 2);
				g2.drawString(yTitle, -(chartTop + (chartHeight + tfm.stringWidth(yTitle)) / 2), 16);
				g2.setTransform(old);
			} finally {
				g2.dispose();
			}
		}

		private void paintMarker(Graphics2D g2, double value, Color color, String label, int slot,
				float[] dash) {
			int x = chartLeft + (int) Math.round((value - minValue) / (maxValue - minValue) * chartWidth);
			Stroke old = g2.getStroke();
			g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
			g2.setColor(color);
			g2.drawLine(x, chartTop, x, chartTop + chartHeight);
			g2.setStroke(old);

			g2.setFont(ViewerStyle.smallFont().deriveFont(Font.BOLD));
			FontMetrics fm = g2.getFontMetrics();
			int lw = fm.stringWidth(label) + 8;
			int lh = fm.getHeight() + 2;
			int ly = chartTop + 4 + slot * (lh + 3);
			int lx = (x + 6 + lw <= chartLeft + chartWidth) ? x + 6 : x - 6 - lw;
			g2.setColor(ViewerStyle.withAlpha(Color.WHITE, 225));
			g2.fillRoundRect(lx, ly, lw, lh, 6, 6);
			g2.setColor(ViewerStyle.withAlpha(color, 140));
			g2.drawRoundRect(lx, ly, lw - 1, lh - 1, 6, 6);
			g2.setColor(color);
			g2.drawString(label, lx + 4, ly + 1 + fm.getAscent());
		}
	}
}