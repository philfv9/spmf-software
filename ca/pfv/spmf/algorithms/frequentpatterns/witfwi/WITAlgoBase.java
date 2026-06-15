package ca.pfv.spmf.algorithms.frequentpatterns.witfwi;

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
*
* Do not remove copyright or license information from this file.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Abstract base class shared by all WIT-FWI algorithm variants.
 *
 * Encapsulates the infrastructure that is identical across WIT-FWI,
 * WIT-FWI-MODIFY, and WIT-FWI-DIFF: common static fields, methods for loading
 * files, output formatting, and tidset-based support computation. Each concrete
 * subclass supplies its own runAlgorithm() entry point and recursive extend
 * procedure, calling the protected helpers here.
 *
 * @see AlgoWIT_FWI
 * @see AlgoWIT_FWI_MOD
 * @see AlgoWIT_FWI_DIFF
 * @author Philippe Fournier-Viger
 */
abstract class WITAlgoBase {

	/** the time at which the algorithm started */
	public long startTimestamp = 0;

	/** the time at which the algorithm ended */
	public long endTimestamp = 0;

	/** the number of frequent weighted itemsets found */
	public int fwiCount = 0;

	/** the number of candidate itemsets generated */
	public int candidateCount = 0;

	/** the minimum weighted support threshold (ratio in [0.0, 1.0]) */
	protected double minWeightedSupport;

	/**
	 * Sum of tw(tk) over all transactions.
	 */
	protected double totalTransactionWeight;

	/**
	 * Precomputed transaction weight tw(tk) for each transaction index k. tw(tk) =
	 * (sum of weights of items in tk) / |tk|, computed once at load time. Avoids
	 * recomputing item weight sums on every weighted support call.
	 */
	protected double[] transactionWeights;

	/** writer to write the output file */
	protected BufferedWriter writer;

	/** reusable buffer for building output lines without per-call allocation */
	protected final StringBuilder outputBuffer = new StringBuilder(256);

	/**
	 * Opens the output writer and resets all per-run counters. Subclasses call this
	 * at the start of runAlgorithm().
	 *
	 * @param output the path to the output file
	 * @throws IOException if the file cannot be opened for writing
	 */
	protected void initRun(String output, double minWeightedSupp) throws IOException {
		MemoryLogger.getInstance().reset();
		startTimestamp = System.currentTimeMillis();

		this.minWeightedSupport = minWeightedSupp;
		this.fwiCount = 0;
		this.candidateCount = 0;

		writer = new BufferedWriter(new FileWriter(output), 65536);
	}

