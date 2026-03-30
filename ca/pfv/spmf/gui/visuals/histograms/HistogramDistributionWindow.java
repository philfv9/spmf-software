package ca.pfv.spmf.gui.visuals.histograms;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;

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

/**
 * A window ({@link JFrame}) that wraps a {@link HistogramDistributionPanel}
 * inside a scrollable area and exposes controls for bar width, sort order,
 * and export (CSV / PNG).
 *
 * @author Philippe Fournier-Viger
 */
public class HistogramDistributionWindow extends JFrame {

    private static final long serialVersionUID = 4751136799631193209L;

    // ---------------------------------------------------------------
    // Layout constants
    // ---------------------------------------------------------------
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 450;
    private static final int TEXT_FIELD_COLUMNS = 4;
    private static final int MIN_HISTOGRAM_HEIGHT = 200;
    private static final String ICON_PATH_HISTOGRAM = "/ca/pfv/spmf/gui/icons/histogram.png";
    private static final String ICON_PATH_SAVE = "/ca/pfv/spmf/gui/icons/save.gif";

    // ---------------------------------------------------------------
    // Instance fields
    // ---------------------------------------------------------------
    private final JScrollPane scrollPane;
    private final HistogramDistributionPanel histogram;

    // ---------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------

    public HistogramDistributionWindow(boolean runAsStandalone,
                                      int[] yValues, int[] xLabels, String title,
                                      boolean showBarLabels, boolean showBarValues,
                                      String xAxisName, String yAxisName,
                                      Map<Integer, String> mapItemToString, Order order) {

        histogram = new HistogramDistributionPanel(yValues, xLabels, title,
                showBarLabels, showBarValues, xAxisName, yAxisName,
                mapItemToString, order);
        scrollPane = createScrollPane();
        initializeWindow(runAsStandalone, order);
    }

    public HistogramDistributionWindow(boolean runAsStandalone,
                                      Vector<List<Object>> data, int index, String title,
                                      String xAxisName, String yAxisName, Order order) {
        super();

        histogram = new HistogramDistributionPanel(data,
                DEFAULT_WIDTH - 20, DEFAULT_HEIGHT + 150, index, title,
                true, true, xAxisName, yAxisName, order);
        scrollPane = createScrollPane();
        initializeWindow(runAsStandalone, order);
    }

    // ---------------------------------------------------------------
    // Window initialisation
    // ---------------------------------------------------------------

    private JScrollPane createScrollPane() {
        JScrollPane sp = new JScrollPane(histogram);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        sp.getViewport().setBackground(java.awt.Color.WHITE);
        return sp;
    }

    private void initializeWindow(boolean runAsStandalone, Order order) {
        setIconImage(Toolkit.getDefaultToolkit()
                .getImage(MainWindow.class.getResource(ICON_PATH_HISTOGRAM)));
        setTitle("SPMF Histogram Viewer " + Main.SPMF_VERSION);

        if (runAsStandalone) {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }

        setLayout(new BorderLayout());

        // Toolbar at top
        getContentPane().add(buildToolbar(order), BorderLayout.NORTH);

        // Chart area
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        // Resize listener
        getContentPane().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshHistogramSize();
            }
        });

        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(450, 300));
        setLocationRelativeTo(null);
        pack();
        setVisible(true);
    }

    // ---------------------------------------------------------------
    // UI factory methods
    // ---------------------------------------------------------------

    private JPanel buildToolbar(Order order) {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        // --- Bar width ---
        toolbar.add(new JLabel("Bar width:"));
        JTextField widthField = new JTextField(
                String.valueOf(histogram.getBarWidth()), TEXT_FIELD_COLUMNS);
        widthField.addActionListener(e -> applyBarWidth(widthField.getText()));
        toolbar.add(widthField);

        // Separator
        toolbar.add(createSeparator());

        // --- Sort order ---
        toolbar.add(new JLabel("Sort X axis by:"));
        JComboBox<Order> sortBox = new JComboBox<>(Order.values());
        sortBox.setSelectedItem(order);
        sortBox.addActionListener(e -> {
            Order selected = (Order) sortBox.getSelectedItem();
            if (selected != null) {
                histogram.setSortOrder(selected);
                refreshHistogramSize();
            }
        });
        toolbar.add(sortBox);

        // Separator
        toolbar.add(createSeparator());

        // --- Export buttons ---
        toolbar.add(createSaveButton("Save as CSV", e -> histogram.exportAsCSV()));
        toolbar.add(createSaveButton("Save as PNG", e -> histogram.exportAsImage()));

        return toolbar;
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 22));
        return sep;
    }

    private JButton createSaveButton(String text,
                                     java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        try {
            button.setIcon(new ImageIcon(
                    MainWindow.class.getResource(ICON_PATH_SAVE)));
        } catch (Exception ignored) {
        }
        button.addActionListener(listener);
        return button;
    }

    // ---------------------------------------------------------------
    // Bar-width helpers
    // ---------------------------------------------------------------

    private void applyBarWidth(String text) {
        try {
            int newWidth = Integer.parseInt(text.trim());
            if (newWidth > 0) {
                histogram.setBarWidth(newWidth);
                refreshHistogramSize();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    // ---------------------------------------------------------------
    // Layout refresh
    // ---------------------------------------------------------------

    private void refreshHistogramSize() {
        SwingUtilities.invokeLater(() -> {
            int availableW = scrollPane.getViewport().getWidth();
            int availableH = scrollPane.getViewport().getHeight();

            int chartW = histogram.getPreferredSize().width;

            int finalW = Math.max(chartW, availableW);
            int finalH = Math.max(availableH, MIN_HISTOGRAM_HEIGHT);

            Dimension size = new Dimension(finalW, finalH);
            histogram.setSize(size);
            histogram.setPreferredSize(size);
            histogram.setMinimumSize(new Dimension(chartW, MIN_HISTOGRAM_HEIGHT));

            histogram.revalidate();
            histogram.repaint();
            scrollPane.revalidate();
        });
    }
}