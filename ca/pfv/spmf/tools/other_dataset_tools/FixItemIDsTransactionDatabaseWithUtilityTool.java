package ca.pfv.spmf.tools.other_dataset_tools;

/* This file is copyright (c) 2021 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Fix a transaction database with utility information by adding an increment
 * (e.g. 1) to all the item names. If the increment is 1, this means that if an
 * item is called 1, it will be called 2 in the output. And if an item is called
 * 5, it will be called 6 and so on.
 * 
 * @author Philippe Fournier-Viger, 2021
 */
public class FixItemIDsTransactionDatabaseWithUtilityTool {

	/**
	 * Fix a transaction database to increase all the item names by an increment
	 * (e.g. 1).
	 * 
	 * @param input     the input file path (a transaction database with utility
	 *                  values in SPMF format)
	 * @param output    the output file path
	 * @param increment
	 * @throws IOException           if an error while reading/writting files.
	 * @throws NumberFormatException
	 */
	public void convert(String input, String output, int increment) throws NumberFormatException, IOException {

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(output));
				BufferedReader myInput = new BufferedReader(
						new InputStreamReader(new FileInputStream(new File(input))))) {
			String thisLine;
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment
				if (thisLine.isEmpty() == true) {
					continue;
				}
				// if the line is a kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					writer.write(thisLine);
					writer.newLine();
				} else {
					// otherwise

					// Get only the part of the transaction that contains items (before the :
					// separator)
					String itemsString = thisLine.substring(0, thisLine.indexOf(':'));
					// Get the remaining part
					String restString = thisLine.substring(thisLine.indexOf(':'), thisLine.length());

					// get the items
					String[] split = itemsString.split(" ");

					for (int i = 0; i < split.length; i++) {
						// convert item to integer
						Integer item = Integer.parseInt(split[i]);
						writer.write("" + (item + increment)); // increase the ID by the increment
						if (i != split.length - 1) {
							writer.write(" ");
						}
					}
					writer.write(restString);
					writer.newLine();
				}
			}
		}

	}

}
