package ca.pfv.spmf.gui.visuals.histograms;

/*
 * Copyright (c) 2008-2024 Philippe Fournier-Viger
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.ColorScheme;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;

/**
 * A window that wraps a {@link HistogramDistributionPanel} inside a scrollable
 * area and exposes controls for zoom / bar width, sort order, colour scheme,
 * display toggles and export (CSV / PNG / clipboard).
 * <p>
 * Keyboard shortcuts: Ctrl+= zoom in, Ctrl+- zoom out, Ctrl+0 fit to width,
 * Ctrl+S save PNG, Ctrl+C copy image. Ctrl + mouse wheel zooms at the cursor.
 *
 * @author Philippe Fournier-Viger
 */
public class HistogramDistributionWindow extends JFrame {

	private static final long serialVersionUID = 4751136799631193209L;

	private static final int DEFAULT_WIDTH = 900;
	private static final int DEFAULT_HEIGHT = 520;
	private static final String ICON_PATH_HISTOGRAM = "/ca/pfv/spmf/gui/icons/histogram.png";
	private static final String ICON_PATH_SAVE = "/ca/pfv/spmf/gui/icons/save.gif";
	private static final Color TOOLBAR_BG = new Color(248, 249, 251);
	private static final Color LINE_COLOR = new Color(210, 214, 220);

	private final HistogramDistributionPanel histogram;
	private final JScrollPane scrollPane;
	private final JLabel statusLabel = new JLabel(" ");
	private JSpinner widthSpinner;

	// ---------------------------------------------------------------
	// Constructors (signatures unchanged)
	// ---------------------------------------------------------------

	public HistogramDistributionWindow(boolean runAsStandalone,
			int[] yValues, int[] xLabels, String title,
			boolean showBarLabels, boolean showBarValues,
			String xAxisName, String yAxisName,
			Map<Integer, String> mapItemToString, Order order) {
		histogram = new HistogramDistributionPanel(yValues, xLabels, title,
				showBarLabels, showBarValues, xAxisName, yAxisName, mapItemToString, order);
		scrollPane = createScrollPane();
		initializeWindow(runAsStandalone, order);
	}

	public HistogramDistributionWindow(boolean runAsStandalone,
			Vector<List<Object>> data, int index, String title,
			String xAxisName, String yAxisName, Order order) {
		super();
		histogram = new HistogramDistributionPanel(data, DEFAULT_WIDTH - 20,
				DEFAULT_HEIGHT, index, title, true, true, xAxisName, yAxisName, order);
		scrollPane = createScrollPane();
		initializeWindow(runAsStandalone, order);
	}

	/** @return the embedded histogram panel (for programmatic configuration). */
	public HistogramDistributionPanel getHistogramPanel() {
		return histogram;
	}

	// ---------------------------------------------------------------
	// Window initialisation
	// ---------------------------------------------------------------

	private JScrollPane createScrollPane() {
		JScrollPane sp = new JScrollPane(histogram,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getViewport().setBackground(Color.WHITE);
		sp.setWheelScrollingEnabled(true);
		return sp;
	}

	private void initializeWindow(boolean runAsStandalone, Order order) {
		URL iconUrl = MainWindow.class.getResource(ICON_PATH_HISTOGRAM);
		if (iconUrl != null) {
			setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
		}
		setTitle("SPMF Histogram Viewer " + Main.SPMF_VERSION);
		setDefaultCloseOperation(runAsStandalone ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);

		setLayout(new BorderLayout());
		getContentPane().add(buildToolbar(order), BorderLayout.NORTH);
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

		installKeyBindings();

		histogram.addBarHoverListener((idx, label, value) -> {
			if (idx < 0) {
				statusLabel.setText(" ");
			} else {
				statusLabel.setText("  " + label + "  \u2192  " + value);
			}
		});
		histogram.addPropertyChangeListener("barWidth", ev -> {
			if (widthSpinner != null && !widthSpinner.getValue().equals(ev.getNewValue())) {
				widthSpinner.setValue(ev.getNewValue());
			}
		});

		setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
		setMinimumSize(new Dimension(520, 340));
		setLocationRelativeTo(null);
		setVisible(true);
	}

	// ---------------------------------------------------------------
	// Toolbar
	// ---------------------------------------------------------------

	private JPanel buildToolbar(Order order) {
		JPanel toolbar = new JPanel();
		toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
		toolbar.setBackground(TOOLBAR_BG);
		toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE_COLOR));

