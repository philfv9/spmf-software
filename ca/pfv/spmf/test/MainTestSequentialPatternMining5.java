package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.AlgoFournierViger08;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalpatterns.AlgoDim;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.AlgoSeqDim;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequenceDatabase;

/**
 * Example of multidimensional sequential pattern mining (example 4).
 * @author Philippe Fournier-Viger
 */
public class MainTestSequentialPatternMining5 {

	public static void main(String [] arg) throws IOException{   
		String input = fileToPath("ContextMDSequence.txt");
		String output = ".//output.txt";
		
		// Multi-dimensional frequent sequential patterns with timestamps
		
		
		// Load a sequence database
		MDSequenceDatabase contextMDDatabase  = new MDSequenceDatabase(); //
		contextMDDatabase.loadFile(input);
		contextMDDatabase.printDatabase();
		
		AlgoDim algoDim = new AlgoDim(false, false); // <-- here
		
		AlgoSeqDim algoSeqDim2 = new AlgoSeqDim();
		// Minimum absolute support = 50 %
		double minsupp = 0.50;
		
		// Apply algorithm
		AlgoFournierViger08 algoPrefixSpanHirateClustering 
		= new AlgoFournierViger08(minsupp,
				0, Long.MAX_VALUE, 0, Long.MAX_VALUE, null, false, false);  
		algoSeqDim2.runAlgorithm(contextMDDatabase, algoPrefixSpanHirateClustering, algoDim, false, output);
		
		// Print results
		algoSeqDim2.printStatistics(contextMDDatabase.size());
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestSequentialPatternMining5.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}


