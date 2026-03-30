package ca.pfv.spmf.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

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
import ca.pfv.spmf.algorithmmanager.AlgorithmManager;

/**
 * JFrame to provide general information about SPMF
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class AboutWindow extends JDialog {

	/**
	 * default UID
	 */
	private static final long serialVersionUID = 6173164103462475327L;

	/**
	 * Constructor
	 * 
	 * @param window the parent window
	 * @throws Exception if error occurs
	 */
	public AboutWindow(JFrame window) throws Exception {
		super(window);
		setIconImage(Toolkit.getDefaultToolkit()
				.getImage(AboutWindow.class.getResource("/ca/pfv/spmf/gui/icons/About24.gif")));

		setResizable(false);
		setTitle("About SPMF " + Main.SPMF_VERSION);
		// set the layout of the content pane to a new BorderLayout with 10 pixels gaps
		getContentPane().setLayout(new BorderLayout(10, 10));

		JLabel logoLabel = new JLabel("");
		logoLabel.setIcon(new ImageIcon(AboutWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));
		logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		// add the label to the content pane with the NORTH constraint
		getContentPane().add(logoLabel, BorderLayout.NORTH);

		JTextArea textArea = new JTextArea();
		textArea.setText("Thanks for using SPMF version " + Main.SPMF_VERSION + ". This version has "
				+ AlgorithmManager.getInstance().getListOfAlgorithmsAsString(false, false, false, true, false).size()
				+ " algorithms and "
				+ AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true, true, true, false, false).size() + " tools."
				+ System.lineSeparator() + System.lineSeparator()
				+ "SPMF is distributed under the open-source GNU GPL license version 3." + System.lineSeparator()
				+ "This license is available at: <http://www.gnu.org/licenses/>." + System.lineSeparator()
				+ System.lineSeparator()
				+ "SPMF was founded in 2008 by Philippe Fournier-Viger and many persons have contributed to the project."
				+ System.lineSeparator() + System.lineSeparator() + "Click the buttons below for more information:");
		textArea.setEditable(false);
		textArea.setRows(10);
		
		// add the text area to the content pane with the CENTER constraint
		getContentPane().add(textArea, BorderLayout.CENTER);

		// Set the window as modal
		setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);

		JButton btnNewButton_1 = new JButton("Documentation");
		btnNewButton_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openWebPage("http://philippe-fournier-viger.com/spmf/index.php?link=documentation.php");
			}
		});

		JButton btnNewButton_2 = new JButton("Contributors");
		btnNewButton_2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openWebPage("http://philippe-fournier-viger.com/spmf/index.php?link=contributors.php");
			}
		});

		JButton btnNewButton_3 = new JButton("Website");
		btnNewButton_3.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openWebPage("http://www.philippe-fournier-viger.com/spmf/");
			}
		});

		// create a new panel to hold the buttons
		JPanel buttonPanel = new JPanel();
		// set the layout of the panel to a new FlowLayout with center alignment
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		// add the buttons to the panel
		buttonPanel.add(btnNewButton_1);
		buttonPanel.add(btnNewButton_2);
		buttonPanel.add(btnNewButton_3);
		// add the panel to the content pane with the SOUTH constraint
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);

		setSize(680, 300);
		this.setLocationRelativeTo(null);
		// show this window as modal
		setModalityType(ModalityType.APPLICATION_MODAL);
		setModal(true);
	}

	/**
	 * This method open a URL in the default web browser.
	 *
	 * @param url : URL of the webpage
	 */
	private void openWebPage(String url) {
		try {
			java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
		} catch (java.io.IOException e) {
			System.out.println(e.getMessage());
		}
	}
}