		// ---- Row 1: view controls ------------------------------------
		JPanel row1 = newRow();

		row1.add(new JLabel("Sort by:"));
		JComboBox<Order> sortBox = new JComboBox<>(Order.values());
		sortBox.setRenderer(new LabelRenderer());
		sortBox.setSelectedItem(order);
		sortBox.addActionListener(e -> {
			Order selected = (Order) sortBox.getSelectedItem();
			if (selected != null) {
				histogram.setSortOrder(selected);
			}
		});
		row1.add(sortBox);

		row1.add(createSeparator());

		row1.add(new JLabel("Colours:"));
		JComboBox<ColorScheme> colorBox = new JComboBox<>(ColorScheme.values());
		colorBox.setRenderer(new LabelRenderer());
		colorBox.setSelectedItem(histogram.getColorScheme());
		colorBox.addActionListener(e ->
				histogram.setColorScheme((ColorScheme) colorBox.getSelectedItem()));
		row1.add(colorBox);

		row1.add(createSeparator());

		row1.add(new JLabel("Bar width:"));
		widthSpinner = new JSpinner(new SpinnerNumberModel(histogram.getBarWidth(), 1, 400, 1));
		widthSpinner.setPreferredSize(new Dimension(64, widthSpinner.getPreferredSize().height));
		widthSpinner.addChangeListener(e -> histogram.setBarWidth((Integer) widthSpinner.getValue()));
		row1.add(widthSpinner);

		row1.add(createSmallButton("\u2212", "Zoom out (Ctrl + -)", e -> histogram.zoomOut()));
		row1.add(createSmallButton("+", "Zoom in (Ctrl + =)", e -> histogram.zoomIn()));
		row1.add(createSmallButton("Fit", "Fit all bars in the window (Ctrl + 0)",
				e -> histogram.fitToWidth()));

		// ---- Row 2: toggles + export ---------------------------------
		JPanel row2 = newRow();

		row2.add(createCheck("Labels", "Show a label under each bar",
				histogram.isShowBarLabels(), v -> histogram.setShowBarLabels(v)));
		row2.add(createCheck("Values", "Show the value above each bar",
				histogram.isShowBarValues(), v -> histogram.setShowBarValues(v)));
		row2.add(createCheck("Percent", "Show values as a percentage of the total",
				histogram.isShowPercent(), v -> histogram.setShowPercent(v)));
		row2.add(createCheck("Mean line", "Draw a dashed line at the mean value",
				histogram.isShowMeanLine(), v -> histogram.setShowMeanLine(v)));
		row2.add(createCheck("Log scale", "Use a logarithmic Y axis",
				histogram.isLogarithmicScale(), v -> histogram.setLogarithmicScale(v)));

		row2.add(createSeparator());

		row2.add(createSaveButton("Save as CSV", e -> histogram.exportAsCSV()));
		row2.add(createSaveButton("Save as PNG", e -> histogram.exportAsImage()));
		JButton copy = new JButton("Copy image");
		copy.setToolTipText("Copy the histogram to the clipboard (Ctrl + C)");
		copy.addActionListener(e -> histogram.copyImageToClipboard());
		row2.add(copy);

