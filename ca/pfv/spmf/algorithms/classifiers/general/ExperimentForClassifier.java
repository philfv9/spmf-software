
package ca.pfv.spmf.algorithms.classifiers.general;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Class to run some experiment on some classifier(s).
 */
public class ExperimentForClassifier {

	/** Confusion matrix for storing results */
	private ConfusionMatrix matrix;

	/** Predicted classes results (as ID) */
	List<Short> predictedClassesID;

	/** Classification runtime */
	long runtimeClassification;

	/** Memory usage */
	double memoryUsage;

	/**
	 * Constructor
	 */
	public ExperimentForClassifier() {
		// constructor
	}

	/**
	 * Run an experiment
	 * 
	 * @param classifier a classifier
	 * @param dataset    a dataset
	 */
	public void runExperiment(Classifier classifier, Dataset dataset) {
		MemoryLogger.getInstance().reset();
		runtimeClassification = System.currentTimeMillis();
		predictedClassesID = new ArrayList<Short>();
		matrix = new ConfusionMatrix();

		for (int i = 0; i < dataset.getInstances().size(); i++) {
			Instance instance = dataset.getInstances().get(i);
			short predictedKlassIndex = classifier.predict(instance);
			short realKlassIndex = instance.getKlass();

			predictedClassesID.add(predictedKlassIndex);
			matrix.add(realKlassIndex, predictedKlassIndex);
		}

		runtimeClassification = System.currentTimeMillis() - runtimeClassification;
		MemoryLogger.getInstance().checkMemory();
		memoryUsage = MemoryLogger.getInstance().getMaxMemory();
	}

	/**
	 * Save metrics to a file
	 * 
	 * @param metricsReportPath the file path
	 */
	public void saveMetricsResultsToFile(String metricsReportPath) {

		try {
			PrintWriter metricsWriter = new PrintWriter(metricsReportPath, "UTF-8");
			metricsWriter.write(metricsToString());
			metricsWriter.close();

		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Save predicted classes to a file
	 * 
	 * @param dataset                  the dataset
	 * @param predictedClassReportPath the output file path
	 */
	public void savePredictedClassesToFileAsString(Dataset dataset, String predictedClassReportPath) {

		try {
			PrintWriter predictedClassWriter = new PrintWriter(predictedClassReportPath, "UTF-8");
			predictedClassWriter.println("realKlass, predictedKlass");

			for (int i = 0; i < dataset.getInstances().size(); i++) {
				short predictedKlassIndex = predictedClassesID.get(i);
				short realKlassIndex = dataset.getInstances().get(i).getKlass();

				String realKlass = dataset.getStringCorrespondingToItem(realKlassIndex);
				String predictedKlass = dataset.getStringCorrespondingToItem(predictedKlassIndex);
				predictedClassWriter.println(realKlass + "," + predictedKlass);
			}
			predictedClassWriter.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Print statistics to the console
	 */
	/**
	 * Print statistics to the console
	 */
	public void printStats() {
		System.out.println(metricsToString());
	}
	
	/**
	 * Get metrics from the experiment
	 * @return the metrics as a string
	 */
	private String metricsToString() {
		StringBuilder builder = new StringBuilder();
		//========
		builder.append("#ACCURACY: " + matrix.getAccuracy());
		builder.append(System.lineSeparator());
		builder.append("#RECALL: " + matrix.getAverageRecall());
		builder.append(System.lineSeparator());
		builder.append("#PRECISION: " + matrix.getAveragePrecision());
		builder.append(System.lineSeparator());
		builder.append("#KAPPA: " + matrix.getKappa());
		builder.append(System.lineSeparator());
		builder.append("#F-MEASURE-MICRO: " + matrix.getMicroFMeasure());
		builder.append(System.lineSeparator());
		builder.append("#F-MEASURE-MACRO: " + matrix.getMacroFMeasure());
		builder.append(System.lineSeparator());
		builder.append("#NOPREDICTION-PERCENTAGE: " + matrix.getNopredictions());
		builder.append(System.lineSeparator());
		builder.append("#CLASSIFICATION-TIME-ms: " + runtimeClassification);
		builder.append(System.lineSeparator());
		builder.append("#MEMORY-mb: " + memoryUsage);
		
		return builder.toString();
	}
}
