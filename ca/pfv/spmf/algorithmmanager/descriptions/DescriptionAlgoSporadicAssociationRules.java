package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFASTInverse;

/**
 * This class describes parameters of the algorithm for generating sporadic association rules 
 * with the AprioriInverse algorithm. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoAprioriInverse, AlgoAgrawalFaster94
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSporadicAssociationRules extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSporadicAssociationRules(){
	}

	@Override
	public String getName() {
		return "Sporadic_association_rules";
	}

	@Override
	public String getAlgorithmCategory() {
		return "ASSOCIATION RULE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/PerfectlySporadicAssociationRules.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		double maxsup = getParamAsDouble(parameters[1]);
		double minconf = getParamAsDouble(parameters[2]);

		AlgoAprioriFASTInverse apriori = new AlgoAprioriFASTInverse();
		ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets patterns = apriori
				.runAlgorithm(minsup, maxsup, inputFile, null, 30);
		apriori.printStats();
		int databaseSize = apriori.getDatabaseSize();

		// STEP 2: Generating all rules from the set of frequent itemsets
		// (based on Agrawal & Srikant, 94)
		ca.pfv.spmf.algorithms.associationrules.agrawal94_association_rules.AlgoAgrawalFaster94 algoAgrawal = new ca.pfv.spmf.algorithms.associationrules.agrawal94_association_rules.AlgoAgrawalFaster94();
		algoAgrawal.runAlgorithm(patterns, outputFile, databaseSize,
				minconf);
		algoAgrawal.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.1 or 10%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Maxsup (%)", "(e.g. 0.61 or 61%)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Minconf (%)", "(e.g. 0.6 or 60%)", Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Association rules", "Sporadic association rules"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
