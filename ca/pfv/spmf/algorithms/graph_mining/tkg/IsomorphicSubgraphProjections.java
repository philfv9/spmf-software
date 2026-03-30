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
 * Contains projections of isomorphic subgraphs
 * Rightmost path edges may belong to isomorphic subgraph. In such case projections are indexed to provide efficient forward and backward extensions.
 *
 * @see IsomorphicSubgraph
 * @author Shaul Zevin
 */
public class IsomorphicSubgraphProjections extends PDFSCompact {
    // projections which have particular database vertex on a rightmost path index
    // those projections will be used for the forward extension
    private Map<Integer, Map<Integer, List<Set<ProjectedEdge>>>> vertexRightMostPathIndexProjections = new HashMap<Integer, Map<Integer, List<Set<ProjectedEdge>>>>();
    // number of projections which have vertex on a rightmost path index
    private Map<Integer, Map<Integer, Integer>> vertexRightMostPathIndexProjectionsCount = new HashMap<Integer, Map<Integer, Integer>>();

    // projections which have particular rightmost database vertex and also have particular database vertex on a rightmost path index
    // those projections will be used for the backward extension
    private Map<Integer, Map<Integer, Map<Integer, List<Set<ProjectedEdge>>>>> rightMostVertexVertexRightMostPathIndexProjections = new HashMap<Integer, Map<Integer, Map<Integer, List<Set<ProjectedEdge>>>>>();
    // number of projections from rightmost vertex to vertex on a rightmost path index
    private Map<Integer, Map<Integer, Map<Integer, Integer>>> rightMostVertexVertexRightMostPathIndexProjectionsCount = new HashMap<Integer, Map<Integer, Map<Integer, Integer>>>();

    // projections not from vertices on a rightmost path
    // those projections will be used for all extensions
    private List<Set<ProjectedEdge>> projections = new ArrayList<Set<ProjectedEdge>>();
    // number of projections
    private int numProjections = 0;

    // DFS code indices of isomorphic subgraph edges
    private Set<Integer> isomorphismEdgesIndices;

    public IsomorphicSubgraphProjections(DatabaseGraph databaseGraph, List<ProjectedEdge> projectedEdges, List<Vertex> vertices, Set<Integer> isomorphismEdgesIndices) {
        super(databaseGraph, projectedEdges, vertices);
        this.isomorphismEdgesIndices = new HashSet<Integer>(isomorphismEdgesIndices);
        addProjection(projectedEdges);
    }

    public Map<Integer, Map<Integer, List<Set<ProjectedEdge>>>> getVertexRightMostPathIndexProjections() {
        return vertexRightMostPathIndexProjections;
    }

    public void setVertexRightMostPathIndexProjections(Map<Integer, Map<Integer, List<Set<ProjectedEdge>>>> vertexRightMostPathIndexProjections) {
        this.vertexRightMostPathIndexProjections = vertexRightMostPathIndexProjections;
    }

    public Map<Integer, Map<Integer, Integer>> getVertexRightMostPathIndexProjectionsCount() {
        return vertexRightMostPathIndexProjectionsCount;
    }

    public void setVertexRightMostPathIndexProjectionsCount(Map<Integer, Map<Integer, Integer>> vertexRightMostPathIndexProjectionsCount) {
        this.vertexRightMostPathIndexProjectionsCount = vertexRightMostPathIndexProjectionsCount;
    }

    public List<Set<ProjectedEdge>> getProjections() {
        return projections;
    }

    public void setProjections(List<Set<ProjectedEdge>> projections) {
        this.projections = projections;
    }

    public int getNumProjections() {
        return numProjections;
    }

    public void setNumProjections(int numProjections) {
        this.numProjections = numProjections;
    }

    public Map<Integer, Map<Integer, Map<Integer, List<Set<ProjectedEdge>>>>> getRightMostVertexVertexRightMostPathIndexProjections() {
        return rightMostVertexVertexRightMostPathIndexProjections;
    }

    public void setRightMostVertexVertexRightMostPathIndexProjections(Map<Integer, Map<Integer, Map<Integer, List<Set<ProjectedEdge>>>>> rightMostVertexVertexRightMostPathIndexProjections) {
        this.rightMostVertexVertexRightMostPathIndexProjections = rightMostVertexVertexRightMostPathIndexProjections;
    }

