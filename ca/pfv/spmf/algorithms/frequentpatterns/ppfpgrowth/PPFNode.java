package ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth;
import java.util.ArrayList;
import java.util.List;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Illyes Hamimid et al. 2026.
 *
 * Adapted from the LPPNode class of the LPP-Growth implementation by Peng Yang (SPMF).
 * 
 * Do not remove the copyright and license information.
 */

/**
 * A node of a {@link PPFTree} (PPFP-tree).
 * <p>
 * A node represents one item with one existential probability: transactions
 * share a node only if they contain the same item with the same probability
 * (and share the same prefix). A tail node also stores the TIDs of the
 * transactions whose path ends at this node.
 *
 * @see PPFTree
 */
public class PPFNode {

    /** item id of the root node */
    static final int ROOT_ITEM = -1;

    /** the item of this node, or {@link #ROOT_ITEM} for the root */
    int itemID = ROOT_ITEM;

    /** existential probability of the item in every transaction whose path goes through this node */
    double probability = 1.0;

    /** TIDs of the transactions whose path ends at this node (only tail nodes have TIDs) */
    List<Integer> tids = new ArrayList<Integer>();

    /** the parent node, or null for the root */
    PPFNode parent = null;

    /** the child nodes */
    List<PPFNode> children = new ArrayList<PPFNode>();

    /** next node with the same item id (node-link of the header table), or null */
    PPFNode nodeLink = null;

    /** Create a root node. */
    PPFNode() {
    }

    /**
     * Return the child of this node having a given item and existential probability.
     *
     * @param id          the item id
     * @param probability the existential probability
     * @return the child, or null if there is no such child
     */
    PPFNode getChild(int id, double probability) {
        for (PPFNode child : children) {
            if (child.itemID == id && child.probability == probability) {
                return child;
            }
        }
        return null;
    }

    /**
     * Remove a child node (compared by identity).
     *
     * @param child the child to remove
     */
    void removeChild(PPFNode child) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == child) {
                children.remove(i);
                return;
            }
        }
    }

    @Override
    public String toString() {
        return itemID + ":" + probability;
    }
}
