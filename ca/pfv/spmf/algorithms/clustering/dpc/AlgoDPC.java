package ca.pfv.spmf.algorithms.clustering.dpc;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceFunction;
import ca.pfv.spmf.algorithms.clustering.instancereader.AlgoInstanceFileReader;
import ca.pfv.spmf.datastructures.kdtree.KDTree;
import ca.pfv.spmf.patterns.cluster.Cluster;
import ca.pfv.spmf.patterns.cluster.DoubleArray;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Implementation of the Density Peak Clustering algorithm of Lopez, A. et al.
 * (2014) based on the original article proposing that algorithm.
 * 
 * @author Philippe Fournier-Viger, copyright 2024
 */
public class AlgoDPC {

	/** The list of clusters generated */
	protected List<Cluster> clusters = null;

	// For statistics
	/** the start time of the latest execution **/
	protected long startTimestamp;
	/** the end time of the latest execution **/
	protected long endTimestamp;
	/** the number of iterations that was performed **/
	protected long iterationCount;

	/** The distance function to be used for clustering */
	protected DistanceFunction distanceFunction = null;

	/** The names of the attributes **/
	private List<String> attributeNames = null;

	/** Boolean to activate the debug mode **/
	boolean DEBUG_MODE = false;

	/**
	 * This KD-Tree is used to index the data points for fast access to points in
	 * the epsilon radius
	 */
	private KDTree kdtree;

	/** Buffers for storing points **/
	List<DoubleArray> bufferNeighboors1 = null;

	/**
	 * Default constructor
	 */
	public AlgoDPC() {
	}

	/**
	 * Run the DPC algorithm
	 *
	 * @param inputFile         an input file path containing a list of vectors of
	 *                          double values
	 * @param distanceFunction2
	 * @param separator         the character used to separate double values in the
	 *                          input file
	 * @param dc                the cutoff distance
	 * @return a list of clusters (some of them may be empty)
	 * @throws IOException exception if an error while writing the file occurs
	 */
	public List<Cluster> runAlgorithm(String inputFile, DistanceFunction distanceFunction2, String separator,
			int rhoMin, double deltaMin, double dc) throws NumberFormatException, IOException {
		// record the start time
		startTimestamp = System.currentTimeMillis();
		// reset the number of iterations
		iterationCount = 0;

		this.distanceFunction = distanceFunction2;

		// Structure to store the vectors from the file
		List<DoubleArray> instances;

		// Read the input file
		AlgoInstanceFileReader reader = new AlgoInstanceFileReader();
		instances = reader.runAlgorithm(inputFile, separator);
		attributeNames = reader.getAttributeNames();

		kdtree = new KDTree();
		kdtree.buildtree(instances);

		// Create the buffer structure for storing neighboors when searching neighboors
		// of a point within a radius
		bufferNeighboors1 = new ArrayList<DoubleArray>();

		// Apply DPC
		applyAlgorithm(instances, rhoMin, deltaMin, dc);

		// check memory usage
		MemoryLogger.getInstance().checkMemory();

		// record end time
		endTimestamp = System.currentTimeMillis();

		// return the clusters
		return clusters;
	}

