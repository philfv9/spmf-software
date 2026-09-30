package ca.pfv.spmf.gui.guided_interface;

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
 * 
 * Do not remove copyright and license.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.AboutWindow;
import ca.pfv.spmf.gui.DialogSelectAlgorithmParameter;
import ca.pfv.spmf.gui.MemoryViewer;
import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmExplorer;
import ca.pfv.spmf.gui.developerswindow.DevelopersToolsWindow;
import ca.pfv.spmf.gui.developerswindow.DocumentationDownloaderWindow;
import ca.pfv.spmf.gui.pattern_diff_analyzer.PatternDiffAnalyzer;
import ca.pfv.spmf.gui.patternvizualizer.PatternVizualizer;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;

/**
 * Builds the "Tools", "Experiments" and "Help" menus for {@link GuidedInterface}
 * and implements the logic to run a tool/experiment algorithm chosen from
 * those menus.
 *
 * <p>
 * "Tools" now shows the same curated list of items as the "Other Tools"
 * popup menu of the main SPMF launcher window (Pattern Viewer, Pattern Diff
 * Analyzer, Algorithm Explorer, SPMF Text Editor, Developers' Tools,
 * Download Offline Documentation, Memory Viewer), instead of an
 * auto-generated list built from {@link AlgorithmType#OTHER_TOOL} and
 * {@link AlgorithmType#OTHER_GUI_TOOL}. "Experiments" still lists algorithms
 * of type {@link AlgorithmType#EXPERIMENT_TOOL}, built automatically and
 * shown as a flat list.
 * </p>
 *
 * <p>
 * These algorithms are typically simple actions (e.g. opening a viewer,
 * exporting data, launching an experiment window) rather than long-running
 * data-mining tasks, so they are launched directly on the Swing event thread
 * and no success/failure notification is shown — any window they open (or
 * error they print) is itself the feedback.
 * </p>
 *
 * @author Philippe Fournier-Viger
 */
public class ToolMenuController {

	private final JFrame parentFrame;

	/**
	 * Creates a new controller that will build menus and run tools relative to
	 * the given parent frame.
	 *
	 * @param parentFrame the owning frame, used for dialog placement
	 */
	public ToolMenuController(JFrame parentFrame) {
		this.parentFrame = parentFrame;
	}

	// =========================================================================
	// Menu construction
	// =========================================================================

	/**
	 * Builds the "Tools" menu with a fixed, curated list of items — the same
	 * ones offered by the "Other Tools" button of the main SPMF launcher
	 * window:
	 * <ul>
	 *   <li>Pattern Viewer</li>
	 *   <li>Pattern Diff Analyzer</li>
	 *   <li>Algorithm Explorer</li>
	 *   <li>SPMF Text Editor</li>
	 *   <li>Developers' Tools</li>
	 *   <li>Download Offline Documentation</li>
	 *   <li>Memory Viewer</li>
	 * </ul>
	 *
	 * @return the assembled "Tools" menu
	 */
	public JMenu buildToolsMenu() {
		JMenu menu = new JMenu("Tools");

		JMenuItem itemPatternViewer = new JMenuItem("Pattern Viewer");
		itemPatternViewer.setToolTipText("Visualise and explore discovered patterns");
		itemPatternViewer.addActionListener(e -> actionPatternViewer());

		JMenuItem itemPatternDiff = new JMenuItem("Pattern Diff Analyzer");
		itemPatternDiff.setToolTipText("Compare differences between two pattern files");
		itemPatternDiff.addActionListener(e -> actionPatternDiff());

		JMenuItem itemAlgorithmExplorer = new JMenuItem("Algorithm Explorer");
		itemAlgorithmExplorer.setToolTipText("Browse and search the algorithms implemented in SPMF");
		itemAlgorithmExplorer.addActionListener(e -> actionAlgorithmExplorer());

		JMenuItem itemTextEditor = new JMenuItem("SPMF Text Editor");
		itemTextEditor.setToolTipText("Open the built-in text editor for input/output files");
		itemTextEditor.addActionListener(e -> actionTextEditor());

		JMenuItem itemDevTools = new JMenuItem("Developers' Tools");
		itemDevTools.setToolTipText("Tools intended for SPMF developers");
		itemDevTools.addActionListener(e -> actionDevelopersTools());

		JMenuItem itemDownloadDocs = new JMenuItem("Download Offline Documentation");
		itemDownloadDocs.setToolTipText("Download an offline copy of the SPMF documentation");
		itemDownloadDocs.addActionListener(e -> actionDownloadOfflineDocumentation());

		JMenuItem itemMemoryViewer = new JMenuItem("Memory Viewer");
		itemMemoryViewer.setToolTipText("Show a live chart of the current JVM memory usage");
		itemMemoryViewer.addActionListener(e -> actionMemoryViewer());

		menu.add(itemPatternViewer);
		menu.add(itemPatternDiff);
		menu.addSeparator();
		menu.add(itemAlgorithmExplorer);
		menu.add(itemTextEditor);
		menu.addSeparator();
		menu.add(itemMemoryViewer);
		menu.add(itemDevTools);
//		menu.add(itemDownloadDocs);

		return menu;
	}

