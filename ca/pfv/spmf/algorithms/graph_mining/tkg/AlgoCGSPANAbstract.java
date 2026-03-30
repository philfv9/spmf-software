package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * This file is copyright (c) 2022 by Shaul Zevin
 * <p>
 * This is the base class of the CGSPAN algorithm implementation<br/>
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
public abstract class AlgoCGSPANAbstract {
    /**
     * The list of closed subgraphs found by the last execution
     */
    protected List<ClosedSubgraph> closedSubgraphs;

    /**
     * runtime of the most recent execution
     */
    protected long runtime = 0;

    /**
     * runtime of the most recent execution
     */
    protected double maxmemory = 0;

    /**
     * pattern count of the most recent execution
     */
    protected int patternCount = 0;

    /**
     * number of graph in the input database
     */
    protected int graphCount = 0;

    /**
     * frequent vertex labels
     */
    protected List<Integer> frequentVertexLabels;

    /**
     * if true, debug mode is activated
     */
    protected boolean DEBUG_MODE = false;

    /**
     * eliminate infrequent labels from graphs
     */
    protected static final boolean ELIMINATE_INFREQUENT_VERTICES = true;  // strategy in Gspan paper

    /**
     * eliminate infrequent vertex pairs from graphs
     */
    protected static final boolean ELIMINATE_INFREQUENT_VERTEX_PAIRS = true;

    /**
     * eliminate infrequent labels from graphs
     */
    protected static final boolean ELIMINATE_INFREQUENT_EDGE_LABELS = true;  // strategy in Gspan paper

    /**
     * apply edge count pruning strategy
     */
    protected static final boolean EDGE_COUNT_PRUNING = true;

    /**
     * skip strategy
     */
    protected static final boolean SKIP_STRATEGY = false;

    /**
     * infrequent edges removed
     */
    protected int infrequentVertexPairsRemoved;

    /**
     * infrequent edges removed
     */
    protected int infrequentVerticesRemovedCount;

    /**
     * remove infrequent edge labels
     */
    protected int edgeRemovedByLabel;

    /**
     * remove infrequent edge labels
     */
    protected int eliminatedWithMaxSize;

    /**
     * empty graph removed count
     */
    protected int emptyGraphsRemoved;

    /**
     * empty graph removed by edge count pruning
     */
    protected Integer pruneByEdgeCountCount;

    /**
     * skip strategy count
     */
    protected int skipStrategyCount;

    /**
     * maximum number of edges in each closed subgraph
     */
    protected int maxNumberOfEdges = Integer.MAX_VALUE;

    /**
     * Output the ids of graph containing each closed subgraph
     */
    protected boolean outputGraphIds = true;

    /**
     * activates early termination failure analysis and detection
     */
    protected boolean detectEarlyTerminationFailure = true;

    /**
     * counts number of times early termination was applied
     */
    protected int earlyTerminationAppliedCount;

    /**
     * counts number of times early termination failure was detected
     */
    protected int earlyTerminationFailureDetectedCount;

    /**
     * activates projected automorphism optimization
     */
    protected boolean pdfsAutomorphismOptimization = false;

    /**
     * discovered closed subgraphs hash table
     */
    protected Map<Set<EdgeEnumeration>, List<ClosedSubgraph>> closedSubgraphsHashTable = new HashMap<Set<EdgeEnumeration>, List<ClosedSubgraph>>();

    /**
     * counts of vertices labels in database graphs, used only for closed one vertex subgraphs discovery and output
     */
    protected HashMap<Integer, Integer> labelCountM;

    /**
     * counts of vertices labels for each graph in database graphs, used only for closed one vertex subgraphs output
     */
    protected Map<Integer, Map<Integer, Integer>> labelInGraphCountM;

    /**
     * output closed subgraph that can be extended to another closed subgraph using more than one isomorphism.
     */
    protected boolean outputExtendableByMultipleIsomorphisms = true;

    /**
     * output number of projections in each database graph
     */
    protected boolean outputProjections = true;

    // for debugging only, monitors progress by counting number of rightmost extended projections
    protected long rightMostExtendedProjectionsCount = 0;

