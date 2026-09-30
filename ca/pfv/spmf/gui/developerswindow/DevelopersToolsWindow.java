package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmExplorer;
import ca.pfv.spmf.gui.preferences.PreferencesViewer;
import ca.pfv.spmf.gui.web.WebpageAlgorithmDocViewer;

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
 * Do not remove the copyright and license information
 */
/**
 * JFrame to provide tools for developers.
 *
 * @author Philippe Fournier-Viger
 */
public class DevelopersToolsWindow extends JFrame implements ActionListener {

    /** Serial UID */
    private static final long serialVersionUID = 6003542342904279422L;

    /** Preferred button size */
    private static final Dimension BUTTON_SIZE = new Dimension(380, 32);

    /** The main panel */
    private JPanel mainPanel;

    /** Algorithm list and I/O buttons */
    private JButton simpleAlgorithmButton, outputInputAlgorithmButton,
            algorithmByInputButton, algorithmByOutputButton;

    /** Statistics buttons */
    private JButton authorCountButton, categoryCountButton, typeCountButton;

    /**
     * Documentation and browser buttons.
     * <p>
     * {@code extractMappingButton} opens {@link AlgorithmFileMappingExtractorWindow},
     * which scans offline HTML documentation files and extracts the mapping
     * between algorithm names and their example input files.
     * </p>
     * <p>
     * {@code runExamplesButton} opens {@link AlgorithmExampleAutoRunnerWindow},
     * which scans the offline HTML documentation files for command-line
     * examples, extracts the algorithm name, input file name, and actual
     * parameter values used in each example, and then automatically runs
     * every algorithm found, reporting success/failure/timeout in a
     * sortable table and capturing all console output.
     * </p>
     * <p>
     * {@code runMainTestButton} opens {@link MainTestAutoRunnerWindow}, which
     * discovers every class whose name starts with "MainTest" in the
     * {@code ca.pfv.spmf.test} package and automatically runs each one's
     * main() method, reporting success/failure/timeout in a sortable, searchable
     * table with full console output and stack traces available via double-click.
     * </p>
     * <p>
     * {@code extractMappingButton}, {@code runExamplesButton} and
     * {@code runMainTestButton} are <b>hidden by default</b> and only become
     * visible after the user presses the secret shortcut <b>F12</b> while this
     * window is focused. Pressing F12 again hides them. This keeps the UI
     * uncluttered for casual users while keeping the tools available for
     * advanced developers.
     * </p>
     */
    private JButton findDocButton, webpageAlgorithmButton,
            downloadDocumentation, extractMappingButton, runExamplesButton,
            runMainTestButton;

    /** Utility buttons */
    private JButton preferencesButton, algorithmExplorerButton,
            systemInfoButton;

    /**
     * Reference to the Documentation group's button stack. Kept as a field so
     * the hidden developer-only buttons can be dynamically added to /
     * removed from it at runtime when the F12 shortcut is pressed.
     */
    private JPanel docStack;

    /**
     * Whether the hidden developer-only buttons (extractMappingButton,
     * runExamplesButton, runMainTestButton) are currently shown in the
     * Documentation group.
     */
    private boolean hiddenButtonsVisible = false;

