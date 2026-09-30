// ============================================================
// File: AlgoCFGM.java
// ============================================================
package ca.pfv.spmf.algorithms.graph_mining.tkg.cfgm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.tkg.DFSCode;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Edge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.ExtendedEdge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.FrequentSubgraph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.SparseTriangularMatrix;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Vertex;
import ca.pfv.spmf.algorithms.graph_mining.tkg.VizGraph;
import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2018-2023 by Chao Cheng, Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*
* Do not remove copyright and license.
*/
/**
 * Implementation of the CFGM algorithm for mining closed frequent subgraphs.
 * <br/>
 * <br/>
 * CFGM: An algorithm for closed frequent graph patterns mining, by He Peng
 * and Defu Zhang. Information Sciences 625 (2023) 327-341.
 * <br/>
 * <br/>
 * This implementation reuses the DFS-code / rightmost extension framework of
 * the GSPAN implementation of SPMF, and adds on top of it: an exact
 * closedness test (Lemma 2 of CloseGraph), the explicit stack-based search of
 * Algorithm 2 of the CFGM paper with Case 1 and Case 2 pruning (Theorem 2),
 * embeddings propagated incrementally from parent to child nodes instead of
 * being recomputed, and a non-recursive incremental canonical-form check
 * (Algorithm 3, "DFSCodeMin"). <br/>
 * <br/>
 * The logic is split between this class (orchestration, I/O, preprocessing,
 * and the stack-based driver), {@link CFGMDfsCodeUtils} (embeddings,
 * extensions, canonical form, and the equivalent-extension test), and
 * {@link CFGMStackNode} (the search-tree node). <br/>
 * <br/>
 * This implementation saves the result to a file, using the same
 * input/output format as the other graph mining algorithms of SPMF.
 *
 * @author Philippe Fournier-Viger (based on the GSPAN implementation of Chao
 *         Cheng)
 */
public class AlgoCFGM {

	/** the minimum support represented as a count (number of graphs) */
	private int minSup;

	/** the list of closed frequent subgraphs found by the last execution */
	private List<FrequentSubgraph> frequentSubgraphs;

	/** runtime of the most recent execution, in seconds */
	private long runtime = 0;

	/** maximum memory usage of the most recent execution, in mb */
	private double maxmemory = 0;

	/** pattern count of the most recent execution */
	private int patternCount = 0;

	/** number of graphs in the input database */
	private int graphCount = 0;

	/** true if at least one frequent vertex label was found in the graph database */
	private boolean hasFrequentVertexLabels;

	/** helper holding all the DFS-code / embedding / canonical-form logic */
	private CFGMDfsCodeUtils dfsUtils;

	/** if true, debug mode is activated and extra statistics are printed */
	private static final boolean DEBUG_MODE = false;

	/** if true, eliminate infrequent vertex labels from graphs */
	private static final boolean ELIMINATE_INFREQUENT_VERTICES = true;

	/** if true, eliminate infrequent vertex pairs from graphs */
	private static final boolean ELIMINATE_INFREQUENT_VERTEX_PAIRS = true;

	/** if true, eliminate infrequent edge labels from graphs */
	private static final boolean ELIMINATE_INFREQUENT_EDGE_LABELS = true;

	/** if true, apply Case 2 pruning (Theorem 2 statement 2), disabled by default as in the paper */
	private static final boolean CASE2_PRUNING = false;

	/** number of infrequent vertex pairs removed during preprocessing */
	private int infrequentVertexPairsRemoved;

	/** number of infrequent vertices removed during preprocessing */
	private int infrequentVerticesRemovedCount;

	/** number of edges removed because of an infrequent edge label */
	private int edgeRemovedByLabel;

	/** number of empty graphs removed during preprocessing */
	private int emptyGraphsRemoved;

	/** number of extra stack nodes discarded by Case 1 pruning */
	private int case1PruneCount;

	/** number of nodes whose subtree expansion was skipped entirely by Case 2 pruning */
	private int case2PruneCount;

	/** maximum number of edges allowed in each frequent subgraph */
	private int maxNumberOfEdges = Integer.MAX_VALUE;

	/** if true, output the ids of graphs containing each frequent subgraph */
	private boolean outputGraphIds = true;

	/** counter used to assign a unique id/tag to each stack node */
	private int nextTagId = 0;

