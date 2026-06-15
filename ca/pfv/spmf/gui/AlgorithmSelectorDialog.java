package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2021 Philippe Fournier-Viger
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
 * Do not remove copyright or license information.
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * Modal dialog for selecting an SPMF algorithm, with optional highlighting of suggested algorithms.
 *
 * <p>A search bar spans the full width at the top. Below it the left side
 * shows a category tree and the right side shows algorithm information.
 * When a set of suggested algorithm names is provided, suggested algorithms
 * are shown in black and non-suggested ones are shown in light gray.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmSelectorDialog extends JDialog {

    // --- Fonts -----------------------------------------------------------
    private static final Font  FONT_CATEGORY  = new Font(Font.DIALOG, Font.BOLD,  12);
    private static final Font  FONT_ALGORITHM = new Font(Font.DIALOG, Font.PLAIN, 11);
    private static final Font  FONT_LABEL     = new Font(Font.DIALOG, Font.BOLD,  11);

    /** Color for suggested (compatible) algorithms. */
    private static final Color COLOR_SUGGESTED     = Color.BLACK;

    /** Color for non-suggested (incompatible) algorithms. */
    private static final Color COLOR_NOT_SUGGESTED = Color.LIGHT_GRAY;

    // --- Widgets ---------------------------------------------------------
    private final JTextField             searchField;
    private final JTree                  tree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel       treeModel;

    private final JTextField       fieldName;
    private final JTextField       fieldInputType;
    private final JTextField       fieldOutputType;
    private final JTable           tableParameters;
    private final DefaultTableModel parametersModel;
    private final JButton          btnDocumentation;
    private final JButton          btnOK;
    private final JButton          btnCancel;

    // --- State -----------------------------------------------------------
    /** Map: Category → sorted algorithm names. */
    private final Map<String, List<String>> categoryMap;

    /** The algorithm name chosen by the user, or null if cancelled. */
    private String selectedAlgorithm = null;

    /** Cache of algorithm descriptions for quick lookup. */
    private final Map<String, DescriptionOfAlgorithm> algorithmCache;

    /**
     * Optional set of suggested algorithm names.
     * If null, all algorithms are treated as equally suggested (no highlighting).
     * If non-null, algorithms in the set are highlighted in black and others in gray.
     */
    private final Set<String> suggestedAlgorithms;

    // =====================================================================
    // Construction — original signature (no suggested set)
    // =====================================================================

    /**
     * Creates the algorithm selector dialog without suggested-algorithm highlighting.
     *
     * @param owner                the parent frame.
     * @param showViewAndTransform include view-and-transform algorithms.
     * @param showGenerateData     include data-generation algorithms.
     * @param showTools            include tool algorithms.
     * @param showAlgorithms       include data-mining algorithms.
     * @param showExperimentTools  include experiment-tool algorithms.
     * @throws Exception if AlgorithmManager cannot be initialised.
     */
    public AlgorithmSelectorDialog(Frame owner,
                                   boolean showViewAndTransform,
                                   boolean showGenerateData,
                                   boolean showTools,
                                   boolean showAlgorithms,
                                   boolean showExperimentTools) throws Exception {
        this(owner, showViewAndTransform, showGenerateData, showTools,
             showAlgorithms, showExperimentTools, null);
    }

    // =====================================================================
    // Construction — extended signature (with optional suggested set)
    // =====================================================================

    /**
     * Creates the algorithm selector dialog with optional suggested-algorithm highlighting.
     *
     * <p>When {@code suggestedAlgorithms} is non-null, algorithms whose names
     * are in the set are displayed in black (suggested/compatible) and all
     * others are displayed in light gray (not suggested).</p>
     *
     * @param owner                the parent frame.
     * @param showViewAndTransform include view-and-transform algorithms.
     * @param showGenerateData     include data-generation algorithms.
     * @param showTools            include tool algorithms.
     * @param showAlgorithms       include data-mining algorithms.
     * @param showExperimentTools  include experiment-tool algorithms.
     * @param suggestedAlgorithms  set of suggested algorithm names, or null to disable highlighting.
     * @throws Exception if AlgorithmManager cannot be initialised.
     */
    public AlgorithmSelectorDialog(Frame owner,
                                   boolean showViewAndTransform,
                                   boolean showGenerateData,
                                   boolean showTools,
                                   boolean showAlgorithms,
                                   boolean showExperimentTools,
                                   Set<String> suggestedAlgorithms) throws Exception {
        super(owner, "Select an Algorithm", true);

        this.suggestedAlgorithms = suggestedAlgorithms;

        algorithmCache = new java.util.HashMap<>();
        categoryMap    = buildCategoryMap(showViewAndTransform, showGenerateData,
                                          showTools, showAlgorithms, showExperimentTools);

        // -----------------------------------------------------------------
        // Top bar: full-width search field
        // -----------------------------------------------------------------
        JLabel lblSearch = new JLabel("Search: ");
        lblSearch.setFont(FONT_LABEL);
        searchField = new JTextField();
        searchField.setToolTipText("Type to filter algorithms by name");

        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        searchBar.add(lblSearch,   BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);

        // -----------------------------------------------------------------
        // Left side: category tree with optional suggestion highlighting
        // -----------------------------------------------------------------
        rootNode  = new DefaultMutableTreeNode("Algorithms");
        treeModel = new DefaultTreeModel(rootNode);
        tree      = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel()
            .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(
                    JTree t, Object value, boolean sel, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(
                        t, value, sel, expanded, leaf, row, hasFocus);

                if (leaf) {
                    setFont(FONT_ALGORITHM);
                    String algoName = value.toString();
                    // Apply suggestion colouring only when not selected
                    // (selection background already provides sufficient contrast)
                    if (!sel && suggestedAlgorithms != null) {
                        if (suggestedAlgorithms.contains(algoName)) {
                            setForeground(COLOR_SUGGESTED);
                        } else {
                            setForeground(COLOR_NOT_SUGGESTED);
                        }
                    } else if (!sel) {
                        setForeground(COLOR_SUGGESTED);
                    }
                } else {
                    setFont(FONT_CATEGORY);
                }
                return this;
            }
        });

        // Double-click on a leaf confirms selection
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) confirmSelection();
            }
        });

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setPreferredSize(new Dimension(300, 440));

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 4));
        leftPanel.add(treeScroll, BorderLayout.CENTER);

        // Add a legend label when suggestion mode is active
        if (suggestedAlgorithms != null) {
            JLabel legend = new JLabel(
                    "<html><font color='#000000'>&#9632;</font> Compatible &nbsp;"
                    + "<font color='#C0C0C0'>&#9632;</font> Not compatible</html>");
            legend.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
            legend.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            leftPanel.add(legend, BorderLayout.SOUTH);
        }

        // -----------------------------------------------------------------
        // Right side: "Algorithm Information" panel with titled border
        // -----------------------------------------------------------------
        fieldName       = createReadOnlyField();
        fieldInputType  = createReadOnlyField();
        fieldOutputType = createReadOnlyField();

        parametersModel = new DefaultTableModel(
                new Object[0][0], new String[]{"Parameter", "Type", "Optional"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableParameters = new JTable(parametersModel);
        tableParameters.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tableParameters.getTableHeader().setReorderingAllowed(false);
        JScrollPane paramScroll = new JScrollPane(tableParameters);

        btnDocumentation = new JButton("Documentation");
        btnDocumentation.setEnabled(false);
        btnDocumentation.addActionListener(e -> openDocumentation());

        JPanel infoContent = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.insets  = new java.awt.Insets(3, 4, 3, 4);
        gc.anchor  = java.awt.GridBagConstraints.WEST;
        gc.fill    = java.awt.GridBagConstraints.HORIZONTAL;

        int infoRow = 0;
        addInfoRow(infoContent, gc, infoRow++, "Name:",        fieldName);
        addInfoRow(infoContent, gc, infoRow++, "Input type:",  fieldInputType);
        addInfoRow(infoContent, gc, infoRow++, "Output type:", fieldOutputType);

        gc.gridx = 0; gc.gridy = infoRow; gc.gridwidth = 1; gc.weightx = 0;
        JLabel lblParams = new JLabel("Parameters:");
        lblParams.setFont(FONT_LABEL);
        infoContent.add(lblParams, gc);
        infoRow++;

        gc.gridx = 0; gc.gridy = infoRow; gc.gridwidth = 2;
        gc.weightx = 1; gc.weighty = 1;
        gc.fill = java.awt.GridBagConstraints.BOTH;
        paramScroll.setMinimumSize(new Dimension(100, 80));
        infoContent.add(paramScroll, gc);
        infoRow++;

        gc.gridx = 0; gc.gridy = infoRow; gc.gridwidth = 2;
        gc.weightx = 0; gc.weighty = 0;
        gc.fill   = java.awt.GridBagConstraints.NONE;
        gc.anchor = java.awt.GridBagConstraints.WEST;
        gc.insets = new java.awt.Insets(6, 4, 4, 4);
        infoContent.add(btnDocumentation, gc);

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 4, 8, 8),
                BorderFactory.createTitledBorder(
                        BorderFactory.createEtchedBorder(),
                        "Algorithm Information")));
        infoPanel.add(infoContent, BorderLayout.CENTER);
        infoPanel.setPreferredSize(new Dimension(420, 440));

        // -----------------------------------------------------------------
        // Centre: tree (left) + info panel (right)
        // -----------------------------------------------------------------
        JPanel centrePanel = new JPanel(new BorderLayout(0, 0));
        centrePanel.add(leftPanel,  BorderLayout.WEST);
        centrePanel.add(infoPanel,  BorderLayout.CENTER);

        // -----------------------------------------------------------------
        // Bottom: OK / Cancel buttons
        // -----------------------------------------------------------------
        btnOK     = new JButton("OK");
        btnCancel = new JButton("Cancel");
        btnOK.addActionListener(e -> confirmSelection());
        btnCancel.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnPanel.add(btnOK);
        btnPanel.add(btnCancel);

        // -----------------------------------------------------------------
        // Assemble the dialog
        // -----------------------------------------------------------------
        setLayout(new BorderLayout());
        add(searchBar,   BorderLayout.NORTH);
        add(centrePanel, BorderLayout.CENTER);
        add(btnPanel,    BorderLayout.SOUTH);

        rebuildTree("");

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override public void valueChanged(TreeSelectionEvent e) { handleTreeSelection(); }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onSearchChanged(); }
            @Override public void removeUpdate(DocumentEvent e)  { onSearchChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onSearchChanged(); }
        });

        pack();
        setMinimumSize(new Dimension(780, 560));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    // =====================================================================
    // Public API
    // =====================================================================

    /**
     * Returns the algorithm name selected by the user, or null if cancelled.
     *
     * @return selected algorithm name, or null.
     */
    public String getSelectedAlgorithm() {
        return selectedAlgorithm;
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    /**
     * Builds a map of Category to sorted algorithm names, skipping separator entries.
     *
     * @param showViewAndTransform filter flag.
     * @param showGenerateData     filter flag.
     * @param showTools            filter flag.
     * @param showAlgorithms       filter flag.
     * @param showExperimentTools  filter flag.
     * @return sorted category map.
     * @throws Exception if AlgorithmManager cannot be initialised.
     */
    private Map<String, List<String>> buildCategoryMap(
            boolean showViewAndTransform, boolean showGenerateData,
            boolean showTools, boolean showAlgorithms,
            boolean showExperimentTools) throws Exception {

        List<String> names = AlgorithmManager.getInstance()
                .getListOfAlgorithmsAsString(showViewAndTransform,
                        showGenerateData, showTools, showAlgorithms, showExperimentTools);

        Map<String, List<String>> map = new LinkedHashMap<>();

        for (String name : names) {
            if (name == null || name.trim().isEmpty() || name.trim().startsWith("--")) {
                continue;
            }

            DescriptionOfAlgorithm desc = null;
            try {
                desc = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(name);
                algorithmCache.put(name, desc);
            } catch (Exception ignored) { }

            String category = (desc != null && desc.getAlgorithmCategory() != null)
                    ? desc.getAlgorithmCategory() : "Other";

            map.computeIfAbsent(category, k -> new ArrayList<>()).add(name);
        }

        List<String> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);
        Map<String, List<String>> sorted = new LinkedHashMap<>();
        for (String key : sortedKeys) {
            List<String> algs = map.get(key);
            Collections.sort(algs);
            sorted.put(key, algs);
        }
        return sorted;
    }

    /**
     * Rebuilds the tree filtering by the given lower-cased string and expands all nodes when filtering.
     *
     * @param filter lower-cased filter string, may be empty.
     */
    private void rebuildTree(String filter) {
        rootNode.removeAllChildren();
        boolean filtering = !filter.isEmpty();

        for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
            String       category = entry.getKey();
            List<String> algs     = entry.getValue();

            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(category);
            for (String alg : algs) {
                if (!filtering || alg.toLowerCase().contains(filter)) {
                    catNode.add(new DefaultMutableTreeNode(alg));
                }
            }
            if (catNode.getChildCount() > 0) {
                rootNode.add(catNode);
            }
        }

        treeModel.reload();

        if (filtering) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        }
    }

    /** Invoked when the search field text changes. */
    private void onSearchChanged() {
        rebuildTree(searchField.getText().trim().toLowerCase());
    }

    /** Invoked when the tree selection changes. */
    private void handleTreeSelection() {
        TreePath path = tree.getSelectionPath();
        if (path == null) { clearDetails(); return; }

        DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!node.isLeaf()) { clearDetails(); return; }

        String algorithmName = node.getUserObject().toString();
        DescriptionOfAlgorithm desc = algorithmCache.get(algorithmName);
        if (desc == null) {
            try {
                desc = AlgorithmManager.getInstance()
                           .getDescriptionOfAlgorithm(algorithmName);
                algorithmCache.put(algorithmName, desc);
            } catch (Exception ex) { clearDetails(); return; }
        }
        displayDetails(algorithmName, desc);
    }

    /**
     * Populates the information panel with details of the selected algorithm.
     *
     * @param algoName the algorithm name.
     * @param desc     the algorithm description.
     */
    private void displayDetails(String algoName, DescriptionOfAlgorithm desc) {
        fieldName.setText(algoName);

        String[] in = desc.getInputFileTypes();
        fieldInputType.setText((in != null && in.length > 0) ? in[in.length - 1] : "N/A");

        String[] out = desc.getOutputFileTypes();
        fieldOutputType.setText((out != null && out.length > 0) ? out[out.length - 1] : "N/A");

        parametersModel.setRowCount(0);
        DescriptionOfParameter[] params = desc.getParametersDescription();
        if (params != null) {
            for (DescriptionOfParameter p : params) {
                parametersModel.addRow(new Object[]{
                        p.getName(),
                        p.getParameterType().getSimpleName(),
                        p.isOptional() ? "Yes" : "No"
                });
            }
        }
        btnDocumentation.setEnabled(true);
    }

    /** Clears all fields in the information panel. */
    private void clearDetails() {
        fieldName.setText("");
        fieldInputType.setText("");
        fieldOutputType.setText("");
        parametersModel.setRowCount(0);
        btnDocumentation.setEnabled(false);
    }

    /** Opens the documentation URL for the currently displayed algorithm. */
    private void openDocumentation() {
        String name = fieldName.getText();
        if (name.isEmpty()) return;
        DescriptionOfAlgorithm desc = algorithmCache.get(name);
        if (desc == null) return;
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(desc.getURLOfDocumentation()));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Confirms the current tree selection, setting selectedAlgorithm and closing the dialog for leaf nodes.
     */
    private void confirmSelection() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!node.isLeaf()) {
            if (tree.isExpanded(path)) tree.collapsePath(path);
            else                       tree.expandPath(path);
            return;
        }
        selectedAlgorithm = node.getUserObject().toString();
        dispose();
    }

    /**
     * Creates a non-editable text field used in the information panel.
     *
     * @return the configured JTextField.
     */
    private JTextField createReadOnlyField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        return f;
    }

    /**
     * Adds a label and text-field row to the information panel using GridBagLayout.
     *
     * @param panel the target panel.
     * @param gc    the shared constraints object mutated in place.
     * @param row   the grid row index.
     * @param label the label text.
     * @param field the text field for the value column.
     */
    private void addInfoRow(JPanel panel, java.awt.GridBagConstraints gc,
                            int row, String label, JTextField field) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_LABEL);
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
        gc.weightx = 0; gc.weighty = 0;
        gc.fill = java.awt.GridBagConstraints.NONE;
        gc.insets = new java.awt.Insets(3, 4, 3, 4);
        panel.add(lbl, gc);

        gc.gridx = 1; gc.weightx = 1;
        gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        panel.add(field, gc);
    }
}