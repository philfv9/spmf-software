package ca.pfv.spmf.gui.viewers.graphviewer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.*;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.*;

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
 * SPMF. If not, see http://www.gnu.org/licenses/.
 *
 * Do not remove license and copyright information.
 */

/**
 * Tool to visualize subgraphs, implemented as a JFrame using Swing.
 *
 * @author Philippe Fournier-Viger
 */
public class GraphViewer extends JFrame {

    /** Serial UID */
    private static final long serialVersionUID = -9054590513003092459L;

    // ==================== UI Constants ====================
    /** Height of the bottom toolbar panel */
    private static final int TOOLBAR_HEIGHT        = 150;
    /** Minimum window width */
    private static final int MIN_WINDOW_WIDTH      = 800;
    /** Minimum window height */
    private static final int MIN_WINDOW_HEIGHT     = 300;
    /** Default window width */
    private static final int DEFAULT_WINDOW_WIDTH  = 900;
    /** Default window height */
    private static final int DEFAULT_WINDOW_HEIGHT = 650;

    /** Background color of the toolbar */
    private static final Color TOOLBAR_BG     = new Color(245, 245, 245);
    /** Border color of the toolbar */
    private static final Color TOOLBAR_BORDER = new Color(210, 210, 210);
    /** Background color of the status bar */
    private static final Color STATUS_BG      = new Color(250, 250, 250);

    /** Font used for labels in the toolbar */
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD,  11);
    /** Font used for text fields in the toolbar */
    private static final Font FIELD_FONT = new Font("SansSerif", Font.PLAIN, 11);

    // ==================== Appearance Dialog Constants ====================

    /** Minimum node radius allowed via the slider */
    private static final int NODE_RADIUS_MIN  = 5;
    /** Maximum node radius allowed via the slider */
    private static final int NODE_RADIUS_MAX  = 80;
    /** Minimum node text size allowed via the slider */
    private static final int NODE_TEXT_MIN    = 6;
    /** Maximum node text size allowed via the slider */
    private static final int NODE_TEXT_MAX    = 48;
    /** Minimum edge thickness allowed via the slider */
    private static final int EDGE_WIDTH_MIN   = 1;
    /** Maximum edge thickness allowed via the slider */
    private static final int EDGE_WIDTH_MAX   = 20;
    /** Minimum edge text size allowed via the slider */
    private static final int EDGE_TEXT_MIN    = 6;
    /** Maximum edge text size allowed via the slider */
    private static final int EDGE_TEXT_MAX    = 48;

    // ==================== Canvas Resize Dialog Constants ====================

    /** Minimum canvas dimension (width or height) accepted by the resize dialog */
    private static final int CANVAS_DIM_MIN  = 100;
    /** Maximum canvas dimension (width or height) offered by the resize dialog sliders */
    private static final int CANVAS_DIM_MAX  = 4000;
    /** Spinner step size used in the canvas resize dialog */
    private static final int CANVAS_DIM_STEP = 50;

    // ==================== Core Components ====================

    /** The panel to display subgraphs */
    GraphViewerPanel viewerPanel;

    /**
     * The scroll pane that wraps the viewer panel.
     * Kept as a field so that {@link #refreshViewerPanel()} can reach its
     * viewport and force a complete repaint after layout or resize operations.
     */
    private JScrollPane viewerScrollPane;

    /** Flag indicating if the application is run as a standalone program or not */
    boolean runAsStandalone = true;

    // ==================== Toolbar Widgets ====================

    /** A label to display the edge count */
    private JLabel labelEdgeCount;

    /** A label to display the node count */
    private JLabel labelNodeCount;

    /** A field for the node count */
    private JTextField fieldNodeCount;

    /** A field for the edge count */
    private JTextField fieldEdgeCount;

    /** A field to display name */
    private JTextField fieldName;

    /** label to show the number of graph */
    private JLabel labelNumberOf;

    /** The current graph that is displayed if a graph database is loaded */
    int currentGraphIndex = 0;

    /** Button to change to the previous graph */
    private JButton buttonPrevious;

    /** Button to change to the next graph */
    private JButton buttonNext;

    /** Label to display the support of the current subgraph */
    private JLabel labelSupport;

    /** Field for the support */
    private JTextField fieldSupport;

    /** Boolean indicating if the current graphs have support values or not */
    private boolean hasSupportValues;

    /** A JLabel to display the current graph name */
    private JLabel labelGraphName;

    /** Status label shown at the top of the window */
    private JLabel statusLabel;

    /** The navigation panel group */
    private JPanel navPanel;

    // ==================== Graph State ====================

    /** Keep string representation of input file in memory (true or false) */
    private boolean SHOW_STRING_REPRESENTATION_OF_FILE = true;

    /** The current graph database */
    private List<GGraph> graphDatabase;

    /** String representations of graphs in the current graph database */
    private List<String> graphStringRepresentations;

    /** Scroll pane containing the string representation text pane */
    private JScrollPane scrollPaneStrings;

    /** Text pane that shows the string representation of the current graph */
    private JTextPane textPaneStrings;

    /** Split pane used when the string representation panel is shown */
    private JSplitPane splitPane;

    // ==================== Canvas Defaults ====================

    /** Minimum canvas width */
    int minimumCanvasWidth  = 400;

    /** Minimum canvas height */
    int minimumCanvasHeight = 350;

    // ==================== Constructor ====================

    /**
     * Constructor
     *
     * @param runAsStandalone                  true if run as standalone program, otherwise false.
     * @param displayGraphStringRepresentation true to show the string representation panel
     */
    public GraphViewer(boolean runAsStandalone,
                       boolean displayGraphStringRepresentation) {

        this.SHOW_STRING_REPRESENTATION_OF_FILE = displayGraphStringRepresentation;
        this.runAsStandalone = runAsStandalone;

        // Set the icon
        setIconImage(Toolkit.getDefaultToolkit()
                .getImage(MainWindow.class
                        .getResource("/ca/pfv/spmf/gui/spmf.png")));

        // Set the window size
        setSize(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

        // Set the window in the center of the screen
        this.setLocationRelativeTo(null);

        // Set the title
        this.setTitle("SPMF Graph Viewer");

        // Set the minimum size
        this.setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT));

        // If running as standalone, exit when the window is closed
        if (runAsStandalone) {
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }

        // Build the UI components
        buildUI();

        // Create the menu bar
        createMenuBar();

        // Set the window visible
        setVisible(true);

        // Set the size of the panel (!! Important otherwise, no scrollbars!!)
        viewerPanel.setPreferredSize(
                new Dimension(viewerPanel.getWidth(), viewerPanel.getHeight()));
    }

    // ==================== UI Construction ====================

    /**
     * Build the entire content area of the frame.
     */
    private void buildUI() {
        getContentPane().setLayout(new BorderLayout(0, 0));

        // Add the status bar at the top
        getContentPane().add(buildStatusBar(), BorderLayout.NORTH);

        // Add the toolbar at the bottom
        getContentPane().add(buildToolbar(), BorderLayout.SOUTH);

        // Add the center graph / string panel
        buildCenterPanel();
    }

    /**
     * Build the bottom toolbar panel containing graph info, navigation,
     * and appearance controls.
     *
     * @return the constructed toolbar JPanel
     */
    private JPanel buildToolbar() {

        // Create the tools panel with a null layout for precise positioning
        JPanel toolsPanel = new JPanel(null);
        toolsPanel.setBackground(TOOLBAR_BG);
        toolsPanel.setBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, TOOLBAR_BORDER));
        toolsPanel.setPreferredSize(new Dimension(MIN_WINDOW_WIDTH, TOOLBAR_HEIGHT));
        toolsPanel.setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, TOOLBAR_HEIGHT));

        // ---- Graph Info group (leftmost) ----
        JPanel infoPanel = createTitledGroup("Graph Info", 6, 6, 250, TOOLBAR_HEIGHT - 14);
        toolsPanel.add(infoPanel);

        // Graph ID label and field
        labelGraphName = makeLabel("Graph ID:", 8, 22, 68);
        infoPanel.add(labelGraphName);

        fieldName = makeField(78, 20, 80, 20);
        infoPanel.add(fieldName);

        // Node count label and field
        labelNodeCount = makeLabel("Nodes:", 8, 48, 68);
        infoPanel.add(labelNodeCount);

        fieldNodeCount = makeField(78, 46, 80, 20);
        infoPanel.add(fieldNodeCount);

        // Edge count label and field
        labelEdgeCount = makeLabel("Edges:", 8, 74, 68);
        infoPanel.add(labelEdgeCount);

        fieldEdgeCount = makeField(78, 72, 80, 20);
        infoPanel.add(fieldEdgeCount);

        // Support label and field (fourth row, under Edges)
        labelSupport = makeLabel("Support:", 8, 100, 68);
        infoPanel.add(labelSupport);

        fieldSupport = makeField(78, 98, 80, 20);
        infoPanel.add(fieldSupport);

        // ---- Navigation group (second from left) ----
        navPanel = createTitledGroup("Navigation", 262, 6, 140, TOOLBAR_HEIGHT - 14);
        toolsPanel.add(navPanel);

        // Previous graph button
        buttonPrevious = new JButton("<");
        buttonPrevious.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                displayPreviousGraph();
            }
        });
        buttonPrevious.setEnabled(false);
        buttonPrevious.setToolTipText("Show previous graph");
        buttonPrevious.setBounds(8, 22, 50, 28);
        navPanel.add(buttonPrevious);

        // Next graph button
        buttonNext = new JButton(">");
        buttonNext.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                displayNextGraph();
            }
        });
        buttonNext.setEnabled(false);
        buttonNext.setToolTipText("Show next graph");
        buttonNext.setBounds(72, 22, 50, 28);
        navPanel.add(buttonNext);

        // Label showing "Graph X of Y"
        labelNumberOf = new JLabel("Graph 1 of 1", SwingConstants.CENTER);
        labelNumberOf.setFont(FIELD_FONT);
        labelNumberOf.setBounds(4, 58, 128, 16);
        navPanel.add(labelNumberOf);

//        // ---- Appearance group (merged Canvas + Appearance, rightmost) ----
//        JPanel appearancePanel = createTitledGroup("Appearance", 408, 6, 370, TOOLBAR_HEIGHT - 14);
//        toolsPanel.add(appearancePanel);
//
//        // "Canvas Appearance" button – opens the consolidated canvas appearance dialog
//        JButton btnCanvasAppearance = new JButton("Canvas Appearance\u2026");
//        btnCanvasAppearance.setFont(FIELD_FONT);
//        btnCanvasAppearance.setToolTipText("Change canvas size and color");
//        btnCanvasAppearance.setBounds(6, 20, 170, 26);
//        btnCanvasAppearance.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                showCanvasAppearanceDialog();
//            }
//        });
//        appearancePanel.add(btnCanvasAppearance);
//
//        // "Auto Layout" button
//        JButton btnAutoLayout = new JButton("Auto Layout");
//        btnAutoLayout.setFont(FIELD_FONT);
//        btnAutoLayout.setToolTipText("Auto-set canvas size and run layout algorithm");
//        btnAutoLayout.setBounds(182, 20, 170, 26);
//        btnAutoLayout.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                actionAutoSetCanvasSize();
//            }
//        });
//        appearancePanel.add(btnAutoLayout);
//
//        // "Node Appearance" button
//        JButton btnNodeAppearance = new JButton("Node Appearance\u2026");
//        btnNodeAppearance.setFont(FIELD_FONT);
//        btnNodeAppearance.setToolTipText(
//                "Change node radius, text size and colors");
//        btnNodeAppearance.setBounds(6, 54, 170, 26);
//        btnNodeAppearance.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                showNodeAppearanceDialog();
//            }
//        });
//        appearancePanel.add(btnNodeAppearance);

