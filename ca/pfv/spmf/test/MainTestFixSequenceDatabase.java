package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.other_dataset_tools.FixSequenceDatabaseTool;

/**
 * Example of how to fix common problems for a sequence database
 * in SPMF format.
 * @author Philippe Fournier-Viger
 */
public class MainTestFixSequenceDatabase {

	public static void main(String [] arg) throws IOException{
		
		String inputFile = fileToPath("sequence_broken.txt");
		String outputFile = "output.txt";
		
		FixSequenceDatabaseTool tool = new FixSequenceDatabaseTool();
		tool.convert(inputFile, outputFile);
		

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFixSequenceDatabase.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
