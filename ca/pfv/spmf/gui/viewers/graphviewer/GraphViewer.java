package ca.pfv.spmf.gui.viewers.graphviewer;

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
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.GraphLayoutCircle;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.GraphLayoutFruchtermanReingold;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.GraphLayoutFruchtermanReingoldGrid;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.GraphLayoutGrid;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.GraphLayoutRandom;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.GraphLayoutRectangle;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GGraph;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

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
 */
/**
 * Tool to visualize subgraphs, implemented as a JFrame using Swing.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class GraphViewer extends JFrame {
	/** Serial UID */
	private static final long serialVersionUID = -9054590513003092459L;

	/** The panel to display subgraphs */
	GraphViewerPanel viewerPanel;

	/** Flag indicating if the application is run as a standalone program or not */
	boolean runAsStandalone = true;

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

	/** Keep string representation of input file in memory (true or false */
	private boolean SHOW_STRING_REPRESENTATION_OF_FILE = true;

	/** The current graph database */
	private List<GGraph> graphDatabase;

	/** The current graph database */
	private List<String> graphStringRepresentations;
	private JScrollPane scrollPaneStrings;
	private JTextPane textPaneStrings;

	/** Minimum canvas size */
	int minimumCanvasWidth = 300;
	
	/** Minimum canvas height */
	int minimumCanvasHeight = 300;

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone true if run as standalone program, otherwise false.
	 */
	public GraphViewer(boolean runAsStandalone, boolean displayGraphStringRepresentation) {

		this.SHOW_STRING_REPRESENTATION_OF_FILE = displayGraphStringRepresentation;

		// Set the icon
		setIconImage(Toolkit.getDefaultToolkit().getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));
		setSize(800, 625);

		// Set the window in the center of the screen
		this.setLocationRelativeTo(null);

		// Set the title
		this.setTitle("SPMF Graph Viewer");

		// Set the size
		this.setMinimumSize(new Dimension(800, 300));

		// If running as standalone
		if (runAsStandalone) {
			this.runAsStandalone = runAsStandalone;
			this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		createMenuBar();

		// Set the layout
		getContentPane().setLayout(new BorderLayout(0, 0));

		// Create the tools panel
		JPanel toolsPanel = new JPanel();
		toolsPanel.setLayout(null);
		toolsPanel.setMinimumSize(new Dimension(800, 100));
		toolsPanel.setPreferredSize(new Dimension(800, 100));
		toolsPanel.setMaximumSize(new Dimension(800, 100));
		getContentPane().add(toolsPanel, BorderLayout.SOUTH);

		buttonPrevious = new JButton("<");
		buttonPrevious.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				displayPreviousGraph();
			}
		});
		buttonPrevious.setEnabled(false);
		buttonPrevious.setToolTipText("Redraw");
		buttonPrevious.setBounds(10, 28, 44, 29);
		toolsPanel.add(buttonPrevious);

		buttonNext = new JButton(">");
		buttonNext.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				displayNextGraph();
			}
		});
		buttonNext.setEnabled(false);
		buttonNext.setToolTipText("Redraw");
		buttonNext.setBounds(58, 28, 44, 29);
		toolsPanel.add(buttonNext);

		// Label for edge count
		labelEdgeCount = new JLabel("Edge count:");
		labelEdgeCount.setBounds(215, 39, 104, 14);
		toolsPanel.add(labelEdgeCount);

		// Label for node count
		labelNodeCount = new JLabel("Node count:");
		labelNodeCount.setBounds(215, 11, 104, 14);
		toolsPanel.add(labelNodeCount);

		labelGraphName = new JLabel("Graph ID:");
		labelGraphName.setBounds(133, 11, 104, 14);
		toolsPanel.add(labelGraphName);

		labelNumberOf = new JLabel("Graph 1 of 1");
		labelNumberOf.setBounds(10, 11, 113, 14);
		toolsPanel.add(labelNumberOf);

		fieldNodeCount = new JTextField();
		fieldNodeCount.setEditable(false);
		fieldNodeCount.setBounds(292, 8, 104, 20);
		toolsPanel.add(fieldNodeCount);
		fieldNodeCount.setColumns(10);

		fieldEdgeCount = new JTextField();
		fieldEdgeCount.setEditable(false);
		fieldEdgeCount.setColumns(10);
		fieldEdgeCount.setBounds(292, 36, 104, 20);
		toolsPanel.add(fieldEdgeCount);

		fieldName = new JTextField();
		fieldName.setEditable(false);
		fieldName.setColumns(10);
		fieldName.setBounds(133, 32, 66, 20);
		toolsPanel.add(fieldName);

		labelSupport = new JLabel("Support:");
		labelSupport.setBounds(235, 67, 104, 14);
		toolsPanel.add(labelSupport);

		fieldSupport = new JTextField();
		fieldSupport.setEditable(false);
		fieldSupport.setColumns(10);
		fieldSupport.setBounds(292, 64, 104, 20);
		toolsPanel.add(fieldSupport);

		JButton btnNewButton = new JButton("Change canvas size");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseCanvasSize(false);
			}
		});
		btnNewButton.setBounds(606, 7, 170, 23);
		toolsPanel.add(btnNewButton);

		// Panel to view the subgraph with scrollbars (JScrollpane)
		int panelWidth = SHOW_STRING_REPRESENTATION_OF_FILE ? minimumCanvasWidth : minimumCanvasWidth;
		int panelHeight = minimumCanvasHeight;
		viewerPanel = new GraphViewerPanel(new GraphLayoutFruchtermanReingold(), panelWidth, panelHeight);
		viewerPanel.setPreferredSize(new Dimension(panelWidth, panelHeight));
		JScrollPane scrollPane = new JScrollPane(viewerPanel);

		scrollPane.setPreferredSize(new Dimension(300, 250));
