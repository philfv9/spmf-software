package ca.pfv.spmf.tools.dataset_stats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.ItemSimple;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Itemset;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalpatterns.MDPattern;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequenceDatabase;

/* This file is copyright (c) 2008-2014 Philippe Fournier-Viger
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
 * 
 * A tool to calculate stats about a multidimensional database from the source
 * code.
 * 
 * @see MDSequenceDatabase
 * @see MDSequence
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MDSequenceDatabaseStats {

	/**
	 * Run the tool
	 * 
	 * @param input the file path
	 * @throws IOException
	 */
	public void runAlgorithm(String input) throws IOException {
		MDSequenceDatabase db = new MDSequenceDatabase();
		// Load the file containing the md-sequence database
		db.loadFile(input);

		System.out.println("============  MD-SEQUENCE DATABASE STATS ==========");
		System.out.println("File " + input);
		System.out.println("Number of MD-sequences : " + db.size());
		
		if(db.size() == 0) {
			return;
		}
		
		int numberOfDimensions = db.getPatternDatabase().getDimensionCount();
		System.out.println("Number of dimensions: " + numberOfDimensions);
		
		// For each dimension
		for(int i= 0; i < numberOfDimensions; i++) {
			
			Set<Integer> distinctValues = new HashSet<Integer>();
			for(MDPattern mdpattern : db.getPatternDatabase().getMDPatterns()) {
				distinctValues.add(mdpattern.get(i));
			}
			System.out.println( "  Dimension " + i +" has " + distinctValues.size() + " different values.");
		}
		
		boolean hasTimeStamps = false;
		long minTime = Long.MAX_VALUE;
		long maxTime = 0;
		

		// we initialize some variables that we will use to generate the statistics
		java.util.Set<Integer> items = new java.util.HashSet<Integer>(); // the set of all items
		List<Integer> sizes = new ArrayList<Integer>(); // the lengths of each sequence
		List<Integer> itemsetsizes = new ArrayList<Integer>(); // the lengths of each itemset
		List<Integer> differentitems = new ArrayList<Integer>(); // the number of different item for each sequence
		List<Integer> appearXtimesbySequence = new ArrayList<Integer>(); // the average number of times that items
																			// appearing
																			// in a sequence, appears in this sequence.
		int maxItem = 0;

		// Loop on sequences from the database
		for (MDSequence mdsequence : db.getSequences()) {
			ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Sequence sequence = mdsequence.getSequence();

			// we add the size of this sequence to the list of sizes
			sizes.add(sequence.size());

			// this map is used to BasicStatsFunctions.calculate the number of times that
			// each item
			// appear in this sequence.
			// the key is an item
			// the value is the number of occurences of the item until now for this sequence
			HashMap<Integer, Integer> mapIntegers = new HashMap<Integer, Integer>();

			// Loop on itemsets from this sequence
			for (Itemset itemset : sequence.getItemsets()) {
				
				long timestamp = itemset.getTimestamp();
				if(timestamp > maxTime) {
					maxTime = timestamp;
				}
				if(timestamp < minTime) {
					minTime = timestamp;
				}
				if(timestamp != 0f) {
					hasTimeStamps = true;
				}
				
				
				// we add the size of this itemset to the list of itemset sizes
				itemsetsizes.add(itemset.size());
				// Loop on items from this itemset
				for (ItemSimple itemObject : itemset.getItems()) {
					int item = itemObject.getId();
					// If the item is not in the map already, we set count to 0
					Integer count = mapIntegers.get(item);
					if (count == null) {
						count = 0;
					}
					// otherwise we set the count to count +1
					count = count + 1;
					mapIntegers.put(item, count);
					// finally, we add the item to the set of items
					items.add(item);

					if (item > maxItem) {
						maxItem = item;
					}
				}
			}
			// we add all items found in this sequence to the global list
			// of different items for the database
			differentitems.add(mapIntegers.entrySet().size());

			// for each item appearing in this sequence,
			// we put the number of times in a global list "appearXtimesbySequence"
			// previously described.
			for (Entry<Integer, Integer> entry : mapIntegers.entrySet()) {
				appearXtimesbySequence.add(entry.getValue());
			}
		}

		// we print the statistics
		System.out.println("Number of distinct items: " + items.size());
		System.out.println("Largest item id: " + maxItem);
		System.out.println("Average number of itemsets per sequence : " +

				BasicStatsFunctions.calculateMean(sizes) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(sizes) + " variance: "
				+ BasicStatsFunctions.calculateVariance(sizes));
		System.out.println(
				"Average number of distinct item per sequence : " + BasicStatsFunctions.calculateMean(differentitems)
						+ " standard deviation: " + BasicStatsFunctions.calculateStdDeviation(differentitems)
						+ " variance: " + BasicStatsFunctions.calculateVariance(differentitems));
		System.out.println("Average number of occurences in a sequence for each item appearing in a sequence : "
				+ BasicStatsFunctions.calculateMean(appearXtimesbySequence) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(appearXtimesbySequence) + " variance: "
				+ BasicStatsFunctions.calculateVariance(appearXtimesbySequence));
		System.out.println("Average number of items per itemset : " + BasicStatsFunctions.calculateMean(itemsetsizes)
				+ " standard deviation: " + BasicStatsFunctions.calculateStdDeviation(itemsetsizes) + " variance: "
				+ BasicStatsFunctions.calculateVariance(itemsetsizes));
			if(hasTimeStamps) {
				System.out.println("Timestamps range: " + minTime + " to " + maxTime);	
			}
	}

}
