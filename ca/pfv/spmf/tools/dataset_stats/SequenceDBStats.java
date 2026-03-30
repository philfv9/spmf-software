package ca.pfv.spmf.tools.dataset_stats;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import ca.pfv.spmf.input.sequence_database_list_integers.Sequence;

/**
 * This class read a sequence database and BasicStatsFunctions.calculates statistics about this
 * sequence database, then it prints the statistics. <br/>
 * <br/>
 * In this version this class reads the database into memory before calculating
 * the statistics. It could be optimized to BasicStatsFunctions.calculate statistics without reading
 * the database in memory because a single pass is required. It was done like
 * that because the code is simpler and easier to understand.
 * 
 * @author Philippe Fournier-Viger
 */
public class SequenceDBStats {

	/**
	 * This method generates statistics for a sequence database (a file)
	 * @param path the path to the file
	 * @throws IOException  exception if there is a problem while reading the file.
	 */
	public void runAlgorithm(String path) throws IOException {

		/////////////////////////////////////
		//  (1) First we will read the sequence database into memory.
		// (actually, we don't really need to read it into memory because it
		//  just require a single pass, but the code is more simple like that
		//  - it could be optimized, if necessary).
		///////////////////////////////////
		
		List<Sequence> sequences = new ArrayList<Sequence>(); //  A sequence database is stored as a list of sequences
		int maxItem = 0; // the largest id for items in the database
		
		String thisLine;  // a temporary variable to read each line from the file
		
		Long minTime = Long.MAX_VALUE;
		Long maxTime = 0l;
		boolean hasTimeStamp = false;
		List<Integer> values = new ArrayList<Integer>();

		BufferedReader myInput = null;
		try {
			// we read the file line by line
			FileInputStream fin = new FileInputStream(new File(path));
			myInput = new BufferedReader(new InputStreamReader(fin));
			int i=0; // used to count the lines.
			
			// for each line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {
				
				// if the line is not a comment, is not empty or is not other
				// kind of metadata
				if (thisLine.isEmpty() == false &&
						thisLine.charAt(0) != '#' && thisLine.charAt(0) != '%'
						&& thisLine.charAt(0) != '@') {
				
					// we split the line according to spaces into tokens
					String tokens[] = thisLine.split(" ");
					// we create a new sequence object to store the sequence that correspond to this line.
					Sequence sequence = new Sequence(i++);
					// we create a list of integer to store the current itemset from the sequence
					// that correspond to this line.
					List<Integer> itemset = new ArrayList<Integer>();
					// For each token
					for (String token : tokens) {
						//if the token starts with "<" it means that it is a timestamp
						if (token.codePointAt(0) == '<') { 
							hasTimeStamp = true;
							// we extract the timestamp and set it as the timestamp
							// of the current itemset
							String value = token.substring(1, token.length()-1);
							Long timeStamp = Long.parseLong(value);
							
							if(timeStamp > maxTime) {
								maxTime = timeStamp;
							}
							if(timeStamp < minTime) {
								minTime = timeStamp;
							}
						} 
						// if the token is "-1" it means that it is the end of an itemset
						else if (token.equals("-1")) { 
							// we add the itemset to the sequence
							sequence.addItemset(itemset);
							// we reset the variable itemset to read the next itemset
							itemset = new ArrayList<Integer>();
						} 
						// if the token is "-2", it indicates the end of this sequence and the
						// end of the line
						else if (token.equals("-2")) { 
							// we add the sequence to the list of sequences
							sequences.add(sequence);
						}
						// otherwise, it means that the token is an item
						else {
							// otherwise, check if it is 
							// an item with the format : id(value)  where
							// id is the item ID and value is a value associated with the item
							
							// we find the position of the left parenthesis
							int indexLeftParenthesis = token.indexOf("(");
							// if there is a left parenthesis
							if(indexLeftParenthesis != -1){
								// we find the position of the left parenthesis
								int indexRightParenthesis = token.indexOf(")");
								// we extract the value
								int value = Integer.parseInt(token.substring(indexLeftParenthesis+1, indexRightParenthesis));
								values.add(value);
								// we extract the item ID
								token = token.substring(0, indexLeftParenthesis);
								// We create a new item with the item ID and the value
								int itemAsInteger = Integer.parseInt(token);
								// The item is then added to the current itemset
								itemset.add(itemAsInteger);
								
								if(itemAsInteger > maxItem) {
									maxItem = itemAsInteger;
								}
							}else{
								// Otherwise, it is just a regular item without value.
								
								//The item ID is extracted
								int itemAsInteger = Integer.parseInt(token);
								// If the item is not already in this itemset
								if(!itemset.contains(itemAsInteger)){
									// we add it to the itemset.
									itemset.add(itemAsInteger);
								}
								
								if(itemAsInteger > maxItem) {
									maxItem = itemAsInteger;
								}
							}
						}
					}
				
				}
			}
		}catch(

	Exception e)
	{
		e.printStackTrace();
	}finally
	{
		if (myInput != null) {
			myInput.close();
		}
	}

