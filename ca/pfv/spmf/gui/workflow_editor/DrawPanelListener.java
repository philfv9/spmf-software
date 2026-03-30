package ca.pfv.spmf.gui.workflow_editor;

import java.util.List;
/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 */
/**
 * A class that represents a listener for events from the draw panel of the workflow editor
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 * @see DrawPanel
 */
interface DrawPanelListener {

	/**
	 * Method to notify the listener that a node was selected in the draw panel
	 */
	void notifyNodeSelected(Node node);
	/**
	 * Method to notify the listenerthat the number of groups (algorithms) has changed in the draw panel
	 */
	void notifyOfListOfGroups(List<GroupOfNodes> allgroups);
	/**
	 * Notify the listener that the last node of the workflow has an output.
	 * This is important to determine if aditional nodes can be added.
	 */
	void notifyHasOutputNode(boolean hasOutput);
}
