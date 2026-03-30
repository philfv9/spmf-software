package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.pattern_diff_analyzer.PatternDiffAnalyzer;

/**
 * This class describes the algorithm to run the Pattern Diff Analyzer tool of SPMF
 * 
 * @see PatternDiffAnalyzer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoPatternDiffAnalyzer extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoPatternDiffAnalyzer(){
	}

	@Override
	public String getName() {
		return "SPMF_Pattern_Diff_Analyzer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - SPMF GUI";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Pattern_Diff_Analyzer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		boolean runAsStandalone = false;
		PatternDiffAnalyzer viewer = new PatternDiffAnalyzer(runAsStandalone);
		viewer.setVisible(true);
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
		return null;
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
		return AlgorithmType.OTHER_GUI_TOOL;
	}
}