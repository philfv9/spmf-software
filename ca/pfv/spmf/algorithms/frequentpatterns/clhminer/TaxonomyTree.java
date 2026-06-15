package ca.pfv.spmf.algorithms.frequentpatterns.clhminer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * Implementation of a taxonomy tree as used by CLH-Miner
 * @see AlgoCLHMiner
 * 
 * @author Bay Vo et al.
 */
public class TaxonomyTree {

	/** map an item to its taxonomy node */
	HashMap<Integer, TaxonomyNode> mapItemToTaxonomyNode;

	/** the root node of the taxonomy tree */
	private TaxonomyNode Root;

	/** number of general items */
	private int GI;

	/** number of leaf items */
	private int I;

	/** maximum level in the taxonomy tree */
	private int MaxLevel;

	/**
	 * Constructor.
	 */
	public TaxonomyTree() {
		Root = new TaxonomyNode(-1);
		mapItemToTaxonomyNode = new HashMap<Integer, TaxonomyNode>();
		mapItemToTaxonomyNode.put(-1, Root);
		GI = 0;
		I = 0;
		MaxLevel = 0;
	}

	/**
	 * Get the root node
	 * @return the root node
	 */
	public TaxonomyNode getRoot() {
		return Root;
	}

	/**
	 * Set the root node
	 * @param root the root node
	 */
	public void setRoot(TaxonomyNode root) {
		Root = root;
	}

	/**
	 * Get the number of general items
	 * @return the number of general items
	 */
	public int getGI() {
		return GI;
	}

	/**
	 * Set the number of general items
	 * @param gI the number of general items
	 */
	public void setGI(int gI) {
		GI = gI;
	}

	/**
	 * Get the number of leaf items
	 * @return the number of leaf items
	 */
	public int getI() {
		return I;
	}

	/**
	 * Set the number of leaf items
	 * @param i the number of leaf items
	 */
	public void setI(int i) {
		I = i;
	}

	/**
	 * Get the maximum level
	 * @return the maximum level
	 */
	public int getMaxLevel() {
		return MaxLevel;
	}

	/**
	 * Set the maximum level
	 * @param maxLevel the maximum level
	 */
	public void setMaxLevel(int maxLevel) {
		MaxLevel = maxLevel;
	}

	/**
	 * Read taxonomy data from a file path
	 * @param Path the file path
	 * @throws IOException if an error while reading the file
	 */
	public void ReadDataFromPath(String Path) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(Path))));

		String line;

		try {
			while ((line = reader.readLine()) != null) {
				// skipping comments and empty lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '@') {
					continue;
				}

				// splitting string using ','
				String tokens[] = line.split(",");

				// child comes first
				Integer child = Integer.parseInt(tokens[0]);

				// then its parent
				Integer parent = Integer.parseInt(tokens[1]);

				TaxonomyNode nodeParent = mapItemToTaxonomyNode.get(parent);
				TaxonomyNode nodeChildren = mapItemToTaxonomyNode.get(child);

				if (nodeParent == null) {
					nodeParent = new TaxonomyNode(parent);
					mapItemToTaxonomyNode.put(parent, nodeParent);
				}

				if (nodeChildren == null) {
					nodeChildren = new TaxonomyNode(child);
					mapItemToTaxonomyNode.put(child, nodeChildren);
				}

				nodeParent.addChildren(nodeChildren);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				reader.close();
			}

			for (Integer item : mapItemToTaxonomyNode.keySet()) {
				if (item != -1) {
					TaxonomyNode node = mapItemToTaxonomyNode.get(item);
					if (node.getParent() == null) {
						Root.addChildren(node);
					}
				}
			}

			SetLevelForNode();
		}
	}

	/**
	 * Set the level for each node in the taxonomy tree
	 */
	public void SetLevelForNode() {
		for (Integer item : mapItemToTaxonomyNode.keySet()) {

			int currentLevel = 0;

			if (item != -1) {
				currentLevel = 1;
				TaxonomyNode parent = mapItemToTaxonomyNode.get(item).getParent();

				while (parent.getData() != -1) {
					currentLevel++;
					parent = parent.getParent();
				}
			}

			if (mapItemToTaxonomyNode.get(item).getChildren().size() == 0) {
				I++;
			} else {
				GI++;
			}

			mapItemToTaxonomyNode.get(item).setLevel(currentLevel);

			if (currentLevel > MaxLevel) {
				MaxLevel = currentLevel;
			}
		}
	}

	/**
	 * Get the map from item to taxonomy node
	 * @return the map from item to taxonomy node
	 */
	public HashMap<Integer, TaxonomyNode> getMapItemToTaxonomyNode() {
		return mapItemToTaxonomyNode;
	}

	/**
	 * Set the map from item to taxonomy node
	 * @param mapItemToTaxonomyNode the map from item to taxonomy node
	 */
	public void setMapItemToTaxonomyNode(HashMap<Integer, TaxonomyNode> mapItemToTaxonomyNode) {
		this.mapItemToTaxonomyNode = mapItemToTaxonomyNode;
	}
}