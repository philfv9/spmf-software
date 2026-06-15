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

/**
 * An item with its existential probability, as used by UH-Mine.
 *
 * @see AlgoUHMine
 * @see UncertainTransactionDatabaseUHMine
 * @author Philippe Fournier-Viger, 2015
 */
public class ItemUHMine {

    /** the item id */
    final int id;

    /** the existential probability of this item in its transaction */
    final double probability;

    /**
     * Constructor.
     *
     * @param id the item id
     * @param probability the existential probability
     */
    public ItemUHMine(int id, double probability) {
        this.id = id;
        this.probability = probability;
    }

    /**
     * Get the item id.
     *
     * @return the item id
     */
    public int getId() {
        return id;
    }

    /**
     * Get the existential probability.
     *
     * @return the probability
     */
    public double getProbability() {
        return probability;
    }

    /**
     * Return a string representation.
     *
     * @return a string
     */
    public String toString() {
        return id + "(" + probability + ")";
    }
}