	/**
	 * Builds the "Experiments" menu, containing all
	 * {@link AlgorithmType#EXPERIMENT_TOOL} algorithms as a single flat list.
	 *
	 * @return the assembled "Experiments" menu
	 */
	public JMenu buildExperimentsMenu() {
		return buildMenuForTypes("Experiments", AlgorithmType.EXPERIMENT_TOOL);
	}

	/**
	 * Builds the "Help" menu, containing the "About" item that opens
	 * {@link AboutWindow}.
	 *
	 * @return the assembled "Help" menu
	 */
	public JMenu buildHelpMenu() {
		JMenu menu = new JMenu("Help");

		JMenuItem aboutItem = new JMenuItem("About SPMF...");
		aboutItem.setToolTipText("Show information about SPMF");
		aboutItem.addActionListener(e -> {
			try {
				AboutWindow about = new AboutWindow(parentFrame);
				about.setVisible(true);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(parentFrame,
						"Could not open the About window.\nERROR: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
				ex.printStackTrace();
			}
		});
		menu.add(aboutItem);

		return menu;
	}

	/**
	 * Builds a flat menu titled {@code title} containing one item per algorithm
	 * whose type is one of {@code types}, sorted alphabetically by name. No
	 * sub-menus are used, regardless of how many algorithm categories exist.
	 *
	 * <p>
	 * Currently only used for the "Experiments" menu, since the "Tools" menu
	 * now uses a fixed, curated list built by {@link #buildToolsMenu()}.
	 * </p>
	 *
	 * @param title the menu title
	 * @param types the algorithm types to include
	 * @return the assembled menu
	 */
	private JMenu buildMenuForTypes(String title, AlgorithmType... types) {
		JMenu menu = new JMenu(title);

		try {
			AlgorithmManager mgr = AlgorithmManager.getInstance();

			List<DescriptionOfAlgorithm> matching = new ArrayList<>();
			for (DescriptionOfAlgorithm algorithm : mgr.getAlgorithms()) {
				if (matchesAnyType(algorithm.getAlgorithmType(), types)) {
					matching.add(algorithm);
				}
			}
			matching.sort(Comparator.comparing(DescriptionOfAlgorithm::getName,
					String.CASE_INSENSITIVE_ORDER));

			for (DescriptionOfAlgorithm algorithm : matching) {
				JMenuItem item = new JMenuItem(algorithm.getName());
				item.setToolTipText("Run " + algorithm.getName());
				item.addActionListener(e -> runToolAlgorithm(algorithm));
				menu.add(item);
			}

			if (matching.isEmpty()) {
				JMenuItem emptyItem = new JMenuItem("(none available)");
				emptyItem.setEnabled(false);
				menu.add(emptyItem);
			}
		} catch (Exception e) {
			JMenuItem errorItem = new JMenuItem("(could not load: " + e.getMessage() + ")");
			errorItem.setEnabled(false);
			menu.add(errorItem);
		}

		return menu;
	}

	private boolean matchesAnyType(AlgorithmType type, AlgorithmType[] types) {
		if (type == null || types == null) {
			return false;
		}
		for (AlgorithmType t : types) {
			if (type.equals(t)) {
				return true;
			}
		}
		return false;
	}

	// =========================================================================
	// Fixed "Tools" menu actions (mirrors LauncherWindow's "Other Tools" menu)
	// =========================================================================

	/**
	 * Opens a file chooser so the user can select a pattern file, then opens
	 * it in the {@link PatternVizualizer}.
	 */
	private void actionPatternViewer() {
		try {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select a Pattern File");
			String previousPath = PreferencesManager.getInstance().getInputFilePath();
			if (previousPath != null && !previousPath.trim().isEmpty()) {
				File previousDir = new File(previousPath);
				if (previousDir.isDirectory()) {
					chooser.setCurrentDirectory(previousDir);
				}
			}
			chooser.setFileFilter(
					new javax.swing.filechooser.FileNameExtensionFilter("Text Files (*.txt)", "txt"));
			chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
					"All Supported Files (*.txt, *.csv)", "txt", "csv"));
			chooser.setAcceptAllFileFilterUsed(true);

			if (chooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
				PatternVizualizer viewer = new PatternVizualizer(chooser.getSelectedFile().getAbsolutePath());
				viewer.setVisible(true);
			}
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(parentFrame, "Error opening Pattern Viewer: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the {@link PatternDiffAnalyzer} tool.
	 */
	private void actionPatternDiff() {
		try {
			PatternDiffAnalyzer analyzer = new PatternDiffAnalyzer(false);
			analyzer.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(parentFrame, "Error opening Pattern Diff Analyzer: " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the {@link AlgorithmExplorer} tool, which lets the user browse
	 * and search the algorithms implemented in SPMF.
	 */
	private void actionAlgorithmExplorer() {
		try {
			// Not standalone: closing the explorer must not exit the whole
			// application.
			boolean runAsStandalone = false;
			AlgorithmExplorer frame = new AlgorithmExplorer(runAsStandalone);
			frame.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(parentFrame, "Error opening Algorithm Explorer: " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the {@link SPMFTextEditor}.
	 */
	private void actionTextEditor() {
		try {
			boolean runAsStandalone = false;
			@SuppressWarnings("unused")
			SPMFTextEditor editor = new SPMFTextEditor(runAsStandalone);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(parentFrame, "Error opening SPMF Text Editor: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the {@link DevelopersToolsWindow}, a set of tools intended for
	 * SPMF developers.
	 */
	private void actionDevelopersTools() {
		try {
			boolean runAsStandalone = false;
			DevelopersToolsWindow frame = new DevelopersToolsWindow(runAsStandalone);
			frame.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(parentFrame, "Error opening Developers' Tools: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the {@link DocumentationDownloaderWindow}, which lets the user
	 * download an offline copy of the SPMF documentation.
	 */
	private void actionDownloadOfflineDocumentation() {
		try {
			DocumentationDownloaderWindow window = new DocumentationDownloaderWindow();
			window.createAndShowGUI();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(parentFrame,
					"Error opening Documentation Downloader: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Opens the {@link MemoryViewer} tool, which displays a live chart of
	 * the current JVM memory usage.
	 */
	private void actionMemoryViewer() {
		try {
			MemoryViewer.displayMemoryChart();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(parentFrame, "Error opening Memory Viewer: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// =========================================================================
	// Running a tool / experiment algorithm (still used by "Experiments")
	// =========================================================================

	/**
	 * Runs the given tool/experiment algorithm directly on the event-dispatch
	 * thread. Prompts the user for any declared parameters, input file, and
	 * output file first.
	 *
	 * <p>
	 * No completion dialog is shown and no success/failure is tracked: these
	 * algorithms are simple actions (open a viewer/editor window, export a
	 * file, launch an experiment UI, etc.) whose own window or console output
	 * is the feedback. If something goes wrong, the exception is printed to
	 * the console rather than shown in a popup, so as not to imply a
	 * "success/failure" semantic that does not apply here.
	 * </p>
	 *
	 * @param algorithm the algorithm to run
	 */
	private void runToolAlgorithm(DescriptionOfAlgorithm algorithm) {
		// Ask for parameter values, if any.
		DescriptionOfParameter[] paramDescriptions = algorithm.getParametersDescription();
		String[] params;
		if (paramDescriptions != null && paramDescriptions.length > 0) {
			params = new String[paramDescriptions.length];
			for (int i = 0; i < paramDescriptions.length; i++) {
				DialogSelectAlgorithmParameter dialog =
						new DialogSelectAlgorithmParameter(paramDescriptions[i], parentFrame);
				String value = dialog.getUserInput();
				if (value == null && !paramDescriptions[i].isOptional()) {
					return; // user cancelled a required parameter
				}
				params[i] = value == null ? "" : value;
			}
		} else {
			params = new String[] {};
		}

		// Ask for an input file, if the algorithm declares one.
		String inputFile = null;
		if (algorithm.getInputFileTypes() != null) {
			inputFile = askUserForFile(false);
			if (inputFile == null) {
				return; // user cancelled
			}
		}

		// Ask for an output file, if the algorithm declares one.
		String outputFile = null;
		if (algorithm.getOutputFileTypes() != null) {
			outputFile = askUserForFile(true);
			if (outputFile == null) {
				return; // user cancelled
			}
		}

		// Run directly on the EDT: these tools are expected to be quick
		// actions (open a window, write a small file, etc.), not long
		// data-mining computations, so no background thread is needed.
		try {
			algorithm.runAlgorithm(params, inputFile, outputFile);
		} catch (Exception e) {
			// Just log it — no popup, since these tools have no notion of
			// "run succeeded/failed" that the user needs to be told about.
			e.printStackTrace();
		}
	}

	/**
	 * Opens a file chooser and returns the chosen path, or {@code null} if the
	 * user cancelled.
	 *
	 * @param isOutput {@code true} to show a save dialog, {@code false} for open
	 * @return the chosen file path, or {@code null} if cancelled
	 */
	private String askUserForFile(boolean isOutput) {
		String previousPath = isOutput ? PreferencesManager.getInstance().getOutputFilePath()
				: PreferencesManager.getInstance().getInputFilePath();
		JFileChooser fc = (previousPath != null && new File(previousPath).isDirectory())
				? new JFileChooser(previousPath) : new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int result = isOutput ? fc.showSaveDialog(parentFrame) : fc.showOpenDialog(parentFrame);
		if (result != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		File file = fc.getSelectedFile();
		return file.getAbsolutePath();
	}
}