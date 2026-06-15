package ca.pfv.spmf.gui.experiments;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.util.List;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.experiments.scalability.ExperimenterForScalability;
import ca.pfv.spmf.gui.NotifyingThread;
import ca.pfv.spmf.gui.ThreadCompleteListener;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/*
 * This file is copyright (c) 2022 Philippe Fournier-Viger
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

/**
 * Window for running scalability experiments.
 *
 * <p>The user selects:
 * <ol>
 *   <li>One or more algorithms (must share the same parameters)</li>
 *   <li>A single input dataset file</li>
 *   <li>An output directory</li>
 *   <li>Fixed algorithm parameters (no "##")</li>
 *   <li>A list of integer percentages, e.g. "20 40 60 80 100"</li>
 * </ol>
 *
 * <p>{@link ExperimenterForScalability} will generate subset files
 * (first N% of data lines) and run each algorithm on each subset,
 * then write a result table and optionally a LaTeX figure file.
 *
 * @author Philippe Fournier-Viger, 2022
 * @see ExperimenterForScalability
 */
public class ExperimenterScalabilityWindow extends JFrame
        implements ThreadCompleteListener, UncaughtExceptionHandler {

    // ----------------------------------------------------------------
    // Serial UID
    // ----------------------------------------------------------------
    private static final long serialVersionUID = 2151286070078740128L;

    // ----------------------------------------------------------------
    // UI components
    // ----------------------------------------------------------------

    /** Fixed algorithm parameters (space-separated, no "##") */
    private JTextField textFieldParameters;

    /** Time limit per run in seconds */
    private JTextField textFieldTimeLimit;

    /**
     * Space-separated integer percentages, e.g. "20 40 60 80 100".
     * The "%" suffix is accepted but stripped before parsing.
     */
    private JTextField textFieldValues;

    /**
     * Space-separated list of algorithm names selected by the user.
     * Populated by clicking "Add algorithm".
     */
    private JTextField textFieldAlgorithms;

    /** Scrollable text area that shows console output and results */
    private JTextArea textAreaResult;

    /**
     * Background thread used to run the experiment so the EDT stays
     * responsive.
     */
    private static NotifyingThread currentRunningAlgorithmThread = null;

    /** Triggers the experiment */
    private JButton buttonRun;

    /** Absolute path of the single input dataset file */
    private String inputFile = "";

    /** Absolute path of the output directory */
    private String outputDirectory = "";

    /** Absolute path to spmf.jar (may be null → use system default) */
    private String pathToSPMFJar = null;

    /** Shows the short name of the selected input file */
    private JTextField textFieldInputFile;

    /** Shows the short name of the selected output directory */
    private JTextField textFieldOutputDirectory;

    /** Shows / lets the user edit the path to spmf.jar */
    private JTextField textFieldJARPath;

    /** Algorithm drop-down populated from AlgorithmManager */
    private JComboBox<String> comboBoxAlgorithms;

    /** Adds the selected algorithm to the algorithm list */
    JButton buttonAddAlgorithm;

    /** Option: count non-empty lines in each output file */
    JCheckBox checkboxCountLines;

    /** Option: generate a compilable LaTeX / PGFPlots file */
    JCheckBox checkboxPGFPlots;

    /** Timeout converted to milliseconds (default: no limit) */
    private int timeoutMilliseconds;

    /** Opens the SPMF documentation page for this tool */
    JButton buttonHelp;

    // ----------------------------------------------------------------
    // Entry point (for standalone testing)
    // ----------------------------------------------------------------

    /**
     * Launches the window directly (useful during development).
     *
     * @param arg ignored
     * @throws IOException never thrown; signature kept for consistency
     */
    public static void main(String[] arg) throws IOException {
        ExperimenterScalabilityWindow w =
                new ExperimenterScalabilityWindow();
        w.setVisible(true);
        w.setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    // ----------------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------------

    /** Builds the window and wires up all event listeners. */
    public ExperimenterScalabilityWindow() {
        setTitle("Run an experiment (dataset size is varied)");
        setBounds(100, 100, 825, 801);
        setResizable(false);
        getContentPane().setLayout(null);

        // ── Step labels ─────────────────────────────────────────────

        JLabel lblStep1 = new JLabel(
            "Step 1: Select algorithm(s) to be compared "
            + "(Note: must have the same parameters):");
        lblStep1.setBounds(10, 15, 570, 14);
        getContentPane().add(lblStep1);

        JLabel lblStep2 = new JLabel(
            "Step 2: Select an input file:");
        lblStep2.setBounds(10, 123, 193, 14);
        getContentPane().add(lblStep2);

        JLabel lblStep3 = new JLabel(
            "Step 3: Select output directory:");
        lblStep3.setBounds(10, 166, 193, 14);
        getContentPane().add(lblStep3);

        JLabel lblStep4 = new JLabel(
            "Step 4: Provide the algorithm(s) fixed parameter values "
            + "(do NOT use ##):");
        lblStep4.setBounds(10, 208, 781, 14);
        getContentPane().add(lblStep4);

        JLabel lblStep5 = new JLabel(
            "Step 5: Percentages of dataset size to use "
            + "(integer values 1-100, space-separated, e.g. 20 40 60 80 100):");
        lblStep5.setBounds(10, 278, 781, 14);
        getContentPane().add(lblStep5);

        // ── Algorithm combo + text field ────────────────────────────

        comboBoxAlgorithms = new JComboBox<>(new Vector<>());
        comboBoxAlgorithms.setBounds(34, 40, 283, 22);
        comboBoxAlgorithms.setMaximumRowCount(20);
        comboBoxAlgorithms.addItem("");

        AlgorithmManager manager = null;
        try {
            manager = AlgorithmManager.getInstance();
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        if (manager != null) {
            List<String> algorithmList =
                    manager.getListOfAlgorithmsAsString(
                            false, false, false, true, false);
            for (String name : algorithmList) {
                comboBoxAlgorithms.addItem(name);
            }
        }

        comboBoxAlgorithms.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent evt) {
                try {
                    updateUserInterfaceAfterAlgorithmSelection(
                            evt.getItem().toString(),
                            evt.getStateChange() == ItemEvent.SELECTED);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        getContentPane().add(comboBoxAlgorithms);

        textFieldAlgorithms = new JTextField();
        textFieldAlgorithms.setBounds(34, 73, 757, 20);
        textFieldAlgorithms.setColumns(10);
        getContentPane().add(textFieldAlgorithms);

        buttonAddAlgorithm = new JButton("Add algorithm");
        buttonAddAlgorithm.setBounds(327, 40, 176, 23);
        buttonAddAlgorithm.setEnabled(false);
        buttonAddAlgorithm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    addAnAlgorithm();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null,
                            "An error occurred: " + ex.toString(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        getContentPane().add(buttonAddAlgorithm);

        // ── Input file ───────────────────────────────────────────────

        textFieldInputFile = new JTextField();
        textFieldInputFile.setEditable(false);
        textFieldInputFile.setBounds(213, 120, 496, 20);
        textFieldInputFile.setColumns(10);
        getContentPane().add(textFieldInputFile);

        JButton buttonInputFile = new JButton("...");
        buttonInputFile.setBounds(719, 119, 72, 23);
        buttonInputFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                askUserToChooseInputFile();
            }
        });
        getContentPane().add(buttonInputFile);

        // ── Output directory ─────────────────────────────────────────

        textFieldOutputDirectory = new JTextField();
        textFieldOutputDirectory.setEditable(false);
        textFieldOutputDirectory.setBounds(213, 160, 496, 20);
        textFieldOutputDirectory.setColumns(10);
        getContentPane().add(textFieldOutputDirectory);

        JButton buttonOutputDir = new JButton("...");
        buttonOutputDir.setBounds(719, 161, 72, 23);
        buttonOutputDir.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                askUserToChooseOutputDirectory();
            }
        });
        getContentPane().add(buttonOutputDir);

        // ── Fixed parameters ─────────────────────────────────────────

        textFieldParameters = new JTextField();
        textFieldParameters.setBounds(34, 233, 757, 20);
        textFieldParameters.setColumns(10);
        getContentPane().add(textFieldParameters);

        // ── Percentages ──────────────────────────────────────────────

        textFieldValues = new JTextField();
        textFieldValues.setText("20 40 60 80 100");
        textFieldValues.setBounds(34, 299, 757, 20);
        textFieldValues.setColumns(10);
        getContentPane().add(textFieldValues);

        // ── Options section ──────────────────────────────────────────

        JLabel lblOptions = new JLabel("Options:");
        lblOptions.setBounds(10, 350, 781, 14);
        getContentPane().add(lblOptions);

        JLabel lblTimeLimit = new JLabel(
                "Time limit for each run (s): ");
        lblTimeLimit.setBounds(34, 375, 169, 14);
        getContentPane().add(lblTimeLimit);

        textFieldTimeLimit = new JTextField();
        textFieldTimeLimit.setBounds(213, 372, 496, 20);
        textFieldTimeLimit.setColumns(10);
        getContentPane().add(textFieldTimeLimit);

        JLabel lblJarPath = new JLabel(
                "Path to SPMF.jar (optional):");
        lblJarPath.setBounds(34, 399, 169, 14);
        getContentPane().add(lblJarPath);

        textFieldJARPath = new JTextField();
        pathToSPMFJar =
                PreferencesManager.getInstance().getSPMFJarFilePath();
        textFieldJARPath.setText(
                pathToSPMFJar != null ? pathToSPMFJar : "");
        textFieldJARPath.setEditable(false);
        textFieldJARPath.setBounds(213, 396, 496, 20);
        textFieldJARPath.setColumns(10);
        getContentPane().add(textFieldJARPath);

        JButton buttonSPMFJar = new JButton("...");
        buttonSPMFJar.setBounds(719, 395, 72, 23);
        buttonSPMFJar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                askUserToChooseSPMFJarPath();
            }
        });
        getContentPane().add(buttonSPMFJar);

        checkboxCountLines = new JCheckBox(
            "Compare the number of lines in the output of each algorithm");
        checkboxCountLines.setBounds(34, 420, 578, 23);
        getContentPane().add(checkboxCountLines);

        checkboxPGFPlots = new JCheckBox(
            "Save results as LaTeX PGFPlots figures");
        checkboxPGFPlots.setBounds(34, 446, 466, 23);
        getContentPane().add(checkboxPGFPlots);

        // ── Run button ───────────────────────────────────────────────

        buttonRun = new JButton("Run the experiment");
        buttonRun.setBounds(270, 475, 230, 23);
        buttonRun.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runExperiment();
            }
        });
        getContentPane().add(buttonRun);

        // ── Results area ─────────────────────────────────────────────

        JLabel lblResults = new JLabel("Results:");
        lblResults.setBounds(10, 484, 414, 14);
        getContentPane().add(lblResults);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(34, 509, 757, 242);
        getContentPane().add(scrollPane);

        textAreaResult = new JTextArea();
        textAreaResult.setText(" ");
        textAreaResult.setEditable(false);
        scrollPane.setViewportView(textAreaResult);

        // ── Help button ──────────────────────────────────────────────

        buttonHelp = new JButton("");
        buttonHelp.setIcon(new ImageIcon(
            ExperimenterScalabilityWindow.class.getResource(
                "/ca/pfv/spmf/gui/Help24.gif")));
        buttonHelp.setBounds(761, 11, 38, 34);
        buttonHelp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(
                        java.net.URI.create(
                            "https://www.philippe-fournier-viger.com"
                            + "/spmf/ExperimenterPerformance"
                            + "_Scalability.php"));
                } catch (java.io.IOException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        });
        getContentPane().add(buttonHelp);
    }

    // ----------------------------------------------------------------
    // Add algorithm
    // ----------------------------------------------------------------

    /**
     * Appends the currently selected algorithm to the algorithm list
     * text field, after verifying that its mandatory parameters match
     * those already in the list.
     *
     * @throws Exception if the AlgorithmManager cannot be reached
     */
    protected void addAnAlgorithm() throws Exception {

        String algorithms  = textFieldAlgorithms.getText().trim();
        String newAlgorithm =
                ((String) comboBoxAlgorithms.getSelectedItem()).trim();

        if (newAlgorithm.isEmpty()) {
            return;
        }

        // First algorithm in the list → just add it
        if (algorithms.isEmpty()) {
            textFieldAlgorithms.setText(newAlgorithm);
            return;
        }

        String[] existing = algorithms.split("\\s+");

        // Check for duplicates
        for (String a : existing) {
            if (a.equals(newAlgorithm)) {
                return; // Already present - silently ignore
            }
        }

        // Verify that mandatory parameters match those of the first
        // algorithm already in the list
        DescriptionOfAlgorithm first =
                AlgorithmManager.getInstance()
                                .getDescriptionOfAlgorithm(existing[0]);
        DescriptionOfAlgorithm candidate =
                AlgorithmManager.getInstance()
                                .getDescriptionOfAlgorithm(newAlgorithm);

        boolean sameParams = true;

        if (first.getNumberOfMandatoryParameters()
                != candidate.getNumberOfMandatoryParameters()) {
            sameParams = false;
        } else {
            DescriptionOfParameter[] fp =
                    first.getParametersDescription();
            DescriptionOfParameter[] cp =
                    candidate.getParametersDescription();
            for (int i = 0; i < fp.length; i++) {
                if (!fp[i].isOptional) {
                    if (i >= cp.length
                            || fp[i].getParameterType()
                                    != cp[i].getParameterType()
                            || !fp[i].name.equals(cp[i].name)) {
                        sameParams = false;
                        break;
                    }
                }
            }
        }

        if (!sameParams) {
            JOptionPane.showMessageDialog(null,
                "Algorithm \"" + newAlgorithm + "\" does not have the "
                + "same mandatory parameters as \""
                + existing[0] + "\" and cannot be added.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        textFieldAlgorithms.setText(algorithms + " " + newAlgorithm);
    }

    // ----------------------------------------------------------------
    // Console → text area redirect
    // ----------------------------------------------------------------

    /**
     * Redirects bytes written to an {@link OutputStream} into a
     * {@link JTextArea}.
     */
    static class TextAreaOutputStream extends OutputStream {

        private final JTextArea textArea;

        TextAreaOutputStream(JTextArea ta) {
            this.textArea = ta;
        }

        @Override
        public void flush() {
            textArea.repaint();
        }

        @Override
        public void write(int b) {
            textArea.append(
                    new String(new byte[] { (byte) b }));
        }
    }

    // ----------------------------------------------------------------
    // Run experiment
    // ----------------------------------------------------------------

    /**
     * Validates user inputs then launches
     * {@link ExperimenterForScalability#runScalabilityExperiment}
     * inside a background thread so the EDT is not blocked.
     *
     * <p>Key validation steps:
     * <ul>
     *   <li>At least one algorithm must be listed.</li>
     *   <li>An input file must be chosen.</li>
     *   <li>An output directory must be chosen.</li>
     *   <li>Parameters must NOT contain "##".</li>
     *   <li>Each percentage token must be a valid integer 1–100.</li>
     * </ul>
     */
    protected void runExperiment() {

        buttonRun.setEnabled(false);
        textAreaResult.setText("");

        try {
            // ── Algorithm names ──────────────────────────────────────
            String rawAlgorithms =
                    textFieldAlgorithms.getText().trim();
            if (rawAlgorithms.isEmpty()) {
                throw new Exception(
                    "You must add at least one algorithm name "
                    + "(use the drop-down and 'Add algorithm' button).");
            }
            String[] algorithmNames = rawAlgorithms.split("\\s+");

            // ── Input file ───────────────────────────────────────────
            if (inputFile == null || inputFile.isEmpty()) {
                throw new Exception(
                    "You must select an input dataset file (Step 2).");
            }

            // ── Output directory ─────────────────────────────────────
            if (outputDirectory == null || outputDirectory.isEmpty()) {
                throw new Exception(
                    "You must select an output directory (Step 3).");
            }

            // ── Fixed parameters (no "##" allowed) ───────────────────
            String rawParams =
                    textFieldParameters.getText().trim();
            String[] parameters;
            if (rawParams.isEmpty()) {
                parameters = new String[0];
            } else {
                parameters = rawParams.split("\\s+");
            }
            for (String p : parameters) {
                if ("##".equals(p)) {
                    throw new Exception(
                        "Do not use '##' in a scalability experiment.\n"
                        + "Parameters are fixed across all runs.\n"
                        + "Only the size of the dataset subset changes.");
                }
            }

            // ── Percentages ──────────────────────────────────────────
            String rawValues = textFieldValues.getText().trim();
            if (rawValues.isEmpty()) {
                throw new Exception(
                    "You must provide at least one percentage value "
                    + "in Step 5 (e.g. \"20 40 60 80 100\").");
            }

            // Split on whitespace; strip any trailing "%" from each token
            String[] rawTokens = rawValues.split("\\s+");
            String[] percentages = new String[rawTokens.length];
            for (int i = 0; i < rawTokens.length; i++) {
                // Remove "%" suffix if present (user-friendly)
                String token = rawTokens[i].replace("%", "").trim();
                // Validate: must be an integer 1–100
                int pct;
                try {
                    pct = Integer.parseInt(token);
                } catch (NumberFormatException nfe) {
                    throw new Exception(
                        "\"" + rawTokens[i] + "\" is not a valid "
                        + "integer percentage. "
                        + "Please use values like: 20 40 60 80 100");
                }
                if (pct < 1 || pct > 100) {
                    throw new Exception(
                        "Percentage value " + pct
                        + " is out of range. "
                        + "Each value must be between 1 and 100.");
                }
                percentages[i] = token; // clean integer string
            }

            // ── Timeout ──────────────────────────────────────────────
            timeoutMilliseconds = Integer.MAX_VALUE;
            String timeoutStr = textFieldTimeLimit.getText().trim();
            if (!timeoutStr.isEmpty()) {
                try {
                    timeoutMilliseconds =
                            Integer.parseInt(timeoutStr) * 1000;
                } catch (NumberFormatException nfe) {
                    throw new Exception(
                        "Time limit must be a whole number of seconds.");
                }
            }

            // ── Options ──────────────────────────────────────────────
            boolean compareOutputSize =
                    checkboxCountLines.isSelected();
            boolean generatePGFPlots =
                    checkboxPGFPlots.isSelected();

            // ── Create the experimenter ───────────────────────────────
            ExperimenterForScalability experimenter =
                    new ExperimenterForScalability();

            if (pathToSPMFJar != null && !pathToSPMFJar.isEmpty()) {
                experimenter.setSPMFJarFilePath(pathToSPMFJar);
            }

            // ── Redirect console output to the text area ─────────────
            System.setOut(new PrintStream(
                    new TextAreaOutputStream(textAreaResult)));

            // ── Capture finals for the background thread ─────────────
            final String[]  fAlgorithms   = algorithmNames;
            final String[]  fParameters   = parameters;
            final String    fInputFile    = inputFile;
            final String[]  fPercentages  = percentages;
            final String    fOutputDir    = outputDirectory;
            final int       fTimeout      = timeoutMilliseconds;
            final boolean   fCompare      = compareOutputSize;
            final boolean   fLatex        = generatePGFPlots;
            final ExperimenterForScalability fExp = experimenter;

            // ── Launch in a background thread ─────────────────────────
            currentRunningAlgorithmThread = new NotifyingThread() {
                @Override
                public boolean doRun() throws Exception {
                    fExp.runScalabilityExperiment(
                            fAlgorithms,
                            fParameters,
                            fInputFile,
                            fPercentages,
                            fOutputDir,
                            fTimeout,
                            fCompare,
                            false,      // showCommand = false
                            fLatex);
                    return true;
                }
            };

            currentRunningAlgorithmThread.addListener(this);
            currentRunningAlgorithmThread
                    .setUncaughtExceptionHandler(this);
            currentRunningAlgorithmThread.start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "An error occurred while trying to run the experiment."
                + "\n\nERROR: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            buttonRun.setEnabled(true);
        }
    }

    // ----------------------------------------------------------------
    // File / directory choosers
    // ----------------------------------------------------------------

    /** Opens a file chooser for the input dataset file. */
    private void askUserToChooseInputFile() {
        try {
            File startPath = resolveStartPath(
                    PreferencesManager.getInstance().getInputFilePath());

            JFileChooser fc = startPath != null
                    ? new JFileChooser(startPath.getAbsolutePath())
                    : new JFileChooser();

            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                textFieldInputFile.setText(file.getName());
                inputFile = file.getPath();
                PreferencesManager.getInstance()
                        .setInputFilePath(file.getParent());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Error opening the input file dialog.\n" + e,
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Opens a file chooser for the spmf.jar path. */
    protected void askUserToChooseSPMFJarPath() {
        try {
            File startPath = resolveStartPath(
                    PreferencesManager.getInstance()
                                      .getSPMFJarFilePath());

            JFileChooser fc = startPath != null
                    ? new JFileChooser(startPath.getAbsolutePath())
                    : new JFileChooser();

            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                pathToSPMFJar = file.getPath();
                textFieldJARPath.setText(pathToSPMFJar);
                PreferencesManager.getInstance()
                        .setSPMFJarFilePath(pathToSPMFJar);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Error opening the spmf.jar dialog.\n" + e,
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Opens a directory chooser for the output directory. */
    private void askUserToChooseOutputDirectory() {
        try {
            File startPath = resolveStartPath(
                    PreferencesManager.getInstance()
                                      .getExperimentDirectoryPath());

            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setAcceptAllFileFilterUsed(false);
            if (startPath != null) {
                fc.setCurrentDirectory(startPath);
            }

            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                textFieldOutputDirectory.setText(file.getName());
                outputDirectory = file.getAbsolutePath();
                PreferencesManager.getInstance()
                        .setExperimentDirectoryPath(outputDirectory);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Error opening the output directory dialog.\n" + e,
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Returns a {@link File} for the given path string, or falls back
     * to the SPMF examples directory, or {@code null} if neither is
     * available.
     */
    private File resolveStartPath(String savedPath) {
        if (savedPath != null) {
            return new File(savedPath);
        }
        try {
            URL url = MainTestApriori_simple_saveToFile.class
                    .getResource("MainTestApriori_saveToFile.class");
            if (url != null
                    && "file".equalsIgnoreCase(url.getProtocol())) {
                return new File(url.getPath());
            }
        } catch (Exception ignored) {
            // Fall through to null
        }
        return null;
    }

    // ----------------------------------------------------------------
    // Thread callbacks
    // ----------------------------------------------------------------

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        buttonRun.setEnabled(true);
    }

    @Override
    public void notifyOfThreadComplete(Thread thread, boolean succeed) {
        buttonRun.setEnabled(true);
    }

    // ----------------------------------------------------------------
    // Algorithm combo-box handler
    // ----------------------------------------------------------------

    /**
     * Enables or disables the "Add algorithm" button depending on
     * whether a valid algorithm is currently selected in the combo box.
     *
     * @param algorithmName name of the item that changed state
     * @param isSelected    {@code true} when the item was just selected
     * @throws Exception if the AlgorithmManager cannot be reached
     */
    protected void updateUserInterfaceAfterAlgorithmSelection(
            String algorithmName, boolean isSelected) throws Exception {

        if (isSelected) {
            AlgorithmManager mgr = AlgorithmManager.getInstance();
            DescriptionOfAlgorithm alg =
                    mgr.getDescriptionOfAlgorithm(algorithmName);
            buttonAddAlgorithm.setEnabled(alg != null);
        } else {
            buttonAddAlgorithm.setEnabled(false);
        }
    }
}