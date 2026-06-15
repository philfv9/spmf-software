package ca.pfv.spmf.gui.viewers.fastaviewer;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/* Copyright (c) 2008-2024 Philippe Fournier-Viger
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
* 
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * Implementation of a FASTA dataset with methods for reading, analyzing,
 * and exporting FASTA files. Provides comprehensive statistics and validation.
 * 
 * @author Philippe Fournier-Viger
 */
public class FastaDataset {
    
    /** The list of sequence entries in this dataset */
    private List<FastaSequenceEntry> sequences;
    
    /** Regular expression pattern for validating sequence symbols */
    private static final Pattern VALID_SEQUENCE_PATTERN = Pattern.compile("[A-Za-z*\\-]*");
    
    /** Character indicating the start of a header line */
    private static final char HEADER_PREFIX = '>';
    
    /** Character indicating a comment line */
    private static final char COMMENT_PREFIX = ';';
    
    /** Character to be removed from sequences (stop codon marker) */
    private static final char STOP_CODON_MARKER = '*';

    /**
     * Constructs a new empty FASTA dataset.
     */
    public FastaDataset() {
        this.sequences = new ArrayList<>();
    }

    /**
     * Loads and parses a FASTA file from the specified path.
     * 
     * @param filePath the path to the FASTA file
     * @return the list of sequences loaded from the file
     * @throws IOException if an error occurs while reading the file
     * @throws FileNotFoundException if the file does not exist or cannot be read
     * @throws IllegalArgumentException if the file contains invalid sequence characters
     */
    public List<FastaSequenceEntry> loadFile(String filePath) throws IOException {
        validateFile(filePath);
        sequences.clear();
        
        StringBuilder sequenceBuilder = new StringBuilder();
        String header = null;
        int lineNumber = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (isEmptyOrComment(line)) {
                    continue;
                }

                if (isHeaderLine(line)) {
                    if (header != null) {
                        addSequence(header, sequenceBuilder.toString());
                        sequenceBuilder = new StringBuilder();
                    }
                    header = parseHeader(line);
                } else {
                    validateSequenceLine(line, lineNumber);
                    sequenceBuilder.append(removeStopCodons(line));
                }
            }
            
