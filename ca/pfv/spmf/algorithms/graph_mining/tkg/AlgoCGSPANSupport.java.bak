package ca.pfv.spmf.algorithms.graph_mining.tkg;

import ca.pfv.spmf.tools.MemoryLogger;

import java.io.IOException;
import java.util.*;

/**
 * This file is copyright (c) 2022 by Shaul Zevin
 * <p>
 * This is implementation of the CGSPAN algorithm with closed subgraphs frequency defined by support - percentage of number of graphs in the database in which closed graph must be found<br/>
 * <br/>
 * <p>
 * The cgspan algorithm is described in : <br/>
 * <br/>
 * <p>
 * cgSpan: Closed Graph-Based Substructure Pattern Mining, by Zevin Shaul, Sheikh Naaz
 * IEEE BigData 2021 7th Special Session on Intelligent Data Mining
 * <br/>
 * <br/>
 * <p>
 * The cgspan algorithm finds all the closed subgraphs and their support in a
 * graph provided by the user. <br/>
 * <br/>
 * <p>
 * This implementation saves the result to a file
 *
 * @author Shaul Zevin
 */
public class AlgoCGSPANSupport extends AlgoCGSPANAbstract {
    /**
     * the minimum support represented as a count (number of subgraph occurrences)
     */
    private int minSup;

    /**
     * Run the cgSpan algorithm
     *
     * @param inPath               the input file
     * @param outPath              the output file
     * @param minSupport           a minimum support value (a percentage represented
     *                             by a value between 0 and 1)
     * @param outputSingleVertices if true, closed subgraphs containing a single
     *                             vertex will be output
     * @param outputDotFile        if true, a graphviz DOT file will be generated to
     *                             visualize the patterns
     * @param maxNumberOfEdges     an integer indicating a maximum number of edges
     *                             for each frequent subgraph
     * @param outputGraphIds       Output the ids of graph containing each frequent
     *                             subgraph
     * @throws IOException            if error while writing to file
     * @throws ClassNotFoundException
     */
    public void runAlgorithm(String inPath, String outPath, double minSupport, boolean outputSingleVertices,
                             boolean outputDotFile, int maxNumberOfEdges, boolean outputGraphIds)
            throws IOException, ClassNotFoundException, InterruptedException {

        // if maximum size is 0
        if (maxNumberOfEdges <= 0) {
            return;
        }

        /**
         * the minimum support threshold as a percentage represented by a value between
         * 0 and 1
         */
        double minFrequency = minSupport;

        // Save the maximum number of edges
        this.maxNumberOfEdges = maxNumberOfEdges;

        // Save parameter
        this.outputGraphIds = outputGraphIds;

        // initialize variables for statistics
        infrequentVertexPairsRemoved = 0;
        infrequentVerticesRemovedCount = 0;
        edgeRemovedByLabel = 0;
        eliminatedWithMaxSize = 0;
        emptyGraphsRemoved = 0;
        pruneByEdgeCountCount = 0;
        earlyTerminationAppliedCount = 0;
        earlyTerminationFailureDetectedCount = 0;

        // initialize structure to store results
        closedSubgraphs = new ArrayList<ClosedSubgraph>();

        // Initialize the tool to check memory usage
        MemoryLogger.getInstance().reset();

        // reset the number of patterns found
        patternCount = 0;

        // Record the start time
        Long t1 = System.currentTimeMillis();

        // read graphs
        List<DatabaseGraph> graphDB = readGraphs(inPath);

        // Calculate the minimum support as a number of graphs
        minSup = (int) Math.ceil(minFrequency * graphDB.size());

        // Create early termination failure handler
        IEarlyTerminationFailureHandler earlyTerminationFailureHandler = new EarlyTerminationFailureHandlerSupport(graphDB, minSup);

        // projections automorphism optimization is disabled by default
        pdfsAutomorphismOptimization = false;

        if (!DEBUG_MODE) {
            outputProjections = false;
        }

        // mining
        cgSpan(graphDB, outputSingleVertices, earlyTerminationFailureHandler);

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();

        // output
        writeResultToFile(outPath);

        Long t2 = System.currentTimeMillis();

        runtime = (t2 - t1) / 1000;

        maxmemory = MemoryLogger.getInstance().getMaxMemory();

        patternCount = closedSubgraphs.size();

        if (outputDotFile) {
            outputDotFile(outPath);
        }

        ThreadPool.shutdown();
    }

