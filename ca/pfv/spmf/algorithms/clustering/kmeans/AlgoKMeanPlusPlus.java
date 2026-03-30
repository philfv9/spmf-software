package ca.pfv.spmf.algorithms.clustering.kmeans;

import java.util.List;

import ca.pfv.spmf.patterns.cluster.ClusterWithMean;
import ca.pfv.spmf.patterns.cluster.ClustersEvaluation;
import ca.pfv.spmf.patterns.cluster.DoubleArray;
import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
 * An implementation of the Bisecting K-means++ algorithm <br/>
 * <br/>
 * 
 * @author Philippe Fournier-Viger
 * @see AlgoKMeans
 */

public class AlgoKMeanPlusPlus extends AlgoKMeans {

	/**
	 * Initialize the centroids using the K-Means++ algorithm.
	 * 
	 * @param vectors     the vectors to cluster
	 * @param vectors     the list of vectors to cluster
	 * @param k           the number of clusters
	 * @param vectorsSize the size of each vector
	 * @param newClusters a list in which the new clusters should be stored.
	 */
	@SuppressWarnings("unused")
	private void initializeCentroids(List<DoubleArray> vectors, int k, int vectorsSize,
	        List<ClusterWithMean> newClusters) {
	    
	    // (1) Randomly select the first centroid from the data points
	    int randomChoice = random.nextInt(vectors.size());
	    DoubleArray firstCentroid = vectors.get(randomChoice);
	    ClusterWithMean firstCluster = new ClusterWithMean(vectorsSize);
	    firstCluster.setMean(firstCentroid);
	    newClusters.add(firstCluster);
	    
	    boolean[] usedIndices = new boolean[vectors.size()];
	    usedIndices[randomChoice] = true;

	    // (2) Choose each subsequent centroid
	    for (int i = 1; i < k; i++) {
	        double[] distances = new double[vectors.size()];
	        double totalDistance = 0;

	        // Calculate the squared distance from the nearest centroid for each point
	        for (int j = 0; j < vectors.size(); j++) {
	            double nearestDistance = Double.MAX_VALUE;
	            for (ClusterWithMean cluster : newClusters) {
	                double distance = distanceFunction.calculateDistance(cluster.getMean(), vectors.get(j));
	                nearestDistance = Math.min(nearestDistance, distance);
	            }
	            distances[j] = nearestDistance * nearestDistance;
	            totalDistance += distances[j];
	        }

	        // Use roulette wheel selection to choose the next centroid
	        while (true) {
	            double r = random.nextDouble() * totalDistance;
	            double sum = 0;
	            for (int j = 0; j < distances.length; j++) {
	                sum += distances[j];
	                if (sum >= r && !usedIndices[j]) {
	                    DoubleArray nextCentroid = vectors.get(j);
	                    ClusterWithMean nextCluster = new ClusterWithMean(vectorsSize);
	                    nextCluster.setMean(nextCentroid);
	                    newClusters.add(nextCluster);
	                    usedIndices[j] = true;
	                    break;
	                }
	            }
	            // Check if a new centroid was added in this iteration
	            if (newClusters.size() > i) {
	                break; // Exit the while loop if a new centroid was added
	            }
	            // Otherwise, continue the loop and try again
	        }
	    }
	}



	/**
	 * Print statistics of the latest execution to System.out.
	 */
	public void printStatistics() {
		System.out.println("========== KMEANS++ - SPMF 2.09 - STATS ============");
		System.out.println(" Distance function: " + distanceFunction.getName());
		System.out.println(" Total time ~: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" SSE (Sum of Squared Errors) (lower is better) : "
				+ ClustersEvaluation.calculateSSE(clusters, distanceFunction));
		System.out.println(" Max memory:" + MemoryLogger.getInstance().getMaxMemory() + " mb ");
		System.out.println("=====================================");
	}

}