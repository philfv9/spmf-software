package ca.pfv.spmf.gui.viewers.utility_time_tdb_viewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use the Utility-Period Transaction Database viewer  from the
 * source code. This format is used by algorithms such as TS-HOUN and FOSHU
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestUtilityPeriodTransactionViewer {

	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_FOSHU.txt");

		// Applying the viewer
		UtilityTimeTransactionDatabaseViewer viewer = new UtilityTimeTransactionDatabaseViewer(true, input, TypeOfTime.PERIODS);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
//		System.out.println("filename : " + filename);
		URL url = MainTestUtilityPeriodTransactionViewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
