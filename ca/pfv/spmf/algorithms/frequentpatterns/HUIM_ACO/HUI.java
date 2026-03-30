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
/**
 * A High utility itemset as used by the HUIM-ACO algorithm
 * 
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */

public class HUI {
	/** the itemset */
	private String itemset;
	/** the utility of that itemset */
	private int utility;

	/**
	 * Constructor
	 * @param itemset an itemset
	 * @param utility its utility
	 */
	public HUI(String itemset, int utility) {
		this.itemset = itemset;
		this.utility = utility;
	}

	/** 
	 * Get the itemset
	 * @return the itemset as a string
	 */
	public String getItemset() {
		return itemset;
	}

	/**
	 * Get the utility of that itemset
	 * @return the utility as an integer
	 */
	public int getUtility() {
		return utility;
	}

	/** Set the itemset
	 * 
	 * @param itemset an itemset as a String
	 */
	public void setItemset(String itemset) {
		this.itemset = itemset;
	}

	/** 
	 * Set the utility of that itemset
	 * @param utility the utility as an integer
	 */
	public void setUtility(int utility) {
		this.utility = utility;
	}
}
