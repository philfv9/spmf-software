package ca.pfv.spmf.gui.viewers.graph_transition_viewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.SequentialPattern;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.SequentialPatternsReader;

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
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Core data model for the Sequential Pattern Transition Network. Built once
 * from all patterns via the constructor. Filtering is performed via query
 * methods without modifying the graph structure.
 * <p>
 * <b>Node metrics:</b> pattern count (for sizing), max support.
 * <b>Edge metrics:</b> pattern count (for sizing), max support.
 * <p>
 * Maintains adjacency indices for O(degree) edge lookups.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class TransitionGraph {

	/**
	 * Weighting measure types for edges.
	 */
	public enum WeightMeasure {
		/** Weight by pattern count */
		SUPPORT,
		/** Weight by confidence */
		CONFIDENCE,
		/** Weight by lift */
		LIFT
	}

	/**
	 * Type of transition edge.
	 */
	public enum EdgeType {
		/** Sequential transition between itemsets */
		SEQUENTIAL,
		/** Co-occurrence within same itemset */
		CO_OCCURRENCE
	}

	/**
	 * A node in the transition graph representing an item.
	 */
	public static class GraphNode {
		/** Item label */
		private final String itemLabel;
		/** Number of patterns containing this item */
		private int patternCount;
		/** Highest support among patterns containing this item */
		private int maxSupport;
		/** X coordinate for layout */
		private double x;
		/** Y coordinate for layout */
		private double y;
		/** Whether position has been initialized */
		private boolean positionInitialized;

		/**
		 * Constructor.
		 * @param itemLabel the item label
		 */
		public GraphNode(String itemLabel) {
			this.itemLabel = itemLabel;
			this.patternCount = 0;
			this.maxSupport = 0;
			this.x = 0;
			this.y = 0;
			this.positionInitialized = false;
		}

		/** @return the item label */
		public String getItemLabel() { return itemLabel; }

		/** @return number of patterns containing this item */
		public int getPatternCount() { return patternCount; }

		/** @return highest support among patterns containing this item */
		public int getMaxSupport() { return maxSupport; }

		/**
		 * Updates node statistics for a pattern occurrence.
		 * @param support the support of the pattern
		 */
		public void addPatternOccurrence(int support) {
			patternCount++;
			if (support > maxSupport) maxSupport = support;
		}

		/** @return x coordinate */
		public double getX() { return x; }
		/** @return y coordinate */
		public double getY() { return y; }
		/** @param x x coordinate */
		public void setX(double x) { this.x = x; }
		/** @param y y coordinate */
		public void setY(double y) { this.y = y; }
		/** @return true if position initialized */
		public boolean isPositionInitialized() { return positionInitialized; }
		/** @param v position initialized flag */
		public void setPositionInitialized(boolean v) { this.positionInitialized = v; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof GraphNode)) return false;
			return itemLabel.equals(((GraphNode) o).itemLabel);
		}

		@Override
		public int hashCode() { return itemLabel.hashCode(); }

		@Override
		public String toString() {
			return itemLabel + " (patterns=" + patternCount
					+ ", maxSup=" + maxSupport + ")";
		}
	}

	/**
	 * A directed edge in the transition graph.
	 */
	public static class GraphEdge {
		/** Source item label */
		private final String sourceItem;
		/** Target item label */
		private final String targetItem;
		/** Edge type */
		private final EdgeType edgeType;
		/** Unique key */
		private final String key;
		/** Number of patterns containing this edge */
		private int patternCount;
		/** Highest support among patterns containing this edge */
		private int maxSupport;
		/** Computed weight */
		private double weight;

		/**
		 * Constructor.
		 * @param sourceItem source item label
		 * @param targetItem target item label
		 * @param edgeType   edge type
		 */
		public GraphEdge(String sourceItem, String targetItem, EdgeType edgeType) {
			this.sourceItem = sourceItem;
			this.targetItem = targetItem;
			this.edgeType = edgeType;
			this.key = sourceItem + "->" + targetItem + ":" + edgeType;
			this.patternCount = 0;
			this.maxSupport = 0;
			this.weight = 0.0;
		}

		/** @return source item label */
		public String getSourceItem() { return sourceItem; }
		/** @return target item label */
		public String getTargetItem() { return targetItem; }
		/** @return edge type */
		public EdgeType getEdgeType() { return edgeType; }
		/** @return number of patterns containing this edge */
		public int getPatternCount() { return patternCount; }
		/** @return highest support among patterns containing this edge */
		public int getMaxSupport() { return maxSupport; }

		/**
		 * Updates edge statistics for a pattern occurrence.
		 * @param support the support of the pattern
		 */
		public void addPatternOccurrence(int support) {
			patternCount++;
			if (support > maxSupport) maxSupport = support;
		}

		/** @return computed weight */
		public double getWeight() { return weight; }
		/** @param weight the weight to set */
		public void setWeight(double weight) { this.weight = weight; }
		/** @return unique key */
		public String getKey() { return key; }

		/** @return human-readable edge type label */
		public String getEdgeTypeLabel() {
			return edgeType == EdgeType.CO_OCCURRENCE ? "co-occurrence" : "sequential";
		}

		/**
		 * Returns display string with appropriate arrow symbol.
		 * @return formatted display label
		 */
		public String getDisplayLabel() {
			String arrow = (edgeType == EdgeType.CO_OCCURRENCE) ? " \u2194 " : " \u2192 ";
			return sourceItem + arrow + targetItem;
		}

		@Override
		public String toString() {
			return sourceItem + (edgeType == EdgeType.CO_OCCURRENCE ? " <-> " : " -> ")
					+ targetItem + " [" + edgeType + "] (patterns=" + patternCount
					+ ", maxSup=" + maxSupport
					+ ", weight=" + String.format("%.4f", weight) + ")";
		}
	}

	/**
	 * A stored sequential pattern with pre-computed properties.
	 */
	public static class StoredPattern {
		/** Itemsets in the pattern */
		private final List<List<String>> itemsets;
		/** Support value */
		private final int support;
		/** Measure name-value pairs */
		private final Map<String, String> measures;
		/** Pre-built sequence string */
		private final String sequenceString;
		/** Pre-built full display string */
		private final String fullDisplayString;
		/** Set of items in this pattern */
		private final Set<String> containedItems;
		/** Set of edge keys in this pattern */
		private final Set<String> edgeKeys;
		/** Total item count */
		private final int totalItemCount;
		/** Whether pattern has multi-item itemset */
		private final boolean hasMultiItemItemset;

		/**
		 * Constructor.
		 * @param itemsets the itemsets
		 * @param support  the support value
		 * @param measures the measures map
		 */
		public StoredPattern(List<List<String>> itemsets, int support, Map<String, String> measures) {
			this.itemsets = itemsets;
			this.support = support;
			this.measures = measures != null ? new LinkedHashMap<>(measures) : new LinkedHashMap<>();
			this.containedItems = new HashSet<>();
			this.edgeKeys = new HashSet<>();

			int count = 0;
			boolean multiItem = false;
			StringBuilder seqSb = new StringBuilder();
			for (int i = 0; i < itemsets.size(); i++) {
				List<String> itemset = itemsets.get(i);
				if (itemset.size() > 1) {
					multiItem = true;
					seqSb.append("{");
				}
				for (int j = 0; j < itemset.size(); j++) {
					String item = itemset.get(j);
					containedItems.add(item);
					count++;
					seqSb.append(item);
					if (j < itemset.size() - 1) seqSb.append(", ");
				}
				if (itemset.size() > 1) seqSb.append("}");
				if (i < itemsets.size() - 1) seqSb.append(" \u2192 ");
			}
			this.totalItemCount = count;
			this.hasMultiItemItemset = multiItem;
			this.sequenceString = seqSb.toString();

			StringBuilder fullSb = new StringBuilder(sequenceString);
			for (Map.Entry<String, String> entry : this.measures.entrySet()) {
				fullSb.append("  #").append(entry.getKey()).append(": ").append(entry.getValue());
			}
			this.fullDisplayString = fullSb.toString();

			// Pre-compute edge keys
			for (List<String> itemset : itemsets) {
				if (itemset.size() > 1) {
					for (int i = 0; i < itemset.size(); i++) {
						for (int j = i + 1; j < itemset.size(); j++) {
							String a = itemset.get(i), b = itemset.get(j);
							if (a.compareTo(b) < 0)
								edgeKeys.add(a + "->" + b + ":" + EdgeType.CO_OCCURRENCE);
							else
								edgeKeys.add(b + "->" + a + ":" + EdgeType.CO_OCCURRENCE);
						}
					}
				}
			}
			for (int i = 0; i < itemsets.size() - 1; i++) {
				for (String src : itemsets.get(i)) {
					for (String tgt : itemsets.get(i + 1)) {
						edgeKeys.add(src + "->" + tgt + ":" + EdgeType.SEQUENTIAL);
					}
				}
			}
		}

		/** @return the itemsets */
		public List<List<String>> getItemsets() { return itemsets; }
		/** @return the support value */
		public int getSupport() { return support; }
		/** @return the measures map */
		public Map<String, String> getMeasures() { return measures; }
		/** @return formatted sequence string */
		public String getSequenceString() { return sequenceString; }
		/** @return full display string with measures */
		public String getDisplayString() { return fullDisplayString; }
		/** @return set of contained items */
		public Set<String> getContainedItems() { return containedItems; }
		/** @return total item count */
		public int getTotalItemCount() { return totalItemCount; }
		/** @return number of itemsets */
		public int getItemsetCount() { return itemsets.size(); }
		/** @return true if pattern has multi-item itemset */
		public boolean hasMultiItemItemset() { return hasMultiItemItemset; }
		/**
		 * Checks if pattern contains an item.
		 * @param item the item to check
		 * @return true if contained
		 */
		public boolean containsItem(String item) { return containedItems.contains(item); }
		/** @return set of edge keys */
		public Set<String> getEdgeKeys() { return edgeKeys; }

		@Override
		public String toString() { return fullDisplayString; }
	}

	// ---- Fields ----
	/** Map of item label to node */
	private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
	/** Map of edge key to edge */
	private final Map<String, GraphEdge> edges = new LinkedHashMap<>();
	/** All stored patterns */
	private final List<StoredPattern> storedPatterns = new ArrayList<>();

	/** Outgoing edge index: node label -> edges */
	private final Map<String, List<GraphEdge>> outgoingIndex = new HashMap<>();
	/** Incoming edge index: node label -> edges */
	private final Map<String, List<GraphEdge>> incomingIndex = new HashMap<>();

	/** Current weight measure */
	private WeightMeasure currentMeasure = WeightMeasure.SUPPORT;
	/** Total number of patterns */
	private int totalPatternCount = 0;
	/** Whether weights need recomputation */
	private boolean weightsDirty = true;

	/** Whether any pattern has multi-item itemset */
	private boolean multiItemDetected = false;
	/** Whether any co-occurrence edge exists */
	private boolean coOccurrenceDetected = false;
	/** Cached max node pattern count */
	private int cachedMaxNodePatternCount = 0;
	/** Cached max edge weight */
	private double cachedMaxEdgeWeight = 0;
	/** Whether max edge weight needs recomputation */
	private boolean maxEdgeWeightDirty = true;

	// =========================================================================
	// Constructors
	// =========================================================================

	/**
	 * Creates an empty graph.
	 */
	public TransitionGraph() {
	}

	/**
	 * Creates a graph from a pattern reader.
	 * @param reader the pattern reader
	 */
	public TransitionGraph(SequentialPatternsReader reader) {
		this(reader.getPatterns());
	}

	/**
	 * Creates a graph from a list of patterns.
	 * @param patterns the patterns
	 */
	public TransitionGraph(List<SequentialPattern> patterns) {
		if (patterns != null) {
			for (SequentialPattern pattern : patterns) {
				List<List<String>> itemsets = pattern.getItemsets();
				Map<String, String> measures = pattern.getMeasures();

				int support = 1;
				String supStr = measures.get("SUP");
				if (supStr != null) {
					try {
						support = (int) Double.parseDouble(supStr.trim());
					} catch (NumberFormatException e) {
						support = 1;
					}
				}

				addPattern(itemsets, support, measures);
			}
		}
		recomputeWeights();
	}

	// =========================================================================
	// Adding patterns
	// =========================================================================

	/**
	 * Adds a pattern to the graph.
	 * @param itemsets the itemsets
	 * @param support  the support value
	 * @param measures the measures map
	 */
	private void addPattern(List<List<String>> itemsets, int support, Map<String, String> measures) {
		totalPatternCount++;
		weightsDirty = true;
		maxEdgeWeightDirty = true;

		StoredPattern sp = new StoredPattern(itemsets, support, measures);
		storedPatterns.add(sp);

		if (sp.hasMultiItemItemset()) {
			multiItemDetected = true;
		}

		// Ensure all items have nodes, count each item ONCE per pattern
		Set<String> itemsSeen = new HashSet<>();
		for (List<String> itemset : itemsets) {
			for (String item : itemset) {
				GraphNode node = nodes.get(item);
				if (node == null) {
					node = new GraphNode(item);
					nodes.put(item, node);
				}
				if (itemsSeen.add(item)) {
					node.addPatternOccurrence(support);
					if (node.getPatternCount() > cachedMaxNodePatternCount) {
						cachedMaxNodePatternCount = node.getPatternCount();
					}
				}
			}
		}

		// Co-occurrence edges: count each edge ONCE per pattern
		Set<String> edgesSeen = new HashSet<>();

		for (List<String> itemset : itemsets) {
			if (itemset.size() > 1) {
				coOccurrenceDetected = true;
				for (int i = 0; i < itemset.size(); i++) {
					for (int j = i + 1; j < itemset.size(); j++) {
						String a = itemset.get(i), b = itemset.get(j);
						String src, tgt;
						if (a.compareTo(b) < 0) { src = a; tgt = b; }
						else { src = b; tgt = a; }
						String key = src + "->" + tgt + ":" + EdgeType.CO_OCCURRENCE;
						GraphEdge edge = edges.get(key);
						if (edge == null) {
							edge = new GraphEdge(src, tgt, EdgeType.CO_OCCURRENCE);
							edges.put(key, edge);
							addToAdjacencyIndex(edge);
						}
						if (edgesSeen.add(key)) {
							edge.addPatternOccurrence(support);
						}
					}
				}
			}
		}

		// Sequential edges: count each edge ONCE per pattern
		for (int i = 0; i < itemsets.size() - 1; i++) {
			for (String src : itemsets.get(i)) {
				for (String tgt : itemsets.get(i + 1)) {
					String key = src + "->" + tgt + ":" + EdgeType.SEQUENTIAL;
					GraphEdge edge = edges.get(key);
					if (edge == null) {
						edge = new GraphEdge(src, tgt, EdgeType.SEQUENTIAL);
						edges.put(key, edge);
						addToAdjacencyIndex(edge);
					}
					if (edgesSeen.add(key)) {
						edge.addPatternOccurrence(support);
					}
				}
			}
		}
	}

	/**
	 * Adds an edge to adjacency indices. Co-occurrence edges appear bidirectionally.
	 * @param edge the edge to add
	 */
	private void addToAdjacencyIndex(GraphEdge edge) {
		outgoingIndex.computeIfAbsent(edge.getSourceItem(), k -> new ArrayList<>()).add(edge);
		incomingIndex.computeIfAbsent(edge.getTargetItem(), k -> new ArrayList<>()).add(edge);

		if (edge.getEdgeType() == EdgeType.CO_OCCURRENCE) {
			outgoingIndex.computeIfAbsent(edge.getTargetItem(), k -> new ArrayList<>()).add(edge);
			incomingIndex.computeIfAbsent(edge.getSourceItem(), k -> new ArrayList<>()).add(edge);
		}
	}

	// =========================================================================
	// Weights
	// =========================================================================

	/**
	 * Recomputes edge weights based on current measure.
	 */
	public void recomputeWeights() {
		if (!weightsDirty) return;

		switch (currentMeasure) {
		case SUPPORT:
			for (GraphEdge edge : edges.values())
				edge.setWeight(edge.getPatternCount());
			break;
		case CONFIDENCE:
			for (GraphEdge edge : edges.values()) {
				if (edge.getEdgeType() == EdgeType.CO_OCCURRENCE) {
					GraphNode sn = nodes.get(edge.getSourceItem());
					GraphNode tn = nodes.get(edge.getTargetItem());
					int minPC = Math.min(
							sn != null ? sn.getPatternCount() : 1,
							tn != null ? tn.getPatternCount() : 1);
					if (minPC == 0) minPC = 1;
					edge.setWeight((double) edge.getPatternCount() / minPC);
				} else {
					GraphNode sn = nodes.get(edge.getSourceItem());
					int srcPC = (sn != null) ? sn.getPatternCount() : 1;
					if (srcPC == 0) srcPC = 1;
					edge.setWeight((double) edge.getPatternCount() / srcPC);
				}
			}
			break;
		case LIFT:
			int totalNodes = nodes.size();
			if (totalNodes == 0) totalNodes = 1;
			for (GraphEdge edge : edges.values()) {
				if (edge.getEdgeType() == EdgeType.CO_OCCURRENCE) {
					GraphNode sn = nodes.get(edge.getSourceItem());
					GraphNode tn = nodes.get(edge.getTargetItem());
					int minPC = Math.min(
							sn != null ? sn.getPatternCount() : 1,
							tn != null ? tn.getPatternCount() : 1);
					if (minPC == 0) minPC = 1;
					edge.setWeight((double) edge.getPatternCount() / minPC);
				} else {
					GraphNode sn = nodes.get(edge.getSourceItem());
					GraphNode tn = nodes.get(edge.getTargetItem());
					int srcPC = (sn != null) ? sn.getPatternCount() : 1;
					if (srcPC == 0) srcPC = 1;
					double conf = (double) edge.getPatternCount() / srcPC;
					double exp = (tn != null) ? (double) tn.getPatternCount() / totalPatternCount : 1.0;
					if (exp == 0) exp = 1.0;
					edge.setWeight(conf / exp);
				}
			}
			break;
		}
		weightsDirty = false;
		maxEdgeWeightDirty = true;
	}

	/**
	 * Sets the weight measure.
	 * @param measure the measure to use
	 */
	public void setWeightMeasure(WeightMeasure measure) {
		if (this.currentMeasure != measure) {
			this.currentMeasure = measure;
			this.weightsDirty = true;
		}
	}

	// =========================================================================
	// Accessors
	// =========================================================================

	/** @return current weight measure */
	public WeightMeasure getWeightMeasure() { return currentMeasure; }
	/** @return map of nodes */
	public Map<String, GraphNode> getNodes() { return nodes; }
	/** @return map of edges */
	public Map<String, GraphEdge> getEdges() { return edges; }
	/** @return total pattern count */
	public int getTotalPatternCount() { return totalPatternCount; }
	/** @return list of stored patterns */
	public List<StoredPattern> getStoredPatterns() { return storedPatterns; }

	/** @return true if any pattern has multi-item itemsets */
	public boolean hasMultiItemItemsets() {
		return multiItemDetected;
	}

	/** @return true if any co-occurrence edges exist */
	public boolean hasCoOccurrenceEdges() {
		return coOccurrenceDetected;
	}

	/**
	 * Returns outgoing edges for a node in O(degree) time.
	 * @param itemLabel the node label
	 * @return list of outgoing edges
	 */
	public List<GraphEdge> getOutgoingEdges(String itemLabel) {
		List<GraphEdge> result = outgoingIndex.get(itemLabel);
		return result != null ? result : new ArrayList<>();
	}

	/**
	 * Returns incoming edges for a node in O(degree) time.
	 * @param itemLabel the node label
	 * @return list of incoming edges
	 */
	public List<GraphEdge> getIncomingEdges(String itemLabel) {
		List<GraphEdge> result = incomingIndex.get(itemLabel);
		return result != null ? result : new ArrayList<>();
	}

	/**
	 * Returns cached max node pattern count in O(1).
	 * @return max pattern count across all nodes
	 */
	public int getMaxNodePatternCount() {
		return cachedMaxNodePatternCount;
	}

	/**
	 * Returns cached max edge weight, recomputing if needed.
	 * @return max edge weight
	 */
	public double getMaxEdgeWeight() {
		recomputeWeights();
		if (maxEdgeWeightDirty) {
			cachedMaxEdgeWeight = 0;
			for (GraphEdge e : edges.values()) {
				if (e.getWeight() > cachedMaxEdgeWeight) cachedMaxEdgeWeight = e.getWeight();
			}
			maxEdgeWeightDirty = false;
		}
		return cachedMaxEdgeWeight;
	}

	// =========================================================================
	// Filtering — returns views, never modifies the graph
	// =========================================================================

	/**
	 * Filters patterns by various criteria.
	 * @param minItemCount    minimum item count
	 * @param maxItemCount    maximum item count
	 * @param minItemsetCount minimum itemset count
	 * @param maxItemsetCount maximum itemset count
	 * @param requiredItems   required items (may be null)
	 * @return filtered pattern list
	 */
	public List<StoredPattern> getFilteredPatterns(int minItemCount, int maxItemCount,
			int minItemsetCount, int maxItemsetCount, Set<String> requiredItems) {
		List<StoredPattern> result = new ArrayList<>();
		boolean hasReq = requiredItems != null && !requiredItems.isEmpty();
		for (StoredPattern p : storedPatterns) {
			int tic = p.getTotalItemCount();
			if (tic < minItemCount || tic > maxItemCount) continue;
			int isc = p.getItemsetCount();
			if (isc < minItemsetCount || isc > maxItemsetCount) continue;
			if (hasReq && !p.getContainedItems().containsAll(requiredItems)) continue;
			result.add(p);
		}
		return result;
	}

	/**
	 * Filters nodes by minimum and maximum pattern count and allowed items.
	 * @param minPatternCount minimum pattern count
	 * @param maxPatternCount maximum pattern count
	 * @param allowedItems    allowed items (may be null for all)
	 * @return filtered node list
	 */
	public List<GraphNode> getFilteredNodes(int minPatternCount, int maxPatternCount, Set<String> allowedItems) {
		List<GraphNode> result = new ArrayList<>();
		for (GraphNode n : nodes.values()) {
			if (n.getPatternCount() < minPatternCount) continue;
			if (n.getPatternCount() > maxPatternCount) continue;
			if (allowedItems != null && !allowedItems.contains(n.getItemLabel())) continue;
			result.add(n);
		}
		return result;
	}

	/**
	 * Filters edges by minimum and maximum weight, visible nodes, and allowed edge keys.
	 * @param minWeight       minimum weight
	 * @param maxWeight       maximum weight
	 * @param visibleNodes    set of visible node labels
	 * @param allowedEdgeKeys allowed edge keys (may be null for all)
	 * @return filtered edge list
	 */
	public List<GraphEdge> getFilteredEdges(double minWeight, double maxWeight, Set<String> visibleNodes, Set<String> allowedEdgeKeys) {
		recomputeWeights();
		List<GraphEdge> result = new ArrayList<>();
		for (GraphEdge e : edges.values()) {
			if (e.getWeight() < minWeight) continue;
			if (e.getWeight() > maxWeight) continue;
			if (!visibleNodes.contains(e.getSourceItem()) || !visibleNodes.contains(e.getTargetItem())) continue;
			if (allowedEdgeKeys != null && !allowedEdgeKeys.contains(e.getKey())) continue;
			result.add(e);
		}
		return result;
	}
}