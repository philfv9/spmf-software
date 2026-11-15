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
public class MainTestFixItemIDsTransactionDatabaseWithUtility {

	
	public static void main(String[] args) throws NumberFormatException, IOException {
		
		String inputPath = fileToPath("Chicago_Crimes_2001_to_2017_utility2.txt");
		String outputPath = "Chicago_Crimes_2001_to_2017_utility_fixed.txt";
		
		// This is a parameter that indicates that we want to increase the item ids by 1
		int increment = 1;
				
				
		FixItemIDsTransactionDatabaseWithUtilityTool tool = new FixItemIDsTransactionDatabaseWithUtilityTool();
		
		tool.convert(inputPath, outputPath, increment);
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFixItemIDsTransactionDatabaseWithUtility.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
