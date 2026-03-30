package ca.pfv.spmf.datastructures.collections.comparators;

/*
 * Copyright (c) 2023 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This class implements a simple comparators for short values.
 * 
 * @author Philippe Fournier-Viger
 * @see ListShort
 */
public class ComparatorShort {

	/**
	 * Compare two short values and return a value >0, =0 or <0 if the first short
	 * is bigger, equal or smaller than the second short
	 * 
	 * @param num1 the first short
	 * @param num2 the second short
	 * @return the comparison value
	 */
	public short compare(short num1, short num2) {
		return (short) (num1 - num2);
	}
}