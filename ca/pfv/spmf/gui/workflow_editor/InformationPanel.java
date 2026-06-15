package ca.pfv.spmf.gui.workflow_editor;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.AlgorithmSelectorDialog;
import ca.pfv.spmf.gui.DialogSelectAlgorithmParameter;
import ca.pfv.spmf.gui.RecentFilesPopup;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;
import ca.pfv.spmf.gui.preferences.PreferencesManager;

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
 * Do not remove copyright and license information.
 */

/**
 * Information panel for the branching workflow editor that displays and allows editing of the currently selected node.
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
class InformationPanel extends JPanel implements DrawPanelListener {

    /** The parent frame (the workflow editor window). */
    JFrame parent;

    /** Reference to the draw panel, used to trigger relayout when node data changes. */
    private final DrawPanel drawPanel;

    /** All root branch nodes, used for algorithm suggestion filtering. */
    List<BranchNode> allRoots = null;

    /** The node currently displayed in the panel. */
    Node currentNode = null;

    // -----------------------------------------------------------------------
    // Output file sub-panel
    // -----------------------------------------------------------------------

    /** Text field showing the selected output file name. */
    private JTextField textFieldFileNameOutput;

    /** Sub-panel shown when an output-file node is selected. */
    private JPanel nodeFileOutputPanel;

    /** Button to open the file chooser for the output file. */
    private JButton buttonOutput;

    /** Button to show the recent output files popup. */
    private JButton buttonRecentOutput;

    // -----------------------------------------------------------------------
    // Input file sub-panel
    // -----------------------------------------------------------------------

    /** Text field showing the selected input file name. */
    private JTextField textFieldFileNameInput;

    /** Sub-panel shown when an input-file node is selected. */
    private JPanel nodeFileInputPanel;

    /** Button to open the file chooser for the input file. */
    private JButton buttonInput;

    /** Button to show the recent input files popup. */
    private JButton buttonRecentInput;

    /** Button to open the input file in a viewer. */
    JButton buttonViewInput;

    // -----------------------------------------------------------------------
    // Algorithm sub-panel
    // -----------------------------------------------------------------------

    /** Sub-panel shown when an algorithm node is selected. */
    private JPanel nodeAlgorithmPanel;

    /** Panel for selecting algorithm parameters. */
    private ParameterSelectionPanel parameterSelectionPanel;

    /** Read-only text field showing the selected algorithm name. */
    private JTextField textFieldAlgorithmName;

    /** Button to open the algorithm selector dialog. */
    private JButton buttonSelectAlgorithm;

    /** Button to show the recently used algorithms popup, filtered to valid suggestions. */
    private JButton buttonRecentAlgorithm;

    /** Button to open the documentation for the selected algorithm. */
    private JButton buttonExample;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a new information panel linked to the given draw panel.
     *
     * @param parent    the owner frame.
     * @param drawPanel the draw panel to refresh when node data changes.
     * @throws Exception if algorithm manager initialisation fails.
     */
    InformationPanel(JFrame parent, DrawPanel drawPanel) throws Exception {
        super();
        this.parent    = parent;
        this.drawPanel = drawPanel;

        createNodeInputFilePanel();
        add(nodeFileInputPanel);
        nodeFileInputPanel.setVisible(false);

        createNodeOutputFilePanel();
        add(nodeFileOutputPanel);
        nodeFileOutputPanel.setVisible(false);

        createNodeAlgorithmPanel();
        add(nodeAlgorithmPanel);
        nodeAlgorithmPanel.setVisible(false);
    }

    // -----------------------------------------------------------------------
    // Sub-panel builders
    // -----------------------------------------------------------------------

    /**
     * Builds the sub-panel shown when an output-file node is selected.
     */
    private void createNodeOutputFilePanel() {
        nodeFileOutputPanel = new JPanel();
        nodeFileOutputPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel labelFileName = new JLabel("File:");
        textFieldFileNameOutput = new JTextField(30);
        textFieldFileNameOutput.setEditable(false);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST; gbc.weighty = 0;
        nodeFileOutputPanel.add(labelFileName, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        nodeFileOutputPanel.add(textFieldFileNameOutput, gbc);

        buttonOutput = new JButton("Select");
        buttonOutput.setToolTipText("Select an output file");
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 2; gbc.gridy = 1;
        nodeFileOutputPanel.add(buttonOutput, gbc);

        buttonRecentOutput = new JButton("Recent \u25BC");
        buttonRecentOutput.setToolTipText("Show recently used output files");
        gbc.gridx = 3; gbc.gridy = 1;
        nodeFileOutputPanel.add(buttonRecentOutput, gbc);

        gbc.gridx = 0; gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.weighty = 1;
        nodeFileOutputPanel.add(Box.createVerticalGlue(), gbc);

        buttonOutput.addActionListener(e -> {
            ((NodeFileOutput) currentNode).askUserToReplaceFile(parent);
            notifyNodeSelected(currentNode);
            textFieldFileNameOutput.setText(currentNode.name);
            saveRecentOutputFile(((NodeFileOutput) currentNode).outputFile);
            drawPanel.relayoutAndRepaint();
            parent.repaint();
        });

        buttonRecentOutput.addActionListener(e -> {
            List<String> recent = PreferencesManager.getInstance().getRecentOutputFiles();
            RecentFilesPopup.show(buttonRecentOutput, recent, path -> {
                NodeFileOutput outNode = (NodeFileOutput) currentNode;
                java.io.File f = new java.io.File(path);
                outNode.name       = f.getName();
                outNode.outputFile = path;
                outNode.rectangle  = null;
                textFieldFileNameOutput.setText(outNode.name);
                drawPanel.relayoutAndRepaint();
                parent.repaint();
            });
        });
    }

    /**
     * Builds the sub-panel shown when an input-file node is selected.
     */
    private void createNodeInputFilePanel() {
        nodeFileInputPanel = new JPanel();
        nodeFileInputPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel labelFileName = new JLabel("File:");
        textFieldFileNameInput = new JTextField(30);
        textFieldFileNameInput.setEditable(false);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST; gbc.weighty = 0;
        nodeFileInputPanel.add(labelFileName, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        nodeFileInputPanel.add(textFieldFileNameInput, gbc);

        buttonInput = new JButton("Select");
        buttonInput.setToolTipText("Choose an input file");
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 2; gbc.gridy = 1;
        nodeFileInputPanel.add(buttonInput, gbc);

        buttonRecentInput = new JButton("Recent \u25BC");
        buttonRecentInput.setToolTipText("Show recently used input files");
        gbc.gridx = 3; gbc.gridy = 1;
        nodeFileInputPanel.add(buttonRecentInput, gbc);

        buttonViewInput = new JButton("View");
        buttonViewInput.setToolTipText("View the input file");
        buttonViewInput.setEnabled(false);
        gbc.gridx = 4; gbc.gridy = 1;
        nodeFileInputPanel.add(buttonViewInput, gbc);

        gbc.gridx = 0; gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.weighty = 1;
        nodeFileInputPanel.add(Box.createVerticalGlue(), gbc);

        buttonInput.addActionListener(e -> {
            ((NodeFileInput) currentNode).askUserToChooseFile(parent);
            notifyNodeSelected(currentNode);
            textFieldFileNameInput.setText(currentNode.name);
            if (currentNode.name != null && !currentNode.name.isEmpty()) {
                buttonViewInput.setEnabled(true);
                saveRecentInputFile(((NodeFileInput) currentNode).inputFile);
            }
            drawPanel.relayoutAndRepaint();
            parent.repaint();
        });

        buttonRecentInput.addActionListener(e -> {
            List<String> recent = PreferencesManager.getInstance().getRecentInputFiles();
            RecentFilesPopup.show(buttonRecentInput, recent, path -> {
                NodeFileInput inNode = (NodeFileInput) currentNode;
                java.io.File f = new java.io.File(path);
                inNode.name      = f.getName();
                inNode.inputFile = path;
                inNode.rectangle = null;
                textFieldFileNameInput.setText(inNode.name);
                buttonViewInput.setEnabled(true);
                drawPanel.relayoutAndRepaint();
                parent.repaint();
            });
        });

        buttonViewInput.addActionListener(e ->
                openInputFileWithViewer(((NodeFileInput) currentNode).inputFile));
    }

    /**
     * Builds the sub-panel shown when an algorithm node is selected.
     *
     * @throws Exception if the algorithm manager cannot be accessed.
     */
    private void createNodeAlgorithmPanel() throws Exception {
        nodeAlgorithmPanel = new JPanel();
        nodeAlgorithmPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST; gbc.weighty = 0;
        nodeAlgorithmPanel.add(new JLabel("Algorithm:"), gbc);

        // Build algorithm row panel (text field + Select + Recent + Guide buttons)
        JPanel algorithmRowPanel = new JPanel(new GridBagLayout());
        algorithmRowPanel.setOpaque(false);

        textFieldAlgorithmName = new JTextField(15);
        textFieldAlgorithmName.setEditable(false);
        textFieldAlgorithmName.setToolTipText("Currently selected algorithm");

        GridBagConstraints rowGbc = new GridBagConstraints();
        rowGbc.gridx = 0; rowGbc.gridy = 0;
        rowGbc.fill = GridBagConstraints.HORIZONTAL; rowGbc.weightx = 1;
        rowGbc.anchor = GridBagConstraints.WEST;
        algorithmRowPanel.add(textFieldAlgorithmName, rowGbc);

        buttonSelectAlgorithm = new JButton("Select");
        buttonSelectAlgorithm.setToolTipText("Open the algorithm browser");
        rowGbc.gridx = 1; rowGbc.weightx = 0;
        rowGbc.fill = GridBagConstraints.NONE;
        rowGbc.insets = new java.awt.Insets(0, 4, 0, 0);
        algorithmRowPanel.add(buttonSelectAlgorithm, rowGbc);

        buttonRecentAlgorithm = new JButton("Recent \u25BC");
        buttonRecentAlgorithm.setToolTipText("Show recently used algorithms valid for this position");
        rowGbc.gridx = 2;
        rowGbc.insets = new java.awt.Insets(0, 4, 0, 0);
        algorithmRowPanel.add(buttonRecentAlgorithm, rowGbc);

        buttonExample = new JButton("Guide");
        buttonExample.setEnabled(false);
        buttonExample.setToolTipText("Open documentation for this algorithm");
        rowGbc.gridx = 3;
        rowGbc.insets = new java.awt.Insets(0, 4, 0, 0);
        algorithmRowPanel.add(buttonExample, rowGbc);

        // Add algorithm row panel to main panel
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        nodeAlgorithmPanel.add(algorithmRowPanel, gbc);

        // Parameters label
        gbc.gridx = 0; gbc.gridy = 1; gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        nodeAlgorithmPanel.add(new JLabel("Parameters:"), gbc);

        // Parameters panel
        parameterSelectionPanel = new ParameterSelectionPanel(null);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1; gbc.weighty = 1;
        nodeAlgorithmPanel.add(parameterSelectionPanel, gbc);

        buttonSelectAlgorithm.addActionListener(e -> openAlgorithmSelectorDialog());

        buttonRecentAlgorithm.addActionListener(e -> {
            try {
                List<String> filtered = buildFilteredRecentAlgorithms();
                if (filtered.isEmpty()) {
                    JOptionPane.showMessageDialog(parent,
                            "No recently used algorithms are valid for this position in the workflow.",
                            "Recent Algorithms", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                RecentFilesPopup.show(buttonRecentAlgorithm, filtered, algorithmName -> {
                    if (algorithmName != null && !algorithmName.isEmpty()) {
                        updateUserInterfaceForAlgorithm(algorithmName, null, false);
                        saveRecentAlgorithm(algorithmName);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        buttonExample.addActionListener(e -> {
            try {
                openHelpWebPageForAlgorithm(textFieldAlgorithmName.getText());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Algorithm selector dialog
    // -----------------------------------------------------------------------

    /**
     * Opens the algorithm selector dialog, pre-filtering suggestions based on the parent node's output type.
     */
    private void openAlgorithmSelectorDialog() {
        try {
            Set<String> suggested = buildSuggestedSet();
            Set<String> highlightSet = (suggested == null) ? null : suggested;

            AlgorithmSelectorDialog dialog = new AlgorithmSelectorDialog(
                    (Frame) javax.swing.SwingUtilities.getWindowAncestor(this),
                    true, true, true, true, true,
                    highlightSet);
            dialog.setVisible(true);

            String chosen = dialog.getSelectedAlgorithm();
            if (chosen != null && !chosen.isEmpty()) {
                updateUserInterfaceForAlgorithm(chosen, null, false);
                saveRecentAlgorithm(chosen);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Builds the set of suggested algorithm names based on the parent node's output type, or null if all are suggested.
     *
     * @return set of suggested names, or null if no filtering should be applied.
     * @throws Exception if the algorithm manager cannot be accessed.
     */
    private Set<String> buildSuggestedSet() throws Exception {
        if (!(currentNode instanceof NodeAlgorithm)) return null;

        BranchNode owningBranchNode = findBranchNodeForGroup(currentNode.group);
        if (owningBranchNode == null || owningBranchNode.isRoot()) return null;

        BranchNode parentBranchNode = owningBranchNode.parent;
        DescriptionOfAlgorithm parentAlgorithm =
                AlgorithmManager.getInstance().getDescriptionOfAlgorithm(
                        parentBranchNode.group.nodeAlgorithm.name);
        if (parentAlgorithm == null) return new HashSet<>();

        String[] outputTypes = parentAlgorithm.getOutputFileTypes();
        if (outputTypes == null) return new HashSet<>();

        List<String> allNames = AlgorithmManager.getInstance()
                .getListOfAlgorithmsAsString(true, true, true, true, true);

        Set<String> suggested = new HashSet<>();
        for (String name : allNames) {
            if (name == null || name.trim().isEmpty() || name.trim().startsWith("--")) continue;

            if ("Open_text_file_with_SPMF_text_editor".equals(name)
                    || "Open_text_file_with_system_text_editor".equals(name)
                    || "Open_text_file_with_pattern_viewer".equals(name)
                    || "Visualize_With_ItemItemset_Matrix".equals(name)) {
                suggested.add(name);
                continue;
            }

            DescriptionOfAlgorithm candidate =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(name);
            if (candidate == null) continue;

            String[] inputTypes = candidate.getInputFileTypes();
            if (inputTypes != null && hasCommonType(outputTypes, inputTypes)) {
                suggested.add(name);
            }
        }
        return suggested;
    }

    /**
     * Returns the recent algorithms list filtered to only those valid for the current workflow position.
     * When the current node is a root, all recent algorithms are returned unchanged.
     * When the current node is a non-root, only entries present in the suggested set are kept, in recency order.
     *
     * @return the filtered list of recent algorithm names, never null but may be empty.
     * @throws Exception if the algorithm manager cannot be accessed.
     */
    private List<String> buildFilteredRecentAlgorithms() throws Exception {
        List<String> recent = PreferencesManager.getInstance().getRecentAlgorithms();
        Set<String> suggested = buildSuggestedSet();

        // null means root node: no type-based filtering applies, return all recent entries
        if (suggested == null) {
            return new ArrayList<>(recent);
        }

        // non-root node: keep only recent entries that are in the suggested set
        List<String> filtered = new ArrayList<>();
        for (String name : recent) {
            if (suggested.contains(name)) {
                filtered.add(name);
            }
        }
        return filtered;
    }

    // -----------------------------------------------------------------------
    // BranchingDrawPanelListener callbacks
    // -----------------------------------------------------------------------

    /**
     * Saves any unsaved edits for the previously displayed node, then loads the data of the newly selected node.
     *
     * @param node the newly selected node, or null if the selection was cleared.
     */
    @Override
    public void notifyNodeSelected(Node node) {
        if (node == currentNode) return;
        saveInformation(currentNode);
        currentNode = node;

        boolean isOutput    = node instanceof NodeFileOutput;
        boolean isInput     = node instanceof NodeFileInput;
        boolean isAlgorithm = node instanceof NodeAlgorithm;

        nodeFileOutputPanel.setVisible(isOutput);
        nodeFileInputPanel.setVisible(isInput);

        if (isAlgorithm) {
            nodeAlgorithmPanel.setVisible(true);
            updateUserInterfaceForAlgorithm(node.name,
                    ((NodeAlgorithm) node).parameters, false);
        } else {
            nodeAlgorithmPanel.setVisible(false);
        }

        if (isOutput) textFieldFileNameOutput.setText(currentNode.name);
        if (isInput)  textFieldFileNameInput.setText(currentNode.name);

        if (node == null) {
            setBorder(BorderFactory.createTitledBorder(""));
        } else {
            setBorder(BorderFactory.createTitledBorder(node.getType()));
        }
    }

    /**
     * Stores the updated list of root nodes for use in algorithm suggestion filtering.
     *
     * @param roots the current list of root BranchNodes.
     */
    @Override
    public void notifyOfListOfRootNodes(List<BranchNode> roots) {
        this.allRoots = roots;
    }

    /**
     * Called when the add-algorithm button enabledness changes; not used by this panel.
     *
     * @param hasOutput true if adding an algorithm is currently allowed.
     */
    @Override
    public void notifyHasOutputNode(boolean hasOutput) {
        // nothing to do
    }

    /**
     * Called when the remove-algorithm button enabledness changes; not used by this panel.
     *
     * @param canRemove true if the selected node may be removed.
     */
    @Override
    public void notifyCanRemoveSelectedNode(boolean canRemove) {
        // nothing to do
    }

    // -----------------------------------------------------------------------
    // Algorithm UI update
    // -----------------------------------------------------------------------

    /**
     * Updates all UI elements for the given algorithm name and triggers a draw-panel relayout.
     *
     * @param algorithmName      the algorithm name to display.
     * @param parameters         existing parameter values to populate, or null.
     * @param comboBoxHasChanged unused; kept for internal consistency.
     */
    private void updateUserInterfaceForAlgorithm(String algorithmName,
                                                  String[] parameters,
                                                  boolean comboBoxHasChanged) {
        try {
            DescriptionOfAlgorithm algorithm =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);

            boolean hasOutput = algorithm != null && algorithm.getOutputFileTypes() != null;
            boolean hasInput  = algorithm != null && algorithm.getInputFileTypes()  != null;

            textFieldAlgorithmName.setText(algorithmName != null ? algorithmName : "");

            updateExampleButton(algorithm);
            updateParameterPanel(algorithm, parameters);
            ((NodeAlgorithm) currentNode).updateAlgorithmChoice(algorithmName, hasOutput, hasInput);

            drawPanel.relayoutAndRepaint();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Enables or disables the example button depending on whether an algorithm is selected.
     *
     * @param algorithm the selected algorithm description, or null.
     */
    private void updateExampleButton(DescriptionOfAlgorithm algorithm) {
        buttonExample.setEnabled(algorithm != null);
    }

    /**
     * Populates the parameter selection panel for the given algorithm and optional existing values.
     *
     * @param algorithm  the algorithm whose parameters should be shown.
     * @param parameters existing parameter values, or null.
     */
    private void updateParameterPanel(DescriptionOfAlgorithm algorithm, String[] parameters) {
        parameterSelectionPanel.update(algorithm);
        parameterSelectionPanel.setParameterValues(parameters);
    }

    /**
     * Opens the documentation web page for the given algorithm in the default browser.
     *
     * @param algorithmName the algorithm name whose documentation should be opened.
     * @throws Exception if the algorithm is not found in the manager.
     */
    private void openHelpWebPageForAlgorithm(String algorithmName) throws Exception {
        DescriptionOfAlgorithm algorithm =
                AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
        if (algorithm != null) {
            try {
                java.awt.Desktop.getDesktop().browse(
                        java.net.URI.create(algorithm.getURLOfDocumentation()));
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Opens the given input file with the viewer appropriate for the current algorithm's input type.
     *
     * @param inputFile the path of the input file to view.
     */
    protected void openInputFileWithViewer(String inputFile) {
        try {
            if (inputFile == null || inputFile.isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "No input file is selected. Please click \"...\" to select one.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String algorithmName = currentNode.group.nodeAlgorithm.name;
            DescriptionOfAlgorithm algorithm =
                    AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
            DescriptionOfAlgorithm viewer =
                    AlgorithmManager.getInstance().getViewerFor(algorithm.getInputFileTypes());

            String[] params;
            if (viewer.getParametersDescription().length > 0) {
                params = new String[viewer.getParametersDescription().length];
                for (int i = 0; i < viewer.getParametersDescription().length; i++) {
                    params[i] = askUserValueForParameter(viewer.getParametersDescription()[i]);
                }
            } else {
                params = new String[]{};
            }
            viewer.runAlgorithm(params, inputFile, null);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while opening the file. ERROR = " + e.toString(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Asks the user to enter a value for a single parameter via a dialog.
     *
     * @param paramDescription the description of the parameter to request.
     * @return the string value entered by the user.
     */
    private String askUserValueForParameter(DescriptionOfParameter paramDescription) {
        DialogSelectAlgorithmParameter dialog =
                new DialogSelectAlgorithmParameter(paramDescription, parent);
        return dialog.getUserInput();
    }

    // -----------------------------------------------------------------------
    // Suggestion helpers
    // -----------------------------------------------------------------------

    /**
     * Walks the entire tree to find the BranchNode whose GroupOfNodes is the same object as the given group.
     *
     * @param group the group to search for.
     * @return the matching BranchNode, or null if not found.
     */
    private BranchNode findBranchNodeForGroup(GroupOfNodes group) {
        if (allRoots == null) return null;
        for (BranchNode root : allRoots) {
            BranchNode found = findBranchNodeForGroupDFS(root, group);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * DFS helper that searches the subtree rooted at bn for a node owning the given group.
     *
     * @param bn    the subtree root to search.
     * @param group the group to locate.
     * @return the matching BranchNode, or null if not found.
     */
    private BranchNode findBranchNodeForGroupDFS(BranchNode bn, GroupOfNodes group) {
        if (bn.group == group) return bn;
        for (BranchNode child : bn.children) {
            BranchNode result = findBranchNodeForGroupDFS(child, group);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Returns true if the two type arrays share at least one compatible element.
     *
     * @param outputTypes the output types of the parent algorithm.
     * @param inputTypes  the input types of the candidate algorithm.
     * @return true if there is a compatible type pair.
     */
    public boolean hasCommonType(String[] outputTypes, String[] inputTypes) {
        for (String inputType : inputTypes) {
            for (String outputType : outputTypes) {
                if (!"Patterns".equals(outputType)
                        && !"Database of instances".equals(outputType)) {
                    if (outputType.equals(inputType)) return true;
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Recent file helpers
    // -----------------------------------------------------------------------

    /**
     * Saves the given path to the recent input files list in preferences.
     *
     * @param path the full path to record.
     */
    private void saveRecentInputFile(String path) {
        if (path == null || path.isEmpty()) return;
        PreferencesManager.getInstance().addRecentInputFile(path);
    }

    /**
     * Saves the given path to the recent output files list in preferences.
     *
     * @param path the full path to record.
     */
    private void saveRecentOutputFile(String path) {
        if (path == null || path.isEmpty()) return;
        PreferencesManager.getInstance().addRecentOutputFile(path);
    }

    /**
     * Saves the given algorithm name to the recent algorithms list in preferences.
     *
     * @param algorithmName the algorithm name to record.
     */
    private void saveRecentAlgorithm(String algorithmName) {
        if (algorithmName == null || algorithmName.isEmpty()) return;
        PreferencesManager.getInstance().addRecentAlgorithm(algorithmName);
    }

    // -----------------------------------------------------------------------
    // Save helper
    // -----------------------------------------------------------------------

    /**
     * Reads the current parameter values from the UI and writes them back to the given node.
     *
     * @param node the node whose parameters should be saved, may be null.
     */
    void saveInformation(Node node) {
        if (node instanceof NodeAlgorithm) {
            ((NodeAlgorithm) node).parameters = parameterSelectionPanel.getParameterValues();
        }
    }
}