package ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * A sequence database. Sequences are parsed into Sequence's flat int[]
 * representation. After loading, infrequent items are dropped and every
 * remaining item is renamed to a dense id ordered by ascending support.
 *
 * While parsing, the database also detects whether every itemset in every
 * sequence holds exactly one item (the common "plain sequence of items"
 * case for public datasets) as opposed to true itemsets with several items.
 * AlgoGSP uses this flag (isPureItemSequences()) to select a cheaper
 * matching and candidate-generation flow.
 * 
 * @author Antonio Gomariz Peñalver,  Philippe Fournier-Viger (some optimizations)
 */
public class SequenceDatabase {

    /**
     * Maps the original item value to the 1-pattern composed of that item.
     * Keyed by original item value; only populated/used before renaming.
     * Cleared as soon as buildAscendingSupportRenaming() has produced the
     * renamed patterns, since it is never looked up by key afterwards.
     */
    private final Map<Integer, Pattern> frequentItems = new HashMap<>(128);

    /**
     * Cache of the frequent 1-patterns already sorted in ascending-support
     * (i.e. ascending renamed-id) order, populated once by
     * buildAscendingSupportRenaming() during loadFile(). When present,
     * frequentItems() returns it directly instead of re-deriving and
     * re-sorting a list from frequentItems.
     */
    private List<Pattern> renamedFrequentItemsSorted;

    private final List<Sequence> sequences = new ArrayList<>(1000);

    /**
     * newToOldItem[renamedId] == originalItem. Null if loadFile was never used.
     */
    private int[] newToOldItem;

    /**
     * True as long as every itemset seen so far (across every sequence) has
     * held exactly one item. Set to false as soon as an itemset with more
     * than one item is encountered.
     */
    private boolean pureItemSequences = true;

    /**
     * Creates an empty sequence database.
     */
    public SequenceDatabase() {
    }

