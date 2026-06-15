package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import java.io.File;
import ca.pfv.spmf.algorithms.frequentpatterns.UBTGen.AlgoUBTGen;

/**
 * Example of how to use the UBTGen algorithm 
 * from the source code, and save the output to a file.
 * @author Srikumar Krishnamoorthy
 */
public class MainTestUBTGen {

	public static void main(String [] arg) throws IOException{
		
		//Set the input file
        String configFile = "configTitanic.txt"; 
		//String configFile = fileToPath(configFile);
		
		if (arg.length>0)
			configFile = arg[0];
			
        
        //Get the folder path of the file, to read the input file specified in the config file
        String folderPath = new File(fileToPath(configFile)).getParent(); 

		String[] args = new String[] { configFile };
		
        //Applying the UBTGen algorithm
		AlgoUBTGen algo = new AlgoUBTGen();
		algo.createOutputDirs();
		algo.runAlgorithm(folderPath, args);
		
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestUBTGen.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
