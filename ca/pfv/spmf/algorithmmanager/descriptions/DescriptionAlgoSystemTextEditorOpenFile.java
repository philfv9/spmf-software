package ca.pfv.spmf.algorithmmanager.descriptions;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * This class describes the algorithm to run the system text editor to open a
 * file
 * 
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSystemTextEditorOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSystemTextEditorOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_text_file_with_system_text_editor";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/System_Text_Editor.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// open the output file if the checkbox is checked
		Desktop desktop = Desktop.getDesktop();
		// check first if we can open it on this operating system:
		if (desktop.isSupported(Desktop.Action.OPEN)) {
			// if yes, open it
			desktop.open(new File(inputFile));
		}
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
		return new String[] { "Text file" };
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
