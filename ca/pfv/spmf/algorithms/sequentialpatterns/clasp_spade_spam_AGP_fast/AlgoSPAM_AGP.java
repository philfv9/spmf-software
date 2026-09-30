package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

/*
 * Copyright Antonio Gomariz Peñalver 2013, modified by Philippe Fournier-Viger,
 * 2026 
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDList;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the SPAM algorithm, which was proposed by Ayres
 * in 2002.
 * <p>
 * All the output handling, minimum-support computation, getPatterns() and
 * clear() are shared with {@link AlgoSPADE} and {@link AlgoCMSPADE} and now
 * live in {@link AbstractVerticalAlgorithm}. This class keeps only what is
 * actually specific to SPAM.
 * 
 * @author Antonio Gomariz Peñalver
 */
public class AlgoSPAM_AGP extends AbstractVerticalAlgorithm {

	/** minimum number of items that an output pattern must contain */
	private int minimumPatternLength = 0;

	/** maximum number of items that an output pattern can contain */
	private int maximumPatternLength = Integer.MAX_VALUE;

	/**
	 * Constructor of the class that calls SPAM algorithm.
	 *
	 * @param minsupRelative Minimum support (from 0 up to 1)
	 */
	public AlgoSPAM_AGP(double minsupRelative) {
		// SPAM only ever performs a depth-first search; the dfs flag inherited
		// from AbstractSPADEAlgorithm is never read by this class.
		super(minsupRelative, true, false);
	}

