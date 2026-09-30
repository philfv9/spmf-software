package ca.pfv.spmf.algorithms.sequentialpatterns.tribackclo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.SequenceDatabase;
import ca.pfv.spmf.tools.MemoryLogger;
/* This file is copyright (c) 2026 Nabil Azizi et al.
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
* This code was provided by the authors under the MIT-LICENSE.
* 
* Do not remove copyright and license information.
*/
/**
 * TriBack-Clo: closed sequential pattern mining for itemset-sequences.
 *
 * Java implementation used for the benchmark campaigns in this project.
 * Uses SPMF's SequenceDatabase and MemoryLogger for comparable loading and
 * measurement while keeping the TriBack-Clo prune--gate--verify search logic.
 *
 * Includes a flat-array fast path for singleton-itemset datasets together with
 * the general itemset-sequence path used on multi-item benchmarks.
 * 
 * @author Nabil Azizi, Makhlouf Ledmi, Abdeldjalil Ledmi, Mohammed El Habib
 *         Souidi, Mohamed Boussalem Aboubekeur Hamdi-Cherif
 */
public class AlgoTriBackClo {

    // Statistics
    long startTime;
    long endTime;
    public int patternCount = 0;
    private long nodesVisited = 0;
    private long prunedSubtrees = 0;
    private long nodesGated = 0;

    // Ablation flags (set before calling runAlgorithm)
    public boolean enableSubtreePruning = true;
    public boolean enableNodeGating = true;
    public boolean enableEagerVerification = false;

    private long exactVerifierCalls = 0;
    private long eagerWastedVerifierCalls = 0;

    // Parameters
    private int minsupAbsolute;
    
    // Output
    BufferedWriter writer = null;
    
    // Database - stored as itemsets (Sequence -> Itemset -> Item)
    private int[][][] sequences;
    private int[][] singletonSeqs = null; // Flat array for singleton datasets: singletonSeqs[sid][pos] = item
    private int sequenceCount;
    private int maxItem;
    private boolean isSingleton = true; // Optimization flag
    private int[] itemSupport;  // Global support of each item
    
    // Pattern buffer (reused)
    private int[] patternBuffer = new int[2000];
    private int[] targetsBuf = new int[0];

    // Per-depth child buffers (reuse across DFS levels)
    private int[][] sChildItemsByDepth = new int[0][];
    private int[][] sChildSupportsByDepth = new int[0][];
    private int[][][] sChildPositionsByDepth = new int[0][][];
    private int[][] iChildItemsByDepth = new int[0][];
    private int[][] iChildSupportsByDepth = new int[0][];
    private int[][][] iChildPositionsByDepth = new int[0][][];
    
    // Context arrays for stamp-based intersection (reused across DFS)
    private int[] seenStamp;
    private int[] candidates;
    private int[] sExtSupport;
    private int[] sExtSeen;
    private int[] sTouched;
    private int stampCounter = 0;
    
    // For building child stores
    private int[][] sLists;  // sLists[item] = positions for S-extension by item
    private int[] sListSizes;
    private int[][] iLists;  // iLists[item] = positions for I-extension by item
    private int[] iListSizes;
    
    // I-extension context
    private int[] iExtSupport;
    private int[] iExtSeen;
    private int[] iTouched;
    
    // Reusable envelope buffers and SID scratch space
    private int[] envFirstBuf = new int[0];
    private int[] envLastBuf = new int[0];
    
    // Closure checker for envelope-based verification
    private ClosureCheckerFast closureChecker;
    private int[] sidBuf; // Buffer for SIDs
    private int[] itemsetOffsets = new int[128]; // [start0, end0, start1, end1, ...] for zero-alloc closure check
    private final int[] prevItemsetRange = new int[2];

    
    private void ensureEnvCapacity(int n) {
        if (envFirstBuf.length < n) {
            envFirstBuf = new int[n * 2];
            envLastBuf = new int[n * 2];
        }
    }

    private void ensureChildCacheCapacity(int depth, int sCount, int iCount) {
        if (sChildItemsByDepth.length <= depth) {
            int newLen = Math.max(depth + 1, sChildItemsByDepth.length * 2 + 1);
            sChildItemsByDepth = Arrays.copyOf(sChildItemsByDepth, newLen);
            sChildSupportsByDepth = Arrays.copyOf(sChildSupportsByDepth, newLen);
            sChildPositionsByDepth = Arrays.copyOf(sChildPositionsByDepth, newLen);
            iChildItemsByDepth = Arrays.copyOf(iChildItemsByDepth, newLen);
            iChildSupportsByDepth = Arrays.copyOf(iChildSupportsByDepth, newLen);
            iChildPositionsByDepth = Arrays.copyOf(iChildPositionsByDepth, newLen);
        }

        if (sChildItemsByDepth[depth] == null || sChildItemsByDepth[depth].length < sCount) {
            sChildItemsByDepth[depth] = new int[sCount];
        }
        if (sChildSupportsByDepth[depth] == null || sChildSupportsByDepth[depth].length < sCount) {
            sChildSupportsByDepth[depth] = new int[sCount];
        }
        if (sChildPositionsByDepth[depth] == null || sChildPositionsByDepth[depth].length < sCount) {
            sChildPositionsByDepth[depth] = new int[sCount][];
        }

        if (iChildItemsByDepth[depth] == null || iChildItemsByDepth[depth].length < iCount) {
            iChildItemsByDepth[depth] = new int[iCount];
        }
        if (iChildSupportsByDepth[depth] == null || iChildSupportsByDepth[depth].length < iCount) {
            iChildSupportsByDepth[depth] = new int[iCount];
        }
        if (iChildPositionsByDepth[depth] == null || iChildPositionsByDepth[depth].length < iCount) {
            iChildPositionsByDepth[depth] = new int[iCount][];
        }
    }
    
