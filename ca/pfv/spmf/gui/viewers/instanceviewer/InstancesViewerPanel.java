package ca.pfv.spmf.gui.viewers.instanceviewer;

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
import ca.pfv.spmf.gui.visuals.plot.Plot;
import ca.pfv.spmf.gui.visuals.plot.Plot.AxisFormat;
import ca.pfv.spmf.gui.visuals.plot.Plot.LegendFormat;
import ca.pfv.spmf.gui.visuals.plot.Plot.Line;
import ca.pfv.spmf.gui.visuals.plot.Plot.Marker;
import ca.pfv.spmf.gui.visuals.plot.Plot.PointHit;
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
 * The panel of the instance viewer (scatter plot of two attributes of a list
 * of instances). All instances are drawn as one series, as vectors directly on
 * the component (crisp on HiDPI screens); the plot is rebuilt only when the
 * data or an option changes.
 *
 * @author Philippe Fournier-Viger, 2016
 */
public class InstancesViewerPanel extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;

	private static final String SERIES_KEY = "Instances";
	private static final Color POINT_COLOR = Plot.DEFAULT_PALETTE[0];
	private static final int HOVER_RADIUS = 12;
	private static final int MAX_CANVAS_SIZE = 30_000;

	/** The instances to be displayed */
	private final List<DoubleArray> multipleInstances;
	/** Attribute names (axis titles), may be null */
	private String[] attributeNames;

	private Plot plot;
	private boolean plotDirty = true;

	/** Logical canvas size; zoom changes these */
	int width;
	int height;
	int originalWidth;
	int originalHeigth;

	boolean drawTheGrid = false;
	private int markerSize = 5;

	private int attributeSelectedX;
	private int attributeSelectedY;

	/** bounds of the data for the selected attributes */
	private double minX, maxX, minY, maxY;

	private PointHit hover;
	private final List<InstanceViewerPanelListener> listeners = new ArrayList<InstanceViewerPanelListener>();

	/**
	 * Constructor
	 * @param instances the instances to display
	 * @param attributeSelectedX the attribute selected on the X axis
	 * @param attributeSelectedY the attribute selected on the Y axis
	 */
	public InstancesViewerPanel(List<DoubleArray> instances, int attributeSelectedX, int attributeSelectedY) {
		this(instances, attributeSelectedX, attributeSelectedY, null);
	}

	public InstancesViewerPanel(List<DoubleArray> instances, int attributeSelectedX, int attributeSelectedY,
			String[] attributeNames) {
		this.multipleInstances = instances != null ? instances : new ArrayList<DoubleArray>();
		this.attributeSelectedX = Math.max(0, attributeSelectedX);
		this.attributeSelectedY = Math.max(0, attributeSelectedY);
		this.attributeNames = attributeNames;

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
		int n = multipleInstances.size();
		double[] x = new double[n];
		double[] y = new double[n];
		minX = Double.POSITIVE_INFINITY; maxX = Double.NEGATIVE_INFINITY;
		minY = Double.POSITIVE_INFINITY; maxY = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			DoubleArray inst = multipleInstances.get(i);
			double[] d = inst != null ? inst.data : null;
			boolean ok = d != null && attributeSelectedX < d.length && attributeSelectedY < d.length;
			x[i] = ok ? d[attributeSelectedX] : Double.NaN;
			y[i] = ok ? d[attributeSelectedY] : Double.NaN;
			if (ok && !Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
				if (x[i] < minX) minX = x[i];
				if (x[i] > maxX) maxX = x[i];
				if (y[i] < minY) minY = y[i];
				if (y[i] > maxY) maxY = y[i];
			}
		}
		if (minX > maxX) { minX = 0; maxX = 0; minY = 0; maxY = 0; }

		Plot p = Plot.plot(Plot.plotOpts()
				.width(Math.max(1, getWidth() > 0 ? getWidth() : width))
				.height(Math.max(1, getHeight() > 0 ? getHeight() : height))
				.bgColor(Color.WHITE)
				.plotPadding(Math.max(8, markerSize + 4))
				.legend(LegendFormat.NONE));

		// ONE series for all instances (the old code created one series per point)
		p.series(SERIES_KEY, Plot.data().xy(x, y), Plot.seriesOpts()
				.color(POINT_COLOR)
				.line(Line.NONE)
				.marker(markerSize > 0 ? Marker.CIRCLE : Marker.NONE)
				.markerSize(Math.max(1, markerSize)));

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
			p.drawChartOnGraphics2D(g2);     // vectors, inherits the HiDPI transform
			drawHover(g2, p);
		} finally {
			g2.dispose();
		}
	}

	private void drawHover(Graphics2D g2, Plot p) {
		if (hover == null) return;
		Rectangle r = p.getPlotAreaRectangle();
		Graphics2D g = (Graphics2D) g2.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.clip(r);
			g.setColor(new Color(0, 0, 0, 60));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
					new float[] { 3f, 3f }, 0f));
			g.draw(new Line2D.Double(r.x, hover.pixelY, r.x + r.width, hover.pixelY));
			g.draw(new Line2D.Double(hover.pixelX, r.y, hover.pixelX, r.y + r.height));

			double rad = Math.max(markerSize, 4) + 3;
			Ellipse2D ring = new Ellipse2D.Double(hover.pixelX - rad, hover.pixelY - rad, 2 * rad, 2 * rad);
			Color c = new Color(214, 64, 64);
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
					parent.dispatchEvent(SwingUtilities.convertMouseEvent(InstancesViewerPanel.this, e, parent));
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
			for (InstanceViewerPanelListener l : listeners) {
				l.notifyMouseOutOfChart();
				l.notifyOfInstanceUnderMouse(-1, Double.NaN, Double.NaN);
			}
			return;
		}

		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		double chartX = plot.pixelToX(pt.x);
		double chartY = plot.pixelToY(pt.y);
		PointHit hit = plot.nearestPoint(pt.x, pt.y, HOVER_RADIUS);

		for (InstanceViewerPanelListener l : listeners) {
			l.notifyOfNewMousePosition(chartX, chartY);
			if (hit != null) l.notifyOfInstanceUnderMouse(hit.index, hit.x, hit.y);
			else l.notifyOfInstanceUnderMouse(-1, Double.NaN, Double.NaN);
		}

		boolean changed = (hit == null) != (hover == null) || (hit != null && hit.index != hover.index);
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
		pj.setJobName("SPMF print instances");
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
			fc.setSelectedFile(new File(fc.getCurrentDirectory(), "instances." + extension));
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
	// Listeners / options / getters
	// ---------------------------------------------------------------

	public void addListener(InstanceViewerPanelListener listener) {
		if (listener != null) listeners.add(listener);
	}

	public void setDrawGrid(boolean drawTheGrid) {
		this.drawTheGrid = drawTheGrid;
		invalidatePlot();
	}

	/** @param selection marker size in points (0 = no markers) */
	public void setMarkersSize(int selection) {
		this.markerSize = Math.max(0, selection);
		invalidatePlot();
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

	public int getInstanceCount() {
		return multipleInstances.size();
	}

	/** Minimum of the selected X attribute */
	public double getMinX() { ensurePlot(); return minX; }
	/** Maximum of the selected X attribute */
	public double getMaxX() { ensurePlot(); return maxX; }
	/** Minimum of the selected Y attribute */
	public double getMinY() { ensurePlot(); return minY; }
	/** Maximum of the selected Y attribute */
	public double getMaxY() { ensurePlot(); return maxY; }
}