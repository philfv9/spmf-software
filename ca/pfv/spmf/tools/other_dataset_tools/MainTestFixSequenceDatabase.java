package ca.pfv.spmf.tools.other_dataset_tools;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use the tool to increase all items ids by a given value in a transaction database
 * in SPMF format.
 */
public class MainTestFixSequenceDatabase {

	public static void main(String [] arg) throws IOException{
		
		String inputFile = fileToPath("online_best.txt");
		String outputFile = "online_modified.txt";
		
		FixSequenceDatabaseTool tool = new FixSequenceDatabaseTool();
		tool.convert(inputFile, outputFile);
		

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFixSequenceDatabase.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
