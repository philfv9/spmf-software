package ca.pfv.spmf.experimental.iolayer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use HMine from the source code.
 * 
 * @author Philippe Fournier-Viger, 2011.
 */
public class MainTestIOLayerRead {

	public static void main(String[] arg) throws IOException {

		@SuppressWarnings("rawtypes")
		Class testID = MainTestIOLayerRead.class;

		IOManager iomanager = IOManager.getInstance();
		AbstractSPMFReader reader = iomanager.getNewReader(testID, fileToPath("contextPasquier99.txt"));

		String line = reader.readLine();
		reader.close();
		System.out.println("First line:  " + line);
		
		
		AbstractSPMFWriter writer = iomanager.getNewWriter(testID, "output.txt");
		writer.close();
		IOManager.getInstance().releaseResources(testID);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestIOLayerRead.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
