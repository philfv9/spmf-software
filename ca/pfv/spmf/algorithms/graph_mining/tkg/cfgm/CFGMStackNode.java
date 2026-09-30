// ============================================================
// File: CFGMStackNode.java
// ============================================================
package ca.pfv.spmf.algorithms.graph_mining.tkg.cfgm;

import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.tkg.DFSCode;

/* This file is copyright (c) 2018-2023 by Chao Cheng, Philippe Fournier-Viger
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
*
* Do not remove copyright and license.
*/
/**
 * A node of the DFS code search tree, as handled by the explicit stack of
 * {@code AlgoCFGM#cfgmStackDFS}, mirroring Algorithm 2 of the CFGM paper.
 *
 * @author Philippe Fournier-Viger (based on the GSPAN implementation of Chao
 *         Cheng)
 */
class CFGMStackNode {

	/** the DFS code of this node */
	DFSCode code;

	/** the ids of the graphs where this DFS code currently occurs */
	Set<Integer> graphIds;

	/** the per-graph embeddings of this node's DFS code (graph id -> list of code-vertex to graph-vertex mappings) */
	Map<Integer, List<Map<Integer, Integer>>> embeddings;

	/** true once this node has been expanded and is only kept on the stack for the Case 1 pruning check */
	boolean expanded;

	/** a unique id, used as the default value of "tag" when not inherited */
	int id;

	/** the "equivalent extension" class tag of this node, inherited from its founder ancestor (Theorem 2) */
	int tag;

	/** the parent of this node in the search tree (null for initial 1-edge nodes), used to restrict Case 1 pruning to genuine ancestors */
	CFGMStackNode parent;
}