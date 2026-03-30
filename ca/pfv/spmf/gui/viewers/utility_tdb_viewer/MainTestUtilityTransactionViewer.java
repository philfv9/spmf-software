package ca.pfv.spmf.gui.viewers.utility_tdb_viewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use Utility Transaction Database viewer algorithm from the
 * source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestUtilityTransactionViewer {

	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_Utility.txt");

		// Applying the viewer
		UtilityTransactionDatabaseViewer viewer = new UtilityTransactionDatabaseViewer(true, input);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
//		System.out.println("filename : " + filename);
		URL url = MainTestUtilityTransactionViewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
