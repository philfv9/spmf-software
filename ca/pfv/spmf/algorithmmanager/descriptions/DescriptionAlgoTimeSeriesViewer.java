package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.util.List;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.timeseries.TimeSeries;
import ca.pfv.spmf.algorithms.timeseries.reader_writer.AlgoTimeSeriesReader;
import ca.pfv.spmf.gui.viewers.timeseriesviewer.TimeSeriesViewer;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * 
 * Do not remove copyright and license information
 */
/**
 * This class describes the algorithm to visualize a time series.
 * 
 * @see TimeSeriesViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoTimeSeriesViewer extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoTimeSeriesViewer(){
	}

	@Override
	public String getName() {
		return "Vizualize_time_series";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TIME SERIES MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/TimeSeriesViewer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		String separator;
		if (parameters.length > 0 && "".equals(parameters[0]) == false) {
			separator = getParamAsString(parameters[0]);
		}else{
			separator = " ";
		}
		
		AlgoTimeSeriesReader reader = new AlgoTimeSeriesReader();
		List<TimeSeries> timeSeries = reader.runAlgorithm(inputFile, separator);

		TimeSeriesViewer viewer = new TimeSeriesViewer(timeSeries);
		viewer.setVisible(true);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
		parameters[0] = new DescriptionOfParameter("separator", "(e.g. ',' , default: ' ')", String.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Time series database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return null;
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_VIEWER;
	}

	
}
