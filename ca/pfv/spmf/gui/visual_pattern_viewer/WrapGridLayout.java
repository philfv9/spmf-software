package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

import javax.swing.JViewport;

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
 * A grid layout where all cells have the same size (the largest preferred size
 * of the children) but the number of columns adapts to the available width,
 * like a {@code GridLayout} that wraps. Cells are never stretched, and the grid
 * is centered horizontally. Designed for a panel placed in a
 * {@link javax.swing.JScrollPane} that tracks the viewport width.
 *
 * @author Philippe Fournier-Viger
 */
public class WrapGridLayout implements LayoutManager {

	private final int hgap;
	private final int vgap;
	private final int fallbackColumns;

	/**
	 * @param hgap            horizontal gap (also used as left/right margin)
	 * @param vgap            vertical gap (also used as top/bottom margin)
	 * @param fallbackColumns number of columns used when the available width is
	 *                        not known yet (before the component is shown)
	 */
	public WrapGridLayout(int hgap, int vgap, int fallbackColumns) {
		this.hgap = Math.max(0, hgap);
		this.vgap = Math.max(0, vgap);
		this.fallbackColumns = Math.max(1, fallbackColumns);
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {
	}

	@Override
	public void removeLayoutComponent(Component comp) {
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets in = parent.getInsets();
			int n = countVisible(parent);
			Dimension cell = cellSize(parent);
			int cols = computeColumns(parent, cell.width, n);
			int rows = (n == 0) ? 0 : (n + cols - 1) / cols;
			int w = in.left + in.right + hgap + cols * (cell.width + hgap);
			int h = in.top + in.bottom + vgap + rows * (cell.height + vgap);
			return new Dimension(w, h);
		}
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets in = parent.getInsets();
			Dimension cell = cellSize(parent);
			return new Dimension(in.left + in.right + cell.width + 2 * hgap,
					in.top + in.bottom + cell.height + 2 * vgap);
		}
	}

	@Override
	public void layoutContainer(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets in = parent.getInsets();
			int n = countVisible(parent);
			if (n == 0) {
				return;
			}
			Dimension cell = cellSize(parent);
			int cols = computeColumns(parent, cell.width, n);
			int gridW = cols * cell.width + (cols - 1) * hgap;
			int availW = parent.getWidth() - in.left - in.right;
			int x0 = in.left + Math.max(hgap, (availW - gridW) / 2);
			int y0 = in.top + vgap;

			int i = 0;
			for (Component c : parent.getComponents()) {
				if (!c.isVisible()) {
					continue;
				}
				int col = i % cols;
				int row = i / cols;
				c.setBounds(x0 + col * (cell.width + hgap), y0 + row * (cell.height + vgap), cell.width,
						cell.height);
				i++;
			}
		}
	}

	private int computeColumns(Container parent, int cellWidth, int n) {
		if (n <= 0 || cellWidth <= 0) {
			return 1;
		}
		int avail = availableWidth(parent);
		int cols = (avail <= 0) ? fallbackColumns : (avail - hgap) / (cellWidth + hgap);
		return Math.max(1, Math.min(cols, n));
	}

	/**
	 * The width that the container will have. When inside a viewport, the
	 * viewport width is used (the container tracks it), otherwise the current
	 * width of the container.
	 */
	private int availableWidth(Container parent) {
		Insets in = parent.getInsets();
		int w = 0;
		if (parent.getParent() instanceof JViewport vp) {
			w = vp.getExtentSize().width;
		}
		if (w <= 0) {
			w = parent.getWidth();
		}
		return w - in.left - in.right;
	}

	private static int countVisible(Container parent) {
		int n = 0;
		for (Component c : parent.getComponents()) {
			if (c.isVisible()) {
				n++;
			}
		}
		return n;
	}

	private static Dimension cellSize(Container parent) {
		int w = 0;
		int h = 0;
		for (Component c : parent.getComponents()) {
			if (!c.isVisible()) {
				continue;
			}
			Dimension d = c.getPreferredSize();
			w = Math.max(w, d.width);
			h = Math.max(h, d.height);
		}
		return new Dimension(w, h);
	}
}