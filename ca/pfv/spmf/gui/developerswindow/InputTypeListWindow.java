package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

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
 * A class for developers that display the list of input types
 * for algorithms in SPMF and obtain more informaton about them
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class InputTypeListWindow {

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	public InputTypeListWindow(boolean runAsStandalone) throws Exception {

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
		JFrame frame = new JFrame("SPMF - List of Algorithms by Input Types");
		if (runAsStandalone) {
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		frame.setSize(800, 600);

		// Create a map to store the input types and the algorithms that have them
		Map<String, List<String>> inputTypes = new HashMap<>();

		// Loop through the list of algorithms and add them to the map
		for (String algorithmName : algorithms) {
			if (algorithmName.contains("---")) {
				continue;
			}
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			String[] inputTypesOfAlgorithm = algorithm.getInputFileTypes();
			if (inputTypesOfAlgorithm != null) {
				String mainType =  inputTypesOfAlgorithm[inputTypesOfAlgorithm.length-1];
				

				
				
//				for (String type : inputTypesOfAlgorithm) {
					// If the type is not in the map, create a new list for it
					List<String> list = inputTypes.get(mainType);
					if (list == null) {
						list =  new ArrayList<String>();
						inputTypes.put(mainType,list);
					}
					// Add the algorithm name to the list of the type
					list.add(algorithmName);
//				}
			}
		}
		

		// Create a list model to store the data for the JList
		DefaultListModel<String> inputListModel = new DefaultListModel<>();
		
		List<String> inputTypesList = new ArrayList<String>(inputTypes.keySet());
		
		// Sort the list of algorithms by their input types using the custom comparator
		Collections.sort(inputTypesList, new Comparator<String>() {
			public int compare(String obj1, String obj2) {
					return obj1.compareTo(obj2);
				}
		});

		// Add the types to the list model
		for (String type : inputTypesList) {
			inputListModel.addElement(type);
		}

		// Create a JList with the list model
		JList<String> inputList = new JList<>(inputListModel);

		// In your displayTypes method, set the custom ListCellRenderer to your JList
		inputList.setCellRenderer(new InputTypeListCellRenderer());


		// Create a text area to display the algorithms for the selected input type
		JTextArea algorithmTextArea = new JTextArea();
		algorithmTextArea.setEditable(false);

		// Create a list selection listener to update the text area when an input type is selected
		inputList.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				// Get the selected input type
				String selectedType = inputList.getSelectedValue();
				// Clear the text area
				algorithmTextArea.setText("");
				// If the input type is not null, append the algorithms that have it to the text area
				if (selectedType != null) {
					for (String algorithm : inputTypes.get(selectedType)) {
						algorithmTextArea.append(algorithm + System.lineSeparator());
					}
				}
			}
		});

		// Create a scroll pane to hold the JList
		JScrollPane inputScrollPane = new JScrollPane(inputList);

		// Create a scroll pane to hold the text area
		JScrollPane algorithmScrollPane = new JScrollPane(algorithmTextArea);

		// Create a label for the JList
		JLabel inputLabel = new JLabel("Input Types (" + inputTypesList.size() + ")", SwingConstants.CENTER);

		// Create a label for the text area
		JLabel algorithmLabel = new JLabel("Algorithms", SwingConstants.CENTER);

		// Create a panel to hold the label and the scroll pane for the input types
		JPanel inputPanel = new JPanel(new BorderLayout());

		// Create a panel to hold the label and the scroll pane for the algorithms
		JPanel algorithmPanel = new JPanel(new BorderLayout());

		// Add the labels and the scroll panes to the panels
		inputPanel.add(inputLabel, BorderLayout.NORTH);
		inputPanel.add(inputScrollPane, BorderLayout.CENTER);
		algorithmPanel.add(algorithmLabel, BorderLayout.NORTH);
		algorithmPanel.add(algorithmScrollPane, BorderLayout.CENTER);
		

		// Create a split pane to hold the two panels
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, algorithmPanel);

		// Add the split pane to the frame
		frame.add(splitPane, BorderLayout.CENTER);
		
		JLabel explanationLabel = new JLabel("Green color = has a viewer tool,  Red color = has no viewer tool", SwingConstants.CENTER);
		frame.add(explanationLabel, BorderLayout.SOUTH);

		// Set the window in the center of the screen
		frame.setLocationRelativeTo(null);

		// Make the frame visible
		frame.setVisible(true);

		// Set the divider location to 50%
		splitPane.setDividerLocation(0.5);

	}

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("unused")
		InputTypeListWindow viewer = new InputTypeListWindow(true);
	}
	
	// Create a custom ListCellRenderer class that extends DefaultListCellRenderer
	private static class InputTypeListCellRenderer extends DefaultListCellRenderer {
	    /**
		 * Serial UID
		 */
		private static final long serialVersionUID = -5826159937196573338L;

		@Override
	    public Component getListCellRendererComponent(@SuppressWarnings("rawtypes") JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
	        // Call the super method to get the default component
	        Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
	        // Cast the value to a String
	        String inputType = (String) value;
	        // Check if the input type has a viewer
	        try {
				if (AlgorithmManager.getInstance().getViewerFor(new String[] {inputType}) == null) {
				    // If not, set the background color to red
				    c.setBackground(Color.RED);
				} else {
				    // If yes, set the background color to green
				    c.setBackground(Color.GREEN);
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        // Return the modified component
	        return c;
	    }
	}
}