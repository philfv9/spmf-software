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
 * This is an implementation of a DFS code projections into a graphs database, used by the CGSPAN algorithm.
 * Projections are implemented as a linked list of projected edges sets.
 * Set in position i contains database edge in position i of each DFS code projection.
 * Sets are indexed by the database graph and then by 'from' vertex.
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

public class ProjectedCompact {
    // projections DFS code
    private DFSCode dfsCode;
    // graphs database
    private List<DatabaseGraph> graphDatabase;
    // projections
    private List<Map<Integer, Map<Integer,Set<ProjectedEdge>>>> projected = new ArrayList<Map<Integer, Map<Integer,Set<ProjectedEdge>>>>();
    // projections count
    private long numProjections = 0;
    // projections count in each database graph
    private Map<Integer, Long> numProjectionsInGraph = new HashMap<Integer, Long>();
    // ids of graphs to which DFS code has projection
    private Set<Integer> graphIds;
    // if projections have extended equivalent occurrence in the graphs database
    private boolean extendedEquivalentOccurrence = true;

    public ProjectedCompact(DFSCode dfsCode, List<DatabaseGraph> graphDatabase) {
        this.dfsCode = dfsCode;
        this.graphDatabase = graphDatabase;
    }

    /**
     * computes MNI, minimum node image, defined by the minimum of |Number of graphs database vertices to which particular DFS code vertex is projected|
     * @return MNI value
     */
    public int MNI() {
        int mni = 0;
        Set<Integer> visitedVertices = new HashSet<Integer>();

        // computes number of projected vertices for each DFS vertex
        for (int i = 0; i < dfsCode.size(); i++) {
            ExtendedEdge extendedEdge = dfsCode.getAt(i);
            int v1 = extendedEdge.v1;
            if (!visitedVertices.contains(v1)) {
                visitedVertices.add(v1);
                Set<VertexEnumeration> projectedVertices = new HashSet<VertexEnumeration>();
                Map<Integer, Map<Integer, Set<ProjectedEdge>>> index = projected.get(i);
                for (int gid: index.keySet()) {
                    Map<Integer, Set<ProjectedEdge>> vertexEdges = index.get(gid);
                    for (int vertex: vertexEdges.keySet()) {
                        VertexEnumeration vertexEnumeration = new VertexEnumeration(vertex, gid);
                        projectedVertices.add(vertexEnumeration);
                    }
                }

                if (mni == 0) {
                    mni = projectedVertices.size();
                }
                else {
                    if (projectedVertices.size() < mni) {
                        mni = projectedVertices.size();
                    }
                }
            }

            int v2 = extendedEdge.v2;
            if (!visitedVertices.contains(v2)) {
                visitedVertices.add(v2);
                Set<VertexEnumeration> projectedVertices = new HashSet<VertexEnumeration>();

                Map<Integer, Map<Integer, Set<ProjectedEdge>>> index = projected.get(i);
                for (int gid: index.keySet()) {
                    Map<Integer, Set<ProjectedEdge>> vertexEdges = index.get(gid);
                    for (int fromVertex: vertexEdges.keySet()) {
                        for (ProjectedEdge projectedEdge: vertexEdges.get(fromVertex)) {
                            int vertex = projectedEdge.isReversed()? projectedEdge.getEdgeEnumeration().getEdge().v1: projectedEdge.getEdgeEnumeration().getEdge().v2;
                            VertexEnumeration vertexEnumeration = new VertexEnumeration(vertex, gid);
                            projectedVertices.add(vertexEnumeration);
                        }
                    }
                }

                if (mni == 0) {
                    mni = projectedVertices.size();
                }
                else {
                    if (projectedVertices.size() < mni) {
                        mni = projectedVertices.size();
                    }
                }
            }
        }

        return mni;
    }


    public DFSCode getDfsCode() {
        return dfsCode;
    }

    public void setDfsCode(DFSCode dfsCode) {
        this.dfsCode = dfsCode;
    }

