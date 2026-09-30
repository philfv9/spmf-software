package ca.pfv.spmf.gui.viewers.clusterviewer;

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
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.visuals.heatmap.ClusterViewerPanelListener;
import ca.pfv.spmf.gui.visuals.plot.Plot;
import ca.pfv.spmf.gui.visuals.plot.Plot.AxisFormat;
import ca.pfv.spmf.gui.visuals.plot.Plot.LegendFormat;
import ca.pfv.spmf.gui.visuals.plot.Plot.Line;
import ca.pfv.spmf.gui.visuals.plot.Plot.Marker;
import ca.pfv.spmf.gui.visuals.plot.Plot.PointHit;
import ca.pfv.spmf.patterns.cluster.Cluster;
import ca.pfv.spmf.patterns.cluster.DoubleArray;
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
 * The panel of the cluster viewer (scatter plot of two attributes). The chart
 * is drawn as vectors directly on the component (crisp on HiDPI screens), the
 * plot is rebuilt only when the data or an option changes, and the point under
 * the mouse is found geometrically (no screen-pixel probing).
 *
 * @author Philippe Fournier-Viger, 2016
 */
public class ClusterViewerPanel extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;

	/** Max pixel distance between the mouse and a point to consider it hovered */
	private static final int HOVER_RADIUS = 12;
	private static final int MAX_CANVAS_SIZE = 30_000;
	private static final Marker[] MARKER_TYPES = {
			Marker.CIRCLE, Marker.DIAMOND, Marker.SQUARE, Marker.TRIANGLE };

	/** The clusters to be displayed */
	private final List<Cluster> multipleClusters;
	/** Colour of each cluster */
	private final Color[] multipleClustersColors;
	/** Series key of each cluster */
	private final String[] clusterKeys;
	/** Attribute names (axis titles) */
	private String[] attributeNames;

	private Plot plot;
	private boolean plotDirty = true;

	/** Logical canvas size; zoom changes these */
	int width;
	int height;
	int originalWidth;
	int originalHeigth;

	boolean drawTheGrid = false;
	private boolean drawCentroids = true;
	private int markerSize = 5;
	LegendFormat legendFormat = LegendFormat.BOTTOM;

	private int attributeSelectedX;
	private int attributeSelectedY;

	private PointHit hover;
	private final List<ClusterViewerPanelListener> listeners = new ArrayList<ClusterViewerPanelListener>();

	/**
	 * Constructor
	 * @param clusters a list of clusters to display
	 * @param attributeSelectedX the attribute selected on the X axis
	 * @param attributeSelectedY the attribute selected on the Y axis
	 */
	public ClusterViewerPanel(List<Cluster> clusters, int attributeSelectedX, int attributeSelectedY) {
		this(clusters, attributeSelectedX, attributeSelectedY, null);
	}

	public ClusterViewerPanel(List<Cluster> clusters, int attributeSelectedX, int attributeSelectedY,
			String[] attributeNames) {
		this.multipleClusters = clusters != null ? clusters : new ArrayList<Cluster>();
		this.attributeSelectedX = Math.max(0, attributeSelectedX);
		this.attributeSelectedY = Math.max(0, attributeSelectedY);
		this.attributeNames = attributeNames;

		int n = multipleClusters.size();
		multipleClustersColors = new Color[n];
		clusterKeys = new String[n];
		for (int i = 0; i < n; i++) {
			multipleClustersColors[i] = Plot.DEFAULT_PALETTE[i % Plot.DEFAULT_PALETTE.length];
			clusterKeys[i] = "Cluster " + (i + 1);
		}

		originalWidth = width = 600;
		originalHeigth = height = 500;

		setBackground(Color.WHITE);
		setOpaque(true);
		setPreferredSize(new Dimension(width, height));
		installMouseHandling();
	}

	// ---------------------------------------------------------------
	// Plot construction
	// ---------------------------------------------------------------

	private String attributeName(int index) {
		if (attributeNames != null && index >= 0 && index < attributeNames.length
				&& attributeNames[index] != null && !attributeNames[index].isEmpty()) {
			return attributeNames[index];
		}
		return "Attribute " + index;
	}

	private Plot buildPlot() {
		Plot p = Plot.plot(Plot.plotOpts()
				.width(Math.max(1, getWidth() > 0 ? getWidth() : width))
				.height(Math.max(1, getHeight() > 0 ? getHeight() : height))
				.bgColor(Color.WHITE)
				.plotPadding(Math.max(8, markerSize + 4))
				.legend(legendFormat));

		for (int i = 0; i < multipleClusters.size(); i++) {
			Cluster cluster = multipleClusters.get(i);
			List<DoubleArray> vectors = cluster.getVectors();
			int size = vectors != null ? vectors.size() : 0;
			double[] x = new double[size];
			double[] y = new double[size];
			for (int j = 0; j < size; j++) {
				double[] d = vectors.get(j).data;
				boolean ok = d != null && attributeSelectedX < d.length && attributeSelectedY < d.length;
				x[j] = ok ? d[attributeSelectedX] : Double.NaN;
				y[j] = ok ? d[attributeSelectedY] : Double.NaN;
			}
			Marker marker = markerSize > 0
					? MARKER_TYPES[(i / Plot.DEFAULT_PALETTE.length) % MARKER_TYPES.length]
					: Marker.NONE;
			p.series(clusterKeys[i], Plot.data().xy(x, y), Plot.seriesOpts()
					.color(multipleClustersColors[i])
					.line(Line.NONE)
					.marker(marker)
					.markerSize(Math.max(1, markerSize)));
		}

		p.xAxis("x", Plot.axisOpts().format(AxisFormat.NUMBER).title(attributeName(attributeSelectedX))
				.niceRange(true).gridLines(drawTheGrid))
		 .yAxis("y", Plot.axisOpts().format(AxisFormat.NUMBER).title(attributeName(attributeSelectedY))
				.niceRange(true).gridLines(drawTheGrid));
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

	public Plot getPlot() {
		return ensurePlot();
	}

	// ---------------------------------------------------------------
	// Painting
	// ---------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			Plot p = ensurePlot();
			p.resize(getWidth(), getHeight(), g2);
			p.drawChartOnGraphics2D(g2);     // vectors, inherits HiDPI transform
			drawOverlays(g2, p);
		} finally {
			g2.dispose();
		}
	}

	private void drawOverlays(Graphics2D g2, Plot p) {
		Rectangle r = p.getPlotAreaRectangle();
		Graphics2D g = (Graphics2D) g2.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.clip(r);
			if (drawCentroids) {
				drawCentroids(g, p);
			}
			if (hover != null) {
				drawHover(g, p, r);
			}
		} finally {
			g.dispose();
		}
	}

	/** Draws a big "x" at the centroid of each cluster (for the selected attributes). */
	private void drawCentroids(Graphics2D g, Plot p) {
		double size = Math.max(7, markerSize + 5);
		for (int i = 0; i < multipleClusters.size(); i++) {
			double[] c = centroid(multipleClusters.get(i));
			if (c == null) continue;
			double px = p.xToPixel(c[0]);
			double py = p.yToPixel(c[1]);
			if (Double.isNaN(px) || Double.isNaN(py)) continue;
			// white halo then coloured cross
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(px - size, py - size, px + size, py + size));
			g.draw(new Line2D.Double(px - size, py + size, px + size, py - size));
			Color col = multipleClustersColors[i];
			g.setColor(new Color(Math.round(col.getRed() * 0.7f), Math.round(col.getGreen() * 0.7f),
					Math.round(col.getBlue() * 0.7f)));
			g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(px - size, py - size, px + size, py + size));
			g.draw(new Line2D.Double(px - size, py + size, px + size, py - size));
		}
	}

	private double[] centroid(Cluster cluster) {
		List<DoubleArray> vectors = cluster.getVectors();
		if (vectors == null || vectors.isEmpty()) return null;
		double sx = 0, sy = 0;
		int n = 0;
		for (DoubleArray v : vectors) {
			double[] d = v.data;
			if (d == null || attributeSelectedX >= d.length || attributeSelectedY >= d.length) continue;
			if (Double.isNaN(d[attributeSelectedX]) || Double.isNaN(d[attributeSelectedY])) continue;
			sx += d[attributeSelectedX];
			sy += d[attributeSelectedY];
			n++;
		}
		return n == 0 ? null : new double[] { sx / n, sy / n };
	}

	private void drawHover(Graphics2D g, Plot p, Rectangle r) {
		Color c = colorOf(hover.seriesName);
		// thin crosshair
		g.setColor(new Color(0, 0, 0, 60));
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
				new float[] { 3f, 3f }, 0f));
		g.draw(new Line2D.Double(r.x, hover.pixelY, r.x + r.width, hover.pixelY));
		g.draw(new Line2D.Double(hover.pixelX, r.y, hover.pixelX, r.y + r.height));
		// ring around the point
		double rad = Math.max(markerSize, 4) + 3;
		Ellipse2D ring = new Ellipse2D.Double(hover.pixelX - rad, hover.pixelY - rad, 2 * rad, 2 * rad);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
		g.fill(ring);
		g.setColor(c);
		g.setStroke(new BasicStroke(1.6f));
		g.draw(ring);
	}

	private Color colorOf(String seriesKey) {
		for (int i = 0; i < clusterKeys.length; i++) {
			if (clusterKeys[i].equals(seriesKey)) return multipleClustersColors[i];
		}
		return Color.DARK_GRAY;
	}

	// ---------------------------------------------------------------
	// Mouse handling
	// ---------------------------------------------------------------

	private void installMouseHandling() {
		MouseAdapter adapter = new MouseAdapter() {
			@Override public void mouseMoved(MouseEvent e) { handleMouse(e.getPoint()); }
			@Override public void mouseDragged(MouseEvent e) { handleMouse(e.getPoint()); }
			@Override public void mouseExited(MouseEvent e) { handleMouse(null); }

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
					parent.dispatchEvent(SwingUtilities.convertMouseEvent(ClusterViewerPanel.this, e, parent));
				}
			}
		};
		addMouseListener(adapter);
		addMouseMotionListener(adapter);
		addMouseWheelListener(adapter);
	}

	private void handleMouse(Point pt) {
		if (plot == null || plotDirty) return;
		Rectangle area = plot.getPlotAreaRectangle();

		if (pt == null || !area.contains(pt)) {
			setCursor(Cursor.getDefaultCursor());
			if (hover != null) {
				hover = null;
				repaint();
			}
			for (ClusterViewerPanelListener l : listeners) l.notifyMouseOutOfChart();
			return;
		}

		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		double chartX = plot.pixelToX(pt.x);
		double chartY = plot.pixelToY(pt.y);

		PointHit hit = plot.nearestPoint(pt.x, pt.y, HOVER_RADIUS);
		String name = "";
		Color color = Color.BLACK;
		if (hit != null) {
			name = hit.seriesName + "   [point " + (hit.index + 1) + ": ("
					+ String.format(Locale.US, "%.4g", hit.x) + ", "
					+ String.format(Locale.US, "%.4g", hit.y) + ")]";
			color = colorOf(hit.seriesName);
		}
		for (ClusterViewerPanelListener l : listeners) {
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
	// Zoom / Scrollable
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

	/** Renders the chart as currently displayed ({@code scale} = 2 for HiDPI). */
	public BufferedImage renderImage(double scale) {
		int w = Math.max(1, getWidth() > 0 ? getWidth() : width);
		int h = Math.max(1, getHeight() > 0 ? getHeight() : height);
		double s = Math.max(1.0, Math.min(scale, 16000.0 / Math.max(w, h)));
		BufferedImage img = new BufferedImage((int) Math.ceil(w * s), (int) Math.ceil(h * s),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		try {
			g.scale(s, s);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, w, h);
			Plot p = ensurePlot();
			p.resize(w, h, g);
			p.drawChartOnGraphics2D(g);
			if (drawCentroids) {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.clip(p.getPlotAreaRectangle());
				drawCentroids(g, p);
			}
		} finally {
			g.dispose();
		}
		return img;
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

	/** Prints the chart as vectors. */
	public void doPrint() {
		PrinterJob pj = PrinterJob.getPrinterJob();
		pj.setJobName("SPMF print clusters");
		pj.setPrintable(new Printable() {
			@Override
			public int print(Graphics pg, PageFormat pageFormat, int pageNum) throws PrinterException {
				if (pageNum > 0) return Printable.NO_SUCH_PAGE;
				int cw = Math.max(1, getWidth() > 0 ? getWidth() : width);
				int ch = Math.max(1, getHeight() > 0 ? getHeight() : height);
				Graphics2D g2 = (Graphics2D) pg.create();
				try {
					g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
					double s = Math.min(pageFormat.getImageableWidth() / cw, pageFormat.getImageableHeight() / ch);
					g2.scale(s, s);
					Plot p = ensurePlot();
					p.resize(cw, ch, g2);
					p.drawChartOnGraphics2D(g2);
					if (drawCentroids) {
						g2.clip(p.getPlotAreaRectangle());
						drawCentroids(g2, p);
					}
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
			fc.setSelectedFile(new File(fc.getCurrentDirectory(), "clusters." + extension));
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
			// default directory
		}
		return null;
	}

	// ---------------------------------------------------------------
	// Listeners / options
	// ---------------------------------------------------------------

	public void addListener(ClusterViewerPanelListener listener) {
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

	/** @param selection marker size in points (0 = no markers) */
	public void setMarkersSize(int selection) {
		this.markerSize = Math.max(0, selection);
		invalidatePlot();
	}

	/** Shows / hides the centroid ("x") of each cluster. */
	public void setDrawCentroids(boolean draw) {
		this.drawCentroids = draw;
		repaint();
	}

	/** Sets the attribute names used as axis titles. */
	public void setAttributeNames(String[] names) {
		this.attributeNames = names;
		invalidatePlot();
	}

	/**
	 * Set the selection of attributes
	 * @param attributeSelectedX the attribute selected on the X axis
	 * @param attributeSelectedY the attribute selected on the Y axis
	 */
	public void setAttributeSelection(int attributeSelectedX, int attributeSelectedY) {
		this.attributeSelectedX = Math.max(0, attributeSelectedX);
		this.attributeSelectedY = Math.max(0, attributeSelectedY);
		invalidatePlot();
	}
}