package ca.pfv.spmf.algorithms.frequentpatterns.tm;

import java.util.ArrayList;
import java.util.List;
/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class represents the Transaction Tree used in the TM algorithm.
 * The transaction tree is similar to an FP-tree but without header table or node links.
 * 
 * @author Philippe Fournier-Viger
 */
public class TransactionTree {
    
    /** The root node of the tree */
    TransactionTreeNode root;
    
    /** Total number of transactions */
    int transactionCount;
    
    /**
     * Constructor - creates an empty transaction tree
     */
    public TransactionTree() {
        root = new TransactionTreeNode(-1); // dummy root
        root.count = 0;
        transactionCount = 0;
    }
    
    /**
     * Insert a transaction into the tree
     * @param transaction the sorted list of items in the transaction
     */
    public void insertTransaction(List<Integer> transaction) {
        if (transaction == null || transaction.isEmpty()) {
            return;
        }
        
        TransactionTreeNode current = root;
        root.count++;
        transactionCount++;
        
        for (int item : transaction) {
            TransactionTreeNode child = current.getChildWithId(item);
            if (child == null) {
                child = current.addChild(item);
            }
            child.count++;
            current = child;
        }
    }
    
    /**
     * Insert a transaction into the tree using a primitive int array.
     * <p>
     * This method is an optimized version that avoids autoboxing overhead
     * by accepting a primitive int array instead of a List&lt;Integer&gt;.
     * Only the first {@code length} elements of the array are considered
     * as part of the transaction.
     * </p>
     * 
     * @param transaction the sorted array of items in the transaction
     * @param length the number of valid items in the transaction array
     *               (items from index 0 to length-1 will be inserted)
     */
    public void insertTransaction(int[] transaction, int length) {
        if (transaction == null || length <= 0) {
            return;
        }
        
        TransactionTreeNode current = root;
        root.count++;
        transactionCount++;
        
        for (int i = 0; i < length; i++) {
            int item = transaction[i];
            TransactionTreeNode child = current.getChildWithId(item);
            if (child == null) {
                child = current.addChild(item);
            }
            child.count++;
            current = child;
        }
    }
    
    /**
     * Insert a transaction into the tree using a primitive int array.
     * <p>
     * This is a convenience method that inserts the entire array as a transaction.
     * It is equivalent to calling {@code insertTransaction(transaction, transaction.length)}.
     * </p>
     * 
     * @param transaction the sorted array of items in the transaction
     */
    public void insertTransaction(int[] transaction) {
        if (transaction == null) {
            return;
        }
        insertTransaction(transaction, transaction.length);
    }
    
    /**
     * Get the total number of transactions in the tree
     * @return transaction count
     */
    public int getTransactionCount() {
        return transactionCount;
    }
    
    /**
     * Get the root node
     * @return root node
     */
    public TransactionTreeNode getRoot() {
        return root;
    }
    
    /**
     * Inner class representing a node in the transaction tree
     */
    public static class TransactionTreeNode {
        /** Item id for this node */
        int itemId;
        
        /** Count of transactions passing through this node */
        int count;
        
        /** Parent node */
        TransactionTreeNode parent;
        
        /** Children nodes */
        List<TransactionTreeNode> children;
        
        /** Start id of the interval for this node */
        int startId;
        
        /** End id of the interval for this node */
        int endId;
        
        /**
         * Constructor
         * @param itemId the item id
         */
        public TransactionTreeNode(int itemId) {
            this.itemId = itemId;
            this.count = 0;
            this.children = new ArrayList<>();
            this.startId = 0;
            this.endId = 0;
        }
        
        /**
         * Get child node with specified item id
         * @param id the item id
         * @return the child node or null
         */
        public TransactionTreeNode getChildWithId(int id) {
            for (int i = 0, size = children.size(); i < size; i++) {
                TransactionTreeNode child = children.get(i);
                if (child.itemId == id) {
                    return child;
                }
            }
            return null;
        }
        
        /**
         * Add a new child with the specified item id
         * @param id the item id
         * @return the new child node
         */
        public TransactionTreeNode addChild(int id) {
            TransactionTreeNode child = new TransactionTreeNode(id);
            child.parent = this;
            children.add(child);
            return child;
        }
        
        /**
         * Get the item id
         * @return item id
         */
        public int getItemId() {
            return itemId;
        }
        
        /**
         * Get the count
         * @return count
         */
        public int getCount() {
            return count;
        }
        
        /**
         * Get the children
         * @return list of children
         */
        public List<TransactionTreeNode> getChildren() {
            return children;
        }
        
        /**
         * Check if this is a leaf node
         * @return true if leaf
         */
        public boolean isLeaf() {
            return children.isEmpty();
        }
        
        @Override
        public String toString() {
            return "Node[item=" + itemId + ", count=" + count + 
                   ", interval=[" + startId + "," + endId + "]]";
        }
    }
}