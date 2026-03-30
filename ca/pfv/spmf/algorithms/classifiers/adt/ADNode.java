/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
* @Copyright original version LAC 2019   @copyright of modifications SPMF 2021
*
* SPMF is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* SPMF is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
* 
*/
package ca.pfv.spmf.algorithms.classifiers.adt;

import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.ArraysAlgos;

/**
 * Class used to represent each node forming an ADT tree
 * @see AlgoADT
 */
public class ADNode implements Cloneable {
	/**
	 * Parent of the current node
	 */
	ADNode parent = null;

	/**
	 * Rule in the current node
	 */
	RuleADT rule = null;

	/**
	 * Array with all the childs from this node
	 */
	List<ADNode> childs = new ArrayList<ADNode>();

	/**
	 * Constructor
	 * 
	 * @param rule in current node
	 */
	public ADNode(RuleADT rule) {
		this.rule = rule;
		this.parent = null;
	}

	/**
	 * Constructor for cloning
	 * 
	 * @param node a node to be copied
	 */
	public ADNode(ADNode node) {
		this.rule = new RuleADT(node.rule);
		this.parent = null;
		this.parent = node.parent;
		this.childs = new ArrayList<ADNode>(node.childs);
	}

	/**
	 * Check if there is some node as child containing the specified rule
	 * 
	 * @param rule to search in childs
	 * @return true if rule is contained in the childs
	 */
	public ADNode isChild(RuleADT rule) {
		ADNode child = null;
		for (int i = 0; i < childs.size() && child == null; i++) {
			if (ArraysAlgos.containsOrEquals(rule.getAntecedent(), childs.get(i).rule.getAntecedent())) {
				child = childs.get(i); 
			}
		}
		return child;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object v) {
		boolean retVal = false;

		if (v instanceof ADNode) {
			ADNode ptr = (ADNode) v;
			retVal = ptr.rule.equals(rule) && ptr.parent == parent;
		}

		return retVal;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.rule.hashCode();
	}
}
