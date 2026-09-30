package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

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
 * An abstract class implementing a Swing panel for displaying a list of
 * patterns, used by the Visual Pattern Viewer.
 * <p>
 * The panel is {@link Scrollable}: it tracks the viewport width (so that the
 * grid wraps and cards are centered) and scrolls by a sensible amount with the
 * mouse wheel. Subclasses only need to provide the list of pattern panels for
 * the current page and call {@link #layoutPatternPanels(List)}.
 *
 * @author Philippe Fournier-Viger
 * @see VisualPatternViewer
 */
public abstract class PatternsPanel extends JPanel implements Scrollable {

	/** serial UID */
	private static final long serialVersionUID = 263741498271800378L;

	/** Gap between pattern cards, in pixels */
	protected static final int GAP_BETWEEN_PATTERNS = 10;

	/** Columns used by the grid before the panel width is known */
	protected static final int DEFAULT_GRID_COLUMNS = 3;

	/** Pixels scrolled by one mouse-wheel notch / arrow click */
	private static final int SCROLL_UNIT_INCREMENT = 24;

	/** The layout mode **/
	protected LayoutMode layoutMode = LayoutMode.GRID;
	
	/** Optional action run by the "Clear filters" button of the empty state. */
	private Runnable clearFiltersAction;

	/**
	 * Default page size: show all patterns (no pagination). Subclasses use this
	 * field to control how many patterns appear per page.
	 */
	protected int pageSize = Integer.MAX_VALUE;

	/** Current page index (0-based). */
	protected int currentPage = 0;

	/**
	 * Constructor
	 *
	 * @param mode the layout mode
	 */
	public PatternsPanel(LayoutMode mode) {
		layoutMode = mode;
		setBackground(ViewerStyle.CANVAS_BG);
		setOpaque(true);
		setFocusable(true); // so Page Up/Down, Home/End scroll the canvas after a click
		addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				requestFocusInWindow();
			}
		});
	}

	/**
	 * Clears and recreates the pattern panels of the current page.
	 */
	protected abstract void rebuildPanels();

	/**
	 * @return an ordered list of all sorting orders that are offered
	 */
	public abstract List<String> getListOfSortingOrders();
	
	/** Sets the action of the "Clear search & filters" button shown when a filter hides everything. */
	public void setClearFiltersAction(Runnable action) {
		this.clearFiltersAction = action;
	}

	/** @return true if a search / filter is currently restricting the patterns (subclasses override). */
	protected boolean hasActiveFilter() {
		return false;
	}

	// ---------------------------------------------------------------
	// Helpers for subclasses: pagination window and layout
	// ---------------------------------------------------------------

	/**
	 * Computes the index range [from, to) of the visible patterns that belong to
	 * the current page. Also clamps {@link #currentPage} to a valid value.
	 *
	 * @param totalVisible number of patterns passing the current filter
	 * @return an array {from (inclusive), to (exclusive)}
	 */
	protected int[] computePageRange(int totalVisible) {
		if (pageSize == Integer.MAX_VALUE || pageSize <= 0) {
			return new int[] { 0, totalVisible };
		}
		int totalPages = Math.max(1, (int) Math.ceil((double) totalVisible / pageSize));
		if (currentPage >= totalPages) {
			currentPage = totalPages - 1;
		}
		if (currentPage < 0) {
			currentPage = 0;
		}
		int from = currentPage * pageSize;
		int to = Math.min(from + pageSize, totalVisible);
		return new int[] { from, to };
	}

	/**
	 * Removes all children and lays out the given pattern panels according to
	 * the current {@link LayoutMode}:
	 * <ul>
	 * <li>GRID: responsive wrapping grid with uniform cells;</li>
	 * <li>VERTICAL: one centered column, all cards with the same width;</li>
	 * <li>HORIZONTAL: one row, all cards with the same height.</li>
	 * </ul>
	 *
	 * @param panels the panels to display (current page)
	 */
	protected void layoutPatternPanels(List<? extends JComponent> panels) {
		removeAll();

		if (panels.isEmpty()) {
			setLayout(new java.awt.GridBagLayout()); // centers the box
			JPanel box = new JPanel();
			box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
			box.setOpaque(false);
			JLabel label = new JLabel(getEmptyMessage(), SwingConstants.CENTER);
			label.setFont(ViewerStyle.baseFont().deriveFont(Font.ITALIC, 14f));
			label.setForeground(ViewerStyle.TEXT_SECONDARY);
			label.setAlignmentX(CENTER_ALIGNMENT);
			box.add(label);
			if (hasActiveFilter() && clearFiltersAction != null) {
				box.add(Box.createVerticalStrut(12));
				javax.swing.JButton clear = new javax.swing.JButton("Clear search & filters");
				ViewerStyle.styleButton(clear);
				clear.setAlignmentX(CENTER_ALIGNMENT);
				clear.addActionListener(e -> clearFiltersAction.run());
				box.add(clear);
			}
			add(box);
			revalidate();
			repaint();
			return;
		}

		switch (layoutMode) {
		case VERTICAL -> {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			int maxW = 0;
			for (JComponent p : panels) {
				maxW = Math.max(maxW, p.getPreferredSize().width);
			}
			add(Box.createVerticalStrut(GAP_BETWEEN_PATTERNS));
			for (JComponent p : panels) {
				Dimension pref = p.getPreferredSize();
				p.setMaximumSize(new Dimension(maxW, pref.height));
				p.setAlignmentX(Component.CENTER_ALIGNMENT);
				add(p);
				add(Box.createVerticalStrut(GAP_BETWEEN_PATTERNS));
			}
			add(Box.createVerticalGlue());
		}
		case HORIZONTAL -> {
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			int maxH = 0;
			for (JComponent p : panels) {
				maxH = Math.max(maxH, p.getPreferredSize().height);
			}
			add(Box.createHorizontalStrut(GAP_BETWEEN_PATTERNS));
			for (JComponent p : panels) {
				Dimension pref = p.getPreferredSize();
				p.setMaximumSize(new Dimension(pref.width, maxH));
				p.setAlignmentY(Component.CENTER_ALIGNMENT);
				add(p);
				add(Box.createHorizontalStrut(GAP_BETWEEN_PATTERNS));
			}
			add(Box.createHorizontalGlue());
		}
		default -> {
			setLayout(new WrapGridLayout(GAP_BETWEEN_PATTERNS, GAP_BETWEEN_PATTERNS, DEFAULT_GRID_COLUMNS));
			for (JComponent p : panels) {
				add(p);
			}
		}
		}

		revalidate();
		repaint();
	}

	/**
	 * @return the message shown when there is nothing to display (subclasses may
	 *         override to be more specific)
	 */
	protected String getEmptyMessage() {
		return "No patterns to display";
	}

	// ---------------------------------------------------------------
	// Scrollable
	// ---------------------------------------------------------------

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return SCROLL_UNIT_INCREMENT;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		int extent = (orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width;
		return Math.max(SCROLL_UNIT_INCREMENT, extent - SCROLL_UNIT_INCREMENT);
	}

	/**
	 * The panel fills the viewport width whenever its content fits, so the grid
	 * can wrap and cards are centered; otherwise a horizontal scrollbar appears.
	 */
	@Override
	public boolean getScrollableTracksViewportWidth() {
		return getParent() instanceof JViewport vp && vp.getWidth() >= getPreferredSize().width;
	}

	/**
	 * The panel fills the viewport height whenever its content fits, so the
	 * background is uniform and rows are centered in HORIZONTAL mode.
	 */
	@Override
	public boolean getScrollableTracksViewportHeight() {
		return getParent() instanceof JViewport vp && vp.getHeight() >= getPreferredSize().height;
	}

	// ---------------------------------------------------------------
	// Export methods - ALL MUST BE CALLED FROM THE EDT
	// ---------------------------------------------------------------

	/**
	 * Renders only the current page to a PNG file. <strong>Must be called from
	 * the EDT.</strong>
	 *
	 * @param filename output filename (PNG format)
	 * @throws IOException if writing the image fails
	 */
	public void exportCurrentPageAsPNG(String filename) throws IOException {
		revalidate();
		doLayout();
		Dimension size = getSize();
		if (size.width <= 0 || size.height <= 0) {
			size = getPreferredSize();
			setSize(size);
			doLayout();
		}
		renderToFile(filename, size);
		System.out.println("Exported current page to: " + new File(filename).getAbsolutePath());
	}

	/**
	 * Renders ALL visible patterns (across all pages) to a PNG file. Temporarily
	 * switches to "show all" mode, renders, then restores state. <strong>Must be
	 * called from the EDT.</strong>
	 *
	 * @param filename output filename (PNG format)
	 * @throws IOException if writing the image fails
	 */
	public void exportAllVisibleAsPNG(String filename) throws IOException {
		int savedPageSize = this.pageSize;
		int savedCurrentPage = this.currentPage;
		try {
			this.pageSize = Integer.MAX_VALUE;
			this.currentPage = 0;
			rebuildPanels();
			revalidate();
			doLayout();
			Dimension size = getPreferredSize();
			setSize(size);
			doLayout();
			renderToFile(filename, size);
			System.out.println("Exported all visible patterns to: " + new File(filename).getAbsolutePath());
		} finally {
			this.pageSize = savedPageSize;
			this.currentPage = savedCurrentPage;
			rebuildPanels();
			revalidate();
			repaint();
		}
	}

	/**
	 * Renders ALL patterns (including filtered-out ones) to a PNG file. Clears
	 * filters, switches to "show all" mode, renders, then restores pagination.
	 * <strong>Note:</strong> The filter state is NOT restored; the caller must
	 * re-apply filters if needed. <strong>Must be called from the EDT.</strong>
	 *
	 * @param filename output filename (PNG format)
	 * @throws IOException if writing the image fails
	 */
	public void exportAllPatternsAsPNG(String filename) throws IOException {
		int savedPageSize = this.pageSize;
		int savedCurrentPage = this.currentPage;
		try {
			clearSearchAndFilters();
			this.pageSize = Integer.MAX_VALUE;
			this.currentPage = 0;
			rebuildPanels();
			revalidate();
			doLayout();
			Dimension size = getPreferredSize();
			setSize(size);
			doLayout();
			renderToFile(filename, size);
			System.out.println("Exported all patterns to: " + new File(filename).getAbsolutePath());
		} finally {
			this.pageSize = savedPageSize;
			this.currentPage = savedCurrentPage;
			rebuildPanels();
			revalidate();
			repaint();
		}
	}

	/**
	 * Paints this panel into an image of the given size and writes it as PNG.
	 */
	private void renderToFile(String filename, Dimension size) throws IOException {
		BufferedImage img = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			printAll(g2);
		} finally {
			g2.dispose();
		}
		ImageIO.write(img, "png", new File(filename));
	}

	/**
	 * Legacy export method for backward compatibility.
	 *
	 * @param filename output filename (PNG format)
	 * @throws IOException if writing the image fails
	 * @deprecated Use {@link #exportCurrentPageAsPNG(String)},
	 *             {@link #exportAllVisibleAsPNG(String)}, or
	 *             {@link #exportAllPatternsAsPNG(String)} instead.
	 */
	@Deprecated
	public void exportAsPNG(String filename) throws IOException {
		exportCurrentPageAsPNG(filename);
	}

	// ---------------------------------------------------------------
	// Rest of the API
	// ---------------------------------------------------------------

	/**
	 * Sort the patterns based on the user's choice
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

	/**
	 * Sets the layout mode and fires a callback after the rebuild.
	 *
	 * @param mode     the new layout mode
	 * @param callback runnable to call after {@link #rebuildPanels()}
	 */
	public void setLayoutModeWithCallback(LayoutMode mode, Runnable callback) {
		setLayoutMode(mode);
		if (callback != null) {
			callback.run();
		}
	}

	public abstract Double getMinForMeasureOriginal(String measure);

	public abstract Double getMaxForMeasureOriginal(String measure);

	public abstract Set<String> getAllMeasures();

	public abstract void applySearchAndFilters(String searchString, Map<String, Double> measureThresholds,
			Map<String, String> operators);

	public abstract int getNumberOfVisiblePatterns();

	public abstract void clearSearchAndFilters();

	public abstract void populateMeasureValues(Map<String, List<Double>> measureValuesMap);

	public abstract List<Integer> getVisiblePatternSizes();

	// ---------------------------------------------------------------
	// Pagination support
	// ---------------------------------------------------------------

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
		this.currentPage = 0;
		rebuildPanels();
	}
	public int getCurrentPage() {
		return currentPage;
	}

	public int getTotalPages() {
		int visible = getNumberOfVisiblePatterns();
		if (pageSize <= 0 || pageSize == Integer.MAX_VALUE) {
			return 1;
		}
		return Math.max(1, (int) Math.ceil((double) visible / pageSize));
	}

	public void goToPage(int page) {
		this.currentPage = Math.max(0, Math.min(page, getTotalPages() - 1));
		rebuildPanels();
	}

	public void goToFirstPage() {
		goToPage(0);
	}

	public void goToPreviousPage() {
		goToPage(currentPage - 1);
	}

	public void goToNextPage() {
		goToPage(currentPage + 1);
	}

	public void goToLastPage() {
		goToPage(getTotalPages() - 1);
	}

	public boolean isPaginated() {
		return pageSize != Integer.MAX_VALUE;
	}
}