package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.*;
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
 * Defines subgraph on the DFS code by specifying subset of the DFS code edges
 *
 * @author Shaul Zevin
 */
public class IsomorphicSubgraph {
    // frequent subgraph dfs code
    private DFSCode dfsCode;
    // prefix of frequent graph dfs code which includes all extended edges of isomorphism
    private DFSCode isomorphismDfsCode;
    // DFS code indices of isomorphism edges with identically labeled vertices indices in dfs code
    private List<Integer> isomorphicEdgesIndices = new LinkedList<Integer>();
    // DFS code indices of edges that connect isomorphic edges
    private List<Integer> internalNonIsomorphicEdgesIndices = new LinkedList<Integer>();
    // DFS code indices of edges which share one vertex with isomorphic edge
    private List<Integer> outgoingEdgesIndices = new LinkedList<Integer>();
    // sorted DFS code indices of isomorphic, internal and outgoing edges
    private List<Integer> edgesIndices = new LinkedList<Integer>();
    // vertices of isomorphic edges
    private Set<Integer> isomorphicEdgesVertices = new HashSet<Integer>();
    // vertices of isomorphic edges that are on the rightmost path
    private Set<Integer> isomorphismRightmostPathVertices = new HashSet<Integer>();
    // if rightmost vertex is included in isomorphic vertices
    private boolean includesRightmostVertex;
    // the maximum position of all isomorphic subgraph edges in DFS code
    int maxEdgeIndex = 0;

    /**
     *
     * @param dfsCode frequent subgraph DFS code
     * @param isomorphicEdges edges with both vertices having identical labels.
     *                        Each edge in the set must share at least one vertex with another edge in the set.
     *                        If the edge does not share a vertex with any edge in the set, DFS code must have an edge that connects
     *                        that vertex to some other edge in the set.
     */
    public IsomorphicSubgraph(DFSCode dfsCode, Set<ExtendedEdge> isomorphicEdges) {
        this.dfsCode = dfsCode;

        List<ExtendedEdge> graphDfsCode = new ArrayList(dfsCode.getEeL());

        // isomorphic vertices construction
        for (ExtendedEdge extendedEdge: isomorphicEdges) {
            isomorphicEdgesVertices.add(extendedEdge.v1);
            isomorphicEdgesVertices.add(extendedEdge.v2);
        }

        // isomorphic, internal and outgoing edges DFS code indices construction
        for (int i = 0; i < graphDfsCode.size(); i++) {
            ExtendedEdge graphExtendedEdge = graphDfsCode.get(i);
            if (isomorphicEdges.contains(graphExtendedEdge)) {
                isomorphicEdgesIndices.add(i);
                if (maxEdgeIndex < i) {
                    maxEdgeIndex = i;
                }
                continue;
            }

            if (isomorphicEdgesVertices.contains(graphExtendedEdge.v1) && isomorphicEdgesVertices.contains(graphExtendedEdge.v2)) {
                internalNonIsomorphicEdgesIndices.add(i);
                if (maxEdgeIndex < i) {
                    maxEdgeIndex = i;
                }
                continue;
            }

            if (isomorphicEdgesVertices.contains(graphExtendedEdge.v1) || isomorphicEdgesVertices.contains(graphExtendedEdge.v2)) {
                outgoingEdgesIndices.add(i);
                if (maxEdgeIndex < i) {
                    maxEdgeIndex = i;
                }
                continue;
            }
        }

        isomorphismDfsCode = new DFSCode();
        for (int i = 0; i <= maxEdgeIndex; i++) {
            isomorphismDfsCode.add(graphDfsCode.get(i));
        }

        edgesIndices.addAll(isomorphicEdgesIndices);
        edgesIndices.addAll(internalNonIsomorphicEdgesIndices);
        edgesIndices.addAll(outgoingEdgesIndices);
        Collections.sort(edgesIndices);

        // find which isomorphic vertices are on the rightmost path
        Set<Integer> rightmostPathVertices = new HashSet<Integer>();
        for(int rightmostPathVertex: dfsCode.getRightMostPath()) {
            rightmostPathVertices.add(rightmostPathVertex);
        }

        for (int extendedEdgeIndex: edgesIndices) {
            ExtendedEdge extendedEdge = dfsCode.getAt(extendedEdgeIndex);
            if (rightmostPathVertices.contains(extendedEdge.v1)) {
                isomorphismRightmostPathVertices.add(extendedEdge.v1);
            }

            if (rightmostPathVertices.contains(extendedEdge.v2)) {
                isomorphismRightmostPathVertices.add(extendedEdge.v2);
            }
        }

        includesRightmostVertex = isomorphismRightmostPathVertices.contains(dfsCode.getRightMost());
    }

