package ca.pfv.spmf.tools.documentation_downloader;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to download the documentation of SPMF so that it can be browsed offline.
 * @author Philippe Fournier-Viger
 */
public class MainTestSPMFDownloadDoc {

	public static void main(String[] arg) throws IOException {

		try {
			AlgoSPMFDownloadDoc algo = new AlgoSPMFDownloadDoc();
			algo.runAlgorithm();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestSPMFDownloadDoc.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
