package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.LinkedList;
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
 * Validates that only canonical isomorphic subgraph projection will be returned by the iterator
 *
 * @see IProjectedIteratorCallback
 * @author Shaul Zevin
 */
public class LocalAutomorphismCallback implements IProjectedIteratorCallback {
    private LocalPDFSAutomorphismDetector localPDFSAutomorphismDetector;

    public LocalAutomorphismCallback(ProjectedCompact projectedCompact) {
        localPDFSAutomorphismDetector = new LocalPDFSAutomorphismDetector(projectedCompact);
    }

    /**
     *
     * @param projectedEdges projection
     * @param nextProjectedEdge projection extension
     * @return false if nextProjectedEdge is the last edge of isomorphic subgraph projection and projection is not canonical,
     * true otherwise
     */
    @Override
    public boolean beforeAdvance(List<ProjectedEdge> projectedEdges, ProjectedEdge nextProjectedEdge) {

        List<ProjectedEdge> projectedEdgesNext = new LinkedList<>();
        if (projectedEdges != null) {
            projectedEdgesNext.addAll(projectedEdges);
        }
        projectedEdgesNext.add(nextProjectedEdge);


        return localPDFSAutomorphismDetector.beforeAdvance(projectedEdgesNext);
    }

    @Override
    public boolean afterAdvance(List<ProjectedEdge> projectedEdges, ProjectedEdge nextProjectedEdge) {
        return true;
    }

    /**
     * merges projections of isomorphic subgraphs of the canonical projection
     * @param pdfsCompact merged projection
     * @return
     */
    @Override
    public PDFSCompact beforeSubmit(PDFSCompact pdfsCompact) {
        return localPDFSAutomorphismDetector.beforeSubmit(pdfsCompact);
    }
}