    public Map<Integer, Map<Integer, Map<Integer, Integer>>> getRightMostVertexVertexRightMostPathIndexProjectionsCount() {
        return rightMostVertexVertexRightMostPathIndexProjectionsCount;
    }

    public void setRightMostVertexVertexRightMostPathIndexProjectionsCount(Map<Integer, Map<Integer, Map<Integer, Integer>>> rightMostVertexVertexRightMostPathIndexProjectionsCount) {
        this.rightMostVertexVertexRightMostPathIndexProjectionsCount = rightMostVertexVertexRightMostPathIndexProjectionsCount;
    }

    public Set<Integer> getIsomorphismEdgesIndices() {
        return isomorphismEdgesIndices;
    }

    public void setIsomorphismEdgesIndices(Set<Integer> isomorphismEdgesIndices) {
        this.isomorphismEdgesIndices = isomorphismEdgesIndices;
    }

    /**
     * adds a single projection
     * @param projection projection to be added
     */
    public void addProjection(List<ProjectedEdge> projection) {
        if (projections.size() == 0) {
            for (int i = 0; i < projection.size(); i++) {
                projections.add(new HashSet<ProjectedEdge>());
            }
        }

        for (int i = 0; i < projection.size(); i++) {
            projections.get(i).add(projection.get(i));
        }

        numProjections++;
    }

    /**
     * adds a single projection which exists when database vertex is projected from a rightmost path position specified by index
     * @param vertex projected vertex
     * @param rightMostPathIndex rightmost path position
     * @param projection projection to add
     */
    public void addVertexRightMostPathIndexProjection(int vertex, int rightMostPathIndex, List<ProjectedEdge> projection) {
        if (!vertexRightMostPathIndexProjections.containsKey(vertex)) {
            vertexRightMostPathIndexProjections.put(vertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
            vertexRightMostPathIndexProjectionsCount.put(vertex, new HashMap<Integer, Integer>());
        }

        if (!vertexRightMostPathIndexProjections.get(vertex).containsKey(rightMostPathIndex)) {
            vertexRightMostPathIndexProjections.get(vertex).put(rightMostPathIndex, new ArrayList<Set<ProjectedEdge>>());
            vertexRightMostPathIndexProjectionsCount.get(vertex).put(rightMostPathIndex, 0);
        }

        if (vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).size() == 0) {
            for (int i = 0; i < projection.size(); i++) {
                vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).add(new HashSet<ProjectedEdge>());
            }
        }

