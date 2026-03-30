package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * This is an implementation of a graph, used by the CGSPAN algorithm.
 * The implementation enumerates all edges of a graph by (graph id, edge) pair.
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

public class DatabaseGraph extends Graph {

    /**
     * enumeration of each edge in a graph
     */
    private Map<Edge, EdgeEnumeration> edgesEnumeration;

    public DatabaseGraph(int id, Map<Integer, Vertex> vMap) {
        super(id, vMap);
    }

    /**
     * builds enumeration of graph edges
     */
    public void buildEdgeEnumeration() {
        edgesEnumeration = new ConcurrentHashMap<Edge, EdgeEnumeration>();

        Set<Edge> allEdges = getAllEdges();

        for (Edge edge: allEdges) {
            edgesEnumeration.put(edge, new EdgeEnumeration(getId(), edge));
        }
    }

    public EdgeEnumeration getEdgeEnumeration(Edge edge) {
        return edgesEnumeration.get(edge);
    }

    public Map<Edge, EdgeEnumeration> getEdgesEnumeration() {
        return edgesEnumeration;
    }

    public void setEdgesEnumeration(Map<Edge, EdgeEnumeration> edgesEnumeration) {
        this.edgesEnumeration = edgesEnumeration;
    }
}