    /**
     *
     * @param projectedCompact DFS code projections
     * @param pdfsCompactCanonical canonical projection.
     *                             Projection is canonical with respect to the isomorphic subgraph if the following holds:
     *                             Construct set of isomorphic projections by
     *                             1. projection in the set is projected from the isomorphic subgraph DFS code.
     *                             2. projection in the set has same projected edges as canonical projection from DFS edges that do not belong to the isomorphic subgraph.
     *                             3. projection in the set has same set of projected vertices as canonical projection from DFS code edges that belong to the isomorphic subgraph.
     *                             For each projection in the isomorphic projections set, from the ordered list of isomorphic subgraph DFS code isomorphic vertices construct list of their projections.
     *                             Canonical projection is the projection having the smallest list by the lexicographical order.
     *
     * @return isomorphic projections defined by conditions 1,2 and 3 above.
     */
    public IsomorphicSubgraphProjections projections(ProjectedCompact projectedCompact, PDFSCompact pdfsCompactCanonical) {
        List<ProjectedEdge> pdfs = pdfsCompactCanonical.projectedEdges;
        IsomorphicSubgraphProjections isomorphicSubgraphProjections = new IsomorphicSubgraphProjections(pdfsCompactCanonical.databaseGraph, pdfsCompactCanonical.projectedEdges, pdfsCompactCanonical.vertices, new HashSet<Integer>(edgesIndices));
        isomorphicSubgraphProjections.setNumProjections(0);

        ProjectedIterator iterator = isomorphismOrderIterator(projectedCompact, pdfs);
        while (iterator.hasNext()) {
            PDFSCompact pdfsCompact = iterator.next();
            List<ProjectedEdge> projection = pdfsCompact.getProjectedEdges();

            isomorphicSubgraphProjections.addProjection(projection);

            // rightmost path projections
            for (int isomorphismRightmostPathVertex: isomorphismRightmostPathVertices) {
                int projectedIsomorphismRightmostPathVertex = pdfsCompact.getVertices().get(isomorphismRightmostPathVertex).getId();
                isomorphicSubgraphProjections.addVertexRightMostPathIndexProjection(projectedIsomorphismRightmostPathVertex, isomorphismRightmostPathVertex, projection);
            }

            // projections from rightmost vertex to other vertex on rightmost path
            if (includesRightmostVertex) {
                int projectedIsomorphismRightmostVertex = pdfsCompact.getVertices().get(pdfsCompact.getVertices().size() - 1).getId();

                for (int isomorphismRightmostPathVertex: isomorphismRightmostPathVertices) {
                    if (isomorphismRightmostPathVertex == dfsCode.getRightMost() || isomorphismRightmostPathVertex == dfsCode.getRightMost() - 1) {
                        continue;
                    }

                    int projectedIsomorphismRightmostPathVertex = pdfsCompact.getVertices().get(isomorphismRightmostPathVertex).getId();

                    isomorphicSubgraphProjections.addRightMostVertexVertexRightMostPathIndexProjection(projectedIsomorphismRightmostVertex, projectedIsomorphismRightmostPathVertex, isomorphismRightmostPathVertex, projection);
                }
            }
        }

        return isomorphicSubgraphProjections;
    }

    /**
     *
     * @param projectedCompact projections
     * @param pdfs projection to be tested
     * @return true if projection is canonical, false otherwise
     *         Projection is canonical with respect to the isomorphic subgraph if the following holds:
     *         Construct set of isomorphic projections by
     *         1. projection in the set is projected from the isomorphic subgraph DFS code.
     *         2. projection in the set has same projected edges as canonical projection from DFS edges that do not belong to the isomorphic subgraph.
     *         3. projection in the set has same set of projected vertices as canonical projection from DFS code edges that belong to the isomorphic subgraph.
     *         For each projection in the isomorphic projections set, from the ordered list of isomorphic subgraph DFS code isomorphic vertices construct list of their projections.
     *         Canonical projection is the projection having the smallest list by the lexicographical order.
     */
    public boolean isCanonicalPDFS(ProjectedCompact projectedCompact, List<ProjectedEdge> pdfs) {

        // construct iterator to return projections in projected vertices lexicographical order
        ProjectedIterator iterator = isomorphismOrderIterator(projectedCompact, pdfs);

        // the first projection returned by the iterator is the canonical projection
        PDFSCompact canonical = iterator.next();

        // compare to canonical projection
        boolean isCanonical = pdfs.equals(canonical.getProjectedEdges());

        return isCanonical;
    }

