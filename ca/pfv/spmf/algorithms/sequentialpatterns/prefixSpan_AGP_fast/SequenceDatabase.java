package ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import ca.pfv.spmf.datastructures.collections.list.ArrayListObject;
import ca.pfv.spmf.datastructures.collections.list.ListObject;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToObject;
import ca.pfv.spmf.datastructures.collections.map.MapIntToObject;
/*
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * Inspired in SPMF. Implementation of a sequence database. Each sequence should
 * have a unique id. See examples in /test/ directory for the format of input
 * files.
 *
 * Copyright Antonio Gomariz Peñalver 2013
 *
 * @author agomariz
 */
public class SequenceDatabase {

	private MapIntToObject<BitSet> frequentItems = new AMapIntToObject<BitSet>();

	private ListObject<Sequence> sequences = new ArrayListObject<Sequence>();

	/**
	 * true as long as every itemset seen so far is a singleton, i.e. the
	 * database looks like a plain sequence of items rather than a sequence
	 * of itemsets. Used to enable a simpler/faster pair-counting path.
	 */
	private boolean itemSequenceDatabase = true;
	
	public SequenceDatabase() {

	}

	/**
	 * From a file located at the given string path, we create a database composed
	 * of a list of sequences
	 *
	 * @param path               File path where we have the database
	 * @param minSupportRelative Minimum absolute support
	 * @throws IOException
	 */
	public void loadFile(String path, double minSupportRelative) throws IOException {
		String thisLine;
		BufferedReader myInput = null;
		try {
			FileInputStream fin = new FileInputStream(new File(path));
			myInput = new BufferedReader(new InputStreamReader(fin));
			int sequenceID = 1;
			// For each line
			while ((thisLine = myInput.readLine()) != null) {
				// If the line is not a comment line
				if (thisLine.charAt(0) != '#' && thisLine.charAt(0) != '%' && thisLine.charAt(0) != '@') {
					// we read it and add it as a sequence
					addSequence(thisLine.split(" "), sequenceID);
					sequenceID++;
				}
			}
			double minSupportAbsolute = (int) Math.ceil(minSupportRelative * sequences.size());
			// We scan the frequent items and remove, on the fly, those that
			// turn out to be non-frequent (the iterator supports removal
			// during iteration, so no second pass/temporary set is needed)
			MapIntToObject<BitSet>.KeyIterator keyIterator = frequentItems.iteratorForKeys();
			while (keyIterator.hasNext()) {
				int item = keyIterator.next();
				if (frequentItems.get(item).cardinality() < minSupportAbsolute) {
					keyIterator.remove();
				}
			}
		} catch (Exception e) {
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
	}

	public void addSequence(String[] integers, int sequenceID) {
		Sequence sequence = new Sequence(sequences.size());
		sequence.setID(sequenceID);
		Itemset itemset = new Itemset();
		Set<Integer> counted = new HashSet<Integer>();

		for (int i = 0; i < integers.length; i++) {
			if (integers[i].equals("-1")) { // end of an itemset
				if (itemset.size() != 1) {
					itemSequenceDatabase = false;
				}
				sequence.addItemset(itemset);
				itemset = new Itemset();
			} else if (integers[i].equals("-2")) { // end of a sequence
				sequences.add(sequence);
			} else {
				// extract the value for an item
				int item = Integer.parseInt(integers[i]);
				if (counted.add(item)) {
					BitSet appearances = frequentItems.get(item);
					if (appearances == null) {
						appearances = new BitSet();
						frequentItems.put(item, appearances);
					}
					appearances.set(sequence.getId());
				}
				itemset.addItem(item);
			}

		}
	}

	/**
	 * It informs whether every itemset of the database is a singleton (i.e.
	 * the database is actually a plain sequence of items, as opposed to a
	 * sequence of itemsets). This lets the recursion skip the "postfix"
	 * (same itemset) bookkeeping entirely, since it can never occur.
	 * @return true if every itemset has exactly one item
	 */
	public boolean isItemSequenceDatabase() {
		return itemSequenceDatabase;
	}

	/**
	 * String representation of the SecuenceDatabase
	 * 
	 * @return the string
	 */
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		for (int i = 0; i < sequences.size(); i++) {
			Sequence sequence = sequences.get(i);
			r.append(sequence.getId());
			r.append(":  ");
			r.append(sequence.toString());
			r.append('\n');
		}
		return r.toString();
	}

	/**
	 * It returns the number of sequences of the sequence database
	 * 
	 * @return the number of sequences
	 */
	public int size() {
		return sequences.size();
	}

	/**
	 * It return the list of sequences in this sequence database
	 * 
	 * @return the list of sequences
	 */
	public ListObject<Sequence> getSequences() {
		return sequences;
	}

	/**
	 * It return the frequent items
	 * 
	 * @return a map of entries where key = item and value = its bitset.
	 */
	public MapIntToObject<BitSet> getFrequentItems() {
		return frequentItems;
	}

	public void clear() {
		if (frequentItems != null) {
			frequentItems.clear();
		}
		frequentItems = null;
		if (sequences != null) {
			sequences.clear();
		}
		sequences = null;
	}
}