    public List<DatabaseGraph> getGraphDatabase() {
        return graphDatabase;
    }

    public void setGraphDatabase(List<DatabaseGraph> graphDatabase) {
        this.graphDatabase = graphDatabase;
    }

    public List<Map<Integer, Map<Integer,Set<ProjectedEdge>>>> getProjected() {
        return projected;
    }

    public void setProjected(List<Map<Integer, Map<Integer,Set<ProjectedEdge>>>> projected) {
        this.projected = projected;
    }

    public long getNumProjections() {
        return numProjections;
    }

    public void setNumProjections(long numProjections) {
        this.numProjections = numProjections;
    }

    public Set<Integer> getGraphIds() {
        return graphIds;
    }

    public void setGraphIds(Set<Integer> graphIds) {
        this.graphIds = graphIds;
    }

    /**
     *
     * @return iterator on projections
     */
    public ProjectedIterator iterator() {
        return new ProjectedIterator(this);
    }

    /**
     *
     * @param gid database graph id
     * @return iterator on projections of the database graph
     */
    public ProjectedIterator iterator(int gid) {
        return new ProjectedIterator(this, gid);
    }

    /**
     *
     * @param gid database graph id
     * @param callbacks list of callbacks to be called by the iterator
     * @return iterator on projections of the database graph
     */
    public ProjectedIterator iterator(int gid, List<IProjectedIteratorCallback> callbacks) {
        return new ProjectedIterator(this, gid, callbacks);
    }

    /**
     * Consumer side of consumers/producers projections iterator implementation
     * @param queueSize maximal number of projections to be produced by the producers at any point of time
     * @param numProducers number of producer threads
     * @return projections consumer
     */
    public ProjectedIteratorConsumer iterator(int queueSize, int numProducers) {
        return new ProjectedIteratorConsumer(this, queueSize, numProducers);
    }

    /**
     * Consumer side of consumers/producers projections iterator implementation
     * @param queueSize maximal number of projections to be produced by the producers at any point of time
     * @param numProducers number of producer threads
     * @param callbacks list of callbacks to be called by producer
     * @return projections consumer
     */
    public ProjectedIteratorConsumer iterator(int queueSize, int numProducers, List<IProjectedIteratorCallback> callbacks) {
        return new ProjectedIteratorConsumer(this, queueSize, numProducers, callbacks);
    }

    /**
     * Consumer side of consumers/producers projections iterator implementation
     * @param queueSize maximal number of projections to be produced by the producers at any point of time
     * @param numProducers number of producer threads
     * @param gid database graph id, producers will produce projections from this graph only
     * @return projections consumer
     */
    public ProjectedIteratorConsumer iterator(int queueSize, int numProducers, int gid) {
        return new ProjectedIteratorConsumer(this, queueSize, numProducers, gid);
    }

    /**
     * Consumer side of consumers/producers projections iterator implementation
     * @param queueSize maximal number of projections to be produced by the producers at any point of time
     * @param numProducers number of producer threads
     * @param gid database graph id, producers will produce projections from this graph only
     * @param callbacks list of callbacks to be called by producer
     * @return projections consumer
     */
    public ProjectedIteratorConsumer iterator(int queueSize, int numProducers, int gid, List<IProjectedIteratorCallback> callbacks) {
        return new ProjectedIteratorConsumer(this, queueSize, numProducers, gid, callbacks);
    }

    /**
     *
     * @param prefixLength the length of projection prefix
     * @return iterator to output unique projections prefixes
     */
    public ProjectedPrefixIterator prefixIterator(int prefixLength) {
        return new ProjectedPrefixIterator(this, prefixLength);
    }

    /**
     *
     * @param gid database graph id, projections prefixes will be returned for this graph only
     * @param prefixLength the length of projection prefix
     * @return iterator to output unique projections prefixes
     */
    public ProjectedPrefixIterator prefixIterator(int gid, int prefixLength) {
        return new ProjectedPrefixIterator(this, gid, prefixLength);
    }

