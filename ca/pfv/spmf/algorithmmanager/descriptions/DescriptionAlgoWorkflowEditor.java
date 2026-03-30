package ca.pfv.spmf.algorithmmanager.descriptions;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.workflow_editor.WorkflowEditorWindow;

/**
 * This class describes the SPMF workflow editor algorithm to run the text editor of SPMF without
 * opening a file
 * 
 * @see WorkflowEditorWindow
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoWorkflowEditor extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoWorkflowEditor() {
	}

	@Override
	public String getName() {
		return "SPMF_workflow_editor";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - SPMF GUI";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Workflows.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws Exception {
		boolean runAsStandalone = false;
		// Create an instance of the editor and run it
		WorkflowEditorWindow drawFrame = new WorkflowEditorWindow(runAsStandalone);
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
