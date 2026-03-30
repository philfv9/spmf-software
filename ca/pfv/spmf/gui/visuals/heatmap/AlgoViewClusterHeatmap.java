package ca.pfv.spmf.gui.visuals.heatmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceEuclidian;
import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceFunction;
import ca.pfv.spmf.patterns.cluster.Cluster;
import ca.pfv.spmf.patterns.cluster.DoubleArray;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
 * Algorithm to view heatmap from clusters
 * 
 * @author Philippe Fournier-Viger 2016
 */
public class AlgoViewClusterHeatmap {

	public void runAlgorithm(List<Cluster> clusters, DistanceFunction function) {
		// FIXED: Null check for input parameters
		if (clusters == null || function == null) {
			System.err.println("Clusters and distance function must not be null!");
			return;
		}

		List<DoubleArray> allInstances = new ArrayList<DoubleArray>();
		for (Cluster cluster : clusters) {
			if (cluster != null && cluster.getVectors() != null) {
				allInstances.addAll(cluster.getVectors());
			}
		}

		int instanceCount = allInstances.size();

		// FIXED: Handle empty cluster case
		if (instanceCount == 0) {
			System.err.println("No instances found in clusters!");
			return;
		}

		double[][] data = new double[instanceCount][instanceCount];
		String[] rowNames = new String[instanceCount];
		String[] columnNames = new String[instanceCount];

		// Initialize row and column names
		for (int i = 0; i < instanceCount; i++) {
			rowNames[i] = "Inst. " + i;
			columnNames[i] = "Inst. " + i;
		}

		// Calculate distances between all pairs of instances
		double max = 0;
		for (int i = 0; i < instanceCount; i++) {
			for (int j = i + 1; j < instanceCount; j++) {
				// FIXED: Only compute distance once per pair (matrix is symmetric)
				// and distance(i,i) is always 0
				double distance = function.calculateDistance(allInstances.get(i), allInstances.get(j));
				data[i][j] = distance;
				data[j][i] = distance;
				if (distance > max) {
					max = distance;
				}
			}
			// FIXED: Diagonal is explicitly zero (distance to self)
			data[i][i] = 0;
		}

		// FIXED: Avoid division by zero when all distances are the same
		if (max > 0) {
			// normalize
			for (int i = 0; i < instanceCount; i++) {
				for (int j = 0; j < instanceCount; j++) {
					data[i][j] /= max;
				}
			}
		}

		// Display the heatmap
		// FIXED: Pass false for standalone when called from within SPMF framework;
		// main() passes true
		new HeatMapViewer(false, data, rowNames, columnNames, true, true, true, true);
	}

	public static void main(String[] args) {
		List<Cluster> clusters = new ArrayList<>();

		// Example: Creating 3 clusters with random data for demonstration
		int numberOfClusters = 3;
		int numberOfInstances = 50; // Assuming each cluster will have 50 instances
		int dimensions = 3; // FIXED: Use multiple dimensions for meaningful distance calculation

		Random random = new Random();
		for (int i = 0; i < numberOfClusters; i++) {
			Cluster cluster = new Cluster();
			for (int j = 0; j < numberOfInstances; j++) {
				double[] instanceData = new double[dimensions];
				for (int k = 0; k < dimensions; k++) {
					instanceData[k] = random.nextDouble() * 100; // Random values between 0 and 100
				}
				DoubleArray instance = new DoubleArray(instanceData);
				cluster.addVector(instance);
			}
			clusters.add(cluster);
		}

		AlgoViewClusterHeatmap algoView = new AlgoViewClusterHeatmap();
		// FIXED: Pass true for standalone when running from main
		algoView.runAlgorithm(clusters, new DistanceEuclidian());
	}
}