    /**
     *
     * @param projectedCompact projections
     * @param pdfs projection
     * @return iterator, iterator will output projections in projected vertices lexicographical order
     */
    private ProjectedIterator isomorphismOrderIterator(ProjectedCompact projectedCompact, List<ProjectedEdge> pdfs) {
        if (pdfs.size() < maxEdgeIndex) {
            return null;
        }

        int gid = pdfs.get(0).getEdgeEnumeration().getGid();

        Map<Integer, Set<Integer>> labelIsomorphicVertices = new HashMap<Integer, Set<Integer>>();
        Set<Integer> isomorphicVertices = new HashSet<Integer>();

        for (int isomorphicEdgeIndex: isomorphicEdgesIndices) {
            ProjectedEdge isomorphicProjectedEdge = pdfs.get(isomorphicEdgeIndex);
            int label = isomorphismDfsCode.getAt(isomorphicEdgeIndex).vLabel1;
            if (!labelIsomorphicVertices.containsKey(label)) {
                labelIsomorphicVertices.put(label, new HashSet<Integer>());
            }
            labelIsomorphicVertices.get(label).add(isomorphicProjectedEdge.getEdgeEnumeration().getEdge().v1);
            labelIsomorphicVertices.get(label).add(isomorphicProjectedEdge.getEdgeEnumeration().getEdge().v2);
            isomorphicVertices.add(isomorphicProjectedEdge.getEdgeEnumeration().getEdge().v1);
            isomorphicVertices.add(isomorphicProjectedEdge.getEdgeEnumeration().getEdge().v2);
        }

        // from label to sorted list of projected vertices
        Map<Integer, List<Integer>> labelIsomorphicVerticesSorted = new HashMap<Integer, List<Integer>>();

        for (int label: labelIsomorphicVertices.keySet()) {
            List<Integer> vertices = new ArrayList(labelIsomorphicVertices.get(label));
            Collections.sort(vertices);
            labelIsomorphicVerticesSorted.put(label, vertices);
        }

        List<Map<Integer, Map<Integer, Set<ProjectedEdge>>>> projections = new LinkedList<Map<Integer, Map<Integer, Set<ProjectedEdge>>>>();

        for (int i = 0; i <= maxEdgeIndex; i++) {
            // LinkedHashSet guarantees order of edges retrieval will follow the order they were added
            Map<Integer, Map<Integer, Set<ProjectedEdge>>> projectionsAt = new HashMap<Integer, Map<Integer, Set<ProjectedEdge>>>();
            projectionsAt.put(gid, new LinkedHashMap<Integer, Set<ProjectedEdge>>());

            // isomorphic edges
            if (isomorphicEdgesIndices.contains(i)) {
                int label = isomorphismDfsCode.getAt(i).vLabel1;
                int edgeLabel = isomorphismDfsCode.getAt(i).edgeLabel;
                List<Integer> sortedVertices = labelIsomorphicVerticesSorted.get(label);
                // add edges in the lexicographical order of projected vertices
                // create all possible edges in the lexicographical order of projected vertices and add an edge if it exists
                for (int f = 0; f < sortedVertices.size(); f++) {
                    int v1 = sortedVertices.get(f);
                    if (!projectedCompact.getProjected().get(i).get(gid).containsKey(v1)) {
                        continue;
                    }
                    for (int t = 0; t < sortedVertices.size(); t++) {
                        if (t == f) {
                            continue;
                        }
                        int v2 = sortedVertices.get(t);
                        Edge edge = new Edge(v1, v2, edgeLabel);
                        Edge edgeDB = new Edge(edge.v1 < edge.v2? edge.v1: edge.v2, edge.v1 < edge.v2? edge.v2: edge.v1, edgeLabel);
                        EdgeEnumeration edgeEnumeration = new EdgeEnumeration(gid, edgeDB);
                        ProjectedEdge projectedEdge = ProjectedEdge.getIfExists(edgeEnumeration, edge.v1 != edgeDB.v1);

                        if (projectedEdge != null && projectedCompact.getProjected().get(i).get(gid).get(v1).contains(projectedEdge)) {
                            if (!projectionsAt.get(gid).containsKey(v1)) {
                                projectionsAt.get(gid).put(v1, new LinkedHashSet<ProjectedEdge>());
                            }
                            projectionsAt.get(gid).get(v1).add(projectedEdge);
                        }
                    }
                }
            }
            else {
                // internal edges
                if (internalNonIsomorphicEdgesIndices.contains(i)) {
                    ExtendedEdge extendedEdge = isomorphismDfsCode.getAt(i);
                    int edgeLabel = extendedEdge.edgeLabel;
                    int vLabel1 = extendedEdge.vLabel1;
                    int vLabel2 = extendedEdge.vLabel2;

                    boolean forward = extendedEdge.v2 > extendedEdge.v1;

                    if (forward) {
                        // add edges in the lexicographical order of projected vertices
                        // create all possible edges in the lexicographical order of projected vertices and add an edge if it exists
                        for (int v1: labelIsomorphicVerticesSorted.get(vLabel1)) {
                            if (!projectedCompact.getProjected().get(i).get(gid).containsKey(v1)) {
                                continue;
                            }
                            for (int v2: labelIsomorphicVerticesSorted.get(vLabel2)) {
                                Edge edge = new Edge(v1, v2, edgeLabel);
                                Edge edgeDB = new Edge(edge.v1 < edge.v2? edge.v1: edge.v2, edge.v1 < edge.v2? edge.v2: edge.v1, edgeLabel);
                                EdgeEnumeration edgeEnumeration = new EdgeEnumeration(gid, edgeDB);
                                ProjectedEdge projectedEdge = ProjectedEdge.getIfExists(edgeEnumeration, edge.v1 != edgeDB.v1);

                                if (projectedEdge != null && projectedCompact.getProjected().get(i).get(gid).get(v1).contains(projectedEdge)) {
                                    if (!projectionsAt.get(gid).containsKey(v1)) {
                                        projectionsAt.get(gid).put(v1, new LinkedHashSet<ProjectedEdge>());
                                    }
                                    projectionsAt.get(gid).get(v1).add(projectedEdge);
                                }
                            }
                        }
                    }
                    else {
                        // add edges in the lexicographical order of projected vertices
                        // create all possible edges in the lexicographical order of projected vertices and add an edge if it exists
                        for (int v2: labelIsomorphicVerticesSorted.get(vLabel2)) {
                            for (int v1: labelIsomorphicVerticesSorted.get(vLabel1)) {
                                if (!projectedCompact.getProjected().get(i).get(gid).containsKey(v1)) {
                                    continue;
                                }
                                Edge edge = new Edge(v1, v2, edgeLabel);
                                Edge edgeDB = new Edge(edge.v1 < edge.v2? edge.v1: edge.v2, edge.v1 < edge.v2? edge.v2: edge.v1, edgeLabel);
                                EdgeEnumeration edgeEnumeration = new EdgeEnumeration(gid, edgeDB);
                                ProjectedEdge projectedEdge = ProjectedEdge.getIfExists(edgeEnumeration, edge.v1 != edgeDB.v1);

                                if (projectedEdge != null && projectedCompact.getProjected().get(i).get(gid).get(v1).contains(projectedEdge)) {
                                    if (!projectionsAt.get(gid).containsKey(v1)) {
                                        projectionsAt.get(gid).put(v1, new LinkedHashSet<ProjectedEdge>());
                                    }
                                    projectionsAt.get(gid).get(v1).add(projectedEdge);
                                }
                            }
                        }
                    }
                }
                else {
                    // outgoing edges
                    if (outgoingEdgesIndices.contains(i)) {
                        ExtendedEdge extendedEdge = isomorphismDfsCode.getAt(i);
                        int isomorphicVertexLabel;
                        if (isomorphicEdgesVertices.contains(extendedEdge.v1)) {
                            isomorphicVertexLabel = extendedEdge.vLabel1;
                        }
                        else {
                            isomorphicVertexLabel = extendedEdge.vLabel2;
                        }

                        ProjectedEdge outgoingProjectedEdge = pdfs.get(i);

                        Edge outgoingEdge = outgoingProjectedEdge.getEdgeEnumeration().getEdge();
                        // add edges in the lexicographical order of projected vertices
                        // create all possible edges in the lexicographical order of projected vertices and add an edge if it exists
                        if (isomorphicVertices.contains(outgoingEdge.v1)) {
                            for (int v1: labelIsomorphicVerticesSorted.get(isomorphicVertexLabel)) {
                                if (!projectedCompact.getProjected().get(i).get(gid).containsKey(v1)) {
                                    continue;
                                }
                                Edge edge = new Edge(v1, outgoingEdge.v2, outgoingEdge.getEdgeLabel());
                                Edge edgeDB = new Edge(edge.v1 < edge.v2? edge.v1: edge.v2, edge.v1 < edge.v2? edge.v2: edge.v1, edge.getEdgeLabel());
                                EdgeEnumeration edgeEnumeration = new EdgeEnumeration(gid, edgeDB);
                                boolean reversed = edge.v1 != edgeDB.v1;
                                if (outgoingProjectedEdge.isReversed()) {
                                    reversed = !reversed;
                                }
                                ProjectedEdge projectedEdge = ProjectedEdge.getIfExists(edgeEnumeration, reversed);

                                if (projectedEdge != null && projectedCompact.getProjected().get(i).get(gid).get(v1).contains(projectedEdge)) {
                                    if (!projectionsAt.get(gid).containsKey(v1)) {
                                        projectionsAt.get(gid).put(v1, new LinkedHashSet<ProjectedEdge>());
                                    }
                                    projectionsAt.get(gid).get(v1).add(projectedEdge);
                                }
                            }
                        }
                        else {
                            if (projectedCompact.getProjected().get(i).get(gid).containsKey(outgoingEdge.v1)) {
                                for (int v2 : labelIsomorphicVerticesSorted.get(isomorphicVertexLabel)) {
                                    Edge edge = new Edge(outgoingEdge.v1, v2, outgoingEdge.getEdgeLabel());
                                    Edge edgeDB = new Edge(edge.v1 < edge.v2 ? edge.v1 : edge.v2, edge.v1 < edge.v2 ? edge.v2 : edge.v1, edge.getEdgeLabel());
                                    EdgeEnumeration edgeEnumeration = new EdgeEnumeration(gid, edgeDB);
                                    boolean reversed = edge.v1 != edgeDB.v1;
                                    if (outgoingProjectedEdge.isReversed()) {
                                        reversed = !reversed;
                                    }
                                    ProjectedEdge projectedEdge = ProjectedEdge.getIfExists(edgeEnumeration, reversed);

                                    if (projectedEdge != null && projectedCompact.getProjected().get(i).get(gid).get(outgoingEdge.v1).contains(projectedEdge)) {
                                        if (!projectionsAt.get(gid).containsKey(outgoingEdge.v1)) {
                                            projectionsAt.get(gid).put(outgoingEdge.v1, new LinkedHashSet<ProjectedEdge>());
                                        }
                                        projectionsAt.get(gid).get(outgoingEdge.v1).add(projectedEdge);
                                    }
                                }
                            }
                        }
                    }
                    else {
                        int v1 = pdfs.get(i).isReversed()? pdfs.get(i).getEdgeEnumeration().getEdge().v2: pdfs.get(i).getEdgeEnumeration().getEdge().v1;
                        if (!projectionsAt.get(gid).containsKey(v1)) {
                            projectionsAt.get(gid).put(v1, new LinkedHashSet<ProjectedEdge>());
                        }
                        projectionsAt.get(gid).get(v1).add(pdfs.get(i));
                    }
                }
            }
            projections.add(projectionsAt);
        }


        ProjectedCompact isomorphicProjectedCompact = new ProjectedCompact(isomorphismDfsCode, projectedCompact.getGraphDatabase());
        isomorphicProjectedCompact.setProjected(projections);

        ProjectedIterator iterator = isomorphicProjectedCompact.iterator();

        return iterator;
    }

    public int getMaxEdgeIndex() {
        return maxEdgeIndex;
    }

    public void setMaxEdgeIndex(int maxEdgeIndex) {
        this.maxEdgeIndex = maxEdgeIndex;
    }
}