    /**
     * Run the algorithm
     */
    public int runAlgorithm(String inputFile, String outputFile, int minsup) throws IOException {
        patternCount = 0;
        nodesVisited = 0;
        prunedSubtrees = 0;
        nodesGated = 0;
        exactVerifierCalls = 0;
        eagerWastedVerifierCalls = 0;
        MemoryLogger.getInstance().reset();
        
        // Start time (before loading, like SPMF)
        startTime = System.currentTimeMillis();
        
        // Load database
        SequenceDatabase database = new SequenceDatabase();
        database.loadFile(inputFile);
        List<int[]> rawSeqs = database.getSequences();
        sequenceCount = rawSeqs.size();
        
        // Convert to itemset format (split by -1)
        sequences = new int[sequenceCount][][];
        maxItem = 0;
        for (int i = 0; i < sequenceCount; i++) {
            int[] raw = rawSeqs.get(i);
            if (raw == null) {
                sequences[i] = new int[0][];
                continue;
            }
            
            // Pass 1: Count itemsets
            int itemsetCount = 0;
            for (int token : raw) {
                if (token == -1) itemsetCount++;
                else if (token == -2) break; // End of sequence
            }
            
            sequences[i] = new int[itemsetCount][];
            
            // For singleton detection - check if any itemset has more than 1 item
            boolean seqIsSingleton = true;
            
            // Pass 2: Populate itemsets
            int currentItemsetIdx = 0;
            int currentItemsetStart = 0;
            int length = 0;
            
            for (int j = 0; j < raw.length; j++) {
                int token = raw[j];
                if (token == -1) {
                    if (length > 1) seqIsSingleton = false;
                    // Create itemset
                    sequences[i][currentItemsetIdx] = new int[length];
                    for (int k = 0; k < length; k++) {
                        int item = raw[currentItemsetStart + k];
                        sequences[i][currentItemsetIdx][k] = item;
                        if (item > maxItem) maxItem = item;
                    }
                    Arrays.sort(sequences[i][currentItemsetIdx]);
                    currentItemsetIdx++;
                    currentItemsetStart = j + 1;
                    length = 0;
                } else if (token == -2) {
                    break;
                } else {
                    length++;
                }
            }
            
            if (!seqIsSingleton) isSingleton = false;
        }
        
        // Build singletonSeqs for singleton datasets (flat int[] per sequence)
        if (isSingleton) {
            singletonSeqs = new int[sequenceCount][];
            for (int s = 0; s < sequenceCount; s++) {
                int[][] seq = sequences[s];
                singletonSeqs[s] = new int[seq.length];
                for (int p = 0; p < seq.length; p++) {
                    singletonSeqs[s][p] = seq[p][0];
                }
            }
        }
        
        this.minsupAbsolute = Math.max(minsup, 1);
        
        // Setup output
        if (outputFile != null && !outputFile.equals("null") && !outputFile.equals("/dev/null")) {
            writer = new BufferedWriter(new FileWriter(outputFile));
    		writer.write("@FILETYPE=\"Frequent closed sequential patterns\"");
    		writer.append(System.lineSeparator());
    		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
    		writer.append(System.lineSeparator());
        }
        
        // Allocate context arrays
        int arraySize = maxItem + 1;
        seenStamp = new int[arraySize];
        candidates = new int[arraySize];
        sExtSupport = new int[arraySize];
        sExtSeen = new int[arraySize];
        sTouched = new int[arraySize];
        sLists = new int[arraySize][];
        sListSizes = new int[arraySize];
        iLists = new int[arraySize][];
        iListSizes = new int[arraySize];
        
        iExtSupport = new int[arraySize];
        iExtSeen = new int[arraySize];
        iTouched = new int[arraySize];
        
        // Compute global item support
        itemSupport = computeItemSupport();
        
        // Initialize closure checker
        closureChecker = new ClosureCheckerFast(sequences, singletonSeqs, isSingleton, maxItem, itemSupport, minsupAbsolute);
        sidBuf = new int[sequenceCount];  // Buffer for extracting SIDs
        
        // Run mining
        mine();
        
        endTime = System.currentTimeMillis();
        MemoryLogger.getInstance().checkMemory();
        
        if (writer != null) writer.close();
        return patternCount;
    }
    
    /**
     * Compute support of each item (number of sequences containing it)
     */
    private int[] computeItemSupport() {
        int[] support = new int[maxItem + 1];
        int[] seen = new int[maxItem + 1];
        
        for (int sid = 0; sid < sequenceCount; sid++) {
            int[][] seq = sequences[sid];
            int stamp = sid + 1;
            for (int[] itemset : seq) {
                for (int item : itemset) {
                    if (seen[item] != stamp) {
                        seen[item] = stamp;
                        support[item]++;
                    }
                }
            }
        }
        return support;
    }
    
    /**
     * Main mining method
     */
    private void mine() throws IOException {
        MemoryLogger.getInstance().checkMemory();
        
        // Find frequent items and their initial positions
        // Initial store: for each SID, position is (sid, startIdx=0, currentIdx=-1, lastItem=-1)
        // startIdx: index of first itemset (S-step)
        // currentIdx: index of last completed itemset
        int[] rootPositions = new int[sequenceCount * 4];
        for (int sid = 0; sid < sequenceCount; sid++) {
            rootPositions[sid * 4] = sid;
            rootPositions[sid * 4 + 1] = 0;      // startIdx of search
            rootPositions[sid * 4 + 2] = -1;     // currentIdx (last filled itemset index)
            rootPositions[sid * 4 + 3] = -1;     // lastItem (for I-extensions)
        }
        
        // DFS from root
        dfs(0, rootPositions, sequenceCount, null);
        
        MemoryLogger.getInstance().checkMemory();
    }
    
