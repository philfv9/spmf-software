package ca.pfv.spmf.gui.texteditor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import javax.swing.text.Utilities;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * Object to add line numbers to a text area with enhanced visual appearance
 * and performance optimizations.
 * 
 * Features:
 * - Right-aligned line numbers
 * - Current line highlighting
 * - Configurable colors for day/night mode
 * - Performance optimized painting
 * - Anti-aliased text rendering
 * - Border separator between numbers and text
 *
 */
public class LineNumberPane extends JPanel {

	/**
	 * serial UID
	 */
	private static final long serialVersionUID = 1076288361088590368L;

	// ==================== CONSTANTS ====================
	/** Default padding on left side */
	private static final int LEFT_PADDING = 5;
	
	/** Default padding on right side */
	private static final int RIGHT_PADDING = 5;
	
	/** Minimum width to show at least 3 digits */
	private static final String MIN_WIDTH_STRING = "999";
	
	// ==================== INSTANCE VARIABLES ====================
	/** Text area */
	private JTextArea textArea;
	
	/** Background color for line numbers */
	private Color backgroundColor = new Color(240, 240, 240);
	
	/** Foreground color for line numbers */
	private Color foregroundColor = new Color(120, 120, 120);
	
	/** Color for current line number */
	private Color currentLineColor = new Color(60, 60, 60);
	
	/** Border color */
	private Color borderColor = new Color(200, 200, 200);
	
	/** Background color for current line */
	private Color currentLineBackgroundColor = new Color(220, 220, 220);
	
	/** Font metrics cache */
	private FontMetrics cachedFontMetrics = null;
	
	/** Last calculated width */
	private int lastWidth = 0;
	
	/** Last line count */
	private int lastLineCount = 0;

	/**
	 * Constructor
	 * 
	 * @param ta a text area
	 */
	public LineNumberPane(JTextArea ta) {
		this.textArea = ta;
		
		// Set initial background
		setBackground(backgroundColor);
		
		// Create border with separator line
		updateBorder();
		
		// Add document listener to repaint when text changes
		ta.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				invalidateCache();
				revalidate();
				repaint();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				invalidateCache();
				revalidate();
				repaint();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				invalidateCache();
				revalidate();
				repaint();
			}
		});
		
		// Add caret listener to repaint when cursor moves (for current line highlighting)
		ta.addCaretListener(e -> repaint());
	}
	
	/**
	 * Update the border with current border color
	 */
	private void updateBorder() {
		setBorder(new CompoundBorder(
				new MatteBorder(0, 0, 0, 1, borderColor),
				new EmptyBorder(0, LEFT_PADDING, 0, RIGHT_PADDING)
		));
	}
	
	/**
	 * Invalidate cached values
	 */
	private void invalidateCache() {
		cachedFontMetrics = null;
		lastWidth = 0;
	}
	
	/**
	 * Set colors for night mode
	 * 
	 * @param nightMode true for night mode, false for day mode
	 */
	public void setNightMode(boolean nightMode) {
		if (nightMode) {
			backgroundColor = new Color(30, 30, 30);
			foregroundColor = new Color(150, 150, 150);
			currentLineColor = new Color(220, 220, 220);
			borderColor = new Color(60, 60, 60);
			currentLineBackgroundColor = new Color(50, 50, 50);
		} else {
			backgroundColor = new Color(240, 240, 240);
			foregroundColor = new Color(120, 120, 120);
			currentLineColor = new Color(60, 60, 60);
			borderColor = new Color(200, 200, 200);
			currentLineBackgroundColor = new Color(220, 220, 220);
		}
		
		setBackground(backgroundColor);
		updateBorder();
		repaint();
	}
	
	/**
	 * Set custom colors
	 * 
	 * @param bg background color
	 * @param fg foreground color
	 * @param currentLine current line color
	 */
	public void setColors(Color bg, Color fg, Color currentLine) {
		this.backgroundColor = bg;
		this.foregroundColor = fg;
		this.currentLineColor = currentLine;
		setBackground(backgroundColor);
		repaint();
	}

	@Override
	public Dimension getPreferredSize() {
		// Get or create font metrics
		if (cachedFontMetrics == null) {
			cachedFontMetrics = getFontMetrics(textArea.getFont());
		}
		
		int lineCount = textArea.getLineCount();
		
		// Only recalculate width if line count changed significantly
		if (lastWidth == 0 || lineCount != lastLineCount) {
			Insets insets = getInsets();
			
			// Calculate width based on maximum line number
			int minWidth = cachedFontMetrics.stringWidth(MIN_WIDTH_STRING);
			int maxWidth = cachedFontMetrics.stringWidth(Integer.toString(lineCount));
			
			lastWidth = Math.max(minWidth, maxWidth) + insets.left + insets.right;
			lastLineCount = lineCount;
		}
		
		// Height should match the text area
		int height = textArea.getPreferredSize().height;
		
		return new Dimension(lastWidth, height);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		// Enable anti-aliasing for smoother text
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, 
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, 
				RenderingHints.VALUE_RENDER_QUALITY);
		
		// Use text area font
		g.setFont(textArea.getFont());
		FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
		Insets insets = getInsets();
		
		// Get visible rectangle to only paint visible lines
		Rectangle clip = g.getClipBounds();
		
		// Determine visible line range
		int rowStartOffset = textArea.viewToModel2D(new Point(0, clip.y));
		int endOffset = textArea.viewToModel2D(new Point(0, clip.y + clip.height));
		
		// Get current line for highlighting
		int currentLine = getCurrentLine();
		
		Element root = textArea.getDocument().getDefaultRootElement();
		
		while (rowStartOffset <= endOffset) {
			try {
				int index = root.getElementIndex(rowStartOffset);
				Element line = root.getElement(index);
				
				// Only draw line number at the start of each line
				if (line.getStartOffset() == rowStartOffset) {
					String lineNumber = String.valueOf(index + 1);
					
					// Get the rectangle for this line
					Rectangle2D r = textArea.modelToView2D(rowStartOffset);
					int y = (int) (r.getY() + r.getHeight());
					
					// Highlight current line background
					if (index + 1 == currentLine) {
						g.setColor(currentLineBackgroundColor);
						g.fillRect(0, (int) r.getY(), getWidth(), (int) r.getHeight());
					}
					
					// Set color based on whether this is the current line
					if (index + 1 == currentLine) {
						g.setColor(currentLineColor);
					} else {
						g.setColor(foregroundColor);
					}
					
					// Right-align the line numbers
					int stringWidth = fm.stringWidth(lineNumber);
					int x = getWidth() - insets.right - stringWidth;
					
					// Draw the line number
					g.drawString(lineNumber, x, y - fm.getDescent());
				}
				
				// Move to the next row
				rowStartOffset = Utilities.getRowEnd(textArea, rowStartOffset) + 1;
			} catch (Exception e) {
				// If there's an error, stop painting
				break;
			}
		}
	}
	
	/**
	 * Get the current line number (1-based) where the caret is located
	 * 
	 * @return the current line number, or -1 if error
	 */
	private int getCurrentLine() {
		try {
			int caretPosition = textArea.getCaretPosition();
			Element root = textArea.getDocument().getDefaultRootElement();
			return root.getElementIndex(caretPosition) + 1;
		} catch (Exception e) {
			return -1;
		}
	}
}