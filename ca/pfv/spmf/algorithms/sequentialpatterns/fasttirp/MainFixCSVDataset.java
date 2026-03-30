package ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to convert time interval datasets in VERTIRP format to the SPMF format
 * 
 * @author Philippe Fournier-Viger, 2023
 */
public class MainFixCSVDataset {

	public static void main(String[] arg) throws IOException {

		String input = "skating.csv"; // fileToPath();
		String output = "skating_spmf.csv";

		// create a writer object to write results to file
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));

		// We scan the database a first time to calculate the support of each item.
		BufferedReader myInput = null;
		String thisLine;

		boolean isOddline = true;

		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true) {
					continue;
				}
				char firstChar = thisLine.charAt(0);
				if (firstChar == '#' || firstChar == '%' || firstChar == 's' || firstChar == '@' || firstChar == 'n') {
					continue;
				}
				if (!isOddline) {
					writer.write(thisLine);
					writer.newLine();
				}

				isOddline = !isOddline;
			}
		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		writer.close();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainFixCSVDataset.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
