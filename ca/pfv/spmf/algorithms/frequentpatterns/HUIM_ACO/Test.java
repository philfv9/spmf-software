package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_ACO;

/**
 * * * * This is an implementation of the high utility itemset mining algorithm
 * based on Binary Particle Swarm Optimization Algorithm.
 * 
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
 * @author Jiakai Nan, Wei Song
 */
import java.text.DecimalFormat;
import java.util.*;

public class Test {

	public void printTraUtility(Integer[] tranUti) {
		for (int x = 0; x < tranUti.length; x++) {
			System.out.println(tranUti[x]);
		}
	}

	public void printDatabase(Integer[][] database) {
		for (int y = 0; y < database.length; y++) {
			for (int x = 0; x < database[y].length; x++) {
				System.out.print(database[y][x] + "   ");
			}
			System.out.println();
		}
	}

	public void printCollection(List list, Map<Integer, Integer> map) {
		System.out.println("1-itemset having HTWU:");
		for (Object str : list) {
			Set<Map.Entry<Integer, Integer>> set = map.entrySet();
			Iterator<Map.Entry<Integer, Integer>> iter = set.iterator();
			while (iter.hasNext()) {

				Map.Entry<Integer, Integer> record = iter.next();
				if (record.getKey() == str) {
					System.out.print(record.getKey() + "->" + record.getValue() + " ");
				}
			}
		}
		System.out.println();
		System.out.println();
	}

	public void printMap(Map<Integer, Integer> map) {
		System.out.println("item -> twu:");
		Set<Map.Entry<Integer, Integer>> set = map.entrySet();
		Iterator<Map.Entry<Integer, Integer>> iter = set.iterator();
		while (iter.hasNext()) {
			Map.Entry<Integer, Integer> record = iter.next();
			System.out.print(record.getKey() + "->" + record.getValue() + " ");
		}
		System.out.println();
		System.out.println();
	}

	public void printDatabase(ArrayList<List<ItemUtility>> list) {
		System.out.println("database:");
		for (List<ItemUtility> transaction : list) {
			for (ItemUtility itemUtility : transaction) {
				System.out.print(itemUtility.getItem());
			}
			System.out.println();
		}
		System.out.println();
	}

	public void printBit(List<ItemBitmap> Bitmap, List<Integer> HTWUs_1, Integer[][] database) {
		System.out.println("位图：");
		for (Integer item : HTWUs_1) {
			System.out.print(item + "       ");
		}
		System.out.println();
		for (int y = 0; y < database.length; y++) {
			for (int x = 0; x < HTWUs_1.size(); x++) {
				if (Bitmap.get(x).getItemBitmap().get(y)) {
					System.out.print(Bitmap.get(x).getItemBitmap().get(y) + "    ");
				} else {
					System.out.print(/* Bitmap.get(x).itemBitmap.get(y) + */"        ");
				}

			}
			System.out.println();
		}
	}

	public void printHUISets(HUIS huis) {
		System.out.println();
		System.out.println("HUISets:");
		System.out.println("hui" + "  " + "high utility");
		for (HUI hui : huis.getHuiSet()) {
			System.out.print(hui.getItemset() + "    " + hui.getUtility());
			System.out.println();
		}
		System.out.println("HUIS SIZE:" + huis.getHuiSet().size());
		System.out.println();
	}

	public void printPercentage(List<Integer> HTWUs_1, List<Double> percentage) {
		System.out.println();
		System.out.println("HTWUs_1" + "    " + "percentage");
		for (int i = 0; i < HTWUs_1.size(); i++) {
			System.out.println(HTWUs_1.get(i) + "    " + percentage.get(i));
		}
		System.out.println();
	}

	public void printAntBitset(int i, Ant ant) {

		System.out.println("ant tour:" + ant.getBitSet().length() + " " + ant.getBitSet().cardinality());
		System.out.print(i + ":");
		for (int x = 0; x < ant.getBitSet().length(); x++) {
			if (ant.isOwnFood(x)) {
				System.out.print(1 + " ");
			} else {
				System.out.print(0 + " ");
			}
		}
		System.out.println();
	}

	public void printPheromones(double[][] globalPheromones, List<Integer> HTWUs_1) {

		DecimalFormat df = new DecimalFormat("#.00");

		System.out.println("GLOBAL PHEROMONES:");

		for (Object str : HTWUs_1) {
			System.out.print("       " + str);
		}

		System.out.println();

		for (int y = 0; y < globalPheromones.length; y++) {
			for (int x = 0; x < globalPheromones[y].length; x++) {
				System.out.print(df.format(globalPheromones[y][x]) + "    ");
			}
			System.out.println();
		}
	}

	public void printSerachRecord(AntColony antColony) {
		System.out.println();
		System.out.println("ANT SEARCH RECORD:");

		for (int i = 0; i < antColony.getAntColonySize(); i++) {

			System.out.print("第" + i + "个蚂蚁搜索过的食物:");
			for (int x : antColony.getAnt(i).getExistedFoods()) {
				System.out.print(+x + "    ");
			}
			System.out.print("未搜索的食物:");
			for (int x : antColony.getAnt(i).getNoExistedFoods()) {
				System.out.print(x + "    ");
			}
			System.out.println();
		}

	}
}
