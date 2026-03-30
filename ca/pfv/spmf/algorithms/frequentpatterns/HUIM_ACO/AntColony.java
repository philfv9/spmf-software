package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_ACO;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * 
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */
public class AntColony {
	/** The ant colony size */
	private int antColonySize = 0;
	/** the ant Colony */
	private List<Ant> antColony = null;
	/** the search times */
	private int searchTimes = 0;
	/** the search method threshold 1 */
	private double searchMethodThreshold1 = 0;
	/** the search method threshold 2 */
	private double searchMethodThreshold2 = 0;
	/** the Pheromones betweenone one item to another item */
	private double[][] globalPheromones;
	/** The roulette used by the ant colony */
	private Roulette antColonyRoulette;
//	/** The list of big item */
//	private ArrayList<Integer> bigItem = new ArrayList();
	/** * a map of visited itemsets */
	private HashMap<BitSet, Integer> visitedItemset = new HashMap();
//	/** The visited itemset utility */
//	private Integer visitedItemsetUtility = 0;

	/**
	 * Get the roulette
	 * 
	 * @return the roulette
	 */
	public Roulette getAntColonyRoulette() {
		return antColonyRoulette;
	}

	// reset parameter
	private int firstHuiSize = 0;
	private int secondHuiSize = 0;
	private int thirdHuiSize = 0;
	private int initHuiSize = 0;
	private boolean resetAntColonyFlag = false;
	private int initPheromoneTimes = -1;

	private boolean filterFlag = true;
	private boolean copyEliteAntFlag = false;

	public HashMap<BitSet, Integer> getVisitedItemset() {
		return visitedItemset;
	}

	public void setRoulette(List<Integer> HTWUs_1, Map<Integer, Integer> itemAndTWU) {
		this.antColonyRoulette = new Roulette(HTWUs_1, itemAndTWU);
	}

	public AntColony(int length) {
		antColony = new ArrayList<Ant>();
		globalPheromones = new double[length - 1][length];
	}

	public int getAntColonySize() {
		return antColonySize;
	}

	public void setGlobalPheromones(int x, int y, double pheromones) {
		this.globalPheromones[x][y] += pheromones;
	}

	public double[][] getGlobalPheromones() {
		return globalPheromones;
	}

	public void setAntColonySize(int antColonySize) {
		this.antColonySize = antColonySize;
	}

	public void setSearchTimes(int searchTimes) {
		this.searchTimes = searchTimes;
	}

	public void setSearchMethodThreshold1(double searchMethodThreshold1) {
		this.searchMethodThreshold1 = searchMethodThreshold1;
	}

	public void setSearchMethodThreshold2(double searchMethodThreshold2) {
		this.searchMethodThreshold2 = searchMethodThreshold2;
	}

	/**
	 * Initialize the ant colony
	 * 
	 * @param minUtility  the minimum utility threshold
	 * @param HTWUs_1     the HTWUIs_1
	 * @param database    the database
	 * @param itemsBitmap the bitmaps of items
	 * @param huis        the high utility itemsets
	 */
	public void initializeAntColony(int minUtility, List<Integer> HTWUs_1, Integer[][] database,
			List<ItemBitmap> itemsBitmap, HUIS huis) {

		// the transaction lists include some itemset the Ant finded
		List<Integer> transLists = new ArrayList<Integer>();
		List<Integer> itemList = new ArrayList<Integer>();
		int i = 0;
		int toFood = 0;
		while (i < this.antColonySize) {
			// generate a ant
			Ant ant = new Ant(HTWUs_1.size());

//			if (true) {
				toFood = this.antColonyRoulette.whirlRoulette();
//			} else if (false) {
//				toFood = (int) (Math.random() * HTWUs_1.size());
//			} else if (false) {
//				toFood++;
//				if (toFood == HTWUs_1.size()) {
//					toFood = 0;
//				}
//			}

			ant.setFood(toFood).isPurchasedItemset(transLists, itemList, database, itemsBitmap);
			ant.setCurrentFood(toFood);
			ant.setExistedFoods(toFood);
			ant.judgeFoodSize(transLists, itemList, database, HTWUs_1);
			transLists.clear();
			itemList.clear();

			this.antColony.add(ant);
			// and HUI
			if (ant.getFoodSize() >= minUtility && ant.getBitSet().cardinality() > 0) {
				huis.addHUI(ant, HTWUs_1);
			}
			i++;
		}
	}

