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
 * LabelBinarizer encodes string labels as integer indices.
 * <p>
 * Each unique label is assigned a unique positive integer starting from 1.
 * Unknown labels are mapped to -1 during transformation.
 */
 
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;

public class LabelBinarizer {
    Map<String, Integer> labelToIdx = new HashMap<String, Integer>();
	
	/** Creates a new empty LabelBinarizer. */
	public LabelBinarizer(){}
	
	/**
     * Sets the label-to-index mapping manually.
     *
     * @param labelToIdx mapping from label strings to integer indices
     */
	public void setLabelToIdx(Map<String, Integer> labelToIdx){
		this.labelToIdx = labelToIdx;
	}
	
	/**
     * Fits the binarizer to a set of labels, assigning indices to each unique label.
     *
     * @param labels array of labels to fit
     */
    public void fit(String[] labels) {
		String[] uniqueLabels = Arrays.stream(labels).distinct().toArray(String[]::new);
        int index = 1;
        for (String label : uniqueLabels){
			labelToIdx.put(label, index++);
		}
    }

	/**
     * Transforms an array of labels into integer indices based on the fitted mapping.
     * Unknown labels are returned as -1.
     *
     * @param labels array of labels to transform
     * @return array of integer indices
     */
	public int[] transformDense(String[] labels) {
        int[] binarized = new int [labels.length];
        for (int i = 0; i < labels.length; i++) {
            Integer index = labelToIdx.get(labels[i]);
            if (index != null) {
                binarized[i] = index;
            }else binarized[i] = -1;//not found
        }
		return binarized;
    }

	/**
     * Returns the list of labels with a column name prefix.
     *
     * @param colName prefix for each label
     * @return array of prefixed labels
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
