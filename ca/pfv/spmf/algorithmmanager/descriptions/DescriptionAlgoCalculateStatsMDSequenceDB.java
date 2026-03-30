package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.dataset_stats.MDSequenceDatabaseStats;
/*
 *  Copyright (c) 2008-2012 Philippe Fournier-Viger
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This class describes the algorithm to calculate stats for an MD-sequence database. It is designed to be used by the graphical and command line interface.
 * 
 * @see MDSequenceDatabaseStats
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCalculateStatsMDSequenceDB extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoCalculateStatsMDSequenceDB(){
	}

	@Override
	public String getName() {
		return "Calculate_stats_for_an_md_sequence_database";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - STATS CALCULATORS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/CalcStats_MD_SDB.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		MDSequenceDatabaseStats algo = new MDSequenceDatabaseStats();
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
		return new String[]{"Database of instances","Sequence database", "Multi-dimensional sequence database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return null;
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_STATS_CALCULATOR;
	}
}
