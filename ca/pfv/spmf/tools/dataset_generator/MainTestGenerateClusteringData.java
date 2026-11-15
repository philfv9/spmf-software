package ca.pfv.spmf.tools.dataset_generator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.algorithms.clustering.instancereader.AlgoInstanceFileReader;
import ca.pfv.spmf.gui.viewers.instanceviewer.InstanceViewer;
import ca.pfv.spmf.patterns.cluster.DoubleArray;
import ca.pfv.spmf.tools.dataset_generator.ClusteringDataGenerator.Distribution;
import ca.pfv.spmf.tools.dataset_generator.ClusteringDataGenerator.NormalDistribution;
import ca.pfv.spmf.tools.dataset_generator.ClusteringDataGenerator.UniformDistribution;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * 
 * Do not remove copyright and license information
 */
/**
 * Example of how to use the synthetic clustering data generator from the source
 * code.
 */
public class MainTestGenerateClusteringData {

	public static void main(String[] arg) throws IOException {
		String outputFile = ".//output.txt";
		
		int attributeCount = 2;
		
		// We want to generate two clusters, one with 100 points and one with 50 points
		List<Integer> clusterSizes = Arrays.asList(300, 300, 300);

		// We want to use different distributions for each cluster
		// For the first cluster, we use a normal distribution with mean 0 and std 1 for both attributes
		// For the second cluster, we use a uniform distribution in the range [-5, 5] for both attributes
		List<Distribution[]> clusterDistributions = new ArrayList<>();
		clusterDistributions.add(new Distribution[] {new NormalDistribution(10, 3), new NormalDistribution(20, 3)});
		clusterDistributions.add(new Distribution[] {new UniformDistribution(-5, 5), new UniformDistribution(-5, 5)});
		clusterDistributions.add(new Distribution[] {new NormalDistribution(20, 2), new NormalDistribution(0, 2)});

		// We generate the dataset and write it to a file
		ClusteringDataGenerator.generateDataset(clusterSizes, attributeCount, clusterDistributions, "output.txt");
		
		// ==================== CODE TO VISUALIZE THE GENERATED DATA =================
		// Parameters of the algorithm
		String separator = " ";

		// Applying the algorithm
		AlgoInstanceFileReader algorithm = new AlgoInstanceFileReader();
		List<DoubleArray> instances = algorithm.runAlgorithm(outputFile, separator);
		List<String> attributeNames = algorithm.getAttributeNames();

		InstanceViewer viewer = new InstanceViewer(instances, attributeNames);
		viewer.setVisible(true);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestGenerateClusteringData.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
