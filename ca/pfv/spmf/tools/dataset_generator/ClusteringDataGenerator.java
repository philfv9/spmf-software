package ca.pfv.spmf.tools.dataset_generator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class is a synthetic clustering data generator such that the user
 * provides some parameters and this class generate a dataset that is written to
 * the disk.
 * 
 *  
 * @author Philippe Fournier-Viger, 2024
 */
public class ClusteringDataGenerator {

	/** the random number generator */
	private static Random random = new Random(System.currentTimeMillis());

	/**
	 * This method randomly generates a clustering dataset according to parameters
	 * provided.
	 * 
	 * @param attributeCount the number of attributes per point
	 * @param distribution   the type of distribution to use for generating the data
	 *                       (uniform or normal)
	 * @param output         the file path for writting the generated dataset
	 * @throws IOException if error while reading or writting a file
	 */
	public static void generateDataset(List<Integer> clusterSizes, int attributeCount, List<Distribution[]> clusterDistributions, String output)
			throws IOException {
		// We create a BufferedWriter to write the dataset to disk
		BufferedWriter writer = new BufferedWriter(new FileWriter(output)); 

//		// We write the attribute definitions
//		for (int i = 0; i < attributeCount; i++) {
//			writer.write("@ATTRIBUTEDEF=A" + (i + 1));
//			writer.newLine();
//		}

		// We keep track of the total number of points generated
//		int pointCount = 0;

		// We loop over the cluster sizes and distributions
		for (int c = 0; c < clusterSizes.size(); c++) {
			// We get the cluster size and distribution for the current cluster
			int clusterSize = clusterSizes.get(c);
			Distribution[] clusterDistribution = clusterDistributions.get(c);

			// For the number of points to be generated in this cluster
			for (int i = 0; i < clusterSize; i++) {
				// We increment the point count
//				pointCount++;

				// We write the point name with a cluster label
//				writer.write("@NAME=Point" + pointCount + "_C" + (c + 1));
//				writer.newLine();

				// We create a list to store the attribute values for this point
				List<Double> values = new ArrayList<Double>();

				// For the number of attributes per point
				for (int j = 0; j < attributeCount; j++) {
					// We generate the attribute value randomly according to the cluster
					// distribution
					double value;
					Distribution distribution = clusterDistribution[j]; // We get the distribution for this attribute
					if (distribution instanceof NormalDistribution) {
						// We use a normal distribution with mean and standard deviation specified by
						// the user
						NormalDistribution normal = (NormalDistribution) distribution;
						double mean = normal.getMean(); // We get the mean for this attribute
						double std = normal.getStd(); // We get the standard deviation for this attribute
						value = random.nextGaussian() * std + mean;
					} else if (distribution instanceof UniformDistribution) {
						// We use a uniform distribution in the range specified by the user
						UniformDistribution uniform = (UniformDistribution) distribution;
						double min = uniform.getMin(); // We get the minimum value for this attribute
						double max = uniform.getMax(); // We get the maximum value for this attribute
						value = random.nextDouble() * (max - min) + min;
					} else {
						// We throw an exception if the distribution is invalid
						writer.close();
						throw new IllegalArgumentException("Invalid distribution: " + distribution);
					}
					// We add the value to the list
					values.add(value);
				}
				// We write the attribute values for this point
				for (int k = 0; k < values.size(); k++) {
					if (k != 0) {
						writer.write(" ");
					}
					writer.write("" + values.get(k));
				}
				writer.newLine();
			}
		}
		writer.close(); // close the file.
	}

	/**
	 * A probability distribution
	 */
	static public abstract class Distribution {
		/**
		 * This is an abstract method that returns a random value according to the
		 * distribution
		 */
		public abstract double nextValue();
	}

	/** A normal distribution */
	static public class NormalDistribution extends Distribution {
		/** the mean */
		private double mean;
		/** the standard deviation */
		private double std;

		/**
		 * This is the constructor
		 * 
		 * @param mean the mean
		 * @param std  the standard deviation
		 */
		public NormalDistribution(double mean, double std) {
			this.mean = mean;
			this.std = std;
		}

		/**
		 * getter method for the mean
		 * 
		 * @return the mean
		 */
		public double getMean() {
			return mean;
		}

		/**
		 * Getter method for the standard deviation
		 * 
		 * @return the standard deviation
		 */
		public double getStd() {
			return std;
		}

		/**
		 * Overridden method that returns a random value according to the normal
		 * distribution
		 * 
		 * @return random value
		 */
		public double nextValue() {
			return random.nextGaussian() * std + mean;
		}
	}

	/**
	 * Class representing a uniform distribution
	 */
	static public class UniformDistribution extends Distribution {
		/** the minimum value */
		private double min;
		/** the maximum value */
		private double max;

		/** Constructor that takes the minimum and maximum values as parameters */
		public UniformDistribution(double min, double max) {
			this.min = min;
			this.max = max;
		}

		/**
		 * Getter method for the minimum value
		 * 
		 * @return minimum value
		 */
		public double getMin() {
			return min;
		}

		/**
		 * Getter method for the maximum value
		 * 
		 * @return maximum value
		 */
		public double getMax() {
			return max;
		}

		/**
		 * Overridden method that returns a random value according to the uniform
		 * distribution
		 * 
		 * @return the value
		 */
		public double nextValue() {
			return random.nextDouble() * (max - min) + min;
		}
	}

}
