package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.clustering.instancereader.AlgoInstanceFileReader;
import ca.pfv.spmf.gui.viewers.instanceviewer.InstanceViewer;
import ca.pfv.spmf.patterns.cluster.DoubleArray;
import ca.pfv.spmf.tools.dataset_generator.ClusteringDataGenerator;
import ca.pfv.spmf.tools.dataset_generator.ClusteringDataGenerator.Distribution;
import ca.pfv.spmf.tools.dataset_generator.ClusteringDataGenerator.NormalDistribution;
import ca.pfv.spmf.tools.dataset_generator.ClusteringDataGenerator.UniformDistribution;

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
 * This class describes the algorithm to generate a clustering dataset. It is
 * designed to be used by the graphical and command line interface.
 * 
 * @see ClusteringDataGenerator
 * @author Philippe Fournier-Viger, 2024
 */
public class DescriptionAlgoGenerateAClusteringDataset extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoGenerateAClusteringDataset() {
	}

	@Override
	public String getName() {
		return "Generate_a_clustering_dataset";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA GENERATORS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/clustering_data_generator.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int pointCount = getParamAsInteger(parameters[0]);
		int attributeCount = getParamAsInteger(parameters[1]);
		boolean visualizeData = getParamAsBoolean(parameters[2]);

		// We want to use different distributions for each cluster
		// For the first cluster, we use a normal distribution with mean 0 and std 1 for
		// both attributes
		// For the second cluster, we use a uniform distribution in the range [-5, 5]
		// for both attributes
		List<Distribution[]> clusterDistributions = new ArrayList<>();

		clusterDistributions.add(getParamAsDistributionArray(parameters[3]));

		if (parameters.length >= 5 && "".equals(parameters[4]) == false) {
			clusterDistributions.add(getParamAsDistributionArray(parameters[4]));
		}
		if (parameters.length >= 6 && "".equals(parameters[5]) == false) {
			clusterDistributions.add(getParamAsDistributionArray(parameters[5]));
		}
		if (parameters.length >= 7 && "".equals(parameters[6]) == false) {
			clusterDistributions.add(getParamAsDistributionArray(parameters[6]));
		}
		if (parameters.length >= 8 && "".equals(parameters[7]) == false) {
			clusterDistributions.add(getParamAsDistributionArray(parameters[7]));
		}

		// We want to generate two clusters, one with 100 points and one with 50 points
		List<Integer> clusterSizes = new ArrayList<Integer>();
		for (int i = 0; i < clusterDistributions.size(); i++) {
			clusterSizes.add(pointCount);
		}

		// We generate the dataset and write it to a file
		ClusteringDataGenerator.generateDataset(clusterSizes, attributeCount, clusterDistributions, outputFile);

		System.out.println("Clustering dataset generated.  ");

		if (visualizeData) {
			// Applying the algorithm
			AlgoInstanceFileReader algorithm = new AlgoInstanceFileReader();
			List<DoubleArray> instances = algorithm.runAlgorithm(outputFile, " ");
			List<String> attributeNames = algorithm.getAttributeNames();

			InstanceViewer viewer = new InstanceViewer(instances, attributeNames);
			viewer.setVisible(true);
		}
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[8];
		parameters[0] = new DescriptionOfParameter("Point per cluster", "(e.g. 200)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Attribute count", "(e.g. 2)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Visualize generated data", "(e.g. true)", Distribution[].class,
				false);
		parameters[3] = new DescriptionOfParameter("Distributions cluster 1", "(e.g. Normal(20,30) Uniform(14,4)",
				Distribution[].class, false);
		parameters[4] = new DescriptionOfParameter("Distributions cluster 2", "(e.g. Normal(20,30) Uniform(14,4)",
				Distribution[].class, true);
		parameters[5] = new DescriptionOfParameter("Distributions cluster 3", "(e.g. Normal(20,30) Uniform(14,4)",
				Distribution[].class, true);
		parameters[6] = new DescriptionOfParameter("Distributions cluster 4", "(e.g. Normal(20,30) Uniform(14,4)",
				Distribution[].class, true);
		parameters[7] = new DescriptionOfParameter("Distributions cluster 5", "(e.g. Normal(20,30) Uniform(14,4)",
				Distribution[].class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return null;
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Database of instances", "Database of double vectors" };
	}

	// A helper method to parse the distributions parameter as an array of
	// Distribution objects
	private Distribution[] getParamAsDistributionArray(String parameter) {
		// We check if the parameter string is null or empty
		if (parameter == null || parameter.isEmpty()) {
			// We throw an exception with a message
			throw new IllegalArgumentException("The parameter string cannot be null or empty");
		}

		// We create an ArrayList to store the Distribution objects
		List<Distribution> distributions = new ArrayList<Distribution>();

		// We split the parameter string by commas
		String[] parts = parameter.split(" ");

		// We loop over the parts
		for (String part : parts) {
			// We trim the whitespace from the part
			part = part.trim();

			// We split the part by parentheses
			String[] subparts = part.split("\\(");

			// We check if the part has two subparts
			if (subparts.length != 2) {
				// We throw an exception with a message
				throw new IllegalArgumentException("Invalid distribution format: " + part);
			}

			// We get the distribution name from the first subpart
			String name = subparts[0];

			// We get the distribution parameters from the second subpart
			String parameters = subparts[1];

			// We remove the closing parenthesis from the parameters
			parameters = parameters.replace(")", "");

			// We split the parameters by commas
			String[] values = parameters.split(",");

			// We create a Distribution object based on the name
			switch (name) {
			case "Normal": // If the name is Normal
				// We check if the values array has two elements
				if (values.length != 2) {
					// We throw an exception with a message
					throw new IllegalArgumentException("Invalid normal distribution parameters: " + parameters);
				}

				// We parse the mean and standard deviation as doubles
				double mean = Double.parseDouble(values[0]);
				double std = Double.parseDouble(values[1]);
				System.out.println(" mean : "+ mean + " std: " + std);

				// We create a NormalDistribution object with the mean and standard deviation
				Distribution normal = new NormalDistribution(mean, std);

				// We add the NormalDistribution object to the list
				distributions.add(normal);
				break;
			case "Uniform": // If the name is Uniform
				// We check if the values array has two elements
				if (values.length != 2) {
					// We throw an exception with a message
					throw new IllegalArgumentException("Invalid uniform distribution parameters: " + parameters);
				}

				// We parse the minimum and maximum values as doubles
				double min = Double.parseDouble(values[0]);
				double max = Double.parseDouble(values[1]);

				// We create a UniformDistribution object with the minimum and maximum values
				Distribution uniform = new UniformDistribution(min, max);

				// We add the UniformDistribution object to the list
				distributions.add(uniform);
				break;
			}

		}

		// We convert the list to an array of Distribution objects
		Distribution[] array = distributions.toArray(new Distribution[distributions.size()]);
		// We return the array
		return array;
	}
	
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_GENERATOR;
	}
}
