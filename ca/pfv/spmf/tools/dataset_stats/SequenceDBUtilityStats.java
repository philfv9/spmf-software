package ca.pfv.spmf.tools.dataset_stats;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.sequential_rules.husrm.SequenceDatabaseWithUtility;
import ca.pfv.spmf.algorithms.sequential_rules.husrm.SequenceWithUtility;
/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class read a sequence database and BasicStatsFunctions.calculates
 * statistics about this sequence database, then it prints the statistics. <br/>
 * <br/>
 * In this version this class reads the database into memory before calculating
 * the statistics. It could be optimized to BasicStatsFunctions.calculate
 * statistics without reading the database in memory because a single pass is
 * required. It was done like that because the code is simpler and easier to
 * understand.
 * 
 * @author Philippe Fournier-Viger
 */
public class SequenceDBUtilityStats {

	/**
	 * This method generates statistics for a sequence database (a file)
	 * 
	 * @param path the path to the file
	 * @throws IOException exception if there is a problem while reading the file.
	 */
	public void runAlgorithm(String path) throws IOException {

		/////////////////////////////////////
		// (1) First we will read the sequence database into memory.

		// Create a new SequenceDatabase object
		SequenceDatabaseWithUtility db = new SequenceDatabaseWithUtility();
		// Load the file containing the sequence database
		db.loadFile(path, Integer.MAX_VALUE, true);

		// Get the file size in bytes
		long fileSize = new File(path).length();
		// Convert the file size to megabytes
		double fileSizeMB = fileSize / (1024.0 * 1024.0);
		// Format the file size to two decimal places
		String fileSizeMBString = String.format("%.2f", fileSizeMB);

		System.out.println("============  SEQUENCE UTILITY DATABASE STATS ==========");
		System.out.println("File size (MB): " + fileSizeMBString);
		System.out.println("Number of sequences : " + db.size());
		System.out.println("Max item: " + db.getMaxItemID());

		List<Integer> sizes = new ArrayList<Integer>(); // the lengths of each sequence
		List<Integer> itemsetsizes = new ArrayList<Integer>(); // the lengths of each itemset
		List<Double> utilities = new ArrayList<Double>(); // the utilities of each item
		List<Double> sequenceUtilities = new ArrayList<Double>();

		// Loop through the sequences in the database
		for (SequenceWithUtility sequence : db.getSequences()) {
			// Add the sequence length to the sum
			sizes.add(sequence.size());
			
			sequenceUtilities.add(sequence.exactUtility);
			
			for (List<Integer> itemset : sequence.getItemsets()) {
				// we add the size of this itemset to the list of itemset sizes
				itemsetsizes.add(itemset.size());
			}
			
			for (List<Double> itemsetUtilities : sequence.getUtilities()) {
				// we add the size of this itemset to the list of itemset sizes
				utilities.addAll(itemsetUtilities);
			}
		}

		System.out.println("Average number of itemsets per sequence : " +
				BasicStatsFunctions.calculateMean(sizes) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(sizes) + " variance: "
				+ BasicStatsFunctions.calculateVariance(sizes));
		System.out.println("Average number of items per itemset : " + BasicStatsFunctions.calculateMean(itemsetsizes)
				+ " standard deviation: " + BasicStatsFunctions.calculateStdDeviation(itemsetsizes) + " variance: "
				+ BasicStatsFunctions.calculateVariance(itemsetsizes));
		System.out.println("Average utility per item: " + BasicStatsFunctions.calculateMeanDouble(utilities)
		+ " standard deviation: " + BasicStatsFunctions.calculateStdDeviationDouble(utilities) + " variance: "
		+ BasicStatsFunctions.calculateVarianceDouble(utilities));
		System.out.println("Average utility per sequence: " + BasicStatsFunctions.calculateMeanDouble(sequenceUtilities)
		+ " standard deviation: " + BasicStatsFunctions.calculateStdDeviationDouble(sequenceUtilities) + " variance: "
		+ BasicStatsFunctions.calculateVarianceDouble(sequenceUtilities));

	}

}
