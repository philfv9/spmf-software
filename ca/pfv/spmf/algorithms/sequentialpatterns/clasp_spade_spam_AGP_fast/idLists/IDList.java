package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.Pattern;

/*
 * Copyright Antonio Gomariz Peñalver 2013
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */
/**
 * Interface for a IdList class. If we are interested in adding any other kind
 * of IdList, we can create a new one if there we implement the methods here exposed.
 *
 * @author agomariz
 */
public interface IDList {

    /**
     * It return the intersection IdList that results from the current object and
     * the IdList given as an argument.
     * @param idList IdList with which we join the current IdList.
     * @param equals Flag indicating if we want a intersection for equal relation,
     * or, if it is false, an after relation.
     * @param minSupport Minimum relative support.
     * @return the intersection
     */
    public IDList join(IDList idList, boolean equals, int minSupport);

    /**
     * Get the minimum relative support outlined by the IdList, i.e. the number
     * of sequences with any appearance on it.
     * @return the support
     */
    public int getSupport();

    /**
     * Get the string representation of this IdList.
     * @return the string representation of this idlist
     */
    @Override
    public String toString();

    /**
     * It moves to a pattern the sequences where the Idlist is active.
     * @param pattern the pattern
     */
    public void setAppearingSequences(Pattern pattern);

    /**
     * It clears the IdList.
     */
    public void clear();

    /**
     * Returns this id-list to a reuse pool once it is no longer needed, e.g.
     * because the candidate pattern it was built for did not meet the minimum
     * support. Implementations that do not support pooling can simply leave
     * this as a no-op, which is what this default implementation does.
     */
    default void recycle() {
    }
}