    /**
     * Loads the database from a file, then filters infrequent items and
     * renames the survivors to a dense ascending-support id space.
     *
     * @param path                 the input file path (SPMF sequence format)
     * @param minSupportAbsolute   the minimum support as a fraction of the
     *                             number of sequences (0 to 1)
     * @throws IOException if an error occurs while reading the file
     */
    public void loadFile(String path, double minSupportAbsolute) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                char firstChar = line.charAt(0);
                if (firstChar == '#' || firstChar == '%' || firstChar == '@') {
                    continue;
                }
                parseAndAddSequence(line);
            }
        }

        // Use the SAME epsilon-corrected rounding as AlgoGSP.runAlgorithm()'s
        int minSupRelativeRaw = (int) Math.ceil(minSupportAbsolute * sequences.size() - 1e-10);
        final int minSupThreshold = (minSupRelativeRaw == 0) ? 1 : minSupRelativeRaw;
        frequentItems.values().removeIf(pattern -> pattern.getSupport() < minSupThreshold);

        int[] oldToNew = buildAscendingSupportRenaming();
        shrinkAndRenameDatabase(oldToNew);
    }
    /**
     * Parses one input line directly into the flat Sequence representation,
     * without String.split or per-token String allocation. Numbers are read
     * digit-by-digit off the raw line. Also tracks, per itemset, whether it
     * holds more than one item, to determine pureItemSequences.
     *
     * @param line the raw input line to parse (one sequence, SPMF format)
     */
    private void parseAndAddSequence(String line) {
        int length = line.length();
        int sequenceId = sequences.size();

        int[] items = new int[16];
        int itemCount = 0;
        int[] offsets = new int[8];
        int offsetCount = 0;
        offsets[offsetCount++] = 0;
        int itemsInCurrentItemset = 0;

        int pos = 0;
        while (pos < length) {
            char c = line.charAt(pos);
            if (c == ' ') {
                pos++;
                continue;
            }

            boolean negative = false;
            if (c == '-') {
                negative = true;
                pos++;
            }

            int value = 0;
            while (pos < length && line.charAt(pos) != ' ') {
                value = value * 10 + (line.charAt(pos) - '0');
                pos++;
            }

            if (negative) {
                if (value == 1) {
                    if (itemsInCurrentItemset > 1) {
                        pureItemSequences = false;
                    }
                    itemsInCurrentItemset = 0;
                    if (offsetCount == offsets.length) {
                        offsets = Arrays.copyOf(offsets, offsets.length * 2);
                    }
                    offsets[offsetCount++] = itemCount;
                } else if (value == 2) {
                    int[] finalItems = Arrays.copyOf(items, itemCount);
                    for (int i = 0; i < offsetCount - 1; i++) {
                        Arrays.sort(finalItems, offsets[i], offsets[i + 1]);
                    }
                    int[] finalOffsets = Arrays.copyOf(offsets, offsetCount);
                    sequences.add(new Sequence(finalItems, finalOffsets));
                    return;
                }
            } else {
                // frequentItems is still keyed by raw item value here;
                // autoboxing is unavoidable until the renaming below fires.
                Pattern pattern = frequentItems.get(value);
                if (pattern == null) {
                    pattern = new Pattern(value, false);
                    frequentItems.put(value, pattern);
                }
                pattern.addAppearance(sequenceId);

                if (itemCount == items.length) {
                    items = Arrays.copyOf(items, items.length * 2);
                }
                items[itemCount++] = value;
                itemsInCurrentItemset++;
            }
        }
    }

    /**
     * API-compatibility entry point for callers that pre-tokenise lines.
     * loadFile() uses parseAndAddSequence() directly on the raw line.
     *
     * @param tokens the tokens of one sequence line (SPMF format), or null/empty
     *               to do nothing
     */
    public void addSequence(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return;
        }

        int sequenceId = sequences.size();

        int[] items = new int[Math.max(tokens.length, 4)];
        int itemCount = 0;
        int[] offsets = new int[8];
        int offsetCount = 0;
        offsets[offsetCount++] = 0;
        int itemsInCurrentItemset = 0;

        for (String token : tokens) {
            char firstChar = token.charAt(0);

            if (firstChar == '-' && token.length() == 2 && token.charAt(1) == '1') {
                if (itemsInCurrentItemset > 1) {
                    pureItemSequences = false;
                }
                itemsInCurrentItemset = 0;
                if (offsetCount == offsets.length) {
                    offsets = Arrays.copyOf(offsets, offsets.length * 2);
                }
                offsets[offsetCount++] = itemCount;
            } else if (firstChar == '-' && token.length() == 2 && token.charAt(1) == '2') {
                int[] finalItems = Arrays.copyOf(items, itemCount);
                for (int i = 0; i < offsetCount - 1; i++) {
                    Arrays.sort(finalItems, offsets[i], offsets[i + 1]);
                }
                int[] finalOffsets = Arrays.copyOf(offsets, offsetCount);
                sequences.add(new Sequence(finalItems, finalOffsets));
                return;
            } else {
                int item = Integer.parseInt(token);

                Pattern pattern = frequentItems.get(item);
                if (pattern == null) {
                    pattern = new Pattern(item, false);
                    frequentItems.put(item, pattern);
                }
                pattern.addAppearance(sequenceId);

                if (itemCount == items.length) {
                    items = Arrays.copyOf(items, items.length * 2);
                }
                items[itemCount++] = item;
                itemsInCurrentItemset++;
            }
        }
    }

    /**
     * Appends an already-built sequence to the database and updates the
     * pureItemSequences flag accordingly.
     *
     * @param sequence the sequence to add
     */
    public void addSequence(Sequence sequence) {
        sequences.add(sequence);
        if (pureItemSequences) {
            int[] offsets = sequence.getItemsetOffsets();
            for (int i = 0; i < offsets.length - 1; i++) {
                if (offsets[i + 1] - offsets[i] > 1) {
                    pureItemSequences = false;
                    break;
                }
            }
        }
    }

    /**
     * Builds a human-readable dump of every sequence in the database.
     *
     * @return the string representation of the database
     */
    @Override
    public String toString() {
        StringBuilder r = new StringBuilder(sequences.size() * 100);
        for (int i = 0; i < sequences.size(); i++) {
            r.append(i).append(":  ").append(sequences.get(i).toString()).append('\n');
        }
        return r.toString();
    }

    /**
     * Returns the number of sequences in the database.
     *
     * @return the number of sequences
     */
    public int size() {
        return sequences.size();
    }

    /**
     * Returns the live list of sequences. Callers should not structurally
     * modify it except through the database's own methods.
     *
     * @return the list of sequences
     */
    public List<Sequence> getSequences() {
        return sequences;
    }

    /**
     * Returns whether every itemset in every sequence of this database holds
     * exactly one item. When true, AlgoGSP uses the cheaper item-sequence
     * matching and candidate-generation flow instead of the general itemset
     * flow.
     *
     * @return true if every itemset holds exactly one item
     */
    public boolean isPureItemSequences() {
        return pureItemSequences;
    }

    /**
     * Returns the frequent 1-patterns. After loadFile, their items are
     * renamed ids sorted ascending (i.e. ascending by original support).
     *
     * @return the sorted list of frequent 1-patterns
     */
    public List<Pattern> frequentItems() {
        if (renamedFrequentItemsSorted != null) {
            return new ArrayList<>(renamedFrequentItemsSorted);
        }
        List<Pattern> frequentPatterns = new ArrayList<>(frequentItems.values());
        Collections.sort(frequentPatterns);
        return frequentPatterns;
    }

    /**
     * Returns the renamed-id -> original-item mapping, or null if loadFile
     * was never used and no renaming took place.
     *
     * @return the renamed-id to original-item mapping array, or null
     */
    public int[] getItemMapping() {
        return newToOldItem;
    }

    /**
     * Replaces every sequence in place with a filtered copy keeping only the
     * active items, collapsing sequences that fall below minItemCount to the
     * shared EMPTY sentinel. Sequences are never removed or reordered, so
     * every Pattern's appearingIn BitSet remains valid without translation.
     *
     * Already-empty sequences are skipped to avoid redundant work.
     *
     * @param activeItemMask the mask of items to keep active
     * @param minItemCount   the minimum surviving item count for a sequence
     *                       to remain worth keeping
     */
    public void projectToActiveItems(boolean[] activeItemMask, int minItemCount) {
        for (int i = 0; i < sequences.size(); i++) {
            Sequence current = sequences.get(i);
            if (current.getLength() == 0) {
                continue;
            }
            sequences.set(i, current.filterActiveItems(activeItemMask, minItemCount));
        }
    }

    /**
     * Resets this database to an empty state, ready for a new load.
     */
    public void clear() {
        sequences.clear();
        frequentItems.clear();
        renamedFrequentItemsSorted = null;
        newToOldItem = null;
        pureItemSequences = true;
    }

    /**
     * Assigns every frequent item a dense id in [0, n), ordered by ascending
     * support (ties broken by original item value for determinism). Builds
     * the renamed patterns directly into renamedFrequentItemsSorted, in that
     * same ascending order, so frequentItems() can hand that list out
     * as-is later with no further HashMap round-trip or re-sort. The raw,
     * pre-rename map is cleared immediately afterwards since it is never
     * looked up by key again, freeing its Pattern objects early.
     *
     * @return an array mapping original item value to its dense renamed id;
     *         positions corresponding to items that are not frequent (or
     *         out of range) hold a negative value
     */
    private int[] buildAscendingSupportRenaming() {
        List<Map.Entry<Integer, Pattern>> entries = new ArrayList<>(frequentItems.entrySet());
        entries.sort((e1, e2) -> {
            int cmp = Integer.compare(e1.getValue().getSupport(), e2.getValue().getSupport());
            return (cmp != 0) ? cmp : Integer.compare(e1.getKey(), e2.getKey());
        });

        int n = entries.size();
        newToOldItem = new int[n];

        int maxOldItem = -1;
        for (Map.Entry<Integer, Pattern> entry : entries) {
            if (entry.getKey() > maxOldItem) {
                maxOldItem = entry.getKey();
            }
        }
        int[] oldToNew = new int[maxOldItem + 1];
        Arrays.fill(oldToNew, -1);

        List<Pattern> renamedSorted = new ArrayList<>(n);
        for (int newId = 0; newId < n; newId++) {
            Map.Entry<Integer, Pattern> entry = entries.get(newId);
            int oldItem = entry.getKey();
            Pattern oldPattern = entry.getValue();

            newToOldItem[newId] = oldItem;
            oldToNew[oldItem] = newId;

            Pattern renamedPattern = new Pattern(newId, false);
            renamedPattern.setAppearingIn(oldPattern.getAppearingIn());
            renamedSorted.add(renamedPattern);
        }

        frequentItems.clear();
        renamedFrequentItemsSorted = renamedSorted;
        return oldToNew;
    }

    /**
     * Replaces every sequence with a filtered-and-renamed copy that keeps
     * only the items present in oldToNew, translated through it.
     *
     * @param oldToNew the original-item -> renamed-id lookup table (negative
     *                 entries mean "not frequent, drop this item")
     */
    private void shrinkAndRenameDatabase(int[] oldToNew) {
        for (int i = 0; i < sequences.size(); i++) {
            sequences.set(i, sequences.get(i).filterAndRename(oldToNew));
        }
    }
}