	/**
	 * Search for food
	 * 
	 * @param minUtility             the minimum utility threshold
	 * @param HTWUs_1                the HTWUIs_1
	 * @param database               the database
	 * @param itemsBitmap            the bitmaps of items
	 * @param huis                   the high utility itemsets
	 * @param itemAndTWU             map of items to TWU
	 * @param transactionUtilityList the list of transaction utility values
	 * @param debugmode              if true, debug messages will be printed in the
	 *                               console.
	 */
	public void searchFood(int minUtility, Integer[][] database, List<Integer> HTWUs_1, List<ItemBitmap> itemsBitmap,
			HUIS huis, Map<Integer, Integer> itemAndTWU, Integer[] transactionUtilityList, boolean debugmode) {
		for (int searchTimesIndex = 0; searchTimesIndex < this.searchTimes; searchTimesIndex++) {

			if (this.resetAntColonyFlag) {
				huis.copyBestItemIndexSpareToBestItemIndex();
				if (debugmode) {
					System.out.println("bestitem:" + huis.getBestItem() + " \n" + "        " + huis.getBestItemIndex()
							+ "\n" + huis.getBestItemIndex().size());
				}
				this.globalPheromones = new double[HTWUs_1.size() - 1][HTWUs_1.size()];
			}

			for (int antIndex = 0, otherToFood = 0; antIndex < this.antColonySize; antIndex++) {
				int toFood = 0;

				if (this.resetAntColonyFlag) {
					this.getAnt(antIndex).getExistedFoods().clear();
					this.getAnt(antIndex).getNoExistedFoods().clear();
					for (int i = 0; i < huis.getBestItemIndex().size(); i++) {
						this.getAnt(antIndex).getNoExistedFoods().add(huis.getBestItemIndex().get(i));
					}
					this.antColonyRoulette.getRoulette().clear();
					this.antColonyRoulette.setChanged(true);
					this.antColonyRoulette.initializeRandomTable(huis.getBestItem(), itemAndTWU);

//					if (true) {
						toFood = huis.getBestItemIndex().get(this.antColonyRoulette.whirlRoulette());
//					} else if (false) {
//						toFood = huis.getBestItemIndex().get((int) (Math.random() * huis.getBestItemIndex().size()));
//					} else if (false) {
//						toFood = huis.getBestItemIndex().get(otherToFood);
//						otherToFood++;
//						if (otherToFood == huis.getBestItemIndex().size()) {
//							otherToFood = 0;
//						}
//					}
					this.getAnt(antIndex).setCurrentFood(toFood);
					this.getAnt(antIndex).getBitSet().clear();
					this.getAnt(antIndex).setExistedFoods(toFood);
					if (antIndex == this.antColonySize - 1) {
						this.firstHuiSize = 0;
						this.secondHuiSize = 0;
						this.thirdHuiSize = 0;
						this.initHuiSize = huis.getHuiSet().size();
						this.initPheromoneTimes = searchTimesIndex + 1;
						this.resetAntColonyFlag = false;

						this.filterFlag = true;
					}
					continue;
				}

				List<Integer> transLists = new ArrayList<Integer>();// the transaction lists include some itemset the
																	// Ant finded
				List<Integer> itemList = new ArrayList<>();
				double searchMethod;

				if (initPheromoneTimes == searchTimesIndex) {
					searchMethod = (double) (Math.random() * this.searchMethodThreshold1);
				} else {
					searchMethod = (searchTimesIndex == 0) ? (double) (Math.random() * this.searchMethodThreshold1)
							: (double) (Math.random());// the ant chosse a search method
				}

				if (searchMethod <= this.searchMethodThreshold1) {

					toFood = this.getAnt(antIndex).searchFood(HTWUs_1, huis, this.antColonyRoulette);
					if (toFood == -1 || toFood == -2) {
						continue;
					}
					this.antMove(antIndex, toFood, minUtility, transLists, itemList, database, HTWUs_1, itemsBitmap,
							huis, transactionUtilityList);
				} else if (this.searchMethodThreshold1 < searchMethod && searchMethod < this.searchMethodThreshold2) {

					this.getAnt(antIndex).initializeDecisionTable(this.getGlobalPheromones());
					toFood = this.getAnt(antIndex).makedecision(Ant.getDecisionTable());
					Ant.getDecisionTable().clear();
					Ant.getExistPheromones().clear();

					if (toFood == -2) {
						continue;
					}
					this.antMove(antIndex, toFood, minUtility, transLists, itemList, database, HTWUs_1, itemsBitmap,
							huis, transactionUtilityList);
				} else if (searchMethod >= this.searchMethodThreshold2) {

					toFood = this.getAnt(antIndex).selectedMaximumPheromones(this.getGlobalPheromones());
					if (toFood == -1) {
						continue;
					}

					this.antMove(antIndex, toFood, minUtility, transLists, itemList, database, HTWUs_1, itemsBitmap,
							huis, transactionUtilityList);
				}
			}

			this.firstHuiSize = secondHuiSize;
			this.secondHuiSize = thirdHuiSize;
			this.thirdHuiSize = huis.getHuiSet().size();

			if (this.firstHuiSize != this.initHuiSize && this.secondHuiSize == this.firstHuiSize
					&& this.secondHuiSize == this.thirdHuiSize) {
				resetAntColonyFlag = true;
			}
			if (this.firstHuiSize != this.initHuiSize && this.filterFlag) {
				this.copyEliteAntFlag = true;
			}

			if (this.copyEliteAntFlag) {
				Collections.sort(this.antColony);
				/*
				 * for(int i = 0;i<this.antColony.size();i++){
				 * System.out.println(this.antColony.get(i).getFoodSize()); }
				 */
				for (int eliteIndex = 0; eliteIndex < this.antColonySize * 0.2; eliteIndex++) {
					for (int powerlessIndex = 1; powerlessIndex < this.antColonySize
							/ (this.antColonySize * 0.2); powerlessIndex++) {
						this.antColony.get(eliteIndex).copyTo(
								this.antColony.get((eliteIndex + powerlessIndex * (int) (this.antColonySize * 0.2))));
					}
				}
				/*
				 * for(int i = 0;i<this.antColony.size();i++){
				 * System.out.println(this.antColony.get(i).getFoodSize()); }
				 */
				this.filterFlag = false;
				this.copyEliteAntFlag = false;
			}

			if (debugmode) {
				System.out.println(searchTimesIndex + "times search     " + "HUI:" + huis.getHuiSet().size() + "     "
						+ this.firstHuiSize + "       " + this.secondHuiSize + "     " + this.thirdHuiSize + "   "
						+ this.resetAntColonyFlag + "     ");
			}
		}
	}

