package ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p;

/*
 * Copyright  Wei Song et al., Antonio Gomariz Peñalver, 2013
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
 */
import java.io.IOException;
import java.util.BitSet;
import java.util.Map;

import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.Item;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.PseudoSequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.PseudoSequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.Sequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.savers.Saver;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.savers.SaverIntoFile;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.savers.SaverIntoMemory;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the SPMF-FC_P algorithm proposed by Wei Song et
 * al. in this paper: <br/>
 * <br/>
 * Song, W., Ye, W., Fournier-Viger, P. (2022). Mining sequential patterns with
 * flexible constraints from MOOC data. Applied Intelligence, to appear. DOI :
 * 10.1007/s10489-021-03122-7
 * 
 * NOTE: This implementation saves the pattern to a file as soon as they are
 * found or can keep the pattern into memory if no output path is provided by
 * the user.
 * 
 * @author Antonio Gomariz Pe帽alver
 */
public class AlgoSPM_FC_P {

	/**
	 * the minimum support threshold as a value in [0,1]
	 */
	protected double minSupRelative;
	/**
	 * The minimum support relative threshold, i.e. the minimum number of sequences
	 * where the patterns have to be
	 */
	protected double minSupAbsolute;
	/**
	 * original sequential database to be used for sequential patterns extraction
	 */
	public SequenceDatabase originalDataset;
	/**
	 * Saver variable to decide where the user want to save the results, if it the
	 * case
	 */
	Saver saver = null;
	/**
	 * Start and end points in order to calculate the overall time taken by the
	 * algorithm
	 */
	protected long start, end;
	/**
	 * The abstraction creator
	 */
	private AbstractionCreator abstractionCreator;
	/**
	 * Number of frequent patterns found by the algorithm
	 */
	private int numberOfFrequentPatterns = 0;

	/**
	 * Standard constructor. It takes the minimum support threshold (from 1 up to 0)
	 * and an abstraction creator
	 * 
	 * @param minsupRelative the relative minimum support threshold
	 * @param creator        the abstraction creator
	 */
	public AlgoSPM_FC_P(double minsupRelative, AbstractionCreator creator) {
		this.minSupRelative = minsupRelative;
		this.abstractionCreator = creator;
	}

	/**
	 * Method that starts the execution of the algorithm.
	 * 
	 * @param database                  The original database in which we apply
	 *                                  PrefixSpan
	 * @param keepPatterns              Flag indicating if the user want to keep the
	 *                                  frequent patterns or he just want the amount
	 *                                  of them
	 * @param verbose                   Flag for debugging purposes
	 * @param outputFilePath            Path pointing out to the file where the
	 *                                  output, composed of frequent patterns, has
	 *                                  to be kept. If, conversely, this parameter
	 *                                  is null, we understand that the user wants
	 *                                  the output in the main memory
	 * @param outputSequenceIdentifiers if true, sequences ids will be output for
	 *                                  each pattern
	 * @param gamma
	 * @param beta
	 * @param alpha
	 * @param support
	 * @throws IOException
	 */
	public void runAlgorithm(SequenceDatabase database, boolean keepPatterns, boolean verbose, String outputFilePath,
			boolean outputSequenceIdentifiers, double support, double alpha, double beta, double gamma)
			throws IOException {
		minSupAbsolute = (int) Math.ceil(minSupRelative * database.size());
		if (this.minSupAbsolute == 0) { // protection
			this.minSupAbsolute = 1;
		}
		MemoryLogger.getInstance().reset();
		start = System.currentTimeMillis();
		prefixSpan(database, keepPatterns, verbose, outputFilePath, outputSequenceIdentifiers, support, alpha, beta,
				gamma);
		end = System.currentTimeMillis();
		saver.finish();
	}

