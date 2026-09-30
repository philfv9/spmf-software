package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.util.ArrayList;
import java.util.List;
/*
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * A set of sequential patterns grouped by their size (number of itemsets).
  * @author Philipe-Fournier-Viger
  * */
public class SequentialPatterns {

	/**
	 * Levels of patterns, where level i contains all patterns with i itemsets (level 0 is empty).
	 */
	public final List<List<SequentialPattern>> levels = new ArrayList<List<SequentialPattern>>();

	/** Total number of sequential patterns in this collection. */
	public int sequenceCount = 0;

	/** Name given to this set of sequential patterns. */
	private final String name;

	/**
	 * Creates a new container for sequential patterns with the given name.
	 * 
	 * @param name name of this pattern collection
	 */
	public SequentialPatterns(String name) {
		this.name = name;
		// initialize level 0 with an empty list
		levels.add(new ArrayList<SequentialPattern>());
	}

	/**
	 * Prints all sequential patterns to the standard output.
	 * 
	 * @param nbObject                number of sequences in the original database
	 * @param showSequenceIdentifiers {@code true} to show sequence IDs for each pattern
	 */
	public void printFrequentPatterns(int nbObject, boolean showSequenceIdentifiers) {
		System.out.println(toString(nbObject, showSequenceIdentifiers));
	}

	/**
	 * Returns a deep copy of this SequentialPatterns object.
	 * 
	 * @return copied SequentialPatterns instance
	 */
	public SequentialPatterns copy() {
		int k = 0;
		SequentialPatterns clone = new SequentialPatterns(this.name);
		for (List<SequentialPattern> level : this.getLevels()) {
			for (SequentialPattern pattern : level) {
				clone.addSequence(pattern.copy(), k);
			}
			k++;
		}
		return clone;
	}

	/**
	 * Returns a string representation of all sequential patterns with their support.
	 * 
	 * @param nbObject                number of sequences in the database
	 * @param showSequenceIdentifiers {@code true} to show sequence IDs for each pattern
	 * @return string representation of this collection
	 */
	public String toString(int nbObject, boolean showSequenceIdentifiers) {
		StringBuilder r = new StringBuilder(200);
		r.append(" ----------");
		r.append(name);
		r.append(" -------\n");
		int levelCount = 0;
		int patternCount = 0;
		for (List<SequentialPattern> level : levels) {
			r.append("  L");
			r.append(levelCount);
			r.append(" \n");
			for (SequentialPattern sequence : level) {
				patternCount++;
				r.append("  pattern ");
				r.append(patternCount);
				r.append(":  ");
				r.append(sequence.toString());
				r.append("support :  ");
				r.append(sequence.getRelativeSupportFormated(nbObject));
				r.append(" (");
				r.append(sequence.getAbsoluteSupport());
				r.append('/');
				r.append(nbObject);
				r.append(")");
				if (showSequenceIdentifiers) {
					r.append(" sequence ids: ");
					for (Integer sid : sequence.getSequenceIDs()) {
						r.append(sid);
						r.append(" ");
					}
				}
				r.append("\n");
			}
			levelCount++;
		}
		r.append(" -------------------------------- Patterns count : ");
		r.append(sequenceCount);
		return r.toString();
	}

	/**
	 * Adds a sequential pattern to this collection at the given level.
	 * 
	 * @param sequence sequential pattern to add
	 * @param k        level index, equal to the number of itemsets in the pattern
	 */
	public void addSequence(SequentialPattern sequence, int k) {
		while (levels.size() <= k) {
			levels.add(new ArrayList<SequentialPattern>());
		}
		levels.get(k).add(sequence);
		sequenceCount++;
	}

	/**
	 * Returns all sequential patterns at the given level.
	 * 
	 * @param index level index (number of itemsets)
	 * @return list of sequential patterns at that level
	 */
	public List<SequentialPattern> getLevel(int index) {
		return levels.get(index);
	}

	/**
	 * Returns the number of levels (maximum pattern size in itemsets plus one).
	 * 
	 * @return number of levels
	 */
	public int getLevelCount() {
		return levels.size();
	}

	/**
	 * Returns all levels of sequential patterns.
	 * 
	 * @return list of levels, where each level is a list of patterns
	 */
	public List<List<SequentialPattern>> getLevels() {
		return levels;
	}

	/**
	 * Returns the total number of sequential patterns in this collection.
	 * 
	 * @return number of sequential patterns
	 */
	public int getSequenceCount() {
		return sequenceCount;
	}
}