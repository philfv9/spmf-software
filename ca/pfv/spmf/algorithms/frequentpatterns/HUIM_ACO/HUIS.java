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

import java.util.ArrayList;
import java.util.List;

/**
 * A set of High utility itemsets as used by the HUIM-ACO algorithm
 * 
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 * @see HUI
 *
 */
public class HUIS {

	/** The list of High utility itemsets */
	private List<HUI> huiSet;

	public HUIS() {
		this.huiSet = new ArrayList<HUI>();
	}

	/** The list of best item */
	private List<Integer> bestItem = new ArrayList<Integer>();
	/** The best item index list */
	private List<Integer> bestItemIndex = new ArrayList<Integer>();

	/** The best item spare list */
	private List<Integer> bestItemSpare = new ArrayList<Integer>();
	/** The best item spare index list */
	private List<Integer> bestItemIndexSpare = new ArrayList<Integer>();

	/**
	 * Get the best item list
	 * @return a list of integers
	 */
	public List<Integer> getBestItem() {
		return bestItem;
	}

	/** 
	 * Get the best item index list
	 * @return a list of integers
	 */
	public List<Integer> getBestItemIndex() {
		return bestItemIndex;
	}

	/** 
	 * Get hte best item spare list
	 * @return a list of integers
	 */
	public List<Integer> getBestItemSpare() {
		return bestItemSpare;
	}

	/** 
	 * Get the best item index list
	 * @return a list of integers
	 */
	public List<Integer> getBestItemIndexSpare() {
		return bestItemIndexSpare;
	}

	/**
	 * Get the set of high utility itemsets
	 * @return A list of high utility itemsets
	 */
	public List<HUI> getHuiSet() {
		return huiSet;
	}

	/** Set the list of high utility itemsets
	 * 
	 * @param huiSet A list of high utility itemsets
	 */
	public void setHuiSet(List<HUI> huiSet) {
		this.huiSet = huiSet;
	}

	/**
	 * Get the i-th high utility itemset
	 * @param index the index i
	 * @return the i-th high utility itemset
	 */
	public HUI getHUI(int index) {
		return this.huiSet.get(index);
	}

	/** Add a high utility itemset
	 * 
	 * @param hui the high utility itemset
	 */
	public void addHUI(HUI hui) {
		this.huiSet.add(hui);
	}

	/**
	 * Add a high utility itemset
	 * @param ant the ant
	 * @param HTWUs_1 the list of HTWUIs_1
	 */
	public void addHUI(Ant ant, List<Integer> HTWUs_1) {

		StringBuilder itemsString = new StringBuilder();
		int i = 0;
		for (i = 0; i < HTWUs_1.size(); i++) {
			if (ant.isOwnFood(i)) {
				itemsString.append(HTWUs_1.get(i));
				if (!bestItemSpare.contains(HTWUs_1.get(i))) {
					bestItemSpare.add(HTWUs_1.get(i));
					bestItemIndexSpare.add(i);
				}
				itemsString.append(" ");
			}
		}

		if (this.huiSet.size() == 0) {
			this.addHUI(new HUI(itemsString.toString(), ant.getFoodSize()));
		} else {
			// huiSets is not null, judge whether exist an itemset in huiSets same with
			// itemsString
			for (i = 0; i < this.getHuiSet().size(); i++) {
				if (itemsString.toString().equals(this.getHUI(i).getItemset())) {
					break;
				}
			}

			// if not exist same itemset in huiSets with tempChroNode,insert it into huiSets
			if (i == this.huiSet.size()) {
				this.addHUI(new HUI(itemsString.toString(), ant.getFoodSize()));
			}
		}

//        System.out.println("HUI 大小："+this.huiSet.size());
	}

	/**
	 * Copy the best item index spare to the best item index
	 */
	public void copyBestItemIndexSpareToBestItemIndex() {
		this.bestItemIndex.clear();
		this.bestItem.clear();
		for (Integer Index : this.bestItemIndexSpare) {
			this.bestItemIndex.add(Index);
		}
		for (Integer Item : this.bestItemSpare) {
			this.bestItem.add(Item);
		}
		this.bestItemIndexSpare.clear();
		this.bestItemSpare.clear();
	}

}
