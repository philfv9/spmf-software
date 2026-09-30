package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.gui.SortableJTable;

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
 * 
 * Do not remove the copyright and license information
 */
/**
 * A class for developers that display the list of output types and input types
 * for algorithms in SPMF.
 *
 * @author Philippe Fournier-Viger
 *
 */
public class InputOutputTypeListWindow {

	/**
	 * name of the special category used to group algorithms that do not
	 * have any output type (i.e. getOutputFileTypes() returns null).
	 */
	private static final String NO_OUTPUT_CATEGORY = "NO OUTPUT";

	/**
	 * name of the special category used to group algorithms that do not
	 * have any input type (i.e. getInputFileTypes() returns null).
	 */
	private static final String NO_INPUT_CATEGORY = "NO INPUT";

	/**
	 * title of the "Full Mapping (Table View)" tab
	 */
	private static final String TABLE_VIEW_TAB_TITLE = "Full Mapping (Table View)";

	/**
	 * Constructor
	 *
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	public InputOutputTypeListWindow(boolean runAsStandalone) throws Exception {

		displayTypes(AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true, true, true, true, true),
				runAsStandalone);
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
		frame.setSize(900, 650);
		frame.setMinimumSize(new java.awt.Dimension(650, 450));

		// instead of only storing counts, we now store the full list of
		// algorithm names for each output/input type, so that the "Full Mapping"
		// table view can display them (counts can be derived from list sizes).
		Map<String, List<String>> outputTypes = new HashMap<>();
		Map<String, List<String>> inputTypes = new HashMap<>();

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
					outputTypes.computeIfAbsent(type, k -> new ArrayList<>()).add(algorithmName);
				}
			} else {
				// algorithms without an output type are counted under the
				// special "NO OUTPUT" category instead of being ignored.
				outputTypes.computeIfAbsent(NO_OUTPUT_CATEGORY, k -> new ArrayList<>()).add(algorithmName);
			}
			if (inputTypesOfAlgorithm != null) {
				for (String type : inputTypesOfAlgorithm) {
					inputTypes.computeIfAbsent(type, k -> new ArrayList<>()).add(algorithmName);
				}
			} else {
				// algorithms without an input type are counted under the
				// special "NO INPUT" category instead of being ignored.
				inputTypes.computeIfAbsent(NO_INPUT_CATEGORY, k -> new ArrayList<>()).add(algorithmName);
			}
		}

		// sort the algorithms within each type alphabetically, and remove
		// duplicates that could occur if an algorithm lists the same type twice
		for (List<String> list : outputTypes.values()) {
			Collections.sort(list);
		}
		for (List<String> list : inputTypes.values()) {
			Collections.sort(list);
		}

		// Create two list models to store the data for the JLists
		DefaultListModel<String> outputListModel = new DefaultListModel<>();
		DefaultListModel<String> inputListModel = new DefaultListModel<>();

		// Add the types and their counts to the list models
		for (Map.Entry<String, List<String>> entry : outputTypes.entrySet()) {
			outputListModel.addElement(entry.getKey() + " (" + entry.getValue().size() + ")");
		}
		for (Map.Entry<String, List<String>> entry : inputTypes.entrySet()) {
			inputListModel.addElement(entry.getKey() + " (" + entry.getValue().size() + ")");
		}

		// comparator that sorts entries alphabetically by their type name
		// (the part before the opening parenthesis), while keeping the special
		// "NO OUTPUT" and "NO INPUT" categories pinned to the very top of their
		// respective lists so they are immediately visible.
		Comparator<String> alphabeticalComparator = new Comparator<String>() {
			@Override
			public int compare(String s1, String s2) {
				// Extract the type name (everything before the last " (")
				String name1 = extractName(s1);
				String name2 = extractName(s2);

				// Pin the special NO-type categories to the top
				boolean isSpecial1 = name1.equals(NO_OUTPUT_CATEGORY) || name1.equals(NO_INPUT_CATEGORY);
				boolean isSpecial2 = name2.equals(NO_OUTPUT_CATEGORY) || name2.equals(NO_INPUT_CATEGORY);
				if (isSpecial1 && !isSpecial2) {
					return -1;
				}
				if (!isSpecial1 && isSpecial2) {
					return 1;
				}

				// Otherwise sort alphabetically (case-insensitive)
				return name1.compareToIgnoreCase(name2);
			}

			/** Extract the display name from a list entry of the form "TypeName (count)". */
			private String extractName(String entry) {
				int lastParen = entry.lastIndexOf(" (");
				return lastParen >= 0 ? entry.substring(0, lastParen) : entry;
			}
		};

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

		// Sort the lists using the alphabetical comparator
		Collections.sort(outputListData, alphabeticalComparator);
		Collections.sort(inputListData, alphabeticalComparator);

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

		// build the sorted table list of input/output types used for the
		// "Full Mapping" tab (same pinning rule as the list view: special
		// categories first, then alphabetical order)
		List<String> inputTypeKeys = new ArrayList<>(inputTypes.keySet());
		List<String> outputTypeKeys = new ArrayList<>(outputTypes.keySet());
		Collections.sort(inputTypeKeys, buildTypeKeyComparator());
		Collections.sort(outputTypeKeys, buildTypeKeyComparator());

		// build the "Full Mapping (Table View)" panel with two sortable
		// tables, one for input types and one for output types
		JPanel inputTablePanel = createTableViewPanel(inputTypeKeys, inputTypes,
				"Input Type", "Input Types -> Algorithms (Full Mapping)", "input_types_mapping.txt");
		JPanel outputTablePanel = createTableViewPanel(outputTypeKeys, outputTypes,
				"Output Type", "Output Types -> Algorithms (Full Mapping)", "output_types_mapping.txt");

		JSplitPane tableSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputTablePanel, outputTablePanel);
		tableSplitPane.setResizeWeight(0.5);
		tableSplitPane.setOneTouchExpandable(true);
		tableSplitPane.setContinuousLayout(true);

		// wrap the browsing UI (split pane) into a tabbed pane. The
		// "Full Mapping (Table View)" tab 
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.addTab("Browse", splitPane);

		// Add the tabbed pane to the frame
		frame.add(tabbedPane, BorderLayout.CENTER);

		// register a key binding on the frame's root pane so
		// that pressing F12 anywhere in the window toggles the visibility of the
		// hidden "Full Mapping (Table View)" tab. Since it uses
		// WHEN_IN_FOCUSED_WINDOW, it works regardless of which component currently
		// has the focus.
		frame.getRootPane().registerKeyboardAction(e -> {
			int index = tabbedPane.indexOfTab(TABLE_VIEW_TAB_TITLE);
			if (index == -1) {
				// The tab is currently hidden: reveal it and select it
				tabbedPane.addTab(TABLE_VIEW_TAB_TITLE, tableSplitPane);
				tabbedPane.setSelectedIndex(tabbedPane.indexOfTab(TABLE_VIEW_TAB_TITLE));
			} else {
				// The tab is currently visible: hide it again
				tabbedPane.removeTabAt(index);
			}
		}, KeyStroke.getKeyStroke("F12"), JComponent.WHEN_IN_FOCUSED_WINDOW);

		// Set the window in the center of the screen
		frame.setLocationRelativeTo(null);

		// Make the frame visible
		frame.setVisible(true);

		// Set the divider location to 50%
		splitPane.setDividerLocation(0.5);
		tableSplitPane.setDividerLocation(0.5);

	}

	/**
	 * NEW METHOD
	 * Build a comparator on raw type names (not on "name (count)" strings) that
	 * pins the special "NO INPUT" / "NO OUTPUT" categories to the top and
	 * otherwise sorts alphabetically (case-insensitive). Used to order the rows
	 * of the "Full Mapping" tables the same way as the list view.
	 * 
	 * @return the comparator
	 */
	private static Comparator<String> buildTypeKeyComparator() {
		return new Comparator<String>() {
			@Override
			public int compare(String name1, String name2) {
				boolean isSpecial1 = name1.equals(NO_OUTPUT_CATEGORY) || name1.equals(NO_INPUT_CATEGORY);
				boolean isSpecial2 = name2.equals(NO_OUTPUT_CATEGORY) || name2.equals(NO_INPUT_CATEGORY);
				if (isSpecial1 && !isSpecial2) {
					return -1;
				}
				if (!isSpecial1 && isSpecial2) {
					return 1;
				}
				return name1.compareToIgnoreCase(name2);
			}
		};
	}

	/**
	 * NEW METHOD
	 * Build a panel showing the complete mapping from types to algorithms in a
	 * sortable {@link SortableJTable}. The table has three columns: the type
	 * name, the number of algorithms associated with it, and the (comma
	 * separated) list of algorithm names.
	 * 
	 * @param typesList          the sorted list of types
	 * @param typesToAlgos       a map associating each type to the list of
	 *                           algorithms using it
	 * @param typeColumnName     the label to use for the "type" column (e.g.
	 *                           "Input Type" or "Output Type")
	 * @param titleText          the title to display above the table
	 * @param defaultFileName    the default file name proposed when saving the
	 *                           mapping to a file
	 * @return a panel containing the table wrapped in a scroll pane, with a
	 *         title label above it and copy/save buttons below it
	 */
	private static JPanel createTableViewPanel(List<String> typesList, Map<String, List<String>> typesToAlgos,
			String typeColumnName, String titleText, String defaultFileName) {

		// Build the table model: one row per type
		DefaultTableModel model = new DefaultTableModel(
				new Object[] { typeColumnName, "# Algorithms", "Algorithms" }, 0) {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
			@Override
			public Class<?> getColumnClass(int columnIndex) {
				return columnIndex == 1 ? Integer.class : String.class;
			}
		};

		for (String type : typesList) {
			List<String> algos = typesToAlgos.get(type);
			String algosJoined = String.join(", ", algos);
			model.addRow(new Object[] { type, algos.size(), algosJoined });
		}

		// use a SortableJTable so the user can sort the full mapping by any
		// column (type name, number of algorithms, or algorithm list)
		SortableJTable table = new SortableJTable();
		table.setModel(model);
		table.getColumnModel().getColumn(0).setPreferredWidth(150);
		table.getColumnModel().getColumn(1).setPreferredWidth(80);
		table.getColumnModel().getColumn(2).setPreferredWidth(400);
		table.setRowHeight(22);
		table.setFillsViewportHeight(true);

		JPanel panel = new JPanel(new BorderLayout());
		JLabel titleLabel = new JLabel(titleText, SwingConstants.CENTER);
		panel.add(titleLabel, BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);

		// Buttons to copy/save the full table content as text
		JButton copyTableButton = new JButton("Copy Table as Text");
		copyTableButton.addActionListener(e -> {
			String report = buildFullReport(typesList, typesToAlgos, typeColumnName);
			copyToClipboard(report);
			JOptionPane.showMessageDialog(panel, "The table content has been copied to the clipboard.");
		});
		JButton saveTableButton = new JButton("Save Table to File");
		saveTableButton.addActionListener(e -> {
			String report = buildFullReport(typesList, typesToAlgos, typeColumnName);
			saveReportToFile(null, report, defaultFileName);
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		buttonPanel.add(copyTableButton);
		buttonPanel.add(saveTableButton);
		panel.add(buttonPanel, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * NEW METHOD
	 * Build a textual report listing, for each type, the number and names of the
	 * algorithms associated with it.
	 * 
	 * @param typesList      the sorted list of types
	 * @param typesToAlgos   a map associating each type to the list of algorithms
	 *                       using it
	 * @param typeColumnName the label describing the kind of type (e.g. "Input
	 *                       Type" or "Output Type"), used in the report header
	 * @return the report as a single string
	 */
	private static String buildFullReport(List<String> typesList, Map<String, List<String>> typesToAlgos,
			String typeColumnName) {
		StringBuilder sb = new StringBuilder();
		sb.append("SPMF - List of algorithms by ").append(typeColumnName.toLowerCase())
				.append(System.lineSeparator());
		sb.append("========================================").append(System.lineSeparator());
		sb.append(System.lineSeparator());
		for (String type : typesList) {
			List<String> algos = typesToAlgos.get(type);
			sb.append(type).append("  (").append(algos.size()).append(" algorithm(s))")
					.append(System.lineSeparator());
			sb.append("----------------------------------------").append(System.lineSeparator());
			for (String algo : algos) {
				sb.append("   - ").append(algo).append(System.lineSeparator());
			}
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}

	/**
	 * NEW METHOD
	 * Save the given report to a text file chosen by the user via a file
	 * chooser dialog.
	 * 
	 * @param parent          the parent frame (may be null)
	 * @param report          the report to save
	 * @param defaultFileName the default file name to propose
	 */
	private static void saveReportToFile(JFrame parent, String report, String defaultFileName) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setSelectedFile(new File(defaultFileName));
		int result = fileChooser.showSaveDialog(parent);
		if (result == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
				writer.write(report);
				JOptionPane.showMessageDialog(parent, "The file has been saved successfully.");
			} catch (Exception e) {
				JOptionPane.showMessageDialog(parent, "An error occured while saving the file: " + e.getMessage(),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 * NEW METHOD
	 * Copy the given text to the system clipboard.
	 * 
	 * @param text the text to copy
	 */
	private static void copyToClipboard(String text) {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(new StringSelection(text), null);
	}

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("unused")
		InputOutputTypeListWindow viewer = new InputOutputTypeListWindow(true);
	}
}