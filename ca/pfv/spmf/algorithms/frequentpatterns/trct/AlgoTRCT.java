package ca.pfv.spmf.algorithms.frequentpatterns.trct;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
*
* Do not remove copyright and license information from this file.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the TR-CT algorithm for Mining Top-K Periodic Patterns
 * using Compressed Tidsets.
 *
 * <p>TR-CT discovers the top-K periodic-frequent patterns satisfying a maximum periodicity
 * constraint without a minimum support threshold. It uses a compressed tidset where runs of
 * consecutive tids are stored as (positive-start, negative-end) with a +1 shift to avoid
 * storing zero. All patterns tied at the K-th rank are returned.</p>
 *
 * <p>Reference: Amphawan, K., Lenca, P., Surarerks, A. (2011). Efficient mining Top-k
 * regular-frequent itemset using compressed tidsets. PAKDD BI Workshop, pp. 159-170.</p>
 *
 * @see CompressedTIDList
 * @author Philippe Fournier-Viger, 2016
 */
public class AlgoTRCT {

    /** the number of periodic patterns generated */
    public int patternCount = 0;

    /** the number of candidate patterns explored during search */
    public int candidateCount = 0;

    /** writer to write the output file */
    BufferedWriter writer = null;

    /** the database size (number of transactions) */
    protected int databaseSize = 0;

    /** maximum periodicity threshold */
    int maxPeriodicity;

    /** the number of top-K patterns to find */
    int k;

    /** minimum number of items that patterns should contain */
    int minimumLength = 0;

    /** maximum number of items that patterns should contain */
    int maximumLength = Integer.MAX_VALUE;

    /** the total execution time in milliseconds */
    public double totalExecutionTime = 0;

    /** the maximum memory usage in megabytes */
    public double maximumMemoryUsage = 0;

    /** min-heap of size k tracking the top-k supports; root is the smallest of the top-k supports */
    private PriorityQueue<Integer> topKHeap;

    /** the current k-th highest support used as a dynamic pruning boundary */
    private int kthHighestSupport;

    /** support pruning threshold; candidates strictly below this value are discarded */
    private int supportPruningThreshold;

    /** all valid patterns collected during mining before final top-k extraction */
    private List<CompressedTIDList> allPatterns;

    /**
     * Default constructor.
     */
    public AlgoTRCT() {
    }

