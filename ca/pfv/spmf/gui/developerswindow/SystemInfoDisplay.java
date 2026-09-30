package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

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
 * A class to display system information
 * 
 * NEW: the class has been improved with:
 * <ul>
 * <li>Null-safe reading of environment variables, so that variables that do not
 * exist on the current OS (e.g. "COMPUTERNAME" on Linux/Mac) are shown as "N/A"
 * instead of the literal text "null".</li>
 * <li>Additional useful, portable JVM/OS information (available processors,
 * default charset, default locale, temp directory, file/path/line
 * separators).</li>
 * <li>Memory values are now shown in both bytes and megabytes for easier
 * reading.</li>
 * <li>A toolbar with "Refresh" (updates dynamic values such as free memory),
 * "Copy to Clipboard" and "Save to File" buttons.</li>
 * <li>The table is now explicitly non-editable, uses a monospaced font for the
 * "Value" column, has sensible column widths, and shows the full cell content
 * as a tooltip (useful for long values such as the classpath).</li>
 * <li>Removed a duplicate/conflicting call to setSize(...).</li>
 * </ul>
 * 
 * @author Philippe Fournier-Viger
 */
public class SystemInfoDisplay extends JFrame {
	/**
	 * serial UID
	 */
	private static final long serialVersionUID = -6239858712290930028L;

	/** NEW: the table model is kept as a field so it can be refreshed later. */
	private DefaultTableModel model;