    /**
     * DFS mining with pointer-based projection
     */
    private void dfs(int bufferLen, int[] positions, int numSids, int[] tailItemset) throws IOException {
        nodesVisited++;
        
        if ((nodesVisited & 0x3FF) == 0) {
            MemoryLogger.getInstance().checkMemory();
        }
        
        if (numSids < minsupAbsolute) return;
        
        // 1. BackScan subtree pruning (Temporal Witness)
        if (bufferLen > 0 && hasTemporalWitness(positions, numSids, bufferLen)) {
            prunedSubtrees++;
            if (enableSubtreePruning) return;
        }
        
        // 2. Enumerate Extensions (S and I)
        int[] counts = enumerateExtensions(positions, numSids, tailItemset);
        int sTouchedCount = counts[0];
        int iTouchedCount = counts[1];
        int hasSameSupp = counts[2];
        
        // 3. Current-node gating + exact closure check
        // Default mode is lazy: exact verification is invoked only after node gating.
        // Eager-verification ablation: exact verification is invoked before node gating
        // for every forward-closed, non-pruned node.
        if (bufferLen > 0 && hasSameSupp == 0) {
            boolean exactClosed = false;
            boolean exactAlreadyComputed = false;

            if (enableEagerVerification) {
                exactClosed = isClosedExactCurrentNode(positions, numSids, bufferLen);
                exactAlreadyComputed = true;
                exactVerifierCalls++;
            }

            boolean gated = enableNodeGating && detectNotClosedFast(positions, numSids, bufferLen, tailItemset);

            if (!gated) {
                if (!exactAlreadyComputed) {
                    exactClosed = isClosedExactCurrentNode(positions, numSids, bufferLen);
                    exactVerifierCalls++;
                }

                if (exactClosed) {
                    savePattern(bufferLen, numSids);
                }
            } else {
                nodesGated++;
                
                if (enableEagerVerification && exactClosed) {
                    throw new IllegalStateException(
                        "Eager verifier says closed, but node gate says non-closed at pattern length " + bufferLen
                    );
                }

                if (enableEagerVerification) {
                    eagerWastedVerifierCalls++;
                }
            }
        }
        
        // 4. Recurse (I-extensions first, then S-extensions - typical order)
        // Note: Recursion logic
        
        // 4. Materialize all frequent children BEFORE recursing
        // (Recursive DFS will clear the enumeration state, so we must save it first)
        
        // I-children
        int iChildCount = 0;
        for (int t = 0; t < iTouchedCount; t++) {
            int item = iTouched[t];
            if (iListSizes[item] / 4 >= minsupAbsolute) {
                iChildCount++;
            }
        }

        // S-children
        int sChildCount = 0;
        for (int t = 0; t < sTouchedCount; t++) {
            int item = sTouched[t];
            if (sListSizes[item] / 4 >= minsupAbsolute) {
                sChildCount++;
            }
        }
        ensureChildCacheCapacity(bufferLen, sChildCount, iChildCount);

        int[] iChildItems = iChildItemsByDepth[bufferLen];
        int[][] iChildPositions = iChildPositionsByDepth[bufferLen];
        int[] iChildSupports = iChildSupportsByDepth[bufferLen];
        int ci = 0;
        for (int t = 0; t < iTouchedCount; t++) {
            int item = iTouched[t];
            int listSize = iListSizes[item];
            int support = listSize / 4;
            if (support >= minsupAbsolute) {
                iChildItems[ci] = item;
                iChildSupports[ci] = support;
                int[] childPos = iChildPositions[ci];
                if (childPos == null || childPos.length < listSize) {
                    childPos = new int[listSize];
                    iChildPositions[ci] = childPos;
                }
                System.arraycopy(iLists[item], 0, childPos, 0, listSize);
                iChildPositions[ci] = childPos;
                ci++;
            }
        }

        int[] sChildItems = sChildItemsByDepth[bufferLen];
        int[][] sChildPositions = sChildPositionsByDepth[bufferLen];
        int[] sChildSupports = sChildSupportsByDepth[bufferLen];
        int cs = 0;
        for (int t = 0; t < sTouchedCount; t++) {
            int item = sTouched[t];
            int listSize = sListSizes[item];
            int support = listSize / 4;
            if (support >= minsupAbsolute) {
                sChildItems[cs] = item;
                sChildSupports[cs] = support;
                int[] childPos = sChildPositions[cs];
                if (childPos == null || childPos.length < listSize) {
                    childPos = new int[listSize];
                    sChildPositions[cs] = childPos;
                }
                System.arraycopy(sLists[item], 0, childPos, 0, listSize);
                sChildPositions[cs] = childPos;
                cs++;
            }
        }
        
        // Recurse I-extensions
        for (int i = 0; i < iChildCount; i++) {
             int item = iChildItems[i];
             // Update pattern buffer: Append item
             patternBuffer[bufferLen] = item;
             
             // Update tail: tail + item
             int[] newTail;
             if (tailItemset == null) {
                 newTail = new int[]{item};
             } else {
                 newTail = new int[tailItemset.length + 1];
                 System.arraycopy(tailItemset, 0, newTail, 0, tailItemset.length);
                 newTail[tailItemset.length] = item;
             }
             
             dfs(bufferLen + 1, iChildPositions[i], iChildSupports[i], newTail);
        }
        
        // Recurse S-extensions
        for (int i = 0; i < sChildCount; i++) {
             int item = sChildItems[i];
             // Update pattern buffer
             int newLen = bufferLen;
             if (bufferLen > 0) {
                 patternBuffer[newLen++] = -1; // Separator
             }
             patternBuffer[newLen++] = item;
             
             // New tail is just [item]
             int[] newTail = new int[]{item};
             
             dfs(newLen, sChildPositions[i], sChildSupports[i], newTail);
        }
    }
    
    // Track items found in current enumeration
    private int[] currentEnumTouched = new int[0];
    private int currentEnumTouchedCount = 0;
    
