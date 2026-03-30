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
 * Class to represent what the authors called Structure, with the following
 * structure
 */
public class Structure {
    /**
     * Identifier for the instance being represented
     */
    private int idInstance;

    /**
     * Class for the instance
     */
    private short klass;

    /**
     * Index for the cRule
     */
    private int indexCRule;

    /**
     * Index for the wRule
     */
    private int indexWRule;

    /**
     * Constructor
     * 
     * @param idInstance Identifier for the current instance
     * @param klass      klass being represented at the current structure
     * @param indexCRule index for the cRule being represented
     * @param indexWRule index for the wRule being represented
     */
    public Structure(int idInstance, short klass, int indexCRule, int indexWRule) {
        this.idInstance = idInstance;
        this.klass = klass;
        this.indexCRule = indexCRule;
        this.indexWRule = indexWRule;
    }

    /**
     * Get the index for the current instance
     * 
     * @return the index for the instance
     */
    public int getdIdInstance() {
        return this.idInstance;
    }

    /**
     * Get the class for the current instance
     * 
     * @return the klass
     */
    public short getKlass() {
        return this.klass;
    }

    /**
     * Get index for the cRule
     * 
     * @return the index for the cRule
     */
    public int getIndexCRule() {
        return this.indexCRule;
    }

    /**
     * Get index for the wRule
     * 
     * @return the index for the wRule
     */
    public int getIndexWRule() {
        return this.indexWRule;
    }
}
