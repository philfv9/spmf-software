package ca.pfv.spmf.tools.dataset_converter.graphconverter;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import javax.xml.transform.TransformerConfigurationException;

import org.xml.sax.SAXException;

import ca.pfv.spmf.algorithms.graph_mining.tkg.GSPAN2GraphMLConverter;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.gui.viewers.graphviewer.MainTestGraphViewer_PatternFile;
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
 * Test file to convert a graph file from the GSpan format to the ML format
 * @see GSPAN2GraphMLConverter
 */
public class MainTestConvertGSpanFormatToMLFormat{

    public static void main(String [] arg) throws IOException, InterruptedException, TransformerConfigurationException, SAXException{

        String input = fileToPath("patterns.txt");
        String output = "output.dot";

       GSPAN2GraphMLConverter converter = new GSPAN2GraphMLConverter();
       List<Graph> graphsDatabase = converter.readCGSPANGraphs(input);
       converter.writeGraphMLGraphs(output, graphsDatabase);
       
    }
    
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestGraphViewer_PatternFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}