		toolbar.add(row1);
		toolbar.add(row2);
		return toolbar;
	}

	private JPanel newRow() {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
		return row;
	}

	private JPanel buildStatusBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(TOOLBAR_BG);
		bar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, LINE_COLOR),
				BorderFactory.createEmptyBorder(3, 6, 3, 6)));

		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
		statusLabel.setForeground(new Color(60, 64, 72));
		bar.add(statusLabel, BorderLayout.WEST);

		JLabel hint = new JLabel("Ctrl + wheel: zoom   \u00B7   Ctrl+0: fit   \u00B7   "
				+ "Ctrl+S: save PNG   \u00B7   Ctrl+C: copy");
		hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
		hint.setForeground(new Color(130, 135, 145));
		bar.add(hint, BorderLayout.EAST);
		return bar;
	}

	private JSeparator createSeparator() {
		JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
		sep.setPreferredSize(new Dimension(1, 22));
		return sep;
	}

	private JButton createSmallButton(String text, String tooltip, ActionListener l) {
		JButton b = new JButton(text);
		b.setToolTipText(tooltip);
		b.setMargin(new java.awt.Insets(2, 8, 2, 8));
		b.setFocusable(false);
		b.addActionListener(l);
		return b;
	}

	private JCheckBox createCheck(String text, String tooltip, boolean selected,
			java.util.function.Consumer<Boolean> onChange) {
		JCheckBox cb = new JCheckBox(text, selected);
		cb.setToolTipText(tooltip);
		cb.setOpaque(false);
		cb.setFocusable(false);
		cb.addActionListener(e -> onChange.accept(cb.isSelected()));
		return cb;
	}

	private JButton createSaveButton(String text, ActionListener listener) {
		JButton button = new JButton(text);
		URL iconUrl = MainWindow.class.getResource(ICON_PATH_SAVE);
		if (iconUrl != null) {
			button.setIcon(new ImageIcon(iconUrl));
		}
		button.addActionListener(listener);
		return button;
	}

	/** Renders {@link Order} / {@link ColorScheme} using their human-readable label. */
	private static final class LabelRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus) {
			Object shown = value;
			if (value instanceof Order) shown = ((Order) value).getLabel();
			else if (value instanceof ColorScheme) shown = ((ColorScheme) value).getLabel();
			return super.getListCellRendererComponent(list, shown, index, isSelected, cellHasFocus);
		}
	}

	// ---------------------------------------------------------------
	// Keyboard shortcuts
	// ---------------------------------------------------------------

	private void installKeyBindings() {
		JComponent root = getRootPane();
		InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

		bind(im, root, "zoomIn", () -> histogram.zoomIn(),
				KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl),
				KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrl),
				KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrl),
				KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl | InputEvent.SHIFT_DOWN_MASK));
		bind(im, root, "zoomOut", () -> histogram.zoomOut(),
				KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrl),
				KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrl));
		bind(im, root, "fit", () -> histogram.fitToWidth(),
				KeyStroke.getKeyStroke(KeyEvent.VK_0, ctrl),
				KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, ctrl));
		bind(im, root, "savePng", () -> histogram.exportAsImage(),
				KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrl));
		bind(im, root, "copyImage", () -> histogram.copyImageToClipboard(),
				KeyStroke.getKeyStroke(KeyEvent.VK_C, ctrl));
	}

	private static void bind(InputMap im, JComponent c, String name, Runnable action,
			KeyStroke... keys) {
		for (KeyStroke k : keys) {
			im.put(k, name);
		}
		c.getActionMap().put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	// ---------------------------------------------------------------
	// Demo
	// ---------------------------------------------------------------

	/** Small demo with synthetic data (not used by SPMF itself). */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			int n = 60;
			int[] x = new int[n];
			int[] y = new int[n];
			Random rnd = new Random(1);
			for (int i = 0; i < n; i++) {
				x[i] = i + 1;
				y[i] = (int) Math.round(1000 * Math.exp(-Math.pow((i - 25) / 12.0, 2)))
						+ rnd.nextInt(40);
			}
			new HistogramDistributionWindow(true, y, x, "Item support distribution",
					true, true, "Item", "Support", null, Order.ASCENDING_X);
		});
	}
}