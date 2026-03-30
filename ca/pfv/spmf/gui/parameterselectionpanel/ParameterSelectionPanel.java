package ca.pfv.spmf.gui.parameterselectionpanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
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
 * JPanel that let the user enter parameter values for an algorithm. It is used
 * by the graphical user interface of SPMF.
 * 
 * @author Philippe Fournier-Viger, 2024.
 */

public class ParameterSelectionPanel extends JPanel {

	/** Serial version UID */
	private static final long serialVersionUID = 1L;

	/** The table to display the parameters */
	private JTable parameterTable;
	
	/** The custom table model used to display parameters */
	private ParameterSelectionTableModel tableModel; 
	

	/**
	 * Constructor
	 * 
	 * @param descriptionOfAlgorithm The algorithm for which the parameters will be displayed
	 * @param width the desired width of the panel
	 * @param height the desired heigth of the panel
	 */
	public ParameterSelectionPanel(DescriptionOfAlgorithm descriptionOfAlgorithm) {
		super(new BorderLayout()); // Use a BorderLayout for the panel
		setBorder(null);
		update(descriptionOfAlgorithm);

        setPreferredSize(new Dimension(500, 200));  
        setMinimumSize(new Dimension(500, 200));  
	}

	@SuppressWarnings("serial")
	/**
	 * Method to refresh the panel to display a different algorithm's parameter
	 * @param descriptionOfAlgorithm the description of the algorithm
	 */
	public void update(DescriptionOfAlgorithm descriptionOfAlgorithm) {

		// Initialize on first call
		if (tableModel == null) { 
			
			// Create the table model
			tableModel = new ParameterSelectionTableModel(descriptionOfAlgorithm);
			
			// Create the table
			parameterTable = new JTable(tableModel) {
				@Override
				public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
					Component c = super.prepareRenderer(renderer, row, column);
					if (column != 1) { // Highlight non-editable columns
						c.setBackground(new Color(245, 245, 245));
					} else {
						c.setBackground(Color.WHITE);
					}
					return c;
				}
			};
			parameterTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
			JScrollPane scrollPane = new JScrollPane(parameterTable);
			scrollPane.setBorder(BorderFactory.createEmptyBorder());
			
			// Set the size of the columns
			parameterTable.getColumnModel().getColumn(0).setPreferredWidth(200);
			parameterTable.getColumnModel().getColumn(1).setPreferredWidth(150);
			parameterTable.getColumnModel().getColumn(2).setPreferredWidth(150);
			add(scrollPane, BorderLayout.CENTER); 
			
		} else {
			
			// Update existing table model
			if(descriptionOfAlgorithm != null) {
				tableModel.setData(descriptionOfAlgorithm.getParametersDescription());
			}else {
				tableModel.setData(new DescriptionOfParameter[0]);
			}
			tableModel.fireTableDataChanged(); // Trigger table update
		}
		
		// Load the data
		
		

	}

	/**
	 * Get the values for parameters that have been entered by the user
	 * @return the values as an array of String objects
	 */
	public String[] getParameterValues() {
		return tableModel.getParameterValues();
	}

	public void setParameterValues(String[] parameters) {
		tableModel.setParameterValues(parameters);
	}
}
