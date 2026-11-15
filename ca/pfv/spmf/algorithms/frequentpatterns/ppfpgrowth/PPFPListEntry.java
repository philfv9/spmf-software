package ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth;
/*
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Illyes Hamimid et al. 2026.
 * 
 * Do not remove the copyright and license information.
 */

/**
 * An entry of a PPFP-list: the measures of a pattern that satisfies the three
 * thresholds of {@link AlgoPPFPgrowth}.
 */
final class PPFPListEntry {

    /** expected support: sum over the transactions of the existential probability of the pattern */
    final double expectedSupport;

    /** periodicity: largest period (in transactions) when every occurrence is present */
    final int periodicity;

    /** probability that the pattern is periodic (total probabilistic periodicity), in [0, 1] */
    final double periodicProbability;

    /**
     * Constructor.
     *
     * @param expectedSupport     the expected support
     * @param periodicity         the periodicity
     * @param periodicProbability the probability of periodicity
     */
    PPFPListEntry(double expectedSupport, int periodicity, double periodicProbability) {
        this.expectedSupport = expectedSupport;
        this.periodicity = periodicity;
        this.periodicProbability = periodicProbability;
    }
}
