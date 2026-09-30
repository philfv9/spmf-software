package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators;

import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDList;
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
 * Interface for a IdList Creator. If we are interested in adding any other kind
 * of IdList, we will have to define a creator which implements these three methods.
 *
 * @author agomariz
 */
public interface IdListCreator {

    /**
     * It creates an empty IdList.
     * @return the idlist
     */
    public IDList create();

    /**
     * It adds an appearance <sid,tid> in a sequence to an Idlist.
     * Uses primitive ints (instead of boxed Integer) since this is called once per
     * item occurrence while loading the database, so avoiding autoboxing here
     * matters for large datasets.
     * @param idlist the idlist
     * @param sequence the sequence
     * @param timestamp the timestamp
     */
    public void addAppearance(IDList idlist, int sequence, int timestamp);

    /**
     * It adds several appearances <sid, {tid_1,tid_2,...,tid_n}> in a sequence to an Idlist
     * @param idlist the idlist
     * @param sequence the sequence
     * @param items a list of items
     */
    public void addAppearancesInSequence(IDList idlist, int sequence, List<Integer> items);
}