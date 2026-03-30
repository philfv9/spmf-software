package ca.pfv.spmf.gui.patternvizualizer;

import java.awt.BorderLayout;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableRowSorter;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.patternvizualizer.filters.AbstractFilter;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionWindow;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

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
 * This is a simple user interface to vizualize patterns found by algorithms in
 * SPMF.
 * 
 * @author Philippe Fournier-Viger
 */
public class PatternVizualizer extends JFrame {

	/** Generated serial ID */
	private static final long serialVersionUID = -2012129335077139428L;

	/** The table for showing the patterns to the user */
	JTable table;
	/** The label indicating the number of patterns currently shown in the Jtable */
	private JLabel labelNumberOfPatterns;

	/** Variables for storing the data from the TableModel used in the Jtable */
	Vector<List<Object>> data = null;
	/** List of table column names */
	Vector<String> columnNames = null;
	/** List of table column classes (Integer, Double, String) */
	@SuppressWarnings("rawtypes")
	Vector<Class> columnClasses = null;

	/** The JList showing the current filters that are applied on the Jtable */
	private JList<String> listFilters;
	/** The list model for the JList showing the filters */
	private DefaultListModel<String> listModelFilters;

	/** The "Remove selected filter" button */
	private JButton btnRemoveFilter;
	/** The "Remove all filters" button */
	private JButton btnRemoveAllFilters;

	/** The list of current filters, used for filtering the JTable **/
	PatternTableRowFilters rowFilters = new PatternTableRowFilters();

	/** The TableRowSorter used by the JTable */
	private TableRowSorter<PatternTableModel> sorter;

	/** The TableModel used by the JTable */
	private PatternTableModel model;
	private JTextField textFieldSearch;

	private JComboBox<String> comboBoxExport;

	/**
	 * Method to initialize the windows for vizualizing patterns, and diplay
	 * patterns from a file in SPMF format.
	 * 
	 * @param patternFilePath the path to a file containing patterns, in SPMF
	 *                        format.
	 * @throws IOException if error while reading file
	 */
	public PatternVizualizer(String patternFilePath) throws ParseException, IOException {
		// set the size of the window, and make it non-resizeable
		setSize(800, 600);
		setLocationRelativeTo(null);
//        setResizable(false);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		// get the file name
		File file = new File(patternFilePath);
		String fileName = file.getName();

		// get the file last modification date
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd, HH:mm");
		String modificationDate = sdf.format(new Date(file.lastModified()));

		// set the window title and layout
		setTitle("SPMF Pattern Viewer" + Main.SPMF_VERSION);
		getContentPane().setLayout(new BorderLayout());
		
		Image icon = new ImageIcon(
			    getClass().getResource("/ca/pfv/spmf/gui/icons/viewdata24.png")
			).getImage();

			setIconImage(icon);

		// ************************** NORTH panel **************************
		JLabel lblPatterns = new JLabel(" Patterns:");
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new GridBagLayout());
		northPanel.add(lblPatterns);
		
		// Add a filler component with weighty = 1.0 at the end to push everything up
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.weightx = 1.0;
		northPanel.add(Box.createHorizontalGlue(), gbc);
		
		
		getContentPane().add(northPanel, BorderLayout.NORTH);

		// ************************** CENTER panel **************************
		JScrollPane scrollPane = new JScrollPane();
		table = new JTable();
		// let the user sort the columns in the table by clicking on the column headers
		table.setAutoCreateRowSorter(true);
		table.setCellSelectionEnabled(true);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		scrollPane.setViewportView(table);
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		// set the horizontal and vertical scrollbars for the table
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		// ************************** SOUTH panel **************************
		JPanel southPanel = new JPanel(new GridBagLayout());

		// First row
		gbc.gridx = 1; // column
		gbc.gridy = 0; // row
		labelNumberOfPatterns = new JLabel("Number of Patterns: "); // Assuming you will set text later
		southPanel.add(labelNumberOfPatterns, gbc);

		gbc.gridx = 0; // column
		gbc.gridy = 0; // row
		JLabel lblFileName = new JLabel("File name: " + fileName);
		southPanel.add(lblFileName, gbc);

		// Second row
		gbc.gridx = 2; // column
		gbc.gridy = 0; // row
		double size = file.length() / 1024d / 1024d;
		String fileSize = String.format("%.4f", size);
		JLabel lblFileSizemb = new JLabel("File size (MB): " + fileSize);
		southPanel.add(lblFileSizemb, gbc);

