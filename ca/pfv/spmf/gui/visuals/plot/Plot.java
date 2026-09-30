package ca.pfv.spmf.gui.visuals.plot;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/*
The MIT License (MIT)
http://yuriy-g.github.io/simple-java-plot/

Copyright (c) 2013 Yuriy Guskov

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/**
 * Simple, dependency-free 2D plot (lines, areas, scatter markers, columns and
 * bars) with a fluent option API.
 * <p>
 * Typical use:
 * <pre>
 * Plot plot = Plot.plot(Plot.plotOpts().title("Support").legend(LegendFormat.BOTTOM))
 *     .series("apriori", Plot.data().xy(x, y), Plot.seriesOpts().marker(Marker.CIRCLE));
 * plot.showInJFrame("Demo");            // resizable window with tooltips + PNG export
 * plot.drawChartOnGraphics2D(g2);       // or draw inside your own component
 * </pre>
 * <p>
 * Original implementation by Yuriy Guskov (MIT license) from
 * http://yuriy-g.github.io/simple-java-plot/ <br>
 * Modifications and integration in SPMF by Philippe Fournier-Viger
 * (modifications under the GNU GPL license).
 *
 * @author Yuriy Guskov
 * @author Philippe Fournier-Viger
 */
public class Plot {

	// ---------------------------------------------------------------
	// Public enums
	// ---------------------------------------------------------------

	public enum Line { NONE, SOLID, DASHED, DOTTED }
	public enum Marker { NONE, CIRCLE, SQUARE, DIAMOND, TRIANGLE, CROSS, PLUS, COLUMN, BAR }
	public enum AxisFormat { NUMBER, NUMBER_KGM, NUMBER_INT, PERCENT, TIME_HM, TIME_HMS, DATE, DATETIME_HM, DATETIME_HMS }
	public enum LegendFormat { NONE, TOP, RIGHT, BOTTOM }

	private enum HorizAlign { LEFT, CENTER, RIGHT }
	private enum VertAlign { TOP, CENTER, BOTTOM }

	// ---------------------------------------------------------------
	// Defaults
	// ---------------------------------------------------------------

