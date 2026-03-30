package ca.pfv.spmf.algorithmmanager;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
/**
 * This class is used to indicate the types of different algorithms in SPMF.
 * 
 * @see DescriptionOfAlgorithm
 * @author Philippe Fournier-Viger 2024
 */
public enum AlgorithmType {
	/**
	 * A general data mining algorithm
	 * (has input, has output)
	 */
	DATA_MINING,
	/**
	 * An algorithm that generates synthetic data
	 * (NO input, has output)
	 */
	DATA_GENERATOR,
	/**
	 * An algorithm that processes data and transform it (e.g. convert a dataset or fix some issues in the data)
	 * (has input, has output)
	 */
	DATA_PROCESSOR,
	/**
	 * An algorithm to visualize data 
	 * (has input, NO output)
	 */
	DATA_VIEWER,
	/**
	 * An algorithm that provides some other user interface tool
	 * (NO input, NO output)
	 */
	OTHER_GUI_TOOL,
	/**
	 * An algorithm that calculates some statistics on data
	 * (has input, NO output)
	 */
	DATA_STATS_CALCULATOR, 
	/**
	 * An algorithm that is used to run experiments
	 */
	EXPERIMENT_TOOL,
	/**
	 * An algorithm that is a tool without a user interface (such as  exporting the list of algorithms to JSON format)
	 */
	OTHER_TOOL
}