//		scrollPane.setViewportBorder(BorderFactory.createLineBorder(Color.blue));
		scrollPane.setAutoscrolls(true);
		viewerPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
		getContentPane().add(scrollPane, BorderLayout.CENTER);

		// Set the window visible
		setVisible(true);

		// Set the size of the panel (!! Important otherwise, no scrollbars!!)
		viewerPanel.setPreferredSize(new Dimension(viewerPanel.getWidth(), viewerPanel.getHeight()));

		if (SHOW_STRING_REPRESENTATION_OF_FILE) {
			textPaneStrings = new JTextPane();
			textPaneStrings.setEditable(false);
			textPaneStrings.setEnabled(true);
			scrollPaneStrings = new JScrollPane(textPaneStrings);
			scrollPaneStrings.setAutoscrolls(true);
			scrollPaneStrings.setPreferredSize(new Dimension(100, 100));
			getContentPane().add(scrollPaneStrings, BorderLayout.EAST);
		}
	}

	/**
	 * Create the menu bar
	 */
	private void createMenuBar() {
		// Create a menubar
		JMenuBar mb = new JMenuBar();

		// ================ Appearance File ======================
		JMenu menuFile = new JMenu("File");
		JMenuItem menuFileSave = new JMenuItem("Export as PNG");
		menuFileSave.setIcon(new ImageIcon(SPMFTextEditor.class.getResource("/ca/pfv/spmf/gui/icons/save.gif")));
		menuFileSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewerPanel.exportAsPNG();
			}
		});
		menuFile.add(menuFileSave);

		String quitName = runAsStandalone ? "Quit" : "Close";
		menuFile.addSeparator();
		JMenuItem menuFileQuit = new JMenuItem(quitName);
		menuFileQuit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionQuit();
			}
		});
		menuFile.add(menuFileQuit);

		mb.add(menuFile);

		// ================ Appearance MENU NODES, EDGES, CANVAS ======================
		JMenu menuNodes = new JMenu("Nodes");
		JMenu menuEdges = new JMenu("Edges");
		JMenu menuCanvas = new JMenu("Canvas");


		JMenuItem menuNodeWidth = new JMenuItem("Change node radius");
