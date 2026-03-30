package ca.pfv.spmf.tools.other_dataset_tools;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import ca.pfv.spmf.test.MainTestSampleRecords;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This algorithm samples records (lines) from a file. It can perform
 * sampling by specifying either a fixed number of records or a percentage
 * of the total records. The sampling can be done with or without replacement.
 * Metadata lines (starting with #, %, @ or empty lines) are preserved
 * and copied to the output file.
 * 
 * @author Philippe Fournier-Viger
 * @see MainTestSampleRecords
 */
public class AlgoSampleRecords {

    /** the number of data records in the input file */
    private int recordCountInput = 0;
    
    /** the number of data records in the output file */
    private int recordCountOutput = 0;
    
    /** the number of metadata lines */
    private int metadataLineCount = 0;
    
    /** the time the algorithm started */
    private long startTimestamp = 0;
    
    /** the time the algorithm ended */
    private long endTimestamp = 0;
    
    /** the maximum memory usage */
    private double maxMemory = 0;
    
    /** random number generator */
    private Random random;

    /**
     * Default constructor
     */
    public AlgoSampleRecords() {
        this.random = new Random(System.currentTimeMillis());
    }
    
    /**
     * Constructor with seed for reproducible results.
     * @param seed the random seed
     */
    public AlgoSampleRecords(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Run the algorithm to sample a fixed number of records from a file.
     * Metadata lines are preserved in the output.
     * 
     * @param inputFile       the path to the input file
     * @param outputFile      the path to the output file
     * @param sampleCount     the number of records to sample
     * @param withReplacement if true, sampling is done with replacement
     * @throws IOException if an I/O error occurs
     */
    public void runAlgorithm(String inputFile, String outputFile, 
            int sampleCount, boolean withReplacement) throws IOException {
        
        // Reset statistics
        recordCountInput = 0;
        recordCountOutput = 0;
        metadataLineCount = 0;
        maxMemory = 0;
        MemoryLogger.getInstance().reset();
        
        // Record start time
        startTimestamp = System.currentTimeMillis();
        
        // Lists to store metadata and data records separately
        List<String> metadataLines = new ArrayList<String>();
        List<String> dataRecords = new ArrayList<String>();
        
        // First pass: read all records and separate metadata from data
        readAndSeparateRecords(inputFile, metadataLines, dataRecords);
        recordCountInput = dataRecords.size();
        
        // Validate sample count
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                "Sample count cannot be negative: " + sampleCount);
        }
        
        if (!withReplacement && sampleCount > recordCountInput) {
            throw new IllegalArgumentException(
                "Sample count (" + sampleCount + ") cannot be greater than " +
                "the number of data records (" + recordCountInput + ") when sampling " +
                "without replacement.");
        }
        
        // Perform sampling on data records only
        List<String> sampledRecords;
        if (withReplacement) {
            sampledRecords = sampleWithReplacement(dataRecords, sampleCount);
        } else {
            sampledRecords = sampleWithoutReplacement(dataRecords, sampleCount);
        }
        
        // Write output: metadata first, then sampled records
        writeOutput(outputFile, metadataLines, sampledRecords);
        recordCountOutput = sampledRecords.size();
        
        // Check memory one final time
        maxMemory = MemoryLogger.getInstance().checkMemory();
        
        // Record end time
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Run the algorithm to sample a percentage of records from a file.
     * Metadata lines are preserved in the output.
     * 
     * @param inputFile       the path to the input file
     * @param outputFile      the path to the output file
     * @param percentage      the percentage of records to sample (0.0 to 1.0)
     * @param withReplacement if true, sampling is done with replacement
     * @throws IOException if an I/O error occurs
     */
    public void runAlgorithmPercentage(String inputFile, String outputFile, 
            double percentage, boolean withReplacement) throws IOException {
        
        if (percentage < 0.0 || percentage > 1.0) {
            throw new IllegalArgumentException(
                "Percentage must be between 0.0 and 1.0, got: " + percentage);
        }
        
        // Reset statistics
        recordCountInput = 0;
        recordCountOutput = 0;
        metadataLineCount = 0;
        maxMemory = 0;
        MemoryLogger.getInstance().reset();
        
        // Record start time
        startTimestamp = System.currentTimeMillis();
        
        // Lists to store metadata and data records separately
        List<String> metadataLines = new ArrayList<String>();
        List<String> dataRecords = new ArrayList<String>();
        
        // First pass: read all records and separate metadata from data
        readAndSeparateRecords(inputFile, metadataLines, dataRecords);
        recordCountInput = dataRecords.size();
        
        // Calculate sample count based on percentage
        int sampleCount = (int) Math.round(recordCountInput * percentage);
        
        // Adjust sample count if it exceeds available records (for no-replacement case)
        if (!withReplacement && sampleCount > recordCountInput) {
            sampleCount = recordCountInput;
        }
        
        // Perform sampling on data records only
        List<String> sampledRecords;
        if (withReplacement) {
            sampledRecords = sampleWithReplacement(dataRecords, sampleCount);
        } else {
            sampledRecords = sampleWithoutReplacement(dataRecords, sampleCount);
        }
        
        // Write output: metadata first, then sampled records
        writeOutput(outputFile, metadataLines, sampledRecords);
        recordCountOutput = sampledRecords.size();
        
        // Check memory one final time
        maxMemory = MemoryLogger.getInstance().checkMemory();
        
        // Record end time
        endTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Run the algorithm using reservoir sampling for large files.
     * This method is memory efficient as it doesn't require loading
     * all records into memory at once (except for metadata lines).
     * Metadata lines are preserved in the output.
     * 
     * @param inputFile   the path to the input file
     * @param outputFile  the path to the output file
     * @param sampleCount the number of records to sample
     * @throws IOException if an I/O error occurs
     */
    public void runAlgorithmReservoir(String inputFile, String outputFile, 
            int sampleCount) throws IOException {
        
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                "Sample count cannot be negative: " + sampleCount);
        }
        
        // Reset statistics
        recordCountInput = 0;
        recordCountOutput = 0;
        metadataLineCount = 0;
        maxMemory = 0;
        MemoryLogger.getInstance().reset();
        
        // Record start time
        startTimestamp = System.currentTimeMillis();
        
        // List to store metadata lines (must be preserved)
        List<String> metadataLines = new ArrayList<String>();
        
        // Reservoir to hold the sample
        String[] reservoir = new String[sampleCount];
        
        // Open the input file
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(inputFile)), StandardCharsets.UTF_8))) {
            
            String line;
            int dataRecordIndex = 0;
            
            // Read each line from the input file
            while ((line = reader.readLine()) != null) {
                
                // Check if this is a metadata line
                if (isMetadataLine(line)) {
                    metadataLines.add(line);
                    metadataLineCount++;
                    continue;
                }
                
                // This is a data record
                recordCountInput++;
                
                if (dataRecordIndex < sampleCount) {
                    // Fill the reservoir initially
                    reservoir[dataRecordIndex] = line;
                } else {
                    // Reservoir sampling algorithm
                    int j = random.nextInt(dataRecordIndex + 1);
                    if (j < sampleCount) {
                        reservoir[j] = line;
                    }
                }
                
                dataRecordIndex++;
                
                // Check memory periodically
                if (dataRecordIndex % 10000 == 0) {
                    MemoryLogger.getInstance().checkMemory();
                }
            }
        }
        
        // Handle case where file has fewer records than sample count
        int actualSampleSize = Math.min(sampleCount, recordCountInput);
        
        // Convert reservoir to list for writing
        List<String> sampledRecords = new ArrayList<String>(actualSampleSize);
        for (int i = 0; i < actualSampleSize; i++) {
            sampledRecords.add(reservoir[i]);
        }
        
        // Write output: metadata first, then sampled records
        writeOutput(outputFile, metadataLines, sampledRecords);
        recordCountOutput = sampledRecords.size();
        
        // Check memory one final time
        maxMemory = MemoryLogger.getInstance().checkMemory();
        
        // Record end time
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Read all records from a file and separate metadata from data records.
     * 
     * @param inputFile     the path to the input file
     * @param metadataLines list to store metadata lines
     * @param dataRecords   list to store data records
     * @throws IOException if an I/O error occurs
     */
    private void readAndSeparateRecords(String inputFile, 
            List<String> metadataLines, List<String> dataRecords) throws IOException {
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(inputFile)), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                
                if (isMetadataLine(line)) {
                    metadataLines.add(line);
                    metadataLineCount++;
                } else {
                    dataRecords.add(line);
                }
                
                // Check memory periodically
                if ((metadataLineCount + dataRecords.size()) % 10000 == 0) {
                     MemoryLogger.getInstance().checkMemory();
                }
            }
        }
    }

    /**
     * Check if a line is a metadata line.
     * A metadata line is empty or starts with #, %, or @.
     * 
     * @param line the line to check
     * @return true if it's a metadata line, false otherwise
     */
    private boolean isMetadataLine(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.charAt(0) == '#' 
                || trimmed.charAt(0) == '%' || trimmed.charAt(0) == '@';
    }

    /**
     * Sample records with replacement.
     * 
     * @param allRecords  the list of all data records
     * @param sampleCount the number of records to sample
     * @return the sampled records
     */
    private List<String> sampleWithReplacement(List<String> allRecords, 
            int sampleCount) {
        
        if (allRecords.isEmpty() || sampleCount == 0) {
            return new ArrayList<String>();
        }
        
        List<String> sampled = new ArrayList<String>(sampleCount);
        int totalRecords = allRecords.size();
        
        for (int i = 0; i < sampleCount; i++) {
            int randomIndex = random.nextInt(totalRecords);
            sampled.add(allRecords.get(randomIndex));
        }
        
        return sampled;
    }

    /**
     * Sample records without replacement using Fisher-Yates shuffle.
     * 
     * @param allRecords  the list of all data records
     * @param sampleCount the number of records to sample
     * @return the sampled records
     */
    private List<String> sampleWithoutReplacement(List<String> allRecords, 
            int sampleCount) {
        
        if (allRecords.isEmpty() || sampleCount == 0) {
            return new ArrayList<String>();
        }
        
        // Create a copy to avoid modifying the original list
        List<String> recordsCopy = new ArrayList<String>(allRecords);
        
        // Partial Fisher-Yates shuffle
        for (int i = 0; i < sampleCount; i++) {
            int j = i + random.nextInt(recordsCopy.size() - i);
            // Swap elements
            Collections.swap(recordsCopy, i, j);
        }
        
        // Return the first sampleCount elements
        return new ArrayList<String>(recordsCopy.subList(0, sampleCount));
    }

    /**
     * Write metadata and sampled records to an output file.
     * Metadata lines are written first, then the sampled data records.
     * 
     * @param outputFile     the path to the output file
     * @param metadataLines  the metadata lines to write
     * @param sampledRecords the sampled data records to write
     * @throws IOException if an I/O error occurs
     */
    private void writeOutput(String outputFile, List<String> metadataLines,
            List<String> sampledRecords) throws IOException {
        
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            
            boolean isFirstLineWritten = false;
            
            // Write metadata lines first
            for (String metadataLine : metadataLines) {
                if (isFirstLineWritten) {
                    writer.newLine();
                }
                writer.write(metadataLine);
                isFirstLineWritten = true;
            }
            
            // Write sampled data records
            for (String record : sampledRecords) {
                if (isFirstLineWritten) {
                    writer.newLine();
                }
                writer.write(record);
                isFirstLineWritten = true;
            }
        }
    }

    /**
     * Print statistics about the last algorithm execution.
     */
    public void printStats() {
        System.out.println("=============  SAMPLE RECORDS TOOL v 2.65 - STATS =============");
        System.out.println(" Metadata lines preserved: " + metadataLineCount);
        System.out.println(" Input data records count: " + recordCountInput);
        System.out.println(" Output data records count: " + recordCountOutput);
        
        double ratio = recordCountInput == 0 ? 0.0
                : (recordCountOutput * 100.0 / recordCountInput);
        
        System.out.println(" Sampling ratio: " + 
                String.format("%.2f", ratio) + "%");
        System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Max memory: " + String.format("%.2f", maxMemory) + " MB");
        System.out.println("===================================================");
    }

    /**
     * Get the number of data records in the input file.
     * @return the record count
     */
    public int getRecordCountInput() {
        return recordCountInput;
    }

    /**
     * Get the number of data records in the output file.
     * @return the record count
     */
    public int getRecordCountOutput() {
        return recordCountOutput;
    }
    
    /**
     * Get the number of metadata lines preserved.
     * @return the metadata line count
     */
    public int getMetadataLineCount() {
        return metadataLineCount;
    }
    
    /**
     * Set a new random seed.
     * @param seed the new seed
     */
    public void setSeed(long seed) {
        this.random = new Random(seed);
    }
}