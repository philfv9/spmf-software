package ca.pfv.spmf.test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.Item;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.Pair;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.PseudoSequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.PseudoSequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.abstractions.Abstraction_Generic;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.abstractions.ItemAbstractionPair;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.creators.ItemAbstractionPairCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.patterns.Pattern;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.savers.Saver;

/**
 * This is an the real execution of PrefixSpan algorithm. The main methods of
 * this class are called from class AlgoPrefixSpan_AGP, and the main loop of the
 * algorithm is executed here.
 * 
 * NOTE: This implementation saves the pattern to a file as soon as they are
 * found or can keep the pattern into memory, depending on what the user choose.
 *
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
 * @author agomariz
 */
class RecursionSPM_FC_P {

	/**
	 * Abstraction creator
	 */
	private AbstractionCreator abstractionCreator;
	/**
	 * Saver, got from Class AlgoPrefixSpan where the user has already chosen where
	 * he wants to keep the results.
	 */
	private Saver saver;
	/**
	 * absolute minimum support.
	 */
	private long minSupportAbsolute;
	/**
	 * Original pseudosequence database (without infrequent items)
	 */
	private PseudoSequenceDatabase pseudoDatabase;
	public SequenceDatabase originalDataset;
	/**
	 * Map which match the frequent items with their appearances
	 */
	private Map<Item, BitSet> mapSequenceID;
	/**
	 * Number of frequent items found by PrefixSpan
	 */
	private int numberOfFrequentPatterns = 0;

	int max_sequence_length = 398;
	double k1 = 13.2667;
	double max_min = 547;
	double k3 = 0.14;
	double k2 = 0.6;
	double each_length_constraint_sup = 0;
	double each_vality_constraint_sup;
	int count = 0;
	double average = 0;
	String str_primaryitemsettime;
	String str_time_flag;
	int time_flag;
	String str_time;
	double time_day;
	double tempdistance;

	double alpha;
	double beta;
	double gamma;

	/**
	 * Standard constructor
	 * 
	 * @param abstractionCreator the abstraction creator
	 * @param saver              The saver for correctly save the results where the
	 *                           user wants
	 * @param minSupportAbsolute The absolue minimum support
	 * @param pseudoDatabase     The original pseudoSequence database (without
	 *                           frequent items)
	 * @param mapSequenceID      Map which match the frequent items with their
	 *                           appearances
	 * @param gamma 
	 * @param beta2 
	 * @param alpha2 
	 * @param support 
	 */
	public RecursionSPM_FC_P(AbstractionCreator abstractionCreator, Saver saver, long minSupportAbsolute,
			PseudoSequenceDatabase pseudoDatabase, Map<Item, BitSet> mapSequenceID, SequenceDatabase originalDataset, double support, double alpha2, double beta2, double gamma) {
		this.abstractionCreator = abstractionCreator;
		this.saver = saver;
		this.minSupportAbsolute = minSupportAbsolute;
		this.pseudoDatabase = pseudoDatabase;
		this.mapSequenceID = mapSequenceID;
		this.originalDataset = originalDataset;
		this.alpha = alpha2;
		this.beta = beta2;
		this.gamma = gamma;
	}