	/**
	 * Runs the CFGM algorithm on an input graph database and writes the closed
	 * frequent subgraphs found to an output file.
	 *
	 * @param inPath               the input file
	 * @param outPath              the output file
	 * @param minSupport           a minimum support value (percentage, between 0
	 *                             and 1)
	 * @param outputSingleVertices if true, closed frequent subgraphs containing a
	 *                             single vertex will be output
	 * @param outputDotFile        if true, a graphviz DOT file will be generated
	 * @param maxNumberOfEdges     the maximum number of edges of the closed
	 *                             frequent subgraphs to be found
	 * @param outputGraphIds       if true, output the ids of the graphs
	 *                             containing each closed frequent subgraph
	 * @throws IOException            if error while writing/reading a file
	 * @throws ClassNotFoundException if error while cloning a DFS code
	 */
	public void runAlgorithm(String inPath, String outPath, double minSupport, boolean outputSingleVertices,
			boolean outputDotFile, int maxNumberOfEdges, boolean outputGraphIds)
			throws IOException, ClassNotFoundException {

		if (maxNumberOfEdges <= 0) {
			return;
		}

		this.maxNumberOfEdges = maxNumberOfEdges;
		this.outputGraphIds = outputGraphIds;

		// reset counters and statistics from any previous execution
		infrequentVertexPairsRemoved = 0;
		infrequentVerticesRemovedCount = 0;
		edgeRemovedByLabel = 0;
		emptyGraphsRemoved = 0;
		case1PruneCount = 0;
		case2PruneCount = 0;
		nextTagId = 0;

		dfsUtils = new CFGMDfsCodeUtils();
		frequentSubgraphs = new ArrayList<>();

		MemoryLogger.getInstance().reset();

		long t1 = System.currentTimeMillis();

		// read the input graphs and compute the minimum support count
		List<Graph> graphDB = readGraphs(inPath);
		minSup = (int) Math.ceil(minSupport * graphDB.size());

		// run the mining process
		cfgm(graphDB, outputSingleVertices);

		MemoryLogger.getInstance().checkMemory();

		writeResultToFile(outPath);

		long t2 = System.currentTimeMillis();
		runtime = (t2 - t1) / 1000;
		maxmemory = MemoryLogger.getInstance().getMaxMemory();
		patternCount = frequentSubgraphs.size();

		if (outputDotFile) {
			outputDotFile(outPath);
		}
	}

	/**
	 * Generates graphviz DOT files for the frequent subgraphs written to an
	 * output file.
	 *
	 * @param outputPath the output file path
	 * @throws IOException if some exception occurs while reading/writing files
	 */
	private static void outputDotFile(String outputPath) throws IOException {
		String dirName = outputPath + "_dotfile";
		File dir = new File(dirName);
		if (!dir.exists())
			dir.mkdir();
		VizGraph.visulizeFromFile(outputPath, dirName);
	}

