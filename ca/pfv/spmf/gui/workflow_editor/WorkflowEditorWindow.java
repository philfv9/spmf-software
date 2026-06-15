package ca.pfv.spmf.gui.workflow_editor;

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
 *
 * Do not remove license and copyright information
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
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
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.CommandProcessor;
import ca.pfv.spmf.gui.ConsolePanel;
import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.MemoryViewer;
import ca.pfv.spmf.gui.NotifyingThread;
import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmExplorer;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * Workflow Editor window for SPMF that allows building, validating, and running branching algorithm workflows.
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class WorkflowEditorWindow extends JFrame
        implements DrawPanelListener, UncaughtExceptionHandler {

    /** The branching draw panel that renders the workflow graph. */
    DrawPanel drawPanel;

    /** Progress bar displayed while algorithms are running. */
    private JProgressBar progressBar;

    /** Console output panel for displaying algorithm status messages and output. */
    private ConsolePanel consolePanel;

    /** Information panel shown on the right-hand side. */
    private InformationPanel infoPanel;

    /** Button to add a new algorithm node to the workflow. */
    JButton buttonAddAlgorithm;

    /** Button to remove the currently selected leaf algorithm node. */
    JButton buttonRemoveSelectedNode;

    /** Button to validate the entire workflow. */
    JButton buttonValidate;

    /** Button to run the workflow, relabelled "Stop workflow" while running. */
    JButton buttonRun;

    /** Menu item to create a new empty workflow. */
    private JMenuItem menuItemNew;

    /** Menu item to load a workflow from a file using the file chooser. */
    private JMenuItem menuItemLoad;

    /** Submenu listing recently opened workflow files for quick loading. */
    private JMenu menuRecentWorkflows;

    /** Menu item to save the current workflow to the current file. */
    private JMenuItem menuItemSave;

    /** Menu item to save the current workflow to a new file chosen by the user. */
    private JMenuItem menuItemSaveAs;

    /** Menu item to validate the workflow. */
    private JMenuItem menuItemValidate;

    /** Menu item to run the workflow. */
    private JMenuItem menuItemRun;

    /** Check box menu item to run the workflow in a separate JVM process. */
    private JCheckBoxMenuItem checkBoxSeparatedProcess;

    /** Maximum execution time in seconds; Integer.MAX_VALUE means unlimited. */
    int maxTime = Integer.MAX_VALUE;

    /** Set to true when the user clicks Stop during a run. */
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    /** All OS processes launched during the current run. */
    private final List<Process> activeProcesses = new ArrayList<>();

    /** All algorithm threads launched during the current run. */
    private final List<NotifyingThread> activeThreads = new ArrayList<>();

    /** The file path of the workflow file currently open, or null if none has been saved or loaded. */
    private String currentWorkflowFilePath = null;

    /** True when the workflow has unsaved changes relative to the last save or load. */
    private boolean workflowModified = false;

    /**
     * Creates and displays the workflow editor window.
     *
     * @param runAsStandalone when true the JVM exits when this window is closed.
     * @throws Exception if any UI resource cannot be loaded.
     */
    public WorkflowEditorWindow(boolean runAsStandalone) throws Exception {
        setTitle("SPMF Workflow Editor " + Main.SPMF_VERSION);
        setIconImage(Toolkit.getDefaultToolkit().getImage(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/History24.gif")));
        setSize(1000, 750);
        setLayout(new BorderLayout());

        drawPanel = new DrawPanel();
        drawPanel.addDrawPanelListener(this);

        infoPanel = new InformationPanel(this, drawPanel);
        drawPanel.addDrawPanelListener(infoPanel);

        JScrollPane scrollDraw = new JScrollPane(drawPanel);
        scrollDraw.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollDraw.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JScrollPane scrollInfo = new JScrollPane(infoPanel);

        // Horizontal split: draw panel on the left, info panel on the right
        JSplitPane horizontalSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, scrollDraw, scrollInfo);
        horizontalSplit.setDividerLocation((int) (getWidth() * 0.3));

        // Top area: workflow label above the horizontal split
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(new JLabel("Workflow:"), BorderLayout.NORTH);
        topArea.add(horizontalSplit, BorderLayout.CENTER);

        // Button toolbar
        JPanel buttonPanel = new JPanel();

        buttonAddAlgorithm = new JButton("Add algorithm");
        buttonAddAlgorithm.setIcon(new ImageIcon(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Add.png")));
        buttonAddAlgorithm.setEnabled(true);
        buttonPanel.add(buttonAddAlgorithm);

        buttonRemoveSelectedNode = new JButton("Remove algorithm (leaf only)");
        buttonRemoveSelectedNode.setIcon(new ImageIcon(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Remove.png")));
        buttonRemoveSelectedNode.setEnabled(false);
        buttonPanel.add(buttonRemoveSelectedNode);

        buttonValidate = new JButton("Validate the workflow");
        buttonValidate.setIcon(new ImageIcon(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Validate.png")));
        buttonValidate.setEnabled(false);
        buttonPanel.add(buttonValidate);

        buttonRun = new JButton("Run the workflow");
        buttonRun.setIcon(new ImageIcon(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Play24.gif")));
        buttonRun.setEnabled(false);
        buttonPanel.add(buttonRun);

        // Console and progress bar panel
        JPanel consoleProgressPanel = new JPanel(new BorderLayout());
        progressBar = new JProgressBar();
        consolePanel = new ConsolePanel(false);
        consolePanel.setPreferredSize(new Dimension(200, 120));
        consoleProgressPanel.add(new JLabel("Console:"), BorderLayout.NORTH);
        consoleProgressPanel.add(consolePanel, BorderLayout.CENTER);
        consoleProgressPanel.add(progressBar, BorderLayout.SOUTH);

        // Bottom area: buttons above the console panel
        JPanel bottomArea = new JPanel(new BorderLayout());
        bottomArea.add(buttonPanel, BorderLayout.NORTH);
        bottomArea.add(consoleProgressPanel, BorderLayout.CENTER);

        // Vertical split: top is the workflow area, bottom is the console area
        JSplitPane verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, topArea, bottomArea);
        verticalSplit.setResizeWeight(0.75);
        verticalSplit.setDividerLocation((int) (getHeight() * 0.75));
        verticalSplit.setOneTouchExpandable(true);

        add(verticalSplit, BorderLayout.CENTER);

        createMenuBar();

        buttonAddAlgorithm.addActionListener(e -> {
            drawPanel.addAlgorithmNode();
            markModified();
        });
        buttonRemoveSelectedNode.addActionListener(e -> {
            drawPanel.removeSelectedNode();
            markModified();
        });
        buttonValidate.addActionListener(e -> validateWorkflow());
        buttonRun.addActionListener(e -> {
            try { runWorkflow(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });

        if (runAsStandalone) setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);

        consolePanel.redirectOutputStream();
        consolePanel.postStatusMessage("Workflow editor initialized and ready.");
    }

    /**
     * Builds and attaches the menu bar with Workflow, Options, and Tools menus.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Workflow menu (first)
        JMenu menuWorkflow = new JMenu("Workflow");

        menuItemNew      = new JMenuItem("New workflow");
        menuItemLoad     = new JMenuItem("Load workflow");
        menuRecentWorkflows = new JMenu("Load recent workflow");
        menuItemSave     = new JMenuItem("Save workflow");
        menuItemSaveAs   = new JMenuItem("Save workflow as");
        menuItemValidate = new JMenuItem("Validate the workflow");
        menuItemRun      = new JMenuItem("Run the workflow");
        JMenuItem menuExportBAT = new JMenuItem("Export workflow as BAT script (for Windows)");
        JMenuItem menuExportSH  = new JMenuItem("Export workflow as SH script (for Linux)");
        JMenuItem menuExportPNG = new JMenuItem("Export workflow graph as PNG image");
        JMenuItem menuExportJPG = new JMenuItem("Export workflow graph as JPG image");

        menuWorkflow.add(menuItemNew);
        menuWorkflow.addSeparator();
        menuWorkflow.add(menuItemLoad);
        menuWorkflow.add(menuRecentWorkflows);
        menuWorkflow.add(menuItemSave);
        menuWorkflow.add(menuItemSaveAs);
        menuWorkflow.addSeparator();
        menuWorkflow.add(menuItemValidate);
        menuWorkflow.add(menuItemRun);
        menuWorkflow.addSeparator();
        menuWorkflow.add(menuExportBAT);
        menuWorkflow.add(menuExportSH);
        menuWorkflow.addSeparator();
        menuWorkflow.add(menuExportPNG);
        menuWorkflow.add(menuExportJPG);

        menuItemValidate.setEnabled(false);
        menuItemRun.setEnabled(false);

        // Populate the recent workflows submenu now and each time it is opened
        rebuildRecentWorkflowsMenu();
        menuRecentWorkflows.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                rebuildRecentWorkflowsMenu();
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e)   { }
        });

        // Options menu (second)
        JMenu menuOptions = new JMenu("Options");
        checkBoxSeparatedProcess = new JCheckBoxMenuItem("Run workflow in a separated process");
        menuOptions.add(checkBoxSeparatedProcess);

        // Tools menu (third)
        JMenu menuTools = new JMenu("Tools");
        JMenuItem menuDocumentation         = new JMenuItem("Open documentation webpage");
        JMenuItem menuItemAlgorithmExplorer = new JMenuItem("Algorithm Explorer");
        JMenuItem menuItemMemoryViewer      = new JMenuItem("Memory viewer");
        menuTools.add(menuDocumentation);
        menuTools.addSeparator();
        menuTools.add(menuItemAlgorithmExplorer);
        menuTools.add(menuItemMemoryViewer);

        menuBar.add(menuWorkflow);
        menuBar.add(menuOptions);
        menuBar.add(menuTools);
        setJMenuBar(menuBar);

        menuDocumentation.addActionListener(e -> openDocumentation());
        menuItemAlgorithmExplorer.addActionListener(e -> openAlgorithmExplorer());
        menuItemMemoryViewer.addActionListener(e -> openMemoryViewer());
        menuExportBAT.addActionListener(e -> exportAsBATFile());
        menuExportSH.addActionListener(e -> exportAsSHFile());
        menuExportPNG.addActionListener(e -> exportGraphAsImage("PNG", "png"));
        menuExportJPG.addActionListener(e -> exportGraphAsImage("JPEG", "jpg"));
        menuItemNew.addActionListener(e -> newWorkflow());
        menuItemLoad.addActionListener(e -> loadWorkflow());
        menuItemSave.addActionListener(e -> saveWorkflow());
        menuItemSaveAs.addActionListener(e -> saveWorkflowAs());
        menuItemValidate.addActionListener(e -> validateWorkflow());
        menuItemRun.addActionListener(e -> {
            try { runWorkflow(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
    }

    /**
     * Clears and rebuilds the recent-workflows submenu from the current preference store contents.
     * Each entry is a JMenuItem whose action directly loads that workflow file.
     * If the list is empty a single disabled placeholder item is shown.
     */
    private void rebuildRecentWorkflowsMenu() {
        menuRecentWorkflows.removeAll();
        List<String> recent = PreferencesManager.getInstance().getRecentWorkflowFiles();
        if (recent.isEmpty()) {
            JMenuItem empty = new JMenuItem("(no recent workflows)");
            empty.setEnabled(false);
            menuRecentWorkflows.add(empty);
            return;
        }
        for (String filePath : recent) {
            String label = new File(filePath).getName() + "  [" + filePath + "]";
            JMenuItem item = new JMenuItem(label);
            item.setToolTipText(filePath);
            item.addActionListener(e -> loadWorkflowFromPath(filePath));
            menuRecentWorkflows.add(item);
        }
    }

    /**
     * Exports the workflow draw panel as a raster image (PNG or JPG) chosen by the user.
     *
     * @param formatName the ImageIO format name, e.g. "PNG" or "JPEG".
     * @param extension  the file extension without a leading dot, e.g. "png" or "jpg".
     */
    private void exportGraphAsImage(String formatName, String extension) {
        if (drawPanel.roots.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "The workflow is empty. There is nothing to export.",
                    "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File file = chooseExportFile(formatName.toUpperCase() + " Images", extension);
        if (file == null) return;

        if (!file.getName().toLowerCase().endsWith("." + extension)) {
            file = new File(file.getAbsolutePath() + "." + extension);
        }

        Dimension size = drawPanel.getPreferredSize();
        int width  = Math.max(size.width,  1);
        int height = Math.max(size.height, 1);

        int imageType = "JPEG".equalsIgnoreCase(formatName)
                ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, imageType);

        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, width, height);
        drawPanel.paint(g2);
        g2.dispose();

        try {
            boolean written = ImageIO.write(image, formatName, file);
            if (!written) {
                JOptionPane.showMessageDialog(this,
                        "Could not write image format: " + formatName,
                        "Export Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            consolePanel.postStatusMessage(
                    "Workflow graph exported as " + formatName + " to: " + file.getAbsolutePath());
        } catch (IOException ex) {
            consolePanel.postStatusMessage("Error exporting image: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error exporting image: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Called when the user selects a node; button state is managed by other callbacks.
     *
     * @param node the newly selected node, or null if selection was cleared.
     */
    @Override
    public void notifyNodeSelected(Node node) {
        // Button state managed by notifyHasOutputNode and notifyCanRemoveSelectedNode.
    }

    /**
     * Enables or disables the Add Algorithm button based on whether adding is currently legal.
     *
     * @param hasOutput true if a new algorithm node may be added.
     */
    @Override
    public void notifyHasOutputNode(boolean hasOutput) {
        buttonAddAlgorithm.setEnabled(hasOutput);
    }

    /**
     * Enables or disables the validate and run buttons based on whether the workflow has nodes.
     *
     * @param roots the current list of root BranchNodes.
     */
    @Override
    public void notifyOfListOfRootNodes(List<BranchNode> roots) {
        boolean hasNodes = !roots.isEmpty();
        buttonRun.setEnabled(hasNodes);
        buttonValidate.setEnabled(hasNodes);
        menuItemRun.setEnabled(hasNodes);
        menuItemValidate.setEnabled(hasNodes);
    }

    /**
     * Enables or disables the Remove button based on whether a leaf algorithm node is selected.
     *
     * @param canRemove true if the selected node is a leaf algorithm and may be removed.
     */
    @Override
    public void notifyCanRemoveSelectedNode(boolean canRemove) {
        buttonRemoveSelectedNode.setEnabled(canRemove);
    }

    /**
     * Marks the workflow as having unsaved changes.
     */
    private void markModified() {
        workflowModified = true;
    }

    /**
     * Asks the user whether to discard or save unsaved changes; returns false if the user cancels.
     *
     * @return true if it is safe to proceed with the destructive operation, false if the user cancelled.
     */
    private boolean confirmDiscardOrSave() {
        if (!workflowModified || drawPanel.roots.isEmpty()) {
            return true;
        }
        int choice = JOptionPane.showOptionDialog(
                this,
                "The current workflow has unsaved changes. What would you like to do?",
                "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new Object[]{"Save", "Discard", "Cancel"},
                "Save");
        if (choice == JOptionPane.YES_OPTION) {
            return saveWorkflow();
        } else if (choice == JOptionPane.NO_OPTION) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Clears the current workflow and resets the editor to an empty state.
     */
    private void newWorkflow() {
        if (!confirmDiscardOrSave()) {
            return;
        }
        drawPanel.roots.clear();
        drawPanel.selected           = null;
        drawPanel.selectedBranchNode = null;

        infoPanel.notifyNodeSelected(null);

        drawPanel.relayoutAndRepaint();
        currentWorkflowFilePath = null;
        workflowModified        = false;
        consolePanel.postStatusMessage("New workflow created.");
    }

    /**
     * Opens a file chooser so the user can select a workflow file to load, replacing the current workflow.
     */
    private void loadWorkflow() {
        if (!confirmDiscardOrSave()) {
            return;
        }

        File startDir = resolveStartDirectory();
        JFileChooser fc = (startDir != null)
                ? new JFileChooser(startDir.getAbsolutePath()) : new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Workflow files (*.txt)", "txt"));
        fc.setAcceptAllFileFilterUsed(true);
        fc.setDialogTitle("Load Workflow");

        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = fc.getSelectedFile();
        PreferencesManager.getInstance().setInputFilePath(file.getParent());
        loadWorkflowFromPath(file.getAbsolutePath());
    }

    /**
     * Loads a workflow from the given absolute file path, replacing the current workflow.
     *
     * @param filePath the absolute path of the workflow file to load.
     */
    private void loadWorkflowFromPath(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this,
                    "The workflow file no longer exists:\n" + filePath,
                    "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            List<BranchNode> loaded = WorkflowSerializer.load(file.getAbsolutePath());
            drawPanel.roots.clear();
            drawPanel.roots.addAll(loaded);
            drawPanel.selected           = null;
            drawPanel.selectedBranchNode = null;

            infoPanel.notifyNodeSelected(null);

            drawPanel.relayoutAndRepaint();

            currentWorkflowFilePath = file.getAbsolutePath();
            workflowModified        = false;

            PreferencesManager.getInstance().addRecentWorkflowFile(file.getAbsolutePath());
            rebuildRecentWorkflowsMenu();

            consolePanel.postStatusMessage("Workflow loaded from: " + file.getAbsolutePath());
        } catch (IOException ex) {
            consolePanel.postStatusMessage("Error loading workflow: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error loading workflow: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves the current workflow to the previously used file, or opens a chooser if none exists.
     *
     * @return true if the workflow was saved successfully, false if the user cancelled or an error occurred.
     */
    private boolean saveWorkflow() {
        infoPanel.saveInformation(infoPanel.currentNode);
        if (currentWorkflowFilePath != null) {
            return writeWorkflowToFile(currentWorkflowFilePath);
        }
        return saveWorkflowAs();
    }

    /**
     * Opens a save file chooser and writes the workflow to the chosen file, always prompting for a new path.
     *
     * @return true if the workflow was saved successfully, false if the user cancelled or an error occurred.
     */
    private boolean saveWorkflowAs() {
        infoPanel.saveInformation(infoPanel.currentNode);
        File startDir = resolveStartDirectory();
        JFileChooser fc = (startDir != null)
                ? new JFileChooser(startDir.getAbsolutePath()) : new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Workflow files (*.txt)", "txt"));
        fc.setAcceptAllFileFilterUsed(false);
        fc.setDialogTitle("Save Workflow As");

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".txt")) {
            file = new File(file.getAbsolutePath() + ".txt");
        }
        PreferencesManager.getInstance().setInputFilePath(file.getParent());
        currentWorkflowFilePath = file.getAbsolutePath();
        return writeWorkflowToFile(currentWorkflowFilePath);
    }

    /**
     * Serializes the current workflow to the given file path and updates the modified flag.
     *
     * @param filePath the absolute path of the destination file.
     * @return true if the file was written without error, false otherwise.
     */
    private boolean writeWorkflowToFile(String filePath) {
        try {
            WorkflowSerializer.save(drawPanel.roots, filePath);
            workflowModified = false;

            PreferencesManager.getInstance().addRecentWorkflowFile(filePath);
            rebuildRecentWorkflowsMenu();

            consolePanel.postStatusMessage("Workflow saved to: " + filePath);
            return true;
        } catch (IOException ex) {
            consolePanel.postStatusMessage("Error saving workflow: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error saving workflow: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Returns the directory to use as the starting location for file choosers, or null if unknown.
     *
     * @return the starting directory File, or null.
     */
    private File resolveStartDirectory() {
        String previousPath = PreferencesManager.getInstance().getInputFilePath();
        if (previousPath != null) {
            return new File(previousPath);
        }
        URL url = MainTestApriori_simple_saveToFile.class
                .getResource("MainTestApriori_saveToFile.class");
        if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
            return new File(url.getPath());
        }
        return null;
    }

    /**
     * Saves the current node's information, validates the workflow, and reports the result in a dialog.
     */
    private void validateWorkflow() {
        infoPanel.saveInformation(infoPanel.currentNode);
        String error = drawPanel.validateTheWorkflow();
        if (error != null) {
            consolePanel.postStatusMessage("Validation failed: " + error);
            JOptionPane.showMessageDialog(this,
                    "The workflow is not valid. " + error,
                    "Error", JOptionPane.ERROR_MESSAGE);
        } else {
            consolePanel.postStatusMessage("Workflow validation successful.");
            JOptionPane.showMessageDialog(this,
                    "The workflow is valid.", "", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Validates and then executes the workflow in a background orchestrator thread.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting.
     */
    private void runWorkflow() throws InterruptedException {
        infoPanel.saveInformation(infoPanel.currentNode);

        String error = drawPanel.validateTheWorkflow();
        if (error != null) {
            consolePanel.postStatusMessage("Cannot run workflow: " + error);
            JOptionPane.showMessageDialog(this,
                    "The workflow is not valid. " + error,
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (checkBoxSeparatedProcess.isSelected() && !new File("spmf.jar").exists()) {
            consolePanel.postStatusMessage("spmf.jar not found. Running in same process.");
            JOptionPane.showMessageDialog(this,
                    "spmf.jar not found. The workflow will run in the same process.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            checkBoxSeparatedProcess.setSelected(false);
        }

        stopRequested.set(false);
        synchronized (activeProcesses) { activeProcesses.clear(); }
        synchronized (activeThreads)   { activeThreads.clear(); }

        switchToRunningMode();
        consolePanel.postStatusMessage("Starting workflow execution...");

        List<List<BranchNode>> levels = buildBFSLevels();
        Thread orchestrator = new Thread(
                () -> executeWorkflowLevels(levels), "workflow-orchestrator");
        orchestrator.setDaemon(true);
        orchestrator.setUncaughtExceptionHandler(this);
        orchestrator.start();
    }

    /**
     * Executes the workflow level by level, running all nodes within a level concurrently.
     *
     * @param levels BFS levels as produced by buildBFSLevels.
     */
    private void executeWorkflowLevels(List<List<BranchNode>> levels) {
        try {
            for (int i = 0; i < levels.size(); i++) {
                List<BranchNode> level = levels.get(i);
                if (stopRequested.get()) break;

                final int levelNum = i;
                SwingUtilities.invokeLater(() ->
                    consolePanel.postStatusMessage("Executing level " + (levelNum + 1) +
                        " with " + level.size() + " algorithm(s)..."));

                CountDownLatch latch = new CountDownLatch(level.size());
                AtomicBoolean levelFailed = new AtomicBoolean(false);

                for (BranchNode bn : level) {
                    if (stopRequested.get()) { latch.countDown(); continue; }
                    launchBranchNode(bn, latch, levelFailed);
                }

                latch.await();

                if (levelFailed.get()) {
                    SwingUtilities.invokeLater(() -> {
                        consolePanel.postStatusMessage("Workflow execution stopped due to algorithm failure.");
                        JOptionPane.showMessageDialog(this,
                                "One or more algorithms failed. Workflow execution stopped.",
                                "Error", JOptionPane.ERROR_MESSAGE);
                    });
                    break;
                }
            }

            if (!stopRequested.get()) {
                SwingUtilities.invokeLater(() ->
                    consolePanel.postStatusMessage("Workflow execution completed successfully."));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SwingUtilities.invokeLater(() ->
                consolePanel.postStatusMessage("Workflow execution interrupted."));
        } finally {
            SwingUtilities.invokeLater(this::resetUIAfterRunCompletion);
        }
    }

    /**
     * Launches the algorithm associated with the given branch node, either in-process or as a subprocess.
     *
     * @param bn          the branch node whose algorithm should be executed.
     * @param latch       decremented when the algorithm finishes.
     * @param levelFailed set to true if the algorithm fails.
     */
    private void launchBranchNode(BranchNode bn, CountDownLatch latch,
                                   AtomicBoolean levelFailed) {
        GroupOfNodes group  = bn.group;
        String choice       = group.nodeAlgorithm.name;
        String inputFile    = resolveInputFile(bn);
        String outputFile   = group.showOutput ? group.nodeOutput.outputFile : null;
        String[] parameters = group.nodeAlgorithm.getNonNullParameters();

        SimpleDateFormat fmt = new SimpleDateFormat("hh:mm:ss aa");
        consolePanel.postStatusMessage(
                "Algorithm '" + choice + "' is running... (" + fmt.format(new Date()) + ")");
        progressBar.setIndeterminate(true);

        if (checkBoxSeparatedProcess.isSelected()) {
            List<String> cmd = buildCommand(choice, inputFile, outputFile,
                    parameters, bn.isRoot(), group.showInput);
            NotifyingThread t = new NotifyingThread() {
                @Override
                public boolean doRun() throws Exception {
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectOutput(Redirect.INHERIT);
                    pb.redirectError(Redirect.INHERIT);
                    Process proc = pb.start();
                    synchronized (activeProcesses) { activeProcesses.add(proc); }
                    int exit = proc.waitFor();
                    synchronized (activeProcesses) { activeProcesses.remove(proc); }
                    return exit == 0;
                }
            };
            startAlgorithmThread(t, choice, latch, levelFailed);
        } else {
            NotifyingThread t = new NotifyingThread() {
                @Override
                public boolean doRun() throws Exception {
                    CommandProcessor.runAlgorithm(choice, inputFile, outputFile, parameters);
                    return true;
                }
            };
            startAlgorithmThread(t, choice, latch, levelFailed);
        }
    }

    /**
     * Wires completion callbacks onto the given thread and starts it.
     *
     * @param thread      the thread to start.
     * @param algName     human-readable algorithm name used in log messages.
     * @param latch       decremented when the thread completes.
     * @param levelFailed set to true if the thread reports failure.
     */
    private void startAlgorithmThread(NotifyingThread thread, String algName,
                                       CountDownLatch latch, AtomicBoolean levelFailed) {
        thread.setUncaughtExceptionHandler(this);
        thread.addListener((t, succeeded) -> {
            if (!succeeded) {
                levelFailed.set(true);
                consolePanel.postStatusMessage("Algorithm '" + algName + "' FAILED.");
            } else {
                consolePanel.postStatusMessage("Algorithm '" + algName + "' completed successfully.");
            }
            synchronized (activeThreads) { activeThreads.remove(thread); }
            latch.countDown();
        });
        synchronized (activeThreads) { activeThreads.add(thread); }
        thread.start();
    }

    /**
     * Performs a BFS over the workflow tree and groups BranchNodes by depth level.
     *
     * @return an ordered list of levels, each containing the nodes at that depth.
     */
    private List<List<BranchNode>> buildBFSLevels() {
        List<List<BranchNode>> levels = new ArrayList<>();
        Queue<BranchNode> queue = new LinkedList<>();
        Map<BranchNode, Integer> depthMap = new HashMap<>();

        for (BranchNode root : drawPanel.roots) {
            queue.add(root);
            depthMap.put(root, 0);
        }

        while (!queue.isEmpty()) {
            BranchNode current = queue.poll();
            int depth = depthMap.get(current);
            while (levels.size() <= depth) levels.add(new ArrayList<>());
            levels.get(depth).add(current);
            for (BranchNode child : current.children) {
                if (!depthMap.containsKey(child)) {
                    depthMap.put(child, depth + 1);
                    queue.add(child);
                }
            }
        }
        return levels;
    }

    /**
     * Determines the input file path for the given branch node based on its position in the tree.
     *
     * @param bn the branch node whose input file should be resolved.
     * @return the resolved input file path, or null if none applies.
     */
    private String resolveInputFile(BranchNode bn) {
        if (bn.isRoot()) {
            return bn.group.showInput ? bn.group.nodeInput.inputFile : null;
        }
        BranchNode parent = bn.parent;
        return (parent != null && parent.group.showOutput)
                ? parent.group.nodeOutput.outputFile : null;
    }

    /**
     * Builds the ordered list of command-line tokens needed to invoke spmf.jar for the given algorithm.
     *
     * @param algorithmName the SPMF algorithm identifier.
     * @param inputFile     the resolved input file path, may be null.
     * @param outputFile    the output file path, may be null.
     * @param parameters    algorithm-specific parameter values.
     * @param isRoot        true if this node is a root node.
     * @param showInput     true if this node uses an explicit input file.
     * @return the ordered list of command tokens.
     */
    private List<String> buildCommand(String algorithmName, String inputFile,
                                       String outputFile, String[] parameters,
                                       boolean isRoot, boolean showInput) {
        List<String> cmd = new ArrayList<>();
        cmd.add("java"); cmd.add("-jar"); cmd.add("spmf.jar"); cmd.add("run");
        cmd.add(algorithmName);
        if (isRoot) {
            if (showInput && inputFile != null) cmd.add(inputFile);
        } else {
            if (inputFile != null) cmd.add(inputFile);
        }
        if (outputFile != null && !outputFile.isEmpty()) cmd.add(outputFile);
        for (String p : parameters) {
            if (p != null && !p.isEmpty()) cmd.add(p);
        }
        return cmd;
    }

    /**
     * Forcibly stops all currently running algorithm processes and threads.
     */
    private void stopWorkflow() {
        stopRequested.set(true);
        synchronized (activeProcesses) {
            for (Process p : activeProcesses) p.destroyForcibly();
            activeProcesses.clear();
        }
        synchronized (activeThreads) {
            for (NotifyingThread t : activeThreads) {
                try { t.cancel(); } catch (UnsupportedOperationException ignored) { }
            }
            activeThreads.clear();
        }
        consolePanel.postStatusMessage("Workflow stopped by user.");
        SwingUtilities.invokeLater(this::resetUIAfterRunCompletion);
    }

    /**
     * Switches the UI into running mode by relabelling the Run button to Stop.
     */
    private void switchToRunningMode() {
        buttonRun.setText("Stop workflow");
        buttonRun.setIcon(new ImageIcon(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Stop24.gif")));
        for (ActionListener al : buttonRun.getActionListeners()) {
            buttonRun.removeActionListener(al);
        }
        buttonRun.addActionListener(e -> stopWorkflow());
        progressBar.setIndeterminate(true);
    }

    /**
     * Resets the UI back to idle mode after a workflow run finishes or is stopped.
     */
    private void resetUIAfterRunCompletion() {
        buttonRun.setText("Run the workflow");
        buttonRun.setIcon(new ImageIcon(
                MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Play24.gif")));
        buttonRun.setEnabled(true);
        for (ActionListener al : buttonRun.getActionListeners()) {
            buttonRun.removeActionListener(al);
        }
        buttonRun.addActionListener(e -> {
            try { runWorkflow(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        progressBar.setIndeterminate(false);
    }

    /**
     * Exports the workflow as a Windows BAT script file chosen by the user.
     */
    private void exportAsBATFile() {
        File file = chooseExportFile("BAT Scripts", "bat");
        if (file == null) return;
        List<List<BranchNode>> levels = buildBFSLevels();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            for (List<BranchNode> level : levels) {
                for (BranchNode bn : level) {
                    w.write(buildSingleLineCommand(bn, false));
                    w.newLine();
                }
            }
            consolePanel.postStatusMessage("Workflow exported as .bat to: " + file.getAbsolutePath());
        } catch (IOException e) {
            consolePanel.postStatusMessage("Error writing BAT file: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error writing BAT file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Exports the workflow as a Unix/Linux SH script file chosen by the user.
     */
    private void exportAsSHFile() {
        File file = chooseExportFile("SH Scripts", "sh");
        if (file == null) return;
        List<List<BranchNode>> levels = buildBFSLevels();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write("#!/bin/bash");
            w.newLine();
            for (List<BranchNode> level : levels) {
                for (BranchNode bn : level) {
                    w.write(buildSingleLineCommand(bn, true));
                    w.newLine();
                }
            }
            consolePanel.postStatusMessage("Workflow exported as .sh to: " + file.getAbsolutePath());
        } catch (IOException e) {
            consolePanel.postStatusMessage("Error writing SH file: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error writing SH file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Assembles a single command line string for the given branch node.
     *
     * @param bn             the branch node for which to build the command.
     * @param normalisePaths when true, backslashes are replaced with forward slashes.
     * @return the command line as a trimmed string.
     */
    private String buildSingleLineCommand(BranchNode bn, boolean normalisePaths) {
        GroupOfNodes group  = bn.group;
        String choice       = group.nodeAlgorithm.name;
        String inputFile    = resolveInputFile(bn);
        String outputFile   = group.showOutput ? group.nodeOutput.outputFile : null;
        String[] parameters = group.nodeAlgorithm.getNonNullParameters();

        if (normalisePaths) {
            if (inputFile  != null) inputFile  = inputFile.replace(File.separatorChar, '/');
            if (outputFile != null) outputFile = outputFile.replace(File.separatorChar, '/');
        }

        List<String> cmd = buildCommand(choice, inputFile, outputFile,
                parameters, bn.isRoot(), group.showInput);
        StringBuilder sb = new StringBuilder();
        for (String token : cmd) sb.append(token).append(' ');
        return sb.toString().trim();
    }

    /**
     * Opens a Save file chooser filtered to the given file extension and returns the chosen file.
     *
     * @param description the file-type description shown in the chooser.
     * @param extension   the file extension without a leading dot.
     * @return the chosen File, or null if the user cancelled.
     */
    private File chooseExportFile(String description, String extension) {
        File startDir = resolveStartDirectory();
        JFileChooser fc = (startDir != null)
                ? new JFileChooser(startDir.getAbsolutePath()) : new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(description, extension));
        fc.setAcceptAllFileFilterUsed(false);

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            PreferencesManager.getInstance().setInputFilePath(file.getParent());
            return file;
        }
        return null;
    }

    /**
     * Opens the SPMF documentation web page in the default browser.
     */
    private void openDocumentation() {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(
                    "http://philippe-fournier-viger.com/spmf/index.php?link=documentation.php"));
        } catch (IOException e) {
            consolePanel.postStatusMessage("Error opening documentation: " + e.getMessage());
        }
    }

    /**
     * Opens the memory usage viewer window.
     */
    private void openMemoryViewer() {
        MemoryViewer.displayMemoryChart();
    }

    /**
     * Opens the algorithm explorer window.
     */
    private void openAlgorithmExplorer() {
        new AlgorithmExplorer(false).setVisible(true);
    }

    /**
     * Handles uncaught exceptions thrown by algorithm threads and shows an error dialog.
     *
     * @param thread the thread that threw the exception.
     * @param e      the throwable that was not caught.
     */
    @Override
    public void uncaughtException(Thread thread, Throwable e) {
        if (e instanceof ThreadDeath) return;
        SwingUtilities.invokeLater(() -> {
            if (e instanceof NumberFormatException) {
                consolePanel.postStatusMessage("Parameter format error: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Parameter format error. Please check numeric parameters.\nERROR: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            } else {
                consolePanel.postStatusMessage("Algorithm error: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "An error occurred while running an algorithm.\nERROR: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
            resetUIAfterRunCompletion();
        });
    }
}