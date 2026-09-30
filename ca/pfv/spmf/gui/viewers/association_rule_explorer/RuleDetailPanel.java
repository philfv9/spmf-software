package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

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
 * Shows the selected rule(s): the rule, a plain-language explanation, a bar for
 * each measure, and a button to copy the rule(s) to the clipboard.
 * 
 * @author Philippe Fournier-Viger
 */
public class RuleDetailPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final String HINT = "Click a point in the plot or a row in the table to see the details of a rule.";

	private final JLabel countLabel = new JLabel(" ");
	private final JButton copyButton = new JButton("Copy rule");
	private final JTextArea ruleText = new JTextArea(2, 20);
	private final JTextArea explanation = new JTextArea(4, 20);
	private final BarsPanel bars = new BarsPanel();

	private List<ARule> rules = new ArrayList<>();
	private List<String> measureNames = new ArrayList<>();
	private Map<String, double[]> ranges = Collections.emptyMap();
	private long transactionCount = -1;

	public RuleDetailPanel() {
		super(new BorderLayout(4, 4));
		setBorder(BorderFactory.createTitledBorder("Selected rule"));

		copyButton.setToolTipText("Copy the selected rule(s) with their measures to the clipboard");
		copyButton.setEnabled(false);
		copyButton.addActionListener(e -> copyToClipboard());
		JPanel top = new JPanel(new BorderLayout());
		top.add(countLabel, BorderLayout.CENTER);
		top.add(copyButton, BorderLayout.EAST);

		for (JTextArea t : new JTextArea[] { ruleText, explanation }) {
			t.setEditable(false);
			t.setLineWrap(true);
			t.setWrapStyleWord(true);
			t.setOpaque(false);
			t.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		}
		ruleText.setFont(ruleText.getFont().deriveFont(Font.BOLD, 13f));
		explanation.setForeground(new Color(50, 50, 50));
		explanation.setText(HINT);

		JPanel texts = new JPanel();
		texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
		texts.add(ruleText);
		texts.add(explanation);

		JPanel body = new JPanel(new BorderLayout());
		body.add(texts, BorderLayout.NORTH);
		body.add(bars, BorderLayout.CENTER);
		JScrollPane scroll = new JScrollPane(body);
		scroll.setBorder(null);

		add(top, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
	}

	public void setMeasureInfo(List<String> measureNames, Map<String, double[]> ranges) {
		this.measureNames = new ArrayList<>(measureNames);
		this.ranges = ranges;
		bars.revalidate();
		bars.repaint();
	}

	/** @param count number of transactions in the database, or -1 if unknown */
	public void setTransactionCount(long count) {
		this.transactionCount = count;
		setRules(rules);
	}

	/** Show the given rules (the first one is explained in detail). */
	public void setRules(List<ARule> selected) {
		this.rules = selected == null ? new ArrayList<>() : new ArrayList<>(selected);
		copyButton.setEnabled(!rules.isEmpty());
		copyButton.setText(rules.size() > 1 ? "Copy " + rules.size() + " rules" : "Copy rule");
		if (rules.isEmpty()) {
			countLabel.setText(" ");
			ruleText.setText("");
			explanation.setText(HINT);
		} else {
			ARule first = rules.get(0);
			countLabel.setText(rules.size() == 1 ? "1 rule selected"
					: rules.size() + " rules selected - details of the first one:");
			ruleText.setText("IF " + first.getAntecedentText() + "  THEN " + first.getConsequentText());
			explanation.setText(explain(first, transactionCount));
		}
		bars.repaint();
	}

	private void copyToClipboard() {
		StringBuilder sb = new StringBuilder();
		for (ARule r : rules) {
			sb.append(toReadableText(r)).append(System.lineSeparator());
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
	}

	/**
	 * One readable line for a rule: "IF {..} THEN {..} Support: 3 Confidence: 0.75
	 * ..."
	 */
	public static String toReadableText(ARule r) {
		StringBuilder sb = new StringBuilder("IF ").append(r.getAntecedentText()).append(" THEN ")
				.append(r.getConsequentText());
		for (Map.Entry<String, Double> m : r.getMeasures().entrySet()) {
			sb.append("\t").append(MeasureUtils.prettyName(m.getKey())).append(": ")
					.append(MeasureUtils.format(m.getValue()));
		}
		return sb.toString();
	}

	/** Build a plain-language explanation of a rule. */
	static String explain(ARule r, long transactionCount) {
		String a = "{" + String.join(", ", r.getAntecedent()) + "}";
		String c = "{" + String.join(", ", r.getConsequent()) + "}";
		String aPresent = r.isAntecedentNegated() ? " is absent" : " appears";
		double sup = r.getMeasureIgnoreCase("SUP");
		if (Double.isNaN(sup)) {
			sup = r.getMeasureIgnoreCase("SUPPORT");
		}
		double conf = r.getMeasureIgnoreCase("CONF");
		if (Double.isNaN(conf)) {
			conf = r.getMeasureIgnoreCase("CONFIDENCE");
		}
		double lift = r.getMeasureIgnoreCase("LIFT");

		StringBuilder sb = new StringBuilder();
		sb.append("When ").append(a).append(aPresent).append(", ").append(c)
				.append(r.isConsequentNegated() ? " is also absent" : " also appears");
		if (!Double.isNaN(conf)) {
			sb.append(" ").append(MeasureUtils.percent(conf)).append(" of the time");
		}
		if (!Double.isNaN(sup)) {
			sb.append(" (").append(MeasureUtils.format(sup)).append(" transactions");
			if (transactionCount > 0) {
				sb.append(" = ").append(MeasureUtils.percent(sup / transactionCount)).append(" of the database");
			}
			sb.append(")");
		}
		sb.append(".");
		if (!Double.isNaN(lift)) {
			sb.append("\n");
			if (Math.abs(lift - 1) < 0.005) {
				sb.append("Lift \u2248 1: ").append(c).append(" is about as likely with or without ").append(a)
						.append(" \u2192 no correlation (independent).");
			} else if (lift > 1) {
				sb.append(c).append(" is ").append(MeasureUtils.format(lift)).append("\u00d7 more likely when ")
						.append(a).append(aPresent).append(" \u2192 positive correlation.");
			} else {
				sb.append(c).append(" is only ").append(MeasureUtils.format(lift)).append("\u00d7 as likely when ")
						.append(a).append(aPresent).append(" \u2192 negative correlation.");
			}
		}
		return sb.toString();
	}

	/** Inner panel that paints one bar per measure for the first selected rule. */
	private class BarsPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private static final int ROW_HEIGHT = 22;
		private static final int LABEL_WIDTH = 100;
		private static final int VALUE_WIDTH = 80;

		BarsPanel() {
			setOpaque(false);
			setToolTipText("Bar length = position of the value between the minimum and maximum of all rules");
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(320, 10 + ROW_HEIGHT * Math.max(1, measureNames.size()));
		}

		@Override
		protected void paintComponent(Graphics g0) {
			super.paintComponent(g0);
			if (rules.isEmpty()) {
				return;
			}
			ARule rule = rules.get(0);
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			FontMetrics fm = g.getFontMetrics();
			int barX = 10 + LABEL_WIDTH;
			int barW = Math.max(20, getWidth() - barX - VALUE_WIDTH - 10);
			int y = 5;
			for (String m : measureNames) {
				double v = rule.getMeasure(m);
				double[] r = ranges.get(m);
				double t = MeasureUtils.fraction(v, r);
				int textY = y + ROW_HEIGHT / 2 + fm.getAscent() / 2 - 2;
				g.setColor(Color.DARK_GRAY);
				g.drawString(MeasureUtils.prettyName(m), 10, textY);
				g.setColor(new Color(235, 235, 235));
				g.fillRect(barX, y + 4, barW, ROW_HEIGHT - 8);
				if (r != null && !Double.isNaN(v)) {
					g.setColor(MeasureUtils.colorFor(v, m, r));
					g.fillRect(barX, y + 4, (int) Math.round(barW * t), ROW_HEIGHT - 8);
					// mark the "independence" value for lift-like measures
					double c = MeasureUtils.centerValue(m);
					if (!Double.isNaN(c) && c >= r[0] && c <= r[1]) {
						int cx = barX + (int) Math.round(barW * MeasureUtils.fraction(c, r));
						g.setColor(Color.BLACK);
						g.drawLine(cx, y + 2, cx, y + ROW_HEIGHT - 2);
					}
				}
				g.setColor(Color.GRAY);
				g.drawRect(barX, y + 4, barW, ROW_HEIGHT - 8);
				g.setColor(Color.BLACK);
				g.drawString(MeasureUtils.format(v), barX + barW + 6, textY);
				y += ROW_HEIGHT;
			}
			g.dispose();
		}
	}
}
