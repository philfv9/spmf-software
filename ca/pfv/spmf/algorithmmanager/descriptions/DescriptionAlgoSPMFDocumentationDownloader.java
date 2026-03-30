package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.developerswindow.DocumentationDownloaderWindow;

/**
 * This class describes the algorithm to download and offline copy of the SPMF documentation
 * @see DocumentationDownloaderWindow
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSPMFDocumentationDownloader extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSPMFDocumentationDownloader(){
	}

	@Override
	public String getName() {
		return "SPMF_download_offline_documentation_tool";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - SPMF GUI";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/SPMF_DocDownloader.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		DocumentationDownloaderWindow window = new DocumentationDownloaderWindow();
		window.createAndShowGUI();

//		textEditor.openAFile(new File(inputFile));
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
