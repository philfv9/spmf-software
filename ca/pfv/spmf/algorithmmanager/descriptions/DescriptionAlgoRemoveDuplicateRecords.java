package ca.pfv.spmf.algorithmmanager.descriptions;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.other_dataset_tools.AlgoRemoveDuplicateRecords;

/**
 * This class describes the algorithm to remove duplicate records from a file.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @author Philippe Fournier-Viger
 * @see AlgoRemoveDuplicateRecords
 */
public class DescriptionAlgoRemoveDuplicateRecords extends DescriptionOfAlgorithm {

    /** 
     * Default constructor
     */
    public DescriptionAlgoRemoveDuplicateRecords() {
    }

    @Override
    public String getName() {
        return "Remove_duplicate_records";
    }

    @Override
    public String getAlgorithmCategory() {
        return "TOOLS - DATA TRANSFORMATION";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/RemoveDuplicateRecords.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile) 
            throws IOException {
        
        // Create an instance of the algorithm
        AlgoRemoveDuplicateRecords algorithm = new AlgoRemoveDuplicateRecords();
        
        // Run the algorithm
        algorithm.runAlgorithm(inputFile, outputFile);
        
        // Print statistics
        algorithm.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        // This algorithm has no additional parameters
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
        return parameters;
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Philippe Fournier-Viger";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[] { "Text file" };
    }

    @Override
    public String[] getOutputFileTypes() {
        return new String[] { "Text file" };
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DATA_PROCESSOR;
    }
}