package ca.pfv.spmf.gui.visuals.heatmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
/*
 * Copyright (c) 2008-2023 Philippe Fournier-Viger
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
 * A Jpanel to display a heat map.
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class HeatMap extends JPanel {
	private final String[] rowNames;
	private final String[] columnNames;
	private final double[][] data;
	private double min = Double.MAX_VALUE;
	private double max = -Double.MAX_VALUE;
	// Flags to control the drawing of labels
	private boolean drawRowLabels = true;
	private boolean drawColumnLabels = true;
	// Flag to control the drawing of the color scale
	private boolean drawColorScale = true;
	private boolean drawColumnLabelsVertically = false;

	/** The font to be used for labels */
	private Font labelFont = new Font("Arial", Font.PLAIN, 10);

	private int margin = 10; // Default margin size

	private int minCellWidth = 20; // Default minimum width for the heatmap cells
	private int minCellHeight = 20; // Default minimum height for the heatmap cells

	/** Minimum horizontal gap between heatmap and color scale */
	private final int COLOR_SCALE_MIN_GAP = 10;
	
	/** Width of the color scale bar */
	private final int COLOR_SCALE_BAR_WIDTH = 20;
	
	/** Space for color scale labels */
	private final int COLOR_SCALE_LABEL_SPACE = 60;

	private JPanel drawingPanel = null;
	private JScrollPane scrollpane = null;
	
	/** Cached dimensions calculated based on labels */
	private int cachedRowLabelWidth = 0;
	private int cachedColumnLabelHeight = 0;

	public HeatMap(double[][] data, String[] rowNames, String[] columnNames) {
		if (data == null || rowNames == null || columnNames == null) {
			throw new IllegalArgumentException("Data, rowNames, and columnNames must not be null");
		}
		if (data.length == 0 || data[0].length == 0) {
			throw new IllegalArgumentException("Data must not be empty");
		}
		if (data.length != rowNames.length || data[0].length != columnNames.length) {
			throw new IllegalArgumentException("Mismatch between data and names dimensions");
		}
		this.data = data;
		this.rowNames = rowNames;
		this.columnNames = columnNames;
		calculateMinMaxValues();
		setLayout(new BorderLayout());
		drawingPanel = createHeatMapPanel();
		scrollpane = new JScrollPane(drawingPanel);
		add(scrollpane, BorderLayout.CENTER);
	}

	/**
	 * Method to change the font size and style of the text labels
	 * 
	 * @param font the font
	 */
	public void setLabelFont(Font font) {
		this.labelFont = font;
		// Reset cached dimensions
		cachedRowLabelWidth = 0;
		cachedColumnLabelHeight = 0;
		if (drawingPanel != null) {
			drawingPanel.repaint();
		}
		repaint();
	}

	/**
	 * Get the font used for labels
	 * 
	 * @return the font
	 */
	public Font getLabelFont() {
		return labelFont;
	}

	public void setDrawRowLabels(boolean drawRowLabels) {
		this.drawRowLabels = drawRowLabels;
		if (drawingPanel != null) {
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
	}

	public void setDrawColumnLabels(boolean drawColumnLabels) {
		this.drawColumnLabels = drawColumnLabels;
		if (drawingPanel != null) {
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
	}

	public void setDrawColorScale(boolean drawColorScale) {
		this.drawColorScale = drawColorScale;
		if (drawingPanel != null) {
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
	}

	public void setDrawColumnLabelsVertically(boolean drawColumnLabelsVertically) {
		this.drawColumnLabelsVertically = drawColumnLabelsVertically;
		// Reset cached column label height since orientation changed
		cachedColumnLabelHeight = 0;
		if (drawingPanel != null) {
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
	}

	private void calculateMinMaxValues() {
		for (double[] row : data) {
			for (double v : row) {
				if (v < min)
					min = v;
				if (v > max)
					max = v;
			}
		}
	}

	/**
	 * Calculate the maximum width needed for row labels
	 * @param fm FontMetrics to use for measurement
	 * @return the maximum width in pixels
	 */
	private int calculateRowLabelWidth(FontMetrics fm) {
		if (!drawRowLabels) {
			return 0;
		}
		int maxWidth = 0;
		for (String name : rowNames) {
			int width = fm.stringWidth(name);
			if (width > maxWidth) {
				maxWidth = width;
			}
		}
		return maxWidth + margin; // Add some padding
	}

	/**
	 * Calculate the maximum height needed for column labels
	 * @param fm FontMetrics to use for measurement
	 * @return the maximum height in pixels
	 */
	private int calculateColumnLabelHeight(FontMetrics fm) {
		if (!drawColumnLabels) {
			return 0;
		}
		if (drawColumnLabelsVertically) {
			// For vertical labels, height is the max string width
			int maxWidth = 0;
			for (String name : columnNames) {
				int width = fm.stringWidth(name);
				if (width > maxWidth) {
					maxWidth = width;
				}
			}
			return maxWidth + margin;
		} else {
			// For horizontal labels, height is the font height
			return fm.getHeight() + margin;
		}
	}

	/**
	 * Calculate the total width needed for the color scale area
	 * @return the width in pixels
	 */
	private int getColorScaleSpace() {
		if (!drawColorScale) {
			return 0;
		}
		return COLOR_SCALE_MIN_GAP + COLOR_SCALE_BAR_WIDTH + COLOR_SCALE_LABEL_SPACE;
	}

	/**
	 * Truncate a string to fit within a given width
	 * @param text the original text
	 * @param fm FontMetrics for measurement
	 * @param maxWidth the maximum allowed width
	 * @return the truncated string with ellipsis if needed
	 */
	private String truncateString(String text, FontMetrics fm, int maxWidth) {
		if (fm.stringWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = fm.stringWidth(ellipsis);
		if (maxWidth <= ellipsisWidth) {
			return "";
		}
		int availableWidth = maxWidth - ellipsisWidth;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (fm.stringWidth(sb.toString() + text.charAt(i)) > availableWidth) {
				break;
			}
			sb.append(text.charAt(i));
		}
		return sb.toString() + ellipsis;
	}

	private JPanel createHeatMapPanel() {
		JPanel panel = new JPanel() {
			{
				addMouseMotionListener(new MouseMotionAdapter() {
					@Override
					public void mouseMoved(MouseEvent e) {
						Graphics g = getGraphics();
						if (g == null) {
							return;
						}
						g.setFont(labelFont);
						FontMetrics fm = g.getFontMetrics();
						
						int rowLabelWidth = drawRowLabels ? cachedRowLabelWidth : 0;
						int columnLabelHeight = drawColumnLabels ? cachedColumnLabelHeight : 0;
						int colorScaleSpace = getColorScaleSpace();

						int heatmapStartX = margin + rowLabelWidth;
						int heatmapStartY = margin + columnLabelHeight;
						
						int availableWidth = getWidth() - heatmapStartX - colorScaleSpace - margin;
						int availableHeight = getHeight() - heatmapStartY - margin;

						if (availableWidth <= 0 || availableHeight <= 0 || data.length == 0 || data[0].length == 0) {
							setToolTipText(null);
							g.dispose();
							return;
						}

						int colWidth = Math.max(minCellWidth, availableWidth / data[0].length);
						int rowHeight = Math.max(minCellHeight, availableHeight / data.length);

						int col = (e.getX() - heatmapStartX) / colWidth;
						int row = (e.getY() - heatmapStartY) / rowHeight;

						if (col >= 0 && col < data[0].length && row >= 0 && row < data.length) {
							setToolTipText("<html>Row: " + rowNames[row] + "<br>Column: " + columnNames[col]
									+ "<br>Value: " + data[row][col] + "</html>");
						} else {
							setToolTipText(null);
						}
						g.dispose();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				
				Graphics2D g2d = (Graphics2D) g;
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

				// Clear background to white
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, getWidth(), getHeight());

				// Set the font for the text labels
				g.setFont(labelFont);
				FontMetrics fm = g.getFontMetrics();

				// Calculate label dimensions based on actual text
				cachedRowLabelWidth = calculateRowLabelWidth(fm);
				cachedColumnLabelHeight = calculateColumnLabelHeight(fm);
				
				int rowLabelWidth = drawRowLabels ? cachedRowLabelWidth : 0;
				int columnLabelHeight = drawColumnLabels ? cachedColumnLabelHeight : 0;
				int colorScaleSpace = getColorScaleSpace();

				// Calculate heatmap area
				int heatmapStartX = margin + rowLabelWidth;
				int heatmapStartY = margin + columnLabelHeight;
				
				int availableWidth = getWidth() - heatmapStartX - colorScaleSpace - margin;
				int availableHeight = getHeight() - heatmapStartY - margin;

				if (availableWidth <= 0 || availableHeight <= 0) {
					return;
				}

				// Calculate the size of each cell
				int colWidth = Math.max(minCellWidth, availableWidth / data[0].length);
				int rowHeight = Math.max(minCellHeight, availableHeight / data.length);

				// Calculate actual heatmap dimensions
				int heatmapWidth = colWidth * data[0].length;
				int heatmapHeight = rowHeight * data.length;

				// Adjust the size of the panel based on the new cell sizes
				int requiredWidth = heatmapStartX + heatmapWidth + colorScaleSpace + margin;
				int requiredHeight = heatmapStartY + heatmapHeight + margin;
				
				Dimension newSize = new Dimension(requiredWidth, requiredHeight);
				if (!getPreferredSize().equals(newSize)) {
					setPreferredSize(newSize);
					revalidate();
				}

				// Draw the heatmap cells
				for (int row = 0; row < data.length; row++) {
					for (int col = 0; col < data[row].length; col++) {
						g.setColor(calculateColor(data[row][col]));
						g.fillRect(heatmapStartX + col * colWidth,
								heatmapStartY + row * rowHeight, 
								colWidth, rowHeight);
					}
				}

				// Draw cell borders for better visibility
				g.setColor(Color.LIGHT_GRAY);
				for (int row = 0; row <= data.length; row++) {
					g.drawLine(heatmapStartX, heatmapStartY + row * rowHeight,
							heatmapStartX + heatmapWidth, heatmapStartY + row * rowHeight);
				}
				for (int col = 0; col <= data[0].length; col++) {
					g.drawLine(heatmapStartX + col * colWidth, heatmapStartY,
							heatmapStartX + col * colWidth, heatmapStartY + heatmapHeight);
				}

				// Draw the row names if enabled
				if (drawRowLabels) {
					g.setColor(Color.BLACK);
					int fontAscent = fm.getAscent();
					int maxLabelWidth = rowLabelWidth - margin / 2;
					
					for (int row = 0; row < rowNames.length; row++) {
						String label = truncateString(rowNames[row], fm, maxLabelWidth);
						int labelWidth = fm.stringWidth(label);
						// Right-align the labels
						int x = margin + (rowLabelWidth - margin / 2 - labelWidth);
						int y = heatmapStartY + row * rowHeight + (rowHeight + fontAscent) / 2 - fm.getDescent();
						g.drawString(label, x, y);
					}
				}

				// Draw the column names if enabled
				if (drawColumnLabels) {
					g.setColor(Color.BLACK);
					
					if (drawColumnLabelsVertically) {
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setFont(labelFont);
						FontMetrics metrics = g2.getFontMetrics();
						int maxLabelHeight = columnLabelHeight - margin / 2;
						
						for (int col = 0; col < columnNames.length; col++) {
							String label = truncateString(columnNames[col], metrics, maxLabelHeight);
							int labelWidth = metrics.stringWidth(label);
							
							// Calculate center of cell
							int cellCenterX = heatmapStartX + col * colWidth + colWidth / 2;
							
							// Position: bottom of label area, centered on cell
							int x = cellCenterX + metrics.getDescent();
							int y = margin + labelWidth;
							
							drawRotate(g2, x, y, -90, label);
						}
						g2.dispose();
					} else {
						// Draw the column labels horizontally
						int maxLabelWidth = colWidth - 2; // Leave small padding
						
						for (int col = 0; col < columnNames.length; col++) {
							String label = truncateString(columnNames[col], fm, maxLabelWidth);
							int labelWidth = fm.stringWidth(label);
							
							// Center the label on the cell
							int x = heatmapStartX + col * colWidth + (colWidth - labelWidth) / 2;
							int y = margin + columnLabelHeight - fm.getDescent();
							
							g.drawString(label, x, y);
						}
					}
				}

				// Draw the color scale if enabled
				if (drawColorScale) {
					int scaleStartX = heatmapStartX + heatmapWidth + COLOR_SCALE_MIN_GAP;
					int scaleStartY = heatmapStartY;
					int scaleHeight = heatmapHeight;
					
					if (scaleHeight <= 0) {
						return;
					}

					// Draw the gradient bar
					for (int i = 0; i < scaleHeight; i++) {
						double value = max - (max - min) * i / (double)(scaleHeight - 1);
						g.setColor(calculateColor(value));
						g.fillRect(scaleStartX, scaleStartY + i, COLOR_SCALE_BAR_WIDTH, 1);
					}

					// Draw border around color scale
					g.setColor(Color.GRAY);
					g.drawRect(scaleStartX, scaleStartY, COLOR_SCALE_BAR_WIDTH, scaleHeight);

					// Draw the min and max labels for the color scale
					g.setColor(Color.BLACK);
					int labelX = scaleStartX + COLOR_SCALE_BAR_WIDTH + 5;
					
					// Format numbers nicely
					String maxLabel = formatValue(max);
					String minLabel = formatValue(min);
					
					// Max label at top
					g.drawString(maxLabel, labelX, scaleStartY + fm.getAscent());
					
					// Min label at bottom
					g.drawString(minLabel, labelX, scaleStartY + scaleHeight);
					
					// Middle value label
					double midValue = (max + min) / 2.0;
					String midLabel = formatValue(midValue);
					int midY = scaleStartY + scaleHeight / 2 + fm.getAscent() / 2;
					g.drawString(midLabel, labelX, midY);
				}
			}
		};
		panel.setBackground(Color.WHITE);
		return panel;
	}

	/**
	 * Format a value for display, avoiding excessive decimal places
	 * @param value the value to format
	 * @return formatted string
	 */
	private String formatValue(double value) {
		if (value == (long) value) {
			return String.valueOf((long) value);
		} else {
			return String.format("%.2f", value);
		}
	}

	/**
	 * Draw rotated text at the specified position
	 * 
	 * @param g2d   the graphics context
	 * @param x     the x position
	 * @param y     the y position
	 * @param angle the rotation angle in degrees
	 * @param text  the text to draw
	 */
	public static void drawRotate(Graphics2D g2d, double x, double y, int angle, String text) {
		g2d.translate((float) x, (float) y);
		g2d.rotate(Math.toRadians(angle));
		g2d.drawString(text, 0, 0);
		g2d.rotate(-Math.toRadians(angle));
		g2d.translate(-(float) x, -(float) y);
	}

	/**
	 * Calculate the color for a given value based on the min/max range.
	 * 
	 * @param value the data value
	 * @return the interpolated color
	 */
	protected Color calculateColor(double value) {
		// Handle the case where min == max to avoid division by zero
		if (max == min) {
			return Color.GREEN;
		}

		// Normalize the value to be between 0 and 1
		double normalizedValue = (value - min) / (max - min);

		// Clamp to [0, 1] range for safety
		normalizedValue = Math.max(0.0, Math.min(1.0, normalizedValue));

		// Define the colors for the start, middle, and end of the gradient
		Color startColor = Color.RED;
		Color middleColor = Color.GREEN;
		Color endColor = Color.BLUE;

		// Calculate the color based on the normalized value
		int r, g, b;
		if (normalizedValue < 0.5) {
			double ratio = normalizedValue * 2;
			r = (int) (startColor.getRed() * (1 - ratio) + middleColor.getRed() * ratio);
			g = (int) (startColor.getGreen() * (1 - ratio) + middleColor.getGreen() * ratio);
			b = (int) (startColor.getBlue() * (1 - ratio) + middleColor.getBlue() * ratio);
		} else {
			double ratio = (normalizedValue - 0.5) * 2;
			r = (int) (middleColor.getRed() * (1 - ratio) + endColor.getRed() * ratio);
			g = (int) (middleColor.getGreen() * (1 - ratio) + endColor.getGreen() * ratio);
			b = (int) (middleColor.getBlue() * (1 - ratio) + endColor.getBlue() * ratio);
		}

		// Clamp RGB values to valid range
		r = Math.max(0, Math.min(255, r));
		g = Math.max(0, Math.min(255, g));
		b = Math.max(0, Math.min(255, b));

		return new Color(r, g, b);
	}

	public boolean isDrawRowLabels() {
		return drawRowLabels;
	}

	public boolean isDrawColumnLabels() {
		return drawColumnLabels;
	}

	public boolean isDrawColorScale() {
		return drawColorScale;
	}

	public boolean isDrawColumnLabelsVertically() {
		return drawColumnLabelsVertically;
	}

	public double[][] getData() {
		return data;
	}

	public String[] getRowNames() {
		return rowNames;
	}

	public String[] getColumnNames() {
		return columnNames;
	}

	public int getMinCellWidth() {
		return minCellWidth;
	}

	public void setMinCellWidth(int minCellWidth) {
		this.minCellWidth = Math.max(minCellWidth, 1);
		if (drawingPanel != null) {
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
	}

	public int getMinCellHeight() {
		return minCellHeight;
	}

	public void setMinCellHeight(int minCellHeight) {
		this.minCellHeight = Math.max(minCellHeight, 1);
		if (drawingPanel != null) {
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
	}

	public void setCanvasSize(int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		if (drawingPanel != null) {
			drawingPanel.setPreferredSize(new Dimension(width, height));
		}
		if (scrollpane != null) {
			scrollpane.revalidate();
			scrollpane.repaint();
		}
	}

	public int getMargin() {
		return margin;
	}

	public void setMargin(int margin) {
		this.margin = Math.max(margin, 0);
		if (drawingPanel != null) {
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
		revalidate();
		repaint();
	}

	@Override
	public void repaint() {
		super.repaint();
		if (drawingPanel != null) {
			drawingPanel.repaint();
		}
	}
	
	/**
	 * Refresh the display - forces the drawing panel to recalculate its size and redraw
	 */
	public void refreshDisplay() {
		if (drawingPanel != null) {
			// Reset the preferred size to allow recalculation
			drawingPanel.setPreferredSize(null);
			drawingPanel.revalidate();
			drawingPanel.repaint();
		}
		if (scrollpane != null) {
			scrollpane.revalidate();
			scrollpane.repaint();
		}
		revalidate();
		repaint();
	}
}