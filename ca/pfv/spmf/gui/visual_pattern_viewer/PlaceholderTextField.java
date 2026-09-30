package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.JTextField;

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
 * A text field that shows a grey hint when it is empty.
 *
 * @author Philippe Fournier-Viger
 */
class PlaceholderTextField extends JTextField {

	private static final long serialVersionUID = 1L;

	private String placeholder;

	PlaceholderTextField(String placeholder, int columns) {
		super(columns);
		this.placeholder = placeholder;
	}

	void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (placeholder == null || placeholder.isEmpty() || !getText().isEmpty()) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			ViewerStyle.applyRenderingHints(g2);
			g2.setFont(getFont());
			g2.setColor(ViewerStyle.withAlpha(ViewerStyle.TEXT_SECONDARY, 150));
			FontMetrics fm = g2.getFontMetrics();
			Insets in = getInsets();
			int x = (getHorizontalAlignment() == RIGHT) ? getWidth() - in.right - fm.stringWidth(placeholder) - 1
					: in.left + 1;
			int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(placeholder, x, y);
		} finally {
			g2.dispose();
		}
	}
}