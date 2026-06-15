package ca.pfv.spmf.algorithms.frequentpatterns.UBTGen;

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
 * This class implements a simple Min-Max normalization.
 * 
 * Values are scaled to the range [0,1] using:
 * 
 * X' = (X - min) / (max - min)
 *
 * The value -999 is treated as a missing value and ignored
 * during fitting and preserved during transformation.
 *
 * @author Srikumar Krishnamoorthy
 */

public class MinMaxScaler {

	/** Minimum value observed in the data. */
    public float dataMin;
	
	/** Maximum value observed in the data. */
    public float dataMax;

    public MinMaxScaler() {}
	
	/**
     * Compute the minimum and maximum values from the data.
     *
     * @param X input array
     */
    public void fit(float[] X) {
        dataMin = Float.POSITIVE_INFINITY;
        dataMax = Float.NEGATIVE_INFINITY;
        for (float value : X) {
			if (value==-999) continue;//ignore missing value while computing the range 
            if (value < dataMin) dataMin = value;
            if (value > dataMax) dataMax = value;
        }
		if (dataMax == dataMin) dataMax = dataMin + 1.0f;
    }

	 /**
     * Scale the data in-place to the range [0,1].
     *
     * @param X input array
     */
    public void transform(float[] X) {//updates in-place
        float range = dataMax - dataMin;
        for (int i = 0; i < X.length; i++) {
			if (X[i]!=-999)//retain missing value code as-is
				X[i] = (X[i] - dataMin) / range;  
        }
    }

	/**
     * Fit to the data, then transform it.
     *
     * @param X input array
     */
    public void fitTransform(float[] X) {
        fit(X);
        transform(X); 
    }

	/**
     * Reverse the scaling operation.
     *
     * @param X_scaled scaled value
     * @return original value
     */
    public float inverseTransform(float X_scaled) {
		if (X_scaled==-999) return X_scaled;
		return (X_scaled * (dataMax - dataMin)) + dataMin;
    }

}
