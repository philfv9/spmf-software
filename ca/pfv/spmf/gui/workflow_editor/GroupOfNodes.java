package ca.pfv.spmf.gui.workflow_editor;
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
 * A class that represents a group of nodes which can be  an input file, an algorithm, and its output file
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
class GroupOfNodes {
	
	/** The node that is the input file */
	public NodeFileInput nodeInput = null;
	
	/** The node that is the algorithm */
	public NodeAlgorithm nodeAlgorithm = null;
	
	/** The node that is the output file */
	public NodeFileOutput nodeOutput = null;
	
	/** boolean that indicates if the output node should be visible */
	boolean showOutput = false;
	
	/** boolean that indicates if the input node should be visible */
	boolean showInput = false;

	/**
	 * Constructor
	 * @param nodeInput the node that is the input node (or null if none)
	 * @param nodeAlgorithm the node that is the algorithm node
	 * @param nodeOutput the node that is the output node (or null if none)
	 */
	public GroupOfNodes(NodeFileInput nodeInput, NodeAlgorithm nodeAlgorithm, NodeFileOutput nodeOutput) {
		this.nodeInput = nodeInput;
		this.nodeAlgorithm = nodeAlgorithm;
		this.nodeOutput = nodeOutput;
		
		setGroupLink(nodeInput);
		setGroupLink(nodeAlgorithm);
		setGroupLink(nodeOutput);
	}

	/**
	 * Register a node as belonging to this group
	 * @param node the node
	 */
	private void setGroupLink(Node node) {
		if(node != null) {
			node.setGroup(this);
		}
	}

	/**
	 * Get the nodes of this group that are not null as an array
	 * @return an array of nodes
	 */
	public Node[] getNodes() {
		if (nodeInput == null) {
			if (nodeOutput == null) {
				return new Node[] {nodeAlgorithm};
			}else {
				return new Node[] {nodeAlgorithm, nodeOutput };
			}
		}else if (nodeOutput == null) {
			if (nodeInput == null) {
				return new Node[] {nodeAlgorithm};
			}else {
				return new Node[] {nodeInput, nodeAlgorithm };
			}
		}
		
		return new Node[] { nodeInput, nodeAlgorithm, nodeOutput };
	}
}
