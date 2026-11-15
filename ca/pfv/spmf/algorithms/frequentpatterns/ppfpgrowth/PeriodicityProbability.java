package ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth;
import java.util.ArrayList;
import java.util.List;

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
 * Computes the probability that a pattern is periodic in an uncertain
 * database (the probability of periodicity P<sub>&le;maxPer</sub>(X),
 * Definition 13 of the paper), using the database decomposition of
 * PPFP-growth (Definitions 15-16 and Properties 4-6 of the paper). Paper:
 * I. Hamimid, F. Nouioua, S. Boutouhami, P. Fournier-Viger. Probabilistic
 * periodic frequent pattern mining in uncertain databases. Applied
 * Intelligence 56(15), article 469, 2026.
 * <a href="https://doi.org/10.1007/s10489-026-07388-7">https://doi.org/10.1007/s10489-026-07388-7</a>
 * <p>
 * <b>Model.</b> A pattern X occurs in the transactions with TIDs
 * t<sub>1</sub> &lt; ... &lt; t<sub>k</sub> of a database of n transactions,
 * with existential probabilities q<sub>1</sub>, ..., q<sub>k</sub>
 * (q<sub>i</sub> = E<sub>t_i</sub>(X), the product of the
 * probabilities of the items of X in t<sub>i</sub>). In the possible-world model
 * the occurrences are independent: occurrence i is present with probability
 * q<sub>i</sub> and absent with probability 1 - q<sub>i</sub>. In a world w,
 * the periods of X are the differences between consecutive present
 * occurrences, including the period from TID 0 to the first one and from the
 * last one to TID n. X is periodic in w if all of them are &lt;= maxPer.
 * P<sub>&le;maxPer</sub>(X) is the total probability of the worlds where X is
 * periodic.
 * <p>
 * <b>Decomposition.</b> Certain occurrences (q = 1) are present in every world.
 * They split the uncertain occurrences into independent segments; a segment
 * whose surrounding certain occurrences (or database bounds) are at most maxPer
 * apart is periodic in every world (probability 1). The probability is
 * therefore the product, over the other ("critical") segments, of the
 * probability that the segment is periodic. Within a critical segment the
 * probability is the sum over the worlds that keep every merged period
 * &lt;= maxPer: the world where all occurrences are present, the worlds where
 * exactly one is absent, and the worlds where two or more are absent (enumerated
 * by {@link #probabilityOfSeveralAbsent}).
 * <p>
 * <b>Assumptions</b> (checked by the caller): TIDs are strictly increasing and
 * in [1, n]; probabilities are in (0, 1]; the periodicity computed with every
 * occurrence present is &lt;= maxPer.
 * <p>
 * <b>Complexity.</b> The enumeration of the worlds with several absent
 * occurrences is exponential in the number of occurrences of a critical
 * segment in the worst case (for example for a large maxPer and long runs of
 * uncertain occurrences).
 */
final class PeriodicityProbability {

    /** No instances: this class only provides static methods. */
    private PeriodicityProbability() {
    }

    /**
     * Compute the probability that a pattern is periodic.
     *
     * @param tids           TIDs of the transactions containing the pattern, strictly increasing
     * @param probabilities  existential probability of the pattern in each of these transactions, in (0, 1]
     * @param count          number of occurrences to use (prefix of the two arrays)
     * @param databaseSize   number of transactions n of the database
     * @param maxPer         maximum periodicity threshold
     * @param minProbability minimum probability threshold (beta) used for early pruning
     * @return the exact probability if it is &gt;= minProbability; otherwise a value
     *         &lt; minProbability (the computation stops at the first critical segment
     *         whose probability is &lt; minProbability, as allowed by the
     *         decomposition: the product can only decrease)
     */
    static double compute(int[] tids, double[] probabilities, int count, int databaseSize, int maxPer,
            double minProbability) {
        int[] periods = periods(tids, count, databaseSize);
        double probability = 1.0;
        int i = 0;
        while (i < count) {
            if (probabilities[i] == 1.0) {
                i++;
                continue;
            }
            // occurrences start..i-1 are uncertain and lie between two certain occurrences
            // (or the database bounds) that are 'span' transactions apart
            int start = i;
            int span = 0;
            while (i < count && probabilities[i] < 1.0) {
                span += periods[i];
                i++;
            }
            span += periods[i];
            if (span > maxPer) {
                double segmentProbability = segmentProbability(start, i - 1, probabilities, periods, maxPer);
                if (segmentProbability < minProbability) {
                    return probability * segmentProbability;
                }
                probability *= segmentProbability;
            }
        }
        return probability;
    }

    /**
     * Periods of the pattern when every occurrence is present: periods[i] is the
     * period ending at occurrence i (from TID 0 for i = 0) and periods[count] is
     * the period from the last occurrence to the end of the database.
     *
     * @param tids         TIDs of the occurrences, strictly increasing
     * @param count        number of occurrences to use (prefix of tids)
     * @param databaseSize number of transactions n of the database
     * @return the count + 1 periods
     */
    static int[] periods(int[] tids, int count, int databaseSize) {
        int[] periods = new int[count + 1];
        int previous = 0;
        for (int i = 0; i < count; i++) {
            periods[i] = tids[i] - previous;
            previous = tids[i];
        }
        periods[count] = databaseSize - previous;
        return periods;
    }

    /**
     * Probability that the pattern is periodic within the critical segment made
     * of the uncertain occurrences first..last (indexes in the occurrence list).
     *
     * @param first         index of the first occurrence of the segment
     * @param last          index of the last occurrence of the segment
     * @param probabilities existential probabilities of the occurrences
     * @param periods       periods when every occurrence is present (see {@link #periods})
     * @param maxPer        maximum periodicity threshold
     * @return the probability that every period within the segment is &lt;= maxPer
     */
    private static double segmentProbability(int first, int last, double[] probabilities, int[] periods, int maxPer) {
        double allPresent = 1.0;
        for (int j = first; j <= last; j++) {
            allPresent *= probabilities[j];
        }
        double probability = allPresent;

        // worlds where exactly occurrence j is absent: its two periods merge
        for (int j = first; j <= last; j++) {
            if (periods[j] + periods[j + 1] <= maxPer) {
                probability += allPresent / probabilities[j] * (1 - probabilities[j]);
            }
        }

        // worlds where two or more occurrences are absent
        List<Integer> candidates = new ArrayList<Integer>();
        for (int j = first; j <= last; j++) {
            candidates.add(j);
        }
        int[] absent = new int[candidates.size()];
        probability += probabilityOfSeveralAbsent(absent, 0, probabilities, periods, maxPer, candidates, allPresent);
        return probability;
    }

    /**
     * Sum of the probabilities of the periodic worlds in which the occurrences
     * absent[0..absentCount-1] are absent, at least two more absent occurrences
     * are chosen from 'candidates' (all greater than absent[absentCount-1]) and
     * every other occurrence of the segment is present.
     * <p>
     * The first candidate c is tentatively made absent (absent[absentCount] = c).
     * For every other candidate d, the world "absent[0..absentCount] + d absent" is
     * counted if its longest merged period is &lt;= maxPer; otherwise d is dropped
     * for all larger worlds that contain c (making more occurrences absent can only
     * lengthen periods). The method then recurses (1) with c kept absent and (2)
     * without c.
     *
     * @param absent           buffer holding the indexes of the occurrences already absent
     * @param absentCount      number of entries of 'absent' in use
     * @param probabilities    existential probabilities of the occurrences
     * @param periods          periods when every occurrence is present (see {@link #periods})
     * @param maxPer           maximum periodicity threshold
     * @param candidates       increasing indexes of the occurrences that may still be made absent
     * @param worldProbability probability of the world where exactly absent[0..absentCount-1]
     *                         are absent within the segment
     * @return the sum of the probabilities of the periodic worlds described above
     */
    private static double probabilityOfSeveralAbsent(int[] absent, int absentCount, double[] probabilities,
            int[] periods, int maxPer, List<Integer> candidates, double worldProbability) {
        if (candidates.size() < 2) {
            return 0;
        }
        List<Integer> others = new ArrayList<Integer>(candidates);
        int newAbsent = others.remove(0);
        absent[absentCount] = newAbsent;
        List<Integer> stillPossible = new ArrayList<Integer>(others);
        double withNewAbsent = worldProbability / probabilities[newAbsent] * (1 - probabilities[newAbsent]);

        // longest closed merged period and length of the open merged period ending at newAbsent
        int openPeriod = periods[absent[0]];
        int longestPeriod = openPeriod;
        for (int i = 1; i <= absentCount; i++) {
            if (absent[i] - absent[i - 1] == 1) {
                openPeriod += periods[absent[i]];
                longestPeriod = Math.max(longestPeriod, openPeriod);
            } else {
                openPeriod += periods[absent[i - 1] + 1];
                longestPeriod = Math.max(longestPeriod, openPeriod);
                openPeriod = periods[absent[i]];
            }
        }

        double sum = 0;
        for (int candidate : others) {
            int longest = longestPeriod;
            if (candidate - newAbsent == 1) {
                longest = Math.max(longest, openPeriod + periods[candidate] + periods[candidate + 1]);
            } else {
                longest = Math.max(longest, openPeriod + periods[newAbsent + 1]);
                longest = Math.max(longest, periods[candidate] + periods[candidate + 1]);
            }
            if (longest > maxPer) {
                stillPossible.remove(Integer.valueOf(candidate));
            } else {
                sum += withNewAbsent / probabilities[candidate] * (1 - probabilities[candidate]);
            }
        }

        sum += probabilityOfSeveralAbsent(absent, absentCount + 1, probabilities, periods, maxPer, stillPossible,
                withNewAbsent);
        sum += probabilityOfSeveralAbsent(absent, absentCount, probabilities, periods, maxPer, others,
                worldProbability);
        return sum;
    }
}
