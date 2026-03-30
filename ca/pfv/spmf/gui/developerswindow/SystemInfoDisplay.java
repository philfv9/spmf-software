package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import ca.pfv.spmf.gui.SortableJTable;
/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * A class to display system information
 * @author Philippe Fournier-Viger
 */
public class SystemInfoDisplay extends JFrame {
	/**
	 * serial UID
	 */
	private static final long serialVersionUID = -6239858712290930028L;

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	public SystemInfoDisplay(boolean runAsStandalone) throws Exception {
		if (runAsStandalone) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		
        // Set up the frame
        setTitle("System and JVM Information");
        setSize(500, 200);
        setLocationRelativeTo(null);

        // Prepare table data
        String[] columnNames = {"Property", "Value"};
        String[][] data = {
        	    {"Java Version", System.getProperty("java.version")},
        	    {"Java Runtime Version", System.getProperty("java.runtime.version")},
        	    {"Java Vendor", System.getProperty("java.vendor")},
        	    {"OS Name", System.getProperty("os.name")},
        	    {"OS Version", System.getProperty("os.version")},
        	    {"OS Architecture", System.getProperty("os.arch")},
        	    {"User's Home Directory", System.getProperty("user.home")},
        	    {"User's Current Working Directory", System.getProperty("user.dir")},
        	    {"Java Class Path", System.getProperty("java.class.path")},
        	    {"Java Library Path", System.getProperty("java.library.path")},
        	    {"PATH", System.getenv("PATH")},
        	    {"TEMP", System.getenv("TEMP")},
        	    {"Java Installation Directory", System.getProperty("java.home")},
//        	    {"Java Vendor URL", System.getProperty("java.vendor.url")},
//        	    {"JVM Specification Version", System.getProperty("java.vm.specification.version")},
//        	    {"JVM Specification Vendor", System.getProperty("java.vm.specification.vendor")},
//        	    {"JVM Specification Name", System.getProperty("java.vm.specification.name")},
//        	    {"JVM Implementation Version", System.getProperty("java.vm.version")},
//        	    {"JVM Implementation Vendor", System.getProperty("java.vm.vendor")},
//        	    {"JVM Implementation Name", System.getProperty("java.vm.name")},
//        	    {"Java Compiler", System.getProperty("java.compiler")},
        	    {"User name", System.getenv("USERNAME")},
        	    {"Computer name", System.getenv("COMPUTERNAME")},
        	    {"Processor ID", System.getenv("PROCESSOR_IDENTIFIER")},
        	    {"Processor Architecture", System.getenv("PROCESSOR_ARCHITECTURE")},
        	    {"Processor Level", System.getenv("PROCESSOR_LEVEL")},
        	    {"Processor count", System.getenv("NUMBER_OF_PROCESSORS")},
        	    {"Total Memory (bytes)", String.valueOf(Runtime.getRuntime().totalMemory())},
        	    {"Free Memory (bytes)", String.valueOf(Runtime.getRuntime().freeMemory())},
        	    {"Max Memory (bytes)", String.valueOf(Runtime.getRuntime().maxMemory())}};


        // Create table model and JTable
        DefaultTableModel model = new DefaultTableModel(data, columnNames);
        JTable table = new SortableJTable();
        table.setModel(model);

        // Add the table to a scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        setSize(500,500);
        table.setFillsViewportHeight(true);

        // Add the scroll pane to the frame
        add(scrollPane, BorderLayout.CENTER);
        
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /** Method for testing **/
    public static void main(String[] args) throws Exception {
    	 SystemInfoDisplay frame = new SystemInfoDisplay(true);
         frame.setVisible(true);
    }
}