	/**
	 * Apply the algorithm
	 * @param vectors the instances to classify (records)
	 * @param rhoMin the RhoMin parameter
	 * @param deltaMin the DeltaMin parameter
	 * @param dc the Dc parameter
	 * @return a list of clusters.
	 */
	List<Cluster> applyAlgorithm(List<DoubleArray> vectors, int rhoMin, double deltaMin, double dc) {
		int n = vectors.size();
		Point[] pointList = new Point[n];

		// ====== Compute local density (rho) of each point =======
		for (int i = 0; i < n; i++) {
			pointList[i] = new Point(vectors.get(i));
			// find the neighboors of this point within the radius
			bufferNeighboors1.clear();
			kdtree.pointsWithinRadiusOf(vectors.get(i), dc, bufferNeighboors1);
			pointList[i].rho = bufferNeighboors1.size();
		}

		Arrays.sort(pointList, new Comparator<Point>() {
			@Override
			public int compare(Point p1, Point p2) {
				return p2.rho - p1.rho;
			}
		});

		if (DEBUG_MODE) {
			System.out.println("=== List of points sorted by decreasing rho values == ");
			for (Point point : pointList) {
				System.out.println(" " + point.array + "  rho: " + point.rho);
			}

			System.out.println(pointList);
		} 

		// ====== Compute the minimum distance (delta) of each point =======
		// Initialize delta for the highest density point
		pointList[0].delta = Double.MAX_VALUE;
		int[] nearestNeighbor = new int[n];

		for (int i = 1; i < n; i++) {
			for (int j = 0; j < i; j++) {
				if (pointList[j].rho == pointList[i].rho) {
					break;
				}
				double dist = distanceFunction.calculateDistance(pointList[i].array, pointList[j].array);
				if (dist < pointList[i].delta) {
					pointList[i].delta = dist;
					nearestNeighbor[i] = j;
				}
			}
		}

		if (DEBUG_MODE) {
			System.out.println("=== Delta and Nearest Neighbor ===");
			for (int i = 0; i < n; i++) {
				System.out.println("Point: " + pointList[i].array + "  delta: " + pointList[i].delta + "  rho: "
						+ pointList[i].rho);
				if (nearestNeighbor[i] != -1) {
					System.out.println("    neighreast neigbor : " + pointList[nearestNeighbor[i]].array);
				}
			}
		}

		// ====== Select cluster centers based on rhoMin and deltaMin =======
		clusters = new ArrayList<Cluster>();
		int nextClusterId = 0;

		for (int i = 0; i < n; i++) {
			if (pointList[i].rho >= rhoMin && pointList[i].delta >= deltaMin) {
				pointList[i].clusterID = nextClusterId++;
				Cluster cluster = new Cluster();
				cluster.getVectors().add(pointList[i].array);
				clusters.add(cluster);
			} else {
		        pointList[i].clusterID = -1; // Initialize to -1 for non-center points
		    }
		}
		
		//  If there are no cluster centers
		if(clusters.size() == 0) {
			System.out.println("No cluster centers are found for the given parameters");
			return clusters;
		}

		// ====== Assign points to clusters =======
		for (int i = 0; i < n; i++) {
		    if (pointList[i].clusterID == -1 && nearestNeighbor[i] != -1) {
		        int currentPointIndex = nearestNeighbor[i];
		        while (currentPointIndex != -1 && pointList[currentPointIndex].clusterID == -1) {
		            currentPointIndex = nearestNeighbor[currentPointIndex];
		        }
		        if (currentPointIndex != -1) {
		            int clusterLabel = pointList[currentPointIndex].clusterID;
		            if (clusterLabel != -1) {
		                pointList[i].clusterID = clusterLabel;
		                clusters.get(clusterLabel).addVector(pointList[i].array);
		            }
		        }
		    }
		}


		if (DEBUG_MODE) {
			System.out.println("=== Clusters ===");
			for (Cluster cluster : clusters) {
				System.out.println(cluster);
			}
		}
		return clusters;
	}

	/**
	 * Save the clusters to an output file
	 *
	 * @param output the output file path
	 * @throws IOException exception if there is some writing error.
	 */
	public void saveToFile(String output) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));

		// First, we will print the attribute names
		for (String attributeName : attributeNames) {
			writer.write("@ATTRIBUTEDEF=" + attributeName);
			writer.newLine();
		}

		// for each cluster
		for (int i = 0; i < clusters.size(); i++) {
			// if the cluster is not empty
			if (clusters.get(i).getVectors().size() >= 1) {
				// write the cluster
				writer.write(clusters.get(i).toString());
				// if not the last cluster, add a line return
				if (i < clusters.size() - 1) {
					writer.newLine();
				}
			}
		}
		// close the file
		writer.close();
	}

	/**
	 * Print statistics of the latest execution to System.out.
	 */
	public void printStatistics() {
		System.out.println("========== DPC - SPMF 63 - STATS ============");
		System.out.println(" Distance function: " + distanceFunction.getName());
		System.out.println(" Total time ~: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory:" + MemoryLogger.getInstance().getMaxMemory() + " mb ");
		System.out.println(" Cluster count: " + clusters.size());
		System.out.println("=====================================");
	}

	/**
	 * A class representing a point from the DPC algorithm
	 */
	static class Point {
		/** The array of values */
		DoubleArray array;
		/** The delta value */
		double delta = Double.MAX_VALUE;
		/** The rho (density value) of that point */
		int rho;
		/** The index of the cluster that this point belongs to */
		int clusterID = -1;

		/**
		 * Constructor
		 * 
		 * @param doubleArray point
		 */
		Point(DoubleArray doubleArray) {
			this.array = doubleArray;
		}
	}

}