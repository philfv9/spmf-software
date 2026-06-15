package ca.pfv.spmf.algorithms.frequentpatterns.uhmine;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger

 This file is part of the SPMF DATA MINING SOFTWARE
 (http://www.philippe-fournier-viger.com/spmf).
 SPMF is free software: you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation, either version 3 of the License, or (at your option) any later
 version.
 SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 SPMF. If not, see http://www.gnu.org/licenses/.
 Do not remove copyright or license information.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * An uncertain transaction database for use with the UH-Mine algorithm.
 * Each transaction is stored as a list of ItemUHMine objects, each carrying
 * an item id and its existential probability.
 * The file format is the same as UApriori: tokens of the form itemID(probability).
 *
 * @see AlgoUHMine
 * @see ItemUHMine
 * @author Philippe Fournier-Viger, 2015
 */
public class UncertainTransactionDatabaseUHMine {

    /** the list of transactions */
    private final List<List<ItemUHMine>> transactions = new ArrayList<List<ItemUHMine>>();

    /**
     * Load the database from a file.
     *
     * @param path the path to the input file
     * @throws IOException if an error occurs while reading the file
     */
    public void loadFile(String path) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(path))));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#'
                        || line.charAt(0) == '%' || line.charAt(0) == '@') {
                    continue;
                }
                processTransaction(line.split(" "));
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Parse one transaction line and store it in the database.
     *
     * @param tokens the tokens of one line, each in the form itemID(probability)
     */
    private void processTransaction(String[] tokens) {
        List<ItemUHMine> transaction = new ArrayList<ItemUHMine>();
        for (String token : tokens) {
            int lp = token.indexOf('(');
            int rp = token.indexOf(')');
            int itemId = Integer.parseInt(token.substring(0, lp));
            double prob = Double.parseDouble(token.substring(lp + 1, rp));
            transaction.add(new ItemUHMine(itemId, prob));
        }
        if (!transaction.isEmpty()) {
            transactions.add(transaction);
        }
    }

    /**
     * Get the list of transactions.
     *
     * @return the list of transactions
     */
    public List<List<ItemUHMine>> getTransactions() {
        return transactions;
    }

    /**
     * Get the number of transactions in the database.
     *
     * @return the transaction count
     */
    public int size() {
        return transactions.size();
    }

    /**
     * Print the database to System.out.
     */
    public void printDatabase() {
        System.out.println("=== UNCERTAIN DATABASE (UHMine) ===");
        int count = 0;
        for (List<ItemUHMine> transaction : transactions) {
            System.out.print("T" + count + ": ");
            for (ItemUHMine item : transaction) {
                System.out.print(item + " ");
            }
            System.out.println();
            count++;
        }
        System.out.println("===================================");
    }
}