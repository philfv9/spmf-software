package ca.pfv.spmf.gui.workflow_editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.net.URL;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;
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
 * A class that represents a node that is an input file, which will be drawn on the DrawPanel of the workflow editor
 * This class stores information about the input file such as its path.
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
class NodeFileInput extends Node {
	
	/** The input file name */
	public String inputFile = null; 

	/** Constructor
	 * 
	 * @param label the name of the node
	 * @param x     the X position
	 * @param y     the Y position
	 */
	public NodeFileInput(String label, int x, int y) {
		super(label, x, y);
	}

	/**
	 * Method to paint this node on a Panel
	 * 
	 * @param g          the Graphics object on which the node should be drawn
	 * @param isSelected whether the node is selected or not
	 */
	void paintNode(Graphics g, boolean isSelected) {
		if (rectangle == null) {
			recalculateRectangle(g);
		}

		// Set the color based on selection
		Graphics2D g2 = (Graphics2D) g;
		g.setColor(Color.ORANGE);
		if(isSelected) {
			float thickness = 3;
			g2.setStroke(new BasicStroke(thickness));
		}else {
			float thickness = 1;
			g2.setStroke(new BasicStroke(thickness));
		}
		g.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
		
		g.setColor(Color.BLACK);
		// Draw the rounded rectangle
		g.drawRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);


		// Draw the text, aligned to the center of the rectangle
		g.drawString(name, x - textWidth / 2, y + textHeight / 4);
	}

	/**
	 * 
	 * @param parent 
	 * @return true if some update is made
	 */
	public void askUserToChooseFile(JFrame parent) {
		try {
			// Determine the path and preference based on input or output
			String previousPath = PreferencesManager.getInstance().getInputFilePath();
			File path = null;
			if (previousPath == null) {
				URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
				if ("file".equalsIgnoreCase(main.getProtocol())) {
					path = new File(main.getPath());
				}
			} else {
				path = new File(previousPath);
			}

			// Create a file chooser
			final JFileChooser fc = path != null ? new JFileChooser(path.getAbsolutePath()) : new JFileChooser();

			// Show dialog based on input or output
			int returnVal = fc.showOpenDialog(parent);
 
			// Process the result of the file chooser
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				//*************************************
				String fileName = file.getName();
				name = fileName;
				inputFile = file.getPath();
				rectangle = null;
				//*************************************
				PreferencesManager.getInstance().setInputFilePath(file.getParent());
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occurred while opening the file dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public String getType() {
		return "Input";
	}

}