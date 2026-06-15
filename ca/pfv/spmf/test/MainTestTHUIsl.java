package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import java.io.File;
import java.nio.file.*;
import java.io.*;

import ca.pfv.spmf.algorithms.frequentpatterns.THUIsl.AlgoTHUIsl;
import ca.pfv.spmf.algorithms.frequentpatterns.THUIsl.PrepareData;

/**
 * Example of how to use the THUIsl algorithm 
 * from the source code, and save the output to a file.
 * @author Srikumar Krishnamoorthy
 */
public class MainTestTHUIsl {

	public static void main(String [] args) throws Exception{
		
		String configFile = "configTitanic.txt";//actual data file (inputFile params) is read from the config file
		if (args.length>0)
			configFile = args[0];

		String input = "outputs/inpdata/";//location where processed files are written, and used as input in subsequent top-k HUI mining
		String output = "outputs/hui/";//location where the mined HUIs are stored
		
        int topK = 30; //top-k size
        int B = -1; //-1, bins are auto-determined, if B is set to -1
        int fsK = topK; //feature selection size
        boolean eucsPrune = false; //apply eucs pruning
        String dsName = "data"; //a prefix used in filenames
        int foldNo = 1; //default value, may be used as a file suffix when number of folds is greater than 1; currently disabled in AlgoTHUIsl.java file
        int L = 1; //default value
        double G = 1e-4; //default value
		double imbWeights = 1.0; //weights to be assigned to handle class imbalance
        boolean modelTest = false; //test file where mined patterns have to be applied?
		
		int numRows = 12; //set by reading the file
		int numIntCols = 2, numFloatCols = 2, numCatCols = 3; //set by reading the file
		int numClasses = 2; //set by reading the file
		
		Map<String, String> params = new HashMap<>();
		for (String arg : args) {
            if (arg.contains("=")) {
                String[] keyValue = arg.split("=", 2);
                params.put(keyValue[0], keyValue[1]);
            }
        }
		if (params.containsKey("input"))input = params.get("input");
		if (params.containsKey("output")) output = params.get("output");
		if (params.containsKey("topK")) topK = Integer.parseInt(params.get("topK"));
		if (params.containsKey("fsK")) fsK = Integer.parseInt(params.get("fsK"));
		else fsK=topK;
        if (params.containsKey("B")) B = Integer.parseInt(params.get("B"));
        if (params.containsKey("eucsprune")) eucsPrune = Boolean.parseBoolean(params.get("eucsprune"));
        //if (params.containsKey("dsName")) dsName = params.get("dsName");//set in config file
        if (params.containsKey("L")){
			if (params.get("L").equals("all")) L = -1;//mine all pattern lengths 
			else L = Integer.parseInt(params.get("L"));//mine max specified length of patterns 
		}
        if (params.containsKey("G")) G = Double.parseDouble(params.get("G"));
		if (params.containsKey("imbWeights")) imbWeights = Double.parseDouble(params.get("imbWeights"));
		if (params.containsKey("modelTest")) modelTest = Boolean.parseBoolean(params.get("modelTest"));
		
		String fileType = "train";
		if (modelTest) fileType = "test";
		String[] argsData = new String[] { fileToPath(configFile), fileType };
		
		PrepareData pd = new PrepareData();
		pd.run(argsData);
		dsName = pd.dsName;//read from config file, if present
		numIntCols = pd.numIntCols;
		numFloatCols = pd.numFloatCols;
		numCatCols = pd.numCatCols;
		numRows = pd.numRows;
		numClasses = pd.numClasses;
		
		//Applying the THUIsl algorithm
		AlgoTHUIsl topkalgo = new AlgoTHUIsl(topK);
		if (!modelTest){//training phase
			topkalgo.runAlgorithm(input, output, fsK, B, L, G, dsName, foldNo, imbWeights, numRows, numIntCols, numFloatCols, numCatCols, numClasses);
		}else{//test phase
			System.out.println("Test phase: patterns mined from train data are used as features to transform data.");
			topkalgo.applyPatterns(input, output, dsName, foldNo, numRows, numIntCols, numFloatCols, numCatCols);
		}
	}

	public static String fileToPath(String filename) throws Exception {
		URL url = MainTestTHUIsl.class.getResource(filename);
		if (url == null) throw new FileNotFoundException("File not found: " + filename);
		return Paths.get(url.toURI()).toAbsolutePath().toString();
	}
	
}
