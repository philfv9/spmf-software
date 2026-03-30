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
 * This is implementation of the iterator over all DFS code projections into the graphs database
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
 * @see ProjectedCompact
 * @author Shaul Zevin
 */
public class ProjectedPrefixIterator implements Iterator<PDFSCompact> {
    // projections compact memory representation
    private ProjectedCompact projected;
    // next projection
    private PDFSCompact nextPDFS = null;
    // next projection database graph
    private DatabaseGraph databaseGraph;
    // list of first edges from all projections
    private List<ProjectedEdge> firstEdges;
    // index in the list of first edges from all projections (firstEdges).
    // projections are built starting with first edge pointed by the index
    private int firstEdgeIndex;
    // projected edges iterators stack, iterator at offset i outputs projected edges to match DFS code edge at offset i
    private Stack<Iterator<ProjectedEdge>> vertexEdgesIterators = new Stack<Iterator<ProjectedEdge>>();
    // projected edges outputted by projected edges iterators
    private Stack<ProjectedEdge> pdfs = new Stack<ProjectedEdge>();
    // projected vertices stack, database graph vertex at offset i is projected from DFS code vertex i.
    private Stack<Vertex> vertices = new Stack<Vertex>();
    // set of database graph projected vertices
    private Set<Vertex> verticesSet = new HashSet<Vertex>();
    // length of created projections
    private int prefixLength;

    public ProjectedPrefixIterator(ProjectedCompact projected, int prefixLength) {
        this.projected = projected;
        this.prefixLength = prefixLength;
        firstEdges = new ArrayList<ProjectedEdge>();
        for (Map<Integer, Set<ProjectedEdge>> vertexEdges: projected.getProjected().get(0).values()) {
            for (Set<ProjectedEdge> edges: vertexEdges.values()) {
                firstEdges.addAll(edges);
            }
        }
        firstEdgeIndex = 0;

        advance();
    }

    public ProjectedPrefixIterator(ProjectedCompact projected, int gid, int prefixLength) {
        this.projected = projected;
        this.prefixLength = prefixLength;
        firstEdges = new ArrayList<ProjectedEdge>();
        for (Integer vertexEdgesGid: projected.getProjected().get(0).keySet()) {
            if (vertexEdgesGid != gid) {
                continue;
            }

            Map<Integer, Set<ProjectedEdge>> vertexEdges = projected.getProjected().get(0).get(vertexEdgesGid);
            for (Set<ProjectedEdge> edges: vertexEdges.values()) {
                firstEdges.addAll(edges);
            }
        }
        firstEdgeIndex = 0;

        advance();
    }

    /**
     * create next projection
     */
    private void advance() {
        DFSCode dfsCode = projected.getDfsCode();

        // special case when DFS code is of length 1. only first edges from all projections are considered.
        if (dfsCode.size() == 1) {

            if (firstEdgeIndex == firstEdges.size()) {
                nextPDFS = null;
                return;
            }

            ProjectedEdge firstEdge = firstEdges.get(firstEdgeIndex);
            databaseGraph = projected.getGraphDatabase().get(firstEdge.getEdgeEnumeration().getGid());
            pdfs.push(firstEdge);
            if (firstEdge.isReversed()) {
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
            } else {
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
            }

            nextPDFS = new PDFSCompact(databaseGraph, pdfs, vertices);
            return;
        }

        // first projection edge creation
        if (pdfs.size() == 0) {
            if (firstEdgeIndex == firstEdges.size()) {
                nextPDFS = null;
                return;
            }

            ProjectedEdge firstEdge = firstEdges.get(firstEdgeIndex);
            firstEdgeIndex++;
            databaseGraph = projected.getGraphDatabase().get(firstEdge.getEdgeEnumeration().getGid());
            pdfs.push(firstEdge);
            if (firstEdge.isReversed()) {
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
            } else {
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
                vertices.push(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v1));
                verticesSet.add(databaseGraph.vMap.get(firstEdge.getEdgeEnumeration().getEdge().v2));
            }

            ExtendedEdge extendedEdge = dfsCode.getAt(1);
            Vertex v = vertices.get(extendedEdge.v1);
            Iterator<ProjectedEdge> edgeIterator = projected.getProjected().get(1).get(databaseGraph.getId()).get(v.getId()).iterator();
            vertexEdgesIterators.push(edgeIterator);
        }

        // next projected edge creation by the stack top iterator
        Iterator<ProjectedEdge> edgeIterator = vertexEdgesIterators.peek();