            if (header != null) {
                addSequence(header, sequenceBuilder.toString());
            }
        }
        
        return sequences;
    }

    /**
     * Validates that the specified file exists and can be read.
     * 
     * @param filePath the path to the file to validate
     * @throws FileNotFoundException if the file does not exist or cannot be read
     */
    private void validateFile(String filePath) throws FileNotFoundException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("File does not exist: " + filePath);
        }
        if (!file.canRead()) {
            throw new FileNotFoundException("File cannot be read: " + filePath);
        }
        if (file.length() == 0) {
            throw new FileNotFoundException("File is empty: " + filePath);
        }
    }

    /**
     * Checks if a line is empty or a comment line.
     * 
     * @param line the line to check
     * @return true if the line is empty or starts with the comment prefix
     */
    private boolean isEmptyOrComment(String line) {
        return line.isEmpty() || line.charAt(0) == COMMENT_PREFIX;
    }

    /**
     * Checks if a line is a header line.
     * 
     * @param line the line to check
     * @return true if the line starts with the header prefix
     */
    private boolean isHeaderLine(String line) {
        return line.charAt(0) == HEADER_PREFIX;
    }

    /**
     * Parses the header from a header line.
     * 
     * @param line the header line
     * @return the parsed header (first word after the '>' character)
     */
    private String parseHeader(String line) {
        String headerContent = line.substring(1).trim();
        if (headerContent.isEmpty()) {
            return "Unknown";
        }
        String[] parts = headerContent.split("\\s+");
        return parts[0];
    }

    /**
     * Validates that a sequence line contains only valid characters.
     * 
     * @param line the sequence line to validate
     * @param lineNumber the line number for error reporting
     * @throws IllegalArgumentException if the line contains invalid characters
     */
    private void validateSequenceLine(String line, int lineNumber) {
        if (!VALID_SEQUENCE_PATTERN.matcher(line).matches()) {
            throw new IllegalArgumentException(
                "Invalid sequence characters at line " + lineNumber + ": " + line
            );
        }
    }

    /**
     * Removes stop codon markers from a sequence line.
     * 
     * @param line the sequence line
     * @return the line with stop codon markers removed
     */
    private String removeStopCodons(String line) {
        return line.replace(String.valueOf(STOP_CODON_MARKER), "");
    }

    /**
     * Adds a sequence entry to the dataset.
     * 
     * @param header the sequence header
     * @param sequence the sequence data
     */
    private void addSequence(String header, String sequence) {
        if (!sequence.isEmpty()) {
            sequences.add(new FastaSequenceEntry(header, sequence));
        }
    }

    /**
     * Exports sequences to a file.
     * 
     * @param filePath the output file path
     * @param sequencesToExport the sequences to export
     * @throws IOException if an error occurs while writing
     */
    public void exportToFile(String filePath, List<FastaSequenceEntry> sequencesToExport) 
            throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (FastaSequenceEntry entry : sequencesToExport) {
                writer.write(">");
                writer.write(entry.getHeader());
                writer.newLine();
                
                String sequence = entry.getSequence();
                int lineLength = 80;
                for (int i = 0; i < sequence.length(); i += lineLength) {
                    int end = Math.min(i + lineLength, sequence.length());
                    writer.write(sequence.substring(i, end));
                    writer.newLine();
                }
                writer.newLine();
            }
        }
    }

    /**
     * Computes and prints statistics about the sequences.
     */
    public void computeAndPrintStatistics() {
        if (sequences.isEmpty()) {
            System.out.println("No sequences loaded.");
            return;
        }

        Map<Character, Integer> frequencyMap = new HashMap<>();
        int totalLength = 0;
        int minLength = Integer.MAX_VALUE;
        int maxLength = 0;

        for (FastaSequenceEntry entry : sequences) {
            String sequence = entry.getSequence();
            int length = sequence.length();
            
            totalLength += length;
            minLength = Math.min(minLength, length);
            maxLength = Math.max(maxLength, length);

            for (char nucleotide : sequence.toCharArray()) {
                frequencyMap.merge(nucleotide, 1, Integer::sum);
            }
        }

        printStatistics(frequencyMap, totalLength, minLength, maxLength);
    }

    /**
     * Prints formatted statistics to the console.
     * 
     * @param frequencyMap map of characters to their frequencies
     * @param totalLength total length of all sequences
     * @param minLength minimum sequence length
     * @param maxLength maximum sequence length
     */
    private void printStatistics(Map<Character, Integer> frequencyMap, 
                                  int totalLength, int minLength, int maxLength) {
        System.out.println("========================");
        System.out.println("Letter  \t Count \t Percentage");
        System.out.println("========================");
        
        frequencyMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                double percentage = (double) entry.getValue() / totalLength * 100;
                System.out.printf("%c\t\t%d\t%.2f%%%n", 
                    entry.getKey(), entry.getValue(), percentage);
            });
        
        System.out.println("========================");
        
        double averageLength = (double) totalLength / sequences.size();
        System.out.printf("Total sequences: %d%n", sequences.size());
        System.out.printf("Total bases: %,d%n", totalLength);
        System.out.printf("Average length: %.2f%n", averageLength);
        System.out.printf("Minimum length: %d%n", minLength);
        System.out.printf("Maximum length: %d%n", maxLength);
        System.out.println("========================");
    }

    /**
     * Gets nucleotide composition statistics.
     * 
     * @return map of nucleotides to their counts
     */
    public Map<Character, Integer> getNucleotideComposition() {
        Map<Character, Integer> composition = new HashMap<>();
        for (FastaSequenceEntry entry : sequences) {
            for (char c : entry.getSequence().toUpperCase().toCharArray()) {
                composition.merge(c, 1, Integer::sum);
            }
        }
        return composition;
    }

    /**
     * Gets length statistics.
     * 
     * @return array with [min, max, avg, total]
     */
    public double[] getLengthStatistics() {
        if (sequences.isEmpty()) {
            return new double[]{0, 0, 0, 0};
        }
        
        int min = Integer.MAX_VALUE;
        int max = 0;
        int total = 0;
        
        for (FastaSequenceEntry entry : sequences) {
            int len = entry.getLength();
            min = Math.min(min, len);
            max = Math.max(max, len);
            total += len;
        }
        
        double avg = (double) total / sequences.size();
        return new double[]{min, max, avg, total};
    }

    /**
     * Gets the list of sequence entries.
     * 
     * @return unmodifiable view of sequence entries
     */
    public List<FastaSequenceEntry> getSequenceEntries() {
        return new ArrayList<>(sequences);
    }

    /**
     * Gets the number of sequences.
     * 
     * @return the number of sequences
     */
    public int getSequenceCount() {
        return sequences.size();
    }

    /**
     * Checks if this dataset is empty.
     * 
     * @return true if there are no sequences
     */
    public boolean isEmpty() {
        return sequences.isEmpty();
    }

    /**
     * Gets a sequence by index.
     * 
     * @param index the index
     * @return the sequence entry
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public FastaSequenceEntry getSequence(int index) {
        return sequences.get(index);
    }

    /**
     * Finds sequences by header pattern.
     * 
     * @param pattern the pattern to search for
     * @return list of matching sequences
     */
    public List<FastaSequenceEntry> findByHeader(String pattern) {
        List<FastaSequenceEntry> results = new ArrayList<>();
        String lowerPattern = pattern.toLowerCase();
        for (FastaSequenceEntry entry : sequences) {
            if (entry.getHeader().toLowerCase().contains(lowerPattern)) {
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * Filters sequences by length range.
     * 
     * @param minLength minimum length (inclusive)
     * @param maxLength maximum length (inclusive)
     * @return list of sequences within the length range
     */
    public List<FastaSequenceEntry> filterByLength(int minLength, int maxLength) {
        List<FastaSequenceEntry> results = new ArrayList<>();
        for (FastaSequenceEntry entry : sequences) {
            int len = entry.getLength();
            if (len >= minLength && len <= maxLength) {
                results.add(entry);
            }
        }
        return results;
    }
}