/**
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
package ca.pfv.spmf.algorithms.classifiers.cba;

/**
 * Auxiliary class used to represent a replacement for a rule
 */
public class Replace implements Comparable<Replace>, Cloneable {
    /**
     * Identifier of the instance
     */
    private int idInstance;

    /**
     * Klass for the instance
     */
    private short klass;

    /**
     * Index of the rule acting as cRule
     */
    private int indexCRule;

    /**
     * Constructor
     * 
     * @param indexCRule index identifying the rule acting as cRule
     * @param idInstance Index for the instance
     * @param klass      of he instance
     */
    public Replace(int indexCRule, int idInstance, short klass) {
        this.indexCRule = indexCRule;
        this.idInstance = idInstance;
        this.klass = klass;
    }
 
    public Replace(Replace instance) {
        this.indexCRule = instance.indexCRule;
        this.idInstance = instance.idInstance;
        this.klass = instance.klass;
    }


    /**
     * Returns the index for the instance in the training dataset
     * 
     * @return the id for the instance
     */
    public int getdIdInstance() {
        return this.idInstance;
    }

    /**
     * Get the class
     * 
     * @return the class
     */
    public short getKlass() {
        return this.klass;
    }

    /**
     * Returns the index of the best rule classifying correctly the example
     * 
     * @return index of the rule acting as cRule
     */
    public int getIndexCRule() {
        return this.indexCRule;
    }

    /**
     * Function to compare objects of the Structure class. Necessary to be able to
     * use "sort" function It sorts in an decreasing order of example's position
     * 
     * @param a Replace object to compare with.
     * @return -1 if a is bigger, 1 if smaller and 0 otherwise.
     */
    @Override
    public int compareTo(Replace a) {
        return Integer.compare(idInstance,  a.idInstance);
    }

}
