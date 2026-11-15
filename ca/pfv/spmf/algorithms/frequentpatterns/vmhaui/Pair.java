package ca.pfv.spmf.algorithms.frequentpatterns.vmhaui;

/* This is an implementation of the VMHAUI algorithm.
*
* Copyright (c) 2026 VMHAUI
*
* This file is part of the SPMF DATA MINING SOFTWARE * (http://www.philippe-fournier-viger.com/spmf).
*
*
* SPMF is free software: you can redistribute it and/or modify it under the * terms of the GNU General Public License as published by the Free Software * Foundation, either version 3 of the License, or (at your option) any later * version. *
*
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR * A PARTICULAR PURPOSE. See the GNU General Public License for more details. *
*
* You should have received a copy of the GNU General Public License along with * SPMF. If not, see <http://www.gnu.org/licenses/>.
*
* @author Philippe Fournier-Viger
* 
* Do not remove copyright and license information.
*/

/**
 * Represents an item and its utility in a transaction (a cell of the integrated
 * matrix).
 */
class Pair {
	
	/** the item identifier */
	int item = 0;
	
	/** the utility of the item */
	int utility = 0;
}