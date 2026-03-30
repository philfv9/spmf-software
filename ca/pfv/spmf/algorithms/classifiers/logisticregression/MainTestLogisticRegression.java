package ca.pfv.spmf.algorithms.classifiers.logisticregression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class to test the logistic regression classifier
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class MainTestLogisticRegression {

	public static void main(String[] args) {
//		List<Double> instances = DataSet.readDataSet("data.txt");

		List<InstanceContinuous> instances = new ArrayList<InstanceContinuous>();
		instances.add(new InstanceContinuous(new double[] { 5, 100, 300 }));
		instances.add(new InstanceContinuous(new double[] { 5, 130, 400 }));
		instances.add(new InstanceContinuous(new double[] { 10, 200, 600 }));
		instances.add(new InstanceContinuous(new double[] { -1, 10, 60 }));
		instances.add(new InstanceContinuous(new double[] { -6, 3, 60 }));

		List<Boolean> targetOutput = new ArrayList<Boolean>();
		targetOutput.add(false);
		targetOutput.add(false);
		targetOutput.add(false);
		targetOutput.add(true);
		targetOutput.add(true);

		int iterationCount = 11000;
		double learningRate = 0.005d;

		AlgoBinaryLogisticRegression classifier = new AlgoBinaryLogisticRegression();
		classifier.setIterationCount(iterationCount);
		classifier.setLearningRate(learningRate);
		classifier.train(instances, targetOutput);
		classifier.printStats();

		InstanceContinuous instance6 = new InstanceContinuous(new double[] { 663, 700, 900 });
		InstanceContinuous instance7 = new InstanceContinuous(new double[] { -1, 0, 3 });

		System.out.println("Prediction instance 1: " + classifier.predictBoolean(instances.get(0)) + " probability: "
				+ classifier.predictDouble(instances.get(0)));
		System.out.println("Prediction instance 2: " + classifier.predictBoolean(instances.get(1)) + " probability: "
				+ classifier.predictDouble(instances.get(1)));
		System.out.println("Prediction instance 3: " + classifier.predictBoolean(instances.get(2)) + " probability: "
				+ classifier.predictDouble(instances.get(2)));
		System.out.println("Prediction instance 4: " + classifier.predictBoolean(instances.get(3)) + " probability: "
				+ classifier.predictDouble(instances.get(3)));
		System.out.println("Prediction instance 5: " + classifier.predictBoolean(instances.get(4)) + " probability: "
				+ classifier.predictDouble(instances.get(4)));
		System.out.println("Prediction instance 6: " + classifier.predictBoolean(instance6) + " probability: "
				+ classifier.predictDouble(instance6));
		System.out.println("Prediction instance 7: " + classifier.predictBoolean(instance7) + " probability: "
				+ classifier.predictDouble(instance7));

		System.out.println("weights " + Arrays.toString(classifier.weights));
		// weights [-3.0727531342901626, -9.551865099589486, 1.6476022874452927]

	}
}
