package ca.pfv.spmf.algorithms.associationrules.hgb;

/* This file is copyright (c) Jayakrushna Sahoo
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

import java.util.ArrayList;
import java.util.List;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * This class implements a utility list as used by HUCI-Miner and FHIM
 *  @see AlgoFHIM_and_HUCI
 * @author Jayakrushna Sahoo
 */
public class UtilityList {
	int item; // the item
	int sumIutils = 0; // the sum of item utilities
	int sumRutils = 0; // the sum of remaining utilities
	int exutil = 0;
	List<Element> elements = new ArrayList<>(); // the elements

	/**
	 * Constructor.
	 * 
	 * @param item the item that is used for this utility list
	 */
	public UtilityList(int item) {
		this.item = item;
	}

	public void addElement(Element element) {
		sumIutils += element.iutils;
		sumRutils += element.rutils;
		elements.add(element);
	}

	public void setexutil(int ext) {
		this.exutil = ext;
	}

}
