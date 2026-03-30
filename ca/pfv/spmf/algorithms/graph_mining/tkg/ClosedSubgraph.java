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
 * This is a closed subgraph. It stores (1) closed subgraph projections into database graphs
 * (2) list of closed subgraph projections into database graphs mapped by database graph id
 * (3) id of the database graph with minimum number of projections.
 * (4)the MNI of the subgraph
 *
 * @see AlgoCGSPANAbstract
 * @author Shaul Zevin
 */
public class ClosedSubgraph extends FrequentSubgraph {

    /** list of closed subgraph projections into database graphs */
    private ProjectedCompact projected;

    /** set of closed subgraph projections into database graphs in a form of set of projected edges mapped by database graph id
     *  this data structure maps internally isomorphic projections in a database graph into a single set of edges
     * */
    private Map<Integer, Set<Set<EdgeEnumeration>>> projectionsSetsInDatabaseGraph = null;

    /** id of the database graph with minimum number of projections */
    private Integer exampleGid;

    /** support or MNI */
    private int thresholdValue;


    /**
     * Constructor
     *
     * @param dfsCode        a dfs code
     * @param setOfGraphsIDs the ids of graphs where the subgraph appears
     * @param support        the support of the subgraph
     */
    public ClosedSubgraph(DFSCode dfsCode, Set<Integer> setOfGraphsIDs, int support, ProjectedCompact projected, int thresholdValue) {
        super(dfsCode, setOfGraphsIDs, support);
        this.projected = projected;
        this.thresholdValue = thresholdValue;
        // find gid with minimal number of occurrences
        findExampleGid();
    }

    /**
     * finds the graph id with minimum number of subgraph projections
     */
    private void findExampleGid() {
        Map<Integer, Integer> graphProjectionsCount = new HashMap<Integer, Integer>();

        Set<ProjectedEdge> edges = projected.getProjections(0);
        for (ProjectedEdge edge: edges) {
            if (!graphProjectionsCount.containsKey(edge.getEdgeEnumeration().getGid())) {
                graphProjectionsCount.put(edge.getEdgeEnumeration().getGid(), 0);
            }

            graphProjectionsCount.put(edge.getEdgeEnumeration().getGid(), graphProjectionsCount.get(edge.getEdgeEnumeration().getGid()) + 1);
        }

        for (int i = 1; i < projected.getProjected().size(); i++) {
            edges = projected.getProjections(i);
            Map<Integer, Integer> tempGraphProjectionsCount = new HashMap<Integer, Integer>();

            for (ProjectedEdge edge: edges) {
                if (!tempGraphProjectionsCount.containsKey(edge.getEdgeEnumeration().getGid())) {
                    tempGraphProjectionsCount.put(edge.getEdgeEnumeration().getGid(), 0);
                }

                tempGraphProjectionsCount.put(edge.getEdgeEnumeration().getGid(), tempGraphProjectionsCount.get(edge.getEdgeEnumeration().getGid()) + 1);
            }

            for (int gid: graphProjectionsCount.keySet()) {
                if (tempGraphProjectionsCount.get(gid) > graphProjectionsCount.get(gid)) {
                    graphProjectionsCount.put(gid, tempGraphProjectionsCount.get(gid));
                }
            }
        }

        exampleGid = null;
        int minProjections = 0;
        for (int gid: graphProjectionsCount.keySet()) {
            if (exampleGid == null) {
                exampleGid = gid;
                minProjections = graphProjectionsCount.get(gid);
                continue;
            }

            if (graphProjectionsCount.get(gid) < minProjections) {
                exampleGid = gid;
                minProjections = graphProjectionsCount.get(gid);
            }
        }

    }

    /**
     * builds set of projected edges enumerations for each edge in the closed subgraph
     * @return list of sets of projected edges enumerations
     */
    public List<Set<EdgeEnumeration>> getKeys() {
        return projected.buildKeys();
    }

