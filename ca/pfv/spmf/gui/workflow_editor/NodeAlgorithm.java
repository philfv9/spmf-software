package ca.pfv.spmf.gui.workflow_editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
 * A class that represents a node that is an algorithm, which will be drawn on the DrawPanel of the workflow editor.
 * This class stores information about the selected algorithm like its name and parameters.
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
class NodeAlgorithm extends Node {
	/** The arc width for drawing the node as a rounded rectangle */
	int ARC_WIDTH = 10;
	
	/** The arc height for drawing the node as a rounded rectangle */
	int ARC_HEIGHT = 10;
	
	/** The parameters of the algorithm */
	public String[] parameters = null;
	
	/**
	 * Constructor
	 * @param label
	 * @param x
	 * @param y
	 */
	public NodeAlgorithm(String label, int x, int y) {
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

		int x = getX();
		int y = getY();

		// Set the color based on selection
		Graphics2D g2 = (Graphics2D) g;
		g.setColor(Color.RED);
		if(isSelected) {
			float thickness = 3;
			g2.setStroke(new BasicStroke(thickness));
		}else {
			float thickness = 1;
			g2.setStroke(new BasicStroke(thickness));
		}
		g.fillRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, ARC_WIDTH, ARC_HEIGHT);
		
		g.setColor(Color.BLACK);
		// Draw the rounded rectangle
		g.drawRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, ARC_WIDTH, ARC_HEIGHT);

		// Draw the text, aligned to the center of the rectangle
		g.drawString(name, x - textWidth / 2, y + textHeight / 4);
	}

	@Override
	public String getType() {
		return "Algorithm";
	}

	/**
	 * Update  the information stored about the algorithm
	 * @param algorithmName the name
	 * @param hasOutput true if he has an output file. Otherwise, false.
	 * @param hasInput true if he has an input file. Otherwise, false.
	 */
	public void updateAlgorithmChoice(String algorithmName, boolean hasOutput, boolean hasInput) {
		name = algorithmName;
		group.showInput = hasInput;
		group.showOutput = hasOutput;
		rectangle = null;
	}


	/**
	 * Get the non null parameters of this algorithm
	 * @return the array of non null parameters with the last null parameters being trimmed.
	 */
	public String[] getNonNullParameters() {
	    int nonNullCount = 0;
	    for (String parameter : parameters) {
	        if (parameter != null) {
	            nonNullCount++;
	        }
	    }
	    String[] nonNullParameters = new String[nonNullCount];
	    int index = 0;
	    for (String parameter : parameters) {
	        if (parameter != null) {
	            nonNullParameters[index] = parameter;
	            index++;
	        }
	    }
	    return nonNullParameters;
	}

}