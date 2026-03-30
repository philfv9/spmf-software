package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.other_dataset_tools.FixItemIDsTransactionDatabaseWithUtilityTool;
/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
 * This class describes the algorithm to fix a transaction database by incrementing item identifiers
 * by some increment (e.g. 1). 
 * It is designed to be used by the graphical and command line interfaces.
 * 
 * @see FixItemIDsTransactionDatabaseWithUtilityTool
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoFixItemIDsTransactionUtility extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoFixItemIDsTransactionUtility(){
	}

	@Override
	public String getName() {
		return "Fix_item_ids_in_transaction_database_with_utility";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA TRANSFORMATION";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Fix_item_ids_in_a_transaction_database_utility.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int increment = getParamAsInteger(parameters[0]);
		
		FixItemIDsTransactionDatabaseWithUtilityTool tool = new FixItemIDsTransactionDatabaseWithUtilityTool();
		tool.convert(inputFile, outputFile, increment);
		System.out.println("Finished fixing the transaction database.");
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
		parameters[0] = new DescriptionOfParameter("Value to add to item ids", "(e.g. 1)", Integer.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_PROCESSOR;
	}
}