    /**
     * Enumerate both S and I extensions (General Path)
     * Returns: [sTouchedCount, iTouchedCount, hasSameSuppForward (0 or 1)]
     * Fills sTouched/iTouched and sLists/iLists
     * 
     * @param positions Current positions
     * @param numSids Support of current node
     * @param tailItemset The last itemset of the current pattern (to check subset ordering), can be null
     */
    private int[] enumerateExtensions(int[] positions, int numSids, int[] tailItemset) {
        int sTouchedCount = 0;
        int iTouchedCount = 0;
        
        // Clear previous state (Critical for preventing support accumulation overflow)
        for (int i = 0; i < currentEnumTouchedCount; i++) {
            int item = currentEnumTouched[i];
            sExtSupport[item] = 0;
            sListSizes[item] = 0;
            iExtSupport[item] = 0;
            iListSizes[item] = 0;
        }
        currentEnumTouchedCount = 0;
        
        // Ensure enough space for touched items
        if (currentEnumTouched.length < maxItem + 1) {
            currentEnumTouched = new int[maxItem + 1];
        }
        
        // ===== FAST PATH: Singleton Datasets (Two-Pass Enumeration) =====
        if (isSingleton) {
            // PASS 1: Count support only (no list materialization)
            stampCounter += sequenceCount + 1;
            int stampBase1 = stampCounter;
            
            for (int i = 0; i < numSids; i++) {
                int idx = i * 4;
                int sid = positions[idx];
                int currentIdx = positions[idx + 2];
                int[] seq = singletonSeqs[sid];
                int stamp = stampBase1 + sid;
                
                // S-Extensions only (no I-extensions for singletons)
                for (int k = currentIdx + 1; k < seq.length; k++) {
                    int item = seq[k];
                    if (sExtSeen[item] != stamp) {
                        sExtSeen[item] = stamp;
                        if (sExtSupport[item] == 0) {
                            sTouched[sTouchedCount++] = item;
                            currentEnumTouched[currentEnumTouchedCount++] = item;
                        }
                        sExtSupport[item]++;
                    }
                }
            }
            
            // PASS 2: Materialize only frequent items
            stampCounter += sequenceCount + 1;
            int stampBase2 = stampCounter;
            
            for (int i = 0; i < numSids; i++) {
                int idx = i * 4;
                int sid = positions[idx];
                int currentIdx = positions[idx + 2];
                int[] seq = singletonSeqs[sid];
                int stamp = stampBase2 + sid;
                
                for (int k = currentIdx + 1; k < seq.length; k++) {
                    int item = seq[k];
                    // OPTIMIZATION: Skip infrequent items entirely
                    if (sExtSupport[item] >= minsupAbsolute) {
                        if (sExtSeen[item] != stamp) {
                            sExtSeen[item] = stamp;
                            
                            // Initialize list if needed
                            if (sLists[item] == null) {
                                sLists[item] = new int[Math.min(256, numSids * 4)];
                            }
                            if (sListSizes[item] == 0) {
                                // Reset for this enumeration
                            }
                            
                            int[] list = sLists[item];
                            int sz = sListSizes[item];
                            // Grow if needed
                            if (sz + 4 > list.length) {
                                int[] newList = new int[list.length * 2];
                                System.arraycopy(list, 0, newList, 0, sz);
                                sLists[item] = newList;
                                list = newList;
                            }
                            list[sz] = sid;
                            // Fix: startIdx for child should be currentIdx + 1 (gap starts after parent match)
                            // If root (currentIdx == -1), startIdx becomes 0.
                            list[sz + 1] = currentIdx + 1;
                            list[sz + 2] = k;
                            list[sz + 3] = item;
                            sListSizes[item] = sz + 4;
                        }
                    }
                }
            }
        } else {
            // ===== GENERAL PATH: Multi-item Itemsets (Two-Pass Optimization) =====
            
            // --- Pass 1: Counting ---
            stampCounter += sequenceCount + 1;
            int stampBase = stampCounter;
            
            for (int i = 0; i < numSids; i++) {
                int idx = i * 4;
                int sid = positions[idx];
                int currentIdx = positions[idx + 2];
                int lastItem = positions[idx + 3];
                
                int[][] seq = sequences[sid];
                int stamp = stampBase + sid;
                
                // I-extensions: existential tail scan from currentIdx onward
                if (tailItemset != null && currentIdx >= 0 && currentIdx < seq.length) {
                    boolean requireFullTailSubsetCheck = tailItemset.length > 1;
                    
                    for (int pos = currentIdx; pos < seq.length; pos++) {
                        int[] ev = seq[pos];
                        if (ev.length > 0) {
                            int evLast = ev[ev.length - 1];
                            // Quick check: ev's max must be > lastItem
                            // And ev must be large enough to contain tailItemset
                            if (evLast > lastItem && (!requireFullTailSubsetCheck || ev.length >= tailItemset.length)) {
                                // Find lastItem in ev via binary search
                                int idxLast = Arrays.binarySearch(ev, lastItem);
                                if (idxLast >= 0 && (!requireFullTailSubsetCheck || isSubset(tailItemset, ev))) {
                                    // Valid event: count candidates after lastItem
                                    int j = idxLast + 1;
                                    // Skip duplicates of lastItem
                                    while (j < ev.length && ev[j] == lastItem) j++;
                                    // Count all items after lastItem
                                    while (j < ev.length) {
                                        int item = ev[j];
                                        if (iExtSeen[item] != stamp) {
                                            iExtSeen[item] = stamp;
                                            if (iExtSupport[item] == 0) {
                                                iTouched[iTouchedCount++] = item;
                                                
                                                // Link to global cleanup
                                                if (sExtSupport[item] == 0) {
                                                    currentEnumTouched[currentEnumTouchedCount++] = item;
                                                }
                                                
                                                // Initialize list (lazy)
                                                int initialCap = Math.min(256, numSids * 4);
                                                if (iLists[item] == null) {
                                                    iLists[item] = new int[initialCap];
                                                }
                                                iListSizes[item] = 0;
                                            }
                                            iExtSupport[item]++;
                                        }
                                        j++;
                                    }
                                }
                            }
                        }
                    }
                }
                
                // S-Extensions
                for (int k = currentIdx + 1; k < seq.length; k++) {
                    int[] itemset = seq[k];
                    for (int item : itemset) {
                        if (sExtSeen[item] != stamp) {
                            sExtSeen[item] = stamp;
                            if (sExtSupport[item] == 0) {
                                sTouched[sTouchedCount++] = item;
                                
                                // Link to global cleanup
                                if (iExtSupport[item] == 0) {
                                    currentEnumTouched[currentEnumTouchedCount++] = item;
                                }
                                
                                int initialCap = Math.min(256, numSids * 4);
                                if (sLists[item] == null) {
                                    sLists[item] = new int[initialCap];
                                }
                                sListSizes[item] = 0;
                            }
                            sExtSupport[item]++;
                        }
                    }
                }
            }
            
            // --- Pass 2: Materialization (Filtered) ---
            stampCounter += sequenceCount + 1;
            int stampBase2 = stampCounter;
            
            for (int i = 0; i < numSids; i++) {
                int idx = i * 4;
                int sid = positions[idx];
                int currentIdx = positions[idx + 2];
                int lastItem = positions[idx + 3]; // Recapture for consistency
                
                int[][] seq = sequences[sid];
                int stamp = stampBase2 + sid;
                
                // I-Extensions: Scan forward from currentIdx (matching Pass 1 logic)
                if (tailItemset != null && currentIdx >= 0 && currentIdx < seq.length) {
                    boolean requireFullTailSubsetCheck = tailItemset.length > 1;
                    
                    for (int pos = currentIdx; pos < seq.length; pos++) {
                        int[] ev = seq[pos];
                        if (ev.length > 0) {
                            int evLast = ev[ev.length - 1];
                            if (evLast > lastItem && (!requireFullTailSubsetCheck || ev.length >= tailItemset.length)) {
                                int idxLast = Arrays.binarySearch(ev, lastItem);
                                if (idxLast >= 0 && (!requireFullTailSubsetCheck || isSubset(tailItemset, ev))) {
                                    // Candidates after lastItem
                                    int j = idxLast + 1;
                                    while (j < ev.length && ev[j] == lastItem) j++;
                                    while (j < ev.length) {
                                        int item = ev[j];
                                        // Filter by minsup
                                        if (iExtSupport[item] >= minsupAbsolute) {
                                            if (iExtSeen[item] != stamp) {
                                                iExtSeen[item] = stamp;
                                                
                                                // Add to list: (sid, startIdx, pos, item)
                                                // Note: pos is the actual itemset position found, not currentIdx
                                                int[] list = iLists[item];
                                                int sz = iListSizes[item];
                                                if (sz + 4 > list.length) {
                                                    int[] newList = new int[list.length * 2];
                                                    System.arraycopy(list, 0, newList, 0, sz);
                                                    iLists[item] = newList;
                                                    list = newList;
                                                }
                                                list[sz] = sid;
                                                list[sz + 1] = positions[idx + 1]; // startIdx
                                                list[sz + 2] = pos;                // actual position where found
                                                list[sz + 3] = item;
                                                iListSizes[item] = sz + 4;
                                            }
                                        }
                                        j++;
                                    }
                                }
                            }
                        }
                    }
                }
                
                // S-Extensions
                for (int k = currentIdx + 1; k < seq.length; k++) {
                    int[] itemset = seq[k];
                    for (int item : itemset) {
                         // Filter by minsup
                        if (sExtSupport[item] >= minsupAbsolute) {
                            if (sExtSeen[item] != stamp) {
                                sExtSeen[item] = stamp;
                                
                                // Add to list
                                int[] list = sLists[item];
                                int sz = sListSizes[item];
                                if (sz + 4 > list.length) {
                                    int[] newList = new int[list.length * 2];
                                    System.arraycopy(list, 0, newList, 0, sz);
                                    sLists[item] = newList;
                                    list = newList;
                                }
                                list[sz] = sid;
                                // Fix: startIdx for child should be currentIdx + 1 (gap starts after parent match)
                                // If root (currentIdx == -1), startIdx becomes 0.
                                list[sz + 1] = currentIdx + 1;
                                list[sz + 2] = k;
                                list[sz + 3] = item;
                                sListSizes[item] = sz + 4;
                            }
                        }
                    }
                }
            }
        }
        
        // Check for forward closure (same support)
        int hasSameSupp = 0;
        for(int k=0; k<sTouchedCount; k++) {
            if (sExtSupport[sTouched[k]] == numSids) { hasSameSupp = 1; break; }
        }
        if (hasSameSupp == 0) {
            for(int k=0; k<iTouchedCount; k++) {
                if (iExtSupport[iTouched[k]] == numSids) { hasSameSupp = 1; break; }
            }
        }
        
        return new int[]{sTouchedCount, iTouchedCount, hasSameSupp};
    }

    
    /**
     * Reset stamp arrays when the global counter approaches overflow.
     */
    private void resetStamps() {
        Arrays.fill(seenStamp, 0);
        if (sExtSeen != null) Arrays.fill(sExtSeen, 0);
        if (iExtSeen != null) Arrays.fill(iExtSeen, 0);
        stampCounter = 0;
    }

