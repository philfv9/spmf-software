package ca.pfv.spmf.experimental.bioinformatics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Implementation of a Fasta dataset with method for reading FASTA files.
* 
 * @author Philipe-Fournier-Viger
 **/
public class FastaDataset {
	/** The different sequences of the dataset */
    private List<FastaSequenceEntry> sequences = new ArrayList<>();
    
    /** regular expression patterns for matching valid sequence symbols */
    Pattern validSequencePattern = Pattern.compile("[A-Za-z*]*");

    /**
     * Constructor
     */
    public FastaDataset() {
    }

    /**
     * Method for reading a file in memory
     * @param filePath the file path
     * @return the list of sequences that has been read until now
     * @throws IOException if error reading the file
     */
    public List<FastaSequenceEntry> loadFile(String filePath) throws IOException {
        StringBuilder sequenceBuilder = new StringBuilder();
        String header = null;

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            throw new FileNotFoundException("File does not exist or cannot be read");
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(";")) continue; // Skip empty lines and comments

                if (line.startsWith(">")) {
                    if (header != null) {
                        sequences.add(new FastaSequenceEntry(header, sequenceBuilder.toString()));
                        sequenceBuilder = new StringBuilder();
                    }
                    header = line.substring(1).split("\\s+")[0]; // Get the first word of the header
                } else if (!validSequencePattern.matcher(line).matches()) {
                    throw new IllegalArgumentException("Invalid sequence characters detected");
                }else {
                    sequenceBuilder.append(line.replace("*", "")); // Remove asterisks as you read each line
                }
            }
            if (header != null) {
                sequences.add(new FastaSequenceEntry(header, sequenceBuilder.toString()));
            }
        }
        return sequences;
    }
    

    /**
     * Compute the average, minimum and maximum sequence lengths, and the frequency of each letter and display it in the console
     */
    public void computeAndPrintStatistics() {
        Map<Character, Integer> frequencyMap = new HashMap<>();
        int totalLength = 0;
        int minLength = Integer.MAX_VALUE;
        int maxLength = 0;

        System.out.println("========================");
        System.out.println("Letter  \t Count");
        System.out.println("========================");
        for (FastaSequenceEntry entry : sequences) {
            String sequence = entry.getSequence();
            totalLength += sequence.length();
            minLength = Math.min(minLength, sequence.length());
            maxLength = Math.max(maxLength, sequence.length());

            for (char nucleotide : sequence.toCharArray()) {
                frequencyMap.put(nucleotide, frequencyMap.getOrDefault(nucleotide, 0) + 1);
            }
        }

        // Print the frequency of each element
        frequencyMap.forEach((key, value) -> System.out.println(key + "\t" + value));
        System.out.println("========================");

        double averageLength = sequences.isEmpty() ? 0 : (double) totalLength / sequences.size();
        System.out.println("Average sequence length: " + averageLength);
        System.out.println("Minimum sequence length: " + minLength);
        System.out.println("Maximum sequence length: " + maxLength);

        System.out.println("========================");
    }

    /**
     * Get the sequence entries from this datset
     * @return the sequence entries
     */
    public List<FastaSequenceEntry> getSequenceEntries() {
        return sequences;
    }


}

