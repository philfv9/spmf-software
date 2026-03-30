package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_converter.Formats;
import ca.pfv.spmf.tools.dataset_converter.TransactionDatabaseConverter;

/**
 * Example of how to convert a sequence database with cost in SPMF format to 
 * a transaction database with cost in SPMF format.
 */
public class MainTestConvertCostSeqDBToTCosTransDB {
	
	public static void main(String [] arg) throws IOException{
		
		String inputFile = fileToPath("example_CEPN.txt");
		String outputFile = ".//output.txt";
		Formats inputFileformat = Formats.SPMF_COST_SEQUENCE_DB;
		int sequenceCount = Integer.MAX_VALUE;
		
		TransactionDatabaseConverter converter = new TransactionDatabaseConverter();
		converter.convert(inputFile, outputFile, inputFileformat, sequenceCount);
	}

	

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestConvertCostSeqDBToTCosTransDB.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
