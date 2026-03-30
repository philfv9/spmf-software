package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.SequenceDBStats;

/**
 * Example of how to generate statistics about a sequence database
 */
public class MainTestGenerateSequenceDatabaseStats {
	
	public static void main(String [] arg) throws IOException{
		
		String inputFile = fileToPath("contextPrefixSpan.txt");
		try{
			SequenceDBStats sequenceDatabase = new SequenceDBStats(); 
			sequenceDatabase.runAlgorithm(inputFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestApriori_simple_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
