package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseGraph extends Graph {

    /**
     * enumeration of each edge in a graph.
     * NOTE: initialized eagerly (never null) so that any caller that accesses
     * it before buildEdgeEnumeration() has explicitly been invoked does not
     * crash with a NullPointerException. Accessors additionally build it
     * lazily on first use as a safety net.
     */
    private Map<Edge, EdgeEnumeration> edgesEnumeration = new ConcurrentHashMap<Edge, EdgeEnumeration>();

    public DatabaseGraph(int id, Map<Integer, Vertex> vMap) {
        super(id, vMap);
    }

    /**
     * builds enumeration of graph edges
     */
    public synchronized void buildEdgeEnumeration() {
        Map<Edge, EdgeEnumeration> newEnumeration = new ConcurrentHashMap<Edge, EdgeEnumeration>();

        Set<Edge> allEdges = getAllEdges();

        for (Edge edge : allEdges) {
            newEnumeration.put(edge, new EdgeEnumeration(getId(), edge));
        }

        edgesEnumeration = newEnumeration;
    }

    /**
     * Returns the enumeration for a single edge, building the enumeration map
     * on demand if it has not been built yet (defensive, avoids NPE if
     * buildEdgeEnumeration() was not called beforehand on some code path).
     */
    public EdgeEnumeration getEdgeEnumeration(Edge edge) {
        if (edgesEnumeration == null || edgesEnumeration.isEmpty()) {
            buildEdgeEnumeration();
        }
        return edgesEnumeration.get(edge);
    }

    /**
     * Returns the full edge enumeration map, building it on demand if needed.
     */
    public Map<Edge, EdgeEnumeration> getEdgesEnumeration() {
        if (edgesEnumeration == null || edgesEnumeration.isEmpty()) {
            buildEdgeEnumeration();
        }
        return edgesEnumeration;
    }

    public void setEdgesEnumeration(Map<Edge, EdgeEnumeration> edgesEnumeration) {
        this.edgesEnumeration = edgesEnumeration == null
                ? new ConcurrentHashMap<Edge, EdgeEnumeration>()
                : edgesEnumeration;
    }
}