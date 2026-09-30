package ca.pfv.spmf.gui.visuals.timeline;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * The timeline panel used by the TimelineViewer of SPMF. Displays either a
 * list of events (markers on a time axis with non-overlapping labels) or a list
 * of intervals (bars stacked in rows, grouped in bands).
 * <p>
 * Features: automatic "nice" ticks, zoom (Ctrl + wheel, anchored under the
 * cursor), hover card, sticky group gutter, HiDPI-friendly vector rendering and
 * PNG export.
 *
 * @author Philippe Fournier-Viger
 */
public class Timeline extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;

	/** Listener notified when the element under the mouse changes (null = none). */
	public interface HoverListener {
		void hovered(ElementT element);
	}

	// ---------------------------------------------------------------
	// Constants
	// ---------------------------------------------------------------

	private static final int    MARGIN_TOP       = 18;
	private static final int    MARGIN_RIGHT     = 30;
	private static final int    PLOT_PAD         = 20;
	private static final int    MIN_GUTTER       = 26;
	private static final int    EVENT_GUTTER     = 12;
	private static final int    LABEL_PAD        = 4;
	private static final int    MAX_LABEL_ROWS   = 80;
	private static final int    MAX_CONTENT_WIDTH = 40_000;
	private static final int    MAX_TICKS        = 4000;
	private static final double MIN_SCALE        = 1e-12;
	private static final double MAX_SCALE        = 1e9;
	private static final double ZOOM_FACTOR      = 1.25;

	private static final String FONT_FAMILY = chooseFontFamily();
	private static final Font   FONT_NORMAL = new Font(FONT_FAMILY, Font.PLAIN,  12);
	private static final Font   FONT_SMALL  = new Font(FONT_FAMILY, Font.PLAIN,  10);
	private static final Font   FONT_BOLD   = new Font(FONT_FAMILY, Font.BOLD,   12);

	private static final Color BACKGROUND_COLOR = Color.WHITE;
	private static final Color TEXT_COLOR       = new Color( 40,  44,  52);
	private static final Color TEXT_MUTED       = new Color(110, 115, 125);
	private static final Color AXIS_COLOR       = new Color( 70,  74,  80);
	private static final Color GRID_COLOR       = new Color(232, 234, 238);
	private static final Color BAND_COLOR       = new Color(247, 248, 250);
	private static final Color BAND_LINE        = new Color(222, 225, 230);
	private static final Color STEM_COLOR       = new Color(190, 194, 200);
	private static final Color HOVER_BORDER     = new Color( 30,  30,  30);
	private static final Color CARD_BG          = new Color(255, 255, 255, 242);
	private static final Color CARD_BORDER      = new Color(160, 165, 175);
	private static final Color CARD_SHADOW      = new Color(  0,   0,   0,  30);

	/** Colours for events (by distinct name), Tableau-10. */
	private static final Color[] EVENT_PALETTE = {
			new Color( 31, 119, 180), new Color(255, 127,  14), new Color( 44, 160,  44),
			new Color(214,  39,  40), new Color(148, 103, 189), new Color(140,  86,  75),
			new Color(227, 119, 194), new Color(127, 127, 127), new Color(188, 189,  34),
			new Color( 23, 190, 207) };

	/** Pastel colours for interval groups. */
	private static final Color[] DEFAULT_GROUP_COLORS = {
			new Color(244, 154, 154), new Color(158, 224, 158), new Color(160, 206, 240),
			new Color(250, 222, 120), new Color(178, 226, 226), new Color(248, 186, 200),
			new Color(252, 190, 120), new Color(200, 180, 235), new Color(205, 208, 214),
			new Color(250, 226, 200) };

	private static final Stroke THIN        = new BasicStroke(1f);
	private static final Stroke AXIS_STROKE = new BasicStroke(1.4f);
	private static final Stroke HOVER_STROKE= new BasicStroke(2f);
	private static final Stroke GRID_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_MITER, 10f, new float[] { 3f, 4f }, 0f);

	// ---------------------------------------------------------------
	// Data
	// ---------------------------------------------------------------

	/** Events (sorted copy) or null when this timeline shows intervals */
	private final List<EventT>    events;
	/** Intervals (sorted copy) or null when this timeline shows events */
	private final List<IntervalT> intervals;
	private final long dataMinTimestamp;
	private final long dataMaxTimestamp;
	/** Displayed range */
	private long minTimestamp;
	private long maxTimestamp;

	// ---------------------------------------------------------------
	// Options
	// ---------------------------------------------------------------

	/** Time between two ticks (0 = automatic) */
	private int     tickInterval       = 0;
	private int     tickHeight         = 8;
	private int     intervalHeight     = 18;
	private int     intervalVerticalGap = 6;
	private int     eventHeight        = 20;
	private int     eventMarkSize      = 9;
	private boolean showIntervalLabels = true;
	private boolean showEventLabels    = true;
	/** Pixels per time unit */
	private double  xScaleFactor       = 1.0;
	private double  autoScaleFactor    = 1.0;
	private Font    font;
	private String  groupLabelPrefix   = "S";
	private Color[] groupColors        = DEFAULT_GROUP_COLORS.clone();

	// ---------------------------------------------------------------
	// Layout cache
	// ---------------------------------------------------------------

	private boolean  layoutDirty  = true;
	private double   layoutScale  = Double.NaN;
	private int      gutterWidth  = EVENT_GUTTER;
	private int      rowCount     = 1;
	private int[]    eventRow     = new int[0];
	private int[]    labelWidth   = new int[0];
	private Color[]  eventColors  = new Color[0];
	private final Map<String, Color>  eventColorByName = new HashMap<String, Color>();
	private final List<Band>          bands            = new ArrayList<Band>();
	/** intervals: bar rectangle; events: label rectangle (null = not drawn) */
	private Rectangle[] bounds       = new Rectangle[0];
	/** events only: marker rectangle */
	private Rectangle[] markerBounds = new Rectangle[0];

	private ElementT hovered;
	private Point    mousePoint;
	private boolean  exporting;
	private Dimension exportSize;
	private final NumberFormat          numberFormat   =
			NumberFormat.getNumberInstance(Locale.US);
	private final List<HoverListener>   hoverListeners =
			new CopyOnWriteArrayList<HoverListener>();

	private static final class Band {
		int group;
		int firstRow;
		int lastRow;
	}

	// ---------------------------------------------------------------
	// Constructors
	// ---------------------------------------------------------------

	/**
	 * Constructor to display a list of events.
	 *
	 * @param events          a list of events (not modified)
	 * @param showEventLabels whether event names are shown
	 */
	public Timeline(List<EventT> events, boolean showEventLabels) {
		this.showEventLabels = showEventLabels;
		List<EventT> copy = new ArrayList<EventT>();
		if (events != null) for (EventT e : events) if (e != null) copy.add(e);
		Collections.sort(copy, Comparator.comparingLong(EventT::getTime));
		this.events    = copy;
		this.intervals = null;

		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (EventT e : copy) { min = Math.min(min, e.getTime()); max = Math.max(max, e.getTime()); }
		if (min > max) { min = 0; max = 1; }
		dataMinTimestamp = min;
		dataMaxTimestamp = Math.max(max, min + 1);
		minTimestamp = dataMinTimestamp;
		maxTimestamp = dataMaxTimestamp;
		assignEventColors();
		init();
	}

	/**
	 * Constructor to display a list of intervals.
	 *
	 * @param showIntervalLabels whether interval names are shown
	 * @param intervals          a list of intervals (not modified)
	 */
	public Timeline(boolean showIntervalLabels, List<IntervalT> intervals) {
		this.showIntervalLabels = showIntervalLabels;
		List<IntervalT> copy = new ArrayList<IntervalT>();
		if (intervals != null) for (IntervalT it : intervals) if (it != null) copy.add(it);
		Collections.sort(copy, Comparator.comparingInt(IntervalT::getGroup)
				.thenComparingLong(IntervalT::getStartTime)
				.thenComparingLong(IntervalT::getEndTime));
		this.intervals = copy;
		this.events    = null;

		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (IntervalT it : copy) {
			min = Math.min(min, it.getStartTime());
			max = Math.max(max, it.getEndTime());
		}
		if (min > max) { min = 0; max = 1; }
		dataMinTimestamp = min;
		dataMaxTimestamp = Math.max(max, min + 1);
		minTimestamp = dataMinTimestamp;
		maxTimestamp = dataMaxTimestamp;
		init();
	}

	private void init() {
		setFont(FONT_NORMAL);
		setBackground(BACKGROUND_COLOR);
		setOpaque(true);
		autoScaleFactor = computeAutoScale();
		xScaleFactor    = autoScaleFactor;
		installMouseHandling();
	}

	private void assignEventColors() {
		eventColors = new Color[events.size()];
		for (int i = 0; i < events.size(); i++) {
			EventT e = events.get(i);
			if (e.getColor() != null) { eventColors[i] = e.getColor(); continue; }
			Color c = eventColorByName.get(e.getName());
			if (c == null) {
				c = EVENT_PALETTE[eventColorByName.size() % EVENT_PALETTE.length];
				eventColorByName.put(e.getName(), c);
			}
			eventColors[i] = c;
		}
	}

	/**
	 * Chooses a scale so that the timeline is ~1 000 px wide, or wider if needed
	 * for readability.
	 */
	private double computeAutoScale() {
		long range = Math.max(1, maxTimestamp - minTimestamp);
		double fit = 1000.0 / range;
		double readable = 0;
		List<Long> values = new ArrayList<Long>();
		if (events != null) {
			for (int i = 1; i < events.size(); i++) {
				long gap = events.get(i).getTime() - events.get(i - 1).getTime();
				if (gap > 0) values.add(gap);
			}
			long median = median(values);
			if (median > 0) readable = 28.0 / median;
		} else {
			for (IntervalT it : intervals) if (it.getDuration() > 0) values.add(it.getDuration());
			long median = median(values);
			if (median > 0) readable = 48.0 / median;
		}
		return clampScale(Math.max(fit, readable));
	}

	private static long median(List<Long> values) {
		if (values.isEmpty()) return 0;
		Collections.sort(values);
		return values.get(values.size() / 2);
	}

	// ---------------------------------------------------------------
	// Geometry helpers
	// ---------------------------------------------------------------

	private boolean isEmpty() {
		return events != null ? events.isEmpty()
		                      : (intervals == null || intervals.isEmpty());
	}

	private int canvasWidth()  {
		return exporting && exportSize != null ? exportSize.width  : getWidth();
	}
	private int canvasHeight() {
		return exporting && exportSize != null ? exportSize.height : getHeight();
	}

	private double xOf(double t) {
		return gutterWidth + PLOT_PAD + (t - minTimestamp) * xScaleFactor;
	}
	private double timeAt(double x) {
		return minTimestamp + (x - gutterWidth - PLOT_PAD) / xScaleFactor;
	}
	private int plotRight() {
		return (int) Math.ceil(xOf(maxTimestamp)) + PLOT_PAD;
	}
	private int rowHeight() {
		return intervals != null ? intervalHeight + intervalVerticalGap : eventHeight;
	}
	private int baseZone() {
		return intervals != null ? 6 : eventMarkSize + 8;
	}
	private int axisArea() {
		return tickHeight / 2 + LABEL_PAD + getFontMetrics(FONT_SMALL).getHeight() + 8;
	}
	private int axisY() { return canvasHeight() - axisArea(); }
	private int rowsBottom(int axisY) { return axisY - baseZone(); }
	private int rowBottom(int rowsBottom, int row) { return rowsBottom - row * rowHeight(); }
	private int rowTop   (int rowsBottom, int row) { return rowsBottom - (row + 1) * rowHeight(); }

	private int contentHeight() {
		return MARGIN_TOP + Math.max(1, rowCount) * rowHeight() + baseZone() + axisArea();
	}
	private int contentWidth() { return plotRight() + MARGIN_RIGHT; }

	private double clampScale(double s) {
		long range = Math.max(1, maxTimestamp - minTimestamp);
		double maxByWidth =
				(MAX_CONTENT_WIDTH - gutterWidth - 2 * PLOT_PAD - MARGIN_RIGHT) / (double) range;
		double upper = Math.min(MAX_SCALE, Math.max(MIN_SCALE, maxByWidth));
		if (Double.isNaN(s)) return upper;
		return Math.max(MIN_SCALE, Math.min(upper, s));
	}

	// ---------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------

	private void ensureLayout(FontMetrics fm) {
		if (!layoutDirty && layoutScale == xScaleFactor) return;
		if (intervals != null) layoutIntervals(); else layoutEvents(fm);
		layoutDirty = false;
		layoutScale = xScaleFactor;
	}

	private int labelLeft(double x, int w) {
		int lx = (int) Math.round(x - w / 2.0);
		lx = Math.min(lx, plotRight() + MARGIN_RIGHT - w - 2);
		return Math.max(gutterWidth + 2, lx);
	}

	/** Assigns each event label to the lowest row where it does not overlap another. */
	private void layoutEvents(FontMetrics fm) {
		gutterWidth = EVENT_GUTTER;
		int n = events.size();
		eventRow    = new int[n];
		labelWidth  = new int[n];
		bounds      = new Rectangle[n];
		markerBounds= new Rectangle[n];
		bands.clear();

		double[] rowRight = new double[MAX_LABEL_ROWS];
		Arrays.fill(rowRight, Double.NEGATIVE_INFINITY);
		int rows = 0;
		for (int i = 0; i < n; i++) {
			EventT e = events.get(i);
			labelWidth[i] = fm.stringWidth(e.getName());
			eventRow[i]   = -1;
			if (!showEventLabels || e.getTime() < minTimestamp || e.getTime() > maxTimestamp)
				continue;
			int    lx    = labelLeft(xOf(e.getTime()), labelWidth[i]);
			double left  = lx - LABEL_PAD;
			double right = lx + labelWidth[i] + LABEL_PAD;
			for (int r = 0; r < MAX_LABEL_ROWS; r++) {
				if (rowRight[r] <= left) {
					rowRight[r] = right;
					eventRow[i] = r;
					rows = Math.max(rows, r + 1);
					break;
				}
			}
		}
		rowCount = Math.max(1, rows);
	}

	/** Assigns each interval to the first row of its group where it fits. */
	private void layoutIntervals() {
		FontMetrics bfm = getFontMetrics(FONT_BOLD);
		int maxLabel = 0;
		Set<Integer> groups = new HashSet<Integer>();
		for (IntervalT it : intervals) {
			if (groups.add(it.getGroup()))
				maxLabel = Math.max(maxLabel, bfm.stringWidth(groupLabelPrefix + it.getGroup()));
		}
		gutterWidth = Math.max(MIN_GUTTER, maxLabel + 18);

		int n = intervals.size();
		bounds      = new Rectangle[n];
		markerBounds= new Rectangle[0];
		bands.clear();

		List<Double> rowRight = new ArrayList<Double>();
		int     groupStart   = 0;
		Integer currentGroup = null;
		for (IntervalT it : intervals) {
			if (currentGroup == null || it.getGroup() != currentGroup) {
				closeBand(currentGroup, groupStart, rowRight.size());
				currentGroup = it.getGroup();
				groupStart   = rowRight.size();
			}
			if (it.getEndTime() < minTimestamp || it.getStartTime() > maxTimestamp) {
				it.layerIndex = -1;
				continue;
			}
			double xs = xOf(Math.max(it.getStartTime(), minTimestamp));
			double xe = Math.max(xs + 2, xOf(Math.min(it.getEndTime(), maxTimestamp)));
			int placed = -1;
			for (int r = groupStart; r < rowRight.size(); r++) {
				if (rowRight.get(r) + 2 <= xs) { rowRight.set(r, xe); placed = r; break; }
			}
			if (placed < 0) { placed = rowRight.size(); rowRight.add(xe); }
			it.layerIndex = placed;
		}
		closeBand(currentGroup, groupStart, rowRight.size());
		rowCount = Math.max(1, rowRight.size());
	}

	private void closeBand(Integer group, int firstRow, int endRow) {
		if (group == null || endRow <= firstRow) return;
		Band b = new Band();
		b.group    = group;
		b.firstRow = firstRow;
		b.lastRow  = endRow - 1;
		bands.add(b);
	}

	private void invalidateLayout() {
		layoutDirty = true;
		hovered     = null;
		revalidate();
		repaint();
	}

	// ---------------------------------------------------------------
	// Ticks
	// ---------------------------------------------------------------

	private long tickStep(FontMetrics smallFm) {
		long range = Math.max(1, maxTimestamp - minTimestamp);
		long step;
		if (tickInterval > 0) {
			step = tickInterval;
		} else {
			int labelW = Math.max(
					smallFm.stringWidth(numberFormat.format(minTimestamp)),
					smallFm.stringWidth(numberFormat.format(maxTimestamp)));
			double raw = (labelW + 24) / xScaleFactor;
			step = Math.max(1, (long) Math.ceil(niceStep(raw)));
		}
		if (range / step > MAX_TICKS)
			step = Math.max(1, (long) Math.ceil(niceStep((double) range / MAX_TICKS)));
		return step;
	}

	private long[] tickPositions(long step) {
		long first = Math.floorDiv(minTimestamp, step) * step;
		if (first < minTimestamp) first += step;
		if (first > maxTimestamp) return new long[0];
		int count = (int) Math.min(MAX_TICKS, (maxTimestamp - first) / step + 1);
		long[] ticks = new long[count];
		for (int k = 0; k < count; k++) ticks[k] = first + k * step;
		return ticks;
	}

	private static double niceStep(double raw) {
		if (!(raw > 0) || Double.isInfinite(raw)) return 1;
		double exp  = Math.floor(Math.log10(raw));
		double base = Math.pow(10, exp);
		double frac = raw / base;
		double nice = frac <= 1 ? 1 : frac <= 2 ? 2 : frac <= 5 ? 5 : 10;
		return nice * base;
	}

	// ---------------------------------------------------------------
	// Painting
	// ---------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			applyHints(g2);
			Rectangle vis = exporting
					? new Rectangle(0, 0, canvasWidth(), canvasHeight())
					: getVisibleRect();
			if (isEmpty()) { drawEmptyState(g2, vis); return; }
			g2.setFont(font);
			FontMetrics fm = g2.getFontMetrics();
			ensureLayout(fm);
			Rectangle clip  = g2.getClipBounds();
			int        axisY = axisY();
			long[]     ticks = tickPositions(tickStep(g2.getFontMetrics(FONT_SMALL)));

			drawBands(g2, axisY);
			drawGridAndAxis(g2, ticks, axisY);
			if (intervals != null) {
				drawIntervals(g2, fm, clip, axisY);
			} else {
				drawEvents(g2, fm, clip, axisY);
			}
			drawGutter(g2, vis, axisY);
			drawHoverCard(g2, vis);
		} finally {
			g2.dispose();
		}
	}

	private void applyHints(Graphics2D g2) {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
	}

	private void drawEmptyState(Graphics2D g2, Rectangle vis) {
		g2.setFont(FONT_BOLD);
		g2.setColor(TEXT_MUTED);
		String msg = "No data to display";
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(msg,
				vis.x + (vis.width  - fm.stringWidth(msg)) / 2,
				vis.y +  vis.height / 2);
	}

	private void drawBands(Graphics2D g2, int axisY) {
		if (bands.isEmpty()) return;
		int rb    = rowsBottom(axisY);
		int right = Math.max(plotRight() + MARGIN_RIGHT, canvasWidth());
		for (int i = 0; i < bands.size(); i++) {
			Band b      = bands.get(i);
			int  top    = rowTop   (rb, b.lastRow);
			int  bottom = rowBottom(rb, b.firstRow);
			if (i % 2 == 1) {
				g2.setColor(BAND_COLOR);
				g2.fillRect(0, top, right, bottom - top);
			}
			g2.setColor(BAND_LINE);
			g2.setStroke(THIN);
			g2.drawLine(0, top, right, top);
		}
	}

	private void drawGridAndAxis(Graphics2D g2, long[] ticks, int axisY) {
		// light vertical grid lines – drawn first, underneath everything else
		g2.setColor(GRID_COLOR);
		g2.setStroke(GRID_STROKE);
		for (long t : ticks) {
			int x = (int) Math.round(xOf(t));
			g2.drawLine(x, MARGIN_TOP, x, axisY - 1);
		}
		// axis line
		g2.setColor(AXIS_COLOR);
		g2.setStroke(AXIS_STROKE);
		g2.drawLine(gutterWidth, axisY, plotRight(), axisY);
		// ticks + labels (overlapping labels are skipped)
		g2.setStroke(THIN);
		g2.setFont(FONT_SMALL);
		FontMetrics sfm    = g2.getFontMetrics();
		int         labelY = axisY + tickHeight / 2 + LABEL_PAD + sfm.getAscent();
		int         lastRight = Integer.MIN_VALUE;
		for (long t : ticks) {
			int    x = (int) Math.round(xOf(t));
			g2.setColor(AXIS_COLOR);
			g2.drawLine(x, axisY - tickHeight / 2, x, axisY + tickHeight / 2);
			String s  = numberFormat.format(t);
			int    w  = sfm.stringWidth(s);
			int    lx = x - w / 2;
			if (lx > lastRight + 8) {
				g2.setColor(TEXT_MUTED);
				g2.drawString(s, lx, labelY);
				lastRight = lx + w;
			}
		}
	}

	// ---- events ----------------------------------------------------

	/**
	 * Draws events in four passes so that no stem or grid line ever crosses a
	 * label:
	 * <ol>
	 *   <li>Geometry (bounds arrays)</li>
	 *   <li>Stems</li>
	 *   <li>Label boxes with opaque background</li>
	 *   <li>Axis markers</li>
	 * </ol>
	 * The hovered element is repeated on top of everything.
	 */
	private void drawEvents(Graphics2D g2, FontMetrics fm, Rectangle clip, int axisY) {
		int rb   = rowsBottom(axisY);
		int half = eventMarkSize / 2;
		int n    = events.size();

		int[]     xs      = new int[n];
		boolean[] visible = new boolean[n];
		int hoveredIndex  = -1;

		// pass 0: geometry
		for (int i = 0; i < n; i++) {
			EventT e = events.get(i);
			long   t = e.getTime();
			if (t < minTimestamp || t > maxTimestamp) {
				bounds[i]      = null;
				markerBounds[i]= null;
				continue;
			}
			int x = (int) Math.round(xOf(t));
			xs[i] = x;
			markerBounds[i] = new Rectangle(
					x - half - 3, axisY - half - 3, eventMarkSize + 6, eventMarkSize + 6);
			int row = eventRow[i];
			if (row >= 0) {
				int w  = labelWidth[i];
				int lx = labelLeft(x, w);
				int baseline = rowBottom(rb, row) - 4;
				bounds[i] = new Rectangle(
						lx - LABEL_PAD,
						baseline - fm.getAscent() - 2,
						w + 2 * LABEL_PAD,
						fm.getHeight() + 3);
			} else {
				bounds[i] = null;
			}
			if (e == hovered) { hoveredIndex = i; continue; }
			visible[i] = clip == null
					|| clip.intersects(markerBounds[i])
					|| (bounds[i] != null && clip.intersects(bounds[i]))
					|| (x >= clip.x - 2 && x <= clip.x + clip.width + 2);
		}

		// pass 1: stems (below everything else)
		for (int i = 0; i < n; i++) {
			if (visible[i] && bounds[i] != null)
				drawEventStem(g2, i, xs[i], axisY, false);
		}
		// pass 2: label boxes with opaque background (cover grid lines and stems)
		for (int i = 0; i < n; i++) {
			if (visible[i] && bounds[i] != null)
				drawEventLabel(g2, fm, i, false);
		}
		// pass 3: markers on the axis (on top of labels if they overlap)
		for (int i = 0; i < n; i++) {
			if (visible[i])
				drawEventMarker(g2, i, xs[i], axisY, false);
		}
		// hovered element drawn last, always on top
		if (hoveredIndex >= 0) {
			if (bounds[hoveredIndex] != null) {
				drawEventStem (g2, hoveredIndex, xs[hoveredIndex], axisY, true);
				drawEventLabel(g2, fm, hoveredIndex, true);
			}
			drawEventMarker(g2, hoveredIndex, xs[hoveredIndex], axisY, true);
		}
	}

	/** Draws the thin vertical line from the axis marker up to the label box. */
	private void drawEventStem(Graphics2D g2, int i, int x, int axisY, boolean hov) {
		Rectangle lb   = bounds[i];
		int        half = eventMarkSize / 2;
		g2.setColor(hov ? eventColors[i] : STEM_COLOR);
		g2.setStroke(hov ? HOVER_STROKE : THIN);
		g2.draw(new Line2D.Double(x + 0.5, axisY - half - 1, x + 0.5, lb.y + lb.height));
	}

	/**
	 * Draws the event label with an opaque background rectangle so that grid
	 * lines and stems drawn earlier never show through the text.
	 */
	private void drawEventLabel(Graphics2D g2, FontMetrics fm, int i, boolean hov) {
		Rectangle lb = bounds[i];
		Color      c  = eventColors[i];
		RoundRectangle2D r =
				new RoundRectangle2D.Double(lb.x, lb.y, lb.width, lb.height, 6, 6);
		// opaque background – always paint it, even in the normal (non-hover) case
		g2.setColor(hov ? lighten(c, 0.75f) : getBackground());
		g2.fill(r);
		if (hov) {
			g2.setColor(c);
			g2.setStroke(THIN);
			g2.draw(r);
		}
		g2.setFont(hov ? FONT_BOLD : font);
		g2.setColor(TEXT_COLOR);
		g2.drawString(events.get(i).getName(), lb.x + LABEL_PAD, lb.y + 2 + fm.getAscent());
	}

	/** Draws the circular marker centred on the axis. */
	private void drawEventMarker(Graphics2D g2, int i, int x, int axisY, boolean hov) {
		Color  c    = eventColors[i];
		double size = hov ? eventMarkSize + 3 : eventMarkSize;
		double hs   = size / 2;
		Ellipse2D m = new Ellipse2D.Double(x - hs, axisY - hs, size, size);
		// white halo separates the marker from the axis line
		g2.setColor(getBackground());
		g2.setStroke(new BasicStroke(3f));
		g2.draw(m);
		g2.setColor(c);
		g2.fill(m);
		g2.setColor(hov ? HOVER_BORDER : darken(c, 0.7f));
		g2.setStroke(hov ? HOVER_STROKE : THIN);
		g2.draw(m);
	}

	// ---- intervals -------------------------------------------------

	private void drawIntervals(Graphics2D g2, FontMetrics fm, Rectangle clip, int axisY) {
		int rb = rowsBottom(axisY);
		int hoveredIndex = -1;
		for (int i = 0; i < intervals.size(); i++) {
			IntervalT it = intervals.get(i);
			int r = it.layerIndex;
			if (r < 0) { bounds[i] = null; continue; }
			double xs = xOf(Math.max(it.getStartTime(), minTimestamp));
			double xe = Math.max(xs + 2, xOf(Math.min(it.getEndTime(), maxTimestamp)));
			int y    = rowTop(rb, r) + intervalVerticalGap / 2;
			Rectangle rect = new Rectangle(
					(int) Math.round(xs), y,
					(int) Math.round(xe - xs), intervalHeight);
			bounds[i] = rect;
			if (it == hovered) { hoveredIndex = i; continue; }
			if (clip != null && !clip.intersects(rect)) continue;
			drawSingleInterval(g2, fm, i, false);
		}
		if (hoveredIndex >= 0) drawSingleInterval(g2, fm, hoveredIndex, true);
	}

	private Color intervalColor(IntervalT it) {
		if (it.getColor() != null) return it.getColor();
		return groupColors[Math.floorMod(it.getGroup(), groupColors.length)];
	}

	private void drawSingleInterval(Graphics2D g2, FontMetrics fm, int i, boolean hov) {
		IntervalT it  = intervals.get(i);
		Rectangle r   = bounds[i];
		Color     c   = intervalColor(it);
		double    arc = Math.min(6, Math.min(r.width, r.height) / 2.0);
		RoundRectangle2D shape =
				new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, arc, arc);
		g2.setPaint(new GradientPaint(
				r.x, r.y,            lighten(c, hov ? 0.45f : 0.3f),
				r.x, r.y + r.height, hov ? lighten(c, 0.1f) : c));
		g2.fill(shape);
		g2.setColor(hov ? HOVER_BORDER : darken(c, 0.62f));
		g2.setStroke(hov ? HOVER_STROKE : THIN);
		g2.draw(shape);

		if (showIntervalLabels && r.width >= 14) {
			String text = ellipsize(it.getName(), fm, r.width - 8);
			if (!text.isEmpty() && !"\u2026".equals(text)) {
				g2.setFont(hov ? FONT_BOLD : font);
				g2.setColor(TEXT_COLOR);
				int tw = g2.getFontMetrics().stringWidth(text);
				int tx = r.x + (r.width - tw) / 2;
				int ty = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
				g2.drawString(text, tx, ty);
			}
		}
	}

	// ---- gutter / hover card ---------------------------------------

	/** Sticky left gutter with group names (stays visible when scrolling horizontally). */
	private void drawGutter(Graphics2D g2, Rectangle vis, int axisY) {
		int gx = vis.x;
		g2.setColor(getBackground());
		g2.fillRect(gx, 0, gutterWidth, canvasHeight());
		if (!bands.isEmpty()) {
			int rb = rowsBottom(axisY);
			g2.setFont(FONT_BOLD);
			FontMetrics bfm = g2.getFontMetrics();
			for (int i = 0; i < bands.size(); i++) {
				Band b      = bands.get(i);
				int  top    = rowTop   (rb, b.lastRow);
				int  bottom = rowBottom(rb, b.firstRow);
				if (i % 2 == 1) {
					g2.setColor(BAND_COLOR);
					g2.fillRect(gx, top, gutterWidth, bottom - top);
				}
				g2.setColor(BAND_LINE);
				g2.setStroke(THIN);
				g2.drawLine(gx, top, gx + gutterWidth, top);
				String label = groupLabelPrefix + b.group;
				int    w     = bfm.stringWidth(label);
				g2.setColor(TEXT_MUTED);
				g2.drawString(label,
						gx + (gutterWidth - w) / 2,
						(top + bottom) / 2 + (bfm.getAscent() - bfm.getDescent()) / 2);
			}
			g2.setColor(BAND_LINE);
			g2.drawLine(gx + gutterWidth - 1, MARGIN_TOP, gx + gutterWidth - 1, axisY);
		}
		if (vis.x > 0 && !exporting) {
			g2.setPaint(new GradientPaint(
					gx + gutterWidth,      0, new Color(0, 0, 0, 26),
					gx + gutterWidth + 10, 0, new Color(0, 0, 0,  0)));
			g2.fillRect(gx + gutterWidth, MARGIN_TOP, 10,
					Math.max(0, axisY - MARGIN_TOP));
		}
	}

	private void drawHoverCard(Graphics2D g2, Rectangle vis) {
		if (exporting || hovered == null || mousePoint == null) return;

		FontMetrics bf = g2.getFontMetrics(FONT_BOLD);
		FontMetrics nf = g2.getFontMetrics(FONT_NORMAL);
		String head = hovered.getName().isEmpty() ? "(unnamed)" : hovered.getName();
		head = ellipsize(head, bf, 280);
		List<String> lines = new ArrayList<String>();
		Color swatch;
		if (hovered instanceof IntervalT) {
			IntervalT it = (IntervalT) hovered;
			lines.add("Start: "    + numberFormat.format(it.getStartTime()));
			lines.add("End: "      + numberFormat.format(it.getEndTime()));
			lines.add("Duration: " + numberFormat.format(it.getDuration()));
			lines.add("Group: "    + groupLabelPrefix + it.getGroup());
			swatch = intervalColor(it);
		} else {
			EventT e = (EventT) hovered;
			lines.add("Time: " + numberFormat.format(e.getTime()));
			int sameTime = 0;
			for (EventT o : events) if (o.getTime() == e.getTime()) sameTime++;
			if (sameTime > 1) lines.add(sameTime + " events at this time");
			swatch = e.getColor() != null ? e.getColor() : eventColorByName.get(e.getName());
			if (swatch == null) swatch = EVENT_PALETTE[0];
		}

		int pad = 10, sw = 10;
		int w = bf.stringWidth(head) + sw + 6;
		for (String l : lines) w = Math.max(w, nf.stringWidth(l));
		w += pad * 2;
		int h = pad + bf.getHeight() + 4 + lines.size() * nf.getHeight() + pad;

		int x = mousePoint.x + 16, y = mousePoint.y + 18;
		if (x + w > vis.x + vis.width  - 4) x = mousePoint.x - w - 12;
		if (y + h > vis.y + vis.height - 4) y = mousePoint.y - h - 12;
		x = Math.max(vis.x + 4, x);
		y = Math.max(vis.y + 4, y);

		g2.setColor(CARD_SHADOW);
		g2.fill(new RoundRectangle2D.Float(x + 2, y + 3, w, h, 10, 10));
		RoundRectangle2D card = new RoundRectangle2D.Float(x, y, w, h, 10, 10);
		g2.setColor(CARD_BG);    g2.fill(card);
		g2.setColor(CARD_BORDER); g2.setStroke(THIN); g2.draw(card);

		int ty = y + pad + bf.getAscent();
		g2.setColor(swatch);
		g2.fillRoundRect(x + pad, ty - sw + 1, sw, sw, 3, 3);
		g2.setColor(darken(swatch, 0.7f));
		g2.drawRoundRect(x + pad, ty - sw + 1, sw, sw, 3, 3);
		g2.setFont(FONT_BOLD);  g2.setColor(TEXT_COLOR);
		g2.drawString(head, x + pad + sw + 6, ty);
		g2.setFont(FONT_NORMAL); g2.setColor(TEXT_MUTED);
		int ly = ty + 4 + nf.getHeight();
		for (String l : lines) { g2.drawString(l, x + pad, ly); ly += nf.getHeight(); }
	}

	// ---------------------------------------------------------------
	// Mouse handling / hover
	// ---------------------------------------------------------------

	private void installMouseHandling() {
		MouseAdapter adapter = new MouseAdapter() {
			@Override public void mouseMoved   (MouseEvent e) { updateHover(e.getPoint()); }
			@Override public void mouseDragged (MouseEvent e) { updateHover(e.getPoint()); }
			@Override public void mouseExited  (MouseEvent e) { updateHover(null); }

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (e.isControlDown()) {
					zoom(-e.getWheelRotation(), e.getX());
					e.consume();
					return;
				}
				Container parent = getParent();
				if (parent != null)
					parent.dispatchEvent(
							SwingUtilities.convertMouseEvent(Timeline.this, e, parent));
			}
		};
		addMouseListener(adapter);
		addMouseMotionListener(adapter);
		addMouseWheelListener(adapter);
	}

	private void updateHover(Point p) {
		mousePoint = p;
		ElementT el      = elementAt(p);
		boolean  changed = el != hovered;
		hovered = el;
		setCursor(el != null
				? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
				: Cursor.getDefaultCursor());
		if (changed) for (HoverListener l : hoverListeners) l.hovered(el);
		if (changed || el != null) repaint(getVisibleRect());
	}

	/** @return the element drawn at the given panel position, or null */
	public ElementT elementAt(Point p) {
		if (p == null || layoutDirty) return null;
		if (intervals != null) {
			for (int i = 0; i < intervals.size() && i < bounds.length; i++)
				if (bounds[i] != null && bounds[i].contains(p)) return intervals.get(i);
		} else {
			for (int i = 0; i < events.size() && i < bounds.length; i++)
				if (bounds[i] != null && bounds[i].contains(p)) return events.get(i);
			for (int i = 0; i < events.size() && i < markerBounds.length; i++)
				if (markerBounds[i] != null && markerBounds[i].contains(p)) return events.get(i);
		}
		return null;
	}

	public ElementT getHoveredElement() { return hovered; }

	public void addHoverListener   (HoverListener l) { if (l != null) hoverListeners.add(l); }
	public void removeHoverListener(HoverListener l) { hoverListeners.remove(l); }

	// ---------------------------------------------------------------
	// Zoom
	// ---------------------------------------------------------------

	/** Zooms in one step around the centre of the visible area. */
	public void zoomIn() {
		Rectangle vis = getVisibleRect();
		zoom(1, vis.x + vis.width / 2);
	}

	/** Zooms out one step around the centre of the visible area. */
	public void zoomOut() {
		Rectangle vis = getVisibleRect();
		zoom(-1, vis.x + vis.width / 2);
	}

	/**
	 * Changes the X scale by one step, keeping the time under {@code anchorX}
	 * (panel coordinate) at the same on-screen position.
	 */
	public void zoom(int direction, int anchorX) {
		if (direction == 0) return;
		double old = xScaleFactor;
		double nw  = clampScale(direction > 0 ? old * ZOOM_FACTOR : old / ZOOM_FACTOR);
		if (nw == old) return;

		final JViewport vp         = (JViewport) SwingUtilities
				.getAncestorOfClass(JViewport.class, this);
		final double    anchorTime = timeAt(anchorX);
		final int       viewOffset = vp != null ? anchorX - vp.getViewPosition().x : 0;

		setXScaleFactor(nw);

		if (vp != null) {
			SwingUtilities.invokeLater(() -> {
				Container sp = vp.getParent();
				if (sp != null) sp.validate(); else vp.validate();
				int newAnchor = (int) Math.round(xOf(anchorTime));
				int maxX = Math.max(0, getWidth() - vp.getExtentSize().width);
				int nx   = Math.max(0, Math.min(newAnchor - viewOffset, maxX));
				vp.setViewPosition(new Point(nx, vp.getViewPosition().y));
			});
		}
	}

	/** Chooses the scale so that the whole time range fits in the visible width. */
	public void fitToWidth() {
		Rectangle vis       = getVisibleRect();
		int       available = vis.width - gutterWidth - 2 * PLOT_PAD - MARGIN_RIGHT;
		if (available <= 0) return;
		setXScaleFactor(available / (double) Math.max(1, maxTimestamp - minTimestamp));
	}

	// ---------------------------------------------------------------
	// Export
	// ---------------------------------------------------------------

	/**
	 * Renders the whole timeline (no hover card) to an image.
	 *
	 * @param scale pixel multiplier (2.0 for HiDPI / Retina quality)
	 */
	public BufferedImage renderToImage(double scale) {
		Dimension pref = getPreferredSize();
		int w = getWidth()  > 0 ? Math.max(getWidth(),  pref.width)  : pref.width;
		int h = getHeight() > 0 ? Math.max(getHeight(), pref.height) : pref.height;
		double s = Math.max(1.0, Math.min(scale, 16000.0 / Math.max(w, h)));
		BufferedImage img = new BufferedImage(
				(int) Math.ceil(w * s), (int) Math.ceil(h * s),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		exporting  = true;
		exportSize = new Dimension(w, h);
		try {
			g.scale(s, s);
			g.setColor(getBackground());
			g.fillRect(0, 0, w, h);
			paintComponent(g);
		} finally {
			exporting  = false;
			exportSize = null;
			g.dispose();
		}
		return img;
	}

	// ---------------------------------------------------------------
	// Sizing / Scrollable
	// ---------------------------------------------------------------

	@Override
	public Dimension getPreferredSize() {
		if (isEmpty()) return new Dimension(600, 200);
		if (font == null) font = FONT_NORMAL;
		ensureLayout(getFontMetrics(font));
		return new Dimension(contentWidth(), contentHeight());
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		Dimension d = getPreferredSize();
		return new Dimension(Math.min(d.width, 960), Math.min(Math.max(d.height, 220), 600));
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return 24;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return orientation == SwingConstants.HORIZONTAL
				? Math.max(1, visibleRect.width - gutterWidth - 20)
				: visibleRect.height;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		Container p = getParent();
		return p instanceof JViewport && p.getWidth() > getPreferredSize().width;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		Container p = getParent();
		return p instanceof JViewport && p.getHeight() > getPreferredSize().height;
	}

	@Override
	public void addNotify() {
		super.addNotify();
		Container p = getParent();
		if (p instanceof JViewport) {
			// sticky gutter: blit scrolling would smear it
			((JViewport) p).setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
		}
	}

	// ---------------------------------------------------------------
	// Getters / setters (API kept from the original class)
	// ---------------------------------------------------------------

	@Override
	public void setFont(Font f) {
		super.setFont(f);
		if (f != null) font = f;
		layoutDirty = true;
		if (isDisplayable()) { revalidate(); repaint(); }
	}

	public int  getTickHeight()    { return tickHeight; }
	public void setTickHeight(int tickHeight) {
		this.tickHeight = Math.max(0, tickHeight); invalidateLayout();
	}

	public int  getIntervalHeight() { return intervalHeight; }
	public void setIntervalHeight(int intervalHeight) {
		this.intervalHeight = Math.max(2, intervalHeight); invalidateLayout();
	}

	public int  getIntervalVerticalGap() { return intervalVerticalGap; }
	public void setIntervalVerticalGap(int intervalVerticalGap) {
		this.intervalVerticalGap = Math.max(0, intervalVerticalGap); invalidateLayout();
	}

	public int  getEventHeight() { return eventHeight; }
	public void setEventHeight(int eventHeight) {
		this.eventHeight = Math.max(8, eventHeight); invalidateLayout();
	}

	public boolean isShowIntervalLabels() { return showIntervalLabels; }
	public void    setShowIntervalLabels(boolean show) {
		showIntervalLabels = show; repaint();
	}
	public void toggleShowIntervalLabels() { setShowIntervalLabels(!showIntervalLabels); }

	public boolean isShowEventLabels() { return showEventLabels; }
	public void    setShowEventLabels(boolean show) {
		showEventLabels = show; invalidateLayout();
	}
	public void toggleShowEventLabels() { setShowEventLabels(!showEventLabels); }

	/** @return pixels per time unit */
	public double getXScaleFactor()    { return xScaleFactor; }
	/** @return the scale chosen automatically for this data */
	public double getAutoScaleFactor() { return autoScaleFactor; }

	/** Sets the number of pixels per time unit. Fires "xScaleFactor" property change. */
	public void setXScaleFactor(double scale) {
		double clamped = clampScale(scale);
		if (clamped == xScaleFactor) return;
		double old = xScaleFactor;
		xScaleFactor = clamped;
		hovered = null;
		firePropertyChange("xScaleFactor", old, clamped);
		revalidate();
		repaint();
	}

	public long getMinTimestamp()     { return minTimestamp; }
	public long getMaxTimestamp()     { return maxTimestamp; }
	public long getDataMinTimestamp() { return dataMinTimestamp; }
	public long getDataMaxTimestamp() { return dataMaxTimestamp; }

	public void setMinTimestamp(long minTimestamp) {
		if (minTimestamp >= maxTimestamp || minTimestamp == this.minTimestamp) return;
		this.minTimestamp = minTimestamp; rangeChanged();
	}
	public void setMaxTimestamp(long maxTimestamp) {
		if (maxTimestamp <= minTimestamp || maxTimestamp == this.maxTimestamp) return;
		this.maxTimestamp = maxTimestamp; rangeChanged();
	}
	public void resetTimeRange() {
		minTimestamp = dataMinTimestamp;
		maxTimestamp = dataMaxTimestamp;
		rangeChanged();
	}

	private void rangeChanged() {
		xScaleFactor = clampScale(xScaleFactor);
		firePropertyChange("range", null, new long[] { minTimestamp, maxTimestamp });
		invalidateLayout();
	}

	public int  getTickInterval() { return tickInterval; }
	public void setTickInterval(int tickInterval) {
		this.tickInterval = Math.max(0, tickInterval); repaint();
	}

	public String getGroupLabelPrefix() { return groupLabelPrefix; }
	public void   setGroupLabelPrefix(String prefix) {
		groupLabelPrefix = prefix == null ? "" : prefix; invalidateLayout();
	}

	public void setGroupColors(Color[] colors) {
		if (colors != null && colors.length > 0) { groupColors = colors.clone(); repaint(); }
	}

	public boolean          isEventTimeline() { return events != null; }
	public List<EventT>     getEvents()    {
		return events    == null ? Collections.<EventT>emptyList()
		                        : Collections.unmodifiableList(events);
	}
	public List<IntervalT>  getIntervals() {
		return intervals == null ? Collections.<IntervalT>emptyList()
		                        : Collections.unmodifiableList(intervals);
	}
	public int getGroupCount() { return bands.size(); }

	// ---------------------------------------------------------------
	// Utilities
	// ---------------------------------------------------------------

	private static String ellipsize(String s, FontMetrics fm, int maxW) {
		if (s == null || maxW <= 0) return "";
		if (fm.stringWidth(s) <= maxW) return s;
		String ell = "\u2026";
		int    ew  = fm.stringWidth(ell);
		int    end = s.length();
		while (end > 0 && fm.stringWidth(s.substring(0, end)) + ew > maxW) end--;
		return end <= 0 ? ell : s.substring(0, end) + ell;
	}

	private static Color lighten(Color c, float f) {
		return new Color(
				clamp(c.getRed()   + Math.round((255 - c.getRed())   * f)),
				clamp(c.getGreen() + Math.round((255 - c.getGreen()) * f)),
				clamp(c.getBlue()  + Math.round((255 - c.getBlue())  * f)));
	}

	private static Color darken(Color c, float f) {
		return new Color(
				clamp(Math.round(c.getRed()   * f)),
				clamp(Math.round(c.getGreen() * f)),
				clamp(Math.round(c.getBlue()  * f)));
	}

	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

	private static String chooseFontFamily() {
		try {
			Set<String> available = new HashSet<String>(Arrays.asList(
					GraphicsEnvironment.getLocalGraphicsEnvironment()
					                   .getAvailableFontFamilyNames()));
			for (String f : new String[] {
					"Segoe UI", "Helvetica Neue", "DejaVu Sans", "Liberation Sans" }) {
				if (available.contains(f)) return f;
			}
		} catch (Throwable ignored) { /* headless */ }
		return Font.SANS_SERIF;
	}
}