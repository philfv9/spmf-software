package ca.pfv.spmf.gui.algorithmgraphvisualizer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger. Part of SPMF (GPL v3).
 */
/**
 * Non-visual model: loads the algorithms of SPMF, keeps them grouped by
 * category (in SPMF order), assigns category colours, and applies filters.
 *
 * The lists returned by {@link #getNodes()} and {@link #getCategories()} are
 * live, unmodifiable views: they stay valid across {@link #reload()}.
 */
public class AlgorithmGraphModel {

	/** Immutable filter criteria. */
	public static final class Filter {
		public final String query;
		/** null = all categories */
		public final String category;
		public final boolean hideTools;

		public Filter(String query, String category, boolean hideTools) {
			this.query = query == null ? "" : query.trim();
			this.category = category;
			this.hideTools = hideTools;
		}

		String[] tokens() {
			if (query.isEmpty()) {
				return null;
			}
			return query.toLowerCase(Locale.ROOT).split("\\s+");
		}
	}

	private final AlgorithmManager manager;
	private final List<AlgorithmNode> nodes = new ArrayList<>();
	private final List<AlgorithmNode> nodesView = Collections.unmodifiableList(nodes);
	private final Map<String, List<AlgorithmNode>> categories = new LinkedHashMap<>();
	private final Map<String, List<AlgorithmNode>> categoriesView = Collections.unmodifiableMap(categories);
	private final Map<String, Color> categoryColors = new LinkedHashMap<>();
	private final Map<String, AlgorithmNode> byName = new HashMap<>();
	private Filter lastFilter = new Filter("", null, true);

	/**
	 * @throws IllegalStateException if the SPMF AlgorithmManager is unavailable
	 */
	public AlgorithmGraphModel() {
		try {
			manager = AlgorithmManager.getInstance();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize the SPMF AlgorithmManager", e);
		}
		reload();
	}

	/** Reload everything from SPMF and re-apply the last filter. */
	public void reload() {
		nodes.clear();
		categories.clear();
		categoryColors.clear();
		byName.clear();

		List<String> list = manager.getListOfAlgorithmsAsString(true, true, true, true, true);
		String current = AlgorithmNode.UNCATEGORIZED;
		for (String entry : list) {
			if (entry == null) {
				continue;
			}
			DescriptionOfAlgorithm desc = manager.getDescriptionOfAlgorithm(entry);
			if (desc == null) { // category header
				current = entry.trim().isEmpty() ? AlgorithmNode.UNCATEGORIZED : entry.trim();
				categories.putIfAbsent(current, new ArrayList<>());
			} else {
				AlgorithmNode node = new AlgorithmNode(entry, desc, current, colorFor(current));
				nodes.add(node);
				categories.computeIfAbsent(current, k -> new ArrayList<>()).add(node);
				byName.put(node.getName().toLowerCase(Locale.ROOT), node);
			}
		}
		// remove headers without algorithms
		for (Iterator<Map.Entry<String, List<AlgorithmNode>>> it = categories.entrySet().iterator(); it.hasNext();) {
			if (it.next().getValue().isEmpty()) {
				it.remove();
			}
		}
		applyFilter(lastFilter);
	}

	private Color colorFor(String category) {
		return categoryColors.computeIfAbsent(category, k -> AlgorithmNode.paletteColor(categoryColors.size()));
	}

	// =====================================================================
	// Queries
	// =====================================================================

	public List<AlgorithmNode> getNodes() { return nodesView; }
	public Map<String, List<AlgorithmNode>> getCategories() { return categoriesView; }
	public Set<String> getCategoryNames() { return categoriesView.keySet(); }
	public int getAlgorithmCount() { return nodes.size(); }
	public int getCategoryCount() { return categories.size(); }
	public Filter getLastFilter() { return lastFilter; }

	public Color getCategoryColor(String category) {
		return categoryColors.get(category);
	}

	public int getCategorySize(String category) {
		List<AlgorithmNode> l = categories.get(category);
		return l == null ? 0 : l.size();
	}

	/** Case-insensitive lookup by algorithm name; null if unknown. */
	public AlgorithmNode findByName(String name) {
		return name == null ? null : byName.get(name.trim().toLowerCase(Locale.ROOT));
	}

	public int getVisibleCount() {
		int n = 0;
		for (AlgorithmNode node : nodes) {
			if (node.isVisible()) {
				n++;
			}
		}
		return n;
	}

	public List<AlgorithmNode> getVisibleNodes() {
		List<AlgorithmNode> out = new ArrayList<>();
		for (AlgorithmNode node : nodes) {
			if (node.isVisible()) {
				out.add(node);
			}
		}
		return out;
	}

	/**
	 * Update the visibility flag of every node.
	 *
	 * @return number of visible nodes
	 */
	public int applyFilter(Filter filter) {
		lastFilter = filter == null ? new Filter("", null, true) : filter;
		String[] tokens = lastFilter.tokens();
		int visible = 0;
		for (AlgorithmNode n : nodes) {
			boolean v = (lastFilter.category == null || n.getCategory().equals(lastFilter.category))
					&& !(lastFilter.hideTools && n.isTool()) && n.matches(tokens);
			n.setVisible(v);
			if (v) {
				visible++;
			}
		}
		return visible;
	}

	public void clear() {
		nodes.clear();
		categories.clear();
		categoryColors.clear();
		byName.clear();
	}
}