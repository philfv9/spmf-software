package ca.pfv.spmf.algorithms.frequentpatterns.dhauim;

/* This is an implementation of the dHAUIM algorithm.
*
* Copyright (c) 2026 dHAUIM
*
* This file is part of the SPMF DATA MINING SOFTWARE * (http://www.philippe-fournier-viger.com/spmf).
*
*
* SPMF is free software: you can redistribute it and/or modify it under the * terms of the GNU General Public License as published by the Free Software * Foundation, either version 3 of the License, or (at your option) any later * version. *
*
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR * A PARTICULAR PURPOSE. See the GNU General Public License for more details. *
*
* You should have received a copy of the GNU General Public License along with * SPMF. If not, see <http://www.gnu.org/licenses/>.
*
* @author Philippe Fournier-Viger
* 
* Do not remove copyright and license information.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the dHAUIM (Diffset-based High Average-Utility
 * Itemset Mining) algorithm as described in:
 * T. Truong, H. Duong, B. Le, P. Fournier-Viger (2019).
 * Efficient Vertical Mining of High Average-Utility Itemsets based on Novel Upper-Bounds.
 * IEEE Transactions on Knowledge and Data Engineering.
 * 
 * Note: This is not the original implementation but a re-implementation based on the paper.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoDHAUIM {

	/** the maximum memory usage */
	double maxMemory = 0;

	/** the time the algorithm started */
	long startTimestamp = 0;

	/** the time the algorithm terminated */
	long endTimestamp = 0;

	/** the number of HAUIs generated */
	int huiCount = 0;

	/** the minimum average-utility threshold (mu) */
	int mu = 0;

	/** the integrated matrix Q stored as sparse rows, one row per transaction */
	QRow[] matrix;

	/** map from a column of the integrated matrix to the corresponding item */
	int[] columnToItem;

	/** writer to write the output file */
	BufferedWriter writer = null;

	/**
	 * Represents a row of the integrated matrix Q in sparse form.
	 */
	class QRow {
		/** the columns having a utility greater than zero, in ascending order */
		int[] columns;
		/** the utilities q' of the columns */
		int[] utilities;
	}

	/**
	 * Represents an IDUL node (C, d(C), V(C)) of the prefix-tree.
	 */
	class IDULNode {
		/** the columns of the itemset C in ascending order */
		int[] itemset;
		/** the diffset d(C) as a sorted list of transaction identifiers */
		int[] diffset;
		/** the vector V(C) starting from column minInd(C) */
		long[] vector;
	}

	/**
	 * Default constructor.
	 */
	public AlgoDHAUIM() {
	}

	/**
	 * Run the dHAUIM algorithm.
	 * @param input the input file path
	 * @param output the output file path
	 * @param minAUtility the minimum average-utility threshold
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public void runAlgorithm(String input, String output, int minAUtility) throws IOException {
		maxMemory = 0;
		huiCount = 0;
		startTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();
		mu = minAUtility;

		writer = new BufferedWriter(new FileWriter(output));
		writer.write("@FILETYPE=\"High average-utility itemsets\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

		List<int[]> listItems = new ArrayList<int[]>();
		List<int[]> listUtilities = new ArrayList<int[]>();
		Map<Integer, Integer> mapItemToColumn = new TreeMap<Integer, Integer>();

		BufferedReader myInput = null;
		String thisLine;
		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#'
						|| thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}
				String[] split = thisLine.split(":");
				String[] items = split[0].split(" ");
				String[] utilityValues = split[2].split(" ");

				int[] transactionItems = new int[items.length];
				int[] transactionUtilities = new int[items.length];
				for (int i = 0; i < items.length; i++) {
					transactionItems[i] = Integer.parseInt(items[i]);
					transactionUtilities[i] = Integer.parseInt(utilityValues[i]);
					mapItemToColumn.put(transactionItems[i], 0);
				}
				listItems.add(transactionItems);
				listUtilities.add(transactionUtilities);
			}
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		int numColumns = mapItemToColumn.size();
		columnToItem = new int[numColumns];
		int column = 0;
		for (Map.Entry<Integer, Integer> entry : mapItemToColumn.entrySet()) {
			entry.setValue(column);
			columnToItem[column] = entry.getKey();
			column++;
		}

		matrix = createIntegratedMatrix(listItems, listUtilities, mapItemToColumn);
		listItems = null;
		listUtilities = null;

		long[] vEmpty = computeColumnUtilities(numColumns);
		int[][] tidsets = buildTidsets(numColumns);
		int[][] diffsets = new int[numColumns][];
		boolean[] retained = new boolean[numColumns];

		for (int j = 0; j < numColumns; j++) {
			int[] diffset = complement(tidsets[j], matrix.length);
			long[] vector = Arrays.copyOf(vEmpty, numColumns);
			subtractRows(vector, 0, diffset);
			long aub1 = 0;
			for (int k = 0; k < vector.length; k++) {
				if (vector[k] > aub1) {
					aub1 = vector[k];
				}
			}
			if (aub1 >= mu) {
				retained[j] = true;
				diffsets[j] = diffset;
			}
		}
		tidsets = null;

		int[] newIndex = new int[numColumns];
		int newCount = 0;
		for (int j = 0; j < numColumns; j++) {
			newIndex[j] = retained[j] ? newCount++ : -1;
		}
		reduceMatrix(newIndex, newCount);
		int[] newColumnToItem = new int[newCount];
		int[][] newDiffsets = new int[newCount][];
		for (int j = 0; j < numColumns; j++) {
			if (retained[j]) {
				newColumnToItem[newIndex[j]] = columnToItem[j];
				newDiffsets[newIndex[j]] = diffsets[j];
			}
		}
		columnToItem = newColumnToItem;
		diffsets = null;

		vEmpty = computeColumnUtilities(newCount);
		List<IDULNode> root = new ArrayList<IDULNode>();
		for (int j = 0; j < newCount; j++) {
			IDULNode node = new IDULNode();
			node.itemset = new int[] { j };
			node.diffset = newDiffsets[j];
			node.vector = Arrays.copyOfRange(vEmpty, j, newCount);
			subtractRows(node.vector, j, node.diffset);
			root.add(node);
		}

		MemoryLogger.getInstance().checkMemory();

		hauSearch(root);

		MemoryLogger.getInstance().checkMemory();
		writer.close();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Create the integrated matrix Q in sparse form, where each transaction
	 * row has its items sorted by column.
	 * @param listItems the items of each transaction
	 * @param listUtilities the utilities of the items of each transaction
	 * @param mapItemToColumn the map from each item to its column
	 * @return the integrated matrix as an array of sparse rows
	 */
	private QRow[] createIntegratedMatrix(List<int[]> listItems, List<int[]> listUtilities,
			Map<Integer, Integer> mapItemToColumn) {
		QRow[] rows = new QRow[listItems.size()];
		for (int t = 0; t < rows.length; t++) {
			int[] items = listItems.get(t);
			int[] utilities = listUtilities.get(t);
			long[] keys = new long[items.length];
			int count = 0;
			for (int i = 0; i < items.length; i++) {
				if (utilities[i] > 0) {
					keys[count++] = (((long) mapItemToColumn.get(items[i])) << 32) | i;
				}
			}
			keys = Arrays.copyOf(keys, count);
			Arrays.sort(keys);
			QRow row = new QRow();
			row.columns = new int[count];
			row.utilities = new int[count];
			for (int i = 0; i < count; i++) {
				row.columns[i] = (int) (keys[i] >>> 32);
				row.utilities[i] = utilities[(int) (keys[i] & 0xFFFFFFFFL)];
			}
			rows[t] = row;
		}
		return rows;
	}

	/**
	 * Compute the vector V(empty set), i.e. the sum of each column of the matrix Q.
	 * @param numColumns the number of columns of the matrix
	 * @return the vector of column utilities u(a_j)
	 */
	private long[] computeColumnUtilities(int numColumns) {
		long[] vector = new long[numColumns];
		for (QRow row : matrix) {
			for (int p = 0; p < row.columns.length; p++) {
				vector[row.columns[p]] += row.utilities[p];
			}
		}
		return vector;
	}

	/**
	 * Build the tidset rho(a_j) of each column of the matrix Q.
	 * @param numColumns the number of columns of the matrix
	 * @return an array containing the sorted tidset of each column
	 */
	private int[][] buildTidsets(int numColumns) {
		int[] counts = new int[numColumns];
		for (QRow row : matrix) {
			for (int p = 0; p < row.columns.length; p++) {
				counts[row.columns[p]]++;
			}
		}
		int[][] tidsets = new int[numColumns][];
		for (int j = 0; j < numColumns; j++) {
			tidsets[j] = new int[counts[j]];
			counts[j] = 0;
		}
		for (int t = 0; t < matrix.length; t++) {
			QRow row = matrix[t];
			for (int p = 0; p < row.columns.length; p++) {
				int c = row.columns[p];
				tidsets[c][counts[c]++] = t;
			}
		}
		return tidsets;
	}

	/**
	 * Compute the diffset of a 1-itemset as d(a_j) = D \ rho(a_j) (formula 6).
	 * @param tidset the sorted tidset of the item
	 * @param numTransactions the number of transactions in the database
	 * @return the sorted diffset of the item
	 */
	private int[] complement(int[] tidset, int numTransactions) {
		int[] result = new int[numTransactions - tidset.length];
		int count = 0;
		int p = 0;
		for (int t = 0; t < numTransactions; t++) {
			if (p < tidset.length && tidset[p] == t) {
				p++;
			} else {
				result[count++] = t;
			}
		}
		return result;
	}

	/**
	 * Compute the set difference of two sorted diffsets, as in d(E) = d(Cj) \ d(Ci) (formula 7).
	 * @param first the diffset from which elements are kept
	 * @param second the diffset whose elements are removed
	 * @return the sorted difference first \ second
	 */
	private int[] difference(int[] first, int[] second) {
		int[] result = new int[first.length];
		int count = 0;
		int q = 0;
		for (int p = 0; p < first.length; p++) {
			while (q < second.length && second[q] < first[p]) {
				q++;
			}
			if (q < second.length && second[q] == first[p]) {
				continue;
			}
			result[count++] = first[p];
		}
		return Arrays.copyOf(result, count);
	}

	/**
	 * Subtract from a vector the rows of Q indexed by a diffset (formula 9).
	 * @param vector the vector to update, where position 0 is the column offset
	 * @param offset the column corresponding to the first position of the vector
	 * @param diffset the transactions whose rows are subtracted
	 */
	private void subtractRows(long[] vector, int offset, int[] diffset) {
		for (int tid : diffset) {
			QRow row = matrix[tid];
			for (int p = 0; p < row.columns.length; p++) {
				int col = row.columns[p];
				if (col >= offset) {
					vector[col - offset] -= row.utilities[p];
				}
			}
		}
	}

	/**
	 * Delete the columns of the matrix Q that were removed by the strong width pruning strategy.
	 * @param newIndex the new column of each old column, or -1 if the column is deleted
	 * @param newCount the number of remaining columns
	 */
	private void reduceMatrix(int[] newIndex, int newCount) {
		for (int t = 0; t < matrix.length; t++) {
			QRow row = matrix[t];
			int count = 0;
			for (int p = 0; p < row.columns.length; p++) {
				if (newIndex[row.columns[p]] >= 0) {
					count++;
				}
			}
			QRow reduced = new QRow();
			reduced.columns = new int[count];
			reduced.utilities = new int[count];
			int q = 0;
			for (int p = 0; p < row.columns.length; p++) {
				if (newIndex[row.columns[p]] >= 0) {
					reduced.columns[q] = newIndex[row.columns[p]];
					reduced.utilities[q] = row.utilities[p];
					q++;
				}
			}
			matrix[t] = reduced;
		}
	}

	/**
	 * Recursively search for high average-utility itemsets (HAU-Search procedure).
	 * @param extensions the set [P] of all item-extensions of a prefix P
	 * @throws IOException if an error occurs while writing output
	 */
	private void hauSearch(List<IDULNode> extensions) throws IOException {
		for (int i = 0; i < extensions.size(); i++) {
			IDULNode ci = extensions.get(i);
			double au = computeAu(ci);
			if (computeIaub(ci) >= mu && computeLaub(ci, au) >= mu) {
				if (au >= mu) {
					writeOut(ci.itemset, au);
				}
				if (extensions.size() > 1) {
					List<IDULNode> children = new ArrayList<IDULNode>();
					for (int j = i + 1; j < extensions.size(); j++) {
						IDULNode e = createExtension(ci, extensions.get(j));
						if (computeAub(e) >= mu) {
							children.add(e);
						}
					}
					hauSearch(children);
				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Create the extension E = Ci U Cj with its diffset d(E) and its vector V(E).
	 * @param ci the left node Ci
	 * @param cj the right node Cj
	 * @return the IDUL node of the extension E
	 */
	private IDULNode createExtension(IDULNode ci, IDULNode cj) {
		IDULNode e = new IDULNode();
		e.itemset = Arrays.copyOf(ci.itemset, ci.itemset.length + 1);
		e.itemset[ci.itemset.length] = cj.itemset[cj.itemset.length - 1];
		e.diffset = difference(cj.diffset, ci.diffset);
		e.vector = ci.vector.clone();
		subtractRows(e.vector, ci.itemset[0], e.diffset);
		return e;
	}

	/**
	 * Compute the average-utility au(C) = u(C) / |C| using the vertical form (formula 1).
	 * @param node the IDUL node of the itemset C
	 * @return the average-utility of C
	 */
	private double computeAu(IDULNode node) {
		int minInd = node.itemset[0];
		long utility = 0;
		for (int k : node.itemset) {
			utility += node.vector[k - minInd];
		}
		return utility / (double) node.itemset.length;
	}

	/**
	 * Compute the upper-bound aub(C) = max{v_k(C) | k >= minInd(C)} (formula 3).
	 * @param node the IDUL node of the itemset C
	 * @return the value of aub(C)
	 */
	private long computeAub(IDULNode node) {
		long max = 0;
		for (int idx = 0; idx < node.vector.length; idx++) {
			if (node.vector[idx] > max) {
				max = node.vector[idx];
			}
		}
		return max;
	}

	/**
	 * Compute the upper-bound iaub(C) = max{v_k(C) | a_k in C or k > m} (formula 4).
	 * @param node the IDUL node of the itemset C
	 * @return the value of iaub(C)
	 */
	private long computeIaub(IDULNode node) {
		int minInd = node.itemset[0];
		int last = node.itemset[node.itemset.length - 1];
		long max = 0;
		int p = 0;
		for (int idx = 0; idx < node.vector.length; idx++) {
			int k = minInd + idx;
			boolean inItemset = false;
			if (p < node.itemset.length && node.itemset[p] == k) {
				inItemset = true;
				p++;
			}
			if ((inItemset || k > last) && node.vector[idx] > max) {
				max = node.vector[idx];
			}
		}
		return max;
	}

	/**
	 * Compute the upper-bound laub(C) = au(C) + aub_{m+1}(C) (formula 5).
	 * @param node the IDUL node of the itemset C
	 * @param au the average-utility of C
	 * @return the value of laub(C)
	 */
	private double computeLaub(IDULNode node, double au) {
		int minInd = node.itemset[0];
		int last = node.itemset[node.itemset.length - 1];
		long max = 0;
		for (int idx = last + 1 - minInd; idx < node.vector.length; idx++) {
			if (node.vector[idx] > max) {
				max = node.vector[idx];
			}
		}
		return au + max;
	}

	/**
	 * Write a high average-utility itemset to the output file.
	 * @param itemset the columns of the itemset
	 * @param autility the average utility of the itemset
	 * @throws IOException if an error occurs while writing
	 */
	private void writeOut(int[] itemset, double autility) throws IOException {
		huiCount++;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < itemset.length; i++) {
			if (i > 0) {
				buffer.append(' ');
			}
			buffer.append(columnToItem[itemset[i]]);
		}
		buffer.append(" #AUTIL: ");
		buffer.append(autility);
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  dHAUIM ALGORITHM v.2.68 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility itemsets count : " + huiCount);
		System.out.println("===================================================");
	}
}