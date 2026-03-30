package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.sequencedb_utility_viewer.SequenceUtilityDatabaseViewer;

/**
 * This class describes the algorithm to run the sequence utility database viewer to
 * open a sequence database file
 * 
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSequenceUtilityDatabaseViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSequenceUtilityDatabaseViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_sequence_utility_database_file_with_sequence_db_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Seq_with_utility_db_viewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		boolean runAsStandalone = false;
		SequenceUtilityDatabaseViewer tool = new SequenceUtilityDatabaseViewer(runAsStandalone, inputFile);
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
		return new String[]{"Database of instances","Sequence database", "Sequence database with utility values"};
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
