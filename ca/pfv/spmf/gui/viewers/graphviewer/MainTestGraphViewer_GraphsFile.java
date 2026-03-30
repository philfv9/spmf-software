package ca.pfv.spmf.gui.viewers.graphviewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
/**
 * This is an example of how to use the Graph Viewer to load a graph database file and display it.
 * @author Philippe Fournier-Viger
 */
public class MainTestGraphViewer_GraphsFile {



	public static void main(String[] args) throws IOException {
		boolean runAsStandalone = true;
		boolean displayGraphStringRepresentation = true;
		GraphViewer frame = new GraphViewer(runAsStandalone,displayGraphStringRepresentation);
//		frame.loadSampleGraph();
		frame.loadFileGSPANFormat(fileToPath("contextTKG.txt"));

	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestGraphViewer_GraphsFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
	
}