package ca.pfv.spmf.gui.visual_pattern_viewer.rules;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JPanel;

import ca.pfv.spmf.gui.visual_pattern_viewer.ChipBlock;
import ca.pfv.spmf.gui.visual_pattern_viewer.MeasureTable;
import ca.pfv.spmf.gui.visual_pattern_viewer.ViewerStyle;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Displays an association rule as a compact card: the antecedent items on the
 * left and the consequent items on the right (as colored chips that wrap on
 * several lines if needed), separated by an arrow; the measures are listed
 * below in aligned columns. A negated side (negative rules) is shown with
 * red-tinted chips and a "NOT" label above it. Both sides are always
 * vertically aligned on the arrow.
 *
 * @author Philippe Fournier-Viger
 */
public class RulePanel extends JPanel {

	private static final long serialVersionUID = 5263914307631939188L;

	/** Padding between the card border and its content */
	private static final int PAD = 10;
	/** Chips of one side wrap when a row would be wider than this */
	private static final int MAX_SIDE_WIDTH = 190;
	/** Length of the implication arrow */
	private static final int ARROW_LENGTH = 26;
	/** Space between the arrow and the chips */
	private static final int ARROW_MARGIN = 8;
	/** Height reserved above the chips for the "NOT" label */
	private static final int NOT_LABEL_HEIGHT = 14;
	/** Minimum width of the content area */
	private static final int MIN_CONTENT_WIDTH = 150;

	/** The rule */
	private final Rule rule;
	/** Min value of each measure */
	private final Map<String, Double> minValues;
	/** Max value of each measure */
	private final Map<String, Double> maxValues;

	private boolean hover;
	private Dimension cachedPreferredSize;
	private MeasureTable measureTable;

