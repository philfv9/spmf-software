package ca.pfv.spmf.algorithms.frequentpatterns.feacp;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
/* This file is copyright (c) 2022 Tung et al.
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * A taxonomy node
 * @author Tung et al.
 * @see AlgoFEACP
 */
public class TaxonomyNode {
	/** data */
	private int data;
	
	/** the list of childs of that node */
	private List<TaxonomyNode> children = new ArrayList<>();

	/** the parent of that node */
	private TaxonomyNode parent = null;

	/** the level of that node */
	private int level;


	/**
	 * Get the data
	 * @return the data
	 */
	public int getData() {
		return data;
	}

	/** Constructor
	 * @param data the data to be stored in that node
	 */
	public TaxonomyNode(int data) {
		this.data = data;
	}

	/** Set the data to be stored in that node
	 * 
	 * @param data  The data
	 */
	public void setData(int data) {
		this.data = data;
	}

	/**
	 * Get the children of that node
	 * @return at list of taxonomy nodes
	 */
	public List<TaxonomyNode> getChildren() {
		return children;
	}

	/**
	 * Set the children of that node
	 * @param children a list of taxonomy nodes
	 */
	public void setChildren(List<TaxonomyNode> children) {
		this.children = children;
	}

	/**
	 * Get the parent of that node
	 * @return the parent
	 */
	public TaxonomyNode getParent() {
		return parent;
	}

	/**
	 * Set the parent of that node
	 * @param parent the parent
	 */
	public void setParent(TaxonomyNode parent) {
		this.parent = parent;
	}

	/**
	 * Get the level of that node
	 * @return the level
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Set the level of that node
	 * @param level the level
	 */
	public void setLevel(int level) {
		this.level = level;
	}

	/**
	 * Add a children to the list of childs of that node
	 * @param child the child to be added
	 * @return the child that has been added
	 */
	public TaxonomyNode addChildren(TaxonomyNode child) {
		child.setParent(this);
		this.children.add(child);
		return child;
	}

	/**
	 * Add several childs to that node
	 * @param children the list of childs
	 */
	public void addChildren(List<TaxonomyNode> children) {
		children.forEach(each -> each.setParent(this));
		this.children.addAll(children);
	}

	/**
	 * Get all childs of that node
	 * @param i the index of that node
	 * @param taxonomy the taxonomy
	 * @return the list of childs
	 */
	public static List<Integer> getAllChildNewName(int i, TaxonomyTree taxonomy) {
		List<Integer> listChild = new ArrayList<Integer>();
		TaxonomyNode Node = taxonomy.getMapItemToTaxonomyNode().get(i);
		Queue<TaxonomyNode> queue = new LinkedList<>();
		queue.add(Node);
		while (queue.size() != 0) {
			TaxonomyNode child = queue.poll();
			listChild.add(child.getData());
			for (TaxonomyNode productInfo : child.getChildren()) {
				queue.add(productInfo);
			}

		}
		return listChild;
	}
}
