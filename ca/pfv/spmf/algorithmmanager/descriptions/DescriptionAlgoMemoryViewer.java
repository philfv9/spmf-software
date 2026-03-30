package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.MemoryViewer;

/**
 * This class describes the algorithm to show the current memory usage of the JVM
 * 
 * @see MemoryViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMemoryViewer extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoMemoryViewer(){
	}

	@Override
	public String getName() {
		return "MemoryViewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - SPMF GUI";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/memory_Usage_chart.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		MemoryViewer.displayMemoryChart();
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