	/**
	 * It executes the actual PrefixSpan Algorithm
	 * 
	 * @param keepPatterns Flag indicating if the user wants to keep the results or
	 *                     he is just interested in the number of frequent patterns
	 * @param verbose      Flag for debugging purposes
	 */
	public void execute(boolean keepPatterns, boolean verbose) {

		List<Item> keySetList = new ArrayList<Item>(mapSequenceID.keySet());
		Collections.sort(keySetList);
		for (Item item : keySetList) {
			PseudoSequenceDatabase projectedContext = makePseudoProjections(item, pseudoDatabase,
					abstractionCreator.CreateDefaultAbstraction(), true);
			ItemAbstractionPair pair = new ItemAbstractionPair(item, abstractionCreator.CreateDefaultAbstraction());
			Pattern prefix = new Pattern(pair);
			prefix.setAppearingIn((BitSet) ((mapSequenceID.get(item).clone())));
			// length

			double total_length_constraint_sup = 0;
			// vality
			double local_total_vality_constraint_sup = 0;

			for (int m = prefix.getAppearingIn().nextSetBit(0); m >= 0; m = prefix.getAppearingIn().nextSetBit(m + 1)) {
				// length
				each_length_constraint_sup = Math
						.exp((-1.0) * (1.0 * k1 / max_sequence_length) * originalDataset.SItemTime.get(m - 1).size());
				total_length_constraint_sup = total_length_constraint_sup + each_length_constraint_sup;
				// discrete vality
				int low_num = 0;
				str_primaryitemsettime = Long.toString(originalDataset.SItemTime.get(m - 1).get(item));
				str_time_flag = str_primaryitemsettime.substring(str_primaryitemsettime.length() - 1,
						str_primaryitemsettime.length());
				time_flag = Integer.parseInt(str_time_flag);
				if (time_flag < 1) {
					low_num++;
				}
				each_vality_constraint_sup = Math.exp((-1.0 * k3) * low_num);
				local_total_vality_constraint_sup += each_vality_constraint_sup;
			}
			// length vality discrete
			prefix.total_vality_constraint_sup = local_total_vality_constraint_sup;
			prefix.total_length_constraint_sup = total_length_constraint_sup;
			prefix.total_discrete_constraint_sup = prefix.getAppearingIn().cardinality();
			prefix.total_three_constraint_integration_sup = alpha * prefix.total_length_constraint_sup
					+ beta * prefix.total_discrete_constraint_sup + gamma * prefix.total_vality_constraint_sup;

			if (keepPatterns) {
				// We keep the 1-patterns if the flag is active
				saver.savePattern(prefix);
			}
			// We update the number of frequent patterns
			numberOfFrequentPatterns++;
			if (projectedContext != null && projectedContext.size() > minSupportAbsolute) { // 鎴戝幓鎺変簡绛変簬鍙�
				prefixSpanLoop(prefix, 2, projectedContext, keepPatterns, verbose);
			}
		}
	}

