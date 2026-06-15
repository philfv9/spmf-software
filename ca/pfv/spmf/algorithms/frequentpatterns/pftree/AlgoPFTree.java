package ca.pfv.spmf.algorithms.frequentpatterns.pftree;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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

import ca.pfv.spmf.tools.MemoryLogger;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * This is an implementation of the PFTree algorithm for discovering
 * Periodic-Frequent Patterns in Transactional Databases as described in:
 *
 * Tanbeer, S.K., Ahmed, C.F., Jeong, B.-S., Lee, Y.-K. (2009).
 * Discovering Periodic-Frequent Patterns in Transactional Databases.
 * PAKDD 2009, LNAI 5476, pp. 242-253. Springer.
 * https://doi.org/10.1007/978-3-642-01307-2_24
 *
 * @see PFTreeNode
 * @author Philippe Fournier-Viger, 2026
 */
public class AlgoPFTree {

    /** the total execution time of the last run */
    public double totalExecutionTime = 0;

    /** the maximum memory usage during the last run */
    public double maximumMemoryUsage = 0;

    /** the number of periodic-frequent patterns found */
    public int patternCount = 0;

    /** the number of transactions in the database */
    int databaseSize = 0;

    /** the maximum periodicity threshold (lambda) */
    int maxPeriodicity;

    /** the minimum support threshold as an absolute transaction count */
    int minSupportAbsolute;

    /** writer to write the output file */
    BufferedWriter writer = null;

    /** map from item to its support count collected during the first scan */
    Map<Integer, Integer> mapItemToSupport;

    /** map from item to its maximum periodicity collected during the first scan */
    Map<Integer, Integer> mapItemToMaxPeriodicity;

    /** map from item to the last transaction id seen during the first scan */
    Map<Integer, Integer> mapItemToLastTid;

    /**
     * the PF-list: ordered list of periodic-frequent items.
     * Each entry is int[]{item, support, maxPeriodicity}.
     * Sorted in support-descending order (ties broken by item id ascending).
     */
    List<int[]> pfList;

    /** map from item to its index (rank) in pfList */
    Map<Integer, Integer> mapItemToRank;

    /** the root node of the PF-tree */
    PFTreeNode root;

    /** map from item to the first node in the PF-tree for that item (node-link header) */
    Map<Integer, PFTreeNode> mapItemToFirstNode;

    /**
     * Default constructor.
     */
    public AlgoPFTree() {
    }