	/** Default series colours (Tableau-10). */
	public static final Color[] DEFAULT_PALETTE = {
			new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44),
			new Color(214, 39, 40), new Color(148, 103, 189), new Color(140, 86, 75),
			new Color(227, 119, 194), new Color(127, 127, 127), new Color(188, 189, 34),
			new Color(23, 190, 207) };

	private static final String FONT_FAMILY = chooseFontFamily();
	private static final Color DEFAULT_FG = new Color(55, 60, 68);
	private static final Color DEFAULT_GRID = new Color(226, 229, 234);
	private static final Color DEFAULT_BORDER = new Color(200, 204, 210);
	private static final Color LEGEND_BG = new Color(255, 255, 255, 235);

	private static final long DAY_MS = 86_400_000L;
	private static final long[] TIME_STEPS_MS = {
			1000L, 2000L, 5000L, 10_000L, 15_000L, 30_000L,
			60_000L, 120_000L, 300_000L, 600_000L, 900_000L, 1_800_000L,
			3_600_000L, 7_200_000L, 10_800_000L, 21_600_000L, 43_200_000L,
			DAY_MS, 2 * DAY_MS, 7 * DAY_MS, 14 * DAY_MS, 30 * DAY_MS,
			91 * DAY_MS, 182 * DAY_MS, 365 * DAY_MS };

	// ---------------------------------------------------------------
	// State
	// ---------------------------------------------------------------

	private PlotOptions opts = new PlotOptions();
	private Rectangle boundRect;
	private final PlotArea plotArea;
	private final Map<String, Axis> xAxes = new LinkedHashMap<String, Axis>(3);
	private final Map<String, Axis> yAxes = new LinkedHashMap<String, Axis>(3);
	private final Map<String, DataSeries> dataSeriesMap = new LinkedHashMap<String, DataSeries>(5);

	// ---------------------------------------------------------------
	// Factories
	// ---------------------------------------------------------------

	public static Plot plot(PlotOptions opts) {
		return new Plot(opts);
	}

	public static PlotOptions plotOpts() {
		return new PlotOptions();
	}

	public static AxisOptions axisOpts() {
		return new AxisOptions();
	}

	public static DataSeriesOptions seriesOpts() {
		return new DataSeriesOptions();
	}

	public static Data data() {
		return new Data();
	}

	private Plot(PlotOptions opts) {
		if (opts != null) {
			this.opts = opts;
		}
		boundRect = new Rectangle(0, 0, this.opts.width, this.opts.height);
		plotArea = new PlotArea();
	}

	// ---------------------------------------------------------------
	// Plot options
	// ---------------------------------------------------------------

	public static class PlotOptions {

		private String title = "";
		private String subtitle = "";
		private int width = 800;
		private int height = 600;
		private Color backgroundColor = Color.WHITE;
		private Color plotBackgroundColor = null;
		private Color foregroundColor = DEFAULT_FG;
		private Color borderColor = DEFAULT_BORDER;
		private Font titleFont = new Font(FONT_FAMILY, Font.BOLD, 15);
		private Font subtitleFont = new Font(FONT_FAMILY, Font.PLAIN, 11);
		private Font labelFont = new Font(FONT_FAMILY, Font.PLAIN, 11);
		private int padding = 12;        // padding for the entire image
		private int plotPadding = 0;     // padding inside the plot frame (min/max values not on the border)
		private int labelPadding = 8;
		private int defaultLegendSignSize = 14;
		private int legendSignSize = 14;
		private Point grids = new Point(8, 6); // target number of ticks / grid lines by x and y
		private Color gridColor = DEFAULT_GRID;
		private Stroke gridStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
				BasicStroke.JOIN_MITER, 10.0f, new float[] { 3.0f, 4.0f }, 0.0f);
		private int tickSize = 4;
		private LegendFormat legend = LegendFormat.NONE;
		private boolean niceTicks = true;
		private boolean drawBorder = true;
		private boolean antialias = true;
		private Color[] palette = DEFAULT_PALETTE;

		private PlotOptions() {}

		public PlotOptions title(String title) { this.title = title == null ? "" : title; return this; }
		public PlotOptions subtitle(String subtitle) { this.subtitle = subtitle == null ? "" : subtitle; return this; }
		public PlotOptions width(int width) { this.width = Math.max(1, width); return this; }
		public PlotOptions height(int height) { this.height = Math.max(1, height); return this; }
		public PlotOptions bgColor(Color color) { if (color != null) this.backgroundColor = color; return this; }
		/** Background of the plotting frame only (null = same as {@link #bgColor}). */
		public PlotOptions plotBgColor(Color color) { this.plotBackgroundColor = color; return this; }
		public PlotOptions fgColor(Color color) { if (color != null) this.foregroundColor = color; return this; }
		public PlotOptions borderColor(Color color) { if (color != null) this.borderColor = color; return this; }
		public PlotOptions titleFont(Font font) { if (font != null) this.titleFont = font; return this; }
		public PlotOptions subtitleFont(Font font) { if (font != null) this.subtitleFont = font; return this; }
		public PlotOptions padding(int padding) { this.padding = Math.max(0, padding); return this; }
		public PlotOptions plotPadding(int padding) { this.plotPadding = Math.max(0, padding); return this; }
		public PlotOptions labelPadding(int padding) { this.labelPadding = Math.max(0, padding); return this; }
		public PlotOptions labelFont(Font font) { if (font != null) this.labelFont = font; return this; }
		/** Target number of ticks / grid lines on each axis. */
		public PlotOptions grids(int byX, int byY) { this.grids = new Point(Math.max(1, byX), Math.max(1, byY)); return this; }
		public PlotOptions gridColor(Color color) { if (color != null) this.gridColor = color; return this; }
		public PlotOptions gridStroke(Stroke stroke) { if (stroke != null) this.gridStroke = stroke; return this; }
		public PlotOptions tickSize(int value) { this.tickSize = Math.max(0, value); return this; }
		public PlotOptions legend(LegendFormat legend) { this.legend = legend == null ? LegendFormat.NONE : legend; return this; }
		/** Use "nice" 1-2-5 tick values (default true). False = evenly divide the range as in the original library. */
		public PlotOptions niceTicks(boolean nice) { this.niceTicks = nice; return this; }
		/** Draw a frame around the plotting area (default true). */
		public PlotOptions drawBorder(boolean draw) { this.drawBorder = draw; return this; }
		public PlotOptions antialias(boolean on) { this.antialias = on; return this; }
		/** Colours used for series that do not specify one. */
		public PlotOptions palette(Color... colors) { if (colors != null && colors.length > 0) this.palette = colors.clone(); return this; }

		public String getTitle() { return title; }
		public int getWidth() { return width; }
		public int getHeight() { return height; }
		public LegendFormat getLegend() { return legend; }
	}

	// ---------------------------------------------------------------
	// Axis options / axis
	// ---------------------------------------------------------------

	public static class AxisOptions {

		private AxisFormat format = AxisFormat.NUMBER;
		private boolean dynamicRange = true;
		private boolean niceRange = false;
		private boolean gridLines = true;
		private int ticks = 0; // 0 = use PlotOptions.grids
		private String title;  // null = axis key name
		private Range range;

		public AxisOptions format(AxisFormat format) { if (format != null) this.format = format; return this; }

		/** Fixes the axis range (disables the automatic range). */
		public AxisOptions range(double min, double max) {
			this.range = new Range(Math.min(min, max), Math.max(min, max));
			this.dynamicRange = false;
			return this;
		}

		/** Restores the automatic (data-driven) range. */
		public AxisOptions dynamicRange() { this.range = null; this.dynamicRange = true; return this; }

		/** When the range is automatic, expand it to "nice" tick boundaries (default false). */
		public AxisOptions niceRange(boolean nice) { this.niceRange = nice; return this; }

		public AxisOptions gridLines(boolean show) { this.gridLines = show; return this; }

		/** Target tick count for this axis (overrides {@link PlotOptions#grids}). */
		public AxisOptions ticks(int count) { this.ticks = Math.max(0, count); return this; }

		/** Text shown as axis name (defaults to the axis key). */
		public AxisOptions title(String title) { this.title = title; return this; }

		public AxisFormat getFormat() { return format; }
	}

	private class Axis {

		private final String name;
		private AxisOptions opts = new AxisOptions();

		// computed for the current drawing
		private Range range;
		private double[] ticks = new double[0];
		private String[] labels = new String[0];
		private double tickStep = Double.NaN;
		private int tickTarget;
		private int labelW;
		private int labelH;
		private int nameH;
		private int thickness;

		Axis(String name, AxisOptions opts) {
			this.name = name == null ? "" : name;
			if (opts != null) {
				this.opts = opts;
			}
		}

		String displayName() {
			return opts.title != null ? opts.title : name;
		}

		boolean hasName() {
			return !displayName().isEmpty();
		}

		@Override
		public String toString() {
			return "Axis [name=" + name + ", range=" + range + "]";
		}
	}

	// ---------------------------------------------------------------
	// Series options / data / series
	// ---------------------------------------------------------------

	public static class DataSeriesOptions {

		private Color seriesColor = null; // null = automatic palette colour
		private Line line = Line.SOLID;
		private float lineWidth = 2f;
		private float[] lineDash = new float[] { 4.0f, 4.0f };
		private Marker marker = Marker.NONE;
		private int markerSize = 8;
		private Color markerColor = null; // null = series colour
		private Color areaColor = null;
		private String xAxisName;
		private String yAxisName;
		private Axis xAxis;
		private Axis yAxis;

		public DataSeriesOptions color(Color seriesColor) { this.seriesColor = seriesColor; return this; }
		public DataSeriesOptions line(Line line) { this.line = line == null ? Line.NONE : line; return this; }
		public DataSeriesOptions lineWidth(int width) { this.lineWidth = Math.max(0.5f, width); return this; }
		public DataSeriesOptions lineWidth(float width) { this.lineWidth = Math.max(0.5f, width); return this; }
		public DataSeriesOptions lineDash(float[] dash) { if (dash != null && dash.length > 0) this.lineDash = dash.clone(); return this; }
		public DataSeriesOptions marker(Marker marker) { this.marker = marker == null ? Marker.NONE : marker; return this; }
		public DataSeriesOptions markerSize(int markerSize) { this.markerSize = Math.max(1, markerSize); return this; }
		public DataSeriesOptions markerColor(Color color) { this.markerColor = color; return this; }
		public DataSeriesOptions areaColor(Color color) { this.areaColor = color; return this; }
		public DataSeriesOptions xAxis(String name) { this.xAxisName = name; return this; }
		public DataSeriesOptions yAxis(String name) { this.yAxisName = name; return this; }

		public Color getColor() { return seriesColor; }
		public Marker getMarker() { return marker; }
		public Line getLine() { return line; }
	}

	public static class Data {

		private double[] x1;
		private double[] y1;
		private List<Double> x2;
		private List<Double> y2;

		private Data() {}

		public Data xy(double[] x, double[] y) {
			if (x == null || y == null) throw new IllegalArgumentException("x and y must not be null");
			if (x.length != y.length) throw new IllegalArgumentException(
					"x and y must have the same length (" + x.length + " vs " + y.length + ")");
			this.x1 = x;
			this.y1 = y;
			this.x2 = null;
			this.y2 = null;
			return this;
		}

		public Data xy(double x, double y) {
			if (this.x2 == null || this.y2 == null) {
				this.x2 = new ArrayList<Double>(16);
				this.y2 = new ArrayList<Double>(16);
				this.x1 = null;
				this.y1 = null;
			}
			x2.add(x);
			y2.add(y);
			return this;
		}

		public Data xy(List<Double> x, List<Double> y) {
			if (x == null || y == null) throw new IllegalArgumentException("x and y must not be null");
			if (x.size() != y.size()) throw new IllegalArgumentException(
					"x and y must have the same size (" + x.size() + " vs " + y.size() + ")");
			this.x2 = x;
			this.y2 = y;
			this.x1 = null;
			this.y1 = null;
			return this;
		}

		public Data clear() {
			x1 = null; y1 = null; x2 = null; y2 = null;
			return this;
		}

		public int size() {
			if (x1 != null) return x1.length;
			if (x2 != null) return x2.size();
			return 0;
		}

		public double x(int i) {
			if (x1 != null) return x1[i];
			if (x2 != null) return x2.get(i);
			return 0;
		}

		public double y(int i) {
			if (y1 != null) return y1[i];
			if (y2 != null) return y2.get(i);
			return 0;
		}
	}

	public class DataSeries {

		private final String name;
		private String displayName;
		private DataSeriesOptions opts = new DataSeriesOptions();
		private Data data;
		private Color color = DEFAULT_PALETTE[0]; // resolved colour

		public DataSeries(String name, Data data, DataSeriesOptions opts) {
			this.name = name == null ? "" : name;
			this.displayName = this.name;
			if (opts != null) {
				this.opts = opts;
			}
			this.data = data != null ? data : data();
		}

		public String getName() { return name; }
		public Data getData() { return data; }
		public DataSeriesOptions getOptions() { return opts; }
		public Color getColor() { return color; }

		public void clear() {
			// kept for API compatibility
		}

		private Range xRange() {
			return rangeOf(true);
		}

		private Range yRange() {
			return rangeOf(false);
		}

		private Range rangeOf(boolean isX) {
			if (data == null) return null;
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			int n = data.size();
			for (int i = 0; i < n; i++) {
				double v = isX ? data.x(i) : data.y(i);
				if (Double.isNaN(v) || Double.isInfinite(v)) continue;
				if (v < min) min = v;
				if (v > max) max = v;
			}
			return min <= max ? new Range(min, max) : null;
		}

		private Color markerFill() {
			return opts.markerColor != null ? opts.markerColor : color;
		}

		private Color markerBorder() {
			return opts.markerColor != null ? color : darken(color, 0.78f);
		}

		private Stroke lineStroke(float width) {
			switch (opts.line) {
			case DASHED:
				return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, opts.lineDash, 0f);
			case DOTTED:
				return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
						new float[] { 0.5f, Math.max(2f, width * 2.2f) }, 0f);
			default:
				return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
			}
		}

		private void draw(Graphics2D g0) {
			if (data == null || data.size() == 0 || opts.xAxis == null || opts.yAxis == null) return;
			Range xr = opts.xAxis.range;
			Range yr = opts.yAxis.range;
			if (xr == null || yr == null) return;

			int n = data.size();
			double[] px = new double[n];
			double[] py = new double[n];
			for (int i = 0; i < n; i++) {
				px[i] = x2x(data.x(i), xr, plotArea.xPlotRange);
				py[i] = y2y(data.y(i), yr, plotArea.yPlotRange);
			}

			boolean hasLine = opts.line != Line.NONE;
			if (hasLine || opts.areaColor != null) {
				Graphics2D g = (Graphics2D) g0.create();
				try {
					g.clip(plotArea.plotClipRect);
					double base = y2y(Math.max(yr.min, Math.min(yr.max, 0)), yr, plotArea.yPlotRange);
					int start = -1;
					for (int i = 0; i <= n; i++) {
						boolean valid = i < n && !Double.isNaN(px[i]) && !Double.isNaN(py[i])
								&& !Double.isInfinite(px[i]) && !Double.isInfinite(py[i]);
						if (valid && start < 0) start = i;
						if (!valid && start >= 0) {
							drawRun(g, px, py, start, i - 1, base, hasLine);
							start = -1;
						}
					}
				} finally {
					g.dispose();
				}
			}

			if (opts.marker != Marker.NONE) {
				Graphics2D g = (Graphics2D) g0.create();
				try {
					Rectangle clip = new Rectangle(plotArea.plotClipRect);
					clip.grow(opts.markerSize, opts.markerSize);
					g.clip(clip);
					g.setStroke(new BasicStroke(1.2f));
					double bottom = plotArea.plotRect.y + (double) plotArea.plotRect.height;
					double left = plotArea.plotRect.x;
					Color fill = markerFill();
					Color border = markerBorder();
					for (int i = 0; i < n; i++) {
						if (Double.isNaN(px[i]) || Double.isNaN(py[i])) continue;
						drawMarkerShape(g, opts.marker, px[i], py[i], left, bottom, opts.markerSize, fill, border);
					}
				} finally {
					g.dispose();
				}
			}
		}

		private void drawRun(Graphics2D g, double[] px, double[] py, int from, int to, double base, boolean hasLine) {
			if (from == to) {
				if (hasLine) {
					g.setColor(color);
					g.setStroke(lineStroke(opts.lineWidth));
					g.draw(new Line2D.Double(px[from], py[from], px[from], py[from]));
				}
				return;
			}
			if (opts.areaColor != null) {
				Path2D.Double area = new Path2D.Double(Path2D.WIND_NON_ZERO, to - from + 3);
				area.moveTo(px[from], base);
				for (int i = from; i <= to; i++) area.lineTo(px[i], py[i]);
				area.lineTo(px[to], base);
				area.closePath();
				g.setColor(opts.areaColor);
				g.fill(area);
			}
			if (hasLine) {
				Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, to - from + 1);
				path.moveTo(px[from], py[from]);
				for (int i = from + 1; i <= to; i++) path.lineTo(px[i], py[i]);
				g.setColor(color);
				g.setStroke(lineStroke(opts.lineWidth));
				g.draw(path);
			}
		}

		private void drawLegendSign(Graphics2D g, int x, int cy, int sign) {
			if (opts.areaColor != null) {
				g.setColor(opts.areaColor);
				g.fillRect(x, cy - sign / 4, sign, sign / 2);
			}
			if (opts.line != Line.NONE) {
				g.setColor(color);
				g.setStroke(lineStroke(Math.min(opts.lineWidth, 3f)));
				g.draw(new Line2D.Double(x, cy, x + sign, cy));
			}
			switch (opts.marker) {
			case NONE:
				break;
			case COLUMN:
			case BAR:
				g.setColor(markerFill());
				g.fillRect(x + 2, cy - sign / 3, sign - 4, 2 * sign / 3);
				g.setColor(markerBorder());
				g.setStroke(new BasicStroke(1f));
				g.drawRect(x + 2, cy - sign / 3, sign - 4, 2 * sign / 3);
				break;
			default:
				g.setStroke(new BasicStroke(1.2f));
				drawMarkerShape(g, opts.marker, x + sign / 2.0, cy, x, cy + sign / 2.0,
						Math.min(opts.markerSize, sign - 2), markerFill(), markerBorder());
			}
		}
	}

	// ---------------------------------------------------------------
	// Public API: axes and series
	// ---------------------------------------------------------------

	public PlotOptions opts() {
		return opts;
	}

	public Plot xAxis(String name, AxisOptions opts) {
		xAxes.put(name, new Axis(name, opts));
		return this;
	}

	public Plot yAxis(String name, AxisOptions opts) {
		yAxes.put(name, new Axis(name, opts));
		return this;
	}

	public Plot series(String name, Data data, DataSeriesOptions opts) {
		DataSeries series = dataSeriesMap.get(name);
		if (series == null) {
			dataSeriesMap.put(name, new DataSeries(name, data, opts));
		} else {
			series.data = data != null ? data : data();
			if (opts != null) series.opts = opts;
		}
		return this;
	}

	public Plot series(String name, DataSeriesOptions opts) {
		DataSeries series = dataSeriesMap.get(name);
		if (series != null && opts != null) {
			series.opts = opts;
		}
		return this;
	}

	public Plot removeSeries(String name) {
		dataSeriesMap.remove(name);
		return this;
	}

	public Plot clearSeries() {
		dataSeriesMap.clear();
		return this;
	}

	public Set<String> seriesNames() {
		return Collections.unmodifiableSet(dataSeriesMap.keySet());
	}

	// ---------------------------------------------------------------
	// Public API: size / geometry / mapping
	// ---------------------------------------------------------------

	public int getWidth() {
		return opts.width;
	}

	public int getHeight() {
		return opts.height;
	}

	/** Resizes the chart and recomputes its layout using the given graphics for font metrics. */
	public void resize(int width, int height, Graphics g) {
		opts.width = Math.max(1, width);
		opts.height = Math.max(1, height);
		boundRect = new Rectangle(0, 0, opts.width, opts.height);
		plotArea.clear();
		if (g instanceof Graphics2D) {
			calc((Graphics2D) g);
		}
	}

	/** The frame of the plotting area (outer rectangle, without labels/legend). */
	public Rectangle getPlotAreaRectangle() {
		return plotArea.plotBorderRect;
	}

	/** The rectangle in which data is mapped (frame minus {@code plotPadding}). */
	public Rectangle getPlotRectangle() {
		return plotArea.plotRect;
	}

	/** @return a copy of the range of the first X axis, or null if not yet computed */
	public Range getXRange() {
		Axis a = firstAxis(xAxes);
		return a == null || a.range == null ? null : new Range(a.range);
	}

	/** @return a copy of the range of the first Y axis, or null if not yet computed */
	public Range getYRange() {
		Axis a = firstAxis(yAxes);
		return a == null || a.range == null ? null : new Range(a.range);
	}

	public double xToPixel(double x) { return xToPixel(x, null); }
	public double yToPixel(double y) { return yToPixel(y, null); }
	public double pixelToX(double px) { return pixelToX(px, null); }
	public double pixelToY(double py) { return pixelToY(py, null); }

	public double xToPixel(double x, String axisName) {
		Axis a = axisOrFirst(xAxes, axisName);
		return a == null || a.range == null ? Double.NaN : x2x(x, a.range, plotArea.xPlotRange);
	}

	public double yToPixel(double y, String axisName) {
		Axis a = axisOrFirst(yAxes, axisName);
		return a == null || a.range == null ? Double.NaN : y2y(y, a.range, plotArea.yPlotRange);
	}

	public double pixelToX(double px, String axisName) {
		Axis a = axisOrFirst(xAxes, axisName);
		if (a == null || a.range == null) return Double.NaN;
		Range r = a.range, p = plotArea.xPlotRange;
		return r.diff == 0 || p.diff == 0 ? r.min : r.min + (px - p.min) / p.diff * r.diff;
	}

	public double pixelToY(double py, String axisName) {
		Axis a = axisOrFirst(yAxes, axisName);
		if (a == null || a.range == null) return Double.NaN;
		Range r = a.range, p = plotArea.yPlotRange;
		return r.diff == 0 || p.diff == 0 ? r.min : r.min + (p.max - py) / p.diff * r.diff;
	}

	/** Result of {@link Plot#nearestPoint(double, double, double)}. */
	public static final class PointHit {
		public final String seriesName;
		public final int index;
		public final double x;
		public final double y;
		public final double pixelX;
		public final double pixelY;
		public final double distance;

		PointHit(String seriesName, int index, double x, double y, double pixelX, double pixelY, double distance) {
			this.seriesName = seriesName;
			this.index = index;
			this.x = x;
			this.y = y;
			this.pixelX = pixelX;
			this.pixelY = pixelY;
			this.distance = distance;
		}

		@Override
		public String toString() {
			return seriesName + "[" + index + "] = (" + x + ", " + y + ")";
		}
	}

	/**
	 * Finds the data point closest to a pixel position (after the chart has been
	 * drawn or {@link #resize} called).
	 *
	 * @param maxDistance maximum pixel distance (&lt;= 0 for unlimited)
	 * @return the closest point or null
	 */
	public PointHit nearestPoint(double px, double py, double maxDistance) {
		double bestD2 = maxDistance > 0 ? maxDistance * maxDistance : Double.POSITIVE_INFINITY;
		PointHit best = null;
		for (DataSeries s : dataSeriesMap.values()) {
			if (s.data == null || s.opts.xAxis == null || s.opts.yAxis == null
					|| s.opts.xAxis.range == null || s.opts.yAxis.range == null) continue;
			int n = s.data.size();
			for (int i = 0; i < n; i++) {
				double x = x2x(s.data.x(i), s.opts.xAxis.range, plotArea.xPlotRange);
				double y = y2y(s.data.y(i), s.opts.yAxis.range, plotArea.yPlotRange);
				if (Double.isNaN(x) || Double.isNaN(y)) continue;
				double d2 = (x - px) * (x - px) + (y - py) * (y - py);
				if (d2 < bestD2) {
					bestD2 = d2;
					best = new PointHit(s.name, i, s.data.x(i), s.data.y(i), x, y, Math.sqrt(d2));
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------
	// Public API: rendering / export
	// ---------------------------------------------------------------

	/**
	 * Draws the chart on a Graphics2D object (state of {@code g} is preserved).
	 */
	public void drawChartOnGraphics2D(Graphics2D g) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			applyHints(g2);
			calc(g2);
			drawBackground(g2);
			plotArea.draw(g2);
		} finally {
			g2.dispose();
		}
	}

	/** Renders the chart to an image; {@code scale = 2} produces a HiDPI image. */
	public BufferedImage toImage(double scale) {
		if (scale <= 0) scale = 1;
		int w = Math.max(1, (int) Math.ceil(opts.width * scale));
		int h = Math.max(1, (int) Math.ceil(opts.height * scale));
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try {
			g.scale(scale, scale);
			boundRect = new Rectangle(0, 0, opts.width, opts.height);
			drawChartOnGraphics2D(g);
		} finally {
			g.dispose();
		}
		return image;
	}

	/** Saves the chart as {@code fileName + "." + type} (e.g. type = "png"). */
	public void save(String fileName, String type) throws IOException {
		save(new File(fileName + "." + type), type, 1.0);
	}

	public void save(File file, String type, double scale) throws IOException {
		clear();
		BufferedImage bi = toImage(scale);
		if (!ImageIO.write(bi, type, file)) {
			throw new IOException("No image writer for type: " + type);
		}
	}

	/**
	 * Shows the chart in a resizable window with tooltips and a "Save as PNG" button.
	 *
	 * @return the created frame
	 */
	public JFrame showInJFrame(String title) {
		clear();
		final PlotPanel panel = new PlotPanel(this);
		panel.setPreferredSize(new Dimension(opts.width, opts.height));

		JButton save = new JButton("Save as PNG");
		save.setFocusable(false);
		save.addActionListener(e -> panel.savePng());
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
		bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DEFAULT_BORDER));
		bar.add(save);

		JFrame frame = new JFrame(title);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(bar, BorderLayout.NORTH);
		frame.getContentPane().add(panel, BorderLayout.CENTER);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		return frame;
	}

	/**
	 * A Swing component that draws a {@link Plot}, redraws on resize and shows
	 * a tooltip for the point under the mouse.
	 */
	public static class PlotPanel extends JPanel {

		private static final long serialVersionUID = 1L;
		private final Plot plot;

		public PlotPanel(Plot plot) {
			this.plot = plot;
			setBackground(plot.opts.backgroundColor);
			setToolTipText(""); // registers with the tooltip manager
		}

		public Plot getPlot() {
			return plot;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				plot.resize(getWidth(), getHeight(), g2);
				plot.drawChartOnGraphics2D(g2);
			} finally {
				g2.dispose();
			}
		}

		@Override
		public String getToolTipText(MouseEvent e) {
			PointHit hit = plot.nearestPoint(e.getX(), e.getY(), 12);
			if (hit == null) return null;
			Axis xa = plot.firstAxis(plot.xAxes);
			Axis ya = plot.firstAxis(plot.yAxes);
			String xs = formatDouble(hit.x, xa == null ? AxisFormat.NUMBER : xa.opts.format);
			String ys = formatDouble(hit.y, ya == null ? AxisFormat.NUMBER : ya.opts.format);
			return "<html><b>" + hit.seriesName + "</b><br>x = " + xs + "<br>y = " + ys + "</html>";
		}

		public void savePng() {
			JFileChooser fc = new JFileChooser();
			fc.setDialogTitle("Save as PNG");
			fc.setFileFilter(new FileNameExtensionFilter("PNG image (*.png)", "png"));
			String base = plot.opts.title.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
			fc.setSelectedFile(new File((base.isEmpty() ? "plot" : base) + ".png"));
			if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
			File file = fc.getSelectedFile();
			if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
				file = new File(file.getPath() + ".png");
			}
			try {
				plot.save(file, "png", 2.0);
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Failed to save image: " + ex.getMessage(),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// ---------------------------------------------------------------
	// Internal: calc / draw entry points
	// ---------------------------------------------------------------

	private void calc(Graphics2D g) {
		plotArea.calc(g);
	}

	private void clear() {
		plotArea.clear();
		for (DataSeries series : dataSeriesMap.values()) {
			series.clear();
		}
	}

	private void applyHints(Graphics2D g) {
		Object aa = opts.antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private void drawBackground(Graphics2D g) {
		g.setColor(opts.backgroundColor);
		g.fillRect(boundRect.x, boundRect.y, boundRect.width, boundRect.height);
	}

	private Axis firstAxis(Map<String, Axis> axes) {
		return axes.isEmpty() ? null : axes.values().iterator().next();
	}

	private Axis axisOrFirst(Map<String, Axis> axes, String name) {
		if (name != null) {
			Axis a = axes.get(name);
			if (a != null) return a;
		}
		return firstAxis(axes);
	}

	// ---------------------------------------------------------------
	// Legend / plot area
	// ---------------------------------------------------------------

	private static class Legend {
		final Rectangle rect = new Rectangle();
		int labelW;
		int labelH;
		int entryW;
		int entryWPadded;
		int xCount;
		int yCount;
		int signSize;
		int rowH;
	}

	private class PlotArea {

		// boundRect | title/labels/legend | plotBorderRect | plotPadding | plotRect
		private final Rectangle plotBorderRect = new Rectangle();
		private final Rectangle plotRect = new Rectangle();
		private final Rectangle plotClipRect = new Rectangle();
		private final Legend legend = new Legend();
		private final Range xPlotRange = new Range(0, 0);
		private final Range yPlotRange = new Range(0, 0);

		private int titleH;
		private int subtitleH;
		private boolean hasData;

		PlotArea() {
			clear();
		}

		private void clear() {
			plotBorderRect.setBounds(boundRect);
			plotRectChanged();
		}

		private void plotRectChanged() {
			plotRect.setBounds(plotBorderRect.x + opts.plotPadding, plotBorderRect.y + opts.plotPadding,
					Math.max(1, plotBorderRect.width - opts.plotPadding * 2),
					Math.max(1, plotBorderRect.height - opts.plotPadding * 2));
			xPlotRange.setMin(plotRect.getX());
			xPlotRange.setMax(plotRect.getX() + plotRect.getWidth());
			yPlotRange.setMin(plotRect.getY());
			yPlotRange.setMax(plotRect.getY() + plotRect.getHeight());
			plotClipRect.setBounds(plotBorderRect.x, plotBorderRect.y,
					plotBorderRect.width + 1, plotBorderRect.height + 1);
		}

		// ----- layout -------------------------------------------------

		private void calc(Graphics2D g) {
			plotBorderRect.setBounds(boundRect);
			plotRectChanged();
			resolveSeries();
			calcRanges();
			for (Axis a : xAxes.values()) a.tickTarget = a.opts.ticks > 0 ? a.opts.ticks : opts.grids.x;
			for (Axis a : yAxes.values()) a.tickTarget = a.opts.ticks > 0 ? a.opts.ticks : opts.grids.y;

			for (int attempt = 0; attempt < 4; attempt++) {
				for (Axis a : xAxes.values()) computeTicks(g, a);
				for (Axis a : yAxes.values()) computeTicks(g, a);
				layout(g);
				if (!reduceCrowdedAxes()) break;
			}
		}

		private void resolveSeries() {
			hasData = false;
			if (dataSeriesMap.isEmpty()) return;
			if (xAxes.isEmpty()) xAxes.put("x", new Axis("", null));
			if (yAxes.isEmpty()) yAxes.put("y", new Axis("", null));
			Axis defX = firstAxis(xAxes);
			Axis defY = firstAxis(yAxes);
			boolean multiAxes = xAxes.size() > 1 || yAxes.size() > 1;
			int i = 0;
			for (DataSeries s : dataSeriesMap.values()) {
				DataSeriesOptions o = s.opts;
				Axis ax = o.xAxisName != null ? xAxes.get(o.xAxisName) : null;
				Axis ay = o.yAxisName != null ? yAxes.get(o.yAxisName) : null;
				o.xAxis = ax != null ? ax : defX;
				o.yAxis = ay != null ? ay : defY;
				s.color = o.seriesColor != null ? o.seriesColor : opts.palette[i % opts.palette.length];
				s.displayName = multiAxes
						? s.name + " (" + o.yAxis.displayName() + "/" + o.xAxis.displayName() + ")"
						: s.name;
				if (s.data != null && s.data.size() > 0) hasData = true;
				i++;
			}
		}

		private void calcRanges() {
			for (Axis a : xAxes.values()) a.range = a.opts.dynamicRange ? null : new Range(a.opts.range);
			for (Axis a : yAxes.values()) a.range = a.opts.dynamicRange ? null : new Range(a.opts.range);
			for (DataSeries s : dataSeriesMap.values()) {
				Axis ax = s.opts.xAxis;
				Axis ay = s.opts.yAxis;
				if (ax != null && ax.opts.dynamicRange) ax.range = union(ax.range, s.xRange());
				if (ay != null && ay.opts.dynamicRange) ay.range = union(ay.range, s.yRange());
			}
			for (Axis a : xAxes.values()) if (a.range == null || !a.range.isFinite()) a.range = new Range(0, 1);
			for (Axis a : yAxes.values()) if (a.range == null || !a.range.isFinite()) a.range = new Range(0, 1);
		}

		private Range union(Range acc, Range r) {
			if (r == null) return acc;
			if (acc == null) return new Range(r);
			if (r.min < acc.min) acc.setMin(r.min);
			if (r.max > acc.max) acc.setMax(r.max);
			return acc;
		}

		private void computeTicks(Graphics2D g, Axis a) {
			Range r = a.range;
			AxisFormat fmt = a.opts.format;
			List<Double> list = new ArrayList<Double>();
			double step = Double.NaN;

			if (!(r.diff > 0)) {
				list.add(r.min);
			} else if (!opts.niceTicks) {
				int n = Math.max(1, a.tickTarget);
				step = r.diff / n;
				for (int j = 0; j <= n; j++) list.add(r.min + step * j);
			} else {
				double raw = r.diff / Math.max(1, a.tickTarget);
				step = isTimeFormat(fmt) ? niceTimeStep(raw) : niceStep(raw);
				if (fmt == AxisFormat.NUMBER_INT && step < 1) step = 1;
				if (a.opts.niceRange && a.opts.dynamicRange) {
					r.setMin(Math.floor(r.min / step + 1e-9) * step);
					r.setMax(Math.ceil(r.max / step - 1e-9) * step);
				}
				long k0 = (long) Math.ceil(r.min / step - 1e-9);
				long k1 = (long) Math.floor(r.max / step + 1e-9);
				if (k1 - k0 > 1000) k1 = k0 + 1000;
				for (long k = k0; k <= k1; k++) {
					double v = k * step;
					if (Math.abs(v) < step * 1e-9) v = 0;
					list.add(v);
				}
				if (list.size() < 2) {
					list.clear();
					list.add(r.min);
					list.add(r.max);
					step = r.diff;
				}
			}

			a.tickStep = step;
			a.ticks = new double[list.size()];
			a.labels = new String[list.size()];
			FontMetrics fm = g.getFontMetrics(opts.labelFont);
			a.labelW = 0;
			a.labelH = fm.getHeight();
			for (int j = 0; j < a.ticks.length; j++) {
				a.ticks[j] = list.get(j);
				a.labels[j] = formatDouble(a.ticks[j], fmt, step);
				a.labelW = Math.max(a.labelW, fm.stringWidth(a.labels[j]));
			}
			a.nameH = g.getFontMetrics(opts.labelFont.deriveFont(Font.BOLD)).getHeight();
		}

		private void layout(Graphics2D g) {
			titleH = opts.title.isEmpty() ? 0 : g.getFontMetrics(opts.titleFont).getHeight();
			subtitleH = opts.subtitle.isEmpty() ? 0 : g.getFontMetrics(opts.subtitleFont).getHeight();

			int top = opts.padding;
			if (titleH > 0) top += titleH + 2;
			if (subtitleH > 0) top += subtitleH + 2;
			if (titleH > 0 || subtitleH > 0) top += opts.labelPadding;
			int maxYLabelH = 0;
			for (Axis a : yAxes.values()) maxYLabelH = Math.max(maxYLabelH, a.labelH);
			top = Math.max(top, opts.padding + maxYLabelH / 2 + 2);

			int bottom = opts.padding;
			for (Axis a : xAxes.values()) {
				a.thickness = opts.tickSize + 3 + a.labelH + (a.hasName() ? a.nameH + 2 : 0) + opts.labelPadding / 2;
				bottom += a.thickness;
			}

			int left = opts.padding;
			for (Axis a : yAxes.values()) {
				a.thickness = opts.tickSize + 4 + a.labelW + (a.hasName() ? a.nameH + 4 : 0) + opts.labelPadding / 2;
				left += a.thickness;
			}

			int overflow = 0;
			for (Axis a : xAxes.values()) overflow = Math.max(overflow, a.labelW / 2);
			int right = Math.max(opts.padding, overflow + 4);

			calcLegend(g, boundRect.width - left - right);
			switch (opts.legend) {
			case TOP: top += legend.rect.height + opts.labelPadding; break;
			case BOTTOM: bottom += legend.rect.height + opts.labelPadding; break;
			case RIGHT: right += legend.rect.width + opts.labelPadding; break;
			default:
			}

			int w = Math.max(10, boundRect.width - left - right);
			int h = Math.max(10, boundRect.height - top - bottom);
			plotBorderRect.setBounds(boundRect.x + left, boundRect.y + top, w, h);
			plotRectChanged();
			positionLegend();
		}

		/** Lowers the tick target of axes whose labels would collide. */
		private boolean reduceCrowdedAxes() {
			boolean changed = false;
			for (Axis a : xAxes.values()) {
				if (a.ticks.length < 2 || !(a.range.diff > 0) || a.tickTarget <= 1) continue;
				double spacing = Double.isNaN(a.tickStep)
						? plotRect.getWidth() / (a.ticks.length - 1)
						: a.tickStep / a.range.diff * plotRect.getWidth();
				int need = a.labelW + 8;
				if (need > spacing) {
					int newTarget = (int) Math.floor(plotRect.getWidth() / need);
					a.tickTarget = Math.max(1, Math.min(newTarget, a.tickTarget - 1));
					changed = true;
				}
			}
			for (Axis a : yAxes.values()) {
				if (a.ticks.length < 2 || !(a.range.diff > 0) || a.tickTarget <= 1) continue;
				double spacing = Double.isNaN(a.tickStep)
						? plotRect.getHeight() / (a.ticks.length - 1)
						: a.tickStep / a.range.diff * plotRect.getHeight();
				int need = a.labelH + 4;
				if (need > spacing) {
					int newTarget = (int) Math.floor(plotRect.getHeight() / need);
					a.tickTarget = Math.max(1, Math.min(newTarget, a.tickTarget - 1));
					changed = true;
				}
			}
			return changed;
		}

		private void calcLegend(Graphics2D g, int availableWidth) {
			legend.rect.setBounds(0, 0, 0, 0);
			if (opts.legend == LegendFormat.NONE || dataSeriesMap.isEmpty()) return;

			FontMetrics fm = g.getFontMetrics(opts.labelFont);
			legend.labelW = 0;
			legend.labelH = fm.getHeight();
			int sign = opts.defaultLegendSignSize;
			for (DataSeries s : dataSeriesMap.values()) {
				legend.labelW = Math.max(legend.labelW, fm.stringWidth(s.displayName));
				if (s.opts.marker != Marker.NONE && s.opts.marker != Marker.COLUMN && s.opts.marker != Marker.BAR) {
					sign = Math.max(sign, s.opts.markerSize + 4);
				}
			}
			opts.legendSignSize = sign;
			legend.signSize = sign;
			legend.entryW = sign + 6 + legend.labelW;
			legend.entryWPadded = legend.entryW + opts.labelPadding * 2;
			legend.rowH = Math.max(legend.labelH, sign) + opts.labelPadding / 2;
			int size = dataSeriesMap.size();

			switch (opts.legend) {
			case TOP:
			case BOTTOM: {
				int perRow = Math.max(1, (availableWidth - opts.labelPadding) / legend.entryWPadded);
				legend.xCount = Math.min(size, perRow);
				legend.yCount = (int) Math.ceil((double) size / legend.xCount);
				legend.rect.width = opts.labelPadding + legend.xCount * legend.entryWPadded;
				legend.rect.height = opts.labelPadding + legend.yCount * legend.rowH + opts.labelPadding / 2;
				break;
			}
			case RIGHT:
				legend.xCount = 1;
				legend.yCount = size;
				legend.rect.width = opts.labelPadding * 2 + legend.entryW;
				legend.rect.height = opts.labelPadding + size * legend.rowH + opts.labelPadding / 2;
				break;
			default:
			}
		}

		private void positionLegend() {
			if (opts.legend == LegendFormat.NONE || legend.rect.width == 0) return;
			switch (opts.legend) {
			case TOP:
				legend.rect.x = plotBorderRect.x + (plotBorderRect.width - legend.rect.width) / 2;
				legend.rect.y = plotBorderRect.y - opts.labelPadding - legend.rect.height;
				break;
			case BOTTOM:
				legend.rect.x = plotBorderRect.x + (plotBorderRect.width - legend.rect.width) / 2;
				legend.rect.y = boundRect.y + boundRect.height - opts.padding - legend.rect.height;
				break;
			case RIGHT:
				legend.rect.x = boundRect.x + boundRect.width - opts.padding - legend.rect.width;
				legend.rect.y = plotBorderRect.y + (plotBorderRect.height - legend.rect.height) / 2;
				break;
			default:
			}
			legend.rect.x = Math.max(boundRect.x + opts.padding, legend.rect.x);
			legend.rect.y = Math.max(boundRect.y + opts.padding, legend.rect.y);
		}

		// ----- drawing ------------------------------------------------

		private void draw(Graphics2D g) {
			drawPlotBackground(g);
			drawGrid(g);
			for (DataSeries series : dataSeriesMap.values()) {
				series.draw(g);
			}
			drawBorder(g);
			drawAxes(g);
			drawTitle(g);
			drawLegend(g);
			if (!hasData) {
				drawEmptyState(g);
			}
		}

		private void drawPlotBackground(Graphics2D g) {
			if (opts.plotBackgroundColor != null) {
				g.setColor(opts.plotBackgroundColor);
				g.fillRect(plotBorderRect.x, plotBorderRect.y, plotBorderRect.width, plotBorderRect.height);
			}
		}

		private void drawGrid(Graphics2D g) {
			Stroke old = g.getStroke();
			g.setStroke(opts.gridStroke);
			g.setColor(opts.gridColor);
			int top = plotBorderRect.y;
			int bottom = plotBorderRect.y + plotBorderRect.height;
			int left = plotBorderRect.x;
			int right = plotBorderRect.x + plotBorderRect.width;

			Axis xa = firstAxis(xAxes);
			if (xa != null && xa.opts.gridLines) {
				for (double t : xa.ticks) {
					int x = toInt(x2x(t, xa.range, xPlotRange));
					if (x > left && x < right) g.drawLine(x, top, x, bottom);
				}
			}
			Axis ya = firstAxis(yAxes);
			if (ya != null && ya.opts.gridLines) {
				for (double t : ya.ticks) {
					int y = toInt(y2y(t, ya.range, yPlotRange));
					if (y > top && y < bottom) g.drawLine(left, y, right, y);
				}
			}
			g.setStroke(old);
		}

		private void drawBorder(Graphics2D g) {
			if (!opts.drawBorder) return;
			g.setColor(opts.borderColor);
			g.setStroke(new BasicStroke(1f));
			g.drawRect(plotBorderRect.x, plotBorderRect.y, plotBorderRect.width, plotBorderRect.height);
		}

		private void drawAxes(Graphics2D g) {
			Font nameFont = opts.labelFont.deriveFont(Font.BOLD);
			Color tickLabelColor = lighten(opts.foregroundColor, 0.25f);
			Stroke axisStroke = new BasicStroke(1.3f);
			Stroke tickStroke = new BasicStroke(1f);
			int bottomY = plotBorderRect.y + plotBorderRect.height;
			int leftX = plotBorderRect.x;

			int offset = 0;
			for (Axis a : xAxes.values()) {
				int y0 = bottomY + offset;
				g.setColor(opts.foregroundColor);
				g.setStroke(axisStroke);
				g.drawLine(plotBorderRect.x, y0, plotBorderRect.x + plotBorderRect.width, y0);
				g.setStroke(tickStroke);
				g.setFont(opts.labelFont);
				int labelTop = y0 + opts.tickSize + 3;
				for (int j = 0; j < a.ticks.length; j++) {
					int x = toInt(x2x(a.ticks[j], a.range, xPlotRange));
					g.setColor(opts.foregroundColor);
					g.drawLine(x, y0, x, y0 + opts.tickSize);
					g.setColor(tickLabelColor);
					drawLabel(g, a.labels[j], x, labelTop, HorizAlign.CENTER, VertAlign.TOP);
				}
				if (a.hasName()) {
					g.setFont(nameFont);
					g.setColor(opts.foregroundColor);
					drawLabel(g, a.displayName(), plotBorderRect.x + plotBorderRect.width / 2,
							labelTop + a.labelH + 2, HorizAlign.CENTER, VertAlign.TOP);
				}
				offset += a.thickness;
			}

			offset = 0;
			for (Axis a : yAxes.values()) {
				int x0 = leftX - offset;
				g.setColor(opts.foregroundColor);
				g.setStroke(axisStroke);
				g.drawLine(x0, plotBorderRect.y, x0, bottomY);
				g.setStroke(tickStroke);
				g.setFont(opts.labelFont);
				int labelRight = x0 - opts.tickSize - 4;
				for (int j = 0; j < a.ticks.length; j++) {
					int y = toInt(y2y(a.ticks[j], a.range, yPlotRange));
					g.setColor(opts.foregroundColor);
					g.drawLine(x0 - opts.tickSize, y, x0, y);
					g.setColor(tickLabelColor);
					drawLabel(g, a.labels[j], labelRight, y, HorizAlign.RIGHT, VertAlign.CENTER);
				}
				if (a.hasName()) {
					g.setFont(nameFont);
					g.setColor(opts.foregroundColor);
					int cx = labelRight - a.labelW - 4 - a.nameH / 2;
					drawRotatedLabel(g, a.displayName(), cx, plotBorderRect.y + plotBorderRect.height / 2);
				}
				offset += a.thickness;
			}
		}

		private void drawTitle(Graphics2D g) {
			int cx = plotBorderRect.x + plotBorderRect.width / 2;
			int y = boundRect.y + opts.padding;
			if (titleH > 0) {
				g.setFont(opts.titleFont);
				g.setColor(opts.foregroundColor);
				drawLabel(g, opts.title, cx, y, HorizAlign.CENTER, VertAlign.TOP);
				y += titleH + 2;
			}
			if (subtitleH > 0) {
				g.setFont(opts.subtitleFont);
				g.setColor(lighten(opts.foregroundColor, 0.4f));
				drawLabel(g, opts.subtitle, cx, y, HorizAlign.CENTER, VertAlign.TOP);
			}
		}

		private void drawEmptyState(Graphics2D g) {
			g.setFont(opts.labelFont.deriveFont(Font.BOLD, 13f));
			g.setColor(lighten(opts.foregroundColor, 0.45f));
			drawLabel(g, "No data to display",
					plotBorderRect.x + plotBorderRect.width / 2,
					plotBorderRect.y + plotBorderRect.height / 2,
					HorizAlign.CENTER, VertAlign.CENTER);
		}

		private void drawLegend(Graphics2D g) {
			if (opts.legend == LegendFormat.NONE || legend.rect.width == 0) return;

			RoundRectangle2D card = new RoundRectangle2D.Float(
					legend.rect.x, legend.rect.y, legend.rect.width, legend.rect.height, 8, 8);
			g.setColor(LEGEND_BG);
			g.fill(card);
			g.setColor(opts.borderColor);
			g.setStroke(new BasicStroke(1f));
			g.draw(card);

			int x0 = legend.rect.x + opts.labelPadding;
			int x = x0;
			int cy = legend.rect.y + (legend.rect.height - legend.yCount * legend.rowH) / 2 + legend.rowH / 2;
			int i = 0;
			for (DataSeries s : dataSeriesMap.values()) {
				drawLegendEntry(g, s, x, cy);
				i++;
				if (i % legend.xCount == 0) {
					x = x0;
					cy += legend.rowH;
				} else {
					x += legend.entryWPadded;
				}
			}
		}

		private void drawLegendEntry(Graphics2D g, DataSeries s, int x, int cy) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				s.drawLegendSign(g2, x, cy, legend.signSize);
			} finally {
				g2.dispose();
			}
			g.setFont(opts.labelFont);
			g.setColor(opts.foregroundColor);
			drawLabel(g, s.displayName, x + legend.signSize + 6, cy, HorizAlign.LEFT, VertAlign.CENTER);
		}
	}

	// ---------------------------------------------------------------
	// Range
	// ---------------------------------------------------------------

	public static class Range {

		private double min;
		private double max;
		private double diff;

		public Range(double min, double max) {
			this.min = min;
			this.max = max;
			this.diff = max - min;
		}

		public Range(Range range) {
			this(range.min, range.max);
		}

		public double getMin() { return min; }
		public double getMax() { return max; }
		public double getDiff() { return diff; }

		public void setMin(double min) {
			this.min = min;
			this.diff = max - min;
		}

		public void setMax(double max) {
			this.max = max;
			this.diff = max - min;
		}

		boolean isFinite() {
			return !Double.isNaN(min) && !Double.isNaN(max)
					&& !Double.isInfinite(min) && !Double.isInfinite(max);
		}

		@Override
		public String toString() {
			return "Range [min=" + min + ", max=" + max + "]";
		}
	}

	// ---------------------------------------------------------------
	// Drawing primitives
	// ---------------------------------------------------------------

	private static void drawLabel(Graphics2D g, String s, int x, int y, HorizAlign hAlign, VertAlign vAlign) {
		if (s == null || s.isEmpty()) return;
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(s);

		if (hAlign == HorizAlign.RIGHT) x -= w;
		else if (hAlign == HorizAlign.CENTER) x -= w / 2;

		// y is the baseline by default (BOTTOM)
		if (vAlign == VertAlign.TOP) y += fm.getAscent();
		else if (vAlign == VertAlign.CENTER) y += (fm.getAscent() - fm.getDescent()) / 2;

		g.drawString(s, x, y);
	}

	/** Draws text rotated by -90 degrees, centred on (cx, cy). */
	private static void drawRotatedLabel(Graphics2D g, String s, int cx, int cy) {
		if (s == null || s.isEmpty()) return;
		AffineTransform old = g.getTransform();
		try {
			g.translate(cx, cy);
			g.rotate(-Math.PI / 2);
			FontMetrics fm = g.getFontMetrics();
			g.drawString(s, -fm.stringWidth(s) / 2, (fm.getAscent() - fm.getDescent()) / 2);
		} finally {
			g.setTransform(old);
		}
	}

	/**
	 * Draws one marker.
	 *
	 * @param x, y   the data point in pixels
	 * @param x3, y3 the left / bottom reference of the plot (used by BAR / COLUMN)
	 */
	private static void drawMarkerShape(Graphics2D g, Marker marker, double x, double y,
			double x3, double y3, int size, Color fill, Color border) {
		double half = size / 2.0;
		Shape shape;
		switch (marker) {
		case CIRCLE:
			shape = new Ellipse2D.Double(x - half, y - half, size, size);
			break;
		case SQUARE:
			shape = new Rectangle2D.Double(x - half, y - half, size, size);
			break;
		case DIAMOND: {
			double d = half * 1.25;
			Path2D.Double p = new Path2D.Double();
			p.moveTo(x, y - d);
			p.lineTo(x + d, y);
			p.lineTo(x, y + d);
			p.lineTo(x - d, y);
			p.closePath();
			shape = p;
			break;
		}
		case TRIANGLE: {
			double d = half * 1.15;
			Path2D.Double p = new Path2D.Double();
			p.moveTo(x, y - d);
			p.lineTo(x + d, y + d * 0.85);
			p.lineTo(x - d, y + d * 0.85);
			p.closePath();
			shape = p;
			break;
		}
		case CROSS: {
			g.setColor(border);
			g.setStroke(new BasicStroke(Math.max(1.5f, size / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(x - half, y - half, x + half, y + half));
			g.draw(new Line2D.Double(x - half, y + half, x + half, y - half));
			return;
		}
		case PLUS: {
			g.setColor(border);
			g.setStroke(new BasicStroke(Math.max(1.5f, size / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(x - half, y, x + half, y));
			g.draw(new Line2D.Double(x, y - half, x, y + half));
			return;
		}
		case COLUMN: {
			double top = Math.min(y, y3);
			double h = Math.abs(y3 - y);
			shape = new Rectangle2D.Double(x - half, top, size, h);
			break;
		}
		case BAR: {
			double left = Math.min(x, x3);
			double w = Math.abs(x - x3);
			shape = new Rectangle2D.Double(left, y - half, w, size);
			break;
		}
		default:
			return;
		}
		g.setColor(fill);
		g.fill(shape);
		g.setColor(border);
		g.draw(shape);
	}

	// ---------------------------------------------------------------
	// Number / date formatting
	// ---------------------------------------------------------------

	/** Formats a value for the given axis format (kept for API compatibility). */
	public static String formatDouble(double d, AxisFormat format) {
		return formatDouble(d, format, Double.NaN);
	}

	/**
	 * Formats a value; when {@code step} (the tick spacing) is known, exactly the
	 * number of decimals required by the step is shown.
	 */
	public static String formatDouble(double d, AxisFormat format, double step) {
		if (Double.isNaN(d)) return "NaN";
		if (format == null) format = AxisFormat.NUMBER;
		switch (format) {
		case TIME_HM: return String.format(Locale.US, "%tR", new Date((long) d));
		case TIME_HMS: return String.format(Locale.US, "%tT", new Date((long) d));
		case DATE: return String.format(Locale.US, "%tF", new Date((long) d));
		case DATETIME_HM: return String.format(Locale.US, "%tF %1$tR", new Date((long) d));
		case DATETIME_HMS: return String.format(Locale.US, "%tF %1$tT", new Date((long) d));
		case NUMBER_INT: return String.format(Locale.US, "%,d", Math.round(d));
		case NUMBER_KGM: return formatKGM(d, step);
		case PERCENT: // values in [0, 1] are shown as 0% .. 100%
			return formatNumber(d * 100, Double.isNaN(step) ? Double.NaN : step * 100) + "%";
		default: return formatNumber(d, step);
		}
	}

	private static String formatNumber(double d, double step) {
		if (Double.isInfinite(d)) return d > 0 ? "\u221E" : "-\u221E";
		if (Math.abs(d) < 1e-12) d = 0;
		int decimals = decimalsFor(step);
		if (decimals >= 0 && Math.abs(d) < 1e15) {
			return String.format(Locale.US, "%,." + decimals + "f", d);
		}
		if (d == Math.rint(d) && Math.abs(d) < 1e15) {
			return String.format(Locale.US, "%,d", (long) d);
		}
		if (Math.abs(d) >= 1e15 || Math.abs(d) < 1e-4) {
			return String.format(Locale.US, "%.3G", d);
		}
		BigDecimal bd = new BigDecimal(d).round(new MathContext(4)).stripTrailingZeros();
		return bd.toPlainString();
	}

	private static String formatKGM(double d, double step) {
		double a = Math.abs(d);
		if (a < 1000) return formatNumber(d, step);
		double[] div = { 1e3, 1e6, 1e9, 1e12 };
		char[] suffix = { 'K', 'M', 'G', 'T' };
		int i = 0;
		while (i < div.length - 1 && a >= div[i + 1]) i++;
		double v = d / div[i];
		int decimals = Double.isNaN(step) ? 1 : Math.min(2, Math.max(0, decimalsFor(step / div[i])));
		String s = String.format(Locale.US, "%,." + decimals + "f", v);
		if (Double.isNaN(step) && s.endsWith(".0")) s = s.substring(0, s.length() - 2);
		return s + suffix[i];
	}

	/** Number of decimals needed to print multiples of {@code step} exactly (-1 if unknown). */
	private static int decimalsFor(double step) {
		if (!(step > 0) || Double.isInfinite(step)) return -1;
		int dec = 0;
		double s = step;
		while (dec < 10 && Math.abs(s - Math.rint(s)) > 1e-6 * Math.max(1, Math.abs(s))) {
			s *= 10;
			dec++;
		}
		return dec;
	}

	// ---------------------------------------------------------------
	// Tick helpers
	// ---------------------------------------------------------------

	private static boolean isTimeFormat(AxisFormat f) {
		return f == AxisFormat.TIME_HM || f == AxisFormat.TIME_HMS || f == AxisFormat.DATE
				|| f == AxisFormat.DATETIME_HM || f == AxisFormat.DATETIME_HMS;
	}

	/** Rounds a raw step up to 1, 2 or 5 times a power of ten. */
	private static double niceStep(double raw) {
		if (!(raw > 0) || Double.isInfinite(raw)) return 1;
		double exp = Math.floor(Math.log10(raw));
		double base = Math.pow(10, exp);
		double frac = raw / base;
		double nice;
		if (frac <= 1) nice = 1;
		else if (frac <= 2) nice = 2;
		else if (frac <= 5) nice = 5;
		else nice = 10;
		return nice * base;
	}

	/** Rounds a raw step (milliseconds) up to a calendar-friendly step. */
	private static double niceTimeStep(double raw) {
		if (!(raw > 0)) return 1000;
		if (raw < 1000) return niceStep(raw);
		for (long s : TIME_STEPS_MS) {
			if (s >= raw) return s;
		}
		double years = raw / (365.0 * DAY_MS);
		return niceStep(years) * 365.0 * DAY_MS;
	}

	// ---------------------------------------------------------------
	// Coordinate mapping
	// ---------------------------------------------------------------

	private static double x2x(double x, Range xr1, Range xr2) {
		if (Double.isNaN(x)) return Double.NaN;
		return xr1.diff == 0 ? xr2.min + xr2.diff / 2 : xr2.min + (x - xr1.min) / xr1.diff * xr2.diff;
	}

	// y axis is reversed in Graphics
	private static double y2y(double y, Range yr1, Range yr2) {
		if (Double.isNaN(y)) return Double.NaN;
		return yr1.diff == 0 ? yr2.min + yr2.diff / 2 : yr2.max - (y - yr1.min) / yr1.diff * yr2.diff;
	}

	private static int toInt(double d) {
		if (Double.isNaN(d)) return 0;
		// keep far-away points in a range Java2D handles gracefully
		if (d > 1e7) return 10_000_000;
		if (d < -1e7) return -10_000_000;
		return (int) Math.round(d);
	}

	// ---------------------------------------------------------------
	// Colour / font helpers
	// ---------------------------------------------------------------

	private static Color lighten(Color c, float f) {
		return new Color(
				clamp(c.getRed() + Math.round((255 - c.getRed()) * f)),
				clamp(c.getGreen() + Math.round((255 - c.getGreen()) * f)),
				clamp(c.getBlue() + Math.round((255 - c.getBlue()) * f)),
				c.getAlpha());
	}

	private static Color darken(Color c, float f) {
		return new Color(
				clamp(Math.round(c.getRed() * f)),
				clamp(Math.round(c.getGreen() * f)),
				clamp(Math.round(c.getBlue() * f)),
				c.getAlpha());
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static String chooseFontFamily() {
		try {
			Set<String> available = new HashSet<String>(Arrays.asList(
					GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
			for (String f : new String[] { "Segoe UI", "Helvetica Neue", "DejaVu Sans", "Liberation Sans" }) {
				if (available.contains(f)) return f;
			}
		} catch (Throwable ignored) {
			// headless or font system unavailable: fall through
		}
		return Font.SANS_SERIF;
	}
}