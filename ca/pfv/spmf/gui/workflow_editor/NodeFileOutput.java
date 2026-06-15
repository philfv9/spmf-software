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
 * 
 * Do not remove copyright and license information.
 */

/**
 * Node representing an output file on the workflow draw panel, storing the chosen file path.
 *
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
public class NodeFileOutput extends Node {

    /** The full path of the output file, or null if none has been set. */
    public String outputFile = null;

    /**
     * Creates a new output file node with the given label and position.
     *
     * @param label the initial display label and default output file name.
     * @param x     the initial X position.
     * @param y     the initial Y position.
     */
    public NodeFileOutput(String label, int x, int y) {
        super(label, x, y);
        outputFile = label;
    }

    /**
     * Paints this output file node as a filled rectangle on the given Graphics context.
     *
     * @param g          the Graphics object to draw on.
     * @param isSelected true if the node should be drawn with a thicker border.
     */
    @Override
    void paintNode(Graphics g, boolean isSelected) {
        if (rectangle == null) {
            recalculateRectangle(g);
        }

        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.ORANGE);
        float thickness = isSelected ? 3f : 1f;
        g2.setStroke(new BasicStroke(thickness));
        g.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);

        g.setColor(Color.BLACK);
        g.drawRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        g.drawString(name, x - textWidth / 2, y + textHeight / 4);
    }

    /**
     * Returns the type label for this node.
     *
     * @return the string "Output".
     */
    @Override
    public String getType() {
        return "Output";
    }

    /**
     * Opens a save-file dialog so the user can choose an output file path, then updates this node's name and path.
     *
     * @param parent the parent frame for the dialog.
     */
    public void askUserToReplaceFile(JFrame parent) {
        try {
            String previousPath = PreferencesManager.getInstance().getOutputFilePath();
            File path = null;
            if (previousPath == null) {
                URL main = MainTestApriori_simple_saveToFile.class
                        .getResource("MainTestApriori_saveToFile.class");
                if ("file".equalsIgnoreCase(main.getProtocol())) {
                    path = new File	(main.getPath());
                }
            } else {
                path = new File(previousPath);
            }

            final JFileChooser fc = path != null
                    ? new JFileChooser(path.getAbsolutePath()) : new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int returnVal = fc.showSaveDialog(parent);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                name       = file.getName();
                outputFile = file.getPath();
                rectangle  = null;
                PreferencesManager.getInstance().setOutputFilePath(file.getParent());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while opening the file dialog. ERROR MESSAGE = " + e.toString(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}