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
 * This class implements a simple label encoding (binarization) method.
 * 
 * Each distinct categorical value is mapped to a unique integer index
 * starting from 1. The value "-999" is treated as a missing value.
 *
 * @author Srikumar Krishnamoorthy
 */
 
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;

public class LabelBinarizer {
	
	/** Mapping between label values and integer indices. */
    Map<String, Integer> labelToIdx = new HashMap<String, Integer>();
	
	/** Indicates if a missing value (-999) was found during fitting. */
	boolean missingValueFound=false;
	
	public LabelBinarizer(){}
	
	/**
     * Learn the mapping from labels to integer indices.
     *
     * @param labels array of categorical values
     */
	public void fit(String[] labels) {
		String[] uniqueLabels = Arrays.stream(labels).distinct().toArray(String[]::new);
		int index = 1;
        for (String label : uniqueLabels){
			if (label.equals("-999")){
				missingValueFound = true;
			}else labelToIdx.put(label, index++);
		}
    }

	/**
     * Transform labels to their corresponding integer indices.
     *
     * @param labels array of categorical values
     * @return encoded integer array
     */
	public int[] transformDense(String[] labels) {
        int[] binarized = new int [labels.length];
        for (int i = 0; i < labels.length; i++) {
            Integer index = labelToIdx.get(labels[i]);
            if (index != null) {
                binarized[i] = index;
            }else binarized[i] = -999;//missing value case
        }
		return binarized;
    }
	
	/**
     * Get the formatted label names for a given column.
     *
     * @param colName the column name
     * @return array of formatted labels (column=value)
     */
	public String[] getLabels(String colName){
		List<Map.Entry<String, Integer>> entries =
        new ArrayList<>(labelToIdx.entrySet());
		
		//sorting ensures that labels are correctly ordered for inverse transform
		entries.sort(Comparator.comparingInt(Map.Entry::getValue));
		String[] res = new String[entries.size()];
		//System.out.print(colName + " ");
		for (int i = 0; i < entries.size(); i++) {
			String label = entries.get(i).getKey();
			Integer index = entries.get(i).getValue();
			//System.out.print(i + " " + label + " " + index + "; ");
			res[i] = colName + "=" + label;
		}
		//System.out.println();
		return res;
	}
}