//				menuNodeWidth.setIcon(new ImageIcon(SPMFTextEditor.class.getResource("/ca/pfv/spmf/gui/icons/Cut24.gif")));
		menuNodeWidth.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				resizeNodesRadius();
			}
		});
		menuNodes.add(menuNodeWidth);
		

		JMenuItem menuNodeTextSize = new JMenuItem("Change node text size");
		menuNodeTextSize.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeNodeTextSize();
			}
		});
		menuNodes.add(menuNodeTextSize);
		
		menuNodes.addSeparator();

		JMenuItem menuNodeColor = new JMenuItem("Change node background color");
		menuNodeColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeNodeBackgroundColor();
			}
		});
		menuNodes.add(menuNodeColor);

		JMenuItem menuNodeBorderColor = new JMenuItem("Change node border color");
		menuNodeBorderColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeNodeBorderColor();
			}
		});
		menuNodes.add(menuNodeBorderColor);

		JMenuItem menuTextColor = new JMenuItem("Change node text color");
		menuTextColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeNodeTextColor();
			}

		});
		menuNodes.add(menuTextColor);
		
		
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

//		menuNodes.addSeparator();

		JMenuItem menuEdgeWidth = new JMenuItem("Change edge width");
//				menuEdgeWidth.setIcon(new ImageIcon(SPMFTextEditor.class.getResource("/ca/pfv/spmf/gui/icons/Cut24.gif")));
		menuEdgeWidth.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				resizeEdges();
			}
		});
		menuEdges.add(menuEdgeWidth);
		
		JMenuItem menuEdgeTextSize = new JMenuItem("Change edge text size");
		menuEdgeTextSize.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeEdgeTextSize();
			}
		});
		menuEdges.add(menuEdgeTextSize);
		
		menuEdges.addSeparator();

		JMenuItem menuEdgeColor = new JMenuItem("Change edge color");
		menuEdgeColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeEdgeColor();
			}

		});
		menuEdges.add(menuEdgeColor);

		JMenuItem menuEdgeTextColor = new JMenuItem("Change edge text color");
		menuEdgeTextColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeEdgeTextColor();
			}

		});
		menuEdges.add(menuEdgeTextColor);
	
		

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

