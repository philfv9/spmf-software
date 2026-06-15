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

import java.util.List;

/**
 * Listener interface for events fired by BranchingDrawPanel when the user interacts with the workflow graph.
 *
 * @author Philippe Fournier-Viger
 */
interface DrawPanelListener {

    /**
     * Called when the user selects or deselects a node on the draw panel.
     *
     * @param node the newly selected node, or null if the selection was cleared.
     */
    void notifyNodeSelected(Node node);

    /**
     * Called whenever the tree structure of the workflow changes.
     *
     * @param roots the current list of root BranchNodes; never null but may be empty.
     */
    void notifyOfListOfRootNodes(List<BranchNode> roots);

    /**
     * Called to inform listeners whether it is currently legal to add a new algorithm node.
     *
     * @param canAdd true if the Add Algorithm button should be enabled.
     */
    void notifyHasOutputNode(boolean canAdd);

    /**
     * Called to inform listeners whether the currently selected branch node may be removed.
     *
     * @param canRemove true if the Remove Selected Algorithm button should be enabled.
     */
    void notifyCanRemoveSelectedNode(boolean canRemove);
}