package ca.pfv.spmf.gui.workflow_editor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.DialogSelectAlgorithmParameter;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;
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
@SuppressWarnings("serial")
/**
 * This class is the information panel of the workflow editor. It is used to display information about
 * the currently selected node of the workflow.  
 * The information panel displays information about the currently selected node based on the type of node that is selected.
 * The selected node can be either an input file node, an algorithm node or an output file node.
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
class InformationPanel extends JPanel implements DrawPanelListener {
	/** The JFrame containing the information panel */
	JFrame parent = null;
	
	/** The list of all groups currently displayed by the draw panel of the workflow editor*/
	List<GroupOfNodes> allGroups = null;
	
	/** The currently selected node, displayed by the information panel */
	Node currentNode = null;

	// ======= Output File panel =======
	/** For output node: a text field to display its name */
	private JTextField textFieldFileNameOutput;
	/** For output node: a panel to display it */
	private JPanel nodeFileOutputPanel;
	/** For output node: a button to select the output file */
	private JButton buttonOutput;

	// ======= Input File panel =======
	/** For input node: a text field to display its name */
	private JTextField textFieldFileNameInput;
	/** For input node: a panel to display it */
	private JPanel nodeFileInputPanel;
	/** For input node: a button to select the input file */
	private JButton buttonInput;
	/** For input node: a button to view the input file */
	JButton buttonViewInput;

	// ======= Algorithm node =======
	/** For algorithm node: a panel to display it */
	private JPanel nodeAlgorithmPanel;
	/** For algorithm node: a panel to display and let the user set the parameter values*/
	private ParameterSelectionPanel parameterSelectionPanel;
	/** For algorithm node: The JComboBox to select an algorithm */
	private JComboBox<String> comboBoxAlgorithms;
	/** For algorithm node: a  button to open the example from the documentation */
	private JButton buttonExample;
	/** For algorithm node: a combobox rendered that is used for displaying the list of algorithms */
	WorkflowAlgoBoxRenderer comboBoxRenderer = null;

	/**
	 * Constructor of the information panel
	 * @param parent the parent JFrame from the workflow editor
	 * @throws Exception if something bad happens
	 */
	public InformationPanel(JFrame parent) throws Exception {
		super();
		this.parent = parent;

		createNodeInputFilePanel();
		add(nodeFileInputPanel);
		nodeFileInputPanel.setVisible(false);

		createNodeOutputFilePanel();
		add(nodeFileOutputPanel);
		nodeFileOutputPanel.setVisible(false);

		createNodeAlgorithmPanel();
		add(nodeAlgorithmPanel);
		nodeAlgorithmPanel.setVisible(false);
	}

	/**
	 *  Initialize the panel for displaying nodes of type "okutput file"
	 */
	private void createNodeOutputFilePanel() {
		nodeFileOutputPanel = new JPanel();
		nodeFileOutputPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		// For information about INPUT node:
		JLabel labelFileName = new JLabel("File:");
		textFieldFileNameOutput = new JTextField(30);
		textFieldFileNameOutput.setEditable(false);

		// Set the initial gridx and gridy to 0,0
		gbc.gridx = 0;
		gbc.gridy = 0;
		// Set the anchor to north
		gbc.anchor = GridBagConstraints.NORTHWEST;
		// Set the weighty to 1 to allow vertical spacing
		gbc.weighty = 0;

		// Add the label to the information panel
		nodeFileOutputPanel.add(labelFileName, gbc);

		// Increment the gridx for the next component
		gbc.gridx = 1;
		gbc.gridy = 1;
		// Add the text field to the information panel
		nodeFileOutputPanel.add(textFieldFileNameOutput, gbc);

		buttonOutput = new JButton("...");
		buttonOutput.setToolTipText("Select a file");
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridx = 2;
		gbc.gridy = 1;
		nodeFileOutputPanel.add(buttonOutput, gbc);

		// Add a filler component with weighty = 1.0 at the end to push everything up
		gbc.gridx = 0;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.weighty = 1;
		nodeFileOutputPanel.add(Box.createVerticalGlue(), gbc);

		buttonOutput.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				((NodeFileOutput) currentNode).askUserToReplaceFile(parent);
				notifyNodeSelected(currentNode);
				textFieldFileNameOutput.setText(currentNode.name);
				parent.repaint();
			}
		});
	}

	/**
	 *  Initialize the panel for displaying nodes of type "input file"
	 */
	private void createNodeInputFilePanel() {
		nodeFileInputPanel = new JPanel();
		nodeFileInputPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		// For information about INPUT node:
		JLabel labelFileName = new JLabel("File:");
		textFieldFileNameInput = new JTextField(30);
		textFieldFileNameInput.setEditable(false);

		// Set the initial gridx and gridy to 0,0
		gbc.gridx = 0;
		gbc.gridy = 0;
		// Set the anchor to north
		gbc.anchor = GridBagConstraints.NORTHWEST;
		// Set the weighty to 1 to allow vertical spacing
		gbc.weighty = 0;

		// Add the label to the information panel
		nodeFileInputPanel.add(labelFileName, gbc);

		// Increment the gridx for the next component
		gbc.gridx = 1;
		gbc.gridy = 1;
		// Add the text field to the information panel
		nodeFileInputPanel.add(textFieldFileNameInput, gbc);

		buttonInput = new JButton("...");
		buttonInput.setToolTipText("Choose a filename");
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridx = 2;
		gbc.gridy = 1;
		nodeFileInputPanel.add(buttonInput, gbc);

		buttonViewInput = new JButton("View",
				new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/viewdata20.png")));
		buttonViewInput.setToolTipText("View the input file");
		buttonViewInput.setEnabled(false);
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridx = 3;
		gbc.gridy = 1;
		nodeFileInputPanel.add(buttonViewInput, gbc);

		// Add a filler component with weighty = 1.0 at the end to push everything up
		gbc.gridx = 0;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.weighty = 1;
		nodeFileInputPanel.add(Box.createVerticalGlue(), gbc);

		buttonInput.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				((NodeFileInput) currentNode).askUserToChooseFile(parent);
				notifyNodeSelected(currentNode);
				textFieldFileNameInput.setText(currentNode.name);
				if (currentNode.name != null && "".equals(currentNode.name) == false) {
					buttonViewInput.setEnabled(true);
				}
				parent.repaint();
			}
		});

		buttonViewInput.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				openInputFileWithViewer(((NodeFileInput) currentNode).inputFile);
			}
		});
	}

	/**
	 * Open the selected input file with the corresponding viewer tool available in
	 * SPMF
	 */
	protected void openInputFileWithViewer(String inputFile) {
		try {
			if (inputFile == null || "".equals(inputFile)) {
				JOptionPane.showMessageDialog(null,
						"This button is for viewing an input file but none is selected. Please first, click on the \"...\" button to select an input file.",
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			String algorithmName = currentNode.group.nodeAlgorithm.name;
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			DescriptionOfAlgorithm viewer = AlgorithmManager.getInstance().getViewerFor(algorithm.getInputFileTypes());

			String[] params;
			if (viewer.getParametersDescription().length > 0) {
				params = new String[viewer.getParametersDescription().length];
				for (int i = 0; i < viewer.getParametersDescription().length; i++) {
					DescriptionOfParameter paramDescription = viewer.getParametersDescription()[i];
					params[i] = askUserValueForParameter(paramDescription);
				}
			} else {
				params = new String[] {};
			}
			viewer.runAlgorithm(params, inputFile, null);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null,
					"The output file failed to open with the default application. "
							+ "\n This error occurs if there is no default application on your system "
							+ "for opening the output file or the application failed to start. " + "\n\n"
							+ "To fix the problem, consider changing the extension of the output file to .txt."
							+ "\n\n ERROR MESSAGE = " + e.toString(),
					"Error", JOptionPane.ERROR_MESSAGE);
		} catch (SecurityException e) {
			JOptionPane.showMessageDialog(null,
					"A security error occured while trying to open the output file. ERROR MESSAGE = " + e.toString(),
					"Error", JOptionPane.ERROR_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while opening the output file. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * A method that asks the user to set a value for a parameter using a dialog
	 * box.
	 * 
	 * @param paramDescription the description of the parameter to be set
	 * @return the user chosen value as a string
	 */
	private String askUserValueForParameter(DescriptionOfParameter paramDescription) {
		// create an instance of the dialog class
		DialogSelectAlgorithmParameter dialog = new DialogSelectAlgorithmParameter(paramDescription, parent);
		// return the user input from the dialog
		return dialog.getUserInput();
	}

	/**
	 *  Initialize the panel for displaying nodes of type "algorithm"
	 */
	private void createNodeAlgorithmPanel() throws Exception {
		nodeAlgorithmPanel = new JPanel();
		nodeAlgorithmPanel.setLayout(new GridBagLayout());

		GridBagConstraints gbc = new GridBagConstraints();

		// Label algorithm name
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.weighty = 0;
		nodeAlgorithmPanel.add(new JLabel("Name:"), gbc);

		// Textfield algorithm name
		gbc.gridx = 1;
		gbc.gridy = 1;

		comboBoxAlgorithms = new JComboBox<>(new Vector<>());
		comboBoxAlgorithms.setMaximumRowCount(30);
		comboBoxAlgorithms.addItem("");
		// Assuming AlgorithmManager and showTools, showAlgorithms, showExperimentTools
		// are defined elsewhere
		List<String> algorithmList = AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true, true, true, true, true);
		for (String algorithmOrCategoryName : algorithmList) {
			comboBoxAlgorithms.addItem(algorithmOrCategoryName);
		}
		comboBoxRenderer = new WorkflowAlgoBoxRenderer(comboBoxAlgorithms);
		comboBoxAlgorithms.setRenderer(comboBoxRenderer);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.gridwidth = 3;
		nodeAlgorithmPanel.add(comboBoxAlgorithms, gbc);

		buttonExample = new JButton("?");
		buttonExample.setEnabled(false);
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.gridx = 4;
		gbc.gridy = 1;
		nodeAlgorithmPanel.add(buttonExample, gbc);

		// Label parameters
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.weighty = 0;
		nodeAlgorithmPanel.add(new JLabel("Parameters:"), gbc);

		// Parameters table
		gbc.gridx = 1;
		gbc.gridy = 3;
		gbc.weighty = 0;
		parameterSelectionPanel = new ParameterSelectionPanel(null);
		nodeAlgorithmPanel.add(parameterSelectionPanel, gbc);

		// What to do when the user choose an algorithm :
		comboBoxAlgorithms.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent evt) {
				if (evt.getStateChange() == ItemEvent.SELECTED) {
					updateUserInterfaceForAlgorithm(evt.getItem().toString(), null, true);
				}
			}

		});

		buttonExample.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				// When the user clicks on the "?",
				// we open the webpage of the selected algorithm.
				String choice = (String) comboBoxAlgorithms.getSelectedItem();
				try {
					openHelpWebPageForAlgorithm(choice);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * This method show the help webpage for a given algorithm in the default
	 * browser of the user.
	 * 
	 * @param algorithmName the algorithm name (e.g. "PrefixSpan")
	 * @throws Exception
	 */
	private void openHelpWebPageForAlgorithm(String algorithmName) throws Exception {
		DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
		if (algorithm != null) {
			try {
				java.awt.Desktop.getDesktop().browse(java.net.URI.create(algorithm.getURLOfDocumentation()));
			} catch (java.io.IOException e) {
				System.out.println(e.getMessage());
			}
		}
	}

	/**
	 * Update the Example button after an algorithm is selected
	 * 
	 * @param algorithm the algorithm
	 */
	private void updateExampleButton(DescriptionOfAlgorithm algorithm) {
		boolean visible = algorithm != null;
		buttonExample.setEnabled(visible);
	}

	@Override
	public void notifyNodeSelected(Node node) {
		if (node == currentNode) {
			return;
		} else {
			saveInformation(currentNode);
		}
		this.currentNode = node;

		boolean isOutputFile = node != null && node instanceof NodeFileOutput;
		boolean isInputFile = node != null && node instanceof NodeFileInput;
		boolean isAlgorithm = node != null && node instanceof NodeAlgorithm;

		nodeFileOutputPanel.setVisible(isOutputFile);
		nodeFileInputPanel.setVisible(isInputFile);
		if (isAlgorithm) {
			nodeAlgorithmPanel.setVisible(true);
			updateUserInterfaceForAlgorithm(node.name, ((NodeAlgorithm) node).parameters, false);
		} else {
			nodeAlgorithmPanel.setVisible(false);
		}

		if (isOutputFile) {
			textFieldFileNameOutput.setText(currentNode.name);
		}

		if (node == null) {
			// Set the text of the labels to the node information
			setBorder(BorderFactory.createTitledBorder(""));
			return;
		} else {
			// Set the text of the labels to the node information
			setBorder(BorderFactory.createTitledBorder(node.getType()));
		}
	}

	/**
	 * Method to update the items in the JComboBox based on an algorithm
	 * @param group the group of nodes
	 * @throws Exception if something happens
	 */
	public void updateComboBoxItems(GroupOfNodes group) throws Exception {
		// Find the index of the current group
		int index = allGroups.indexOf(group);

		int cardinality = comboBoxRenderer.cardinality;
		comboBoxRenderer.suggested.set(0, cardinality, true);

		if (allGroups.size() >= 1 && index > 0) {
			
			GroupOfNodes previousGroup = allGroups.get(index - 1);
			
			DescriptionOfAlgorithm previousAlgorithm = AlgorithmManager.getInstance()
					.getDescriptionOfAlgorithm(previousGroup.nodeAlgorithm.name);
			
			String[] outputTypes = previousAlgorithm.getOutputFileTypes();

			if (outputTypes == null) {
				comboBoxRenderer.suggested.set(0, cardinality, false);
			} else {
				for (int i = 1; i < comboBoxAlgorithms.getItemCount(); i++) {
					String algo = comboBoxAlgorithms.getItemAt(i);

					if ("Open_text_file_with_SPMF_text_editor".equals(algo) || "Open_text_file_with_system_text_editor".equals(algo) ||
							"Open_text_file_with_pattern_viewer".equals(algo)) {
						comboBoxRenderer.suggested.set(i, true);
					}else if (algo.startsWith(" --")) {
						comboBoxRenderer.suggested.set(i, true);
					}else if ("Visualize_With_ItemItemset_Matrix".equals(algo)){
						// Check if the itemset matrix viewer should be added
						for(String type: outputTypes) {
							if(type.contains("itemset")) {
								comboBoxRenderer.suggested.set(i, true);
								break;
							}
						}
					}else{
						DescriptionOfAlgorithm algorithmCandidate = AlgorithmManager.getInstance()
								.getDescriptionOfAlgorithm(algo);
						String[] inputTypes = algorithmCandidate.getInputFileTypes();
						if (inputTypes == null) {
							comboBoxRenderer.suggested.set(i, cardinality, false);
						} else {
//							String inputMainType = inputTypes[inputTypes.length - 1];
							if (!hasCommonType(outputTypes, inputTypes)) {
//							if (!inputMainType.contains(mainOutputType)) {
								comboBoxRenderer.suggested.set(i, false);
							}else {
								comboBoxRenderer.suggested.set(i, true);
							}

						}
					}

				}
			}
		}
	}

	/**
	 * Check if there is something in common between an array of input types and an array of output types
	 * @param outputTypes the array of output types
	 * @param inputTypes the array of input types
	 * @return true if there is something in common
	 */
	public boolean hasCommonType(String[] outputTypes, String[] inputTypes) {
		for (String inputType : inputTypes) {
			for (String outputType : outputTypes) {
				if (!"Patterns".equals(outputType) &&  ! "Database of instances".equals(outputType)) {
					if (outputType.equals(inputType)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Uptdate the user interface for a given algorithm
	 * @param algorithmName the algorithm name
	 * @param parameters the list of parameters of the algorithm
	 * @param comboBoxHasChanged Set it to true if the combobbox of algorithms was changed. otherwise false.
	 */
	private void updateUserInterfaceForAlgorithm(String algorithmName, String[] parameters,
			boolean comboBoxHasChanged) {
		// We need to update the user interface:
		try {
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);

			boolean hasOutput = algorithm != null && algorithm.getOutputFileTypes() != null;
			boolean hasInput = algorithm != null && algorithm.getInputFileTypes() != null;

			updateComboBoxItems(currentNode.group);

			if (comboBoxHasChanged == false) {
				if (algorithm == null) {
					comboBoxAlgorithms.setSelectedIndex(0);
				} else {
					comboBoxAlgorithms.setSelectedItem(algorithmName);
				}
			}

			updateExampleButton(algorithm);
			updateParameterPanel(algorithm, parameters);
			((NodeAlgorithm) currentNode).updateAlgorithmChoice(algorithmName, hasOutput, hasInput);
			parent.repaint();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Save the information of the current node from the information panel to the node itself.
	 * @param currentNode2 the current node
	 */
	void saveInformation(Node currentNode2) {
//		boolean isOutputFile = currentNode instanceof NodeFileOutput;
//		boolean isInputFile = currentNode instanceof NodeFileInput;
		boolean isAlgorithm = currentNode instanceof NodeAlgorithm;

		if (isAlgorithm) {
			final String[] parameters = parameterSelectionPanel.getParameterValues();
			((NodeAlgorithm) currentNode).parameters = parameters;
		}
	}

	/**
	 * Update the parameter panel after an algorithm is selected
	 * 
	 * @param algorithm  the algorithm
	 * @param parameters a list of parameter values to be put in the panel or NULL
	 *                   if they should not be updated.
	 */
	private void updateParameterPanel(DescriptionOfAlgorithm algorithm, String[] parameters) {
		parameterSelectionPanel.update(algorithm);
		parameterSelectionPanel.setParameterValues(parameters);
	}

	@Override
	public void notifyHasOutputNode(boolean hasOutput) {
	
	}

	@Override
	public void notifyOfListOfGroups(List<GroupOfNodes> allGroups) {
		this.allGroups = allGroups;
	}

}
