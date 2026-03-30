package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.gui.viewers.timeintervaldbviewer.TimeIntervalDatabaseViewer;

/**
 * 
 * Example of how to use the Time Interval Sequence Database viewer
 * 
 * algorithm from the source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestTimeIntervalDBViewer {

	public static void main(String[] arg) throws IOException {

		// Input file path
		String input = fileToPath("test.csv");

		// Applying the Sequence Database viewer
		TimeIntervalDatabaseViewer algorithm = new TimeIntervalDatabaseViewer(false, input);

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		System.out.println("filename : " + filename);
		URL url = MainTestTimeIntervalDBViewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}