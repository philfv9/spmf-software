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
 *
 * Do not remove copyright and license information.
 */

/**
 * Node representing an algorithm step on the workflow draw panel, storing the algorithm name and parameters.
 *
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
public class NodeAlgorithm extends Node {

    /** Arc width used when drawing the rounded-rectangle border. */
    int ARC_WIDTH = 10;

    /** Arc height used when drawing the rounded-rectangle border. */
    int ARC_HEIGHT = 10;

    /** The parameter values for this algorithm, or null if none have been set. */
    public String[] parameters = null;

    /**
     * Creates a new algorithm node with the given label and position.
     *
     * @param label the algorithm name to display.
     * @param x     the initial X position.
     * @param y     the initial Y position.
     */
    public NodeAlgorithm(String label, int x, int y) {
        super(label, x, y);
    }

    /**
     * Paints this algorithm node as a rounded rectangle on the given Graphics context.
     *
     * @param g          the Graphics object to draw on.
     * @param isSelected true if the node should be drawn with a thicker border.
     */
    @Override
    void paintNode(Graphics g, boolean isSelected) {
        if (rectangle == null) {
            recalculateRectangle(g);
        }

        int x = getX();
        int y = getY();

        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.RED);
        float thickness = isSelected ? 3f : 1f;
        g2.setStroke(new BasicStroke(thickness));
        g.fillRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height,
                ARC_WIDTH, ARC_HEIGHT);

        g.setColor(Color.BLACK);
        g.drawRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height,
                ARC_WIDTH, ARC_HEIGHT);
        g.drawString(name, x - textWidth / 2, y + textHeight / 4);
    }

    /**
     * Returns the type label for this node.
     *
     * @return the string "Algorithm".
     */
    @Override
    public String getType() {
        return "Algorithm";
    }

    /**
     * Updates the algorithm name and adjusts the owning group's output visibility flag.
     * The input visibility flag is only set if the group was originally created with a visible input node.
     *
     * @param algorithmName the new algorithm name.
     * @param hasOutput     true if the algorithm produces an output file.
     * @param hasInput      true if the algorithm requires an input file.
     */
    public void updateAlgorithmChoice(String algorithmName, boolean hasOutput, boolean hasInput) {
        name = algorithmName;
        // Only update showInput if this group originally had an input node (i.e., it's a root or explicitly has input)
        // Child nodes always receive input from parent's output, so showInput must remain false
        if (group.nodeInput != null) {
            group.showInput = hasInput;
        }
        group.showOutput = hasOutput;
        rectangle = null;
    }

    /**
     * Returns a trimmed copy of the parameters array containing only non-null entries.
     *
     * @return an array of non-null parameter strings.
     */
    public String[] getNonNullParameters() {
        int nonNullCount = 0;
        for (String parameter : parameters) {
            if (parameter != null) nonNullCount++;
        }
        String[] nonNullParameters = new String[nonNullCount];
        int index = 0;
        for (String parameter : parameters) {
            if (parameter != null) nonNullParameters[index++] = parameter;
        }
        return nonNullParameters;
    }
}