//        // "Edge Appearance" button
//        JButton btnEdgeAppearance = new JButton("Edge Appearance\u2026");
//        btnEdgeAppearance.setFont(FIELD_FONT);
//        btnEdgeAppearance.setToolTipText(
//                "Change edge width, text size and colors");
//        btnEdgeAppearance.setBounds(182, 54, 170, 26);
//        btnEdgeAppearance.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                showEdgeAppearanceDialog();
//            }
//        });
//        appearancePanel.add(btnEdgeAppearance);

        return toolsPanel;
    }

    /**
     * Build the top status bar that shows live feedback messages
     * and a keyboard-shortcut hint.
     *
     * @return the constructed status bar JPanel
     */
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBackground(STATUS_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, TOOLBAR_BORDER),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));

        // Status message on the left
        statusLabel = new JLabel("Ready  \u2014  No file loaded");
        statusLabel.setFont(FIELD_FONT);
        statusLabel.setForeground(new Color(80, 80, 80));
        bar.add(statusLabel, BorderLayout.CENTER);

        // Keyboard hint on the right
        JLabel hint = new JLabel(
                "Drag nodes  |  Ctrl+click to multi-select  |  Drag empty area to box-select");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 10));
        hint.setForeground(new Color(140, 140, 140));
        bar.add(hint, BorderLayout.EAST);

        return bar;
    }

    /**
     * Build the center panel that contains the graph viewer and,
     * optionally, the string representation panel.
     */
    private void buildCenterPanel() {

        // Panel to view the subgraph with scrollbars (JScrollPane)
        int panelWidth  = minimumCanvasWidth;
        int panelHeight = minimumCanvasHeight;
        viewerPanel = new GraphViewerPanel(
                new GraphLayoutFruchtermanReingold(), panelWidth, panelHeight);
        viewerPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
        viewerPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

        // Store the scroll pane as a field so refreshViewerPanel() can reach it
        viewerScrollPane = new JScrollPane(viewerPanel);
        viewerScrollPane.setAutoscrolls(true);
        viewerScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        viewerScrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        if (SHOW_STRING_REPRESENTATION_OF_FILE) {
            // Build the string representation pane
            textPaneStrings = new JTextPane();
            textPaneStrings.setEditable(false);
            textPaneStrings.setEnabled(true);
            textPaneStrings.setFont(new Font("Monospaced", Font.PLAIN, 12));

            scrollPaneStrings = new JScrollPane(textPaneStrings);
            scrollPaneStrings.setAutoscrolls(true);
            scrollPaneStrings.setPreferredSize(new Dimension(220, 100));
            scrollPaneStrings.setBorder(
                    BorderFactory.createTitledBorder("Graph String"));

            // Use a JSplitPane so the user can resize both panes
            splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    viewerScrollPane, scrollPaneStrings);
            splitPane.setResizeWeight(0.78);
            splitPane.setOneTouchExpandable(true);
            splitPane.setContinuousLayout(true);
            getContentPane().add(splitPane, BorderLayout.CENTER);
        } else {
            getContentPane().add(viewerScrollPane, BorderLayout.CENTER);
        }
    }

    // ==================== Panel Refresh Helper ====================

    /**
     * Force a complete, artifact-free repaint of the graph viewer panel and its
     * surrounding scroll-pane viewport.
     *
     * <p>This method must be called after <em>every</em> operation that changes
     * the logical size of the canvas or repositions nodes (auto-layout, manual
     * resize, graph navigation, etc.).  Without it, Swing may leave ghost images
     * of the previous graph or the old canvas boundary on screen because:
     * <ol>
     *   <li>The preferred size of {@link #viewerPanel} is stale, so the scroll
     *       pane's viewport does not know the drawable area has changed.</li>
     *   <li>The viewport's backing buffer is not cleared before the new paint
     *       pass, causing old pixels to bleed through.</li>
     * </ol>
     * The fix performs three steps in the correct order:
     * <ol>
     *   <li>Synchronise the panel's preferred size with its current logical
     *       canvas dimensions ({@code viewerPanel.width} / {@code .height}).</li>
     *   <li>Call {@code revalidate()} so the layout manager recalculates
     *       scrollbar extents and viewport bounds.</li>
     *   <li>Call {@code repaint()} on both the panel and the viewport so every
     *       pixel in the visible area is redrawn from scratch.</li>
     * </ol>
     * </p>
     */
    private void refreshViewerPanel() {
        // Step 1 – sync preferred size to the current logical canvas size so
        //          the scroll pane shows correct scrollbars.
        viewerPanel.setPreferredSize(
                new Dimension(viewerPanel.width, viewerPanel.height));

        // Step 2 – tell the layout system that the preferred size has changed.
        viewerPanel.revalidate();

        // Step 3 – repaint the panel itself (clears ghost node/edge artifacts).
        viewerPanel.repaint();

        // Step 4 – also repaint the viewport so the area outside the panel
        //          (the grey border region) is cleared when the canvas shrank.
        if (viewerScrollPane != null) {
            viewerScrollPane.getViewport().revalidate();
            viewerScrollPane.getViewport().repaint();
            viewerScrollPane.repaint();
        }
    }

    // ==================== Menu Bar ====================

    /**
     * Create the menu bar and attach it to the frame.
     */
    private void createMenuBar() {
        JMenuBar mb = new JMenuBar();

        mb.add(buildFileMenu());
        mb.add(buildCanvasMenu());
        mb.add(buildNodesMenu());
        mb.add(buildEdgesMenu());
        mb.add(buildLayoutMenu());

        this.setJMenuBar(mb);
    }

    /**
     * Build the File menu.
     *
     * @return the constructed JMenu
     */
    private JMenu buildFileMenu() {
        // ================ File Menu ======================
        JMenu menuFile = new JMenu("File");
        menuFile.setMnemonic(KeyEvent.VK_F);

        JMenuItem menuFileSave = new JMenuItem("Export as PNG");
        menuFileSave.setIcon(new ImageIcon(SPMFTextEditor.class
                .getResource("/ca/pfv/spmf/gui/icons/save.gif")));
        menuFileSave.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
        menuFileSave.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                viewerPanel.exportAsPNG();
            }
        });
        menuFile.add(menuFileSave);

        menuFile.addSeparator();

        String quitName = runAsStandalone ? "Quit" : "Close";
        JMenuItem menuFileQuit = new JMenuItem(quitName);
        menuFileQuit.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        menuFileQuit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actionQuit();
            }
        });
        menuFile.add(menuFileQuit);

        return menuFile;
    }

    /**
     * Build the Nodes menu.
     * <p>
     * The menu contains a single "Change node appearance…" entry that opens
     * the consolidated {@link NodeAppearanceDialog}, as well as the original
     * show/hide toggle items.
     * </p>
     *
     * @return the constructed JMenu
     */
    private JMenu buildNodesMenu() {
        // ================ Nodes Menu ======================
        JMenu menuNodes = new JMenu("Nodes");
        menuNodes.setMnemonic(KeyEvent.VK_N);

        JMenuItem menuNodeAppearance = new JMenuItem("Change node appearance\u2026");
        menuNodeAppearance.setToolTipText(
                "Adjust node radius, text size, background, border and text color");
        menuNodeAppearance.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showNodeAppearanceDialog();
            }
        });
        menuNodes.add(menuNodeAppearance);

        menuNodes.addSeparator();

        JCheckBoxMenuItem menuShowNodeIDs = new JCheckBoxMenuItem("Show node IDs");
        menuShowNodeIDs.setSelected(true);
        menuShowNodeIDs.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showNodeIDs(menuShowNodeIDs.isSelected());
            }
        });
        menuNodes.add(menuShowNodeIDs);

        JCheckBoxMenuItem menuShowNodeLabels = new JCheckBoxMenuItem("Show node labels");
        menuShowNodeLabels.setSelected(true);
        menuShowNodeLabels.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showNodeLabels(menuShowNodeLabels.isSelected());
            }
        });
        menuNodes.add(menuShowNodeLabels);

        return menuNodes;
    }

    /**
     * Build the Edges menu.
     * <p>
     * The menu contains a single "Change edge appearance…" entry that opens
     * the consolidated {@link EdgeAppearanceDialog}, as well as the original
     * show/hide toggle items.
     * </p>
     *
     * @return the constructed JMenu
     */
    private JMenu buildEdgesMenu() {
        // ================ Edges Menu ======================
        JMenu menuEdges = new JMenu("Edges");
        menuEdges.setMnemonic(KeyEvent.VK_E);

        JMenuItem menuEdgeAppearance = new JMenuItem("Change edge appearance\u2026");
        menuEdgeAppearance.setToolTipText(
                "Adjust edge width, text size, edge color and text color");
        menuEdgeAppearance.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showEdgeAppearanceDialog();
            }
        });
        menuEdges.add(menuEdgeAppearance);

        menuEdges.addSeparator();

        JCheckBoxMenuItem menuShowEdgeIDs = new JCheckBoxMenuItem("Show edge IDs");
        menuShowEdgeIDs.setSelected(true);
        menuShowEdgeIDs.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showEdgeIDs(menuShowEdgeIDs.isSelected());
            }
        });
        menuEdges.add(menuShowEdgeIDs);

        JCheckBoxMenuItem menuShowEdgeLabels = new JCheckBoxMenuItem("Show edge labels");
        menuShowEdgeLabels.setSelected(true);
        menuShowEdgeLabels.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showEdgeLabels(menuShowEdgeLabels.isSelected());
            }
        });
        menuEdges.add(menuShowEdgeLabels);

        return menuEdges;
    }

    /**
     * Build the Canvas menu.
     *
     * @return the constructed JMenu
     */
    private JMenu buildCanvasMenu() {
        // ================ Canvas Menu ======================
        JMenu menuCanvas = new JMenu("Canvas");
        menuCanvas.setMnemonic(KeyEvent.VK_C);

        JMenuItem menuCanvasAppearance = new JMenuItem("Canvas appearance\u2026");
        menuCanvasAppearance.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showCanvasAppearanceDialog();
            }
        });
        menuCanvas.add(menuCanvasAppearance);

