package ca.pfv.spmf.gui.visuals.heatmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.MainWindow;

/*
 * Copyright (c) 2008-2023 Philippe Fournier-Viger
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
 * A JFrame that displays a HeatMap with an improved user interface.
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class HeatMapViewer extends JFrame {
	
	/** The heatmap component */
	private HeatMap heatMap;
	
	/** Control panel on the right side */
	private JPanel controlPanel;
	
	/** Status bar at the bottom */
	private JLabel statusLabel;
	
	/** Checkboxes for display options */
	private JCheckBox showRowLabelsCheckBox;
	private JCheckBox showColumnLabelsCheckBox;
	private JCheckBox showColorScaleCheckBox;
	private JCheckBox verticalColumnLabelsCheckBox;
	
	/** Spinners for size controls */
	private JSpinner cellWidthSpinner;
	private JSpinner cellHeightSpinner;
	private JSpinner marginSpinner;
	
	/** Slider for zoom */
	private JSlider zoomSlider;
	private JLabel zoomLabel;
	
	/** Flag to prevent recursive updates */
	private boolean isUpdating = false;
	
	/** Data dimensions */
	private int numRows;
	private int numCols;

	/**
	 * Constructor
	 * 
	 * @param runAsStandaloneProgram true if running standalone
	 * @param data the heatmap data
	 * @param rowNames the row names
	 * @param columnNames the column names
	 * @param drawColumnLabels whether to draw column labels
	 * @param drawRowLabels whether to draw row labels
	 * @param drawColorScale whether to draw color scale
	 * @param drawColumnLabelsVertically whether to draw column labels vertically
	 */
	public HeatMapViewer(boolean runAsStandaloneProgram, double[][] data, String[] rowNames, 
			String[] columnNames, boolean drawColumnLabels, boolean drawRowLabels, 
			boolean drawColorScale, boolean drawColumnLabelsVertically) {
		
		super("SPMF HeatMap Viewer");
		
		// Set icon
		try {
			setIconImage(Toolkit.getDefaultToolkit().getImage(
					MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/heatmap20.png")));
		} catch (Exception e) {
			// Icon not found, continue without it
		}
		
		// Store dimensions
		this.numRows = data.length;
		this.numCols = data[0].length;
		
		// Create the heatmap
		this.heatMap = new HeatMap(data, rowNames, columnNames);
		heatMap.setDrawColumnLabels(drawColumnLabels);
		heatMap.setDrawRowLabels(drawRowLabels);
		heatMap.setDrawColorScale(drawColorScale);
		heatMap.setDrawColumnLabelsVertically(drawColumnLabelsVertically);
		
		// Set close operation
		if (runAsStandaloneProgram) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		} else {
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		}
		
		// Build the UI
		initializeUI();
		
		// Set size and position
		setPreferredSize(new Dimension(1000, 700));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		
		// Update status
		updateStatus();
	}
	
	/**
	 * Initialize the user interface
	 */
	private void initializeUI() {
		setLayout(new BorderLayout(5, 5));
		
		// Create menu bar
		setJMenuBar(createMenuBar());
		
		// Create toolbar
		add(createToolBar(), BorderLayout.NORTH);
		
		// Add heatmap to center
		add(heatMap, BorderLayout.CENTER);
		
		// Create and add control panel on the right
		controlPanel = createControlPanel();
		add(controlPanel, BorderLayout.EAST);
		
		// Create status bar
		JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
		statusLabel = new JLabel(" ");
		statusPanel.add(statusLabel);
		add(statusPanel, BorderLayout.SOUTH);
	}
	
	/**
	 * Create the menu bar
	 * @return the menu bar
	 */
	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		
		// File menu
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		
		JMenuItem exportImageItem = new JMenuItem("Export as Image...");
		exportImageItem.setMnemonic(KeyEvent.VK_I);
		exportImageItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, 
				Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		exportImageItem.addActionListener(e -> exportHeatMapAsImage());
		fileMenu.add(exportImageItem);
		
		JMenuItem exportCSVItem = new JMenuItem("Export as CSV...");
		exportCSVItem.setMnemonic(KeyEvent.VK_C);
		exportCSVItem.addActionListener(e -> exportHeatMapDataAsCSV());
		fileMenu.add(exportCSVItem);
		
		fileMenu.addSeparator();
		
		JMenuItem closeItem = new JMenuItem("Close");
		closeItem.setMnemonic(KeyEvent.VK_X);
		closeItem.addActionListener(e -> dispose());
		fileMenu.add(closeItem);
		
		menuBar.add(fileMenu);
		
		// View menu
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);
		
		JMenuItem resetZoomItem = new JMenuItem("Reset Zoom");
		resetZoomItem.setMnemonic(KeyEvent.VK_R);
		resetZoomItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, 
				Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		resetZoomItem.addActionListener(e -> resetZoom());
		viewMenu.add(resetZoomItem);
		
		JMenuItem fitToWindowItem = new JMenuItem("Fit to Window");
		fitToWindowItem.setMnemonic(KeyEvent.VK_F);
		fitToWindowItem.addActionListener(e -> fitToWindow());
		viewMenu.add(fitToWindowItem);
		
		viewMenu.addSeparator();
		
		JMenuItem toggleControlPanelItem = new JMenuItem("Toggle Control Panel");
		toggleControlPanelItem.setMnemonic(KeyEvent.VK_P);
		toggleControlPanelItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, 
				Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		toggleControlPanelItem.addActionListener(e -> toggleControlPanel());
		viewMenu.add(toggleControlPanelItem);
		
		menuBar.add(viewMenu);
		
		return menuBar;
	}
	
	/**
	 * Create the toolbar
	 * @return the toolbar
	 */
	private JToolBar createToolBar() {
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		
		// Export buttons
		JButton exportImageBtn = new JButton("Export Image");
		exportImageBtn.setToolTipText("Export heatmap as PNG image");
		exportImageBtn.addActionListener(e -> exportHeatMapAsImage());
		toolBar.add(exportImageBtn);
		
		JButton exportCSVBtn = new JButton("Export CSV");
		exportCSVBtn.setToolTipText("Export data as CSV file");
		exportCSVBtn.addActionListener(e -> exportHeatMapDataAsCSV());
		toolBar.add(exportCSVBtn);
		
		toolBar.addSeparator();
		
		// Zoom controls
		toolBar.add(new JLabel("Zoom: "));
		
		JButton zoomOutBtn = new JButton("-");
		zoomOutBtn.setToolTipText("Zoom out");
		zoomOutBtn.addActionListener(e -> adjustZoom(-10));
		toolBar.add(zoomOutBtn);
		
		zoomSlider = new JSlider(10, 200, 100);
		zoomSlider.setPreferredSize(new Dimension(150, 25));
		zoomSlider.setMaximumSize(new Dimension(150, 25));
		zoomSlider.setToolTipText("Adjust zoom level");
		zoomSlider.addChangeListener(e -> {
			if (!isUpdating) {
				applyZoom();
			}
		});
		toolBar.add(zoomSlider);
		
		JButton zoomInBtn = new JButton("+");
		zoomInBtn.setToolTipText("Zoom in");
		zoomInBtn.addActionListener(e -> adjustZoom(10));
		toolBar.add(zoomInBtn);
		
		zoomLabel = new JLabel("100%");
		zoomLabel.setPreferredSize(new Dimension(45, 20));
		toolBar.add(zoomLabel);
		
		toolBar.addSeparator();
		
		// Reset button
		JButton resetBtn = new JButton("Reset View");
		resetBtn.setToolTipText("Reset all view settings to defaults");
		resetBtn.addActionListener(e -> resetView());
		toolBar.add(resetBtn);
		
		return toolBar;
	}
	
	/**
	 * Create the control panel
	 * @return the control panel
	 */
	private JPanel createControlPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY),
				BorderFactory.createEmptyBorder(10, 10, 10, 10)));
		panel.setPreferredSize(new Dimension(220, 0));
		
		// Display Options Section
		JPanel displayPanel = createSectionPanel("Display Options");
		
		showRowLabelsCheckBox = new JCheckBox("Show Row Labels", heatMap.isDrawRowLabels());
		showRowLabelsCheckBox.addActionListener(e -> {
			heatMap.setDrawRowLabels(showRowLabelsCheckBox.isSelected());
			refreshHeatMap();
		});
		displayPanel.add(showRowLabelsCheckBox);
		
		showColumnLabelsCheckBox = new JCheckBox("Show Column Labels", heatMap.isDrawColumnLabels());
		showColumnLabelsCheckBox.addActionListener(e -> {
			heatMap.setDrawColumnLabels(showColumnLabelsCheckBox.isSelected());
			refreshHeatMap();
		});
		displayPanel.add(showColumnLabelsCheckBox);
		
		showColorScaleCheckBox = new JCheckBox("Show Color Scale", heatMap.isDrawColorScale());
		showColorScaleCheckBox.addActionListener(e -> {
			heatMap.setDrawColorScale(showColorScaleCheckBox.isSelected());
			refreshHeatMap();
		});
		displayPanel.add(showColorScaleCheckBox);
		
		verticalColumnLabelsCheckBox = new JCheckBox("Vertical Column Labels", 
				heatMap.isDrawColumnLabelsVertically());
		verticalColumnLabelsCheckBox.addActionListener(e -> {
			heatMap.setDrawColumnLabelsVertically(verticalColumnLabelsCheckBox.isSelected());
			refreshHeatMap();
		});
		displayPanel.add(verticalColumnLabelsCheckBox);
		
		panel.add(displayPanel);
		panel.add(Box.createVerticalStrut(15));
		
		// Cell Size Section
		JPanel cellSizePanel = createSectionPanel("Cell Size");
		
		JPanel widthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		widthPanel.add(new JLabel("Min Width:"));
		cellWidthSpinner = new JSpinner(new SpinnerNumberModel(
				heatMap.getMinCellWidth(), 5, 200, 5));
		cellWidthSpinner.setPreferredSize(new Dimension(70, 25));
		cellWidthSpinner.addChangeListener(e -> {
			if (!isUpdating) {
				heatMap.setMinCellWidth((Integer) cellWidthSpinner.getValue());
				refreshHeatMap();
				updateZoomFromCellSize();
			}
		});
		widthPanel.add(cellWidthSpinner);
		widthPanel.add(new JLabel("px"));
		cellSizePanel.add(widthPanel);
		
		JPanel heightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		heightPanel.add(new JLabel("Min Height:"));
		cellHeightSpinner = new JSpinner(new SpinnerNumberModel(
				heatMap.getMinCellHeight(), 5, 200, 5));
		cellHeightSpinner.setPreferredSize(new Dimension(70, 25));
		cellHeightSpinner.addChangeListener(e -> {
			if (!isUpdating) {
				heatMap.setMinCellHeight((Integer) cellHeightSpinner.getValue());
				refreshHeatMap();
				updateZoomFromCellSize();
			}
		});
		heightPanel.add(cellHeightSpinner);
		heightPanel.add(new JLabel("px"));
		cellSizePanel.add(heightPanel);
		
		JPanel marginPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		marginPanel.add(new JLabel("Margin:"));
		marginSpinner = new JSpinner(new SpinnerNumberModel(
				heatMap.getMargin(), 0, 100, 5));
		marginSpinner.setPreferredSize(new Dimension(70, 25));
		marginSpinner.addChangeListener(e -> {
			if (!isUpdating) {
				heatMap.setMargin((Integer) marginSpinner.getValue());
				refreshHeatMap();
			}
		});
		marginPanel.add(marginSpinner);
		marginPanel.add(new JLabel("px"));
		cellSizePanel.add(marginPanel);
		
		panel.add(cellSizePanel);
		panel.add(Box.createVerticalStrut(15));
		
		// Quick Actions Section
		JPanel actionsPanel = createSectionPanel("Quick Actions");
		
		JButton fitCellsBtn = new JButton("Auto-fit Cell Size");
		fitCellsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		fitCellsBtn.setToolTipText("Automatically adjust cell size to fit window");
		fitCellsBtn.addActionListener(e -> fitToWindow());
		actionsPanel.add(fitCellsBtn);
		actionsPanel.add(Box.createVerticalStrut(5));
		
		JButton resetDefaultsBtn = new JButton("Reset to Defaults");
		resetDefaultsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		resetDefaultsBtn.setToolTipText("Reset all settings to default values");
		resetDefaultsBtn.addActionListener(e -> resetView());
		actionsPanel.add(resetDefaultsBtn);
		
		panel.add(actionsPanel);
		panel.add(Box.createVerticalStrut(15));
		
		// Info Section
		JPanel infoPanel = createSectionPanel("Data Info");
		
		JLabel rowsLabel = new JLabel("Rows: " + numRows);
		rowsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		infoPanel.add(rowsLabel);
		
		JLabel colsLabel = new JLabel("Columns: " + numCols);
		colsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		infoPanel.add(colsLabel);
		
		JLabel cellsLabel = new JLabel("Total Cells: " + (numRows * numCols));
		cellsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		infoPanel.add(cellsLabel);
		
		panel.add(infoPanel);
		
		// Push everything to the top
		panel.add(Box.createVerticalGlue());
		
		return panel;
	}
	
	/**
	 * Create a section panel with a title
	 * @param title the section title
	 * @return the panel
	 */
	private JPanel createSectionPanel(String title) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(title),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 200));
		return panel;
	}
	
	/**
	 * Refresh the heatmap display - forces complete redraw with new size
	 */
	private void refreshHeatMap() {
		heatMap.refreshDisplay();
		updateStatus();
	}
	
	/**
	 * Update the status bar
	 */
	private void updateStatus() {
		StringBuilder sb = new StringBuilder();
		sb.append("Size: ").append(numRows).append(" x ").append(numCols);
		sb.append(" | Cell: ").append(heatMap.getMinCellWidth());
		sb.append(" x ").append(heatMap.getMinCellHeight()).append(" px");
		sb.append(" | Zoom: ").append(zoomSlider.getValue()).append("%");
		statusLabel.setText(sb.toString());
	}
	
	/**
	 * Update zoom slider based on current cell size
	 */
	private void updateZoomFromCellSize() {
		isUpdating = true;
		int cellSize = Math.max(heatMap.getMinCellWidth(), heatMap.getMinCellHeight());
		int zoomPercent = (int) (cellSize * 100.0 / 20);
		zoomPercent = Math.max(10, Math.min(200, zoomPercent));
		zoomSlider.setValue(zoomPercent);
		zoomLabel.setText(zoomPercent + "%");
		isUpdating = false;
		updateStatus();
	}
	
	/**
	 * Adjust zoom level
	 * @param delta the amount to adjust
	 */
	private void adjustZoom(int delta) {
		int newValue = zoomSlider.getValue() + delta;
		newValue = Math.max(zoomSlider.getMinimum(), Math.min(zoomSlider.getMaximum(), newValue));
		zoomSlider.setValue(newValue);
	}
	
	/**
	 * Apply the current zoom level
	 */
	private void applyZoom() {
		isUpdating = true;
		
		int zoom = zoomSlider.getValue();
		zoomLabel.setText(zoom + "%");
		
		// Calculate new cell sizes based on zoom
		int baseSize = 20;
		int newSize = (int) (baseSize * zoom / 100.0);
		newSize = Math.max(5, newSize);
		
		heatMap.setMinCellWidth(newSize);
		heatMap.setMinCellHeight(newSize);
		
		// Update spinners
		cellWidthSpinner.setValue(newSize);
		cellHeightSpinner.setValue(newSize);
		
		isUpdating = false;
		
		refreshHeatMap();
	}
	
	/**
	 * Reset zoom to 100%
	 */
	private void resetZoom() {
		zoomSlider.setValue(100);
	}
	
	/**
	 * Fit the heatmap to the window
	 */
	private void fitToWindow() {
		// Calculate available space
		int availableWidth = heatMap.getWidth() - 150; // Account for labels and color scale
		int availableHeight = heatMap.getHeight() - 100; // Account for labels
		
		if (availableWidth <= 0 || availableHeight <= 0) {
			return;
		}
		
		// Calculate cell sizes to fit
		int cellWidth = Math.max(5, availableWidth / numCols);
		int cellHeight = Math.max(5, availableHeight / numRows);
		
		// Use the smaller dimension to maintain square cells
		int cellSize = Math.min(cellWidth, cellHeight);
		cellSize = Math.max(5, Math.min(200, cellSize));
		
		isUpdating = true;
		
		heatMap.setMinCellWidth(cellSize);
		heatMap.setMinCellHeight(cellSize);
		
		// Update controls
		cellWidthSpinner.setValue(cellSize);
		cellHeightSpinner.setValue(cellSize);
		
		// Update zoom slider to reflect the change
		int zoomPercent = (int) (cellSize * 100.0 / 20);
		zoomPercent = Math.max(10, Math.min(200, zoomPercent));
		zoomSlider.setValue(zoomPercent);
		zoomLabel.setText(zoomPercent + "%");
		
		isUpdating = false;
		
		refreshHeatMap();
	}
	
	/**
	 * Reset all view settings to defaults
	 */
	private void resetView() {
		isUpdating = true;
		
		// Reset display options
		showRowLabelsCheckBox.setSelected(true);
		showColumnLabelsCheckBox.setSelected(true);
		showColorScaleCheckBox.setSelected(true);
		verticalColumnLabelsCheckBox.setSelected(true);
		
		heatMap.setDrawRowLabels(true);
		heatMap.setDrawColumnLabels(true);
		heatMap.setDrawColorScale(true);
		heatMap.setDrawColumnLabelsVertically(true);
		
		// Reset sizes
		cellWidthSpinner.setValue(20);
		cellHeightSpinner.setValue(20);
		marginSpinner.setValue(10);
		
		heatMap.setMinCellWidth(20);
		heatMap.setMinCellHeight(20);
		heatMap.setMargin(10);
		
		// Reset zoom
		zoomSlider.setValue(100);
		zoomLabel.setText("100%");
		
		isUpdating = false;
		
		refreshHeatMap();
	}
	
	/**
	 * Toggle the control panel visibility
	 */
	private void toggleControlPanel() {
		controlPanel.setVisible(!controlPanel.isVisible());
		revalidate();
	}
	
	/**
	 * Export the heatmap as an image
	 */
	private void exportHeatMapAsImage() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export HeatMap as Image");
		fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
		
		int userSelection = fileChooser.showSaveDialog(this);
		
		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fileChooser.getSelectedFile();
			
			// Ensure the file has the correct extension
			if (!fileToSave.getName().toLowerCase().endsWith(".png")) {
				fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".png");
			}
			
			// Check if file exists
			if (fileToSave.exists()) {
				int result = JOptionPane.showConfirmDialog(this,
						"File already exists. Overwrite?",
						"Confirm Overwrite",
						JOptionPane.YES_NO_OPTION);
				if (result != JOptionPane.YES_OPTION) {
					return;
				}
			}
			
			saveComponentAsImage(heatMap, fileToSave);
		}
	}
	
	/**
	 * Export the heatmap data as CSV
	 */
	private void exportHeatMapDataAsCSV() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export HeatMap Data as CSV");
		fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
		
		int userSelection = fileChooser.showSaveDialog(this);
		
		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fileChooser.getSelectedFile();
			
			// Ensure the file has the correct extension
			if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
				fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".csv");
			}
			
			// Check if file exists
			if (fileToSave.exists()) {
				int result = JOptionPane.showConfirmDialog(this,
						"File already exists. Overwrite?",
						"Confirm Overwrite",
						JOptionPane.YES_NO_OPTION);
				if (result != JOptionPane.YES_OPTION) {
					return;
				}
			}
			
			saveDataAsCSV(heatMap.getData(), heatMap.getRowNames(), heatMap.getColumnNames(), fileToSave);
		}
	}
	
	/**
	 * Save data as CSV file
	 */
	private void saveDataAsCSV(double[][] data, String[] rowNames, String[] columnNames, File file) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			// Write column names header row
			writer.write(",");
			for (int i = 0; i < columnNames.length; i++) {
				// Escape commas and quotes in column names
				String name = escapeCSV(columnNames[i]);
				writer.write(name);
				if (i < columnNames.length - 1) {
					writer.write(",");
				}
			}
			writer.newLine();
			
			// Write data with row names
			for (int i = 0; i < data.length; i++) {
				writer.write(escapeCSV(rowNames[i]) + ",");
				for (int j = 0; j < data[i].length; j++) {
					writer.write(String.valueOf(data[i][j]));
					if (j < data[i].length - 1) {
						writer.write(",");
					}
				}
				writer.newLine();
			}
			
			JOptionPane.showMessageDialog(this,
					"Data exported successfully to:\n" + file.getAbsolutePath(),
					"Export Successful",
					JOptionPane.INFORMATION_MESSAGE);
			
		} catch (IOException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Error saving data: " + ex.getMessage(),
					"Export Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}
	
	/**
	 * Escape a string for CSV format
	 */
	private String escapeCSV(String value) {
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
	
	/**
	 * Save a component as an image file
	 */
	private void saveComponentAsImage(Component comp, File file) {
		// Create image with proper size
		int width = Math.max(comp.getWidth(), 100);
		int height = Math.max(comp.getHeight(), 100);
		
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		
		// Set rendering hints for better quality
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		
		// Paint the component
		comp.paint(g2d);
		g2d.dispose();
		
		try {
			ImageIO.write(image, "png", file);
			JOptionPane.showMessageDialog(this,
					"Image exported successfully to:\n" + file.getAbsolutePath(),
					"Export Successful",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Error saving image: " + ex.getMessage(),
					"Export Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}
}