package ca.pfv.spmf.test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.other_dataset_tools.FixItemIDsTransactionDatabaseWithUtilityTool;

public class MainTestFixItemIDsTransactionDatabaseWithUtility {

	
	public static void main(String[] args) throws NumberFormatException, IOException {
		
		String inputPath = fileToPath("DB_Utility_broken.txt");
		String outputPath = "Output.txt";
		
		// This is a parameter that indicates that we want to increase the item ids by 1
		int increment = -1;
				
				
		FixItemIDsTransactionDatabaseWithUtilityTool tool = new FixItemIDsTransactionDatabaseWithUtilityTool();
		
		tool.convert(inputPath, outputPath, increment);
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFixItemIDsTransactionDatabaseWithUtility.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
