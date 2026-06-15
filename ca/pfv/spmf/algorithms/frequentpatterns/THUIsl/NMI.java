package ca.pfv.spmf.algorithms.frequentpatterns.THUIsl;

/* This file is copyright (c) 2024 Srikumar Krishnamoorthy
 * 
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Computes the Normalized Mutual Information (NMI) between two cluster label arrays.
 * 
 * NMI is a measure of similarity between two labelings, normalized to [0, 1].
 */
 
import java.util.HashMap;
import java.util.Map;

public class NMI {

	/**
     * Computes the entropy of a probability distribution.
     *
     * @param probabilities array of probabilities
     * @return entropy in bits
     */
    private double computeEntropy(double[] probabilities) {
        double entropy = 0.0;
        for (double p : probabilities) {
            if (p > 0) {
                entropy -= p * Math.log(p) / Math.log(2);
            }
        }
        return entropy;
    }

	 /**
     * Computes the mutual information between two distributions.
     *
     * @param xProbabilities probabilities of x
     * @param yProbabilities probabilities of y
     * @param jointProbabilities joint probabilities of x and y
     * @return mutual information in bits
     */
    private double computeMutualInformation(double[] xProbabilities, double[] yProbabilities, double[][] jointProbabilities) {
        double entropyX = computeEntropy(xProbabilities);
        double entropyY = computeEntropy(yProbabilities);
        double jointEntropy = 0.0;

        for (int i = 0; i < jointProbabilities.length; i++) {
            for (int j = 0; j < jointProbabilities[i].length; j++) {
                if (jointProbabilities[i][j] > 0) {
                    jointEntropy -= jointProbabilities[i][j] * Math.log(jointProbabilities[i][j]) / Math.log(2);
                }
            }
        }
        double mutualInformation = entropyX + entropyY - jointEntropy;
        return mutualInformation;
    }
	
	/**
     * Computes the Normalized Mutual Information (NMI) between two label arrays.
     *
     * @param x first array of labels
     * @param y second array of labels
     * @return NMI value between 0 and 1
     */
	public double getNMI(int[] x, int[] y) {
        int n = x.length;
        Map<Integer, Integer> xFreq = new HashMap<>();
        Map<Integer, Integer> yFreq = new HashMap<>();
        Map<String, Integer> jointFreq = new HashMap<>();

        for (int i = 0; i < n; i++) {
            xFreq.put(x[i], xFreq.getOrDefault(x[i], 0) + 1);
            yFreq.put(y[i], yFreq.getOrDefault(y[i], 0) + 1);
            jointFreq.put(x[i] + "," + y[i], jointFreq.getOrDefault(x[i] + "," + y[i], 0) + 1);
        }
		double[] xProbabilities = xFreq.values().stream().mapToDouble(count -> (double) count / n).toArray();
        double[] yProbabilities = yFreq.values().stream().mapToDouble(count -> (double) count / n).toArray();
        double[][] jointProbabilities = new double[xFreq.size()][yFreq.size()];

        int i = 0;
        for (int xi : xFreq.keySet()) {
            int j = 0;
            for (int yi : yFreq.keySet()) {
                jointProbabilities[i][j] = (double) jointFreq.getOrDefault(xi + "," + yi, 0) / n;
                j++;
            }
            i++;
        }
        double mutualInformation = computeMutualInformation(xProbabilities, yProbabilities, jointProbabilities);
        double entropyX = computeEntropy(xProbabilities);
        double entropyY = computeEntropy(yProbabilities);
        return mutualInformation / Math.sqrt(entropyX * entropyY);
    }
}

