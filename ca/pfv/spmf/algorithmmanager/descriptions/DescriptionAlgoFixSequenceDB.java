package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.other_dataset_tools.FixSequenceDatabaseTool;

/**
 * This class describes the algorithm to fix a sequence database. It is designed to be used by the graphical and command line interface.
 * 
 * @see FixSequenceDatabaseTool
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoFixSequenceDB extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoFixSequenceDB(){
	}

	@Override
	public String getName() {
		return "Fix_a_sequence_database";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA TRANSFORMATION";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/sequence_database_fix.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		FixSequenceDatabaseTool tool = new FixSequenceDatabaseTool();
		tool.convert(inputFile, outputFile);
		System.out.println("Finished fixing the sequence database.");
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
		return new String[]{"Database of instances","Sequence database", "Simple sequence database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Database of instances","Sequence database", "Simple sequence database"};
	}
//
//	@Override
//	String[] getSpecialInputFileTypes() {
//		return null; //new String[]{"ARFF"};
//	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_PROCESSOR;
	}
	
}