	/** NEW: the table is kept as a field for convenience (copy/save/refresh). */
	private JTable table;

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	public SystemInfoDisplay(boolean runAsStandalone) throws Exception {
		if (runAsStandalone) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		// Set up the frame
		setTitle("System and JVM Information");
		setSize(700, 550);
		setMinimumSize(new Dimension(500, 350));

		// Prepare table data
		String[] columnNames = { "Property", "Value" };
		List<String[]> data = buildSystemInfoData();

		// Create table model and JTable
		model = new DefaultTableModel(data.toArray(new String[0][]), columnNames) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				// NEW: the table is now explicitly read-only
				return false;
			}
		};

		table = new SortableJTable();
		table.setModel(model);
		table.setFillsViewportHeight(true);
		table.setRowHeight(22);

		// NEW: give the "Property" and "Value" columns sensible preferred widths
		table.getColumnModel().getColumn(0).setPreferredWidth(220);
		table.getColumnModel().getColumn(1).setPreferredWidth(450);

		// NEW: use a monospaced font for the "Value" column and show the full
		// cell content as a tooltip, which is very useful for long values such
		// as the Java class path or the library path.
		DefaultTableCellRenderer valueRenderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
					int row, int column) {
				Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
				c.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
				if (c instanceof javax.swing.JComponent) {
					((javax.swing.JComponent) c).setToolTipText(value == null ? null : value.toString());
				}
				return c;
			}
		};
		table.getColumnModel().getColumn(1).setCellRenderer(valueRenderer);

		// Add the table to a scroll pane
		JScrollPane scrollPane = new JScrollPane(table);

		// NEW: toolbar with refresh / copy / save buttons
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);

		JButton refreshButton = new JButton("Refresh");
		refreshButton.setToolTipText("Refresh dynamic values such as free/total memory");
		refreshButton.addActionListener(e -> refreshData());
		toolBar.add(refreshButton);

		JButton copyButton = new JButton("Copy to Clipboard");
		copyButton.setToolTipText("Copy the full table content to the clipboard as text");
		copyButton.addActionListener(e -> {
			copyToClipboard(buildReportText());
			JOptionPane.showMessageDialog(this, "The system information has been copied to the clipboard.");
		});
		toolBar.add(copyButton);

		JButton saveButton = new JButton("Save to File");
		saveButton.setToolTipText("Save the full table content to a text file");
		saveButton.addActionListener(e -> saveReportToFile(buildReportText()));
		toolBar.add(saveButton);

		// Add the toolbar and the scroll pane to the frame
		add(toolBar, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);

		setLocationRelativeTo(null);
		setVisible(true);
	}

	/**
	 * NEW METHOD Build the list of [property, value] pairs describing the current
	 * system and JVM. Environment variables that do not exist on the current OS are
	 * shown as "N/A" instead of "null".
	 * 
	 * @return the list of property/value rows
	 */
	private List<String[]> buildSystemInfoData() {
		List<String[]> data = new ArrayList<>();

		data.add(new String[] { "Java Version", prop("java.version") });
		data.add(new String[] { "Java Runtime Version", prop("java.runtime.version") });
		data.add(new String[] { "Java Vendor", prop("java.vendor") });
		data.add(new String[] { "OS Name", prop("os.name") });
		data.add(new String[] { "OS Version", prop("os.version") });
		data.add(new String[] { "OS Architecture", prop("os.arch") });
		data.add(new String[] { "User's Home Directory", prop("user.home") });
		data.add(new String[] { "User's Current Working Directory", prop("user.dir") });
		data.add(new String[] { "Java Class Path", prop("java.class.path") });
		data.add(new String[] { "Java Library Path", prop("java.library.path") });
		data.add(new String[] { "Java Installation Directory", prop("java.home") });
		data.add(new String[] { "Temp Directory", prop("java.io.tmpdir") });

		// NEW: additional, portable (cross-platform) information
		data.add(new String[] { "Default Charset", Charset.defaultCharset().displayName() });
		data.add(new String[] { "Default Locale", Locale.getDefault().toString() });
		data.add(new String[] { "File Separator", prop("file.separator") });
		data.add(new String[] { "Path Separator", prop("path.separator") });
		data.add(new String[] { "Line Separator", prop("line.separator").replace("\n", "\\n").replace("\r", "\\r") });
		data.add(new String[] { "Available Processors (JVM)",
				String.valueOf(Runtime.getRuntime().availableProcessors()) });

		// Environment variables (may not exist on all operating systems)
		data.add(new String[] { "PATH", env("PATH") });
		data.add(new String[] { "TEMP", env("TEMP") });
		data.add(new String[] { "User name", env("USERNAME") });
		data.add(new String[] { "Computer name", env("COMPUTERNAME") });
		data.add(new String[] { "Processor ID", env("PROCESSOR_IDENTIFIER") });
		data.add(new String[] { "Processor Architecture", env("PROCESSOR_ARCHITECTURE") });
		data.add(new String[] { "Processor Level", env("PROCESSOR_LEVEL") });
		data.add(new String[] { "Processor count (env)", env("NUMBER_OF_PROCESSORS") });

		// Memory information, shown in both bytes and megabytes for readability
		long totalMemory = Runtime.getRuntime().totalMemory();
		long freeMemory = Runtime.getRuntime().freeMemory();
		long maxMemory = Runtime.getRuntime().maxMemory();
		data.add(new String[] { "Total Memory", formatMemory(totalMemory) });
		data.add(new String[] { "Free Memory", formatMemory(freeMemory) });
		data.add(new String[] { "Used Memory", formatMemory(totalMemory - freeMemory) });
		data.add(new String[] { "Max Memory", formatMemory(maxMemory) });

		return data;
	}

	/**
	 * NEW METHOD Safely read a system property, returning "N/A" if it is null.
	 * 
	 * @param name the property name
	 * @return the property value, or "N/A" if not available
	 */
	private static String prop(String name) {
		String value = System.getProperty(name);
		return value == null ? "N/A" : value;
	}

	/**
	 * NEW METHOD Safely read an environment variable, returning "N/A" if it is null
	 * (which commonly happens for Windows-specific variables when running on
	 * Linux/Mac, or vice versa).
	 * 
	 * @param name the environment variable name
	 * @return the variable value, or "N/A" if not available
	 */
	private static String env(String name) {
		String value = System.getenv(name);
		return value == null ? "N/A" : value;
	}

	/**
	 * NEW METHOD Format a memory size (in bytes) as a human-readable string showing
	 * both the raw byte count and the equivalent value in megabytes.
	 * 
	 * @param bytes the memory size in bytes
	 * @return the formatted string, e.g. "134,217,728 bytes (128 MB)"
	 */
	private static String formatMemory(long bytes) {
		long mb = bytes / (1024 * 1024);
		return String.format("%,d bytes (%,d MB)", bytes, mb);
	}

	/**
	 * NEW METHOD Refresh the dynamic values in the table (e.g. free/used memory),
	 * by rebuilding the whole data set and updating the table model.
	 */
	private void refreshData() {
		List<String[]> data = buildSystemInfoData();
		model.setRowCount(0);
		for (String[] row : data) {
			model.addRow(row);
		}
	}

	/**
	 * NEW METHOD Build a plain-text report of the current table content (one
	 * "Property: Value" line per row), suitable for copying to the clipboard or
	 * saving to a file.
	 * 
	 * @return the report as a single string
	 */
	private String buildReportText() {
		StringBuilder sb = new StringBuilder();
		sb.append("SPMF - System and JVM Information").append(System.lineSeparator());
		sb.append("===================================").append(System.lineSeparator());
		sb.append(System.lineSeparator());
		for (int row = 0; row < model.getRowCount(); row++) {
			Object property = model.getValueAt(row, 0);
			Object value = model.getValueAt(row, 1);
			sb.append(property).append(": ").append(value).append(System.lineSeparator());
		}
		return sb.toString();
	}

	/**
	 * NEW METHOD Copy the given text to the system clipboard.
	 * 
	 * @param text the text to copy
	 */
	private static void copyToClipboard(String text) {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(new StringSelection(text), null);
	}

	/**
	 * NEW METHOD Save the given report to a text file chosen by the user via a file
	 * chooser dialog.
	 * 
	 * @param report the report to save
	 */
	private void saveReportToFile(String report) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setSelectedFile(new File("spmf_system_info.txt"));
		int result = fileChooser.showSaveDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
				writer.write(report);
				JOptionPane.showMessageDialog(this, "The file has been saved successfully.");
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "An error occured while saving the file: " + e.getMessage(),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/** Method for testing **/
	public static void main(String[] args) throws Exception {
		SystemInfoDisplay frame = new SystemInfoDisplay(true);
		frame.setVisible(true);
	}
}