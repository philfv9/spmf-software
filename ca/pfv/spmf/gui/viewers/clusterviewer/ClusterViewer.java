package ca.pfv.spmf.gui.viewers.clusterviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.visuals.heatmap.ClusterViewerPanelListener;
import ca.pfv.spmf.gui.visuals.plot.Plot.LegendFormat;
import ca.pfv.spmf.patterns.cluster.Cluster;
import ca.pfv.spmf.patterns.cluster.DoubleArray;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * The SPMF cluster viewer: displays clusters as a scatter plot of two
 * selectable attributes.
 *
 * @author Philippe Fournier-Viger, 2016
 */
public class ClusterViewer extends JFrame implements ClusterViewerPanelListener {

	private static final long serialVersionUID = 1L;

	private static final Color BAR_BG = new Color(248, 249, 251);
	private static final Color LINE_COLOR = new Color(210, 214, 220);
	private static final String[] POINT_SIZES = {
			"NONE", "1 pts", "2 pts", "3 pts", "4 pts", "5 pts", "6 pts", "7 pts", "8 pts", "9 pts", "10 pts" };

	String title = "SPMF Cluster Viewer " + Main.SPMF_VERSION;

	ClusterViewerPanel panelChart;
	String[] attributeNamesArray;

	private final JLabel labelX = new JLabel("x = ");
	private final JLabel labelY = new JLabel("y = ");
	private final JLabel labelName = new JLabel("name = ");
	private final DecimalFormat df = new DecimalFormat("0.00");

	private JComboBox<String> comboBoxX;
	private JComboBox<String> comboBoxY;
	/** guards against re-entrant events while swapping X/Y */
	private boolean updatingCombos;

