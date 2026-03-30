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
import ca.pfv.spmf.tools.other_dataset_tools.AlgoSampleRecords;

/**
 * This class describes the algorithm to sample records from a file with a 
 * specified random seed for reproducible results.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @author Philippe Fournier-Viger
 * @see AlgoSampleRecords
 */
public class DescriptionAlgoSampleRecordsWithSeed extends DescriptionOfAlgorithm {

    /**
     * Default constructor
     */
    public DescriptionAlgoSampleRecordsWithSeed() {
    }

    @Override
    public String getName() {
        return "Sample_Records_With_Seed";
    }

    @Override
    public String getAlgorithmCategory() {
        return "TOOLS - DATA TRANSFORMATION";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/SampleRecords.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile) 
            throws IOException {
        
        // Get the sample count parameter
        int sampleCount = getParamAsInteger(parameters[0]);
        
        // Get the with replacement parameter
        boolean withReplacement = getParamAsBoolean(parameters[1]);
        
        // Get the random seed parameter
        long seed = getParamAsLong(parameters[2]);
          
        // Create an instance of the algorithm with the specified seed
        AlgoSampleRecords algorithm = new AlgoSampleRecords(seed);
        
        // Run the algorithm
        algorithm.runAlgorithm(inputFile, outputFile, sampleCount, withReplacement);
        
        // Print statistics
        algorithm.printStats();
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
        parameters[0] = new DescriptionOfParameter("Sample count", 
                "(e.g. 100)", Integer.class, false);
        parameters[1] = new DescriptionOfParameter("With replacement?", 
                "(e.g. true or false)", Boolean.class, false);
        parameters[2] = new DescriptionOfParameter("Random seed", 
                "(e.g. 12345)", Long.class, false);
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
    
//    @Override
//    public String getDescription() {
//        return "Samples records from a file with a specified random seed "
//                + "for reproducible results. Running the algorithm with the "
//                + "same seed will produce the same sample.";
//    }
}