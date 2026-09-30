package ca.pfv.spmf.algorithms.sequentialpatterns.spam_oldversion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import ca.pfv.spmf.patterns.itemset_list_integers_without_support.Itemset;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the VMSP algorithm (mining maximal sequential
 * patterns).
 * <p>
 * Maximality is determined via the forward DFS search (S-steps and I-steps)
 * combined with the post-hoc subsumption performed in
 * {@link #savePatternMultipleItems}: any witness pattern that would make a
 * shorter pattern non-maximal is itself generated during the search and
 * retroactively removes the shorter, subsumed pattern.
 */
public class AlgoVMSP {

    /** for statistics */
    public long startTime;
    public long endTime;
    public int patternCount;

    /** minsup */
    private int minsup = 0;

    /** object to write to a file */
    BufferedWriter writer = null;

    /** Vertical database */
    Map<Integer, Bitmap> verticalDB = new HashMap<Integer, Bitmap>();

    /** List indicating the number of bits per sequence */
    List<Integer> sequencesSize = null;

    /** the last bit position that is used in bitmaps */
    int lastBitIndex = 0;

    /** maximum pattern length in terms of item count */
    private int maximumPatternLength = 1000;

    /**
     * Map: key: item value: another item that followed the first item + support
     * (could be replaced with a triangular matrix...)
     */
    Map<Integer, Map<Integer, Integer>> coocMapAfter = null;
    Map<Integer, Map<Integer, Integer>> coocMapEquals = null;

    /**
     * Map indicating for each item, the smallest tid containing this item in a
     * sequence.
     */
    Map<Integer, Short> lastItemPositionMap;
    boolean useCMAPPruning = true;
    boolean useLastPositionPruning = false;

    /** STRUCTURE TO STORE MAXIMAL PATTERNS BY ASCENDING ORDER OF SUPPORT */
    List<TreeSet<PatternVMSP>> maxPatterns = null;

    /**
     * Indicate if the forward checking strategy should be used or not by the
     * algorithm
     */
    private boolean useStrategyForwardExtensionChecking = true;

    /**
     * the max gap between two itemsets of a pattern. It is an optional parameter
     * that the user can set.
     */
    private int maxGap = Integer.MAX_VALUE;

    /**
     * Optional parameter to decide whether sequence identifiers should be shown in
     * the output for each pattern found
     */
    private boolean outputSequenceIdentifiers;

    /** DEBUG mode for debugging **/
    private boolean DEBUG = false;

    /**
     * this variable is true if the user has called the setMaxGap method at least
     * once
     */
    private boolean maxGapActivated = false;

    /**
     * Default constructor
     */
    public AlgoVMSP() {
    }

    /**
     * Method to run the algorithm
     *
     * @param input          path to an input file
     * @param outputFilePath path for writing the output file
     * @param minsupRel      the minimum support as a relative value
     * @throws IOException exception if error while writing the file or reading
     */
    public List<TreeSet<PatternVMSP>> runAlgorithm(String input, String outputFilePath, double minsupRel)
            throws IOException {
        Bitmap.INTERSECTION_COUNT = 0;
        writer = new BufferedWriter(new FileWriter(outputFilePath));
        patternCount = 0;
        MemoryLogger.getInstance().reset();

        startTime = System.currentTimeMillis();
        vmsp(input, minsupRel);
        endTime = System.currentTimeMillis();
        writeResultTofile(outputFilePath);
        writer.close();

        return maxPatterns;
    }

    /**
     * This is the main method for the VMSP algorithm.
     *
     * @param input      an input file
     * @param minsupRel  the minimum support as a relative value
     * @throws IOException
     */
    private void vmsp(String input, double minsupRel) throws IOException {
        maxPatterns = new ArrayList<TreeSet<PatternVMSP>>(20);
        maxPatterns.add(null);
        maxPatterns.add(new TreeSet<PatternVMSP>());

        verticalDB = new HashMap<Integer, Bitmap>();
        List<int[]> inMemoryDB = new ArrayList<int[]>();

        if (DEBUG) {
            System.out.println(" == HORIZONTAL DATABASE ==");
        }

        // STEP 0: SCAN THE DATABASE TO STORE THE FIRST BIT POSITION OF EACH SEQUENCE
        sequencesSize = new ArrayList<Integer>();
        lastBitIndex = 0;
        try {
            FileInputStream fin = new FileInputStream(new File(input));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;
            int bitIndex = 0;
            while ((thisLine = reader.readLine()) != null) {
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                sequencesSize.add(bitIndex);
                String tokens[] = thisLine.split(" ");
                int[] transactionArray = new int[tokens.length];
                inMemoryDB.add(transactionArray);
                for (int i = 0; i < tokens.length; i++) {
                    int item = Integer.parseInt(tokens[i]);
                    transactionArray[i] = item;
                    if (item == -1) {
                        bitIndex++;
                    }
                }
                if (DEBUG) {
                    System.out.println(" " + Arrays.toString(transactionArray));
                }
            }
            lastBitIndex = bitIndex - 1;
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        minsup = (int) Math.ceil((minsupRel * sequencesSize.size()) - 0.000001);
        if (minsup == 0) {
            minsup = 1;
        }

        // STEP 1: SCAN THE DATABASE TO CREATE THE BITMAP VERTICAL DATABASE
        try {
            FileInputStream fin = new FileInputStream(new File(input));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;
            int sid = 0;
            int tid = 0;
            while ((thisLine = reader.readLine()) != null) {
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                for (String token : thisLine.split(" ")) {
                    if (token.equals("-1")) {
                        tid++;
                    } else if (token.equals("-2")) {
                        sid++;
                        tid = 0;
                    } else {
                        Integer item = Integer.parseInt(token);
                        Bitmap bitmapItem = verticalDB.get(item);
                        if (bitmapItem == null) {
                            bitmapItem = new Bitmap(lastBitIndex);
                            verticalDB.put(item, bitmapItem);
                        }
                        bitmapItem.registerBit(sid, tid, sequencesSize);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // STEP 2: REMOVE INFREQUENT ITEMS
        List<Integer> frequentItems = new ArrayList<Integer>();
        Iterator<Entry<Integer, Bitmap>> iter = verticalDB.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Bitmap> entry = iter.next();
            if (entry.getValue().getSupport() < minsup) {
                iter.remove();
            } else {
                frequentItems.add(entry.getKey());
            }
        }

        if (DEBUG) {
            System.out.println(" == VERTICAL DATABASE ==");
            for (Entry<Integer, Bitmap> itemBitmap : verticalDB.entrySet()) {
                System.out.print(" item " + itemBitmap.getKey());
                System.out.println(" bitmap " + itemBitmap.getValue().bitmap);
            }
            System.out.println(" =======================");
        }

        // STEP 2.1: SORT ITEMS BY ASCENDING SUPPORT
        Collections.sort(frequentItems, new Comparator<Integer>() {
            @Override
            public int compare(Integer arg0, Integer arg1) {
                return verticalDB.get(arg0).getSupport() - verticalDB.get(arg1).getSupport();
            }
        });

        if (DEBUG) {
            System.out.println(" == Order of items ==");
            System.out.println(frequentItems);
            System.out.println(" ====================");
        }

        // STEP 3.1: CREATE CMAP
        coocMapEquals = new HashMap<Integer, Map<Integer, Integer>>(frequentItems.size());
        coocMapAfter  = new HashMap<Integer, Map<Integer, Integer>>(frequentItems.size());

        if (useLastPositionPruning) {
            lastItemPositionMap = new HashMap<Integer, Short>(frequentItems.size());
        }

        for (int[] transaction : inMemoryDB) {
            short itemsetCount = 0;

            Set<Integer> alreadyProcessed = new HashSet<Integer>();
            Map<Integer, Set<Integer>> equalProcessed = new HashMap<>();
            loopI: for (int i = 0; i < transaction.length; i++) {
                Integer itemI = transaction[i];

                Set<Integer> equalSet = equalProcessed.get(itemI);
                if (equalSet == null) {
                    equalSet = new HashSet<Integer>();
                    equalProcessed.put(itemI, equalSet);
                }

                if (itemI < 0) {
                    itemsetCount++;
                    continue;
                }

                if (useLastPositionPruning) {
                    Short last = lastItemPositionMap.get(itemI);
                    if (last == null || last < itemsetCount) {
                        lastItemPositionMap.put(itemI, itemsetCount);
                    }
                }

                Bitmap bitmapOfItem = verticalDB.get(itemI);
                if (bitmapOfItem == null || bitmapOfItem.getSupport() < minsup) {
                    continue;
                }

                Set<Integer> alreadyProcessedB = new HashSet<Integer>();

                boolean sameItemset = true;
                for (int j = i + 1; j < transaction.length; j++) {
                    Integer itemJ = transaction[j];

                    if (itemJ < 0) {
                        sameItemset = false;
                        continue;
                    }

                    Bitmap bitmapOfitemJ = verticalDB.get(itemJ);
                    if (bitmapOfitemJ == null || bitmapOfitemJ.getSupport() < minsup) {
                        continue;
                    }

                    Map<Integer, Integer> map = null;
                    if (sameItemset) {
                        if (!equalSet.contains(itemJ)) {
                            map = coocMapEquals.get(itemI);
                            if (map == null) {
                                map = new HashMap<Integer, Integer>();
                                coocMapEquals.put(itemI, map);
                            }
                            Integer support = map.get(itemJ);
                            if (support == null) {
                                map.put(itemJ, 1);
                            } else {
                                map.put(itemJ, ++support);
                            }
                            equalSet.add(itemJ);
                        }
                    } else if (!alreadyProcessedB.contains(itemJ)) {
                        if (alreadyProcessed.contains(itemI)) {
                            continue loopI;
                        }
                        map = coocMapAfter.get(itemI);
                        if (map == null) {
                            map = new HashMap<Integer, Integer>();
                            coocMapAfter.put(itemI, map);
                        }
                        Integer support = map.get(itemJ);
                        if (support == null) {
                            map.put(itemJ, 1);
                        } else {
                            map.put(itemJ, ++support);
                        }
                        alreadyProcessedB.add(itemJ);
                    }
                }
                alreadyProcessed.add(itemI);
            }
        }

        // STEP 3: DEPTH-FIRST SEARCH
        for (Entry<Integer, Bitmap> entry : verticalDB.entrySet()) {
            PrefixVMSP prefix = new PrefixVMSP();
            prefix.addItemset(new Itemset(entry.getKey()));
            boolean itemIsEven = entry.getKey() % 2 == 0;
            if (itemIsEven) {
                prefix.sumOfEvenItems = entry.getKey();
                prefix.sumOfOddItems = 0;
            } else {
                prefix.sumOfEvenItems = 0;
                prefix.sumOfOddItems = entry.getKey();
            }

            boolean hasExtension = false;
            if (maximumPatternLength > 1) {
                hasExtension = dfsPruning(prefix, entry.getValue(), frequentItems, frequentItems,
                        entry.getKey(), 2, entry.getKey());
            }
            if (!hasExtension) {
                savePatternSingleItem(entry.getKey(), entry.getValue(), itemIsEven);
            }
        }
    }

    /**
     * This is the dfsPruning method as described in the SPAM paper.
     *
     * @param prefix                     the current prefix
     * @param prefixBitmap               the bitmap corresponding to the current prefix
     * @param sn                         a list of items to be considered for s-steps
     * @param in                         a list of items to be considered for i-steps
     * @param hasToBeGreaterThanForIStep
     * @param m                          size of the current prefix in terms of items
     * @param lastAppendedItem           the last appended item to the prefix
     * @throws IOException if there is an error writing a pattern to the output file
     * @return TRUE IF A FREQUENT PATTERN WAS CREATED USING THE PREFIX.
     */
    boolean dfsPruning(PrefixVMSP prefix, Bitmap prefixBitmap, List<Integer> sn, List<Integer> in,
            int hasToBeGreaterThanForIStep, int m, Integer lastAppendedItem) throws IOException {
        boolean atLeastOneFrequentExtension = false;

        if (DEBUG) {
            System.out.println("PREFIX: " + prefix.toString() + "  sn=" + sn + " in=" + in);
        }

        // ====== S-STEPS ======
        List<Integer> sTemp = new ArrayList<Integer>();
        List<Bitmap>  sTempBitmaps = new ArrayList<Bitmap>();

        Map<Integer, Integer> mapSupportItemsAfter = coocMapAfter.get(lastAppendedItem);

        loopi: for (Integer i : sn) {
            if (useCMAPPruning) {
                if (mapSupportItemsAfter == null) {
                    continue loopi;
                }
                Integer support = mapSupportItemsAfter.get(i);
                if (support == null || support < minsup) {
                    continue loopi;
                }
            }
            Bitmap.INTERSECTION_COUNT++;
            Bitmap newBitmap = prefixBitmap.createNewBitmapSStep(verticalDB.get(i), sequencesSize,
                    lastBitIndex, maxGap);
            if (newBitmap.getSupportWithoutGapTotal() >= minsup) {
                sTemp.add(i);
                sTempBitmaps.add(newBitmap);
            }
        }

        for (int k = 0; k < sTemp.size(); k++) {
            int item = sTemp.get(k);
            PrefixVMSP prefixSStep = prefix.cloneSequence();
            prefixSStep.addItemset(new Itemset(item));
            if (item % 2 == 0) {
                prefixSStep.sumOfEvenItems = item + prefix.sumOfEvenItems;
                prefixSStep.sumOfOddItems  = prefix.sumOfOddItems;
            } else {
                prefixSStep.sumOfEvenItems = prefix.sumOfEvenItems;
                prefixSStep.sumOfOddItems  = item + prefix.sumOfOddItems;
            }
            Bitmap newBitmap = sTempBitmaps.get(k);

            if (newBitmap.getSupport() >= minsup) {
                boolean hasFrequentExtension = false;
                if (maximumPatternLength > m) {
                    hasFrequentExtension = dfsPruning(prefixSStep, newBitmap, sTemp, sTemp,
                            item, m + 1, item);
                }

                atLeastOneFrequentExtension = true;
                if (!hasFrequentExtension) {
                    savePatternMultipleItems(prefixSStep, newBitmap, m);
                }
            }
        }

        // ====== I-STEPS ======
        Map<Integer, Integer> mapSupportItemsEquals = coocMapEquals.get(lastAppendedItem);
        List<Integer> iTemp = new ArrayList<Integer>();
        List<Bitmap>  iTempBitmaps = new ArrayList<Bitmap>();

        loop2: for (Integer i : in) {
            if (i > hasToBeGreaterThanForIStep) {
                if (useCMAPPruning) {
                    if (mapSupportItemsEquals == null) {
                        continue loop2;
                    }
                    Integer support = mapSupportItemsEquals.get(i);
                    if (support == null || support < minsup) {
                        continue loop2;
                    }
                }
                Bitmap.INTERSECTION_COUNT++;
                Bitmap newBitmap = prefixBitmap.createNewBitmapIStep(verticalDB.get(i), sequencesSize,
                        lastBitIndex);
                if (newBitmap.getSupport() >= minsup) {
                    iTemp.add(i);
                    iTempBitmaps.add(newBitmap);
                }
            }
        }

        for (int k = 0; k < iTemp.size(); k++) {
            int item = iTemp.get(k);
            PrefixVMSP prefixIStep = prefix.cloneSequence();
            prefixIStep.getItemsets().get(prefixIStep.size() - 1).addItem(item);
            if (item % 2 == 0) {
                prefixIStep.sumOfEvenItems = item + prefix.sumOfEvenItems;
                prefixIStep.sumOfOddItems  = prefix.sumOfOddItems;
            } else {
                prefixIStep.sumOfEvenItems = prefix.sumOfEvenItems;
                prefixIStep.sumOfOddItems  = item + prefix.sumOfOddItems;
            }
            Bitmap newBitmap = iTempBitmaps.get(k);

            boolean hasFrequentExtension = false;
            if (maximumPatternLength > m) {
                hasFrequentExtension = dfsPruning(prefixIStep, newBitmap, sTemp, iTemp,
                        item, m + 1, item);
            }

            atLeastOneFrequentExtension = true;
            if (!hasFrequentExtension) {
                savePatternMultipleItems(prefixIStep, newBitmap, m);
            }
        }

        MemoryLogger.getInstance().checkMemory();
        return atLeastOneFrequentExtension || !useStrategyForwardExtensionChecking;
    }

    // =========================================================================
    // SAVE / SUBSUMPTION / OUTPUT
    // =========================================================================

    /**
     * Save a pattern of size 1 to the output file
     */
    private boolean savePatternSingleItem(Integer item, Bitmap bitmap, boolean itemIsEven) throws IOException {
        if (DEBUG) {
            System.out.println("Trying to save : " + item);
        }
        if (itemIsEven) {
            for (int i = maxPatterns.size() - 1; i > 1; i--) {
                for (PatternVMSP pPrime : maxPatterns.get(i)) {
                    if (pPrime.prefix.sumOfOddItems + pPrime.prefix.sumOfEvenItems < item) {
                        break;
                    }
                    boolean supportCheck = maxGapActivated || bitmap.getSupport() >= pPrime.support;
                    if (pPrime.prefix.sumOfEvenItems >= item && supportCheck) {
                        if (pPrime.prefix.containsItem(item)) {
                            return true;
                        }
                    }
                }
            }
        } else {
            for (int i = maxPatterns.size() - 1; i > 1; i--) {
                for (PatternVMSP pPrime : maxPatterns.get(i)) {
                    if (pPrime.prefix.sumOfOddItems + pPrime.prefix.sumOfEvenItems < item) {
                        break;
                    }
                    boolean supportCheck = maxGapActivated || bitmap.getSupport() >= pPrime.support;
                    if (pPrime.prefix.sumOfOddItems >= item && supportCheck) {
                        if (pPrime.prefix.containsItem(item)) {
                            return true;
                        }
                    }
                }
            }
        }
        patternCount++;
        PrefixVMSP prefix = new PrefixVMSP();
        prefix.addItemset(new Itemset(item));
        if (itemIsEven) {
            prefix.sumOfEvenItems = item;
            prefix.sumOfOddItems  = 0;
        } else {
            prefix.sumOfEvenItems = 0;
            prefix.sumOfOddItems  = item;
        }
        PatternVMSP pattern = new PatternVMSP(prefix, bitmap.getSupport());
        if (outputSequenceIdentifiers) {
            pattern.bitmap = bitmap;
        }
        maxPatterns.get(1).add(pattern);
        if (DEBUG) {
            System.out.println(" saved");
        }
        return false;
    }

    /**
     * Save a pattern of size > 1 to the output file.
     */
    private boolean savePatternMultipleItems(PrefixVMSP prefix, Bitmap bitmap, int length) throws IOException {
        if (DEBUG) {
            System.out.println("*Trying to save : " + prefix);
        }
        for (int i = maxPatterns.size() - 1; i > length; i--) {
            for (PatternVMSP pPrime : maxPatterns.get(i)) {
                if (pPrime.prefix.sumOfOddItems + pPrime.prefix.sumOfEvenItems < prefix.sumOfOddItems
                        + prefix.sumOfEvenItems) {
                    break;
                }
                boolean supportCheck = maxGapActivated || bitmap.getSupport() >= pPrime.support;
                if (prefix.sumOfEvenItems <= pPrime.prefix.sumOfEvenItems
                        && prefix.sumOfOddItems <= pPrime.prefix.sumOfOddItems && supportCheck
                        && strictlyContains(pPrime.prefix, prefix)) {
                    return true;
                }
            }
        }

        for (int i = 1; i < length && i < maxPatterns.size(); i++) {
            Iterator<PatternVMSP> it = maxPatterns.get(i).descendingIterator();
            while (it.hasNext()) {
                PatternVMSP pPrime = it.next();
                if (pPrime.prefix.sumOfOddItems + pPrime.prefix.sumOfEvenItems >= prefix.sumOfOddItems
                        + prefix.sumOfEvenItems) {
                    break;
                }
                boolean supportCheck = maxGapActivated || bitmap.getSupport() <= pPrime.support;
                if (prefix.sumOfEvenItems >= pPrime.prefix.sumOfEvenItems
                        && prefix.sumOfOddItems >= pPrime.prefix.sumOfOddItems && supportCheck
                        && strictlyContains(prefix, pPrime.prefix)) {
                    patternCount--;
                    if (DEBUG) {
                        System.out.println("REMOVE : " + pPrime.prefix);
                    }
                    it.remove();
                }
            }
        }

        while (maxPatterns.size() - 1 < length) {
            maxPatterns.add(new TreeSet<PatternVMSP>());
        }

        patternCount++;
        PatternVMSP pattern = new PatternVMSP(prefix, bitmap.getSupport());
        if (outputSequenceIdentifiers) {
            pattern.bitmap = bitmap;
        }
        maxPatterns.get(length).add(pattern);

        if (DEBUG) {
            System.out.println(" saved");
        }
        return false;
    }

    /**
     * This method checks if a seq. pattern "pattern2" is strictly contained in a
     * seq. pattern "pattern1" (a purely structural subsequence test, deliberately
     * ignoring {@code maxGap}: {@code maxGap} constrains what counts as a valid
     * occurrence of a pattern WITHIN A DATABASE SEQUENCE, and has no bearing on
     * this structural comparison between two already-mined patterns, which is
     * used to decide whether one renders the other non-maximal).
     */
    boolean strictlyContains(PrefixVMSP pattern1, PrefixVMSP pattern2) {
        int i = 0;
        int j = 0;
        while (true) {
            if (pattern1.get(j).containsAll(pattern2.get(i))) {
                i++;
                if (i == pattern2.size()) {
                    return true;
                }
            }
            j++;
            if (j >= pattern1.size()) {
                return false;
            }
            if ((pattern1.size() - j) < pattern2.size() - i) {
                return false;
            }
        }
    }

    /**
     * Print the statistics of the algorithm execution to System.out.
     */
    public void printStatistics() {
        StringBuilder r = new StringBuilder(200);
        r.append("=============  VMSP v0.97 - STATISTICS =============\n Total time ~ ");
        r.append(endTime - startTime);
        r.append(" ms\n");
        r.append(" Frequent sequences count : " + patternCount);
        r.append('\n');
        r.append(" Max memory (mb) : ");
        r.append(MemoryLogger.getInstance().getMaxMemory());
        r.append(patternCount);
        r.append('\n');
        r.append("minsup " + minsup);
        r.append('\n');
        r.append("Intersection count " + Bitmap.INTERSECTION_COUNT + " \n");
        r.append("===================================================\n");
        System.out.println(r.toString());
    }

    public int getMaximumPatternLength() {
        return maximumPatternLength;
    }

    public void setMaximumPatternLength(int maximumPatternLength) {
        this.maximumPatternLength = maximumPatternLength;
    }

    /**
     * Write the result to an output file
     */
    public void writeResultTofile(String path) throws IOException {
        for (TreeSet<PatternVMSP> tree : maxPatterns) {
            if (tree == null) {
                continue;
            }
            for (PatternVMSP pattern : tree) {
                StringBuilder r = new StringBuilder("");
                for (Itemset itemset : pattern.prefix.getItemsets()) {
                    for (Integer item : itemset.getItems()) {
                        r.append(item.toString());
                        r.append(' ');
                    }
                    r.append("-1 ");
                }
                r.append("#SUP: ");
                r.append(pattern.support);
                if (outputSequenceIdentifiers) {
                    r.append(" #SID: ");
                    r.append(pattern.bitmap.getSIDs(sequencesSize));
                }
                writer.write(r.toString());
                writer.newLine();
            }
        }
    }

    public void setMaxGap(int maxGap) {
        this.maxGapActivated = true;
        this.maxGap = maxGap;
    }

    public void showSequenceIdentifiersInOutput(boolean showSequenceIdentifiers) {
        this.outputSequenceIdentifiers = showSequenceIdentifiers;
    }
}