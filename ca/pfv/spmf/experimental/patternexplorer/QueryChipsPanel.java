package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import ca.pfv.spmf.experimental.patternexplorer.ExplorerQuery.Restriction;

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
 * Displays the restrictions of the current query as colored chips. Each chip
 * has a "×" button removing only that restriction; a "Clear all" button
 * removes everything.
 * 
 * @author Philippe Fournier-Viger
 */
public class QueryChipsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final ExplorerController controller;
	private final JPanel chipsContainer;
	private final JButton clearAllButton;

	/**
	 * Constructor
	 * 
	 * @param controller the explorer controller
	 */
	public QueryChipsPanel(ExplorerController controller) {
		super(new BorderLayout(6, 0));
		this.controller = controller;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

		JLabel title = new JLabel("Current query:");
		title.setFont(ExplorerStyle.FONT_BOLD);
		title.setVerticalAlignment(SwingConstants.TOP);
		title.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
		add(title, BorderLayout.WEST);

		chipsContainer = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
		chipsContainer.setOpaque(false);
		add(chipsContainer, BorderLayout.CENTER);

		clearAllButton = new JButton("Clear all");
		ExplorerStyle.makeCompact(clearAllButton);
		clearAllButton.setToolTipText("Remove all restrictions and return to the whole collection");
		clearAllButton.addActionListener(e -> controller.applyQuery(ExplorerQuery.UNRESTRICTED));
		JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		east.setOpaque(false);
		east.add(clearAllButton);
		add(east, BorderLayout.EAST);
	}

	/**
	 * Show the restrictions of a query.
	 * 
	 * @param query the current query
	 */
	public void update(ExplorerQuery query) {
		chipsContainer.removeAll();
		List<Restriction> restrictions = query.getRestrictions();
		if (restrictions.isEmpty()) {
			JLabel none = new JLabel(
					"no restriction \u2013 select an item, a pattern size or a measure range to drill down");
			none.setFont(ExplorerStyle.FONT_ITALIC);
			none.setForeground(ExplorerStyle.MUTED);
			chipsContainer.add(none);
		} else {
			for (Restriction restriction : restrictions) {
				chipsContainer.add(new Chip(restriction));
			}
		}
		clearAllButton.setEnabled(!restrictions.isEmpty());
		chipsContainer.revalidate();
		chipsContainer.repaint();
		revalidate();
		repaint();
	}

	/** A rounded chip with a label and a close button */
	private final class Chip extends JPanel {
		private static final long serialVersionUID = 1L;
		private final Color background;
		private final Color foreground;

		Chip(Restriction restriction) {
			super(new FlowLayout(FlowLayout.LEFT, 3, 2));
			setOpaque(false);
			switch (restriction.getKind()) {
			case REQUIRED:
				background = ExplorerStyle.REQUIRED_BG;
				foreground = ExplorerStyle.REQUIRED_FG;
				break;
			case EXCLUDED:
				background = ExplorerStyle.EXCLUDED_BG;
				foreground = ExplorerStyle.EXCLUDED_FG;
				break;
			default:
				background = ExplorerStyle.OTHER_BG;
				foreground = ExplorerStyle.OTHER_FG;
			}
			JLabel label = new JLabel(restriction.getLabel());
			label.setFont(ExplorerStyle.FONT_BOLD);
			label.setForeground(foreground);
			label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

			JButton close = new JButton("\u00D7");
			close.setFont(ExplorerStyle.FONT_BOLD);
			close.setForeground(foreground);
			close.setContentAreaFilled(false);
			close.setBorderPainted(false);
			close.setFocusable(false);
			close.setMargin(new Insets(0, 3, 0, 3));
			close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			close.setToolTipText("Remove this restriction");
			close.addActionListener(e -> controller.applyQuery(restriction.getQueryWithout()));

			setToolTipText(restriction.getTooltip());
			add(label);
			add(close);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(background);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
			g2.setColor(ExplorerStyle.withAlpha(foreground, 120));
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
			g2.dispose();
			super.paintComponent(g);
		}
	}
}