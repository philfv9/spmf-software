package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.episodes.emdo.AlgoPEMDO;
import ca.pfv.spmf.algorithms.episodes.emdo.Episode;
import ca.pfv.spmf.algorithms.episodes.emdo.Occurrence;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class describes the EMDO algorithm parameters for episode rules. It is
 * designed to be used by the graphical and command line interface.
 * 
 * @see AlgoPEMDO
 * @author O. Ouarem et al.
 */
public class DescriptionAlgoEMDORules extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoEMDORules() {
	}

	@Override
	public String getName() {
		return "EMDO-Rules";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE RULE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/EMDO_episode_and_rules.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minsup = getParamAsInteger(parameters[0]);
		double minconf = getParamAsDouble(parameters[1]);

		// Create the algorithm
		AlgoPEMDO algo = new AlgoPEMDO();

		// Find frequent episodes and save them to a file in SPMF format
		Map<String, List<Occurrence>> singleEpisodeEvent = algo.scanSequence(inputFile, true, false);
		List<Episode> t_freq = algo.findFrequentEpisodesEMDO(minsup, singleEpisodeEvent);
		algo.printStats();

		// Find episode rules
		algo.generateEpisodeRulesWithPruning(t_freq, minconf);
		algo.saveRulesToFile(outputFile);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minimum support", "(e.g. 2)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum confidence", "(e.g. 0.2)", Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Oualid Ouarem";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Transaction database", "Transaction database with timestamps" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Episode rules" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}

}
