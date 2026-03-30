package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.transactionviewer.TransactionDatabaseViewer;

/**
 * This class describes the algorithm to run the transaction database viewer to
 * open a transaction database file
 * 
 * @see TransactionDatabaseViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoTransactionDatabaseViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoTransactionDatabaseViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_transaction_database_file_with_transaction_db_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Trans_Viewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		boolean runAsStandalone = false;
		TransactionDatabaseViewer tool = new TransactionDatabaseViewer(runAsStandalone, inputFile);

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
		return new String[] { "Database of instances", "Transaction database", "Simple transaction database" };
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
