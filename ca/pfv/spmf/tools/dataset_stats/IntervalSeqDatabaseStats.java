package ca.pfv.spmf.tools.dataset_stats;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger, Yuechun Li
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp.SymbolicTimeInterval;

/**
 * An tool to calculate stats about an interval sequence database (as used by
 * algorithms such as FastTIRP)
 * 
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public class IntervalSeqDatabaseStats {

	

	/**
	 * Run the algorithm
	 * 
	 * @param input the input file path
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input) throws IOException {
		// We scan the database a first time to calculate the support of each item.
		BufferedReader myInput = null;
		String thisLine;

		List<List<SymbolicTimeInterval>> database = new ArrayList<>();
		
		// Number of sequences in the database (for statistics) 
		int numberOfSequences = -1;

		// Number of time interval in the database (for statistics) 
		int numberOfTimeIntervals = -1;
		
		// Set of event types
		Set<Integer> eventTypes = new HashSet<Integer>();
		
		// durations
		List<Integer> durations = new ArrayList<Integer>();

		try {
			// prepare the object for reading the file
			File file = new File(input);
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true) {
					continue;
				}
				char firstChar = thisLine.charAt(0);
				if (firstChar == '#' || firstChar == '%' || firstChar == 's' || firstChar == '@') {
					continue;
				}

				// Read a sequence
				String[] allStis = thisLine.split(";");

				// Create a sequence object to store the sequence
				List<SymbolicTimeInterval> sequence = new ArrayList<>(allStis.length);

				// For each symbol time interval
				for (int i = 0; i < allStis.length; i++) {
					String stiString = allStis[i];
					String[] tokens = stiString.split(",");
					int start = Integer.parseInt(tokens[0]);
					int end = Integer.parseInt(tokens[1]);
					int symbol = Integer.parseInt(tokens[2]);
					
					eventTypes.add(symbol);

					numberOfTimeIntervals++;

					// Add the symbolic time interval to the sequene
					// If the duration satisfies the constraints
					int duration = end - start;
					durations.add(duration);
					sequence.add(new SymbolicTimeInterval(start, end, symbol));
				}

				// Add the sequence to the database
				database.add(sequence);
			}
		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		numberOfSequences = database.size();
		
		System.out.println("=============  Interval sequence database stats- STATS =============");
		System.out.println(" Dataset: " + input);
		System.out.println("   Number of sequences: " + numberOfSequences);
		System.out.println("   Number of distinct event types: " + eventTypes.size());
		System.out.println("   Number of time intervals: " + numberOfTimeIntervals);
		System.out.println("   Average duration of time intervals: "
				+ BasicStatsFunctions.calculateMean(durations) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(durations) + " variance: "
				+ BasicStatsFunctions.calculateVariance(durations));
		System.out.println(
				"   Average number of time intervals per sequence: " + numberOfTimeIntervals / (double) numberOfSequences);
		System.out.println("=============================================================");
	}


}