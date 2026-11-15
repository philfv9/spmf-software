package ca.pfv.spmf.tools.other_dataset_tools;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
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
 * Example of how to use the tool to increase all items ids by a given value in a transaction database
 * in SPMF format.
 */
public class MainTestFixItemIDsTransactionDatabase {

	public static void main(String [] arg) throws IOException{
		
		String inputFile = fileToPath("contextPasquier99.txt");
		String outputFile = "output.txt";
		
		// This is a parameter that indicates that we want to increase the item ids by 1
		int increment = 1;

		FixItemIDsTransactionDatabaseTool tool = new FixItemIDsTransactionDatabaseTool();
		tool.convert(inputFile, outputFile, increment);
		

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFixItemIDsTransactionDatabase.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
