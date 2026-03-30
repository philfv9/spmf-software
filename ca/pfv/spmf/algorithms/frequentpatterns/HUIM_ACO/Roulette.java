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
import java.util.Map;
/**
 * The class Roulette as used by the HUIM-ACO algorithm
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */
public class Roulette {

	/** the roulette (list of double values */
	private List<Double> roulette = new ArrayList<Double>();
	/** flag to remember if it has changed */
	private Boolean isChanged = false;

	/** 
	 * Check if it has changed
	 * @return true if yes. otherwise false.
	 */
	public Boolean getChanged() {
		return isChanged;
	}

	/** 
	 * Get the list of double values for the roulette
	 * @return a list of Double values
	 */
	public List<Double> getRoulette() {
		return roulette;
	}

	/**
	 * Set that a change has happened
	 * @param changed true if a change has happened. Otherwise, false
	 */
	public void setChanged(Boolean changed) {
		isChanged = changed;
	}

	/**
	 * Constructor
	 * @param HTWUs_1 the HWTUIs_1 
	 * @param itemAndTWU the items and their TWUs as a map
	 */
	public Roulette(List<Integer> HTWUs_1, Map<Integer, Integer> itemAndTWU) {
		initializeRandomTable(HTWUs_1, itemAndTWU);
	}

	/**
	 * Initialize a random table
	 * @param HTWUs_1 the HWTUIs_1 
	 * @param itemAndTWU the items and their TWUs as a map
	 */
	public void initializeRandomTable(List<Integer> HTWUs_1, Map<Integer, Integer> itemAndTWU) {
		double Htwu_1_Sum = 0;
		double eachHtwu_1_Sum = 0;
		double proportion = 0;

		// caculate the allHtwuSum
		for (int i = 0; i < HTWUs_1.size(); i++) {
			Htwu_1_Sum += itemAndTWU.get(HTWUs_1.get(i));
		}
		// caculate the eachHtwuSum
		for (int i = 0; i < HTWUs_1.size(); i++) {
			eachHtwu_1_Sum += itemAndTWU.get(HTWUs_1.get(i));
			proportion = eachHtwu_1_Sum / Htwu_1_Sum;
			this.roulette.add(proportion);

		}
	}

	/**
	 * Whirl the roulette
	 * @return the result
	 */
	public int whirlRoulette() {
		int i, temp = 0;
		double randNum;
		randNum = Math.random();
		for (i = 0; i < roulette.size(); i++) {
			if (i == 0) {
				if ((randNum >= 0) && (randNum <= this.roulette.get(0))) {
					temp = 0;
					break;
				}
			} else if ((randNum > this.roulette.get(i - 1)) && (randNum <= this.roulette.get(i))) {
				temp = i;
				break;
			}
		}
		return temp;
	}

}