    private boolean hasTemporalWitness(int[] positions, int numSids, int bufferLen) {
        if (numSids == 0) return false;
        
        // Overflow check
        if (stampCounter > 2000000000) {
            resetStamps();
        }
        
        // Intersect items in the temporal gap [startIdx, currentIdx) for all SIDs.
        // This is the representative window before the current tail match.
        
        int baseStamp = stampCounter;
        stampCounter += numSids; // Reserve validation stamps
        int candidateCount = 0;
        
        // Process first SID: build candidate set
        {
            int startIdx = positions[1];  // parent's currentIdx (or 0)
            int currentIdx = positions[2]; // current match position
            int stamp1 = baseStamp + 1;
            
            if (isSingleton) {
                int sid = positions[0];
                int[] seq = singletonSeqs[sid];
                
                for (int isIdx = startIdx; isIdx < currentIdx && isIdx >= 0 && isIdx < seq.length; isIdx++) {
                    int item = seq[isIdx];
                    if (seenStamp[item] != stamp1) {
                        // Only consider items with enough global support
                        if (itemSupport[item] >= numSids) {
                            seenStamp[item] = stamp1;
                            candidates[candidateCount++] = item;
                        }
                    }
                }
            } else {
                int sid = positions[0];
                int[][] seq = sequences[sid];
                
                for (int isIdx = startIdx; isIdx < currentIdx && isIdx >= 0 && isIdx < seq.length; isIdx++) {
                    int[] itemset = seq[isIdx];
                    for (int t = 0; t < itemset.length; t++) {
                        int item = itemset[t];
                        if (seenStamp[item] != stamp1) {
                            if (itemSupport[item] >= numSids) {
                                seenStamp[item] = stamp1;
                                candidates[candidateCount++] = item;
                            }
                        }
                    }
                }
            }
        }
        
        if (candidateCount == 0) return false;
        
        // Intersect with remaining SIDs
        for (int idx = 1; idx < numSids; idx++) {
            int posIdx = idx * 4;
            int startIdx = positions[posIdx + 1];
            int currentIdx = positions[posIdx + 2];
            
            int curStamp = baseStamp + idx + 1;
            int prevStamp = curStamp - 1;
            
            if (isSingleton) {
                int sid = positions[posIdx];
                int[] seq = singletonSeqs[sid];
                
                // Intersect
                for (int isIdx = startIdx; isIdx < currentIdx && isIdx >= 0 && isIdx < seq.length; isIdx++) {
                    int item = seq[isIdx];
                    if (seenStamp[item] == prevStamp) {
                        seenStamp[item] = curStamp;
                    }
                }
            } else {
                int sid = positions[posIdx];
                int[][] seq = sequences[sid];
                
                for (int isIdx = startIdx; isIdx < currentIdx && isIdx >= 0 && isIdx < seq.length; isIdx++) {
                    int[] itemset = seq[isIdx];
                    for (int t = 0; t < itemset.length; t++) {
                        int item = itemset[t];
                        if (seenStamp[item] == prevStamp) {
                            seenStamp[item] = curStamp;
                        }
                    }
                }
            }
            
            // Compact candidates
            int w = 0;
            for (int r = 0; r < candidateCount; r++) {
                int item = candidates[r];
                if (seenStamp[item] == curStamp) {
                    candidates[w++] = item;
                }
            }
            candidateCount = w;
            
            // Optimization: fail fast if candidates empty
            if (candidateCount == 0) return false;
        }
        
        return true;
    }

    /**
     * Sound NODE GATING after Stage 1 pruning: local and internal witnesses.
     * Used to skip closure verification for patterns known to be non-closed.
     */
    private boolean detectNotClosedFast(int[] positions, int numSids, int bufferLen, int[] tailItemset) {
        if (numSids == 0) return false;

        // Singleton datasets have no additional node-gating witnesses once
        // temporal subtree pruning has already been handled by Stage 1.
        if (isSingleton) {
            return false;
        }

        // 1) Local-gap witness (tail residuals)
        if (tailItemset != null && tailItemset.length > 0) {
            int maxTailItem = tailItemset[tailItemset.length - 1];
            if (hasLocalWitness(positions, numSids, tailItemset, maxTailItem)) return true;

            // 2) Internal backward-I witness (previous itemset residuals)
            if (getPrevItemsetRange(bufferLen, prevItemsetRange)) {
                if (hasLocalWitnessInternal(positions, numSids, prevItemsetRange[0], prevItemsetRange[1])) return true;
            }
        }

        return false;
    }

    private boolean getPrevItemsetRange(int bufferLen, int[] outRange) {
        int i = bufferLen - 1;
        while (i >= 0 && patternBuffer[i] != -1) i--;
        if (i < 0) return false; // Only one itemset
        int lastSep = i;
        i = lastSep - 1;
        while (i >= 0 && patternBuffer[i] != -1) i--;
        int start = i + 1;
        int end = lastSep;
        if (start >= end) return false;
        outRange[0] = start;
        outRange[1] = end;
        return true;
    }

