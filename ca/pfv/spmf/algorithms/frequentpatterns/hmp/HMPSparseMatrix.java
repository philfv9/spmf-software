package ca.pfv.spmf.algorithms.frequentpatterns.hmp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.datastructures.triangularmatrix.SparseTriangularMatrix;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/


/**
 * HMPSparseMatrix extends the SPMF SparseTriangularMatrix to provide an
 * additional utility used by the HMP algorithm: enumeration of all
 * length-2 patterns (pairs) with their occurrence counts.
 * 
 * This class reuses the implementation from the core sparse triangular
 * matrix (SparseTriangularMatrix) and adds only the extra method needed
 * by HMP.
 * 
 * @see ca.pfv.spmf.datastructures.triangularmatrix.SparseTriangularMatrix
 * @author Enze Chen, Philippe Fournier-Viger et al. (adapted)
 */
public class HMPSparseMatrix extends SparseTriangularMatrix {
	
	/**
     * Default constructor.
     * Calls the parent constructor to initialize the matrix.
     */
    public HMPSparseMatrix() {
        super();
    }

    /**
     * Compatibility constructor (kept for API compatibility with other implementations).
     * The parameter itemCount is not used by the sparse implementation but kept
     * for compatibility.
     *
     * @param itemCount unused, for compatibility only
     */
    public HMPSparseMatrix(int itemCount) {
        super(itemCount);
    }

    /**
     * Get all length-2 patterns with their occurrence counts.
     * 
     * This method iterates through the internal sparse triangular matrix and
     * produces a list of Pair objects where each Pair contains a sorted
     * int[2] representing the 2-item pattern and an integer count of its
     * occurrences.
     *
     * Notes:
     * - The triangular matrix stores only one half (i < j) so the method
     *   sorts the two items to produce a canonical representation.
     * - This method relies on the parent class exposing the internal matrix
     *   via a protected getter `getMatrix()` (or by making the field protected).
     *
     * @return a List of Pair objects containing 2-item patterns and counts.
     */
    public List<Pair> getAllPatternsWithOccurrences() {
        List<Pair> patterns = new ArrayList<>();

        // Obtain the internal matrix from parent (assumes parent provides getMatrix())
        Map<Integer, Map<Integer, Integer>> matrix = getMatrix();

        // Iterate through the matrix entries (rows)
        for (Map.Entry<Integer, Map<Integer, Integer>> row : matrix.entrySet()) {
            int item1 = row.getKey();
            // For each column entry in this row
            for (Map.Entry<Integer, Integer> entry : row.getValue().entrySet()) {
                int item2 = entry.getKey();
                int count = entry.getValue();

                // Create a sorted pattern [min, max] so pairs are canonical
                int[] arr = item1 < item2 ? new int[]{item1, item2} : new int[]{item2, item1};

                // Add the pattern and its occurrence count to the result list
                patterns.add(new Pair(arr, count));
            }
        }

        return patterns;
    }
    
    /**
     * Clear the matrix by resetting all stored data.
     */
    public void clear() {
        // Access the matrix via parent getter
        getMatrix().clear();
    }
}
