package ca.pfv.spmf.tools.dataset_converter.graphconverter;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.gui.viewers.graphviewer.MainTestGraphViewer_PatternFile;

import java.util.Set;
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
 * Test file to convert a graph file from the GSpan format to the DOT format
 */
public class MainTestConvertToDotFormat{

    public static void main(String [] arg) throws IOException, InterruptedException{

        String input = fileToPath("patterns.txt");
        String output = "output.dot";

        BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(input))));

        // map to remember arrow already seen
        Map<String, Set<String>>  mapEdges = new HashMap<String, Set<String>>();

        // for each line (pattern) until the end of file
        String thisLine;
        while ((thisLine = myInput.readLine()) != null) {
            if (thisLine.isEmpty() == true) {
                continue;
            }

            // split the pattern according to the " " separator
            String split[] = thisLine.split(" "); 

            String previousItemFromPreviousItemset = null;

            // for each token  (-1, -2 or an item)
            for(String token : split) {
            	// if it is the end of an itemset, we dont need to do anything
                if("-1".equals(token)) { 
                	

                }else if("-2".equals(token) || '#' == token.charAt(0)){ 
                	// if end of sequence
                    previousItemFromPreviousItemset = null;
                    break;
                }else { // if an item
                	
                	// if there was some item before, we will put an edge
                    if(previousItemFromPreviousItemset != null) {
                    	Set<String> set = mapEdges.get(previousItemFromPreviousItemset);
                    	if(set == null) {
                    		set = new HashSet<String>();
                    		 mapEdges.put(previousItemFromPreviousItemset, set);
                    	}
                        set.add(token);
                    }
                    // Remember this item for next time
                    previousItemFromPreviousItemset = token;
                }
            }
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(output));     
        writer.write("digraph mygraph{");
        // PRINT THE ARROWS
        for(Entry<String, Set<String>> edge : mapEdges.entrySet()) {
        	String vertex = edge.getKey() ;
        	for(String otherVertex : edge.getValue()) {
        		writer.write(vertex + " -> " + otherVertex + " \n");
        	}
        }

        writer.write("}");
        myInput.close();
        writer.close();
    }
    
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestGraphViewer_PatternFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}