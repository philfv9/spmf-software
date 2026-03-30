package ca.pfv.spmf.test;

import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.classifiers.acac.AlgoACAC;
import ca.pfv.spmf.algorithms.classifiers.accf.AlgoACCF;
import ca.pfv.spmf.algorithms.classifiers.acn.AlgoACN;
import ca.pfv.spmf.algorithms.classifiers.adt.AlgoADT;
import ca.pfv.spmf.algorithms.classifiers.cba.AlgoCBA;
import ca.pfv.spmf.algorithms.classifiers.cmar.AlgoCMAR;
import ca.pfv.spmf.algorithms.classifiers.data.StringDataset;
import ca.pfv.spmf.algorithms.classifiers.decisiontree.id3.AlgoID3;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.Evaluator;
import ca.pfv.spmf.algorithms.classifiers.general.OverallResults;
import ca.pfv.spmf.algorithms.classifiers.knn.AlgoKNN;
import ca.pfv.spmf.algorithms.classifiers.l3.AlgoL3;
import ca.pfv.spmf.algorithms.classifiers.mac.AlgoMAC;

/**
 * Example of how to run the CBA algorithm
 * @author Philippe Fournier-Viger, 2020
 *
 */
public class MainTestEVALUATOR_kfold {

	
	public static void main(String[] args) throws Exception {

		// The target attribute
		String targetClassName = "play";

		System.out.println("========= READ THE DATASET ==========");
		String datasetFile = fileToPath("tennisExtended.txt");
		StringDataset dataset = new StringDataset(datasetFile, targetClassName);
		
		// If the dataset is in ARFF format, then use these lines instead:
//		String datasetPath = fileToPath("weather-train.arff");
//		ARFFDataset dataset = new ARFFDataset(datasetPath, targetClassName);
		
		// If the dataset is in CSV format, then use these lines instead:
//		String datasetPath = fileToPath("tennisExtendedCSV.txt");
//		CSVDataset dataset = new CSVDataset(datasetPath, targetClassName);
		
		dataset.printStats();
//		dataset.printInternalRepresentation();
//		dataset.printStringRepresentation();
		
		System.out.println("========= PREPARE ALGORITHMS ==========");
		
		//========================= CLASSIFIER 1
		double minSup = 0.1;
		double minConf = 0.4;
		double minAllConf = 0.5;
		ClassificationAlgorithm algorithmACAC = new AlgoACAC(minSup, minConf, minAllConf);

		//========================= CLASSIFIER 2
		ClassificationAlgorithm algorithmACCF = new AlgoACCF(minSup, minConf);
		
		// ======================== CLASSIFIER 3
	    double minAcc = 0.55;
	    double minCorr = 0.2;
		ClassificationAlgorithm algorithmACN = new AlgoACN(minSup, minConf, minAcc, minCorr);
		
		// ======================== CLASSIFIER 4
		ClassificationAlgorithm algorithmADT = new AlgoADT(minSup, minConf);
		
		// ======================== CLASSIFIER 5
		ClassificationAlgorithm algorithmCBA = new AlgoCBA(minSup, minConf,false);
		
		// ======================== CLASSIFIER 6
		ClassificationAlgorithm algorithmCBA2 = new AlgoCBA(minSup, minConf,true);
		
		// ======================== CLASSIFIER 7
		int delta = 2;
		ClassificationAlgorithm algorithmCMAR = new AlgoCMAR(minSup, minConf,delta);

		// ======================== CLASSIFIER 8
		ClassificationAlgorithm algorithmL3 = new AlgoL3(minSup, minConf);
		
		// ======================== CLASSIFIER 9
		ClassificationAlgorithm algorithmMAC = new AlgoMAC(minSup, minConf);
		
		// ======================== CLASSIFIER 10
		ClassificationAlgorithm algorithmID3 = new AlgoID3();
		
		// ========================= CLASSIFIER 11
		int k = 3;
		ClassificationAlgorithm algorithmKNN = new AlgoKNN(k);

//		double deltaCPAR = 0.05;
//	    double minBestGain = 0.7;
//	    double alpha = 2 / 3.0;
//	    int k = 5;
//		ClassificationAlgorithm algorithmCPAR = new AlgoCPAR(deltaCPAR,minBestGain, alpha, k);
		
		ClassificationAlgorithm[] algorithms = new ClassificationAlgorithm[] {algorithmACAC, algorithmACCF, algorithmACN, algorithmADT, algorithmCBA,
				 algorithmCBA2, algorithmMAC,algorithmL3,algorithmCMAR,algorithmID3,algorithmKNN};
		
		System.out.println("========= RUN EXPERIMENTS==========");
		// Save the class report for the training data
		String forTrainingPath = "outputReportForTraining.txt";
		String onTrainingPath = "outputReportOnTraining.txt";
		String onTrestingPath = "outputReportOnTesting.txt";

		Evaluator experiment1 = new Evaluator();
		int kFoldCount = 3;
		OverallResults allResults = experiment1.trainAndRunClassifiersKFold(algorithms, dataset, kFoldCount);
		allResults.saveMetricsResultsToFile(forTrainingPath,onTrainingPath, onTrestingPath);
		allResults.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestEVALUATOR_kfold.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
