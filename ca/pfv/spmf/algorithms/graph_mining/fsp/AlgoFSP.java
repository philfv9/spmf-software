// ===================== File: AlgoFSP.java =====================
package ca.pfv.spmf.algorithms.graph_mining.fsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.tkg.DFSCode;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Edge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.ExtendedEdge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.FrequentSubgraph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Vertex;
import ca.pfv.spmf.algorithms.graph_mining.tkg.VizGraph;
import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2018 by Chao Cheng, Philippe Fournier-Viger
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
 * This is an implementation of the FSP algorithm (Frequent Substructure Pattern
 * mining) <br/>
 * <br/>
 *
 * The FSP algorithm is described in : <br/>
 * <br/>
 *
 * S. Han, W. K. Ng, and Y. Yu. FSP: Frequent Substructure Pattern Mining. ICICS
 * 2007.
 *
 * <br/>
 * <br/>
 *
 * FSP is an improvement over the gSpan algorithm. It uses the same
 * lexicographic DFS-code based canonical form as gSpan, but adds optimizations
 * described in the paper, plus one additional optimization that is not named in
 * the paper but is required to make the paper's own stated goal ("reduce the
 * number of graph and subgraph isomorphism tests") actually hold given the
 * architecture used here:
 * <ul>
 * <li>(1) <b>Label sorting/relabeling</b>: infrequent vertex/edge labels are
 * removed and the remaining ones are relabeled by ascending or descending
 * frequency order (Algorithm 1 in the paper). See {@link FSPPreprocessor}.</li>
 * <li>(2) <b>Fast Pruning</b> (DFS Balance): a DFS code that is not "DFS
 * balanced" cannot be a minimum DFS code and is discarded without running the
 * more expensive canonical-form (MinCheck) test. See {@link FSPMiner}.</li>
 * <li>(3) <b>Backward Graph-Index Assignation</b> (paper Section III-C, first
 * structural similarity, Figure 2): if a DFS code {@code C} was created by
 * adding a backward edge to a parent {@code P}, and {@code C} has "sibling"
 * backward-edge children of {@code P} (other backward targets), then whenever
 * {@code C} is further extended by a backward edge matching one of those
 * sibling targets, the resulting compound subgraph is *exactly* the same
 * subgraph that a sibling branch would eventually (re)derive. This is cached
 * (see {@code BackwardPairKey} in {@link FSPMiner}) so that whichever branch
 * reaches it first lets the other one skip the MinCheck test and the entire
 * redundant recursive subtree exploration. This is implemented faithfully and
 * exactly (not just approximated) for this well-defined pairwise, one-level
 * case.
 * <p>
 * <b>Note on the paper's second structural similarity</b> (forward-edge case,
 * Figure 3): after analysis, this optimization's benefit is already fully
 * subsumed here by the fact that {@link FSPMiner#rightMostPathExtensions}
 * computes ALL rightmost-path extensions of a code (both forward and backward,
 * at every valid position) in a single isomorphism pass over the code's
 * support set. In the paper's own (naive) baseline architecture, each
 * candidate child requires its own separate {@code getFrequency()} isomorphism
 * test, so reusing a grandchild's index for a shallower sibling saves a real,
 * dedicated test. Here, that shallower sibling's exact support was already
 * computed for free in the very same pass that discovered its parent - before
 * we even descend into the deeper branch. Implementing the paper's shortcut on
 * top of that would perform no additional tests, and would in fact introduce a
 * correctness risk (the relationship is only an "is a subset of" relationship,
 * not an equality, so naively assigning indices this way without an exhaustive
 * re-verification could silently under/over count support). It is therefore
 * intentionally NOT implemented as a separate mechanism.
 * </p>
 * </li>
 * <li>(4) <b>Incremental isomorphism reuse across recursion levels</b> (not
 * separately named by the paper, but required to actually realize its stated
 * goal): the true remaining redundancy in a batched/embedding-list architecture
 * like this one is that, naively, extending a code {@code C} into a child
 * {@code C'} would re-run {@link FSPMiner#subgraphIsomorphisms(DFSCode, Graph)}
 * on {@code C'} from scratch for every graph, re-deriving the match of the
 * entire prefix {@code C} yet again, even though that exact prefix was just
 * matched one recursion level up. This implementation now retains, for every
 * candidate extension edge, the list of resulting embeddings (isomorphism
 * mappings) per supporting graph, and threads them down into the recursive
 * call, so each recursion level only performs one incremental matching step
 * instead of re-matching the whole prefix. This can be disabled via
 * {@link #setUseIncrementalIsomorphism(boolean)} to fall back to the original
 * from-scratch behavior (lower memory usage, more isomorphism tests).</li>
 * </ul>
 *
 * <br/>
 * This implementation saves the result to a file.
 * <p>
 * This class is the main entry point of the algorithm (I/O, orchestration and
 * statistics). The actual preprocessing (label relabeling/pruning) is
 * implemented in {@link FSPPreprocessor}, and the DFS-tree mining
 * (isomorphism matching, extension generation, DFS-balance pruning and
 * canonical form checking) is implemented in {@link FSPMiner}.
 * </p>
 *
 * @author Chen Hui (based on the SPMF gSpan implementation by Chao Cheng)
 */
public class AlgoFSP {

	/**
	 * the minimum support represented as a count (number of subgraph occurrences)
	 */
	int minSup;

	/** the list of frequent subgraphs found by the last execution */
	List<FrequentSubgraph> frequentSubgraphs;

	/** runtime of the most recent execution, in seconds */
	private long runtime = 0;

	/** max memory of the most recent execution, in megabytes */
	private double maxmemory = 0;

	/** pattern count of the most recent execution */
	private int patternCount = 0;

	/** number of graph in the input database */
	private int graphCount = 0;

	/** frequent vertex labels (relabeled ids) */
	List<Integer> frequentVertexLabels;

	/** if true, debug mode is activated */
	static final boolean DEBUG_MODE = false;

	/** eliminate infrequent vertex pairs from graphs */
	static final boolean ELIMINATE_INFREQUENT_VERTEX_PAIRS = true;

	/** eliminate infrequent labels from graphs */
	static final boolean ELIMINATE_INFREQUENT_EDGE_LABELS = true;

	/** apply edge count pruning strategy */
	static final boolean EDGE_COUNT_PRUNING = true;

	/**
	 * if true, relabel vertex/edge labels by descending frequency instead of
	 * ascending
	 */
	boolean useDescendingLabelOrder = false;

	/**
	 * if true (default), apply the DFS-balance based fast pruning test before the
	 * full canonical form check
	 */
	boolean useFastPruning = true;

	/**
	 * if true (default), enable the "Backward Graph-Index Assignation" optimization
	 * (see class-level documentation)
	 */
	boolean useBackwardAssignation = true;

	/**
	 * if true (default), reuse isomorphism embeddings across recursion levels
	 * instead of recomputing them from scratch
	 */
	boolean useIncrementalIsomorphism = true;

	/** number of infrequent vertex pairs removed */
	int infrequentVertexPairsRemoved;

	/** number of infrequent vertices removed (by label) */
	int infrequentVerticesRemovedCount;

	/** number of infrequent edges removed (by label) */
	int edgeRemovedByLabel;

	/** number of empty graphs removed */
	int emptyGraphsRemoved;

	/** number of extensions skipped by the edge count pruning strategy */
	int pruneByEdgeCountCount;

	/** number of times fast pruning (DFS balance test) avoided a MinCheck call */
	int fastPruningCount;

	/**
	 * number of times the Backward Graph-Index Assignation optimization avoided a
	 * redundant MinCheck test and subtree exploration
	 */
	int backwardAssignationCount;

	/**
	 * number of times a full subgraph isomorphism recomputation was avoided by
	 * reusing embeddings from the previous recursion level
	 */
	int embeddingReuseCount;

	/** maximum number of edges in each frequent subgraph */
	int maxNumberOfEdges = Integer.MAX_VALUE;

	/** output the ids of graph containing each frequent subgraph */
	boolean outputGraphIds = true;

	/** old vertex label -> new (relabeled) vertex label */
	Map<Integer, Integer> vertexLabelOldToNew;

	/** new (relabeled) vertex label -> old vertex label (for output) */
	Map<Integer, Integer> vertexLabelNewToOld;

	/** old edge label -> new (relabeled) edge label */
	Map<Integer, Integer> edgeLabelOldToNew;

	/** new (relabeled) edge label -> old edge label (for output) */
	Map<Integer, Integer> edgeLabelNewToOld;

	/** helper object implementing the label relabeling/pruning preprocessing */
	private final FSPPreprocessor preprocessor = new FSPPreprocessor(this);

	/** helper object implementing the DFS-tree mining */
	private final FSPMiner miner = new FSPMiner(this);

	/**
	 * Default constructor.
	 */
	public AlgoFSP() {
	}

	/**
	 * Set whether descending frequency order should be used when relabeling
	 * vertex/edge labels.
	 *
	 * @param flag true to use descending order, false (default) for ascending
	 */
	public void setUseDescendingLabelOrder(boolean flag) {
		this.useDescendingLabelOrder = flag;
	}

	/**
	 * Set whether the DFS-balance based fast pruning should be used.
	 *
	 * @param flag true (default) to enable
	 */
	public void setUseFastPruning(boolean flag) {
		this.useFastPruning = flag;
	}

	/**
	 * Set whether the backward graph-index assignation optimization should be used
	 * (see class-level documentation).
	 *
	 * @param flag true (default) to enable
	 */
	public void setUseBackwardAssignation(boolean flag) {
		this.useBackwardAssignation = flag;
	}

	/**
	 * Set whether isomorphism embeddings should be reused across recursion levels
	 * (incremental matching) instead of being recomputed from scratch at every
	 * level.
	 *
	 * @param flag true (default) to enable
	 */
	public void setUseIncrementalIsomorphism(boolean flag) {
		this.useIncrementalIsomorphism = flag;
	}

	/**
	 * Run the FSP algorithm.
	 *
	 * @param inPath               the input file
	 * @param outPath              the output file
	 * @param minSupport           a minimum support value (a percentage represented
	 *                             by a value between 0 and 1)
	 * @param outputSingleVertices if true, frequent subgraphs containing a single
	 *                             vertex will be output
	 * @param outputDotFile        if true, a graphviz DOT file will be generated to
	 *                             visualize the patterns
	 * @param maxNumberOfEdges     an integer indicating a maximum number of edges
	 *                             for each frequent subgraph
	 * @param outputGraphIds       Output the ids of graph containing each frequent
	 *                             subgraph
	 * @throws IOException            if error while writing to file
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

		// reset statistics
		infrequentVertexPairsRemoved = 0;
		infrequentVerticesRemovedCount = 0;
		edgeRemovedByLabel = 0;
		emptyGraphsRemoved = 0;
		pruneByEdgeCountCount = 0;
		fastPruningCount = 0;
		backwardAssignationCount = 0;
		embeddingReuseCount = 0;

		frequentSubgraphs = new ArrayList<FrequentSubgraph>();

		MemoryLogger.getInstance().reset();

		patternCount = 0;

		long t1 = System.currentTimeMillis();

		// read graphs
		List<Graph> graphDB = readGraphs(inPath);

		// calculate the minimum support as a number of graphs
		minSup = (int) Math.ceil(minSupport * graphDB.size());

		// mining
		fsp(graphDB, outputSingleVertices);

		MemoryLogger.getInstance().checkMemory();

		// output
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
	 * Generate a graphviz DOT file for each frequent subgraph found.
	 *
	 * @param outputPath the output file path used to locate the result file
	 * @throws IOException if some exception when reading/writing the files
	 */
	private static void outputDotFile(String outputPath) throws IOException {
		String dirName = outputPath + "_dotfile";
		File dir = new File(dirName);
		if (!dir.exists())
			dir.mkdir();
		VizGraph.visulizeFromFile(outputPath, dirName);
	}

	/**
	 * Translate a (possibly relabeled) vertex label back to its original label for
	 * output purposes.
	 *
	 * @param newLabel the relabeled label
	 * @return the original label, or newLabel if no mapping is found
	 */
	private int translateVLabel(int newLabel) {
		if (vertexLabelNewToOld == null) {
			return newLabel;
		}
		Integer old = vertexLabelNewToOld.get(newLabel);
		return old == null ? newLabel : old;
	}

	/**
	 * Translate a (possibly relabeled) edge label back to its original label for
	 * output purposes. The special value -1 (used for single-vertex patterns) is
	 * never translated.
	 *
	 * @param newLabel the relabeled label
	 * @return the original label, or newLabel if no mapping is found
	 */
	private int translateELabel(int newLabel) {
		if (newLabel == -1 || edgeLabelNewToOld == null) {
			return newLabel;
		}
		Integer old = edgeLabelNewToOld.get(newLabel);
		return old == null ? newLabel : old;
	}

	/**
	 * Write the result (frequent subgraphs) to an output file.
	 *
	 * @param outputPath an output file path
	 * @throws IOException if an error occurs while writing to file
	 */
	private void writeResultToFile(String outputPath) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputPath)));
		bw.write("@FILETYPE=\"Frequent subgraphs\"");
		bw.append(System.lineSeparator());
		bw.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		bw.append(System.lineSeparator());

		int i = 0;
		for (FrequentSubgraph subgraph : frequentSubgraphs) {
			StringBuilder sb = new StringBuilder();

			DFSCode dfsCode = subgraph.dfsCode;
			sb.append("t # ").append(i).append(" * ").append(subgraph.support).append(System.lineSeparator());
			if (dfsCode.size() == 1) {
				// single-edge (or single-vertex) subgraph
				ExtendedEdge ee = dfsCode.getEeL().get(0);
				if (ee.getEdgeLabel() == -1) {
					sb.append("v 0 ").append(translateVLabel(ee.getvLabel1())).append(System.lineSeparator());
				} else {
					sb.append("v 0 ").append(translateVLabel(ee.getvLabel1())).append(System.lineSeparator());
					sb.append("v 1 ").append(translateVLabel(ee.getvLabel2())).append(System.lineSeparator());
					sb.append("e 0 1 ").append(translateELabel(ee.getEdgeLabel())).append(System.lineSeparator());
				}
			} else {
				// write all vertices then all edges
				List<Integer> vLabels = dfsCode.getAllVLabels();
				for (int j = 0; j < vLabels.size(); j++) {
					sb.append("v ").append(j).append(" ").append(translateVLabel(vLabels.get(j)))
							.append(System.lineSeparator());
				}
				for (ExtendedEdge ee : dfsCode.getEeL()) {
					sb.append("e ").append(ee.getV1()).append(" ").append(ee.getV2()).append(" ")
							.append(translateELabel(ee.getEdgeLabel())).append(System.lineSeparator());
				}
			}
			if (outputGraphIds) {
				sb.append("x");
				for (int id : subgraph.setOfGraphsIDs) {
					sb.append(" ").append(id);
				}
			}
			sb.append(System.lineSeparator()).append(System.lineSeparator());

			bw.write(sb.toString());

			i++;
		}
		bw.close();
	}

	/**
	 * Read graphs from the input file.
	 *
	 * @param path the input file
	 * @return a list of input graph from the input graph database
	 * @throws IOException if error reading or writing to file
	 */
	private List<Graph> readGraphs(String path) throws IOException {
	    if (DEBUG_MODE) {
	        System.out.println("start reading graphs...");
	    }
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

	            if (line.startsWith("@")) {
	                continue;
	            }

	            String[] items = line.split(" ");

	            if (line.startsWith("v")) {
	                int vId = Integer.parseInt(items[1]);
	                int vLabel = Integer.parseInt(items[2]);
	                vMap.put(vId, new Vertex(vId, vLabel));
	            } else if (line.startsWith("e")) {
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

	    if (DEBUG_MODE) {
	        System.out.println("read successfully, totally " + graphDatabase.size() + " graphs");
	    }
	    graphCount = graphDatabase.size();
	    return graphDatabase;
	}

	/**
	 * Initial call for FSP mining (equivalent to FSP Algorithm 1, lines 4-10).
	 * This drives the preprocessing done by {@link FSPPreprocessor} and then
	 * hands off the recursive DFS-tree exploration to {@link FSPMiner}.
	 *
	 * @param graphDB                a graph database
	 * @param outputFrequentVertices if true, include frequent subgraph with a
	 *                               single vertex in the output
	 * @throws IOException            exception if error writing/reading to file
	 * @throws ClassNotFoundException if error casting a class
	 */
	private void fsp(List<Graph> graphDB, boolean outputFrequentVertices) throws IOException, ClassNotFoundException {

		// FSP Algorithm 1, lines 1-3: remove infrequent vertices/edges and relabel
		// the remaining ones by frequency order
		List<Graph> relabeledDB = preprocessor.relabelAndFilter(graphDB, outputFrequentVertices);

		for (Graph g : relabeledDB) {
			g.precalculateVertexList();
		}

		if (ELIMINATE_INFREQUENT_VERTEX_PAIRS || ELIMINATE_INFREQUENT_EDGE_LABELS) {
			preprocessor.removeInfrequentVertexPairs(relabeledDB);
		}

		if (DEBUG_MODE) {
			System.out.println("Precalculating information...");
		}

		// build the set of non-empty graphs and precalculate lookup structures
		Set<Integer> graphIds = new HashSet<Integer>();
		for (int i = 0; i < relabeledDB.size(); i++) {
			Graph g = relabeledDB.get(i);

			if (g.vertices == null || g.vertices.length != 0) {
				if (infrequentVerticesRemovedCount > 0) {
					g.precalculateVertexList();
				}

				graphIds.add(i);

				g.precalculateVertexNeighbors();
				g.precalculateLabelsToVertices();
			} else {
				if (DEBUG_MODE) {
					System.out.println("EMPTY GRAPHS REMOVED");
				}
				emptyGraphsRemoved++;
			}
		}

		if (!frequentVertexLabels.isEmpty()) {
			if (DEBUG_MODE) {
				System.out.println("Starting depth-first search (FSP)...");
			}

			// FSP Algorithm 1, lines 5-10 / Algorithm 2: Build_DFS_Tree for each frequent
			// 1-edge subgraph
			miner.fspDFS(new DFSCode(), relabeledDB, graphIds);
		}
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  FSP v.2.67 - STATS =============");
		System.out.println(" Number of graph in the input database: " + graphCount);
		System.out.println(" Frequent subgraph count : " + patternCount);
		System.out.println(" Total time ~ " + runtime + " s");
		System.out.println(" Minsup : " + minSup + " graphs");
		System.out.println(" Maximum memory usage : " + maxmemory + " mb");
		System.out.println("  -------------------");
		System.out.println(
				"  Label order used for relabeling : " + (useDescendingLabelOrder ? "descending" : "ascending"));
		System.out.println("  Number of infrequent vertex labels pruned : " + infrequentVerticesRemovedCount);
		System.out.println("  Number of infrequent edge labels pruned : " + edgeRemovedByLabel);
		System.out.println("  Number of infrequent vertex pairs pruned : " + infrequentVertexPairsRemoved);
		System.out.println("  Empty graphs removed : " + emptyGraphsRemoved);
		System.out.println("  Extensions skipped (edge count pruning) : " + pruneByEdgeCountCount);
		if (useFastPruning) {
			System.out.println("  Fast pruning (DFS balance) rejections : " + fastPruningCount);
		}
		if (useBackwardAssignation) {
			System.out.println("  Backward graph-index assignation skips : " + backwardAssignationCount);
		}
		if (useIncrementalIsomorphism) {
			System.out.println(
					"  Subgraph isomorphism recomputations avoided (embedding reuse) : " + embeddingReuseCount);
		}
		System.out.println("===================================================");
	}

	/**
	 * Get the list of frequent subgraphs found by the last execution.
	 *
	 * @return the list of frequent subgraphs
	 */
	public List<FrequentSubgraph> getFrequentSubgraphs() {
		return frequentSubgraphs;
	}
}