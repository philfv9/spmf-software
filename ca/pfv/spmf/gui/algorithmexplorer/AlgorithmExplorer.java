package ca.pfv.spmf.gui.algorithmexplorer;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmJTree.AlgoNode;

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
 * Window that lets the user browse the algorithms offered in SPMF, see their
 * description (inputs, outputs, parameters, ...) and highlight algorithms that
 * are similar to the selected one.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmExplorer extends JFrame {

	/** Serial UID */
	private static final long serialVersionUID = 6208435839510050052L;

	private static final String WINDOW_TITLE = "Algorithm Explorer";
	private static final String ICON_PATH = "/ca/pfv/spmf/gui/spmf.png";

	private static final int DEFAULT_WIDTH = 920;
	private static final int DEFAULT_HEIGHT = 760;
	private static final int MIN_WIDTH = 720;
	private static final int MIN_HEIGHT = 560;
	private static final int TREE_PANEL_WIDTH = 300;
	private static final int LIST_HEIGHT = 90;
	private static final int TABLE_HEIGHT = 180;
	private static final int PADDING = 10;

	private static final String[] PARAMETER_COLUMNS = { "Name", "Type", "Optional?" };

	// ---- UI components -------------------------------------------------

	private final AlgorithmJTree tree;
	private final JLabel labelTreeTitle = new JLabel();

	private final JTextField fieldName = createReadOnlyField();
	private final JTextField fieldCategory = createReadOnlyField();
	private final JTextField fieldType = createReadOnlyField();
	private final JTextField fieldAuthors = createReadOnlyField();
	private final JTextField fieldDoc = createReadOnlyField();

	private final DefaultListModel<String> listInputModel = new DefaultListModel<>();
	private final DefaultListModel<String> listOutputModel = new DefaultListModel<>();
	private final DefaultTableModel parametersModel = new DefaultTableModel(PARAMETER_COLUMNS, 0) {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};

	private final JButton buttonWeb = new JButton("Open");
	private final JButton buttonHighlightSameInOut = new JButton("Highlight algorithms with same input/output");
	private final JButton buttonHighlightSameInOutParams = new JButton(
			"Highlight algorithms with same input/output/mandatory parameters");
	private final JButton buttonRemoveHighlight = new JButton("Remove highlight");

	/**
	 * Constructor.
	 *
	 * @param runAsStandalone true if the window is the main window of the
	 *                        application (closing it exits the JVM)
	 */
	public AlgorithmExplorer(boolean runAsStandalone) {
		super(WINDOW_TITLE);
		tree = new AlgorithmJTree(true, true, true, true, true);

		setDefaultCloseOperation(runAsStandalone ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE);
		setIcon();
		setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
		setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
		setContentPane(buildContentPane());

		registerListeners();
		clearAlgorithmDetails();
		updateTreeTitle(0);
		updateHighlightButtons();

		setLocationRelativeTo(null);
		setVisible(true);
	}

	// ---- UI construction -----------------------------------------------

	private void setIcon() {
		URL iconUrl = MainWindow.class.getResource(ICON_PATH);
		if (iconUrl != null) {
			setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
		}
	}

	private JComponent buildContentPane() {
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildTreePanel(), buildDetailsPanel());
		split.setResizeWeight(0.0); // extra space goes to the details panel
		split.setBorder(null);
		split.setDividerLocation(TREE_PANEL_WIDTH);

		JPanel content = new JPanel(new BorderLayout());
		content.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
		content.add(split, BorderLayout.CENTER);
		return content;
	}

	private JPanel buildTreePanel() {
		labelTreeTitle.setFont(bold(labelTreeTitle.getFont()));

		JPanel panel = new JPanel(new BorderLayout(0, 5));
		panel.add(labelTreeTitle, BorderLayout.NORTH);
		panel.add(new JScrollPane(tree), BorderLayout.CENTER);
		panel.setPreferredSize(new Dimension(TREE_PANEL_WIDTH, 0));
		panel.setMinimumSize(new Dimension(200, 0));
		return panel;
	}

	private JPanel buildDetailsPanel() {
		JLabel title = new JLabel("Algorithm information", SwingConstants.CENTER);
		title.setFont(bold(title.getFont()));

		JPanel panel = new JPanel(new BorderLayout(0, PADDING));
		panel.setBorder(BorderFactory.createEmptyBorder(0, PADDING, 0, 0));
		panel.add(title, BorderLayout.NORTH);
		panel.add(buildDetailsForm(), BorderLayout.CENTER);
		panel.add(buildButtonPanel(), BorderLayout.SOUTH);
		return panel;
	}

	private JPanel buildDetailsForm() {
		JPanel form = new JPanel(new GridBagLayout());
		int row = 0;

		addFormRow(form, row++, "Name:", fieldName, 0);
		addFormRow(form, row++, "Category:", fieldCategory, 0);
		addFormRow(form, row++, "Type:", fieldType, 0);
		addFormRow(form, row++, "Coded by:", fieldAuthors, 0);

		JPanel docRow = new JPanel(new BorderLayout(5, 0));
		docRow.add(fieldDoc, BorderLayout.CENTER);
		docRow.add(buttonWeb, BorderLayout.EAST);
		addFormRow(form, row++, "Example:", docRow, 0);

		addFormRow(form, row++, "Input type:", createListPane(listInputModel), 1);
		addFormRow(form, row++, "Output type:", createListPane(listOutputModel), 1);

		JTable tableParameters = new JTable(parametersModel);
		tableParameters.setFillsViewportHeight(true);
		tableParameters.getTableHeader().setReorderingAllowed(false);
		JScrollPane tableScroll = new JScrollPane(tableParameters);
		tableScroll.setPreferredSize(new Dimension(0, TABLE_HEIGHT));
		addFormRow(form, row, "Parameters:", tableScroll, 2);

		return form;
	}

	private JPanel buildButtonPanel() {
		JPanel panel = new JPanel(new GridLayout(3, 1, 0, 5));
		panel.add(buttonHighlightSameInOut);
		panel.add(buttonHighlightSameInOutParams);
		panel.add(buttonRemoveHighlight);
		return panel;
	}

	/**
	 * Adds a "label : component" row to a GridBagLayout form.
	 *
	 * @param weightY 0 for single-line rows, &gt;0 for rows that should grow
	 *                vertically when the window is resized
	 */
	private static void addFormRow(JPanel form, int row, String labelText, JComponent component, double weightY) {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = row;
		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.weighty = weightY;
		gbc.anchor = weightY == 0 ? GridBagConstraints.WEST : GridBagConstraints.NORTHWEST;
		gbc.insets = new Insets(3, 0, 3, 8);
		form.add(new JLabel(labelText), gbc);

		gbc.gridx = 1;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(3, 0, 3, 0);
		form.add(component, gbc);
	}

	private static JTextField createReadOnlyField() {
		JTextField field = new JTextField();
		field.setEditable(false);
		field.setHorizontalAlignment(SwingConstants.LEFT);
		return field;
	}

	private static JScrollPane createListPane(DefaultListModel<String> model) {
		JList<String> list = new JList<>(model);
		list.setBackground(UIManager.getColor("Panel.background"));
		list.setFocusable(false);
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(0, LIST_HEIGHT));
		return scroll;
	}

	private static Font bold(Font font) {
		return font.deriveFont(Font.BOLD);
	}

	// ---- Listeners -----------------------------------------------------

	private void registerListeners() {
		buttonWeb.addActionListener(e -> openWebPage(fieldDoc.getText()));
		buttonHighlightSameInOut.addActionListener(e -> highlightSimilar(false));
		buttonHighlightSameInOutParams.addActionListener(e -> highlightSimilar(true));
		buttonRemoveHighlight.addActionListener(e -> removeHighlight());
		tree.addTreeSelectionListener(e -> onTreeSelectionChanged());
	}

	private void onTreeSelectionChanged() {
		AlgoNode selected = tree.getSelectedAlgoNode();
		if (selected == null || selected.isCategory()) {
			clearAlgorithmDetails();
		} else {
			showAlgorithmDetails(selected.getName(), selected.getDescription());
		}
		updateHighlightButtons();
	}

	private void highlightSimilar(boolean compareMandatoryParameters) {
		int count = tree.highlightSimilarAlgorithmsToSelection(compareMandatoryParameters);
		updateTreeTitle(count);
		updateHighlightButtons();
	}

	private void removeHighlight() {
		tree.clearAllHighlights();
		updateTreeTitle(0);
		updateHighlightButtons();
	}

	// ---- View updates --------------------------------------------------

	private void showAlgorithmDetails(String algorithmName, DescriptionOfAlgorithm description) {
		setText(fieldName, algorithmName);
		setText(fieldCategory, description.getAlgorithmCategory());
		setText(fieldType, description.getAlgorithmType() == null ? "" : description.getAlgorithmType().toString());
		setText(fieldAuthors, description.getImplementationAuthorNames());
		setText(fieldDoc, description.getURLOfDocumentation());
		buttonWeb.setEnabled(!fieldDoc.getText().isEmpty());

		fillListModel(listInputModel, description.getInputFileTypes());
		fillListModel(listOutputModel, description.getOutputFileTypes());
		fillParametersTable(description.getParametersDescription());
	}

	private void clearAlgorithmDetails() {
		setText(fieldName, "");
		setText(fieldCategory, "");
		setText(fieldType, "");
		setText(fieldAuthors, "");
		setText(fieldDoc, "");
		buttonWeb.setEnabled(false);

		listInputModel.clear();
		listOutputModel.clear();
		parametersModel.setRowCount(0);
	}

	private void updateHighlightButtons() {
		boolean algorithmSelected = tree.getSelectedAlgorithmDescription() != null;
		buttonHighlightSameInOut.setEnabled(algorithmSelected);
		buttonHighlightSameInOutParams.setEnabled(algorithmSelected);
		buttonRemoveHighlight.setEnabled(tree.isActivatedHighlight());
	}

	private void updateTreeTitle(int highlightedCount) {
		StringBuilder text = new StringBuilder("Choose an algorithm (").append(tree.getAlgorithmCount());
		if (highlightedCount > 0) {
			text.append(", ").append(highlightedCount).append(" highlighted");
		}
		labelTreeTitle.setText(text.append("):").toString());
	}

	private static void setText(JTextField field, String text) {
		field.setText(text == null ? "" : text.trim());
		field.setCaretPosition(0); // show the beginning of long texts
	}

	private static void fillListModel(DefaultListModel<String> model, String[] values) {
		model.clear();
		if (values != null) {
			for (String value : values) {
				model.addElement(value);
			}
		}
	}

	private void fillParametersTable(DescriptionOfParameter[] parameters) {
		parametersModel.setRowCount(0);
		if (parameters == null) {
			return;
		}
		for (DescriptionOfParameter parameter : parameters) {
			if (parameter == null) {
				continue;
			}
			String type = parameter.getParameterType() == null ? "" : parameter.getParameterType().getSimpleName();
			parametersModel.addRow(new Object[] { parameter.getName(), type, parameter.isOptional() ? "yes" : "no" });
		}
	}

	// ---- Helpers -------------------------------------------------------

	/**
	 * Opens a URL in the default web browser, reporting any problem to the user.
	 *
	 * @param url the URL of the web page
	 */
	private void openWebPage(String url) {
		if (url == null || url.trim().isEmpty()) {
			return;
		}
		try {
			if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				throw new UnsupportedOperationException("Opening a web browser is not supported on this platform.");
			}
			Desktop.getDesktop().browse(new URI(url.trim()));
		} catch (IOException | URISyntaxException | UnsupportedOperationException | IllegalArgumentException e) {
			JOptionPane.showMessageDialog(this,
					"Could not open the web page:\n" + url + "\n\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Launches the explorer as a standalone application.
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new AlgorithmExplorer(true));
	}
}