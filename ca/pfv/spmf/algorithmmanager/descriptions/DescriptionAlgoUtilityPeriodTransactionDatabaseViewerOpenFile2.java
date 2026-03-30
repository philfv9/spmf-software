package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.utility_time_tdb_viewer.TypeOfTime;
import ca.pfv.spmf.gui.viewers.utility_time_tdb_viewer.UtilityTimeTransactionDatabaseViewer;

/**
 * This class describes the algorithm to run the period utility transaction database
 * viewer to open a transaction database file with period information (e.g. the type of data used by the TSHOUN and FOSHU algorithms).
 * 
 * @see UtilityTimeTransactionDatabaseViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoUtilityPeriodTransactionDatabaseViewerOpenFile2 extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoUtilityPeriodTransactionDatabaseViewerOpenFile2() {
	}

	@Override
	public String getName() {
		return "Open_utility_period_transaction_database_file_with_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Period_data_with_Utility_TDB_View.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		boolean runAsStandalone = false;
		UtilityTimeTransactionDatabaseViewer tool = new UtilityTimeTransactionDatabaseViewer(runAsStandalone, inputFile, TypeOfTime.PERIODS);

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
		return new String[]{"Database of instances","Transaction database", "Transaction database with shelf-time periods and utility values"};
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
