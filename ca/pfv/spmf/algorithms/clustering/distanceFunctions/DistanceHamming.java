package ca.pfv.spmf.algorithms.clustering.distanceFunctions;

import ca.pfv.spmf.patterns.cluster.DoubleArray;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
/**
 * This class implements the HAMMING distance function. It is a subclass of
 * the DistanceFunction class which represents any distance function. The
 * correlation distance function calculates the distance between two vectors of
 * double and returns a value that is a positive number A smaller value
 * indicates that the two vectors are closer. Note: this function does not work
 * well with algorithms such as K-Means. <br/>
 * <br/>
 * 
 * @see DistanceFunction
 * @author Philippe Fournier-Viger
 */
public class DistanceHamming extends DistanceFunction {
	/** the name of this distance function */
	static String NAME = "HAMMING";

	/**
	 * Calculate the HAMMING distance between two vectors of doubles.
	 * 
	 * @param vector1 the first vector
	 * @param vector2 the second vector
	 * @return the distance
	 */
	public double calculateDistance(DoubleArray vector1, DoubleArray vector2) {
		double distance = 0;
		for (int i = 0; i < vector1.data.length; i++) {
			if (vector1.data[i] != vector2.data[i]) {
				distance++;
			}
		}
//		System.out.println(distance);
		return distance;
	}

	@Override
	public String getName() {
		return NAME;
	}
}