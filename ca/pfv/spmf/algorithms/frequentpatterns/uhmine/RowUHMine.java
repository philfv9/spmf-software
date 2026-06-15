package ca.pfv.spmf.algorithms.frequentpatterns.uhmine;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger

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
 Do not remove copyright or license information.
 */

import java.util.ArrayList;
import java.util.List;

/**
 * A row in the UH-Mine header table.
 * Stores the item, its accumulated expected support, and pointers into the cell array.
 *
 * @see AlgoUHMine
 * @author Philippe Fournier-Viger, 2015
 */
class RowUHMine {

    /** the item id */
    int item;

    /** accumulated expected support in the current (projected) database */
    double expectedSupport = 0.0;

    /** pointers to positions in the cell arrays where this item occurs */
    List<Integer> pointers = new ArrayList<Integer>();

    /**
     * Constructor.
     *
     * @param item the item id
     */
    public RowUHMine(int item) {
        this.item = item;
    }

    /**
     * Return a string representation of this row.
     *
     * @return a string
     */
    public String toString() {
        return item + " expSup:" + expectedSupport + " pointers:" + pointers;
    }
}