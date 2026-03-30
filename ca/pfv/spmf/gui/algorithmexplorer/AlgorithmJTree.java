package ca.pfv.spmf.gui.algorithmexplorer;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * JTree that displays the algorithms offered in SPMF
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class AlgorithmJTree extends JTree {

	// Color constants for better customization
	private static final Color HIGHLIGHT_COLOR = Color.BLUE;
	private static final Color NORMAL_COLOR = Color.BLACK;
	private static final Color CATEGORY_COLOR = new Color(50, 50, 50);
	
	/** indicate if the highlight function is activated for the JTree */
	private boolean activatedHighlight = false;
	
	/** Cache for algorithm manager to avoid repeated getInstance calls */
	private AlgorithmManager algorithmManager;

	/**
	 * Inner class representing a node in the algorithm tree
	 */
	class AlgoNode {
		private final String name;
		private boolean isHighlight;
		private final boolean isCategory;

		/**
		 * Constructor for AlgoNode
		 * 
		 * @param name the name of the algorithm or category
		 * @param isCategory true if this is a category node
		 */
		AlgoNode(String name, boolean isCategory) {
			this.name = Objects.requireNonNull(name, "Node name cannot be null");
			this.isHighlight = false;
			this.isCategory = isCategory;
		}

		public boolean isHighlight() {
			return isHighlight;
		}

		public void setHighlight(boolean isHighlight) {
			this.isHighlight = isHighlight;
		}

		public boolean isCategory() {
			return isCategory;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return name;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			AlgoNode algoNode = (AlgoNode) o;
			return isCategory == algoNode.isCategory && name.equals(algoNode.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, isCategory);
		}
	}

	/**
	 * Custom cell renderer for the algorithm tree
	 */
	private class AlgorithmTreeCellRenderer extends DefaultTreeCellRenderer {
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
				boolean expanded, boolean leaf, int row, boolean hasFocus) {
			JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			// Special case for the root
			if (((DefaultMutableTreeNode) value).getUserObject() instanceof String) {
				return label;
			}
			
			// The case of other nodes
			AlgoNode node = (AlgoNode) ((DefaultMutableTreeNode) value).getUserObject();
			
			if (activatedHighlight && node.isHighlight()) {
				label.setForeground(HIGHLIGHT_COLOR);
			} else if (node.isCategory()) {
				label.setForeground(CATEGORY_COLOR);
			} else {
				label.setForeground(NORMAL_COLOR);
			}
			
			return label;
		}
	}

	/**
	 * Constructor for AlgorithmJTree
	 * 
	 * @param showTools true to show tools
	 * @param showAlgorithms true to show algorithms
	 * @param showExperimentTools true to show experiment tools
	 */
	public AlgorithmJTree(boolean showViewAndTransform, boolean showGenerateData, boolean showTools, boolean showAlgorithms, boolean showExperimentTools) {
		super(new DefaultMutableTreeNode("Root"));
		this.setRootVisible(false);
		this.setShowsRootHandles(true);
		
		initializeAlgorithmManager();
		buildTree(showViewAndTransform, showGenerateData, showTools, showAlgorithms, showExperimentTools);
		setupRenderer();
		
		this.setVisible(true);
	}

	/**
	 * Initialize the algorithm manager
	 */
	private void initializeAlgorithmManager() {
		try {
			algorithmManager = AlgorithmManager.getInstance();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to initialize AlgorithmManager", e);
		}
	}

	/**
	 * Build the tree structure with algorithms and categories
	 * 
	 * @param showTools true to show tools
	 * @param showAlgorithms true to show algorithms
	 * @param showExperimentTools true to show experiment tools
	 */
	private void buildTree(boolean showViewAndTransform, boolean showGenerateData, boolean showTools, boolean showAlgorithms, boolean showExperimentTools) {
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
		DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
		setModel(treeModel);

		List<String> algorithmList = algorithmManager.getListOfAlgorithmsAsString(showViewAndTransform, showGenerateData,
				showTools, showAlgorithms, showExperimentTools);
		
		DefaultMutableTreeNode currentCategoryNode = null;

		for (String algorithmOrCategoryName : algorithmList) {
			if (isCategory(algorithmOrCategoryName)) {
				currentCategoryNode = createCategoryNode(algorithmOrCategoryName);
				addNodeToTreeModel(treeModel, rootNode, currentCategoryNode);
			} else {
				DefaultMutableTreeNode algorithmNode = createAlgorithmNode(algorithmOrCategoryName);
				addNodeToTreeModel(treeModel, currentCategoryNode, algorithmNode);
			}
		}
	}

	/**
	 * Check if the given name represents a category
	 * 
	 * @param name the name to check
	 * @return true if it's a category
	 */
	private boolean isCategory(String name) {
		return algorithmManager.getDescriptionOfAlgorithm(name) == null;
	}

	/**
	 * Create a category node
	 * 
	 * @param categoryName the category name
	 * @return the created node
	 */
	private DefaultMutableTreeNode createCategoryNode(String categoryName) {
		AlgoNode categoryAlgoNode = new AlgoNode(categoryName, true);
		return new DefaultMutableTreeNode(categoryAlgoNode);
	}

	/**
	 * Create an algorithm node
	 * 
	 * @param algorithmName the algorithm name
	 * @return the created node
	 */
	private DefaultMutableTreeNode createAlgorithmNode(String algorithmName) {
		AlgoNode algorithmAlgoNode = new AlgoNode(algorithmName, false);
		return new DefaultMutableTreeNode(algorithmAlgoNode);
	}

	/**
	 * Setup the custom cell renderer
	 */
	private void setupRenderer() {
		this.setCellRenderer(new AlgorithmTreeCellRenderer());
	}

	/**
	 * Add a node to the tree model
	 * 
	 * @param treeModel the tree model
	 * @param parentNode the parent node
	 * @param childNode the child node to add
	 */
	private static void addNodeToTreeModel(DefaultTreeModel treeModel, 
			DefaultMutableTreeNode parentNode, DefaultMutableTreeNode childNode) {
		if (parentNode == null) {
			throw new IllegalArgumentException("Parent node cannot be null");
		}
		
		treeModel.insertNodeInto(childNode, parentNode, parentNode.getChildCount());

		if (parentNode == treeModel.getRoot()) {
			treeModel.nodeStructureChanged((TreeNode) treeModel.getRoot());
		}
	}

	/**
	 * Get the highlight activation status
	 * 
	 * @return true if highlight is activated
	 */
	public boolean isActivatedHighlight() {
		return activatedHighlight;
	}

	/**
	 * Set the highlight activation status
	 * 
	 * @param activatedHighlight true to activate highlight
	 */
	public void setActivatedHighlight(boolean activatedHighlight) {
		this.activatedHighlight = activatedHighlight;
		repaint();
	}

	/**
	 * Highlight algorithms similar to the selected one
	 * 
	 * @param withParameters true to also compare mandatory parameters
	 */
	public void highlightSimilarAlgorithmsToSelection(boolean withParameters) {
		DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) this.getLastSelectedPathComponent();
		
		if (selectedNode == null || !(selectedNode.getUserObject() instanceof AlgoNode)) {
			return;
		}
		
		AlgoNode selectedAlgoNode = (AlgoNode) selectedNode.getUserObject();
		DescriptionOfAlgorithm selectedDescription = algorithmManager.getDescriptionOfAlgorithm(selectedAlgoNode.getName());
		
		if (selectedDescription == null) {
			return;
		}

		processNodesForHighlighting(selectedDescription, withParameters);
		setActivatedHighlight(true);
		expandHighlightedNodes();
	}

	/**
	 * Process all nodes to determine which should be highlighted
	 * 
	 * @param selectedDescription the description of the selected algorithm
	 * @param withParameters true to compare parameters
	 */
	private void processNodesForHighlighting(DescriptionOfAlgorithm selectedDescription, boolean withParameters) {
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<TreeNode> enumeration = rootNode.depthFirstEnumeration();
		
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			
			if (node == rootNode) {
				continue;
			}
			
			AlgoNode algoNode = (AlgoNode) node.getUserObject();
			algoNode.setHighlight(false);
			
			if (!algoNode.isCategory()) {
				boolean shouldHighlight = shouldHighlightAlgorithm(algoNode, selectedDescription, withParameters);
				algoNode.setHighlight(shouldHighlight);
			}
		}
	}

	/**
	 * Determine if an algorithm should be highlighted
	 * 
	 * @param algoNode the algorithm node to check
	 * @param selectedDescription the selected algorithm description
	 * @param withParameters true to compare parameters
	 * @return true if should be highlighted
	 */
	private boolean shouldHighlightAlgorithm(AlgoNode algoNode, 
			DescriptionOfAlgorithm selectedDescription, boolean withParameters) {
		DescriptionOfAlgorithm currentDescription = algorithmManager.getDescriptionOfAlgorithm(algoNode.getName());
		
		if (currentDescription == null) {
			return false;
		}
		
		boolean sameInput = arraysEqual(currentDescription.getInputFileTypes(), 
				selectedDescription.getInputFileTypes());
		boolean sameOutput = arraysEqual(currentDescription.getOutputFileTypes(), 
				selectedDescription.getOutputFileTypes());
		
		if (!sameInput || !sameOutput) {
			return false;
		}
		
		if (!withParameters) {
			return true;
		}
		
		return haveSameMandatoryParameters(
				currentDescription.getParametersDescription(),
				selectedDescription.getParametersDescription(),
				currentDescription.getNumberOfMandatoryParameters(),
				selectedDescription.getNumberOfMandatoryParameters());
	}

	/**
	 * Expand tree nodes that contain highlighted algorithms
	 */
	private void expandHighlightedNodes() {
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<TreeNode> enumeration = rootNode.depthFirstEnumeration();
		
		List<TreePath> pathsToExpand = new ArrayList<>();
		
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			
			if (node != rootNode && node.getUserObject() instanceof AlgoNode) {
				AlgoNode algoNode = (AlgoNode) node.getUserObject();
				
				if (algoNode.isHighlight()) {
					TreePath path = new TreePath(node.getPath());
					pathsToExpand.add(path.getParentPath());
				}
			}
		}
		
		for (TreePath path : pathsToExpand) {
			this.expandPath(path);
		}
	}

	/**
	 * Check if two algorithms have the same mandatory parameters
	 * 
	 * @param params1 first parameter array
	 * @param params2 second parameter array
	 * @param count1 number of mandatory parameters in first array
	 * @param count2 number of mandatory parameters in second array
	 * @return true if they have the same mandatory parameters
	 */
	private boolean haveSameMandatoryParameters(DescriptionOfParameter[] params1,
			DescriptionOfParameter[] params2, int count1, int count2) {
		if (count1 != count2) {
			return false;
		}
		
		if (params1 == null || params2 == null) {
			return params1 == params2;
		}
		
		for (int i = 0; i < count1; i++) {
			if (!params1[i].getName().equals(params2[i].getName())) {
				return false;
			}
			
			// Additional check for parameter type
			if (!params1[i].getParameterType().equals(params2[i].getParameterType())) {
				return false;
			}
		}
		
		return true;
	}

	/**
	 * Check if two string arrays are equal
	 * 
	 * @param array1 first array
	 * @param array2 second array
	 * @return true if arrays are equal
	 */
	private boolean arraysEqual(String[] array1, String[] array2) {
		if (array1 == null && array2 == null) {
			return true;
		}
		
		if (array1 == null || array2 == null) {
			return false;
		}
		
		return Arrays.equals(array1, array2);
	}

	/**
	 * Clear all highlights from the tree
	 */
	public void clearAllHighlights() {
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<TreeNode> enumeration = rootNode.depthFirstEnumeration();
		
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			
			if (node != rootNode && node.getUserObject() instanceof AlgoNode) {
				AlgoNode algoNode = (AlgoNode) node.getUserObject();
				algoNode.setHighlight(false);
			}
		}
		
		setActivatedHighlight(false);
	}

	/**
	 * Get the count of highlighted algorithms
	 * 
	 * @return the number of highlighted algorithms
	 */
	public int getHighlightedAlgorithmCount() {
		int count = 0;
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<TreeNode> enumeration = rootNode.depthFirstEnumeration();
		
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			
			if (node != rootNode && node.getUserObject() instanceof AlgoNode) {
				AlgoNode algoNode = (AlgoNode) node.getUserObject();
				if (algoNode.isHighlight()) {
					count++;
				}
			}
		}
		
		return count;
	}

	/**
	 * Get a list of all highlighted algorithm names
	 * 
	 * @return list of highlighted algorithm names
	 */
	public List<String> getHighlightedAlgorithmNames() {
		List<String> highlightedNames = new ArrayList<>();
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<TreeNode> enumeration = rootNode.depthFirstEnumeration();
		
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			
			if (node != rootNode && node.getUserObject() instanceof AlgoNode) {
				AlgoNode algoNode = (AlgoNode) node.getUserObject();
				if (algoNode.isHighlight() && !algoNode.isCategory()) {
					highlightedNames.add(algoNode.getName());
				}
			}
		}
		
		return highlightedNames;
	}

	/**
	 * Search for algorithms by name (case-insensitive)
	 * 
	 * @param searchText the text to search for
	 * @return list of matching algorithm names
	 */
	public List<String> searchAlgorithms(String searchText) {
		List<String> matchingAlgorithms = new ArrayList<>();
		
		if (searchText == null || searchText.trim().isEmpty()) {
			return matchingAlgorithms;
		}
		
		String searchLower = searchText.toLowerCase();
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<TreeNode> enumeration = rootNode.depthFirstEnumeration();
		
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			
			if (node != rootNode && node.getUserObject() instanceof AlgoNode) {
				AlgoNode algoNode = (AlgoNode) node.getUserObject();
				if (!algoNode.isCategory() && algoNode.getName().toLowerCase().contains(searchLower)) {
					matchingAlgorithms.add(algoNode.getName());
				}
			}
		}
		
		return matchingAlgorithms;
	}
}