//		menuNodes.addSeparator();


		JMenuItem menuCanvasSize = new JMenuItem("Change canvas size");
		menuCanvasSize.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseCanvasSize(false);
			}

		});
		menuCanvas.add(menuCanvasSize);

		JMenuItem menuCanvasSizeAndAuto = new JMenuItem("Change canvas size and do auto-layout");
		menuCanvasSizeAndAuto.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseCanvasSize(true);
			}

		});
		menuCanvas.add(menuCanvasSizeAndAuto);

		JMenuItem menuCanvasSizeAuto = new JMenuItem("Auto-set canvas size and do auto-layout");
		menuCanvasSizeAuto.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionAutoSetCanvasSize();
			}

		});
		menuCanvas.add(menuCanvasSizeAuto);

		menuCanvas.addSeparator();


		JMenuItem menuBackgroundColor = new JMenuItem("Change canvas color");
		menuBackgroundColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeCanvasColor();
			}

		});
		menuCanvas.add(menuBackgroundColor);

		menuCanvas.addSeparator();
		
		JCheckBoxMenuItem menuAntiAliasing = new JCheckBoxMenuItem("Anti-aliasing");
		menuAntiAliasing.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setAntiAliasing(menuAntiAliasing.isSelected());
			}
		});
		menuCanvas.add(menuAntiAliasing);


		// ================ menu LAYOUT ======================
		JMenu menuLayouts = new JMenu("Graph layout");
		ButtonGroup layoutGroup = new ButtonGroup();

		JMenuItem menuLayout1 = new JRadioButtonMenuItem("FruchtermanReingold91");
		menuLayout1.setSelected(true);
		menuLayout1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				changeLayout(0);
			}
		});
		menuLayouts.add(menuLayout1);

		JMenuItem menuLayout2 = new JRadioButtonMenuItem("FruchtermanReingold91(grid)");
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


		mb.add(menuCanvas);
		mb.add(menuNodes);
		mb.add(menuEdges);
		mb.add(menuLayouts);

		layoutGroup.add(menuLayout1);
		layoutGroup.add(menuLayout2);
		layoutGroup.add(menuLayout3);
		layoutGroup.add(menuLayout4);
		layoutGroup.add(menuLayout5);
		layoutGroup.add(menuLayout6);

		// Set the menubar
		this.setJMenuBar(mb);
	}

	protected void actionAutoSetCanvasSize() {
		autoEnlargeCanvasIfNecessary(true);
		viewerPanel.autoLayout();
		viewerPanel.repaint();
	}

	protected void changeNodeTextSize() {
		String s = (String) JOptionPane.showInputDialog(this, "Choose node text size:", "Text size dialog",
				JOptionPane.QUESTION_MESSAGE, null, null, viewerPanel.getNodeTextSize());

		if (s == null || s.length() == 0) {
			return;
		}

		int newTextSize = 0;
		try {
			newTextSize = Integer.parseInt(s);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Text size must be a positive integer number.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (newTextSize < 1) {
			JOptionPane.showMessageDialog(this, "Text size sizedth must be a positive integer number.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		viewerPanel.updateNodeTextSize(newTextSize);
	}
	
	protected void changeEdgeTextSize() {
		String s = (String) JOptionPane.showInputDialog(this, "Choose edge text size:", "Text size dialog",
				JOptionPane.QUESTION_MESSAGE, null, null, viewerPanel.getEdgeTextSize());

		if (s == null || s.length() == 0) {
			return;
		}

		int newTextSize = 0;
		try {
			newTextSize = Integer.parseInt(s);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Text size must be a positive integer number.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (newTextSize < 1) {
			JOptionPane.showMessageDialog(this, "Text size sizedth must be a positive integer number.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		viewerPanel.updateEdgeTextSize(newTextSize);
	}

	protected void actionQuit() {
		if (runAsStandalone) {
			System.exit(0);
		} else {
			setVisible(false);
		}
	}

	/**
	 * Ask user to resize the edges
	 */
	protected void resizeEdges() {
		String s = (String) JOptionPane.showInputDialog(this, "Choose edge width:", "Resize dialog",
				JOptionPane.QUESTION_MESSAGE, null, null, GEdge.getEdgeThickness());

		if (s == null || s.length() == 0) {
			return;
		}

		int newThickness = 0;
		try {
			newThickness = Integer.parseInt(s);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Width must be a positive integer number.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (newThickness < 1) {
			JOptionPane.showMessageDialog(this, "Width must be a positive integer number.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		viewerPanel.updateEdgeThickness(newThickness);
	}

	/**
	 * Ask user to resize the nodes
	 */
	protected void resizeNodesRadius() {

		String s = (String) JOptionPane.showInputDialog(this, "Choose node radius:", "Resize dialog",
				JOptionPane.QUESTION_MESSAGE, null, null, GNode.getRadius());

		if (s == null || s.length() == 0) {
			return;
		}

		int newRadius = 0;
		try {
			newRadius = Integer.parseInt(s);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Width must be a positive integer number (>5).", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (newRadius < 5) {
			JOptionPane.showMessageDialog(this, "Width must be a positive integer number (>5).", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		viewerPanel.updateNodeSize(newRadius);
	}

	/**
	 * Ask the user to choose a canvas size
	 * 
	 * @param doAutoLayout do auto layout
	 */
	protected void chooseCanvasSize(boolean doAutoLayout) {
		int currentWidth = viewerPanel.getWidth();
		int currentHeight = viewerPanel.getHeight();

		// Width
		String s = (String) JOptionPane.showInputDialog(this, "Choose width:", "Resize dialog",
				JOptionPane.QUESTION_MESSAGE, null, null, currentWidth);

		if (s == null || s.length() == 0) {
			return;
		}

		int newWidth = 0;
		try {
			newWidth = Integer.parseInt(s);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Width must be a positive integer number (>100).", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (newWidth < 100) {
			JOptionPane.showMessageDialog(this, "Width must be a positive integer number (>100).", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Height
		s = (String) JOptionPane.showInputDialog(this, "Choose height:", "Resize dialog", JOptionPane.QUESTION_MESSAGE,
				null, null, currentHeight);

		if (s == null || s.length() == 0) {
			return;
		}

		int newHeight = 0;
		try {
			newHeight = Integer.parseInt(s);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Height must be a positive integer number (>100).", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (newHeight < 100) {
			JOptionPane.showMessageDialog(this, "Height must be a positive integer number (>100).", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		viewerPanel.updateSize(newWidth, newHeight, doAutoLayout);
	}

	/**
	 * Check the graph layout after it is selected in the combo box
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
		viewerPanel.repaint();

		// String[] layoutAlgorithms = new String[] { "FruchtermanReingold91",
		// "FruchtermanReingold91(faster)", "Grid", "Circle", "Random" };
	}

	/**
	 * Load a sambple graph for debugging
	 */
	public void loadSampleGraph() {

		GNode[] nodes = new GNode[] { new GNode("Paul"), new GNode("Jack"), new GNode("Katie"), new GNode("Paolo"),
				new GNode("Usman") };
		for (GNode node : nodes) {
			viewerPanel.addNode(node);
		}

		GEdge edge1 = new GEdge(nodes[0], nodes[1], "friend", true);
		GEdge edge2 = new GEdge(nodes[1], nodes[2], "roommate", false);
		GEdge edge3 = new GEdge(nodes[0], nodes[3], "friend", true);
		GEdge edge4 = new GEdge(nodes[2], nodes[3], "friend", true);
		GEdge edge5 = new GEdge(nodes[4], nodes[3], "friend", true);
		GEdge edge6 = new GEdge(nodes[4], nodes[0], "friend", true);

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
		labelNumberOf.setVisible(false);
		buttonPrevious.setVisible(false);
		buttonNext.setVisible(false);
		labelSupport.setVisible(false);
		fieldSupport.setVisible(false);
		labelGraphName.setVisible(false);
		fieldName.setVisible(false);
		// ****************************************
	}

	/**
	 * Auto-enlarge the canvas if the size is too small for the number of nodes in a
	 * graph.
	 * 
	 * @param forceResize force to resize no matter what
	 * @return true if the canvas was enlarged.
	 */
	private boolean autoEnlargeCanvasIfNecessary(boolean forceResize) {
		// Calculate the required area in pixels to put all nodes
		// This is the sum of the area of each node (square of the diameter), multiplied
		// by the number of nodes
		// multiplied by a multiplying factor.

		double multiplyingFactor = 4d;

		double requiredAreaForAllNodes = viewerPanel.getNodeCount() * GNode.getDiameter() * GNode.getDiameter()
				* multiplyingFactor;

		// Obtain the current area in the canvas
		double currentArea = viewerPanel.width * viewerPanel.height;

		// If the needed area is more than zero
		double neededArea = requiredAreaForAllNodes - currentArea;
		if (forceResize || neededArea >= 0) {
			// We will increase the width and height of the canvas by a value "x".
			// To find the appropriate value "x", we need to solve a second degree
			// polynomial
			// which is : x * (width + height) + x^2 - neededArea = 0.
			// We can find the value of X using the method x = (-b + sqrt(b2-4ac))/2a
			// where a = 1 b = width + height c = - neededArea

			double b = viewerPanel.width + viewerPanel.height;
			double c = -neededArea;
			int XSolution = (int) (-b + Math.sqrt(Math.pow(b, 2) - (4d * c))) / 2;

			int newWidth = (int) (viewerPanel.width + XSolution);
			int newHeight = (int) (viewerPanel.height + XSolution);
			viewerPanel.updateSize(newWidth, newHeight, false);

			return true;
		}
		return false;
	}

	/**
	 * Read graph from an input file in gSpan format
	 * 
	 * @param path the input file
	 * @return a list of input graph from the input graph database
	 * @throws IOException if error reading or writing to file
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

		// For each graph of the graph database
		while (hasNextGraph) {

			String split[] = line.split(" ");
			int gId = Integer.parseInt(split[2]);
			GGraph currentGraph = new GGraph("" + gId);

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
					int vId = Integer.parseInt(items[1]);
					int vLabel = Integer.parseInt(items[2]);
					GNode node = new GNode("" + vLabel, "" + vId);
					vMap.put(vId, node);
					currentGraph.getNodes().add(node);
				} else if (line.startsWith("e")) {
					// If it is an edge
					int v1 = Integer.parseInt(items[1]);
					int v2 = Integer.parseInt(items[2]);
					int eLabel = Integer.parseInt(items[3]);
					GNode node1 = vMap.get(v1);
					GNode node2 = vMap.get(v2);
					GEdge edge = new GEdge(node1, node2, "" + eLabel, false);
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

	}

	/**
	 * Display the currently selected graph from the graph database.
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

		// Check if the canvas is big enough and auto-enlarge it
		boolean enlarged = autoEnlargeCanvasIfNecessary(false);
		viewerPanel.autoLayout();

		// Update edge and vertex count
		fieldName.setText("" + graph.getName());
		labelNumberOf.setText("Graph " + (currentGraphIndex + 1) + " of " + graphDatabase.size());
		fieldNodeCount.setText("" + viewerPanel.getNodeCount());
		fieldEdgeCount.setText("" + viewerPanel.getEdgeCount());

		if (currentGraphIndex == 0) {
			buttonPrevious.setEnabled(false);
		} else {
			buttonPrevious.setEnabled(true);
		}

		if (currentGraphIndex == graphDatabase.size() - 1) {
			buttonNext.setEnabled(false);
		} else {
			buttonNext.setEnabled(true);
		}

		labelSupport.setVisible(hasSupportValues);
		fieldSupport.setVisible(hasSupportValues);
		if (hasSupportValues) {
			fieldSupport.setText("" + graph.getSupport());
		}

		if (SHOW_STRING_REPRESENTATION_OF_FILE && textPaneStrings != null) {
			textPaneStrings.setText(graphStringRepresentations.get(currentGraphIndex));
		}

		viewerPanel.repaint();
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
	 * Display the next graph (if there are many) and assuming there is a next one.
	 */
	protected void displayNextGraph() {
		currentGraphIndex++;
		displayCurrentGraphFromGraphDatabase();
	}

	/**
	 * Ask the user to choose the node background color.
	 */
	private void changeNodeBackgroundColor() {
		Color color = JColorChooser.showDialog(this, "Choose node background color", viewerPanel.getNodeColor());
		if (color != null) {
			viewerPanel.setNodeColor(color);
		}
	}

	/**
	 * Ask the user to choose the node text color.
	 */
	private void changeNodeTextColor() {
		Color color = JColorChooser.showDialog(this, "Choose node text color", viewerPanel.getNodeTextColor());
		if (color != null) {
			viewerPanel.setNodeTextColor(color);
		}
	}

	/**
	 * Ask the user to choose the edge color.
	 */
	protected void changeEdgeColor() {
		Color color = JColorChooser.showDialog(this, "Choose edge color", viewerPanel.getEdgeColor());
		if (color != null) {
			viewerPanel.setEdgeColor(color);
		}
	}

	/**
	 * Ask the user to choose the edge text color.
	 */
	private void changeEdgeTextColor() {
		Color color = JColorChooser.showDialog(this, "Choose edge text color", viewerPanel.getEdgeTextColor());
		if (color != null) {
			viewerPanel.setEdgeTextColor(color);
		}
	}

	/**
	 * Ask the user to choose the canvas color.
	 */
	protected void changeCanvasColor() {
		Color color = JColorChooser.showDialog(this, "Choose canvas color", viewerPanel.getCanvasColor());
		if (color != null) {
			viewerPanel.setCanvasColor(color);
		}
	}

	/**
	 * Activate the anti-aliasing or deactivate
	 * @param selected if true, activate, otherwise, deactivate
	 */
	protected void setAntiAliasing(boolean selected) {
		viewerPanel.setAntiAliasing(selected);
	}

	/**
	 * Show or hide the edge IDs.
	 * @param selected if true, show. Otherwise, hide.
	 */
	protected void showEdgeIDs(boolean selected) {
		viewerPanel.showEdgeIDs(selected);
	}

	/**
	 * Show or hide the node IDs.
	 * @param selected if true, show. Otherwise, hide.
	 */
	protected void showNodeIDs(boolean selected) {
		viewerPanel.showNodeIDs(selected);
	}

	/**
	 * Show or hide the edge labels
	 * @param selected if true, show. Otherwise, hide.
	 */
	protected void showEdgeLabels(boolean selected) {
		viewerPanel.showEdgeLabels(selected);

	}

	/**
	 * Show or hide the node labels
	 * @param selected if true, show. Otherwise, hide.
	 */
	protected void showNodeLabels(boolean selected) {
		viewerPanel.showNodeLabels(selected);

	}

	/**
	 * Ask the user to choose the node border color.
	 */
	protected void changeNodeBorderColor() {
		Color color = JColorChooser.showDialog(this, "Choose node border color", viewerPanel.getNodeBorderColor());
		if (color != null) {
			viewerPanel.setNodeBorderColor(color);
		}
	}
}