    /**
     * Output the DOT files to a given file path
     *
     * @param outputPath the output file path
     * @throws IOException if some exception when reading/writing the files
     */
    protected static void outputDotFile(String outputPath) throws IOException {
        String dirName = outputPath + "_dotfile";
        File dir = new File(dirName);
        if (!dir.exists())
            dir.mkdir();
        VizGraph.visulizeFromFile(outputPath, dirName);
    }

    /**
     * Write the result to an output file
     *
     * @param outputPath an output file path
     **/
    protected void writeResultToFile(String outputPath) throws IOException {
        // Create the output file
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputPath)));

        closedSubgraphs.sort((ClosedSubgraph z1, ClosedSubgraph z2) -> (Integer.compare(z1.support, z2.support)));

        if (!outputExtendableByMultipleIsomorphisms) {
            closedSubgraphs = closedSubgraphs.stream()
                    .filter(g -> !isExtendableWithMultipleIsomorphisms(g)).collect(Collectors.toList());
        }

        // For each frequent subgraph
        int i = 0;
        for (ClosedSubgraph subgraph : closedSubgraphs) {
            StringBuilder sb = new StringBuilder();

            DFSCode dfsCode = subgraph.dfsCode;
            if (dfsCode.size() == 1) {
                ExtendedEdge ee = dfsCode.getEeL().get(0);
                if (ee.getEdgeLabel() == -1) {
                    sb.append("t # ").append(i).append(" * ").append(subgraph.getThresholdValue());
                    if (outputProjections) {
                        sb.append(" * ").append(labelCountM.get(ee.getvLabel1()));
                    }
                    sb.append(System.lineSeparator());
                    sb.append("v 0 ").append(ee.getvLabel1()).append(System.lineSeparator());
                } else {
                    sb.append("t # ").append(i).append(" * ").append(subgraph.getThresholdValue());
                    if (outputProjections) {
                        sb.append(" * ").append(subgraph.getProjected().getNumProjections());
                    }
                    sb.append(System.lineSeparator());
                    sb.append("v 0 ").append(ee.getvLabel1()).append(System.lineSeparator());
                    sb.append("v 1 ").append(ee.getvLabel2()).append(System.lineSeparator());
                    sb.append("e 0 1 ").append(ee.getEdgeLabel()).append(System.lineSeparator());
                }
            } else {
                sb.append("t # ").append(i).append(" * ").append(subgraph.getThresholdValue());
                if (outputProjections) {
                    sb.append(" * ").append(subgraph.getProjected().getNumProjections());
                }
                sb.append(System.lineSeparator());
                List<Integer> vLabels = dfsCode.getAllVLabels();
                for (int j = 0; j < vLabels.size(); j++) {
                    sb.append("v ").append(j).append(" ").append(vLabels.get(j)).append(System.lineSeparator());
                }
                for (ExtendedEdge ee : dfsCode.getEeL()) {
                    int startV = ee.getV1();
                    int endV = ee.getV2();
                    int eL = ee.edgeLabel;
                    sb.append("e ").append(startV).append(" ").append(endV).append(" ").append(eL)
                            .append(System.lineSeparator());
                }
            }
            // If the user choose to output the graph ids where the frequent subgraph
            // appears
            // We output it
            if (outputGraphIds) {
                if (dfsCode.size() > 1 || (dfsCode.size() == 1 && dfsCode.getEeL().get(0).getEdgeLabel() != -1)) {
                    List<Integer> sortedGraphIds = new ArrayList(subgraph.getProjected().getGraphIds());
                    sortedGraphIds.sort(Integer::compare);
                    sb.append("x");
                    for (int id : sortedGraphIds) {
                        sb.append(" ").append(id);
                        if (outputProjections) {
                            sb.append('x').append(subgraph.getProjected().getNumProjectionsInGraph().get(id));
                        }
                    }
                } else {
                    List<Integer> sortedGraphIds = new ArrayList(labelInGraphCountM.keySet());
                    sortedGraphIds.sort(Integer::compare);
                    sb.append("x");
                    for (int id : sortedGraphIds) {
                        Integer count = labelInGraphCountM.get(id).get(dfsCode.getEeL().get(0).vLabel1);
                        if (count != null && count > 0) {
                            sb.append(" ").append(id);
                            if (outputProjections) {
                                sb.append('x').append(count);
                            }
                        }
                    }
                }
            }
            sb.append(System.lineSeparator()).append(System.lineSeparator());

            bw.write(sb.toString());

            i++;
        }
        bw.flush();
        bw.close();
    }

    /**
     * Read graph from the input file
     *
     * @param path the input file
     * @return a list of input graph from the input graph database
     * @throws IOException if error reading or writing to file
     */
    protected List<DatabaseGraph> readGraphs(String path) throws IOException {
        if (DEBUG_MODE) {
            System.out.println("start reading graphs...");
        }
        BufferedReader br = new BufferedReader(new FileReader(new File(path)));
        List<DatabaseGraph> graphDatabase = new ArrayList<>();

        String line = br.readLine();
        Boolean hasNextGraph = (line != null) && line.startsWith("t");

        // For each graph of the graph database
        while (hasNextGraph) {
            hasNextGraph = false;
            int gId = Integer.parseInt(line.split(" ")[2]);
            Map<Integer, Vertex> vMap = new HashMap<>();
            while ((line = br.readLine()) != null && !line.startsWith("t")) {

                String[] items = line.split(" ");

                if (line.startsWith("v")) {
                    // If it is a vertex
                    int vId = Integer.parseInt(items[1]);
                    int vLabel = Integer.parseInt(items[2]);
                    vMap.put(vId, new Vertex(vId, vLabel));
                } else if (line.startsWith("e")) {
                    // If it is an edge
                    int v1 = Integer.parseInt(items[1]);
                    int v2 = Integer.parseInt(items[2]);
                    int eLabel = Integer.parseInt(items[3]);
                    // the edge is constructed from smaller to bigger vertex
                    if (v1 < v2) {
                        Edge e = new Edge(v1, v2, eLabel);
                        vMap.get(v1).addEdge(e);
                        vMap.get(v2).addEdge(e);
                    } else {
                        Edge e = new Edge(v2, v1, eLabel);
                        vMap.get(v1).addEdge(e);
                        vMap.get(v2).addEdge(e);
                    }
                }
            }
            graphDatabase.add(new DatabaseGraph(gId, vMap));
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
     * Find all isomorphisms between graph described by c and graph g each
     * isomorphism is represented by a map
     *
     * @param c a dfs code representing a subgraph
     * @param g a graph
     * @return the list of all isomorphisms
     */
    protected List<Map<Integer, Integer>> subgraphIsomorphisms(DFSCode c, Graph g) {

        List<Map<Integer, Integer>> isoms = new ArrayList<>();

        // initial isomorphisms by finding all vertices with same label as vertex 0 in C
        int startLabel = c.getEeL().get(0).getvLabel1(); // only non-empty DFSCode will be real parameter
        for (int vID : g.findAllWithLabel(startLabel)) {
            Map<Integer, Integer> map = new HashMap<>();
            map.put(0, vID);
            isoms.add(map);
        }

        // each extended edge will update partial isomorphisms
        // for forward edge, each isomorphism will be either extended or discarded
        // for backward edge, each isomorphism will be either unchanged or discarded
        for (ExtendedEdge ee : c.getEeL()) {
            int v1 = ee.getV1();
            int v2 = ee.getV2();
            int v2Label = ee.getvLabel2();
            int eLabel = ee.getEdgeLabel();

            List<Map<Integer, Integer>> updateIsoms = new ArrayList<>();
            // For each isomorphism
            for (Map<Integer, Integer> iso : isoms) {

                // Get the vertex corresponding to v1 in the current edge
                int mappedV1 = iso.get(v1);

                // If it is a forward edge extension
                if (v1 < v2) {
                    Collection<Integer> mappedVertices = iso.values();

                    // For each neighbor of the vertex corresponding to V1
                    for (Vertex mappedV2 : g.getAllNeighbors(mappedV1)) {

                        // If the neighbor has the same label as V2 and is not already mapped and the
                        // edge label is
                        // the same as that between v1 and v2.
                        if (v2Label == mappedV2.getLabel() && (!mappedVertices.contains(mappedV2.getId()))
                                && eLabel == g.getEdgeLabel(mappedV1, mappedV2.getId())) {

                            // TODO: PHILIPPE: getEdgeLabel() in the above line could be precalculated in
                            // Graph.java ...

                            // because there may exist multiple extensions, need to copy original partial
                            // isomorphism
                            HashMap<Integer, Integer> tempM = new HashMap<>(iso.size() + 1);
                            tempM.putAll(iso);
                            tempM.put(v2, mappedV2.getId());

                            updateIsoms.add(tempM);
                        }
                    }
                } else {
                    // If it is a backward edge extension
                    // v2 has been visited, only require mappedV1 and mappedV2 are connected in g
                    int mappedV2 = iso.get(v2);
                    if (g.isNeighboring(mappedV1, mappedV2) && eLabel == g.getEdgeLabel(mappedV1, mappedV2)) {
                        updateIsoms.add(iso);
                    }
                }
            }
            isoms = updateIsoms;
        }

        // Return the isomorphisms
        return isoms;
    }

    protected Map<ExtendedEdge, Set<Integer>> rightMostPathExtensionsFromSingle(DFSCode c, Graph g) {
        int gid = g.getId();

        // Map of extended edges to graph ids
        Map<ExtendedEdge, Set<Integer>> extensions = new HashMap<>();

        if (c.isEmpty()) {
            // IF WE HAVE AN EMPTY SUBGRAPH THAT WE WANT TO EXTEND

            // find all distinct label tuples
            for (Vertex vertex : g.vertices) {
                for (Edge e : vertex.getEdgeList()) {
                    int v1L = g.getVLabel(e.v1);
                    int v2L = g.getVLabel(e.v2);
                    ExtendedEdge ee1;
                    if (v1L < v2L) {
                        ee1 = new ExtendedEdge(0, 1, v1L, v2L, e.getEdgeLabel());
                    } else {
                        ee1 = new ExtendedEdge(0, 1, v2L, v1L, e.getEdgeLabel());
                    }

                    // Update the set of graph ids for this pattern
                    Set<Integer> setOfGraphIDs = extensions.get(ee1);
                    if (setOfGraphIDs == null) {
                        setOfGraphIDs = new HashSet<>();
                        extensions.put(ee1, setOfGraphIDs);
                    }
                    setOfGraphIDs.add(gid);
                }
            }
        } else {
            // IF WE WANT TO EXTEND A SUBGRAPH
            int rightMost = c.getRightMost();

            // Find all isomorphisms of the DFS code "c" in graph "g"
            List<Map<Integer, Integer>> isoms = subgraphIsomorphisms(c, g);

            // For each isomorphism
            for (Map<Integer, Integer> isom : isoms) {

                // backward extensions from rightmost child
                Map<Integer, Integer> invertedISOM = new HashMap<>();
                for (Map.Entry<Integer, Integer> entry : isom.entrySet()) {
                    invertedISOM.put(entry.getValue(), entry.getKey());
                }
                int mappedRM = isom.get(rightMost);
                int mappedRMlabel = g.getVLabel(mappedRM);
                for (Vertex x : g.getAllNeighbors(mappedRM)) {
                    Integer invertedX = invertedISOM.get(x.getId());
                    if (invertedX != null && c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
                            && !c.containEdge(rightMost, invertedX)) {
                        // rightmost and invertedX both have correspondings in g, so label of vertices
                        // and edge all
                        // can be found by correspondings
                        ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
                                g.getEdgeLabel(mappedRM, x.getId()));
                        if (extensions.get(ee) == null)
                            extensions.put(ee, new HashSet<>());
                        extensions.get(ee).add(g.getId());
                    }
                }
                // forward extensions from nodes on rightmost path
                Collection<Integer> mappedVertices = isom.values();
                for (int v : c.getRightMostPath()) {
                    int mappedV = isom.get(v);
                    int mappedVlabel = g.getVLabel(mappedV);
                    for (Vertex x : g.getAllNeighbors(mappedV)) {
                        if (!mappedVertices.contains(x.getId())) {
                            ExtendedEdge ee = new ExtendedEdge(v, rightMost + 1, mappedVlabel, x.getLabel(),
                                    g.getEdgeLabel(mappedV, x.getId()));
                            if (extensions.get(ee) == null)
                                extensions.put(ee, new HashSet<>());
                            extensions.get(ee).add(g.getId());
                        }
                    }
                }
            }
        }
        return extensions;
    }

    protected Map<ExtendedEdge, ProjectedCompact> rightMostPathExtensions(DFSCode c, List<DatabaseGraph> graphDatabase,
                                                                          ProjectedCompact projected) throws IOException, ClassNotFoundException, InterruptedException {
        Set<Integer> graphIds = projected.getGraphIds();

        // rightmost extensions, extension is the key, extension projections into graph database is the value
        Map<ExtendedEdge, ProjectedCompact> extensions = new ConcurrentHashMap<>();

        // for debug only
        rightMostExtendedProjectionsCount = 0;

        // if the DFS code is empty (WE START FROM AN EMPTY GRAPH)
        if (c.isEmpty()) {

            // For each graph
//            int highestSupport = 0;
//        	int remaininggraphCount = graphIds.size();
            for (Integer graphId : graphIds) {
                DatabaseGraph g = graphDatabase.get(graphId);

                if (EDGE_COUNT_PRUNING && c.size() >= g.getEdgeCount()) {
                    pruneByEdgeCountCount++;
                    continue;
                }

                // find all distinct label tuples
                for (Vertex vertex : g.vertices) {
                    for (Edge e : vertex.getEdgeList()) {
                        int v1L = g.getVLabel(e.v1);
                        int v2L = g.getVLabel(e.v2);

                        // if vertices have different labels, use the edge only once
                        if (v1L != v2L && vertex.getId() != e.v1) {
                            continue;
                        }

                        ExtendedEdge ee1;
                        if (v1L < v2L) {
                            ee1 = new ExtendedEdge(0, 1, v1L, v2L, e.getEdgeLabel());
                        } else {
                            ee1 = new ExtendedEdge(0, 1, v2L, v1L, e.getEdgeLabel());
                        }

                        // Update the set of graph ids for this pattern
                        ProjectedCompact extensionProjected = extensions.get(ee1);
                        if (extensionProjected == null) {
                            DFSCode newC = c.copy();
                            newC.add(ee1);
                            extensionProjected = new ProjectedCompact(newC, graphDatabase);
                            extensionProjected.setGraphIds(new HashSet<>());
                            extensions.put(ee1, extensionProjected);
                        }

                        EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
                        boolean isReversed = (v1L < v2L ? false : (v2L < v1L ? true : vertex.getId() != e.v1));
                        ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);
                        extensionProjected.addProjection(projectedEdge);
                    }
                }
//            	remaininggraphCount--;
//            	if(SKIP_STRATEGY && (highestSupport + remaininggraphCount  < minSup)){
////            		System.out.println("BREAK");
//            		skipStrategyCount++;
//            		break;
//            	}
            }
        } else {
            // IF THE DFS CODE IS NOT EMPTY (WE WANT TO EXTEND SOME EXISTING GRAPH)
            int remaininggraphCount = graphIds.size();
            int highestSupport = 0;

            int rightMost = c.getRightMost();

            // count the number of distinct projections extended by the rightmost extension
            Map<ExtendedEdge, Integer> extensionPDFSCounter = new ConcurrentHashMap<ExtendedEdge, Integer>();

            List<IProjectedIteratorCallback> callbacks = new LinkedList<IProjectedIteratorCallback>();
            if (pdfsAutomorphismOptimization) {
                LocalAutomorphismCallback localAutomorphismCallback = new LocalAutomorphismCallback(projected);
                callbacks.add(localAutomorphismCallback);
            }

            // start projections producer threads
            ProjectedIteratorConsumer iterator = projected.iterator(ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() * 10, ThreadPool.getProjectedIteratorProducersInstance().getThreadCount(), callbacks);

            // start rightmost extender threads
            ThreadPool threadPool = ThreadPool.getRightMostExtenderInstance();
            /*
            List<RightPathExtenderSynchronized> extenders = new LinkedList<RightPathExtenderSynchronized>();
            for (int i = 0; i < threadPool.getThreadCount(); i++) {
                RightPathExtenderSynchronized rightPathExtender = new RightPathExtenderSynchronized(extensionPDFSCounter, iterator, this, c, graphDatabase, extensions);
                extenders.add(rightPathExtender);
            }
            */
            List<RightPathExtender> extenders = new LinkedList<RightPathExtender>();
            for (int i = 0; i < threadPool.getThreadCount(); i++) {
                RightPathExtender rightPathExtender = new RightPathExtender(extensionPDFSCounter, iterator, this, c, graphDatabase, extensions);
                extenders.add(rightPathExtender);
            }

            List<Future<Integer>> futures = threadPool.getExecutorService().invokeAll(extenders);

            // check equivalent occurrence for each rightmost extension by comparing number of projections to the number of extended projections
            for (ExtendedEdge ee : extensionPDFSCounter.keySet()) {
                if (projected.getNumProjections() != extensionPDFSCounter.get(ee)) {
                    extensions.get(ee).setExtendedEquivalentOccurrence(false);
                }
            }
        }
        return extensions;
    }

    /**
     * Finds the set of projected edges enumerations for each edge in a closed subgraph and adds (set, subgraph) entries to the hash table.
     *
     * @param closedSubgraph closed subgraph to be added to hash table
     */
    protected void updateClosedSubgraphsHashTable(ClosedSubgraph closedSubgraph) {
        ProjectedCompact projected = closedSubgraph.getProjected();

        List<Set<EdgeEnumeration>> keys = projected.buildKeys();

        for (Set<EdgeEnumeration> key : keys) {
            if (!closedSubgraphsHashTable.containsKey(key)) {
                closedSubgraphsHashTable.put(key, new LinkedList<ClosedSubgraph>());
            }

            closedSubgraphsHashTable.get(key).add(closedSubgraph);
        }
    }

    /**
     * checks if further expansion of the DFS tree should be terminated
     *
     * @param setOfGraphsIDs                 ids of database graphs with current DFS code projection
     * @param projected                      edges of each projection of the current DFS code into database graph
     * @param earlyTerminationFailureHandler early termination failure detector
     * @return early termination and early termination failure indicators
     */
    protected EarlyTerminationResult earlyTermination(Set<Integer> setOfGraphsIDs, ProjectedCompact projected, IEarlyTerminationFailureHandler earlyTerminationFailureHandler) {
        // set of edges enumerations of the projection of the last edge in the DFS code
        List<Set<EdgeEnumeration>> keys = projected.buildKeys();
        Set<ClosedSubgraph> closedSubgraphs = null;

        for (Set<EdgeEnumeration> key : keys) {
            if (!closedSubgraphsHashTable.containsKey(key)) {
                return new EarlyTerminationResult(false, false);
            }
            if (closedSubgraphs == null) {
                closedSubgraphs = new HashSet<ClosedSubgraph>();
                closedSubgraphs.addAll(closedSubgraphsHashTable.get(key));
            } else {
                closedSubgraphs.retainAll(closedSubgraphsHashTable.get(key));
                if (closedSubgraphs.isEmpty()) {
                    return new EarlyTerminationResult(false, false);
                }
            }
        }

        if (closedSubgraphs == null) {
            return new EarlyTerminationResult(false, false);
        }

        boolean earlyTermination = false;
        for (ClosedSubgraph closedSubgraph : closedSubgraphs) {
            // checks equivalent occurrence of the DFS code with a previously discovered closed subgraph
            Map<Integer, Integer> isomorphism = closedSubgraph.checkEquivalentOccurrence(setOfGraphsIDs, setOfGraphsIDs.size(), projected);
            if (isomorphism != null) {
                earlyTermination = true;
                if (detectEarlyTerminationFailure) {
                    // checks if early termination should not be applied because of early termination failure conditions
                    boolean earlyTerminationFailure = checkEarlyTerminationFailure(closedSubgraph, isomorphism, earlyTerminationFailureHandler);
                    if (earlyTerminationFailure) {
                        return new EarlyTerminationResult(false, true);
                    }
                }
            }
        }

        return new EarlyTerminationResult(earlyTermination, false);
    }

    /**
     * checks if early termination should not be applied
     *
     * @param closedSubgraph                 previously discovered closed subgraph with equivalent occurrence from the current DFS code
     * @param isomorphism                    isomorphism of the current DFS code into closed subgraph
     * @param earlyTerminationFailureHandler early termination failure detector
     * @return true if early termination should not be applied, false otherwise
     */
    protected boolean checkEarlyTerminationFailure(ClosedSubgraph closedSubgraph, Map<Integer, Integer> isomorphism, IEarlyTerminationFailureHandler earlyTerminationFailureHandler) {
        // finds maximum edge index in the closed subgraph DFS code injected by the isomorphism
        int maxDfsIndex = 0;
        for (int dfsIndex : isomorphism.values()) {
            if (dfsIndex > maxDfsIndex) {
                maxDfsIndex = dfsIndex;
            }
        }

        // extract prefix of the closed subgraph DFS code
        List<ExtendedEdge> extendedEdges = closedSubgraph.dfsCode.getEeL().subList(0, maxDfsIndex + 1);
        // search DFS code prefix in early termination failure DFS codes trie
        boolean detected = earlyTerminationFailureHandler.detect(extendedEdges);
        return detected;
    }

    /**
     * Checks if closed subgraph can be extended to any of discovered closed subgraphs using multiple isomorphisms
     *
     * @param closedSubgraph checked subgraph
     * @return true if such extension is possible, false otherwise
     */
    protected boolean isExtendableWithMultipleIsomorphisms(ClosedSubgraph closedSubgraph) {
        for (ClosedSubgraph subgraph : closedSubgraphs) {
            if (subgraph == closedSubgraph) {
                continue;
            }

            boolean extendableWithMultipleIsomorphisms = closedSubgraph.isExtendedWithMultipleIsomorphisms(subgraph);

            if (extendableWithMultipleIsomorphisms) {
                return true;
            }
        }

        return false;
    }

    /**
     * Initial call of the depth-first search
     *
     * @param graphDB              a graph database
     * @param outputClosedVertices if true, include frequent subgraph with a
     *                             single vertex in the output
     * @throws IOException            exception if error writing/reading to file
     * @throws ClassNotFoundException if error casting a class
     */
    protected void cgSpan(List<DatabaseGraph> graphDB, boolean outputClosedVertices, IEarlyTerminationFailureHandler earlyTerminationFailureHandler) throws IOException, ClassNotFoundException, InterruptedException {

        // If the user wants single vertex graph, we will output them
        if (outputClosedVertices || ELIMINATE_INFREQUENT_VERTICES) {
            findAllOnlyOneVertex(graphDB);
        }

        for (DatabaseGraph g : graphDB) {
            g.precalculateVertexList();
        }

        if (ELIMINATE_INFREQUENT_VERTEX_PAIRS || ELIMINATE_INFREQUENT_EDGE_LABELS) {
            removeInfrequentVertexPairs(graphDB);
        }

        if (DEBUG_MODE) {
            System.out.println("Precalculating information...");
        }

        // Create a set with all the graph ids
        Set<Integer> graphIds = new HashSet<Integer>();
        for (int i = 0; i < graphDB.size(); i++) {
            DatabaseGraph g = graphDB.get(i);

            if (g.vertices == null || g.vertices.length != 0) {
                // If we deleted some vertices, we recalculate again the vertex list
                if (infrequentVerticesRemovedCount > 0) {
                    g.precalculateVertexList();
                }

                graphIds.add(i);

                // Precalculate the list of neighbors of each vertex
                g.precalculateVertexNeighbors();

                // Precalculate the list of vertices having each label
                g.precalculateLabelsToVertices();

                g.buildEdgeEnumeration();
            } else {
                if (DEBUG_MODE) {
                    System.out.println("EMPTY GRAPHS REMOVED");
                }
                emptyGraphsRemoved++;
            }
        }

        if (frequentVertexLabels.size() != 0) {
            // initialize projected edges cache
            ProjectedEdge.init(graphDB);

            if (outputClosedVertices) {
                ProjectedCompact projected = new ProjectedCompact(null, graphDB);
                projected.setGraphIds(graphIds);
                outputClosedOneVertex(graphDB, projected);
            }


            if (DEBUG_MODE) {
                System.out.println("Starting depth-first search...");
            }

            ProjectedCompact projected = new ProjectedCompact(null, graphDB);
            projected.setGraphIds(graphIds);

            // Start the depth-first search
            cgSpanDFS(new DFSCode(), graphDB, graphIds, projected, earlyTerminationFailureHandler);
        }
    }

    /**
     * Pair
     */
    protected class Pair {
        /**
         * a value
         */
        int x;
        /**
         * another value
         */
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
            return x + 100 * y;
        }
    }

    /**
     * Pair
     */
    protected class EdgeLabelVertexLabel {
        int edgeLabel;
        int vertexLabel;

        public EdgeLabelVertexLabel(int edgeLabel, int vertexLabel) {
            this.edgeLabel = edgeLabel;
            this.vertexLabel = vertexLabel;
        }

        public int getEdgeLabel() {
            return edgeLabel;
        }

        public void setEdgeLabel(int edgeLabel) {
            this.edgeLabel = edgeLabel;
        }

        public int getVertexLabel() {
            return vertexLabel;
        }

        public void setVertexLabel(int vertexLabel) {
            this.vertexLabel = vertexLabel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgeLabelVertexLabel that = (EdgeLabelVertexLabel) o;
            return edgeLabel == that.edgeLabel && vertexLabel == that.vertexLabel;
        }

        @Override
        public int hashCode() {
            return Objects.hash(edgeLabel, vertexLabel);
        }
    }

    /**
     * Create the pruning matrix
     */
    protected abstract void removeInfrequentVertexPairs(List<DatabaseGraph> graphDB);

    /**
     * compares two edges by the lexicographical order
     */
    public class ExtendedEdgeLexicographicalComparator implements Comparator<ExtendedEdge> {
        @Override
        public int compare(ExtendedEdge ee1, ExtendedEdge ee2) {
            if (ee1.equals(ee2)) {
                return 0;
            }

            if (ee1.smallerThanOriginal(ee2)) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Check if a DFS code is canonical
     *
     * @param c a DFS code
     * @return true if it is canonical, and otherwise, false.
     */
    protected boolean isCanonical(DFSCode c) {
        DFSCode canC = new DFSCode();
        for (int i = 0; i < c.size(); i++) {
            Map<ExtendedEdge, Set<Integer>> extensions = rightMostPathExtensionsFromSingle(canC, new Graph(c));
            ExtendedEdge minEE = null;
            for (ExtendedEdge ee : extensions.keySet()) {
                if (ee.smallerThanOriginal(minEE))
                    minEE = ee;
            }

            if (minEE.smallerThanOriginal(c.getAt(i)))
                return false;
            canC.add(minEE);
        }
        return true;
    }

    /**
     * This method outputs vertices that do not have equivalent occurrence with any closed subgraph.
     *
     * @param graphDB   a graph database
     * @param projected empty projections
     */
    protected abstract void outputClosedOneVertex(List<DatabaseGraph> graphDB, ProjectedCompact projected) throws IOException, ClassNotFoundException, InterruptedException;


    public boolean isDetectEarlyTerminationFailure() {
        return detectEarlyTerminationFailure;
    }

    public void setDetectEarlyTerminationFailure(boolean detectEarlyTerminationFailure) {
        this.detectEarlyTerminationFailure = detectEarlyTerminationFailure;
    }

    public boolean isOutputExtendableByMultipleIsomorphisms() {
        return outputExtendableByMultipleIsomorphisms;
    }

    public void setOutputExtendableByMultipleIsomorphisms(boolean outputExtendableByMultipleIsomorphisms) {
        this.outputExtendableByMultipleIsomorphisms = outputExtendableByMultipleIsomorphisms;
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
    protected abstract void cgSpanDFS(DFSCode c, List<DatabaseGraph> graphDB, Set<Integer> graphIds, ProjectedCompact projected, IEarlyTerminationFailureHandler earlyTerminationFailureHandler) throws IOException, ClassNotFoundException, InterruptedException;

    /**
     * This method finds all frequent vertex labels from a graph database.
     *
     * @param graphDB a graph database
     */
    protected abstract void findAllOnlyOneVertex(List<DatabaseGraph> graphDB);


    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public abstract void printStats();

    /**
     * Set the debug mode to true or false
     *
     * @param value true or false
     */
    public void setDebugMode(boolean value) {
        DEBUG_MODE = value;
    }

    public boolean isPdfsAutomorphismOptimization() {
        return pdfsAutomorphismOptimization;
    }

    public void setPdfsAutomorphismOptimization(boolean pdfsAutomorphismOptimization) {
        this.pdfsAutomorphismOptimization = pdfsAutomorphismOptimization;
    }

    /**
     * used to return early termination and early termination failure indicators
     */
    protected class EarlyTerminationResult {
        private boolean earlyTermination;
        private boolean isEarlyTerminationFailure;

        public EarlyTerminationResult(boolean earlyTermination, boolean isEarlyTerminationFailure) {
            this.earlyTermination = earlyTermination;
            this.isEarlyTerminationFailure = isEarlyTerminationFailure;
        }

        public boolean isEarlyTermination() {
            return earlyTermination;
        }

        public boolean isEarlyTerminationFailure() {
            return isEarlyTerminationFailure;
        }
    }

}
