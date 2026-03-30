package ca.pfv.spmf.experimental.bioinformatics;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
/* Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * A simple class to visualize the content of a FASTA file.
* 
 * @author Philipe-Fournier-Viger
 **/
public class FastaViewer extends JFrame {
    /**
	 * serial UID
	 */
	private static final long serialVersionUID = -8629592973530410144L;
	/** The text area for displaying the file content */
	private JTextArea textArea;
	/** The current fasta dataset loaded in memory */
    private FastaDataset dataset;
    /** The status bar */
    private JLabel statusBar;

    public FastaViewer(boolean runAsStandaloneApp) {
        dataset = new FastaDataset();
        createMenuBar();
        createTextArea();
        createStatusBar();
        setTitle("Simple FASTA Dataset Viewer");
        setSize(800, 600);
        if(runAsStandaloneApp) {
        	setDefaultCloseOperation(EXIT_ON_CLOSE);
        }
        setLocationRelativeTo(null);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openMenuItem = new JMenuItem("Open");
        openMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    load(fileChooser.getSelectedFile().getAbsolutePath());
                }
            }
        });

        JMenuItem searchMenuItem = new JMenuItem("Search");
        searchMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                String searchTerm = JOptionPane.showInputDialog("Enter a search term:");
                if (searchTerm != null && !searchTerm.isEmpty()) {
                    searchAndDisplay(searchTerm);
                }
            }
        });

        fileMenu.add(openMenuItem);
        fileMenu.add(searchMenuItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
        // Create the Tools menu
        JMenu toolsMenu = new JMenu("Tasks");

        // Add Counting Codons tool
        JMenuItem countCodonsItem = new JMenuItem("Count Codons");
        countCodonsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                boolean includeDegeneracy = JOptionPane.showConfirmDialog(null, "Include degeneracy?", "Counting Codons", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
                // Call the method to count codons here
                countCodons(includeDegeneracy);
            }
        });

        // Add Counting Kmers tool
        JMenuItem countKmersItem = new JMenuItem("Count Kmers");
        countKmersItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                String kValue = JOptionPane.showInputDialog("Enter the value of k:");
                if (kValue != null && !kValue.isEmpty()) {
                    int k = Integer.parseInt(kValue);
                    // Call the method to count kmers here
                    countKmers(k);
                }
            }
        });

        // Add Counting Top-k Kmers tool
        JMenuItem countTopKKmersItem = new JMenuItem("Count Top-k Kmers");
        countTopKKmersItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                String kValue = JOptionPane.showInputDialog("Enter the value of k for k-mers:");
                String topkValue = JOptionPane.showInputDialog("Enter the value k for top-k:");
                if (kValue != null && !kValue.isEmpty() && topkValue != null && !topkValue.isEmpty()) {
                    int k = Integer.parseInt(kValue);
                    int topk = Integer.parseInt(topkValue);
                    // Call the method to count top-k kmers here
                    countTopKKmers(k, topk);
                }
            }
        });

        // Add items to the Tools menu
        toolsMenu.add(countCodonsItem);
        toolsMenu.add(countKmersItem);
        toolsMenu.add(countTopKKmersItem);

        // Add the Tools menu to the menu bar
        menuBar.add(toolsMenu);
    }

    private void createTextArea() {
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane);
    }

    private void createStatusBar() {
        statusBar = new JLabel("Ready");
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        add(statusBar, BorderLayout.SOUTH);
    }

    private void displaySequences() {
        List<FastaSequenceEntry> sequences = dataset.getSequenceEntries();
        textArea.setText("");
        for (FastaSequenceEntry entry : sequences) {
            textArea.append(">" + entry.getHeader() + "\n" + entry.getSequence() + "\n\n");
        }
        statusBar.setText("Displayed " + sequences.size() + " sequences.");
    }

    private void searchAndDisplay(String searchTerm) {
        List<FastaSequenceEntry> sequences = dataset.getSequenceEntries();
        textArea.setText("");
        for (FastaSequenceEntry entry : sequences) {
            String header = entry.getHeader();
            String sequence = entry.getSequence();
            if (header.contains(searchTerm) || sequence.contains(searchTerm)) {
                // Highlight the found word
                String highlightedHeader = header.replaceAll("(?i)" + searchTerm, "**$0**");
                String highlightedSequence = sequence.replaceAll("(?i)" + searchTerm, "**$0**");
                textArea.append(">" + highlightedHeader + "\n" + highlightedSequence + "\n\n");
            }
        }
        statusBar.setText("Search complete.");
    }

    public void load(String filePath) {
        try {
            dataset = new FastaDataset();
            dataset.loadFile(filePath);
            displaySequences();
            statusBar.setText("Loaded file: " + filePath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error reading file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    // Method to count codons
    private void countCodons(boolean includeDegeneracy) {
        try {
            String output = chooseOutputFilePath();
            if(output != null) { // Ensure the user selected a file
                AlgoCountCodons algo = new AlgoCountCodons();
                algo.runAlgorithm(dataset, output, includeDegeneracy);
                algo.printStats();

                statusBar.setText("Codons counted. Output file: " + output);
                promptToViewOutput(output); // Prompt to view the output file
            } else {
                statusBar.setText("Codon counting cancelled by user.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error processing codons: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

 // Method to count kmers
    private void countKmers(int k) {
        try {
            String output = chooseOutputFilePath();
            if(output != null) { // Ensure the user selected a file
                AlgoCountKMers algo = new AlgoCountKMers();
                algo.runAlgorithm(dataset, k, output);
                algo.printStats();

                statusBar.setText("Kmers counted. Output file: " + output);
                promptToViewOutput(output); // Prompt to view the output file
            } else {
                statusBar.setText("Kmer counting cancelled by user.");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error processing kmers: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Method to count top-k kmers
    private void countTopKKmers(int k, int topk) {
        try {
            String output = chooseOutputFilePath();
            if(output != null) { // Ensure the user selected a file
                AlgoCountTopKMers algo = new AlgoCountTopKMers();
                algo.runAlgorithm(dataset, k, topk, output);
                algo.printStats();

                statusBar.setText("Top-k kmers counted. Output file: " + output);
                promptToViewOutput(output); // Prompt to view the output file
            } else {
                statusBar.setText("Top-k kmer counting cancelled by user.");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error processing top-k kmers: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Helper methods (chooseOutputFilePath and promptToViewOutput) would be the same as provided earlier

    
    private String chooseOutputFilePath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select an output file");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().getAbsolutePath();
        }
        return null; // or handle cancellation
    }
    
 // Method to prompt the user to view the output file and open it if requested
    private void promptToViewOutput(String outputFilePath) {
        int response = JOptionPane.showConfirmDialog(null, "Do you want to view the output file?", "View Output", JOptionPane.YES_NO_OPTION);
        if (response == JOptionPane.YES_OPTION) {
            try {
                // Ensure the desktop is supported on the current platform
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    File outputFile = new File(outputFilePath);
                    desktop.open(outputFile); // Opens the file with the default text editor
                } else {
                    JOptionPane.showMessageDialog(null, "Desktop is not supported on this platform.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error opening file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
