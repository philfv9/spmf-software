package ca.pfv.spmf.tools.dataset_stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.input.arff.ARFFDatabase;
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
 * This class compute stats about an ARFF file and display the results in the console output
 * @author Philippe Fournier-Viger
 */
public class ARFFFileStats {

    /**
     * This method generates statistics for ARFF file data
     * @param filepath the path to the ARFF file
     * @throws Exception if error reading the file
     */
    public void runAlgorithm(String filepath) throws Exception {
        // Load the ARFF file
        ARFFDatabase db = new ARFFDatabase();
        db.loadFile(filepath);

        // Get the records and attribute names
        List<List<String>> records = db.getRecords();
        List<String> attributeNames = db.getAttributeNames();

        System.out.println("============ ARFF FILE STATS ===========");
        System.out.println("Number of instances: " + records.size());

        // Iterate over each attribute
        for (int attrIndex = 0; attrIndex < attributeNames.size(); attrIndex++) {
            System.out.println("Statistics for attribute: " + attributeNames.get(attrIndex));
            Map<String, Integer> valueCounts = new HashMap<>();
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            double sum = 0;
            int numericCount = 0;

            // Iterate over each record
            for (List<String> record : records) {
                String value = record.get(attrIndex);

                // Check if the value is numeric
                try {
                    double numericValue = Double.parseDouble(value);
                    min = Math.min(min, numericValue);
                    max = Math.max(max, numericValue);
                    sum += numericValue;
                    numericCount++;
                } catch (NumberFormatException e) {
                    // If not numeric, count the occurrences
                    valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
                }
            }

            // If the attribute is numeric, calculate statistics
            if (numericCount > 0) {
                double average = sum / numericCount;
                System.out.println("   Numeric Attribute");
                System.out.println("   Min value: " + min);
                System.out.println("   Max value: " + max);
                System.out.println("   Average value: " + average);
            } else {
                // If the attribute is non-numeric, print the occurrences
                System.out.println("   Non-numeric Attribute");
                for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
                    System.out.println("   Value: " + entry.getKey() + ", Count: " + entry.getValue());
                }
            }
            System.out.println("=======================================");
        }
    }
}
