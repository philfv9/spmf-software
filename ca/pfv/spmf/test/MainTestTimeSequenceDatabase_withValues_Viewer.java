package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.gui.viewers.timesequencedbviewer.TimeSequenceDatabaseViewer;

/**
 * 
 * Example of how to use the Multi-Dimensional Sequence Database viewer from the source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestTimeSequenceDatabase_withValues_Viewer {

	public static void main(String[] arg) throws IOException {

		// Input file path
		String input = fileToPath("contextSequencesTimeExtended_ValuedItems.txt");

		// Applying the Sequence Database viewer
		TimeSequenceDatabaseViewer algorithm = new TimeSequenceDatabaseViewer(true, input);

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		System.out.println("filename : " + filename);
		URL url = MainTestTimeSequenceDatabase_withValues_Viewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}