package ca.pfv.spmf.test;

/* This file is copyright (c) 2024
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
*/

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.newcharm.AlgoNewCharm_Bitset;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;

/**
 * Example of how to use the NEWCHARM algorithm from the source code and save
 * the result to a file.
 * 
 * NEWCHARM is an optimization of CHARM that doesn't require a hash table,
 * making it more memory-efficient.
 */
public class MainTestNewCharm_Bitset_saveToFile {

    public static void main(String[] args) throws IOException {

        // Load a transaction database
        String input = fileToPath("contextPasquier99.txt");
        
        // The output file path
        String output = ".//output.txt";

        // The minimum support threshold
        double minsup = 0.4; // 40%

        // Load the transaction database
        TransactionDatabase database = new TransactionDatabase();
        try {
            database.loadFile(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Optional: print the database to console
        // database.printDatabase();

        // Create the algorithm object
        AlgoNewCharm_Bitset algo = new AlgoNewCharm_Bitset();
        
        // Optional: Set this to true to show transaction IDs in output
        // algo.setShowTransactionIdentifiers(true);

        // Run the algorithm
        // Parameters: output file, database, minsup, use triangular matrix optimization
        algo.runAlgorithm(output, database, minsup, true);

        // Print statistics about the algorithm execution
        algo.printStats();
    }

    public static String fileToPath(String filename)
            throws UnsupportedEncodingException {
        URL url = MainTestNewCharm_Bitset_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}