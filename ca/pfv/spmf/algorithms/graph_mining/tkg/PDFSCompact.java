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
 * This is an implementation of a DFS code projection into a database graph, used by the CGSPAN algorithm.
 * The projection is implemented as a linked list of database graph edges.
 *  <br/><br/>
 *
 * The cgspan algorithm is described in : <br/>
 * <br/>
 * <p>
 * cgSpan: Closed Graph-Based Substructure Pattern Mining, by Zevin Shaul, Sheikh Naaz
 * IEEE BigData 2021 7th Special Session on Intelligent Data Mining
 * <p>
 *
 * <br/>
 *
 * The CGspan algorithm finds all the closed subgraphs and their support in a
 * graph provided by the user.
 * <br/><br/>
 *
 * This implementation saves the result to a file
 *
 * @see AlgoCGSPANAbstract
 * @author Shaul Zevin
 */

public class PDFSCompact {
    // dummy projection to signal consumer iterator that no more projections can be produced
    public static final PDFSCompact POISON_PDFSCompact = new PDFSCompact();
    // projection database graph
    protected DatabaseGraph databaseGraph;
    // projection edges
    protected List<ProjectedEdge> projectedEdges;
    // projection vertices
    protected List<Vertex> vertices;

    public PDFSCompact() {
    }

    public PDFSCompact(DatabaseGraph databaseGraph, List<ProjectedEdge> projectedEdges, List<Vertex> vertices) {
        this.databaseGraph = databaseGraph;
        this.projectedEdges = new ArrayList<>(projectedEdges);
        this.vertices = new ArrayList<Vertex>(vertices);
    }

    /**
     * creates a projection which is a prefix of another projection
     * @param databaseGraph projection database graph
     * @param projectedEdges edges of another projection
     * @param vertices vertices of another projection
     * @param dfsCode dfsCode of another projection
     * @param prefixLength number of edges from the start of another projection to include in created projection
     */
    public PDFSCompact(DatabaseGraph databaseGraph, Stack<ProjectedEdge> projectedEdges, Stack<Vertex> vertices, DFSCode dfsCode, int prefixLength) {
        this.databaseGraph = databaseGraph;
        this.projectedEdges = new ArrayList<>(projectedEdges).subList(0,prefixLength);
        int numVertices = vertices.size();
        for (int i = dfsCode.size() - 1; i >= prefixLength; i--) {
            ExtendedEdge extendedEdge = dfsCode.getAt(i);
            // if forward edge remove top vertex
            if (extendedEdge.v2 > extendedEdge.v1) {
                numVertices--;
            }
        }
        this.vertices = new ArrayList<Vertex>(vertices).subList(0, numVertices);
    }

    public DatabaseGraph getDatabaseGraph() {
        return databaseGraph;
    }

    public void setDatabaseGraph(DatabaseGraph databaseGraph) {
        this.databaseGraph = databaseGraph;
    }

    public List<ProjectedEdge> getProjectedEdges() {
        return projectedEdges;
    }

    public void setProjectedEdges(Stack<ProjectedEdge> projectedEdges) {
        this.projectedEdges = projectedEdges;
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public void setVertices(Stack<Vertex> vertices) {
        this.vertices = vertices;
    }

    /**
     * builds isomorphism between DFS code vertices and vertices in the database graph
     * @param c DFS code
     * @return isomorphism that maps each vertex in DFS code to it's matching vertex in the projection
     */
    public Map<Integer, Integer> subgraphIsomorphism(DFSCode c) {
        Map<Integer, Integer> isomorphism = new HashMap<Integer, Integer>();
        List<ExtendedEdge> dfsEdges = c.getEeL();
        for (int i = 0; i < dfsEdges.size(); i++) {
            ExtendedEdge dfsEdge = dfsEdges.get(i);
            ProjectedEdge projectedEdge = projectedEdges.get(i);
            if (projectedEdge.isReversed()) {
                isomorphism.put(dfsEdge.v1, projectedEdge.getEdgeEnumeration().getEdge().v2);
                isomorphism.put(dfsEdge.v2, projectedEdge.getEdgeEnumeration().getEdge().v1);
            }
            else {
                isomorphism.put(dfsEdge.v1, projectedEdge.getEdgeEnumeration().getEdge().v1);
                isomorphism.put(dfsEdge.v2, projectedEdge.getEdgeEnumeration().getEdge().v2);
            }
        }

        return isomorphism;
    }

    /**
     *
     * @param index edge index in the projection
     * @return 'to' vertex of the edge specified by index
     */
    public int getDFSedgeAtToVertex(int index) {
        ProjectedEdge projectedEdge = projectedEdges.get(index);

        return (projectedEdge.isReversed() ? projectedEdge.getEdgeEnumeration().getEdge().v1 : projectedEdge.getEdgeEnumeration().getEdge().v2);
    }

    /**
     *
     * @param index edge index in the projection
     * @return 'from' vertex of the edge specified by index
     */
    public int getDFSedgeAtFromVertex(int index) {
        ProjectedEdge projectedEdge = projectedEdges.get(index);

        return (projectedEdge.isReversed() ? projectedEdge.getEdgeEnumeration().getEdge().v2 : projectedEdge.getEdgeEnumeration().getEdge().v1);
    }

    /**
     *
     * @param edge searched edge
     * @return true if projection includes the edge, false otherwise
     * uses edges cache for the search
     * if hit serves result from the cache
     * if miss updates cache with search result
     */
    public boolean hasEdge(Edge edge) {
        for (ProjectedEdge projectedEdge: projectedEdges) {
            if (projectedEdge.getEdgeEnumeration().getEdge().equals(edge)) {
                return true;
            }
        }

        return false;
    }

    /**
     *
     * @param vertexId searched vertex
     * @param edgesIndexes indexes of edges in projection to use for the search
     * @return true if vertex belongs to one of the edges specified by indexes, false otherwise
     */
    public boolean hasVertex(int vertexId, List<Integer> edgesIndexes) {
        for (int index: edgesIndexes) {
            ProjectedEdge projectedEdge = projectedEdges.get(index);
            if (projectedEdge.getEdgeEnumeration().getEdge().v1 == vertexId || projectedEdge.getEdgeEnumeration().getEdge().v2 == vertexId) {
                return true;
            }
        }

        return false;
    }

    /**
     *
     * @param vertexId searched vertex
     * @return true if vertex belongs to one of the edges in projection, false otherwise
     */
    public boolean hasVertex(int vertexId) {
        for (ProjectedEdge projectedEdge: projectedEdges) {
            if (projectedEdge.getEdgeEnumeration().getEdge().v1 == vertexId || projectedEdge.getEdgeEnumeration().getEdge().v2 == vertexId) {
                return true;
            }
        }

        return false;
    }
}