    /**
     * Run the TR-CT algorithm on a transaction database file.
     * @param input the input file path
     * @param output the output file path
     * @param k the number of top-K patterns to find
     * @param maxPeriodicity the maximum periodicity threshold
     * @throws IOException exception if error while reading or writing files
     */
    public void runAlgorithm(String input, String output, int k, int maxPeriodicity)
            throws IOException {

        MemoryLogger.getInstance().reset();
        long startTimestamp = System.currentTimeMillis();

        this.k = k;
        this.maxPeriodicity = maxPeriodicity;

        writer = new BufferedWriter(new FileWriter(output));
        allPatterns = new ArrayList<CompressedTIDList>();
        topKHeap = new PriorityQueue<Integer>();
        kthHighestSupport = 0;
        supportPruningThreshold = 1;

        // ----------------------------------------------------------------
        // STEP 1: Single database scan – build one compressed tidset per item
        // ----------------------------------------------------------------
        Map<Integer, CompressedTIDList> mapItemToCT =
                new HashMap<Integer, CompressedTIDList>();
        databaseSize = 0;

        BufferedReader myInput = null;
        try {
            myInput = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(input))));
            String thisLine;
            while ((thisLine = myInput.readLine()) != null) {
                if (thisLine.isEmpty() ||
                        thisLine.charAt(0) == '#' ||
                        thisLine.charAt(0) == '%' ||
                        thisLine.charAt(0) == '@') {
                    continue;
                }
                int tid = databaseSize; // 0-based
                for (String token : thisLine.split(" ")) {
                    if (token.isEmpty()) continue;
                    int item = Integer.parseInt(token);
                    CompressedTIDList ct = mapItemToCT.get(item);
                    if (ct == null) {
                        ct = new CompressedTIDList(item);
                        mapItemToCT.put(item, ct);
                    }
                    ct.addTID(tid);
                }
                databaseSize++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (myInput != null) myInput.close();
        }

        // ----------------------------------------------------------------
        // STEP 2: Filter items by maxPeriodicity; sort survivors by support desc
        // ----------------------------------------------------------------
        List<CompressedTIDList> periodicItems = new ArrayList<CompressedTIDList>();
        for (CompressedTIDList ct : mapItemToCT.values()) {
            ct.largestPeriodicity = ct.computeLargestPeriodicity(databaseSize);
            if (ct.largestPeriodicity <= maxPeriodicity) {
                periodicItems.add(ct);
            }
        }
        mapItemToCT = null;

        Collections.sort(periodicItems, new Comparator<CompressedTIDList>() {
            public int compare(CompressedTIDList o1, CompressedTIDList o2) {
                return o2.support - o1.support;
            }
        });

        // Record valid 1-item patterns and prime the top-k heap
        for (CompressedTIDList ct : periodicItems) {
            if (1 >= minimumLength && 1 <= maximumLength) {
                recordPattern(ct);
            } else {
                updateHeap(ct.support);
            }
        }

        MemoryLogger.getInstance().checkMemory();

        // ----------------------------------------------------------------
        // STEP 3: Recursive ECLAT-style search over compressed tidsets
        // ----------------------------------------------------------------
        search(periodicItems, 1);

        // ----------------------------------------------------------------
        // STEP 4: Extract top-k with tie handling and write to output file
        // ----------------------------------------------------------------
        List<CompressedTIDList> topKPatterns = extractTopKWithTies();
        for (CompressedTIDList ct : topKPatterns) {
            writePattern(ct);
        }
        patternCount = topKPatterns.size();

        MemoryLogger.getInstance().checkMemory();
        writer.close();
        totalExecutionTime = System.currentTimeMillis() - startTimestamp;
        maximumMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
    }

    /**
     * Recursive ECLAT-style search: combine each extension X with all subsequent
     * extensions Y to form XY, then recurse with XY as the new prefix context.
     * @param extensions list of compressed tidsets extending the current prefix
     * @param currentPatternLength the item-count of patterns in the extensions list
     * @throws IOException exception if error while writing the file
     */
    private void search(List<CompressedTIDList> extensions, int currentPatternLength)
            throws IOException {

        int nextLength = currentPatternLength + 1;
        if (nextLength > maximumLength) {
            return;
        }

        for (int i = 0; i < extensions.size(); i++) {
            CompressedTIDList X = extensions.get(i);
            List<CompressedTIDList> newExtensions = new ArrayList<CompressedTIDList>();

            for (int j = i + 1; j < extensions.size(); j++) {
                CompressedTIDList Y = extensions.get(j);
                candidateCount++;

                CompressedTIDList XY = intersect(X, Y, nextLength);
                if (XY != null) {
                    newExtensions.add(XY);
                    if (nextLength >= minimumLength && nextLength <= maximumLength) {
                        recordPattern(XY);
                    } else {
                        updateHeap(XY.support);
                    }
                }
            }

            if (!newExtensions.isEmpty()) {
                search(newExtensions, nextLength);
            }
        }

        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Intersect the compressed tidsets of X and Y to produce the tidset of itemset XY.
     * @param px the compressed tidset of itemset X
     * @param py the compressed tidset of itemset Y
     * @param nextLength the item-count of the resulting pattern XY
     * @return the compressed tidset of XY, or null if pruned by periodicity or support
     */
    private CompressedTIDList intersect(CompressedTIDList px, CompressedTIDList py,
                                         int nextLength) {
        // Build the items array: all items of X plus the last item of Y
        int[] xyItems = new int[px.items.length + 1];
        System.arraycopy(px.items, 0, xyItems, 0, px.items.length);
        xyItems[px.items.length] = py.items[py.items.length - 1];

        CompressedTIDList pxy = new CompressedTIDList(xyItems);

        List<Integer> ex = px.elements;
        List<Integer> ey = py.elements;

        // State machine for X: position in ex, current tid, run-end (-1 if not in run)
        int xi = 0;
        int tidX = -1, runEndX = -1;

        // State machine for Y: position in ey, current tid, run-end (-1 if not in run)
        int yi = 0;
        int tidY = -1, runEndY = -1;

        // Load first tid for X
        if (xi < ex.size()) {
            int val = ex.get(xi);
            tidX = CompressedTIDList.decodePos(val);
            if (xi + 1 < ex.size() && ex.get(xi + 1) < 0) {
                runEndX = CompressedTIDList.decodeNeg(ex.get(xi + 1));
                // xi stays at the run-start entry; we advance xi by 2 when done with run
            } else {
                runEndX = -1;
                xi++;
            }
        } else {
            return null; // X is empty
        }

        // Load first tid for Y
        if (yi < ey.size()) {
            int val = ey.get(yi);
            tidY = CompressedTIDList.decodePos(val);
            if (yi + 1 < ey.size() && ey.get(yi + 1) < 0) {
                runEndY = CompressedTIDList.decodeNeg(ey.get(yi + 1));
            } else {
                runEndY = -1;
                yi++;
            }
        } else {
            return null; // Y is empty
        }

        // Merge-intersect loop
        outer:
        while (true) {
            if (tidX == tidY) {
                pxy.addTID(tidX);
                // Advance X
                if (runEndX >= 0 && tidX < runEndX) {
                    tidX++;
                } else {
                    int nextXi = (runEndX >= 0) ? xi + 2 : xi;
                    if (nextXi >= ex.size()) break outer;
                    int val = ex.get(nextXi);
                    tidX = CompressedTIDList.decodePos(val);
                    if (nextXi + 1 < ex.size() && ex.get(nextXi + 1) < 0) {
                        runEndX = CompressedTIDList.decodeNeg(ex.get(nextXi + 1));
                        xi = nextXi;
                    } else {
                        runEndX = -1;
                        xi = nextXi + 1;
                    }
                }
                // Advance Y
                if (runEndY >= 0 && tidY < runEndY) {
                    tidY++;
                } else {
                    int nextYi = (runEndY >= 0) ? yi + 2 : yi;
                    if (nextYi >= ey.size()) break outer;
                    int val = ey.get(nextYi);
                    tidY = CompressedTIDList.decodePos(val);
                    if (nextYi + 1 < ey.size() && ey.get(nextYi + 1) < 0) {
                        runEndY = CompressedTIDList.decodeNeg(ey.get(nextYi + 1));
                        yi = nextYi;
                    } else {
                        runEndY = -1;
                        yi = nextYi + 1;
                    }
                }
            } else if (tidX < tidY) {
                // Advance X
                if (runEndX >= 0 && tidX < runEndX) {
                    tidX++;
                } else {
                    int nextXi = (runEndX >= 0) ? xi + 2 : xi;
                    if (nextXi >= ex.size()) break outer;
                    int val = ex.get(nextXi);
                    tidX = CompressedTIDList.decodePos(val);
                    if (nextXi + 1 < ex.size() && ex.get(nextXi + 1) < 0) {
                        runEndX = CompressedTIDList.decodeNeg(ex.get(nextXi + 1));
                        xi = nextXi;
                    } else {
                        runEndX = -1;
                        xi = nextXi + 1;
                    }
                }
            } else {
                // tidX > tidY: Advance Y
                if (runEndY >= 0 && tidY < runEndY) {
                    tidY++;
                } else {
                    int nextYi = (runEndY >= 0) ? yi + 2 : yi;
                    if (nextYi >= ey.size()) break outer;
                    int val = ey.get(nextYi);
                    tidY = CompressedTIDList.decodePos(val);
                    if (nextYi + 1 < ey.size() && ey.get(nextYi + 1) < 0) {
                        runEndY = CompressedTIDList.decodeNeg(ey.get(nextYi + 1));
                        yi = nextYi;
                    } else {
                        runEndY = -1;
                        yi = nextYi + 1;
                    }
                }
            }
        }

        if (pxy.support == 0) return null;

        pxy.largestPeriodicity = pxy.computeLargestPeriodicity(databaseSize);
        if (pxy.largestPeriodicity > maxPeriodicity) return null;
        if (pxy.support < supportPruningThreshold) return null;

        return pxy;
    }

    /**
     * Record a pattern in allPatterns and update the top-k heap and pruning threshold.
     * @param ct the compressed tidset of the pattern to record
     */
    private void recordPattern(CompressedTIDList ct) {
        allPatterns.add(ct);
        updateHeap(ct.support);
    }

    /**
     * Update the top-k min-heap with a new support value and raise the pruning threshold if possible.
     * @param support the support value to consider for the top-k heap
     */
    private void updateHeap(int support) {
        if (topKHeap.size() < k) {
            topKHeap.offer(support);
        } else if (support > topKHeap.peek()) {
            topKHeap.poll();
            topKHeap.offer(support);
        }
        if (topKHeap.size() == k) {
            kthHighestSupport = topKHeap.peek();
            int newThreshold = kthHighestSupport - 1;
            if (newThreshold > supportPruningThreshold) {
                supportPruningThreshold = newThreshold;
            }
        }
    }

    /**
     * Extract all top-K patterns including every pattern tied at the K-th rank.
     * @return list of all patterns whose support is at or above the k-th highest support
     */
    private List<CompressedTIDList> extractTopKWithTies() {
        if (allPatterns.isEmpty()) return new ArrayList<CompressedTIDList>();

        Collections.sort(allPatterns, new Comparator<CompressedTIDList>() {
            public int compare(CompressedTIDList o1, CompressedTIDList o2) {
                return o2.support - o1.support;
            }
        });

        List<CompressedTIDList> result = new ArrayList<CompressedTIDList>();
        if (allPatterns.size() <= k) {
            result.addAll(allPatterns);
        } else {
            int kthSupport = allPatterns.get(k - 1).support;
            for (CompressedTIDList ct : allPatterns) {
                if (ct.support >= kthSupport) result.add(ct);
                else break;
            }
        }
        return result;
    }

    /**
     * Write a single pattern to the output file in SPMF format.
     * @param ct the compressed tidset representing the pattern to write
     * @throws IOException exception if error while writing the file
     */
    private void writePattern(CompressedTIDList ct) throws IOException {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < ct.items.length; i++) {
            if (i > 0) buffer.append(' ');
            buffer.append(ct.items[i]);
        }
        buffer.append(" #SUP: ").append(ct.support);
        buffer.append(" #MAXPER: ").append(ct.largestPeriodicity);
        writer.write(buffer.toString());
        writer.newLine();
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  TR-CT ALGORITHM v2.66 =====");
        System.out.println(" Database size: " + databaseSize + " transactions");
        System.out.println(" K: " + k);
        System.out.println(" Max periodicity: " + maxPeriodicity);
        System.out.println(" Support pruning threshold: " + supportPruningThreshold);
        System.out.println(" Candidate count: " + candidateCount);
        System.out.println(" Time : " + totalExecutionTime + " ms");
        System.out.println(" Memory ~ " + maximumMemoryUsage + " MB");
        System.out.println(" Periodic patterns count : " + patternCount);
        System.out.println("===================================================");
    }

    /**
     * Set the minimum length for patterns to be found.
     * @param minimumLength the minimum number of items in a pattern
     */
    public void setMinimumLength(int minimumLength) {
        this.minimumLength = minimumLength;
    }

    /**
     * Set the maximum length for patterns to be found.
     * @param maximumLength the maximum number of items in a pattern
     */
    public void setMaximumLength(int maximumLength) {
        this.maximumLength = maximumLength;
    }
}