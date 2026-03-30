package ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger

This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;

/**
 * This class represents an itemset hash tree as used by the AprioriHT algorithm
 * (a version of Apriori implemented with a hash tree).
 * <br/><br/>
 * 
 * In the original Apriori paper it is suggested to not subdivide a node until
 * there is enough itemsets in that node. In this implementations, all nodes are
 * always subdivided.
 * 
 * @see AlgoAprioriFAST
 * @author Philippe Fournier-Viger
 */
public class ItemsetHashTree {

	// this constant indicates how many child nodes a node should have
	private int branch_count = 30;

	// the size of the itemsets that are inserted into this tree
	private int itemsetSize;

	// the number of itemsets that have been inserted into this tree
	int candidateCount;

	// the root node of the tree
	InnerNode root;

	// the last leaf node that was added to the tree
	LeafNode lastInsertedNode = null;
	
	// === OPTIMIZATION  ===
	// BEFORE: Creating new int[] prefix arrays in each recursive call of updateSupportCount
	// AFTER: Use a reusable buffer to avoid allocations in hot path
	private int[] prefixBuffer;
	// === END OPTIMIZATION  ===

	/**
	 * Constructor
	 * @param itemsetSize the size of the itemsets that will be inserted in the tree
	 */
	public ItemsetHashTree(int itemsetSize, int branch_count){
		this.itemsetSize = itemsetSize;
		this.branch_count = branch_count;
		root = new InnerNode(); // create root node
		// OPTIMIZATION 5: Pre-allocate prefix buffer
		this.prefixBuffer = new int[itemsetSize];
	}

	/**
	 * Inserts an itemset in the hash-tree
	 * @param itemset the itemset to be inserted
	 */
	public void insertCandidateItemset(Itemset itemset){
		candidateCount++; // increase the counter for the number of itemsets in the tree
		insertCandidateItemset(root, itemset, 0); // insert the itemset
	}

	/**
	 * Inserts an itemset in the hash-tree (this is called recursively to search where
	 * to insert the itemset)
	 * @param node the current node to be explored
	 * @param itemset the itemset to be inserted
	 * @param level the current level in the tree (root = level 1 ...)
	 */
	private void insertCandidateItemset(Node node, Itemset itemset, int level){
		// use the modulo to know which child we should explore
		int branchIndex = itemset.itemset[level] % branch_count;
		// if we have reached the level of leaf nodes
		if(node instanceof LeafNode){
			// insert the itemset in the appropriate list of the leaf node
			List<Itemset> list = ((LeafNode)node).candidates[branchIndex];
			if(list == null){
				list = new ArrayList<Itemset>();
				((LeafNode)node).candidates[branchIndex] = list;
			}
			list.add(itemset);
		}else{
			Node nextNode = ((InnerNode)node).childs[branchIndex];
			if(nextNode == null){
				if(level == itemsetSize - 2){
					nextNode = new LeafNode();
					((LeafNode)nextNode).nextLeafNode = lastInsertedNode;
					lastInsertedNode = (LeafNode)nextNode;
				}else{
					nextNode = new InnerNode();
				}
				((InnerNode)node).childs[branchIndex] = nextNode;
			}
			insertCandidateItemset(nextNode, itemset, level+1);
		}
	}

	/**
	 * Abstract class for a node in the hash-tree.
	 */
	abstract class Node{
	}

	/**
	 * Class for nodes that are not a leaves in the hash-tree.
	 */
	class InnerNode extends Node{
		Node childs[ ] = new Node[branch_count]; // contains a list of child nodes
	}

	/**
	 * Class for leaf nodes in the hash-tree.
	 */
	class LeafNode extends Node{
		// contains a list of list of candidates
		@SuppressWarnings("unchecked")
		final List<Itemset> [] candidates = new ArrayList[branch_count];
		// a pointer to the leaf node that was created just before this one.
		// It is used to navigate quickly between leaves.
		LeafNode nextLeafNode = null;
	}

	/**
	 * This method increase the support count of all itemsets contained in the hash-tree
	 * that are contained in a transaction, using the specified weight.
	 * @param transaction the transaction.
	 * @param weight the weight (count) of this transaction.
	 */
	public void updateSupportCount(int[] transaction, int weight) {
		// OPTIMIZATION: Use reusable buffer instead of creating new arrays
		updateSupportCountOptimized(transaction, root, 0, 0, weight);
	}
	
	/**
	 * This method increase the support count of all itemsets contained in the hash-tree
	 * that are contained in a transaction (backward compatible version with weight=1).
	 * @param transaction the transaction.
	 */
	public void updateSupportCount(int[] transaction) {
		updateSupportCountOptimized(transaction, root, 0, 0, 1);
	}

