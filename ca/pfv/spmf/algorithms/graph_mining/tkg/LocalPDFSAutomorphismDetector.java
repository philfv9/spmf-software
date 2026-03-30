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
 * finds and holds isomorphic subgraphs on the DFS code
 *
 * @see IsomorphicSubgraph
 * @author Shaul Zevin
 */
public class LocalPDFSAutomorphismDetector {
    // DFS code projections
    private ProjectedCompact projectedCompact;
    // index of isomorphic subgraph by the maximum index of the DFS code edge that belongs to the isomorphic subgraph
    private Map<Integer, IsomorphicSubgraph> isomorphismsMaxIndices = new HashMap<Integer, IsomorphicSubgraph>();

    public LocalPDFSAutomorphismDetector(ProjectedCompact projectedCompact) {
        this.projectedCompact = projectedCompact;
        connectedComponents();
    }

    /**
     * checks if the projection is canonical
     * @param projectedEdges projection
     * @return false if projection length equals to the maximum edge index of the isomorphic subgraph on the DFS code and projection is not canonical,
     * true otherwise
     */
    public boolean beforeAdvance(List<ProjectedEdge> projectedEdges) {
        int index = projectedEdges.size() - 1;

        if (isomorphismsMaxIndices.containsKey(index)) {
            boolean isCanonical = isomorphismsMaxIndices.get(index).isCanonicalPDFS(projectedCompact, projectedEdges);
            return isCanonical;
        }

        return true;
    }

    /**
     * detects isomorphic subgraphs on the DFS code
     * first all edges having both vertices identically labeled are found and a new component is initialized with that edge
     * next if exists an edge such that one of its vertices belongs to one component
     * and the other vertex belongs to another component, two components are merged.
     */
    private void connectedComponents() {
        DFSCode dfsCode = projectedCompact.getDfsCode();

        // find all edges with identical vertex labels
        Map<ExtendedEdge, Set<ExtendedEdge>> isomorphicExtendedEdges = new HashMap<ExtendedEdge, Set<ExtendedEdge>>();
        Map<ExtendedEdge, Boolean> visited = new HashMap<ExtendedEdge, Boolean>();
        Map<ExtendedEdge, Integer> components = new HashMap<ExtendedEdge, Integer>();

        for (ExtendedEdge extendedEdge: dfsCode.getEeL()) {
            if (extendedEdge.vLabel1 == extendedEdge.vLabel2) {
                isomorphicExtendedEdges.put(extendedEdge, new HashSet<ExtendedEdge>());
                visited.put(extendedEdge, false);
            }
        }

        // merge edges that have identical vertex labels and share a vertex
        for (ExtendedEdge ee1: isomorphicExtendedEdges.keySet()) {
            for (ExtendedEdge ee2: isomorphicExtendedEdges.keySet()) {
                if (ee1 == ee2) {
                    continue;
                }
                // check if two isomorphic edges share a vertex
                if (ee1.v1 == ee2.v1 || ee1.v1 == ee2.v2 || ee1.v2 == ee2.v1 || ee1.v2 == ee2.v2) {
                    isomorphicExtendedEdges.get(ee1).add(ee2);
                }
            }
        }

        // merge sets of edges from the previous step if exists an edge that connects them
        for (ExtendedEdge ee1: isomorphicExtendedEdges.keySet()) {
            for (ExtendedEdge extendedEdge: dfsCode.getEeL()) {
                if (ee1 == extendedEdge) {
                    continue;
                }

                if (isomorphicExtendedEdges.keySet().contains(extendedEdge)) {
                    continue;
                }

                if (extendedEdge.v1 == ee1.v1 || extendedEdge.v1 == ee1.v2) {
                    for (ExtendedEdge ee2: isomorphicExtendedEdges.keySet()) {
                        if (extendedEdge.v2 == ee2.v1 || extendedEdge.v2 == ee2.v2) {
                            isomorphicExtendedEdges.get(ee1).add(ee2);
                            isomorphicExtendedEdges.get(ee2).add(ee1);
                        }
                    }
                }
            }
        }

        // run connected components algorithm
        int component = 0;

        for (ExtendedEdge extendedEdge: isomorphicExtendedEdges.keySet()) {
            if (!visited.get(extendedEdge)) {
                connectedComponentsDFS(extendedEdge, isomorphicExtendedEdges, visited, components, component);
                component++;
            }
        }

        // create isomorphic graph for each connected component
        for (int i = 0; i < component; i++) {
            Set<ExtendedEdge> componentIsomorphicEdges = new HashSet<ExtendedEdge>();
            for (ExtendedEdge extendedEdge: components.keySet()) {
                if (components.get(extendedEdge) == i) {
                    componentIsomorphicEdges.add(extendedEdge);
                }
            }

            IsomorphicSubgraph isomorphicSubgraph = new IsomorphicSubgraph(dfsCode, componentIsomorphicEdges);
            isomorphismsMaxIndices.put(isomorphicSubgraph.getMaxEdgeIndex(), isomorphicSubgraph);
        }
    }

    private void connectedComponentsDFS(ExtendedEdge extendedEdge, Map<ExtendedEdge, Set<ExtendedEdge>> isomorphicExtendedEdges, Map<ExtendedEdge, Boolean> visited, Map<ExtendedEdge, Integer> components, int component)  {
        visited.put(extendedEdge, true);
        components.put(extendedEdge, component);
        for (ExtendedEdge connectedExtendedEdge : isomorphicExtendedEdges.get(extendedEdge)) {
            if (!visited.get(connectedExtendedEdge))
                connectedComponentsDFS(connectedExtendedEdge, isomorphicExtendedEdges, visited, components, component);
        }
    }

    /**
     * replaces a canonical projection with projections of all isomorphic subgraphs represented by that projection
     * @param pdfsCompactCanonical canonical projection
     * @return all projections of the isomorphic subgraphs
     */
    public PDFSCompact beforeSubmit(PDFSCompact pdfsCompactCanonical) {
        IsomorphicSubgraphProjections projections = new IsomorphicSubgraphProjections(pdfsCompactCanonical.databaseGraph, pdfsCompactCanonical.projectedEdges, pdfsCompactCanonical.vertices, new HashSet<Integer>());
        // merge projections of all isomorphic subgraphs
        for (IsomorphicSubgraph isomorphicSubgraph: isomorphismsMaxIndices.values()) {
            IsomorphicSubgraphProjections isomorphicSubgraphProjections = isomorphicSubgraph.projections(projectedCompact, pdfsCompactCanonical);
            projections = projections.merge(isomorphicSubgraphProjections, projectedCompact.getDfsCode().getRightMost());
        }

        return projections;
    }
}
