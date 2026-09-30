package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.function.DoubleSupplier;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

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
 * Table cell renderers shared by the panels of the Interactive Pattern
 * Explorer.
 * 
 * @author Philippe Fournier-Viger
 */
final class ExplorerRenderers {

	private ExplorerRenderers() {
	}

	/** @return a compact textual form of a cell value */
	static String format(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof Double || value instanceof Float) {
			return ExplorerFormat.number(((Number) value).doubleValue());
		}
		if (value instanceof Number) {
			return ExplorerFormat.integer(((Number) value).longValue());
		}
		return value.toString();
	}

	/** Right-aligned renderer for numbers */
	static final class NumberRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		NumberRenderer() {
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		protected void setValue(Object value) {
			setText(format(value));
		}
	}

	/** Renderer showing a fraction (0..1) as a percentage */
	static final class PercentRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		PercentRenderer() {
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		protected void setValue(Object value) {
			setText(value instanceof Number ? ExplorerFormat.percent(((Number) value).doubleValue()) : "");
		}
	}

	/**
	 * Renderer that draws a horizontal bar behind the number, proportional to a
	 * maximum given by a supplier.
	 */
	static final class BarRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		private final DoubleSupplier maxSupplier;
		private final Color color;
		private double fraction;

		BarRenderer(DoubleSupplier maxSupplier, Color color) {
			this.maxSupplier = maxSupplier;
			this.color = color;
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			double max = maxSupplier.getAsDouble();
			double v = value instanceof Number ? ((Number) value).doubleValue() : 0;
			fraction = (max > 0 && !Double.isNaN(v)) ? Math.max(0, Math.min(1, v / max)) : 0;
			setText(format(value));
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			int w = getWidth();
			int h = getHeight();
			g.setColor(getBackground());
			g.fillRect(0, 0, w, h);
			int barWidth = (int) Math.round(fraction * (w - 6));
			if (barWidth > 0) {
				g.setColor(ExplorerStyle.withAlpha(color, 90));
				g.fillRect(3, 3, barWidth, h - 6);
			}
			// Paint only the text over the bar
			setOpaque(false);
			super.paintComponent(g);
			setOpaque(true);
		}
	}
}