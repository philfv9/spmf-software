package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Displays a sequential pattern as a compact card. Each itemset is drawn as a
 * vertical stack of colored chips (itemsets with several items are framed),
 * successive itemsets are linked by arrows, and the sequence wraps onto
 * several rows when it is long. The measures are listed below in aligned
 * columns.
 *
 * @author Philippe Fournier-Viger
 */
public class SequentialPatternPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	/** Padding between the card border and its content */
	private static final int PAD = 10;
	/** The sequence wraps when a row would be wider than this */
	private static final int MAX_ROW_WIDTH = 420;
	/** Length of the arrows between itemsets */
	private static final int ARROW_LENGTH = 18;
	/** Space between an arrow and the itemsets */
	private static final int ARROW_MARGIN = 5;
	/** Padding inside the frame of a multi-item itemset */
	private static final int GROUP_PAD = 5;
	/** Gap between two rows of the sequence */
	private static final int ROW_GAP = 8;
	/** Minimum width of the content area */
	private static final int MIN_CONTENT_WIDTH = 150;

	/** The sequential pattern to display */
	private final SequentialPattern pattern;
	/** Min values of the measures */
	private final Map<String, Double> minValues;
	/** Max values of the measures */
	private final Map<String, Double> maxValues;

	private boolean hover;
	private Dimension cachedPreferredSize;
	private MeasureTable measureTable;

	/** One itemset of the sequence, laid out */
	private static final class Group {
		ChipBlock block;
		boolean framed;
		int w;
		int h;
	}

	/** One row of the (possibly wrapped) sequence */
	private static final class Row {
		final List<Integer> groups = new ArrayList<>();
		final List<Integer> xs = new ArrayList<>();
		int width;
		int height;
	}

	/** Complete layout of the sequence */
	private static final class SeqLayout {
		final List<Group> groups = new ArrayList<>();
		final List<Row> rows = new ArrayList<>();
		int width;
		int height;
	}

	/**
	 * Constructor
	 *
	 * @param pattern   the sequential pattern (not null)
	 * @param minValues map of minimum measure values, or null
	 * @param maxValues map of maximum measure values, or null
	 */
	public SequentialPatternPanel(SequentialPattern pattern, Map<String, Double> minValues,
			Map<String, Double> maxValues) {
		this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
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
			measureTable = new MeasureTable(this, pattern.getMeasures(), minValues, maxValues);
		}
		return measureTable;
	}

	// ------------------------------------------------------------------
	// Layout
	// ------------------------------------------------------------------

	private SeqLayout layoutSequence(FontMetrics fm) {
		SeqLayout layout = new SeqLayout();
		for (List<String> itemset : pattern.getItemsets()) {
			Group g = new Group();
			g.block = ChipBlock.stack(fm, itemset);
			g.framed = itemset.size() > 1;
			int pad = g.framed ? 2 * GROUP_PAD : 0;
			g.w = g.block.getWidth() + pad;
			g.h = g.block.getHeight() + pad;
			layout.groups.add(g);
		}

		int arrowSpace = ARROW_MARGIN + ARROW_LENGTH + ARROW_MARGIN;
		Row row = new Row();
		for (int i = 0; i < layout.groups.size(); i++) {
			Group g = layout.groups.get(i);
			boolean first = row.groups.isEmpty();
			int needed = (first ? 0 : arrowSpace) + g.w;
			if (!first && row.width + needed > MAX_ROW_WIDTH) {
				layout.rows.add(row);
				row = new Row();
				// Continuation rows start with a short arrow
				row.width = ARROW_LENGTH + ARROW_MARGIN;
				first = true;
			}
			if (!first) {
				row.width += arrowSpace;
			}
			row.xs.add(row.width);
			row.groups.add(i);
			row.width += g.w;
			row.height = Math.max(row.height, g.h);
		}
		if (!row.groups.isEmpty()) {
			layout.rows.add(row);
		}

		for (int i = 0; i < layout.rows.size(); i++) {
			Row r = layout.rows.get(i);
			layout.width = Math.max(layout.width, r.width);
			layout.height += r.height + (i > 0 ? ROW_GAP : 0);
		}
		return layout;
	}

	@Override
	public Dimension getPreferredSize() {
		if (cachedPreferredSize == null) {
			FontMetrics fm = getFontMetrics(ViewerStyle.itemFont());
			SeqLayout layout = layoutSequence(fm);
			int contentW = Math.max(MIN_CONTENT_WIDTH, Math.max(layout.width, measures().getPreferredWidth()));
			int contentH = layout.height + measures().getSectionHeight();
			cachedPreferredSize = new Dimension(contentW + 2 * PAD + ViewerStyle.SHADOW_DX,
					contentH + 2 * PAD + ViewerStyle.SHADOW_DY);
		}
		return new Dimension(cachedPreferredSize);
	}

	@Override
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	// ------------------------------------------------------------------
	// Painting
	// ------------------------------------------------------------------

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
			SeqLayout layout = layoutSequence(fm);
			int bx = contentX + Math.max(0, (contentW - layout.width) / 2);
			int y = bodyTop + Math.max(0, (bodyBottom - bodyTop - layout.height) / 2);

			for (int ri = 0; ri < layout.rows.size(); ri++) {
				Row row = layout.rows.get(ri);
				int midY = y + row.height / 2;
				if (ri > 0) {
					ViewerStyle.paintArrow(g2, bx, midY, bx + ARROW_LENGTH, midY);
				}
				for (int j = 0; j < row.groups.size(); j++) {
					Group grp = layout.groups.get(row.groups.get(j));
					int gx = bx + row.xs.get(j);
					int gy = y + (row.height - grp.h) / 2;
					if (j > 0) {
						Group prev = layout.groups.get(row.groups.get(j - 1));
						int prevEnd = bx + row.xs.get(j - 1) + prev.w;
						ViewerStyle.paintArrow(g2, prevEnd + ARROW_MARGIN, midY,
								prevEnd + ARROW_MARGIN + ARROW_LENGTH, midY);
					}
					g2.setFont(ViewerStyle.itemFont());
					if (grp.framed) {
						g2.setColor(ViewerStyle.GROUP_FILL);
						g2.fillRoundRect(gx, gy, grp.w, grp.h, 10, 10);
						g2.setColor(ViewerStyle.GROUP_BORDER);
						g2.drawRoundRect(gx, gy, grp.w - 1, grp.h - 1, 10, 10);
						grp.block.paint(g2, fm, gx + GROUP_PAD, gy + GROUP_PAD, null);
					} else {
						grp.block.paint(g2, fm, gx, gy, null);
					}
				}
				y += row.height + ROW_GAP;
			}

			mt.paintAtBottom(g2, contentX, cardH - PAD, contentW);
		} finally {
			g2.dispose();
		}
	}

	// ------------------------------------------------------------------
	// Tooltip
	// ------------------------------------------------------------------

	private String buildToolTip() {
		List<List<String>> itemsets = pattern.getItemsets();
		int itemCount = 0;
		StringBuilder seq = new StringBuilder("<");
		for (int i = 0; i < itemsets.size(); i++) {
			if (i > 0) {
				seq.append(", ");
			}
			seq.append('{').append(String.join(", ", itemsets.get(i))).append('}');
			itemCount += itemsets.get(i).size();
		}
		seq.append('>');

		StringBuilder sb = new StringBuilder("<html><b>Sequential pattern</b> (");
		sb.append(itemsets.size()).append(itemsets.size() == 1 ? " itemset, " : " itemsets, ");
		sb.append(itemCount).append(itemCount == 1 ? " item)" : " items)");
		sb.append("<br>").append(ViewerStyle.escapeHtml(ViewerStyle.abbreviate(seq.toString(), 400)));
		if (!pattern.getMeasures().isEmpty()) {
			sb.append("<br><br>").append(MeasureTable.toHtmlRows(pattern.getMeasures()));
		}
		return sb.append("</html>").toString();
	}

	/**
	 * Get the pattern
	 *
	 * @return the pattern
	 */
	public SequentialPattern getPattern() {
		return pattern;
	}
}