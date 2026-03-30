package ca.pfv.spmf.gui.experiments;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.util.List;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.experiments.oneparametervaried.ExperimenterForParameterChange;
import ca.pfv.spmf.gui.NotifyingThread;
import ca.pfv.spmf.gui.ThreadCompleteListener;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * This class is a window that can be used to launch an experiment to compare
 * the performance of one or more algorithm when a parameter value is varied.
 * 
 * @author Philippe Fournier-Viger, 2022
 * @see ExperimenterForParameterChange
 *
 */
public class ExperimenterWindow extends JFrame implements ThreadCompleteListener, UncaughtExceptionHandler {
	/** serial UID */
	private static final long serialVersionUID = 2151286070078740128L;

	/** text field used to put the algorithm(s) parameters */
	private JTextField textFieldParameters;

	/** text field to specify the timeount */
	private JTextField textFieldTimeLimit;

	/** text field to specify the values for the varied parameter */
	private JTextField textFieldValues;

	/** text field to provide a list of algorithms */
	private JTextField textFieldAlgorithms;

	/** text area to show the results */
	private JTextArea textAreaResult;

	/**
	 * A thread that is used to run the experiment so that the GUI will not freeze
	 */
	private static NotifyingThread currentRunningAlgorithmThread = null;

	/** The button to run the experiments */
	private JButton buttonRun;

	/** The input file */
	private String inputFile = "";

	/** The output directory */
	private String outputDirectory = "";

	/** Path to SPMF.jar */
	private String pathToSPMFJar = null;

	/** The text field to choose the input file */
	private JTextField textFieldInputFile;

	/** The text field to choose the output directory */
	private JTextField textFieldOutputDirectory;

	/** The text field to provide a path to a jar file */
	private JTextField textFieldJARPath;

	/** Combo box for the list of algorithms */
	private JComboBox<String> comboBoxAlgorithms;

	/** Button to add an algorithm */
	JButton buttonAddAlgorithm;
	
	/** Checkbox for counting the number of lines in the output */
	JCheckBox checkboxCountLines;
	
	/** Checkbox to indicate if PGFPlot figures should be generated */
	JCheckBox checkboxPGFPlots;

	/** The text field to specify a timeout in milliseconds */
	private int timeoutMilliseconds;
	
	/** Help button */
	JButton buttonHelp;

