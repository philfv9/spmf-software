package ca.pfv.spmf.algorithmmanager.descriptions;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.dataset_stats.ProductTDBStats;

/**
 * This class describes the algorithm to calculate stats for a product transaction database. It is designed to be used by the graphical and command line interface.
 * 
 * @see ProductTDBStats
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCalculateStatsProductTDB extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCalculateStatsProductTDB(){
	}

	@Override
	public String getName() {
		return "Calculate_stats_for_a_product_transaction_database";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - STATS CALCULATORS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Calculating_product_tdb.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws Exception {
		ProductTDBStats algo = new ProductTDBStats();
		algo.runAlgorithm(inputFile);
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
		return new String[]{"Database of instances","Transaction database", "Transaction database with profit information"};
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
		return AlgorithmType.DATA_STATS_CALCULATOR;
	}
}