    private boolean hasLocalWitness(int[] positions, int numSids, int[] tailItemset, int maxTailItem) {
        if (numSids == 0) return false;

        if (stampCounter > 2000000000) {
            resetStamps();
        }

        int baseStamp = stampCounter;
        stampCounter += numSids;
        int candidateCount = 0;

        // First SID
        {
            int currentIdx = positions[2];
            int[][] seq = sequences[positions[0]];
            int stamp1 = baseStamp + 1;

            if (currentIdx >= 0 && currentIdx < seq.length) {
                int[] currentSet = seq[currentIdx];
                for (int item : currentSet) {
                    if (item < maxTailItem && seenStamp[item] != stamp1) {
                        if (tailItemset == null || Arrays.binarySearch(tailItemset, item) < 0) {
                            if (itemSupport[item] >= numSids) {
                                seenStamp[item] = stamp1;
                                candidates[candidateCount++] = item;
                            }
                        }
                    }
                }
            }
        }

        if (candidateCount == 0) return false;

        // Intersect with remaining SIDs
        int sidCount = 1;
        for (int idx = 4; idx < numSids * 4; idx += 4) {
            int currentIdx = positions[idx + 2];
            int[][] seq = sequences[positions[idx]];

            int curStamp = baseStamp + sidCount + 1;
            int prevStamp = curStamp - 1;

            if (currentIdx >= 0 && currentIdx < seq.length) {
                int[] currentSet = seq[currentIdx];
                for (int item : currentSet) {
                    if (item < maxTailItem && seenStamp[item] == prevStamp) {
                        seenStamp[item] = curStamp;
                    }
                }
            }

            int w = 0;
            for (int r = 0; r < candidateCount; r++) {
                int item = candidates[r];
                if (seenStamp[item] == curStamp) {
                    candidates[w++] = item;
                }
            }
            candidateCount = w;

            if (candidateCount == 0) return false;
            sidCount++;
        }

        return true;
    }

    private boolean hasLocalWitnessInternal(int[] positions, int numSids, int prevStart, int prevEnd) {
        if (numSids == 0) return false;
        if (prevStart >= prevEnd) return false;

        if (stampCounter > 2000000000) {
            resetStamps();
        }

        int baseStamp = stampCounter;
        stampCounter += numSids;
        int candidateCount = 0;

        // First SID
        {
            int startIdx = positions[1];
            int[][] seq = sequences[positions[0]];
            int stamp1 = baseStamp + 1;

            int prevMatchIdx = startIdx - 1;
            if (prevMatchIdx >= 0 && prevMatchIdx < seq.length) {
                int[] currentSet = seq[prevMatchIdx];
                for (int item : currentSet) {
                    if (Arrays.binarySearch(patternBuffer, prevStart, prevEnd, item) < 0 && seenStamp[item] != stamp1) {
                        if (itemSupport[item] >= numSids) {
                            seenStamp[item] = stamp1;
                            candidates[candidateCount++] = item;
                        }
                    }
                }
            }
        }

        if (candidateCount == 0) return false;

        // Intersect with remaining SIDs
        int sidCount = 1;
        for (int idx = 4; idx < numSids * 4; idx += 4) {
            int startIdx = positions[idx + 1];
            int[][] seq = sequences[positions[idx]];

            int curStamp = baseStamp + sidCount + 1;
            int prevStamp = curStamp - 1;

            int prevMatchIdx = startIdx - 1;
            if (prevMatchIdx >= 0 && prevMatchIdx < seq.length) {
                int[] currentSet = seq[prevMatchIdx];
                for (int item : currentSet) {
                    if (Arrays.binarySearch(patternBuffer, prevStart, prevEnd, item) < 0 && seenStamp[item] == prevStamp) {
                        seenStamp[item] = curStamp;
                    }
                }
            }

            int w = 0;
            for (int r = 0; r < candidateCount; r++) {
                int item = candidates[r];
                if (seenStamp[item] == curStamp) {
                    candidates[w++] = item;
                }
            }
            candidateCount = w;

            if (candidateCount == 0) return false;
            sidCount++;
        }

        return true;
    }
    
    /**
     * Fast path for backward S-prepend on length-1 singleton patterns.
     * Checks whether any item appears before the rightmost feasible match of
     * targetItem in all supporting sequences.
     */
    private boolean hasCommonItemBeforeLastOccurrence(int[] positions, int numSids, int targetItem) {
        if (numSids == 0) return false;
        
        stampCounter++;
        int baseStamp = stampCounter * numSids;
        int candidateCount = 0;
        
        // SID 0: Find last occurrence and collect items before it
        {
            int sid = positions[0];
            int[] seq = singletonSeqs[sid]; // Direct flat array access
            int stamp1 = baseStamp + 1;
            
            // Find last occurrence of targetItem
            int lastOcc = seq.length - 1;
            while (lastOcc >= 0 && seq[lastOcc] != targetItem) {
                lastOcc--;
            }
            if (lastOcc <= 0) return false; // No room for prepend
            
            // Collect items in [0, lastOcc)
            for (int k = 0; k < lastOcc; k++) {
                int item = seq[k];
                if (seenStamp[item] != stamp1) {
                    seenStamp[item] = stamp1;
                    candidates[candidateCount++] = item;
                }
            }
        }
        
        if (candidateCount == 0) return false;
        
        // Remaining SIDs: intersect
        for (int i = 1; i < numSids; i++) {
            int sid = positions[i * 4];
            int[] seq = singletonSeqs[sid]; // Direct flat array access
            int curStamp = baseStamp + i + 1;
            int prevStamp = curStamp - 1;
            
            // Find last occurrence of targetItem
            int lastOcc = seq.length - 1;
            while (lastOcc >= 0 && seq[lastOcc] != targetItem) {
                lastOcc--;
            }
            if (lastOcc <= 0) return false;
            
            // Mark items in [0, lastOcc) that have prevStamp
            for (int k = 0; k < lastOcc; k++) {
                int item = seq[k];
                if (seenStamp[item] == prevStamp) {
                    seenStamp[item] = curStamp;
                }
            }
            
            // Filter candidates
            int write = 0;
            for (int c = 0; c < candidateCount; c++) {
                int item = candidates[c];
                if (seenStamp[item] == curStamp) {
                    candidates[write++] = item;
                }
            }
            candidateCount = write;
            if (candidateCount == 0) return false;
        }
        
        return true;
    }
    
