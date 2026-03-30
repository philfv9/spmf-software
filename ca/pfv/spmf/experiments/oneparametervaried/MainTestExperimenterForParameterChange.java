package ca.pfv.spmf.experiments.oneparametervaried;

import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * This is a class to run experiments where multiple algorithms are compared
 * in terms of performance when and one parameter is varied.
 * 
 * @author philippe
 */
public class MainTestExperimenterForParameterChange {

	public static void main(String[] args) throws Exception {

		// The input file
		String inputFile = fileToPath("contextPasquier99.txt");
		
		// The directory where results from the experiments will be stored.
		String outputDirectory = "EXPERIMENTS";

		// Create the tool to run experiment
		ExperimenterForParameterChange experimenter = new ExperimenterForParameterChange();
		
		// Here we must give a path to the spmf.jar file on your computer
		//  If you did not download spmf.jar, you need to download it
		// from the download page on the SPMF website
		experimenter.setSPMFJarFilePath("C:\\\\Users\\\\philippe\\\\Desktop\\\\spmf.jar");
		
		// We indicate that if an algorithm run out of time, 
		// the result should be indicated using the "-" string
		experimenter.setTimeoutCodeS("-");

		// A list of algorithm to be compared.
		// They must have the same parameters
		String algorithmNames[] = new String[] { "FPGrowth_association_rules", "Apriori_association_rules" };

		// The default list of parameter values
		// The code ## should be used to indicate the parameter that will be varied
		// in the experiment.
		// Below, I have indicated that I want to vary the first parameter.
		String params[] = new String[] { "##", "0.6" };
		
		// The values for the parameter that is varied
		// Below, it means that we will vary the first parameter using the 
		// following values
		String varyingParameterValues[] = new String[] { "0.01", "0.02", "0.03", "0.04", "0.05" };
		
		// The name of the varied parameter (just for  printing purpose
		String variedParameterName = "minsup";
		
		// The maximum time to run each algorithm
		// If an algorithm exceed that time limit, it will be stopped.
		int timeoutInMilliseconds = 100000;
		
		// If true, the output file sizes will be compared in the results
		boolean compareOutputSizes = true;
		
		// If true, the command for running each algorithm will be shown in
		// the console. This is useful for debugging but not very important.
		boolean showCommand = false;
		
		// If true, latex figures will be generated
		boolean latexFigures = true;

		// Run the experiments.
		experimenter.runAnAlgorithmAndVaryParameter(algorithmNames, params, varyingParameterValues, inputFile,
				outputDirectory, timeoutInMilliseconds, compareOutputSizes, showCommand, latexFigures,variedParameterName);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestExperimenterForParameterChange.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}

}
