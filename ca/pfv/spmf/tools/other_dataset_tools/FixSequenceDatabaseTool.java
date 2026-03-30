package ca.pfv.spmf.tools.other_dataset_tools;

/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This tool allows to fix some common problems in a sequence database file in
 * SPMF format. In particular: (1) the tool removes items that appears more than
 * once in an itemset. (2) it sort itemsets according to the lexicographical
 * ordering.
 * 
 * @author Philippe Fournier-Viger, 2014
 */
public class FixSequenceDatabaseTool {

	/**
	 * Fix a sequence database
	 * 
	 * @param input  the input file path (a sequence database in SPMF format)
	 * @param output the output file path (the fixed sequence database in SPMF
	 *               format)
	 * @throws IOException           if an error while reading/writing files.
	 * @throws NumberFormatException
	 */
	public void convert(String input, String output) throws NumberFormatException, IOException {

		// for stats
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(output));
				BufferedReader myInput = new BufferedReader(
						new InputStreamReader(new FileInputStream(new File(input))))) {
			// for each line (transaction) until the end of file
			String thisLine;
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is empty we skip it
				if (thisLine.isEmpty() == true) {
					continue;
					// if the line is some kind of metadata we just write the line as it is
				} else if (thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					writer.write(thisLine + " ");
					writer.newLine();
					continue;
				}

				// Otherwise
				// split the transaction according to the white space separator
				String[] split = thisLine.split(" ");

				List<Integer> currentItemset = new ArrayList<Integer>();

				// for each itemset
				for (String token : split) {
					Integer value = Integer.parseInt(token);

					if (value == -1) {
						Collections.sort(currentItemset);
						int previousItem = -2;
						for (int i = 0; i < currentItemset.size(); i++) {
							int currentItem = currentItemset.get(i);
							if (currentItem != previousItem) {
								writer.write("" + currentItem);
								writer.write(" ");
							}

							previousItem = currentItem;
						}
						writer.write("-1 ");

						currentItemset.clear();
					} else if (value == -2) {
						writer.write("-2");
						writer.newLine();
					} else {
						currentItemset.add(value);
					}

				}

			}
		}
	}

}
