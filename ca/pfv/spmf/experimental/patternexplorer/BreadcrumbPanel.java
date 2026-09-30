package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.Cursor;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
 * The breadcrumb of the exploration: one clickable step per state of the
 * {@link ExplorationHistory}, e.g. "All patterns › + milk › + bread". Steps
 * after the current one (reachable with "Forward") are shown dimmed.
 * 
 * @author Philippe Fournier-Viger
 */
public class BreadcrumbPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Called with the index of the step selected by the user */
	private final IntConsumer onStepSelected;

	/**
	 * Constructor
	 * 
	 * @param onStepSelected callback receiving the history index to go to
	 */
	public BreadcrumbPanel(IntConsumer onStepSelected) {
		super(new WrapLayout(FlowLayout.LEFT, 2, 2));
		this.onStepSelected = onStepSelected;
		setOpaque(false);
	}

	/**
	 * Rebuild the breadcrumb from the history.
	 * 
	 * @param history the exploration history
	 */
	public void update(ExplorationHistory history) {
		removeAll();
		List<ExplorerQuery> states = history.getStates();
		int current = history.getCurrentIndex();
		for (int i = 0; i < states.size(); i++) {
			if (i > 0) {
				JLabel separator = new JLabel("\u203A");
				separator.setFont(ExplorerStyle.FONT_BOLD);
				separator.setForeground(ExplorerStyle.MUTED);
				add(separator);
			}
			String label = i == 0 ? "All patterns"
					: ExplorationHistory.stepLabel(states.get(i - 1), states.get(i));
			JButton button = new JButton(label);
			ExplorerStyle.makeCompact(button);
			button.setToolTipText(ExplorerHtml.tooltip(states.get(i).describe()
					+ (i == current ? "" : "\n(click to return to this step)")));
			if (i == current) {
				button.setFont(ExplorerStyle.FONT_BOLD);
				button.setBackground(ExplorerStyle.withAlpha(ExplorerStyle.ACCENT, 40));
				button.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(ExplorerStyle.ACCENT),
						BorderFactory.createEmptyBorder(2, 6, 2, 6)));
			} else {
				button.setContentAreaFilled(false);
				button.setBorderPainted(false);
				button.setForeground(i > current ? ExplorerStyle.MUTED : ExplorerStyle.LINK);
				button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			final int index = i;
			button.addActionListener(e -> onStepSelected.accept(index));
			add(button);
		}
		revalidate();
		repaint();
	}
}