package ca.pfv.spmf.gui.web;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

/*
 * Copyright (c) 2023 Philippe Fournier-Viger
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
 * This is a simple user interface to display the documentation of algorithms in
 * a window using a JEditorPane
 * 
 * @author Philippe Fournier-Viger 2021
 */
@SuppressWarnings("serial")
public class WebpageAlgorithmDocViewer extends JFrame {

	/** The JEditorPane */
	JEditorPane editorPane;

	/** Address bar */
	JTextField addressBar;

	/** ALGORITHM BOX */
	private JComboBox<String> algorithmBox; // added

	/** Default url */
	String defaultUrl = "https://www.philippe-fournier-viger.com/spmf/documentations.php"; // added

	public static void main(String[] args) throws Exception {
		new WebpageAlgorithmDocViewer();
	}

	/**
	 * Constructor
	 * 
	 * @throws Exception
	 */
	public WebpageAlgorithmDocViewer() throws Exception {
		JFrame frame = new JFrame("SPMF Documentation Webpage Viewer");
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		frame.setSize(800, 600);

		addressBar = new JTextField();
		addressBar.setEditable(false);

		editorPane = new JEditorPane();
		editorPane.setEditable(false);

		// Create the bar panel
		JPanel barPanel = new JPanel();
		barPanel.setLayout(new BorderLayout());
//		barPanel.add(addressBar, BorderLayout.CENTER);

		// Create the button panel
		JPanel buttonPanel = new JPanel(); // added
		buttonPanel.add(new JLabel("Choose an algorithm :"));

		// Create the combo box for algorithms
		algorithmBox = new JComboBox<String>(); // added

		// Get the list of algorithms from SPMF
		List<String> algorithms = AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true, true, true, true, true);
		for (String algorithmName : algorithms) {
			if (algorithmName.contains("---")) {
				continue;
			}
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			// Add the algorithm name to the combo box
			algorithmBox.addItem(algorithm.getName());
		}
		// Add an action listener to the combo box
		algorithmBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Get the selected algorithm name
				String algorithmName = (String) algorithmBox.getSelectedItem();
				// Get the description of the algorithm from SPMF
				DescriptionOfAlgorithm algorithm = null;
				try {
					algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				// Get the URL of the documentation of the algorithm
				if (algorithm != null) {
					String url = algorithm.getURLOfDocumentation();
					// Load the URL in the editor pane
					setPage(url);
				}
			}
		});

		// Set the initial selection to the first item
		algorithmBox.setSelectedIndex(0);
		// Add the combo box to the button panel
		buttonPanel.add(algorithmBox);

		barPanel.add(buttonPanel, BorderLayout.WEST); // added

//        frame.getContentPane().add(addressBar, BorderLayout.NORTH);
		frame.getContentPane().add(barPanel, BorderLayout.NORTH);
		frame.getContentPane().add(new JScrollPane(editorPane), BorderLayout.CENTER);
		frame.getContentPane().add(addressBar, BorderLayout.SOUTH); // added
		frame.setVisible(true);

		editorPane.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent event) {
				if (HyperlinkEvent.EventType.ACTIVATED.equals(event.getEventType())) {
					setPage(event.getURL().toString());
				}
			}
		});
	}

	/**
	 * Load a webpage
	 * 
	 * @param url           the url of that webpage
	 * @param goingBackward
	 */
	private void setPage(String url) {
		try {
			editorPane.setPage(url);
			addressBar.setText(url);
		} catch (Exception e) {
			editorPane.setText(
					"Error retrieving webpage. " + System.lineSeparator() + System.lineSeparator() + e.getStackTrace());
//            e.printStackTrace();
		}
	}

}
