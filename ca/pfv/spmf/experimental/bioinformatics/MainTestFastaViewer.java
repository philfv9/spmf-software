package ca.pfv.spmf.experimental.bioinformatics;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
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
 * Class for testing the FastaReader functions for reading Fasta data in memory.
* 
 * @author Philipe-Fournier-Viger
 **/
public class MainTestFastaViewer {
	
	public static void main(String[] args) throws IOException {
//		String filePath = fileToPath("FastaStar.txt");
		String filePath = fileToPath("FastaDouble.txt");
//		String filePath = fileToPath("Bio.txt");
//		FastaDataset dataset = new FastaDataset();
//		dataset.loadFile(filePath);

		boolean runAsStandalone = true;
		FastaViewer viewer = new FastaViewer(runAsStandalone);
		viewer.load(filePath);
        viewer.setVisible(true);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestFastaViewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
