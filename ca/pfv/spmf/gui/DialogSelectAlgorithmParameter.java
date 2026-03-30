package ca.pfv.spmf.gui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextField;

import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
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
 * A class for creating a dialog box to ask the user to choose a value for a
 * parameter.
 */
@SuppressWarnings("serial")
public class DialogSelectAlgorithmParameter extends JDialog {

	/** the text field for the user input */
	private JTextField textField;

	/**
	 * Constructs a new dialog box with the given parameter description.
	 * 
	 * @param paramDescription the description of the parameter to be chosen
	 */
	public DialogSelectAlgorithmParameter(DescriptionOfParameter paramDescription, JFrame parent) {
		// set the title and layout of the dialog
		this.setTitle("Enter a value as \"" + paramDescription.getName() + "\" " + paramDescription.getExample());
		this.setLayout(new FlowLayout());

		// create and add the text field
		textField = new JTextField(20);
		this.add(textField);
		// create and add the button
		JButton button = new JButton("OK");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose(); // close the dialog when the button is clicked
			}
		});
		this.add(button);
		// pack and show the dialog
		this.pack();
		this.setSize(400, 100);
		this.setModal(true);
		this.setLocationRelativeTo(parent);
		this.setVisible(true);
	}

	/**
	 * Returns the user input from the text field.
	 * 
	 * @return the user input as a string
	 */
	public String getUserInput() {
		return textField.getText();
	}
}
