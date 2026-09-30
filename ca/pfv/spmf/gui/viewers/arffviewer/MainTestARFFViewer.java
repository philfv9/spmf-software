package ca.pfv.spmf.gui.viewers.arffviewer;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import javax.swing.SwingUtilities;

/**
 * Example of how to use the ARFF file viewer from the source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestARFFViewer {

	public static void main(String[] arg) throws UnsupportedEncodingException {
		final String input = fileToPath("test.arff");

		// Swing components must be created on the event dispatch thread
		SwingUtilities.invokeLater(() -> new ARFFDatabaseViewer(true, input));
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestARFFViewer.class.getResource(filename);
		if (url == null) {
			throw new IllegalArgumentException("Resource not found on the classpath: " + filename);
		}
		return URLDecoder.decode(url.getPath(), "UTF-8");
	}
}