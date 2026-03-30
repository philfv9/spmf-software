package ca.pfv.spmf.gui.viewers.mdsequenceviewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * 
 * Example of how to use the Multi-Dimensional Sequence Database viewer from the source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestMDSequenceDatabaseViewerWithTime {

	public static void main(String[] arg) throws IOException {

		// Input file path
		String input = fileToPath("ContextMDSequence.txt");

		// Applying the Sequence Database viewer
		MDSequenceDatabaseViewer algorithm = new MDSequenceDatabaseViewer(true, input, true);

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		System.out.println("filename : " + filename);
		URL url = MainTestMDSequenceDatabaseViewerWithTime.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}