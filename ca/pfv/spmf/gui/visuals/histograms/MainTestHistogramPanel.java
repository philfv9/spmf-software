package ca.pfv.spmf.gui.visuals.histograms;

import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;
/*
 * Copyright (c) 2008-2023 Philippe Fournier-Viger
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
 * A class to test the HeatMapViewer
 * 
 * @author Philippe Fournier-Viger
 */
class MainTestHistogramPanel {
	@SuppressWarnings("unused")
	public static void main(String[] args) throws UnsupportedEncodingException {

		// Create some sample values and labels arrays
		int size = 20;
		int[] xLabels = new int[size];
		int[] yValues = new int[size];
		for (int i = 0; i < size; i++) {
			xLabels[i] = i + 1;
			yValues[i] = i * 10;
		}
		String title = "Title of the histogram";

		HistogramDistributionWindow window = new HistogramDistributionWindow(true, yValues, xLabels, title, true, true,
				"x Axis", "y axis", null, Order.ASCENDING_X);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestHistogramPanel.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}