    /**
     * Runs the PFTree algorithm on the given input file and writes results to the output file.
     * @param input the path of the input transaction database file
     * @param output the path of the output file
     * @param maxPeriodicity the maximum periodicity threshold lambda
     * @param minSupportPercent the minimum support as a percentage of database size (0 to 100)
     * @throws IOException if an error occurs while reading or writing files
     */
    public void runAlgorithm(String input, String output,
                             int maxPeriodicity, double minSupportPercent) throws IOException {
        MemoryLogger.getInstance().reset();
        long startTimestamp = System.currentTimeMillis();

        this.maxPeriodicity = maxPeriodicity;
        patternCount = 0;

        writer = new BufferedWriter(new FileWriter(output));

        // ============================================================
        // FIRST SCAN: compute support and max periodicity for each item
        // ============================================================
        mapItemToSupport = new HashMap<>();
        mapItemToMaxPeriodicity = new HashMap<>();
        mapItemToLastTid = new HashMap<>();

        databaseSize = 0;
        BufferedReader myInput = null;
        String thisLine;

        try {
            myInput = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(input))));
            while ((thisLine = myInput.readLine()) != null) {
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
                    continue;
                }
                databaseSize++;
                String[] tokens = thisLine.split(" ");
                for (String token : tokens) {
                    int item = Integer.parseInt(token);

                    // update support
                    mapItemToSupport.merge(item, 1, Integer::sum);

                    // update max periodicity using current tid gap from last seen
                    int lastTid = mapItemToLastTid.getOrDefault(item, 0);
                    int period = databaseSize - lastTid;
                    mapItemToLastTid.put(item, databaseSize);

                    Integer curMax = mapItemToMaxPeriodicity.get(item);
                    if (curMax == null || period > curMax) {
                        mapItemToMaxPeriodicity.put(item, period);
                    }
                }
            }
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // Update periodicity with the final period (last occurrence -> end of database)
        for (Entry<Integer, Integer> entry : mapItemToLastTid.entrySet()) {
            int item = entry.getKey();
            int lastTid = entry.getValue();
            int lastPeriod = databaseSize - lastTid;
            Integer curMax = mapItemToMaxPeriodicity.get(item);
            if (curMax == null || lastPeriod > curMax) {
                mapItemToMaxPeriodicity.put(item, lastPeriod);
            }
        }

        // Compute absolute minimum support from percentage
        this.minSupportAbsolute = (int) Math.ceil(minSupportPercent * databaseSize / 100.0);
        if (this.minSupportAbsolute < 1) {
            this.minSupportAbsolute = 1;
        }

        // Build PF-list: only items satisfying both thresholds,
        // sorted support-descending (ties broken by item id ascending)
        pfList = new ArrayList<>();
        for (Entry<Integer, Integer> entry : mapItemToSupport.entrySet()) {
            int item = entry.getKey();
            int sup = entry.getValue();
            int per = mapItemToMaxPeriodicity.getOrDefault(item, Integer.MAX_VALUE);
            if (sup >= minSupportAbsolute && per <= maxPeriodicity) {
                pfList.add(new int[]{item, sup, per});
            }
        }
        pfList.sort((a, b) -> {
            int cmp = b[1] - a[1]; // descending support
            return (cmp != 0) ? cmp : (a[0] - b[0]); // ascending item id for ties
        });

        // Build rank map
        mapItemToRank = new HashMap<>();
        for (int i = 0; i < pfList.size(); i++) {
            mapItemToRank.put(pfList.get(i)[0], i);
        }

        // ============================================================
        // SECOND SCAN: build the PF-tree
        // ============================================================
        root = new PFTreeNode(-1);
        mapItemToFirstNode = new HashMap<>();

        try {
            myInput = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(input))));
            int tid = 0;
            while ((thisLine = myInput.readLine()) != null) {
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
                    continue;
                }
                String[] tokens = thisLine.split(" ");

                // Keep only PF items, sort by PF-list rank (support-descending)
                List<Integer> transaction = new ArrayList<>();
                for (String token : tokens) {
                    int item = Integer.parseInt(token);
                    if (mapItemToRank.containsKey(item)) {
                        transaction.add(item);
                    }
                }
                transaction.sort(Comparator.comparingInt(it -> mapItemToRank.get(it)));

                if (!transaction.isEmpty()) {
                    insertTransaction(root, mapItemToFirstNode, transaction, tid);
                }
                tid++;
            }
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // Free memory no longer needed
        mapItemToSupport = null;
        mapItemToMaxPeriodicity = null;
        mapItemToLastTid = null;

        // ============================================================
        // MINE: output length-1 patterns, then mine recursively
        // ============================================================

        // Output all length-1 periodic-frequent items
        for (int[] entry : pfList) {
            writeOut(new int[0], 0, entry[0], entry[1], entry[2]);
        }

        // Mine longer patterns
        mineTree(root, mapItemToFirstNode, pfList, new int[0], 0);

        MemoryLogger.getInstance().checkMemory();
        writer.close();

        totalExecutionTime = System.currentTimeMillis() - startTimestamp;
        maximumMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
    }

    /**
     * Inserts a transaction into the PF-tree rooted at the given node. The TIDS are stored in every node.
     * @param treeRoot the root of the tree to insert into
     * @param nodeLinks the node-link header map for the tree
     * @param transaction the sorted list of items in the transaction
     * @param tid the transaction id
     */
    private void insertTransaction(PFTreeNode treeRoot,
                                   Map<Integer, PFTreeNode> nodeLinks,
                                   List<Integer> transaction,
                                   int tid) {
        PFTreeNode current = treeRoot;

        for (int i = 0; i < transaction.size(); i++) {
            int item = transaction.get(i);
            PFTreeNode child = current.getChildWithItem(item);

            if (child == null) {
                child = new PFTreeNode(item);
                child.parent = current;
                current.children.add(child);
                appendNodeLink(nodeLinks, item, child);
            }

            // Store TID at EVERY node
            if (child.tidList == null) {
                child.tidList = new ArrayList<>();
            }
            child.tidList.add(tid);
            
            current = child;
        }
    }

    /**
     * Inserts a transaction into the PF-tree and attaches tids to all nodes.
     * @param treeRoot the root of the tree
     * @param nodeLinks the node-link header map
     * @param transaction the sorted list of items
     * @param tids the transaction IDs to attach
     */
    private void insertTransactionWithTids(PFTreeNode treeRoot,
                                           Map<Integer, PFTreeNode> nodeLinks,
                                           List<Integer> transaction,
                                           List<Integer> tids) {
        PFTreeNode current = treeRoot;

        for (int i = 0; i < transaction.size(); i++) {
            int item = transaction.get(i);
            PFTreeNode child = current.getChildWithItem(item);

            if (child == null) {
                child = new PFTreeNode(item);
                child.parent = current;
                current.children.add(child);
                appendNodeLink(nodeLinks, item, child);
            }

            // Add TIDs at every node
            if (child.tidList == null) {
                child.tidList = new ArrayList<>();
            }
            child.tidList.addAll(tids);
            
            current = child;
        }
    }

    /**
     * Appends a node to the end of the node-link chain for the given item.
     * @param nodeLinks the node-link header map
     * @param item the item whose chain is extended
     * @param node the new node to append
     */
    private void appendNodeLink(Map<Integer, PFTreeNode> nodeLinks, int item, PFTreeNode node) {
        PFTreeNode first = nodeLinks.get(item);
        if (first == null) {
            nodeLinks.put(item, node);
        } else {
            PFTreeNode cur = first;
            while (cur.nodeLink != null) {
                cur = cur.nodeLink;
            }
            cur.nodeLink = node;
        }
    }

    /**
     * Mines the PF-tree recursively using a bottom-up pattern-growth approach.
     * @param treeRoot the root of the current (conditional) PF-tree
     * @param nodeLinks the node-link header map for the current tree
     * @param currentPfList the PF-list of the current (conditional) tree
     * @param prefix the current prefix itemset buffer
     * @param prefixLength the number of valid items in the prefix buffer
     * @throws IOException if an error occurs while writing output
     */
    private void mineTree(PFTreeNode treeRoot,
                          Map<Integer, PFTreeNode> nodeLinks,
                          List<int[]> currentPfList,
                          int[] prefix,
                          int prefixLength) throws IOException {

        // Process items bottom-up in the PF-list
        for (int idx = currentPfList.size() - 1; idx >= 0; idx--) {
            int[] pfEntry = currentPfList.get(idx);
            int item = pfEntry[0];

            // Collect all nodes for this item via node-link chain
            List<PFTreeNode> nodes = new ArrayList<>();
            PFTreeNode cur = nodeLinks.get(item);
            while (cur != null) {
                nodes.add(cur);
                cur = cur.nodeLink;
            }
            if (nodes.isEmpty()) {
                continue;
            }

            // Collect (prefix-path, tidList) pairs
            List<int[]> pathItems = new ArrayList<>();
            List<List<Integer>> pathTids = new ArrayList<>();

            for (PFTreeNode node : nodes) {
                // Collect ancestor items (root-to-parent order)
                List<Integer> path = new ArrayList<>();
                PFTreeNode ancestor = node.parent;
                while (ancestor != null && ancestor.item != -1) {
                    path.add(0, ancestor.item);
                    ancestor = ancestor.parent;
                }
                int[] pathArr = new int[path.size()];
                for (int k = 0; k < path.size(); k++) {
                    pathArr[k] = path.get(k);
                }
                pathItems.add(pathArr);

                List<Integer> tids = (node.tidList != null)
                        ? node.tidList : Collections.emptyList();
                pathTids.add(tids);
            }

            // Collect all tids for prefix+item
            List<Integer> allTids = new ArrayList<>();
            for (List<Integer> tids : pathTids) {
                allTids.addAll(tids);
            }
            Collections.sort(allTids);
            
            int support = allTids.size();
            int periodicity = computePeriodicity(allTids);

            // Output prefix+item if it passes thresholds (except when prefixLength==0, already output)
            if (prefixLength >= 1) {
                if (support >= minSupportAbsolute && periodicity <= maxPeriodicity) {
                    writeOut(prefix, prefixLength, item, support, periodicity);
                } else {
                    continue; // Downward closure: prune
                }
            }

            // Build conditional PF-list
            Map<Integer, List<Integer>> condItemTids = new HashMap<>();

            for (int p = 0; p < pathItems.size(); p++) {
                int[] path = pathItems.get(p);
                List<Integer> tids = pathTids.get(p);
                if (tids.isEmpty()) continue;

                for (int ancestorItem : path) {
                    if (!isItemInPfListBefore(currentPfList, ancestorItem, idx)) {
                        continue;
                    }
                    condItemTids.computeIfAbsent(ancestorItem, k -> new ArrayList<>())
                            .addAll(tids);
                }
            }

            List<int[]> conditionalPfList = new ArrayList<>();
            for (int jdx = 0; jdx < idx; jdx++) {
                int ancItem = currentPfList.get(jdx)[0];
                List<Integer> tids = condItemTids.get(ancItem);
                if (tids == null || tids.isEmpty()) {
                    continue;
                }
                Collections.sort(tids);
                
                int condSup = tids.size();
                int condPer = computePeriodicity(tids);
                if (condSup >= minSupportAbsolute && condPer <= maxPeriodicity) {
                    conditionalPfList.add(new int[]{ancItem, condSup, condPer});
                }
            }

            if (conditionalPfList.isEmpty()) {
                continue;
            }

            // Build conditional PF-tree
            Set<Integer> condItemSet = new HashSet<>();
            Map<Integer, Integer> condRank = new HashMap<>();
            for (int r = 0; r < conditionalPfList.size(); r++) {
                int ancItem = conditionalPfList.get(r)[0];
                condItemSet.add(ancItem);
                condRank.put(ancItem, r);
            }

            PFTreeNode condRoot = new PFTreeNode(-1);
            Map<Integer, PFTreeNode> condNodeLinks = new HashMap<>();

            for (int p = 0; p < pathItems.size(); p++) {
                int[] path = pathItems.get(p);
                List<Integer> tids = pathTids.get(p);
                if (tids.isEmpty()) continue;

                List<Integer> filteredPath = new ArrayList<>();
                for (int ancItem : path) {
                    if (condItemSet.contains(ancItem)) {
                        filteredPath.add(ancItem);
                    }
                }
                if (filteredPath.isEmpty()) continue;

                filteredPath.sort(Comparator.comparingInt(condRank::get));
                insertTransactionWithTids(condRoot, condNodeLinks, filteredPath, tids);
            }

            int[] newPrefix = Arrays.copyOf(prefix, prefixLength + 1);
            newPrefix[prefixLength] = item;

            mineTree(condRoot, condNodeLinks, conditionalPfList, newPrefix, prefixLength + 1);

            MemoryLogger.getInstance().checkMemory();
        }
    }

    /**
     * Checks whether an item appears in the PF-list at a position before the given index.
     * @param pfList the PF-list to search
     * @param item the item to look for
     * @param beforeIndex the exclusive upper bound index
     * @return true if the item is found at an index strictly less than beforeIndex
     */
    private boolean isItemInPfListBefore(List<int[]> pfList, int item, int beforeIndex) {
        for (int i = 0; i < beforeIndex; i++) {
            if (pfList.get(i)[0] == item) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes the periodicity (maximum period) for a sorted list of transaction ids.
     * @param sortedTids the sorted list of transaction ids (must not be empty)
     * @return the maximum period across all intervals
     */
    int computePeriodicity(List<Integer> sortedTids) {
        if (sortedTids.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int maxPer = 0;
        int prev = -1;
        for (int tid : sortedTids) {
            int period = tid - prev;
            if (period > maxPer) {
                maxPer = period;
            }
            prev = tid;
        }
        int lastPeriod = (databaseSize - 1) - prev;
        if (lastPeriod > maxPer) {
            maxPer = lastPeriod;
        }
        return maxPer;
    }

    /**
     * Writes a periodic-frequent pattern to the output file.
     * @param prefix the prefix items of the pattern
     * @param prefixLength the number of valid items in the prefix
     * @param item the item appended to the prefix to form this pattern
     * @param support the support of the pattern
     * @param periodicity the maximum periodicity of the pattern
     * @throws IOException if an error occurs while writing
     */
    private void writeOut(int[] prefix, int prefixLength, int item,
                          int support, int periodicity) throws IOException {
        patternCount++;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prefixLength; i++) {
            sb.append(prefix[i]);
            sb.append(' ');
        }
        sb.append(item);
        sb.append(" #SUP: ");
        sb.append(support);
        sb.append(" #MAXPER: ");
        sb.append(periodicity);
        writer.write(sb.toString());
        writer.newLine();
    }

    /**
     * Prints statistics about the last execution to System.out.
     */
    public void printStats() {
        System.out.println("============= PFTREE ALGORITHM v2.66 =====");
        System.out.println(" Database size: " + databaseSize + " transactions");
        System.out.println(" Min support (absolute): " + minSupportAbsolute);
        System.out.println(" Max periodicity: " + maxPeriodicity);
        System.out.println(" Periodic-frequent patterns found: " + patternCount);
        System.out.println(" Time: " + totalExecutionTime + " ms");
        System.out.println(" Memory: " + maximumMemoryUsage + " MB");
        System.out.println("==========================================");
    }
}