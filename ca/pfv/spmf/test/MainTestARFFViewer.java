package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.gui.viewers.arffviewer.ARFFDatabaseViewer;

/**
 * Example of how to use the ARFF file viewer
 *   from the source code.
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestARFFViewer {

	public static void main(String [] arg) throws IOException{

//		String input = fileToPath("example_contextZart.arff");
		String input = fileToPath("test.arff");

		// Applying the viewer
		ARFFDatabaseViewer viewer = new ARFFDatabaseViewer(true, input);
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
//		System.out.println("filename : " + filename);
		URL url = MainTestARFFViewer.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
