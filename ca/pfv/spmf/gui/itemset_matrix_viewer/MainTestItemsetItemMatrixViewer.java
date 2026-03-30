package ca.pfv.spmf.gui.itemset_matrix_viewer;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.UnsupportedEncodingException;
import java.net.URL;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Example showing how to launch the Item-Pattern Matrix Viewer
 * both in standalone mode and in embedded mode (from another class).
 *
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestItemsetItemMatrixViewer {

    public static void main(String[] args) throws Exception {

        // Input file name
        String input = fileToPath("itemsetsWithNames2.txt");
        
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // ignore
            }

            // Run the tool
            ItemsetItemMatrixViewer viewer = new ItemsetItemMatrixViewer(input, false);
            viewer.setVisible(true);
        });
    }

    /**
     * Resolves a filename on the classpath to an absolute file path.
     *
     * @param filename the resource filename
     * @return the absolute path
     * @throws UnsupportedEncodingException if UTF-8 is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestItemsetItemMatrixViewer.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}