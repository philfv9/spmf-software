package ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger, Yuechun Li
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * An implementation of the VertTIRP algorithm, which was proposed in this
 * paper: <br/>
 * <br/>
 * 
 * Natalia Mordvanyuk, Beatriz López, Albert Bifet: vertTIRP: Robust and
 * efficient vertical frequent time interval-related pattern mining. Expert
 * Syst. Appl. 168: 114276 (2021) <br/>
 * <br/>
 * <br/>
 * * and the FastTIRP algorithm from this paper:<br/>
 * <br/>
 * 
 * Fournier-Viger, P. Li, Y., Nawaz, M. S., He, Y. (2022) FastTIRP: Efficient
 * discovery of Time-Interval Related Patterns. Proc. of 10th Intern. Conf. on
 * Big Data Analytics (BDA 2022), Springer, to appear.
 * 
 * 
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public class AlgoFastTIRP {

	/** the runtime of the algorithm (for statistics) */
	protected long runtime = 0;

	/** the maximum memory usage of the algorithm (for statistics) */
	protected double maxMemory = 0;

	/** the number of patterns generated (for statistics) */
	protected int patternCount = 0;

	/** Number of sequences in the database (for statistics) **/
	protected int numberOfSequences = -1;

	/** Number of distinct event types in the database (for statistics) **/
	protected int numberOfEventTypes = -1;

	/** Number of time interval in the database (for statistics) **/
	protected int numberOfTimeIntervals = -1;

	/** writer to write the output file **/
	protected BufferedWriter writer = null;

	/** if true, debugging information will be shown in the console */
	protected static final boolean DEBUG = false;

	/** The vertical database */
	protected Map<Integer, Table> verticalDatabase;

	/** The minimum duration */
	protected int minDuration;

	/** The maximum duration */
	protected int maxDuration;

	/** The minimum gap */
	protected int minGap;

	/** The maximum gap */
	protected int maxGap;

	/** The dataset name */
	protected String datasetName;

	/** The epsilon */
	protected int epsilon;

	/** The minimum horizontal support */
	protected double minHorSup;

	/** The minimum vertical support as integer */
	protected int minsup;

	/** The minimum pattern length */
	protected int maxPatternLength;

	/** List of frequent symbols r */
	protected List<Table> frequentSymbols;

	/** Use the pair support matrix of FastTIRP */
	protected boolean usePairSupportMatrix;

	/** if true, use the SUPPORT_JOIN optimization */
	protected static final boolean SUPPORT_JOIN_OPTIMIZATION = true;

	/** The number of joins (for statistics) */
	protected long joinCount;

	/** The Pair support matrix of FastTIRP */
	protected Map<Integer, Map<Integer, Integer>> mapEventEventToSupport;

	/**
	 * If true, details about each matching sequence for each pattern will be
	 * displayed
	 */
	protected boolean detailedOutput = true;

	/** Use the relation B if this variable is true */
	protected boolean useB = true;

	/** Use the relation M if this variable is true */
	protected boolean useM = true;

	/** Use the relation O if this variable is true */
	protected boolean useO = true;

	/** Use the relation F if this variable is true */
	protected boolean useF = true;

	/** Use the relation S if this variable is true */
	protected boolean useS = true;

	/** Use the relation L if this variable is true */
	protected boolean useL = true;

	/** Use the relation C if this variable is true */
	protected boolean useC = true;

	/** Use the relation E if this variable is true */
	protected boolean useE = true;

	/**
	 * Run the algorithm
	 * 
	 * @param input                the input file path
	 * @param output               the output file path
	 * @param minHorSup            the minimum support threshold
	 * @param minVertSup           the minimum vertical support
	 * @param maxgap               the maximum gap
	 * @param mingap               the minimum gap
	 * @param epsilon              the epsilon parameter
	 * @param maxDuration          the maximum duration
	 * @param minDuration          the minimum duration
	 * @param maxPatternLength     the maximum pattern length
	 * @param detailedOutput       If true, details about each matching sequence for
	 *                             each pattern will be displayed
	 * @param usePairSupportMatrix use the pair support matrix
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output, double minHorSup, double minVertSup, int epsilon, int minGap,
			int maxGap, int minDuration, int maxDuration, int maxPatternLength, boolean detailedOutput,
			boolean usePairSupportMatrix) throws IOException {

		// Save the parameters
		this.minHorSup = minHorSup;
		this.epsilon = epsilon;
		this.minGap = minGap;
		this.maxGap = maxGap;
		this.minDuration = minDuration;
		this.maxDuration = maxDuration;
		this.maxPatternLength = maxPatternLength;
		this.detailedOutput = detailedOutput;
		this.usePairSupportMatrix = usePairSupportMatrix;

		if (maxPatternLength == 0) {
			return;
		}

		// reset memory logger
		MemoryLogger.getInstance().reset();

		// record the start time of the algorithm
		long startTimestamp = System.currentTimeMillis();

		// create a writer object to write results to file
		writer = new BufferedWriter(new FileWriter(output));

		// Read the input database in memory
		List<List<SymbolicTimeInterval>> database = loadDatabase(input);

		// Sort the database
		sortDatabase(database);

		if (DEBUG) {
			printHorizontalDatabase(database);
		}

		// Calculate the minimum support
		minsup = (int) Math.ceil(minVertSup * database.size());
		if (minsup == 0) {
			minsup = 1;
		}

		// Create the vertical database
		createVerticalDatabase(database);

		if (DEBUG) {
			printVerticalDatabaseAndFrequentSymbols();
		}

		// If the pair support matrix pruning optimization of FastTIRP is activated
		if (usePairSupportMatrix) {
			createMatrix(database);

			if (DEBUG) {
				printMatrix();
			}
		}

		// We don't need to keep the horizontal database in memory
		database = null;

		// Search for patterns
		mining();

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();

		// close output file
		writer.close();

		// We don't need to keep the vertical database in memory
		verticalDatabase = null;

		// record end time
		runtime = System.currentTimeMillis() - startTimestamp;
	}

	/**
	 * Print the content of the Pair support matrix to the console
	 */
	protected void printMatrix() {
		System.out.println("=======  MATRIX ========");
		// For each event A
		for (Entry<Integer, Map<Integer, Integer>> entryA : mapEventEventToSupport.entrySet()) {
			int a = entryA.getKey();

			Map<Integer, Integer> mapAtoB = entryA.getValue();

			// For each other event B associated with A in the matrix
			for (Entry<Integer, Integer> entryBandSupport : mapAtoB.entrySet()) {
				int b = entryBandSupport.getKey();
				int support = entryBandSupport.getValue();

				// Print the support of A with B
				System.out.println(" " + a + " " + b + "  support: " + support);
			}
		}
	}

	/**
	 * A pair of two events
	 */
	protected class Pair {
		/** an event */
		final int a;
		/** another event */
		final int b;

		/**
		 * constructor
		 * 
		 * @param a an event
		 * @param b another event
		 */
		public Pair(int a, int b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public int hashCode() {
			return a + b;
		}

		@Override
		public boolean equals(Object obj) {
			Pair otherPair = (Pair) obj;
			return this.a == otherPair.a && this.b == otherPair.b;
		}
	}

	/**
	 * Create the Pair Support Matrix structure of FastTIRP
	 * 
	 * @param database the horizontal database
	 */
	protected void createMatrix(List<List<SymbolicTimeInterval>> database) {
		// Initialize the matrix
		mapEventEventToSupport = new HashMap<>();

		// Create a set to remember the pairs of items that we have already seen
		// together
		// when we will scan a sequence
		Set<Pair> mapPairAlreadySeen = new HashSet<>();

		// For each sequence
		for (List<SymbolicTimeInterval> sequence : database) {

			// For each time interval A
			for (int i = 0; i < sequence.size(); i++) {
				SymbolicTimeInterval timeIntervalA = sequence.get(i);

				int a = timeIntervalA.symbol;

				// If A is infrequent, skip it
				if (verticalDatabase.get(a) == null) {
					continue;
				}

				// For each other time interval B that appears after A
				for (int j = i + 1; j < sequence.size(); j++) {
					SymbolicTimeInterval timeIntervalB = sequence.get(j);

					int b = timeIntervalB.symbol;

					// If B is infrequent, skip it
					if (verticalDatabase.get(b) == null) {
						continue;
					}

					Pair ab = new Pair(a, b);

					// If we did not see A with B before in that sequence
					if (mapPairAlreadySeen.contains(ab) == false) {

						// Calculate the duration of AB
						int endAB = Math.max(timeIntervalA.end, timeIntervalB.end);
						int duration = endAB - timeIntervalA.start;

						// Calculate the gap of AB
						int gap = timeIntervalB.start - timeIntervalA.end;

						// If the duration and gap satisfy maxDuration and maxGap
						if (duration <= maxDuration && gap < maxGap) {

							// Get the map of A
							Map<Integer, Integer> mapAEventToSupport = mapEventEventToSupport.get(a);
							if (mapAEventToSupport == null) {
								mapAEventToSupport = new HashMap<>();
								mapEventEventToSupport.put(a, mapAEventToSupport);
							}

							// Get the count of AB
							Integer countAB = mapAEventToSupport.get(b);
							if (countAB == null) {
								// If it does not exist, set it to 1
								mapAEventToSupport.put(b, 1);
							} else {
								// Otherwise increase the count by 1
								mapAEventToSupport.put(b, countAB + 1);
							}

							// Remember that we have seen AB in that sequence to
							// avoid counting it again if we see AB again.
							mapPairAlreadySeen.add(ab);
						}
					}
				}
			}
			mapPairAlreadySeen.clear();
		}
	}

	/**
	 * Create the vertical database
	 * 
	 * @param database the horizontal database
	 */
	protected void createVerticalDatabase(List<List<SymbolicTimeInterval>> database) {
		// Create the database
		verticalDatabase = new HashMap<>();

		// For each sequence
		for (int sid = 0; sid < database.size(); sid++) {
			List<SymbolicTimeInterval> sequence = database.get(sid);

			// For each time interval
			for (int eid = 0; eid < sequence.size(); eid++) {
				SymbolicTimeInterval sti = sequence.get(eid);

				// Get the table for this symbol
				Table table = verticalDatabase.get(sti.symbol);

				// If the table does not exist, create a new one
				if (table == null) {
					table = new Table(sti.symbol);
					verticalDatabase.put(sti.symbol, table);
				}

				// Insert new line in the table
				table.addLine(new Line(sid, eid, sti.start, sti.end, sti));
			}
		}

		numberOfEventTypes = verticalDatabase.size();

		// Create the list of frequent items and
		// remove infrequent itemsets from the table
		frequentSymbols = new ArrayList<>();

		Iterator<Entry<Integer, Table>> iterator = verticalDatabase.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Integer, Table> entry = iterator.next();
			Table table = entry.getValue();

			if (table.getVerticalSupport() >= minsup) {
				frequentSymbols.add(table);
			} else {
				iterator.remove();
			}
		}

		// Sort the list of frequent items
		Collections.sort(frequentSymbols, new Comparator<Table>() {
			@Override
			public int compare(Table o1, Table o2) {
				return o1.symbols[0] - o2.symbols[0];
			}
		});

	}

	/**
	 * Sort the database
	 * 
	 * @param database a horizontal database
	 */
	protected void sortDatabase(List<List<SymbolicTimeInterval>> database) {

		// For each sequence
		for (List<SymbolicTimeInterval> sequence : database) {

			// Sort the sequence using a comparator
			Collections.sort(sequence, new Comparator<SymbolicTimeInterval>() {

				@Override
				public int compare(SymbolicTimeInterval a, SymbolicTimeInterval b) {
					if (a.start < b.start) {
						return -1;
					} else if (a.start == b.start) {
						if (a.end < b.end) {
							return -1;
						} else if (a.end > b.end) {
							return 1;
						}
						return a.symbol - b.symbol;
					}
					// Otherwise, if a.start > b.start
					return 1;
				}
			});
		}
	}

	/**
	 * Load the database
	 * 
	 * @param input the input file path
	 * @throws IOException if some error reading the file
	 */
	protected List<List<SymbolicTimeInterval>> loadDatabase(String input) throws IOException {
		// We scan the database a first time to calculate the support of each item.
		BufferedReader myInput = null;
		String thisLine;

		List<List<SymbolicTimeInterval>> database = new ArrayList<>();

		try {
			// prepare the object for reading the file
			File file = new File(input);
			datasetName = file.getName();
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true) {
					continue;
				}
				char firstChar = thisLine.charAt(0);
				if (firstChar == '#' || firstChar == '%' || firstChar == 's' || firstChar == '@') {
					continue;
				}

				// Read a sequence
				String[] allStis = thisLine.split(";");

				// Create a sequence object to store the sequence
				List<SymbolicTimeInterval> sequence = new ArrayList<>(allStis.length);

				// For each symbol time interval
				for (int i = 0; i < allStis.length; i++) {
					String stiString = allStis[i];
					String[] tokens = stiString.split(",");
					int start = Integer.parseInt(tokens[0]);
					int end = Integer.parseInt(tokens[1]);
					int symbol = Integer.parseInt(tokens[2]);

					numberOfTimeIntervals++;

					// Add the symbolic time interval to the sequene
					// If the duration satisfies the constraints
					int duration = end - start;
					if (duration >= minDuration && duration <= maxDuration) {
						sequence.add(new SymbolicTimeInterval(start, end, symbol));
					}
				}

				// Add the sequence to the database
				database.add(sequence);
			}
		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		numberOfSequences = database.size();

		return database;
	}

	/**
	 * Print the hotizontal database
	 * 
	 * @param database database
	 */
	protected void printHorizontalDatabase(List<List<SymbolicTimeInterval>> database) {
		// Print the database
		for (List<SymbolicTimeInterval> sequence : database) {
			for (SymbolicTimeInterval sti : sequence) {
				System.out.print(sti.start + "," + sti.end + "," + sti.symbol + ";");
			}
			System.out.println();
		}
	}

	/**
	 * Print the vertical database
	 */
	protected void printVerticalDatabaseAndFrequentSymbols() {
		System.out.println("==== VERTICAL DATABASE ====");
		for (Entry<Integer, Table> entry : verticalDatabase.entrySet()) {
			Table table = entry.getValue();
			System.out.println(table.toString());
		}

		System.out.println("==== FREQUENT SYMBOLS ====");
		for (Table table : frequentSymbols) {
			System.out.print(table.symbols[0] + " ");
		}
		System.out.println();
	}

	/**
	 * Search for patterns
	 * 
	 * @throws IOException
	 */
	protected void mining() throws IOException {

		// For each frequent symbol
		for (Table frequentSymbol : frequentSymbols) {
			depthFirstSearch(frequentSymbol);
		}
	}

	/**
	 * Depth-first search
	 * 
	 * @throws IOException
	 */
	protected void depthFirstSearch(Table tableP) throws IOException {

		// Save this pattern to the file
		output(tableP);

		// Check the maximum pattern length
		if (tableP.symbols.length == maxPatternLength) {
			return;
		}

		// For each frequent symbol F
		for (Table tableF : frequentSymbols) {

			Table tablePF = null;

			// If the PairSupportMatrix pruning of FastTIRP is activated
			if (usePairSupportMatrix) {
				// Get the last symbol of P and the symbol of F
				int lastSymbolOfP = tableP.symbols[tableP.symbols.length - 1];
				int symbolOfF = tableF.symbols[0];

				// If the support of P with F in the matrix is greater than the minimum support
				Map<Integer, Integer> mapEventToSupport = mapEventEventToSupport.get(lastSymbolOfP);
				if (mapEventToSupport != null) {
					int support = mapEventToSupport.getOrDefault(symbolOfF, 0);
					if (support >= minsup) {

						// Join the tables of P and F
						tablePF = join(tableP, tableF);
					}

				}
			} else {
				// Join the tables of P and F
				tablePF = join(tableP, tableF);
			}

			if (tablePF != null && tablePF.getVerticalSupport() >= minsup) {
				depthFirstSearch(tablePF);
			}
		}
	}

	/**
	 * Join the table of a pattern P with the table of an event F to make a new
	 * table for the pattern PF.
	 * 
	 * @param tableP the table of a pattern P containing one or more events
	 * @param tableF the table of an event F
	 */
	protected Table join(Table tableP, Table tableF) {
		joinCount++;
		
		// Used for the join count optimization
		int howMuchWeCanFail = Math.min(tableP.getVerticalSupport(), tableF.getVerticalSupport()) - minsup;;
		int numberDontMatch = 0;

		// Create the new table for the join of table P with table F
		Table tablePF = new Table(tableP.symbols, tableF.symbols);

		// We will use two points to scan through the tables of P and F
		int pointerP = 0;
		int pointerF = 0;

		// The current lines in the table of P and F
		Line lineP;
		Line lineF;

		int lastSymbolOfP = tableP.symbols[tableP.symbols.length - 1];
		int symbolOfF = tableF.symbols[0];
		
		int currentSID = -1;
		int sizeOfTablePreviousSID = 0;

		// We use the pointer to scan the two tables until the end
		while (pointerP < tableP.size() && pointerF < tableF.size()) {
			lineP = tableP.getLine(pointerP);
			lineF = tableF.getLine(pointerF);  

			// If the lines have the same SID
			if (lineP.sid == lineF.sid) {
				
				if (SUPPORT_JOIN_OPTIMIZATION && currentSID != lineP.sid) {
					currentSID = lineP.sid;
					sizeOfTablePreviousSID = tablePF.size();
				}

				// We remember the position of line F
				int initialPositionF = pointerF;

				// We join the two current lines
				joinLines(lineP, lineF, tablePF, lastSymbolOfP, symbolOfF, tableP.symbols.length);

				// In case there are many lines with the same SIDs,
				// we need to check other lines of F
				while (true) {
					// Get the next line of F
					pointerF++;

					// If the pointer is not outside the table and the sids
					// of the current lines are still the same, join the lines
					if (pointerF < tableF.size()) {

						lineF = tableF.getLine(pointerF);

						if (lineF.sid == lineP.sid) {
							joinLines(lineP, lineF, tablePF, lastSymbolOfP, symbolOfF, tableP.symbols.length);
						}
					} else {
						// Otherwise break
						break;
					}
				}

				// Go back the line that we remember for F
				pointerF = initialPositionF;

				// Go to the next line of P
				pointerP++;

			} else {
				
				if (SUPPORT_JOIN_OPTIMIZATION && currentSID != -1) {
					currentSID = -1;
					if (sizeOfTablePreviousSID == tablePF.size()) {
						numberDontMatch++;
						if (numberDontMatch > howMuchWeCanFail) {
							break;
						}
					}
				}
				if (lineP.sid < lineF.sid) {
					// If the sid of P is less than that of F
					pointerP++;
				} else {
					// If the sid of F is less than that of P
					pointerF++;
				}
			}
		}

		return tablePF;
	}

	/**
	 * Join two lines from some tables P and F to create a line in the table PF.
	 * 
	 * @param lineP         A line in a table P
	 * @param lineF         A line in a table F
	 * @param tablePF       the table PF that is being constructed
	 * @param lastSymbolOfP the last event (sympbol) of P
	 * @param symbolOfF     the event (symbol) F
	 * @param lengthOfP     the length of P
	 */
	protected void joinLines(Line lineP, Line lineF, Table tablePF, int lastSymbolOfP, int symbolOfF, int lengthOfP) {
		if (lineF.eid <= lineP.eid) {
			return;
		}

		int startPF = Math.min(lineP.start, lineF.start);
		int endPF = Math.max(lineP.end, lineF.end);

		int duration = endPF - startPF;

		if (duration <= maxDuration) {

			int eidPF = Math.max(lineP.eid, lineF.eid);

			// Find relationship
			TimeInterval tiF = lineF.sourceIntervals.get(0);

			// Create a new TIRP with r and add it to the table of PF
			// and update the support
			Line linePF = new Line(lineP.sid, eidPF, startPF, endPF);
			linePF.relations.addAll(lineP.relations);
			linePF.sourceIntervals.addAll(lineP.sourceIntervals);

			boolean foundSomething = false;

			for (TimeInterval tiP : lineP.sourceIntervals) {

				Relation relation = findRelationship(tiP, tiF);

				if ((Relation.E.equals(relation) && lastSymbolOfP < symbolOfF) || !Relation.E.equals(relation)) {

						linePF.relations.add(relation);
						if(tiF.start - tiP.end < maxGap) {
							foundSomething = true;
						}
				}

			}

			if (foundSomething) {
				linePF.sourceIntervals.add(tiF);
				tablePF.addLine(linePF);
			}

		}
	}

	/**
	 * Find the relationship between two time intervals
	 * 
	 * @param a A time interval A
	 * @param b A time interval B
	 * @return the relation between the two time intervals A and B
	 */
	protected Relation findRelationship(TimeInterval a, TimeInterval b) {

		// Before
		if (useB && b.start - a.end > epsilon && b.start - a.end < maxGap && b.start - a.end > minGap) {
			return Relation.B;
		}

		// Meets
		if (useM && Math.abs(b.start - a.end) <= epsilon && b.start - a.start > epsilon && b.end - a.end > epsilon) {
			return Relation.M;
		}

		// Overlaps
		if (useO && b.start - a.start > epsilon && a.end - b.start > epsilon && b.end - a.end > epsilon) {
			return Relation.O;
		}

		// Left contains
		if (useL && epsilon > 0 && Math.abs(b.start - a.start) <= epsilon && a.end - b.end > epsilon) {
			return Relation.L;
		}

		// Contains
		if (useC && b.start - a.start > epsilon && a.end - b.end > epsilon) {
			return Relation.C;
		}

		// Finishes
		if (useF && b.start - a.start > epsilon && Math.abs(b.end - a.end) <= epsilon) {
			return Relation.F;
		}

		// Equals
		if (useE && Math.abs(b.start - a.start) <= epsilon && Math.abs(b.end - a.end) <= epsilon) {
			return Relation.E;
		}

		// Starts
		if (useS && Math.abs(b.start - a.start) <= epsilon && b.end - a.end > epsilon) {
			return Relation.S;
		}

		// Otherwise, the relationship is N (no relationship)
		return Relation.N;
	}

	/**
	 * Output a pattern
	 * 
	 * @throws IOException
	 */
	protected void output(Table table) throws IOException {
		// If the pattern satisfies the mean horizontal support threshold
		if (table.getMeanHorizontalSupport() >= minHorSup) {
			patternCount++; // increase the number of high support itemsets found

			// Create a string buffer
			StringBuilder buffer = new StringBuilder();

			// append the prefix
			buffer.append(table.toString(detailedOutput));

			// write to file
			writer.write(buffer.toString());
		}
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {

		if (usePairSupportMatrix) {
			System.out.println("=============  FastTIRP ALGORITHM v2.60- STATS =============");
		} else {
			System.out.println("=============  VertTIRP ALGORITHM v2.60- STATS =============");
		}
		System.out.println(" Dataset: " + datasetName);
		System.out.println("   Number of sequences: " + numberOfSequences);
		System.out.println("   Number of distinct event types: " + numberOfEventTypes);
		System.out.println(
				"   Avg. number of time intervals per sequence: " + numberOfTimeIntervals / (double) numberOfSequences);
		System.out.println(" Results for minsup = " + minsup + " sequences");
		System.out.println("   Total time ~ " + runtime + " ms");
		System.out.println("   Max Memory ~ " + maxMemory + " MB");
		System.out.println("   Pattern count (1 event): " + frequentSymbols.size());
		System.out.println("   Pattern count (all): " + patternCount);
		System.out.println("   Join count: " + joinCount);
		if (DEBUG) {
			System.out.println("   Join count: " + joinCount);
			int numberOfIntervalsForFrequentSymbols = 0;
			for (Table table : frequentSymbols) {
				numberOfIntervalsForFrequentSymbols += table.size();
			}
			System.out.println(
					"   Total number of intervals for frequent symbols: " + numberOfIntervalsForFrequentSymbols);
		}
		System.out.println("=============================================================");
	}

	/**
	 * Remove a relation (it will be ignored)
	 * 
	 * @param relation any relation except N, which cannot be removed.
	 */
	public void removeRelation(Relation relation) {

		if (Relation.B.equals(relation)) {
			useB = false;
		} else if (Relation.M.equals(relation)) {
			useM = false;
		} else if (Relation.O.equals(relation)) {
			useO = false;
		} else if (Relation.F.equals(relation)) {
			useF = false;
		} else if (Relation.S.equals(relation)) {
			useS = false;
		} else if (Relation.L.equals(relation)) {
			useL = false;
		} else if (Relation.C.equals(relation)) {
			useC = false;
		} else if (Relation.E.equals(relation)) {
			useE = false;
		}

		if (Relation.N.equals(relation)) {
			throw new IllegalArgumentException("It is forbidden to remove the relation N");
		}
	}
}