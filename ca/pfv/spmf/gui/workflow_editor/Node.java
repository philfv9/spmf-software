package ca.pfv.spmf.gui.workflow_editor;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;

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
 * Abstract base class for a labelled, positioned node drawn on the workflow draw panel.
 *
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
public abstract class Node {

    /** Padding in pixels added around the label text when drawing the node. */
    final int TEXT_PADDING = 5;

    /** The display label of this node. */
    public String name;

    /** The X position of this node's centre. */
    protected int x;

    /** The Y position of this node's centre. */
    protected int y;

    /** The bounding rectangle used for mouse-click detection; null until first paint. */
    Rectangle rectangle = null;

    /** The pixel width of the label text, measured during painting. */
    int textWidth;

    /** The pixel height of the label text, measured during painting. */
    int textHeight;

    /** The group that this node belongs to. */
    GroupOfNodes group = null;

    /**
     * Creates a new node with the given label and position.
     *
     * @param label the display label of the node.
     * @param x     the initial X position.
     * @param y     the initial Y position.
     */
    public Node(String label, int x, int y) {
        this.name = label;
        this.x    = x;
        this.y    = y;
    }

    /**
     * Returns the X position of this node's centre.
     *
     * @return the X coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the Y position of this node's centre.
     *
     * @return the Y coordinate.
     */
    public int getY() {
        return y;
    }

    /**
     * Paints this node on the given Graphics context.
     *
     * @param g          the Graphics object to draw on.
     * @param isSelected true if the node should be drawn in its selected style.
     */
    void paintNode(Graphics g, boolean isSelected) {
        if (isSelected) {
            g.setColor(Color.RED);
        } else {
            g.setColor(Color.BLUE);
        }
        g.fillOval(x - 15, y - 15, 30, 30);
        g.setColor(Color.WHITE);
        g.drawString(name, x - 5, y + 5);
    }

    /**
     * Returns a string identifying the type of this node.
     *
     * @return the type label.
     */
    public abstract String getType();

    /**
     * Registers the group that this node belongs to.
     *
     * @param group the owning GroupOfNodes.
     */
    public void setGroup(GroupOfNodes group) {
        this.group = group;
    }

    /**
     * Recalculates the bounding rectangle of this node based on the current font metrics.
     *
     * @param g the Graphics object used to obtain font metrics.
     */
    void recalculateRectangle(Graphics g) {
        FontMetrics fm = g.getFontMetrics();
        textWidth  = fm.stringWidth(name);
        textHeight = fm.getHeight();

        int rectWidth  = textWidth  + TEXT_PADDING * 2;
        int rectHeight = textHeight + TEXT_PADDING / 2;

        rectangle = new Rectangle(
                x - rectWidth  / 2 - (TEXT_PADDING / 2),
                y - rectHeight / 2 - (TEXT_PADDING / 2),
                rectWidth  + TEXT_PADDING,
                rectHeight + TEXT_PADDING);
    }

    /**
     * Returns true if the given point falls within this node's bounding rectangle.
     *
     * @param x the point X value.
     * @param y the point Y value.
     * @return true if the point is inside the node, false otherwise.
     */
    public boolean contains(int x, int y) {
        if (rectangle == null) return false;
        return rectangle.contains(x, y);
    }

    /**
     * Moves this node to a new position and invalidates its bounding rectangle.
     *
     * @param x the new X position.
     * @param y the new Y position.
     */
    public void updatePosition(int x, int y) {
        this.x    = x;
        this.y    = y;
        rectangle = null;
    }
}