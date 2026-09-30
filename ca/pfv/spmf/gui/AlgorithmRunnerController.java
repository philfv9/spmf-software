package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license
 */
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
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
import ca.pfv.spmf.gui.viewers.arffviewer.ARFFDatabaseViewer;
import ca.pfv.spmf.gui.viewers.csvviewer.CSVDatabaseViewer;
import ca.pfv.spmf.gui.viewers.fastaviewer.FastaViewer;
import ca.pfv.spmf.gui.viewers.timeseriesviewer.TimeSeriesViewer;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * Controller for {@link AlgorithmRunnerPanel}. Wires all event handlers and
 * implements the business logic for selecting algorithms, choosing files,
 * running or stopping executions, and opening result viewers.
 * <p>
 * Recent-algorithm tracking: every time the user successfully starts an
 * algorithm run the algorithm name is recorded in
 * {@link PreferencesManager#addRecentAlgorithm(String)} so that the "Recent ▼"
 * popup next to the algorithm combo box can offer quick re-selection.
 * <p>
 * Completion feedback: whenever an algorithm run finishes — whether it
 * succeeded or was stopped by the user / time limit —
 * {@link #notifyOfThreadComplete} makes the Output panel flash briefly via
 * {@link AlgorithmRunnerPanel#flashOutputPanel()} and, if the user has checked
 * the "Beep on completion" checkbox, plays a short system beep. This feedback
 * is only given for algorithms that actually produce an output file (i.e.
 * {@link DescriptionOfAlgorithm#getOutputFileTypes()} is non-null), via
 * {@link #algorithmHasOutputFile(String)} — algorithms that have no output
 * (e.g. some tools or viewers) are silently skipped, since there is nothing
 * meaningful to preview or announce for them.
 * <p>
 * Running feedback: to give the user a clear visual cue that an algorithm is
 * actively running (as opposed to simply being finished), the Output panel is
 * cleared as soon as a run that will produce an output file is started (see
 * {@link #processRunAlgorithmCommandFromGUI()}). When the run terminates,
 * {@link #notifyOfThreadComplete} re-populates the Output panel with a preview
 * of the freshly generated output file. Together, this clear-then-populate
 * sequence makes it obvious to the user that the algorithm is running and has
 * completed its work, even for very fast runs.
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgorithmRunnerController implements ThreadCompleteListener, UncaughtExceptionHandler {

	/** The panel whose widgets this controller manages. */
	private final AlgorithmRunnerPanel panel;

	/** The runner that owns the actual thread and process references. */
	private final AlgorithmRunner runner;

	/** The parent frame, used for dialog ownership. */
	private final JFrame parentFrame;

	/** The current input file path selected by the user. */
	private String inputFile = null;

	/** The current output file path selected by the user. */
	private String outputFile = null;

	/** The maximum time limit in seconds for an algorithm execution. */
	private int maxTime;

	/**
	 * Wall-clock time in milliseconds at which the current algorithm run started.
	 */
	private long runStartTimeMillis = 0L;

	/** Snapshot of the parameter values passed to the most-recently started run. */
	private String[] lastParameters = new String[0];

	/** The name of the algorithm that was most recently started. */
	private String lastAlgorithmName = "";

	/** The output file path of the most recently started algorithm run. */
	private String lastOutputFile = "";

	/**
	 * Flag set to {@code true} when the user clicks the Stop button or when the
	 * time limit expires.
	 */
	private volatile boolean wasStoppedByUser = false;

	// =========================================================================
	// Construction
	// =========================================================================

	/**
	 * Constructs a controller that manages the given panel inside the given parent
	 * frame.
	 * 
	 * @param panel       the {@link AlgorithmRunnerPanel} to control
	 * @param parentFrame the owning {@link JFrame} (used for dialog placement)
	 */
	public AlgorithmRunnerController(AlgorithmRunnerPanel panel, JFrame parentFrame) {
		this.panel = panel;
		this.parentFrame = parentFrame;
		this.runner = new AlgorithmRunner(panel);
		this.runner.setController(this);
	}

	// =========================================================================
	// Listener wiring
	// =========================================================================

	/**
	 * Registers all event listeners on the panel's widgets. Must be called exactly
	 * once, after the panel has been added to its frame.
	 */
	public void initializeUIEventHandling() {

		// ---- Run-as-external checkbox -----------------------------------
		panel.addRunAsExternalListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				PreferencesManager.getInstance().setRunAsExternalProgram(panel.isRunAsExternal());
				panel.setTimeLimitWidgetsVisible(panel.isRunAsExternal());
			}
		});

		// ---- Time-limit checkbox ----------------------------------------
		panel.addMaxSecondsCheckboxListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				panel.setMaxSecondsFieldEnabled(panel.isTimeLimitEnabled());
			}
		});

		// ---- Run / Stop button ------------------------------------------
		panel.addRunButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				processRunAlgorithmCommandFromGUI();
			}
		});

		// ---- Select algorithm button — opens the searchable category-tree dialog
		panel.addSelectAlgorithmButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				openAlgorithmSelectorDialog();
			}
		});

		// ---- SPMF logo --------------------------------------------------
		panel.addLogoMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent arg0) {
				try {
					AboutWindow about = new AboutWindow(parentFrame);
					about.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// ---- Input file Browse button ------------------------------------
		panel.addInputButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				askUserToChooseFile(false);
			}
		});

		// ---- View input file button --------------------------------------
		panel.addViewInputButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				openInputFileWithViewer();
			}
		});

		// ---- Output file Browse button -----------------------------------
		panel.addOutputButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				askUserToChooseFile(true);
			}
		});

		// ---- Recent input files popup -----------------------------------
		panel.addRecentInputButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showRecentFilesMenu(false);
			}
		});

		// ---- Recent output files popup ----------------------------------
		panel.addRecentOutputButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showRecentFilesMenu(true);
			}
		});

		// ---- Recent algorithms popup ------------------------------------
		panel.addRecentAlgorithmButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showRecentAlgorithmsMenu();
			}
		});

		// ---- Documentation button ---------------------------------------
		panel.addExampleButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String choice = panel.getSelectedAlgorithmName();
				try {
					openHelpWebPageForAlgorithm(choice);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// ---- Input file text field (direct typing) ----------------------
		panel.addInputFileTextListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				syncInputFileFromTextField();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				syncInputFileFromTextField();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				syncInputFileFromTextField();
			}
		});

		// ---- Output file text field (direct typing) ---------------------
		panel.addOutputFileTextListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				syncOutputFileFromTextField();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				syncOutputFileFromTextField();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				syncOutputFileFromTextField();
			}
		});

		// ---- Run history re-run -----------------------------------------
		panel.addRerunHistoryListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				RunHistoryEntry entry = panel.getSelectedHistoryEntry();
				rerunHistoryEntry(entry);
			}
		});

		// ---- Run history clear ------------------------------------------
		panel.addClearHistoryListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				panel.clearHistory();
			}
		});

		// ---- Output tab "Open using:" button ----------------------------
		panel.addOutputTabViewButtonListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String viewerMethod = panel.getOutputTabSelectedViewerMethod();
				openSelectedViewer(viewerMethod, lastOutputFile);
			}
		});
	}

	// ===================================
	// Validation
	// ===================================
	/**
	 * Validates that the algorithm can actually be run with the values currently
	 * entered in the UI, before the run is launched. This avoids letting the
	 * algorithm start and crash with a cryptic low-level error (e.g. a
	 * NullPointerException or FileNotFoundException) when the real cause is simply
	 * that the user forgot to fill in a required field.
	 * <p>
	 * Three things are checked, using {@link DescriptionOfAlgorithm} directly as
	 * the source of truth (rather than the UI's row-visibility state, which is only
	 * a downstream reflection of the same information):
	 * <ul>
	 * <li>All required (non-optional) parameters have a value, via
	 * {@link ParameterSelectionPanel#getMissingRequiredParameterNames()} (exposed
	 * on the panel).</li>
	 * <li>An input file has been selected, if
	 * {@link DescriptionOfAlgorithm#getInputFileTypes()} is non-null.</li>
	 * <li>An output file has been selected, if
	 * {@link DescriptionOfAlgorithm#getOutputFileTypes()} is non-null.</li>
	 * </ul>
	 * If any problems are found, a single dialog lists all of them so the user can
	 * fix everything in one pass instead of being interrupted repeatedly.
	 *
	 * @param algorithmName the name of the algorithm about to be run
	 * @return {@code true} if the run can proceed, {@code false} if it was blocked
	 *         and a warning dialog was shown
	 */
	private boolean validateBeforeRun(String algorithmName) {
		List<String> problems = new ArrayList<>();

		if (algorithmName == null || algorithmName.trim().isEmpty()) {
			problems.add("No algorithm has been selected.");
			return reportProblemsIfAny(problems);
		}

		// Missing required parameters — always checked regardless of
		// whether the algorithm lookup below succeeds.
		List<String> missingParams = panel.getMissingRequiredParameterNames();
		if (!missingParams.isEmpty()) {
			problems.add("Missing required parameter(s): " + String.join(", ", missingParams));
		}

		// Ask DescriptionOfAlgorithm directly whether an input/output file
		// is required, rather than relying on the UI's row-visibility state.
		try {
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);

			if (algorithm != null) {
				boolean needsInput = algorithm.getInputFileTypes() != null;
				boolean needsOutput = algorithm.getOutputFileTypes() != null;

				if (needsInput && (inputFile == null || inputFile.trim().isEmpty())) {
					problems.add("No input file has been selected.");
				}
				if (needsOutput && (outputFile == null || outputFile.trim().isEmpty())) {
					problems.add("No output file has been selected.");
				}
			}
		} catch (Exception ex) {
			// If the algorithm cannot be looked up, we cannot reliably tell
			// whether an input/output file is required, so those two checks
			// are simply skipped rather than risking a false-positive
			// warning that would block a legitimate run.
		}

		return reportProblemsIfAny(problems);
	}

	/**
	 * Shows a single warning dialog listing every entry in {@code problems}, if
	 * any, and returns whether the run may proceed.
	 *
	 * @param problems the list of human-readable problem descriptions
	 * @return {@code true} if {@code problems} is empty (run may proceed),
	 *         {@code false} otherwise (a dialog was shown)
	 */
	private boolean reportProblemsIfAny(List<String> problems) {
		if (problems.isEmpty()) {
			return true;
		}

		StringBuilder message = new StringBuilder("The algorithm cannot be run for the following reason(s):\n\n");
		for (String problem : problems) {
			message.append("\u2022 ").append(problem).append('\n');
		}
		message.append("\nPlease fix the above and try again.");

		JOptionPane.showMessageDialog(parentFrame, message.toString(), "Cannot run algorithm",
				JOptionPane.WARNING_MESSAGE);
		return false;
	}

	// =========================================================================
	// Recent-files / recent-algorithms popup menus
	// =========================================================================

	/**
	 * Builds and shows a popup menu of recently used files anchored to the
	 * appropriate "Recent ▼" button.
	 * 
	 * @param isOutput {@code true} for recent output files, {@code false} for input
	 */
	private void showRecentFilesMenu(boolean isOutput) {
		List<String> recentFiles = isOutput ? PreferencesManager.getInstance().getRecentOutputFiles()
				: PreferencesManager.getInstance().getRecentInputFiles();

		JPopupMenu menu = new JPopupMenu();

		if (recentFiles.isEmpty()) {
			JMenuItem emptyItem = new JMenuItem("(no recent files)");
			emptyItem.setEnabled(false);
			menu.add(emptyItem);
		} else {
			for (String filePath : recentFiles) {
				String displayName = new File(filePath).getName();
				JMenuItem item = new JMenuItem(displayName);
				item.setToolTipText(filePath);

				final String fullPath = filePath;
				item.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if (isOutput) {
							panel.setOutputFileName(fullPath);
							outputFile = fullPath;
						} else {
							panel.setInputFileName(fullPath);
							inputFile = fullPath;
							panel.setViewInputButtonEnabled(true);
						}
					}
				});
				menu.add(item);
			}
		}

		javax.swing.JButton anchor = isOutput ? panel.getRecentOutputButton() : panel.getRecentInputButton();
		menu.show(anchor, 0, anchor.getHeight());
	}

	/**
	 * Builds and shows a popup menu of recently used algorithm names anchored to
	 * the "Recent ▼" button. Only algorithms that match the current category
	 * filters are shown.
	 */
	private void showRecentAlgorithmsMenu() {
		List<String> recentAlgorithms = PreferencesManager.getInstance().getRecentAlgorithms();

		JPopupMenu menu = new JPopupMenu();

		// Filter the recent algorithms based on the category flags
		List<String> filteredAlgorithms = new ArrayList<>();

		for (String algorithmName : recentAlgorithms) {
			try {
				DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance()
						.getDescriptionOfAlgorithm(algorithmName);

				if (algorithm != null && isAlgorithmInAllowedCategories(algorithm)) {
					filteredAlgorithms.add(algorithmName);
				}
			} catch (Exception ex) {
				// Algorithm not found or error — skip it
			}
		}

		if (filteredAlgorithms.isEmpty()) {
			JMenuItem emptyItem = new JMenuItem("(no recent algorithms)");
			emptyItem.setEnabled(false);
			menu.add(emptyItem);
		} else {
			for (String algorithmName : filteredAlgorithms) {
				JMenuItem item = new JMenuItem(algorithmName);
				item.setToolTipText("Select " + algorithmName);

				final String name = algorithmName;
				item.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						panel.setSelectedAlgorithm(name);

						try {
							DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance()
									.getDescriptionOfAlgorithm(name);
							panel.updateUserInterfaceForAlgorithm(algorithm);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				});
				menu.add(item);
			}
		}

		javax.swing.JButton anchor = panel.getRecentAlgorithmButton();
		menu.show(anchor, 0, anchor.getHeight());
	}

	/**
	 * Returns {@code true} if the given algorithm belongs to one of the allowed
	 * categories based on the current filter flags.
	 * <p>
	 * The category strings are matched against the actual values returned by
	 * {@link DescriptionOfAlgorithm#getAlgorithmCategory()}, which were determined
	 * by inspecting the AlgorithmManager at runtime.
	 *
	 * @param algorithm the algorithm to check
	 * @return {@code true} if the algorithm should be visible given the current
	 *         flags
	 */
	private boolean isAlgorithmInAllowedCategories(DescriptionOfAlgorithm algorithm) {
		if (algorithm == null) {
			return false;
		}

		String category = algorithm.getAlgorithmCategory();
		if (category == null) {
			return false;
		}

		// Ask AlgorithmManager directly using the same logic as AlgorithmSelectorDialog
		try {
			boolean isViewOrTransform = AlgorithmManager.getInstance().isViewOrTransformAlgorithm(algorithm);
			boolean isGenerateData = AlgorithmManager.getInstance().isDataGeneratorAlgorithm(algorithm);
			boolean isTool = AlgorithmManager.getInstance().isToolAlgorithm(algorithm);
			boolean isExperiment = AlgorithmManager.getInstance().isExperimentAlgorithm(algorithm);
			// Everything else is a data-mining algorithm
			boolean isDataMining = !isViewOrTransform && !isGenerateData && !isTool && !isExperiment;

			if (isViewOrTransform && panel.getShowViewAndTransform())
				return true;
			if (isGenerateData && panel.getShowGenerateData())
				return true;
			if (isTool && panel.getShowTools())
				return true;
			if (isExperiment && panel.getShowExperimentTools())
				return true;
			if (isDataMining && panel.getShowAlgorithms())
				return true;

		} catch (Exception ex) {
			// AlgorithmManager does not expose those helpers — fall through
			// to the manual category-string approach below.
		}

		return false;
	}
	// =========================================================================
	// Input-file viewer
	// =========================================================================

	/**
	 * Opens the currently selected input file using the viewer registered for the
	 * selected algorithm's input file types. Extension-based overrides are applied
	 * first.
	 */
	protected void openInputFileWithViewer() {
		try {
			if (inputFile == null || inputFile.isEmpty()) {
				JOptionPane.showMessageDialog(null,
						"This button is for viewing an input file but none is "
								+ "selected. Please first click the Select button " + "to select an input file.",
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			if (inputFile.toLowerCase().endsWith(".arff")) {
				new ARFFDatabaseViewer(true, inputFile);
				return;
			}
			if (inputFile.toLowerCase().endsWith(".csv")) {
				new CSVDatabaseViewer(true, inputFile);
				return;
			}
			if (isFastaFile(inputFile)) {
				FastaViewer fastaViewer = new FastaViewer(false);
				fastaViewer.setVisible(true);
				fastaViewer.load(inputFile);
				return;
			}

			String algorithmName = panel.getSelectedAlgorithmName();
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			DescriptionOfAlgorithm viewer = AlgorithmManager.getInstance().getViewerFor(algorithm.getInputFileTypes());

			String[] params;
			if (viewer.getParametersDescription().length > 0) {
				params = new String[viewer.getParametersDescription().length];
				for (int i = 0; i < params.length; i++) {
					params[i] = askUserValueForParameter(viewer.getParametersDescription()[i]);
				}
			} else {
				params = new String[] {};
			}
			viewer.runAlgorithm(params, inputFile, null);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null,
					"The output file failed to open with the default application." + "\n\n ERROR MESSAGE = " + e,
					"Error", JOptionPane.ERROR_MESSAGE);
		} catch (SecurityException e) {
			JOptionPane.showMessageDialog(null, "A security error occurred. ERROR MESSAGE = " + e, "Error",
					JOptionPane.ERROR_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "An error occurred. ERROR MESSAGE = " + e, "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the input file recorded in a history entry using the appropriate
	 * viewer. Falls back to the system text editor if no specific viewer is
	 * registered.
	 * 
	 * @param entry the history entry whose input file should be opened
	 */
	private void openHistoryInputFileWithViewer(RunHistoryEntry entry) {
		String filePath = entry.getInputFile();

		if (filePath == null || filePath.isEmpty()) {
			JOptionPane.showMessageDialog(null, "No input file was recorded for this run.", "No input file",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		try {
			if (filePath.toLowerCase().endsWith(".arff")) {
				new ARFFDatabaseViewer(true, filePath);
				return;
			}
			if (filePath.toLowerCase().endsWith(".csv")) {
				new CSVDatabaseViewer(true, filePath);
				return;
			}
			if (isFastaFile(filePath)) {
				FastaViewer fastaViewer = new FastaViewer(false);
				fastaViewer.setVisible(true);
				fastaViewer.load(filePath);
				return;
			}

			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance()
					.getDescriptionOfAlgorithm(entry.getAlgorithmName());

			if (algorithm != null && algorithm.getInputFileTypes() != null) {
				DescriptionOfAlgorithm viewer = null;
				try {
					viewer = AlgorithmManager.getInstance().getViewerFor(algorithm.getInputFileTypes());
				} catch (Exception ex) {
					// No registered viewer — fall through to text-editor fallback
				}

				if (viewer != null) {
					String[] params;
					if (viewer.getParametersDescription().length > 0) {
						params = new String[viewer.getParametersDescription().length];
						for (int i = 0; i < params.length; i++) {
							params[i] = askUserValueForParameter(viewer.getParametersDescription()[i]);
						}
					} else {
						params = new String[] {};
					}
					viewer.runAlgorithm(params, filePath, null);
					return;
				}
			}

			// Fallback: open with the system text editor
			new DescriptionAlgoSystemTextEditorOpenFile().runAlgorithm(new String[] {}, filePath, null);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "The input file failed to open. ERROR MESSAGE = " + e, "Error",
					JOptionPane.ERROR_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "An error occurred. ERROR MESSAGE = " + e, "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// =========================================================================
	// Small helpers
	// =========================================================================

	/**
	 * Returns {@code true} if {@code filePath} has a recognised FASTA extension.
	 */
	private boolean isFastaFile(String filePath) {
		String lower = filePath.toLowerCase();
		return lower.endsWith(".fasta") || lower.endsWith(".fa") || lower.endsWith(".fna") || lower.endsWith(".ffn");
	}

	/**
	 * Returns {@code true} if the named algorithm produces an output file, i.e.
	 * {@link DescriptionOfAlgorithm#getOutputFileTypes()} returns a non-null array.
	 * Used to decide whether completion feedback (Output-panel flash and optional
	 * beep) should be shown — algorithms without an output (some tools, viewers,
	 * etc.) have nothing to preview or announce, so the feedback is skipped for
	 * them. It is also used to decide whether the Output panel should be cleared
	 * when the run starts (see {@link #processRunAlgorithmCommandFromGUI()}).
	 *
	 * <p>
	 * If the algorithm cannot be found or {@link AlgorithmManager} throws for any
	 * reason, this method conservatively returns {@code false} so that no feedback
	 * is given rather than risk a {@link NullPointerException} or misleading
	 * behaviour.
	 * </p>
	 *
	 * @param algorithmName the name of the algorithm to check
	 * @return {@code true} if the algorithm has an output file type declared
	 */
	private boolean algorithmHasOutputFile(String algorithmName) {
		if (algorithmName == null || algorithmName.trim().isEmpty()) {
			return false;
		}
		try {
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			return algorithm != null && algorithm.getOutputFileTypes() != null;
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * Shows a dialog so the user can supply a value for a single algorithm
	 * parameter.
	 */
	private String askUserValueForParameter(DescriptionOfParameter paramDescription) {
		DialogSelectAlgorithmParameter dialog = new DialogSelectAlgorithmParameter(paramDescription, parentFrame);
		return dialog.getUserInput();
	}

	/**
	 * Opens the documentation web page for the given algorithm in the system
	 * default browser.
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

	// =========================================================================
	// File chooser
	// =========================================================================

	/**
	 * Opens a file-chooser dialog so the user can pick an input or output file.
	 * 
	 * @param isOutput {@code true} for the output file, {@code false} for input
	 */
	private void askUserToChooseFile(boolean isOutput) {
		try {
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

			final JFileChooser fc = (path != null) ? new JFileChooser(path.getAbsolutePath()) : new JFileChooser();

			if (isOutput) {
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
			}

			int returnVal = isOutput ? fc.showSaveDialog(parentFrame) : fc.showOpenDialog(parentFrame);

			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				if (isOutput) {
					panel.setOutputFileName(file.getPath());
					outputFile = file.getPath();
					PreferencesManager.getInstance().setOutputFilePath(file.getParent());
					PreferencesManager.getInstance().addRecentOutputFile(file.getPath());
				} else {
					panel.setInputFileName(file.getPath());
					inputFile = file.getPath();
					PreferencesManager.getInstance().setInputFilePath(file.getParent());
					PreferencesManager.getInstance().addRecentInputFile(file.getPath());
				}
				panel.setViewInputButtonEnabled(true);
			} else {
				if (!isOutput && (inputFile == null || inputFile.isEmpty())) {
					panel.setViewInputButtonEnabled(false);
				}
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occurred while opening the file dialog. " + "ERROR MESSAGE = " + e, "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// =========================================================================
	// ThreadCompleteListener
	// =========================================================================

	/**
	 * Called by the {@link NotifyingThread} when the algorithm thread finishes.
	 * <p>
	 * If the run succeeded or was stopped by the user / time limit, a
	 * {@link RunHistoryEntry} is created and forwarded to the panel's history
	 * panel. Runs that fail due to bad parameters or other errors are not recorded.
	 * </p>
	 * <p>
	 * Regardless of the outcome, this method also gives the user visible and
	 * (optionally) audible feedback that the run has finished — but only if the
	 * algorithm that was run actually produces an output file, as determined by
	 * {@link #algorithmHasOutputFile(String)}. Algorithms without an output (some
	 * tools, viewers, etc.) have nothing to preview or announce, so no flash or
	 * beep is triggered for them. When applicable, the Output panel is made to
	 * flash briefly via {@link AlgorithmRunnerPanel#flashOutputPanel()}, and if the
	 * "Beep on completion" checkbox is selected, a short system beep is played.
	 * This prevents the user from missing very fast runs (e.g. those completing in
	 * 1 ms) that would otherwise leave the interface looking unchanged.
	 * </p>
	 * <p>
	 * Note: the Output panel is deliberately <em>not</em> cleared here. It was
	 * already cleared when the run was started (see
	 * {@link #processRunAlgorithmCommandFromGUI()}), so that the sequence seen by
	 * the user is: panel cleared at start → panel shows the output preview here at
	 * completion. Clearing it again at this point would immediately erase the
	 * preview that was just populated below.
	 * </p>
	 * 
	 * @param thread  the thread that completed
	 * @param succeed {@code true} if the algorithm finished without an exception
	 */
	@Override
	public void notifyOfThreadComplete(Thread thread, boolean succeed) {
		long durationMillis = System.currentTimeMillis() - runStartTimeMillis;

		if (succeed || wasStoppedByUser) {
			String status = succeed ? RunHistoryEntry.STATUS_OK : RunHistoryEntry.STATUS_STOPPED;
			RunHistoryEntry entry = new RunHistoryEntry(lastAlgorithmName, inputFile != null ? inputFile : "",
					outputFile != null ? outputFile : "", lastParameters, status, LocalDateTime.now(), durationMillis);
			panel.addHistoryEntry(entry);
		}

		if (succeed) {
			String pathForPreview = panel.isOutputFileSectionVisible() ? lastOutputFile : null;

			// Now that the algorithm has finished, populate the Output panel
			// with a preview of the freshly generated output file. This is
			// the second half of the "clear on start, populate on finish"
			// sequence that gives the user a clear cue that the algorithm ran
			// and has completed its work.
			panel.updateOutputTab(pathForPreview);
		}

		// Give the user clear feedback that the run has finished, since fast
		// algorithms (e.g. those completing in 1 ms) would otherwise give no
		// visible indication that anything happened at all. Only do this for
		// algorithms that actually produce an output file — algorithms with
		// no output (some tools, viewers, etc.) have nothing to preview or
		// announce, so flashing/beeping for them would be misleading.
		if (algorithmHasOutputFile(lastAlgorithmName)) {
			panel.flashOutputPanel();
			if (panel.isBeepOnCompletionSelected()) {
				java.awt.Toolkit.getDefaultToolkit().beep();
			}
		}

		panel.resetUIAfterThreadCompletion();
	}

	// =========================================================================
	// Viewer dispatch
	// =========================================================================

	/**
	 * Opens the result file with the viewer whose name matches {@code viewerType}.
	 * 
	 * @param viewerType the viewer name as it appears in the "Open using:" combo
	 * @param filePath   the path to the file to open
	 */
	private void openSelectedViewer(String viewerType, String filePath) {
		if (viewerType == null || viewerType.equals("Don't open")) {
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
					AlgorithmManager.getInstance().getDescriptionOfAlgorithm(viewerType).runAlgorithm(new String[] {},
							filePath, null);
				} catch (Exception e) {
					throw new UnsupportedOperationException("Viewer type not supported: " + viewerType);
				}
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "An error occurred while trying to open the output file with "
					+ viewerType + ". ERROR MESSAGE = " + e, "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the given file using the time-series viewer.
	 */
	private void openTimeSeriesViewer(String filePath) throws IOException {
		String separator = ",";
		final String[] parameters = panel.getParameterValues();
		for (String parameter : parameters) {
			if (parameter.equals("Separator")) {
				separator = parameter;
			}
		}
		AlgoTimeSeriesReader reader = new AlgoTimeSeriesReader();
		List<TimeSeries> timeSeries = reader.runAlgorithm(outputFile, separator);
		TimeSeriesViewer viewer = new TimeSeriesViewer(timeSeries);
		viewer.setVisible(true);
	}

	/**
	 * Opens the {@link AlgorithmSelectorDialog}, waits for the user to pick an
	 * algorithm (or cancel), then updates the panel and fires the normal
	 * algorithm-selection UI update.
	 */
	private void openAlgorithmSelectorDialog() {
		try {
			AlgorithmSelectorDialog dialog = new AlgorithmSelectorDialog(parentFrame, panel.getShowViewAndTransform(),
					panel.getShowGenerateData(), panel.getShowTools(), panel.getShowAlgorithms(),
					panel.getShowExperimentTools());
			dialog.setVisible(true); // blocks until the dialog closes

			String chosen = dialog.getSelectedAlgorithm();
			if (chosen == null || chosen.trim().isEmpty()) {
				return; // user cancelled
			}

			panel.setSelectedAlgorithm(chosen);

			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(chosen);
			panel.updateUserInterfaceForAlgorithm(algorithm);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(parentFrame,
					"Could not open the algorithm selector.\n" + "ERROR: " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
			ex.printStackTrace();
		}
	}

	// =========================================================================
	// UncaughtExceptionHandler
	// =========================================================================

	/**
	 * Called by the JVM when the algorithm thread throws an uncaught exception.
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable e) {
		if (e instanceof ThreadDeath) {
			// Intentional stop — nothing to do
		} else if (e instanceof NumberFormatException) {
			JOptionPane.showMessageDialog(
					null, "Error. Please check the parameters of the algorithm. "
							+ "The format for numbers is incorrect.\n" + "\n ERROR MESSAGE = " + e,
					"Error", JOptionPane.ERROR_MESSAGE);
		} else {
			JOptionPane.showMessageDialog(null,
					"An error occurred while trying to run the algorithm.\n" + "ERROR MESSAGE = " + e, "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		panel.clearConsole();
	}

	// =========================================================================
	// Run / Stop
	// =========================================================================

	/**
	 * Processes the user's click on the Run/Stop button.
	 */
	private void processRunAlgorithmCommandFromGUI() {

		// Guard: spmf.jar must exist when running as external process
		if (PreferencesManager.getInstance().getRunAsExternalProgram()) {
			File file = new File("spmf.jar");
			if (!file.exists()) {
				JOptionPane.showMessageDialog(null,
						"The algorithm cannot be run in a separated process because "
								+ "spmf.jar is not found. It will be run in the same process.",
						"Error", JOptionPane.ERROR_MESSAGE);
				panel.setRunAsExternalSelected(false);
				PreferencesManager.getInstance().setRunAsExternalProgram(false);
			}
		}

		// If the algorithm is already running, try to kill it
		boolean killed = runner.tryToKillProcess(panel);
		if (killed) {
			wasStoppedByUser = true;
			return;
		}

		final String choice = panel.getSelectedAlgorithmName();

		// Validate required parameters and input/output files before doing
		// anything else. This must happen before any UI state is changed
		// (status message, running-state, output-tab clearing) so that a
		// blocked run leaves the interface exactly as it was.
		if (!validateBeforeRun(choice)) {
			return;
		}

		SimpleDateFormat dateTimeInGMT = new SimpleDateFormat("hh:mm:ss aa");
		String time = dateTimeInGMT.format(new Date());
		panel.postStatusMessage("Algorithm is running... (" + time + ") \n");

		if (!choice.equals("MemoryViewer")) {
			panel.setUIToRunningState();
		}

		// Clear the Output panel right away, before the algorithm actually
		// starts running, so the user immediately sees that any previous
		// preview has been wiped and gets a clear visual cue that a new run
		// is in progress. Only do this for algorithms that actually produce
		// an output file — algorithms with no output (some tools, viewers,
		// etc.) have nothing to preview, so there is nothing to clear for
		// them. The preview is redisplayed once the run completes, in
		// notifyOfThreadComplete().
		if (algorithmHasOutputFile(choice)) {
			panel.clearOutputTab();
			panel.startElapsedTimer();
		}

		final String[] parameters = panel.getParameterValues();

		wasStoppedByUser = false;

		runStartTimeMillis = System.currentTimeMillis();
		lastAlgorithmName = choice;
		lastParameters = parameters != null ? Arrays.copyOf(parameters, parameters.length) : new String[0];
		lastOutputFile = outputFile != null ? outputFile : "";

		// Record this algorithm in the recent-algorithms list
		if (choice != null && !choice.trim().isEmpty()) {
			PreferencesManager.getInstance().addRecentAlgorithm(choice);
		}

		// Record the input/output files in their respective recent lists
		if (inputFile != null && !inputFile.isEmpty()) {
			PreferencesManager.getInstance().addRecentInputFile(inputFile);
		}
		if (outputFile != null && !outputFile.isEmpty()) {
			PreferencesManager.getInstance().addRecentOutputFile(outputFile);
		}

		// Dispatch to the appropriate runner
		if (PreferencesManager.getInstance().getRunAsExternalProgram()) {
			runner.runExternal(choice, inputFile, outputFile, parameters, this, this);
		} else {
			runner.runInternal(choice, inputFile, outputFile, parameters, this, this);
		}

		// Start the killer thread if the user has set a time limit
		if (panel.isTimeLimitEnabled()) {
			maxTime = -1;
			try {
				maxTime = Integer.parseInt(panel.getMaxSecondsText());
			} catch (NumberFormatException exception) {
				// maxTime stays -1; startKillerThreadIfNeeded will be a no-op
			}
			runner.startKillerThreadIfNeeded(maxTime, panel);
		}
	}

	// =========================================================================
	// History re-run
	// =========================================================================

	/**
	 * Re-applies a past run configuration to the UI and immediately executes it.
	 * 
	 * @param entry the history entry to replay, or {@code null} to do nothing
	 */
	private void rerunHistoryEntry(RunHistoryEntry entry) {
		if (entry == null) {
			return;
		}

		panel.setSelectedAlgorithm(entry.getAlgorithmName());

		SwingUtilities.invokeLater(() -> {

			inputFile = entry.getInputFile().isEmpty() ? null : entry.getInputFile();
			outputFile = entry.getOutputFile().isEmpty() ? null : entry.getOutputFile();
			panel.setInputFileName(inputFile != null ? inputFile : "");
			panel.setOutputFileName(outputFile != null ? outputFile : "");
			panel.setViewInputButtonEnabled(inputFile != null);

			String[] storedParams = entry.getParameters();
			int panelCount = panel.getParameterCount();
			int restoreCount = Math.min(storedParams.length, panelCount);

			if (restoreCount > 0) {
				String[] paramsToRestore = Arrays.copyOf(storedParams, panelCount);
				for (int i = restoreCount; i < panelCount; i++) {
					paramsToRestore[i] = "";
				}
				panel.setParameterValues(paramsToRestore);
			}

			processRunAlgorithmCommandFromGUI();
		});
	}

	// =========================================================================
	// Package-private callbacks from AlgorithmRunner
	// =========================================================================

	/**
	 * Called by {@link AlgorithmRunner} when a run is stopped by the user or the
	 * time limit.
	 */
	void notifyWasStopped() {
		wasStoppedByUser = true;
	}

	// =========================================================================
	// Text-field sync helpers
	// =========================================================================

	/**
	 * Reads the current text from the input file text field and stores it as the
	 * active input file path.
	 */
	private void syncInputFileFromTextField() {
		String text = panel.getInputFileText().trim();
		if (text.isEmpty()) {
			inputFile = null;
			panel.setViewInputButtonEnabled(false);
		} else {
			inputFile = text;
			panel.setViewInputButtonEnabled(true);
		}
	}

	/**
	 * Reads the current text from the output file text field and stores it as the
	 * active output file path.
	 */
	private void syncOutputFileFromTextField() {
		String text = panel.getOutputFileText().trim();
		outputFile = text.isEmpty() ? null : text;
	}
}