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
import java.util.HashMap;
import java.util.List;

/**
 * Class representing a node of a FP-Tree
 * 
 * @see AlgoCMAR
 */
public class FPNode {
    /**
     * Item saved in current node
     */
    Short item = -1;

    /**
     * Frequency of occurrence for current item
     */
    long support = 1;

    /**
     * Support for this item for each class
     */
    HashMap<Short, Long> supportByklass;

    /**
     * Parent of the current node, null if it is root element
     */
    FPNode parent = null;

    /**
     * Array of immediate childs from current node
     */
    List<FPNode> childs = new ArrayList<FPNode>();

    /**
     * Next node with the same item, used to create the header table
     */
    FPNode nextNode = null;

    /**
     * Default constructor
     */
    FPNode() {
    }

    /**
     * Search in children the specified item, return the child with this item
     * 
     * @param item to look for
     * @return the node with the item, or null otherwise
     */
    FPNode getChildByItem(short item) {
        // Search item in childs
        for (FPNode child : childs) {
            if (child.item == item) {
                return child;
            }
        }

        return null;
    }
}