	/**
	 * Constructor
	 *
	 * @param rule      the rule to display
	 * @param minValues map of minimum values for each measure
	 * @param maxValues map of maximum values for each measure
	 */
	public RulePanel(Rule rule, Map<String, Double> minValues, Map<String, Double> maxValues) {
		this.rule = Objects.requireNonNull(rule, "rule must not be null");
		this.minValues = minValues;
		this.maxValues = maxValues;
		setOpaque(false);
		setFont(ViewerStyle.itemFont());
		setToolTipText(buildToolTip());
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hover = false;
				repaint();
			}
		});
	}

	private MeasureTable measures() {
		if (measureTable == null) {
			measureTable = new MeasureTable(this, rule.getMeasures(), minValues, maxValues);
		}
		return measureTable;
	}

	private int notOffset() {
		return (rule.isAntecedentNegated() || rule.isConsequentNegated()) ? NOT_LABEL_HEIGHT : 0;
	}

	@Override
	public Dimension getPreferredSize() {
		if (cachedPreferredSize == null) {
			FontMetrics fm = getFontMetrics(ViewerStyle.itemFont());
			ChipBlock a = ChipBlock.layout(fm, rule.getAntecedent(), MAX_SIDE_WIDTH);
			ChipBlock c = ChipBlock.layout(fm, rule.getConsequent(), MAX_SIDE_WIDTH);
			int bodyW = a.getWidth() + ARROW_MARGIN + ARROW_LENGTH + ARROW_MARGIN + c.getWidth();
			int bodyH = notOffset() + Math.max(a.getHeight(), c.getHeight());
			int contentW = Math.max(MIN_CONTENT_WIDTH, Math.max(bodyW, measures().getPreferredWidth()));
			int contentH = bodyH + measures().getSectionHeight();
			cachedPreferredSize = new Dimension(contentW + 2 * PAD + ViewerStyle.SHADOW_DX,
					contentH + 2 * PAD + ViewerStyle.SHADOW_DY);
		}
		return new Dimension(cachedPreferredSize);
	}

	@Override
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			ViewerStyle.applyRenderingHints(g2);
			int w = getWidth();
			int h = getHeight();
			ViewerStyle.paintCard(g2, 0, 0, w, h, hover);

			int cardW = w - ViewerStyle.SHADOW_DX;
			int cardH = h - ViewerStyle.SHADOW_DY;
			int contentX = PAD;
			int contentW = cardW - 2 * PAD;
			if (contentW <= 0 || cardH <= 2 * PAD) {
				return;
			}

			MeasureTable mt = measures();
			int bodyTop = PAD;
			int bodyBottom = cardH - PAD - mt.getSectionHeight();

			FontMetrics fm = g2.getFontMetrics(ViewerStyle.itemFont());
			ChipBlock a = ChipBlock.layout(fm, rule.getAntecedent(), MAX_SIDE_WIDTH);
			ChipBlock c = ChipBlock.layout(fm, rule.getConsequent(), MAX_SIDE_WIDTH);
			int blocksH = Math.max(a.getHeight(), c.getHeight());
			int bodyW = a.getWidth() + ARROW_MARGIN + ARROW_LENGTH + ARROW_MARGIN + c.getWidth();
			int bodyH = notOffset() + blocksH;

			// Center the body in the area above the measures
			int bx = contentX + Math.max(0, (contentW - bodyW) / 2);
			int by = bodyTop + Math.max(0, (bodyBottom - bodyTop - bodyH) / 2);
			int blocksTop = by + notOffset();
			int midY = blocksTop + blocksH / 2;

			int ax = bx;
			int arrowX = bx + a.getWidth() + ARROW_MARGIN;
			int cx = arrowX + ARROW_LENGTH + ARROW_MARGIN;

			g2.setFont(ViewerStyle.itemFont());
			a.paint(g2, fm, ax, blocksTop + (blocksH - a.getHeight()) / 2,
					rule.isAntecedentNegated() ? ViewerStyle.NEGATED_CHIP_COLORS : null);
			if (rule.isAntecedentNegated()) {
				paintNotLabel(g2, ax, a.getWidth(), blocksTop);
			}

			ViewerStyle.paintArrow(g2, arrowX, midY, arrowX + ARROW_LENGTH, midY);

			g2.setFont(ViewerStyle.itemFont());
			c.paint(g2, fm, cx, blocksTop + (blocksH - c.getHeight()) / 2,
					rule.isConsequentNegated() ? ViewerStyle.NEGATED_CHIP_COLORS : null);
			if (rule.isConsequentNegated()) {
				paintNotLabel(g2, cx, c.getWidth(), blocksTop);
			}

			mt.paintAtBottom(g2, contentX, cardH - PAD, contentW);
		} finally {
			g2.dispose();
		}
	}

	/**
	 * Draws a small "NOT" label centered above a block of chips.
	 */
	private void paintNotLabel(Graphics2D g2, int blockX, int blockW, int blockTop) {
		Font f = ViewerStyle.smallFont().deriveFont(Font.BOLD);
		g2.setFont(f);
		FontMetrics fm = g2.getFontMetrics();
		String text = "NOT";
		int tx = blockX + (blockW - fm.stringWidth(text)) / 2;
		int ty = blockTop - 3;
		g2.setColor(ViewerStyle.NEGATED_CHIP_COLORS[2]);
		g2.drawString(text, tx, ty);
	}

	private String buildToolTip() {
		StringBuilder sb = new StringBuilder("<html><b>Rule</b><br>");
		if (rule.isAntecedentNegated()) {
			sb.append("<span style='color:#a01e1e'>NOT</span> ");
		}
		sb.append(ViewerStyle.escapeHtml(join(rule.getAntecedent())));
		sb.append(" &rarr; ");
		if (rule.isConsequentNegated()) {
			sb.append("<span style='color:#a01e1e'>NOT</span> ");
		}
		sb.append(ViewerStyle.escapeHtml(join(rule.getConsequent())));
		if (!rule.getMeasures().isEmpty()) {
			sb.append("<br><br>").append(MeasureTable.toHtmlRows(rule.getMeasures()));
		}
		sb.append("</html>");
		return sb.toString();
	}

	private static String join(List<String> items) {
		StringBuilder sb = new StringBuilder("{");
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(items.get(i));
		}
		return ViewerStyle.abbreviate(sb.append('}').toString(), 300);
	}

	/** @return the rule */
	public Rule getRule() {
		return rule;
	}

	/** Unused here but kept for API compatibility with the previous version */
	@SuppressWarnings("unused")
	private static final Color UNUSED = null;
}