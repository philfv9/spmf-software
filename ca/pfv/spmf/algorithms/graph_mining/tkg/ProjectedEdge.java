package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * This is an implementation of a DFS code edge projection into a database graph, used by the CGSPAN algorithm.
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

public class ProjectedEdge {
    /**
     * projection of the DFS code edge into database graph edge
     */
    private EdgeEnumeration edgeEnumeration;
    /**
     * direction of the edge as used in the DFS code
     */
    private boolean isReversed;

    private int hash;

    /**
     * projected edges cache
     */
    public static Map<EdgeEnumeration, ProjectedEdge[]> projectedEdges = new ConcurrentHashMap<EdgeEnumeration, ProjectedEdge[]>();

    protected ProjectedEdge(EdgeEnumeration edgeEnumeration, boolean isReversed) {
        this.edgeEnumeration = edgeEnumeration;
        this.isReversed = isReversed;
        hash = Objects.hash(edgeEnumeration, isReversed);
    }

    /**
     * retrieves projected edge from cache
     * @param edgeEnumeration database graph edge
     * @param isReversed projected edge direction
     * @return projected edge
     */
    public static ProjectedEdge get(EdgeEnumeration edgeEnumeration, boolean isReversed) {
        if (isReversed) {
            return projectedEdges.get(edgeEnumeration)[0];
        }
        else {
            return projectedEdges.get(edgeEnumeration)[1];
        }
    }

    /**
     * retrieves projected edge from cache
     * @param edgeEnumeration database graph edge
     * @param isReversed projected edge direction
     * @return projected edge if found in cache, null otherwise
     */
    public static ProjectedEdge getIfExists(EdgeEnumeration edgeEnumeration, boolean isReversed) {
        if (!projectedEdges.containsKey(edgeEnumeration)) {
            return null;

        }

        if (isReversed) {
            return projectedEdges.get(edgeEnumeration)[0];
        }
        else {
            return projectedEdges.get(edgeEnumeration)[1];
        }
    }

    /**
     * initializes projected edges cache with all edges from graphs database.
     * For each database graph edge, two projected edges are created, one for each direction.
     * @param graphDB
     */
    public static void init(List<DatabaseGraph> graphDB) {
        projectedEdges = new ConcurrentHashMap<EdgeEnumeration, ProjectedEdge[]>();

        for (DatabaseGraph databaseGraph: graphDB) {
            Map<Edge, EdgeEnumeration> edgeEdgeEnumerationMap = databaseGraph.getEdgesEnumeration();
            for (EdgeEnumeration edgeEnumeration: edgeEdgeEnumerationMap.values()) {
                ProjectedEdge reversed = new ProjectedEdge(edgeEnumeration, true);
                ProjectedEdge notReversed = new ProjectedEdge(edgeEnumeration, false);
                ProjectedEdge arr[] = new ProjectedEdge[2];
                arr[0] = reversed;
                arr[1] = notReversed;
                projectedEdges.put(edgeEnumeration, arr);
            }
        }
    }


    public EdgeEnumeration getEdgeEnumeration() {
        return edgeEnumeration;
    }

    public void setEdgeEnumeration(EdgeEnumeration edgeEnumeration) {
        this.edgeEnumeration = edgeEnumeration;
    }

    public boolean isReversed() {
        return isReversed;
    }

    public void setReversed(boolean reversed) {
        isReversed = reversed;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
