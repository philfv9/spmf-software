package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * A class for developers that display the list of output types and input types
 * for algorithms in SPMF
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class InputOutputTypeListWindow {

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	public InputOutputTypeListWindow(boolean runAsStandalone) throws Exception {

		displayTypes(AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true,true,true, true, true), runAsStandalone);
	}

	/**
	 * Initialize the window
	 * 
	 * @param algorithms      the list of algorithms to be used for the display
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	public static void displayTypes(List<String> algorithms, boolean runAsStandalone) throws Exception {
		// Create a new frame
		JFrame frame = new JFrame("SPMF - List of All Algorithms Input and Output Types");
		if (runAsStandalone) {
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		frame.setSize(800, 600);

		// Create two maps to store the output types and input types with their counts
		Map<String, Integer> outputTypes = new HashMap<>();
		Map<String, Integer> inputTypes = new HashMap<>();

		// Loop through the list of algorithms and add their types to the maps
		for (String algorithmName : algorithms) {
			if (algorithmName.contains("---")) {
				continue;
			}
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			String[] outputTypesOfAlgorithm = algorithm.getOutputFileTypes();
			String[] inputTypesOfAlgorithm = algorithm.getInputFileTypes();
			if (outputTypesOfAlgorithm != null) {
				for (String type : outputTypesOfAlgorithm) {
					outputTypes.put(type, outputTypes.getOrDefault(type, 0) + 1);
				}
			}
			if (inputTypesOfAlgorithm != null) {
				for (String type : inputTypesOfAlgorithm) {
					inputTypes.put(type, inputTypes.getOrDefault(type, 0) + 1);
				}
			}
		}

		// Create two list models to store the data for the JLists
		DefaultListModel<String> outputListModel = new DefaultListModel<>();
		DefaultListModel<String> inputListModel = new DefaultListModel<>();

		// Add the types and their counts to the list models
		for (Map.Entry<String, Integer> entry : outputTypes.entrySet()) {
			outputListModel.addElement(entry.getKey() + " (" + entry.getValue() + ")");
		}
		for (Map.Entry<String, Integer> entry : inputTypes.entrySet()) {
			inputListModel.addElement(entry.getKey() + " (" + entry.getValue() + ")");
		}

		// Create a custom comparator that compares the counts in the parentheses
		Comparator<String> comparator = new Comparator<String>() {
			@Override
			public int compare(String s1, String s2) {
				// Extract the counts from the strings
				int count1 = Integer.parseInt(s1.substring(s1.lastIndexOf("(") + 1, s1.lastIndexOf(")")));
				int count2 = Integer.parseInt(s2.substring(s2.lastIndexOf("(") + 1, s2.lastIndexOf(")")));
				// Compare the counts in reverse order
				return Integer.compare(count2, count1);
			}
		};

		// Sort the list models using the comparator
		// Create two lists to store the data from the list models
		List<String> outputListData = new ArrayList<>();
		List<String> inputListData = new ArrayList<>();

		// Loop through the list models and add the data to the lists
		for (int i = 0; i < outputListModel.size(); i++) {
			outputListData.add(outputListModel.get(i));
		}
		for (int i = 0; i < inputListModel.size(); i++) {
			inputListData.add(inputListModel.get(i));
		}

		// Sort the lists using the comparator
		Collections.sort(outputListData, comparator);
		Collections.sort(inputListData, comparator);

		// Clear the list models
		outputListModel.clear();
		inputListModel.clear();

		// Add the sorted data back to the list models
		for (String data : outputListData) {
			outputListModel.addElement(data);
		}
		for (String data : inputListData) {
			inputListModel.addElement(data);
		}


		// Create two JLists with the list models
		JList<String> outputList = new JList<>(outputListModel);
		JList<String> inputList = new JList<>(inputListModel);

		// Create two scroll panes to hold the JLists
		JScrollPane outputScrollPane = new JScrollPane(outputList);
		JScrollPane inputScrollPane = new JScrollPane(inputList);

		// Create two labels for the JLists
		JLabel outputLabel = new JLabel("Output Types (count)", SwingConstants.CENTER);
		JLabel inputLabel = new JLabel("Input Types (count)", SwingConstants.CENTER);

		// Create two panels to hold the labels and the scroll panes
		JPanel outputPanel = new JPanel(new BorderLayout());
		JPanel inputPanel = new JPanel(new BorderLayout());

		// Add the labels and the scroll panes to the panels
		outputPanel.add(outputLabel, BorderLayout.NORTH);
		outputPanel.add(outputScrollPane, BorderLayout.CENTER);
		inputPanel.add(inputLabel, BorderLayout.NORTH);
		inputPanel.add(inputScrollPane, BorderLayout.CENTER);

		// Create a split pane to hold the two panels
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, outputPanel);

		// Add the split pane to the frame
		frame.add(splitPane, BorderLayout.CENTER);

		// Set the window in the center of the screen
		frame.setLocationRelativeTo(null);

		// Make the frame visible
		frame.setVisible(true);

		// Set the divider location to 50%
		splitPane.setDividerLocation(0.5);

	}

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("unused")
		InputOutputTypeListWindow viewer = new InputOutputTypeListWindow(true);
	}
}