	/**
	 * Sets the minimum number of items that a pattern must contain to be output.
	 *
	 * @param minimumPatternLength the minimum pattern length
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Sets the maximum number of items that a pattern can contain to be output.
	 *
	 * @param maximumPatternLength the maximum pattern length
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Actual call to SPAM algorithm. The output can be either kept or ignored.
	 * Whenever we choose to keep the patterns found, we can keep them in a file or
	 * in the main memory.
	 *
	 * @param database                  Original database in where we want to search
	 *                                  for the frequent patterns
	 * @param keepPatterns              Flag indicating if we want to keep the
	 *                                  output or not
	 * @param verbose                   Flag for debugging purposes
	 * @param outputFilePath            Path of the file in which we want to store
	 *                                  the frequent patterns; null means keep in
	 *                                  memory
	 * @param outputSequenceIdentifiers if true, sequence identifiers will be output
	 *                                  for each pattern found
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithm(SequenceDatabase database, boolean keepPatterns, boolean verbose, String outputFilePath,
			boolean outputSequenceIdentifiers) throws IOException {
		
		// We initialize where the output patterns will be saved
		initializeOutput(outputFilePath, outputSequenceIdentifiers);

		computeAbsoluteMinimumSupport(database.size());

		// reset the stats about memory usage
		MemoryLogger.getInstance().reset();

		// keeping the starting time
		start = System.currentTimeMillis();

		// We run SPAM algorithm
		runSPAM(database, keepPatterns, verbose);

		// keeping the ending time
		end = System.currentTimeMillis();

		// Search for frequent patterns: Finished
		finishOutput();
	}

	/**
	 * Method for extracting frequent sequences.
	 *
	 * @param database     The original database
	 * @param keepPatterns flag indicating if we are interested in keeping the
	 *                     output of the algorithm
	 * @param verbose      Flag for debugging purposes
	 */
	protected void runSPAM(SequenceDatabase database, boolean keepPatterns, boolean verbose) {

		// We get the equivalence classes formed by the frequent 1-patterns
		frequentItems = database.frequentItems();

		// We extract their patterns
		Collection<Pattern> size1sequences = getPatterns(frequentItems);

		// A 1-pattern is valid only if the length constraints allow a length of 1
		boolean size1Valid = minimumPatternLength <= 1 && maximumPatternLength >= 1;

		// If we want to keep the output
		if (keepPatterns && size1Valid) {
			savePatterns(size1sequences);
		}

		database = null;

		// We define the root class and insert the equivalence classes corresponding to
		// the frequent 1-patterns
		EquivalenceClass rootClass = new EquivalenceClass(null, new ArrayList<EquivalenceClass>(frequentItems));

		// We start the count of frequent patterns with the number of frequent
		// 1-patterns, if they satisfy the length constraints
		numberOfFrequentPatterns = size1Valid ? frequentItems.size() : 0;

		// We execute the search
		execute(rootClass, keepPatterns, verbose);

		// check the memory usage for statistics
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Execution of the search of frequent patterns for every member of the given
	 * equivalence class.
	 *
	 * @param equivalenceClass The equivalence class from we start to search for
	 * @param keepPatterns     Flag to indicate if we want to keep the patterns
	 *                         found
	 * @param verbose          Flag for debugging purposes
	 */
	private void execute(EquivalenceClass equivalenceClass, boolean keepPatterns, boolean verbose) {
		List<EquivalenceClass> members = equivalenceClass.getClassMembers();
		int numberOfMembersInEC = members.size();
		for (int i = 0; i < numberOfMembersInEC; i++) {

			// For each member of the given equivalence class
			EquivalenceClass ec = equivalenceClass.getIthMember(i);

			// We call to the main method of the algorithm for that equivalence class,
			// starting with a pattern length of 1
			dfsPruning(ec, members, members, i + 1, keepPatterns, 1);
		}
	}

	/**
	 * Main method of SPAM algorithm. For each processed pattern, the algorithm
	 * tries to make a s-extension first, and then, once it found all the possible
	 * s-extensions, it tries to make an i-extension. The method receives two sets
	 * of equivalence classes, one with the frequent items that can be used for
	 * making s-extensions and another one for making i-extensions.
	 *
	 * @param currentClass       The current class whose identifier we are trying to
	 *                           extend
	 * @param sequenceExtensions The set of equivalence classes used as possible
	 *                           s-extensions
	 * @param itemsetsExtensions The set of equivalence classes used as possible
	 *                           i-extensions
	 * @param beginning          The beginning index from where we can use the
	 *                           elements of itemsetsExtensions
	 * @param keepPatterns       Flag indicating if we are interested in saving the
	 *                           frequent patterns that we find
	 * @param currentLength      The number of items contained in currentClass's
	 *                           pattern
	 */
	private void dfsPruning(EquivalenceClass currentClass, List<EquivalenceClass> sequenceExtensions,
			List<EquivalenceClass> itemsetsExtensions, int beginning, boolean keepPatterns, int currentLength) {

		final int minSupAbs = minSupAbsolute;
		final IDList currentIdList = currentClass.getIdList();

		// The class identifier: candidate extensions are built directly from it via
		// cloneAndAppend().
		final Pattern classIdentifier = currentClass.getClassIdentifier();

		// Any pattern extended from currentClass will have this many items
		final int extensionLength = currentLength + 1;
		// Whether extending is still allowed under the maximum length constraint
		final boolean canExtend = extensionLength <= maximumPatternLength;

		// Initialization of new sets
		List<EquivalenceClass> new_sequenceExtension = new ArrayList<EquivalenceClass>(sequenceExtensions.size());
		List<EquivalenceClass> new_itemsetExtension = new ArrayList<EquivalenceClass>(
				Math.max(0, itemsetsExtensions.size() - beginning));
		List<EquivalenceClass> new_classes = new ArrayList<EquivalenceClass>(sequenceExtensions.size());

		// For all the elements of sequenceExtensions
		for (EquivalenceClass eq : sequenceExtensions) {
			Pattern eqIdentifier = eq.getClassIdentifier();

			// We read the item id and relation flag of eq's last element
			int newItemId = eqIdentifier.getLastItemId();
			boolean newEqualRelation = eqIdentifier.getLastEqualRelation();

			/*
			 * We make the join operation between both patterns in order to know the
			 * appearances of the new pattern and its support.
			 */
			IDList newIdList = currentIdList.join(eq.getIdList(), false, minSupAbs);
			int support = newIdList.getSupport();

			// If the new pattern is frequent and its length does not exceed the maximum
			if (support >= minSupAbs && canExtend) {

				Pattern extension = classIdentifier.cloneAndAppend(newItemId, newEqualRelation);

				// We insert it its appearances
				newIdList.setAppearingSequences(extension);

				// The support is already known from the join: cache it on the pattern so
				// output never needs to fall back to a full BitSet popcount scan.
				extension.setCachedSupport(support);

				// We only count and output the pattern if it meets the minimum length
				if (extensionLength >= minimumPatternLength) {
					numberOfFrequentPatterns++;
					// and we keep the pattern if the flag is activated
					if (keepPatterns) {
						savePattern(extension);
					}
				}

				// We create a new class for the new pattern
				EquivalenceClass newClass = new EquivalenceClass(extension);

				// we inserted the IdList that we computed
				newClass.setIdList(newIdList);

				// And we insert the new class in the set of new classes
				new_classes.add(newClass);

				// Create the equivalence class
				EquivalenceClass newEq = new EquivalenceClass(eqIdentifier, newIdList);
				
				// And we add this new class as a possible future s-extension of the new pattern
				new_sequenceExtension.add(newEq);
			} else {
				// The candidate did not meet the minimum support or exceeds the maximum
				// length: return its id-list to its reuse pool instead of letting it become
				// garbage.
				newIdList.recycle();
			}
		}

		int sequenceExtensionSize = new_sequenceExtension.size();
		
		// For all the elements valuables as future s-extensions
		for (int i = 0; i < sequenceExtensionSize; i++) {
			// we get the new pattern
			EquivalenceClass newClass = new_classes.get(i);

			// Make a recursive call to dfsPruning with the new sequence extension.
			//  Besides we establish the same set as the set which we will make the
			// i-extensions, but beginning from the (i+1)-th element
			dfsPruning(newClass, new_sequenceExtension, new_sequenceExtension, i + 1, keepPatterns, extensionLength);
			
			// Once we had finished the search for this patterns and their children, we can
			// remove that class (and its descendants) from the memory
			newClass.clear();
		}

		// We clear the set of the new classes discovered since we need to store those
		// that we will find making i-extensions
		new_classes.clear();

		// From the beginning index to the last equivalence class appearing in the
		// itemset extension set.
		int itemsetExtensionsSize = itemsetsExtensions.size();
		for (int k = beginning; k < itemsetExtensionsSize; k++) {
			EquivalenceClass eq = itemsetsExtensions.get(k);
			Pattern eqIdentifier = eq.getClassIdentifier();
			
			// We only need the item id here
			int itemId = eqIdentifier.getLastItemId();

			// We make the join operation between both patterns to know the
			// appearances of the new pattern and its support.
			IDList newIdList = currentIdList.join(eq.getIdList(), true, minSupAbs);
			int support = newIdList.getSupport();
			
			// If the new pattern is frequent and its length does not exceed the maximum
			if (support >= minSupAbs && canExtend) {
				Pattern extension = classIdentifier.cloneAndAppend(itemId, true);

				// We insert it its appearances
				newIdList.setAppearingSequences(extension);
				extension.setCachedSupport(support);
				
				// We only count and output the pattern if it meets the minimum length
				if (extensionLength >= minimumPatternLength) {
					numberOfFrequentPatterns++;
					// and we keep the pattern if the flag is activated
					if (keepPatterns) {
						savePattern(extension);
					}
				}

				// We create a new class for the new pattern
				EquivalenceClass newClass = new EquivalenceClass(extension);
				
				// we inserted the IdList that we computed
				newClass.setIdList(newIdList);
				
				// And we insert the new class in the set of new classes
				new_classes.add(newClass);

				// Create the new equivalence class
				EquivalenceClass newEq = new EquivalenceClass(eqIdentifier, newIdList);
				
				// And we add this new class as a possible future i-extension of the new
				//pattern.
				new_itemsetExtension.add(newEq);
			} else {
				// The candidate did not meet the minimum support or exceeds the maximum
				// length: return its id-list to its reuse pool.
				newIdList.recycle();
			}
		}

		int itemsetExtensionSize = new_itemsetExtension.size();
		
		// For all the elements valuables as future i-extensions
		for (int i = 0; i < itemsetExtensionSize; i++) {
			// we get the new pattern
			EquivalenceClass newClass = new_classes.get(i);
			
			// Make a recursive call to dfsPruning with the new itemset extension.
			// The beginning of the itemset extension set will be starting from the (i+1)-th
			// element.
			dfsPruning(newClass, new_sequenceExtension, new_itemsetExtension, i + 1, keepPatterns, extensionLength);

			newClass.clear();
		}
	}

	/**
	 * Returns a string with statistics about the last algorithm execution.
	 *
	 * @return the statistics string
	 */
	public String printStatistics() {
		StringBuilder sb = new StringBuilder(200);
		sb.append("=============  SPAM-AGP v.2.67  - STATISTICS =============\n Total time ~ ");
		sb.append(getRunningTime());
		sb.append(" ms\n");
		sb.append(" Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
		sb.append('\n');
		sb.append(" Max memory (mb):");
		sb.append(MemoryLogger.getInstance().getMaxMemory());
		sb.append('\n');
		sb.append(getOutputDescription());
		sb.append("\n===================================================\n");
		return sb.toString();
	}

}