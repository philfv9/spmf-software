package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.*;
import java.util.stream.Collectors;
/* This file is copyright (c) 2022 by Shaul Zevin
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
 */

/**
 * This is the implementation of early termination failure handling when closed subgraph frequency is defined by support
 *
 * @see EarlyTerminationFailureHandlerAbstract
 * @author Shaul Zevin
 */
public class EarlyTerminationFailureHandlerSupport extends EarlyTerminationFailureHandlerAbstract {
    /**
     * the minimum support represented as a count (number of subgraph occurrences)
     */
    private int minSup;

    public EarlyTerminationFailureHandlerSupport(List<DatabaseGraph> graphDB, int minSup) {
        super(graphDB);
        this.minSup = minSup;
    }

    /**
     * Checks if by breaking some edges not on the rightmost path
     * either a new frequent forward extension from the rightmost vertex can be created
     * or more projections can be added to a forward extension from the rightmost vertex and this extension is frequent
     * Adds DFS code to trie if check evaluates to true
     *
     * @param dfsCode            examined DFS code
     * @param projected          examined DFS code projections
     * @param rightMostPathEdges edges on the rightmost path
     * @param forwardExtensions  forward extensions
     * @return true if the check evaluates to true, false otherwise
     */
    protected boolean analyzeCase1(DFSCode dfsCode, ProjectedCompact projected, List<Integer> rightMostPathEdges, Map<ExtendedEdge, ProjectedCompact> forwardExtensions) {
        int length = projected.getProjected().size();

        if (dfsCode.getAt(length - 1).v2 > dfsCode.getAt(length - 1).v1) {
            Map<ElbVlbKey, Set<Integer>> elbVlbGids = new HashMap<ElbVlbKey, Set<Integer>>();
            ProjectedIteratorConsumer iterator = projected.iterator(ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() * 2, ThreadPool.getProjectedIteratorProducersInstance().getThreadCount());
            while (iterator.hasNext()) {
                PDFSCompact pdfs = iterator.next();

                int edgeIndex = rightMostPathEdges.get(0);
                // rightmost vertex
                int fromVertexId = pdfs.getDFSedgeAtToVertex(edgeIndex);
                DatabaseGraph g = graphDB.get(pdfs.getProjectedEdges().get(0).getEdgeEnumeration().getGid());
                Vertex fromVertex = g.vMap.get(fromVertexId);
                for (Edge edge : fromVertex.getEdgeList()) {
                    if (pdfs.hasEdge(edge)) {
                        continue;
                    }

                    int toVertexId = (edge.v1 == fromVertexId ? edge.v2 : edge.v1);

                    if (pdfs.hasVertex(toVertexId, rightMostPathEdges)) {
                        continue;
                    }

                    if (pdfs.hasVertex(toVertexId)) {
                        // edge from a rightmost vertex to one of the vertices not on the rightmost path found
                        ElbVlbKey elbVlbKey = new ElbVlbKey(edge.getEdgeLabel(), g.getVLabel(toVertexId));

                        // break the edge(s) which have 'to' vertex of the found edge
                        if (!elbVlbGids.containsKey(elbVlbKey)) {
                            elbVlbGids.put(elbVlbKey, new HashSet<Integer>());
                        }
                        elbVlbGids.get(elbVlbKey).add(g.getId());
                        // check if a new forward edge is frequent
                        if (elbVlbGids.get(elbVlbKey).size() >= minSup) {
                            trie.insert(dfsCode.getEeL());
                            //System.out.println("case 1");
                            iterator.stop();
                            return true;
                        }
                    }
                }
            }

            for (ElbVlbKey elbVlbKey : elbVlbGids.keySet()) {
                for (ExtendedEdge extendedEdge : forwardExtensions.keySet()) {
                    if (extendedEdge.v1 == dfsCode.getRightMost()
                            && extendedEdge.edgeLabel == elbVlbKey.getElb() && extendedEdge.vLabel2 == elbVlbKey.getVlb()) {
                        // add projections to the original forward extensions
                        elbVlbGids.get(elbVlbKey).addAll(forwardExtensions.get(extendedEdge).getGraphIds());

                        // check if forward extension is frequent
                        if (elbVlbGids.get(elbVlbKey).size() >= minSup) {
                            trie.insert(dfsCode.getEeL());
                            //System.out.println("case 1");
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks if by breaking some edges not on the rightmost path
     * either a new frequent forward extension not from the rightmost vertex can be created
     * or more projections can be added to a forward extension not from the rightmost vertex and this extension is frequent
     * Adds DFS code to trie if check evaluates to true
     *
     * @param dfsCode            examined DFS code
     * @param projected          examined DFS code projections
     * @param rightMostPathEdges edges on the rightmost path
     * @param forwardExtensions  forward extensions
     * @return true if the check evaluates to true, false otherwise
     */
    protected boolean analyzeCase2(DFSCode dfsCode, ProjectedCompact projected, List<Integer> rightMostPathEdges, Map<ExtendedEdge, ProjectedCompact> forwardExtensions) {
        int length = projected.getProjected().size();

        if (dfsCode.getAt(length - 1).v2 > dfsCode.getAt(length - 1).v1) {
            Map<VertexElbVlbKey, Set<Integer>> vertexElbVlbGids = new HashMap<VertexElbVlbKey, Set<Integer>>();

            ProjectedIteratorConsumer iterator = projected.iterator(ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() * 2, ThreadPool.getProjectedIteratorProducersInstance().getThreadCount());
            while (iterator.hasNext()) {
                PDFSCompact pdfs = iterator.next();
                DatabaseGraph g = graphDB.get(pdfs.getProjectedEdges().get(0).getEdgeEnumeration().getGid());
                Map<Integer, Integer> dfs2g = pdfs.subgraphIsomorphism(dfsCode);
                Map<Integer, Integer> g2dfs =
                        dfs2g.entrySet()
                                .stream()
                                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

                // ?? check if it should be enough to consider only the last rightmost path edge
                for (int i = 0; i < rightMostPathEdges.size(); i++) {
                    ExtendedEdge extendedEdge = dfsCode.getAt(rightMostPathEdges.get(i));
                    int vertexStart = extendedEdge.v1;
                    int vertexEnd = extendedEdge.v2;
                    // select only rightmost path edges such that not rightmost path edge starts from the edge v1 (vertexStart)
                    // ?? check if considering multiple forks together is better
                    if (vertexEnd == vertexStart + 1) {
                        continue;
                    }

                    // check all rightmost path edges before the edge
                    for (int j = i + 1; j < rightMostPathEdges.size(); j++) {
                        int vertexFrom = dfsCode.getAt(rightMostPathEdges.get(j)).v1;

                        // collect all vertices between vertexStart + 1 and vertexEnd not inclusive
                        Set<Integer> gNotRmpathVertices = new HashSet<Integer>();
                        for (int v = vertexStart + 1; v < vertexEnd; v++) {
                            gNotRmpathVertices.add(dfs2g.get(v));
                        }

                        for (Edge edge : g.vMap.get(dfs2g.get(vertexFrom)).getEdgeList()) {
                            if (pdfs.hasEdge(edge)) {
                                continue;
                            }

                            int vertexTo = (edge.v1 == dfs2g.get(vertexFrom) ? edge.v2 : edge.v1);

                            if (!gNotRmpathVertices.contains(vertexTo)) {
                                continue;
                            }

                            // edge from a rightmost vertex to one of gNotRmpathVertices found
                            VertexElbVlbKey vertexElbVlbKey = new VertexElbVlbKey(vertexFrom, edge.getEdgeLabel(), g.vMap.get(vertexTo).getLabel());

                            // break the edge(s) which have 'to' vertex of the found edge
                            if (!vertexElbVlbGids.containsKey(vertexElbVlbKey)) {
                                vertexElbVlbGids.put(vertexElbVlbKey, new HashSet<Integer>());
                            }
                            vertexElbVlbGids.get(vertexElbVlbKey).add(g.getId());
                            // check if a new forward edge is frequent
                            if (vertexElbVlbGids.get(vertexElbVlbKey).size() >= minSup) {
                                trie.insert(dfsCode.getEeL());
                                //System.out.println("case 2");
                                iterator.stop();
                                return true;
                            }
                        }
                    }
                }
            }

            for (VertexElbVlbKey vertexElbVlbKey : vertexElbVlbGids.keySet()) {
                for (ExtendedEdge extendedEdge : forwardExtensions.keySet()) {
                    if (extendedEdge.v1 == vertexElbVlbKey.getVertex()
                            && extendedEdge.edgeLabel == vertexElbVlbKey.getElb() && extendedEdge.vLabel2 == vertexElbVlbKey.getVlb()) {
                        // add projections to the original forward extensions
                        vertexElbVlbGids.get(vertexElbVlbKey).addAll(forwardExtensions.get(extendedEdge).getGraphIds());

                        // check if forward extension is frequent
                        if (vertexElbVlbGids.get(vertexElbVlbKey).size() >= minSup) {
                            trie.insert(dfsCode.getEeL());
                            //System.out.println("case 2");
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks if by breaking first rightmost path edge
     * either a new frequent forward extension from the rightmost vertex can be created
     * or more projections can be added to a forward extension from the rightmost vertex and this extension is frequent
     * Adds DFS code to trie if check evaluates to true
     *
     * @param dfsCode            examined DFS code
     * @param projected          examined DFS code projections
     * @param rightMostPathEdges edges on the rightmost path
     * @param forwardExtensions  forward extensions
     * @return true if the check evaluates to true, false otherwise
     */
    protected boolean analyzeCase3(DFSCode dfsCode, ProjectedCompact projected, List<Integer> rightMostPathEdges, Map<ExtendedEdge, ProjectedCompact> forwardExtensions) {
        int length = projected.getProjected().size();

        // second rightmost path edge should have same vertices and edge labels as the first rightmost path edge
        // ?? check if longer rightmost path prefixes which are followed by identically labeled edges should be considered
        if ((dfsCode.getAt(length - 1).v2 > dfsCode.getAt(length - 1).v1)
                && (rightMostPathEdges.size() > 2)
                && (dfsCode.getAt(rightMostPathEdges.get(rightMostPathEdges.size() - 1)).edgeLabel ==
                dfsCode.getAt(rightMostPathEdges.get(rightMostPathEdges.size() - 2)).edgeLabel)
                && (dfsCode.getAt(rightMostPathEdges.get(rightMostPathEdges.size() - 1)).vLabel2 ==
                dfsCode.getAt(rightMostPathEdges.get(rightMostPathEdges.size() - 2)).vLabel2)) {
            Map<ElbVlbKey, Set<Integer>> elbVlbGids = new HashMap<ElbVlbKey, Set<Integer>>();
            ProjectedIteratorConsumer iterator = projected.iterator(ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() * 2, ThreadPool.getProjectedIteratorProducersInstance().getThreadCount());
            while (iterator.hasNext()) {
                PDFSCompact pdfs = iterator.next();
                DatabaseGraph g = graphDB.get(pdfs.getProjectedEdges().get(0).getEdgeEnumeration().getGid());
                // first vertex in rightmost path
                int firstRmpathEdgeIndex = rightMostPathEdges.get(rightMostPathEdges.size() - 1);
                int gRmpathFirstVertex = pdfs.getDFSedgeAtFromVertex(firstRmpathEdgeIndex);
                int edgeIndex = rightMostPathEdges.get(0);
                // rightmost vertex
                int fromVertexId = pdfs.getDFSedgeAtToVertex(edgeIndex);
                Vertex fromVertex = g.vMap.get(fromVertexId);
                for (Edge edge : fromVertex.getEdgeList()) {
                    if (pdfs.hasEdge(edge)) {
                        continue;
                    }

                    int vertexTo = (edge.v1 == fromVertexId ? edge.v2 : edge.v1);
                    if (vertexTo != gRmpathFirstVertex) {
                        continue;
                    }

                    // edge from rightmost vertex to the first rightmost path vertex found
                    ElbVlbKey elbVlbKey = new ElbVlbKey(edge.getEdgeLabel(), g.getVLabel(vertexTo));

                    // break the first rightmost path edge
                    if (!elbVlbGids.containsKey(elbVlbKey)) {
                        elbVlbGids.put(elbVlbKey, new HashSet<Integer>());
                    }
                    elbVlbGids.get(elbVlbKey).add(g.getId());
                    // check if a new forward edge is frequent
                    if (elbVlbGids.get(elbVlbKey).size() >= minSup) {
                        trie.insert(dfsCode.getEeL());
                        //System.out.println("case 3");
                        iterator.stop();
                        return true;
                    }
                }
            }

            for (ElbVlbKey elbVlbKey : elbVlbGids.keySet()) {
                for (ExtendedEdge extendedEdge : forwardExtensions.keySet()) {
                    if (extendedEdge.v1 == dfsCode.getRightMost()
                            && extendedEdge.edgeLabel == elbVlbKey.getElb() && extendedEdge.vLabel2 == elbVlbKey.getVlb()) {
                        // add projections to the original forward extensions
                        elbVlbGids.get(elbVlbKey).addAll(forwardExtensions.get(extendedEdge).getGraphIds());

                        // check if forward extension is frequent
                        if (elbVlbGids.get(elbVlbKey).size() >= minSup) {
                            trie.insert(dfsCode.getEeL());
                            //System.out.println("case 3");
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

}
