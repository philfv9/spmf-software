package ca.pfv.spmf.tools.dataset_stats;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class read a graph database and calculates statistics
 * about this  database, then it prints the statistics.
 * <br/><br/>
 * In this version this class reads the database into memory before calculating the
 * statistics. It could be optimized to calculate statistics without
 * reading the database in memory because a single pass is required. It
 * was done like that because the code is simpler and easier to understand.

* @author Philippe Fournier-Viger, 2024
 */

public class GraphStatsGenerator {

	/**
	 * This method generates statistics for a graph database (a file)
	 * @param path the path to the file
	 * @throws IOException exception if there is a problem while reading the file.
	 */
	public void getStats(String path) throws IOException {

		/////////////////////////////////////
		//  (1) First we will read the graph database into memory.
		///////////////////////////////////

		List<Graph> graphs = new ArrayList<Graph>(); // A graph database is stored as a list of graphs
		int maxVertexLabel = 0; // the largest label for vertices in the database
		int maxEdgeLabel = 0; // the largest label for edges in the database

		String thisLine; // a temporary variable to read each line from the file

		BufferedReader myInput = null;
		try {
			// we read the file line by line
			FileInputStream fin = new FileInputStream(new File(path));
			myInput = new BufferedReader(new InputStreamReader(fin));

			// for each line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {
				// we split the line according to spaces into tokens
				String tokens[] = thisLine.split(" ");
				// we check the first token to determine the type of the line
				String firstToken = tokens[0];
				// if the first token is "t", it means that it is the start of a new graph
				if (firstToken.equals("t")) {
					// we create a new graph object to store the graph that correspond to this line.
					Graph graph = new Graph();
					// we add the graph to the list of graphs
					graphs.add(graph);
				}
				// if the first token is "v", it means that it is a vertex definition
				else if (firstToken.equals("v")) {
					// we get the vertex id and label from the tokens
					int vertexId = Integer.parseInt(tokens[1]);
					int vertexLabel = Integer.parseInt(tokens[2]);
					// we check if it has the largest label because we
					// want to keep this information
					if (vertexLabel > maxVertexLabel) {
						maxVertexLabel = vertexLabel;
					}
					// we add the vertex to the current graph
					graphs.get(graphs.size() - 1).addVertex(vertexId, vertexLabel);
				}
				// if the first token is "e", it means that it is an edge definition
				else if (firstToken.equals("e")) {
					// we get the source and target vertex ids and the edge label from the tokens
					int sourceId = Integer.parseInt(tokens[1]);
					int targetId = Integer.parseInt(tokens[2]);
					int edgeLabel = Integer.parseInt(tokens[3]);
					// we check if it has the largest label because we
					// want to keep this information
					if (edgeLabel > maxEdgeLabel) {
						maxEdgeLabel = edgeLabel;
					}
					// we add the edge to the current graph
					graphs.get(graphs.size() - 1).addEdge(sourceId, targetId, edgeLabel);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		/////////////////////////////////////
		//  We finished reading the database into memory.
		//  We will calculate statistics on this graph database.
		///////////////////////////////////

		System.out.println("============  GRAPH DATABASE STATS ==========");
		System.out.println("Number of graphs : " + graphs.size());

		// we initialize some variables that we will use to generate the statistics
		java.util.Set<Integer> vertexLabels = new java.util.HashSet<Integer>(); // the set of all vertex labels
		java.util.Set<Integer> edgeLabels = new java.util.HashSet<Integer>(); // the set of all edge labels
		List<Integer> vertexCounts = new ArrayList<Integer>(); // the number of vertices for each graph
		List<Integer> edgeCounts = new ArrayList<Integer>(); // the number of edges for each graph
		// Loop on graphs from the database
		for (Graph graph : graphs) {
			// we add the number of vertices and edges of this graph to the lists of counts
			vertexCounts.add(graph.getVertexCount());
			edgeCounts.add(graph.getEdgeCount());

			// this map is used to calculate the number of times that each vertex label
			// appear in this graph.
			// the key is a vertex label
			// the value is the number of occurences of the label until now for this graph
			HashMap<Integer, Integer> mapVertexLabels = new HashMap<Integer, Integer>();

			// this map is used to calculate the number of times that each edge label
			// appear in this graph.
			// the key is an edge label
			// the value is the number of occurences of the label until now for this graph
			HashMap<Integer, Integer> mapEdgeLabels = new HashMap<Integer, Integer>();

			// Loop on vertices from this graph
			for (Vertex vertex : graph.getVertices()) {
				// we get the vertex label
				int vertexLabel = vertex.getLabel();
				// If the label is not in the map already, we set count to 0
				Integer count = mapVertexLabels.get(vertexLabel);
				if (count == null) {
					count = 0;
				}
				// otherwise we set the count to count +1
				count = count + 1;
				mapVertexLabels.put(vertexLabel, count);
				// finally, we add the label to the set of vertex labels
				vertexLabels.add(vertexLabel);
			}

			// Loop on edges from this graph
			for (Edge edge : graph.getEdges()) {
				// we get the edge label
				int edgeLabel = edge.getLabel();
				// If the label is not in the map already, we set count to 0
				Integer count = mapEdgeLabels.get(edgeLabel);
				if (count == null) {
					count = 0;
				}
				// otherwise we set the count to count +1
				count = count + 1;
				mapEdgeLabels.put(edgeLabel, count);
				// finally, we add the label to the set of edge labels
				edgeLabels.add(edgeLabel);
			}
		}

		// we print the statistics
		System.out.println("File " + path);
		System.out.println("Number of distinct vertex labels: " + vertexLabels.size());
		System.out.println("Number of distinct edge labels: " + edgeLabels.size());
		System.out.println("Largest vertex label: " + maxVertexLabel);
		System.out.println("Largest edge label: " + maxEdgeLabel);
		System.out.println("Average number of vertices per graph : "
				+ calculateMean(vertexCounts) + " standard deviation: "
				+ calculateStdDeviation(vertexCounts) + " variance: "
				+ calculateVariance(vertexCounts));
		System.out.println("Average number of edges per graph : "
				+ calculateMean(edgeCounts) + " standard deviation: "
				+ calculateStdDeviation(edgeCounts) + " variance: "
				+ calculateVariance(edgeCounts));
	}

	// we define some helper methods to calculate the mean, standard deviation, variance, min, and max of a list of integers

	/**
	 * This method calculate the mean of a list of integers
	 * 
	 * @param list the list of integers
	 * @return the mean
	 */
	private static double calculateMean(List<Integer> list) {
		double sum = 0;
		for (Integer val : list) {
			sum += val;
		}
		return sum / list.size();
	}

	/**
	 * This method calculate the standard deviation of a list of integers
	 * 
	 * @param list the list of integers
	 * @return the standard deviation
	 */
	private static double calculateStdDeviation(List<Integer> list) {
		double deviation = 0;
		double mean = calculateMean(list);
		for (Integer val : list) {
			deviation += Math.pow(mean - val, 2);
		}
		return Math.sqrt(deviation / list.size());
	}

	/**
	 * This method calculate the variance of a list of integers
	 * 
	 * @param list the list of integers
	 * @return the variance
	 */
	private static double calculateVariance(List<Integer> list) {
		double deviation = 0;
		double mean = calculateMean(list);
		for (Integer val : list) {
			deviation += Math.pow(mean - val, 2);
		}
		return Math.pow(Math.sqrt(deviation / list.size()), 2);
	}


	/**
	 * A class to represent a graph
	 */
	 class Graph {

		// A list of vertices in the graph
		private List<Vertex> vertices;

		// A list of edges in the graph
		private List<Edge> edges;

		// A constructor to create an empty graph
		public Graph() {
			vertices = new ArrayList<Vertex>();
			edges = new ArrayList<Edge>();
		}

		// A method to add a vertex to the graph
		public void addVertex(int id, int label) {
			vertices.add(new Vertex(id, label));
		}

		// A method to add an edge to the graph
		public void addEdge(int sourceId, int targetId, int label) {
			edges.add(new Edge(sourceId, targetId, label));
		}

		// A method to get the number of vertices in the graph
		public int getVertexCount() {
			return vertices.size();
		}

		// A method to get the number of edges in the graph
		public int getEdgeCount() {
			return edges.size();
		}

		// A method to get the list of vertices in the graph
		public List<Vertex> getVertices() {
			return vertices;
		}

		// A method to get the list of edges in the graph
		public List<Edge> getEdges() {
			return edges;
		}
	}

	/**
	 *  A class to represent a vertex in the graph
	 */
	class Vertex {

		// The id of the vertex
		private int id;

		// The label of the vertex
		private int label;

		// A constructor to create a vertex with a given id and label
		public Vertex(int id, int label) {
			this.id = id;
			this.label = label;
		}

		// A method to get the id of the vertex
		public int getId() {
			return id;
		}

		// A method to get the label of the vertex
		public int getLabel() {
			return label;
		}
	}

	/**
	 *  A class to represent an edge in the graph
	 */
	class Edge {

		// The id of the source vertex
		private int sourceId;

		// The id of the target vertex
		private int targetId;

		// The label of the edge
		private int label;

		// A constructor to create an edge with a given source, target, and label
		public Edge(int sourceId, int targetId, int label) {
			this.sourceId = sourceId;
			this.targetId = targetId;
			this.label = label;
		}

		// A method to get the id of the source vertex
		public int getSourceId() {
			return sourceId;
		}

		// A method to get the id of the target vertex
		public int getTargetId() {
			return targetId;
		}

		// A method to get the label of the edge
		public int getLabel() {
			return label;
		}
	}
}
