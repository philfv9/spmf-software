package ca.pfv.spmf.experimental.itemsetvisualizer_test;

import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Example of how to use the Itemset Viewer
 */
public class MainTestItemsetViewer {

    public static void main(String[] args) throws Exception {
        String input = fileToPath("patterns40.txt");
        
        // Launch the viewer
        ItemsetViewer viewer = new ItemsetViewer();
        viewer.setVisible(true);
    }
    
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestItemsetViewer.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}