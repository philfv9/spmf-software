package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.sequencedb_cost_utility_viewer.SequenceCostUtilityDatabaseViewer;

/**
 * This class describes the algorithm to run the sequence cost utility database viewer to
 * open a sequence database file with cost and binary utility values
 * 
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSequenceCostBinaryUtilityDBViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSequenceCostBinaryUtilityDBViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_sequence_database_cost_binary_utility_file_with_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Seq_with_cost_binary_utility_db_viewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		boolean runAsStandalone = false;
		SequenceCostUtilityDatabaseViewer tool = new SequenceCostUtilityDatabaseViewer(runAsStandalone, inputFile);
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
		return new String[] { "Database of instances", "Sequence database", "Sequence database with cost and binary utility" };
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