    /**
     * Constructor
     *
     * @param runAsStandalone true if running standalone
     * @throws UnsupportedLookAndFeelException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws ClassNotFoundException
     */
    public DevelopersToolsWindow(boolean runAsStandalone) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {

        setTitle("SPMF Developer Tools  \u2014  v" + Main.SPMF_VERSION);
        setDefaultCloseOperation(runAsStandalone ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        initializeButtons();
        addComponentsToMainPanel();
        setupHiddenShortcut();

        getContentPane().add(mainPanel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /** Initialize all buttons grouped by category. */
    private void initializeButtons() {

        // ── Algorithm lists ────────────────────────────────────────────────────
        simpleAlgorithmButton      = createButton(
                "View and export list of algorithms",
                "Display all available algorithms.");
        outputInputAlgorithmButton = createButton(
                "All input/output types",
                "List all I/O types.");
        algorithmByInputButton     = createButton(
                "Algorithms by input type",
                "Group algorithms by input type.");
        algorithmByOutputButton    = createButton(
                "Algorithms by output type",
                "Group algorithms by output type.");

        // ── Statistics ─────────────────────────────────────────────────────────
        authorCountButton   = createButton(
                "Algorithm count by author",
                "View algorithm statistics by author.");
        categoryCountButton = createButton(
                "Algorithm count by category",
                "View algorithm statistics by category.");
        typeCountButton     = createButton(
                "Algorithm count by internal type",
                "View algorithm statistics by internal type.");

        // ── Documentation ──────────────────────────────────────────────────────
        findDocButton         = createButton(
                "Find broken URLs in documentation",
                "Check for invalid documentation links.");
        webpageAlgorithmButton = createButton(
                "View documentation via internal browser",
                "Browse algorithm documentation.");
        downloadDocumentation  = createButton(
                "Download offline documentation to /doc/",
                "Save documentation for offline use.");

        // Scans the offline HTML documentation files and extracts the mapping
        // between algorithm names and their example input files (e.g.
        // contextPasquier99.txt). Sits in the Documentation group because it
        // works directly on the offline HTML files that live in /doc/.
        // NOTE: This button is hidden by default; press F12 to reveal it.
        extractMappingButton = createButton(
                "Extract algorithm/input-file mapping from offline doc",
                "<html>Scan the offline HTML documentation folder and extract<br>"
                + "the mapping between algorithm names and their example<br>"
                + "input files (e.g. contextPasquier99.txt).</html>");

        // Scans the offline HTML documentation files for command-line examples,
        // extracts the algorithm name, input file name, and actual parameter
        // values shown in each example, and then automatically runs every
        // algorithm found using AlgorithmManager, displaying a per-algorithm
        // success/failure/timeout report in a sortable JTable and capturing all
        // System.out / System.err output in a console pane. Also sits in the
        // Documentation group because it drives directly from the same offline
        // HTML files that the mapping extractor reads.
        // NOTE: This button is hidden by default; press F12 to reveal it.
        runExamplesButton = createButton(
                "Run all documentation examples automatically",
                "<html>Scan the offline HTML documentation folder for command-line<br>"
                + "examples (java -jar spmf.jar run ...), extract the algorithm<br>"
                + "name, input file, and parameter values from each example,<br>"
                + "and automatically run every algorithm found. Results are shown<br>"
                + "in a sortable table (SUCCESS / ERROR / TIMEOUT / SKIPPED)<br>"
                + "with full console output captured for each algorithm.</html>");

        // Discovers every class whose name starts with "MainTest" in the
        // ca.pfv.spmf.test package and runs each one's main() method on a
        // background thread with a per-test timeout, capturing System.out /
        // System.err and any thrown exception. Results are shown in a sortable,
        // searchable JTable; double-clicking a row opens a details dialog with
        // the full console output and stack trace. Sits in the Documentation
        // group alongside the other hidden developer-only runners.
        // NOTE: This button is hidden by default; press F12 to reveal it.
        runMainTestButton = createButton(
                "Run all MainTest* test classes",
                "<html>Scan the ca.pfv.spmf.test package for classes whose name<br>"
                + "starts with MainTest, run each main() method, and display<br>"
                + "success / failure / timeout results in a searchable, sortable<br>"
                + "table with full console output and stack traces available via<br>"
                + "double-click.</html>");

        // ── Utilities ──────────────────────────────────────────────────────────
        preferencesButton      = createButton(
                "View user preferences",
                "Open the preferences viewer.");
        algorithmExplorerButton = createButton(
                "Algorithm manager",
                "Explore and manage algorithms.");
        systemInfoButton        = createButton(
                "System information",
                "Display system and Java information.");
    }

    /**
     * Creates a styled, uniformly-sized button wired to this window's
     * {@link ActionListener}.
     *
     * @param text    the button label
     * @param tooltip the tooltip text (may use basic HTML)
     * @return the configured button
     */
    private JButton createButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setPreferredSize(BUTTON_SIZE);
        button.setMaximumSize(BUTTON_SIZE);
        button.setFocusPainted(false);
        button.addActionListener(this);
        return button;
    }

    /** Build and add all components to the main panel. */
    private void addComponentsToMainPanel() {
        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildButtonPanel(), BorderLayout.CENTER);
//        mainPanel.add(buildFooterPanel(), BorderLayout.SOUTH);
    }

    /**
     * Builds the header panel with the SPMF logo and a subtitle label.
     *
     * @return the assembled header panel
     */
    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JLabel logo = new JLabel(
                new ImageIcon(MainWindow.class.getResource("spmf.png")));
        logo.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitle = new JLabel("Developer Tools", SwingConstants.CENTER);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.BOLD, 13f));
        subtitle.setForeground(new Color(60, 60, 60));

        header.add(logo,     BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);

        return header;
    }

    /**
     * Builds the main button panel arranged in two columns, each column
     * containing two titled groups of buttons.
     *
     * <pre>
     * Left column          Right column
     * ─────────────────    ──────────────────────────────────────────────
     * Algorithm Lists      Documentation
     *   …                    Find broken URLs
     *                        View via browser
     * Statistics             Download offline docs
     *   …                    [hidden] Extract doc/input-file mapping  ← F12
     *                        [hidden] Run all documentation examples  ← F12
     *                        [hidden] Run all MainTest* test classes  ← F12
     *                      Utilities
     *                        …
     * </pre>
     *
     * @return the assembled button panel
     */
    private JPanel buildButtonPanel() {
        JPanel outer = new JPanel(new BorderLayout(12, 0));

        // ── Algorithm Tools group ──────────────────────────────────────────────
        JPanel algorithmGroup = buildGroup("Algorithm Lists & I/O",
                simpleAlgorithmButton,
                outputInputAlgorithmButton,
                algorithmByInputButton,
                algorithmByOutputButton);

        // ── Statistics group ───────────────────────────────────────────────────
        JPanel statsGroup = buildGroup("Statistics",
                authorCountButton,
                categoryCountButton,
                typeCountButton);

        // ── Documentation group (built specially so we keep a handle on its
        //    button stack for adding/removing the hidden buttons) ──────────
        JPanel docGroup = buildDocGroup();

        // ── Utilities group ────────────────────────────────────────────────────
        JPanel utilGroup = buildGroup("Utilities",
                preferencesButton,
                algorithmExplorerButton,
                systemInfoButton);

        // Left column: algorithm lists + statistics
        JPanel leftColumn = new JPanel(new BorderLayout(0, 12));
        leftColumn.add(algorithmGroup, BorderLayout.NORTH);
        leftColumn.add(statsGroup,     BorderLayout.CENTER);

        // Right column: documentation + utilities
        JPanel rightColumn = new JPanel(new BorderLayout(0, 12));
        rightColumn.add(docGroup,  BorderLayout.NORTH);
        rightColumn.add(utilGroup, BorderLayout.CENTER);

        JPanel columns = new JPanel(new BorderLayout(12, 0));
        columns.add(leftColumn,  BorderLayout.WEST);
        columns.add(rightColumn, BorderLayout.EAST);

        outer.add(columns, BorderLayout.NORTH);
        return outer;
    }

    /**
     * Builds a titled group panel containing vertically stacked buttons with
     * a small gap between them.
     *
     * @param title   the border title for this group
     * @param buttons one or more buttons to stack top-to-bottom
     * @return the assembled group panel
     */
    private JPanel buildGroup(String title, JButton... buttons) {
        JPanel group = new JPanel(new BorderLayout());
        group.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP));

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));

        for (int i = 0; i < buttons.length; i++) {
            stack.add(buttons[i]);
            if (i < buttons.length - 1) {
                stack.add(Box.createVerticalStrut(6));
            }
        }

        group.add(stack, BorderLayout.NORTH);
        return group;
    }

    /**
     * Builds the Documentation group. Only the three always-visible buttons
     * ({@code findDocButton}, {@code webpageAlgorithmButton},
     * {@code downloadDocumentation}) are added initially. The advanced
     * developer-only buttons ({@code extractMappingButton},
     * {@code runExamplesButton}, {@code runMainTestButton}) are omitted until
     * the user presses the hidden F12 shortcut, which calls
     * {@link #toggleHiddenButtons()}.
     *
     * <p>A reference to the stack panel is stored in {@link #docStack} so the
     * hidden buttons can be added/removed at runtime without rebuilding the
     * whole UI.</p>
     *
     * @return the assembled Documentation group panel
     */
    private JPanel buildDocGroup() {
        JPanel group = new JPanel(new BorderLayout());
        group.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Documentation",
                TitledBorder.LEFT,
                TitledBorder.TOP));

        docStack = new JPanel();
        docStack.setLayout(new BoxLayout(docStack, BoxLayout.Y_AXIS));
        docStack.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));

        // Always-visible documentation buttons
        docStack.add(findDocButton);
        docStack.add(Box.createVerticalStrut(6));
        docStack.add(webpageAlgorithmButton);
        docStack.add(Box.createVerticalStrut(6));
        docStack.add(downloadDocumentation);

        // NOTE: extractMappingButton, runExamplesButton and runMainTestButton
        // are intentionally NOT added here. They are revealed only via the
        // hidden F12 shortcut.

        group.add(docStack, BorderLayout.NORTH);
        return group;
    }

    /**
     * Wires up the hidden shortcut that toggles the visibility of the
     * advanced developer-only buttons in the Documentation group:
     * {@code extractMappingButton} ("Extract algorithm/input-file mapping
     * from offline doc"), {@code runExamplesButton} ("Run all documentation
     * examples automatically") and {@code runMainTestButton} ("Run all
     * MainTest* test classes").
     *
     * <p>The shortcut is <b>F12</b> (a conventional developer-tools key in
     * most browsers and IDEs). It is registered on the root pane using the
     * {@code WHEN_IN_FOCUSED_WINDOW} input map, so it works regardless of
     * which child component currently has focus. No menu item, tooltip, or
     * other visible hint advertises this shortcut — it is intentionally
     * undiscoverable for casual users.</p>
     */
    private void setupHiddenShortcut() {
        KeyStroke f12 = KeyStroke.getKeyStroke("F12");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(f12, "toggleHiddenDevButtons");
        getRootPane().getActionMap().put("toggleHiddenDevButtons", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                toggleHiddenButtons();
            }
        });
    }

    /**
     * Adds or removes the hidden developer-only buttons
     * ({@code extractMappingButton}, {@code runExamplesButton} and
     * {@code runMainTestButton}) from the Documentation group, then re-packs
     * the window so its size adjusts to the new contents.
     */
    private void toggleHiddenButtons() {
        hiddenButtonsVisible = !hiddenButtonsVisible;

        // Rebuild the Documentation stack from scratch so spacing stays
        // consistent whether the hidden buttons are shown or not.
        docStack.removeAll();
        docStack.add(findDocButton);
        docStack.add(Box.createVerticalStrut(6));
        docStack.add(webpageAlgorithmButton);
        docStack.add(Box.createVerticalStrut(6));
        docStack.add(downloadDocumentation);

        if (hiddenButtonsVisible) {
            docStack.add(Box.createVerticalStrut(6));
            docStack.add(extractMappingButton);
            docStack.add(Box.createVerticalStrut(6));
            docStack.add(runExamplesButton);
            docStack.add(Box.createVerticalStrut(6));
            docStack.add(runMainTestButton);
        }

        docStack.revalidate();
        docStack.repaint();
        pack();
    }

