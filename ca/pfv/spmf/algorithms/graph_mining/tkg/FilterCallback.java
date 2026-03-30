package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.List;

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
 * This callback restricts the iterator to output projections with edges specified by the filter.
 * If filter edge at position i is not null, all output projections must have that edge at position i.
 *
 * @see IProjectedIteratorCallback
 * @author Shaul Zevin
 */
public class FilterCallback implements IProjectedIteratorCallback {
    private List<EdgeEnumeration> filter;

    /**
     *
     * @param filter list of edges to be filtered by, may include nulls
     */
    public FilterCallback(List<EdgeEnumeration> filter) {
        this.filter = filter;
    }

    @Override
    public boolean beforeAdvance(List<ProjectedEdge> projectedEdges, ProjectedEdge nextProjectedEdge) {
        int index = projectedEdges == null? 0: projectedEdges.size();
        if (filter.get(index) == null) {
            return true;
        }
        // continue with projection expansion only if the edge matches filter
        return filter.get(index).equals(nextProjectedEdge.getEdgeEnumeration());
    }

    @Override
    public boolean afterAdvance(List<ProjectedEdge> projectedEdges, ProjectedEdge nextProjectedEdge) {
        int index = projectedEdges == null? 0: projectedEdges.size();
        if (filter.get(index) == null) {
            return true;
        }

        // since filter edge was already expanded, other edges expansion is not necessary
        return false;
    }

    @Override
    public PDFSCompact beforeSubmit(PDFSCompact pdfsCompact) {
        return pdfsCompact;
    }
}