	/**
	 * Optimized recursive method for increasing the support count of all itemsets contained in the hash-tree
	 * that are contained in a transaction.
	 * @param transaction the transaction
	 * @param node the current node that is explored
	 * @param firstPositionToCheck the current position in the transaction to be explored
	 * @param prefixLength the current length of prefix stored in prefixBuffer
	 * @param weight the weight (count) of this transaction
	 */
	private void updateSupportCountOptimized(int[] transaction, InnerNode node, int firstPositionToCheck, int prefixLength, int weight) {
		// the index of the last item in the transaction
		int lastPosition = transaction.length - 1;
		// the index of the last item that can be the first item in lexical order of an itemset in the transaction
		int lastPositionToCheck = transaction.length - itemsetSize + prefixLength;

		// for each item until lastPositionToCheck
		for(int i = firstPositionToCheck; i <= lastPositionToCheck; i++){
			int itemI = transaction[i];
			
			int branchIndex = itemI % branch_count;
			Node nextNode = node.childs[branchIndex];

			if(nextNode == null){
				// there is no node, so nothing to do!  we stop exploring this path...
			}else if(nextNode instanceof InnerNode){
				// if the node is not a leaf node,
				// add item i to the prefix buffer
				prefixBuffer[prefixLength] = itemI;
				// we call the method recursively with incremented prefix length
				updateSupportCountOptimized(transaction, (InnerNode) nextNode, i + 1, prefixLength + 1, weight);
			}else{
				// if the node is a leaf node
				LeafNode theNode = (LeafNode) nextNode;
				// we search for an additional item that could be added
				for(int j = i + 1; j <= lastPosition; j++){
					int itemJ = transaction[j];
					// we check which branch
					int branchIndexNextNode = itemJ % branch_count;
					List<Itemset> listCandidates = theNode.candidates[branchIndexNextNode];
					// if the branch is not null
					if(listCandidates != null){
						// we check if the resulting itemset is in this branch.
						for(int c = 0; c < listCandidates.size(); c++){
							Itemset candidate = listCandidates.get(c);
							// if so, we increase its support count
							if(sameAsPrefixOptimized(candidate.itemset, prefixLength, itemI, itemJ)){
								candidate.support += weight;
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Optimized method that checks if an itemset matches prefix (from buffer) + itemI + itemJ
	 * @param itemset1 the itemset to check
	 * @param prefixLength the length of prefix in prefixBuffer
	 * @param itemI an item that should be appended to prefix
	 * @param itemJ a second item that should be appended to prefix
	 * @return true if itemset1 equals prefixBuffer[0..prefixLength-1] + itemI + itemJ
	 */
	private boolean sameAsPrefixOptimized(int[] itemset1, int prefixLength, int itemI, int itemJ) {
		for(int i = 0; i < prefixLength; i++){
			if(itemset1[i] != prefixBuffer[i]){
				return false;
			}
		}
		return itemset1[itemset1.length - 2] == itemI 
				&& itemset1[itemset1.length - 1] == itemJ;
	}


	/**
	 * This method checks if an itemset exists in the tree.
	 * @param itemset the itemset
	 * @param posRemoved the position of an item that should be ignored in this itemset
	 * @return true if the itemset appears in the tree
	 */
	public boolean isInTheTree(int[] itemset, int posRemoved) {
		// we start from the root..
		Node node = root;
		int count = 0;
		// we will consider each item of the itemset to go down in the hash tree
		loop: for(int i=0; i< itemset.length; i++){
			// if the current item is the item to be ignored, we ignore it
			if(i== posRemoved){
				continue;
			}
			count++;
			// we check which branch we should explore
			int branchIndex = itemset[i] % branch_count;
			// if this is the last item of the itemset, this node is a leaf node
			if(count == itemsetSize){
				// if the leaf node is null, the itemset is not there, so we return false.
				if(node == null){
					return false;
				}
				// we check the appropriate branch of the leaf node
				List<Itemset> list = ((LeafNode)node).candidates[branchIndex];
				// if it is null, then the itemset is not there
				if(list == null){
					return false;
				}
				// Otherwise, we perform a binary search to check if the itemset
				// appear there.
				int first = 0;
				int last = list.size() - 1;

				// === Binary research
				while( first <= last )
				{
					int middle = ( first + last ) / 2;
					int cmp = ArraysAlgos.sameAs(list.get(middle).getItems(), itemset, posRemoved);

					if(cmp < 0){
						first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
					}
					else if(cmp > 0){
						last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
					}
					else{
						break loop;  // It was found, so we return true;
					}
				}
				return false; // it was not found

			}else{
				// if it is not a leaf node
				if(node == null){
					return false;
				}
				// we explore the next node in the appropriate branch
				node = ((InnerNode)node).childs[branchIndex];
			}
		}
		return true; 
	}
	
	/** Sort all buckets so that the binary search can work */
	public void sortAllBuckets() {
	    for (LeafNode node = lastInsertedNode; node != null; node = node.nextLeafNode) {
	        for (List<Itemset> list : node.candidates) {
	            if (list != null && list.size() > 1) {
	                Collections.sort(list, new Comparator<Itemset>() {
	                    public int compare(Itemset a, Itemset b) {
	                        return ArraysAlgos.comparatorItemsetSameSize.compare(a.itemset, b.itemset);
	                    }
	                });
	            }
	        }
	    }
	}
}