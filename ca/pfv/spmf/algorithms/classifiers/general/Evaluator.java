
package ca.pfv.spmf.algorithms.classifiers.general;

import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.data.VirtualDataset;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Class to run some experiment on some classifier(s).
 */
public class Evaluator {

	/** Debug mode */
	private boolean DEBUGMODE = false;
	

	class TrainingResults {
		long runtime = 0l;
		Double memory = 0d;
		double avgRuleCount = 0;
	}

	class ClassificationResults {
		ConfusionMatrix matrix = new ConfusionMatrix();
		List<Short> predictedClasses = new ArrayList<Short>();
		long runtime = 0l;
		Double memory = 0d;
	}

	/**
	 * Constructor
	 */
	public Evaluator() {
		// constructor
	}

	private void runOnInstancesAnUpdateResults(Dataset dataset, Classifier classifier, ClassificationResults results) {
 		MemoryLogger.getInstance().reset();
		long thisRuntime = System.currentTimeMillis();

		for (Instance instance : dataset.getInstances()) {
			short predictedKlassIndex = classifier.predict(instance);
			short realKlassIndex = instance.getKlass();

			results.predictedClasses.add(predictedKlassIndex);
			results.matrix.add(realKlassIndex, predictedKlassIndex);
		}
		results.runtime += System.currentTimeMillis() - thisRuntime;
		MemoryLogger.getInstance().checkMemory();
		results.memory += MemoryLogger.getInstance().getMaxMemory();
	}

	public OverallResults trainAndRunClassifiersHoldout(ClassificationAlgorithm[] algorithms, Dataset dataset,
			double percentage) throws Exception {
		List<String> names = new ArrayList<String>();
		for (ClassificationAlgorithm algorithm : algorithms) {
			names.add(algorithm.getName());
		}

		OverallResults allResults = new OverallResults(names);

		// percentage has to be in ]0,1[
		if (percentage <= 0d || percentage >= 1d) {
			throw new RuntimeException("Sampling percentage must be in the range [0,1]");
		}

		// Split the dataset in two parts
		Dataset[] datasets = VirtualDataset.splitDatasetForHoldout(dataset, percentage);
		Dataset training = datasets[0];
		Dataset testing = datasets[1];
		if (DEBUGMODE) {
			System.out.println("===== HOLDOUT SAMPLING =====");
			System.out.println("Holdout sampling with percentage = " + percentage);
			System.out.println("  - Original dataset: " + dataset.getInstances().size() + " records.");
			System.out.println("  - Training part: " + training.getInstances().size() + " records.");
			System.out.println("  - Testing part: " + testing.getInstances().size() + " records.");
			System.out.println("===== RUNNING =====");
		}

		// for each classifier
		for (ClassificationAlgorithm algorithm : algorithms) {
			if (DEBUGMODE) {
				System.out.println("Running algorithm ... " + algorithm.getName());
			}
			// Train the classifier
			Classifier classifier = algorithm.trainAndCalculateStats(training);
			TrainingResults trainResults = new TrainingResults();
			trainResults.memory += algorithm.getTrainingMaxMemory();
			trainResults.runtime += algorithm.getTrainingTime();
			if (classifier instanceof RuleClassifier) {
				trainResults.avgRuleCount += ((RuleClassifier) classifier).getNumberRules();
			}

			// Run on training set
			ClassificationResults resultsOnTraining = new ClassificationResults();
			runOnInstancesAnUpdateResults(training, classifier, resultsOnTraining);

			// Run on testing set
			ClassificationResults resultsOnTesting = new ClassificationResults();
			runOnInstancesAnUpdateResults(testing, classifier, resultsOnTesting);

			/** Save results for this classifier for this dataset */
			allResults.addResults(resultsOnTraining, resultsOnTesting, trainResults);
		}

		return allResults;
	}

	public OverallResults trainAndRunClassifiersKFold(ClassificationAlgorithm[] algorithms, Dataset dataset,
			int k) throws Exception {
		List<String> names = new ArrayList<String>();
		for (ClassificationAlgorithm algorithm : algorithms) {
			names.add(algorithm.getName());
		}

		OverallResults allResults = new OverallResults(names);

		// k has to be at least 2
		if (k < 2) {
			throw new RuntimeException("k needs to be 2 or more");
		}

		// calculating absolute ratio
		double relativeRatio = 1 / (double) k;
		int absoluteRatio = (int) Math.ceil(dataset.getInstances().size() * relativeRatio);

		// For each fold, it does training and testing
		for (int i = 0; i < k; i++) {
//				//Partitioning database 
			int posStart = i * absoluteRatio; // start position of testing set
			int posEnd = posStart + absoluteRatio; // end position of testing set
			if (i == (k - 1)) { // if last fold we adjust the size to include all the left-over sequences
				posEnd = dataset.getInstances().size(); // special case
			}

			// Split the dataset in two parts
			Dataset[] datasets = VirtualDataset.splitDatasetForKFold(dataset, posStart, posEnd);
			Dataset training = datasets[0];
			Dataset testing = datasets[1];

			if (DEBUGMODE) {
				System.out.println("===== KFOLD " + i + " =====");
				System.out.println(" k = " + k);
				System.out.println("  - Original dataset: " + dataset.getInstances().size() + " records.");
				System.out.println("  - Training part: " + training.getInstances().size() + " records.");
				System.out.println("  - Testing part: " + testing.getInstances().size() + " records.");
				System.out.println("===== RUNNING =====");
			}

			// for each classifier
			for (ClassificationAlgorithm algorithm : algorithms) {
				if (DEBUGMODE) {
					System.out.println("Running algorithm ... " + algorithm.getName());
//						System.out.println(datasets[0].getMapClassToFrequency());
//						System.out.println(datasets[1].getMapClassToFrequency());
				}
				// Train the classifier
				Classifier classifier = algorithm.trainAndCalculateStats(testing);
				TrainingResults trainResults = new TrainingResults();
				trainResults.memory += algorithm.getTrainingMaxMemory();
				trainResults.runtime += algorithm.getTrainingTime();
				if (classifier instanceof RuleClassifier) {
					trainResults.avgRuleCount += ((RuleClassifier) classifier).getNumberRules() / (double) k;
				}

				// Run on training set
				ClassificationResults resultsOnTraining = new ClassificationResults();
				runOnInstancesAnUpdateResults(training, classifier, resultsOnTraining);

				// Run on testing set
				ClassificationResults resultsOnTesting = new ClassificationResults();
				runOnInstancesAnUpdateResults(testing, classifier, resultsOnTesting);

				/** Save results for this classifier for this dataset */
				allResults.addResults(resultsOnTraining, resultsOnTesting, trainResults);
			}
		}
		return allResults;
	}

}
