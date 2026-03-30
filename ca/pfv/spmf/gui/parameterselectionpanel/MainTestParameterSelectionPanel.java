package ca.pfv.spmf.gui.parameterselectionpanel;

import javax.swing.JFrame;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 * Class for testing the ParameterSelectionPanel
 * 
 * @author Philippe Fournier-Viger, 2024.
 */
public class MainTestParameterSelectionPanel {
	/**
	 * Method for testing
	 * @param args arguments
	 * @throws Exception if some exception happens
	 */
	public static void main(String[] args) throws Exception {
		JFrame frame = new JFrame("Parameter Selection Panel");
		DescriptionOfAlgorithm description = AlgorithmManager.getInstance().getDescriptionOfAlgorithm("CM-SPAM");
		ParameterSelectionPanel panel = new ParameterSelectionPanel(description);
		frame.add(panel);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
}