	/**
	 * It projects the database given as parameter
	 * 
	 * @param item        The item from which we make the projection
	 * @param database    The database where we make the projection
	 * @param abstraction Abstraction associated with the item to project
	 * @param firstTime   Flag that points out if it the first time that
	 * @return The new projected database
	 */
	private PseudoSequenceDatabase makePseudoProjections(Item item, PseudoSequenceDatabase database,
			Abstraction_Generic abstraction, boolean firstTime) {
		// The projected pseudo-database
		PseudoSequenceDatabase newProjectedDatabase = new PseudoSequenceDatabase();
		List<PseudoSequence> pseudoSequences = database.getPseudoSequences();
		for (int sequenceIndex = 0; sequenceIndex < pseudoSequences.size(); sequenceIndex++) { // for each sequence
			PseudoSequence sequence = pseudoSequences.get(sequenceIndex);
			int potentialSize = newProjectedDatabase.size() + pseudoSequences.size() - sequenceIndex;
			if (potentialSize < minSupportAbsolute) {
				return null;
			}
			/*
			 * Flag indicating if the current sequence has already been projected for the
			 * new projected database
			 */
			boolean alreadyProjected = false;
			// Initialization of the new projected sequence for the current one
			PseudoSequence newSequence = null;
			// Initialization of the number of projections done in the current sequence
			int numberOfProjections = 0;
			// Set keeping the projections already done
			Set<Integer> projectionsAlreadyMade = new HashSet<Integer>();

			for (int k = 0; k < sequence.numberOfProjectionsIncluded(); k++) {
				int sequenceSize = sequence.size(k);
				for (int i = 0; i < sequenceSize; i++) {
					// we get the index ofthe given item to project in current the itemset
					int index = sequence.indexOf(k, i, item);
					if (index != -1 && (firstTime || (abstraction.compute(sequence, k, i)))) {
						int itemsetSize = sequence.getSizeOfItemsetAt(k, i);
						if (index != itemsetSize - 1) {
							// If this sequence has not been yet projected
							if (!alreadyProjected) {
								// A new pseudosequence is created starting from the next point to the found
								// item
								newSequence = new PseudoSequence(sequence.getRelativeTimeStamp(i, k), sequence, i,
										index + 1, k);
								projectionsAlreadyMade.add(sequence.getFirstItemset(k) + i);
								if (newSequence.size(numberOfProjections) > 0) {
									// we increase the number of projections
									numberOfProjections++;
									// And we add the new projected sequence to the new database
									newProjectedDatabase.addSequence(newSequence);

								}
								/*
								 * We set the flag to true, indicating that the current sequence has been
								 * already projected
								 */
								alreadyProjected = true;
							} else {
								/*
								 * If the sequence is already projected and the projection point has not been
								 * previously used
								 */
								if (projectionsAlreadyMade.add(sequence.getFirstItemset(k) + i)) {
									newSequence.addProjectionPoint(k, sequence.getRelativeTimeStamp(i, k), sequence, i,
											index + 1);

								}
							}
							/*
							 * if the found item is the last item of the sequence and the item where it is,
							 * it is not the last itemset of the sequence
							 */
						} else if ((i != sequenceSize - 1)) {
							// and has not been yet projected
							if (!alreadyProjected) {

								newSequence = new PseudoSequence(sequence.getRelativeTimeStamp(i, k), sequence, i + 1,
										0, k);

								projectionsAlreadyMade.add(sequence.getFirstItemset(k) + i);

								if (itemsetSize > 0 && newSequence.size(numberOfProjections) > 0) {
									// we increase the number of projections
									numberOfProjections++;
									// And we add the new projected sequence to the new database
									newProjectedDatabase.addSequence(newSequence);

								}
								/*
								 * We set the flag to true, indicating that the current sequence has been
								 * already projected
								 */
								alreadyProjected = true;
							} else {

								if (projectionsAlreadyMade.add(sequence.getFirstItemset(k) + i)) {

									newSequence.addProjectionPoint(k, sequence.getRelativeTimeStamp(i, k), sequence,
											i + 1, 0);
								}
							}
						}
					}
				}
			}
		}
		return newProjectedDatabase;
	}

