package ca.pfv.spmf.gui.viewers.timesequencedbviewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * 
 * Example of how to use the Multi-Dimensional Sequence Database viewer from the source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestTimeSequenceDatabaseViewer {

	public static void main(String[] arg) throws IOException {

		// Input file path
//		String input = fileToPath("mooc_small.txt");
		String input = fileToPath("contextSequencesTimeExtended.txt");


		// Applying the Sequence Database viewer
		TimeSequenceDatabaseViewer algorithm = new TimeSequenceDatabaseViewer(true, input);

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		System.out.println("filename : " + filename);
		URL url = MainTestTimeSequenceDatabaseViewer.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}