    /**
     * Create the pruning matrix
     */
    protected void removeInfrequentVertexPairs(List<DatabaseGraph> graphDB) {

        Set<Pair> alreadySeenPair;
        SparseTriangularMatrix matrix;
        if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
            if (DEBUG_MODE) {
                System.out.println("Calculating the pruning matrix...");
            }
            matrix = new SparseTriangularMatrix();
            alreadySeenPair = new HashSet<Pair>();
        }

        Set<Integer> alreadySeenEdgeLabel;
        Map<Integer, Integer> mapEdgeLabelToSupport;
        if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
            mapEdgeLabelToSupport = new HashMap<Integer, Integer>();
            alreadySeenEdgeLabel = new HashSet<Integer>();
        }

        // CALCULATE THE SUPPORT OF EACH ENTRY
        for (DatabaseGraph g : graphDB) {
            Vertex[] vertices = g.getAllVertices();

            for (int i = 0; i < vertices.length; i++) {
                Vertex v1 = vertices[i];
                int labelV1 = v1.getLabel();

                for (Edge edge : v1.getEdgeList()) {
                    int v2 = edge.another(v1.getId());
                    int labelV2 = g.getVLabel(v2);

                    if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
                        // Update vertex pair count
                        Pair pair = new Pair(labelV1, labelV2);
                        boolean seen = alreadySeenPair.contains(pair);
                        if (!seen) {
                            matrix.incrementCount(labelV1, labelV2);
                            alreadySeenPair.add(pair);
                        }
                    }

                    if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
                        // Update edge label count
                        int edgeLabel = edge.getEdgeLabel();
                        if (!alreadySeenEdgeLabel.contains(edgeLabel)) {
                            alreadySeenEdgeLabel.add(edgeLabel);

                            Integer edgeSupport = mapEdgeLabelToSupport.get(edgeLabel);
                            if (edgeSupport == null) {
                                mapEdgeLabelToSupport.put(edgeLabel, 1);
                            } else {
                                mapEdgeLabelToSupport.put(edgeLabel, edgeSupport + 1);
                            }
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

        alreadySeenPair = null;

        // REMOVE INFREQUENT ENTRIES FROM THE MATRIX
        if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
            if (DEBUG_MODE) {
                System.out.println("Removing infrequent pairs...  minsup = " + minSup);
            }
            matrix.removeInfrequentEntriesFromMatrix(minSup);
        }

        // REMOVE INFREQUENT EDGES
        if (ELIMINATE_INFREQUENT_VERTEX_PAIRS || ELIMINATE_INFREQUENT_EDGE_LABELS) {
            // CALCULATE THE SUPPORT OF EACH ENTRY
            for (DatabaseGraph g : graphDB) {
                Vertex[] vertices = g.getAllVertices();

                for (int i = 0; i < vertices.length; i++) {
                    Vertex v1 = vertices[i];
                    int labelV1 = v1.getLabel();

                    Iterator<Edge> iter = v1.getEdgeList().iterator();
                    while (iter.hasNext()) {
                        Edge edge = (Edge) iter.next();
                        int v2 = edge.another(v1.getId());
                        int labelV2 = g.getVLabel(v2);

                        int count = matrix.getSupportForItems(labelV1, labelV2);
                        if (ELIMINATE_INFREQUENT_VERTEX_PAIRS && count < minSup) {
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
     * Recursive method to perform the depth-first search
     *
     * @param c                              the current DFS code
     * @param graphDB                        the graph database
     * @param graphIds                       the ids of graph where the graph "c" appears
     * @param projected                      projections of the current DFS code into database graphs
     * @param earlyTerminationFailureHandler early termination failure detector
     * @throws IOException            exception if error writing/reading to file
     * @throws ClassNotFoundException if error casting a class
     */
    protected void cgSpanDFS(DFSCode c, List<DatabaseGraph> graphDB, Set<Integer> graphIds, ProjectedCompact projected, IEarlyTerminationFailureHandler earlyTerminationFailureHandler)
            throws IOException, ClassNotFoundException, InterruptedException {
        // If we have reached the maximum size, we do not need to extend this graph
        if (c.size() == maxNumberOfEdges - 1) {
            return;
        }

        // check if DFS tree should not be further extended
        EarlyTerminationResult earlyTerminationResult = earlyTermination(graphIds, projected, earlyTerminationFailureHandler);
        if (earlyTerminationResult.isEarlyTerminationFailure()) {
            earlyTerminationFailureDetectedCount++;
        }
        if (earlyTerminationResult.isEarlyTermination()) {
            // terminate further DFS tree extension
            earlyTerminationAppliedCount++;
            return;
        }

        // Find all the extensions of this graph, with their support values
        // They are stored in a map where the key is an extended edge, and the value is
        // the
        // is the list of graph ids where this edge extends the current subgraph c.
        Map<ExtendedEdge, ProjectedCompact> extensions = rightMostPathExtensions(c, graphDB, projected);

        // For each extension
        if (extensions != null) {
            // sort extension by lexicographical order
            List<ExtendedEdge> orderedExtensions = new ArrayList<ExtendedEdge>(extensions.keySet());
            Collections.sort(orderedExtensions, new ExtendedEdgeLexicographicalComparator());

            for (ExtendedEdge extension : orderedExtensions) {
                ProjectedCompact newProjected = extensions.get(extension);
                // Get the support
                Set<Integer> newGraphIDs = newProjected.getGraphIds();
                int sup = newGraphIDs.size();

                // if the support is enough
                if (sup >= minSup) {

                    // Create the new DFS code of this graph
                    DFSCode newC = newProjected.getDfsCode();

                    // if the resulting graph is canonical (it means that the graph is non
                    // redundant)
                    if (isCanonical(newC)) {
                        // Try to extend this graph to generate larger frequent subgraphs
                        cgSpanDFS(newC, graphDB, newGraphIDs, newProjected, earlyTerminationFailureHandler);
                    }
                }
            }
        }

        if (c.size() > 0) {
            // analyze current DFS code for early termination failure conditions
            if (detectEarlyTerminationFailure) {
                earlyTerminationFailureHandler.analyze(c, projected, extensions);
            }

            // if early termination failure was detected, the current DFS code has equivalent occurrence with some closed subgraph
            // and therefore is not a closed graph
            if (earlyTerminationResult.isEarlyTerminationFailure()) {
                return;
            }

            // check if one of the extensions has equivalent occurrence with current DFS code
            boolean hasEquivalentOccurrence = false;
            if (extensions != null) {
                for (ProjectedCompact extendedProjected : extensions.values()) {
                    if (extendedProjected.isExtendedEquivalentOccurrence()) {
                        hasEquivalentOccurrence = true;
                        break;
                    }
                }
            }

            if (!hasEquivalentOccurrence) {
                // Save the graph
                ClosedSubgraph subgraph = new ClosedSubgraph(c, graphIds, graphIds.size(), projected,graphIds.size());
                closedSubgraphs.add(subgraph);
                System.out.println("closed subgraph " + closedSubgraphs.size() + " added");
                updateClosedSubgraphsHashTable(subgraph);
            }
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * This method finds all frequent vertex labels from a graph database.
     *
     * @param graphDB                a graph database
     */
    protected void findAllOnlyOneVertex(List<DatabaseGraph> graphDB) {

        frequentVertexLabels = new ArrayList<Integer>();

        // Create a map (key = vertex label, value = graph ids)
        // to count the support of each vertex
        Map<Integer, Set<Integer>> labelM = new HashMap<>();

        // For each graph
        for (DatabaseGraph g : graphDB) {
            // For each vertex
            for (Vertex v : g.getNonPrecalculatedAllVertices()) {

                // if it has some edges
                if (!v.getEdgeList().isEmpty()) {

                    // Get the vertex label
                    Integer vLabel = v.getLabel();

                    // Store the graph id in the map entry for this label
                    // if it is not there already
                    Set<Integer> set = labelM.get(vLabel);
                    if (set == null) {
                        set = new HashSet<>();
                        labelM.put(vLabel, set);
                    }
                    set.add(g.getId());
                }
            }
        }

        // For each vertex label
        for (Map.Entry<Integer, Set<Integer>> entry : labelM.entrySet()) {
            int label = entry.getKey();

            // if it is a frequent vertex, then record that as a frequent subgraph
            Set<Integer> tempSupG = entry.getValue();
            int sup = tempSupG.size();
            if (sup >= minSup) {
                frequentVertexLabels.add(label);
            } else if (ELIMINATE_INFREQUENT_VERTICES) {
                // for each graph
                for (Integer graphid : tempSupG) {
                    Graph g = graphDB.get(graphid);

                    g.removeInfrequentLabel(label);
                    infrequentVerticesRemovedCount++;
                }
            }
        }
    }

    /**
     * This method outputs vertices that do not have equivalent occurrence with any closed subgraph.
     *
     * @param graphDB                a graph database
     * @param projected              empty projections
     */
    protected void outputClosedOneVertex(List<DatabaseGraph> graphDB, ProjectedCompact projected) throws IOException, ClassNotFoundException, InterruptedException {
        // Create a map (key = vertex label, value = graph ids)
        // to count the support of each vertex
        Map<Integer, Set<Integer>> labelM = new HashMap<>();
        // count vertices with label in graphs database
        labelCountM = new HashMap<Integer, Integer>();
        // count vertices with label for each graph in graphs database
        labelInGraphCountM = new HashMap<Integer, Map<Integer, Integer>>();

        Set<Integer> gids = projected.getGraphIds();

        // For each graph
        for (DatabaseGraph g : graphDB) {
            if (!gids.contains(g.getId())) {
                continue;
            }
            // For each vertex
            for (Vertex v : g.getAllVertices()) {

                // if it has some edges
                if (!v.getEdgeList().isEmpty()) {

                    // Get the vertex label
                    Integer vLabel = v.getLabel();

                    // Store the graph id in the map entry for this label
                    // if it is not there already
                    Set<Integer> set = labelM.get(vLabel);
                    if (set == null) {
                        set = new HashSet<>();
                        labelM.put(vLabel, set);
                    }
                    set.add(g.getId());

                    // increase label count
                    if (!labelCountM.containsKey(vLabel)) {
                        labelCountM.put(vLabel, 0);
                    }
                    labelCountM.put(vLabel, labelCountM.get(vLabel) + 1);

                    if (!labelInGraphCountM.containsKey(g.getId())) {
                        labelInGraphCountM.put(g.getId(), new HashMap<Integer, Integer>());
                    }

                    if (!labelInGraphCountM.get(g.getId()).containsKey(vLabel)) {
                        labelInGraphCountM.get(g.getId()).put(vLabel, 0);
                    }

                    labelInGraphCountM.get(g.getId()).put(vLabel, labelInGraphCountM.get(g.getId()).get(vLabel) + 1);
                }
            }
        }

        Map<ExtendedEdge, ProjectedCompact> extensions = rightMostPathExtensions(new DFSCode(), graphDB, projected);

        // For each vertex label
        for (Map.Entry<Integer, Set<Integer>> entry : labelM.entrySet()) {
            int label = entry.getKey();

            // if it is a frequent vertex, then record that as a frequent subgraph
            Set<Integer> tempSupG = entry.getValue();
            int sup = tempSupG.size();
            if (sup >= minSup) {
                boolean output = true;

                int labelCount = labelCountM.get(label);

                for (ExtendedEdge extendedEdge : extensions.keySet()) {
                    if (extendedEdge.vLabel1 != label && extendedEdge.vLabel2 != label) {
                        continue;
                    }
                    ProjectedCompact extensionProjected = extensions.get(extendedEdge);
                    int labelCountInProjections = extensionProjected.verticesWithLabelCount(label, graphDB);
                    if (labelCountInProjections == labelCount) {
                        output = false;
                        break;
                    }
                }

                if (output) {
                    DFSCode tempD = new DFSCode();
                    tempD.add(new ExtendedEdge(0, 0, label, label, -1));

                    closedSubgraphs.add(new ClosedSubgraph(tempD, tempSupG, sup, new ProjectedCompact(tempD, graphDB), sup));
                    System.out.println("closed graph " + closedSubgraphs.size() + " added");
                }
            }
        }
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  CGSPAN v2.53 - STATS =============");
        System.out.println(" Number of graph in the input database: " + graphCount);
        System.out.println(" Frequent subgraph count : " + patternCount);
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
            if (EDGE_COUNT_PRUNING) {
                System.out.println("  Extensions skipped (edge count pruning) : " + pruneByEdgeCountCount);
            }
            if (SKIP_STRATEGY) {
                System.out.println("  Skip strategy count : " + skipStrategyCount);
            }
            System.out.println("early termination was applied " + earlyTerminationAppliedCount + " times");
            System.out.println("early termination failure was detected " + earlyTerminationFailureDetectedCount + " times");
        }
        System.out.println("===================================================");
    }

}
