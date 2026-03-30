package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.taxonomyviewer.TaxonomyViewer;

/**
 * This class describes the algorithm to run the taxonomy viewer to
 * open a taxonomy file
 * 
 * @see TaxonomyViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoTaxonomyViewerOpenFile extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoTaxonomyViewerOpenFile() {
	}

	@Override
	public String getName() {
		return "Open_taxonomy_file_with_taxonomy_viewer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/TaxonomyViewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// A path to a transaction database file (optional... can be set to null)
		String transactionsFilePath = null;
		
		if (parameters.length >=1 && "".equals(parameters[0]) == false) {
			transactionsFilePath = getParamAsString(parameters[0]);
			
			File file = new File(inputFile);
			if (file.getParent() != null) {
				transactionsFilePath = file.getParent() + File.separator + transactionsFilePath;
			}
		}

		// Applying the viewer
		TaxonomyViewer viewer = new TaxonomyViewer(false, inputFile, transactionsFilePath);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
		parameters[0] = new DescriptionOfParameter("Transaction file", "(e.g. transaction_CLHMiner.txt)", String.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Taxonomy file" };
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
