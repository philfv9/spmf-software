package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmExplorer;

/**
 * This class describes the algorithm to run the Algorithm Explorer tool of SPMF
 * 
 * @see AlgorithmExplorer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgorithmGraphVisualizer extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgorithmGraphVisualizer(){
	}

	@Override
	public String getName() {
		return "Algorithm_Explorer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - SPMF GUI";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/SPMF_Algorithm_Explorer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		boolean runAsStandalone = false;
		AlgorithmExplorer frame = new AlgorithmExplorer(runAsStandalone);
		frame.setVisible(true);
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
