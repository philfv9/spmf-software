package ca.pfv.spmf.gui.workflow_editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.AboutWindow;
import ca.pfv.spmf.gui.CommandProcessor;
import ca.pfv.spmf.gui.ConsolePanel;
import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.MemoryViewer;
import ca.pfv.spmf.gui.NotifyingThread;
import ca.pfv.spmf.gui.ThreadCompleteListener;
import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmExplorer;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 * This class is a Workflow Editor tool offered in the SPMF software. It is a
 * JFrame that let the user build a workflow consisting of multiple groups. A
 * group is a set of node that may include: an input file, an algorithm to be
 * applied on it, and an output file produced by the algorithm. This JFrame
 * contains a DrawPanel for displaying the current workflow and an information
 * panel to display information about the current selected node of the workflow.
 * There are also a menu and some buttons for interacting with the workflow
 * editor
 * 
 * @author Philippe Fournier-Viger
 */
public class WorkflowEditorWindow extends JFrame
		implements DrawPanelListener, ThreadCompleteListener, UncaughtExceptionHandler {
	// The draw panel
	DrawPanel drawPanel;

	/** The progress bar when an algorithm is running */
	private JProgressBar progressBar;

	/** The textArea to display the console output */
	private ConsolePanel consolePanel;

	// The buttons
	JButton buttonAddAlgorithm, buttonEditNode, buttonRemoveLastNode, buttonValidate, buttonRun;

	/** Max time **/
	int maxTime = Integer.MAX_VALUE;

	public WorkflowEditorWindow(boolean runAsStandalone) throws Exception {
		// Set the title of the frame
		setTitle("SPMF Workflow Editor " + Main.SPMF_VERSION);
		setIconImage(Toolkit.getDefaultToolkit().getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/History24.gif")));

		// Set the size of the frame
		setSize(900, 700);

		// Set the layout of the frame to border layout
		setLayout(new BorderLayout());
		

		// Create the draw panel and add it to the center of the frame
		drawPanel = new DrawPanel();
		drawPanel.addDrawPanelListener(this);

		// Create a panel for the buttons and add it to the south of the frame
		JPanel buttonPanel = new JPanel();

		buttonAddAlgorithm = new JButton("Add an algorithm");
		buttonAddAlgorithm.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Add.png")));
		buttonAddAlgorithm.setEnabled(true);
		buttonPanel.add(buttonAddAlgorithm);

		// Create the remove button and add it to the button panel
		buttonRemoveLastNode = new JButton("Remove last algorithm");
		buttonRemoveLastNode.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Remove.png")));
		buttonRemoveLastNode.setEnabled(false);
		buttonPanel.add(buttonRemoveLastNode);

		JPanel runPanel = new JPanel();
		buttonValidate = new JButton("Validate the workflow");
		buttonValidate.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Validate.png")));
		buttonValidate.setEnabled(false);
		buttonRun = new JButton("Run the workflow");
		runPanel.add(buttonValidate);
		buttonRun.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Play24.gif")));
		buttonRun.setEnabled(false);
		runPanel.add(buttonRun);

		JPanel consoleProgressPanel = new JPanel();
		consoleProgressPanel.setLayout(new BorderLayout());
		progressBar = new JProgressBar();
		consolePanel = new ConsolePanel(false);
		consolePanel.setPreferredSize(new Dimension(200, 200));
		JLabel consoleLabel = new JLabel("Console:");
		consoleProgressPanel.add(consoleLabel, BorderLayout.NORTH);
		consoleProgressPanel.add(consolePanel, BorderLayout.CENTER);
		consoleProgressPanel.add(progressBar, BorderLayout.SOUTH);

		JPanel southPanel = new JPanel();
		southPanel.setLayout(new BorderLayout());
		southPanel.add(buttonPanel, BorderLayout.NORTH);
		southPanel.add(runPanel, BorderLayout.SOUTH);
		southPanel.add(consoleProgressPanel, BorderLayout.CENTER);

		add(southPanel, BorderLayout.SOUTH);

		infoPanel = new InformationPanel(this);
		drawPanel.addDrawPanelListener(infoPanel);
		// ===========================================================

		JScrollPane scrollPaneDraw = new JScrollPane(drawPanel);
		JScrollPane scrollPaneInformation = new JScrollPane(infoPanel);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPaneDraw, scrollPaneInformation);
		splitPane.setDividerLocation((int) (getWidth() * 0.30));

		JLabel workflowLabel = new JLabel("Workflow:");
		add(workflowLabel, BorderLayout.NORTH);
		add(splitPane, BorderLayout.CENTER);

		createMenuBar();

//		// Add an action listener to the add button to handle clicks
//		buttonAddInput.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				drawPanel.addInputNode();
//			}
//		});

		buttonAddAlgorithm.addActionListener(e -> drawPanel.addAlgorithmNode());
		buttonRemoveLastNode.addActionListener(e -> drawPanel.removeLastNode());
		buttonValidate.addActionListener(e -> validateWorkflow());
		buttonRun.addActionListener(e -> {
			try {
				runWorkflow();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		});

		// Set the default close operation of the frame to exit on close
		if (runAsStandalone) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		setLocationRelativeTo(null);

		// Set the visibility of the frame to true
		setVisible(true);
	}

	private void validateWorkflow() {
		// Save the current node information
		infoPanel.saveInformation(infoPanel.currentNode);

		String errorMessage = drawPanel.validateTheWorkflow();
		if (errorMessage != null) {
			JOptionPane.showMessageDialog(null, "The workflow is not valid. " + errorMessage, "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		} else {
			JOptionPane.showMessageDialog(null, "The workflow is valid. ", "", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void runWorkflow() throws InterruptedException {
		// Save the current node information
		infoPanel.saveInformation(infoPanel.currentNode);

		String errorMessage = drawPanel.validateTheWorkflow();
		if (errorMessage != null) {
			JOptionPane.showMessageDialog(null, "The workflow is not valid. " + errorMessage, "Error",
					JOptionPane.ERROR_MESSAGE);
			System.out.println("  " + errorMessage);
			return;
		}

		processRunAlgorithmCommandFromGUI();

	}

	private void createMenuBar() {
		// Create the menu bar
		JMenuBar menuBar = new JMenuBar();

		// Build the second menu "General"
//		JMenu menuAbout = new JMenu("About");
//		JMenuItem menuItemAbout = new JMenuItem("About SPMF");
		JMenuItem menuDocumentation = new JMenuItem("Open documentation webpage");
//		menuAbout.add(menuItemAbout);
//		menuAbout.add(menuDocumentation);

		// Build the second menu "Run options"
		JMenu menuRun = new JMenu("Options");
		checkBoxSeparatedProcess = new JCheckBoxMenuItem("Run workflow in a separated process");
//		JMenuItem menuItemRunWorkflowSeparatedProcess = new JMenuItem("Run workflow in a separated process");
//		menuTimeLimit = new JMenuItem("Run algorithms with time limit (s)");
//		menuRun.add(menuItemRunWorkflow);
//		menuRun.add(menuItemRunWorkflowSeparatedProcess);
//		menuRun.addSeparator();
		menuRun.add(checkBoxSeparatedProcess);
//		menuRunOptions.addSeparator();
//		menuRunOptions.add(menuTimeLimit);

		// Build the third menu "Tools"
		JMenu menuTools = new JMenu("Tools");
		JMenuItem menuItemAlgorithmExplorer = new JMenuItem("Algorithm Explorer");
		JMenuItem menuItemMemoryViewer = new JMenuItem("Memory viewer");

		menuTools.add(menuDocumentation);
		menuTools.addSeparator();
		menuTools.add(menuItemAlgorithmExplorer);
		menuTools.add(menuItemMemoryViewer);
		
		// Build the third menu "Export"
		JMenu menuExport = new JMenu("Workflow");
		menuItemValidate = new JMenuItem("Validate the workflow");
		menuItemRun = new JMenuItem("Run the workflow");
		JMenuItem menuExportBAT = new JMenuItem("Export workflow as BAT script (for Windows)");
		JMenuItem menuExportSH = new JMenuItem("Export workflow as SH script (for Linux)");
		menuExport.add(menuItemValidate);
		menuExport.add(menuItemRun);
		menuExport.addSeparator();
		menuExport.add(menuExportBAT);
		menuExport.add(menuExportSH);

		// Add menus to the menu bar
		menuBar.add(menuRun);
		menuBar.add(menuExport);
		menuBar.add(menuTools);
//		menuBar.add(menuAbout);

		// Set the menu bar for the frame
		setJMenuBar(menuBar);

		// Add action listeners
//		menuItemAbout.addActionListener(e -> showAboutDialog());
		menuDocumentation.addActionListener(e -> openDocumentation());
		menuItemAlgorithmExplorer.addActionListener(e -> openAlgorithmExplorer());
		menuItemMemoryViewer.addActionListener(e -> openMemoryViewer());
		menuExportBAT.addActionListener(e -> exportAsBATFile());
		menuExportSH.addActionListener(e -> exportAsSHFile());
		menuItemValidate.addActionListener(e -> validateWorkflow());
		menuItemValidate.setEnabled(false);
		menuItemRun.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					runWorkflow();
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		menuItemRun.setEnabled(false);
	}

	/**
	 * This method open the documentation in the default web browser.
	 *
	 * @param url : URL of the webpage
	 */
	private void openDocumentation() {
		try {
			java.awt.Desktop.getDesktop().browse(
					java.net.URI.create("http://philippe-fournier-viger.com/spmf/index.php?link=documentation.php"));
		} catch (java.io.IOException e) {
			System.out.println(e.getMessage());
		}
	}

	private void openMemoryViewer() {
		MemoryViewer.displayMemoryChart();
	}

	private void openAlgorithmExplorer() {
		AlgorithmExplorer frame = new AlgorithmExplorer(false);
		frame.setVisible(true);
	}

	// Methods for each action
	private void showAboutDialog() {
		try {
			AboutWindow about = new AboutWindow(this);
			about.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void notifyNodeSelected(Node node) {
		// do nothing

	}

	@Override
	public void notifyHasOutputNode(boolean hasOutput) {
		buttonAddAlgorithm.setEnabled(hasOutput);

	}

	@Override
	public void notifyOfListOfGroups(List<GroupOfNodes> allgroups) {
		buttonRemoveLastNode.setEnabled(allgroups.size() > 0);
		buttonRun.setEnabled(allgroups.size() > 0);
		buttonValidate.setEnabled(allgroups.size() > 0);
		menuItemRun.setEnabled(allgroups.size() > 0);
		menuItemValidate.setEnabled(allgroups.size() > 0);
	}

	/** The current data mining task (used for running an algorithm as a thread */
	private static NotifyingThread currentRunningAlgorithmThread = null;

	/** the current data mining process */
	private static Process currentExternalProcess = null;

	private JCheckBoxMenuItem checkBoxSeparatedProcess;

	private InformationPanel infoPanel;

	private int tasksCompleted;

	private JMenuItem menuItemValidate;

	private JMenuItem menuItemRun; 

	/**
	 * This method receives the notifications when an algorithm launched by the user
	 * throw an exception
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable e) {
		// If the thread just die because the user click on the "Stop algorithm" button
		if (e instanceof ThreadDeath) {
			// we just let the thread die.
		} else if (e instanceof NumberFormatException) {
			// if it is a number format exception, meaning that the user enter a string as a
			// parameter instead
			// of an integer or double value.
			JOptionPane.showMessageDialog(null,
					"Error. Please check the parameters of the algorithm.  The format for numbers is incorrect. \n"
							+ "\n ERROR MESSAGE = " + e.toString(),
					"Error", JOptionPane.ERROR_MESSAGE);
		} else {
			// If another kind of error occurred while running the algorithm, show the
			// error.
			JOptionPane.showMessageDialog(null,
					"An error occurred while trying to run the algorithm. \n ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		consolePanel.clearConsole();
	}
	
	/**
	 * Method to export the current workflow as a SH file.
	 * 
	 * @throws IOException if some errors occurs
	 */
	private void exportAsSHFile() {
		/// ASK TO CHOOSE A FILE PATH
		BufferedWriter writer = null;
		try {
			// Determine the path and preference based on input or output
			String previousPath = PreferencesManager.getInstance().getInputFilePath();
			File path = null;
			if (previousPath == null) {
				URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
				if ("file".equalsIgnoreCase(main.getProtocol())) {
					path = new File(main.getPath());
				}
			} else {
				path = new File(previousPath);
			}

		    // Create a file chooser
		    final JFileChooser fc = path != null ? new JFileChooser(path.getAbsolutePath()) : new JFileChooser();

		    // Set up the file filter for .sh files
		    FileNameExtensionFilter filter = new FileNameExtensionFilter("SH Scripts", "sh");
		    fc.setFileFilter(filter);
		    fc.setAcceptAllFileFilterUsed(false);

		    // Show save dialog
		    int returnVal = fc.showSaveDialog(this);

			File file = null;
			// Process the result of the file chooser
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				file = fc.getSelectedFile();
				PreferencesManager.getInstance().setInputFilePath(file.getParent());
			}
			if (file == null) {
				return;
			}

			writer = new BufferedWriter(new FileWriter(file.getPath()));
			
		    // Write the shebang line for Unix/Linux script
	        writer.write("#!/bin/bash");
	        writer.newLine();

			// For each group
			for (int i = 0; i < drawPanel.groups.size(); i++) {
				GroupOfNodes group = drawPanel.groups.get(i);
				// Get the parameters
				final String choice = group.nodeAlgorithm.name;
				boolean isFirst = (i == 0);

				String inputFile = (isFirst) ? group.nodeInput.inputFile
						: drawPanel.groups.get(i - 1).nodeOutput.outputFile;

				List<String> commandWithParameters = new ArrayList<String>(15);
				commandWithParameters.add("java");
				commandWithParameters.add("-jar");
				commandWithParameters.add("spmf.jar");
				commandWithParameters.add("run");

				commandWithParameters.add(choice);
				// If the first algorithm and it has an input
				if (isFirst) {
					if (group.showInput == true) {
			            // Replace backslashes with forward slashes in file paths
						inputFile = inputFile.replace(File.separatorChar, '/');
						commandWithParameters.add(inputFile);
					}
				} else {
					commandWithParameters.add(inputFile);
				}

				if (group.showOutput == true) {
					String outputFile = group.nodeOutput.outputFile;
					outputFile = outputFile.replace(File.separatorChar, '/');
					commandWithParameters.add(outputFile);
				}

				final String[] parameters = group.nodeAlgorithm.getNonNullParameters();
				for (int j = 0; j < parameters.length; j++) {
					if (parameters[j] != null & !parameters[j].isEmpty()) {
						commandWithParameters.add(parameters[j]);
					}
				}

				StringBuffer singleLineCommand = new StringBuffer();
				for (String value : commandWithParameters) {
					singleLineCommand.append(value);
					singleLineCommand.append(" ");
				}

				writer.write(singleLineCommand.toString());
				writer.newLine();

			}
			writer.close();
			
			System.out.println("Workflow exported successfully as a .sh script.");

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occurred while opening the file dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Method to export the current workflow as a BAT file.
	 * 
	 * @throws IOException if some errors occurs
	 */
	private void exportAsBATFile() {
		/// ASK TO CHOOSE A FILE PATH
		BufferedWriter writer = null;
		try {
			// Determine the path and preference based on input or output
			String previousPath = PreferencesManager.getInstance().getInputFilePath();
			File path = null;
			if (previousPath == null) {
				URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
				if ("file".equalsIgnoreCase(main.getProtocol())) {
					path = new File(main.getPath());
				}
			} else {
				path = new File(previousPath);
			}

		    // Create a file chooser
		    final JFileChooser fc = path != null ? new JFileChooser(path.getAbsolutePath()) : new JFileChooser();

		    // Set up the file filter for .sh files
		    FileNameExtensionFilter filter = new FileNameExtensionFilter("BAT Scripts", "bat");
		    fc.setFileFilter(filter);
		    fc.setAcceptAllFileFilterUsed(false);

		    // Show save dialog
		    int returnVal = fc.showSaveDialog(this);

			File file = null;
			// Process the result of the file chooser
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				file = fc.getSelectedFile();
				PreferencesManager.getInstance().setInputFilePath(file.getParent());
			}
			if (file == null) {
				return;
			}

			writer = new BufferedWriter(new FileWriter(file.getPath()));

			// For each group
			for (int i = 0; i < drawPanel.groups.size(); i++) {
				GroupOfNodes group = drawPanel.groups.get(i);
				// Get the parameters
				final String choice = group.nodeAlgorithm.name;
				boolean isFirst = (i == 0);

				String inputFile = (isFirst) ? group.nodeInput.inputFile
						: drawPanel.groups.get(i - 1).nodeOutput.outputFile;

				List<String> commandWithParameters = new ArrayList<String>(15);
				commandWithParameters.add("java");
				commandWithParameters.add("-jar");
				commandWithParameters.add("spmf.jar");
				commandWithParameters.add("run");

				commandWithParameters.add(choice);
				// If the first algorithm and it has an input
				if (isFirst) {
					if (group.showInput == true) {
						commandWithParameters.add(inputFile);
					}
				} else {
					commandWithParameters.add(inputFile);
				}

				if (group.showOutput == true) {
					commandWithParameters.add(group.nodeOutput.outputFile);
				}

				final String[] parameters = group.nodeAlgorithm.getNonNullParameters();
				for (int j = 0; j < parameters.length; j++) {
					if (parameters[j] != null & !parameters[j].isEmpty()) {
						commandWithParameters.add(parameters[j]);
					}
				}

				StringBuffer singleLineCommand = new StringBuffer();
				for (String value : commandWithParameters) {
					singleLineCommand.append(value);
					singleLineCommand.append(" ");
				}

				writer.write(singleLineCommand.toString());
				writer.newLine();

			}
			writer.close();
			
			System.out.println(" Workflow exported successfully.");

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occurred while opening the file dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}
	
	/**
	 * This method is called when the user click the "Run" or "Stop" button of the
	 * user interface, to launch the chosen algorithm and thereafter catch exception
	 * if one occurs.
	 * 
	 * @throws InterruptedException
	 */
	private void processRunAlgorithmCommandFromGUI() throws InterruptedException {

		if (checkBoxSeparatedProcess.isSelected()) {
			File file = new File("spmf.jar");
			if (file.exists() == false) {
				JOptionPane.showMessageDialog(null,
						"The workflow cannot be run in a separated process because spmf.jar is not found. It will be run in the same process.",
						"Error", JOptionPane.ERROR_MESSAGE);
				checkBoxSeparatedProcess.setSelected(false);
			}
		}

		buttonRun.setEnabled(false);

		// If the algorithm is running, try to kill it
		boolean killed = tryToKillProcess();
		if (killed) {
			return;
		}

		tasksCompleted = 0;

		// For each group
		for (int i = 0; i < drawPanel.groups.size(); i++) {
			GroupOfNodes group = drawPanel.groups.get(i);
			// Get the parameters
			final String choice = group.nodeAlgorithm.name;

			// Get the current time
			SimpleDateFormat dateTimeInGMT = new SimpleDateFormat("hh:mm:ss aa");
			String time = dateTimeInGMT.format(new Date());

			consolePanel.postStatusMessage("Algorithm is running... (" + time + ")  \n");

			////////////////////////////////////////
			if (!choice.equals("MemoryViewer")) {
				////////////////////////////////////////
				currentRunningAlgorithmThread = null;
				progressBar.setIndeterminate(true);
				buttonRun.setText("Stop algorithm");
				buttonRun.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Stop24.gif")));
//				buttonRun.setEnabled(false);
			}

			Thread.sleep(10);

			boolean isFirst = (i == 0);

			// Wait for the previous tasks to be completed
			while (tasksCompleted != i) {
				Thread.sleep(10);

				if (tasksCompleted == -1) {
					return;
				}
			}
//			System.out.println("Starting task i");

			String inputFile = (isFirst) ? group.nodeInput.inputFile
					: drawPanel.groups.get(i - 1).nodeOutput.outputFile;

			// RUN THE SELECTED ALGORITHM in a new thread
			// create a thread to execute the algorithm
			if (checkBoxSeparatedProcess.isSelected()) {
				// If the algorithm is run as an external program
				currentRunningAlgorithmThread = new NotifyingThread() {
					@Override
					public boolean doRun() throws Exception {
						List<String> commandWithParameters = new ArrayList<String>(15);
						commandWithParameters.add("java");
						commandWithParameters.add("-jar");
						commandWithParameters.add("spmf.jar");
						commandWithParameters.add("run");

						commandWithParameters.add(choice);
						// If the first algorithm and it has an input
						if (isFirst) {
							if (group.showInput == true) {
								commandWithParameters.add(inputFile);
							}
						} else {
							commandWithParameters.add(inputFile);
						}

						if (group.showOutput == true) {
							commandWithParameters.add(group.nodeOutput.outputFile);
						}

						final String[] parameters = group.nodeAlgorithm.getNonNullParameters();
						for (int i = 0; i < parameters.length; i++) {
							if (parameters[i] != null & !parameters[i].isEmpty()) {
								commandWithParameters.add(parameters[i]);
							}
						}

						// Call the JAR file to run the algorithm
						System.out.println("===== RUN AS EXTERNAL PROGRAM ========");
						StringBuffer singleLineCommand = new StringBuffer(80);
						singleLineCommand.append(" COMMAND: ");
						for (String value : commandWithParameters) {
							singleLineCommand.append(value);
							singleLineCommand.append(" ");
						}
						System.out.println(singleLineCommand);
						ProcessBuilder pb = new ProcessBuilder(commandWithParameters);
						pb.redirectOutput(Redirect.INHERIT);
						pb.redirectError(Redirect.INHERIT);

						int exitValue = 1;
						try {
							currentExternalProcess = pb.start();
							exitValue = currentExternalProcess.waitFor();
						} catch (IOException e) {
							throw new IllegalArgumentException(
									System.lineSeparator() + System.lineSeparator() + "I/O Error.");
						}
						notifyOfThreadComplete(this, false);
						return (exitValue == 0);
					}
				};
				// The main thread will listen for} the completion of the algorithm
				currentRunningAlgorithmThread.addListener(this);
				// The main thread will also listen for exception generated by the
				// algorithm.
				currentRunningAlgorithmThread.setUncaughtExceptionHandler(this);
				// Run the thread
				currentRunningAlgorithmThread.start();

			} else {
				final String[] parameters = group.nodeAlgorithm.getNonNullParameters();

				// If the algorithm is run as a thread
				currentRunningAlgorithmThread = new NotifyingThread() {
					@Override
					public boolean doRun() throws Exception {
						CommandProcessor.runAlgorithm(choice, inputFile, group.nodeOutput.outputFile, parameters);
						return true;
					}
				};
				// The main thread will listen for the completion of the algorithm
				currentRunningAlgorithmThread.addListener(this);
				// The main thread will also listen for exception generated by the
				// algorithm.
				currentRunningAlgorithmThread.setUncaughtExceptionHandler(this);
				// Run the thread
				currentRunningAlgorithmThread.start();

				if (choice.equals("MemoryViewer")) {
					currentRunningAlgorithmThread = null;
				}
			}

			// If the user set a max time limit, we launch a thread to monitor and kill the
			// process
			// if the time limit is exceeded
			if (maxTime >= 0) {

				// Create the killer thread
				NotifyingThread killerThread = new NotifyingThread() {
					@Override
					public boolean doRun() throws Exception {
						int secondsElapsed = 0;

						// While the algorithm is still running
						while (currentRunningAlgorithmThread != null && currentRunningAlgorithmThread.isAlive()
								|| currentExternalProcess != null && currentExternalProcess.isAlive()) {

							// wait one second
							Thread.sleep(1000);

							// increase number of seconds by 1
							secondsElapsed++;

							// If time is up
							if (secondsElapsed >= maxTime) {

								// Try to kill the algorithm
								boolean killed = tryToKillProcess();
								if (killed) {
									System.out.println(" Stopped because of time limit of " + maxTime + " seconds");
								}
							}
						}
						return false;
					}
				};
				// Run the killer thread
				killerThread.start();
			}

		}
	}

	/**
	 * Try to kill the current algorithm if it is running
	 * 
	 * @return true if the algorithm is killed, otherwise false.
	 */
	private boolean tryToKillProcess() {
		// if an external process is running
		if (currentExternalProcess != null && currentExternalProcess.isAlive()) {
			// stop that thread
			currentExternalProcess.destroyForcibly();
			consolePanel.postStatusMessage("Algorithm stopped. \n");
			currentRunningAlgorithmThread = null;
			resetUIAfterThreadCompletion();
			return true;
		}
		// If a thread is already running (the user click on the stop Button
		else if (currentRunningAlgorithmThread != null && currentRunningAlgorithmThread.isAlive()) {
			// stop that thread
			try {
				currentRunningAlgorithmThread.stop();
				consolePanel.postStatusMessage("Algorithm stopped. \n");
				currentRunningAlgorithmThread = null;
				resetUIAfterThreadCompletion();
				return true;
			} catch (java.lang.UnsupportedOperationException e) {
				JOptionPane.showMessageDialog(null, "Stopping the algorithm is not supported for Java version "
						+ System.getProperty("java.version"), "Error", JOptionPane.ERROR_MESSAGE);
				return true;
			}
		}
		return false;
	}

	/**
	 * Reset the UI after thread completion
	 */
	private void resetUIAfterThreadCompletion() {
		buttonRun.setText("Run workflow");
		buttonRun.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Play24.gif")));
		buttonRun.setEnabled(true);
		progressBar.setIndeterminate(false);
	}

	@Override
	public void notifyOfThreadComplete(Thread thread, boolean succeed) {
		currentRunningAlgorithmThread = null;
		// If success
		if (succeed) {
			tasksCompleted += 1;
			if (tasksCompleted == drawPanel.groups.size()) {
				resetUIAfterThreadCompletion();
			}
		} else {
			// If failure
			tasksCompleted = -1;
			resetUIAfterThreadCompletion();
		}
	}

}