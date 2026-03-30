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
 * This class implements a simple comparators for double values.
 * 
 * @author Philippe Fournier-Viger
 * @see ListDouble
 */
public class ComparatorDouble {

	/**
	 * Compare two doubles and return a value >0, =0 or <0 if the first double is
	 * bigger, equal or smaller than the second double
	 * 
	 * @param num1 the first double
	 * @param num2 the second double
	 * @return the comparison value
	 */
	public double compare(double num1, double num2) {
		return num1 - num2;
	}
}