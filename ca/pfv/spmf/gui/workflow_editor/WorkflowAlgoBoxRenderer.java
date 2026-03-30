package ca.pfv.spmf.gui.workflow_editor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.BitSet;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 * A custom combo box model for the Workflow editor of SPMF. It has the feature
 * of highlight suggested algorithm in black while showing other algorithms in
 * light gray
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
class WorkflowAlgoBoxRenderer extends JPanel implements ListCellRenderer<Object> {

	/** Serial UID */
	private static final long serialVersionUID = 234234235L;

	/** Panel for the JComboBox */
	JPanel panel;
	/** JLabel object to draw text */
	JLabel textLabel;

	BitSet suggested;
	int cardinality;

	/**
	 * Constructor
	 * 
	 * @param combo a JComboBox
	 */
	public WorkflowAlgoBoxRenderer(JComboBox<?> combo) {
		panel = new JPanel();
		panel.add(this);
		textLabel = new JLabel();
		textLabel.setOpaque(true);
		textLabel.setFont(combo.getFont());
		panel.add(textLabel);

		// ==== Initialize a bit vector for which items will be enabled ===
		cardinality = combo.getItemCount();
		suggested = new BitSet(cardinality);
		suggested.set(1, cardinality, false);
	}

	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
			boolean cellHasFocus) {

		// Set the background color for selection for the selected item
		if (isSelected) {
			setBackground(list.getSelectionBackground());
		} else {
			setBackground(Color.WHITE);
		}
		textLabel.setBackground(getBackground());

		// Get the selected text
		String stringValue = value.toString();

		// Get the current font
		Font f = textLabel.getFont();

		// If it is a category of algorithm
		// We will set the font to red and bold
		if (index > 0 && stringValue.startsWith(" --")) {
			textLabel.setForeground(Color.RED);
			textLabel.setText(stringValue);
			// Set bold
			textLabel.setFont(f.deriveFont(f.getStyle() | Font.BOLD));
		} else if ("".equals(stringValue)) {
			// If it is empty, then just leave it like that
			textLabel.setText(" ");
		} else {
			// Else, it is an algorithm name. We put it in black color without bold
			if (index >= 0 && suggested.get(index) == true) {
				textLabel.setForeground(Color.BLACK);
			} else {
//				System.out.println(index);
				textLabel.setForeground(Color.LIGHT_GRAY);
			}
			textLabel.setText("   " + stringValue);
			// Unbold
			textLabel.setFont(f.deriveFont(f.getStyle() & ~Font.BOLD));
		}
		return textLabel;
	}
}