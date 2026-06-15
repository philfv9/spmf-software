package ca.pfv.spmf.algorithms.frequentpatterns.HTK_Miner;

 /*
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * Copyright (c) [2026] [Konstantinos Malliaridis / Stefanos Ougiaroglou].
 * * This algorithm is an implementation of the HTK-Miner / HTK-negFIN algorithms.
 * If you use this code in your research, please cite the original manuscript:
 * [Malliaridis, K., & Ougiaroglou, S. (2026). Efficient techniques for retrieving top-K Frequent itemsets. Expert Systems with Applications, 131250.].
 * @Email konsmall@ihu.gr, stoug@ihu.gr 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove the copyright and license information.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import ca.pfv.spmf.tools.MemoryLogger;


/**
 * HTK-negFIN (A Top-K Frequent Itemset Miner based on negNodesets presented in negFIN algorithm Aryabarzan, N., Minaei-Bidgoli, B., & Teshnehlab, M. (2018). negFIN: An efficient algorithm for fast mining frequent itemsets. Expert Systems with Applications, 105, 129-143.) 
 * is an algorithm designed for the efficient discovery of the most frequent negative itemsets 
 * without requiring a user-defined minimum support threshold. 
 * This Java implementation is optimized for the SPMF library, 
 * translating high-level Python performance strategies into efficient JVM-compliant code.
 * 
 * Key Technical Features:
 * - Integrated Top-K Management: Q-Heap is implemented which is a structure for maintaining the top-K frequent itemsets implementing quick insertion logic.
 * - Memory Efficiency: The implementation prioritizes Java primitive arrays and pre-compiled regex patterns to minimize garbage collection (GC) overhead and maximize throughput on large-scale datasets.
 * - Original Support for K+N Ties. The implementation will return K itemsets plus any tied itemsets N at the K-th position.
 */ 
public class AlgoHTKnegFIN {

    private String datasetFile;
    private String outputFile;
    private String delimiter;
    
    private int topK; // User defined Top-K threshold
    private int minCount = 1; // The absolute minimum support initially set to 1 - the possible lowest value
    private int numOfTransactions = 0;
    private int numOfCandidateFI = 0;
    
    private long startTime; // the start time
    private long executionTime; // the overall execution time
	private double maxMemoryUsage;
    
    // Internal mappings for 1-itemsets
    private List<F1Item> F1;
    private Map<String, Integer> itemNameToItemIndex;
    
    // NodeSets mapping
    private List<BMCTreeNode>[] itemToNodeSet;
    
    // Final Results
    private List<Itemset> finalTopK;
    private QuickHeap Qheap;

    public AlgoHTKnegFIN() {
    }

    /**
     * Classic SPMF entry point signature.
     */
    public void runAlgorithm(String inputPath, String outputPath, int k, String delimiter) throws IOException {
        this.datasetFile = inputPath;
        this.outputFile = outputPath;
        this.topK = k;
        this.delimiter = delimiter;
		this.maxMemoryUsage = 0;
		MemoryLogger.getInstance().reset();
        
        this.Qheap = new QuickHeap(topK);
        this.finalTopK = new ArrayList<>();
        this.startTime = System.currentTimeMillis();

        // 1. Find Frequent 1-itemsets
        findF1();

        // 2. Build BMC Tree and NodeSets
        generateNodeSetsOf1Itemsets();

        // 3. Create Search Space Root
        FrequentItemsetTreeNode root = createRootOfFrequentItemsetTree();

        // Buffers for depth-first traversal
        int[] itemsetBuffer = new int[F1.size()];
        int itemsetLength = 0;
        
        int[] fisParentBuffer = new int[F1.size()];
        int fisParentLength = 0;
        
        MemoryLogger.getInstance().checkMemory();

        // 4. Traverse and Construct
        while (!root.children.isEmpty()) {
            FrequentItemsetTreeNode child = root.children.get(0);
            itemsetBuffer[itemsetLength] = child.item;
            root.children.remove(0); // Free memory early
            
            constructFrequentItemsetTree(child, itemsetBuffer, itemsetLength + 1, root.children, 
                                         fisParentBuffer, fisParentLength);
        }
        
        MemoryLogger.getInstance().checkMemory();

        // 5. Final TopK Filter
        this.finalTopK = getTopKFI(this.finalTopK);

        this.maxMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
        this.executionTime = System.currentTimeMillis() - startTime;
        
        writeFIM(this.outputFile);
    }

