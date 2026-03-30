package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.List;
import java.util.Map;

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
 * Handler provides analysis of early termination failure cases to be used later for early termination failure detection
 *
 * @see EarlyTerminationFailureHandlerAbstract
 * @author Shaul Zevin
 */
public interface IEarlyTerminationFailureHandler {
    /**
     * analyzes 5 early termination failure cases. DFS code is added to trie if one of 5 cases is discovered.
     *
     * @param dfsCode    analyzed DFS code
     * @param projected  analyzed projections of the DFS code into graphs database
     * @param extensions DFS code extension from the rightmost path.
     */
    void analyze(DFSCode dfsCode, ProjectedCompact projected, Map<ExtendedEdge, ProjectedCompact> extensions);

    /**
     * @param extendedEdges DFS code edges
     * @return true if DFS code found in the trie, false otherwise
     */
    boolean detect(List<ExtendedEdge> extendedEdges);
}
