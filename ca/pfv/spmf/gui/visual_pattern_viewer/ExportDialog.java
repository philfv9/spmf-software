package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * A modal dialog for exporting the pattern view as a PNG file, used by the Visual Pattern Viewer.
 */
class ExportDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    enum ExportScope {
        CURRENT_PAGE, ALL_VISIBLE, ALL_PATTERNS
    }

    private final PatternsPanel patternsPanel;
    private final JRadioButton rbCurrentPage;
    private final JRadioButton rbAllVisible;
    private final JRadioButton rbAllPatterns;
    private final JTextField   filePathField;
    private final JButton      browseButton;
    private final JButton      exportButton;
    private final JButton      cancelButton;

    ExportDialog(JFrame parent, PatternsPanel patternsPanel) {
        super(parent, "Export as PNG", true);
        this.patternsPanel = patternsPanel;

        JPanel scopePanel = new JPanel(new GridBagLayout());
        scopePanel.setBorder(BorderFactory.createTitledBorder("What to export"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.gridx = 0; gbc.gridy = 0;

        int totalPatterns   = patternsPanel.getTotalPatternCount();
        int visiblePatterns = patternsPanel.getNumberOfVisiblePatterns();
        boolean paginated   = patternsPanel.isPaginated();
        int pageSize        = patternsPanel.getPageSize();
        int currentPage     = patternsPanel.getCurrentPage();
        int patternsOnPage  = Math.max(0, paginated
                ? Math.min(pageSize, visiblePatterns - currentPage * pageSize)
                : visiblePatterns);

        rbCurrentPage = new JRadioButton(String.format(
                "Current page only  (%d pattern%s)",
                patternsOnPage, patternsOnPage != 1 ? "s" : ""));
        rbAllVisible  = new JRadioButton(String.format(
                "All visible patterns  (%d pattern%s)",
                visiblePatterns, visiblePatterns != 1 ? "s" : ""));
        rbAllPatterns = new JRadioButton(String.format(
                "All patterns, including filtered-out  (%d pattern%s)",
                totalPatterns, totalPatterns != 1 ? "s" : ""));

        rbAllVisible.setSelected(true);
        rbCurrentPage.setEnabled(paginated);
        rbAllPatterns.setEnabled(visiblePatterns != totalPatterns);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbCurrentPage); bg.add(rbAllVisible); bg.add(rbAllPatterns);

        scopePanel.add(rbCurrentPage, gbc); gbc.gridy++;
        scopePanel.add(rbAllVisible,  gbc); gbc.gridy++;
        scopePanel.add(rbAllPatterns, gbc);

        JPanel filePanel = new JPanel(new BorderLayout(4, 0));
        filePanel.setBorder(BorderFactory.createTitledBorder("Output file"));
        filePathField = new JTextField(VisualPatternViewer.DEFAULT_EXPORT_FILENAME, 28);
        browseButton  = new JButton("Browse\u2026");
        browseButton.addActionListener(e -> browseForFile());
        filePanel.add(filePathField, BorderLayout.CENTER);
        filePanel.add(browseButton,  BorderLayout.EAST);

        exportButton = new JButton("Export");
        exportButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        cancelButton = new JButton("Cancel");
        exportButton.addActionListener(e -> runExport());
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonRow.add(exportButton); buttonRow.add(cancelButton);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(scopePanel);
        content.add(Box.createVerticalStrut(8));
        content.add(filePanel);
        content.add(Box.createVerticalStrut(8));
        content.add(buttonRow);

        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void browseForFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save PNG file");
        fc.setFileFilter(new FileNameExtensionFilter("PNG images (*.png)", "png"));
        String current = filePathField.getText().trim();
        fc.setSelectedFile(new File(current.isEmpty()
                ? VisualPatternViewer.DEFAULT_EXPORT_FILENAME : current));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) path += ".png";
            filePathField.setText(path);
        }
    }

    private void runExport() {
        String path = filePathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify an output file path.",
                    "Missing File Path", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!path.toLowerCase().endsWith(".png")) {
            path += ".png";
            filePathField.setText(path);
        }
        ExportScope scope = rbCurrentPage.isSelected() ? ExportScope.CURRENT_PAGE
                : rbAllPatterns.isSelected() ? ExportScope.ALL_PATTERNS
                : ExportScope.ALL_VISIBLE;

        exportButton.setEnabled(false);
        browseButton.setEnabled(false);

        try {
            switch (scope) {
                case CURRENT_PAGE -> patternsPanel.exportCurrentPageAsPNG(path);
                case ALL_VISIBLE  -> patternsPanel.exportAllVisibleAsPNG(path);
                case ALL_PATTERNS -> patternsPanel.exportAllPatternsAsPNG(path);
            }
            JOptionPane.showMessageDialog(this,
                    "<html>Export successful!<br><b>" + path + "</b></html>",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Export failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            exportButton.setEnabled(true);
            browseButton.setEnabled(true);
        }
    }
}