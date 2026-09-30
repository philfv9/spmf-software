package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.border.Border;

/**
 * Cell renderer for algorithm combo boxes. Category headers (values starting
 * with {@code " --"}) are shown as bold, tinted section rows; algorithm names
 * are indented beneath them. Selection, disabled and Look-and-Feel colours are
 * inherited from {@link DefaultListCellRenderer}.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmComboBoxRenderer extends DefaultListCellRenderer {

    private static final long serialVersionUID = 234234235L;

    /** Prefix identifying a category header in the model. */
    private static final String CATEGORY_PREFIX = " --";

    private static final Color CATEGORY_FOREGROUND = new Color(160, 24, 24);
    private static final Color CATEGORY_BACKGROUND = new Color(244, 246, 248);

    private static final Border BORDER_ALGORITHM = BorderFactory.createEmptyBorder(2, 18, 2, 6);
    private static final Border BORDER_CATEGORY  = BorderFactory.createEmptyBorder(5, 6, 3, 6);
    private static final Border BORDER_PLAIN     = BorderFactory.createEmptyBorder(2, 6, 2, 6);

    private Font plainFont;
    private Font boldFont;

    /**
     * @param combo the combo box this renderer is installed on (kept for API
     *              compatibility; fonts are read from the list at render time so
     *              that font changes are honoured)
     */
    public AlgorithmComboBoxRenderer(JComboBox<?> combo) {
        // nothing to cache: fonts and colours come from the list when rendering
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        Font base = list.getFont();
        if (plainFont == null || !plainFont.equals(base)) {
            plainFont = base;
            boldFont = base.deriveFont(base.getStyle() | Font.BOLD);
        }

        String text = (value == null) ? "" : value.toString();
        boolean inDropDown = index >= 0; // -1 = the combo box's own display area

        if (text.isEmpty()) {
            setText(" ");
            setFont(plainFont);
            setBorder(BORDER_PLAIN);
            setToolTipText(null);
        } else if (text.startsWith(CATEGORY_PREFIX)) {
            String name = text.replaceAll("^[\\s-]+|[\\s-]+$", "");
            setText(name.isEmpty() ? text.trim() : name);
            setFont(boldFont);
            setBorder(inDropDown ? BORDER_CATEGORY : BORDER_PLAIN);
            setToolTipText(null);
            if (inDropDown && !isSelected && list.isEnabled()) {
                setForeground(CATEGORY_FOREGROUND);
                setBackground(CATEGORY_BACKGROUND);
            }
        } else {
            setText(text);
            setFont(plainFont);
            setBorder(inDropDown ? BORDER_ALGORITHM : BORDER_PLAIN);
            setToolTipText(text);
        }
        return this;
    }
}