    /**
     * checks if frequent subgraph with projections has equivalent occurrence with this closed subgraph
     * @param otherSetOfGraphsIDs ids of database graphs where other subgraph has projection
     * @param otherSupport support of other subgraph
     * @param otherProjected projections of the other subgraph
     * @return isomorphism of frequent subgraph into this closed subgraph if equivalent occurrence exists, null otherwise
     */
    public Map<Integer, Integer> checkEquivalentOccurrence(Set<Integer> otherSetOfGraphsIDs, int otherSupport, ProjectedCompact otherProjected) {
        if (otherSupport > support) {
            return null;
        }
        if (!setOfGraphsIDs.equals(otherSetOfGraphsIDs)) {
            return null;
        }

        if (otherProjected.getProjected().size() > projected.getProjected().size()) {
            return null;
        }

        if (otherProjected.getNumProjections() > projected.getNumProjections()) {
            return null;
        }

        // find all possible isomorphisms of projections of other subgraph into projections of this closed graph in database graph with id exampleGid
        List<Map<Integer, Integer>> possibleIsomorphisms = findPossibleIsomorphisms(otherProjected.iterator(ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() * 2, ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() / 2, exampleGid));

        //System.out.println("number of possible isomorphisms " + possibleIsomorphisms + " for dfs code " + otherProjected.getDfsCode().toString());
        Map<Integer, Integer> isomorphism = null;
        boolean isomorphismFound = false;

        // check if one possible isomorphism is valid for projections in the rest of database graphs
        for (Map<Integer, Integer> possibleIsomorphism: possibleIsomorphisms) {
            isomorphismFound = true;
            for (int gid:  otherProjected.getGraphIds()) {
                ProjectedIteratorConsumer otherIterator = otherProjected.iterator(ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() * 2, ThreadPool.getProjectedIteratorProducersInstance().getThreadCount(), gid);
                while (otherIterator.hasNext()) {
                    List<ProjectedEdge> otherProjectedEdges = otherIterator.next().getProjectedEdges();

                    // build iterator filter
                    List<EdgeEnumeration> filter = new ArrayList<EdgeEnumeration>();
                    for (int i = 0; i < projected.getProjected().size(); i++) {
                        filter.add(null);
                    }
                    for (int otherIndex: possibleIsomorphism.keySet()) {
                        int myIndex = possibleIsomorphism.get(otherIndex);
                        filter.set(myIndex, otherProjectedEdges.get(otherIndex).getEdgeEnumeration());
                    }

                    FilterCallback filterCallback = new FilterCallback(filter);
                    List<IProjectedIteratorCallback> callbacks = new LinkedList<IProjectedIteratorCallback>();
                    callbacks.add(filterCallback);
                    ProjectedIterator myIterator = projected.iterator(gid, callbacks);
                    if (myIterator.hasNext()) {
                        isomorphismFound = true;
                    }
                    else {
                        isomorphismFound = false;
                    }

                    if (!isomorphismFound) {
                        break;
                    }
                }

                otherIterator.stop();

                if (!isomorphismFound) {
                    break;
                }
            }

            if (isomorphismFound) {
                isomorphism = possibleIsomorphism;
                break;
            }
        }

        return isomorphism;
    }

    /**
     * finds all possible isomorphisms from projections passed in the parameter into this closed subgraph projections in database graph with id exampleGid
     * @param edgesProjectionListIterator projections to be checked for isomorphism(s)
     * @return list of isomorphisms
     */
    private List<Map<Integer, Integer>> findPossibleIsomorphisms(ProjectedIteratorConsumer edgesProjectionListIterator) {
        //Set<Map<Integer, Integer>> isomorphisms = new HashSet<Map<Integer, Integer>>();
        List<Map<Integer, Integer>> isomorphisms = new LinkedList();

        if (!edgesProjectionListIterator.hasNext()) {
            return new LinkedList<Map<Integer, Integer>>(isomorphisms);
        }
        List<ProjectedEdge> otherProjectedEdges = edgesProjectionListIterator.next().getProjectedEdges();
        Map<EdgeEnumeration, Integer> otherProjectedEdgesIndices = new HashMap<EdgeEnumeration, Integer>();
        for (int i = 0; i < otherProjectedEdges.size(); i++) {
            ProjectedEdge otherProjectedEdge = otherProjectedEdges.get(i);
            otherProjectedEdgesIndices.put(otherProjectedEdge.getEdgeEnumeration(), i);
        }

        ProjectedIteratorConsumer myEdgesProjectionListIterator = projected.iterator(ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() * 2, ThreadPool.getProjectedIteratorProducersInstance().getThreadCount() / 2, exampleGid);
        while (myEdgesProjectionListIterator.hasNext()) {
            PDFSCompact myPDFS = myEdgesProjectionListIterator.next();
            List<ProjectedEdge> myProjectedEdges = myPDFS.getProjectedEdges();
            Map<Integer, Integer> isomorphism = new HashMap<Integer, Integer>();
            for (int j = 0; j < myProjectedEdges.size(); j++) {
                ProjectedEdge myProjectedEdge = myProjectedEdges.get(j);
                // if edges match, update isomorphism
                if (otherProjectedEdgesIndices.containsKey(myProjectedEdge.getEdgeEnumeration())) {
                    isomorphism.put(otherProjectedEdgesIndices.get(myProjectedEdge.getEdgeEnumeration()), j);
                }
            }

            // all edges were matched
            if (isomorphism.size() == otherProjectedEdges.size()) {
                isomorphisms.add(isomorphism);
            }
        }

        LinkedList<Map<Integer, Integer>> uniqueIsomorphisms = new LinkedList<Map<Integer, Integer>>();

        for (Map<Integer, Integer> isomorphism: isomorphisms) {
            boolean unique = true;

            for (Map<Integer, Integer> uniqueIsomorphism: uniqueIsomorphisms) {
                Set<Integer> isomorphismValues = new HashSet<Integer>(isomorphism.values());
                Set<Integer> uniqueIsomorphismValues = new HashSet<Integer>(uniqueIsomorphism.values());
                if (isomorphismValues.equals(uniqueIsomorphismValues)) {
                    unique = false;
                    break;
                }
            }

            if (unique) {
                uniqueIsomorphisms.add(isomorphism);
            }
        }

        // sort isomorphisms to minimize the closed graph dfs code maximum index in the isomorphism
        // this will provide the correctness of early termination failure detection
        Collections.sort(uniqueIsomorphisms, new Comparator<Map<Integer, Integer>>() {

            public int compare(Map<Integer, Integer> o1, Map<Integer, Integer> o2) {
                // compare two instance of `Score` and return `int` as result.
                return Collections.max(o1.values()).compareTo(Collections.max(o2.values()));
            }
        });



        edgesProjectionListIterator.stop();

        return uniqueIsomorphisms;
    }

