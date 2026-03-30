package ca.pfv.spmf.tools.other_dataset_tools.fix_tdb_utility_time;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This tool allows to fix some common problems in a transaction database file
 * with utility and timestamps in SPMF format. In particular: (1) the tool
 * removes items that appears more than once in a transaction. (2) it sort
 * transactions according to the lexicographical ordering. The reason for
 * performing this is that many itemset and association rule mining algorithms
 * assumes that items cannot appear more than once in a transaction and that
 * transactions are sorted.
 * 
 * @author Philippe Fournier-Viger, 2020
 */
public class AlgoFixTDBTimeUtility {

	/** the time at which the algorithm started */
	public long startTimestamp = 0;

	/** the time at which the algorithm ended */
	public long endTimestamp = 0;

	/** this class represent an item and its utility in a transaction */
	class Pair {
		int item = 0;
		long utility = 0;
	}

	/**
	 * Default constructor
	 */
	public AlgoFixTDBTimeUtility() {
	}

	/**
	 * Run the algorithm
	 * 
	 * @param input  the input file path
	 * @param output the output file path
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output) throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();

		startTimestamp = System.currentTimeMillis();

		/** writer to write the output file */
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));

		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is empty we skip it
				if (thisLine.isEmpty() == true) {
					continue;
					// if the line is some kind of metadata we just write the line as it is
				} else if (thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					writer.write(thisLine + " ");
					writer.newLine();
					continue;
				}

				// split the line according to the separator
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" ");

				// Copy the transaction into lists but
				// without items with TWU < minutility
				long totalUtility = 0;

				Map<Integer, Pair> mapItemToPairAlreadySeen = new HashMap<Integer, Pair>();

				// Create a list to store items
				List<Pair> revisedTransaction = new ArrayList<Pair>();
				// for each item
				for (int i = 0; i < items.length; i++) {
					int item = Integer.parseInt(items[i]);
					int utility = Integer.parseInt(utilityValues[i]);

					// check if we saw that item already
					Pair pair = mapItemToPairAlreadySeen.get(item);
					if (pair == null) {
						// / convert values to integers
						pair = new Pair();
						pair.item = item;
						pair.utility = utility;
						revisedTransaction.add(pair);
						mapItemToPairAlreadySeen.put(item, pair);
					} else {
						pair.utility += utility;
					}
					totalUtility += utility;
				}

				Collections.sort(revisedTransaction, new Comparator<Pair>() {
					public int compare(Pair o1, Pair o2) {
						return o1.item - o2.item;
					}
				});

				// Write each item
				for (int i = 0; i < revisedTransaction.size(); i++) {
					Pair pair = revisedTransaction.get(i);
					writer.write(String.valueOf(pair.item));
					if (i != revisedTransaction.size() - 1) {
						writer.write(" ");
					}
				}
				// Write total utility
				writer.write(":" + totalUtility + ":");
				// Write each utility value
				for (int i = 0; i < revisedTransaction.size(); i++) {
					Pair pair = revisedTransaction.get(i);
					writer.write(String.valueOf(pair.utility));
					if (i != revisedTransaction.size() - 1) {
						writer.write(" ");
					}
				}
				// write the time
				if (split.length == 4) {
					long time = Long.parseLong(split[3]);
					writer.write(":" + time);
				}
				writer.newLine();
			}
		} catch (Exception e) {
			// to catch error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		// close output file
		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("========  FIX TransactionDB tool (with Utility/time) - STATS =======");
		System.out.println(" Runtime ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println("=====================================================================");
	}
}