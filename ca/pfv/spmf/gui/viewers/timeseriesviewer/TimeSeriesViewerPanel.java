package ca.pfv.spmf.gui.viewers.timeseriesviewer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.algorithms.timeseries.TimeSeries;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.visuals.plot.Plot;
import ca.pfv.spmf.gui.visuals.plot.Plot.AxisFormat;
import ca.pfv.spmf.gui.visuals.plot.Plot.LegendFormat;
import ca.pfv.spmf.gui.visuals.plot.Plot.Line;
import ca.pfv.spmf.gui.visuals.plot.Plot.Marker;
import ca.pfv.spmf.gui.visuals.plot.Plot.PointHit;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * The panel of the time series viewer. The chart is drawn as vectors directly
 * on the component (crisp on HiDPI screens), the plot is rebuilt only when the
 * data or an option changes, and the series under the mouse is found
 * geometrically (no screen-pixel probing).
 *
 * @author Philippe Fournier-Viger, 2016
 */
public class TimeSeriesViewerPanel extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;

	/** Max pixel distance between the mouse and a data point to consider it hovered */
	private static final int HOVER_RADIUS = 12;
	/** Upper bound for the canvas size when zooming in (pixels) */
	private static final int MAX_CANVAS_SIZE = 30_000;
	/** Marker shapes cycled when the palette is exhausted */
	private static final Marker[] MARKER_TYPES = {
			Marker.CIRCLE, Marker.DIAMOND, Marker.SQUARE, Marker.TRIANGLE };

	/** The time series to be displayed */
	private final List<TimeSeries> multipleTimeSeries;
	/** Colour of each time series */
	private final Color[] multipleTimeSeriesColors;
	/** Unique name used for each series in the plot (names may repeat in the input) */
	private final String[] seriesKeys;
	private final Map<String, Color> colorByKey = new HashMap<String, Color>();

	/** The plot (rebuilt lazily when {@link #plotDirty}) */
	private Plot plot;
	private boolean plotDirty = true;

	/** Logical canvas size (preferred size); zoom changes these */
	int width;
	int height;
	int originalWidth;
	int originalHeigth;

	boolean drawTheGrid = true;
	private int seriesLineWidth = 1;
	private int markerSize = 5;
	LegendFormat legendFormat = LegendFormat.BOTTOM;

	private PointHit hover;
	private final List<TimeSeriesViewerPanelListener> listeners = new ArrayList<TimeSeriesViewerPanelListener>();

	public TimeSeriesViewerPanel(List<TimeSeries> timeSeries) {
		this.multipleTimeSeries = timeSeries != null ? timeSeries : new ArrayList<TimeSeries>();

		// ---- colours and unique keys ------------------------------------
		int n = multipleTimeSeries.size();
		multipleTimeSeriesColors = new Color[n];
		seriesKeys = new String[n];
		Set<String> used = new HashSet<String>();
		for (int i = 0; i < n; i++) {
			multipleTimeSeriesColors[i] = Plot.DEFAULT_PALETTE[i % Plot.DEFAULT_PALETTE.length];
			String base = multipleTimeSeries.get(i).getName();
			if (base == null || base.isEmpty()) base = "series " + (i + 1);
			String key = base;
			int k = 2;
			while (!used.add(key)) key = base + " (" + k++ + ")";
			seriesKeys[i] = key;
			colorByKey.put(key, multipleTimeSeriesColors[i]);
		}

		// ---- canvas size --------------------------------------------------
		int maxLength = 0;
		for (TimeSeries ts : multipleTimeSeries) {
			maxLength = Math.max(maxLength, ts.size());
		}
		originalWidth = width = Math.min(MAX_CANVAS_SIZE, 400 + maxLength * 30);
		originalHeigth = height = 370 + n * 10;

		setBackground(Color.WHITE);
		setOpaque(true);
		setPreferredSize(new Dimension(width, height));
		installMouseHandling();
	}

	// ---------------------------------------------------------------
	// Plot construction
	// ---------------------------------------------------------------

	private Plot buildPlot() {
		Plot p = Plot.plot(Plot.plotOpts()
				.width(Math.max(1, getWidth() > 0 ? getWidth() : width))
				.height(Math.max(1, getHeight() > 0 ? getHeight() : height))
				.bgColor(Color.WHITE)
				.plotPadding(6)
				.legend(legendFormat));

		Line lineType = seriesLineWidth > 0 ? Line.SOLID : Line.NONE;

		for (int i = 0; i < multipleTimeSeries.size(); i++) {
			TimeSeries ts = multipleTimeSeries.get(i);
			int size = ts.size();
			double[] x = new double[size];
			double[] y = new double[size];
			for (int j = 0; j < size; j++) {
				double v = ts.get(j);
				x[j] = j;
				// missing values (encoded as -infinity) become gaps in the line
				y[j] = (v == Double.NEGATIVE_INFINITY) ? Double.NaN : v;
			}

			Marker marker = markerSize > 0
					? MARKER_TYPES[(i / Plot.DEFAULT_PALETTE.length) % MARKER_TYPES.length]
					: Marker.NONE;

			p.series(seriesKeys[i], Plot.data().xy(x, y), Plot.seriesOpts()
					.color(multipleTimeSeriesColors[i])
					.line(lineType)
					.lineWidth(Math.max(1, seriesLineWidth))
					.marker(marker)
					.markerSize(Math.max(1, markerSize)));
		}

		p.xAxis("x", Plot.axisOpts().format(AxisFormat.NUMBER_INT).title("Time").gridLines(drawTheGrid))
		 .yAxis("y", Plot.axisOpts().format(AxisFormat.NUMBER).title("Value").niceRange(true).gridLines(drawTheGrid));
		return p;
	}

	private Plot ensurePlot() {
		if (plot == null || plotDirty) {
			plot = buildPlot();
			plotDirty = false;
		}
		return plot;
	}

	private void invalidatePlot() {
		plotDirty = true;
		hover = null;
		revalidate();
		repaint();
	}

	/** @return the plot currently displayed (layout valid after the first paint) */
	public Plot getPlot() {
		return ensurePlot();
	}

	// ---------------------------------------------------------------
	// Painting (vector, directly on the component's graphics)
	// ---------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			Plot p = ensurePlot();
			p.resize(getWidth(), getHeight(), g2);
			p.drawChartOnGraphics2D(g2);     // inherits the HiDPI transform -> crisp
			drawHoverOverlay(g2, p);
		} finally {
			g2.dispose();
		}
	}

	private void drawHoverOverlay(Graphics2D g2, Plot p) {
		if (hover == null) return;
		Rectangle r = p.getPlotAreaRectangle();
		Graphics2D g = (Graphics2D) g2.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			Rectangle clip = new Rectangle(r);
			clip.grow(markerSize + 6, markerSize + 6);
			g.clip(clip);

			// crosshair
			g.setColor(new Color(0, 0, 0, 70));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
					new float[] { 3f, 3f }, 0f));
			g.draw(new Line2D.Double(r.x, hover.pixelY, r.x + r.width, hover.pixelY));
			g.draw(new Line2D.Double(hover.pixelX, r.y, hover.pixelX, r.y + r.height));

			// ring around the point
			Color c = colorByKey.get(hover.seriesName);
			if (c == null) c = Color.DARK_GRAY;
			double rad = Math.max(markerSize, 4) + 3;
			Ellipse2D ring = new Ellipse2D.Double(hover.pixelX - rad, hover.pixelY - rad, 2 * rad, 2 * rad);
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
			g.fill(ring);
			g.setColor(c);
			g.setStroke(new BasicStroke(1.6f));
			g.draw(ring);
		} finally {
			g.dispose();
		}
	}

	// ---------------------------------------------------------------
	// Mouse handling
	// ---------------------------------------------------------------

	private void installMouseHandling() {
		MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				handleMouse(e.getPoint());
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				handleMouse(e.getPoint());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				handleMouse(null);
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (e.isControlDown()) {
					if (e.getWheelRotation() < 0) increaseZoom();
					else decreaseZoom();
					e.consume();
					return;
				}
				Container parent = getParent();
				if (parent != null) {
					parent.dispatchEvent(SwingUtilities.convertMouseEvent(TimeSeriesViewerPanel.this, e, parent));
				}
			}
		};
		addMouseListener(adapter);
		addMouseMotionListener(adapter);
		addMouseWheelListener(adapter);
	}

	private void handleMouse(Point pt) {
		if (plot == null || plotDirty) return; // not painted yet
		Rectangle area = plot.getPlotRectangle();

		if (pt == null || !area.contains(pt)) {
			setCursor(Cursor.getDefaultCursor());
			if (hover != null) {
				hover = null;
				repaint();
			}
			for (TimeSeriesViewerPanelListener l : listeners) {
				l.notifyMouseOutOfChart();
			}
			return;
		}

		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		double chartX = plot.pixelToX(pt.x);
		double chartY = plot.pixelToY(pt.y);

		PointHit hit = plot.nearestPoint(pt.x, pt.y, HOVER_RADIUS);
		String name = "";
		Color color = Color.BLACK;
		if (hit != null) {
			name = hit.seriesName + "   [t = " + (long) hit.x + ", value = "
					+ String.format(Locale.US, "%.4g", hit.y) + "]";
			Color c = colorByKey.get(hit.seriesName);
			if (c != null) color = c;
		}

		for (TimeSeriesViewerPanelListener l : listeners) {
			l.notifyOfNewMousePosition(chartX, chartY, color, name);
		}

		boolean changed = (hit == null) != (hover == null)
				|| (hit != null && (hit.index != hover.index || !hit.seriesName.equals(hover.seriesName)));
		if (changed) {
			hover = hit;
			repaint();
		}
	}

	// ---------------------------------------------------------------
	// Zoom
	// ---------------------------------------------------------------

	public void increaseZoom() {
		if (width * 2 > MAX_CANVAS_SIZE || height * 2 > MAX_CANVAS_SIZE) return;
		width *= 2;
		height *= 2;
		invalidatePlot();
	}

	public void decreaseZoom() {
		if (width >= originalWidth) {
			width /= 2;
			height /= 2;
			invalidatePlot();
		}
	}

	/** Restores the initial size (the chart then fills the window). */
	public void resetZoom() {
		width = originalWidth;
		height = originalHeigth;
		invalidatePlot();
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(width, height);
	}

	// ---- Scrollable: fill the viewport when it is larger than the canvas ----

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return 24;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return orientation == SwingConstants.HORIZONTAL ? visibleRect.width : visibleRect.height;
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

	// ---------------------------------------------------------------
	// Export / print
	// ---------------------------------------------------------------

	/** Renders the chart, as currently displayed, to an image ({@code scale} = 2 for HiDPI). */
	public BufferedImage renderImage(double scale) {
		Plot p = ensurePlot();
		int w = Math.max(1, getWidth() > 0 ? getWidth() : width);
		int h = Math.max(1, getHeight() > 0 ? getHeight() : height);
		// avoid gigantic images for very long series
		double s = Math.min(scale, 16000.0 / Math.max(w, h));
		p.opts().width(w).height(h);
		return p.toImage(Math.max(1.0, s));
	}

	/** Called when the user clicks the button to export the current plot to a PNG file. */
	protected void export() {
		String path = promptForFilePath("png", "PNG image");
		if (path == null) return;
		try {
			ImageIO.write(renderImage(2.0), "png", new File(path));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this,
					"An error occurred while attempting to save the plot. ERROR MESSAGE = " + e,
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Prints the chart as vectors (crisp at any printer resolution). */
	public void doPrint() {
		PrinterJob pj = PrinterJob.getPrinterJob();
		pj.setJobName("SPMF print time series");
		pj.setPrintable(new Printable() {
			@Override
			public int print(Graphics pg, PageFormat pageFormat, int pageNum) throws PrinterException {
				if (pageNum > 0) return Printable.NO_SUCH_PAGE;
				Plot p = ensurePlot();
				int cw = Math.max(1, getWidth() > 0 ? getWidth() : width);
				int ch = Math.max(1, getHeight() > 0 ? getHeight() : height);
				Graphics2D g2 = (Graphics2D) pg.create();
				try {
					g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
					double s = Math.min(pageFormat.getImageableWidth() / cw,
							pageFormat.getImageableHeight() / ch);
					g2.scale(s, s);
					p.resize(cw, ch, g2);
					p.drawChartOnGraphics2D(g2);
				} finally {
					g2.dispose();
				}
				return Printable.PAGE_EXISTS;
			}
		});
		if (!pj.printDialog()) return;
		try {
			pj.print();
		} catch (PrinterException ex) {
			JOptionPane.showMessageDialog(this, "Printing failed: " + ex.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private String promptForFilePath(String extension, String description) {
		try {
			File initialDir = getInitialDirectory();
			JFileChooser fc = initialDir != null ? new JFileChooser(initialDir) : new JFileChooser();
			fc.setDialogTitle("Save as " + extension.toUpperCase(Locale.ROOT));
			fc.setFileFilter(new FileNameExtensionFilter(description + " (*." + extension + ")", extension));
			fc.setSelectedFile(new File(fc.getCurrentDirectory(), "time_series." + extension));

			while (true) {
				if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
				File file = fc.getSelectedFile();
				if (!file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
					file = new File(file.getPath() + "." + extension);
				}
				if (file.exists()) {
					int r = JOptionPane.showConfirmDialog(this,
							"\"" + file.getName() + "\" already exists. Overwrite?", "Confirm overwrite",
							JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
					if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return null;
					if (r == JOptionPane.NO_OPTION) continue;
				}
				PreferencesManager.getInstance().setOutputFilePath(file.getParent());
				return file.getPath();
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this,
					"An error occurred while opening the save plot dialog. ERROR MESSAGE = " + e,
					"Error", JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}

	private static File getInitialDirectory() {
		try {
			String previousPath = PreferencesManager.getInstance().getOutputFilePath();
			if (previousPath != null) return new File(previousPath);
			URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
			if (main != null && "file".equalsIgnoreCase(main.getProtocol())) {
				return new File(main.getPath()).getParentFile();
			}
		} catch (Exception ignored) {
			// fall back to default directory
		}
		return null;
	}

	// ---------------------------------------------------------------
	// Listeners / options
	// ---------------------------------------------------------------

	public void addListener(TimeSeriesViewerPanelListener listener) {
		if (listener != null) listeners.add(listener);
	}

	public void setDrawGrid(boolean drawTheGrid) {
		this.drawTheGrid = drawTheGrid;
		invalidatePlot();
	}

	public void setDrawLegend(LegendFormat selection) {
		this.legendFormat = selection != null ? selection : LegendFormat.NONE;
		invalidatePlot();
	}

	/** @param width line width in points (0 = no lines) */
	public void setSeriesLineWidth(int width) {
		this.seriesLineWidth = Math.max(0, width);
		invalidatePlot();
	}

	/** @param selection marker size in points (0 = no markers) */
	public void setMarkersSize(int selection) {
		this.markerSize = Math.max(0, selection);
		invalidatePlot();
	}
}