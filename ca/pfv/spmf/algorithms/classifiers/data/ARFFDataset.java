/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
* @Copyright original version LAC 2019   @copyright of modifications SPMF 2021
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
* 
*/
package ca.pfv.spmf.algorithms.classifiers.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class containing the logic to read an ARFF format. The specification for this
 * file could be found on the following link:
 * https://www.cs.waikato.ac.nz/~ml/weka/arff.html
 */
public class ARFFDataset extends Dataset {
	/**
	 * Character used in ARFF to specify a comment
	 */
	private static final String COMMENTCHAR1 = "%";

	/**
	 * Character used in ARFF to specify a comment
	 */
	private static final String COMMENTCHAR2 = "#";

	/**
	 * Character used in ARFF to specify that a line contains meta-information
	 */
	private static final String METACHAR = "@";

	/**
	 * Separator used to separate each value in the instances
	 */
	private static final String SEPARATOR = ",";

	/**
	 * Starts of the line containing meta information of the name of the dataset
	 */
	private static final String RELATION = "@relation";

	/**
	 * Starts of the line containing meta information on an attribute
	 */
	private static final String ATTRIBUTE = "@attribute";

	/**
	 * Name of the attribute containing the class
	 */
	private String klass = "class";

	/** the index of the attribute that is the target attribute IN THE INPUT FILE */
	private int klassIndexInFile = -1;

	/** the klass values in File */
	String[] klassValuesInFile = null;

	/**
	 * Constructor for the dataset. It reads from disk the dataset, and generates an
	 * object in main memory with all the information contained in the dataset
	 * 
	 * @param path of the dataset to be read
	 * @throws Exception
	 */
	public ARFFDataset(String path, String className) throws Exception {
		super();

		klass = className;
		klassIndexInFile = -1;
		BufferedReader bufferedReader = null;
		try {
			FileReader fileReader = new FileReader(path);
			bufferedReader = new BufferedReader(fileReader);

			String line;
			while ((line = bufferedReader.readLine()) != null) {
				// Remove empty spaces
				line = line.trim();

				// Ignore empty lines
				// Ignore lines with comments
				if (line.isEmpty() || this.isComment(line)) {
					continue;
					// Metadata information
				} else if (line.startsWith(METACHAR)) {
					this.proccessMetadata(line);
				} else {
					if (indexKlass == -1) {
						if (klassValuesInFile == null) {
							throw new Exception(
									"The target attribute \"" + className + "\" that you have chosen does not exist.");
						}
						this.addKlass(klassValuesInFile); // modified by phil
					}
					this.proccessData(line);
				}
			}

			bufferedReader.close();
		} catch (Exception e) {
//            System.err.println("File " + path + " cannot be found.");
			throw e;
		} finally {
			if (bufferedReader != null) {
				bufferedReader.close();
			}
		}
	}

	/**
	 * Receives a line containing meta information of the dataset, and delegates its
	 * treatment to another low level method
	 * 
	 * @param line containing the meta information
	 */
	private void proccessMetadata(String line) {
		if (line.toLowerCase().startsWith(RELATION)) {
			this.setName(line.replace(RELATION + " ", ""));
		} else if (line.toLowerCase().startsWith(ATTRIBUTE)) {
			this.processAttribute(line);

		}
	}

	/**
	 * Process a line containing information of an attribute
	 * 
	 * @param line containing the information of the attribute
	 */
	private void processAttribute(String line) {
		Pattern p = Pattern.compile("@attribute .*\\{(.*)\\}", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(line);

		String[] splitted = line.replaceAll(" +", " ").split(" ");
		String nameAttribute = splitted[1];

		// Nominal attributes in weka starts with {, to list all the possible values
		if (m.matches()) {
			String[] values = m.group(1).replaceAll(" *", "").split(",");

			Arrays.sort(values); // added by phil

			if (nameAttribute.contains(klass)) {
				klassIndexInFile = this.listAttributeIndexToItems.size();
				klassValuesInFile = values;
			} else {
				this.addNominalAttribute(nameAttribute, values);
			}
		} else {
			this.addNumericAttribute(nameAttribute);
		}

	}

	/**
	 * Process each one of the lines contained in the dataset
	 * 
	 * @param line to be persited on the object being generated in main memory
	 * @throws Exception
	 */
	private void proccessData(String line) throws Exception {
		String[] lineX = line.split(SEPARATOR);

		// ==== new by Phil ====
		// Reorder the instance so that the target attribute is the last one
		// before adding the instance to the in-memory dataset
		String[] reorderedLine = new String[lineX.length];
		int currentPosition = 0;
		for (int j = 0; j < lineX.length; j++) {
			if (j != klassIndexInFile) {
				reorderedLine[currentPosition++] = lineX[j];
			}
		}
		reorderedLine[reorderedLine.length - 1] = lineX[klassIndexInFile];
		// ==== end new by Phil ====

		this.getItemForAttributeValue(reorderedLine);
	}

	/**
	 * Check if a line passed as parameter is a comment or not
	 * 
	 * @param line to check if it is a comment
	 * @return true if line is a comment, false otherwise
	 */
	private boolean isComment(String line) {
		return line.startsWith(COMMENTCHAR1) || line.startsWith(COMMENTCHAR2);
	}

}