	/////////////////////////////////////
	// We finished reading the database into memory.
	// We will BasicStatsFunctions.calculate statistics on this sequence database.
	///////////////////////////////////

	System.out.println("============  SEQUENCE DATABASE STATS ==========");System.out.println("Number of sequences : "+sequences.size());

	// we initialize some variables that we will use to generate the statistics
	java.util.Set<Integer> items = new java.util.HashSet<Integer>(); // the set of all items
	List<Integer> sizes = new ArrayList<Integer>(); // the lengths of each sequence
	List<Integer> itemsetsizes = new ArrayList<Integer>(); // the lengths of each itemset
	List<Integer> differentitems = new ArrayList<Integer>(); // the number of different item for each sequence
	List<Integer> appearXtimesbySequence = new ArrayList<Integer>(); // the average number of times that items appearing
																		// in a sequence, appears in this sequence.
	// Loop on sequences from the database
	for(
	Sequence sequence:sequences)
	{
		// we add the size of this sequence to the list of sizes
		sizes.add(sequence.size());

		// this map is used to BasicStatsFunctions.calculate the number of times that each item
		// appear in this sequence.
		// the key is an item
		// the value is the number of occurences of the item until now for this sequence
		HashMap<Integer, Integer> mapIntegers = new HashMap<Integer, Integer>();

		// Loop on itemsets from this sequence
		for (List<Integer> itemset : sequence.getItemsets()) {
			// we add the size of this itemset to the list of itemset sizes
			itemsetsizes.add(itemset.size());
			// Loop on items from this itemset
			for (Integer item : itemset) {
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
	System.out.println("File "+path);System.out.println("Number of distinct items: "+items.size());System.out.println("Largest item id: "+maxItem);System.out.println("Average number of itemsets per sequence : "+

	BasicStatsFunctions.calculateMean(sizes) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(sizes) + " variance: "
				+ BasicStatsFunctions.calculateVariance(sizes));
		System.out.println("Average number of distinct item per sequence : "
				+ BasicStatsFunctions.calculateMean(differentitems) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(differentitems) + " variance: "
				+ BasicStatsFunctions.calculateVariance(differentitems));
		System.out
				.println("Average number of occurences in a sequence for each item appearing in a sequence : "
						+ BasicStatsFunctions.calculateMean(appearXtimesbySequence)
						+ " standard deviation: "
						+ BasicStatsFunctions.calculateStdDeviation(appearXtimesbySequence)
						+ " variance: "
						+ BasicStatsFunctions.calculateVariance(appearXtimesbySequence));
		System.out.println("Average number of items per itemset : "
				+ BasicStatsFunctions.calculateMean(itemsetsizes) + " standard deviation: "
				+ BasicStatsFunctions.calculateStdDeviation(itemsetsizes) + " variance: "
				+ BasicStatsFunctions.calculateVariance(itemsetsizes));
		if(hasTimeStamp) {
			System.out.println("Timestamps range: " + minTime + " to " + maxTime);	
		}
		if(values.size()>0) {
			System.out.println("Average value per item: "
					+ BasicStatsFunctions.calculateMean(values) + " standard deviation: "
					+ BasicStatsFunctions.calculateStdDeviation(values) + " variance: "
					+ BasicStatsFunctions.calculateVariance(values));
		}
	}

	

}