    /**
     * for each DFS code edge builds a set of all enumerated edges projected by that edge
     * @return list of sets of all enumerated edges projected by each edge in the DFS code
     */
    public List<Set<EdgeEnumeration>> buildKeys() {
        List<Set<EdgeEnumeration>> keys = new LinkedList<Set<EdgeEnumeration>>();
        for (Map<Integer, Map<Integer, Set<ProjectedEdge>>> index: projected) {
            Set<EdgeEnumeration> key = new HashSet<EdgeEnumeration>();
            for (Map<Integer, Set<ProjectedEdge>> vertexEdges: index.values()) {
                for (Set<ProjectedEdge> edges: vertexEdges.values()) {
                    key.addAll(edges.stream().map(ProjectedEdge::getEdgeEnumeration).collect(Collectors.toSet()));
                }
            }
            keys.add(key);
        }

        return keys;
    }

    /**
     *
     * @param label counted label
     * @param graphDatabase graphs database
     * @return count of distinct vertices in all projections labeled by 'label' parameter
     */
    public int verticesWithLabelCount(int label, List<DatabaseGraph> graphDatabase) {
        Map<Integer, Set<Integer>> graphVerticesWithLabel = new HashMap<Integer, Set<Integer>>();
        for (Integer graphId: graphIds) {
            graphVerticesWithLabel.put(graphId, new HashSet<Integer>());
        }

        ProjectedIterator iterator = iterator();
        while (iterator.hasNext()) {
            PDFSCompact pdfs = iterator.next();
            for (ProjectedEdge projectedEdge: pdfs.getProjectedEdges()) {
                int gid = projectedEdge.getEdgeEnumeration().getGid();
                DatabaseGraph g = graphDatabase.get(gid);
                Edge edge = projectedEdge.getEdgeEnumeration().getEdge();
                int label1 = g.getVLabel(edge.v1);
                if (label1 == label) {
                    graphVerticesWithLabel.get(gid).add(edge.v1);
                }
                int label2 = g.getVLabel(edge.v2);
                if (label2 == label) {
                    graphVerticesWithLabel.get(gid).add(edge.v2);
                }
            }
        }

        int count = 0;

        for (int gid: graphVerticesWithLabel.keySet()) {
            count += graphVerticesWithLabel.get(gid).size();
        }

        return count;
    }

    private void addProjectionEdge(ProjectedEdge projectedEdge, int i) {
        if (!projected.get(i).containsKey(projectedEdge.getEdgeEnumeration().getGid())) {
            projected.get(i).put(projectedEdge.getEdgeEnumeration().getGid(), new HashMap<Integer, Set<ProjectedEdge>>());
        }

        int vertex = projectedEdge.isReversed() ? projectedEdge.getEdgeEnumeration().getEdge().v2: projectedEdge.getEdgeEnumeration().getEdge().v1;

        if (!projected.get(i).get(projectedEdge.getEdgeEnumeration().getGid()).containsKey(vertex)) {
            projected.get(i).get(projectedEdge.getEdgeEnumeration().getGid()).put(vertex, new HashSet<ProjectedEdge>());
        }

        projected.get(i).get(projectedEdge.getEdgeEnumeration().getGid()).get(vertex).add(projectedEdge);
    }

