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
 * Callback provides customized control on projection extensions by the iterator
 *
 * @author Shaul Zevin
 */
public interface IProjectedIteratorCallback {
    /**
     *
     * @param projectedEdges projection
     * @param nextProjectedEdge projection extension
     * @return true if projection should be extended with edge and false otherwise
     */
    boolean beforeAdvance(List<ProjectedEdge> projectedEdges, ProjectedEdge nextProjectedEdge);

    /**
     *
     * @param projectedEdges projection
     * @param nextProjectedEdge projection extension
     * @return true if after extending the projection with edge, the iterator should also try extensions with other edges at the same position, false otherwise
     */
    boolean afterAdvance(List<ProjectedEdge> projectedEdges, ProjectedEdge nextProjectedEdge);

    /**
     *
     * @param pdfsCompact fully extended projection
     * @return allows callback to add more information to the projection by subclassing PDFSCompact
     */
    PDFSCompact beforeSubmit(PDFSCompact pdfsCompact);
}
