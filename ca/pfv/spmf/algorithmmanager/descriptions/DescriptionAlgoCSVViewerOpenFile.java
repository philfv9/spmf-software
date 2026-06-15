package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.csvviewer.CSVDatabaseViewer;

/* Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This class describes the algorithm to run the CSV file viewer of SPMF.
 *
 * @see CSVDatabaseViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoCSVViewerOpenFile extends DescriptionOfAlgorithm {

    /**
     * Default constructor.
     */
    public DescriptionAlgoCSVViewerOpenFile() {
    }

    @Override
    public String getName() {
        return "Open_CSV_file_with_viewer";
    }

    @Override
    public String getAlgorithmCategory() {
        return "TOOLS - DATA VIEWERS";
    }

    @Override
    public String getURLOfDocumentation() {
        return "http://www.philippe-fournier-viger.com/spmf/CSV_basic_viewer.php";
    }

    @Override
    public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
            throws IOException {
        boolean runAsStandalone = false;
        @SuppressWarnings("unused")
        CSVDatabaseViewer viewer = new CSVDatabaseViewer(runAsStandalone, inputFile);
    }

    @Override
    public DescriptionOfParameter[] getParametersDescription() {
        return new DescriptionOfParameter[0];
    }

    @Override
    public String getImplementationAuthorNames() {
        return "Philippe Fournier-Viger";
    }

    @Override
    public String[] getInputFileTypes() {
        return new String[]{"CSV file"};
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