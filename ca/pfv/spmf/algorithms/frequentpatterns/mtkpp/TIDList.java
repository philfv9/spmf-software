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

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a TID list used by the MTKPP algorithm.
 *
 * @see AlgoMTKPP
 * @author Philippe Fournier-Viger
 */
public class TIDList {

    /** the item represented by this TID list */
    Integer item;

    /** the list of transaction ids where this item appears */
    List<Integer> elements = new ArrayList<Integer>();

    /** the largest periodicity of this pattern */
    int largestPeriodicity = 0;

    /**
     * Constructor.
     * @param item the item that is used for this TID list
     */
    public TIDList(Integer item) {
        this.item = item;
    }

    /**
     * Get the largest periodicity of the pattern represented by this TID list.
     * @return the largest periodicity
     */
    public int getLargestPeriodicity() {
        return largestPeriodicity;
    }

    /**
     * Add a transaction id to this TID list.
     * @param tid the transaction id to add
     */
    public void addElement(Integer tid) {
        elements.add(tid);
    }

    /**
     * Get the support of the pattern represented by this TID list.
     * @return the support as a number of transactions
     */
    public int getSupport() {
        return elements.size();
    }
}