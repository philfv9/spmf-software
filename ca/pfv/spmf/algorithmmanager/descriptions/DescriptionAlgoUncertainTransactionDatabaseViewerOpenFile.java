package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.uncertaintransactionsviewer.UncertainTransactionDatabaseViewer;

/**
 * This class describes the algorithm to run the uncertain transaction database
 * viewer to open an uncertain transaction database file
 * 
 * @see UncertainTransactionDatabaseViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoUncertainTransactionDatabaseViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoUncertainTransactionDatabaseViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_uncertain_transaction_database_file_with_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/uncertain_transactions_view.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		boolean runAsStandalone = false;
		UncertainTransactionDatabaseViewer tool = new UncertainTransactionDatabaseViewer(runAsStandalone, inputFile);

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
		return new String[]{"Database of instances","Transaction database", "Uncertain transaction database"};
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
