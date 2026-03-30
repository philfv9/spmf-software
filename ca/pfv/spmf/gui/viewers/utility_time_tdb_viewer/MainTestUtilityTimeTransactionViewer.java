package ca.pfv.spmf.gui.viewers.utility_time_tdb_viewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use the Time-Utility Transaction Database viewer  from the
 * source code. This format is used by algorithms such as LTHUI-Miner
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestUtilityTimeTransactionViewer {

	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_LTHUI.txt");

		// Applying the viewer
		UtilityTimeTransactionDatabaseViewer viewer = new UtilityTimeTransactionDatabaseViewer(true, input, TypeOfTime.TIMESTAMP);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
//		System.out.println("filename : " + filename);
		URL url = MainTestUtilityTimeTransactionViewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