	public Ant getAnt(int Index) {
		return this.antColony.get(Index);
	}

	public void antMove(int antIndex, int toFood, int minUtility, List<Integer> transLists, List<Integer> itemList,
			Integer[][] database, List<Integer> HTWUs_1, List<ItemBitmap> itemsBitmap, HUIS huis,
			Integer[] transactionUtilityList) {

		if ((this.getAnt(antIndex).setFood(toFood).isPurchasedItemset(transLists, itemList, database, itemsBitmap))) {
			this.getAnt(antIndex).setPassFood(this.getAnt(antIndex).getCurrentFood());
			this.getAnt(antIndex).setCurrentFood(toFood);
			this.getAnt(antIndex).judgeFoodSize(transLists, itemList, database, HTWUs_1, transactionUtilityList);// caculate
																													// the
																													// fitness
																													// of
																													// the
																													// ant
																													// tour
			transLists.clear();
			itemList.clear();
//                this.addVisitedItemset(this.getAnt(antIndex).getBitSet(), this.getAnt(antIndex).getFoodSize());
			if (this.getAnt(antIndex).getFoodTWU() < minUtility
					&& this.getAnt(antIndex).getBitSet().cardinality() == 2) {
//                    System.out.println("this.getAnt(antIndex)"+this.getAnt(antIndex).getPassFood()+"    "+this.getAnt(antIndex).getCurrentFood());
				this.getAnt(antIndex).releasePheromones(this, minUtility);
				this.getAnt(antIndex).getBitSet().clear(toFood);
				this.getAnt(antIndex).setCurrentFood(this.getAnt(antIndex).getPassFood());

			} else if (this.getAnt(antIndex).getFoodTWU() < minUtility) {
//                    System.out.println(this.getAnt(antIndex).getBitSet().cardinality()+"this.getAnt(antIndex)"+this.getAnt(antIndex).getPassFood()+"    "+this.getAnt(antIndex).getCurrentFood());
				this.getAnt(antIndex).getBitSet().clear(toFood);
				this.getAnt(antIndex).setCurrentFood(this.getAnt(antIndex).getPassFood());

			} else {
				this.getAnt(antIndex).releasePheromones(this, minUtility);
			}

			if (this.getAnt(antIndex).getFoodSize() >= minUtility
					&& this.getAnt(antIndex).getBitSet().cardinality() > 0) {
				huis.addHUI(this.getAnt(antIndex), HTWUs_1);
			}
		}
		this.getAnt(antIndex).setExistedFoods(toFood);
	}

}
