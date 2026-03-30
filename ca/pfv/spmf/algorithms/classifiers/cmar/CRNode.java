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

/**
 * Class representing a CRTree node, as used by the CMAR algorithm.
 * 
 * @see AlgoCMAR
 */
public class CRNode {
    /**
     * Rule stored in the current node
     */
    RuleCMAR rule = null;

    /**
     * Link to the next node
     */
    CRNode next = null;

    /**
     * Constructor 
     * 
     * @param rule to be stored in the node
     */
    CRNode(RuleCMAR rule) {
        this.rule = rule;
    }
}
