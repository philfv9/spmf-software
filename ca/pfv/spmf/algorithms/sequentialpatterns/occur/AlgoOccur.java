package ca.pfv.spmf.algorithms.sequentialpatterns.occur;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.SequenceDatabase;
import ca.pfv.spmf.tools.MemoryLogger;

/*
 *
 * Copyright (c) 2008-2012 Philippe Fournier-Viger
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
 * Do not remove copyright and license information.
 */

/**
 * This is an implementation of the OCCUR algorithm.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoOccur {

    /** for statistics **/
    long startTime;
    long endTime;

    /** writer to write output file */
    BufferedWriter writer = null;

    /** the sequence database **/
    SequenceDatabase sequenceDatabase;

    /** Raw view of the sequence database to avoid ArrayList.get() overhead */
    private int[][] sequencesArray;

    /**
     * Reusable stack holding, for the pattern currently being matched, the
     * itemset position (in the sequence) chosen for each level (itemset)
     * of the pattern.
     */
    private int[] occurrencePositions = new int[16];

    /** Reusable growable buffer for the pattern tokens of the current line. */
    private int[] patternBuffer = new int[64];
    private int patternLength;

    /** Reusable growable buffer for the sids of the current line. */
    private int[] sidBuffer = new int[256];
    private int sidLength;

    /** Positions (within the raw line) delimiting the text to re-emit as-is. */
    private int patternTextEnd; // exclusive end of the pattern text
    private int supStart;       // inclusive start of the "#SUP: ..." text
    private int supEnd;         // exclusive end of the "#SUP: ..." text

    /**
     * Flag used while writing the occurrences of a single sid, so that we
     * know whether a separating space must be written before the next
     * occurrence.
     */
    private boolean firstOccurrenceForSid;

    /**
     * Reusable buffer used to build a complete output line before it is
     * flushed to the underlying writer with a single call.
     */
    private final StringBuilder lineBuilder = new StringBuilder(4096);

    /** Reusable char array mirroring lineBuilder, to flush without toString(). */
    private char[] flushBuffer = new char[4096];

    private static final String NEWLINE = System.lineSeparator();

    private static final int MEMORY_CHECK_INTERVAL = 256;
    private int lineCounter = 0;

    /**
     * Default constructor
     */
    public AlgoOccur() {
    }

    /**
     * Run the algorithm
     * @param inputFile    : a sequence database
     * @param patternFile  : a file of sequential patterns
     * @param outputFilePath : the path of the output file to save the result
     *                         or null if you want the result to be saved into memory
     * @throws IOException  exception if error while writing the file
     */
    public void runAlgorithm(String inputFile, String patternFile, String outputFilePath) throws IOException {
        MemoryLogger.getInstance().reset();
        startTime = System.currentTimeMillis();

        // Load the sequence database
        sequenceDatabase = new SequenceDatabase();
        sequenceDatabase.loadFile(inputFile);

        // Convert once to a raw array: removes ArrayList.get() overhead
        // from the hottest lookup path (done once per sequential search).
        final List<int[]> seqList = sequenceDatabase.getSequences();
        sequencesArray = seqList.toArray(new int[seqList.size()][]);

        if (outputFilePath == null) {
            writer = null;
        } else {
            writer = new BufferedWriter(new FileWriter(outputFilePath), 1 << 16);
    		writer.write("@FILETYPE=\"Frequent sequential patterns with occurrences\"");
    						writer.append(System.lineSeparator());
    						writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
    						writer.append(System.lineSeparator());
        }

        try {
            processPatterns(patternFile);
        } finally {
            endTime = System.currentTimeMillis();
            if (writer != null) {
                writer.close();
            }
        }
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Process all patterns of a file
     * @param patternFile input file
     * @throws IOException
     */
    private void processPatterns(String patternFile) throws IOException {
        String thisLine;

        try (BufferedReader myInput = new BufferedReader(
                new InputStreamReader(new FileInputStream(new File(patternFile))), 1 << 16)) {

            while ((thisLine = myInput.readLine()) != null) {
                if (thisLine.isEmpty()
                        || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                // Single manual pass: fills patternBuffer/patternLength,
                // sidBuffer/sidLength, and the text ranges to re-emit.
                parseLine(thisLine);

                lineBuilder.setLength(0);
                // Append pattern text and support text directly from the
                // original line - no substring allocated.
                lineBuilder.append(thisLine, 0, patternTextEnd);
                lineBuilder.append(' ');
                lineBuilder.append(thisLine, supStart, supEnd);
                lineBuilder.append(" #SIDOCC:");

                findOccurrences();

                lineBuilder.append(NEWLINE);
                flushLine();

                if (++lineCounter >= MEMORY_CHECK_INTERVAL) {
                    lineCounter = 0;
                    MemoryLogger.getInstance().checkMemory();
                }
            }
        }
    }

    /**
     * Parse one pattern-file line in a single pass, filling:
     *  - patternBuffer[0..patternLength) with the pattern's integer tokens
     *  - sidBuffer[0..sidLength) with the sid list
     *  - patternTextEnd / supStart / supEnd, the character ranges of the
     *    original line to re-emit verbatim in the output (no substrings).
     *
     * @param line the raw pattern-file line (already known non-empty/valid)
     */
    private void parseLine(final String line) {
        final int lineLen = line.length();

        // ---- Phase 1: parse pattern tokens up to the first '#' ----
        patternLength = 0;
        int i = 0;
        int currentValue = 0;
        boolean negative = false;
        boolean inToken = false;

        while (i < lineLen) {
            final char c = line.charAt(i);
            if (c == '#') {
                break;
            }
            if (c == '-' && !inToken) {
                negative = true;
                inToken = true;
            } else if (c >= '0' && c <= '9') {
                currentValue = currentValue * 10 + (c - '0');
                inToken = true;
            } else if (c == ' ' && inToken) {
                addPatternValue(negative ? -currentValue : currentValue);
                currentValue = 0;
                negative = false;
                inToken = false;
            }
            i++;
        }
        if (inToken) {
            addPatternValue(negative ? -currentValue : currentValue);
            currentValue = 0;
            negative = false;
        }

        final int posFirstCharacterSUP = i; // index of the first '#'
        patternTextEnd = posFirstCharacterSUP - 1; // exclude the space before '#'

        // ---- Phase 2: locate "#SID" (search only after the first '#') ----
        final int posFirstCharacterSIDLine = line.indexOf("#SID", posFirstCharacterSUP + 1);
        supStart = posFirstCharacterSUP;
        supEnd = posFirstCharacterSIDLine - 1;

        // ---- Phase 3: parse the sid list after "#SID: " (6 chars) ----
        sidLength = 0;
        i = posFirstCharacterSIDLine + 6;
        inToken = false;

        while (i < lineLen) {
            final char c = line.charAt(i);
            if (c == '-' && !inToken) {
                negative = true;
                inToken = true;
            } else if (c >= '0' && c <= '9') {
                currentValue = currentValue * 10 + (c - '0');
                inToken = true;
            } else if (c == ' ' && inToken) {
                addSidValue(negative ? -currentValue : currentValue);
                currentValue = 0;
                negative = false;
                inToken = false;
            }
            i++;
        }
        if (inToken) {
            addSidValue(negative ? -currentValue : currentValue);
        }
    }

    /** Append a value to the reusable pattern buffer, growing it if needed. */
    private void addPatternValue(final int value) {
        if (patternLength == patternBuffer.length) {
            patternBuffer = Arrays.copyOf(patternBuffer, patternBuffer.length * 2);
        }
        patternBuffer[patternLength++] = value;
    }

    /** Append a value to the reusable sid buffer, growing it if needed. */
    private void addSidValue(final int value) {
        if (sidLength == sidBuffer.length) {
            sidBuffer = Arrays.copyOf(sidBuffer, sidBuffer.length * 2);
        }
        sidBuffer[sidLength++] = value;
    }

    /**
     * Push the content currently accumulated in {@link #lineBuilder} to the
     * writer using a single call.
     *
     * @throws IOException
     */
    private void flushLine() throws IOException {
        if (writer == null) {
            return;
        }
        final int len = lineBuilder.length();
        if (flushBuffer.length < len) {
            flushBuffer = new char[Math.max(len, flushBuffer.length * 2)];
        }
        lineBuilder.getChars(0, len, flushBuffer, 0);
        writer.write(flushBuffer, 0, len);
    }

    /**
     * Find all occurrences of the pattern currently stored in
     * {@link #patternBuffer} for every sid currently stored in
     * {@link #sidBuffer}, appending the result text directly to
     * {@link #lineBuilder}.
     */
    private void findOccurrences() {
        int itemsetCount = 0;
        for (int idx = 0; idx < patternLength; idx++) {
            if (patternBuffer[idx] == -1) {
                itemsetCount++;
            }
        }
        if (occurrencePositions.length < itemsetCount) {
            occurrencePositions = new int[itemsetCount];
        }

        for (int s = 0; s < sidLength; s++) {
            final int sid = sidBuffer[s];
            final int[] sequence = sequencesArray[sid];

            lineBuilder.append(' ');
            lineBuilder.append(sid);

            firstOccurrenceForSid = true;
            findOccurrencesHelper(patternBuffer, patternLength, sequence, 0, 0, 0, 0);
        }
    }

    /**
     * Recursive function to match a pattern against a sequence and append
     * every occurrence found directly to {@link #lineBuilder}.
     *
     * @param pattern              the pattern buffer to match (may be oversized)
     * @param patLen               logical length of the pattern
     * @param sequence             the sequence to search in
     * @param posPattern           current position in the pattern array
     * @param posSequence          current position in the sequence array
     * @param posItemsetSequence   current itemset index within the sequence
     * @param depth                current depth in occurrencePositions
     */
    private void findOccurrencesHelper(
            final int[] pattern,
            final int patLen,
            final int[] sequence,
            final int posPattern,
            final int posSequence,
            final int posItemsetSequence,
            final int depth) {

        final int patternResetPosition = posPattern;
        int curPosPattern = posPattern;
        int curPosSequence = posSequence;
        int curPosItemsetSequence = posItemsetSequence;

        final int seqLen = sequence.length;

        do {
            final int seqVal = sequence[curPosSequence];
            final int patVal = pattern[curPosPattern];

            if (patVal == seqVal) {
                if (patVal == -1) {
                    occurrencePositions[depth] = curPosItemsetSequence;

                    if (curPosPattern == patLen - 1) {
                        writeOccurrence(depth);
                    } else {
                        findOccurrencesHelper(
                                pattern, patLen, sequence,
                                curPosPattern + 1,
                                curPosSequence + 1,
                                curPosItemsetSequence + 1,
                                depth + 1);
                    }

                    curPosItemsetSequence++;
                    curPosPattern = patternResetPosition;

                } else {
                    curPosPattern++;
                }
            } else if (seqVal == -1) {
                curPosPattern = patternResetPosition;
                curPosItemsetSequence++;
            }

            curPosSequence++;

        } while (curPosSequence < seqLen);
    }

    /**
     * Append a full occurrence (all itemset positions from depth 0 to
     * {@code depth}) directly to {@link #lineBuilder}: "[p0] [p1] ... [pk]",
     * with occurrences for the same sid separated by a single space.
     *
     * @param depth the deepest index (inclusive) in occurrencePositions to write
     */
    private void writeOccurrence(final int depth) {
        if (firstOccurrenceForSid) {
            firstOccurrenceForSid = false;
        } else {
            lineBuilder.append(' ');
        }
        lineBuilder.append('[');
        for (int i = 0; i <= depth; i++) {
            if (i > 0) {
                lineBuilder.append(' ');
            }
            lineBuilder.append(occurrencePositions[i]);
        }
        lineBuilder.append(']');
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStatistics() {
        StringBuilder r = new StringBuilder(200);
        r.append("=============  Occur 2.67 - STATISTICS =============\n Total time ~ ");
        r.append(endTime - startTime);
        r.append(" ms");
        r.append(System.lineSeparator());
        r.append(" Max memory (mb) : ");
        r.append(MemoryLogger.getInstance().getMaxMemory());
        r.append(System.lineSeparator());
        r.append("===================================================");
        r.append(System.lineSeparator());
        System.out.println(r.toString());
    }
}