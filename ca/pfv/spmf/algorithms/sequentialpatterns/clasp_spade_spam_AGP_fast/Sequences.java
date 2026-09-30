package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Copyright Antonio Gomariz Peñalver 2013
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */
/**
 * This class stores the frequent sequential patterns found by CMSPADE, SPADE or
 * SPAM-AGP. The patterns are stored by level, such that all sequences with 1
 * item are stored in level 1, all sequences with 2 items are stored in level 2,
 * and so on.
 * 
 * @author agomariz
 */
public class Sequences {

	/**
	 * Frequent patterns organized by level; level k holds patterns of size k.
	 */
	public List<List<Pattern>> levels = new ArrayList<List<Pattern>>();

	/** Total number of frequent sequences stored, maintained incrementally. */
	public int numberOfFrequentSequences = 0;
	
	/** Descriptive name of this collection, used when printing. */
	private String name;

	/**
	 * Constructs an empty collection of sequences with the given name.
	 * 
	 * @param name the name of this collection
	 */
	public Sequences(String name) {
		levels.add(new ArrayList<Pattern>());
		this.name = name;
	}

	/**
	 * Returns the string representation of this collection.
	 * 
	 * @return the string representation
	 */
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder(200);
		r.append(name);
		int levelCount = 0;
		for (List<Pattern> level : levels) {
			r.append("\n***Level ").append(levelCount).append("***\n\n");
			for (Pattern sequence : level) {
				r.append(sequence.toString());
				r.append('\n');
			}
			levelCount++;
		}
		return r.toString();
	}

	/**
	 * Get patterns as string.
	 * 
	 * @param outputSequenceIdentifiers if true, the sequential identifiers will be
	 *                                  shown
	 * @return a string
	 */
	public String toStringToFile(boolean outputSequenceIdentifiers) {
		StringBuilder r = new StringBuilder(200);
		int levelCount = 0;
		if (levels != null) {
			for (List<Pattern> level : levels) {
				r.append("\n***Level ").append(levelCount).append("***\n\n");
				for (Pattern sequence : level) {
					r.append(sequence.toStringToFile(outputSequenceIdentifiers));
					r.append('\n');
				}
				levelCount++;
			}
		}
		return r.toString();
	}

	/**
	 * Adds a pattern at the level corresponding to its size.
	 * 
	 * @param sequence the pattern to add
	 * @param k        the level (size) at which to store the pattern
	 */
	public void addSequence(Pattern sequence, int k) {
		while (levels.size() <= k) {
			levels.add(new ArrayList<Pattern>());
		}
		levels.get(k).add(sequence);
		numberOfFrequentSequences++;
	}

	/**
	 * Returns the patterns stored at the given level.
	 * 
	 * @param index the level index
	 * @return the list of patterns at that level
	 */
	public List<Pattern> getLevel(int index) {
		return levels.get(index);
	}

	/**
	 * Returns the number of levels in this collection.
	 * 
	 * @return the number of levels
	 */
	public int getLevelCount() {
		return levels.size();
	}

	/**
	 * Returns all the levels of this collection.
	 * 
	 * @return the list of levels
	 */
	public List<List<Pattern>> getLevels() {
		return levels;
	}

	/**
	 * Returns the total number of patterns stored in this collection.
	 * 
	 * @return the total pattern count
	 */
	public int size() {
		return numberOfFrequentSequences;
	}

	/**
	 * Sorts the patterns within each level.
	 */
	public void sort() {
		for (List<Pattern> nivel : levels) {
			Collections.sort(nivel);
		}
	}

	/**
	 * Clears all the levels and patterns of this collection.
	 */
	public void clear() {
		for (List<Pattern> nivel : levels) {
			nivel.clear();
		}
		levels.clear();
		levels = null;
	}

	/**
	 * Adds a list of patterns, dispatching each one to its corresponding level.
	 * 
	 * @param list the patterns to add
	 */
	public void addSequences(List<Pattern> list) {
		for (Pattern p : list) {
			addSequence(p, p.size());
		}
	}
}