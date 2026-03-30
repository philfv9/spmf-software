package ca.pfv.spmf.test;

/* This file is copyright (c) 2021 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.classifiers.acac.AlgoACAC;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.data.StringDataset;
import ca.pfv.spmf.algorithms.classifiers.general.Classifier;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Example of how to run the ACAC algorithm 
 *  @author Philippe Fournier-Viger, 2021
 *
 */
public class MainTestACAC_single_prediction {

	
	public static void main(String[] args) throws Exception {
		
		//********************************************************
		//**************** READ A DATASET IN MEMORY ************
		//********************************************************
		System.out.println("========= Step 1: Read the dataset in memory ==========");

		// We choose "play" as the target attribute that we want to predict using the other attributes
		String targetClassName = "play";

		// Load the dataset in memory.
		//   If the dataset is in SPMF format:
		String datasetPath = fileToPath("tennisExtended.txt");
		StringDataset dataset = new StringDataset(datasetPath, targetClassName);
		
		//  If the dataset is in ARFF format, then use these lines instead:
//		String datasetPath = fileToPath("weather-train.arff");
//		ARFFDataset dataset = new ARFFDataset(datasetPath, targetClassName);
		
		// If the dataset is in CSV format, then use these lines instead:
//		String datasetPath = fileToPath("tennisExtendedCSV.txt");
//		CSVDataset dataset = new CSVDataset(datasetPath, targetClassName);

		// Use the following line to see statistics about the dataset
		dataset.printStats();
		
		// For debugging we could print the dataset as it is loaded in memory:
//		dataset.printInternalRepresentation();
		dataset.printStringRepresentation();
		
		//********************************************************
		//**************** TRAIN THE MODEL (classifier) **********
		//********************************************************
		System.out.println();
		System.out.println("==== Step 2: Train the model and run automated classification experiments on the dataset===");
		System.out.println();
		// Parameters of the algorithm for training
		double minSup = 0.1;
		double minConf = 0.8;
		double minAllConf = 0.5;
		
		// Train the model on the training data and make predictions  on the testing data
		Classifier classifier = new AlgoACAC(minSup, minConf, minAllConf).trainAndCalculateStats(dataset);
		
		//****************************************
		//**************** OPTIONAL **************
		//****************************************
		//  If you want to keep the rules of the trained model, you may create
		// a file in SPMF format and put the rules inside.
		String rulesPath = "rulesPath.txt";
		((RuleClassifier)classifier).writeRulesToFileSPMFFormatAsStrings(rulesPath,dataset);
//		((RuleClassifier)classifier).writeRulesToFileSPMFFormatAsNumbers(rulesPath);

		//****************************************
		//**************** OPTIONAL **************
		//****************************************
		// If you want to save a trained model so that you can load it into memory later,
		// you can do as follows.
		// First, save the classifier to a file using serialization:
//		System.out.println(" Save the classifier to a file");
//		classifier.saveTrainedClassifierToFile("classifier.ser");
		
		// Second, you can the classifier into memory:
//		System.out.println(" Read the classifier from a file");
//		classifier = Classifier.loadTrainedClassifierToFile("classifier.ser");

		//********************************************************
		//***** USE THE MODEL TO MAKE A PREDICTION      **********
		//********************************************************
		System.out.println(" Making a prediction for the record: {rainy, mild, high, strong, monday, small}");
		Instance instance = dataset.stringToInstance(new String[] {"rainy", "mild", "high", "strong", "monday", "small"});
		short result = classifier.predict(instance);
		System.out.println("    The predicted value is: " + dataset.getStringCorrespondingToItem(result));

	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestACAC_single_prediction.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