    /**
     * adds a single projection of length one
     * @param projectedEdge edge the projection is composed of
     */
    public void addProjection(ProjectedEdge projectedEdge) {

        if (projected.size() == 0) {
            projected.add(new HashMap<Integer, Map<Integer, Set<ProjectedEdge>>>());
        }

        addProjectionEdge(projectedEdge, 0);

        graphIds.add(projectedEdge.getEdgeEnumeration().getGid());

        numProjections++;

        if (!numProjectionsInGraph.containsKey(projectedEdge.getEdgeEnumeration().getGid())) {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), 1l);
        }
        else {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), numProjectionsInGraph.get(projectedEdge.getEdgeEnumeration().getGid()) + 1);
        }
    }

    /**
     * adds projection edge at specific position
     * @param projectedEdge projection edge to add
     * @param index position of projection edge
     */
    public void addProjection(ProjectedEdge projectedEdge, int index) {
        addProjection(projectedEdge, index, 1);
    }

    /**
     * adds an edge which one or more projections have as the specific position
     * @param projectedEdge projection edge to add
     * @param index position of of projection(s) edge
     * @param projectionsNumber number of projections which share the edge
     */
    public void addProjection(ProjectedEdge projectedEdge, int index, long projectionsNumber) {

        addProjectionEdge(projectedEdge, index);

        graphIds.add(projectedEdge.getEdgeEnumeration().getGid());

        numProjections += projectionsNumber;

        if (!numProjectionsInGraph.containsKey(projectedEdge.getEdgeEnumeration().getGid())) {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), projectionsNumber);
        }
        else {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), numProjectionsInGraph.get(projectedEdge.getEdgeEnumeration().getGid()) + projectionsNumber);
        }
    }


    /**
     * adds a single projection which consists of projection of prefix up to the last edge and the last edge
     * @param pdfs projection prefix up to the last edge
     * @param projectedEdge last projected edge
     */
    public void addProjection(PDFSCompact pdfs, ProjectedEdge projectedEdge) {
        if (projected.size() == 0) {
            for (int i = 0; i < pdfs.getProjectedEdges().size() + 1; i++) {
                projected.add(new HashMap<Integer, Map<Integer, Set<ProjectedEdge>>>());
            }
        }

        for (int i = 0; i < pdfs.getProjectedEdges().size(); i++) {
            addProjectionEdge(pdfs.getProjectedEdges().get(i), i);
        }

        addProjectionEdge(projectedEdge, pdfs.getProjectedEdges().size());

        graphIds.add(projectedEdge.getEdgeEnumeration().getGid());

        numProjections++;

        if (!numProjectionsInGraph.containsKey(projectedEdge.getEdgeEnumeration().getGid())) {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), 1l);
        }
        else {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), numProjectionsInGraph.get(projectedEdge.getEdgeEnumeration().getGid()) + 1);
        }
    }

    /**
     * adds projections which consists of projections prefixes up to the last edge and the last edge which is shared by all projections
     * @param projectedEdges projections prefixes up the last edge
     * @param projectionsNumber number of projections
     * @param projectedEdge last projected edge shared by all projections
     */
    public void addProjection(List<Set<ProjectedEdge>> projectedEdges, long projectionsNumber, ProjectedEdge projectedEdge) {
        if (projected.size() == 0) {
            for (int i = 0; i < projectedEdges.size() + 1; i++) {
                projected.add(new HashMap<Integer, Map<Integer, Set<ProjectedEdge>>>());
            }
        }

        for (int i = 0; i < projectedEdges.size(); i++) {
            for (ProjectedEdge edge: projectedEdges.get(i)) {
                addProjectionEdge(edge, i);
            }
        }

        addProjectionEdge(projectedEdge, projectedEdges.size());

        graphIds.add(projectedEdge.getEdgeEnumeration().getGid());

        numProjections += projectionsNumber;

        if (!numProjectionsInGraph.containsKey(projectedEdge.getEdgeEnumeration().getGid())) {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), projectionsNumber);
        }
        else {
            numProjectionsInGraph.put(projectedEdge.getEdgeEnumeration().getGid(), numProjectionsInGraph.get(projectedEdge.getEdgeEnumeration().getGid()) + projectionsNumber);
        }
    }

    /**
     * adds projections
     * @param projectedCompact added projections
     */
    public void addProjection(ProjectedCompact projectedCompact) {
        for (int i = 0; i < projected.size(); i++) {
            Map<Integer, Map<Integer, Set<ProjectedEdge>>> otherIndex = projectedCompact.getProjected().get(i);
            Map<Integer, Map<Integer, Set<ProjectedEdge>>> myIndex = projected.get(i);

            for (Integer gid: otherIndex.keySet()) {
                if (!myIndex.containsKey(gid)) {
                    myIndex.put(gid, otherIndex.get(gid));
                }
                else {
                    Map<Integer, Set<ProjectedEdge>> otherVertexEdges = otherIndex.get(gid);
                    Map<Integer, Set<ProjectedEdge>> myVertexEdges = myIndex.get(gid);
                    for (Integer vertex: otherVertexEdges.keySet()) {
                        if (!myVertexEdges.containsKey(vertex)) {
                            myVertexEdges.put(vertex, otherVertexEdges.get(vertex));
                        }
                        else {
                            myVertexEdges.get(vertex).addAll(otherVertexEdges.get(vertex));
                        }
                    }
                }
            }
        }

        graphIds.addAll(projectedCompact.getGraphIds());

        numProjections += projectedCompact.getNumProjections();

        for (int graphId: projectedCompact.getNumProjectionsInGraph().keySet()) {
            if (numProjectionsInGraph.containsKey(graphId)) {
                numProjectionsInGraph.put(graphId, numProjectionsInGraph.get(graphId) + projectedCompact.getNumProjectionsInGraph().get(graphId));
            }
            else {
                numProjectionsInGraph.put(graphId, projectedCompact.getNumProjectionsInGraph().get(graphId));
            }
        }
    }

    public Map<Integer, Long> getNumProjectionsInGraph() {
        return numProjectionsInGraph;
    }

    public void setNumProjectionsInGraph(Map<Integer, Long> numProjectionsInGraph) {
        this.numProjectionsInGraph = numProjectionsInGraph;
    }

    public boolean isExtendedEquivalentOccurrence() {
        return extendedEquivalentOccurrence;
    }

    public void setExtendedEquivalentOccurrence(boolean extendedEquivalentOccurrence) {
        this.extendedEquivalentOccurrence = extendedEquivalentOccurrence;
    }

    public Set<ProjectedEdge> getProjections(int index) {
        Set<ProjectedEdge> projectedEdges = new HashSet<ProjectedEdge>();
        for (Map<Integer, Set<ProjectedEdge>> vertexEdges: projected.get(index).values()) {
            for (Set<ProjectedEdge> edges: vertexEdges.values()) {
                projectedEdges.addAll(edges);
            }
        }

        return projectedEdges;
    }

    /**
     *
     * @param i DFS index of projected edges
     * @return enumerations of all 'to' projected vertices from all projected edges at position specified by the index
     */
    public Set<VertexEnumeration> getDFSedgeAtToVerticesEnumerations(int i) {
        Set<VertexEnumeration> verticesEnumerations = new HashSet<VertexEnumeration>();
        Map<Integer, Map<Integer, Set<ProjectedEdge>>> index = projected.get(i);
        for (Map<Integer, Set<ProjectedEdge>> vertexEdges: index.values()) {
            for (Set<ProjectedEdge> edges: vertexEdges.values()) {
                for (ProjectedEdge projectedEdge: edges) {
                    verticesEnumerations.add(new VertexEnumeration(projectedEdge.getEdgeEnumeration().getGid(), projectedEdge.isReversed() ? projectedEdge.getEdgeEnumeration().getEdge().v1 : projectedEdge.getEdgeEnumeration().getEdge().v2));
                }
            }
         }

        return verticesEnumerations;
    }

    /**
     * enumeration of graphs database vertices
     */
    public static class VertexEnumeration {
        /**
         * database graph id
         */
        private int gid;
        /**
         * database graph vertex
         */
        private int vertex;

        public VertexEnumeration(int gid, int vertex) {
            this.gid = gid;
            this.vertex = vertex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VertexEnumeration that = (VertexEnumeration) o;
            return gid == that.gid && vertex == that.vertex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(gid, vertex);
        }
    }
}
