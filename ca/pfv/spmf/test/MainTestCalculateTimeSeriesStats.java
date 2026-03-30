package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.TimeSeriesStats;

/**
 * Example of how to calculate stats about time series from a file
 * @author Philippe Fournier-Viger, 2024.
 */
public class MainTestCalculateTimeSeriesStats {

	public static void main(String [] arg) throws IOException{
		
		// the input file
		String input = fileToPath("contextSAX.txt");  

		// Parameters of the algorithm
		String separator = ",";
		
		// Applying the  algorithm
		TimeSeriesStats algorithm = new TimeSeriesStats();
		algorithm.runAlgorithm(input, separator);
		
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestCalculateTimeSeriesStats.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
