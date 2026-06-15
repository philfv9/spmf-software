package ca.pfv.spmf.algorithms.frequentpatterns.mtkpp;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
* Do not remove copyright and license information from files.
*/

import java.util.Arrays;

/**
 * This class represents an itemset with support and periodicity values for the MTKPP algorithm.
 *
 * @see AlgoMTKPP
 * @author Philippe Fournier-Viger
 */
public class Itemset {

    /** the items in the itemset */
    int[] itemset;

    /** the support of the itemset */
    int support;

    /** the smallest periodicity of the itemset */
    int smallestPeriodicity;

    /** the largest periodicity of the itemset */
    int largestPeriodicity;

    /**
     * Constructor for an itemset.
     * @param itemset the items in the itemset
     * @param support the support of the itemset
     * @param smallestPeriodicity the smallest periodicity
     * @param largestPeriodicity the largest periodicity
     */
    public Itemset(int[] itemset, int support, int smallestPeriodicity, int largestPeriodicity) {
        this.itemset = itemset;
        this.support = support;
        this.smallestPeriodicity = smallestPeriodicity;
        this.largestPeriodicity = largestPeriodicity;
    }

    /**
     * Return a string representation of this itemset.
     * @return a string with items, support, and periodicity information
     */
    public String toString() {
        return Arrays.toString(itemset)
                + " support:" + support
                + " minPer:" + smallestPeriodicity
                + " maxPer:" + largestPeriodicity;
    }
}