package ca.pfv.spmf.gui.visuals.heatmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
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
 * A JFrame that displays a {@link HeatMap} with controls for zooming, display
 * options and export.
 * <p>
 * The viewer never stores view state itself: every action simply calls a
 * setter on the {@link HeatMap}, and the heat map's property change events
 * bring the controls back in sync. This keeps the spinners, slider, status bar
 * and Ctrl+wheel zoom consistent with a single code path.
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class HeatMapViewer extends JFrame {

	/** Cell size corresponding to 100 % zoom */
	private static final int BASE_CELL_SIZE = HeatMap.DEFAULT_MIN_CELL_SIZE;
	private static final int ZOOM_MIN = 10;
	private static final int ZOOM_MAX = 400;
	private static final int ZOOM_STEP = 10;
	private static final int CELL_MIN = 1;
	private static final int CELL_MAX = HeatMap.MAX_CELL_SIZE;
	private static final int MARGIN_MAX = 100;
	private static final int FONT_MIN = 6;
	private static final int FONT_MAX = 36;

	private final HeatMap heatMap;
	private final ViewSettings initialSettings;

	private JPanel controlPanel;
	private JCheckBoxMenuItem controlPanelMenuItem;
	private JLabel statusLabel;

	private JCheckBox showRowLabelsCheckBox;
	private JCheckBox showColumnLabelsCheckBox;
	private JCheckBox showColorScaleCheckBox;
	private JCheckBox verticalColumnLabelsCheckBox;

	private JSpinner cellWidthSpinner;
	private JSpinner cellHeightSpinner;
	private JSpinner marginSpinner;
	private JSpinner fontSizeSpinner;

	private JSlider zoomSlider;
	private JLabel zoomLabel;

	/** True while controls are being updated from the model, to avoid feedback */
	private boolean updatingControls;

	/** Directory of the last export, reused by the file chooser */
	private File lastDirectory;

	/**
	 * Constructor
	 *
	 * @param runAsStandaloneProgram    true if running standalone (closing exits the JVM)
	 * @param data                      the heatmap data
	 * @param rowNames                  the row names
	 * @param columnNames               the column names
	 * @param drawColumnLabels          whether to draw column labels
	 * @param drawRowLabels             whether to draw row labels
	 * @param drawColorScale            whether to draw color scale
	 * @param drawColumnLabelsVertically whether to draw column labels vertically
	 */
	public HeatMapViewer(boolean runAsStandaloneProgram, double[][] data, String[] rowNames,
			String[] columnNames, boolean drawColumnLabels, boolean drawRowLabels,
			boolean drawColorScale, boolean drawColumnLabelsVertically) {
		super("SPMF HeatMap Viewer");

		try {
			setIconImage(Toolkit.getDefaultToolkit()
					.getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/heatmap20.png")));
		} catch (Exception e) {
			// Icon not found: continue without it
		}

		heatMap = new HeatMap(data, rowNames, columnNames);
		heatMap.setDrawColumnLabels(drawColumnLabels);
		heatMap.setDrawRowLabels(drawRowLabels);
		heatMap.setDrawColorScale(drawColorScale);
		heatMap.setDrawColumnLabelsVertically(drawColumnLabelsVertically);
		initialSettings = ViewSettings.capture(heatMap);

		setDefaultCloseOperation(runAsStandaloneProgram ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);

		initializeUI();
		heatMap.addPropertyChangeListener(evt -> {
			String name = evt.getPropertyName();
			if (HeatMap.PROP_CELL_SIZE.equals(name) || HeatMap.PROP_MARGIN.equals(name)
					|| HeatMap.PROP_LABEL_FONT.equals(name) || HeatMap.PROP_DISPLAY_OPTIONS.equals(name)) {
				syncControls();
			}
		});
		syncControls();

		setPreferredSize(new Dimension(1000, 700));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}

	// =====================================================================
	// UI construction
	// =====================================================================

	private void initializeUI() {
		setLayout(new BorderLayout(5, 5));
		setJMenuBar(createMenuBar());
		add(createToolBar(), BorderLayout.NORTH);
		add(heatMap, BorderLayout.CENTER);

		controlPanel = createControlPanel();
		add(controlPanel, BorderLayout.EAST);

		JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
		statusLabel = new JLabel(" ");
		statusPanel.add(statusLabel);
		add(statusPanel, BorderLayout.SOUTH);

		// Extra zoom bindings for the numeric keypad
		int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		JComponent root = getRootPane();
		root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, mask), "zoomIn");
		root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, mask), "zoomOut");
		root.getActionMap().put("zoomIn", action(() -> adjustZoom(ZOOM_STEP)));
		root.getActionMap().put("zoomOut", action(() -> adjustZoom(-ZOOM_STEP)));
	}

	private JMenuBar createMenuBar() {
		int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		JMenuBar menuBar = new JMenuBar();

		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		fileMenu.add(menuItem("Export as Image...", KeyEvent.VK_I,
				KeyStroke.getKeyStroke(KeyEvent.VK_S, mask), this::exportHeatMapAsImage));
		fileMenu.add(menuItem("Export as CSV...", KeyEvent.VK_C, null, this::exportHeatMapDataAsCSV));
		fileMenu.addSeparator();
		fileMenu.add(menuItem("Close", KeyEvent.VK_X,
				KeyStroke.getKeyStroke(KeyEvent.VK_W, mask), this::dispose));
		menuBar.add(fileMenu);

		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);
		viewMenu.add(menuItem("Zoom In", KeyEvent.VK_I,
				KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, mask), () -> adjustZoom(ZOOM_STEP)));
		viewMenu.add(menuItem("Zoom Out", KeyEvent.VK_O,
				KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, mask), () -> adjustZoom(-ZOOM_STEP)));
		viewMenu.add(menuItem("Reset Zoom", KeyEvent.VK_R,
				KeyStroke.getKeyStroke(KeyEvent.VK_0, mask), () -> applyZoom(100)));
		viewMenu.add(menuItem("Fit to Window", KeyEvent.VK_F,
				KeyStroke.getKeyStroke(KeyEvent.VK_F, mask), heatMap::fitToViewport));
		viewMenu.addSeparator();
		controlPanelMenuItem = new JCheckBoxMenuItem("Show Control Panel", true);
		controlPanelMenuItem.setMnemonic(KeyEvent.VK_P);
		controlPanelMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, mask));
		controlPanelMenuItem.addActionListener(e -> setControlPanelVisible(controlPanelMenuItem.isSelected()));
		viewMenu.add(controlPanelMenuItem);
		viewMenu.addSeparator();
		viewMenu.add(menuItem("Reset View", KeyEvent.VK_D, null, this::resetView));
		menuBar.add(viewMenu);

		return menuBar;
	}

	private JToolBar createToolBar() {
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);

		toolBar.add(button("Export Image", "Export the whole heatmap as a PNG image", this::exportHeatMapAsImage));
		toolBar.add(button("Export CSV", "Export the data as a CSV file", this::exportHeatMapDataAsCSV));
		toolBar.addSeparator();

		toolBar.add(new JLabel("Zoom: "));
		toolBar.add(button("-", "Zoom out (Ctrl+-, or Ctrl+wheel)", () -> adjustZoom(-ZOOM_STEP)));
		zoomSlider = new JSlider(ZOOM_MIN, ZOOM_MAX, 100);
		zoomSlider.setPreferredSize(new Dimension(150, 25));
		zoomSlider.setMaximumSize(new Dimension(150, 25));
		zoomSlider.setToolTipText("Adjust zoom level");
		zoomSlider.addChangeListener(e -> {
			if (!updatingControls) {
				applyZoom(zoomSlider.getValue());
			}
		});
		toolBar.add(zoomSlider);
		toolBar.add(button("+", "Zoom in (Ctrl++, or Ctrl+wheel)", () -> adjustZoom(ZOOM_STEP)));
		zoomLabel = new JLabel("100%");
		zoomLabel.setPreferredSize(new Dimension(50, 20));
		toolBar.add(zoomLabel);
		toolBar.addSeparator();

		toolBar.add(button("Fit to Window", "Choose a cell size so the whole heatmap is visible", heatMap::fitToViewport));
		toolBar.add(button("Reset View", "Reset all view settings to their initial values", this::resetView));
		return toolBar;
	}

	private JPanel createControlPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY),
				BorderFactory.createEmptyBorder(10, 10, 10, 10)));
		panel.setPreferredSize(new Dimension(230, 0));

		// --- Display options
		JPanel displayPanel = createSectionPanel("Display Options");
		showRowLabelsCheckBox = checkBox("Show Row Labels", heatMap::setDrawRowLabels);
		showColumnLabelsCheckBox = checkBox("Show Column Labels", heatMap::setDrawColumnLabels);
		verticalColumnLabelsCheckBox = checkBox("Vertical Column Labels", heatMap::setDrawColumnLabelsVertically);
		showColorScaleCheckBox = checkBox("Show Color Scale", heatMap::setDrawColorScale);
		displayPanel.add(showRowLabelsCheckBox);
		displayPanel.add(showColumnLabelsCheckBox);
		displayPanel.add(verticalColumnLabelsCheckBox);
		displayPanel.add(showColorScaleCheckBox);
		panel.add(finishSection(displayPanel));
		panel.add(Box.createVerticalStrut(15));

		// --- Sizes
		JPanel sizePanel = createSectionPanel("Size");
		cellWidthSpinner = spinner(CELL_MIN, CELL_MAX, 1, heatMap::setMinCellWidth);
		cellHeightSpinner = spinner(CELL_MIN, CELL_MAX, 1, heatMap::setMinCellHeight);
		marginSpinner = spinner(0, MARGIN_MAX, 1, heatMap::setMargin);
		fontSizeSpinner = spinner(FONT_MIN, FONT_MAX, 1, heatMap::setLabelFontSize);
		sizePanel.add(labeledRow("Cell width:", cellWidthSpinner, "px"));
		sizePanel.add(labeledRow("Cell height:", cellHeightSpinner, "px"));
		sizePanel.add(labeledRow("Margin:", marginSpinner, "px"));
		sizePanel.add(labeledRow("Label font:", fontSizeSpinner, "pt"));
		panel.add(finishSection(sizePanel));
		panel.add(Box.createVerticalStrut(15));

		// --- Actions
		JPanel actionsPanel = createSectionPanel("Quick Actions");
		actionsPanel.add(leftAligned(button("Fit to Window",
				"Choose a cell size so the whole heatmap is visible", heatMap::fitToViewport)));
		actionsPanel.add(Box.createVerticalStrut(5));
		actionsPanel.add(leftAligned(button("Reset View",
				"Reset all view settings to their initial values", this::resetView)));
		panel.add(finishSection(actionsPanel));
		panel.add(Box.createVerticalStrut(15));

		// --- Info
		JPanel infoPanel = createSectionPanel("Data Info");
		infoPanel.add(leftAligned(new JLabel("Rows: " + heatMap.getRowCount())));
		infoPanel.add(leftAligned(new JLabel("Columns: " + heatMap.getColumnCount())));
		infoPanel.add(leftAligned(new JLabel("Cells: " + ((long) heatMap.getRowCount() * heatMap.getColumnCount()))));
		infoPanel.add(leftAligned(new JLabel("Min: " + heatMap.getMinValue())));
		infoPanel.add(leftAligned(new JLabel("Max: " + heatMap.getMaxValue())));
		panel.add(finishSection(infoPanel));

		panel.add(Box.createVerticalGlue());
		return panel;
	}

	// --- small factory helpers -------------------------------------------

	private JPanel createSectionPanel(String title) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(title),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	/** Freeze the section height so BoxLayout does not stretch it. */
	private static JPanel finishSection(JPanel panel) {
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	private static <T extends JComponent> T leftAligned(T component) {
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	private JCheckBox checkBox(String text, Consumer<Boolean> onChange) {
		JCheckBox box = new JCheckBox(text);
		box.addActionListener(e -> {
			if (!updatingControls) {
				onChange.accept(box.isSelected());
			}
		});
		return leftAligned(box);
	}

	private JSpinner spinner(int min, int max, int step, IntConsumer onChange) {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
		spinner.setPreferredSize(new Dimension(65, 25));
		spinner.addChangeListener(e -> {
			if (!updatingControls) {
				onChange.accept((Integer) spinner.getValue());
			}
		});
		return spinner;
	}

	private static JPanel labeledRow(String label, JComponent component, String unit) {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		JLabel jLabel = new JLabel(label);
		jLabel.setPreferredSize(new Dimension(75, 20));
		row.add(jLabel);
		row.add(component);
		row.add(new JLabel(unit));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return leftAligned(row);
	}

	private static JButton button(String text, String tooltip, Runnable action) {
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.addActionListener(e -> action.run());
		return button;
	}

	private static JMenuItem menuItem(String text, int mnemonic, KeyStroke accelerator, Runnable action) {
		JMenuItem item = new JMenuItem(text);
		item.setMnemonic(mnemonic);
		if (accelerator != null) {
			item.setAccelerator(accelerator);
		}
		item.addActionListener(e -> action.run());
		return item;
	}

	private static javax.swing.Action action(Runnable runnable) {
		return new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				runnable.run();
			}
		};
	}

	// =====================================================================
	// Model -> controls synchronisation
	// =====================================================================

	/** Bring every control in line with the current state of the heat map. */
	private void syncControls() {
		updatingControls = true;
		try {
			showRowLabelsCheckBox.setSelected(heatMap.isDrawRowLabels());
			showColumnLabelsCheckBox.setSelected(heatMap.isDrawColumnLabels());
			showColorScaleCheckBox.setSelected(heatMap.isDrawColorScale());
			verticalColumnLabelsCheckBox.setSelected(heatMap.isDrawColumnLabelsVertically());

			cellWidthSpinner.setValue(heatMap.getMinCellWidth());
			cellHeightSpinner.setValue(heatMap.getMinCellHeight());
			marginSpinner.setValue(heatMap.getMargin());
			fontSizeSpinner.setValue(clamp(heatMap.getLabelFont().getSize(), FONT_MIN, FONT_MAX));

			int cellSize = Math.max(heatMap.getMinCellWidth(), heatMap.getMinCellHeight());
			int zoom = zoomForCellSize(cellSize);
			// Only move the slider when it does not already represent this size;
			// avoids jitter from rounding while the user drags it.
			if (cellSizeForZoom(zoomSlider.getValue()) != cellSize) {
				zoomSlider.setValue(zoom);
			}
			zoomLabel.setText(zoom + "%");
		} finally {
			updatingControls = false;
		}
		updateStatus();
	}

	private void updateStatus() {
		statusLabel.setText(String.format(Locale.ROOT,
				"Size: %d \u00d7 %d  |  Cell: %d \u00d7 %d px  |  Zoom: %d%%  |  Range: %s \u2013 %s",
				heatMap.getRowCount(), heatMap.getColumnCount(),
				heatMap.getMinCellWidth(), heatMap.getMinCellHeight(),
				zoomForCellSize(Math.max(heatMap.getMinCellWidth(), heatMap.getMinCellHeight())),
				heatMap.getMinValue(), heatMap.getMaxValue()));
	}

	// =====================================================================
	// Actions
	// =====================================================================

	private static int zoomForCellSize(int cellSize) {
		return clamp((int) Math.round(cellSize * 100.0 / BASE_CELL_SIZE), ZOOM_MIN, ZOOM_MAX);
	}

	private static int cellSizeForZoom(int zoomPercent) {
		return clamp((int) Math.round(BASE_CELL_SIZE * zoomPercent / 100.0), CELL_MIN, CELL_MAX);
	}

	private void applyZoom(int zoomPercent) {
		int size = cellSizeForZoom(clamp(zoomPercent, ZOOM_MIN, ZOOM_MAX));
		heatMap.setMinCellSize(size, size);
		syncControls(); // in case the size did not change (no event fired) but the slider moved
	}

	private void adjustZoom(int delta) {
		applyZoom(zoomSlider.getValue() + delta);
	}

	private void resetView() {
		initialSettings.applyTo(heatMap);
		syncControls();
	}

	private void setControlPanelVisible(boolean visible) {
		controlPanel.setVisible(visible);
		controlPanelMenuItem.setSelected(visible);
		revalidate();
	}

	// =====================================================================
	// Export
	// =====================================================================

	private void exportHeatMapAsImage() {
		File file = chooseSaveFile("Export HeatMap as Image", "PNG Images (*.png)", "png");
		if (file == null) {
			return;
		}
		try {
			BufferedImage image = heatMap.renderToImage(1.0);
			if (!ImageIO.write(image, "png", file)) {
				throw new IOException("No PNG writer available");
			}
			showExportSuccess("Image", file);
		} catch (IOException | OutOfMemoryError ex) {
			showExportError("image", ex);
		}
	}

	private void exportHeatMapDataAsCSV() {
		File file = chooseSaveFile("Export HeatMap Data as CSV", "CSV Files (*.csv)", "csv");
		if (file == null) {
			return;
		}
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			writeCSV(writer, heatMap.getData(), heatMap.getRowNames(), heatMap.getColumnNames());
			showExportSuccess("Data", file);
		} catch (IOException ex) {
			showExportError("data", ex);
		}
	}

	private static void writeCSV(BufferedWriter writer, double[][] data, String[] rowNames,
			String[] columnNames) throws IOException {
		StringBuilder line = new StringBuilder();
		// header
		line.append(',');
		for (int j = 0; j < columnNames.length; j++) {
			if (j > 0) {
				line.append(',');
			}
			line.append(escapeCSV(columnNames[j]));
		}
		writer.write(line.toString());
		writer.newLine();
		// rows
		for (int i = 0; i < data.length; i++) {
			line.setLength(0);
			line.append(escapeCSV(rowNames[i]));
			for (double v : data[i]) {
				line.append(',').append(v);
			}
			writer.write(line.toString());
			writer.newLine();
		}
	}

	private static String escapeCSV(String value) {
		if (value == null) {
			return "";
		}
		if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	/**
	 * Show a save dialog, enforce the extension and confirm overwrites.
	 *
	 * @return the chosen file, or null if the user cancelled
	 */
	private File chooseSaveFile(String title, String description, String extension) {
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle(title);
		chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		File file = chooser.getSelectedFile();
		lastDirectory = file.getParentFile();
		if (!file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
			file = new File(file.getParentFile(), file.getName() + "." + extension);
		}
		if (file.exists()) {
			int result = JOptionPane.showConfirmDialog(this,
					"The file \"" + file.getName() + "\" already exists. Overwrite?",
					"Confirm Overwrite", JOptionPane.YES_NO_OPTION);
			if (result != JOptionPane.YES_OPTION) {
				return null;
			}
		}
		return file;
	}

	private void showExportSuccess(String what, File file) {
		JOptionPane.showMessageDialog(this,
				what + " exported successfully to:\n" + file.getAbsolutePath(),
				"Export Successful", JOptionPane.INFORMATION_MESSAGE);
	}

	private void showExportError(String what, Throwable ex) {
		ex.printStackTrace();
		JOptionPane.showMessageDialog(this,
				"Error saving " + what + ": " + ex.getMessage(),
				"Export Error", JOptionPane.ERROR_MESSAGE);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// =====================================================================
	// Snapshot of view settings, used for "Reset View"
	// =====================================================================

	private static final class ViewSettings {
		private boolean rowLabels;
		private boolean columnLabels;
		private boolean colorScale;
		private boolean verticalColumnLabels;
		private int cellWidth;
		private int cellHeight;
		private int margin;
		private java.awt.Font labelFont;

		static ViewSettings capture(HeatMap heatMap) {
			ViewSettings s = new ViewSettings();
			s.rowLabels = heatMap.isDrawRowLabels();
			s.columnLabels = heatMap.isDrawColumnLabels();
			s.colorScale = heatMap.isDrawColorScale();
			s.verticalColumnLabels = heatMap.isDrawColumnLabelsVertically();
			s.cellWidth = heatMap.getMinCellWidth();
			s.cellHeight = heatMap.getMinCellHeight();
			s.margin = heatMap.getMargin();
			s.labelFont = heatMap.getLabelFont();
			return s;
		}

		void applyTo(HeatMap heatMap) {
			heatMap.setDrawRowLabels(rowLabels);
			heatMap.setDrawColumnLabels(columnLabels);
			heatMap.setDrawColorScale(colorScale);
			heatMap.setDrawColumnLabelsVertically(verticalColumnLabels);
			heatMap.setMinCellSize(cellWidth, cellHeight);
			heatMap.setMargin(margin);
			heatMap.setLabelFont(labelFont);
		}
	}
}