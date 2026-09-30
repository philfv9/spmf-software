package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

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
 * A list of the items that appear in the currently shown rules, sorted by how
 * often they appear. Clicking an item toggles it in the rule search box
 * (items currently in the search are shown in bold blue). The number next to
 * an item is how many of the shown rules contain it.
 * 
 * @author Philippe Fournier-Viger
 */
public class ItemFilterPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	/** maximum number of items shown in the list */
	private static final int MAX_SHOWN = 200;

	/** one row of the list: an item and the number of shown rules containing it */
	private static final class ItemEntry {
		final String item;
		final int count;

		ItemEntry(String item, int count) {
			this.item = item;
			this.count = count;
		}

		@Override
		public String toString() {
			return item;
		}
	}

	private final DefaultListModel<ItemEntry> model = new DefaultListModel<>();
	private final JList<ItemEntry> list = new JList<>(model);
	private final JLabel summary = new JLabel(" ");
	private Set<String> activeTerms = Collections.emptySet();
	private final Consumer<String> clickConsumer;

	public ItemFilterPanel(Consumer<String> clickConsumer) {
		super(new BorderLayout(0, 2));
		this.clickConsumer = clickConsumer;

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(8);
		list.setFixedCellHeight(18);
		list.setCellRenderer(new ItemRenderer());
		list.setToolTipText("Click an item to add it to (or remove it from) the search box.\n"
				+ "The number is how many of the shown rules contain this item.");
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int index = list.locationToIndex(e.getPoint());
				if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint())) {
					return;
				}
				ItemEntry entry = model.get(index);
				if (entry != null && clickConsumer != null) {
					list.clearSelection();
					clickConsumer.accept(entry.item);
				}
			}
		});

		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setPreferredSize(new Dimension(10, 150));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

		summary.setFont(summary.getFont().deriveFont(Font.PLAIN, 10.5f));
		summary.setForeground(Color.GRAY);
		summary.setAlignmentX(Component.LEFT_ALIGNMENT);
		summary.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

		add(scroll, BorderLayout.CENTER);
		add(summary, BorderLayout.SOUTH);
	}

	/**
	 * Replace the list contents.
	 * 
	 * @param items       entries "item name -> number of shown rules containing
	 *                    it", already sorted by count (descending)
	 * @param totalItems  total number of distinct items in the shown rules
	 * @param activeTerms items currently in the search box (drawn bold)
	 */
	public void setItems(List<Map.Entry<String, Integer>> items, int totalItems, Set<String> activeTerms) {
		Set<String> normalized = new java.util.HashSet<>();
		for (String t : activeTerms) {
			normalized.add(t.toLowerCase());
		}
		this.activeTerms = normalized;
		model.clear();
		int shown = Math.min(items.size(), MAX_SHOWN);
		for (int i = 0; i < shown; i++) {
			model.addElement(new ItemEntry(items.get(i).getKey(), items.get(i).getValue()));
		}
		if (totalItems > shown) {
			summary.setText(totalItems + " distinct items - showing the " + shown + " most frequent");
		} else {
			summary.setText(totalItems + " distinct items in the shown rules");
		}
		list.repaint();
	}

	/** @return the top entries for a rule list (count descending, then name) */
	public static List<Map.Entry<String, Integer>> countItems(List<ARule> rules) {
		Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		for (ARule r : rules) {
			Set<String> seen = new java.util.HashSet<>();
			for (String item : r.getAllItems()) {
				if (seen.add(item)) {
					counts.merge(item, 1, Integer::sum);
				}
			}
		}
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
		entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
				.thenComparing(Map.Entry.comparingByKey()));
		return entries;
	}

	/** Renderer: "item  3" with a gray count; bold blue when in the search. */
	private class ItemRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<?> jlist, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			ItemEntry entry = (ItemEntry) value;
			super.getListCellRendererComponent(jlist, entry, index, isSelected, cellHasFocus);
			String label = entry.item.length() > 42 ? entry.item.substring(0, 39) + "..." : entry.item;
			setText(label + "   " + entry.count);
			if (activeTerms.contains(entry.item.toLowerCase())) {
				setFont(getFont().deriveFont(Font.BOLD));
				setForeground(new Color(30, 90, 150));
			} else {
				setFont(getFont().deriveFont(Font.PLAIN));
				if (!isSelected) {
					setForeground(Color.BLACK);
				}
			}
			setToolTipText(entry.item + " - appears in " + entry.count
					+ " of the shown rule(s). Click to add/remove it in the search.");
			return this;
		}
	}
}
