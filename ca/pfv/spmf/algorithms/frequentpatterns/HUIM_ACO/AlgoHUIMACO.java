package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_ACO;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.MemoryLogger;

/*
 * Copyright (c) 2020 Wei Song, Jiakai Nan
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * 
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. *
 * 
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * *
 * 
 * You should have received a copy of the GNU General Public License along with
 * * SPMF. If not, see <http://www.gnu.org/licenses/>..
 * 
 */
/**
 * This is the original implementation of the HUIM-ACO algorithm for mining high
 * utility itemsets using ant-colony optimization. This algorithm was presented
 * at FSKD 2020 in this paper:<br/>
 * <br/>
 * 
 * Song, W., Nan, J. (2020) Mining High Utility Itemsets Using Ant Colony
 * Optimization. Proc. of ICNC-FSKD 2020. <br/>
 * <br/>
 * 
 * @author Jiakai Nan, Wei Song
 */

public class AlgoHUIMACO {
	/** the maximum memory usage */
	private double maxMemory = 0;
	/** the time the algorithm started */
	private long startTimestamp = 0; //
	/** the time the algorithm terminated */
	private long endTimestamp = 0; //
	/** the minimum utility threshold */
	private int min_utility;

	/** The input file */
	private String input;
	/** The output file */
	private String output;

	/** The number of HUIs found */
	private int patternCount = 0;

	/** debug mode */
	private boolean DEBUG_MODE = false;

	/**
	 * Constructor
	 */
	public AlgoHUIMACO() {
		this.maxMemory = 0;
		this.startTimestamp = 0;
		this.endTimestamp = 0;
	}

	/**
	 * Set the start timestamp
	 * 
	 * @param startTimestamp the start timestamp
	 */
	public void setStartTimestamp(long startTimestamp) {
		this.startTimestamp = startTimestamp;
	}

	/**
	 * Set the end timestamp
	 * 
	 * @param endTimestamp the end timestamp
	 */
	public void setEndTimestamp(long endTimestamp) {
		this.endTimestamp = endTimestamp;
	}

	/**
	 * Run the algorithm
	 * 
	 * @throws IOException
	 */
	public void runAlgorithm(String inputPath, String outputPath, int minUtility) throws IOException {

		// Reset memory usage
		MemoryLogger.getInstance().reset();

//		Test test = new Test();

		this.input = inputPath;
		this.output = outputPath;
		this.startTimestamp = System.currentTimeMillis();
		this.min_utility = minUtility;

		if (DEBUG_MODE) {
			System.out.println("Start processing data");
		}
		Data data = new Data();
		data.calculateTwu(this.input);
		data.removeLowTWU(this.input, this.min_utility);
		data.sortHTWUs_1();
		data.readDatabase(this.input);

//        test.printMap(data.getItemAndTWU());
//        test.printCollection(data.getHTWUs_1(),data.getItemAndTWU());
//        test.printTraUtility(data.getTransactionUtilityList());

//         test.printDatabase(data.getDatabase());

		MemoryLogger.getInstance().checkMemory();
		ItemsBitMap itemsBitMap = new ItemsBitMap();
		itemsBitMap.createBitmap(data.getHTWUs_1(), data.getDatabase());

//        test.printBit(itemsBitMap.getItemsBitmap(),data.getHTWUs_1(),data.getDatabase());

		if(DEBUG_MODE) {
		System.out.println("End of data processing");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		}

		AntColony antColony = new AntColony(data.getHTWUs_1().size());
		antColony.setAntColonySize(10000);
		antColony.setSearchTimes(50);
		antColony.setSearchMethodThreshold1(0.1);
		antColony.setSearchMethodThreshold2(0.7);
		antColony.setRoulette(data.getHTWUs_1(), data.getItemAndTWU());
		HUIS huis = new HUIS();

		if (data.getHTWUs_1().size() > 0) {
			if(DEBUG_MODE) {
			System.out.println("Start initializing the ant colony");
			}

			antColony.initializeAntColony(this.min_utility, data.getHTWUs_1(), data.getDatabase(),
					itemsBitMap.getItemsBitmap(), huis);

			if(DEBUG_MODE) {
			System.out.println("End of ant colony initialization");
			}

			antColony.searchFood(this.min_utility, data.getDatabase(), data.getHTWUs_1(), itemsBitMap.getItemsBitmap(),
					huis, data.getItemAndTWU(), data.getTransactionUtilityList(), DEBUG_MODE);
		}

		// Write the output to file
		writeOut(huis, output);

		patternCount = huis.getHuiSet().size();

		MemoryLogger.getInstance().checkMemory();

		// record end time
		this.setEndTimestamp(System.currentTimeMillis());

//        test.printMap(data.getItemAndTWU());
//       test.printCollection(data.getHTWUs_1());
//        test.printDatabase(data.getDatabase());
//        test.printBit(itemsBitMap.getItemsBitmap(),data.getHTWUs_1(),data.getDatabase());
//       test.printHUISets(huis);
//        test.printPheromones(antColony.getGlobalPheromones(),data.getHTWUs_1());
//        test.printSerachRecord(antColony);*/
	}

	/**
	 * Write the result to file
	 * 
	 * @param huis   a set of high utility itemsets
	 * @param output the output file path
	 * @throws IOException if there is an exception while writing to file
	 */
	private void writeOut(HUIS huis, String output) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));
		; // writer to write the output file
			// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < huis.getHuiSet().size(); i++) {
			buffer.append(huis.getHuiSet().get(i).getItemset());
			// append the utility value
			buffer.append("#UTIL: ");
			buffer.append(huis.getHuiSet().get(i).getUtility());
			buffer.append(System.lineSeparator());
		}
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
		writer.close();
	}

	/**
	 * Print statistics about the last execution of this algorithm to the console
	 * 
	 * @param huis the set of HUIS
	 */
	public void printStats() {
		System.out.println("=============  HUIM-ACO ALGORITHM v.2.57 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility itemsets count : " + patternCount);
		System.out.println("===================================================");
	}

}
