package ca.pfv.spmf.gui.visuals.timeline;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
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
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.preferences.PreferencesManager;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 * The TimelineViewer tool of SPMF.
 * <p>
 * Shortcuts: Ctrl + wheel zoom at the cursor, Ctrl+= / Ctrl+- zoom, Ctrl+0 fit
 * to width, Ctrl+1 automatic zoom, Ctrl+S save PNG, Ctrl+C copy image.
 *
 * @author Philippe Fournier-Viger
 */
public class TimelineViewer extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final String ICON_TIMELINE = "/ca/pfv/spmf/gui/icons/timeline20b.png";
	private static final String ICON_SAVE     = "/ca/pfv/spmf/gui/icons/save.gif";
	private static final Color  BAR_BG        = new Color(248, 249, 251);
	private static final Color  LINE_COLOR    = new Color(210, 214, 220);

	/**
	 * The slider is logarithmic: spans auto-scale / 10^DECADES .. auto-scale *
	 * 10^DECADES
	 */
	private static final double SLIDER_DECADES = 3.0;
	private static final int    SLIDER_MAX     = 1000;

	private final Timeline     timeline;
	private final JScrollPane  scrollPane;
	private final JSlider      scaleSlider   = new JSlider(0, SLIDER_MAX, SLIDER_MAX / 2);
	private final JLabel       scaleLabel    = new JLabel();
	private final JLabel       statusLabel   = new JLabel(" ");
	private final JLabel       infoLabel     = new JLabel(" ");
	private final NumberFormat nf            = NumberFormat.getNumberInstance(Locale.US);
	private       boolean      updatingSlider;

	/**
	 * The constructor
	 *
	 * @param runAsStandaloneProgram true if run as a standalone program
	 * @param events                 events to display (or null)
	 * @param intervals              intervals to display (used when events is null)
	 */
	public TimelineViewer(boolean runAsStandaloneProgram,
	                      List<EventT> events, List<IntervalT> intervals) {

		super("SPMF Timeline Viewer " + Main.SPMF_VERSION);

		URL iconUrl = MainWindow.class.getResource(ICON_TIMELINE);
		if (iconUrl != null) {
			setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
		}

		timeline = (events != null)
				? new Timeline(events, true)
				: new Timeline(true, intervals);

		setDefaultCloseOperation(
				runAsStandaloneProgram ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);

		setJMenuBar(buildMenuBar());

		scrollPane = new JScrollPane(timeline,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getViewport().setBackground(Color.WHITE);
		scrollPane.setPreferredSize(new Dimension(960, 320));

		JComponent toolbar = buildToolbar();

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(toolbar,          BorderLayout.NORTH);
		getContentPane().add(scrollPane,       BorderLayout.CENTER);
		getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

		timeline.addHoverListener(this::showHovered);
		timeline.addPropertyChangeListener("xScaleFactor", e -> syncScaleControls());
		timeline.addPropertyChangeListener("range",        e -> updateInfo());
		syncScaleControls();
		updateInfo();
		installKeyBindings();

		pack();
		int minWidth = Math.max(640, toolbar.getPreferredSize().width + 24);
		setMinimumSize(new Dimension(minWidth, 320));
		setLocationRelativeTo(null);
		setVisible(true);

		// rows grow upward from the axis: start scrolled to the bottom
		SwingUtilities.invokeLater(() -> {
			JScrollBar v = scrollPane.getVerticalScrollBar();
			v.setValue(v.getMaximum());
		});
	}

	/** @return the timeline panel (for programmatic configuration) */
	public Timeline getTimeline() { return timeline; }

	// ---------------------------------------------------------------
	// Menu bar
	// ---------------------------------------------------------------

	private JMenuBar buildMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		// ---- Settings ------------------------------------------------
		JMenu settings = new JMenu("Settings");
		settings.setMnemonic(KeyEvent.VK_S);

		if (timeline.isEventTimeline()) {
			JCheckBoxMenuItem item =
					new JCheckBoxMenuItem("Show event labels", timeline.isShowEventLabels());
			item.addActionListener(e -> timeline.setShowEventLabels(item.isSelected()));
			settings.add(item);
		} else {
			JCheckBoxMenuItem item =
					new JCheckBoxMenuItem("Show interval labels", timeline.isShowIntervalLabels());
			item.addActionListener(e -> timeline.setShowIntervalLabels(item.isSelected()));
			settings.add(item);
		}
		settings.addSeparator();
		settings.add(menuItem("Set tick interval... (0 = automatic)", e -> askInt(
				"Time between two ticks (0 = automatic):",
				timeline.getTickInterval(), 0, timeline::setTickInterval)));
		settings.add(menuItem("Set X axis scale factor...", e -> askDouble(
				"Pixels per time unit:",
				timeline.getXScaleFactor(), timeline::setXScaleFactor)));
		settings.addSeparator();
		settings.add(menuItem("Set min. timestamp...", e -> askLong(
				"First displayed timestamp:",
				timeline.getMinTimestamp(), timeline::setMinTimestamp)));
		settings.add(menuItem("Set max. timestamp...", e -> askLong(
				"Last displayed timestamp:",
				timeline.getMaxTimestamp(), timeline::setMaxTimestamp)));
		settings.add(menuItem("Reset time range", e -> timeline.resetTimeRange()));
		settings.addSeparator();

		JMenu layout = new JMenu("Layout");
		if (timeline.isEventTimeline()) {
			layout.add(menuItem("Event row height...", e -> askInt(
					"Height of a row of event labels (pixels):",
					timeline.getEventHeight(), 8, timeline::setEventHeight)));
		} else {
			layout.add(menuItem("Interval height...", e -> askInt(
					"Height of an interval bar (pixels):",
					timeline.getIntervalHeight(), 2, timeline::setIntervalHeight)));
			layout.add(menuItem("Gap between intervals...", e -> askInt(
					"Vertical gap between interval rows (pixels):",
					timeline.getIntervalVerticalGap(), 0, timeline::setIntervalVerticalGap)));
			layout.add(menuItem("Group label prefix...", e -> {
				String s = JOptionPane.showInputDialog(this,
						"Text placed before the group number (e.g. \"S\" gives S1, S2, ...):",
						timeline.getGroupLabelPrefix());
				if (s != null) timeline.setGroupLabelPrefix(s.trim());
			}));
		}
		layout.add(menuItem("Tick height...", e -> askInt(
				"Height of the tick marks (pixels):",
				timeline.getTickHeight(), 0, timeline::setTickHeight)));
		settings.add(layout);

		// ---- View ----------------------------------------------------
		JMenu view = new JMenu("View");
		view.setMnemonic(KeyEvent.VK_V);
		view.add(menuItem("Zoom in",        ctrl(KeyEvent.VK_EQUALS), e -> timeline.zoomIn()));
		view.add(menuItem("Zoom out",       ctrl(KeyEvent.VK_MINUS),  e -> timeline.zoomOut()));
		view.add(menuItem("Fit to width",   ctrl(KeyEvent.VK_0),      e -> timeline.fitToWidth()));
		view.add(menuItem("Automatic zoom", ctrl(KeyEvent.VK_1),
				e -> timeline.setXScaleFactor(timeline.getAutoScaleFactor())));
		view.addSeparator();
		view.add(menuItem("Scroll to start",
				e -> scrollPane.getHorizontalScrollBar().setValue(0)));
		view.add(menuItem("Scroll to end", e -> {
			JScrollBar h = scrollPane.getHorizontalScrollBar();
			h.setValue(h.getMaximum());
		}));

		// ---- Export --------------------------------------------------
		JMenu export = new JMenu("Export");
		export.setMnemonic(KeyEvent.VK_E);
		JMenuItem save = menuItem("Save as PNG image...", ctrl(KeyEvent.VK_S),
				e -> saveAsPng());
		URL saveUrl = MainWindow.class.getResource(ICON_SAVE);
		if (saveUrl != null) save.setIcon(new ImageIcon(saveUrl));
		export.add(save);
		export.add(menuItem("Copy image to clipboard", ctrl(KeyEvent.VK_C),
				e -> copyToClipboard()));

		// ---- Help ----------------------------------------------------
		JMenu help = new JMenu("Help");
		help.setMnemonic(KeyEvent.VK_H);
		help.add(menuItem("Keyboard & mouse shortcuts", e -> JOptionPane.showMessageDialog(this,
				"Ctrl + mouse wheel       zoom at the cursor\n"
				+ "Mouse wheel                scroll vertically\n"
				+ "Shift + mouse wheel     scroll horizontally\n"
				+ "Ctrl + =  /  Ctrl + -      zoom in / out\n"
				+ "Ctrl + 0                        fit to width\n"
				+ "Ctrl + 1                        automatic zoom\n"
				+ "Ctrl + S                        save as PNG\n"
				+ "Ctrl + C                        copy image to clipboard\n"
				+ "Hover                            show details of an element",
				"Shortcuts", JOptionPane.INFORMATION_MESSAGE)));

		menuBar.add(settings);
		menuBar.add(view);
		menuBar.add(export);
		menuBar.add(help);
		return menuBar;
	}

	@SuppressWarnings("deprecation")
	private static KeyStroke ctrl(int key) {
		return KeyStroke.getKeyStroke(key,
				Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
	}

	private JMenuItem menuItem(String text, ActionListener l) {
		return menuItem(text, null, l);
	}

	private JMenuItem menuItem(String text, KeyStroke accelerator, ActionListener l) {
		JMenuItem item = new JMenuItem(text);
		if (accelerator != null) item.setAccelerator(accelerator);
		item.addActionListener(l);
		return item;
	}

	// ---------------------------------------------------------------
	// Toolbar
	// ---------------------------------------------------------------

	private JComponent buildToolbar() {
		JPanel bar = new JPanel();
		bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
		bar.setBackground(BAR_BG);
		bar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, LINE_COLOR),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));

		bar.add(new JLabel("Zoom:"));
		bar.add(gap(6));
		bar.add(toolButton("\u2212", "Zoom out (Ctrl + -)", e -> timeline.zoomOut()));
		bar.add(gap(4));

		Dimension sliderSize = new Dimension(220, scaleSlider.getPreferredSize().height);
		scaleSlider.setOpaque(false);
		scaleSlider.setPreferredSize(sliderSize);
		scaleSlider.setMaximumSize(sliderSize);
		scaleSlider.setMinimumSize(new Dimension(80, sliderSize.height));
		scaleSlider.setToolTipText(
				"X axis scale (logarithmic). Ctrl + mouse wheel zooms at the cursor.");
		scaleSlider.addChangeListener(e -> {
			if (updatingSlider) return;
			timeline.setXScaleFactor(sliderToScale(scaleSlider.getValue()));
		});
		bar.add(scaleSlider);

		bar.add(gap(4));
		bar.add(toolButton("+", "Zoom in (Ctrl + =)", e -> timeline.zoomIn()));
		bar.add(gap(8));

		// Fixed-width label: measure every possible text format so nothing is clipped
		Font labelFont = scaleLabel.getFont().deriveFont(Font.PLAIN, 11f);
		scaleLabel.setFont(labelFont);
		scaleLabel.setForeground(new Color(90, 95, 105));
		scaleLabel.setHorizontalAlignment(SwingConstants.LEFT);
		FontMetrics fm = scaleLabel.getFontMetrics(labelFont);
		int labelWidth = 0;
		for (String sample : new String[] {
				"1,000,000 px / unit",
				"0.0001234 px / unit",
				"1.23e-05 px / unit",
				"99.9999 px / unit" }) {
			labelWidth = Math.max(labelWidth, fm.stringWidth(sample));
		}
		Dimension labelSize = new Dimension(labelWidth + 12, fm.getHeight() + 6);
		scaleLabel.setPreferredSize(labelSize);
		scaleLabel.setMinimumSize(labelSize);
		scaleLabel.setMaximumSize(labelSize);
		bar.add(scaleLabel);

		bar.add(gap(8));
		bar.add(separator());
		bar.add(gap(8));
		bar.add(toolButton("Fit",  "Fit the whole time range in the window (Ctrl + 0)",
				e -> timeline.fitToWidth()));
		bar.add(gap(4));
		bar.add(toolButton("Auto", "Automatic zoom for this data (Ctrl + 1)",
				e -> timeline.setXScaleFactor(timeline.getAutoScaleFactor())));

		bar.add(gap(8));
		bar.add(separator());
		bar.add(gap(8));

		JCheckBox labels;
		if (timeline.isEventTimeline()) {
			labels = new JCheckBox("Labels", timeline.isShowEventLabels());
			labels.addActionListener(e -> timeline.setShowEventLabels(labels.isSelected()));
		} else {
			labels = new JCheckBox("Labels", timeline.isShowIntervalLabels());
			labels.addActionListener(e -> timeline.setShowIntervalLabels(labels.isSelected()));
		}
		labels.setOpaque(false);
		labels.setFocusable(false);
		labels.setToolTipText("Show or hide the element names");
		bar.add(labels);

		bar.add(gap(8));
		bar.add(separator());
		bar.add(gap(8));

		JButton saveBtn = toolButton("Save PNG",
				"Save the timeline as a PNG image (Ctrl + S)", e -> saveAsPng());
		URL saveUrl = MainWindow.class.getResource(ICON_SAVE);
		if (saveUrl != null) saveBtn.setIcon(new ImageIcon(saveUrl));
		bar.add(saveBtn);
		bar.add(gap(4));
		bar.add(toolButton("Copy",
				"Copy the timeline image to the clipboard (Ctrl + C)",
				e -> copyToClipboard()));

		bar.add(Box.createHorizontalGlue());
		return bar;
	}

	private static Component gap(int width) {
		return Box.createHorizontalStrut(width);
	}

	private static JButton toolButton(String text, String tooltip, ActionListener l) {
		JButton b = new JButton(text);
		b.setToolTipText(tooltip);
		b.setMargin(new Insets(2, 8, 2, 8));
		b.setFocusable(false);
		b.setMaximumSize(b.getPreferredSize());
		b.addActionListener(l);
		return b;
	}

	private static JComponent separator() {
		JSeparator s = new JSeparator(SwingConstants.VERTICAL);
		Dimension d = new Dimension(1, 22);
		s.setPreferredSize(d);
		s.setMaximumSize(d);
		return s;
	}

	// ---------------------------------------------------------------
	// Status bar
	// ---------------------------------------------------------------

	private JComponent buildStatusBar() {
		JPanel bar = new JPanel(new BorderLayout(12, 0));
		bar.setBackground(BAR_BG);
		bar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, LINE_COLOR),
				BorderFactory.createEmptyBorder(3, 8, 3, 8)));
		Font f = statusLabel.getFont().deriveFont(Font.PLAIN, 11f);
		statusLabel.setFont(f);
		infoLabel.setFont(f);
		infoLabel.setForeground(new Color(90, 95, 105));
		bar.add(statusLabel, BorderLayout.CENTER);
		bar.add(infoLabel,   BorderLayout.EAST);
		return bar;
	}

	// ---------------------------------------------------------------
	// Slider <-> scale  (logarithmic, centred on the automatic scale)
	// ---------------------------------------------------------------

	private double sliderToScale(int value) {
		// value in [0, SLIDER_MAX] -> t in [-1, 1]
		double t = (value - SLIDER_MAX / 2.0) / (SLIDER_MAX / 2.0);
		return timeline.getAutoScaleFactor() * Math.pow(10, t * SLIDER_DECADES);
	}

	private int scaleToSlider(double scale) {
		double auto = timeline.getAutoScaleFactor();
		if (!(scale > 0) || !(auto > 0)) return SLIDER_MAX / 2;
		double t = Math.log10(scale / auto) / SLIDER_DECADES;   // in [-1, 1]
		int v = (int) Math.round(SLIDER_MAX / 2.0 + t * SLIDER_MAX / 2.0);
		return Math.max(0, Math.min(SLIDER_MAX, v));
	}

	private void syncScaleControls() {
		updatingSlider = true;
		try {
			scaleSlider.setValue(scaleToSlider(timeline.getXScaleFactor()));
		} finally {
			updatingSlider = false;
		}
		scaleLabel.setText(formatScale(timeline.getXScaleFactor()) + " px / unit");
	}

	private static String formatScale(double s) {
		if (s >= 100)   return String.format(Locale.US, "%.0f",  s);
		if (s >= 1)     return String.format(Locale.US, "%.2f",  s);
		if (s >= 0.01)  return String.format(Locale.US, "%.4f",  s);
		return                  String.format(Locale.US, "%.3g",  s);
	}

	// ---------------------------------------------------------------
	// Status helpers
	// ---------------------------------------------------------------

	private void updateInfo() {
		StringBuilder sb = new StringBuilder();
		if (timeline.isEventTimeline()) {
			int n = timeline.getEvents().size();
			sb.append(nf.format(n)).append(n == 1 ? " event" : " events");
		} else {
			List<IntervalT> list = timeline.getIntervals();
			Set<Integer> groups = new HashSet<Integer>();
			for (IntervalT it : list) groups.add(it.getGroup());
			sb.append(nf.format(list.size()))
			  .append(list.size() == 1 ? " interval" : " intervals");
			sb.append(" in ").append(nf.format(groups.size()))
			  .append(groups.size() == 1 ? " group" : " groups");
		}
		sb.append("   |   range ")
		  .append(nf.format(timeline.getMinTimestamp()))
		  .append(" \u2013 ")
		  .append(nf.format(timeline.getMaxTimestamp()));
		infoLabel.setText(sb.toString());
	}

	private void showHovered(ElementT el) {
		if (el == null) { setStatus(" "); return; }
		String name = el.getName().isEmpty() ? "(unnamed)" : el.getName();
		if (el instanceof IntervalT) {
			IntervalT it = (IntervalT) el;
			setStatus(name
					+ "   start "    + nf.format(it.getStartTime())
					+ "   end "      + nf.format(it.getEndTime())
					+ "   duration " + nf.format(it.getDuration())
					+ "   group "    + timeline.getGroupLabelPrefix() + it.getGroup());
		} else if (el instanceof EventT) {
			setStatus(name + "   time " + nf.format(((EventT) el).getTime()));
		} else {
			setStatus(name);
		}
	}

	private void setStatus(String text) {
		statusLabel.setText(text == null || text.isEmpty() ? " " : text);
	}

	// ---------------------------------------------------------------
	// Key bindings
	// ---------------------------------------------------------------

	private void installKeyBindings() {
		InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = getRootPane().getActionMap();

		bind(im, am, "zoomIn",   timeline::zoomIn,
				ctrl(KeyEvent.VK_EQUALS),
				ctrl(KeyEvent.VK_PLUS),
				ctrl(KeyEvent.VK_ADD));
		bind(im, am, "zoomOut",  timeline::zoomOut,
				ctrl(KeyEvent.VK_MINUS),
				ctrl(KeyEvent.VK_SUBTRACT));
		bind(im, am, "fit",      timeline::fitToWidth,
				ctrl(KeyEvent.VK_0),
				ctrl(KeyEvent.VK_NUMPAD0));
		bind(im, am, "auto",     () -> timeline.setXScaleFactor(timeline.getAutoScaleFactor()),
				ctrl(KeyEvent.VK_1),
				ctrl(KeyEvent.VK_NUMPAD1));
		bind(im, am, "save",     this::saveAsPng,
				ctrl(KeyEvent.VK_S));
		bind(im, am, "copy",     this::copyToClipboard,
				ctrl(KeyEvent.VK_C));
	}

	private static void bind(InputMap im, ActionMap am,
	                          String name, Runnable action, KeyStroke... keys) {
		am.put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override public void actionPerformed(ActionEvent e) { action.run(); }
		});
		for (KeyStroke k : keys) im.put(k, name);
	}

	// ---------------------------------------------------------------
	// Input dialogs
	// ---------------------------------------------------------------

	private void askInt(String message, int current, int min, IntConsumer setter) {
		String input = JOptionPane.showInputDialog(this, message, current);
		if (input == null || input.trim().isEmpty()) return;
		try {
			int v = Integer.parseInt(input.trim());
			if (v < min) {
				JOptionPane.showMessageDialog(this,
						"Please enter a value >= " + min + ".",
						"Invalid value", JOptionPane.WARNING_MESSAGE);
				return;
			}
			setter.accept(v);
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Please enter a valid integer.",
					"Invalid value", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void askLong(String message, long current, LongConsumer setter) {
		String input = JOptionPane.showInputDialog(this, message, current);
		if (input == null || input.trim().isEmpty()) return;
		try {
			setter.accept(Long.parseLong(input.trim().replace(",", "")));
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Please enter a valid integer.",
					"Invalid value", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void askDouble(String message, double current, DoubleConsumer setter) {
		String input = JOptionPane.showInputDialog(this, message, current);
		if (input == null || input.trim().isEmpty()) return;
		try {
			double v = Double.parseDouble(input.trim());
			if (!(v > 0)) {
				JOptionPane.showMessageDialog(this,
						"Please enter a value > 0.",
						"Invalid value", JOptionPane.WARNING_MESSAGE);
				return;
			}
			setter.accept(v);
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Please enter a valid number.",
					"Invalid value", JOptionPane.WARNING_MESSAGE);
		}
	}

	// ---------------------------------------------------------------
	// Export
	// ---------------------------------------------------------------

	/** Saves the whole timeline (2× resolution) as a PNG file chosen by the user. */
	private void saveAsPng() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save timeline as PNG image");
		chooser.setFileFilter(new FileNameExtensionFilter("PNG image (*.png)", "png"));
		chooser.setAcceptAllFileFilterUsed(false);

		String lastDir = PreferencesManager.getInstance().getOutputFilePath();
		if (lastDir != null && !lastDir.isEmpty() && new File(lastDir).isDirectory()) {
			chooser.setCurrentDirectory(new File(lastDir));
		}
		chooser.setSelectedFile(
				new File(chooser.getCurrentDirectory(), "timeline.png"));

		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
			file = new File(file.getParentFile(), file.getName() + ".png");
		}
		if (file.exists()) {
			int r = JOptionPane.showConfirmDialog(this,
					"\"" + file.getName() + "\" already exists.\nDo you want to replace it?",
					"Confirm overwrite", JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (r != JOptionPane.YES_OPTION) return;
		}
		try {
			BufferedImage image = timeline.renderToImage(2.0);
			if (!ImageIO.write(image, "png", file)) {
				throw new IOException("No PNG writer available");
			}
			if (file.getParent() != null) {
				PreferencesManager.getInstance().setOutputFilePath(file.getParent());
			}
			setStatus("Image saved: " + file.getAbsolutePath());
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this,
					"Error saving image: " + ex.getMessage(),
					"Export error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Copies the whole timeline (2× resolution) to the system clipboard. */
	private void copyToClipboard() {
		try {
			BufferedImage image = timeline.renderToImage(2.0);
			Toolkit.getDefaultToolkit().getSystemClipboard()
			       .setContents(new ImageSelection(image), null);
			setStatus("Image copied to the clipboard ("
					+ image.getWidth() + " \u00d7 " + image.getHeight() + " px)");
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
					"Could not copy the image: " + ex.getMessage(),
					"Clipboard error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Transferable wrapping a BufferedImage for the system clipboard. */
	private static final class ImageSelection implements Transferable {
		private final BufferedImage image;
		ImageSelection(BufferedImage image) { this.image = image; }

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] { DataFlavor.imageFlavor };
		}
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return DataFlavor.imageFlavor.equals(flavor);
		}
		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
			if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
			return image;
		}
	}

	// ---------------------------------------------------------------
	// Quick demo / standalone entry point
	// ---------------------------------------------------------------

	/**
	 * Small demo. Run without arguments for an event timeline, or with the
	 * argument {@code intervals} for an interval timeline.
	 */
	public static void main(String[] args) {
		final boolean showIntervals =
				args.length > 0 && "intervals".equalsIgnoreCase(args[0]);
		SwingUtilities.invokeLater(() -> {
			Random rnd = new Random(1);
			if (!showIntervals) {
				String[] names = {
						"login", "search", "view item", "add to cart", "checkout", "logout" };
				List<EventT> events = new ArrayList<EventT>();
				long t = 100;
				for (int i = 0; i < 60; i++) {
					t += 1 + rnd.nextInt(40);
					events.add(new EventT(names[rnd.nextInt(names.length)], t));
				}
				new TimelineViewer(true, events, null);
			} else {
				List<IntervalT> intervals = new ArrayList<IntervalT>();
				for (int g = 1; g <= 5; g++) {
					long t = 0;
					for (int k = 0; k < 8; k++) {
						long start = t + rnd.nextInt(30);
						long end   = start + 5 + rnd.nextInt(60);
						intervals.add(new IntervalT("Task " + (k + 1), start, end, g));
						t = start + rnd.nextInt(30);
					}
				}
				new TimelineViewer(true, null, intervals);
			}
		});
	}
}