    /**
     * Compute first/last envelope positions for all supporting sequences.
     * Populates envFirstBuf and envLastBuf and returns the real pattern length k.
     */
    private int computeEnvelopes(int[] positions, int numSids, int bufferLen) {
        // Envelopes need space for k elements per SID.
        // Estimate k <= bufferLen
        ensureEnvCapacity(numSids * bufferLen);
        
        int[] firstBuf = envFirstBuf;
        int[] lastBuf = envLastBuf;
        
        int k;
        
        if (isSingleton) {
            // SINGLETON PATH:
            // Extract real items from patternBuffer (skip separators -1)
            // patternBuffer: [A, -1, B, -1, C] -> targets: [A, B, C]
            
            // Count items
            k = 0;
            for(int p=0; p<bufferLen; p++) {
                if (patternBuffer[p] != -1) k++;
            }
            // If pattern is empty (should not happen), return 0
            if (k == 0) return 0;
            computeItemsetOffsets(bufferLen, k);
            
            // Extract items
            if (targetsBuf.length < k) {
                targetsBuf = new int[k * 2];
            }
            int[] targets = targetsBuf;
            int p = 0;
            for(int i=0; i<bufferLen; i++) {
                if (patternBuffer[i] != -1) targets[p++] = patternBuffer[i];
            }
            
            int i = 0;
            while (i < numSids) {
                int posIdx = i * 4;
                int sid = positions[posIdx];
                sidBuf[i] = sid;
                int off = i * k; // Tight packing based on real k
                
                int[] seq = singletonSeqs[sid];
                
                // First embedding: scan forward
                int j = 0;
                int pi = 0;
                while (pi < k && j < seq.length) {
                    if (seq[j] == targets[pi]) {
                        firstBuf[off + pi] = j;
                        pi++;
                    }
                    j++;
                }
                // Mark remaining as invalid
                while (pi < k) {
                    firstBuf[off + pi] = -1;
                    pi++;
                }
                
                // Last embedding: scan backward
                j = seq.length - 1;
                pi = k - 1;
                while (pi >= 0 && j >= 0) {
                    if (seq[j] == targets[pi]) {
                        lastBuf[off + pi] = j;
                        pi--;
                    }
                    j--;
                }
                // Mark remaining as invalid
                while (pi >= 0) {
                    lastBuf[off + pi] = -1;
                    pi--;
                }
                
                i++;
            }
        } else {
            // General Path
            k = computeNumItemsets(bufferLen);
            if (k == 0) return 0;
            computeItemsetOffsets(bufferLen, k);
            
            for (int i = 0; i < numSids; i++) {
                int posIdx = i * 4;
                int sid = positions[posIdx];
                sidBuf[i] = sid;
                int[][] seq = sequences[sid];
                int off = i * k;
                
                 // First embedding
                 int j = 0;
                 int pi = 0;
                 while (pi < k) {
                     boolean found = false;
                     while (j < seq.length && !found) {
                         int[] ev = seq[j];
                         if (isSubsetRange(patternBuffer, itemsetOffsets[pi * 2], itemsetOffsets[pi * 2 + 1], ev)) {
                             firstBuf[off + pi] = j;
                             found = true;
                         }
                         j++;
                     }
                     if (!found) {
                         for (int t = pi; t < k; t++) {
                             firstBuf[off + t] = -1;
                         }
                         break;
                     }
                     pi++;
                 }
                 
                 // Last embedding
                 j = seq.length - 1;
                 pi = k - 1;
                 while (pi >= 0) {
                     boolean found = false;
                     while (j >= 0 && !found) {
                         int[] ev = seq[j];
                         if (isSubsetRange(patternBuffer, itemsetOffsets[pi * 2], itemsetOffsets[pi * 2 + 1], ev)) {
                             lastBuf[off + pi] = j;
                             found = true;
                         }
                         j--;
                     }
                     if (!found) {
                         for (int t = 0; t <= pi; t++) {
                             lastBuf[off + t] = -1;
                         }
                         break;
                     }
                     pi--;
                 }
            }
        }
        
        return k;
    }



    
    // Helper to check if small is subset of large (both sorted)
    private boolean isSubset(int[] small, int[] large) {
        int i = 0, j = 0;
        while (i < small.length && j < large.length) {
            if (small[i] == large[j]) {
                i++; j++;
            } else if (large[j] < small[i]) {
                j++;
            } else {
                return false;
            }
        }
        return i == small.length;
    }

    private boolean isSubsetRange(int[] patternBuf, int start, int end, int[] itemset) {
        int i = start;
        int j = 0;
        while (i < end && j < itemset.length) {
            int pv = patternBuf[i];
            int sv = itemset[j];
            if (pv == sv) {
                i++;
                j++;
            } else if (sv < pv) {
                j++;
            } else {
                return false;
            }
        }
        return i == end;
    }
    
