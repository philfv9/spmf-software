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
 * 
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 *
 */

import java.util.BitSet;

public class ItemBitmap {
	/** An item */
//	private Integer item = null;
	/** The bitmap of this item */
	private BitSet itemBitmap = null;
	/** The item index */
//	private Integer itemIndex;

	/**
	 * Get the bitmap of this item
	 * 
	 * @return the bitmap
	 */
	public BitSet getItemBitmap() {
		return itemBitmap;
	}

	/**
	 * Constructor
	 * 
	 * @param item      an item
	 * @param itemIndex the item's index
	 * @param high      the bitmap
	 */
	public ItemBitmap(//Integer item, Integer itemIndex, 
	int high) {
//		this.item = item;
//		this.itemIndex = itemIndex;
		itemBitmap = new BitSet(high);
	}

}
