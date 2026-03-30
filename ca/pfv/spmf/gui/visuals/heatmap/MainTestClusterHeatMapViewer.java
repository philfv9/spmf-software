package ca.pfv.spmf.gui.visuals.heatmap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.clustering.clusterreader.AlgoClusterReader;
import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceEuclidian;
import ca.pfv.spmf.gui.viewers.clusterviewer.MainTestClusterViewerFile;
import ca.pfv.spmf.patterns.cluster.Cluster;

/**
 * Class to test the HeatMapViewer
 * @author Philippe Fournier-Viger
 */
public class MainTestClusterHeatMapViewer {
	public static void main(String[] args) throws IOException {
		// the input file
		String input = fileToPath("clustersFound.txt");  
		
		// Applying the  algorithm
		AlgoClusterReader algorithm = new AlgoClusterReader();
		List<Cluster> clusters = algorithm.runAlgorithm(input);
		algorithm.printStats();

		AlgoViewClusterHeatmap algo = new AlgoViewClusterHeatmap();
		algo.runAlgorithm(clusters, new DistanceEuclidian());
		
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestClusterViewerFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}