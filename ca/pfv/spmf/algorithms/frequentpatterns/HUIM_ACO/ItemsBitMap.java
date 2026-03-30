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
 * A list of bitmaps for some items, as used by the HUIM-ACO algorithm.
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */
public class ItemsBitMap {

	/**
	 * The bitmaps
	 */
	List<ItemBitmap> itemsBitmap;

	/**
	 * Default constructor
	 */
	public ItemsBitMap() {
		this.itemsBitmap = new ArrayList<ItemBitmap>();
	}

	/** 
	 * Get the bitmaps
	 * @return a list of bitmaps
	 */
	public List<ItemBitmap> getItemsBitmap() {
		return itemsBitmap;
	}

	/**
	 * Create a bitmap
	 * @param HTWUs_1 the HTWUIs_1
	 * @param database the database
	 */
	public void createBitmap(List<Integer> HTWUs_1, Integer[][] database) {
		for (int itemIndex = 0; itemIndex < HTWUs_1.size(); itemIndex++) {
			itemsBitmap.add(new ItemBitmap(//HTWUs_1.get(itemIndex), itemIndex, 
					database.length));
		}

		/*
		 * System.out.println("位图项："); int o=0; for(ItemBitmap itemBitmap
		 * :this.itemsBitmap){ System.out.print(o+"  "+itemBitmap.getItem()+"   "); o++;
		 * }
		 */

		for (int x = 0; x < HTWUs_1.size(); x++) {
			for (int y = 0; y < database.length; y++) {
				if (database[y][x] != null) {
					itemsBitmap.get(x).getItemBitmap().set(y);
				}

			}
		}

	}

}