        ExtendedEdge extendedEdge = dfsCode.getAt(pdfs.size());
        // forward edge
        if (extendedEdge.v2 > extendedEdge.v1) {
            Vertex v1 = vertices.elementAt(extendedEdge.v1);
            while (edgeIterator.hasNext()) {
                ProjectedEdge nextEdge = edgeIterator.next();
                int v2Id = nextEdge.isReversed()? nextEdge.getEdgeEnumeration().getEdge().v1 : nextEdge.getEdgeEnumeration().getEdge().v2;
                Vertex v2 = databaseGraph.vMap.get(v2Id);

                // check that v2 was not projected yet
                if (verticesSet.contains(v2)) {
                    continue;
                }

                vertices.push(v2);
                verticesSet.add(v2);

                pdfs.push(nextEdge);

                // if length of projected edges reached the DFS code length, output projection
                if (pdfs.size() == dfsCode.size()) {
                    nextPDFS = new PDFSCompact(databaseGraph, pdfs, vertices, dfsCode, prefixLength);
                    return;
                }

                // check if next dfs edge is forward and add iterator
                ExtendedEdge nextExtendedEdge = dfsCode.getAt(pdfs.size());
                if (nextExtendedEdge.v2 > nextExtendedEdge.v1) {
                    Vertex nextV = vertices.elementAt(nextExtendedEdge.v1);

                    Iterator<ProjectedEdge> nextEdgeIterator = projected.getProjected().get(pdfs.size()).get(databaseGraph.getId()).get(nextV.getId()).iterator();
                    vertexEdgesIterators.push(nextEdgeIterator);
                }

                advance();

                if (pdfs.size() == dfsCode.size()) {
                    return;
                }

                // projection was not found
                Vertex v = vertices.pop();
                verticesSet.remove(v);
                pdfs.pop();
                if (nextExtendedEdge.v2 > nextExtendedEdge.v1) {
                    vertexEdgesIterators.pop();
                }
            }

            // check if first edge needs to be replaced
            if (pdfs.size() == 1) {
                Vertex v = vertices.pop();
                verticesSet.remove(v);
                v = vertices.pop();
                verticesSet.remove(v);
                vertexEdgesIterators.pop();
                pdfs.pop();
                advance();
            }
        }
        // backward edge
        else {
            Vertex v1 = vertices.elementAt(extendedEdge.v1);
            Vertex v2 = vertices.elementAt(extendedEdge.v2);
            ProjectedEdge nextEdge = null;

            if (!projected.getProjected().get(pdfs.size()).containsKey(databaseGraph.getId())) {
                return;
            }

            if (!projected.getProjected().get(pdfs.size()).get(databaseGraph.getId()).containsKey(v1.getId())) {
                return;
            }

            for (ProjectedEdge projectedEdge: projected.getProjected().get(pdfs.size()).get(databaseGraph.getId()).get(v1.getId())) {
                int projectedV2 = projectedEdge.isReversed() ? projectedEdge.getEdgeEnumeration().getEdge().v1: projectedEdge.getEdgeEnumeration().getEdge().v2;
                if (projectedV2 == v2.getId()) {
                    nextEdge = projectedEdge;
                    break;
                }
            }

            if (nextEdge == null) {
                return;
            }

            pdfs.push(nextEdge);

            // if length of projected edges reached the DFS code length, output projection
            if (pdfs.size() == dfsCode.size()) {
                nextPDFS = new PDFSCompact(databaseGraph, pdfs, vertices, dfsCode, prefixLength);
                return;
            }

            // check if next dfs edge is forward and add iterator
            ExtendedEdge nextExtendedEdge = dfsCode.getAt(pdfs.size());
            if (nextExtendedEdge.v2 > nextExtendedEdge.v1) {
                Vertex nextV = vertices.elementAt(nextExtendedEdge.v1);

                Iterator<ProjectedEdge> nextEdgeIterator = projected.getProjected().get(pdfs.size()).get(databaseGraph.getId()).get(nextV.getId()).iterator();
                vertexEdgesIterators.push(nextEdgeIterator);
            }

            advance();

            if (pdfs.size() == dfsCode.size()) {
                return;
            }

            // projection was not found
            pdfs.pop();
            if (nextExtendedEdge.v2 > nextExtendedEdge.v1) {
                vertexEdgesIterators.pop();
            }
        }

    }

    @Override
    /**
     * true if iterator hadn't produced all projections, false otherwise
     */
    public boolean hasNext() {
        return nextPDFS != null;
    }

    @Override
    /**
     * outputs next projection
     */
    public PDFSCompact next() {
        if (nextPDFS == null) {
            return nextPDFS;
        }

        // save last projection
        PDFSCompact previousPDFS = nextPDFS;

        // create new projection
        nextPDFS = null;

        DFSCode dfsCode = projected.getDfsCode();

        // remove iterators, vertices and edges from prefixLength top edges
        for (int i = dfsCode.size() - 1; i >= 2; i--) {
            ExtendedEdge extendedEdge = dfsCode.getAt(i);
            // if forward edge remove top vertex
            if (extendedEdge.v2 > extendedEdge.v1) {
                if (i < prefixLength) {
                    break;
                }

                Vertex v = vertices.pop();
                verticesSet.remove(v);
                vertexEdgesIterators.pop();
            }

            pdfs.pop();
        }

        // special case when DFS code is of length 1
        if (dfsCode.size() == 1) {
            // clean all stacks
            pdfs.pop();
            Vertex v = vertices.pop();
            verticesSet.remove(v);
            v = vertices.pop();
            verticesSet.remove(v);
            // pick next first edge
            firstEdgeIndex++;
            advance();
        } else {
            while (nextPDFS == null) {
                // pop all top backward edges
                for (int i = pdfs.size() - 1; i >= 0; i--) {
                    ExtendedEdge extendedEdge = dfsCode.getAt(i);
                    if (extendedEdge.v2 < extendedEdge.v1) {
                        pdfs.pop();
                    } else {
                        break;
                    }
                }

                // pop last forward edge
                Vertex v = vertices.pop();
                verticesSet.remove(v);
                pdfs.pop();

                if (prefixLength == 1) {
                    pdfs.pop();
                    v = vertices.pop();
                    verticesSet.remove(v);
                    v = vertices.pop();
                    verticesSet.remove(v);
                    vertexEdgesIterators.pop();
                }
                advance();

                // check if all projections were outputted
                if (firstEdgeIndex == firstEdges.size() && pdfs.size() == 0) {
                    break;
                }

                // check if all projections from the top iterator were outputted
                if (nextPDFS == null) {
                    vertexEdgesIterators.pop();
                }
            }
        }

        return previousPDFS;
    }
}
