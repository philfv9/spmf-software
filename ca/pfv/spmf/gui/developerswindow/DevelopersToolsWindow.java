package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
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

    /** Documentation and browser buttons */
    private JButton findDocButton, webpageAlgorithmButton, downloadDocumentation;

    /** Utility buttons */
    private JButton preferencesButton, algorithmExplorerButton,
            systemInfoButton;

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

        getContentPane().add(mainPanel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /** Initialize all buttons grouped by category */
    private void initializeButtons() {
        // Algorithm lists
        simpleAlgorithmButton       = createButton("View and export list of algorithms",        "Display all available algorithms.");
        outputInputAlgorithmButton  = createButton("All input/output types",                    "List all I/O types.");
        algorithmByInputButton      = createButton("Algorithms by input type",                  "Group algorithms by input type.");
        algorithmByOutputButton     = createButton("Algorithms by output type",                 "Group algorithms by output type.");

        // Statistics
        authorCountButton           = createButton("Algorithm count by author",                 "View algorithm statistics by author.");
        categoryCountButton         = createButton("Algorithm count by category",               "View algorithm statistics by category.");
        typeCountButton             = createButton("Algorithm count by internal type",          "View algorithm statistics by internal type.");

        // Documentation
        findDocButton               = createButton("Find broken URLs in documentation",         "Check for invalid documentation links.");
        webpageAlgorithmButton      = createButton("View documentation via internal browser",   "Browse algorithm documentation.");
        downloadDocumentation       = createButton("Download offline documentation to /doc/",   "Save documentation for offline use.");

        // Utilities
        preferencesButton           = createButton("View user preferences",                     "Open the preferences viewer.");
        algorithmExplorerButton     = createButton("Algorithm manager",                         "Explore and manage algorithms.");
        systemInfoButton            = createButton("System information",                        "Display system and Java information.");
    }

    /**
     * Create a styled button.
     *
     * @param text    the button label
     * @param tooltip the tooltip text
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

    /** Build and add all components to the main panel */
    private void addComponentsToMainPanel() {
        mainPanel.add(buildHeaderPanel(),  BorderLayout.NORTH);
        mainPanel.add(buildButtonPanel(),  BorderLayout.CENTER);
        mainPanel.add(buildFooterPanel(),  BorderLayout.SOUTH);
    }

    /**
     * Build the header panel with logo and title.
     *
     * @return the header panel
     */
    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        // Logo
        JLabel logo = new JLabel(new ImageIcon(MainWindow.class.getResource("spmf.png")));
        logo.setHorizontalAlignment(SwingConstants.CENTER);

        // Subtitle label
        JLabel subtitle = new JLabel("Developer Tools", SwingConstants.CENTER);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.BOLD, 13f));
        subtitle.setForeground(new Color(60, 60, 60));

        header.add(logo,     BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);

        return header;
    }

    /**
     * Build the main button panel with grouped sections.
     *
     * @return the button panel
     */
    private JPanel buildButtonPanel() {
        JPanel outer = new JPanel(new BorderLayout(12, 0));

        // --- Algorithm Tools group ---
        JPanel algorithmGroup = buildGroup("Algorithm Lists & I/O",
                simpleAlgorithmButton,
                outputInputAlgorithmButton,
                algorithmByInputButton,
                algorithmByOutputButton);

        // --- Statistics group ---
        JPanel statsGroup = buildGroup("Statistics",
                authorCountButton,
                categoryCountButton,
                typeCountButton);

        // --- Documentation group ---
        JPanel docGroup = buildGroup("Documentation",
                findDocButton,
                webpageAlgorithmButton,
                downloadDocumentation);

        // --- Utilities group ---
        JPanel utilGroup = buildGroup("Utilities",
                preferencesButton,
                algorithmExplorerButton,
                systemInfoButton);

        // Left column: algorithm + stats
        JPanel leftColumn = new JPanel(new BorderLayout(0, 12));
        leftColumn.add(algorithmGroup, BorderLayout.NORTH);
        leftColumn.add(statsGroup,     BorderLayout.CENTER);

        // Right column: docs + utilities
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
     * Build a titled group panel containing vertically stacked buttons.
     *
     * @param title   the group title
     * @param buttons the buttons to add vertically
     * @return the group panel
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
     * Build the footer panel showing a version label.
     *
     * @return the footer panel
     */
    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.add(new JSeparator(), BorderLayout.NORTH);

        JLabel version = new JLabel("SPMF v" + Main.SPMF_VERSION, SwingConstants.RIGHT);
        version.setFont(version.getFont().deriveFont(Font.PLAIN, 11f));
        version.setForeground(Color.GRAY);
        version.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 4));

        footer.add(version, BorderLayout.CENTER);
        return footer;
    }

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
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Main method
     */
    public static void main(String[] args) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, UnsupportedLookAndFeelException {
        new DevelopersToolsWindow(true);
    }
}