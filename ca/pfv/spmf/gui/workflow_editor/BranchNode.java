package ca.pfv.spmf.gui.workflow_editor;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Do not remove copyright and license information.
 */

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single algorithm step in the branching workflow tree, wrapping a GroupOfNodes and holding tree links.
 *
 * @author Philippe Fournier-Viger
 */
class BranchNode {

    /** The GroupOfNodes wrapped by this branch node. */
    GroupOfNodes group;

    /** The parent branch node, or null if this is a root node. */
    BranchNode parent;

    /** The list of child branch nodes; more than one child means the workflow branches here. */
    List<BranchNode> children = new ArrayList<>();

    /** The X coordinate of this node's column centre, assigned during layout. */
    int layoutX;

    /** The Y coordinate of the top of this node's group, assigned during layout. */
    int layoutY;

    /**
     * Creates a new branch node wrapping the given group and linked to the given parent.
     *
     * @param group  the GroupOfNodes to wrap; must not be null.
     * @param parent the parent BranchNode, or null if this is a root node.
     */
    BranchNode(GroupOfNodes group, BranchNode parent) {
        this.group  = group;
        this.parent = parent;
    }

    /**
     * Returns true if this node has no parent.
     *
     * @return true for root nodes.
     */
    boolean isRoot() {
        return parent == null;
    }

    /**
     * Returns true if this node has no children.
     *
     * @return true for leaf nodes.
     */
    boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Adds the given node as a child of this node and sets this node as its parent.
     *
     * @param child the child node to attach; must not be null.
     */
    void addChild(BranchNode child) {
        children.add(child);
        child.parent = this;
    }

    /**
     * Removes the given child from this node's children list and clears the child's parent reference.
     *
     * @param child the child node to detach.
     */
    void removeChild(BranchNode child) {
        boolean removed = children.remove(child);
        if (removed) {
            child.parent = null;
        }
    }

    /**
     * Returns the output file path produced by this step, or null if this node has no output.
     *
     * @return the output file path string, or null.
     */
    String getOutputFilePath() {
        if (group.showOutput && group.nodeOutput != null) {
            return group.nodeOutput.outputFile;
        }
        return null;
    }

    /**
     * Collects all descendants of this node including itself into the given list using pre-order DFS.
     *
     * @param result the list to which nodes are appended; must not be null.
     */
    void collectAllNodes(List<BranchNode> result) {
        result.add(this);
        for (BranchNode child : new ArrayList<>(children)) {
            child.collectAllNodes(result);
        }
    }

    /**
     * Creates a deep copy of this node and its entire subtree.
     * The copy is detached from the tree (parent is null) and all node ids and
     * output file names are left identical to the original; the caller is
     * responsible for attaching the copy to the tree and adjusting names if needed.
     *
     * @return a new BranchNode that is a structural deep copy of this subtree.
     */
    BranchNode deepCopy() {
        return deepCopyInternal(null);
    }

    /**
     * Recursive helper that deep-copies this node and its subtree, wiring the given parent.
     *
     * @param newParent the parent to assign to the copied node, or null for a root copy.
     * @return the newly created copy of this node.
     */
    private BranchNode deepCopyInternal(BranchNode newParent) {
        GroupOfNodes g = this.group;

        // Copy input node (only for roots that actually have one)
        NodeFileInput inCopy = null;
        if (g.nodeInput != null) {
            inCopy = new NodeFileInput(g.nodeInput.name, g.nodeInput.getX(), g.nodeInput.getY());
            inCopy.inputFile = g.nodeInput.inputFile;
        }

        // Copy algorithm node
        NodeAlgorithm algCopy = new NodeAlgorithm(
                g.nodeAlgorithm.name, g.nodeAlgorithm.getX(), g.nodeAlgorithm.getY());
        if (g.nodeAlgorithm.parameters != null) {
            algCopy.parameters = g.nodeAlgorithm.parameters.clone();
        } else {
            algCopy.parameters = new String[0];
        }

        // Copy output node
        NodeFileOutput outCopy = new NodeFileOutput(
                g.nodeOutput.name, g.nodeOutput.getX(), g.nodeOutput.getY());
        outCopy.outputFile = g.nodeOutput.outputFile;

        // Assemble the group
        GroupOfNodes groupCopy = new GroupOfNodes(inCopy, algCopy, outCopy);
        groupCopy.showInput  = g.showInput;
        groupCopy.showOutput = g.showOutput;

        // Create the branch node copy
        BranchNode copy = new BranchNode(groupCopy, newParent);

        // Recursively deep-copy all children
        for (BranchNode child : this.children) {
            BranchNode childCopy = child.deepCopyInternal(copy);
            copy.children.add(childCopy);
        }

        return copy;
    }
}