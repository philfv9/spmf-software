package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDList;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDListPools;
/*
 * Copyright Antonio Gomariz Peñalver 2013, modified by Philippe Fournier-Viger,
 * 2013
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
 * Common infrastructure shared by every several vertical sequential pattern
 * mining algorithms of this package (SPADE, CM-SPADE, SPAM_AGP...).
 * <p>
 * This class intentionally does NOT declare execute()/generateCandidates().Only
 * the plumbing that is truly identical across algorithms lives here.
 *
 * @author agomariz, Philippe Fournier-Viger
 */
public abstract class AbstractVerticalAlgorithm {

	/** Minimum support threshold, expressed as a relative value between 0 and 1. */
	protected double minSupRelative;

	/** Minimum support threshold, expressed as a number of sequences. */
	public int minSupAbsolute;

	/** Flag: true = depth-first search; false = breadth-first search. */
	protected boolean dfs;

	/** Start timestamp from the last run. */
	public long start;

	/** End timestamp from the last run. */
	public long end;

	/** Equivalence classes of all frequent items. */
	protected List<EquivalenceClass> frequentItems;

	/** Number of frequent patterns found (single-threaded run). */
	protected int numberOfFrequentPatterns;

	/** Number of join (intersection) operations performed (single-threaded run). */
	protected int intersectionCounter;

	/** True while running in parallel mode; selects which counters get updated. */
	protected boolean parallelMode;

	/** Thread-safe counter of frequent patterns found (parallel run). */
	protected final AtomicInteger frequentPatternsCounter = new AtomicInteger();

	/** Thread-safe counter of join operations performed (parallel run). */
	protected final AtomicInteger joinCounter = new AtomicInteger();

	/**
	 * Writer used to save patterns directly to a file, or null if kept in memory.
	 */
	private BufferedWriter outputWriter;

	/** Output file path, kept only for display in statistics. */
	private String outputFilePathUsed;

	/** In-memory container of the patterns found, or null if saved to a file. */
	private Sequences patternsInMemory;

	/** Flag: if true, sequence identifiers are output alongside each pattern. */
	protected boolean outputSequenceIdentifiers;
	
	/** Flag: algorithms that find closed patterns */
	protected boolean findClosedPatterns = false;

	/**
	 * @param support Minimum support (from 0 up to 1)
	 * @param dfs     true for a depth-first search, false for breadth-first
	 * @param findClosed find closed patterns or not
	 */
	protected AbstractVerticalAlgorithm(double support, boolean dfs, boolean findClosed) {
		this.minSupRelative = support;
		this.dfs = dfs;
		this.findClosedPatterns = findClosed;
	}

	/** Initializes the pattern output destination, either a file or memory. */
	protected void initializeOutput(String outputFilePath, boolean outputSequenceIdentifiers) throws IOException {
		this.outputSequenceIdentifiers = outputSequenceIdentifiers;
		if (outputFilePath == null) {
			patternsInMemory = new Sequences("FREQUENT SEQUENTIAL PATTERNS");
		} else {
			outputFilePathUsed = outputFilePath;
			outputWriter = new BufferedWriter(new FileWriter(outputFilePath));
			if(findClosedPatterns) {

				outputWriter.write("@FILETYPE=\"Frequent closed sequential patterns\"");
			}else {

				outputWriter.write("@FILETYPE=\"Frequent sequential patterns\"");
			}
			outputWriter.newLine();
			outputWriter.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			outputWriter.newLine();
		}
	}

	/** Saves a single pattern to the file or to memory. */
	protected void savePattern(Pattern p) {
		if (outputWriter != null) {
			try {
				p.writeToFile(outputWriter, outputSequenceIdentifiers);
				outputWriter.newLine();
			} catch (IOException ex) {
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
			}
		} else {
			patternsInMemory.addSequence(p, p.size());
		}
	}

	/** Saves a collection of patterns, one at a time. */
	protected void savePatterns(Collection<Pattern> patterns) {
		for (Pattern pattern : patterns) {
			savePattern(pattern);
		}
	}

	/**
	 * Saves the given pattern, synchronizing only in parallel mode (an uncontended
	 * synchronized block still isn't free, and this runs once per emitted pattern).
	 */
	protected void keepPattern(Pattern pattern) {
		if (parallelMode) {
			synchronized (this) {
				savePattern(pattern);
			}
		} else {
			savePattern(pattern);
		}
	}

	/**
	 * Finalizes the output: closes the file writer, or sorts the in-memory
	 * patterns.
	 */
	protected void finishOutput() {
		if (outputWriter != null) {
			try {
				outputWriter.close();
			} catch (IOException ex) {
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
			}
		} else if (patternsInMemory != null) {
			patternsInMemory.sort();
		}
	}

	/** Clears the output resources held by this algorithm instance. */
	protected void clearOutput() {
		outputWriter = null;
		if (patternsInMemory != null) {
			patternsInMemory.clear();
			patternsInMemory = null;
		}
	}

