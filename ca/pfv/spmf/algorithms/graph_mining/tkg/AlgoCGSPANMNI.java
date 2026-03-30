package ca.pfv.spmf.algorithms.graph_mining.tkg;

import ca.pfv.spmf.tools.MemoryLogger;

import java.io.IOException;
import java.util.*;

/**
 * This file is copyright (c) 2022 by Shaul Zevin
 * <p>
 * This is implementation of the CGSPAN algorithm with closed subgraphs frequency defined by MNI (Minimum Node Image)
 * For MNI definition please check Bringmann, B, Nijssen, S. What is frequent in a single graph?. In: Proc. 12th PacificAsia Conference on Knowledge Discovery and Data Mining, Osaka, Japan, May 20-23, 2008:858–863. <br/>
 * MNI should be used when graphs database consists of a single graph.
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
 * The cgspan algorithm finds all the closed subgraphs and their MNI in a
 * graph provided by the user. <br/>
 * <br/>
 * <p>
 * This implementation saves the result to a file
 *
 * @author Shaul Zevin
 */
public class AlgoCGSPANMNI extends AlgoCGSPANAbstract {
    /**
     * the minimum number of projected nodes for a frequent node
     */
    private int minMNI;

    /**
     * Run the cgSpan algorithm
     *
     * @param inPath               the input file
     * @param outPath              the output file
     * @param minMNI               the minimum node image, minimum number of projected nodes of a frequent node
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
    public void runAlgorithm(String inPath, String outPath, int minMNI, boolean outputSingleVertices,
                             boolean outputDotFile, int maxNumberOfEdges, boolean outputGraphIds)
            throws IOException, ClassNotFoundException, InterruptedException {

        // if maximum size is 0
        if (maxNumberOfEdges <= 0) {
            return;
        }

        // Save minimum node image
        this.minMNI = minMNI;

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

        // Create early termination failure handler
        IEarlyTerminationFailureHandler earlyTerminationFailureHandler = new EarlyTerminationFailureHandlerMNI(graphDB, minMNI);

        // projections automorphism optimization is enabled by default
        pdfsAutomorphismOptimization = true;

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

        SparseTriangularMatrix matrix;
        if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
            if (DEBUG_MODE) {
                System.out.println("Calculating the pruning matrix...");
            }
            matrix = new SparseTriangularMatrix();
        }

        Map<Integer, Map<Pair, Integer>> mapEdgeLabelToPairCount;
        if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
            mapEdgeLabelToPairCount = new HashMap<Integer, Map<Pair, Integer>>();
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

                    if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
                        // count each pair once
                        if (v1.getId() < v2) {
                            matrix.incrementCount(labelV1, labelV2);
                            if (labelV1 != labelV2) {
                                matrix.incrementCount(labelV1, labelV2);
                            }
                            matrix.incrementCount(labelV2, labelV1);
                            if (labelV1 != labelV2) {
                                matrix.incrementCount(labelV2, labelV1);
                            }
                        }
                    }

                    if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
                        // count each pair once
                        if (v1.getId() < v2) {
                            // Update edge pair count
                            Pair pair = new Pair(labelV1, labelV2);
                            int edgeLabel = edge.getEdgeLabel();
                            if (mapEdgeLabelToPairCount.get(edgeLabel) == null) {
                                mapEdgeLabelToPairCount.put(edgeLabel, new HashMap<Pair, Integer>());
                            }

                            Map<Pair, Integer> mapPairCount = mapEdgeLabelToPairCount.get(edgeLabel);
                            if (mapPairCount.get(pair) == null) {
                                mapPairCount.put(pair, 0);
                            }

                            mapPairCount.put(pair, mapPairCount.get(pair) + 1);

                            if (labelV1 != labelV2) {
                                mapPairCount.put(pair, mapPairCount.get(pair) + 1);
                            }

                            pair = new Pair(labelV2, labelV1);

                            if (mapPairCount.get(pair) == null) {
                                mapPairCount.put(pair, 0);
                            }

                            mapPairCount.put(pair, mapPairCount.get(pair) + 1);

                            if (labelV1 != labelV2) {
                                mapPairCount.put(pair, mapPairCount.get(pair) + 1);
                            }
                        }
                    }
                }
            }
        }

        // REMOVE INFREQUENT ENTRIES FROM THE MATRIX
        if (ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
            if (DEBUG_MODE) {
                System.out.println("Removing infrequent pairs...  MNI = " + minMNI);
            }
            matrix.removeInfrequentEntriesFromMatrix(minMNI);
        }

        Set<Integer> infrequentEdgeLabels = new HashSet<Integer>();
        if (ELIMINATE_INFREQUENT_EDGE_LABELS) {
            for (Integer edgeLabel : mapEdgeLabelToPairCount.keySet()) {
                boolean infrequent = true;
                Map<Pair, Integer> mapPairCount = mapEdgeLabelToPairCount.get(edgeLabel);
                for (Integer count : mapPairCount.values()) {
                    if (count >= minMNI) {
                        infrequent = false;
                        break;
                    }
                }

                if (infrequent) {
                    infrequentEdgeLabels.add(edgeLabel);
                }
            }
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
                        if (ELIMINATE_INFREQUENT_VERTEX_PAIRS && count < minMNI) {
                            iter.remove();

                            infrequentVertexPairsRemoved++;
                        } else if (ELIMINATE_INFREQUENT_EDGE_LABELS
                                && infrequentEdgeLabels.contains(edge.getEdgeLabel())) {
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

                int nmi = newProjected.MNI();
                // if the nmi is enough
                if (nmi >= minMNI) {

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
                ClosedSubgraph subgraph = new ClosedSubgraph(c, graphIds, graphIds.size(), projected, projected.MNI());
                closedSubgraphs.add(subgraph);
                updateClosedSubgraphsHashTable(subgraph);
            }
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * This method finds all frequent vertex labels from a graph database.
     *
     * @param graphDB a graph database
     */
    protected void findAllOnlyOneVertex(List<DatabaseGraph> graphDB) {

        frequentVertexLabels = new ArrayList<Integer>();

        // Create a map (key = vertex label, value = graph ids)
        // to count the support of each vertex
        Map<Integer, Set<Integer>> labelM = new HashMap<>();

        // Create a map (key = vertex label, value = count of label in all graphs)
        // to count each vertex label
        Map<Integer, Integer> labelCountsM = new HashMap<>();

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

                    // Increment label count
                    Integer labelCount = labelCountsM.get(vLabel);
                    if (labelCount == null) {
                        labelCountsM.put(vLabel, 0);
                    }
                    labelCountsM.put(vLabel, labelCountsM.get(vLabel) + 1);
                }
            }
        }

        // For each vertex label
        for (Map.Entry<Integer, Set<Integer>> entry : labelM.entrySet()) {
            int label = entry.getKey();

            // if it is a frequent vertex, then record that as a frequent subgraph
            Set<Integer> tempSupG = entry.getValue();
            int labelCount = labelCountsM.get(label);

            if (labelCount >= minMNI) {
                frequentVertexLabels.add(label);
            } else if (ELIMINATE_INFREQUENT_VERTICES) {
                // for each graph
                for (Integer graphid : tempSupG) {
                    Graph g = graphDB.get(graphid);

                    g.removeInfrequentLabel(label);
                    infrequentVerticesRemovedCount += labelCountsM.get(label);
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
            int mni = labelCountM.get(label);
            if (mni >= minMNI) {
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

                    closedSubgraphs.add(new ClosedSubgraph(tempD, tempSupG, tempSupG.size(), new ProjectedCompact(tempD, graphDB),mni));
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
        System.out.println(" MinMNI : " + minMNI + " nodes");
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
