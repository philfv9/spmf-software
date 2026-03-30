package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmExplorer;
import ca.pfv.spmf.gui.preferences.PreferencesViewer;
import ca.pfv.spmf.gui.web.WebpageAlgorithmDocViewer;

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
 * JFrame to provide tools for developers.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class DevelopersToolsWindow extends JFrame implements ActionListener {

	/** serial UID */
	private static final long serialVersionUID = 6003542342904279422L;
	/** The main panel */
	private JPanel mainPanel;

	/** The buttons */
	private JButton preferencesButton, findDocButton, simpleAlgorithmButton, webpageAlgorithmButton,
			algorithmExplorerButton, outputInputAlgorithmButton, authorCountButton, categoryCountButton,
			typeCountButton, algorithmByInputButton, algorithmByOutputButton, systemInfoButton, downloadDocumentation;

	/**
	 * Constructor
	 * 
	 * @throws UnsupportedLookAndFeelException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws ClassNotFoundException
	 */
	public DevelopersToolsWindow(boolean runAsStandalone) throws ClassNotFoundException, InstantiationException,
			IllegalAccessException, UnsupportedLookAndFeelException {
		setTitle("SPMF Developers Tools " + Main.SPMF_VERSION);
		setDefaultCloseOperation(runAsStandalone ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);

		mainPanel = new JPanel(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		initializeButtons();
		addComponentsToMainPanel();

		getContentPane().add(mainPanel);
		setSize(850, 500);
		setLocationRelativeTo(null);
		setVisible(true);
	}

	/** Initialize the buttons */
	private void initializeButtons() {
		preferencesButton = createButton("View user preferences in registry", "Open the preferences viewer.");
		findDocButton = createButton("Find broken URLs in SPMF documentation", "Check for invalid documentation links.");
		algorithmExplorerButton = createButton("Algorithm Manager", "Explore and manage algorithms.");
		simpleAlgorithmButton = createButton("View and export list of algorithms", "Display all available algorithms.");
		webpageAlgorithmButton = createButton("View documentation via internal browser", "View algorithm docs.");
		outputInputAlgorithmButton = createButton("All Input/Output Types", "List all I/O types.");
		authorCountButton = createButton("Algorithm count by authors", "View algorithm stats by author.");
		categoryCountButton = createButton("Algorithm count by category", "View algorithm stats by category.");
		typeCountButton = createButton("Algorithm count by internal type", "View algorithm stats by type.");
		algorithmByInputButton = createButton("Algorithms by input type", "Group algorithms by input.");
		algorithmByOutputButton = createButton("Algorithms by output type", "Group algorithms by output.");
		systemInfoButton = createButton("System information", "Display system and Java info.");
		downloadDocumentation = createButton("Download offline documentation to /doc/ folder", "Save docs offline.");
	}

	/**
	 * Create a button
	 * @param text the name
	 * @param tooltip the tooltip text
	 * @return the button
	 */
	private JButton createButton(String text, String tooltip) {
		JButton button = new JButton(text);
		button.setToolTipText(tooltip);
		button.setPreferredSize(new Dimension(300, 30));
		button.addActionListener(this);
		return button;
	}

	/** Add components to the main panel */
	private void addComponentsToMainPanel() {
		JPanel topPanel = new JPanel();
		JLabel labelSPMF = new JLabel(new ImageIcon(MainWindow.class.getResource("spmf.png")));
		topPanel.add(labelSPMF);

		JPanel buttonPanel = new JPanel(new GridLayout(0, 2, 10, 10));
		buttonPanel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(), "Tools", TitledBorder.LEFT, TitledBorder.TOP));

		// Add buttons
		buttonPanel.add(simpleAlgorithmButton);
		buttonPanel.add(outputInputAlgorithmButton);
		buttonPanel.add(algorithmByInputButton);
		buttonPanel.add(algorithmByOutputButton);
		buttonPanel.add(authorCountButton);
		buttonPanel.add(categoryCountButton);
		buttonPanel.add(typeCountButton);
		buttonPanel.add(findDocButton);
		buttonPanel.add(preferencesButton);
		buttonPanel.add(systemInfoButton);
		buttonPanel.add(downloadDocumentation);

		mainPanel.add(topPanel, BorderLayout.NORTH);
		mainPanel.add(buttonPanel, BorderLayout.CENTER);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		try {
			if (source == preferencesButton) {
				new PreferencesViewer().setVisible(true);
			} else if (source == findDocButton) {
				new FindDocBrokenURLsViewer();
			} else if (source == simpleAlgorithmButton) {
				new AlgorithmListExporterWindow(false);
			} else if (source == webpageAlgorithmButton) {
				new WebpageAlgorithmDocViewer();
			} else if (source == algorithmExplorerButton) {
				new AlgorithmExplorer(false);
			} else if (source == outputInputAlgorithmButton) {
				new InputOutputTypeListWindow(false);
			} else if (source == authorCountButton) {
				new AuthorAlgorithmCountWindow(false);
			} else if (source == algorithmByInputButton) {
				new InputTypeListWindow(false);
			}  else if (source == algorithmByOutputButton) {
				new OutputTypeListWindow(false);
			}else if (source == systemInfoButton) {
				new SystemInfoDisplay(false);
			} else if (source == categoryCountButton) {
				new CategoryAlgorithmCountWindow(false);
			} else if (source == typeCountButton) {
				new TypeAlgorithmCountWindow(false);
			} else if (source == downloadDocumentation) {
				DocumentationDownloaderWindow window = new DocumentationDownloaderWindow();
				window.createAndShowGUI();
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Main method
	 */
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException,
			IllegalAccessException, UnsupportedLookAndFeelException {
		new DevelopersToolsWindow(true);
	}

}
