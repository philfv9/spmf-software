package ca.pfv.spmf.tools.dataset_stats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ca.pfv.spmf.algorithms.clustering.instancereader.AlgoInstanceFileReader;
import ca.pfv.spmf.patterns.cluster.DoubleArray;
/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class compute stats about an file containing records that are double vectors (the format used for example by clustering algorithms in SPMF).
 * 
 * @author Philippe Fournier-Viger
 */
public class DoubleVectorDBStats {

    /**
     * This method generates statistics for a database of double vectors (a file)
     * @param inputFile the path to the file
     * @param separator Separator for the double vector data (e.g., comma, semicolon)
     * @throws IOException exception if there is a problem while reading the file.
     */
    public void runAlgorithm(String inputFile, String separator) throws IOException {
        // Applying the algorithm
        AlgoInstanceFileReader algorithm = new AlgoInstanceFileReader();
        List<DoubleArray> instances = algorithm.runAlgorithm(inputFile, separator);

        // Check if instances are not empty
        if (instances.isEmpty()) {
            System.out.println("No instances found in the file.");
            return;
        }

        // Assuming all instances have the same number of attributes
        int numberOfAttributes = instances.get(0).size();

        System.out.println("============ DOUBLE VECTOR DB STATS ============");
        System.out.println("Number of instances: " + instances.size());
        System.out.println("Number of attributes: " + numberOfAttributes);

        // Calculate statistics for each attribute
        for (int attrIndex = 0; attrIndex < numberOfAttributes; attrIndex++) {
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            double sum = 0;
            List<Double> values = new ArrayList<>();

            for (DoubleArray instance : instances) {
                double value = instance.get(attrIndex);
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
                values.add(value);
            }

            double average = sum / instances.size();

            // Calculate median
            Collections.sort(values);
            double median;
            int middle = values.size() / 2;
            if (values.size() % 2 == 0) {
                median = (values.get(middle - 1) + values.get(middle)) / 2.0;
            } else {
                median = values.get(middle);
            }

            System.out.println("Statistics for attribute " + (attrIndex + 1) + ":");
            System.out.println("   Min value: " + min);
            System.out.println("   Max value: " + max);
            System.out.println("   Average value: " + average);
            System.out.println("   Median value: " + median);
        }
        System.out.println("=================================================");
    }
}
