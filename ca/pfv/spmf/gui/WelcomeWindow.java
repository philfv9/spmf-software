package ca.pfv.spmf.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import ca.pfv.spmf.gui.pattern_diff_analyzer.PatternDiffAnalyzer;
import ca.pfv.spmf.gui.patternvizualizer.PatternVizualizer;
import ca.pfv.spmf.gui.pluginmanager.PluginWindow;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.workflow_editor.WorkflowEditorWindow;

/*
 * Copyright (c) 2008-2019 Philippe Fournier-Viger
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

public class WelcomeWindow extends JFrame {
    public static final long serialVersionUID = 1L;
    
    // UI Colors
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color SECONDARY_COLOR = new Color(52, 73, 94);
    private static final Color ACCENT_COLOR = new Color(46, 204, 113);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(44, 62, 80);
    private static final Color TEXT_SECONDARY = new Color(127, 140, 141);
    private static final Color BACKGROUND_START = new Color(236, 240, 241);
    private static final Color BACKGROUND_END = new Color(220, 230, 240);
    
    // Button accent colors
    private static final Color COLOR_VIEW_TRANSFORM = new Color(52, 152, 219);
    private static final Color COLOR_GENERATE_DATA = new Color(41, 128, 185);
    private static final Color COLOR_RUN_ALGORITHM = new Color(46, 204, 113);
    private static final Color COLOR_WORKFLOW = new Color(155, 89, 182);
    private static final Color COLOR_PATTERN_DIFF = new Color(230, 126, 34);
    private static final Color COLOR_PATTERN_VIEWER = new Color(26, 188, 156);
    private static final Color COLOR_OTHER_TOOLS = new Color(241, 196, 15);
    private static final Color COLOR_DOCUMENTATION = new Color(52, 73, 94);
    private static final Color COLOR_ABOUT = new Color(149, 165, 166);
    
    // Buttons
    private JButton buttonViewTransformData;
    private JButton buttonGenerateData;
    private JButton buttonRunAlgorithm;
    private JButton buttonPlugins;
    private JButton buttonRunManyAlgorithms;
    private JButton buttonPatternDiff;
    private JButton buttonPatternViewer;
    private JButton buttonOtherTools;
    private JButton buttonDocumentation;
    private JButton buttonAboutSPMF;
    
    // Labels
    private JLabel labelWhatWouldYouLike;
    private JLabel labelLogo;
    private JLabel labelVersion;
    private JLabel labelSubtitle;
    private JLabel labelAlgorithmCount;
    
    // Panels
    private JPanel panelMain;

    public WelcomeWindow() throws Exception {
        setTitle("SPMF v." + Main.SPMF_VERSION + " - Data Mining Software");
        setMinimumSize(new Dimension(750, 700));
        setSize(850, 750);
        setLocationRelativeTo(null);
        setResizable(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initComponents();
        setVisible(true);
    }

    private void initComponents() throws Exception {
        JFrame frame = this;
        frame.setLayout(new BorderLayout());

        // Main panel with gradient background
        panelMain = new GradientPanel();
        panelMain.setLayout(new BorderLayout(15, 15));
        panelMain.setBorder(new EmptyBorder(20, 30, 20, 30));

        // Create header panel
        JPanel headerPanel = createHeaderPanel();
        
        // Create main content panel with cards
        JPanel contentPanel = createContentPanel();
        
        // Create footer panel
       // JPanel footerPanel = createFooterPanel();

        panelMain.add(headerPanel, BorderLayout.NORTH);
        panelMain.add(contentPanel, BorderLayout.CENTER);
      //  panelMain.add(footerPanel, BorderLayout.SOUTH);

        setupButtonEvents(frame);
        frame.setContentPane(panelMain);
    }
    
    /**
     * Creates the header panel with logo and title
     * @throws Exception 
     */
    private JPanel createHeaderPanel() throws Exception {
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(new EmptyBorder(10, 0, 15, 0));
        
        // Logo
        try {
            ImageIcon logoIcon = new ImageIcon(WelcomeWindow.class.getResource("spmf.png"));
            labelLogo = new JLabel(logoIcon);
            labelLogo.setAlignmentX(Component.CENTER_ALIGNMENT);
            headerPanel.add(labelLogo);
        } catch (Exception e) {
            // If logo not found, show text
            JLabel textLogo = new JLabel("SPMF");
            textLogo.setFont(new Font("SansSerif", Font.BOLD, 48));
            textLogo.setForeground(PRIMARY_COLOR);
            textLogo.setAlignmentX(Component.CENTER_ALIGNMENT);
            headerPanel.add(textLogo);
        }
        
        headerPanel.add(Box.createVerticalStrut(8));
        
        // Version label
       // labelVersion = new JLabel("Version " + Main.SPMF_VERSION);
       // labelVersion.setFont(new Font("SansSerif", Font.PLAIN, 14));
       // labelVersion.setForeground(TEXT_SECONDARY);
      //  labelVersion.setAlignmentX(Component.CENTER_ALIGNMENT);
      //  headerPanel.add(labelVersion);
        
     //   headerPanel.add(Box.createVerticalStrut(4));
        
        // Subtitle
      //  labelSubtitle = new JLabel("Open-Source Data Mining Library");
     //   labelSubtitle.setFont(new Font("SansSerif", Font.ITALIC, 12));
      //  labelSubtitle.setForeground(TEXT_SECONDARY);
      //  labelSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
      //  headerPanel.add(labelSubtitle);
        
     //   headerPanel.add(Box.createVerticalStrut(4));
        
        // Algorithm count label
     //   int algorithmCount = AlgorithmManager.getInstance().getListOfAlgorithmsAsString(false, false, false, true, false).size();
     //   labelAlgorithmCount = new JLabel("Featuring " + algorithmCount + " algorithms");
     //   labelAlgorithmCount.setFont(new Font("SansSerif", Font.BOLD, 11));
     //   labelAlgorithmCount.setForeground(ACCENT_COLOR);
     //   labelAlgorithmCount.setAlignmentX(Component.CENTER_ALIGNMENT);
     //   headerPanel.add(labelAlgorithmCount);
        
     //   headerPanel.add(Box.createVerticalStrut(15));
        
        // Question label
        labelWhatWouldYouLike = new JLabel("What would you like to do?");
        labelWhatWouldYouLike.setFont(new Font("SansSerif", Font.BOLD, 18));
        labelWhatWouldYouLike.setForeground(TEXT_PRIMARY);
        labelWhatWouldYouLike.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(labelWhatWouldYouLike);
        
        return headerPanel;
    }
    
    
    /**
     * Creates the main content panel with action cards
     */
    private JPanel createContentPanel() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        
        // Create styled buttons
        buttonViewTransformData = createStyledButton(
            "View and Transform Data",
            "View, convert and transform datasets",
            "/ca/pfv/spmf/gui/icons/viewdata24.png",
            COLOR_VIEW_TRANSFORM
        );
        
        buttonGenerateData = createStyledButton(
            "Generate Data",
            "Generate synthetic datasets for testing",
            "/ca/pfv/spmf/gui/icons/preparedata24.png",
            COLOR_GENERATE_DATA
        );
        
        buttonRunAlgorithm = createStyledButton(
            "Run Data Mining Algorithm",
            "Execute a single data mining algorithm",
            "/ca/pfv/spmf/gui/icons/Play24.gif",
            COLOR_RUN_ALGORITHM
        );
        
        buttonRunManyAlgorithms = createStyledButton(
            "Workflow Editor",
            "Design and run complex algorithm workflows",
            "/ca/pfv/spmf/gui/icons/History24.gif",
            COLOR_WORKFLOW
        );
        
        buttonPatternViewer = createStyledButton(
            "Pattern Viewer",
            "Visualize and explore discovered patterns",
            "/ca/pfv/spmf/gui/icons/viewdata24.png",
            COLOR_PATTERN_VIEWER
        );
        
        buttonPatternDiff = createStyledButton(
            "Pattern Diff Analyzer",
            "Compare and analyze differences between two pattern files",
            "/ca/pfv/spmf/gui/icons/viewdatatwice24.png",
            COLOR_PATTERN_DIFF
        );
        
        buttonOtherTools = createStyledButton(
            "Other Tools",
            "Additional utilities and experimental features",
            "/ca/pfv/spmf/gui/icons/Preferences24.gif",
            COLOR_OTHER_TOOLS
        );
        
        buttonDocumentation = createStyledButton(
            "Documentation",
            "Access online tutorials and API documentation",
            "/ca/pfv/spmf/gui/icons/Information24.gif",
            COLOR_DOCUMENTATION
        );
        
        // Plugins button - DEACTIVATED but code kept
        buttonPlugins = createStyledButton(
            "Add/Remove Plugins",
            "Manage SPMF plugins",
            "/ca/pfv/spmf/gui/icons/Preferences24.gif",
            new Color(149, 165, 166)
        );
        buttonPlugins.setEnabled(false);
        buttonPlugins.setVisible(false);
        
        // Layout: 2 columns, 4 rows
        // Row 1 - Data operations
        gbc.gridx = 0; gbc.gridy = 0;
        contentPanel.add(buttonViewTransformData, gbc);
        
        gbc.gridx = 1; gbc.gridy = 0;
        contentPanel.add(buttonGenerateData, gbc);
        
        // Row 2 - Algorithm execution
        gbc.gridx = 0; gbc.gridy = 1;
        contentPanel.add(buttonRunAlgorithm, gbc);
        
        gbc.gridx = 1; gbc.gridy = 1;
        contentPanel.add(buttonRunManyAlgorithms, gbc);
        
        // Row 3 - Pattern analysis
        gbc.gridx = 0; gbc.gridy = 2;
        contentPanel.add(buttonPatternViewer, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        contentPanel.add(buttonPatternDiff, gbc);
        
        // Row 4 - Other tools and documentation
        gbc.gridx = 0; gbc.gridy = 3;
        contentPanel.add(buttonOtherTools, gbc);
        
        gbc.gridx = 1; gbc.gridy = 3;
        contentPanel.add(buttonDocumentation, gbc);
        
        return contentPanel;
    }
    
    /**
     * Creates a styled button with icon and description
     * @param title The button title
     * @param description The button description
     * @param iconPath Path to the icon resource
     * @param accentColor The accent color for this button
     * @return The styled JButton
     */
    private JButton createStyledButton(String title, String description, String iconPath, Color accentColor) {
        JButton button = new JButton() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Background
                if (!isEnabled()) {
                    g2d.setColor(new Color(240, 240, 240));
                } else if (getModel().isPressed()) {
                    g2d.setColor(new Color(240, 240, 240));
                } else if (getModel().isRollover()) {
                    g2d.setColor(new Color(250, 250, 252));
                } else {
                    g2d.setColor(CARD_COLOR);
                }
                
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                
                // Draw accent bar on the left
                if (isEnabled()) {
                    g2d.setColor(accentColor);
                } else {
                    g2d.setColor(new Color(200, 200, 200));
                }
                g2d.fillRoundRect(0, 0, 5, getHeight(), 5, 5);
                g2d.fillRect(3, 0, 2, getHeight());
                
                // Draw subtle shadow/border
                if (isEnabled() && getModel().isRollover()) {
                    g2d.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 100));
                    g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                } else {
                    g2d.setColor(new Color(220, 220, 220));
                    g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        
        button.setLayout(new BorderLayout(12, 5));
        button.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 15));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(320, 70));
        button.setMinimumSize(new Dimension(280, 60));
        
        // Icon panel
        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(32, 32));
        try {
            ImageIcon icon = new ImageIcon(WelcomeWindow.class.getResource(iconPath));
            JLabel iconLabel = new JLabel(icon);
            iconPanel.add(iconLabel);
        } catch (Exception e) {
            // Icon not found, use placeholder
            JLabel placeholder = new JLabel("•");
            placeholder.setFont(new Font("SansSerif", Font.BOLD, 24));
            placeholder.setForeground(accentColor);
            iconPanel.add(placeholder);
        }
        
        // Text panel
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel descLabel = new JLabel("<html><body style='width: 200px'>" + description + "</body></html>");
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLabel.setForeground(TEXT_SECONDARY);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(descLabel);
        
        // Arrow indicator
        JLabel arrowLabel = new JLabel("›");
        arrowLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        arrowLabel.setForeground(new Color(200, 200, 200));
        
        button.add(iconPanel, BorderLayout.WEST);
        button.add(textPanel, BorderLayout.CENTER);
        button.add(arrowLabel, BorderLayout.EAST);
        
        // Hover effect for arrow
        final Color originalArrowColor = new Color(200, 200, 200);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                arrowLabel.setForeground(accentColor);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                arrowLabel.setForeground(originalArrowColor);
            }
        });
        
        return button;
    }
    
    /**
     * Creates the footer panel with copyright and links
     */
    private JPanel createFooterPanel() {
        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setOpaque(false);
        footerPanel.setBorder(new EmptyBorder(15, 0, 5, 0));
        
        // Separator line
        JPanel separatorPanel = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(new Color(200, 200, 200));
                g2d.drawLine(50, getHeight() / 2, getWidth() - 50, getHeight() / 2);
            }
        };
        separatorPanel.setOpaque(false);
        separatorPanel.setPreferredSize(new Dimension(0, 10));
        separatorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        footerPanel.add(separatorPanel);
        
        footerPanel.add(Box.createVerticalStrut(8));
        
        // Copyright and link panel
        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        linkPanel.setOpaque(false);
        
        JLabel copyrightLabel = new JLabel("© Philippe Fournier-Viger et al.");
        copyrightLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        copyrightLabel.setForeground(TEXT_SECONDARY);
        
        JLabel websiteLabel = new JLabel("Visit Website");
        websiteLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        websiteLabel.setForeground(PRIMARY_COLOR);
        websiteLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        websiteLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openWebsite("http://www.philippe-fournier-viger.com/spmf");
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                websiteLabel.setText("<html><u>Visit Website</u></html>");
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                websiteLabel.setText("Visit Website");
            }
        });

        // About link
        JLabel aboutLabel = new JLabel("About");
        aboutLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        aboutLabel.setForeground(PRIMARY_COLOR);
        aboutLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        aboutLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                actionAboutSPMF(null);
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                aboutLabel.setText("<html><u>About</u></html>");
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                aboutLabel.setText("About");
            }
        });
        
        linkPanel.add(copyrightLabel);
        linkPanel.add(new JLabel("|"));
        linkPanel.add(websiteLabel);
        linkPanel.add(new JLabel("|"));
        linkPanel.add(aboutLabel);
        
        footerPanel.add(linkPanel);
        
        return footerPanel;
    }
    
    /**
     * Opens a website in the default browser
     * @param url The URL to open
     */
    private void openWebsite(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            System.err.println("Could not open browser: " + ex.getMessage());
        }
    }
    
    /**
     * Gradient background panel
     */
    private class GradientPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            GradientPaint gradient = new GradientPaint(
                0, 0, BACKGROUND_START,
                0, getHeight(), BACKGROUND_END
            );
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * Setup the button events for a frame
     * @param frame The parent frame
     */
    private void setupButtonEvents(JFrame frame) {
        buttonDocumentation.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                actionDocumentation(evt);
            }
        });
        
        buttonViewTransformData.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                try {
                    actionViewTransformData(evt);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        
        buttonGenerateData.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                try {
                    actionGenerateData(evt);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        
        buttonRunAlgorithm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                try {
                    actionRunAlgorithm(evt);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // Plugins button - DEACTIVATED but event handler kept
        buttonPlugins.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                actionPlugins(evt);
            }
        });
        
        buttonRunManyAlgorithms.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    @SuppressWarnings("unused")
                    WorkflowEditorWindow drawFrame = new WorkflowEditorWindow(false);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });
        
        // Pattern Diff Analyzer button
        buttonPatternDiff.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actionPatternDiff(e);
            }
        });
        
        // Pattern Viewer button
        buttonPatternViewer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actionPatternViewer(e);
            }
        });
        
        // Other Tools button
        buttonOtherTools.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    actionOtherTools(e);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void actionViewTransformData(ActionEvent evt) throws Exception {
        MainWindow mainWindowTools = new MainWindow(true, false, false, false, false);
        mainWindowTools.setTitle("SPMF - View and Transform Data");
        mainWindowTools.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        mainWindowTools.setVisible(true);
    }
    
    private void actionGenerateData(ActionEvent evt) throws Exception {
        MainWindow mainWindowTools = new MainWindow(false, true, false, false, true);
        mainWindowTools.setTitle("SPMF - Generate Data");
        mainWindowTools.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        mainWindowTools.setVisible(true);
    }
    
    private void actionOtherTools(ActionEvent evt) throws Exception {
        MainWindow mainWindowTools = new MainWindow(false, false, true, false, false);
        mainWindowTools.setTitle("SPMF - Other Tools");
        mainWindowTools.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        mainWindowTools.setVisible(true);
    }

    private void actionRunAlgorithm(ActionEvent evt) throws Exception {
        MainWindow window = new MainWindow(false, false, false, true, false);
        window.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        window.setVisible(true);
    }

    private void actionPlugins(ActionEvent evt) {
        // DEACTIVATED - Code kept for future use
        PluginWindow mainplugin = new PluginWindow(this);
        mainplugin.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    }
    
    /**
     * Opens the Pattern Diff Analyzer tool in non-standalone mode
     * @param evt The action event
     */
    private void actionPatternDiff(ActionEvent evt) {
        try {
            // Run in non-standalone mode (false parameter)
            PatternDiffAnalyzer viewer = new PatternDiffAnalyzer(false);
            viewer.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Opens the Pattern Viewer tool with a file selection dialog
     * @param evt The action event
     */
    private void actionPatternViewer(ActionEvent evt) {
        try {
            // Create a file chooser dialog
            javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
            fileChooser.setDialogTitle("Select a Pattern File");
            
            // Add as a class field
            String lastUsedDirectory = PreferencesManager.getInstance().getInputFilePath();

            // Then in actionPatternViewer, before showing dialog:
            fileChooser.setCurrentDirectory(new java.io.File(lastUsedDirectory));
            
            // Optionally set file filters
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Text Files (*.txt)", "txt"));
            fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "All Supported Files (*.txt, *.csv)", "txt", "csv"));
            fileChooser.setAcceptAllFileFilterUsed(true);
            
            // Show the dialog
            int result = fileChooser.showOpenDialog(this);
            
            // If user selected a file
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                java.io.File selectedFile = fileChooser.getSelectedFile();
                String inputFilePath = selectedFile.getAbsolutePath();
                
                // Open the Pattern Viewer with the selected file
                PatternVizualizer viewer = new PatternVizualizer(inputFilePath);
                viewer.setVisible(true);
            }
            // If user cancelled, do nothing
            
        } catch (Exception e) {
            e.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(this,
                "Error opening Pattern Viewer: " + e.getMessage(),
                "Error",
                javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actionDocumentation(ActionEvent evt) {
        String url = "http://www.philippe-fournier-viger.com/spmf/index.php?link=documentation.php";
        openWebsite(url);
    }

    private void actionAboutSPMF(ActionEvent evt) {
        try {
            AboutWindow about = new AboutWindow(this);
            about.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new WelcomeWindow();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}