	/**
	 * Returns a description of where the patterns were saved, for statistics.
	 * Correctly handles both the file case and the in-memory case (a previous,
	 * duplicated version of this method in CM-SPADE omitted the file-case check and
	 * would NPE when statistics were printed after a file-output run).
	 */
	protected String getOutputDescription() {
		if (outputWriter != null) {
			return "Content at file " + outputFilePathUsed;
		}
		return patternsInMemory.toStringToFile(outputSequenceIdentifiers);
	}

	/** Computes minSupAbsolute from minSupRelative and the database size. */
	protected void computeAbsoluteMinimumSupport(int databaseSize) {
		minSupAbsolute = (int) Math.ceil((minSupRelative * databaseSize) - 0.0000001);
		if (minSupAbsolute < 1) {
			minSupAbsolute = 1;
		}
	}

	/**
	 * Inserts the given equivalence class as a member of whichever of the two
	 * candidate classes is its prefix.
	 */
	protected void insertClassByPrefix(EquivalenceClass eq, EquivalenceClass eq_X, EquivalenceClass eq_Y) {
		Pattern identifier = eq.getClassIdentifier();
		if (eq_X.getClassIdentifier().isPrefix(identifier)) {
			eq_X.addClassMember(eq);
		} else {
			eq_Y.addClassMember(eq);
		}
	}

	/**
	 * Returns the patterns that are the identifiers of the given equivalence
	 * classes.
	 */
	protected Collection<Pattern> getPatterns(List<EquivalenceClass> equivalenceClasses) {
		List<Pattern> patterns = new ArrayList<Pattern>(equivalenceClasses.size());
		for (EquivalenceClass equivalenceClass : equivalenceClasses) {
			patterns.add(equivalenceClass.getClassIdentifier());
		}
		return patterns;
	}

	/**
	 * Returns an equivalence class's id-list to its pool and clears the reference.
	 * Safe once every join involving its siblings has been done.
	 */
	protected void recycleIdList(EquivalenceClass eq) {
		IDList idList = eq.getIdList();
		if (idList != null) {
			idList.recycle();
			eq.setIdList(null);
		}
	}

	/**
	 * Executes the join operation over the id-lists of the two equivalence classes
	 * given as parameters. Identical for every id-list based algorithm of this
	 * package: the direction/nature of the join only depends on the shape of the
	 * candidate pattern, never on how it was pruned or generated.
	 */
	protected IDList join(Pattern extension, EquivalenceClass equivalenceClass_i, EquivalenceClass equivalenceClass_j,
			int minSupport) {
		int lastItemId = extension.getLastItemId();
		boolean lastEqualRelation = extension.getLastEqualRelation();

		if (lastEqualRelation) {
			int lastButOneItemId = extension.getLastButOneItemId();
			boolean lastButOneEqualRelation = extension.getLastButOneEqualRelation();
			if (!(lastButOneItemId == lastItemId && lastButOneEqualRelation == lastEqualRelation)) {
				return equivalenceClass_i.getIdList().join(equivalenceClass_j.getIdList(), true, minSupport);
			}
		} else {
			Pattern jIdentifier = equivalenceClass_j.getClassIdentifier();
			if (jIdentifier.getLastItemId() == lastItemId && jIdentifier.getLastEqualRelation() == lastEqualRelation) {
				return equivalenceClass_i.getIdList().join(equivalenceClass_j.getIdList(), false, minSupport);
			} else {
				return equivalenceClass_j.getIdList().join(equivalenceClass_i.getIdList(), false, minSupport);
			}
		}
		return null;
	}

	/**
	 * Increments the join-operation counter, using the plain field directly in
	 * single-threaded mode to avoid paying for CAS synchronization on every single
	 * join, which is the hottest counting site in the search.
	 */
	protected void incrementJoinCounter() {
		if (parallelMode) {
			joinCounter.incrementAndGet();
		} else {
			intersectionCounter++;
		}
	}

	/**
	 * Increments the frequent-pattern counter (see
	 * {@link #incrementJoinCounter()}).
	 */
	protected void incrementPatternCounter() {
		if (parallelMode) {
			frequentPatternsCounter.incrementAndGet();
		} else {
			numberOfFrequentPatterns++;
		}
	}

	/** @return the total number of frequent patterns found by the last run */
	public int getNumberOfFrequentPatterns() {
		return numberOfFrequentPatterns;
	}

	/** @return the total running time of the last run, in milliseconds */
	public long getRunningTime() {
		return (end - start);
	}

	/** @return the absolute minimum support used by the algorithm */
	public double getAbsoluteMinimumSupport() {
		return minSupAbsolute;
	}

	/** Clears all the attributes held by this algorithm instance. */
	public void clear() {
		if (frequentItems != null) {
			frequentItems.clear();
		}
		clearOutput();
		IDListPools.clearAll();
	}
}