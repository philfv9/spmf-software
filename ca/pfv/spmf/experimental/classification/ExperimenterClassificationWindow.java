package ca.pfv.spmf.experimental.classification;

/*
 * This file is copyright (c) 2024 Philippe Fournier-Viger
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
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.experimental.classification.ExperimenterForClassification;
import ca.pfv.spmf.gui.NotifyingThread;
import ca.pfv.spmf.gui.ThreadCompleteListener;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * This class is a window that can be used to launch classification experiments
 * to compare the performance of one or more classification algorithms using
 * either k-fold cross validation or holdout validation.
 *
 * @author Philippe Fournier-Viger, 2024
 * @see ExperimenterForClassification
 */
public class ExperimenterClassificationWindow extends JFrame
        implements ThreadCompleteListener, UncaughtExceptionHandler {

    /** serial UID */
    private static final long serialVersionUID = 3151286070078740128L;

    /** Text area to show results */
    private JTextArea textAreaResult;

    /** Text field for the input dataset file */
    private JTextField textFieldInputFile;

    /** Text field for the output directory */
    private JTextField textFieldOutputDirectory;

    /** Text field for the target class name */
    private JTextField textFieldTargetClass;

    /** Text field for the holdout percentage */
    private JTextField textFieldHoldoutPercentage;

    /** Text field for the k-fold count */
    private JTextField textFieldKFold;

    /** Radio button to select holdout validation */
    private JRadioButton radioButtonHoldout;

    /** Radio button to select k-fold cross validation */
    private JRadioButton radioButtonKFold;

    /** Button to run the experiment */
    private JButton buttonRun;

    /** Button to add an algorithm to the list */
    private JButton buttonAddAlgorithm;

    /** Button to remove the selected algorithm from the list */
    private JButton buttonRemoveAlgorithm;

    /** Help button */
    private JButton buttonHelp;

    /** List model for storing selected algorithms */
    private DefaultListModel<String> listModelAlgorithms;

    /** JList to display selected algorithms */
    private JList<String> listAlgorithms;

    /** Combo box (as a JList) to pick available algorithms */
    private JList<String> listAvailableAlgorithms;

    /** Model for the available algorithms list */
    private DefaultListModel<String> listModelAvailable;

    /** The input file path */
    private String inputFile = "";

    /** The output directory path */
    private String outputDirectory = "";

    /**
     * A thread that is used to run the experiment so that the GUI will not freeze
     */
    private static NotifyingThread currentRunningAlgorithmThread = null;

    /**
     * A main method to directly launch this tool.
     *
     * @param arg arguments (should be empty)
     * @throws IOException if some error occurs
     */
    public static void main(String[] arg) throws IOException {
        ExperimenterClassificationWindow window = new ExperimenterClassificationWindow();
        window.setVisible(true);
        window.setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    /**
     * Constructor
     */
    public ExperimenterClassificationWindow() {
        setTitle("Run a Classification Experiment");
        setSize(860, 820);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setLayout(null);

        // ===== Step 1: Dataset =====
        JLabel lblStep1 = new JLabel("Step 1: Select the dataset file:");
        lblStep1.setBounds(10, 15, 300, 14);
        getContentPane().add(lblStep1);

        textFieldInputFile = new JTextField();
        textFieldInputFile.setEditable(false);
        textFieldInputFile.setBounds(213, 35, 496, 20);
        getContentPane().add(textFieldInputFile);

        JButton buttonInputFile = new JButton("...");
        buttonInputFile.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                askUserToChooseInputFile();
            }
        });
        buttonInputFile.setBounds(719, 34, 72, 23);
        getContentPane().add(buttonInputFile);

        // ===== Step 2: Target class =====
        JLabel lblStep2 = new JLabel("Step 2: Enter the target class attribute name:");
        lblStep2.setBounds(10, 65, 300, 14);
        getContentPane().add(lblStep2);

        textFieldTargetClass = new JTextField();
        textFieldTargetClass.setText("class");
        textFieldTargetClass.setBounds(213, 83, 496, 20);
        getContentPane().add(textFieldTargetClass);

        // ===== Step 3: Output directory =====
        JLabel lblStep3 = new JLabel("Step 3: Select the output directory:");
        lblStep3.setBounds(10, 113, 300, 14);
        getContentPane().add(lblStep3);

        textFieldOutputDirectory = new JTextField();
        textFieldOutputDirectory.setEditable(false);
        textFieldOutputDirectory.setBounds(213, 131, 496, 20);
        getContentPane().add(textFieldOutputDirectory);

        JButton buttonOutputDir = new JButton("...");
        buttonOutputDir.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                askUserToChooseOutputDirectory();
            }
        });
        buttonOutputDir.setBounds(719, 130, 72, 23);
        getContentPane().add(buttonOutputDir);

        // ===== Step 4: Select algorithms =====
        JLabel lblStep4 = new JLabel("Step 4: Select classification algorithm(s) to compare:");
        lblStep4.setBounds(10, 163, 400, 14);
        getContentPane().add(lblStep4);

        // Available algorithms list (left)
        JLabel lblAvailable = new JLabel("Available algorithms:");
        lblAvailable.setBounds(10, 183, 200, 14);
        getContentPane().add(lblAvailable);

        listModelAvailable = new DefaultListModel<String>();
        listAvailableAlgorithms = new JList<String>(listModelAvailable);
        listAvailableAlgorithms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollAvailable = new JScrollPane(listAvailableAlgorithms);
        scrollAvailable.setBounds(10, 200, 360, 150);
        getContentPane().add(scrollAvailable);

        // Populate the available algorithms list
        populateAvailableAlgorithms();

        // Selected algorithms list (right)
        JLabel lblSelected = new JLabel("Selected algorithms:");
        lblSelected.setBounds(430, 183, 200, 14);
        getContentPane().add(lblSelected);

        listModelAlgorithms = new DefaultListModel<String>();
        listAlgorithms = new JList<String>(listModelAlgorithms);
        listAlgorithms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollSelected = new JScrollPane(listAlgorithms);
        scrollSelected.setBounds(430, 200, 360, 150);
        getContentPane().add(scrollSelected);

        // Add algorithm button
        buttonAddAlgorithm = new JButton("Add >>");
        buttonAddAlgorithm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addSelectedAlgorithm();
            }
        });
        buttonAddAlgorithm.setBounds(385, 215, 40, 23);
        getContentPane().add(buttonAddAlgorithm);

        // Remove algorithm button
        buttonRemoveAlgorithm = new JButton("<< Remove");
        buttonRemoveAlgorithm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeSelectedAlgorithm();
            }
        });
        buttonRemoveAlgorithm.setBounds(375, 248, 55, 23);
        getContentPane().add(buttonRemoveAlgorithm);

        // ===== Step 5: Validation method =====
        JLabel lblStep5 = new JLabel("Step 5: Choose validation method:");
        lblStep5.setBounds(10, 365, 300, 14);
        getContentPane().add(lblStep5);

        radioButtonHoldout = new JRadioButton("Holdout validation");
        radioButtonHoldout.setBounds(10, 385, 200, 23);
        radioButtonHoldout.setSelected(true);
        getContentPane().add(radioButtonHoldout);

        radioButtonKFold = new JRadioButton("K-Fold cross validation");
        radioButtonKFold.setBounds(10, 410, 200, 23);
        getContentPane().add(radioButtonKFold);

        // Group radio buttons so only one can be selected at a time
        ButtonGroup groupValidation = new ButtonGroup();
        groupValidation.add(radioButtonHoldout);
        groupValidation.add(radioButtonKFold);

        // Holdout percentage
        JLabel lblPercentage = new JLabel("Holdout training percentage (e.g. 0.5 for 50%):");
        lblPercentage.setBounds(230, 388, 340, 14);
        getContentPane().add(lblPercentage);

        textFieldHoldoutPercentage = new JTextField();
        textFieldHoldoutPercentage.setText("0.5");
        textFieldHoldoutPercentage.setBounds(590, 385, 200, 20);
        getContentPane().add(textFieldHoldoutPercentage);

        // K-fold count
        JLabel lblKFold = new JLabel("Number of folds k (e.g. 10):");
        lblKFold.setBounds(230, 413, 340, 14);
        getContentPane().add(lblKFold);

        textFieldKFold = new JTextField();
        textFieldKFold.setText("10");
        textFieldKFold.setBounds(590, 410, 200, 20);
        getContentPane().add(textFieldKFold);

        // ===== Run button =====
        buttonRun = new JButton("Run the experiment");
        buttonRun.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runExperiment();
            }
        });
        buttonRun.setBounds(300, 450, 230, 23);
        getContentPane().add(buttonRun);

        // ===== Results area =====
        JLabel lblResults = new JLabel("Results:");
        lblResults.setBounds(10, 485, 200, 14);
        getContentPane().add(lblResults);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(10, 505, 820, 265);
        getContentPane().add(scrollPane);

        textAreaResult = new JTextArea();
        textAreaResult.setText(" ");
        textAreaResult.setEditable(false);
        scrollPane.setViewportView(textAreaResult);

        // ===== Help button =====
        buttonHelp = new JButton("");
        buttonHelp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(
                            java.net.URI.create(
                                    "https://www.philippe-fournier-viger.com/spmf/ClassificationExperimenter.php"));
                } catch (java.io.IOException exception) {
                    System.out.println(exception.getMessage());
                }
            }
        });
        buttonHelp.setIcon(new ImageIcon(
                ExperimenterClassificationWindow.class.getResource("/ca/pfv/spmf/gui/Help24.gif")));
        buttonHelp.setBounds(800, 11, 38, 34);
        getContentPane().add(buttonHelp);
    }

    /**
     * Populate the list of available classification algorithms by scanning
     * the classifiers package using reflection.
     */
    private void populateAvailableAlgorithms() {
        try {
            // Use the ExperimenterForClassification helper to get available algorithms
            List<String> algorithmNames = ExperimenterForClassification.getAvailableClassificationAlgorithms();
            for (String name : algorithmNames) {
                listModelAvailable.addElement(name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add the algorithm currently selected in the available list to the
     * selected algorithms list (if not already present).
     */
    private void addSelectedAlgorithm() {
        String selected = listAvailableAlgorithms.getSelectedValue();
        if (selected == null || selected.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Please select an algorithm from the available list.",
                    "No Algorithm Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Check if already added
        if (!listModelAlgorithms.contains(selected)) {
            listModelAlgorithms.addElement(selected);
        }
    }

    /**
     * Remove the algorithm currently selected in the selected list.
     */
    private void removeSelectedAlgorithm() {
        int index = listAlgorithms.getSelectedIndex();
        if (index >= 0) {
            listModelAlgorithms.remove(index);
        }
    }

    /**
     * Run the classification experiment when the user clicks the "Run experiment"
     * button.
     */
    protected void runExperiment() {
        // Deactivate the run button while running
        buttonRun.setEnabled(false);
        textAreaResult.setText("");

        try {
            // Check that at least one algorithm is selected
            if (listModelAlgorithms.isEmpty()) {
                throw new Exception("You must select at least one classification algorithm.");
            }

            // Build the array of algorithm names
            String[] algorithmNames = new String[listModelAlgorithms.size()];
            for (int i = 0; i < listModelAlgorithms.size(); i++) {
                algorithmNames[i] = listModelAlgorithms.get(i);
            }

            // Check that an input file has been selected
            if ("".equals(inputFile)) {
                throw new Exception("You must select a dataset file.");
            }

            // Check that an output directory has been selected
            if ("".equals(outputDirectory)) {
                throw new Exception("You must select an output directory.");
            }

            // Get the target class name
            String targetClassName = textFieldTargetClass.getText();
            if (targetClassName == null || targetClassName.isEmpty()) {
                throw new Exception("You must enter the target class attribute name.");
            }

            // Determine the validation method and its parameter
            final boolean useKFold = radioButtonKFold.isSelected();
            double holdoutPercentage = 0.5;
            int kFoldCount = 10;

            if (useKFold) {
                // Parse k
                String kText = textFieldKFold.getText();
                try {
                    kFoldCount = Integer.parseInt(kText);
                    if (kFoldCount < 2) {
                        throw new Exception("K must be 2 or more.");
                    }
                } catch (NumberFormatException nfe) {
                    throw new Exception("K must be a valid integer.");
                }
            } else {
                // Parse holdout percentage
                String percText = textFieldHoldoutPercentage.getText();
                try {
                    holdoutPercentage = Double.parseDouble(percText);
                    if (holdoutPercentage <= 0 || holdoutPercentage >= 1) {
                        throw new Exception("Holdout percentage must be between 0 and 1 (exclusive).");
                    }
                } catch (NumberFormatException nfe) {
                    throw new Exception("Holdout percentage must be a valid decimal number.");
                }
            }

            // Redirect console output to the result text area
            System.setOut(new PrintStream(new TextAreaOutputStream(textAreaResult)));

            // Create the experimenter
            ExperimenterForClassification experimenter = new ExperimenterForClassification();

            // Final copies for use in the thread
            final String[] finalAlgorithmNames = algorithmNames;
            final String finalInputFile = inputFile;
            final String finalOutputDirectory = outputDirectory;
            final String finalTargetClassName = targetClassName;
            final double finalHoldoutPercentage = holdoutPercentage;
            final int finalKFoldCount = kFoldCount;

            // Create a thread to run the experiment without freezing the GUI
            currentRunningAlgorithmThread = new NotifyingThread() {
                @Override
                public boolean doRun() throws Exception {
                    if (useKFold) {
                        experimenter.runKFoldExperiment(finalAlgorithmNames, finalInputFile,
                                finalTargetClassName, finalKFoldCount, finalOutputDirectory);
                    } else {
                        experimenter.runHoldoutExperiment(finalAlgorithmNames, finalInputFile,
                                finalTargetClassName, finalHoldoutPercentage, finalOutputDirectory);
                    }
                    return true;
                }
            };

            // The main thread will listen for the completion of the algorithm
            currentRunningAlgorithmThread.addListener(this);
            // The main thread will also listen for exceptions generated by the algorithm
            currentRunningAlgorithmThread.setUncaughtExceptionHandler(this);
            // Run the thread
            currentRunningAlgorithmThread.start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while trying to run the experiment. ERROR MESSAGE = " + e.toString(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            buttonRun.setEnabled(true);
        }
    }

    /**
     * Ask the user to choose the input dataset file.
     */
    private void askUserToChooseInputFile() {
        try {
            File path;
            String previousPath = PreferencesManager.getInstance().getInputFilePath();
            if (previousPath == null) {
                URL main = MainTestApriori_simple_saveToFile.class
                        .getResource("MainTestApriori_saveToFile.class");
                if (!"file".equalsIgnoreCase(main.getProtocol())) {
                    path = null;
                } else {
                    path = new File(main.getPath());
                }
            } else {
                path = new File(previousPath);
            }

            final JFileChooser fc;
            if (path != null) {
                fc = new JFileChooser(path.getAbsolutePath());
            } else {
                fc = new JFileChooser();
            }

            int returnVal = fc.showOpenDialog(ExperimenterClassificationWindow.this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                textFieldInputFile.setText(file.getName());
                inputFile = file.getPath();
                if (fc.getSelectedFile() != null) {
                    PreferencesManager.getInstance().setInputFilePath(fc.getSelectedFile().getParent());
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while opening the input file dialog. ERROR MESSAGE = " + e.toString(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Ask the user to choose the output directory.
     */
    private void askUserToChooseOutputDirectory() {
        try {
            File path;
            String previousPath = PreferencesManager.getInstance().getExperimentDirectoryPath();
            if (previousPath == null) {
                URL main = MainTestApriori_simple_saveToFile.class
                        .getResource("MainTestApriori_saveToFile.class");
                if (!"file".equalsIgnoreCase(main.getProtocol())) {
                    path = null;
                } else {
                    path = new File(main.getPath());
                }
            } else {
                path = new File(previousPath);
            }

            final JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setAcceptAllFileFilterUsed(false);
            if (path != null) {
                fc.setCurrentDirectory(new File(path.getAbsolutePath()));
            }

            int returnVal = fc.showSaveDialog(ExperimenterClassificationWindow.this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                textFieldOutputDirectory.setText(file.getName());
                outputDirectory = file.getAbsolutePath();
                if (fc.getSelectedFile() != null) {
                    PreferencesManager.getInstance()
                            .setExperimentDirectoryPath(fc.getSelectedFile().getAbsolutePath());
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while opening the output directory dialog. ERROR MESSAGE = " + e.toString(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // Re-enable the run button if an exception occurs
        buttonRun.setEnabled(true);
    }

    @Override
    public void notifyOfThreadComplete(Thread thread, boolean succeed) {
        // Re-enable the run button when the thread finishes
        buttonRun.setEnabled(true);
    }

    /**
     * Inner class used to forward the console output to the result text area.
     *
     * @author Philippe Fournier-Viger
     */
    static class TextAreaOutputStream extends OutputStream {

        /** The JTextArea where output is appended */
        JTextArea textArea;

        /**
         * Constructor
         *
         * @param textArea a JTextArea
         */
        public TextAreaOutputStream(JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void flush() {
            textArea.repaint();
        }

        @Override
        public void write(int b) {
            textArea.append(new String(new byte[]{(byte) b}));
        }
    }
}