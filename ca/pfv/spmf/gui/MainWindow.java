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
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;

/**
 * Main window of SPMF for running a single algorithm. The UI is provided by
 * {@link AlgorithmRunnerPanel} and the behaviour by
 * {@link AlgorithmRunnerController}.
 * <p>
 * Closing the window disposes it and restores the standard output streams; the
 * application exits only if no other window remains open (so the window can be
 * used both standalone and as a child of the launcher).
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class MainWindow extends JFrame {

    private final AlgorithmRunnerPanel runnerPanel;
    private final AlgorithmRunnerController controller;
    private final boolean showSpmfLogo;

    /**
     * Shows all algorithm categories and the logo, and makes the window visible.
     *
     * @throws Exception if initialisation fails
     */
    public MainWindow() throws Exception {
        this(true, true, true, true, true, true);
        setVisible(true);
    }

    /**
     * Creates the window (not visible yet).
     *
     * @param showViewAndTransform whether view-and-transform algorithms are shown
     * @param showGenerateData     whether data-generation algorithms are shown
     * @param showTools            whether tool algorithms are shown
     * @param showAlgorithms       whether data-mining algorithms are shown
     * @param showExperimentTools  whether experiment-tool algorithms are shown
     * @param showSpmfLogo         whether the SPMF logo is displayed
     * @throws Exception if initialisation fails
     */
    public MainWindow(boolean showViewAndTransform, boolean showGenerateData, boolean showTools,
                      boolean showAlgorithms, boolean showExperimentTools, boolean showSpmfLogo)
            throws Exception {
        this.showSpmfLogo = showSpmfLogo;

        if (showSpmfLogo) {
            URL icon = MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png");
            if (icon != null) {
                setIconImage(new ImageIcon(icon).getImage());
            }
        }

        setTitle(titleFor(showViewAndTransform, showGenerateData, showTools, showAlgorithms, showExperimentTools));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        runnerPanel = new AlgorithmRunnerPanel(showViewAndTransform, showGenerateData, showTools,
                showAlgorithms, showExperimentTools, showSpmfLogo);
        runnerPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(runnerPanel);

        controller = new AlgorithmRunnerController(runnerPanel, this);
        controller.initializeUIEventHandling();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                runnerPanel.restoreOutputStream();
                if (!anotherWindowIsOpen()) {
                    System.exit(0);
                }
            }
        });

        pack();
        clampToScreen();
        // The packed size already reserves room for every row, so it is a safe minimum
        setMinimumSize(getSize());
        setLocationRelativeTo(null);

        runnerPanel.applyInitialVisibility();
    }

    /** @return true if the logo was requested at construction */
    public boolean isShowSpmfLogo() {
        return showSpmfLogo;
    }

    /** Never let the initial window exceed the usable screen area. */
    private void clampToScreen() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            return;
        }
        Rectangle bounds = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int maxW = bounds.width - in.left - in.right;
        int maxH = bounds.height - in.top - in.bottom;
        if (getWidth() > maxW || getHeight() > maxH) {
            setSize(new Dimension(Math.min(getWidth(), maxW), Math.min(getHeight(), maxH)));
        }
    }

    private boolean anotherWindowIsOpen() {
        for (Window w : Window.getWindows()) {
            if (w != this && w.isDisplayable() && w.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static String titleFor(boolean showViewAndTransform, boolean showGenerateData, boolean showTools,
                                   boolean showAlgorithms, boolean showExperimentTools) {
        String subject;
        if (showViewAndTransform && showGenerateData && !showAlgorithms) {
            subject = "Prepare data";
        } else if (!showTools && showAlgorithms && !showExperimentTools) {
            subject = "Run a data mining algorithm";
        } else if (!showTools && !showAlgorithms && showExperimentTools) {
            subject = "Run an experiment";
        } else if (showViewAndTransform && !showAlgorithms) {
            subject = "View and transform data";
        } else if (showGenerateData && !showAlgorithms) {
            subject = "Generate data";
        } else {
            return "SPMF v" + Main.SPMF_VERSION;
        }
        return "SPMF \u2013 " + subject;
    }
}