package ca.pfv.spmf.algorithms.classifiers.general;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.general.Evaluator.ClassificationResults;
import ca.pfv.spmf.algorithms.classifiers.general.Evaluator.TrainingResults;

public class OverallResults{

	List<Long> runtimeToTrain = new ArrayList<Long>();
	List<Double> memoryToTrain = new ArrayList<Double>();
	List<Double> avgRuleCount = new ArrayList<Double>();
	
	List<ConfusionMatrix> listMatrixOnTraining = new ArrayList<ConfusionMatrix>();
	List<Long> runtimeOnTraining = new ArrayList<Long>();
	List<Double> memoryUsageOnTraining = new ArrayList<Double>();

	List<ConfusionMatrix> listMatrixOnTesting= new ArrayList<ConfusionMatrix>();
	List<List<Short>> predictedClasseOnTesting = new ArrayList<List<Short>>();
	List<Long> runtimeOnTesting = new ArrayList<Long>();
	List<Double> memoryUsageOnTesting = new ArrayList<Double>();

//	Format df = new DecimalFormat("#.####");
	
	List<String> names = new ArrayList<String>();
	int algorithmCount;
	
	/** Double formatting code */
	private static final String DOUBLE_FORMAT = "%.4f";
	
	/**
	 * Convert a double to a formatted string
	 * 
	 * @param value the double value
	 * @return the strings
	 */
	private String asString(double value) {
		return String.format(DOUBLE_FORMAT, value);
	}
	
	public OverallResults(List<String> names){
		this.names = names;
		this.algorithmCount = names.size();
		
		// result for building the model
		runtimeToTrain = new ArrayList<Long>();
		memoryToTrain = new ArrayList<Double>(algorithmCount);
		
		// results for classification on the training set
		listMatrixOnTraining = new ArrayList<ConfusionMatrix>(algorithmCount);
		runtimeOnTraining = new ArrayList<Long>(algorithmCount);
		memoryUsageOnTraining = new ArrayList<Double>(algorithmCount);
		
		// results for classification  on the testing set
		listMatrixOnTesting= new ArrayList<ConfusionMatrix>(algorithmCount);
		predictedClasseOnTesting = new ArrayList<List<Short>>(algorithmCount);
		runtimeOnTesting = new ArrayList<Long>(algorithmCount);
		memoryUsageOnTesting = new ArrayList<Double>(algorithmCount);
	}
	
	public void addResults(ClassificationResults resultsOnTraining, 
			ClassificationResults resultsOnTesting, 
			TrainingResults trainResults) {
		if(trainResults != null) {
			runtimeToTrain.add(trainResults.runtime);
			memoryToTrain.add(trainResults.memory);
			avgRuleCount.add(trainResults.avgRuleCount);
		}
		
		if(resultsOnTraining != null) {
			listMatrixOnTraining.add(resultsOnTraining.matrix);
			runtimeOnTraining.add(resultsOnTraining.runtime);
			memoryUsageOnTraining.add(resultsOnTraining.memory);
			
		}
		if(resultsOnTesting != null) {
			listMatrixOnTesting.add(resultsOnTesting.matrix);
			predictedClasseOnTesting.add(resultsOnTesting.predictedClasses);
			runtimeOnTesting.add(resultsOnTesting.runtime);
			memoryUsageOnTesting.add(resultsOnTesting.memory);
		}
	}
	

	/**
	 * Save metrics to a file
	 * 
	 * @param metricsReportPath the file path
	 */
	public void saveMetricsResultsToFile(String toTrainpath, String onTrainingPath, String onTestingPath) {
		try {
			if(toTrainpath != null) {
				PrintWriter metricsWriter = new PrintWriter(toTrainpath, "UTF-8");
				metricsWriter.write(trainingMetricsToString(runtimeToTrain, memoryToTrain));
				metricsWriter.close();
			}
			
			if(onTrainingPath != null) {
				PrintWriter metricsWriter = new PrintWriter(onTrainingPath, "UTF-8");
				metricsWriter.write(metricsToString(listMatrixOnTraining, runtimeOnTraining, memoryUsageOnTraining));
				metricsWriter.close();
			}
			if(onTestingPath != null) {
				PrintWriter metricsWriter = new PrintWriter(onTestingPath, "UTF-8");
				metricsWriter.write(metricsToString(listMatrixOnTesting, runtimeOnTesting, memoryUsageOnTesting));
				metricsWriter.close();
			}
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Print statistics to the console
	 */
	public void printStats() {
		System.out.println("=== MODEL TRAINING RESULTS ===");
		System.out.println(trainingMetricsToString(runtimeToTrain, memoryToTrain));
		// training
		System.out.println("==== CLASSIFICATION RESULTS ON TRAINING DATA =====");
		System.out.println(metricsToString(listMatrixOnTraining, runtimeOnTraining, memoryUsageOnTraining));
		System.out.println();
		// testing
		System.out.println("==== CLASSIFICATION RESULTS ON TESTING DATA =====");
		System.out.println(metricsToString(listMatrixOnTesting, runtimeOnTesting, memoryUsageOnTesting));
	}
	
	private String trainingMetricsToString(List<Long> runtimes, List<Double> memoryUsages) {
		StringBuilder builder = new StringBuilder();
		//========
		builder.append("#NAME:\t");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + names.get(i));
		}
		builder.append(System.lineSeparator());
		//====
		
		builder.append("#RULECOUNT:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(avgRuleCount.get(i)));
		}
		builder.append(System.lineSeparator());
		// ===
		
		builder.append("#TIMEms:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + runtimes.get(i));
		}
		builder.append(System.lineSeparator());
		//========
		builder.append("#MEMORYmb:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(memoryUsages.get(i)));
		}
		builder.append(System.lineSeparator());

		return builder.toString();
	}

	private String metricsToString(List<ConfusionMatrix> listMatrix,
			List<Long> runtimes,
			List<Double> memoryUsages) {
		StringBuilder builder = new StringBuilder();
		//========
		builder.append("#NAME:\t");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + names.get(i));
		}
		//========
		builder.append(System.lineSeparator());
		builder.append("#ACCURACY:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(listMatrix.get(i).getAccuracy()));
		}
		builder.append(System.lineSeparator());
		//========
		builder.append("#RECALL:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(listMatrix.get(i).getAverageRecall()));
		}
		builder.append(System.lineSeparator());
		//========
		builder.append("#PRECISION:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(listMatrix.get(i).getAveragePrecision()));
		}
		builder.append(System.lineSeparator());
		//========
		builder.append("#KAPPA:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(listMatrix.get(i).getKappa()));
		}
		builder.append(System.lineSeparator());
		//========
		builder.append("#FMICRO:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(listMatrix.get(i).getMicroFMeasure()));
		}
		builder.append(System.lineSeparator());
		//========x
		builder.append("#FMACRO:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(listMatrix.get(i).getMacroFMeasure()));
		}
		builder.append(System.lineSeparator());

		//========x
		builder.append("#TIMEms:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + runtimes.get(i));
		}
		builder.append(System.lineSeparator());
		//========
		builder.append("#MEMORYmb:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + asString(memoryUsages.get(i)));
		}
		builder.append(System.lineSeparator());
		//========x
		builder.append("#NOPREDICTION:");
		for(int i =0; i< algorithmCount; i++) {
			builder.append("\t" + listMatrix.get(i).getNopredictions());
		}
		
		return builder.toString();
	}
}