	/**
	 * Constructor
	 * @param clusters a list of clusters to display
	 * @param attributeNames the attribute names (may be null or empty)
	 */
	public ClusterViewer(List<Cluster> clusters, List<String> attributeNames) {
		if (clusters == null) clusters = new ArrayList<Cluster>();
		if (clusters.isEmpty()) {
			JOptionPane.showMessageDialog(null,
					"The file is empty. The cluster viewer has nothing to display",
					"Error", JOptionPane.ERROR_MESSAGE);
		}

		attributeNamesArray = buildAttributeNames(clusters, attributeNames);
		int dims = attributeNamesArray.length;
		int initialX = 0;
		int initialY = Math.min(1, Math.max(0, dims - 1));

		setTitle(title);
		setSize(900, 687);
		setMinimumSize(new Dimension(720, 480));
		setLocationRelativeTo(null);

		panelChart = new ClusterViewerPanel(clusters, initialX, initialY, attributeNamesArray);
		panelChart.addListener(this);

		JScrollPane scrollPane = new JScrollPane(panelChart);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getViewport().setBackground(Color.WHITE);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(buildToolbar(initialX, initialY), BorderLayout.NORTH);
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);
	}

	/** Uses the given names, generating / padding "Attribute i" names as needed. */
	private static String[] buildAttributeNames(List<Cluster> clusters, List<String> names) {
		int dims = 0;
		for (Cluster c : clusters) {
			if (c.getVectors() != null) {
				for (DoubleArray v : c.getVectors()) {
					if (v != null && v.data != null) dims = Math.max(dims, v.data.length);
				}
			}
		}
		if (names != null) dims = Math.max(dims, names.size());
		if (dims == 0) dims = 1;
		String[] result = new String[dims];
		for (int i = 0; i < dims; i++) {
			String n = (names != null && i < names.size()) ? names.get(i) : null;
			result[i] = (n == null || n.isEmpty()) ? "Attribute " + i : n;
		}
		return result;
	}

	// ---------------------------------------------------------------
	// UI
	// ---------------------------------------------------------------

	private JPanel buildToolbar(int initialX, int initialY) {
		JPanel toolbar = new JPanel();
		toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
		toolbar.setBackground(BAR_BG);
		toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE_COLOR));

		// ---- row 1: attributes -----------------------------------------
		JPanel row1 = newRow();

		row1.add(new JLabel("X attribute:"));
		comboBoxX = new JComboBox<String>(attributeNamesArray);
		comboBoxX.setSelectedIndex(initialX);
		comboBoxX.addActionListener(e -> applyAttributeSelection());
		row1.add(comboBoxX);

		row1.add(new JLabel("Y attribute:"));
		comboBoxY = new JComboBox<String>(attributeNamesArray);
		comboBoxY.setSelectedIndex(initialY);
		comboBoxY.addActionListener(e -> applyAttributeSelection());
		row1.add(comboBoxY);

		row1.add(createButton("\u21C4", null, "Swap the X and Y attributes", e -> swapAttributes(), null));

		row1.add(createSeparator());

		row1.add(new JLabel("Legend:"));
		JComboBox<LegendFormat> comboBoxLegend = new JComboBox<LegendFormat>(LegendFormat.values());
		comboBoxLegend.setSelectedItem(LegendFormat.BOTTOM);
		comboBoxLegend.addActionListener(e ->
				panelChart.setDrawLegend((LegendFormat) comboBoxLegend.getSelectedItem()));
		row1.add(comboBoxLegend);

		row1.add(new JLabel("Markers:"));
		JComboBox<String> comboBoxMarkers = new JComboBox<String>(POINT_SIZES);
		comboBoxMarkers.setSelectedIndex(5);
		comboBoxMarkers.addActionListener(e -> panelChart.setMarkersSize(comboBoxMarkers.getSelectedIndex()));
		row1.add(comboBoxMarkers);

		// ---- row 2: toggles, zoom, export ---------------------------------
		JPanel row2 = newRow();

		JCheckBox checkGrid = new JCheckBox("Grid", false);
		checkGrid.setOpaque(false);
		checkGrid.setFocusable(false);
		checkGrid.addActionListener(e -> panelChart.setDrawGrid(checkGrid.isSelected()));
		row2.add(checkGrid);

		JCheckBox checkCentroids = new JCheckBox("Centroids", true);
		checkCentroids.setOpaque(false);
		checkCentroids.setFocusable(false);
		checkCentroids.setToolTipText("Mark the centroid of each cluster with an \u00D7");
		checkCentroids.addActionListener(e -> panelChart.setDrawCentroids(checkCentroids.isSelected()));
		row2.add(checkCentroids);

		row2.add(createSeparator());

		row2.add(new JLabel("Zoom:"));
		row2.add(createButton("", "/ca/pfv/spmf/gui/icons/zoomin.gif", "Zoom in (Ctrl + wheel)",
				e -> panelChart.increaseZoom(), "+"));
		row2.add(createButton("", "/ca/pfv/spmf/gui/icons/zoomout.gif", "Zoom out (Ctrl + wheel)",
				e -> panelChart.decreaseZoom(), "\u2212"));
		row2.add(createButton("Fit", null, "Fit the chart in the window", e -> panelChart.resetZoom(), null));

		row2.add(createSeparator());

		row2.add(createButton("Save as PNG", "/ca/pfv/spmf/gui/icons/save.gif",
				"Save the chart as a PNG image (2x resolution)", e -> panelChart.export(), null));
		row2.add(createButton("Print", "/ca/pfv/spmf/gui/icons/print.gif",
				"Print the chart", e -> panelChart.doPrint(), null));

		toolbar.add(row1);
		toolbar.add(row2);
		return toolbar;
	}

	private void applyAttributeSelection() {
		if (updatingCombos) return;
		panelChart.setAttributeSelection(comboBoxX.getSelectedIndex(), comboBoxY.getSelectedIndex());
	}

	private void swapAttributes() {
		int x = comboBoxX.getSelectedIndex();
		int y = comboBoxY.getSelectedIndex();
		updatingCombos = true;
		try {
			comboBoxX.setSelectedIndex(y);
			comboBoxY.setSelectedIndex(x);
		} finally {
			updatingCombos = false;
		}
		applyAttributeSelection();
	}

	private JPanel buildStatusBar() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
		bar.setBackground(BAR_BG);
		bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE_COLOR));
		Font f = labelX.getFont().deriveFont(Font.PLAIN, 12f);
		for (JLabel l : new JLabel[] { labelX, labelY, labelName }) l.setFont(f);
		labelX.setPreferredSize(new Dimension(110, 18));
		labelY.setPreferredSize(new Dimension(110, 18));
		bar.add(labelX);
		bar.add(labelY);
		bar.add(labelName);
		return bar;
	}

	private JPanel newRow() {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
		return row;
	}

	private JSeparator createSeparator() {
		JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
		sep.setPreferredSize(new Dimension(1, 22));
		return sep;
	}

	private JButton createButton(String text, String iconPath, String tooltip,
			java.awt.event.ActionListener l, String fallbackText) {
		JButton b = new JButton(text);
		URL url = iconPath != null ? ClusterViewer.class.getResource(iconPath) : null;
		if (url != null) {
			b.setIcon(new ImageIcon(url));
		} else if (fallbackText != null && text.isEmpty()) {
			b.setText(fallbackText);
		}
		b.setToolTipText(tooltip);
		b.setFocusable(false);
		b.addActionListener(l);
		return b;
	}

	// ---------------------------------------------------------------
	// ClusterViewerPanelListener
	// ---------------------------------------------------------------

	@Override
	public void notifyOfNewMousePosition(double x, double y, Color colorUnderMouse, String objectUnderMouse) {
		labelX.setText("x = " + df.format(x));
		labelY.setText("y = " + df.format(y));
		if (objectUnderMouse != null && !objectUnderMouse.isEmpty()) {
			labelName.setText("Name = " + objectUnderMouse);
			labelName.setForeground(colorUnderMouse != null ? colorUnderMouse : Color.BLACK);
		} else {
			labelName.setText("Name = ");
			labelName.setForeground(Color.BLACK);
		}
	}

	@Override
	public void notifyMouseOutOfChart() {
		labelX.setText("x = ");
		labelY.setText("y = ");
		labelName.setText("Name = ");
		labelName.setForeground(Color.BLACK);
	}
}