    private List<Itemset> getTopKFI(List<Itemset> itemsets) {
        // Sort descending by support, then by item logic (lexicographically for ties)
        Collections.sort(itemsets, (a, b) -> {
            if (a.support != b.support) return b.support - a.support;
            return Arrays.compare(a.items, b.items); 
        });

        List<Itemset> topKList = new ArrayList<>();
        int[] supList = new int[itemsets.size()];
        int mS = -1;
        
        for (int i = 0; i < itemsets.size(); i++) {
            Itemset current = itemsets.get(i);
            if (i >= this.topK - 1) {
                if (mS == -1) {
                    mS = current.support;
                } else if (current.support != mS) {
                    break; // End of Ties
                }
            }
            topKList.add(current);
            supList[i] = current.support;
        }

        this.minCount = (mS == -1) ? 1 : mS;
        
        // Quick heap synchronization
        int actualSize = topKList.size();
        int[] initialSupports = new int[actualSize];
        System.arraycopy(supList, 0, initialSupports, 0, actualSize);
        this.Qheap.initialFill(initialSupports);

        return topKList;
    }

    private void findF1() throws IOException {
        Map<String, Integer> itemNameToCount = new HashMap<>();
        Pattern sep = Pattern.compile(Pattern.quote(delimiter));
        numOfTransactions = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(datasetFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                numOfTransactions++;
                String[] items = sep.split(line);
                for (String itemStr : items) {
                    itemStr = itemStr.trim();
                    if (!itemStr.isEmpty()) {
                        itemNameToCount.put(itemStr, itemNameToCount.getOrDefault(itemStr, 0) + 1);
                    }
                }
            }
        }

        // Convert Map to Itemset List to reuse getTopKFI
        List<Itemset> f1Candidates = new ArrayList<>(itemNameToCount.size());
        for (Map.Entry<String, Integer> entry : itemNameToCount.entrySet()) {
            f1Candidates.add(new Itemset(new String[]{entry.getKey()}, entry.getValue()));
        }

        f1Candidates = getTopKFI(f1Candidates);

        // Populate F1
        this.F1 = new ArrayList<>(f1Candidates.size());
        for (Itemset is : f1Candidates) {
            this.F1.add(new F1Item(is.items[0], is.support));
        }

        // Sort F1 descending by count, then name (matching Python: lambda x: (-x['count'], x['name']))
        Collections.sort(this.F1, (a, b) -> {
            if (a.count != b.count) return b.count - a.count;
            return a.name.compareTo(b.name);
        });