	/**
	 * A main method to directly launch this tool.
	 * 
	 * @param arg arguments (should be empty)
	 * @throws IOException if some error occurs
	 */
	public static void main(String[] arg) throws IOException {
		
		System.out.println("replace%".replaceAll("[%]", "\\%"));

		// Create the window
		ExperimenterWindow experimenter = new ExperimenterWindow();
		// Make it visible
		experimenter.setVisible(true);
		experimenter.setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	/**
	 * Constructor
	 */
	public ExperimenterWindow() {
		setTitle("Run an experiment (one parameter is varied)");
		// size of the window
		setSize(825, 801);
		setLocationRelativeTo(null); 
		setResizable(false);
		getContentPane().setLayout(null);

		JLabel lblNewLabel = new JLabel("Step 2: Select an input file:");
		lblNewLabel.setBounds(10, 123, 193, 14);
		getContentPane().add(lblNewLabel);

		textFieldParameters = new JTextField();
		textFieldParameters.setText("##");
		textFieldParameters.setBounds(34, 233, 757, 20);
		getContentPane().add(textFieldParameters);
		textFieldParameters.setColumns(10);

		JLabel lblNewLabel_1 = new JLabel(
				"Step 1: Select algorithm(s) to be compared (Note: must have the same parameters):");
		lblNewLabel_1.setBounds(10, 15, 570, 14);
		getContentPane().add(lblNewLabel_1);

		JLabel lblNewLabel_2 = new JLabel(
				"Step 5: Provide a list of values for the parameter to be varied (separated by a space):");
		lblNewLabel_2.setBounds(10, 278, 781, 14);
		getContentPane().add(lblNewLabel_2);

		JLabel lblNewLabel_3 = new JLabel("Time limit for each run (s): ");
		lblNewLabel_3.setBounds(34, 375, 169, 14);
		getContentPane().add(lblNewLabel_3);

		textFieldTimeLimit = new JTextField();
		textFieldTimeLimit.setBounds(213, 372, 496, 20);
		getContentPane().add(textFieldTimeLimit);
		textFieldTimeLimit.setColumns(10);

		textFieldValues = new JTextField();
		textFieldValues.setText("0.5 0.4 0.3 0.2 0.1");
		textFieldValues.setBounds(34, 299, 757, 20);
		getContentPane().add(textFieldValues);
		textFieldValues.setColumns(10);

		buttonRun = new JButton("Run the experiment");
		buttonRun.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				runExperiment();
			}
		});
		buttonRun.setBounds(270, 475, 230, 23);
		getContentPane().add(buttonRun);

		textFieldAlgorithms = new JTextField();
		textFieldAlgorithms.setBounds(34, 73, 757, 20);
		getContentPane().add(textFieldAlgorithms);
		textFieldAlgorithms.setColumns(10);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(34, 509, 757, 242);
		getContentPane().add(scrollPane);

		textAreaResult = new JTextArea();
		textAreaResult.setText(" ");
		textAreaResult.setEditable(false);
		scrollPane.setViewportView(textAreaResult);

		JLabel lblNewLabel_5 = new JLabel("Results:");
		lblNewLabel_5.setBounds(10, 484, 414, 14);
		getContentPane().add(lblNewLabel_5);

		textFieldInputFile = new JTextField();
		textFieldInputFile.setEditable(false);
		textFieldInputFile.setBounds(213, 120, 496, 20);
		getContentPane().add(textFieldInputFile);
		textFieldInputFile.setColumns(10);

		JButton buttonInputFile = new JButton("...");
		buttonInputFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				askUserToChooseInputFile();
			}
		});
		buttonInputFile.setBounds(719, 119, 72, 23);
		getContentPane().add(buttonInputFile);

		JLabel lblNewLabel_6 = new JLabel(
				"Step 4: Provide the algorithm(s) parameter values, using ## to represent the value that will be varied:");
		lblNewLabel_6.setBounds(10, 208, 781, 14);
		getContentPane().add(lblNewLabel_6);

		textFieldOutputDirectory = new JTextField();
		textFieldOutputDirectory.setEditable(false);
		textFieldOutputDirectory.setBounds(213, 160, 496, 20);
		getContentPane().add(textFieldOutputDirectory);
		textFieldOutputDirectory.setColumns(10);

		JLabel lblNewLabel_7 = new JLabel("Step 3: Select output directory:");
		lblNewLabel_7.setBounds(10, 166, 193, 14);
		getContentPane().add(lblNewLabel_7);

		JButton btnNewButton = new JButton("...");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				askUserToChooseOutputDirectory();
			}
		});
		btnNewButton.setBounds(719, 161, 72, 23);
		getContentPane().add(btnNewButton);

		JLabel lblNewLabel_8 = new JLabel("Options:");
		lblNewLabel_8.setBounds(10, 350, 781, 14);
		getContentPane().add(lblNewLabel_8);

		JLabel lblNewLabel_9 = new JLabel("Path to SPMF.jar (optional):");
		lblNewLabel_9.setBounds(34, 399, 169, 14);
		getContentPane().add(lblNewLabel_9);

		textFieldJARPath = new JTextField();
		pathToSPMFJar = PreferencesManager.getInstance().getSPMFJarFilePath();
		textFieldJARPath.setText(pathToSPMFJar);
		textFieldJARPath.setEditable(false);
		textFieldJARPath.setBounds(213, 396, 496, 20);
		getContentPane().add(textFieldJARPath);
		textFieldJARPath.setColumns(10);

		JButton buttonSPMFJar = new JButton("...");
		buttonSPMFJar.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				askUserToChooseSPMFJarPath();
			}
		});
		buttonSPMFJar.setBounds(719, 395, 72, 23);
		getContentPane().add(buttonSPMFJar);

		comboBoxAlgorithms = new JComboBox<String>(new Vector<String>());
		comboBoxAlgorithms.setBounds(34, 40, 283, 22);
		// Combo box to store the list of algorithms.
		comboBoxAlgorithms.setMaximumRowCount(20);

		// ************************************************************************
		// ********* Use the algorithm manager to populate the list of algorithms
		// ******* //
		comboBoxAlgorithms.addItem("");

		AlgorithmManager manager = null;
		try {
			manager = AlgorithmManager.getInstance();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		List<String> algorithmList = manager.getListOfAlgorithmsAsString(false, false, false, true, false);
		for (String algorithmOrCategoryName : algorithmList) {
			comboBoxAlgorithms.addItem(algorithmOrCategoryName);
		}

		// ************************************************************************
		// ************************************************************************

		// What to do when the user choose an algorithm :
		comboBoxAlgorithms.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent evt) {
				// We need to update the user interface:
				try {
					updateUserInterfaceAfterAlgorithmSelection(evt.getItem().toString(),
							evt.getStateChange() == ItemEvent.SELECTED);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		});

		getContentPane().add(comboBoxAlgorithms);

		buttonAddAlgorithm = new JButton("Add algorithm");
		buttonAddAlgorithm.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					addAnAlgorithm();
				} catch (Exception e1) {
					JOptionPane.showMessageDialog(null,
							"An error occurred ERROR MESSAGE = " + e.toString(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		buttonAddAlgorithm.setBounds(327, 40, 176, 23);
		buttonAddAlgorithm.setEnabled(false);
		getContentPane().add(buttonAddAlgorithm);
		
		checkboxCountLines = new JCheckBox("Compare the number of lines in the output of each algorithm");
		checkboxCountLines.setBounds(34, 420, 578, 23);
		getContentPane().add(checkboxCountLines);
		
		buttonHelp = new JButton("");
		buttonHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://www.philippe-fournier-viger.com/spmf/ExperimenterPerformance.php"));
				} catch (java.io.IOException exception) {
					System.out.println(exception.getMessage());
				}
			}
		});
		buttonHelp.setIcon(new ImageIcon(ExperimenterWindow.class.getResource("/ca/pfv/spmf/gui/icons/About24.gif")));
		buttonHelp.setBounds(761, 11, 38, 34);
		getContentPane().add(buttonHelp);
		
		checkboxPGFPlots = new JCheckBox("Save results as Latex PGFPlots figures");
		checkboxPGFPlots.setBounds(34, 446, 466, 23);
		getContentPane().add(checkboxPGFPlots);
	}

	/**
	 * When the user clicks on the "Add algorithm" button
	 * @throws Exception if error occurs
	 */
	protected void addAnAlgorithm() throws Exception{
		// Get the current list of algorithms
		String algorithms = textFieldAlgorithms.getText();
		
		// Get the selected algorithm
		String newAlgorithm = (String) comboBoxAlgorithms.getSelectedItem();
		
		
		if(algorithms == null || "".equals(algorithms)) {
			textFieldAlgorithms.setText(newAlgorithm);
		}else {

			// Check if the new algorithm is already in the list
			String[] algorithmNames = algorithms.split(" ");
			boolean alreadyThere = false;
			for(String algorithm : algorithmNames) {
				if(algorithm.equals(newAlgorithm)) {
					alreadyThere = true;
				}
			}
			
			// WE WILL CHECK IF THE TWO ALGORITHMS HAVE THE SAME MANDATORY PARAMETERS
			// Get the first algorithm from the  list
			DescriptionOfAlgorithm descriptionOfAlgorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmNames[0]);
			DescriptionOfParameter[] description = descriptionOfAlgorithm.getParametersDescription();
			// Get the description of the new algorithm
			DescriptionOfAlgorithm newDescriptionOfAlgorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(newAlgorithm);
			DescriptionOfParameter[] newDescription = newDescriptionOfAlgorithm.getParametersDescription();
			
			boolean sameMandatoryParameters = true;
			
			if(descriptionOfAlgorithm.getNumberOfMandatoryParameters() != newDescriptionOfAlgorithm.getNumberOfMandatoryParameters()) {
				sameMandatoryParameters = false;
			}else {
				for(int i = 0; i<description.length; i++) {
					DescriptionOfParameter parameter = description[i];
					
					if(parameter.isOptional == false) {
						if(i < newDescription.length) {
							DescriptionOfParameter newParameter = newDescription[i];
							if(parameter.parameterType != newParameter.parameterType || !parameter.name.equals(newParameter.name)) {
								sameMandatoryParameters = false;
							}
						}else {
								sameMandatoryParameters = false;
						}
					}
				}
			}
			
			if(sameMandatoryParameters == false) {
				JOptionPane.showMessageDialog(null,
						"This algorithm (" + newAlgorithm + ") does not have the same mandatory parameters has other algorithms in this list (e.g. " + algorithmNames[0] + "). Hence, it cannot be added.", "Error",
						JOptionPane.ERROR_MESSAGE);
			}
						
					
			// If the algorithm is not already in the list, we dont need to add it again
			if(alreadyThere == false && sameMandatoryParameters == true) {
				textFieldAlgorithms.setText(algorithms + " " + newAlgorithm);
			}
		}

	}

	/**
	 * Inner class used to forward the console output to the result text area
	 * 
	 * @author Philippe Fournier-Viger
	 *
	 */
	static class TextAreaOutputStream extends OutputStream {

		/** the JTextArea */
		JTextArea textArea;

		/**
		 * Constructor
		 * 
		 * @param textArea a JTextArea
		 */
		public TextAreaOutputStream(JTextArea textArea) {
			this.textArea = textArea;
		}

		@Override
		public void flush() {
			textArea.repaint();
		}

		public void write(int b) {
			textArea.append(new String(new byte[] { (byte) b }));
		}
	}

	/**
	 * Run an experiment (when the user clicks the "Run experiment" button
	 **/
	protected void runExperiment() {
		// Deactivate the run button
		buttonRun.setEnabled(false);
		textAreaResult.setText("");

		try {
			// Create the tool to run an experiment
			ExperimenterForParameterChange experimenter = new ExperimenterForParameterChange();

			// If the user has specified a path to the spmf.jar file, we need to set it so
			// that
			// we will use it to run the algorithms.
			if (pathToSPMFJar != null) {
				System.out.println(pathToSPMFJar);
				experimenter.setSPMFJarFilePath(pathToSPMFJar);
			}

			// Get the list of algorithm names
			String spaceSeparatedAlgorithmNames = textFieldAlgorithms.getText();
			// If the list is empty, throw an error
			if (spaceSeparatedAlgorithmNames == null || "".equals(spaceSeparatedAlgorithmNames)) {
				throw new Exception("You must enter some algorithm names.");
			}
			String algorithmNames[] = spaceSeparatedAlgorithmNames.split(" ");

			// If input file is null, throw an error
			if ("".equals(inputFile)) {
				throw new Exception("You must select an input file.");
			}

			// If output directory is null, throw an error
			if ("".equals(outputDirectory)) {
				throw new Exception("You must select an output diretory.");
			}

			// Get the list of parameter values
			String spaceSeparatedParameters = textFieldParameters.getText();
			// If the list is empty, throw an error
			if (spaceSeparatedParameters == null || "".equals(spaceSeparatedParameters)) {
				throw new Exception("You must provide a list of parameters.");
			}
			String parameters[] = spaceSeparatedParameters.split(" ");
			
			// Which parameter is varied?
			int positionParameter = -1;
			for(int i = 0; i < parameters.length; i++) {
				if("##".equals(parameters[i])) {
					positionParameter = i;
				}
			}
			if (positionParameter == -1) {
				throw new Exception("The parameter to be varied should be identified with the code ## ");
			}
//			// Get the name of the varied parameter
//			String variedParameterName = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmNames[0]).getParametersDescription()[positionParameter].name;
//			

			// Find the values to be varied
			String spaceSeparatedVaryingValues = textFieldValues.getText();
			// If the list is empty, throw an error
			if (spaceSeparatedVaryingValues == null || "".equals(spaceSeparatedVaryingValues)) {
				throw new Exception("You must provide a list of values for the parameter that is varied.");
			}
			String varyingValues[] = spaceSeparatedVaryingValues.split(" ");

			// Get the time-out value specified by the user
			timeoutMilliseconds = Integer.MAX_VALUE;
			String timeoutAsString = textFieldTimeLimit.getText();
			// If there is a timeout value
			if ("".equals(timeoutAsString) == false) {
				// Check if it is a number
				try {
					timeoutMilliseconds = Integer.parseInt(timeoutAsString) * 1000;
				} catch (NumberFormatException f) {
					// If it is not a number, then throw an error
					throw new Exception(
							" Timeout must be an integer number representing a maximum duration in milliseconds.");
				}
			}

			// Redirect the console output to the JTextArea in this window so that the user
			// can see it
			System.setOut(new PrintStream(new TextAreaOutputStream(textAreaResult)));
			
			// Should we compare the output size of algorithms?
			boolean compareOutputSize = checkboxCountLines.isSelected();
			
			// Should PGFPLOTS FIGURES be generated?
			boolean generatePGFPLOTFigures = checkboxPGFPlots.isSelected();

			// Create a thread to run the experiment using a separated thread.
			// This is important so that the GUI will not be frozen during the experiment.
			currentRunningAlgorithmThread = new NotifyingThread() {
				@Override
				public boolean doRun() throws Exception {
					// Run the experiment
					experimenter.runAnAlgorithmAndVaryParameter(algorithmNames, parameters, varyingValues, inputFile,
							outputDirectory, timeoutMilliseconds, compareOutputSize, false, generatePGFPLOTFigures,"parameter");
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

		} catch (Exception e) {
			// If there is any error, it will be displayed to the user using a dialog
			JOptionPane.showMessageDialog(null,
					"An error occured while trying to run the experiment. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
			buttonRun.setEnabled(true);
		}

	}

	/**
	 * This method ask the user to choose the input file. This method is called when
	 * the user click on the button to choose the input file of the S P M F
	 * interface.
	 */
	private void askUserToChooseInputFile() {
		try {
			// WHEN THE USER CLICK TO CHOOSE THE INPUT FILE

			File path;
			// Get the last path used by the user, if there is one
			String previousPath = PreferencesManager.getInstance().getInputFilePath();
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
			int returnVal = fc.showSaveDialog(ExperimenterWindow.this);

			// If the user chose a file
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				textFieldInputFile.setText(file.getName());
				inputFile = file.getPath(); // save the file path
				// save the path of this folder for next time.
				if (fc.getSelectedFile() != null) {
					PreferencesManager.getInstance().setInputFilePath(fc.getSelectedFile().getParent());
				}
			}

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while opening the input file dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * This method ask the user to choose a path to SPMF.jar
	 */
	protected void askUserToChooseSPMFJarPath() {
		// pathToSPMFJar
		try {
			// WHEN THE USER CLICK TO CHOOSE THE OUTPUT FILE

			File path;
			// Get the last path used by the user, if there is one
			String previousPath = PreferencesManager.getInstance().getSPMFJarFilePath();
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
			int returnVal = fc.showSaveDialog(ExperimenterWindow.this);

			// If the user chose a file
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				textFieldJARPath.setText(file.getPath());
				pathToSPMFJar = file.getPath(); // save the file path
				// save the path of this folder for next time.
				if (fc.getSelectedFile() != null) {
					PreferencesManager.getInstance().setSPMFJarFilePath(fc.getSelectedFile().getPath());
				}
			}

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while opening the SPMF.jar file path dialog. ERROR MESSAGE = " + e.toString(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}

	}

	/**
	 * This method ask the user to choose the output directory. This method is
	 * called when the user click on the button to choose the output directory.
	 */
	private void askUserToChooseOutputDirectory() {
		try {
			// WHEN THE USER CLICK TO CHOOSE THE OUTPUT DIRECTORY

			File path;
			// Get the last path used by the user, if there is one
			String previousPath = PreferencesManager.getInstance().getExperimentDirectoryPath();
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
			final JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fc.setAcceptAllFileFilterUsed(false);
			if (path != null) {
				fc.setCurrentDirectory(new File(path.getAbsolutePath()));
			}
			int returnVal = fc.showSaveDialog(ExperimenterWindow.this);

			// If the user chose a file
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				textFieldOutputDirectory.setText(file.getName());
				outputDirectory = file.getAbsolutePath(); // save the file path
//				System.out.println(outputDirectory);
				// save the path of this folder for next time.
				if (fc.getSelectedFile() != null) {
					PreferencesManager.getInstance().setExperimentDirectoryPath(fc.getSelectedFile().getAbsolutePath());
				}
			}

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while opening the output file dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		// Activate the run button again
		buttonRun.setEnabled(true);
	}

	@Override
	public void notifyOfThreadComplete(Thread thread, boolean succeed) {
		// Activate the run button again
		buttonRun.setEnabled(true);
	}

	/**
	 * Method is called when the user selects something in the combo box
	 * 
	 * @param algorithmName the algorithm name.
	 * @throws Exception
	 * @boolean isSelected indicate if the algorithm has been selected or unselected
	 */
	protected void updateUserInterfaceAfterAlgorithmSelection(String algorithmName, boolean isSelected) throws Exception {
		// COMBOBOX ITEM SELECTION - ITEM STATE CHANGED
		if (isSelected) {

			// ************************************************************************
			// ********* Prepare the user interface for this algorithm ******* //

			AlgorithmManager manager = AlgorithmManager.getInstance();
			DescriptionOfAlgorithm algorithm = manager.getDescriptionOfAlgorithm(algorithmName);
			if (algorithm != null) {
				buttonAddAlgorithm.setEnabled(true);

			} else {
				buttonAddAlgorithm.setEnabled(false);
			}

		} else {
			buttonAddAlgorithm.setEnabled(false);
		}
	}
}
