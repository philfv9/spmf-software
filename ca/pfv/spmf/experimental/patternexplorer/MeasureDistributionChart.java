package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * A histogram of a numeric measure over the current result. The measure is
 * chosen in a combo box (no measure is privileged). The user can click a bin
 * or drag over the histogram to restrict the query to a range of values. When
 * a range restriction exists for the measure, the histogram shows the values
 * <i>before</i> that restriction and highlights the selected range.
 * 
 * @author Philippe Fournier-Viger
 */
public class MeasureDistributionChart extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Number of histogram bins */
	private static final int BINS = 40;

	private final ExplorerController controller;
	private final PatternCollection collection;
	private final JComboBox<String> measureCombo;
	private final JButton clearButton;
	private final JLabel statsLabel;
	private final HistogramCanvas canvas;

	private QueryResult lastResult;

	/** Histogram state */
	private int[] histogram = new int[0];
	private double histMin = Double.NaN;
	private double histMax = Double.NaN;
	private int histTotal = 0;
	private int measureIdx = -1;
	private String measureName = null;
	private double rangeLo = Double.NaN;
	private double rangeHi = Double.NaN;

	/**
	 * Constructor
	 * 
	 * @param controller the controller
	 * @param collection the collection
	 */
	public MeasureDistributionChart(ExplorerController controller, PatternCollection collection) {
		super(new BorderLayout(0, 2));
		this.controller = controller;
		this.collection = collection;
		setBorder(ExplorerStyle.titledBorder("Measure distribution"));
		setBackground(Color.WHITE);
		setPreferredSize(new Dimension(300, 200));
		setMinimumSize(new Dimension(180, 120));

		List<String> numeric = collection.getNumericMeasureNames();
		measureCombo = new JComboBox<>(numeric.toArray(new String[0]));
		measureCombo.setFont(ExplorerStyle.FONT_SMALL);
		measureCombo.setToolTipText("Numeric measure shown in the histogram");
		measureCombo.addActionListener(e -> recompute());

		clearButton = new JButton("\u00D7 range");
		ExplorerStyle.makeCompact(clearButton);
		clearButton.setFont(ExplorerStyle.FONT_SMALL);
		clearButton.setToolTipText("Remove the range restriction on this measure");
		clearButton.setVisible(false);
		clearButton.addActionListener(e -> {
			if (measureName != null) {
				controller.applyQuery(controller.getCurrentQuery().clearMeasureRange(measureName));
			}
		});

		JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		north.setOpaque(false);
		north.add(ExplorerStyle.mutedLabel("Measure:"));
		north.add(measureCombo);
		north.add(clearButton);
		add(north, BorderLayout.NORTH);

		canvas = new HistogramCanvas();
		add(canvas, BorderLayout.CENTER);

		statsLabel = ExplorerStyle.mutedLabel(" ");
		add(statsLabel, BorderLayout.SOUTH);

		if (numeric.isEmpty()) {
			measureCombo.setEnabled(false);
			statsLabel.setText("No numeric measure in this file");
		}
	}

	/** @return the measure currently displayed (or null) */
	public String getSelectedMeasure() {
		return measureName;
	}

	/**
	 * Select a measure to display.
	 * 
	 * @param measure a numeric measure name
	 */
	public void selectMeasure(String measure) {
		if (measure != null && collection.isNumericMeasure(measure)) {
			measureCombo.setSelectedItem(measure);
		}
	}

	/**
	 * Update the chart from a query result.
	 * 
	 * @param result the current result
	 */
	public void update(QueryResult result) {
		lastResult = result;
		recompute();
	}

	private void recompute() {
		measureName = (String) measureCombo.getSelectedItem();
		measureIdx = measureName == null ? -1 : collection.getNumericMeasureIndex(measureName);
		if (lastResult == null || measureIdx < 0) {
			histogram = new int[0];
			histTotal = 0;
			clearButton.setVisible(false);
			canvas.repaint();
			return;
		}
		ExplorerQuery query = lastResult.getQuery();
		double[] range = query.getMeasureRanges().get(measureName);
		rangeLo = range == null ? Double.NaN : range[0];
		rangeHi = range == null ? Double.NaN : range[1];
		QueryResult base = range == null ? lastResult : collection.evaluate(query.clearMeasureRange(measureName));
		MeasureStats stats = base.getMeasureStats(measureIdx);
		if (!stats.hasValues()) {
			histogram = new int[0];
			histTotal = 0;
			statsLabel.setText("No value for " + measureName + " in the matching patterns");
		} else {
			histogram = base.computeHistogram(measureIdx, BINS);
			histMin = stats.getMin();
			histMax = stats.getMax();
			histTotal = stats.getCount();
			statsLabel.setText(ExplorerFormat.count(histTotal, "value") + "  \u00B7  min " + ExplorerFormat.number(histMin)
					+ "  \u00B7  mean " + ExplorerFormat.number(stats.getMean()) + "  \u00B7  max "
					+ ExplorerFormat.number(histMax) + "  \u00B7  drag to filter");
		}
		clearButton.setVisible(range != null);
		canvas.hovered = -1;
		canvas.repaint();
		revalidate();
	}

	// ------------------------------------------------------------------
	// Canvas
	// ------------------------------------------------------------------

	/** The drawing area of the histogram */
	private final class HistogramCanvas extends JPanel {
		private static final long serialVersionUID = 1L;
		private static final int PAD = 6;
		private static final int LABEL_AREA = 14;

		private int hovered = -1;
		private int dragX0 = -1;
		private int dragX1 = -1;

		HistogramCanvas() {
			setOpaque(false);
			ToolTipManager.sharedInstance().registerComponent(this);
			MouseAdapter mouse = new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					int b = binAt(e.getX());
					if (b != hovered) {
						hovered = b;
						setCursor(b >= 0 ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
								: Cursor.getDefaultCursor());
						repaint();
					}
				}

				@Override
				public void mouseExited(MouseEvent e) {
					hovered = -1;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e) {
					if (SwingUtilities.isLeftMouseButton(e) && histTotal > 0) {
						dragX0 = clampX(e.getX());
						dragX1 = dragX0;
						repaint();
					}
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (dragX0 >= 0) {
						dragX1 = clampX(e.getX());
						repaint();
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (dragX0 >= 0 && SwingUtilities.isLeftMouseButton(e)) {
						int a = Math.min(dragX0, dragX1);
						int b = Math.max(dragX0, dragX1);
						double lo;
						double hi;
						if (b - a < 3) {
							// A click: select the bin under the cursor
							int bin = binAt(a);
							if (bin < 0) {
								bin = binAt(b);
							}
							if (bin < 0) {
								dragX0 = -1;
								dragX1 = -1;
								repaint();
								return;
							}
							lo = binLow(bin);
							hi = binHigh(bin);
						} else {
							lo = valueAt(a);
							hi = valueAt(b);
						}
						dragX0 = -1;
						dragX1 = -1;
						if (measureName != null) {
							controller.setMeasureRange(measureName, lo, hi);
						}
					}
					dragX0 = -1;
					dragX1 = -1;
					repaint();
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		private int plotLeft() {
			return PAD;
		}

		private int plotRight() {
			return getWidth() - PAD;
		}

		private int plotWidth() {
			return Math.max(1, plotRight() - plotLeft());
		}

		private int clampX(int x) {
			return Math.max(plotLeft(), Math.min(plotRight(), x));
		}

		private double valueAt(int x) {
			if (histMax <= histMin) {
				return histMin;
			}
			double f = (double) (x - plotLeft()) / plotWidth();
			f = Math.max(0, Math.min(1, f));
			return histMin + f * (histMax - histMin);
		}

		private int xOf(double value) {
			if (histMax <= histMin) {
				return value <= histMin ? plotLeft() : plotRight();
			}
			double f = (value - histMin) / (histMax - histMin);
			f = Math.max(0, Math.min(1, f));
			return plotLeft() + (int) Math.round(f * plotWidth());
		}

		private double binLow(int bin) {
			if (histMax <= histMin) {
				return histMin;
			}
			return histMin + (histMax - histMin) * bin / histogram.length;
		}

		private double binHigh(int bin) {
			if (histMax <= histMin) {
				return histMax;
			}
			return histMin + (histMax - histMin) * (bin + 1) / histogram.length;
		}

		private int binAt(int x) {
			if (histogram.length == 0 || histTotal == 0 || x < plotLeft() || x > plotRight()) {
				return -1;
			}
			int bin = (int) ((double) (x - plotLeft()) / plotWidth() * histogram.length);
			return Math.max(0, Math.min(histogram.length - 1, bin));
		}

		@Override
		public String getToolTipText(MouseEvent e) {
			int bin = binAt(e.getX());
			if (bin < 0) {
				return null;
			}
			int count = histogram[bin];
			double pct = histTotal > 0 ? (double) count / histTotal : 0;
			return "<html><b>" + ExplorerHtml.escape(measureName) + " \u2208 [" + ExplorerFormat.number(binLow(bin))
					+ ", " + ExplorerFormat.number(binHigh(bin)) + "]</b><br>"
					+ ExplorerFormat.count(count, "pattern") + " (" + ExplorerFormat.percent(pct)
					+ ")<br><i>click to keep only this range; drag to select a range</i></html>";
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int left = plotLeft();
			int right = plotRight();
			int top = PAD;
			int bottom = getHeight() - LABEL_AREA;
			int plotH = Math.max(1, bottom - top);

			if (histogram.length == 0 || histTotal == 0) {
				g2.setFont(ExplorerStyle.FONT_ITALIC);
				g2.setColor(ExplorerStyle.MUTED);
				String msg = measureIdx < 0 ? "No numeric measure" : "No values";
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(msg, left + (plotWidth() - fm.stringWidth(msg)) / 2, (top + bottom) / 2);
				g2.dispose();
				return;
			}

			int maxBin = 0;
			for (int c : histogram) {
				if (c > maxBin) {
					maxBin = c;
				}
			}
			Color color = ExplorerStyle.measureColor(measureIdx);
			boolean hasRange = !Double.isNaN(rangeLo) || !Double.isNaN(rangeHi);
			double lo = Double.isNaN(rangeLo) ? Double.NEGATIVE_INFINITY : rangeLo;
			double hi = Double.isNaN(rangeHi) ? Double.POSITIVE_INFINITY : rangeHi;
			double binW = (double) plotWidth() / histogram.length;

			// Bars
			for (int i = 0; i < histogram.length; i++) {
				int x0 = left + (int) Math.round(i * binW);
				int x1 = left + (int) Math.round((i + 1) * binW);
				int count = histogram[i];
				int bh = maxBin > 0 && count > 0 ? Math.max(2, (int) Math.round((double) count / maxBin * plotH)) : 0;
				boolean inRange = !hasRange || (binHigh(i) >= lo && binLow(i) <= hi);
				Color c = inRange ? color : ExplorerStyle.withAlpha(color, 60);
				if (i == hovered && dragX0 < 0) {
					c = c.brighter();
				}
				g2.setColor(c);
				g2.fillRect(x0, bottom - bh, Math.max(1, x1 - x0 - 1), bh);
			}

			// Selected range
			if (hasRange) {
				int xa = xOf(Double.isNaN(rangeLo) ? histMin : rangeLo);
				int xb = xOf(Double.isNaN(rangeHi) ? histMax : rangeHi);
				if (xb <= xa) {
					xb = xa + 2;
				}
				g2.setColor(ExplorerStyle.withAlpha(ExplorerStyle.SELECTED, 45));
				g2.fillRect(xa, top, xb - xa, plotH);
				g2.setColor(ExplorerStyle.SELECTED);
				g2.drawLine(xa, top, xa, bottom);
				g2.drawLine(xb, top, xb, bottom);
			}

			// Drag preview
			if (dragX0 >= 0) {
				int a = Math.min(dragX0, dragX1);
				int b = Math.max(dragX0, dragX1);
				g2.setColor(ExplorerStyle.withAlpha(ExplorerStyle.ACCENT, 50));
				g2.fillRect(a, top, Math.max(1, b - a), plotH);
				g2.setColor(ExplorerStyle.ACCENT);
				g2.drawRect(a, top, Math.max(1, b - a), plotH);
				g2.setFont(ExplorerStyle.FONT_SMALL_BOLD);
				String txt = "[" + ExplorerFormat.number(valueAt(a)) + ", " + ExplorerFormat.number(valueAt(b)) + "]";
				FontMetrics fm = g2.getFontMetrics();
				int tx = Math.max(left, Math.min(right - fm.stringWidth(txt), (a + b) / 2 - fm.stringWidth(txt) / 2));
				g2.setColor(Color.WHITE);
				g2.fillRect(tx - 2, top, fm.stringWidth(txt) + 4, fm.getHeight());
				g2.setColor(ExplorerStyle.ACCENT.darker());
				g2.drawString(txt, tx, top + fm.getAscent());
			}

			// Axis and labels
			g2.setColor(ExplorerStyle.AXIS);
			g2.drawLine(left, bottom, right, bottom);
			g2.setFont(ExplorerStyle.FONT_SMALL);
			g2.setColor(ExplorerStyle.MUTED);
			FontMetrics fm = g2.getFontMetrics();
			String minLabel = ExplorerFormat.number(histMin);
			String maxLabel = ExplorerFormat.number(histMax);
			g2.drawString(minLabel, left, bottom + fm.getAscent() + 1);
			g2.drawString(maxLabel, right - fm.stringWidth(maxLabel), bottom + fm.getAscent() + 1);
			if (histMax > histMin) {
				String midLabel = ExplorerFormat.number((histMin + histMax) / 2);
				int mx = (left + right) / 2 - fm.stringWidth(midLabel) / 2;
				if (mx > left + fm.stringWidth(minLabel) + 8
						&& mx + fm.stringWidth(midLabel) < right - fm.stringWidth(maxLabel) - 8) {
					g2.drawString(midLabel, mx, bottom + fm.getAscent() + 1);
				}
			}
			g2.dispose();
		}
	}
}