package ca.pfv.spmf.gui;

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
 *
 * Do not remove copyright or license information.
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

/**
 * This class provides a custom cell renderer for the algorithm-selection
 * combo box in the SPMF GUI. It visually distinguishes between category
 * headers (rendered in bold red) and individual algorithm names (rendered
 * in regular black with indentation).
 *
 * <p>Category headers are identified by a {@code " --"} prefix in the
 * string value. Algorithm names are indented with three leading spaces.
 * Empty strings are rendered as blank rows.</p>
 *
 * <p>Non-selected items are rendered with a white background regardless of
 * the Look and Feel, matching the original SPMF design. Selected items use
 * the standard selection colors from the JList.</p>
 *
 * <p>The renderer reuses a single {@link JLabel} instance across all
 * render calls for efficiency, updating only the text, font, and colors
 * as needed.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmComboBoxRenderer implements ListCellRenderer<Object> {

    /** Serial UID */
    private static final long serialVersionUID = 234234235L;

    /** The prefix that identifies a category header in the combo box model */
    private static final String CATEGORY_PREFIX = " --";

    /** The indentation applied to algorithm names (not categories) */
    private static final String ALGORITHM_INDENT = "   ";

    /** The background color for non-selected items (forced white) */
    private static final Color BACKGROUND_COLOR = Color.WHITE;

    /** The color used for category headers */
    private static final Color CATEGORY_COLOR = new Color(180, 0, 0);

    /** The color used for algorithm names */
    private static final Color ALGORITHM_COLOR = Color.BLACK;

    /** Reusable label component that is returned on every render call */
    private final JLabel label;

    /** The base font from the combo box, cached on construction */
    private final Font baseFont;

    /** The bold variant of the base font, derived once and cached */
    private final Font boldFont;

    /**
     * Constructs a renderer for the given combo box. The base font is
     * extracted from the combo box at construction time and cached so that
     * font derivation does not occur on every render.
     *
     * @param combo the {@link javax.swing.JComboBox} whose font and style
     *              are used as the base for rendering
     */
    public AlgorithmComboBoxRenderer(javax.swing.JComboBox<?> combo) {
        label = new JLabel();
        label.setOpaque(true);
        // Add a small horizontal border so text is not flush against the edge
        label.setBorder(new EmptyBorder(2, 4, 2, 4));

        // Cache the base font and pre-derive the bold variant once
        baseFont = combo.getFont();
        boldFont = baseFont.deriveFont(baseFont.getStyle() | Font.BOLD);

        // Set the initial font to the base font
        label.setFont(baseFont);
    }

    /**
     * Returns a component configured to render the given value at the
     * specified index in the list. Category headers (identified by a
     * {@code " --"} prefix) are rendered in bold red. Algorithm names are
     * rendered in regular black with three-space indentation. Empty strings
     * are rendered as blank rows.
     *
     * <p>The background color is always white for non-selected items,
     * regardless of the Look and Feel. Selected items use the standard
     * selection background color from the JList.</p>
     *
     * @param list         the JList being rendered
     * @param value        the value to render (expected to be a String)
     * @param index        the cell index (0-based; -1 when rendering the
     *                     selected item in the combo box's button area)
     * @param isSelected   true if the cell is selected
     * @param cellHasFocus true if the cell has focus
     * @return the configured label component
     */
    @Override
    public Component getListCellRendererComponent(JList<?> list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {

        // Set background color: white for non-selected, selection color for selected
        if (isSelected) {
            label.setBackground(list.getSelectionBackground());
            label.setForeground(list.getSelectionForeground());
        } else {
            label.setBackground(BACKGROUND_COLOR);
            label.setForeground(ALGORITHM_COLOR);
        }

        // Convert the value to a string
        String text = (value == null) ? "" : value.toString();

        // Determine the rendering style based on the text content
        if (text.isEmpty()) {
            // Empty string: render as a blank row
            label.setText(" ");
            label.setFont(baseFont);
            // Keep the default foreground color (black or selection color)

        } else if (index > 0 && text.startsWith(CATEGORY_PREFIX)) {
            // Category header: bold red text, no indentation
            label.setText(text);
            label.setFont(boldFont);
            // Override foreground color to red only if not selected
            if (!isSelected) {
                label.setForeground(CATEGORY_COLOR);
            }

        } else {
            // Algorithm name: regular font, black text, indented
            label.setText(ALGORITHM_INDENT + text);
            label.setFont(baseFont);
            // Foreground is already set to black or selection color above
        }

        return label;
    }
}