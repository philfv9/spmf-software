package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.utility_cost_tdb_viewer.CostUtilityTransactionDatabaseViewer;

/**
 * This class describes the algorithm to run the cost utility transaction database
 * viewer to open a transaction database file
 * 
 * @see CostUtilityTransactionDatabaseViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoUtilityCostTransactionDatabaseViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoUtilityCostTransactionDatabaseViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_cost_utility_transaction_database_file_with_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Cost_and_Utility_TDB_View.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		boolean runAsStandalone = false;
		CostUtilityTransactionDatabaseViewer tool = new CostUtilityTransactionDatabaseViewer(runAsStandalone, inputFile);

	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility and cost values"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return null;
	}
//
//	@Override
//	String[] getSpecialInputFileTypes() {
//		return null; //new String[]{"ARFF"};
//	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_VIEWER;
	}

}
