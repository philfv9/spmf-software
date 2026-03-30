
package ca.pfv.spmf.algorithms.classifiers.general;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ca.pfv.spmf.algorithms.classifiers.data.Instance;

/**
 * This class represents a classifier
 */
public abstract class Classifier { 
	/**
	 * Value used when the classifier is not able to perform a prediction
	 */
	public static short NOPREDICTION = -1;

	/**
	 * Main constructor
	 */
	public Classifier() {

	}
	
	/**
	 * Get the name of the classifier
	 * @return the name
	 */
	public abstract String getName();

	/**
	 * Predict the class of an instance consisting of several attribute values
	 * 
	 * @param instance an instance
	 * @return the predicted class
	 */
	public abstract short predict(Instance instance);

	
	/**
	 * Save this classifier to a file (as a serialized object)
	 * @param outputPath the file path
	 * @throws IOException if error while reading from file
	 */
	public void saveTrainedClassifierToFile(String outputPath) throws IOException {
		ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(outputPath));
		stream.writeObject(this);
		stream.close();
	}
	
	/**
	 * Read a classifier from a file (as a serialized object)
	 * @param inputPath the file path
	 * @return a classifier
	 * @throws IOException if error readinsg from file
	 */
	static public Classifier loadTrainedClassifierToFile(String inputPath) throws IOException {
		ObjectInputStream stream2 = new ObjectInputStream(new FileInputStream(inputPath));
		Classifier classifier = null;
		try {
			classifier = (Classifier) stream2.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		stream2.close();
		return classifier;
	}

}