    public Map<Integer, Set<Set<EdgeEnumeration>>> getProjectionsSetsInDatabaseGraph() {
        if (projectionsSetsInDatabaseGraph != null) {
            return projectionsSetsInDatabaseGraph;
        }

        projectionsSetsInDatabaseGraph = new HashMap<Integer, Set<Set<EdgeEnumeration>>>();

        for (Integer gid: projected.getGraphIds()) {
            projectionsSetsInDatabaseGraph.put(gid, new HashSet<Set<EdgeEnumeration>>());
        }

        for (int i = 0; i < projected.getProjected().size(); i++) {
            Set<ProjectedEdge> edges = projected.getProjections(i);
            Map<Integer, Set<EdgeEnumeration>> graphEdgeProjections = new HashMap<Integer, Set<EdgeEnumeration>>();
            for (Integer gid: projected.getGraphIds()) {
                graphEdgeProjections.put(gid, new HashSet<EdgeEnumeration>());
            }
            for (ProjectedEdge edge: edges) {
                graphEdgeProjections.get(edge.getEdgeEnumeration().getGid()).add(edge.getEdgeEnumeration());
            }

            for (Integer gid: projected.getGraphIds()) {
                projectionsSetsInDatabaseGraph.get(gid).add(graphEdgeProjections.get(gid));
            }
        }

        return projectionsSetsInDatabaseGraph;
    }

    /**
     * checks if closed graph can be extended into anotherClosedGraph by using more than one isomorphism
     *
     * @param otherClosedSubgraph
     * @return true if such extension is possible, false otherwise
     */

    public boolean isExtendedWithMultipleIsomorphisms(ClosedSubgraph otherClosedSubgraph) {
        if (otherClosedSubgraph.getProjected().getProjected().size() <= getProjected().getProjected().size()) {
            return false;
        }

        if (!otherClosedSubgraph.getProjected().getGraphIds().equals(getProjected().getGraphIds())) {
            return false;
        }

        projectionsSetsInDatabaseGraph = getProjectionsSetsInDatabaseGraph();
        Map<Integer, Set<Set<EdgeEnumeration>>> otherProjectionsSetsInDatabaseGraph = otherClosedSubgraph.getProjectionsSetsInDatabaseGraph();

        for (Integer gid: projectionsSetsInDatabaseGraph.keySet()) {
            for (Set<EdgeEnumeration> projection: projectionsSetsInDatabaseGraph.get(gid)) {
                boolean extendable = false;
                for (Set<EdgeEnumeration> otherProjection: otherProjectionsSetsInDatabaseGraph.get(gid)) {
                    if (otherProjection.containsAll(projection)) {
                        extendable = true;
                        break;
                    }
                }

                if (!extendable) {
                    return false;
                }
            }
        }

        return true;
    }

    public ProjectedCompact getProjected() {
        return projected;
    }

    public int getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(int thresholdValue) {
        this.thresholdValue = thresholdValue;
    }
}
