package ca.pfv.spmf.algorithms.sequentialpatterns.vepreco;

/*
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
 * Copyright (c) Chen Hui
 *
 * Do not remove copyright and license information.
 */
/**
 * Holds the s-candidates and i-candidates computed for a node as plain sorted
 * int arrays, avoiding object allocation per candidate entry.
 *
 * @author Chen Hui
 */
class CandidateSet {

    /** Items eligible for s-extension, sorted; valid entries are in [0, sCandCount). */
    int[] sCands;

    /** Number of valid entries in sCands. */
    int sCandCount;

    /** Items eligible for i-extension, sorted; valid entries are in [0, iCandCount). */
    int[] iCands;

    /** Number of valid entries in iCands. */
    int iCandCount;

    /**
     * Allocates empty candidate arrays for the given maximum sizes.
     *
     * @param maxS maximum number of s-candidates
     * @param maxI maximum number of i-candidates
     */
    CandidateSet(int maxS, int maxI) {
        sCands = new int[Math.max(maxS, 0)];
        iCands = new int[Math.max(maxI, 0)];
        sCandCount = 0;
        iCandCount = 0;
    }
}