//    /**
//     * Builds the footer panel containing a right-aligned version label.
//     *
//     * @return the assembled footer panel
//     */
//    private JPanel buildFooterPanel() {
//        JPanel footer = new JPanel(new BorderLayout(0, 4));
//        footer.add(new JSeparator(), BorderLayout.NORTH);
//
//        JLabel version = new JLabel(
//                "SPMF v" + Main.SPMF_VERSION, SwingConstants.RIGHT);
//        version.setFont(version.getFont().deriveFont(Font.PLAIN, 11f));
//        version.setForeground(Color.GRAY);
//        version.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 4));
//
//        footer.add(version, BorderLayout.CENTER);
//        return footer;
//    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        try {
            if (source == preferencesButton) {
                new PreferencesViewer().setVisible(true);

            } else if (source == findDocButton) {
                new FindDocBrokenURLsViewer();

            } else if (source == simpleAlgorithmButton) {
                new AlgorithmListExporterWindow(false);

            } else if (source == webpageAlgorithmButton) {
                new WebpageAlgorithmDocViewer();

            } else if (source == algorithmExplorerButton) {
                new AlgorithmExplorer(false);

            } else if (source == outputInputAlgorithmButton) {
                new InputOutputTypeListWindow(false);

            } else if (source == authorCountButton) {
                new AuthorAlgorithmCountWindow(false);

            } else if (source == algorithmByInputButton) {
                new InputTypeListWindow(false);

            } else if (source == algorithmByOutputButton) {
                new OutputTypeListWindow(false);

            } else if (source == systemInfoButton) {
                new SystemInfoDisplay(false);

            } else if (source == categoryCountButton) {
                new CategoryAlgorithmCountWindow(false);

            } else if (source == typeCountButton) {
                new TypeAlgorithmCountWindow(false);

            } else if (source == downloadDocumentation) {
                new DocumentationDownloaderWindow().createAndShowGUI();

            } else if (source == extractMappingButton) {
                // Open the tool that scans offline HTML documentation files and
                // extracts algorithm-name → example-input-file mappings.
                new AlgorithmFileMappingExtractorWindow().createAndShowGUI();

            } else if (source == runExamplesButton) {
                // Open the tool that scans offline HTML documentation files for
                // command-line examples (java -jar spmf.jar run ...), extracts the
                // algorithm name, input file name, and actual parameter values from
                // each example line, and then automatically runs every algorithm
                // found using AlgorithmManager. Results are displayed in a sortable
                // JTable (SUCCESS / ERROR / TIMEOUT / SKIPPED) with full console
                // output captured for each algorithm.
                new AlgorithmExampleAutoRunnerWindow().createAndShowGUI();

            } else if (source == runMainTestButton) {
                // Open the tool that discovers every class whose name starts with
                // "MainTest" in the ca.pfv.spmf.test package and runs each one's
                // main() method on a background thread with a per-test timeout.
                // Results are displayed in a sortable, searchable JTable
                // (SUCCESS / ERROR / TIMEOUT / NO_MAIN / NOT_FOUND) with full
                // console output and stack traces available via double-click.
                new MainTestAutoRunnerWindow().createAndShowGUI();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Standalone entry point.
     */
    public static void main(String[] args) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException,
            UnsupportedLookAndFeelException {
        new DevelopersToolsWindow(true);
    }
}