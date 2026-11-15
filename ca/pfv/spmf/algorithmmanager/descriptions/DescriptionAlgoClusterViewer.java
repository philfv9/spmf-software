package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.util.List;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.clustering.clusterreader.AlgoClusterReader;
import ca.pfv.spmf.gui.viewers.clusterviewer.ClusterViewer;
import ca.pfv.spmf.patterns.cluster.Cluster;
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
 * This class describes the algorithm to visualize a set of clusters.
 * 
 * @see ClusterViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoClusterViewer extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoClusterViewer(){
	}

	@Override
	public String getName() {
		return "Vizualize_clusters_of_instances";
	}

	@Override
	public String getAlgorithmCategory() {
		return "CLUSTERING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/ClusterViewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		
		// Applying the  algorithm
		AlgoClusterReader algorithm = new AlgoClusterReader();
		List<Cluster> clusters = algorithm.runAlgorithm(inputFile);
		List<String> attributeNames =  algorithm.getAttributeNames();
		
		// Otherwise, we use the cluster viewer
		ClusterViewer viewer = new ClusterViewer(clusters, attributeNames);
		viewer.setVisible(true);

	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Clusters"};
	}


	@Override
	public String[] getOutputFileTypes() {
		return null;
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_VIEWER;
	}
	
}
