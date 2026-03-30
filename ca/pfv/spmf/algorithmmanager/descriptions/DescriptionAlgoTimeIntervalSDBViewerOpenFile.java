package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.timeintervaldbviewer.TimeIntervalDatabaseViewer;

/**
 * This class describes the algorithm to run the time interval sequence database
 * viewer to open a sequence database file
 * 
 * @see TimeIntervalDatabaseViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoTimeIntervalSDBViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoTimeIntervalSDBViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_sequence_database_file_with_time_interval_sdb_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Sdb_interval_viewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		boolean runAsStandalone = false;
		TimeIntervalDatabaseViewer tool = new TimeIntervalDatabaseViewer(runAsStandalone, inputFile);
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
		return new String[]{"Database of instances","Time interval sequence database", "Simple time interval sequence database"};
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
