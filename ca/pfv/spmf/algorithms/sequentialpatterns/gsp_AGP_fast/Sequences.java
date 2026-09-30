package ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
 * This class implements a list of frequent sequence lists (or frequent pattern
 * lists) that is organized by levels. That level contains all of sequences that
 * have a concrete number of items. Therefore, we allocate 1-sequences in level
 * 1, 2-sequences in level 2, and so forth. Index 0 is an intentionally unused
 * placeholder kept so that level indices line up directly with "k" (the pattern
 * length) used throughout the GSP algorithm; {@link #getLevelCount()} accounts
 * for it.
 *
 * @author Antonio Gomariz Peñalver,  Philippe Fournier-Viger (some optimizations)
 **/
public class Sequences {

	/**
	 * List of lists, where each list contains the frequent patterns of a concrete
	 * length
	 */
	private final List<List<Pattern>> levels;

	/**
	 * Number of frequent sequences
	 */
	private int numberOfFrequentSequences = 0;

	/**
	 * Descriptive header that can be printed along with the patterns
	 */
	private final String header;

	/**
	 * Creates a new, empty level-organized pattern structure.
	 *
	 * @param header a descriptive header printed along with the patterns
	 */
	public Sequences(String header) {
		this.levels = new ArrayList<>(16);
		this.levels.add(new ArrayList<Pattern>());
		this.header = header;
	}

	/**
	 * Print all the levels of the structure in the standard output
	 */
	public void printFrequentSequences() {
		System.out.println(toString());
	}

	/**
	 * Method that gives us in a string the content of all Sequences structure
	 * 
	 * @return the string
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(numberOfFrequentSequences * 50 + 100);
		sb.append(header);

		for (int levelCount = 0; levelCount < levels.size(); levelCount++) {
			List<Pattern> level = levels.get(levelCount);
			sb.append("\n***Level ").append(levelCount).append("***\n\n");

			for (Pattern sequence : level) {
				sb.append(sequence.toString()).append('\n');
			}
		}
		return sb.toString();
	}

	/**
	 * Method that gives us in a string the content of all Sequences structure in a
	 * SPMF format
	 * 
	 * @param outputSequenceIdentifiers if true, sequence identifiers will be output
	 * @return the string
	 */
	public String toStringToFile(boolean outputSequenceIdentifiers) {
		StringBuilder sb = new StringBuilder(numberOfFrequentSequences * 40);

		for (List<Pattern> level : levels) {
			for (Pattern sequence : level) {
				sb.append(sequence.toStringToFile(outputSequenceIdentifiers)).append('\n');
			}
		}
		return sb.toString();
	}

	/**
	 * Method that adds a sequence in a given level
	 * 
	 * @param sequence   a sequence to add
	 * @param levelIndex the level where the sequence must be
	 */
	public void addSequence(Pattern sequence, int levelIndex) {
		while (levels.size() <= levelIndex) {
			levels.add(new ArrayList<Pattern>(32));
		}
		levels.get(levelIndex).add(sequence);
		numberOfFrequentSequences++;
	}

	/**
	 * Method that adds a sequence set in a given level
	 * 
	 * @param sequences  a sequence collection to add
	 * @param levelIndex the level where the sequences must be
	 */
	public void addSequences(Collection<Pattern> sequences, int levelIndex) {
		if (sequences == null || sequences.isEmpty()) {
			return;
		}

		while (levels.size() <= levelIndex) {
			levels.add(new ArrayList<Pattern>(sequences.size()));
		}

		List<Pattern> targetLevel = levels.get(levelIndex);
		targetLevel.addAll(sequences);
		numberOfFrequentSequences += sequences.size();
	}

	/**
	 * Get an unmodifiable view of the frequent sequences that appear in a
	 * particular level.
	 * 
	 * @param index the level index
	 * @return the list of patterns of this level
	 */
	public List<Pattern> getLevel(int index) {
		return Collections.unmodifiableList(levels.get(index));
	}

	/**
	 * It gives us the total number of levels. Notice that we discount the level 0
	 * placeholder.
	 * 
	 * @return the number of levels.
	 */
	public int getLevelCount() {
		return levels.size() - 1;
	}

	/**
	 * It obtains an unmodifiable view of the pattern lists, being each pattern list
	 * the frequent sequences with a concrete length. For example, at position 1, it
	 * is the list of patterns of size 1. at position 2, it is the list of patterns
	 * of size 2 ... etc.
	 * 
	 * @return the list of pattern lists.
	 */
	public List<List<Pattern>> getLevels() {
		List<List<Pattern>> view = new ArrayList<>(levels.size());
		for (List<Pattern> level : levels) {
			view.add(Collections.unmodifiableList(level));
		}
		return Collections.unmodifiableList(view);
	}

	/**
	 * It returns the total number of sequences
	 * 
	 * @return the total number of sequences
	 */
	public int size() {
		return numberOfFrequentSequences;
	}

	/**
	 * Method to sort each level of the structure
	 */
	public void sort() {
		for (List<Pattern> level : levels) {
			Collections.sort(level);
		}
	}

	/**
	 * It completely deletes a concrete level of sequences
	 * 
	 * @param i the level index to delete
	 */
	public void delete(int i) {
		if (i >= 0 && i < levels.size()) {
			List<Pattern> level = levels.get(i);
			numberOfFrequentSequences -= level.size();
			level.clear();
		}
	}

	/**
	 * It clears the structure
	 */
	public void clear() {
		for (List<Pattern> level : levels) {
			level.clear();
		}
		levels.clear();
		numberOfFrequentSequences = 0;
	}
}