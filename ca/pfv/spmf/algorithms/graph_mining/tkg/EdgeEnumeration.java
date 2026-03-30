package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.Objects;

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
 * This is an implementation of database graph edge enumeration, used by the CGSPAN algorithm.
 * Each edge in database graphs is uniquely enumerated by (graph id, edge) pair.
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

public class EdgeEnumeration implements Comparable {
    /**
     * database graph id
     */
    private int gid;
    /**
     * database graph edge
     */
    private Edge edge;

    private int hash;

    public EdgeEnumeration(int gid, Edge edge) {
        this.gid = gid;
        this.edge = edge;
        hash = Objects.hash(gid, edge);
    }

    public int getGid() {
        return gid;
    }

    public Edge getEdge() {
        return edge;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdgeEnumeration that = (EdgeEnumeration) o;
        return gid == that.gid && edge.equals(that.edge);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int compareTo(Object o) {
        EdgeEnumeration other = (EdgeEnumeration)o;
        if (gid != other.gid) {
            return gid - other.gid;
        }
        if (edge.v1 != other.getEdge().v1) {
            return edge.v1 - other.getEdge().v1;
        }

        return edge.v2 - other.getEdge().v2;
    }
}
