package ca.pfv.spmf.tools.dataset_stats;

import java.io.IOException;
import java.util.List;

import ca.pfv.spmf.algorithms.timeseries.TimeSeries;
import ca.pfv.spmf.algorithms.timeseries.reader_writer.AlgoTimeSeriesReader;

/**
 * This class reads time series data and calculates statistics
 * about the time series, then it prints the statistics to the console.
 * <br/><br/>
 * In this version, this class reads the time series data into memory before calculating the
 * statistics. It could be optimized.
 * 
 * @author Your Name
 */
public class TimeSeriesStats {

	/**
	 * This method generates statistics for time series data (a file)
	 * @param inputFile the path to the file
	 * @param separator  Separator for the time series data (e.g., comma, semicolon)
	 * @throws IOException exception if there is a problem while reading the file.
	 */
	public void runAlgorithm(String inputFile, String separator) throws IOException {
		/////////////////////////////////////
		// (1) First, we will read the time series data into memory.
		/////////////////////////////////////
		
		AlgoTimeSeriesReader algorithm = new AlgoTimeSeriesReader();
		List<TimeSeries> timeSeriesList = algorithm.runAlgorithm(inputFile, separator);

		/////////////////////////////////////
		// We finished reading the time series data into memory.
		// We will calculate statistics on this time series data.
		/////////////////////////////////////

		System.out.println("============ TIME SERIES STATS ==========");
		System.out.println("Number of time series: " + timeSeriesList.size());
		
		// Calculate statistics for each time series
		for(TimeSeries series : timeSeriesList) {
			System.out.println(" Statistics for time series: " + series.getName());
			System.out.println("   Number of data points: " + series.size());
			
			// Find min, max, and average values
			double min = Double.MAX_VALUE;
			double max = Double.MIN_VALUE;
			double sum = 0;
			for(int i = 0; i < series.size(); i++) {
				double value = series.get(i);
				min = Math.min(min, value);
				max = Math.max(max, value);
				sum += value;
			}
			double average = sum / series.size();
			
			System.out.println("   Min value: " + min);
			System.out.println("   Max value: " + max);
			System.out.println("   Average value: " + average);
			System.out.println("=========================================");
		}
	}
}