	/**
	 * Method that executes the first steps before calling the actual main method of
	 * PrefixSpan. In particular, the original database is fully converted to a
	 * pseudosequece database, removing the infrequent items that appeared in the
	 * original dabase
	 * 
	 * @param database                  The original database
	 * @param keepPatterns              Flag indicating if the user want to keep the
	 *                                  frequent patterns or he just want the amount
	 *                                  of them
	 * @param verbose                   Flag for debugging purposes
	 * @param outputFilePath            Path pointing out to the file where the
	 *                                  output, composed of frequent patterns, has
	 *                                  to be kept. If, conversely, this parameter
	 *                                  is null, we understand that the user wants
	 *                                  the output in the main memory
	 * @param outputSequenceIdentifiers if true, sequences ids will be output for
	 *                                  each pattern
	 * @param gamma
	 * @param beta
	 * @param alpha
	 * @param support
	 * @throws IOException
	 */
	protected void prefixSpan(SequenceDatabase database, boolean keepPatterns, boolean verbose, String outputFilePath,
			boolean outputSequenceIdentifiers, double support, double alpha, double beta, double gamma)
			throws IOException {
		// If we do no have any file path
		if (outputFilePath == null) {
			saver = new SaverIntoMemory(outputSequenceIdentifiers);
		} else {
			saver = new SaverIntoFile(outputFilePath, outputSequenceIdentifiers);
		}
		Map<Item, BitSet> mapSequenceID = database.getFrequentItems();
		PseudoSequenceDatabase pseudoDatabase = projectInitialDatabase(database, mapSequenceID, (int) minSupAbsolute);
		RecursionSPM_FC_P algorithm = new RecursionSPM_FC_P(abstractionCreator, saver, (int) minSupAbsolute,
				pseudoDatabase, mapSequenceID, database, support, alpha, beta, gamma);
		algorithm.execute(keepPatterns, verbose);
		numberOfFrequentPatterns = algorithm.numberOfFrequentPatterns();
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Method to get the outlined information about the search for frequent
	 * sequences by means of PrefixSpan algorithm as a string.
	 * 
	 * @return a string
	 */
	public String printStatistics() {
		StringBuilder sb = new StringBuilder(200);
		sb.append("=============  SPM_FC_P v.2.58 - STATISTICS =============" + System.lineSeparator()
				+ " Total time ~ ");
		sb.append(getRunningTime());
		sb.append(" ms" + System.lineSeparator());
		sb.append(" Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
		sb.append(System.lineSeparator());
		sb.append(" Max memory (mb):");
		sb.append(MemoryLogger.getInstance().getMaxMemory());
		sb.append(System.lineSeparator());
		sb.append("=================================================== " + System.lineSeparator());
		sb.append(saver.print());
		sb.append(System.lineSeparator());
		return sb.toString();
	}

	public String printsimpleStatistics() {
		StringBuilder sb = new StringBuilder(200);
		sb.append("=============  SPM_FC_P v.2.58 - STATISTICS =============" + System.lineSeparator()
				+ " Total time ~ ");
		sb.append(getRunningTime());
		sb.append(" ms" + System.lineSeparator());
		sb.append(" Frequent sequences count : ");
		sb.append(numberOfFrequentPatterns);
		sb.append(System.lineSeparator());
		sb.append(" Max memory (mb):");
		sb.append(MemoryLogger.getInstance().getMaxMemory());
		sb.append(System.lineSeparator());
		// sb.append(saver.print());
		// sb.append('\n');
		sb.append("===================================================" + System.lineSeparator());
		return sb.toString();
	}

	/**
	 * Get the number of frequent patterns found.
	 * 
	 * @return the number of frequent patterns.
	 */
	public int getNumberOfFrequentPatterns() {
		return numberOfFrequentPatterns;
	}

	/**
	 * It gets the time spent by the algoritm in its execution.
	 * 
	 * @return the time spent (long).
	 */
	public long getRunningTime() {
		return (end - start);
	}

	/**
	 * It gets the absolute minimum support, i.e. the minimum number of database
	 * sequences where a pattern has to appear
	 * 
	 * @return the minimum support (double)
	 */
	public double getAbsoluteMinSupport() {
		return minSupAbsolute;
	}

	/**
	 * It projects the initial database converting each original sequence to
	 * pseudosequences in order to enable the later pseudoprojections in the main
	 * loop of PrefixSpan
	 * 
	 * @param database           The original Database
	 * @param mapSequenceID      Map with all the items appearing in the original
	 *                           database, and a bitset pointing out in which
	 *                           sequences the items appear
	 * @param minSupportAbsolute The absolute minimum support
	 * @return
	 */
	private PseudoSequenceDatabase projectInitialDatabase(SequenceDatabase database, Map<Item, BitSet> mapSequenceID,
			long minSupportAbsolute) {
		PseudoSequenceDatabase initialContext = new PseudoSequenceDatabase();
		// For each database sequence
		for (Sequence sequence : database.getSequences()) {
			Sequence optimizedSequence = sequence.cloneSequenceMinusItems(mapSequenceID, minSupportAbsolute);
			if (optimizedSequence.size() != 0) {
				PseudoSequence pseudoSequence = new PseudoSequence(0, optimizedSequence, 0, 0);
				initialContext.addSequence(pseudoSequence);
			}
		}
		return initialContext;
	}

	/**
	 * It clears all the attributes of AlgoPrefixSpan class
	 */
	public void clear() {
		if (originalDataset != null) {
			originalDataset.clear();
			originalDataset = null;
		}
		if (saver != null) {
			saver.clear();
			saver = null;
		}
		abstractionCreator = null;
	}
}
