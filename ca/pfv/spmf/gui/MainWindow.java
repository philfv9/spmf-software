package ca.pfv.spmf.gui;

/*
Copyright (c) 2008-2021 Philippe Fournier-Viger
This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
Do not remove copyright or license information.
*/
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;

import ca.pfv.spmf.gui.preferences.PreferencesManager;

/**
 * Main window of SPMF. Allows the user to launch single algorithms. The visual
 * content and event handling are delegated to {@link AlgorithmRunnerPanel} and
 * {@link AlgorithmRunnerController} respectively.
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class MainWindow extends JFrame {

    /** The reusable panel that contains the complete algorithm-runner UI */
    private AlgorithmRunnerPanel runnerPanel;

    /** The controller that wires events and drives business logic for the panel */
    private AlgorithmRunnerController controller;

    /**
     * Default constructor. Shows all algorithm categories and makes the window
     * visible immediately.
     * 
     * @throws Exception if some error happens during initialisation
     */
    public MainWindow() throws Exception {
        this(true, true, true, true, true);
        setVisible(true);
    }

    /**
     * Constructor that allows selective display of algorithm categories.
     * 
     * @param showViewAndTransform whether view-and-transform algorithms are shown
     * @param showGenerateData     whether data-generation algorithms are shown
     * @param showTools            whether tool algorithms are shown
     * @param showAlgorithms       whether data-mining algorithms are shown
     * @param showExperimentTools  whether experiment-tool algorithms are shown
     * @throws Exception if some error happens during initialisation
     */
    public MainWindow(boolean showViewAndTransform, boolean showGenerateData,
            boolean showTools, boolean showAlgorithms,
            boolean showExperimentTools) throws Exception {

        setIconImage(Toolkit.getDefaultToolkit().getImage(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));

        setResizable(true);

        // When the user clicks the "x" the software will close.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set the title of the window
        setTitleBasedOnFlags(showViewAndTransform, showGenerateData,
                showTools, showAlgorithms, showExperimentTools);

        // Build the runner panel with the category flags
        runnerPanel = new AlgorithmRunnerPanel(
                showViewAndTransform, showGenerateData,
                showTools, showAlgorithms, showExperimentTools);
        runnerPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Set the runner panel as the content pane
        setContentPane(runnerPanel);

        // Build the controller and wire all event handlers
        controller = new AlgorithmRunnerController(runnerPanel, this);
        controller.initializeUIEventHandling();

        // Close the application when the window is closed
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent arg0) {
                System.exit(0);
            }
        });

        validate();
        pack();

        // Set the minimum size to the packed size BEFORE hiding anything
        this.setMinimumSize(getSize());
        this.setLocationRelativeTo(null);

        // Hide all optional components until an algorithm is selected.
        runnerPanel.applyInitialVisibility();
    }

    /**
     * Sets the window title based on which algorithm categories are enabled.
     */
    private void setTitleBasedOnFlags(boolean showViewAndTransform,
            boolean showGenerateData,
            boolean showTools,
            boolean showAlgorithms,
            boolean showExperimentTools) {
        if (showViewAndTransform && showGenerateData && !showAlgorithms) {
            setTitle("Prepare data (run a dataset tool)");
        } else if (!showTools && showAlgorithms && !showExperimentTools) {
            setTitle("Run a data mining algorithm");
        } else if (!showTools && !showAlgorithms && showExperimentTools) {
            setTitle("Run an experiment");
        } else if (showViewAndTransform && !showAlgorithms) {
            setTitle("View and transform data");
        } else if (showGenerateData && !showAlgorithms) {
            setTitle("Generate data");
        } else {
            setTitle("SPMF v" + Main.SPMF_VERSION);
        }
    }
}