	/**
	 * Closes the output writer and records the end timestamp. Subclasses call this
	 * at the end of runAlgorithm()
	 *
	 * @throws IOException if the writer cannot be closed
	 */
	protected void finalizeRun() throws IOException {
		MemoryLogger.getInstance().checkMemory();
		writer.close();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Computes ws(X) = sum_{tk in tidset} tw(tk) / sum_{tk in D} tw(tk) using the
	 * precomputed transactionWeights array.
	 *
	 * @param tidset the BitSet t(X) of transaction IDs containing itemset X
	 * @return the weighted support of X, normalized to [0.0, 1.0]
	 */
	protected double computeWeightedSupportTidset(BitSet tidset) {
		double weightSum = 0.0;
		for (int tid = tidset.nextSetBit(0); tid >= 0; tid = tidset.nextSetBit(tid + 1)) {
			weightSum += transactionWeights[tid];
		}
		return weightSum / totalTransactionWeight;
	}

	/**
	 * Loads the weight table from a file where each line has format: item weight.
	 *
	 * @param filePath the path to the weight table file
	 * @return a map from item integer to its weight
	 * @throws IOException if an error occurs while reading the file
	 */
	protected Map<Integer, Double> loadWeightTable(String filePath) throws IOException {
		Map<Integer, Double> table = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath), 65536)) {
			String line;
			// For each line
			while ((line = reader.readLine()) != null) {
				// Skip metadata lines or comments
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				int len = line.length();
				int pos = 0;

				// skip leading whitespace
				while (pos < len && line.charAt(pos) == ' ')
					pos++;

				// parse item integer manually
				int item = 0;
				while (pos < len && line.charAt(pos) >= '0' && line.charAt(pos) <= '9') {
					item = item * 10 + (line.charAt(pos) - '0');
					pos++;
				}

				// skip whitespace between tokens
				while (pos < len && line.charAt(pos) == ' ')
					pos++;

				// parse weight with Double.parseDouble on the remaining substring
				double weight = Double.parseDouble(line.substring(pos));
				table.put(item, weight);
			}
		}
		return table;
	}

	/**
	 * Loads the transaction database from a file and precomputes per-transaction
	 * weights tw(tk) into the transactionWeights field. Accumulates
	 * totalTransactionWeight in the same pass. Uses manual integer parsing
	 * to avoid regex overhead. As transactions are processed, 1-itemset tidsets are
	 * concurrently constructed.
	 *
	 * @param filePath    the path to the database file
	 * @param weightTable the pre-loaded map of item weights
	 * @return a map from each item to its full BitSet Tidset t({item})
	 * @throws IOException if an error occurs while reading the file
	 */
	protected Map<Integer, BitSet> loadDatabase(String filePath, Map<Integer, Double> weightTable) throws IOException {
		Map<Integer, BitSet> itemTidsets = new HashMap<>();
		// Buffer for weights
		double[] weightsBuf = new double[1024];
		totalTransactionWeight = 0.0;
		int tid = 0;

		// Reusable buffer for items parsed per transaction 
		int[] itemsBuf = new int[256];

		try (BufferedReader reader = new BufferedReader(new FileReader(filePath), 65536)) {
			String line;
			// For each line
			while ((line = reader.readLine()) != null) {
				// Skip metadata or comments lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				// Manual integer parsing avoids split() regex and String[] allocation
				int len = line.length();
				int itemCount = 0;
				int number = 0;
				boolean hasNumber = false;
				for (int pos = 0; pos < len; pos++) {
					char c = line.charAt(pos);
					if (c >= '0' && c <= '9') {
						number = number * 10 + (c - '0');
						hasNumber = true;
					} else if (hasNumber) {
						if (itemCount == itemsBuf.length) {
							itemsBuf = Arrays.copyOf(itemsBuf, itemsBuf.length * 2);
						}
						itemsBuf[itemCount++] = number;
						number = 0;
						hasNumber = false;
					}
				}
				if (hasNumber) {
					if (itemCount == itemsBuf.length) {
						itemsBuf = Arrays.copyOf(itemsBuf, itemsBuf.length * 2);
					}
					itemsBuf[itemCount++] = number;
				}

				if (itemCount == 0) {
					continue;
				}

				// Process weight and populate tidsets in the same operation,
				// eliminating a separate second scan over the database.
				double sum = 0.0;
				for (int k = 0; k < itemCount; k++) {
					int item = itemsBuf[k];
					Double w = weightTable.get(item);
					if (w != null) {
						sum += w;
					}

					BitSet bs = itemTidsets.get(item);
					if (bs == null) {
						bs = new BitSet();
						itemTidsets.put(item, bs);
					}
					bs.set(tid);
				}

				double tw = sum / itemCount;
				if (tid == weightsBuf.length) {
					weightsBuf = Arrays.copyOf(weightsBuf, weightsBuf.length * 2);
				}
				weightsBuf[tid] = tw;
				totalTransactionWeight += tw;
				tid++;
			}
		}

		// Trim to exact size
		transactionWeights = Arrays.copyOf(weightsBuf, tid);

		return itemTidsets;
	}

	/**
	 * Writes a frequent weighted itemset to the output file. 
	 *
	 * @param itemset         the itemset to write
	 * @param weightedSupport the weighted support of the itemset
	 * @throws IOException if an error occurs while writing
	 */
	protected void writeOut(int[] itemset, double weightedSupport) throws IOException {
		fwiCount++;
		outputBuffer.setLength(0);
		for (int i = 0; i < itemset.length; i++) {
			if (i > 0)
				outputBuffer.append(' ');
			outputBuffer.append(itemset[i]);
		}
		outputBuffer.append(" #WSUP: ");
		appendFormatted(outputBuffer, weightedSupport);
		writer.write(outputBuffer.toString());
		writer.newLine();
	}

	/**
	 * Appends a double value formatted to exactly 4 decimal places into sb without
	 * invoking String.format (which is 10-30x slower).
	 *
	 * @param sb    the StringBuilder to append into
	 * @param value the value to format
	 */
	protected static void appendFormatted(StringBuilder sb, double value) {
		long rounded = Math.round(value * 10000L);
		sb.append(rounded / 10000L).append('.');
		long frac = Math.abs(rounded % 10000L);
		if (frac < 1000)
			sb.append('0');
		if (frac < 100)
			sb.append('0');
		if (frac < 10)
			sb.append('0');
		sb.append(frac);
	}
}