package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */

/**
 * A simple two-handle slider (minimum and maximum) with integer positions in
 * [0, MAX]. Double-click resets it to the full range. The arrow keys move the
 * nearest handle (Shift+arrows move by a bigger step).
 * 
 * @author Philippe Fournier-Viger
 */
public class RangeSlider extends JComponent {

	private static final long serialVersionUID = 1L;

	public static final int MAX = 1000;
	private static final int THUMB = 7;
	private static final Color TRACK = new Color(215, 215, 215);
	private static final Color RANGE = new Color(70, 130, 180);

	private int low = 0;
	private int high = MAX;
	/** 0 = dragging the low handle, 1 = the high handle, -1 = none */
	private int dragging = -1;
	private final List<ChangeListener> listeners = new ArrayList<>();

	public RangeSlider() {
		setPreferredSize(new Dimension(150, 22));
		setMinimumSize(new Dimension(50, 22));
		setFocusable(true);
		setToolTipText("Drag the handles to set the minimum and maximum. Double-click to reset. "
				+ "Arrow keys move the nearest handle (Shift = bigger step).");
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!isEnabled()) {
					return;
				}
				requestFocusInWindow();
				if (e.getClickCount() == 2) {
					setValues(0, MAX);
					return;
				}
				int x = e.getX();
				int dl = Math.abs(x - toX(low));
				int dh = Math.abs(x - toX(high));
				dragging = (dl < dh || (dl == dh && x < toX(low))) ? 0 : 1;
				dragTo(x);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (dragging >= 0) {
					dragTo(e.getX());
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				dragging = -1;
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int step = Math.max(1, MAX / 200);
				if (e.isShiftDown()) {
					step *= 5;
				}
				if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT) {
					int dir = e.getKeyCode() == KeyEvent.VK_LEFT ? -1 : 1;
					// move the handle that is closer to the middle of the
					// selection (or the one that can move)
					int dl = Math.abs(low - (low + high) / 2);
					int dh = Math.abs(high - (low + high) / 2);
					int target = dl <= dh ? 0 : 1;
					if (target == 0) {
						setValues(low + dir * step, high);
					} else {
						setValues(low, high + dir * step);
					}
					e.consume();
				}
			}
		});
	}

	private void dragTo(int x) {
		int v = toValue(x);
		if (dragging == 0) {
			setValues(Math.min(v, high), high);
		} else {
			setValues(low, Math.max(v, low));
		}
	}

	public int getLow() {
		return low;
	}

	public int getHigh() {
		return high;
	}

	public boolean isFullRange() {
		return low == 0 && high == MAX;
	}

	/** Set both handles (values are clamped; fires a change event if changed). */
	public void setValues(int newLow, int newHigh) {
		newLow = Math.max(0, Math.min(MAX, newLow));
		newHigh = Math.max(0, Math.min(MAX, newHigh));
		if (newLow > newHigh) {
			newLow = newHigh;
		}
		if (newLow != low || newHigh != high) {
			low = newLow;
			high = newHigh;
			repaint();
			ChangeEvent ev = new ChangeEvent(this);
			for (ChangeListener l : listeners) {
				l.stateChanged(ev);
			}
		}
	}

	public void addChangeListener(ChangeListener l) {
		listeners.add(l);
	}

	private int toX(int value) {
		int pad = THUMB + 2;
		return pad + (int) Math.round(value / (double) MAX * (getWidth() - 2 * pad));
	}

	private int toValue(int x) {
		int pad = THUMB + 2;
		double t = (x - pad) / (double) Math.max(1, getWidth() - 2 * pad);
		return (int) Math.round(Math.max(0, Math.min(1, t)) * MAX);
	}

	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int cy = getHeight() / 2;
		int pad = THUMB + 2;
		g.setColor(TRACK);
		g.fillRoundRect(pad, cy - 3, w - 2 * pad, 6, 6, 6);
		int xl = toX(low);
		int xh = toX(high);
		g.setColor(isEnabled() ? RANGE : Color.GRAY);
		g.fillRoundRect(xl, cy - 3, Math.max(1, xh - xl), 6, 6, 6);
		for (int x : new int[] { xl, xh }) {
			g.setColor(Color.WHITE);
			g.fillOval(x - THUMB, cy - THUMB, 2 * THUMB, 2 * THUMB);
			g.setColor(RANGE.darker());
			g.drawOval(x - THUMB, cy - THUMB, 2 * THUMB, 2 * THUMB);
		}
		g.dispose();
	}
}
