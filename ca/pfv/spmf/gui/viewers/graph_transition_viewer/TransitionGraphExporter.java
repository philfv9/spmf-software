package ca.pfv.spmf.gui.viewers.graph_transition_viewer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

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
 * Exports a {@link TransitionGraph} (nodes, edges, patterns) to a CSV-like text file.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class TransitionGraphExporter {

	/**
	 * Export the currently filtered graph data (nodes, edges, and patterns) to a
	 * CSV-like text file.
	 *
	 * @param graph    the transition graph containing metadata (weight measure, total pattern count)
	 * @param nodes    the list of filtered graph nodes to export
	 * @param edges    the list of filtered graph edges to export
	 * @param patterns the list of filtered stored patterns to export (may be {@code null})
	 * @param file     the output file to write to
	 * @throws IOException if an I/O error occurs while writing to the file
	 */
	public static void exportToFile(TransitionGraph graph, List<TransitionGraph.GraphNode> nodes,
			List<TransitionGraph.GraphEdge> edges, List<TransitionGraph.StoredPattern> patterns, File file)
			throws IOException {
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
			writer.println("# Sequential Pattern Transition Graph");
			writer.println("# Weight measure: " + graph.getWeightMeasure());
			writer.println("# Total patterns in file: " + graph.getTotalPatternCount());
			writer.println("# Exported patterns: " + (patterns != null ? patterns.size() : 0));
			writer.println();

			writer.println("# NODES");
			writer.println("# Item,PatternCount,MaxSupport,X,Y");
			for (TransitionGraph.GraphNode node : nodes) {
				writer.println(node.getItemLabel()
						+ "," + node.getPatternCount()
						+ "," + node.getMaxSupport()
						+ "," + String.format("%.2f", node.getX())
						+ "," + String.format("%.2f", node.getY()));
			}
			writer.println();

			writer.println("# EDGES");
			writer.println("# Source,Target,Type,PatternCount,MaxSupport,Weight");
			for (TransitionGraph.GraphEdge edge : edges) {
				writer.println(edge.getSourceItem()
						+ "," + edge.getTargetItem()
						+ "," + edge.getEdgeType()
						+ "," + edge.getPatternCount()
						+ "," + edge.getMaxSupport()
						+ "," + String.format("%.6f", edge.getWeight()));
			}
			writer.println();

			if (patterns != null) {
				writer.println("# PATTERNS");
				for (TransitionGraph.StoredPattern pattern : patterns) {
					writer.println(pattern.getDisplayString());
				}
			}
		}
	}
}