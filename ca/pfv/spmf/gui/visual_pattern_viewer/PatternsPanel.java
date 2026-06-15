package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * 
 * Do not remove copyright and license information
 */
/**
 * An abstract class implementing a Swing panel for displaying a list of
 * patterns, used by the Visual Pattern Viewer.
 *
 * @author Philippe Fournier-Viger
 * @see VisualPatternViewer
 */
public abstract class PatternsPanel extends JPanel {

    /** serial UID */
    private static final long serialVersionUID = 263741498271800378L;

    /** The layout mode **/
    protected LayoutMode layoutMode = LayoutMode.GRID;

    /**
     * Default page size: show all patterns (no pagination).
     * Subclasses use this field to control how many patterns appear per page.
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
    }

    /**
     * Clears and recreates a {@link RulePanel} for each rule.
     */
    protected abstract void rebuildPanels();

    /**
     * @return an ordered list of all sorting orders that are offered
     */
    public abstract List<String> getListOfSortingOrders();

    // ---------------------------------------------------------------
    // Export methods - ALL MUST BE CALLED FROM THE EDT
    // ---------------------------------------------------------------

 // ---------------------------------------------------------------
 // Export methods - ALL MUST BE CALLED FROM THE EDT
 // ---------------------------------------------------------------

 /**
  * Renders only the current page to a PNG file.
  * <strong>Must be called from the EDT.</strong>
  *
  * @param filename output filename (PNG format)
  * @throws IOException if writing the image fails
  */
 public void exportCurrentPageAsPNG(String filename) throws IOException {
     // Force layout of current state
     revalidate();
     doLayout();
     
     // Get the actual size of what's currently displayed
     Dimension size = getSize();
     if (size.width <= 0 || size.height <= 0) {
         size = getPreferredSize();
     }
     
     // Create image
     BufferedImage img = new BufferedImage(
             Math.max(1, size.width),
             Math.max(1, size.height),
             BufferedImage.TYPE_INT_ARGB);
     
     // Render
     Graphics2D g2 = img.createGraphics();
     try {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                 RenderingHints.VALUE_ANTIALIAS_ON);
         g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                 RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                 RenderingHints.VALUE_RENDER_QUALITY);
         printAll(g2);
     } finally {
         g2.dispose();
     }
     
     // Write file
     File outputFile = new File(filename);
     ImageIO.write(img, "png", outputFile);
     System.out.println("Exported current page to: " + outputFile.getAbsolutePath());
 }

 /**
  * Renders ALL visible patterns (across all pages) to a PNG file.
  * Temporarily switches to "show all" mode, renders, then restores state.
  * <strong>Must be called from the EDT.</strong>
  *
  * @param filename output filename (PNG format)
  * @throws IOException if writing the image fails
  */
 public void exportAllVisibleAsPNG(String filename) throws IOException {
     // Save current state
     int savedPageSize = this.pageSize;
     int savedCurrentPage = this.currentPage;

     try {
         // Switch to show-all mode
         this.pageSize = Integer.MAX_VALUE;
         this.currentPage = 0;
         rebuildPanels();
         
         // Force complete layout
         revalidate();
         doLayout();
         
         // Get preferred size after showing all
         Dimension size = getPreferredSize();
         setSize(size);
         doLayout();
         
         // Create image
         BufferedImage img = new BufferedImage(
                 Math.max(1, size.width),
                 Math.max(1, size.height),
                 BufferedImage.TYPE_INT_ARGB);
         
         // Render
         Graphics2D g2 = img.createGraphics();
         try {
             g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                     RenderingHints.VALUE_ANTIALIAS_ON);
             g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                     RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
             g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                     RenderingHints.VALUE_RENDER_QUALITY);
             printAll(g2);
         } finally {
             g2.dispose();
         }
         
         // Write file
         File outputFile = new File(filename);
         ImageIO.write(img, "png", outputFile);
         System.out.println("Exported all visible patterns to: " + outputFile.getAbsolutePath());
         
     } finally {
         // Always restore original state
         this.pageSize = savedPageSize;
         this.currentPage = savedCurrentPage;
         rebuildPanels();
         revalidate();
         repaint();
     }
 }

 /**
  * Renders ALL patterns (including filtered-out ones) to a PNG file.
  * Clears filters, switches to "show all" mode, renders, then restores pagination.
  * <strong>Note:</strong> The filter state is NOT restored; the caller must
  * re-apply filters if needed.
  * <strong>Must be called from the EDT.</strong>
  *
  * @param filename output filename (PNG format)
  * @throws IOException if writing the image fails
  */
 public void exportAllPatternsAsPNG(String filename) throws IOException {
     // Save current state
     int savedPageSize = this.pageSize;
     int savedCurrentPage = this.currentPage;

     try {
         // Clear filters and show everything
         clearSearchAndFilters();
         this.pageSize = Integer.MAX_VALUE;
         this.currentPage = 0;
         rebuildPanels();
         
         // Force complete layout
         revalidate();
         doLayout();
         
         // Get preferred size after showing all
         Dimension size = getPreferredSize();
         setSize(size);
         doLayout();
         
         // Create image
         BufferedImage img = new BufferedImage(
                 Math.max(1, size.width),
                 Math.max(1, size.height),
                 BufferedImage.TYPE_INT_ARGB);
         
         // Render
         Graphics2D g2 = img.createGraphics();
         try {
             g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                     RenderingHints.VALUE_ANTIALIAS_ON);
             g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                     RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
             g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                     RenderingHints.VALUE_RENDER_QUALITY);
             printAll(g2);
         } finally {
             g2.dispose();
         }
         
         // Write file
         File outputFile = new File(filename);
         ImageIO.write(img, "png", outputFile);
         System.out.println("Exported all patterns to: " + outputFile.getAbsolutePath());
         
     } finally {
         // Restore pagination (filter is NOT restored - the dialog closes anyway)
         this.pageSize = savedPageSize;
         this.currentPage = savedCurrentPage;
         rebuildPanels();
         revalidate();
         repaint();
     }
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
    // Rest of the API (unchanged)
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
    public abstract void applySearchAndFilters(String searchString,
            Map<String, Double> measureThresholds,
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
        if (pageSize <= 0 || pageSize == Integer.MAX_VALUE) return 1;
        return Math.max(1, (int) Math.ceil((double) visible / pageSize));
    }

    public void goToPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, getTotalPages() - 1));
        rebuildPanels();
    }

    public void goToFirstPage()    { goToPage(0); }
    public void goToPreviousPage() { goToPage(currentPage - 1); }
    public void goToNextPage()     { goToPage(currentPage + 1); }
    public void goToLastPage()     { goToPage(getTotalPages() - 1); }
    public boolean isPaginated()   { return pageSize != Integer.MAX_VALUE; }
}