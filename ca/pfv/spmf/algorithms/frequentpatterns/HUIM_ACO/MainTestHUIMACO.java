package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_ACO;

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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.HUIM_BPSO.AlgoHUIM_BPSO;
import ca.pfv.spmf.test.MainTestHUIM_BPSO;

/**
 * Example of how to run the HUIM-ACO algorithm
 * 
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */
public class MainTestHUIMACO {

	public static void main(String[] args) throws IOException {

		String input = fileToPath("contextHUIM.txt");

		String output = ".//output.txt";

		int min_utility = 40; //

		// Applying the algorithm
		AlgoHUIMACO algorithm = new AlgoHUIMACO();
		algorithm.runAlgorithm(input, output, min_utility);
		algorithm.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestHUIM_BPSO.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}

}
