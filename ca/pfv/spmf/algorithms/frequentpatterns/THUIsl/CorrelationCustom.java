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
 * Utility class for computing fast Pearson correlation coefficients between
 * two variables. Supports both continuous (float) and binary/integer (int) data.
 *
 * The correlation is computed using the standard Pearson formula:
 * covariance(x, y) / (stdDev(x) * stdDev(y)). To prevent division by zero,
 * a small epsilon (1e-6) is used if the variance is zero.
 */

public class CorrelationCustom {

	/**
     * Computes the Pearson correlation coefficient between a float array (continuous variable)
     * and an integer array (discrete variable).
     *
     * @param x the first variable (float array)
     * @param y the second variable (int array)
     * @return the correlation coefficient, ranging from -1 to 1
     */
    public static float getFastCorr(float[] x, int[] y) {
        int samples = x.length;
        float meanX = 0.0f, meanY = 0.0f;
        float centeredX, centeredY;
		float covXY = 0.0f, varX = 0.0f, varY = 0.0f;
        float corrcoefXY;
        for (int i = 0; i < samples; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= samples;
        meanY /= samples;
        for (int i = 0; i < samples; i++) {
            centeredX = x[i] - meanX;
            centeredY = y[i] - meanY;
            covXY += centeredX * centeredY;
            varX += centeredX * centeredX;
            varY += centeredY * centeredY;
        }
        covXY /= (samples - 1);
        varX /= (samples - 1);
        varY /= (samples - 1);
        varX = (varX == 0) ? 1e-6f : varX;
        varY = (varY == 0) ? 1e-6f : varY;
        corrcoefXY = covXY / (float)Math.sqrt(varX * varY);
        
        return corrcoefXY;
    }
	
	/**
     * Computes the Pearson correlation coefficient between two integer arrays.
     * Typically used when the first variable is binary. Can be used as a
     * fast approximation instead of a chi-squared test for binary data.
     *
     * @param x the first variable (int array, possibly binary)
     * @param y the second variable (int array)
     * @return the correlation coefficient, ranging from -1 to 1
     */
	public static float getFastCorr(int[] x, int[] y) {//x is binary valued, alternately use chi-squared in place of pearson
        int samples = x.length;
        float meanX = 0.0f, meanY = 0.0f;
        float centeredX, centeredY;
		float covXY = 0.0f, varX = 0.0f, varY = 0.0f;
        float corrcoefXY;
        for (int i = 0; i < samples; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= samples;
        meanY /= samples;
        for (int i = 0; i < samples; i++) {
            centeredX = x[i] - meanX;
            centeredY = y[i] - meanY;
            covXY += centeredX * centeredY;
            varX += centeredX * centeredX;
            varY += centeredY * centeredY;
        }
        covXY /= (samples - 1);
        varX /= (samples - 1);
        varY /= (samples - 1);
        varX = (varX == 0) ? 1e-6f : varX;
        varY = (varY == 0) ? 1e-6f : varY;
        corrcoefXY = covXY / (float)Math.sqrt(varX * varY);
        
        return corrcoefXY;
    }
}
