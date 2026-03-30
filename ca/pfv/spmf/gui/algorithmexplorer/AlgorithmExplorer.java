package ca.pfv.spmf.gui.algorithmexplorer;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.MainWindow;

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
 * JTree that displays the algorithms offered in SPMF
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgorithmExplorer extends JFrame {
	/** Serial UID */
	private static final long serialVersionUID = 6208435839510050052L;

	// UI Layout Constants
	private static final int WINDOW_WIDTH = 800;
	private static final int WINDOW_HEIGHT = 800;
	private static final int TREE_X = 20;
	private static final int TREE_Y = 45;
	private static final int TREE_WIDTH = 276;
	private static final int TREE_HEIGHT = 700;
	private static final int LABEL_X = 307;
	private static final int FIELD_X = 395;
	private static final int FIELD_WIDTH = 380;
	private static final int FIELD_HEIGHT = 20;
	
	// Font Constants
	private static final Font BOLD_FONT = new Font("Tahoma", Font.BOLD, 12);
	
	// Table Column Names
	private static final String[] PARAMETER_COLUMNS = {"Name", "Type", "Optional?"};

	// UI Components
	private JTextField fieldName;
	private JTextField fieldAuthors;
	private JTextField fieldCategory;
	private JTextField fieldType;
	private JTextField fieldDoc;

	private final DefaultListModel<String> listInputModel = new DefaultListModel<>();
	private final DefaultListModel<String> listOutputModel = new DefaultListModel<>();
	private DefaultTableModel listParametersModel;

	private JButton buttonWeb;
	private JTable tableParameters;
	private AlgorithmJTree treePanel;
	private JButton buttonRemoveHighlight;
	private JButton buttonAddHighlightWithoutTheParams;
	private JButton buttonAddHighlightWithParams;

	/**
	 * Constructor for AlgorithmExplorer
	 * 
	 * @param runAsStandalone true if running as standalone application
	 */
	public AlgorithmExplorer(boolean runAsStandalone) {
		initializeFrame();
		initializeComponents();
		layoutComponents();
		setupEventListeners();
		
		if (runAsStandalone) {
			this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		
		this.setTitle("Algorithm Explorer");
		this.setLocationRelativeTo(null);
		this.setVisible(true);
	}

	/**
	 * Initialize the main frame
	 */
	private void initializeFrame() {
		setIconImage(Toolkit.getDefaultToolkit().getImage(
				MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));
		getContentPane().setLayout(null);
		setResizable(false);
		this.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
	}

	/**
	 * Initialize all UI components
	 */
	private void initializeComponents() {
		treePanel = new AlgorithmJTree(true, true, true, true, true);
		
		// Initialize text fields
		fieldName = createTextField();
		fieldAuthors = createTextField();
		fieldCategory = createTextField();
		fieldType = createTextField();
		fieldDoc = createTextField();
		
		// Initialize buttons
		buttonWeb = new JButton("Open");
		buttonRemoveHighlight = new JButton("Remove highlight");
		buttonAddHighlightWithoutTheParams = new JButton("Highlight algorithms with same in/out");
		buttonAddHighlightWithParams = new JButton("Highlight algorithms with same in/out/mandatory parameters");
		
		buttonWeb.setEnabled(true);
		buttonRemoveHighlight.setEnabled(false);
		buttonAddHighlightWithoutTheParams.setEnabled(false);
		buttonAddHighlightWithParams.setEnabled(false);
		
		// Initialize table
		listParametersModel = new DefaultTableModel(new Object[][] {}, PARAMETER_COLUMNS);
		tableParameters = new JTable(listParametersModel);
	}

	/**
	 * Layout all components on the frame
	 */
	private void layoutComponents() {
		// Tree panel
		JScrollPane treeScroll = new JScrollPane(treePanel);
		treeScroll.setBounds(TREE_X, TREE_Y, TREE_WIDTH, TREE_HEIGHT);
		getContentPane().add(treeScroll);
		
		// Title label
		JLabel lblAlgorithmList = createTitleLabel();
		lblAlgorithmList.setBounds(10, 20, 278, 14);
		getContentPane().add(lblAlgorithmList);
		
		// Information section title
		JLabel lblInfo = new JLabel("Algorithm information");
		lblInfo.setHorizontalAlignment(SwingConstants.CENTER);
		lblInfo.setFont(BOLD_FONT);
		lblInfo.setBounds(306, 45, 469, 14);
		getContentPane().add(lblInfo);
		
		// Add all field labels and text fields
		addFieldRow("Name", fieldName, 70);
		addFieldRow("Category:", fieldCategory, 95);
		addFieldRow("Type:", fieldType, 120);
		addFieldRow("Coded by:", fieldAuthors, 148);
		addDocumentationRow();
		
		// Add lists
		addListSection("Input type:", listInputModel, 214);
		addListSection("Output type:", listOutputModel, 328);
		
		// Add parameters table
		addParametersSection();
		
		// Add buttons
		layoutButtons();
	}

	/**
	 * Create a standard text field
	 * 
	 * @return configured JTextField
	 */
	private JTextField createTextField() {
		JTextField field = new JTextField();
		field.setHorizontalAlignment(SwingConstants.LEFT);
		field.setEditable(false);
		field.setColumns(10);
		return field;
	}

	/**
	 * Create the title label with algorithm count
	 * 
	 * @return configured JLabel
	 */
	private JLabel createTitleLabel() {
		int algorithmCount = 0;
		try {
			algorithmCount = AlgorithmManager.getInstance()
					.getListOfAlgorithmsAsString(true, true, true, true, true).size();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		JLabel label = new JLabel("Choose an algorithm (" + algorithmCount + "):");
		label.setFont(BOLD_FONT);
		return label;
	}

	/**
	 * Add a label and text field row
	 * 
	 * @param labelText the label text
	 * @param field the text field
	 * @param yPosition the y position
	 */
	private void addFieldRow(String labelText, JTextField field, int yPosition) {
		JLabel label = new JLabel(labelText);
		label.setBounds(LABEL_X, yPosition, 75, 14);
		getContentPane().add(label);
		
		field.setBounds(FIELD_X, yPosition - 3, FIELD_WIDTH, FIELD_HEIGHT);
		getContentPane().add(field);
	}

	/**
	 * Add the documentation field with open button
	 */
	private void addDocumentationRow() {
		JLabel labelDoc = new JLabel("Example:");
		labelDoc.setBounds(LABEL_X, 179, 100, 14);
		getContentPane().add(labelDoc);
		
		fieldDoc.setBounds(FIELD_X, 176, 300, FIELD_HEIGHT);
		getContentPane().add(fieldDoc);
		
		buttonWeb.setBounds(702, 175, 73, 23);
		getContentPane().add(buttonWeb);
	}

	/**
	 * Add a list section with label and scroll pane
	 * 
	 * @param labelText the label text
	 * @param listModel the list model
	 * @param yPosition the y position
	 */
	private void addListSection(String labelText, DefaultListModel<String> listModel, int yPosition) {
		JLabel label = new JLabel(labelText);
		label.setBounds(LABEL_X, yPosition + 2, 100, 14);
		getContentPane().add(label);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(FIELD_X, yPosition, FIELD_WIDTH, 95);
		getContentPane().add(scrollPane);
		
		JList<String> list = new JList<>(listModel);
		list.setBackground(getContentPane().getBackground());
		scrollPane.setViewportView(list);
	}

	/**
	 * Add the parameters table section
	 */
	private void addParametersSection() {
		JLabel labelParameters = new JLabel("Parameters:");
		labelParameters.setBounds(LABEL_X, 435, 100, 14);
		getContentPane().add(labelParameters);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(FIELD_X, 434, FIELD_WIDTH, 194);
		scrollPane.setViewportView(tableParameters);
		getContentPane().add(scrollPane);
	}

	/**
	 * Layout the action buttons
	 */
	private void layoutButtons() {
		buttonAddHighlightWithoutTheParams.setBounds(364, 639, 411, 23);
		getContentPane().add(buttonAddHighlightWithoutTheParams);
		
		buttonAddHighlightWithParams.setBounds(364, 668, 411, 23);
		getContentPane().add(buttonAddHighlightWithParams);
		
		buttonRemoveHighlight.setBounds(365, 696, 410, 23);
		getContentPane().add(buttonRemoveHighlight);
	}

	/**
	 * Setup all event listeners
	 */
	private void setupEventListeners() {
		buttonWeb.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				openWebPage(fieldDoc.getText());
			}
		});
		
		buttonRemoveHighlight.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				removeHighlight();
			}
		});
		
		buttonAddHighlightWithoutTheParams.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				addHighlightWithoutParameters();
			}
		});
		
		buttonAddHighlightWithParams.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				addHighlightWithParameters();
			}
		});
		
		treePanel.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
			@Override
			public void valueChanged(TreeSelectionEvent e) {
				handleTreeSelection();
			}
		});
	}

	/**
	 * Handle tree selection change
	 */
	private void handleTreeSelection() {
		DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treePanel.getLastSelectedPathComponent();
		
		if (selectedNode == null) {
			clearAlgorithmDetails();
			return;
		}
		
		String algoName = selectedNode.getUserObject().toString();
		DescriptionOfAlgorithm description = getAlgorithmDescription(algoName);
		
		if (description != null) {
			displayAlgorithmDetails(algoName, description);
			updateHighlightButtons(false);
		} else {
			clearAlgorithmDetails();
		}
	}

	/**
	 * Get algorithm description from manager
	 * 
	 * @param algoName the algorithm name
	 * @return the description or null
	 */
	private DescriptionOfAlgorithm getAlgorithmDescription(String algoName) {
		try {
			AlgorithmManager manager = AlgorithmManager.getInstance();
			return manager.getDescriptionOfAlgorithm(algoName);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Display algorithm details in UI
	 * 
	 * @param algoName the algorithm name
	 * @param description the algorithm description
	 */
	private void displayAlgorithmDetails(String algoName, DescriptionOfAlgorithm description) {
		fieldName.setText(algoName);
		fieldAuthors.setText(description.getImplementationAuthorNames());
		fieldCategory.setText(description.getAlgorithmCategory());
		fieldType.setText(description.getAlgorithmType().toString());
		fieldDoc.setText(description.getURLOfDocumentation());
		buttonWeb.setEnabled(true);
		
		updateInputList(description.getInputFileTypes());
		updateOutputList(description.getOutputFileTypes());
		updateParametersTable(description.getParametersDescription());
	}

	/**
	 * Update the input types list
	 * 
	 * @param inputTypes array of input types
	 */
	private void updateInputList(String[] inputTypes) {
		listInputModel.clear();
		if (inputTypes != null && inputTypes.length > 0) {
			for (String type : inputTypes) {
				listInputModel.addElement(type);
			}
		}
	}

	/**
	 * Update the output types list
	 * 
	 * @param outputTypes array of output types
	 */
	private void updateOutputList(String[] outputTypes) {
		listOutputModel.clear();
		if (outputTypes != null && outputTypes.length > 0) {
			for (String type : outputTypes) {
				listOutputModel.addElement(type);
			}
		}
	}

	/**
	 * Update the parameters table
	 * 
	 * @param parameters array of parameter descriptions
	 */
	private void updateParametersTable(DescriptionOfParameter[] parameters) {
		listParametersModel.setRowCount(0);
		if (parameters != null) {
			for (DescriptionOfParameter parameter : parameters) {
				listParametersModel.addRow(new String[] { 
					parameter.getName(),
					parameter.getParameterType().getSimpleName(),
					Boolean.toString(parameter.isOptional()) 
				});
			}
		}
	}

	/**
	 * Clear all algorithm details from UI
	 */
	private void clearAlgorithmDetails() {
		fieldName.setText("");
		fieldAuthors.setText("");
		fieldCategory.setText("");
		fieldType.setText("");
		fieldDoc.setText("");
		buttonWeb.setEnabled(false);
		
		listInputModel.clear();
		listOutputModel.clear();
		listParametersModel.setRowCount(0);
		
		updateHighlightButtons(true);
	}

	/**
	 * Update the state of highlight buttons
	 * 
	 * @param disable true to disable highlight buttons
	 */
	private void updateHighlightButtons(boolean disable) {
		if (treePanel.isActivatedHighlight()) {
			return;
		}
		
		buttonAddHighlightWithoutTheParams.setEnabled(!disable);
		buttonAddHighlightWithParams.setEnabled(!disable);
		buttonRemoveHighlight.setEnabled(disable);
	}

	/**
	 * Remove highlight from tree
	 */
	protected void removeHighlight() {
		treePanel.setActivatedHighlight(false);
		buttonAddHighlightWithoutTheParams.setEnabled(true);
		buttonAddHighlightWithParams.setEnabled(true);
		buttonRemoveHighlight.setEnabled(false);
	}

	/**
	 * Add highlight with parameters to tree
	 */
	protected void addHighlightWithParameters() {
		treePanel.highlightSimilarAlgorithmsToSelection(true);
		buttonAddHighlightWithoutTheParams.setEnabled(false);
		buttonAddHighlightWithParams.setEnabled(false);
		buttonRemoveHighlight.setEnabled(true);
	}

	/**
	 * Add highlight without parameters to tree
	 */
	protected void addHighlightWithoutParameters() {
		treePanel.highlightSimilarAlgorithmsToSelection(false);
		buttonAddHighlightWithoutTheParams.setEnabled(false);
		buttonAddHighlightWithParams.setEnabled(false);
		buttonRemoveHighlight.setEnabled(true);
	}

	/**
	 * This method open a URL in the default web browser.
	 *
	 * @param url : URL of the webpage
	 */
	private void openWebPage(String url) {
		try {
			java.awt.Desktop.getDesktop().browse(URI.create(url));
		} catch (IOException e) {
			System.err.println("Error opening web page: " + e.getMessage());
		}
	}
}