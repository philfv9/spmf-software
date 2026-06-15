package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * Example of how to use the Visual Pattern Viewer to visualise subgraph
 * patterns stored in a file in SPMF / gSpan format.
 *
 * @author Philippe Fournier-Viger (Copyright 2025)
 */
public class MainTestVisualPatternViewer_Subgraphs {

    /**
     * Entry point.
     *
     * @param args command-line arguments (not used)
     * @throws IOException if the pattern file cannot be read
     */
    public static void main(String[] args) throws IOException {
        // Path to the subgraph pattern file
        String inputPath = fileToPath("patterns_graph.txt");

        // Launch the viewer
        VisualPatternViewer viewer = new VisualPatternViewer(
                true, inputPath, PatternType.SUBGRAPHS);
        viewer.setVisible(true);
    }

    /**
     * Resolves a resource file name (located in the same package directory as this
     * class) to an absolute file-system path.
     *
     * @param filename the resource file name (e.g. {@code "patterns_graph.txt"})
     * @return the absolute path as a string
     * @throws UnsupportedEncodingException if UTF-8 decoding fails
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestVisualPatternViewer_Subgraphs.class.getResource(filename);
        if (url == null) {
            throw new RuntimeException("File not found in resources: " + filename);
        }
        return URLDecoder.decode(url.getPath(), "UTF-8");
    }
}