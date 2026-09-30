package ca.pfv.spmf.gui.algorithmexplorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

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
 * JTree that displays the algorithms offered in SPMF, grouped by category,
 * with the ability to highlight algorithms similar to the selected one.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmJTree extends JTree {

	private static final long serialVersionUID = 1L;

	private static final Color HIGHLIGHT_COLOR = new Color(0, 0, 210);
	private static final Color CATEGORY_COLOR = new Color(50, 50, 50);
	/** Category used for algorithms that appear before any category header */
	private static final String UNCATEGORIZED = "Other";

	/** whether highlighting is currently shown */
	private boolean activatedHighlight;

	private final AlgorithmManager algorithmManager;

	// ---- Node model ----------------------------------------------------

	/**
	 * User object stored in each tree node: either a category header or an
	 * algorithm (with its description).
	 */
	public static final class AlgoNode {
		private final String name;
		/** null for category nodes */
		private final DescriptionOfAlgorithm description;
		private boolean highlighted;

		AlgoNode(String name, DescriptionOfAlgorithm description) {
			this.name = Objects.requireNonNull(name, "Node name cannot be null");
			this.description = description;
		}

		public String getName() {
			return name;
		}

		/** @return the algorithm description, or null if this node is a category */
		public DescriptionOfAlgorithm getDescription() {
			return description;
		}

		public boolean isCategory() {
			return description == null;
		}

		public boolean isHighlighted() {
			return highlighted;
		}

		void setHighlighted(boolean highlighted) {
			this.highlighted = highlighted;
		}

		@Override
		public String toString() {
			return name;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof AlgoNode)) {
				return false;
			}
			AlgoNode other = (AlgoNode) o;
			return isCategory() == other.isCategory() && name.equals(other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, isCategory());
		}
	}

	// ---- Renderer ------------------------------------------------------

	/** Colors categories, algorithms and highlighted algorithms differently. */
	private final class AlgorithmTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 1L;

		private Font baseFont;
		private Font boldFont;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			Object userObject = value instanceof DefaultMutableTreeNode
					? ((DefaultMutableTreeNode) value).getUserObject() : null;
			if (!(userObject instanceof AlgoNode)) {
				return this; // root or unknown node: default rendering
			}

			AlgoNode node = (AlgoNode) userObject;
			boolean highlighted = activatedHighlight && node.isHighlighted();

			setFont(highlighted ? boldFontFor(tree.getFont()) : tree.getFont());

			// Never override the foreground of a selected row, it must stay readable.
			if (!sel) {
				if (highlighted) {
					setForeground(HIGHLIGHT_COLOR);
				} else if (node.isCategory()) {
					setForeground(CATEGORY_COLOR);
				} else {
					setForeground(getTextNonSelectionColor());
				}
			}
			return this;
		}

		private Font boldFontFor(Font base) {
			if (boldFont == null || !base.equals(baseFont)) {
				baseFont = base;
				boldFont = base.deriveFont(Font.BOLD);
			}
			return boldFont;
		}
	}

	// ---- Construction --------------------------------------------------

	/**
	 * Constructor.
	 *
	 * @param showViewAndTransform true to show "view and transform" tools
	 * @param showGenerateData     true to show data generators
	 * @param showTools            true to show tools
	 * @param showAlgorithms       true to show algorithms
	 * @param showExperimentTools  true to show experiment tools
	 */
	public AlgorithmJTree(boolean showViewAndTransform, boolean showGenerateData, boolean showTools,
			boolean showAlgorithms, boolean showExperimentTools) {
		super(new DefaultMutableTreeNode("Root"));
		this.algorithmManager = obtainAlgorithmManager();

		setModel(new DefaultTreeModel(buildRootNode(showViewAndTransform, showGenerateData, showTools,
				showAlgorithms, showExperimentTools)));
		setRootVisible(false);
		setShowsRootHandles(true);
		getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		setCellRenderer(new AlgorithmTreeCellRenderer());
	}

	private static AlgorithmManager obtainAlgorithmManager() {
		try {
			return AlgorithmManager.getInstance();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize AlgorithmManager", e);
		}
	}

	/**
	 * Builds the whole tree structure offline (no model events fired per node).
	 */
	private DefaultMutableTreeNode buildRootNode(boolean showViewAndTransform, boolean showGenerateData,
			boolean showTools, boolean showAlgorithms, boolean showExperimentTools) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
		List<String> entries = algorithmManager.getListOfAlgorithmsAsString(showViewAndTransform,
				showGenerateData, showTools, showAlgorithms, showExperimentTools);

		DefaultMutableTreeNode currentCategory = null;
		for (String entry : entries) {
			DescriptionOfAlgorithm description = algorithmManager.getDescriptionOfAlgorithm(entry);
			if (description == null) {
				// An entry without description is a category header
				currentCategory = new DefaultMutableTreeNode(new AlgoNode(entry, null));
				root.add(currentCategory);
			} else {
				if (currentCategory == null) {
					currentCategory = new DefaultMutableTreeNode(new AlgoNode(UNCATEGORIZED, null));
					root.add(currentCategory);
				}
				currentCategory.add(new DefaultMutableTreeNode(new AlgoNode(entry, description)));
			}
		}
		return root;
	}

	// ---- Selection helpers ---------------------------------------------

	/** @return the selected node (category or algorithm), or null if none */
	public AlgoNode getSelectedAlgoNode() {
		Object component = getLastSelectedPathComponent();
		if (component instanceof DefaultMutableTreeNode) {
			Object userObject = ((DefaultMutableTreeNode) component).getUserObject();
			if (userObject instanceof AlgoNode) {
				return (AlgoNode) userObject;
			}
		}
		return null;
	}

	/** @return the name of the selected algorithm, or null if none / a category is selected */
	public String getSelectedAlgorithmName() {
		AlgoNode node = getSelectedAlgoNode();
		return node == null || node.isCategory() ? null : node.getName();
	}

	/** @return the description of the selected algorithm, or null if none / a category is selected */
	public DescriptionOfAlgorithm getSelectedAlgorithmDescription() {
		AlgoNode node = getSelectedAlgoNode();
		return node == null ? null : node.getDescription();
	}

	/**
	 * Selects (and scrolls to) the algorithm with the given name.
	 *
	 * @return true if the algorithm was found
	 */
	public boolean selectAlgorithm(String algorithmName) {
		for (DefaultMutableTreeNode treeNode : allAlgoTreeNodes()) {
			AlgoNode node = toAlgoNode(treeNode);
			if (!node.isCategory() && node.getName().equals(algorithmName)) {
				TreePath path = new TreePath(treeNode.getPath());
				setSelectionPath(path);
				scrollPathToVisible(path);
				return true;
			}
		}
		return false;
	}

	/** @return number of algorithms (categories excluded) displayed in the tree */
	public int getAlgorithmCount() {
		int count = 0;
		for (DefaultMutableTreeNode treeNode : allAlgoTreeNodes()) {
			if (!toAlgoNode(treeNode).isCategory()) {
				count++;
			}
		}
		return count;
	}

	// ---- Highlighting --------------------------------------------------

	/** @return true if highlighting is currently displayed */
	public boolean isActivatedHighlight() {
		return activatedHighlight;
	}

	/**
	 * Shows or hides the highlighting (does not change which nodes are marked).
	 */
	public void setActivatedHighlight(boolean activatedHighlight) {
		if (this.activatedHighlight != activatedHighlight) {
			this.activatedHighlight = activatedHighlight;
			repaint();
		}
	}

	/**
	 * Highlights all algorithms having the same input/output types as the
	 * selected algorithm (and optionally the same mandatory parameters), and
	 * expands the categories that contain them.
	 *
	 * @param compareMandatoryParameters true to also require identical mandatory
	 *                                   parameters
	 * @return the number of highlighted algorithms (0 if nothing is selected)
	 */
	public int highlightSimilarAlgorithmsToSelection(boolean compareMandatoryParameters) {
		DescriptionOfAlgorithm selected = getSelectedAlgorithmDescription();
		if (selected == null) {
			return 0;
		}

		int count = 0;
		List<TreePath> categoriesToExpand = new ArrayList<>();
		for (DefaultMutableTreeNode treeNode : allAlgoTreeNodes()) {
			AlgoNode node = toAlgoNode(treeNode);
			boolean match = !node.isCategory()
					&& isSimilar(node.getDescription(), selected, compareMandatoryParameters);
			node.setHighlighted(match);
			if (match) {
				count++;
				categoriesToExpand.add(new TreePath(treeNode.getPath()).getParentPath());
			}
		}

		setActivatedHighlight(true);
		categoriesToExpand.forEach(this::expandPath);
		scrollPathToVisible(getSelectionPath());
		repaint();
		return count;
	}

	/** Removes all highlight marks and hides highlighting. */
	public void clearAllHighlights() {
		for (DefaultMutableTreeNode treeNode : allAlgoTreeNodes()) {
			toAlgoNode(treeNode).setHighlighted(false);
		}
		setActivatedHighlight(false);
		repaint();
	}

	/** @return the names of the currently highlighted algorithms */
	public List<String> getHighlightedAlgorithmNames() {
		List<String> names = new ArrayList<>();
		for (DefaultMutableTreeNode treeNode : allAlgoTreeNodes()) {
			AlgoNode node = toAlgoNode(treeNode);
			if (!node.isCategory() && node.isHighlighted()) {
				names.add(node.getName());
			}
		}
		return names;
	}

	/** @return the number of currently highlighted algorithms */
	public int getHighlightedAlgorithmCount() {
		return getHighlightedAlgorithmNames().size();
	}

	/**
	 * Searches algorithms whose name contains the given text (case-insensitive).
	 *
	 * @return matching algorithm names (empty list if the text is blank)
	 */
	public List<String> searchAlgorithms(String searchText) {
		if (searchText == null || searchText.trim().isEmpty()) {
			return Collections.emptyList();
		}
		String needle = searchText.trim().toLowerCase();
		List<String> matches = new ArrayList<>();
		for (DefaultMutableTreeNode treeNode : allAlgoTreeNodes()) {
			AlgoNode node = toAlgoNode(treeNode);
			if (!node.isCategory() && node.getName().toLowerCase().contains(needle)) {
				matches.add(node.getName());
			}
		}
		return matches;
	}

	// ---- Similarity ----------------------------------------------------

	private static boolean isSimilar(DescriptionOfAlgorithm candidate, DescriptionOfAlgorithm reference,
			boolean compareMandatoryParameters) {
		if (!Arrays.equals(candidate.getInputFileTypes(), reference.getInputFileTypes())) {
			return false;
		}
		if (!Arrays.equals(candidate.getOutputFileTypes(), reference.getOutputFileTypes())) {
			return false;
		}
		return !compareMandatoryParameters || haveSameMandatoryParameters(
				candidate.getParametersDescription(), reference.getParametersDescription());
	}

	private static boolean haveSameMandatoryParameters(DescriptionOfParameter[] params1,
			DescriptionOfParameter[] params2) {
		List<DescriptionOfParameter> mandatory1 = mandatoryParameters(params1);
		List<DescriptionOfParameter> mandatory2 = mandatoryParameters(params2);
		if (mandatory1.size() != mandatory2.size()) {
			return false;
		}
		for (int i = 0; i < mandatory1.size(); i++) {
			DescriptionOfParameter p1 = mandatory1.get(i);
			DescriptionOfParameter p2 = mandatory2.get(i);
			if (!Objects.equals(p1.getName(), p2.getName())
					|| !Objects.equals(p1.getParameterType(), p2.getParameterType())) {
				return false;
			}
		}
		return true;
	}

	private static List<DescriptionOfParameter> mandatoryParameters(DescriptionOfParameter[] params) {
		List<DescriptionOfParameter> result = new ArrayList<>();
		if (params != null) {
			for (DescriptionOfParameter param : params) {
				if (param != null && !param.isOptional()) {
					result.add(param);
				}
			}
		}
		return result;
	}

	// ---- Traversal -----------------------------------------------------

	/** @return every tree node (categories and algorithms) whose user object is an {@link AlgoNode} */
	private List<DefaultMutableTreeNode> allAlgoTreeNodes() {
		List<DefaultMutableTreeNode> nodes = new ArrayList<>();
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<?> enumeration = root.depthFirstEnumeration();
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			if (node.getUserObject() instanceof AlgoNode) {
				nodes.add(node);
			}
		}
		return nodes;
	}

	private static AlgoNode toAlgoNode(DefaultMutableTreeNode treeNode) {
		return (AlgoNode) treeNode.getUserObject();
	}
}