package ca.pfv.spmf.tools.dataset_stats;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to generate statistics about a graph database
 */
public class MainTestGenerateGraphDatabaseStats {

	public static void main(String[] arg) throws IOException {

		String inputFile = fileToPath("contextTKG.txt");
		try {
			GraphStatsGenerator graphDatabase = new GraphStatsGenerator();
			graphDatabase.getStats(inputFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestGenerateGraphDatabaseStats.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
