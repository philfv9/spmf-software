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
 * MinMaxScaler scales numerical data to the range [0, 1].
 * 
 * It can fit the scaler to data, transform data in-place, and perform inverse transformations.
 */

public class MinMaxScaler {

    public float dataMin;
    public float dataMax;

    public MinMaxScaler() {}
	
	
	/**
     * Creates a MinMaxScaler with given min and max values.
     *
     * @param params array containing {dataMin, dataMax}
     */
	 public MinMaxScaler(float[] params){
		this.dataMin = params[0];
		this.dataMax = params[1];
	}
	
	/**
     * Fits the scaler to the input data, determining min and max values.
     *
     * @param X array of data to fit
     */
    public void fit(float[] X) {
        dataMin = Float.POSITIVE_INFINITY;
        dataMax = Float.NEGATIVE_INFINITY;
        for (float value : X) {
            if (value < dataMin) dataMin = value;
            if (value > dataMax) dataMax = value;
        }
		if (dataMax == dataMin) dataMax = dataMin + 1.0f;
    }

	/**
     * Transforms the input data in-place to the range [0, 1] based on fitted min and max.
     *
     * @param X array of data to transform
     */
    public void transform(float[] X) {//updates in-place
        float range = dataMax - dataMin;
        for (int i = 0; i < X.length; i++) {
            X[i] = (X[i] - dataMin) / range;  
        }
    }

	/**
     * Fits the scaler to the data and transforms it in-place.
     *
     * @param X array of data to fit and transform
     */
    public void fitTransform(float[] X) {
        fit(X);
        transform(X); 
    }

	/**
     * Inverse transforms a scaled value back to the original range.
     *
     * @param X_scaled scaled value in [0, 1]
     * @return original value before scaling
     */
    public float inverseTransform(float X_scaled) {
		return (X_scaled * (dataMax - dataMin)) + dataMin;
    }

}
