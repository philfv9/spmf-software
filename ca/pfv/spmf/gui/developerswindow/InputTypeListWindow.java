package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
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

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
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
 * A class for developers that display the list of input types
 * for algorithms in SPMF and obtain more informaton about them.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class InputTypeListWindow {

	/**
	 * name of the special category used to group algorithms that do not
	 * have any input type (i.e. getInputFileTypes() returns null).
	 */
	private static final String NO_INPUT_CATEGORY = "NO INPUT";

	/**
	 * title of "Full Mapping (Table View)" tab, used to
	 * identify it when toggling its visibility with F12.
	 */
	private static final String TABLE_VIEW_TAB_TITLE = "Full Mapping (Table View)";

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
		frame.setSize(900, 650);
		frame.setMinimumSize(new java.awt.Dimension(650, 450));

		// Create a map to store the input types and the algorithms that have them
		Map<String, List<String>> inputTypes = new HashMap<>();

		// counter for the total number of algorithms that have input type information
		int totalAlgorithmsWithType = 0;

		// Loop through the list of algorithms and add them to the map
		for (String algorithmName : algorithms) {
			if (algorithmName.contains("---")) {
				continue;
			}
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			String[] inputTypesOfAlgorithm = algorithm.getInputFileTypes();

			// determine the "main" input type of this algorithm. If the
			// algorithm does not have any input type (null), it is placed under
			// a special category "NO INPUT" instead of being skipped, so that
			// developers can easily find algorithms without any input.
			String mainType;
			if (inputTypesOfAlgorithm != null) {
				mainType = inputTypesOfAlgorithm[inputTypesOfAlgorithm.length - 1];
			} else {
				mainType = NO_INPUT_CATEGORY;
			}

//				for (String type : inputTypesOfAlgorithm) {
			// If the type is not in the map, create a new list for it
			List<String> list = inputTypes.get(mainType);
			if (list == null) {
				list = new ArrayList<String>();
				inputTypes.put(mainType, list);
			}
			// Add the algorithm name to the list of the type
			list.add(algorithmName);
			totalAlgorithmsWithType++;
//				}
		}
		
		// sort the algorithms within each input type alphabetically so that
		// they are easier to read/browse both on screen and in exported reports
		for (List<String> list : inputTypes.values()) {
			Collections.sort(list);
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
		// the renderer now also receives the map so it can display algorithm counts
		inputList.setCellRenderer(new InputTypeListCellRenderer(inputTypes));


		// Create a text area to display the algorithms for the selected input type
		JTextArea algorithmTextArea = new JTextArea();
		algorithmTextArea.setEditable(false);
		// use a monospaced font for better readability and easier copy/paste
		algorithmTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

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
					algorithmTextArea.setCaretPosition(0);
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

		// search field to quickly filter the list of input types
		JTextField searchField = new JTextField();
		searchField.setToolTipText("Type here to filter the list of input types");
		// label showing how many input types match the current filter
		JLabel matchCountLabel = new JLabel("", SwingConstants.CENTER);
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			private void filter() {
				String text = searchField.getText().trim().toLowerCase();
				inputListModel.clear();
				int count = 0;
				for (String type : inputTypesList) {
					if (text.isEmpty() || type.toLowerCase().contains(text)) {
						inputListModel.addElement(type);
						count++;
					}
				}
				matchCountLabel.setText(text.isEmpty() ? "" : count + " matching type(s)");
			}
			@Override
			public void insertUpdate(DocumentEvent e) { filter(); }
			@Override
			public void removeUpdate(DocumentEvent e) { filter(); }
			@Override
			public void changedUpdate(DocumentEvent e) { filter(); }
		});

		// Create a panel to hold the label and the scroll pane for the input types
		JPanel inputPanel = new JPanel(new BorderLayout());

		// panel holding the label, the search field and the match count label
		JPanel inputTopPanel = new JPanel(new BorderLayout());
		inputTopPanel.add(inputLabel, BorderLayout.NORTH);
		inputTopPanel.add(searchField, BorderLayout.CENTER);
		inputTopPanel.add(matchCountLabel, BorderLayout.SOUTH);

		// Create a panel to hold the label and the scroll pane for the algorithms
		JPanel algorithmPanel = new JPanel(new BorderLayout());

		// Add the labels and the scroll panes to the panels
		inputPanel.add(inputTopPanel, BorderLayout.NORTH);
		inputPanel.add(inputScrollPane, BorderLayout.CENTER);
		algorithmPanel.add(algorithmLabel, BorderLayout.NORTH);
		algorithmPanel.add(algorithmScrollPane, BorderLayout.CENTER);

		// button to copy the algorithms currently shown on the right to the clipboard
		JButton copySelectedButton = new JButton("Copy this list");
		copySelectedButton.setToolTipText("Copy the list of algorithms shown on the right to the clipboard");
		copySelectedButton.addActionListener(e -> {
			if (algorithmTextArea.getText().isEmpty()) {
				JOptionPane.showMessageDialog(frame, "Please select an input type first.");
				return;
			}
			copyToClipboard(algorithmTextArea.getText());
			JOptionPane.showMessageDialog(frame, "The list has been copied to the clipboard.");
		});
		JPanel algorithmButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		algorithmButtonPanel.add(copySelectedButton);
		algorithmPanel.add(algorithmButtonPanel, BorderLayout.SOUTH);
		

		// Create a split pane to hold the two panels
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, algorithmPanel);

		// build the "Full Mapping (Table View)" panel showing the complete
		// mapping from input types to algorithms using a sortable SortableJTable.
		JPanel tableViewPanel = createTableViewPanel(inputTypesList, inputTypes,
				"Input Type", "Input Types -> Algorithms (Full Mapping)");

		// wrap the browsing UI (split pane) into a tabbed pane.
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.addTab("Browse", splitPane);

		// Add the tabbed pane to the frame
		frame.add(tabbedPane, BorderLayout.CENTER);

		//  register a key binding on the frame's root pane so
		// that pressing F12 anywhere in the window toggles the visibility of the
		// hidden "Full Mapping (Table View)" tab. Since it uses
		// WHEN_IN_FOCUSED_WINDOW, it works regardless of which component currently
		// has the focus.
		frame.getRootPane().registerKeyboardAction(e -> {
			int index = tabbedPane.indexOfTab(TABLE_VIEW_TAB_TITLE);
			if (index == -1) {
				// The tab is currently hidden: reveal it and select it
				tabbedPane.addTab(TABLE_VIEW_TAB_TITLE, tableViewPanel);
				tabbedPane.setSelectedIndex(tabbedPane.indexOfTab(TABLE_VIEW_TAB_TITLE));
			} else {
				// The tab is currently visible: hide it again
				tabbedPane.removeTabAt(index);
			}
		}, KeyStroke.getKeyStroke("F12"), JComponent.WHEN_IN_FOCUSED_WINDOW);

		// toolbar with a button to display (and copy/save) the full report of
		// all input types and their algorithms in a single text area
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);

		JButton showAllButton = new JButton("Show All As Text");
		showAllButton.setMnemonic('S');
		showAllButton.setToolTipText("Open a window showing the full list of input types and algorithms "
				+ "in a text area that can be selected and copied");
		showAllButton.addActionListener(e -> {
			String report = buildFullReport(inputTypesList, inputTypes);
			showTextReportWindow(frame, report);
		});
		toolBar.add(showAllButton);

		JButton saveAllButton = new JButton("Save All To File");
		saveAllButton.setMnemonic('F');
		saveAllButton.setToolTipText("Save the full list of input types and algorithms to a text file");
		saveAllButton.addActionListener(e -> {
			String report = buildFullReport(inputTypesList, inputTypes);
			saveReportToFile(frame, report);
		});
		toolBar.add(saveAllButton);

		frame.add(toolBar, BorderLayout.NORTH);
		
		// south panel now contains both the color legend (with actual colors
		// shown using HTML) and the total number of algorithms found
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));

		JLabel explanationLabel = new JLabel(
				"<html><div style='text-align:center;'>"
				+ "<span style='color:green;'><b>Green</b></span> = has a viewer tool &nbsp;&nbsp;&nbsp; "
				+ "<span style='color:red;'><b>Red</b></span> = has no viewer tool &nbsp;&nbsp;&nbsp; "
				+ "<span style='color:gray;'><b>Gray</b></span> = " + NO_INPUT_CATEGORY + " category"
				+ "</div></html>", SwingConstants.CENTER);

		JLabel totalLabel = new JLabel("Total algorithms with input type information: " + totalAlgorithmsWithType,
				SwingConstants.CENTER);

		southPanel.add(explanationLabel);
		southPanel.add(totalLabel);
		frame.add(southPanel, BorderLayout.SOUTH);

		// Set the window in the center of the screen
		frame.setLocationRelativeTo(null);

		// Make the frame visible
		frame.setVisible(true);

		// Set the divider location to 50%
		splitPane.setDividerLocation(0.4);

	}

	/**
	 * Build a panel showing the complete mapping from types to algorithms in a
	 * sortable {@link SortableJTable}. The table has five columns: the type
	 * name, the number of algorithms associated with it, the (comma
	 * separated) list of algorithm names, the (comma separated) list of
	 * documentation page names, and the (comma separated) list of
	 * implementation class names ({@link DescriptionOfAlgorithm} subclasses).
	 * 
	 * @param typesList       the sorted list of types
	 * @param typesToAlgos    a map associating each type to the list of
	 *                        algorithms using it
	 * @param typeColumnName  the label to use for the "type" column (e.g.
	 *                        "Input Type" or "Output Type")
	 * @param titleText       the title to display above the table
	 * @return a panel containing the table wrapped in a scroll pane, with a
	 *         title label above it and copy/save buttons below it
	 */
	private static JPanel createTableViewPanel(List<String> typesList, Map<String, List<String>> typesToAlgos,
			String typeColumnName, String titleText) {

		// Build the table model: one row per type
		DefaultTableModel model = new DefaultTableModel(
				new Object[] { typeColumnName, "# Algorithms", "Algorithms", "Documentation Pages",
						"Implementation Class" }, 0) {
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
			String docPagesJoined = String.join(", ", getDocumentationPagesForAlgorithms(algos));
			String classNamesJoined = String.join(", ", getImplementationClassNamesForAlgorithms(algos));
			model.addRow(new Object[] { type, algos.size(), algosJoined, docPagesJoined, classNamesJoined });
		}

		// use a SortableJTable so the user can sort the full mapping by any
		// column (type name, number of algorithms, algorithm list,
		// documentation pages, or implementation class)
		SortableJTable table = new SortableJTable(model);
		table.getColumnModel().getColumn(0).setPreferredWidth(150);
		table.getColumnModel().getColumn(1).setPreferredWidth(70);
		table.getColumnModel().getColumn(2).setPreferredWidth(350);
		table.getColumnModel().getColumn(3).setPreferredWidth(250);
		table.getColumnModel().getColumn(4).setPreferredWidth(250);
		table.setRowHeight(22);

		JPanel panel = new JPanel(new BorderLayout());
		JLabel titleLabel = new JLabel(titleText, SwingConstants.CENTER);
		panel.add(titleLabel, BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);

		// Buttons to copy/save the full table content as text (reusing the
		// same report format as the "Show All As Text" / "Save All To File"
		// toolbar buttons)
		JButton copyTableButton = new JButton("Copy Table as Text");
		copyTableButton.addActionListener(e -> {
			String report = buildFullReport(typesList, typesToAlgos);
			copyToClipboard(report);
			JOptionPane.showMessageDialog(panel, "The table content has been copied to the clipboard.");
		});
		JButton saveTableButton = new JButton("Save Table to File");
		saveTableButton.addActionListener(e -> {
			String report = buildFullReport(typesList, typesToAlgos);
			saveReportToFile(null, report);
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		buttonPanel.add(copyTableButton);
		buttonPanel.add(saveTableButton);
		panel.add(buttonPanel, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Given a list of algorithm names, return the list of documentation page
	 * names (in the same order) obtained by calling
	 * {@link DescriptionOfAlgorithm#getURLOfDocumentation()} on each algorithm
	 * and keeping only the webpage name rather than the full URL.
	 * 
	 * @param algorithmNames the list of algorithm names
	 * @return the list of documentation page names
	 */
	private static List<String> getDocumentationPagesForAlgorithms(List<String> algorithmNames) {
		List<String> pages = new ArrayList<>();
		for (String algorithmName : algorithmNames) {
			try {
				DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
				String url = algorithm.getURLOfDocumentation();
				pages.add(extractWebPageName(url));
			} catch (Exception e) {
				pages.add("");
			}
		}
		return pages;
	}

	/**
	 * Given a list of algorithm names, return the list of implementation
	 * class (simple) names, i.e. the {@link DescriptionOfAlgorithm} subclass
	 * describing each algorithm.
	 * 
	 * @param algorithmNames the list of algorithm names
	 * @return the list of implementation class (simple) names
	 */
	private static List<String> getImplementationClassNamesForAlgorithms(List<String> algorithmNames) {
		List<String> classNames = new ArrayList<>();
		for (String algorithmName : algorithmNames) {
			try {
				DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
				classNames.add(algorithm.getClass().getSimpleName());
			} catch (Exception e) {
				classNames.add("");
			}
		}
		return classNames;
	}

	/**
	 * Extract the webpage name (the last segment of a URL) from a full URL.
	 * 
	 * @param url the full URL, may be null
	 * @return the webpage name, or an empty string if the URL is null/empty
	 */
	private static String extractWebPageName(String url) {
		if (url == null || url.trim().isEmpty()) {
			return "";
		}
		String trimmed = url.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		int idx = trimmed.lastIndexOf('/');
		return idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
	}

	/**
	 * Build a textual report listing, for each input type, the number and names
	 * of the algorithms that use it, along with their documentation page and
	 * implementation class. This report can then be displayed in a
	 * text area (so the user can select/copy it) or saved to a file.
	 * 
	 * @param inputTypesList the sorted list of input types
	 * @param inputTypes     a map associating each input type to the list of
	 *                       algorithms using it
	 * @return the report as a single string
	 */
	private static String buildFullReport(List<String> inputTypesList, Map<String, List<String>> inputTypes) {
		StringBuilder sb = new StringBuilder();
		sb.append("SPMF - List of algorithms by input type").append(System.lineSeparator());
		sb.append("========================================").append(System.lineSeparator());
		sb.append(System.lineSeparator());
		for (String type : inputTypesList) {
			List<String> algos = inputTypes.get(type);
			sb.append(type).append("  (").append(algos.size()).append(" algorithm(s))").append(System.lineSeparator());
			sb.append("----------------------------------------").append(System.lineSeparator());
			for (String algo : algos) {
				String docPage = getDocumentationPagesForAlgorithms(Collections.singletonList(algo)).get(0);
				String className = getImplementationClassNamesForAlgorithms(Collections.singletonList(algo)).get(0);
				sb.append("   - ").append(algo);
				if (!docPage.isEmpty()) {
					sb.append("   [doc: ").append(docPage).append("]");
				}
				if (!className.isEmpty()) {
					sb.append("   [class: ").append(className).append("]");
				}
				sb.append(System.lineSeparator());
			}
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}

	/**
	 * NEW METHOD
	 * Display a non-modal window containing a text area with the given report.
	 * The user can select the text and copy it manually, or use the provided
	 * "Copy to Clipboard" button.
	 * 
	 * @param parent the parent frame (used to center the new window)
	 * @param report the report to be displayed
	 */
	private static void showTextReportWindow(JFrame parent, String report) {
		JDialog dialog = new JDialog(parent, "SPMF - Full list of algorithms by input type", false);
		dialog.setSize(700, 600);

		JTextArea textArea = new JTextArea(report);
		textArea.setEditable(false);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		textArea.setCaretPosition(0);
		// Select all the text automatically to make it easy to copy right away
		textArea.selectAll();

		JScrollPane scrollPane = new JScrollPane(textArea);
		dialog.add(scrollPane, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton copyButton = new JButton("Copy to Clipboard");
		copyButton.addActionListener(e -> {
			copyToClipboard(textArea.getText());
			JOptionPane.showMessageDialog(dialog, "The text has been copied to the clipboard.");
		});
		JButton saveButton = new JButton("Save To File");
		saveButton.addActionListener(e -> saveReportToFile(parent, textArea.getText()));

		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dialog.dispose());

		buttonPanel.add(copyButton);
		buttonPanel.add(saveButton);
		buttonPanel.add(closeButton);
		dialog.add(buttonPanel, BorderLayout.SOUTH);

		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	/**
	 * NEW METHOD
	 * Save the given report to a text file chosen by the user via a file
	 * chooser dialog.
	 * 
	 * @param parent the parent frame (may be null)
	 * @param report the report to save
	 */
	private static void saveReportToFile(JFrame parent, String report) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setSelectedFile(new File("spmf_algorithms_by_input_type.txt"));
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
		InputTypeListWindow viewer = new InputTypeListWindow(true);
	}
	
	// Create a custom ListCellRenderer class that extends DefaultListCellRenderer
	private static class InputTypeListCellRenderer extends DefaultListCellRenderer {
	    /**
		 * Serial UID
		 */
		private static final long serialVersionUID = -5826159937196573338L;

		// keep a reference to the map so that the number of algorithms for
		// each input type can be displayed next to its name
		private final Map<String, List<String>> inputTypes;

		public InputTypeListCellRenderer(Map<String, List<String>> inputTypes) {
			this.inputTypes = inputTypes;
		}

		@Override
	    public Component getListCellRendererComponent(@SuppressWarnings("rawtypes") JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
	        // Call the super method to get the default component
	        Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
	        // Cast the value to a String
	        String inputType = (String) value;

	        // append the number of algorithms that use this input type
	        List<String> algorithmsForType = inputTypes.get(inputType);
	        if (algorithmsForType != null && c instanceof JLabel) {
	        	((JLabel) c).setText(inputType + "   (" + algorithmsForType.size() + ")");
	        }

	        // the "NO INPUT" category is a synthetic category (it is not an
	        // actual input type known by the AlgorithmManager), so it cannot be
	        // used to look up a viewer. In that case, use a neutral gray color
	        // instead of the usual green/red viewer indicator.
	        if (NO_INPUT_CATEGORY.equals(inputType)) {
	        	c.setBackground(isSelected ? Color.GRAY.darker() : Color.LIGHT_GRAY);
	        } else {
		        // Check if the input type has a viewer
		        try {
					if (AlgorithmManager.getInstance().getViewerFor(new String[] {inputType}) == null) {
					    // If not, set the background color to red
					    c.setBackground(isSelected ? Color.RED.darker() : Color.RED);
					} else {
					    // If yes, set the background color to green
					    c.setBackground(isSelected ? Color.GREEN.darker() : Color.GREEN);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	        // Return the modified component
	        return c;
	    }
	}
}