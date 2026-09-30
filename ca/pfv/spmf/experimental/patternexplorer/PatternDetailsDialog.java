package ca.pfv.spmf.experimental.patternexplorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;

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
 * A non-modal dialog showing one pattern in detail: its items, its measures
 * (with bars relative to the range of each measure in the whole collection),
 * and, on demand, its relationships with the other patterns (subsets,
 * supersets, maximality, closedness). Buttons allow continuing the
 * exploration from this pattern.
 * 
 * @author Philippe Fournier-Viger
 */
public class PatternDetailsDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** Maximum number of related patterns listed */
	private static final int MAX_LISTED = 2000;

	private final ExplorerController controller;
	private final PatternCollection collection;
	private final int patternIndex;
	private final ExplorerPattern pattern;
	private final QueryResult currentResult;

	private final JLabel relationSummary;
	private final DefaultListModel<Integer> subsetsModel = new DefaultListModel<>();
	private final DefaultListModel<Integer> supersetsModel = new DefaultListModel<>();
	private final JLabel subsetsTitle;
	private final JLabel supersetsTitle;
	private final JButton computeButton;

	/**
	 * Constructor
	 * 
	 * @param owner         the owner window
	 * @param controller    the controller
	 * @param collection    the collection
	 * @param patternIndex  index of the pattern to show
	 * @param currentResult the current result (used to count relatives in the
	 *                      selection), may be null
	 */
	public PatternDetailsDialog(Window owner, ExplorerController controller, PatternCollection collection,
			int patternIndex, QueryResult currentResult) {
		super(owner, "Pattern details", ModalityType.MODELESS);
		this.controller = controller;
		this.collection = collection;
		this.patternIndex = patternIndex;
		this.pattern = collection.getPattern(patternIndex);
		this.currentResult = currentResult;

		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		setContentPane(content);

		// ---- Header: items ----
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		JLabel title = new JLabel(ExplorerFormat.count(pattern.size(), "item"));
		title.setFont(ExplorerStyle.FONT_TITLE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		header.add(title);
		JPanel itemsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 3));
		itemsPanel.setOpaque(false);
		itemsPanel.setAlignmentX(LEFT_ALIGNMENT);
		for (String item : pattern.getItems()) {
			JButton chip = new JButton(item);
			ExplorerStyle.makeCompact(chip);
			chip.setBackground(ExplorerStyle.REQUIRED_BG);
			chip.setForeground(ExplorerStyle.REQUIRED_FG);
			chip.setToolTipText("<html>Item <b>" + ExplorerHtml.escape(item) + "</b> appears in "
					+ ExplorerFormat.count(collection.getItemFrequency(collection.getItemId(item)), "pattern")
					+ " of the collection<br><i>click to require this item in the query</i></html>");
			chip.addActionListener(e -> controller.requireItems(List.of(item)));
			itemsPanel.add(chip);
		}
		header.add(itemsPanel);
		content.add(header, BorderLayout.NORTH);

		// ---- Center: measures + relationships ----
		JPanel center = new JPanel(new BorderLayout(0, 8));
		center.setOpaque(false);
		center.add(buildMeasuresPanel(), BorderLayout.NORTH);

		JPanel relations = new JPanel(new BorderLayout(0, 4));
		relations.setBorder(ExplorerStyle.titledBorder("Relationships with other patterns"));
		JPanel relTop = new JPanel(new BorderLayout(6, 0));
		relTop.setOpaque(false);
		relationSummary = new JLabel("<html><i>Subsets, supersets, maximality and closedness are computed on request.</i></html>");
		relationSummary.setFont(ExplorerStyle.FONT_NORMAL);
		relTop.add(relationSummary, BorderLayout.CENTER);
		computeButton = new JButton("Compute");
		ExplorerStyle.makeCompact(computeButton);
		computeButton.setToolTipText("Find all subsets and supersets of this pattern in the collection");
		computeButton.addActionListener(e -> computeRelations());
		relTop.add(computeButton, BorderLayout.EAST);
		relations.add(relTop, BorderLayout.NORTH);

		subsetsTitle = new JLabel("Subsets");
		subsetsTitle.setFont(ExplorerStyle.FONT_BOLD);
		supersetsTitle = new JLabel("Supersets");
		supersetsTitle.setFont(ExplorerStyle.FONT_BOLD);
		JSplitPane lists = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel(subsetsTitle, subsetsModel),
				listPanel(supersetsTitle, supersetsModel));
		lists.setResizeWeight(0.5);
		lists.setBorder(null);
		lists.setOpaque(false);
		relations.add(lists, BorderLayout.CENTER);
		center.add(relations, BorderLayout.CENTER);
		content.add(center, BorderLayout.CENTER);

		// ---- South: actions ----
		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		south.setOpaque(false);
		JButton supersetsButton = new JButton("Explore supersets");
		ExplorerStyle.makeCompact(supersetsButton);
		supersetsButton.setToolTipText("Require all items of this pattern in the query");
		supersetsButton.addActionListener(e -> {
			controller.requireItems(pattern.getItems());
			dispose();
		});
		JButton subsetsButton = new JButton("Explore subsets");
		ExplorerStyle.makeCompact(subsetsButton);
		subsetsButton.setToolTipText("Keep only the patterns made of items of this pattern");
		subsetsButton.addActionListener(e -> {
			controller.applyQuery(ExplorerQuery.UNRESTRICTED.restrictWithin(pattern.getItems()));
			dispose();
		});
		JButton copyButton = new JButton("Copy");
		ExplorerStyle.makeCompact(copyButton);
		copyButton.setToolTipText("Copy this pattern (SPMF format) to the clipboard");
		copyButton.addActionListener(e -> {
			StringBuilder sb = new StringBuilder(pattern.toSpaceSeparatedString());
			for (Map.Entry<String, String> en : pattern.getAttributes().entrySet()) {
				sb.append(" #").append(en.getKey()).append(": ").append(en.getValue());
			}
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
		});
		JButton closeButton = new JButton("Close");
		ExplorerStyle.makeCompact(closeButton);
		closeButton.addActionListener(e -> dispose());
		south.add(supersetsButton);
		south.add(subsetsButton);
		south.add(copyButton);
		south.add(closeButton);
		content.add(south, BorderLayout.SOUTH);

		getRootPane().setDefaultButton(closeButton);
		setSize(new Dimension(640, 560));
		setMinimumSize(new Dimension(420, 380));
		setLocationRelativeTo(owner);
	}

	// ------------------------------------------------------------------
	// Measures
	// ------------------------------------------------------------------

	private JComponent buildMeasuresPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(ExplorerStyle.titledBorder("Measures"));
		Map<String, String> attributes = pattern.getAttributes();
		if (attributes.isEmpty()) {
			panel.add(ExplorerStyle.mutedLabel("This pattern has no measure"));
			return panel;
		}
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(2, 4, 2, 4);
		gc.anchor = GridBagConstraints.WEST;
		int row = 0;
		for (Map.Entry<String, String> e : attributes.entrySet()) {
			String name = e.getKey();
			String value = e.getValue();
			int idx = collection.getNumericMeasureIndex(name);

			gc.gridx = 0;
			gc.gridy = row;
			gc.weightx = 0;
			gc.fill = GridBagConstraints.NONE;
			JLabel nameLabel = new JLabel(name + ":");
			nameLabel.setFont(ExplorerStyle.FONT_BOLD);
			panel.add(nameLabel, gc);

			gc.gridx = 1;
			JLabel valueLabel = new JLabel(idx >= 0 ? ExplorerFormat.number(collection.getNumericValue(idx, patternIndex))
					: value);
			valueLabel.setFont(ExplorerStyle.FONT_NORMAL);
			panel.add(valueLabel, gc);

			gc.gridx = 2;
			gc.weightx = 1;
			gc.fill = GridBagConstraints.HORIZONTAL;
			if (idx >= 0) {
				double v = collection.getNumericValue(idx, patternIndex);
				double min = collection.getGlobalMin(idx);
				double max = collection.getGlobalMax(idx);
				MeasureBar bar = new MeasureBar(v, min, max, ExplorerStyle.measureColor(idx));
				bar.setToolTipText("Range of " + name + " in the whole collection: " + ExplorerFormat.number(min)
						+ " \u2013 " + ExplorerFormat.number(max));
				panel.add(bar, gc);
			} else {
				panel.add(ExplorerStyle.mutedLabel("(text)"), gc);
			}
			row++;
		}
		return panel;
	}

	/** A small horizontal bar showing a value within a range */
	private static final class MeasureBar extends JComponent {
		private static final long serialVersionUID = 1L;
		private final double fraction;
		private final Color color;

		MeasureBar(double value, double min, double max, Color color) {
			this.color = color;
			this.fraction = (Double.isNaN(value) || Double.isNaN(min) || Double.isNaN(max) || max <= min) ? 1.0
					: Math.max(0, Math.min(1, (value - min) / (max - min)));
			setPreferredSize(new Dimension(180, 14));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth() - 1;
			int h = getHeight() - 1;
			g2.setColor(new Color(240, 240, 240));
			g2.fillRoundRect(0, 0, w, h, 6, 6);
			g2.setColor(color);
			g2.fillRoundRect(0, 0, (int) Math.round(fraction * w), h, 6, 6);
			g2.setColor(ExplorerStyle.BORDER);
			g2.drawRoundRect(0, 0, w, h, 6, 6);
			g2.dispose();
		}
	}

	// ------------------------------------------------------------------
	// Relationships
	// ------------------------------------------------------------------

	private JPanel listPanel(JLabel title, DefaultListModel<Integer> model) {
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setOpaque(false);
		panel.add(title, BorderLayout.NORTH);
		JList<Integer> list = new JList<>(model);
		list.setFont(ExplorerStyle.FONT_NORMAL);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
					boolean focus) {
				super.getListCellRendererComponent(l, value, index, selected, focus);
				if (value instanceof Integer) {
					int idx = (Integer) value;
					ExplorerPattern p = collection.getPattern(idx);
					StringBuilder sb = new StringBuilder(p.toString());
					List<String> numeric = collection.getNumericMeasureNames();
					if (!numeric.isEmpty()) {
						sb.append("   ");
						for (int k = 0; k < numeric.size() && k < 3; k++) {
							if (k > 0) {
								sb.append(", ");
							}
							sb.append(numeric.get(k)).append('=')
									.append(ExplorerFormat.number(collection.getNumericValue(k, idx)));
						}
					}
					setText(sb.toString());
					boolean inSelection = currentResult != null && containsIndex(currentResult.getMatchIndices(), idx);
					if (!selected) {
						setForeground(inSelection ? Color.BLACK : ExplorerStyle.MUTED);
					}
					setToolTipText(inSelection ? "In the current selection (double-click for details)"
							: "Not in the current selection (double-click for details)");
				}
				return this;
			}
		});
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					int i = list.locationToIndex(e.getPoint());
					if (i >= 0) {
						controller.showPatternDetails(model.get(i));
					}
				}
			}
		});
		panel.add(new JScrollPane(list), BorderLayout.CENTER);
		return panel;
	}

	private static boolean containsIndex(int[] sorted, int value) {
		return java.util.Arrays.binarySearch(sorted, value) >= 0;
	}

	private void computeRelations() {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		computeButton.setEnabled(false);
		try {
			int[] supersets = collection.findSupersets(patternIndex, null);
			int[] subsets = collection.findSubsets(patternIndex, null);
			BitSet selection = currentResult == null ? null
					: PatternCollection.toBitSet(currentResult.getMatchIndices(), collection.size());
			int supersetsInSelection = countIn(supersets, selection);
			int subsetsInSelection = countIn(subsets, selection);

			StringBuilder sb = new StringBuilder("<html>");
			sb.append("<b>").append(ExplorerFormat.count(subsets.length, "subset")).append("</b>");
			if (selection != null) {
				sb.append(" (").append(ExplorerFormat.integer(subsetsInSelection)).append(" in the selection)");
			}
			sb.append(" &nbsp;&middot;&nbsp; <b>").append(ExplorerFormat.count(supersets.length, "superset"))
					.append("</b>");
			if (selection != null) {
				sb.append(" (").append(ExplorerFormat.integer(supersetsInSelection)).append(" in the selection)");
			}
			sb.append("<br>Maximal in the collection: <b>").append(supersets.length == 0 ? "yes" : "no")
					.append("</b>");
			List<String> numeric = collection.getNumericMeasureNames();
			if (!numeric.isEmpty() && supersets.length > 0) {
				sb.append(" &nbsp;&middot;&nbsp; Closed by: ");
				boolean first = true;
				for (int k = 0; k < numeric.size(); k++) {
					if (!first) {
						sb.append(", ");
					}
					first = false;
					boolean closed = collection.isClosed(patternIndex, k, null);
					sb.append(ExplorerHtml.escape(numeric.get(k))).append(' ').append(closed ? "<b>yes</b>" : "no");
				}
			} else if (!numeric.isEmpty()) {
				sb.append(" &nbsp;&middot;&nbsp; Closed: <b>yes</b> (no superset)");
			}
			sb.append("</html>");
			relationSummary.setText(sb.toString());

			fill(subsetsModel, subsets);
			fill(supersetsModel, supersets);
			subsetsTitle.setText("Subsets (" + ExplorerFormat.integer(subsets.length)
					+ (subsets.length > MAX_LISTED ? ", first " + MAX_LISTED + " listed" : "") + ")");
			supersetsTitle.setText("Supersets (" + ExplorerFormat.integer(supersets.length)
					+ (supersets.length > MAX_LISTED ? ", first " + MAX_LISTED + " listed" : "") + ")");
		} finally {
			setCursor(Cursor.getDefaultCursor());
		}
	}

	private static int countIn(int[] indices, BitSet selection) {
		if (selection == null) {
			return 0;
		}
		int count = 0;
		for (int i : indices) {
			if (selection.get(i)) {
				count++;
			}
		}
		return count;
	}

	private static void fill(DefaultListModel<Integer> model, int[] indices) {
		model.clear();
		int n = Math.min(indices.length, MAX_LISTED);
		for (int i = 0; i < n; i++) {
			model.addElement(indices[i]);
		}
	}
}