package ca.pfv.spmf.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoClusterViewer;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoGraphViewerOpenFile;
import ca.pfv.spmf.algorithmmanager.descriptions.DescriptionAlgoSystemTextEditorOpenFile;
import ca.pfv.spmf.algorithms.timeseries.TimeSeries;
import ca.pfv.spmf.algorithms.timeseries.reader_writer.AlgoTimeSeriesReader;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;
import ca.pfv.spmf.gui.patternvizualizer.PatternVizualizer;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;
import ca.pfv.spmf.gui.viewers.timeseriesviewer.TimeSeriesViewer;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * This class is the user interface of SPMF (the main Window). It allows the
 * user to launch single algorithms.
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class MainWindow extends JFrame implements ThreadCompleteListener, UncaughtExceptionHandler {

	/** current input file */
	private String inputFile = null;

	/** The label of SPMF */
	private JLabel labelSPMF;

	/** current output file */
	private String outputFile = null;

	/** The content panel of this window */
	private JPanel contentPane;

	/** parameter selection panel */
	private ParameterSelectionPanel parameterPanel;

	/** Text field for input file path */
	private JTextField textFieldInput;

	/** Text field for output file path */
	private JTextField textFieldOutput;

	/** The JComboBox to select an algorithm */
	private JComboBox<String> comboBoxAlgorithms;

	/** The textArea to display the console output */
	private ConsolePanel consolePanel;

	/** The button to run an algorithm */
	private JButton buttonRun;

	/** The button to open an example from the documentation */
	private JButton buttonExample;

	/** The label asking to choose the output file */
	private JLabel lblSetOutputFile;

	/** The label asking to choose the input file */
	private JLabel lblSetInputFile;

	/** The button for selecting the output file */
	private JButton buttonOutput;

	/** The button for selecting the input file */
	private JButton buttonInput;

	/** The button to view and input file using a viewer */
	private JButton buttonViewInput;

	/** The progress bar when an algorithm is running */
	private JProgressBar progressBar;

	/** The current data mining task (used for running an algorithm as a thread */
	private static NotifyingThread currentRunningAlgorithmThread = null;

	/** the current data mining process */
	private static Process currentExternalProcess = null;

	/** The label for opening an output file */
	private JLabel lblOpenOutputFile;

	/** The combo box that let the user choose a method to view the output file */
	private JComboBox<String> comboBoxOutputFileMethod;

	/** The label about options for running an algorithm */
	private JLabel lblOptions;

	/** The checkbox : run an algorithm as external */
	private JCheckBox chckbxRunAsExternal;

	/** The textfield to enter the maximum number of seconds for an execution */
	private JTextField textMaxSeconds;

	/** The checkbox to set a time limit for an algorithm's execution */
	private JCheckBox chckbxMaxSeconds;

	/** The maximum time for an algorithm's execution */
	private int maxTime;

	private JLabel lblParameters;

	private JLabel lblChooseAnAlgorithm;

	/**
	 * Default constructor
	 * 
	 * @throws Exception if some error happens
	 */
	public MainWindow() throws Exception {
		this(true, true, true, true, true);
		setVisible(true);
	}

	public MainWindow(boolean showViewAndTransform, boolean showGenerateData, boolean showTools, boolean showAlgorithms, boolean showExperimentTools) throws Exception {
		setIconImage(Toolkit.getDefaultToolkit().getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));

		setResizable(true);

		// When the user clicks the "x" the software will close.
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Set the title of the window
		setTitleBasedOnFlags(showViewAndTransform, showGenerateData, showTools, showAlgorithms, showExperimentTools);

		contentPane = new JPanel();
		this.setContentPane(contentPane);
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		GridBagLayout gbl_contentPane = new GridBagLayout();
		contentPane.setLayout(gbl_contentPane);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(1, 1, 1, 1);

		// ----- SPMF logo ----
		labelSPMF = new JLabel(new ImageIcon(MainWindow.class.getResource("spmf.png")));
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		contentPane.add(labelSPMF, gbc);

		// ----- Choose an algorithm----
		lblChooseAnAlgorithm = new JLabel("Choose an algorithm:");
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = 1;
		contentPane.add(lblChooseAnAlgorithm, gbc);

		comboBoxAlgorithms = new JComboBox<>(new Vector<>());
		comboBoxAlgorithms.setMaximumRowCount(30);
		comboBoxAlgorithms.addItem("");
		// Assuming AlgorithmManager and showTools, showAlgorithms, showExperimentTools
		// are defined elsewhere
		List<String> algorithmList = AlgorithmManager.getInstance().getListOfAlgorithmsAsString(showViewAndTransform, showGenerateData, showTools,
				showAlgorithms, showExperimentTools);
		for (String algorithmOrCategoryName : algorithmList) {
			comboBoxAlgorithms.addItem(algorithmOrCategoryName);
		}
		AlgorithmComboBoxRenderer comboBoxRenderer = new AlgorithmComboBoxRenderer(comboBoxAlgorithms);
		comboBoxAlgorithms.setRenderer(comboBoxRenderer);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.gridwidth = 3;
		contentPane.add(comboBoxAlgorithms, gbc);

		buttonExample = new JButton("?");
		buttonExample.setEnabled(false);
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 4;
		gbc.gridy = 1;
		contentPane.add(buttonExample, gbc);

		// ----- Set input file ----
		lblSetInputFile = new JLabel("Choose input file:");
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = 2;
		contentPane.add(lblSetInputFile, gbc);

		textFieldInput = new JTextField();
		textFieldInput.setEditable(false);
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.gridwidth = 3;
		contentPane.add(textFieldInput, gbc);

		buttonInput = new JButton("...");
		buttonInput.setToolTipText("Select an input file");
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridwidth = 1;
		gbc.gridx = 4;
		gbc.gridy = 2;
		contentPane.add(buttonInput, gbc);

		buttonViewInput = new JButton("View");
//				new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/viewdata20.png")));
		buttonViewInput.setToolTipText("View the input file");
		buttonViewInput.setEnabled(false);
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridwidth = 1;
		gbc.gridx = 5;
		gbc.gridy = 2;
		contentPane.add(buttonViewInput, gbc);

		// ----- Set output file ----
		lblSetOutputFile = new JLabel("Set output file:");
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = 3;
		contentPane.add(lblSetOutputFile, gbc);

		textFieldOutput = new JTextField();
		textFieldOutput.setEditable(false);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 3;
		gbc.gridwidth = 3;
		contentPane.add(textFieldOutput, gbc);

		buttonOutput = new JButton("...");
		buttonOutput.setToolTipText("Select an output file");
		gbc.fill = GridBagConstraints.NONE;
		gbc.weighty = 0;
		gbc.gridwidth = 1;
		gbc.gridx = 4;
		gbc.gridy = 3;
		contentPane.add(buttonOutput, gbc);

		// Reset fill and weights for subsequent components
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 0;
		gbc.weighty = 0;

		// ----- Parameters ----
		lblParameters = new JLabel("Set parameters:");
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTHWEST; // Align to the top-left (north-west) of the cell
		gbc.weightx = 0;
		gbc.weighty = 0; // Set weighty to 0 to prevent vertical expansion
		gbc.gridx = 0;
		gbc.gridy = 4;
		contentPane.add(lblParameters, gbc);

		parameterPanel = new ParameterSelectionPanel(null);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 4;
		gbc.gridwidth = 3;
		gbc.gridheight = 1;
		contentPane.add(parameterPanel, gbc);

		// ----- Open output file using ----
		lblOpenOutputFile = new JLabel("Open output file using:");

		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = 5;
		contentPane.add(lblOpenOutputFile, gbc);

		comboBoxOutputFileMethod = new JComboBox<>(
				new String[] { "System text editor", "SPMF text editor", "Pattern viewer", "Don't open" });
		comboBoxOutputFileMethod.setMaximumRowCount(10);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 5;
		gbc.gridwidth = 3;
		contentPane.add(comboBoxOutputFileMethod, gbc);

		// ----- Options ----
		lblOptions = new JLabel("Options:");
		gbc.gridwidth = 1;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = 6;
		contentPane.add(lblOptions, gbc);

		chckbxRunAsExternal = new JCheckBox("Run in a separated process");
		boolean runAsExternal = PreferencesManager.getInstance().getRunAsExternalProgram();
		chckbxRunAsExternal.setSelected(runAsExternal);
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 6;
		contentPane.add(chckbxRunAsExternal, gbc);

		chckbxMaxSeconds = new JCheckBox("Time limit (s):");
		gbc.weighty = 0;
		gbc.gridx = 2;
		gbc.gridy = 6;
		contentPane.add(chckbxMaxSeconds, gbc);

		textMaxSeconds = new JTextField();
		textMaxSeconds.setEditable(false);
		textMaxSeconds.setVisible(false);
		textMaxSeconds.setEnabled(false);
		gbc.weighty = 0;
		gbc.gridx = 3;
		gbc.gridy = 6;
		contentPane.add(textMaxSeconds, gbc);

		// ----- Run algorithm ----
		buttonRun = new JButton("Run algorithm");
		buttonRun.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Play24.gif")));
		buttonRun.setEnabled(false);
		gbc.fill = GridBagConstraints.NONE; // This will prevent the button from filling the space in its cell
		gbc.anchor = GridBagConstraints.CENTER; // Keep the button centered
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 7;
		gbc.gridwidth = 3;
		gbc.anchor = GridBagConstraints.CENTER;
		contentPane.add(buttonRun, gbc);

		// ----- Progress bar ----
		progressBar = new JProgressBar();
		gbc.weighty = 0;
		gbc.gridx = 1;
		gbc.gridy = 8;
		gbc.gridwidth = 3;
		gbc.anchor = GridBagConstraints.CENTER;
		contentPane.add(progressBar, gbc);

		// Add a filler component with weighty = 1.0 at the end to push everything up
		gbc.gridx = 0;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.weighty = 0.001;
		contentPane.add(Box.createVerticalGlue(), gbc);

		// ----- Console ----
		consolePanel = new ConsolePanel(false);
		consolePanel.setPreferredSize(new Dimension(100, 100));
		gbc.gridx = 0;
		gbc.gridy = GridBagConstraints.RELATIVE; 
		gbc.gridwidth = 7;
		gbc.gridheight = GridBagConstraints.REMAINDER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1; // This allows horizontal resizing
		gbc.weighty = 0.5;
		contentPane.add(consolePanel, gbc);

		validate();
		pack();

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

		this.setLocationRelativeTo(null);

		// Load preference for text editor
		boolean useDefaultTextEditor = PreferencesManager.getInstance().getShouldUseSystemTextEditor();
		if (useDefaultTextEditor) {
			comboBoxOutputFileMethod.setSelectedItem("System text editor");
		} else {
			comboBoxOutputFileMethod.setSelectedItem("SPMF text editor");
		}

		// Initialize the event handling
		initializeUIEventHandling();

		// Prepare the user interface by hiding some components
		updateUserInterfaceAfterAlgorithmSelection(null, false);

//		repaint();
	}

	/**
	 * Initialize the UI event handling
	 */
	private void initializeUIEventHandling() {

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent arg0) {
				System.exit(0);
			}
		});

		chckbxRunAsExternal.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Save preference
				PreferencesManager.getInstance().setRunAsExternalProgram(chckbxRunAsExternal.isSelected());
				textMaxSeconds.setVisible(chckbxRunAsExternal.isSelected());
				chckbxMaxSeconds.setVisible(chckbxRunAsExternal.isSelected());
			}
		});
		chckbxMaxSeconds.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				textMaxSeconds.setEnabled(chckbxMaxSeconds.isSelected());
				textMaxSeconds.setEditable(chckbxMaxSeconds.isSelected());
			}
		});
		buttonRun.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				processRunAlgorithmCommandFromGUI();

			}
		});
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

		final MainWindow self = this;
		labelSPMF.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent arg0) {
				try {
					AboutWindow about = new AboutWindow(self);
					about.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		buttonInput.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				askUserToChooseFile(false);
			}
		});
		buttonViewInput.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				openInputFileWithViewer();
			}
		});
		buttonOutput.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				askUserToChooseFile(true);
			}
		});
		// What to do when the user choose an algorithm :
		comboBoxOutputFileMethod.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent evt) {
				// We need to update the user interface:
				try {
					updateUserInterfaceAfterOutputMethodSelected(evt.getItem().toString(),
							evt.getStateChange() == ItemEvent.SELECTED);
				} catch (Exception e) {
					e.printStackTrace();
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
	 * Set the title of the window
	 * 
	 * @param showTools           whether tools are shown
	 * @param showAlgorithms      whether algorithms are shown
	 * @param showExperimentTools whether experiment tools are shown
	 */
	private void setTitleBasedOnFlags(boolean showViewAndTransform, boolean showGenerateData,boolean showTools, boolean showAlgorithms, boolean showExperimentTools) {
		if (showViewAndTransform && showGenerateData && !showAlgorithms) {
			setTitle("Prepare data (run a dataset tool)");
		} else if (!showTools && showAlgorithms && !showExperimentTools) {
			setTitle("Run a data mining algorithm");
		} else if (!showTools && !showAlgorithms && showExperimentTools) {
			setTitle("Run an experiment");
		}else if (showViewAndTransform && !showAlgorithms) {
			setTitle("View and transform data");
		}else if (showGenerateData && !showAlgorithms) {
			setTitle("Generate data");
		} else {
			// set the title of the window
			setTitle("SPMF v" + Main.SPMF_VERSION);
		}
	}

	/**
	 * Update the user interface after the user choose a method to open the output
	 * file
	 * 
	 * @param method     the method name
	 * @param isSelected whether the element is selected in the combobox or not
	 *                   (true or false)
	 */
	protected void updateUserInterfaceAfterOutputMethodSelected(String method, boolean isSelected) {
		if (isSelected) {
			if ("System text editor".equals(method)) {
				PreferencesManager.getInstance().setShouldUseSystemTextEditor(true);
			} else if ("SPMF text editor".equals(method)) {
				PreferencesManager.getInstance().setShouldUseSystemTextEditor(false);
			}
		}
	}

	/**
	 * Open the selected input file with the corresponding viewer tool available in
	 * SPMF
	 */
	protected void openInputFileWithViewer() {
		try {
			if (inputFile == null || "".equals(inputFile)) {
				JOptionPane.showMessageDialog(null,
						"This button is for viewing an input file but none is selected. Please first, click on the \"...\" button to select an input file.",
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			String algorithmName = (String) comboBoxAlgorithms.getSelectedItem();
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
		DialogSelectAlgorithmParameter dialog = new DialogSelectAlgorithmParameter(paramDescription, this);
		// return the user input from the dialog
		return dialog.getUserInput();
	}

	/**
	 * This method updates the user interface according to what the user has
	 * selected or unselected in the list of algorithms. For example, if the user
	 * choose the "PrefixSpan" algorithm the parameters of the PrefixSpan algorithm
	 * will be shown in the user interface.
	 * 
	 * @param algorithmName the algorithm name.
	 * @throws Exception
	 * @boolean isSelected indicate if the algorithm has been selected or unselected
	 */
	private void updateUserInterfaceAfterAlgorithmSelection(String algorithmName, boolean isSelected) throws Exception {
		DescriptionOfAlgorithm algorithm = null;

		if (isSelected) {
			algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
		}
		// Update the different components of the user interface
		updateRunButton(algorithm);
		updateExampleButton(algorithm);
		updateParameterPanel(algorithm);
		updateInputFileComponents(algorithm);
		updateOutputFileComponents(algorithm);
		updateButtonViewInput(algorithm);
		updateComboBoxOutputOptions(algorithm);
		updateRunningOptions(algorithm);

		consolePanel.setVisible(algorithm != null);

	}

	/**
	 * Update the Run button after an algorithm is selected
	 * 
	 * @param algorithm the algorithm
	 */
	private void updateRunButton(DescriptionOfAlgorithm algorithm) {
		boolean visible = algorithm != null;
		if (visible) {
			buttonRun.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Play24.gif")));
		}
		buttonRun.setEnabled(visible);
		buttonRun.setVisible(visible);
		progressBar.setVisible(visible);
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

	/**
	 * Update the parameter panel after an algorithm is selected
	 * 
	 * @param algorithm the algorithm
	 */
	private void updateParameterPanel(DescriptionOfAlgorithm algorithm) {
		if (algorithm == null) {
			lblParameters.setVisible(false);
			parameterPanel.setVisible(false);
		} else {
			DescriptionOfParameter[] parameters = algorithm.getParametersDescription();
			if (parameters.length > 0) {
				lblParameters.setVisible(true);
				parameterPanel.setVisible(true);
			}
			parameterPanel.update(algorithm);
		}
	}

	/**
	 * Update the input file components of the UI after an algorithm is selected
	 * 
	 * @param algorithm the algorithm
	 */
	private void updateInputFileComponents(DescriptionOfAlgorithm algorithm) {
		boolean visible = algorithm != null && algorithm.getInputFileTypes() != null;
		lblSetInputFile.setVisible(visible);
		buttonInput.setVisible(visible);
		textFieldInput.setVisible(visible);
		buttonViewInput.setVisible(visible);
	}

	/**
	 * Update the output file components of the UI after an algorithm is selected
	 * 
	 * @param algorithm the algorithm
	 */
	private void updateOutputFileComponents(DescriptionOfAlgorithm algorithm) {
		boolean visible = algorithm != null && algorithm.getOutputFileTypes() != null;
		lblSetOutputFile.setVisible(visible);
		buttonOutput.setVisible(visible);
		textFieldOutput.setVisible(visible);
	}

	/**
	 * Update the visibility of options for running the algorithm
	 * 
	 * @param algorithm the algorithm
	 */
	private void updateRunningOptions(DescriptionOfAlgorithm algorithm) {
		boolean visible = algorithm != null && AlgorithmType.DATA_MINING.equals(algorithm.getAlgorithmType()) == true;
		lblOptions.setVisible(visible);
		chckbxRunAsExternal.setVisible(visible);
		chckbxMaxSeconds.setVisible(visible);
		if (visible) {
			textMaxSeconds.setVisible(chckbxRunAsExternal.isSelected());
			chckbxMaxSeconds.setVisible(chckbxRunAsExternal.isSelected());
		} else {
			chckbxMaxSeconds.setVisible(false);
		}
	}

	/**
	 * Update the "View" button after an algorithm is selected
	 * 
	 * @param algorithm the algorithm
	 * @throws Exception if some error occurs
	 */
	private void updateButtonViewInput(DescriptionOfAlgorithm algorithm) throws Exception {
		boolean hasInput = algorithm != null && algorithm.getInputFileTypes() != null;
		// Check if there is a viewer for this algorithm
		if (hasInput && algorithm.getAlgorithmType() != AlgorithmType.DATA_VIEWER) {
			String[] inputtypes = algorithm.getInputFileTypes();
			DescriptionOfAlgorithm viewer = AlgorithmManager.getInstance().getViewerFor(inputtypes);
			if (viewer != null) {
				buttonViewInput.setVisible(true);
			} else {
				buttonViewInput.setVisible(false);
			}
		} else {
			buttonViewInput.setVisible(false);
		}
	}

	/**
	 * Update the combobox Output File Method after an algorithm is selected
	 * 
	 * @param algorithm the selected algorithm
	 */
	private void updateComboBoxOutputOptions(DescriptionOfAlgorithm algorithm) {

		if (algorithm == null || algorithm.getOutputFileTypes() == null) {
			lblOpenOutputFile.setVisible(false);
			comboBoxOutputFileMethod.setVisible(false);
			return;
		}

		String[] outputFileTypes = algorithm.getOutputFileTypes();
		
		// Define default options that are always present
		String[] defaultOutputOptions;
		
		if(algorithm.getAlgorithmType().equals(AlgorithmType.OTHER_TOOL) || algorithm.getAlgorithmType().equals(AlgorithmType.OTHER_GUI_TOOL) || algorithm.getAlgorithmType().equals(AlgorithmType.DATA_GENERATOR)) {
			defaultOutputOptions = new String []{ "System text editor", "SPMF text editor"};
		}else {
			defaultOutputOptions = new String []{ "System text editor", "SPMF text editor", "Pattern viewer"};
		}

		int selectedIndex = comboBoxOutputFileMethod.getSelectedIndex();
		comboBoxOutputFileMethod.setModel(new DefaultComboBoxModel<>(defaultOutputOptions)); // Ensure model is
		// initialized
		
		// Check if there is a specific viewer for this type of pattern
		if(algorithm.getAlgorithmType() != AlgorithmType.DATA_VIEWER) {
			DescriptionOfAlgorithm viewer = null;;
			try {
				viewer = AlgorithmManager.getInstance().getViewerFor(outputFileTypes);
				comboBoxOutputFileMethod.addItem(viewer.getName());
			} catch (Exception e) {
			}
		}
		
		// Check if the itemset matrix viewer should be added
		for(String type: outputFileTypes) {
			if(type.contains("itemset")) {
				comboBoxOutputFileMethod.addItem("Visualize_With_ItemItemset_Matrix");
				break;
			}
		}
		
		// Check if the transition graph viewer should be added
		for(String type: outputFileTypes) {
			if(type.contains("sequential patterns")) {
				comboBoxOutputFileMethod.addItem("Visualize_sequential_patterns_with_transition_graph_viewer");
				break;
			}
		}
			
		comboBoxOutputFileMethod.addItem("Don't open");

		if (selectedIndex < defaultOutputOptions.length) {
			comboBoxOutputFileMethod.setSelectedIndex(selectedIndex);
		}

		if (outputFileTypes[0].equals("Time series database")) {
			comboBoxOutputFileMethod.removeItem("Pattern viewer");
			comboBoxOutputFileMethod.addItem("Time series viewer");
			comboBoxOutputFileMethod.setSelectedItem("Time series viewer");
		}

		if (outputFileTypes[0].equals("Clusters")) {
			comboBoxOutputFileMethod.addItem("Cluster viewer");
			comboBoxOutputFileMethod.setSelectedItem("Cluster viewer");
		}

		int lastIndex = outputFileTypes.length - 1;
		if (outputFileTypes[lastIndex].equals("Top-k Frequent subgraphs")
				|| outputFileTypes[lastIndex].equals("Frequent subgraphs")) {
			comboBoxOutputFileMethod.removeItem("Pattern viewer");
			comboBoxOutputFileMethod.addItem("Graph viewer");
			comboBoxOutputFileMethod.setSelectedItem("Graph viewer");
		}

		lblOpenOutputFile.setVisible(true);
		comboBoxOutputFileMethod.setVisible(true);
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
	 * Ask the user to choose a file (input file or output file)
	 * 
	 * @param isOutput if true, ask to choose an output file, otherwise ask to
	 *                 choose an input file
	 */
	private void askUserToChooseFile(boolean isOutput) {
		try {
			// Determine the path and preference based on input or output
			String previousPath = isOutput ? PreferencesManager.getInstance().getOutputFilePath()
					: PreferencesManager.getInstance().getInputFilePath();
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
			if (isOutput) {
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
			}

			// Show dialog based on input or output
			int returnVal = isOutput ? fc.showSaveDialog(MainWindow.this) : fc.showOpenDialog(MainWindow.this);

			// Process the result of the file chooser
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				if (isOutput) {
					textFieldOutput.setText(file.getName());
					outputFile = file.getPath();
					PreferencesManager.getInstance().setOutputFilePath(file.getParent());
				} else {
					textFieldInput.setText(file.getName());
					inputFile = file.getPath();
					PreferencesManager.getInstance().setInputFilePath(file.getParent());
				}
				buttonViewInput.setEnabled(true);
			} else {
				buttonViewInput.setEnabled(false);
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occurred while opening the file dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * This method receives a notifications when an algorithm terminates that was
	 * launched by the user by clicking "Run algorithm..."
	 */
	@Override
	public void notifyOfThreadComplete(Thread thread, boolean succeed) {

		// IF - the algorithm terminates... and there is an output file
		if (succeed && lblSetOutputFile.isVisible()) {

			// Open the viewer
			openSelectedViewer(comboBoxOutputFileMethod.getSelectedItem().toString(), outputFile);
		}

		resetUIAfterThreadCompletion();
	}

	private void openSelectedViewer(String viewerType, String filePath) {
		if(viewerType.equals("Don't open")) {
			return;
		}
		
		
		
		try {
			switch (viewerType) {
			case "System text editor":
				new DescriptionAlgoSystemTextEditorOpenFile().runAlgorithm(new String[] {}, filePath, null);
				break;
			case "SPMF text editor":
				new SPMFTextEditor(false).openAFile(new File(filePath));
				break;
			case "Pattern viewer":
				new PatternVizualizer(filePath);
				break;
			case "Time series viewer":
				openTimeSeriesViewer(filePath);
				break;
			case "Cluster viewer":
				new DescriptionAlgoClusterViewer().runAlgorithm(new String[] {}, filePath, null);
				break;
			case "Graph viewer":
				new DescriptionAlgoGraphViewerOpenFile().runAlgorithm(new String[] {}, filePath, null);
				break;
			default:
				try {
					AlgorithmManager.getInstance().getDescriptionOfAlgorithm(viewerType).runAlgorithm(new String[] {}, filePath, null);
				} catch (Exception e) {
					throw new UnsupportedOperationException("Viewer type not supported: " + viewerType);
				}
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "An error occurred while trying to open the output file with "
					+ viewerType + ". ERROR MESSAGE = " + e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Open a file with the time series viewer
	 * 
	 * @param filePath file path
	 * @throws IOException if an exception occurs
	 */
	private void openTimeSeriesViewer(String filePath) throws IOException {
		// We need to know what is the separator in the output file
		String separator = ",";
		// But we don't have that information

		// So we need to do a hack to find what is the file separator used in the output
		// file...
		// We will check all the field of the user interface to find the separator
		final String[] parameters = parameterPanel.getParameterValues();
		for (int i = 0; i < parameters.length; i++) {
			if (parameters[i].equals("Separator")) {
				separator = parameters[i];
			}
		}

		AlgoTimeSeriesReader reader = new AlgoTimeSeriesReader();
		List<TimeSeries> timeSeries = reader.runAlgorithm(outputFile, separator);
		TimeSeriesViewer viewer = new TimeSeriesViewer(timeSeries);
		viewer.setVisible(true);
	}

	/**
	 * Reset the UI after thread completion
	 */
	private void resetUIAfterThreadCompletion() {
		buttonRun.setText("Run algorithm");
		buttonRun.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Play24.gif")));
		progressBar.setIndeterminate(false);
		comboBoxAlgorithms.setEnabled(true);
		chckbxMaxSeconds.setEnabled(true);
		chckbxRunAsExternal.setEnabled(true);
	}

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
	 * This method is called when the user click the "Run" or "Stop" button of the
	 * user interface, to launch the chosen algorithm and thereafter catch exception
	 * if one occurs.
	 */
	private void processRunAlgorithmCommandFromGUI() {
		
		if (PreferencesManager.getInstance().getRunAsExternalProgram()) {
			File file = new File("spmf.jar");
			if(file.exists() == false) {
				JOptionPane.showMessageDialog(null, "The algorithm cannot be run in a separated process because spmf.jar is not found. It will be run in the same process.", "Error", JOptionPane.ERROR_MESSAGE);
				chckbxRunAsExternal.setSelected(false);
				PreferencesManager.getInstance().setRunAsExternalProgram(false);
			}
		}

		// If the algorithm is running, try to kill it
		boolean killed = tryToKillProcess();
		if (killed) {
			return;
		}

		// Get the parameters
		final String choice = (String) comboBoxAlgorithms.getSelectedItem();

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
			comboBoxAlgorithms.setEnabled(false);
			chckbxMaxSeconds.setEnabled(false);
			chckbxRunAsExternal.setEnabled(false);
		}

		// RUN THE SELECTED ALGORITHM in a new thread
		// create a thread to execute the algorithm
		if (PreferencesManager.getInstance().getRunAsExternalProgram()) {
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
					if (inputFile != null) {
						commandWithParameters.add(inputFile);
					}
					if (outputFile != null) {
						commandWithParameters.add(outputFile);
					}

					final String[] parameters = parameterPanel.getParameterValues();
					for (int i = 0; i < parameters.length; i++) {
						if (parameters[i] != null && !parameters[i].isEmpty()) {
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
					pb.redirectOutput(ProcessBuilder.Redirect.PIPE);    //  BUG FIX 2024-5-20
					pb.redirectError(ProcessBuilder.Redirect.PIPE);     //  BUG FIX 2024-5-20
					
					

					int exitValue = 1;
					try {
						currentExternalProcess = pb.start();
						
					    // Capture the output stream
					    BufferedReader reader = new BufferedReader(new InputStreamReader(currentExternalProcess.getInputStream()));
					    String line;
					    while ((line = reader.readLine()) != null) {
					        // Update this part to integrate with your GUI console
					        consolePanel.appendLine(line); // Replace with GUI console output method
					    }
						
						exitValue = currentExternalProcess.waitFor();
					} catch (IOException e) {
						throw new IllegalArgumentException(
								System.lineSeparator() + System.lineSeparator() + "I/O Error.");
					}
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
			final String[] parameters = parameterPanel.getParameterValues();

			// If the algorithm is run as a thread
			currentRunningAlgorithmThread = new NotifyingThread() {
				@Override
				public boolean doRun() throws Exception {
					CommandProcessor.runAlgorithm(choice, inputFile, outputFile, parameters);
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
			
			consolePanel.redirectOutputStream();
		}

		// If the user set a max time limit, we launch a thread to monitor and kill the
		// process
		// if the time limit is exceeded
		if (chckbxMaxSeconds.isSelected()) {
			// get the maximum amount of itme
			maxTime = -1;
			try {
				maxTime = Integer.parseInt(textMaxSeconds.getText());
			} catch (NumberFormatException exception) {

			}
			// if the maximum amount of time is a valid number
			if (maxTime > 0) {
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
			resetUIAfterThreadCompletion();
			return true;
		}
		// If a thread is already running (the user click on the stop Button
		else if (currentRunningAlgorithmThread != null && currentRunningAlgorithmThread.isAlive()) {
			// stop that thread
			try {
				currentRunningAlgorithmThread.stop();
				consolePanel.postStatusMessage("Algorithm stopped. \n");
				resetUIAfterThreadCompletion();
				return true;
			} catch (java.lang.UnsupportedOperationException e) {
				JOptionPane.showMessageDialog(null,
						"Stopping an algorithm is not supported for Java version " + System.getProperty("java.version"), "Error",
						JOptionPane.ERROR_MESSAGE);
				return true;
			}
		}
		return false;
	}
} // S P M F
