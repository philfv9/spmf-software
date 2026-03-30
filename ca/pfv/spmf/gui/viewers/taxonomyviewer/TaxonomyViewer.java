package ca.pfv.spmf.gui.viewers.taxonomyviewer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

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
 */
/**
 * A tool to visualize a taxonomy in SPMF format.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class TaxonomyViewer {

	/** The frame */
	private JFrame frame;
	/** The tree for displaying the taxonomy */
	private JTree tree;
	/** The label of the status bar */
	private JLabel statusLabel;
	/** The filename label */
	private JLabel nameLabel;

	/** Number of distinct items */
	private int numberOfDistinctItems = 0;
	/** Number of distinct levels */
	private int numberOfLevels = 0;
	/** Total number of nodes */
	private int numberOfNodes = 0;
	/** Number of leaf nodes */
	private int numberOfLeafNodes = 0;

	/**
	 * A map, where an entry in the map is key = String (attribute value), value =
	 * Integer (item id)
	 */
	Map<Integer, String> mapItemIDtoStringValue = null;

	/**
	 * Constructor
	 * 
	 * @param runAsStandaloneProgram must be set to true, if this tool is run as a
	 *                               standalone program
	 * @param taxonomyFilePath       a path to a taxonomy file
	 * @param transactionFile        a path to a transaction file
	 * @throws IOException if a file reading error occurs
	 */
	public TaxonomyViewer(boolean runAsStandaloneProgram, String taxonomyFilePath, String transactionFile)
			throws IOException {
		frame = new JFrame("SPMF Taxonomy Viewer");
		if (runAsStandaloneProgram) {
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		if (transactionFile != null) {
			loadFile(transactionFile);
		}

		// Create the root node
		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Taxonomy");

		// Create the tree model and pass in the root node
		DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);

		// Create the tree with the new model
		tree = new JTree(treeModel);

		// Set custom renderer to highlight leaf nodes
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
					boolean leaf, int row, boolean hasFocus) {
				Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				if (leaf) {
					setForeground(Color.BLUE);
				} else {
					setForeground(Color.BLACK);
				}
				return c;
			}
		});

		// Read the taxonomy from the file and build the tree
		buildTreeFromFile(taxonomyFilePath, rootNode, treeModel);

		JScrollPane scrollPane = new JScrollPane(tree);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		frame.add(scrollPane, BorderLayout.CENTER);

		JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statusLabel = new JLabel();
		statusPanel.add(statusLabel);

		// After building the tree, calculate the number of levels
		numberOfLevels = calculateNumberOfLevels(rootNode);

		// Update the status label with the statistics
		updateStatusLabel();

		nameLabel = new JLabel("Taxonomy: " + taxonomyFilePath);
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		topPanel.add(nameLabel);

		// Add buttons to expand/collapse
		JButton expandButton = new JButton("Expand All");
		expandButton.addActionListener(e -> expandOrCollapseAll(true));
		topPanel.add(expandButton);

		JButton collapseButton = new JButton("Collapse All");
		collapseButton.addActionListener(e -> expandOrCollapseAll(false));
		topPanel.add(collapseButton);

		frame.add(topPanel, BorderLayout.NORTH);
		frame.add(statusPanel, BorderLayout.SOUTH);

		frame.setSize(800, 600);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	/**
	 * Method to load a file containing a transaction database
	 * 
	 * @param path the path of the file
	 * @throws IOException exception if error reading the file
	 */
	public void loadFile(String path) throws IOException {
		String thisLine; // variable to read each line
		BufferedReader myInput = null; // object to read the file
		FileInputStream fin = new FileInputStream(new File(path));
		myInput = new BufferedReader(new InputStreamReader(fin));

		mapItemIDtoStringValue = new HashMap<Integer, String>();

		// for each line
		while ((thisLine = myInput.readLine()) != null) {
			// We only want to read the lines that starts with @ITEM to get the item names
			if (thisLine.isEmpty() == false) {
				if (thisLine.startsWith("@ITEM")) {
					// remove "@ITEM="
					thisLine = thisLine.substring(6);
					// get the position of the first = in the remaining string
					int index = thisLine.indexOf("=");
					int itemID = Integer.parseInt(thisLine.substring(0, index));
					String stringValue = thisLine.substring(index + 1);
					if (mapItemIDtoStringValue == null) {
						mapItemIDtoStringValue = new HashMap<Integer, String>();
					}
					mapItemIDtoStringValue.put(itemID, stringValue);
				}
			}
		}
		myInput.close();
	}

	/**
	 * Load a taxonomy from a file and build the tree
	 * 
	 * @param filePath  the file path
	 * @param rootNode  the root node
	 * @param treeModel the tree model
	 */
	private void buildTreeFromFile(String filePath, DefaultMutableTreeNode rootNode, DefaultTreeModel treeModel) {
		Map<Integer, DefaultMutableTreeNode> treeNodeMap = new HashMap<>();
		Map<Integer, Integer> childParentMap = new HashMap<>();

		// First, read all parent-child relationships from the file
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(",");
				int child = Integer.parseInt(parts[0]);
				int parent = Integer.parseInt(parts[1]);
				childParentMap.put(child, parent);

				// If items have namesx
				if (mapItemIDtoStringValue != null) {
					// Create tree nodes for each unique item and parent
					String childName = mapItemIDtoStringValue.getOrDefault(child, "" + child);
					treeNodeMap.putIfAbsent(child, new DefaultMutableTreeNode(childName));
					String parentName = mapItemIDtoStringValue.getOrDefault(parent, "" + parent);
					treeNodeMap.putIfAbsent(parent, new DefaultMutableTreeNode(parentName));
				} else {
					// Create tree nodes for each unique item and parent
					treeNodeMap.putIfAbsent(child, new DefaultMutableTreeNode(child));
					treeNodeMap.putIfAbsent(parent, new DefaultMutableTreeNode(parent));
				}

			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(frame, "Error reading file: " + e.getMessage(), "File Read Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Now, build the tree using the parent-child relationships
		for (Map.Entry<Integer, Integer> entry : childParentMap.entrySet()) {
			int child = entry.getKey();
			int parent = entry.getValue();

			// Get the tree nodes of the parent and child
			DefaultMutableTreeNode parentNode = treeNodeMap.get(parent);
			DefaultMutableTreeNode childNode = treeNodeMap.get(child);

			// If the parent node is not the root and it's not already in the tree, find its
			// parent
			if (!parentNode.equals(rootNode) && parentNode.getParent() == null) {
				Integer grandparent = childParentMap.get(parent);
				if (grandparent != null) {
					DefaultMutableTreeNode grandparentNode = treeNodeMap.get(grandparent);
					grandparentNode.add(parentNode);
				} else {
					rootNode.add(parentNode); // If no grandparent, add to root
				}
			}

			// Add the child node to the parent node
			parentNode.add(childNode);
		}

		// Notify the model that the structure has changed to update the view
		treeModel.nodeStructureChanged(rootNode);
	}

	/**
	 * Calculates the number of levels in the taxonomy tree.
	 *
	 * @param rootNode the root node of the tree
	 * @return the number of levels in the tree
	 */
	private int calculateNumberOfLevels(DefaultMutableTreeNode rootNode) {
		Enumeration<?> enumeration = rootNode.depthFirstEnumeration();
		int maxLevel = 0;
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			if (node.getLevel() > maxLevel) {
				maxLevel = node.getLevel();
			}
			// Count distinct items
			if (!node.isRoot()) {
				numberOfDistinctItems++;
			}
			numberOfNodes++;
			if (node.isLeaf()) {
				numberOfLeafNodes++;
			}
		}
		return maxLevel - 1; // The root is level 0, so the actual number of levels is maxLevel + 1
	}

	/**
	 * Updates the status label with the current statistics of the taxonomy.
	 */
	private void updateStatusLabel() {
		statusLabel.setText("Number of distinct items: " + numberOfDistinctItems +
				", Taxonomy levels: " + (numberOfLevels + 1) +
				", Total nodes: " + numberOfNodes +
				", Leaf nodes: " + numberOfLeafNodes);
	}

	/**
	 * Expand or collapse all tree nodes.
	 *
	 * @param expand true to expand, false to collapse
	 */
	private void expandOrCollapseAll(boolean expand) {
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
		if (!expand) {
			for (int i = tree.getRowCount() - 1; i > 0; i--) {
				tree.collapseRow(i);
			}
		}
	}
}
