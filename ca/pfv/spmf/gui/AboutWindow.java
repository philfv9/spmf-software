package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * Do not remove copyright or license information.
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;

/**
 * This class is a modal dialog that provides general information about the
 * SPMF software: version number, algorithm count, tool count, license
 * information, and links to the website, documentation, and contributors page.
 *
 * @author Philippe Fournier-Viger
 */
public class AboutWindow extends JDialog {

    /** Default serial UID */
    private static final long serialVersionUID = 6173164103462475327L;

    /** URL of the SPMF documentation page */
    private static final String URL_DOCUMENTATION =
            "http://philippe-fournier-viger.com/spmf/index.php?link=documentation.php";

    /** URL of the SPMF contributors page */
    private static final String URL_CONTRIBUTORS =
            "http://philippe-fournier-viger.com/spmf/index.php?link=contributors.php";

    /** URL of the SPMF website */
    private static final String URL_WEBSITE =
            "http://www.philippe-fournier-viger.com/spmf/";

    /** Background colour used for the information text area */
    private static final Color COLOR_INFO_BACKGROUND = Color.WHITE;

    /** Border colour used for the information text area wrapper */
    private static final Color COLOR_INFO_BORDER = new Color(200, 200, 200);

    /**
     * Constructor. Builds and displays the About dialog.
     *
     * @param window the parent frame used for dialog positioning
     * @throws Exception if the AlgorithmManager cannot be initialised
     */
    public AboutWindow(JFrame window) throws Exception {
        super(window);

        setIconImage(Toolkit.getDefaultToolkit().getImage(
                AboutWindow.class.getResource("/ca/pfv/spmf/gui/icons/About24.gif")));
        setResizable(false);
        setTitle("About SPMF " + Main.SPMF_VERSION);

        // Use APPLICATION_MODAL so the parent frame is blocked while this dialog
        // is open. Do not also call setModalExclusionType — it would contradict
        // the modal setting and is unnecessary.
        setModalityType(ModalityType.APPLICATION_MODAL);

        // ----- Root panel with padding ----
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        // ----- Logo ----
        JLabel logoLabel = new JLabel(new ImageIcon(
                AboutWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        root.add(logoLabel, BorderLayout.NORTH);

        // ----- Info text ----
        // Count algorithms and tools from the AlgorithmManager
        int algorithmCount = AlgorithmManager.getInstance()
                .getListOfAlgorithmsAsString(false, false, false, true, false).size();
        int toolCount = AlgorithmManager.getInstance()
                .getListOfAlgorithmsAsString(true, true, true, false, false).size();

        // Build the HTML body text. Using HTML inside a JEditorPane gives us
        // word-wrap, correct background colour, and the system UI font without
        // the monospaced look of a plain JTextArea.
        String bodyHtml = "<html><body style='font-family: Dialog; font-size: 11pt; width: 420px;'>"
                + "<p>Thanks for using <b>SPMF version " + Main.SPMF_VERSION + "</b>. "
                + "This version has <b>" + algorithmCount + " algorithms</b> and "
                + "<b>" + toolCount + " tools</b>.</p>"
                + "<p>SPMF is distributed under the open-source <b>GNU GPL license version 3</b>.<br>"
                + "This license is available at: "
                + "<a href='http://www.gnu.org/licenses/'>http://www.gnu.org/licenses/</a>.</p>"
                + "<p>SPMF was founded in <b>2008</b> by <b>Philippe Fournier-Viger</b> and many "
                + "persons have contributed to the project.</p>"
                + "<p>Click the buttons below for more information.</p>"
                + "</body></html>";

        // JEditorPane with text/html renders HTML, respects system font, word-wraps.
        // White background is set explicitly to match the original readable appearance.
        JEditorPane infoPane = new JEditorPane("text/html", bodyHtml);
        infoPane.setEditable(false);
        infoPane.setOpaque(true);
        infoPane.setBackground(COLOR_INFO_BACKGROUND);
        infoPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Wrap the pane in a white panel with a subtle light-gray border so the
        // white area is cleanly separated from the dialog's gray background
        JPanel infoWrapper = new JPanel(new BorderLayout());
        infoWrapper.setBackground(COLOR_INFO_BACKGROUND);
        infoWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_INFO_BORDER, 1),
                new EmptyBorder(6, 8, 6, 8)));
        infoWrapper.add(infoPane, BorderLayout.CENTER);
        root.add(infoWrapper, BorderLayout.CENTER);

        // ----- Buttons ----
        JButton btnDocumentation = new JButton("Documentation");
        btnDocumentation.setToolTipText("Open the SPMF documentation page in your browser");
        btnDocumentation.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openWebPage(URL_DOCUMENTATION);
            }
        });

        JButton btnContributors = new JButton("Contributors");
        btnContributors.setToolTipText("Open the SPMF contributors page in your browser");
        btnContributors.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openWebPage(URL_CONTRIBUTORS);
            }
        });

        JButton btnWebsite = new JButton("Website");
        btnWebsite.setToolTipText("Open the SPMF website in your browser");
        btnWebsite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openWebPage(URL_WEBSITE);
            }
        });

        // Close button so the user has an explicit way to dismiss the dialog
        JButton btnClose = new JButton("Close");
        btnClose.setToolTipText("Close this window");
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        // Button panel — centred row with all four buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setBorder(new EmptyBorder(6, 0, 0, 0));
        buttonPanel.add(btnDocumentation);
        buttonPanel.add(btnContributors);
        buttonPanel.add(btnWebsite);
        buttonPanel.add(btnClose);
        root.add(buttonPanel, BorderLayout.SOUTH);

        // Let pack() compute the natural size from content rather than hardcoding
        pack();
        setLocationRelativeTo(window);
    }

    /**
     * Opens the given URL in the system default web browser.
     *
     * @param url the URL to open
     */
    private void openWebPage(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (java.io.IOException e) {
            System.out.println(e.getMessage());
        }
    }
}