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
 * An item and its utility  as used by the HUIM-ACO algorithm
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */

public class ItemUtility {
	/** the item */
	private Integer item = null;
	/** the utility of the item */
	private Integer utility = null;

	/**
	 * Constructor
	 * @param item the item
	 * @param utility the utility of the item
	 */
	public ItemUtility(Integer item, Integer utility) {
		this.item = item;
		this.utility = utility;
	}

	/**
	 * Set the item
	 * @param item the item
	 */
	public void setItem(Integer item) {
		this.item = item;
	}

	/** 
	 * Set the utility
	 * @param utility the utility
	 */
	public void setUtility(Integer utility) {
		this.utility = utility;
	}

	/**
	 * Get the item
	 * @return the item
	 */
	public Integer getItem() {
		return item;
	}

	/**
	 * Get the utility 
	 * @return the utility
	 */
	public Integer getUtility() {
		return utility;
	}
}