        for (int i = 0; i < projection.size(); i++) {
            vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).get(i).add(projection.get(i));
        }

        vertexRightMostPathIndexProjectionsCount.get(vertex).put(rightMostPathIndex, vertexRightMostPathIndexProjectionsCount.get(vertex).get(rightMostPathIndex) + 1);
    }

    /**
     * adds a single projection which exists when rightMostVertex database vertex is projected from the rightmost position
     * and database vertex is projected from a rightmost path position specified by index
     * @param rightMostVertex vertex projected from the rightmost position
     * @param vertex vertex projected from rightMostPathIndex position
     * @param rightMostPathIndex rightmost path position
     * @param projection projection to add
     */
    public void addRightMostVertexVertexRightMostPathIndexProjection(int rightMostVertex, int vertex, int rightMostPathIndex, List<ProjectedEdge> projection) {
        if (!rightMostVertexVertexRightMostPathIndexProjections.containsKey(rightMostVertex)) {
            rightMostVertexVertexRightMostPathIndexProjections.put(rightMostVertex, new HashMap<Integer, Map<Integer, List<Set<ProjectedEdge>>>>());
            rightMostVertexVertexRightMostPathIndexProjectionsCount.put(rightMostVertex, new HashMap<Integer, Map<Integer, Integer>>());
        }

        if (!rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).containsKey(vertex)) {
            rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).put(vertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
            rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).put(vertex, new HashMap<Integer, Integer>());
        }

        if (!rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).containsKey(rightMostPathIndex)) {
            rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).put(rightMostPathIndex, new ArrayList<Set<ProjectedEdge>>());
            rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(vertex).put(rightMostPathIndex, 0);
        }

        if (rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).get(rightMostPathIndex).size() == 0) {
            for (int i = 0; i < projection.size(); i++) {
                rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).get(rightMostPathIndex).add(new HashSet<ProjectedEdge>());
            }
        }

        for (int i = 0; i < projection.size(); i++) {
            rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).get(rightMostPathIndex).get(i).add(projection.get(i));
        }

        rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(vertex).put(rightMostPathIndex, rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(vertex).get(rightMostPathIndex) + 1);
    }

    /**
     * merges projections from two isomorphic graphs
     * @param other isomorphic projections to be merged with this isomorphic projections
     * @param rightMostIndex index of the rightmost vertex
     * @return merged projections
     */
    public IsomorphicSubgraphProjections merge(IsomorphicSubgraphProjections other, int rightMostIndex) {
        IsomorphicSubgraphProjections merged = new IsomorphicSubgraphProjections(databaseGraph, projectedEdges, vertices, new HashSet<Integer>());

        merged.getIsomorphismEdgesIndices().addAll(isomorphismEdgesIndices);
        merged.getIsomorphismEdgesIndices().addAll(other.getIsomorphismEdgesIndices());

        // merge projections
        for (int i = 0; i < projections.size(); i++) {
            Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();
            if (isomorphismEdgesIndices.contains(i)) {
                mergedProjection.addAll(projections.get(i));
            }
            else {
                if (other.getIsomorphismEdgesIndices().contains(i)) {
                    mergedProjection.addAll(other.getProjections().get(i));
                }
                else {
                    mergedProjection.add(projectedEdges.get(i));
                }
            }

            merged.getProjections().get(i).addAll(mergedProjection);
        }

        merged.setNumProjections(numProjections * other.getNumProjections());

        // merge projections which have particular vertex on a rightmost path index
        for (int vertex : vertexRightMostPathIndexProjections.keySet()) {
            merged.vertexRightMostPathIndexProjections.put(vertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
            merged.vertexRightMostPathIndexProjectionsCount.put(vertex, new HashMap<Integer, Integer>());
            for (int rightMostPathIndex : vertexRightMostPathIndexProjections.get(vertex).keySet()) {
                merged.vertexRightMostPathIndexProjections.get(vertex).put(rightMostPathIndex, new ArrayList<Set<ProjectedEdge>>());
                if (other.vertexRightMostPathIndexProjections.containsKey(vertex) && other.vertexRightMostPathIndexProjections.get(vertex).containsKey(rightMostPathIndex)) {
                    merged.vertexRightMostPathIndexProjectionsCount.get(vertex).put(rightMostPathIndex, vertexRightMostPathIndexProjectionsCount.get(vertex).get(rightMostPathIndex) * other.vertexRightMostPathIndexProjectionsCount.get(vertex).get(rightMostPathIndex));
                    for (int i = 0; i < projections.size(); i++) {

                        Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();
                        if (isomorphismEdgesIndices.contains(i)) {
                            mergedProjection.addAll(vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).get(i));
                        }
                        else {
                            if (other.getIsomorphismEdgesIndices().contains(i)) {
                                mergedProjection.addAll(other.vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).get(i));
                            } else {
                                mergedProjection.add(projectedEdges.get(i));
                            }
                        }

                        merged.vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).add(mergedProjection);
                    }
                } else {
                    merged.vertexRightMostPathIndexProjectionsCount.get(vertex).put(rightMostPathIndex, vertexRightMostPathIndexProjectionsCount.get(vertex).get(rightMostPathIndex) * other.getNumProjections());
                    for (int i = 0; i < projections.size(); i++) {
                        Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();
                        if (isomorphismEdgesIndices.contains(i)) {
                            mergedProjection.addAll(vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).get(i));
                        }
                        else {
                            if (other.getIsomorphismEdgesIndices().contains(i)) {
                                mergedProjection.addAll(other.projections.get(i));
                            } else {
                                mergedProjection.add(projectedEdges.get(i));
                            }
                        }

                        merged.vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).add(mergedProjection);
                    }
                }
            }
        }

        for (int vertex : other.vertexRightMostPathIndexProjections.keySet()) {
            if (!merged.vertexRightMostPathIndexProjections.containsKey(vertex)) {
                merged.vertexRightMostPathIndexProjections.put(vertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
                merged.vertexRightMostPathIndexProjectionsCount.put(vertex, new HashMap<Integer, Integer>());
            }
            for (int rightMostPathIndex : other.vertexRightMostPathIndexProjections.get(vertex).keySet()) {
                if (merged.vertexRightMostPathIndexProjections.get(vertex).keySet().contains(rightMostPathIndex)) {
                    continue;
                }
                merged.vertexRightMostPathIndexProjections.get(vertex).put(rightMostPathIndex, new ArrayList<Set<ProjectedEdge>>());

                merged.vertexRightMostPathIndexProjectionsCount.get(vertex).put(rightMostPathIndex, other.vertexRightMostPathIndexProjectionsCount.get(vertex).get(rightMostPathIndex) * getNumProjections());
                for (int i = 0; i < projections.size(); i++) {
                    Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();
                    if (isomorphismEdgesIndices.contains(i)) {
                        mergedProjection.addAll(projections.get(i));
                    }
                    else {
                        if (other.getIsomorphismEdgesIndices().contains(i)) {
                            mergedProjection.addAll(other.vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).get(i));
                        } else {
                            mergedProjection.add(projectedEdges.get(i));
                        }
                    }

                    merged.vertexRightMostPathIndexProjections.get(vertex).get(rightMostPathIndex).add(mergedProjection);
                }
            }
        }

        // merge projections which have particular rightmost vertex projection and also have particular database vertex projected from a rightmost path index
        for (int rightMostVertex : rightMostVertexVertexRightMostPathIndexProjections.keySet()) {
            merged.rightMostVertexVertexRightMostPathIndexProjections.put(rightMostVertex, new HashMap<Integer, Map<Integer, List<Set<ProjectedEdge>>>>());
            merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.put(rightMostVertex, new HashMap<Integer, Map<Integer, Integer>>());
            for (int vertex : rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).keySet()) {
                merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).put(vertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
                merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).put(vertex, new HashMap<Integer, Integer>());
                for (int rightMostPathIndex : rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).keySet()) {
                    merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).put(rightMostPathIndex, new ArrayList<Set<ProjectedEdge>>());
                    merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(vertex).put(rightMostPathIndex, rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(vertex).get(rightMostPathIndex) * other.getNumProjections());
                    for (int i = 0; i < projections.size(); i++) {

                        Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();
                        if (isomorphismEdgesIndices.contains(i)) {
                            mergedProjection.addAll(rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).get(rightMostPathIndex).get(i));
                        }
                        else {
                            if (other.getIsomorphismEdgesIndices().contains(i)) {
                                mergedProjection.addAll(other.projections.get(i));
                            } else {
                                mergedProjection.add(projectedEdges.get(i));
                            }
                        }

                        merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).get(rightMostPathIndex).add(mergedProjection);
                    }
                }
            }

            // backward projections to other
            List<Set<ProjectedEdge>> rightMostProjections = vertexRightMostPathIndexProjections.get(rightMostVertex).get(rightMostIndex);
            for (int otherVertex: other.vertexRightMostPathIndexProjections.keySet()) {
                merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).put(otherVertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
                merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).put(otherVertex, new HashMap<Integer, Integer>());
                for (int otherIndex: other.vertexRightMostPathIndexProjections.get(otherVertex).keySet()) {
                    merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(otherVertex).put(otherIndex, new ArrayList<Set<ProjectedEdge>>());
                    merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(otherVertex).put(otherIndex, vertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(rightMostIndex) * other.vertexRightMostPathIndexProjectionsCount.get(otherVertex).get(otherIndex));
                    for (int i = 0; i < projections.size(); i++) {
                        Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();

                        if (isomorphismEdgesIndices.contains(i)) {
                            mergedProjection.addAll(vertexRightMostPathIndexProjections.get(rightMostVertex).get(rightMostIndex).get(i));
                        }
                        else {
                            if (other.getIsomorphismEdgesIndices().contains(i)) {
                                mergedProjection.addAll(other.vertexRightMostPathIndexProjections.get(otherVertex).get(otherIndex).get(i));
                            } else {
                                mergedProjection.add(projectedEdges.get(i));
                            }
                        }

                        merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(otherVertex).get(otherIndex).add(mergedProjection);
                    }
                }
            }
        }

        for (int rightMostVertex : other.rightMostVertexVertexRightMostPathIndexProjections.keySet()) {
            if (!merged.rightMostVertexVertexRightMostPathIndexProjections.keySet().contains(rightMostVertex)) {
                merged.rightMostVertexVertexRightMostPathIndexProjections.put(rightMostVertex, new HashMap<Integer, Map<Integer, List<Set<ProjectedEdge>>>>());
                merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.put(rightMostVertex, new HashMap<Integer, Map<Integer, Integer>>());
            }
            for (int vertex : other.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).keySet()) {
                merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).put(vertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
                merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).put(vertex, new HashMap<Integer, Integer>());
                for (int rightMostPathIndex : other.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).keySet()) {
                    merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).put(rightMostPathIndex, new ArrayList<Set<ProjectedEdge>>());
                    merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(vertex).put(rightMostPathIndex, other.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(vertex).get(rightMostPathIndex) * getNumProjections());
                    for (int i = 0; i < projections.size(); i++) {


                        Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();

                        if (isomorphismEdgesIndices.contains(i)) {
                            mergedProjection.addAll(projections.get(i));
                        }
                        else {
                            if (other.getIsomorphismEdgesIndices().contains(i)) {
                                mergedProjection.addAll(other.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).get(rightMostPathIndex).get(i));
                            } else {
                                mergedProjection.add(projectedEdges.get(i));
                            }
                        }

                        merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(vertex).get(rightMostPathIndex).add(mergedProjection);
                    }
                }
            }

            // backward projections to other
            List<Set<ProjectedEdge>> rightMostProjections = other.vertexRightMostPathIndexProjections.get(rightMostVertex).get(rightMostIndex);
            for (int myVertex: vertexRightMostPathIndexProjections.keySet()) {
                if (other.rightMostVertexVertexRightMostPathIndexProjections != null && other.rightMostVertexVertexRightMostPathIndexProjections.containsKey(myVertex)) {
                    continue;
                }
                merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).put(myVertex, new HashMap<Integer, List<Set<ProjectedEdge>>>());
                merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).put(myVertex, new HashMap<Integer, Integer>());
                for (int myIndex: vertexRightMostPathIndexProjections.get(myVertex).keySet()) {
                    merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(myVertex).put(myIndex, new ArrayList<Set<ProjectedEdge>>());
                    merged.rightMostVertexVertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(myVertex).put(myIndex, other.vertexRightMostPathIndexProjectionsCount.get(rightMostVertex).get(rightMostIndex) * vertexRightMostPathIndexProjectionsCount.get(myVertex).get(myIndex));
                    for (int i = 0; i < projections.size(); i++) {


                        Set<ProjectedEdge> mergedProjection = new HashSet<ProjectedEdge>();

                        if (isomorphismEdgesIndices.contains(i)) {
                            mergedProjection.addAll(vertexRightMostPathIndexProjections.get(myVertex).get(myIndex).get(i));
                        }
                        else {
                            if (other.getIsomorphismEdgesIndices().contains(i)) {
                                mergedProjection.addAll(other.vertexRightMostPathIndexProjections.get(rightMostVertex).get(rightMostIndex).get(i));
                            } else {
                                mergedProjection.add(projectedEdges.get(i));
                            }
                        }

                        merged.rightMostVertexVertexRightMostPathIndexProjections.get(rightMostVertex).get(myVertex).get(myIndex).add(mergedProjection);
                    }
                }
            }
        }

        return merged;
    }

    /**
     * checks if isomorphic projections include rightmost vertex
     * @param rightMost rightmost vertex index
     * @return true if rightmost vertex is included, false otherwise
     */
    public boolean containsRightmost(int rightMost) {
        for (int v: vertexRightMostPathIndexProjections.keySet()) {
            if (vertexRightMostPathIndexProjections.get(v).containsKey(rightMost)) {
                return true;
            }
        }

        return false;
    }
}