		gbc.gridx = 3; // column
		gbc.gridy = 0; // row
		JLabel lblLastModified = new JLabel("Last modified: " + modificationDate);
		southPanel.add(lblLastModified, gbc);

		// Add a filler component with weighty = 1.0 at the end to push everything up
		gbc.weightx = 1.0;
		southPanel.add(Box.createHorizontalGlue(), gbc);

		getContentPane().add(southPanel, BorderLayout.SOUTH);

		// ****** read the file containing patterns to fill the JTable *********
		readFile(patternFilePath);

		// Set the window as modal
		setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);

		// ****************************** EAST panel **************************

		JPanel eastPanel = new JPanel(new GridBagLayout());
		gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(5, 5, 5, 5);

		// Search row
		gbc.gridx = 0; // column
		gbc.gridy = 0; // row
		JLabel lblSearch = new JLabel("Search:");
		eastPanel.add(lblSearch, gbc);

		gbc.gridy++; // move to next row
		gbc.gridx = 0; // column
		textFieldSearch = new JTextField(10);
		eastPanel.add(textFieldSearch, gbc);

		gbc.gridx = 1; // column
		JButton btnSearch = new JButton("Search",
				new ImageIcon(PatternVizualizer.class.getResource("/ca/pfv/spmf/gui/patternvizualizer/find.gif")));
		eastPanel.add(btnSearch, gbc);

		// Add some space
		gbc.gridy++; // move to next row
		gbc.gridx = 0; // reset to first column
		gbc.gridwidth = 3; // span across three columns
		eastPanel.add(Box.createVerticalStrut(20), gbc);

		// Apply filters row
		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 1; // reset to one column
		JLabel lblApplyFilters = new JLabel("Apply filter(s):");
		eastPanel.add(lblApplyFilters, gbc);

		gbc.gridy++; // move to next row
		gbc.gridx = 0;
		gbc.gridwidth = 2; // span across two columns for listScrollPane
//		gbc.weighty = 1.0; // give weight to expand vertically
		listModelFilters = new DefaultListModel<>();
		listFilters = new JList<>(listModelFilters);
		JScrollPane listScrollPane = new JScrollPane(listFilters);
		listScrollPane.setPreferredSize(new Dimension(250, 200));
		eastPanel.add(listScrollPane, gbc);

		// reset weighty for subsequent components
		gbc.weighty = 0;

		// Buttons row
		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 1; // reset to one column
		JButton btnAddAFilter = new JButton("Add");
		eastPanel.add(btnAddAFilter, gbc);

//		gbc.gridy++; // move to next row
		gbc.gridx = 1;
		btnRemoveFilter = new JButton("Remove");
		btnRemoveFilter.setEnabled(false);
		eastPanel.add(btnRemoveFilter, gbc);

		gbc.gridy++; // move to next row
		gbc.gridx = 0;
		btnRemoveAllFilters = new JButton("Remove all");
		btnRemoveAllFilters.setEnabled(false);
		eastPanel.add(btnRemoveAllFilters, gbc);
		
		// Add some space
		gbc.gridy++; // move to next row
		gbc.gridx = 0; // reset to first column
		gbc.gridwidth = 3; // span across three columns
		eastPanel.add(Box.createVerticalStrut(20), gbc);
		gbc.gridwidth = 1; // span across three columns

		// Export row
		gbc.gridy +=1; // skip a row for space
		gbc.gridx = 0;
		JLabel lblExportTo = new JLabel("Export current view to:");
		eastPanel.add(lblExportTo, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		comboBoxExport = new JComboBox<>();
		comboBoxExport.addItem("SPMF format");
		comboBoxExport.addItem("TSV format");
		comboBoxExport.addItem("CSV format");
		eastPanel.add(comboBoxExport, gbc);

		gbc.gridx = 1;
		JButton buttonExport = new JButton("Export",
				new ImageIcon(PatternVizualizer.class.getResource("/ca/pfv/spmf/gui/patternvizualizer/save.gif")));
		eastPanel.add(buttonExport, gbc);

		//===============================================================================================
		// View distribution row
		gbc.gridy += 2; // skip a row for space
		gbc.gridx = 0;
		JLabel labelView = new JLabel("View distribution (no filter):");
		eastPanel.add(labelView, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		JComboBox<String> comboBoxMeasures = new JComboBox<>();
		int i = 1;
		for (; i < columnNames.size(); i++) {
			String value = columnNames.get(i);
			if (columnClasses.get(i).equals(Boolean.class) == false) {
				comboBoxMeasures.addItem(value);
			}
		}
		eastPanel.add(comboBoxMeasures, gbc);

		gbc.gridx = 1;
		JButton buttonView = new JButton("View",
				new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/histogram.png")));
		eastPanel.add(buttonView, gbc);

		// Add a filler component with weighty = 1.0 at the end to push everything up
		gbc.gridy++;
		gbc.weighty = 1.0;
		eastPanel.add(Box.createVerticalGlue(), gbc);

		// Add the eastPanel to the main content pane
		getContentPane().add(eastPanel, BorderLayout.EAST);

		// ************************* EVENT HANDLING **************************
		btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				search();
			}
		});
		listFilters.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent arg0) {
				selectFilter(arg0);
			}
		});
		btnAddAFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				// When the user clicks on the button,
				// a window is created for letting the user create the filter
				FilterSelectionWindow window = new FilterSelectionWindow(columnNames, columnClasses, rowFilters,
						PatternVizualizer.this);
			}
		});
		btnRemoveFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				removeSelectedFilter();
			}
		});
		btnRemoveAllFilters.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				removeAllFilters();
			}
		});
		buttonExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				export();
			}
		});
		
		//=======================================================
		buttonView.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String measure = (String) comboBoxMeasures.getSelectedItem();
				// find the column id:
				int i = 1;
				for (; i < columnNames.size(); i++) {
					if (columnNames.get(i).equals(measure)) {
						break;
					}
				}
