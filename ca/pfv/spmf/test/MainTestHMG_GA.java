package ca.pfv.spmf.test;

/* Copyright (c) 2008-2025 M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger
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

// package DNA;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.hmg.AlgoHMG;
import ca.pfv.spmf.algorithms.sequentialpatterns.hmg.HMGCrossoverVariant;
import ca.pfv.spmf.algorithms.sequentialpatterns.hmg.HMGMutationVariant;

/**
 * Example of how to use the HMG algorithm from the source code (genetic algorithm variant).
 */
public class MainTestHMG_GA {

	public static void main(String[] args) throws IOException {
		
		// Crossover variant: SINGLE or CYCLE   (only for genetic algorithm)
		HMGCrossoverVariant crossoverVariant = HMGCrossoverVariant.SINGLE;
		
		// Mutation variant:  SINGLE or SCRAMBLE  (only for genetic algorithm)
		HMGMutationVariant mutationvariant = HMGMutationVariant.SINGLE;
		
		// Input file
		String dataset = fileToPath("AeCa");
		
		// Output file    
		String outputFile = ("output_AeCa.txt");
		
		// Number of generations  (only for genetic algorithm)
		int generations = 10;
		
		// Number of patterns to find
		int patterns = 2;
		
		// If true the output file will be in SPMF format otherwise as a string of characters
		boolean spmfStyleOutput = false;
		
		// Run the algorithm
		AlgoHMG hmg = new AlgoHMG();
		AlgoHMG.HMGResult result = hmg.runGAAlgorithm(dataset, generations, patterns, outputFile, crossoverVariant, mutationvariant, spmfStyleOutput);
		hmg.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestHMG_GA.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
