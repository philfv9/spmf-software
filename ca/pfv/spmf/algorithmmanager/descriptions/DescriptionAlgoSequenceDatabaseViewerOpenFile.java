package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.sequencedb_viewer.SequenceDatabaseViewer;

/**
 * This class describes the algorithm to run the sequence database viewer to
 * open a sequence database file
 * 
 * @see SequenceDatabaseViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSequenceDatabaseViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSequenceDatabaseViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_sequence_database_file_with_sequence_db_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Seq_db_db_viewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		boolean runAsStandalone = false;
		SequenceDatabaseViewer tool = new SequenceDatabaseViewer(runAsStandalone, inputFile);
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
		return new String[] { "Database of instances", "Sequence database", "Simple sequence database" };
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
