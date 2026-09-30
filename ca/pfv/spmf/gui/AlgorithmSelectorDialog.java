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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
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
 * are shown in black and non-suggested ones are shown in light gray. Categories
 * that contain only non-suggested algorithms are also shown in light gray.</p>
 *
 * <p>Algorithms whose description reports a {@code null} input file type are
 * never treated as suggested, even if their name is present in the set of
 * suggested algorithms supplied by the caller.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmSelectorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final Font  FONT_CATEGORY  = new Font(Font.DIALOG, Font.BOLD,  12);
    private static final Font  FONT_ALGORITHM = new Font(Font.DIALOG, Font.PLAIN, 11);
    private static final Font  FONT_LABEL     = new Font(Font.DIALOG, Font.BOLD,  11);

    /** Color for suggested (compatible) algorithms and categories that contain at least one. */
    private static final Color COLOR_SUGGESTED     = Color.BLACK;

    /** Color for non-suggested (incompatible) algorithms and categories that contain none. */
    private static final Color COLOR_NOT_SUGGESTED = Color.LIGHT_GRAY;

    /** Label used for the synthetic shortcut category that groups suggested algorithms. */
    private static final String SUGGESTED_CATEGORY_LABEL = "\u2713 Suggested Algorithms";

    /**
     * Name of the algorithm selected in the most recently closed dialog instance,
     * so it can be pre-selected the next time the dialog is opened.
     */
    private static String lastSelectedAlgorithmName = null;

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

    /** Map: Category → sorted algorithm names. */
    private final Map<String, List<String>> categoryMap;

    /** The set of all algorithm names currently visible under the active category filters. */
    private final Set<String> allAlgorithmNames;

    /** The algorithm name chosen by the user, or null if cancelled. */
    private String selectedAlgorithm = null;

    /** Cache of algorithm descriptions for quick lookup. */
    private final Map<String, DescriptionOfAlgorithm> algorithmCache;

    /**
     * Optional set of suggested algorithm names.
     * If null, all algorithms are treated as equally suggested (no highlighting).
     * If non-null, algorithms in the set are highlighted in black and others in gray,
     * and categories containing only non-suggested algorithms are also shown in gray.
     * Algorithms whose description returns a {@code null} input file type are never
     * included in this set, even if the caller passed them in.
     */
    private final Set<String> suggestedAlgorithms;

    /**
     * Marker object used as the user object of a placeholder leaf node shown
     * when a search filter matches no algorithm. Kept distinct from plain
     * {@code String} user objects so it can be recognised and excluded from
     * selection handling.
     */
    private static final class NoResultsPlaceholder {
        private final String message;

        NoResultsPlaceholder(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

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

    /**
     * Creates the algorithm selector dialog with optional suggested-algorithm highlighting.
     *
     * <p>When {@code requestedSuggestedAlgorithms} is non-null, algorithms whose names
     * are in the set are displayed in black (suggested/compatible) and all
     * others are displayed in light gray (not suggested). Categories that
     * contain only non-suggested algorithms are also displayed in light gray.
     * Algorithms whose description reports a {@code null} input file type are
     * silently removed from the suggested set.</p>
     *
     * @param owner                        the parent frame.
     * @param showViewAndTransform         include view-and-transform algorithms.
     * @param showGenerateData             include data-generation algorithms.
     * @param showTools                    include tool algorithms.
     * @param showAlgorithms               include data-mining algorithms.
     * @param showExperimentTools          include experiment-tool algorithms.
     * @param requestedSuggestedAlgorithms set of suggested algorithm names, or null to disable highlighting.
     * @throws Exception if AlgorithmManager cannot be initialised.
     */
    public AlgorithmSelectorDialog(Frame owner,
                                   boolean showViewAndTransform,
                                   boolean showGenerateData,
                                   boolean showTools,
                                   boolean showAlgorithms,
                                   boolean showExperimentTools,
                                   Set<String> requestedSuggestedAlgorithms) throws Exception {
        super(owner, "Select an Algorithm", true);

        algorithmCache = new java.util.HashMap<>();
        categoryMap    = buildCategoryMap(showViewAndTransform, showGenerateData,
                                          showTools, showAlgorithms, showExperimentTools);

        // Must be done AFTER buildCategoryMap() so that algorithmCache is populated.
        // Algorithms with a null input type are dropped from the suggested set.
        this.suggestedAlgorithms = filterSuggestedAlgorithms(requestedSuggestedAlgorithms);

        Set<String> namesSet = new HashSet<>();
        for (List<String> algs : categoryMap.values()) {
            namesSet.addAll(algs);
        }
        allAlgorithmNames = namesSet;

        JLabel lblSearch = new JLabel("Search: ");
        lblSearch.setFont(FONT_LABEL);
        searchField = new JTextField();
        searchField.setToolTipText("Type to filter algorithms by name");

        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        searchBar.add(lblSearch,   BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);

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

                    Object userObject = (value instanceof DefaultMutableTreeNode)
                            ? ((DefaultMutableTreeNode) value).getUserObject()
                            : value;

                    if (userObject instanceof NoResultsPlaceholder) {
                        setFont(FONT_ALGORITHM.deriveFont(Font.ITALIC));
                        if (!sel) {
                            setForeground(COLOR_NOT_SUGGESTED);
                        }
                        return this;
                    }

                    String algoName = value.toString();
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
                    if (!sel && suggestedAlgorithms != null && value instanceof DefaultMutableTreeNode) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                        if (categoryContainsOnlyNonSuggested(node)) {
                            setForeground(COLOR_NOT_SUGGESTED);
                        } else {
                            setForeground(COLOR_SUGGESTED);
                        }
                    }
                }
                return this;
            }
        });

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

        if (suggestedAlgorithms != null) {
            JLabel legend = new JLabel(
                    "<html><font color='#000000'>&#9632;</font> Compatible &nbsp;"
                    + "<font color='#C0C0C0'>&#9632;</font> Not compatible</html>");
            legend.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
            legend.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            leftPanel.add(legend, BorderLayout.SOUTH);
        }

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

        JPanel centrePanel = new JPanel(new BorderLayout(0, 0));
        centrePanel.add(leftPanel,  BorderLayout.WEST);
        centrePanel.add(infoPanel,  BorderLayout.CENTER);

        btnOK     = new JButton("OK");
        btnCancel = new JButton("Cancel");
        btnOK.addActionListener(e -> confirmSelection());
        btnCancel.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnPanel.add(btnOK);
        btnPanel.add(btnCancel);

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

        setupKeyBindings();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });

        pack();
        setMinimumSize(new Dimension(780, 560));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Returns the algorithm name selected by the user, or null if cancelled.
     *
     * @return selected algorithm name, or null.
     */
    public String getSelectedAlgorithm() {
        return selectedAlgorithm;
    }

    /**
     * Registers keyboard shortcuts so that pressing Enter confirms the current
     * selection and pressing Escape cancels the dialog, regardless of which
     * widget currently has keyboard focus.
     */
    private void setupKeyBindings() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "spmf.confirmSelection");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "spmf.cancelDialog");

        am.put("spmf.confirmSelection", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                confirmSelection();
            }
        });
        am.put("spmf.cancelDialog", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });

        getRootPane().setDefaultButton(btnOK);
    }

    /**
     * Returns {@code true} if the given category node contains at least one algorithm
     * and none of its algorithm children are present in {@link #suggestedAlgorithms}.
     * Used to decide whether an entire category should be rendered in gray. When
     * {@link #suggestedAlgorithms} is null this always returns {@code false}, since
     * highlighting is disabled in that mode.
     *
     * @param categoryNode the category tree node to inspect.
     * @return true if the category should be shown in the non-suggested color.
     */
    private boolean categoryContainsOnlyNonSuggested(DefaultMutableTreeNode categoryNode) {
        if (suggestedAlgorithms == null) {
            return false;
        }
        boolean hasAlgorithmChild = false;
        for (int i = 0; i < categoryNode.getChildCount(); i++) {
            Object child = categoryNode.getChildAt(i);
            if (!(child instanceof DefaultMutableTreeNode)) {
                continue;
            }
            DefaultMutableTreeNode leaf = (DefaultMutableTreeNode) child;
            Object userObj = leaf.getUserObject();
            if (userObj instanceof NoResultsPlaceholder) {
                continue;
            }
            hasAlgorithmChild = true;
            if (suggestedAlgorithms.contains(userObj.toString())) {
                return false;
            }
        }
        return hasAlgorithmChild;
    }

    /**
     * Returns a copy of the given set of suggested algorithm names from which every
     * algorithm whose description reports a {@code null} input file type has been removed.
     * Such algorithms cannot be meaningfully recommended for a given input file, so they
     * are never highlighted or listed in the "Suggested Algorithms" category.
     *
     * <p>Algorithms whose description cannot be obtained are also excluded, since their
     * input type is unknown.</p>
     *
     * @param requested the caller-supplied set of suggested algorithm names, or null.
     * @return a filtered set, or null if {@code requested} was null (highlighting disabled).
     */
    private Set<String> filterSuggestedAlgorithms(Set<String> requested) {
        if (requested == null) {
            return null;
        }
        Set<String> filtered = new HashSet<>();
        for (String name : requested) {
            if (name == null) {
                continue;
            }
            DescriptionOfAlgorithm desc = algorithmCache.get(name);
            if (desc == null) {
                try {
                    desc = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(name);
                    if (desc != null) {
                        algorithmCache.put(name, desc);
                    }
                } catch (Exception ignored) { }
            }
            // Only keep algorithms that declare an input type
            if (desc != null && desc.getInputFileTypes() != null) {
                filtered.add(name);
            }
        }
        return filtered;
    }

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
     * When suggested algorithms are configured, a synthetic shortcut category listing them is added
     * at the top, and within every regular category suggested algorithms are sorted before the rest.
     * If filtering is active and no algorithm matches, a placeholder message node is shown instead.
     *
     * @param filter lower-cased filter string, may be empty.
     */
    private void rebuildTree(String filter) {
        rootNode.removeAllChildren();
        boolean filtering = !filter.isEmpty();

        if (suggestedAlgorithms != null && !suggestedAlgorithms.isEmpty()) {
            List<String> compatible = new ArrayList<>();
            for (String alg : suggestedAlgorithms) {
                if (allAlgorithmNames.contains(alg)
                        && (!filtering || alg.toLowerCase().contains(filter))) {
                    compatible.add(alg);
                }
            }
            Collections.sort(compatible);

            if (!compatible.isEmpty()) {
                DefaultMutableTreeNode compatNode = new DefaultMutableTreeNode(
                        SUGGESTED_CATEGORY_LABEL + " (" + compatible.size() + ")");
                for (String alg : compatible) {
                    compatNode.add(new DefaultMutableTreeNode(alg));
                }
                rootNode.add(compatNode);
            }
        }

        for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
            String       category = entry.getKey();
            List<String> algs     = entry.getValue();

            List<String> suggestedMatches = new ArrayList<>();
            List<String> otherMatches     = new ArrayList<>();

            for (String alg : algs) {
                if (!filtering || alg.toLowerCase().contains(filter)) {
                    if (suggestedAlgorithms != null && suggestedAlgorithms.contains(alg)) {
                        suggestedMatches.add(alg);
                    } else {
                        otherMatches.add(alg);
                    }
                }
            }

            int total = suggestedMatches.size() + otherMatches.size();
            if (total > 0) {
                DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(category + " (" + total + ")");
                for (String alg : suggestedMatches) {
                    catNode.add(new DefaultMutableTreeNode(alg));
                }
                for (String alg : otherMatches) {
                    catNode.add(new DefaultMutableTreeNode(alg));
                }
                rootNode.add(catNode);
            }
        }

        if (rootNode.getChildCount() == 0 && filtering) {
            DefaultMutableTreeNode noResultsNode = new DefaultMutableTreeNode(
                    new NoResultsPlaceholder("No algorithms match \"" + filter + "\""));
            rootNode.add(noResultsNode);
        }

        treeModel.reload();

        if (filtering) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        } else {
            if (suggestedAlgorithms != null && !suggestedAlgorithms.isEmpty()
                    && rootNode.getChildCount() > 0
                    && rootNode.getChildAt(0) instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode first = (DefaultMutableTreeNode) rootNode.getChildAt(0);
                tree.expandPath(new TreePath(first.getPath()));
            }
            selectAndReveal(lastSelectedAlgorithmName);
        }
    }

    /**
     * Searches the tree model for a leaf node whose user object equals the given algorithm name,
     * expands its parent path and selects it, so the user immediately sees their previous choice.
     *
     * @param algorithmName the algorithm name to look for, or null to do nothing.
     */
    private void selectAndReveal(String algorithmName) {
        if (algorithmName == null) {
            return;
        }
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            Object childObj = rootNode.getChildAt(i);
            if (!(childObj instanceof DefaultMutableTreeNode)) continue;
            DefaultMutableTreeNode catNode = (DefaultMutableTreeNode) childObj;

            for (int j = 0; j < catNode.getChildCount(); j++) {
                Object leafObj = catNode.getChildAt(j);
                if (!(leafObj instanceof DefaultMutableTreeNode)) continue;
                DefaultMutableTreeNode leafNode = (DefaultMutableTreeNode) leafObj;

                if (algorithmName.equals(leafNode.getUserObject())) {
                    TreePath path = new TreePath(leafNode.getPath());
                    tree.expandPath(path.getParentPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    return;
                }
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

        if (node.getUserObject() instanceof NoResultsPlaceholder) {
            clearDetails();
            return;
        }

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

        if (node.getUserObject() instanceof NoResultsPlaceholder) {
            return;
        }

        if (!node.isLeaf()) {
            if (tree.isExpanded(path)) tree.collapsePath(path);
            else                       tree.expandPath(path);
            return;
        }
        selectedAlgorithm = node.getUserObject().toString();
        lastSelectedAlgorithmName = selectedAlgorithm;
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