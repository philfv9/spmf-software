package ca.pfv.spmf.algorithms.sequentialpatterns.occur;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.SequenceDatabase;
import ca.pfv.spmf.tools.MemoryLogger;


/*** 
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
 */

/**
 * This is an implementation of the OCCUR algorithm, a custom
 * algorithm to find occurrences of sequential patterns in a sequence database.
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

    /** 
     * Reusable list of occurrences to avoid allocating a new list per sequence.
     * This is cleared before each use inside findOccurrences.
     */
    private final List<String> reusableOccurrences = new ArrayList<>();

    /**
     * Reusable StringBuilder to build occurrence strings without allocating
     * new String objects on every recursive call.
     */
    private final StringBuilder occurrenceBuilder = new StringBuilder(64);

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
        // record start time
        startTime = System.currentTimeMillis();

        // Load the sequence database
        sequenceDatabase = new SequenceDatabase();
        sequenceDatabase.loadFile(inputFile);

        // if the user want to keep the result into memory
        if (outputFilePath == null) {
            writer = null;
        } else { // if the user want to save the result to a file
            writer = new BufferedWriter(new FileWriter(outputFilePath));
        }

        processPatterns(patternFile);

        // record end time
        endTime = System.currentTimeMillis();
        // close the output file if the result was saved to a file
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Process all patterns of a file
     * @param patternFile input file
     * @throws IOException
     */
    private void processPatterns(String patternFile) throws IOException {
        String thisLine; // variable to read each line.

        try (BufferedReader myInput = new BufferedReader(
                new InputStreamReader(new FileInputStream(new File(patternFile))))) {

            while ((thisLine = myInput.readLine()) != null) {
                // if the line is not a comment, is not empty or is not other
                // kind of metadata
                if (thisLine.isEmpty()
                        || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                // Find the position of the first '#' which starts the support text
                final int posFirstCharacterSUP = thisLine.indexOf('#');

                // Extract the sequence text (the pattern items before the support block)
                final String sequenceText = thisLine.substring(0, posFirstCharacterSUP - 1);

                // Find the position of "#SID" block
                final int posFirstCharacterSIDLine = thisLine.indexOf("#SID");

                // Extract the support text block (between '#' and '#SID')
                final String supText = thisLine.substring(posFirstCharacterSUP, posFirstCharacterSIDLine - 1);

                // THE SIDS as text - everything after "#SID: " (6 characters)
                final String sidListString = thisLine.substring(posFirstCharacterSIDLine + 6);

                // THE SIDS as an Integer array - parse manually to avoid regex overhead
                final int[] sids = parseIntArray(sidListString);

                // The sequential pattern as integers - parse manually to avoid regex overhead
                final int[] pattern = parseIntArray(sequenceText);

                // Write the pattern header
                writer.append(sequenceText);
                writer.append(' ');
                writer.append(supText);
                writer.append(" #SIDOCC:");

                // Find and write occurrences for this pattern
                findOccurrences(sids, pattern);

                writer.newLine();
            }
        }
    }

    /**
     * Parse a space-separated string of integers into an int array.
     * This avoids the overhead of String.split() and Integer.parseInt()
     * by doing a single-pass manual parse.
     *
     * @param str the string to parse
     * @return the parsed int array
     */
    private int[] parseIntArray(final String str) {
        // Count tokens first to allocate exact size
        int tokenCount = 0;
        boolean inToken = false;
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            final char c = str.charAt(i);
            if (c != ' ') {
                if (!inToken) {
                    tokenCount++;
                    inToken = true;
                }
            } else {
                inToken = false;
            }
        }

        final int[] result = new int[tokenCount];
        int idx = 0;
        int currentValue = 0;
        boolean negative = false;
        inToken = false;

        for (int i = 0; i < len; i++) {
            final char c = str.charAt(i);
            if (c == '-' && !inToken) {
                negative = true;
                inToken = true;
            } else if (c >= '0' && c <= '9') {
                currentValue = currentValue * 10 + (c - '0');
                inToken = true;
            } else if (c == ' ' && inToken) {
                result[idx++] = negative ? -currentValue : currentValue;
                currentValue = 0;
                negative = false;
                inToken = false;
            }
        }
        // last token
        if (inToken) {
            result[idx] = negative ? -currentValue : currentValue;
        }
        return result;
    }

    /**
     * Recursive function to find occurrences
     * @param sids    the list of sids
     * @param pattern the pattern
     * @throws IOException
     */
    private void findOccurrences(final int[] sids, final int[] pattern) throws IOException {
        final List<int[]> allSequences = sequenceDatabase.getSequences();

        for (final int sid : sids) {
            // Reuse the list to avoid repeated GC pressure
            reusableOccurrences.clear();

            final int[] sequence = allSequences.get(sid);

            // try to match the pattern with that sequence
            findOccurrencesHelper(pattern, sequence, 0, 0, 0, reusableOccurrences);

            // Write SID
            writer.append(' ');
            writer.append(Integer.toString(sid));

            // Write occurrences
            final int occSize = reusableOccurrences.size();
            for (int i = 0; i < occSize; i++) {
                writer.append('[');
                writer.append(reusableOccurrences.get(i));
                writer.append(']');
                if (i != occSize - 1) {
                    writer.append(' ');
                }
            }
        }
    }

    /**
     * Try to match an itemset of a sequence with a pattern.
     * Uses an iterative do-while loop for the inner matching, and
     * only recurses when moving to the next itemset in the pattern.
     *
     * @param pattern              the pattern to match
     * @param sequence             the sequence to search in
     * @param posPattern           current position in the pattern array
     * @param posSequence          current position in the sequence array
     * @param posItemsetSequence   current itemset index within the sequence
     * @param listOccurrences      accumulated list of occurrence strings
     */
    private void findOccurrencesHelper(
            final int[] pattern,
            final int[] sequence,
            final int posPattern,
            final int posSequence,
            final int posItemsetSequence,
            final List<String> listOccurrences) {

        final int patternResetPosition = posPattern;
        int curPosPattern = posPattern;
        int curPosSequence = posSequence;
        int curPosItemsetSequence = posItemsetSequence;

        final int seqLen = sequence.length;
        final int patLen = pattern.length;

        do {
            final int seqVal = sequence[curPosSequence];
            final int patVal = pattern[curPosPattern];

            if (patVal == seqVal) {
                // matched current pattern token (either item or -1 separator)
                if (patVal == -1) {
                    // We matched an itemset separator, so record the itemset position
                    // Build occurrence string using the shared builder to minimize allocations
                    occurrenceBuilder.setLength(0);
                    // We need to reconstruct the full occurrence path up to this point.
                    // Since we pass the occurrence as a parameter in the recursive call,
                    // we handle it via a local inline approach here:
                    // Note: the occurrence string tracking requires passing it along,
                    // so we keep the string-based approach but build it efficiently.
                    // (See note below about why we still pass a String.)
                    // We'll call a helper that rebuilds from scratch with the builder.
                    final String newOccurrence = buildOccurrence("", curPosItemsetSequence);

                    if (curPosPattern == patLen - 1) {
                        // last element of pattern -> record occurrence
                        listOccurrences.add(newOccurrence);
                    } else {
                        // recurse into next itemset of the pattern
                        findOccurrencesHelperWithOccurrence(
                                pattern, sequence,
                                curPosPattern + 1,
                                curPosSequence + 1,
                                newOccurrence,
                                curPosItemsetSequence + 1,
                                listOccurrences);
                    }

                    curPosItemsetSequence++;
                    curPosPattern = patternResetPosition;

                } else {
                    // matched a regular item, advance pattern
                    curPosPattern++;
                }
            } else if (seqVal == -1) {
                // end of sequence itemset but did not match -> reset pattern to itemset start
                curPosPattern = patternResetPosition;
                curPosItemsetSequence++;
            }

            curPosSequence++;

        } while (curPosSequence < seqLen);
    }

    /**
     * Continuation of findOccurrencesHelper for levels deeper than the first,
     * where a partial occurrence string has already been built.
     * This avoids passing an occurrence string at the top level and incurring
     * unnecessary string concatenations.
     *
     * @param pattern              the pattern to match
     * @param sequence             the sequence to search in
     * @param posPattern           current position in the pattern array
     * @param posSequence          current position in the sequence array
     * @param occurrence           occurrence string built so far
     * @param posItemsetSequence   current itemset index within the sequence
     * @param listOccurrences      accumulated list of occurrence strings
     */
    private void findOccurrencesHelperWithOccurrence(
            final int[] pattern,
            final int[] sequence,
            final int posPattern,
            final int posSequence,
            final String occurrence,
            final int posItemsetSequence,
            final List<String> listOccurrences) {

        final int patternResetPosition = posPattern;
        int curPosPattern = posPattern;
        int curPosSequence = posSequence;
        int curPosItemsetSequence = posItemsetSequence;

        final int seqLen = sequence.length;
        final int patLen = pattern.length;

        do {
            final int seqVal = sequence[curPosSequence];
            final int patVal = pattern[curPosPattern];

            if (patVal == seqVal) {
                if (patVal == -1) {
                    // Build new occurrence string efficiently
                    final String newOccurrence = buildOccurrence(occurrence, curPosItemsetSequence);

                    if (curPosPattern == patLen - 1) {
                        listOccurrences.add(newOccurrence);
                    } else {
                        findOccurrencesHelperWithOccurrence(
                                pattern, sequence,
                                curPosPattern + 1,
                                curPosSequence + 1,
                                newOccurrence,
                                curPosItemsetSequence + 1,
                                listOccurrences);
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
     * Build an occurrence string by appending the current itemset position
     * to the existing occurrence string. Uses the shared occurrenceBuilder
     * to minimize object allocation.
     *
     * @param existing           the existing occurrence string (may be empty)
     * @param itemsetPosition    the itemset position to append
     * @return the new occurrence string
     */
    private String buildOccurrence(final String existing, final int itemsetPosition) {
        occurrenceBuilder.setLength(0);
        if (existing.length() == 0) {
            occurrenceBuilder.append(itemsetPosition);
        } else {
            occurrenceBuilder.append(existing);
            occurrenceBuilder.append(' ');
            occurrenceBuilder.append(itemsetPosition);
        }
        return occurrenceBuilder.toString();
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     * @param size  the size of the database
     */
    public void printStatistics() {
        StringBuilder r = new StringBuilder(200);
        r.append("=============  Occur 2.66 - STATISTICS =============\n Total time ~ ");
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