//        menuCanvas.addSeparator();
//
//        JMenuItem menuCanvasSizeAuto = new JMenuItem(
//                "Do auto-layout");
//        menuCanvasSizeAuto.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//            	viewerPanel.autoLayout();
//            }
//        });
//        menuCanvas.add(menuCanvasSizeAuto);

        menuCanvas.addSeparator();

        JCheckBoxMenuItem menuAntiAliasing = new JCheckBoxMenuItem("Anti-aliasing");
        menuAntiAliasing.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setAntiAliasing(menuAntiAliasing.isSelected());
            }
        });
        menuCanvas.add(menuAntiAliasing);

        return menuCanvas;
    }

    /**
     * Build the Graph Layout menu.
     *
     * @return the constructed JMenu
     */
    private JMenu buildLayoutMenu() {
        // ================ Layout Menu ======================
        JMenu menuLayouts = new JMenu("Graph layout");
        menuLayouts.setMnemonic(KeyEvent.VK_L);

        ButtonGroup layoutGroup = new ButtonGroup();

        JMenuItem menuLayout1 = new JRadioButtonMenuItem("FruchtermanReingold91");
        menuLayout1.setSelected(true);
        menuLayout1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                changeLayout(0);
            }
        });
        menuLayouts.add(menuLayout1);

        JMenuItem menuLayout2 = new JRadioButtonMenuItem(
                "FruchtermanReingold91(grid)");
        menuLayout2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                changeLayout(1);
            }
        });
        menuLayouts.add(menuLayout2);

        JMenuItem menuLayout3 = new JRadioButtonMenuItem("Grid");
        menuLayout3.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                changeLayout(2);
            }
        });
        menuLayouts.add(menuLayout3);

        JMenuItem menuLayout4 = new JRadioButtonMenuItem("Circle");
        menuLayout4.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                changeLayout(3);
            }
        });
        menuLayouts.add(menuLayout4);

        JMenuItem menuLayout5 = new JRadioButtonMenuItem("Random");
        menuLayout5.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                changeLayout(4);
            }
        });
        menuLayouts.add(menuLayout5);

        JMenuItem menuLayout6 = new JRadioButtonMenuItem("Rectangle");
        menuLayout6.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                changeLayout(5);
            }
        });
        menuLayouts.add(menuLayout6);

        layoutGroup.add(menuLayout1);
        layoutGroup.add(menuLayout2);
        layoutGroup.add(menuLayout3);
        layoutGroup.add(menuLayout4);
        layoutGroup.add(menuLayout5);
        layoutGroup.add(menuLayout6);

        return menuLayouts;
    }

    // ==================== Helper Widget Factories ====================

    /**
     * Create a JPanel with a titled border, positioned with absolute coordinates.
     *
     * @param title the title for the border
     * @param x     left position in pixels
     * @param y     top position in pixels
     * @param w     width in pixels
     * @param h     height in pixels
     * @return the constructed JPanel
     */
    private JPanel createTitledGroup(String title, int x, int y, int w, int h) {
        JPanel p = new JPanel(null);
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(TOOLBAR_BORDER, 1),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10),
                new Color(90, 90, 90)));
        p.setBackground(TOOLBAR_BG);
        p.setBounds(x, y, w, h);
        return p;
    }

    /**
     * Create a right-aligned JLabel with the given text and bounds.
     *
     * @param text  label text
     * @param x     left position
     * @param y     top position
     * @param w     width
     * @return the constructed JLabel
     */
    private JLabel makeLabel(String text, int x, int y, int w) {
        JLabel l = new JLabel(text, SwingConstants.RIGHT);
        l.setFont(LABEL_FONT);
        l.setBounds(x, y, w, 20);
        return l;
    }

    /**
     * Create a non-editable JTextField with the given bounds.
     *
     * @param x left position
     * @param y top position
     * @param w width
     * @param h height
     * @return the constructed JTextField
     */
    private JTextField makeField(int x, int y, int w, int h) {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setFont(FIELD_FONT);
        f.setColumns(8);
        f.setBounds(x, y, w, h);
        return f;
    }

    // ==================== Appearance Dialogs ====================

    /**
     * Open the consolidated canvas appearance dialog.
     * <p>
     * The dialog lets the user adjust canvas size with spinners and pick the
     * canvas background color with a color-chooser button, all in a single
     * modal window. Color changes are applied live; pressing Cancel reverts
     * all changes including any color already picked.
     * </p>
     */
    private void showCanvasAppearanceDialog() {
        new CanvasAppearanceDialog(this).setVisible(true);
    }

    /**
     * Open the consolidated node appearance dialog.
     * <p>
     * The dialog lets the user adjust node radius and text size with sliders,
     * and pick node background, border and text colors with color-chooser buttons,
     * all in a single modal window.  Changes are applied live as the sliders move
     * and when color buttons are clicked; pressing Cancel reverts all changes.
     * </p>
     */
    private void showNodeAppearanceDialog() {
        new NodeAppearanceDialog(this).setVisible(true);
    }

    /**
     * Open the consolidated edge appearance dialog.
     * <p>
     * The dialog lets the user adjust edge width and text size with sliders,
     * and pick edge line and text colors with color-chooser buttons, all in a
     * single modal window.  Changes are applied live as the sliders move and
     * when color buttons are clicked; pressing Cancel reverts all changes.
     * </p>
     */
    private void showEdgeAppearanceDialog() {
        new EdgeAppearanceDialog(this).setVisible(true);
    }

    // ==================== Actions ====================

    /**
     * Auto-enlarge the canvas if necessary and apply auto-layout.
     * <p>
     * After the layout algorithm repositions all nodes, {@link #refreshViewerPanel()}
     * is called to ensure the scroll-pane viewport is fully repainted and no
     * ghost image of the previous graph state remains on screen.
     * </p>
     */
    protected void actionAutoSetCanvasSize() {
        autoEnlargeCanvasIfNecessary(true);
        viewerPanel.autoLayout();
        refreshViewerPanel();
        updateStatus("Canvas auto-sized and layout applied.");
    }

    /**
     * Ask the user to choose a new node text size and apply it.
     * <p>
     * Note: this method is kept for backward compatibility but is no longer
     * reachable from the menu.  Use {@link #showNodeAppearanceDialog()} instead.
     * </p>
     */
    protected void changeNodeTextSize() {
        String s = (String) JOptionPane.showInputDialog(
                this, "Choose node text size:", "Text size dialog",
                JOptionPane.QUESTION_MESSAGE, null, null,
                GraphViewerPanel.getNodeTextSize());

        if (s == null || s.length() == 0) {
            return;
        }

        int newTextSize = 0;
        try {
            newTextSize = Integer.parseInt(s);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Text size must be a positive integer number.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newTextSize < 1) {
            JOptionPane.showMessageDialog(this,
                    "Text size must be a positive integer number.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        viewerPanel.updateNodeTextSize(newTextSize);
        refreshViewerPanel();
        updateStatus("Node text size set to " + newTextSize + ".");
    }

    /**
     * Ask the user to choose a new edge text size and apply it.
     * <p>
     * Note: this method is kept for backward compatibility but is no longer
     * reachable from the menu.  Use {@link #showEdgeAppearanceDialog()} instead.
     * </p>
     */
    protected void changeEdgeTextSize() {
        String s = (String) JOptionPane.showInputDialog(
                this, "Choose edge text size:", "Text size dialog",
                JOptionPane.QUESTION_MESSAGE, null, null,
                GraphViewerPanel.getEdgeTextSize());

        if (s == null || s.length() == 0) {
            return;
        }

        int newTextSize = 0;
        try {
            newTextSize = Integer.parseInt(s);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Text size must be a positive integer number.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newTextSize < 1) {
            JOptionPane.showMessageDialog(this,
                    "Text size must be a positive integer number.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        viewerPanel.updateEdgeTextSize(newTextSize);
        refreshViewerPanel();
        updateStatus("Edge text size set to " + newTextSize + ".");
    }

    /**
     * Quit or close the window depending on the standalone flag.
     */
    protected void actionQuit() {
        if (runAsStandalone) {
            System.exit(0);
        } else {
            setVisible(false);
        }
    }

    /**
     * Ask the user to choose a new edge width and apply it.
     * <p>
     * Note: this method is kept for backward compatibility but is no longer
     * reachable from the menu.  Use {@link #showEdgeAppearanceDialog()} instead.
     * </p>
     */
    protected void resizeEdges() {
        String s = (String) JOptionPane.showInputDialog(
                this, "Choose edge width:", "Resize dialog",
                JOptionPane.QUESTION_MESSAGE, null, null,
                GEdge.getEdgeThickness());

        if (s == null || s.length() == 0) {
            return;
        }

        int newThickness = 0;
        try {
            newThickness = Integer.parseInt(s);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Width must be a positive integer number.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newThickness < 1) {
            JOptionPane.showMessageDialog(this,
                    "Width must be a positive integer number.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        viewerPanel.updateEdgeThickness(newThickness);
        refreshViewerPanel();
        updateStatus("Edge width set to " + newThickness + ".");
    }

    /**
     * Ask the user to choose a new node radius and apply it.
     * <p>
     * Note: this method is kept for backward compatibility but is no longer
     * reachable from the menu.  Use {@link #showNodeAppearanceDialog()} instead.
     * </p>
     */
    protected void resizeNodesRadius() {
        String s = (String) JOptionPane.showInputDialog(
                this, "Choose node radius:", "Resize dialog",
                JOptionPane.QUESTION_MESSAGE, null, null,
                GNode.getRadius());

        if (s == null || s.length() == 0) {
            return;
        }

        int newRadius = 0;
        try {
            newRadius = Integer.parseInt(s);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Width must be a positive integer number (>5).",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newRadius < 5) {
            JOptionPane.showMessageDialog(this,
                    "Width must be a positive integer number (>5).",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        viewerPanel.updateNodeSize(newRadius);
        refreshViewerPanel();
        updateStatus("Node radius set to " + newRadius + ".");
    }

    /**
     * Change the graph layout algorithm based on the selected index and
     * immediately run auto-layout with the new algorithm.
     * <p>
     * {@link #refreshViewerPanel()} is called after the layout so any ghost
     * pixels left by the previous node positions are erased.
     * </p>
     *
     * @param selectedIndex the index of the chosen layout (0-5)
     */
    private void changeLayout(int selectedIndex) {
        if (selectedIndex == 0) {
            viewerPanel.setGraphLayoutGenerator(new GraphLayoutFruchtermanReingold());
        } else if (selectedIndex == 1) {
            viewerPanel.setGraphLayoutGenerator(new GraphLayoutFruchtermanReingoldGrid());
        } else if (selectedIndex == 2) {
            viewerPanel.setGraphLayoutGenerator(new GraphLayoutGrid());
        } else if (selectedIndex == 3) {
            viewerPanel.setGraphLayoutGenerator(new GraphLayoutCircle());
        } else if (selectedIndex == 4) {
            viewerPanel.setGraphLayoutGenerator(new GraphLayoutRandom());
        } else if (selectedIndex == 5) {
            viewerPanel.setGraphLayoutGenerator(new GraphLayoutRectangle());
        }
        viewerPanel.autoLayout();
        refreshViewerPanel();
    }

    // ==================== Graph Loading ====================

    /**
     * Load a sample graph for debugging.
     */
    public void loadSampleGraph() {

        GNode[] nodes = new GNode[]{
            new GNode("Paul"),  new GNode("Jack"),
            new GNode("Katie"), new GNode("Paolo"),
            new GNode("Usman")
        };
        for (GNode node : nodes) {
            viewerPanel.addNode(node);
        }

        GEdge edge1 = new GEdge(nodes[0], nodes[1], "friend",   true);
        GEdge edge2 = new GEdge(nodes[1], nodes[2], "roommate", false);
        GEdge edge3 = new GEdge(nodes[0], nodes[3], "friend",   true);
        GEdge edge4 = new GEdge(nodes[2], nodes[3], "friend",   true);
        GEdge edge5 = new GEdge(nodes[4], nodes[3], "friend",   true);
        GEdge edge6 = new GEdge(nodes[4], nodes[0], "friend",   true);

        viewerPanel.addEdge(edge1);
        viewerPanel.addEdge(edge2);
        viewerPanel.addEdge(edge3);
        viewerPanel.addEdge(edge4);
        viewerPanel.addEdge(edge5);
        viewerPanel.addEdge(edge6);

        for (int i = 0; i < 10; i++) {
            GNode nodeA = new GNode("Node " + i);
            viewerPanel.addNode(nodeA);
            viewerPanel.addEdge(new GEdge(nodeA, nodes[i % 5], "", false));
        }

        // Check if the canvas is big enough and auto-enlarge it
        autoEnlargeCanvasIfNecessary(false);

        // Do the layout
        viewerPanel.autoLayout();

        // Update edge and vertex count
        fieldNodeCount.setText("" + viewerPanel.getNodeCount());
        fieldEdgeCount.setText("" + viewerPanel.getEdgeCount());

        // Hide all navigation and graph-identity widgets for a single graph
        navPanel.setVisible(false);
        labelSupport.setVisible(false);
        fieldSupport.setVisible(false);
        labelGraphName.setVisible(false);
        fieldName.setVisible(false);

        // Ensure the viewport is fully refreshed after loading
        refreshViewerPanel();
        updateStatus("Sample graph loaded.");
    }

    /**
     * Auto-enlarge the canvas if the size is too small for the number of nodes
     * in the graph.
     *
     * @param forceResize true to force resize regardless of current size
     * @return true if the canvas was enlarged
     */
    private boolean autoEnlargeCanvasIfNecessary(boolean forceResize) {
        // Calculate the required area in pixels to put all nodes.
        // This is the sum of the area of each node (square of the diameter),
        // multiplied by the number of nodes, multiplied by a multiplying factor.
        double multiplyingFactor = 4d;

        double requiredAreaForAllNodes =
                viewerPanel.getNodeCount()
                * GNode.getDiameter() * (double) GNode.getDiameter()
                * multiplyingFactor;

        // Obtain the current area in the canvas
        double currentArea = (double) viewerPanel.width * viewerPanel.height;

        // If the needed area is more than zero
        double neededArea = requiredAreaForAllNodes - currentArea;
        if (forceResize || neededArea >= 0) {
            // We will increase the width and height of the canvas by a value "x".
            // To find the appropriate value "x", we need to solve a second degree
            // polynomial: x * (width + height) + x^2 - neededArea = 0.
            // Using the quadratic formula x = (-b + sqrt(b^2 - 4ac)) / 2a
            // where a=1, b=width+height, c=-neededArea
            double b         = viewerPanel.width + viewerPanel.height;
            double c         = -neededArea;
            int    xSolution = (int) (-b + Math.sqrt(Math.pow(b, 2) - (4d * c))) / 2;

            int newWidth  = (int) (viewerPanel.width  + xSolution);
            int newHeight = (int) (viewerPanel.height + xSolution);
            viewerPanel.updateSize(newWidth, newHeight, false);
            return true;
        }
        return false;
    }

    /**
     * Read a graph database from an input file in gSpan format.
     *
     * @param path the input file path
     * @throws IOException if an error reading or writing to file
     */
    public void loadFileGSPANFormat(String path) throws IOException {
        hasSupportValues = false;

        File file = new File(path);
        BufferedReader br = new BufferedReader(new FileReader(file));
        graphDatabase = new ArrayList<>();

        String line = br.readLine();
        Boolean hasNextGraph = (line != null) && line.startsWith("t");

        StringBuffer linesOfCurrentGraph = null;
        if (SHOW_STRING_REPRESENTATION_OF_FILE) {
            linesOfCurrentGraph = new StringBuffer();
            graphStringRepresentations = new ArrayList<>();
        }

        // For each graph in the graph database
        while (hasNextGraph) {

            String[] split  = line.split(" ");
            int      gId    = Integer.parseInt(split[2]);
            GGraph   currentGraph = new GGraph("" + gId);

            if (split.length == 5) {
                currentGraph.setSupport(Integer.parseInt(split[4]));
                hasSupportValues = true;
            }

            hasNextGraph = false;
            Map<Integer, GNode> vMap = new HashMap<>();

            while ((line = br.readLine()) != null && !line.startsWith("t")) {
                if (line.length() == 0) {
                    continue;
                }
                if (SHOW_STRING_REPRESENTATION_OF_FILE) {
                    linesOfCurrentGraph.append(line);
                    linesOfCurrentGraph.append(System.lineSeparator());
                }

                String[] items = line.split(" ");

                if (line.startsWith("v")) {
                    // If it is a vertex
                    int   vId    = Integer.parseInt(items[1]);
                    int   vLabel = Integer.parseInt(items[2]);
                    GNode node   = new GNode("" + vLabel, "" + vId);
                    vMap.put(vId, node);
                    currentGraph.getNodes().add(node);
                } else if (line.startsWith("e")) {
                    // If it is an edge
                    int   v1     = Integer.parseInt(items[1]);
                    int   v2     = Integer.parseInt(items[2]);
                    int   eLabel = Integer.parseInt(items[3]);
                    GNode node1  = vMap.get(v1);
                    GNode node2  = vMap.get(v2);
                    GEdge edge   = new GEdge(node1, node2, "" + eLabel, false);
                    currentGraph.getEdges().add(edge);
                }
            }

            graphDatabase.add(currentGraph);
            if (SHOW_STRING_REPRESENTATION_OF_FILE) {
                graphStringRepresentations.add(linesOfCurrentGraph.toString());
                linesOfCurrentGraph.setLength(0);
            }
            if (line != null) {
                hasNextGraph = true;
            }
        }

        br.close();

        setTitle("SPMF Subgraph Viewer    -    File: " + file.getName());

        // Load the first graph
        if (graphDatabase.get(0).getNodes().size() != 0) {
            currentGraphIndex = 0;
            displayCurrentGraphFromGraphDatabase();
        }

        updateStatus("Loaded " + graphDatabase.size()
                + " graph(s) from: " + file.getName());
    }

    // ==================== Graph Display ====================

    /**
     * Display the currently selected graph from the graph database.
     * <p>
     * After loading the new graph and running auto-layout,
     * {@link #refreshViewerPanel()} is called to guarantee that the viewport is
     * fully repainted.  Without this call, ghost pixels from the previous graph
     * (different node count, different canvas size) can persist on screen.
     * </p>
     */
    private void displayCurrentGraphFromGraphDatabase() {
        viewerPanel.clear();

        GGraph graph = graphDatabase.get(currentGraphIndex);

        for (GNode node : graph.getNodes()) {
            viewerPanel.addNode(node);
        }

        for (GEdge edge : graph.getEdges()) {
            viewerPanel.addEdge(edge);
        }

        // Check if the canvas is big enough and auto-enlarge it, then lay out
        autoEnlargeCanvasIfNecessary(false);
        viewerPanel.autoLayout();

        // Update node and edge counts
        fieldNodeCount.setText("" + viewerPanel.getNodeCount());
        fieldEdgeCount.setText("" + viewerPanel.getEdgeCount());

        // Show or hide navigation controls depending on whether there is more than one graph
        boolean multipleGraphs = graphDatabase.size() > 1;
        navPanel.setVisible(multipleGraphs);
        labelGraphName.setVisible(multipleGraphs);
        fieldName.setVisible(multipleGraphs);

        if (multipleGraphs) {
            fieldName.setText("" + graph.getName());
            labelNumberOf.setText("Graph " + (currentGraphIndex + 1)
                    + " of " + graphDatabase.size());
            buttonPrevious.setEnabled(currentGraphIndex > 0);
            buttonNext.setEnabled(currentGraphIndex < graphDatabase.size() - 1);
        }

        // Support is only meaningful when navigating a multi-graph database
        labelSupport.setVisible(hasSupportValues && multipleGraphs);
        fieldSupport.setVisible(hasSupportValues && multipleGraphs);
        if (hasSupportValues) {
            fieldSupport.setText("" + graph.getSupport());
        }

        if (SHOW_STRING_REPRESENTATION_OF_FILE && textPaneStrings != null) {
            textPaneStrings.setText(
                    graphStringRepresentations.get(currentGraphIndex));
            textPaneStrings.setCaretPosition(0);
        }

        // Flush any ghost pixels left by the previous graph before painting the new one
        refreshViewerPanel();

        updateStatus("Displaying graph " + (currentGraphIndex + 1)
                + " of " + graphDatabase.size()
                + "  |  Nodes: " + viewerPanel.getNodeCount()
                + "  Edges: " + viewerPanel.getEdgeCount());
    }

    /**
     * Display the previous graph (if there are many) and assuming there is a
     * previous one.
     */
    protected void displayPreviousGraph() {
        currentGraphIndex--;
        displayCurrentGraphFromGraphDatabase();
    }

    /**
     * Display the next graph (if there are many) and assuming there is a
     * next one.
     */
    protected void displayNextGraph() {
        currentGraphIndex++;
        displayCurrentGraphFromGraphDatabase();
    }

    // ==================== Color Choosers ====================

    /**
     * Ask the user to choose the edge color.
     * <p>
     * Note: kept for backward compatibility; the consolidated
     * {@link EdgeAppearanceDialog} is now the primary entry point.
     * </p>
     */
    protected void changeEdgeColor() {
        Color color = JColorChooser.showDialog(
                this, "Choose edge color",
                viewerPanel.getEdgeColor());
        if (color != null) {
            viewerPanel.setEdgeColor(color);
            refreshViewerPanel();
            updateStatus("Edge color changed.");
        }
    }


    /**
     * Ask the user to choose the canvas color.
     * <p>
     * Note: kept for backward compatibility; the consolidated
     * {@link CanvasAppearanceDialog} is now the primary entry point.
     * </p>
     */
    protected void changeCanvasColor() {
        Color color = JColorChooser.showDialog(
                this, "Choose canvas color",
                viewerPanel.getCanvasColor());
        if (color != null) {
            viewerPanel.setCanvasColor(color);
            refreshViewerPanel();
            updateStatus("Canvas color changed.");
        }
    }

    /**
     * Ask the user to choose the node border color.
     * <p>
     * Note: kept for backward compatibility; the consolidated
     * {@link NodeAppearanceDialog} is now the primary entry point.
     * </p>
     */
    protected void changeNodeBorderColor() {
        Color color = JColorChooser.showDialog(
                this, "Choose node border color",
                viewerPanel.getNodeBorderColor());
        if (color != null) {
            viewerPanel.setNodeBorderColor(color);
            refreshViewerPanel();
            updateStatus("Node border color changed.");
        }
    }

    // ==================== Display Toggles ====================

    /**
     * Activate or deactivate anti-aliasing.
     *
     * @param selected true to activate, false to deactivate
     */
    protected void setAntiAliasing(boolean selected) {
        viewerPanel.setAntiAliasing(selected);
        refreshViewerPanel();
        updateStatus("Anti-aliasing " + (selected ? "enabled" : "disabled") + ".");
    }

    /**
     * Show or hide the edge IDs.
     *
     * @param selected true to show, false to hide
     */
    protected void showEdgeIDs(boolean selected) {
        viewerPanel.showEdgeIDs(selected);
        refreshViewerPanel();
    }

    /**
     * Show or hide the node IDs.
     *
     * @param selected true to show, false to hide
     */
    protected void showNodeIDs(boolean selected) {
        viewerPanel.showNodeIDs(selected);
        refreshViewerPanel();
    }

    /**
     * Show or hide the edge labels.
     *
     * @param selected true to show, false to hide
     */
    protected void showEdgeLabels(boolean selected) {
        viewerPanel.showEdgeLabels(selected);
        refreshViewerPanel();
    }

    /**
     * Show or hide the node labels.
     *
     * @param selected true to show, false to hide
     */
    protected void showNodeLabels(boolean selected) {
        viewerPanel.showNodeLabels(selected);
        refreshViewerPanel();
    }

    // ==================== Status Bar ====================

    /**
     * Update the status bar message.
     *
     * @param message the new status message to display
     */
    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    // ==================== Inner Dialog Classes ====================

    // ----------------------------------------------------------------------------------

    /**
     * A modal dialog that consolidates canvas size and canvas color settings
     * in one place.
     *
     * <p>Features:
     * <ul>
     *   <li>Two {@link JSpinner} controls (one for width, one for height) that
     *       accept values between {@value GraphViewer#CANVAS_DIM_MIN} and
     *       {@value GraphViewer#CANVAS_DIM_MAX} pixels in steps of
     *       {@value GraphViewer#CANVAS_DIM_STEP}.</li>
     *   <li>A "Lock aspect ratio" checkbox: when checked, changing one dimension
     *       automatically scales the other to maintain the current ratio.</li>
     *   <li>A live "W &times; H" readout label that updates as spinners change.</li>
     *   <li>A "Reset to current" button that restores the spinners to the canvas's
     *       actual dimensions at any point.</li>
     *   <li>An "Apply auto-layout after resize" checkbox so the user can
     *       optionally re-run the layout algorithm when OK is pressed.</li>
     *   <li>A color swatch button for picking the canvas background color, with
     *       live preview; Cancel reverts the color to the original.</li>
     *   <li>Standard OK / Cancel buttons; Cancel closes without applying size
     *       changes and reverts any color change.</li>
     * </ul>
     * </p>
     *
     * @author Philippe Fournier-Viger
     */
    private final class CanvasAppearanceDialog extends JDialog {

        /** Serial UID */
        private static final long serialVersionUID = 4L;

        /** Spinner for the canvas width */
        private final JSpinner spinnerWidth;
        /** Spinner for the canvas height */
        private final JSpinner spinnerHeight;
        /** Live readout label showing "W × H" */
        private final JLabel   labelReadout;
        /** Checkbox to lock the width:height aspect ratio */
        private final JCheckBox chkLockAspect;
        /** Checkbox to request an auto-layout pass after the resize is applied */
        private final JCheckBox chkAutoLayout;
        /** Button whose background reflects the current canvas color */
        private final JButton btnCanvasColor;

        /** Canvas color saved when the dialog was opened (used for Cancel) */
        private final Color savedCanvasColor;
        /** Currently selected canvas color (may differ from saved until OK) */
        private Color currentCanvasColor;

        /**
         * Flag set to {@code true} while one spinner is programmatically
         * updating the other, preventing infinite mutual recursion.
         */
        private boolean updatingSpinners = false;

        /** Aspect ratio (width / height) captured when the lock checkbox is ticked */
        private double lockedRatio = 1.0;

        /**
         * Construct the canvas appearance dialog.
         *
         * @param owner the parent frame
         */
        CanvasAppearanceDialog(Frame owner) {
            super(owner, "Canvas Appearance", true /* modal */);

            // Read the current canvas dimensions as the starting values
            int currentWidth  = viewerPanel.width;
            int currentHeight = viewerPanel.height;

            // Clamp into the spinner range just in case
            currentWidth  = Math.max(CANVAS_DIM_MIN, Math.min(CANVAS_DIM_MAX, currentWidth));
            currentHeight = Math.max(CANVAS_DIM_MIN, Math.min(CANVAS_DIM_MAX, currentHeight));

            // Snapshot the current canvas color so Cancel can revert it
            savedCanvasColor   = viewerPanel.getCanvasColor();
            currentCanvasColor = savedCanvasColor;

            // ---- Width spinner ----
            SpinnerNumberModel widthModel = new SpinnerNumberModel(
                    currentWidth, CANVAS_DIM_MIN, CANVAS_DIM_MAX, CANVAS_DIM_STEP);
            spinnerWidth = new JSpinner(widthModel);
            spinnerWidth.setPreferredSize(new Dimension(100, 26));

            // ---- Height spinner ----
            SpinnerNumberModel heightModel = new SpinnerNumberModel(
                    currentHeight, CANVAS_DIM_MIN, CANVAS_DIM_MAX, CANVAS_DIM_STEP);
            spinnerHeight = new JSpinner(heightModel);
            spinnerHeight.setPreferredSize(new Dimension(100, 26));

            // ---- Live readout ----
            labelReadout = new JLabel(
                    buildReadoutText(currentWidth, currentHeight),
                    SwingConstants.CENTER);
            labelReadout.setFont(new Font("SansSerif", Font.BOLD, 13));
            labelReadout.setForeground(new Color(50, 50, 160));

            // ---- Lock aspect ratio checkbox ----
            chkLockAspect = new JCheckBox("Lock aspect ratio");
            chkLockAspect.setToolTipText(
                    "When checked, changing one dimension automatically scales the other");

            // ---- Auto-layout checkbox ----
            chkAutoLayout = new JCheckBox("Apply auto-layout after resize");
            chkAutoLayout.setSelected(false);
            chkAutoLayout.setToolTipText(
                    "When checked, the layout algorithm is re-run after OK is pressed");

            // ---- Canvas color button ----
            btnCanvasColor = buildColorButton(currentCanvasColor);

            // ---- Wire listeners ----
            wireListeners();

            // ---- Lay out the dialog ----
            buildLayout(currentWidth, currentHeight);

            // ---- Dialog settings ----
            setResizable(false);
            pack();
            setLocationRelativeTo(owner);
        }

        /**
         * Create a square button whose background is the supplied color, sized
         * to give a clear swatch appearance.
         *
         * @param color initial background color
         * @return the constructed JButton
         */
        private JButton buildColorButton(Color color) {
            JButton btn = new JButton();
            btn.setBackground(color);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setPreferredSize(new Dimension(60, 26));
            btn.setToolTipText("Click to choose canvas background color");
            return btn;
        }

        /**
         * Format the live readout string from the given dimensions.
         *
         * @param w canvas width in pixels
         * @param h canvas height in pixels
         * @return the formatted string, e.g. {@code "1200 × 800 px"}
         */
        private String buildReadoutText(int w, int h) {
            return w + " \u00d7 " + h + " px";
        }

        /**
         * Attach change listeners to both spinners, the lock-aspect checkbox,
         * and the canvas color button.
         */
        private void wireListeners() {

            // Width spinner changed
            spinnerWidth.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    if (updatingSpinners) {
                        return;
                    }
                    int w = (Integer) spinnerWidth.getValue();
                    if (chkLockAspect.isSelected() && lockedRatio > 0) {
                        updatingSpinners = true;
                        int newH = (int) Math.round(w / lockedRatio);
                        newH = Math.max(CANVAS_DIM_MIN, Math.min(CANVAS_DIM_MAX, newH));
                        spinnerHeight.setValue(newH);
                        updatingSpinners = false;
                    }
                    updateReadout();
                }
            });

            // Height spinner changed
            spinnerHeight.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    if (updatingSpinners) {
                        return;
                    }
                    int h = (Integer) spinnerHeight.getValue();
                    if (chkLockAspect.isSelected() && lockedRatio > 0) {
                        updatingSpinners = true;
                        int newW = (int) Math.round(h * lockedRatio);
                        newW = Math.max(CANVAS_DIM_MIN, Math.min(CANVAS_DIM_MAX, newW));
                        spinnerWidth.setValue(newW);
                        updatingSpinners = false;
                    }
                    updateReadout();
                }
            });

            // Lock aspect ratio checkbox toggled
            chkLockAspect.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (chkLockAspect.isSelected()) {
                        // Capture the ratio at the moment the box is ticked
                        int w = (Integer) spinnerWidth.getValue();
                        int h = (Integer) spinnerHeight.getValue();
                        lockedRatio = (h > 0) ? (double) w / h : 1.0;
                    }
                }
            });

            // Canvas color button – live preview
            btnCanvasColor.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Color chosen = JColorChooser.showDialog(
                            CanvasAppearanceDialog.this,
                            "Choose canvas background color", currentCanvasColor);
                    if (chosen != null) {
                        currentCanvasColor = chosen;
                        btnCanvasColor.setBackground(chosen);
                        viewerPanel.setCanvasColor(chosen);
                        refreshViewerPanel();
                        updateStatus("Canvas color changed.");
                    }
                }
            });

            // Revert color if the dialog is closed with the window button
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    revertColor();
                }
            });
        }

        /**
         * Refresh the live "W × H" readout from the current spinner values.
         */
        private void updateReadout() {
            int w = (Integer) spinnerWidth.getValue();
            int h = (Integer) spinnerHeight.getValue();
            labelReadout.setText(buildReadoutText(w, h));
        }

        /**
         * Assemble all widgets into the dialog's content pane.
         *
         * @param currentWidth  initial canvas width shown in the spinners
         * @param currentHeight initial canvas height shown in the spinners
         */
        private void buildLayout(int currentWidth, int currentHeight) {

            JPanel content = new JPanel(new GridBagLayout());
            content.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets  = new Insets(5, 6, 5, 6);
            gbc.anchor  = GridBagConstraints.WEST;

            // ---- Row 0: live readout (spans all columns) ----
            gbc.gridx     = 0;
            gbc.gridy     = 0;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            gbc.anchor    = GridBagConstraints.CENTER;
            content.add(labelReadout, gbc);

            // ---- Separator ----
            gbc.gridy     = 1;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            content.add(new JSeparator(), gbc);

            // ---- Section header: Size ----
            gbc.gridy     = 2;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            gbc.anchor    = GridBagConstraints.WEST;
            JLabel lblSizeHeader = new JLabel("Size");
            lblSizeHeader.setFont(lblSizeHeader.getFont().deriveFont(Font.BOLD));
            content.add(lblSizeHeader, gbc);

            // ---- Row 3: Width ----
            gbc.gridwidth = 1;
            gbc.fill      = GridBagConstraints.NONE;
            gbc.anchor    = GridBagConstraints.EAST;

            gbc.gridx = 0;
            gbc.gridy = 3;
            JLabel lblW = new JLabel("Width:");
            lblW.setFont(LABEL_FONT);
            content.add(lblW, gbc);

            gbc.gridx  = 1;
            gbc.anchor = GridBagConstraints.WEST;
            content.add(spinnerWidth, gbc);

            gbc.gridx = 2;
            content.add(new JLabel("px"), gbc);

            // ---- Row 4: Height ----
            gbc.gridx  = 0;
            gbc.gridy  = 4;
            gbc.anchor = GridBagConstraints.EAST;
            JLabel lblH = new JLabel("Height:");
            lblH.setFont(LABEL_FONT);
            content.add(lblH, gbc);

            gbc.gridx  = 1;
            gbc.anchor = GridBagConstraints.WEST;
            content.add(spinnerHeight, gbc);

            gbc.gridx = 2;
            content.add(new JLabel("px"), gbc);

            // ---- Row 5: lock aspect ratio checkbox ----
            gbc.gridx     = 0;
            gbc.gridy     = 5;
            gbc.gridwidth = 3;
            gbc.anchor    = GridBagConstraints.WEST;
            content.add(chkLockAspect, gbc);

            // ---- Row 6: auto-layout checkbox ----
            gbc.gridy     = 6;
            gbc.gridwidth = 3;
            gbc.anchor    = GridBagConstraints.WEST;
            content.add(chkAutoLayout, gbc);

            // ---- Separator ----
            gbc.gridy = 7;
            gbc.fill  = GridBagConstraints.HORIZONTAL;
            content.add(new JSeparator(), gbc);

            // ---- Section header: Color ----
            gbc.gridy     = 8;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            gbc.anchor    = GridBagConstraints.WEST;
            JLabel lblColorHeader = new JLabel("Color");
            lblColorHeader.setFont(lblColorHeader.getFont().deriveFont(Font.BOLD));
            content.add(lblColorHeader, gbc);

            // ---- Row 9: Canvas background color ----
            gbc.gridwidth = 1;
            gbc.fill      = GridBagConstraints.NONE;
            gbc.anchor    = GridBagConstraints.EAST;
            gbc.gridx     = 0;
            gbc.gridy     = 9;
            JLabel lblColor = new JLabel("Background:");
            lblColor.setFont(LABEL_FONT);
            content.add(lblColor, gbc);

            gbc.gridx  = 1;
            gbc.anchor = GridBagConstraints.WEST;
            content.add(btnCanvasColor, gbc);

            // ---- Separator ----
            gbc.gridx     = 0;
            gbc.gridy     = 10;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            content.add(new JSeparator(), gbc);

            // ---- Row 11: Reset / OK / Cancel buttons ----
            gbc.gridy  = 11;
            gbc.fill   = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.CENTER;

            JButton btnReset = new JButton("Reset to current");
            btnReset.setFont(FIELD_FONT);
            btnReset.setToolTipText("Restore the spinners to the canvas's current dimensions");
            btnReset.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    updatingSpinners = true;
                    spinnerWidth.setValue(
                            Math.max(CANVAS_DIM_MIN,
                            Math.min(CANVAS_DIM_MAX, viewerPanel.width)));
                    spinnerHeight.setValue(
                            Math.max(CANVAS_DIM_MIN,
                            Math.min(CANVAS_DIM_MAX, viewerPanel.height)));
                    updatingSpinners = false;
                    updateReadout();
                }
            });

            JButton btnOk = new JButton("OK");
            btnOk.setPreferredSize(new Dimension(80, 28));
            btnOk.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    applyAndClose();
                }
            });

            JButton btnCancel = new JButton("Cancel");
            btnCancel.setPreferredSize(new Dimension(80, 28));
            btnCancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    revertColor();
                    dispose();
                }
            });

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            btnRow.add(btnReset);
            btnRow.add(btnOk);
            btnRow.add(btnCancel);

            content.add(btnRow, gbc);

            getRootPane().setDefaultButton(btnOk);
            setContentPane(content);
        }

        /**
         * Revert the canvas color to the value captured when the dialog was opened.
         */
        private void revertColor() {
            viewerPanel.setCanvasColor(savedCanvasColor);
            refreshViewerPanel();
            updateStatus("Canvas color reverted.");
        }

        /**
         * Read the spinner values, validate them, apply the new canvas size,
         * optionally run auto-layout, refresh the viewport, and close the dialog.
         */
        private void applyAndClose() {
            int newWidth  = (Integer) spinnerWidth.getValue();
            int newHeight = (Integer) spinnerHeight.getValue();

            if (newWidth < CANVAS_DIM_MIN || newHeight < CANVAS_DIM_MIN) {
                JOptionPane.showMessageDialog(
                        CanvasAppearanceDialog.this,
                        "Width and height must each be at least "
                                + CANVAS_DIM_MIN + " pixels.",
                        "Invalid size",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Apply the new size (pass false here; we handle auto-layout ourselves below)
            viewerPanel.updateSize(newWidth, newHeight, false);

            // If the user requested auto-layout, run it now after the resize
            if (chkAutoLayout.isSelected()) {
                viewerPanel.autoLayout();
            }

            // Flush stale pixels – the old canvas outline can otherwise remain visible
            refreshViewerPanel();

            String autoLayoutNote = chkAutoLayout.isSelected() ? " (auto-layout applied)" : "";
            updateStatus("Canvas resized to "
                    + newWidth + " \u00d7 " + newHeight + " px" + autoLayoutNote + ".");

            dispose();
        }
    }

    // ----------------------------------------------------------------------------------

    /**
     * A modal dialog that consolidates all node appearance settings in one place.
     * <p>
     * The dialog contains:
     * <ul>
     *   <li>A slider for the node radius ({@value GraphViewer#NODE_RADIUS_MIN}
     *       – {@value GraphViewer#NODE_RADIUS_MAX})</li>
     *   <li>A slider for the node text size ({@value GraphViewer#NODE_TEXT_MIN}
     *       – {@value GraphViewer#NODE_TEXT_MAX})</li>
     *   <li>A color swatch button for the node background color</li>
     *   <li>A color swatch button for the node border color</li>
     *   <li>A color swatch button for the node text color</li>
     * </ul>
     * Changes are applied live to the viewer panel so the user gets immediate
     * visual feedback.  If the user presses <em>Cancel</em> (or closes the
     * window) all values are reverted to the state they were in when the dialog
     * was opened.
     * </p>
     *
     * @author Philippe Fournier-Viger
     */
    private final class NodeAppearanceDialog extends JDialog {

        /** Serial UID */
        private static final long serialVersionUID = 1L;

        // ---- snapshot of values at dialog-open time (used for Cancel) ----
        /** Node radius value captured when the dialog was opened */
        private final int   savedRadius;
        /** Node text size value captured when the dialog was opened */
        private final int   savedTextSize;
        /** Node background color captured when the dialog was opened */
        private final Color savedBgColor;
        /** Node border color captured when the dialog was opened */
        private final Color savedBorderColor;
        /** Node text color captured when the dialog was opened */
        private final Color savedTextColor;

        // ---- live working copies updated as the user interacts ----
        /** Currently selected background color (may differ from saved until OK) */
        private Color currentBgColor;
        /** Currently selected border color (may differ from saved until OK) */
        private Color currentBorderColor;
        /** Currently selected text color (may differ from saved until OK) */
        private Color currentTextColor;

        // ---- widgets ----
        /** Slider to control the node radius */
        private final JSlider sliderRadius;
        /** Label that shows the current numeric value of the radius slider */
        private final JLabel  labelRadiusValue;
        /** Slider to control the node text size */
        private final JSlider sliderTextSize;
        /** Label that shows the current numeric value of the text-size slider */
        private final JLabel  labelTextSizeValue;
        /** Button whose background reflects the current node background color */
        private final JButton btnBgColor;
        /** Button whose background reflects the current node border color */
        private final JButton btnBorderColor;
        /** Button whose background reflects the current node text color */
        private final JButton btnTextColor;

        /**
         * Construct the node appearance dialog.
         *
         * @param owner the parent frame
         */
        NodeAppearanceDialog(Frame owner) {
            super(owner, "Node Appearance", true /* modal */);

            // Snapshot current values so Cancel can revert them
            savedRadius      = GNode.getRadius();
            savedTextSize    = GraphViewerPanel.getNodeTextSize();
            savedBgColor     = viewerPanel.getNodeColor();
            savedBorderColor = viewerPanel.getNodeBorderColor();
            savedTextColor   = viewerPanel.getNodeTextColor();

            // Working copies
            currentBgColor     = savedBgColor;
            currentBorderColor = savedBorderColor;
            currentTextColor   = savedTextColor;

            // ---- Radius slider ----
            int clampedRadius = Math.max(NODE_RADIUS_MIN,
                                Math.min(NODE_RADIUS_MAX, savedRadius));
            sliderRadius = new JSlider(NODE_RADIUS_MIN, NODE_RADIUS_MAX, clampedRadius);
            sliderRadius.setMajorTickSpacing(15);
            sliderRadius.setMinorTickSpacing(5);
            sliderRadius.setPaintTicks(true);
            sliderRadius.setPaintLabels(true);
            labelRadiusValue = new JLabel(String.valueOf(clampedRadius));
            labelRadiusValue.setPreferredSize(new Dimension(32, 20));

            // ---- Text-size slider ----
            int clampedTextSize = Math.max(NODE_TEXT_MIN,
                                  Math.min(NODE_TEXT_MAX, savedTextSize));
            sliderTextSize = new JSlider(NODE_TEXT_MIN, NODE_TEXT_MAX, clampedTextSize);
            sliderTextSize.setMajorTickSpacing(6);
            sliderTextSize.setMinorTickSpacing(2);
            sliderTextSize.setPaintTicks(true);
            sliderTextSize.setPaintLabels(true);
            labelTextSizeValue = new JLabel(String.valueOf(clampedTextSize));
            labelTextSizeValue.setPreferredSize(new Dimension(32, 20));

            // ---- Color swatch buttons ----
            btnBgColor     = buildColorButton(currentBgColor);
            btnBorderColor = buildColorButton(currentBorderColor);
            btnTextColor   = buildColorButton(currentTextColor);

            // ---- Wire up listeners ----
            wireListeners();

            // ---- Layout ----
            buildLayout();

            // ---- Dialog settings ----
            setResizable(false);
            pack();
            setLocationRelativeTo(owner);
        }

        /**
         * Create a square button whose background is the supplied color, sized
         * to give a clear swatch appearance.
         *
         * @param color initial background color
         * @return the constructed JButton
         */
        private JButton buildColorButton(Color color) {
            JButton btn = new JButton();
            btn.setBackground(color);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setPreferredSize(new Dimension(60, 26));
            btn.setToolTipText("Click to choose color");
            return btn;
        }

        /**
         * Attach all slider-change and button-click listeners to the widgets.
         */
        private void wireListeners() {

            // Radius slider – live preview
            sliderRadius.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int val = sliderRadius.getValue();
                    labelRadiusValue.setText(String.valueOf(val));
                    viewerPanel.updateNodeSize(val);
                    refreshViewerPanel();
                    updateStatus("Node radius: " + val);
                }
            });

            // Text-size slider – live preview
            sliderTextSize.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int val = sliderTextSize.getValue();
                    labelTextSizeValue.setText(String.valueOf(val));
                    viewerPanel.updateNodeTextSize(val);
                    refreshViewerPanel();
                    updateStatus("Node text size: " + val);
                }
            });

            // Background color button
            btnBgColor.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Color chosen = JColorChooser.showDialog(
                            NodeAppearanceDialog.this,
                            "Choose node background color", currentBgColor);
                    if (chosen != null) {
                        currentBgColor = chosen;
                        btnBgColor.setBackground(chosen);
                        viewerPanel.setNodeColor(chosen);
                        refreshViewerPanel();
                        updateStatus("Node background color changed.");
                    }
                }
            });

            // Border color button
            btnBorderColor.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Color chosen = JColorChooser.showDialog(
                            NodeAppearanceDialog.this,
                            "Choose node border color", currentBorderColor);
                    if (chosen != null) {
                        currentBorderColor = chosen;
                        btnBorderColor.setBackground(chosen);
                        viewerPanel.setNodeBorderColor(chosen);
                        refreshViewerPanel();
                        updateStatus("Node border color changed.");
                    }
                }
            });

            // Text color button
            btnTextColor.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Color chosen = JColorChooser.showDialog(
                            NodeAppearanceDialog.this,
                            "Choose node text color", currentTextColor);
                    if (chosen != null) {
                        currentTextColor = chosen;
                        btnTextColor.setBackground(chosen);
                        viewerPanel.setNodeTextColor(chosen);
                        refreshViewerPanel();
                        updateStatus("Node text color changed.");
                    }
                }
            });

            // Revert to saved values if the dialog is closed with the window button
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    revertAll();
                }
            });
        }

        /**
         * Assemble all widgets into the dialog's content pane using
         * a clean GridBagLayout.
         */
        private void buildLayout() {
            JPanel content = new JPanel(new GridBagLayout());
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets  = new Insets(5, 5, 5, 5);
            gbc.fill    = GridBagConstraints.HORIZONTAL;
            gbc.anchor  = GridBagConstraints.WEST;

            // ---- Section: Sizes ----
            addSectionHeader(content, gbc, 0, "Sizes");

            // Row: Radius
            addRow(content, gbc, 1,
                   "Node radius:", sliderRadius, labelRadiusValue);

            // Row: Text size
            addRow(content, gbc, 2,
                   "Node text size:", sliderTextSize, labelTextSizeValue);

            // ---- Separator ----
            addSeparator(content, gbc, 3);

            // ---- Section: Colors ----
            addSectionHeader(content, gbc, 4, "Colors");

            // Row: Background color
            addColorRow(content, gbc, 5,
                        "Background color:", btnBgColor);

            // Row: Border color
            addColorRow(content, gbc, 6,
                        "Border color:", btnBorderColor);

            // Row: Text color
            addColorRow(content, gbc, 7,
                        "Text color:", btnTextColor);

            // ---- Separator ----
            addSeparator(content, gbc, 8);

            // ---- OK / Cancel buttons ----
            JPanel btnPanel = buildOkCancelPanel();

            gbc.gridx      = 0;
            gbc.gridy      = 9;
            gbc.gridwidth  = 3;
            gbc.fill       = GridBagConstraints.NONE;
            gbc.anchor     = GridBagConstraints.CENTER;
            content.add(btnPanel, gbc);

            setContentPane(content);
        }

        /**
         * Add a bold section-header label spanning all three columns.
         *
         * @param panel the target panel
         * @param gbc   the shared constraints object (will be mutated)
         * @param row   the grid row to use
         * @param title the header text
         */
        private void addSectionHeader(JPanel panel, GridBagConstraints gbc,
                                      int row, String title) {
            JLabel hdr = new JLabel(title);
            hdr.setFont(hdr.getFont().deriveFont(Font.BOLD));
            gbc.gridx     = 0;
            gbc.gridy     = row;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            panel.add(hdr, gbc);
            gbc.gridwidth = 1; // reset
        }

        /**
         * Add a thin horizontal separator spanning all three columns.
         *
         * @param panel the target panel
         * @param gbc   the shared constraints object (will be mutated)
         * @param row   the grid row to use
         */
        private void addSeparator(JPanel panel, GridBagConstraints gbc, int row) {
            JSeparator sep = new JSeparator();
            gbc.gridx     = 0;
            gbc.gridy     = row;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            panel.add(sep, gbc);
            gbc.gridwidth = 1; // reset
        }

        /**
         * Add a slider row: label | slider (expanding) | value label.
         *
         * @param panel      the target panel
         * @param gbc        the shared constraints object (will be mutated)
         * @param row        the grid row to use
         * @param labelText  text for the row label
         * @param slider     the JSlider widget
         * @param valueLabel the label that shows the numeric value
         */
        private void addRow(JPanel panel, GridBagConstraints gbc,
                            int row, String labelText,
                            JSlider slider, JLabel valueLabel) {
            // Column 0 – row label
            gbc.gridx  = 0;
            gbc.gridy  = row;
            gbc.fill   = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.EAST;
            gbc.weightx = 0.0;
            panel.add(new JLabel(labelText), gbc);

            // Column 1 – slider (gets all extra horizontal space)
            gbc.gridx   = 1;
            gbc.fill    = GridBagConstraints.HORIZONTAL;
            gbc.anchor  = GridBagConstraints.WEST;
            gbc.weightx = 1.0;
            panel.add(slider, gbc);

            // Column 2 – numeric value label (fixed width)
            gbc.gridx   = 2;
            gbc.fill    = GridBagConstraints.NONE;
            gbc.weightx = 0.0;
            panel.add(valueLabel, gbc);
        }

        /**
         * Add a color-picker row: label | color swatch button.
         *
         * @param panel     the target panel
         * @param gbc       the shared constraints object (will be mutated)
         * @param row       the grid row to use
         * @param labelText text for the row label
         * @param btn       the color swatch JButton
         */
        private void addColorRow(JPanel panel, GridBagConstraints gbc,
                                 int row, String labelText, JButton btn) {
            // Column 0 – row label
            gbc.gridx   = 0;
            gbc.gridy   = row;
            gbc.fill    = GridBagConstraints.NONE;
            gbc.anchor  = GridBagConstraints.EAST;
            gbc.weightx = 0.0;
            panel.add(new JLabel(labelText), gbc);

            // Column 1 – swatch button
            gbc.gridx   = 1;
            gbc.fill    = GridBagConstraints.NONE;
            gbc.anchor  = GridBagConstraints.WEST;
            gbc.weightx = 0.0;
            panel.add(btn, gbc);
        }

        /**
         * Build the OK / Cancel button panel.
         *
         * @return the constructed JPanel
         */
        private JPanel buildOkCancelPanel() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

            JButton btnOk = new JButton("OK");
            btnOk.setPreferredSize(new Dimension(80, 28));
            btnOk.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    refreshViewerPanel();
                    updateStatus("Node appearance updated: radius="
                            + sliderRadius.getValue()
                            + ", textSize=" + sliderTextSize.getValue() + ".");
                    dispose();
                }
            });
            p.add(btnOk);

            JButton btnCancel = new JButton("Cancel");
            btnCancel.setPreferredSize(new Dimension(80, 28));
            btnCancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    revertAll();
                    dispose();
                }
            });
            p.add(btnCancel);

            // Make OK the default button so Enter confirms the dialog
            getRootPane().setDefaultButton(btnOk);

            return p;
        }

        /**
         * Revert all node appearance settings to the values captured when the
         * dialog was opened.
         */
        private void revertAll() {
            viewerPanel.updateNodeSize(savedRadius);
            viewerPanel.updateNodeTextSize(savedTextSize);
            viewerPanel.setNodeColor(savedBgColor);
            viewerPanel.setNodeBorderColor(savedBorderColor);
            viewerPanel.setNodeTextColor(savedTextColor);
            refreshViewerPanel();
            updateStatus("Node appearance changes cancelled.");
        }
    }

    // ----------------------------------------------------------------------------------

    /**
     * A modal dialog that consolidates all edge appearance settings in one place.
     * <p>
     * The dialog contains:
     * <ul>
     *   <li>A slider for the edge line width ({@value GraphViewer#EDGE_WIDTH_MIN}
     *       – {@value GraphViewer#EDGE_WIDTH_MAX})</li>
     *   <li>A slider for the edge text size ({@value GraphViewer#EDGE_TEXT_MIN}
     *       – {@value GraphViewer#EDGE_TEXT_MAX})</li>
     *   <li>A color swatch button for the edge line color</li>
     *   <li>A color swatch button for the edge text color</li>
     * </ul>
     * Changes are applied live to the viewer panel so the user gets immediate
     * visual feedback.  If the user presses <em>Cancel</em> (or closes the
     * window) all values are reverted to the state they were in when the dialog
     * was opened.
     * </p>
     *
     * @author Philippe Fournier-Viger
     */
    private final class EdgeAppearanceDialog extends JDialog {

        /** Serial UID */
        private static final long serialVersionUID = 2L;

        // ---- snapshot of values at dialog-open time (used for Cancel) ----
        /** Edge thickness value captured when the dialog was opened */
        private final int   savedThickness;
        /** Edge text size value captured when the dialog was opened */
        private final int   savedTextSize;
        /** Edge line color captured when the dialog was opened */
        private final Color savedEdgeColor;
        /** Edge text color captured when the dialog was opened */
        private final Color savedTextColor;

        // ---- live working copies updated as the user interacts ----
        /** Currently selected edge line color (may differ from saved until OK) */
        private Color currentEdgeColor;
        /** Currently selected edge text color (may differ from saved until OK) */
        private Color currentTextColor;

        // ---- widgets ----
        /** Slider to control the edge line width */
        private final JSlider sliderWidth;
        /** Label that shows the current numeric value of the width slider */
        private final JLabel  labelWidthValue;
        /** Slider to control the edge text size */
        private final JSlider sliderTextSize;
        /** Label that shows the current numeric value of the text-size slider */
        private final JLabel  labelTextSizeValue;
        /** Button whose background reflects the current edge line color */
        private final JButton btnEdgeColor;
        /** Button whose background reflects the current edge text color */
        private final JButton btnTextColor;

        /**
         * Construct the edge appearance dialog.
         *
         * @param owner the parent frame
         */
        EdgeAppearanceDialog(Frame owner) {
            super(owner, "Edge Appearance", true /* modal */);

            // Snapshot current values so Cancel can revert them
            savedThickness = GEdge.getEdgeThickness();
            savedTextSize  = GraphViewerPanel.getEdgeTextSize();
            savedEdgeColor = viewerPanel.getEdgeColor();
            savedTextColor = viewerPanel.getEdgeTextColor();

            // Working copies
            currentEdgeColor = savedEdgeColor;
            currentTextColor = savedTextColor;

            // ---- Width slider ----
            int clampedWidth = Math.max(EDGE_WIDTH_MIN,
                               Math.min(EDGE_WIDTH_MAX, savedThickness));
            sliderWidth = new JSlider(EDGE_WIDTH_MIN, EDGE_WIDTH_MAX, clampedWidth);
            sliderWidth.setMajorTickSpacing(4);
            sliderWidth.setMinorTickSpacing(1);
            sliderWidth.setPaintTicks(true);
            sliderWidth.setPaintLabels(true);
            labelWidthValue = new JLabel(String.valueOf(clampedWidth));
            labelWidthValue.setPreferredSize(new Dimension(32, 20));

            // ---- Text-size slider ----
            int clampedTextSize = Math.max(EDGE_TEXT_MIN,
                                  Math.min(EDGE_TEXT_MAX, savedTextSize));
            sliderTextSize = new JSlider(EDGE_TEXT_MIN, EDGE_TEXT_MAX, clampedTextSize);
            sliderTextSize.setMajorTickSpacing(6);
            sliderTextSize.setMinorTickSpacing(2);
            sliderTextSize.setPaintTicks(true);
            sliderTextSize.setPaintLabels(true);
            labelTextSizeValue = new JLabel(String.valueOf(clampedTextSize));
            labelTextSizeValue.setPreferredSize(new Dimension(32, 20));

            // ---- Color swatch buttons ----
            btnEdgeColor = buildColorButton(currentEdgeColor);
            btnTextColor = buildColorButton(currentTextColor);

            // ---- Wire up listeners ----
            wireListeners();

            // ---- Layout ----
            buildLayout();

            // ---- Dialog settings ----
            setResizable(false);
            pack();
            setLocationRelativeTo(owner);
        }

        /**
         * Create a square button whose background is the supplied color, sized
         * to give a clear swatch appearance.
         *
         * @param color initial background color
         * @return the constructed JButton
         */
        private JButton buildColorButton(Color color) {
            JButton btn = new JButton();
            btn.setBackground(color);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setPreferredSize(new Dimension(60, 26));
            btn.setToolTipText("Click to choose color");
            return btn;
        }

        /**
         * Attach all slider-change and button-click listeners to the widgets.
         */
        private void wireListeners() {

            // Width slider – live preview
            sliderWidth.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int val = sliderWidth.getValue();
                    labelWidthValue.setText(String.valueOf(val));
                    viewerPanel.updateEdgeThickness(val);
                    refreshViewerPanel();
                    updateStatus("Edge width: " + val);
                }
            });

            // Text-size slider – live preview
            sliderTextSize.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int val = sliderTextSize.getValue();
                    labelTextSizeValue.setText(String.valueOf(val));
                    viewerPanel.updateEdgeTextSize(val);
                    refreshViewerPanel();
                    updateStatus("Edge text size: " + val);
                }
            });

            // Edge line color button
            btnEdgeColor.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Color chosen = JColorChooser.showDialog(
                            EdgeAppearanceDialog.this,
                            "Choose edge color", currentEdgeColor);
                    if (chosen != null) {
                        currentEdgeColor = chosen;
                        btnEdgeColor.setBackground(chosen);
                        viewerPanel.setEdgeColor(chosen);
                        refreshViewerPanel();
                        updateStatus("Edge color changed.");
                    }
                }
            });

            // Edge text color button
            btnTextColor.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Color chosen = JColorChooser.showDialog(
                            EdgeAppearanceDialog.this,
                            "Choose edge text color", currentTextColor);
                    if (chosen != null) {
                        currentTextColor = chosen;
                        btnTextColor.setBackground(chosen);
                        viewerPanel.setEdgeTextColor(chosen);
                        refreshViewerPanel();
                        updateStatus("Edge text color changed.");
                    }
                }
            });

            // Revert to saved values if the dialog is closed with the window button
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    revertAll();
                }
            });
        }

        /**
         * Assemble all widgets into the dialog's content pane using
         * a clean GridBagLayout.
         */
        private void buildLayout() {
            JPanel content = new JPanel(new GridBagLayout());
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets  = new Insets(5, 5, 5, 5);
            gbc.fill    = GridBagConstraints.HORIZONTAL;
            gbc.anchor  = GridBagConstraints.WEST;

            // ---- Section: Sizes ----
            addSectionHeader(content, gbc, 0, "Sizes");

            // Row: Width
            addRow(content, gbc, 1,
                   "Edge width:", sliderWidth, labelWidthValue);

            // Row: Text size
            addRow(content, gbc, 2,
                   "Edge text size:", sliderTextSize, labelTextSizeValue);

            // ---- Separator ----
            addSeparator(content, gbc, 3);

            // ---- Section: Colors ----
            addSectionHeader(content, gbc, 4, "Colors");

            // Row: Edge line color
            addColorRow(content, gbc, 5,
                        "Edge color:", btnEdgeColor);

            // Row: Edge text color
            addColorRow(content, gbc, 6,
                        "Text color:", btnTextColor);

            // ---- Separator ----
            addSeparator(content, gbc, 7);

            // ---- OK / Cancel buttons ----
            JPanel btnPanel = buildOkCancelPanel();

            gbc.gridx     = 0;
            gbc.gridy     = 8;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.NONE;
            gbc.anchor    = GridBagConstraints.CENTER;
            content.add(btnPanel, gbc);

            setContentPane(content);
        }

        /**
         * Add a bold section-header label spanning all three columns.
         *
         * @param panel the target panel
         * @param gbc   the shared constraints object (will be mutated)
         * @param row   the grid row to use
         * @param title the header text
         */
        private void addSectionHeader(JPanel panel, GridBagConstraints gbc,
                                      int row, String title) {
            JLabel hdr = new JLabel(title);
            hdr.setFont(hdr.getFont().deriveFont(Font.BOLD));
            gbc.gridx     = 0;
            gbc.gridy     = row;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            panel.add(hdr, gbc);
            gbc.gridwidth = 1; // reset
        }

        /**
         * Add a thin horizontal separator spanning all three columns.
         *
         * @param panel the target panel
         * @param gbc   the shared constraints object (will be mutated)
         * @param row   the grid row to use
         */
        private void addSeparator(JPanel panel, GridBagConstraints gbc, int row) {
            JSeparator sep = new JSeparator();
            gbc.gridx     = 0;
            gbc.gridy     = row;
            gbc.gridwidth = 3;
            gbc.fill      = GridBagConstraints.HORIZONTAL;
            panel.add(sep, gbc);
            gbc.gridwidth = 1; // reset
        }

        /**
         * Add a slider row: label | slider (expanding) | value label.
         *
         * @param panel      the target panel
         * @param gbc        the shared constraints object (will be mutated)
         * @param row        the grid row to use
         * @param labelText  text for the row label
         * @param slider     the JSlider widget
         * @param valueLabel the label that shows the numeric value
         */
        private void addRow(JPanel panel, GridBagConstraints gbc,
                            int row, String labelText,
                            JSlider slider, JLabel valueLabel) {
            // Column 0 – row label
            gbc.gridx   = 0;
            gbc.gridy   = row;
            gbc.fill    = GridBagConstraints.NONE;
            gbc.anchor  = GridBagConstraints.EAST;
            gbc.weightx = 0.0;
            panel.add(new JLabel(labelText), gbc);

            // Column 1 – slider (gets all extra horizontal space)
            gbc.gridx   = 1;
            gbc.fill    = GridBagConstraints.HORIZONTAL;
            gbc.anchor  = GridBagConstraints.WEST;
            gbc.weightx = 1.0;
            panel.add(slider, gbc);

            // Column 2 – numeric value label (fixed width)
            gbc.gridx   = 2;
            gbc.fill    = GridBagConstraints.NONE;
            gbc.weightx = 0.0;
            panel.add(valueLabel, gbc);
        }

        /**
         * Add a color-picker row: label | color swatch button.
         *
         * @param panel     the target panel
         * @param gbc       the shared constraints object (will be mutated)
         * @param row       the grid row to use
         * @param labelText text for the row label
         * @param btn       the color swatch JButton
         */
        private void addColorRow(JPanel panel, GridBagConstraints gbc,
                                 int row, String labelText, JButton btn) {
            // Column 0 – row label
            gbc.gridx   = 0;
            gbc.gridy   = row;
            gbc.fill    = GridBagConstraints.NONE;
            gbc.anchor  = GridBagConstraints.EAST;
            gbc.weightx = 0.0;
            panel.add(new JLabel(labelText), gbc);

            // Column 1 – swatch button
            gbc.gridx   = 1;
            gbc.fill    = GridBagConstraints.NONE;
            gbc.anchor  = GridBagConstraints.WEST;
            gbc.weightx = 0.0;
            panel.add(btn, gbc);
        }

        /**
         * Build the OK / Cancel button panel.
         *
         * @return the constructed JPanel
         */
        private JPanel buildOkCancelPanel() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

            JButton btnOk = new JButton("OK");
            btnOk.setPreferredSize(new Dimension(80, 28));
            btnOk.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    refreshViewerPanel();
                    updateStatus("Edge appearance updated: width="
                            + sliderWidth.getValue()
                            + ", textSize=" + sliderTextSize.getValue() + ".");
                    dispose();
                }
            });
            p.add(btnOk);

            JButton btnCancel = new JButton("Cancel");
            btnCancel.setPreferredSize(new Dimension(80, 28));
            btnCancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    revertAll();
                    dispose();
                }
            });
            p.add(btnCancel);

            // Make OK the default button so Enter confirms the dialog
            getRootPane().setDefaultButton(btnOk);

            return p;
        }

        /**
         * Revert all edge appearance settings to the values captured when the
         * dialog was opened.
         */
        private void revertAll() {
            viewerPanel.updateEdgeThickness(savedThickness);
            viewerPanel.updateEdgeTextSize(savedTextSize);
            viewerPanel.setEdgeColor(savedEdgeColor);
            viewerPanel.setEdgeTextColor(savedTextColor);
            refreshViewerPanel();
            updateStatus("Edge appearance changes cancelled.");
        }
    }
}