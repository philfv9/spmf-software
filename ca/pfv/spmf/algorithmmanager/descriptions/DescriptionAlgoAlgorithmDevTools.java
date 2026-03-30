package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import javax.swing.UnsupportedLookAndFeelException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.developerswindow.DevelopersToolsWindow;

/**
 * This class describes the algorithm to run the Developpers tool of SPMF
 * 
 * @see DevelopersToolsWindow
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAlgorithmDevTools extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAlgorithmDevTools(){
	}

	@Override
	public String getName() {
		return "SPMF_developers_tools";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - SPMF GUI";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/dev_tools_of_spmf.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
		boolean runAsStandalone = false;
		DevelopersToolsWindow frame = new DevelopersToolsWindow(runAsStandalone);
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