        // Create fast name-to-index lookup mapping
        itemNameToItemIndex = new HashMap<>(F1.size());
        for (int i = 0; i < F1.size(); i++) {
            itemNameToItemIndex.put(F1.get(i).name, i);
        }
    }

    @SuppressWarnings("unchecked")
    private void generateNodeSetsOf1Itemsets() throws IOException {
        itemToNodeSet = new ArrayList[F1.size()];
        for (int i = 0; i < F1.size(); i++) {
            itemToNodeSet[i] = new ArrayList<>();
        }

        // Root of BMC Tree
        CustomBitSet rootBitmap = new CustomBitSet(F1.size());
        BMCTreeNode bmcTreeRoot = new BMCTreeNode(-1, 0, rootBitmap, F1.size());
        Pattern sep = Pattern.compile(Pattern.quote(delimiter));

        try (BufferedReader reader = new BufferedReader(new FileReader(datasetFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] itemStrings = sep.split(line);
                
                List<Integer> transaction = new ArrayList<>();
                for (String itemStr : itemStrings) {
                    Integer idx = itemNameToItemIndex.get(itemStr.trim());
                    if (idx != null) transaction.add(idx);
                }

                // Sort descending by index (Direct relation to count descending)
                transaction.sort(Collections.reverseOrder());

                BMCTreeNode curRoot = bmcTreeRoot;
                for (int item : transaction) {
                    BMCTreeNode n = curRoot.getChild(item);
                    if (n == null) {
                        CustomBitSet newBitmap = curRoot.bitmapCode.copy();
                        newBitmap.set(item);
                        n = new BMCTreeNode(item, 0, newBitmap, F1.size());
                        curRoot.addChild(n);
                        itemToNodeSet[item].add(n);
                    }
                    n.count++;
                    curRoot = n;
                }
            }
        }
        
        // Memory save mode: clean BMC tree
        cleanBMCTree(bmcTreeRoot);
    }

    private void cleanBMCTree(BMCTreeNode root) {
        if (root.children != null) {
            for (BMCTreeNode child : root.children) {
                if (child != null) cleanBMCTree(child);
            }
            root.children = null; // Equivalent to 'del root.children'
        }
    }

    private FrequentItemsetTreeNode createRootOfFrequentItemsetTree() {
        FrequentItemsetTreeNode root = new FrequentItemsetTreeNode();
        for (int item = 0; item < F1.size(); item++) {
            FrequentItemsetTreeNode child = new FrequentItemsetTreeNode();
            child.item = item;
            child.count = F1.get(item).count;
            child.negNodeSet = itemToNodeSet[item];
            root.children.add(child);
        }
        return root;
    }

    private void createItemsets(FrequentItemsetTreeNode n, int[] itemsetBuffer, int nItemsetLength, int[] fisParentBuffer, int fisParentLength) {
        numOfCandidateFI++;
        
        // Base Itemset
        String[] baseItems = new String[nItemsetLength];
        for (int i = 0; i < nItemsetLength; i++) {
            baseItems[i] = F1.get(itemsetBuffer[i]).name;
        }
        finalTopK.add(new Itemset(baseItems, n.count));

        // Subsets combinations using FIS_parent
        if (fisParentLength > 0) {
            int max = 1 << fisParentLength;
            for (int i = 1; i < max; i++) {
                List<String> combined = new ArrayList<>(Arrays.asList(baseItems));
                for (int j = 0; j < fisParentLength; j++) {
                    if ((i & (1 << j)) > 0 && i != j) {
                        combined.add(F1.get(fisParentBuffer[j]).name);
                    }
                }
                finalTopK.add(new Itemset(combined.toArray(new String[0]), n.count));
                numOfCandidateFI++;
            }
        }
    }

    private void constructFrequentItemsetTree(FrequentItemsetTreeNode n, int[] itemsetBuffer, int nItemsetLength, 
                                              List<FrequentItemsetTreeNode> nRightSiblings, int[] fisParentBuffer, int fisParentLength) {
        
        for (FrequentItemsetTreeNode sibling : nRightSiblings) {
            FrequentItemsetTreeNode child = new FrequentItemsetTreeNode();
            int sumOfNegNodeSetsCounts = 0;

            if (nItemsetLength == 1) {
                for (BMCTreeNode ni : n.negNodeSet) {
                    if (!ni.bitmapCode.get(sibling.item)) {
                        child.negNodeSet.add(ni);
                        sumOfNegNodeSetsCounts += ni.count;
                    }
                }
            } else {
                for (BMCTreeNode nj : sibling.negNodeSet) {
                    if (nj.bitmapCode.get(n.item)) {
                        child.negNodeSet.add(nj);
                        sumOfNegNodeSetsCounts += nj.count;
                    }
                }
            }

            child.count = n.count - sumOfNegNodeSetsCounts;

            if (this.minCount <= child.count) {
                this.minCount = this.Qheap.insert(child.count);

                if (n.count == child.count) {
                    fisParentBuffer[fisParentLength] = sibling.item;
                    fisParentLength++;
                } else {
                    child.item = sibling.item;
                    n.children.add(child);
                }
            }
        }

        if (this.minCount <= n.count) {
            createItemsets(n, itemsetBuffer, nItemsetLength, fisParentBuffer, fisParentLength);

            while (!n.children.isEmpty()) {
                FrequentItemsetTreeNode child = n.children.get(0);
                itemsetBuffer[nItemsetLength] = child.item;
                n.children.remove(0);
                constructFrequentItemsetTree(child, itemsetBuffer, nItemsetLength + 1, n.children, fisParentBuffer, fisParentLength);
            }
        }
    }

    private void writeFIM(String path) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (Itemset is : finalTopK) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < is.items.length; i++) {
                    sb.append(is.items[i]);
                    if (i < is.items.length - 1) sb.append(" ");
                }
                sb.append(" #SUP: ").append(is.support);
                writer.write(sb.toString());
                writer.newLine();
            }
        }
    }

    public void printStats() {
		System.out.println("=============  HTK_NEGFIN - 2.66 - STATS =============");
        System.out.println("Total time: " + (executionTime / 1000.0) + " Seconds");
		String memory = String.format("%.3f", maxMemoryUsage);
		System.out.println("Maximum memory usage: " + memory + " MB");
        System.out.println("  Candidates found: " + numOfCandidateFI);
        System.out.println("  Transaction count: " + numOfTransactions);
        System.out.println("  Item count " + F1.size());
        System.out.println("  Absolute minSup: " + minCount);
        System.out.printf("   Relative minSup: %.15f%n", (minCount / (double) numOfTransactions));
        System.out.println("Frequent Itemsets found: " + finalTopK.size());
		System.out.println("===================================================");
    }

    // ==========================================
    // Core Data Structures 
    // ==========================================

    /**
     * Requirement 3: QuickHeap Implementation
     */
    private static class QuickHeap {
        private final int K;
        private final int[] heapList;
        private int size = 0;

        public QuickHeap(int k) {
            this.K = k;
            this.heapList = new int[K];
        }

        public void initialFill(int[] initialList) {
            this.size = Math.min(initialList.length, K);
            System.arraycopy(initialList, 0, this.heapList, 0, this.size);
        }

        public int insert(int value) {
            if (size == K && value <= heapList[size - 1]) return heapList[size - 1];
            
            int pos = size;
            int low = 0, high = size - 1;
            
            // Binary search for descending order
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (heapList[mid] < value) {
                    pos = mid;
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            
            int numToMove = Math.min(size, K - 1) - pos;
            if (numToMove > 0) {
                System.arraycopy(heapList, pos, heapList, pos + 1, numToMove);
            }
            
            heapList[pos] = value;
            if (size < K) size++;
            
            return (size == K) ? heapList[K - 1] : 1; 
        }
    }

    private static class CustomBitSet {
        private final long[] data;

        public CustomBitSet(int numBits) {
            this.data = new long[(numBits + 63) / 64];
        }

        public void set(int index) {
            data[index >> 6] |= (1L << (index & 63));
        }

        public boolean get(int index) {
            return (data[index >> 6] & (1L << (index & 63))) != 0;
        }

        public CustomBitSet copy() {
            CustomBitSet copy = new CustomBitSet(this.data.length * 64);
            System.arraycopy(this.data, 0, copy.data, 0, this.data.length);
            return copy;
        }
    }

    private static class BMCTreeNode {
        int item;
        int count;
        CustomBitSet bitmapCode;
        BMCTreeNode[] children; // Dense array instead of HashMap for blistering speed

        BMCTreeNode(int item, int count, CustomBitSet bitmapCode, int maxItems) {
            this.item = item;
            this.count = count;
            this.bitmapCode = bitmapCode;
            this.children = new BMCTreeNode[maxItems]; 
        }

        BMCTreeNode getChild(int childItem) {
            return children[childItem];
        }

        void addChild(BMCTreeNode child) {
            children[child.item] = child;
        }
    }

    private static class FrequentItemsetTreeNode {
        int item;
        int count;
        List<FrequentItemsetTreeNode> children = new ArrayList<>();
        List<BMCTreeNode> negNodeSet = new ArrayList<>();
    }

    private static class Itemset {
        String[] items;
        int support;
        
        Itemset(String[] items, int support) {
            this.items = items;
            this.support = support;
        }
    }

    private static class F1Item {
        String name;
        int count;

        F1Item(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}