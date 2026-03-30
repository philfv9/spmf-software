package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.gui.viewers.mdsequenceviewer.MDSequenceDatabaseViewer;

/**
 * 
 * Example of how to use the Multi-Dimensional Sequence Database viewer from the source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestMDSequenceDatabaseViewerNoTime {

	public static void main(String[] arg) throws IOException {

		// Input file path
		String input = fileToPath("ContextMDSequenceNoTime.txt");
//		String input = fileToPath("ContextMDSequence.txt");

		// Applying the Sequence Database viewer
		MDSequenceDatabaseViewer algorithm = new MDSequenceDatabaseViewer(true, input, false);

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		System.out.println("filename : " + filename);
		URL url = MainTestMDSequenceDatabaseViewerNoTime.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}