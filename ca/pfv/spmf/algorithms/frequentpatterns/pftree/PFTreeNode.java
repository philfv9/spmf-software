package ca.pfv.spmf.algorithms.frequentpatterns.pftree;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
* Do not remove copyright and license information from files.
*/

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a node in the PF-tree used by the PFTree algorithm.
 *
 * @see AlgoPFTree
 * @author Philippe Fournier-Viger, 2024
 */
public class PFTreeNode {

    /** the item stored in this node, or -1 for the root */
    int item;

    /** the parent of this node */
    PFTreeNode parent;

    /** the children of this node */
    List<PFTreeNode> children;

    /** the next node with the same item (node traversal pointer) */
    PFTreeNode nodeLink;

    /** the tid-list stored at this node if it is a tail-node, otherwise null */
    List<Integer> tidList;

    /**
     * Creates a new PFTreeNode with the given item.
     * @param item the item stored in this node
     */
    public PFTreeNode(int item) {
        this.item = item;
        this.children = new ArrayList<>();
        this.tidList = null;
        this.parent = null;
        this.nodeLink = null;
    }

    /**
     * Returns the child node with the given item, or null if none exists.
     * @param item the item to search for
     * @return the child node with the given item, or null
     */
    public PFTreeNode getChildWithItem(int item) {
        for (PFTreeNode child : children) {
            if (child.item == item) {
                return child;
            }
        }
        return null;
    }
}