    /**
     * Check if a witness exists in a specific logical gap across all SIDs.
     * isPrepend=true: Range [0, lastPos(0))
     * isPrepend=false: Range [firstPos(g)+1, lastPos(g+1))
     */
    private boolean checkGapWitnessRange(int[] positions, int numSids, int[] firstBuf, int[] lastBuf, int numItemsets, int gapIdx, boolean isPrepend, int bufferLen) {
        stampCounter++;
        int baseStamp = stampCounter * numSids;
        int candidateCount = 0;
        
        // SID 0
        {
            int idx = 0;
            int sid = positions[idx];
            int[][] seq = sequences[sid];
            int stamp1 = baseStamp + 1;
            
            int start, end;
            if (isPrepend) {
                start = 0;
                end = lastBuf[idx * numItemsets + 0]; // lastPos(0)
            } else {
                start = firstBuf[idx * numItemsets + gapIdx] + 1;
                end = lastBuf[idx * numItemsets + (gapIdx + 1)];
            }
            
            // Collect candidates
            if (isSingleton) {
                // Optimized loop for singletons - use singletonSeqs
                int[] sseq = singletonSeqs[sid];
                for (int k = start; k < end && k < sseq.length && k >= 0; k++) {
                    int item = sseq[k]; // Direct flat array access
                    
                    // Filter out pattern items
                    boolean inPattern = false;
                    for(int p=0; p<bufferLen; p++) { 
                        if (patternBuffer[p] == item) { inPattern = true; break; } 
                    }
                    
                    if (!inPattern) { 
                        if (seenStamp[item] != stamp1) {
                            if (itemSupport[item] >= numSids) { 
                                seenStamp[item] = stamp1;
                                candidates[candidateCount++] = item;
                            }
                        }
                    }
                }
            } else {
                // General loop
                for (int k = start; k < end && k < seq.length && k >= 0; k++) {
                    int[] itemset = seq[k];
                    for (int item : itemset) {
                        boolean inPattern = false;
                        for(int p=0; p<bufferLen; p++) { 
                            if (patternBuffer[p] == item) { inPattern = true; break; } 
                        }
                        
                        if (!inPattern) { 
                            if (seenStamp[item] != stamp1) {
                                if (itemSupport[item] >= numSids) { 
                                    seenStamp[item] = stamp1;
                                    candidates[candidateCount++] = item;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (candidateCount == 0) return false;
        
        // Intersect
        for (int i = 1; i < numSids; i++) {
            int sid = positions[i * 4];
            int[][] seq = sequences[sid]; 
            int curStamp = baseStamp + i + 1;
            int prevStamp = curStamp - 1;
            
            int start, end;
            if (isPrepend) {
                start = 0;
                end = lastBuf[i * numItemsets + 0];
            } else {
                start = firstBuf[i * numItemsets + gapIdx] + 1;
                end = lastBuf[i * numItemsets + (gapIdx + 1)];
            }
            
             // Mark items in range with curStamp if they have prevStamp
             if (isSingleton) {
                 int[] sseq = singletonSeqs[sid]; // Use singletonSeqs
                 for (int k = start; k < end && k < sseq.length && k >= 0; k++) {
                    int item = sseq[k]; // Direct flat array access
                    if (seenStamp[item] == prevStamp) seenStamp[item] = curStamp;
                 }
                 // Pattern filtering in intersection? strict BIDE says intersect set of items.
                 // If item is in pattern, it was filtered in collecting step, so no need here?"
             } else {
                 for (int k = start; k < end && k < seq.length && k >= 0; k++) {
                    int[] itemset = seq[k];
                    for (int item : itemset) {
                        boolean inPattern = false;
                        for(int p=0; p<bufferLen; p++) { 
                            if (patternBuffer[p] == item) { inPattern = true; break; } 
                        }
                        if (!inPattern) {
                            if (seenStamp[item] == prevStamp) seenStamp[item] = curStamp;
                        }
                    }
                 }
             }
             
             // Filter candidates
             int write = 0;
             for (int c = 0; c < candidateCount; c++) {
                 int item = candidates[c];
                 if (seenStamp[item] == curStamp) {
                     candidates[write++] = item;
                 }
             }
             candidateCount = write;
             if (candidateCount == 0) return false;
        }
        
        return true;
    }
    

    
    /**
     * Compute number of itemsets in the pattern from the flat buffer.
     * Pattern format: items... -1 items... -1 ...
     */
    private int computeNumItemsets(int bufferLen) {
        if (bufferLen == 0) return 0;
        int count = 1; // Start with 1 for the first itemset
        for (int i = 0; i < bufferLen; i++) {
            if (patternBuffer[i] == -1) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Compute itemset offsets from the flat pattern buffer (zero-allocation).
     * Stores [start0, end0, start1, end1, ...] in itemsetOffsets.
     */
    private void computeItemsetOffsets(int bufferLen, int numItemsets) {
        // Ensure capacity
        int needed = numItemsets * 2;
        if (itemsetOffsets.length < needed) {
            itemsetOffsets = new int[needed * 2];
        }
        
        int currentItemsetIdx = 0;
        int start = 0;
        
        for (int i = 0; i < bufferLen; i++) {
            if (patternBuffer[i] == -1) {
                // Store [start, end) for this itemset
                itemsetOffsets[currentItemsetIdx * 2] = start;
                itemsetOffsets[currentItemsetIdx * 2 + 1] = i;
                currentItemsetIdx++;
                start = i + 1;
            }
        }
        
        // Last itemset
        if (currentItemsetIdx < numItemsets) {
            itemsetOffsets[currentItemsetIdx * 2] = start;
            itemsetOffsets[currentItemsetIdx * 2 + 1] = bufferLen;
        }
    }

    
    /**
     * Reconstruct pattern itemsets from the flat buffer.
     * Returns int[k][size_i]
     */
    private int[][] reconstructPatternItemsets(int bufferLen, int numItemsets) {
        int[][] itemsets = new int[numItemsets][];
        
        int currentItemsetIdx = 0;
        int start = 0;
        
        for (int i = 0; i < bufferLen; i++) {
            if (patternBuffer[i] == -1) {
                // Extract itemset
                int len = i - start;
                int[] set = new int[len];
                System.arraycopy(patternBuffer, start, set, 0, len);
                itemsets[currentItemsetIdx++] = set;
                start = i + 1;
            }
        }
        
        // Last itemset
        int len = bufferLen - start;
        if (len > 0) {
            int[] set = new int[len];
            System.arraycopy(patternBuffer, start, set, 0, len);
            itemsets[currentItemsetIdx] = set;
        } else {
             // Should only happen if pattern ends with -1, which is not the in-memory format.
             // Keep a safe empty-itemset fallback anyway.
             itemsets[currentItemsetIdx] = new int[0];
        }
        
        return itemsets;
    }
    

    
    /**
     * Exact closure verification for the current node.
     * This is the expensive verifier used by the default lazy pipeline.
     */
    private boolean isClosedExactCurrentNode(int[] positions, int numSids, int bufferLen) {
        // Singleton k=1 fast path for backward S-prepend only
        if (isSingleton && bufferLen == 1 && patternBuffer[0] != -1) {
            int targetItem = patternBuffer[0];
            return !hasCommonItemBeforeLastOccurrence(positions, numSids, targetItem);
        }

        // Compute envelopes and run exact closure verification
        int k = computeEnvelopes(positions, numSids, bufferLen);

        return closureChecker.isClosedUsingEnvelopesFlat(
                sidBuf,
                numSids,
                patternBuffer,
                itemsetOffsets,
                k,
                envFirstBuf,
                envLastBuf
        );
    }

    /**
     * Save a closed pattern
     */
    private void savePattern(int bufferLen, int support) throws IOException {
        patternCount++;
        
        if (writer != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bufferLen; i++) {
                sb.append(patternBuffer[i]).append(" ");
            }
            sb.append("-1 #SUP: ").append(support);
            writer.write(sb.toString());
            writer.newLine();
        }
    }
    
    public void printStatistics() {
        StringBuilder r = new StringBuilder(200);
        r.append("============  TriBack-Clo - SPMF-2.66 - STATISTICS =====\n Total time ~ ");
        r.append(endTime - startTime);
        r.append(" ms\n Max memory (mb) : ");
        r.append(MemoryLogger.getInstance().getMaxMemory());
        r.append("\n minsup = ").append(minsupAbsolute).append(" sequences.\n Pattern count : ");
        r.append(patternCount);
        r.append("\n   Nodes visited : ").append(nodesVisited);
        r.append("\n   Subtrees pruned : ").append(prunedSubtrees);
        r.append("\n   Nodes gated : ").append(nodesGated);
        r.append("\n   Exact verifier calls : ").append(exactVerifierCalls);
        r.append("\n   Eager wasted verifier calls : ").append(eagerWastedVerifierCalls);
        r.append("\n   Eager verification mode : ").append(enableEagerVerification);
        r.append("\n==========================================================\n");
        System.out.println(r.toString());
    }
}