	/**
	 * Writes the closed frequent subgraphs found to an output file.
	 *
	 * @param outputPath an output file path
	 * @throws IOException if error while writing to the file
	 */
	private void writeResultToFile(String outputPath) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputPath)));
		final String NL = System.lineSeparator();
		bw.write("@FILETYPE=\"Frequent subgraphs\"");
		bw.append(System.lineSeparator());
		bw.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		bw.append(System.lineSeparator());
		
		int i = 0;
		for (FrequentSubgraph subgraph : frequentSubgraphs) {
			StringBuilder sb = new StringBuilder();

			DFSCode dfsCode = subgraph.dfsCode;
			sb.append("t # ").append(i).append(" * ").append(subgraph.support).append(NL);
			if (dfsCode.size() == 1) {
				// single-edge or single-vertex pattern
				ExtendedEdge ee = dfsCode.getEeL().get(0);
				sb.append("v 0 ").append(ee.getvLabel1()).append(NL);
				if (ee.getEdgeLabel() != -1) {
					sb.append("v 1 ").append(ee.getvLabel2()).append(NL);
					sb.append("e 0 1 ").append(ee.getEdgeLabel()).append(NL);
				}
			} else {
				// multi-edge pattern: output all vertices, then all edges
				List<Integer> vLabels = dfsCode.getAllVLabels();
				for (int j = 0; j < vLabels.size(); j++) {
					sb.append("v ").append(j).append(" ").append(vLabels.get(j)).append(NL);
				}
				for (ExtendedEdge ee : dfsCode.getEeL()) {
					sb.append("e ").append(ee.getV1()).append(" ").append(ee.getV2()).append(" ")
							.append(ee.getEdgeLabel()).append(NL);
				}
			}
			if (outputGraphIds) {
				sb.append("x");
				for (int id : subgraph.setOfGraphsIDs) {
					sb.append(" ").append(id);
				}
			}
			sb.append(NL).append(NL);

			bw.write(sb.toString());

			i++;
		}
		bw.close();
	}

	/**
	 * Reads the graphs of a graph database from an input file.
	 *
	 * @param path the input file
	 * @return the list of graphs read from the input file
	 * @throws IOException if error while reading the file
	 */
	private List<Graph> readGraphs(String path) throws IOException {
	    BufferedReader br = new BufferedReader(new FileReader(new File(path)));
	    List<Graph> graphDatabase = new ArrayList<>();

	    // skip any header/comment lines starting with '@' before looking for the first graph
	    String line;
	    do {
	        line = br.readLine();
	    } while (line != null && line.startsWith("@"));

	    boolean hasNextGraph = (line != null) && line.startsWith("t");

	    while (hasNextGraph) {
	        hasNextGraph = false;
	        int gId = Integer.parseInt(line.split(" ")[2]);
	        Map<Integer, Vertex> vMap = new HashMap<>();
	        while ((line = br.readLine()) != null && !line.startsWith("t")) {

	            // ignore comment lines starting with '@'
	            if (line.startsWith("@")) {
	                continue;
	            }

	            String[] items = line.split(" ");

	            if (line.startsWith("v")) {
	                // read a vertex
	                int vId = Integer.parseInt(items[1]);
	                int vLabel = Integer.parseInt(items[2]);
	                vMap.put(vId, new Vertex(vId, vLabel));
	            } else if (line.startsWith("e")) {
	                // read an edge and attach it to both of its endpoints
	                int v1 = Integer.parseInt(items[1]);
	                int v2 = Integer.parseInt(items[2]);
	                int eLabel = Integer.parseInt(items[3]);
	                Edge e = new Edge(v1, v2, eLabel);
	                vMap.get(v1).addEdge(e);
	                vMap.get(v2).addEdge(e);
	            }
	        }
	        graphDatabase.add(new Graph(gId, vMap));
	        if (line != null) {
	            hasNextGraph = true;
	        }
	    }

	    br.close();

	    graphCount = graphDatabase.size();
	    return graphDatabase;
	}

	/**
	 * A node of the DFS code search tree pending Case 1 pruning, together with
	 * the candidate DFS code it would grow into. Used only while gathering the
	 * frequent canonical rightmost children of a node in {@link #expandNode}.
	 */
	private static class RightmostCandidate {
		final ExtendedEdge edge;
		final CFGMDfsCodeUtils.ExtensionInfo info;
		final DFSCode code;

		RightmostCandidate(ExtendedEdge edge, CFGMDfsCodeUtils.ExtensionInfo info, DFSCode code) {
			this.edge = edge;
			this.info = info;
			this.code = code;
		}
	}

	/**
	 * Prepares the graph database (removing infrequent labels, vertex pairs and
	 * edge labels), computes the initial frequent 1-edge patterns, and launches
	 * the stack-based mining process.
	 *
	 * @param graphDB                a graph database
	 * @param outputFrequentVertices if true, include closed frequent single
	 *                               vertex subgraphs in the output
	 * @throws IOException            if error writing/reading a file
	 * @throws ClassNotFoundException if error cloning a DFS code
	 */
	private void cfgm(List<Graph> graphDB, boolean outputFrequentVertices) throws IOException, ClassNotFoundException {

		// find frequent vertex labels and remove infrequent ones (also needed below)
		findAllOnlyOneVertex(graphDB, outputFrequentVertices);

		// nothing can possibly be frequent: bail out before doing any further
		// (potentially expensive) preprocessing on the whole database
		if (!hasFrequentVertexLabels) {
			return;
		}

		// rebuild the cached vertex list of each graph now that infrequent labels are removed
		for (Graph g : graphDB) {
			g.precalculateVertexList();
		}

		if (ELIMINATE_INFREQUENT_VERTEX_PAIRS || ELIMINATE_INFREQUENT_EDGE_LABELS) {
			removeInfrequentVertexPairs(graphDB);
		}

		// keep only non-empty graphs and precalculate neighbor/label lookups
		Set<Integer> graphIds = new HashSet<>();
		for (int i = 0; i < graphDB.size(); i++) {
			Graph g = graphDB.get(i);

			if (g.getAllVertices().length != 0) {
				graphIds.add(i);

				g.precalculateVertexNeighbors();
				g.precalculateLabelsToVertices();
			} else {
				emptyGraphsRemoved++;
			}
		}

		Map<ExtendedEdge, Set<Integer>> initialExtensions = dfsUtils.computeInitialExtensions(graphDB, graphIds);

		List<CFGMStackNode> initialNodes = new ArrayList<>();
		for (Entry<ExtendedEdge, Set<Integer>> entry : initialExtensions.entrySet()) {
			Set<Integer> supportSet = entry.getValue();
			if (supportSet.size() < minSup) {
				continue;
			}

			// keep only the canonical direction of each edge, discard the other
			DFSCode c = new DFSCode();
			c.add(entry.getKey());
			if (!dfsUtils.isCanonical(c)) {
				continue;
			}

			CFGMStackNode node = new CFGMStackNode();
			node.code = c;
			// initialExtensions is local and discarded after this loop, so the set can be reused directly (no copy needed)
			node.graphIds = supportSet;

			// bootstrap the embeddings of this initial 1-edge pattern
			node.embeddings = new HashMap<>();
			for (Integer gid : supportSet) {
				node.embeddings.put(gid, dfsUtils.subgraphIsomorphisms(c, graphDB.get(gid)));
			}

			node.id = nextTagId++;
			node.tag = node.id;
			initialNodes.add(node);
		}

		// sort in inverse DFS lexicographic order so the smallest code ends up on top of the stack
		initialNodes.sort((a, b) -> {
			ExtendedEdge eeA = a.code.getEeL().get(0);
			ExtendedEdge eeB = b.code.getEeL().get(0);
			if (eeA.smallerThan(eeB))
				return 1;
			if (eeB.smallerThan(eeA))
				return -1;
			return 0;
		});

		cfgmStackDFS(initialNodes, graphDB);
	}

	/**
	 * Runs the core stack-based traversal of CFGM (Algorithm 2 of the paper,
	 * with Case 1 and Case 2 pruning). Each node is visited at most twice while
	 * on top of the stack: the first time it is expanded (children pushed on
	 * top of it), the second time it is discarded after the Case 1 pruning
	 * check.
	 *
	 * @param initialNodes the initial (1-edge) frequent canonical DFS codes,
	 *                     already sorted so that the smallest ends up on top
	 *                     once pushed
	 * @param graphDB      the graph database
	 * @throws IOException            if error writing/reading a file
	 * @throws ClassNotFoundException if error cloning a DFS code
	 */
	private void cfgmStackDFS(List<CFGMStackNode> initialNodes, List<Graph> graphDB)
			throws IOException, ClassNotFoundException {

		Deque<CFGMStackNode> stack = new ArrayDeque<>();
		for (CFGMStackNode n : initialNodes) {
			stack.push(n);
		}

		while (!stack.isEmpty()) {
			CFGMStackNode top = stack.peek();

			if (top.expanded) {
				// Case 1 pruning: this node's subtree is fully explored, find the
				// founder of its equivalence chain and discard everything above it
				CFGMStackNode ancestor = top.parent;
				while (ancestor != null && ancestor.id != top.tag) {
					ancestor = ancestor.parent;
				}

				if (ancestor != null) {
					int removed = 0;
					CFGMStackNode popped;
					do {
						popped = stack.pop();
						removed++;
					} while (popped != ancestor);
					case1PruneCount += removed;
				} else {
					stack.pop();
				}
			} else {
				top.expanded = true;
				expandNode(top, graphDB, stack);

				// Memory usage only needs to be sampled when actual mining work (expansion)
				// happens, not on every stack pop performed for the Case 1 pruning check.
				MemoryLogger.getInstance().checkMemory();
			}
		}
	}

	/**
	 * Expands a node of the DFS code search tree: computes its full one-edge
	 * extension set, checks and reports its closedness (Lemma 2), applies Case
	 * 2 pruning if enabled, and pushes its frequent canonical rightmost
	 * extensions on top of the stack.
	 *
	 * @param node    the node to expand (already marked as expanded by the
	 *                caller)
	 * @param graphDB the graph database
	 * @param stack   the shared stack, onto which children are pushed
	 * @throws IOException            if error writing/reading a file
	 * @throws ClassNotFoundException if error cloning a DFS code
	 */
	private void expandNode(CFGMStackNode node, List<Graph> graphDB, Deque<CFGMStackNode> stack)
			throws IOException, ClassNotFoundException {

		DFSCode c = node.code;
		int currentSupport = node.graphIds.size();

		Map<ExtendedEdge, CFGMDfsCodeUtils.ExtensionInfo> extensionInfoMap = dfsUtils.computeFullExtensions(c,
				node.embeddings, graphDB);

		// c is closed iff no extension (rightmost or not) preserves its support (Lemma 2)
		boolean closed = true;
		for (CFGMDfsCodeUtils.ExtensionInfo info : extensionInfoMap.values()) {
			if (info.graphIds.size() == currentSupport) {
				closed = false;
				break;
			}
		}
		if (closed) {
			// node.graphIds is never mutated afterward, so it can be reused directly (no copy needed)
			frequentSubgraphs.add(new FrequentSubgraph(c, node.graphIds, currentSupport));
		}

		// do not extend further if the maximum size is reached
		if (c.size() >= maxNumberOfEdges) {
			return;
		}

		// Case 2 pruning: if a non-rightmost extension is an equivalent extension
		// of this node, its whole subtree is redundant (Theorem 2 statement 2)
		if (CASE2_PRUNING) {
			for (Entry<ExtendedEdge, CFGMDfsCodeUtils.ExtensionInfo> entry : extensionInfoMap.entrySet()) {
				ExtendedEdge ee = entry.getKey();
				if (dfsUtils.isRightmostExtension(c, ee)) {
					continue;
				}
				if (dfsUtils.isEquivalentExtension(node.embeddings, graphDB, ee)) {
					case2PruneCount++;
					return;
				}
			}
		}

		// gather rightmost, frequent, canonical children
		List<RightmostCandidate> candidates = new ArrayList<>();

		for (Entry<ExtendedEdge, CFGMDfsCodeUtils.ExtensionInfo> entry : extensionInfoMap.entrySet()) {
			ExtendedEdge ee = entry.getKey();
			if (!dfsUtils.isRightmostExtension(c, ee)) {
				continue;
			}
			CFGMDfsCodeUtils.ExtensionInfo info = entry.getValue();
			if (info.graphIds.size() < minSup) {
				continue;
			}

			DFSCode newC = c.copy();
			newC.add(ee);
			if (!dfsUtils.isCanonical(newC)) {
				continue;
			}

			candidates.add(new RightmostCandidate(ee, info, newC));
		}

		// sort in inverse DFS lexicographic order so the smallest ends up on top of the stack
		candidates.sort((a, b) -> {
			if (a.edge.smallerThan(b.edge))
				return 1;
			if (b.edge.smallerThan(a.edge))
				return -1;
			return 0;
		});

		for (RightmostCandidate cand : candidates) {
			CFGMStackNode child = new CFGMStackNode();
			child.code = cand.code;
			child.embeddings = cand.info.embeddings; // propagated, not recomputed
			// extensionInfoMap is local and discarded after this method returns, so the set can be reused directly (no copy needed)
			child.graphIds = cand.info.graphIds;
			child.id = nextTagId++;
			child.parent = node;

			// chain the equivalence class tag if this is an equivalent extension of the parent
			if (dfsUtils.isEquivalentExtension(node.embeddings, graphDB, cand.edge)) {
				child.tag = node.tag;
			} else {
				child.tag = child.id;
			}

			stack.push(child);
		}
	}

	/** A pair of vertex labels used as a key in the pruning matrix. */
	private static class Pair {
		int x;
		int y;

		Pair(int x, int y) {
			if (x < y) {
				this.x = x;
				this.y = y;
			} else {
				this.x = y;
				this.y = x;
			}
		}

		@Override
		public boolean equals(Object obj) {
			Pair other = (Pair) obj;
			return other.x == this.x && other.y == this.y;
		}

		@Override
		public int hashCode() {
			return 31 * x + y;
		}
	}

	/**
	 * Removes infrequent vertex pairs and/or infrequent edge labels from the
	 * graphs of the database (same strategy as in GSPAN). The two pruning
	 * strategies are independent and each guarded by its own flag.
	 *
	 * @param graphDB the graph database
	 */
	private void removeInfrequentVertexPairs(List<Graph> graphDB) {

		Set<Pair> alreadySeenPair = null;
		SparseTriangularMatrix matrix = null;
		Set<Integer> alreadySeenEdgeLabel = null;
		Map<Integer, Integer> mapEdgeLabelToSupport = null;

		if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
			matrix = new SparseTriangularMatrix();
			alreadySeenPair = new HashSet<>();
		}
		if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
			mapEdgeLabelToSupport = new HashMap<>();
			alreadySeenEdgeLabel = new HashSet<>();
		}

		// count the support of each vertex-label pair and each edge label
		for (Graph g : graphDB) {
			Vertex[] vertices = g.getAllVertices();

			for (int i = 0; i < vertices.length; i++) {
				Vertex v1 = vertices[i];
				int labelV1 = v1.getLabel();

				for (Edge edge : v1.getEdgeList()) {
					// an undirected edge appears in the list of both its endpoints; process it only once
					if (v1.getId() != edge.v1) {
						continue;
					}

					// v1.getId() == edge.v1 was just confirmed above, so the other endpoint is simply edge.v2
					int v2 = edge.v2;
					int labelV2 = g.getVLabel(v2);

					if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
						Pair pair = new Pair(labelV1, labelV2);
						if (alreadySeenPair.add(pair)) {
							matrix.incrementCount(labelV1, labelV2);
						}
					}

					if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
						int edgeLabel = edge.getEdgeLabel();
						if (alreadySeenEdgeLabel.add(edgeLabel)) {
							Integer edgeSupport = mapEdgeLabelToSupport.get(edgeLabel);
							mapEdgeLabelToSupport.put(edgeLabel, edgeSupport == null ? 1 : edgeSupport + 1);
						}
					}
				}
			}
			if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
				alreadySeenPair.clear();
			}
			if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
				alreadySeenEdgeLabel.clear();
			}
		}

		if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
			matrix.removeInfrequentEntriesFromMatrix(minSup);
		}

		// remove the edges whose vertex pair or edge label is infrequent (each vertex's own list must be
		// scanned fully here, since both endpoints hold their own reference to the edge and must be updated)
		if (ELIMINATE_INFREQUENT_VERTEX_PAIRS || ELIMINATE_INFREQUENT_EDGE_LABELS) {
			for (Graph g : graphDB) {
				Vertex[] vertices = g.getAllVertices();

				for (int i = 0; i < vertices.length; i++) {
					Vertex v1 = vertices[i];
					int labelV1 = v1.getLabel();

					Iterator<Edge> iter = v1.getEdgeList().iterator();
					while (iter.hasNext()) {
						Edge edge = iter.next();
						int v2 = edge.another(v1.getId());
						int labelV2 = g.getVLabel(v2);

						if (ELIMINATE_INFREQUENT_VERTEX_PAIRS
								&& matrix.getSupportForItems(labelV1, labelV2) < minSup) {
							iter.remove();
							infrequentVertexPairsRemoved++;
						} else if (ELIMINATE_INFREQUENT_EDGE_LABELS
								&& mapEdgeLabelToSupport.get(edge.getEdgeLabel()) < minSup) {
							iter.remove();
							edgeRemovedByLabel++;
						}
					}
				}
			}
		}
	}

	/**
	 * Finds all frequent vertex labels of the graph database, removes infrequent
	 * ones, and optionally reports each frequent label that is a closed
	 * single-vertex subgraph.
	 *
	 * @param graphDB                a graph database
	 * @param outputFrequentVertices if true, the closed frequent single vertices
	 *                               will be output
	 */
	private void findAllOnlyOneVertex(List<Graph> graphDB, boolean outputFrequentVertices) {

		hasFrequentVertexLabels = false;

		Map<Integer, Set<Integer>> labelM = new HashMap<>();

		// collect, for each vertex label, the set of graphs where it occurs
		for (Graph g : graphDB) {
			for (Vertex v : g.getNonPrecalculatedAllVertices()) {

				if (!v.getEdgeList().isEmpty()) {
					Integer vLabel = v.getLabel();
					labelM.computeIfAbsent(vLabel, k -> new HashSet<>()).add(g.getId());
				}
			}
		}

		for (Entry<Integer, Set<Integer>> entry : labelM.entrySet()) {
			int label = entry.getKey();

			Set<Integer> tempSupG = entry.getValue();
			int sup = tempSupG.size();
			if (sup >= minSup) {
				hasFrequentVertexLabels = true;

				if (outputFrequentVertices) {
					boolean closed = isSingleVertexClosed(label, tempSupG, graphDB);
					if (closed) {
						DFSCode tempD = new DFSCode();
						tempD.add(new ExtendedEdge(0, 0, label, label, -1));

						frequentSubgraphs.add(new FrequentSubgraph(tempD, tempSupG, sup));
					}
				}
			} else if (ELIMINATE_INFREQUENT_VERTICES) {
				// remove this infrequent label from every graph where it occurs
				for (Integer graphid : tempSupG) {
					Graph g = graphDB.get(graphid);
					g.removeInfrequentLabel(label);
					infrequentVerticesRemovedCount++;
				}
			}
		}
	}

	/**
	 * Checks whether the single-vertex pattern with the given label is closed,
	 * i.e. no single-edge extension of it has the same support.
	 *
	 * @param label           the vertex label
	 * @param supportGraphIds the set of graph ids where the label is frequent
	 * @param graphDB         the graph database
	 * @return true if closed
	 */
	private boolean isSingleVertexClosed(int label, Set<Integer> supportGraphIds, List<Graph> graphDB) {
		Map<ExtendedEdge, Set<Integer>> extSupport = new HashMap<>();

		// compute the support of every single-edge extension of this vertex label
		for (Integer gid : supportGraphIds) {
			Graph g = graphDB.get(gid);
			for (Vertex v : g.getNonPrecalculatedAllVertices()) {
				if (v.getLabel() != label || v.getEdgeList().isEmpty()) {
					continue;
				}
				for (Edge e : v.getEdgeList()) {
					int otherId = e.another(v.getId());
					int otherLabel = g.getVLabel(otherId);
					int edgeLabel = e.getEdgeLabel();
					ExtendedEdge ee = (label <= otherLabel) ? new ExtendedEdge(0, 1, label, otherLabel, edgeLabel)
							: new ExtendedEdge(0, 1, otherLabel, label, edgeLabel);
					extSupport.computeIfAbsent(ee, k -> new HashSet<>()).add(gid);
				}
			}
		}

		// not closed if any extension has the same support as the vertex itself
		for (Set<Integer> s : extSupport.values()) {
			if (s.size() == supportGraphIds.size()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Prints statistics about the last execution of this algorithm to
	 * System.out.
	 */
	public void printStats() {
		System.out.println("=============  CFGM v1.7 - STATS =============");
		System.out.println(" Number of graph in the input database: " + graphCount);
		System.out.println(" Closed frequent subgraph count : " + patternCount);
		System.out.println(" Total time ~ " + runtime + " s");
		System.out.println(" Minsup : " + minSup + " graphs");
		System.out.println(" Maximum memory usage : " + maxmemory + " mb");

		if (DEBUG_MODE) {
			if (ELIMINATE_INFREQUENT_VERTEX_PAIRS || ELIMINATE_INFREQUENT_VERTICES) {
				System.out.println("  -------------------");
			}
			if (ELIMINATE_INFREQUENT_VERTICES) {
				System.out.println("  Number of infrequent vertices pruned : " + infrequentVerticesRemovedCount);
				System.out.println("  Empty graphs removed : " + emptyGraphsRemoved);
			}
			if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
				System.out.println("  Number of infrequent vertex pairs pruned : " + infrequentVertexPairsRemoved);
			}
			if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
				System.out.println("  Number of infrequent edge labels pruned : " + edgeRemovedByLabel);
			}
			if (CFGMDfsCodeUtils.EDGE_COUNT_PRUNING) {
				System.out.println(
						"  Extensions skipped (edge count pruning) : " + dfsUtils.getEdgeCountPruneCount());
			}
			System.out.println("  Extra stack nodes discarded (Case 1 pruning) : " + case1PruneCount);
			if (CASE2_PRUNING) {
				System.out.println(
						"  Nodes whose expansion was skipped (Case 2 pruning) : " + case2PruneCount);
			}
		}
		System.out.println("===================================================");
	}
}