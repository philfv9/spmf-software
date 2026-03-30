package ca.pfv.spmf.gui.viewers.utility_cost_tdb_viewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use Cost Utility Transaction Database viewer algorithm from the
 * source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestCostUtilityTransactionViewer {

	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_cost.txt");

		// Applying the viewer
		CostUtilityTransactionDatabaseViewer viewer = new CostUtilityTransactionDatabaseViewer(true, input);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
//		System.out.println("filename : " + filename);
		URL url = MainTestCostUtilityTransactionViewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
