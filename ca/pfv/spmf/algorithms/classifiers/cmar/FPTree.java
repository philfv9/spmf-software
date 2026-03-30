/*
 * This file is part of SPMF data mining library.
 * It is adapted from some GPL code obtained from the LAC library, which used some SPMF code.
 *
 * Copyright (C) SPMF, LAC
 *   
 * LAC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details. You should have 
 * received a copy of the GNU General Public License along with 
 * this program.  If not, see http://www.gnu.org/licenses/
 */
package ca.pfv.spmf.algorithms.classifiers.cmar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class representing an FP-Tree, used to obtain classification rules.
 * This is a modification of the typical FP-Tree structure, to be used
 * by the CMAR algorithm.
 * 
 * @see AlgoCMAR
 */
public class FPTree {
    /**
     * List of contained items in the header table
     */
    List<Short> headerList = null;

    /**
     * List of items and its respective frequency
     */
    Map<Short, FPNode> mapItemNodes = new HashMap<Short, FPNode>();

    /**
     * Leaf for each item
     */
    Map<Short, FPNode> mapItemLastNode = new HashMap<Short, FPNode>();

    /**
     * Parent (root) element for the tree
     */
    FPNode root = new FPNode();

    /**
     * Default constructor
     */
    public FPTree() {
    }

    /**
     * Adds a transaction to the FP-Tree being created
     * 
     * @param transaction a list of transactions to be added in the FP-Tree
     * @param klass       the class value of the current transaction
     */
    public void addInstance(List<Short> transaction, Short klass) {
        FPNode currentNode = root;

        // For each item in the transaction
        for (Short item : transaction) {
            // Check if it is part of the current tree
            FPNode child = currentNode.getChildByItem(item);

            // If not
            if (child == null) {
                // Create a new node and add it to the tree
                FPNode newNode = new FPNode();
                newNode.item = item;
                newNode.parent = currentNode;
                newNode.supportByklass = new HashMap<Short, Long>();
                newNode.supportByklass.put(klass, 1L);
                currentNode.childs.add(newNode);

                currentNode = newNode;

                // update header table
                updateHeaderTable(item, newNode);
            } else {
            	// Otherwise, update the support of the current node
                child.support++;

                Long counterByKlass = child.supportByklass.getOrDefault(klass, 0L);
                child.supportByklass.put(klass, counterByKlass + 1);

                currentNode = child;
            }
        }
    }

    /**
     * Method to update header table after inserting a new node link,
     * so as to consider all the leaf nodes
     * 
     * @param item    An item to be added to the current FP-Tree
     * @param newNode The new node containing the item
     */
    private void updateHeaderTable(Short item, FPNode newNode) {
        // Search for the leaf node for current item
        FPNode lastNode = mapItemLastNode.get(item);
        if (lastNode != null) {
            // if not null, then we add the new node to the node link of the last node
            lastNode.nextNode = newNode;
        }

        // Set current new node as the last node
        mapItemLastNode.put(item, newNode);

        // Add node to the list of items in header table
        FPNode headernode = mapItemNodes.get(item);
        if (headernode == null) {
            mapItemNodes.put(item, newNode);
        }
    }

    /**
     * Add a new prefixPath to the current FP-Tree
     * 
     * @param prefixPath      The path to be added to the tree
     * @param mapSupportBeta  A map indicating the support of attribute values 
     * @param relativeMinsupp The minimum support threshold
     */
    @SuppressWarnings("unchecked")
    void addPrefixPath(List<FPNode> prefixPath, Map<Short, Long> mapSupportBeta, long relativeMinsupp) {
        // Get the support of the current path
        long pathCount = prefixPath.get(0).support;

        // For each node in the prefix path
        FPNode currentNode = root;
        for (int i = prefixPath.size() - 1; i >= 1; i--) {
            FPNode pathItem = prefixPath.get(i);
            
            // If the item is frequent
            if (mapSupportBeta.get(pathItem.item) >= relativeMinsupp) {
                // Check if this node is in the current FP-Tree
                FPNode child = currentNode.getChildByItem(pathItem.item);

                if (child == null) {
                    // Create a new node with current item
                    FPNode newNode = new FPNode();
                    newNode.item = pathItem.item;
                    newNode.parent = currentNode;
                    newNode.support = pathCount;
                    newNode.supportByklass = (HashMap<Short, Long>) prefixPath.get(0).supportByklass.clone();

                    currentNode.childs.add(newNode);
                    currentNode = newNode;
                    // Update the header table.
                    updateHeaderTable(pathItem.item, newNode);
                } else {
                    // Update support for each class in current existing node
                    for (Entry<Short, Long> entry : prefixPath.get(0).supportByklass.entrySet()) {
                        Long counter = child.supportByklass.get(entry.getKey());

                        if (counter == null) {
                            child.supportByklass.put(entry.getKey(), entry.getValue());
                        } else {
                            child.supportByklass.put(entry.getKey(), counter + entry.getValue());
                        }
                    }

                    child.support += pathCount;
                    currentNode = child;
                }
            }
        }
    }

    /**
     * Create the header table in descending order of support
     * 
     * @param mapSupport the frequencies of each item
     */
    void createHeaderList(final Map<Short, Long> mapSupport) {

    	// Copy all the keys from the map into the header table list
        headerList = new ArrayList<Short>(mapItemNodes.size());
        for(Entry<Short, FPNode> entry:  mapItemNodes.entrySet()) {
        	headerList.add(entry.getKey());
        }
        // Sort the header table list by descending order of support
        Collections.sort(headerList, new Comparator<Short>() {
            public int compare(Short id1, Short id2) {
                int compare = mapSupport.get(id1).compareTo(mapSupport.get(id1));

                // If support is equal, lexical order has to be checked
                return (compare == 0) ? (id1 - id2) : compare;
            }
        });
    }
}
