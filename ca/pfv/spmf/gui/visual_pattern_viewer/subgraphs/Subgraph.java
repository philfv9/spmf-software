package ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import ca.pfv.spmf.gui.visual_pattern_viewer.Pattern;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information.
 */
/**
 * Represents a single subgraph pattern, its vertices, edges, the list of graph
 * identifiers in which it appears, and evaluation measures with their
 * corresponding values.
 *
 * @author Philippe Fournier-Viger
 */
public class Subgraph implements Pattern {

    /** The graph identifier as found in the file (e.g. "0", "1", …). */
    private final String graphId;

    /** The ordered list of vertices in this subgraph. */
    private final List<Vertex> vertices;

    /** The ordered list of edges in this subgraph. */
    private final List<Edge> edges;

    /**
     * Identifiers of the graphs in the database that contain this subgraph
     * (parsed from the {@code x} line).
     */
    private final List<String> containingGraphIds;

    /**
     * Map of measure names to their string values (e.g., "SUP" → "3").
     */
    private final Map<String, String> measures;

    /** Cached string representation (computed once at construction time). */
    private final String cachedToString;
    

    /**
     * A vertex in the subgraph, identified by an integer id and carrying a string
     * label.
     */
    public static class Vertex {
        /** Vertex id (as it appears in the file). */
        private final int id;
        /** Vertex label. */
        private final String label;

        /**
         * Constructs a Vertex.
         *
         * @param id    the vertex identifier
         * @param label the vertex label
         */
        public Vertex(int id, String label) {
            this.id = id;
            this.label = label;
        }

        /**
         * @return the vertex identifier
         */
        public int getId() {
            return id;
        }

        /**
         * @return the vertex label
         */
        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Vertex))
                return false;
            Vertex v = (Vertex) o;
            return id == v.id && Objects.equals(label, v.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, label);
        }

        @Override
        public String toString() {
            return id + "(" + label + ")";
        }
    }

    /**
     * An edge in the subgraph connecting two vertices and carrying a string label.
     */
    public static class Edge {
        /** Source vertex id. */
        private final int sourceId;
        /** Target vertex id. */
        private final int targetId;
        /** Edge label. */
        private final String label;

        /**
         * Constructs an Edge.
         *
         * @param sourceId the source vertex identifier
         * @param targetId the target vertex identifier
         * @param label    the edge label
         */
        public Edge(int sourceId, int targetId, String label) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.label = label;
        }

        /**
         * @return the source vertex identifier
         */
        public int getSourceId() {
            return sourceId;
        }

        /**
         * @return the target vertex identifier
         */
        public int getTargetId() {
            return targetId;
        }

        /**
         * @return the edge label
         */
        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Edge))
                return false;
            Edge e = (Edge) o;
            return sourceId == e.sourceId && targetId == e.targetId
                    && Objects.equals(label, e.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceId, targetId, label);
        }

        @Override
        public String toString() {
            return sourceId + "-" + label + "->" + targetId;
        }
    }


    /**
     * Constructs a new Subgraph instance.
     *
     * @param graphId            the graph identifier string
     * @param vertices           the list of vertices
     * @param edges              the list of edges
     * @param containingGraphIds the identifiers of graphs containing this subgraph
     * @param measures           mapping of measure names to measure values
     * @throws NullPointerException if any argument is null
     */
    public Subgraph(String graphId, List<Vertex> vertices, List<Edge> edges,
            List<String> containingGraphIds, Map<String, String> measures) {
        this.graphId            = Objects.requireNonNull(graphId,            "graphId must not be null");
        this.vertices           = Objects.requireNonNull(vertices,           "vertices must not be null");
        this.edges              = Objects.requireNonNull(edges,              "edges must not be null");
        this.containingGraphIds = Objects.requireNonNull(containingGraphIds, "containingGraphIds must not be null");
        this.measures           = Objects.requireNonNull(measures,           "measures must not be null");
        this.cachedToString     = buildString();
    }

    /**
     * Builds a compact human-readable string for this subgraph (computed once).
     *
     * @return the string representation
     */
    private String buildString() {
        StringBuilder sb = new StringBuilder();
        sb.append("G").append(graphId);
        sb.append(" V").append(vertices);
        if (!edges.isEmpty()) {
            sb.append(" E").append(edges);
        }
        return sb.toString();
    }
    
    /**
     * Returns a search-friendly string containing only vertex labels and edge labels
     * (not vertex IDs or containing graph IDs). Used for text filtering in the viewer.
     *
     * @return searchable string representation
     */
    public String toSearchableString() {
        StringBuilder sb = new StringBuilder();
        
        // Add all vertex labels
        for (Vertex v : vertices) {
            sb.append(v.getLabel()).append(" ");
        }
        
        // Add all edge labels
        for (Edge e : edges) {
            sb.append(e.getLabel()).append(" ");
        }
        
        return sb.toString();
    }

    /**
     * @return the graph identifier
     */
    public String getGraphId() {
        return graphId;
    }

    /**
     * @return the list of vertices
     */
    public List<Vertex> getVertices() {
        return vertices;
    }

    /**
     * @return the list of edges
     */
    public List<Edge> getEdges() {
        return edges;
    }

    /**
     * Returns the identifiers of the graphs in the database that contain this
     * subgraph, as listed on the {@code x} line of the file.
     *
     * @return list of containing graph identifiers (never null)
     */
    public List<String> getContainingGraphIds() {
        return containingGraphIds;
    }

    /**
     * @return map of measure names to their values (e.g. "SUP" → "3")
     */
    public Map<String, String> getMeasures() {
        return measures;
    }

    /**
     * Returns the number of vertices in this subgraph.
     *
     * @return vertex count
     */
    public int size() {
        return vertices.size();
    }

    /**
     * @return a human-readable representation
     */
    @Override
    public String toString() {
        return cachedToString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Subgraph))
            return false;
        Subgraph that = (Subgraph) o;
        return Objects.equals(graphId,            that.graphId)
                && Objects.equals(vertices,           that.vertices)
                && Objects.equals(edges,              that.edges)
                && Objects.equals(containingGraphIds, that.containingGraphIds)
                && Objects.equals(measures,           that.measures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphId, vertices, edges, containingGraphIds, measures);
    }
}