//			data
//			PatternDistributionWindow frame = new PatternDistributionWindow(patternFilePath, measure);
//			FrequencyDistributionWindow frame = new FrequencyDistributionWindow(false, data, i, measure);
				HistogramDistributionWindow frame2 = new HistogramDistributionWindow(false, data, i,
						"Frequency distribution of patterns", measure, "Count", Order.ASCENDING_Y);
			}
		});
		//=======================================================

		getContentPane().add(eastPanel, BorderLayout.EAST);

		validate();
		pack();
		repaint();
		
		// size of the window
		this.setMinimumSize(getSize());
//		this.addComponentListener(new ComponentAdapter() {
//			public void componentResized(ComponentEvent evt) {
//				Dimension size = getSize();
//				Dimension min = getMinimumSize();
//				if (size.getWidth() < min.getWidth()) {
//					setSize((int) min.getWidth(), (int) size.getHeight());
//				}
//				if (size.getHeight() < min.getHeight()) {
//					setSize((int) size.getWidth(), (int) min.getHeight());
//				}
//			}
//		});

		// set this window as visible
		setVisible(true);
	}

	/**
	 * Method to read a file containing patterns and show the patterns in the table.
	 * Supports both standard SPMF lines and extended format with @ITEM mappings.
	 *
	 * @param patternFilePath the path to the file containing patterns, in SPMF format
	 * @throws IOException if an error occurs while reading the file
	 */
	private void readFile(String patternFilePath) throws IOException {
	    int numberOfPatterns = 0;

	    // Prepare table model data structures
	    data = new Vector<List<Object>>();
	    columnNames = new Vector<String>();
	    columnClasses = new Vector<Class>();
	    columnNames.add("Pattern");
	    columnClasses.add(String.class);

	    // Build ID→name map for extended format
	    Map<String, String> itemMapping = new HashMap<>();

	    try (BufferedReader br = new BufferedReader(new FileReader(patternFilePath))) {
	        String line;
	        while ((line = br.readLine()) != null) {
	            line = line.trim();
	            // Skip blank lines and comments
	            if (line.isEmpty() || line.startsWith("#") || line.startsWith("%")) {
	                continue;
	            }
	            // Handle extended mapping lines: @ITEM=id=name
	            if (line.startsWith("@ITEM=")) {
	                String[] parts = line.substring(6).split("=", 2);
	                if (parts.length == 2) {
	                    itemMapping.put(parts[0].trim(), parts[1].trim());
	                }
	                continue;
	            }
	            // Skip any other metadata lines beginning with @
	            if (line.startsWith("@")) {
	                continue;
	            }

	            // Now process a real data line
	            List<Object> lineData = new ArrayList<>();

	            // Split off the pattern part (before first " #")
	            int delimPos = line.indexOf(" #");
	            String rawPattern = (delimPos < 0)
	                    ? line
	                    : line.substring(0, delimPos);

	            // Map numeric IDs to names in the pattern
	            String mappedPattern = Arrays.stream(rawPattern.trim().split("\\s+"))
	                    .map(tok -> itemMapping.getOrDefault(tok, tok))
	                    .collect(Collectors.joining(" "));
	            lineData.add(mappedPattern);

	            // If there are attribute/value pairs, parse them after the pattern
	            if (delimPos >= 0) {
	                String cutLine = line;
	                while (delimPos >= 0) {
	                    cutLine = cutLine.substring(delimPos + 1);
	                    int spacePos = cutLine.indexOf(' ', 1);
	                    int nextDelim = cutLine.indexOf(" #", 1);

	                    String attrName = (spacePos > 0)
	                        ? cutLine.substring(0, spacePos)
	                        : cutLine;
	                    String attrVal = (nextDelim < 0)
	                        ? cutLine.substring(spacePos + 1)
	                        : cutLine.substring(spacePos + 1, nextDelim);

	                    // On first data row, register new columns
	                    if (numberOfPatterns == 0) {
	                        columnNames.add(attrName);
	                    }

	                    // Determine the type of attrVal
	                    Double d = isDouble(attrVal);
	                    Integer i = isInteger(attrVal);
	                    Boolean b = isBoolean(attrVal);

	                    if (d != null) {
	                        if (numberOfPatterns == 0) columnClasses.add(Double.class);
	                        lineData.add(d);
	                    } else if (i != null) {
	                        if (numberOfPatterns == 0) columnClasses.add(Integer.class);
	                        lineData.add(i);
	                    } else if (b != null) {
	                        if (numberOfPatterns == 0) columnClasses.add(Boolean.class);
	                        lineData.add(b);
	                    } else {
	                        if (numberOfPatterns == 0) columnClasses.add(String.class);
	                        lineData.add(attrVal);
	                    }

	                    delimPos = nextDelim;
	                }
	            }

	            data.add(lineData);
	            numberOfPatterns++;
	        }
	    }

	    // No data? leave table empty
	    if (numberOfPatterns == 0) {
	        return;
	    }

	    // Build and set the table model
	    model = new PatternTableModel(data, columnNames, columnClasses);
	    table.setModel(model);

	    // Attach sorter and filters
	    sorter = new TableRowSorter<>(model);
	    table.setRowSorter(sorter);
	    sorter.setRowFilter(rowFilters);

	    // Adjust column widths
	    new TableColumnAdjuster(table).adjustColumns();

	    // Update any UI labels/counts
	    refreshNumberOfPatternsDisplayed();
	}

	/**
	 * Check if a string value is a doule value
	 * 
	 * @param token the string value
	 * @return the value as a double, or null if it is not a double value
	 */
	private Double isDouble(String token) {
		Double result = null;
		try {
			result = Double.valueOf(token);
		} catch (Exception e) {
		}
		return result;
	}

	/**
	 * Check if a string value is an integer value
	 * 
	 * @param token the string value
	 * @return the value as an integer, or null if it is not an integer value
	 */
	private Integer isInteger(String token) {
		Integer result = null;
		try {
			result = Integer.valueOf(token);
		} catch (Exception e) {
		}
		return result;
	}

	/**
	 * Check if a string value is a boolean value
	 * 
	 * @param token the string value
	 * @return the value as a boolean, or null if it is not a boolean value
	 */
	private Boolean isBoolean(String token) {
		if ("true".equals(token)) {
			return Boolean.TRUE;
		} else if ("false".equals(token)) {
			return Boolean.FALSE;
		}
		return null;
	}

	/**
	 * This method is called when the user has added a new filter. It updates the
	 * list of filters, the buttons and also the JTable.
	 */
	public void filtersHaveBeenUpdated() {
		// remove all filters
		listModelFilters.clear();
		// fill the JList of filters
		for (int i = 0; i < rowFilters.filters.size(); i++) {
			AbstractFilter filter = (AbstractFilter) rowFilters.filters.get(i);
			listModelFilters.addElement(filter.getFilterWithParameterName());
		}
		// If there are some filters, then enable the bubtton "Remove all filters"
		if (rowFilters.filters.size() >= 0) {
			btnRemoveAllFilters.setEnabled(true);
		}

		// Notifiy all listeners that the filters have been updated
		// This will refresh the Jtable using the new filters
		for (TableModelListener listener : model.listeners) {
			listener.tableChanged(new TableModelEvent(model));
		}
		// Refresh the number of patterns displayed in the window
		refreshNumberOfPatternsDisplayed();
	}

	/**
	 * This method is called when the user selects a filter from the list of filters
	 * 
	 * @param arg0 a list selection event from the JList of filters
	 */
	protected void selectFilter(ListSelectionEvent arg0) {
		// if a filter is selected
		if (listFilters.getSelectedIndex() > -1) {
			// Enable the button "Remove selected filter"
			btnRemoveFilter.setEnabled(true);
		}
	}

	/**
	 * Method to remove the current selected filter in the list of filters.
	 */
	private void removeSelectedFilter() {
		// Get the index of the selected filter in the list of filter
		int index = listFilters.getSelectedIndex();
		// Remove the filter from the JList model
		listModelFilters.remove(index);
		// Remove the filter from the filters used by the JTable
		rowFilters.filters.remove(index);
		// Disable the button for removing the selected filter
		btnRemoveFilter.setEnabled(false);
		// if there are no filters left, then we also disable the "remove all filters"
		// button
		if (rowFilters.filters.size() == 0) {
			btnRemoveAllFilters.setEnabled(false);
		}
		// Notifiy all listeners that the filters have been updated
		// This will refresh the Jtable using the new filters
		for (TableModelListener listener : model.listeners) {
			listener.tableChanged(new TableModelEvent(model));
		}
		// Refresh the number of patterns displayed in the window
		refreshNumberOfPatternsDisplayed();
	}

	/**
	 * This method is called when the user removes all filters. It clear the JList
	 * of filters and also removes all filters from the Jtable, and update the user
	 * interface accordingly.
	 */
	private void removeAllFilters() {
		// Delete all the filters in the JList of filters
		listModelFilters.clear();
		// Delete all the filters in the Jtable
		rowFilters.filters.clear();
		// Disable the button for removing filters
		btnRemoveAllFilters.setEnabled(false);
		btnRemoveFilter.setEnabled(false);

		// Notifiy all listeners that the filters have been updated
		// This will refresh the Jtable using the new filters
		for (TableModelListener listener : model.listeners) {
			listener.tableChanged(new TableModelEvent(model));
		}
		// Refresh the number of patterns displayed in the window
		refreshNumberOfPatternsDisplayed();
	}

	/**
	 * Refresh the number of patterns displayed in the window
	 */
	private void refreshNumberOfPatternsDisplayed() {
		labelNumberOfPatterns.setText("Number of patterns: " + table.getRowCount());
	}

	/**
	 * This method is called when the user click on the button to export the current
	 * patters to a file format.
	 * 
	 * @throws IOException if an error occurs
	 */
	protected void export() {
		String selection = (String) comboBoxExport.getSelectedItem();

		// ask the user to choose the filename and path
		String outputFilePath = null;
		try {
			File path;
			// Get the last path used by the user, if there is one
			String previousPath = PreferencesManager.getInstance().getOutputFilePath();
			// If there is no previous path (first time user),
			// show the files in the "examples" package of
			// the spmf distribution.
			if (previousPath == null) {
				URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
				if (!"file".equalsIgnoreCase(main.getProtocol())) {
					path = null;
				} else {
					path = new File(main.getPath());
				}
			} else {
				// Otherwise, use the last path used by the user.
				path = new File(previousPath);
			}

			// ASK THE USER TO CHOOSE A FILE
			final JFileChooser fc;
			if (path != null) {
				fc = new JFileChooser(path.getAbsolutePath());
			} else {
				fc = new JFileChooser();
			}
			int returnVal = fc.showSaveDialog(PatternVizualizer.this);

			// If the user chose a file
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				outputFilePath = file.getPath(); // save the file path
				// save the path of this folder for next time.
				if (fc.getSelectedFile() != null) {
					PreferencesManager.getInstance().setOutputFilePath(fc.getSelectedFile().getParent());
				}
			} else {
				// the user did not choose so we return
				return;
			}

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while opening the output file dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}

		try {
			// if the user wants to save in CSV format
			if ("CSV format".equals(selection)) {
				exportToCSV(table, outputFilePath);
			} else if ("TSV format".equals(selection)) {
				// if the user wants to save in TSV format
				exportToTSV(table, outputFilePath);
			} else if ("SPMF format".equals(selection)) {
				// if the user wants to save in SPMF format
				exportToSPMFFormat(table, outputFilePath);
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while attempting to save the file. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Save content of JTable to the SPMF format
	 * 
	 * @param table    a JTable
	 * @param filepath the file path where the file should be saved
	 * @throws IOException exception if error writing to file
	 */
	private void exportToSPMFFormat(JTable table2, String outputFilePath) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));

		// For each row
		for (int i = 0; i < table.getRowCount(); i++) {

			// Write the data in that row for each column
			for (int j = 0; j < table.getColumnCount(); j++) {
				// if the first column
				if (j == 0) {
					// write the value
					writer.write(table.getValueAt(i, j).toString());
				}
				// if not the first column
				else {
					// write the column name + space
					writer.write(columnNames.get(j));
					writer.write(' ');
					// then write the value
					writer.write(table.getValueAt(i, j).toString());
				}
				// if not the last element on this line, we put a "," after
				if (j != table.getColumnCount() - 1) {
					writer.write(' ');
				}
			}
			// Write the end of line
			writer.newLine();
		}
		// Close the file
		writer.close();
	}

	/**
	 * Save content of JTable to tab-separated format compatible with Excel and
	 * other software
	 * 
	 * @param table    a JTable
	 * @param filepath the file path where the file should be saved
	 * @throws IOException exception if error writing to file
	 */
	public void exportToTSV(JTable table, String filepath) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filepath));
		// for each column
		for (int i = 0; i < table.getColumnCount(); i++) {
			// write the column name followed by a tab
			writer.write(table.getColumnName(i));
			// if not the last element on this line, we put a ","
			if (i != table.getColumnCount() - 1) {
				writer.write('\t');
			}
		}
		// then write the end of line
		writer.newLine();

		// For each row
		for (int i = 0; i < table.getRowCount(); i++) {

			// Write the data in that row for each column
			for (int j = 0; j < table.getColumnCount(); j++) {
				writer.write(table.getValueAt(i, j).toString());
				// if not the last element on this line, we put a "," after
				if (j != table.getColumnCount() - 1) {
					writer.write('\t');
				}
			}
			// Write the end of line
			writer.newLine();
		}
		// Close the file
		writer.close();
	}

	/**
	 * Save content of JTable to comma-separated format compatible with Excel and
	 * other software
	 * 
	 * @param table    a JTable
	 * @param filepath the file path where the file should be saved
	 * @throws IOException exception if error writing to file
	 */
	public void exportToCSV(JTable table, String filepath) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filepath));
		// for each column
		for (int i = 0; i < table.getColumnCount(); i++) {
			// write the column name followed by a tab
			String string = table.getColumnName(i);
			// if the , character appears, we should add quotes according to CSV format
			if (string.indexOf(',') != -1) {
				string = '\"' + string + '\"';
			}
			writer.write(string);
			// if not the last element on this line, we put a ","
			if (i != table.getColumnCount() - 1) {
				writer.write(',');
			}
		}
		// then write the end of line
		writer.newLine();

		// For each row
		for (int i = 0; i < table.getRowCount(); i++) {

			// Write the data in that row for each column
			for (int j = 0; j < table.getColumnCount(); j++) {
				String string = table.getValueAt(i, j).toString();
				// if the , character appears, we should add quotes according to CSV format
				if (string.indexOf(',') != -1) {
					string = '\"' + string + '\"';
				}
				writer.write(string);
				// if not the last element on this line, we put a "," after
				if (j != table.getColumnCount() - 1) {
					writer.write(',');
				}
			}
			// Write the end of line
			writer.newLine();
		}
		// Close the file
		writer.close();
	}

	/**
	 * This method is called when the user click on the "search" button
	 */
	protected void search() {
		String text = textFieldSearch.getText();
		// if the user did not enter any text, then we do nothing
		if (text.length() == 0) {
			return;
		}
		// We search from the position that is next to the current selected position in
		// the table
		// except if nothing is selected, then we will search from the current position
		// (0,0).
		int currentRow = table.getSelectedRow();
		int currentColumn = table.getSelectedColumn();
//		System.out.println("before" + currentRow + " " + currentColumn);

		currentColumn++;

		if (currentRow == -1) {
			currentRow = 0;
		}

//		System.out.println(currentRow + " " + currentColumn);

		// For each row
		for (; currentRow < table.getRowCount(); currentRow++) {

			// Write the data in that row for each column
			for (; currentColumn < table.getColumnCount(); currentColumn++) {
				// if we have found the searched text in the current cell
				if (table.getValueAt(currentRow, currentColumn).toString().contains(text)) {
					// select that cell
					table.changeSelection(currentRow, currentColumn, false, false);
//            		System.out.println("FOUND");
					return;
				}
			}
			currentColumn = 0;
		}
	}
}