	/**
	 * Method that executes the main loop of prefixSpan for all the patterns with a
	 * size greater than 1
	 * 
	 * @param prefix       prefix from which we made the projected database and
	 *                     where the frequent items that we find will be added
	 * @param k            size of patterns that are going to be generated
	 * @param context      prefix-projected databases
	 * @param keepPatterns flag indicating if we want to keep the output or we are
	 *                     interesting in just the number of frequent patterns
	 * @param verbose      flag for debuggin purposes
	 */
	private void prefixSpanLoop(Pattern prefix, int k, PseudoSequenceDatabase context, boolean keepPatterns,
			boolean verbose) {
		Set<Pair> pairs = abstractionCreator.findAllFrequentPairs(context.getPseudoSequences());
		ItemAbstractionPairCreator pairCreator = ItemAbstractionPairCreator.getInstance();
		if (verbose) {
			StringBuilder tab = new StringBuilder();
			for (int i = 0; i < k - 2; i++) {
				tab.append('\t');
			}
		}
		for (Pair pair : pairs) {

			Pattern tempnewPrefix = prefix.clonePattern();
			ItemAbstractionPair tempnewPair = pairCreator.getItemAbstractionPair(pair.getPair().getItem(),
					abstractionCreator.createAbstractionFromAPrefix(prefix, pair.getPair().getAbstraction()));
			tempnewPrefix.add(tempnewPair);
			tempnewPrefix.setAppearingIn((BitSet) (pair.getSequencesID().clone()));
			if (tempnewPrefix.getAppearingIn().cardinality() > minSupportAbsolute) {
				double total_length_constraint_sup = 0;

				for (int m = tempnewPrefix.getAppearingIn().nextSetBit(0); m >= 0; m = tempnewPrefix.getAppearingIn()
						.nextSetBit(m + 1)) {
					// length
					each_length_constraint_sup = Math.exp(
							(-1.0 * k1) * (1.0 / max_sequence_length) * originalDataset.SItemTime.get(m - 1).size());
					total_length_constraint_sup = total_length_constraint_sup + each_length_constraint_sup;
					// validity
					List<Double> timelist = new ArrayList<Double>();
					List<Integer> flaglist = new ArrayList<Integer>();
					for (int n = 0; n < tempnewPrefix.size(); n++) {
						str_primaryitemsettime = Long.toString(
								(originalDataset.SItemTime.get(m - 1).get(tempnewPrefix.getIthElement(n).getItem())));
						str_time = str_primaryitemsettime.substring(0, str_primaryitemsettime.length() - 1);
						time_day = (Double.parseDouble(str_time) - 1) / max_min;
						str_time_flag = str_primaryitemsettime.substring(str_primaryitemsettime.length() - 1,
								str_primaryitemsettime.length());
						time_flag = Integer.parseInt(str_time_flag);
						timelist.add(time_day);
						flaglist.add(time_flag);
					}
					int low_num = 0;

					// discrete validity
					for (int p = 0; p < flaglist.size(); p++) {
						if (flaglist.get(p) < 1) {
							low_num = low_num + 1;
						}
					}
					each_vality_constraint_sup = Math.exp((-1.0 * k3) * low_num);
					// validity
					tempnewPrefix.total_vality_constraint_sup += each_vality_constraint_sup;

					// discrete
					double sum = 0;
					double discrete_q = 0;
					count = timelist.size();
					for (int i = 0; i < count; i++) {
						sum += timelist.get(i);
					}
					average = sum / count;
					for (int j = 0; j < count; j++) {
						tempdistance = timelist.get(j) - average;
						discrete_q += Math.pow(tempdistance, 2);
					}
					tempnewPrefix.total_discrete_constraint_sup += Math.exp((-1.0) / k2 * discrete_q);
				}
				// length
				tempnewPrefix.total_length_constraint_sup = total_length_constraint_sup;
				// integration
				tempnewPrefix.total_three_constraint_integration_sup = alpha * tempnewPrefix.total_length_constraint_sup
						+ beta * tempnewPrefix.total_discrete_constraint_sup
						+ gamma * tempnewPrefix.total_vality_constraint_sup;
				if (tempnewPrefix.total_three_constraint_integration_sup >= minSupportAbsolute) {
					ItemAbstractionPair newPair = pairCreator.getItemAbstractionPair(pair.getPair().getItem(),
							abstractionCreator.createAbstractionFromAPrefix(prefix, pair.getPair().getAbstraction()));
					PseudoSequenceDatabase projection = makePseudoProjections(pair.getPair().getItem(), context,
							pair.getPair().getAbstraction(), false);
					tempnewPrefix.setAppearingIn((BitSet) (pair.getSequencesID().clone()));
					// if the flag of keeping patterns if active, we keep this new pattern
					if (keepPatterns) {

						saver.savePattern(tempnewPrefix);
					}
					// update the number of frequent patterns
					numberOfFrequentPatterns++;
					// If the projection exists and has more sequences than the absolute minimum
					// support
					if (projection != null && projection.size() > minSupportAbsolute) { // 鎴戜笉瑕佺瓑浜庡彿
						// We make a recursive call to the main method
						prefixSpanLoop(tempnewPrefix, k + 1, projection, keepPatterns, verbose); // r锟絚ursion
					}
				}
			}
		}
	}

	/**
	 * It returns the number of frequent patterns.
	 * 
	 * @return the number of frequent patterns.
	 */
	public int numberOfFrequentPatterns() {
		return numberOfFrequentPatterns;
	}

	/**
	 * It clears the attributes of this class.
	 */
	public void clear() {
		if (saver != null) {
			saver.clear();
			saver = null;
		}
		if (pseudoDatabase != null) {
			pseudoDatabase.clear();
			pseudoDatabase = null;
		}
		if (mapSequenceID != null) {
			mapSequenceID.clear();
			mapSequenceID = null;
		}
	}
}
